package com.vermouthx.stocker.listeners

import com.intellij.util.messages.Topic
import com.vermouthx.stocker.entities.StockerScreenSnapshot

interface StockerScreenNotifier {
    fun onScreenChanged(snapshot: StockerScreenSnapshot)

    companion object {
        @JvmField
        val TOPIC: Topic<StockerScreenNotifier> = Topic.create("StockerScreen", StockerScreenNotifier::class.java)
    }
}
