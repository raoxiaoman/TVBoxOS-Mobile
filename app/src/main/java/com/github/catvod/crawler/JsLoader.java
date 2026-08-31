package com.github.catvod.crawler;

import android.os.Build;

import com.github.catvod.utils.DynamicCode;
import com.github.catvod.utils.FileLoadCoordinator;
import com.github.catvod.utils.GenerationPublisher;
import com.github.catvod.utils.JarLoadCoordinator;
import com.github.tvbox.osc.base.App;
import com.github.tvbox.osc.util.LOG;
import com.github.tvbox.osc.util.MD5;
import com.github.tvbox.osc.util.js.JsSpider;
import com.lzy.okgo.OkGo;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import dalvik.system.DexClassLoader;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class JsLoader {
    private final ConcurrentHashMap<String, Spider> spiders = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Class<?>> classs = new ConcurrentHashMap<>();
    private final FileLoadCoordinator fileLoads = new FileLoadCoordinator();
    private final JarLoadCoordinator<Class<?>> jarLoads = new JarLoadCoordinator<>();
    private final GenerationPublisher generation = new GenerationPublisher();
    private volatile String recentJarKey = "";

    public void stopAll() {
        for (Spider spider : spiders.values()) {
            spider.cancelByTag();
        }
    }

    public void clear() {
        synchronized (this) {
            generation.advance();
            for (Spider spider : spiders.values()) {
                spider.cancelByTag();
                spider.destroy();
            }
            spiders.clear();
            classs.clear();
            jarLoads.clear();
            recentJarKey = "";
        }
    }

    public boolean isRuntimeReady(String jar) {
        if (jar == null || jar.isEmpty()) {
            return true;
        }
        String jarUrl = jar.split(";md5;", 2)[0];
        return classs.containsKey(MD5.string2MD5(jarUrl));
    }

    private String blockedEntry() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                ? "assets/ftyshinidie.guard" : null;
    }

    private DynamicCode.JarInspection inspectJar(File jar) {
        return DynamicCode.inspectJar(jar, "classes.dex", blockedEntry());
    }

    private boolean hasExpectedJar(File jar, String md5) {
        return inspectJar(jar) == DynamicCode.JarInspection.COMPATIBLE
                && (md5.isEmpty() || MD5.getFileMd5(jar).equalsIgnoreCase(md5))
                && DynamicCode.ensureReadOnly(jar);
    }

    private Class<?> createClassLoader(File jar, String key, long loadGeneration) {
        if (!generation.isCurrent(loadGeneration) || !jar.exists() || !DynamicCode.ensureReadOnly(jar)
                || inspectJar(jar) != DynamicCode.JarInspection.COMPATIBLE) {
            return null;
        }
        try {
            File cacheDir = new File(App.getInstance().getCacheDir(), "catvod_jsapi");
            if (!cacheDir.exists() && !cacheDir.mkdirs() && !cacheDir.isDirectory()) {
                return null;
            }
            DexClassLoader classLoader = new DexClassLoader(jar.getAbsolutePath(),
                    cacheDir.getAbsolutePath(), null, App.getInstance().getClassLoader());
            return cacheClass(key, classLoader.loadClass("com.github.catvod.js.Method"), loadGeneration);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private Class<?> loadClassLoader(String jar, String md5, String key, long loadGeneration) {
        Class<?> loaded = classs.get(key);
        if (loaded != null) {
            return generation.isCurrent(loadGeneration) ? loaded : null;
        }
        try {
            JarLoadCoordinator.Result<Class<?>> result = jarLoads.getOrLoad(key,
                    () -> loadJarOnce(jar, md5, key, loadGeneration));
            return result.isSuccess() && generation.isCurrent(loadGeneration) ? result.getValue() : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private JarLoadCoordinator.Result<Class<?>> loadJarOnce(String jar, String md5, String key,
                                                             long loadGeneration) {
        File cache = new File(App.getInstance().getFilesDir(), key + ".jar");
        try {
            FileLoadCoordinator.Result result = fileLoads.getOrLoad(cache,
                    () -> prepareJar(jar, md5, cache, loadGeneration));
            if (!result.isSuccess() || !generation.isCurrent(loadGeneration)
                    || !hasExpectedJar(cache, md5)) {
                return result.isIncompatible() ? JarLoadCoordinator.Result.incompatible()
                        : JarLoadCoordinator.Result.retryableFailure();
            }
            Class<?> classInit = createClassLoader(cache, key, loadGeneration);
            return classInit == null ? JarLoadCoordinator.Result.retryableFailure()
                    : JarLoadCoordinator.Result.success(classInit);
        } catch (Throwable ignored) {
            return JarLoadCoordinator.Result.retryableFailure();
        }
    }

    private FileLoadCoordinator.Result prepareJar(String jar, String md5, File cache, long loadGeneration) {
        try {
            if (!DynamicCode.restoreReadOnlyTarget(cache, "classes.dex", blockedEntry())) {
                return FileLoadCoordinator.Result.retryableFailure();
            }
            if (cache.exists()) {
                DynamicCode.JarInspection inspection = inspectJar(cache);
                if (inspection == DynamicCode.JarInspection.INCOMPATIBLE) {
                    return FileLoadCoordinator.Result.incompatible();
                }
                if (hasExpectedJar(cache, md5)) {
                    return FileLoadCoordinator.Result.success();
                }
            }
            File parent = cache.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs() && !parent.isDirectory()) {
                return FileLoadCoordinator.Result.retryableFailure();
            }
            File temp = DynamicCode.createTempFile(cache);
            boolean published = false;
            try {
                downloadTo(jar, temp);
                DynamicCode.JarInspection inspection = inspectJar(temp);
                if (inspection == DynamicCode.JarInspection.INCOMPATIBLE) {
                    return FileLoadCoordinator.Result.incompatible();
                }
                if (inspection != DynamicCode.JarInspection.COMPATIBLE
                        || (!md5.isEmpty() && !MD5.getFileMd5(temp).equalsIgnoreCase(md5))) {
                    return FileLoadCoordinator.Result.retryableFailure();
                }
                if (!generation.publishIfCurrent(loadGeneration,
                        () -> DynamicCode.publishReadOnly(temp, cache))) {
                    return FileLoadCoordinator.Result.retryableFailure();
                }
                published = true;
            } finally {
                if (!published) {
                    DynamicCode.deleteQuietly(temp);
                }
            }
            return FileLoadCoordinator.Result.success();
        } catch (Throwable ignored) {
            return FileLoadCoordinator.Result.retryableFailure();
        }
    }

    private Class<?> cacheClass(String key, Class<?> classInit, long loadGeneration) {
        synchronized (this) {
            if (!generation.isCurrent(loadGeneration)) {
                return null;
            }
            classs.put(key, classInit);
            return classInit;
        }
    }

    private void downloadTo(String jar, File target) throws Exception {
        Response response = null;
        InputStream input = null;
        OutputStream output = null;
        try {
            response = OkGo.<File>get(jar).execute();
            if (!response.isSuccessful()) {
                throw new IllegalStateException("下载动态代码失败");
            }
            ResponseBody body = response.body();
            if (body == null) {
                throw new IllegalStateException("动态代码响应为空");
            }
            input = body.byteStream();
            output = new FileOutputStream(target);
            byte[] buffer = new byte[8192];
            int length;
            while ((length = input.read(buffer)) != -1) {
                if (length > 0) {
                    output.write(buffer, 0, length);
                }
            }
            output.flush();
        } finally {
            if (input != null) try {
                input.close();
            } catch (Exception ignored) {
            }
            if (output != null) try {
                output.close();
            } catch (Exception ignored) {
            }
            if (response != null) {
                response.close();
            }
        }
    }

    public Spider getSpider(String key, String api, String ext, String jar) {
        long loadGeneration = generation.current();
        String jarValue = jar == null ? "" : jar;
        String jarUrl = "";
        String jarMd5 = "";
        String jarKey = "";
        if (!jarValue.isEmpty()) {
            String[] urls = jarValue.split(";md5;", 2);
            jarUrl = urls[0];
            jarKey = MD5.string2MD5(jarUrl);
            jarMd5 = urls.length > 1 ? urls[1].trim() : "";
        }
        Spider cached = cachedSpider(key, loadGeneration);
        if (cached != null) {
            return cached;
        }
        Class<?> classLoader = jarValue.isEmpty() ? null
                : loadClassLoader(jarUrl, jarMd5, jarKey, loadGeneration);
        if (!jarValue.isEmpty() && classLoader == null) {
            return new SpiderNull();
        }
        try {
            Spider sp = new JsSpider(key, api, classLoader);
            sp.init(App.getInstance(), ext);
            Spider current = cacheSpider(key, sp, loadGeneration);
            if (current != null) {
                return current;
            }
            sp.destroy();
        } catch (Throwable th) {
            LOG.e("QuJS", th);
        }
        return new SpiderNull();
    }

    private Spider cachedSpider(String key, long loadGeneration) {
        synchronized (this) {
            if (!generation.isCurrent(loadGeneration)) {
                return null;
            }
            recentJarKey = key;
            return spiders.get(key);
        }
    }

    private Spider cacheSpider(String key, Spider spider, long loadGeneration) {
        synchronized (this) {
            if (!generation.isCurrent(loadGeneration)) {
                return null;
            }
            recentJarKey = key;
            Spider cached = spiders.putIfAbsent(key, spider);
            return cached == null ? spider : cached;
        }
    }

    public Object[] proxyInvoke(Map<String, String> params) {
        try {
            Spider proxyFun = spiders.get(recentJarKey);
            if (proxyFun != null) {
                return proxyFun.proxyLocal(params);
            }
        } catch (Throwable th) {
            LOG.e("proxyInvoke", th);
        }
        return null;
    }
}
