package com.vermouthx.stocker.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.vermouthx.stocker.StockerBundle
import com.vermouthx.stocker.listeners.StockerGroupsChangedNotifier
import com.vermouthx.stocker.views.dialogs.StockerGroupManagementDialog

class StockerGroupManageAction : AnAction() {
    override fun update(e: AnActionEvent) {
        val presentation = e.presentation
        presentation.text = StockerBundle.message("action.manage.groups")
        presentation.description = StockerBundle.message("action.manage.groups.description")
        presentation.isEnabled = e.project != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val dialog = StockerGroupManagementDialog(project)
        if (dialog.showAndGet()) {
            ApplicationManager.getApplication().messageBus
                .syncPublisher(StockerGroupsChangedNotifier.STOCKER_GROUPS_CHANGED_TOPIC)
                .groupsChanged()
        }
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
}
