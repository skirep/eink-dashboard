package com.eink.launcher.fold

import android.app.KeyguardManager
import android.content.*
import android.graphics.Bitmap
import android.net.Uri
import android.provider.Settings
import android.text.TextUtils
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

/** Live, local mail notices and attributed RSS articles; never placeholder personal data. */
internal class DashboardPanels(private val activity: AppCompatActivity,
    private val prefs: SharedPreferences, private val changed: () -> Unit,
    private val open: (Intent) -> Unit) {
    private val news = mutableMapOf<Int, List<NewsItem>>()
    private val errors = mutableMapOf<Int, String>()
    private val jobs = mutableMapOf<Int, Job>()
    private val imagePending = mutableSetOf<String>()
    private val images = object : android.util.LruCache<String, Bitmap>(4096) {
        override fun sizeOf(key: String, value: Bitmap) = (value.allocationByteCount / 1024).coerceAtLeast(1)
    }
    private var selected = prefs.getInt("newsCategory", 0).coerceIn(0, 2)
    private fun dp(v: Int) = (v * activity.resources.displayMetrics.density).roundToInt()
    private fun column() = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
    private fun text(value: String, size: Float = 14f, color: Int = MetroStyle.text) = TextView(activity).apply {
        text = value; textSize = size; setTextColor(color); setPadding(0, dp(2), 0, dp(2))
        typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
    }
    private fun caption(value: String) = text(value, 11f, MetroStyle.blue).apply { letterSpacing = .06f }
    private fun button(value: String, click: () -> Unit) = Button(activity).apply {
        text = value; textSize = 12f; isAllCaps = false; setTextColor(MetroStyle.text)
        setPadding(dp(4), 0, dp(4), 0); minimumHeight = dp(44); minHeight = dp(44)
        background = MetroStyle.surface(0xff1b324b.toInt(), 0xff14263b.toInt(), dp(7).toFloat())
        setOnClickListener { click() }
    }
    private fun card() = MetroTile(activity, 0xff183b52.toInt(), 0xff0c2037.toInt()).apply { setPadding(dp(11), dp(9), dp(11), dp(9)) }
    private fun add(parent: LinearLayout, child: View) { parent.addView(child, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) }) }
    fun start() {
        NewsFeed.defaults.indices.forEach { i ->
            news[i] = runCatching {
                val array = JSONArray(prefs.getString("news_$i", "[]"))
                (0 until minOf(array.length(), 10)).map { array.getJSONObject(it) }
                    .map { NewsItem(it.getString("title"), it.getString("url"), it.optString("image")) }.filter { NewsFeed.https(it.url) }
            }.getOrDefault(emptyList())
        }
        // Cached headlines must regain thumbnails after Activity/process recreation,
        // even while the hourly feed refresh is throttled.
        news.values.filter { it.isNotEmpty() }.forEach { items ->
            activity.lifecycleScope.launch { loadImages(items) }
        }
        activity.lifecycleScope.launch {
            activity.repeatOnLifecycle(Lifecycle.State.STARTED) { MailNotifications.state.collect { changed() } }
        }
    }
    private fun hasMailAccess() = NotificationManagerCompat.getEnabledListenerPackages(activity).contains(activity.packageName)
    fun mailSettings() {
        AlertDialog.Builder(activity).setTitle("Avisos de Gmail i Outlook")
            .setMessage("Mostra les notificacions actives de Gmail i Outlook: remitent, assumpte i previsualització. No és la safata d’entrada ni el total de correus sense llegir.\n\nAndroid demanarà accés a notificacions. MagicFold filtra les altres apps, no desa els correus al disc i no els envia a cap servidor. Pots retirar l’accés a Configuració d’Android.")
            .setPositiveButton("Configura l’accés") { _, _ -> open(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
            .setNeutralButton(if (prefs.getBoolean("mailPreview", true)) "Amaga previsualitzacions" else "Mostra previsualitzacions") { _, _ ->
                prefs.edit().putBoolean("mailPreview", !prefs.getBoolean("mailPreview", true)).apply(); changed()
            }.setNegativeButton("Tanca", null).show()
    }
    private fun openPackage(name: String) {
        val intent = activity.packageManager.getLaunchIntentForPackage(name)
        if (intent == null) Toast.makeText(activity, "L’aplicació no està instal·lada", Toast.LENGTH_SHORT).show() else open(intent)
    }
    fun mail(parent: LinearLayout, expanded: Boolean) {
        val card = card()
        card.addView(caption(if (expanded) "CORREU · Gmail + Outlook" else "CORREU").apply {
            isFocusable = true; minimumHeight = dp(32); setOnClickListener { mailSettings() }
        })
        val snapshot = MailNotifications.state.value
        val locked = (activity.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isKeyguardLocked
        when {
            !hasMailAccess() -> card.addView(button("Connecta els avisos") { mailSettings() })
            locked -> card.addView(text("Desbloqueja per veure el correu", 13f))
            !snapshot.connected -> card.addView(text("Esperant les notificacions…", 13f, MetroStyle.muted))
            else -> {
                card.addView(text("${snapshot.total} avisos actius", if (expanded) 23f else 18f))
                if (snapshot.messages.isEmpty()) card.addView(text("Cap avís de correu", 12f, MetroStyle.muted))
                snapshot.messages.take(if (expanded) 2 else 1).forEach { mail ->
                    val row = column().apply { minimumHeight = dp(48); setPadding(0, dp(6), 0, dp(6)); isFocusable = true }
                    row.addView(caption(mail.provider))
                    row.addView(text(mail.title, 15f).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END })
                    if (expanded && prefs.getBoolean("mailPreview", true)) row.addView(text(mail.preview, 12f, MetroStyle.muted).apply { maxLines = 2; ellipsize = TextUtils.TruncateAt.END })
                    row.setOnClickListener {
                        try { mail.intent?.send() ?: openPackage(if (mail.provider == "Gmail") "com.google.android.gm" else "com.microsoft.office.outlook") }
                        catch (_: android.app.PendingIntent.CanceledException) { Toast.makeText(activity, "L’avís ja no està disponible", Toast.LENGTH_SHORT).show() }
                    }
                    card.addView(row)
                }
            }
        }
        if (expanded) {
            val shortcuts = LinearLayout(activity)
            listOf("Gmail" to "com.google.android.gm", "Outlook" to "com.microsoft.office.outlook").forEach { (label, pkg) ->
                shortcuts.addView(button(label) { openPackage(pkg) }, LinearLayout.LayoutParams(0, dp(44), 1f))
            }; card.addView(shortcuts)
        }
        add(parent, card)
    }
    private fun source(i: Int): NewsSource = NewsFeed.defaults[i].let {
        val address = prefs.getString("feed_$i", it.feed).orEmpty()
        it.copy(name = if (address == it.feed) it.name else runCatching { java.net.URL(address).host }.getOrDefault("RSS"), feed = address)
    }
    fun refresh(force: Boolean = false) {
        NewsFeed.defaults.indices.forEach { i ->
            if (jobs[i]?.isActive == true) return@forEach
            if (!force && System.currentTimeMillis() - prefs.getLong("newsAttempt_$i", 0) in 0 until 60 * 60000L) return@forEach
            val feedSource = source(i)
            prefs.edit().putLong("newsAttempt_$i", System.currentTimeMillis()).apply()
            jobs[i] = activity.lifecycleScope.launch {
                try {
                    val result = withContext(Dispatchers.IO) { NewsFeed.fetch(feedSource) }
                    if (feedSource != source(i)) return@launch
                    check(result.isNotEmpty())
                    news[i] = result; errors.remove(i)
                    val array = JSONArray().apply { result.forEach { put(JSONObject().put("title", it.title).put("url", it.url).put("image", it.image)) } }
                    prefs.edit().putString("news_$i", array.toString()).putLong("newsUpdated_$i", System.currentTimeMillis()).apply()
                    changed()
                    loadImages(result)
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) { errors[i] = "No s’ha pogut actualitzar · toca ↻" }
                changed()
            }
        }
    }
    private suspend fun loadImages(items: List<NewsItem>) {
        items.take(3).forEach { item ->
            if (item.image.isNotEmpty() && images.get(item.image) == null && imagePending.add(item.image)) {
                try {
                    val bitmap = withContext(Dispatchers.IO) { NewsFeed.thumbnail(item.image) }
                    if (bitmap != null) { images.put(item.image, bitmap); changed() }
                } finally { imagePending.remove(item.image) }
            }
        }
    }

    fun news(parent: LinearLayout, expanded: Boolean) {
        val card = card()
        val heading = LinearLayout(activity).apply { gravity = Gravity.CENTER_VERTICAL }
        heading.addView(caption("DESCOBREIX"), LinearLayout.LayoutParams(0, -2, 1f))
        heading.addView(text("↻", 22f).apply {
            gravity = Gravity.CENTER; minimumHeight = dp(44); minimumWidth = dp(44); isFocusable = true
            contentDescription = "Actualitza notícies"; setOnClickListener { refresh(true) }
        }); card.addView(heading)
        val chips = LinearLayout(activity)
        NewsFeed.defaults.forEachIndexed { i, source ->
            chips.addView(button(source.category) { selected = i; prefs.edit().putInt("newsCategory", i).apply(); changed() }.apply {
                textSize = 11f; isSelected = i == selected
                if (isSelected) { setTextColor(MetroStyle.background); background = MetroStyle.surface(MetroStyle.mint, MetroStyle.blue, dp(7).toFloat()) }
            }, LinearLayout.LayoutParams(0, dp(40), 1f).apply { rightMargin = dp(3) })
        }; card.addView(chips)
        val items = news[selected].orEmpty(); val source = source(selected)
        if (items.isEmpty()) card.addView(text(if (errors.containsKey(selected)) "Sense notícies · comprova la connexió" else "Carregant titulars…", 13f, MetroStyle.muted))
        items.take(if (expanded) 3 else 1).forEach { item ->
            val row = LinearLayout(activity).apply { gravity = Gravity.CENTER_VERTICAL; minimumHeight = dp(76); setPadding(0, dp(8), 0, dp(8)); isFocusable = true }
            val bitmap = images.get(item.image)
            if (bitmap != null) row.addView(ImageView(activity).apply { setImageBitmap(bitmap); scaleType = ImageView.ScaleType.CENTER_CROP; importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO },
                LinearLayout.LayoutParams(dp(74), dp(60)).apply { rightMargin = dp(10) })
            val labels = column()
            labels.addView(text(item.title, 15f).apply { maxLines = 3; ellipsize = TextUtils.TruncateAt.END })
            labels.addView(text(source.name + "  ↗", 11f, MetroStyle.muted))
            row.addView(labels, LinearLayout.LayoutParams(0, -2, 1f)); row.setOnClickListener { open(Intent(Intent.ACTION_VIEW, Uri.parse(item.url))) }; card.addView(row)
        }
        val updated = prefs.getLong("newsUpdated_$selected", 0)
        card.addView(text(errors[selected] ?: if (updated > 0) "Actualitzat " + SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(updated)) else source.name, 10f, MetroStyle.muted).apply {
            setOnClickListener { newsSettings() }
        }); add(parent, card)
    }
    fun newsSettings() {
        AlertDialog.Builder(activity).setTitle("Fonts de notícies RSS").setItems(NewsFeed.defaults.mapIndexed { i, s -> "${s.category} · ${source(i).name}" }.toTypedArray()) { _, i ->
            val input = EditText(activity).apply { setSingleLine(); inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI; setText(source(i).feed) }
            val dialog = AlertDialog.Builder(activity).setTitle(NewsFeed.defaults[i].category).setMessage("URL HTTPS d’un feed RSS o Atom. Els titulars s’obren al lloc d’origen.")
                .setView(input).setPositiveButton("Desa", null).setNeutralButton("Font original") { _, _ -> setSource(i, NewsFeed.defaults[i].feed) }.setNegativeButton("Cancel·la", null).create()
            dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val value = input.text.toString().trim()
                if (!NewsFeed.https(value)) input.error = "Cal una URL HTTPS vàlida" else { setSource(i, value); dialog.dismiss() }
            } }; dialog.show()
        }.setNegativeButton("Tanca", null).show()
    }
    private fun setSource(i: Int, address: String) {
        jobs.remove(i)?.cancel(); news[i] = emptyList(); errors.remove(i)
        prefs.edit().putString("feed_$i", address).remove("news_$i").remove("newsUpdated_$i").remove("newsAttempt_$i").apply()
        changed(); refresh(true)
    }
}
