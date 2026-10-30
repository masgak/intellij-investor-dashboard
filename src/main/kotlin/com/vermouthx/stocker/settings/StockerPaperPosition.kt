package com.vermouthx.stocker.settings

class StockerPaperPosition {
    var code: String = ""
    var name: String = ""
    var shares: Int = 0
    var costPrice: Double = 0.0
    /** Shares bought on [boughtTodayDate]; subject to A-share T+1. */
    var boughtToday: Int = 0
    var boughtTodayDate: String = ""
}
