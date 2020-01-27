package com.acolello.sunsetwatcher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.OvalShape
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import kotlin.math.absoluteValue

class MainView(context: Context?) : View(context) {

    private val drawable: ShapeDrawable = run {
        val x = 10
        val y = 10
        val width = 300
        val height = 50
        //contentDescription = context.resources.getString(R.string.my_view_desc)

        ShapeDrawable(OvalShape()).apply {
            // If the color isn't set, the shape uses black as the default.
            paint.color = 0xff74AC23.toInt()
            // If the bounds aren't set, the shape can't be drawn.
            setBounds(x, y, x + width, y + height)
        }
    }

    private val mainLayout: ConstraintLayout = ConstraintLayout(context)

    private val offsetText: TextView = run {
        val text = TextView(context).apply {
            visibility = VISIBLE
            text = "0"
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER_HORIZONTAL
        }
        mainLayout.addView(text)
        text
    }


    private fun drawText(canvas: Canvas){
        val sunsetTime = getBestSunsetTime(context)
        val offset = getOffset(context)
        val notificationTime = calculateNotificationTime(sunsetTime, offset)

        //val sunsetTextView = findViewById<TextView>(R.id.sunset_time_text)
        //sunsetTextView.text = epochToString(sunsetTime)


        val offsetSign = if (offset > 0) "+" else "-"
        val offsetString = "%s%01d:%02d:%02d".format(offsetSign, (offset/60/60).absoluteValue, ((offset/60)%60).absoluteValue, (offset%60).absoluteValue)
        offsetText.text = offsetString

        //val notificationTextView = findViewById<TextView>(R.id.notification_time_text)
        //notificationTextView.text = epochToString(notificationTime)

        mainLayout.measure(width, height)
        mainLayout.layout(0, 0, width, height)

        canvas.translate(0f, height - 100f)
        mainLayout.draw(canvas)
        canvas.setMatrix(null)
    }

    override fun onDraw(canvas: Canvas){
        //drawable.draw(canvas)
        drawText(canvas)

    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

    }


}