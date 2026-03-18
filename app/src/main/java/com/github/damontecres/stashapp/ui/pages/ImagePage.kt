package com.github.damontecres.stashapp.ui.pages

import android.annotation.SuppressLint
import android.util.Log
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorMatrixColorFilter
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_SURFACE_VIEW
import androidx.media3.ui.compose.modifiers.resizeWithContentScale
import androidx.media3.ui.compose.state.rememberPresentationState
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.SubcomposeAsyncImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Size
import me.saket.telephoto.zoomable.ZoomSpec
import me.saket.telephoto.zoomable.ZoomableState
import me.saket.telephoto.zoomable.coil3.ZoomableAsyncImage
import me.saket.telephoto.zoomable.rememberZoomableImageState
import me.saket.telephoto.zoomable.rememberZoomableState
import me.saket.telephoto.zoomable.zoomable
import com.github.damontecres.stashapp.R
import com.github.damontecres.stashapp.StashExoPlayer
import com.github.damontecres.stashapp.api.fragment.PerformerData
import com.github.damontecres.stashapp.api.fragment.TagData
import com.github.damontecres.stashapp.data.VideoFilter
import com.github.damontecres.stashapp.navigation.NavigationManagerCompose
import com.github.damontecres.stashapp.playback.maybeMuteAudio
import com.github.damontecres.stashapp.suppliers.FilterArgs
import com.github.damontecres.stashapp.ui.AppColors
import com.github.damontecres.stashapp.ui.ComposeUiConfig
import com.github.damontecres.stashapp.ui.compat.isNotTvDevice
import com.github.damontecres.stashapp.ui.components.ItemOnClicker
import com.github.damontecres.stashapp.ui.components.LongClicker
import com.github.damontecres.stashapp.ui.components.image.DRAG_THROTTLE_DELAY
import com.github.damontecres.stashapp.ui.components.image.ImageDetailsViewModel
import com.github.damontecres.stashapp.ui.components.image.ImageFilterDialog
import com.github.damontecres.stashapp.ui.components.image.ImageLoadingPlaceholder
import com.github.damontecres.stashapp.ui.components.image.ImageOverlay
import com.github.damontecres.stashapp.ui.components.image.SlideshowControls
import com.github.damontecres.stashapp.ui.components.playback.isDirectionalDpad
import com.github.damontecres.stashapp.ui.components.playback.isDpad
import com.github.damontecres.stashapp.ui.components.playback.isEnterKey
import com.github.damontecres.stashapp.ui.tryRequestFocus
import com.github.damontecres.stashapp.ui.util.applyColorMatrix
import com.github.damontecres.stashapp.ui.util.ifElse
import com.github.damontecres.stashapp.util.StashServer
import com.github.damontecres.stashapp.util.findActivity
import com.github.damontecres.stashapp.util.isImageClip
import com.github.damontecres.stashapp.util.isNotNullOrBlank
import com.github.damontecres.stashapp.util.keepScreenOn
import androidx.compose.animation.core.snap
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

private const val TAG = "ImagePage"
private const val DEBUG = false

@SuppressLint("ConfigurationScreenWidthHeight")
@OptIn(UnstableApi::class)
@Composable
fun ImagePage(
    server: StashServer,
    navigationManager: NavigationManagerCompose,
    filter: FilterArgs,
    startPosition: Int,
    startSlideshow: Boolean,
    itemOnClick: ItemOnClicker<Any>,
    longClicker: LongClicker<Any>,
    uiConfig: ComposeUiConfig,
    modifier: Modifier = Modifier,
    viewModel: ImageDetailsViewModel = viewModel(),
) {
    val context = LocalContext.current
    val isNotTvDevice = isNotTvDevice
    val swipeGallery = !uiConfig.preferences.interfacePreferences.swipeGalleryDisabled
    LaunchedEffect(server, filter) {
        val slideshowDelay = uiConfig.preferences.interfacePreferences.slideShowIntervalMs

        viewModel.init(
            server,
            filter,
            startPosition,
            startSlideshow,
            slideshowDelay,
            uiConfig.persistVideoFilters,
            useHorizontalPager = swipeGallery && isNotTvDevice,
        )
        if (isNotTvDevice) {
            // Reduce the throttling for touch devices since a delay when dragging feels like lag
            viewModel.imageFilter.startThrottling(DRAG_THROTTLE_DELAY)
        }
    }

    val imageState by viewModel.image.observeAsState()
    val tags by viewModel.tags.observeAsState(listOf())
    val performers by viewModel.performers.observeAsState(listOf())
    val galleries by viewModel.galleries.observeAsState(listOf())
    val rating100 by viewModel.rating100.observeAsState(0)
    val oCount by viewModel.oCount.observeAsState(0)
    val imageFilter by viewModel.imageFilter.observeAsState(VideoFilter())
    val position by viewModel.position.observeAsState(0)
    val pager by viewModel.pager.observeAsState()

    var zoomFactor by rememberSaveable { mutableFloatStateOf(1f) }
    val isZoomed = zoomFactor * 100 > 102
    var rotation by rememberSaveable { mutableFloatStateOf(0f) }
    var showOverlay by rememberSaveable { mutableStateOf(false) }
    var showFilterDialog by rememberSaveable { mutableStateOf(false) }
    var panX by rememberSaveable { mutableFloatStateOf(0f) }
    var panY by rememberSaveable { mutableFloatStateOf(0f) }
    val galleryId by viewModel.galleryId.observeAsState(null)

    val slideshowControls =
        object : SlideshowControls {
            override fun startSlideshow() {
                showOverlay = false
                viewModel.startSlideshow()
            }

            override fun stopSlideshow() {
                viewModel.stopSlideshow()
            }
        }

    val rotateAnimation: Float by animateFloatAsState(
        targetValue = rotation,
        label = "image_rotation",
    )
    val zoomAnimation: Float by animateFloatAsState(
        targetValue = zoomFactor,
        label = "image_zoom",
    )
    val panXAnimation: Float by animateFloatAsState(
        targetValue = panX,
        label = "image_panX",
    )
    val panYAnimation: Float by animateFloatAsState(
        targetValue = panY,
        label = "image_panY",
    )

    val state =
        rememberTransformableState { zoomChange, offsetChange, rotationChange ->
            zoomFactor *= zoomChange
            rotation += rotationChange
            panX += offsetChange.x
            panY += offsetChange.y
        }

    val slideshowEnabled by viewModel.slideshow.observeAsState(false)
    val slideshowActive by viewModel.slideshowActive.observeAsState(false)

    val focusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    var pagerStateRef by remember { mutableStateOf<PagerState?>(null) }
    var activeZoomableState by remember { mutableStateOf<ZoomableState?>(null) }

    val usesTelephoto = swipeGallery && isNotTvDevice
    val effectiveIsZoomed = if (usesTelephoto && activeZoomableState != null) {
        activeZoomableState?.let { it.zoomFraction != null && it.zoomFraction!! > 0f } ?: false
    } else {
        isZoomed
    }

    LaunchedEffect(Unit) {
        focusRequester.tryRequestFocus()
    }

    val density = LocalDensity.current
    val screenHeight = LocalWindowInfo.current.containerSize.height
    val screenWidth = LocalWindowInfo.current.containerSize.width

    val maxPanX = screenWidth * .75f
    val maxPanY = screenHeight * .75f

    fun reset(resetRotate: Boolean) {
        zoomFactor = 1f
        panX = 0f
        panY = 0f
        if (resetRotate) rotation = 0f
    }

    fun pan(
        xFactor: Int,
        yFactor: Int,
    ) {
        if (xFactor != 0) {
            panX = (panX + with(density) { xFactor.dp.toPx() }).coerceIn(-maxPanX, maxPanX)
        }
        if (yFactor != 0) {
            panY = (panY + with(density) { yFactor.dp.toPx() }).coerceIn(-maxPanY, maxPanY)
        }
    }

    fun zoom(factor: Float) {
        if (factor < 0) {
            val diffFactor = factor / (zoomFactor - 1f)
            // zooming out
            val panXDiff = abs(panX * diffFactor)
            val panYDiff = abs(panY * diffFactor)
            if (DEBUG) {
                Log.d(
                    TAG,
                    "zoomFactor=$zoomFactor, factor=$factor, panX=$panX, panY=$panY, panXDiff=$panXDiff, panYDiff=$panYDiff",
                )
            }
            if (panX > 0f) {
                panX -= panXDiff
            } else if (panX < 0f) {
                panX += panXDiff
            }
            if (panY > 0f) {
                panY -= panYDiff
            } else if (panY < 0f) {
                panY += panYDiff
            }
        }
        zoomFactor = (zoomFactor + factor).coerceIn(1f, 5f)
        if (!isZoomed) {
            // Always reset if not zoomed
            panX = 0f
            panY = 0f
        }
    }

    fun handleZoom(factor: Float) {
        if (usesTelephoto && activeZoomableState != null) {
            scope.launch {
                activeZoomableState?.zoomBy(
                    zoomFactor = 1f + factor,
                    centroid = Offset(screenWidth / 2f, screenHeight / 2f),
                )
            }
        } else {
            zoom(factor)
        }
    }

    fun handleReset(resetRotate: Boolean) {
        if (usesTelephoto && activeZoomableState != null) {
            scope.launch { activeZoomableState?.resetZoom() }
            if (resetRotate) rotation = 0f
        } else {
            reset(resetRotate)
        }
    }

    LaunchedEffect(imageState) {
        if (!usesTelephoto) {
            reset(true)
        }
    }
    val player =
        remember {
            StashExoPlayer.getInstance(context, server).apply {
                maybeMuteAudio(uiConfig.preferences, false, this)
                repeatMode = Player.REPEAT_MODE_OFF
                playWhenReady = true
            }
        }
    LifecycleStartEffect(Unit) {
        onStopOrDispose {
            StashExoPlayer.releasePlayer()
        }
    }

    val playSlideshowDelay = uiConfig.preferences.interfacePreferences.slideShowIntervalMs
    val presentationState = rememberPresentationState(player)
    LaunchedEffect(player) {
        StashExoPlayer.addListener(
            object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) {
                        viewModel.pulseSlideshow(playSlideshowDelay)
                    }
                }
            },
        )
    }
    LaunchedEffect(slideshowActive) {
        player.repeatMode = if (slideshowEnabled) Player.REPEAT_MODE_OFF else Player.REPEAT_MODE_ONE
        context.findActivity()?.keepScreenOn(slideshowActive)
    }
    DisposableEffect(Unit) {
        onDispose {
            context.findActivity()?.keepScreenOn(false)
        }
    }

    var longPressing by remember { mutableStateOf(false) }

    var dragXAmount by remember { mutableFloatStateOf(0f) }

    // TODO move content into a function
    val contentModifier =
        Modifier.ifElse(
            isNotTvDevice,
            if (swipeGallery) {
                Modifier
                    .pointerInput(isZoomed) {
                        detectTapGestures(
                            onTap = {
                                showOverlay = !showOverlay
                            },
                            onDoubleTap = {
                                if (!showOverlay) {
                                    if (isZoomed) {
                                        reset(false)
                                    } else {
                                        zoom(1.5f)
                                    }
                                }
                            },
                        )
                    }.ifElse(
                        condition = isZoomed || showOverlay,
                        Modifier
                            .transformable(
                                state = state,
                                enabled = !showOverlay,
                                lockRotationOnZoomPan = true,
                            ),
                    )
            } else {
                Modifier
                    .clickable(
                        interactionSource = null,
                        indication = null,
                        onClick = {
                            showOverlay = !showOverlay
                        },
                    ).pointerInput(isZoomed) {
                        detectTapGestures(
                            onTap = {
                                showOverlay = !showOverlay
                            },
                            onDoubleTap = {
                                if (!showOverlay) {
                                    if (isZoomed) {
                                        reset(false)
                                    } else {
                                        zoom(1.5f)
                                    }
                                }
                            },
                        )
                    }.ifElse(
                        condition = isZoomed || showOverlay,
                        Modifier
                            .transformable(
                                state = state,
                                enabled = !showOverlay,
                                lockRotationOnZoomPan = true,
                            ),
                        Modifier
                            .transformable(state, lockRotationOnZoomPan = true)
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { dragXAmount = 0f },
                                    onDragCancel = { dragXAmount = 0f },
                                    onDragEnd = {
                                        if (dragXAmount > 300f) {
                                            viewModel.previousImage()
                                        } else if (dragXAmount < -300f) {
                                            viewModel.nextImage()
                                        }
                                        dragXAmount = 0f
                                    },
                                ) { change, dragAmount ->
                                    dragXAmount += dragAmount.x
                                    change.consume()
                                }
                            },
                    )
            },
        )

    Box(
        modifier =
            modifier
                .background(Color.Black)
                .focusRequester(focusRequester)
                .focusable()
                .onKeyEvent {
                    val isOverlayShowing = showOverlay || showFilterDialog
                    var result = false
                    if (!isOverlayShowing) {
                        if (longPressing && it.type == KeyEventType.KeyUp) {
                            // User stopped long pressing, so cancel the zooming action, but still consume the event so it doesn't move the image
                            longPressing = false
                            return@onKeyEvent true
                        }
                        longPressing =
                            it.nativeKeyEvent.isLongPress ||
                            it.nativeKeyEvent.repeatCount > 0
                        if (longPressing) {
                            when (it.key) {
                                Key.DirectionUp -> handleZoom(.05f)
                                Key.DirectionDown -> handleZoom(-.05f)

                                // These work, but feel awkward because Up/Down zoom, so you can't long press them to pan
                                // Key.DirectionLeft -> panX += with(density) { 15.dp.toPx() }
                                // Key.DirectionRight -> panX -= with(density) { 15.dp.toPx() }
                            }
                            return@onKeyEvent true
                        }
                    }
                    if (it.type != KeyEventType.KeyUp) {
                        result = false
                    } else if (!isOverlayShowing && effectiveIsZoomed && isDirectionalDpad(it)) {
                        // Image is zoomed in
                        if (usesTelephoto) {
                            val panAmount = with(density) { 30.dp.toPx() }
                            scope.launch {
                                when (it.key) {
                                    Key.DirectionLeft -> activeZoomableState?.panBy(Offset(panAmount, 0f))
                                    Key.DirectionRight -> activeZoomableState?.panBy(Offset(-panAmount, 0f))
                                    Key.DirectionUp -> activeZoomableState?.panBy(Offset(0f, panAmount))
                                    Key.DirectionDown -> activeZoomableState?.panBy(Offset(0f, -panAmount))
                                }
                            }
                        } else {
                            when (it.key) {
                                Key.DirectionLeft -> pan(30, 0)
                                Key.DirectionRight -> pan(-30, 0)
                                Key.DirectionUp -> pan(0, 30)
                                Key.DirectionDown -> pan(0, -30)
                            }
                        }
                        result = true
                    } else if (!isOverlayShowing && effectiveIsZoomed && it.key == Key.Back) {
                        handleReset(false)
                        result = true
                    } else if (!isOverlayShowing && (it.key == Key.DirectionLeft || it.key == Key.DirectionRight)) {
                        val ps = pagerStateRef
                        when (it.key) {
                            Key.DirectionLeft, Key.DirectionUpLeft, Key.DirectionDownLeft -> {
                                if (ps != null) {
                                    val target = ps.currentPage - 1
                                    if (target >= 0) {
                                        scope.launch { ps.animateScrollToPage(target) }
                                    } else {
                                        Toast
                                            .makeText(
                                                context,
                                                R.string.slideshow_at_beginning,
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                    }
                                } else if (!viewModel.previousImage()) {
                                    Toast
                                        .makeText(
                                            context,
                                            R.string.slideshow_at_beginning,
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                }
                            }

                            Key.DirectionRight, Key.DirectionUpRight, Key.DirectionDownRight -> {
                                if (ps != null) {
                                    val target = ps.currentPage + 1
                                    if (target < ps.pageCount) {
                                        scope.launch { ps.animateScrollToPage(target) }
                                    } else {
                                        Toast
                                            .makeText(
                                                context,
                                                R.string.no_more_images,
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                    }
                                } else if (!viewModel.nextImage()) {
                                    Toast
                                        .makeText(
                                            context,
                                            R.string.no_more_images,
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                }
                            }
                        }
                    } else if (isOverlayShowing && it.key == Key.Back) {
                        showOverlay = false
                        viewModel.unpauseSlideshow()
                        result = true
                    } else if (!isOverlayShowing && (isDpad(it) || isEnterKey(it))) {
                        showOverlay = true
                        viewModel.pauseSlideshow()
                        result = true
                    }
                    if (result) {
                        // Handled the key, so reset the slideshow timer
                        viewModel.pulseSlideshow()
                    }
                    result
                },
    ) {
        imageState?.let { image ->
            val imageContent: @Composable () -> Unit = {
                if (image.paths.image.isNotNullOrBlank()) {
                    if (image.isImageClip) {
                        LaunchedEffect(image.id) {
                            val mediaItem =
                                MediaItem
                                    .Builder()
                                    .setUri(image.paths.image)
                                    .build()
                            player.setMediaItem(mediaItem)
                            player.repeatMode =
                                if (slideshowEnabled) {
                                    Player.REPEAT_MODE_OFF
                                } else {
                                    Player.REPEAT_MODE_ONE
                                }
                            player.prepare()
                            player.play()
                            viewModel.pulseSlideshow(Long.MAX_VALUE)
                        }
                        LifecycleStartEffect(Unit) {
                            onStopOrDispose {
                                player.stop()
                            }
                        }
                        val contentScale = ContentScale.Fit
                        val scaledModifier =
                            contentModifier.resizeWithContentScale(
                                contentScale,
                                presentationState.videoSizeDp,
                            )
                        PlayerSurface(
                            player = player,
                            surfaceType = SURFACE_TYPE_SURFACE_VIEW,
                            modifier =
                                scaledModifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        scaleX = zoomAnimation
                                        scaleY = zoomAnimation
                                        translationX = panXAnimation
                                        translationY = panYAnimation
                                    }.rotate(rotateAnimation),
                        )
                        if (presentationState.coverSurface) {
                            Box(
                                Modifier
                                    .matchParentSize()
                                    .background(Color.Black),
                            )
                        }
                    } else {
                        val colorFilter =
                            remember(image.id, imageFilter) {
                                if (imageFilter.hasImageFilter()) {
                                    ColorMatrixColorFilter(imageFilter.createComposeColorMatrix())
                                } else {
                                    null
                                }
                            }
                        val showLoadingThumbnail = image.paths.thumbnail.isNotNullOrBlank()
                        SubcomposeAsyncImage(
                            modifier =
                                contentModifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        scaleX = zoomAnimation
                                        scaleY = zoomAnimation
                                        translationX = panXAnimation
                                        translationY = panYAnimation

                                        val xTransform =
                                            (screenWidth - panXAnimation) / (screenWidth * 2)
                                        val yTransform =
                                            (screenHeight - panYAnimation) / (screenHeight * 2)
                                        if (DEBUG) {
                                            Log.d(
                                                TAG,
                                                "graphicsLayer: xTransform=$xTransform, yTransform=$yTransform",
                                            )
                                        }

                                        transformOrigin = TransformOrigin(xTransform, yTransform)
                                    }.rotate(rotateAnimation),
                            model =
                                ImageRequest
                                    .Builder(LocalContext.current)
                                    .data(image.paths.image)
                                    .size(Size.ORIGINAL)
                                    .crossfade(false)
                                    .build(),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            colorFilter = colorFilter,
                            error = {
                                Text(
                                    modifier =
                                        Modifier
                                            .align(Alignment.Center),
                                    text = "Error loading image",
                                    color = MaterialTheme.colorScheme.onBackground,
                                )
                            },
                            loading = {
                                ImageLoadingPlaceholder(
                                    thumbnailUrl = image.paths.thumbnail,
                                    showThumbnail = showLoadingThumbnail,
                                    colorFilter = colorFilter,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            },
                            // Ensure that if an image takes a long time to load, it won't be skipped
                            onLoading = {
                                viewModel.pulseSlideshow(Long.MAX_VALUE)
                            },
                            onSuccess = {
                                viewModel.pulseSlideshow()
                            },
                            onError = {
                                Log.e(TAG, "Error loading image ${image.id}", it.result.throwable)
                                Toast
                                    .makeText(
                                        context,
                                        "Error loading image: ${it.result.throwable.localizedMessage}",
                                        Toast.LENGTH_LONG,
                                    ).show()
                                viewModel.pulseSlideshow()
                            },
                        )
                    }
                } else {
                    // TODO
                    Text("No image URL")
                }
            }

            if (swipeGallery && isNotTvDevice) {
                // HorizontalPager path — persistent composition per page
                val currentPager = pager
                if (currentPager != null) {
                    // Key on currentPager so that pagerState is recreated when sort/filter changes
                    val pagerState = key(currentPager) {
                        rememberPagerState(
                            initialPage = 0,
                            pageCount = { currentPager.size.coerceAtLeast(0) },
                        )
                    }
                    pagerStateRef = pagerState

                    LaunchedEffect(currentPager) {
                        viewModel.scrollToPage.collect { targetPage ->
                            if (targetPage in 0 until pagerState.pageCount) {
                                pagerState.animateScrollToPage(targetPage)
                            }
                        }
                    }

                    LaunchedEffect(pagerState) {
                        snapshotFlow { pagerState.settledPage }
                            .distinctUntilChanged()
                            .collect { page ->
                                viewModel.updatePosition(page)
                                rotation = 0f
                            }
                    }

                    // Prefetch thumbnails for N±3 (beyond composed viewport of N±2)
                    LaunchedEffect(pagerState.settledPage) {
                        val currentPage = pagerState.settledPage
                        val pagerSize = currentPager.size
                        if (pagerSize <= 0) return@LaunchedEffect
                        val imageLoader = context.imageLoader
                        for (offset in listOf(-3, 3)) {
                            launch {
                                val targetPage = currentPage + offset
                                if (targetPage in 0 until pagerSize) {
                                    val pageData = currentPager.get(targetPage)
                                    val thumbnailUrl = pageData?.paths?.thumbnail
                                    if (thumbnailUrl.isNotNullOrBlank()) {
                                        imageLoader.enqueue(
                                            ImageRequest.Builder(context)
                                                .data(thumbnailUrl)
                                                .crossfade(false)
                                                .build()
                                        )
                                    }
                                }
                            }
                        }
                    }

                HorizontalPager(
                    state = pagerState,
                    beyondViewportPageCount = 2,
                    flingBehavior = PagerDefaults.flingBehavior(
                        state = pagerState,
                        pagerSnapDistance = PagerSnapDistance.atMost(1),
                    ),
                    userScrollEnabled = !effectiveIsZoomed && !showOverlay,
                    modifier = Modifier.fillMaxSize(),
                ) { pageIndex ->
                    val pageImageData = currentPager.get(pageIndex)
                    val isSettledPage = pageIndex == pagerState.settledPage

                    if (pageImageData != null) {
                        val pageImage = pageImageData

                        if (pageImage.paths.image.isNotNullOrBlank()) {
                            if (pageImage.isImageClip && isSettledPage) {
                                LaunchedEffect(pageImage.id) {
                                    val mediaItem =
                                        MediaItem
                                            .Builder()
                                            .setUri(pageImage.paths.image)
                                            .build()
                                    player.setMediaItem(mediaItem)
                                    player.repeatMode =
                                        if (slideshowEnabled) {
                                            Player.REPEAT_MODE_OFF
                                        } else {
                                            Player.REPEAT_MODE_ONE
                                        }
                                    player.prepare()
                                    player.play()
                                    viewModel.pulseSlideshow(Long.MAX_VALUE)
                                }
                                LifecycleStartEffect(Unit) {
                                    onStopOrDispose {
                                        player.stop()
                                    }
                                }

                                LaunchedEffect(Unit) {
                                    activeZoomableState = null
                                }

                                Box(Modifier.fillMaxSize()) {
                                    ImageLoadingPlaceholder(
                                        thumbnailUrl = pageImage.paths.thumbnail,
                                        showThumbnail = pageImage.paths.thumbnail.isNotNullOrBlank(),
                                        colorFilter = null,
                                        modifier = Modifier.fillMaxSize(),
                                    )

                                    val contentScale = ContentScale.Fit
                                    PlayerSurface(
                                        player = player,
                                        surfaceType = SURFACE_TYPE_SURFACE_VIEW,
                                        modifier =
                                            Modifier
                                                .resizeWithContentScale(
                                                    contentScale,
                                                    presentationState.videoSizeDp,
                                                )
                                                .fillMaxSize()
                                                .pointerInput(Unit) {
                                                    detectTapGestures(onTap = { showOverlay = !showOverlay })
                                                }
                                                .graphicsLayer {
                                                    scaleX = zoomAnimation
                                                    scaleY = zoomAnimation
                                                    translationX = panXAnimation
                                                    translationY = panYAnimation
                                                }
                                                .rotate(rotateAnimation),
                                    )
                                }
                            } else if (pageImage.isImageClip) {
                                // Non-settled clip — show thumbnail with spinner fallback
                                ImageLoadingPlaceholder(
                                    thumbnailUrl = pageImage.paths.thumbnail,
                                    showThumbnail = pageImage.paths.thumbnail.isNotNullOrBlank(),
                                    colorFilter = null,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            } else {
                                // Static image — use ZoomableAsyncImage with sub-sampling
                                val pageZoomableState = rememberZoomableState(
                                    zoomSpec = ZoomSpec(maxZoomFactor = 5f)
                                )
                                val telephotoImageState = rememberZoomableImageState(pageZoomableState)

                                if (isSettledPage) {
                                    LaunchedEffect(pageZoomableState) {
                                        activeZoomableState = pageZoomableState
                                    }
                                    DisposableEffect(pageZoomableState) {
                                        onDispose {
                                            if (activeZoomableState === pageZoomableState) {
                                                activeZoomableState = null
                                            }
                                        }
                                    }
                                }

                                if (!isSettledPage) {
                                    LaunchedEffect(Unit) {
                                        pageZoomableState.resetZoom(animationSpec = snap())
                                    }
                                }

                                if (isSettledPage) {
                                    LaunchedEffect(telephotoImageState) {
                                        viewModel.pulseSlideshow(Long.MAX_VALUE)
                                        snapshotFlow { telephotoImageState.isImageDisplayed }
                                            .distinctUntilChanged()
                                            .collect { displayed ->
                                                if (displayed) viewModel.pulseSlideshow()
                                            }
                                    }
                                    LaunchedEffect(pageImage.id) {
                                        delay(10_000)
                                        if (!telephotoImageState.isImageDisplayed) {
                                            Log.e(TAG, "Image ${pageImage.id} did not load within timeout")
                                            viewModel.pulseSlideshow()
                                        }
                                    }
                                }

                                val colorFilterMatrix = if (isSettledPage) {
                                    remember(pageImage.id, imageFilter) {
                                        if (imageFilter.hasImageFilter()) imageFilter.createComposeColorMatrix() else null
                                    }
                                } else null

                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .ifElse(isSettledPage, Modifier.rotate(rotateAnimation))
                                        .applyColorMatrix(colorFilterMatrix)
                                ) {
                                    // Thumbnail always present as base layer
                                    ImageLoadingPlaceholder(
                                        thumbnailUrl = pageImage.paths.thumbnail,
                                        showThumbnail = pageImage.paths.thumbnail.isNotNullOrBlank(),
                                        colorFilter = null,
                                        modifier = Modifier.fillMaxSize(),
                                    )

                                    // Load full-res for all composed pages (N±2) so it's
                                    // already rendered when the user swipes to an adjacent page
                                    val model = remember(pageImage.id) {
                                        ImageRequest.Builder(context)
                                            .data(pageImage.paths.image)
                                            .crossfade(false)
                                            .build()
                                    }
                                    ZoomableAsyncImage(
                                        model = model,
                                        contentDescription = null,
                                        state = telephotoImageState,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.fillMaxSize(),
                                        onClick = { showOverlay = !showOverlay },
                                    )
                                }
                            }
                        } else {
                            // No image URL
                            ImageLoadingPlaceholder(
                                thumbnailUrl = pageImage.paths.thumbnail,
                                showThumbnail = pageImage.paths.thumbnail.isNotNullOrBlank(),
                                colorFilter = null,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    } else {
                        ImageLoadingPlaceholder(
                            thumbnailUrl = null,
                            showThumbnail = false,
                            colorFilter = null,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                } else {
                    // Show loading placeholder while pager initializes
                    ImageLoadingPlaceholder(
                        thumbnailUrl = null,
                        showThumbnail = false,
                        colorFilter = null,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            } else {
                imageContent()
            }
            val focusManager = LocalFocusManager.current
            AnimatedVisibility(
                showOverlay,
                enter = slideInVertically { it },
                exit = slideOutVertically { it },
            ) {
                ImageOverlay(
                    modifier =
                        contentModifier
                            .fillMaxSize()
                            .background(AppColors.TransparentBlack50),
                    server = server,
                    player = player,
                    slideshowControls = slideshowControls,
                    slideshowEnabled = slideshowEnabled,
                    image = image,
                    tags = tags,
                    performers = performers,
                    galleries = galleries,
                    position = position,
                    count = pager?.size ?: -1,
                    itemOnClick = itemOnClick,
                    longClicker = longClicker,
                    onZoom = ::handleZoom,
                    onRotate = { rotation += it },
                    onReset = { handleReset(true) },
                    rating100 = rating100,
                    oCount = oCount,
                    uiConfig = uiConfig,
                    oCountAction = viewModel::updateOCount,
                    onRatingChange = { viewModel.updateRating(image.id, it) },
                    addItem = { item ->
                        when (item) {
                            is TagData -> {
                                viewModel.addTag(image.id, item.id)
                            }

                            is PerformerData -> {
                                viewModel.addPerformer(image.id, item.id)
                            }

                            else -> {}
                        }
                    },
                    removeItem = { item ->
                        focusManager.moveFocus(FocusDirection.Previous)
                        when (item) {
                            is TagData -> {
                                viewModel.removeTag(image.id, item.id)
                            }

                            is PerformerData -> {
                                viewModel.removePerformer(image.id, item.id)
                            }

                            else -> {}
                        }
                    },
                    onShowFilterDialogClick = {
                        showFilterDialog = true
                        showOverlay = false
                        viewModel.pauseSlideshow()
                    },
                    currentSort = viewModel.currentFilter.sortAndDirection,
                    onSortChange = { newSort ->
                        val newFilter = viewModel.currentFilter.with(newSort).withResolvedRandom()
                        viewModel.init(
                            server,
                            newFilter,
                            startPosition = 0,
                            slideshow = false,
                            slideshowDelay = uiConfig.preferences.interfacePreferences.slideShowIntervalMs,
                            saveFilters = uiConfig.persistVideoFilters,
                            useHorizontalPager = swipeGallery && isNotTvDevice,
                        )
                    },
                )
            }
            AnimatedVisibility(showFilterDialog) {
                ImageFilterDialog(
                    filter = imageFilter,
                    showVideoOptions = false,
                    showSaveGalleryButton = galleryId != null,
                    uiConfig = uiConfig,
                    onChange = viewModel::updateImageFilter,
                    onClickSave = viewModel::saveImageFilter,
                    onClickSaveGallery = viewModel::saveGalleryFilter,
                    onDismissRequest = {
                        showFilterDialog = false
                        viewModel.unpauseSlideshow()
                        viewModel.pulseSlideshow()
                    },
                )
            }
        }
    }
}
