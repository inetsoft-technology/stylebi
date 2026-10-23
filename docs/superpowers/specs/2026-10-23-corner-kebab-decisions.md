# Corner kebab: decisions taken and not taken

Date: 2026-10-23
Design: [2026-10-22-corner-kebab-design.md](./2026-10-22-corner-kebab-design.md)
Grounded against: `origin/epic-74519` @ `1f2a5bc5d8` (line numbers below are from that commit)

Ordered by visibility: how many viewers see the result, and how often.

## 1. Assemblies without a usable lane get a corner kebab

**Taken (the design's premise).** An anchored assembly whose title is hidden, or whose title lane is
shorter than 24px, shows a kebab in its top-right corner. An assembly under 32px tall still draws no
chrome.

**Not taken:**

- *No chrome, right-click only.* This is today's behaviour, from lane decisions §4 (title hidden) and
  open-item decisions §4 (dense). Rejected because a chart without a title looks as if it has no
  actions, and touch has no right-click.
- *Fall back to the floating legacy strip.* This was the v2 §05 reading for dense, already rejected
  in open-item decisions §4 as a second idiom.

**Consequence:** this supersedes lane decisions §4, open-item decisions §4 and the roadmap's §10.1
row. All three records must be amended in the same PR.

## 2. Resting visibility: hidden at rest where the device can hover

**Taken.** On a device that can hover, no kebab is visible at rest, in either placement (lane or
corner) and for all eight anchored types. It appears on assembly hover, strip hover or keyboard
focus. Devices that cannot hover (`@media (hover: none)`) keep a visible resting kebab. That covers
an iPad sending its default desktop user agent and Windows touch devices, which `GuiTool.isMobileDevice()`
(user-agent sniffing, `gui-tool.ts:1143`) does not detect.

**Not taken:**

- *Today's dimmed resting kebab everywhere.* Rejected by the design's goal 3.
- *Hover-only everywhere, as the design was written.* Rejected because a device that cannot hover
  but is not detected as mobile would have no reliable route to the kebab.
- *§10.1's original query, `(hover: hover) and (pointer: fine)`.* Not adopted as written:
  `(hover: hover)` alone answers the question that matters here, whether a hover reveal can happen.
  Revisit if a hover-capable coarse pointer turns up in testing.

Touch devices that are detected as mobile still get no strip at all and are served by
`viewer-mobile-toolbar`, unchanged.

## 3. Hover spacing: covered controls get a permanent reserve

**Taken.** In corner mode, the controls the kebab would cover always sit a pill-width further left,
whether or not the kebab is showing, so nothing moves on hover. The reserve also applies when the lane
is visible but short, because the corner kebab then reaches about 8px into the row below the title.
Affected controls:

- **Table and crosstab:** the last column header's sort button (`.vs-header-cell-button-sort`,
  `right: 2px`, 20px wide, per slice 2 §4).
- **Calendar:** the rightmost nav button, `calendar-nextYear` (`month-calendar.component.html:63-75`),
  and the year calendar's equivalent, on the panel at the card's right edge.
- **Selection list and tree:** `.pending-alert.top` (`top: -1px; right: 5px`). The existing 48px offset
  at `vs-selection.component.scss:226` excludes `.top` on purpose, because there was no strip to clear.
  Under corner mode, `.top` takes the offset too.
- **Selection container (found 2026-10-23):** each child list's `.selection-list__header-buttons`
  (mini-menu plus collapse toggle, `position: absolute; right: 1px`, revealed on child hover). In an
  untitled container, the first child's buttons sit in the container's top-right corner. All children
  take the reserve, so the buttons stay in one column.
- **Selection container, range slider child (found in final review):** the child's
  `.vs-range-slider-header__buttons` (collapse toggle plus mini-menu, `right: 0; top: 0`). All children
  take the reserve, as for the lists.
- **Selection container, current selections (found in final review):** each current-selection row's
  `.mini-menu` (`right: 2px; top: 0`). The rows render before the children, so in an untitled
  container the first row sits in the corner. Every row takes the reserve.
- **Calendar target (corrected in final review):** `.calendar-body` is a flex row
  (`vs-calendar.component.scss:59-62`), so a double calendar's two panels sit side by side, with
  `.calendar-mode-btn` between them. The card's top-right belongs to the second panel. The reserve goes
  on a single calendar's `.child-calendar-title` (`:not(.double-calendar)`) and on the second panel's
  (`.double-calendar.second-calendar`), for both the month and the year calendar. The first version
  reserved the first panel (`:not(.second-calendar)`), which sits on the left.
- **Table limit:** the reserve targets the last header cell (`td:last-child`). When a table is scrolled
  horizontally and not all the way right, the cell at the right edge is a middle column. Its sort
  button is usually clipped off-screen, but a column whose right edge lands within the kebab's span
  stays covered. Accepted as a residual.
- **Not reserved:** the calc table has no header sort control, and the chart can't take a reserve
  (see "Chart click cost" below).
- **Accepted residuals (final review):**
  - A titled calendar at dense keeps its pending icon under the corner kebab. Its `right: 3px` is an
    inline style from `getPendingIconPosition` in `vs-calendar.component.ts`, which a
    `:host-context` rule can't override without `!important`.
  - A fully script-hidden inventory, and an assembly whose data tip is active, keep the reserve though
    no kebab draws.
  - A dense table with a two-row header, and a last column narrower than about 68px, clip the moved
    sort button.
  - A zero-width hidden last column takes the reserve, so the visible last column's sort button stays
    under the kebab.

The precedent is the titled selection list's permanent 48px pending-alert offset, derived from a pill
of about 40px. The reserve width is pill plus corner inset, about 48px; measure it in a browser before
fixing it. The mechanism is a class on `.vs-object-parent-container` in corner mode, read by each
component through `:host-context(...)`.

The class applies only where a kebab can draw. It is bound to `hasCornerReserve`, which adds the
strip's own mount gates, `!hideMiniToolbar` and `enabled`, to `isCornerKebab`. So the embed viewer,
which defaults `hideMiniToolbar` to true, and a disabled assembly open no gap. A strip dismissed with
Hide MiniToolbar keeps its reserve, so the covered controls don't move when it is dismissed.

**Not taken:**

- *Shift only while the kebab is hovered.* It doesn't uncover anything: the pointer has to be on the
  kebab to trigger it, so the click still lands on the kebab. As the pointer follows the control
  left, it leaves the kebab and the control slides back under it, so it oscillates. It would also need
  `:has()` across a DOM sibling, in a global stylesheet reaching into three components.
- *Shift on assembly hover.* No oscillation, but a sorted column's arrow (visible at rest) would jump
  on every hover.
- *Float the corner kebab just above the card's edge.* Covers nothing, but it is the second idiom the
  earlier decisions rejected, and it clips at the canvas top or inside tabs.
- *Drop the kebab below the header or nav row.* Rejected in slice 2 §4 because its geometry would
  differ from the chart's.
- *Accept the overlap.* The design's Risks section treated it as legibility only. While the pointer is
  over the assembly, which is the only time anyone clicks, the covered controls can't be clicked at
  all.

## 4. A visible title shorter than 24px uses the corner position

**Taken (as written).** When the lane is between 1 and 23px, the kebab sits at the corner position:
`objectFormat.top + cardTopShift + inset.top + 4`. At dense density (20px lane) this applies to every
titled anchored assembly. The kebab covers the right end of the title and about 8px of the body, and
decision 3's reserve handles the body side.

**Not taken:**

- *Top-align on the lane.* Read as part of the title row, with a 4px overhang into the body at a 20px
  lane.
- *Keep no chrome for short titled lanes.* Preserves the dense decision, but leaves the problem the
  design exists to fix for every titled assembly at dense.

## 5. Hide at rest with opacity, not visibility

**Taken.** The anchored host stays `visibility: visible`. At rest, on devices that can hover, the
container is `opacity: 0` and its button groups are `pointer-events: none`. Assembly hover, strip hover
and `:has(:focus-visible)` restore opacity and pointer events. The kebab stays a tab stop. Only
keyboard users notice this mechanism; for a mouse user it looks identical to `visibility: hidden`.

**Not taken:**

- *`visibility: hidden`, as the design was written.* Removes the tab stop. Only `NavigationComponent`
  subclasses emit `showHideMiniToolbar` (the calendar, selection list/tree and the range slider;
  `vs-object-container.component.html:92, 218, 230`). So chart, table, crosstab, calc table and
  selection container would have no keyboard route to the kebab at all. Roadmap §10.1 already
  required opacity for this reason.

**Follow-ons:**

- Reveal on `:has(:focus-visible)`, not `:focus-within`. Otherwise a mouse click on the kebab leaves
  focus on it and the strip stays drawn after the pointer leaves.
- Group pointer events must be off at rest. `.mini-toolbar-button-group { pointer-events: all }` is
  unconditional, so an invisible strip with mouse focus inside it would otherwise keep clickable
  buttons over the title.
- **Implemented in final review:** pointer events are now off at rest and restored on every reveal.
  Inside `@media (hover: hover)`, the kebab group and the kebab are `pointer-events: none` at rest. The
  kebab needs its own rule because it declares an explicit `pointer-events: auto`, which the group's
  value can't reach. So a control placed over an untitled card's corner, or an expanded dropdown over
  it, keeps its clicks. Strip `:hover`, `:has(:focus-visible)` and the global assembly-hover rule in
  `_moving-resize.scss` restore `auto`, each scoped `:not(.hidden-mini-toolbar)`. The strip hover is
  what keeps the kebab live once the pointer crosses from the assembly onto it. The global rule is
  unencapsulated, so it is written to outrank the component's resting rule: its weakest selectors are
  0,6,0 (group) and 0,7,0 (kebab), against 0,4,0 and 0,6,0 for the encapsulated resting rule.
- The Esc check (`mini-toolbar.component.ts:372-397`) can no longer use the host's visibility, because
  the host stays visible. "Revealed" becomes "the container's computed opacity is not 0". That
  replaces both the design's proposal and today's no-action-group rule.
- **Esc and the kebab's own menu (final review):** the menu closes on a `document` Esc keyup, and the
  same keyup reaches the toolbar's `window` listener while the pill is still opaque. So the Esc check
  also skips a resident strip while focus is inside it. Otherwise a keyboard user's focused kebab
  would go `visibility: hidden` and focus would be lost. Residual: Safari doesn't focus a button on
  mouse click, so a mouse user who opens the menu there and presses Esc still dismisses the strip.

## Corrections to the design, adopted without a ruling

These are invisible when they work and visible defects when they don't.

- **Host width.** `mini-toolbar.component.html:24` binds the host width only when
  `anchorInTitleLane`, and `vs-object-container.component.html:373` passes `getAnchoredToolbarWidth`
  only when anchored. Without these, the corner pill lands at the left inset. Both bindings need
  `|| cornerKebab`.
- **Empty pill.** `showToolbarContainer` (`mini-toolbar.component.ts:197`) counts groups, not visible
  groups. At zero slots, `getShowingActions` returns one empty group per toolbar group. The corner pill
  has a surface, so a fully script-hidden inventory would draw an empty bordered sliver. Test
  `group.visible`. This was found by reading the code; confirm with a spec.
- **Hide MiniToolbar.** Keep a term on the narrowed suppressed predicate instead of dropping
  `!laneSuppressed` (`abstract-vs-actions.ts:477`). Otherwise it appears in the right-click menu of
  untitled assemblies under 32px, where no control exists.
- **Height floor constant.** Move the 32px floor into `mini-toolbar.service.ts` and import it in
  `AbstractVSActions`, which already imports from the service. No duplicate constant and no "keep these
  in sync" comment.
- **Mobile gate.** Mobile skips `<mini-toolbar>` in corner mode, as the design says. Fold it into
  the container's suppression check so the template keeps one gate, and leave the reserve class
  off on mobile.
- **Corner styling.** The base pill already draws the background, border and radius
  (`mini-toolbar.component.scss:25-34`). Only the shadow is new. The 4px right inset is not a margin:
  `getCornerToolbarWidth` is the anchored width less `CORNER_KEBAB_INSET` (4), and the pill
  right-aligns inside that box with its auto left margin.
- **Dark surface.** Extend both `:host-context(.viz-dark) .mini-toolbar:not(.mini-toolbar--anchored)`
  rules, the container and the group divider, to `.mini-toolbar--corner`.
- **Glyph tone.** `getStripGlyphTone` needs no change. The template only calls it when the strip is
  anchored (`vs-object-container.component.html:354`).
- **Design doc facts.** Assembly hover does not reveal the lane's action buttons. They stay
  `display: none` until the strip itself is hovered or focused (`mini-toolbar.component.scss:135-152`).
  The selection family does not "always" anchor: not with a hidden title, and not in max mode.

## 6. Max-mode selection without a title: kebab at the top-right

**Taken (2026-10-23).** An untitled selection list or tree in max mode shows its kebab in the same
place a titled one already does: the floating max-mode strip. The existing viewport clamp
right-aligns it (`getToolbarLeft` with `left + width`, `vs-object-container.component.ts:604-614`),
and it floats at `objectFormat.top - 24` (`mini-toolbar.component.ts:423`). The only change is that
the untitled list is no longer suppressed. It takes no corner geometry and no `cornerKebab` input.

The server writes the max-mode list's `objectFormat` as `top = 30` and `left = 20`
(`VSSelectionBaseModel.TOP_PADDING` / `LEFT_PADDING`). The 30 is commented "padding mini-toolbar
height", so the floating strip already has its own space there, and the list's real origin isn't
needed.

**Not taken:**

- *Corner geometry computed from `objectFormat`.* The max-mode `objectFormat` holds padding
  constants, not the list's origin, and the corner branch skips the clamp the max-mode left depends
  on.
- *No chrome, as today.* It would leave an untitled max-mode list as the one remaining assembly
  without a kebab.

## 7. Chart click cost: accepted

**Taken (2026-10-23).** A server-rendered plot and legend can't take a reserve. While the kebab is
revealed, it blocks clicks on whatever sits in the chart's top-right corner: legend title, legend
items or marks. This is accepted as both a legibility cost and a click cost.

## Still open

- **Release branch check.** Bug #79211's Reported and Target versions weren't checked (no Redmine
  access in this session). The base is `epic-74519` per the design; confirm neither version is a
  `stylebi-wiz-*` release.
- **Missing manual checks.** Data-tip and pop-up assemblies (usually untitled; the data-tip directive
  repositions the toolbar above the tip at `vs-data-tip.directive.ts:245-249`, and they have no menu
  actions), dense dashboards with titles, an iPad, and a range slider as the first child of an
  untitled container.
