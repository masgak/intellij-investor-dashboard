package com.vermouthx.stocker.enums

import com.vermouthx.stocker.StockerBundle

enum class StockerQuoteProvider(
    val titleKey: String, val host: String, val suggestHost: String, val providerPrefixMap: Map<StockerMarketType, String>
) {
    /**
     * Sina API
     */
    SINA(
        titleKey = "provider.sina",
        host = "https://hq.sinajs.cn/list=",
        suggestHost = "https://suggest3.sinajs.cn/suggest/key=",
        providerPrefixMap = mapOf(
            StockerMarketType.AShare to "",
            StockerMarketType.HKStocks to "hk",
            StockerMarketType.USStocks to "gb_",
            StockerMarketType.Crypto to "btc_"
        )
    ),

    /**
     * Tencent API
     */
    TENCENT(
        titleKey = "provider.tencent",
        host = "https://qt.gtimg.cn/q=",
        suggestHost = "https://smartbox.gtimg.cn/s3/?v=2&t=all&c=1&q=",
        providerPrefixMap = mapOf(
            StockerMarketType.AShare to "",
            StockerMarketType.HKStocks to "hk",
            StockerMarketType.USStocks to "us",
        )
    ),

    /**
     * uSMART Open API. Quotes require an approved channel, keys, and a login token.
     * Crypto is not served here; search falls back to Sina.
     */
    USMART(
        titleKey = "provider.usmart",
        host = "https://open-hz.yxzq.com:8443",
        suggestHost = "https://suggest3.sinajs.cn/suggest/key=",
        providerPrefixMap = mapOf(
            StockerMarketType.AShare to "",
            StockerMarketType.HKStocks to "hk",
            StockerMarketType.USStocks to "us",
        )
    );

    val title: String
        get() = StockerBundle.message(titleKey)

    companion object {
        fun fromTitle(title: String): StockerQuoteProvider {
            return when (title) {
                SINA.title -> SINA
                TENCENT.title -> TENCENT
                USMART.title -> USMART
                else -> SINA
            }
        }
    }

}
