# Settings Redesign, ADB Switch and Optimization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A two-pane Settings screen with a one-press ADB switch, no calendar, the launcher's own clock screensaver, and a smaller, lighter release build, measured on the Fire TV.

**Architecture:** The settings overlay moves out of `MainActivity` into a focused `SettingsPanel` class, inflated on first open from a `ViewStub`. ADB state lives in a tiny `AdbSwitch` helper. Dead features (calendar, Stremio shelf) are deleted. The build switches to a shrunk release APK signed with the existing debug key.

**Tech Stack:** Java 17, Android Gradle plugin (existing), compileSdk 34 / minSdk 26, AndroidX RecyclerView + DynamicAnimation (existing only), JUnit 4 as `testImplementation` (test-only, not shipped).

**Spec:** `docs/superpowers/specs/2026-11-03-settings-redesign-design.md`

## Global Constraints

- Plain Java, no new runtime dependencies. JUnit is test-only.
- Every user-visible string in `res/values/strings.xml`. Code comments in English.
- Animate only translation, scale and alpha. The Settings panes use the overlay timing: 220 ms in, 180 ms out.
- No `Handler`/animator runs while the launcher is paused.
- Keep `TvAccessibilityService` and `letAmazonHomeThrough()` behavior unchanged.
- Release APK target < 1 MB, signed with the debug key so it installs over the current app.
- Nothing pushed to GitHub. Local commits on `settings-redesign` only.
- Build check after every task: `cd launcher && ANDROID_HOME=~/Android/Sdk ./gradlew --no-daemon -q assembleDebug` (from Task 6 on, `assembleRelease`), expected: exit 0.

## Review Focus

- **ADB permission not granted** (fresh install, grant forgotten): the row must not crash or pretend to switch. It shows the real state and opens Developer Options instead. Pinned in Task 4.
- **ADB switched off while the PC is connected**: the write succeeds and the badge updates. The dropped connection is expected. Pinned in Task 4 (badge re-read after write).
- **Home pressed with Settings open on the Appearance pane**: Settings closes, and next open starts on Device again. Pinned in Task 3.
- **Back on the right pane**: returns to the category list, it does not close Settings. Back on the categories closes it. Pinned in Task 3.
- **R8 strips something reached only by reflection or the manifest** (accessibility service, dream service, `GlassView` in XML): the release build must still launch and bind. Pinned in Task 6 (on-device smoke check in Task 7).

---

### Task 1: `Choices` helper with unit tests

**Files:**
- Create: `launcher/app/src/main/java/com/gathin/tvhome/Choices.java`
- Create: `launcher/app/src/test/java/com/gathin/tvhome/ChoicesTest.java`
- Modify: `launcher/app/build.gradle` (add `testImplementation 'junit:junit:4.13.2'`)

**Interfaces:**
- Produces: `static String Choices.next(String[] order, String current)`. Returns the value after `current`, wrapping to `order[0]`. An unknown or null `current` returns `order[0]`.

- [ ] **Step 1: Write the failing tests** in `ChoicesTest`:
  - `next({"auto","day","night"}, "auto") == "day"`
  - `next(..., "night") == "auto"` (wraps)
  - `next(..., "bogus") == "auto"`
  - `next(..., null) == "auto"`
- [ ] **Step 2:** Run `./gradlew --no-daemon -q testDebugUnitTest`. Expected: compile FAIL (`Choices` missing).
- [ ] **Step 3:** Implement `Choices.next`.
- [ ] **Step 4:** Run the same command. Expected: PASS.
- [ ] **Step 5:** Commit: `feat(launcher): Choices helper for cycling setting values`.

### Task 2: Delete the calendar and the Stremio shelf

**Files:**
- Delete: `res/layout/overlay_calendar.xml`, `res/layout/item_cal_day.xml`, `res/drawable/cal_day_bg.xml`, `res/drawable/ic_calendar.xml`, `res/drawable/banner_stremio_*.xml` (5)
- Modify: `res/layout/activity_main.xml` (remove `btnCalendar` and the `overlay_calendar` include)
- Modify: `MainActivity.java`:
  - Remove the calendar section (`showCalendar`, `hideCalendar`, `renderCalendar`, `OCT_EVENTS`, `calendarOverlay`), `launchStremio`, and the media-key branch in `dispatchKeyEvent`.
  - Remove `btnCalendar` from `applyTopBarStyle` and the pill key listener.
  - The initial focus in `onResume` goes to `btnWifi`.
- Modify: `AppAdapter.java` (remove the Stremio row, plus the `isStremio`/`stremioType`/`stremioId` fields of `Entry`)
- Modify: `res/values/strings.xml`, `res/values-de/strings.xml` (remove the now-unused calendar/Stremio strings)

**Interfaces:**
- Produces: the top bar has exactly two pills, `btnWifi` and `btnSettings`. `openOverlay()` only knows `settingsOverlay`.

- [ ] **Step 1:** Make the deletions.
- [ ] **Step 2:** Run `grep -rniE "calendar|stremio" app/src/main --include=*.java --include=*.xml`. Expected: no hits except `java.util.Calendar` uses in `updateGreeting`/`updateWallpaper`.
- [ ] **Step 3:** Run the build check. Expected: exit 0, and `lintDebug` shows no `UnusedResources` for the removed names.
- [ ] **Step 4:** Commit: `refactor(launcher): remove calendar and Stremio shelf`.

### Task 3: Two-pane `SettingsPanel`

**Files:**
- Create: `java/com/gathin/tvhome/SettingsPanel.java`
- Replace: `res/layout/overlay_settings.xml` (two-pane layout)
- Modify: `res/layout/activity_main.xml` (`<ViewStub android:id="@+id/settingsStub" android:layout="@layout/overlay_settings" android:inflatedId="@+id/settingsOverlay">` replaces the include)
- Modify: `MainActivity.java` (delete `showSettings`/`hideSettings`/`updateSettingsUI` and the `btn*` fields; delegate to the panel; `openOverlay()` returns `panel.isShowing() ? panel.root() : null`)
- Modify: `res/values/strings.xml`

**Interfaces:**
- Consumes: `Choices.next` (Task 1).
- Produces:
  - `SettingsPanel(Activity activity, ViewStub stub, SettingsPanel.Host host)`
  - `interface Host { boolean topBarShowsText(); void setTopBarStyle(boolean showText); String peekDepth(); void setPeekDepth(String depth); String wallpaperTheme(); void setWallpaperTheme(String theme); void openSystem(Intent intent); }`. `MainActivity` implements it with its existing private setters, made package-private. `openSystem` calls the existing `open(Intent)`, so `letAmazonHomeThrough()` still applies.
  - `void show()`, `void hide(boolean animate)`, `boolean isShowing()`, `View root()`, `boolean onBack()` (returns true if it consumed Back).

Layout and behavior, from the spec:
- **Left column** (about 32% width): rows `catDevice`, `catAppearance`, `catAbout`.
- **Right pane** (`FrameLayout`) holds three child groups:
  - `paneDevice`: rows `rowAdb` (label + state badge `adbState`), `rowFireSettings`, `rowWifi`.
  - `paneAppearance`: rows `rowWallpaper`, `rowDock`, `rowTopBar`, each showing its current value on the right.
  - `paneAbout`: one text, launcher version from `PackageInfo.versionName`.
- **Focus moving to a category** shows its pane (alpha 0→1, translationX 24dp→0, 220 ms).
- **D-pad Right on a category**, or OK, focuses the first row of its pane.
- **Back on a pane row** focuses its category. **Back on a category** hides the panel.
- **`show()`** always focuses `catDevice` and shows `paneDevice`.
- **Appearance rows cycle on OK** with `Choices.next`, in these orders:
  - wallpaper `{"auto","day","night"}`
  - dock `{"ultra","balanced","none"}`
  - top bar `{text, icon}`
- **Value labels** come from strings: Automatic / Day / Night, Ultra low / Balanced / Always visible, Text + icon / Icon only.
- **Style:** reuse the existing `glass_panel`, `setting_card_bg`, `setting_option_bg` drawables and the `outfit` font.

- [ ] **Step 1:** Write the layout and `SettingsPanel`, and wire `MainActivity`:
  - `dispatchKeyEvent`: Back while `panel.isShowing()` goes to `panel.onBack()`.
  - `onNewIntent`: `panel.hide(false)`.
  - The `btnSettings` click calls `panel.show()`.
- [ ] **Step 2:** Run the build check. Expected: exit 0.
- [ ] **Step 3:** Run `grep -c settingsStub app/src/main/res/layout/activity_main.xml`. Expected: `1`. The overlay is no longer inflated at startup.
- [ ] **Step 4:** Commit: `feat(launcher): two-pane settings with Device / Appearance / About`.

### Task 4: ADB switch

**Files:**
- Create: `java/com/gathin/tvhome/AdbSwitch.java`
- Modify: `AndroidManifest.xml` (`<uses-permission android:name="android.permission.WRITE_SECURE_SETTINGS" tools:ignore="ProtectedPermissions"/>`)
- Modify: `res/values/config.xml` (`<string name="developer_options_component" translatable="false"></string>`, filled in Task 7)
- Modify: `SettingsPanel.java` (the `rowAdb` click), `res/values/strings.xml` (`adb_on` "On", `adb_off` "Off", `adb_no_permission_toast`)

**Interfaces:**
- Produces:
  - `static boolean AdbSwitch.isEnabled(Context)`: reads `Settings.Global.ADB_ENABLED == 1`.
  - `static boolean AdbSwitch.canWrite(Context)`: `checkSelfPermission(WRITE_SECURE_SETTINGS) == GRANTED`.
  - `static boolean AdbSwitch.set(Context, boolean on)`: `Settings.Global.putInt`. Returns false on `SecurityException`.
  - `static Intent AdbSwitch.developerOptionsIntent(Context)`: the component from config if non-empty, else `Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS`.

Behavior:
- **Badge:** read when the Device pane is shown.
- **OK when `canWrite`:** `set(!isEnabled)`, then re-read and update the badge.
- **OK otherwise:** show the toast once, then `host.openSystem(developerOptionsIntent)`.
- The badge always shows the **re-read** state, never the requested one.

- [ ] **Step 1:** Implement.
- [ ] **Step 2:** Run the build check. Expected: exit 0.
- [ ] **Step 3:** Run `aapt2 dump permissions` on the APK (from `~/Android/Sdk/build-tools/34.0.0/aapt2`). Expected: it lists `android.permission.WRITE_SECURE_SETTINGS`.
- [ ] **Step 4:** Commit: `feat(launcher): ADB debugging switch in Settings > Device`.

### Task 5: Clock screensaver

**Files:**
- Modify: `AndroidManifest.xml`:
  - Add `<service android:name=".ClockDream" android:exported="true" android:label="@string/screensaver_name" android:permission="android.permission.BIND_DREAM_SERVICE">` with an intent-filter `android.service.dreams.DreamService` (category DEFAULT).
- Modify: `ClockDream.java`:
  - Move any hard-coded text to strings.
  - Confirm its handler callbacks are removed in `onDreamingStopped`/`onDetachedFromWindow`.
- Modify: `res/values/strings.xml` (`screensaver_name` "Clock")

**Interfaces:**
- Produces: component `com.gathin.tvhome/.ClockDream`, used by Task 7.

- [ ] **Step 1:** Implement.
- [ ] **Step 2:** Run the build check, then run `aapt2 dump xmltree --file AndroidManifest.xml` on the APK and grep for `ClockDream`. Expected: one service entry.
- [ ] **Step 3:** Commit: `feat(launcher): register the clock screensaver`.

### Task 6: Shrunk release build

**Files:**
- Modify: `launcher/app/build.gradle`:
  - `release { minifyEnabled true; shrinkResources true; proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'; signingConfig signingConfigs.debug }`
- Create: `launcher/app/proguard-rules.pro` (keep `GlassView` constructors and any class referenced only from XML; manifest components are kept by AGP)
- Modify: `launcher/build-and-install-firetv.sh`:
  - Build with `assembleRelease` and install `app/build/outputs/apk/release/app-release.apk`.
  - Then run `pm grant com.gathin.tvhome android.permission.WRITE_SECURE_SETTINGS || true`.
  - Then run `settings put secure screensaver_components com.gathin.tvhome/.ClockDream` and `settings put secure screensaver_enabled 1`.
- Optional, same task: recompress `wallpaper_day.jpg` / `wallpaper_night.jpg` (quality 85, same 1376×768). Keep it only if the size drops more than 20% and a side-by-side shows no visible difference.

- [ ] **Step 1:** Implement.
- [ ] **Step 2:** Run `./gradlew --no-daemon -q assembleRelease && stat -c%s app/build/outputs/apk/release/app-release.apk`. Expected: exit 0 and fewer than 1,048,576 bytes.
- [ ] **Step 3:** Run `apksigner verify --print-certs` on the release APK and on a fresh debug APK. Expected: same SHA-256 certificate digest.
- [ ] **Step 4:** Run `testReleaseUnitTest`. Expected: PASS.
- [ ] **Step 4b: Background audit.** Run `grep -nE "postDelayed|\.post\(|start\(\)" app/src/main/java -r`. For every repeating callback or infinite animator, confirm it is cancelled in `onPause`/`onStop` (or in `onDreamingStopped` for `ClockDream`), and add the missing cancels. Expected: none left running while paused.
- [ ] **Step 5:** Commit: `build(launcher): shrunk release build signed with the debug key`.

### Task 7: On-device session (needs ADB on, owner present)

All `adb` commands target `-s <TV>:5555`. Results go into a `## Results` section appended to the spec.

- [ ] **Step 1: Baseline, on the OLD build still installed.**
  - RAM: run `dumpsys meminfo com.gathin.tvhome` on Home, after 30 s idle, and record TOTAL PSS.
  - Home speed: 5× (open Prime Video, then `am start -W -n com.gathin.tvhome/.MainActivity`). Record the median `TotalTime`.
  - Smoothness: `dumpsys gfxinfo com.gathin.tvhome reset`, then `input keyevent` DPAD_DOWN, DPAD_UP, DPAD_DOWN and 10× DPAD_RIGHT/LEFT, then `dumpsys gfxinfo`. Record the janky-frames %.
  - Pull the installed APK (`pm path`) as `old.apk`.
- [ ] **Step 2: REA.**
  - Pull Amazon's settings APK (`pm path com.amazon.tv.settings.v2`).
  - Use REA (`inspect_android_package` / `search_android_classes`) to find the exported Developer Options activity, and confirm it with `am start -n <component>` on the TV.
  - Put the component in `developer_options_component` and rebuild.
  - Run REA `compare_application_versions` on `old.apk` vs the new release APK, and keep its summary.
- [ ] **Step 3: Install.** Run `TV_HOST=<ip> launcher/build-and-install-firetv.sh`. Expected: "Installed.", and `dumpsys accessibility` shows the service bound.
- [ ] **Step 4: Functional checks, screenshot each.**
  - Settings opens on Device, with the ADB badge showing "On".
  - Right/Left/Back behave as in Task 3.
  - Home with Settings open closes it.
  - Appearance rows cycle and apply.
  - Fire TV settings and Wi-Fi open and Back returns.
  - No calendar pill.
- [ ] **Step 5: Screensaver.** Run `am start -n com.android.systemui/.Somnambulator`. Expected: the clock appears.
  - If Amazon's screensaver shows instead, run `pm disable-user --user 0 com.amazon.ftv.screensaver` and note it in the debloat notes.
- [ ] **Step 6: Measure again** as in Step 1. Any metric worse than the baseline: find which task caused it and revert that change before continuing.
- [ ] **Step 7: ADB switch last.** Press ADB on the TV. Expected: the badge shows "Off" and the PC's connection drops.
- [ ] **Step 8:** Update `~/Desktop/FireTV-Setup/FireTV-Setup.md` (new Settings screenshots, ADB switch, screensaver, results), save a new bundle to `~/.local/share/firetv-debloat/`, and commit `docs: on-device results`.
