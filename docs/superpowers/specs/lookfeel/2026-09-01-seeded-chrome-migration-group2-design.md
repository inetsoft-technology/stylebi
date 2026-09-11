# Seeded Chrome Migration, Group 2 — Design

**Date:** 2026-09-01
**Verified against:** community `viz-updates` @ `39aa4b252`, which is `HEAD`. Every file and line citation
below was read at that commit.
**Roadmap entry:** "READ-TIME MIGRATION. Move the substituting resolvers onto `seedChromeDefaults`" in
[chart-card-roadmap.md](./chart-card-roadmap.md), ranked #3 there. Group 1 closed four of the five
resolvers; this closes the rest.
**Precedent:** [2026-09-01-seeded-chrome-migration-group1-design.md](./2026-09-01-seeded-chrome-migration-group1-design.md)
is the immediate predecessor, and its "What the implementation found" section is required reading — three
of its lessons are load-bearing here. [2026-08-28-title-lane-unfilled-design.md](./2026-08-28-title-lane-unfilled-design.md)
§1 is the general argument for seeding over read-time substitution.
**Decides:** how the last three titled types convert, what the calendar's seed writes and how it survives
the show-type prototype swap, whether Excel's dark opt-out extends to titles and painted pictures, and
where the `MaxModeSelectionVSAssemblyInfo` duplication goes.

---

## Scope

**In: three conversions and two follow-ons.** The checkbox and radio-button title lanes; the calendar
(title lane, month/year header, and body cells); the deletion of both read-time resolvers with their
fifteen call sites; the `MaxModeSelectionVSAssemblyInfo` hoist; and the Excel dark opt-out for titles and
the painted slider.

**Out and staying read-time, by design, unchanged from group 1:** `VSDensityDefaults.rowHeight`,
`titleHeight` and `mode()`. `mode()` reads the live org property `viewsheet.density` (`:66-69`); the mark
decides whether an assembly honours density, not which density is in force, so seeding a density-derived
value would freeze it at creation.

**Out: the weekday-header path split.** `MONTH_CALENDAR` / `YEAR_CALENDAR` is one format shared by the
weekday header and the date cells, so the header cannot take a header treatment without the cells taking
it too. Splitting it is a stored-format change with migration, export, format-pane and CSS-mapping
consequences — a different initiative from a chrome migration. §4 explains what this design does instead.

**Out: the calendar's other two design-set calls.** The derived selection fill with endpoint borders and
the density row with two floored values are separate features, not chrome-default migration.

---

## 1. What group 2 ends

After this, **no chrome value is computed at render.** `VSTitleChromeDefaults` becomes a palette supplier —
the shape `VSObjectChromeDefaults` and `VSOutputChromeDefaults` took in group 1, which is what both classes
were becoming anyway. `VSCalendarChromeDefaults` is deleted outright rather than reduced, because its two
colours turn out to be a third copy of a palette that already exists in two places (§4).

The mechanism is group 1's, unchanged and not re-derived here:

- `seedChromeDefaults` writes the stored `DEFAULT` tier at creation.
- **Both branches always write.** `VizModernizeUtil.revert` (`:102-111`) clears the mark and re-runs the
  hook; the only other work it does is sheet-level. A value written on the modern branch and not on the
  legacy one is stranded by Revert.
- **No customization guards.** A `USER` or `CSS` value outranks `DEFAULT` by construction, so the
  `isForegroundCustomized` / `isBackgroundCustomized` tests the resolvers carried are not reimplemented.
- **No skip-list.** `isSeededTitle` existed because the title conversion crossed ten types incrementally.
  This group converts the remaining three in one step, so the predicate is deleted rather than extended.

---

## 2. None of the three types runs the hook today

This is the structural fact that shapes all three conversions, and only the calendar's half of it was
recorded in group 1.

`CheckBoxVSAssemblyInfo:408` and `RadioButtonVSAssemblyInfo:375` override `setDefaultFormat(boolean)`
**without calling super**, so the base's `seedChromeDefaults(VizContext.of(this))` at `VSAssemblyInfo:1235`
is unreachable for them. `CalendarVSAssemblyInfo.initDefaultFormat` (`:88-91`) clones a static `FormatInfo`
and never calls `setDefaultFormat` at all, so the hook has never run for a calendar either.

All three also sit on `bypassesBaseChrome()` (`VSAssemblyInfo:1325-1334`). That is not an obstacle — it is
the position `TextVSAssemblyInfo` was in during group 1, and the same resolution applies: each type
overrides `seedChromeDefaults`, calls `super`, and `super` returns at the bypass guard. The base's object
border and card radius stay where they belong, in each type's own `setDefaultFormat`.

**`bypassesBaseChrome()` and `installsOwnTitleFormat()` are not modified.** The second is never consulted
for a bypassing type, because the guard returns before it is read. Adding the three types to it would be
inert code expressing a false belief about the control flow.

---

## 3. Checkbox and radio: the title lane

**The treatment: unfilled, with a bottom hairline rule** — the same lane every converted type carries.

| | modern | legacy |
| --- | --- | --- |
| borders | `titleRuleBorders()` | `Insets(0, 0, 0, 0)` |
| border colours | `titleRuleColors(ctx)` | `null` |
| foreground | `titleForegroundValue(ctx)` | `null`, **and `setForeground(null)`** |
| background | `null` | `null` |

The foreground needs both writes. `getForeground()` falls back to the `fg` field when `fgval` yields
nothing, so nulling only the value leaves a runtime foreground alive across a Revert — the trap the
selection and slider seeds both document.

The legacy border colour is written as `null` rather than left alone, and this differs from the selection
and slider seeds on purpose. Those write an explicit `legacyTitleRuleColors()` because their legacy state
*has* a grey rule. A gate-off checkbox has no rule on any side, so leaving the modern rule colour stored
after a Revert would put a value in the asset that a gate-off creation never writes. It would not render —
the widths are zero — but the contract is that the legacy branch reproduces a gate-off creation exactly,
and `FormatInfo.equals` is a deep comparison that other code depends on (§5 is the case in point).

Background is `null` on **both** branches because these two titles have never carried a fill: their
`setDefaultFormat` writes borders, font, alignment and a CSS type, and no background. This is the slider's
shape, not the table's — the legacy state is the absence of a value rather than a colour.

**Not hoisted to `ListInputVSAssemblyInfo`.** `ComboBoxVSAssemblyInfo:43` extends it, and a combo box is
not a titled card and must not gain a rule. The override goes on the two concrete classes.

**This extends a decision rather than transcribing one.** The external design set has no opinion on these
two types: the widget spec's "The widget set, one at a time" never mentions either, and the only mentions
anywhere in `chart-card-design3/` place them in a "small input widgets are not in scope and never were"
bucket that is scoped to mini-toolbars, not to chrome. The authority applied here is the in-repo
title-lane-unfilled decision, which covers every titled type. Recorded because the next person will look
for a drawing and not find one.

---

## 4. Calendar: what the seed writes

**A static `applyCalendarSeed(FormatInfo, VizContext)`, with three uses:** the instance hook mutates the
installed `FormatInfo` in place, and §5's two prototype sites apply it to clones. One decision about what
the values are, in one place — the tree's lesson, that a seed which copies rather than re-derives cannot
drift.

`initDefaultFormat` gains the hook call after `setCSSDefaults()`.

**`TITLEPATH`** — the object title lane, treated as §3 treats the inputs, except that the legacy branch
restores `DEFAULT_TITLE_BG` and the calendar's own mixed border colours, because that is what a gate-off
creation writes for this type.

**`CALENDAR_TITLE`** — the month/year header. Takes `VSTableStructureDefaults.headerForeground(ctx)` and
`headerBackground(ctx)`: it is a header band sitting above date cells, which is the relationship a table
header has to its body. Nothing has ever written this path (§6).

**`MONTH_CALENDAR` and `YEAR_CALENDAR`** — the weekday header and date cells, shared. Takes
`VSTableStructureDefaults.bodyForeground(ctx)` and `bodyBackground(ctx)`.

That last one deletes `VSCalendarChromeDefaults` rather than reducing it, and the reason is worth stating
because the design set argues otherwise. Its `CELL_FG_DARK 0xE6E0E9` / `CELL_BG_DARK 0x252428` are
**byte-identical** to `VSTableStructureDefaults.BODY_FG_DARK` / `BODY_BG_DARK` (`:119-120`) — a third
declaration of one palette, kept in sync by nothing.

**The design set's light-modern argument does not survive contact with the code.** The widget spec says
the calendar "keeps legacy black-on-nothing, so it is the one assembly that visibly does not belong" in
light modern. But `VSTableStructureDefaults.bodyForeground` and `bodyBackground` (`:81-91`) return **null
in light** — the table body is also black-on-nothing there, by construction. The calendar's date cells are
not the odd one out, and there is no light body neutral to take.

What the design set was reaching for is real but structural: the *weekday header* has no light-modern
treatment and would want the header neutrals, and it cannot have them while it shares a format with the
date cells. Calling the table's suppliers delivers the part that is actually available — the gate moves
from dark-only to modern-with-dark-as-a-modifier, which is what §02 asked for — and leaves the split
recorded as the open item it is.

---

## 5. Calendar: the prototype swap and the equality guard

`copyViewInfo:1058-1069` swaps the calendar's whole `FormatInfo` when the show type changes, gated on
`getFormatInfo().equals(normalDefault)`. Two problems, one pre-existing and one created by seeding.

**The pre-existing one: the statics are installed uncloned.** `setFormatInfo((FormatInfo) dropdownDefault)`
at `:1063` and `setFormatInfo((FormatInfo) normalDefault)` at `:1067` hand the assembly the JVM-wide static
itself. Any later format edit through the composer mutates it; and since `initDefaultFormat` does
`normalDefault.clone()`, a corrupted prototype propagates to every calendar created afterwards in that JVM,
across orgs. This is a live bug independent of this work, reachable from `AbstractVSAssembly:584` whenever
an author changes the show type in the property dialog.

**The one seeding creates:** `FormatInfo.equals` is `fmtmap.equals(info.fmtmap)` (`FormatInfo:534-541`), a
deep value comparison. Any seeded `DEFAULT`-tier value makes a seeded calendar unequal to the raw
prototype, so the guard stops matching and the show-type swap silently stops happening.

**Both are fixed by comparing against a seeded prototype and installing a seeded clone:**

```java
FormatInfo expected = normalDefault.clone();
applyCalendarSeed(expected, ctx);

if(getFormatInfo().equals(expected)) {
   FormatInfo next = dropdownDefault.clone();
   applyCalendarSeed(next, ctx);
   setFormatInfo(next);
}
```

The guard keeps its real meaning — "this calendar's formats are still the untouched default for its show
type" — now evaluated against the default *for its mark*, and the statics are never aliased. The seed is
applied to a clone here and in place in the hook, which is why it is a static taking a `FormatInfo` rather
than a method reading `this`.

The rejected alternative was to fix only the aliasing and let the guard stop matching for marked
calendars. It is cheaper and it silently changes behaviour: a marked dropdown calendar would keep the
normal calendar's object format, with no test anywhere that notices.

---

## 6. Two defects the render-path sweep found

Group 1's most expensive finding was that seeding a selection tree's `DETAIL` cell left five cloned
`GROUP_HEADER` composites stale, because the design had enumerated the paths the *resolver* was called
from rather than the paths the type *renders*. That sweep was run first here. It found two things, and
both would have made the calendar's seed partly ineffective.

**A `USER`-tier write defeats the dark body ink in the year view.** `VSCalendar:987` sets
`format.getUserDefinedFormat().setForeground(new Color(90, 90, 90))` whenever the user foreground is null.
`USER` outranks `DEFAULT`, so the body colour never reaches the year view. This is **already true today**
with the substitution — a dark year calendar exports as `0x5A5A5A` on `0x252428` — and seeding does not
change it, because the tier ordering is the same. The fallback must become mode-aware, or move off the
`USER` tier.

**`CALENDAR_TITLE` has never had any treatment.** `VSCalendarModel:51-53` and `VSCalendar:952-953` read it
raw; neither resolver has ever touched it. In dark mode that is black ink on the dark card. The browser is
covered by the three `--surface` CSS classes; the server-painted export is not, which is precisely the
class of defect this whole track exists to close. §4 gives it the header treatment.

**The generalisation, for whoever converts the next type:** a `USER`-tier write anywhere in a render path
silently outranks a seed. The sweep is `getUserDefinedFormat().*set` across every painter and model for
the type being converted, run before costing the conversion — not only the paths the resolver touched.

---

## 7. Deleting the read-time family

With the last three types converted, all of this goes:

- `VSTitleChromeDefaults.applyModernDefaults` (both overloads), `applyModernDefaultsInPlace` (both
  overloads), and `isSeededTitle`.
- `VSCalendarChromeDefaults`, entirely.
- Fifteen call sites unwrapped, across `AbstractVSExporter`, `ExportUtil`, `VSSelectionListHelper`,
  `VSSelectionTreeHelper`, `VSTableDataHelper`, `VsToReportConverter`, `FormatPainterService`, `VSCalendar`,
  and six web models.

`ExportUtil.getBackGroundColor` loses the `ctx` and `info` parameters this leaves dead. That signature
reaches `utils/inetsoft-xml-formats`, so this needs **a clean 46-module reactor build, not an incremental
one** — the fourth time on this branch that a cross-module signature change has passed an incremental
install with stale callers.

`VSTitleChromeDefaultsTest` and `VSCalendarChromeDefaultsTest` are live callers of the deleted methods and
must be rewritten against the seeds or deleted. Group 1's rule applies: a "zero callers" claim that greps
only `src/main` is wrong.

---

## 8. The MaxModeSelection hoist

`SelectionBaseVSAssemblyInfo:932` and `TimeSliderVSAssemblyInfo:721` share a duplicated twelve-line title
block inside `seedChromeDefaults`, plus byte-identical four-line `legacyTitleRuleColors()` helpers
(`:1079-1082` and `:1149-1152`). Both move to their shared parent `MaxModeSelectionVSAssemblyInfo`, which
has exactly those two subclasses and is a ~35-line max-mode mixin owning neither method today.

**The bodies are no longer byte-identical, and the original recommendation is stale on this point.** It
was written before group 1 added the `DETAIL`-cell foreground seed to `SelectionBaseVSAssemblyInfo` only.
The shape the hoist therefore takes:

- `MaxModeSelectionVSAssemblyInfo.seedChromeDefaults` — `super`, then the shared title block. Owns
  `legacyTitleRuleColors()`.
- `SelectionBaseVSAssemblyInfo.seedChromeDefaults` — `super` (which now does the title), then its
  `DETAIL`-cell block, which stays.
- `TimeSliderVSAssemblyInfo.seedChromeDefaults` — **deleted**, the parent does all of its work.

**The re-runs stay in both subclasses.** Their reasons differ — one replaces the title composite, one
mutates it in place — and their `setDefaultFormat` bodies differ.

**The value is smaller than the original recommendation claimed, and this is recorded rather than smoothed
over.** That note justified the hoist partly by collapsing two predicates, `installsOwnTitleFormat()` and
`isSeededTitle()`. §7 deletes the second outright, so only the first collapses. What remains is 22 fewer
duplicated lines and one predicate branch — worth doing while the area is open, not worth doing on its own.

---

## 9. Excel: extending the opt-out to titles and pictures

Group 1 inverted Excel's dark opt-out for selection cells: a spreadsheet has no page to paint, its cells
are unfilled white, and a seeded light neutral is invisible on them, so `ExcelSelectionListHelper.applyDarkOptOut`
substitutes the legacy near-black back onto a clone. PPT does not do this, because a slide takes the
viewsheet background.

The same defect exists for the seeded title ink (`0xCAC4D0`) and the slider's painted labels (`0xE6E0E9`),
and was parked by group 1 as a doctrine question. **The doctrine extends.**

It extends at one site rather than ten. `AbstractVSExporter:1801-1806` already mutates the title format
once on the export copy, with the comment *"Every per-widget / per-format title draw reads the title format
from this one assembly, so doing it here covers all titled assemblies at once."* §7 empties that site; the
inverse substitution takes its place:

```java
if(!paintsPageBackground() && VizContext.of(vinfo).dark) {
   // titles: every downstream draw reads this one format
   applyDarkOptOutInPlace(fi.getFormat(TITLEPATH));

   // the slider is a picture painted from format.getForeground()
   if(vinfo instanceof SliderVSAssemblyInfo) {
      applyDarkOptOutInPlace(fi.getFormat(OBJECTPATH));
   }
}
```

`protected boolean paintsPageBackground()` defaults to `true` on `AbstractVSExporter` and is overridden
`false` in exactly one place: `inetsoft.web.viewsheet.service.ExcelVSExporter`, the abstract class **in
core** that `PoiExcelVSExporter` extends and `OfflineExcelVSExporter` extends in turn. One override covers
both Excel exporters, and `utils/inetsoft-xml-formats` is not touched by this item at all.
`CSVVSExporter` keeps the default and is unaffected either way, writing no colours.

The predicate names the actual distinction — does this format paint a surface behind the ink — rather than
testing for Excel, which is what makes it answer for the slider picture and the title cell with the same
branch.

The per-cell `applyDarkOptOut` in the two Excel selection helpers **stays**. It is a different path: cell
values, not the assembly's title format, and it runs downstream of this site.

The rejected alternative was to give Excel a surface — write the dark card background into the cells and
behind the picture. It reverses a doctrine already stated in code, and would make a dark dashboard export
as a dark spreadsheet.

---

## 10. Testing

**Unit tests go in `SeedChromeDefaultsTest`**, which already carries the `gateOn()` / `gateOff()` helpers
and the Spring context, following its existing naming. One modern / legacy / revert triple per converted
type. Two assertions are specified rather than left to the implementer:

- The calendar's body and header values assert **equality with `VSTableStructureDefaults`' suppliers**, not
  with colour literals, so the calendar and the table cannot drift apart.
- `CALENDAR_TITLE` is asserted in light *and* dark. Light is the branch that has never had coverage.

**A new test for the prototype fix** — the part with no coverage today and a silent failure mode:

- After a show-type swap followed by a format edit, `normalDefault` and `dropdownDefault` are unmutated.
  This is the pre-existing aliasing bug, newly testable.
- The swap still fires for a *marked* calendar. If the guard normalization regresses, nothing else catches
  it.

**§6's year-view fallback** gets a test that a dark year view resolves the body ink rather than `0x5A5A5A`.

**§9's substitution is extracted as a package-visible static and tested directly**, following group 1's
precedent for the same reason: `VizContext.of(..)` reads the live density property and needs a bootstrapped
server. The same accepted trade is recorded rather than hidden — a mis-wiring at the call site would not be
caught by that test.

### The manual matrix

No automated gate substitutes for these.

1. **Export a marked dashboard, import it, confirm the values arrive.** The acceptance check: this is the
   portability defect the whole track exists to close.
2. **Dark title, slider, selection list and tree exported to Excel.** Fails *silently* as white-on-white if
   the opt-out is wrong — the reason check 6 existed in group 1.
3. **Calendar show-type switch, marked and unmarked, then edit a format and create a second calendar.**
   Catches prototype corruption, whose blast radius is every calendar in the JVM.
4. **Modernize and Revert on a dashboard holding a checkbox, a radio button and a calendar.**
5. **Dark year-view calendar**, in the browser and in export (§6).
6. **Ad-hoc filter title**, both modes — the surface the selection-family design flagged as missed.

---

## 11. Order

W1 (checkbox + radio, §3) and W2 (calendar, §4-§6) are independent; either may go first. W3 (§7) needs
both, since it deletes a resolver only once its last consumers are gone. W5 (§9) lands at the site W3
empties, so it follows. W4 (§8) is independent of all of them and goes last, being the smallest and, after
§7, the least valuable.

---

## What this closes, and what it does not

**Closes:** the roadmap's read-time migration item, both groups — after this no chrome value is computed at
render, so an export into a build without this work renders the card consistently rather than mixed. The
parked Excel legibility item. A live pre-existing cross-tenant bug (§5). Two dark-mode defects nothing had
recorded (§6).

**Leaves open:**

- **The weekday-header path split.** `MONTH_CALENDAR` is shared by the header and the date cells, so the
  header cannot take the light-modern header neutrals while that holds. This is the structural change the
  design set was reaching for when it asked for "light-modern coverage".
- **The calendar's §07 selection fill and §04 density rows.** Separate features.
- **`chart-card-design3/` §05 is stale.** It still documents the title bar as "#F1EFEA on #6A685F with a
  #D9D5CC rule, deliberately equal to the table header so chrome reads as one system" — the filled band,
  superseded three days after that folder was last synced. The title-lane-unfilled design predicted this
  and asked that the sibling project see it before merging; it has not been resynced. A coordination item,
  not code. Note also that notes written inside `chart-card-design3/` are destroyed on the next sync, so
  this belongs here.

**Decisions taken here that the design set does not cover at all:** the checkbox and radio title treatment
(§3). Neither type appears in the widget spec's enumeration, and the only mentions in the whole external
set place them outside a mini-toolbar scope that has nothing to say about chrome. The treatment applied is
an extension of the in-repo unfilled decision to two types no drawing covers.
