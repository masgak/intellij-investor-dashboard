package com.vermouthx.stocker.backtest

import com.intellij.openapi.diagnostic.Logger
import com.vermouthx.stocker.utils.StockerHttpClientPool
import com.vermouthx.stocker.utils.StockerJson
import com.vermouthx.stocker.utils.asJsonList
import com.vermouthx.stocker.utils.asJsonMap
import com.vermouthx.stocker.utils.asText
import org.apache.http.client.methods.HttpGet
import org.apache.http.util.EntityUtils
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.LocalDate

/**
 * Daily bars come from Tencent's forward-adjusted kline.
 * East Money's history host drops connections when many symbols are requested in a row.
 */
internal object BacktestKlineClient {

    private val log = Logger.getInstance(javaClass)
    private val pool = StockerHttpClientPool(log)
    private const val PAGE = 800

    fun load(pluginCode: String, years: Int): List<Bar> {
        val symbol = tencentSymbol(pluginCode) ?: error("unsupported symbol")
        val start = LocalDate.now().minusYears(years.toLong().coerceAtLeast(1))
        val byDate = LinkedHashMap<String, Bar>()
        var end: LocalDate? = null
        repeat(4) {
            val param = if (end == null) {
                "$symbol,day,,,${PAGE},qfq"
            } else {
                "$symbol,day,,$end,${PAGE},qfq"
            }
            val page = fetch(symbol, param)
            if (page.isEmpty()) {
                return@repeat
            }
            page.forEach { bar -> byDate.putIfAbsent(bar.date, bar) }
            val first = LocalDate.parse(page.first().date)
            if (!first.isAfter(start) || page.size < 100) {
                return@repeat
            }
            end = first.minusDays(1)
        }
        return byDate.values
            .filter { bar -> !LocalDate.parse(bar.date).isBefore(start) }
            .sortedBy { it.date }
    }

    private fun fetch(symbol: String, param: String): List<Bar> {
        val url = "https://web.ifzq.gtimg.cn/appstock/app/fqkline/get?param=${encode(param)}"
        var last: Exception? = null
        repeat(3) { attempt ->
            try {
                val httpGet = HttpGet(url)
                httpGet.setHeader("Referer", "https://gu.qq.com/")
                httpGet.setHeader("User-Agent", "Mozilla/5.0")
                httpGet.setHeader("Connection", "close")
                val body = pool.client().execute(httpGet).use { response ->
                    val text = EntityUtils.toString(response.entity, StandardCharsets.UTF_8)
                    if (response.statusLine.statusCode !in 200..299) {
                        error("HTTP ${response.statusLine.statusCode}")
                    }
                    text
                }
                return parse(symbol, body)
            } catch (e: Exception) {
                last = e
                log.warn("Kline request failed for $symbol (attempt ${attempt + 1})", e)
                Thread.sleep(300L * (attempt + 1))
            }
        }
        throw last ?: IllegalStateException("kline request failed")
    }

    private fun parse(symbol: String, body: String): List<Bar> {
        val node = StockerJson.root(body)["data"].asJsonMap()?.get(symbol).asJsonMap() ?: return emptyList()
        val rows = node["qfqday"].asJsonList().ifEmpty { node["day"].asJsonList() }
        return rows.mapNotNull { item ->
            val parts = item.asJsonList()
            if (parts.size < 5) {
                return@mapNotNull null
            }
            Bar(
                date = parts[0].asText() ?: return@mapNotNull null,
                open = parts[1].asText()?.toDoubleOrNull() ?: return@mapNotNull null,
                close = parts[2].asText()?.toDoubleOrNull() ?: return@mapNotNull null,
                high = parts[3].asText()?.toDoubleOrNull() ?: return@mapNotNull null,
                low = parts[4].asText()?.toDoubleOrNull() ?: return@mapNotNull null,
            )
        }
    }

    private fun tencentSymbol(pluginCode: String): String? {
        val upper = pluginCode.trim().uppercase()
        val digits = upper.removePrefix("SH").removePrefix("SZ").removePrefix("BJ")
        if (digits.length != 6 || digits.any { !it.isDigit() }) {
            return null
        }
        val market = when {
            upper.startsWith("SH") || digits.startsWith("6") || digits.startsWith("9") -> "sh"
            upper.startsWith("BJ") || digits.startsWith("8") || digits.startsWith("4") || digits.startsWith("92") -> "bj"
            else -> "sz"
        }
        return market + digits
    }

    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)
}
