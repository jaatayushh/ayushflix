# Ayushflix 🎬

<p align="center">
  <img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" alt="Ayushflix Logo" width="120" height="120" style="border-radius: 24px;" />
</p>

<p align="center">
  <strong>The Ultimate Dedicated, Ad-Free Streaming Experience for Android & Android TV.</strong>
</p>

<p align="center">
  <a href="https://github.com/jaatayushh/ayushflix/releases"><img src="https://img.shields.io/github/v/release/jaatayushh/ayushflix?style=for-the-badge&color=E50914" alt="Release" /></a>
  <a href="https://github.com/jaatayushh/ayushflix"><img src="https://img.shields.io/badge/Platform-Android%20%7C%20Android%20TV-007ACC?style=for-the-badge" alt="Platform" /></a>
  <a href="https://github.com/jaatayushh/ayushflix/blob/main/LICENSE"><img src="https://img.shields.io/badge/License-GPL%20v3-green?style=for-the-badge" alt="License" /></a>
</p>

---

## 🌟 Overview

**Ayushflix** is an Android application designed for entertainment streaming. Unlike generic media centers that require searching for, installing, and updating external extension repositories, **Ayushflix comes pre-configured out-of-the-box** with a dedicated, high-performance streaming engine.

### 🛡️ Why Ayushflix?
- **Zero Third-Party Redirects & Ads**: Stripped out all third-party ad links, unwanted browser redirect prompts, and external popups. Clean, instant playback without interruptions.
- **Default Hindi Audio**: Automatically selects Hindi dub / audio track when hitting play, without having to manually switch from Tamil or other audio tracks.
- **Dedicated Single-Provider Architecture**: Pre-installed with the fast MovieBox provider. No setup wizards, no repository URLs, and no manual plugin management required.
- **Clean, Clutter-Free Interface**: All provider selectors, source switchers, and plugin manager settings have been replaced with a streamlined, modern UI.
- **Cross-Platform Ready**: Tailored layouts for standard Android smartphones, tablets, and native **Android TV / Google TV** with 10-foot remote D-pad navigation.

---

## ✨ Features

- **⚡ Instant Streaming**: High-quality video streams (1080p FHD, 720p HD, 480p SD, and 360p).
- **🌐 Multi-Audio & Dubs (Default Hindi)**: Seamless support for multi-audio tracks with automatic Hindi default audio selection, plus English, Tamil, Telugu, and more.
- **💬 Subtitles On Demand**: Automatic multi-language subtitle fetching and formatting with customizable fonts, colors, and timing offsets.
- **⬇️ Offline Downloads**: Built-in multi-threaded chunk download engine with background queue support and download resume.
- **📺 Android TV Optimized**: Full Leanback interface with remote control support, continue watching row, and TV clock.
- **🎨 Modern Player**: ExoPlayer & Media3 backed video player with gesture controls (brightness/volume swipes), playback speed controls, and picture-in-picture (PiP).
- **🔒 Privacy First**: No telemetry, no tracker SDKs, and no account requirements.

---

## 📲 Download & Installation

### Option 1: Direct APK Download
1. Navigate to the [Releases](https://github.com/jaatayushh/ayushflix/releases) section or directly download [Ayushflix-v1.0.4.apk](https://github.com/jaatayushh/ayushflix/releases/download/v1.0.4/Ayushflix-v1.0.4.apk).
2. Open the downloaded file on your Android device and allow "Install from Unknown Sources" if prompted.
3. Launch **Ayushflix** and enjoy!

### Option 2: Android TV Sideloading
1. Download `Ayushflix-v1.0.4.apk` onto a USB flash drive or transfer it via apps like *Send Files to TV*.
2. Open a file manager app on your TV (e.g., *File Commander*).
3. Select and install the APK.

---

## 🛠️ Building From Source

### Prerequisites
- **JDK 17 or higher** (JDK 21 / OpenJDK 25 recommended)
- **Android SDK** (API level 35 / 36 / 37)
- **Gradle 9.x** (Included via Gradle Wrapper)

### Steps
1. Clone the repository:
   ```bash
   git clone https://github.com/jaatayushh/ayushflix.git
   cd ayushflix
   ```
2. Configure your Android SDK location in `local.properties`:
   ```properties
   sdk.dir=C:\\Users\\<YourUsername>\\AppData\\Local\\Android\\Sdk
   ```
3. Build the debug APK:
   ```bash
   ./gradlew assembleStableDebug
   ```
4. Find the compiled APK in:
   ```
   app/build/outputs/apk/stable/debug/app-stable-debug.apk
   ```

---

## 📂 Project Architecture

```
Ayushflix/
├── app/
│   ├── src/main/java/com/lagradost/cloudstream3/
│   │   ├── builtin/
│   │   │   └── MovieBoxProvider.kt   # Core streaming engine (clean, ad-free)
│   │   ├── ui/
│   │   │   ├── home/                 # Main browsing interface
│   │   │   ├── search/               # Fast query search
│   │   │   ├── player/               # Media3 / ExoPlayer video playback
│   │   │   └── result/               # Media details & episode selector
│   │   └── MainActivity.kt           # Root activity & initialization
│   └── src/main/res/                 # Icons, drawables, layouts, localized strings
├── library/                          # Common models, extractor APIs, networking
└── gradle/                           # Dependency version catalog
```

---

## ⚖️ Disclaimer

This project is created for educational and personal research purposes. Ayushflix does not host or distribute any video files on its servers. All media streams and metadata are provided by third-party services over public APIs.

---

<p align="center">Made with ❤️ for uninterrupted entertainment.</p>
