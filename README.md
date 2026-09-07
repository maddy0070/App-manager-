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
./gradlew test                 # 103 unit, composition and interaction tests, no device
```

The release variant is signed with the checked-in `app/debug.keystore`. That is deliberate for a
sideloaded personal build: the key is stable, so updates install over one another with
`adb install -r`. Swap in your own keystore before doing anything else with it.

`minSdk 29`, `targetSdk 36`.

---

## What it does

**Onboarding** — one field of five apps, measured twice. It opens unclaimed: five objects, no
order, all the same size, and one invitation. *Explore* measures them by screen time — position is
rank, width is hours, the bar reads the total. Touch an app and it *becomes* the detail surface;
drag that surface and the single figure it arrived with comes apart into app, data and cache.
Closing it re-measures the whole field by storage, and the order visibly contradicts itself: the
app used most is nearly the smallest, and the one barely opened in a fortnight is the largest thing
there. Hold to select — the real gesture, the real bar — and the weight accumulates; review gathers
the batch into a single measured rail. Nothing instructs. Runs entirely on sample data, so it
behaves identically on a fresh install with nothing scanned and nothing granted. Skippable.

**Overview** — a device summary: how many apps and the user/system split, storage broken down by
origin, your five most-used apps, app cache with its largest holders, what arrived recently, a
twelve-month install timeline, the largest apps, and how much is tied up in things you have not
opened in weeks.

**Cache** — every app holding one, ranked by size, by how much of the app it *is*, or by how long
since you last opened it. Manager measures it precisely and hands the clearing to Android, which is
the only thing that can do it; on the way back it measures again and tells you what actually came
back. Coverage is stated rather than assumed — apps Android declines to measure are counted
separately and never folded into the total.

**Apps** — the full inventory. Search by name or package, seven filters, five sort keys in both
directions. Long-press to enter selection mode; the navigation bar becomes an action bar carrying
the combined weight of the selection, which travels to its new value as apps go in and out.

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

**What Manager cannot do, and does not pretend to.** No normal Android app can delete another
app's cache or data. `deleteApplicationCacheFiles` is a system API; `freeStorageAndNotify` has
needed a signature-or-privileged permission since API 26. A sideloaded consumer install holds
neither. So Manager does everything on either side of that step instead — measure precisely, rank
usefully, open the exact system screen that can act, and measure again on the way back — and the
UI says which half is Android's. There is no button here that quietly does nothing.

---

## Design system

Everything visual resolves through `ManagerTheme`. Material is not a dependency at all — not the
colour scheme, not the type roles, not `Icon`, not the ripple — so there is no default to fall
back to and no way for one to leak in.

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
- The action bar answers "how much is this?" before you have asked: the count is trivia, the
  weight is the decision, and it accumulates rather than being redrawn.
- The uninstall confirmation leads with what comes back, drawn as the batch itself — one rail,
  one block per app, sized against each other — then states what Android removes, in order, with
  the real figures where it reported them and "not measured" where it did not.
- Cache cleanup is bracketed rather than claimed: measured before, handed to Android, measured
  after, and reported as the difference — including when the difference is nothing.
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

`./gradlew test` runs 103 tests with no device attached:

- **Format** — every unit, duration and date string the user reads.
- **Contrast** — the palette against WCAG, computed rather than eyeballed: every ink level on
  every ground it is actually drawn on, the accents where they carry text, both inverse surfaces,
  and the chart bands' separation from one another.
- **Insights** — dashboard derivation, including the cases that produce wrong numbers quietly:
  never-updated apps, usage records for packages that are gone, freshly installed apps.
- **Browsing** — search ranking, filter partitioning, sort stability.
- **Squircle** — the shape system at the sizes real layouts produce: tiny chips, extreme aspect
  ratios, radii larger than the box, zero sizes.
- **Storage** — every figure the product states about size: cache aggregation and its coverage,
  the three cache orderings, selection totals as apps go in and out, select-all, what happens to a
  total when one app in it was never measured, dormant bytes across the whole device rather than
  the visible slice, and what a re-measurement after the system screen is allowed to claim.
- **ScreenRender** — Robolectric renders the real Compose tree for every screen, both themes, plus
  each visualisation at its degenerate inputs and app names long enough to break a layout.
- **StoryFlow** — plays the onboarding with real gestures: explores the field, measures the
  objects against each other to prove width really is the datum, opens one, drags its figure apart,
  checks the two dimensions genuinely disagree, holds to select, watches the total accumulate and
  release, and reviews the batch. It also asserts that nothing on the screen instructs, and that no
  two objects can ever overlap. Runs on a small phone too.

Both render suites run at the target device's size with native graphics. Robolectric's defaults
are a 320x470 mdpi screen with stubbed text measurement that lays every string out one character
wide — a composition that survives that has not been meaningfully checked.
