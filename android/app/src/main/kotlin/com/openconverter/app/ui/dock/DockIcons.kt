package com.openconverter.app.ui.dock

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Same four destinations as the desktop sidebar. Strokes are a little
 * heavier and round-capped so they stay readable at dock size.
 */
object DockIcons {
    val convert: ImageVector = strokeIcon(
        "Convert",
        "M17,1 L21,5 L17,9",
        "M3,11 V9 A4,4 0 0 1 7,5 H21",
        "M7,23 L3,19 L7,15",
        "M21,13 V15 A4,4 0 0 1 17,19 H3",
    )
    val history: ImageVector = strokeIcon(
        "History",
        "M12,2 A10,10 0 1 0 12,22 A10,10 0 1 0 12,2",
        "M12,6 V12 L16,14",
    )
    val settings: ImageVector = strokeIcon(
        "Settings",
        "M12,12 m-3,0 a3,3 0 1,0 6,0 a3,3 0 1,0 -6,0",
        "M19.4,15 a1.65,1.65 0 0 0 0.33,1.82 l0.06,0.06 a2,2 0 0 1 -2.83,2.83 l-0.06,-0.06 a1.65,1.65 0 0 0 -1.82,-0.33 a1.65,1.65 0 0 0 -1,1.51 V21 a2,2 0 0 1 -4,0 v-0.09 A1.65,1.65 0 0 0 9,19.4 a1.65,1.65 0 0 0 -1.82,0.33 l-0.06,0.06 a2,2 0 0 1 -2.83,-2.83 l0.06,-0.06 a1.65,1.65 0 0 0 0.33,-1.82 a1.65,1.65 0 0 0 -1.51,-1 H3 a2,2 0 0 1 0,-4 h0.09 A1.65,1.65 0 0 0 4.6,9 a1.65,1.65 0 0 0 -0.33,-1.82 l-0.06,-0.06 a2,2 0 0 1 2.83,-2.83 l0.06,0.06 a1.65,1.65 0 0 0 1.82,0.33 H9 a1.65,1.65 0 0 0 1,-1.51 V3 a2,2 0 0 1 4,0 v0.09 a1.65,1.65 0 0 0 1,1.51 a1.65,1.65 0 0 0 1.82,-0.33 l0.06,-0.06 a2,2 0 0 1 2.83,2.83 l-0.06,0.06 a1.65,1.65 0 0 0 -0.33,1.82 V9 a1.65,1.65 0 0 0 1.51,1 H21 a2,2 0 0 1 0,4 h-0.09 a1.65,1.65 0 0 0 -1.51,1 z",
    )
    val about: ImageVector = strokeIcon(
        "About",
        "M12,2 A10,10 0 1 0 12,22 A10,10 0 1 0 12,2",
        "M12,16 V12",
        "M12,8 L12.01,8",
    )

    private fun strokeIcon(name: String, vararg paths: String): ImageVector {
        val builder = ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        )
        val stroke = SolidColor(Color.Black)
        for (d in paths) {
            builder.addPath(
                pathData = PathParser().parsePathString(d).toNodes(),
                stroke = stroke,
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
        return builder.build()
    }
}
