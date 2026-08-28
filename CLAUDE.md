# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概览

这是一个面向 Android 手机和平板的 TVBox 客户端，主体为 Java，部分页面使用 Kotlin，界面使用 XML 与 ViewBinding。根项目名为 `TVBoxMobile`，应用包名为 `com.github.tvbox.osc`。

模块职责：

- `:app`：应用入口、页面、订阅源解析、搜索、播放编排、Room 数据和局域网控制服务。
- `:player`：基于 DKPlayer 的系统播放器、IJK 和 ExoPlayer 封装。
- `:quickjs`：供动态 JavaScript 源使用的 QuickJS 运行时。
- `:TabLayout`、`:ViewPager1Delegate`：标签页和 ViewPager 支持库。
- `:crash`：应用内崩溃处理页面。

应用当前只打包 `arm64-v8a`，因此不能直接安装到 x86 模拟器或 32 位设备。最低 API 24，编译和目标 API 34。

## 构建与验证

使用 JDK 17、仓库内 Gradle Wrapper；本机需要在 `local.properties` 中配置 Android SDK。CI 安装 Android API 34 和 Build Tools 34.0.0。

```bash
# 与 CI 一致的最小编译验证
./gradlew :app:assembleDebug --stacktrace --no-daemon

# 构建所有模块及已配置的变体
./gradlew assemble

# 运行 app Android Lint
./gradlew :app:lint

# 清理构建产物
./gradlew clean

# 安装 Debug APK 到已连接的 arm64 设备
adb install -r app/build/outputs/apk/debug/TVBox-Mobile-v2.1.26.apk

# 启动应用
adb shell am start -n com.github.tvbox.osc/.ui.activity.SplashActivity
```

APK 文件名由 `app/build.gradle` 按 `TVBox-Mobile-v<versionName>.apk` 生成；不要在命令或文档中长期写死版本号，应先读取当前 `versionName`。

当前仓库没有 `src/test`、`src/androidTest` 测试或测试依赖。添加测试后可按 AGP 标准方式执行：

```bash
# 全部 Debug JVM 单元测试
./gradlew :app:testDebugUnitTest

# 单个测试类或方法
./gradlew :app:testDebugUnitTest --tests 'fully.qualified.ClassName'
./gradlew :app:testDebugUnitTest --tests 'fully.qualified.ClassName.methodName'

# 单个设备测试方法
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=fully.qualified.ClassName#methodName
```

`app` 的 lint 配置为 `abortOnError false` 且不检查 Release 构建，因此 CI 的实际门禁是 `:app:assembleDebug`，lint 结果目前仅供人工检查。

## 核心架构与数据流

### 启动和全局初始化

`AndroidManifest.xml` 以 `SplashActivity` 为入口，再进入 `MainActivity`。`App` 负责初始化 Hawk、网络层、EPG、Room、播放器原生库、QuickJS、崩溃处理、主题/壁纸、默认订阅和局域网控制服务所需的全局组件。所有 Activity 继承 `BaseActivity`，全局主题、壁纸和屏幕适配行为优先在这里理解。

### 订阅配置和内容源

`HomeFragment` 启动本地控制服务，并通过 `ApiConfig` 加载当前订阅配置。配置来源不只包含 HTTP，还可能是缓存文件、`assets://` 或持久授权的 `content://` URI。

`ApiConfig` 是动态源系统的中心：解析站点、解析器、直播源、壁纸、广告规则、IJK 参数，以及 JS/JAR Spider；远程 JS/JAR 会缓存到应用私有目录并动态加载。修改配置格式、源加载或缓存逻辑时，应同时检查 `ApiConfig`、订阅管理页面和 `app/src/main/assets/js/`。

`SourceViewModel` 根据站点类型分发请求：普通 XML/JSON API、动态 Spider 或 type-4 API。结果主要通过 LiveData 返回页面，聚合搜索等跨页面流程还使用 EventBus。

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
- 发布 Tag 必须严格为 `v<app/build.gradle 中的 versionName>`。发布工作流依赖 `TVBOX_KEYSTORE_BASE64`、`TVBOX_KEY_ALIAS`、`TVBOX_KEY_PASSWORD`、`TVBOX_STORE_PASSWORD`。
- 当前 GitHub Actions 的 Tag 发布步骤实际执行 `:app:assembleDebug` 并发布 debug 输出，尽管步骤名称写作 release artifact；修改发布流程前先确认这是有意行为。
- `scripts/sync_release_metadata.sh` 会修改 `update.json` 和 `README.md`，仅用于发布元数据同步，不要在普通开发验证中运行。
