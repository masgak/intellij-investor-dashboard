package com.vermouthx.stocker.views.windows

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import com.vermouthx.stocker.StockerBundle
import com.vermouthx.stocker.entities.StockerQuote
import com.vermouthx.stocker.listeners.StockerQuoteUpdateNotifier
import com.vermouthx.stocker.settings.StockerPaperPosition
import com.vermouthx.stocker.settings.StockerSetting
import java.awt.BorderLayout
import java.awt.Dimension
import java.util.Locale
import javax.swing.JPanel
import javax.swing.ListSelectionModel
import javax.swing.table.DefaultTableModel

class StockerPaperPanel(project: Project) : SimpleToolWindowPanel(true), Disposable {

    private val setting = StockerSetting.instance
    private val quotes = HashMap<String, StockerQuote>()
    private val summary = JBLabel(" ")
    private val table = JBTable()
    private val amountField = JBTextField(10)

    init {
        amountField.text = formatMoney(setting.paperInitialCash)
        val actions = DefaultActionGroup(BuyAction(), SellAction(), ResetAction())
        val toolbar = ActionManager.getInstance().createActionToolbar("StockerPaper", actions, true)
        toolbar.targetComponent = table
        val north = JPanel(BorderLayout())
        north.add(toolbar.component, BorderLayout.WEST)
        val amountRow = JPanel(BorderLayout())
        amountRow.border = JBUI.Borders.empty(0, 8)
        amountRow.add(JBLabel(StockerBundle.message("paper.amount")), BorderLayout.WEST)
        amountRow.add(amountField, BorderLayout.CENTER)
        north.add(amountRow, BorderLayout.EAST)

        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
        table.setStriped(true)
        table.fillsViewportHeight = true
        val south = JPanel(BorderLayout())
        south.border = JBUI.Borders.empty(4, 8)
        south.add(summary, BorderLayout.CENTER)
        val center = JPanel(BorderLayout())
        center.add(north, BorderLayout.NORTH)
        center.add(JBScrollPane(table), BorderLayout.CENTER)
        center.add(south, BorderLayout.SOUTH)
        setContent(center)

        project.messageBus.connect(this).subscribe(
            StockerQuoteUpdateNotifier.STOCK_ALL_QUOTE_UPDATE_TOPIC,
            object : StockerQuoteUpdateNotifier {
                override fun syncQuotes(quotes: MutableList<StockerQuote>, size: Int) {
                    ApplicationManager.getApplication().invokeLater {
                        quotes.forEach { quote -> this@StockerPaperPanel.quotes[quote.code] = quote }
                        refresh()
                    }
                }

                override fun syncIndices(indices: MutableList<StockerQuote>) = Unit
            }
        )
        refresh()
    }

    private fun refresh() {
        val positions = setting.paperPositions()
        val model = DefaultTableModel(columnNames(), 0)
        var marketValue = 0.0
        var todayPnl = 0.0
        var yesterdayEquity = setting.paperCash
        positions.forEach { position ->
            val quote = quotes[position.code]
            val current = quote?.current
            val change = quote?.change
            val pct = quote?.percentage
            val todayAmount = if (current != null && change != null) position.shares * change else null
            val cost = position.shares * position.costPrice
            val value = if (current != null) position.shares * current else null
            if (value != null) {
                marketValue += value
            }
            if (todayAmount != null && current != null && change != null) {
                todayPnl += todayAmount
                yesterdayEquity += position.shares * (current - change)
            }
            val pnl = if (value != null) value - cost else null
            model.addRow(
                arrayOf<Any>(
                    position.code,
                    position.name,
                    position.shares,
                    formatPrice(position.costPrice),
                    current?.let { formatPrice(it) } ?: "-",
                    pct?.let { formatPercent(it / 100.0) } ?: "-",
                    todayAmount?.let { formatSigned(it) } ?: "-",
                    pnl?.let { formatPnl(it, cost) } ?: "-",
                )
            )
        }
        table.model = model
        val equity = setting.paperCash + marketValue
        val todayPct = if (yesterdayEquity > 0.0) todayPnl / yesterdayEquity else 0.0
        val totalPnl = equity - setting.paperInitialCash
        val totalPct = if (setting.paperInitialCash > 0.0) totalPnl / setting.paperInitialCash else 0.0
        summary.text = StockerBundle.message(
            "paper.summary",
            formatMoney(setting.paperCash),
            formatMoney(equity),
            formatSigned(todayPnl),
            formatPercent(todayPct),
            formatSigned(totalPnl),
            formatPercent(totalPct),
        )
    }

    private fun symbolChoice(code: String, knownName: String?): SymbolChoice {
        val custom = setting.getCustomName(code)
        val quoted = quotes[code]?.name
        val name = custom?.takeIf { it.isNotBlank() }
            ?: knownName?.takeIf { it.isNotBlank() }
            ?: quoted?.takeIf { it.isNotBlank() }
            ?: code
        return SymbolChoice(code, name)
    }

    private fun symbols(): List<String> {
        return (setting.aShareList + setting.hkStocksList + setting.usStocksList + setting.cryptoList).distinct()
    }

    private fun buy() {
        val codes = symbols()
        if (codes.isEmpty()) {
            Messages.showInfoMessage(StockerBundle.message("paper.no.symbols"), StockerBundle.message("paper.tab"))
            return
        }
        val dialog = TradeDialog(codes.map { code -> symbolChoice(code, null) }, null, buy = true)
        if (!dialog.showAndGet()) {
            return
        }
        val quote = quotes[dialog.code]
        if (quote == null) {
            Messages.showInfoMessage(StockerBundle.message("paper.no.quote"), StockerBundle.message("paper.tab"))
            return
        }
        val error = setting.paperBuy(dialog.code, quote.name, dialog.shares, quote.current)
        if (error != null) {
            Messages.showInfoMessage(StockerBundle.message("paper.not.enough.cash"), StockerBundle.message("paper.tab"))
            return
        }
        refresh()
    }

    private fun sell() {
        val positions = setting.paperPositions()
        if (positions.isEmpty()) {
            return
        }
        val selectedCode = tableSelectedCode()
        val sellable = selectedCode?.let { setting.paperSellableShares(it) } ?: 0
        val dialog = TradeDialog(
            positions.map { symbolChoice(it.code, it.name) },
            selectedCode,
            buy = false,
            maxShares = if (selectedCode != null) sellable else null,
        )
        if (!dialog.showAndGet()) {
            return
        }
        val quote = quotes[dialog.code]
        if (quote == null) {
            Messages.showInfoMessage(StockerBundle.message("paper.no.quote"), StockerBundle.message("paper.tab"))
            return
        }
        val error = setting.paperSell(dialog.code, dialog.shares, quote.current)
        if (error == "t1") {
            Messages.showInfoMessage(StockerBundle.message("paper.t1.block"), StockerBundle.message("paper.tab"))
            return
        }
        if (error != null) {
            Messages.showInfoMessage(StockerBundle.message("paper.not.enough.shares"), StockerBundle.message("paper.tab"))
            return
        }
        refresh()
    }

    private fun tableSelectedCode(): String? {
        val row = table.selectedRow
        if (row < 0) {
            return null
        }
        return table.getValueAt(row, 0) as? String
    }

    override fun dispose() = Unit

    private inner class BuyAction : AnAction() {
        override fun update(e: AnActionEvent) {
            e.presentation.text = StockerBundle.message("paper.buy")
            e.presentation.icon = AllIcons.General.Add
        }

        override fun actionPerformed(e: AnActionEvent) = buy()

        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
    }

    private inner class SellAction : AnAction() {
        override fun update(e: AnActionEvent) {
            e.presentation.text = StockerBundle.message("paper.sell")
            e.presentation.icon = AllIcons.General.Remove
            e.presentation.isEnabled = setting.paperPositions().isNotEmpty()
        }

        override fun actionPerformed(e: AnActionEvent) = sell()

        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
    }

    private inner class ResetAction : AnAction() {
        override fun update(e: AnActionEvent) {
            e.presentation.text = StockerBundle.message("paper.reset")
            e.presentation.icon = AllIcons.Actions.Rollback
        }

        override fun actionPerformed(e: AnActionEvent) {
            val amount = amountField.text.trim().toDoubleOrNull()
            if (amount == null || amount <= 0.0) {
                Messages.showInfoMessage(StockerBundle.message("paper.amount.invalid"), StockerBundle.message("paper.tab"))
                return
            }
            val confirm = Messages.showYesNoDialog(
                StockerBundle.message("paper.reset.confirm", formatMoney(amount)),
                StockerBundle.message("paper.tab"),
                Messages.getQuestionIcon(),
            )
            if (confirm != Messages.YES) {
                return
            }
            setting.paperReset(amount)
            refresh()
        }

        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
    }

    private class TradeDialog(
        choices: List<SymbolChoice>,
        selected: String?,
        private val buy: Boolean,
        private val maxShares: Int? = null,
    ) : DialogWrapper(null) {
        private val combo = ComboBox(choices.toTypedArray())
        private val sharesField = JBTextField(defaultShares(buy, maxShares))
        private val hint = JBLabel(" ")
        var code: String = ""
        var shares: Int = 0

        init {
            title = StockerBundle.message(if (buy) "paper.buy" else "paper.sell")
            combo.selectedItem = choices.firstOrNull { it.code == selected }
            combo.addActionListener { updateSellHint() }
            updateSellHint()
            init()
        }

        override fun createCenterPanel(): javax.swing.JComponent {
            val panel = JPanel(BorderLayout(0, 8))
            panel.preferredSize = Dimension(280, 90)
            panel.add(combo, BorderLayout.NORTH)
            panel.add(sharesField, BorderLayout.CENTER)
            panel.add(hint, BorderLayout.SOUTH)
            return panel
        }

        private fun updateSellHint() {
            if (buy) {
                hint.text = " "
                return
            }
            val chosen = combo.selectedItem as? SymbolChoice
            val sellable = chosen?.code?.let { StockerSetting.instance.paperSellableShares(it) } ?: maxShares ?: 0
            val locked = chosen?.code?.let { code ->
                val position = StockerSetting.instance.paperPositions().firstOrNull { it.code == code }
                if (position == null) 0 else position.shares - sellable
            } ?: 0
            hint.text = if (locked > 0) {
                StockerBundle.message("paper.t1.available", sellable, locked)
            } else {
                StockerBundle.message("paper.sellable", sellable)
            }
        }

        override fun doOKAction() {
            val chosen = combo.selectedItem as? SymbolChoice
            val count = sharesField.text.trim().toIntOrNull()
            if (chosen == null || count == null || count <= 0) {
                return
            }
            if (!buy) {
                val position = StockerSetting.instance.paperPositions().firstOrNull { it.code == chosen.code }
                val sellable = StockerSetting.instance.paperSellableShares(chosen.code)
                if (count > sellable) {
                    val message = if (position != null && sellable < position.shares) {
                        StockerBundle.message("paper.t1.block")
                    } else {
                        StockerBundle.message("paper.not.enough.shares")
                    }
                    Messages.showInfoMessage(message, StockerBundle.message("paper.tab"))
                    return
                }
            }
            code = chosen.code
            shares = count
            super.doOKAction()
        }

        companion object {
            private fun defaultShares(buy: Boolean, maxShares: Int?): String {
                if (buy) {
                    return "100"
                }
                return maxShares?.takeIf { it > 0 }?.toString() ?: "100"
            }
        }
    }

    private data class SymbolChoice(val code: String, val label: String) {
        override fun toString(): String = label
    }

    companion object {
        private fun columnNames(): Array<String> = arrayOf(
            StockerBundle.message("paper.column.code"),
            StockerBundle.message("paper.column.name"),
            StockerBundle.message("paper.column.shares"),
            StockerBundle.message("paper.column.cost"),
            StockerBundle.message("paper.column.price"),
            StockerBundle.message("paper.column.today.percent"),
            StockerBundle.message("paper.column.today.amount"),
            StockerBundle.message("paper.column.pnl"),
        )

        private fun formatMoney(value: Double): String = String.format(Locale.US, "%.2f", value)

        private fun formatPrice(value: Double): String = String.format(Locale.US, "%.3f", value)

        private fun formatSigned(value: Double): String = String.format(Locale.US, "%+.2f", value)

        private fun formatPercent(value: Double): String = String.format(Locale.US, "%+.2f%%", value * 100.0)

        private fun formatPnl(amount: Double, cost: Double): String {
            val pct = if (cost > 0.0) amount / cost else 0.0
            return "${formatSigned(amount)} (${formatPercent(pct)})"
        }
    }
}
