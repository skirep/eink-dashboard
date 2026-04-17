package com.eink.launcher.fragment

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.eink.launcher.R
import com.eink.launcher.model.AppInfo

/**
 * AppsFragment – displays one page of the installed-apps grid (page ≥ 1).
 *
 * The grid has a fixed number of cells ([COLS] × [ROWS] = [APPS_PER_PAGE]).
 * When there are more apps than fit on a single page, the MainActivity creates
 * multiple AppsFragment instances, each receiving a different subset of the
 * app list via [newInstance].
 *
 * E-ink considerations:
 *  - No RecyclerView / ScrollView – the whole page is static.
 *  - Large 56 dp icons and 2-line labels for readability.
 *  - Plain black text on white background, no ripple animations.
 */
class AppsFragment : Fragment() {

    companion object {
        const val COLS = 4
        const val ROWS = 5
        const val APPS_PER_PAGE = COLS * ROWS   // 20

        private const val ARG_PAGE = "arg_page"

        /**
         * Factory method that creates an [AppsFragment] for the given subset of apps.
         *
         * @param page     0-based page index within the apps section (not the ViewPager).
         * @param apps     The sub-list of [AppInfo] objects for this page.
         */
        fun newInstance(page: Int, apps: List<AppInfo>): AppsFragment {
            val fragment = AppsFragment()
            val bundle = Bundle().apply {
                putInt(ARG_PAGE, page)
                // Serialise just what we need: packageName, label (icon is reloaded).
                val pkgs = ArrayList(apps.map { it.packageName })
                val labels = ArrayList(apps.map { it.label })
                putStringArrayList("pkgs", pkgs)
                putStringArrayList("labels", labels)
            }
            fragment.arguments = bundle
            return fragment
        }
    }

    // Apps for this page – loaded from arguments + PackageManager.
    private var apps: List<AppInfo> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pkgs = arguments?.getStringArrayList("pkgs") ?: return
        val labels = arguments?.getStringArrayList("labels") ?: return
        val pm = requireContext().packageManager
        apps = pkgs.mapIndexedNotNull { i, pkg ->
            try {
                val icon = pm.getApplicationIcon(pkg)
                AppInfo(pkg, labels.getOrElse(i) { pkg }, icon)
            } catch (e: Exception) {
                null
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_apps, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val grid = view.findViewById<GridLayout>(R.id.gridApps)
        grid.columnCount = COLS
        grid.rowCount = ROWS
        populateGrid(grid)
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private fun populateGrid(grid: GridLayout) {
        val inflater = LayoutInflater.from(requireContext())
        for (row in 0 until ROWS) {
            for (col in 0 until COLS) {
                val index = row * COLS + col
                val rowSpec = GridLayout.spec(GridLayout.UNDEFINED, 1, 1f)
                val colSpec = GridLayout.spec(GridLayout.UNDEFINED, 1, 1f)

                val cell = inflater.inflate(R.layout.item_app, grid, false) as LinearLayout
                cell.layoutParams = GridLayout.LayoutParams(rowSpec, colSpec).also { lp ->
                    lp.width = 0
                    lp.height = GridLayout.LayoutParams.WRAP_CONTENT
                }

                if (index < apps.size) {
                    val app = apps[index]
                    val iconView = cell.findViewById<ImageView>(R.id.ivAppIcon)
                    iconView.setImageDrawable(app.icon)
                    iconView.contentDescription = app.label
                    cell.findViewById<TextView>(R.id.tvAppName).text = app.label
                    cell.setOnClickListener { launchApp(it.context, app.packageName) }
                } else {
                    // Empty cell – keep as transparent placeholder.
                    cell.visibility = View.INVISIBLE
                }

                grid.addView(cell)
            }
        }
    }

    private fun launchApp(context: Context, packageName: String) {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            ?: return
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
