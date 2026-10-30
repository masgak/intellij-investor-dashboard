package com.vermouthx.stocker.views.dialogs

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.vermouthx.stocker.StockerBundle
import com.vermouthx.stocker.entities.StockerScreenRow
import com.vermouthx.stocker.screen.ScreenAiClient
import com.vermouthx.stocker.screen.ScreenCompanyClient
import com.vermouthx.stocker.screen.ScreenReason
import com.vermouthx.stocker.settings.StockerSetting
import java.awt.Dimension
import javax.swing.Action
import javax.swing.JComponent

class StockerScreenDetailDialog(
    project: Project?,
    private val row: StockerScreenRow,
    private val strategyId: String,
) : DialogWrapper(project) {

    private val text = JBTextArea()
    @Volatile
    private var closed = false

    init {
        title = "${row.name}  ${row.code}"
        text.isEditable = false
        text.lineWrap = true
        text.wrapStyleWord = true
        val setting = StockerSetting.instance
        val configured = setting.aiApiKey.isNotBlank() && setting.aiActiveModel.isNotBlank()
        text.text = ScreenReason.page(initialReason(configured, setting.aiActiveModel), row, strategyId, null)
        setOKButtonText(StockerBundle.message("screen.detail.close"))
        init()
        loadCompany()
    }

    override fun createCenterPanel(): JComponent {
        val scroll = JBScrollPane(text)
        scroll.preferredSize = Dimension(640, 460)
        return scroll
    }

    override fun createActions(): Array<Action> = arrayOf(okAction)

    override fun dispose() {
        closed = true
        super.dispose()
    }

    private fun loadCompany() {
        val setting = StockerSetting.instance
        val baseUrl = setting.aiBaseUrl
        val apiKey = setting.aiApiKey
        val model = setting.aiActiveModel
        val thinking = setting.aiThinking
        val configured = apiKey.isNotBlank() && model.isNotBlank()
        ApplicationManager.getApplication().executeOnPooledThread {
            val company = runCatching { ScreenCompanyClient.load(row.code) }.getOrNull()
            publish(ScreenReason.page(initialReason(configured, model), row, strategyId, company))
            if (!configured || closed) {
                return@executeOnPooledThread
            }
            val answer = runCatching {
                ScreenAiClient.writeReason(
                    baseUrl,
                    apiKey,
                    model,
                    thinking,
                    ScreenReason.prompt(row, strategyId, company, thinking),
                )
            }
            publish(ScreenReason.page(answer.getOrElse { error ->
                StockerBundle.message("screen.detail.ai.failed", error.message ?: error.javaClass.simpleName)
            }, row, strategyId, company))
        }
    }

    private fun initialReason(configured: Boolean, model: String): String {
        return if (configured) {
            StockerBundle.message("screen.detail.ai.writing", model)
        } else {
            StockerBundle.message("screen.detail.ai.missing")
        }
    }

    private fun publish(body: String) {
        ApplicationManager.getApplication().invokeLater {
            if (closed) {
                return@invokeLater
            }
            text.text = body
            text.caretPosition = 0
        }
    }
}
