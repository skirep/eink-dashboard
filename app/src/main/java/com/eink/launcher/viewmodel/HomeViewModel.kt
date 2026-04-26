package com.eink.launcher.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.eink.launcher.model.NoteItem
import com.eink.launcher.model.ReadingInfo
import com.eink.launcher.model.WeatherInfo
import com.eink.launcher.repository.LocalDataRepository
import com.eink.launcher.repository.WeatherRepository
import kotlinx.coroutines.launch

/**
 * ViewModel for the Home screen
 */
class HomeViewModel(application: Application) : AndroidViewModel(application) {
    
    private val weatherRepository = WeatherRepository()
    private val localDataRepository = LocalDataRepository(application)
    
    private val _weatherInfo = MutableLiveData<WeatherInfo?>()
    val weatherInfo: LiveData<WeatherInfo?> = _weatherInfo
    
    private val _notes = MutableLiveData<List<NoteItem>>()
    val notes: LiveData<List<NoteItem>> = _notes
    
    private val _readingInfo = MutableLiveData<ReadingInfo?>()
    val readingInfo: LiveData<ReadingInfo?> = _readingInfo
    
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading
    
    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error
    
    init {
        loadAllData()
    }
    
    fun loadAllData() {
        loadWeather()
        loadNotes()
        loadReading()
    }
    
    fun loadWeather() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            
            val city = localDataRepository.getCity()
            val result = weatherRepository.getWeatherInfo(city)
            
            result.onSuccess { weather ->
                _weatherInfo.value = weather
            }.onFailure { exception ->
                _error.value = exception.message
                // Keep previous data if available
            }
            
            _isLoading.value = false
        }
    }
    
    fun loadNotes() {
        val notes = localDataRepository.getNotes()
        _notes.value = notes
    }
    
    fun addNote(text: String) {
        if (text.isNotBlank()) {
            localDataRepository.addNote(text)
            loadNotes()
        }
    }
    
    fun deleteNote(index: Int) {
        localDataRepository.deleteNote(index)
        loadNotes()
    }
    
    fun toggleNoteCompleted(index: Int) {
        localDataRepository.toggleNoteCompleted(index)
        loadNotes()
    }
    
    fun loadReading() {
        val reading = localDataRepository.getReadingInfo()
        _readingInfo.value = reading
    }
    
    fun saveReading(reading: ReadingInfo) {
        localDataRepository.saveReadingInfo(reading)
        loadReading()
    }
    
    fun updateReadingPage(currentPage: Int) {
        localDataRepository.updateReadingPage(currentPage)
        loadReading()
    }
    
    fun setCity(city: String) {
        localDataRepository.setCity(city)
        loadWeather()
    }
}
