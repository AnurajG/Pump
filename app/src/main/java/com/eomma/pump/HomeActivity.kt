package com.eomma.pump

import android.content.Intent
import android.graphics.Color
import android.graphics.PorterDuff
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import android.widget.ViewFlipper
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.thingclips.smart.home.sdk.ThingHomeSdk
import com.thingclips.smart.home.sdk.bean.HomeBean
import com.thingclips.smart.home.sdk.callback.IThingGetHomeListCallback
import com.thingclips.smart.home.sdk.callback.IThingHomeResultCallback
import com.thingclips.smart.sdk.bean.DeviceBean

class HomeActivity : AppCompatActivity() {
    private lateinit var viewFlipper: ViewFlipper
    private lateinit var pairedPumpsRecyclerView: RecyclerView
    private lateinit var addPumpButton: Button
    private lateinit var bottomNavigationView: BottomNavigationView
    private var pairedPumps: List<DeviceBean>? = null
    private var foundHome: HomeBean? = null
    private lateinit var pumpAdapter: PairedPumpsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Make system bars work with our gradient
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContentView(R.layout.activity_home)

        initializeViews()
        setupRecyclerView()
        queryHomeList()
        setupClickListeners()
        setupBottomNavigation()
    }

    private fun initializeViews() {
        viewFlipper = findViewById(R.id.viewFlipper)
        pairedPumpsRecyclerView = findViewById(R.id.paired_pumps_list_view)
        addPumpButton = findViewById(R.id.add_pump_button)
        bottomNavigationView = findViewById(R.id.bottomNavigation)
    }

    private fun setupRecyclerView() {
        pairedPumpsRecyclerView.layoutManager = LinearLayoutManager(this)
        pumpAdapter = PairedPumpsAdapter { selectedPump ->
            navigateToPumpControl(selectedPump)
        }
        pairedPumpsRecyclerView.adapter = pumpAdapter
    }

    private fun setupBottomNavigation() {
        bottomNavigationView.setOnItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.navigation_devices -> {
                    // Already on home, do nothing
                    true
                }
                R.id.navigation_statistics -> {
                    startActivity(Intent(this, PumpSessionsActivity::class.java))
                    false // Don't select the tab since we're leaving this activity
                }
                R.id.navigation_me -> {
                    startActivity(Intent(this, ProfileActivity::class.java))
                    false // Don't select the tab since we're leaving this activity
                }
                else -> false
            }
        }

        // Set the home item as selected by default
        bottomNavigationView.selectedItemId = R.id.navigation_devices
    }

    private fun queryHomeList() {
        ThingHomeSdk.getHomeManagerInstance()
            .queryHomeList(object : IThingGetHomeListCallback {
                override fun onSuccess(homeBeans: List<HomeBean>) {
                    if (homeBeans.isEmpty()) {
                        createDefaultHome()
                    } else {
                        foundHome = homeBeans[0]
                        loadPairedPumps()
                    }
                }

                override fun onError(errorCode: String, error: String) {
                    Toast.makeText(this@HomeActivity, "Failed to home: $error", Toast.LENGTH_SHORT).show()
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
                    loadPairedPumps()
                }

                override fun onError(errorCode: String, errorMsg: String) {
                    Toast.makeText(this@HomeActivity, "Failed to home: $errorMsg", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun setupClickListeners() {
        // Existing add pump button listener
        addPumpButton.setOnClickListener {
            startActivity(Intent(this@HomeActivity, PumpScanActivity::class.java))
        }

        // Add FAB click listener
        findViewById<FloatingActionButton>(R.id.add_activity_button).setOnClickListener {
            startActivity(Intent(this@HomeActivity, PumpScanActivity::class.java))
        }
    }

    private fun loadPairedPumps() {
        foundHome?.let {
            ThingHomeSdk.newHomeInstance(it.homeId).getHomeDetail(object : IThingHomeResultCallback {
                override fun onSuccess(homeBean: HomeBean) {
                    pairedPumps = homeBean.deviceList
                    updatePumpListUI()
                }

                override fun onError(errorCode: String, errorMsg: String) {
                    Toast.makeText(this@HomeActivity, "Failed to load pumps: $errorMsg", Toast.LENGTH_SHORT).show()
                }
            })
        }
    }

    private fun updatePumpListUI() {
        if (pairedPumps.isNullOrEmpty()) {
            viewFlipper.displayedChild = 0 // Show empty state
        } else {
            viewFlipper.displayedChild = 1 // Show pump list
            pumpAdapter.submitList(pairedPumps)
        }
    }

    private fun navigateToPumpControl(pump: DeviceBean) {
        val intent = Intent(this@HomeActivity, PumpControlActivity::class.java).apply {
            putExtra("pump_id", pump.devId)
            if(pump.productId == "gzakm5mf")
                putExtra("pump_name", "Eomma 360")
            else
                putExtra("pump_name", pump.name)
            putExtra("pump_dps", HashMap(pump.getDps()))
        }
        startActivity(intent)
    }
}

class PairedPumpsAdapter(
    private val onPumpClick: (DeviceBean) -> Unit
) : RecyclerView.Adapter<PairedPumpsAdapter.PumpViewHolder>() {

    private var pumps: List<DeviceBean> = emptyList()

    fun submitList(newPumps: List<DeviceBean>?) {
        pumps = newPumps ?: emptyList()
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PumpViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_pump, parent, false)
        return PumpViewHolder(view, onPumpClick)
    }

    override fun onBindViewHolder(holder: PumpViewHolder, position: Int) {
        val pump = pumps[position]
        holder.bind(pump)
    }

    override fun getItemCount() = pumps.size

    class PumpViewHolder(
        itemView: View,
        private val onPumpClick: (DeviceBean) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val pumpIcon: ImageView = itemView.findViewById(R.id.pump_icon)
        private val pumpName: TextView = itemView.findViewById(R.id.pump_name)
        private val connectionStatus: ImageView = itemView.findViewById(R.id.connection_status)
        private val batteryLevel: View = itemView.findViewById(R.id.battery_level)
        private var currentPump: DeviceBean? = null

        init {
            itemView.setOnClickListener {
                currentPump?.let { pump ->
                    onPumpClick(pump)
                }
            }
        }

        fun bind(pump: DeviceBean) {
            currentPump = pump
            if(pump.productId == "gzakm5mf")
                pumpName.text="Eomma 360"
            else
                pumpName.text = pump.name

            // Set connection status icon
            connectionStatus.setImageResource(
                if (pump.isOnline) R.drawable.ic_bluetooth_connected
                else R.drawable.ic_bluetooth_disconnected
            )

            // Update battery level
            val batteryPercentage = pump.getDps()["15"] as? Int ?: 0
            updateBatteryLevel(batteryPercentage)
        }

        private fun updateBatteryLevel(percentage: Int) {
            val layoutParams = batteryLevel.layoutParams as FrameLayout.LayoutParams
            // Calculate width based on percentage (14dp is the max width of curved battery inside)
            val maxWidth = itemView.context.resources.getDimensionPixelSize(R.dimen.battery_max_width)
            layoutParams.width = (maxWidth * (percentage / 100f)).toInt()
            batteryLevel.layoutParams = layoutParams

            // Update color based on level with alpha for softer look
            val color = when {
                percentage <= 20 -> Color.parseColor("#FFFF4444") // Soft red
                percentage <= 50 -> Color.parseColor("#FFFFA726") // Soft orange
                else -> Color.parseColor("#FFE31C79") // Soft barbie pink
            }
            batteryLevel.setBackgroundResource(R.drawable.battery_level_background)
            batteryLevel.background.setColorFilter(color, PorterDuff.Mode.SRC_IN)
        }
    }
}