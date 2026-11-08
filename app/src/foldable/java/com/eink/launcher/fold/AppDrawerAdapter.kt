package com.eink.launcher.fold

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.*

/** Recycles visible rows instead of inflating and decoding every installed app per keystroke. */
internal class AppDrawerAdapter(
    private val scope: CoroutineScope,
    private val catalog: AppCatalog,
    private val launch: (LauncherApp) -> Unit,
    private val pin: (String) -> Unit
) : RecyclerView.Adapter<AppDrawerAdapter.Holder>() {
    private var rows = emptyList<LauncherApp>()
    private var pinned = emptySet<String>()
    init { setHasStableIds(true) }
    fun submit(apps: List<LauncherApp>, pins: Set<String>) {
        rows = apps; pinned = pins; notifyDataSetChanged()
    }
    fun first(): LauncherApp? = rows.firstOrNull()
    override fun getItemCount() = rows.size
    // Component strings have unique identities; RecyclerView does not require numeric IDs here.
    override fun getItemId(position: Int): Long = rows[position].id.fold(1125899906842597L) { hash, c -> hash * 31 + c.code }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val context = parent.context
        val d = context.resources.displayMetrics.density
        fun dp(value: Int) = (value * d).toInt()
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = RecyclerView.LayoutParams(-1, -2)
        }
        val letter = TextView(context).apply {
            textSize = 26f; setTextColor(MetroStyle.mint); setPadding(dp(4), dp(14), 0, dp(6))
        }
        row.addView(letter)
        val body = LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL; minimumHeight = dp(68); isFocusable = true
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x228af5dc),
                MetroStyle.surface(0x551d304b, 0x3313233b, dp(12).toFloat()), null)
        }
        val icon = ImageView(context).apply { importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }
        body.addView(icon, LinearLayout.LayoutParams(dp(38), dp(38)))
        val label = TextView(context).apply {
            textSize = 19f; maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END
        }
        body.addView(label, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(16) })
        val arrow = TextView(context).apply { text = "↗"; textSize = 20f; importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }
        body.addView(arrow)
        row.addView(body, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(5) })
        LauncherThemes.style(row)
        label.typeface = MetroStyle.font; label.setTextColor(MetroStyle.text)
        arrow.setTextColor(MetroStyle.mint)
        return Holder(row, letter, body, icon, label)
    }
    override fun onBindViewHolder(holder: Holder, position: Int) {
        val app = rows[position]
        holder.job?.cancel()
        holder.letter.visibility = if (MetroStyle.windows && (position == 0 || rows[position - 1].initial != app.initial)) View.VISIBLE else View.GONE
        holder.letter.text = app.initial
        holder.letter.contentDescription = "Grup ${app.initial}"
        holder.label.text = app.label + if (app.id in pinned) "  ▪" else ""
        holder.body.contentDescription = app.label + if (app.id in pinned) ". Fixada. Mantén premut per treure-la." else ". Mantén premut per fixar-la."
        holder.icon.setImageResource(android.R.drawable.sym_def_app_icon)
        holder.job = scope.launch {
            catalog.icon(app)?.let { holder.icon.setImageDrawable(it) }
        }
        holder.body.setOnClickListener { launch(app) }
        holder.body.setOnLongClickListener { pin(app.id); true }
    }
    override fun onViewRecycled(holder: Holder) {
        holder.job?.cancel(); holder.icon.setImageDrawable(null)
        super.onViewRecycled(holder)
    }
    internal class Holder(view: View, val letter: TextView, val body: View, val icon: ImageView, val label: TextView) : RecyclerView.ViewHolder(view) {
        var job: Job? = null
    }
}
