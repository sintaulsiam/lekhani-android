package com.lekhani.android.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lekhani.android.R

@Composable
fun LekhaniBrandLogo(
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    shapeCornerPercent: Int = 22
) {
    Image(
        painter = painterResource(id = R.drawable.ic_lekhani_logo),
        contentDescription = "Lekhani Logo",
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(shapeCornerPercent))
    )
}
