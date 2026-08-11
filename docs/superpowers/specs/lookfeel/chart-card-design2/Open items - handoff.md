# Implementation plan — handoff

**What changed since the last handoff:** branch moved from `epic-74519` to `viz-updates`. Steps 1
(shell tooltip cleanup), 2 (selection vocabulary), 3a (menu actions) and the first two slices of 3c
(chart, table, crosstab, calc table) have **shipped as code**, matching the decisions on file — treat
those steps as verification passes, not build work, but re-verify against current `viz-updates` before
skipping anything; this branch moves daily. Step 1c is corrected below: it looked touched but the
underlying bug is still open. A new step 0 and a new precondition (M, below) come from a sibling audit
of the wider visualization widget set on the same branch. Two decisions this project hasn't made are
called out at the end.

Every decision is made and every verification is answered; what follows is the order to build in.
Steps are numbered where order matters and grouped where it does not.

**Read first:** `README.md` (entry point) · `Chart Card Spec.html` (the spec) ·
`Chart overlay surfaces - decided visuals.html` (the three decided changes that are visual, drawn).

---

## The dependencies, in one picture

```
  0. :root token additions ──→ gates J (item 10) and the §08 GDefaults mapping

  A. Shell tooltip cleanup ──┐
                             ├─→ (independent of everything below)
  B. Data-tip stacking ──────┘

  C. Selection: drawRegions ─┬─→ SHIP TOGETHER (one decision, two files)
     Selection: annotation ──┘

  D. Menu actions (item 8) ──→ E. §06 ladder ──→ F. Eight-assembly rollout
                                                   ▲
  L. Title lane + strip density gating ─────────────┘  must land first
     (Visualization Widget Spec §08 step 3)

  M. Seed mark (Visualization Widget Spec §03/§08 step 4)
     gates RELEASE and the two workstreams that write persisted
     format (§04 density heights, §07 derived selection) — not F

  G. Chart type scale ───────→ H. Outlined text conversion (separate ticket)

  I. Resize Plot sliders ────  independent
  J. Chart colour literals ──  independent
```

Four things and only four were originally ordered: **C is a pair**, **D precedes E precedes F**, **G
precedes H**, and within any ticket the deletions come before the retokenizing. **A fifth is now
ordered: L precedes F.**

**Corrected — an earlier draft ordered M before F instead.** The sibling project's tri-state seed mark
(`gate-on` / `gate-off` / `before gate`, on `VSAssemblyInfo`) exists to make **persisted** format seeding
reversible: `VSObjectChromeDefaults` is the one resolver of six that writes at creation rather than at
read, so a gate-off product cannot currently un-write the frame colour and card background it seeded
while the gate was on. F writes nothing persisted — every file in the three shipped slices is under
`web/projects/portal/src`, with no Java, no `VSAssemblyInfo` and no `setDefaultFormat` path — so there is
no reversibility record for it to miss, and slice 3 shipping without the mark was not an oversight.

`Visualization Widget Spec.dc.html` §08 says the same from the other side: its step 4 makes the mark a
release condition ("the branch does not release without it") and never names this rollout, while its
**step 3 — the title lane row at 20/26/30, and gating the anchored strip to compact-and-above with dense
falling back to the hover-revealed overlay — is what it says "unblocks the chart card's eight-assembly
rollout."** That is L above, and it reaches back into the three slices already shipped, which anchor
unconditionally with no density condition.

M is still real, still decided, and still not built. It gates release, §04's density heights and §07's
derived selection fill. Check its status with that project before starting either of those.

---

## 0 · Add the four `:root` tokens — one owner, before anything binds to them

Spec §07/§08 ask for four additions that no other step owns. Two later steps already depend on the first
of them, so this is a precondition, not a cleanup.

- **Gridline grey** — the only grey in the audit with no shipped equivalent; it sits between
  `--inet-shell-border-default` and the subtle border. Both the chart colour literals (item 10) and the
  §08 GDefaults mapping bind against it, so neither can start until it exists.
- **Touch control height** — 44px, the value 3b's touch row already assumes.
- **Two icon sizes** — the toolbar glyph and the kebab, currently literals at every call site.

One owner, one changeset, `:root` only — no consumers rebound here. The bindings happen in their own steps.

**While you are in here:** the resizer greys (item 10) and the slider greys (§5, Resize Plot) are the same
affordance class — drag-to-resize at two sizes — and the plan currently sends them down separate paths.
They should land on the same token; if §03's drawing settles the slider on a different one, that is a
divergence to raise, not to implement.

---

## 1 · Shell first — `Shell surfaces - ticket.md`

Shared code, so it lands before anything chart-side inherits from it. Different reviewers than the chart
work; that is why the ticket is split.

**Shipped, verify before rebuilding.** Items 1, 3, 4, 5 and 6 are implemented in `_directives.scss` under
`.viz-modern`: duplicated tooltip rules folded into one mixin (6), elevation on `--inet-shadow-overlay`
(1), radius on `--inet-radius-sm`/`-xl` (3), body copy on `--inet-font-size-base` (5), and the CARD ramp
rebuilt as three roles at 16/13/11 with `--inet-font-size-lg` (4).

**1a. Deduplicate the tooltip classes** (item 6), then bind elevation, radius and spacing (items 1, 3, 5).
Mechanical. Reaches **12 components** — table, crosstab, calc table, chart, plot/axis/legend, selection
list, range slider, gauge, image, text, annotation, hidden annotation — because `tooltipCSS` defaults to
`widget__default-tooltip`. Test one instance per consumer, not one per chart type.

**1b. Rebuild the CARD ramp** (item 4) — decided, and **visual**: read
`Chart overlay surfaces - decided visuals.html` §02 before writing any CSS. Adds
`--inet-font-size-lg: 16px`. Skip item 2; item 4 subsumes it. The consumer inventory found
`widget__card-tooltip` applied in exactly one place (`chart-area.component.ts:369`), so this reaches chart
data-point tooltips only.

**1c. Register the data tips in `$stacking-order`** (item 12). **Still open, despite looking touched.**
A third registry entry (`.pop-component-content`) has landed, placed deliberately below `.fixed-dropdown`
so a dropdown opened inside a data tip renders above it — but that entry is a ceiling, not a
registration. The scrim and pop-source z-indexes are still assigned per-object at runtime in
`date-tip-helper.ts`, outside the registry, so the data tips still sit two orders of magnitude below the
dropdowns and the `+99998`/`+99999` offsets in `vs-pop-component.directive.ts` are still the workaround.
Re-check both files before assuming this is closed — it's exactly the kind of item that looks finished on
a fast-moving branch. The registry and `date-tip-helper.ts`'s max-zindex constant need to change together.
Do not let this wait behind the colour work.

---

## 2 · Selection — both halves, one changeset

`Chart overlay surfaces - ticket.md` items 7–8 **and** `Shell surfaces - ticket.md` item 11.

**Shipped, matching the decision exactly.** `ChartTool.drawRegions()` takes an `areaName` and gates
stroke-only chrome selection on `GuiTool.isVizModern() && isChromeArea(areaName)`; the annotation border
carries `border: 2px solid var(--inet-primary-color)` under `.viz-modern`. Drawn in `Chart overlay
surfaces - decided visuals.html` §01.

- Fill → `--inet-focus-ring-color`, stroke → `--inet-primary-color`, one rule on `.chart-object-canvas`
- Chrome selections (10 areas: four axes, legend content and title, four axis titles) stroke-only at 2px;
  data (`plot_area`) keeps the fill
- Annotation border drops `2px dotted darkgray` for the same solid 2px primary — 11 mount sites
- **Manual pass required, and it's shared with a sibling project's export parity pass** — that CSS branch
  had never executed in production before this shipped, so this is the first time it has; a sibling audit
  of the wider widget set scoped an export parity pass over the same decision (calendar chrome, this
  derived selection fill, the range slider) across PDF/PNG and both densities. Run it once, not twice.
- Verification that catches drift: select an annotation on a chart **and** on a table; they must match.

---

## 3 · Toolbar chain — ordered, and the only long pole

**Shipped: 3a, 3b, and 3c's first two slices (chart, table, crosstab, calc table).** Verify against
current `viz-updates` before treating any of this as still to build; the remaining rollout is below.

**3a. Menu actions** (item 8). `show-data`, `open-max-mode`/`close-max-mode` are in `createMenuActions`
with their toolbar predicates. Verify: menu actions render label-only, no toolbar glyph carried across.

**3b. The §06 ladder** (item 9). Live: `ACTION_FLOOR=32`, `ACTIONS_MIN=56`,
`Math.min(3, allowedActionsNum())`, kebab always last to go. Sanity-check the thresholds against real
dashboards now that they're rendering rather than proposed — 56px was flagged as the likeliest number to
be wrong, and a 56px card is common in a KPI row.

**3c. Eight-assembly rollout** (spec §02, sixth source edit). **Slices 1–2 shipped**: `vschart`,
`vstable`, `vscrosstab`, `vscalctable` are in `ANCHORED_ASSEMBLY_TYPES`, all inheriting the chart's
treatment unchanged. **Remaining, still undecided:** the selection family (list, tree, container),
calendar. Read `Anchoring beyond charts - discussion.md` before starting any of them — the selection
family's title lane is only `titleRatio` of a ~150px assembly, tighter than anything shipped so far
exercises, and the range slider stays excluded (no `titleVisible`). ~~Before starting: confirm the seed
mark (M, above) has landed.~~ **Wrong precondition — see the correction under the dependency picture.**
This rollout seeds nothing; it is render-time gating. **Before starting: land L** —
`Visualization Widget Spec.dc.html` §08 step 3, the title lane row and the strip's compact-and-above
gating, retroactively across the shipped slices.

**Also stale:** the selection family shipped in `a038a30b5` (2026-08-11, after this sync), anchored
kebab-only at any width via `AbstractVSActions.kebabOnly`. `ANCHORED_ASSEMBLY_TYPES` now carries
`vsselectionlist` and `vsselectiontree`; the container is deliberately excluded as its own slice. What
remains is **the container and the calendar**. One decision that slice took and no document here records:
selection list and tree are exempt from the 22px sort-control reserve —
`VSObjectContainer.rightEdgeReserve` returns 0 for both, because a selection's right-edge occupant is the
pending-Apply icon, cleared by `.pending-alert` in `vs-selection.component.scss` instead.

---

## 4 · Type scale — ordered pair, independent of everything above

**4a. Apply the named scale** (`Chart type sizes - ticket.md`). Interior 9/10/11 as
`--inet-font-size-chart-sm`/`-base`/`-title`, chrome 11/13/16. Values are today's, so nothing moves.
**One step first:** measure a rendered tick label to confirm 9pt renders as 9px — derived from Java2D point
units and Batik's 72dpi user space, not yet measured. If it disagrees, keep the roles, substitute the
values. Also closes handoff item 10 (server 9/10/11pt vs browser 13px), which is the same question.
**Check for overlap** with a shell-owned `_viz-tokens.scss` layer that landed on the branch adjacent to
this proposal — not yet reconciled.

**4b. Outlined text conversion** (`Outlined chart text - ticket.md`). Only after 4a: without a named scale,
converting paths to `<text>` lets chart labels inherit the 13px base and silently re-lays out every chart
in the product on upgrade.

---

## 5 · Independent — any time, any order

- **Resize Plot sliders** (chart ticket item 13). Not in the sync diff — still open as originally
  written. Delete the IE11 block **first** — provably dead, Angular 21 cannot run there, roughly half the
  file — then apply `Chart overlay surfaces - decided visuals.html` §03. Keep it a native
  `input[type=range]`: the portal's four div sliders are not keyboard-reachable (`vs-slider` is
  `role="slider"` with `tabindex="-1"`), so matching them would trade a working control for a consistently
  broken one.
- **Chart colour literals** (chart ticket item 10) — mechanical binding.
- **Drill and date-comparison tips** (chart ticket item 14).
- **Nav bar** (spec §02). Stays floating, moves to the lower right, insets from the **plot area** so it
  clears the x-axis and any bottom legend. Not in the sync diff — still open. Two cleanups while it is
  open: the off-scale 5px radius and `z-index:9999`. Open question that changes reach, not the decision:
  does it render for maps only or any zoomable chart?
- **Zoom naming** (`Zoom naming - ticket.md`) — three controls using zoom language for unrelated things.
  Explicitly **not** part of the anchoring pilot.
- **Dead menu icons** (`Dead menu icons - ticket.md`). Not re-verified this sync.

---

## Two decisions this ticket can't make alone

Surfaced by a sibling audit of the wider visualization widget set. Neither blocks anything above; both
are absences worth deciding rather than leaving unstated.

~~**No dark palette on the chart side.**~~ **Half-answered, and the remaining half is narrower than this
says.** Community `3e7e52626` (2026-07-28, "Visualization Phase 9B: org-scoped dark mode") landed dark
values across every **server-rendered** visualization surface, the chart included: `VSChartChromeDefaults`
carries `GRIDLINE_DARK 0x3A383D`, `LABEL_DARK 0xCAC4D0` and `TITLE_DARK 0xE6E0E9` (§08's greys),
`legendBackground()` resolves `LEGEND_BG_DARK 0x252428` (§05), `VSTitleChromeDefaults` carries the dark
title band (§01), and `VSChartPaletteDefaults` covers series colour. So "no dark values at all" was
already false when written, and a dark dashboard does not ship an unconverted chart.

What 9B deliberately scoped out is the browser DOM: its `.viz-dark` block redefines only `--inet-viz-*`
tokens, plus three named exceptions (`vs-slider`, the empty-image placeholder, the table header sort
button). **No shell neutral flips.** Since this spec's own contribution is almost entirely browser-DOM,
that is exactly where the gap sits — four surfaces:

- the anchored strip (§02, §03), a light pill on `--inet-shell-surface-default` over a darkened card
- every tooltip including the CARD ramp (§07), bound to `--inet-text-color` / `--inet-dialog-bg-color`
- chart selection, on `--inet-focus-ring-color` at 28% — a fill calibrated against a light plot
- the nav bar, Resize Plot sliders and annotation selection border

**The decision to take** is therefore whether those four take dark or are explicitly deferred. One gate,
no server or export involvement — a much smaller call than this section assumed.

~~**No alignment-anchor model.**~~ **Answered — `Visualization Widget Spec.dc.html` §09.** The model is
three shipped control-height steps used deliberately: **30px `--inet-control-height-md` in a title lane,
24px `-sm` for the overlaid strip, 44px touch floor**, with the lane itself taking a density row at
20/26/30 (§08 step 3). That is the rule this section says does not exist, and it also corrects the
derivation it complains about — the lane's height comes from the density matrix, not from the strip.

What remains genuinely underived is narrower: the selection family's lane is `titleRatio` of the
assembly rather than a fixed step. Slice 3 shipped without resolving it, by making the kebab the whole
strip at any width — a decision that sidesteps the measurement rather than answering it.

---

## Not ours, but real

**Four forks of one slider design**, ~40KB of SCSS: `vs-slider` (14.5KB), `binding/widget/slider` (10.5KB),
`binding/widget/range-slider` (9.5KB), `vs-range-slider` (4.5KB) — the first two sharing a class vocabulary
almost line for line. None keyboard-reachable. Found while answering a question about the chart's slider.
As of this sync, `vs-slider` is actively being redrawn (a sibling widget spec decided to redraw it in CSS)
— the cheapest moment to fold the others into one consolidation ticket, before there are two moving forks
instead of one.

**Generalising the anchor pattern** beyond charts — `Anchoring beyond charts - discussion.md`. Slices 1–2
(chart, table family) have shipped; the rest is still discussion, not a plan.

---

## Rules that apply throughout

1. **Mark before shell before chart.** The mark changes what a newly created assembly *is*, so anything
   that seeds format has to know about it first. Reversed at either step, the later change has to unpick
   a value the earlier one shipped.
2. **Never scope defensively to the chart.** No `.vs-chart .widget__card-tooltip`. The Hide MiniToolbar
   precedent: the edit lands in shared code, because an override leaves a divergence that becomes
   permanent.
3. **Test one instance per consumer, not one per chart type.** Five chart types tell you nothing new; a
   tooltip on a table and an annotation on a selection list do.
4. **Deletions before bindings**, inside every ticket. Both the slider file and the tooltip rules shrink by
   roughly half before anything is retokenized — retokenizing first means doing it twice.
