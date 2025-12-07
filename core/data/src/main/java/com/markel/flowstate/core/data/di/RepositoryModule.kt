package com.markel.flowstate.core.data.di

import com.markel.flowstate.core.data.TaskRepositoryImpl
import com.markel.flowstate.core.domain.TaskRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    // Hilt, cuando alguien pida un "TaskRepository" (interfaz),
    // entrégale una instancia de "TaskRepositoryImpl" (clase)
    abstract fun bindTaskRepository(
        taskRepositoryImpl: TaskRepositoryImpl
    ): TaskRepository
}