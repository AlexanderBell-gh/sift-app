package com.sift.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

/**
 * Local store mark. Local asset wins; when missing, render nothing
 * (inherited `store_logo` fallback needs an image loader — TODO Coil).
 * M&S is black-on-transparent: seat it on a light chip under dark theme,
 * same as web (`.dark img[src*="mands.png"]`).
 */
@Composable
fun StoreMark(
    assetName: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val resId = remember(assetName) {
        context.resources.getIdentifier(assetName, "drawable", context.packageName)
    }
    if (resId == 0) return
    if (assetName == "mands" && isSystemInDarkTheme()) {
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = Color.White,
            modifier = modifier,
        ) {
            Image(
                painter = painterResource(resId),
                contentDescription = contentDescription,
                modifier = Modifier.padding(2.dp),
            )
        }
    } else {
        Image(
            painter = painterResource(resId),
            contentDescription = contentDescription,
            modifier = modifier,
        )
    }
}
