package com.vermouthx.stocker.screen

import com.intellij.openapi.diagnostic.Logger
import com.vermouthx.stocker.utils.StockerHttpClientPool
import com.vermouthx.stocker.utils.StockerJson
import com.vermouthx.stocker.utils.asDouble
import com.vermouthx.stocker.utils.asInt
import com.vermouthx.stocker.utils.asJsonList
import com.vermouthx.stocker.utils.asJsonMap
import com.vermouthx.stocker.utils.asText
import org.apache.http.client.methods.HttpGet
import org.apache.http.util.EntityUtils
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

internal object StockerScreenClient {

    private val log = Logger.getInstance(javaClass)
    private val pool = StockerHttpClientPool(log)

    private const val REPORT_URL = "https://datacenter-web.eastmoney.com/api/data/v1/get"
    private const val QUOTE_URL = "https://data.eastmoney.com/dataapi/xuangu/list"
    private const val PAGE_SIZE = 500
    private const val QUOTE_BATCH = 50

    fun close() {
        pool.close()
    }

    fun loadAnnualReports(years: List<Int>): Map<Int, Map<String, AnnualRecord>> {
        return years.associateWith { year -> loadYear(year) }
    }

    fun loadQuotes(codes: Collection<String>): Map<String, QuoteSnapshot> {
        val quotes = LinkedHashMap<String, QuoteSnapshot>()
        codes.chunked(QUOTE_BATCH).forEach { batch ->
            if (Thread.currentThread().isInterrupted) {
                throw InterruptedException()
            }
            val inner = batch.joinToString(",") { code -> "\"$code\"" }
            var page = 1
            while (page <= 3) {
                val root = request(
                    QUOTE_URL, mapOf(
                        "st" to "SECURITY_CODE",
                        "sr" to "1",
                        "ps" to QUOTE_BATCH.toString(),
                        "p" to page.toString(),
                        "sty" to "SECURITY_CODE,SECURITY_NAME_ABBR,NEW_PRICE,PE9,PBNEWMRQ,DEBT_ASSET_RATIO,TOTAL_MARKET_CAP,INDUSTRY,NETPROFIT_GROWTHRATE_3Y,TOI_YOY_RATIO,ZXGXL,MARKET",
                        "filter" to "(SECURITY_CODE in ($inner))",
                        "source" to "SELECT_SECURITIES",
                        "client" to "WEB",
                    )
                )
                val (rows, pages) = rowsOf(root)
                rows.forEach { row ->
                    val code = row["SECURITY_CODE"].asText() ?: return@forEach
                    quotes[code] = QuoteSnapshot(
                        code = code,
                        name = row["SECURITY_NAME_ABBR"].asText().orEmpty(),
                        industry = row["INDUSTRY"].asText().orEmpty(),
                        market = row["MARKET"].asText().orEmpty(),
                        price = row["NEW_PRICE"].asDouble(),
                        pe = row["PE9"].asDouble(),
                        pb = row["PBNEWMRQ"].asDouble(),
                        debtRatio = row["DEBT_ASSET_RATIO"].asDouble(),
                        marketCap = row["TOTAL_MARKET_CAP"].asDouble(),
                        growthPercent = row["NETPROFIT_GROWTHRATE_3Y"].asDouble(),
                        revenueGrowth = row["TOI_YOY_RATIO"].asDouble(),
                        dividendYield = row["ZXGXL"].asDouble(),
                    )
                }
                if (page >= pages || rows.size < QUOTE_BATCH) {
                    break
                }
                page++
            }
        }
        return quotes
    }

    private fun loadYear(year: Int): Map<String, AnnualRecord> {
        val records = LinkedHashMap<String, AnnualRecord>()
        var page = 1
        while (page <= 40) {
            if (Thread.currentThread().isInterrupted) {
                throw InterruptedException()
            }
            val root = request(
                REPORT_URL, mapOf(
                    "reportName" to "RPT_LICO_FN_CPD",
                    "columns" to "SECURITY_CODE,SECURITY_NAME_ABBR,WEIGHTAVG_ROE,XSMLL,BASIC_EPS,MGJYXJJE,PARENT_NETPROFIT",
                    "filter" to "(REPORTDATE='$year-12-31')(WEIGHTAVG_ROE>=8)",
                    "pageSize" to PAGE_SIZE.toString(),
                    "pageNumber" to page.toString(),
                    "sortColumns" to "SECURITY_CODE",
                    "sortTypes" to "1",
                    "source" to "WEB",
                    "client" to "WEB",
                )
            )
            val (rows, pages) = rowsOf(root)
            rows.forEach { row ->
                val code = row["SECURITY_CODE"].asText() ?: return@forEach
                if (code.length != 6 || code.any { !it.isDigit() }) {
                    return@forEach
                }
                val roe = row["WEIGHTAVG_ROE"].asDouble() ?: return@forEach
                records[code] = AnnualRecord(
                    year = year,
                    code = code,
                    name = row["SECURITY_NAME_ABBR"].asText().orEmpty(),
                    roe = roe,
                    grossMargin = row["XSMLL"].asDouble(),
                    eps = row["BASIC_EPS"].asDouble(),
                    ocfPerShare = row["MGJYXJJE"].asDouble(),
                    profit = row["PARENT_NETPROFIT"].asDouble(),
                )
            }
            if (page >= pages || rows.size < PAGE_SIZE) {
                break
            }
            page++
        }
        log.info("Loaded ${records.size} annual reports for $year")
        return records
    }

    private fun rowsOf(root: Map<String, Any?>): Pair<List<Map<String, Any?>>, Int> {
        if (root["success"] == false) {
            error(root["message"].asText() ?: "Screen request failed")
        }
        val result = root["result"].asJsonMap() ?: return emptyList<Map<String, Any?>>() to 0
        val rows = result["data"].asJsonList().mapNotNull { it.asJsonMap() }
        return rows to (result["pages"].asInt() ?: 1)
    }

    private fun request(base: String, params: Map<String, String>): Map<String, Any?> {
        val query = params.entries.joinToString("&") { (key, value) ->
            "${encode(key)}=${encode(value)}"
        }
        val url = "$base?$query"
        var last: Exception? = null
        repeat(2) { attempt ->
            if (Thread.currentThread().isInterrupted) {
                throw InterruptedException()
            }
            try {
                val httpGet = HttpGet(url)
                httpGet.setHeader("Referer", "https://data.eastmoney.com/")
                httpGet.setHeader("User-Agent", "Mozilla/5.0")
                pool.client().execute(httpGet).use { response ->
                    val body = EntityUtils.toString(response.entity, StandardCharsets.UTF_8)
                    val status = response.statusLine.statusCode
                    if (status !in 200..299) {
                        error("HTTP $status")
                    }
                    return StockerJson.root(body)
                }
            } catch (e: InterruptedException) {
                throw e
            } catch (e: Exception) {
                last = e
                log.warn("Screen request failed (attempt ${attempt + 1})", e)
            }
        }
        throw last ?: IllegalStateException("Screen request failed")
    }

    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)
}
