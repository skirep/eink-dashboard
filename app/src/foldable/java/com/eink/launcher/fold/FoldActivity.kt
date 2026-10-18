package com.eink.launcher.fold

import android.Manifest
import android.app.role.RoleManager
import android.content.*
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.database.ContentObserver
import android.graphics.Color
import android.net.Uri
import android.os.*
import android.provider.CalendarContract
import android.provider.Settings
import android.view.*
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
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

/** A separate HOME implementation: the original e-ink experience stays in its own flavor. */
class FoldActivity : AppCompatActivity() {
    private val prefs by lazy { getSharedPreferences("fold-metro", MODE_PRIVATE) }
    private val bg = Color.rgb(16, 18, 22)
    private val ink = Color.rgb(239, 241, 235)
    private val muted = Color.rgb(168, 178, 182)
    private val teal = Color.rgb(36, 95, 94)
    private var hinge: FoldingFeature? = null
    private var appDrawer = false
    private var search = ""
    private var events: List<AgendaEvent> = emptyList()
    private var agendaError: String? = null
    private var agendaReady = false
    private var weather: JSONObject? = null
    private var weatherStatus = ""
    private var weatherJob: Job? = null
    private var agendaJob: Job? = null
    private var observerRegistered = false
    private var headerClock: TextView? = null
    private var homeScroll: ScrollView? = null
    private var drawerScroll: ScrollView? = null
    private var homeY = 0
    private var drawerY = 0
    private var rightY = 0
    private var rightScroll: ScrollView? = null
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { refreshAgenda() }
    private val calendarObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) { refreshAgenda() }
    }
    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { render() }
    }
    private val clockHandler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            headerClock?.text = clockText()
            refreshAgenda()
            refreshWeather()
            clockHandler.postDelayed(this, 60000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appDrawer = savedInstanceState?.getBoolean("drawer") ?: false
        search = savedInstanceState?.getString("search") ?: ""
        homeY = savedInstanceState?.getInt("homeY") ?: 0
        drawerY = savedInstanceState?.getInt("drawerY") ?: 0
        rightY = savedInstanceState?.getInt("rightY") ?: 0
        if (!prefs.contains("pins")) {
            val initial = apps().take(6).map { key(it) }
            if (initial.isNotEmpty()) prefs.edit().putString("pins", org.json.JSONArray(initial).toString()).apply()
        }
        weather = runCatching { JSONObject(prefs.getString("weather", "")!!) }.getOrNull()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { appDrawer = false; search = ""; render() }
        })
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                WindowInfoTracker.getOrCreate(this@FoldActivity).windowLayoutInfo(this@FoldActivity).collect { info ->
                    hinge = info.displayFeatures.filterIsInstance<FoldingFeature>().firstOrNull()
                    render()
                }
            }
        }
        render()
    }

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(this, packageReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED); addAction(Intent.ACTION_PACKAGE_REMOVED); addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
        refreshAgenda()
        clockHandler.post(tick)
    }
    override fun onStop() {
        clockHandler.removeCallbacks(tick)
        unregisterReceiver(packageReceiver)
        if (observerRegistered) { contentResolver.unregisterContentObserver(calendarObserver); observerRegistered = false }
        super.onStop()
    }
    override fun onResume() { super.onResume(); refreshAgenda() }
    override fun onConfigurationChanged(newConfig: Configuration) { super.onConfigurationChanged(newConfig); render() }
    override fun onSaveInstanceState(outState: Bundle) {
        saveScroll()
        outState.putBoolean("drawer", appDrawer); outState.putString("search", search)
        outState.putInt("homeY", homeY); outState.putInt("drawerY", drawerY); outState.putInt("rightY", rightY)
        super.onSaveInstanceState(outState)
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        appDrawer = false; search = ""; render()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()
    private fun clockText() = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
    private fun text(value: String, size: Float = 16f, color: Int = ink) = TextView(this).apply {
        text = value; textSize = size; setTextColor(color)
        setPadding(dp(4), dp(4), dp(4), dp(4))
        setLineSpacing(dp(3).toFloat(), 1f)
    }
    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun action(label: String, click: () -> Unit) = Button(this).apply {
        text = label; isAllCaps = false; textSize = 16f; minHeight = dp(52)
        setTextColor(ink); setBackgroundColor(Color.rgb(36, 42, 49)); setOnClickListener { click() }
    }
    private fun saveScroll() {
        homeScroll?.let { homeY = it.scrollY }
        drawerScroll?.let { drawerY = it.scrollY }
        rightScroll?.let { rightY = it.scrollY }
    }
    private fun render() {
        saveScroll(); homeScroll = null; drawerScroll = null; rightScroll = null
        val width = resources.configuration.screenWidthDp
        val wide = FoldPolicy.expanded(width, hinge != null)
        val root = column().apply { setBackgroundColor(bg); setPadding(dp(16), dp(10), dp(16), dp(8)) }
        root.fitsSystemWindows = true
        val top = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val title = column()
        title.addView(text("FOLD / METRO", 13f, Color.rgb(103, 213, 204)))
        headerClock = text(clockText(), 46f).also { title.addView(it) }
        title.addView(text(SimpleDateFormat("EEEE, d MMMM", Locale("ca")).format(Date()), 16f, muted))
        top.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
        top.addView(action("⚙") { settings() }.apply { contentDescription = "Configuració" }, LinearLayout.LayoutParams(dp(56), dp(56)))
        root.addView(top)
        val state = when {
            hinge?.state == FoldingFeature.State.HALF_OPENED -> "Mig obert · mode taula / llibre"
            hinge != null -> "Obert · pantalla interior"
            wide -> "Vista ampla · adaptació per mida"
            else -> "Vista compacta · adaptació per mida"
        }
        root.addView(text(state, 12f, muted))
        if (appDrawer) {
            renderDrawer(root)
        } else {
            val body = LinearLayout(this)
            val horizontalHinge = hinge?.orientation == FoldingFeature.Orientation.HORIZONTAL
            body.orientation = if (wide && !horizontalHinge) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            val left = column(); val right = column()
            addWeather(left, wide)
            if (!wide) addAgenda(left, 2)
            left.addView(text("EL TEU ESPAI", 12f, muted))
            addFavorites(left, if (wide) 2 else FoldPolicy.columns(width))
            if (wide) addAgenda(right, 8)
            right.addView(action("Totes les aplicacions  →") { appDrawer = true; render() })
            if (!isDefaultHome()) right.addView(action("Estableix com a inici") { chooseHome() })
            val separate = hinge?.isSeparating == true
            if (wide) {
                val first = ScrollView(this).apply { addView(left); isFillViewport = true }
                val second = ScrollView(this).apply { addView(right); isFillViewport = true }
                homeScroll = first; rightScroll = second
                val gap = View(this)
                if (body.orientation == LinearLayout.HORIZONTAL) {
                    body.addView(first, LinearLayout.LayoutParams(0, -1, 1f))
                    body.addView(gap, LinearLayout.LayoutParams(dp(16), -1))
                    body.addView(second, LinearLayout.LayoutParams(0, -1, 1f))
                } else {
                    body.addView(first, LinearLayout.LayoutParams(-1, 0, 1f))
                    body.addView(gap, LinearLayout.LayoutParams(-1, dp(16)))
                    body.addView(second, LinearLayout.LayoutParams(-1, 0, 1f))
                }
                root.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
                if (separate) body.post {
                    val bounds = hinge?.bounds ?: return@post
                    val origin = IntArray(2); body.getLocationInWindow(origin)
                    if (horizontalHinge) {
                        val before = (bounds.top - origin[1]).coerceIn(0, body.height)
                        first.layoutParams = LinearLayout.LayoutParams(-1, before)
                        gap.layoutParams = LinearLayout.LayoutParams(-1, bounds.height())
                    } else {
                        val before = (bounds.left - origin[0]).coerceIn(0, body.width)
                        first.layoutParams = LinearLayout.LayoutParams(before, -1)
                        gap.layoutParams = LinearLayout.LayoutParams(bounds.width(), -1)
                    }
                }
                first.post { first.scrollTo(0, homeY) }; second.post { second.scrollTo(0, rightY) }
            } else {
                val scroll = ScrollView(this).apply { isFillViewport = true }
                homeScroll = scroll
                body.addView(left); body.addView(right); scroll.addView(body)
                root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
                scroll.post { scroll.scrollTo(0, homeY) }
            }
        }
        setContentView(root)
    }

    private fun addWeather(parent: LinearLayout, expanded: Boolean) {
        val card = column().apply { setPadding(dp(14), dp(12), dp(14), dp(12)); setBackgroundColor(teal) }
        card.addView(text(prefs.getString("city", "Cabrils")!!, 18f))
        val current = weather?.optJSONObject("current")
        card.addView(text(if (current != null) "${current.optDouble("temperature_2m").roundToInt()}°" else "—°", 52f))
        card.addView(text(if (current != null) DashboardData.condition(current.optInt("weather_code")) else "Toca per carregar el temps", 18f))
        val stamp = prefs.getLong("weatherTime", 0)
        val status = when {
            weatherStatus.isNotEmpty() -> weatherStatus
            stamp > 0 -> "Actualitzat " + SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(stamp))
            else -> ""
        }
        card.addView(text(status, 12f))
        if (expanded) {
            val daily = weather?.optJSONObject("daily")
            val dates = daily?.optJSONArray("time")
            if (dates != null) for (i in 0 until dates.length()) {
                val day = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(dates.getString(i))
                val name = SimpleDateFormat("EEE d", Locale("ca")).format(day!!)
                card.addView(text("$name    ${daily.getJSONArray("temperature_2m_min").getDouble(i).roundToInt()}° / ${daily.getJSONArray("temperature_2m_max").getDouble(i).roundToInt()}°   ${DashboardData.condition(daily.getJSONArray("weather_code").getInt(i))}", 14f))
            }
        }
        card.addView(text("Open-Meteo · previsió meteorològica", 11f))
        card.setOnClickListener { refreshWeather(true) }
        card.contentDescription = "Temps. Toca per actualitzar"
        parent.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
    }

    private fun addAgenda(parent: LinearLayout, count: Int) {
        val card = column().apply { setBackgroundColor(Color.rgb(40, 48, 62)); setPadding(dp(12), dp(12), dp(12), dp(12)) }
        card.addView(text("AGENDA / 7 DIES", 13f, Color.rgb(168, 197, 235)))
        when {
            !calendarGranted() -> {
                card.addView(text("Els teus pròxims esdeveniments, aquí.", 20f))
                card.addView(action("Connecta l’agenda") {
                    if (prefs.getBoolean("calendarAsked", false) && !shouldShowRequestPermissionRationale(Manifest.permission.READ_CALENDAR)) {
                        open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
                    } else {
                        prefs.edit().putBoolean("calendarAsked", true).apply()
                        permission.launch(Manifest.permission.READ_CALENDAR)
                    }
                })
            }
            agendaError != null -> card.addView(text(agendaError!!))
            events.isEmpty() -> card.addView(text("Sense esdeveniments els pròxims 7 dies", 20f))
            else -> events.take(count).forEach { event ->
                val row = column().apply { setPadding(0, dp(8), 0, dp(12)) }
                row.addView(text(event.time(), 13f, Color.rgb(168, 197, 235)))
                row.addView(text(event.title, 22f))
                if (event.location.isNotBlank()) row.addView(text(event.location, 14f, muted))
                row.setOnClickListener {
                    open(Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, event.id))
                        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, event.start).putExtra(CalendarContract.EXTRA_EVENT_END_TIME, event.end))
                }
                card.addView(row)
            }
        }
        card.addView(action("Obre calendari  ↗") { open(Intent(Intent.ACTION_VIEW, Uri.parse("content://com.android.calendar/time/${System.currentTimeMillis()}"))) })
        parent.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
    }

    private fun apps() = packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        .filter { it.activityInfo.packageName != packageName }
        .distinctBy { ComponentName(it.activityInfo.packageName, it.activityInfo.name).flattenToString() }
        .sortedBy { it.loadLabel(packageManager).toString().lowercase() }
    private fun key(info: android.content.pm.ResolveInfo) = ComponentName(info.activityInfo.packageName, info.activityInfo.name).flattenToString()
    private fun pins(): List<String> = runCatching {
        val list = org.json.JSONArray(prefs.getString("pins", "[]"))
        (0 until list.length()).map { list.getString(it) }
    }.getOrDefault(emptyList())
    private fun pin(key: String) {
        val list = pins().toMutableList()
        if (!list.remove(key)) list.add(key)
        prefs.edit().putString("pins", org.json.JSONArray(list).toString()).apply(); render()
    }
    private fun launch(info: android.content.pm.ResolveInfo) {
        open(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(ComponentName(info.activityInfo.packageName, info.activityInfo.name)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    private fun addFavorites(parent: LinearLayout, columns: Int) {
        val all = apps(); val pinned = pins()
        val favorites = pinned.mapNotNull { id -> all.find { key(it) == id } }
        if (favorites.isEmpty()) {
            parent.addView(text("Fixa les teves apps amb una pulsació llarga a la llista d’aplicacions.", 16f, muted))
            return
        }
        val palette = intArrayOf(0xff3a5266.toInt(), 0xff77543d.toInt(), 0xff515c40.toInt(), 0xff61516e.toInt())
        favorites.chunked(columns).forEachIndexed { rowIndex, row ->
            val line = LinearLayout(this)
            row.forEachIndexed { i, app ->
                val tile = column().apply { setPadding(dp(12), dp(12), dp(12), dp(12)); setBackgroundColor(palette[(rowIndex * columns + i) % palette.size]) }
                tile.addView(ImageView(this).apply { setImageDrawable(app.loadIcon(packageManager)); contentDescription = null }, LinearLayout.LayoutParams(dp(36), dp(36)))
                tile.addView(text(app.loadLabel(packageManager).toString(), 18f))
                tile.minimumHeight = dp(116)
                tile.setOnClickListener { launch(app) }
                tile.setOnLongClickListener { pin(key(app)); true }
                line.addView(tile, LinearLayout.LayoutParams(0, -1, 1f).apply { setMargins(0, 0, dp(6), dp(6)) })
            }
            repeat(columns - row.size) { line.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f)) }
            parent.addView(line)
        }
    }

    private fun renderDrawer(root: LinearLayout) {
        root.addView(action("← Inici") { appDrawer = false; render() })
        val input = EditText(this).apply { setTextColor(ink); setHintTextColor(muted); hint = "Cerca aplicacions"; setSingleLine(); setText(search) }
        root.addView(input)
        val list = column()
        fun fill() {
            list.removeAllViews()
            val visible = apps().filter { it.loadLabel(packageManager).toString().contains(search, true) }
            if (visible.isEmpty()) list.addView(text("Cap aplicació coincideix amb la cerca", 18f, muted))
            visible.forEach { app ->
                val pinned = key(app) in pins()
                val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; minimumHeight = dp(64) }
                row.addView(ImageView(this).apply { setImageDrawable(app.loadIcon(packageManager)) }, LinearLayout.LayoutParams(dp(36), dp(36)))
                row.addView(text(app.loadLabel(packageManager).toString() + if (pinned) "  ▪" else "", 20f), LinearLayout.LayoutParams(0, -2, 1f))
                row.setOnClickListener { launch(app) }
                row.setOnLongClickListener { pin(key(app)); true }
                list.addView(row)
            }
        }
        input.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { search = s.toString(); fill() }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        val scroll = ScrollView(this); drawerScroll = scroll; scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f)); fill()
        scroll.post { scroll.scrollTo(0, drawerY) }
    }

    private fun settings() {
        AlertDialog.Builder(this).setTitle("Fold Metro").setItems(arrayOf("Ciutat del temps", "Actualitza temps i agenda", "Launcher per defecte", "Configuració d’Android")) { _, item ->
            when (item) {
                0 -> cityDialog()
                1 -> { refreshWeather(true); refreshAgenda() }
                2 -> chooseHome()
                3 -> open(Intent(Settings.ACTION_SETTINGS))
            }
        }.show()
    }
    private fun cityDialog() {
        val input = EditText(this).apply { setSingleLine(); setText(prefs.getString("city", "Cabrils")) }
        AlertDialog.Builder(this).setTitle("Ciutat del temps").setView(input).setNegativeButton("Cancel·la", null).setPositiveButton("Cerca") { _, _ ->
            val city = input.text.toString().trim()
            if (city.isNotEmpty()) {
                weatherJob?.cancel()
                weatherJob = lifecycleScope.launch {
                    weatherStatus = "Cercant ciutat…"; render()
                    try {
                        val result = withContext(Dispatchers.IO) { DashboardData.findCity(city) }
                        val label = "${result.getString("name")}, ${result.optString("country")}" 
                        AlertDialog.Builder(this@FoldActivity).setTitle("Utilitza aquesta ubicació?").setMessage(label + "\n" + result.optString("admin1"))
                            .setNegativeButton("Cancel·la") { _, _ -> weatherStatus = ""; render() }
                            .setOnCancelListener { weatherStatus = ""; render() }
                            .setPositiveButton("Utilitza") { _, _ ->
                                prefs.edit().putString("city", label).putString("lat", result.getDouble("latitude").toString())
                                    .putString("lon", result.getDouble("longitude").toString()).remove("weather").putLong("weatherTime", 0).apply()
                                weather = null; weatherStatus = ""; refreshWeather(true)
                            }.show()
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) { weatherStatus = "No s'ha trobat la ciutat o no hi ha connexió"; render() }
                }
            }
        }.show()
    }
    private fun calendarGranted() = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
    private fun refreshAgenda() {
        if (!calendarGranted()) { events = emptyList(); agendaReady = false; render(); return }
        if (!observerRegistered) {
            contentResolver.registerContentObserver(CalendarContract.CONTENT_URI, true, calendarObserver); observerRegistered = true
        }
        agendaJob?.cancel()
        agendaJob = lifecycleScope.launch {
            try {
                val updated = withContext(Dispatchers.IO) { DashboardData.agenda(this@FoldActivity) }
                if (events == updated && agendaError == null && agendaReady) return@launch
                events = updated; agendaError = null; agendaReady = true
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { events = emptyList(); agendaError = "No s’ha pogut llegir el calendari del dispositiu" }
            render()
        }
    }
    private fun refreshWeather(force: Boolean = false) {
        if (weatherJob?.isActive == true) return
        if (!force && System.currentTimeMillis() - prefs.getLong("weatherAttempt", 0) < 30 * 60000L) return
        prefs.edit().putLong("weatherAttempt", System.currentTimeMillis()).apply()
        weatherJob = lifecycleScope.launch {
            weatherStatus = "Actualitzant…"; render()
            try {
                val result = withContext(Dispatchers.IO) { DashboardData.weather(prefs.getString("lat", "41.5276")!!.toDouble(), prefs.getString("lon", "2.3691")!!.toDouble()) }
                weather = result
                prefs.edit().putString("weather", result.toString()).putLong("weatherTime", System.currentTimeMillis()).apply()
                weatherStatus = ""
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { weatherStatus = if (weather != null) "Sense connexió · dades desades" else "Temps no disponible · toca per reintentar" }
            render()
        }
    }
    private fun open(intent: Intent) {
        try { startActivity(intent) } catch (e: Exception) { Toast.makeText(this, "No hi ha cap aplicació disponible per obrir-ho", Toast.LENGTH_LONG).show() }
    }
    private fun isDefaultHome(): Boolean = packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName == packageName
    private fun chooseHome() {
        if (Build.VERSION.SDK_INT >= 29) {
            val roles = getSystemService(RoleManager::class.java)
            if (roles.isRoleAvailable(RoleManager.ROLE_HOME) && !roles.isRoleHeld(RoleManager.ROLE_HOME)) { open(roles.createRequestRoleIntent(RoleManager.ROLE_HOME)); return }
        }
        open(Intent(Settings.ACTION_HOME_SETTINGS))
    }
}
