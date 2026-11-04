# What we did

How a Fire TV Stick 4K went from Amazon's home screen to this launcher, in order. Dates are 2026.
Everything was done from a Linux PC over ADB, with Claude Code doing the work and the owner making the calls.

## 1. Starting point

- **Device:** Fire TV Stick 4K, `AFTKM` (codename `karat`), Fire OS 8.1.8.2 = Android 11 (API 30), 1920×1080.
- **Goal:** no ads, no sponsored rows, a calm home screen, without breaking anything Amazon needs.
- **Base:** [benfirad/tv-vibehack](https://github.com/benfirad/tv-vibehack), a fork of
  [Gathin23/cleanyourtv](https://github.com/Gathin23/cleanyourtv): a debloat list per TV plus a small
  tvOS-style launcher.
- **Problem:** both target Android TV, where the launcher is set as default home. Fire OS refuses that.

## 2. Debloat (Nov 3)

- A backup of the device state (package list, settings, `getprop`) was taken before any change and kept
  off the repo (it contains device IDs).
- **21 Amazon packages disabled** with `pm disable-user`, never uninstalled, so every one can come back
  with `pm enable`: [`examples/amazon-fire-tv-stick-4k-aftkm/debloat.txt`](../examples/amazon-fire-tv-stick-4k-aftkm/debloat.txt).
- Findings worth knowing:
  - `com.amazon.logan` can be disabled. cleanyourtv's list keeps it as "accessibility", yet the launcher's
    accessibility service keeps working without it.
  - `com.amazon.vizzini` (Alexa voice UI) is re-enabled by Fire OS at every boot; disabling it does not stick.
  - Appstore auto-updates re-enable apps they update (seen with Amazon Music): re-run the list after updates.

## 3. Making tv-vibehack work on Fire OS (Nov 3)

| Android TV way | Blocked on Fire OS | What we did instead |
|---|---|---|
| `set-home-activity` + disable the stock launcher | `com.amazon.tv.launcher` is protected | An accessibility service brings the launcher back whenever Amazon's home window appears |
| Catch the Home key | Fire OS handles Home before any app | React to the window instead |
| Open Fire OS settings | Fire OS opens them *through* Amazon's home, which the redirect would bounce | The launcher tells the service to let that one pass through |

- Plus four fixes needed along the way: duplicate image resources (the build was broken), settings strings
  moved to `strings.xml`, a D-pad focus trap for overlays, and the Stremio shelf only when Stremio is installed.

## 4. Upstream contributions

- **tv-vibehack:** five pull requests, still open at the time of writing:
  - [#1](https://github.com/benfirad/tv-vibehack/pull/1) fix: duplicate image resources (build was broken).
  - [#2](https://github.com/benfirad/tv-vibehack/pull/2) i18n: settings overlay strings.
  - [#3](https://github.com/benfirad/tv-vibehack/pull/3) fix: D-pad focus trap; Home closes overlays.
  - [#4](https://github.com/benfirad/tv-vibehack/pull/4) feat: Stremio shelf only if installed.
  - [#5](https://github.com/benfirad/tv-vibehack/pull/5) feat: Amazon Fire TV support.
- **cleanyourtv:** a SHOWCASE row for the Fire OS 8 Home redirect with Settings pass-through,
  [#3204](https://github.com/Gathin23/cleanyourtv/pull/3204), **merged Nov 4**. It links to the `fire-tv` branch of
  [Achar0/tv-vibehack](https://github.com/Achar0/tv-vibehack/tree/fire-tv).
- **cleanyourtv, the launcher on Fire OS:**
  [#3276](https://github.com/Gathin23/cleanyourtv/pull/3276), open, waiting for a maintainer decision (CI green).
  - Testing cleanyourtv's own launcher on this stick (installed next to this one under a test package name) showed
    that its `stock_home_redirect` already works on Fire OS: Home redirected 0.14–0.36 s after Amazon's home, and its
    Settings and Wi-Fi buttons open and return with Back, because it starts Amazon's `MainSettingsActivity` directly.
  - What stopped it was the install script. With `STOCK_LAUNCHER=none` it requires Home to resolve to the new
    launcher, and on Fire OS Amazon's launcher always wins (priority 950; `set-home-activity` answers "Success" and
    changes nothing). So the script stopped before enabling the redirect service.
  - The PR lets the script accept that state when `HOME_KEY_SERVICE=1` is set (bash and PowerShell). It adds two
    simulated-TV tests (2864/2864 pass) and updates the AFTKM list's docs. It ran end to end on this stick.
  - It touches the same test helper as #3005 (upgrades on Google TV redirect setups); the two are complementary.
- **Planned:** after a few days of use, add the AFTKM as a second unit to cleanyourtv's
  `amazon-fire-tv-stick-4k-2023` example, with the findings in section 2.

## 5. The redesign (Nov 3–4)

**Why:** after a first day of use, the Settings overlay felt chaotic, with no hierarchy. Turning ADB off
meant digging through Amazon's menus. The calendar was useless next to a phone. And the stick is small
(about 1 GB of RAM), so every addition had to pay for itself.

**How:** brainstorm → written design ([`specs/2026-11-03-settings-redesign-design.md`](superpowers/specs/2026-11-03-settings-redesign-design.md))
→ implementation plan ([`plans/2026-11-03-settings-redesign.md`](superpowers/plans/2026-11-03-settings-redesign.md))
→ build task by task, with unit tests for the logic → three rounds of code review (2 + 10 + 10 findings, all
fixed) → an HTML preview of the new look → the on-device session.

What changed:

- **Settings has a hierarchy:** a two-pane panel with Device / Appearance / About on the left and their rows
  on the right.
  - It is inflated from a `ViewStub` on first open, so it costs nothing until it is used.
- **ADB switch:** a real on/off row.
  - The install script grants `WRITE_SECURE_SETTINGS`, so the launcher writes `adb_enabled` itself.
  - Without the grant it opens Amazon's Developer Options. The exact screen,
  `com.amazon.tv.settings.v2/.tv.device.DeviceActivity` with `APPLICATION_DEVELOPMENT_SETTINGS`, was found by
  pulling Amazon's Settings APK with REA and reading its manifest; it lands right on Debug ADB.
- **Calendar removed**, with the Stremio code and the INTERNET permission.
- **Clock screensaver** (`ClockDream`) set as the system screensaver.
  - If Fire OS had refused it, the plan was to keep Amazon's (owner's call); it didn't refuse.
- **Language:** English / Italiano / 中文, chosen in the launcher.
  - It is applied per activity, and `Locale.setDefault` makes the clock's day and month names follow it.
  - Traditional-Chinese TVs are not forced into Simplified.
- **Apple TV–style motion:**
  - Critically damped springs for focus, a 4° lean toward the D-pad direction that settles flat, press-in on OK.
  - The dock moves on a spring; the panel scales 0.98 → 1 and fades in.
  - Only transform and opacity are animated, and the TV's animation scale is respected.
- **Optimisation:** release build with R8 + resource shrinking, wallpapers recompressed to JPEG q85 (no
  visible difference, SSIM ≈ 0.98). The APK went from 3.6 MB (debug) to 0.75 MB.
- **Safety net:** before installing, the running build and the old screensaver settings were saved, with a
  one-command rollback ([`tools/rollback-firetv.sh`](../tools/rollback-firetv.sh)). It was never needed.

## 6. On-device results (Nov 4)

| Metric | Before | After |
|---|---|---|
| APK size | 3.56 MB | 0.75 MB |
| RAM, fresh start, 40 s on Home (PSS) | 84.5 MB | 78.3 MB |
| Home from Prime Video (`am start -W`, median of 5) | 151 ms | 120 ms |
| Frame time p99 during a D-pad sweep | 40 ms | 29 ms |
| Amazon's home on screen after pressing Home | ≈ 0.89 s | ≈ 0.28 s |

- ADB switch: pressing it cut the PC's ADB connection, and reconnecting was refused.
- Screensaver: `dumpsys dreams` shows the launcher's clock, and it started by itself on idle.
- Chinese renders with the system CJK font, so no font had to be bundled.

## 7. Fixes after living with it (Nov 4)

- **Amazon's home showed for almost a second on Home.**
  - A key-logging test build showed that Down, Right and Back reach the accessibility service, but Home never does.
  - The redirect now fires as soon as Amazon's window appears (the 150 ms wait is gone), with no window animation.
- **Back from Fire TV settings closed the launcher's Settings.**
  - Since Back *is* visible, a Back just before Amazon's home appears marks the return as "back", and Settings
  stays open if it opened that screen. Home still closes it.
- **A blank frame flashed under the clock on Down from the top bar.**
  - The focus highlight subtracted the dock's slide offset from a position that never included it.
  - It now appears only once the dock has settled, directly on the tile.

## 8. More debloat (Nov 4)

- Compared with cleanyourtv's community list for the same model: 37 more Amazon packages (Alexa extras, telemetry,
  Live tab, launcher ad bidding, upgrade promos, `wifilocker`) were still active. All 37 disabled; 58 in total.
- About 120 MB of them was resident before (PSS); used RAM after a reboot is about 165 MB lower than before.
- Survived a reboot with nothing re-enabled; Home, Fire TV settings, YouTube and Prime Video work; no crashes.
- Before applying it, the current state was saved and an undo script written (`pm enable` for each of the 37).
  The first run had a bug: `adb shell` inside a `while read` loop swallowed the list, so only one package was
  disabled. It was caught by checking the TV (`pm list packages -d`), not the script's own output, and re-run with
  `</dev/null`. The undo script had the same bug and was fixed too.
- A warning carried over from cleanyourtv (#2950): one AFTKM with about 50 packages disabled stopped opening Settings
  and most apps after a Fire OS update. Re-enable everything before an update, or keep the restore ready.

## 9. Can Amazon's home be removed? (Nov 4)

Amazon's launcher (`com.amazon.tv.launcher`) is the biggest process left: about 114 MB, always resident. It also
contains Amazon's Settings screens (`MainSettingsActivity`). Every route was checked:

| Route | Result |
|---|---|
| Disable the package | Refused: "Cannot disable a protected package" |
| Disable only its home screen (`.ui.HomeActivity_vNext`) | Refused with the same error: the protection covers components too |
| Make another app the default Home (`set-home-activity`) | Answers "Success", but Home still resolves to Amazon's (priority 950) |
| Hide or suspend the package | Not tried: it would take Amazon's Settings screens down with it |
| Install a modified Amazon launcher as an update | Impossible: it's a system app signed with Amazon's key, and Android only accepts updates signed with the same key |
| A modified Fire OS update | Impossible: the stick checks Amazon's signature on every system update |
| Root / unlocked bootloader | No public unlock for the AFTKM; it would also risk bricking it and break protected streaming |
| Restrict it from running in the background | Accepted, but no gain (below), so it was undone |

The background-restriction test ran twice for 10 minutes each: three Home presses, then idle on this launcher.

| Amazon's launcher | Before | With `RUN_ANY_IN_BACKGROUND ignore` |
|---|---|---|
| CPU used | 0.89 s | 0.97 s |
| Memory (PSS) | 106 MB | 103 MB |
| Log lines written | 273 | 308 |

- The differences are within normal variation. Idle, Amazon's launcher already does almost nothing (under 0.2% of
  one core). Its memory is code and data it keeps loaded, which a background restriction doesn't free.
- Fire OS also ignored `am set-standby-bucket … restricted`: as the home app it stays in the active bucket.
- **Conclusion:** the redirect plus the debloat is as far as a locked stick goes.

## 10. Known limits

- After switching language, the old screen's bitmaps stay in memory until the next garbage collection.
  This only happens on a language change, which is rare.
- Graphics memory is about 9 MB above the old build at a fresh start; total RAM is still lower.
- Reinstalling an older build that lacks `WRITE_SECURE_SETTINGS` drops the grant;
  `build-and-install-firetv.sh` re-grants it.
- Amazon's home can still flash briefly on Home: Fire OS gives apps no earlier signal than its window.
