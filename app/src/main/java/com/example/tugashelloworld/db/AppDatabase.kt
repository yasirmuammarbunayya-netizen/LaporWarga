package com.example.tugashelloworld.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.tugashelloworld.model.Report
import com.example.tugashelloworld.model.User

@Database(entities = [User::class, Report::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun userDao(): UserDao
    abstract fun reportDao(): ReportDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "laporwarga.db"
            )
                // Skema berubah (kolom videoPath). Untuk aplikasi tugas, data lama boleh direset.
                .fallbackToDestructiveMigration()
                .build().also { instance = it }
        }
    }
}
