package com.markel.flowstate.core.widgets

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.glance.ColorFilter
import androidx.glance.LocalSize
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.currentState
import androidx.glance.layout.ContentScale
import androidx.glance.text.TextAlign
import dagger.hilt.android.EntryPointAccessors
import java.time.LocalDate
import com.markel.flowstate.core.designsystem.icon.HabitIconMapper
import com.markel.flowstate.core.domain.Habit
import com.markel.flowstate.core.domain.HabitRepository
import com.markel.flowstate.core.domain.isScheduledFor
import kotlinx.coroutines.flow.first

// Key preferences for the widget
val KEY_HABIT_ID = intPreferencesKey("habit_id")

class HabitWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Preload data BEFORE provideContent so the first compose frame
        // has real data and never shows the "?" placeholder.
        // On some devices (e.g. Android 12) getAppWidgetState may return
        // empty preferences if the DataStore hasn't been initialized yet
        // (process restart after being killed).  The reactive currentState()
        // call inside provideContent acts as a safety-net: if the preloaded
        // habitId is -1 but the real preferences arrive later, the widget
        // recomposes with the correct habitId automatically.
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            HabitWidgetEntryPoint::class.java
        )
        val repository = entryPoint.habitRepository()

        val prefs = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)
        val preloadedHabitId = prefs[KEY_HABIT_ID] ?: -1

        val initialHabit: Habit? = if (preloadedHabitId != -1) repository.getHabitById(preloadedHabitId) else null
        val initialEntries: List<LocalDate> = if (preloadedHabitId != -1) repository.getEntriesForHabit(preloadedHabitId).first() else emptyList()

        provideContent {
            GlanceTheme {
                HabitWidgetContent(preloadedHabitId, initialHabit, initialEntries, repository)
            }
        }
    }

    @Composable
    private fun HabitWidgetContent(
        preloadedHabitId: Int,
        initialHabit: Habit?,
        initialEntries: List<LocalDate>,
        repository: HabitRepository
    ) {
        val prefs = currentState<Preferences>()  // Observe preferences reactively
        val habitId = prefs[KEY_HABIT_ID] ?: preloadedHabitId

        if (habitId == -1) {
            PlaceholderContent()
            return
        }

        // Use preloaded data as initial values to avoid flickering with the placeholder
        // The Flow will emit fresh data shortly after, updating the composition.
        val habit by repository.getHabitFlow(habitId)
            .collectAsState(initial = initialHabit)
        val entries by repository.getEntriesForHabit(habitId)
            .collectAsState(initial = initialEntries)

        // If the habit was deleted show placeholder
        val currentHabit = habit
        if (currentHabit == null) {
            PlaceholderContent()
            return
        }

        val today = LocalDate.now()
        val isCompleted = entries.contains(today)
        val isScheduledToday = currentHabit.isScheduledFor(today)

        // Render the widget subscribed to the flow data
        WidgetPill(
            habitName = currentHabit.name,
            iconName = currentHabit.iconName,
            isCompleted = isCompleted,
            isScheduledToday = isScheduledToday,
            dayNumber = today.dayOfMonth
        )
    }

    @Composable
    private fun PlaceholderContent() {
        Box(
            modifier = GlanceModifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "?",
                style = TextStyle(
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = GlanceTheme.colors.onPrimaryContainer
                )
            )
        }
    }

    @Composable
    private fun WidgetPill(
        habitName: String,
        iconName: String,
        isCompleted: Boolean,
        isScheduledToday: Boolean,
        dayNumber: Int
    ) {
        // On rest days (habit not scheduled today) the pill switches to the
        // muted surfaceVariant color role, so it reads as "nothing to do
        // today" at a glance in both light and dark dynamic themes
        val backgColor =
            if (isScheduledToday) GlanceTheme.colors.primaryContainer
            else GlanceTheme.colors.surfaceVariant
        val backgIconColor =
            if (isScheduledToday) GlanceTheme.colors.onPrimaryContainer
            else GlanceTheme.colors.onSurfaceVariant

        val badgeColor =
            if (isCompleted) GlanceTheme.colors.primary
            else GlanceTheme.colors.primaryContainer
        val badgeTextColor = when {
            isCompleted -> GlanceTheme.colors.onPrimary
            !isScheduledToday -> GlanceTheme.colors.onSurfaceVariant
            else -> GlanceTheme.colors.onPrimaryContainer
        }

        val size = LocalSize.current
        val widgetSize = if (size.width < size.height) size.width else size.height

        val badgeBoxSize = (widgetSize.value * 0.35f).dp
        val badgeTextSize = (widgetSize.value * 0.17f).sp
        val iconSize = (widgetSize.value * 0.29f).dp
        val iconTextSize = (widgetSize.value * 0.18f).sp

        val topPad = (widgetSize.value * 0.15f).dp
        val endPad = (widgetSize.value * 0.12f).dp
        val bottomPad = (widgetSize.value * 0.20f).dp
        val startPad = (widgetSize.value * 0.12f).dp

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .clickable(actionRunCallback<ToggleHabitAction>(), R.drawable.no_ripple),
            contentAlignment = Alignment.Center
        ) {
            Box(modifier = GlanceModifier.size(widgetSize)) {
                // pill tinted with theme color
                Image(
                    provider = ImageProvider(R.drawable.pill),
                    contentDescription = null,
                    modifier = GlanceModifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                    colorFilter = ColorFilter.tint(
                        backgColor
                    )
                )

                // Day in the top right and badge if completed
                Box(
                    modifier = GlanceModifier.fillMaxSize().padding(top = topPad, end = endPad),
                    contentAlignment = Alignment.TopEnd
                ) {
                    Box(
                        modifier = GlanceModifier.size(badgeBoxSize),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isCompleted) {
                            Image(
                                provider = ImageProvider(
                                    R.drawable.widget_badge_done
                                ),
                                contentDescription = "Completed",
                                modifier = GlanceModifier.fillMaxSize(),
                                colorFilter = ColorFilter.tint(badgeColor)
                            )
                        }
                        Text(
                            text = dayNumber.toString(),
                            style = TextStyle(
                                fontSize = badgeTextSize,
                                fontWeight = FontWeight.Bold,
                                color = badgeTextColor,
                                textAlign = TextAlign.Center
                            )
                        )
                    }
                }

                // Icon bottom left
                Box(
                    modifier = GlanceModifier.fillMaxSize().padding(bottom = bottomPad, start = startPad),
                    contentAlignment = Alignment.BottomStart
                ) {
                    val iconRes = HabitIconMapper.getDrawableRes(iconName)
                    if (iconRes != null) {
                        Image(
                            provider = ImageProvider(iconRes),
                            contentDescription = habitName,
                            modifier = GlanceModifier.size(iconSize),
                            colorFilter = ColorFilter.tint(backgIconColor)
                        )
                    } else {
                        Text(
                            text = habitName.take(2).uppercase(),
                            style = TextStyle(
                                fontSize = iconTextSize,
                                fontWeight = FontWeight.Bold,
                                color = backgIconColor
                            )
                        )
                    }
                }
            }
        }
    }
}
