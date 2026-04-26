# E-Ink Dashboard Launcher

An Android home-screen launcher optimised for **e-ink displays** (originally
targeting the Yota Phone 2, but usable on any Android device with an e-ink
panel).

---

## Features

| Feature | Details |
|---------|---------|
| **No-scroll navigation** | Page transitions happen exclusively via large **Anterior / Següent** (Prev / Next) buttons — no swipe gestures that could accidentally trigger a full-screen refresh on e-ink. |
| **Dashboard home screen** | Page 0 shows a comprehensive dashboard with:<br>• Current time and date<br>• Real-time weather with 5-day forecast (OpenWeatherMap API)<br>• Personal notes list<br>• Currently reading book progress |
| **App grid** | Pages 1…N each display a **4 × 5 grid** (20 apps per page) of all installed launchable apps, sorted alphabetically. |
| **E-ink palette** | Pure black/white colour scheme, no animations, no ripple effects, high-contrast text. |
| **Local storage** | Notes and reading progress are saved locally using SharedPreferences. |

---

## Setup

### 1. Clone and build

```bash
git clone https://github.com/skirep/eink-dashboard.git
cd eink-dashboard
./gradlew assembleDebug
```

### 2. Configure Weather API (Optional)

To get real weather data:

1. Get a free API key from [OpenWeatherMap](https://openweathermap.org/api)
2. Edit `app/src/main/java/com/eink/launcher/repository/WeatherRepository.kt`
3. Replace `YOUR_API_KEY_HERE` with your key

See [API_SETUP.md](API_SETUP.md) for detailed instructions.

Without an API key, the app will show an error message but will still function with cached data.

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
    │   │   ├── HomeFragment.kt      – Page 0: dashboard with weather, notes, reading
    │   │   └── AppsFragment.kt      – Pages 1+: static 4×5 app grid
    │   ├── model/
    │   │   ├── AppInfo.kt
    │   │   ├── WeatherInfo.kt
    │   │   ├── NoteItem.kt
    │   │   └── ReadingInfo.kt
    │   ├── api/
    │   │   ├── WeatherModels.kt     – OpenWeatherMap API response models
    │   │   ├── WeatherApiService.kt – Retrofit API interface
    │   │   └── RetrofitClient.kt    – HTTP client configuration
    │   ├── repository/
    │   │   ├── WeatherRepository.kt – Fetches weather data from API
    │   │   └── LocalDataRepository.kt – SharedPreferences storage
    │   ├── viewmodel/
    │   │   └── HomeViewModel.kt     – LiveData and business logic
    │   └── util/
    │       └── SlideshowImageLoader.kt
    └── res/
        ├── layout/
        │   ├── activity_main.xml
        │   ├── fragment_home.xml
        │   ├── fragment_apps.xml
        │   ├── item_app.xml
        │   ├── item_forecast_day.xml
        │   └── item_note.xml
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
