package org.zorel.gb3android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.zorel.gb3android.R
import org.zorel.gb3android.data.SourceId
import org.zorel.gb3android.data.SourceStatus
import org.zorel.gb3android.data.TribuneBadge
import org.zorel.gb3android.data.TribuneKey
import org.zorel.gb3android.ui.TribuneGroup
import org.zorel.gb3android.ui.theme.LocalGb3Palette

private sealed interface TribuneBarItem {
    data class Header(val group: TribuneGroup) : TribuneBarItem
    data class Chip(val tribune: TribuneKey) : TribuneBarItem
}

@Composable
fun TribuneBar(
    groups: List<TribuneGroup>,
    activeKey: String?,
    badges: Map<String, TribuneBadge>,
    gb2cStatus: Map<String, SourceStatus>,
    boardStatus: Map<String, SourceStatus>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val palette = LocalGb3Palette.current
    val listState = rememberLazyListState()
    val items = buildList {
        for (group in groups) {
            add(TribuneBarItem.Header(group))
            for (tribune in group.tribunes) {
                add(TribuneBarItem.Chip(tribune))
            }
        }
    }

    LaunchedEffect(activeKey, items) {
        val index = items.indexOfFirst { item ->
            item is TribuneBarItem.Chip && item.tribune.key == activeKey
        }
        if (index >= 0) listState.animateScrollToItem(index)
    }

    LazyRow(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 1.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(
            items = items,
            key = { item ->
                when (item) {
                    is TribuneBarItem.Header -> "header:${item.group.source.key}"
                    is TribuneBarItem.Chip -> "chip:${item.tribune.key}"
                }
            },
        ) { item ->
            when (item) {
                is TribuneBarItem.Header -> {
                    val status = (item.group.source as? SourceId.Gb2c)?.let { source ->
                        gb2cStatus[source.url]
                    }
                    val label = when (item.group.source) {
                        is SourceId.Gb2c -> stringResource(R.string.source_gb2c, item.group.labelName)
                        is SourceId.Direct -> stringResource(R.string.source_board)
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(start = 4.dp, end = 2.dp),
                    ) {
                        if (status != null) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(statusColor(status, colors.secondary, colors.tertiary, colors.error)),
                            )
                        }
                        Text(
                            text = label.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.muted,
                            maxLines = 1,
                        )
                    }
                }

                is TribuneBarItem.Chip -> {
                    val tribune = item.tribune
                    val selected = tribune.key == activeKey
                    val badge = badges[tribune.key]
                    Surface(
                        onClick = { onSelect(tribune.key) },
                        shape = RoundedCornerShape(50),
                        color = if (selected) colors.primary else colors.surfaceVariant,
                        contentColor = if (selected) colors.onPrimary else colors.onSurfaceVariant,
                        modifier = Modifier.heightIn(min = 38.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            val boardSource = tribune.source as? SourceId.Direct
                            val boardState = boardSource?.let { boardStatus[it.board] }
                            if (boardState != null) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(
                                            statusColor(
                                                boardState,
                                                colors.secondary,
                                                colors.tertiary,
                                                colors.error,
                                            ),
                                        ),
                                )
                            }
                            Text(
                                text = tribune.name,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (!selected && badge != null) {
                                if (badge.hasBigorno) {
                                    Text("📢", fontSize = 12.sp, color = palette.bigorno)
                                }
                                if (badge.hasReply) {
                                    Text("↩", fontSize = 12.sp, color = palette.reply)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun statusColor(
    status: SourceStatus,
    online: Color,
    working: Color,
    error: Color,
): Color = when (status) {
    SourceStatus.Online -> online
    SourceStatus.Working -> working
    SourceStatus.Error -> error
}
