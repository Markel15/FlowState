package com.markel.flowstate

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Custom Application class.
 * It starts only once when the app launches.
 * We will use it to initialize and maintain
 * the unique instance of our Database and Repository.
 */
@HiltAndroidApp
class FlowStateApp : Application() {
    // Hilt generates the necessary code in the background.
}