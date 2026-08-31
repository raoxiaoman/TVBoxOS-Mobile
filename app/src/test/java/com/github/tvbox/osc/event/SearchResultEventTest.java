package com.github.tvbox.osc.event;

import com.github.tvbox.osc.bean.AbsXml;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public class SearchResultEventTest {

    @Test
    public void constructorExposesSearchResultMetadata() {
        AbsXml data = new AbsXml();
        SearchResultEvent event = new SearchResultEvent(42L, "source-key", data);

        assertEquals(42L, event.getBatchId());
        assertEquals("source-key", event.getSourceKey());
        assertSame(data, event.getData());
    }

    @Test
    public void constructorExposesEmptySearchResultMetadata() {
        SearchResultEvent event = new SearchResultEvent(0L, "", null);

        assertEquals(0L, event.getBatchId());
        assertEquals("", event.getSourceKey());
        assertNull(event.getData());
    }
}
