package com.github.tvbox.osc.search;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SearchBatchTest {

    @Test
    public void completesExpectedSourcesOnlyOnce() {
        SearchBatch batch = new SearchBatch(7L, Arrays.asList("a", "b"));

        assertEquals(1, batch.complete("a"));
        assertEquals(1, batch.complete("a"));
        assertEquals(0, batch.complete("b"));
        assertTrue(batch.isComplete());
    }

    @Test
    public void ignoresUnknownSource() {
        SearchBatch batch = new SearchBatch(7L, Arrays.asList("a", "b"));

        assertEquals(2, batch.complete("unknown"));
        assertFalse(batch.isComplete());
    }

    @Test
    public void emptyBatchIsImmediatelyComplete() {
        SearchBatch batch = new SearchBatch(7L, Collections.<String>emptyList());

        assertTrue(batch.isComplete());
    }

    @Test
    public void matchesOnlyItsId() {
        SearchBatch batch = new SearchBatch(7L, Arrays.asList("a", "b"));

        assertTrue(batch.matches(7L));
        assertFalse(batch.matches(8L));
    }
}
