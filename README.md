# E-Ink Launcher

An Android home-screen launcher optimised for **e-ink displays** (originally
targeting the Yota Phone 2, but usable on any Android device with an e-ink
panel).

---

## Features

| Feature | Details |
|---------|---------|
| **No-scroll navigation** | Page transitions happen exclusively via large **Anterior / Següent** (Prev / Next) buttons — no swipe gestures that could accidentally trigger a full-screen refresh on e-ink. |
| **Slideshow home screen** | Page 0 shows a full-screen photo that automatically rotates every **3 hours**. |
| **App grid** | Pages 1…N each display a **4 × 5 grid** (20 apps per page) of all installed launchable apps, sorted alphabetically. |
| **E-ink palette** | Pure black/white colour scheme, no animations, no ripple effects, high-contrast text. |

---

## Project structure

```
app/
└── src/main/
    ├── AndroidManifest.xml          – Declares HOME + DEFAULT launcher intent filter
    ├── java/com/eink/launcher/
    │   ├── MainActivity.kt          – Single activity; drives ViewPager2 + nav buttons
    │   ├── adapter/
    │   │   └── LauncherPagerAdapter.kt
    │   ├── fragment/
    │   │   ├── SlideshowFragment.kt – Page 0: rotating photo every 3 h
    │   │   └── AppsFragment.kt      – Pages 1+: static 4×5 app grid
    │   ├── model/
    │   │   └── AppInfo.kt
    │   └── util/
    │       └── SlideshowImageLoader.kt
    └── res/
        ├── layout/
        │   ├── activity_main.xml
        │   ├── fragment_slideshow.xml
        │   ├── fragment_apps.xml
        │   └── item_app.xml
        └── values/
            ├── colors.xml   – Black-and-white e-ink palette
            ├── strings.xml  – Catalan/English UI strings
            └── themes.xml
```

---

## Slideshow images

Place **JPEG or PNG** photos in the following folder on the device's external
storage (no special permissions required):

```
<external storage>/Android/data/com.eink.launcher/files/EInkLauncher/
```

On most devices you can reach this path via a file manager or ADB:

```bash
adb push my_photo.jpg \
    /sdcard/Android/data/com.eink.launcher/files/EInkLauncher/
```

---

## Building

Requirements:
* Android SDK (API 34)
* JDK 8 or later
* Internet access to [Google Maven](https://maven.google.com) for the Android
  Gradle Plugin

```bash
./gradlew assembleDebug
# APK will be at app/build/outputs/apk/debug/app-debug.apk
```

---

## Setting as default launcher

1. Install the APK on the device.
2. Press the **Home** button.
3. Select **E-Ink Launcher** from the resolver dialog and choose **Always**.
