# 聚合搜索并发修复与性能优化实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复共享 Spider JAR 并发读写导致的结果缺失和 JNI 崩溃，并通过订阅级运行时复用、任务优先级和批次隔离缩短聚合搜索首批结果时间。

**Architecture:** 使用纯 Java `JarLoadCoordinator` 按 `jarKey` 合并并发加载，使用 `DynamicCode` 将下载内容写入临时文件，完成 ZIP、MD5 和保护壳检查后只读原子发布。搜索层为每次查询创建 `SearchBatch`，让 EventBus 结果携带批次与来源，优先调度首页源、HTTP 源和已就绪 Spider 源，同时丢弃旧批次迟到结果。

**Tech Stack:** Android Java/Kotlin、Java 8 并发工具、OkGo/OkHttp、DexClassLoader、EventBus、JUnit 4、Gradle 8.7、AGP 8.5.2、JDK 17。

---

## 文件结构

### 新增文件

- `app/src/main/java/com/github/catvod/utils/JarLoadCoordinator.java`
  - 按 `jarKey` 合并并发加载。
  - 缓存成功和明确不兼容结果。
  - 网络等可重试失败完成后移除任务。
- `app/src/main/java/com/github/tvbox/osc/event/SearchResultEvent.java`
  - 携带 `batchId`、`sourceKey` 和可空 `AbsXml`。
- `app/src/main/java/com/github/tvbox/osc/search/SearchBatch.java`
  - 记录当前搜索批次、预期来源和已完成来源。
  - 保证每个来源只完成一次。
- `app/src/test/java/com/github/catvod/utils/JarLoadCoordinatorTest.java`
  - 固定同键单飞、异键并行、失败释放和不兼容缓存行为。
- `app/src/test/java/com/github/catvod/utils/DynamicCodeTest.java`
  - 固定临时文件、ZIP 完整性、保护资源识别和正式缓存发布行为。
- `app/src/test/java/com/github/tvbox/osc/search/SearchBatchTest.java`
  - 固定旧批次隔离和来源只完成一次。

### 修改文件

- `app/build.gradle`
  - 增加 JUnit 4 单元测试依赖。
- `app/src/main/java/com/github/catvod/utils/DynamicCode.java`
  - 增加临时文件、ZIP 检查、只读发布和清理能力。
- `app/src/main/java/com/github/catvod/crawler/JarLoader.java`
  - 使用单飞协调器、订阅级 ClassLoader 复用和 fail-closed 检查。
- `app/src/main/java/com/github/catvod/crawler/JsLoader.java`
  - 使用相同的单飞和原子缓存流程。
- `app/src/main/java/com/github/tvbox/osc/api/ApiConfig.java`
  - 暴露源运行时是否就绪，并在订阅重新加载时清理订阅级状态。
- `app/src/main/java/com/github/tvbox/osc/viewmodel/SourceViewModel.java`
  - 搜索结果携带批次和来源；所有路径恰好发送一个完成事件。
- `app/src/main/java/com/github/tvbox/osc/ui/activity/FastSearchActivity.kt`
  - 建立搜索批次、优先排序任务、渐进展示结果并忽略迟到事件。

## 提交约束

本计划中的提交步骤只在用户明确授权提交后执行。提交前必须：

1. 运行 `~/.claude/bin/safe-git-submit --check`。
2. 明确列出并暂存本任务涉及的文件，禁止全量 `git add .`。
3. 使用中文 Conventional Commits 信息。
4. 不添加 `Co-Authored-By` 或 AI 身份标记。

---

### Task 1：建立最小 JVM 测试入口

**Files:**
- Modify: `app/build.gradle:105-182`
- Create: `app/src/test/java/com/github/catvod/utils/JarLoadCoordinatorTest.java`

- [ ] **Step 1：增加 JUnit 4 依赖**

在 `app/build.gradle` 的 `dependencies` 末尾加入：

```gradle
    testImplementation 'junit:junit:4.13.2'
```

- [ ] **Step 2：创建单飞并发测试，先引用尚不存在的生产类**

创建 `JarLoadCoordinatorTest.java`：

```java
package com.github.catvod.utils;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class JarLoadCoordinatorTest {

    @Test
    public void sameKeyLoadsOnlyOnce() throws Exception {
        JarLoadCoordinator<String> coordinator = new JarLoadCoordinator<>();
        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        Callable<JarLoadCoordinator.Result<String>> loader = () -> {
            start.await();
            calls.incrementAndGet();
            Thread.sleep(50);
            return JarLoadCoordinator.Result.success("loader");
        };
        List<Future<JarLoadCoordinator.Result<String>>> futures = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            futures.add(executor.submit(() -> coordinator.getOrLoad("shared", loader)));
        }
        start.countDown();
        for (Future<JarLoadCoordinator.Result<String>> future : futures) {
            assertEquals("loader", future.get().getValue());
        }
        executor.shutdownNow();
        assertEquals(1, calls.get());
    }

    @Test
    public void retryableFailureDoesNotStayCached() throws Exception {
        JarLoadCoordinator<String> coordinator = new JarLoadCoordinator<>();
        AtomicInteger calls = new AtomicInteger();
        JarLoadCoordinator.Result<String> first = coordinator.getOrLoad(
                "shared",
                () -> {
                    calls.incrementAndGet();
                    return JarLoadCoordinator.Result.retryableFailure();
                }
        );
        JarLoadCoordinator.Result<String> second = coordinator.getOrLoad(
                "shared",
                () -> {
                    calls.incrementAndGet();
                    return JarLoadCoordinator.Result.success("loader");
                }
        );
        assertTrue(first.isRetryableFailure());
        assertEquals("loader", second.getValue());
        assertEquals(2, calls.get());
    }

    @Test
    public void incompatibleResultStaysCached() throws Exception {
        JarLoadCoordinator<String> coordinator = new JarLoadCoordinator<>();
        AtomicInteger calls = new AtomicInteger();
        JarLoadCoordinator.Result<String> first = coordinator.getOrLoad(
                "blocked",
                () -> {
                    calls.incrementAndGet();
                    return JarLoadCoordinator.Result.incompatible();
                }
        );
        JarLoadCoordinator.Result<String> second = coordinator.getOrLoad(
                "blocked",
                () -> {
                    calls.incrementAndGet();
                    return JarLoadCoordinator.Result.success("unexpected");
                }
        );
        assertTrue(first.isIncompatible());
        assertTrue(second.isIncompatible());
        assertEquals(1, calls.get());
    }

    @Test
    public void clearDropsSubscriptionResults() throws Exception {
        JarLoadCoordinator<String> coordinator = new JarLoadCoordinator<>();
        AtomicInteger calls = new AtomicInteger();
        coordinator.getOrLoad("shared", () -> {
            calls.incrementAndGet();
            return JarLoadCoordinator.Result.success("first");
        });
        coordinator.clear();
        JarLoadCoordinator.Result<String> result = coordinator.getOrLoad("shared", () -> {
            calls.incrementAndGet();
            return JarLoadCoordinator.Result.success("second");
        });
        assertEquals("second", result.getValue());
        assertEquals(2, calls.get());
        assertFalse(result.isRetryableFailure());
    }
}
```

- [ ] **Step 3：运行红灯测试**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests 'com.github.catvod.utils.JarLoadCoordinatorTest' --stacktrace --no-daemon
```

Expected: `compileDebugUnitTestJavaWithJavac` 失败，提示 `JarLoadCoordinator` 不存在。这是首次红灯证据。

---

### Task 2：实现按 jarKey 单飞加载协调器

**Files:**
- Create: `app/src/main/java/com/github/catvod/utils/JarLoadCoordinator.java`
- Test: `app/src/test/java/com/github/catvod/utils/JarLoadCoordinatorTest.java`

- [ ] **Step 1：实现结果类型和共享任务**

创建 `JarLoadCoordinator.java`：

```java
package com.github.catvod.utils;

import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

public final class JarLoadCoordinator<T> {

    private final ConcurrentHashMap<String, FutureTask<Result<T>>> tasks = new ConcurrentHashMap<>();

    public Result<T> getOrLoad(String key, Callable<Result<T>> loader)
            throws InterruptedException, ExecutionException {
        while (true) {
            FutureTask<Result<T>> task = tasks.get(key);
            if (task == null) {
                FutureTask<Result<T>> candidate = new FutureTask<>(loader);
                task = tasks.putIfAbsent(key, candidate);
                if (task == null) {
                    task = candidate;
                    candidate.run();
                }
            }
            Result<T> result;
            try {
                result = task.get();
            } catch (ExecutionException e) {
                tasks.remove(key, task);
                throw e;
            }
            if (result.isRetryableFailure()) {
                tasks.remove(key, task);
            }
            return result;
        }
    }

    public boolean hasTerminalResult(String key) {
        FutureTask<Result<T>> task = tasks.get(key);
        return task != null && task.isDone();
    }

    public void clear() {
        tasks.clear();
    }

    public static final class Result<T> {
        private enum Status {
            SUCCESS,
            INCOMPATIBLE,
            RETRYABLE_FAILURE
        }

        private final Status status;
        private final T value;

        private Result(Status status, T value) {
            this.status = status;
            this.value = value;
        }

        public static <T> Result<T> success(T value) {
            if (value == null) throw new IllegalArgumentException("value == null");
            return new Result<>(Status.SUCCESS, value);
        }

        public static <T> Result<T> incompatible() {
            return new Result<>(Status.INCOMPATIBLE, null);
        }

        public static <T> Result<T> retryableFailure() {
            return new Result<>(Status.RETRYABLE_FAILURE, null);
        }

        public boolean isSuccess() {
            return status == Status.SUCCESS;
        }

        public boolean isIncompatible() {
            return status == Status.INCOMPATIBLE;
        }

        public boolean isRetryableFailure() {
            return status == Status.RETRYABLE_FAILURE;
        }

        public T getValue() {
            return value;
        }
    }
}
```

- [ ] **Step 2：运行单飞测试**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests 'com.github.catvod.utils.JarLoadCoordinatorTest' --stacktrace --no-daemon
```

Expected: 4 tests PASS。

- [ ] **Step 3：若已获提交授权，提交协调器和测试入口**

先运行安全检查并显式暂存：

```bash
~/.claude/bin/safe-git-submit --check
git add app/build.gradle \
  app/src/main/java/com/github/catvod/utils/JarLoadCoordinator.java \
  app/src/test/java/com/github/catvod/utils/JarLoadCoordinatorTest.java
~/.claude/bin/safe-git-submit --commit "test: 添加动态JAR单飞加载回归测试"
```

---

### Task 3：用临时文件验证并发布完整 JAR

**Files:**
- Modify: `app/src/main/java/com/github/catvod/utils/DynamicCode.java`
- Create: `app/src/test/java/com/github/catvod/utils/DynamicCodeTest.java`

- [ ] **Step 1：编写文件发布红灯测试**

创建 `DynamicCodeTest.java`：

```java
package com.github.catvod.utils;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DynamicCodeTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void incompleteZipIsRejected() throws Exception {
        File jar = folder.newFile("broken.jar");
        Files.write(jar.toPath(), new byte[]{0x50, 0x4b, 0x03, 0x04});
        assertEquals(DynamicCode.JarInspection.INVALID,
                DynamicCode.inspectJar(jar, "classes.dex", "assets/ftyshinidie.guard"));
    }

    @Test
    public void guardedZipIsReportedAsIncompatible() throws Exception {
        File jar = createJar("guarded.jar", true);
        assertEquals(DynamicCode.JarInspection.INCOMPATIBLE,
                DynamicCode.inspectJar(jar, "classes.dex", "assets/ftyshinidie.guard"));
    }

    @Test
    public void validZipIsReportedAsCompatible() throws Exception {
        File jar = createJar("valid.jar", false);
        assertEquals(DynamicCode.JarInspection.COMPATIBLE,
                DynamicCode.inspectJar(jar, "classes.dex", "assets/ftyshinidie.guard"));
    }

    @Test
    public void publishReplacesTargetOnlyAfterTempIsComplete() throws Exception {
        File target = folder.newFile("shared.jar");
        Files.write(target.toPath(), "old".getBytes(StandardCharsets.UTF_8));
        File temp = DynamicCode.createTempFile(target);
        try (FileOutputStream output = new FileOutputStream(temp)) {
            output.write("new".getBytes(StandardCharsets.UTF_8));
        }
        assertTrue(DynamicCode.publishReadOnly(temp, target));
        assertEquals("new", new String(Files.readAllBytes(target.toPath()), StandardCharsets.UTF_8));
        assertFalse(temp.exists());
    }

    private File createJar(String name, boolean guarded) throws Exception {
        File jar = folder.newFile(name);
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(jar))) {
            zip.putNextEntry(new ZipEntry("classes.dex"));
            zip.write(new byte[]{1, 2, 3});
            zip.closeEntry();
            if (guarded) {
                zip.putNextEntry(new ZipEntry("assets/ftyshinidie.guard"));
                zip.write(new byte[]{4, 5, 6});
                zip.closeEntry();
            }
        }
        return jar;
    }
}
```

- [ ] **Step 2：运行红灯测试**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests 'com.github.catvod.utils.DynamicCodeTest' --stacktrace --no-daemon
```

Expected: FAIL，提示 `JarInspection`、`inspectJar`、`createTempFile` 和 `publishReadOnly` 不存在。

- [ ] **Step 3：扩展 DynamicCode**

在保留现有兼容方法的基础上加入：

```java
public enum JarInspection {
    COMPATIBLE,
    INCOMPATIBLE,
    INVALID
}

public static File createTempFile(File target) throws IOException {
    File temp = new File(target.getParentFile(), target.getName() + ".tmp");
    if (temp.exists() && !temp.delete()) {
        throw new IOException("无法删除旧的动态代码临时文件");
    }
    return temp;
}

public static JarInspection inspectJar(File file, String requiredEntry, String blockedEntry) {
    try (ZipFile zip = new ZipFile(file)) {
        if (zip.getEntry(requiredEntry) == null) return JarInspection.INVALID;
        if (blockedEntry != null && zip.getEntry(blockedEntry) != null) {
            return JarInspection.INCOMPATIBLE;
        }
        return JarInspection.COMPATIBLE;
    } catch (Throwable ignored) {
        return JarInspection.INVALID;
    }
}

public static boolean publishReadOnly(File temp, File target) {
    if (!temp.isFile() || temp.length() <= 0) return false;
    if (!temp.setReadOnly()) return false;
    return temp.renameTo(target);
}

public static void deleteQuietly(File file) {
    if (file != null && file.exists()) file.delete();
}
```

同时增加 `java.util.zip.ZipFile` import。

`temp` 与 `target` 必须位于同一目录。Android/Linux 的同文件系统 rename 会原子替换目标；若 `renameTo` 返回 `false`，旧目标保持不变并按发布失败处理。`publishReplacesTargetOnlyAfterTempIsComplete` 必须在开发机上验证这一契约。

- [ ] **Step 4：运行 DynamicCode 测试**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests 'com.github.catvod.utils.DynamicCodeTest' --stacktrace --no-daemon
```

Expected: 4 tests PASS。

- [ ] **Step 5：运行已有单飞测试，防止测试入口回归**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests 'com.github.catvod.utils.*Test' --stacktrace --no-daemon
```

Expected: 8 tests PASS。

---

### Task 4：将 JarLoader 改为订阅级单飞和 fail-closed

**Files:**
- Modify: `app/src/main/java/com/github/catvod/crawler/JarLoader.java:26-172`
- Modify: `app/src/main/java/com/github/tvbox/osc/api/ApiConfig.java:70,244-298,739-743`
- Test: `app/src/test/java/com/github/catvod/utils/JarLoadCoordinatorTest.java`
- Test: `app/src/test/java/com/github/catvod/utils/DynamicCodeTest.java`

- [ ] **Step 1：在 JarLoader 中增加协调器和就绪查询**

增加字段：

```java
private final ConcurrentHashMap<String, DexClassLoader> classLoaders = new ConcurrentHashMap<>();
private final JarLoadCoordinator<DexClassLoader> jarLoads = new JarLoadCoordinator<>();
```

增加查询：

```java
public boolean isRuntimeReady(String jar) {
    if (jar == null || jar.isEmpty()) return classLoaders.containsKey("main");
    String jarUrl = jar.split(";md5;", 2)[0];
    return classLoaders.containsKey(MD5.string2MD5(jarUrl));
}
```

在 `load(String cache)` 清空订阅状态：

```java
jarLoads.clear();
classLoaders.clear();
```

- [ ] **Step 2：让安全检查返回三态，不再异常放行**

删除返回 `boolean` 的 `usesUnsupportedGuard()`。在 Android 14+ 使用：

```java
private DynamicCode.JarInspection inspectJar(File jar) {
    String blockedEntry = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
            ? "assets/ftyshinidie.guard"
            : null;
    return DynamicCode.inspectJar(jar, "classes.dex", blockedEntry);
}
```

任何 `INVALID` 或 `INCOMPATIBLE` 都不得进入 `DexClassLoader`。

- [ ] **Step 3：把 ClassLoader 初始化改成返回实例**

将 `loadClassLoader(String jar, String key)` 改为：

```java
private DexClassLoader createClassLoader(File jar, String key) {
    try {
        DynamicCode.JarInspection inspection = inspectJar(jar);
        if (inspection != DynamicCode.JarInspection.COMPATIBLE) return null;
        File cacheDir = new File(App.getInstance().getCacheDir(), "catvod_csp");
        if (!cacheDir.exists() && !cacheDir.mkdirs()) return null;
        DexClassLoader classLoader = new DexClassLoader(
                jar.getAbsolutePath(),
                cacheDir.getAbsolutePath(),
                null,
                App.getInstance().getClassLoader()
        );
        Class<?> init = classLoader.loadClass("com.github.catvod.spider.Init");
        Method method = init.getMethod("init", Context.class);
        method.invoke(null, App.getInstance());
        try {
            Class<?> proxy = classLoader.loadClass("com.github.catvod.spider.Proxy");
            proxyMethods.put(key, proxy.getMethod("proxy", Map.class));
        } catch (Throwable ignored) {
        }
        classLoaders.put(key, classLoader);
        return classLoader;
    } catch (Throwable throwable) {
        throwable.printStackTrace();
        return null;
    }
}
```

移除五轮 `ClassNotFoundException + sleep(200)` 重试。完整 JAR 发布后类加载应同步可用；失败应直接归类，避免每个坏源额外等待一秒。

- [ ] **Step 4：实现缓存复用、唯一下载和原子发布**

将 `loadJarInternal` 改为通过 `jarLoads.getOrLoad()` 执行。加载 callable 必须按以下顺序：

```java
private DexClassLoader loadJarInternal(String jar, String md5, String key) {
    try {
        JarLoadCoordinator.Result<DexClassLoader> result = jarLoads.getOrLoad(key, () -> {
            File cache = new File(App.getInstance().getFilesDir(), key + ".jar");
            DynamicCode.JarInspection cached = inspectJar(cache);
            boolean md5Matches = md5.isEmpty() ||
                    (cache.exists() && MD5.getFileMd5(cache).equalsIgnoreCase(md5));
            if (cached == DynamicCode.JarInspection.INCOMPATIBLE) {
                return JarLoadCoordinator.Result.incompatible();
            }
            if (cached == DynamicCode.JarInspection.COMPATIBLE && md5Matches &&
                    DynamicCode.ensureReadOnly(cache)) {
                DexClassLoader loader = createClassLoader(cache, key);
                return loader == null
                        ? JarLoadCoordinator.Result.retryableFailure()
                        : JarLoadCoordinator.Result.success(loader);
            }

            File temp = DynamicCode.createTempFile(cache);
            try (okhttp3.Response response = OkGo.<File>get(jar).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    DynamicCode.deleteQuietly(temp);
                    return JarLoadCoordinator.Result.retryableFailure();
                }
                try (InputStream input = response.body().byteStream();
                     OutputStream output = new FileOutputStream(temp)) {
                    byte[] buffer = new byte[8192];
                    int length;
                    while ((length = input.read(buffer)) != -1) {
                        output.write(buffer, 0, length);
                    }
                    output.flush();
                }
            } catch (Throwable throwable) {
                DynamicCode.deleteQuietly(temp);
                return JarLoadCoordinator.Result.retryableFailure();
            }

            DynamicCode.JarInspection downloaded = inspectJar(temp);
            if (downloaded == DynamicCode.JarInspection.INCOMPATIBLE) {
                DynamicCode.deleteQuietly(temp);
                return JarLoadCoordinator.Result.incompatible();
            }
            if (downloaded != DynamicCode.JarInspection.COMPATIBLE ||
                    (!md5.isEmpty() && !MD5.getFileMd5(temp).equalsIgnoreCase(md5)) ||
                    !DynamicCode.publishReadOnly(temp, cache)) {
                DynamicCode.deleteQuietly(temp);
                return JarLoadCoordinator.Result.retryableFailure();
            }
            DexClassLoader loader = createClassLoader(cache, key);
            return loader == null
                    ? JarLoadCoordinator.Result.retryableFailure()
                    : JarLoadCoordinator.Result.success(loader);
        });
        return result.isSuccess() ? result.getValue() : null;
    } catch (Throwable throwable) {
        throwable.printStackTrace();
        return null;
    }
}
```

`OkGo.<File>get(jar).execute()` 在当前代码中返回 `okhttp3.Response`。外层 try-with-resources 关闭响应和响应体，内层关闭输入流与临时文件输出流；不得改回 `com.lzy.okgo.model.Response`。

- [ ] **Step 5：让 ApiConfig 暴露运行时就绪状态**

增加：

```java
public boolean isCspRuntimeReady(SourceBean sourceBean) {
    boolean js = sourceBean.getApi().endsWith(".js") || sourceBean.getApi().contains(".js?");
    return js
            ? jsLoader.isRuntimeReady(sourceBean.getJar())
            : jarLoader.isRuntimeReady(sourceBean.getJar());
}
```

订阅重新加载主 JAR 时继续调用 `jarLoader.load(...)`，由该方法清理上一订阅的 ClassLoader 和单飞状态。不要在每次搜索或 `FastSearchActivity.onDestroy()` 清理订阅级缓存。

- [ ] **Step 6：运行 Java 编译和工具测试**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests 'com.github.catvod.utils.*Test' \
  :app:compileDebugJavaWithJavac --stacktrace --no-daemon
```

Expected: tests PASS，Java compilation BUILD SUCCESSFUL。

---

### Task 5：将 JsLoader 接入同一单飞和缓存发布机制

**Files:**
- Modify: `app/src/main/java/com/github/catvod/crawler/JsLoader.java:20-136`
- Modify: `app/src/main/java/com/github/tvbox/osc/api/ApiConfig.java:739-743`

- [ ] **Step 1：增加 JS 运行时协调器**

在 `JsLoader` 中增加：

```java
private final JarLoadCoordinator<Class<?>> jarLoads = new JarLoadCoordinator<>();
```

把 `spiders`、`classs`、`jarLoads` 和 `recentJarKey` 全部改为 `JsLoader` 实例字段。`ApiConfig` 已持有唯一 `jsLoader` 实例，因此不再让不同订阅通过静态字段共享运行时状态。

- [ ] **Step 2：区分停止请求和清理订阅**

保留：

```java
public void stopAll() {
    for (Spider spider : spiders.values()) spider.cancelByTag();
}

public void clear() {
    for (Spider spider : spiders.values()) {
        spider.cancelByTag();
        spider.destroy();
    }
    spiders.clear();
    classs.clear();
    jarLoads.clear();
}
```

在 `ApiConfig` 增加：

```java
public void stopAllCsp() {
    jsLoader.stopAll();
}
```

`FastSearchActivity` 中原有 `JsLoader.stopAll()` 改为 `ApiConfig.get().stopAllCsp()`；`onDestroy()` 删除 `JsLoader.load()`，避免退出搜索页时销毁订阅级缓存。`ApiConfig.loadConfig()` 开始解析新订阅前调用 `jsLoader.clear()`。

- [ ] **Step 3：按 JarLoader 相同顺序重写 loadJarInternal**

必须满足：

```text
缓存完整且 MD5 匹配 → 复用
同 key 正在加载       → 等待共享结果
需要下载              → 写 .tmp、关闭、ZIP 检查、MD5、只读发布
加载成功              → 缓存 Method 类
失败                   → 当前源 SpiderNull，不影响其他源
```

JS API JAR 的必需入口仍为 `classes.dex`；类加载成功标准为：

```java
Class<?> methodClass = classLoader.loadClass("com.github.catvod.js.Method");
```

- [ ] **Step 4：增加 isRuntimeReady**

```java
public boolean isRuntimeReady(String jar) {
    if (jar == null || jar.isEmpty()) return true;
    String jarUrl = jar.split(";md5;", 2)[0];
    return classs.containsKey(MD5.string2MD5(jarUrl));
}
```

- [ ] **Step 5：运行 Java 编译**

Run:

```bash
./gradlew :app:compileDebugJavaWithJavac --stacktrace --no-daemon
```

Expected: BUILD SUCCESSFUL。

---

### Task 6：为搜索结果增加批次和来源信息

**Files:**
- Create: `app/src/main/java/com/github/tvbox/osc/event/SearchResultEvent.java`
- Modify: `app/src/main/java/com/github/tvbox/osc/viewmodel/SourceViewModel.java:485-568,916-998`
- Modify: `app/src/main/java/com/github/tvbox/osc/ui/activity/FastSearchActivity.kt:386-395`

- [ ] **Step 1：创建搜索结果载荷**

```java
package com.github.tvbox.osc.event;

import com.github.tvbox.osc.bean.AbsXml;

public final class SearchResultEvent {
    private final long batchId;
    private final String sourceKey;
    private final AbsXml data;

    public SearchResultEvent(long batchId, String sourceKey, AbsXml data) {
        this.batchId = batchId;
        this.sourceKey = sourceKey;
        this.data = data;
    }

    public long getBatchId() {
        return batchId;
    }

    public String getSourceKey() {
        return sourceKey;
    }

    public AbsXml getData() {
        return data;
    }
}
```

- [ ] **Step 2：为 getSearch 增加 batchId**

将唯一调用入口改为：

```java
public void getSearch(String sourceKey, String wd, long batchId)
```

增加统一发送方法：

```java
private void postSearchResult(long batchId, String sourceKey, AbsXml data) {
    EventBus.getDefault().post(new RefreshEvent(
            RefreshEvent.TYPE_SEARCH_RESULT,
            new SearchResultEvent(batchId, sourceKey, data)
    ));
}
```

- [ ] **Step 3：让所有搜索路径恰好发送一次结果**

为 `xml()` 和 `json()` 增加只供搜索使用的重载：

```java
private AbsXml searchXml(String xml, String sourceKey, long batchId) {
    AbsXml data = parseXml(xml, sourceKey);
    postSearchResult(batchId, sourceKey, data);
    return data;
}

private AbsXml searchJson(String json, String sourceKey, long batchId) {
    AbsXml data = parseJson(json, sourceKey);
    postSearchResult(batchId, sourceKey, data);
    return data;
}
```

将现有 `xml()`/`json()` 中解析和派发拆开：`parseXml`、`parseJson` 只解析并补 `sourceKey`，原有详情、快速搜索和 LiveData 行为继续由原方法派发。不要让 `searchXml/searchJson` 再触发旧的 `TYPE_SEARCH_RESULT`，否则会重复计数。

所有路径使用统一规则：

- type 3 同步成功或异常：发送一次。
- type 0/1/4 `onSuccess`：发送一次。
- type 0/1/4 `onError`：发送一次空结果。
- source 不存在或不支持：发送一次空结果。

先增加空源保护：

```java
SourceBean sourceBean = ApiConfig.get().getSource(sourceKey);
if (sourceBean == null) {
    postSearchResult(batchId, sourceKey, null);
    return;
}
```

- [ ] **Step 4：临时更新 Activity 订阅以兼容载荷**

在完整 `SearchBatch` 接入前，先把订阅转换为：

```kotlin
val result = event.obj as? SearchResultEvent ?: return
searchData(result.data)
```

Task 7 会补批次和来源完成控制。

- [ ] **Step 5：运行 Java 与 Kotlin 编译**

Run:

```bash
./gradlew :app:compileDebugJavaWithJavac \
  :app:compileDebugKotlin --stacktrace --no-daemon
```

Expected: BUILD SUCCESSFUL。

---

### Task 7：固定搜索批次隔离和完成计数

**Files:**
- Create: `app/src/main/java/com/github/tvbox/osc/search/SearchBatch.java`
- Create: `app/src/test/java/com/github/tvbox/osc/search/SearchBatchTest.java`
- Modify: `app/src/main/java/com/github/tvbox/osc/ui/activity/FastSearchActivity.kt:63-80,386-488,529-559`

- [ ] **Step 1：编写 SearchBatch 红灯测试**

```java
package com.github.tvbox.osc.search;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SearchBatchTest {

    @Test
    public void sourceCompletesOnlyOnce() {
        SearchBatch batch = new SearchBatch(7L, Arrays.asList("a", "b"));
        assertEquals(1, batch.complete("a"));
        assertEquals(1, batch.complete("a"));
        assertEquals(0, batch.complete("b"));
        assertTrue(batch.isComplete());
    }

    @Test
    public void unknownSourceDoesNotChangeCount() {
        SearchBatch batch = new SearchBatch(8L, Arrays.asList("a"));
        assertEquals(1, batch.complete("unknown"));
        assertFalse(batch.isComplete());
    }

    @Test
    public void idsIdentifyStaleEvents() {
        SearchBatch batch = new SearchBatch(9L, Arrays.asList("a"));
        assertTrue(batch.matches(9L));
        assertFalse(batch.matches(8L));
    }
}
```

- [ ] **Step 2：运行红灯测试**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests 'com.github.tvbox.osc.search.SearchBatchTest' --stacktrace --no-daemon
```

Expected: FAIL，提示 `SearchBatch` 不存在。

- [ ] **Step 3：实现 SearchBatch**

```java
package com.github.tvbox.osc.search;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public final class SearchBatch {
    private final long id;
    private final Set<String> expected;
    private final Set<String> completed = new HashSet<>();

    public SearchBatch(long id, Collection<String> sourceKeys) {
        this.id = id;
        this.expected = new HashSet<>(sourceKeys);
    }

    public boolean matches(long batchId) {
        return id == batchId;
    }

    public synchronized int complete(String sourceKey) {
        if (expected.contains(sourceKey)) completed.add(sourceKey);
        return expected.size() - completed.size();
    }

    public synchronized boolean isComplete() {
        return completed.size() == expected.size();
    }
}
```

- [ ] **Step 4：运行 SearchBatch 绿灯测试**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests 'com.github.tvbox.osc.search.SearchBatchTest' --stacktrace --no-daemon
```

Expected: 3 tests PASS。

- [ ] **Step 5：在 FastSearchActivity 建立批次**

增加字段：

```kotlin
private var nextBatchId = 0L
private var currentBatch: SearchBatch? = null
```

在 `searchResult()` 收集最终 `siteKey` 后：

```kotlin
val batchId = ++nextBatchId
currentBatch = SearchBatch(batchId, siteKey)
```

任务调用改为：

```kotlin
sourceViewModel.getSearch(key, searchTitle, batchId)
```

任务外层不得静默吞掉异常。若 `getSearch` 之外仍抛出异常，发送一次空事件：

```kotlin
catch (throwable: Throwable) {
    EventBus.getDefault().post(
        RefreshEvent(
            RefreshEvent.TYPE_SEARCH_RESULT,
            SearchResultEvent(batchId, key, null)
        )
    )
}
```

- [ ] **Step 6：按批次和 sourceKey 接收结果**

订阅改为：

```kotlin
val result = event.obj as? SearchResultEvent ?: return
val batch = currentBatch ?: return
if (!batch.matches(result.batchId)) return
searchData(result.sourceKey, result.data)
val remaining = batch.complete(result.sourceKey)
if (remaining == 0) finishSearch()
```

`searchData()` 只追加结果，不再操作 `allRunCount`。删除 `allRunCount` 字段和所有设置、递增、递减逻辑。

增加：

```kotlin
private fun finishSearch() {
    if (searchAdapter.data.isEmpty()) showEmpty()
    cancel()
}
```

- [ ] **Step 7：运行全部 JVM 测试和 Kotlin 编译**

Run:

```bash
./gradlew :app:testDebugUnitTest \
  :app:compileDebugKotlin --stacktrace --no-daemon
```

Expected: 11 tests PASS，Kotlin compilation BUILD SUCCESSFUL。

---

### Task 8：按成本排序搜索源并保持渐进展示

**Files:**
- Modify: `app/src/main/java/com/github/tvbox/osc/ui/activity/FastSearchActivity.kt:446-488`
- Modify: `app/src/main/java/com/github/tvbox/osc/api/ApiConfig.java:739-755`

- [ ] **Step 1：增加明确的来源优先级函数**

在 `FastSearchActivity` 中加入：

```kotlin
private fun sourcePriority(source: SourceBean, homeKey: String): Int {
    if (source.key == homeKey) return 0
    if (source.type == 0 || source.type == 1 || source.type == 4) return 1
    if (source.type == 3 && ApiConfig.get().isCspRuntimeReady(source)) return 2
    return 3
}
```

- [ ] **Step 2：过滤后稳定排序，不改变最终来源集合**

把先移除首页再插到首位的逻辑替换为：

```kotlin
val homeKey = ApiConfig.get().homeSourceBean.key
val searchRequestList = ApiConfig.get().sourceBeanList
    .filter { it.isSearchable }
    .filter { mCheckSources == null || mCheckSources!!.containsKey(it.key) }
    .withIndex()
    .sortedWith(compareBy<IndexedValue<SourceBean>>(
        { sourcePriority(it.value, homeKey) },
        { it.index }
    ))
    .map { it.value }
```

然后从排序后的完整列表生成 `siteKey`、`spNames` 和 `SearchBatch`。不得删除慢源或设置固定总超时。

- [ ] **Step 3：保持固定 10 线程和即时 UI 追加**

保留：

```kotlin
Executors.newFixedThreadPool(10)
```

不要把 JAR 下载放到主线程，不要等待全部 Future 后再更新适配器。每个当前批次结果到达时继续调用 `searchAdapter.addData(data)`。

- [ ] **Step 4：运行编译和完整 JVM 测试**

Run:

```bash
./gradlew :app:testDebugUnitTest \
  :app:compileDebugJavaWithJavac \
  :app:compileDebugKotlin --stacktrace --no-daemon
```

Expected: tests PASS，Java/Kotlin compilation BUILD SUCCESSFUL。

---

### Task 9：执行重复缺陷红绿证明和范围扫描

**Files:**
- Test: `app/src/test/java/com/github/catvod/utils/JarLoadCoordinatorTest.java`
- Test: `app/src/test/java/com/github/catvod/utils/DynamicCodeTest.java`
- Test: `app/src/test/java/com/github/tvbox/osc/search/SearchBatchTest.java`

- [ ] **Step 1：保存当前实现补丁而不修改用户其他工作**

先检查：

```bash
git status --short --branch -uall
```

如果存在与本任务无关的修改，停止红灯回退并报告，禁止覆盖或 stash 用户工作。若只有本任务文件，使用 `git diff` 保存可恢复补丁路径，或在已授权提交的情况下先提交当前任务。

- [ ] **Step 2：红灯回退单飞实现**

临时让 `JarLoadCoordinator.getOrLoad()` 每次直接执行 `loader.call()`，运行：

```bash
./gradlew :app:testDebugUnitTest \
  --tests 'com.github.catvod.utils.JarLoadCoordinatorTest.sameKeyLoadsOnlyOnce' \
  --stacktrace --no-daemon
```

Expected: FAIL，`calls` 大于 1。

立即恢复单飞实现。

- [ ] **Step 3：红灯回退 fail-closed 检查**

临时让 `inspectJar()` 捕获无效 ZIP 时返回 `COMPATIBLE`，运行：

```bash
./gradlew :app:testDebugUnitTest \
  --tests 'com.github.catvod.utils.DynamicCodeTest.incompleteZipIsRejected' \
  --stacktrace --no-daemon
```

Expected: FAIL，实际值为 `COMPATIBLE`。

立即恢复 fail-closed 实现。

- [ ] **Step 4：恢复后运行绿灯全集**

Run:

```bash
./gradlew :app:testDebugUnitTest --stacktrace --no-daemon
```

Expected: 全部测试 PASS。

- [ ] **Step 5：扫描同类风险**

Run:

```bash
rg -n "new DexClassLoader|FileOutputStream\(.*jar|openReadOnlyOutput|\.jar\.tmp|MD5\.string2MD5\(jar" \
  app/src/main/java --glob '!**/build/**'
```

逐项记录：

- `ApiConfig` 主 `csp.jar`：单一订阅加载入口，必须使用完整性检查，但不会被搜索线程重复下载。
- `JarLoader`：本任务已修复。
- `JsLoader`：本任务已修复。
- 其他命中：判断是否存在同一路径并发写入；不相关问题只报告，不扩大修改。

---

### Task 10：完成构建验证和静态清理

**Files:**
- All files modified by Tasks 1-9

- [ ] **Step 1：检查临时日志**

Run:

```bash
rg -n "\[TEMP:search-performance\]|System\.out\.println\(\"自定义.*加载成功" app/src/main/java
```

删除本任务新增的所有 `[TEMP:search-performance]` 日志。已有长期日志仅在符合项目日志规范时保留；不要顺手清理无关历史日志。

- [ ] **Step 2：检查补丁格式**

Run:

```bash
git diff --check
```

Expected: no output。

- [ ] **Step 3：运行 JVM 测试**

Run:

```bash
./gradlew :app:testDebugUnitTest --stacktrace --no-daemon
```

Expected: BUILD SUCCESSFUL，全部新增回归测试 PASS。

- [ ] **Step 4：运行 Java 和 Kotlin 最小编译**

Run:

```bash
./gradlew :app:compileDebugJavaWithJavac \
  :app:compileDebugKotlin --stacktrace --no-daemon
```

Expected: BUILD SUCCESSFUL。

- [ ] **Step 5：运行 CI 对齐构建**

Run:

```bash
./gradlew :app:assembleDebug --stacktrace --no-daemon
```

Expected: BUILD SUCCESSFUL，并生成当前 `versionName` 对应的 Debug APK。

- [ ] **Step 6：若已获提交授权，按功能明确暂存并提交**

建议分三次提交：

```text
test: 添加动态JAR并发加载回归测试
fix: 原子加载并复用共享动态JAR
perf: 优化聚合搜索调度与批次隔离
```

每次提交前运行 `~/.claude/bin/safe-git-submit --check`，并只暂存对应文件。设计文档和计划文档可在用户要求提交时单独使用：

```text
docs: 添加聚合搜索并发与性能优化方案
```

---

### Task 11：获得授权后在 Pixel Fold 做真实设备验证

**Files:**
- No source changes expected

- [ ] **Step 1：等待明确安装与启动授权**

未经用户明确要求，不安装、不启动。获得授权后确认目标设备：

```bash
adb devices -l
```

目标必须是：

```text
35041FDHS003DQ  model:Pixel_Fold
```

- [ ] **Step 2：读取当前 versionName 并安装完整 APK**

从 `app/build.gradle` 读取 `versionName`，安装对应 Debug APK。禁止长期写死文件名：

```bash
adb -s 35041FDHS003DQ install -r <当前Debug APK路径>
```

使用完整安装清除 Apply Changes overlay，不使用 Apply Changes 验证本缺陷。

- [ ] **Step 3：按项目流程启动**

```bash
adb -s 35041FDHS003DQ shell am force-stop com.github.tvbox.osc
adb -s 35041FDHS003DQ shell monkey -p com.github.tvbox.osc -c android.intent.category.LAUNCHER 1
```

- [ ] **Step 4：采集冷搜索基线**

清空日志后，用同一订阅、同一关键词执行首次搜索：

```bash
adb -s 35041FDHS003DQ logcat -c
adb -s 35041FDHS003DQ logcat -v threadtime > /tmp/tvbox-search-cold.log
```

记录：提交搜索时间、首条结果时间、第五条结果时间、完成时间、参与源数、成功/空/失败/不兼容数、每个 `jarKey` 下载与 ClassLoader 初始化次数。

- [ ] **Step 5：采集同会话第二次搜索**

不重启应用，用同一关键词再次搜索并记录相同指标。预期：

- 不重复下载已成功或已标记不兼容的 JAR。
- 首条和第五条结果时间不慢于首次搜索。
- 同一 `jarKey` 不出现重复 ClassLoader 初始化。

- [ ] **Step 6：验证连续关键词批次隔离**

快速依次搜索两个不同关键词。确认第二个关键词列表中没有第一个关键词的迟到结果，最终完成计数等于第二批参与源数。

- [ ] **Step 7：检查崩溃和半成品 JAR 错误**

Run:

```bash
adb -s 35041FDHS003DQ logcat -d -v threadtime | rg -i \
  "EOCD not found|is not zip|ClassNotFoundException.*catvod|JNI DETECTED|Fatal signal 6|MusicGuard|ftyshinidie"
adb -s 35041FDHS003DQ shell dumpsys activity exit-info com.github.tvbox.osc | rg -n -C 3 \
  "APP CRASH|SIGNALED|timestamp="
```

Expected:

- 无新的 `EOCD not found` 或 `is not zip`。
- 无动态 JAR 半成品导致的 `ClassNotFoundException`。
- 无新的 `JNI DETECTED ERROR`、`SIGABRT`。
- 不兼容保护 JAR 不进入 `MusicGuard` 构造和 JNI。

- [ ] **Step 8：记录性能对比和结论**

最终报告必须包含真实数值：

```text
冷搜索：首条 / 第五条 / 完成时间
热搜索：首条 / 第五条 / 完成时间
参与源：总数 / 成功 / 空 / 失败 / 不兼容
共享JAR：每个jarKey下载次数 / ClassLoader初始化次数
崩溃：无新崩溃或列出失败证据
```

不得用“感觉更快”替代测量结果。
