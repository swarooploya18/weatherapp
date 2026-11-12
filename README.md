# Weather App

An Android weather app built with Kotlin. Sign in with email and password, view weather for your current location, check a multi-day forecast, and save cities for quick access.

## Features

- Create an account, sign in, and sign out with Firebase Authentication.
- View current weather and a multi-day temperature forecast for your location.
- Search for cities and add them to your favourites.
- Switch between Celsius and Fahrenheit.
- Pull to refresh weather data.
- View cached weather when location access is unavailable and cached data exists.

## Built with

- Kotlin and Android SDK
- AndroidX, Material Components, RecyclerView, and SwipeRefreshLayout
- Firebase Authentication and Credential Manager dependencies
- Google Play Services Location
- Open-Meteo Forecast and Geocoding APIs

## Requirements

- Android Studio with a compatible Android SDK
- JDK 11 or the JDK configured by Android Studio/Gradle
- A Firebase project with **Email/Password** sign-in enabled
- A Firebase Android configuration file at `app/google-services.json`

The app's minimum Android version is API 24. The compile and target SDK versions are defined in `app/build.gradle.kts`.

## Setup

1. Clone this repository and open the project in Android Studio.
2. Register an Android app in your Firebase project using the application ID `com.example.weatherapp`.
3. Enable the Email/Password sign-in provider in Firebase Authentication.
4. Download that Firebase app's `google-services.json` and place it in the `app/` directory.
5. Sync the project with Gradle, then run the `app` configuration on an emulator or Android device.
6. Allow location access to load weather for the device's current location. You can also search for and save another city from the app.

> Keep Firebase service-account credentials, signing keys, passwords, and other private credentials out of the repository. `google-services.json` is client configuration; secure your Firebase services with appropriate Firebase rules and API restrictions.

## Build from the command line

On Windows PowerShell, from the project root:

```powershell
.\gradlew.bat assembleDebug
```

The debug APK is generated under `app/build/outputs/apk/debug/`.
