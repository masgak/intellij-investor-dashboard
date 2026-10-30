package com.vermouthx.stocker.utils

import com.intellij.openapi.diagnostic.Logger
import com.vermouthx.stocker.StockerBundle
import com.vermouthx.stocker.entities.StockerQuote
import com.vermouthx.stocker.enums.StockerMarketType
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.utils.URIBuilder
import org.apache.http.util.EntityUtils
import java.util.concurrent.ConcurrentHashMap

internal object StockerProfileHttpUtil {

    private val log = Logger.getInstance(javaClass)
    private val http = StockerHttpClientPool(log)
    private val industries = ConcurrentHashMap<String, String>()
    private val romanSuffix = Regex("[ⅠⅡⅢⅣⅤ]$")

    fun apply(marketType: StockerMarketType, quotes: List<StockerQuote>): List<StockerQuote> {
        val found = if (marketType == StockerMarketType.AShare) {
            loadIndustries(marketType, quotes.map { it.code })
        } else {
            emptyMap()
        }
        quotes.forEach { quote ->
            quote.industry = if (marketType == StockerMarketType.AShare) {
                found[quote.code].orEmpty().ifBlank { "-" }
            } else {
                "-"
            }
            quote.board = boardLabel(marketType, quote.code)
        }
        return quotes
    }

    fun boardLabel(marketType: StockerMarketType, code: String): String {
        if (marketType != StockerMarketType.AShare) {
            return "-"
        }
        val digits = digitsOf(code)
        val key = when {
            code.uppercase().startsWith("BJ") || digits.startsWith("92") || digits.startsWith("8") || digits.startsWith("4") -> "board.beijing"
            digits.startsWith("300") || digits.startsWith("301") -> "board.chinext"
            digits.startsWith("688") || digits.startsWith("689") -> "board.star"
            else -> "board.main"
        }
        return StockerBundle.message(key)
    }

    private fun loadIndustries(marketType: StockerMarketType, codes: List<String>): Map<String, String> {
        if (marketType != StockerMarketType.AShare || codes.isEmpty()) {
            return emptyMap()
        }
        val missing = codes.distinct().filter { industries[cacheKey(marketType, it)] == null }
        missing.chunked(20).forEach { chunk ->
            fetch(marketType, chunk)
        }
        return codes.associateWith { industries[cacheKey(marketType, it)].orEmpty() }
    }

    private fun fetch(marketType: StockerMarketType, codes: List<String>) {
        val secids = codes.flatMap { secids(marketType, it) }
        if (secids.isEmpty()) {
            return
        }
        val uri = URIBuilder("https://push2.eastmoney.com/api/qt/ulist.np/get")
            .addParameter("fltt", "2")
            .addParameter("fields", "f12,f100")
            .addParameter("secids", secids.joinToString(","))
            .build()
        val request = HttpGet(uri)
        request.setHeader("Referer", "https://quote.eastmoney.com/")
        try {
            http.client().execute(request).use { response ->
                val text = EntityUtils.toString(response.entity, Charsets.UTF_8)
                val data = StockerJson.root(text)["data"] as? Map<*, *> ?: return
                val rows = data["diff"] as? List<*> ?: return
                val bySymbol = HashMap<String, String>()
                rows.forEach { item ->
                    val row = item as? Map<*, *> ?: return@forEach
                    val symbol = row["f12"]?.toString()?.uppercase().orEmpty()
                    val industry = cleanIndustry(row["f100"]?.toString().orEmpty())
                    if (symbol.isNotEmpty() && industry.isNotEmpty()) {
                        bySymbol.putIfAbsent(symbol, industry)
                    }
                }
                codes.forEach { code ->
                    val industry = bySymbol[lookupSymbol(marketType, code)].orEmpty()
                    industries[cacheKey(marketType, code)] = industry
                }
            }
        } catch (e: Exception) {
            log.warn("Unable to load business sectors", e)
        }
    }

    private fun secids(marketType: StockerMarketType, code: String): List<String> {
        val symbol = lookupSymbol(marketType, code)
        if (symbol.isEmpty()) {
            return emptyList()
        }
        return when (marketType) {
            StockerMarketType.AShare -> {
                val market = if (code.uppercase().startsWith("SH") || symbol.startsWith("6") || symbol.startsWith("9")) "1" else "0"
                listOf("$market.$symbol")
            }
            StockerMarketType.HKStocks -> listOf("116.$symbol")
            StockerMarketType.USStocks -> listOf("105.$symbol", "106.$symbol", "107.$symbol")
            StockerMarketType.Crypto -> emptyList()
        }
    }

    private fun lookupSymbol(marketType: StockerMarketType, code: String): String {
        return when (marketType) {
            StockerMarketType.AShare -> digitsOf(code)
            StockerMarketType.HKStocks -> digitsOf(code).padStart(5, '0')
            StockerMarketType.USStocks -> code.trim().uppercase()
            StockerMarketType.Crypto -> ""
        }
    }

    private fun digitsOf(code: String): String {
        val upper = code.trim().uppercase()
        val stripped = when {
            upper.startsWith("SH") || upper.startsWith("SZ") || upper.startsWith("BJ") || upper.startsWith("HK") -> upper.substring(2)
            else -> upper
        }
        return stripped.filter { it.isDigit() }
    }

    private fun cacheKey(marketType: StockerMarketType, code: String): String {
        return "${marketType.name}:${lookupSymbol(marketType, code)}"
    }

    private fun cleanIndustry(value: String): String {
        return value.trim().replace(romanSuffix, "").trim()
    }
}
