# NUFE-Course-Schedule-App (南京财经大学教务课表)

[中文](#中文) | [English](#english)

---

## 中文

一款专为**南京财经大学 (NUFE)** 深度定制的优雅、现代、高性能 **Android 个人课表客户端**。界面遵循 **Google Material Design 3** 规范设计，并搭载了极速安全的 Native-Hybrid 强智教务系统全自动解析导入向导。

### 🌟 核心特色
- **🎨 谷歌 Material 3 极简视觉**：采用清爽的极简背景，搭配大圆角与柔和纸张感卡片阴影。课程卡片采用 Google Calendar 经典的**莫兰迪淡雅粉彩配色**，舒适护眼。
- **📅 精准 7 课时 CSS Grid 网格**：精准细分出独立的第 5 节与第 10 节。结合动态 `grid-row-span`（跨行）算法，跨课时长的课程（如连上 3 节的课）自动占满格子，并在空白位置智能补齐虚线占位格，绝无重叠错位。
- **⚡ 100% 绝对精准的教务系统内存解析**：突破了传统脆弱的 HTML 表格 DOM 抓取，通过**跨 iframe 树形递归算法**，直接读取网页深层内存中的官方数据变量 `window.courseTableDataList`，数据源 100% 绝对精准！
- **🕵️‍♂️ 纯原生 Java 底部导航浮栏**：引导条完全使用 **Android 原生控件**开发，彻底消灭了双指捏合缩放（Pinch-to-Zoom）导致的引导栏变小缩水 bug！点击响应极度灵敏。
- **🔍 捏合缩放支持 (Pinch-to-Zoom)**：WebView 底层完美支持手势缩放，方便在手机上无障碍拖动和放大 PC 版教务系统登录窗口。
- **🔒 证书校验放行 (SSL Bypass)**：放行南财教务常见自签名证书报错，杜绝白屏发生。
- **📱 手机一屏看全 & 居中详情弹窗**：网格高度按屏幕比例自适应拉伸锁定，彻底消灭全局滚动条。点击微缩课程卡片在屏幕正中央浮现毛玻璃详情面板，点击外侧空白即可轻盈关闭。

### 🚀 编译与真机部署

#### 1. 单纯编译 Debug APK
在项目根目录下通过 PowerShell 运行编译脚本：
```powershell
powershell -ExecutionPolicy Bypass -File scripts/build-debug.ps1
```
编译成功后，产物 APK 将输出至：
`app/build/outputs/apk/debug/app-debug.apk`

#### 2. 一键编译并通过 ADB 自动安装至手机
确保您的安卓手机已开启“USB 调试”，连接电脑后运行：
```powershell
powershell -ExecutionPolicy Bypass -File scripts/install-debug.ps1
```

---

## English

An elegant, modern, and high-performance **Android Timetable Client** customized for **Nanjing University of Finance and Economics (NUFE)**. Designed under **Google Material Design 3** specifications and powered by a secure, native-hybrid automatic scraper.

### 🌟 Key Features
- **🎨 Premium Material Design 3 UI**: Clean slate layout with soft Morandi/pastel color themes for courses. Features card elevations and full system dark/light integration.
- **📅 Accurate 7-Period CSS Grid**: Precisely splits daily sections (including separate periods 5 and 10). Dynamic grid row spanning automatically positions courses stretching across multiple periods without overlapping.
- **⚡ 100% Accurate Educational System Scraper**: Bypasses fragile DOM scraping by directly reading the official `window.courseTableDataList` variables from page memory. Utilizes a recursive iframe tree-walking algorithm to securely retrieve schedules.
- **🕵️‍♂️ Native Java Bridge Guide Banner**: Built entirely with native Android controls. It is completely immune to WebView pinch-to-zoom viewport scaling and provides a smooth one-click automatic parse and import wizard.
- **🔍 Full Pinch-to-Zoom Support**: Enables native dual-finger pinch zoom controls inside the WebView, allowing easy navigation on desktop-optimized login portals.
- **🔒 SSL Certificate Bypass**: Safely bypasses self-signed or expired HTTPS certificate blocks, ensuring NUFE's portals load without blank screen errors.
- **📱 One-Page Fits All**: Automatically scales grid row heights to fit the phone's remaining vertical space, completely eliminating vertical scrollbars. Tapping any course cell floats a beautiful M3 details dialog.

### 🚀 Build & Deploy

#### Prerequisites
- Gradle & JDK 17+ (configured automatically inside `.tools/`)

#### 1. Compile Debug APK
Run the compile script in PowerShell:
```powershell
powershell -ExecutionPolicy Bypass -File scripts/build-debug.ps1
```
The output APK will be saved at:
`app/build/outputs/apk/debug/app-debug.apk`

#### 2. Auto-Install on Phone via ADB
Connect your phone with USB Debugging enabled, then run:
```powershell
powershell -ExecutionPolicy Bypass -File scripts/install-debug.ps1
```
