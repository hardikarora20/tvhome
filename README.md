# 📺 TV Home — Glass Launcher for Android TV

> A clean, glassy, lightweight home screen for Android TV, made by **Hardik** for the living-room TV.
> Big cinematic banner up top, your favourite apps in a frosted dock, and everything driven by the remote. 🎮

![version](https://img.shields.io/badge/version-3.9--glass-blueviolet)
![android](https://img.shields.io/badge/Android%20TV-6.0%2B%20(API%2023)-3DDC84)
![deps](https://img.shields.io/badge/dependencies-zero-success)
![remote](https://img.shields.io/badge/D--pad-friendly-orange)

---

## 📖 Table of contents
1. [✨ At a glance](#-at-a-glance)
2. [🪶 Why it's light](#-why-its-light)
3. [🏠 The home screen, top to bottom](#-the-home-screen-top-to-bottom)
4. [🎬 The banner](#-the-banner)
5. [📝 banner.json: change the banner without code](#-bannerjson-change-the-banner-without-code)
6. [⭐ Dock and app rows](#-dock-and-app-rows)
7. [⚙️ Settings menu](#️-settings-menu)
8. [🔌 Inputs menu](#-inputs-menu)
9. [🌙 Screensaver](#-screensaver)
10. [🎮 Remote controls cheat sheet](#-remote-controls-cheat-sheet)
11. [🎨 Design and animation details](#-design-and-animation-details)
12. [🛠️ Build and install](#️-build-and-install)
13. [🔑 One-time adb grants](#-one-time-adb-grants)
14. [🗂️ Project structure](#️-project-structure)
15. [🧩 Customising](#-customising)
16. [🩺 Troubleshooting](#-troubleshooting)
17. [📜 Version history](#-version-history)

---

## ✨ At a glance

| | Feature | What it does |
|---|---|---|
| 👋 | **Personal greeting** | "Good Evening, **Hardik**" on one line, changing with the time of day; the name is editable |
| 🎬 | **Cinematic banner** | Full-screen artwork that auto-advances, with Play and Open buttons that jump straight into the show |
| 📝 | **banner.json** | Slides come from a JSON file: local, pushed with adb, or hosted online and auto-updated |
| 🔄 | **Refresh Banner** | Fetch the latest banner on demand from Settings, plus an automatic check every 30 minutes |
| ⭐ | **Favourites dock** | Up to 5 apps in a frosted glass dock; add or remove them with a long-press |
| 📱 | **All apps** | Every other TV app on the same grid, sliding up when you press Down |
| 🔌 | **Inputs menu** | HDMI / AV / Antenna sources with live connection status; one click switches source |
| 🔆 | **Brightness and volume** | Sliders in a frosted Settings menu, driven by Left / Right |
| ⏱️ | **Banner speed** | 5–15 seconds per slide, saved |
| 🌙 | **Screensaver button** | Starts the Android TV screensaver right away |
| 🛡️ | **Wi-Fi + VPN dot** | Wi-Fi shortcut with a green dot whenever a VPN is active |
| 🎨 | **Launch animation** | A circle in the app's own colour fills the screen as the app opens |
| 🪶 | **Light** | Pure Android framework: no libraries, no ads, no tracking |

---

## 🪶 Why it's light

- 🚫 **Zero third-party dependencies.** No AndroidX, no Glide, no Retrofit, no Compose. Only the Android framework, which keeps the APK small and start-up quick.
- 🧱 **The whole UI is built in code**, with no XML layouts to inflate. Most of the logic is in a single `HomeActivity`.
- 🖼️ **Memory-friendly images.** Artwork is decoded as `RGB_565` (half the memory of full colour) and downscaled to the screen width.
- 🌫️ **Cheap frosted glass.** Each slide is blurred **once** at about 1/10 size and the blurred copy is reused. No per-frame blur, so it runs well on low-power TV chips.
- 🧵 **Heavy work off the main thread.** Image downloads, blurring, channel lookups and JSON fetches all run in the background.
- 💤 **Nothing runs in the background when you're away.** The clock, slide timer and auto-refresh stop as soon as another app is on screen.
- 📦 **Small caches.** Online banner images are kept on the TV and deleted after 30 days unused.
- 🔋 **Network is optional.** With no online URL set, the launcher never touches the internet.

---

## 🏠 The home screen, top to bottom

```
┌──────────────────────────────────────────────────────────────────────┐
│ Good Evening, Hardik             (🔌) (📶•) (🌙) (⚙️) │  5:28 PM      │
│                                                       │  SUN, NOV 22  │
│                                                                      │
│                    🎞️  full-screen banner artwork                     │
│                                                                      │
│  THE GREAT INDIAN                                                    │
│  KAPIL SHOW                                                          │
│  Netflix • 5 Seasons • Comedy Talk Show • Hindi                      │
│  [ ▶ Play ]  [ ↗ Open Netflix ]                     ( ▬ • • • • • )   │
│ ╭──────────────────────────────────────────────────────────────────╮ │
│ │ [Live Channels] [NETFLIX] [prime video] [YouTube] [✦ JioHotstar] │ │
│ ╰──────────────────────────────────────────────────────────────────╯ │
└──────────────────────────────────────────────────────────────────────┘
                 ⬇ press Down: all your other apps slide up
```

### 👋 Greeting (top-left)
One line: the time-of-day part in soft white, your name in **bold white**.

| Time | Greeting |
|---|---|
| 🌅 5 AM – 12 PM | Good Morning, Hardik |
| ☀️ 12 PM – 5 PM | Good Afternoon, Hardik |
| 🌆 5 PM – 9 PM | Good Evening, Hardik |
| 🌙 9 PM – 12 AM | Good Night, Hardik |
| 🦉 12 AM – 5 AM | Up late, Hardik |

Change the name in **Settings → User Name**. It updates straight away and is saved on the TV.

### 🔘 Top-right bar
Four perfectly round frosted buttons. Each one shows a small label (tooltip) only while it has focus.

| Button | Does |
|---|---|
| 🔌 **Inputs** | Opens the [Inputs menu](#-inputs-menu) |
| 📶 **Wi-Fi** | Opens Wi-Fi settings; a 🟢 green dot appears when a VPN is connected |
| 🌙 **Screensaver** | Starts the [screensaver](#-screensaver) |
| ⚙️ **Settings** | Opens the [Settings menu](#️-settings-menu) |

After a thin divider comes the 🕐 **live clock**, with the date underneath (12- or 24-hour, following your TV setting).

---

## 🎬 The banner

- 🖼️ **Full-screen artwork** with soft dark fades along the top, left and bottom, so white text stays readable on any image.
- 🎥 **Slow zoom ("Ken Burns" effect)** on every slide, with smooth cross-fades between slides.
- 🔤 **One font for every title**: heavy, upper-case, and fitted automatically:
  - Short titles stay on one line, e.g. `ANUPAMAA`.
  - Long titles split into two even lines, e.g. `THE GREAT INDIAN / KAPIL SHOW`, using at most about 38% of the screen width.
  - The title area never changes size, so nothing below it moves.
- 📊 **Progress pill** (bottom-right): a filling bar for the current slide and dots for the others.
- ⏯️ **Pauses itself** while a menu is open, while you're browsing the app rows, or while another app is on screen.

### Buttons
| Button | Look | Action |
|---|---|---|
| ▶️ **Play** | Frosted; turns white when focused | Runs the slide's `play` action: opens the show, live stream or app directly |
| ↗️ **Open …** | Frosted; turns white when focused | Runs the `secondary` action, e.g. "Open JioHotstar", "Open Netflix", "Channel 4". Hidden when a slide doesn't have one |

### Current slides (from `banner.json`)
| # | Slide | ▶️ Play | Second button |
|---|---|---|---|
| 1 | Anupamaa | Show page in JioHotstar | Open JioHotstar |
| 2 | Yeh Rishta Kya Kehlata Hai | Show page in JioHotstar | Open JioHotstar |
| 3 | The Great Indian Kapil Show | Title page in Netflix | Open Netflix |
| 4 | Aaj Tak 🔴 | YouTube live stream (falls back to channel 4) | Channel 4 |
| 5 | Shayad Yahi Hai Pyaar | Show page in JioHotstar | Open JioHotstar |
| 6 | Yeh Fitoor Tera | Show page in JioHotstar | Open JioHotstar |
| 7 | Live Channels | Opens Live Channels | — |

---

## 📝 banner.json: change the banner without code

All slides (titles, tags, images, colours and both buttons) live in **`banner.json`**. Change the file and the banner changes. No Java edits needed. ✅

### 📍 Where it's read from
| Priority | Location | How to update |
|---|---|---|
| 1️⃣ | `/sdcard/Android/data/com.gathin.tvhome/files/banner.json` | `adb push banner.json /sdcard/Android/data/com.gathin.tvhome/files/banner.json`, then press **Home** |
| 2️⃣ | `app/src/main/assets/banner.json` (inside the APK) | Edit and rebuild |
| ☁️ | The `"remote"` URL inside the file in use | Host the JSON online (GitHub raw, Gist, any https link) |

### ☁️ Online updates
- 🚀 Downloaded when the launcher opens.
- ⏰ Checked again **every 30 minutes**, even if the home screen just stays open, and when you return Home after 30 minutes.
- 🔄 Instant check any time with **Settings → Refresh Banner**.
- 💾 A copy is kept on the TV, so the banner works offline and appears instantly on the next start.
- 🧊 Each request adds a timestamp to the link, so GitHub's cache can't serve an old version.
- 🛟 **Safe by design:** a broken or invalid file is ignored and the previous working banner stays. It never goes blank.

### 🧱 Format
```json
{
  "remote": "https://raw.githubusercontent.com/<user>/<repo>/main/banner.json",
  "slides": [
    {
      "id": "kapil",
      "title": "The Great Indian Kapil Show",
      "tags": ["Netflix", "5 Seasons", "Comedy Talk Show", "Hindi"],
      "image": "res:hero_kapil",
      "color": "#B20710",
      "play": {
        "type": "link",
        "uri": "https://www.netflix.com/title/81737430",
        "package": "com.netflix.ninja",
        "extras": { "source": "30" }
      },
      "secondary": { "label": "Open Netflix", "type": "app", "package": "com.netflix.ninja" }
    }
  ]
}
```

#### Slide fields
| Key | Required | Meaning |
|---|---|---|
| `id` | – | Unique name (also used for adb image overrides) |
| `title` | ✅ | Big title |
| `tags` / `meta` | – | Detail line: a list joined with " • ", or free text |
| `image` | – | Background image (16:9, ~1920×1080) |
| `color` | – | `#RRGGBB`; used for the launch animation and the loading placeholder |
| `play` | – | Action for ▶️ Play |
| `secondary` | – | Action for the second button; `null` hides it |
| `enabled` | – | `false` hides the slide without deleting it |

#### 🖼️ `image` values
| Value | Where the image comes from |
|---|---|
| `res:hero_kapil` | Bundled in the APK |
| `https://…/poster.jpg` | Downloaded once, then cached on the TV 📥 |
| `kapil_new.jpg` | A file pushed to `…/files/hero/` |
| `asset:hero/x.jpg` · `/sdcard/x.jpg` | An asset in the APK · an absolute path on the TV |

While an online image is still downloading, the slide shows a gradient in its `color` instead of a blank screen.

#### 🎯 Actions (`play` / `secondary`)
| `type` | Keys | Does |
|---|---|---|
| `link` | `uri`, `package`, `anyApp`, `extras` | Opens a link inside a specific app (e.g. straight to a show). `anyApp: true` = if that app can't, try any app |
| `app` | `package` | Opens the app |
| `channel` | `number` | Opens Live Channels on that channel number 📡 |
| `intent` | `action`, `uri`, `package`, `class`, `extras` | Any custom Android intent (for advanced use) |

These work on every action:
- `label`: the text on the second button.
- `icon`: `open` / `live` / `play`.
- `fallback`: another action to try if this one fails.

Shortcuts: `"play": "https://…"` (a link) or `"play": "734"` (a channel number).

#### 📦 Handy package names
| App | Package |
|---|---|
| JioHotstar | `in.startv.hotstar` |
| Netflix | `com.netflix.ninja` (add `"extras": {"source": "30"}`) |
| Prime Video | `com.amazon.amazonvideo.livingroom` |
| YouTube | `com.google.android.youtube.tv` |
| Live Channels | `com.google.android.tv` |

📘 Full reference: [`BANNER_JSON_GUIDE.md`](BANNER_JSON_GUIDE.md)

---

## ⭐ Dock and app rows

- 🧊 **Frosted glass dock** pinned to the bottom of the screen.
  - The glass blurs whatever artwork is behind it, live, even while it moves.
  - Its corners follow the tile corners exactly (outer radius = tile radius + padding).
- 🏷️ **Default favourites**, each with its own custom-designed tile:
  - Live Channels
  - NETFLIX
  - prime video
  - YouTube
  - ✦ JioHotstar
- ➕ **Up to 5 favourites.** Empty slots show as faint outlines.
- 📱 **All other apps** sit below the screen in rows of 5 on the exact same grid. Press ⬇️ Down from the dock and they glide up while the header and banner fade away.
- ✨ **Focus effect:** the tile grows slightly and gets a white ring with a soft glow. Both are drawn outside the tile, so they never get cut off.
- 🔁 **Updates itself** when apps are installed, updated or removed.

### 📋 Long-press menu (hold OK, or press ☰ Menu)
| Option | Does |
|---|---|
| ⭐ Add to / Remove from Favourites | Moves the app into or out of the dock |
| ℹ️ App info | Opens Android's App info page |
| 🗑️ Uninstall | Uninstalls the app (hidden for built-in system apps) |

---

## ⚙️ Settings menu

A dark frosted dropdown under the ⚙️ button. While it's open, the remote only moves inside the menu and the dock dims, so it's clear where you are.

| Row | Control |
|---|---|
| 🔆 **Brightness** | ⬅️ / ➡️ in 5% steps (10–100%), saved |
| 🔊 **Volume** | ⬅️ / ➡️; the TV's own volume buttons update it too |
| ⏱️ **Banner speed** | ⬅️ / ➡️ from 5 s to 15 s per slide, in 1 s steps |
| 👤 **User Name** | OK → dialog to rename; the greeting updates immediately |
| 🔄 **Refresh Banner** | OK → fetches `banner.json` now (details below) |
| ⚙️ **System Settings** | Opens Android TV settings |

### 🔄 Refresh Banner
- The icon spins while it's checking.
- The line underneath then shows the result: ✅ "Banner updated • 7 slides", 👌 "Already up to date" or ⚠️ "Couldn't reach the server. Try again".
- When idle it shows 🕐 "Last updated 12 min ago • auto every 30 min".

### 🔆 How brightness works
Most Android TVs ignore the normal brightness setting apps can change, so the launcher tries three methods in order:
1. **Real backlight control**, if the TV has a backlight setting apps can change (some MediaTek, Realtek, TCL, Xiaomi and Hisense TVs). Needs the `WRITE_SECURE_SETTINGS` grant.
2. **A dim layer over every app** (needs "Display over other apps").
3. **Dimming the home screen only** (always works).

---

## 🔌 Inputs menu

- 📋 Lists the TV's own sources:
  - 📡 Antenna / Cable
  - 🔌 HDMI ports
  - 🎛️ AV, Component, VGA and similar inputs
- 🟢 Shows the status of each one: *Connected*, *Standby* or *No signal*.
- ↕️ Scrolls smoothly, with soft fades at the edges, when there are more sources than fit.
- 🖱️ One click switches the TV to that source.
- 🛟 Some TVs don't let apps see their inputs. On those, the menu shows **Live TV** and **Source menu** shortcuts instead.

---

## 🌙 Screensaver

The 🌙 button starts Android's own screensaver **immediately** (the same as "Start now" in Settings). If your TV blocks that, it opens the screensaver settings page with a hint to press "Start now".

---

## 🎮 Remote controls cheat sheet

| Where you are | ⬆️⬇️ | ⬅️➡️ | OK | Back | ☰ Menu |
|---|---|---|---|---|---|
| Top bar | Down → banner | Move between buttons | Open | → first dock tile | – |
| Banner buttons | Up → top bar · Down → dock | Move between buttons; **past the edge = previous / next slide** | Play / Open | → first dock tile | – |
| Dock / app rows | Move between rows (rows scroll smoothly) | Move between tiles | Launch | Back to the home view | App menu |
| Long-press a tile | – | – | Hold → app menu | – | – |
| Any menu | Move between rows (scrolls when needed) | Adjust a slider | Select | Close the menu | Close the menu |

🎯 **Only one item is ever highlighted on screen**, so you always know where you are.

---

## 🎨 Design and animation details

- 🧊 **Glass look:** frosted panels with a thin translucent border, about 75% dark charcoal for menus.
- 🔵 **True circles:** the top-bar buttons are perfectly round, never oval.
- 🌈 **Launch animation:** the tile or button pops slightly, then a circle in the app's colour fills the screen and the app fades in:
  - 🔴 Netflix and Aaj Tak in red
  - 🟣 JioHotstar in violet
  - 🔵 Prime Video and Live Channels in blue
  - ⚫ the screensaver in black

  When you come back Home, the colour fades away.
- 🧈 **Smooth motion:** the same easing curves everywhere, and nothing changes size mid-animation, so the layout never jumps.
- 📐 **Fixed layout:** header, then banner, then dock. The dock never moves when the slide changes.

---

## 🛠️ Build and install

### 🤖 GitHub Actions (this repo)
Every push runs `.github/workflows/build-apk.yml`, which builds a debug APK. Download it from the run's **Artifacts** (`tvhome-redesign-debug`).

### 💻 Android Studio
1. Open the project folder (use JDK 17).
2. Run **Build → Build APK(s)**.
3. Install it on the TV:
   ```bash
   adb connect <tv-ip>
   adb install -r app-debug.apk
   ```
4. Press 🏠 Home on the remote → choose **TV Home** → **Always**.

> ℹ️ The package name is `com.gathin.tvhome`. If a version signed with a different key is already installed, uninstall it first.

---

## 🔑 One-time adb grants

None of these are required; each one simply unlocks more of a feature.

```bash
# 🔆 Brightness that works in every app (dim layer)
adb shell appops set com.gathin.tvhome SYSTEM_ALERT_WINDOW allow

# 🔆 Real backlight control on TVs that support it
adb shell pm grant com.gathin.tvhome android.permission.WRITE_SECURE_SETTINGS

# 📡 Open Live Channels on a channel number without the permission prompt
adb shell pm grant com.gathin.tvhome com.android.providers.tv.permission.READ_EPG_DATA
```

### 🔐 Permissions and why they're needed
| Permission | Why |
|---|---|
| INTERNET, ACCESS_NETWORK_STATE | Online banner.json / images; VPN dot |
| ACCESS_WIFI_STATE | Wi-Fi status |
| READ_EPG_DATA / READ_TV_LISTINGS | Finding a channel by number (e.g. channel 4) |
| SYSTEM_ALERT_WINDOW, WRITE_SETTINGS, WRITE_SECURE_SETTINGS | Brightness |
| QUERY_ALL_PACKAGES | Listing all your TV apps |
| REQUEST_DELETE_PACKAGES | Uninstall from the long-press menu |

---

## 🗂️ Project structure

```
tvhome/
├── .github/workflows/build-apk.yml    🤖 CI build
├── BANNER_JSON_GUIDE.md               📘 banner.json reference
├── app/src/main/
│   ├── AndroidManifest.xml
│   ├── assets/banner.json             📝 the banner
│   ├── res/drawable-nodpi/hero_*.jpg  🖼️ bundled artwork
│   ├── res/drawable/ic_h_*.xml        🔣 icons
│   ├── res/raw/keep.xml               🛡️ keeps hero_* in release builds
│   └── java/com/gathin/tvhome/home/
│       ├── HomeActivity.java          🏠 the whole home screen
│       ├── BannerConfig.java          📝 reads, caches and downloads banner.json + images
│       ├── BannerAction.java          🎯 link / app / channel / intent actions
│       ├── HeroItem.java              🎬 one slide
│       ├── Backdrop.java              🌫️ shared blurred artwork
│       ├── FrostedPanel.java          🧊 glass panels (dock, menus)
│       ├── TileView.java              🟦 app tile + focus ring and glow
│       ├── IndicatorView.java         📊 slide progress pill
│       ├── SliderView.java            🎚️ menu sliders
│       ├── Brightness.java            🔆 brightness methods
│       └── TvSources.java             🔌 inputs + channel lookup
```

---

## 🧩 Customising

| Want to… | Do this |
|---|---|
| Add / remove / reorder slides | Edit `banner.json` (push it, host it online, or rebuild) |
| Use your own poster | `"image": "https://…"`, or `adb push poster.jpg /sdcard/Android/data/com.gathin.tvhome/files/hero/<id>.jpg` |
| Change the default favourites | `DEFAULT_FAVS` in `HomeActivity.java` |
| Change the default name | `DEFAULT_NAME` in `HomeActivity.java` (or just use Settings) |
| Change the banner speed range | `SPEED_MIN` / `SPEED_MAX` / `SPEED_DEFAULT` |
| Change the auto-refresh interval | `BANNER_AUTO_MS` (default 30 min) |

---

## 🩺 Troubleshooting

| Problem | Fix |
|---|---|
| ⚠️ "Couldn't reach the server" on Refresh | Open the `remote` URL in a browser. Remove any `?token=…` part (private-repo raw links expire); use a public repo or Gist |
| 🔆 Brightness only dims the launcher | Run the `SYSTEM_ALERT_WINDOW` grant above |
| 📡 Channel button opens Live Channels but not the channel | Allow the TV listings permission, or run the `READ_EPG_DATA` grant |
| 🔌 Inputs menu only shows "Live TV" / "Source menu" | Your TV doesn't let apps see its inputs; use those shortcuts |
| 🌙 Screensaver opens settings instead | Your TV blocks starting it directly; press "Start now" |
| 🖼️ Banner image missing in a release build | Make sure `res/raw/keep.xml` is present |
| 🧾 banner.json change not showing | After `adb push`, press Home. For the online file, use Refresh Banner |

---

## 📜 Version history

| Version | Highlights |
|---|---|
| **3.9** 🔄 | Refresh Banner in Settings · auto-refresh every 30 min · always fetches a fresh copy online |
| **3.8** 📝 | Banner driven by `banner.json` (local / adb / online) · online image caching · frosted Play button |
| **3.7** 🌙 | Open-app button replaces Watchlist · Aaj Tak live stream · screensaver button · scrollable menus · Good Night greeting · full "prime video" tile · launch animation · banner speed slider · new Live Channels art |
| **3.6** 📐 | Two-line titles · dock pinned to the bottom · new slide order |
| **3.5** 🔌 | Inputs menu · brightness that works on TVs · fixed banner height · favourites + long-press menu · uncropped focus glow |
| **3.4** 🧊 | First glass redesign |

---

<p align="center">Made with ☕ and a TV remote by <b>Hardik</b> · Enjoy the show 🍿</p>
