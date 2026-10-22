package com.github.tvbox.osc.api;

import com.github.catvod.utils.GenerationPublisher;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ConfigCacheTest {
    private static final String ORIGINAL = "{\"sites\":[{\"key\":\"a\",\"name\":\"源A\",\"type\":1,\"api\":\"https://example.com/api\"}]}";
    private static final String UPDATED = ORIGINAL.replace("源A", "源B");

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void readsExistingSubscriptionCacheWithoutMigration() throws Exception {
        File file = folder.newFile("existing-url-hash");
        Files.write(file.toPath(), ORIGINAL.getBytes(StandardCharsets.UTF_8));
        assertEquals(ORIGINAL, new ConfigCache(file).read());
    }

    @Test
    public void invalidRefreshKeepsPreviousCache() throws Exception {
        File file = new File(folder.getRoot(), "subscription");
        ConfigCache cache = new ConfigCache(file);
        cache.write(ORIGINAL);
        for (String bad : new String[]{"<html>error</html>", "{}", "{\"sites\":[{}]}",
                ORIGINAL.replace("\"type\":1", "\"type\":\"bad\""),
                ORIGINAL.substring(0, ORIGINAL.length() - 1) + ",\"parses\":[{\"name\":\"broken\"}]}"}) {
            try {
                cache.write(bad);
                fail("无效配置不能覆盖缓存");
            } catch (Exception expected) {
                assertEquals(ORIGINAL, cache.read());
            }
        }
    }

    @Test
    public void updatesCacheAndSkipsIdenticalContent() throws Exception {
        ConfigCache cache = new ConfigCache(new File(folder.getRoot(), "subscription"));
        assertTrue(cache.write(ORIGINAL));
        assertFalse(cache.write(ORIGINAL));
        assertTrue(cache.write(UPDATED));
        assertEquals(UPDATED, cache.read());
    }

    @Test
    public void restoresBackupAfterInterruptedPublish() throws Exception {
        File file = new File(folder.getRoot(), "subscription");
        File backup = new File(folder.getRoot(), "subscription.bak");
        Files.write(backup.toPath(), ORIGINAL.getBytes(StandardCharsets.UTF_8));
        assertEquals(ORIGINAL, new ConfigCache(file).read());
        assertTrue(file.exists());
    }

    @Test
    public void sourcesUseIndependentCacheFiles() throws Exception {
        ConfigCache a = new ConfigCache(new File(folder.getRoot(), "a"));
        ConfigCache b = new ConfigCache(new File(folder.getRoot(), "b"));
        a.write(ORIGINAL);
        b.write(UPDATED);
        assertEquals(ORIGINAL, a.read());
        assertEquals(UPDATED, b.read());
    }

    @Test
    public void validatesRulesAndIjkBeforePublishing() throws Exception {
        ConfigCache cache = new ConfigCache(new File(folder.getRoot(), "subscription"));
        cache.write(ORIGINAL);
        for (String extra : new String[]{",\"rules\":[{\"host\":\"x\",\"rule\":{}}]",
                ",\"ijk\":[{\"group\":\"x\",\"options\":[{}]}]"}) {
            try {
                cache.write(ORIGINAL.substring(0, ORIGINAL.length() - 1) + extra + "}");
                fail("无效的可选配置不能覆盖缓存");
            } catch (Exception expected) {
                assertEquals(ORIGINAL, cache.read());
            }
        }
    }

    @Test
    public void supersededRefreshCannotReplaceNewConfiguration() throws Exception {
        ConfigCache cache = new ConfigCache(new File(folder.getRoot(), "subscription"));
        GenerationPublisher generation = new GenerationPublisher();
        long stale = generation.current();
        cache.write(ORIGINAL);
        generation.advance();
        assertTrue(generation.publishIfCurrent(generation.current(), () -> cache.write(UPDATED)));
        assertFalse(generation.publishIfCurrent(stale, () -> cache.write(ORIGINAL)));
        assertEquals(UPDATED, cache.read());
    }

    @Test
    public void completedPublishWinsOverLeftoverBackup() throws Exception {
        File file = new File(folder.getRoot(), "subscription");
        Files.write(file.toPath(), UPDATED.getBytes(StandardCharsets.UTF_8));
        Files.write(new File(folder.getRoot(), "subscription.bak").toPath(),
                ORIGINAL.getBytes(StandardCharsets.UTF_8));
        assertEquals(UPDATED, new ConfigCache(file).read());
    }

    @Test
    public void acceptsObjectExtAndStringNumericType() {
        String json = ORIGINAL.replace("\"type\":1", "\"type\":\"3\",\"ext\":{\"token\":\"value\"}");
        assertEquals(1, ConfigCache.parse(json).getAsJsonArray("sites").size());
    }
}
