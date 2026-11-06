# VoiceReader（Android 原生 Java）

## GitHub 云端编译
1. 在 GitHub 新建一个空仓库。
2. 上传本项目全部文件（不要只上传 ZIP）。
3. 打开仓库的 Actions，运行 `Android APK` 工作流。
4. 工作流成功后，在运行记录的 Artifacts 下载 `VoiceReader-debug-apk`。

## 当前版本
- SAF 文件夹/文件选择
- TXT / CSV / LOG 最后一个非空行读取
- 自动选择文件夹内修改时间最新的文本文件
- Android 系统中文 TTS
- 自动轮询、前台服务、通知栏暂停
- 开机接收器代码（默认关闭自动启动）

## 注意
- 本机 TTS 是否离线、可用哪些中文音色，取决于手机安装的 TTS 引擎和语音数据。
- 当前版本尚未接入 Edge TTS 网络协议。需要你原来 Edge TTS Flask 服务的实际地址和接口格式，才能可靠接入；界面说明不会把它冒充成已实现。
- 目前自动监控会在文件最后一行发生变化时朗读最新行；首次启动会朗读当前最新行。
- Android 13+ 需要允许通知权限，Android 14+ 需要允许前台媒体播放服务。
