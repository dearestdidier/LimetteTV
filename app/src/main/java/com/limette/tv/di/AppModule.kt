package com.limette.tv.di

import android.content.Context
import androidx.room.Room
import com.limette.tv.data.local.database.LimetteDatabase
import com.limette.tv.data.local.database.dao.EpisodeDao
import com.limette.tv.data.local.database.dao.FavoriteDao
import com.limette.tv.data.local.database.dao.MovieDao
import com.limette.tv.data.local.database.dao.SkipConfigDao
import com.limette.tv.data.local.database.dao.WatchHistoryDao
import com.limette.tv.data.local.database.dao.WebDavMediaDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton
    fun provideDatabase(@ApplicationContext context: Context): LimetteDatabase =
        Room.databaseBuilder(context, LimetteDatabase::class.java, "limette_tv_database")
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun provideWebDavMediaDao(db: LimetteDatabase): WebDavMediaDao = db.webDavMediaDao()
    @Provides fun provideWatchHistoryDao(db: LimetteDatabase): WatchHistoryDao = db.watchHistoryDao()
    @Provides fun provideFavoriteDao(db: LimetteDatabase): FavoriteDao = db.favoriteDao()
    @Provides fun provideSkipConfigDao(db: LimetteDatabase): SkipConfigDao = db.skipConfigDao()
    @Provides fun provideMovieDao(db: LimetteDatabase): MovieDao = db.movieDao()
    @Provides fun provideEpisodeDao(db: LimetteDatabase): EpisodeDao = db.episodeDao()

    @Provides @Singleton @Named("playback")
    fun providePlaybackOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
}
