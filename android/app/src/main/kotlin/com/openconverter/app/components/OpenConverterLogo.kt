package com.openconverter.app.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.openconverter.app.R

/**
 * OpenConverter brand mark. Rendered from a Vector Drawable so it scales
 * crisply on any DPI without bitmap bloat.
 *
 * Default size is 28dp (TopAppBar brand-mark); pass [size] = 120.dp on
 * the About screen for the hero.
 */
@Composable
fun OpenConverterLogo(size: Dp = 28.dp, contentDescription: String? = "OpenConverter") {
    Image(
        painter = painterResource(id = R.drawable.ic_logo),
        contentDescription = contentDescription,
        modifier = Modifier.size(size),
    )
}
