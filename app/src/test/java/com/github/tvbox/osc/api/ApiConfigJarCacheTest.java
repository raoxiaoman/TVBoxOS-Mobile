package com.github.tvbox.osc.api;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ApiConfigJarCacheTest {

    // JVM coverage stops at the validated temp-to-cache publish boundary. OkGo callback delivery and
    // Android Context-backed cache-path selection require instrumentation or integration coverage.

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void publishMainJarRejectsInvalidDownloadWithoutReplacingCachedJar() throws Exception {
        File cache = temporaryFolder.newFile("csp.jar");
        byte[] original = "valid-cache".getBytes(StandardCharsets.UTF_8);
        writeBytes(cache, original);
        File temp = temporaryFolder.newFile("csp.jar.download");
        writeBytes(temp, "incomplete".getBytes(StandardCharsets.UTF_8));

        assertFalse(ApiConfig.publishMainJar(temp, cache, "", null));

        assertArrayEquals(original, Files.readAllBytes(cache.toPath()));
        assertTrue(temp.exists());
    }

    @Test
    public void publishMainJarPublishesCompatibleDownloadReadOnly() throws Exception {
        File cache = temporaryFolder.newFile("csp.jar");
        writeBytes(cache, "old".getBytes(StandardCharsets.UTF_8));
        File temp = temporaryFolder.newFile("csp.jar.download");
        writeJar(temp, "classes.dex");

        assertTrue(ApiConfig.publishMainJar(temp, cache, "", null));

        assertFalse(temp.exists());
        assertFalse(cache.canWrite());
    }

    private static void writeJar(File file, String... entries) throws Exception {
        try (ZipOutputStream output = new ZipOutputStream(new FileOutputStream(file))) {
            for (String entry : entries) {
                output.putNextEntry(new ZipEntry(entry));
                output.write(entry.getBytes(StandardCharsets.UTF_8));
                output.closeEntry();
            }
        }
    }

    private static void writeBytes(File file, byte[] bytes) throws Exception {
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(bytes);
        }
    }
}
