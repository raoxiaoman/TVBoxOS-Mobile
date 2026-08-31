package com.github.catvod.crawler;

import android.content.Context;
import android.os.Build;

import com.github.catvod.utils.DynamicCode;
import com.github.catvod.utils.FileLoadCoordinator;
import com.github.catvod.utils.GenerationPublisher;
import com.github.catvod.utils.JarLoadCoordinator;
import com.github.tvbox.osc.base.App;
import com.github.tvbox.osc.util.MD5;
import com.lzy.okgo.OkGo;

import org.json.JSONObject;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import dalvik.system.DexClassLoader;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class JarLoader {
    private final ConcurrentHashMap<String, DexClassLoader> classLoaders = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Method> proxyMethods = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Spider> spiders = new ConcurrentHashMap<>();
    private final FileLoadCoordinator fileLoads = new FileLoadCoordinator();
    private final JarLoadCoordinator<DexClassLoader> jarLoads = new JarLoadCoordinator<>();
    private final GenerationPublisher generation = new GenerationPublisher();
    private volatile String recentJarKey = "";

    /**
     * 不要在主线程调用我
     *
     * @param cache
     */
    public boolean load(String cache) {
        clear();
        long loadGeneration = generation.current();
        File jar = new File(cache);
        if (!DynamicCode.restoreReadOnlyTarget(jar, "classes.dex", blockedEntry())) {
            return false;
        }
        return createClassLoader(jar, "main", loadGeneration) != null;
    }

    public void clear() {
        synchronized (this) {
            generation.advance();
            spiders.clear();
            recentJarKey = "";
            proxyMethods.clear();
            classLoaders.clear();
            jarLoads.clear();
        }
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

    private DexClassLoader createClassLoader(File jar, String key, long loadGeneration) {
        if (!generation.isCurrent(loadGeneration) || !jar.exists() || !DynamicCode.ensureReadOnly(jar)
                || inspectJar(jar) != DynamicCode.JarInspection.COMPATIBLE) {
            return null;
        }
        try {
            File cacheDir = new File(App.getInstance().getCacheDir(), "catvod_csp");
            if (!cacheDir.exists() && !cacheDir.mkdirs() && !cacheDir.isDirectory()) {
                return null;
            }
            DexClassLoader classLoader = new DexClassLoader(jar.getAbsolutePath(),
                    cacheDir.getAbsolutePath(), null, App.getInstance().getClassLoader());
            Class<?> classInit = classLoader.loadClass("com.github.catvod.spider.Init");
            Method init = classInit.getMethod("init", Context.class);
            init.invoke(null, App.getInstance());
            Method proxyMethod = null;
            try {
                Class<?> proxy = classLoader.loadClass("com.github.catvod.spider.Proxy");
                proxyMethod = proxy.getMethod("proxy", Map.class);
            } catch (Throwable ignored) {
                // Proxy is optional for a spider jar.
            }
            return cacheClassLoader(key, classLoader, proxyMethod, loadGeneration);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private DexClassLoader cacheClassLoader(String key, DexClassLoader classLoader, Method proxyMethod,
                                             long loadGeneration) {
        synchronized (this) {
            if (!generation.isCurrent(loadGeneration)) {
                return null;
            }
            if (proxyMethod != null) {
                proxyMethods.put(key, proxyMethod);
            }
            classLoaders.put(key, classLoader);
            return classLoader;
        }
    }

    public boolean isRuntimeReady(String jar) {
        if (jar == null || jar.isEmpty()) {
            return classLoaders.containsKey("main");
        }
        String jarUrl = jar.split(";md5;", 2)[0];
        return classLoaders.containsKey(MD5.string2MD5(jarUrl));
    }

    private DexClassLoader loadJarInternal(String jar, String md5, String key, long loadGeneration) {
        DexClassLoader loaded = classLoaders.get(key);
        if (loaded != null) {
            return generation.isCurrent(loadGeneration) ? loaded : null;
        }
        try {
            JarLoadCoordinator.Result<DexClassLoader> result = jarLoads.getOrLoad(key,
                    () -> loadJarOnce(jar, md5, key, loadGeneration));
            return result.isSuccess() && generation.isCurrent(loadGeneration) ? result.getValue() : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private JarLoadCoordinator.Result<DexClassLoader> loadJarOnce(String jar, String md5, String key,
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
            DexClassLoader classLoader = createClassLoader(cache, key, loadGeneration);
            return classLoader == null ? JarLoadCoordinator.Result.retryableFailure()
                    : JarLoadCoordinator.Result.success(classLoader);
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
            output = new java.io.FileOutputStream(target);
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

    public Spider getSpider(String key, String cls, String ext, String jar) {
        long loadGeneration = generation.current();
        String clsKey = cls.replace("csp_", "");
        String jarUrl = "";
        String jarMd5 = "";
        String jarKey;
        if (jar.isEmpty()) {
            jarKey = "main";
        } else {
            String[] urls = jar.split(";md5;", 2);
            jarUrl = urls[0];
            jarKey = MD5.string2MD5(jarUrl);
            jarMd5 = urls.length > 1 ? urls[1].trim() : "";
        }
        Spider cached = cachedSpider(key, jarKey, loadGeneration);
        if (cached != null) {
            return cached;
        }
        DexClassLoader classLoader = jarKey.equals("main") ? classLoaders.get("main")
                : loadJarInternal(jarUrl, jarMd5, jarKey, loadGeneration);
        if (classLoader == null || !generation.isCurrent(loadGeneration)) {
            return new SpiderNull();
        }
        try {
            Spider spider = (Spider) classLoader.loadClass("com.github.catvod.spider." + clsKey).newInstance();
            spider.init(App.getInstance(), ext);
            Spider current = cacheSpider(key, jarKey, spider, loadGeneration);
            if (current != null) {
                return current;
            }
        } catch (Throwable th) {
            th.printStackTrace();
        }
        return new SpiderNull();
    }

    private Spider cachedSpider(String key, String jarKey, long loadGeneration) {
        synchronized (this) {
            if (!generation.isCurrent(loadGeneration)) {
                return null;
            }
            recentJarKey = jarKey;
            return spiders.get(key);
        }
    }

    private Spider cacheSpider(String key, String jarKey, Spider spider, long loadGeneration) {
        synchronized (this) {
            if (!generation.isCurrent(loadGeneration)) {
                return null;
            }
            recentJarKey = jarKey;
            Spider cached = spiders.putIfAbsent(key, spider);
            return cached == null ? spider : cached;
        }
    }

    public JSONObject jsonExt(String key, LinkedHashMap<String, String> jxs, String url) {
        try {
            DexClassLoader classLoader = classLoaders.get("main");
            String clsKey = "Json" + key;
            String hotClass = "com.github.catvod.parser." + clsKey;
            Class jsonParserCls = classLoader.loadClass(hotClass);
            Method mth = jsonParserCls.getMethod("parse", LinkedHashMap.class, String.class);
            return (JSONObject) mth.invoke(null, jxs, url);
        } catch (Throwable th) {
            th.printStackTrace();
        }
        return null;
    }

    public JSONObject jsonExtMix(String flag, String key, String name, LinkedHashMap<String, HashMap<String, String>> jxs, String url) {
        try {
            DexClassLoader classLoader = classLoaders.get("main");
            String clsKey = "Mix" + key;
            String hotClass = "com.github.catvod.parser." + clsKey;
            Class jsonParserCls = classLoader.loadClass(hotClass);
            Method mth = jsonParserCls.getMethod("parse", LinkedHashMap.class, String.class, String.class, String.class);
            return (JSONObject) mth.invoke(null, jxs, name, flag, url);
        } catch (Throwable th) {
            th.printStackTrace();
        }
        return null;
    }

    public Object[] proxyInvoke(Map params) {
        try {
            Method proxyFun = proxyMethods.get(recentJarKey);
            if (proxyFun != null) {
                return (Object[]) proxyFun.invoke(null, params);
            }
        } catch (Throwable th) {

        }
        return null;
    }
}
