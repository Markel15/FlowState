package com.markel.flowstate

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Clase Application personalizada.
 * Se inicia una sola vez cuando arranca la app.
 * La usaremos para inicializar y mantener
 * la instancia única de nuestra Base de Datos y Repositorio.
 */
@HiltAndroidApp
class FlowStateApp : Application() {
    // Hilt genera el código necesario en segundo plano.
}