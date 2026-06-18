package com.openconverter.app.theme

import androidx.compose.ui.graphics.Color

// Spotify-inspired dark palette (spec decision #1, #2)
val BackgroundBase = Color(0xFF121212)   // page bg
val SurfaceCard = Color(0xFF181818)      // file cards
val SurfaceButton = Color(0xFF1F1F1F)    // buttons / chips
val SurfaceHighlight = Color(0xFF252527) // hover / selected
val Divider = Color(0xFF282828)          // hairlines

// Brand green (single accent, per decision #2)
val GreenBase = Color(0xFF1ED760)
val GreenDark = Color(0xFF1DB954)        // gradient end / pressed state

// Text
val TextPrimary = Color(0xFFFFFFFF)
val TextSecondary = Color(0xFFB3B3B3)   // meta / subtitles
val TextMuted = Color(0xFF6A6A6A)        // disabled

// Status
val StatusError = Color(0xFFE22134)      // failed badge
val StatusWarning = Color(0xFFFFA42B)    // pending / unknown
val StatusInfo = Color(0xFF2E77D0)       // info / link
