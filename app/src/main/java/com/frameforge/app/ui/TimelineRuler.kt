package com.frameforge.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.frameforge.app.data.TimelineClipEntity
import com.frameforge.app.ui.theme.Accent
import com.frameforge.app.ui.theme.Muted

/**
 * Lightweight timeline ruler. It derives the visible scale from saved clip offsets
 * and durations; it is intentionally read-only and does not alter timeline data.
 */
@Composable
fun TimelineRuler(clips: List<TimelineClipEntity>) {
    val maxEndMs = clips.maxOfOrNull { clip ->
        val end = clip.trimEndMs ?: clip.durationMs
        clip.timelineStartMs + (end - clip.trimStartMs).coerceAtLeast(0L)
    } ?: 0L
    val totalSeconds = ((maxEndMs + 999L) / 1000L).toInt().coerceIn(10, 600)
    val scroll = rememberScrollState()

    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text("TIMELINE • 1 SEC TICKS", color = Muted, style = MaterialTheme.typography.labelSmall)
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(scroll),
            verticalAlignment = Alignment.Bottom
        ) {
            (0..totalSeconds).forEach { second ->
                Column(
                    modifier = Modifier.width(44.dp),
                    horizontalAlignment = Alignment.Start,
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    if (second % 5 == 0) {
                        Text(
                            text = "%02d:%02d".format(second / 60, second % 60),
                            color = Muted,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1
                        )
                    } else {
                        Spacer(Modifier.height(14.dp))
                    }
                    Box(
                        Modifier
                            .width(if (second % 5 == 0) 2.dp else 1.dp)
                            .height(if (second % 5 == 0) 12.dp else 6.dp)
                            .background(if (second % 5 == 0) Accent else Muted.copy(alpha = .45f))
                    )
                }
            }
        }
        Box(
            Modifier.fillMaxWidth().height(4.dp)
                .background(Accent.copy(alpha = .12f), RoundedCornerShape(4.dp))
        )
    }
}
