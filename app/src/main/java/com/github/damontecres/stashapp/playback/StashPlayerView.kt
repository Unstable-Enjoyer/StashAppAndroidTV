package com.github.damontecres.stashapp.playback

import android.content.Context
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.annotation.OptIn
import androidx.core.view.GestureDetectorCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.findFragment
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.ui.PlayerView
import androidx.preference.PreferenceManager
import com.github.damontecres.stashapp.views.SkipIndicator

/**
 * A [PlayerView] which overrides button presses and supports double-tap to seek
 */
class StashPlayerView(
    context: Context,
    attrs: AttributeSet?,
    defStyleAttr: Int,
) : PlayerView(context, attrs, defStyleAttr) {
    constructor(context: Context, attrs: AttributeSet? = null) : this(context, attrs, 0)

    constructor(context: Context) : this(context, null)

    private val dPadSkipEnabled: Boolean =
        PreferenceManager.getDefaultSharedPreferences(context).getBoolean("skipWithDpad", true)

    private val doubleTapSeekEnabled: Boolean =
        PreferenceManager.getDefaultSharedPreferences(context)
            .getBoolean(PREF_KEY_DOUBLE_TAP_SEEK, true)

    var skipIndicator: SkipIndicator? = null
    var doubleTapSeekOverlay: DoubleTapSeekOverlay? = null

    private val gestureDetector = GestureDetectorCompat(context, DoubleTapGestureListener())

    @OptIn(UnstableApi::class)
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (!isControllerFullyVisible) {
            // Controller hidden: gesture detector handles all touches.
            // Consume event to prevent PlayerView's onTouchEvent from
            // immediately showing the controller (which kills double-tap).
            gestureDetector.onTouchEvent(event)
            return true
        }
        // Controller visible: let super route to buttons/seekbar
        return super.dispatchTouchEvent(event)
    }

    @OptIn(UnstableApi::class)
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val keyCode = event.keyCode
        val fragment = findFragment<Fragment>() as PlaybackFragment
        if (player != null &&
            (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        ) {
            if (event.action == KeyEvent.ACTION_UP) {
                val isPaused = !player!!.isPlaying
                if (isPaused) {
                    when (keyCode) {
                        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                            Util.handlePlayPauseButtonAction(
                                player,
                                true,
                            )
                        }

                        KeyEvent.KEYCODE_MEDIA_PLAY -> {
                            Util.handlePlayButtonAction(player)
                        }
                    }
                    return true
                } else if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
                    player!!.pause()
                    showController()
                    return true
                } else {
                    // Not paused, so allow normal handling
                    return super.dispatchKeyEvent(event)
                }
            } else {
                return true
            }
        } else if (player != null &&
            !fragment.isControllerVisible &&
            (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT || keyCode == KeyEvent.KEYCODE_DPAD_LEFT)
        ) {
            if (event.action == KeyEvent.ACTION_UP) {
                if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    if (dPadSkipEnabled) {
                        player!!.seekForward()
                        skipIndicator?.update(player!!.seekForwardIncrement)
                    } else {
                        fragment.showAndFocusSeekBar()
                    }
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                    if (dPadSkipEnabled) {
                        player!!.seekBack()
                        skipIndicator?.update(-1 * player!!.seekBackIncrement)
                    } else {
                        fragment.showAndFocusSeekBar()
                    }
                }
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    @OptIn(UnstableApi::class)
    private inner class DoubleTapGestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            // Toggle controller visibility on single tap
            if (isControllerFullyVisible) {
                hideController()
            } else {
                showController()
            }
            performClick()
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (!doubleTapSeekEnabled) return false
            val currentPlayer = player ?: return false
            val isForward = e.x > width / 2

            if (isForward) {
                currentPlayer.seekForward()
                val seekSeconds = currentPlayer.seekForwardIncrement / 1000
                doubleTapSeekOverlay?.showSeek(true, seekSeconds)
            } else {
                currentPlayer.seekBack()
                val seekSeconds = currentPlayer.seekBackIncrement / 1000
                doubleTapSeekOverlay?.showSeek(false, seekSeconds)
            }
            return true
        }

        override fun onDown(e: MotionEvent): Boolean {
            // Must return true for other gesture events to fire
            return true
        }
    }

    companion object {
        const val TAG = "StashPlayerView"
        const val PREF_KEY_DOUBLE_TAP_SEEK = "doubleTapSeek"
    }
}
