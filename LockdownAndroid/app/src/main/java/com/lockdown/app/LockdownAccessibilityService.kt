package com.lockdown.app

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

class LockdownAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event?.eventType != AccessibilityEvent.TYPE_WINDOWS_CHANGED) return
        if (!LockdownController.isActive(this)) return
        val foregroundPackage = event.packageName?.toString() ?: return
        if (foregroundPackage == packageName || foregroundPackage == "com.android.systemui") return
        if (foregroundPackage in LockdownController.selected(this)) {
            val intent = Intent(this, BlockedActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra(BlockedActivity.EXTRA_BLOCKED_PACKAGE, foregroundPackage)
            }
            startActivity(intent)
        }
    }

    override fun onInterrupt() = Unit
}
