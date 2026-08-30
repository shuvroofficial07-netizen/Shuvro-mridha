package com.example.ai.memory

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class TaskLogDao(private val dbHelper: ArohiDatabaseHelper) {

    private val _logsFlow = MutableStateFlow<List<TaskLog>>(emptyList())
    val logsFlow: Flow<List<TaskLog>> = _logsFlow.asStateFlow()

    init {
        refreshFlow()
    }

    private fun refreshFlow() {
        try {
            val list = queryRecentSync()
            _logsFlow.value = list
        } catch (_: Exception) {}
    }

    suspend fun insertLog(log: TaskLog): Long = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("query", log.query)
            put("response", log.response)
            put("status", log.status)
            put("timestamp", log.timestamp)
        }
        val id = db.insertWithOnConflict("task_logs_table", null, values, SQLiteDatabase.CONFLICT_REPLACE)
        refreshFlow()
        id
    }

    fun getRecentLogsFlow(): Flow<List<TaskLog>> {
        refreshFlow()
        return logsFlow
    }

    private fun queryRecentSync(): List<TaskLog> {
        val db = dbHelper.readableDatabase
        val list = mutableListOf<TaskLog>()
        val cursor = db.rawQuery("SELECT id, query, response, status, timestamp FROM task_logs_table ORDER BY timestamp DESC LIMIT 50", null)
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    TaskLog(
                        id = it.getLong(0),
                        query = it.getString(1),
                        response = it.getString(2),
                        status = it.getString(3),
                        timestamp = it.getLong(4)
                    )
                )
            }
        }
        return list
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        db.delete("task_logs_table", null, null)
        refreshFlow()
    }
}
