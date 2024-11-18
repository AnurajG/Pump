package com.eomma.pump

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// ViewModel for PumpSessionsActivity
class PumpSessionsViewModel(private val repository: PumpingSessionRepository) : ViewModel() {
    fun getSessionsForDate(date: Calendar) = repository.getSessionsForDate(date)
    suspend fun getDayStats(date: Calendar) = repository.getDayStats(date)
    suspend fun updateSessionVolume(sessionId: Long, leftAmount: Double, rightAmount: Double) =
        repository.updateSessionVolume(sessionId, leftAmount, rightAmount)
}

// ViewModel Factory
class PumpSessionsViewModelFactory(private val repository: PumpingSessionRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PumpSessionsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return PumpSessionsViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class PumpSessionsActivity : AppCompatActivity() {
    private lateinit var sessionsRecyclerView: RecyclerView
    private lateinit var sessionAdapter: PumpSessionAdapter

    private lateinit var sessionsCountText: TextView
    private lateinit var dailyTotalText: TextView
    private lateinit var totalMinutesText: TextView
    private lateinit var dateText: TextView
    private lateinit var calendarContainer: LinearLayout

    private lateinit var bottomNavigationView: BottomNavigationView

    private val viewModel: PumpSessionsViewModel by viewModels {
        PumpSessionsViewModelFactory(
            PumpingSessionRepository(
                PumpingSessionDatabase.getDatabase(this).pumpingSessionDao()
            )
        )
    }

    private var selectedDate: Calendar = Calendar.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pump_sessions)

        initializeViews()
        setupRecyclerView()
        setupClickListeners()
        setupBottomNavigation()
        setupCalendarView()
        updateDateDisplay()
        observeSessions()
    }

    private fun initializeViews() {
        sessionsRecyclerView = findViewById(R.id.sessionsRecyclerView)
        sessionsCountText = findViewById(R.id.sessions_count)
        dailyTotalText = findViewById(R.id.daily_total)
        totalMinutesText = findViewById(R.id.total_minutes)
        dateText = findViewById(R.id.dateText)
        bottomNavigationView = findViewById(R.id.bottomNavigation)
        calendarContainer = findViewById(R.id.calendarContainer)
    }

    private fun setupRecyclerView() {
        sessionAdapter = PumpSessionAdapter { session, leftAmount, rightAmount ->
            lifecycleScope.launch {
                try {
                    val success = viewModel.updateSessionVolume(session.id, leftAmount, rightAmount)
                    if (success) {
                        Toast.makeText(
                            this@PumpSessionsActivity,
                            "Session updated successfully",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        Toast.makeText(
                            this@PumpSessionsActivity,
                            "Failed to update session",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(
                        this@PumpSessionsActivity,
                        "Error updating session",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        sessionsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@PumpSessionsActivity)
            adapter = sessionAdapter
        }
    }

    private fun setupClickListeners() {
        findViewById<ImageButton>(R.id.closeButton).setOnClickListener {
            finish()
        }
    }

    private fun setupBottomNavigation() {
        bottomNavigationView.setOnItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.navigation_devices -> {
                    startActivity(Intent(this, HomeActivity::class.java))
                    finish()
                    false
                }
                R.id.navigation_statistics -> {
                    true
                }
                R.id.navigation_me -> {
                    // Handle profile navigation
                    true
                }
                else -> false
            }
        }
        bottomNavigationView.selectedItemId = R.id.navigation_statistics
    }

    private fun setupCalendarView() {
        val calendar = Calendar.getInstance()
        val today = calendar.clone() as Calendar
        val dateFormat = SimpleDateFormat("EEE", Locale.getDefault())
        val days = mutableListOf<CalendarDay>()

        // Move to 6 days ago
        calendar.add(Calendar.DAY_OF_YEAR, -6)

        // Generate 7 days including today
        repeat(7) {
            val isSelected = isSameDay(calendar, selectedDate)
            val isToday = isSameDay(calendar, today)

            days.add(
                CalendarDay(
                    date = calendar.get(Calendar.DAY_OF_MONTH),
                    dayOfWeek = dateFormat.format(calendar.time).uppercase(),
                    isSelected = isSelected,
                    isToday = isToday,
                    calendar = calendar.clone() as Calendar
                )
            )
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }

        populateCalendarView(days)
    }

    private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    private fun populateCalendarView(days: List<CalendarDay>) {
        calendarContainer.removeAllViews()
        val inflater = LayoutInflater.from(this)

        days.forEach { day ->
            val dayView = inflater.inflate(R.layout.calendar_day_item, calendarContainer, false)
            val container = dayView.findViewById<LinearLayout>(R.id.dayContainer)
            val dateText = dayView.findViewById<TextView>(R.id.dateText)
            val dayText = dayView.findViewById<TextView>(R.id.dayText)

            dateText.text = day.date.toString()
            dayText.text = day.dayOfWeek

            // Apply selected or today styling
            when {
                day.isSelected -> {
                    container.setBackgroundResource(R.drawable.circle_background)
                    dateText.setTextColor(getColor(R.color.white))
                    dayText.setTextColor(getColor(R.color.white))
                }
                day.isToday -> {
                    dateText.setTextColor(getColor(R.color.barbie_pink))
                    dayText.setTextColor(getColor(R.color.barbie_pink))
                }
                else -> {
                    dateText.setTextColor(getColor(R.color.black))
                    dayText.setTextColor(getColor(R.color.gray))
                }
            }

            container.setOnClickListener {
                selectedDate = day.calendar
                updateDateSelection(day.calendar)
            }

            val layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            dayView.layoutParams = layoutParams

            calendarContainer.addView(dayView)
        }
    }

    private fun observeSessions() {
        lifecycleScope.launch {
            viewModel.getSessionsForDate(selectedDate).collect { sessions ->
                // Group sessions by timestamp to combine left and right measurements
                val groupedSessions = sessions.groupBy { it.timestamp }

                val pumpSessions = groupedSessions.map { (timestamp, sessionsGroup) ->
                    val leftAmount = sessionsGroup.find { it.side == PumpingSide.LEFT }?.volume ?: 0.0
                    val rightAmount = sessionsGroup.find { it.side == PumpingSide.RIGHT }?.volume ?: 0.0

                    PumpSession(
                        id = sessionsGroup.first().id, // Use the first session's ID
                        timestamp = Date(timestamp),
                        totalAmount = leftAmount + rightAmount,
                        leftAmount = leftAmount,
                        rightAmount = rightAmount,
                        durationMinutes = sessionsGroup.first().duration / 60
                    )
                }

                sessionAdapter.submitList(pumpSessions)

                // Update stats (no change needed here as the SQL query handles the totals)
                val stats = viewModel.getDayStats(selectedDate)
                sessionsCountText.text = stats.sessionCount.toString()
                dailyTotalText.text = String.format("%.1f", stats.totalVolume)
                totalMinutesText.text = (stats.totalDuration / 60).toString()
            }
        }
    }

    private fun updateDateSelection(newDate: Calendar) {
        selectedDate = newDate
        setupCalendarView()
        updateDateDisplay()
        observeSessions()
    }

    private fun updateDateDisplay() {
        val dateFormatter = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault())
        dateText.text = if (isSelectedDateToday()) {
            "Today, ${dateFormatter.format(selectedDate.time)}"
        } else {
            dateFormatter.format(selectedDate.time)
        }
    }

    private fun isSelectedDateToday(): Boolean {
        val today = Calendar.getInstance()
        return isSameDay(selectedDate, today)
    }
}

data class CalendarDay(
    val date: Int,
    val dayOfWeek: String,
    val isSelected: Boolean,
    val isToday: Boolean,
    val calendar: Calendar
)