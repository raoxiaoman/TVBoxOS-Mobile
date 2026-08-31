package com.github.tvbox.osc.event;

import com.github.tvbox.osc.bean.AbsXml;

public final class SearchResultEvent {
    private final long batchId;
    private final String sourceKey;
    private final AbsXml data;

    public SearchResultEvent(long batchId, String sourceKey, AbsXml data) {
        this.batchId = batchId;
        this.sourceKey = sourceKey;
        this.data = data;
    }

    public long getBatchId() {
        return batchId;
    }

    public String getSourceKey() {
        return sourceKey;
    }

    public AbsXml getData() {
        return data;
    }
}
