package com.openconverter.app.decoders.kgg

import android.net.Uri

fun interface KggKeyProvider {
    fun find(encryptionKeyId: String): String?
}

fun interface KggKeyImporter {
    suspend fun import(uri: Uri): KggImportResult
}

data class KggImportResult(
    val added: Int,
    val updated: Int,
    val total: Int,
)

sealed interface KggImportState {
    data class Ready(val total: Int, val lastResult: KggImportResult? = null) : KggImportState
    data object Importing : KggImportState
    data class Failed(val message: String, val total: Int) : KggImportState
}
