# Settings redesign, ADB switch and optimization: design

Date: 2026-11-03 · Branch: `settings-redesign` (on top of `my-setup`, what runs on the TV now)
Device: Fire TV Stick 4K 2nd gen (AFTKM), Fire OS 8.1.8.2, Android 11

## Intent

- The launcher's Settings overlay is "chaotic, no hierarchy": four equal boxes, looks settings mixed with system shortcuts.
- The only system task the owner does from it is turning **ADB debugging** on/off, which today is Settings → My Fire TV → Developer Options → ADB (4+ presses inside Amazon's menus).
- The calendar on Home is useless to the owner (the phone does that).
- Goal: easier to use **and** lighter on a small device (low RAM, low storage). "Extreme optimization with the best of the animation and usability": nothing may get less smooth.
- For the owner's TV only. Saved on its own branch so it can become an upstream PR later if it proves useful.

## Goals

1. **Two-pane Settings** (option B from the mockups).
   - Left column: categories **Device**, **Appearance**, **About** (About: launcher version only). Focus starts on Device.
   - Right pane shows the selected category:
     - Device: **ADB debugging** switch (first, with live On/Off state), **Fire TV settings**, **Wi-Fi**. "Apps" is dropped.
     - Appearance: Wallpaper (Automatic/Day/Night), Dock (Ultra low/Balanced/Always visible), Top bar (Text + icon/Icon only). Each row shows its current value; OK cycles or opens its choices.
   - Navigation: Right enters the pane, Left/Back returns to the categories, Back on the categories closes Settings. Focus never escapes to Home (keep the existing focus trap). Home closes it.
   - All text in `strings.xml`.
2. **ADB switch.** The row reads `Settings.Global.ADB_ENABLED` when the pane opens and writes it on OK.
   - Needs `WRITE_SECURE_SETTINGS`, declared in the manifest and granted once over ADB: `adb shell pm grant com.gathin.tvhome android.permission.WRITE_SECURE_SETTINGS`.
   - Without the grant (e.g. after an uninstall) the row falls back to opening Amazon's Developer Options screen directly. Its exact component name is read from Amazon's settings APK with REA, during the ADB session.
   - Turning ADB off from the TV while connected drops the PC's connection. That is expected, not an error.
3. **Calendar removed.** The pill, overlay layout, `showCalendar`/`renderCalendar` code and its strings are deleted, not hidden. The top bar keeps Wi-Fi and Settings.
4. **Optimization.**
   - Release build with R8 code and resource shrinking (`minifyEnabled true`, `shrinkResources true`), signed with the same debug key as now so it installs over the current app. Target APK < 1 MB (now ~6 MB debug, 3.8 MB of it unused library code).
   - Remove dead code: calendar and the Stremio shelf, which never shows on this TV. `ClockDream` is kept and turned on (see Screensaver).
   - Settings overlay inflated on first open (ViewStub), not at startup.
   - No work while Home is hidden: the minute tick already stops in `onPause`; audit every `Handler`/animator for the same.
   - Wallpapers: one decoded at a time, at screen size; recompress if it saves size with no visible loss.
5. **Animations.** Keep every current one: dock rise/peek, glass focus spring, tile zoom, overlay fades, wallpaper crossfade. Animate only translation, scale and alpha (GPU-composited). Never animate layout properties. The Settings panes use the same 220 ms fade/slide timing as the existing overlays.

## Non-goals

- No new features beyond the above (no weather, no feeds, no network use).
- No changes to the Home redirect / accessibility service logic.
- Nothing pushed to GitHub unless the owner asks.

## Anti-goals (failure even if it works)

- Any animation that stutters or feels slower than today.
- Settings that need more presses than today for anything the owner uses.
- Higher RAM use or slower Home than the current build.

## Proof (measured on the TV, before and after)

- Baseline taken from the **current** build before installing the new one, in the same ADB session.
- RAM: `dumpsys meminfo com.gathin.tvhome` (TOTAL PSS) on Home, idle.
- Home speed: `am start -W` of the launcher from another app, 5 runs, median `TotalTime`.
- Smoothness: `dumpsys gfxinfo com.gathin.tvhome reset`, then dock up/down + 10 D-pad moves, then janky-frame %.
- APK size, plus a REA comparison of the two APKs (what's inside each).
- If any number is worse, that change is reverted before shipping.

## Constraints

- JDK 17, SDK 34, existing Gradle setup; plain Java, no new dependencies.
- Fire OS 8: Amazon's launcher stays installed; keep `letAmazonHomeThrough()` for the Fire TV settings / Wi-Fi rows.
- One ADB session at the end: baseline → REA pull of Amazon settings APK → install → grant → measure → screenshots.

## Left to the builder

- Exact pixel layout, sizes and colours (match the current glass style).
- Whether Appearance rows cycle on OK or open a small choice list (pick whichever needs fewer presses).
- Keep rules for R8 (manifest components are kept automatically).

## Screensaver (owner: "I don't want the Amazon one")

- Register the existing, unused `ClockDream` (clock and date drifting on black over a slow glow, no images, no network) as a `DreamService` in the manifest, with `BIND_DREAM_SERVICE`.
- Make it the system screensaver over ADB: `settings put secure screensaver_components com.gathin.tvhome/.ClockDream` and `screensaver_enabled 1`. Fire OS's own sleep timer stays as it is: clock screensaver first, then sleep.
- Its strings go to `strings.xml`. The glow moves once a minute, with no per-frame work.
- **Risk:** Fire OS may ignore the standard screensaver setting and keep starting Amazon's (`com.amazon.ftv.screensaver`). Verify on the TV by forcing it (`am start -n com.android.systemui/.Somnambulator` or waiting for idle). If Fire OS ignores it, disable Amazon's screensaver package so the TV just goes black, then sleeps. That's reversible with `pm enable`, and goes in the debloat notes.

## Added after approval (2026-11-03)

- **Apple TV feel** (approved on the preview page): critically damped springs for focus (~0.22 s), a 4° lean toward the D-pad direction that settles flat, deeper shadow on focus, press-in on OK, dock on a spring, Settings rows plain until focused, the panel materializes (scale 0.98 → 1 + fade, 220 ms in / 160 ms out), panes slide from the direction of travel. All motion respects the TV's animation scale (off = no motion).
- **Language** (Settings > Appearance > Language): English, Italiano, 中文, independent of the TV's language; until chosen it follows the TV if that is one of the three. Screensaver date follows it.
- **Screensaver fallback:** if Fire OS refuses or ignores `screensaver_components`, keep Amazon's screensaver (owner's call), revert the setting, and don't disable Amazon's package.
- **Open risk:** Chinese needs a CJK system font; check `/system/fonts` in the device session.

## Results (device session, 2026-11-04, AFTKM, Fire OS 8.1.8.2)

- **Installed** release build (747,773 B, R8 + shrunk resources; old debug build 3,555,147 B). Accessibility bound, WRITE_SECURE_SETTINGS granted.
- **ADB switch:** works for real. `Settings.Global.ADB_ENABLED` write accepted; pressing it dropped the PC's connection and reconnect was refused.
- **Developer Options fallback** (found with REA `pull_adb_package` + aapt2 manifest dump; REA's class search needed a JADX jar that isn't installed): `com.amazon.tv.settings.v2/.tv.device.DeviceActivity` is exported and handles `android.settings.APPLICATION_DEVELOPMENT_SETTINGS`, landing on Debug ADB. Set in `developer_options_component`.
- **Screensaver:** Fire OS honours `screensaver_components`; `dumpsys dreams` shows `ClockDream` running. Amazon's package untouched.
- **Language:** it / zh / en all render; Chinese uses the system CJK font (nothing bundled). Settings reopens on the Language row after the switch.
- **Functional:** two-pane navigation, value cycling, Fire TV settings and Wi-Fi open; no calendar pill.
- **Measurements** (old = installed debug build, new = release):

| Metric | Old | New |
| --- | --- | --- |
| RAM, fresh process, 40 s on Home (TOTAL PSS) | 84.5 MB | 78.3 MB |
| Home from Prime Video, `am start -W` hot, median of 5 | 151 ms | 120 ms |
| Frame time p50 / p90 / p99 (D-pad sweep) | 17 / 26 / 40 ms | 18 / 25 / 29 ms |
| Janky frames (gfxinfo) | 78.6 % | 79.5 % |

- APK manifest diff (aapt2, in place of REA `compare_application_versions`, which only handles JavaScript graphs): −INTERNET, +WRITE_SECURE_SETTINGS, +dream component, no longer debuggable.
- **Known, not fixed:**
  - Returning from Fire TV settings / Wi-Fi lands on Home, not the Settings panel (the Home redirect delivers a new intent, which closes the panel).
  - After a language switch the old activity's bitmaps sit in native heap until the next GC (seen at 122 MB right after three switches; no static reference holds it).
  - Graphics memory is ~9 MB higher than the old build on a fresh start.
  - Reinstalling an older build that lacks WRITE_SECURE_SETTINGS drops the grant; `build-and-install-firetv.sh` re-grants it.
