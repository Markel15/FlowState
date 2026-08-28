package com.markel.flowstate.navigation

import androidx.navigation3.runtime.NavKey
import com.markel.flowstate.core.data.MainTab
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import androidx.navigation3.runtime.NavMetadataKey
import androidx.savedstate.serialization.SavedStateConfiguration

// ─────────────────────────────────────────────────────────────────────────────
// NavKey hierarchy
//
// Two layers:
//   - TabKey: the five bottom-nav tabs. Each gets its own NavBackStack in
//     NavigationState. Rendered INSIDE the Scene Decorator (i.e. on top of
//     the bottom bar).
//   - FullScreenKey: detail/editors/settings sub-screens. Pushed onto the
//     current tab's back stack. Rendered OUTSIDE the Scene Decorator
//     (covering the bottom bar) thanks to the FullScreenMeta flag.
//
// The decorator decides which layer applies by reading the FullScreenMeta
// flag from each entry — see FlowStateSceneDecoratorStrategy.kt.
// ─────────────────────────────────────────────────────────────────────────────

/** Marker for the five bottom-nav tabs. They get decorated with the bottom bar. */
@Serializable
sealed interface TabKey : NavKey {
    @Serializable data object Tasks : TabKey
    @Serializable data object Calendar : TabKey
    @Serializable data object Habits : TabKey
    @Serializable data object Mood : TabKey
    @Serializable data object Settings : TabKey
}

/** Fullscreen destinations that must cover the bottom bar. */
@Serializable
sealed interface FullScreenKey : NavKey {
    @Serializable data class TaskEditor(val taskId: Int) : FullScreenKey
    @Serializable data class IdeaEditor(val ideaId: Int?, val categoryId: Int?) : FullScreenKey
    @Serializable data class CheckListEditor(val checkListId: Int?, val categoryId: Int?) : FullScreenKey
    @Serializable data class HabitDetail(val habitId: Int) : FullScreenKey

    @Serializable data object About : FullScreenKey
    @Serializable data object Appearance : FullScreenKey
    @Serializable data object BottomNavConfig : FullScreenKey
    @Serializable data object Integrations : FullScreenKey
    @Serializable data object Categories : FullScreenKey
    @Serializable data object Achievements : FullScreenKey
}

// ─────────────────────────────────────────────────────────────────────────────
// MainTab ↔ TabKey mapping
// ─────────────────────────────────────────────────────────────────────────────

fun MainTab.toKey(): TabKey = when (this) {
    MainTab.TASKS -> TabKey.Tasks
    MainTab.CALENDAR -> TabKey.Calendar
    MainTab.HABITS -> TabKey.Habits
    MainTab.MOOD -> TabKey.Mood
    MainTab.SETTINGS -> TabKey.Settings
}

fun MainTab.Companion.fromKey(key: NavKey): MainTab? = when (key) {
    is TabKey.Tasks -> MainTab.TASKS
    is TabKey.Calendar -> MainTab.CALENDAR
    is TabKey.Habits -> MainTab.HABITS
    is TabKey.Mood -> MainTab.MOOD
    is TabKey.Settings -> MainTab.SETTINGS
    else -> null
}

// ─────────────────────────────────────────────────────────────────────────────
// Metadata — FullScreen flag
//
// A SceneDecoratorStrategy wraps EVERY scene unconditionally. To opt specific
// entries out of the bottom-bar decorator, we tag them with `FullScreenMeta`.
// The decorator inspects each entry's metadata and returns the scene unwrapped
// if the flag is present.
//
// The `fullScreenVerticalSlide()` helper in TransitionMetadata.kt combines this
// flag with the slide-up transition. Use it for every fullscreen destination.
// ─────────────────────────────────────────────────────────────────────────────

object FullScreenMeta : NavMetadataKey<Boolean>