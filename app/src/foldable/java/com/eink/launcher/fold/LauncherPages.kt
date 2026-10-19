package com.eink.launcher.fold

import android.content.Context
import android.view.View
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.viewpager.widget.PagerAdapter
import androidx.viewpager.widget.ViewPager

/** Native horizontal paging, including empty areas around vertical page content. */
internal class LauncherPager(context: Context) : ViewPager(context) {
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

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) pointerDown = true
        return try { super.dispatchTouchEvent(event) } finally {
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
