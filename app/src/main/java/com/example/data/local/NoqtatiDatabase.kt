package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        AcademicClassEntity::class,
        StudentEntity::class,
        SubjectEntity::class,
        SemesterEntity::class,
        GradeEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class NoqtatiDatabase : RoomDatabase() {
    abstract fun noqtatiDao(): NoqtatiDao

    companion object {
        @Volatile
        private var INSTANCE: NoqtatiDatabase? = null

        fun getInstance(context: Context): NoqtatiDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    NoqtatiDatabase::class.java,
                    "noqtati_local.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
