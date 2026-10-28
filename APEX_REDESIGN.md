# Apex Launcher — UI overhaul

Chosen concept: **Aurora Glass** (landscape), reference render: `design/mockups/selected-aurora-glass.png`
Implemented home screen preview: `design/preview/apex-home-landscape.png`

Everything below is implemented in the working tree. The app is also rebranded to **Apex Launcher**.

---

## 1. What the home screen is now

**Landscape (primary orientation — the game itself is landscape):**

```
┌──────┬─────────────────────────────────────────────┬──────────────┐
│ rail │  APEX LAUNCHER          [avatar  Steve  ▾]  │  SESSION     │
│  ▣   ├─────────────────────────────────────────────┤  14:32       │
│ Home │                                             │  RAM / alloc │
│ Inst │   ┌───────────────────────────────────────┐ ├──────────────┤
│ Disc │   │ CURRENT INSTANCE                      │ │ RECENTLY     │
│ Ctrl │   │ Fabric 1.21.4                         │ │ PLAYED       │
│      │   │ 1.21.4 · vulkan_zink · 4 GB           │ │ ▸ instance   │
│      │   │                                       │ │ ▸ instance   │
│ Sett │   │ [Change version] [✎]        [ ▶ PLAY ]│ │ ▸ instance   │
│      │   └───────────────────────────────────────┘ │ [progress]   │
└──────┴─────────────────────────────────────────────┴──────────────┘
```

**Portrait** keeps the same ids/elements but reflows: header (logo, wordmark, account chip) on
top, hero card, a session/recent row, then a full-width **PLAY** bar above a 5-item bottom bar.

### Old → new mapping

| Old home (deleted `fragment_launcher.xml`) | New home |
|---|---|
| Account spinner strip across the very top | Account chip in the header (hero-aware) |
| `setting_button` (gear/home toggle) | **Settings** item in the rail/bottom bar + **Home** item |
| Version spinner + `edit_profile_button` at the bottom | Hero card: **Change version** / **Edit instance** / **Instances** nav item |
| Green `MineButton` "PLAY" | Teal→cyan gradient **PLAY** in the hero card |
| Flat list of 6 buttons (Wiki, Discord, Custom controls, Install JAR, Share logs, Open dir) | Rail: **Controls**; hero **⋮** overflow: Install JAR, Open directory, Share logs, Custom controls, Wiki, Discord |
| *(new)* | **Discover** → mod loader / modpack browser (`ProfileTypeSelectFragment`) |
| *(new)* | **Recently played** card — one tap selects the instance, ▶ launches it |
| *(new)* | **Session** card — live clock, free/total RAM, allocated RAM |
| Bottom `ProgressLayout` strip | Glass progress card in the right column (still collapsible) |

The fragment container is now an **overlay** that is hidden while the home screen is visible, so
settings / installers / mod search look the same as before but can be reached from anywhere.

---

## 2. Design system ("Aurora Glass")

Defined in `res/values/colors.xml`, `res/values/apex_dimens.xml`, `res/values/styles.xml`.

| Token | Value | Use |
|---|---|---|
| `apex_bg` | `#080D16` | app window background |
| `apex_scrim` | `#7A060B14` | darkening layer over the backdrop |
| `apex_glass` / `apex_glass_strong` | `#2B162032` / `#3D16203A` | card fills |
| `apex_glass_stroke` | `#29FFFFFF` | 1dp card border |
| `apex_accent` → `apex_cyan` | `#2BD9C0` → `#23C7E8` | primary gradient, links, selected nav |
| `apex_text` / `_dim` / `_faint` | `#EAF2FF` / `#9BACC6` / `#6D7E96` | type ramp |
| `apex_card_radius` | `20dp` | cards |
| `apex_text_display` | `_30ssp` | hero title |

**Backdrop:** generated aurora artwork (`drawable-nodpi/apex_background.jpg`, 22 KB) + `apex_hero_art.jpg`
(92 KB) — no runtime blur/shadows, so it stays cheap on low-end GPUs.

New drawables: `apex_glass_card`, `apex_glass_card_strong`, `apex_rail_bg`, `apex_chip`,
`apex_chip_active`, `apex_nav_item`, `apex_btn_play`, `apex_btn_ghost`, `apex_btn_primary`,
`apex_icon_button_bg`, `apex_avatar_ring`, `apex_hero_scrim`, `apex_dropdown_bg`.
New vectors: `ic_nav_{home,library,store,controls,settings}`, `ic_apex_{logo,play,play_dark,clock,download,star,folder,article,discord}`,
`ic_search`, `ic_more_horiz`, `ic_chevron_right`.

### Theme
`AppTheme` (portrait + landscape variants) now sets `colorAccent` / `android:colorAccent` /
`colorEdgeEffect` / `windowBackground`, so **every existing Pojav screen** (settings, installers,
file pickers, mod search) inherits the new palette without being rewritten. `MineButton`
(the project-wide primary button) now uses the gradient `apex_btn_primary`; the legacy colour
aliases (`minebutton_color`, `background_app`, …) were remapped, not deleted.

---

## 3. Files touched

**Added**
```
res/layout/item_apex_nav.xml            nav item (icon + label, selected state)
res/layout/item_apex_recent.xml         recently-played row
res/layout/fragment_apex_home_placeholder.xml  back-stack root, no content
res/layout-port/activity_pojav_launcher.xml    portrait home
res/values/apex_dimens.xml              design tokens
res/values/apex_attrs.xml               ApexNavItem styleable
res/color/apex_nav_icon_tint.xml, res/color/apex_nav_text.xml
res/drawable/apex_*.xml, res/drawable/ic_nav_*, ic_apex_*
res/drawable-nodpi/apex_background.jpg, apex_hero_art.jpg
res/mipmap-*/ic_launcher(.png|_foreground.png|_round.png)   new icon
java/com/kdt/mcgui/ApexNavItem.java     custom nav component
```

**Rewritten**
```
res/layout/activity_pojav_launcher.xml  (now landscape home)
res/layout/item_minecraft_account.xml   (glass row in the account dropdown)
res/layout/item_version_profile_layout.xml, res/layout/spinner_mc_version.xml
res/values/colors.xml, styles.xml, values-land/styles.xml, strings.xml (+45 locales rebranded)
java/.../LauncherActivity.java          home logic, nav, hero, recent list, overflow
java/.../fragments/MainMenuFragment.java  now a back-stack root only
assets/about_en.txt                     fork attribution (LGPLv3, upstream credits)
```

**Edited**
```
java/com/kdt/mcgui/mcAccountSpinner.java   background now comes from the layout
java/com/kdt/mcgui/ProgressLayout.java     glass card instead of a flat bar
java/com/kdt/mcgui/MineButton.java         shared primary button
java/net/kdt/pojavlaunch/Tools.java        APP_NAME, game folder name + legacy migration
java/net/kdt/pojavlaunch/PojavApplication.java  crash header uses APP_NAME
AndroidManifest.xml                        label = app_short_name
```

**Removed:** `res/layout/fragment_launcher.xml`, `res/layout-land/activity_pojav_launcher.xml`,
`res/layout-land/fragment_launcher.xml` (superseded).

---

## 4. Behaviour details worth knowing

- **Home chrome visibility** is driven by `FragmentManager.FragmentLifecycleCallbacks`: while any
  fragment other than `MainMenuFragment` is resumed, the hero/rail/info column are hidden and the
  fragment container is shown; going back restores and refreshes the home.
- **Instance selection** (`selectProfile`) writes `currentProfile`, re-syncs the hidden
  `mcVersionSpinner` and refreshes the hero + recent card in place.
- **Change version / Instances / View all** open the existing profile popup
  (`mcVersionSpinner.performClick()`), so profile creation, icons and the editor are unchanged.
- **Recently played** is sorted by `MinecraftProfile.lastUsed` (newest first, max 3 rows) and uses
  `ProfileIconCache` for the icons — no new storage format.
- **Session card** polls `Tools.getFreeDeviceMemory/getTotalDeviceMemory` and a `HH:mm` clock on a
  30 s main-thread ticker that is removed in `onPause`/`onDestroy`.
- **API level guard-rails for `minSdk 21`:** no `paddingHorizontal`, `layout_marginHorizontal` or
  `drawableTint` in the new layouts (they are API 26/23); the play glyph is a dark vector instead.
  `List.sort` was replaced with `Collections.sort` for the same reason.
- **New app id is unchanged** (`net.kdt.pojavlaunch`) so existing installs keep updating, and
  `Tools.getPojavStorageRoot`/`DIR_GAME_HOME` keep using `games/PojavLauncher` **if that folder
  already exists**, otherwise `games/ApexLauncher`. Users do not lose their worlds.

---

## 5. Rebrand coverage

Renamed: launcher label (`Apex Launcher`), wordmark, account/crash/session UI strings,
storage notifications, 45 locale files, crash report header, `Tools.APP_NAME`, about screen,
launcher icon (adaptive + legacy, coral cube on navy), window background.

Intentionally **not** renamed (would break updates or upstream attribution):
`net.kdt.pojavlaunch` package id + `resValue application_package` / provider authorities,
`pojav://` runtime prefix in profile JSON, `Pojav*` class names, Crowdin/wiki/discord links,
upstream credits. See `REPO_ANALYSIS.md` §8 for the full rename checklist if you want to go all
the way (new package id = forced reinstall).

---

## 6. Verification status

This sandbox has **no Android SDK/JDK**, so nothing was compiled. What was verified:

- every layout/drawable parses (`xml.dom.minidom`);
- every `@string/@color/@dimen/@drawable/@layout/@style/@id/@font` reference used by the new
  layouts and Java resolves against the resource tree (scripted check, 0 real misses);
- every edited Java file parses (`javalang`), no stale `R.id.setting_button` / `fragment_launcher`
  references remain;
- `design/preview/apex-home-landscape.png` is rendered from the same tokens/assets to eyeball
  proportions.

To build for real:

```bash
./gradlew :jre_lwjgl3glfw:build
./gradlew :app_pojavlauncher:assembleDebug
```

(remember the JRE/LTW artifacts normally downloaded by CI are absent, see `REPO_ANALYSIS.md` §2).

Likely first-run nits to check on device: hero card height on very short screens, the profile
popup anchoring on a 1dp anchor view, and whether the account dropdown width is comfortable in
landscape.

---

## 7. Suggested next steps

1. **Screens for the rail destinations** — Instances, Discover and Controls currently reuse the
   existing fragments; a real instance grid and a mod-store landing page would complete the design.
2. **Hero artwork per instance** — use the profile icon / modpack icon as the hero image.
3. **Motion** — a subtle aurora drift behind the rail, hero parallax on scroll, and a shared-element
   transition from the recent row into the hero.
4. **Light/"day" variant** of the palette (tokens are already centralised).
5. **Settings redesign** to match (currently inherits the accent only).

---

## 8. Identity: the Apex Launcher rename

The app is fully renamed to **Apex Launcher** in everything a user can see or export.

### Changed

| Surface | Where |
|---|---|
| App label, launcher shortcut, recents, task switcher | `res/values/strings.xml` → `app_name`, `app_short_name` (52 locales, no English fallback left showing the old name) |
| Notification titles (foreground service, modpack installer) | `lazy_service_default_title`, `modpack_install_notification_title` — base + all locales |
| Crash dialog and crash report header | `error_fatal` strings, `PojavApplication` (`Tools.APP_NAME + " crash report"`), logcat tag `ApexCrashReport` |
| Game data folder | `games/ApexLauncher` (`Tools.GAME_FOLDER_NAME`), with automatic fallback to a pre-existing `games/PojavLauncher` so worlds/instances survive an upgrade |
| Runtime references in profiles | new `apex://<runtime>` scheme (`Tools.LAUNCHERPROFILES_RTPREFIX`); legacy `pojav://` is still parsed (`Tools.LEGACY_LAUNCHERPROFILES_RTPREFIX`, `Tools.getRuntimeName`, `Tools.isRuntimeReference`) |
| In-game gamepad name | "Apex XBOX 360 compatible gamepad" (`jre_lwjgl3glfw/.../GLFW.java`) |
| Renderer init log | `ApexRendererInit: …` |
| About screen | `assets/about_en.txt` — Apex Launcher first, upstream credited |
| Repo docs & CI | `README.md`, `APEX_REDESIGN.md`, workflow name `Apex Launcher CI`, artifacts `apex-debug`, `apex-debug-noruntime`, `apex-release-aab`, issue-template paths |

### Intentionally NOT renamed (and why)

| Kept | Reason |
|---|---|
| Java package `net.kdt.pojavlaunch`, class names `PojavApplication` / `PojavProfile` / `PojavRendererInit` | The C layer binds to them by symbol name (`Java_net_kdt_pojavlaunch_utils_JREUtils_*`, `FindClass("net/kdt/pojavlaunch/MainActivity")`). Renaming needs a matching rename of every JNI symbol — do it as its own change, with a build. |
| `applicationId net.kdt.pojavlaunch` | Keeps the update path from an existing install; changing it makes the app a separate install (fresh data, no upgrade). Flip it in `app_pojavlauncher/build.gradle` (`applicationId` + the three `resValue` lines) when a clean identity is wanted. |
| `POJAV_*` environment variables, `pojav_environ`, `pojavRendererName` JSON key | Part of the native/on-disk contract shared with the bundled JRE, LWJGL shim and existing `launcher_profiles.json`. |
