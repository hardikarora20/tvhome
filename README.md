# TV Home - Glass redesign v3.5 (com.gathin.tvhome)

## Build
1. Open this folder in Android Studio (JDK 17) and let Gradle sync.
2. Build > Build APK(s) (or `gradlew assembleRelease`), then `adb install -r app-release.apk`.
3. Press Home and pick "TV Home". Unless it is signed with your original keystore, uninstall the old launcher first.

## One-time adb grants (recommended)
```
# Brightness that works in every app (dim layer). Without this, only the home screen dims.
adb shell appops set com.gathin.tvhome SYSTEM_ALERT_WINDOW allow
# Real backlight control on TVs that expose a vendor backlight setting (MediaTek/Realtek/TCL/Xiaomi...)
adb shell pm grant com.gathin.tvhome android.permission.WRITE_SECURE_SETTINGS
# Lets the slider also write SCREEN_BRIGHTNESS (for the few TVs that honour it)
adb shell appops set com.gathin.tvhome WRITE_SETTINGS allow
```

## What changed in v3.5
| Area | Change |
|---|---|
| Inputs | The Inputs icon opens a frosted Inputs menu that lists the TV's HDMI / AV / Component / Antenna sources (TvInputManager), with Connected / No signal status and a green dot. Choosing one switches the TV to that source. |
| Brightness | Uses a vendor backlight key if present, otherwise a TV-wide dim layer, otherwise dims the home screen. The value is saved and re-applied on start. |
| Banner layout | The title, meta and button block has a fixed height and the dock is pinned at 70% of the screen height. Nothing moves when slides change. |
| Titles | One typeface for every show (heavy sans, upper-case), auto-fitted to the same box. |
| Banner navigation | The arrow buttons are gone. Slides change on their own every 8 s. In the Play / Watchlist row, Left past Play = previous slide and Right past the last button = next slide. The progress pill remains as an indicator only. |
| New slide | The Great Indian Kapil Show (Netflix, title 81737430). |
| Dock = Favourites | The dock shows your favourites (max 5; default Live Channels, Netflix, Prime Video, YouTube, JioHotstar), with branded custom tiles. All other apps appear in the rows below on the same grid. |
| Long-press menu | Hold OK (or press Menu) on any app for Add to / Remove from Favourites, App info and Uninstall. |
| Focus ring | The ring and soft glow are drawn in padding around the tile, outside the clipped card, so they are never cropped. |
| Scrolling | Going down to the apps hides the header and banner text and dims the artwork. The glass behind the dock updates on every frame, not after the scroll finishes. |
| Motion | Same easing curves throughout, longer cross-fades, a Ken Burns effect on the art, and no layout passes during animations. |

## Banner artwork
The bundled images are original placeholders (generated with Microsoft Copilot), not official posters.
Official key art is copyrighted, so it isn't bundled. To use posters you have the rights to, push a file
named after the slide id. It overrides the bundled art without a rebuild:
```
adb push poster.jpg /sdcard/Android/data/com.gathin.tvhome/files/hero/manifest.jpg
```
Slide ids: anupamaa, yrkkh, syhp, yft, manifest, kapil, live (.jpg/.png/.webp, 16:9, about 1920x1080).

## Notes
- Inputs: some vendor TVs (certain Coocaa/TCL/Xiaomi builds) don't expose their inputs to apps. On those, the menu shows "Live TV" and "Source menu" instead.
- Edit slides in `items[]`, default favourites in `DEFAULT_FAVS`, and slide duration in `SLIDE_MS` (all in HomeActivity.java).
