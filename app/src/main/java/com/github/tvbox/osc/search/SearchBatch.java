package com.github.tvbox.osc.search;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public final class SearchBatch {
    private final long id;
    private final Set<String> expected;
    private final Set<String> completed;

    public SearchBatch(long id, Collection<String> expected) {
        this.id = id;
        this.expected = new HashSet<>(expected);
        this.completed = new HashSet<>();
    }

    public long getId() {
        return id;
    }

    public boolean matches(long batchId) {
        return id == batchId;
    }

    public synchronized int complete(String sourceKey) {
        if (expected.contains(sourceKey)) {
            completed.add(sourceKey);
        }
        return expected.size() - completed.size();
    }

    public synchronized boolean isComplete() {
        return completed.containsAll(expected);
    }
}
