package com.eink.launcher.fold

import android.Manifest
import android.animation.ValueAnimator
import android.app.role.RoleManager
import android.content.*
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.content.res.Configuration
import android.database.ContentObserver
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.*
import android.provider.CalendarContract
import android.provider.Settings
import android.view.*
import android.view.animation.DecelerateInterpolator
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

class FoldActivity : AppCompatActivity() {
    private val prefs by lazy { getSharedPreferences("fold-metro", MODE_PRIVATE) }
    private val weatherStore by lazy { WeatherStore(prefs) }
    private var hinge: FoldingFeature? = null
    private var appDrawer = false
    private var search = ""
    private var events = emptyList<AgendaEvent>()
    private var sources = emptyList<CalendarSource>()
    private var agendaError: String? = null
    private var agendaReady = false
    private var agendaJob: Job? = null
    private var cityJob: Job? = null
    private val weatherJobs = mutableMapOf<String, Job>()
    private val weatherStatus = mutableMapOf<String, String>()
    private var observerRegistered = false
    private var headerClock: TextView? = null
    private var headerDate: TextView? = null
    private var homeScroll: ScrollView? = null
    private var rightScroll: ScrollView? = null
    private var drawerScroll: ScrollView? = null
    private var homeY = 0
    private var rightY = 0
    private var drawerY = 0
    private var renderedWide: Boolean? = null
    private var entrancePending = true
    private var calendarPickerPending = false
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        refreshAgenda()
        if (granted && calendarPickerPending) { calendarPickerPending = false; calendarPicker() }
        else calendarPickerPending = false
    }
    private val calendarObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) { refreshAgenda() }
    }
    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { render() }
    }
    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            headerClock?.text = clockText(); headerDate?.text = dateText()
            refreshAgenda(); refreshWeather(); handler.postDelayed(this, 60000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        weatherStore.migratePreviousCity()
        appDrawer = savedInstanceState?.getBoolean("drawer") ?: false
        search = savedInstanceState?.getString("search") ?: ""
        homeY = savedInstanceState?.getInt("homeY") ?: 0
        rightY = savedInstanceState?.getInt("rightY") ?: 0
        drawerY = savedInstanceState?.getInt("drawerY") ?: 0
        entrancePending = savedInstanceState == null
        if (!prefs.contains("pins")) {
            val initial = apps().take(6).map { key(it) }
            if (initial.isNotEmpty()) prefs.edit().putString("pins", org.json.JSONArray(initial).toString()).apply()
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { appDrawer = false; search = ""; entrancePending = true; render() }
        })
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                WindowInfoTracker.getOrCreate(this@FoldActivity).windowLayoutInfo(this@FoldActivity).collect { info ->
                    val feature = info.displayFeatures.filterIsInstance<FoldingFeature>().firstOrNull()
                    if (hinge != feature) { hinge = feature; render() }
                }
            }
        }
        render()
    }
    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(this, packageReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED); addAction(Intent.ACTION_PACKAGE_REMOVED); addAction(Intent.ACTION_PACKAGE_CHANGED); addDataScheme("package")
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
        handler.post(tick)
    }
    override fun onResume() { super.onResume(); refreshAgenda() }
    override fun onStop() {
        handler.removeCallbacks(tick); unregisterReceiver(packageReceiver)
        if (observerRegistered) { contentResolver.unregisterContentObserver(calendarObserver); observerRegistered = false }
        super.onStop()
    }
    override fun onConfigurationChanged(newConfig: Configuration) { super.onConfigurationChanged(newConfig); render() }
    override fun onSaveInstanceState(out: Bundle) {
        saveScroll(); out.putBoolean("drawer", appDrawer); out.putString("search", search)
        out.putInt("homeY", homeY); out.putInt("rightY", rightY); out.putInt("drawerY", drawerY)
        super.onSaveInstanceState(out)
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); appDrawer = false; search = ""; entrancePending = true; render() }

    private fun dp(n: Int) = (n * resources.displayMetrics.density).roundToInt()
    private fun clockText() = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
    private fun dateText() = SimpleDateFormat("EEEE, d MMMM", Locale("ca")).format(Date())
    private fun text(value: String, size: Float = 16f, color: Int = MetroStyle.text) = TextView(this).apply {
        text = value; textSize = size; setTextColor(color); setPadding(0, dp(2), 0, dp(2))
        typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        setLineSpacing(dp(2).toFloat(), 1f)
    }
    private fun caption(value: String, color: Int = MetroStyle.muted) = text(value, 11f, color).apply { letterSpacing = .1f; typeface = Typeface.create("sans-serif-medium", 0) }
    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun action(label: String, click: () -> Unit) = Button(this).apply {
        text = label; isAllCaps = false; textSize = 14f; minHeight = dp(48); minimumWidth = 0
        setTextColor(MetroStyle.text); setPadding(dp(12), dp(6), dp(12), dp(6))
        background = MetroStyle.surface(0xff1d2b45.toInt(), 0xff142139.toInt(), dp(3).toFloat())
        setOnClickListener { click() }
    }
    private fun tile(start: Int, end: Int, padding: Int = 16) = MetroTile(this, start, end).apply { setPadding(dp(padding), dp(padding), dp(padding), dp(padding)) }
    private fun block(parent: LinearLayout, child: View, bottom: Int = 12) { parent.addView(child, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(bottom) }) }
    private fun effectsAllowed(): Boolean {
        if (!prefs.getBoolean("effects", true)) return false
        if ((getSystemService(POWER_SERVICE) as PowerManager).isPowerSaveMode) return false
        return Build.VERSION.SDK_INT < 26 || ValueAnimator.areAnimatorsEnabled()
    }
    private fun entrance(view: View, index: Int, active: Boolean) {
        if (!active || !effectsAllowed()) return
        view.alpha = .2f; view.translationY = dp(22).toFloat(); view.rotationX = 4f
        view.animate().alpha(1f).translationY(0f).rotationX(0f).setDuration(520).setStartDelay((index * 45).toLong()).setInterpolator(DecelerateInterpolator()).start()
    }
    private fun saveScroll() { homeScroll?.let { homeY = it.scrollY }; rightScroll?.let { rightY = it.scrollY }; drawerScroll?.let { drawerY = it.scrollY } }

    private fun render() {
        saveScroll(); homeScroll = null; rightScroll = null; drawerScroll = null
        val width = resources.configuration.screenWidthDp
        val wide = FoldPolicy.expanded(width, hinge != null)
        val animate = entrancePending || (renderedWide != null && renderedWide != wide)
        entrancePending = false; renderedWide = wide
        val surface = AuroraSurface(this)
        val root = column().apply { setPadding(dp(20), dp(12), dp(20), dp(8)); fitsSystemWindows = true }
        surface.addView(root, FrameLayout.LayoutParams(-1, -1))
        val heading = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val title = column()
        title.addView(caption("FOLD / METRO     ·     MAGIC V2", MetroStyle.mint))
        val hourLine = LinearLayout(this).apply { gravity = Gravity.BOTTOM }
        headerClock = text(clockText(), if (width < 420) 48f else 56f).also { hourLine.addView(it) }
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        if (resources.configuration.fontScale <= 1.15f) hourLine.addView(text(when (hour) { in 6..12 -> "Bon dia."; in 13..19 -> "Bona tarda."; else -> "Bona nit." }, 14f, MetroStyle.muted), LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(14); bottomMargin = dp(12) })
        title.addView(hourLine)
        headerDate = text(dateText(), 15f, MetroStyle.muted).also { title.addView(it) }
        heading.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
        heading.addView(action("⚙") { settings() }.apply { contentDescription = "Configuració" }, LinearLayout.LayoutParams(dp(48), dp(48)))
        root.addView(heading)
        val mode = when {
            hinge?.state == FoldingFeature.State.HALF_OPENED -> "MIG OBERT / dues zones"
            hinge != null -> "OBERT / espai ampliat"
            wide -> "Vista ampla"
            else -> "Vista compacta"
        }
        root.addView(caption(mode, MetroStyle.blue), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4); bottomMargin = dp(12) })
        if (appDrawer) renderDrawer(root) else {
            val left = column(); val right = column()
            addWeather(left, wide, animate)
            if (!wide) addAgenda(left, 1, animate)
            block(left, caption("EL TEU ESPAI"), 7)
            addFavorites(left, if (wide) 3 else FoldPolicy.columns(width), animate)
            if (wide) addAgenda(right, 12, animate)
            if (!isDefaultHome()) block(right, action("Estableix com a inici") { chooseHome() })
            if (wide) block(right, text("Mantén premut un mosaic per personalitzar-lo.", 12f, MetroStyle.muted))
            addPanels(root, left, right, wide)
        }
        val dock = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(8), 0, 0) }
        dock.addView(action(if (appDrawer) "← Inici" else "Aplicacions  ↗") { appDrawer = !appDrawer; render() }, LinearLayout.LayoutParams(0, dp(48), 1.2f).apply { rightMargin = dp(8) })
        dock.addView(action("Calendaris") { calendarPicker() }, LinearLayout.LayoutParams(0, dp(48), 1f))
        root.addView(dock)
        setContentView(surface)
    }

    private fun addPanels(root: LinearLayout, left: LinearLayout, right: LinearLayout, wide: Boolean) {
        val body = LinearLayout(this)
        val horizontal = hinge?.orientation == FoldingFeature.Orientation.HORIZONTAL
        body.orientation = if (wide && !horizontal) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        if (wide) {
            val first = ScrollView(this).apply { addView(left); isFillViewport = true; isVerticalScrollBarEnabled = false }
            val second = ScrollView(this).apply { addView(right); isFillViewport = true; isVerticalScrollBarEnabled = false }
            homeScroll = first; rightScroll = second
            val gap = View(this)
            if (horizontal) {
                body.addView(first, LinearLayout.LayoutParams(-1, 0, 1f)); body.addView(gap, LinearLayout.LayoutParams(-1, dp(16))); body.addView(second, LinearLayout.LayoutParams(-1, 0, 1f))
            } else {
                body.addView(first, LinearLayout.LayoutParams(0, -1, 1.25f)); body.addView(gap, LinearLayout.LayoutParams(dp(18), -1)); body.addView(second, LinearLayout.LayoutParams(0, -1, 1f))
            }
            root.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
            if (hinge?.isSeparating == true) body.post {
                val bounds = hinge?.bounds ?: return@post
                val origin = IntArray(2); body.getLocationInWindow(origin)
                if (horizontal) {
                    first.layoutParams = LinearLayout.LayoutParams(-1, (bounds.top - origin[1]).coerceIn(0, body.height)); gap.layoutParams = LinearLayout.LayoutParams(-1, bounds.height())
                } else {
                    first.layoutParams = LinearLayout.LayoutParams((bounds.left - origin[0]).coerceIn(0, body.width), -1); gap.layoutParams = LinearLayout.LayoutParams(bounds.width(), -1)
                }
            }
            first.post { first.scrollTo(0, homeY) }; second.post { second.scrollTo(0, rightY) }
        } else {
            body.addView(left); body.addView(right)
            val scroll = ScrollView(this).apply { addView(body); isVerticalScrollBarEnabled = false }
            homeScroll = scroll; root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f)); scroll.post { scroll.scrollTo(0, homeY) }
        }
    }

    private fun addWeather(parent: LinearLayout, wide: Boolean, animate: Boolean) {
        val line = LinearLayout(this)
        listOf(WeatherLocations.cabrils, weatherStore.extra()).forEachIndexed { i, location ->
            val snapshot = location?.let { weatherStore.snapshot(it.id) }
            val current = snapshot?.data?.optJSONObject("current")
            val night = current?.optInt("is_day", 1) == 0
            val code = current?.optInt("weather_code", -1) ?: -1
            val card = tile(if (i == 0) 0xff176d71.toInt() else 0xff434381.toInt(), if (i == 0) 0xff112c46.toInt() else 0xff201e41.toInt(), 13)
            card.addView(caption(if (i == 0) "A CASA" else "EL TEU ALTRE LLOC", 0xffd4e7ff.toInt()))
            card.addView(text(location?.name ?: "Tria una ciutat", 19f))
            val visual = FrameLayout(this)
            visual.addView(WeatherArt(this, code, night).apply { importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }, FrameLayout.LayoutParams(dp(66), dp(66), Gravity.END or Gravity.CENTER_VERTICAL))
            visual.addView(text(if (current != null) "${current.optDouble("temperature_2m").roundToInt()}°" else if (location == null) "+" else "—°", 41f), FrameLayout.LayoutParams(-2, -2, Gravity.START or Gravity.CENTER_VERTICAL))
            card.addView(visual, LinearLayout.LayoutParams(-1, dp(78)))
            card.addView(text(if (current != null) DashboardData.condition(code) else if (location == null) "Toca per escollir" else "Carregant el temps", 14f))
            val stamp = snapshot?.updated ?: 0
            val state = location?.let { weatherStatus[it.id] }
            card.addView(text(state?.takeIf { it.isNotEmpty() } ?: if (stamp > 0) "Actualitzat " + SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(stamp)) else if (location == null) "Ciutat configurable" else "Toca per reintentar", 11f, 0xffc7d8eb.toInt()))
            card.setOnClickListener { if (location == null || i == 1) cityDialog() else weatherDetails(location) }
            card.setOnLongClickListener { if (i == 1) cityDialog() else refreshWeather(true); true }
            card.contentDescription = if (location == null) "Escull la segona ciutat" else "Temps de ${location.name}. Toca per veure opcions"
            line.addView(card, LinearLayout.LayoutParams(0, -1, 1f).apply { if (i == 0) rightMargin = dp(8) })
            entrance(card, i, animate)
        }
        block(parent, line)
        if (wide) {
            val daily = weatherStore.snapshot("cabrils").data?.optJSONObject("daily")
            val days = daily?.optJSONArray("time")
            if (days != null) {
                val forecast = LinearLayout(this)
                for (i in 0 until minOf(4, days.length())) {
                    val day = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(days.getString(i)) ?: continue
                    val item = column().apply { gravity = Gravity.CENTER; setPadding(dp(4), dp(4), dp(4), dp(8)) }
                    item.addView(caption(SimpleDateFormat("EEE", Locale("ca")).format(day)))
                    item.addView(WeatherArt(this, daily.getJSONArray("weather_code").getInt(i), false), LinearLayout.LayoutParams(dp(42), dp(42)))
                    item.addView(text("${daily.getJSONArray("temperature_2m_max").getDouble(i).roundToInt()}° / ${daily.getJSONArray("temperature_2m_min").getDouble(i).roundToInt()}°", 13f))
                    forecast.addView(item, LinearLayout.LayoutParams(0, -2, 1f))
                }
                block(parent, forecast, 7)
            }
        }
        block(parent, text("Open-Meteo · toca Cabrils per a més detalls", 11f, MetroStyle.muted), 14)
    }

    private fun weatherDetails(location: WeatherLocation) {
        val snapshot = weatherStore.snapshot(location.id)
        val daily = snapshot.data?.optJSONObject("daily")
        val days = daily?.optJSONArray("time")
        val lines = mutableListOf<String>()
        if (snapshot.updated > 0) lines += "Actualitzat " + SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(snapshot.updated))
        if (days != null) for (i in 0 until days.length()) {
            val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(days.getString(i)) ?: continue
            lines += SimpleDateFormat("EEE d", Locale("ca")).format(date) + "  ·  ${daily.getJSONArray("temperature_2m_min").getDouble(i).roundToInt()}° / ${daily.getJSONArray("temperature_2m_max").getDouble(i).roundToInt()}°  ·  " + DashboardData.condition(daily.getJSONArray("weather_code").getInt(i))
        }
        AlertDialog.Builder(this).setTitle(location.name).setMessage(lines.joinToString("\n\n").ifEmpty { "Encara no hi ha dades meteorològiques." }).setPositiveButton("Actualitza") { _, _ -> refreshWeather(true) }.setNegativeButton("Tanca", null).show()
    }

    private fun addAgenda(parent: LinearLayout, count: Int, animate: Boolean) {
        val card = tile(0xff202d4c.toInt(), 0xff121b30.toInt())
        card.addView(caption("LA TEVA AGENDA / 7 DIES", MetroStyle.blue))
        val enabled = selectedCalendars()
        val active = sources.filter { enabled?.contains(it.id) ?: it.visible }
        val summary = if (calendarGranted() && agendaReady) {
            val google = active.count { it.provider == "Google" }; val outlook = active.count { it.provider == "Outlook / Microsoft" }
            "Google · $google    Outlook · $outlook"
        } else "Google Calendar + Outlook"
        card.addView(text(summary, 13f, MetroStyle.muted))
        when {
            !calendarGranted() -> {
                card.addView(text("Tot el teu dia,\nen un sol lloc.", 24f))
                block(card, action("Connecta els calendaris") { requestCalendar() }, 0)
            }
            agendaError != null -> card.addView(text(agendaError!!, 16f))
            !agendaReady -> card.addView(text("Carregant calendaris…", 16f))
            sources.isEmpty() -> {
                card.addView(text("Activa la sincronització de Google i Outlook per veure els esdeveniments aquí.", 16f))
                card.addView(action("Configura els calendaris") { calendarHelp() })
            }
            active.isEmpty() -> card.addView(action("Escull els calendaris que vols veure") { calendarPicker() })
            events.isEmpty() -> card.addView(text("Tens espai per a tu.\nCap acte els pròxims 7 dies.", 20f))
            else -> events.take(count).forEach { event ->
                val source = sources.find { it.id == event.calendarId }
                val row = column().apply { setPadding(dp(10), dp(12), dp(2), dp(12)) }
                row.addView(caption(event.time(), MetroStyle.mint))
                row.addView(text(event.title, 21f))
                row.addView(text(listOfNotNull(source?.provider, source?.name).joinToString(" · "), 12f, MetroStyle.blue))
                if (event.location.isNotBlank()) row.addView(text(event.location, 13f, MetroStyle.muted))
                val eventLine = LinearLayout(this)
                eventLine.addView(View(this).apply { setBackgroundColor(if (source?.provider == "Google") MetroStyle.mint else MetroStyle.blue) }, LinearLayout.LayoutParams(dp(2), -1))
                eventLine.addView(row, LinearLayout.LayoutParams(0, -2, 1f))
                eventLine.setOnClickListener { open(Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, event.id)).putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, event.start).putExtra(CalendarContract.EXTRA_EVENT_END_TIME, event.end)) }
                card.addView(eventLine)
            }
        }
        if (count > 1 && calendarGranted()) {
            card.addView(action("Tria Google / Outlook") { calendarPicker() })
            card.addView(action("Obre calendari  ↗") { open(Intent(Intent.ACTION_VIEW, Uri.parse("content://com.android.calendar/time/${System.currentTimeMillis()}"))) })
        }
        block(parent, card)
        entrance(card, 2, animate)
    }

    private fun apps() = packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        .filter { it.activityInfo.packageName != packageName }
        .distinctBy { ComponentName(it.activityInfo.packageName, it.activityInfo.name).flattenToString() }
        .sortedBy { it.loadLabel(packageManager).toString().lowercase() }
    private fun key(info: ResolveInfo) = ComponentName(info.activityInfo.packageName, info.activityInfo.name).flattenToString()
    private fun pins(): List<String> = runCatching {
        val list = org.json.JSONArray(prefs.getString("pins", "[]")); (0 until list.length()).map { list.getString(it) }
    }.getOrDefault(emptyList())
    private fun pin(id: String) { val list = pins().toMutableList(); if (!list.remove(id)) list.add(id); prefs.edit().putString("pins", org.json.JSONArray(list).toString()).apply(); render() }
    private fun launch(info: ResolveInfo) { open(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(ComponentName(info.activityInfo.packageName, info.activityInfo.name)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    private fun addFavorites(parent: LinearLayout, columns: Int, animate: Boolean) {
        val all = apps(); val favorites = pins().mapNotNull { id -> all.find { key(it) == id } }
        if (favorites.isEmpty()) { block(parent, text("Mantén premuda una app de la llista per fixar-la aquí.", 15f, MetroStyle.muted)); return }
        val colors = listOf(0xff275992.toInt() to 0xff172a50.toInt(), 0xff4b417d.toInt() to 0xff262342.toInt(), 0xff23696b.toInt() to 0xff153b46.toInt(), 0xff765441.toInt() to 0xff3f2934.toInt())
        favorites.chunked(columns).forEachIndexed { rowIndex, row ->
            val line = LinearLayout(this)
            row.forEachIndexed { i, app ->
                val index = rowIndex * columns + i; val color = colors[index % colors.size]
                val item = tile(color.first, color.second, 13).apply { minimumHeight = dp(110) }
                item.addView(ImageView(this).apply { setImageDrawable(app.loadIcon(packageManager)); importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }, LinearLayout.LayoutParams(dp(36), dp(36)))
                item.addView(text(app.loadLabel(packageManager).toString(), 16f), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
                item.setOnClickListener { launch(app) }; item.setOnLongClickListener { pin(key(app)); true }
                item.contentDescription = app.loadLabel(packageManager).toString() + ". Mantén premut per treure el mosaic."
                line.addView(item, LinearLayout.LayoutParams(0, -1, 1f).apply { if(i < columns - 1) rightMargin = dp(8) })
                entrance(item, 3 + index, animate)
            }
            repeat(columns - row.size) { line.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f)) }
            block(parent, line, 8)
        }
    }
    private fun renderDrawer(root: LinearLayout) {
        val input = EditText(this).apply { setTextColor(MetroStyle.text); setHintTextColor(MetroStyle.muted); hint = "Cerca aplicacions"; setSingleLine(); setText(search) }
        root.addView(input)
        val list = column()
        fun fill() {
            list.removeAllViews()
            val visible = apps().filter { it.loadLabel(packageManager).toString().contains(search, true) }
            if (visible.isEmpty()) list.addView(text("Cap aplicació coincideix amb la cerca", 18f, MetroStyle.muted))
            visible.forEach { app ->
                val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; minimumHeight = dp(64) }
                row.addView(ImageView(this).apply { setImageDrawable(app.loadIcon(packageManager)); importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }, LinearLayout.LayoutParams(dp(36), dp(36)))
                row.addView(text(app.loadLabel(packageManager).toString() + if (key(app) in pins()) "  ▪" else "", 20f), LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(14) })
                row.setOnClickListener { launch(app) }; row.setOnLongClickListener { pin(key(app)); true }; list.addView(row)
            }
        }
        input.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { search = s.toString(); fill() }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        val scroll = ScrollView(this).apply { addView(list) }; drawerScroll = scroll
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f)); fill(); scroll.post { scroll.scrollTo(0, drawerY) }
    }

    private fun settings() {
        val effects = if (prefs.getBoolean("effects", true)) "activats" else "desactivats"
        AlertDialog.Builder(this).setTitle("Fold Metro / Magic V2").setItems(arrayOf("Segona ciutat", "Calendaris Google i Outlook", "Efectes visuals: $effects", "Actualitza temps i agenda", "Launcher per defecte", "Configuració d’Android")) { _, item ->
            when (item) {
                0 -> cityDialog(); 1 -> calendarPicker()
                2 -> { prefs.edit().putBoolean("effects", !prefs.getBoolean("effects", true)).apply(); entrancePending = true; render() }
                3 -> { refreshWeather(true); refreshAgenda() }; 4 -> chooseHome(); 5 -> open(Intent(Settings.ACTION_SETTINGS))
            }
        }.show()
    }
    private fun cityDialog() {
        val input = EditText(this).apply { setSingleLine(); hint = "Nom de la ciutat"; setText(weatherStore.extra()?.name ?: "") }
        val builder = AlertDialog.Builder(this).setTitle("Cabrils + el teu altre lloc").setMessage("Cabrils es manté fix. Escull la segona ciutat.").setView(input).setNegativeButton("Cancel·la", null)
            .setPositiveButton("Cerca") { _, _ ->
                val query = input.text.toString().trim()
                if (query.isNotEmpty()) {
                    cityJob?.cancel()
                    cityJob = lifecycleScope.launch {
                        try {
                            val results = withContext(Dispatchers.IO) { DashboardData.findCities(query) }
                            if (results.isEmpty()) { Toast.makeText(this@FoldActivity, "No s’ha trobat cap ciutat", Toast.LENGTH_LONG).show(); return@launch }
                            val labels = results.map { listOf(it.getString("name"), it.optString("admin1"), it.optString("country")).filter { part -> part.isNotBlank() }.joinToString(", ") }.toTypedArray()
                            AlertDialog.Builder(this@FoldActivity).setTitle("Escull la ubicació").setItems(labels) { _, index ->
                                val result = results[index]
                                weatherJobs.remove("extra")?.cancel(); weatherStatus.remove("extra")
                                weatherStore.setExtra(WeatherLocation("extra", result.getString("name"), result.getDouble("latitude"), result.getDouble("longitude")))
                                render(); refreshWeather(true)
                            }.setNegativeButton("Cancel·la", null).show()
                        } catch (e: CancellationException) { throw e }
                        catch (e: Exception) { Toast.makeText(this@FoldActivity, "No s’ha pogut cercar. Comprova la connexió.", Toast.LENGTH_LONG).show() }
                    }
                }
            }
        if (weatherStore.extra() != null) builder.setNeutralButton("Treu la segona ciutat") { _, _ -> weatherJobs.remove("extra")?.cancel(); weatherStatus.remove("extra"); weatherStore.setExtra(null); render() }
        builder.show()
    }
    private fun requestCalendar() {
        if (prefs.getBoolean("calendarAsked", false) && !shouldShowRequestPermissionRationale(Manifest.permission.READ_CALENDAR)) open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        else { prefs.edit().putBoolean("calendarAsked", true).apply(); permission.launch(Manifest.permission.READ_CALENDAR) }
    }
    private fun selectedCalendars(): Set<Long>? = prefs.getStringSet("selectedCalendars", null)?.mapNotNull { it.toLongOrNull() }?.toSet()
    private fun calendarPicker() {
        if (!calendarGranted()) { calendarPickerPending = true; requestCalendar(); return }
        lifecycleScope.launch {
            try {
                val available = withContext(Dispatchers.IO) { DashboardData.calendars(this@FoldActivity) }
                if (available.isEmpty()) { calendarHelp(); return@launch }
                val enabled = selectedCalendars()
                val checked = available.map { enabled?.contains(it.id) ?: it.visible }.toBooleanArray()
                val labels = available.map { "${it.provider} · ${it.name}\n${it.account}" }.toTypedArray()
                AlertDialog.Builder(this@FoldActivity).setTitle("Google + Outlook").setMultiChoiceItems(labels, checked) { _, which, chosen -> checked[which] = chosen }
                    .setPositiveButton("Desa") { _, _ -> prefs.edit().putStringSet("selectedCalendars", available.filterIndexed { index, _ -> checked[index] }.map { it.id.toString() }.toSet()).apply(); agendaReady = false; refreshAgenda() }
                    .setNeutralButton("Com connectar-los") { _, _ -> calendarHelp() }.setNegativeButton("Cancel·la", null).show()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { Toast.makeText(this@FoldActivity, "No s’han pogut consultar els calendaris", Toast.LENGTH_LONG).show() }
        }
    }
    private fun calendarHelp() {
        AlertDialog.Builder(this).setTitle("Google Calendar + Outlook").setMessage(
            "GOOGLE\nAfegeix el compte de Google a Android i activa la sincronització de Calendari.\n\nOUTLOOK\nA Outlook → Configuració → el teu compte → Sincronitza els calendaris. Després torna aquí i escull-los.\n\nEls comptes d’empresa poden restringir la sincronització o mantenir el calendari en un perfil de feina separat. El launcher només pot llegir calendaris accessibles al seu perfil d’Android."
        ).setPositiveButton("Obre Outlook") { _, _ -> openPackage("com.microsoft.office.outlook") }
            .setNeutralButton("Comptes d’Android") { _, _ -> open(Intent(Settings.ACTION_SYNC_SETTINGS)) }.setNegativeButton("Tanca", null).show()
    }
    private fun calendarGranted() = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
    private fun refreshAgenda() {
        if (!calendarGranted()) {
            if (observerRegistered) { contentResolver.unregisterContentObserver(calendarObserver); observerRegistered = false }
            val changed = agendaReady || events.isNotEmpty() || sources.isNotEmpty()
            events = emptyList(); sources = emptyList(); agendaReady = false
            if (changed) render()
            return
        }
        if (!observerRegistered) { contentResolver.registerContentObserver(CalendarContract.CONTENT_URI, true, calendarObserver); observerRegistered = true }
        agendaJob?.cancel()
        agendaJob = lifecycleScope.launch {
            try {
                val selection = selectedCalendars()
                val result = withContext(Dispatchers.IO) { DashboardData.calendars(this@FoldActivity) to DashboardData.agenda(this@FoldActivity, selection) }
                if (events == result.second && sources == result.first && agendaError == null && agendaReady) return@launch
                sources = result.first; events = result.second; agendaError = null; agendaReady = true
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { events = emptyList(); sources = emptyList(); agendaError = "No s’han pogut llegir els calendaris" }
            render()
        }
    }
    private fun refreshWeather(force: Boolean = false) {
        weatherStore.locations().forEach { location ->
            if (weatherJobs[location.id]?.isActive == true) return@forEach
            val snapshot = weatherStore.snapshot(location.id)
            val age = System.currentTimeMillis() - snapshot.attempt
            if (!force && age in 0 until 30 * 60000L) return@forEach
            weatherStore.attempt(location.id)
            weatherJobs[location.id] = lifecycleScope.launch {
                weatherStatus[location.id] = "Actualitzant…"
                try {
                    val result = withContext(Dispatchers.IO) { DashboardData.weather(location.latitude, location.longitude) }
                    weatherStore.save(location, result); weatherStatus[location.id] = ""
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { weatherStatus[location.id] = if (snapshot.data != null) "Dades desades · sense connexió" else "Sense connexió · reintenta" }
                render()
            }
        }
    }
    private fun openPackage(name: String) { val intent = packageManager.getLaunchIntentForPackage(name); if (intent != null) open(intent) else Toast.makeText(this, "L’aplicació no està instal·lada", Toast.LENGTH_LONG).show() }
    private fun open(intent: Intent) { try { startActivity(intent) } catch (e: Exception) { Toast.makeText(this, "No hi ha cap aplicació disponible per obrir-ho", Toast.LENGTH_LONG).show() } }
    private fun isDefaultHome() = packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName == packageName
    private fun chooseHome() {
        if (Build.VERSION.SDK_INT >= 29) {
            val roles = getSystemService(RoleManager::class.java)
            if (roles.isRoleAvailable(RoleManager.ROLE_HOME) && !roles.isRoleHeld(RoleManager.ROLE_HOME)) { open(roles.createRequestRoleIntent(RoleManager.ROLE_HOME)); return }
        }
        open(Intent(Settings.ACTION_HOME_SETTINGS))
    }
}
