package com.vermouthx.stocker.screen

import com.vermouthx.stocker.entities.StockerScreenRow
import java.time.LocalDate
import kotlin.math.pow

/**
 * Mechanical pre-screen from 《价值投资实战手册》.
 *
 * A company is eligible for the simplified PE model only when profit looks like cash,
 * has stayed high for years, and does not need heavy new capital to keep earning.
 * Fair PE is 1 / 4% = 25, cut to 20 when cash conversion is below 1.
 * Debt at or above 40% cuts that fair value to 70%. The buy point is half of the
 * value three years out. The sell point is the lower of 1.5x that value and 50x
 * current earnings. Three-year profit uses the historical growth rate, capped at 15%
 * and never projected below zero.
 *
 * Banks, insurers, brokers, and property companies are left out because their earnings
 * depend on accounting assumptions or on constantly raising capital. Commodity cyclicals
 * are left out because this PE model does not know where they are in the cycle.
 */
internal object TangChaoScreen {

    const val ZONE_BUY = "BUY"
    const val ZONE_HOLD = "HOLD"
    const val ZONE_SELL = "SELL"

    const val GROWTH_NONE = "NONE"
    const val GROWTH_CAPPED = "CAPPED"
    const val GROWTH_FLOOR = "FLOOR"
    const val GROWTH_MISSING = "MISSING"

    private const val MIN_ROE = 15.0
    private const val MAX_ROE = 80.0
    private const val MIN_GROSS = 40.0
    private const val MIN_CASH_RATIO = 0.5
    private const val FULL_CASH_RATIO = 1.0
    private const val FAIR_PE = 25.0
    private const val DISCOUNTED_PE = 20.0
    private const val LEVERAGE_DEBT = 40.0
    private const val MAX_DEBT = 60.0
    private const val LEVERAGE_FACTOR = 0.7
    private const val MIN_MARKET_CAP = 1.0e10
    private const val MAX_GROWTH = 0.15
    private const val SELL_PE = 50.0
    private const val YI = 1.0e8

    internal val blockedIndustry = listOf(
        "银行", "保险", "证券", "多元金融", "房地产",
        "石油", "煤炭", "钢铁", "工业金属", "贵金属", "普钢", "化肥",
    )

    fun annualYears(today: LocalDate): List<Int> {
        // Annual reports are due by April 30, so May is the first month the prior year is complete.
        val latest = if (today.monthValue >= 5) today.year - 1 else today.year - 2
        return (latest - 4..latest).toList()
    }

    fun evaluate(annuals: List<AnnualRecord>, quote: QuoteSnapshot): StockerScreenRow? {
        if (annuals.size < 5) {
            return null
        }
        val ordered = annuals.sortedBy { it.year }
        val code = ordered.last().code
        if (code.startsWith("900") || code.startsWith("200") || code.startsWith("201")) {
            return null
        }
        val name = quote.name.ifBlank { ordered.last().name }
        if (isSpecialTreatment(name)) {
            return null
        }
        if (blockedIndustry.any { token -> quote.industry.contains(token) }) {
            return null
        }

        val roes = ordered.map { it.roe }
        if (roes.any { it < MIN_ROE || it > MAX_ROE }) {
            return null
        }
        val gross = ordered.map { it.grossMargin ?: return null }
        if (gross.any { it < MIN_GROSS }) {
            return null
        }
        if (ordered.any { (it.eps ?: 0.0) <= 0.0 || (it.profit ?: 0.0) <= 0.0 }) {
            return null
        }
        val cashRatio = averageCashRatio(ordered) ?: return null
        if (cashRatio < MIN_CASH_RATIO) {
            return null
        }

        val marketCap = quote.marketCap ?: return null
        val pe = quote.pe ?: return null
        val debt = quote.debtRatio ?: return null
        if (marketCap < MIN_MARKET_CAP || pe <= 0.0 || debt >= MAX_DEBT) {
            return null
        }

        val cashDiscount = cashRatio < FULL_CASH_RATIO
        val leverageDiscount = debt >= LEVERAGE_DEBT
        val fairPe = if (cashDiscount) DISCOUNTED_PE else FAIR_PE
        val leverage = if (leverageDiscount) LEVERAGE_FACTOR else 1.0
        val (usedGrowth, adjustment) = growth(quote.growthPercent)
        val ttmProfit = marketCap / pe
        val fairValue = ttmProfit * (1.0 + usedGrowth).pow(3) * fairPe * leverage
        if (fairValue <= 0.0) {
            return null
        }
        val buyValue = fairValue * 0.5
        val sellValue = minOf(fairValue * 1.5, ttmProfit * SELL_PE)
        val zone = when {
            marketCap <= buyValue -> ZONE_BUY
            marketCap >= sellValue || pe >= SELL_PE -> ZONE_SELL
            else -> ZONE_HOLD
        }
        return StockerScreenRow(
            code = pluginCode(code, quote.market),
            name = name,
            industry = quote.industry,
            price = quote.price ?: 0.0,
            pe = pe,
            minRoe = roes.min(),
            minGrossMargin = gross.min(),
            debtRatio = debt,
            cashRatio = cashRatio,
            rawGrowth = quote.growthPercent ?: 0.0,
            usedGrowth = usedGrowth * 100.0,
            growthAdjustment = adjustment,
            zone = zone,
            capToBuy = marketCap / buyValue,
            marketCapYi = marketCap / YI,
            buyCapYi = buyValue / YI,
            fairCapYi = fairValue / YI,
            cashDiscount = cashDiscount,
            leverageDiscount = leverageDiscount,
        )
    }

    private fun growth(rawPercent: Double?): Pair<Double, String> {
        if (rawPercent == null) {
            return 0.0 to GROWTH_MISSING
        }
        if (rawPercent < 0.0) {
            return 0.0 to GROWTH_FLOOR
        }
        if (rawPercent > MAX_GROWTH * 100.0) {
            return MAX_GROWTH to GROWTH_CAPPED
        }
        return rawPercent / 100.0 to GROWTH_NONE
    }

    internal fun isSpecialTreatment(name: String): Boolean {
        val normalized = name.trim().uppercase()
        return normalized.startsWith("ST") ||
            normalized.startsWith("*ST") ||
            normalized.startsWith("S*ST") ||
            normalized.startsWith("SST")
    }

    internal fun averageCashRatio(ordered: List<AnnualRecord>): Double? {
        val ratios = ordered.mapNotNull { year ->
            val eps = year.eps ?: return@mapNotNull null
            val cash = year.ocfPerShare ?: return@mapNotNull null
            if (eps <= 0.0) null else cash / eps
        }
        if (ratios.size < 4) {
            return null
        }
        return ratios.average()
    }

    internal fun pluginCode(code: String, market: String): String {
        val prefix = when {
            market.contains("北交") -> "BJ"
            market.contains("上交") || market.contains("科创") -> "SH"
            market.contains("深交") || market.contains("创业") -> "SZ"
            code.startsWith("92") || code.startsWith("8") || code.startsWith("4") -> "BJ"
            code.startsWith("6") || code.startsWith("9") -> "SH"
            else -> "SZ"
        }
        return prefix + code
    }
}

internal data class AnnualRecord(
    val year: Int,
    val code: String,
    val name: String,
    val roe: Double,
    val grossMargin: Double?,
    val eps: Double?,
    val ocfPerShare: Double?,
    val profit: Double?,
)

internal data class QuoteSnapshot(
    val code: String,
    val name: String,
    val industry: String,
    val market: String,
    val price: Double?,
    val pe: Double?,
    val pb: Double?,
    val debtRatio: Double?,
    val marketCap: Double?,
    val growthPercent: Double?,
    val revenueGrowth: Double?,
    val dividendYield: Double?,
)
