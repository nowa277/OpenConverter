package com.openconverter.app.ui

/**
 * Typed route names for the 4-tab bottom nav. Strings (not sealed classes)
 * to keep the data model simple — these are UI labels, not deep-link URIs.
 *
 * 4 tabs: Home / History / Settings / About (spec decision #10)
 */
object NavRoutes {
    const val HOME = "home"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val ABOUT = "about"
}
