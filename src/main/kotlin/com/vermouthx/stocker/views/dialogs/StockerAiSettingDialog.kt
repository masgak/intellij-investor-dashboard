package com.vermouthx.stocker.views.dialogs

import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.vermouthx.stocker.StockerBundle
import com.vermouthx.stocker.settings.StockerSetting
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane

class StockerAiSettingDialog(parent: com.intellij.openapi.project.Project?) : DialogWrapper(parent) {

    private val setting = StockerSetting.instance
    private val baseUrl = JBTextField(setting.aiBaseUrl)
    private val apiKey = JBPasswordField()
    private val modelInput = JBTextField()
    private val models = DefaultListModel<String>()
    private val modelList = JBList(models)
    private val thinking = ComboBox(arrayOf("off", "low", "medium", "high"))

    init {
        title = StockerBundle.message("ai.settings.title")
        setting.aiModels.forEach { models.addElement(it) }
        val active = setting.aiActiveModel
        if (active.isNotBlank()) {
            modelList.setSelectedValue(active, true)
        }
        thinking.selectedItem = setting.aiThinking
        thinking.renderer = com.intellij.ui.SimpleListCellRenderer.create("") { thinkingLabel(it) }
        apiKey.text = setting.aiApiKey
        baseUrl.emptyText.text = "https://api.x.ai/v1"
        modelInput.emptyText.text = StockerBundle.message("ai.settings.model.hint")
        setOKButtonText(StockerBundle.message("ai.settings.save"))
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(java.awt.GridBagLayout())
        val constraints = java.awt.GridBagConstraints().apply {
            gridx = 0
            fill = java.awt.GridBagConstraints.HORIZONTAL
            weightx = 1.0
            insets = java.awt.Insets(6, 0, 0, 0)
            anchor = java.awt.GridBagConstraints.WEST
        }
        fun add(component: JComponent) {
            panel.add(component, constraints)
        }
        add(JLabel(StockerBundle.message("ai.settings.url")))
        add(baseUrl)
        add(JLabel(StockerBundle.message("ai.settings.key")))
        add(apiKey)
        add(JLabel(StockerBundle.message("ai.settings.thinking")))
        add(thinking)
        val modelHeader = JPanel(BorderLayout())
        modelHeader.add(JLabel("Model ID"), BorderLayout.WEST)
        val addButton = JButton(StockerBundle.message("ai.settings.model.add"))
        addButton.addActionListener { addModel() }
        modelHeader.add(addButton, BorderLayout.EAST)
        add(modelHeader)
        add(modelInput)
        val listPane = JScrollPane(modelList)
        listPane.preferredSize = Dimension(420, 120)
        add(listPane)
        val remove = JButton(StockerBundle.message("ai.settings.model.remove"))
        remove.addActionListener {
            val index = modelList.selectedIndex
            if (index >= 0) {
                models.remove(index)
            }
        }
        add(remove)
        panel.preferredSize = Dimension(460, 360)
        return panel
    }

    override fun doOKAction() {
        addModel()
        val saved = (0 until models.size()).map { models.getElementAt(it) }
        setting.aiBaseUrl = baseUrl.text
        setting.aiApiKey = String(apiKey.password)
        setting.aiModels = saved
        setting.aiActiveModel = modelList.selectedValue ?: saved.firstOrNull().orEmpty()
        setting.aiThinking = thinking.selectedItem as? String ?: "medium"
        super.doOKAction()
    }

    private fun addModel() {
        val value = modelInput.text.trim()
        if (value.isEmpty() || (0 until models.size()).any { models.getElementAt(it) == value }) {
            modelInput.text = ""
            return
        }
        models.addElement(value)
        modelList.setSelectedValue(value, true)
        modelInput.text = ""
    }

    private fun thinkingLabel(id: String?): String = when (id) {
        "off" -> StockerBundle.message("ai.thinking.off")
        "low" -> StockerBundle.message("ai.thinking.low")
        "high" -> StockerBundle.message("ai.thinking.high")
        else -> StockerBundle.message("ai.thinking.medium")
    }
}
