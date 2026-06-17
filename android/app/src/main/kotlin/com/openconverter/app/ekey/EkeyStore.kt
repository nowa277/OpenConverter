package com.openconverter.app.ekey

import android.content.Context
import android.content.SharedPreferences

/**
 * Persists the QQ Music v2 ekey. Plaintext storage is acceptable for v0.2.2
 * (per spec §18.3 — encryption is a future enhancement).
 *
 * The ekey is a base64 string extracted from the QQ Music client's local DB.
 * Required for decrypting QMCv2 files (MFLAC, MGG, BKC*).
 */
class EkeyStore(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getEkey(): String? = prefs.getString(KEY_EKEY, null)

    fun setEkey(ekey: String) {
        prefs.edit().putString(KEY_EKEY, ekey.trim()).apply()
    }

    fun clearEkey() {
        prefs.edit().remove(KEY_EKEY).apply()
    }

    companion object {
        private const val PREFS_NAME = "openconverter"
        private const val KEY_EKEY = "qmc_v2_ekey"
    }
}
