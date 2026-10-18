# Skins · Android 0.2.1

Three swipeable pages: **All Skins → Shop → Starred**. Shop opens by default. The interface uses the actual Material Components library with a Material 3 dark theme, including the toolbar, tabs, cards, chips, buttons, bottom sheets, dialogs, and text inputs. Android 8.0 or later.

## Install

Transfer `Skins-0.2.1.apk` to your phone and open it. Allow installation from the app opening the APK if Android requests it. It uses the same package and signing certificate as the earlier personal build, so it can install as an update and preserve the session. The launcher name is now **Skins**.

## Catalogue and stars

The APK includes 1,432 skins across 21 weapons, 2,971 variant entries, weapon icons, rarity icons, and variant swatches. Every rendered skin and variant image is bundled offline. There are **no catalogue-update or image-download controls** and no runtime image downloads.

The packed image bundle is 38.1 MB: 6,520 source image references deduplicated to 3,420 transparent WebP files. Images fit within 800 × 400 pixels at quality 82. New skins require a newer app build. Category labels use Barlow Condensed Bold, an OFL-licensed substitute for the reference's commercial Tungsten Bold. Tungsten can be substituted if an appropriate native-app font file and embedding license are supplied.

Scroll the category strip, choose a weapon by its icon, search by name, filter by skinline or rarity, and sort by name or price in either direction. Stars are stored locally and survive app restarts and account switching. Unknown/no individual VP prices sort last. Tap a card for variants and an **Open wiki** action. Wiki links open the skinline's collection page in the phone's browser, where its weapon skins are listed.

## Prices and shop

Settings is the cog icon. Choose your account shard and sign in through Riot's HTTPS login page. `na` also covers BR/LATAM. This build supports Riot username/password login, including Riot's own MFA screen; external social sign-in isn't supported.

Shop displays the four daily offers, exact returned VP prices, rarity-tinted cards, the local refresh time and a countdown updated each second while the app is visible. The refresh icon fetches offers on demand. Shop also refreshes at app launch if a session exists. Expired saved offers are labelled as expired.

For offline catalogue prices, exact prices previously observed in the public data snapshot are used where available. After a successful sign-in, the app also attempts Riot's read-only offer-price catalogue and keeps those prices locally. Other purchasable skins show a clearly marked **≈ edition-base estimate**, with an explanation in the detail sheet. Exclusive/Ultra and some melee prices can differ from their edition base. Battle-pass, included and agent gear items are labelled by acquisition type instead of receiving invented individual VP prices. Catalogue prices are reference information; live shop prices take precedence.

## Home-screen widget

Long-press the Android home screen → Widgets → **Skins**, then place the shop widget. It defaults to 5×1 and remains horizontally resizable, subject to launcher grid sizing. Existing placed widgets may retain their previous size; resize them or remove and re-add them to use the new default. It displays four skin pictures, rarity backgrounds and a refresh icon, with no visible skin names or prices. A small date indicates when the displayed rotation was fetched; cached images remain until the widget is refreshed or Android requests a redraw. On redraw, expired offers are cleared.

Tap refresh to enqueue one Android job. There is **no periodic polling and no notification permission**. Refresh waits for network access and can be delayed or stopped by Android. Open the app if the widget reports an account/session problem. Tapping a skin opens Shop. Android widgets use RemoteViews, which cannot host Material Components directly; their rounded shapes, iconography and colors match the app's Material 3 theme.

## Account and session

Settings provides **Sign in / switch account** and **Sign out**. Switching accounts replaces the session only after successful login. Sign-out clears the encrypted session and saved shop, including the widget's cached rotation. Catalogue and stars remain.

The app doesn't control Riot's session expiry. It attempts cookie-based renewal when you refresh, preserves rotated cookies, and asks for sign-in again if Riot requires it. Riot's documented 30-day checkbox is for remembered MFA verification, separate from staying signed in. Longer or permanent login cannot be guaranteed. https://support.riotgames.com/en-us/riot/account/multi-factor-authentication-how-to-faq/

The isolated login WebView has no JavaScript bridge, no injected scripts and no TLS bypass. Callback host/path and login state are checked. The app never handles your password through its own input fields. Bearer tokens and cookies are encrypted with AES-GCM using Android Keystore. WebView cookies/storage are cleared on normal completion or cancellation; backups are disabled. These protections do not cover a compromised device.

This remains an unofficial app using Riot's client-facing endpoints. Riot lists online store tracking as an unapproved developer-API use case, so login/endpoint support and account policy acceptance cannot be guaranteed. It performs no purchases, client memory inspection, gameplay actions, interception or Vanguard interaction.

## Build and checks

Open this folder in Android Studio, or use the official included Gradle wrapper with JDK 17+ and Android SDK 35:

```powershell
./gradlew.bat assembleDebug
node verify.mjs
```

`build-apk.ps1` is a convenience wrapper. A locally rebuilt APK uses your Android debug signing key, which differs from the supplied personal build unless you configure the retained key used for that build. To keep updates compatible, preserve signing keys; they are deliberately excluded from this source archive.

The bundled-data check verifies every skin and variant image reference, WebP signatures, category coverage and navigation/widget configuration. `tests/SkinLogicTest.java` checks actual countdown, expiry and price-sort logic and can run with a host JDK alongside `SkinLogic.java`. The supplied APK was compiled, converted to DEX, ZIP-alignment checked, and its v2/v3 signatures verified. The isolated emulator did not complete startup in this environment, so visual interaction and widget behavior remain unverified on a running device. Live Riot login/shop access also requires testing with your account.

See [NOTICES.md](NOTICES.md) for image, metadata, font and library credits.
