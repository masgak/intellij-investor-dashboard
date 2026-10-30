package com.vermouthx.stocker.views.dialogs

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.vermouthx.stocker.StockerBundle
import com.vermouthx.stocker.settings.StockerSetting
import com.vermouthx.stocker.utils.UsmartQuoteClient
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane

class StockerUsmartSettingDialog(parent: Project?) : DialogWrapper(parent) {

    private val setting = StockerSetting.instance
    private val channel = JBTextField(setting.usmartChannel)
    private val areaCode = JBTextField(setting.usmartAreaCode)
    private val account = JBTextField(setting.usmartAccount)
    private val password = JBPasswordField()
    private val privateKey = JBTextArea(setting.usmartPrivateKey)
    private val publicKey = JBTextArea(setting.usmartPublicKey)

    init {
        title = StockerBundle.message("settings.usmart.title")
        password.text = setting.usmartPassword
        privateKey.lineWrap = true
        publicKey.lineWrap = true
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(GridBagLayout())
        val constraints = GridBagConstraints().apply {
            gridx = 0
            fill = GridBagConstraints.HORIZONTAL
            weightx = 1.0
            insets = Insets(6, 0, 0, 0)
            anchor = GridBagConstraints.WEST
        }
        fun add(component: JComponent) {
            panel.add(component, constraints)
        }
        val hint = JLabel("<html><body style='width:420px'>${StockerBundle.message("settings.usmart.hint")}</body></html>")
        add(hint)
        add(JLabel(StockerBundle.message("settings.usmart.channel")))
        add(channel)
        add(JLabel(StockerBundle.message("settings.usmart.area")))
        add(areaCode)
        add(JLabel(StockerBundle.message("settings.usmart.account")))
        add(account)
        add(JLabel(StockerBundle.message("settings.usmart.password")))
        add(password)
        add(JLabel(StockerBundle.message("settings.usmart.private")))
        add(scroll(privateKey, 90))
        add(JLabel(StockerBundle.message("settings.usmart.public")))
        add(scroll(publicKey, 90))
        panel.preferredSize = Dimension(480, 520)
        return panel
    }

    override fun doOKAction() {
        setting.usmartChannel = channel.text
        setting.usmartAreaCode = areaCode.text
        setting.usmartAccount = account.text
        setting.usmartPassword = String(password.password)
        setting.usmartPrivateKey = privateKey.text
        setting.usmartPublicKey = publicKey.text
        UsmartQuoteClient.invalidate()
        super.doOKAction()
    }

    private fun scroll(area: JBTextArea, height: Int): JScrollPane {
        val pane = JScrollPane(area)
        pane.preferredSize = Dimension(460, height)
        return pane
    }
}
