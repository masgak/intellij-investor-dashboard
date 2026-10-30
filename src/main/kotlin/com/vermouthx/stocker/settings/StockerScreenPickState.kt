package com.vermouthx.stocker.settings

import com.vermouthx.stocker.entities.StockerScreenRow

class StockerScreenPickState {
    var code: String = ""
    var name: String = ""
    var industry: String = ""
    var price: Double = 0.0
    var pe: Double = 0.0
    var minRoe: Double = 0.0
    var minGrossMargin: Double = 0.0
    var debtRatio: Double = 0.0
    var cashRatio: Double = 0.0
    var rawGrowth: Double = 0.0
    var usedGrowth: Double = 0.0
    var growthAdjustment: String = ""
    var zone: String = ""
    var capToBuy: Double = 0.0
    var marketCapYi: Double = 0.0
    var buyCapYi: Double = 0.0
    var fairCapYi: Double = 0.0
    var cashDiscount: Boolean = false
    var leverageDiscount: Boolean = false

    fun toRow(): StockerScreenRow? {
        if (code.isBlank()) {
            return null
        }
        return StockerScreenRow(
            code = code,
            name = name,
            industry = industry,
            price = price,
            pe = pe,
            minRoe = minRoe,
            minGrossMargin = minGrossMargin,
            debtRatio = debtRatio,
            cashRatio = cashRatio,
            rawGrowth = rawGrowth,
            usedGrowth = usedGrowth,
            growthAdjustment = growthAdjustment,
            zone = zone,
            capToBuy = capToBuy,
            marketCapYi = marketCapYi,
            buyCapYi = buyCapYi,
            fairCapYi = fairCapYi,
            cashDiscount = cashDiscount,
            leverageDiscount = leverageDiscount,
        )
    }

    companion object {
        fun from(row: StockerScreenRow): StockerScreenPickState {
            return StockerScreenPickState().apply {
                code = row.code
                name = row.name
                industry = row.industry
                price = row.price
                pe = row.pe
                minRoe = row.minRoe
                minGrossMargin = row.minGrossMargin
                debtRatio = row.debtRatio
                cashRatio = row.cashRatio
                rawGrowth = row.rawGrowth
                usedGrowth = row.usedGrowth
                growthAdjustment = row.growthAdjustment
                zone = row.zone
                capToBuy = row.capToBuy
                marketCapYi = row.marketCapYi
                buyCapYi = row.buyCapYi
                fairCapYi = row.fairCapYi
                cashDiscount = row.cashDiscount
                leverageDiscount = row.leverageDiscount
            }
        }
    }
}
