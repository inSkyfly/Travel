package com.tourism.assistant.di

import android.content.Context
import androidx.room.Room
import com.tourism.assistant.BuildConfig
import com.tourism.assistant.data.local.AppDatabase
import com.tourism.assistant.data.local.GsonProvider
import com.tourism.assistant.data.local.SavedPlanDao
import com.tourism.assistant.data.local.TripPlanLocalRepository
import com.tourism.assistant.data.mock.MockAgentRepository
import com.tourism.assistant.data.mock.MockRagRepository
import com.tourism.assistant.data.mock.MockWeatherRepository
import com.tourism.assistant.data.remote.RemoteAgentRepository
import com.tourism.assistant.data.remote.RemoteRagRepository
import com.tourism.assistant.data.remote.RemoteWeatherRepository
import com.tourism.assistant.domain.repository.AgentRepository
import com.tourism.assistant.domain.repository.RagRepository
import com.tourism.assistant.domain.repository.TripPlanRepository
import com.tourism.assistant.domain.repository.WeatherRepository
import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideGson(): Gson = GsonProvider.create()

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "tourism_assistant.db"
        ).build()
    }

    @Provides
    fun provideSavedPlanDao(database: AppDatabase): SavedPlanDao = database.savedPlanDao()

    @Provides
    @Singleton
    fun provideAgentRepository(
        remote: RemoteAgentRepository,
        mock: MockAgentRepository
    ): AgentRepository {
        return if (BuildConfig.USE_REMOTE_AI) remote else mock
    }

    @Provides
    @Singleton
    fun provideRagRepository(
        remote: RemoteRagRepository,
        mock: MockRagRepository
    ): RagRepository {
        return if (BuildConfig.USE_REMOTE_AI) remote else mock
    }

    @Provides
    @Singleton
    fun provideWeatherRepository(
        remote: RemoteWeatherRepository,
        mock: MockWeatherRepository
    ): WeatherRepository {
        return if (BuildConfig.USE_MOCK_WEATHER) mock else remote
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @dagger.Binds
    @Singleton
    abstract fun bindTripPlanRepository(impl: TripPlanLocalRepository): TripPlanRepository
}
