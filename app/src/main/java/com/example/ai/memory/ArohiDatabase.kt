package com.example.ai.memory

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class ArohiDatabaseHelper(context: Context) : SQLiteOpenHelper(context.applicationContext, "arohi_database.db", null, 3) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS memory_table (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                category TEXT NOT NULL,
                key_name TEXT NOT NULL,
                value_text TEXT NOT NULL,
                timestamp INTEGER NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS routines_table (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL,
                trigger_phrase TEXT NOT NULL,
                description TEXT NOT NULL,
                actions_json TEXT NOT NULL,
                is_enabled INTEGER NOT NULL DEFAULT 1,
                icon_name TEXT NOT NULL DEFAULT 'Routine'
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS notifications_table (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                package_name TEXT NOT NULL,
                app_name TEXT NOT NULL,
                sender TEXT NOT NULL,
                title TEXT NOT NULL,
                text_content TEXT NOT NULL,
                timestamp INTEGER NOT NULL,
                priority TEXT NOT NULL DEFAULT 'NORMAL',
                is_read INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS task_logs_table (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                query TEXT NOT NULL,
                response TEXT NOT NULL,
                status TEXT NOT NULL DEFAULT 'SUCCESS',
                timestamp INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS memory_table")
        db.execSQL("DROP TABLE IF EXISTS routines_table")
        db.execSQL("DROP TABLE IF EXISTS notifications_table")
        db.execSQL("DROP TABLE IF EXISTS task_logs_table")
        onCreate(db)
    }
}

class ArohiDatabase private constructor(context: Context) {

    private val helper = ArohiDatabaseHelper(context)

    private val _memoryDao = MemoryDao(helper)
    private val _routineDao = RoutineDao(helper)
    private val _notificationDao = NotificationDao(helper)
    private val _taskLogDao = TaskLogDao(helper)

    fun memoryDao(): MemoryDao = _memoryDao
    fun routineDao(): RoutineDao = _routineDao
    fun notificationDao(): NotificationDao = _notificationDao
    fun taskLogDao(): TaskLogDao = _taskLogDao

    companion object {
        @Volatile
        private var INSTANCE: ArohiDatabase? = null

        fun getDatabase(context: Context): ArohiDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = ArohiDatabase(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}
