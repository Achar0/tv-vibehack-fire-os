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
   - Remove dead code: calendar, unregistered `ClockDream` (unless the screensaver question below keeps it), and the Stremio shelf, which never shows on this TV.
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

## Open questions for the owner

- **Screensaver:** Fire OS shows Amazon's screensaver when idle, then sleeps. The launcher already contains an unused clock screensaver (`ClockDream`: clock and date drifting on black, no images, no network). Use it instead of Amazon's, or leave Amazon's alone and delete `ClockDream`?
