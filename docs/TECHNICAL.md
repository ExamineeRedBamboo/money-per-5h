# 技术说明

## 工程

原生 Kotlin、Jetpack Compose / Material 3、Room，minSdk 30，compile/target SDK 36，JDK 17。沿用固定的兼容组合：Gradle 9.2.0、AGP 9.0.1、Compose 编译插件 2.2.10、KSP 2.3.8、Compose BOM 2024.12.01、Room 2.8.4。Wrapper 校验发行包 SHA-256，不依赖 Android Studio。

## 领域与存储

- `domain/Usage.kt`：整数分解析、倍数及有效期、独立固定窗口、北京时间每日汇总。
- `domain/Session.kt`：可单独测试的状态转换，统一处理按键、等待、弹层、生命周期、过期、删除与撤销。
- `data/UsageDatabase.kt`：Room 数据库，`records` 仅有独立 ID、整数分金额、保存时间；`settings` 保存方案及暂存草稿。
- `data/UsageRepository.kt`：载入及事务提交。记录增删和相关设置在同一个事务中提交成功后才向界面报告成功。删除的撤销项不持久化。
- `ui/UsageViewModel.kt`：互斥串行处理操作，避免重复确认与计时器并发写入；写入失败保留待处理草稿并显示错误。
- `ui/UsageScreen.kt`：用量页、记录列表、热力图和输入弹层。

数据库名为 `manual-usage.db`，schema v1 随源码保存。首次升级到本版按所有者确认清除此前原型数据库及其日志文件；不导入旧字段或明细。此后启动不会清除新版数据库，不使用破坏性 Room schema 回退。加载失败时明确显示不可用，不以空账本掩盖错误。

历史不自动清理。固定窗口与每日汇总从保留明细重算；记录列表使用 LazyColumn。当前将全部明细载入内存，极长历史的分页和增量汇总属于后续性能优化，不改变当前保留规则。

## 生命周期与时间

前台每 100ms 更新等待条及到期状态，后台挂起计时循环，不进行自动保存。普通计时更新不写数据库，只在实际数据变化或暂停保留剩余时间时提交。方案到期检查先于自动保存和直接确认。

窗口、方案、保存时间使用系统毫秒时间。3 秒等待用 `elapsedRealtime` 单调时钟；切页、弹层及进入后台时保留剩余等待。进程恢复从持久化草稿恢复，未确认直接输入保持冻结。旋转屏幕沿用 ViewModel，不当成离开记录页来清除撤销；真正进入后台时清除撤销。恢复时再次检查到期，续设只解锁新操作，不自动提交冻结草稿。

每日分组和所有显示时间固定 Asia/Shanghai，和设备当前时区无关。热力图使用含本周的 12 个完整周列，未来格留空。日期切换后按新自然日刷新。

## 权限与构建产物

应用代码不声明网络、通知、无障碍、媒体、存储读取或后台采集权限，没有对应服务、授权入口、解析器、外部数据模型或样例。AndroidX 可能合并内部组件及签名级内部接收器权限，与应用读取外部数据无关。备份与设备迁移禁用。

旧构建产物应经 `clean` 清除后重建，检查合并 Manifest 和 APK 中的类、资源、权限，避免源码已删除而安装包仍含旧组件。

## 验证

```powershell
.\gradlew.bat clean :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --offline --console=plain
```

本地无依赖缓存时去掉 `--offline`。测试报告在 `app/build/reports/tests/testDebugUnitTest/`，Lint 报告在 `app/build/reports/lint-results-debug.html`。

领域测试覆盖金额与倍数边界、两个固定窗口及独立重置、去重与同额独立记录、超额、删除后重放、北京时间午夜、热图范围与颜色；状态测试覆盖等待、撤销上次、清空、直接输入替换与取消、切页及后台冻结、进程重建、到期保存竞态、续设草稿冻结、删除撤销失效和原始日期恢复。

电脑构建、纯 JVM 测试及静态检查不等于真机验收。仍须在 Android 设备验证：默认空账本、常用屏幕与放大字体、输入法遮挡、快速点按、后台返回、旋转、系统回收后恢复、删除持久化及升级时旧数据清除。原始截图、本机路径与设备信息仅保存在本地，不提交。

### 2026-10-15 电脑验证结果

完整 `clean / assembleDebug / testDebugUnitTest / lintDebug` 离线运行成功；41 项 JVM 测试通过，0 失败、0 错误；Lint 0 问题。合并 Manifest 仅有 AndroidX 内部签名权限，APK 类及资源检查未发现退休组件或旧字段模型。

导出的 Room schema 经桌面 SQLite 验证建表、重新打开、删除持久化、原金额时间恢复及事务回滚。此项是 schema 冒烟检查，不是 Android Room 运行时测试。本轮未安装或操作设备，真机验收仍待进行。
