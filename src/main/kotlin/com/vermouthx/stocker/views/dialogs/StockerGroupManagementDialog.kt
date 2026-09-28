package com.vermouthx.stocker.views.dialogs

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dsl.builder.panel
import com.vermouthx.stocker.StockerBundle
import com.vermouthx.stocker.entities.StockerSuggestion
import com.vermouthx.stocker.enums.StockerMarketType
import com.vermouthx.stocker.settings.StockerSetting
import java.awt.BorderLayout
import javax.swing.*

class StockerGroupManagementDialog(
    private val project: Project?
) : DialogWrapper(project, false) {

    private val setting = StockerSetting.instance
    private val editedGroups: MutableMap<String, MutableList<String>> = LinkedHashMap()

    private val groupsListModel = DefaultListModel<String>()
    private val groupsList = JBList(groupsListModel)

    private val membersListModel = DefaultListModel<String>()
    private val membersList = JBList(membersListModel)

    init {
        title = StockerBundle.message("dialog.group.management.title")
        setting.copyCustomGroups().forEach { (name, codes) ->
            editedGroups[name] = codes.toMutableList()
        }
        refreshGroupsList()
        init()
    }

    override fun createCenterPanel(): JComponent {
        val splitPane = JSplitPane(JSplitPane.HORIZONTAL_SPLIT)
        splitPane.leftComponent = createGroupsPanel()
        splitPane.rightComponent = createMembersPanel()
        splitPane.dividerLocation = 220
        return splitPane
    }

    private fun createGroupsPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.border = BorderFactory.createTitledBorder(
            StockerBundle.message("dialog.group.management.group.name")
        )

        groupsList.emptyText.text = StockerBundle.message("dialog.group.management.no.groups")
        groupsList.addListSelectionListener {
            if (!it.valueIsAdjusting) {
                refreshMembersList()
            }
        }

        val decorator = ToolbarDecorator.createDecorator(groupsList)
            .setAddAction {
                val name = Messages.showInputDialog(
                    project,
                    StockerBundle.message("dialog.group.management.create.prompt"),
                    StockerBundle.message("dialog.group.management.create"),
                    null
                )
                if (name != null && name.isNotBlank()) {
                    if (editedGroups.containsKey(name)) {
                        Messages.showErrorDialog(
                            project,
                            StockerBundle.message("dialog.group.management.duplicate", name),
                            StockerBundle.message("dialog.group.management.duplicate.title")
                        )
                    } else {
                        editedGroups.put(name, mutableListOf())
                        refreshGroupsList()
                        groupsList.setSelectedValue(name, true)
                    }
                }
            }
            .setRemoveAction {
                val selected = groupsList.selectedValue ?: return@setRemoveAction
                val confirm = Messages.showYesNoDialog(
                    project,
                    StockerBundle.message("dialog.group.management.delete.confirm", selected),
                    StockerBundle.message("dialog.group.management.delete"),
                    null
                )
                if (confirm == Messages.YES) {
                    editedGroups.remove(selected)
                    refreshGroupsList()
                    refreshMembersList()
                }
            }
            .disableUpDownActions()

        val topPanel = JPanel(BorderLayout())
        topPanel.add(decorator.createPanel(), BorderLayout.CENTER)

        // Add rename button below the list
        val renameButton = JButton(StockerBundle.message("dialog.group.management.rename"))
        renameButton.addActionListener {
            val selected = groupsList.selectedValue ?: return@addActionListener
            val newName = Messages.showInputDialog(
                project,
                StockerBundle.message("dialog.group.management.rename.prompt"),
                StockerBundle.message("dialog.group.management.rename"),
                null,
                selected,
                null
            )
            if (newName != null && newName.isNotBlank() && newName != selected) {
                if (editedGroups.containsKey(newName)) {
                    Messages.showErrorDialog(
                        project,
                        StockerBundle.message("dialog.group.management.duplicate", newName),
                        StockerBundle.message("dialog.group.management.duplicate.title")
                    )
                } else {
                    val codes = editedGroups.remove(selected) ?: mutableListOf()
                    editedGroups.put(newName, codes)
                    refreshGroupsList()
                    groupsList.setSelectedValue(newName, true)
                }
            }
        }
        val buttonPanel = JPanel()
        buttonPanel.add(renameButton)

        panel.add(topPanel, BorderLayout.CENTER)
        panel.add(buttonPanel, BorderLayout.SOUTH)
        return panel
    }

    private fun createMembersPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.border = BorderFactory.createTitledBorder(
            StockerBundle.message("dialog.group.management.members")
        )

        val scrollPane = JBScrollPane(membersList)
        panel.add(scrollPane, BorderLayout.CENTER)

        // Bottom panel with Add and Remove buttons
        val buttonPanel = JPanel()
        val addButton = JButton(StockerBundle.message("dialog.group.management.add.stock"))
        val removeButton = JButton(StockerBundle.message("dialog.group.management.remove.stock"))

        addButton.addActionListener { showAddStockDialog() }
        removeButton.addActionListener {
            val selected = membersList.selectedValue ?: return@addActionListener
            val groupName = groupsList.selectedValue ?: return@addActionListener
            val code = selected.substringBefore(" ")
            editedGroups[groupName]?.remove(code)
            refreshMembersList()
        }

        buttonPanel.add(addButton)
        buttonPanel.add(removeButton)
        panel.add(buttonPanel, BorderLayout.SOUTH)
        return panel
    }

    private fun showAddStockDialog() {
        val groupName = groupsList.selectedValue ?: return
        val groupCodes = editedGroups[groupName] ?: return

        // Collect all available stocks from market lists
        val allStocks = mutableListOf<StockerSuggestion>()
        setting.aShareList.forEach { code ->
            allStocks.add(StockerSuggestion(code, code, StockerMarketType.AShare))
        }
        setting.hkStocksList.forEach { code ->
            allStocks.add(StockerSuggestion(code, code, StockerMarketType.HKStocks))
        }
        setting.usStocksList.forEach { code ->
            allStocks.add(StockerSuggestion(code, code, StockerMarketType.USStocks))
        }
        setting.cryptoList.forEach { code ->
            allStocks.add(StockerSuggestion(code, code, StockerMarketType.Crypto))
        }

        val available = allStocks.filter { !groupCodes.contains(it.code) }
        if (available.isEmpty()) {
            Messages.showInfoMessage(
                project,
                StockerBundle.message("dialog.group.management.all.added"),
                StockerBundle.message("dialog.group.management.all.added.title")
            )
            return
        }

        val items = available.map { s ->
            val displayName = setting.getDisplayName(s.code, s.code)
            "${s.code} ${displayName}"
        }.toTypedArray()

        val selectedIndex = Messages.showChooseDialog(
            project,
            StockerBundle.message("dialog.group.management.search.stocks"),
            StockerBundle.message("dialog.group.management.add.stock"),
            null,
            items,
            items[0]
        )

        if (selectedIndex >= 0) {
            val code = items[selectedIndex].substringBefore(" ")
            groupCodes.add(code)
            refreshMembersList()
        }
    }

    private fun refreshGroupsList() {
        groupsListModel.clear()
        editedGroups.keys.sorted().forEach { groupsListModel.addElement(it) }
    }

    private fun refreshMembersList() {
        membersListModel.clear()
        val groupName = groupsList.selectedValue ?: return
        val codes = editedGroups[groupName] ?: return
        codes.forEach { code ->
            val displayName = setting.getDisplayName(code, code)
            membersListModel.addElement("$code $displayName")
        }
    }

    override fun doOKAction() {
        // Save edited groups back to settings
        setting.replaceCustomGroups(editedGroups)
        super.doOKAction()
    }
}
