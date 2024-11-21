package com.eomma.pump

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
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.eomma.pump.PumpCharacteristics.Companion.DPS_BREAST_SUCKING
import com.eomma.pump.PumpCharacteristics.Companion.DPS_MASSAGE
import com.eomma.pump.PumpCharacteristics.Companion.DPS_SUCK
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.thingclips.smart.android.ble.builder.BleConnectBuilder
import com.thingclips.smart.home.sdk.ThingHomeSdk
import com.thingclips.smart.sdk.api.IDevListener
import com.thingclips.smart.sdk.api.IResultCallback
import com.thingclips.smart.sdk.api.IThingDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
    private lateinit var deletePumpButton: MaterialButton
    private lateinit var rippleEggAnimation: AnimatedVectorDrawable
    private lateinit var connectingOverlay: View

    private lateinit var repository: PumpingSessionRepository
    private lateinit var pumpCharacteristics: PumpCharacteristics

    private var pumpId: String? = null
    private var device: IThingDevice? = null
    private var timerSeconds = 0
    private var isTimerRunning = false
    private val timerHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pump_control)

        connectingOverlay = findViewById(R.id.connecting_overlay)
        checkConnection()

        initializeViews()
        initializeComponents()
        setupListeners()
        setupDeviceConnection()
        updateUI()
    }

    private fun checkConnection() {
        lifecycleScope.launch {
            while (!ThingHomeSdk.getBleManager().isBleLocalOnline(pumpId)) {
                connectingOverlay.visibility = View.VISIBLE
                delay(500)
            }
            connectingOverlay.visibility = View.GONE
        }
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
        deletePumpButton = findViewById(R.id.deletePumpButton)

        val imageView = findViewById<ImageView>(R.id.ripple_egg_image)
        rippleEggAnimation = imageView.drawable as AnimatedVectorDrawable
    }

    private fun initializeComponents() {
        repository = PumpingSessionRepository(PumpingSessionDatabase.getDatabase(this).pumpingSessionDao())
        pumpCharacteristics = PumpCharacteristics()

        // Initialize pump data from intent
        pumpId = intent.getStringExtra("pump_id")
        pumpCharacteristics.updateFromDpsMap(intent.getSerializableExtra("pump_dps") as? HashMap<String, Any>)
        pumpNameTextView.text = intent.getStringExtra("pump_name")?.toLowerCase()
    }

    private fun setupListeners() {
        setupControlButtons()
        setupModeButtons()
        setupTimeoutControl() // renamed from setupTimerControls for clarity
    }

    private fun setupTimeoutControl() {
        // Make the egg progress and timeout text clickable to show timeout dialog
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
        timeoutInput.setText(pumpCharacteristics.shutdownTime.toString())

        saveButton.setOnClickListener {
            val newTimeout = timeoutInput.text.toString().toIntOrNull()
            if (newTimeout == null || newTimeout <= 0) {
                timeoutInput.error = "Please enter a valid timeout"
                return@setOnClickListener
            }

            pumpCharacteristics.shutdownTime = newTimeout
            updateUI()
            publishDeviceCommand(pumpCharacteristics.generateDpsCommand(PumpCharacteristics.DPS_SHUTDOWN_TIME))
            dialog.dismiss()
            showToast("Timer timeout updated to $newTimeout minutes")
        }

        cancelButton.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showSaveSessionDialog() {
        if (pumpCharacteristics.isPlaying) {
            handlePlayPauseToggle()
        }

        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_save_session, null)
        dialog.setContentView(view)

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

        // Side selector handlers
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
                showToast("Please select a side")
                return@setOnClickListener
            }

            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    saveSession(PumpingSession(
                        duration = timerSeconds,
                        volume = volume,
                        side = selectedSide!!,
                        deviceId = pumpId
                    ))
                    withContext(Dispatchers.Main) {
                        dialog.dismiss()
                        shutdownPump()
                        startActivity(Intent(this@PumpControlActivity, PumpSessionsActivity::class.java))
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        showToast("Error saving session: ${e.message}")
                    }
                }
            }
        }

        cancelButton.setOnClickListener {
            dialog.dismiss()
            pumpCharacteristics.isPowerOn = true
            updateUI()
        }

        dialog.show()
    }

    private suspend fun saveSession(session: PumpingSession) {
        withContext(Dispatchers.IO) {
            try {
                repository.insert(session)
                withContext(Dispatchers.Main) {
                    showToast("Session saved successfully")
                }
            } catch (e: Exception) {
                throw e
            }
        }
    }

    private fun setupControlButtons() {
        // Power button
        switchButton.setOnClickListener {
            handlePowerToggle()
        }

        // Play/Pause button
        playPauseButton.setOnClickListener {
            if (!pumpCharacteristics.isPowerOn) return@setOnClickListener
            handlePlayPauseToggle()
        }

        // Level control
        decreaseButton.setOnClickListener {
            if (pumpCharacteristics.decrementLevel()) {
                updateUI()
                publishDeviceCommand(pumpCharacteristics.getCurrentLevelDpsCommand())
            }
        }

        increaseButton.setOnClickListener {
            if (pumpCharacteristics.incrementLevel()) {
                updateUI()
                publishDeviceCommand(pumpCharacteristics.getCurrentLevelDpsCommand())
            }
        }

        // Delete pump
        deletePumpButton.setOnClickListener {
            showConfirmationDialog(
                "Remove Pump",
                "Are you sure you want to remove this pump? This action cannot be undone.",
                onConfirm = { deletePump() }
            )
        }
    }

    private fun setupModeButtons() {
        val modeButtons = mapOf(
            automaticModeButton to PumpCharacteristics.PumpMode.SUCK,
            massageModeButton to PumpCharacteristics.PumpMode.MASSAGE,
            expressionModeButton to PumpCharacteristics.PumpMode.BREAST_SUCKING,
            simulationModeButton to PumpCharacteristics.PumpMode.NIPPLE_TRACTION
        )

        modeButtons.forEach { (button, mode) ->
            button.setOnClickListener {
                pumpCharacteristics.setMode(mode)
                publishDeviceCommand(pumpCharacteristics.generateDpsCommand(PumpCharacteristics.DPS_MODE))
                updateUI()
                device?.getDp("5", object : IResultCallback {
                    override fun onError(code: String, error: String) {
                    }

                    override fun onSuccess() {
                    }
                })

            }
        }
    }
    private fun connectToDevice() {
        if (pumpId == null) {
            Toast.makeText(this, "Device ID is missing", Toast.LENGTH_SHORT).show()
            return
        }

        val builderList = ArrayList<BleConnectBuilder>()
        val bleConnectBuilder = BleConnectBuilder().apply {
            devId = pumpId
        }
        builderList.add(bleConnectBuilder)

        ThingHomeSdk.getBleManager().connectBleDevice(builderList)
    }

    private fun setupDeviceConnection() {
        pumpId?.let { id ->
            device = ThingHomeSdk.newDeviceInstance(id)
            connectToDevice()
            device?.registerDevListener(object : IDevListener {
                override fun onDpUpdate(devId: String, dpStr: String) {
                    DpsParser.parseDpsString(dpStr)?.let { dpsMap ->
                        pumpCharacteristics.updateFromDpsMap(dpsMap)
                        runOnUiThread { updateSpecificDps(dpsMap) }
                    }
                }

                override fun onRemoved(devId: String) {
                    runOnUiThread {
                        Toast.makeText(this@PumpControlActivity, "Pump has been removed", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }

                override fun onStatusChanged(devId: String, online: Boolean) {}
                override fun onNetworkStatusChanged(devId: String, status: Boolean) {}
                override fun onDevInfoUpdate(devId: String) {}
            })
        }
    }
    private fun updateSpecificDps(dpsMap: Map<String, Any>) {
        dpsMap.forEach { (dp, _) ->
            when (dp) {
                PumpCharacteristics.DPS_SWITCH -> {
                    // Update power state
                    switchButton.isChecked = pumpCharacteristics.isPowerOn
                    switchButton.iconTint = ColorStateList.valueOf(
                        ContextCompat.getColor(
                            this,
                            if (pumpCharacteristics.isPowerOn) R.color.white else R.color.gray_light
                        )
                    )
                    updateControlsState()
                }
                PumpCharacteristics.DPS_START -> {
                    if(pumpCharacteristics.isPowerOn) {
                        if (pumpCharacteristics.isPlaying) {
                            startTimer()
                            playPauseButton.setIconResource(R.drawable.ic_pause)
                            rippleEggAnimation.start()
                        } else {
                            pauseTimer()
                            playPauseButton.setIconResource(R.drawable.ic_play)
                            rippleEggAnimation.stop()
                        }
                    }
                }
                PumpCharacteristics.DPS_MODE -> {
                    selectedModeText.text = pumpCharacteristics.getModeDisplayName()
                    updatePumpMode()
                }
                PumpCharacteristics.DPS_LACTATION, DPS_BREAST_SUCKING, DPS_SUCK, DPS_MASSAGE -> {
                    currentLevelText.text = pumpCharacteristics.currentLevel.toString()
                }
                PumpCharacteristics.DPS_BATTERY -> {
                    batteryStatusTextView.text = "${pumpCharacteristics.getBatteryPercentage()}% (${pumpCharacteristics.getBatteryStatus()})"
                }
                PumpCharacteristics.DPS_SHUTDOWN_TIME -> {
                    rightDurationText.text = "${pumpCharacteristics.shutdownTime}min Timeout"
                }
            }
        }
    }

    private fun handlePowerToggle() {
        if (pumpCharacteristics.isPowerOn) {
            if (timerSeconds > 0) showSaveSessionDialog() else shutdownPump()
        } else {
            pumpCharacteristics.isPowerOn = true
            updateUI()
            publishDeviceCommand(pumpCharacteristics.generateDpsCommand(PumpCharacteristics.DPS_SWITCH))
        }
    }

    private fun handlePlayPauseToggle() {
        pumpCharacteristics.isPlaying = !pumpCharacteristics.isPlaying
        if (pumpCharacteristics.isPlaying) {
            startTimer()
            rippleEggAnimation.start()
        } else {
            pauseTimer()
            rippleEggAnimation.stop()
        }
        updateUI()
        publishDeviceCommand(pumpCharacteristics.generateDpsCommand(PumpCharacteristics.DPS_START))
    }

    private fun updateUI() {
        // Update power state
        switchButton.isChecked = pumpCharacteristics.isPowerOn
        switchButton.iconTint = ColorStateList.valueOf(
            ContextCompat.getColor(
                this,
                if (pumpCharacteristics.isPowerOn) R.color.white else R.color.gray_light
            )
        )

        if(pumpCharacteristics.isPowerOn){
            if (pumpCharacteristics.isPlaying) {
                startTimer()
                playPauseButton.setIconResource(R.drawable.ic_pause)
                rippleEggAnimation.start()
            } else {
                pauseTimer()
                playPauseButton.setIconResource(R.drawable.ic_play)
                rippleEggAnimation.stop()
            }
        }else{
            playPauseButton.setIconResource(R.drawable.ic_play)
        }

        // Update mode selection
        selectedModeText.text = pumpCharacteristics.getModeDisplayName()

        // Update level display
        currentLevelText.text = pumpCharacteristics.currentLevel.toString()

        // Update battery status
        batteryStatusTextView.text = "${pumpCharacteristics.getBatteryPercentage()}% (${pumpCharacteristics.getBatteryStatus()})"



        rightDurationText.text =  "${pumpCharacteristics.shutdownTime}min Timeout"

        pumpCharacteristics.currentMode

        updatePumpMode()

        // Update controls state
        updateControlsState()
    }

    private fun updatePumpMode() {
        // Reset all mode button backgrounds
        val modeButtons = mapOf(
            automaticModeButton to PumpCharacteristics.PumpMode.SUCK,
            massageModeButton to PumpCharacteristics.PumpMode.MASSAGE,
            expressionModeButton to PumpCharacteristics.PumpMode.BREAST_SUCKING,
            simulationModeButton to PumpCharacteristics.PumpMode.LACTATION
        )

        // Update button states
        modeButtons.forEach { (button, mode) ->
            val isSelected = mode == pumpCharacteristics.currentMode
            button.isSelected = isSelected
            button.imageTintList = ColorStateList.valueOf(
                ContextCompat.getColor(
                    this,
                    if (isSelected) R.color.barbie_pink else R.color.white
                )
            )
            button.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(
                    this,
                    if (isSelected) R.color.white else R.color.barbie_pink
                )
            )
        }

        // Update mode text
        selectedModeText.text = pumpCharacteristics.getModeDisplayName()
    }

    private fun updateControlsState() {
        val enabled = pumpCharacteristics.isPowerOn
        val alpha = if (enabled) 1.0f else 0.5f

        val controls = listOf(
            playPauseButton,
            automaticModeButton,
            massageModeButton,
            expressionModeButton,
            simulationModeButton,
            decreaseButton,
            increaseButton,
            currentLevelText,
            timerTextView,
            timerLabelTextView,
            rightEggShape,
            rightDurationText
        )

        controls.forEach {
            it.isEnabled = enabled
            it.alpha = alpha
        }
    }

    private fun publishDeviceCommand(command: String) {
        device?.publishDps(command, object : IResultCallback {
            override fun onError(code: String?, error: String?) {
                showToast("Command failed: $error")
            }
            override fun onSuccess() {}
        })
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

    private fun updateTimerDisplay() {
        val minutes = timerSeconds / 60
        val seconds = timerSeconds % 60
        timerTextView.text = String.format("%02d:%02d", minutes, seconds)
        val progress = (timerSeconds / (pumpCharacteristics.shutdownTime * 60f)).coerceIn(0f, 1f)
        eggProgressView.setProgress(progress)
    }

    private fun deletePump() {
        device?.removeDevice(object : IResultCallback {
            override fun onError(code: String?, error: String?) {
                showToast("Failed to remove pump: $error")
            }

            override fun onSuccess() {
                showToast("Pump removed successfully")
                finish()
            }
        })
    }

    private fun showConfirmationDialog(title: String, message: String, onConfirm: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Confirm") { _, _ -> onConfirm() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun shutdownPump() {
        pumpCharacteristics.isPowerOn = false
        pumpCharacteristics.isPlaying = false
        timerSeconds = 0
        rippleEggAnimation.stop()
        updateUI()
        publishDeviceCommand(pumpCharacteristics.generateDpsCommand(PumpCharacteristics.DPS_SWITCH))
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        timerHandler.removeCallbacks(timerRunnable)
        rippleEggAnimation.stop()
        device?.unRegisterDevListener()
    }
}