package com.eink.launcher.fold

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.net.HttpURLConnection
import java.net.URL

internal data class NewsItem(val title: String, val url: String, val image: String = "")
internal data class NewsSource(val category: String, val name: String, val feed: String)
internal object NewsFeed {
    val defaults = listOf(
        NewsSource("Tecnologia", "Xataka", "https://www.xataka.com/index.xml"),
        NewsSource("Retro", "Time Extension", "https://www.timeextension.com/feeds/latest"),
        NewsSource("Videojocs", "VidaExtra", "https://www.vidaextra.com/index.xml"),
        NewsSource("Futbol", "Mundo Deportivo", "https://www.mundodeportivo.com/rss/futbol.xml"),
        NewsSource("Economia", "ARA", "https://www.ara.cat/rss/economia/"),
        NewsSource("Política catalana", "Nació", "https://www.naciodigital.cat/rss/politica.xml"),
        NewsSource("Política espanyola", "elDiario.es", "https://www.eldiario.es/rss/politica/"),
        NewsSource("Món", "ARA · Internacional", "https://www.ara.cat/rss/internacional/"))
    const val MAX_ITEMS = 20
    fun https(value: String): Boolean = runCatching { val u = URL(value); u.protocol == "https" && u.host.isNotBlank() }.getOrDefault(false)
    private fun bytes(address: String, limit: Int): ByteArray {
        require(https(address))
        val c = URL(address).openConnection() as HttpURLConnection
        c.connectTimeout = 6000; c.readTimeout = 6000
        c.setRequestProperty("User-Agent", "MagicFold/0.9 (Android RSS reader)")
        try {
            check(c.responseCode == 200 && c.url.protocol == "https")
            return c.inputStream.use { input ->
                val out = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
                while (true) {
                    val n = input.read(buffer); if (n < 0) break
                    check(out.size() + n <= limit); out.write(buffer, 0, n)
                }
                out.toByteArray()
            }
        } finally { c.disconnect() }
    }
    fun fetch(source: NewsSource): List<NewsItem> = parse(bytes(source.feed, 2 * 1024 * 1024).toString(Charsets.UTF_8))
    fun thumbnail(address: String): Bitmap? = runCatching {
        val data = bytes(address, 2 * 1024 * 1024)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 384) sample *= 2
        BitmapFactory.decodeByteArray(data, 0, data.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }.getOrNull()
    fun parse(xml: String): List<NewsItem> {
        require(!xml.contains("<!DOCTYPE", true) && !xml.contains("<!ENTITY", true))
        val parser = Xml.newPullParser().apply { setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true); setInput(StringReader(xml)) }
        val items = mutableListOf<NewsItem>()
        var inside = false; var title = ""; var link = ""; var image = ""
        while (parser.eventType != XmlPullParser.END_DOCUMENT && items.size < MAX_ITEMS) {
            val name = parser.name.orEmpty()
            if (parser.eventType == XmlPullParser.START_TAG) {
                when {
                    name == "item" || name == "entry" -> { inside = true; title = ""; link = ""; image = "" }
                    inside && name == "title" -> title = parser.nextText().trim().take(240)
                    inside && name == "link" -> {
                        val href = parser.getAttributeValue(null, "href")
                        if (href == null) link = parser.nextText().trim()
                        else if (parser.getAttributeValue(null, "rel") in listOf(null, "alternate")) link = href
                    }
                    inside && name in listOf("thumbnail", "content", "enclosure") -> {
                        val value = parser.getAttributeValue(null, "url").orEmpty()
                        if (image.isEmpty() && https(value)) image = value
                    }
                    inside && name == "description" -> {
                        val html = parser.nextText()
                        if (image.isEmpty()) image = Regex("<img[^>]+src=[\"'](https://[^\"']+)", RegexOption.IGNORE_CASE)
                            .find(html)?.groupValues?.get(1).orEmpty().replace("&amp;", "&")
                    }
                }
            } else if (parser.eventType == XmlPullParser.END_TAG && name in listOf("item", "entry")) {
                if (title.isNotBlank() && https(link)) items += NewsItem(title, link, image.takeIf(::https).orEmpty())
                inside = false
            }
            parser.next()
        }
        return items.distinctBy { it.url }
    }
}
