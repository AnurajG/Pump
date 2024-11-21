package com.eomma.pump

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import android.widget.ViewFlipper
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.thingclips.smart.android.ble.api.LeScanSetting
import com.thingclips.smart.android.ble.api.ScanDeviceBean
import com.thingclips.smart.android.ble.api.ScanType
import com.thingclips.smart.home.sdk.ThingHomeSdk
import com.thingclips.smart.home.sdk.bean.HomeBean
import com.thingclips.smart.home.sdk.callback.IThingGetHomeListCallback
import com.thingclips.smart.home.sdk.callback.IThingHomeResultCallback
import com.thingclips.smart.sdk.api.IBleActivatorListener
import com.thingclips.smart.sdk.bean.BleActivatorBean
import com.thingclips.smart.sdk.bean.DeviceBean

class PumpScanActivity : AppCompatActivity() {

    private lateinit var viewFlipper: ViewFlipper
    private lateinit var foundDevicesList: RecyclerView
    private lateinit var scanStatusText: TextView
    private lateinit var deviceAdapter: FoundDevicesAdapter
    private lateinit var foundDevicesTitle: TextView

    private var isScanning = false
    private val foundDevices = mutableListOf<ScanDeviceBean>()
    private var foundHome: HomeBean? = null

    //private val TARGET_MAC_ADDRESS = "DC:23:50:FE:51:3E"

    private val requiredPermissions = arrayOf(
        Manifest.permission.BLUETOOTH,
        Manifest.permission.BLUETOOTH_ADMIN,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT
    )

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { it.value }) {
            startPumpScan()
        } else {
            Toast.makeText(this, "Permissions are required to use BLE", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pump_scan)

        setupViews()
        setupRecyclerView()
        queryHomeList()
    }

    override fun onResume() {
        super.onResume()
        if (checkPermissions()) {
            startPumpScan()
        } else {
            requestPermissions()
        }
    }

    override fun onPause() {
        super.onPause()
        stopScan()
    }

    private fun setupViews() {
        viewFlipper = findViewById(R.id.viewFlipper)
        foundDevicesList = findViewById(R.id.foundDevicesList)
        scanStatusText = findViewById(R.id.scanStatusText)
        foundDevicesTitle = findViewById(R.id.foundDevicesTitle)

        findViewById<View>(R.id.backButton).setOnClickListener {
            finish()
        }
    }

    private fun setupRecyclerView() {
        deviceAdapter = FoundDevicesAdapter { device ->
            if (foundHome != null) {
                pairWithPump(device)
            } else {
                Toast.makeText(this, "Please wait while we set up your home", Toast.LENGTH_SHORT).show()
            }
        }

        foundDevicesList.apply {
            layoutManager = LinearLayoutManager(this@PumpScanActivity)
            adapter = deviceAdapter
        }
    }

    private fun queryHomeList() {
        ThingHomeSdk.getHomeManagerInstance()
            .queryHomeList(object : IThingGetHomeListCallback {
                override fun onSuccess(homeBeans: List<HomeBean>) {
                    if (homeBeans.isEmpty()) {
                        createDefaultHome()
                    } else {
                        foundHome = homeBeans[0]
                    }
                }

                override fun onError(errorCode: String, error: String) {
                    Toast.makeText(
                        this@PumpScanActivity,
                        "Failed to get home: $error",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })
    }

    private fun createDefaultHome() {
        val rooms: List<String> = listOf()
        ThingHomeSdk.getHomeManagerInstance().createHome(
            "default",
            0.0,
            0.0,
            "default",
            rooms,
            object : IThingHomeResultCallback {
                override fun onSuccess(bean: HomeBean) {
                    foundHome = bean
                }

                override fun onError(errorCode: String, errorMsg: String) {
                    Toast.makeText(
                        this@PumpScanActivity,
                        "Failed to create home: $errorMsg",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })
    }

    private fun checkPermissions(): Boolean {
        return requiredPermissions.all { permission ->
            ContextCompat.checkSelfPermission(
                this,
                permission
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestPermissions() {
        requestPermissionLauncher.launch(requiredPermissions)
    }

    private fun updateUI(scanning: Boolean) {
        isScanning = scanning
        scanStatusText.text = if (scanning) "Scanning for pumps..." else "Scan complete"
        foundDevicesTitle.visibility = if (foundDevices.isEmpty()) View.GONE else View.VISIBLE
        viewFlipper.displayedChild = if (foundDevices.isEmpty()) 0 else 1
    }

    @SuppressLint("MissingPermission")
    private fun startPumpScan() {
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            Toast.makeText(this, "Please enable Bluetooth", Toast.LENGTH_SHORT).show()
            return
        }

        val hasBleSupport = packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
        if (!hasBleSupport) {
            Toast.makeText(this, "No BLE Support", Toast.LENGTH_SHORT).show()
            return
        }

        foundDevices.clear()
        deviceAdapter.submitList(emptyList())

        updateUI(true)

        val scanSetting = LeScanSetting.Builder()
            .setTimeout(30000)
            .addScanType(ScanType.SINGLE)
            .build()

        ThingHomeSdk.getBleOperator().startLeScan(scanSetting) { scanDeviceBean ->
            if (true) {
                if (!foundDevices.any { it.address == scanDeviceBean.address }) {
                    foundDevices.add(scanDeviceBean)
                    runOnUiThread {
                        deviceAdapter.submitList(foundDevices.toList())
                        updateUI(true)
                    }
                }
            } else {
                Log.d("BLEScan", "Non-target device found with MAC: ${scanDeviceBean.address}")
            }
        }

        scanStatusText.postDelayed({
            if (foundDevices.isEmpty()) {
                startPumpScan()
            } else {
                updateUI(false)
            }
        }, 35000)
    }

    private fun stopScan() {
        if (isScanning) {
            ThingHomeSdk.getBleOperator().stopLeScan()
            updateUI(false)
        }
    }

    private fun pairWithPump(scanDeviceBean: ScanDeviceBean) {
        stopScan()
        val previousText = scanStatusText.text
        scanStatusText.text = "Pairing with pump..."

        val bleActivatorBean = BleActivatorBean().apply {
            homeId = foundHome?.homeId!!
            address = scanDeviceBean.address
            deviceType = scanDeviceBean.deviceType
            uuid = scanDeviceBean.uuid
            productId = scanDeviceBean.productId
            isShare = (scanDeviceBean.flag shr 2) and 0x01 == 1
        }

        ThingHomeSdk.getActivator().newBleActivator()
            .startActivator(bleActivatorBean, object : IBleActivatorListener {
                override fun onSuccess(deviceBean: DeviceBean) {
                    runOnUiThread {
                        Toast.makeText(
                            this@PumpScanActivity,
                            "Successfully paired with pump",
                            Toast.LENGTH_SHORT
                        ).show()
                        finish()
                    }
                }

                override fun onFailure(code: Int, msg: String, handle: Any?) {
                    runOnUiThread {
                        scanStatusText.text = previousText
                        Toast.makeText(
                            this@PumpScanActivity,
                            "Failed to pair: $msg",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            })
    }
}

class FoundDevicesAdapter(
    private val onPairClick: (ScanDeviceBean) -> Unit
) : ListAdapter<ScanDeviceBean, FoundDevicesAdapter.DeviceViewHolder>(DeviceDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_found_pump, parent, false)
        return DeviceViewHolder(view, onPairClick)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class DeviceViewHolder(
        view: View,
        private val onPairClick: (ScanDeviceBean) -> Unit
    ) : RecyclerView.ViewHolder(view) {
        private val pumpName: TextView = view.findViewById(R.id.pumpName)
        private val pairButton: MaterialButton = view.findViewById(R.id.pairButton)
        private var currentDevice: ScanDeviceBean? = null

        init {
            pairButton.setOnClickListener {
                currentDevice?.let { device ->
                    onPairClick(device)
                }
            }
        }

        fun bind(device: ScanDeviceBean) {
            currentDevice = device
            pumpName.text = "Eomma Pump (${device.id})"
        }
    }

    private class DeviceDiffCallback : DiffUtil.ItemCallback<ScanDeviceBean>() {
        override fun areItemsTheSame(oldItem: ScanDeviceBean, newItem: ScanDeviceBean): Boolean {
            return oldItem.address == newItem.address
        }

        override fun areContentsTheSame(oldItem: ScanDeviceBean, newItem: ScanDeviceBean): Boolean {
            return oldItem.address == newItem.address &&
                    oldItem.deviceType == newItem.deviceType &&
                    oldItem.productId == newItem.productId
        }
    }
}