package com.vermouthx.stocker.settings

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.passwordSafe.PasswordSafe
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
            if (stored.all { name -> StockerTableColumn.fromName(name) != null }) {
                return withProfileColumns(stored)
            }
            val migrated = stored.mapNotNull { StockerTableColumn.migrateLocalizedTitle(it) }
            if (migrated.isNotEmpty()) {
                myState.visibleTableColumns = migrated.toMutableList()
                log.info("Migrated visibleTableColumns from localized titles to enum names: $migrated")
                return withProfileColumns(migrated)
            }
            return StockerTableColumn.defaultVisibleNames()
        }
        set(value) {
            myState.visibleTableColumns = value.toMutableList()
            myState.profileColumnsAdded = true
            log.info("Stocker visible table columns updated: $value")
        }

    private fun withProfileColumns(stored: List<String>): List<String> {
        if (myState.profileColumnsAdded) {
            return stored
        }
        val updated = stored.toMutableList()
        val nameIndex = updated.indexOf(StockerTableColumn.NAME.name)
        var insertAt = if (nameIndex >= 0) nameIndex + 1 else updated.size
        if (StockerTableColumn.INDUSTRY.name !in updated) {
            updated.add(insertAt, StockerTableColumn.INDUSTRY.name)
        }
        insertAt = updated.indexOf(StockerTableColumn.INDUSTRY.name) + 1
        if (StockerTableColumn.BOARD.name !in updated) {
            updated.add(insertAt, StockerTableColumn.BOARD.name)
        }
        myState.visibleTableColumns = updated
        myState.profileColumnsAdded = true
        return updated
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

    var screenAuto: Boolean
        get() = myState.screenAuto
        set(value) {
            myState.screenAuto = value
        }

    var screenStrategy: String
        get() = myState.screenStrategy.ifBlank { "TANG" }
        set(value) {
            myState.screenStrategy = value
        }

    var screenLastDay: String
        get() = myState.screenLastDay
        set(value) {
            myState.screenLastDay = value
        }

    var screenRanAt: Long
        get() = myState.screenRanAt
        set(value) {
            myState.screenRanAt = value
        }

    fun savedScreenRows(): List<com.vermouthx.stocker.entities.StockerScreenRow> {
        return myState.screenPicks.mapNotNull { it.toRow() }
    }

    fun replaceScreenPicks(picks: List<StockerScreenPickState>) {
        myState.screenPicks = picks.toMutableList()
    }

    var aiBaseUrl: String
        get() = myState.aiBaseUrl.ifBlank { "https://api.x.ai/v1" }
        set(value) {
            myState.aiBaseUrl = value.trim().ifBlank { "https://api.x.ai/v1" }
        }

    var aiThinking: String
        get() = myState.aiThinking.ifBlank { "medium" }
        set(value) {
            myState.aiThinking = value
        }

    var aiModels: List<String>
        get() = myState.aiModels.toList()
        set(value) {
            myState.aiModels = value.map { it.trim() }.filter { it.isNotEmpty() }.distinct().toMutableList()
            if (aiActiveModel !in myState.aiModels) {
                myState.aiActiveModel = myState.aiModels.firstOrNull().orEmpty()
            }
        }

    var aiActiveModel: String
        get() = myState.aiActiveModel.ifBlank { myState.aiModels.firstOrNull().orEmpty() }
        set(value) {
            myState.aiActiveModel = value.trim()
        }

    var usmartChannel: String
        get() = myState.usmartChannel
        set(value) {
            myState.usmartChannel = value.trim()
        }

    var usmartAreaCode: String
        get() = myState.usmartAreaCode.ifBlank { "86" }
        set(value) {
            myState.usmartAreaCode = value.trim().ifBlank { "86" }
        }

    var usmartAccount: String
        get() = myState.usmartAccount
        set(value) {
            myState.usmartAccount = value.trim()
        }

    var usmartPassword: String
        get() = PasswordSafe.instance.getPassword(usmartAttributes("password")).orEmpty()
        set(value) {
            storeSecret(usmartAttributes("password"), value)
        }

    var usmartPrivateKey: String
        get() = PasswordSafe.instance.getPassword(usmartAttributes("private-key")).orEmpty()
        set(value) {
            storeSecret(usmartAttributes("private-key"), value)
        }

    var usmartPublicKey: String
        get() = PasswordSafe.instance.getPassword(usmartAttributes("public-key")).orEmpty()
        set(value) {
            storeSecret(usmartAttributes("public-key"), value)
        }

    private fun usmartAttributes(item: String): CredentialAttributes {
        return CredentialAttributes(generateServiceName("Stocker", "usmart-$item"))
    }

    private fun storeSecret(attributes: CredentialAttributes, value: String) {
        if (value.isBlank()) {
            PasswordSafe.instance.set(attributes, null)
        } else {
            PasswordSafe.instance.set(attributes, Credentials("stocker", value))
        }
    }

    var aiApiKey: String
        get() = PasswordSafe.instance.getPassword(aiKeyAttributes()).orEmpty()
        set(value) {
            val attributes = aiKeyAttributes()
            if (value.isBlank()) {
                PasswordSafe.instance.set(attributes, null)
            } else {
                PasswordSafe.instance.set(attributes, Credentials("stocker", value))
            }
        }

    private fun aiKeyAttributes(): CredentialAttributes {
        return CredentialAttributes(generateServiceName("Stocker", "screen-ai"))
    }

    var paperCash: Double
        get() = myState.paperCash
        set(value) {
            myState.paperCash = value
        }

    var paperInitialCash: Double
        get() = myState.paperInitialCash
        set(value) {
            myState.paperInitialCash = value
        }

    fun paperPositions(): List<StockerPaperPosition> = myState.paperPositions.toList()

    fun paperSellableShares(code: String): Int {
        val existing = myState.paperPositions.firstOrNull { it.code == code } ?: return 0
        return (existing.shares - paperLockedShares(existing)).coerceAtLeast(0)
    }

    fun paperBuy(code: String, name: String, shares: Int, price: Double): String? {
        if (shares <= 0 || price <= 0.0) {
            return "invalid"
        }
        val cost = shares * price
        if (cost > myState.paperCash + 1e-6) {
            return "cash"
        }
        myState.paperCash -= cost
        val existing = myState.paperPositions.firstOrNull { it.code == code }
        if (existing == null) {
            myState.paperPositions.add(StockerPaperPosition().apply {
                this.code = code
                this.name = name
                this.shares = shares
                this.costPrice = price
                recordPaperBuy(this, shares)
            })
        } else {
            val total = existing.shares * existing.costPrice + cost
            existing.shares += shares
            existing.costPrice = total / existing.shares
            if (name.isNotBlank()) {
                existing.name = name
            }
            recordPaperBuy(existing, shares)
        }
        return null
    }

    fun paperSell(code: String, shares: Int, price: Double): String? {
        val existing = myState.paperPositions.firstOrNull { it.code == code } ?: return "position"
        if (shares <= 0 || price <= 0.0) {
            return "invalid"
        }
        if (shares > paperSellableShares(code)) {
            return if (paperLockedShares(existing) > 0) "t1" else "invalid"
        }
        myState.paperCash += shares * price
        existing.shares -= shares
        if (existing.shares == 0) {
            myState.paperPositions.remove(existing)
        }
        return null
    }

    private fun recordPaperBuy(position: StockerPaperPosition, shares: Int) {
        if (!isPaperT1Market(position.code)) {
            return
        }
        val today = paperTradeDay()
        if (position.boughtTodayDate != today) {
            position.boughtToday = 0
            position.boughtTodayDate = today
        }
        position.boughtToday += shares
    }

    private fun paperLockedShares(position: StockerPaperPosition): Int {
        if (!isPaperT1Market(position.code)) {
            return 0
        }
        return if (position.boughtTodayDate == paperTradeDay()) position.boughtToday else 0
    }

    private fun isPaperT1Market(code: String): Boolean {
        if (marketOf(code) == StockerMarketType.AShare) {
            return true
        }
        val upper = code.uppercase()
        return upper.startsWith("SH") || upper.startsWith("SZ") || upper.startsWith("BJ")
    }

    private fun paperTradeDay(): String {
        return java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")).toString()
    }

    fun paperReset(amount: Double) {
        myState.paperPositions.clear()
        myState.paperInitialCash = amount
        myState.paperCash = amount
    }

    override fun getState(): StockerSettingState {
        return myState
    }

    override fun loadState(state: StockerSettingState) {
        myState = state
        if (!myState.defaultSortMigratedToManual) {
            if (myState.defaultSortColumn == "CHANGE_PERCENT") {
                myState.defaultSortColumn = ""
            }
            myState.defaultSortMigratedToManual = true
        }
    }

}
