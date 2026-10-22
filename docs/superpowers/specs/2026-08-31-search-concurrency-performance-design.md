# 聚合搜索并发修复与性能优化设计

> 历史设计资料（2026-08-31）：用于追溯方案，不是当前待办或执行指令。现役实现以源码、测试及根目录 [CLAUDE.md](../../../CLAUDE.md) 为准；文中的原子发布描述不代表当前实现提供原子替换保证，当前 `DynamicCode` 使用同目录临时文件、备份与重命名恢复。未勾选步骤不代表当前仍未实现，也不构成设备验收证据。

## 背景

`FastSearchActivity` 使用固定 10 线程并发搜索所有启用的内容源。多个源可能共享同一个独立 Spider JAR，但 `JarLoader` 和 `JsLoader` 会让每个调用方分别检查、删除、下载并加载同一个缓存文件。

在 Google Pixel Fold（Android 16）上，搜索期间出现了两类直接相关的故障：

- 多个线程读取尚未写完的 JAR，日志出现 `EOCD not found`、`is not zip` 和 `ClassNotFoundException`，对应内容源返回空结果。
- Android 14+ 的保护壳检查读取半成品 JAR 时发生异常并返回“不含保护壳”，随后另一个线程完成文件写入，`DexClassLoader` 成功加载受保护的 Spider。第三方 `MusicGuard` 最终在 JNI 中对空 `ClassLoader` 调用 `loadClass()`，触发 `SIGABRT`。

当前问题的根因是同一 `jarKey` 缺少单飞加载和原子发布机制。简单增加搜索线程数会扩大竞态，不能提升有效吞吐量。

## 目标

1. 同一个 JAR 在一个订阅会话内最多下载、校验和创建一次 ClassLoader。
2. 正式缓存路径始终指向完整、已校验、只读的 JAR。
3. Android 14+ 无法确认 JAR 安全时拒绝加载，不进入第三方初始化或 JNI。
4. 单个坏源不终止其他源的搜索。
5. 搜索结果渐进展示，优先缩短首条和前五条结果的等待时间。
6. 不通过固定短超时截断慢源，保留最终源覆盖率。
7. 连续搜索时隔离不同批次，旧搜索的迟到结果不能混入新结果。
8. 同步处理 `JarLoader` 和 `JsLoader` 的同类并发风险。

## 非目标

- 不修改内容源协议或 Spider 接口。
- 不重写整个搜索页面或网络层。
- 不通过无限增加线程数换取表面速度。
- 不尝试修复第三方 FTYGuard 原生实现。
- 不保证失效、下线或返回错误数据的源产生结果。

## 总体方案

采用“共享加载任务、原子缓存、订阅级复用、渐进搜索”方案。

### JAR 状态模型

每个 `jarKey` 只有一个加载状态：

```text
未加载
  → 加载中
      → 已加载
      → 不兼容
      → 加载失败
```

调用方按以下规则处理：

- 已加载：立即复用缓存的 ClassLoader 或 JS API 类。
- 加载中：等待同一个共享加载任务，不重复下载。
- 不兼容：立即返回失败，本订阅会话内不再尝试。
- 加载失败：当前调用返回失败；是否允许后续重试由失败类型决定。
- 未加载：当前调用成为唯一加载者。

状态按 `jarKey` 隔离。一个 JAR 的失败不能阻塞其他 JAR。

## 组件设计

### 1. 动态 JAR 缓存发布

扩展 `DynamicCode`，提供临时文件写入、完整性检查、只读设置和原子发布所需的最小能力。

下载流程：

```text
创建 <jarKey>.jar.tmp
→ 下载到临时文件
→ flush 并关闭输出流
→ 检查 HTTP 响应和文件长度
→ 使用 ZipFile 验证 ZIP 中央目录
→ 校验必需入口
→ 配置提供 MD5 时校验 MD5
→ Android 14+ 检查不兼容保护资源
→ 设置临时文件只读
→ 原子替换 <jarKey>.jar
→ 从正式文件创建 ClassLoader
```

约束：

- `DexClassLoader` 只能读取正式 `.jar`，不能读取 `.tmp`。
- 校验失败时删除临时文件，保留上一份已验证缓存。
- 原子移动失败时不得继续加载目标文件。
- 新下载文件发布前不删除可用旧缓存。
- 缓存文件缺少 MD5 时仍可复用，但必须通过 ZIP 和入口检查。

### 2. 安全检查

保护壳检查改为 fail-closed：

```text
明确安全       → 允许加载
明确不兼容     → 拒绝加载
文件无效或未知 → 拒绝加载
```

Android 14+ 检测到 `assets/ftyshinidie.guard` 时，将该 `jarKey` 标记为不兼容。应用不得创建对应 `DexClassLoader`，也不得执行 `Init.init()`、Spider 构造函数或第三方 JNI。

Android 13 及以下保留原有兼容行为，但仍执行文件完整性和单飞加载控制。

### 3. JarLoader 单飞加载

`JarLoader` 按 `jarKey` 保存共享加载任务。第一个请求执行实际加载，其他请求等待同一个结果。

加载结果包含：

- 成功及对应 `DexClassLoader`。
- 不兼容。
- 文件无效。
- 下载或网络失败。
- 初始化失败。

加载完成后必须唤醒全部等待者。异常路径也必须完成共享任务，避免永久等待。

成功的 ClassLoader 保留到订阅重新加载。搜索取消不清理 ClassLoader。

### 4. JsLoader 同类治理

`JsLoader` 使用与 `JarLoader` 相同的缓存发布和单飞原则，但结果类型为 `com.github.catvod.js.Method` 对应的类。

`JsLoader.stopAll()` 只取消当前 Spider 请求，不清理已加载类。订阅切换或配置重新加载时，`JsLoader.load()` 才销毁 Spider 并清理订阅级缓存。

### 5. 搜索批次隔离

每次调用 `search(title)` 时生成递增的批次编号。每个源任务和异步结果都携带该编号。

结果到达时：

- 批次与当前批次相同：处理结果并完成该源计数。
- 批次已过期：丢弃结果，不修改适配器、标签和当前计数。

每个源必须恰好完成一次计数，包括成功、空结果、网络失败、JAR 失败和任务取消。

这项约束避免旧请求迟到后污染新关键词结果，也避免加载状态永久停留。

### 6. 搜索优先级

保留 10 个搜索工作线程，不继续提高并发数。提交任务前按成本排序：

1. 首页源。
2. 普通 HTTP/XML/JSON 源。
3. 已完成 ClassLoader 初始化的 Spider 或 JS 源。
4. 需要首次下载或初始化 JAR 的源。

同一优先级保持订阅配置顺序。结果仍逐个源追加到当前列表，不等待全部源完成。

共享 JAR 的首个源完成加载后，共用该 JAR 的其余源可以并发执行实际搜索。

## 数据流

```text
用户提交关键词
→ 取消上一批网络请求和未开始任务
→ 创建新批次
→ 收集并过滤可搜索源
→ 按执行成本排序
→ 提交到固定线程池
→ 源请求 JAR/JS 运行时
    → 已加载：立即复用
    → 加载中：等待共享结果
    → 未加载：唯一线程下载、校验、发布、初始化
→ 执行源搜索
→ 返回带批次和 sourceKey 的结果
→ 丢弃过期批次
→ 当前批次渐进更新列表和来源标签
→ 所有源完成后结束加载状态
```

## 错误处理

- 网络失败：该源返回失败，其他源继续。
- HTTP 响应为空或失败：不创建缓存文件。
- ZIP 无效：删除临时文件，不进入 ClassLoader。
- MD5 不匹配：删除临时文件，不覆盖旧缓存。
- 不兼容保护壳：本订阅会话内记忆失败，避免重复尝试。
- `Init.init()` 失败：ClassLoader 不发布为成功。
- Spider 构造或搜索失败：只影响当前源。
- 搜索取消：中断尚未开始的任务；已发出的异步回调通过批次编号失效。

长期日志只记录可定位问题的摘要，不输出订阅地址、搜索词或其他敏感信息。若实施阶段需要临时计时日志，使用 `[TEMP:search-performance]`，验证后全部删除。

## 性能预期

主要收益来自去重和复用，而非扩大并发：

- 同一共享 JAR 从多个源重复下载降为每订阅会话一次。
- 同一共享 JAR 从多个 ClassLoader 初始化降为一次。
- 后续搜索直接复用已加载运行时。
- 快速 HTTP 源和已就绪 Spider 源优先产生首批结果。
- 无效 JAR 在进入 DexClassLoader 前快速失败，减少每次最多五轮类加载重试。

不预设固定毫秒目标。实施前后在同一设备、同一订阅、同一关键词下测量并报告实际数据。

## 验证方案

### 自动化回归测试

项目当前没有测试基础设施。为本次重复出现的并发缺陷增加最小 JVM 测试边界，测试可独立于 Android `DexClassLoader` 验证缓存协调器和文件发布逻辑。

必须覆盖：

1. 10 个并发调用请求同一 `jarKey`，下载函数只执行一次。
2. 等待者获得同一个成功结果。
3. 下载失败后全部等待者结束，不发生死锁。
4. 临时文件未完成时正式缓存保持不变。
5. ZIP 无效时不发布正式缓存。
6. 安全检查未知时拒绝加载。
7. 不同 `jarKey` 可以并发加载，互不阻塞。
8. 旧搜索批次的结果被丢弃。
9. 每个源在成功、失败和取消路径上只完成一次计数。

针对重复修复要求执行红绿验证：先在未修复实现上确认竞态测试失败，再应用实现并确认通过。

### 构建验证

至少执行：

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:compileDebugJavaWithJavac --stacktrace --no-daemon
./gradlew :app:compileDebugKotlin --stacktrace --no-daemon
```

如果新增或修改 Gradle 测试依赖，扩大到：

```bash
./gradlew :app:assembleDebug --stacktrace --no-daemon
```

### Pixel Fold 运行验证

安装和启动需用户另行明确授权。获得授权后，在 Pixel Fold 上使用同一订阅和关键词执行冷启动首次搜索、同会话第二次搜索和连续切换关键词搜索。

记录：

- 首条结果时间。
- 第五条结果时间。
- 全部源完成时间。
- 参与搜索的源数。
- 成功、空结果、失败和不兼容源数。
- JAR 下载次数和 ClassLoader 初始化次数。
- 搜索前后进程内存。

通过标准：

- 同一 `jarKey` 每个订阅会话最多下载和初始化一次。
- 正式 JAR 在搜索全程保持完整 ZIP。
- 单个坏源不终止搜索。
- 最终完成计数与参与源数一致。
- 无 `EOCD not found`、半成品 JAR ClassNotFoundException、`JNI DETECTED ERROR` 或 `SIGABRT`。
- 第二次搜索不重复下载 JAR，并明显快于首次冷搜索。

## 预期修改范围

主要修改：

- `app/src/main/java/com/github/catvod/utils/DynamicCode.java`
- `app/src/main/java/com/github/catvod/crawler/JarLoader.java`
- `app/src/main/java/com/github/catvod/crawler/JsLoader.java`
- `app/src/main/java/com/github/tvbox/osc/ui/activity/FastSearchActivity.kt`
- `app/src/main/java/com/github/tvbox/osc/viewmodel/SourceViewModel.java`

可能新增少量职责单一的缓存协调或搜索批次类，以及对应 JVM 测试。若实施需要修改超过上述边界或引入跨模块重构，应先暂停并重新确认范围。

## 完成条件

- 竞态根因由自动化红绿测试固定。
- Java、Kotlin 和 Debug 构建验证通过。
- 不兼容保护 JAR 在 Android 14+ fail-closed。
- 聚合搜索渐进展示全部可用源结果。
- 同一订阅会话复用共享 JAR 和运行时。
- 获得设备安装授权后，在 Pixel Fold 上完成性能对比和无崩溃验证。
