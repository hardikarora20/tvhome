# TV Home - Glass redesign (com.gathin.tvhome)

Drop-in redesigned home screen matching the mockup: full-bleed auto-scrolling hero,
single-line greeting, 3 circular glass icons + clock, frosted Settings dropdown,
concentric frosted dock with a peeking app row, strict single-focus D-pad navigation.

## Build (standalone)
1. Open this folder in Android Studio (Koala or newer; JDK 17). Let it sync. It creates the Gradle wrapper jar if it is missing.
2. Build > Build APK(s), or run `gradlew assembleRelease`.
3. Install with `adb install -r app-release.apk`, press Home, and choose "TV Home" as the default.

The applicationId is `com.gathin.tvhome`, the same as your current launcher. If the new build
is not signed with your original keystore, uninstall the old APK first, or change
`applicationId` in app/build.gradle so both can be installed side by side.

## Merge into your existing launcher source
Copy these into your project:
- `app/src/main/java/com/gathin/tvhome/home/` (HomeActivity, FrostedPanel, SliderView, IndicatorView, HeroItem)
- `app/src/main/res/drawable/ic_h_*.xml` and `res/drawable-nodpi/hero_*.jpg`
Then move the HOME / LEANBACK_LAUNCHER intent-filter from `.MainActivity` to `.home.HomeActivity`,
or start HomeActivity from MainActivity. Copy the `<queries>` block and the ACCESS_NETWORK_STATE
permission from this manifest. No new dependencies are needed (framework-only).

## Where to change things (all in HomeActivity.java)
| What | Where |
|---|---|
| Hero slides (title, metadata line, deep link, artwork) | `items[]` |
| Slide duration | `SLIDE_MS` (8 s) |
| Dock apps | `DOCK_PKGS` |
| Branded mockup-style tiles instead of the apps' own banners | `STYLED_DOCK_TILES = true` |
| Default user name | `DEFAULT_NAME` (the edited name is saved in SharedPreferences "glass_home") |

Deep links:
- JioHotstar: `https://www.hotstar.com/in/shows/<slug>/<id>`, launched with package `in.startv.hotstar`.
- Netflix: `https://www.netflix.com/title/80241318`, launched with package `com.netflix.ninja` and extra source=30.
If the target app can't handle the link, the app itself opens. If it isn't installed, the Play Store opens.

## Behaviour notes
- Frosted glass: Android views can't blur what is drawn behind them, so FrostedPanel takes a blurred
  snapshot of the hero underneath it. The snapshot refreshes after each slide change and after the
  rows scroll.
- Brightness: the slider always changes brightness while the home screen is on top. To make the change
  system-wide, grant "Modify system settings":
  `adb shell appops set com.gathin.tvhome WRITE_SETTINGS allow`
- Volume: controls STREAM_MUSIC. The hardware volume keys also update the slider.
- VPN dot: the green dot on Wi-Fi appears whenever any network has TRANSPORT_VPN. It is checked every second.
- Inputs: tries `com.android.tv.action.VIEW_INPUTS` and falls back to the Live Channels app.
- Settings menu: D-pad Up/Down moves between rows, Left/Right adjusts the sliders, OK selects, and Back closes the menu.
  While it is open, every other key is swallowed and the dock is dimmed to 45%.
- Home screen: Back returns focus to the first dock tile and scrolls back up if you are in the app rows.
- Hero artwork: original AI-generated placeholder images (generated with Microsoft Copilot), not
  official show posters. Replace the `hero_*.jpg` files with licensed artwork if you have it.
