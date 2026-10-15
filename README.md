# Money per 5h

一款借鉴 Codex 界面风格的 Android 原生极简记账软件，把手动记录的金额呈现为 5 小时和每周用量。

## 使用

1. 设置倍数：1x 对应的参考月预算为 ¥150。每次确认设置后，方案有效 30 天；修改倍数时保留已用金额和两个窗口的重置时间，按新限额重算剩余比例。
2. 点金额按钮累加，停手 3 秒后记下一笔；也可直接输入金额并确认。只保存金额与自动生成的保存时间。
3. 在“用量”查看剩余比例和重置时间，在“记录”查看每日热力图与全部明细。可删除明细，并在当前记录页撤销最近一次删除。

默认空账本，未设置或方案到期时暂停新增记录。已有记录可查看、删除及撤销；重新确认倍数后可继续记录。超额仍可记录。

本工具只反映你主动记录的金额，不假定记录完整。所有数据保存在本机，不需要账号、网络、通知访问、无障碍或其他特殊权限，不发送消费提醒。卸载会清除数据，系统云备份和设备迁移备份均已禁用。

## 下载与版本

当前版本为 **0.2.3**，支持 Android 11 及以上。

从 [v0.2.3 Release](https://github.com/ExamineeRedBamboo/money-per-5h/releases/tag/v0.2.3) 下载正式签名的安装包，或直接下载 [APK](https://github.com/ExamineeRedBamboo/money-per-5h/releases/download/v0.2.3/money-per-5h-0.2.3.apk) 与 [SHA-256 校验文件](https://github.com/ExamineeRedBamboo/money-per-5h/releases/download/v0.2.3/SHA256SUMS.txt)。APK 不存入源码历史。

开发用 Debug 包与正式发布包使用不同签名，不能直接互相覆盖安装。若已安装开发包，卸载后才能安装正式包；卸载会清除本地账本，目前没有导出功能。后续正式版沿用同一发布签名。

## 开发

- Android 11 / API 30 及以上；compile/target SDK 36。
- Kotlin、Jetpack Compose / Material 3、Room；JDK 17，固定 Gradle Wrapper。
- 当前版本：0.2.3。首次从旧原型升级到手动版时会清除旧原型账本；手动版自身的更新及后续启动保留记录、方案和草稿。

配置 `JAVA_HOME`（JDK 17）和 `ANDROID_HOME`（Android SDK）后执行：

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

macOS / Linux 使用 `sh ./gradlew`。依赖已缓存时可追加 `--offline`。

Debug 安装包输出至 `app/build/outputs/apk/debug/app-debug.apk`。构建与单元测试不替代真机界面、输入法及进程恢复验收，检查范围见技术文档。

## 文档

- [产品规格](docs/PRODUCT.md)：交互、日期、窗口、方案和删除撤销规则。
- [技术说明](docs/TECHNICAL.md)：数据、生命周期与验证方法。
- [发布说明](docs/releases/0.2.3.md)：0.2.3 的功能与验证范围。
- [发布流程](docs/RELEASING.md)：源码版本、APK 签名及附件分发。
- [贡献指南](CONTRIBUTING.md)：开发与提交约定。

本项目原创代码和文档采用 [MIT 许可证](LICENSE)，版权署名为 Redbamboo。依赖及随附第三方工具保留各自许可证；Gradle Wrapper JAR 内含 Apache-2.0 许可证。

本项目有意模仿 Codex 的界面风格，并由 Codex 辅助开发；与 OpenAI 无隶属关系，并非官方产品。
