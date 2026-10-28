# ApexLauncher — Repository Analysis

*Analysis of branch `arena/96d92db0-apexlauncher` @ `b12ad04` ("Discontinued"), 2026-10-28.*

---

## 1. Executive summary

**What this actually is:** a checkout of **PojavLauncher v3_openjdk** — an Android app (Java + C/JNI) that runs *Minecraft: Java Edition* natively on Android by bundling a mobile OpenJDK 8/17/21, a custom LWJGL 3 + GLFW shim, and a set of OpenGL translation layers (GL4ES, VirGL/Zink, ANGLE/LTW).

**Important provenance facts:**

| Fact | Detail |
|---|---|
| Upstream | `PojavLauncherTeam/PojavLauncher`, branch `v3_openjdk` |
| Upstream status | **Discontinued**; the README points users to `AngelAuraMC/Amethyst-Android` |
| Git history in this repo | **1 commit**, no tags, no submodules (`b12ad04 Discontinued`) — a flattened snapshot, not a real fork history |
| Remote | `CruzCoder/ApexLauncher` |
| "Apex" branding in code | **None.** The only "apex" grep hit is a false positive (`glGenerateTextureMipmapEXT` in `GLCapabilities.java`) |
| License file | **LGPL-3.0** (`LICENSE`), but `app_pojavlauncher/src/main/assets/about_en.txt` still says GPLv3 — inconsistent already upstream |

So "ApexLauncher" is currently a **rename of the repository only**. Everything user-visible (app name, package id, storage paths, URLs, crash header) is still PojavLauncher.

**Scale:** ~190 MB working tree (≈130 MB of it is prebuilt native `.so` files), 285 Java files / ~28.7k LOC in the app, ~14.1k LOC in the LWJGL shim module, ~18.8k lines of C headers/sources.

---

## 2. Module layout & build system

```
ApexLauncher/
├── settings.gradle          → :app_pojavlauncher, :jre_lwjgl3glfw, :forge_installer, :arc_dns_injector
├── build.gradle             → AGP 8.7.2, git-hash/git-repo helpers
├── gradle/wrapper           → Gradle 8.13 distribution; wrapper JAR is committed
├── app_pojavlauncher/       → the Android app (com.android.application)
├── jre_lwjgl3glfw/          → plain Java module → produces assets/components/lwjgl3/lwjgl-glfw-classes.jar
├── forge_installer/         → plain Java agent JAR (java.lang.instrument PreMain) for headless Forge install
├── arc_dns_injector/        → java agent that redirects certain DNS names (used by the "Arc capes" toggle)
├── jni/                     → NDK build (Android.mk, ndk-build, NDK 25.2)
├── jniLibs/                 → prebuilt natives per ABI: arm64-v8a 41 MB, x86_64 35 MB, armeabi-v7a 31 MB, x86 22 MB
├── assets/components/       → unpacked-at-runtime payloads (caciocavallo, security, lwjgl3, agents)
└── scripts/                 → languagelist_updater.{sh,bat} (must run before build for translations)
```

Build config highlights (`app_pojavlauncher/build.gradle`):

- `namespace` / `applicationId` = `net.kdt.pojavlaunch` (debug suffix `.debug`)
- `minSdk 21`, `targetSdk 34`, `compileSdk 34`, NDK `25.2.9519653`, Java 8 source level
- **5 build types**: `debug`, `proguard` (initWith debug + minify), `proguardNoDebug`, `release`, `gplay`
- `versionCode` = `getDateSeconds()` → `9934841 + GITHUB_RUN_NUMBER` on CI, else a **fixed `172005`**
- `versionName` from `git describe --tags` + current branch → currently something like `LOCAL-20261028-arena/96d92db0-apexlauncher`
- CurseForge API key injected as a `resValue` from `CURSEFORGE_API_KEY` env or `curseforge_key.txt`; falls back to `"DUMMY"` (CurseForge modpack search then just won't work)
- `afterEvaluate`: `mergeDebugAssets` depends on the three companion-module `jar` tasks

**CI** (`.github/workflows/android.yml`): JDK 21 → downloads LTW `.aar` from GitHub releases into `libs/` → downloads three JRE artifacts (`jre8-pojav`, `jre17-pojav`, `jre21-pojav`) from the `android-openjdk-build-multiarch` repo into `assets/components/jre*` → builds, produces debug APK, no-runtime APK, and (upstream only) a GPlay AAB.

### Build blockers in this checkout

1. **No JREs**: `assets/components/jre`, `jre-new`, `jre-21` are git-ignored and absent. The result is an APK that only works if the user supplies a runtime ("APK without runtime" is an officially supported mode), but first-run experience differs from release builds.
2. **No LTW AAR**: `libs/ltw-release.aar` is git-ignored and absent — so the `opengles3_ltw` renderer is filtered out at runtime (`Tools.getCompatibleRenderers`, checks `libltw.so`).
3. **No Android SDK/JDK in this sandbox** — I could not compile or run anything; the analysis is static.
4. Companion JARs are committed to `assets/components/*` but their `version` files contain **stale commit hashes** (`98947f23…`, `HEllo PeopLE1`), so a local build regenerates them (only when inside a git tree).

---

## 3. Architecture

### 3.1 Process model

The app deliberately spreads work across processes (declared in `AndroidManifest.xml`):

| Process | Hosts | Purpose |
|---|---|---|
| `:launcher` (default `application` process) | `TestStorageActivity` (LAUNCHER entry), `LauncherActivity`, settings, mod installers | UI/launcher; isolated so a launcher crash doesn't kill the game |
| `:gui_installer` | `JavaGUILauncherActivity` | Runs AWT-based (Caciocavallo) installers like Forge with a real window |
| `:game` | `MainActivity` + `GameService` | Game rendering, input, foreground service keeping the JVM alive |

Storage is shared through a **DocumentsProvider** (`scoped.FolderProvider`, authorities from `resValue storageProviderAuthorities`) — the launcher writes files, the game process reads them via SAF-style URIs.

### 3.2 Entry chain

```
TestStorageActivity (launcher icon)
  └─ LauncherActivity  (fragments: MainMenuFragment, settings, auth, mod installers)
       └─ [Play] → MinecraftDownloader.start(...)          // download/verify version
            └─ ContextAwareDoneListener → Intent(MainActivity)
                 └─ MainActivity (:game)
                      ├─ starts + binds GameService (:game, foreground, specialUse FGS)
                      ├─ inflates activity_basemain (GL view, control layout, touchpad, drawer)
                      ├─ onAttachedToWindow → computeNotchSize, load control layout
                      ├─ onServiceConnected → MinecraftGLSurface.start()
                      └─ SurfaceReadyListener → runCraft()
                           └─ Tools.launchMinecraft()
                                └─ JREUtils.launchJavaVM() → VMLauncher.launchJVM(userArgs)  // native
```

### 3.3 The launch pipeline (`Tools.launchMinecraft`, `Tools.java:262`)

1. Memory sanity checks: free RAM vs. selected `-Xmx`; on 32-bit devices also the largest contiguous address-space hole (`MemoryHoleFinder` + `SelfMapsParser`) → warning dialog.
2. Render-distance guard when Sodium + LTW is in play (`checkRenderDistance`).
3. Pick runtime: profile override → `MultiRTUtils.getNearestJreName(requiredJavaVersion)` (`Tools.pickRuntime`, `:1249`).
4. Load version JSON (handles `inheritsFrom` inheritance, `Tools.getVersionInfo`).
5. Delete the Mojang splash screen (`disableSplash`), build client args.
6. `OldVersionsUtils.selectOpenGlVersion` — sets `LIBGL_ES` for ≤1.12 vs newer.
7. Build classpath: libraries + LWJGL3 `lwjgl-glfw-classes.jar`; adds log4j RCE patch configs from `assets/components/security`.
8. Per-version natives dir (`cache/natives/<version>`) → `-Djava.library.path` and `-Djna.boot.library.path`.
9. `JREUtils.launchJavaVM` (`JREUtils.java:280`):
   - `relocateLibPath`, `setJavaEnvironment` (a large env map: `LIBGL_*` for GL4ES, `MESA_*`/`POJAV_VSYNC_IN_ZINK` for Zink, `POJAV_RENDERER`, `POJAV_LOAD_TURNIP` on Adreno, `AWTSTUB_WIDTH/HEIGHT`, `POJAV_FFMPEG_PATH` if the external FFmpeg APK is installed)
   - strips hazardous user args (`-Xms`, `-Xmx`, `-d32`, `-Xint`, `-XX:+UseLargePages`, renderer/freetype overrides, `ActiveProcessorCount`)
   - injects `-Xms/-Xmx` from prefs, renderer lib name, device core count
   - `chdir(gamedir)`, then `VMLauncher.launchJVM(userArgs)` → `jre_launcher.c` (`JLI_Launch` typedef, abort-waiter thread for signal/exit trapping)
   - non-zero exit → "game crashed, share logs" dialog → `Tools.fullyExit()` (kills the process)

---

## 4. Native layer (`app_pojavlauncher/src/main/jni`)

| Area | Files | Notes |
|---|---|---|
| JVM entry | `jre_launcher.c` (GPLv2, Oracle header) | custom `JLI_Launch` caller, signal→exit-code conversion |
| Context bridges | `ctxbridges/{gl_bridge,osm_bridge,egl_loader,osmesa_loader,loader_dlopen,swap_interval_no_egl}.c` | function-pointer table (`bridge_tbl.h`) selecting either **EGL** (GL4ES/ANGLE/LTW) or **OSMesa** (Zink/VirGL) at runtime |
| Input | `input_bridge_v3.c` | the "input pipe rewritten to native code" — keys, mouse, gamepad events from Java |
| AWT | `awt_bridge.c`, `awt_xawt/xawt_fake.c`, `awt_headless` fake lib | lets AWT/Caciocavallo work without X11 |
| Hooks | `jvm_hooks/{java_exec_hooks,lwjgl_dlopen_hook,emui_iterator_fix_hook}.c`, `native_hooks/{exit_hook,chmod_hook}.c`, `driver_helper/{hook,nsbypass}.c` | intercept `Runtime.exec`, `dlopen` of liblwjgl, EMUI iterator bug, process exit; namespace bypass for loading driver `.so` from app storage |
| Env sharing | `environ/environ.c` | passes a C struct pointer between the Java process and the JVM's own copy of the lib via `POJAV_ENVIRON` |
| Misc | `bigcoreaffinity.c` (pin to big cores), `stdio_is.c` (redirect stdio to log), `utils.c` | |

Native libs are linked with `-ldl -llog -landroid` only; `exithook` links `bytehook` via Prefab. `Android.mk` even `rm`s fake `libawt_headless.so` after linking.

`jniLibs/` ships prebuilt: `liblwjgl*.so`, `libOSMesa.so`, `libgl4es_114.so`, `libopenal.so`, `libfreetype.so`, `libunpack200.so`, `libvulkan_freedreno.so` + `libVkLayer_khronos_timeline_semaphore.so` (arm64 only), `libtwitchsdk.so` (arm32 only), `libjnidispatch.so`.

---

## 5. `jre_lwjgl3glfw` — the compatibility shim

This module is the clever core: it is compiled into `assets/components/lwjgl3/lwjgl-glfw-classes.jar` and prepended to the game's classpath, **shadowing** real LWJGL:

- `org.lwjgl.glfw.GLFW` (1.4k lines) + `CallbackBridge` — NativeMethod stubs implemented in `pojavexec.so`; `CallbackBridge` is the giant in-JVM/Java↔native event bridge (window size, grab state, key/mouse dispatch).
- `org.lwjgl.opengl.GLCapabilities` (10.7k lines 😬) — precomputed GL capability tables so GL4ES/Zink contexts don't need real GL queries.
- `GLFWNative{EGL,OSMesa,Cocoa,NSGL,WGL,Wayland}` — platform entry points redirected to the bridge.
- Reimplementations of `android.util.{ArrayMap,MapCollections,ContainerHelpers,EmptyArray,Objects}` — because the real `android.util.ArrayMap` isn't visible to the desktop JVM runtime.
- `net.java.openjdk.cacio.ctc.{ExternalMouseReader,InfdevGrabHandler}`, plus a `ClientBrandRetriever.java.z` template (note the `.z` extension — it's a source template copied into versions that need it).

---

## 6. Feature surface

### Rendering backends (`headings_array.xml` + `Tools:1364`)
| id | Mechanism | Gate |
|---|---|---|
| `opengles2` | GL4ES 1.1.4 over GLES 2/"3" (EGL bridge) | always |
| `vulkan_zink` | VirGL/Zink → Vulkan via OSMesa | device advertises Vulkan **and** ABI has the binary (not 32-bit x86) |
| `opengles3_ltw` | ANGLE-based LTW | GLES3 + `libltw.so` present in the APK |

`PREF_USE_ALTERNATE_SURFACE` switches between `SurfaceView` and `TextureView` (`MinecraftGLSurface.start`).

### Multi-runtime management (`multirt/`)
- Runtimes live under `MULTIRT_HOME`; `Runtime` = parsed `release` file (`JAVA_VERSION`, `OS_ARCH`), optional `.version`/binpack metadata.
- `MultiRTUtils` handles: list/read/cache, nearest-major selection (`MathUtils.findNearestPositive`), install from `.tar.xz` (universal + per-arch binpack), `unpack200` for Java 8, `postPrepare` (symlinks/libs), and the bundled "Internal" JRE unpacked from assets by `AsyncAssetManager.unpackRuntime`.
- Profiles reference runtimes with the pseudo-URI `pojav://<runtime>` (`Tools.LAUNCHERPROFILES_RTPREFIX`).

### Accounts & auth (`authenticator/microsoft`, `value/`)
- **Microsoft OAuth** device-flow-ish login inside a WebView (`MicrosoftLoginFragment` → `login.live.com/oauth20_authorize.srf`), then `MicrosoftBackgroundLogin` chains: `oauth20_token` → XBL `user/authenticate` → XSTS `authorize` → `login_with_xbox` → `entitlements/mcstore` (ownership check) → `minecraft/profile`.
- **Local/offline** accounts (`LocalLoginFragment`), account list persisted as JSON (`MinecraftAccount`, `MinecraftAuthenticationDatabase`), skin/avatar via `mc-heads.net`.
- Profile JSON is a near-clone of Mojang's `launcher_profiles.json` schema (`value/launcherprofiles/*`).

### Downloads (`tasks/MinecraftDownloader`, `mirrors/`)
- Resolves version manifest → downloads version JSON (recursively for `inheritsFrom`), asset index, client JAR, libraries, extracts natives.
- `DownloadMirror` supports **BMCLAPI** (China-friendly mirror) with tamper detection (`MirrorTamperedException`) and hash verification (`PREF_CHECK_LIBRARY_SHA`).
- Thread-pooled with progress reporting (`ProgressKeeper` + `ProgressService` foreground service) and speed/byte counters.

### Mod loaders & modpacks (`modloaders/`)
- Fabric / Quilt (`FabriclikeUtils` against `meta.fabricmc.net`, `meta.quiltmc.org`), Forge (maven-metadata + installer JAR executed headlessly through the `forge_installer` agent, or with a real GUI via Caciocavallo), OptiFine (HTML scraping), Better Than Adventure.
- Modpacks: **Modrinth** + **CurseForge** search/install (`modpacks/api/*`), `.mrpack`/Curse manifest parsing, icon cache with a janitor, `ModpackInstaller` + `ModloaderInstallTracker`.

### Controls (`customcontrols/`)
The largest UI subsystem — a full on-screen control editor:
- `ControlLayout` renders JSON-defined buttons/drawers/joysticks from `default.json` (v4 format; older formats upgraded by `LayoutConverter` and validated by `LayoutSanitizer`).
- Dynamic positioning via expression strings (exp4j), e.g. `"${margin} * 3 + ${width} * 2"`.
- Per-control editing UI (`handleview/*`: move/resize/clone/delete/side dialog), drawers, sub-buttons, toggle/swipe buttons.
- Input pipelines: `Touchpad`, `InGameEventProcessor`/`InGUIEventProcessor`, `AndroidPointerCapture` (Android 8+ pointer capture), `GyroControl` (gyro-as-mouse), `HotbarView`, `Gamepad` + external `android_gamepad_remapper` library, `direct/DirectGamepad` (raw evdev-ish direct gamepad), custom keyboard (`LwjglCharSender`/`AwtCharSender`).

### Storage (`scoped/`)
`FolderProvider` (DocumentsProvider) + `MinecraftAccount`/profile storage; game dir defaults to `getExternalFilesDir()` on API 29+, `/sdcard/games/PojavLauncher` below (`Tools.getPojavStorageRoot`). `MissingStorageActivity` guards unmounted storage.

---

## 7. Data & asset flow at startup

`PojavApplication.onCreate` → `Tools.initEarlyConstants` / `initStorageConstants` →
`AsyncAssetManager.unpackSingleFiles` (`options.txt`, `default.json`, `launcher_profiles.json`, `resolv.conf`) →
`unpackComponents` (version-file-diffed unpack of `caciocavallo`, `caciocavallo17`, `lwjgl3`, `security`, `arc_dns_injector`, `forge_installer`) →
`unpackRuntime` (bundled Java 8 "Internal" runtime, if the APK has it).

Component versions are tracked by a plain-text `version` file next to each unpacked component; mismatch → delete + re-extract. This is why the `version` files are committed and rewritten by the companion Gradle modules.

---

## 8. Risks, tech debt, and gotchas

**Branding/identity (relevant if the goal is a fork):** renaming touches more than `strings.xml`:
- `app_pojavlauncher/build.gradle`: `namespace`, `applicationId`, `resValue application_package`, `storageProviderAuthorities`, `shareProviderAuthority`
- `Tools.APP_NAME` (`Tools.java:104`), `DIR_GAME_HOME` default (`:119`), `getPojavStorageRoot` (`:143`), `LAUNCHERPROFILES_RTPREFIX` (`pojav://`)
- `URL_HOME` (`Tools.java:108`) → still points to Pojav's site
- `PojavApplication` crash header; `about_en.txt`; `res/values/strings.xml` (`app_name`, `app_short_name`)
- Java package `net.kdt.pojavlaunch` (259 files) and `Pojav*` class names — Java-level rename is optional but the *package id* change forces users to reinstall (different app identity) and breaks update paths; keep a migration story in mind.
- `AndroidManifest` `<queries><package android:name="net.kdt.pojavlaunch.ffmpeg">` — the optional FFmpeg companion APK is matched by that exact id.

**Correctness/robustness observations**
- `versionCode` is **not monotonic offline**: `getDateSeconds()` returns constant `172005` without `GITHUB_ACTIONS`, so local builds can't upgrade over CI builds.
- `getVersionName()` shells out to `git describe --tags`; on this snapshot there are no tags, so it falls back to `LOCAL-<date>` and appends the branch name (which is why the version string will look odd here).
- `headings_array.xml` has duplicate `name="2"`/`name="3"` indices in `menu_customcontrol` (harmless but sloppy).
- `MainActivity` has a FIXME about `Logger.begin` thread-safety and several unused/TODO paths; 31 TODO/FIXME markers overall, mostly cosmetic.
- Two storage roots are in play (`DIR_GAME_HOME` pre-29 legacy path vs. app-scoped path) — a classic source of "my world disappeared" reports.
- No unit/instrumentation tests at all (`src/test`, `src/androidTest` absent), so any change is validated only by building + running on a device.
- ProGuard `proguard` build type minifies, but `release` explicitly does **not** ("Don't set to true or java.awt will be a.a") — worth respecting if you plan R8 experiments.
- Security-adjacent: CurseForge key is baked into resources (extractable from the APK); `PREF_JAVA_SANDBOX` enables pro-grade's SecurityManager, which is removed/deprecated on modern JDKs — expect friction on JDK 21 runtimes.
- Hardcoded third-party endpoints (`bmclapi2.bangbang93.com`, `optifine.net`, `maven.minecraftforge.net`, `mc-heads.net`) — no configurable mirror/proxy beyond the built-in BMCLAPI toggle.
- Heavy prebuilt binaries (130 MB of `.so`) are **in git**; the repo is already 190 MB with squashed history. Any fork will inherit that.

**Legal/practical:** upstream is discontinued and LGPLv3; `about_en.txt` still claims GPLv3; the "PojavLauncher" name/logo and Mojang/Microsoft trademarks are not yours to redistribute. A genuine "ApexLauncher" needs its own name, icon, `about` text, and a clear attribution section.

---

## 9. If the goal is "make this ApexLauncher" — minimal viable plan

1. **Identity pass**: applicationId (e.g. `app.apex.launcher`), `app_name`/`app_short_name`, icon set (`mipmap-*`), `about_en.txt`, `Tools.APP_NAME`, storage dir name, `URL_HOME`, crash header, README/LICENSE attribution + the Amethyst/LGPL notice.
2. **Versioning**: replace `getDateSeconds()` with a deterministic, increasing `versionCode` (e.g. `git rev-list --count HEAD` or a `version.properties`).
3. **Build reproducibility**: commit or script the JRE/LTW fetch step so a clean clone builds; mark `assets/components/*/version` as generated.
4. **First-run sanity**: verify the no-JRE path (`AsyncAssetManager.unpackRuntime` returning early) shows a clear "install a runtime" flow instead of failing at `pickRuntime`.
5. **Quality gates**: add a minimal `androidTest`/unit smoke target and a lint baseline; fix the `versionCode` and branch-name-in-version issues first since they block release automation.
6. **Optional cleanup**: drop unused ABIs (`x86` is dead weight for most users), move the 130 MB of `.so` to CI artifacts or LFS.

---

## 10. Where to look first (cheat sheet)

| Question | File |
|---|---|
| How does the app start? | `PojavApplication.java`, `TestStorageActivity`, `LauncherActivity.java` |
| How is the game launched? | `Tools.java:262` (`launchMinecraft`), `utils/JREUtils.java:280` (`launchJavaVM`) |
| How is the JVM started natively? | `jni/jre_launcher.c` |
| How does rendering work? | `MinecraftGLSurface.java`, `jni/ctxbridges/*` |
| How is input handled? | `jni/input_bridge_v3.c`, `jre_lwjgl3glfw/.../CallbackBridge.java` |
| How are runtimes managed? | `multirt/MultiRTUtils.java`, `tasks/AsyncAssetManager.java` |
| How are versions downloaded? | `tasks/MinecraftDownloader.java`, `mirrors/DownloadMirror.java` |
| How are controls defined? | `assets/default.json`, `customcontrols/ControlLayout.java`, `ControlData.java` |
| How do I add a setting? | `prefs/LauncherPreferences.java`, `res/xml/pref_{main,control,java,video,misc,experimental}.xml`, `prefs/screens/*` |
