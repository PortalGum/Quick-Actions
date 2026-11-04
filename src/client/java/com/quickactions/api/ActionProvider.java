package com.quickactions.api;

import java.util.List;

public interface ActionProvider<T> {
    ActionCategory category();

    /** item order = menu order */
    List<ActionEntry<T>> entries();
}