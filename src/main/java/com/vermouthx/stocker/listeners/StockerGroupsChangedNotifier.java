package com.vermouthx.stocker.listeners;

import com.intellij.util.messages.Topic;

public interface StockerGroupsChangedNotifier {
    Topic<StockerGroupsChangedNotifier> STOCKER_GROUPS_CHANGED_TOPIC =
        Topic.create("StockerGroupsChangedTopic", StockerGroupsChangedNotifier.class);

    void groupsChanged();
}
