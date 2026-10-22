# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概览

这是一个面向 Android 手机和平板的 TVBox 客户端，主体为 Java，部分页面使用 Kotlin，界面使用 XML 与 ViewBinding。根项目名为 `TVBoxMobile`，应用包名为 `com.github.tvbox.osc`。

模块职责：

- `:app`：应用入口、页面、订阅源解析、搜索、播放编排、Room 数据和局域网控制服务。
- `:player`：基于 DKPlayer 的系统播放器、IJK 和 ExoPlayer 封装。
- `:TabLayout`、`:ViewPager1Delegate`：标签页和 ViewPager 支持库。
- `:crash`：应用内崩溃处理页面。

QuickJS 运行时通过外部依赖 `wang.harlon.quickjs:wrapper-android:3.2.3` 引入，不是本地 Gradle 模块。

应用当前只打包 `arm64-v8a`，因此不能直接安装到 x86 模拟器或 32 位设备。最低 API 24，编译和目标 API 34。

## 构建与验证

使用 JDK 17、仓库内 Gradle Wrapper；本机需要在 `local.properties` 中配置 Android SDK。CI 安装 Android API 34 和 Build Tools 35.0.0。

```bash
# 与 CI 一致的 Debug 打包
./gradlew :app:assembleDebug --stacktrace --no-daemon

# 构建所有模块及已配置的变体
./gradlew assemble

# 运行 app Android Lint
./gradlew :app:lint

# 清理构建产物
./gradlew clean
```

APK 文件名由 `app/build.gradle` 按 `TVBox-Mobile-v<versionName>.apk` 生成；不要在命令或文档中长期写死版本号，应先读取当前 `versionName`。

JVM 测试位于 `app/src/test`，使用 JUnit 4.13.2，覆盖动态代码文件、加载协调、缓存、搜索事件和搜索批次。

```bash
# 全部 Debug JVM 单元测试
./gradlew :app:testDebugUnitTest

# 单个测试类
./gradlew :app:testDebugUnitTest \
  --tests 'com.github.tvbox.osc.search.SearchBatchTest'

# 单个测试方法
./gradlew :app:testDebugUnitTest \
  --tests 'com.github.tvbox.osc.search.SearchBatchTest.completesExpectedSourcesOnlyOnce'
```

当前没有 `src/androidTest`，也未配置设备测试依赖和 runner；设备测试需先补齐配置和测试用例。

`app` 的 lint 配置为 `abortOnError false` 且不检查 Release 构建，lint 结果目前仅供人工检查。CI 执行 Debug 打包，并检查 APK ZIP 对齐和 native ELF 的 16 KB 页大小兼容性；当前 CI 不执行 JVM 测试。打包后可按 CI 方式检查 APK（`ANDROID_HOME` 必须指向已安装 Build Tools 35.0.0 的 SDK）：

```bash
(
  set -eu
  apk=$(find app/build/outputs/apk/debug -name '*.apk' -print -quit)
  test -n "$apk"
  "$ANDROID_HOME/build-tools/35.0.0/zipalign" -c -P 16 -v 4 "$apk"
  python3 scripts/verify_16k_elf.py "$apk"
)
```

仅在用户明确要求安装或启动时执行设备操作。执行前确认 `ANDROID_SERIAL` 已绑定到本次目标 arm64 设备，并校验设备仍可用；未绑定时先确认目标设备，不沿用历史序列号。

```bash
# 校验当前绑定并安装 Debug APK
(
  set -eu
  : "${ANDROID_SERIAL:?请先绑定目标 Android 设备}"
  test "$(adb -s "$ANDROID_SERIAL" get-state)" = device
  ./gradlew :app:installDebug
)

# 校验当前绑定并重新启动应用
(
  set -eu
  : "${ANDROID_SERIAL:?请先绑定目标 Android 设备}"
  test "$(adb -s "$ANDROID_SERIAL" get-state)" = device
  adb -s "$ANDROID_SERIAL" shell am force-stop com.github.tvbox.osc
  adb -s "$ANDROID_SERIAL" shell monkey -p com.github.tvbox.osc -c android.intent.category.LAUNCHER 1
)
```

## 核心架构与数据流

### 启动和全局初始化

`AndroidManifest.xml` 以 `SplashActivity` 为入口，再进入 `MainActivity`。`App` 负责初始化 Hawk、网络层、EPG、Room、播放器原生库、QuickJS、崩溃处理、主题/壁纸、默认订阅和局域网控制服务所需的全局组件。所有 Activity 继承 `BaseActivity`，全局主题、壁纸和屏幕适配行为优先在这里理解。

### 订阅配置和内容源

`HomeFragment` 启动本地控制服务，并通过 `ApiConfig` 加载当前订阅配置。配置来源不只包含 HTTP，还可能是缓存文件、`assets://` 或持久授权的 `content://` URI。

`ApiConfig` 是动态源系统的中心：解析站点、解析器、直播源、壁纸、广告规则、IJK 参数，以及 JS/JAR Spider；远程 JS/JAR 会缓存到应用私有目录并动态加载。修改配置格式、源加载或缓存逻辑时，应同时检查 `ApiConfig`、订阅管理页面和 `app/src/main/assets/js/`。

`ConfigCache` 沿用按订阅地址 MD5 命名的私有配置文件，校验后通过临时文件、备份与重命名发布。普通启动先读有效缓存，再后台更新磁盘；后台响应不得调用运行时解析或清空 Spider。长按首页源名称可选择“刷新首页 / 更新订阅 / 切换订阅”，“更新订阅”才强制联网并立即应用。订阅切换用代次隔离迟到响应；默认 JAR 有 MD5 时，即使使用缓存也必须校验。

`SourceViewModel` 根据站点类型分发请求：普通 XML/JSON API、动态 Spider 或 type-4 API。结果主要通过 LiveData 返回页面，聚合搜索等跨页面流程还使用 EventBus。

### 动态代码加载与隔离

`ApiConfig` 管理 `JarLoader` 和 `JsLoader`，分别承接 JAR 与 JavaScript Spider 加载。两者使用 `FileLoadCoordinator` 协调文件加载、`JarLoadCoordinator` 协调 JAR 初始化，避免同一资源的并发重复加载。清理加载器时推进 `GenerationPublisher` 的代次，旧代次的加载不能再发布文件或写回当前加载器缓存。

`DynamicCode` 统一处理动态代码文件的只读约束、JAR 检查、同目录临时文件发布及备份恢复。修改源加载、订阅切换或缓存逻辑时，应同时检查加载协调和代次隔离，不能只改下载入口。

### 聚合搜索

`FastSearchActivity` 创建 `SearchBatch`，将批次 ID 传给 `SourceViewModel.getSearch(...)`；结果通过包含批次 ID、源标识和数据的 `SearchResultEvent` 返回。页面忽略旧批次结果，`SearchBatch` 仅对预期源记录完成状态，并对重复完成通知去重。修改搜索调度时，需要保留批次隔离和按源计数的语义。

### 页面导航和播放

`MainActivity` 承载首页与“我的”页面，直播和订阅使用独立 Activity。`HomeFragment` 根据订阅配置动态创建分类标签；每个分类由 `GridFragment` 处理分页、筛选和目录式导航。

内容链路通常为：

`GridFragment` → `DetailActivity` → `SourceViewModel.getPlay(...)` → `PlayFragment` → `PlayerHelper`

`DetailActivity` 同时管理详情、线路/剧集选择、收藏和播放历史。`PlayerHelper` 根据配置选择系统、IJK 或 ExoPlayer，并选择 Texture/Surface 渲染；后台播放可转交前台 `PlayService`。播放问题不能只检查 UI，还要沿上述链路检查源解析结果、播放器类型和服务状态。

### 持久化

Room 数据入口集中在 `AppDataManager` 与 `RoomDataManger`，用于缓存、历史和收藏；Schema 输出到 `app/schemas`。数据库当前允许主线程查询，修改 DAO 调用方式时不要假定所有调用都已异步化。

### 局域网控制服务

`ControlManager`/`RemoteServer` 基于 NanoHTTPD 提供局域网页面、远程输入、文件操作和 `/proxy` Spider 代理。服务通常从 9978 端口开始，冲突时递增查找可用端口，并通过 EventBus/广播与应用交互。修改服务路由、外部存储或 Spider 代理时，需要在真实设备上验证局域网和文件权限行为。

## 设备与发布约束

- 手机和平板布局都需要考虑；`smallestScreenWidthDp >= 600` 时保留平板原生 dp，列表列数还会随宽度和方向变化。
- 真实设备验证应覆盖 Android 13+ 通知/媒体权限、前台播放服务、Android 14 动态广播注册、本地文件导入以及明文 HTTP 源。
- `TVBoxOSC.jks` 和 `local.properties` 是本地文件，不在仓库中。普通 Debug 构建没有 keystore 时仍可使用默认 Debug 签名。
- SDK 初始化必须显式指定 `packages: platform-tools`，避免 setup-android 默认安装已下线的 `tools` 包；Gradle 失败报告只在构建步骤实际失败时读取构建日志。
- 发布 Tag 必须严格为 `v<app/build.gradle 中的 versionName>`。发布工作流依赖 `TVBOX_KEYSTORE_BASE64`、`TVBOX_KEY_ALIAS`、`TVBOX_KEY_PASSWORD`、`TVBOX_STORE_PASSWORD`。
- 当前 GitHub Actions 的 Tag 发布步骤实际执行 `:app:assembleDebug` 并发布 debug 输出，尽管步骤名称写作 release artifact；修改发布流程前先确认这是有意行为。
- `nightly` 预发布仅在推送到 `main` 或手动选择 `main` 运行且前置步骤成功后更新；PR 和其他分支的手动运行只构建验证，不发布。Tag 版本发布走独立任务，以 `.github/workflows/build.yml` 为准。
- `nightly` 是滚动预发布标签，应与产物的构建提交一致；允许只更新此标签，不将该规则用于正式版本标签。发布验证必须同时核对标签提交、CI headSha 和 APK 附件，不能只看 Release 更新时间。
- `scripts/sync_release_metadata.sh` 会修改 `update.json` 和 `README.md`，仅用于发布元数据同步，不要在普通开发验证中运行。
