# iappyxOS — Frequently Asked Questions

## General

### What is iappyxOS?
iappyxOS is an Android app that lets you create, build, and install real Android apps directly on your phone. No computer, no coding, no app store needed. Describe what you want, and it generates a real APK with its own icon in your launcher.

### Who is it for?
Anyone who wants a quick app for something — a personal tool, a calculator for a specific task, a simple game, a dashboard, a checklist. You don't need to know how to code. If you can describe what you want, you can build it.

### Is it free?
iappyxOS itself is free. If you use the AI-powered app generation, you'll need your own API key from an AI provider (Anthropic or OpenRouter). You can also use the manual flow — copy the prompt to any AI chat (free or paid) and paste the result back.

### Does it need an internet connection?
Only for AI generation (sending prompts to the AI). Everything else — building, signing, installing, running generated apps — works completely offline.

---

## Creating apps

### What are the three creation modes?

- **AI-generated App** — Describe what you want in plain language. An AI generates the HTML/JavaScript code, you preview it, then build. The app can use native features like camera, GPS, sensors, and more.
- **Website as App** — Enter a URL, and iappyxOS wraps it into a lightweight standalone app (~1MB). The website opens full-screen with its own launcher icon, separate from your browser. No native bridges — just a clean, sandboxed WebView.
- **Demo App** — 60+ pre-built apps that showcase what's possible. Useful for testing native features (camera, NFC, BLE, sensors, networking, etc.) and as starting points for your own ideas.

### What's the difference between "Full" and "Linked" prompt in Manual mode?
- **Full** copies the entire system prompt (~45 KB) plus your app description. Works with any AI. Use this when your AI has no browser or doesn't accept URLs.
- **Linked** copies a short (~600 char) prompt that points the AI at the canonical `SPEC.md` URL on GitHub. The AI fetches the full spec itself. Works with Claude.ai, ChatGPT (with browsing enabled), and Gemini. Useful when your AI rejects large pastes or when you want a lighter clipboard payload.

Both variants carry the same date-stamped version tag and the same anti-hallucination rules; the Linked one just offloads the bridge reference to a URL fetch.

### How does AI generation work?
iappyxOS generates a detailed prompt that includes your app description plus documentation for all available native features. You send this prompt to an AI, and it returns complete HTML/JavaScript code. There are two ways:

- **AI Manual (recommended)** — The app generates a prompt. Copy it, paste it into any AI chat (Claude, ChatGPT, Gemini, etc.), copy the HTML response back, and paste it in. This gives you the most control — you can use any AI, have a back-and-forth conversation to refine the result, and iterate until it's right.
- **AI API (automatic)** — Connect your own API key (Anthropic or OpenRouter) in Settings. Then tap "Generate" and the app talks to the AI directly.

### What AI provider should I use?
Any large language model works. For best results with native features, use Claude (Anthropic) or GPT-4. The system prompt is detailed enough that most modern AI models produce working apps. If you want the cheapest option, use OpenRouter — it gives you access to many models including free ones.

### Can I edit an app after building it?
Yes. Go to My Apps, tap Edit on any app, make changes (description, HTML, icon), and rebuild. The new version replaces the old one — no need to uninstall first.

### What does "Preview" do?
Preview shows your app in a test WebView with a live JavaScript console. Most native features are simulated (storage, vibration, sensors, alarms, TTS, audio playback), so you can test your app's logic without building an APK. Features that need real hardware (camera, GPS, NFC, contacts) show a clear message instead. Use the "Copy" button in the console to grab errors and feed them back to the AI for fixing.

### What happens if the AI-generated code doesn't work?
Use Preview to check for errors. The console shows JavaScript errors, warnings, and logs. Copy the errors (the "Copy" button filters out preview-only messages), paste them back into the AI conversation, and ask it to fix them. With the API flow, you can do this directly in the app's conversation view.

---

## Generated apps

### Are these real apps?
Yes. Each generated app is a real signed Android APK. It gets its own launcher icon, runs independently from iappyxOS, and can be shared with other people. You can uninstall iappyxOS and your generated apps keep working. Generated apps pass Google Play Protect scanning.

### What can generated apps do?
Generated apps run inside a WebView with access to 37 native bridge classes (140+ methods):

Camera (photo, video, QR scan, OCR, ML classification, background removal, EXIF, real-time scanning), GPS (tracking, geofencing), sensors (accelerometer, gyroscope, magnetometer, compass, proximity, light, pressure, step counter), audio (playback, recording, speech-to-text, media session with lock screen controls, sound effects, audio focus, audio visualizer), notifications (with actions, scheduled, repeating, badge), NFC read/write, Bluetooth LE (scan, connect, read/write characteristics, notifications), SQLite database, biometric authentication, text-to-speech, contacts, SMS, calendar, clipboard (read/write/monitor), screen control, vibration, alarms (exact and repeating), media gallery (browse photos/videos/music, save to gallery, metadata), download manager, HTTP server/client with TLS, SSH/SFTP, SMB network shares, TCP/UDP sockets, mDNS service discovery, WiFi Direct, wallpaper, torch, print, DND, app shortcuts, share target, Material You theme colors, push notifications (FCM), home screen widgets (configurable grid layouts, clocks, timers, checkboxes, toggles), Bluetooth Classic serial (Arduino, ESP32, OBD-II), scheduled background tasks (fetch APIs, update widgets while closed), and persistent storage.

The AI knows about all of these and will use them when appropriate for your app description.

### Can I include data files in my app (databases, JSON, images)?
Yes. In the Create/Edit flow, expand the "App Files" section and tap "+ Add file" to attach any file from your device. Files are baked into the APK at build time and available at runtime via `iappyx.storage.listAssets()`, `readAsset()`, and `extractAsset()`. Use `readAsset` for read-only access to small files (JSON configs, images as base64 — max 25 MB); use `extractAsset` to copy a file to writable storage first (required for SQLite databases or any file > 25 MB). Files are stored uncompressed in the APK, so large bundles increase APK size — a 10 MB database adds 10 MB to the APK.

### Can generated apps access the internet?
Yes. Generated apps can load images, fetch APIs, and make network requests just like a regular web page. They can also cache external JavaScript libraries (like Chart.js or pdf-lib) for offline use after first download.

### Can I share an app I made?
Yes. In My Apps, tap the menu on any app and choose Share. Options include:
- **Share APK** — send via any app (WhatsApp, email, etc.)
- **Share via QR** — beam the app using animated QR codes (no WiFi or internet needed)
- **Share Nearby** — transfer directly via WiFi Direct
- **Share HTML** — export the source code

The receiver can rebuild shared apps in their own iappyxOS with their own icon and name.

### What is the Showcase?
The [Showcase](https://github.com/iappyx/iappyxOS-showcase) is a collection of community-built apps that you can browse and build directly inside iappyxOS. Go to Create → Showcase, pick an app, and build it. You can also submit your own apps — tap the menu on any app in My Apps → "Submit to Showcase" and a GitHub pull request is created automatically.

### Can generated apps receive push notifications?
Yes, with setup. In the app's edit flow, expand "Advanced Settings" — you'll see the app's package name and a file picker for Firebase config. Create a free Firebase project at console.firebase.google.com, add an Android app with that package name, download `google-services.json`, and pick it. Rebuild the app. The AI can then use push notifications when you ask for them.

### Do generated apps auto-update?
No. If you rebuild an app with the same name, it updates on your device. But apps you've shared with others won't update — they'd need the new APK.

### Why does the install dialog say "from unknown source"?
Because the app wasn't downloaded from Google Play. This is normal for any app installed outside the app store. Your generated apps are signed with a secure key stored in your device's hardware — they're safe.

---

## App icons

### Can I customize the app icon?
Yes. The icon editor supports multiple layers — combine emoji, text, and images with custom colors, scaling, rotation, and positioning. The background color is also customizable.

### Why does my icon look different on the phone than in the editor?
The editor shows the raw icon. Android launchers apply their own shape mask (circle, squircle, rounded square, etc.) depending on the device manufacturer. iappyxOS renders full square icons so the launcher mask works correctly.

---

## Settings & configuration

### What is the App ID prefix?
Every Android app needs a unique package name (like `ljocht.harns.myapp12345`). The prefix is the first part. On first launch, iappyxOS generates a random Frisian prefix for your device (like `kreas.snits` or `noflik.dokkum`). You can change it to your own domain in Settings if you want (e.g., `com.yourname`). Max 20 characters, lowercase letters, digits, and dots.

### What is the system prompt?
The system prompt is the instruction document sent to the AI along with your app description. It tells the AI how to write HTML/JavaScript that works with iappyxOS's native bridges. You can view and edit it in Settings, but the default works well for most cases.

### Where are my API keys stored?
API keys are encrypted using Android's hardware-backed Keystore (EncryptedSharedPreferences). They never leave your device and are not readable by other apps.

---

## Troubleshooting

### The app says "not installed" when I tap Launch
The app was either uninstalled from the device or was built on a different device. Tap "Build Again" to rebuild and reinstall it.

### Build fails with no clear error
Check that your app name is not too long (max 37 characters) and doesn't contain unusual special characters. If building a demo app, make sure you haven't modified the template files.

### The AI generated code but it doesn't work
Use Preview to see what's wrong. The console will show JavaScript errors. Common issues:
- The AI included markdown fences (` ``` `) around the code — iappyxOS strips these automatically, but sometimes extra text remains
- The code references a native feature incorrectly — copy the console errors and ask the AI to fix them
- The AI didn't wait for the bridge to load — the `iappyx` object is injected after page load; apps should check `typeof iappyx !== 'undefined'` before using it

### I lost my apps after reinstalling iappyxOS
App data is stored on the device. If you uninstall iappyxOS, the app library is deleted. However, generated apps that are already installed on your device continue to work independently — they don't depend on iappyxOS.

### A generated app crashes or shows a white screen
The HTML/JavaScript has an error. Edit the app in iappyxOS, open Preview, and check the console for errors. If the app was AI-generated, paste the errors back into the AI and ask for a fix.

### My event trigger stopped firing (charger / headphones / Bluetooth / WiFi / Android Auto)
Triggers come in two modes:

- **Non-persistent (default)** — fires only while the app's process is alive. Android will eventually evict the process when you swipe the app away from recents or under memory pressure. After that the trigger stops firing until you open the app again.
- **Persistent** — registered with `{persistent: true}`. The app keeps a low-priority "Triggers active" notification in the shade, which prevents Android from evicting the process. Survives swipe-away and reboot. Required for Android Auto triggers.

If your trigger was working and then stopped, either use persistent mode, or re-open the app before you need it to fire. Aggressive OEM battery-savers (Samsung, Xiaomi, Huawei) may also kill the app even in persistent mode — whitelist it in the OS battery settings if needed.

### My geofence trigger only fires when the app is open, not in the background
Go to Settings → the generated app → Permissions → Location → **Allow all the time**. Android does not let apps request this via a runtime dialog — the user must toggle it manually. Call `iappyx.location.openBackgroundSettings()` from your app to jump them straight to the right Settings page.

### Why does my geofence take a minute or two to fire after I cross the boundary
Android debounces geofence transitions to avoid drive-by false positives. "Dwell" events wait for the configured `dwellDelayMs` (default 60 seconds). This is OS behavior, not an iappyxOS limitation.

### Battery impact of geofence triggers
Using `iappyx.trigger.geofence` is built on Android's `GeofencingClient`, which the OS optimizes heavily (cell/WiFi inference, hardware offload on some chips). One or two geofences typically cost 1–2% battery per 24 hours of background operation. Aggressive OEMs (Xiaomi, Huawei) may throttle Play Services even then — whitelist the app in the OS battery settings if transitions seem to miss.

### My `iappyx.intent.launchApp()` call from a trigger doesn't launch anything
Android blocks background activity starts by default. To launch another app silently from a trigger callback while your app isn't visible, the user must grant "Display over other apps" for your app (Settings → Special access). Call `iappyx.intent.requestOverlayPermission()` once at setup time to jump them to the right Settings page. Without this grant, the call returns `false` silently.

---

## Security

### Can someone read the source code of my generated app?
Yes. The HTML/JavaScript is stored as a plain file inside the APK. Anyone with the APK file can extract and read it. This is true of all Android apps — native apps can be decompiled too, just with more effort. Never hardcode API keys, passwords, or tokens in your app's code. Instead, ask the user to enter credentials at runtime and store them with `iappyx.save()`.

### Are generated apps safe to install?
Generated apps are signed with a hardware-backed key on your device. They cannot be modified after signing without breaking the signature — Android will refuse to install a tampered APK. However, you should only install generated apps from sources you trust, just like any sideloaded app. A malicious generated app could access your contacts, location, or camera if you grant it permissions.

### Are my credentials safe if my app connects to SSH or SMB?
Credentials entered at runtime and stored with `iappyx.save()` are in the app's private storage — other apps cannot access them. But if credentials are hardcoded in the HTML source, anyone with the APK can read them. Always prompt for credentials instead of embedding them.

---

## Technical

### How does it work under the hood?
iappyxOS contains two pre-built WebView shell APK templates — a full shell (~29MB, with all native bridges, ML Kit, and protocol libraries) and a lightweight web-only shell (~1MB, sandboxed, no bridges). When you build an app:
1. Your HTML/JavaScript is injected into the appropriate template as an asset
2. The Android binary manifest is patched with a unique package name and your app label
3. Unused ML Kit libraries are automatically stripped if the app doesn't use camera/ML features
4. Custom icon PNGs are generated at 5 density sizes and injected
5. The APK is signed on-device using APK Signature Scheme v2
6. Android's PackageInstaller handles the install dialog

### What Android versions are supported?
Android 8 (API 26) and above for generated apps. iappyxOS itself runs on Android 10+ (API 29).

### Is the source code available?
Yes. iappyxOS is open source under the MIT license.

### Can I build iappyxOS from source?
Yes. You need Android SDK, Flutter 3.x, and Java 17+. Run `./build.sh` from the project root — it builds the shell template, the container app, and installs it on a connected device.

---

## The hard questions

### Why is it called "OS"? It's not an operating system!
Not yet. But consider the direction: a phone with no pre-installed apps, no app store, no downloads. You just describe what you need and it appears. Need a calculator? Say so. Need a scanner? Say so. Apps aren't installed — they're generated on demand from prompts, run when needed, and dissolve when you're done. No updates, no storage bloat, no subscriptions. Just tokens in, tools out. iappyxOS is another step toward that idea. The "OS" is not a claim — it's a nod to where things are heading.

### Did you write all the code yourself?
No. iappyxOS was built with heavy use of AI — primarily Claude — for code generation, architecture decisions, and bug hunting. The idea, direction, and every design choice are mine. The code is heavily AI-generated. That's kind of the point of the project: if AI can generate apps for end users, it can generate the tool that generates them too. In the end, I believe almost all software will be prompts.

### Who is behind iappyx?
An independent maker from the Netherlands with a non-developer day job, who likes to build useful tools and open-sourcing them. Previously built [Instrumenta](https://github.com/iappyx/Instrumenta), a free consulting-style PowerPoint toolbar that started as a COVID side project.

### Isn't this just a WebView wrapper?
Yes — and that's the point. A WebView wrapper that patches its own binary manifest, signs itself with a hardware-backed key, generates multi-density icons, injects 35 bridge classes (130+ methods), and installs itself through the system package manager. All on a phone. The "just a wrapper" does a lot of heavy lifting.

More seriously: the value isn't the WebView. It's that you go from "I want an app that does X" to a real installed app in your launcher in under a minute, without touching a computer. With 35 bridge classes giving access to Bluetooth, SSH, network shares, push notifications, HTTP servers, ML Kit, home screen widgets, background tasks, and more — the "wrapper" is a full native development platform. The WebView is an implementation detail. The experience is the product.

### Why would I use this instead of just making a website?
A website needs a browser, a URL, and an internet connection. An iappyxOS app has its own icon, launches instantly, works offline, and can access your camera, GPS, NFC, Bluetooth, sensors, contacts, SSH servers, network shares, and 100+ other native features a website can't touch. It also doesn't disappear when you clear your browser tabs.

If a bookmark is enough for your use case, make a bookmark. If you want something that feels like an app and works like an app — that's what this is for.

### Can I publish a generated app on Google Play?
Technically, nothing stops you. The APK is properly signed and installable. But Google Play has review policies, and a single-file WebView app may not pass them depending on what it does. For personal use, sharing with friends, or sideloading in an organization — no problem. For app store distribution, you'd want to polish it further.

### Will iappyxOS still work under Google's new sideloading rules?
Starting September 2026, Google plans to require developer verification for Android apps on certified devices — including sideloaded ones. The policy is still evolving and the impact on iappyxOS isn't fully clear yet. Two potential paths:

1. **Enable Android's "advanced flow"** — one-time per device: Developer Mode → "Allow Unverified Packages" → reboot → 24-hour wait → biometric confirm → "indefinitely". After that, installs work normally with a warning dialog.
2. **Use an uncertified Android** (GrapheneOS, CalyxOS, /e/OS, LineageOS, any custom ROM) — no verification required, no setup friction.

### Are the generated apps any good?
That depends entirely on the AI and your description. A well-described app with clear requirements produces surprisingly capable results — dashboards, calculators, trackers, games, tools. A vague "make me something cool" produces something vague. The native bridges (camera, sensors, SQLite, etc.) give the AI a lot to work with. The quality ceiling is high; the quality floor is your prompt.

### Why not just use Flutter or React Native?
Because those require a computer, a development environment, hours of setup, and knowing how to code. Of course AI can help you with that as well, but iappyxOS is for the other 99% of people who don't have or want any of that. A developer might laugh at a single-HTML-file app — until they realize their mom just built a recipe converter with camera input in 45 seconds on her phone.

If you're a developer, you probably don't need iappyxOS. But you might still enjoy how fast it is for throwaway tools.

### Why is this free?
Because it costs nothing to run. There are no servers, no cloud, no accounts, no backend. Everything happens on your device. The only external cost is the AI API call, and you bring your own key for that. There's nothing to monetize without making the product worse.

If you find it useful, there's a donate link in Settings.

### Are you going to start charging?
No plans to. The architecture is deliberately serverless — no infrastructure means no running costs means no pressure to monetize. If the project grows and needs funding for development time, a paid version with extra features is more likely than paywalling the free one.

### Why not build an app store around this?
Because that would require servers, moderation, accounts, legal compliance, payment processing, and a full-time job maintaining it. iappyxOS is a tool, not a platform. You make apps for yourself and share them however you want — WiFi Direct, email, USB, or just send the APK file. No middleman.

Instead, there's the [Showcase](https://github.com/iappyx/iappyxOS-showcase) — a curated collection of community apps on GitHub. No accounts, no moderation overhead. Anyone can submit an app via pull request, and everyone can browse and build them directly in the app. Open source all the way down.
