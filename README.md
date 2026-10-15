# Money per 5h

给钱包一个用量面板。Android 原生手动金额记录工具，以 5 小时和每周额度展示你主动记录的金额。

## 使用

1. 设置倍数：1x 对应 5 小时 ¥5、每周 ¥35，月预算 ¥150 仅用于设置时参考。每次确认后有效 30 天；修改倍数时保留已用金额和两个窗口的重置时间，按新限额重算剩余比例。
2. 点金额按钮累加，停手 3 秒后记下一笔；也可直接输入金额并确认。只保存金额与自动生成的保存时间。
3. 在“用量”查看剩余比例和重置时间，在“记录”查看每日热力图与全部明细。可删除明细，并在当前记录页撤销最近一次删除。

默认空账本，未设置或方案到期时暂停新增记录。已有记录可查看、删除及撤销；重新确认倍数后可继续记录。超额仍可记录。

本工具只反映你主动记录的金额，不假定记录完整。所有数据保存在本机，不需要账号、网络、通知访问、无障碍或其他特殊权限，不发送消费提醒。卸载会清除数据，系统云备份和设备迁移备份均已禁用。

## 开发

- Android 11 / API 30 及以上；compile/target SDK 36。
- Kotlin、Jetpack Compose / Material 3、Room；JDK 17，固定 Gradle Wrapper。
- 当前版本：0.2.2。首次从旧原型升级到手动版时，按项目所有者确认的决定清除此前原型的本地账本，从空账本开始；手动版自身的更新及后续启动保留记录、方案和草稿。

配置 `JAVA_HOME`（JDK 17）和 `ANDROID_HOME`（Android SDK）后执行：

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

macOS / Linux 使用 `sh ./gradlew`。依赖已缓存时可追加 `--offline`。

Debug 安装包输出至 `app/build/outputs/apk/debug/app-debug.apk`。构建与单元测试不替代真机界面、输入法及进程恢复验收，检查范围见技术文档。

## 文档

- [产品规格](docs/PRODUCT.md)：交互、日期、窗口、方案和删除撤销规则。
- [技术说明](docs/TECHNICAL.md)：数据、生命周期与验证方法。
- [贡献指南](CONTRIBUTING.md)：开发与提交约定。

开源许可证尚未选定，选定后补充 `LICENSE`。
