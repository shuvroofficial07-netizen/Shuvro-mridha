package com.example.services

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class ArohiAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: ArohiAccessibilityService? = null
            private set

        val isConnected: Boolean
            get() = instance != null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d("ArohiA11y", "Arohi Accessibility Service connected.")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Can monitor foreground package changes or UI events for verification
    }

    override fun onInterrupt() {
        Log.w("ArohiA11y", "Accessibility Service interrupted.")
    }

    fun readCurrentScreen(): String {
        val root = rootInActiveWindow ?: return ""
        val stringBuilder = StringBuilder()
        extractText(root, stringBuilder, 0)
        return stringBuilder.toString().trim()
    }

    private fun extractText(node: AccessibilityNodeInfo?, builder: StringBuilder, depth: Int) {
        if (node == null || depth > 20) return

        val text = node.text
        val contentDesc = node.contentDescription
        val viewId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            node.viewIdResourceName
        } else null

        if (!text.isNullOrBlank()) {
            builder.append(text).append("\n")
        } else if (!contentDesc.isNullOrBlank()) {
            builder.append(contentDesc).append("\n")
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                extractText(child, builder, depth + 1)
                child.recycle()
            }
        }
    }

    fun findAndClickElement(query: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val cleanQuery = query.lowercase().trim()

        // 1. By text
        val nodesByText = root.findAccessibilityNodeInfosByText(cleanQuery)
        for (node in nodesByText) {
            if (performClickOnNodeOrParent(node)) {
                return true
            }
        }

        // 2. By view id if resource prefix
        if (cleanQuery.contains(":id/")) {
            val nodesById = root.findAccessibilityNodeInfosByViewId(cleanQuery)
            for (node in nodesById) {
                if (performClickOnNodeOrParent(node)) {
                    return true
                }
            }
        }

        return false
    }

    private fun performClickOnNodeOrParent(node: AccessibilityNodeInfo?): Boolean {
        var current = node
        while (current != null) {
            if (current.isClickable) {
                return current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            current = current.parent
        }
        return false
    }
}
