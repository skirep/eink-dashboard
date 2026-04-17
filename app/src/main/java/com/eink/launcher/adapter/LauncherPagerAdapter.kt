package com.eink.launcher.adapter

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.eink.launcher.fragment.AppsFragment
import com.eink.launcher.fragment.SlideshowFragment
import com.eink.launcher.model.AppInfo

/**
 * LauncherPagerAdapter – feeds pages into the ViewPager2.
 *
 * Page layout:
 *  - Page 0     : [SlideshowFragment] (photo slideshow, 3-hour rotation)
 *  - Page 1..N  : [AppsFragment] instances, one per page of apps
 *
 * The total number of pages = 1 (slideshow) + ceil(apps.size / APPS_PER_PAGE).
 */
class LauncherPagerAdapter(
    activity: FragmentActivity,
    private val apps: List<AppInfo>
) : FragmentStateAdapter(activity) {

    private val appsPageCount: Int
        get() = if (apps.isEmpty()) 1
        else (apps.size + AppsFragment.APPS_PER_PAGE - 1) / AppsFragment.APPS_PER_PAGE

    override fun getItemCount(): Int = 1 + appsPageCount   // slideshow + app pages

    override fun createFragment(position: Int): Fragment {
        if (position == 0) return SlideshowFragment()

        val appsPageIndex = position - 1
        val from = appsPageIndex * AppsFragment.APPS_PER_PAGE
        val to = minOf(from + AppsFragment.APPS_PER_PAGE, apps.size)
        val pageApps = if (from < apps.size) apps.subList(from, to) else emptyList()
        return AppsFragment.newInstance(appsPageIndex, pageApps)
    }
}
