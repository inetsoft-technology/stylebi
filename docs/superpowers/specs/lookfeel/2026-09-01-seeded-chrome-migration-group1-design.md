# Seeded Chrome Migration, Group 1 — Design

**Date:** 2026-09-01
**Verified against:** community `viz-updates` @ `aab93d919`, which is `HEAD`. Every file and line citation
below was read at that commit.
**Roadmap entry:** "READ-TIME MIGRATION. Move the substituting resolvers onto `seedChromeDefaults`" in
[chart-card-roadmap.md](./chart-card-roadmap.md), ranked #3 there.
**Precedent:** [2026-08-28-title-lane-unfilled-design.md](./2026-08-28-title-lane-unfilled-design.md) §1 is
the general argument for seeding over read-time substitution, and
[2026-08-31-selection-family-title-lane-design.md](./2026-08-31-selection-family-title-lane-design.md) is
the worked example. Read §1 of the first before questioning the mechanism.

---

## Scope

**In: four conversions.** The chart's card inset, the selection cell's dark foreground, the slider's dark
foreground, and the text assembly's value emphasis. Eleven read-time call sites; two resolver method pairs
deleted outright.

**Out: everything calendar, and the input titles.** `VSCalendarChromeDefaults`, the calendar's title lane,
and the checkbox and radio-button title lanes are group 2. The calendar is the only type that has never run
`seedChromeDefaults` at all — `initDefaultFormat` (`CalendarVSAssemblyInfo:88-91`) clones a static
`FormatInfo` and never calls `setDefaultFormat` — and it carries a second problem nothing has designed
against: `copyViewInfo:1062-1068` replaces the whole `FormatInfo` with a **shared static prototype,
uncloned**, gated on `getFormatInfo().equals(normalDefault)`. Seeding the calendar breaks that equality test
and aliases a JVM-wide static where it still fires. Both calendar conversions need that answer, so both wait
for it.

**Out: the `MaxModeSelectionVSAssemblyInfo` hoist** recommended in the selection family design §9. It
collapses duplicated *title* seeding, so it belongs with group 2's title work.

**Out and staying read-time, by design:** `VSDensityDefaults.rowHeight`, `titleHeight` and `mode()`. `mode()`
reads the live org property `viewsheet.density` (`:66-69`); the mark decides whether an assembly honours
density, not which density is in force, so seeding a density-derived value would freeze it at creation.

---

## 1. What a conversion is

Each of the four stops substituting a value onto a clone at every read point and writes it once into the
stored `DEFAULT` tier at creation. Four properties make this smaller than the read-time family looks.

**The customization guards disappear.** Every resolver tests `isForegroundCustomized` /
`isBorderCustomized` — USER tier or CSS tier defined — and skips. A seed writes the DEFAULT tier only, so a
USER or CSS value outranks it by construction. The guards are not reimplemented anywhere.

**Both branches write, always.** `VizModernizeUtil.revert` (`:102-111`) clears the mark and calls
`seedChromeDefaults`; the only other work it does is sheet-level, clearing two colour caches. No other
per-assembly write happens, so the legacy value must be written explicitly on the `!ctx.modern` branch or
Revert strands the modern one. This is the established contract from the selection title seed.

**No skip-list.** The title conversion needed `VSTitleChromeDefaults.isSeededTitle` because it crossed ten
types incrementally. Each resolver here owns one type — padding→chart, output→text (all three sites test
`TextVSAssemblyInfo`) — except the dark foreground, which owns two (slider, selection base). With the
calendar out, every resolver in this group converts its whole population in one step, so the method is
deleted rather than skipped.

**`bypassesBaseChrome()` is not touched, and the text assembly proves it.** The roadmap costs this migration
as "a change to the hook's contract", on the premise that the bypass predicate would have to split into an
object-chrome half and an always-run title half. It does not: the guard sits inside the *base*
implementation (`VSAssemblyInfo:1249`), not at the top of a sealed method, so a subclass override runs
regardless. `TextVSAssemblyInfo` gets a `seedChromeDefaults` override whose `super` call returns immediately
at the bypass — correctly skipping the object border and radius that its own `setDefaultFormat` controls —
and then writes the value emphasis. `ChartVSAssemblyInfo` (`:105`), `SelectionBaseVSAssemblyInfo` (`:932`)
and `TimeSliderVSAssemblyInfo` (`:721`) already run exactly this pattern. Removing text from the bypass list
instead would be wrong: the base would write chrome that creation deliberately owns.

Where each seed lives:

| Conversion | Owner | Plumbing |
|---|---|---|
| Chart card inset | `ChartVSAssemblyInfo.seedChromeDefaults` | override and creation re-invoke exist (`:101`, `:105`) |
| Selection cell foreground | `SelectionBaseVSAssemblyInfo.seedChromeDefaults` | override and creation re-invoke exist (`:928`, `:932`) |
| Slider foreground | new `SliderVSAssemblyInfo.seedChromeDefaults` | inherits the base `setDefaultFormat`; not on the bypass list, so the base hook already runs |
| Text value emphasis | new `TextVSAssemblyInfo.seedChromeDefaults` | on the bypass list; needs the override plus `seedChromeDefaults(VizContext.of(this))` at the end of its own `setDefaultFormat` |

---

## 2. Conversion A — the chart's card inset

**Owner** `ChartVSAssemblyInfo.seedChromeDefaults`. The odd one out: the target is the `padding` `Insets`
field (`VSAssemblyInfo:1693`), not a format tier, so there is no DEFAULT tier to write and no tier
precedence to lean on.

**Values.** Modern writes `Insets(12,12,12,12)`; legacy writes `Insets(10,10,10,10)`, which
`VSObjectChromeDefaults.legacyChartPadding()` already supplies. A fresh `Insets` per write — it is mutable
and the constant must not escape by reference, which is why that supplier exists.

**Two guards, one of them new.** Skip both branches when `isUserPadding()` (`ChartVSAssemblyInfo:2826`), the
author's opinion, which is already the rule. And skip both when
`CSSDictionary.isPaddingDefined(objCssFmt.getCSSParam())`: `setCSSDefaults()` runs *before* the hook
(`VSAssemblyInfo:1531`, then `:1235`), so a CSS padding is already installed and would be clobbered. That
predicate is public, so this is a real test rather than the `CompositeValue.cssDefined` problem that made
`2b86a9fa3`'s flag-alone approach a regression. The exact-equality test being deleted was providing this
protection accidentally.

**It changes a dialog write, not only read paths.** `ChartPropertyDialogService:405-408` implements "padding
follows default" by storing the legacy 10s with `setUserPadding(false)` and relying on the resolver to
return 12 at read time. Seeded, that stores 10s permanently. It becomes `setUserPadding(false)` followed by
`assemblyInfo.seedChromeDefaults(VizContext.of(assemblyInfo))`, which writes 12 for a marked chart and 10
for an unmarked one — the same "clear the opinion, re-run the seed" shape Revert uses. This is the only
write path any conversion in this group reaches.

**Deletes** `VSObjectChromeDefaults.chartPadding` (`:73-83`) with its `LEGACY_CHART_PADDING.equals(stored)`
test, and `ChartVSAssemblyInfo.getPadding()` (`:2874-2876`), which reverts to the inherited getter. The
17 read sites that override existed to serve — four exporters, the report converter, annotation placement,
the browser model — follow the field untouched, exactly as they followed the override.

---

## 3. Conversion B — the selection cell's foreground

**Owner** `SelectionBaseVSAssemblyInfo.seedChromeDefaults`. **Target** the `TableDataPath(-1, DETAIL)`
composite's DEFAULT foreground. **Values:** `0xE6E0E9` when `ctx.dark`, else `0x2b2b2b` — the literal
`setDefaultFormat:883` already writes.

That literal stays where it is. It is an unconditional creation default, which the hook's own contract keeps
in `setDefaultFormat`, and the seed overwrites it afterwards. This is how the selection title seed already
relates to the borders `setDefaultFormat` writes at `:894-897`.

**Two simplifications fall out, and neither is a shortcut.** The measure-bar composites live on their own
paths (`getMeasureBarPath(i)`, `getMeasureNBarPath(i)`), so seeding the plain DETAIL composite excludes them
*structurally*; `FormatPainterService.isPlainSelectionCell` (`:942-945`) and the Measure Text / Measure Bar /
Measure Bar(-) exclusion it exists for are deleted rather than reimplemented. And
`VSSelectionListHelper.getValueFormat`'s dimming writes the USER tier, so the constraint that the
substitution must run *before* the dimming becomes moot: an excluded or unselected value keeps its grey by
tier precedence.

**Five sites go:** `SelectionListModel:84` (which covers the tree — `CompositeSelectionValueModel` builds a
nested `SelectionListModel` per level), `VSSelectionListHelper:318`, `HTMLSelectionListHelper:183`,
`HTMLSelectionTreeHelper:129`, `FormatPainterService:229`.

### Excel's opt-out has to invert, and this is the one place seeding takes a capability away

`ExcelSelectionListHelper:260-261` and `ExcelSelectionTreeHelper:230-232` keep the legacy near-black
**deliberately**, and they do it by passing `VizContext.of((VizMark) null)` — a legacy context — into
`getValueFormat`. The in-code comment gives the reason: a spreadsheet has no page to paint and a selection
list seeds no dark card background, so its cells are unfilled white and the light neutral would be
invisible on them. `PPTSelectionListHelper:147` passes the real context, because a slide does take the
viewsheet background.

That mechanism only works while the value is substituted at read time behind a context parameter. **A
seeded value is in the stored format, so Excel reads the light ink whatever context it passes** — and
`ExcelSelectionTreeHelper:227` reads `sv.getFormat()` *uncloned*, taking the stored value directly. Doing
nothing here ships white-on-white cells in an Excel export of a dark selection list.

So the opt-out inverts. Today a renderer opts **in** to the modern ink by passing its assembly context;
after this conversion Excel opts **out**, substituting the legacy near-black onto a clone of its own. The
exception becomes explicit at the one renderer that needs it rather than implicit in an argument, and the
clone is required for the reason `applyDarkForegroundInPlace` already documents: never mutate a format
reached from a `FormatInfo`.

The legacy literal is then in three places — the `SelectionBaseVSAssemblyInfo` seed and the two Excel
helpers — so it becomes a supplier, `VSObjectChromeDefaults.legacyCellForegroundValue()`, rather than a
repeated `"0x2b2b2b"`.

**And `getValueFormat` loses its `ctx` parameter.** After the substitution goes, `ctx` is used for nothing
else in that method. Removing it is a compile error at all six call sites — `PDFSelectionListHelper:164`,
`SVGSelectionListHelper:158`, `VSSelectionTreeHelper:187`, and the three in `utils/inetsoft-xml-formats`
(`ExcelSelectionListHelper`, `ExcelSelectionTreeHelper`, `PPTSelectionListHelper`). That is the third
cross-module signature change on this branch, after `getValueFormat`'s own widening and
`ExportUtil.getBackGroundColor`. **Verify it with a `clean` build**: a 77-module incremental `install`
reported SUCCESS through the last one and only `clean` exposed the stale callers.

---

## 4. Conversion C — the slider's foreground

**Owner** a new `SliderVSAssemblyInfo.seedChromeDefaults` override. **Target** the OBJECT path's DEFAULT
foreground.

**Legacy here is genuinely absent.** `SliderVSAssemblyInfo` inherits the base `setDefaultFormat`, which
never writes a foreground at all. So the legacy branch writes `setForegroundValue(null)` **and**
`setForeground(null)` — both, for the reason the chart's title seed nulls both: `getForeground()` falls back
to the `fg` field when the value yields nothing, so a runtime foreground would survive the clear. Modern
dark writes `0xE6E0E9`; modern light writes null, the same as legacy, so a light modern slider stays
byte-identical.

**The slider has a seventh site the roadmap's tally missed.** `VSSliderModel:44-57` lifts the dark
foreground through `VSObjectChromeDefaults.textForegroundCss` rather than `applyDarkForeground`, so it is
absent from the "6 sites" figure. It sets the *model's* object foreground after construction, guarded by the
same USER/CSS test. Once the stored DEFAULT tier carries the value, the model's normal format path delivers
it and the whole block deletes.

**Two sites go:** `VSSlider:69` and `VSSliderModel:44-57`. With conversion B this empties
`applyDarkForeground`, `applyDarkForegroundInPlace` and `textForegroundCss`.

---

## 5. Conversion D — the text assembly's value emphasis

**Owner** a new `TextVSAssemblyInfo.seedChromeDefaults` override, plus
`seedChromeDefaults(VizContext.of(this))` at the end of its own `setDefaultFormat` — after `setFormat` and
`setCSSDefaults`, which is where the base calls it too.

**Values.** Modern foreground `0x35342F`, dark `0xE6E0E9`; modern border colours `0xD9D5CC`, dark
`0x49454F`. Legacy foreground `0x2b2b2b`, the literal at `TextVSAssemblyInfo:78`; legacy border colours
`DEFAULT_BORDER_COLOR`.

**The border nuance, and why it is not paranoia.** `setDefaultFormat(boolean border)` writes border colours
only when `border` is true. `initDefaultFormat` passes true, but other callers pass false, and writing a
colour where creation wrote none changes the *serialized asset* for an unmarked text assembly — the
bit-for-bit property this track has held throughout. So the seed writes border colours **only when the
DEFAULT tier already carries one**, mirroring the base hook's own existing test at `:1263`
(`getBordersValue() != null` before colouring a title border). Border colours without widths draw nothing,
so the difference is invisible on screen; the point is not writing into an asset that lacked it.

**Three sites go:** `AbstractVSExporter:1914`, `FormatPainterService:223`, `VSTextModel:211`.
`VSTextModel.createFormatModel` survives minus the call — it also passes `scaleFont = true`, which is not
this work's. The highlight foreground and background just below the exporter site
(`AbstractVSExporter:1919-1926`) write the USER tier and still win.

---

## 6. The deletion set, and what survives

**Deleted.** `VSObjectChromeDefaults.chartPadding`, `applyDarkForeground`, `applyDarkForegroundInPlace`,
`textForegroundCss`; `VSOutputChromeDefaults.applyModernDefaults`, `applyModernDefaultsInPlace`;
`ChartVSAssemblyInfo.getPadding()`; `FormatPainterService.isPlainSelectionCell`.

**Surviving as palette suppliers**, which is what `VSObjectChromeDefaults` already is:
`objectBorderColor`, `cardBackgroundCss`, `cardCornerRadius`, `legacyChartPadding`; and
`sliderInactiveTrack`, `sliderActiveTrack`, `sliderHandle`, `sliderTick`, `valueForeground`,
`valueBorderColor`. Those slider colours resolve what a painter draws with rather than a format value, so
nothing about them can travel in an asset and none is in scope.

**Two API additions, and one pair of methods that gains its first production caller.**
`darkForegroundValue()` is promoted from private to public so the slider and selection seeds have a supplier
of the right shape, and `legacyCellForegroundValue()` is added for the selection seed's legacy branch and
Excel's opt-out. And `valueForeground` / `valueBorderColor` currently have **no production caller at
all** — only `applyTo` and their own tests — so the text seed becomes their real caller rather than leaving
them dead behind the deletion.

---

## 7. Commit slicing

Four commits, each self-contained, ordered so a resolver dies with its last caller. Conversions A and D are
independent of the others; B must precede C.

1. **Seed the chart's card inset at creation** — conversion A, the dialog write, `chartPadding` deleted
2. **Seed a selection cell's dark foreground at creation** — conversion B
3. **Seed the slider's dark foreground at creation** — conversion C, `applyDarkForeground*` and
   `textForegroundCss` deleted
4. **Seed a text assembly's value emphasis at creation** — conversion D,
   `VSOutputChromeDefaults.applyModernDefaults*` deleted

The rejected alternative was seeding all four first and sweeping the call sites after. It leaves an
intermediate state where a value is both seeded and substituted, which is the one state that hides a
divergence between the two — and a divergence between what was substituted and what is now seeded is the
actual risk of this change.

---

## 8. Test surface

Five existing classes carry the shape: `SeedChromeDefaultsTest`, `VSObjectChromeDefaultsTest`,
`VSOutputChromeDefaultsTest`, `VizModernizeUtilTest`, `PaddingFollowDefaultTest`.

| Assertion | Why it is not redundant |
|---|---|
| Creation writes the modern value on a marked assembly, the legacy value on an unmarked one | The conversion itself, per value |
| **Revert restores the legacy value** | The branch with no other coverage, and the whole reason both branches write |
| Modernize on an existing unmarked assembly writes the modern value | The other `seedChromeDefaults` entry point |
| A USER-tier value survives creation, Modernize and Revert | Held by an explicit guard today and by tier precedence after; the reason it holds changes, so it needs asserting where the guard used to be |
| A CSS-defined padding survives the seed | Conversion A's new guard, and the regression `2b86a9fa3` hit in its flag-only form |
| A text assembly created with `border == false` gains no border colour | The bit-for-bit property for unmarked assets |
| The measure-bar composites keep their own foreground | What `isPlainSelectionCell` used to provide, now structural — asserted directly so a later tidy-up cannot quietly re-widen the seed |
| `reseedAfterRestore` resolves each of the four on bookmark restore | The seeded value has to survive a restore by construction; that is the claim §1's precedent rests on |
| The padding pane's follow-default round-trip stores a resolved inset | `PaddingFollowDefaultTest` covers the old behaviour and must move with the write path |

---

## Manual checks

1. **Marked chart** — 12px card inset in the viewer, unchanged from before the conversion.
2. **Unmarked chart** — 10px, and the asset byte-identical.
3. **Padding pane, follow-default round-trip** — set it, apply, reopen: still resolved, not stored 10s.
4. **Author padding** — survives Modernize; Revert leaves it alone.
5. **Dark marked selection list and tree** — light cell text in the viewer, PDF, PNG, PPT and both HTML
   paths.
6. **Dark marked selection list and tree exported to Excel** — cells keep the legacy near-black and stay
   readable on white. This is the check that fails if the inverted opt-out is missed.
7. **Dark marked selection list with an excluded and an unselected value** — both still grey.
8. **Composer format painter on a dark selection cell** — the picker shows the light ink, not the stored
   near-black. This is the design-time WYSIWYG break the title precedent exists to prevent.
9. **Dark marked slider** — light tick and value labels in the viewer and in export.
10. **Marked text assembly** — value emphasis foreground and border; a highlight still overrides both.
11. **Revert on a dashboard holding all four** — every value returns to legacy.
12. **Export a marked dashboard, import it, confirm the four values arrive.** The acceptance check for the
    item: this is the portability defect being closed, and it is the check that fails today.

---

## What this closes, and what it does not

**Closes:** the portability hole for four of the five substituting resolvers — a value written at creation
travels in the asset, so an export into a build without this work renders those four correctly rather than
falling back to legacy.

**Leaves open:** everything in the "Out" list above. `VSTitleChromeDefaults.applyModernDefaults` cannot be
deleted until group 2 converts its last three types, and `VSCalendarChromeDefaults` survives group 1
untouched. The roadmap's end state — every substituting resolver deleted — needs both groups.

---

## What the implementation found, and what it left open

Recorded 2026-09-01, after the branch shipped as `083265a7d..a2610c387` (nine commits) with the full `core`
suite at 5219 tests, 0 failures, and a clean 46-module cross-module build. These are the things execution
discovered that this design did not predict, kept here so the next person in this area does not rediscover
them.

**§3's claim that the browser model "covers the tree" was true of the deleted resolver and false of the
seed, and it cost a Critical finding at the final review.** A selection tree renders its non-leaf rows
through five `TableDataPath(i, GROUP_HEADER)` composites, which `SelectionTreeVSAssemblyInfo.setDefaultFormat`
(`:695-701`) clones from DETAIL; only the leaf reads DETAIL. Seeding DETAIL alone was correct at creation
purely by accident — the hook's virtual dispatch runs inside `super.setDefaultFormat`, *before* that clone —
but `Modernize`, `Revert` and `reseedAfterRestore` call the hook on an existing tree, where those composites
already exist and were left stale. Modernizing a dark tree kept near-black group rows on the dark card;
reverting a marked one kept light rows on the light page, in the browser and every export. **The lesson
generalises: when converting a read-time substitution, enumerate every format path the type RENDERS, not
every path the resolver was called from.** The two differ whenever a type clones one composite into others.

**No task-level review could have caught it**, which is the argument for the whole-branch pass: each task
review saw one task's diff, and the tree's own composites live in a class no task touched.

**A seed that copies rather than re-derives cannot drift.** The tree's override takes the value
`super.seedChromeDefaults` just wrote to DETAIL instead of re-deriving it from `ctx`. That is strictly
better than the symmetrical-looking alternative: there is one decision about what the value should be, in
one place, and the branch logic exists once.

**The dialog seam was the whole cost of the visibility widening, and it was avoidable.** "Padding follows
default" originally re-ran the entire hook to reset one `Insets`, which also re-seeded the card background,
the title lane and the palette — including `clearDerivedColors()`, dropping auto-assigned series colours,
without the sheet-level cache clears `modernize`/`revert` pair with that write. A narrow
`ChartVSAssemblyInfo.resetCardInset(VizContext)` replaced it, and `seedChromeDefaults` returned to
`protected` on all ten declarations. **Prefer a narrow public seam over widening the hook.**

**The `userPadding` + `format.css` interaction needed its own answer.** With a CSS padding defined, clearing
the author's opinion left the stale inset stored while the pane claimed the default, because the seed's CSS
guard makes it skip. `resetCardInset` re-reads the live dictionary instead.

**Excel's opt-out inverts wherever a cell value is seeded, and this is the general form.** Excel kept the
legacy ink by passing a legacy `VizContext` so the substitution declined. A seeded value is in the stored
format, so that mechanism is inert and its unfilled white cells would take light-on-white text. The opt-out
now substitutes the legacy value back onto a clone. **Any future conversion of a value Excel declines will
need the same inversion** — and in `ExcelSelectionTreeHelper` the clone is mandatory, not stylistic, because
that site reads `sv.getFormat()` uncloned.

**Testing that inversion at the write path is blocked in `utils/inetsoft-xml-formats`.** Three independent
`ShutdownException` routes — `SelectionList`'s `XSwappable` static init, `VSCompositeFormat.getBackground()`
reaching live beans, and `VizContext.of`'s own density lookup — mean the real helper path needs a Spring
bootstrap that module has no fixture for. The substitution was extracted into a shared package-visible
`ExcelSelectionListHelper.applyDarkOptOut(format, dark)` and tested there. **Accepted trade, recorded rather
than hidden:** a mis-wiring at either call site would not be caught by that test.

**A "zero callers" claim must include `src/test`.** A precondition check that grepped only `core/src/main`
and `utils` reported `applyDarkForegroundInPlace` unused; it had a live caller in
`SelectionListModelDarkForegroundTest`, and deleting the method would have broken test compilation.

**Cross-module signature changes need a `clean` build, for the third time on this branch.** Removing
`getValueFormat`'s `VizContext` parameter reached three callers in `utils/inetsoft-xml-formats`. Verified
here with a full clean 46-module reactor, and confirmed by artifact timestamp that the module was genuinely
rebuilt rather than skipped.

### Outstanding

**The twelve manual checks PASSED**, confirmed by a human partner on 2026-09-01. Two of them were the
reason this section exists, because no automated gate substitutes for either: **check 6**, a dark selection
list and tree exported to Excel, which fails *silently* as white-on-white if the inverted opt-out is wrong;
and **check 12**, the export/import round trip, which is the portability defect this whole item exists to
close and the one check that fails before this work. Both passing is what makes this item done rather than
merely built.

**Parked, and belongs with group 2 rather than here.** Excel renders the seeded title foreground
(`0xCAC4D0`) and the slider's labels (`0xE6E0E9`) illegibly on its unfilled white cells. Verified
pre-existing rather than introduced by this work — `VSSlider` already applied that value at render time, and
nothing in these nine commits touches `VSTitleChromeDefaults`. Fixing it means deciding whether Excel's
opt-out doctrine extends to titles and to painted pictures, which is a design question, not a defect here.

**Deferred minors.** Five stale doc comments and names made false by this change's own deletions
(`legacyChartPadding`'s Javadoc naming the deleted resolver; `VizModernizeUtil`'s class Javadoc saying the
hook is protected — now true again, so this one self-resolved; `BookmarkChromeResolutionTest`'s
`modernizeViaGate` comment and its `:340` "no-op" comment; four prose mentions of "substitution" in
`SelectionListModelDarkForegroundTest`). Plus: no border-tier-precedence test for the text assembly, and two
of the four tasks verified the plan's red-test step by source-reading rather than by running it.

**Group 2 is unchanged by this work and still needs its own design:** `VSTitleChromeDefaults.applyModernDefaults`
for checkbox, radio button and calendar, `VSCalendarChromeDefaults`, and the
`MaxModeSelectionVSAssemblyInfo` hoist. The calendar's `copyViewInfo` shared-static-prototype problem
described in Scope above is still the open question there.
