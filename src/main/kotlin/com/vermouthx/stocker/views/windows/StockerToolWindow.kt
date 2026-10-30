package com.vermouthx.stocker.views.windows

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.content.ContentManager
import com.intellij.util.messages.MessageBusConnection
import com.vermouthx.stocker.StockerApp
import com.vermouthx.stocker.StockerAppManager
import com.vermouthx.stocker.StockerBundle
import com.vermouthx.stocker.enums.StockerMarketType
import com.vermouthx.stocker.listeners.StockerGroupQuoteFilterListener
import com.vermouthx.stocker.listeners.StockerGroupsChangedNotifier
import com.vermouthx.stocker.listeners.StockerQuoteDeleteListener
import com.vermouthx.stocker.listeners.StockerQuoteDeleteNotifier.*
import com.vermouthx.stocker.listeners.StockerQuoteReloadListener
import com.vermouthx.stocker.listeners.StockerQuoteReloadNotifier.*
import com.vermouthx.stocker.listeners.StockerQuoteUpdateListener
import com.vermouthx.stocker.listeners.StockerQuoteUpdateNotifier.*
import com.vermouthx.stocker.settings.StockerSetting

class StockerToolWindow : ToolWindowFactory {

    private val messageBus = ApplicationManager.getApplication().messageBus

    private lateinit var allView: StockerSimpleToolWindow
    private lateinit var tabViewMap: Map<StockerMarketType, StockerSimpleToolWindow>
    private lateinit var myApplication: StockerApp
    private val messageBusConnections = mutableListOf<MessageBusConnection>()
    private val groupViews: MutableMap<String, StockerSimpleToolWindow> = mutableMapOf()
    private val groupContents: MutableMap<String, Content> = mutableMapOf()
    private val groupMessageBusConnections: MutableList<MessageBusConnection> = mutableListOf()
    private var toolWindowRef: ToolWindow? = null
    private var disposed = false

    override fun init(toolWindow: ToolWindow) {
        super.init(toolWindow)
        toolWindowRef = toolWindow
        allView = StockerSimpleToolWindow()
        tabViewMap = mapOf(
            StockerMarketType.AShare to StockerSimpleToolWindow(StockerMarketType.AShare),
            StockerMarketType.HKStocks to StockerSimpleToolWindow(StockerMarketType.HKStocks),
            StockerMarketType.USStocks to StockerSimpleToolWindow(StockerMarketType.USStocks),
            StockerMarketType.Crypto to StockerSimpleToolWindow(StockerMarketType.Crypto),
        )
    }

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val contentManager = toolWindow.contentManager
        val contentFactory = ContentFactory.getInstance()
        
        val disposable = Disposer.newDisposable("StockerToolWindow")
        toolWindow.disposable.let { Disposer.register(it, disposable) }
        
        val allContent = contentFactory.createContent(allView.component, "ALL", false)
        contentManager.addContent(allContent)
        val aShareContent = contentFactory.createContent(
            tabViewMap[StockerMarketType.AShare]?.component, StockerMarketType.AShare.title, false
        )
        contentManager.addContent(aShareContent)
        val hkStocksContent = contentFactory.createContent(
            tabViewMap[StockerMarketType.HKStocks]?.component, StockerMarketType.HKStocks.title, false
        )
        contentManager.addContent(hkStocksContent)
        val usStocksContent = contentFactory.createContent(
            tabViewMap[StockerMarketType.USStocks]?.component, StockerMarketType.USStocks.title, false
        )
        contentManager.addContent(usStocksContent)
        val cryptoContent = contentFactory.createContent(
            tabViewMap[StockerMarketType.Crypto]?.component,
            StockerMarketType.Crypto.title,
            false
        )
        contentManager.addContent(cryptoContent)
        val screenPanel = StockerScreenPanel(project)
        Disposer.register(disposable, screenPanel)
        val screenContent = contentFactory.createContent(screenPanel, StockerBundle.message("screen.tab"), false)
        screenContent.putUserData(StockerScreenTab.KEY, true)
        contentManager.addContent(screenContent)
        val backtestPanel = StockerBacktestPanel(project)
        Disposer.register(disposable, backtestPanel)
        val backtestContent = contentFactory.createContent(backtestPanel, StockerBundle.message("backtest.tab"), false)
        contentManager.addContent(backtestContent)
        val paperPanel = StockerPaperPanel(project)
        Disposer.register(disposable, paperPanel)
        val paperContent = contentFactory.createContent(paperPanel, StockerBundle.message("paper.tab"), false)
        contentManager.addContent(paperContent)
        createGroupTabs(contentManager)
        this.subscribeMessage()
        
        Disposer.register(disposable) {
            cleanup()
        }
        
        myApplication = StockerAppManager.register(project)
        myApplication.schedule()
    }
    
    private fun cleanup() {
        disposed = true
        // Dispose all table views
        allView.tableView.dispose()
        tabViewMap.values.forEach { it.tableView.dispose() }
        groupViews.values.forEach { it.tableView.dispose() }
        groupViews.clear()
        groupContents.clear()

        // Disconnect all message bus connections
        messageBusConnections.forEach { it.disconnect() }
        messageBusConnections.clear()
        groupMessageBusConnections.forEach { it.disconnect() }
        groupMessageBusConnections.clear()
    }

    private fun createGroupTabs(contentManager: ContentManager) {
        val setting = StockerSetting.instance
        val contentFactory = ContentFactory.getInstance()
        setting.customGroupNames().sorted().forEach { groupName ->
            val groupView = StockerSimpleToolWindow()
            groupViews[groupName] = groupView

            val content = contentFactory.createContent(groupView.component, groupName, false)
            contentManager.addContent(content)
            groupContents[groupName] = content

            val updateListener = StockerGroupQuoteFilterListener.Update(groupView.tableView, groupName)
            val deleteListener = StockerGroupQuoteFilterListener.Delete(groupView.tableView)
            val reloadListener = StockerGroupQuoteFilterListener.Reload(groupView.tableView)

            groupMessageBusConnections.add(messageBus.connect().apply {
                subscribe(STOCK_ALL_QUOTE_UPDATE_TOPIC, updateListener)
            })
            groupMessageBusConnections.add(messageBus.connect().apply {
                subscribe(STOCK_ALL_QUOTE_DELETE_TOPIC, deleteListener)
            })
            groupMessageBusConnections.add(messageBus.connect().apply {
                subscribe(STOCK_ALL_QUOTE_RELOAD_TOPIC, reloadListener)
            })
        }
    }

    private fun rebuildGroupTabs(contentManager: ContentManager) {
        groupContents.values.toList().forEach { contentManager.removeContent(it, true) }
        groupContents.clear()

        groupViews.values.forEach { it.tableView.dispose() }
        groupViews.clear()

        groupMessageBusConnections.forEach { it.disconnect() }
        groupMessageBusConnections.clear()

        createGroupTabs(contentManager)
    }

    private fun subscribeMessage() {
        messageBusConnections.add(messageBus.connect().apply {
            subscribe(StockerGroupsChangedNotifier.STOCKER_GROUPS_CHANGED_TOPIC, object : StockerGroupsChangedNotifier {
                override fun groupsChanged() {
                    val tw = toolWindowRef ?: return
                    ApplicationManager.getApplication().invokeLater {
                        if (disposed) return@invokeLater
                        rebuildGroupTabs(tw.contentManager)
                    }
                }
            })
        })

        // Create and store connections for proper disposal
        messageBusConnections.add(messageBus.connect().apply {
            subscribe(STOCK_ALL_QUOTE_UPDATE_TOPIC, StockerQuoteUpdateListener(allView.tableView))
        })
        messageBusConnections.add(messageBus.connect().apply {
            subscribe(STOCK_ALL_QUOTE_DELETE_TOPIC, StockerQuoteDeleteListener(allView.tableView))
        })
        messageBusConnections.add(messageBus.connect().apply {
            subscribe(STOCK_ALL_QUOTE_RELOAD_TOPIC, StockerQuoteReloadListener(allView.tableView))
        })
        
        tabViewMap.forEach { (market, myTableView) ->
            when (market) {
                StockerMarketType.AShare -> {
                    messageBusConnections.add(messageBus.connect().apply {
                        subscribe(STOCK_CN_QUOTE_UPDATE_TOPIC, StockerQuoteUpdateListener(myTableView.tableView))
                    })
                    messageBusConnections.add(messageBus.connect().apply {
                        subscribe(STOCK_CN_QUOTE_DELETE_TOPIC, StockerQuoteDeleteListener(myTableView.tableView))
                    })
                    messageBusConnections.add(messageBus.connect().apply {
                        subscribe(STOCK_CN_QUOTE_RELOAD_TOPIC, StockerQuoteReloadListener(myTableView.tableView))
                    })
                }

                StockerMarketType.HKStocks -> {
                    messageBusConnections.add(messageBus.connect().apply {
                        subscribe(STOCK_HK_QUOTE_UPDATE_TOPIC, StockerQuoteUpdateListener(myTableView.tableView))
                    })
                    messageBusConnections.add(messageBus.connect().apply {
                        subscribe(STOCK_HK_QUOTE_DELETE_TOPIC, StockerQuoteDeleteListener(myTableView.tableView))
                    })
                    messageBusConnections.add(messageBus.connect().apply {
                        subscribe(STOCK_HK_QUOTE_RELOAD_TOPIC, StockerQuoteReloadListener(myTableView.tableView))
                    })
                }

                StockerMarketType.USStocks -> {
                    messageBusConnections.add(messageBus.connect().apply {
                        subscribe(STOCK_US_QUOTE_UPDATE_TOPIC, StockerQuoteUpdateListener(myTableView.tableView))
                    })
                    messageBusConnections.add(messageBus.connect().apply {
                        subscribe(STOCK_US_QUOTE_DELETE_TOPIC, StockerQuoteDeleteListener(myTableView.tableView))
                    })
                    messageBusConnections.add(messageBus.connect().apply {
                        subscribe(STOCK_US_QUOTE_RELOAD_TOPIC, StockerQuoteReloadListener(myTableView.tableView))
                    })
                }

                StockerMarketType.Crypto -> {
                    messageBusConnections.add(messageBus.connect().apply {
                        subscribe(CRYPTO_QUOTE_UPDATE_TOPIC, StockerQuoteUpdateListener(myTableView.tableView))
                    })
                    messageBusConnections.add(messageBus.connect().apply {
                        subscribe(CRYPTO_QUOTE_DELETE_TOPIC, StockerQuoteDeleteListener(myTableView.tableView))
                    })
                    messageBusConnections.add(messageBus.connect().apply {
                        subscribe(STOCK_CRYPTO_QUOTE_RELOAD_TOPIC, StockerQuoteReloadListener(myTableView.tableView))
                    })
                }
            }
        }
    }
}
