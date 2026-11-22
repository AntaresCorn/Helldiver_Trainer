# Helldiver_Trainer Agent 行为规则

本文件定义了 AI Agent 在 `Helldiver_Trainer` 项目中协作时必须遵守的工程规范与行为准则。

## 1. 项目架构与模块职责

本项目是基于 **Kotlin Multiplatform (KMP)** 与 **Compose Multiplatform** 的跨平台应用（支持 Android 与
JVM Desktop），根包名为 `cn.antares.helldiver_trainer`。

- **`:composeApp`**：核心共享模块
    - `commonMain`：跨平台业务逻辑、UI 与资源
        - `bridge/`：平台能力抽象（如 `Platform`、`SoundPlayer`、`SharedKV`、`OpenWeb`、`ModelStorage`）
        - `data/`：网络请求、实体与仓库层（Ktor / Ktorfit / `kotlinx.serialization`）
        - `di/`：Koin 依赖注入配置（`KoinModules.kt`）
        - `ui/`：页面与界面实现（Navigation Compose）
        - `viewmodel/`：页面状态与业务逻辑（Koin ViewModel + Coroutines）
        - `util/`：通用工具、主题与屏幕适配（`ColorAndTheme.kt`、`ScreenAdapter.kt`、`widget/`）
    - `androidMain` / `desktopMain`：各自平台的具体实现与平台专属资源
- **`:androidApp`**：Android 宿主入口与打包配置
- **`:desktopApp`**：Desktop (JVM) 宿主入口与打包配置

### 平台隔离原则

- 严禁在 `commonMain` 中直接引入 Android（`android.*`、`androidx.*`）或 JVM/Swing 专属 API。
- 涉及平台差异的能力（音频播放、键值存储、打开外部链接、本地模型存储等）必须通过 `bridge/` 下的抽象接口或
  `expect`/`actual` 机制实现，并在 `androidMain` 与 `desktopMain` 中分别提供实现。

## 2. 依赖、资源与配置规范

- **版本目录**：所有第三方库与插件版本统一在 `gradle/libs.versions.toml` 中管理，不要在子模块的
  `build.gradle.kts` 中硬编码版本号。
- **多平台资源**：
    - `moko-resources`（`composeApp/src/commonMain/moko-resources/`，资源包名
      `cn.antares.helldiver_trainer`）用于管理多语言字符串与 SVG 图标资源。
    - `composeResources`（`composeApp/src/commonMain/composeResources/`）用于 Compose 图片资源。
    - **音效同步**：Android 音效位于 `composeApp/src/androidMain/res/raw/`，Desktop 音效位于
      `composeApp/src/desktopMain/resources/`；新增、重命名或删除音效文件时，必须同步检查并更新双端资源。
- **构建配置**：版本号通过 `buildkonfig`（如 `VERSION_NAME`）与 `libs.versions.toml` 同步，修改版本时以
  `libs.versions.toml` 为准。

## 3. 编码风格与修改原则

- 遵循 `gradle.properties` 中的 `kotlin.code.style=official` 以及根目录 `.editorconfig` 规则（允许尾随逗号）。
- 优先复用现有组件与工具（如 `util/widget/` 下的 `SuperButton`、`SuperDialog`、`SuperScaffoldTopBar`
  等）以及 `ColorAndTheme.kt` 中的主题定义，保持 UI 风格一致。
- **最小化改动**：只修改与当前任务直接相关的文件；保留与当前改动无关的已有注释和代码结构，避免无故大范围重构。
- **安全与敏感文件**：切勿读取、输出或提交签名密钥与本地环境配置（如
  `androidApp/src/main/release.keystore`、`androidApp/src/main/signing.properties`、
  `local.properties`）。

## 4. 构建、运行与验证

- **验证范围与实机安装原则**：修改代码后，原则上只进行单元测试（单例测试）和编译验证， **不进行实机安装**
  （除非用户主动要求），优先交由用户进行测试和验证；排查、修复问题时，可连接设备对相关日志（log）进行 dump。
- **工具优先级**：进行任何 Android 相关操作时，优先通过 Android CLI 执行。
- **设备选择优先级**：需要选择设备时，先检查是否有已连接的物理机；若无物理机再检查虚拟机，连接虚拟机时优先复用现成的实例。
- 修改代码后，优先使用项目根目录的 Gradle Wrapper（Windows 下为 `.\gradlew.bat`，类 Unix 下为
  `./gradlew`）执行编译或测试以验证改动：
    - Desktop 热重载开发运行：`.\gradlew.bat :desktopApp:hotRun --auto`
    - 检查编译与构建时，按影响范围运行对应模块任务（如 `:composeApp`、`:desktopApp`、`:androidApp`
      的编译或组装任务）。
- 在最终答复中，只陈述在当前会话中实际读取的文件路径、符号名称以及实际运行并观察到的构建/测试结果，不得臆测未验证的输出。
