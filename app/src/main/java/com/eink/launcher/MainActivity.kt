package com.eink.launcher

import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.eink.launcher.adapter.LauncherPagerAdapter
import com.eink.launcher.model.AppInfo

/**
 * MainActivity – the single activity that drives the entire launcher.
 *
 * Pages (managed by [LauncherPagerAdapter]):
 *  - Page 0     : Slideshow (photo that changes every 3 hours)
 *  - Page 1..N  : Grids of installed application icons
 *
 * The ViewPager2 has user-input (swipe) disabled; the only way to switch
 * pages is by pressing the "Anterior" / "Següent" buttons in the nav bar.
 * This is intentional for e-ink displays where accidental swipes would
 * trigger a full-screen refresh.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var btnPrev: Button
    private lateinit var btnNext: Button
    private lateinit var tvPageIndicator: TextView
    private lateinit var adapter: LauncherPagerAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        viewPager = findViewById(R.id.viewPager)
        btnPrev = findViewById(R.id.btnPrev)
        btnNext = findViewById(R.id.btnNext)
        tvPageIndicator = findViewById(R.id.tvPageIndicator)

        val apps = loadInstalledApps()
        adapter = LauncherPagerAdapter(this, apps)
        viewPager.adapter = adapter

        // Disable swipe – navigation is button-only (e-ink friendly).
        viewPager.isUserInputEnabled = false

        // Disable the default ViewPager2 over-scroll glow effect.
        viewPager.getChildAt(0)?.overScrollMode = View.OVER_SCROLL_NEVER

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateNavBar(position)
            }
        })

        btnPrev.setOnClickListener {
            val current = viewPager.currentItem
            if (current > 0) viewPager.setCurrentItem(current - 1, false)
        }

        btnNext.setOnClickListener {
            val current = viewPager.currentItem
            if (current < adapter.itemCount - 1) viewPager.setCurrentItem(current + 1, false)
        }

        updateNavBar(0)

        // Consume the back button – launchers should not be closed.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { /* intentionally empty */ }
        })
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Updates the enabled/visibility state of the navigation buttons and the
     * page indicator label.
     */
    private fun updateNavBar(position: Int) {
        val total = adapter.itemCount
        btnPrev.isEnabled = position > 0
        btnNext.isEnabled = position < total - 1

        tvPageIndicator.text = getString(R.string.page_indicator, position + 1, total)
    }

    /**
     * Queries the [PackageManager] for all user-launchable applications,
     * excluding this launcher itself.  The result is sorted alphabetically.
     */
    private fun loadInstalledApps(): List<AppInfo> {
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolvedList: List<ResolveInfo> = pm.queryIntentActivities(
            intent,
            PackageManager.GET_META_DATA
        )
        return resolvedList
            .mapNotNull { info ->
                val pkg = info.activityInfo.packageName
                if (pkg == packageName) return@mapNotNull null  // skip self
                try {
                    AppInfo(
                        packageName = pkg,
                        label = info.loadLabel(pm).toString(),
                        icon = info.loadIcon(pm)
                    )
                } catch (e: Exception) {
                    null
                }
            }
            .sortedBy { it.label.lowercase() }
    }
}
