package com.vermouthx.stocker.backtest

import kotlin.math.pow

/**
 * Bar-by-bar engine shaped like vn.py and backtrader:
 * daily bars in, a strategy that only sees the current bar,
 * a broker that fills whole A-share lots, then return and drawdown.
 * Fills use that bar's close. Commission is 0.03 percent with a 5 yuan floor.
 * Sells also pay the 0.05 percent stamp tax.
 */
internal object BacktestEngine {

    private const val LOT = 100
    private const val COMMISSION = 0.0003
    private const val MIN_COMMISSION = 5.0
    private const val STAMP_TAX = 0.0005
    private const val TRADING_DAYS = 252.0

    fun run(code: String, bars: List<Bar>, strategy: BacktestStrategy, startingCash: Double = 100_000.0): BacktestReport {
        val broker = Broker(startingCash)
        val equity = ArrayList<Double>(bars.size)
        bars.forEachIndexed { index, _ ->
            strategy.onBar(BarContext(bars, index, broker))
            equity += broker.equity(bars[index].close)
        }
        val end = equity.lastOrNull() ?: startingCash
        val totalReturn = if (startingCash == 0.0) 0.0 else end / startingCash - 1.0
        val annualized = if (bars.size < 2) {
            0.0
        } else {
            (1.0 + totalReturn).pow(TRADING_DAYS / bars.size) - 1.0
        }
        return BacktestReport(
            code = code,
            barCount = bars.size,
            totalReturn = totalReturn,
            annualized = annualized,
            maxDrawdown = maxDrawdown(equity),
            trades = broker.trades.toList(),
            winRate = winRate(broker.trades),
            endEquity = end,
        )
    }

    private fun maxDrawdown(equity: List<Double>): Double {
        var peak = equity.firstOrNull() ?: return 0.0
        var worst = 0.0
        equity.forEach { value ->
            if (value > peak) {
                peak = value
            }
            if (peak > 0.0) {
                worst = minOf(worst, value / peak - 1.0)
            }
        }
        return worst
    }

    private fun winRate(trades: List<Fill>): Double {
        var entryCost = 0.0
        var wins = 0
        var closed = 0
        trades.forEach { fill ->
            if (fill.side == SIDE_BUY) {
                entryCost = fill.price * fill.shares + fill.fee
            } else if (entryCost > 0.0) {
                val proceeds = fill.price * fill.shares - fill.fee
                if (proceeds > entryCost) {
                    wins++
                }
                closed++
                entryCost = 0.0
            }
        }
        return if (closed == 0) 0.0 else wins.toDouble() / closed
    }
}

internal class Broker(startingCash: Double) {
    var cash = startingCash
        private set
    var shares = 0
        private set
    val trades = ArrayList<Fill>()

    fun equity(price: Double): Double = cash + shares * price

    fun buyAll(bar: Bar) {
        if (shares > 0 || bar.close <= 0.0) {
            return
        }
        var lots = (cash / (bar.close * (1.0 + 0.0003))).toInt() / 100 * 100
        while (lots >= 100) {
            val notional = lots * bar.close
            val fee = maxOf(notional * 0.0003, 5.0)
            if (notional + fee <= cash + 1e-6) {
                cash -= notional + fee
                shares = lots
                trades += Fill(bar.date, SIDE_BUY, lots, bar.close, fee)
                return
            }
            lots -= 100
        }
    }

    fun sellAll(bar: Bar) {
        if (shares <= 0) {
            return
        }
        val notional = shares * bar.close
        val fee = maxOf(notional * 0.0003, 5.0) + notional * 0.0005
        cash += notional - fee
        trades += Fill(bar.date, SIDE_SELL, shares, bar.close, fee)
        shares = 0
    }
}

internal interface BacktestStrategy {
    val id: String
    fun onBar(ctx: BarContext)
}

internal class BarContext(
    private val bars: List<Bar>,
    val index: Int,
    private val broker: Broker,
) {
    val bar: Bar get() = bars[index]
    val inPosition: Boolean get() = broker.shares > 0

    fun sma(period: Int): Double? = averageClose(index - period + 1, index)

    fun previousSma(period: Int): Double? = averageClose(index - period, index - 1)

    fun priorHigh(period: Int): Double? = extreme(index - period, index - 1, high = true)

    fun priorLow(period: Int): Double? = extreme(index - period, index - 1, high = false)

    fun buy() = broker.buyAll(bar)

    fun sell() = broker.sellAll(bar)

    private fun averageClose(from: Int, to: Int): Double? {
        if (from < 0 || to >= bars.size || from > to) {
            return null
        }
        var sum = 0.0
        for (i in from..to) {
            sum += bars[i].close
        }
        return sum / (to - from + 1)
    }

    private fun extreme(from: Int, to: Int, high: Boolean): Double? {
        if (from < 0 || to >= bars.size || from > to) {
            return null
        }
        var value = if (high) Double.NEGATIVE_INFINITY else Double.POSITIVE_INFINITY
        for (i in from..to) {
            val price = if (high) bars[i].high else bars[i].low
            value = if (high) maxOf(value, price) else minOf(value, price)
        }
        return value
    }
}

internal object BacktestStrategies {
    val buyHold = object : BacktestStrategy {
        override val id = "HOLD"
        override fun onBar(ctx: BarContext) {
            if (ctx.index == 0) {
                ctx.buy()
            }
        }
    }

    val maCross = object : BacktestStrategy {
        override val id = "MA"
        override fun onBar(ctx: BarContext) {
            val fast = ctx.sma(5) ?: return
            val slow = ctx.sma(20) ?: return
            val previousFast = ctx.previousSma(5) ?: return
            val previousSlow = ctx.previousSma(20) ?: return
            val crossedUp = previousFast <= previousSlow && fast > slow
            val crossedDown = previousFast >= previousSlow && fast < slow
            if (crossedUp && !ctx.inPosition) {
                ctx.buy()
            } else if (crossedDown && ctx.inPosition) {
                ctx.sell()
            }
        }
    }

    val turtle = object : BacktestStrategy {
        override val id = "TURTLE"
        override fun onBar(ctx: BarContext) {
            val entry = ctx.priorHigh(20) ?: return
            if (!ctx.inPosition && ctx.bar.close >= entry) {
                ctx.buy()
                return
            }
            val exit = ctx.priorLow(10) ?: return
            if (ctx.inPosition && ctx.bar.close <= exit) {
                ctx.sell()
            }
        }
    }

    fun fromId(id: String): BacktestStrategy = when (id) {
        maCross.id -> maCross
        turtle.id -> turtle
        else -> buyHold
    }
}

internal data class Bar(
    val date: String,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
)

internal data class Fill(
    val date: String,
    val side: String,
    val shares: Int,
    val price: Double,
    val fee: Double,
)

internal data class BacktestReport(
    val code: String,
    val barCount: Int,
    val totalReturn: Double,
    val annualized: Double,
    val maxDrawdown: Double,
    val trades: List<Fill>,
    val winRate: Double,
    val endEquity: Double,
)

internal const val SIDE_BUY = "BUY"
internal const val SIDE_SELL = "SELL"
