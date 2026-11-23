# banner.json - change the banner without touching code

## Where it is read from
| Priority | Location | How to update |
|---|---|---|
| 1 | `Android/data/com.gathin.tvhome/files/banner.json` | `adb push banner.json /sdcard/Android/data/com.gathin.tvhome/files/banner.json`, then press **Home** |
| 2 | `app/src/main/assets/banner.json` (bundled) | Edit and rebuild |
| + | `"remote"` URL inside the active file | Host the JSON (GitHub raw / Gist / any https). Fetched when the launcher opens, re-checked at most every 15 min on return to Home, cached for offline |

A broken file is ignored and the next source is used, so the banner never goes blank.

## Format
```json
{ "remote": "https://raw.githubusercontent.com/<you>/<repo>/main/banner.json",
  "slides": [ { ...slide... }, { ...slide... } ] }
```
A bare array `[ {...}, {...} ]` also works. File order = banner order.

### Slide keys
| Key | Required | Meaning |
|---|---|---|
| `id` | no | Unique name; adb image override `files/hero/<id>.jpg` |
| `title` | **yes** | Big title (auto-fit, long titles break into 2 lines) |
| `tags` | no | Array joined with " • ". Or `"meta": "free text"` |
| `image` | no | Background, 16:9 (~1920x1080) - see below |
| `color` | no | `"#RRGGBB"` - launch animation + placeholder colour |
| `play` | no | Action for **Play** |
| `secondary` | no | Action for the second pill; `null` hides it |
| `enabled` | no | `false` hides the slide |

### `image`
| Value | Source |
|---|---|
| `"res:hero_kapil"` | Bundled in the APK (res/drawable-nodpi) |
| `"https://.../poster.jpg"` | Downloaded once, cached on the TV |
| `"kapil_new.jpg"` | File pushed to `Android/data/com.gathin.tvhome/files/hero/` |
| `"asset:hero/x.jpg"` / `"/sdcard/x.jpg"` | APK asset / absolute path |

### Actions (`play` / `secondary`)
| `type` | Keys | Does |
|---|---|---|
| `link` | `uri`, `package`, `anyApp`, `extras` | Deep link in that app; `anyApp: true` retries in any app |
| `app` | `package` | Opens the app |
| `channel` | `number` | Live Channels on that channel |
| `intent` | `action`, `uri`, `package`, `class`, `extras` | Any custom intent |

Also on every action: `label` (pill text), `icon` (`open` / `live` / `play`), `fallback` (another action if this fails).
`type` can be omitted (guessed from keys). Shorthand: `"play": "https://..."` or `"play": "734"`.

### Package names
JioHotstar `in.startv.hotstar` · Netflix `com.netflix.ninja` (+ `"extras": {"source": "30"}`) ·
Prime Video `com.amazon.amazonvideo.livingroom` · YouTube `com.google.android.youtube.tv` ·
Live Channels `com.google.android.tv`

## Example: add a slide
```json
{
  "id": "manifest",
  "title": "Manifest",
  "tags": ["Netflix", "4 Seasons", "Mystery Drama"],
  "image": "https://example.com/manifest.jpg",
  "color": "#B20710",
  "play": { "uri": "https://www.netflix.com/title/80241318", "package": "com.netflix.ninja", "extras": { "source": "30" } },
  "secondary": { "label": "Open Netflix", "package": "com.netflix.ninja" }
}
```
