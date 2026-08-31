package com.example.ai.memory

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class NotificationDao(private val dbHelper: ArohiDatabaseHelper) {

    private val _notificationsFlow = MutableStateFlow<List<CapturedNotification>>(emptyList())
    val notificationsFlow: Flow<List<CapturedNotification>> = _notificationsFlow.asStateFlow()

    init {
        refreshFlow()
    }

    private fun refreshFlow() {
        try {
            val list = queryRecentSync()
            _notificationsFlow.value = list
        } catch (_: Exception) {}
    }

    /**
     * Inserts or updates a captured notification.
     *
     * Android re-posts the same notification repeatedly (message count changes,
     * inline reply updates, re-delivery after rebind). The table has no unique
     * constraint, so CONFLICT_REPLACE alone never fired and every re-post created
     * a duplicate row. Match on package + postTime + title instead, which is stable
     * across re-posts of one notification, and update that row.
     */
    suspend fun insertNotification(notification: CapturedNotification): Long = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("package_name", notification.packageName)
            put("app_name", notification.appName)
            put("sender", notification.sender)
            put("title", notification.title)
            put("text_content", notification.text)
            put("timestamp", notification.timestamp)
            put("priority", notification.priority)
            put("is_read", if (notification.isRead) 1 else 0)
        }

        val existingId: Long? = db.rawQuery(
            "SELECT id FROM notifications_table WHERE package_name = ? AND timestamp = ? AND title = ? LIMIT 1",
            arrayOf(notification.packageName, notification.timestamp.toString(), notification.title)
        ).use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else null }

        val id = if (existingId != null) {
            db.update("notifications_table", values, "id = ?", arrayOf(existingId.toString())).toLong()
            existingId
        } else {
            db.insert("notifications_table", null, values)
        }
        refreshFlow()
        id
    }

    fun getRecentNotificationsFlow(): Flow<List<CapturedNotification>> {
        refreshFlow()
        return notificationsFlow
    }

    suspend fun getRecentNotifications(): List<CapturedNotification> = withContext(Dispatchers.IO) {
        queryRecentSync()
    }

    private fun queryRecentSync(): List<CapturedNotification> {
        val db = dbHelper.readableDatabase
        val list = mutableListOf<CapturedNotification>()
        val cursor = db.rawQuery("SELECT id, package_name, app_name, sender, title, text_content, timestamp, priority, is_read FROM notifications_table ORDER BY timestamp DESC LIMIT 50", null)
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    CapturedNotification(
                        id = it.getLong(0),
                        packageName = it.getString(1),
                        appName = it.getString(2),
                        sender = it.getString(3),
                        title = it.getString(4),
                        text = it.getString(5),
                        timestamp = it.getLong(6),
                        priority = it.getString(7),
                        isRead = it.getInt(8) == 1
                    )
                )
            }
        }
        return list
    }

    suspend fun getUnreadNotifications(): List<CapturedNotification> = withContext(Dispatchers.IO) {
        val db = dbHelper.readableDatabase
        val list = mutableListOf<CapturedNotification>()
        val cursor = db.rawQuery("SELECT id, package_name, app_name, sender, title, text_content, timestamp, priority, is_read FROM notifications_table WHERE is_read = 0 ORDER BY timestamp DESC", null)
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    CapturedNotification(
                        id = it.getLong(0),
                        packageName = it.getString(1),
                        appName = it.getString(2),
                        sender = it.getString(3),
                        title = it.getString(4),
                        text = it.getString(5),
                        timestamp = it.getLong(6),
                        priority = it.getString(7),
                        isRead = it.getInt(8) == 1
                    )
                )
            }
        }
        list
    }

    suspend fun markAsRead(id: Long) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("is_read", 1)
        }
        db.update("notifications_table", values, "id = ?", arrayOf(id.toString()))
        refreshFlow()
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        db.delete("notifications_table", null, null)
        refreshFlow()
    }
}
