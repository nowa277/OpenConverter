package com.openconverter.app.ui.vm

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import com.openconverter.app.service.ConversionService

class ConversionViewModel(app: Application) : AndroidViewModel(app) {

    fun startConversion(
        uris: List<Uri>,
        targetFormat: String,
        targetDirUri: Uri? = null,
    ) {
        ConversionService.start(getApplication(), uris, targetFormat, targetDirUri)
    }
}
