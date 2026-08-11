# Chart Card — Slice 3 Design (Anchoring rollout: selection list and tree)

**Date:** 2026-08-10
**Source docs:** `chart-card-design/` (enterprise repo root) — `Anchoring beyond charts - discussion.md`
(required reading, and the source of every settled case cited below), `Open items - handoff.md` §3c,
`Chart Card Spec.html` §02/§03/§06
**Predecessors:** [chart-card-slice1-design.md](./chart-card-slice1-design.md) (chart pilot),
[chart-card-slice2-tables-design.md](./chart-card-slice2-tables-design.md) (table family) — both
implemented, committed and pushed on `viz-updates`
**This design verified against:** community `viz-updates` @ `17961df1e`

This design covers the **second family** of the eight-assembly rollout — `VSSelectionList` and
`VSSelectionTree`. It is the second sub-step of handoff step **3c**.

---

## 0. Why this is the next step

Slice 2 put `VSTable`, `VSCrosstab` and `VSCalcTable` into `ANCHORED_ASSEMBLY_TYPES`, which now reads
`vschart, vstable, vscrosstab, vscalctable`. Four of the eight types are anchored; the three
`TEMPORARY` sites that key the rollout boundary are narrowed, not removed.

The selection family is next in `Anchoring beyond charts - discussion.md` §"Suggested sequencing"
step 3, and it is the first family whose treatment is **not** the chart's. Cases 2, 3 and 4 all
single it out. It is also where the ungated Hide MiniToolbar move from slice 1 still leaves an
asymmetry: under `GuiTool.isVizModern()` a selection list has already lost the close-X from toolbar
slot 0 and gained it as a menu row, without getting a resident kebab in exchange.

---

## 1. Scope

### In scope

`VSSelectionList` and `VSSelectionTree` join the anchored set with a **different treatment from the
chart and table families**: the kebab is the entire strip, at any width, with no action buttons ever.
Per Case 2, settled.

Adhoc-filter selection lists are included, with no extra predicate — see [§2](#2-decisions-this-design-records)
decision 8.

### Out of scope — later slices of the same rollout

- **`VSSelectionContainer`** — its own slice. The source doc's sequencing lists it as a separate
  group and it is a different shape: four toolbar actions rather than nine, a `vs-title` lane in
  normal flow rather than a ratio-split header, and it is the one type whose own strip governs
  whether its children get one. Slice 2's out-of-scope note grouped it with the selection family;
  that grouping is superseded here.
- **`VSCalendar`** — its own slice.
- **Dropdown-variant selections** (`VSSelectionBaseModel.dropdown`) — excluded per Case 3, and
  already excluded by `isMiniToolbarVisible()`. See [§5.3](#53-the-two-kebabs-never-co-render).
- **Selection children of a `VSSelectionContainer`** — excluded per Case 3, already excluded by
  `isMiniToolbarVisible()`.
- **`VSRangeSlider`** — excluded from the rollout permanently (Case 4).
- **Deleting the three `TEMPORARY` sites.** Narrowed here, not removed; the last family slice
  deletes them.

### Out of scope — not this rollout

All card geometry (spec §01/§04/§05, still blocked on the two conflicts recorded in slice 1 §7.1 and
§7.2), the §4 type scale, and the §5 independents.

---

## 2. Decisions this design records

Three are carried from the source docs. Five are new.

### Carried from `Anchoring beyond charts - discussion.md`

1. **Kebab-only, no width floor, no three-action strip** (Case 2). Selection list and tree show a
   single kebab in the header, always. The rejected alternative was a width threshold, rejected
   because authors size each selection by its data, so any number would be arbitrary and each list
   would sit permanently on one side of it.
2. **Title-hidden assemblies reserve nothing** (Case 1). The strip overlays the content's top-right
   corner — for a selection list, the first item's right edge.
3. **The container owns the strip, its children do not** (Case 3). Inherited from
   `isMiniToolbarVisible()`, deliberately, not rediscovered.

### New to this design

4. **The kebab is resident**, matching chart and table, not hover-revealed.

   Nothing in `chart-card-design/` resolves this. `README.md:15` records the chart's resting kebab and
   its rationale — *"the first keyboard and touch route to chart actions"* — and `README.md:29` lists
   *"a persistent kebab per assembly is acceptable at dashboard density"* as an open review question.
   Case 1's qualifier and the discussion doc's closing questions worry about a persistent glyph over
   *content*, never about the header lane. Case 2 argues for protecting the title label, but only
   against three action icons; it says nothing about rest state.

   Resident wins on the two arguments that are load-bearing rather than aesthetic: touch has no hover,
   and a control that exists only on hover has no resting focus target. Those matter more here than on
   a chart, because Search, Sort, Reverse, Unselect and Apply are **toolbar-only on both types** — a
   viewer has no route to them at all except chrome that appears on hover.

   Two costs, accepted knowingly:

   - A permanent 32px-wide pill at the right of a 20px lane on every selection list. A filter rail with
     six lists carries six of them. This is Case 2's own concern — *"the label loses, and the label is what
     identifies the filter"* — applied to one control instead of three.
   - **A within-component inconsistency.** `.selection-list__header-buttons`
     (`vs-selection.component.scss:181`) is `visibility: hidden` until `.selection-list:hover`, with
     `force-toolbar` overriding it for keyboard and mobile. So a dropdown or in-container selection
     reveals its kebab on hover while a standalone one shows it at rest. The honest fix is to bring
     that inline kebab up to resident when its own slice lands, not to hold the anchored path down to
     it. Recorded as an open question in [§7](#7-open-questions-this-design-does-not-close).

5. **Kebab-only is expressed as a capability getter on the actions class**, not as a second type set.

   `protected get kebabOnly(): boolean` on `AbstractVSActions`, returning `false`, overridden to
   `true` in `SelectionListActions` and `SelectionTreeActions`. `allowedActionsNum()` returns `0` when
   it is set.

   The rejected alternative was a `KEBAB_ONLY_ASSEMBLY_TYPES` set beside `ANCHORED_ASSEMBLY_TYPES` in
   `mini-toolbar.service.ts`. It is a smaller diff and symmetric with slice 2, but its neighbour is
   annotated `TEMPORARY` with an explicit instruction to delete itself and its readers when the last
   family slice lands. **Kebab-only is permanent — it outlives the rollout.** Siting a permanent rule
   inside a block that says "delete this" is how the permanent rule gets deleted by accident.

   Slice 2 considered and rejected a capability getter for *anchoring*, for a reason that does not
   transfer: `isToolbarAnchored(object)` and `getAnchoredToolbarWidth(object)` live on the container and
   take a model, with no actions instance in hand, so a getter there would have meant threading state
   through the container and template. `allowedActionsNum()` is a method *on* the actions class, so the
   getter is free, and it sits next to the nine actions it describes.

   Also rejected: deriving kebab-only from lane width or action count. Case 2 killed width thresholds
   by name, and an action-count rule would silently recruit types whose slice has not been designed.

6. **The pending-Apply icon moves; the kebab stays flush.** See [§4](#4-the-apply-collision).

7. **One ungated menu entry, not six.** Max Mode joins `createMenuActions` on both types. Search,
   Sort, Reverse, Unselect and Apply do not.

   Slice 2's row 6 was justified as fixing unreachability. That justification does not hold here:
   under kebab-only *every* toolbar action overflows, so the kebab always carries the complete list.
   What remains is right-click parity as a surface — a preference, not a bug — and this edit is
   ungated, so it ships to everyone. Max Mode is the exception on the same grounds slices 1 and 2
   used: its purpose is rescuing an assembly too small to read, and its absence from right-click is a
   standing bug that predates this spec.

   For scale: a viewer right-clicking a standalone selection list today gets essentially just
   **Select All**. Everything else in `createMenuActions` is `composer`-only or container-child-only.

8. **Adhoc-filter selection lists are included**, with no `adhocFilter` test anywhere in this slice.

   > **Superseded by the manual pass — this decision is vacuous.** It is true that no predicate
   > excludes them, but they never reach the anchoring decision: `ComposerAdhocFilterService`
   > builds a viewer adhoc filter by reusing an **existing child of a selection container**
   > (`getFilterFromContainers(...)`, `containers.get(0)`), flipping `DROPDOWN_SHOW_TYPE` to
   > `LIST_SHOW_TYPE` so it displays expanded. Its `containerType` is therefore
   > `VSSelectionContainer`, which `isMiniToolbarVisible()` excludes first — Case 3, not decision 8,
   > governs. The reasoning below (that excluding them "removes nothing, since they already have a
   > floating toolbar") is simply wrong: they have no strip at all. See
   > [§6.4](#64-manual-pass--max-mode-and-adhoc-filters).

   Case 4 gave three reasons for excluding the adhoc range slider. Two do not transfer — an adhoc
   selection list has a real title lane, and nine actions rather than one. Only *"a transient filter
   rather than a component with chrome"* does, and it is outweighed by the fact that excluding it
   removes nothing: `isMiniToolbarVisible()` does not exclude `adhocFilter`, so these already have a
   floating toolbar. Excluding them from anchoring would leave one type with two idioms — the drift
   the rollout exists to end.

   `AdhocFilterService.showFilter()` (`adhoc-filter.service.ts:53`) whitelists `.mini-toolbar` and
   `.mobile-toolbar` in its dismiss-on-outside-click listener. The anchored strip is the same
   component with the same class, so tapping the kebab still does not dismiss the popup. **Verified,
   not assumed** — this is the one thing that could have made inclusion unworkable.

---

## 3. The edits

| # | File | Change | Gated? |
|---|---|---|---|
| 1 | `vsobjects/objects/mini-toolbar/mini-toolbar.service.ts` | Add `vsselectionlist`, `vsselectiontree` to `ANCHORED_ASSEMBLY_TYPES`, with a slice-3 comment beside slice 2's. This is what drives both `resident` and the container's `isToolbarAnchored` | gate |
| 2 | `vsobjects/action/abstract-vs-actions.ts` | Add `protected get kebabOnly(): boolean { return false; }`. In `allowedActionsNum()`, return `0` when it is set, sited **after** the existing `if(!this.resident) { return num; }` guard and beside the `mobileDevice \|\| height < ACTIONS_MIN` early return it joins | gate |
| 3 | `vsobjects/action/selection-list-actions.ts`, `selection-tree-actions.ts` | Override `kebabOnly` to `true` | gate |
| 4 | `vsobjects/action/selection-list-actions.ts`, `selection-tree-actions.ts` | Append the max-mode pair to `createMenuActions` as **one state-dependent entry**, ids and predicates copied verbatim from the toolbar twins, labels only | **ungated** |
| 5 | `vsobjects/objects/selection/vs-selection.component.scss` | Under the gate, offset `.pending-alert` clear of the pill. See [§4](#4-the-apply-collision) | gate |
| 6 | `vsobjects/objects/vs-object-container.component.ts` | `rightEdgeReserve()` returns `0` for selection list and tree. See [§5.2](#52-the-right-edge-reserve-becomes-per-family) | gate |

**No change** to `getToolbarTop`, `getToolbarLeft`, `getAnchoredToolbarWidth`, the height-band
constants, `mini-toolbar.component.html`, or `mini-toolbar.component.scss`.

> **Superseded — all of these except `getAnchoredToolbarWidth` and the height-band constants did
> change**, in the max-mode fix wave recorded in [§6.4](#64-manual-pass--max-mode-and-adhoc-filters).
> `getToolbarLeft` gained a max-mode override for selections; `isToolbarAnchored` gained a max-mode
> exclusion and was split against a new `isKebabResident()`; and `mini-toolbar.component.{ts,html,scss}`
> gained a `residentKebab` input plus a `kebabResident` getter. That is **a new public input on a
> component every assembly type mounts**, which this section's "no change" claim would have hidden from
> a reviewer reading the design alone.

### Row 2 — why the guard placement is load-bearing

The early return must sit **after** `if(!this.resident) { return num; }`, so the effective condition is
`resident && kebabOnly`. `kebabOnly` alone would return `0` gate-off as well, which would strip a
selection list's floating toolbar down to a bare overflow kebab for users who never opted into the
gate. Reusing the existing guard rather than restating `this.resident && this.kebabOnly` also keeps one
place that decides what "under the gate" means.

### Why row 2 needs no new rendering work

`allowedActionsNum() === 0` is an existing, shipped path — it is what touch and the 32–56px height
band already produce. Everything downstream is already built for it:

- `showingActions` appends the kebab **outside** the budget, so zero slots still leaves the kebab. It
  is removed only below `ACTION_FLOOR`.
- `mini-toolbar.component.scss` already carries a rule for this state, and names it: the divider
  suppression is scoped *"only where a group really does precede the kebab (on touch, and in the
  kebab-only height band, none renders)."*
- `getMoreActions()` at `abstract-vs-actions.ts:257` already branches on
  `this.resident && this.allowedActionsNum() === 0` into `flattenedMoreActions()`, which merges the
  overflowed toolbar actions with the menu into one flat panel, drops the `"menu actions"` wrapper and
  dedupes by `id()`. Every selection list under this slice takes that branch permanently.

`allowedActionsNum()` has exactly three callers inside the actions class — `showingActions`,
`getMoreActions()` and the `flattenedMoreActions()` branch. `ViewerMobileToolbarComponent` and
`ViewerAppComponent` declare their own same-named methods; neither reads this one. So forcing `0`
reaches only the three intended sites.

### Row 4 — the dedup constraint

`flattenedMoreActions()` dedupes by `action.id()`, on the stated basis that menu entries are copied
verbatim from their toolbar twins so *"an id is visible in both places or neither."* Because every
selection list takes the flattened branch, row 4's entry **must** reuse
`selection-list open-max-mode` / `selection-list close-max-mode` (and the tree's equivalents)
exactly. A new id would make the kebab show Maximize twice, in adjacent groups.

Two constraints carried from slices 1 and 2: the max-mode pair is one state-dependent entry, not two
rows; and menu rows render label-only, so no toolbar glyph crosses over.

### What the kebab contains

Both types emit nine toolbar actions:

```
open-max-mode, close-max-mode, search, sort, sort-asc, sort-desc, reverse, unselect, apply
```

These collapse to **six** rows, because two are state machines rather than sets. `search` flips its
own label (`Show Search` / `Hide Search`). `sort` / `sort-asc` / `sort-desc` are mutually exclusive on
`sortType` (`8` / `1` / `2`) and label the *next* state (`Sort Ascending` / `Sort Descending` /
`Sort Hide Others`) — one three-state cycle, the same shape as the max-mode pair. Only one of the
three is ever visible.

So the resting kebab opens: Max Mode, Search, Sort, Reverse, Unselect, Apply, then the menu group —
flattened into one panel, no `More` submenu.

---

## 4. The Apply collision

This is the one issue in this slice that is functional rather than cosmetic.

`.pending-alert` (`vs-selection.component.scss:205`) is `position: absolute; top: 2px; right: 5px;
width: 20px`, mounted on `.vs-object` as a sibling of `.selection-list`. It renders whenever
`pendingSubmit` — `controller.unappliedSelections.length > 0` (`vs-selection.component.ts:1025`) — and
it is **clickable**: it calls `controller.applySelections()`.

The anchored pill lands on top of it. Measured:

| | Offset from the assembly's right edge |
|---|---|
| Anchored pill, title visible | `0` → `32` (1px border + 30px `--inet-control-height-md` button + 1px border, at `height: 24px`) |
| `.pending-alert` | `5` → `25` |

Total containment. Today the floating toolbar overlaps the same corner, but only on hover, so the icon
is clear at rest; a resident kebab makes the overlap permanent. Unlike slice 2's sort-arrow overlap —
a passive indicator — **both controls here are live, and one of them is Apply.**

**Decision: move the icon, not the kebab.** Under the gate, offset `.pending-alert` to sit left of the
pill: `right: calc(var(--inet-control-height-md) + 6px)` — that is `30px` button + `2px` of pill border
+ a `4px` gap = 36px, expressed against the token the pill's button width actually comes from rather
than restated as a `36px` literal that would drift if the token moved.

Three alternatives were considered:

- **Always reserve 22px at the right edge** (reusing slice 2's `rightEdgeReserve` verbatim). Rejected:
  it leaves a permanent empty gap on every selection list, including the majority that have
  `submitOnChange` on and can never show the icon, and it stops the kebab aligning with the table or
  chart beside it.
- **Reserve only while pending.** Rejected: the kebab would slide 22px left the instant the user
  clicks a value — the reshuffle-under-the-pointer failure slice 2's crosstab reorder was taken to
  prevent, fired here by the user's own click.
- **Keep Apply as a toolbar button.** Visually identical to the chosen option, but it reopens Case 2's
  settled *"kebab only, no action icons, at any width"*, and it duplicates a control the component
  already draws, so `.pending-alert` would then need suppressing under the gate to avoid two Applies.

**Scope note:** the collision requires `submitOnChange` off, which is not the default. The
`.pending-alert` variants confirm the narrowness — `.left` applies when
`dropdown || maxMode || inContainer`, and all three of those are either excluded from this slice or
move the icon away from the corner. So the gated offset is needed only for the standalone,
non-max-mode case, in both the title-visible and title-hidden (`.top`) states.

---

## 5. Geometry

### 5.1 The lane generalizes; `titleRatio` does not bind

No selection model declares `paddingTop` / `paddingLeft` / `paddingRight` — those are on
`vs-chart-model.ts` only. The container already reads them defensively
(`(<VSChartModel> object).paddingTop || 0`), so a selection list resolves to a strip flush at the
assembly's top-right inside the content box, spanning the full width, with the pill right-aligning by
`margin-left: auto`. Identical to the table family. **No positioning edit.**

Slice 2 §7 carried forward an open item: *"The selection family's lane-width test must measure the
title's `titleRatio` share, not the assembly width (Case 4's follow-on)."*

**That item is closed, and the answer is that it does not apply.** The ratio-split lane at
`vs-selection.component.html:100` is bound to `model.container && listSelectedString` — it renders
only inside a selection container, which Case 3 excludes from this slice. A standalone selection list
gets a full-width lane, exactly like a table. The split lane becomes live in the
`VSSelectionContainer` slice, against the container's children, which that slice must handle.

The lane is 20px tall (`AssetUtil.defh`, via `TitleInfo`) and the pill is 24px tall, so it overhangs by
4px vertically.
Not new: chart and table titles default to the same 20px through the same `TitleInfo`, so slice 2
already ships a 24px pill over a 20px lane. Carried, not reopened.

### 5.2 The right-edge reserve becomes per-family

`rightEdgeReserve()` returns `SORT_CONTROL_RESERVE = 22` when `titleVisible === false`. Slice 2 keyed
it on the absence of a title *"rather than on a second assembly-type list"*, to keep one rule for the
whole anchored set, accepting that a chart pays 22px of empty plot corner for a control it does not
have.

That reserve exists for one specific thing: the table's `.vs-header-cell-button-sort` at
`right: 2px; width: 20px`. A selection cell has no such control. If selections inherited the reserve,
the title-hidden pill would sit at offset `22 → 54` while the gated `.pending-alert` sits at
`5 → 25`, overlapping by 3px — and clearing *that* would need a second, larger offset for the
title-hidden state, making the icon's position depend on a TypeScript constant from a different file.

**Decision: `rightEdgeReserve()` returns `0` for selection list and tree.** The pill is then flush in
both states, and row 5 needs exactly one rule instead of two. This makes the method per-family, which
slice 2 avoided — but slice 2 avoided it to keep one rule for a set that was homogeneous, and this
slice is the point at which the set stops being homogeneous.

The chart's 22px of empty corner is **left as it is.** It is already-accepted cost on shipped code and
changing it is not this slice's business. Recorded in [§7](#7-open-questions-this-design-does-not-close).

### 5.3 The two kebabs never co-render

`vs-selection.component.html:69` mounts `<mini-menu [actions]="actions?.toolbarActions">` inside
`.selection-list__header-buttons`, at `right: 1px`, hover-revealed. It is guarded by
`model.dropdown || model.containerType == 'VSSelectionContainer'`.

`isMiniToolbarVisible()` returns false for **both** of those conditions. So the inline kebab and the
anchored strip are mutually exclusive by construction, and no selection list ever shows two. This is
worth an assertion rather than a comment — it is the failure that a reader of either file alone would
not predict.

### 5.4 The height bands mostly stop binding

With `kebabOnly` forcing `0`, `ACTIONS_MIN` (56px) becomes unreachable for these two types: it exists
to decide when action buttons stop fitting, and no action button ever renders. `ACTION_FLOOR` (32px)
still applies and still removes the kebab. `MAX_TOOLBAR_ACTIONS` and the `realActions` budget
arithmetic are likewise bypassed.

This is a simplification, not a gap — but it means the 56px band, already flagged in slice 1 as *"the
likeliest number in this spec to be wrong"*, gets no new validation from this slice.

---

## 6. Verification

### 6.1 Automated

No selection spec references `viz-modern` or `isVizModern`, so gate-off is byte-identical and existing
coverage holds unedited: `selection-list-actions.spec.ts` (8 tests, 21 assertions),
`selection-tree-actions.spec.ts` (5 tests, 18 assertions), and both `.snap` files. New coverage goes
in gated sections.

- `ANCHORED_ASSEMBLY_TYPES` now contains six types and still **excludes** `VSSelectionContainer`,
  `VSCalendar` and `VSRangeSlider` — the assertion that keeps the rollout boundary explicit. Update
  slice 2's exclusion test rather than adding a second one; leaving it asserting that
  `VSSelectionList` is excluded would fail, and that failure is the intended signal.
- `resident` and `isToolbarAnchored`: true for both types under the gate, false gate-off.
- `kebabOnly`: true on both selection actions classes, **false on chart, table, crosstab and calc
  table** — the assertion that stops a future slice widening it by accident.
- `allowedActionsNum()` returns `0` under the gate at a width and height where a chart or table would
  return more than one, and returns the unchanged width-derived number gate-off.
- **Rendered-button count, not `allowedActionsNum()`.** Exactly one control renders under the gate at
  any width — 150px, 400px, 800px. Slice 2 records why the substitution matters: `allowedActionsNum()`
  returns *slots*, and `ToolbarActionsHandler` spends one on the overflow control. That exact
  substitution hid the slot-vs-button defect through the whole chart pilot.
- `getMoreActions()` takes the `flattenedMoreActions()` branch, and the panel contains **one** Maximize
  row — the regression row 4's shared ids exist to prevent.
- Row 4's entry: present, last, label-only, one state-dependent row, predicates matching the toolbar
  twins, and **present gate-off too** (it is the one ungated edit).
- Container geometry against a selection model carrying no padding fields → flush top/left, full-width
  box, `rightEdgeReserve() === 0` in both title states, and still `22` for a title-hidden table.
- The two kebabs never co-render: with `dropdown` true, and with
  `containerType === 'VSSelectionContainer'`, `isMiniToolbarVisible()` is false while the inline
  `mini-menu` renders; with neither, the inverse.
- The kebab-ancestor guard test from slice 1 (walks ancestors, fails on any contributed
  `opacity: 0` / `visibility: hidden`) extended to a selection host — `.selection-list__header-buttons`
  puts a `visibility: hidden` rule in this component's own stylesheet, which is exactly the shape that
  test exists to catch.

Suites: `npm run test:portal`, `npm run test:em`, and the `:tl` variants. Scope every `*.tl.spec.ts`
run with `--include`.

### 6.2 Manual

| Check | Catches |
|---|---|
| Selection list, title visible, ~150px wide | the kebab-only strip: one control, right-aligned, resident and dim |
| Selection tree | the second type is genuinely covered by the shared path |
| **`submitOnChange` off, select a value** | [§4](#4-the-apply-collision) — the pending icon and the kebab must both be fully clickable, and the kebab must not move |
| Same, title hidden | the `.top` variant with `rightEdgeReserve() === 0` |
| Adhoc-filter selection list | decision 8 — tapping the kebab must **not** dismiss the popup |
| Dropdown selection list | the inline hover-revealed kebab, unchanged, and no second strip |
| **Selection list inside a selection container** | the inherited Case 3 exclusion — no anchored strip at all |
| Filter rail of 5–6 lists | decision 4's accepted cost, at the density that shows it |
| Long title that truncates | whether 32px of permanent pill costs the label its identity |
| Search open | the search row is a sibling in normal flow, not an overlay — confirm it does not reach the lane |
| List with a vertical scrollbar | whether the flush pill lands on the scrollbar (untested for tables too) |
| Max mode | `.pending-alert.left` moves the icon; the offset rule must not fight it |
| Selection list at ~40px, ~24px tall | `ACTION_FLOOR` with a selection's own lane |
| Touch | kebab only at 44px, carrying the full flattened list |
| Dark mode (`viz-dark`) | every gated colour on a new host |
| **Gate off** | that nothing moved except row 4's menu entry |

The adhoc-filter dismissal, the container-nested case and the `submitOnChange` case are the three a
copy of slice 2's matrix would miss.

### 6.3 Manual pass — 2026-08-10, row 1 failed and found two bugs

**Automated status:** `npm run test:portal` 215/215 files, 1284/1284 tests; `npm run test:em` 101/101,
356/356. `npm run lint` **could not run at all** — installed eslint is 8.57.1 against a `package.json`
requiring `^10.6.0`, and `@angular-eslint/builder` passes a `stats` option ESLint 8 rejects, aborting
before any file is linted. Pre-existing and branch-wide; this slice therefore has **no lint evidence**.

**Row 1 (`submitOnChange` off, select a value) — the row [§4](#4-the-apply-collision) called the only
functional risk in the slice — failed, in two independent ways.**

**Bug 1: the pill overlapped the Apply icon.** [§4](#4-the-apply-collision)'s measurement was wrong.
It read the pill as 32px from `min-width: var(--inet-control-height-md)` (30px) plus 2px of border —
but that `min-width` is a **floor the content exceeds**, so it never sizes the pill. The real pill is
~40px: an 18px glyph (`.icon-size-small`, `font-size: 18px !important`) + 4px of `i` padding + 16px of
`.btn-sm` horizontal padding (Bootstrap default; no override in this repo) + 2px border. The 36px
offset landed ~4px inside the pill, which also ate clicks there — that strip is
`.mini-toolbar-button-group { pointer-events: all }` at `z-index: 9920`.

Fixed: `right: 48px` (40px pill + 8px gap). The `calc(var(--inet-control-height-md) + …)` form was
removed deliberately, not merely re-tuned — it encoded the false premise that the token sizes the
pill, which is the mistake itself.

**Bug 2: with the title hidden, Apply was not clickable at all — pre-existing, unrelated to this
slice.** `.selection-list-body` declares `position: relative; z-index: 99`; `.pending-alert` declared
no z-index. Their common ancestor `.selection-list` is `position: absolute` with `z-index: auto`, so it
creates no stacking context, and the body's 99 competes directly against the icon's `auto` inside
`.vs-object`'s context. The body always wins; DOM order, which favours the icon, only breaks ties
between equal z-indexes.

Title-visible, the icon overlays the header, which the body starts below — nothing covers it.
Title-hidden, no header renders, the body starts at the top, and the icon at `top: -1px` falls inside
it. The glyph still paints (the body is transparent), so the control looks live and is not: devtools
pointer-select on it returns `.selection-list-cell-label`, and the native `title` tooltip never fires.

Fixed: `z-index: 100` on `.pending-alert` — above the body's 99, below `.quick-switch-overlay-btn`'s
200. **Ungated, in the base rule.** This is the slice's *second* ungated change, which contradicts
[§2](#2-decisions-this-design-records) decision 7's "one ungated menu entry" framing; recorded here
rather than absorbed silently. It is justified on the same grounds: it fixes a real defect, removes
nothing, and predates the gate.

**Verification gap, stated plainly.** Neither fix has an automated guard. A computed-`z-index` test was
attempted and abandoned on evidence: `createSelectionComponent` in the selection component's
testing-library spec never renders real DOM (`nativeElement instanceof Element` is `false`), so
`getComputedStyle` yields nothing and any such assertion would pass for the wrong reason. Both fixes
are **manual-verification-only**, and both were derived by the same arithmetic-from-stylesheets method
that produced bug 1 — so row 1 must be re-walked in a browser before this slice is considered done.

### 6.4 Manual pass — max mode and adhoc filters

**Max mode: [§7](#7-open-questions-this-design-does-not-close)'s open question is answered, and the
answer was a regression.** The anchored strip rendered ~30px below the title lane.

`VSSelection` abandons `objectFormat` positioning in max mode: `get topPosition()` guards its whole
viewer branch on `!this.model.maxMode`, the remaining branches are composer-only, so it falls through
to `return null`, and `[style.left.px]` resolves to `null` the same way. The assembly renders at its
**static** position while the anchored `getToolbarTop()` places the strip at `objectFormat.top`.

The information needed to avoid this was already in the file being edited. The **floating** path
compensates — `getToolbarLeft()`'s non-anchored branch passes `maxMode` into
`miniToolbarService.getToolbarLeft()`, and its comment names this assembly explicitly: *"some max mode
assembly has not start from 0. e.g. selection list."* The anchored branch returns before that call,
under a comment asserting *"an anchored strip is inside the assembly, so there are no viewport bounds
to clamp against"* — max mode is exactly where that premise fails. The chart pilot could not have
caught it: charts do not null their own positioning in max mode.

**Fixed, over four passes — the count matters, because each pass exposed the next:**

1. `isToolbarAnchored()` excludes max mode, so it uses the floating path that already compensates.
   **First written blanket (`&& !maxMode`), which silently un-anchored maximised charts and tables —
   shipped, tested behaviour — and a fix pass then rewrote slice 1's passing chart test to assert the
   regression.** Corrected to `&& !isMaxModeSelection(object)`: only `VSSelectionList` /
   `VSSelectionTree` have padding constants in `objectFormat`, because `setMaxModeLayout()` exists
   only on `VSSelectionBaseModel` and `VSSelectionContainerModel`. Chart and table `objectFormat` is
   rewritten to true coordinates, so anchoring was always correct for them. Slice 1's test was
   restored verbatim from git, and a maximised-table test now guards that half too.
2. `[class.left]` dropped `maxMode` (above).
3. A `getToolbarLeft` override for max-mode selections, aiming past the visible right edge so the
   overflow clamp right-aligns the strip. A companion `getToolbarTop` override was **added and then
   removed**: its justification — that floating from the padding constant "put the strip on the header
   row" — was false. `MiniToolbar.topY` already lifts a floating strip by its own height when
   `top > 20`, so it was landing inside the reserved lane without help.
4. `residentKebab` / `isKebabResident()`, splitting "carries the kebab-only design" (type + gate) from
   "is geometrically anchored right now". Pass 1 had stranded **touch** users in max mode with no
   control at all: the non-anchored branch is `!mobileDevice`, so "hover-revealed" means *absent* where
   there is no hover. The accepted trade-off below did not cover touch, and should have.

`resident` and `kebabOnly` stay keyed on assembly type, so the strip remains kebab-only in max mode.
**Accepted cost:** on **desktop**, max mode alone shows a hover-revealed rather than resident kebab.
The two alternatives were rejected — computing a max-mode origin would mean measuring the DOM or
replicating a fall-through the container cannot see, and reverting max mode to the full nine-action
strip would break Case 2's "kebab-only at any width".

**Known fragility, accepted:** the `getToolbarLeft` override right-aligns by leaning on a
viewport-safety clamp, which has no contract to right-align. It works because
`.mini-toolbar-container` is `width: fit-content !important`, so the strip's true width is not
knowable at that point. Worth a follow-up rather than a rewrite here.

**Apply icon on the title in max mode — pre-existing, fixed here.** `[class.left]` applied in max mode,
setting `left: 1px` while the base rule kept `right: 5px` *and* `width: 20px`. All three non-auto is
over-constrained, so LTR discards `right` and the icon landed on the title's first characters
("Category" rendered as "egory"). `.left` exists because the right side is occupied on dropdowns
(collapse toggle) and container children (selected-value text); neither holds in max mode. **Fixed** by
dropping `maxMode` from the binding, which also lets the gated offset apply there.

**Scope drift, recorded rather than absorbed.** The slice was designed with one ungated edit
([§2](#2-decisions-this-design-records) decision 7). It ships **four**: the Max Mode menu entry, the
`.pending-alert` stacking fix ([§6.3](#63-manual-pass--2026-08-10-row-1-failed-and-found-two-bugs)),
this `.left` change, and the `getToolbarLeft` max-mode override above — that last one is consulted
outside the gate branch, so it changes gate-off positioning too. Each fixes a real defect and removes
nothing, and three of the four are bugs older than this work, surfaced only because anchoring put a
resident control in a corner nobody had looked at closely.

The count was wrong twice in this document before a review caught it. That is the tell: reactive fixes
do not announce themselves as scope, so the number has to be recounted rather than remembered.

**The adhoc range slider is unimplemented, not broken.** It still shows its pre-existing floating
toolbar (horizontal-dots overflow plus an eraser) from `hasMiniToolbar()`'s
`VSRangeSlider && adhocFilter` clause. It is absent from `ANCHORED_ASSEMBLY_TYPES`, so this slice does
not touch it. Case 4's stated decision goes further than [§1](#1-scope) implemented — *"the range
slider shows nothing — no strip, no kebab"* would require **removing** chrome users have today, which
§1 scoped out as "excluded from the rollout permanently". That gap needs its own decision.

**Rows not yet walked:** everything in [§6.2](#62-manual) except row 1 and max mode. Also checked and dismissed
while investigating bug 2: `vs-calendar.component.scss` has a `.pending-alert` of similar shape but no
`z-index`-raised body — its `z-index: 99` is on `.row-resize-label` at `left: 50%; bottom: 0` — so the
calendar does **not** share bug 2.

---

## 7. Open questions this design does not close

- **The inline kebab's rest state.** Decision 4 leaves dropdown and in-container selections
  hover-revealed while standalone ones are resident. The fix belongs to the `VSSelectionContainer` and
  dropdown work, not here. Until then, one component has two rest states.
- **The chart's 22px title-hidden corner.** [§5.2](#52-the-right-edge-reserve-becomes-per-family) makes
  `rightEdgeReserve()` per-family and deliberately leaves the chart paying a reserve for a sort control
  it does not have. Cheap to fix; not this slice's call.
- **The ~56px width floor.** Case 2 says *"Below ~56px of width even the kebab plus a truncated label
  stops making sense; drop chrome entirely there, as in the chart's bottom row."* Not implemented:
  `ACTION_FLOOR` is height-only, and adding a width floor to `showingActions` would change shipped
  chart and table behaviour. It belongs to the last slice, where the whole anchored set can move at
  once.
- **`ACTIONS_MIN` (56px) still unvalidated**, and this slice cannot validate it —
  [§5.4](#54-the-height-bands-mostly-stop-binding).
- ~~**Does max mode change the pattern for non-charts?**~~ **Answered: yes, and it cost four fixes.**
  See [§6.4](#64-manual-pass--max-mode-and-adhoc-filters). The selection family abandons `objectFormat`
  positioning in max mode, so the anchored geometry has no lane origin to work from; charts and tables
  do not, so anchoring is correct for them. The remaining piece is the **container**:
  `VSSelectionContainerModel` uses the same `setMaxModeLayout()` padding-constant layout and is not
  matched by `isMaxModeSelection()`, so its slice will hit this identically — extend that predicate
  rather than re-deriving the cause.
- **Does the flush pill land on a vertical scrollbar?** On the matrix for selections; unexamined for
  the table family too.

---

## 8. Assumptions

- **Community-only.** Every file is under `community/`; no enterprise change is involved.
- **No PR.** Per the current working agreement, `viz-updates` accumulates the rollout and is not
  proposed into `epic-74519` until the feature is complete.
- **Selection tree mirrors selection list.** Both emit the same nine toolbar actions in the same order
  and share `vs-selection.component.*`. Every edit applies to both; nothing in this design treats them
  differently.
