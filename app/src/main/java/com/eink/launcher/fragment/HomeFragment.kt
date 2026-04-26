package com.eink.launcher.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.eink.launcher.R
import com.eink.launcher.model.DayForecast
import com.eink.launcher.model.NoteItem
import com.eink.launcher.model.ReadingInfo
import com.eink.launcher.model.WeatherInfo
import com.eink.launcher.viewmodel.HomeViewModel
import java.text.SimpleDateFormat
import java.util.*

/**
 * HomeFragment – displays the main dashboard with:
 * - Current time and date
 * - Weather widget with forecast (from OpenWeatherMap API)
 * - Notes list (stored locally)
 * - Currently reading book (stored locally)
 */
class HomeFragment : Fragment() {

    private lateinit var viewModel: HomeViewModel
    
    private lateinit var tvTime: TextView
    private lateinit var tvDate: TextView
    private lateinit var tvLocation: TextView
    private lateinit var tvCurrentTemp: TextView
    private lateinit var tvCondition: TextView
    private lateinit var tvFeelsLike: TextView
    private lateinit var tvWind: TextView
    private lateinit var tvHumidity: TextView
    private lateinit var tvSunrise: TextView
    private lateinit var tvSunset: TextView
    private lateinit var forecastContainer: LinearLayout
    private lateinit var notesContainer: LinearLayout
    private lateinit var tvReadingTitle: TextView
    private lateinit var tvReadingPage: TextView

    private var updateTimeRunnable: Runnable? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize ViewModel
        viewModel = ViewModelProvider(this)[HomeViewModel::class.java]

        // Initialize views
        tvTime = view.findViewById(R.id.tvTime)
        tvDate = view.findViewById(R.id.tvDate)
        tvLocation = view.findViewById(R.id.tvLocation)
        tvCurrentTemp = view.findViewById(R.id.tvCurrentTemp)
        tvCondition = view.findViewById(R.id.tvCondition)
        tvFeelsLike = view.findViewById(R.id.tvFeelsLike)
        tvWind = view.findViewById(R.id.tvWind)
        tvHumidity = view.findViewById(R.id.tvHumidity)
        tvSunrise = view.findViewById(R.id.tvSunrise)
        tvSunset = view.findViewById(R.id.tvSunset)
        forecastContainer = view.findViewById(R.id.forecastContainer)
        notesContainer = view.findViewById(R.id.notesContainer)
        tvReadingTitle = view.findViewById(R.id.tvReadingTitle)
        tvReadingPage = view.findViewById(R.id.tvReadingPage)

        // Observe ViewModel data
        observeViewModel()
        
        // Start time updates
        startTimeUpdates()
        
        // Initial data load
        ensureMockDataExists()
    }

    private fun observeViewModel() {
        viewModel.weatherInfo.observe(viewLifecycleOwner) { weather ->
            weather?.let { displayWeather(it) }
        }
        
        viewModel.notes.observe(viewLifecycleOwner) { notes ->
            displayNotes(notes)
        }
        
        viewModel.readingInfo.observe(viewLifecycleOwner) { reading ->
            displayReading(reading)
        }
        
        viewModel.error.observe(viewLifecycleOwner) { error ->
            error?.let {
                Toast.makeText(requireContext(), "Error: $it", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun startTimeUpdates() {
        updateTimeRunnable = object : Runnable {
            override fun run() {
                updateTimeAndDate()
                view?.postDelayed(this, 60000) // Update every minute
            }
        }
        updateTimeRunnable?.run()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        updateTimeRunnable?.let { view?.removeCallbacks(it) }
    }

    private fun updateTimeAndDate() {
        val calendar = Calendar.getInstance()
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val dateFormat = SimpleDateFormat("EEEE, d MMMM", Locale("ca", "ES"))

        tvTime.text = timeFormat.format(calendar.time)
        tvDate.text = dateFormat.format(calendar.time)
    }

    /**
     * Ensures some mock data exists on first run
     */
    private fun ensureMockDataExists() {
        // Add default notes if none exist
        if (viewModel.notes.value.isNullOrEmpty()) {
            viewModel.addNote("Comprar coses casa")
            viewModel.addNote("Idea projecte retro")
            viewModel.addNote("Revisar API Okta")
        }
        
        // Add default reading info if none exists
        if (viewModel.readingInfo.value == null) {
            viewModel.saveReading(
                ReadingInfo(
                    title = "\"Atomic Habits\"",
                    currentPage = 47,
                    totalPages = 320
                )
            )
        }
    }

    private fun displayWeather(weather: WeatherInfo) {
        tvLocation.text = weather.location
        tvCurrentTemp.text = "${weather.currentTemp}°C"
        tvCondition.text = weather.condition
        tvFeelsLike.text = "Sensació ${weather.feelsLike}°C"
        tvWind.text = weather.wind
        tvHumidity.text = "${weather.humidity}%"
        tvSunrise.text = weather.sunrise
        tvSunset.text = weather.sunset

        // Display forecast
        forecastContainer.removeAllViews()
        weather.forecast.forEach { day ->
            val dayView = layoutInflater.inflate(R.layout.item_forecast_day, forecastContainer, false)
            dayView.findViewById<TextView>(R.id.tvDayName).text = day.dayOfWeek
            dayView.findViewById<TextView>(R.id.tvMaxTemp).text = "${day.maxTemp}°"
            dayView.findViewById<TextView>(R.id.tvMinTemp).text = "${day.minTemp}°"
            forecastContainer.addView(dayView)
        }
    }

    private fun displayNotes(notes: List<NoteItem>) {
        notesContainer.removeAllViews()
        notes.forEach { note ->
            val noteView = layoutInflater.inflate(R.layout.item_note, notesContainer, false)
            noteView.findViewById<TextView>(R.id.tvNoteText).text = "• ${note.text}"
            notesContainer.addView(noteView)
        }
    }

    private fun displayReading(reading: ReadingInfo?) {
        if (reading != null) {
            tvReadingTitle.text = reading.title
            tvReadingPage.text = "Pàgina ${reading.currentPage} de ${reading.totalPages}"
        } else {
            tvReadingTitle.text = "Cap llibre actualment"
            tvReadingPage.text = ""
        }
    }
}
