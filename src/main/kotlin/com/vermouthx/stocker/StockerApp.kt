package com.vermouthx.stocker

import com.intellij.openapi.application.ApplicationManager
import com.vermouthx.stocker.entities.StockerQuote
import com.vermouthx.stocker.enums.StockerMarketIndex
import com.vermouthx.stocker.enums.StockerMarketType
import com.vermouthx.stocker.enums.StockerQuoteProvider
import com.vermouthx.stocker.listeners.StockerQuoteReloadNotifier.*
import com.vermouthx.stocker.listeners.StockerQuoteUpdateNotifier.*
import com.vermouthx.stocker.settings.StockerSetting
import com.vermouthx.stocker.utils.StockerProfileHttpUtil
import com.vermouthx.stocker.utils.StockerQuoteHttpUtil
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class StockerApp {

    private val setting = StockerSetting.instance
    private val messageBus = ApplicationManager.getApplication().messageBus

    private var scheduledExecutorService: ScheduledExecutorService = Executors.newScheduledThreadPool(1)
    private var scheduledFuture: ScheduledFuture<*>? = null

    private var scheduleInitialDelay: Long = 3
    @Volatile
    private var refreshActive: Boolean = false

    fun schedule() {
        synchronized(this) {
            val current = scheduledFuture
            if (refreshActive && current != null && !current.isCancelled && !current.isDone) {
                return
            }
            if (scheduledExecutorService.isShutdown) {
                scheduledExecutorService = Executors.newScheduledThreadPool(1)
            }
            refreshActive = true
            val delay = scheduleInitialDelay
            scheduleInitialDelay = 0
            val period = setting.refreshInterval.coerceAtLeast(1L)
            scheduledFuture = scheduledExecutorService.scheduleAtFixedRate(
                createConsolidatedUpdateThread(),
                delay,
                period,
                TimeUnit.SECONDS
            )
        }
    }

    fun shutdown() {
        synchronized(this) {
            refreshActive = false
            scheduledFuture?.cancel(false)
            scheduledFuture = null
        }
    }

    fun isShutdown(): Boolean {
        return !refreshActive
    }

    private fun clear() {
        messageBus.syncPublisher(STOCK_ALL_QUOTE_RELOAD_TOPIC).clear()
        messageBus.syncPublisher(STOCK_CN_QUOTE_RELOAD_TOPIC).clear()
        messageBus.syncPublisher(STOCK_HK_QUOTE_RELOAD_TOPIC).clear()
        messageBus.syncPublisher(STOCK_US_QUOTE_RELOAD_TOPIC).clear()
        messageBus.syncPublisher(STOCK_CRYPTO_QUOTE_RELOAD_TOPIC).clear()
    }

    fun shutdownThenClear() {
        shutdown()
        clear()
    }

    /**
     * Consolidated update thread that fetches all market data once and publishes to all relevant topics.
     */
    private fun createConsolidatedUpdateThread(): Runnable {
        return Runnable {
            if (!shouldContinueRefresh()) {
                return@Runnable
            }

            val quoteProvider = setting.quoteProvider
            val cryptoQuoteProvider = setting.cryptoQuoteProvider

            // Fetch all market data once
            val aShareQuotes = profile(StockerMarketType.AShare, fetchQuotesIfActive(StockerMarketType.AShare, quoteProvider, setting.aShareList)) ?: return@Runnable
            val hkStocksQuotes = profile(StockerMarketType.HKStocks, fetchQuotesIfActive(StockerMarketType.HKStocks, quoteProvider, setting.hkStocksList)) ?: return@Runnable
            val usStocksQuotes = profile(StockerMarketType.USStocks, fetchQuotesIfActive(StockerMarketType.USStocks, quoteProvider, setting.usStocksList)) ?: return@Runnable
            val cryptoQuotes = profile(StockerMarketType.Crypto, fetchQuotesIfActive(StockerMarketType.Crypto, cryptoQuoteProvider, setting.cryptoList)) ?: return@Runnable

            val aShareIndices = fetchQuotesIfActive(StockerMarketType.AShare, quoteProvider, StockerMarketIndex.CN.codes) ?: return@Runnable
            val hkStocksIndices = fetchQuotesIfActive(StockerMarketType.HKStocks, quoteProvider, StockerMarketIndex.HK.codes) ?: return@Runnable
            val usStocksIndices = fetchQuotesIfActive(StockerMarketType.USStocks, quoteProvider, StockerMarketIndex.US.codes) ?: return@Runnable
            val cryptoIndices = fetchQuotesIfActive(StockerMarketType.Crypto, cryptoQuoteProvider, StockerMarketIndex.Crypto.codes) ?: return@Runnable

            if (!shouldContinueRefresh()) {
                return@Runnable
            }

            // Publish to individual market topics
            val cnPublisher = messageBus.syncPublisher(STOCK_CN_QUOTE_UPDATE_TOPIC)
            if (setting.aShareList.isNotEmpty()) {
                cnPublisher.syncQuotes(aShareQuotes, setting.aShareList.size)
            }
            cnPublisher.syncIndices(aShareIndices)

            val hkPublisher = messageBus.syncPublisher(STOCK_HK_QUOTE_UPDATE_TOPIC)
            if (setting.hkStocksList.isNotEmpty()) {
                hkPublisher.syncQuotes(hkStocksQuotes, setting.hkStocksList.size)
            }
            hkPublisher.syncIndices(hkStocksIndices)

            val usPublisher = messageBus.syncPublisher(STOCK_US_QUOTE_UPDATE_TOPIC)
            if (setting.usStocksList.isNotEmpty()) {
                usPublisher.syncQuotes(usStocksQuotes, setting.usStocksList.size)
            }
            usPublisher.syncIndices(usStocksIndices)

            val cryptoPublisher = messageBus.syncPublisher(CRYPTO_QUOTE_UPDATE_TOPIC)
            if (setting.cryptoList.isNotEmpty()) {
                cryptoPublisher.syncQuotes(cryptoQuotes, setting.cryptoList.size)
            }
            cryptoPublisher.syncIndices(cryptoIndices)

            val allPublisher = messageBus.syncPublisher(STOCK_ALL_QUOTE_UPDATE_TOPIC)
            val allStockQuotes = listOf(aShareQuotes, hkStocksQuotes, usStocksQuotes, cryptoQuotes).flatten()
            val allStockIndices = listOf(aShareIndices, hkStocksIndices, usStocksIndices, cryptoIndices).flatten()
            allPublisher.syncQuotes(allStockQuotes, setting.allStockListSize)
            allPublisher.syncIndices(allStockIndices)
        }
    }

    private fun fetchQuotesIfActive(
        marketType: StockerMarketType,
        quoteProvider: StockerQuoteProvider,
        codes: List<String>
    ): List<StockerQuote>? {
        if (!shouldContinueRefresh()) {
            return null
        }
        return StockerQuoteHttpUtil.get(marketType, quoteProvider, codes)
    }

    private fun profile(marketType: StockerMarketType, quotes: List<StockerQuote>?): List<StockerQuote>? {
        if (quotes == null) {
            return null
        }
        return StockerProfileHttpUtil.apply(marketType, quotes)
    }

    private fun shouldContinueRefresh(): Boolean {
        return refreshActive && !Thread.currentThread().isInterrupted
    }

    fun dispose() {
        shutdown()
        scheduledExecutorService.shutdownNow()
        StockerQuoteHttpUtil.closeConnections()
    }
}
