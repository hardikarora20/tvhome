# TV Home - Glass redesign v3.8 (com.gathin.tvhome)

Build: open in Android Studio (JDK 17) -> Build > Build APK(s) -> `adb install -r`.
Your poster images from the v3.6 build are kept (res/drawable-nodpi).

## v3.7 changes
| Area | Change |
|---|---|
| Banner buttons | Watchlist removed. Second pill opens the app: "Open JioHotstar", "Open Netflix", or "Channel 734" on Aaj Tak. Hidden on the Live Channels slide. |
| Banner slides | Sony TV removed. Order: Anupamaa, YRKKH, Kapil Show, Aaj Tak, SYHP, YFT, Live Channels. |
| Aaj Tak | Play opens Aaj Tak's official YouTube live stream (`youtube.com/channel/UCt4t-jeY85JegMlZ-E5UWtA/live`) in YouTube. Falls back to channel 734 in Live Channels. |
| Screensaver | New moon icon (Inputs, Wi-Fi, Screensaver, Settings). Starts the Android screensaver immediately; falls back to the screensaver settings page. |
| Menus | All menus scroll when they are taller than the screen (Inputs with many sources), with soft fading edges. |
| Greeting | Morning 5-11, Afternoon 12-16, Evening 17-20, Good Night 21-23, "Up late" 0-4. |
| Prime Video tile | Shows the full "prime video", auto-sized to fit on one line. |
| Launch animation | The tile/button pops, a brand-coloured circle expands from it to fill the screen, the app fades in, and the veil fades away when you come back. |
| Live Channels art | New banner: broadcast control room with a wall of screens. |
| Banner speed | New Settings slider: 5-15 s per slide (default 8 s), 1 s per D-pad press, saved. |

## Grants (same as before)
```
adb shell appops set com.gathin.tvhome SYSTEM_ALERT_WINDOW allow
adb shell pm grant com.gathin.tvhome android.permission.WRITE_SECURE_SETTINGS
adb shell pm grant com.gathin.tvhome com.android.providers.tv.permission.READ_EPG_DATA
```

## Poster override (no rebuild)
`adb push poster.jpg /sdcard/Android/data/com.gathin.tvhome/files/hero/<id>.jpg`
ids: anupamaa, yrkkh, kapil, aajtak, syhp, yft, live

### v3.8 changes
| Area | Change |
|---|---|
| Play button | Frosted like the Open button when not focused; white with dark text only when focused. |
| Banner from JSON | All slides (title, tags, image, colour, both buttons) now come from `app/src/main/assets/banner.json`. See BANNER_JSON_GUIDE.md. |
| Live updates | Push `banner.json` to `Android/data/com.gathin.tvhome/files/` (no rebuild), or set `"remote"` to an https URL that is fetched on open and cached. |
| Remote images | `"image": "https://..."` is downloaded once and cached; a colour gradient shows until it arrives. |
| Manifest | Added INTERNET permission (only used for remote JSON/images). |


### v3.9 changes
| Area | Change |
|---|---|
| Refresh Banner | New Settings row (above System Settings). Fetches the remote banner.json immediately, with a spinning icon and a result line: "Banner updated", "Already up to date" or "Couldn't reach the server". When idle it shows "Last updated X min ago". |
| Auto update | Remote banner.json is checked every 30 min (was 15), including while the home screen stays open, and when you return Home after 30 min. |
| Fresh fetch | Requests add a timestamp parameter so GitHub's raw CDN cache doesn't return an old copy. |



#### v4.0 changes
| Area | Change |
|---|---|
| Top bar | New order: Wi-Fi, Play stream, Inputs, Settings. The screensaver button moved into Settings. |
| Background stream | The new play button starts the stream from `play.json` in the background and turns into a pause button. The icon pulses while connecting, and the tooltip shows the stream name and status. It keeps playing in other apps, stops when another app starts playing audio, retries 3 times if the connection drops, and has a "Stop" button in its notification. |
| play.json | Works like banner.json: `/sdcard/Android/data/com.gathin.tvhome/files/play.json` (adb push), then `app/src/main/assets/play.json`, then the built-in AIR Live link. Its `"remote"` URL (repo root `play.json`) is fetched on open, every 30 min, and from Settings > Refresh Banner & Stream. If the link changes while playing, it switches to the new one. |
| Settings | New "Screensaver - Start now" row; the refresh row is now "Refresh Banner & Stream". |
| Focus | The launcher opens with focus on the banner's Play button; Back also returns to it. |
| Manifest | Foreground media service, WAKE_LOCK, and `usesCleartextTraffic` (the AIR stream is plain http). |
