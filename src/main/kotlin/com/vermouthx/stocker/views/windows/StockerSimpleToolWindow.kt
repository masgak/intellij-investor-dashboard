package com.vermouthx.stocker.views.windows

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.SearchTextField
import com.intellij.util.ui.JBUI
import com.vermouthx.stocker.StockerBundle
import com.vermouthx.stocker.actions.StockerGroupManageAction
import com.vermouthx.stocker.actions.StockerRefreshAction
import com.vermouthx.stocker.actions.StockerSettingAction
import com.vermouthx.stocker.actions.StockerStockManageAction
import com.vermouthx.stocker.actions.StockerStockSearchAction
import com.vermouthx.stocker.actions.StockerStopAction
import com.vermouthx.stocker.enums.StockerMarketType
import com.vermouthx.stocker.views.StockerTableView
import javax.swing.event.DocumentEvent

class StockerSimpleToolWindow(
    private val marketScope: StockerMarketType? = null,
) : SimpleToolWindowPanel(true) {
    var tableView: StockerTableView = StockerTableView(marketScope)

    init {
        val actionManager = ActionManager.getInstance()
        val leftActions = listOfNotNull(
            StockerStockSearchAction::class.qualifiedName?.let { actionManager.getAction(it) },
            StockerRefreshAction::class.qualifiedName?.let { actionManager.getAction(it) },
            StockerStopAction::class.qualifiedName?.let { actionManager.getAction(it) },
            StockerStockManageAction::class.qualifiedName?.let { actionManager.getAction(it) },
            StockerGroupManageAction::class.qualifiedName?.let { actionManager.getAction(it) }
        )
        val actionGroup = DefaultActionGroup(leftActions)
        val actionToolbar = actionManager.createActionToolbar(ActionPlaces.TOOLWINDOW_CONTENT, actionGroup, true)
        actionToolbar.targetComponent = tableView.component
        
        val rightActionGroup = DefaultActionGroup().apply {
            StockerSettingAction::class.qualifiedName?.let { actionManager.getAction(it) }?.let { add(it) }
        }
        val rightActionToolbar = actionManager.createActionToolbar(ActionPlaces.TOOLWINDOW_CONTENT, rightActionGroup, true)
        rightActionToolbar.targetComponent = tableView.component
        
        val filterField = SearchTextField(true).apply {
            textEditor.emptyText.text = StockerBundle.message("table.filter.empty")
            addDocumentListener(object : DocumentAdapter() {
                override fun textChanged(e: DocumentEvent) {
                    tableView.setRowFilterText(text)
                }
            })
        }
        val filterWrapper = javax.swing.JPanel(java.awt.BorderLayout()).apply {
            isOpaque = false
            border = JBUI.Borders.empty(4, 8, 4, 4)
            add(filterField, java.awt.BorderLayout.CENTER)
        }

        val toolbarPanel = javax.swing.JPanel(java.awt.BorderLayout()).apply {
            add(actionToolbar.component, java.awt.BorderLayout.WEST)
            add(filterWrapper, java.awt.BorderLayout.CENTER)
            add(rightActionToolbar.component, java.awt.BorderLayout.EAST)
        }
        
        this.toolbar = toolbarPanel
        setContent(tableView.component)
    }
}
