package com.eink.launcher.fold

import android.Manifest
import android.animation.ValueAnimator
import android.app.role.RoleManager
import android.content.*
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.content.res.Configuration
import android.database.ContentObserver
import android.graphics.Bitmap
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
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
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
import androidx.viewpager.widget.ViewPager
import com.eink.launcher.R

class FoldActivity : AppCompatActivity() {
    private val prefs by lazy { getSharedPreferences("fold-metro", MODE_PRIVATE) }
    private val weatherStore by lazy { WeatherStore(prefs) }
    private val wallpaperStore by lazy { WallpaperStore(this) }
    private var wallpaper: Bitmap? = null
    private var softenedWallpaper: Bitmap? = null
    private val panels by lazy { DashboardPanels(this, prefs, { renderData() }, { open(it) }) }
    private var wallpaperJob: Job? = null
    private val imagePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importWallpaper(uri)
    }
    internal fun importWallpaper(uri: Uri) {
        val pending = wallpaperJob
        val photoTheme = LauncherThemes.id(prefs.getString("theme", LauncherThemes.METRO))
        Toast.makeText(this, "Preparació de la imatge…", Toast.LENGTH_SHORT).show()
        wallpaperJob = lifecycleScope.launch {
            pending?.join()
            try {
                wallpaper = wallpaperStore.import(uri)
                prefs.edit().putBoolean("themePhoto_$photoTheme", true).apply()
                softenedWallpaper = withContext(Dispatchers.Default) { wallpaper?.let(WeatherBackdrop::soften) }
                renderData()
                Toast.makeText(this@FoldActivity, "Imatge de fons desada", Toast.LENGTH_SHORT).show()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { Toast.makeText(this@FoldActivity, "No s’ha pogut obrir la imatge. Tria’n una altra (màxim 50 MB).", Toast.LENGTH_LONG).show() }
        }
    }
    private var hinge: FoldingFeature? = null
    private var pages: LauncherPager? = null
    private var homePrompt: View? = null
    private val homeRole = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        homePrompt?.visibility = if (isDefaultHome()) View.GONE else View.VISIBLE
    }
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
    private var dataRenderPending = false
    private var layoutRenderPending = false
    private var touchSequenceActive = false
    internal val perspective by lazy { PanelPerspective(this) }
    private var resumed = false
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
            refreshAgenda(); refreshWeather(); panels.refresh(); handler.postDelayed(this, 60000)
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
            val initial = apps().take(4).map { key(it) }
            if (initial.isNotEmpty()) prefs.edit().putString("pins", org.json.JSONArray(initial).toString()).apply()
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { appDrawer = false; search = ""; entrancePending = true; render() }
        })
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                WindowInfoTracker.getOrCreate(this@FoldActivity).windowLayoutInfo(this@FoldActivity).collect { info ->
                    val feature = info.displayFeatures.filterIsInstance<FoldingFeature>().firstOrNull()
                    if (hinge != feature) { hinge = feature; entrancePending = true; render() }
                }
            }
        }
        panels.start()
        render()
        wallpaperJob = lifecycleScope.launch {
            wallpaper = wallpaperStore.load()
            softenedWallpaper = withContext(Dispatchers.Default) { wallpaper?.let(WeatherBackdrop::soften) }
            renderData()
        }
    }
    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(this, packageReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED); addAction(Intent.ACTION_PACKAGE_REMOVED); addAction(Intent.ACTION_PACKAGE_CHANGED); addDataScheme("package")
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
        handler.post(tick)
    }
    override fun onResume() {
        super.onResume(); resumed = true; refreshAgenda(); renderData(); syncPerspective()
        homePrompt?.visibility = if (isDefaultHome()) View.GONE else View.VISIBLE
    }
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) { applyFullscreen(); pages?.let { flushPendingRender(it) } }
        syncPerspective()
    }
    private fun applyFullscreen() {
        // Keep normal inset/IME resizing; hidden bars have zero visible insets.
        // Android still lets the user reveal transient bars with an edge swipe.
        WindowCompat.setDecorFitsSystemWindows(window, true)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (prefs.getBoolean("fullscreen", false)) hide(WindowInsetsCompat.Type.systemBars())
            else show(WindowInsetsCompat.Type.systemBars())
        }
    }
    internal fun setFullscreen(enabled: Boolean) {
        prefs.edit().putBoolean("fullscreen", enabled).apply()
        applyFullscreen()
    }
    override fun onPause() { resumed = false; perspective.touch(false); perspective.enable(false); super.onPause() }
    private fun syncPerspective() {
        perspective.enable(resumed && hasWindowFocus() && !appDrawer && effectsAllowed() && prefs.getBoolean("perspective", true))
    }
    private fun renderData() {
        // Data updates must not replace a focused search field or disturb an open dialog.
        if (appDrawer || touchSequenceActive || pages?.pointerDown == true || !hasWindowFocus() || (pages?.pagingState ?: ViewPager.SCROLL_STATE_IDLE) != ViewPager.SCROLL_STATE_IDLE) dataRenderPending = true else render()
    }
    override fun onStop() {
        touchSequenceActive = false
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

    // The clock, photos and dock live outside the pager. A data refresh must not
    // remove their pressed view between DOWN and UP either.
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) { touchSequenceActive = true; perspective.touch(true) }
        return try { super.dispatchTouchEvent(event) } finally {
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                touchSequenceActive = false
                perspective.touch(false)
                pages?.let { pager -> pager.post { flushPendingRender(pager) } }
            }
        }
    }

    private fun showApps(show: Boolean) {
        (getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
            .hideSoftInputFromWindow(window.decorView.windowToken, 0)
        pages?.setCurrentItem(if (show) 1 else 0, effectsAllowed())
    }

    private fun dp(n: Int) = (n * resources.displayMetrics.density).roundToInt()
    private fun clockText() = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
    private fun dateText() = SimpleDateFormat("EEEE, d MMMM", Locale("ca")).format(Date())
    private fun text(value: String, size: Float = 16f, color: Int = MetroStyle.text) = TextView(this).apply {
        text = value; textSize = size; setTextColor(color); setPadding(0, dp(2), 0, dp(2))
        typeface = if (MetroStyle.terminal) Typeface.MONOSPACE else Typeface.create("sans-serif-light", Typeface.NORMAL)
        setLineSpacing(dp(2).toFloat(), 1f)
    }
    private fun caption(value: String, color: Int = MetroStyle.muted) = text(value, 11f, color).apply { letterSpacing = if (MetroStyle.terminal) 0f else .1f; typeface = if (MetroStyle.terminal) Typeface.MONOSPACE else Typeface.create("sans-serif-medium", Typeface.NORMAL) }
    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun action(label: String, click: () -> Unit) = Button(this).apply {
        text = label; isAllCaps = false; textSize = 14f; minHeight = dp(48); minimumWidth = 0
        if (MetroStyle.terminal) typeface = Typeface.MONOSPACE
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
        if (prefs.getBoolean("perspective", true)) {
            view.alpha = .2f
            view.animate().alpha(1f).setDuration(520).setStartDelay((index * 45).toLong()).start()
            return
        }
        view.alpha = .2f; view.translationY = dp(22).toFloat(); view.rotationX = 4f
        view.animate().alpha(1f).translationY(0f).rotationX(0f).setDuration(520).setStartDelay((index * 45).toLong()).setInterpolator(DecelerateInterpolator()).start()
    }
    private fun saveScroll() { homeScroll?.let { homeY = it.scrollY }; rightScroll?.let { rightY = it.scrollY }; drawerScroll?.let { drawerY = it.scrollY } }

    private fun flushPendingRender(pager: LauncherPager) {
        if (pages !== pager || !hasWindowFocus() || touchSequenceActive || pager.pointerDown || pager.pagingState != ViewPager.SCROLL_STATE_IDLE) return
        if (layoutRenderPending) render()
        else if (dataRenderPending && !appDrawer) renderData()
    }

    private fun render() {
        // Configuration and package callbacks also rebuild the hierarchy. Guard the
        // single render entry point, not only asynchronous weather/mail updates.
        if (touchSequenceActive || pages?.pointerDown == true || (pages?.pagingState ?: ViewPager.SCROLL_STATE_IDLE) != ViewPager.SCROLL_STATE_IDLE) {
            layoutRenderPending = true
            return
        }
        layoutRenderPending = false
        dataRenderPending = false
        LauncherThemes.select(prefs.getString("theme", LauncherThemes.METRO))
        saveScroll(); homeScroll = null; rightScroll = null; drawerScroll = null
        val width = resources.configuration.screenWidthDp
        val wide = FoldPolicy.expanded(width, hinge != null)
        val animate = entrancePending || (renderedWide != null && renderedWide != wide)
        entrancePending = false; renderedWide = wide
        val snapshot = weatherStore.snapshot("cabrils")
        val sky = snapshot.data?.optJSONObject("current")?.takeIf {
            System.currentTimeMillis() - snapshot.updated in 0..2 * 60 * 60000L
        }
        val theme = LauncherThemes.id(prefs.getString("theme", LauncherThemes.METRO))
        val ownBackdrop = (MetroStyle.windows || MetroStyle.desktop || MetroStyle.pirate || MetroStyle.linux) && !prefs.getBoolean("themePhoto_$theme", false)
        val surface: FrameLayout = if (MetroStyle.terminal) TerminalScene(this)
        else if (MetroStyle.linux && (ownBackdrop || wallpaper == null)) LinuxScene(this)
        else if (MetroStyle.desktop && (ownBackdrop || wallpaper == null)) DesktopScene(this, sky?.optInt("weather_code", -1) ?: -1, sky?.optInt("is_day", 1) == 0, prefs.getBoolean("effects", true))
        else WeatherScene(this, sky?.optInt("weather_code", -1) ?: -1,
            sky?.optInt("is_day", 1) == 0, prefs.getBoolean("effects", true) && !(MetroStyle.windows && ownBackdrop), animate,
            if (MetroStyle.pirate && (ownBackdrop || wallpaper == null)) PirateBackdrop.image(this) else if (prefs.getBoolean("photoBlur", true)) softenedWallpaper ?: wallpaper else wallpaper, prefs.getBoolean("photoWeather", true),
            nightArtwork = MetroStyle.pirate && (ownBackdrop || wallpaper == null))
        if (MetroStyle.cyberpunk) surface.addView(CyberpunkOverlay(this), FrameLayout.LayoutParams(-1, -1))
        if (MetroStyle.windows) surface.addView(View(this).apply { setBackgroundColor(if (ownBackdrop || wallpaper == null) Color.BLACK else 0x99000000.toInt()) }, FrameLayout.LayoutParams(-1, -1))
        val root = column().apply { setPadding(dp(20), dp(12), dp(20), dp(8)); fitsSystemWindows = true }
        surface.addView(root, FrameLayout.LayoutParams(-1, -1))
        val heading = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            if (MetroStyle.desktop) { tag = "desktop-surface"; background = MetroStyle.surface(0xffedf1fa.toInt(), 0xffdce5f5.toInt(), dp(14).toFloat()); setPadding(dp(12), dp(8), dp(12), dp(8)) }
        }
        val title = column()
        val brand = when {
            MetroStyle.linux -> "oriol@magicfold:~"
            MetroStyle.terminal -> "C:\\MAGICFOLD> HOME_"
            MetroStyle.cyberpunk -> "MAGICFOLD // NIGHT CITY"
            MetroStyle.windows -> "Inici"
            MetroStyle.desktop -> "MagicFold · Escriptori"
            MetroStyle.pirate -> "El quadern del capità"
            else -> "MagicFold     ·     Magic V2"
        }
        title.addView(if (MetroStyle.windows || MetroStyle.desktop || MetroStyle.pirate || MetroStyle.linux)
            text(brand, if (MetroStyle.windows) 30f else if (MetroStyle.pirate) 17f else 11f, MetroStyle.mint)
            else caption(brand, MetroStyle.mint))
        val hourLine = LinearLayout(this).apply { gravity = Gravity.BOTTOM }
        headerClock = text(clockText(), if (MetroStyle.windows || MetroStyle.desktop || MetroStyle.linux) 28f else if (MetroStyle.pirate) 36f else if (width < 420) 40f else 50f).apply {
            contentDescription = "Obre rellotge i alarmes"; isFocusable = true; setOnClickListener { openClock() }
        }.also { hourLine.addView(it) }
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        if (!MetroStyle.windows && !MetroStyle.desktop && !MetroStyle.pirate && !MetroStyle.linux && resources.configuration.fontScale <= 1.15f) hourLine.addView(text(when (hour) { in 6..12 -> "Bon dia."; in 13..19 -> "Bona tarda."; else -> "Bona nit." }, 14f, MetroStyle.muted), LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(14); bottomMargin = dp(12) })
        title.addView(hourLine)
        title.addView(caption("◷  Rellotge i alarmes").apply {
            isFocusable = true; setOnClickListener { openClock() }; contentDescription = "Rellotge i alarmes"
        })
        headerDate = text(dateText(), 15f, MetroStyle.muted).also { title.addView(it) }
        heading.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
        val controls = column().apply { gravity = Gravity.CENTER }
        controls.addView(action("⚙") { settings() }.apply { contentDescription = "Configuració" }, LinearLayout.LayoutParams(dp(48), dp(48)))
        controls.addView(text(if (MetroStyle.pirate) "✧  Mapa" else "▧  Fotos", 12f).apply {
            minimumHeight = dp(48); gravity = Gravity.CENTER; isFocusable = true
            contentDescription = if (MetroStyle.pirate) "Mapa del capità" else "Les meves fotos"
            setOnClickListener { if (MetroStyle.pirate) captainMap() else wallpaperSettings() }
        }, LinearLayout.LayoutParams(-2, dp(48)))
        heading.addView(controls)
        root.addView(heading)
        val mode = when {
            hinge?.state == FoldingFeature.State.HALF_OPENED -> "MIG OBERT / dues zones"
            hinge != null -> "OBERT / espai ampliat"
            wide -> "Vista ampla"
            else -> "Vista compacta"
        }
        val navigation = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val homeTab = caption("$mode · Inici", MetroStyle.mint).apply {
            minimumHeight = dp(48); gravity = Gravity.CENTER_VERTICAL
            contentDescription = "Pàgina inici"; isFocusable = true
            setOnClickListener { showApps(false) }
        }
        val appsTab = caption("Aplicacions →", MetroStyle.blue).apply {
            minimumHeight = dp(48); gravity = Gravity.CENTER_VERTICAL or Gravity.END
            contentDescription = "Pàgina aplicacions"; isFocusable = true
            setOnClickListener { showApps(true) }
        }
        navigation.addView(homeTab, LinearLayout.LayoutParams(0, -2, 1f))
        navigation.addView(appsTab, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(navigation)
        val homePage = column()
        val appsPage = column().apply {
            if (MetroStyle.desktop) { tag = "desktop-surface"; background = MetroStyle.surface(0xffedf1fa.toInt(), 0xffdce5f5.toInt(), dp(14).toFloat()); setPadding(dp(14), dp(8), dp(14), 0) }
        }
        appsPage.addView(caption("TOTES LES APLICACIONS", MetroStyle.mint))
        renderDrawer(appsPage)
        run {
            val left = column().apply { clipChildren = false }; val right = column().apply { clipChildren = false }
            if (MetroStyle.windows) {
                block(left, caption("APLICACIONS FIXADES"), 6)
                addFavorites(left, if (wide) 4 else 3, animate)
                addWeather(left, wide, animate)
                addAgenda(left, if (wide) 3 else 1, animate)
                panels.mail(if (wide) right else left, wide)
                panels.news(if (wide) right else left, wide)
            } else if (MetroStyle.desktop || MetroStyle.linux) {
                addWeather(left, wide, animate)
                block(left, caption("APLICACIONS PREFERIDES"), 6)
                addFavorites(left, 4, animate)
                addAgenda(if (wide) right else left, if (wide) 3 else 1, animate)
                panels.mail(if (wide) right else left, wide)
                panels.news(if (wide) right else left, wide)
            } else {
                addWeather(left, wide, animate)
                if (wide) {
                    addAgenda(left, 3, animate)
                    block(left, caption("A MÀ"), 7)
                    addFavorites(left, 4, animate)
                    panels.mail(right, true)
                    panels.news(right, true)
                } else {
                    val overview = LinearLayout(this).apply { clipChildren = false }
                    val agendaColumn = column().apply { clipChildren = false }; val mailColumn = column().apply { clipChildren = false }
                    addAgenda(agendaColumn, 1, animate); panels.mail(mailColumn, false)
                    overview.addView(agendaColumn, LinearLayout.LayoutParams(0, -2, 1.45f).apply { rightMargin = dp(8) })
                    overview.addView(mailColumn, LinearLayout.LayoutParams(0, -2, 1f))
                    block(left, overview, 0)
                    panels.news(left, false)
                    block(left, caption("A MÀ"), 7)
                    addFavorites(left, if (width >= 360 && resources.configuration.fontScale <= 1.15f) 4 else 3, animate)
                }
            }
            homePrompt = action("Estableix com a inici") { chooseHome() }.apply {
                visibility = if (isDefaultHome()) View.GONE else View.VISIBLE
            }
            block(right, homePrompt!!)
            if (wide && (MetroStyle.windows || MetroStyle.desktop || MetroStyle.linux) && prefs.getBoolean("desktopEnabled", true)) {
                homePage.addView(DesktopWorkspace(this, prefs, theme, listOf(left,right)), LinearLayout.LayoutParams(-1,0,1f))
            } else addPanels(homePage, left, right, wide)
        }
        // ViewPager attaches adapter pages only during layout. Theme their full
        // trees now, while they are still detached from the Activity root.
        LauncherThemes.style(homePage)
        LauncherThemes.style(appsPage)
        val selectedPage = if (appDrawer) 1 else 0
        val pager = LauncherPager(this).apply {
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            offscreenPageLimit = 1
            adapter = LauncherPages(listOf(homePage, appsPage))
            setCurrentItem(selectedPage, false)
        }
        pages = pager
        pager.onInteractionFinished = { flushPendingRender(pager) }
        fun updateNavigation(position: Int) {
            homeTab.isSelected = position == 0; appsTab.isSelected = position == 1
            homeTab.text = if (position == 0) "$mode · Inici" else "← Inici"
            appsTab.text = if (position == 0) "Aplicacions →" else "Aplicacions · 2/2"
            homeTab.setTextColor(if (position == 0) MetroStyle.mint else MetroStyle.muted)
            appsTab.setTextColor(if (position == 1) MetroStyle.mint else MetroStyle.blue)
        }
        updateNavigation(selectedPage)
        pager.addOnPageChangeListener(object : ViewPager.SimpleOnPageChangeListener() {
            override fun onPageSelected(position: Int) {
                appDrawer = position == 1
                syncPerspective()
                updateNavigation(position)
                if (!appDrawer) (getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
                    .hideSoftInputFromWindow(window.decorView.windowToken, 0)
            }
            override fun onPageScrollStateChanged(state: Int) {
                if (state == ViewPager.SCROLL_STATE_IDLE) pager.post { flushPendingRender(pager) }
            }
        })
        root.addView(pager, LinearLayout.LayoutParams(-1, 0, 1f))
        val dock = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            if (MetroStyle.desktop || MetroStyle.pirate) setPadding(dp(5), dp(8), dp(5), dp(4))
            else setPadding(0, dp(8), 0, 0)
            if (MetroStyle.desktop) { tag = "desktop-surface"; background = MetroStyle.surface(0xeef0f4ff.toInt(), 0xdde0e6f4.toInt(), dp(22).toFloat()) }
            if (MetroStyle.pirate) background = PirateFrame()
        }
        data class DockAction(val label: String, val icon: Int, val click: () -> Unit)
        val dockItems = mutableListOf(DockAction("Trucades", R.drawable.magic_phone) { open(Intent(Intent.ACTION_DIAL)) },
            DockAction("WhatsApp", R.drawable.magic_whatsapp) { openWhatsApp() },
            DockAction("Agenda", R.drawable.magic_calendar) { openCalendar() })
        if (MetroStyle.desktop) dockItems += DockAction("Apps", android.R.drawable.ic_menu_sort_by_size) { showApps(true) }
        if (MetroStyle.pirate) dockItems += DockAction("Mapa", android.R.drawable.ic_menu_compass) { captainMap() }
        dockItems.forEachIndexed { index, item ->
            val control = column().apply {
                gravity = Gravity.CENTER; minimumHeight = dp(64); isFocusable = true
                background = if (MetroStyle.desktop) null else if (MetroStyle.pirate) PirateFrame() else MetroStyle.surface(0xdd152c44.toInt(), 0xdd0e2034.toInt(), dp(9).toFloat())
                contentDescription = item.label; setOnClickListener { item.click() }
                if (item.label == "Agenda") setOnLongClickListener { calendarPicker(); true }
                addView(ImageView(this@FoldActivity).apply {
                    tag = if (MetroStyle.desktop) "desktop-dock-icon" else "theme-icon"
                    setImageResource(item.icon); importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    if (MetroStyle.desktop) {
                        setPadding(dp(6), dp(6), dp(6), dp(6))
                        background = android.graphics.drawable.GradientDrawable().apply {
                            setColor(intArrayOf(0xff29af65.toInt(), 0xff15aa8b.toInt(), 0xff3875df.toInt(), 0xff5a9ee7.toInt())[index]); cornerRadius = dp(9).toFloat()
                        }
                    }
                }, LinearLayout.LayoutParams(dp(if (MetroStyle.desktop) 36 else 29), dp(if (MetroStyle.desktop) 36 else 29)))
                addView(text(item.label, 12f).apply { gravity = Gravity.CENTER })
            }
            dock.addView(control, LinearLayout.LayoutParams(0, dp(68), 1f).apply { if (index < dockItems.lastIndex) rightMargin = dp(7) })
        }
        if (MetroStyle.desktop) {
            val dockShelf = FrameLayout(this)
            dockShelf.addView(dock, FrameLayout.LayoutParams(if (wide) dp(420) else -1, -2, Gravity.CENTER))
            root.addView(dockShelf, LinearLayout.LayoutParams(-1, -2))
        } else root.addView(dock)
        LauncherThemes.style(root)
        setContentView(surface)
        applyFullscreen()
        // The pager has no adapter children until its first layout. Bind the
        // already populated home tree instead, so real panels receive sensor updates.
        perspective.bind(homePage)
        syncPerspective()
    }

    private fun addPanels(root: LinearLayout, left: LinearLayout, right: LinearLayout, wide: Boolean) {
        val body = LinearLayout(this).apply { clipChildren = false; clipToPadding = false }
        val horizontal = hinge?.orientation == FoldingFeature.Orientation.HORIZONTAL
        body.orientation = if (wide && !horizontal) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        if (wide) {
            val first = ScrollView(this).apply { setPadding(dp(8), dp(8), dp(8), dp(12)); clipToPadding = false; addView(left); isFillViewport = true; isVerticalScrollBarEnabled = false }
            val second = ScrollView(this).apply { setPadding(dp(8), dp(8), dp(8), dp(12)); clipToPadding = false; addView(right); isFillViewport = true; isVerticalScrollBarEnabled = false }
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
            val scroll = ScrollView(this).apply { setPadding(dp(8), dp(8), dp(8), dp(12)); clipToPadding = false; addView(body); isVerticalScrollBarEnabled = false }
            homeScroll = scroll; root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f)); scroll.post { scroll.scrollTo(0, homeY) }
        }
    }

    private fun addWeather(parent: LinearLayout, wide: Boolean, animate: Boolean) {
        val line = LinearLayout(this).apply { tag = "desktop-weather"; clipChildren = false; clipToPadding = false }
        listOf(WeatherLocations.cabrils, weatherStore.extra()).forEachIndexed { i, location ->
            val snapshot = location?.let { weatherStore.snapshot(it.id) }
            val current = snapshot?.data?.optJSONObject("current")
            val night = current?.optInt("is_day", 1) == 0
            val code = current?.optInt("weather_code", -1) ?: -1
            val card = tile(if (i == 0) 0xff176d71.toInt() else 0xff434381.toInt(), if (i == 0) 0xff112c46.toInt() else 0xff201e41.toInt(), 11)
            card.addView(caption(if (i == 0) "A CASA" else "SEGONA CIUTAT", 0xffd4e7ff.toInt()))
            card.addView(text(location?.name ?: "Tria una ciutat", 17f))
            val visual = FrameLayout(this)
            visual.addView(WeatherArt(this, code, night).apply { importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }, FrameLayout.LayoutParams(dp(58), dp(58), Gravity.END or Gravity.CENTER_VERTICAL))
            visual.addView(text(if (current != null) "${current.optDouble("temperature_2m").roundToInt()}°" else if (location == null) "+" else "—°", 39f), FrameLayout.LayoutParams(-2, -2, Gravity.START or Gravity.CENTER_VERTICAL))
            card.addView(visual, LinearLayout.LayoutParams(-1, dp(62)))
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
        block(parent, text("Fons viu de Cabrils · Open-Meteo", 11f, MetroStyle.muted), 7)
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
        val card = tile(0xff202d4c.toInt(), 0xff121b30.toInt(), if (count == 1) 11 else 16)
        card.tag = "desktop-agenda"
        card.addView(caption("AGENDA / 7 DIES", MetroStyle.blue))
        val enabled = selectedCalendars()
        val active = sources.filter { enabled?.contains(it.id) ?: it.visible }
        val summary = if (calendarGranted() && agendaReady) {
            val google = active.count { it.provider == "Google" }; val outlook = active.count { it.provider == "Outlook / Microsoft" }
            "Google · $google    Outlook · $outlook"
        } else "Google Calendar + Outlook"
        card.addView(text(summary, 11f, MetroStyle.muted).apply { isFocusable = true; setOnClickListener { calendarPicker() }; contentDescription = "Configura calendaris" })
        when {
            !calendarGranted() -> {
                if (count > 3) card.addView(text("Tot el teu dia,\nen un sol lloc.", 24f))
                block(card, action("Connecta els calendaris") { requestCalendar() }, 0)
            }
            agendaError != null -> card.addView(text(agendaError!!, 16f))
            !agendaReady -> card.addView(text("Carregant calendaris…", 16f))
            sources.isEmpty() -> {
                if (count > 3) card.addView(text("Activa la sincronització de Google i Outlook per veure els esdeveniments aquí.", 16f))
                card.addView(action("Configura els calendaris") { calendarHelp() })
            }
            active.isEmpty() -> card.addView(action("Escull els calendaris que vols veure") { calendarPicker() })
            events.isEmpty() -> card.addView(text(if (count == 1) "Cap acte els pròxims 7 dies." else "Cap acte els pròxims 7 dies.", if (count == 1) 16f else 20f))
            else -> events.take(count).forEach { event ->
                val source = sources.find { it.id == event.calendarId }
                val row = column().apply { setPadding(dp(10), dp(5), dp(2), dp(5)) }
                row.addView(caption(event.time(), MetroStyle.mint))
                row.addView(text(event.title, if (count == 1) 15f else 17f).apply {
                    if (count <= 3) { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }
                })
                if (count > 1) row.addView(text(source?.provider.orEmpty(), 11f, MetroStyle.blue))
                if (count > 1 && event.location.isNotBlank()) row.addView(text(event.location, 13f, MetroStyle.muted))
                val eventLine = LinearLayout(this)
                eventLine.addView(View(this).apply { setBackgroundColor(if (source?.provider == "Google") MetroStyle.mint else MetroStyle.blue) }, LinearLayout.LayoutParams(dp(2), -1))
                eventLine.addView(row, LinearLayout.LayoutParams(0, -2, 1f))
                eventLine.setOnClickListener { open(Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, event.id)).putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, event.start).putExtra(CalendarContract.EXTRA_EVENT_END_TIME, event.end)) }
                card.addView(eventLine)
            }
        }
        if (count > 3 && calendarGranted()) {
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
    private fun pin(id: String) { val list = pins().toMutableList(); if (!list.remove(id)) list.add(0, id); prefs.edit().putString("pins", org.json.JSONArray(list).toString()).apply(); render() }
    private fun launch(info: ResolveInfo) { open(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(ComponentName(info.activityInfo.packageName, info.activityInfo.name)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    private fun addFavorites(parent: LinearLayout, columns: Int, animate: Boolean) {
        val all = apps(); val favorites = pins().distinct().mapNotNull { id -> all.find { key(it) == id } }
        if (favorites.isEmpty()) { block(parent, text("Mantén premuda una app de la llista per fixar-la aquí.", 15f, MetroStyle.muted).apply { tag = "desktop-favorites-row" }); return }
        val colors = listOf(0xff275992.toInt() to 0xff172a50.toInt(), 0xff4b417d.toInt() to 0xff262342.toInt(), 0xff23696b.toInt() to 0xff153b46.toInt(), 0xff765441.toInt() to 0xff3f2934.toInt())
        favorites.chunked(columns).forEachIndexed { rowIndex, row ->
            val line = LinearLayout(this).apply { tag = "desktop-favorites-row"; clipChildren = false; clipToPadding = false }
            row.forEachIndexed { i, app ->
                val index = rowIndex * columns + i; val color = colors[index % colors.size]
                val item = tile(color.first, color.second, 9).apply { minimumHeight = dp(78); tag = "favorite:" + key(app) }
                item.addView(ImageView(this).apply { setImageDrawable(app.loadIcon(packageManager)); importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }, LinearLayout.LayoutParams(dp(36), dp(36)))
                item.addView(text(app.loadLabel(packageManager).toString(), 12f).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
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
            var initial = ""
            visible.forEach { app ->
                if (MetroStyle.windows) {
                    val letter = app.loadLabel(packageManager).toString().take(1).uppercase(Locale.getDefault())
                    if (letter != initial) {
                        initial = letter
                        list.addView(text(letter, 28f, MetroStyle.mint).apply { minimumHeight = dp(48); contentDescription = "Grup $letter" })
                    }
                }
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

    internal fun captainMap() {
        val body = column().apply { setPadding(dp(16), dp(8), dp(16), dp(8)) }
        body.addView(CaptainChart(this).apply { contentDescription = "Mapa il·lustrat del capità" }, LinearLayout.LayoutParams(-1, dp(160)))
        body.addView(text("Tria el teu pròxim destí", 18f, MetroStyle.mint))
        val dialog = AlertDialog.Builder(this).setTitle("Mapa del capità").setView(body).setNegativeButton("Torna a bord", null).create()
        listOf("Port · Temps de Cabrils" to { weatherDetails(WeatherLocations.cabrils) },
            "Quadern · Agenda" to { openCalendar() },
            "Taverna · Notícies" to { panels.openHeadlines() },
            "Missatges · WhatsApp" to { openWhatsApp() }).forEach { (label, destination) ->
            body.addView(action(label) { dialog.dismiss(); destination() }, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(6) })
        }
        LauncherThemes.style(body)
        dialog.show()
    }

    private fun settings() {
        val effects = if (prefs.getBoolean("effects", true)) "activats" else "desactivats"
        AlertDialog.Builder(this).setTitle("MagicFold / Magic V2").setItems(arrayOf("Segona ciutat", "Calendaris Google i Outlook", "Efectes visuals: $effects", "Actualitza temps i agenda", "Launcher per defecte", "Configuració d’Android", "Prova els paisatges vius", "Imatge de fons", "Avisos de Gmail i Outlook", "Fonts de notícies", "Temes i perspectiva", "Pantalla completa: " + if (prefs.getBoolean("fullscreen", false)) "activada" else "desactivada")) { _, item ->
            when (item) {
                0 -> cityDialog(); 1 -> calendarPicker()
                2 -> { prefs.edit().putBoolean("effects", !prefs.getBoolean("effects", true)).apply(); entrancePending = true; render() }
                3 -> { refreshWeather(true); refreshAgenda(); panels.refresh(true) }; 4 -> chooseHome(); 5 -> open(Intent(Settings.ACTION_SETTINGS))
                6 -> previewWeather()
                7 -> wallpaperSettings()
                8 -> panels.mailSettings()
                9 -> panels.newsSettings()
                10 -> themeSettings()
                11 -> setFullscreen(!prefs.getBoolean("fullscreen", false))
            }
        }.show()
    }
    internal fun themeSettings() {
        val current = LauncherThemes.id(prefs.getString("theme", LauncherThemes.METRO))
        AlertDialog.Builder(this).setTitle("Temes de MagicFold")
            .setSingleChoiceItems(LauncherThemes.labels, LauncherThemes.ids.indexOf(current)) { dialog, index ->
                prefs.edit().putString("theme", LauncherThemes.ids[index]).apply()
                dialog.dismiss(); entrancePending = true; render()
            }
            .setPositiveButton("Perspectiva 3D") { _, _ -> perspectiveSettings() }
            .setNeutralButton("Escriptori i apps") { _, _ -> desktopSettings() }
            .setNegativeButton("Tanca", null).show()
    }
    private fun desktopSettings() {
        val desktop = prefs.getBoolean("desktopEnabled", true)
        val windowed = prefs.getBoolean("windowedApps", true)
        AlertDialog.Builder(this).setTitle("Escriptori i apps")
            .setItems(arrayOf("Panells lliures en obrir: " + if(desktop) "activats" else "desactivats",
                "Demana finestres per a les apps: " + if(windowed) "activat" else "desactivat",
                "Com funcionen les finestres de MagicOS")) { _,i -> when(i) {
                0 -> { prefs.edit().putBoolean("desktopEnabled",!desktop).apply(); render() }
                1 -> prefs.edit().putBoolean("windowedApps",!windowed).apply()
                2 -> AlertDialog.Builder(this).setTitle("Finestres de les apps")
                    .setMessage("Els panells de MagicFold es poden moure i redimensionar als temes Windows, macOS i Linux amb el mòbil obert.\n\nPer a altres apps, demanem una mida de finestra a Android. MagicOS pot ignorar-la i obrir l’app a pantalla completa. No totes les apps admeten finestres.\n\nA HONOR pots obrir les finestres flotants des de la barra lateral de multifinestra: llisca des d’un lateral, mantén el dit i toca l’app.")
                    .setPositiveButton("Entesos",null).show()
            } }.setNegativeButton("Tanca",null).show()
    }
    private fun perspectiveSettings() {
        val enabled = prefs.getBoolean("perspective", true)
        val status = when {
            !enabled -> "Perspectiva desactivada"
            !prefs.getBoolean("effects", true) -> "Bloquejada: Efectes visuals desactivats"
            (getSystemService(POWER_SERVICE) as PowerManager).isPowerSaveMode -> "Bloquejada: estalvi de bateria"
            Build.VERSION.SDK_INT >= 26 && !ValueAnimator.areAnimatorsEnabled() -> "Bloquejada: animacions d’Android desactivades"
            !perspective.available -> "Cap sensor de rotació compatible"
            perspective.hasReadings -> "S’han rebut lectures de moviment"
            else -> "Encara no s’han rebut lectures de moviment"
        }
        AlertDialog.Builder(this).setTitle("Perspectiva amb el giroscopi")
            .setMessage("Els panells s’inclinen quan mous el mòbil (màxim 8°), amb més desplaçament i ombra per donar profunditat. S’aturen mentre toques la pantalla, a la pàgina d’apps, sense focus, amb estalvi de bateria o amb animacions desactivades.\n\n" +
                status + "\nSensor: " + (perspective.sensorName ?: "No disponible") +
                "\n\nDeixa el mòbil en una posició còmoda i prem Recalibra per fixar el centre. Funciona amb tots els temes.")
            .setPositiveButton(if (enabled) "Desactiva" else "Activa") { _, _ ->
                prefs.edit().putBoolean("perspective", !enabled).apply(); syncPerspective()
            }.setNeutralButton("Recalibra") { _, _ -> perspective.recalibrate() }
            .setNegativeButton("Tanca", null).show()
    }
    internal fun wallpaperSettings() {
        if (wallpaperJob?.isActive == true) {
            Toast.makeText(this, "S’està preparant la imatge…", Toast.LENGTH_SHORT).show(); return
        }
        val overlay = prefs.getBoolean("photoWeather", true)
        val theme = LauncherThemes.id(prefs.getString("theme", LauncherThemes.METRO))
        val choices = mutableListOf("Tria una imatge del mòbil", "Efectes del temps sobre la imatge: " + (if (overlay) "activats" else "desactivats"), "Torna al paisatge original", "Desenfocament de la foto: " + (if (prefs.getBoolean("photoBlur", true)) "activat" else "desactivat"))
        if (theme in listOf(LauncherThemes.WINDOWS, LauncherThemes.DESKTOP, LauncherThemes.PIRATE, LauncherThemes.LINUX)) choices += "Recupera el fons d’aquest tema"
        AlertDialog.Builder(this).setTitle("Imatge de fons")
            .setItems(choices.toTypedArray()) { _, item ->
                when (item) {
                    0 -> try { imagePicker.launch(arrayOf("image/*")) }
                        catch (_: ActivityNotFoundException) { Toast.makeText(this, "No hi ha cap selector d’imatges disponible", Toast.LENGTH_LONG).show() }
                    1 -> { prefs.edit().putBoolean("photoWeather", !overlay).apply(); renderData() }
                    3 -> { prefs.edit().putBoolean("photoBlur", !prefs.getBoolean("photoBlur", true)).apply(); renderData() }
                    4 -> { prefs.edit().putBoolean("themePhoto_$theme", false).apply(); renderData() }
                    2 -> {
                        wallpaperJob = lifecycleScope.launch { wallpaperStore.clear(); wallpaper = null; softenedWallpaper = null; renderData() }
                    }
                }
            }.setNegativeButton("Tanca", null).show()
    }

    internal fun previewWeather() {
        val scenes = arrayOf("Sol", "Núvols", "Pluja", "Neu", "Nit")
        val body = column()
        val stage = FrameLayout(this)
        fun show(index: Int) {
            stage.removeAllViews()
            stage.addView(WeatherScene(this, intArrayOf(0, 3, 61, 73, 0)[index], index == 4,
                prefs.getBoolean("effects", true), true, if (prefs.getBoolean("photoBlur", true)) softenedWallpaper ?: wallpaper else wallpaper, true), FrameLayout.LayoutParams(-1, -1))
            stage.addView(text("SIMULACIÓ · ${scenes[index].uppercase(Locale("ca"))}", 12f).apply {
                setPadding(dp(16), dp(16), dp(16), 0)
            })
        }
        show(0)
        body.addView(stage, LinearLayout.LayoutParams(-1, dp(320)))
        val choices = LinearLayout(this)
        scenes.forEachIndexed { i, label -> choices.addView(action(label) { show(i) }.apply { textSize = 12f; setPadding(dp(2), dp(6), dp(2), dp(6)) }, LinearLayout.LayoutParams(0, dp(48), 1f)) }
        body.addView(choices)
        AlertDialog.Builder(this).setTitle("Paisatges vius / Cabrils").setView(body)
            .setMessage("Mostres visuals. El fons d’inici segueix el temps real de Cabrils. Mou el dit per veure la profunditat.")
            .setPositiveButton("Tanca", null).show()
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
            if (changed) renderData()
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
            renderData()
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
                renderData()
            }
        }
    }
    private fun openPackage(name: String) { val intent = packageManager.getLaunchIntentForPackage(name); if (intent != null) open(intent) else Toast.makeText(this, "L’aplicació no està instal·lada", Toast.LENGTH_LONG).show() }
    private fun open(intent: Intent) {
        val requestWindow = Build.VERSION.SDK_INT >= 24 && (MetroStyle.windows || MetroStyle.desktop || MetroStyle.linux) &&
            FoldPolicy.expanded(resources.configuration.screenWidthDp, hinge != null) &&
            prefs.getBoolean("windowedApps",true) && intent.action?.startsWith("android.settings.") != true
        val options = if(requestWindow) {
            val area = if(Build.VERSION.SDK_INT >= 30) {
                val metrics=windowManager.currentWindowMetrics
                val insets=metrics.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars())
                android.graphics.Rect(metrics.bounds).apply { left+=insets.left;top+=insets.top;right-=insets.right;bottom-=insets.bottom }
            } else android.graphics.Rect().also { window.decorView.getWindowVisibleDisplayFrame(it) }
            val w=(area.width()*.68f).roundToInt();val h=(area.height()*.76f).roundToInt()
            android.app.ActivityOptions.makeBasic().setLaunchBounds(android.graphics.Rect(
                area.centerX()-w/2,area.centerY()-h/2,area.centerX()+w/2,area.centerY()+h/2)).toBundle()
        } else null
        try {
            startActivity(if(requestWindow) Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) else intent,options)
            if(requestWindow && !prefs.getBoolean("windowRequestExplained",false)) {
                prefs.edit().putBoolean("windowRequestExplained",true).apply()
                Toast.makeText(this,"Mida de finestra demanada. MagicOS pot obrir l’app a pantalla completa; en aquest cas, fes servir la barra lateral de multifinestra.",Toast.LENGTH_LONG).show()
            }
        } catch (_: Exception) {
            try { startActivity(intent) }
            catch (_: Exception) { Toast.makeText(this,"No hi ha cap aplicació disponible per obrir-ho",Toast.LENGTH_LONG).show() }
        }
    }
    private fun openClock() {
        if (!ClockLauncher.open(this)) Toast.makeText(this, "No s’ha pogut obrir cap app de rellotge en aquest perfil", Toast.LENGTH_LONG).show()
    }
    private fun openWhatsApp() {
        val intent = listOf("com.whatsapp", "com.whatsapp.w4b").firstNotNullOfOrNull { packageManager.getLaunchIntentForPackage(it) }
        if (intent == null) Toast.makeText(this, "WhatsApp no està instal·lat en aquest perfil", Toast.LENGTH_LONG).show() else open(intent)
    }
    private fun openCalendar() {
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("content://com.android.calendar/time/${System.currentTimeMillis()}"))) }
        catch (_: ActivityNotFoundException) { calendarPicker() }
    }
    private fun isDefaultHome() = packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName == packageName
    private fun chooseHome() {
        if (Build.VERSION.SDK_INT >= 29) {
            val roles = getSystemService(RoleManager::class.java)
            if (roles.isRoleAvailable(RoleManager.ROLE_HOME) && !roles.isRoleHeld(RoleManager.ROLE_HOME)) { try { homeRole.launch(roles.createRequestRoleIntent(RoleManager.ROLE_HOME)); return }
                catch (_: ActivityNotFoundException) { /* OEM fallback below. */ } }
        }
        open(Intent(Settings.ACTION_HOME_SETTINGS))
    }
}
