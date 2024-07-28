package com.github.iml885203.intellijgitmergeinto

import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.vcs.console.ShowVcsConsoleTabAction

class MyNotifier(private var project: Project) {
    fun notifyFailed(content: String) {
        createNotification("Git merge into failed", content, NotificationType.WARNING)
            .notify(project)
    }

    fun notifySuccess(content: String) {
        createNotification("Git merge into success", content, NotificationType.INFORMATION)
            .notify(project)
    }

    private fun createNotification(title: String, content: String, type: NotificationType): Notification {
        return NotificationGroupManager.getInstance()
            .getNotificationGroup("Git Merge Into")
            .createNotification(title, content, type)
            .addAction(object : NotificationAction("Open git console") {
                override fun actionPerformed(e: AnActionEvent, notification: Notification) {
                    ShowVcsConsoleTabAction().actionPerformed(e)
                }
            })
    }
}
