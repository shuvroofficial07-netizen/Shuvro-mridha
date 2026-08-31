package com.example.services

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.graphics.Path
import android.accessibilityservice.GestureDescription
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

        /**
         * Package that most recently took over the window. Populated only from real
         * accessibility events; null until the first event arrives or if the service
         * is not enabled. Never guessed, never defaulted to our own package.
         */
        @Volatile
        var foregroundPackage: String? = null
            private set
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
        if (event == null) return
        // Only a window-state change means a different surface took focus.
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = event.packageName?.toString()
            if (!pkg.isNullOrBlank()) {
                foregroundPackage = pkg
            }
        }
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

    /** Long-presses the first node matching the query by text or content description. */
    fun longClickElement(query: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val nodes = root.findAccessibilityNodeInfosByText(query.lowercase().trim())
        for (node in nodes) {
            if (performLongClickOnNodeOrParent(node)) return true
        }
        return false
    }

    private fun performLongClickOnNodeOrParent(node: AccessibilityNodeInfo?): Boolean {
        var current = node
        while (current != null) {
            if (current.isLongClickable) {
                return current.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)
            }
            current = current.parent
        }
        return false
    }

    /**
     * Types into the field that currently holds input focus.
     *
     * Returns false when nothing is focused or the field rejects the text, so the
     * caller can never claim the text was entered when it was not.
     */
    fun typeIntoFocused(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    /** Scrolls the first scrollable container on screen. direction: down/up. */
    fun scrollScreen(direction: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val node = findScrollable(root, 0) ?: return false
        val forward = direction.equals("down", true) || direction.equals("forward", true)
        val action = if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        return node.performAction(action)
    }

    private fun findScrollable(node: AccessibilityNodeInfo?, depth: Int): AccessibilityNodeInfo? {
        if (node == null || depth > 20) return null
        if (node.isScrollable) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findScrollable(child, depth + 1)
            if (found != null) {
                if (found !== child) child.recycle()
                return found
            }
            child.recycle()
        }
        return null
    }

    /**
     * Dispatches a real swipe gesture. Uses the canPerformGestures capability that
     * is already declared in accessibility_service_config.xml - this is a genuine
     * system gesture, not a simulated one.
     */
    fun swipeGesture(direction: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        val dm = resources.displayMetrics
        val w = dm.widthPixels.toFloat()
        val h = dm.heightPixels.toFloat()
        val path = Path()
        when (direction.lowercase().trim()) {
            "up" -> { path.moveTo(w / 2f, h * 0.75f); path.lineTo(w / 2f, h * 0.25f) }
            "down" -> { path.moveTo(w / 2f, h * 0.25f); path.lineTo(w / 2f, h * 0.75f) }
            "left" -> { path.moveTo(w * 0.8f, h / 2f); path.lineTo(w * 0.2f, h / 2f) }
            "right" -> { path.moveTo(w * 0.2f, h / 2f); path.lineTo(w * 0.8f, h / 2f) }
            else -> return false
        }
        return try {
            val stroke = GestureDescription.StrokeDescription(path, 0L, 300L)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            dispatchGesture(gesture, null, null)
        } catch (e: Exception) {
            Log.w("ArohiA11y", "Gesture dispatch failed", e)
            false
        }
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
