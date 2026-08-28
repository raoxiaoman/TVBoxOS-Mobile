package com.github.catvod.utils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public final class DynamicCode {

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
}
