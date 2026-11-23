# Brain Singer Open — experimental Android client

An independently written Android client for the existing Brain Singer lecture feed. It shows the server's lecture catalogue, offers search and category filters, and attempts to stream audio or video without a login screen or in-app premium purchase flow.

**This is not the original Brain Singer source code and not a feature-for-feature replacement.** The original Flutter app and backend source were not available. The client does not alter the original APK or the server. Server-side authentication, access restrictions, and missing media files cannot be fixed by this client. Several tested media links on `assets.brainsinger.com` returned HTTP 406; consequently, access to every lecture is not guaranteed.

## Important security and privacy notes

- The existing catalogue endpoint and media URLs use plain HTTP, not HTTPS. Network operators and other parties on the network may observe or alter requests and content. Do not enter passwords, personal data, or payment information. There is no login in this client.
- The app contacts the existing backend at `http://165.22.215.253:8090/userlectures` and media hosts returned by that endpoint. The backend and hosts operate independently of this repository; they may log network requests and may change or stop working.
- This client does not bundle lecture audio, video, catalogue data, trademarks, or original app code. The code license does not grant rights to external content, server APIs, branding, or third-party media.

## What works / limitations

- The code is designed to browse and filter the server-provided lecture list; tapping an item attempts playback through Android's built-in media player.
- Some URLs, formats or server responses may not play. Background playback, downloads, bookmarks, accounts, wallets, purchases, analytics, and original-app features are not implemented.
- This app has a different package ID (`org.brainsinger.openplayer`) and installs separately; it cannot update the original APK in place.
- Minimum Android 6.0 (API 23). The debug build targets API 28 and is for testing, not a production Google Play release.
- Compilation, APK signing and HTTP feed responses were checked, but playback and installation on a physical Android device or emulator have not been verified.
- The optional `downloads/BrainSinger-Open-0.1.0-debug.apk` is locally debug-signed and is **experimental**. A production release needs device testing, an updated target SDK, and a securely stored release signing key. Debug keystores are never included in this repository.

## Build a debug APK

Install a JDK, Android SDK platform 35 and Android SDK Build Tools 35.0.0, plus `zip`. Set `ANDROID_HOME` or `ANDROID_SDK_ROOT` to your Android SDK path, and `JAVA_HOME` to the JDK if necessary. Then run:

```sh
chmod +x build.sh
./build.sh
```

The result is `build/BrainSinger-Open-debug.apk`. The script generates a local debug signing key in the ignored `build/` directory if one does not exist. Debug keys are for testing only and should not be used for production distribution.

## Source license

The newly written client code is released under the MIT license in `LICENSE`. External content and names are not included in or licensed by this repository.
