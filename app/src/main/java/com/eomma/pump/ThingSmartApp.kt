package com.eomma.pump

import android.app.Application
import com.thingclips.smart.home.sdk.ThingHomeSdk
import com.thingclips.smart.sdk.ThingSdk


class ThingSmartApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ThingHomeSdk.init(this, "k3saxpgkh8w54pjgdfwp", "hhxggkfwa55wnrgu84k5nyegukjump5s")
        ThingHomeSdk.setDebugMode(true)

        val database = PumpingSessionDatabase.getDatabase(applicationContext)
        val repository = PumpingSessionRepository(database.pumpingSessionDao())
    }
}
