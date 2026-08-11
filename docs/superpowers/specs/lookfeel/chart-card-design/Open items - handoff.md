# Implementation plan — handoff

Nothing here is done. Every decision is made and every verification is answered; what follows is the order
to build in. Steps are numbered where order matters and grouped where it does not.

All code references verified against `inetsoft-technology/stylebi`, branch `epic-74519` @ `c75c3fabdf64`,
confirmed against a local checkout at the same commit.

**Read first:** `README.md` (entry point) · `Chart Card Spec.html` (the spec) ·
`Chart overlay surfaces - decided visuals.html` (the three decided changes that are visual, drawn).

---

## The dependencies, in one picture

```
  A. Shell tooltip cleanup ──┐
                             ├─→ (independent of everything below)
  B. Data-tip stacking ──────┘

  C. Selection: drawRegions ─┬─→ SHIP TOGETHER (one decision, two files)
     Selection: annotation ──┘

  D. Menu actions (item 8) ──→ E. §06 ladder ──→ F. Eight-assembly rollout

  G. Chart type scale ───────→ H. Outlined text conversion (separate ticket)

  I. Resize Plot sliders ────  independent
  J. Chart colour literals ──  independent
```

Four things and only four are truly ordered: **C is a pair**, **D precedes E precedes F**, **G precedes H**,
and within any ticket the deletions come before the retokenizing.

---

## 1 · Shell first — `Shell surfaces - ticket.md`

Shared code, so it lands before anything chart-side inherits from it. Different reviewers than the chart
work; that is why the ticket is split.

**1a. Deduplicate the tooltip classes** (item 6), then bind elevation, radius and spacing (items 1, 3, 5).
Mechanical. Reaches **12 components** — table, crosstab, calc table, chart, plot/axis/legend, selection
list, range slider, gauge, image, text, annotation, hidden annotation — because `tooltipCSS` defaults to
`widget__default-tooltip`. Test one instance per consumer, not one per chart type.

**1b. Rebuild the CARD ramp** (item 4) — decided, and **visual**: read
`Chart overlay surfaces - decided visuals.html` §02 before writing any CSS. Adds
`--inet-font-size-lg: 16px`. Skip item 2; item 4 subsumes it. The consumer inventory found
`widget__card-tooltip` applied in exactly one place (`chart-area.component.ts:369`), so this reaches chart
data-point tooltips only.

**1c. Register the data tips in `$stacking-order`** (item 12). **Highest-value single change in the whole
set** — a live bug, not a cleanup: the registry holds two entries (`.fixed-dropdown` 999900, `w-tooltip`
999901) while the data tips sit at 9996, two orders of magnitude below, so any dropdown or tooltip renders
over a data tip. The `+99998`/`+99999` offsets in `vs-pop-component.directive.ts` are the workaround. Do
not let this wait behind the colour work.

---

## 2 · Selection — both halves, one changeset

`Chart overlay surfaces - ticket.md` items 7–8 **and** `Shell surfaces - ticket.md` item 11.

One decision, two implementations: `ChartTool.drawRegions()` for canvas regions, the annotation border for
overlays. **Ship them together.** Either alone leaves the product with two selection idioms, which is the
thing the decision was taken to end. Drawn in `Chart overlay surfaces - decided visuals.html` §01.

- Fill → `--inet-focus-ring-color`, stroke → `--inet-primary-color`, one rule on `.chart-object-canvas`
- Chrome selections (10 areas: four axes, legend content and title, four axis titles) stroke-only at 2px;
  data (`plot_area`) keeps the fill
- Annotation border drops `2px dotted darkgray` for the same solid 2px primary — 11 mount sites
- **Manual pass required.** That CSS branch has never executed in production: nothing currently sets
  `color`/`border-color` on those canvases, so every selection today is on the hardcoded `#dc581e`. This is
  the first time the code path runs.
- Verification that catches drift: select an annotation on a chart **and** on a table; they must match.

---

## 3 · Toolbar chain — ordered, and the only long pole

**3a. Menu actions** (item 8). Add `show-data` and the `open-max-mode`/`close-max-mode` pair to
`createMenuActions` with their existing visibility predicates. Small. Not a hard precondition — §02's
concatenation means a suppressed toolbar's actions overflow to both entry points anyway — but that
reachability is a side effect of overflow arithmetic rather than a guarantee, and their absence is a
standing bug: right-click cannot reach max-mode today, spec or no spec. Two notes: the max-mode pair is one
state-dependent entry, not two rows; menu actions render label-only, so do not carry the toolbar glyph
across.

**3b. The §06 ladder** (item 9). Comfortable ≥96px · compact 56–96px · kebab-only 32–56px · no chrome below
32px, capped at `Math.min(3, allowedActionsNum())`, kebab always last to go. Two pieces of work: add the
height test `allowedActionsNum()` lacks (it only divides `objectFormat.width` by the ~40px
`getActionsWidth` derives from the root font size), and sanity-check the thresholds against real
dashboards. Only 32px is derived; **56px is the likeliest number in this spec to be wrong** — a 56px card is
common in a KPI row. Touch has no ladder: the buttons stay inside `@if (!mobileDevice)`, so the kebab is
the whole strip at 44px above 52px.

**3c. Eight-assembly rollout** (spec §02, sixth source edit). Hide MiniToolbar moves to the menu **in the
shared base class** — `AbstractVSActions.createToolbarActions` prepends `close-icon` ahead of every
assembly action, so under a cap of three it eats a slot before `show-data`. Sequence the other seven types
with it rather than shipping the chart alone. **Before starting, read `Anchoring beyond charts -
discussion.md`:** the range slider is out (no `titleVisible`), and selection assemblies are the tightest
geometry in the set — their title lane is only `titleRatio` of a ~150px assembly, so 3b's fit test must
measure the *title's* share, not the assembly width. The chart pilot never exercises that case.

---

## 4 · Type scale — ordered pair, independent of everything above

**4a. Apply the named scale** (`Chart type sizes - ticket.md`). Interior 9/10/11 as
`--inet-font-size-chart-sm`/`-base`/`-title`, chrome 11/13/16. Values are today's, so nothing moves.
**One step first:** measure a rendered tick label to confirm 9pt renders as 9px — derived from Java2D point
units and Batik's 72dpi user space, not yet measured. If it disagrees, keep the roles, substitute the
values. Also closes handoff item 10 (server 9/10/11pt vs browser 13px), which is the same question.

**4b. Outlined text conversion** (`Outlined chart text - ticket.md`). Only after 4a: without a named scale,
converting paths to `<text>` lets chart labels inherit the 13px base and silently re-lays out every chart
in the product on upgrade.

---

## 5 · Independent — any time, any order

- **Resize Plot sliders** (chart ticket item 13). Delete the IE11 block **first** — provably dead, Angular
  21 cannot run there, roughly half the file — then apply `Chart overlay surfaces - decided visuals.html` §03. Keep it a native
  `input[type=range]`: the portal's four div sliders are not keyboard-reachable (`vs-slider` is
  `role="slider"` with `tabindex="-1"`), so matching them would trade a working control for a consistently
  broken one.
- **Chart colour literals** (chart ticket item 10) — mechanical binding.
- **Drill and date-comparison tips** (chart ticket item 14).
- **Nav bar** (spec §02). Stays floating, moves to the lower right, insets from the **plot area** so it
  clears the x-axis and any bottom legend. Two cleanups while it is open: the off-scale 5px radius and
  `z-index:9999`. Open question that changes reach, not the decision: does it render for maps only or any
  zoomable chart?
- **Zoom naming** (`Zoom naming - ticket.md`) — three controls using zoom language for unrelated things.
  Explicitly **not** part of the anchoring pilot.
- **Dead menu icons** (`Dead menu icons - ticket.md`).

---

## Not ours, but real

**Four forks of one slider design**, ~40KB of SCSS: `vs-slider` (14.5KB), `binding/widget/slider` (10.5KB),
`binding/widget/range-slider` (9.5KB), `vs-range-slider` (4.5KB) — the first two sharing a class vocabulary
almost line for line. None keyboard-reachable. Found while answering a question about the chart's slider;
it is somebody's consolidation ticket, and nobody's yet.

**Generalising the anchor pattern** beyond charts — `Anchoring beyond charts - discussion.md` is a
discussion, not a plan. Read it before 3c; do not treat it as specified.

---

## Rules that apply throughout

1. **Never scope defensively to the chart.** No `.vs-chart .widget__card-tooltip`. The Hide MiniToolbar
   precedent: the edit lands in shared code, because an override leaves a divergence that becomes
   permanent.
2. **Test one instance per consumer, not one per chart type.** Five chart types tell you nothing new; a
   tooltip on a table and an annotation on a selection list do.
3. **Shell before chart.** Reversed, the chart ships a local value the shared change then has to unpick.
4. **Deletions before bindings**, inside every ticket. Both the slider file and the tooltip rules shrink by
   roughly half before anything is retokenized — retokenizing first means doing it twice.
