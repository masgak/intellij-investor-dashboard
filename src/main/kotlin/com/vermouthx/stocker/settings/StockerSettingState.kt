package com.vermouthx.stocker.settings

import com.vermouthx.stocker.enums.StockerQuoteColorPattern
import com.vermouthx.stocker.enums.StockerQuoteProvider
import com.vermouthx.stocker.enums.StockerTableColumn

class StockerSettingState {
    var version: String = ""
    var refreshInterval: Long = 5
    var quoteProvider: StockerQuoteProvider = StockerQuoteProvider.SINA
    var usmartChannel: String = ""
    var usmartAreaCode: String = "86"
    var usmartAccount: String = ""
    var cryptoQuoteProvider: StockerQuoteProvider = StockerQuoteProvider.SINA
    var quoteColorPattern: StockerQuoteColorPattern = StockerQuoteColorPattern.RED_UP_GREEN_DOWN
    var displayNameWithPinyin: Boolean = false
    var languageOverride: String = "" // Empty string means follow system language
    var visibleTableColumns: MutableList<String> = mutableListOf() // Empty list will be populated with defaults on first access
    var profileColumnsAdded: Boolean = false
    var aShareList: MutableList<String> = mutableListOf()
    var hkStocksList: MutableList<String> = mutableListOf()
    var usStocksList: MutableList<String> = mutableListOf()
    var cryptoList: MutableList<String> = mutableListOf()
    var customStockNames: MutableMap<String, String> = mutableMapOf()
    var stockCostPrices: MutableMap<String, Double> = mutableMapOf()
    var stockHoldings: MutableMap<String, Int> = mutableMapOf()
    var defaultSortColumn: String = ""
    var defaultSortDirection: String = "DESCENDING"
    var defaultSortMigratedToManual: Boolean = false
    var customGroups: MutableList<StockerCustomGroup> = mutableListOf()
    var screenAuto: Boolean = true
    var screenStrategy: String = "TANG"
    var screenLastDay: String = ""
    var screenRanAt: Long = 0
    var screenPicks: MutableList<StockerScreenPickState> = mutableListOf()
    var aiBaseUrl: String = "https://api.x.ai/v1"
    var aiThinking: String = "medium"
    var aiModels: MutableList<String> = mutableListOf()
    var aiActiveModel: String = ""
    var paperCash: Double = 100_000.0
    var paperInitialCash: Double = 100_000.0
    var paperPositions: MutableList<StockerPaperPosition> = mutableListOf()
}

class StockerCustomGroup {
    var name: String = ""
    var codes: MutableList<String> = mutableListOf()
}