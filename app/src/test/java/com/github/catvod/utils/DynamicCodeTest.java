package com.github.catvod.utils;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DynamicCodeTest {

    private static final String REQUIRED_ENTRY = "classes.dex";
    private static final String BLOCKED_ENTRY = "assets/ftyshinidie.guard";

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void incompleteZipIsRejected() throws Exception {
        File jar = temporaryFolder.newFile("incomplete.jar");
        writeBytes(jar, "not a zip".getBytes(StandardCharsets.UTF_8));

        assertEquals(DynamicCode.JarInspection.INVALID,
                DynamicCode.inspectJar(jar, REQUIRED_ENTRY, BLOCKED_ENTRY));
    }

    @Test
    public void inspectJarReturnsInvalidWhenRequiredEntryIsMissing() throws Exception {
        File jar = createZip("missing-classes.jar", "only.txt");

        assertEquals(DynamicCode.JarInspection.INVALID,
                DynamicCode.inspectJar(jar, REQUIRED_ENTRY, BLOCKED_ENTRY));
    }

    @Test
    public void inspectJarReturnsIncompatibleForBlockedEntry() throws Exception {
        File jar = createZip("incompatible.jar", REQUIRED_ENTRY, BLOCKED_ENTRY);

        assertEquals(DynamicCode.JarInspection.INCOMPATIBLE,
                DynamicCode.inspectJar(jar, REQUIRED_ENTRY, BLOCKED_ENTRY));
    }

    @Test
    public void inspectJarReturnsCompatibleForRequiredEntryOnly() throws Exception {
        File jar = createZip("compatible.jar", REQUIRED_ENTRY);

        assertEquals(DynamicCode.JarInspection.COMPATIBLE,
                DynamicCode.inspectJar(jar, REQUIRED_ENTRY, BLOCKED_ENTRY));
    }

    @Test
    public void inspectJarSkipsBlockedEntryCheckWhenBlockedEntryIsNull() throws Exception {
        File jar = createZip("legacy-compatible.jar", REQUIRED_ENTRY, BLOCKED_ENTRY);

        assertEquals(DynamicCode.JarInspection.COMPATIBLE,
                DynamicCode.inspectJar(jar, REQUIRED_ENTRY, null));
    }

    @Test
    public void restoreReadOnlyTargetRestoresMissingTargetFromBackup() throws Exception {
        File target = new File(temporaryFolder.getRoot(), "dynamic.jar");
        File backup = new File(target.getParentFile(), target.getName() + ".bak");
        writeJar(backup, REQUIRED_ENTRY);

        assertTrue(DynamicCode.restoreReadOnlyTarget(target));

        assertTrue(target.exists());
        assertFalse(backup.exists());
        assertFalse(target.canWrite());
        assertEquals(DynamicCode.JarInspection.COMPATIBLE,
                DynamicCode.inspectJar(target, REQUIRED_ENTRY, null));
    }

    @Test
    public void restoreReadOnlyTargetAllowsDownloadAfterDeletingOnlyInvalidBackup() throws Exception {
        File target = new File(temporaryFolder.getRoot(), "dynamic.jar");
        File backup = new File(target.getParentFile(), target.getName() + ".bak");
        writeBytes(backup, "broken".getBytes(StandardCharsets.UTF_8));

        assertTrue(DynamicCode.restoreReadOnlyTarget(target, REQUIRED_ENTRY, BLOCKED_ENTRY));

        assertFalse(target.exists());
        assertFalse(backup.exists());
    }

    @Test
    public void restoreReadOnlyTargetFallsBackFromInvalidCanonicalBackupWithSameTimestamp() throws Exception {
        File target = new File(temporaryFolder.getRoot(), "dynamic.jar");
        File canonical = new File(target.getParentFile(), target.getName() + ".bak");
        writeBytes(canonical, "broken".getBytes(StandardCharsets.UTF_8));
        File unique = new File(target.getParentFile(), target.getName() + ".bak.unique");
        writeJar(unique, REQUIRED_ENTRY);
        long timestamp = 1000L;
        assertTrue(canonical.setLastModified(timestamp));
        assertTrue(unique.setLastModified(timestamp));

        assertTrue(DynamicCode.restoreReadOnlyTarget(target, REQUIRED_ENTRY, BLOCKED_ENTRY));

        assertFalse(canonical.exists());
        assertFalse(unique.exists());
        assertFalse(target.canWrite());
        assertEquals(DynamicCode.JarInspection.COMPATIBLE,
                DynamicCode.inspectJar(target, REQUIRED_ENTRY, BLOCKED_ENTRY));
    }

    @Test
    public void restoreReadOnlyTargetUsesCanonicalBackupFirstWhenCandidatesAreComplete() throws Exception {
        File target = new File(temporaryFolder.getRoot(), "dynamic.jar");
        File canonical = new File(target.getParentFile(), target.getName() + ".bak");
        writeJar(canonical, REQUIRED_ENTRY);
        File unique = new File(target.getParentFile(), target.getName() + ".bak.unique");
        writeJar(unique, REQUIRED_ENTRY, "unique.marker");
        long timestamp = 1000L;
        assertTrue(canonical.setLastModified(timestamp));
        assertTrue(unique.setLastModified(timestamp));

        assertTrue(DynamicCode.restoreReadOnlyTarget(target, REQUIRED_ENTRY, BLOCKED_ENTRY));

        try (ZipFile zip = new ZipFile(target)) {
            assertFalse(zip.getEntry("unique.marker") != null);
        }
    }

    @Test
    public void restoreReadOnlyTargetLeavesExistingTargetUnchangedWhenBackupRemains() throws Exception {
        File target = temporaryFolder.newFile("dynamic.jar");
        byte[] original = "current".getBytes(StandardCharsets.UTF_8);
        writeBytes(target, original);
        File backup = new File(target.getParentFile(), target.getName() + ".bak");
        assertTrue(backup.mkdir());
        File backupChild = new File(backup, "keep");
        writeBytes(backupChild, "keep".getBytes(StandardCharsets.UTF_8));

        try {
            assertTrue(DynamicCode.restoreReadOnlyTarget(target, REQUIRED_ENTRY, BLOCKED_ENTRY));

            assertTrue(backup.exists());
            assertArrayEquals(original, Files.readAllBytes(target.toPath()));
        } finally {
            assertTrue(backupChild.delete());
            assertTrue(backup.delete());
        }
    }

    @Test
    public void publishReadOnlyReplacesExistingTargetAndRemovesTemp() throws Exception {
        File target = temporaryFolder.newFile("dynamic.jar");
        writeBytes(target, "old".getBytes(StandardCharsets.UTF_8));
        assertTrue(target.setReadOnly());
        File temp = DynamicCode.createTempFile(target);
        byte[] replacement = "replacement".getBytes(StandardCharsets.UTF_8);
        writeBytes(temp, replacement);

        DynamicCode.publishReadOnly(temp, target);

        assertFalse(temp.exists());
        assertTrue(target.exists());
        assertFalse(target.canWrite());
        assertFalse(new File(target.getParentFile(), target.getName() + ".bak").exists());
        assertArrayEquals(replacement, Files.readAllBytes(target.toPath()));
    }

    @Test
    public void createTempFileUsesUniqueFilesWithoutDeletingEarlierTemp() throws Exception {
        File target = new File(temporaryFolder.getRoot(), "dynamic.jar");

        File first = DynamicCode.createTempFile(target);
        File second = DynamicCode.createTempFile(target);

        assertFalse(first.equals(second));
        assertTrue(first.exists());
        assertTrue(second.exists());
        DynamicCode.deleteQuietly(first);
        DynamicCode.deleteQuietly(second);
    }

    @Test(expected = IOException.class)
    public void publishReadOnlyRejectsEmptyTemp() throws Exception {
        File target = new File(temporaryFolder.getRoot(), "dynamic.jar");
        File temp = DynamicCode.createTempFile(target);

        DynamicCode.publishReadOnly(temp, target);
    }

    @Test(expected = IOException.class)
    public void publishReadOnlyRejectsTempFromDifferentDirectory() throws Exception {
        File target = temporaryFolder.newFile("dynamic.jar");
        writeBytes(target, "old".getBytes(StandardCharsets.UTF_8));
        File otherDirectory = temporaryFolder.newFolder("other");
        File temp = DynamicCode.createTempFile(new File(otherDirectory, "dynamic.jar"));
        writeBytes(temp, "replacement".getBytes(StandardCharsets.UTF_8));

        DynamicCode.publishReadOnly(temp, target);
    }

    @Test
    public void publishReadOnlyCleansStaleBackupBeforePublishing() throws Exception {
        File target = temporaryFolder.newFile("dynamic.jar");
        writeBytes(target, "old".getBytes(StandardCharsets.UTF_8));
        File backup = new File(target.getParentFile(), target.getName() + ".bak");
        writeBytes(backup, "stale".getBytes(StandardCharsets.UTF_8));
        File temp = DynamicCode.createTempFile(target);
        writeBytes(temp, "replacement".getBytes(StandardCharsets.UTF_8));

        DynamicCode.publishReadOnly(temp, target);

        assertFalse(backup.exists());
        assertArrayEquals("replacement".getBytes(StandardCharsets.UTF_8),
                Files.readAllBytes(target.toPath()));
    }

    @Test
    public void publishReadOnlyKeepsExistingTargetWhenNewTempIsInvalid() throws Exception {
        File target = temporaryFolder.newFile("dynamic.jar");
        byte[] original = "old".getBytes(StandardCharsets.UTF_8);
        writeBytes(target, original);
        File temp = DynamicCode.createTempFile(target);

        try {
            DynamicCode.publishReadOnly(temp, target);
        } catch (IOException expected) {
            assertTrue(target.exists());
            assertArrayEquals(original, Files.readAllBytes(target.toPath()));
            return;
        }
        throw new AssertionError("publishReadOnly should reject an empty temporary file");
    }

    @Test
    public void publishReadOnlyKeepsPublishingWhenStaleBackupCannotBeDeleted() throws Exception {
        File target = temporaryFolder.newFile("dynamic.jar");
        writeBytes(target, "old".getBytes(StandardCharsets.UTF_8));
        File backup = new File(target.getParentFile(), target.getName() + ".bak");
        assertTrue(backup.mkdir());
        File backupChild = new File(backup, "keep");
        writeBytes(backupChild, "keep".getBytes(StandardCharsets.UTF_8));
        File temp = DynamicCode.createTempFile(target);
        byte[] replacement = "replacement".getBytes(StandardCharsets.UTF_8);
        writeBytes(temp, replacement);

        try {
            DynamicCode.publishReadOnly(temp, target);

            assertTrue(target.exists());
            assertFalse(target.canWrite());
            assertArrayEquals(replacement, Files.readAllBytes(target.toPath()));
        } finally {
            assertTrue(backupChild.delete());
            assertTrue(backup.delete());
            DynamicCode.deleteQuietly(temp);
        }
    }

    private static void writeJar(File jar, String... entries) throws IOException {
        try (ZipOutputStream output = new ZipOutputStream(new FileOutputStream(jar))) {
            for (String entry : entries) {
                output.putNextEntry(new ZipEntry(entry));
                output.write(entry.getBytes(StandardCharsets.UTF_8));
                output.closeEntry();
            }
        }
    }

    private File createZip(String name, String... entries) throws IOException {
        File jar = temporaryFolder.newFile(name);
        try (ZipOutputStream output = new ZipOutputStream(new FileOutputStream(jar))) {
            for (String entry : entries) {
                output.putNextEntry(new ZipEntry(entry));
                output.write(entry.getBytes(StandardCharsets.UTF_8));
                output.closeEntry();
            }
        }
        return jar;
    }

    private static void writeBytes(File file, byte[] content) throws IOException {
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(content);
        }
    }
}
