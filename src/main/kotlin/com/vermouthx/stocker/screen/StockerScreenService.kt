package com.vermouthx.stocker.screen

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.vermouthx.stocker.entities.StockerScreenSnapshot
import com.vermouthx.stocker.listeners.StockerScreenNotifier
import com.vermouthx.stocker.settings.StockerScreenPickState
import com.vermouthx.stocker.settings.StockerSetting
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class StockerScreenService : Disposable {

    private val log = Logger.getInstance(javaClass)
    private val executor = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "stocker-tangchao-screen").apply { isDaemon = true }
    }
    private val disposed = AtomicBoolean(false)
    private val messageBus = ApplicationManager.getApplication().messageBus

    @Volatile
    private var lastFailureAt: Long = 0

    @Volatile
    var snapshot: StockerScreenSnapshot = StockerScreenSnapshot(emptyList(), 0, false, null)
        private set

    init {
        snapshot = loadSaved()
        scheduleNext()
    }

    fun refreshNow() {
        if (disposed.get()) {
            return
        }
        executor.execute {
            runScreen()
        }
    }

    private fun loadSaved(): StockerScreenSnapshot {
        return try {
            val setting = StockerSetting.instance
            StockerScreenSnapshot(
                rows = setting.savedScreenRows(),
                ranAtMillis = setting.screenRanAt,
                running = false,
                error = null,
                strategy = setting.screenStrategy,
            )
        } catch (e: Exception) {
            log.warn("Unable to load the saved screen", e)
            StockerScreenSnapshot(emptyList(), 0, false, null)
        }
    }

    private fun scheduleNext() {
        if (disposed.get()) {
            return
        }
        val delay = computeDelay()
        executor.schedule({
            try {
                if (!disposed.get() && StockerSetting.instance.screenAuto) {
                    runScreen()
                }
            } catch (e: Exception) {
                log.warn("Scheduled screen failed", e)
            } finally {
                if (!disposed.get()) {
                    scheduleNext()
                }
            }
        }, delay, TimeUnit.MILLISECONDS)
    }

    private fun computeDelay(): Long {
        val now = ZonedDateTime.now(SHANGHAI)
        val today = now.toLocalDate()
        val slot = today.atTime(RUN_HOUR, RUN_MINUTE).atZone(SHANGHAI)
        val lastDay = runCatching { StockerSetting.instance.screenLastDay }.getOrDefault("")
        val next = when {
            lastDay == today.toString() -> slot.plusDays(1)
            now.isBefore(slot) -> slot
            else -> null
        }
        if (next == null) {
            val auto = runCatching { StockerSetting.instance.screenAuto }.getOrDefault(true)
            if (!auto) {
                return Duration.between(now, slot.plusDays(1)).toMillis().coerceAtLeast(60_000)
            }
            val failedAt = lastFailureAt
            if (failedAt != 0L) {
                val wait = TimeUnit.MINUTES.toMillis(30) - (System.currentTimeMillis() - failedAt)
                if (wait > 0) {
                    return wait
                }
            }
            return 0
        }
        return Duration.between(now, next).toMillis().coerceAtLeast(0)
    }

    private fun runScreen() {
        if (disposed.get() || Thread.currentThread().isInterrupted) {
            return
        }
        val strategy = ScreenStrategy.fromId(StockerSetting.instance.screenStrategy)
        publish(snapshot.copy(running = true, error = null, strategy = strategy.id))
        try {
            val today = LocalDate.now(SHANGHAI)
            val years = TangChaoScreen.annualYears(today)
            val annuals = StockerScreenClient.loadAnnualReports(years)
            val codes = years.map { year -> annuals[year].orEmpty().keys }
                .reduce { left, right -> left intersect right }
            val quotes = StockerScreenClient.loadQuotes(codes)
            val inputs = codes.mapNotNull { code ->
                val series = years.mapNotNull { year -> annuals[year]?.get(code) }
                val quote = quotes[code] ?: return@mapNotNull null
                series to quote
            }
            val rows = ScreenLibrary.apply(strategy, inputs).sortedWith(rowOrder(strategy))
            val ranAt = System.currentTimeMillis()
            val setting = StockerSetting.instance
            setting.screenLastDay = today.toString()
            setting.screenRanAt = ranAt
            setting.replaceScreenPicks(rows.map { StockerScreenPickState.from(it) })
            lastFailureAt = 0
            log.info("${strategy.id} screen kept ${rows.size} of ${codes.size} names")
            val done = StockerScreenSnapshot(rows, ranAt, false, null, strategy.id)
            publish(done)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            publish(snapshot.copy(running = false, error = null))
        } catch (e: Exception) {
            lastFailureAt = System.currentTimeMillis()
            log.warn("Tang Chao screen failed", e)
            publish(snapshot.copy(running = false, error = e.message ?: e.javaClass.simpleName))
        }
    }

    private fun publish(next: StockerScreenSnapshot) {
        snapshot = next
        if (ApplicationManager.getApplication().isDisposed) {
            return
        }
        ApplicationManager.getApplication().invokeLater {
            if (!ApplicationManager.getApplication().isDisposed) {
                messageBus.syncPublisher(StockerScreenNotifier.TOPIC).onScreenChanged(next)
            }
        }
    }

    override fun dispose() {
        disposed.set(true)
        executor.shutdownNow()
        StockerScreenClient.close()
    }

    companion object {
        private val SHANGHAI: ZoneId = ZoneId.of("Asia/Shanghai")
        private const val RUN_HOUR = 8
        private const val RUN_MINUTE = 50

        val instance: StockerScreenService
            get() = ApplicationManager.getApplication().getService(StockerScreenService::class.java)

        private fun zoneRank(zone: String): Int = when (zone) {
            TangChaoScreen.ZONE_BUY -> 0
            TangChaoScreen.ZONE_HOLD -> 1
            else -> 2
        }

        private fun rowOrder(strategy: ScreenStrategy): Comparator<com.vermouthx.stocker.entities.StockerScreenRow> {
            val score = if (strategy == ScreenStrategy.QUALITY) {
                compareByDescending { row: com.vermouthx.stocker.entities.StockerScreenRow -> row.capToBuy }
            } else {
                compareBy { row: com.vermouthx.stocker.entities.StockerScreenRow -> row.capToBuy }
            }
            return compareBy<com.vermouthx.stocker.entities.StockerScreenRow> { zoneRank(it.zone) }
                .then(score)
                .thenBy { it.code }
        }
    }
}
