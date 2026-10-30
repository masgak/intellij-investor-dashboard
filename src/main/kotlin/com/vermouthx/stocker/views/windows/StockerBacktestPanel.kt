package com.vermouthx.stocker.views.windows

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import com.vermouthx.stocker.StockerBundle
import com.vermouthx.stocker.backtest.BacktestEngine
import com.vermouthx.stocker.backtest.BacktestKlineClient
import com.vermouthx.stocker.backtest.BacktestReport
import com.vermouthx.stocker.backtest.BacktestStrategies
import com.vermouthx.stocker.backtest.SIDE_BUY
import com.vermouthx.stocker.settings.StockerSetting
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JPanel
import javax.swing.ListSelectionModel
import javax.swing.table.DefaultTableModel

class StockerBacktestPanel(private val project: Project) : SimpleToolWindowPanel(true), Disposable {

    private val log = Logger.getInstance(javaClass)

    private val strategyBox = ComboBox(strategyItems())
    private val yearBox = ComboBox(arrayOf(1, 3, 5))
    private val symbolField = JBTextField(10)
    private val status = JBLabel(" ")
    private val tableModel = DefaultTableModel()
    private val table = JBTable(tableModel)
    private val running = AtomicBoolean(false)

    init {
        yearBox.selectedItem = 3
        strategyBox.selectedIndex = 1
        val firstCode = StockerSetting.instance.aShareList.firstOrNull().orEmpty()
        symbolField.text = firstCode
        symbolField.emptyText.text = StockerBundle.message("backtest.symbol.prompt")

        val actions = DefaultActionGroup(RunOneAction(), RunFavoritesAction(), HelpAction())
        val toolbar = ActionManager.getInstance().createActionToolbar("StockerBacktest", actions, true)
        toolbar.targetComponent = table
        val controls = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(6), 0))
        controls.add(strategyBox)
        controls.add(yearBox)
        controls.add(symbolField)
        val north = JPanel(BorderLayout())
        north.add(toolbar.component, BorderLayout.WEST)
        north.add(controls, BorderLayout.CENTER)

        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
        table.setStriped(true)
        table.fillsViewportHeight = true
        showTrades(emptyList())

        val south = JPanel(BorderLayout())
        south.border = JBUI.Borders.empty(4, 8)
        south.add(status, BorderLayout.CENTER)

        val center = JPanel(BorderLayout())
        center.add(north, BorderLayout.NORTH)
        center.add(JBScrollPane(table), BorderLayout.CENTER)
        center.add(south, BorderLayout.SOUTH)
        setContent(center)
    }

    private fun years(): Int = (yearBox.selectedItem as? Int) ?: 3

    private fun strategy() = BacktestStrategies.fromId((strategyBox.selectedItem as StrategyChoice).id)

    private fun runCodes(codes: List<String>) {
        if (!running.compareAndSet(false, true)) {
            return
        }
        val years = years()
        val strategy = strategy()
        status.text = StockerBundle.message("backtest.status.running")
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                var skipped = 0
                val reports = codes.mapNotNull { code ->
                    try {
                        val bars = BacktestKlineClient.load(code, years)
                        if (bars.size < 30) {
                            skipped++
                            return@mapNotNull null
                        }
                        BacktestEngine.run(code, bars, strategy)
                    } catch (e: Exception) {
                        skipped++
                        log.warn("Backtest skipped $code", e)
                        null
                    }
                }
                val missed = skipped
                ApplicationManager.getApplication().invokeLater {
                    if (project.isDisposed) {
                        return@invokeLater
                    }
                    if (reports.isEmpty()) {
                        showTrades(emptyList())
                        status.text = StockerBundle.message("backtest.status.empty")
                    } else if (reports.size == 1 && codes.size == 1) {
                        showTrades(reports.first())
                    } else {
                        showPortfolio(reports, missed)
                    }
                }
            } catch (e: Exception) {
                ApplicationManager.getApplication().invokeLater {
                    if (!project.isDisposed) {
                        status.text = StockerBundle.message("backtest.status.error", e.message ?: e.javaClass.simpleName)
                    }
                }
            } finally {
                running.set(false)
            }
        }
    }

    private fun showTrades(report: BacktestReport) {
        val model = DefaultTableModel(
            arrayOf(
                StockerBundle.message("backtest.column.date"),
                StockerBundle.message("backtest.column.side"),
                StockerBundle.message("backtest.column.shares"),
                StockerBundle.message("backtest.column.price"),
                StockerBundle.message("backtest.column.fee"),
            ),
            0,
        )
        report.trades.forEach { fill ->
            model.addRow(
                arrayOf<Any>(
                    fill.date,
                    if (fill.side == SIDE_BUY) StockerBundle.message("backtest.side.buy") else StockerBundle.message("backtest.side.sell"),
                    fill.shares,
                    String.format(Locale.US, "%.2f", fill.price),
                    String.format(Locale.US, "%.2f", fill.fee),
                )
            )
        }
        table.model = model
        status.text = summary(report)
    }

    private fun showTrades(rows: List<Nothing>) {
        table.model = DefaultTableModel(arrayOf<Any>(), 0)
        if (rows.isEmpty()) {
            status.text = " "
        }
    }

    private fun showPortfolio(reports: List<BacktestReport>, skipped: Int) {
        val model = DefaultTableModel(
            arrayOf(
                StockerBundle.message("backtest.column.code"),
                StockerBundle.message("backtest.column.return"),
                StockerBundle.message("backtest.column.annualized"),
                StockerBundle.message("backtest.column.drawdown"),
                StockerBundle.message("backtest.column.trades"),
                StockerBundle.message("backtest.column.win"),
            ),
            0,
        )
        reports.sortedByDescending { it.totalReturn }.forEach { report ->
            model.addRow(
                arrayOf<Any>(
                    report.code,
                    percent(report.totalReturn),
                    percent(report.annualized),
                    percent(report.maxDrawdown),
                    report.trades.size,
                    percent(report.winRate),
                )
            )
        }
        table.model = model
        val average = reports.map { it.totalReturn }.average()
        status.text = StockerBundle.message("backtest.status.portfolio", reports.size, percent(average), skipped)
    }

    private fun summary(report: BacktestReport): String {
        return StockerBundle.message(
            "backtest.status.one",
            report.code,
            report.barCount,
            percent(report.totalReturn),
            percent(report.annualized),
            percent(report.maxDrawdown),
            report.trades.size,
            percent(report.winRate),
            String.format(Locale.US, "%.0f", report.endEquity),
        )
    }

    override fun dispose() = Unit

    private inner class RunOneAction : AnAction() {
        override fun update(e: AnActionEvent) {
            e.presentation.text = StockerBundle.message("backtest.action.run")
            e.presentation.icon = AllIcons.Actions.Execute
            e.presentation.isEnabled = !running.get() && symbolField.text.isNotBlank()
        }

        override fun actionPerformed(e: AnActionEvent) {
            runCodes(listOf(symbolField.text.trim()))
        }

        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
    }

    private inner class RunFavoritesAction : AnAction() {
        override fun update(e: AnActionEvent) {
            e.presentation.text = StockerBundle.message("backtest.action.favorites")
            e.presentation.icon = AllIcons.Actions.RunAll
            e.presentation.isEnabled = !running.get() && StockerSetting.instance.aShareList.isNotEmpty()
        }

        override fun actionPerformed(e: AnActionEvent) {
            runCodes(StockerSetting.instance.aShareList.toList())
        }

        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
    }

    private class HelpAction : AnAction() {
        override fun update(e: AnActionEvent) {
            e.presentation.text = StockerBundle.message("backtest.action.help")
            e.presentation.icon = AllIcons.Actions.Help
        }

        override fun actionPerformed(e: AnActionEvent) {
            Messages.showInfoMessage(
                e.project,
                StockerBundle.message("backtest.hint"),
                StockerBundle.message("backtest.hint.title"),
            )
        }

        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
    }

    private data class StrategyChoice(val id: String, val label: String) {
        override fun toString(): String = label
    }

    private fun strategyItems(): Array<StrategyChoice> = arrayOf(
        StrategyChoice(BacktestStrategies.buyHold.id, StockerBundle.message("backtest.strategy.hold")),
        StrategyChoice(BacktestStrategies.maCross.id, StockerBundle.message("backtest.strategy.ma")),
        StrategyChoice(BacktestStrategies.turtle.id, StockerBundle.message("backtest.strategy.turtle")),
    )

    private fun percent(value: Double): String = String.format(Locale.US, "%.1f%%", value * 100.0)
}
