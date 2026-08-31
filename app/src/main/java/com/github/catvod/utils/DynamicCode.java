package com.github.catvod.utils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.zip.ZipFile;

public final class DynamicCode {

    public enum JarInspection {
        COMPATIBLE,
        INCOMPATIBLE,
        INVALID
    }

    private DynamicCode() {
    }

    public static FileOutputStream openReadOnlyOutput(File file) throws IOException {
        if (file.exists() && !file.delete()) {
            throw new IOException("无法删除旧的动态代码文件");
        }
        FileOutputStream output = new FileOutputStream(file);
        if (!file.setReadOnly()) {
            output.close();
            file.delete();
            throw new IOException("无法将动态代码文件设为只读");
        }
        return output;
    }

    public static boolean ensureReadOnly(File file) {
        return !file.canWrite() || file.setReadOnly();
    }

    public static File createTempFile(File target) throws IOException {
        File absoluteTarget = target.getAbsoluteFile();
        File parent = absoluteTarget.getParentFile();
        if (parent == null || !parent.isDirectory()) {
            throw new IOException("动态代码目标目录不可用");
        }
        return File.createTempFile(absoluteTarget.getName() + ".", ".tmp", parent);
    }

    public static JarInspection inspectJar(File jar, String requiredEntry, String blockedEntry) {
        if (jar == null || requiredEntry == null) {
            return JarInspection.INVALID;
        }
        try (ZipFile zipFile = new ZipFile(jar)) {
            if (zipFile.getEntry(requiredEntry) == null) {
                return JarInspection.INVALID;
            }
            return blockedEntry == null || zipFile.getEntry(blockedEntry) == null
                    ? JarInspection.COMPATIBLE : JarInspection.INCOMPATIBLE;
        } catch (IOException e) {
            return JarInspection.INVALID;
        }
    }

    public static boolean restoreReadOnlyTarget(File target) {
        return restoreReadOnlyTarget(target, "classes.dex", null);
    }

    /**
     * Returns false only when a compatible backup exists but cannot be restored as read-only.
     * A missing target with no recoverable backup returns true so callers can download it again.
     */
    public static boolean restoreReadOnlyTarget(File target, String requiredEntry, String blockedEntry) {
        File absoluteTarget = target.getAbsoluteFile();
        File parent = absoluteTarget.getParentFile();
        if (parent == null || !parent.isDirectory()) {
            return false;
        }
        File[] backups = findBackups(absoluteTarget);
        if (absoluteTarget.exists()) {
            for (File backup : backups) {
                deleteQuietly(backup);
            }
            return true;
        }
        boolean hasRecoverableBackup = false;
        for (File backup : backups) {
            if (inspectJar(backup, requiredEntry, blockedEntry) != JarInspection.COMPATIBLE) {
                deleteQuietly(backup);
                continue;
            }
            hasRecoverableBackup = true;
            if (!backup.renameTo(absoluteTarget)) {
                continue;
            }
            if (ensureReadOnly(absoluteTarget)) {
                return true;
            }
            if (!absoluteTarget.renameTo(backup)) {
                return false;
            }
        }
        return !hasRecoverableBackup;
    }

    public static void publishReadOnly(File temp, File target) throws IOException {
        File absoluteTemp = temp.getAbsoluteFile();
        File absoluteTarget = target.getAbsoluteFile();
        if (!absoluteTemp.exists() || absoluteTemp.length() == 0L) {
            throw new IOException("动态代码临时文件不存在或为空");
        }
        File parent = absoluteTarget.getParentFile();
        if (parent == null || !parent.isDirectory() || !parent.equals(absoluteTemp.getParentFile())) {
            throw new IOException("动态代码临时文件必须与目标文件位于同一目录");
        }
        if (absoluteTemp.equals(absoluteTarget)) {
            throw new IOException("动态代码临时文件不能是目标文件");
        }
        if (!restoreReadOnlyTarget(absoluteTarget)) {
            throw new IOException("无法恢复动态代码文件");
        }
        if (!absoluteTemp.setReadOnly()) {
            throw new IOException("无法将动态代码临时文件设为只读");
        }

        // API 24 lacks an atomic replace API. Keep the prior target as a same-directory backup
        // so startup recovery can restore it if the rename-based publish is interrupted.
        File backup = nextBackup(absoluteTarget);
        boolean hadTarget = absoluteTarget.exists();
        if (hadTarget && !absoluteTarget.renameTo(backup)) {
            throw new IOException("无法备份旧的动态代码文件");
        }
        if (!absoluteTemp.renameTo(absoluteTarget)) {
            if (hadTarget && !backup.renameTo(absoluteTarget)) {
                throw new IOException("无法发布动态代码文件，且无法恢复旧文件");
            }
            throw new IOException("无法发布动态代码文件");
        }
        deleteQuietly(backup);
    }

    private static File nextBackup(File target) throws IOException {
        File parent = target.getParentFile();
        File backup = new File(parent, target.getName() + ".bak");
        deleteQuietly(backup);
        if (!backup.exists()) {
            return backup;
        }
        File uniqueBackup = File.createTempFile(target.getName() + ".bak.", ".tmp", parent);
        if (!uniqueBackup.delete()) {
            throw new IOException("无法创建动态代码备份文件");
        }
        return uniqueBackup;
    }

    private static File[] findBackups(File target) {
        File parent = target.getParentFile();
        String backupName = target.getName() + ".bak";
        File[] backups = parent.listFiles((dir, name) -> name.equals(backupName)
                || name.startsWith(backupName + "."));
        if (backups == null) {
            return new File[0];
        }
        Arrays.sort(backups, new Comparator<File>() {
            @Override
            public int compare(File first, File second) {
                boolean firstCanonical = first.getName().equals(backupName);
                boolean secondCanonical = second.getName().equals(backupName);
                if (firstCanonical != secondCanonical) {
                    return firstCanonical ? -1 : 1;
                }
                return first.getName().compareTo(second.getName());
            }
        });
        return backups;
    }

    public static void deleteQuietly(File file) {
        if (file != null && file.exists()) {
            file.delete();
        }
    }
}
