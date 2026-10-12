package com.acolello.sunsetui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import android.os.Bundle
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction
import android.view.animation.DecelerateInterpolator
import android.widget.OverScroller
import androidx.core.graphics.ColorUtils
import com.acolello.sunsetwatcher.SUNSET_ELEVATION_DEGREES
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.random.Random

// Draws the sun setting behind a hill. Sliding up and down changes how many minutes before or
// after sunset to be notified, and the sun, sky and stars move to where they'll be at that time.
class SunsetSceneView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        const val MIN_OFFSET_MINUTES = -240
        const val MAX_OFFSET_MINUTES = 120

        // How far a finger moves for one minute. With DP_PER_DEGREE below this keeps the sun
        // roughly under the finger at mid latitudes, where it sets at about 0.15° a minute.
        private const val DP_PER_MINUTE = 4f
        private const val DP_PER_DEGREE = 24f
        // Used when the location isn't known yet.
        private const val FALLBACK_DEGREES_PER_MINUTE = 0.15

        private const val HAPTIC_STEP_MINUTES = 5
        private const val SNAP_DURATION_MS = 180
        private const val INTRO_DURATION_MS = 1200L

        // Fractions of the view's height and width.
        private const val HORIZON = 0.68f
        private const val SUN_RADIUS = 0.2f
        private const val GLOW_SCALE = 2.6f
        private const val STAR_COUNT = 80

        private const val GROUND_COLOR = 0xFF160026.toInt()
        private const val SUN_HIGH_COLOR = 0xFFFFF0B5.toInt()
        private const val SUN_COLOR = 0xFFFFD152.toInt()
        private const val SUN_LOW_COLOR = 0xFFFF9548.toInt()
        private const val GLOW_COLOR = 0xFFFFE0A0.toInt()

        // Sky gradient (top to horizon) at different sun elevations, from day to night.
        // The sunset colors are the gradient the app used before.
        private val SKY = listOf(
            SkyColors(20.0, 0xFF2F80ED.toInt(), 0xFF9FD3FA.toInt()),
            SkyColors(6.0, 0xFF4F7CC4.toInt(), 0xFFFFD08A.toInt()),
            SkyColors(SUNSET_ELEVATION_DEGREES, 0xFFFFBB33.toInt(), 0xFFFF4444.toInt()),
            SkyColors(-4.0, 0xFF5B3F8C.toInt(), 0xFFE0566B.toInt()),
            SkyColors(-8.0, 0xFF241A4F.toInt(), 0xFF5B3F8C.toInt()),
            SkyColors(-18.0, 0xFF070514.toInt(), 0xFF1A0B33.toInt())
        )
    }

    private class SkyColors(val elevation: Double, val top: Int, val bottom: Int)
    private class Star(val x: Float, val y: Float, val radius: Float, val brightness: Float)

    // Called whenever the offset passes a whole minute, and with settled = true once the
    // user lets go and it has snapped to a minute.
    var onOffsetChanged: ((minutes: Int, settled: Boolean) -> Unit)? = null

    // Sun elevation in degrees at a number of minutes from sunset. Null when unknown.
    var elevationAt: ((offsetMinutes: Float) -> Double)? = null
        set(value) {
            field = value
            updateScene()
        }

    var offsetMinutes = 0f
        private set
    private var reportedMinutes = 0

    private val density = resources.displayMetrics.density
    private val pxPerMinute = DP_PER_MINUTE * density
    private val pxPerDegree = DP_PER_DEGREE * density

    private val scroller = OverScroller(context)
    private var velocityTracker: VelocityTracker? = null
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val minFlingVelocity = ViewConfiguration.get(context).scaledMinimumFlingVelocity
    private val maxFlingVelocity = ViewConfiguration.get(context).scaledMaximumFlingVelocity
    private var downY = 0f
    private var lastTouchY = 0f
    private var dragging = false
    private var flinging = false
    private var snapTarget: Int? = null

    private var elevation = SUNSET_ELEVATION_DEGREES
    private var introProgress = 1f
    private var introAnimator: ValueAnimator? = null

    private var horizonY = 0f
    private var sunRadius = 0f
    private val groundOval = RectF()
    private var skyTop = 0
    private var skyBottom = 0
    private val skyPaint = Paint()
    private val sunPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val groundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = GROUND_COLOR }
    private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }

    private val stars = Random(42).let { random ->
        List(STAR_COUNT) {
            Star(
                x = random.nextFloat(),
                y = random.nextFloat() * HORIZON * 0.95f,
                radius = (0.6f + random.nextFloat() * 1.2f) * density,
                brightness = 0.4f + random.nextFloat() * 0.6f
            )
        }
    }

    private val animationStep = object : Runnable {
        override fun run() {
            if (scroller.computeScrollOffset()) {
                setOffsetInternal(scroller.currY / pxPerMinute)
                postOnAnimation(this)
            } else if (flinging) {
                flinging = false
                settle()
            } else {
                snapTarget?.let {
                    snapTarget = null
                    setOffsetInternal(it.toFloat())
                    onOffsetChanged?.invoke(it, true)
                }
            }
        }
    }

    init {
        isFocusable = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    // Jumps to an offset without notifying the listener.
    fun setOffset(minutes: Int) {
        stopAnimation()
        offsetMinutes = minutes.coerceIn(MIN_OFFSET_MINUTES, MAX_OFFSET_MINUTES).toFloat()
        reportedMinutes = offsetMinutes.roundToInt()
        updateScene()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        horizonY = h * HORIZON
        sunRadius = w * SUN_RADIUS
        // A wide, flat oval so the horizon curves away gently at the edges.
        groundOval.set(-0.5f * w, horizonY, 1.5f * w, horizonY + 2f * w)

        glowPaint.shader = RadialGradient(
            0f, 0f, sunRadius * GLOW_SCALE,
            intArrayOf(GLOW_COLOR, ColorUtils.setAlphaComponent(GLOW_COLOR, 0x40), Color.TRANSPARENT),
            floatArrayOf(1f / GLOW_SCALE, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
        skyTop = 0
        skyBottom = 0
        updateScene()

        if (oldw == 0 && ValueAnimator.areAnimatorsEnabled()) {
            startIntro()
        }
    }

    // The sun rises into place when the app opens.
    private fun startIntro() {
        introAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = INTRO_DURATION_MS
            interpolator = DecelerateInterpolator(2f)
            addUpdateListener {
                introProgress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        introAnimator?.cancel()
        stopAnimation()
        super.onDetachedFromWindow()
    }

    private fun updateScene() {
        elevation = elevationAt?.invoke(offsetMinutes)
            ?: (SUNSET_ELEVATION_DEGREES - offsetMinutes * FALLBACK_DEGREES_PER_MINUTE)

        val (top, bottom) = skyColors(elevation)
        if ((top != skyTop || bottom != skyBottom) && horizonY > 0) {
            skyTop = top
            skyBottom = bottom
            skyPaint.shader = LinearGradient(0f, 0f, 0f, horizonY, top, bottom, Shader.TileMode.CLAMP)
        }

        sunPaint.color = when {
            elevation > 2 -> ColorUtils.blendARGB(SUN_COLOR, SUN_HIGH_COLOR, ((elevation - 2) / 13).toFloat().coerceIn(0f, 1f))
            else -> ColorUtils.blendARGB(SUN_COLOR, SUN_LOW_COLOR, ((2 - elevation) / 3).toFloat().coerceIn(0f, 1f))
        }

        // The glow is strongest near the horizon and fades out as the sun drops further below it.
        val nearHorizon = (1 - abs(elevation) / 20).toFloat().coerceIn(0.45f, 1f)
        val visible = ((elevation + 6) / 5).toFloat().coerceIn(0f, 1f)
        glowPaint.alpha = (255 * nearHorizon * visible).roundToInt()

        invalidate()
    }

    private fun skyColors(elevation: Double): Pair<Int, Int> {
        if (elevation >= SKY.first().elevation) return SKY.first().top to SKY.first().bottom
        if (elevation <= SKY.last().elevation) return SKY.last().top to SKY.last().bottom

        val above = SKY.last { it.elevation >= elevation }
        val below = SKY[SKY.indexOf(above) + 1]
        val fraction = ((above.elevation - elevation) / (above.elevation - below.elevation)).toFloat()
        return ColorUtils.blendARGB(above.top, below.top, fraction) to
                ColorUtils.blendARGB(above.bottom, below.bottom, fraction)
    }

    private fun sunCenterY(): Float {
        // At sunset the sun sits half below the horizon. Strictly its top edge would be touching,
        // but the sun is drawn far larger than to scale and a hidden sun doesn't read as sunset.
        val target = (horizonY - (elevation - SUNSET_ELEVATION_DEGREES) * pxPerDegree).toFloat()
            .coerceIn(sunRadius + 48 * density, height + sunRadius * GLOW_SCALE)
        val start = horizonY + sunRadius * GLOW_SCALE
        return start + (target - start) * introProgress
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        canvas.drawRect(0f, 0f, width.toFloat(), horizonY + sunRadius, skyPaint)

        val night = ((-4 - elevation) / 6).toFloat().coerceIn(0f, 1f)
        if (night > 0) {
            for (star in stars) {
                starPaint.alpha = (255 * night * star.brightness).roundToInt()
                canvas.drawCircle(star.x * width, star.y * height, star.radius, starPaint)
            }
        }

        val save = canvas.save()
        canvas.translate(width / 2f, sunCenterY())
        canvas.drawCircle(0f, 0f, sunRadius * GLOW_SCALE, glowPaint)
        canvas.drawCircle(0f, 0f, sunRadius, sunPaint)
        canvas.restoreToCount(save)

        canvas.drawOval(groundOval, groundPaint)
        canvas.drawRect(0f, groundOval.centerY(), width.toFloat(), height.toFloat(), groundPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val tracker = velocityTracker ?: VelocityTracker.obtain().also { velocityTracker = it }
        tracker.addMovement(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                stopAnimation()
                downY = event.y
                lastTouchY = event.y
                dragging = false
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                if (!dragging && abs(event.y - downY) > touchSlop) {
                    dragging = true
                    lastTouchY = event.y
                }
                if (dragging) {
                    // Dragging up moves the sun up, which is earlier, so a negative offset.
                    setOffsetInternal(offsetMinutes + (event.y - lastTouchY) / pxPerMinute)
                    lastTouchY = event.y
                }
            }
            MotionEvent.ACTION_UP -> {
                tracker.computeCurrentVelocity(1000, maxFlingVelocity.toFloat())
                val velocity = tracker.yVelocity
                if (!dragging) {
                    performClick()
                    settle()
                } else if (abs(velocity) > minFlingVelocity) {
                    fling(velocity)
                } else {
                    settle()
                }
                recycleTracker()
            }
            MotionEvent.ACTION_CANCEL -> {
                settle()
                recycleTracker()
            }
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()

    private fun recycleTracker() {
        velocityTracker?.recycle()
        velocityTracker = null
    }

    private fun stopAnimation() {
        scroller.forceFinished(true)
        removeCallbacks(animationStep)
        flinging = false
        snapTarget = null
    }

    private fun fling(velocity: Float) {
        flinging = true
        scroller.fling(
            0, (offsetMinutes * pxPerMinute).roundToInt(),
            0, velocity.roundToInt(),
            0, 0,
            (MIN_OFFSET_MINUTES * pxPerMinute).roundToInt(), (MAX_OFFSET_MINUTES * pxPerMinute).roundToInt()
        )
        postOnAnimation(animationStep)
    }

    // Animates to the nearest whole minute and then reports it as settled.
    private fun settle() {
        val target = offsetMinutes.roundToInt().coerceIn(MIN_OFFSET_MINUTES, MAX_OFFSET_MINUTES)
        val from = (offsetMinutes * pxPerMinute).roundToInt()
        val to = (target * pxPerMinute).roundToInt()

        snapTarget = target
        if (from != to) {
            scroller.startScroll(0, from, 0, to - from, SNAP_DURATION_MS)
        }
        postOnAnimation(animationStep)
    }

    private fun setOffsetInternal(minutes: Float) {
        val clamped = minutes.coerceIn(MIN_OFFSET_MINUTES.toFloat(), MAX_OFFSET_MINUTES.toFloat())
        performStepHaptics(offsetMinutes, clamped)
        offsetMinutes = clamped
        updateScene()

        val rounded = clamped.roundToInt()
        if (rounded != reportedMinutes) {
            reportedMinutes = rounded
            onOffsetChanged?.invoke(rounded, false)
        }
    }

    // A light tick every few minutes, and a stronger one when passing sunset itself.
    private fun performStepHaptics(from: Float, to: Float) {
        val feedback = when {
            (from < 0) != (to < 0) ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM
                else HapticFeedbackConstants.VIRTUAL_KEY
            floor(from / HAPTIC_STEP_MINUTES) != floor(to / HAPTIC_STEP_MINUTES) ->
                HapticFeedbackConstants.CLOCK_TICK
            else -> return
        }
        performHapticFeedback(feedback)
    }

    // Lets accessibility services move the time in steps, like a slider.
    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.isScrollable = true
        if (offsetMinutes < MAX_OFFSET_MINUTES) info.addAction(AccessibilityAction.ACTION_SCROLL_FORWARD)
        if (offsetMinutes > MIN_OFFSET_MINUTES) info.addAction(AccessibilityAction.ACTION_SCROLL_BACKWARD)
    }

    override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean {
        val step = when (action) {
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD -> HAPTIC_STEP_MINUTES
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD -> -HAPTIC_STEP_MINUTES
            else -> return super.performAccessibilityAction(action, arguments)
        }
        stopAnimation()
        setOffsetInternal((offsetMinutes.roundToInt() + step).toFloat())
        onOffsetChanged?.invoke(offsetMinutes.roundToInt(), true)
        return true
    }
}
