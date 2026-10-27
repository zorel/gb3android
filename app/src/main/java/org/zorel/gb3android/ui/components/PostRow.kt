package org.zorel.gb3android.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.zorel.gb3android.domain.MessageToken
import org.zorel.gb3android.ui.DisplayPost
import org.zorel.gb3android.ui.theme.LocalGb3Palette

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PostRow(
    index: Int,
    display: DisplayPost,
    selected: Boolean,
    selectedKey: String?,
    onSelect: () -> Unit,
    onReply: () -> Unit,
    onCallAuthor: () -> Unit,
    onActions: () -> Unit,
    onNorlogeClick: (MessageToken.Norloge) -> Unit,
    onUrlClick: (String) -> Unit,
    onTotozClick: (String) -> Unit,
) {
    val palette = LocalGb3Palette.current
    val colors = MaterialTheme.colorScheme
    val post = display.post
    val conversationColor = display.conversationColor?.let { palette.conversationColors[it] }
    val background = when {
        selected -> palette.selectedBackground
        display.hidden -> palette.filteredBackground
        index % 2 == 0 -> colors.surface
        else -> colors.background
    }
    var revealedSpoilers by remember(post.key) { mutableStateOf(setOf<Int>()) }

    // Web gc2 (desktop) and gb3tui keep the timestamp, author and message on the
    // same flow. Doing the same here is what makes a phone screen show a useful
    // amount of the tribune.
    val prefix = buildAnnotatedString {
        display.marker?.let { marker ->
            withStyle(
                SpanStyle(
                    color = conversationColor ?: colors.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                ),
            ) {
                append(marker)
                append(' ')
            }
        }
        when {
            display.isMine -> withStyle(SpanStyle(color = palette.mine)) { append("⭐ ") }
            display.isReply -> withStyle(SpanStyle(color = palette.reply)) { append("↩ ") }
            display.isBigorno -> withStyle(SpanStyle(color = palette.bigorno)) { append("📢 ") }
        }
        val clockStyle = if (selected) {
            SpanStyle(
                color = colors.onPrimary,
                background = colors.primary,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
            )
        } else {
            SpanStyle(
                color = if (display.hidden) palette.filteredText else palette.clock,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
            )
        }
        withLink(
            LinkAnnotation.Clickable(
                tag = "clock:${post.key}",
                styles = TextLinkStyles(style = clockStyle),
                linkInteractionListener = { onReply() },
            ),
        ) {
            append(post.clock)
        }
        append("  ")
        val authorStyle = SpanStyle(
            color = if (display.hidden) palette.filteredText else palette.name,
            fontWeight = FontWeight.Bold,
        )
        withLink(
            LinkAnnotation.Clickable(
                tag = "author:${post.key}",
                styles = TextLinkStyles(style = authorStyle),
                linkInteractionListener = { onCallAuthor() },
            ),
        ) {
            append(post.displayIdentity)
        }
        append("  ")
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .background(background)
            .combinedClickable(
                onClick = onSelect,
                onLongClick = onActions,
            ),
    ) {
        Box(
            modifier = Modifier
                .width(if (conversationColor != null) 2.dp else 1.dp)
                .fillMaxHeight()
                .background(conversationColor ?: Color.Transparent),
        )
        MessageText(
            parsed = display.parsed,
            fromPostKey = post.key,
            refTargets = display.refTargets,
            selectedKey = selectedKey,
            conversationColor = conversationColor,
            hidden = display.hidden,
            prefix = prefix,
            revealedSpoilers = revealedSpoilers,
            onNorlogeClick = { _, ref -> onNorlogeClick(ref) },
            onUrlClick = onUrlClick,
            onTotozClick = onTotozClick,
            onSpoilerToggle = { spoiler ->
                revealedSpoilers = if (spoiler in revealedSpoilers) {
                    revealedSpoilers - spoiler
                } else {
                    revealedSpoilers + spoiler
                }
            },
            modifier = Modifier
                .weight(1f)
                .padding(start = 7.dp, end = 10.dp, top = 2.dp, bottom = 3.dp),
        )
    }
}
