package com.github.damontecres.stashapp.ui.pages

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import androidx.media3.ui.compose.modifiers.resizeWithContentScale
import androidx.media3.ui.compose.state.rememberPresentationState
import com.github.damontecres.stashapp.R
import com.github.damontecres.stashapp.data.Scene
import com.github.damontecres.stashapp.navigation.NavigationManagerCompose
import com.github.damontecres.stashapp.playback.CodecSupport
import com.github.damontecres.stashapp.playback.PlaybackMode
import com.github.damontecres.stashapp.playback.TrackActivityPlaybackListener
import com.github.damontecres.stashapp.playback.buildMediaItem
import com.github.damontecres.stashapp.playback.getStreamDecision
import com.github.damontecres.stashapp.ui.ComposeUiConfig
import com.github.damontecres.stashapp.ui.compat.isTvDevice
import com.github.damontecres.stashapp.ui.compat.isNotTvDevice
import com.github.damontecres.stashapp.ui.pages.reels.ReelsBottomMeta
import com.github.damontecres.stashapp.ui.pages.reels.ReelsFilterOverlay
import com.github.damontecres.stashapp.ui.pages.reels.ReelsInfoOverlay
import com.github.damontecres.stashapp.ui.pages.reels.ReelsPlayerPool
import com.github.damontecres.stashapp.ui.pages.reels.ReelsProgressBar
import com.github.damontecres.stashapp.ui.pages.reels.ReelsSortOverlay
import com.github.damontecres.stashapp.ui.pages.reels.ReelsTopBar
import com.github.damontecres.stashapp.ui.util.OneTimeLaunchedEffect
import com.github.damontecres.stashapp.util.QueryEngine
import com.github.damontecres.stashapp.util.StashServer
import com.github.damontecres.stashapp.util.findActivity
import com.github.damontecres.stashapp.util.titleOrFilename
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

private const val TAG = "ReelsPage"

enum class ReelsOverlay {
    NONE, INFO, SORT, FILTER
}

@OptIn(UnstableApi::class)
@Composable
fun ReelsPage(
    server: StashServer,
    uiConfig: ComposeUiConfig,
    navigationManager: NavigationManagerCompose,
    modifier: Modifier = Modifier,
    viewModel: ReelsViewModel = viewModel(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    OneTimeLaunchedEffect { viewModel.init(server, context) }

    val scenes by viewModel.scenes.collectAsState()
    val currentFilter by viewModel.currentFilter.collectAsState()
    val currentSort by viewModel.currentSort.collectAsState()
    val savedFilters by viewModel.savedFilters.collectAsState()
    val sceneDetail by viewModel.sceneDetail.collectAsState()
    val loop by viewModel.loop.collectAsState()
    val autoAdvance by viewModel.autoAdvance.collectAsState()

    var controlsVisible by remember { mutableStateOf(true) }
    var isPaused by remember { mutableStateOf(false) }
    var activeOverlay by remember { mutableStateOf(ReelsOverlay.NONE) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var totalDuration by remember { mutableLongStateOf(0L) }
    var markerPositions by remember { mutableStateOf<List<Long>>(emptyList()) }
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubPosition by remember { mutableLongStateOf(0L) }

    val skipForwardMs = remember { uiConfig.preferences.playbackPreferences.skipForwardMs }
    val skipBackMs = remember { uiConfig.preferences.playbackPreferences.skipBackwardMs }

    val focusRequester = remember { FocusRequester() }
    val haptics = LocalHapticFeedback.current
    val isTV = isTvDevice
    val isTouch = isNotTvDevice

    // Hide system bars on non-TV (edge-to-edge immersive)
    val windowInsetsController =
        remember(context) {
            context
                .findActivity()
                ?.let { WindowCompat.getInsetsController(it.window, it.window.decorView) }
        }

    if (isTouch && windowInsetsController != null) {
        LaunchedEffect(controlsVisible, activeOverlay) {
            windowInsetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
        }
        DisposableEffect(Unit) {
            onDispose {
                windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // Auto-hide timer for controls
    LaunchedEffect(controlsVisible, isPaused, activeOverlay) {
        if (controlsVisible && !isPaused && activeOverlay == ReelsOverlay.NONE) {
            delay(3000)
            controlsVisible = false
        }
    }

    // Create player pool
    val pool = remember {
        val httpClient = uiConfig.preferences.playbackPreferences.playbackHttpClient
        val debugLogging = uiConfig.preferences.playbackPreferences.debugLoggingEnabled
        ReelsPlayerPool(
            context = context,
            server = server,
            httpClientChoice = httpClient.name,
            debugLogging = debugLogging,
        )
    }

    // Track loop changes across all players
    LaunchedEffect(loop) {
        val repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        // Apply to all existing pool players when loop changes
        // The pool always creates players with REPEAT_MODE_ONE by default
        // so we only need to change if loop is off
        if (!loop) {
            // We can't easily iterate pool players, so we'll set on the settled player below
        }
    }

    // Activity tracker ref
    var activityListener by remember { mutableStateOf<TrackActivityPlaybackListener?>(null) }

    // Pager state
    val savedIndex by viewModel.currentIndex.collectAsState()
    val scenesGeneration by viewModel.scenesGeneration.collectAsState()

    val pagerState = rememberPagerState(
        initialPage = savedIndex.coerceIn(0, (scenes.size - 1).coerceAtLeast(0)),
        pageCount = { scenes.size.coerceAtLeast(1) },
    )

    // When scenes are reloaded (filter/sort change), clear old players and reset to page 0
    var lastProcessedGeneration by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(scenesGeneration) {
        if (scenes.isEmpty()) return@LaunchedEffect
        if (scenesGeneration == lastProcessedGeneration) return@LaunchedEffect
        lastProcessedGeneration = scenesGeneration
        // Release old activity listener before clearing
        activityListener?.let { listener ->
            val oldSettled = pagerState.settledPage
            if (oldSettled < scenes.size) {
                listener.release(pool.getPlayer(oldSettled).currentPosition)
            }
        }
        activityListener = null
        pool.stopAndClearAll()
        pagerState.scrollToPage(0)
    }

    // Helper to load scene and build media item for a page index
    suspend fun loadSceneForPage(pageIndex: Int): Pair<Scene, androidx.media3.common.MediaItem>? {
        if (scenes.isEmpty() || pageIndex < 0 || pageIndex >= scenes.size) return null
        val sceneData = scenes[pageIndex]
        return try {
            withTimeoutOrNull(15_000) {
                val queryEngine = QueryEngine(server)
                val fullScene = queryEngine.getScene(sceneData.id) ?: return@withTimeoutOrNull null
                val scene = Scene.fromFullSceneData(fullScene)
                val decision = getStreamDecision(
                    context,
                    scene,
                    PlaybackMode.Choose,
                    uiConfig.preferences.playbackPreferences.streamChoice,
                    uiConfig.preferences.playbackPreferences.transcodeAboveResolution,
                    CodecSupport.getSupportedCodecs(uiConfig.preferences.playbackPreferences),
                )
                val mediaItem = buildMediaItem(context, decision, scene)
                Pair(scene, mediaItem)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading scene ${sceneData.id}", e)
            null
        }
    }

    // Preload on targetPage (fires during fling, before settle)
    LaunchedEffect(scenes) {
        if (scenes.isEmpty()) return@LaunchedEffect
        snapshotFlow { pagerState.targetPage }
            .distinctUntilChanged()
            .collect { target ->
                if (target < 0 || target >= scenes.size) return@collect
                val currentSettled = pagerState.settledPage
                Log.d(TAG, "targetPage=$target, preloading (settled=$currentSettled)")

                // Preload target (but skip if it's the currently playing page)
                if (target != currentSettled) {
                    val result = loadSceneForPage(target)
                    if (result != null) {
                        pool.preparePlayer(target, result.second)
                    }
                }
                // Best-effort preload adjacent pages + pre-cache to disk
                // Skip the currently settled page to avoid clobbering the playing video
                for (adj in listOf(target - 1, target + 1)) {
                    if (adj in 0 until scenes.size && adj != target && adj != currentSettled) {
                        val adjResult = loadSceneForPage(adj)
                        if (adjResult != null) {
                            pool.preparePlayer(adj, adjResult.second)
                            // Pre-cache first bytes to disk for faster start
                            adjResult.second.localConfiguration?.uri?.let { uri ->
                                pool.preCacheVideo(uri)
                            }
                        }
                    }
                }
            }
    }

    // Play on settledPage (fires only after animation completes)
    LaunchedEffect(scenes) {
        if (scenes.isEmpty()) return@LaunchedEffect
        var previousSettled = -1
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { settled ->
                if (settled < 0 || settled >= scenes.size) return@collect
                Log.d(TAG, "settledPage=$settled, starting playback (prev=$previousSettled)")

                viewModel.setCurrentIndex(settled)

                // Clean up old activity listener from previous page
                if (previousSettled >= 0 && previousSettled < scenes.size) {
                    activityListener?.let { listener ->
                        val oldPlayer = pool.getPlayer(previousSettled)
                        listener.release(oldPlayer.currentPosition)
                    }
                    // Pause only the previous page's player (not all)
                    pool.getPlayer(previousSettled).playWhenReady = false
                }
                activityListener = null

                val player = pool.getPlayer(settled)
                // Apply loop setting
                player.repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF

                // If this page wasn't preloaded yet, load it now
                if (player.mediaItemCount == 0) {
                    val result = loadSceneForPage(settled)
                    if (result != null) {
                        pool.preparePlayer(settled, result.second)
                    }
                }

                pool.playPlayer(settled)
                isPaused = false
                pool.stopDistant(settled)

                // Update marker positions
                try {
                    val sceneData = scenes[settled]
                    val queryEngine = QueryEngine(server)
                    val fullScene = queryEngine.getScene(sceneData.id)
                    if (fullScene != null) {
                        markerPositions = fullScene.scene_markers.map {
                            (it.seconds * 1000).toLong()
                        }

                        // Create new activity tracker
                        val scene = Scene.fromFullSceneData(fullScene)
                        activityListener = TrackActivityPlaybackListener(
                            server = server,
                            scene = scene,
                            getCurrentPosition = { player.currentPosition },
                        )
                        player.addListener(activityListener!!)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error setting up activity tracking for page $settled", e)
                }

                previousSettled = settled
            }
    }

    // Position update ticker - tracks the settled page's player
    LaunchedEffect(Unit) {
        while (true) {
            val settled = pagerState.settledPage
            if (scenes.isNotEmpty() && settled < scenes.size) {
                val player = pool.getPlayer(settled)
                if (player.isPlaying) {
                    currentPosition = player.currentPosition
                    totalDuration = player.duration.coerceAtLeast(0)
                }
            }
            delay(250)
        }
    }

    // Auto-advance listener
    LaunchedEffect(autoAdvance, pagerState.settledPage) {
        if (!autoAdvance || loop) return@LaunchedEffect
        val settled = pagerState.settledPage
        if (scenes.isEmpty() || settled >= scenes.size) return@LaunchedEffect
        val player = pool.getPlayer(settled)
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED && autoAdvance && !loop) {
                    val nextPage = settled + 1
                    if (nextPage < scenes.size) {
                        scope.launch {
                            pagerState.animateScrollToPage(nextPage)
                        }
                    }
                }
            }
        }
        player.addListener(listener)
    }

    // Clean up on dispose
    DisposableEffect(Unit) {
        onDispose {
            activityListener?.let { listener ->
                val settled = pagerState.settledPage
                if (scenes.isNotEmpty() && settled < scenes.size) {
                    listener.release(pool.getPlayer(settled).currentPosition)
                }
            }
            pool.releaseAll()
        }
    }

    // Request focus
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    // Helper to get the current settled player
    fun settledPlayer(): Player? {
        val settled = pagerState.settledPage
        if (scenes.isEmpty() || settled >= scenes.size) return null
        return pool.getPlayer(settled)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                // If overlay is open, let it handle keys
                if (activeOverlay != ReelsOverlay.NONE) {
                    if (event.key == Key.Back || event.key == Key.Escape) {
                        activeOverlay = ReelsOverlay.NONE
                        return@onPreviewKeyEvent true
                    }
                    return@onPreviewKeyEvent false
                }

                // Show controls on any key
                controlsVisible = true

                val player = settledPlayer()

                when (event.key) {
                    Key.DirectionUp -> {
                        if (pagerState.currentPage > 0) {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage - 1)
                            }
                        }
                        true
                    }
                    Key.DirectionDown -> {
                        if (pagerState.currentPage < scenes.size - 1) {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        }
                        true
                    }
                    Key.DirectionLeft -> {
                        if (player != null) {
                            val newPos = (player.currentPosition - skipBackMs).coerceAtLeast(0)
                            player.seekTo(newPos)
                        }
                        true
                    }
                    Key.DirectionRight -> {
                        if (player != null) {
                            val newPos = (player.currentPosition + skipForwardMs)
                                .coerceAtMost(player.duration.coerceAtLeast(0))
                            player.seekTo(newPos)
                        }
                        true
                    }
                    Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                        if (player != null) {
                            if (player.isPlaying) {
                                player.pause()
                                isPaused = true
                            } else {
                                player.play()
                                isPaused = false
                            }
                        }
                        true
                    }
                    Key.Back, Key.Escape -> {
                        navigationManager.goBack()
                        true
                    }
                    else -> false
                }
            },
    ) {
        // Video Pager - touch swipe enabled on non-TV, disabled on TV (use D-pad)
        VerticalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = !isTV && !isScrubbing,
            beyondViewportPageCount = 1,
            flingBehavior = PagerDefaults.flingBehavior(
                state = pagerState,
                pagerSnapDistance = PagerSnapDistance.atMost(1),
            ),
        ) { page ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .then(
                        if (isTouch) {
                            Modifier.pointerInput(Unit) {
                                detectTapGestures(
                                    onTap = {
                                        if (activeOverlay != ReelsOverlay.NONE) return@detectTapGestures
                                        val player = settledPlayer() ?: return@detectTapGestures
                                        if (player.isPlaying) {
                                            player.pause()
                                            isPaused = true
                                            controlsVisible = true
                                        } else {
                                            player.play()
                                            isPaused = false
                                        }
                                    },
                                    onDoubleTap = { offset ->
                                        if (activeOverlay != ReelsOverlay.NONE) return@detectTapGestures
                                        val player = settledPlayer() ?: return@detectTapGestures
                                        val width = size.width
                                        if (offset.x > width / 2) {
                                            val newPos = (player.currentPosition + skipForwardMs)
                                                .coerceAtMost(player.duration.coerceAtLeast(0))
                                            player.seekTo(newPos)
                                        } else {
                                            val newPos = (player.currentPosition - skipBackMs)
                                                .coerceAtLeast(0)
                                            player.seekTo(newPos)
                                        }
                                        controlsVisible = true
                                    },
                                )
                            }
                            .pointerInput(totalDuration) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { offset ->
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        isScrubbing = true
                                        val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                                        scrubPosition = (fraction * totalDuration).toLong()
                                        settledPlayer()?.seekTo(scrubPosition)
                                    },
                                    onDrag = { change, _ ->
                                        val fraction = (change.position.x / size.width).coerceIn(0f, 1f)
                                        scrubPosition = (fraction * totalDuration).toLong()
                                        settledPlayer()?.seekTo(scrubPosition)
                                    },
                                    onDragEnd = { isScrubbing = false },
                                    onDragCancel = { isScrubbing = false },
                                )
                            }
                        } else {
                            Modifier
                        },
                    ),
            ) {
                // Player surface - show on current page and adjacent pre-composed pages
                if (scenes.isNotEmpty() && page < scenes.size &&
                    abs(page - pagerState.settledPage) <= 1
                ) {
                    val pagePlayer = pool.getPlayer(page)
                    val presentationState = rememberPresentationState(pagePlayer)
                    PlayerSurface(
                        player = pagePlayer,
                        surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .resizeWithContentScale(
                                ContentScale.Fit,
                                presentationState.videoSizeDp,
                            ),
                    )
                }
                if (scenes.isEmpty()) {
                    Text(
                        text = stringResource(R.string.stashapp_studio_tagger_no_results_found),
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }
        }

        // Pause icon
        if (isPaused) {
            PauseIcon(modifier = Modifier.align(Alignment.Center))
        }

        // Top bar (auto-hide)
        ReelsTopBar(
            visible = controlsVisible || isPaused,
            filterName = currentFilter.name ?: "All Scenes",
            sortName = currentSort.sort.getString(context),
            isTV = isTV,
            onFilterClick = {
                activeOverlay = ReelsOverlay.FILTER
            },
            onSortClick = {
                activeOverlay = ReelsOverlay.SORT
            },
            onInfoClick = {
                if (scenes.isNotEmpty() && pagerState.currentPage < scenes.size) {
                    viewModel.loadSceneDetail(scenes[pagerState.currentPage].id)
                    activeOverlay = ReelsOverlay.INFO
                }
            },
        )

        // Bottom controls: metadata + progress bar, above navigation bar
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            // Bottom metadata (always visible)
            if (scenes.isNotEmpty() && pagerState.currentPage < scenes.size) {
                val scene = scenes[pagerState.currentPage]
                ReelsBottomMeta(
                    title = scene.titleOrFilename ?: "",
                    performer = scene.performers.firstOrNull()?.let { it.name } ?: "",
                )
            }

            // Progress bar (always visible)
            ReelsProgressBar(
                currentPosition = if (isScrubbing) scrubPosition else currentPosition,
                totalDuration = totalDuration,
                markerPositions = markerPositions,
                isPaused = isPaused,
                onSeek = { position ->
                    settledPlayer()?.seekTo(position)
                },
                isScrubbing = isScrubbing,
            )
        }

        // Overlays
        when (activeOverlay) {
            ReelsOverlay.INFO -> {
                BackHandler {
                    activeOverlay = ReelsOverlay.NONE
                    viewModel.clearSceneDetail()
                }
                ReelsInfoOverlay(
                    sceneData = sceneDetail,
                    onDismiss = {
                        activeOverlay = ReelsOverlay.NONE
                        viewModel.clearSceneDetail()
                    },
                    onNavigate = { destination ->
                        activeOverlay = ReelsOverlay.NONE
                        navigationManager.navigate(destination)
                    },
                    onSeekToMarker = { seconds ->
                        activeOverlay = ReelsOverlay.NONE
                        settledPlayer()?.seekTo((seconds * 1000).toLong())
                    },
                )
            }
            ReelsOverlay.SORT -> {
                BackHandler {
                    activeOverlay = ReelsOverlay.NONE
                }
                ReelsSortOverlay(
                    currentSort = currentSort,
                    onSelectSort = { sort ->
                        viewModel.setSort(sort)
                        activeOverlay = ReelsOverlay.NONE
                    },
                    onDismiss = {
                        activeOverlay = ReelsOverlay.NONE
                    },
                )
            }
            ReelsOverlay.FILTER -> {
                BackHandler {
                    activeOverlay = ReelsOverlay.NONE
                }
                ReelsFilterOverlay(
                    currentFilter = currentFilter,
                    savedFilters = savedFilters,
                    loop = loop,
                    autoAdvance = autoAdvance,
                    server = server,
                    onSelectSavedFilter = { savedFilter ->
                        viewModel.setSavedFilter(savedFilter)
                        activeOverlay = ReelsOverlay.NONE
                    },
                    onToggleLoop = { viewModel.toggleLoop() },
                    onToggleAutoAdvance = { viewModel.toggleAutoAdvance() },
                    onDismiss = {
                        activeOverlay = ReelsOverlay.NONE
                    },
                )
            }
            ReelsOverlay.NONE -> {}
        }
    }
}

@Composable
private fun PauseIcon(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(56.dp)
            .background(Color.Black.copy(alpha = 0.3f), androidx.compose.foundation.shape.CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                Modifier
                    .size(width = 6.dp, height = 24.dp)
                    .background(Color.White.copy(alpha = 0.8f)),
            )
            Box(
                Modifier
                    .size(width = 6.dp, height = 24.dp)
                    .background(Color.White.copy(alpha = 0.8f)),
            )
        }
    }
}
