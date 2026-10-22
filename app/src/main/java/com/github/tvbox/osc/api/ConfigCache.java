package com.github.tvbox.osc.api;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** 订阅配置缓存；沿用旧版按订阅地址散列命名的文件，不改变历史数据位置。 */
final class ConfigCache {
    private final File file;
    private final File backup;

    ConfigCache(File file) {
        this.file = file;
        this.backup = new File(file.getParentFile(), file.getName() + ".bak");
    }

    String read() throws IOException {
        synchronized (ConfigCache.class) {
            restoreBackup();
            String json = readFile(file);
            parse(json);
            return json;
        }
    }

    boolean write(String json) throws IOException {
        // 后台更新不能调用 ApiConfig.parseJson：它会修改当前站点、直播和播放器状态。
        parse(json);
        synchronized (ConfigCache.class) {
            File parent = file.getParentFile();
            if (!parent.isDirectory() && !parent.mkdirs()) {
                throw new IOException("无法创建订阅缓存目录");
            }
            restoreBackup();
            if (file.isFile() && json.equals(readFile(file))) {
                return false;
            }
            File temp = File.createTempFile(file.getName() + ".download.", ".tmp", parent);
            try {
                try (FileOutputStream output = new FileOutputStream(temp)) {
                    output.write(json.getBytes(StandardCharsets.UTF_8));
                    output.getFD().sync();
                }
                if (backup.exists() && !backup.delete()) {
                    throw new IOException("无法清理旧的订阅缓存备份");
                }
                if (file.exists() && !file.renameTo(backup)) {
                    throw new IOException("无法备份订阅缓存");
                }
                if (!temp.renameTo(file)) {
                    restoreBackup();
                    throw new IOException("无法发布订阅缓存");
                }
                if (backup.exists()) {
                    backup.delete();
                }
                return true;
            } finally {
                temp.delete();
            }
        }
    }

    private void restoreBackup() throws IOException {
        if (!file.exists() && backup.exists() && !backup.renameTo(file)) {
            throw new IOException("无法恢复订阅缓存");
        }
    }

    private static String readFile(File file) throws IOException {
        try (FileInputStream input = new FileInputStream(file);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    /** 与运行时解析共用结构校验，避免 HTML、截断响应或错误字段覆盖可用缓存。 */
    static JsonObject parse(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        for (JsonElement element : root.getAsJsonArray("sites")) {
            JsonObject site = element.getAsJsonObject();
            site.get("key").getAsString();
            site.get("name").getAsString();
            site.get("type").getAsInt();
            site.get("api").getAsString();
        }
        if (root.has("parses")) {
            for (JsonElement element : root.getAsJsonArray("parses")) {
                JsonObject parser = element.getAsJsonObject();
                parser.get("name").getAsString();
                parser.get("url").getAsString();
                if (parser.has("ext")) {
                    parser.getAsJsonObject("ext");
                }
            }
        }
        if (root.has("rules")) {
            for (JsonElement element : root.getAsJsonArray("rules")) {
                JsonObject rule = element.getAsJsonObject();
                if (rule.has("host")) {
                    rule.get("host").getAsString();
                    validateStrings(rule, "rule");
                    validateStrings(rule, "filter");
                }
                if (rule.has("hosts") && rule.has("regex")) {
                    validateStrings(rule, "hosts");
                    validateStrings(rule, "regex");
                }
            }
        }
        validateStrings(root, "ads");
        if (root.has("ijk")) {
            for (JsonElement element : root.getAsJsonArray("ijk")) {
                JsonObject codec = element.getAsJsonObject();
                codec.get("group").getAsString();
                for (JsonElement option : codec.getAsJsonArray("options")) {
                    JsonObject value = option.getAsJsonObject();
                    value.get("category").getAsString();
                    value.get("name").getAsString();
                    value.get("value").getAsString();
                }
            }
        }
        return root;
    }

    private static void validateStrings(JsonObject object, String key) {
        if (object.has(key)) {
            for (JsonElement element : object.getAsJsonArray(key)) {
                element.getAsString();
            }
        }
    }
}
