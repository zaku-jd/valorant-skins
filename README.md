# Skins

An Android app for browsing VALORANT skins and checking your daily shop.

- All Skins: offline images and variants, weapon/skin-line/rarity filters, search, prices and wiki links. Weapon filters use a bundled overall player pick-rate snapshot.
- Shop: your current offers, reset countdown and saved daily history for each account.
- Starred: saved skins, observed appearances and estimated chances/average waits. Estimates assume a uniform four-item rotation; Riot's actual odds can differ.
- Settings: add Riot accounts, switch between saved sessions, sign out, and control daily refresh and quiet account reminders.
- A 5×1 home-screen widget shows the active account's four offers and a refresh button.

The catalogue and images are bundled with the app. Shop access uses Riot's client-facing endpoints. History records successfully fetched rotations; missed days cannot be recovered. Android may delay background refresh. Saved sessions can renew while Riot permits it; expired sessions require signing in again.

## Install

Download the APK from [Releases](https://github.com/zaku-jd/valorant-skins/releases). Updates signed with the same key preserve app data. Android 8+ is required.

## Build

Open this folder in Android Studio, or use JDK 17+ and Android SDK 35:

```powershell
./gradlew.bat assembleDebug
node verify.mjs
```

The debug APK is written to `app/build/outputs/apk/debug/`. Your own build uses a different signing key from the published APK; keep your key for future updates.

## Review security

- [LoginActivity.java](app/src/main/java/app/quietshop/LoginActivity.java): Riot sign-in WebView, callback checks and cookie cleanup.
- [Vault.java](app/src/main/java/app/quietshop/Vault.java) and [Accounts.java](app/src/main/java/app/quietshop/Accounts.java): Android Keystore encryption and saved-session handling.
- [Store.java](app/src/main/java/app/quietshop/Store.java): network requests, session renewal and shop caching.
- [AndroidManifest.xml](app/src/main/AndroidManifest.xml): permissions, exported components and disabled backups.

Tokens/cookies stay in the encrypted local vault. Shop history and stars are local app data. This is an unofficial app; Riot can change or restrict its endpoints. [NOTICES.md](NOTICES.md) lists data, image, font and library sources.
