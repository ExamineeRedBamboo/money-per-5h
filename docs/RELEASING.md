# 发布流程

源码采用 MIT 许可证，安装包通过 GitHub Releases 分发。源码发布和签名 APK 发布可以分开进行。

## 源码版本

发布前核对 README 版本与 `app/build.gradle.kts` 的版本一致，更新对应版本说明，并提交已审查的源文件。为该提交创建版本标签，例如 `v0.2.3`。GitHub Release 绑定该标签，避免源码与安装包版本不一致。

本地协作记录、账本、密钥、设备资料和构建缓存不进入仓库。构建使用 JDK 17、Android SDK 36 与仓库中的固定 Wrapper。

## 构建与验证

```powershell
.\gradlew.bat :app:assembleRelease :app:testDebugUnitTest :app:lintRelease
```

macOS / Linux 使用 `sh ./gradlew`。依赖已缓存时可追加 `--offline`。同时确认应用没有新增非必要权限、对外采集或上传入口。

当前工程未配置发布签名，输出为 `app/build/outputs/apk/release/app-release-unsigned.apk`。它需要签名后才能供用户安装，不能直接作为正式下载附件。

## 安装包签名

使用由维护者长期保管的发布密钥签名，并保留离线备份。后续更新沿用相同应用 ID 和发布签名；密钥、密码及机器专用配置不提交到仓库。

Android Build Tools 提供 `apksigner`。下面命令中的路径由维护者替换；使用交互式密码提示，避免将密码写入命令、文档或提交历史。

```text
apksigner sign --ks <release-keystore.jks> --out money-per-5h-0.2.3.apk app-release-unsigned.apk
apksigner verify --verbose money-per-5h-0.2.3.apk
```

Debug 包供开发使用。若设备已安装 Debug 包，切换到使用不同签名的正式包通常需要先卸载；卸载会清除本地账本。本项目尚无导出功能，因此切换前应明确告知用户数据影响。

## Release 附件

签名 APK 按 `money-per-5h-版本号.apk` 命名，随版本提供更新说明和 SHA-256 校验值。APK 不通过 Git 提交保存，也不上传未签名 APK、私钥、账本或设备截图。

首次公开可以先发布源码及版本说明，待签名包完成安装与更新验证后，再补充下载附件。不要把单元测试或编译成功写成全部设备兼容性均已验证。
