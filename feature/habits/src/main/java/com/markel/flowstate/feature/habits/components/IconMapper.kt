package com.markel.flowstate.feature.habits.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import com.markel.flowstate.core.designsystem.icon.HabitIconMapper

/**
 * Convenience aliases that delegate to the shared [HabitIconMapper]
 * in `core:designsystem`.
 *
 * Kept as top-level vals/funs so existing call-sites in the habits
 * feature module don't need any import changes.
 */

/** Full list of icon entries — used by [AddHabitSheet] icon picker. */
val HabitIconList: List<Pair<String, Int?>> = HabitIconMapper.iconList

/**
 * Composable helper that resolves an icon name to an [ImageVector].
 * Returns `null` when [iconName] is `"none"`, and falls back to
 * [HabitIconMapper.fallbackIconRes] for unknown names.
 */
@Composable
fun getHabitIcon(iconName: String): ImageVector? {
    if (iconName == "none") return null
    val resId = HabitIconMapper.getDrawableRes(iconName) ?: HabitIconMapper.fallbackIconRes
    return ImageVector.vectorResource(resId)
}
