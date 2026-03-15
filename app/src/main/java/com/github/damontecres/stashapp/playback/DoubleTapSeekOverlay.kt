package com.github.damontecres.stashapp.playback

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.github.damontecres.stashapp.R

/**
 * Overlay that shows YouTube-style double-tap seek feedback.
 *
 * Displays a semi-transparent overlay on the left or right half of the screen
 * with cumulative seek seconds text and a directional arrow icon.
 */
class DoubleTapSeekOverlay(
    context: Context,
    attrs: AttributeSet?,
    defStyleAttr: Int,
) : FrameLayout(context, attrs, defStyleAttr) {
    constructor(context: Context) : this(context, null, 0)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)

    private val leftContainer: LinearLayout
    private val rightContainer: LinearLayout
    private val leftText: TextView
    private val rightText: TextView

    private val handler = Handler(Looper.getMainLooper())
    private var hideRunnable: Runnable? = null

    private var cumulativeSeconds = 0L
    private var currentSide: Side? = null

    private enum class Side { LEFT, RIGHT }

    init {
        inflate(context, R.layout.double_tap_seek_overlay, this)
        leftContainer = findViewById(R.id.seek_left_container)
        rightContainer = findViewById(R.id.seek_right_container)
        leftText = findViewById(R.id.seek_left_text)
        rightText = findViewById(R.id.seek_right_text)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // Each side covers half the screen width
        val halfWidth = w / 2
        leftContainer.layoutParams = (leftContainer.layoutParams as LayoutParams).apply {
            width = halfWidth
        }
        rightContainer.layoutParams = (rightContainer.layoutParams as LayoutParams).apply {
            width = halfWidth
        }
    }

    /**
     * Show seek feedback on the given side with the given number of seconds.
     * Consecutive calls on the same side accumulate the seconds display.
     */
    @SuppressLint("SetTextI18n")
    fun showSeek(isForward: Boolean, seekSeconds: Long) {
        val side = if (isForward) Side.RIGHT else Side.LEFT

        // Reset if switching sides
        if (currentSide != null && currentSide != side) {
            cumulativeSeconds = 0
        }
        currentSide = side
        cumulativeSeconds += seekSeconds

        // Cancel any pending hide
        hideRunnable?.let { handler.removeCallbacks(it) }

        // Show the correct side
        visibility = View.VISIBLE
        if (side == Side.LEFT) {
            rightContainer.visibility = View.GONE
            leftContainer.visibility = View.VISIBLE
            leftText.text = "$cumulativeSeconds seconds"
            leftContainer.alpha = 1f
        } else {
            leftContainer.visibility = View.GONE
            rightContainer.visibility = View.VISIBLE
            rightText.text = "$cumulativeSeconds seconds"
            rightContainer.alpha = 1f
        }

        // Schedule hide after timeout
        hideRunnable = Runnable { hideOverlay() }
        handler.postDelayed(hideRunnable!!, HIDE_DELAY_MS)
    }

    private fun hideOverlay() {
        val container = if (currentSide == Side.LEFT) leftContainer else rightContainer
        container.animate()
            .alpha(0f)
            .setDuration(FADE_OUT_DURATION_MS)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    visibility = View.GONE
                    leftContainer.visibility = View.GONE
                    rightContainer.visibility = View.GONE
                    cumulativeSeconds = 0
                    currentSide = null
                }
            })
            .start()
    }

    companion object {
        private const val HIDE_DELAY_MS = 800L
        private const val FADE_OUT_DURATION_MS = 300L
    }
}
