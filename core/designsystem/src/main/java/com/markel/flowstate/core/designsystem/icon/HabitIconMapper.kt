package com.markel.flowstate.core.designsystem.icon

import androidx.annotation.DrawableRes
import com.markel.flowstate.core.designsystem.R

/**
 * Centralized mapping from habit icon name strings (stored in DB)
 * to drawable resource IDs.
 *
 * Shared between [feature/habits] and [core/widgets] so that both
 * modules resolve icons from a single source of truth.
 *
 * - **Glance / widgets**: use [getDrawableRes] — returns a plain `Int?`
 *   with no Compose dependencies.
 * - **Compose screens**: use [iconList] or the `getHabitIcon()` composable
 *   wrapper provided by each feature module.
 */
object HabitIconMapper {

    /** Full list of icon entries (name -> drawable resId, or null for "none"). */
    val iconList: List<Pair<String, Int?>> = listOf(
        "none" to null,
        "self_improvement" to R.drawable.self_improvement_24px,
        "fitness_center" to R.drawable.fitness_center_24px,
        "directions_run" to R.drawable.directions_run_24px,
        "directions_bike" to R.drawable.directions_bike_24px,
        "book" to R.drawable.book_ribbon_24px,
        "bedtime" to R.drawable.bedtime_24px,
        "shower" to R.drawable.shower_24px,
        "cleaning" to R.drawable.cleaning_24px,
        "dentistry" to R.drawable.dentistry_24px,
        "language" to R.drawable.language_chinese_dayi_24px,
        "laundry" to R.drawable.local_laundry_service_24px,
        "nutrition" to R.drawable.nutrition_24px,
        "recycling" to R.drawable.recycling_24px,
        "shopping" to R.drawable.shopping_basket_24px,
        "water" to R.drawable.water_drop_24px,
        "assignment" to R.drawable.assignment_24px,
        "pets" to R.drawable.pets_24px,
        "washoku" to R.drawable.washoku_24px,
        "pool" to R.drawable.pool_24px,
        "hiking" to R.drawable.hiking_24px,
        "pill" to R.drawable.pill_24px,
        "nature" to R.drawable.nature_24px
    )

    private val iconMap: Map<String, Int?> = iconList.toMap()

    /**
     * Returns the drawable resource ID for the given icon name.
     * Returns `null` if the icon name is `"none"` or not found.
     *
     * Glance/widget-safe — no Compose dependencies required.
     */
    @DrawableRes
    fun getDrawableRes(iconName: String): Int? = when (iconName) {
        "none" -> null
        else -> iconMap[iconName]
    }

    /** Fallback icon resource for unknown icon names. */
    @get:DrawableRes
    val fallbackIconRes: Int = R.drawable.asterisk_24px
}
