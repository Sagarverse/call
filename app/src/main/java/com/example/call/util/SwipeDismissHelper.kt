package com.example.call.util

import android.annotation.SuppressLint
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import androidx.activity.ComponentActivity
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs

class SwipeDismissHelper(
    private val activity: ComponentActivity,
    private val recyclerView: RecyclerView
) : GestureDetector.SimpleOnGestureListener(), View.OnTouchListener {

    private val gestureDetector = GestureDetector(activity, this)
    private val SWIPE_THRESHOLD = 150
    private val SWIPE_VELOCITY_THRESHOLD = 150

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(v: View?, event: MotionEvent?): Boolean {
        if (event == null) return false
        return gestureDetector.onTouchEvent(event)
    }

    override fun onFling(
        e1: MotionEvent?,
        e2: MotionEvent,
        velocityX: Float,
        velocityY: Float
    ): Boolean {
        if (e1 == null) return false
        
        val diffY = e2.y - e1.y
        val diffX = e2.x - e1.x
        
        // Detect vertical swipe down
        if (abs(diffY) > abs(diffX) && 
            diffY > SWIPE_THRESHOLD && 
            abs(velocityY) > SWIPE_VELOCITY_THRESHOLD) {
            
            // Only dismiss if we are at the top of the scroll
            if (!recyclerView.canScrollVertically(-1)) {
                activity.finish()
                return true
            }
        }
        return false
    }

    companion object {
        fun attach(activity: ComponentActivity, recyclerView: RecyclerView) {
            val helper = SwipeDismissHelper(activity, recyclerView)
            recyclerView.setOnTouchListener(helper)
        }
    }
}
