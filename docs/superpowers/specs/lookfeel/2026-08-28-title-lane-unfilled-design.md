# Title Lane — Unfilled, With a Bottom Rule

**Date:** 2026-08-28 (second revision — **the mechanism reversed from read-time substitution to
creation-time seeding**, after three of the first revision's premises were disproved. The reasoning is
kept in "Why this is seeded, not substituted" because the same three arguments will be reached for again
by whoever migrates the rest of the read-time family, and two of them are wrong in general, not just here)
**Verified against:** community `viz-updates`, at the commit subject "fix(viewsheet): resolve an assembly's chrome when its state is restored" (hashes on this branch are rebased often — cite by subject and re-resolve)
**Decides:** how the modern title lane stops being a filled band and becomes a hairline rule, for the
chart and the three table types; where the value is written, and what that costs on restore and on export
**Implements:** [chart-card-open-item-decisions.md](./chart-card-open-item-decisions.md) §1, which decided
*that* the band is unfilled and left *how* uncosted
**Source drawing:** `chart-card-design3/Chart Card Spec v3.dc.html` §01, the card anatomy at `:47`

---

## Why this file exists

Decision §1 settled the appearance — "unfilled, a hairline rule only" — on 2026-08-11 and has sat in the
roadmap's *Decided, unscheduled* section since. It named `VSTitleChromeDefaults` as where the work lands
and stopped there. Three things it did not answer, each of which changes the shape of the change:

- **The rule does not exist yet.** §1 says `TITLE_BORDER` "stays: the hairline *is* the treatment, so the
  border is the part that carries it." But `titleBorderColor()` has no production caller — only its own
  test — and a modern chart's title format carries no borders value at all. This is not "swap the fill for
  the rule"; it is "clear the fill and *introduce* the rule."
- **It is not one treatment across the titled types.** Six of the eight ship different title borders today,
  and two of them already ship exactly the target treatment. Scope is a decision, not a detail.
- **Where the value is written matters more than what it is.** §1 assumed `VSTitleChromeDefaults`, which
  today substitutes at read time. That choice decides whether the look survives an asset export, and it
  turns out not to.

---

## Scope

**In: the chart and the three table types** — `ChartVSAssemblyInfo`, and `TableVSAssemblyInfo` /
`CrosstabVSAssemblyInfo` / `CalcTableVSAssemblyInfo` via their shared `TableDataVSAssemblyInfo`. These are
the four the chart card spec and the widget spec both draw.

**Deferred, and it converts to seeding too: the selection and input family** — selection list, selection
tree, selection container, range slider, checkbox, radio button, calendar. They keep the read-time path
until they convert; when they do, they take the same creation-time treatment, not a second mechanism. That
is what lets `VSTitleChromeDefaults.applyModernDefaults` be deleted rather than kept forever. Tracked as
the roadmap's read-time migration item.

**The cross-project consequence §1 flagged is still live and is triggered by this change.**
`VSTitleChromeDefaults` chose `TITLE_BG 0xF1EFEA` deliberately equal to the table header background "so
chrome reads as one system", and `Visualization Widget Spec.dc.html` §05 endorses it on exactly that basis.
Clearing the fill on the three table types breaks that equality where it is most visible — a table's title
bar sits directly above its header band. The sibling project should see this before it merges.

---

## 1. Why this is seeded, not substituted

The first revision of this file chose read-time substitution and gave three reasons. All three are wrong,
and they are recorded here because they are the reasons anyone will reach for again.

**"A seeded value needs a Revert reverser."** False. `VizModernizeUtil.revert:102-111` clears the mark and
re-runs `seedChromeDefaults`, and the class comment states the contract: *"No reverser is written: with the
mark cleared, seedChromeDefaults writes the legacy branch of every ternary, which is the identical call a
gate-off creation makes. A property added to what Modernize does is therefore reverted by the same edit to
the same method, or it is not added."* A seeded title value is reverted by the same edit to the same
method. The obligation is not a reverser; it is that **the legacy branch must reproduce what a gate-off
creation writes, exactly** — see §2.

**"It persists into the chart's `state_format`."** False. `ChartVSAssembly:485-491` writes
`getChartInfo().getFormat()` — the chart's *object* format. A chart's title format is not in its bookmark
at all. The table half was right: `TableVSAssembly:158-164` writes the whole `FormatInfo` through
`writeContents`, TITLEPATH included.

**"It collides with the in-flight bookmark work."** Obsolete as of the bookmark work's "resolve an assembly's chrome when its state is restored". `AbstractVSAssembly.parseState`
now captures the live mark, parses the state, restores the mark, and calls
`VizModernizeUtil.reseedAfterRestore`, which re-runs `seedChromeDefaults` against that mark. **Anything
routed through that hook is resolved on restore by construction.** That inverts the argument: the hook is
now the *safest* place for a persisted chrome value, not the riskiest.

### What decides it: the asset

A read-time value exists only while the server is resolving a render. It is not in the asset, so it does
not travel.

- **Export to a JAR and import to another server of the same build** — works either way. `vizMark` is
  written by `writeAttributes` (`VSAssemblyInfo:877-878`) and parsed at `:927`, so the mark travels and a
  read-time resolver reproduces the look on the far side. (Density does *not* travel: `VizContext.of(VizMark)`
  takes it from `VSDensityDefaults.mode():66-69`, the target org's live `viewsheet.density`. Pre-existing.)
- **Import into a build from before this work** — read-time loses, and loses *unevenly*. An old build
  ignores the unknown `vizMark` attribute, so everything seeded at creation — object border, card radius,
  card background, bar corner radius, smooth lines, colour frames — renders correctly because it is a plain
  stored value, while everything resolved at read time falls back to the legacy stored default. The card
  arrives **mixed**: modern frame, radius, background, bars and palette, classic title lane. For a chart it
  is worse than a clean fallback, because its title format carries no stored background *or* border — the
  lane arrives with no delineation at all.

Seeding puts the value in the asset, so the look is carried by the thing that is exported.

### What is left of the read-time argument

One thing, and it is transitional: the title fill is read-time *today*, so this converts an existing
mechanism rather than extending one. Migration cost is nil — the roadmap records the branch as unreleased
and unmerged, so every marked asset in existence is a test asset.

**Density must stay read-time and is not part of this.** `VSDensityDefaults.mode()` reads a live org
property and the dependency picture is explicit that "the mark decides whether an assembly honours density,
not which density is in force." Seeding a density-derived value would freeze it at creation. Dark is
different and *is* seedable: `VizContext.of(VizMark)` derives it from `mark == MODERN_DARK`, so it is
already per-assembly and already stamped at creation.

---


### What seeding costs that substitution did not — found in verification, 2026-08-31

The section above argues seeding beats substitution and does not name a price. There is one, and it
produced two of the three defects the manual pass found.

**A seeded value is written to the DEFAULT tier, and something older overwrites that tier.**
`FormatInfo.getFormat(tpath, false)` does not merely read a sub-path format — it **mutates it in
place** (`:233-248`). `VSCompositeFormat.getDefaultFormat()` hands back the live field, and
`copyDefaultFormat` then copies the OBJECT format over it. Its border-colour block (`:301-308`)
copies the object's *resolved* colour whenever the object's USER or CSS tier defines one, so an
author's frame colour lands on the title's DEFAULT tier — on top of the seeded rule colour — every
time the title is read. The read-time substitution was **accidentally immune**: it ran after that
mutation and wrote to a *clone*, so it always won. Seeding is not immune.

Fixed by scoping, not by reverting the approach: `copyDefaultFormat` gains a `keepBorderColors` flag
and `getFormat` sets it for a chart's TITLEPATH only (`ownsTitleBorder`). A table's title still
inherits the frame colour, which is deliberate and long-standing (`VSAssemblyInfo:1263-1267` says so)
and which `installsOwnTitleFormat()` already encodes as the chart-versus-table difference. The same
exposure exists for the selection base and the time slider, which carry hardcoded `0xc0c0c0` title
borders; left alone, because changing them alters types outside this work with no reported defect.

**And a seeded value has to carry everything the substitution carried.** `applyTo` wrote background
*and foreground*; the first implementation seeded background and borders only, so a seeded type
silently lost `TITLE_FG` / `TITLE_FG_DARK` and fell back to the composite default — black, which is
unreadable on a dark card and wrong in export too. The foreground is now seeded in both types.
`VSFormat.getForeground()` has the same field fallback as `getBackground()`, so the legacy branch
nulls both `setForegroundValue` and `setForeground`.

**The lesson for the read-time migration item**: before converting any other resolver, check what
`FormatInfo.copyDefaultFormat` does to the tier you intend to seed, and enumerate every property the
substitution wrote. Neither question arises while a value is resolved at read time.

### Excel: a chart title draws no rule, and that is accepted

Decided 2026-08-31. A chart's Excel title is a native `XSSFTextBox`, and
`PoiExcelVSExporter.applyFormat` (`:1049-1057`) reads only `borders.top` / `bcolors.topColor`, so a
bottom-only rule yields `EXCEL_NO_BORDER` and no outline at all. Reading `.bottom` would not fix it:
a shape outline is a single OOXML `ln` element for the whole perimeter, so a bottom-only rule cannot
be expressed that way and would need a separately drawn line shape. Pre-existing — before this work a
chart title carried no border on export, so the single-side read had nothing to get wrong.

**Tables are unaffected and were verified in a real export**: the title merge's bottom row carries
`<bottom style="thin"><color rgb="FFD9D5CC"/></bottom>` on every cell. Only the chart is short, and
only in Excel; PDF, PNG and print layout all draw it.
## 2. The mechanism

Creation-time seeding through `seedChromeDefaults`, the same hook the object border and card radius
already use, with the palette staying in `VSTitleChromeDefaults`.

### The values

| | modern | legacy (must equal a gate-off creation) |
|---|---|---|
| **Chart** background | none — *unchanged*; the chart's title format never had a stored background | none |
| **Chart** borders | `Insets(NONE, NONE, THIN_LINE, NONE)` | none |
| **Chart** border colours | `TITLE_BORDER` / `TITLE_BORDER_DARK` | none |
| **Table** background | none | `DEFAULT_TITLE_BG 0xf5f5f5` (`VSAssemblyInfo:1219`) |
| **Table** borders | `Insets(NONE, NONE, THIN_LINE, NONE)` | four-side `THIN_LINE` (`VSAssemblyInfo:1193-1197`, `border=true`) |
| **Table** border colours | already correct — see below | already correct |

**A chart's fill is not cleared, because it was never stored.** The `#F1EFEA` a modern chart shows today
comes entirely from `applyModernDefaults`. Once the chart stops calling it, the fill is gone with no write
at all. The whole of the chart's change is *adding* the rule.

**A table's border colour needs no change either.** `seedChromeDefaults:1263-1267` already writes
`objectBorderColor` (modern) or `DEFAULT_BORDER_COLOR` (legacy) onto the title's border colours, and
`TITLE_BORDER == objectBorderColor == #D9D5CC`. So for tables only the *widths* and the *fill* move.

**Nothing new enters the palette.** `titleBorderColor()` and the `TITLE_BORDER 0xD9D5CC` /
`TITLE_BORDER_DARK 0x49454F` constants exist and are unused; this gives them their first production caller.
The value is already the object frame and the table header→body separator
(`VSTableStructureDefaults.headerSeparator`), so the rule matches the two structural lines nearest it.

**The alpha in the drawing cannot survive and is not attempted.** v3 draws `rgba(217,213,204,0.72)`
(`:47`), but `VSCSSUtil.getBorder:130` emits `"#" + Tool.colorToHTMLString(color)` — opaque hex, no rgba —
and the Java painters take a `Color` from `BorderColors`. A baked wash would also be correct only over the
card it was composited against, needing a separately derived dark value rather than a mirrored one. The
flat structural neutral is used instead, deliberately. It also makes the print-layout path agree with
itself — see §4.

### Where each write lives

Per-type ternaries in each type's own `seedChromeDefaults` override, which is the pattern the bookmark work
established for the table's object background. `VSTitleChromeDefaults` supplies the modern values so the
palette stays in one class.

- `TableDataVSAssemblyInfo.seedChromeDefaults` — background and borders, both branches spelled out.
- `ChartVSAssemblyInfo.seedChromeDefaults` — borders only.

### The chart's ordering problem

`VSAssemblyInfo.setDefaultFormat` calls `seedChromeDefaults` as its last act (`:1235`), but
`ChartVSAssemblyInfo.setDefaultFormat` calls `super` at `:89` and **then replaces the title composite
wholesale at `:94-98`**. So at creation the hook writes a title the chart is about to throw away. This is
exactly why the chart sits in `installsOwnTitleFormat()` (`VSAssemblyInfo:1340`).

The fix is one line: re-invoke the hook after the install.

```java
getFormatInfo().setFormat(TITLEPATH, tFormat);
// the base seeded a title composite this method just replaced; re-run against the real one
seedChromeDefaults(VizContext.of(this));
```

`seedChromeDefaults` is a set of unconditional writes with no accumulation, so re-running is idempotent —
the object border, radius, card background and the two plot values are simply written twice with the same
context. `installsOwnTitleFormat()` is **not** modified: it guards the base's border-colour block, and the
chart's title seeding lives in the chart's own override, which that predicate does not gate.

The same ordering trap waits for `SelectionBaseVSAssemblyInfo` and `TimeSliderVSAssemblyInfo`, which also
install or overwrite their title composite after `super`. Whoever converts them needs this paragraph.

### The one trap

`VSFormat.getBackground():276` ends `return (rcolor instanceof Color) ? (Color) rcolor : bg;` — it falls
back to the `bg` field when `bgval` yields nothing. Setting a value hides that fallback; *clearing* one
exposes it. **The table's modern branch must null both `bgval` and `bg`**, or a background set at runtime
survives the clear. It does not arise at creation, where nothing has set `bg` yet; it arises on the three
paths that run the hook against a live assembly — Modernize, Revert, and `reseedAfterRestore`.

---

## 3. `VSTitleChromeDefaults` becomes a shrinking shim

A seeded type must stop calling `applyModernDefaults`, or it re-fills the lane it just cleared: the
substitution is keyed on the USER and CSS tiers, and a seeded modern table has neither set, so `TITLE_BG`
would land straight back on top.

Both entry points take the `VSAssemblyInfo` and early-return for a seeded type:

```java
if(!ctx.modern || titleFmt == null || isSeededTitle(info)) {
   return titleFmt;
}
```

```java
// these types carry their title chrome in the stored format; the rest are still substituted here
private static boolean isSeededTitle(VSAssemblyInfo info) {
   return info instanceof ChartVSAssemblyInfo || info instanceof TableDataVSAssemblyInfo;
}
```

The two-arg forms delegate with `null`, so a call site that has no info keeps today's behaviour with no
edit. **When the last titled type converts, `isSeededTitle` is true for all of them,
`applyModernDefaults` is dead, and all 18 call sites drop it.** That is the end state, not a maintenance
burden left behind.

### Call sites — 18 in all: 9 pass the info, 9 stay

Every converted site already holds the info at the call; none needs threading.

| Pass the info | Note |
|---|---|
| `VSChartModel:59` | chart, browser |
| `BaseTableModel:40` | the three table types, browser |
| `VSTableDataHelper:401` | tables, PDF / SVG / PNG / Excel title cell |
| `AbstractVSExporter:1411` | chart export title — also needs §4's block |
| `AbstractVSExporter:1803` | `prepareAssembly`, umbrella over every titled type |
| `AbstractVSExporter:1901` | `getTextFormat`, chart title path |
| `VsToReportConverter:1375` | print layout, umbrella |
| `VsToReportConverter:1516` | print layout, chart title |
| `FormatPainterService:220` | composer format picker (design-time WYSIWYG) |

Three of those — `AbstractVSExporter:1803`, `VsToReportConverter:1375`, `FormatPainterService:220` — are
umbrella calls reached by every titled type, which is why the predicate belongs in the defaults class
rather than at the call sites: an umbrella caller cannot express the scoping itself.

**Left on the two-arg form**, all deferred: `VSSelectionListHelper:76`, `VSSelectionTreeHelper:268`,
`VSCompositeModel:38` (selection base and selection container only — verified: `BaseTableModel` does not
extend `VSCompositeModel`), `VSCalendarModel:47`, `VSCheckBoxModel:37`, `VSRadioButtonModel:37`,
`VSRangeSliderModel:108`, `ExportUtil:111`, `AbstractVSExporter:2693`.

---

## 4. Where the format reaches, and the one place it does not

Because the border now lives in the stored format, every path that reads a title `VSCompositeFormat` gets
it. Each row is the read that consumes it, checked in code.

| Path | How the rule arrives | Change |
|---|---|---|
| Browser, all types | `VSFormatModel:95` → `VSCSSUtil.getBorder` → `vs-title.component.html:35` | none |
| PDF / SVG / PNG, tables | `VSTableDataHelper:401` → `writeTitleCell` → `ExportUtil.drawTextBox:152-153` reads `getBorders()` / `getBorderColors()` | none |
| Excel, tables | `PoiExcelVSUtil.setBottomBorderAndColor:1425-1450` reads `getBorders().bottom` and `getBorderColors().bottomColor` | none |
| Print layout, tables | `applyFormat(isTitle=true)` → `getTitleBorders:2169` takes `detailBorders.bottom` verbatim | none |
| Print layout, chart | `VsToReportConverter:1526-1529` calls `setBorders(tformat.getBorders())`, and `TextBoxElementDef:858-861` falls back to `getBorder()` only when borders is null — so the `setBorder(NO_BORDER)` at `:1522` is already overridden | none |
| PDF / SVG / PNG / Excel, chart | `getTextFormat:1899-1903` takes the CSS-type-`Chart` branch and reads TITLEPATH **directly**, and every exporter's `writeText` draws from `getTextFormat(info)` — PDF `:573`, SVG `:313`, HTML `:311`, PPT `:625`, Excel `PoiExcelVSExporter:969` | none |

**Corrected 2026-08-28 during implementation: there is no gap, and this section previously claimed
one.** The earlier text said the chart's export title needed a third property-copy block, because the
title chrome is copied onto a synthetic `TextVSAssembly`'s *object* format one property at a time —
background at `AbstractVSExporter:1418-1421`, foreground at `:1423-1427`, nothing else. That copy is
real, but **nothing draws from it**. Every exporter's `writeText` resolves the format it draws through
`getTextFormat(info)` — PDF `:573`, SVG `:313`, HTML `:311`, PPT `:625`, Excel
`PoiExcelVSExporter:969` — and `getTextFormat:1896-1903` tests the object format's CSS type for
`Chart` and, on that branch, returns the chart's **TITLEPATH format read directly**. The object-format
copies are bypassed on every path.

So the seeded border reaches chart export with no change at all, and a block written to carry it would
be dead code. It was implemented, traced, and reverted rather than shipped.

Two things this leaves for someone else. The **pre-existing background and foreground copies at
`:1418-1427` are dead by the same argument** — they were not touched here, because deleting code
another change deliberately added needs its own trace of every path, not a side effect of this one.
And the synthetic assembly is a `TextVSAssemblyInfo`, which is **not** a `TitledVSAssemblyInfo`, so
`prepareAssembly:1801-1804`'s title substitution skips it — worth knowing, because that guard is the
only thing stopping the read-time fill being re-applied to a chart's title on the export path.

**And one fragility worth knowing before anyone edits `getTextFormat`.** It calls
`applyModernDefaults(..., ctx, info)` where `info` is the *synthetic* `TextVSAssemblyInfo`, not the
real `ChartVSAssemblyInfo` — so `isSeededTitle(info)` is false there. That is harmless today only
because `ctx = VizContext.of(info)` resolves against the same synthetic wrapper, which is built by
`new TextVSAssembly()` and never stamped, so `ctx.modern` is false and the `!ctx.modern`
short-circuit fires before `isSeededTitle` is ever reached. Give that wrapper a real mark for any
reason and the fill silently comes back on chart exports. The safe fix at that point is to pass the
chart's own info, not the wrapper's.

### Two consequences, stated rather than discovered

**Print layout's chart title inherits its other three sides from the object border.**
`getTitleBorders:2166-2168` takes the higher-priority of title vs object per side for top / left / right,
and `applyFormat:2081` takes a single border colour from `detailfmt.getBorderColors().leftColor`. Because
all four colours are written as `TITLE_BORDER`, and `TITLE_BORDER == objectBorderColor == #D9D5CC` in
modern, the inherited card edge and the new rule are the same colour. They agree by construction — a second
reason the structural neutral beat v3's softened wash.

**Report text boxes keep only one border colour.** `TextBoxElementDef.setBorderColors:209-210` is
`getTextBoxInfo().setBorderColor(bcolors.topColor)` — three of the four are discarded. Writing all four
identically is what makes that lossy setter harmless; a per-side palette would collapse silently.

---

## 5. Risks

**The legacy branch is the whole safety property, and it is per type.** Revert, and now
`reseedAfterRestore`, are correct only if `seedChromeDefaults`'s legacy branch writes exactly what a
gate-off creation writes. The table's is `0xf5f5f5` plus a four-side `THIN_LINE`; the chart's is nothing.
Getting either wrong produces an assembly that *almost* matches one that was never modernized — the
failure mode the bookmark work's own comment was written against.

**The fill clear is the first removal this initiative has made.** Every modern default so far has added
chrome, so no path has been exercised against a cleared value. §2's `getBackground()` trap is the concrete
instance.

**`getCalendarTitleFormat` merges the object border into the title border.** `AbstractVSExporter:2693`
fills any zero-width title border side from the object border (`:2716-2721`). Calendar is deferred, so this
is inert now — but the conversion that adds it must reckon with the merge, or the calendar gains a
four-sided box where its siblings get a rule.

**The chart's re-invocation is a real behaviour change at creation**, not just a reordering: values the
hook writes are now written against the chart's real title composite rather than a discarded one. Verify a
freshly created chart and a Modernized one are identical — that equality is what `installsOwnTitleFormat()`
exists to protect and what the re-run must preserve.

---

## 6. Implementation order

Ordered so each step is independently verifiable, and so the step most likely to hide a bug is proved
first.

1. **Tests for the seed, before the seed.** Add the `SeedChromeDefaultsTest` cases in §7 and watch them
   fail. The legacy-branch cases matter most: written first, they pin the Revert contract before any
   render path depends on it.
2. **`TableDataVSAssemblyInfo.seedChromeDefaults`** — background and borders, both branches. Tables have no
   ordering problem, so this is the clean half and it lands first.
3. **`ChartVSAssemblyInfo`** — the seed in its override, plus the re-invocation at the end of
   `setDefaultFormat`. Verify a freshly created chart equals a Modernized one before moving on; that
   equality is the thing the ordering fix can break.
4. **`VSTitleChromeDefaults`** — `isSeededTitle`, the widened entry points, the early return. **At the end
   of this step the seeded types are correct in the browser and the substitution no longer double-applies.**
   This is the checkpoint: a chart and a table at all three densities, plus a selection list and a calendar
   confirmed unmoved.
5. **The remaining seven call sites**, export and composer.
6. **The `AbstractVSExporter` block** from §4, last, because its effect is invisible in the browser and
   should not be confounded with steps 4–5. Do the "what does the synthetic title box draw today" check
   here.
7. **The verification matrix** in §7, then the sibling-project conversation from Scope.

Steps 3 and 6 are the only ones that can be got wrong quietly. Steps 2, 4 and 5 announce their own failures.

---

## 7. Testing

**Unit — `SeedChromeDefaultsTest`** (the seed's own contract):

- a modern chart's title format resolves bottom-only `THIN_LINE` in `TITLE_BORDER`, and no background
- a modern table's resolves the same rule and a cleared background
- **the legacy branch of each restores exactly what a gate-off creation writes** — nothing for the chart,
  `0xf5f5f5` plus four-side `THIN_LINE` for the table
- Modernize then Revert returns an assembly equal to one created gate-off, for both types
- a freshly created modern chart equals a Modernized one (the re-invocation's contract)
- dark seeds `TITLE_BORDER_DARK 0x49454F`

**Unit — `BookmarkChromeResolutionTest`**: a bookmark taken before a Revert, restored after it, resolves
classic title chrome. This should pass without new production code — `reseedAfterRestore` already covers
it — and the test exists to prove that claim rather than to drive a change.

**Unit — `VSTitleChromeDefaultsTest`**: a seeded type returns the identical instance (`assertSame`); a
deferred type still resolves `TITLE_BG`; a legacy context still returns the identical instance, as `:118`
already asserts.

**Unit — `VSFormatModel`**: a seeded title format serialises `border.bottom` to `1px solid #d9d5cc` and
leaves the other three sides empty.

**Manual matrix** — the four in-scope types × three densities, in the viewer and in PDF, PNG, Excel and
print layout. Plus four targeted checks: a chart with an author-set title background (USER tier survives);
a selection list and a calendar (unmoved); **an asset exported to a JAR and imported into a second server**
(the case that motivated seeding); and the whole matrix once with the gate off, byte-identical to `main`.
