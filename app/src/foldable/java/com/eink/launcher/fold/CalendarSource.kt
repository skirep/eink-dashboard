package com.eink.launcher.fold

internal data class CalendarSource(val id: Long, val name: String, val account: String, val type: String, val color: Int, val visible: Boolean) {
    val provider: String get() = providerFor(type)
    companion object {
        fun providerFor(type: String): String {
            val normalized = type.lowercase(java.util.Locale.ROOT)
            return when {
                normalized == "com.google" -> "Google"
                listOf("outlook", "microsoft", "exchange").any { it in normalized } -> "Outlook / Microsoft"
                else -> "Calendari"
            }
        }
    }
}
