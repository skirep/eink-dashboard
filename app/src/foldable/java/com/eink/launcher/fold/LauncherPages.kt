package com.eink.launcher.fold

import android.content.Context
import android.graphics.Rect
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.View
import android.widget.EditText
import kotlin.math.abs
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.widget.RecyclerView

/** Two real adjacent pages: home at index 0, alphabetical apps at index 1 (right). */
internal class LauncherPages(private val pages: List<View>) : RecyclerView.Adapter<LauncherPages.Holder>() {
    class Holder(val frame: FrameLayout) : RecyclerView.ViewHolder(frame)
    override fun getItemCount() = pages.size
    override fun getItemViewType(position: Int) = position
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(PageSurface(parent.context).apply {
        // Own DOWN events in empty page areas so the parent pager can intercept the drag.
        isClickable = true
        layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    })
    override fun onBindViewHolder(holder: Holder, position: Int) {
        val page = pages[position]
        if (page.parent === holder.frame) return
        (page.parent as? ViewGroup)?.removeView(page)
        holder.frame.removeAllViews()
        holder.frame.addView(page, FrameLayout.LayoutParams(-1, -1))
    }
}

/** Let vertical children scroll, but release horizontal drags back to the native pager. */
private class PageSurface(context: Context) : FrameLayout(context) {
    private var downX = 0f
    private var downY = 0f
    private var editing = false
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            downX = event.x; downY = event.y
            val field = findFocus() as? EditText
            val rect = Rect()
            editing = field?.getGlobalVisibleRect(rect) == true && rect.contains(event.rawX.toInt(), event.rawY.toInt())
        }
        val handled = super.dispatchTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_MOVE && !editing) {
            val dx = abs(event.x - downX); val dy = abs(event.y - downY)
            if (dx > slop && dx > dy * 1.3f) parent?.requestDisallowInterceptTouchEvent(false)
        }
        return handled
    }
}
