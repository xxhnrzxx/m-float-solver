# 悬浮答题（m）

安卓悬浮窗做题助手：屏幕上浮一个小 m，点一下自动截屏，AI 解析直接浮在当前界面，不用切屏。

## 功能

- 悬浮在任意 App 之上，可拖动；长按关闭
- 点一下自动截屏 → 调用任意 OpenAI 兼容的识图模型，答案流式显示
- 三档外观：不透明 / 半透明 / 全透明（只剩一个白色小 m）
- 自动模式：每 2–60 秒检测画面，换题自动重新识别，同题不重复请求
- 大号「答案 B」角标 + 答案行标红；「只显示选项」精简模式
- 自动把模型输出的 LaTeX 转成纯文本（`\frac{2}{5}` → `2/5`）

## 环境

- 安卓 11+（API 30+）
- 只依赖 Android SDK，不引入第三方库

## 权限

| 权限 | 用途 |
| --- | --- |
| 悬浮窗（SYSTEM_ALERT_WINDOW） | 显示悬浮球和答案浮层 |
| 无障碍服务 | 截取当前屏幕（MediaProjection 的前置替代方案） |
| 网络 | 调用 AI 接口 |

## 构建

需要 JDK 17 与 Android SDK（build-tools、platform android-36）。

```powershell
powershell -ExecutionPolicy Bypass -File .\build.ps1
```

产物在 `build/app-debug.apk`。


## 配置

在 App 首页填三项后保存：

- 接口地址：如 `https://api.deepseek.com/v1`
- 模型名：如 `deepseek-v4.1-flash`（需支持识图）
- API Key：自己的 Key

模型必须支持图片输入，例如 `deepseek-v4.1-flash`、`qwen3-vl-plus`、`glm-4v-flash`。

## 说明

- 截图只发送到用户自己填写的接口，不经过任何第三方服务器。
- 自动模式只负责把答案显示出来，不会模拟点击选项或自动交卷。
- iOS 不支持悬浮截屏，仅安卓可用。

## 目录

```
src/com/trae/floatsolver/
  MainActivity.java   设置页
  BubbleService.java  悬浮球与答案浮层、自动模式
  ShotService.java    无障碍截图服务
  ChatClient.java     OpenAI 兼容接口调用（SSE 流式）
  Tex.java            LaTeX → 纯文本
  Prefs.java          本地配置
```
<img width="350" height="800" alt="9765d3d72d5d925032d197f33fce9acc" src="https://github.com/user-attachments/assets/4956b5dd-bddc-4da5-846e-3d82f419aee3" />
<img width="350" height="800" alt="41031a8e6b75fcd0be38f8330e013c85" src="https://github.com/user-attachments/assets/63ca2f65-07c7-4a09-b605-33a3dbb8c009" />
<img width=".50" height="800" alt="71e145768ed43d073652b95fe1f197b0" src="https://github.com/user-attachments/assets/25d30a8b-dc6c-43b5-910f-891ebf7cfab4" />

