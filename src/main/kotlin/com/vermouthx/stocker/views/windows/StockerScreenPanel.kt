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
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.util.Key
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import com.vermouthx.stocker.StockerAppManager
import com.vermouthx.stocker.StockerBundle
import com.vermouthx.stocker.entities.StockerScreenRow
import com.vermouthx.stocker.entities.StockerScreenSnapshot
import com.vermouthx.stocker.entities.StockerSuggestion
import com.vermouthx.stocker.enums.StockerMarketType
import com.vermouthx.stocker.listeners.StockerScreenNotifier
import com.vermouthx.stocker.screen.ScreenStrategy
import com.vermouthx.stocker.screen.StockerScreenService
import com.vermouthx.stocker.screen.TangChaoScreen
import com.vermouthx.stocker.settings.StockerSetting
import com.vermouthx.stocker.utils.StockerActionUtil
import com.vermouthx.stocker.views.dialogs.StockerAiSettingDialog
import com.vermouthx.stocker.views.dialogs.StockerScreenDetailDialog
import java.awt.BorderLayout
import java.awt.event.MouseAdapter
import java.awt.Color
import java.awt.Component
import java.awt.event.MouseEvent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.ListSelectionModel
import javax.swing.ToolTipManager
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.DefaultTableModel
import javax.swing.table.TableRowSorter

object StockerScreenTab {
    val KEY: Key<Boolean> = Key.create("StockerScreenTab")
}

class StockerScreenPanel(private val project: Project) : SimpleToolWindowPanel(true), Disposable {

    private val tableModel = ScreenTableModel()
    private val table = object : JBTable(tableModel) {
        override fun getToolTipText(event: MouseEvent): String? {
            val viewRow = rowAtPoint(event.point)
            if (viewRow < 0) {
                return null
            }
            val modelRow = convertRowIndexToModel(viewRow)
            return rows.getOrNull(modelRow)?.let { detailText(it) }
        }
    }
    private val status = JBLabel()
    private val rows = mutableListOf<StockerScreenRow>()
    private val autoCheck = JBCheckBox(StockerBundle.message("screen.auto.short"))
    private val strategyBox = ComboBox(strategyItems())
    private var suppressStrategy = false
    private var shownStrategy = StockerSetting.instance.screenStrategy

    init {
        val actionGroup = DefaultActionGroup(RefreshAction(), AddAction(), PaperBuyAction(), DetailAction(), AiSettingAction(), HelpAction())
        val toolbar = ActionManager.getInstance().createActionToolbar("StockerScreen", actionGroup, true)
        toolbar.targetComponent = table
        val toolbarPanel = JPanel(BorderLayout())
        toolbarPanel.add(toolbar.component, BorderLayout.CENTER)
        strategyBox.selectedItem = strategyItems().firstOrNull { it.id == shownStrategy }
        strategyBox.addActionListener {
            if (suppressStrategy) {
                return@addActionListener
            }
            val selected = strategyBox.selectedItem as? StrategyItem ?: return@addActionListener
            if (selected.id == StockerSetting.instance.screenStrategy) {
                return@addActionListener
            }
            StockerSetting.instance.screenStrategy = selected.id
            shownStrategy = selected.id
            applyStrategyChrome(selected.id)
            StockerScreenService.instance.refreshNow()
        }
        toolbarPanel.add(strategyBox, BorderLayout.EAST)
        this.toolbar = toolbarPanel

        ToolTipManager.sharedInstance().registerComponent(table)
        table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION)
        table.autoResizeMode = JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS
        table.fillsViewportHeight = true
        table.setStriped(true)
        table.columnModel.getColumn(2).cellRenderer = ZoneRenderer()
        val number = NumberRenderer("%.1f")
        val ratio = NumberRenderer("%.2f")
        table.columnModel.getColumn(3).cellRenderer = number
        table.columnModel.getColumn(4).cellRenderer = ratio
        table.columnModel.getColumn(5).cellRenderer = number
        table.columnModel.getColumn(6).cellRenderer = number
        table.columnModel.getColumn(7).cellRenderer = ratio
        intArrayOf(96, 88, 84, 64, 64, 72, 60, 60).forEachIndexed { index, width ->
            val column = table.columnModel.getColumn(index)
            column.minWidth = 48
            column.preferredWidth = width
        }
        val sorter = TableRowSorter(tableModel)
        sorter.setComparator(2, compareBy<String> { zoneRank(it) })
        table.rowSorter = sorter
        table.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(event: MouseEvent) {
                if (event.clickCount == 2 && table.rowAtPoint(event.point) >= 0) {
                    openDetail()
                }
            }
        })

        autoCheck.isSelected = StockerSetting.instance.screenAuto
        autoCheck.toolTipText = StockerBundle.message("screen.auto")
        autoCheck.addActionListener {
            val enabled = autoCheck.isSelected
            StockerSetting.instance.screenAuto = enabled
            if (enabled && StockerSetting.instance.screenLastDay != todayShanghai()) {
                StockerScreenService.instance.refreshNow()
            }
        }

        val south = JPanel(BorderLayout())
        south.border = JBUI.Borders.empty(2, 8)
        status.toolTipText = StockerBundle.message(hintKey(shownStrategy))
        applyStrategyChrome(shownStrategy)
        south.add(status, BorderLayout.CENTER)
        south.add(autoCheck, BorderLayout.EAST)

        val center = JPanel(BorderLayout())
        center.add(JBScrollPane(table), BorderLayout.CENTER)
        center.add(south, BorderLayout.SOUTH)
        setContent(center)

        ApplicationManager.getApplication().messageBus.connect(this).subscribe(
            StockerScreenNotifier.TOPIC,
            object : StockerScreenNotifier {
                override fun onScreenChanged(snapshot: StockerScreenSnapshot) {
                    ApplicationManager.getApplication().invokeLater { render(snapshot) }
                }
            }
        )
        render(StockerScreenService.instance.snapshot)
    }

    private fun applyStrategyChrome(strategyId: String) {
        table.columnModel.getColumn(4).headerValue = StockerBundle.message(scoreKey(strategyId))
        table.tableHeader.repaint()
        status.toolTipText = StockerBundle.message(hintKey(strategyId))
        val pattern = if (strategyId == ScreenStrategy.MAGIC.id) "%.0f" else "%.1f"
        table.columnModel.getColumn(4).cellRenderer = NumberRenderer(pattern)
        suppressStrategy = true
        strategyBox.selectedItem = strategyItems().firstOrNull { it.id == strategyId }
        suppressStrategy = false
    }

    private fun render(snapshot: StockerScreenSnapshot) {
        shownStrategy = snapshot.strategy
        applyStrategyChrome(snapshot.strategy)
        val selected = selectedCodes()
        rows.clear()
        rows.addAll(snapshot.rows)
        tableModel.rowCount = 0
        snapshot.rows.forEach { row ->
            tableModel.addRow(
                arrayOf<Any>(
                    row.code,
                    row.name,
                    row.zone,
                    row.pe,
                    row.capToBuy,
                    row.minRoe,
                    row.debtRatio,
                    row.cashRatio,
                )
            )
        }
        restoreSelection(selected)
        status.text = when {
            snapshot.running -> StockerBundle.message("screen.status.running")
            snapshot.error != null -> StockerBundle.message("screen.status.error", snapshot.error)
            snapshot.rows.isEmpty() -> StockerBundle.message("screen.status.empty")
            else -> {
                val whenText = if (snapshot.ranAtMillis <= 0) {
                    ""
                } else {
                    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(snapshot.ranAtMillis))
                }
                val buys = snapshot.rows.count { it.zone == TangChaoScreen.ZONE_BUY }
                StockerBundle.message("screen.status.ready", whenText, snapshot.rows.size, buys)
            }
        }
    }

    private fun openDetail() {
        val row = selectedRows().firstOrNull() ?: return
        StockerScreenDetailDialog(project, row, shownStrategy).show()
    }

    private fun selectedCodes(): Set<String> = selectedRows().map { it.code }.toSet()

    private fun selectedRows(): List<StockerScreenRow> {
        val picked = ArrayList<StockerScreenRow>()
        for (viewIndex in table.selectedRows) {
            if (viewIndex < 0) {
                continue
            }
            val modelIndex = table.convertRowIndexToModel(viewIndex)
            rows.getOrNull(modelIndex)?.let { picked.add(it) }
        }
        return picked
    }

    private fun restoreSelection(codes: Set<String>) {
        if (codes.isEmpty()) {
            return
        }
        table.clearSelection()
        for (modelIndex in rows.indices) {
            if (rows[modelIndex].code in codes) {
                val viewIndex = table.convertRowIndexToView(modelIndex)
                if (viewIndex >= 0) {
                    table.addRowSelectionInterval(viewIndex, viewIndex)
                }
            }
        }
    }

    private fun detailText(row: StockerScreenRow): String {
        val parts = mutableListOf(
            StockerBundle.message(
                "screen.detail.main",
                row.industry.ifBlank { "-" },
                formatNumber(row.price),
                formatYi(row.marketCapYi),
                formatNumber(row.minGrossMargin),
            )
        )
        when (row.growthAdjustment) {
            TangChaoScreen.GROWTH_CAPPED -> parts += StockerBundle.message("screen.note.growth.capped")
            TangChaoScreen.GROWTH_FLOOR -> parts += StockerBundle.message("screen.note.growth.floor")
            TangChaoScreen.GROWTH_MISSING -> parts += StockerBundle.message("screen.note.growth.missing")
            else -> parts += StockerBundle.message("screen.note.growth.raw", formatNumber(row.rawGrowth))
        }
        if (row.cashDiscount) {
            parts += StockerBundle.message("screen.note.cash")
        }
        if (row.leverageDiscount) {
            parts += StockerBundle.message("screen.note.leverage")
        }
        parts += StockerBundle.message("screen.detail.value", formatYi(row.buyCapYi), formatYi(row.fairCapYi))
        return parts.joinToString("  ")
    }

    override fun dispose() = Unit

    private inner class RefreshAction : AnAction() {
        override fun update(e: AnActionEvent) {
            e.presentation.text = StockerBundle.message("screen.action.refresh")
            e.presentation.description = StockerBundle.message("screen.action.refresh.description")
            e.presentation.icon = AllIcons.Actions.Refresh
            e.presentation.isEnabled = !StockerScreenService.instance.snapshot.running
        }

        override fun actionPerformed(e: AnActionEvent) {
            StockerScreenService.instance.refreshNow()
        }

        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
    }

    private inner class AiSettingAction : AnAction() {
        override fun update(e: AnActionEvent) {
            e.presentation.text = StockerBundle.message("ai.settings.action")
            e.presentation.description = StockerBundle.message("ai.settings.action.description")
            e.presentation.icon = AllIcons.General.Settings
        }

        override fun actionPerformed(e: AnActionEvent) {
            StockerAiSettingDialog(project).show()
        }

        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
    }

    private inner class DetailAction : AnAction() {
        override fun update(e: AnActionEvent) {
            e.presentation.text = StockerBundle.message("screen.action.detail")
            e.presentation.description = StockerBundle.message("screen.action.detail.description")
            e.presentation.icon = AllIcons.Actions.Preview
            e.presentation.isEnabled = selectedRows().isNotEmpty()
        }

        override fun actionPerformed(e: AnActionEvent) {
            openDetail()
        }

        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
    }

    private inner class PaperBuyAction : AnAction() {
        override fun update(e: AnActionEvent) {
            e.presentation.text = StockerBundle.message("screen.action.paper")
            e.presentation.description = StockerBundle.message("screen.action.paper.description")
            e.presentation.icon = AllIcons.Actions.Lightning
            e.presentation.isEnabled = selectedRows().isNotEmpty()
        }

        override fun actionPerformed(e: AnActionEvent) {
            val pending = selectedRows()
            if (pending.isEmpty()) {
                return
            }
            var ok = 0
            var failed = 0
            pending.forEach { row ->
                if (row.price <= 0.0) {
                    failed++
                    return@forEach
                }
                val error = StockerSetting.instance.paperBuy(row.code, row.name, PAPER_LOT_SIZE, row.price)
                if (error == null) {
                    ok++
                } else {
                    failed++
                }
            }
            val message = when {
                ok > 0 && failed > 0 -> StockerBundle.message("paper.buy.partial", ok, failed)
                ok > 0 -> StockerBundle.message("paper.buy.ok", ok)
                else -> StockerBundle.message("paper.buy.none")
            }
            Messages.showInfoMessage(message, StockerBundle.message("paper.tab"))
        }

        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
    }

    private inner class AddAction : AnAction() {
        override fun update(e: AnActionEvent) {
            e.presentation.text = StockerBundle.message("screen.action.add")
            e.presentation.description = StockerBundle.message("screen.action.add.description")
            e.presentation.icon = AllIcons.General.Add
            e.presentation.isEnabled = selectedRows().any { !StockerSetting.instance.containsCode(it.code) }
        }

        override fun actionPerformed(e: AnActionEvent) {
            val pending = selectedRows().filter { !StockerSetting.instance.containsCode(it.code) }
            if (pending.isEmpty()) {
                return
            }
            val app = StockerAppManager.myApplication(project)
            app?.shutdownThenClear()
            pending.forEach { row ->
                StockerActionUtil.addStock(
                    StockerMarketType.AShare,
                    StockerSuggestion(row.code, row.name, StockerMarketType.AShare),
                    project,
                )
            }
            app?.schedule()
        }

        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
    }

    private class HelpAction : AnAction() {
        override fun update(e: AnActionEvent) {
            e.presentation.text = StockerBundle.message("screen.action.help")
            e.presentation.description = StockerBundle.message("screen.action.help.description")
            e.presentation.icon = AllIcons.Actions.Help
        }

        override fun actionPerformed(e: AnActionEvent) {
            val strategyId = StockerSetting.instance.screenStrategy
            Messages.showInfoMessage(
                e.project,
                StockerBundle.message(hintKey(strategyId)),
                StockerBundle.message("screen.hint.title"),
            )
        }

        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
    }

    private class ScreenTableModel : DefaultTableModel(columnNames(), 0) {
        override fun isCellEditable(row: Int, column: Int): Boolean = false

        override fun getColumnClass(column: Int): Class<*> = when (column) {
            3, 4, 5, 6, 7 -> java.lang.Double::class.java
            else -> String::class.java
        }
    }

    private class NumberRenderer(private val pattern: String) : DefaultTableCellRenderer() {
        init {
            horizontalAlignment = RIGHT
        }

        override fun setValue(value: Any?) {
            text = if (value is Number) String.format(Locale.US, pattern, value.toDouble()) else ""
        }
    }

    private class ZoneRenderer : DefaultTableCellRenderer() {
        override fun getTableCellRendererComponent(
            table: JTable, value: Any?, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int,
        ): Component {
            val component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column) as JLabel
            val zone = value as? String
            component.text = zoneLabel(zone)
            component.horizontalAlignment = CENTER
            if (!isSelected) {
                component.foreground = when (zone) {
                    TangChaoScreen.ZONE_BUY -> JBColor(Color(0x1B5E20), Color(0xA5D6A7))
                    TangChaoScreen.ZONE_SELL -> JBColor(Color(0xB71C1C), Color(0xEF9A9A))
                    else -> table.foreground
                }
            }
            return component
        }
    }

    companion object {
        private fun columnNames(): Array<String> = arrayOf(
            StockerBundle.message("screen.column.code"),
            StockerBundle.message("screen.column.name"),
            StockerBundle.message("screen.column.zone"),
            StockerBundle.message("screen.column.pe"),
            StockerBundle.message("screen.column.cap.to.buy"),
            StockerBundle.message("screen.column.min.roe"),
            StockerBundle.message("screen.column.debt"),
            StockerBundle.message("screen.column.cash"),
        )

        private fun zoneRank(zone: String): Int = when (zone) {
            TangChaoScreen.ZONE_BUY -> 0
            TangChaoScreen.ZONE_HOLD -> 1
            else -> 2
        }

        private fun zoneLabel(zone: String?): String = when (zone) {
            TangChaoScreen.ZONE_BUY -> StockerBundle.message("screen.zone.buy")
            TangChaoScreen.ZONE_HOLD -> StockerBundle.message("screen.zone.hold")
            TangChaoScreen.ZONE_SELL -> StockerBundle.message("screen.zone.sell")
            else -> zone.orEmpty()
        }

        private fun formatNumber(value: Double): String = String.format(Locale.US, "%.1f", value)

        private fun formatYi(value: Double): String {
            return if (value >= 100) String.format(Locale.US, "%.0f", value) else String.format(Locale.US, "%.1f", value)
        }

        private const val PAPER_LOT_SIZE = 100

        private fun todayShanghai(): String {
            return java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")).toString()
        }

        private fun hintKey(strategyId: String): String = when (strategyId) {
            ScreenStrategy.GRAHAM.id -> "screen.hint.graham"
            ScreenStrategy.MAGIC.id -> "screen.hint.magic"
            ScreenStrategy.QUALITY.id -> "screen.hint.quality"
            else -> "screen.hint.tang"
        }

        private fun scoreKey(strategyId: String): String = when (strategyId) {
            ScreenStrategy.GRAHAM.id -> "screen.column.score.graham"
            ScreenStrategy.MAGIC.id -> "screen.column.score.magic"
            ScreenStrategy.QUALITY.id -> "screen.column.score.quality"
            else -> "screen.column.score.tang"
        }

        private fun strategyItems(): Array<StrategyItem> = arrayOf(
            StrategyItem(ScreenStrategy.TANG.id, StockerBundle.message("screen.strategy.tang")),
            StrategyItem(ScreenStrategy.GRAHAM.id, StockerBundle.message("screen.strategy.graham")),
            StrategyItem(ScreenStrategy.MAGIC.id, StockerBundle.message("screen.strategy.magic")),
            StrategyItem(ScreenStrategy.QUALITY.id, StockerBundle.message("screen.strategy.quality")),
        )
    }
}

private data class StrategyItem(val id: String, val label: String) {
    override fun toString(): String = label
}
