package com.eomma.pump

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.drawable.AnimatedVectorDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.thingclips.smart.home.sdk.ThingHomeSdk
import com.thingclips.smart.sdk.api.IDevListener
import com.thingclips.smart.sdk.api.IThingDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PumpControlActivity : AppCompatActivity() {

    private lateinit var pumpNameTextView: TextView
    private lateinit var currentLevelText: TextView
    private lateinit var batteryStatusTextView: TextView
    private lateinit var timerLabelTextView: TextView
    private lateinit var switchButton: MaterialButton
    private lateinit var playPauseButton: MaterialButton
    private lateinit var eggProgressView: EggProgressView
    private lateinit var rightEggShape: ImageView
    private lateinit var rightDurationText: TextView
    private lateinit var automaticModeButton: ImageButton
    private lateinit var massageModeButton: ImageButton
    private lateinit var expressionModeButton: ImageButton
    private lateinit var simulationModeButton: ImageButton
    private lateinit var selectedModeText: TextView
    private lateinit var decreaseButton: ImageButton
    private lateinit var increaseButton: ImageButton
    private lateinit var timerTextView: TextView
    private lateinit var repository: PumpingSessionRepository
    private lateinit var rippleEggAnimation: AnimatedVectorDrawable


    private var currentLevel = 1
    private val maxLevel = 12
    private var pumpId: String? = null
    private var dpsMap: HashMap<String, Any>? = null
    private var device: IThingDevice? = null
    private var isPowerOn = false
    private var isPlaying = false
    private var maxMinutes = 3
    private var timerSeconds = 0
    private var isTimerRunning = false
    private var timerHandler = Handler(Looper.getMainLooper())

    private enum class PumpMode {
        AUTOMATIC, MASSAGE, EXPRESSION, SIMULATION
    }

    private var currentMode = PumpMode.EXPRESSION

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pump_control)

        val database = PumpingSessionDatabase.getDatabase(this)
        repository = PumpingSessionRepository(database.pumpingSessionDao())

        initializeViews()
        setupAnimations()
        setupPumpData()
        setupModeSelector()
        setupPowerButton()
        initializeLevelControl()
    }

    private val timerRunnable = object : Runnable {
        override fun run() {
            timerSeconds++
            updateTimerDisplay()
            timerHandler.postDelayed(this, 1000)
        }
    }

    private fun startTimer() {
        if (!isTimerRunning) {
            isTimerRunning = true
            timerHandler.post(timerRunnable)
        }
    }

    private fun pauseTimer() {
        if (isTimerRunning) {
            isTimerRunning = false
            timerHandler.removeCallbacks(timerRunnable)
        }
    }

    private fun resetTimer() {
        pauseTimer()
        timerSeconds = 0
        updateTimerDisplay()
        eggProgressView.setProgress(0f, animate = true)
    }

    private fun updateMaxDuration(minutes: Int) {
        maxMinutes = minutes
        rightDurationText.text = "${maxMinutes}min Timeout"
    }

    private fun updateTimerDisplay() {
        val minutes = timerSeconds / 60
        val seconds = timerSeconds % 60
        timerTextView.text = String.format("%02d:%02d", minutes, seconds)
        val progress = (timerSeconds / (maxMinutes * 60f)).coerceIn(0f, 1f)
        eggProgressView.setProgress(progress)
    }

    private fun initializeViews() {
        pumpNameTextView = findViewById(R.id.pump_name_text_view)
        batteryStatusTextView = findViewById(R.id.battery_status_text_view)
        automaticModeButton = findViewById(R.id.automaticModeButton)
        massageModeButton = findViewById(R.id.massageModeButton)
        expressionModeButton = findViewById(R.id.expressionModeButton)
        simulationModeButton = findViewById(R.id.simulationModeButton)
        selectedModeText = findViewById(R.id.selectedModeText)
        decreaseButton = findViewById(R.id.decreaseButton)
        increaseButton = findViewById(R.id.increaseButton)
        currentLevelText = findViewById(R.id.currentLevelText)
        eggProgressView = findViewById(R.id.rightEggShape)
        rightEggShape = findViewById(R.id.rightEggShape)
        rightDurationText = findViewById(R.id.rightDurationText)
        timerLabelTextView = findViewById(R.id.timerLabel)
        switchButton = findViewById(R.id.switchButton)
        playPauseButton = findViewById(R.id.pauseButton)
        timerTextView = findViewById(R.id.timer)

        resetTimer()
        rightDurationText.text = "${maxMinutes} min Timeout"

        val imageView = findViewById<ImageView>(R.id.ripple_egg_image)
        rippleEggAnimation = imageView.drawable as AnimatedVectorDrawable

        setupPlayPauseButton()
        setupTimeoutDialog()
    }

    private fun setupPlayPauseButton() {
        isPlaying = false
        updatePlayPauseButtonState(animate = false)

        playPauseButton.setOnClickListener {
            if (!isPowerOn) return@setOnClickListener

            isPlaying = !isPlaying
            updatePlayPauseButtonState(animate = true)

            if (isPlaying) {
                startTimer()
                rippleEggAnimation.start()  // Start animation when playing
            } else {
                pauseTimer()
                rippleEggAnimation.stop()   // Stop animation when paused
            }

            device?.let {
                // Send play/pause command to device
                // it.publishDps("{\"2\": ${isPlaying}}", null)
            }
        }
    }

    private fun updatePlayPauseButtonState(animate: Boolean = true) {
        playPauseButton.setIconResource(
            if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        )

        playPauseButton.isEnabled = isPowerOn

        if (animate) {
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 300
                addUpdateListener { animator ->
                    val value = animator.animatedValue as Float
                    playPauseButton.alpha = if (isPowerOn) {
                        0.5f + (0.5f * value)
                    } else {
                        1f - (0.5f * value)
                    }
                }
                start()
            }
        } else {
            playPauseButton.alpha = if (isPowerOn) 1f else 0.5f
        }
    }

    private fun setupPowerButton() {
        isPowerOn = false
        updatePowerButtonState()

        switchButton.setOnClickListener {
            if (isPowerOn) {
                if (timerSeconds > 0) {
                    showSaveSessionDialog()
                } else {
                    isPowerOn = false
                    completeShutdown()
                }
            } else {
                isPowerOn = true
                updatePowerButtonState(animate = true)
                updatePlayPauseButtonState(animate = true)

                val controlsAnimator = ValueAnimator.ofFloat(0.5f, 1.0f).apply {
                    duration = 300
                    addUpdateListener { animator ->
                        val alpha = animator.animatedValue as Float
                        updateControlsVisibility(alpha)
                    }
                }

                enablePumpControls(true)
                controlsAnimator.start()

                device?.let {
                    // Send power on command to device
                    // it.publishDps("{\"1\": true}", null)
                }
            }
        }
    }

    private fun showSaveSessionDialog() {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_save_session, null)
        dialog.setContentView(view)

        // Initialize views
        val sessionDurationText = view.findViewById<TextView>(R.id.sessionDurationText)

        val volumeInput = view.findViewById<TextInputEditText>(R.id.volumeInput)
        val leftSideSelector = view.findViewById<FrameLayout>(R.id.leftSideSelector)
        val rightSideSelector = view.findViewById<FrameLayout>(R.id.rightSideSelector)
        val saveButton = view.findViewById<MaterialButton>(R.id.saveButton)
        val cancelButton = view.findViewById<MaterialButton>(R.id.cancelButton)


        var selectedSide: PumpingSide? = null

        // Set session duration
        val minutes = timerSeconds / 60
        val seconds = timerSeconds % 60
        sessionDurationText.text = String.format("%02d:%02d", minutes, seconds)


        // Side selector click handlers
        leftSideSelector.setOnClickListener {
            leftSideSelector.isSelected = true
            rightSideSelector.isSelected = false
            selectedSide = PumpingSide.LEFT
        }

        rightSideSelector.setOnClickListener {
            rightSideSelector.isSelected = true
            leftSideSelector.isSelected = false
            selectedSide = PumpingSide.RIGHT
        }

        saveButton.setOnClickListener {
            val volume = volumeInput.text.toString().toDoubleOrNull()
            if (volume == null) {
                volumeInput.error = "Please enter a valid volume"
                return@setOnClickListener
            }

            if (selectedSide == null) {
                Toast.makeText(this, "Please select a side", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val session = PumpingSession(
                duration = timerSeconds,
                volume = volume,
                side = selectedSide!!,
                deviceId = pumpId
            )

            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    saveSession(session)
                    withContext(Dispatchers.Main) {
                        dialog.dismiss()
                        completeShutdown()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@PumpControlActivity,
                            "Error saving session: ${e.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
            startActivity(Intent(this, PumpSessionsActivity::class.java))
        }

        // Cancel button handler remains the same
        cancelButton.setOnClickListener {
            dialog.dismiss()
            isPowerOn = true
            updatePowerButtonState(animate = true)
        }

        dialog.setOnDismissListener {
            if (isPowerOn) {
                isPowerOn = true
                updatePowerButtonState(animate = true)
            }
        }

        dialog.show()
    }

    private suspend fun saveSession(session: PumpingSession) = withContext(Dispatchers.IO) {
        try {
            repository.insert(session)

            withContext(Dispatchers.Main) {
                Toast.makeText(
                    this@PumpControlActivity,
                    "Session saved: ${session.volume}ml, ${
                        if (session.side == PumpingSide.LEFT) "Left" else "Right"
                    } side",
                    Toast.LENGTH_SHORT
                ).show()
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    this@PumpControlActivity,
                    "Failed to save session: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
            throw e
        }
    }

    private fun updatePowerButtonState(animate: Boolean = true) {
        switchButton.isChecked = isPowerOn

        if (animate) {
            val colorFrom = if (isPowerOn)
                ContextCompat.getColor(this, R.color.barbie_pink_dark)
            else
                ContextCompat.getColor(this, R.color.barbie_pink)

            val colorTo = if (isPowerOn)
                ContextCompat.getColor(this, R.color.barbie_pink)
            else
                ContextCompat.getColor(this, R.color.barbie_pink_dark)

            ValueAnimator.ofObject(ArgbEvaluator(), colorFrom, colorTo).apply {
                duration = 300
                addUpdateListener { animator ->
                    switchButton.iconTint = ColorStateList.valueOf(animator.animatedValue as Int)
                }
                start()
            }

            ValueAnimator.ofFloat(
                if (isPowerOn) 0.5f else 1.0f,
                if (isPowerOn) 1.0f else 0.5f
            ).apply {
                duration = 300
                addUpdateListener { animator ->
                    val alpha = animator.animatedValue as Float
                    updateControlsVisibility(alpha)
                }
                start()
            }
        } else {
            switchButton.iconTint = ColorStateList.valueOf(
                ContextCompat.getColor(
                    this,
                    if (isPowerOn) R.color.barbie_pink else R.color.barbie_pink_dark
                )
            )
            updateControlsVisibility(if (isPowerOn) 1.0f else 0.5f)
        }

        enablePumpControls(isPowerOn)
    }

    private fun updateControlsVisibility(alpha: Float) {
        playPauseButton.alpha = alpha
        automaticModeButton.alpha = alpha
        massageModeButton.alpha = alpha
        expressionModeButton.alpha = alpha
        simulationModeButton.alpha = alpha
        decreaseButton.alpha = alpha
        increaseButton.alpha = alpha
        currentLevelText.alpha = alpha
        timerTextView.alpha = alpha
        timerLabelTextView.alpha = alpha
        rightEggShape.alpha = alpha
        rightDurationText.alpha = alpha
    }

    private fun enablePumpControls(enabled: Boolean) {
        playPauseButton.isEnabled = enabled
        automaticModeButton.isEnabled = enabled
        massageModeButton.isEnabled = enabled
        expressionModeButton.isEnabled = enabled
        simulationModeButton.isEnabled = enabled
        decreaseButton.isEnabled = enabled
        increaseButton.isEnabled = enabled
        rightEggShape.isEnabled = enabled
        rightDurationText.isEnabled = enabled

        val alpha = if (enabled) 1.0f else 0.5f
        automaticModeButton.alpha = alpha
        massageModeButton.alpha = alpha
        expressionModeButton.alpha = alpha
        simulationModeButton.alpha = alpha
        decreaseButton.alpha = alpha
        increaseButton.alpha = alpha
        currentLevelText.alpha = alpha
    }

    private fun setupAnimations() {

    }

    private fun setupPumpData() {
        pumpId = intent.getStringExtra("pump_id")
        device = ThingHomeSdk.newDeviceInstance(pumpId)

        val pumpName = intent.getStringExtra("pump_name")
        dpsMap = intent.getSerializableExtra("pump_dps") as? HashMap<String, Any>

        if (pumpName != null) {
            pumpNameTextView.text = pumpName.toLowerCase()
        }

        if (pumpId != null) {
            loadPumpDetails()
            loadBatteryStatus()
        } else {
            Toast.makeText(this, "No pump information available", Toast.LENGTH_SHORT).show()
        }
    }

    private fun initializeLevelControl() {
        // Set initial level display
        currentLevelText.text = currentLevel.toString()

        // Set up control buttons
        decreaseButton.setOnClickListener {
            if (currentLevel > 1) {
                currentLevel--
                currentLevelText.text = currentLevel.toString()
                updatePumpLevel()
            }
        }

        increaseButton.setOnClickListener {
            if (currentLevel < maxLevel) {
                currentLevel++
                currentLevelText.text = currentLevel.toString()
                updatePumpLevel()
            }
        }
    }


    private fun updatePumpLevel() {
        // Implement the logic to send the level to the pump device

    }

    private fun loadPumpDetails() {
        device?.registerDevListener(object : IDevListener {
            override fun onDpUpdate(devId: String, dpStr: String) {
                // Handle DP updates here
            }

            override fun onRemoved(devId: String) {
                Toast.makeText(this@PumpControlActivity, "Pump has been removed", Toast.LENGTH_SHORT).show()
                finish()
            }

            override fun onStatusChanged(devId: String, online: Boolean) {
                runOnUiThread {
                    // Update status UI if needed
                }
            }

            override fun onNetworkStatusChanged(devId: String, status: Boolean) {}
            override fun onDevInfoUpdate(devId: String) {}
        })
    }

    private fun loadBatteryStatus() {
        val dpId = "15" // DP ID for battery status
        dpsMap?.let {
            batteryStatusTextView.text = it[dpId].toString()
        }
    }

    private fun setupModeSelector() {
        val modeButtons = mapOf(
            automaticModeButton to PumpMode.AUTOMATIC,
            massageModeButton to PumpMode.MASSAGE,
            expressionModeButton to PumpMode.EXPRESSION,
            simulationModeButton to PumpMode.SIMULATION
        )

        // Set initial state
        updateModeSelection(modeButtons, expressionModeButton, selectedModeText)

        // Set up click listeners
        modeButtons.forEach { (button, mode) ->
            button.setOnClickListener {
                currentMode = mode
                updateModeSelection(modeButtons, button, selectedModeText)
                handleModeChange(mode)
            }
        }
    }

    private fun updateModeSelection(
        modeButtons: Map<ImageButton, PumpMode>,
        selectedButton: ImageButton,
        selectedModeText: TextView
    ) {
        modeButtons.forEach { (button, _) ->
            button.isSelected = (button == selectedButton)
            button.imageTintList = ColorStateList.valueOf(
                if (button == selectedButton)
                    ContextCompat.getColor(this, R.color.white)
                else
                    ContextCompat.getColor(this, R.color.barbie_pink)
            )
        }

        selectedModeText.text = when(modeButtons[selectedButton]) {
            PumpMode.AUTOMATIC -> "Automatic"
            PumpMode.MASSAGE -> "Massage"
            PumpMode.EXPRESSION -> "Expression"
            PumpMode.SIMULATION -> "Simulation"
            null -> "Expression"
        }
    }

    private fun handleModeChange(mode: PumpMode) {
        // Implement mode-specific logic here
        val dpValue = when (mode) {
            PumpMode.AUTOMATIC -> "automatic"
            PumpMode.MASSAGE -> "massage"
            PumpMode.EXPRESSION -> "expression"
            PumpMode.SIMULATION -> "simulation"
        }


    }

    private fun setupTimeoutDialog() {
        // Make the egg and timeout text clickable
        eggProgressView.setOnClickListener { showTimeoutDialog() }
        rightDurationText.setOnClickListener { showTimeoutDialog() }
    }

    private fun showTimeoutDialog() {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_timeout_settings, null)
        dialog.setContentView(view)

        val timeoutInput = view.findViewById<TextInputEditText>(R.id.timeoutInput)
        val saveButton = view.findViewById<MaterialButton>(R.id.saveButton)
        val cancelButton = view.findViewById<MaterialButton>(R.id.cancelButton)

        // Set current timeout as default
        timeoutInput.setText(maxMinutes.toString())

        saveButton.setOnClickListener {
            val newTimeout = timeoutInput.text.toString().toIntOrNull()
            if (newTimeout == null || newTimeout <= 0) {
                timeoutInput.error = "Please enter a valid timeout"
                return@setOnClickListener
            }

            // Update timeout
            updateMaxDuration(newTimeout)
            dialog.dismiss()

            // Show confirmation toast
            Toast.makeText(
                this,
                "Timer timeout updated to $newTimeout minutes",
                Toast.LENGTH_SHORT
            ).show()
        }

        cancelButton.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun completeShutdown() {
        isPowerOn = false
        isPlaying = false
        resetTimer()
        rippleEggAnimation.stop()  // Make sure animation stops on shutdown
        updatePowerButtonState(animate = true)
        updatePlayPauseButtonState(animate = true)
    }

    override fun onDestroy() {
        super.onDestroy()
        timerHandler.removeCallbacks(timerRunnable)
        rippleEggAnimation.stop()  // Make sure animation stops when activity is destroyed
        device?.unRegisterDevListener()
    }
}