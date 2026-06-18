package com.openconverter.app.ui

/**
 * Typed route names for the 4-tab bottom nav plus the crash-log sub-flow.
 * Strings (not sealed classes) to keep the data model simple — these are
 * UI labels, not deep-link URIs.
 */
object NavRoutes {
    const val HOME = "home"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val ABOUT = "about"
    const val CRASHLOG = "crashlog"
    const val CRASHLOG_DETAIL = "crashlog/{fileName}"
    fun crashlogDetail(fileName: String) = "crashlog/$fileName"
}
