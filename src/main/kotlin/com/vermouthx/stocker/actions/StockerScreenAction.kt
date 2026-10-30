package com.vermouthx.stocker.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.wm.ToolWindowManager
import com.vermouthx.stocker.StockerBundle
import com.vermouthx.stocker.views.windows.StockerScreenTab

class StockerScreenAction : AnAction() {

    override fun update(e: AnActionEvent) {
        e.presentation.text = StockerBundle.message("screen.action.open")
        e.presentation.description = StockerBundle.message("screen.action.open.description")
        e.presentation.icon = AllIcons.Actions.Find
        e.presentation.isEnabled = e.project != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("Stocker") ?: return
        toolWindow.show {
            val content = toolWindow.contentManager.contents.firstOrNull { tab ->
                tab.getUserData(StockerScreenTab.KEY) == true
            }
            if (content != null) {
                toolWindow.contentManager.setSelectedContent(content)
            }
        }
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}
