package com.vermouthx.stocker.screen

import com.vermouthx.stocker.StockerBundle
import com.vermouthx.stocker.entities.StockerScreenRow
import java.util.Locale

internal object ScreenReason {

    fun prompt(row: StockerScreenRow, strategyId: String, company: CompanyProfile?, thinking: String): String {
        val depth = when (thinking) {
            "high" -> "高：把规则、数字和风险写完整"
            "low" -> "低：只写结论和两条依据"
            "off" -> "关闭：直接给结论"
            else -> "中：结论、依据和一条主要风险"
        }
        return buildString {
            appendLine("思考强度：$depth")
            appendLine("策略：$strategyId")
            appendLine(strategyRules(strategyId))
            appendLine("机械筛选给出的区间：${row.zone}")
            appendLine(facts(row))
            appendLine(verdict(row, strategyId))
            if (company != null && company.fullName.isNotBlank()) {
                appendLine()
                appendLine(companyText(company, row))
                company.managers.take(5).forEach { manager ->
                    appendLine("${manager.name} ${manager.title}")
                }
            }
            appendLine()
            appendLine("请用这段材料写给投资者看的买卖理由。结论要和区间一致：BUY 是买点，HOLD 是持有，SELL 是卖出。")
        }
    }

    fun page(reason: String, row: StockerScreenRow, strategyId: String, company: CompanyProfile?): String {
        return render(reason, row, company)
    }

    fun explain(row: StockerScreenRow, strategyId: String, company: CompanyProfile?): String {
        return render(verdict(row, strategyId), row, company)
    }

    private fun render(reason: String, row: StockerScreenRow, company: CompanyProfile?): String {
        return buildString {
            appendLine(StockerBundle.message("screen.detail.section.reason"))
            appendLine(reason.trim())
            appendLine()
            appendLine(facts(row))
            appendLine()
            appendLine(StockerBundle.message("screen.detail.section.company"))
            if (company == null) {
                appendLine(StockerBundle.message("screen.detail.company.loading"))
            } else {
                appendLine(companyText(company, row))
            }
            appendLine()
            appendLine(StockerBundle.message("screen.detail.section.managers"))
            if (company == null) {
                appendLine(StockerBundle.message("screen.detail.company.loading"))
            } else if (company.managers.isEmpty()) {
                appendLine(StockerBundle.message("screen.detail.managers.empty"))
            } else {
                company.managers.forEach { manager ->
                    appendLine(managerText(manager))
                    appendLine()
                }
            }
        }.trim()
    }

    private fun strategyRules(strategyId: String): String = when (strategyId) {
        ScreenStrategy.GRAHAM.id -> "格雷厄姆：PE×PB 不超过 22.5；市盈率不超过 15 且市净率不超过 1.5 才是买点。"
        ScreenStrategy.MAGIC.id -> "神奇公式：按盈利收益率和 ROE 排名，前 10 名是买点，其后到第 30 名是持有。"
        ScreenStrategy.QUALITY.id -> "优质量化：利润和营收都在增长。市盈率低于 20 是买点，低于 35 是持有，否则是卖出。"
        else -> "老唐：合理市盈率 25 倍（净现比不足 1 时 20 倍）。理想买点是三年后合理估值的一半，卖点是 1.5 倍或当年 50 倍市盈率。"
    }

    private fun verdict(row: StockerScreenRow, strategyId: String): String {
        val score = format(row.capToBuy)
        return when (strategyId) {
            ScreenStrategy.GRAHAM.id -> {
                val pb = if (row.pe > 0.0) format(row.capToBuy / row.pe) else "-"
                val key = if (row.zone == TangChaoScreen.ZONE_BUY) "screen.reason.graham.buy" else "screen.reason.graham.hold"
                StockerBundle.message(key, score, format(row.pe), pb)
            }
            ScreenStrategy.MAGIC.id -> {
                val yield = if (row.pe > 0.0) format(100.0 / row.pe) else "-"
                val key = if (row.zone == TangChaoScreen.ZONE_BUY) "screen.reason.magic.buy" else "screen.reason.magic.hold"
                StockerBundle.message(key, score, yield, format(row.minRoe))
            }
            ScreenStrategy.QUALITY.id -> {
                val key = when (row.zone) {
                    TangChaoScreen.ZONE_BUY -> "screen.reason.quality.buy"
                    TangChaoScreen.ZONE_SELL -> "screen.reason.quality.sell"
                    else -> "screen.reason.quality.hold"
                }
                StockerBundle.message(key, score, format(row.pe))
            }
            else -> {
                val key = when (row.zone) {
                    TangChaoScreen.ZONE_BUY -> "screen.reason.tang.buy"
                    TangChaoScreen.ZONE_SELL -> "screen.reason.tang.sell"
                    else -> "screen.reason.tang.hold"
                }
                StockerBundle.message(key, score, formatYi(row.buyCapYi), formatYi(row.fairCapYi))
            }
        }
    }

    private fun facts(row: StockerScreenRow): String {
        val lines = mutableListOf(
            StockerBundle.message(
                "screen.reason.facts",
                format(row.minRoe),
                format(row.minGrossMargin),
                format(row.debtRatio),
                format(row.cashRatio),
                format(row.pe),
                format(row.price),
                formatYi(row.marketCapYi),
            )
        )
        when (row.growthAdjustment) {
            TangChaoScreen.GROWTH_CAPPED -> lines += StockerBundle.message("screen.note.growth.capped")
            TangChaoScreen.GROWTH_FLOOR -> lines += StockerBundle.message("screen.note.growth.floor")
            TangChaoScreen.GROWTH_MISSING -> lines += StockerBundle.message("screen.note.growth.missing")
            else -> lines += StockerBundle.message("screen.note.growth.raw", format(row.rawGrowth))
        }
        if (row.cashDiscount) {
            lines += StockerBundle.message("screen.note.cash")
        }
        if (row.leverageDiscount) {
            lines += StockerBundle.message("screen.note.leverage")
        }
        return lines.joinToString("\n")
    }

    private fun companyText(company: CompanyProfile, row: StockerScreenRow): String {
        val lines = mutableListOf<String>()
        if (company.fullName.isNotBlank()) {
            lines += company.fullName
        }
        if (company.englishName.isNotBlank()) {
            lines += company.englishName
        }
        lines += StockerBundle.message(
            "screen.detail.company.meta",
            company.industry.ifBlank { row.industry }.ifBlank { "-" },
            company.chairman.ifBlank { "-" },
            company.generalManager.ifBlank { "-" },
            company.legalPerson.ifBlank { "-" },
            company.capital.ifBlank { "-" },
            company.employees.ifBlank { "-" },
        )
        if (company.website.isNotBlank()) {
            lines += company.website
        }
        if (company.address.isNotBlank()) {
            lines += company.address
        }
        if (company.intro.isNotBlank()) {
            lines += ""
            lines += company.intro
        }
        if (company.business.isNotBlank()) {
            lines += ""
            lines += StockerBundle.message("screen.detail.company.business", shorten(company.business, 280))
        }
        return lines.joinToString("\n")
    }

    private fun managerText(manager: CompanyManager): String {
        val who = listOf(manager.gender, manager.age.let { if (it.isBlank()) "" else "${it}${StockerBundle.message("screen.detail.age")}" }, manager.education)
            .filter { it.isNotBlank() }
            .joinToString("，")
        val head = listOf(manager.name, who, manager.title).filter { it.isNotBlank() }.joinToString("，")
        val resume = shorten(manager.resume, 180)
        return if (resume.isBlank()) head else "$head\n$resume"
    }

    private fun shorten(text: String, limit: Int): String {
        val compact = text.replace(Regex("\\s+"), " ").trim()
        return if (compact.length <= limit) compact else compact.take(limit) + "…"
    }

    private fun format(value: Double): String = String.format(Locale.US, "%.1f", value)

    private fun formatYi(value: Double): String {
        return if (value >= 100) String.format(Locale.US, "%.0f", value) else String.format(Locale.US, "%.1f", value)
    }
}
