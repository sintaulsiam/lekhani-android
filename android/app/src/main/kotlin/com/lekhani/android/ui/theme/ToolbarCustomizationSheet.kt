package com.lekhani.android.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lekhani.android.data.settings.KeyboardPreferences

/**
 * ToolbarCustomizationSheet
 * ══════════════════════════════════════════════════════════════════════════════
 * Settings sheet for customizing and reordering top toolbar shortcut tools.
 */
@Composable
fun ToolbarCustomizationSheet(
    prefs: KeyboardPreferences,
    onClose: () -> Unit,
) {
    var activeTools by remember { mutableStateOf(prefs.getActiveToolbarTools()) }

    val allTools = KeyboardPreferences.ToolbarTool.values().toList()

    fun moveTool(fromIdx: Int, toIdx: Int) {
        if (fromIdx in activeTools.indices && toIdx in activeTools.indices) {
            val list = activeTools.toMutableList()
            val item = list.removeAt(fromIdx)
            list.add(toIdx, item)
            activeTools = list
            prefs.setToolbarToolsList(list)
        }
    }

    fun toggleTool(tool: KeyboardPreferences.ToolbarTool) {
        val list = activeTools.toMutableList()
        if (list.contains(tool)) {
            if (list.size > 1) { // keep at least 1 tool
                list.remove(tool)
            }
        } else {
            list.add(tool)
        }
        activeTools = list
        prefs.setToolbarToolsList(list)
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "ফিরে যান",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "টুলবার কাস্টমাইজেশন",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "কীবোর্ড স্ট্রিপের শর্টকাট টুল সক্রিয় ও সাজান",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text(
                        text = "টুলবারের টুল ও ক্রমবিন্যাস",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                itemsIndexed(allTools) { _, tool ->
                    val isEnabled = activeTools.contains(tool)
                    val activeIndex = activeTools.indexOf(tool)

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(
                                alpha = if (isEnabled) 0.6f else 0.25f
                            )
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = isEnabled,
                                    onCheckedChange = { toggleTool(tool) }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = tool.iconRes,
                                    fontSize = 24.sp
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = tool.titleBengali,
                                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                                    )
                                    Text(
                                        text = tool.name,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            if (isEnabled) {
                                Row {
                                    IconButton(
                                        onClick = { moveTool(activeIndex, activeIndex - 1) },
                                        enabled = activeIndex > 0
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowUp,
                                            contentDescription = "উপরে নিন"
                                        )
                                    }
                                    IconButton(
                                        onClick = { moveTool(activeIndex, activeIndex + 1) },
                                        enabled = activeIndex < activeTools.lastIndex
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowDown,
                                            contentDescription = "নিচে নিন"
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = {
                            activeTools = KeyboardPreferences.DEFAULT_TOOL_LIST
                            prefs.setToolbarToolsList(KeyboardPreferences.DEFAULT_TOOL_LIST)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("ডিফল্ট টুলবারে রিসেট করুন")
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onClose,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("সম্পন্ন", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
