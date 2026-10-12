# SeiDesk RM

Android Remote Desktop Protocol (RDP) client for Windows and Windows Server. Connect directly to your PC — peer-to-peer, no cloud relay, no third-party account required.

[**Releases on GitHub**](https://github.com/Seijii-Dev/SeiDesk-RM/releases) · [Build Guide](docs/BUILD.md) · [Notice & Licenses](XYDESK-REMOTE-NOTICE.md)

---

## Overview

SeiDesk RM displays your Windows desktop on your Android device over standard RDP, complete with customizable touch controls, trackpad gestures, keyboard input, HUD controls, and audio redirection. Connections are established directly to your target host; there is no intermediate server intercepting your display or storing credentials.

**Requirements:**
- Windows 10/11 Pro, Enterprise, or Windows Server (Windows Home edition does not include an RDP host)
- Android 7.0 (API 24) or newer

---

## Step-by-Step Guide

### 1. Prepare Your Windows PC

1. Open **Settings → System → Remote Desktop** and turn on **Enable Remote Desktop**.
2. Note your PC name or local IP address via **Settings → Network & internet → Properties**, or run `ipconfig` in Command Prompt.
3. Ensure your Windows user account has a password. RDP standard security requires password-authenticated accounts.
4. For access outside your local network, use a secure VPN (WireGuard, Tailscale, ZeroTier) or SSH port forwarding. Do not expose port 3389 directly to the public internet.

### 2. Install on Android

1. Download the release APK matching your device's architecture:
   - `arm64-v8a` (`SeiDesk-RM64bit.apk`) → Most modern Android devices (2017+)
   - `armeabi-v7a` (`SeiDesk-RM32bit.apk`) → Older 32-bit devices
   - `x86_64` (`SeiDesk-RM64bit-x86.apk`) → Android emulators and x86 tablets
2. Open the APK file and allow **Install unknown apps** for your browser or file manager.
3. Launch SeiDesk RM.

### 3. Add a Device

1. Tap **New device** (+).
2. Enter the **host address** (IP or hostname) and **port** (default: `3389`).
3. Enter your Windows **username** and **password**. Credentials are encrypted on-device via Android Keystore and never sent elsewhere.
4. Save the profile.

### 4. Connect

1. Tap the device card to start connecting.
2. On first connection, verify and accept the server's **certificate fingerprint**.
3. Your Windows desktop appears.

### 5. In-Session Controls

| Feature | How to Use |
|---|---|
| Mouse navigation | **Trackpad** mode (drag anywhere) or **Direct touch** — toggle in the **Input** panel |
| Right / Middle click | HUD buttons, or configure in the **Buttons** panel |
| Keyboard | Keyboard icon on the bottom-right rail |
| Clipboard sync | Automatic text synchronization across Android and Windows |
| Send file to PC | **Session → File transfer** → *Send file to PC*. Appears on Windows as drive `XyDesk` (`\\tsclient\XyDesk`) |
| Receive file from PC | Copy files to the `XyDesk` redirected drive in Windows, then tap **Refresh** on Android |
| Screenshot desktop PC | **Session → Screenshot**; saved to `Pictures/SeiDesk-RM` |
| Screen recording | **Session → Recording**; MP4 video + PC audio saved to `Movies/SeiDesk-RM` |
| Media control | **Music** panel tab — play/pause, seek, shuffle, repeat, queue, and music library browser |
| Smooth scrolling | Bottom-right rail → scroll pill icon |
| Gamepad | Enable in **Buttons** panel; dual analog joysticks + 18 mappable controls |

All session controls are available by tapping **Menu** inside the active session.

---

## Features

- **Connection & Protocol** — Unlimited profiles, Wake-on-LAN, certificate pinning, auto-reconnection, FreeRDP 3 core engine.
- **Display & Scaling** — Desktop resolution options, zoom, orientation locking, Windows DPI scaling, multi-monitor configuration.
- **Input Methods** — Trackpad mode, direct touch, mouse gesture shortcuts, inertial scrolling, gyro mouse, hardware keyboard, IME support.
- **Customizable HUD** — Move, resize, and assign actions to on-screen buttons; virtual gamepad presets; layout import/export.
- **Media Controls** — Manage playback on active media apps (Spotify, SoundCloud, local players) directly from the session HUD without leaving the remote desktop.
- **File Transfer** — Bidirectional file transfer via RDP drive redirection.
- **Recording & Capture** — Native PC-only desktop capture to MP4 and screenshot capture to PNG without recording phone HUD or notifications.
- **Audio & Peripheral Redirection** — PC audio playback, microphone redirection, and clipboard sharing.

---

## Troubleshooting

- **Connection Timeout** → The host PC is asleep, the IP address is incorrect, or devices are on different subnets. Try pinging the host IP from your Android device.
- **CredSSP / Authentication Rejected** → Account has a blank password, or Remote Desktop is disabled on the host.
- **Black Screen After Login** → Lock and unlock the Windows session, or temporarily disable hardware-accelerated GPU scheduling in Windows Display settings.
- **Latency / Stutter** → Use 5 GHz Wi-Fi or wired Ethernet, reduce display resolution in the **Display** panel, and verify UDP transport.

---

## Security

RDP connections run directly peer-to-peer to your chosen host. Saved credentials are protected with AES-GCM encryption backed by the Android Keystore. Diagnostic logs may contain host addresses and certificate fingerprints — sanitize logs before public sharing.

Report security vulnerabilities privately via [GitHub Security Advisories](https://github.com/Seijii-Dev/SeiDesk-RM/security/advisories/new).

---

## Building from Source

See [docs/BUILD.md](docs/BUILD.md) for required versions of JDK, Android SDK, NDK, and CMake.

```bash
cd client/Android/Studio
./gradlew testDebugUnitTest
```

---

## Licensing & Attribution

- FreeRDP is licensed under Apache-2.0. Copyright (C) 2012-2026 FreeRDP contributors.
- SeiDesk RM application code: (C) Seijixinghe888.
- Upstream notices and modification records: see [`XYDESK-REMOTE-NOTICE.md`](XYDESK-REMOTE-NOTICE.md).
