package com.markel.flowstate.core.data.di

import android.app.Application
import androidx.room.Room
import com.markel.flowstate.core.data.local.FlowStateDatabase
import com.markel.flowstate.core.data.local.TaskDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class) // Vivirá mientras la app viva
object DatabaseModule {

    @Provides
    @Singleton // Queremos una sola instancia de la DB
    fun provideFlowStateDatabase(app: Application): FlowStateDatabase {
        return Room.databaseBuilder(
            app,
            FlowStateDatabase::class.java,
            FlowStateDatabase.DATABASE_NAME
        ).build()
    }

    @Provides
    @Singleton // Una sola instancia del DAO
    fun provideTaskDao(db: FlowStateDatabase): TaskDao {
        return db.taskDao
    }
}