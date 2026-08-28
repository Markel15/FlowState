package com.markel.flowstate.core.widgets

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.markel.flowstate.core.domain.Habit
import com.markel.flowstate.core.domain.isScheduledFor
import com.markel.flowstate.core.domain.nextScheduledDate
import dagger.hilt.android.EntryPointAccessors
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ToggleHabitAction : ActionCallback {

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        // Read habitId and habitType from the state of the widget
        val prefs = getAppWidgetState(
            context, PreferencesGlanceStateDefinition, glanceId
        )
        val habitId = prefs[KEY_HABIT_ID] ?: return

        // Inject repository
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            HabitWidgetEntryPoint::class.java
        )
        val repository = entryPoint.habitRepository()
        val habit = repository.getHabitById(habitId) ?: return
        val today = LocalDate.now()

        // Rest day: tapping cannot toggle anything, so instead of failing
        // silently tell the user the habit is off today and when it returns
        if (!habit.isScheduledFor(today)) {
            showRestDayToast(context, habit, today)
            return
        }

        // For the numeric habits (should be impossible, but in any case) open the app
        if (habit.habitType.name == "NUMERIC") {
            val launchIntent = context.packageManager
                .getLaunchIntentForPackage(context.packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
            }
            return
        }
        // Boolean habit : Toggle and persist in the DB
        repository.toggleEntry(habitId, today)

        // Force redraw of the widget
        HabitWidget().update(context, glanceId)
    }

    private suspend fun showRestDayToast(context: Context, habit: Habit, today: LocalDate) {
        val message = when (val next = habit.nextScheduledDate(today)) {
            null ->
                // Defensive fallback for an empty schedule (impossible by
                // domain invariants today, but never hang on a toast)
                context.getString(R.string.widget_rest_day_toast_no_next, habit.name)

            else -> {
                val nextDayName = next.dayOfWeek
                    .getDisplayName(TextStyle.FULL, Locale.getDefault())
                    .replaceFirstChar { it.uppercase(Locale.getDefault()) }
                context.getString(R.string.widget_rest_day_toast, habit.name, nextDayName)
            }
        }

        // Toasts need a thread with a Looper: Glance runs action callbacks
        // off the main thread, and a failing Toast is swallowed silently
        withContext(Dispatchers.Main) {
            Toast.makeText(context.applicationContext, message, Toast.LENGTH_SHORT).show()
        }
    }
}
