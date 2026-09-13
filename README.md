<p align="center">
  <img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" alt="Ayushflix Logo" width="130" height="130" style="border-radius: 28px; box-shadow: 0 8px 24px rgba(229, 9, 20, 0.4);" />
</p>

<h1 align="center">Ayushflix</h1>

<p align="center">
  <strong>The Ultimate Dedicated, Ad-Free Streaming Experience for Android & Android TV.</strong>
</p>

<p align="center">
  <a href="https://github.com/jaatayushh/ayushflix/releases/latest"><img src="https://img.shields.io/github/v/release/jaatayushh/ayushflix?style=for-the-badge&color=E50914&label=Latest%20Release" alt="Release" /></a>
  <a href="https://github.com/jaatayushh/ayushflix/releases"><img src="https://img.shields.io/github/downloads/jaatayushh/ayushflix/total?style=for-the-badge&color=007ACC&label=Downloads" alt="Downloads" /></a>
  <a href="https://github.com/jaatayushh/ayushflix/blob/main/LICENSE"><img src="https://img.shields.io/badge/License-GPL%20v3-22c55e?style=for-the-badge" alt="License" /></a>
  <a href="https://github.com/jaatayushh/ayushflix"><img src="https://img.shields.io/badge/Platform-Android%20%7C%20Android%20TV-8b5cf6?style=for-the-badge" alt="Platforms" /></a>
  <a href="https://github.com/jaatayushh/ayushflix"><img src="https://img.shields.io/badge/Ads-0%25%20Guaranteed-ff7b00?style=for-the-badge" alt="Zero Ads" /></a>
</p>

---

## 🌟 What is Ayushflix?

**Ayushflix** is a dedicated media streaming application built specifically for **Android Smartphones, Tablets, and Android TV / Google TV / FireStick devices**.

Unlike generic media centers that require hunting down third-party repository URLs, installing extensions, and dealing with broken links, **Ayushflix comes 100% pre-configured out-of-the-box**. It provides high-performance, ad-free streaming with smart multi-audio dub prioritization.

---

## ⚡ Direct Download (v1.0.4)

| Platform | Format | Size | Architecture | Direct Download |
| :--- | :---: | :---: | :---: | :---: |
| 📱 **Android Smartphone / Tablet** | `.apk` | **~52 MB** | `arm64-v8a`, `armeabi-v7a` | [**Download Ayushflix-v1.0.4.apk**](https://github.com/jaatayushh/ayushflix/releases/download/v1.0.4/Ayushflix-v1.0.4.apk) |
| 📺 **Android TV / Google TV / FireStick** | `.apk` | **~52 MB** | Native 10-Foot Leanback UI | [**Download Ayushflix-v1.0.4.apk**](https://github.com/jaatayushh/ayushflix/releases/download/v1.0.4/Ayushflix-v1.0.4.apk) |

---

## 💎 Why Ayushflix?

| Feature | Ayushflix 🎬 | Generic Media Apps ❌ |
| :--- | :---: | :---: |
| **Ads & Popups** | **0% (Completely Stripped)** | Invasive popups & redirects |
| **Setup Process** | **Instant (Zero Config)** | Requires external repo links & setups |
| **Default Audio** | **Smart Hindi Dub Auto-Selection** | Random / Tamil / Non-Hindi default |
| **Fake Video Traps** | **Filtered & Decrypted** | Plays fake update warning loops |
| **Android TV Mode** | **Native D-Pad Remote Navigation** | Broken mouse-only navigation |
| **App Performance** | **Butter-smooth 60fps (Release Build)** | Sluggish with debug bloat |
| **Privacy** | **Zero Telemetry / No Account Needed** | Trackers & mandatory sign-ups |

---

## ✨ Features

- **⚡ Instant 1080p FHD Playback**: Stream in crisp 1080p Full HD, 720p HD, and auto-adaptive resolutions.
- **🎧 Intelligent Hindi Dub Auto-Selector**: Movies and series with multi-language audio (Hindi, English, Tamil, Telugu) automatically start in **Hindi** without needing manual audio switching.
- **🛡️ Authentic Stream Extraction**: Automatically decodes signed CloudFront policies and bypasses decoy update prompts and dummy video traps.
- **📺 Android TV & Google TV Optimized**: Full Leanback 10-foot UI with remote D-pad navigation, continue watching row, and TV clock.
- **🎨 Modern Player**: Built on Google's Media3 & ExoPlayer engine with gesture controls (brightness/volume swipes), playback speed controls, and Picture-in-Picture (PiP).
- **💬 Subtitles On Demand**: Automatic multi-language subtitle fetching with customizable styling, sizing, and delay synchronization.
- **⬇️ Offline Download Engine**: Built-in multi-threaded downloader with resume capability for offline viewing.
- **🔒 Private & Open**: No tracking, no telemetry, and no registration required.

---

## 📲 Installation & Sideloading Guide

### 📱 Android Smartphone / Tablet
1. Download [**Ayushflix-v1.0.4.apk**](https://github.com/jaatayushh/ayushflix/releases/download/v1.0.4/Ayushflix-v1.0.4.apk).
2. Tap the downloaded `.apk` file.
3. If prompted, toggle **Allow from this source** in your device settings.
4. Tap **Install** and launch **Ayushflix**!

### 📺 Android TV, Google TV & Amazon FireStick
1. **Method A (Send Files to TV)**:
   - Install *Send Files to TV* on both your phone/PC and your Android TV.
   - Send `Ayushflix-v1.0.4.apk` to your TV and install using a file manager (e.g., *File Commander*).
2. **Method B (USB Drive)**:
   - Copy `Ayushflix-v1.0.4.apk` onto a FAT32/NTFS formatted USB flash drive.
   - Plug into your TV, open a file manager, and select the APK to install.
3. **Method C (Downloader App)**:
   - Open the *Downloader* app on FireStick / Android TV.
   - Enter the direct release URL: `https://github.com/jaatayushh/ayushflix/releases/download/v1.0.4/Ayushflix-v1.0.4.apk` to download and install.

---

## 📂 Project Architecture

```
Ayushflix/
├── app/                              # Android / Android TV Application
│   ├── src/main/java/com/lagradost/cloudstream3/
│   │   ├── builtin/
│   │   │   └── MovieBoxProvider.kt   # Core streaming engine (Hindi prioritization & ad-free)
│   │   ├── ui/
│   │   │   ├── home/                 # Netflix-style home screen & category rails
│   │   │   ├── search/               # Real-time search query handler
│   │   │   ├── player/               # Media3 / ExoPlayer hardware video engine
│   │   │   └── result/               # Metadata, seasons, and episode selection
│   │   └── MainActivity.kt           # Application bootstrap & lifecycle
│   └── src/main/res/                 # Drawables, mipmaps, Leanback TV layouts, strings
├── library/                          # Common networking, models, and extractor APIs
└── gradle/                           # Dependency version catalog & Gradle configuration
```

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
2. Build the production signed release APK:
   ```bash
   ./gradlew assembleStableRelease
   ```
3. The compiled APK will be located at:
   ```
   app/build/outputs/apk/stable/release/app-stable-release.apk
   ```

---

## ⚖️ Legal Disclaimer

Ayushflix is an open-source project created strictly for educational, personal research, and software development purposes. 

Ayushflix does **not** host, store, upload, or manage any media files, video streams, or copyrighted material on its own servers. All content and metadata are fetched dynamically via public third-party APIs. Any copyright inquiries or complaints regarding hosted streams should be addressed directly to the third-party providers hosting the respective files.

---

## 📄 License

This project is licensed under the [GNU General Public License v3.0](LICENSE).

<p align="center">
  Crafted with ❤️ for seamless, uninterrupted entertainment.
</p>
