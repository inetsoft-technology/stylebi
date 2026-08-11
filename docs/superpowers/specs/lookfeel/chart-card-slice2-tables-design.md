# Chart Card — Slice 2 Design (Anchoring rollout: the table family)

**Date:** 2026-08-10
**Source docs:** `chart-card-design/` (`docs/superpowers/specs/lookfeel/`) — `Anchoring beyond charts - discussion.md`
(required reading, and the source of every settled case cited below), `Open items - handoff.md` §3c,
`Chart Card Spec.html` §02/§03/§06
**Predecessor:** [chart-card-slice1-design.md](./chart-card-slice1-design.md) — slice 1's three PRs are
implemented and committed on `viz-updates` (`43a934add`, `052fe61f1`, `67c486d67`), manual pass complete
**This design verified against:** community `viz-updates` @ `67c486d67`

This design covers the **first family** of the eight-assembly rollout — `VSTable`, `VSCrosstab`,
`VSCalcTable`. It is handoff step **3c**, sliced per family as `Anchoring beyond charts - discussion.md`
§"Suggested sequencing" step 3 asks for.

---

## 0. Why this is the next step

Slice 1 shipped the chart pilot. It also shipped three edits marked `TEMPORARY`, whose named removal
condition is this rollout:

| Site | What it does |
|---|---|
| `abstract-vs-actions.ts:141` — `resident` | Keys the cap, the height bands and the resident kebab on `VSChart` |
| `abstract-vs-actions.ts:393` | Relaxes the pre-existing `!mobileDevice` exclusion on the "menu actions" wrapper, so touch can reach the menu through the kebab |
| `vs-object-container.component.ts:465` — `isToolbarAnchored` | Keys the anchored geometry on `VSChart` |

More pressingly, **one slice-1 edit is already live on all eight types.** The Hide MiniToolbar move at
`abstract-vs-actions.ts:363` is gated on `GuiTool.isVizModern()` **alone**, with no type test — the
comment there claiming the other seven are "unaffected until the rollout" describes the intent, not the
code. Under the gate, every assembly type has already lost the close-X from toolbar slot 0 and gained it
as a menu row. Charts got a resident kebab in exchange; the other seven did not. The rollout is what
closes that asymmetry, and the table family is the largest share of it.

Correct this comment in passing (see [§3](#3-the-edits) row 2 note) rather than leaving a false statement
in shared code.

---

## 1. Scope

### In scope

`VSTable`, `VSCrosstab`, `VSCalcTable` join the anchored set and inherit the chart's treatment
**unchanged**: strip anchored in the title lane, resident kebab at `opacity: 0.55`, action buttons
revealed on hover/focus, cap of `Math.min(3, allowedActionsNum())`, and the 56 / 32px height bands.

### Out of scope — later slices of the same rollout

- **The selection family** (`VSSelectionList`, `VSSelectionTree`, `VSSelectionContainer`) — a materially
  different treatment: kebab-only at any width, no three-action strip, per Case 2. Dropdown variants
  (`VSSelectionBaseModel.dropdown`, `VSCalendarModel.dropdownCalendar`) excluded.
- **`VSCalendar`** — its own slice.
- **`VSRangeSlider`** — excluded from the rollout permanently (Case 4: it is the only one of the eight
  with a title that declares no `titleVisible`, its toolbar is essentially one action, and at ~40px tall a
  24px kebab is over half its height).
- **Deleting the three `TEMPORARY` sites.** They are narrowed here, not removed; the last family slice
  deletes them.

### Out of scope — not this rollout

All card geometry (spec §01/§04/§05, still blocked on the two conflicts recorded in slice 1 §7.1 and
§7.2), the §4 type scale, and the §5 independents.

---

## 2. Decisions this design records

Four were settled in the source docs and are carried, not reopened. Three are new.

### Carried from `Anchoring beyond charts - discussion.md`

1. **Title-hidden assemblies reserve nothing** (Case 1). The strip overlays the content's top-right
   corner on its own surface. The rejected alternative — a condensed ~30px lane — would have added chrome
   to every title-hidden table in every existing dashboard on upgrade, with no author action.
2. **Three is a ceiling, not a target** (Case 2). `Math.min(3, allowedActionsNum())`, which the table
   family inherits as-is.
3. **The container owns the strip, its children do not** (Case 3). `isMiniToolbarVisible()` already
   returns false when `containerType === "VSSelectionContainer"`, and `createToolbarActions()` already
   skips the dismissal for those children. The anchored design **inherits this deliberately** rather than
   rediscovering it — see the verification matrix, where a table nested in a selection container is the
   easiest case to break.
4. **The 24px density transfers; the chart's lane height does not.** Nothing here introduces a 30px lane.

### New to this design

5. **The table triad stays as shipped: max-mode → show-details → export.** No Properties toolbar entry
   for the table family. Table and calc table are *already* stable-first and need no reorder at all; only
   crosstab leads with contextual actions. The chart's triad (show-data, max-mode, Properties) is
   deliberately **not** mirrored: Export is a table's most-used action and would be demoted to the kebab
   on every table to buy a cross-type symmetry no user navigates by. Properties stays reachable by kebab
   and right-click, as today.
6. **The resident kebab stays resident on title-hidden tables**, accepting that it overlays the last
   column header's sort control. See [§4](#4-the-title-hidden-collision).
7. **Row 6 of [§3](#3-the-edits) — the menu-reachability fix — is ungated**, mirroring slice 1's 3a on the
   same reasoning: it adds reachability and removes nothing, and the bug it fixes predates this spec.

---

## 3. The edits

`ANCHORED_ASSEMBLY_TYPES` replaces three separate `Tool.equalsIgnoreCase(objectType, "VSChart")` literals
with one exported constant. Two alternatives were considered and rejected: a capability getter per actions
subclass reads better but `isToolbarAnchored(object)` and `getAnchoredToolbarWidth(object)` take no action
index, so it would force threading `i` through the container and template — churn in the pilot's
freshly-tested code for no behavioural gain; and deriving anchoring from capability (`titleVisible` +
`hasMiniToolbar()`) is elegant but anchors all seven types at once, discarding the per-family sequencing.

| # | File | Change | Gated? |
|---|---|---|---|
| 1 | `vsobjects/objects/mini-toolbar/mini-toolbar.service.ts` | Export `ANCHORED_ASSEMBLY_TYPES` = `VSChart, VSTable, VSCrosstab, VSCalcTable`, sited beside the `hasMiniToolbar()` enumeration it mirrors. The `TEMPORARY` comment moves here — one site that cannot drift, instead of three that can | — |
| 2 | `vsobjects/action/abstract-vs-actions.ts:141` | `resident` reads the constant. `:393`'s mobile relaxation follows for free — it already delegates to `this.resident`. **Also** correct the `:363` comment, which claims the other seven types are unaffected when that branch carries no type test | gate |
| 3 | `vsobjects/objects/vs-object-container.component.ts:465` | `isToolbarAnchored` reads the constant. **No geometry change** — see [§5](#5-geometry-generalizes-unchanged) | gate |
| 4 | `vsobjects/action/crosstab-actions.ts` | Gated reorder, using the chart's proven pattern: lift the action literals to named locals and return one of two arrangements. Gate-off is byte-identical. Gate-on emits `open-max-mode, close-max-mode, show-details, export, drilldown, drillup, multi-select, edit` — the stable set first, so the three surviving slots are the max-mode pair's visible member, show-details and export, and drilldown/drillup overflow to the kebab | gate |
| 5 | `vsobjects/action/table-actions.ts`, `calc-table-actions.ts` | **No change.** Both already emit max-mode → show-details → export | — |
| 6 | all three `*-actions.ts` | Add `show-details`, `export` and the max-mode pair to `createMenuActions`, **appended last**, predicates copied verbatim, labels only | **ungated** |

### Row 4 — why only crosstab

Today's toolbar orders, read from the branch:

```
table      : open-max-mode, close-max-mode, show-details, export, multi-select, edit,
             selection-reset, selection-apply, form-apply
calc-table : open-max-mode, close-max-mode, show-details, export, multi-select, edit
crosstab   : drilldown, drillup, open-max-mode, close-max-mode, show-details, export,
             multi-select, edit
```

The chart's problem — contextual actions seizing the first three slots on selection and reshuffling icons
under the pointer — exists on crosstab and nowhere else in this family.

### Row 6 — the standing bug, and why it is ungated

`open-max-mode`, `close-max-mode`, `show-details` and `export` are **toolbar-only on all three types**, so
right-click reaches none of them. Max mode is the action whose purpose is rescuing an assembly too small
to read, and it is unreachable from the menu on every table in the product today. This is the same gap
slice 1's 3a closed for charts, fixed the same way and for the same reason.

Two constraints carried from 3a: the max-mode pair is **one state-dependent entry, not two rows**; and
menu rows render label-only (`actions-contextmenu.component.html` renders `action.label()` and an optional
child arrow, nothing else), so **no toolbar glyph crosses over**. Appending the group last is what keeps
the existing positional assertions from shifting.

### The chart edit with no analogue here

Slice 1's §02 edit 3 deleted `clear-brush` and `clear-zoom` from the chart toolbar, because the kebab
concatenates toolbar overflow with the menu groups and the menu already carried Clear Brushing and View
All Data under different names. **Verified absent in this family:** across table, crosstab and calc table
every toolbar action is toolbar-only, and no menu entry names the same operation. No deletion is needed —
overflow into the kebab is already a genuine blind append for these three.

---

## 4. The title-hidden collision

Case 1 flagged this as wanting "a look at real density before the table rollout." Located precisely:

- With the title hidden, the anchored strip overlays the **column header row's** top-right corner — not a
  data row, as the discussion doc assumed.
- `.vs-header-cell-button-sort` is `position: absolute; right: 2px; height: 18px; width: 20px`
  (`base-table.scss`) — the same 20px the kebab occupies.
- `.sort-button:not(.col-sorted) { visibility: hidden }` (`vs-table.component.scss:90`) hides the control
  on unsorted columns until `.table-cell:hover` reveals it — but a **sorted** column keeps its indicator
  visible permanently.

So on a title-hidden table whose last column is sorted, a resident kebab sits on a live sort indicator at
rest.

**Decision: stay resident.** The rejected alternative — resident only where a title lane exists,
hover-revealed otherwise — protects the indicator at rest but costs those tables the permanent "there are
controls here" signal and, because touch has no hover, the touch route the rollout exists to provide.
Dropping the strip one row to overlay the first data cell instead was also rejected: it keeps the header
clean but introduces a geometry rule that diverges from the chart, which overlays its plot's top-right
corner directly.

The cost is one 24px glyph at 55% opacity over a sort arrow on a subset of title-hidden tables, recoverable
by hovering. The dense-grid legibility check stays in the manual matrix.

---

## 5. Geometry generalizes unchanged

`paddingTop` / `paddingLeft` / `paddingRight` are declared on `vs-chart-model.ts` only. No table model has
them: `base-table-model.ts` carries `titleFormat` and `titleVisible`, and mounts `vs-title` in normal flow,
where the chart positions its own with explicit `[style.top.px]="model.paddingTop"`.

The container already reads them defensively — `(<VSChartModel> object).paddingTop || 0` at
`vs-object-container.component.ts:475`, `paddingLeft || 0` at `:511`, and both at `:528` in
`getAnchoredToolbarWidth`. A table therefore resolves to a strip flush at the assembly's top-right inside
the content box, spanning the full width, with the pill right-aligning inside it by `margin-left: auto`.

That is the correct answer under slice 1's own rule — *use the lane geometry the type already has; do not
introduce the card spec's 12px inset*, which belongs to the card-geometry plan. **No container edit beyond
row 3.**

---

## 6. Verification

### 6.1 Automated

Existing coverage holds with no edits, which is the point of the byte-identical gate-off path: no table
spec references `viz-modern`, so all 108 index-based assertions across `table-actions.spec.ts` (55),
`crosstab-actions.spec.ts` (36) and `calc-table-actions.spec.ts` (17) stay green, as do all three `.snap`
files. New coverage goes in gated sections.

- `ANCHORED_ASSEMBLY_TYPES` contains the four types and **excludes** `VSCalendar`, the selection family and
  `VSRangeSlider` — the assertion that makes the rollout boundary explicit rather than implied.
- `resident` and `isToolbarAnchored`: true for all three table types under the gate, false gate-off, false
  for `VSCalendar` and `VSSelectionList`.
- Crosstab's gated order asserted by **counting rendered buttons**, never by `allowedActionsNum()`. That
  exact substitution is what hid the slot-vs-button defect through the whole chart pilot until static
  review caught it: `allowedActionsNum()` returns *slots*, and `ToolbarActionsHandler` spends one on the
  overflow control.
- Row 6's menu entries: present, last, label-only, predicates matching their toolbar twins; the max-mode
  pair renders as one state-dependent row.
- Container geometry against a table model carrying no padding fields → flush top/left, full-width box.
- Height bands at 32 / 56 / 52px re-run with a table model; the kebab-ancestor guard test (walks ancestors,
  fails on any contributed `opacity: 0` or `visibility: hidden`) extended to a table host.

Suites: `npm run test:portal`, `npm run test:em`, and the `:tl` variants.

### 6.2 Manual — one instance per family, not one per type

| Check | Catches |
|---|---|
| Table, title visible | the anchored lane, 3 buttons + kebab, right-aligned |
| Table, title hidden, **last column sorted** | [§4](#4-the-title-hidden-collision)'s accepted overlap, in the state that shows it |
| Crosstab mid-drill, selecting cells | row 4's reorder — icons must not reshuffle under the pointer |
| Calc table | the third type is genuinely covered by the shared path |
| **Table nested in a selection container** | the inherited Case 3 exclusion — must show no strip at all |
| ~40-row dense grid | resting-kebab legibility over data |
| Table in max mode | max-mode geometry, which the chart spec only handled for charts |
| Table at ~70px, ~40px, ~24px | the height bands with a table's own lane |
| Touch | kebab only, carrying the full list, at 44px |
| Dark mode (`viz-dark`) | every gated colour on a new host |
| **Gate off** | that nothing moved except row 6's menu entries |

The nested-container case and the sorted-title-hidden case are the two a copy of the chart's matrix would
miss.

---

## 7. Open questions this design does not close

Recorded so the later slices inherit them rather than rediscover them.

- **Does max-mode change the pattern for non-charts?** The chart spec handled max mode for charts only.
  This design puts a table in max mode on the manual matrix, but does not specify behaviour beyond
  "the anchored path applies"; if the matrix shows otherwise, it becomes a real design question for the
  remaining slices.
- **The 56px band is still judgement**, as it was in slice 1 — inherited unvalidated for tables
  specifically, where a short table is a plausible shape.
- **The selection family's lane-width test** must measure the *title's* `titleRatio` share, not the
  assembly width (Case 4's follow-on). Nothing in this slice exercises it; the selection slice must.

---

## 8. Assumptions

- **Community-only.** Every file is under `community/`; no enterprise change is involved.
- **No PR.** Per the current working agreement, `viz-updates` accumulates the rollout and is not proposed
  into `epic-74519` until the feature is complete.
