package com.markel.flowstate.core.designsystem.components

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow

/**
 * Draws [text] using **all the height it is given** and, when it does not fit,
 * places the ellipsis at the end of the last complete line that does.
 *
 * Use it inside a bounded box (e.g. a card with a fixed height) where the text
 * has to absorb the leftover space instead of either wasting it or being cut in
 * half by the card bounds.
 *
 * Why this exists instead of `Text(maxLines = …)`:
 *  - a fixed `maxLines` is a guess. The space available depends on how many
 *    lines the sibling content took, the width and the user's font scale, so any
 *    hardcoded number either wastes room or truncates text that would have fit;
 *  - `TextOverflow.Ellipsis` alone is not enough: Compose applies it when
 *    `maxLines` is exceeded, **not** when the text overflows the *height*. On
 *    height overflow it just clips, mid-line. That is why the second pass below
 *    re-measures with `maxLines` = number of fully visible lines.
 *
 * Cost: one extra composition/measure pass, and only while the text actually
 * overflows.
 */
@Composable
fun FillHeightText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
) {
    SubcomposeLayout(modifier = modifier) { constraints ->
        var measured: TextLayoutResult? = null

        fun measureWith(maxLines: Int) = subcompose(maxLines) {
            Text(
                text = text,
                style = style,
                color = color,
                maxLines = maxLines,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { measured = it },
            )
        }[0].measure(constraints)

        val first = measureWith(Int.MAX_VALUE)
        val textLayout = measured

        val placeable = if (textLayout != null && textLayout.didOverflowHeight) {
            // Last line whose bottom stays inside the box we were given.
            val lastVisible = (0 until textLayout.lineCount)
                .lastOrNull { textLayout.getLineBottom(it) <= textLayout.size.height }
                ?: 0
            measureWith(lastVisible + 1)
        } else {
            first
        }

        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }
}
