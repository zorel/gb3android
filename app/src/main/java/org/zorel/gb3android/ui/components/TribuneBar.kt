package org.zorel.gb3android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.zorel.gb3android.data.TribuneBadge
import org.zorel.gb3android.ui.theme.LocalGb3Palette

@Composable
fun TribuneBar(
    tribunes: List<String>,
    activeTribune: String?,
    badges: Map<String, TribuneBadge>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val palette = LocalGb3Palette.current
    val listState = rememberLazyListState()
    LaunchedEffect(activeTribune, tribunes) {
        val index = activeTribune?.let { tribunes.indexOf(it) } ?: -1
        if (index >= 0) listState.animateScrollToItem(index)
    }

    LazyRow(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 1.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(tribunes.size, key = { tribunes[it] }) { index ->
            val name = tribunes[index]
            val selected = name == activeTribune
            val badge = badges[name]
            Surface(
                onClick = { onSelect(name) },
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
                    Text(
                        text = name,
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
