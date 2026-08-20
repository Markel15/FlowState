package com.markel.flowstate.core.data.di

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.markel.flowstate.core.data.UserPreferencesRepository
import com.markel.flowstate.core.data.local.CategoryDao
import com.markel.flowstate.core.data.local.CheckListDao
import com.markel.flowstate.core.data.local.FlowStateDatabase
import com.markel.flowstate.core.data.local.HabitDao
import com.markel.flowstate.core.data.local.IdeaDao
import com.markel.flowstate.core.data.local.TaskDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class) // It will live as long as the app lives
object DatabaseModule {

    @Provides
    @Singleton // We want a single instance of the DB
    fun provideFlowStateDatabase(app: Application): FlowStateDatabase {
        return Room.databaseBuilder(
            app,
            FlowStateDatabase::class.java,
            FlowStateDatabase.DATABASE_NAME
        )
            .addMigrations(FlowStateDatabase.MIGRATION_5_6, FlowStateDatabase.MIGRATION_6_7, FlowStateDatabase.MIGRATION_7_8, FlowStateDatabase.MIGRATION_8_9,
                FlowStateDatabase.MIGRATION_9_10, FlowStateDatabase.MIGRATION_10_11, FlowStateDatabase.MIGRATION_11_12, FlowStateDatabase.MIGRATION_12_13,
                FlowStateDatabase.MIGRATION_13_14, FlowStateDatabase.MIGRATION_14_15, FlowStateDatabase.MIGRATION_15_16, FlowStateDatabase.MIGRATION_16_17,
                FlowStateDatabase.MIGRATION_17_18, FlowStateDatabase.MIGRATION_18_19, FlowStateDatabase.MIGRATION_19_20)
            .build()
    }

    @Provides
    @Singleton // A single instance of the DAO
    fun provideTaskDao(db: FlowStateDatabase): TaskDao {
        return db.taskDao
    }

    @Provides
    @Singleton
    fun provideIdeaDao(db: FlowStateDatabase): IdeaDao{
        return db.ideaDao
    }

    @Provides
    @Singleton
    fun provideCheckListDao(db: FlowStateDatabase): CheckListDao{
        return db.checkListDao
    }

    @Provides
    @Singleton
    fun provideUserPreferencesRepository(
        @ApplicationContext context: Context
    ): UserPreferencesRepository = UserPreferencesRepository(context)

    @Provides
    @Singleton
    fun provideCategoryDao(db: FlowStateDatabase): CategoryDao = db.categoryDao

    @Provides
    @Singleton
    fun provideHabitDao(db: FlowStateDatabase): HabitDao = db.habitDao
}