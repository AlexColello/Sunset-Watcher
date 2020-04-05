package com.acolello.sunsetwatcher

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.coordinatorlayout.widget.CoordinatorLayout
import kotlin.math.absoluteValue

class MainView : CoordinatorLayout {

    // Needs all of the implementations of the constructor to work. Got syntax from
    // https://antonioleiva.com/custom-views-android-kotlin/
    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        setWillNotDraw(false)
    }

    private fun drawText(canvas: Canvas){
        val offset = getOffset(context)

        val sunsetTextView = findViewById<TextView>(R.id.sunset_time_text)

        val offsetSign = if (offset > 0) "+" else "-"
        val offsetString = "%s%01d:%02d:%02d".format(offsetSign, (offset/60/60).absoluteValue, ((offset/60)%60).absoluteValue, (offset%60).absoluteValue)
        sunsetTextView.text = offsetString
    }


    override fun onDraw(canvas: Canvas){
        //drawable.draw(canvas)
        drawText(canvas)
        super.onDraw(canvas)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

}