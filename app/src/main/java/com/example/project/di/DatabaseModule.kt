package com.example.project.di

import android.content.Context
import androidx.room.Room
import com.example.project.data.local.AppDatabase
import com.example.project.data.local.dao.DeviceDao
import com.example.project.data.local.dao.FriendDao
import com.example.project.data.local.dao.ZoneDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "contexthub_db"
        ).fallbackToDestructiveMigration().build()
    }

    @Provides
    fun provideZoneDao(db: AppDatabase): ZoneDao = db.zoneDao()

    @Provides
    fun provideDeviceDao(db: AppDatabase): DeviceDao = db.deviceDao()

    @Provides
    fun provideFriendDao(db: AppDatabase): FriendDao = db.friendDao()
}
