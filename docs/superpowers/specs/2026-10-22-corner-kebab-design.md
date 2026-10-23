# Corner kebab for assemblies without a title lane

Date: 2026-10-22
Branch base: `epic-74519`
Status: design, not implemented

## Problem

Under the modern visualization gate, the mini-toolbar of the anchored assembly types sits in the
assembly's title lane. When that lane is hidden, or shorter than `ANCHORED_LANE_MIN` (24px), the
assembly draws no toolbar and no kebab at all, and right-click is the only route to its actions.
That was a deliberate design decision (see `isAnchoredChromeSuppressed` in
`mini-toolbar.service.ts`), but it doesn't work well in practice. A chart without a title looks as
if it has no actions, and users don't think to right-click.

## Goals

1. Wherever the toolbar cannot anchor in the title lane, show a kebab in the assembly's top-right
   corner. It appears on mouse-over and opens the full action inventory.
2. Apply this to every anchored (modern) type: `VSChart`, `VSTable`, `VSCrosstab`, `VSCalcTable`,
   `VSSelectionList`, `VSSelectionTree`, `VSSelectionContainer`, `VSCalendar`.
3. The kebab is always hover-only, in every placement and for every anchored type. No kebab is
   visible at rest. This replaces today's dimmed resting kebab in the title lane.
4. Leave touch devices to the page-level mobile toolbar (`viewer-mobile-toolbar`), unchanged.

## Non-goals

- No change to what the lane strip holds when the lane is tall enough: charts, tables and the
  calendar still get up to three action buttons plus the kebab. Only resting visibility changes
  (goal 3).
- No change to the five floating mount sites: composer, binding pane, both wizard panes and the
  embedded chart. They already overlay the strip and never consult the lane.
- No change for the gate off (legacy viz) or for non-anchored types.
- No backend change.

## Current behaviour (reference)

| Piece | Location | Role |
|---|---|---|
| `anchoredLaneHeight(model)` | `mini-toolbar.service.ts:87` | Title lane height; 0 when the title is hidden |
| `isAnchoredResident(...)` | `mini-toolbar.service.ts:110` | Anchored design and lane ≥ 24px |
| `isAnchoredChromeSuppressed(...)` | `mini-toolbar.service.ts:131` | Anchored design and lane < 24px, so no chrome |
| `isChromeSuppressed(vsObject)` | `vs-object-container.component.ts` | Template gate that skips `<mini-toolbar>` |
| `laneSuppressed` | `abstract-vs-actions.ts:152` | Hides "Hide MiniToolbar" when the strip isn't drawn |
| `kebabOnly` | `abstract-vs-actions.ts:163`, overridden `true` by the selection family | Zero action buttons, kebab is the whole strip |
| `kebabResident` | `mini-toolbar.component.ts:208` | `anchorInTitleLane && !mobileDevice`; adds `.mini-toolbar--anchored` |
| `.mini-toolbar--anchored { visibility: visible }` | `mini-toolbar.component.scss` | Keeps the anchored kebab visible (dimmed) at rest |
| Assembly-hover reveal | `scss/internal/_moving-resize.scss:24-51` | Shows a floating strip, or an anchored strip's buttons, on hover |
| `ACTION_FLOOR` (32px) | `abstract-vs-actions.ts:54` | Below this assembly height a modern assembly shows no controls at all |

Selection list, tree and container always anchor in their flush title lane with
`kebabOnly = true`. So their kebab is the whole strip. For every anchored type the kebab is
visible (dimmed) at rest, and on charts, tables and the calendar the action buttons join it on
hover.

## Design

### Three placements, one predicate each

For an anchored-design assembly in the viewer (`isAnchoredDesign(type, vizModern)`), the container
picks exactly one placement:

| Placement | Condition | Rendering |
|---|---|---|
| **Lane** | lane ≥ `ANCHORED_LANE_MIN` | Strip in the title lane, as today, but the whole strip (kebab included) is hidden until hover or focus |
| **Corner** (new) | lane < `ANCHORED_LANE_MIN` and `objectFormat.height ≥ ACTION_FLOOR` | Kebab only, overlaid inside the top-right corner, hidden until hover or focus |
| **None** | `objectFormat.height < ACTION_FLOOR` | No chrome. `showingActions` already empties the strip below this height, so this rung already exists |

In `mini-toolbar.service.ts`:

- Add `isCornerKebab(objectType, vizModern, laneHeight, objectHeight)` =
  `isAnchoredDesign && laneHeight < ANCHORED_LANE_MIN && objectHeight >= CORNER_KEBAB_FLOOR`.
  Export `CORNER_KEBAB_FLOOR` from the service, set to the same 32px as
  `AbstractVSActions.ACTION_FLOOR`, so the service doesn't import the action layer. Add a comment
  coupling the two values.
- Narrow `isAnchoredChromeSuppressed` to
  `isAnchoredDesign && laneHeight < ANCHORED_LANE_MIN && objectHeight < CORNER_KEBAB_FLOOR`, adding
  an `objectHeight` parameter. Keep it a separate predicate rather than `!isCornerKebab`, for the
  reason its existing doc comment gives: it must stay false for non-anchored types.
- Update the doc comments that describe "no strip, no kebab, right-click only". That statement
  becomes true only below the height floor.

### Actions layer (`abstract-vs-actions.ts`)

- Rename `laneSuppressed` to `cornerKebab` (same viewer/preview/binding/embed guards) and switch it
  to `isCornerKebab(...)`.
- `allowedActionsNum()`: after the `resident` guard, return 0 when `cornerKebab` is true, next to
  the existing `kebabOnly` early return. With zero slots, `getMoreActions()` takes the
  `flattenedMoreActions()` path, so the kebab holds the full inventory: toolbar actions first, then
  the menu, deduplicated by id. That path already exists for the selection family.
- "Hide MiniToolbar": drop the `!this.laneSuppressed` term. In corner mode a control exists, so
  hiding it is meaningful again. Hiding uses the existing `.hidden-mini-toolbar` path.
- The `ACTION_FLOOR` rung in `showingActions` stays as is.

### Container (`vs-object-container.component.ts` / `.html`)

- Add `isCornerKebab(vsObject)`, which delegates to the service predicate with
  `anchoredLaneHeight(object)` and `objectFormat.height`.
- The `@if` gate keeps `!isChromeSuppressed(vsObject)`, which now means "below the height floor"
  only.
- Pass a new input `[cornerKebab]="isCornerKebab(vsObject)"` to `<mini-toolbar>`.
  `anchorInTitleLane` stays false in corner mode, since there is no lane.
- Geometry, in corner mode only:
  - `getToolbarTop`: `objectFormat.top + getCardTopShift(object) + inset.top + CORNER_INSET`, with
    `CORNER_INSET = 4`, fixed across densities.
  - `getToolbarLeft`: `objectFormat.left + inset.left`, the same as the anchored branch.
  - Width: reuse `getAnchoredToolbarWidth(object)`. The host spans the assembly inset to inset,
    and the pill right-aligns itself with the existing `margin-left: auto` mechanism, so there's
    no kebab-width estimate. Then subtract `CORNER_INSET` on the right by giving the container a
    `margin-right` in CSS rather than doing arithmetic here.
  - No viewport clamping, for the same reason as the anchored branch: the kebab is inside the
    assembly.
- Max-mode selection (`isMaxModeSelection`) still doesn't anchor. If the selection list has no lane
  in max mode, it falls through to the corner. Use the max-mode left/width override that
  `getToolbarLeft` already applies for this case, so the corner tracks the true right edge. Verify
  this with a test.
- `getStripGlyphTone` returns `null` in corner mode, because the kebab draws on its own surface
  (see Styling).

### Mini-toolbar component

- Add `@Input() cornerKebab = false`.
- `kebabResident` becomes `(anchorInTitleLane || cornerKebab) && !mobileDevice`. This reuses the
  existing kebab split, the separately rendered kebab button and the `isKebabFocused()` keyboard
  handling. Because `allowedActionsNum()` is 0, `kebabSplit.groups` is empty and only the kebab
  renders.
- Add a host-div class `[class.mini-toolbar--corner]="cornerKebab"` next to
  `mini-toolbar--anchored`, which stays set because `kebabResident` drives it. That way
  `.mini-toolbar--corner` only needs to add the background and the right margin.
- `topY`: in corner mode return `this.top` unchanged, like the `anchorInTitleLane` early return.
  The host has already resolved the position.
- The Esc-dismiss "revealed" check (around line 385): once the anchored strip is hidden at rest,
  its existing `getComputedStyle(...).visibility == "hidden"` early return already says "not
  revealed" at rest. Drop the `kebabResident` branch's "no action group means not revealed" rule.
  It was there only because the kebab was visible at rest, and it would make Esc a no-op on a
  revealed kebab-only strip (the selection family and the corner).

### Styling (`mini-toolbar.component.scss`, `_moving-resize.scss`)

**Hover-only for every anchored kebab.** Delete the resting rule in `mini-toolbar.component.scss`:

```scss
// remove
.mini-toolbar.mini-toolbar--anchored {
  visibility: visible;
}
```

The anchored strip then falls back to the base `.mini-toolbar { visibility: hidden }`, and the
reveal rules that already exist bring it back:

- `_moving-resize.scss:24-27`: assembly hover, `.object-editor` hover, `.vs-object-container`
  hover and `force-show-toolbar` set `visibility: visible` on `.mini-toolbar`.
- `mini-toolbar.component.scss`: `.mini-toolbar:hover { visibility: visible }` keeps the strip up
  while the pointer is on it.

Removing that one rule covers the lane strip of every type and the corner kebab, with no per-type
class. Add one rule for keyboard focus, because without the resting rule nothing keeps a focused
strip visible:

```scss
.mini-toolbar:not(.hidden-mini-toolbar):focus-within {
  visibility: visible;
}
```

Clean up what only made sense with a resting kebab:

- Rewrite the comments in `mini-toolbar.component.scss` and `_moving-resize.scss:31-51` that call
  the kebab "visible at rest", "a touch and keyboard affordance" or the "resting state". Touch
  never reaches these rules (`kebabResident` is false on mobile), so the touch-floor
  `@media (pointer: coarse)` block is unaffected.
- Keep the anchored action groups out of layout until hover (`display: none`, then
  `display: inline-flex`). That keeps the kebab in the same spot when the buttons appear.
- `_moving-resize.scss:44-51` reveals the anchored `.mini-toolbar-container` on hover. It's
  harmless once the host is hidden at rest. Check whether the container still needs its own
  hidden-at-rest state; if not, delete both.
- `--viz-strip-glyph-a` (the dimmed "resting" alpha) becomes the alpha the kebab shows while the
  assembly is hovered, and `--viz-strip-glyph-a-hover` applies when the kebab itself is hovered.
  No code change is needed, but update the "resting" wording.

**Corner background.** This is the only new styling for the corner placement:

```scss
// The small background that keeps the glyph readable over plot, legend or cells.
.mini-toolbar--corner .mini-toolbar-container {
  margin-right: 4px;
  background-color: var(--inet-shell-surface-default);
  border: 1px solid var(--inet-default-border-color);
  border-radius: var(--inet-radius-xl); // same pill as the floating strip
  box-shadow: var(--inet-shadow-sm);
}
```

- The corner host carries no `data-tone`, because `getStripGlyphTone` returns null. So none of the
  `:host([data-tone])` transparent-pill rules apply and the background above is kept. The kebab
  draws at full ink.
- Dark viz: extend the existing `:host-context(.viz-dark) .mini-toolbar:not(.mini-toolbar--anchored)`
  surface rule to also match `.mini-toolbar--corner`, so the pill is dark on a dark dashboard.
- Keep `transition: visibility 0.5s linear` from the base rule. It gives the same short linger as
  the floating strip when the pointer leaves the assembly on its way to the kebab.

The selection container's children already draw no strips (`isMiniToolbarVisible`), so hovering
a child list reveals only the container's kebab, through the same assembly-hover path.

### Keyboard and touch

- Keyboard: a hidden-at-rest kebab isn't a tab stop, because `visibility: hidden` removes it from
  the focus order. That's a change for the lane kebab, which is focusable at rest today. The
  route in is the existing viewer key navigation, which sets
  `forceShowMiniToolbar` / `force-show-toolbar`. That reveals the kebab and `isKebabFocused()`
  highlights it. Once focused, `:focus-within` keeps it visible. This is the same contract the
  floating strip has today.
- Touch: `kebabResident` is already false on `mobileDevice`, so nothing renders on touch and the
  page-level mobile toolbar serves it. To keep that true, make the container gate skip
  `<mini-toolbar>` when `cornerKebab && mobileDevice`, rather than relying on CSS.

## Tests

Update:

- `mini-toolbar.service.spec.ts`: `isAnchoredChromeSuppressed` now needs an object height. Add
  `isCornerKebab` cases: lane 0 / 23 / 24, heights 31 / 32, every anchored type, gate off, and a
  non-anchored type.
- `abstract-vs-actions.spec.ts`: the `laneSuppressed` cases become `cornerKebab`. Hide MiniToolbar
  is visible in corner mode. `allowedActionsNum() === 0` in corner mode. `getMoreActions()`
  returns the flattened full inventory (all toolbar ids plus menu ids, deduplicated, no
  "menu actions" wrapper).
- `vs-object-container.component.spec.ts` and the `display` / `risk` / `interaction` TL specs: an
  untitled chart renders `<mini-toolbar>` with `.mini-toolbar--corner`, top = object top + inset +
  4, kebab right-aligned. A 30px-tall untitled chart still renders nothing.
- `mini-toolbar.component.spec.ts` / `.tl.spec.ts`: in corner mode only the kebab renders, there
  are no action-button groups, and Esc dismissal works. Any spec that expects the anchored kebab
  to be visible or focusable at rest (search for "resting" and `mini-toolbar--anchored`) now
  expects it hidden until hover, focus or `force-show-toolbar`.

Add:

- For a titled chart, table, calendar and selection list, the strip is hidden at rest, and
  visible on assembly hover, under `force-show-toolbar` and with `:focus-within`.
- A max-mode selection list without a title: the corner kebab tracks the true right edge.
- Mobile: an untitled chart renders no `<mini-toolbar>`.
- Dark viz: the corner pill gets the dark surface.

Manual check in the viewer: untitled chart, table, crosstab, calc table, calendar, selection
list/tree/container, each in light and dark viz. Also check a title shorter than 24px, max mode,
an assembly inside a tab, and an embedded viewsheet.

## Risks

- **Overlap:** the kebab covers about 28×28px of the top-right corner while revealed, which is
  often the legend, the last column header or the first list row. The background keeps the glyph
  readable, and the kebab is only revealed while the pointer is over the assembly. That was
  accepted.
- **Hover flicker:** `<mini-toolbar>` is a DOM sibling of `.vs-object-parent-container`, so moving
  onto the kebab leaves the assembly's `:hover`. The kebab's own `:hover` rule and the 0.5s
  visibility transition cover the crossing, the same mechanism the floating strip relies on. Check
  this with a real pointer, not only in specs.
- **Discoverability:** with no resting kebab anywhere, an assembly shows no sign of its actions
  until it is hovered. This was chosen deliberately; it matches the floating (legacy) strip.
- **Scripted hiding:** scripts that hide "Menu Actions" or individual actions can leave the
  flattened inventory empty. `showToolbarContainer` already returns false when there is nothing to
  show, so no empty kebab renders. Cover this with a test.

## Size

Frontend only, roughly 1–2 days including tests. Files touched: `mini-toolbar.service.ts`,
`abstract-vs-actions.ts`, `vs-object-container.component.ts/.html`,
`mini-toolbar.component.ts/.html/.scss`, `scss/internal/_moving-resize.scss`, plus the specs listed
above.
