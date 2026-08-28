# Title Lane — Unfilled, With a Bottom Rule

**Date:** 2026-08-28
**Verified against:** community `viz-updates` @ `4e39cf71d`
**Decides:** how the modern title lane stops being a filled band and becomes a hairline rule, for the
chart and the three table types; the mechanism, the call sites that convert, and the ones that do not
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
- **Clearing a value is not the mirror of setting one.** Every modern default so far has *added* chrome.
  This is the first that removes some, and `VSFormat.getBackground()` does not behave symmetrically.

---

## Scope

**In: the chart and the three table types** — `ChartVSAssemblyInfo`, and `TableVSAssemblyInfo` /
`CrosstabVSAssemblyInfo` / `CalcTableVSAssemblyInfo` via their shared `TableDataVSAssemblyInfo`. These are
the four the chart card spec and the widget spec both draw.

**Deferred: the selection and input family** — selection list, selection tree, selection container, range
slider, checkbox, radio button, calendar. Deferred in *code* rather than in prose: they keep calling the
existing two-arg entry points, which keep today's fill. See §2.

**The cross-project consequence §1 flagged is still live and is triggered by this change.**
`VSTitleChromeDefaults` chose `TITLE_BG 0xF1EFEA` deliberately equal to the table header background "so
chrome reads as one system", and `Visualization Widget Spec.dc.html` §05 endorses it on exactly that basis.
Clearing the fill on the three table types breaks that equality where it is most visible — a table's title
bar sits directly above its header band. The sibling project should see this before it merges.

---

## 1. The mechanism

Read-time substitution, in `VSTitleChromeDefaults`, scoped by assembly type.

Both entry points gain a three-arg form taking the `VSAssemblyInfo`; the existing two-arg forms delegate
with `null`. A null info means "not an in-scope type", so every unconverted call site keeps today's
behaviour with no edit.

```java
// the unfilled lane is scoped to the card types; the selection and input family follows
private static boolean isCardTitle(VSAssemblyInfo info) {
   return info instanceof ChartVSAssemblyInfo || info instanceof TableDataVSAssemblyInfo;
}
```

`applyTo` branches on it. For an in-scope type it **clears** the background rather than substituting
`TITLE_BG`, and writes the rule onto the same DEFAULT tier of the same clone:

```java
Color c = titleBorderColor(ctx);
def.setBordersValue(new Insets(GraphConstants.NONE, GraphConstants.NONE,
                               GraphConstants.THIN_LINE, GraphConstants.NONE));
def.setBorderColorsValue(new BorderColors(c, c, c, c));
```

All four colours, only the bottom width. This is the shape `SelectionBaseVSAssemblyInfo:893-900` and
`TimeSliderVSAssemblyInfo:706-714` already use for the same visual, and §3 gives a second reason all four
must be written.

**Nothing new enters the palette.** `titleBorderColor()` and the `TITLE_BORDER 0xD9D5CC` /
`TITLE_BORDER_DARK 0x49454F` constants exist and are unused; this gives them their first production caller.
The value is already the object frame (`VSObjectChromeDefaults.objectBorderColor`) and the table
header→body separator (`VSTableStructureDefaults.headerSeparator`), so the rule matches the two structural
lines nearest it.

**The alpha in the drawing cannot survive and is not attempted.** v3 draws
`rgba(217,213,204,0.72)` (`:47`), but `VSCSSUtil.getBorder:130` emits `"#" + Tool.colorToHTMLString(color)`
— opaque hex, no rgba — and the Java painters take a `Color` from `BorderColors`. A baked wash would also
be correct only over the card it was composited against, needing a separately derived dark value rather
than the mirrored one. The flat structural neutral is used instead, deliberately.

### The customization test

The rule gets its own, parallel to the two that exist:

```java
private static boolean isBorderCustomized(VSCompositeFormat f) {
   return f.getUserDefinedFormat().isBordersValueDefined() ||
      f.getCSSFormat().isBordersValueDefined();
}
```

Both tiers have the predicate (`VSFormat:949`, `VSCSSFormat:538`). A table's existing four-sided box is a
DEFAULT-tier value written by `setDefaultFormat(border=true)`, so it is correctly overridden; an author's
border, or a `format.css` TITLE class, still wins. The early-out `if(!bg && !fg) return titleFmt` widens to
include the border case.

### The one trap

`VSFormat.getBackground():276` ends:

```java
return (rcolor instanceof Color) ? (Color) rcolor : bg;
```

It falls back to the `bg` field when `bgval` yields nothing. Setting a value hides that fallback; *clearing*
one exposes it. **Clearing the fill must null both `bgval` and `bg`**, or a background set at runtime
survives the clear and the lane stays filled on exactly the assemblies hardest to reproduce. This
asymmetry is why "pass null to the existing setter" is not sufficient, and it is the most likely
implementation bug in the whole change.

### Why read-time and not creation-time

A creation-time seed in `seedChromeDefaults` would be one mechanism instead of two, and would match how the
selection base and range slider already express this exact visual. It is rejected on timing: it persists
into the chart's `state_format` and the table's `state_tableformat`, which is precisely the surface the
bookmark work (decision 10) is being built against on this branch right now. Read-time substitution reaches
`writeState` not at all, so it needs no Revert reverser, no migration, and no coordination with that work.
This is the same reasoning that made the card inset read-time — `VSObjectChromeDefaults.chartPadding` stays
out of the bookmark because `writeAttributes` is not reached by `writeState`.

---

## 2. Call sites — 18 in all: 9 convert, 9 stay

Every converted site already holds the assembly info at the call; none needs threading.

**Convert** (pass the info):

| Site | Note |
|---|---|
| `VSChartModel:59` | chart, browser |
| `BaseTableModel:40` | the three table types, browser |
| `VSTableDataHelper:401` | tables, PDF / SVG / PNG / Excel title cell |
| `AbstractVSExporter:1411` | chart export title — also needs §3's block |
| `AbstractVSExporter:1803` | `prepareAssembly`, umbrella over every titled type |
| `AbstractVSExporter:1901` | `getTextFormat`, chart title path |
| `VsToReportConverter:1375` | print layout, umbrella |
| `VsToReportConverter:1516` | print layout, chart title |
| `FormatPainterService:220` | composer format picker (design-time WYSIWYG) |

Three of those — `AbstractVSExporter:1803`, `VsToReportConverter:1375`, `FormatPainterService:220` — are
umbrella calls reached by every titled type. They pass the info and let `isCardTitle` decide. That is the
reason the predicate lives in the defaults class rather than at the call sites: an umbrella caller cannot
express the scoping itself.

**Leave on the two-arg form** (all deferred types): `VSSelectionListHelper:76`,
`VSSelectionTreeHelper:268`, `VSCompositeModel:38` (selection base and selection container only — verified:
`BaseTableModel` does not extend `VSCompositeModel`), `VSCalendarModel:47`, `VSCheckBoxModel:37`,
`VSRadioButtonModel:37`, `VSRangeSliderModel:108`, `ExportUtil:111`, `AbstractVSExporter:2693`.

The follow-on is then one line — widen or delete `isCardTitle` — plus converting those nine sites.

---

## 3. Where the format reaches, and the one place it does not

All of the following describe what happens *once the title format carries a bottom border*. Each row is the
read that consumes it, checked in code.

| Path | How the rule arrives | Change |
|---|---|---|
| Browser, all types | `VSFormatModel:95` → `VSCSSUtil.getBorder` → `vs-title.component.html:35` | none |
| PDF / SVG / PNG, tables | `VSTableDataHelper:401` → `writeTitleCell` → `ExportUtil.drawTextBox:152-153` reads `getBorders()` / `getBorderColors()` | none |
| Excel, tables | `PoiExcelVSUtil.setBottomBorderAndColor:1425-1450` reads `getBorders().bottom` and `getBorderColors().bottomColor` | none |
| Print layout, tables | `applyFormat(isTitle=true)` → `getTitleBorders:2169` takes `detailBorders.bottom` verbatim | none |
| Print layout, chart | `VsToReportConverter:1526-1529` calls `setBorders(tformat.getBorders())`, and `TextBoxElementDef:858-861` falls back to `getBorder()` only when borders is null — so the `setBorder(NO_BORDER)` at `:1522` is already overridden | none |
| **PDF / SVG / PNG / Excel, chart** | **nothing — the copy block stops at foreground** | **the one addition** |

The chart export title is drawn as a synthetic `TextVSAssembly` whose *object* format receives the resolved
title chrome one property at a time — background at `AbstractVSExporter:1418-1421`, foreground at
`:1423-1427`, nothing else. It needs a third block, inside the `if(ctx.modern)` that already wraps them:

```java
if(titleFmt != null && titleFmt.getBorders() != null) {
   objFmt.getDefaultFormat().setBordersValue(titleFmt.getBorders());
   objFmt.getDefaultFormat().setBorderColorsValue(titleFmt.getBorderColors());
}
```

Gate-scoped and defaults-tier, so gate-off stays byte-identical: the enclosing block does not run. The
extent is already correct — `:1396-1400` positions the synthetic assembly at `pos + padding` and sizes it
`width − padding.left − padding.right`, matching the browser's inset.

### Two consequences, stated rather than discovered

**Print layout's chart title inherits its other three sides from the object border.**
`getTitleBorders:2166-2168` takes the higher-priority of title vs object per side for top / left / right,
and `applyFormat:2081` takes a single border colour from `detailfmt.getBorderColors().leftColor`. Because
all four colours are written as `TITLE_BORDER`, and `TITLE_BORDER == objectBorderColor == #D9D5CC` in
modern, the inherited card edge and the new rule are the same colour. They agree by construction, not by
coincidence — a second reason the structural neutral was the right choice over v3's softened wash.

**Report text boxes keep only one border colour.** `TextBoxElementDef.setBorderColors:209-210` is
`getTextBoxInfo().setBorderColor(bcolors.topColor)` — three of the four are discarded. Writing all four
identically is what makes that lossy setter harmless; a per-side palette here would collapse silently.

---

## 4. Risks

**The fill clear is the first removal this initiative has made.** Every modern default so far has added
chrome, so no existing code path has been exercised against a *cleared* value. §1's trap is the concrete
instance; the general risk is that clearing has no symmetric precedent to copy.

**`getCalendarTitleFormat` merges the object border into the title border.** `AbstractVSExporter:2693`
fills any zero-width title border side from the object border (`:2716-2721`). Calendar is out of scope now,
so this is inert — but the follow-on that adds the selection and input family must reckon with it, or the
calendar gains a four-sided box where its siblings get a rule.

**Nothing here touches creation or `writeState`.** The chart's membership in `installsOwnTitleFormat()`
(`VSAssemblyInfo:1340`) is unaffected — that predicate governs the creation-time seed, and this change
writes nothing at creation. The bookmark work proceeding in parallel on this branch is untouched, in both
directions.

---

## 5. Implementation order

Ordered so each step is independently verifiable, and so the step most likely to hide a bug is the one
proved first.

1. **Tests for the class, before the class.** Add the `VSTitleChromeDefaultsTest` cases in §6 and watch
   them fail. The cleared-background case is the one that matters: written first, it catches the
   `getBackground()` fallback trap before any call site depends on the behaviour.
2. **`VSTitleChromeDefaults`** — the predicate, the widened overloads, the border write, the clear, the
   customization test, the widened early-out. The two-arg forms delegate with `null`. At the end of this
   step the suite is green and **no rendered output has changed anywhere**, because nothing passes an info
   yet. That is the checkpoint: a green suite with no visual diff proves the delegation is behaviour-neutral.
3. **The two browser call sites** — `VSChartModel:59`, `BaseTableModel:40`. First visible change; verify a
   chart and a table in the viewer at all three densities before going further.
4. **The remaining seven call sites**, export and composer. Verify the three umbrella sites do not move a
   deferred type: a selection list and a calendar must look exactly as they did at step 1.
5. **The `AbstractVSExporter` block** from §3, last, because it is the only change whose effect is invisible
   in the browser and it should not be confounded with steps 3–4.
6. **The verification matrix** in §6, then the sibling-project conversation from the Scope section.

Steps 2 and 5 are the only ones that can be got wrong quietly. Steps 3 and 4 announce their own failures.

---

## 6. Testing

**Unit — `VSTitleChromeDefaultsTest`**, extending its existing shape:

- an in-scope type resolves a cleared background, bottom-only `THIN_LINE`, colour `TITLE_BORDER`
- a deferred type still resolves `TITLE_BG` and no border
- a legacy context returns the identical instance (`assertSame`, as `:118` already does)
- a USER-tier background survives; a USER-tier border survives; a CSS-tier value of each survives
- dark resolves `TITLE_BORDER_DARK 0x49454F` and the dark clear
- the in-place variant matches the clone variant on all of the above

**Unit — `VSFormatModel`**: an in-scope title format serialises `border.bottom` to `1px solid #d9d5cc` and
leaves the other three sides empty. This is the assertion that would catch the `getBackground()` fallback
surviving into the model, and it is worth having even though it overlaps the class test.

**Manual matrix** — the four in-scope types × three densities, in the viewer and in PDF, PNG, Excel and
print layout. Plus three targeted checks: a chart with an author-set title background (fill survives); a
selection list and a calendar (unmoved); and the whole matrix once with the gate off, which must be
byte-identical to `main`.
