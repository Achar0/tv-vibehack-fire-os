# tv-vibehack for Fire OS

A clean, Apple TV–style home screen that actually takes over **Home on an Amazon Fire TV Stick**,
plus an ad-free debloat list for it.

This is an alternative version built on two projects:

- [**benfirad/tv-vibehack**](https://github.com/benfirad/tv-vibehack): the launcher this repo was forked from.
- [**Gathin23/cleanyourtv**](https://github.com/Gathin23/cleanyourtv): the original project tv-vibehack forked.

Both are MIT-licensed; so is this. The original README is kept in [`docs/upstream-README.md`](docs/upstream-README.md).

Tested on a Fire TV Stick 4K (`AFTKM`), Fire OS 8.1.8.2 (Android 11).

| Home | Dock |
|---|---|
| ![Home screen, dock lowered](docs/images/home.jpg) | ![Dock raised, glass highlight on a tile](docs/images/dock.jpg) |
| **Settings: Device** | **Settings: Appearance** |
| ![Two-pane settings, Device: Debug ADB switch](docs/images/settings-device.jpg) | ![Two-pane settings, Appearance: wallpaper, dock, top bar, language](docs/images/settings-appearance-en.jpg) |
| **中文** | **Clock screensaver** |
| ![Settings in Simplified Chinese](docs/images/settings-zh.jpg) | ![Clock screensaver](docs/images/screensaver.jpg) |

## What it does on Fire OS

- **Takes Home back from Amazon.** Fire OS won't let another app become the default home and protects
  its own launcher, so an accessibility service brings this launcher to the front whenever Amazon's home
  appears.
  - The switch happens at once, with no window animation. The launcher has focus about 0.28 s after
  Amazon's home starts (was 0.89 s).
  - Fire OS keeps the Home key itself from apps (checked with a key-logging build: Down, Right and Back
  arrive, Home never does), so Amazon's window appearing is the earliest possible signal.
- **Fire OS settings still open.** Fire OS opens Settings through Amazon's home, so the launcher lets
  that one pass through.
  - Back from Fire TV settings or Wi-Fi returns to the launcher's Settings panel; Home goes to the home screen.
- **Two-pane Settings** with a hierarchy: Device / Appearance / About.
  - **Debug ADB is a real switch.** It flips `adb_enabled` directly (the install script grants
  `WRITE_SECURE_SETTINGS`).
  - Without that permission, it opens Amazon's Developer Options straight on the Debug ADB row. That
  screen was found by reverse-engineering Amazon's Settings app.
- **Clock screensaver** replaces Amazon's, using the same idle timer.
- **Launcher language**: English, Italiano, 中文 (Simplified), independent of the TV's language.
- **Apple TV–style motion**: critically damped springs, a slight tilt toward the D-pad direction,
  press-in on OK. Only transform and opacity are animated, and the TV's animation scale is respected.
- **Small**: release build with R8 and resource shrinking, **≈ 0.75 MB** (the debug build was 3.6 MB).
  No INTERNET permission.
- **Debloat list** for the AFTKM: [`examples/amazon-fire-tv-stick-4k-aftkm/debloat.txt`](examples/amazon-fire-tv-stick-4k-aftkm/debloat.txt).
  - 21 packages, disabled and never uninstalled.

## Compared with cleanyourtv

cleanyourtv (v3.3, Nov 2026) is the far bigger project: hundreds of TV models, 31 languages, themes and
add-ons. On Fire TV, though, its README says it does **"the debloat half only, since Fire OS will not hand
Home to a third-party launcher"**. That missing half is what this version adds.

| | cleanyourtv 3.3 | This version |
|---|---|---|
| **Scope** | Any Android / Google TV; Fire TV debloat lists for many models | Fire TV Stick 4K (AFTKM), one owner's setup |
| **Home on Fire TV** | Not possible, debloat only | Taken over via the accessibility redirect |
| **Home redirect speed** | n/a | Launcher focused ~0.28 s after Amazon's home starts |
| **Fire OS settings from the launcher** | n/a on Fire TV | Pass-through; Back returns to the Settings panel |
| **Settings on the TV** | Hold OK on Settings → launcher settings page | Two-pane panel: Device / Appearance / About |
| **ADB on/off** | Through the TV's own menus | One switch in Settings (`WRITE_SECURE_SETTINGS`); falls back to Amazon's Debug ADB screen |
| **Languages** | 31, follows the TV, right-to-left for Hebrew | 3 chosen in the launcher (en / it / zh) + German strings from tv-vibehack |
| **Themes / module cards / add-ons** | Yes (21 themes, cards, add-ons) | No |
| **App library, tile menu (move / remove)** | Yes | Long-press = App info only |
| **Inputs (HDMI / AV / Live TV)** | Yes | Hidden: a stick has none |
| **Clock screensaver** | Drifting clock | Clock with a slow colour glow; replaces Amazon's |
| **Size** | ≈ 572 KB | ≈ 0.75 MB (includes 1376×768 wallpapers) |
| **Network permission** | None | None |
| **Rollback** | `tools/restore.*` | Same, plus [`tools/rollback-firetv.sh`](tools/rollback-firetv.sh): reinstalls a saved earlier build and the old screensaver settings |

**In short:** on any Android or Google TV, use cleanyourtv. On a Fire TV, cleanyourtv's debloat list plus
this launcher gives you the home screen back.

## Install (Fire TV)

1. On the TV: Settings → My Fire TV → About → press OK on the device name 7 times → Developer Options →
   **ADB debugging** on.
2. Debloat: see [`docs/fire-tv.md`](docs/fire-tv.md).
3. Build and install (JDK 17, Android SDK 34):

   ```bash
   TV_HOST=<fire-tv-ip> launcher/build-and-install-firetv.sh
   ```

   It builds the release APK, installs it, grants `WRITE_SECURE_SETTINGS`, sets the Clock screensaver
   and enables the Home override.
4. Turn ADB off afterwards: Settings → Device → Debug ADB.

Personal choices live in [`launcher/app/src/main/res/values/config.xml`](launcher/app/src/main/res/values/config.xml):
dock apps, hidden apps, columns.

## How it was made

The full account, including measurements, the upstream contributions and what is still open, is in
[`docs/what-we-did.md`](docs/what-we-did.md).
