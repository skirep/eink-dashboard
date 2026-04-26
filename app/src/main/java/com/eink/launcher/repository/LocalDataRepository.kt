package com.eink.launcher.repository

import android.content.Context
import android.content.SharedPreferences
import com.eink.launcher.model.NoteItem
import com.eink.launcher.model.ReadingInfo
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Repository for local data storage using SharedPreferences
 */
class LocalDataRepository(context: Context) {
    
    private val prefs: SharedPreferences = 
        context.getSharedPreferences("eink_launcher_prefs", Context.MODE_PRIVATE)
    
    private val gson = Gson()
    
    // ==================== NOTES ====================
    
    fun getNotes(): List<NoteItem> {
        val json = prefs.getString(KEY_NOTES, null) ?: return emptyList()
        val type = object : TypeToken<List<NoteItem>>() {}.type
        return gson.fromJson(json, type)
    }
    
    fun saveNotes(notes: List<NoteItem>) {
        val json = gson.toJson(notes)
        prefs.edit().putString(KEY_NOTES, json).apply()
    }
    
    fun addNote(noteText: String) {
        val currentNotes = getNotes().toMutableList()
        currentNotes.add(NoteItem(noteText, false))
        saveNotes(currentNotes)
    }
    
    fun deleteNote(index: Int) {
        val currentNotes = getNotes().toMutableList()
        if (index in currentNotes.indices) {
            currentNotes.removeAt(index)
            saveNotes(currentNotes)
        }
    }
    
    fun toggleNoteCompleted(index: Int) {
        val currentNotes = getNotes().toMutableList()
        if (index in currentNotes.indices) {
            val note = currentNotes[index]
            currentNotes[index] = note.copy(isCompleted = !note.isCompleted)
            saveNotes(currentNotes)
        }
    }
    
    // ==================== READING ====================
    
    fun getReadingInfo(): ReadingInfo? {
        val json = prefs.getString(KEY_READING, null) ?: return null
        return gson.fromJson(json, ReadingInfo::class.java)
    }
    
    fun saveReadingInfo(reading: ReadingInfo) {
        val json = gson.toJson(reading)
        prefs.edit().putString(KEY_READING, json).apply()
    }
    
    fun updateReadingPage(currentPage: Int) {
        val reading = getReadingInfo() ?: return
        val updated = reading.copy(currentPage = currentPage)
        saveReadingInfo(updated)
    }
    
    // ==================== SETTINGS ====================
    
    fun getCity(): String {
        return prefs.getString(KEY_CITY, "Barcelona,ES") ?: "Barcelona,ES"
    }
    
    fun setCity(city: String) {
        prefs.edit().putString(KEY_CITY, city).apply()
    }
    
    companion object {
        private const val KEY_NOTES = "notes"
        private const val KEY_READING = "reading"
        private const val KEY_CITY = "city"
    }
}
