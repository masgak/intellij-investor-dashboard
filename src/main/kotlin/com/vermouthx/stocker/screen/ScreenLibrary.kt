package com.vermouthx.stocker.screen

import com.vermouthx.stocker.entities.StockerScreenRow

/**
 * Fundamental screens borrowed from open-source stock-selection projects.
 * Tang Chao stays the handbook model. The other three use the same annual reports,
 * with the rule each project actually publishes:
 * Graham PE×PB from securities-screener / The Intelligent Investor,
 * Greenblatt's two-rank magic formula (earnings yield and return on capital, here ROE),
 * and investool's quality filter (ROE, profit growth, revenue growth, debt, size).
 */
internal enum class ScreenStrategy(val id: String) {
    TANG("TANG"),
    GRAHAM("GRAHAM"),
    MAGIC("MAGIC"),
    QUALITY("QUALITY");

    companion object {
        fun fromId(id: String): ScreenStrategy = entries.firstOrNull { it.id == id } ?: TANG
    }
}

internal object ScreenLibrary {

    private const val YI = 1.0e8

    fun apply(
        strategy: ScreenStrategy,
        inputs: List<Pair<List<AnnualRecord>, QuoteSnapshot>>,
    ): List<StockerScreenRow> {
        return when (strategy) {
            ScreenStrategy.TANG -> inputs.mapNotNull { (annuals, quote) -> TangChaoScreen.evaluate(annuals, quote) }
            ScreenStrategy.GRAHAM -> inputs.mapNotNull { (annuals, quote) -> graham(annuals, quote) }
            ScreenStrategy.MAGIC -> magic(inputs)
            ScreenStrategy.QUALITY -> quality(inputs)
        }
    }

    private fun graham(annuals: List<AnnualRecord>, quote: QuoteSnapshot): StockerScreenRow? {
        val prepared = prepare(annuals, quote) ?: return null
        if (prepared.profits.any { it <= 0.0 }) {
            return null
        }
        val pe = prepared.pe ?: return null
        val pb = prepared.pb ?: return null
        val debt = prepared.debt ?: return null
        val marketCap = prepared.marketCap ?: return null
        if (pe <= 0.0 || pb <= 0.0 || debt >= 50.0 || marketCap < 5.0e9) {
            return null
        }
        if (prepared.dividend != null && prepared.dividend <= 0.0) {
            return null
        }
        val product = pe * pb
        if (product > 22.5) {
            return null
        }
        val zone = if (pe <= 15.0 && pb <= 1.5) TangChaoScreen.ZONE_BUY else TangChaoScreen.ZONE_HOLD
        return baseRow(prepared, zone, product, marketCap)
    }

    private fun magic(inputs: List<Pair<List<AnnualRecord>, QuoteSnapshot>>): List<StockerScreenRow> {
        val prepared = inputs.mapNotNull { (annuals, quote) ->
            val row = prepare(annuals, quote) ?: return@mapNotNull null
            val pe = row.pe ?: return@mapNotNull null
            val roe = row.roes.lastOrNull() ?: return@mapNotNull null
            val debt = row.debt ?: return@mapNotNull null
            val marketCap = row.marketCap ?: return@mapNotNull null
            if (row.profits.last() <= 0.0 || pe <= 0.0 || pe >= 100.0 || roe <= 0.0) {
                return@mapNotNull null
            }
            if (debt >= 60.0 || marketCap < 5.0e9) {
                return@mapNotNull null
            }
            if (listOf("电力", "燃气", "水务").any { row.quote.industry.contains(it) }) {
                return@mapNotNull null
            }
            row
        }
        if (prepared.isEmpty()) {
            return emptyList()
        }
        val byYield = prepared.sortedByDescending { 1.0 / it.pe!! }
        val byRoe = prepared.sortedByDescending { it.roes.last() }
        val yieldRank = byYield.withIndex().associate { it.value.annuals.last().code to (it.index + 1) }
        val roeRank = byRoe.withIndex().associate { it.value.annuals.last().code to (it.index + 1) }
        return prepared
            .sortedBy { row ->
                val code = row.annuals.last().code
                (yieldRank[code] ?: prepared.size) + (roeRank[code] ?: prepared.size)
            }
            .take(30)
            .mapIndexed { index, row ->
                val zone = if (index < 10) TangChaoScreen.ZONE_BUY else TangChaoScreen.ZONE_HOLD
                baseRow(row, zone, (index + 1).toDouble(), row.marketCap!!)
            }
    }

    private fun quality(inputs: List<Pair<List<AnnualRecord>, QuoteSnapshot>>): List<StockerScreenRow> {
        return inputs.mapNotNull { (annuals, quote) ->
            val prepared = prepare(annuals, quote) ?: return@mapNotNull null
            if (prepared.roes.any { it < 8.0 || it > 80.0 } || prepared.profits.any { it <= 0.0 }) {
                return@mapNotNull null
            }
            val debt = prepared.debt ?: return@mapNotNull null
            val marketCap = prepared.marketCap ?: return@mapNotNull null
            val profitGrowth = prepared.profitGrowth ?: return@mapNotNull null
            val revenueGrowth = prepared.revenueGrowth ?: return@mapNotNull null
            val pe = prepared.pe ?: return@mapNotNull null
            if (debt >= 60.0 || marketCap < 1.0e10 || profitGrowth <= 0.0 || revenueGrowth <= 0.0 || pe <= 0.0) {
                return@mapNotNull null
            }
            val zone = when {
                pe < 20.0 -> TangChaoScreen.ZONE_BUY
                pe < 35.0 -> TangChaoScreen.ZONE_HOLD
                else -> TangChaoScreen.ZONE_SELL
            }
            baseRow(prepared, zone, profitGrowth, marketCap)
        }.sortedByDescending { it.capToBuy }.take(40)
    }

    private fun prepare(annuals: List<AnnualRecord>, quote: QuoteSnapshot): Prepared? {
        if (annuals.size < 5) {
            return null
        }
        val ordered = annuals.sortedBy { it.year }
        val code = ordered.last().code
        if (code.startsWith("900") || code.startsWith("200") || code.startsWith("201")) {
            return null
        }
        val name = quote.name.ifBlank { ordered.last().name }
        if (TangChaoScreen.isSpecialTreatment(name)) {
            return null
        }
        if (TangChaoScreen.blockedIndustry.any { quote.industry.contains(it) }) {
            return null
        }
        val profits = ordered.map { it.profit ?: return null }
        val grossValues = ordered.mapNotNull { it.grossMargin }
        return Prepared(
            annuals = ordered,
            quote = quote,
            name = name,
            roes = ordered.map { it.roe },
            profits = profits,
            minGross = grossValues.minOrNull() ?: 0.0,
            pe = quote.pe,
            pb = quote.pb,
            debt = quote.debtRatio,
            marketCap = quote.marketCap,
            dividend = quote.dividendYield,
            profitGrowth = quote.growthPercent,
            revenueGrowth = quote.revenueGrowth,
            cashRatio = TangChaoScreen.averageCashRatio(ordered) ?: 0.0,
        )
    }

    private fun baseRow(
        prepared: Prepared,
        zone: String,
        score: Double,
        marketCap: Double,
    ): StockerScreenRow {
        return StockerScreenRow(
            code = TangChaoScreen.pluginCode(prepared.annuals.last().code, prepared.quote.market),
            name = prepared.name,
            industry = prepared.quote.industry,
            price = prepared.quote.price ?: 0.0,
            pe = prepared.pe ?: 0.0,
            minRoe = prepared.roes.min(),
            minGrossMargin = prepared.minGross,
            debtRatio = prepared.debt ?: 0.0,
            cashRatio = prepared.cashRatio,
            rawGrowth = prepared.profitGrowth ?: 0.0,
            usedGrowth = prepared.profitGrowth ?: 0.0,
            growthAdjustment = TangChaoScreen.GROWTH_NONE,
            zone = zone,
            capToBuy = score,
            marketCapYi = marketCap / YI,
            buyCapYi = 0.0,
            fairCapYi = 0.0,
            cashDiscount = false,
            leverageDiscount = false,
        )
    }

    private data class Prepared(
        val annuals: List<AnnualRecord>,
        val quote: QuoteSnapshot,
        val name: String,
        val roes: List<Double>,
        val profits: List<Double>,
        val minGross: Double,
        val pe: Double?,
        val pb: Double?,
        val debt: Double?,
        val marketCap: Double?,
        val dividend: Double?,
        val profitGrowth: Double?,
        val revenueGrowth: Double?,
        val cashRatio: Double,
    )
}
