package com.openconverter.app.engine

enum class HistoryStatus { SUCCESS, FAILED }

data class HistoryRecord(
    val ts: Long,
    val inputName: String,
    val targetFormat: String,
    val status: HistoryStatus,
    val outputName: String?,
    val durationMs: Long?,
    val error: String?,
)

interface HistoryPort {
    suspend fun append(record: HistoryRecord)
    suspend fun readAll(): List<HistoryRecord>
    suspend fun clear()
}