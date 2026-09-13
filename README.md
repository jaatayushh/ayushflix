<p align="center">
  <img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" alt="Ayushflix Logo" width="130" height="130" style="border-radius: 28px; box-shadow: 0 8px 24px rgba(229, 9, 20, 0.4);" />
</p>

<h1 align="center">Ayushflix</h1>

<p align="center">
  <strong>The Ultimate Dedicated, Ad-Free Streaming Experience for Android, Android TV & Windows (WSA).</strong>
</p>

<p align="center">
  <a href="https://github.com/jaatayushh/ayushflix/releases/latest"><img src="https://img.shields.io/github/v/release/jaatayushh/ayushflix?style=for-the-badge&color=E50914&label=Latest%20Release" alt="Release" /></a>
  <a href="https://github.com/jaatayushh/ayushflix/releases"><img src="https://img.shields.io/github/downloads/jaatayushh/ayushflix/total?style=for-the-badge&color=007ACC&label=Downloads" alt="Downloads" /></a>
  <a href="https://github.com/jaatayushh/ayushflix/blob/main/LICENSE"><img src="https://img.shields.io/badge/License-GPL%20v3-22c55e?style=for-the-badge" alt="License" /></a>
  <a href="https://github.com/jaatayushh/ayushflix"><img src="https://img.shields.io/badge/Platform-Android%20%7C%20TV%20%7C%20Windows-8b5cf6?style=for-the-badge" alt="Platforms" /></a>
  <a href="https://github.com/jaatayushh/ayushflix"><img src="https://img.shields.io/badge/Ads-0%25%20Guaranteed-ff7b00?style=for-the-badge" alt="Zero Ads" /></a>
</p>

---

## 🌟 What is Ayushflix?

**Ayushflix** is a dedicated media streaming application built specifically for **Android Smartphones, Tablets, Android TV / Google TV / FireStick, and Windows PC (via WSA)**.

Unlike generic media centers that require hunting down third-party repository URLs, installing extensions, and dealing with broken links, **Ayushflix comes 100% pre-configured out-of-the-box**. It provides high-performance, ad-free streaming with smart multi-audio dub prioritization and built-in DNS-over-HTTPS (DoH) to bypass ISP throttling and blocks.

---

## ⚡ Direct Download (v1.0.6)

| Platform | Format | Size | Architecture | Direct Download |
| :--- | :---: | :---: | :---: | :---: |
| 📱 **Android Smartphone / Tablet** | `.apk` | **~50 MB** | `arm64-v8a`, `armeabi-v7a`, `x86_64` | [**Download Ayushflix-v1.0.6.apk**](https://github.com/jaatayushh/ayushflix/releases/download/v1.0.6/Ayushflix-v1.0.6.apk) |
| 📺 **Android TV / Google TV / FireStick** | `.apk` | **~50 MB** | Native 10-Foot Leanback UI | [**Download Ayushflix-v1.0.6.apk**](https://github.com/jaatayushh/ayushflix/releases/download/v1.0.6/Ayushflix-v1.0.6.apk) |
| 🖥️ **Windows 10 / 11 (WSA / Emulator)** | `.apk` | **~50 MB** | Native Desktop Window | [**Download Ayushflix-v1.0.6.apk**](https://github.com/jaatayushh/ayushflix/releases/download/v1.0.6/Ayushflix-v1.0.6.apk) |

---

## 🖥️ Complete Windows Setup Guide (WSA & True Fullscreen)

You can run Ayushflix natively on **Windows 10 and Windows 11** without heavy emulators like BlueStacks using **Windows Subsystem for Android (WSA)**.

### Step 1: Enable Virtual Machine Platform
Open **PowerShell as Administrator** (Right-click Start button -> *Terminal / PowerShell (Admin)*) and run:
```powershell
dism.exe /online /enable-feature /featurename:VirtualMachinePlatform /all /norestart
```
*(Restart your PC if prompted).*

### Step 2: Install WSA
1. Download a WSA build with Magisk & GApps (e.g. from GitHub or WSABuilds).
2. Extract the archive (e.g. to `C:\WSA`).
3. Open **PowerShell as Administrator** inside the folder and run:
   ```powershell
   .\Install.ps1
   ```
   *(Or simply right-click `Run.bat` inside the folder and click **Run as administrator**).*

### Step 3: Enable Developer Mode in WSA
1. Open **Windows Subsystem for Android Settings** from your Windows Start Menu.
2. Go to **Advanced settings** (or **Developer** tab).
3. Turn **Developer mode** to **ON**. Note the IP address and port (usually `127.0.0.1:58526`).

### Step 4: Install Ayushflix on Windows

**Option A: 1-Liner PowerShell Command (Fastest)**
Open PowerShell and run:
```powershell
Invoke-WebRequest -Uri "https://github.com/jaatayushh/ayushflix/releases/download/v1.0.6/Ayushflix-v1.0.6.apk" -OutFile "$env:TEMP\Ayushflix.apk"
adb connect 127.0.0.1:58526
adb install -r "$env:TEMP\Ayushflix.apk"
```

**Option B: 1-Click GUI via WSA-Pacman**
1. Download and install **[WSA-Pacman](https://github.com/alesimula/wsa_pacman/releases/latest)**.
2. Download [**Ayushflix-v1.0.6.apk**](https://github.com/jaatayushh/ayushflix/releases/download/v1.0.6/Ayushflix-v1.0.6.apk).
3. Double-click the APK and click **Install**.

Ayushflix will now appear directly in your Windows Start Menu and Taskbar!

---

### 📺 How to Enter 100% True Borderless Fullscreen on Windows

> [!TIP]
> **Hiding the Windows Title Bar (App Name, Minimize, Maximize, Close Buttons):**
> * When Ayushflix is open on your PC, press **`F11`** (or **`Fn + F11`** on laptops) on your keyboard.
> * This instantly toggles **True Borderless Fullscreen**—the top title bar and Windows taskbar disappear completely, giving you a full 100% cinema display.
> * To exit fullscreen or reveal the window controls, press **`F11`** again or move your mouse to the top edge of your monitor.

---

## 💎 Why Ayushflix?

| Feature | Ayushflix 🎬 | Generic Media Apps ❌ |
| :--- | :---: | :---: |
| **Ads & Popups** | **0% (Completely Stripped)** | Invasive popups & redirects |
| **Setup Process** | **Instant (Zero Config)** | Requires external repo links & setups |
| **ISP Bypass** | **Built-in Google DoH (No VPN needed)** | Blocked by Indian ISPs (Jio/Airtel) |
| **Default Audio** | **Smart Hindi Dub Auto-Selection** | Random / Non-Hindi default |
| **Fake Video Traps** | **Filtered & Decrypted** | Plays fake update warning loops |
| **Android TV Mode** | **Native D-Pad Remote Navigation** | Broken mouse-only navigation |
| **Desktop / WSA** | **Native Windows Window + F11 Fullscreen** | Unsupported / Stretched |
| **App Performance** | **Butter-smooth 60fps (Release Build)** | Sluggish with debug bloat |
| **Privacy** | **Zero Telemetry / No Account Needed** | Trackers & mandatory sign-ups |

---

## ✨ Features

- **⚡ Instant 1080p FHD Playback**: Stream in crisp 1080p Full HD, 720p HD, and auto-adaptive resolutions.
- **🛡️ Built-in ISP Block Bypass**: Native DNS-over-HTTPS (DoH) integration prevents ISP DNS hijacking and timeout errors.
- **🎧 Intelligent Hindi Dub Auto-Selector**: Multi-language titles automatically start in **Hindi** audio by default.
- **📺 Android TV & Google TV Optimized**: Full Leanback 10-foot UI with remote D-pad navigation, continue watching row, and TV clock.
- **🖥️ Windows Desktop Native**: Fluid mouse, keyboard, and resize support with borderless `F11` cinema view.
- **🎨 Modern Player**: Built on Google's Media3 & ExoPlayer engine with gesture controls, playback speed controls, and Picture-in-Picture (PiP).
- **💬 Subtitles On Demand**: Automatic multi-language subtitle fetching with customizable styling, sizing, and delay synchronization.
- **⬇️ Offline Download Engine**: Built-in multi-threaded downloader with resume capability for offline viewing.
- **🔒 Private & Open**: No tracking, no telemetry, and no registration required.

---

## 📲 Mobile & TV Installation Guide

### 📱 Android Smartphone / Tablet
1. Download [**Ayushflix-v1.0.6.apk**](https://github.com/jaatayushh/ayushflix/releases/download/v1.0.6/Ayushflix-v1.0.6.apk).
2. Tap the downloaded `.apk` file.
3. If prompted, toggle **Allow from this source** in your device settings.
4. Tap **Install** and launch **Ayushflix**!

### 📺 Android TV, Google TV & Amazon FireStick
1. **Method A (Send Files to TV)**:
   - Install *Send Files to TV* on both your phone/PC and your Android TV.
   - Send `Ayushflix-v1.0.6.apk` to your TV and install using a file manager (e.g., *File Commander*).
2. **Method B (USB Drive)**:
   - Copy `Ayushflix-v1.0.6.apk` onto a FAT32/NTFS formatted USB flash drive.
   - Plug into your TV, open a file manager, and select the APK to install.
3. **Method C (Downloader App)**:
   - Open the *Downloader* app on FireStick / Android TV.
   - Enter the direct release URL: `https://github.com/jaatayushh/ayushflix/releases/download/v1.0.6/Ayushflix-v1.0.6.apk` to download and install.

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
   app/build/outputs/apk/stable/release/Ayushflix-v1.0.6.apk
   ```

---

## ⚖️ Legal Disclaimer & Non-Hosting Notice

> [!IMPORTANT]
> **Ayushflix does NOT host, upload, store, archive, or broadcast any media, videos, audio, or copyrighted content.**

### 1. Pure Client-Side Indexer & Aggregator
Ayushflix operates strictly as a specialized client-side user interface and search indexer—functionally identical to a web browser (e.g., Google Chrome, Firefox) or search engine (e.g., Google, Bing). It merely queries, parses, and plays publicly accessible links that are already indexed and openly hosted on third-party web servers across the internet.

### 2. Zero Hosting & Storage Policy
- **No Media Servers**: Ayushflix does not own, manage, maintain, or operate any media servers, CDNs, databases, or cloud storage facilities containing video files.
- **No Video Uploading**: Neither the creators, contributors, nor the software itself ever upload or distribute copyrighted material.
- **Stateless Hyperlink Resolution**: The software only resolves public hyperlinks provided by external third-party APIs. Under established international internet and digital copyright jurisprudence (including EU and US rulings regarding hyperlinks and web indexing), linking to publicly accessible third-party web content does not constitute copyright infringement or illicit distribution.

### 3. Third-Party Liability & DMCA Inquiries
- Ayushflix has **no affiliation, partnership, sponsorship, or association** with any third-party websites, providers, or stream hosts accessed through the app.
- Because Ayushflix does not host any media, the developers have neither the technical capability nor the legal authority to remove content hosted on independent external servers.
- Any copyright owners seeking removal or takedown of specific media files must contact the actual web host or server provider physically hosting the content.

### 4. Non-Commercial & Educational Purpose
- Ayushflix is 100% free, non-commercial, and open-source software distributed under the **GNU General Public License v3.0**.
- There are **no advertisements, no paid subscriptions, no paywalls, and zero monetization** of any kind.
- The project is created solely for personal research, educational study of Android Media3/ExoPlayer client architecture, and software development experimentation.

### 5. End-User Compliance
Users are solely responsible for ensuring that their use of this software complies with all applicable intellectual property, copyright, and digital communications regulations in their respective country or jurisdiction.

---

## 📄 License

This project is licensed under the [GNU General Public License v3.0](LICENSE).

<p align="center">
  Crafted with ❤️ for seamless, uninterrupted entertainment.
</p>
