package com.example.ai.memory

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class RoutineDao(private val dbHelper: ArohiDatabaseHelper) {

    private val _routinesFlow = MutableStateFlow<List<RoutineItem>>(emptyList())
    val routinesFlow: Flow<List<RoutineItem>> = _routinesFlow.asStateFlow()

    init {
        refreshFlow()
    }

    private fun refreshFlow() {
        try {
            val list = queryAllSync()
            _routinesFlow.value = list
        } catch (_: Exception) {}
    }

    suspend fun insertRoutine(routine: RoutineItem): Long = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("title", routine.title)
            put("trigger_phrase", routine.triggerPhrase)
            put("description", routine.description)
            put("actions_json", routine.actionsJson)
            put("is_enabled", if (routine.isEnabled) 1 else 0)
            put("icon_name", routine.iconName)
        }
        val id = db.insertWithOnConflict("routines_table", null, values, SQLiteDatabase.CONFLICT_REPLACE)
        refreshFlow()
        id
    }

    fun getAllRoutinesFlow(): Flow<List<RoutineItem>> {
        refreshFlow()
        return routinesFlow
    }

    suspend fun getEnabledRoutines(): List<RoutineItem> = withContext(Dispatchers.IO) {
        val db = dbHelper.readableDatabase
        val list = mutableListOf<RoutineItem>()
        val cursor = db.rawQuery("SELECT id, title, trigger_phrase, description, actions_json, is_enabled, icon_name FROM routines_table WHERE is_enabled = 1 ORDER BY id ASC", null)
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    RoutineItem(
                        id = it.getLong(0),
                        title = it.getString(1),
                        triggerPhrase = it.getString(2),
                        description = it.getString(3),
                        actionsJson = it.getString(4),
                        isEnabled = it.getInt(5) == 1,
                        iconName = it.getString(6)
                    )
                )
            }
        }
        list
    }

    private fun queryAllSync(): List<RoutineItem> {
        val db = dbHelper.readableDatabase
        val list = mutableListOf<RoutineItem>()
        val cursor = db.rawQuery("SELECT id, title, trigger_phrase, description, actions_json, is_enabled, icon_name FROM routines_table ORDER BY id ASC", null)
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    RoutineItem(
                        id = it.getLong(0),
                        title = it.getString(1),
                        triggerPhrase = it.getString(2),
                        description = it.getString(3),
                        actionsJson = it.getString(4),
                        isEnabled = it.getInt(5) == 1,
                        iconName = it.getString(6)
                    )
                )
            }
        }
        return list
    }

    suspend fun setRoutineEnabled(id: Long, enabled: Boolean) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("is_enabled", if (enabled) 1 else 0)
        }
        db.update("routines_table", values, "id = ?", arrayOf(id.toString()))
        refreshFlow()
    }

    suspend fun deleteRoutine(id: Long) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        db.delete("routines_table", "id = ?", arrayOf(id.toString()))
        refreshFlow()
    }
}
