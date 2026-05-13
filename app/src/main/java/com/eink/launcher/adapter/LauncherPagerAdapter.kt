package com.eink.launcher.adapter

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.eink.launcher.fragment.AppsFragment
import com.eink.launcher.fragment.HomeFragment
import com.eink.launcher.fragment.WalkingPersonFragment
import com.eink.launcher.model.AppInfo

/**
 * LauncherPagerAdapter – feeds pages into the ViewPager2.
 *
 * Page layout:
 *  - Page 0     : [HomeFragment] (dashboard with time, weather, notes, reading)
 *  - Page 1     : [WalkingPersonFragment] (animated walking character)
 *  - Page 2..N  : [AppsFragment] instances, one per page of apps
 *
 * The total number of pages = 2 (home + walking person) + ceil(apps.size / APPS_PER_PAGE).
 */
class LauncherPagerAdapter(
    activity: FragmentActivity,
    private val apps: List<AppInfo>
) : FragmentStateAdapter(activity) {

    private val appsPageCount: Int
        get() = if (apps.isEmpty()) 1
        else (apps.size + AppsFragment.APPS_PER_PAGE - 1) / AppsFragment.APPS_PER_PAGE

    override fun getItemCount(): Int = 2 + appsPageCount   // home + walking person + app pages

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> HomeFragment()
            1 -> WalkingPersonFragment()
            else -> {
                val appsPageIndex = position - 2
                val from = appsPageIndex * AppsFragment.APPS_PER_PAGE
                val to = minOf(from + AppsFragment.APPS_PER_PAGE, apps.size)
                val pageApps = if (from < apps.size) apps.subList(from, to) else emptyList()
                AppsFragment.newInstance(appsPageIndex, pageApps)
            }
        }
    }
}
