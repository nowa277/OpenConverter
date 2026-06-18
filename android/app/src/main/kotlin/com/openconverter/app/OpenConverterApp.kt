package com.openconverter.app

import android.app.Application
import com.openconverter.app.crash.CrashReporter

class OpenConverterApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Install as early as possible so any later init failure is captured.
        CrashReporter.install(this)
    }
}
