package com.github.tvbox.osc.search;

import com.github.tvbox.osc.util.live.LiveRetry;
import com.google.gson.Gson;
import com.github.tvbox.osc.util.live.LiveStore;
import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.*;

public class LiveStateTest {
    @Test public void exhaustedLinesNeverReturnAnotherChannel() {
        LiveRetry retry = new LiveRetry();
        retry.begin(3, 1);
        assertEquals(2, retry.next());
        assertEquals(0, retry.next());
        assertEquals(-1, retry.next());
        assertEquals(-1, retry.next());
        retry.begin(1, 0);
        assertEquals(-1, retry.next());
    }
    @Test public void playingStartsOneNewBoundedRecoveryCycle() {
        LiveRetry retry = new LiveRetry();
        retry.begin(2, 0);
        assertEquals(1, retry.next());
        retry.played();
        assertEquals(0, retry.next());
        assertEquals(-1, retry.next());
    }
    @Test public void migrationIsIdempotentAndKeepsCurrentSource() {
        LiveStore store = new LiveStore(new LiveStore.State());
        store.migrate("https://example.org/a", Arrays.asList("https://example.org/a", "https://example.org/b"));
        String id = store.current().id;
        store.migrate("https://example.org/a", Arrays.asList("https://example.org/a"));
        assertEquals(3, store.subscriptions().size());
        assertEquals(id, store.current().id);
        store.rename(id, "足球");
        assertEquals(id, store.current().id);
        assertEquals("足球", store.current().name);
    }
    @Test public void favoritesAndRecentAreScopedAndBounded() {
        LiveStore store = new LiveStore(new LiveStore.State());
        assertTrue(store.toggleFavorite("a", "channel"));
        assertTrue(store.favorites("b").isEmpty());
        for (int i = 0; i < 40; i++) { store.recordPlayed("a", "ch" + i); }
        store.recordPlayed("a", "ch39");
        assertEquals(30, store.recent("a").size());
        assertEquals("ch39", store.last("a"));
        assertTrue(store.recent("b").isEmpty());
        assertFalse(store.toggleFavorite("a", "channel"));
    }
    @Test public void followingModeSeparatesVodSubscriptions() {
        LiveStore store = new LiveStore(new LiveStore.State());
        assertNotEquals(store.scope("a"), store.scope("b"));
        LiveStore.Subscription sub = store.upsert("独立", "https://example.org/live");
        store.select(sub.id);
        assertEquals(store.scope("a"), store.scope("b"));
    }
    @Test public void persistenceRoundTripRetainsIdentityAndScopedHistory() {
        LiveStore.State state = new LiveStore.State();
        LiveStore original = new LiveStore(state);
        LiveStore.Subscription source = original.upsert("足球", "https://example.org/live");
        original.select(source.id);
        original.toggleFavorite(original.scope("vod"), "one");
        original.recordPlayed(original.scope("vod"), "one");
        Gson gson = new Gson();
        LiveStore restored = new LiveStore(gson.fromJson(gson.toJson(state), LiveStore.State.class));
        assertEquals(source.id, restored.current().id);
        assertEquals(original.favorites(original.scope("vod")), restored.favorites(restored.scope("vod")));
        assertEquals("one", restored.last(restored.scope("vod")));
        assertEquals(source.id, restored.upsert("新名称", source.url).id);
        assertEquals(2, restored.subscriptions().size());
    }
}
