package com.eink.launcher.fold

import android.content.Context
import android.view.View
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.viewpager.widget.PagerAdapter
import androidx.viewpager.widget.ViewPager

/** Native horizontal paging, including empty areas around vertical page content. */
internal class LauncherPager(context: Context) : ViewPager(context) {
    private var startX = 0f
    private var startY = 0f
    private var startPage = 0
    var pointerDown = false
        private set
    var pagingState: Int = SCROLL_STATE_IDLE
        private set
    init {
        addOnPageChangeListener(object : SimpleOnPageChangeListener() {
            override fun onPageScrollStateChanged(state: Int) { pagingState = state }
        })
    }
    // Pages contain vertical scroll views and text. Horizontal overflow in a label
    // must not claim the launcher gesture before ViewPager can intercept it.
    override fun canScroll(v: View, checkV: Boolean, dx: Int, x: Int, y: Int) = false

    // Vertical children may disallow interception on DOWN. Keep receiving moves:
    // ViewPager itself locks vertical gestures to the child once direction is known.
    override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
        if (!disallowIntercept) super.requestDisallowInterceptTouchEvent(false)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            pointerDown = true
            startX = event.x; startY = event.y; startPage = currentItem
        }
        val dx = event.x - startX
        val dy = event.y - startY
        val commit = event.actionMasked == MotionEvent.ACTION_UP &&
            kotlin.math.abs(dx) > width * .18f && kotlin.math.abs(dx) > kotlin.math.abs(dy) * 1.3f
        return try {
            val handled = super.dispatchTouchEvent(event)
            // Commit a deliberate page-length drag independently of nested child
            // velocity tracking. Native ViewPager still draws and animates the pages.
            if (commit) setCurrentItem((startPage + if (dx < 0) 1 else -1)
                .coerceIn(0, (adapter?.count ?: 1) - 1), true)
            handled
        } finally {
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) pointerDown = false
        }
    }
}

/** Home is page 0 on the left, alphabetical applications are page 1 on the right. */
internal class LauncherPages(private val pages: List<View>) : PagerAdapter() {
    override fun getCount() = pages.size
    override fun isViewFromObject(view: View, item: Any) = view === item
    override fun instantiateItem(container: ViewGroup, position: Int): Any {
        val page = pages[position]
        (page.parent as? ViewGroup)?.removeView(page)
        page.isClickable = true
        container.addView(page)
        return page
    }
    override fun destroyItem(container: ViewGroup, position: Int, item: Any) { container.removeView(item as View) }
}
