# Skins

An Android app for browsing VALORANT skins and checking your daily shop.

- All Skins: switch between gun skins and accessories, with offline images, filters, exact known prices and wiki links. Weapon filters use a bundled overall player pick-rate snapshot.
- Shop: the selected account's daily skins, weekly accessory offers, reset countdown and saved daily history.
- Starred: saved skins, observed appearances and estimated chances/average waits. Estimates assume a uniform four-item rotation; Riot's actual odds can differ.
- Settings: compact account cards ordered by level, with player-card art, current/peak rank, owned items and removal controls. Tap a card to view its shop; mark one MAIN for the widget. Tap a rank icon for its name/rating.
- A 5×1 home-screen widget shows the MAIN account's four offers and a refresh button.

The catalogue and images are bundled with the app. Shop access uses Riot's client-facing endpoints. History records successfully fetched rotations; missed days cannot be recovered. Background refresh waits for a network connection. Quiet notifications report new rotations or failed checks; Android may delay jobs. Rank, ownership and shop data are cached per account. Unknown prices stay unavailable rather than being estimated.

Accessories include full card portraits/banners, larger spray artwork and Flex items where the source provides them. Titles use their actual text. Other items retain their supplied preview or icon.

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
