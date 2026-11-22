# TV Home - Glass redesign v3.6 (com.gathin.tvhome)

## Build
Open this folder in Android Studio (JDK 17), then Build > Build APK(s) and `adb install -r`.
Your poster images from v3.5 are kept as they were in res/drawable-nodpi.

## v3.6 changes
| Area | Change |
|---|---|
| Layout | Header, then the banner, then the favourites dock pinned to the bottom edge. Other apps are hidden below the screen and slide in when you press Down from the dock. |
| Titles | Limited to about 38% of the screen width. Long titles break into 2 balanced lines at a word boundary. |
| New slides | Aaj Tak (channel 734) and Sony TV (channel 35). Play opens Live Channels on that channel. |
| Order | Anupamaa, Yeh Rishta Kya Kehlata Hai, The Great Indian Kapil Show, Aaj Tak, Sony TV, Shayad Yahi Hai Pyaar, Yeh Fitoor Tera, Live Channels |

How the titles fit:
- A title that fits on one line at 40sp or larger stays on one line (up to 56sp).
- Otherwise it splits into two lines, sized 44sp down to 26sp, so the wider line fits.
- Expected results: ANUPAMAA | YEH RISHTA KYA / KEHLATA HAI | THE GREAT INDIAN / KAPIL SHOW |
  AAJ TAK | SONY TV | SHAYAD YAHI / HAI PYAAR | YEH FITOOR / TERA | LIVE CHANNELS

## Channel slides
Play looks up the channel number in the TV's channel list (TvContract) and opens
content://android.media.tv/channel/<id>.
- The first time, Android asks for permission to read TV listings. Allow it.
- If the permission isn't granted, or the number isn't in the channel list, Live Channels opens with a hint to tune manually.

Grant it without the prompt:
```
adb shell pm grant com.gathin.tvhome com.android.providers.tv.permission.READ_EPG_DATA
```
To change the numbers, edit "734" / "35" in `items[]` in HomeActivity.java.

## Poster override (no rebuild)
```
adb push poster.jpg /sdcard/Android/data/com.gathin.tvhome/files/hero/<id>.jpg
```
ids: anupamaa, yrkkh, kapil, aajtak, sony, syhp, yft, live
