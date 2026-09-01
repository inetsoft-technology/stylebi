# Anchored Rollout Slices 4 and 5 — Design

**Date:** 2026-09-01
**Verified against:** community `viz-updates` @ `02e9fdbed`. The commit originally cited here, `f96191e8c`, was amended to this hash by a parallel session shortly after this design was written and is no longer reachable; every file and line citation below was checked against `02e9fdbed` instead. This design's own implementation then landed on top of it.
**Follows:** slices 1–3 — `5c7e106f2` (chart), `39239784b` (table family), `65e249e91` (selection list and tree) — and `408ea004c`, L″, which replaced the density-keyed strip test with a lane measurement.
**Roadmap entry:** "Unblocked by `380705bc1`" in [chart-card-roadmap.md](./chart-card-roadmap.md) — rollout slices 4 and 5, the container and the calendar.
**Source:** [Anchoring beyond charts - discussion.md](./chart-card-design3/Anchoring%20beyond%20charts%20-%20discussion.md), Cases 2, 3 and 4.

---

## Scope

**In: the selection container and the calendar.** The last two types in `MiniToolbarService.hasMiniToolbar()` that do not yet anchor their strip into their title lane, and with them the retargeting of `ANCHORED_ASSEMBLY_TYPES` from a rollout stage into the permanent anchored set.

**Out: §10.1, resting visibility by pointer capability.** The roadmap's "Ready now" table pairs it with these slices and `github.md`'s 2026-08-12 entry records why it cannot ride along: the `(hover: hover) and (pointer: fine)` query would leave every kebab-only family — list, tree, and after this design the container — with no chrome at all at rest on a desktop, which is *"not what `kebabOnly` was approved for."* That is an unresolved product question, not a sequencing one, and folding it into a rollout slice is how the rollout stalls. It gets its own design.

**Out: the adhoc range slider.** Case 4 settled that it draws no chrome. This design does not deliver that — see section 3.

**Out: the mobile relaxation of the "menu actions" wrapper.** `ANCHORED_ASSEMBLY_TYPES`'s comment claims the last slice deletes it alongside the predicate. It is orthogonal — see section 3.

---

## 1. Slice 4 — the selection container

Two edits.

- `ANCHORED_ASSEMBLY_TYPES` gains `"vsselectioncontainer"`.
- `SelectionContainerActions` overrides `kebabOnly => true`, mirroring `selection-list-actions.ts:44` and `selection-tree-actions.ts:44`.

**Kebab-only, per Case 2**, which names the container explicitly: *"selection list, selection tree and selection container show a single kebab in the header, always."* Case 4's later note reopened the question by saying the fit test *"returns for the container, whose slice has not been written"* — that note is about geometry, not treatment, and the geometry answers itself here: the container's lane is full width (`vs-selection-container.component.html:42` binds `[titleWidth]="model.objectFormat.width"`), not the `titleRatio` split the list and tree take, so nothing in it fails a fit test. The treatment decision stands on its own grounds: a container and its child lists sit adjacent in one visual stack, and a denser header on the parent than on its children reads as an inconsistency rather than as a hierarchy. Its four toolbar actions — `open-max-mode`, `close-max-mode`, `unselect-all`, `addfilter` (`selection-container-actions.ts:62-91`) — are set-and-leave rather than study-and-iterate, which is the distinction Case 2 draws between a filter and a chart.

**Nothing else is needed, because four preconditions already hold.** Each was checked rather than assumed:

| Precondition | Where it is already satisfied |
|---|---|
| A lane tall enough to hold the 24px strip | `VSCompositeModel:44` (Java) sizes `titleFormat` from `assemblyInfo.getTitleHeight()`, and `CurrentSelectionVSAssemblyInfo:244-245` routes that through `VSDensityDefaults.titleHeight`. At the `compact` default shipped in `c7790bbf0` a marked container resolves **26px**, over `ANCHORED_LANE_MIN` |
| Children must not each draw their own strip | `isMiniToolbarVisible` returns false for `containerType === "VSSelectionContainer"` (`vs-object-container.component.ts:277`). Case 3's double-strip problem is inherited, not re-solved |
| Max mode must not break the lane origin | `VSSelectionContainerModel.setMaxModeLayout:116-124` sets `objectFormat` left and top to **0** — true coordinates, as chart and table do. The container therefore needs **no** entry in `isMaxModeSelection` (`vs-object-container.component.ts:506`), whose exclusion exists only because `VSSelectionBaseModel` puts `TOP_PADDING`/`LEFT_PADDING` constants there instead |
| A full-width lane for the pill to right-align into | `getAnchoredToolbarWidth` subtracts `paddingLeft`/`paddingRight`, both absent on this model, so the box is the whole lane and `margin-left: auto` does the alignment |

**The `<mini-menu>` inside `current-selection.component.html:26` is not this strip and is not touched.** It belongs to an *outer selection* row — one child filter's collapsed title bar inside the container's body — and binds `CurrentSelectionActions.toolbarActions` (`current-selection.component.ts:115-116`), a different action class. The container's own title lane, `vs-selection-container.component.html:37-55`, holds only `<vs-title>` and is where the anchored strip lands.

---

## 2. Slice 5 — the calendar

One edit: `ANCHORED_ASSEMBLY_TYPES` gains `"vscalendar"`. No `kebabOnly` override — the calendar takes the table treatment, `Math.min(3, allowedActionsNum())` action buttons plus the kebab, which is what the roadmap predicted (*"the calendar is expected to take the table treatment unmodified"*) and what its action profile wants: six toolbar actions — `toggle-year`, `toggle-double-calendar`, `clear`, `toggle-range-comparison`, `multi-select`, `apply` (`calendar-actions.ts:64-117`) — is the largest set in the rollout, so a strip that surfaces three of them saves more than it costs.

**The dropdown variant needs no special case, and this is worth stating because it looks like it should.** `vs-calendar.component.html:41-59` renders an inline `<mini-menu>` for `model.dropdownCalendar`, which would be a second strip in the same corner. It cannot collide: `isMiniToolbarVisible` already returns false when `dropdownCalendar` is set (`vs-object-container.component.ts:278`), so the two are mutually exclusive by a guard that predates this work. The non-dropdown calendar's lane is `.calendar-title` (`:76-99`), a plain full-width div at `model.titleFormat.height` with nothing but the title in it.

**Its lane clears the threshold at both heights.** The calendar is the one type whose pre-density lane is not `AssetUtil.defh` — `CalendarVSAssemblyInfo:95` returns 36, and `TitledVSAssemblyInfo:108` documents the exception. Marked, `getTitleHeight()` (`CalendarVSAssemblyInfo:634-635`) resolves the density row to 26. Unmarked or author-set, it stays 36. Both are over 24, so the calendar is the one type that would have anchored correctly even before L′.

---

## 3. The boundary — retargeted, not deleted

`ANCHORED_ASSEMBLY_TYPES` stops being a rollout stage and becomes the permanent anchored set: the `hasMiniToolbar()` enumeration **minus `vsrangeslider`**. The four `TEMPORARY` markers — `mini-toolbar.service.ts:33`, `:35`, `:87`, `abstract-vs-actions.ts:139`, `vs-object-container.component.ts:463` — are rewritten to say what the predicates now are, not deleted.

**The stated end-state is unsafe as written, and this is the load-bearing finding of this design.** `ANCHORED_ASSEMBLY_TYPES`'s comment says the last slice deletes the predicate *"leaving the `.viz-modern` gate as the only condition."* Do that and `isAnchoredChromeSuppressed` — `vizModern && laneHeight < ANCHORED_LANE_MIN` with the type test gone — becomes true for **every laneless assembly in a modern org**. `anchoredLaneHeight` returns 0 for any model without `titleVisible`, so text, gauge, image, spinner, line, oval and rectangle all qualify, and `showingActions` empties their action list. That reaches a real surface: the composer's mobile toolbar renders `actions?.showingActions` for whatever assembly is focused (`mobile-toolbar.component.html:19`, fed by `AbstractActionComponent:31,44` which builds a real `AbstractVSActions` for any type). It is exactly the failure `isAnchoredChromeSuppressed`'s own doc-comment warns about — *"that would be true for every non-anchored type and gate-off, stripping toolbars users have today"* — arriving by the other route. **The type test is not scaffolding; it is the thing that keeps suppression scoped to types that have a lane.**

Two further claims in those comments are corrected rather than carried:

- **The mobile relaxation does not ride along.** `mini-toolbar.service.ts:35` says the last slice deletes the predicate *"together with `AbstractVSActions.resident`, its `TEMPORARY` mobile relaxation of the 'menu actions' wrapper."* That relaxation is the `!this.mobileDevice` term on the wrapper's `visible()` (`abstract-vs-actions.ts:487`); it applies to every assembly type on every mobile device and has no dependence on the rollout set. Out of scope, and the comment stops claiming otherwise.
- **The dismissal comment narrows to one type.** `abstract-vs-actions.ts:455-464` explains that the hide-strip action moves to the menu under the gate, and that *"types that have not yet joined the rollout therefore reach the dismissal by right-click until their slice lands and gives them a resident kebab."* After this design the only such type is the adhoc range slider.

**`isKebabResident`, `isToolbarAnchored` and `resident` all survive.** They stop being scaffolding and become the permanent condition. `isToolbarAnchored`'s `isMaxModeSelection` exclusion stays exactly as it is — it is a list/tree quirk, and neither new type joins it.

**The range slider keeps its floating toolbar, and Case 4 stays undelivered.** Including `vsrangeslider` in the set would deliver Case 4 for free — its lane is 0, since `VSRangeSliderModel` extends `VSObjectModel` directly and declares no `titleVisible`, so it would fall to `isAnchoredChromeSuppressed` and draw nothing, which is precisely what Case 4 decided. It is excluded anyway, deliberately: that is a behaviour change to a type this design is not otherwise touching, and keeping the diff to the two types in scope is worth carrying one hand-maintained exception. **Record it as an exception with a reason, not as an oversight** — the next reader will otherwise re-derive the question. Case 4 remains open work.

---

## 4. Test surface

`abstract-vs-actions.spec.ts` already carries the shape. Its `kebabOnly capability` block (`:673-683`) asserts true for list and tree, false for the four table-family types; it grows two rows.

| Assertion | Why it is not redundant |
|---|---|
| `kebabOnly` true for the container, false for the calendar | The two treatment decisions of this design, stated where a future edit would break them |
| `resident` true for container and calendar at lane 26 | The slice landing at all |
| `isAnchoredChromeSuppressed` true for both at lane 0 | Title-hidden falls to the no-chrome rung rather than to the floating strip |
| Container in max mode stays anchored | `isMaxModeSelection` must not grow to catch it; this is the assertion that fails if someone "fixes" the selection family uniformly |
| `vsrangeslider` neither resident nor suppressed | The deliberate exception, pinned so a later tidy-up of the two lists does not silently deliver Case 4 |
| A laneless type — text or gauge — neither resident nor suppressed under the gate | The regression this design's section 3 exists to prevent, asserted directly rather than argued in a comment |

The calendar's `allowedActionsNum()` arithmetic needs no new test: it is the table family's path unchanged, already covered.

---

## Manual checks

1. **Container, compact, modern** — kebab alone, right-aligned in the 26px lane, no action buttons at any width.
2. **Container with populated children** — exactly one strip in the stack; no child list draws its own.
3. **Container, max mode** — strip stays in the lane, does not jump to the viewport corner.
4. **Container, title hidden** — no strip, no kebab; right-click still reaches the full menu.
5. **Calendar, compact, modern** — up to three action buttons plus the kebab in the 26px lane; kebab opens the overflow.
6. **Calendar, author-set 36px lane** — same, in the taller lane.
7. **Dropdown calendar** — the inline `<mini-menu>` only; no second strip.
8. **Calendar, title hidden** — no chrome; right-click reaches the menu.
9. **Both types, dark** — glyph tone resolves against the dark card, as the six shipped types do.
10. **A text or gauge assembly in a modern org** — floating toolbar unchanged. This is the section 3 regression check.
11. **Composer on a touch device, container focused** — the mobile toolbar renders; it does not come back empty.
12. **Adhoc range slider, modern** — floating toolbar unchanged.

---

## What this closes, and what it does not

**Closes:** the anchored rollout for every type that has a title lane. After it, `ANCHORED_ASSEMBLY_TYPES` and `hasMiniToolbar()` differ by exactly one entry, with a recorded reason.

**Leaves open**, and neither should be read as forgotten:

- **§10.1, resting visibility by pointer capability.** Blocked on what a kebab-only family does at rest on a desktop. Three of the eight anchored types are now kebab-only, so the question is larger after this design than before it.
- **Case 4, the range slider drawing no chrome.** One line away — adding `"vsrangeslider"` to the set — and deliberately not taken here.

---

## What the implementation found

Recorded after the branch shipped as five commits: the test-control migration (`4434877ca`), slice 4
(`f36469d2f`) and its follow-up (`8b92878fd`), slice 5 (`09b29620e`), and the comment retarget
(`aab93d919`).

**Eleven existing tests used the calendar as their "type outside the anchored set" control** — six in `abstract-vs-actions.spec.ts`, three in `mini-toolbar.service.spec.ts`, two in `vs-object-container.component.display.tl.spec.ts`. Slice 5 destroys that role. The successor is the adhoc range slider: it is the one `hasMiniToolbar()` type that stays outside the set permanently, and unlike a text or gauge assembly it has toolbar actions to assert on — neither `TextActions` nor `GaugeActions` defines `createToolbarActions`, so their `showingActions` is empty and an assertion on it proves nothing. Measured before the migration: at 2000×400, modern, lane 20, the range slider yields four actions and `allowedActionsNum()` 50.

**The migration was sequenced first, as its own commit, while the calendar was still outside the set.** That is what made it provably behaviour-neutral — the suite stayed green across it. Folded into the slice, it would have produced eleven simultaneous failures with two unrelated causes.

**Two of the migrated tests had to change width, and the reason generalises.** The plan assumed the range slider would overflow its toolbar at the same 120px the calendar did. It does not: the calendar exposes about six visible toolbar actions, the range slider exactly one. `openMaxModeVisible` requires both `!composer` and `!adhocFilter` (`range-slider-actions.ts:166-168`), and the fixture sets `adhocFilter` under a composer context, so the max-mode pair is invisible and only `unselect` survives. At 120px, `floor(120/40) = 3` slots held everything, nothing reached the kebab, and both `not.toContain("menu actions")` assertions would have passed **vacuously**. Recalibrated to 60px — one slot — so the overflow the tests are about actually happens. **When a control type changes, re-derive the fixture width from that type's count of *visible* actions rather than carrying the old one over.**

**One test comment claimed more than a browser test can measure.** Slice 4's two max-mode tests were written as though they pinned the server-side geometry that makes the container safe to anchor — it rewrites `objectFormat` to true coordinates where the list and tree carry padding constants. `isMaxModeSelection` (`vs-object-container.component.ts:506-510`) never reads `objectFormat`; it tests `objectType` and `maxMode` only, so the fixtures' differing coordinates are inert. What the tests do pin — and it is worth pinning — is that the exclusion list was not widened to include the container. The comments were corrected in `8b92878fd` to say that, keeping the server-side asymmetry as the stated reason rather than the measured thing.
