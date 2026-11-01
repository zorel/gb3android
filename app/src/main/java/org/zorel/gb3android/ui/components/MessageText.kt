package org.zorel.gb3android.ui.components

import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import org.zorel.gb3android.domain.InlineFormat
import org.zorel.gb3android.domain.MessageToken
import org.zorel.gb3android.domain.ParsedMessage
import org.zorel.gb3android.ui.theme.LocalGb3Palette

@Composable
fun MessageText(
    parsed: ParsedMessage,
    fromPostKey: String,
    refTargets: List<String?>,
    selectedKey: String?,
    conversationColor: Color?,
    hidden: Boolean,
    prefix: AnnotatedString? = null,
    revealedSpoilers: Set<Int>,
    onNorlogeClick: (String, MessageToken.Norloge) -> Unit,
    onUrlClick: (String) -> Unit,
    onTotozClick: (String, Offset) -> Unit,
    onSpoilerToggle: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalGb3Palette.current
    val colors = MaterialTheme.colorScheme
    val currentNorlogeClick by rememberUpdatedState(onNorlogeClick)
    val currentUrlClick by rememberUpdatedState(onUrlClick)
    val currentTotozClick by rememberUpdatedState(onTotozClick)
    val currentSpoilerToggle by rememberUpdatedState(onSpoilerToggle)
    // Only read from the click listener: the origin changes on every scroll,
    // and observing it here would recompose each post while the list moves.
    val textLayout = remember { mutableStateOf<TextLayoutResult?>(null) }
    val textOrigin = remember { mutableStateOf(Offset.Zero) }

    val referenceColor = conversationColor ?: palette.clock
    val annotated = remember(
        parsed,
        refTargets,
        selectedKey,
        conversationColor,
        hidden,
        prefix,
        revealedSpoilers,
        referenceColor,
        palette,
        colors.surfaceVariant,
    ) {
        buildAnnotatedString {
            prefix?.let { append(it) }
            for (token in parsed.tokens) {
                when (token) {
                    is MessageToken.Text -> {
                        if (token.text.isEmpty()) continue
                        val start = length
                        withStyle(spanStyleFor(token.format, colors.surfaceVariant)) {
                            append(token.text)
                        }
                        token.spoiler?.let { spoiler ->
                            val revealed = spoiler in revealedSpoilers
                            val spoilerStyle = if (revealed) {
                                SpanStyle(
                                    color = palette.spoilerRevealed,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            } else {
                                SpanStyle(
                                    color = palette.spoilerBackground,
                                    background = palette.spoilerBackground,
                                )
                            }
                            addStyle(spoilerStyle, start, length)
                            addLink(
                                LinkAnnotation.Clickable(
                                    tag = "spoiler:$spoiler",
                                    styles = TextLinkStyles(),
                                    linkInteractionListener = {
                                        currentSpoilerToggle(spoiler)
                                    },
                                ),
                                start,
                                length,
                            )
                        }
                    }

                    is MessageToken.Link -> {
                        val style = SpanStyle(
                            color = palette.url,
                            fontWeight = FontWeight.SemiBold,
                            textDecoration = TextDecoration.Underline,
                        )
                        withLink(
                            LinkAnnotation.Clickable(
                                tag = "url:${token.href}",
                                styles = TextLinkStyles(style = style),
                                linkInteractionListener = {
                                    currentUrlClick(token.href)
                                },
                            ),
                        ) {
                            append(token.label)
                        }
                    }

                    is MessageToken.Norloge -> {
                        val highlighted = refTargets.getOrNull(token.index) == selectedKey
                        val style = if (highlighted) {
                            SpanStyle(
                                color = palette.highlightText,
                                background = palette.highlight,
                                fontWeight = FontWeight.Bold,
                            )
                        } else {
                            SpanStyle(
                                color = referenceColor,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        withLink(
                            LinkAnnotation.Clickable(
                                tag = "norloge:${token.index}",
                                styles = TextLinkStyles(style = style),
                                linkInteractionListener = {
                                    currentNorlogeClick(fromPostKey, token)
                                },
                            ),
                        ) {
                            append(token.displayText)
                        }
                    }

                    is MessageToken.Totoz -> {
                        val style = SpanStyle(
                            color = palette.totoz,
                            fontWeight = FontWeight.Bold,
                        )
                        val label = "[:${token.name}]"
                        val start = length
                        val end = start + label.length
                        withLink(
                            LinkAnnotation.Clickable(
                                tag = "totoz:$start:${token.name}",
                                styles = TextLinkStyles(style = style),
                                linkInteractionListener = {
                                    val anchor = textLayout.value?.totozAnchor(
                                        start = start,
                                        end = end,
                                        origin = textOrigin.value,
                                    ) ?: Offset.Unspecified
                                    currentTotozClick(token.name, anchor)
                                },
                            ),
                        ) {
                            append(label)
                        }
                    }

                    is MessageToken.Bigorno -> {
                        withStyle(
                            SpanStyle(
                                color = palette.bigorno,
                                fontWeight = FontWeight.Bold,
                            ),
                        ) {
                            append(token.word)
                            append("📢")
                        }
                    }
                }
            }
        }
    }

    Text(
        text = annotated,
        modifier = modifier.onGloballyPositioned { coordinates ->
            textOrigin.value = coordinates.positionInRoot()
        },
        onTextLayout = { textLayout.value = it },
        style = MaterialTheme.typography.bodyMedium.copy(
            lineHeight = 19.sp,
            color = if (hidden) palette.filteredText else colors.onSurface,
        ),
    )
}

/**
 * Top-center of the totoz text in root coordinates, mirroring roinroin's
 * hover preview anchor so the image can sit just above the characters.
 */
private fun TextLayoutResult.totozAnchor(start: Int, end: Int, origin: Offset): Offset {
    val length = layoutInput.text.length
    if (length == 0) return Offset.Unspecified
    val first = start.coerceIn(0, length - 1)
    val last = (end - 1).coerceIn(first, length - 1)
    var left = Float.MAX_VALUE
    var right = -Float.MAX_VALUE
    var top = Float.MAX_VALUE
    for (offset in first..last) {
        val box = getBoundingBox(offset)
        left = minOf(left, box.left)
        right = maxOf(right, box.right)
        top = minOf(top, box.top)
    }
    return origin + Offset((left + right) / 2f, top)
}

private fun spanStyleFor(format: InlineFormat, surfaceVariant: Color): SpanStyle {
    val decorations = buildList {
        if (format.underline) add(TextDecoration.Underline)
        if (format.strike) add(TextDecoration.LineThrough)
    }
    return SpanStyle(
        fontWeight = if (format.bold) FontWeight.Bold else null,
        fontStyle = if (format.italic) FontStyle.Italic else null,
        textDecoration = when (decorations.size) {
            0 -> null
            1 -> decorations.first()
            else -> TextDecoration.combine(decorations)
        },
        fontFamily = if (format.monospace) FontFamily.Monospace else null,
        background = if (format.monospace) surfaceVariant.copy(alpha = 0.65f) else Color.Unspecified,
    )
}
