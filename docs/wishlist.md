# What other launchers have that we don't

Compared on 2026-11-05 with:

- [cleanyourtv](https://github.com/Gathin23/cleanyourtv) 3.3
- [Projectivy](https://github.com/spocky/miproja1) 4.7x
- [FLauncher](https://gitlab.com/etienn01/flauncher) 0.18
- Wolf Launcher (unmaintained)
- [pulgagr/tv-vibehack-firetv](https://github.com/pulgagr/tv-vibehack-firetv) (another Fire TV fork of tv-vibehack, Fire OS 6)
- [projectivy-fast-home-firetv](https://github.com/1jump2/projectivy-fast-home-firetv)

Sorted by what it's worth on this stick (1.7 GB RAM, Fire OS 8) against what it costs in RAM, APK size and risk.
"Who has it" is where the idea comes from.

## Worth doing

| # | Feature | Who has it | Why | Cost |
|---|---|---|---|---|
| 1 | **Shortcut tiles**: a URL or stream opened in a chosen app (TV Bro, VLC) | (planned here) | A favourite site or stream becomes one press | Tiny: config + one intent |
| 2 | **Tile menu on hold-OK**: move, hide, app info, uninstall | cleanyourtv, FLauncher, Projectivy | Today the dock order and hidden apps need `config.xml` + a rebuild | Small: a popup + saved order in prefs |
| 3 | **Phone-only apps in the grid**: apps that declare only `LAUNCHER`, not `LEANBACK_LAUNCHER` | pulgagr fork (`include_phone_launcher_apps`), FLauncher | Apps from Aurora Store that are phone-only don't show up today | Tiny: a second query |
| 4 | **Remote's TV/Live button opens an app of choice**: Fire OS sends it as a `livetv://` link, and with Amazon Live TV disabled (it is) any app declaring the scheme gets it | pulgagr fork | One button straight to IPTV in VLC | Small: a forwarding activity with that intent filter |
| 5 | **Earlier Home signal + black cover**: watch the system log for `FireTVKeyPolicyManager.handleShortPressOnHome` (`READ_LOGS`, granted once over ADB) and show a black overlay (`SYSTEM_ALERT_WINDOW`) until the launcher is in front | pulgagr fork, projectivy-fast-home | Amazon's home would never be visible on Home; today it shows for 0.14–0.36 s | Medium: a log reader always running (measure its CPU); on Fire OS 8 we don't need their shell helper, since our own start isn't held |
| 6 | **App name under the clock ("top shelf")**: the focused app's name in large type | cleanyourtv, tvOS | Already half there: `heroName` exists but is hidden | Tiny |
| 7 | **Stats line**: free RAM and storage under the clock, off by default | cleanyourtv (`show_stats`) | Handy for watching the debloat's effect | Tiny, refreshes only while Home is visible |

## Maybe later

| Feature | Who has it | Note |
|---|---|---|
| Categories / several rows (e.g. "Streaming", "Tools") | FLauncher, Projectivy, Wolf | More layout code; worth it only past ~20 apps |
| Themes without a rebuild (wallpaper, colours, columns from a pushed file) | cleanyourtv, Projectivy Premium | We have 2 wallpapers + day/night; a "pick your own wallpaper" row is the cheap half |
| Custom tile images and titles | Wolf, Projectivy | Needs a way to pick images on a TV |
| Hidden apps behind a PIN | Wolf | Only useful with kids around |
| App library: browse every app, add or remove Home shortcuts | cleanyourtv | Overlaps with item 2 |

## Not for us

| Feature | Who has it | Why not |
|---|---|---|
| 31 languages, right-to-left layout | cleanyourtv | en / it / zh is what's needed |
| Module cards, add-ons | cleanyourtv | Extra moving parts for one user |
| HDMI / AV inputs menu, Live TV at power-on | cleanyourtv, pulgagr fork | A stick has no inputs |
| Shell-user helper with an ADB-started process | pulgagr fork, projectivy-fast-home | Needs re-running from a computer or NAS after every reboot, with ADB kept open: a security cost. Not needed on Fire OS 8 |

## Already ahead

- Home taken over on Fire OS 8 with no helper process; Back vs Home handling for Fire OS settings.
- Real ADB on/off switch.
- 0.75 MB release build, no INTERNET permission.
- Unit tests.
- Rollback script.
