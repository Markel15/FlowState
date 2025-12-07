package com.markel.flowstate.core.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Esta es la clase principal de la base de datos.
 * Le dice a Room qué "Entidades" (tablas) debe conocer
 * y qué versión de la base de datos estamos usando.
 */
@Database(
    entities = [TaskEntity::class], // Aquí listamos todas nuestras tablas
    version = 1
)
abstract class FlowStateDatabase : RoomDatabase() {

    // Expone nuestro DAO para que el resto de la app pueda usarlo
    abstract val taskDao: TaskDao

    // Room usará esto para crear la instancia de la DB.
    // Lo definiremos en el módulo :app con Dagger/Hilt
    // o un simple Singleton por ahora.
    companion object {
        const val DATABASE_NAME = "flowstate_db"
    }
}