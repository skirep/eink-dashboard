package com.eink.launcher.fold

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import org.json.JSONObject
import org.json.JSONArray
import kotlin.math.*

/** Launcher-owned windows only. Other Android apps remain managed by the OS. */
internal class DesktopWorkspace(context: Context, private val prefs: SharedPreferences,
    private val theme: String, sources: List<View>) : LinearLayout(context) {
    private val density = resources.displayMetrics.density
    private fun dp(n: Int) = (n * density).roundToInt()
    private val stage = FrameLayout(context)
    private val shelf = LinearLayout(context)
    private val windows = linkedMapOf<String, WorkspaceWindow>()
    init {
        orientation = VERTICAL
        val tools = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        tools.addView(TextView(context).apply {
            text = "Arrossega les barres · estira el cantó"; textSize = 11f; setTextColor(Color.WHITE)
        }, LayoutParams(0, dp(40), 1f))
        fun tool(label: String, action: () -> Unit) = Button(context).apply {
            text = label; textSize = 11f; isAllCaps = false; minWidth = 0; minimumWidth = 0
            setTextColor(MetroStyle.text); background = MetroStyle.surface(0xff0078d4.toInt(),0xff0078d4.toInt(), dp(7).toFloat())
            setOnClickListener { action() }
        }
        tools.addView(tool("Panells") { chooseWindows() }, LayoutParams(-2, dp(40)))
        tools.addView(tool("↺") { reset() }.apply { contentDescription = "Restableix l’escriptori" }, LayoutParams(dp(44), dp(40)))
        addView(tools)
        stage.clipChildren = true
        addView(stage, LayoutParams(-1, 0, 1f))
        addView(HorizontalScrollView(context).apply { addView(shelf); isHorizontalScrollBarEnabled = false }, LayoutParams(-1, dp(38)))
        val found = mutableListOf<View>()
        fun visit(view: View) {
            val tag = view.tag as? String
            if (tag in listOf("desktop-weather", "desktop-agenda", "desktop-mail", "desktop-news", "desktop-favorites-row")) found += view
            else if (view is ViewGroup) (0 until view.childCount).forEach { visit(view.getChildAt(it)) }
        }
        sources.forEach { visit(it) }
        val apps = LinearLayout(context).apply { orientation = VERTICAL }
        for (view in found) {
            (view.parent as? ViewGroup)?.removeView(view)
            when (view.tag) {
                "desktop-favorites-row" -> apps.addView(view, LayoutParams(-1,-2))
                "desktop-weather" -> addWindow("weather", "Temps", LinearLayout(context).apply {
                    orientation=VERTICAL; addView(view,LayoutParams(-1,-2))
                    addView(TextView(context).apply { text="Open-Meteo · Cabrils";textSize=10f;setTextColor(MetroStyle.muted) })
                })
                "desktop-agenda" -> addWindow("agenda", "Agenda", view)
                "desktop-mail" -> addWindow("mail", "Correu", view)
                "desktop-news" -> addWindow("news", "Notícies", view)
            }
        }
        if (apps.childCount > 0) addWindow("apps", "Apps fixades", apps)
        runCatching {
            val order=JSONArray(prefs.getString("desk-z:$theme","[]"))
            for(i in 0 until order.length()) windows[order.getString(i)]?.bringToFront()
        }
        rebuildShelf()
    }
    private fun front(window: WorkspaceWindow) {
        window.bringToFront()
        val order=JSONArray()
        for(i in 0 until stage.childCount) (stage.getChildAt(i) as? WorkspaceWindow)?.let { order.put(it.windowKey) }
        prefs.edit().putString("desk-z:$theme",order.toString()).apply()
    }
    private fun addWindow(id: String, label: String, content: View) {
        val window = WorkspaceWindow(id, label, content)
        windows[id] = window
        stage.addView(window, FrameLayout.LayoutParams(dp(260),dp(200)))
        window.visibility = if (prefs.getBoolean("desk-hidden:$theme:$id", id == "news")) GONE else VISIBLE
    }
    override fun onSizeChanged(w: Int,h: Int,oldw: Int,oldh: Int) {
        super.onSizeChanged(w,h,oldw,oldh)
        post { windows.values.forEach { it.restoreGeometry() } }
    }
    private fun chooseWindows() {
        val list = windows.values.toList()
        AlertDialog.Builder(context).setTitle("Panells de l’escriptori")
            .setItems(list.map { if (it.visibility == VISIBLE) "${it.label} · visible" else "${it.label} · minimitzat" }.toTypedArray()) { _,i -> list[i].showWindow() }
            .setNegativeButton("Tanca",null).show()
    }
    private fun rebuildShelf() {
        shelf.removeAllViews()
        windows.values.filter { it.visibility != VISIBLE }.forEach { window ->
            shelf.addView(Button(context).apply {
                text = "▣ ${window.label}"; textSize = 11f; isAllCaps = false
                setTextColor(MetroStyle.text); typeface=MetroStyle.font
                background=MetroStyle.surface(0xff0078d4.toInt(),0xff132c50.toInt(),dp(7).toFloat())
                contentDescription = "Restaura el panell ${window.label}"
                setOnClickListener { window.showWindow() }
            }, LayoutParams(-2,dp(38)))
        }
    }
    fun reset() {
        val edit = prefs.edit()
        windows.keys.forEach { edit.remove("desk:$theme:$it").remove("desk-hidden:$theme:$it").remove("desk-max:$theme:$it") }
        edit.remove("desk-z:$theme")
        edit.apply()
        windows.values.forEach { it.maximized = false; it.visibility = if (it.windowKey == "news") GONE else VISIBLE; it.restoreGeometry() }
        rebuildShelf()
    }
    internal inner class WorkspaceWindow(val windowKey: String, val label: String, content: View) : LinearLayout(context) {
        var maximized = prefs.getBoolean("desk-max:$theme:$windowKey",false)
        private val maxButton = Button(context)
        init {
            orientation = VERTICAL; tag = "desktop-surface"
            elevation = dp(10).toFloat()
            background = MetroStyle.surface(0xff0078d4.toInt(),0xff132c50.toInt(),dp(if (MetroStyle.desktop) 14 else 0).toFloat())
            clipToOutline = true
            val bar = LinearLayout(context).apply {
                gravity = Gravity.CENTER_VERTICAL
                if (MetroStyle.linux) setBackgroundColor(0xff203a39.toInt())
            }
            val title = TextView(context).apply {
                text = if (MetroStyle.linux) "~/" + when(windowKey) { "weather" -> "weather"; "agenda" -> "calendar"; "mail" -> "mail"; "news" -> "news"; else -> "apps" } else "≡ $label"; textSize = 13f; setTextColor(MetroStyle.text)
                setPadding(dp(9),0,0,0); gravity = Gravity.CENTER_VERTICAL
                maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
                tag = "workspace-drag"; contentDescription = "Arrossega $label"; isFocusable = true
            }
            title.setOnTouchListener(geometryGesture(false))
            bar.addView(title,LayoutParams(0,dp(36),1f))
            fun control(symbol: String, description: String, action: () -> Unit) = Button(context).apply {
                text = symbol; contentDescription = description; textSize = 15f
                minWidth=0; minimumWidth=0; setPadding(0,0,0,0); isAllCaps=false
                setTextColor(MetroStyle.text); background = null; setOnClickListener { action() }
            }
            bar.addView(control("−","Minimitza $label") {
                visibility=GONE; prefs.edit().putBoolean("desk-hidden:$theme:$windowKey",true).apply(); rebuildShelf()
            },LayoutParams(dp(40),dp(36)))
            maxButton.apply {
                text="□"; textSize=16f; setPadding(0,0,0,0); minWidth=0; minimumWidth=0
                setTextColor(MetroStyle.text); background=null
                contentDescription="Maximitza $label"
                setOnClickListener {
                    front(this@WorkspaceWindow); maximized=!maximized
                    prefs.edit().putBoolean("desk-max:$theme:$windowKey",maximized).apply(); restoreGeometry()
                }
            }
            bar.addView(maxButton,LayoutParams(dp(40),dp(36)))
            addView(bar)
            val scroll = ScrollView(context).apply { addView(content); isFillViewport=false }
            addView(scroll,LayoutParams(-1,0,1f))
            addView(TextView(context).apply {
                text="◢"; textSize=16f; gravity=Gravity.END; setTextColor(MetroStyle.muted)
                contentDescription="Redimensiona $label"; tag="workspace-resize"; isFocusable=true
                setOnClickListener {
                    AlertDialog.Builder(context).setTitle("Mida de $label").setItems(arrayOf("Petita","Mitjana","Gran")) { _,i ->
                        maximized=false; prefs.edit().putBoolean("desk-max:$theme:$windowKey",false).apply()
                        val fraction=floatArrayOf(.38f,.55f,.75f)[i]
                        place(x,y,stage.width*fraction,stage.height*fraction); saveGeometry()
                    }.show()
                }
                setOnTouchListener(geometryGesture(true))
            },LayoutParams(-1,dp(24)))
        }
        private fun defaults(): FloatArray = when(windowKey) {
            "weather" -> floatArrayOf(0f,0f,.54f,.60f)
            "agenda" -> floatArrayOf(.56f,0f,.44f,.48f)
            "mail" -> floatArrayOf(.56f,.5f,.44f,.50f)
            "apps" -> floatArrayOf(0f,.62f,.54f,.38f)
            else -> floatArrayOf(.18f,.08f,.68f,.85f)
        }
        fun restoreGeometry() {
            if (stage.width==0 || stage.height==0) return
            if (maximized) place(0f,0f,stage.width.toFloat(),stage.height.toFloat())
            else {
                val d=defaults()
                val saved=runCatching { JSONObject(prefs.getString("desk:$theme:$windowKey", "{}")) }.getOrDefault(JSONObject())
                place(saved.optDouble("x",d[0].toDouble()).toFloat()*stage.width,
                    saved.optDouble("y",d[1].toDouble()).toFloat()*stage.height,
                    saved.optDouble("w",d[2].toDouble()).toFloat()*stage.width,
                    saved.optDouble("h",d[3].toDouble()).toFloat()*stage.height)
            }
            maxButton.contentDescription=if(maximized) "Restaura $label" else "Maximitza $label"
        }
        private fun place(left: Float,top: Float,w: Float,h: Float) {
            val maxW=stage.width.coerceAtLeast(1); val maxH=stage.height.coerceAtLeast(1)
            val width=w.coerceIn(min(dp(180),maxW).toFloat(),maxW.toFloat()).roundToInt()
            val height=h.coerceIn(min(dp(130),maxH).toFloat(),maxH.toFloat()).roundToInt()
            layoutParams=FrameLayout.LayoutParams(width,height)
            x=left.coerceIn(0f,(maxW-width).toFloat()); y=top.coerceIn(0f,(maxH-height).toFloat())
        }
        private fun saveGeometry() {
            if (stage.width==0 || stage.height==0 || maximized) return
            val json=JSONObject().put("x",x/stage.width).put("y",y/stage.height)
                .put("w",layoutParams.width.toFloat()/stage.width).put("h",layoutParams.height.toFloat()/stage.height)
            prefs.edit().putString("desk:$theme:$windowKey",json.toString()).apply()
        }
        fun showWindow() {
            visibility=VISIBLE; front(this); prefs.edit().putBoolean("desk-hidden:$theme:$windowKey",false).apply()
            restoreGeometry(); rebuildShelf()
        }
        private fun geometryGesture(resize: Boolean): OnTouchListener {
            var rawX=0f;var rawY=0f;var left=0f;var top=0f;var w=0f;var h=0f;var moved=false
            return OnTouchListener { handle,event ->
                when(event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        front(this@WorkspaceWindow); rawX=event.rawX;rawY=event.rawY;left=x;top=y;w=width.toFloat();h=height.toFloat();moved=false
                        handle.parent.requestDisallowInterceptTouchEvent(true)
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx=event.rawX-rawX;val dy=event.rawY-rawY
                        if(abs(dx)+abs(dy)>dp(5)) moved=true
                        if(moved && !maximized) {
                            if(resize) place(left,top,w+dx,h+dy) else place(left+dx,top+dy,w,h)
                        }
                    }
                    MotionEvent.ACTION_UP,MotionEvent.ACTION_CANCEL -> {
                        saveGeometry();handle.parent.requestDisallowInterceptTouchEvent(false)
                        if(!moved && event.actionMasked==MotionEvent.ACTION_UP && resize) handle.performClick()
                    }
                }
                true
            }
        }
    }
}
