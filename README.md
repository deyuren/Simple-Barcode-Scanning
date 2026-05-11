# 条形码扫描 - Android App

## 功能说明
- 📸 实时相机预览，自动识别条形码 / 二维码
- ✅ 识别成功后震动提示 + 结果展示
- 📋 一键复制识别结果到剪贴板
- 🔄 支持重新扫描

## 支持的码制
支持所有主流码制，包括：
- QR Code、Data Matrix、Aztec
- EAN-13、EAN-8、UPC-A、UPC-E
- Code 128、Code 39、Code 93
- ITF、Codabar、PDF417

## 编译步骤

### 方式一：Android Studio（推荐）
1. 安装 [Android Studio](https://developer.android.com/studio)
2. 打开 Android Studio → File → Open → 选择本文件夹
3. 等待 Gradle 同步完成
4. 连接安卓手机（开启开发者选项和USB调试）
5. 点击 Run ▶ 即可安装运行

### 方式二：命令行
```bash
# 确保已安装 JDK 17+ 和 Android SDK
./gradlew assembleDebug
# APK 生成路径：app/build/outputs/apk/debug/app-debug.apk
```

## 最低系统要求
- Android 7.0 (API 24) 及以上
- 需要相机权限

## 项目结构
```
BarcodeScanner/
├── app/src/main/
│   ├── java/com/example/barcodescanner/
│   │   └── MainActivity.java      # 主界面逻辑
│   ├── res/
│   │   ├── layout/activity_main.xml   # UI 布局
│   │   ├── drawable/                  # 扫描框、卡片样式
│   │   └── values/                    # 颜色、字符串、主题
│   └── AndroidManifest.xml
└── app/build.gradle               # 依赖配置
```

## 技术栈
- **CameraX** - 相机预览与图像分析
- **ML Kit Barcode Scanning** - Google 条形码识别引擎
- **Material Design 3** - UI 组件库
