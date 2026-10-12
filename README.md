# YTSave

Personal-use YouTube downloader for Android (Kotlin, Jetpack Compose, Material 3).
Uses yt-dlp + ffmpeg through the `youtubedl-android` library.

## Build

1. Install Android Studio (Ladybug or newer).
2. File > Open > select this folder, let Gradle sync (needs internet; JitPack is used for yt-dlp).
3. Plug in your phone (USB debugging on) and press Run, or Build > Build APK(s) and sideload it.

## Use

- Paste a link and tap Fetch, or share a video from the YouTube app to YTSave.
- Pick a quality (or audio only), then Download.
- Files are saved in Downloads/YTSave.

## Notes

- yt-dlp updates itself in the background on launch; if downloads start failing, open the app
  once on Wi-Fi so it can refresh.
- Only download content you own or have the right to save. Downloading from YouTube otherwise
  breaks its Terms of Service.
