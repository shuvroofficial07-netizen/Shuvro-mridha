package com.example.ai.memory

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class MemoryDao(private val dbHelper: ArohiDatabaseHelper) {

    private val _memoriesFlow = MutableStateFlow<List<MemoryItem>>(emptyList())
    val memoriesFlow: Flow<List<MemoryItem>> = _memoriesFlow.asStateFlow()

    init {
        refreshFlow()
    }

    private fun refreshFlow() {
        try {
            val list = queryAllSync()
            _memoriesFlow.value = list
        } catch (_: Exception) {}
    }

    suspend fun insertMemory(item: MemoryItem): Long = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("category", item.category)
            put("key_name", item.key)
            put("value_text", item.value)
            put("timestamp", item.timestamp)
        }
        val id = db.insertWithOnConflict("memory_table", null, values, SQLiteDatabase.CONFLICT_REPLACE)
        refreshFlow()
        id
    }

    fun getAllMemoriesFlow(): Flow<List<MemoryItem>> {
        refreshFlow()
        return memoriesFlow
    }

    suspend fun getAllMemories(): List<MemoryItem> = withContext(Dispatchers.IO) {
        queryAllSync()
    }

    private fun queryAllSync(): List<MemoryItem> {
        val db = dbHelper.readableDatabase
        val list = mutableListOf<MemoryItem>()
        val cursor = db.rawQuery("SELECT id, category, key_name, value_text, timestamp FROM memory_table ORDER BY timestamp DESC", null)
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    MemoryItem(
                        id = it.getLong(0),
                        category = it.getString(1),
                        key = it.getString(2),
                        value = it.getString(3),
                        timestamp = it.getLong(4)
                    )
                )
            }
        }
        return list
    }

    suspend fun getMemory(category: String, key: String): MemoryItem? = withContext(Dispatchers.IO) {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery("SELECT id, category, key_name, value_text, timestamp FROM memory_table WHERE category = ? AND key_name = ? LIMIT 1", arrayOf(category, key))
        cursor.use {
            if (it.moveToFirst()) {
                MemoryItem(
                    id = it.getLong(0),
                    category = it.getString(1),
                    key = it.getString(2),
                    value = it.getString(3),
                    timestamp = it.getLong(4)
                )
            } else null
        }
    }

    suspend fun getMemoriesByCategory(category: String): List<MemoryItem> = withContext(Dispatchers.IO) {
        val db = dbHelper.readableDatabase
        val list = mutableListOf<MemoryItem>()
        val cursor = db.rawQuery("SELECT id, category, key_name, value_text, timestamp FROM memory_table WHERE category = ? ORDER BY timestamp DESC", arrayOf(category))
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    MemoryItem(
                        id = it.getLong(0),
                        category = it.getString(1),
                        key = it.getString(2),
                        value = it.getString(3),
                        timestamp = it.getLong(4)
                    )
                )
            }
        }
        list
    }

    suspend fun searchMemories(query: String): List<MemoryItem> = withContext(Dispatchers.IO) {
        val db = dbHelper.readableDatabase
        val list = mutableListOf<MemoryItem>()
        val wild = "%$query%"
        val cursor = db.rawQuery("SELECT id, category, key_name, value_text, timestamp FROM memory_table WHERE key_name LIKE ? OR value_text LIKE ?", arrayOf(wild, wild))
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    MemoryItem(
                        id = it.getLong(0),
                        category = it.getString(1),
                        key = it.getString(2),
                        value = it.getString(3),
                        timestamp = it.getLong(4)
                    )
                )
            }
        }
        list
    }

    suspend fun deleteMemoryById(id: Long) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        db.delete("memory_table", "id = ?", arrayOf(id.toString()))
        refreshFlow()
    }

    suspend fun deleteMemory(key: String) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        db.delete("memory_table", "key_name = ?", arrayOf(key))
        refreshFlow()
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        db.delete("memory_table", null, null)
        refreshFlow()
    }
}
