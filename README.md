<div align="center">

<img src="docs/readme/logo.svg" alt="Sideraven logo" width="160">

# Sideraven

### Zen-style vertical tabs for Firefox on Android tablets

[![Latest release](https://img.shields.io/github/v/release/Dweep4269/sideraven?style=for-the-badge&labelColor=1B264F&color=EDF67D)](https://github.com/Dweep4269/sideraven/releases/latest)
[![Downloads](https://img.shields.io/github/downloads/Dweep4269/sideraven/total?style=for-the-badge&labelColor=1B264F&color=EDF67D)](https://github.com/Dweep4269/sideraven/releases)
[![Android 8.0+](https://img.shields.io/badge/Android-8.0%2B-EDF67D?style=for-the-badge&labelColor=1B264F&logo=android&logoColor=EDF67D)](#install)
[![License: MPL-2.0](https://img.shields.io/badge/license-MPL--2.0-EDF67D?style=for-the-badge&labelColor=1B264F)](LICENSE)

[![Report a bug](https://img.shields.io/badge/Report_a_bug-1B264F?style=for-the-badge&logo=github&logoColor=EDF67D)](https://github.com/Dweep4269/sideraven/issues/new?template=bug_report.yml)
[![Request a feature](https://img.shields.io/badge/Request_a_feature-1B264F?style=for-the-badge&logo=github&logoColor=EDF67D)](https://github.com/Dweep4269/sideraven/issues/new?template=feature_request.yml)

[Screenshots](#screenshots) · [Features](#features) · [Download](#download) · [Zen Sync](#sync-with-zen-on-your-computer) · [FAQ](#faq) · [Build](#build-from-source)

<img alt="Sideraven on a tablet, with the Sidebar down the side of the page" src="docs/readme/banner.png" width="100%">

</div>

Sideraven is a web browser made mainly for Android tablets, and (not so much) for phones. Most Android browsers are built for phones first and stretched onto tablets. Sideraven starts from the tablet. Your tabs move off the top of the screen into a column down the side, the way [Zen Browser](https://zen-browser.app) does it on the desktop. Essentials sit at the top of the Sidebar, your pinned tabs below them, and Spaces keep work, study and everything else apart. If you use Zen on your computer, Sideraven can sync your Spaces, Essentials and pinned tabs with it.

Under the hood it's a fork of [Iceraven](https://github.com/fork-maintainers/iceraven-browser), which is a fork of Firefox for Android, so it keeps Firefox's engine, add-ons and sync.

> [!WARNING]
> Sideraven is an early, independent project. Mozilla, Zen and the Iceraven maintainers don't make, endorse or review it. Expect bugs, and keep a backup of anything you can't lose. Sideraven has a backup feature for exactly that.

> [!NOTE]
> Sideraven is built with substantial help from AI coding tools. Every change is tested and reviewed before it ships.

---

## Screenshots

<table>
  <tr>
    <td width="50%"><img src="docs/readme/screen-persistent.png" alt="The full Sidebar in Persistent mode: Essentials at the top, pinned tabs and open tabs below, beside a web page"></td>
    <td width="50%"><img src="docs/readme/screen-autohide.png" alt="The Auto-Hide Sidebar pulled out over a full-screen page, with its handle at the edge"></td>
  </tr>
  <tr>
    <td align="center"><b>Persistent:</b> the whole Sidebar beside the page</td>
    <td align="center"><b>Auto-Hide:</b> full-screen pages, the Sidebar a swipe away</td>
  </tr>
  <tr>
    <td width="50%"><img src="docs/readme/screen-collapsed.png" alt="The Sidebar in Collapsed mode: a narrow strip of site icons beside a web page"></td>
    <td width="50%"><img src="docs/readme/screen-search.png" alt="The search pop-up open over a page, with suggestions and search engines"></td>
  </tr>
  <tr>
    <td align="center"><b>Collapsed:</b> just the icons, more room for the page</td>
    <td align="center"><b>Search pop-up:</b> suggestions, engines, your copied link</td>
  </tr>
</table>

---

## Features

<table>
<tr>
<td width="50%" valign="top">

#### The Sidebar
- **Persistent** keeps the full Sidebar beside the page.
- **Collapsed** shrinks it to a strip of icons.
- **Auto-Hide** keeps it out of the way until you pull it from the screen edge or tap its handle. Hold the handle to move it up or down.
- Put it on the left or the right.
- `Ctrl+Shift+S` switches between the modes.

</td>
<td width="50%" valign="top">

#### Spaces and Essentials
- **Spaces**, each with its own tabs, name, icon and colour. Switch with a tap, or `Ctrl+Alt+←` and `Ctrl+Alt+→`.
- **Essentials:** up to 12 favourite sites as icons at the top of the Sidebar, in every Space. `Ctrl+1` to `Ctrl+9` open the first nine.
- **Pinned tabs and folders:** up to 12 sites per Space that you always want open.

</td>
</tr>
<tr>
<td width="50%" valign="top">

#### Tabs
- Swipe a tab toward the screen edge to close it.
- Drag tabs to reorder them, or onto Essentials to pin them.
- Unload tabs you aren't using to free memory. They reload when you open them.
- Private browsing switches instantly and gets its own Sidebar.

</td>
<td width="50%" valign="top">

#### Search and the address bar
- A pop-up search bar with suggestions, search engines, your copied link, QR scanning and voice search.
- Move the address bar into the Sidebar for a full-screen page, in any mode that can show it.

</td>
</tr>
<tr>
<td width="50%" valign="top">

#### Make it yours
- Row size, accent colour, animations and haptics.
- App icons, including a few made just for Sideraven.
- **Backup:** save your Spaces, Essentials, pinned tabs and settings to a file, and restore them on another device.

</td>
<td width="50%" valign="top">

#### Everything from Iceraven
- Firefox's GeckoView engine, Firefox Sync and tracking protection.
- A longer add-on list than Firefox's, and `about:config`.
- Iceraven's telemetry removal. Sideraven adds no analytics of its own.

</td>
</tr>
</table>

---

## Download

<p align="center">
  <a href="https://github.com/Dweep4269/sideraven/releases/latest"><img src="https://raw.githubusercontent.com/Kunzisoft/Github-badge/main/get-it-on-github.png" alt="Get Sideraven on GitHub" height="70"></a>
  <a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/Dweep4269/sideraven"><img src="https://raw.githubusercontent.com/ImranR98/Obtainium/main/assets/graphics/badge_obtainium.png" alt="Get Sideraven with Obtainium" height="70"></a>
</p>

| Where | Status |
|---|---|
| GitHub Releases | [![Latest release](https://img.shields.io/github/v/release/Dweep4269/sideraven?labelColor=1B264F&color=EDF67D)](https://github.com/Dweep4269/sideraven/releases/latest) |
| Obtainium | Tracks GitHub Releases: add `https://github.com/Dweep4269/sideraven` |
| IzzyOnDroid | Planned, once 1.0 settles |
| Google Play | Not planned |

### Install

Sideraven runs on Android 8.0 or newer.

1. Download `sideraven-<version>-arm64-v8a.apk` from the [latest release](https://github.com/Dweep4269/sideraven/releases/latest). If your device is an older 32-bit one, take `armeabi-v7a` instead.
2. Open the file. When Android asks, let your browser or file manager install apps.

With Obtainium, tap the badge above, or tap **Add app** and enter `https://github.com/Dweep4269/sideraven`. Obtainium then tells you when an update is out.

Sideraven installs as `app.sideraven.browser`, a separate app from Firefox and Iceraven, so you can keep them side by side. Updates install over each other without losing your data.

### Verify your download

Every release is signed with Sideraven's own key. Its certificate's SHA-256 fingerprint is:

```
8F:D7:0E:28:D8:B8:14:80:D5:AA:D6:E6:95:C6:E0:96:2B:35:33:A1:34:56:62:17:E7:AE:E9:7A:B1:B7:A9:6C
```

Check it with `apksigner verify --print-certs sideraven-<version>-arm64-v8a.apk`, or with [AppVerifier](https://github.com/soupslurpr/AppVerifier) on the phone.

---

## Sync with Zen on your computer

Zen Sync reads and writes Zen's own data through Firefox Sync. Sign in to Sideraven with the Firefox account your desktop Zen uses, then turn on Zen Sync in **Settings > Sideraven > Zen Sync**.

- Your Spaces, Essentials, pinned tabs and folders sync both ways.
- You choose which desktop Spaces appear on Android. A Space you create later on the computer stays off until you turn it on.
- The tabs open in a Space on your computer show under **Desktop Tabs**. You can open them on Android, but changes don't go back.
- Sync runs on its own, or only when you tap **Sync now**.

> [!NOTE]
> Zen Sync is Sideraven's own feature, not an official Zen one. If Zen changes how it stores Spaces, Sideraven stops writing to them until it catches up. It keeps reading, so nothing is lost.

---

## FAQ

<details>
<summary><b>Does it work on phones?</b></summary>

Yes. On a phone the Sidebar hides at the screen edge until you pull it out by its handle, and you can move the address bar into it for full-screen pages. To use Sideraven as a plain browser instead, turn off **Use the sidebar on phones** in **Settings > Sideraven > Sidebar**.
</details>

<details>
<summary><b>Can I keep using Firefox or Iceraven?</b></summary>

Yes. Sideraven is a separate app with its own data, so all three install side by side. Sign in to the same Firefox account to share bookmarks, history and passwords.
</details>

<details>
<summary><b>Do my add-ons work?</b></summary>

The add-ons that work in Iceraven work in Sideraven. Iceraven offers a longer list than Firefox, but not every add-on works on Android.
</details>

<details>
<summary><b>Why isn't it on Google Play?</b></summary>

Sideraven is a small, independent project, and GitHub Releases with Obtainium covers updates. An IzzyOnDroid listing is planned once 1.0 settles.
</details>

<details>
<summary><b>Does it send data anywhere?</b></summary>

Sideraven keeps Iceraven's removal of Firefox's data collection and adds no analytics of its own. Firefox Sync and Zen Sync talk to Mozilla's sync servers only when you sign in and turn them on.
</details>

---

## Planned

These aren't in Sideraven yet:

- A Sidebar button for the synchronised tabs feature by firefox
- Settings to hide Sidebar buttons you don't use, for a cleaner Sidebar
- Split view, with two tabs side by side
- Searching your tabs from the Sidebar, and selecting several tabs at once
- Folders for ordinary tabs
- Zen theming (a simpler version of it)

---

## Build from source

<details>
<summary>Build Sideraven yourself</summary>

You need Linux (or WSL on Windows), JDK 17 and the Android SDK. The first build compiles Firefox's Android code, so it takes about an hour and 8 GB of memory.

```sh
git clone --recursive https://github.com/Dweep4269/sideraven
cd sideraven
./automation/iceraven/install-sdk.sh          # the Android SDK and NDK, in ~/android-sdk
./automation/sideraven/prepare.sh --release   # Iceraven's patches; the app's text says Sideraven
./gradlew app:assembleForkRelease -PversionName=1.0.0
```

The APKs land in `gradle/build/app/outputs/apk/forkRelease/`, unsigned. Android only installs a signed APK, so sign the one for your device with a key of your own:

```sh
keytool -genkeypair -keystore my-key.jks -alias me -keyalg RSA -keysize 4096 -validity 10000
apksigner=$(ls -d ~/android-sdk/android-sdk-linux/build-tools/*/apksigner | sort -V | tail -1)
"$apksigner" sign --ks my-key.jks --out sideraven.apk \
  gradle/build/app/outputs/apk/forkRelease/app-arm64-v8a-forkRelease-unsigned.apk
```

A build signed with your key can't update an official release, or the other way round. Uninstall one before installing the other.

To work on Sideraven itself, build the development app, Sideraven Dev, with `./gradlew app:assembleForkDebug`. It installs beside the release. Run Sideraven's tests with:

```sh
./gradlew -PtestBuildType=forkDebug -PsideravenTests app:testForkDebugUnitTest
```

Sideraven's own code lives in `app/src/main/java/pt/dweep/sideraven/`. Where it has to change Iceraven's files, the change is a short call marked `SIDERAVEN-HOOK`, so updating to a new Firefox version stays manageable. `./automation/sideraven/list-hooks.sh` lists them all.
</details>

---

## Get help

- Found a bug? [Report it](https://github.com/Dweep4269/sideraven/issues/new?template=bug_report.yml), with your Sideraven version and Sidebar mode.
- Have an idea? [Request a feature](https://github.com/Dweep4269/sideraven/issues/new?template=feature_request.yml).
- Contributions are welcome. For anything big, open an issue first so we can agree on the approach.

---

## Credits

Sideraven builds on Mozilla's Firefox for Android, GeckoView and Android Components, and on the Iceraven fork. The Sidebar, Spaces and Essentials follow ideas from Zen Browser. The wordmark uses Space Mono by Colophon Foundry, under the SIL Open Font License. The A Short Hike app icon is a fan tribute to the game by adamgryu.

## Star history

<a href="https://star-history.com/#Dweep4269/sideraven&Date">
  <img src="https://api.star-history.com/svg?repos=Dweep4269/sideraven&type=Date" alt="Sideraven's GitHub stars over time" width="100%">
</a>

---

<p align="center">
  <sub>Sideraven is licensed under the <a href="LICENSE">Mozilla Public License 2.0</a>. It isn't affiliated with Mozilla, Zen, Iceraven or the makers of A Short Hike. Firefox is a trademark of the Mozilla Foundation, and Sideraven uses no Mozilla or Zen logos.</sub>
</p>
