# iappyxOS

**Generate real Android apps on your device — no server, no app store, no code.**

Describe what you want to your favorite AI, and iappyxOS turns it into a real installed APK with its own launcher icon. Building, signing, and installing all happen on-device. No cloud. No subscription. No coding required.

## Demo

[![Watch the demo](https://img.youtube.com/vi/VWvjfr7BCnY/maxresdefault.jpg)](https://youtu.be/VWvjfr7BCnY)

## Screenshots

<p align="center">
  <img src="docs/screenshot_1_myapps.png" width="24%" />
  <img src="docs/screenshot_2_create.png" width="24%" />
  <img src="docs/screenshot_3_demos.png" width="24%" />
  <img src="docs/screenshot_4_app.png" width="24%" />
</p>
<p align="center">
  <img src="docs/screenshot_5_icon.png" width="24%" />
  <img src="docs/screenshot_6_preview.png" width="24%" />
</p>

*My Apps • Create • 57 Demos • Generated Radio App • Icon Editor • Preview with Console*

## What it does

- **AI-generated App** — describe your app in plain language, let an AI generate the code, preview it, and build
- **Website as App** — turn any website into a lightweight standalone app (1MB, no bridges, sandboxed)
- **Demo Apps** — 57 pre-built apps to test native bridges (camera, GPS, NFC, BLE, SSH, network shares, and more)

Every generated app is a real signed APK that appears in your Android launcher. You can share it, uninstall it, or update it — just like any app. Generated apps pass Google Play Protect scanning.

## How it works

```
┌─────────────────────────────────────┐
│         STANDARD LAUNCHER           │
│   App 1 🎯   App 2 📍   App 3 📷   │
└──────────────┬──────────────────────┘
               │ real installed APKs
┌──────────────▼──────────────────────┐
│         GENERATED APP SHELL         │
│  WebView + native bridge layer      │
│  HTML/JS injected as assets         │
└──────────────┬──────────────────────┘
               │ created and signed by
┌──────────────▼──────────────────────┐
│       CONTAINER APP (iappyxOS)      │
│  AI prompt → HTML → APK injection   │
│  → manifest patching → v2 signing   │
│  → PackageInstaller                 │
└─────────────────────────────────────┘
```

1. User describes an app (or picks a template, or enters a URL)
2. HTML/JS is injected into a pre-built WebView shell APK
3. The Android manifest is binary-patched with a unique package name
4. The APK is signed on-device using the Android Keystore (hardware-backed)
5. The system installer dialog appears — the app shows up in the launcher

## Features

- **AI generation** — automatic via API (Anthropic, OpenRouter) or manual copy-paste to any AI
- **35 native bridge classes (130+ methods)** — camera, location, sensors, audio, notifications, push notifications (FCM), NFC, Bluetooth LE, Bluetooth Classic (serial), SSH/SFTP, SMB network shares, HTTP server/client, TCP/UDP sockets, WiFi Direct, mDNS, biometric, SQLite, contacts, SMS, calendar, clipboard, TTS, screen, vibration, alarms, media gallery, download manager, home screen widgets, scheduled background tasks, and more
- **Icon editor** — emoji, text, images, custom colors, gradients, shadows, filters, rotation, multiple layers
- **App management** — launch, rebuild, edit, share (APK, HTML, QR code, WiFi Direct), uninstall
- **QR code transfer** — share apps between devices via animated QR codes (no internet needed)
- **P2P sharing** — share built apps directly between devices via WiFi Direct
- **CSS style presets** — Material, Glassmorphic, Minimal, Dynamic (Material You colors) — applied globally
- **Preview** — test your app in a WebView with live JS console and simulated bridges before building
- **Offline-first** — everything except AI generation works in airplane mode
- **Configurable** — custom system prompt, app ID prefix, multiple AI providers, CSS styles
- **Showcase** — browse and build community apps from GitHub, submit your own via PR
- **Home screen widgets** — generated apps can create configurable widgets with grids, clocks, timers, toggles
- **Background tasks** — scheduled JS execution even when the app is closed, auto-updates widgets
- **Version history** — automatic snapshots on rebuild, restore previous versions
- **Onboarding** — guided first-launch experience

## Quick start

1. [Download the latest Beta](https://github.com/iappyx/iappyxOS/raw/main/bin/iappyxOS.apk)
2. Sideload on Android 10+ (enable "Install unknown apps" for your browser)
3. Open iappyxOS → Create → pick a mode → build

**AI generation (two options):**
- **Manual (recommended)** — copy the generated prompt, paste it into any AI chat (Claude, ChatGPT, Gemini, etc.), paste the HTML back, preview, build
- **Automatic** — add your API key in Settings (Anthropic or OpenRouter), then tap "Generate" in the Create flow

## Sharing apps

Share your generated apps with others — multiple ways, no account needed:

- **Share APK** — send the compiled app file via any app (WhatsApp, email, Drive, Bluetooth)
- **Share Nearby** — transfer directly to another device via WiFi Direct, no internet required
- **Share via QR** — beam your app to another device using animated QR codes — no WiFi, no Bluetooth, just two screens
- **Share HTML** — export the source code so others can preview, modify, and rebuild it in their own iappyxOS

Recipients with iappyxOS installed can rebuild shared apps with their own icon and name.

## Native bridges

Generated apps access device hardware through a JavaScript bridge (`window.iappyx`):

| Bridge | What it does |
|--------|-------------|
| Storage | Key-value, file storage, pick files, save to Downloads, share files, move/copy with content URI support |
| Camera | Photo, video, QR/barcode scan, OCR, ML image classification, background removal, EXIF metadata, real-time frame scanning |
| Location | GPS single shot, continuous tracking, foreground service, geofencing |
| Sensors | Accelerometer, gyroscope, magnetometer, compass heading, proximity, light, pressure, step counter |
| Audio | Play/pause/seek/loop, record, speech-to-text, media session (lock screen controls), sound effects, audio focus, audio visualizer (waveform + FFT) |
| Notifications | Send with actions, schedule, repeating, badge count, cancel |
| NFC | Read tags, write NDEF text/URI |
| Bluetooth LE | Scan, connect, read/write characteristics, subscribe to notifications |
| SQLite | Full SQL database with transactions |
| Biometric | Fingerprint/face authentication |
| TTS | Text-to-speech with language, pitch, rate, completion callback |
| Contacts | Read device contacts (name, phone, email) |
| SMS | Send SMS messages |
| Calendar | Read/add calendar events |
| Clipboard | Read/write, monitor changes |
| Screen | Keep on, brightness, wake lock |
| Vibration | Patterns, haptic feedback (click, tick, heavy) |
| Alarms | Exact and repeating alarms (fire even when app is closed) |
| Share | Photos, text, files via share sheet; receive shared content from other apps |
| Device | Info, connectivity, torch, print, wallpaper, DND, shortcuts, dark mode, Material You theme colors |
| Media | Browse gallery (photos/videos/music), pick images, save to gallery, get metadata |
| Download | Queue file downloads with progress, survives app close |
| HTTP Server | Local HTTP/HTTPS server with TLS, CORS, file streaming |
| HTTP Client | Native requests with self-signed cert support, multipart upload, cookies (OkHttp) |
| SSH / SFTP | Remote terminal, command execution, file transfer, port forwarding (JSch) |
| SMB | Browse Windows/NAS network shares, upload, download, copy, rename, delete (jcifs-ng) |
| TCP Socket | Persistent bidirectional connections with TLS support |
| UDP | Datagram send/receive, unicast and multicast |
| NSD (mDNS) | Service registration, discovery, and resolve |
| WiFi Direct | P2P peer discovery, connection, group management |
| Push Notifications | FCM push notifications (requires Firebase setup in Advanced Settings) |
| Bluetooth Classic | Serial communication (SPP) for Arduino, ESP32, OBD-II car diagnostics, HC-05/HC-06 |
| Widget | Home screen widgets with configurable grid layouts, clocks, timers, checkboxes, toggles |
| Tasks | Scheduled background JS execution — fetch APIs, update widgets, send notifications while app is closed |
| Capabilities | Query available bridges and permissions at runtime |

## Building from source

### Prerequisites
- Android SDK
- Flutter 3.x
- Java 17+

### Quick build
```bash
./build.sh
# Output: bin/iappyxOS.apk (auto-installs if device connected)
```

### Manual build
```bash
# Shell APK (the template injected into generated apps):
cd src/shell_apk && ./gradlew assembleRelease
cp app/build/outputs/apk/release/app-release.apk ../container_app/assets/shell_template.apk

# Container app (iappyxOS itself):
cd src/container_app && flutter pub get && flutter build apk --release
```

> **Note:** Release builds require a signing keystore. Without `key.properties`, the build falls back to debug signing. See [Signing](#signing) below.

### Signing

The release APK is signed with a private keystore (not in this repo). To set up your own:

```bash
keytool -genkey -v -keystore iappyxos-release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias iappyxos
```

Create `src/container_app/key.properties`:
```
storePassword=YOUR_PASSWORD
keyPassword=YOUR_PASSWORD
keyAlias=iappyxos
storeFile=../../iappyxos-release.jks
```

Both files are gitignored. Never commit your keystore or passwords.

## FAQ

See [FAQ.md](FAQ.md) for answers to common questions — including the hard ones like "isn't this just a WebView wrapper?" and "why is it called OS?"

## License

MIT

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

## Support

If you find iappyxOS useful, consider [buying me a coffee](https://ko-fi.com/iappyx).
