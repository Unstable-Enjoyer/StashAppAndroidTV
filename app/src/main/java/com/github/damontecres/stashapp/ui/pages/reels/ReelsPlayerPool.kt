package com.github.damontecres.stashapp.ui.pages.reels

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.util.EventLogger
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.TsExtractor
import com.github.damontecres.stashapp.util.Constants
import com.github.damontecres.stashapp.util.StashClient
import com.github.damontecres.stashapp.util.StashServer
import com.github.damontecres.stashapp.util.isNotNullOrBlank
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

private const val TAG = "ReelsPlayerPool"
private const val POOL_SIZE = 3
private const val CACHE_SIZE_BYTES = 150L * 1024 * 1024 // 150 MB disk cache
private const val PRE_CACHE_BYTES = 1L * 1024 * 1024 // pre-cache first 1 MB per video

@OptIn(UnstableApi::class)
class ReelsPlayerPool(
    private val context: Context,
    private val server: StashServer,
    private val httpClientChoice: String,
    private val debugLogging: Boolean,
) {
    private val players = HashMap<Int, ExoPlayer>(POOL_SIZE)
    private val cacheScope = CoroutineScope(Dispatchers.IO)

    // Shared disk cache for all players
    private val cache: SimpleCache = synchronized(Companion) {
        sharedCache ?: SimpleCache(
            File(context.cacheDir, "reels_video_cache"),
            LeastRecentlyUsedCacheEvictor(CACHE_SIZE_BYTES),
            androidx.media3.database.StandaloneDatabaseProvider(context),
        ).also { sharedCache = it }
    }

    private fun createUpstreamDataSourceFactory(): DataSource.Factory {
        return if (httpClientChoice.equals("okhttp", ignoreCase = true)) {
            OkHttpDataSource.Factory(server.streamingOkHttpClient)
        } else {
            DefaultHttpDataSource.Factory()
                .setConnectTimeoutMs(5_000)
                .setReadTimeoutMs(30_000)
                .setUserAgent(StashClient.createUserAgent(context))
                .apply {
                    if (server.apiKey.isNotNullOrBlank()) {
                        setDefaultRequestProperties(mapOf(Constants.STASH_API_HEADER to server.apiKey))
                    }
                }
        }
    }

    private fun createCacheDataSourceFactory(): CacheDataSource.Factory {
        return CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(createUpstreamDataSourceFactory())
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    private fun createPlayer(): ExoPlayer {
        val trackSelector = DefaultTrackSelector(context).apply {
            parameters = buildUponParameters()
                .setAllowInvalidateSelectionsOnRendererCapabilitiesChange(true)
                .setAudioOffloadPreferences(
                    TrackSelectionParameters.AudioOffloadPreferences.Builder()
                        .setAudioOffloadMode(
                            TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED,
                        )
                        .build(),
                ).build()
        }

        val extractorsFactory = DefaultExtractorsFactory().apply {
            setTsExtractorTimestampSearchBytes(TsExtractor.DEFAULT_TIMESTAMP_SEARCH_BYTES * 3)
            setConstantBitrateSeekingEnabled(true)
            setConstantBitrateSeekingAlwaysEnabled(true)
        }

        return ExoPlayer.Builder(context)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(createCacheDataSourceFactory(), extractorsFactory),
            ).setRenderersFactory(
                DefaultRenderersFactory(context)
                    .setEnableDecoderFallback(true)
                    .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON),
            ).setTrackSelector(trackSelector)
            .setLoadControl(
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(
                        15_000,    // minBufferMs — 15s (short for reels)
                        30_000,    // maxBufferMs — 30s
                        1_500,     // bufferForPlaybackMs — fast start
                        3_000      // bufferForPlaybackAfterRebufferMs
                    )
                    .setTargetBufferBytes(40 * 1024 * 1024) // 40 MB hard cap per player
                    .setBackBuffer(0, false)  // no back-buffer in reels
                    .build()
            )
            .build()
            .also { player ->
                player.repeatMode = Player.REPEAT_MODE_ONE
                if (debugLogging) {
                    player.addAnalyticsListener(EventLogger())
                }
            }
    }

    private fun slotFor(pageIndex: Int): Int = pageIndex % POOL_SIZE

    fun getPlayer(pageIndex: Int): ExoPlayer {
        val slot = slotFor(pageIndex)
        return players.getOrPut(slot) {
            Log.d(TAG, "Creating player for slot $slot (page $pageIndex)")
            createPlayer()
        }
    }

    fun preparePlayer(pageIndex: Int, mediaItem: MediaItem) {
        val player = getPlayer(pageIndex)
        player.playWhenReady = false
        player.setMediaItem(mediaItem)
        player.prepare()
        Log.d(TAG, "Prepared player for page $pageIndex (slot ${slotFor(pageIndex)})")
    }

    /**
     * Pre-cache the first bytes of a video URI to disk in the background.
     * When the player later calls prepare(), it reads from cache instead of network.
     */
    fun preCacheVideo(uri: Uri) {
        cacheScope.launch {
            try {
                val dataSpec = DataSpec.Builder()
                    .setUri(uri)
                    .setLength(PRE_CACHE_BYTES)
                    .build()
                val cacheDataSource = CacheDataSource(
                    cache,
                    createUpstreamDataSourceFactory().createDataSource(),
                    /* flags= */ 0,
                )
                val cacheWriter = CacheWriter(
                    cacheDataSource,
                    dataSpec,
                    /* temporaryBuffer= */ null,
                    /* progressListener= */ null,
                )
                cacheWriter.cache()
                Log.d(TAG, "Pre-cached ${PRE_CACHE_BYTES / 1024}KB for $uri")
            } catch (e: Exception) {
                Log.w(TAG, "Pre-cache failed for $uri", e)
            }
        }
    }

    fun playPlayer(pageIndex: Int) {
        val player = getPlayer(pageIndex)
        player.playWhenReady = true
        Log.d(TAG, "Playing player for page $pageIndex")
    }

    fun pauseAll() {
        players.values.forEach { it.playWhenReady = false }
    }

    /** Stop all players and clear their media items (for filter/sort changes). */
    fun stopAndClearAll() {
        players.values.forEach { player ->
            player.stop()
            player.clearMediaItems()
        }
        Log.d(TAG, "Stopped and cleared all ${players.size} players")
    }

    fun stopDistant(currentPage: Int) {
        players.entries.forEach { (slot, player) ->
            val nearbySlots = setOf(
                slotFor(currentPage),
                slotFor(currentPage - 1),
                slotFor(currentPage + 1),
            )
            if (slot !in nearbySlots) {
                player.stop()
                Log.d(TAG, "Stopped distant player in slot $slot (current=$currentPage)")
            }
        }
    }

    fun releaseAll() {
        Log.d(TAG, "Releasing all ${players.size} players")
        players.values.forEach { player ->
            player.stop()
            player.release()
        }
        players.clear()
    }

    companion object {
        // SimpleCache must be a singleton per directory
        @Volatile
        private var sharedCache: SimpleCache? = null
    }
}
