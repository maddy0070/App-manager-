# Manager

A personal Android app manager for a Pixel 10 Pro XL. It reads what Android already knows about
every installed package — sizes, install and update dates, split structure, time spent — and lays
it out so you can act on it: inspect, extract APKs, uninstall, in bulk.

Built with Kotlin and Jetpack Compose. No third-party UI libraries: every surface, control, icon
and transition in the app is drawn by this codebase.

---

## Building

```bash
./gradlew assembleRelease      # app/build/outputs/apk/release/app-release.apk  (~3 MB)
./gradlew assembleDebug        # installs alongside release as com.manager.app.debug
./gradlew test                 # 71 unit, composition and interaction tests, no device
```

The release variant is signed with the checked-in `app/debug.keystore`. That is deliberate for a
sideloaded personal build: the key is stable, so updates install over one another with
`adb install -r`. Swap in your own keystore before doing anything else with it.

`minSdk 29`, `targetSdk 36`.

---

## What it does

**Onboarding** — a forty-second interactive story rather than a slideshow. A field of suspended
objects; tap one and that object grows into a detail surface; drag the surface and the numbers
fill in under your finger; hold an app to select it, pick two more and watch them converge into
the selection capsule; then the whole scattered field resolves into the list the product is. It
runs entirely on sample data, so it behaves identically on a fresh install with nothing scanned
and nothing granted. Skippable from anywhere.

**Overview** — a device summary: how many apps and the user/system split, storage broken down by
origin, your five most-used apps, what arrived recently, a twelve-month install timeline, the
largest apps, and anything untouched for three weeks or more.

**Apps** — the full inventory. Search by name or package, seven filters, five sort keys in both
directions. Long-press to enter selection mode; the navigation bar becomes an action bar.

**Usage** — every app ranked by foreground time over Today / 3 / 7 / 30 days.

**Detail** — tap any app anywhere and its icon flies into a floating panel carrying version,
dates, a storage breakdown, usage, and the two actions.

**Extraction** — writes real APKs to `Downloads/Manager`. Split installs become a single `.apks`
archive containing every part, because a lone `base.apk` from a split install cannot be
reinstalled. Results are shareable from the completion sheet.

**Uninstall** — single or bulk, through `PackageInstaller`.

---

## Honesty about the platform

The product's rule is that it never shows a number Android did not give it.

| Situation | What the app does |
|---|---|
| Usage access not granted | Storage falls back to APK size on disk, and says so in place. Usage screens explain what is missing and link to the right Settings page. |
| Ranges beyond a week | Labelled approximate — Android stops keeping daily buckets and aggregates into coarser ones. |
| `Android/obb` unreadable | Reported as unreadable, not as zero. |
| System package | Uninstall is disabled with the reason stated, unless it carries updates that can be removed. |
| Bulk uninstall | The confirmation says how many system prompts to expect. Android confirms every package itself; the app does not pretend otherwise. |
| APK unreadable | Extraction fails with the actual reason. |

Permissions: `QUERY_ALL_PACKAGES` (the product *is* the complete inventory),
`PACKAGE_USAGE_STATS` (user-granted; powers usage analytics and real storage figures),
`REQUEST_DELETE_PACKAGES`. Nothing leaves the device.

---

## Design system

Everything visual resolves through `ManagerTheme`. No screen reads a Material colour scheme or
type role.

**Colour — "Bone & Evergreen"** (`design/Color.kt`). A warm paper neutral rather than clinical
white, so surfaces read as printed matter. Deep evergreen is the only identity colour: selection,
primary action, data. Ember is reserved strictly for destructive intent and for the single
highlighted value in a visualisation. There is no third accent, on purpose. Dark mode is fully
architected on the same tokens; light mode carries the identity.

**Geometry — continuous curvature** (`design/Squircle.kt`). Every card, sheet, chip, button, field
and icon tile is cut from one function that builds each corner as cubic → arc → cubic, so
curvature ramps in and out instead of meeting the straight edge at a seam. `smoothing` is the same
0–1 quantity as Figma's corner smoothing. Adaptive app icons are re-masked with it, so a list full
of third-party icons still reads as one designed surface.

**Type** (`design/Type.kt`). Space Grotesk for display and every number; Inter for text and
metadata. Both variable, so each weight in the scale is a real instance.

**Motion** (`design/Motion.kt`). Two personalities and nothing else: springs for anything a finger
caused, eased tweens for anything the system caused. Nothing in the app animates on a default spec.

**Signature interactions**
- The detail panel's shared-element flight from the tapped row.
- Pull-to-refresh assembles the app's mark tile by tile as you pull.
- The navigation bar and the selection action bar share one capsule and one slot.
- Selection inverts the whole row rather than adding a checkbox to a gap.

---

## Architecture

Single module. A hand-written object graph (`ManagerGraph`) rather than a DI framework — one
process, one activity, a handful of long-lived collaborators.

```
data/      PackageRepository · UsageRepository · ApkExtractor · UninstallCoordinator
           IconLoader · PermissionMonitor · PreferencesStore
domain/    Insights (dashboard derivation) · Browsing (filter/search/sort)  — pure functions
design/    Colour · Type · Squircle · Spacing · Motion · Icons · components/
ui/        ManagerViewModel · AppRoot · onboarding · dashboard · apps · usage · detail · overlays
```

Package discovery runs in two passes: cheap metadata first so the list paints immediately, then
`StorageStatsManager` and OBB sizes streamed in batches. Neither blocks the main thread. Derived
lists are computed on `Dispatchers.Default` and cached as state flows.

One view model serves every screen, because every screen reads the same inventory — splitting it
would mean scanning the device twice and letting two screens disagree about what is installed.

---

## Tests

`./gradlew test` runs 71 tests with no device attached:

- **Format** — every unit, duration and date string the user reads.
- **Insights** — dashboard derivation, including the cases that produce wrong numbers quietly:
  never-updated apps, usage records for packages that are gone, freshly installed apps.
- **Browsing** — search ranking, filter partitioning, sort stability.
- **Squircle** — the shape system at the sizes real layouts produce: tiny chips, extreme aspect
  ratios, radii larger than the box, zero sizes.
- **ScreenRender** — Robolectric renders the real Compose tree for every screen, both themes, plus
  each visualisation at its degenerate inputs and app names long enough to break a layout.
- **StoryFlow** — plays the onboarding end to end with real gestures: taps an object open, drags
  the surface to fill it in, long-presses to select, picks two more, and checks the story reaches
  its ending with a working way out. Also runs the whole thing on a small phone.

Both render suites run at the target device's size with native graphics. Robolectric's defaults
are a 320x470 mdpi screen with stubbed text measurement that lays every string out one character
wide — a composition that survives that has not been meaningfully checked.
