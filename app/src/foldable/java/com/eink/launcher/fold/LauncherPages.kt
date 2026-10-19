package com.eink.launcher.fold

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.widget.RecyclerView

/** Two real adjacent pages: home at index 0, alphabetical apps at index 1 (right). */
internal class LauncherPages(private val pages: List<View>) : RecyclerView.Adapter<LauncherPages.Holder>() {
    class Holder(val frame: FrameLayout) : RecyclerView.ViewHolder(frame)
    override fun getItemCount() = pages.size
    override fun getItemViewType(position: Int) = position
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(FrameLayout(parent.context).apply {
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
