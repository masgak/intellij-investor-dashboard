package com.vermouthx.stocker.utils

import com.intellij.openapi.diagnostic.Logger
import com.vermouthx.stocker.entities.StockerSuggestion
import com.vermouthx.stocker.enums.StockerMarketType
import com.vermouthx.stocker.enums.StockerQuoteProvider
import org.apache.commons.text.StringEscapeUtils
import org.apache.http.client.methods.HttpGet
import org.apache.http.util.EntityUtils

object StockerSuggestHttpUtil {

    private val log = Logger.getInstance(javaClass)
    private val httpClientPool = StockerHttpClientPool(log)

    fun closeConnections() {
        httpClientPool.close()
    }

    /**
     * Search for stock/crypto suggestions
     * @param key Search term
     * @param provider Quote provider (Sina or Tencent)
     * @param marketTypeFilter Optional filter to limit results to specific market types. 
     *                         If null, returns all results. If specified, only returns matching market types.
     * @return List of suggestions matching the filter
     */
    fun suggest(
        key: String, 
        provider: StockerQuoteProvider,
        marketTypeFilter: Set<StockerMarketType>? = null
    ): List<StockerSuggestion> {
        val providerForSearch = if (provider == StockerQuoteProvider.USMART) StockerQuoteProvider.SINA else provider
        val url = "${providerForSearch.suggestHost}$key"
        val httpGet = HttpGet(url)
        if (providerForSearch == StockerQuoteProvider.SINA) {
            httpGet.setHeader("Referer", "https://finance.sina.com.cn") // Sina API requires this header
        }
        return try {
            httpClientPool.client().execute(httpGet).use { response ->
                val allSuggestions = when (providerForSearch) {
                    StockerQuoteProvider.SINA -> {
                        val responseText = EntityUtils.toString(response.entity, "UTF-8")
                        parseSinaSuggestion(responseText)
                    }

                    StockerQuoteProvider.TENCENT -> {
                        val responseText = EntityUtils.toString(response.entity, "UTF-8")
                        parseTencentSuggestion(responseText)
                    }

                    StockerQuoteProvider.USMART -> emptyList()
                }
                
                // Apply market type filter if specified
                if (marketTypeFilter != null) {
                    allSuggestions.filter { it.market in marketTypeFilter }
                } else {
                    allSuggestions
                }
            }
        } catch (e: Exception) {
            log.warn(e)
            emptyList()
        }
    }

    private fun parseSinaSuggestion(responseText: String): List<StockerSuggestion> {
        val result = mutableListOf<StockerSuggestion>()
        val regex = Regex("var suggestvalue=\"(.*?)\";")
        val matchResult = regex.find(responseText)
        val (_, snippetsText) = matchResult!!.groupValues
        if (snippetsText.isEmpty()) {
            return emptyList()
        }
        val snippets = snippetsText.split(";")
        for (snippet in snippets) {
            val columns = snippet.split(",")
            if (columns.size < 5) {
                continue
            }
            when (columns[1]) {
                "11" -> {
                    if (columns[4].startsWith("S*ST")) {
                        continue
                    }
                    result.add(StockerSuggestion(columns[3].uppercase(), columns[4], StockerMarketType.AShare))
                }

                // Type 22 = on-exchange ETF, type 203 = on-exchange LOF. Both share the same
                // numeric code space (SZ 15x/16x/18x, SH 50x/51x) and field layout, and Sina
                // reports the numeric code in columns[2] for both types.
                "22", "203" -> {
                    val code = columns[2]
                    val candidate = when {
                        code.startsWith("15") || code.startsWith("16") || code.startsWith("18") -> "SZ$code"

                        code.startsWith("50") || code.startsWith("51") -> "SH$code"

                        else -> null
                    }
                    if (candidate != null) {
                        result.add(StockerSuggestion(candidate, columns[4], StockerMarketType.AShare))
                    }
                }

                "31" -> result.add(StockerSuggestion(columns[3].uppercase(), columns[4], StockerMarketType.HKStocks))
                "41" -> result.add(StockerSuggestion(columns[3].uppercase(), columns[4], StockerMarketType.USStocks))
                "71" -> {
                    // Only include crypto codes that follow the supported pattern: BTC{COIN}{FIAT}
                    // Examples: BTCBTCUSD, BTCETHUSD, BTCBTCCNY
                    val cryptoCode = columns[3].uppercase()
                    if (isSupportedCryptoCode(cryptoCode)) {
                        result.add(StockerSuggestion(cryptoCode, columns[4], StockerMarketType.Crypto))
                    }
                }
                "81" -> result.add(StockerSuggestion(columns[3].uppercase(), columns[4], StockerMarketType.AShare))
            }
        }
        // Sina returns both a type-22 and a type-203 row for the same ETF;
        // drop duplicate codes while preserving the first occurrence.
        return result.distinctBy { it.code }
    }
    
    /**
     * Check if a crypto code follows Sina's supported format.
     * Based on testing, Sina only supports USD/USDT-based crypto pairs with BTC prefix.
     * 
     * Supported pattern: BTC{COIN}USD or BTC{COIN}USDT where {COIN} is the cryptocurrency name
     * 
     * Examples of supported codes:
     * - BTCBTCUSD (Bitcoin/USD) ✅
     * - BTCBTCUSDT (Bitcoin/USDT) ✅
     * - BTCETHUSD (Ethereum/USD) ✅
     * - BTCETHUSDT (Ethereum/USDT) ✅
     * - BTCLTCUSD (Litecoin/USD) ✅
     * 
     * Unsupported examples:
     * - BTCUSD (too short, missing coin name) ❌
     * - ETHUSD (missing BTC prefix) ❌
     * - BCHUSD (missing BTC prefix) ❌
     * - BTCBTCCNY (CNY not supported) ❌
     * - BTCBTCEUR (EUR not supported) ❌
     */
    private fun isSupportedCryptoCode(code: String): Boolean {
        // Must start with "BTC" prefix
        if (!code.startsWith("BTC")) {
            return false
        }
        
        // Must end with "USD" or "USDT"
        val endsWithUSD = code.endsWith("USD")
        val endsWithUSDT = code.endsWith("USDT")
        if (!endsWithUSD && !endsWithUSDT) {
            return false
        }
        
        // Length check: minimum is BTCBTCUSD (9), with USDT it's 10+
        // Maximum reasonable length is 15 chars
        if (code.length < 9 || code.length > 15) {
            return false
        }
        
        // Pattern: BTC + {COIN} + (USD|USDT)
        // The coin name part must exist (at least 3 chars after BTC and before USD/USDT)
        val fiatSuffix = if (endsWithUSDT) "USDT" else "USD"
        val coinPart = code.substring(3, code.length - fiatSuffix.length)
        
        // Coin name must have at least 3 characters
        if (coinPart.length < 3) {
            return false
        }
        
        return true
    }

    private fun parseTencentSuggestion(responseText: String): List<StockerSuggestion> {
        if (responseText.isEmpty()) {
            return emptyList()
        }
        val result = mutableListOf<StockerSuggestion>()
        val snippets = responseText.replace("v_hint=\"", "").replace("\"", "").split("^")
        for (snippet in snippets) {
            val columns = snippet.split("~")
            if (columns.size < 3) {
                continue
            }
            val type = columns[0]
            val code = columns[1]
            val rawName = columns[2]
            val name = StringEscapeUtils.unescapeJava(rawName)
            when (type) {
                "sz", "sh" -> result.add(StockerSuggestion(type.uppercase() + code, name, StockerMarketType.AShare))

                "hk" -> result.add(StockerSuggestion(code, name, StockerMarketType.HKStocks))

                "us" -> result.add(StockerSuggestion(code.split(".")[0].uppercase(), name, StockerMarketType.USStocks))
            }
        }
        return result
    }
}
