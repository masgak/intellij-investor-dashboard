package com.vermouthx.stocker.entities

data class StockerScreenRow(
    val code: String,
    val name: String,
    val industry: String,
    val price: Double,
    val pe: Double,
    val minRoe: Double,
    val minGrossMargin: Double,
    val debtRatio: Double,
    val cashRatio: Double,
    val rawGrowth: Double,
    val usedGrowth: Double,
    val growthAdjustment: String,
    val zone: String,
    val capToBuy: Double,
    val marketCapYi: Double,
    val buyCapYi: Double,
    val fairCapYi: Double,
    val cashDiscount: Boolean,
    val leverageDiscount: Boolean,
)

data class StockerScreenSnapshot(
    val rows: List<StockerScreenRow>,
    val ranAtMillis: Long,
    val running: Boolean,
    val error: String?,
    val strategy: String = "TANG",
)
