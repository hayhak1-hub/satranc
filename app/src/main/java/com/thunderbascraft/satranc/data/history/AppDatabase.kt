package com.thunderbascraft.satranc.data.history

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Local database (game history). Schema version 1 is exported to app/schemas; any change
 * must bump the version and add a migration so that players keep their games.
 */
@Database(entities = [GameRecord::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun games(): GameDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "satranc.db")
                .build().also { instance = it }
        }
    }
}
