package com.markel.flowstate.core.widgets

import android.content.Context
import android.content.Intent
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.markel.flowstate.core.domain.isScheduledFor
import dagger.hilt.android.EntryPointAccessors
import java.time.LocalDate

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
        if (!habit.isScheduledFor(today)) return

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
}
