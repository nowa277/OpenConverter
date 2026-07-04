package com.openconverter.app

import android.app.Application
import com.openconverter.app.decoders.kgg.KggKeyStore

class OpenConverterApp : Application() {
    val kggKeyStore: KggKeyStore by lazy { KggKeyStore(this) }
}
