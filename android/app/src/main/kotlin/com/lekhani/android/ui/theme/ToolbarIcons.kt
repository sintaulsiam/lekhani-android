package com.lekhani.android.ui.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Height
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.SentimentSatisfied
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VerticalSplit
import androidx.compose.ui.graphics.vector.ImageVector
import com.lekhani.android.data.settings.KeyboardPreferences.ToolbarTool

val ToolbarTool.iconVector: ImageVector
    get() = when (this) {
        ToolbarTool.EMOJI -> Icons.Filled.SentimentSatisfied
        ToolbarTool.TEXT_EDITOR -> Icons.Filled.OpenWith
        ToolbarTool.VOICE -> Icons.Filled.Mic
        ToolbarTool.CLIPBOARD -> Icons.Filled.ContentPaste
        ToolbarTool.DICTIONARY -> Icons.AutoMirrored.Filled.MenuBook
        ToolbarTool.NUMPAD -> Icons.Filled.Dialpad
        ToolbarTool.RESIZE -> Icons.Filled.Height
        ToolbarTool.THEME -> Icons.Filled.Palette
        ToolbarTool.ONE_HANDED -> Icons.Filled.PanTool
        ToolbarTool.FLOATING -> Icons.Filled.PictureInPictureAlt
        ToolbarTool.SPLIT -> Icons.Filled.VerticalSplit
        ToolbarTool.SETTINGS -> Icons.Filled.Settings
    }
