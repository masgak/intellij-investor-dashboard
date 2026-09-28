package com.vermouthx.stocker.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.diagnostic.Logger
import com.vermouthx.stocker.enums.StockerMarketType
import com.vermouthx.stocker.enums.StockerQuoteColorPattern
import com.vermouthx.stocker.enums.StockerQuoteProvider
import com.vermouthx.stocker.enums.StockerTableColumn
import com.vermouthx.stocker.utils.StockerPinyinUtil
import com.vermouthx.stocker.views.StockerTableView

@State(name = "Stocker", storages = [Storage("stocker-config.xml")])
class StockerSetting : PersistentStateComponent<StockerSettingState> {
    private var myState = StockerSettingState()

    private val log = Logger.getInstance(javaClass)

    companion object {
        val instance: StockerSetting
            get() = ApplicationManager.getApplication().getService(StockerSetting::class.java)
    }

    var version: String
        get() = myState.version
        set(value) {
            myState.version = value
            log.info("Stocker updated to $value")
        }

    var quoteProvider: StockerQuoteProvider
        get() = myState.quoteProvider
        set(value) {
            myState.quoteProvider = value
            log.info("Stocker stock quote provider switched to ${value.title}")
        }

    var cryptoQuoteProvider: StockerQuoteProvider
        get() = myState.cryptoQuoteProvider
        set(value) {
            myState.cryptoQuoteProvider = value
            log.info("Stocker crypto quote provider switched to ${value.title}")
        }

    var quoteColorPattern: StockerQuoteColorPattern
        get() = myState.quoteColorPattern
        set(value) {
            myState.quoteColorPattern = value
            log.info("Stocker quote color pattern switched to ${value.title}")
        }

    var displayNameWithPinyin: Boolean
        get() = myState.displayNameWithPinyin
        set(value) {
            myState.displayNameWithPinyin = value
            log.info("Stocker display name with pinyin set to $value")
        }

    var languageOverride: String
        get() = myState.languageOverride
        set(value) {
            myState.languageOverride = value
            log.info("Stocker language override set to $value")
        }

    var visibleTableColumns: List<String>
        get() {
            val stored = myState.visibleTableColumns
            if (stored.isEmpty()) return StockerTableColumn.defaultVisibleNames()
            if (stored.all { name -> StockerTableColumn.fromName(name) != null }) return stored
            val migrated = stored.mapNotNull { StockerTableColumn.migrateLocalizedTitle(it) }
            if (migrated.isNotEmpty()) {
                myState.visibleTableColumns = migrated.toMutableList()
                log.info("Migrated visibleTableColumns from localized titles to enum names: $migrated")
                return migrated
            }
            return StockerTableColumn.defaultVisibleNames()
        }
        set(value) {
            myState.visibleTableColumns = value.toMutableList()
            log.info("Stocker visible table columns updated: $value")
        }

    var refreshInterval: Long
        get() = myState.refreshInterval
        set(value) {
            myState.refreshInterval = value
            log.info("Stocker refresh interval set to $value")
        }

    var defaultSortColumn: String
        get() = myState.defaultSortColumn
        set(value) {
            myState.defaultSortColumn = value
            StockerTableView.refreshAllSorts()
        }

    var defaultSortDirection: String
        get() = myState.defaultSortDirection
        set(value) {
            myState.defaultSortDirection = value
            StockerTableView.refreshAllSorts()
        }

    fun getDefaultSortDirectionEnum(): com.vermouthx.stocker.enums.StockerSortState {
        return try {
            com.vermouthx.stocker.enums.StockerSortState.valueOf(defaultSortDirection)
        } catch (_: IllegalArgumentException) {
            com.vermouthx.stocker.enums.StockerSortState.DESCENDING
        }
    }

    var aShareList: MutableList<String>
        get() = myState.aShareList
        set(value) {
            myState.aShareList = value
            pruneGroupMembers()
        }

    var hkStocksList: MutableList<String>
        get() = myState.hkStocksList
        set(value) {
            myState.hkStocksList = value
            pruneGroupMembers()
        }

    var usStocksList: MutableList<String>
        get() = myState.usStocksList
        set(value) {
            myState.usStocksList = value
            pruneGroupMembers()
        }

    var cryptoList: MutableList<String>
        get() = myState.cryptoList
        set(value) {
            myState.cryptoList = value
            pruneGroupMembers()
        }

    var customStockNames: MutableMap<String, String>
        get() = myState.customStockNames
        set(value) {
            myState.customStockNames = value
        }

    var stockCostPrices: MutableMap<String, Double>
        get() = myState.stockCostPrices
        set(value) {
            myState.stockCostPrices = value
        }

    var stockHoldings: MutableMap<String, Int>
        get() = myState.stockHoldings
        set(value) {
            myState.stockHoldings = value
        }

    val allStockListSize: Int
        get() = aShareList.size + hkStocksList.size + usStocksList.size + cryptoList.size

    fun customGroupNames(): List<String> = myState.customGroups.map { it.name }

    fun copyCustomGroups(): Map<String, List<String>> {
        return myState.customGroups.associate { it.name to it.codes.toList() }
    }

    fun replaceCustomGroups(groups: Map<String, List<String>>) {
        myState.customGroups = groups.map { (name, codes) ->
            StockerCustomGroup().apply {
                this.name = name
                this.codes = codes.toMutableList()
            }
        }.toMutableList()
    }

    fun clearCustomGroups() {
        myState.customGroups.clear()
    }

    fun createGroup(name: String): Boolean {
        if (name.isBlank() || findGroup(name) != null) return false
        myState.customGroups.add(StockerCustomGroup().apply {
            this.name = name
            this.codes = mutableListOf()
        })
        return true
    }

    fun deleteGroup(name: String): Boolean {
        return myState.customGroups.removeAll { it.name == name }
    }

    fun renameGroup(oldName: String, newName: String): Boolean {
        if (newName.isBlank() || oldName == newName) return false
        if (findGroup(newName) != null) return false
        val group = findGroup(oldName) ?: return false
        group.name = newName
        return true
    }

    fun addToGroup(groupName: String, code: String): Boolean {
        val members = findGroup(groupName)?.codes ?: return false
        if (members.contains(code)) return false
        return members.add(code)
    }

    fun removeFromGroup(groupName: String, code: String): Boolean {
        val members = findGroup(groupName)?.codes ?: return false
        return members.remove(code)
    }

    fun getGroupMembers(groupName: String): List<String> {
        val codes = findGroup(groupName)?.codes ?: return emptyList()
        return codes.filter { containsCode(it) }
    }

    fun getGroupsForCode(code: String): List<String> {
        return myState.customGroups.filter { it.codes.contains(code) }.map { it.name }
    }

    private fun findGroup(name: String): StockerCustomGroup? {
        return myState.customGroups.firstOrNull { it.name == name }
    }

    private fun pruneGroupMembers() {
        myState.customGroups.forEach { group ->
            group.codes.removeAll { !containsCode(it) }
        }
    }

    fun setCustomName(code: String, customName: String) {
        customStockNames[code] = customName
        log.info("Custom name set for $code: $customName")
    }

    fun getCustomName(code: String): String? {
        return customStockNames[code]
    }

    fun removeCustomName(code: String) {
        customStockNames.remove(code)
        log.info("Custom name removed for $code")
    }

    fun setCostPrice(code: String, costPrice: Double) {
        val rounded = Math.round(costPrice * 1000.0) / 1000.0
        stockCostPrices[code] = rounded
        log.info("Cost price set for $code: $rounded")
    }

    fun getCostPrice(code: String): Double? {
        return stockCostPrices[code]
    }

    fun removeCostPrice(code: String) {
        stockCostPrices.remove(code)
        log.info("Cost price removed for $code")
    }

    fun setHoldings(code: String, holdings: Int) {
        stockHoldings[code] = holdings
        log.info("Holdings set for $code: $holdings")
    }

    fun getHoldings(code: String): Int? {
        return stockHoldings[code]
    }

    fun removeHoldings(code: String) {
        stockHoldings.remove(code)
        log.info("Holdings removed for $code")
    }

    fun getDisplayName(code: String, originalName: String): String {
        // Priority: Custom name > Pinyin mode > Original name
        customStockNames[code]?.let { return it }
        if (displayNameWithPinyin) {
            return StockerPinyinUtil.toPinyin(originalName)
        }
        return originalName
    }

    fun isTableColumnVisible(column: StockerTableColumn): Boolean {
        return visibleTableColumns.contains(column.name)
    }

    fun containsCode(code: String): Boolean {
        return aShareList.contains(code) ||
                hkStocksList.contains(code) ||
                usStocksList.contains(code) ||
                cryptoList.contains(code)
    }

    fun marketOf(code: String): StockerMarketType? {
        if (aShareList.contains(code)) {
            return StockerMarketType.AShare
        }
        if (hkStocksList.contains(code)) {
            return StockerMarketType.HKStocks
        }
        if (usStocksList.contains(code)) {
            return StockerMarketType.USStocks
        }
        if (cryptoList.contains(code)) {
            return StockerMarketType.Crypto
        }
        return null
    }

    fun removeCode(market: StockerMarketType, code: String) {
        when (market) {
            StockerMarketType.AShare -> {
                synchronized(aShareList) {
                    aShareList.remove(code)
                }
            }

            StockerMarketType.HKStocks -> {
                synchronized(hkStocksList) {
                    hkStocksList.remove(code)
                }
            }

            StockerMarketType.USStocks -> {
                synchronized(usStocksList) {
                    usStocksList.remove(code)
                }
            }

            StockerMarketType.Crypto -> {
                synchronized(cryptoList) {
                    cryptoList.remove(code)
                }
            }
        }
        if (!containsCode(code)) {
            myState.customGroups.forEach { it.codes.remove(code) }
        }
    }

    override fun getState(): StockerSettingState {
        return myState
    }

    override fun loadState(state: StockerSettingState) {
        myState = state
    }

}
