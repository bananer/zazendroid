# ZazenDroid — Task List

Living plan for the three features below. Checked against code + `../balloon/data/raw/` on 2026-09-24.

Codebase facts (load-bearing):
- Tabs in `app/src/main/java/de/bananer/zazendroid/MainActivity.kt` (`TABS`): `home`, `courses`, `singles`; one shared `LibraryViewModel`, `NavHost` with `course/{courseId}` + `player` detail routes.
- Screens in `app/src/main/java/de/bananer/zazendroid/ui/screen/LibraryScreen.kt`: `HomeScreen` (continue + favorites), `CoursesScreen`, `SinglesScreen` — all fed by `LibraryUiState.Ready` in `ui/viewmodel/LibraryViewModel.kt`.
- Catalog model: `data/catalog/Catalog.kt` + `CatalogDto.kt` — `Course(id,title,description,authorName,categoryTitle?,units)`, `Single(id,title,description,authorName,categoryTitle?,durationSeconds,audioUrl)`. Only `categoryTitle: String?` today, no category id/list. `CatalogRepository` resolves relative `audioUrl` against server base URL; `Json { ignoreUnknownKeys = true }`.
- Local DB `data/local/ZazenDb.kt` v2 (`course_progress`, `favorite`) + `MIGRATION_1_2`, no destructive migration. Comment reserves `daily_checkin(dateEpochDay PK, sleepScore, stressScore, moodScore, note)` — claim the name, do not invent a second DB.
- DI: `AppContainer.kt` manual lazy singletons (`db`, repos, `catalogRepository`, `playbackManager`).
- Strings duplicated `res/values/strings.xml` + `res/values-de/strings.xml`; `TranslationParityTest` enforces parity — every new string needs both locales.
- Test catalog `test_data/catalog.json` (2 courses, 2 singles) + `CatalogMapperTest` fixture; converter `../balloon/tools/convert_to_zazen.py` maps curated `author`→`authorName`, `category`→`categoryTitle`, sorts by raw `prio`.
- Raw data findings (`../balloon/data/raw/`): `categories.json` = 19 flat categories (`id,title,description,prio,slug,subcategoryIds,…`); `courses.json` = 24 courses / 10 distinct category titles; `singles.json` = 217 singles / 17 distinct titles; all course/single `category.id`s resolve into `categories.json`. BUT the 20 distinct `subcategoryIds` referenced by 7 parent categories resolve to **nothing** (not in `categories.json`, only echoed inside embedded `category` objects in `courses/singles/popularSingles.json`). `rootCategories.json` is a GraphQL error stub, unusable. → No nestable subcategory data exists; model categories as a **flat, prio-sorted list**.

---

## 1. Search (4th tab: courses + units + singles)

**Decided:** 4th bottom-bar tab (not top-bar icon). Searches local loaded catalog only: course titles/descriptions, unit titles, single titles/descriptions. Case/diacritic-insensitive substring match.

- [x] Add `SearchScreen` + `search` tab entry (`MainActivity.kt` `TABS`, `NavHost composable("search")`, `tab_search` strings EN/DE, search icon).
- [x] Define search matching helper (pure, unit-testable, e.g. `data/catalog/Search.kt` or in `LibraryViewModel`): normalized `contains` over course (title+description), unit (title + parent course title?), single (title+description); empty query → empty/groups, not full dump.
 - [x] Search UI: text field on top, result rows grouped/labeled by type (course / unit-in-course / single); tapping course or unit → `course/{id}` detail (unit pre-selected/scrolled), single → queue + `player`. Reuse existing `CourseCard`/single row styles.
- [x] Wire to shared `LibraryViewModel` `Ready` state (same pattern as other tabs); no new repository. Handle `Loading` / `NeedsServerSetup` / `CatalogError` via existing `LibraryTabScaffold`.
- [x] Add EN + DE strings (`tab_search`, `search_hint`, `search_empty`, result type labels); keep `TranslationParityTest` green.
- [x] Extend `test_data/catalog.json` if needed for manual search verification (titles already distinct EN/DE — probably sufficient, no change).
- [x] Unit test matching helper (course hit, unit hit, single hit, description hit, diacritic/case-insensitivity, empty query); no UI test.

Accept: 4 tabs visible; typing part of any course/unit/single title or description filters to the right rows; taps navigate to existing detail/player; offline/error states match other tabs.

## 2. Daily mood tracking (check-in flow + history)

 **Decided:** 0–100 circular setter, numeric value never displayed; set by tapping the ring edge or dragging (gesture starts at top); one question per screen; confirm button below advances; after day-3 save show 14-day history bar graph (3 values/day, higher bar = more stressed) on result screen; one entry/day, after completion Home card switches to "view history" entry (no re-entry).

- [x] DB: add `daily_checkin` entity (`dateEpochDay PK: Long`, `sleepScore: Int 0–100`, `stressScore: Int`, `moodScore: Int`, `updatedAtEpochMs: Long`; no note field — not requested) + DAO (`get(date)`, `last14Days()`, `upsert`) + `MIGRATION_2_3`; register in `ZazenDb` (v3, `exportSchema = true`); wire repository (e.g. `data/mood/MoodRepository.kt`) in `AppContainer`.
 - [x] `MoodViewModel` + navigation: `mood` route (from Home button) with steps sleep → stress → satisfaction → result; `isCompleteToday` flow from repo; step state holds three 0–100 ints; ring starts at 0 (top) on Q1, Q2/Q3 start at the previous answer's value.
 - [x] Home: large check-in button card at top of `HomeScreen` (above Continue) (`LibraryUiState.Ready` + mood flow combine or separate collect in `HomeScreen`); tapping → `mood` route. Incomplete → invite label; completed → "view history" label → `mood` result/history route (view-only, no re-entry).
 - [x] Circular input widget (custom composable, Canvas or `CircularProgressIndicator`-based): 0–100, no numeric label, no legend/scale labels — only the question text above; tap-on-ring sets value (angle from 12 o'clock clockwise), drag rotates; accessibility action for ±steps (TalkBack needs a non-visual path since value is hidden). One widget reused for all 3 questions.
- [x] Question screens: German texts exactly `Hast du gut geschlafen?`, `Fühlst du dich gestresst?`, `Bist du zufrieden?` (+ EN equivalents in `values/strings.xml`, e.g. `Did you sleep well?` / `Do you feel stressed?` / `Are you satisfied?`); confirm button below (`Weiter`/`Fertig`, EN `Next`/`Done`); back allowed between steps without losing entered values; save all three atomically at the end (local date via `LocalDate.now()`, `toEpochDay()`).
- [x] Result screen after save: 14-day grouped bar graph (3 bars/day: sleep/stress/satisfaction, days with no entry empty/gapped), custom Canvas/Compose drawing, no new chart dependency; view-only (no edit control). Same screen serves as history view for completed days.
 - [x] Strings EN+DE for all of the above (button label, questions, confirm/back, history title + history legend, accessibility descriptions — no legend/labels on the input itself, just the question); keep parity test green.
- [x] Tests: DAO test (upsert/get/14-day window, overwrite-same-day), ViewModel or repo test (incomplete → complete transition, view-only blocking), mapper/scale test if angle↔0–100 conversion is a pure function. No numeric-display assertion (there is none by design).

 Accept: fresh day → big Home button → 3 one-by-one circular questions → save → 14-day 3-value bar graph; completed day → same card opens view-only history; airplane/offline works (Room-local); migration 2→3 covered.

 Open micro-decisions: none. Decided: ring starts 0 on Q1 then carries previous value; input shows question only, no legend; history higher bar = more stressed; completed day keeps Home card as history entry.

## 3. Categories (flat filter chips on Courses + Singles)

**Decided:** filter chips on both `CoursesScreen` and `SinglesScreen` (no new tab, no section grouping). Flat model — nesting dropped because `subcategoryIds` are unresolvable (see findings above).

 - [x] Data model: add `Category(id, title, prio)` to `CatalogDto`/`Catalog` + `categories: List<Category>` on catalog; replace `categoryTitle` with `categoryId: String` on `CourseDto`/`Course` and `SingleDto`/`Single` (display title resolves via `categories`; no dual-field compat — server/app deploy in sync). Sort categories by `prio` (missing → last, stable).
 - [x] `convert_to_zazen.py` (in balloon repo): read `data/raw/categories.json` (id/title/prio), emit top-level `categories`; map each course/single embedded `category.id` → `categoryId` + `category.title` → validated against `categories`; warn on unknown ids (should be zero today); document nesting as intentionally dropped (`subcategoryIds` unresolvable — see findings).
- [x] `test_data/catalog.json`: add `categories` array + `categoryId` on the 2 courses / 2 singles (use 2–3 test categories so chip filtering is exercisable); update `CatalogMapperTest` fixture + `CatalogRepositoryTest` if they assert exact catalog shape.
 - [x] UI: chip row (`FilterChip` / `AssistChip`, single-select + `All`) at top of `CoursesScreen` and `SinglesScreen`; chips list only categories that have content on that screen (per-screen subset, prio-ordered); selection filters displayed list by `categoryId`; per-screen selection state (hoist in screen or VM — survive rotation, reset on catalog reload is fine); empty-filter state reuses `singles_empty`-style string (add `courses_empty` + `filter_empty` EN/DE).
 - [x] Resolve display titles via `categories` id lookup in single rows / course cards; chips show category titles.
 - [x] Tests: mapper test (categories parsed + sorted, `categoryId` mapped), filter test (chip selection narrows list; `All` restores). Converter change verified by running it once (`python3 tools/convert_to_zazen.py`) and checking output shape.

 Accept: real + test catalogs carry `categories`; both screens show prio-ordered chips; selecting a chip filters to that category; converter + test data + mapper tests updated together.

---

## Cross-cutting

- [x] EN+DE strings for every new label (parity test must pass).
- [x] No new database, no new DI framework (`AppContainer` + `ZazenDb` migration only).
- [x] Manual smoke on device/emulator per feature (search tab, mood day-flow + completed-day state, chips on both screens) before closing its section.
