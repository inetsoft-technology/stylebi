# Modernize Dialog Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn Modernize into a dialog offering "Keep my formatting" (today's Modernize plus residue cleanup) or "Reset all look and feel to modern defaults" on a saved copy, with a script warning and an Options-pane hint.

**Architecture:** Every author-settable look property of an assembly becomes a `ChromeSlot` that can be read, compared and cleared on the assembly or on any clone of it. Residue cleanup compares each author value with what a classic clone (author value removed, legacy seed, chart text defaults as the render builds them) resolves to. The reset refreshes DEFAULT tiers from a freshly initialised instance of the same type, clears every slot, and stamps and seeds. A new STOMP event saves the copy through the existing Save As path, resets it and saves again, deleting the copy if anything after the first save fails.

**Tech Stack:** Java 21, Spring, JUnit 5 + Mockito (`core`); Angular 21 standalone components, Vitest, `@testing-library/angular` + MSW (`web/projects/portal`).

**Spec:** `docs/superpowers/specs/lookfeel/2026-11-04-modernize-dialog-design.md` (read it first; section numbers below refer to it).

## Global Constraints

- Repository: community submodule, branch `feature-80398` off `epic-74519`, worktree `C:/Users/Franky/AppData/Local/Temp/claude/714db04ea36030c4/71cb27b8-0ef2-4f3b-84e7-2cae8979a810/scratchpad/feature-80398`. All paths below are relative to it. Java sources: `core/src/main/java/inetsoft/` (abbreviated `J/`); Java tests: `core/src/test/java/inetsoft/` (`T/`); portal: `web/projects/portal/src/app/` (`P/`).
- Java test classes that build a `Viewsheet` copy the header of `T/uql/viewsheet/internal/VizModernizeUtilTest.java:46-60` (SpringExtension, `BaseTestConfiguration` + `LibManagerTestConfiguration`, `ConfigurationContextInitializer`, `@SreeHome`, `@Tag("core")`, an `@AfterEach` that nulls `viewsheet.modernVisualization` and `viewsheet.darkMode`). Mockito service tests copy `T/web/composer/vs/controller/ModernizeViewsheetServiceTest.java`.
- Run one Java class: `./mvnw -q test -pl core -Dtest=<Class>` from the worktree root. Run each new or changed class on its own.
- Run one portal TL spec: `cd web && npx ng run portal:test-tl --include="**/<name>.tl.spec.ts"`. Never run the full TL suite. Plain specs: `cd web && npx ng test portal --include="**/<name>.spec.ts"`.
- No comments in `.html` templates. Code comments are one short clause; no ticket numbers, PR numbers or design-doc references in code comments.
- Allowlist (spec §5.1) — component formats: foreground, background (incl. gradient), font, borders, border colours, transparency, round corner. Chart text: font, colour, background, transparency. Never cleared: alignment, wrapping, number/date format and extent, presenter, span, rotation, highlights, bindings, scripts, positions, author sizes, title height.
- Font rule (spec §5.1): titles (`VSAssemblyInfo.TITLEPATH`), chart titles, axis labels, axis titles, legend titles and legend items reset the whole font. Every other font keeps the author's size and `StyleFont` style bits and takes today's default family.
- Expression- or variable-bound values (design value starting with `=` or `$(`) are never cleared and never residue.
- Residue (spec §4) runs only on an unmarked→marked transition (`VizModernizeUtil.moveMark` with a non-null target mark, per target whose mark is null). Never on Revert, light↔dark flips, density re-seed or restore. Strict equality with the classic resolved value.
- Table, crosstab and calc **cell** paths are never residue-eligible: their classic value is built at render from the table style and data (`DataVSAQuery:101`, `VSFormatTableLens.createCellFormat:246`), so it cannot be computed here. They are still cleared by the reset. (Task 4 records this in the spec.)
- Subagents cannot run `git add`/`git commit` (hook-blocked). The implementer leaves the task's files staged; the controller commits. Commit messages start `Feature #80398, ` and end with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. Run git commands one per Bash call, never chained.
- Strings go in `core/src/main/resources/inetsoft/util/srinter.properties`, kept alphabetically sorted. No apostrophes in parameterised values (MessageFormat). Exact copy:

| key | value |
|---|---|
| `composer.vs.modernize.componentCount` | `{0} components` |
| `composer.vs.modernize.componentCount.one` | `1 component` |
| `composer.vs.modernize.copyCreated` | `Created {0}.` |
| `composer.vs.modernize.copyCreated.scripts` | `Created {0}. Scripts set formatting on: {1}. The reset does not change scripts, so they can still override the new defaults.` |
| `composer.vs.modernize.copyName` | `{0} (modern)` |
| `composer.vs.modernize.copyNameN` | `{0} (modern {1})` |
| `composer.vs.modernize.createCopy` | `Create modern copy` |
| `composer.vs.modernize.dialog.title` | `Modernize Dashboard` |
| `composer.vs.modernize.embedded` | `Embedded dashboards are not changed.` |
| `composer.vs.modernize.keep` | `Keep my formatting` |
| `composer.vs.modernize.keep.description` | `Adopts the modern look for components still on the classic look and keeps every formatting choice you made. Revert brings the classic look back.` |
| `composer.vs.modernize.keep.none` | `Everything already uses the modern look.` |
| `composer.vs.modernize.links` | `Bookmarks, schedules and links stay with the original.` |
| `composer.vs.modernize.nameExists` | `A dashboard with this name already exists.` |
| `composer.vs.modernize.optionsHint` | `This keeps your formatting. To reset it, use Modernize... to create a modern copy.` |
| `composer.vs.modernize.reset` | `Reset all look and feel to modern defaults` |
| `composer.vs.modernize.reset.description` | `Overwrites your formatting on a saved copy. The original dashboard is not changed.` |
| `composer.vs.modernize.reset.neverSaved` | `Save the dashboard first.` |
| `composer.vs.modernize.saveAs` | `Save as` |
| `composer.vs.modernize.scripts` | `Scripts set formatting on {0}. The reset does not change scripts, so a script that sets formatting can still override the new defaults.` |
| `composer.vs.modernize.scripts.dashboardOnly` | `The dashboard script sets formatting. The reset does not change scripts, so a script that sets formatting can still override the new defaults.` |
| `composer.vs.modernize.scripts.withDashboard` | `Scripts set formatting on {0} and in the dashboard script. The reset does not change scripts, so a script that sets formatting can still override the new defaults.` |
| `composer.vs.revert.confirmCopy` | `Revert this dashboard to the classic look? This is a modern copy whose earlier formatting was reset, so Revert gives the classic defaults, not the original formatting. The original dashboard, {0}, is the way back to that.` |

## Review Focus

1. **Double submit of "Create modern copy"** — exactly one copy is made. Pinned by Task 10 (button emits once) and Task 9 (a second request for a now-taken name is refused with nothing saved).
2. **The copy name is held by another open runtime** (`SaveViewsheetDialogService.saveViewsheet` throws `common.overwriteForbidden` from `IsDuplicateTask`) — refused, the runtime stays on the original, nothing deleted. Pinned by Task 9.
3. **A name with banned characters or surrounding spaces** — rejected or trimmed exactly as Save As does, on the client and again on the server. Pinned by Task 10 and Task 9.
4. **A dashboard with bottom tabs** — after the reset re-seeds title lanes, every child still stands on its strip. Pinned by Task 5.
5. **A chart whose object font is a real choice while its axis fonts equal the plain default** — "Keep my formatting" keeps the axis fonts, because the classic axis font inherits the object font. Pinned by Task 4.

---

### Task 1: Chrome slots for component formats

**Files:**
- Create: `J/uql/viewsheet/internal/ChromeSlot.java`, `J/uql/viewsheet/internal/FormatChromeSlots.java`
- Modify: `J/uql/viewsheet/internal/VSAssemblyInfo.java` (add `chromeSlots()` beside `seedChromeDefaults` ~:1284), `J/uql/viewsheet/internal/TableDataVSAssemblyInfo.java`, `J/uql/viewsheet/internal/SelectionBaseVSAssemblyInfo.java`, `J/uql/viewsheet/internal/ShapeVSAssemblyInfo.java`, `J/uql/viewsheet/internal/ImageVSAssemblyInfo.java`
- Test: `T/uql/viewsheet/internal/ChromeSlotTest.java`

**Interfaces:**
- Produces:
  - `public final class ChromeSlot` with factories
    `static ChromeSlot tiered(String key, boolean objectLevel, boolean residueEligible, Predicate<VSAssemblyInfo> authorSet, Predicate<VSAssemblyInfo> expression, Function<VSAssemblyInfo, Object> authorValue, Function<VSAssemblyInfo, Object> resolvedValue, Consumer<VSAssemblyInfo> clearAuthor, BiConsumer<VSAssemblyInfo, VSAssemblyInfo> reset)` (a null `reset` means "clear the author value") and
    `static ChromeSlot plain(String key, BiConsumer<VSAssemblyInfo, VSAssemblyInfo> copyFromFresh)` (reset-only, never residue);
    accessors `String key()`, `boolean objectLevel()`, `boolean residueEligible()`, `boolean isAuthorSet(VSAssemblyInfo)`, `boolean isExpression(VSAssemblyInfo)`, `Object authorValue(VSAssemblyInfo)`, `Object resolvedValue(VSAssemblyInfo)`, `void clearAuthor(VSAssemblyInfo)`, `void reset(VSAssemblyInfo info, VSAssemblyInfo fresh)` (`fresh` may be null: plain slots then do nothing). Lambdas address the property through the info passed in, so one slot works on the assembly, a clone, or a fresh instance.
  - `enum FormatChromeSlots.Property { FOREGROUND, BACKGROUND, GRADIENT, FONT, BORDERS, BORDER_COLORS, ALPHA, ROUND_CORNER }`
  - `static String FormatChromeSlots.key(TableDataPath path, Property prop)` — equal for equal paths (build it from `getType()`, `getLevel()`, `getIndex()`, `isRow()`, `isCol()` and `getPath()`).
  - `static List<ChromeSlot> FormatChromeSlots.forPath(TableDataPath path, boolean residueEligible)` — eight tiered slots. `objectLevel` is true only for `VSAssemblyInfo.OBJECTPATH`.
  - `protected List<ChromeSlot> VSAssemblyInfo.chromeSlots()` — `forPath` for every `getFormatInfo().getPaths()` entry, plus a padding slot (author set = `isUserPadding()`; clear = `setUserPadding(false)`; value = `getPadding()`).
  - `TableDataVSAssemblyInfo.chromeSlots()` — cell paths (types `HEADER`, `DETAIL`, `TRAILER`, `GROUP_HEADER`, `SUMMARY`, `SUMMARY_HEADER`) use `residueEligible=false`; adds `plain("table:style", …)` copying `getTableStyleValue()` from fresh; adds a cell-padding slot (`isUserCellPadding()` / `resetUserCellPadding()` at :949/:957).
  - `SelectionBaseVSAssemblyInfo.chromeSlots()` — adds its cell-padding slot (`isUserCellPadding` :223).
  - `ShapeVSAssemblyInfo.chromeSlots()` (rectangle, oval, line) and `ImageVSAssemblyInfo.chromeSlots()` return `List.of()` — spec §5.4 leaves shapes and images alone, for both the reset and residue.

Slot behaviour for a format slot: author set = `getUserDefinedFormat().is*ValueDefined()`; author value = the typed USER value (`Color`, `Font`, `Insets`, `BorderColors`, `GradientColor`, `Integer`); resolved value = the same property resolved through `VSCompositeFormat` (USER→CSS→DEFAULT), reading non-object paths through `getFormatInfo().getFormat(path, false)` so the OBJECTPATH inheritance the render applies is included; clear = the `set*Value(<null|100|0>, false)` undefine from the API table (alpha `100`, round corner `0`, font `setFontValue(null)`); expression = the design string starts with `=` or `$(`. Font reset when `path != TITLEPATH` and the author font is set: replace the USER font with `new StyleFont(family, authorStyle, authorSize)`, where `family` is the family of `fresh`'s DEFAULT font at the same path, else `fresh`'s OBJECTPATH DEFAULT font, else `info.getFormatInfo().getFormat(path).getDefaultFormat().getFontValue()`.

- [ ] **Step 1: Write the failing tests** in `ChromeSlotTest`

```java
@Test void eachStoredPathYieldsTheEightLookSlots()          // Text1 OBJECTPATH: all eight keys present, objectLevel true
@Test void clearingASlotUndefinesOnlyThatProperty()          // USER fg red + USER alignment → clear fg: fg not defined, alignment still defined
@Test void numberFormatAlignmentAndWrapAreNotSlots()        // reset every slot of a Table: getFormatValue(), alignment, wrapping still defined
@Test void anExpressionBoundForegroundIsFlagged()            // USER fg "=if(true,'red','blue')" → isExpression true
@Test void aTitleFontResetsWhole()                           // Table TITLEPATH USER Arial-BOLD-16 → reset → font not defined
@Test void anObjectFontKeepsSizeAndStyleAndTakesTheFreshFamily() // OBJECTPATH USER Arial-ITALIC-20, fresh default family F → USER font == new StyleFont(F, Font.ITALIC, 20)
@Test void paddingSlotClearsUserPadding()                    // setUserPadding(true) → reset → isUserPadding() false
@Test void tableCellPathsAreNotResidueEligible()             // Table with a HEADER path → that path's slots residueEligible false; OBJECTPATH true
@Test void tableStyleResetsToTheFreshValue()                 // setTableStyleValue("X") → reset with fresh whose style is S → getTableStyleValue() == S
@Test void shapesAndImagesHaveNoSlots()                      // RectangleVSAssembly and ImageVSAssembly with USER fg → chromeSlots() empty
```

- [ ] **Step 2: Run** `./mvnw -q test -pl core -Dtest=ChromeSlotTest` — expect compilation failure (`ChromeSlot` missing).
- [ ] **Step 3: Implement** `ChromeSlot`, `FormatChromeSlots` and the three `chromeSlots()` overrides as specified.
- [ ] **Step 4: Run** `./mvnw -q test -pl core -Dtest=ChromeSlotTest` — expect PASS.
- [ ] **Step 5: Stage** the five files; controller commits `Feature #80398, chrome slots for component formats`.

### Task 2: Extract chart text defaults from VGraphPair

**Files:**
- Create: `J/report/composition/graph/ChartFormatDefaults.java`
- Modify: `J/report/composition/graph/VGraphPair.java` (`fixChartFormat` :1060 and its private helpers `copyDefaultFormat` :1429/:1483, `initDefaultFormat(ChartRef…)`, `initDefaultFormat(CompositeTextFormat…)`)
- Test: `T/report/composition/graph/ChartFormatDefaultsTest.java`

**Interfaces:**
- Produces: `public static void ChartFormatDefaults.fix(ChartVSAssemblyInfo info)` — the body of today's `fixChartFormat`, moved verbatim with its helpers; `VGraphPair.fixChartFormat(info)` becomes a one-line delegation. If the moved code reads any `VGraphPair` instance field, pass it as a parameter instead and say so in the report.

- [ ] **Step 1: Write the failing tests**

```java
@Test void anAuthorObjectFontBecomesTheAxisLabelDefault()   // chart OBJECTPATH USER font Arial-BOLD-14 → fix → x-axis getAxisLabelTextFormat().getDefaultFormat().getFont() equals it
@Test void anUnmarkedChartGetsTheLegacyPerColumnLabelColor() // per-column label format DEFAULT color == GDefaults.DEFAULT_TEXT_COLOR
@Test void aMarkedChartGetsTheModernPerColumnLabelColor()    // MODERN_LIGHT → == VSChartChromeDefaults.labelColor(VizContext.of(info))
@Test void legendTitleDefaultsMatchTheDescriptorsOwnInit()   // after fix, legend title DEFAULT color/font == a LegendsDescriptor freshly initDefaultFormat(VizContext.of(info))
```

- [ ] **Step 2: Run** `./mvnw -q test -pl core -Dtest=ChartFormatDefaultsTest` — expect compilation failure.
- [ ] **Step 3: Move the code** as specified.
- [ ] **Step 4: Run** `./mvnw -q test -pl core -Dtest='ChartFormatDefaultsTest,CSSChartStylesModernChromeTest,ChartInsetCssOverrideTest,SeedChromeDefaultsTest,VizModernizeUtilTest'` — expect PASS.
- [ ] **Step 5: Stage**; controller commits `Feature #80398, extract chart text defaults from VGraphPair`.

### Task 3: Chrome slots for charts

**Files:**
- Create: `J/uql/viewsheet/internal/ChartChromeSlots.java`
- Modify: `J/uql/viewsheet/internal/ChartVSAssemblyInfo.java` (override `chromeSlots()`), `J/uql/viewsheet/graph/PlotDescriptor.java`, `J/uql/viewsheet/graph/LegendsDescriptor.java`
- Test: `T/uql/viewsheet/internal/ChartChromeSlotsTest.java`

**Interfaces:**
- Consumes: `ChromeSlot` (Task 1).
- Produces:
  - `public CompositeValue<?> PlotDescriptor.chromeValue(String field)` for `xGridColor`, `yGridColor`, `xGridStyle`, `yGridStyle`, `diagonalColor`, `diagonalStyle`, `quadrantColor`, `quadrantStyle`, `facetColor`, `bgColor`; `IllegalArgumentException` otherwise.
  - `public CompositeValue<?> LegendsDescriptor.chromeValue(String field)` for `borderColor`, `border`, `gap`, `padding`.
  - `static List<ChromeSlot> ChartChromeSlots.of(ChartVSAssemblyInfo info)`; `ChartVSAssemblyInfo.chromeSlots()` returns `super.chromeSlots()` plus these. All chart slots have `objectLevel=false`.

Slots (spec §5.1), each addressed by a locator from the info so it works on clones:
- Text formats (tiered, props FONT/COLOR/BACKGROUND/ALPHA via `TextFormat` `is*Defined` / `set*(v, false)`; resolved = `CompositeTextFormat.get*()`): whole-font — legends title, colour/shape/size legend content, the four titles, every axis label format reachable from the design `VSChartInfo` and its refs, including per-column label formats; keep-size-and-style — the plot text format and every aggregate/dimension ref text format and target label format (family from the format's DEFAULT font).
- Plot and legend composite values (tiered via `chromeValue`: `hasUserValue()` / `get(USER)` / `get()` / `resetValue(USER)`).
- Plain: legend `getLayout()`/`setLayout` and `isRoundCorners()`; plot `getTrendLineColor`/`getBorderColor`; each axis descriptor's line colour.
- Flag slots (tiered): bar corner radius (`isUserBarCornerRadius` / `setUserBarCornerRadius(false)`, value `getBarCornerRadius()`), smooth lines (same pattern).
- Colour frames on design aesthetic refs (`getAestheticRefs(false)`): one tiered slot per `CategoricalColorFrame.getUserColors()` entry (resolved = CSS colour at that index, else `getDefaultColor(i)`; clear = remove the entry); one plain slot that clears every per-value colour with `clearStatic()` (derived colours are recomputed at render); a tiered slot for `StaticColorFrame.getUserColor()` (resolved = CSS colour else `getDefaultColor()`); a plain slot that replaces a `LinearColorFrame` with `VSChartPaletteDefaults.defaultLinearFrame(VizContext.of(fresh))` when its class differs.

- [ ] **Step 1: Write the failing tests**

```java
@Test void aUserGridColorIsATieredSlot()            // setXGridColor(#d2d2d2, USER) → slot authorSet, clear → chromeValue("xGridColor").hasUserValue() false
@Test void axisLabelFontsResetWhole()               // axis label USER Arial-BOLD-16 → reset → not fontDefined
@Test void dataLabelFontsKeepSizeAndStyle()         // plot text USER Arial-BOLD-16 → reset → font size 16, BOLD, family == DEFAULT family
@Test void legendPositionResetsToTheFreshLayout()  // setLayout(BOTTOM) → reset with a fresh chart → getLayout() == LegendsDescriptor.RIGHT
@Test void authorPaletteEntriesAndValueColorsClear() // userColors {0: red}, setColor("West", red) → reset → getUserColors() empty, isStatic("West") false
@Test void aStaticAuthorColorClears()               // setUserColor(red) → reset → getUserColor() null
@Test void barCornerAndSmoothLineFlagsClear()       // both user flags true → reset → both false
@Test void anAuthorRampIsReplacedByTheMarksRamp()   // SpectralColorFrame → reset (fresh MODERN_LIGHT) → class == defaultLinearFrame(VizContext.of(MODERN_LIGHT)).getClass()
```

- [ ] **Step 2: Run** `./mvnw -q test -pl core -Dtest=ChartChromeSlotsTest` — expect FAIL.
- [ ] **Step 3: Implement** as specified.
- [ ] **Step 4: Run** `./mvnw -q test -pl core -Dtest='ChartChromeSlotsTest,ChromeSlotTest'` — expect PASS.
- [ ] **Step 5: Stage**; controller commits `Feature #80398, chrome slots for charts`.

### Task 4: Residue cleanup on Modernize and the Options switch

**Files:**
- Modify: `J/uql/viewsheet/internal/VizModernizeUtil.java` (`moveMark` :105), `docs/superpowers/specs/lookfeel/2026-11-04-modernize-dialog-design.md` (§4: add the table-cell exclusion from Global Constraints), `docs/superpowers/specs/lookfeel/chart-card-roadmap.md` (P6 "byte-comparable" lines ~:884 and ~:1303 → "renders identically", with one sentence why)
- Test: `T/uql/viewsheet/internal/VizModernizeResidueTest.java`

**Interfaces:**
- Consumes: `ChromeSlot`, `chromeSlots()` (Tasks 1, 3), `ChartFormatDefaults.fix` (Task 2).
- Produces: `static int VizModernizeUtil.clearResidue(VSAssemblyInfo info)` (package-private; returns how many slots were cleared). `moveMark` calls it for each target whose `getVizMark() == null` when `mark != null`, before `seedAll`.

Algorithm (fixed by the spec's guarantee; dependent formats inherit only from OBJECTPATH, so two passes suffice):

```
candidates = info.chromeSlots() filtered: residueEligible && isAuthorSet(info) && !isExpression(info)
for pass in [objectLevel slots, all other slots]:
   if pass is empty: continue
   shadow = info.clone()                       // deep; mark already null
   for slot in pass: slot.clearAuthor(shadow)
   shadow.seedChromeDefaults(VizContext.of(shadow))
   if shadow is ChartVSAssemblyInfo: ChartFormatDefaults.fix(shadow)
   for slot in pass:
      if Objects.equals(slot.authorValue(info), slot.resolvedValue(shadow)): slot.clearAuthor(info); count++
```

- [ ] **Step 1: Write the failing tests** (live `format.css` via `DataSpace.getDataSpace().withOutputStream("portal", "format.css", …)` + `CSSDictionary.resetDictionaryCache()`, as `ChartInsetCssOverrideTest:103-108`; delete it in `@AfterEach`)

```java
@Test void aTitleBackgroundEqualToTheClassicValueIsDropped()   // Table TITLEPATH USER bg VSAssemblyInfo.DEFAULT_TITLE_BG → modernize → bg not USER-defined
@Test void aRealTitleBackgroundSurvives()                      // USER bg 0xff0000 → modernize → still defined, == 0xff0000
@Test void todaysClassicGridlineIsDroppedAnOlderGreyIsKept()   // chart A USER xGrid #eeeeee → cleared; chart B USER xGrid #d2d2d2 → kept
@Test void anAxisFontEqualToItsClassicDefaultIsDropped()       // axis label USER font == its DEFAULT after ChartFormatDefaults.fix on an unmarked clone → cleared
@Test void anAxisFontIsKeptWhenTheObjectFontIsARealChoice()    // OBJECTPATH USER Arial-BOLD-14, axis USER = plain default font → axis kept
@Test void aValueOverridingACssValueIsNotResidue()             // format.css "ChartPlot { line_x_color: #123456; }", USER xGrid #eeeeee → kept
@Test void anExpressionIsNeverResidue()                        // USER fg "=1" → kept
@Test void tableCellsAreNeverResidue()                         // HEADER path USER fg equal to its stored DEFAULT → kept
@Test void theOptionsSwitchAlsoDropsResidue()                  // applyMark(vs, MODERN_LIGHT) on an unmarked table with title-bg residue → cleared
@Test void revertAndADarkFlipDoNotRunResidue()                 // marked table with USER title bg == classic: revert → still defined; applyMark(MODERN_DARK) → still defined
@Test void modernizeThenRevertResolvesLikeNeverModernized()    // sheet with residue in table + chart: modernize+revert; every slot key's resolvedValue (after ChartFormatDefaults.fix) equals an untouched copy's
```

- [ ] **Step 2: Run** `./mvnw -q test -pl core -Dtest=VizModernizeResidueTest` — expect FAIL.
- [ ] **Step 3: Implement** `clearResidue` and the `moveMark` call; amend the two docs.
- [ ] **Step 4: Run** `./mvnw -q test -pl core -Dtest='VizModernizeResidueTest,VizModernizeUtilTest,SeedChromeDefaultsTest,ViewsheetPropertyVizTest'` — expect PASS.
- [ ] **Step 5: Stage**; controller commits `Feature #80398, drop residue when content moves to a modern mark`.

### Task 5: Reset to modern defaults

**Files:**
- Create: `J/uql/viewsheet/internal/ChromeReset.java`
- Modify: `J/uql/viewsheet/internal/VizModernizeUtil.java`
- Test: `T/uql/viewsheet/internal/VizModernizeResetTest.java`

**Interfaces:**
- Consumes: Tasks 1-3.
- Produces:
  - `static VSAssemblyInfo ChromeReset.freshDefaults(VSAssemblyInfo info, VizMark mark)` — a new instance of `info.getClass()` with `info`'s viewsheet, stamped `mark`, after `initDefaultFormat()`; null (and a debug log) when the type cannot be instantiated.
  - `static void ChromeReset.refreshDefaults(VSAssemblyInfo info, VSAssemblyInfo fresh)` — for each path of `fresh.getFormatInfo().getPaths()`, copy the eight allowlisted properties' design values and defined flags from `fresh`'s DEFAULT `VSFormat` into `info`'s composite DEFAULT at that path, creating the composite when `info` lacks it. Never installs a composite over an existing one (`setDefaultFormat` builds new composites, `VSAssemblyInfo.java:1209`).
  - `public static int VizModernizeUtil.resetToModern(Viewsheet vs)` — mark = the sheet info's own mark if set, else `VizMark.fromGate()`; returns 0 and changes nothing when that is null. Targets = `collect(vs, info -> true)` (embedded children excluded as today). Per target: `fresh = freshDefaults`, `refreshDefaults`, `ChartFormatDefaults.fix` for charts, then `slot.reset(info, fresh)` for every slot that is not an expression. Then `seedAll(vs, mark, targets, true, true)`, `restoreContainedSliders(targets)`, `keepOnBottomTabs(vs)`. Returns the target count.

- [ ] **Step 1: Write the failing tests**

```java
@Test void resetClearsAuthorLookOnMarkedAndUnmarkedAssemblies() // marked + unmarked Text with USER fg/bg/border → none defined after
@Test void resetRefreshesAnOldReleaseDefaultFont()               // Text OBJECTPATH DEFAULT font Arial-PLAIN-10 → after == freshDefaults(...) DEFAULT font
@Test void resetKeepsWhatItMustKeep()                            // USER number format, alignment, pixel offset/size, userTitleHeight 44 → all unchanged
@Test void resetKeepsExpressionBoundValues()                     // USER fg "=1" → unchanged
@Test void resetUsesTheSheetsOwnMark()                           // sheet MODERN_DARK → every target MODERN_DARK
@Test void resetFallsBackToTheGateAndDoesNothingWithItOff()      // unmarked sheet: gate on → MODERN_LIGHT; gate off → returns 0, USER values intact
@Test void resetLeavesEmbeddedAssembliesAlone()                  // fixture as VizModernizeUtilTest:276; InnerText USER fg survives
@Test void resetKeepsBottomTabsChildrenOnTheStrip()              // bottom-tabs Tab + child with a title → after reset child bottom == strip top (TabVSAssemblyInfo.getBottomTabChildHeight)
```

- [ ] **Step 2: Run** `./mvnw -q test -pl core -Dtest=VizModernizeResetTest` — expect FAIL.
- [ ] **Step 3: Implement** `ChromeReset` and `resetToModern`.
- [ ] **Step 4: Run** `./mvnw -q test -pl core -Dtest='VizModernizeResetTest,VizModernizeResidueTest,VizModernizeUtilTest'` — expect PASS.
- [ ] **Step 5: Stage**; controller commits `Feature #80398, reset a dashboard to modern defaults`.

### Task 6: Script formatting scanner

**Files:**
- Create: `J/uql/viewsheet/internal/ScriptFormattingScanner.java`
- Test: `T/uql/viewsheet/internal/ScriptFormattingScannerTest.java`

**Interfaces:**
- Produces: `public static Report ScriptFormattingScanner.scan(Viewsheet vs)`; `public record Report(SortedSet<String> components, boolean dashboardScript) { public boolean isEmpty() }`.

Rules (spec §7): tokenise with `inetsoft.uql.asset.internal.ScriptIterator` (listener pattern as `VSUtil:538`). Scan each own (non-embedded) assembly's script when `isScriptEnabled()`, plus `ViewsheetInfo.getOnInit()` / `getOnLoad()`. A formatting write is `<target>.<prop> =` (not `==`) or `<target>.<setter>(` where `<prop>` is in `LOOK_PROPERTIES = {foreground, background, font, borders, borderColors, alpha, roundCorner, titleFont, titleColor}` or ends with `Color`, `Colors` or `Font`, and `<setter>` is `setForeground`, `setBackground`, `setFont`, `setColor` or `setColors`. Target: an assembly name in `vs` → that name; no target in a component script → that component; anything else in a dashboard script → `dashboardScript = true`. Also add every own assembly with an expression-bound allowlisted USER value (any format path).

- [ ] **Step 1: Write the failing tests**

```java
@Test void anotherComponentsScriptCanTargetIt()        // Text2 script "Text1.foreground = [255,0,0];" → {Text1}
@Test void anImplicitTargetIsTheComponentItself()      // Text1 script "foreground = 0xff0000;" → {Text1}
@Test void readsAndComparisonsDoNotCount()             // "var c = Text1.foreground; if(Text1.font == null) {}" → empty
@Test void chartAxisColorsCount()                      // "Chart1.axis.y.labelColor = 0xff0000;" → {Chart1}
@Test void aDisabledScriptDoesNotCount()               // setScriptEnabled(false) → empty
@Test void aDashboardScriptIsAttributedByName()        // onInit "Table1.background = 0xeeeeee;" → {Table1}, dashboardScript false
@Test void anUnresolvedDashboardTargetSetsTheFlag()    // onLoad "var t = x; t.foreground = 1;" → dashboardScript true
@Test void anExpressionBoundFormatCounts()             // Text1 USER fg "=if(true,'red','blue')" → {Text1}
```

- [ ] **Step 2: Run** `./mvnw -q test -pl core -Dtest=ScriptFormattingScannerTest` — expect FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** the same command — expect PASS.
- [ ] **Step 5: Stage**; controller commits `Feature #80398, find scripts that set formatting`.

### Task 7: Provenance and viewsheet info flags

**Files:**
- Modify: `J/uql/viewsheet/ViewsheetInfo.java` (field, accessors, `writeAttributes` ~:777, `parseAttributes` ~:853 — the `vizDensity` pattern), `J/web/viewsheet/service/CoreLifecycleService.java` (~:305-318), `core/src/main/resources/inetsoft/util/srinter.properties` (all keys in Global Constraints)
- Create: `J/web/composer/vs/controller/ModernCopyProvenance.java`
- Test: `T/uql/viewsheet/ViewsheetInfoModernCopyTest.java`, `T/web/composer/vs/controller/ModernCopyProvenanceTest.java`

**Interfaces:**
- Produces:
  - `String ViewsheetInfo.getModernCopyOf()` / `setModernCopyOf(String identifier)` — the original's `AssetEntry.toIdentifier()`; XML attribute `modernCopyOf`, written only when non-null, `Tool.escape`d.
  - `static String ModernCopyProvenance.displayPath(String identifier, AssetRepository repository, Principal principal)` — the original's path when `identifier` parses and `repository.containsEntry(entry)`, else null; never throws.
  - Info map keys: `modernizeAvailable` = `VSDensityDefaults.isModern()`; `modernCopyOf` = `displayPath(...)` or absent.

- [ ] **Step 1: Write the failing tests**

```java
@Test void modernCopyOfRoundTripsThroughXml()   // set "1^128^__NULL__^A/B" → writeXML → parseXML → equal; unset → attribute absent
@Test void displayPathResolvesAnExistingOriginal()  // mock containsEntry true → "A/B"
@Test void displayPathIsNullWhenTheOriginalIsGone() // containsEntry false → null; malformed identifier → null
```

- [ ] **Step 2: Run** `./mvnw -q test -pl core -Dtest='ViewsheetInfoModernCopyTest,ModernCopyProvenanceTest'` — expect FAIL.
- [ ] **Step 3: Implement**, add the info-map entries and every string from Global Constraints.
- [ ] **Step 4: Run** the same command — expect PASS.
- [ ] **Step 5: Stage**; controller commits `Feature #80398, record and expose a modern copy's original`.

### Task 8: Dialog model and name check

**Files:**
- Create: `J/web/composer/model/vs/ModernizeDialogModel.java`, `J/web/composer/model/vs/ModernCopyRequest.java`, `J/web/composer/model/vs/ModernCopyNameCheck.java`, `J/web/composer/vs/dialog/ModernCopyNames.java`, `J/web/composer/vs/dialog/ModernizeDialogService.java`, `J/web/composer/vs/dialog/ModernizeDialogController.java`
- Test: `T/web/composer/vs/dialog/ModernCopyNamesTest.java`, `T/web/composer/vs/dialog/ModernizeDialogServiceTest.java`

**Interfaces:**
- Consumes: `ScriptFormattingScanner` (Task 6), `SaveViewsheetDialogService.getSaveViewsheetInfo` / `validateSaveViewSheet`.
- Produces:
  - POJO `ModernizeDialogModel { boolean keepAvailable; boolean neverSaved; String suggestedName; String parentId; boolean embedsDashboards; String scriptWarning; }` (getters/setters, like `SaveViewsheetDialogModel`).
  - POJO `ModernCopyRequest { String name; }`; POJO `ModernCopyNameCheck { boolean exists; String permissionDenied; }`.
  - `static String ModernCopyNames.suggest(String baseName, Predicate<String> taken, Catalog catalog)` — `copyName` if free, else `copyNameN` with 2, 3, … up to 999, else null.
  - `static String ModernizeDialogService.scriptWarning(ScriptFormattingScanner.Report report, Catalog catalog)` — null when empty; else `scripts`, `scripts.withDashboard` or `scripts.dashboardOnly`, with `{0}` = `componentCount.one` or `componentCount`.
  - `ModernizeDialogModel ModernizeDialogService.getModel(String runtimeId, Principal principal)` — `keepAvailable = VizModernizeUtil.hasUnmarked(vs)`; `neverSaved` = entry scope `AssetRepository.TEMPORARY_SCOPE`; `parentId` = the original's parent identifier; `suggestedName` from `suggest(entry.getName(), taken, catalog)`, where a name is taken when `validateSaveViewSheet` for it (original's parent id) reports `alreadyExists`; `embedsDashboards` = any `vs.getAssemblies(false)` entry is a `Viewsheet`.
  - `ModernCopyNameCheck ModernizeDialogService.checkName(String runtimeId, ModernCopyRequest request, Principal principal)` — builds a `SaveViewsheetDialogModel` (trimmed name, the original's parent id) and maps the validator.
  - REST (controller, `@ClusterProxy` service methods as `SaveViewsheetDialogService`): `GET /api/composer/vs/modernize-dialog-model/**` (runtime id as remaining path) → model; `POST /api/composer/vs/modernize-dialog-model/check-name/{runtimeId}` with `ModernCopyRequest` → `ModernCopyNameCheck`.

- [ ] **Step 1: Write the failing tests**

```java
@Test void suggestPrefersTheModernSuffix()              // nothing taken → "Sales (modern)"
@Test void suggestSkipsTakenNames()                     // "(modern)" and "(modern 2)" taken → "Sales (modern 3)"
@Test void scriptWarningIsNullWhenNothingCounts()
@Test void scriptWarningUsesSingularAndDashboardForms() // 1 component → "...on 1 component..."; 2 + flag → withDashboard text; 0 + flag → dashboardOnly text
@Test void modelReflectsTheSheet()                      // legacy sheet → keepAvailable; fully marked → !keepAvailable; temp scope → neverSaved; embedded Viewsheet child → embedsDashboards
@Test void checkNameMapsTheSaveValidator()              // alreadyExists set → exists true; permissionDenied message passed through; name trimmed before validation
```

- [ ] **Step 2: Run** `./mvnw -q test -pl core -Dtest='ModernCopyNamesTest,ModernizeDialogServiceTest'` — expect FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** the same command — expect PASS.
- [ ] **Step 5: Stage**; controller commits `Feature #80398, Modernize dialog model and name check`.

### Task 9: Create the modern copy

**Files:**
- Create: `J/web/composer/vs/controller/ModernCopyController.java`, `J/web/composer/vs/controller/ModernCopyService.java`
- Test: `T/web/composer/vs/controller/ModernCopyServiceTest.java` (Mockito, pattern of `ModernizeViewsheetServiceTest`; a real legacy `Viewsheet` behind the mocked `RuntimeViewsheet`)

**Interfaces:**
- Consumes: `VizModernizeUtil.resetToModern` (Task 5), `ViewsheetInfo.setModernCopyOf` (Task 7), `ModernizeDialogService.checkName` + `ScriptFormattingScanner` (Tasks 6, 8), `SaveViewsheetDialogService.getSaveViewsheetInfo` / `saveViewsheet`, `ComposerViewsheetService.saveViewsheet(runtimeId, SaveSheetEvent, principal, dispatcher, linkUri)`, `AssetRepository.removeSheet(entry, principal, true)`, `AutoSaveUtils.deleteAutoSaveFile(entry, principal)`, `MessageCommand`.
- Produces: STOMP `composer/viewsheet/modernize-copy` with payload `ModernCopyRequest`, annotated like `ModernizeViewsheetController` (`@Undoable @LoadingMask @HandleAssetExceptions`). `public Void ModernCopyService.createModernCopy(@ClusterProxyKey String runtimeId, String name, Principal principal, CommandDispatcher dispatcher, String linkUri) throws Exception`.

Sequence (spec §6): refuse with `MessageException` when `VSDensityDefaults.isModern()` is false or the entry is temporary-scope; `checkName` (refuse on exists/permission); remember `original = rvs.getEntry()`, `before = rvs.getViewsheet().clone()`, `savePoint = rvs.getSavePoint()`; `saveDialogModel = getSaveViewsheetInfo(...)` with the trimmed name, the original's parent id, `updateDepend=false`; `saveViewsheet(...)` (failure here → rethrow, nothing to clean up); then `resetToModern`, `setModernCopyOf(original.toIdentifier())`, `ComposerViewsheetService.saveViewsheet`, `coreLifecycleService.setViewsheetInfo` + `refreshViewsheet` as `ModernizeViewsheetService:72-75`, `deleteAutoSaveFile(original, principal)`, and a `MessageCommand` — `INFO` with `copyCreated.scripts` (names joined by ", ") when the scan is non-empty, else `OK` with `copyCreated`. If anything after the first save throws: `removeSheet(copyEntry, principal, true)`, `rvs.setEntry(original)`, `rvs.setViewsheet(before)`, `rvs.setSavePoint(savePoint)`, re-send `setViewsheetInfo` and a `SaveSheetCommand` carrying the original's id and save point, then rethrow.

- [ ] **Step 1: Write the failing tests**

```java
@Test void createsTheCopyInOrder()               // InOrder: checkName, saveDialog.saveViewsheet(updateDepend=false, name, parentId), composer save, refresh; sheet marked; getModernCopyOf()==original id; OK message "Created X."
@Test void aScriptWarningTurnsTheMessageIntoInfo() // Text2 script sets Text1.foreground → INFO, message names Text1
@Test void aTakenNameIsRefusedBeforeAnySave()     // checkName exists → MessageException, saveViewsheet never called
@Test void aNameHeldByAnotherRuntimeChangesNothing() // saveViewsheet throws MessageException("overwriteForbidden") → removeSheet never called, rvs entry still original
@Test void aFailureAfterTheFirstSaveDeletesTheCopy() // composer save throws → removeSheet(copy, p, true), setEntry(original), setViewsheet(before), exception rethrown
@Test void bannedCharactersAreRefusedServerSide()   // name "a/b" → checkName/validator reports → refused
@Test void refusedWithTheGateOffOrANeverSavedSheet()
```

- [ ] **Step 2: Run** `./mvnw -q test -pl core -Dtest=ModernCopyServiceTest` — expect FAIL.
- [ ] **Step 3: Implement.** If `vsService.setViewsheet(...)` inside Save As copies bookmarks to the new entry, remove them from the copy here and add a test for it; report which way it went.
- [ ] **Step 4: Run** `./mvnw -q test -pl core -Dtest='ModernCopyServiceTest,ModernizeViewsheetServiceTest,RevertViewsheetServiceTest'` — expect PASS.
- [ ] **Step 5: Stage**; controller commits `Feature #80398, create a modern copy`.

### Task 10: Modernize dialog component

**Files:**
- Create: `P/composer/data/vs/modernize-dialog-model.ts`, `P/composer/dialog/vs/modernize-dialog.component.ts`, `.html`, `.scss`
- Test: `P/composer/dialog/vs/modernize-dialog.component.tl.spec.ts`

**Interfaces:**
- Consumes: the REST endpoints and model of Task 8.
- Produces:
  - `export interface ModernizeDialogModel { keepAvailable: boolean; neverSaved: boolean; suggestedName: string; parentId: string; embedsDashboards: boolean; scriptWarning: string | null; }`
  - `export type ModernizeDialogResult = { choice: "keep" } | { choice: "copy"; name: string };`
  - Standalone `ModernizeDialogComponent`, selector `modernize-dialog`: `@Input() model: ModernizeDialogModel`, `@Input() runtimeId: string`, `@Input() modified: boolean`, `@Output() onCommit: EventEmitter<ModernizeDialogResult>`, `@Output() onCancel: EventEmitter<void>`. Imports like `SaveViewsheetDialog` (`ModalHeaderComponent`, `ReactiveFormsModule`, `InputTrimDirective`, …).

Behaviour (spec §3.2): two radio options (`keep`, `reset`), `keep` selected unless `!keepAvailable`, then `reset` is selected and `keep` disabled with `keep.none`. `reset` disabled with `reset.neverSaved` when `neverSaved`. When `reset` is selected: a `saveAs` field prefilled with `suggestedName` using the Save As validators (`Validators.required`, `FormValidators.assetEntryBannedCharacters`, `FormValidators.assetNameStartWithCharDigit`); a debounced (300 ms) `POST ../api/composer/vs/modernize-dialog-model/check-name/<Tool.byteEncode(runtimeId)>`; `nameExists` or the returned `permissionDenied` shown inline; the `links` line; `unsaved` when `modified`; `embedded` when `embedsDashboards`; `scriptWarning` text when non-null. Primary button: `composer.vs.modernize.action` for keep, `createCopy` for reset, disabled while the name is invalid, unchecked or taken, and after the first click.

- [ ] **Step 1: Write the failing TL tests** (MSW handler for `check-name`)

```ts
it("selects keep by default and emits keep")
it("disables keep and preselects reset when nothing is unmarked")
it("disables reset with the save-first line for a never-saved sheet")
it("shows the save-as field, links line, unsaved, embedded and script lines for reset")
it("shows the name-exists error and keeps the button disabled")
it("shows the permission error from the server")
it("rejects a name with banned characters")
it("emits copy with the trimmed name once even when clicked twice")
```

- [ ] **Step 2: Run** `cd web && npx ng run portal:test-tl --include="**/modernize-dialog.component.tl.spec.ts"` — expect FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** the same command — expect PASS.
- [ ] **Step 5: Stage**; controller commits `Feature #80398, Modernize dialog`.

### Task 11: Wire the dialog into the viewsheet pane and Revert

**Files:**
- Modify: `P/composer/data/vs/viewsheet.ts` (:54-58), `P/composer/gui/vs/editor/viewsheet-pane.component.ts` (menu :419-434, info read :812-832, `modernize()`/`revert()` :1744-1766), `P/composer/gui/vs/editor/viewsheet-pane.component.html` (bar :37-41, a `#modernizeDialog` template beside `:228`)
- Test: `P/composer/gui/vs/editor/viewsheet-pane.component.interaction.tl.spec.ts`

**Interfaces:**
- Consumes: `ModernizeDialogComponent` (Task 10), info keys `modernizeAvailable` / `modernCopyOf` (Task 7).
- Produces: `Viewsheet.modernizeAvailable: boolean = false`, `Viewsheet.modernCopyOf: string = null`; `ViewsheetPaneComponent.openModernizeDialog(): void`.

Behaviour: the menu entry label is `"_#(js:composer.vs.modernize.menu)..."`, visible when `vs.modernizeAvailable && !deployed`, action `openModernizeDialog()`. The bar's `(onModernize)` calls `openModernizeDialog()`. `openModernizeDialog` fetches the model with `modelService.getModel("../api/composer/vs/modernize-dialog-model/" + Tool.byteEncode(vs.runtimeId))`, opens the template with `this.modalService.open(…, {backdrop: "static"})`, passes `modified = vs.isModified()`; on `keep` it runs today's `modernize()`; on `copy` it sends `"/events/composer/viewsheet/modernize-copy"` with `{name}`. `revert()` confirms with `Tool.formatCatalogString("_#(js:composer.vs.revert.confirmCopy)", [vs.modernCopyOf])` when `vs.modernCopyOf` is set, else today's text.

- [ ] **Step 1: Write the failing TL tests**

```ts
it("shows Modernize... whenever modernization is available, even with nothing unmarked")
it("hides Modernize... when deployed or unavailable")
it("opens the dialog from the bar")                       // spy openModernizeDialog
it("sends the modernize event for keep")                  // resolve modal with {choice:"keep"} → sendEvent("/events/composer/viewsheet/modernize")
it("sends the copy event with the name")                  // {choice:"copy",name:"S (modern)"} → sendEvent("/events/composer/viewsheet/modernize-copy", {name:"S (modern)"})
it("uses the copy wording in Revert's confirm on a copy") // modernCopyOf "A/B" → confirm text contains "A/B"
```

- [ ] **Step 2: Run** `cd web && npx ng run portal:test-tl --include="**/viewsheet-pane.component.interaction.tl.spec.ts"` — expect FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** the same command, then `cd web && npx ng test portal --include="**/modernize-bar.component.spec.ts"` — expect PASS.
- [ ] **Step 5: Stage**; controller commits `Feature #80398, open the Modernize dialog from the pane`.

### Task 12: Options pane hint

**Files:**
- Modify: `P/composer/dialog/vs/viewsheet-options-pane.component.ts` (inputs :55-59), `.html` (viz block :98-125), `P/composer/dialog/vs/viewsheet-property-dialog.component.html` (:24-28), `.ts` (`loadedVizModern` :80)
- Test: `P/composer/dialog/vs/viewsheet-options-pane.component.tl.spec.ts`, `P/composer/dialog/vs/viewsheet-property-dialog.component.tl.spec.ts`

**Interfaces:**
- Produces: `@Input() modernizeHint: boolean = false` on `ViewsheetOptionsPane`; the property dialog binds `[modernizeHint]="!!viewsheet?.modernizable && !loadedVizModern"`. `modernizable` already means gate on and unmarked content (`CoreLifecycleService:314`), which is the spec's condition.

Behaviour: under the Modern Visualization switch, render `_#(composer.vs.modernize.optionsHint)` as a `form-text` when `modernizeHint && model.vizModern`.

- [ ] **Step 1: Write the failing TL tests**

```ts
it("shows the hint when the switch is turned on and a hint is allowed")   // pane
it("hides the hint when the switch is off or no hint is allowed")         // pane
it("allows the hint only for a classic dashboard that is modernizable")   // dialog: modernizable true & loaded classic → hint input true; modernizable false → false
```

- [ ] **Step 2: Run** `cd web && npx ng run portal:test-tl --include="**/viewsheet-options-pane.component.tl.spec.ts"` and the property-dialog spec — expect FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** both — expect PASS.
- [ ] **Step 5: Stage**; controller commits `Feature #80398, Options pane hint toward Modernize`.

### Task 13: Manual verification (spec §12)

No code. Build `core` and the portal, start the dev server, then check and record results:

- [ ] Both entry points open the dialog; deployed, gate-off and fully marked behaviour.
- [ ] Options pane: turning Modern Visualization on shows the hint with the gate on and not with it off; OK modernizes and creates no copy.
- [ ] `Examples/Census`: Keep shows modern gridlines and chart text where residue was; Reset produces a fully modern copy; the original asset is unchanged on reopen.
- [ ] Revert on the copy shows the original's name; Revert after Keep looks identical to the original.
- [ ] Undo right after creating the copy returns the copy to its pre-reset look; the original is untouched.
- [ ] Export of the copy (PDF, PNG, Excel) matches the composer.
