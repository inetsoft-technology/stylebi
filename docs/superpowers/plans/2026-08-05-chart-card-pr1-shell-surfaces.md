# Chart Card PR 1 — Shell Surfaces Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deduplicate and retokenize the portal's three tooltip surfaces, rebuild the CARD tooltip's six-size type ramp into three roles, and pull the data-tip scrim and stacking numbers onto the shell's declared layer registry.

**Architecture:** All changes live in shared shell code (`scss/internal/_directives.scss`, `widget/tooltip/`, `vsobjects/objects/data-tip/`) and reach up to 12 components, so this PR lands *before* anything chart-side inherits from it. Three changes are unconditional because they emit identical pixels; every change that moves a pixel sits behind the `.viz-modern` gate. Inside the PR, deduplication comes before binding and deletion before retokenizing — otherwise every binding is applied twice.

**Tech Stack:** SCSS (Dart Sass, `@use`), Angular 21.2 + TypeScript 5.9, Vitest 4.1.7.

## Global Constraints

- **Design source of truth:** `docs/superpowers/specs/lookfeel/chart-card-slice1-design.md` §3. The CARD ramp is drawn at real sizes in `chart-card-design/Chart overlay surfaces - decided visuals.html` §02 — **read that before writing any ramp CSS**.
- **The source tickets contain four wrong code claims and five stale ones.** Two of each affect this PR; both are restated below. The full list, with how to confirm each, is in `docs/superpowers/specs/lookfeel/chart-card-source-doc-corrections.md` — **read it before trusting a line reference in `Shell surfaces - ticket.md`.**
- **Never scope defensively to the chart.** No `.vs-chart .widget__card-tooltip`. If a shared class is wrong, fix the shared class.
- **Gate readers:** CSS `.viz-modern` on `<body>` (component styles use `:host-context(.viz-modern)`); TypeScript `GuiTool.isVizModern()` in `app/common/util/gui-tool.ts:65`.
- **Gated selectors are written as `.viz-modern` descendant rules** in `_directives.scss`, because the tooltip classes are global (not component-scoped) — `:host-context` is unavailable there.
- **Token values (already shipped, do not redeclare):** `--inet-radius-sm: 2px`, `--inet-radius-xl: 6px`, `--inet-font-size-base: 13px`, `--inet-feedback-font-size: 11px`, `--inet-space-1: 2px`, `--inet-space-3: 6px`, `--inet-space-4: 8px`, `--inet-shadow-overlay`, `--inet-text-color`, `--inet-text-muted-color`, `--inet-text-subtle-color`, `--inet-default-border-color`, `--inet-overlay-scrim-bg-color: rgba(0, 0, 0, 0.3)`.
- **One token to add:** `--inet-font-size-lg: 16px`.
- **Test one instance per consumer, not one per chart type.** Five chart types tell you nothing new; a tooltip on a table and one on a selection list do.
- **Do not touch** the second `$stacking-order` map at `_directives.scss:365` — that one is worksheet graph thumbnails and is unrelated despite the name reuse.
- **Frontend test commands:** `cd web && npm run test:portal`, `npm run test:portal:tl`, `npm run lint`.

## Two source-doc corrections this plan carries

Both were found by reading the code; implement the corrected version, not the ticket's.

1. **The two tooltip classes are not identical.** `Shell surfaces - ticket.md` item 6 says
   `.hidden__annotation-tooltip` duplicates `.widget__default-tooltip` "property-for-property". It
   duplicates the eleven properties the ticket lists, but `.widget__default-tooltip` *also* carries
   `max-width: 40vw` and `max-height: 40vh`, which `.hidden__annotation-tooltip` does not. **Those two
   must stay out of the mixin**, or hidden-annotation tooltips silently gain a viewport cap.
2. **Item 12's z-index work is a maintainability fix, not a live bug.** The handoff calls it "a live
   bug… any dropdown or tooltip renders over a data tip." The relative order is in fact already
   correct: scrim `9996` < pop source `9997` < pop content (`natural + 99999`, so ≲ `101000`) <
   `.fixed-dropdown` `999900` < `w-tooltip` `999901`. A dropdown opened *inside* a data tip should be
   above it, which is what happens. The real defect is that the order is accidental rather than
   declared. **So this task must preserve today's relative order**, not reorder anything. Its value is
   that the next person can reason about the layering.

---

## File Structure

| File | Responsibility in this PR |
|---|---|
| `web/projects/portal/src/scss/internal/_directives.scss` | The three tooltip surfaces, the new shared mixin, the `$stacking-order` registry, all gated tooltip overrides |
| `web/projects/portal/src/scss/_variables.scss` | `:root` — add `--inet-font-size-lg` |
| `web/projects/portal/src/app/graph/objects/chart-area.component.scss` | `.chart__tooltip` — the fourth radius call site |
| `web/projects/portal/src/app/vsobjects/objects/data-tip/date-tip-helper.ts` | Scrim colour + the three z-index constants |
| `web/projects/portal/src/app/vsobjects/objects/data-tip/date-tip-helper.spec.ts` | **New** — unit coverage for the gated scrim colour |
| `web/projects/portal/src/app/vsobjects/objects/data-tip/vs-pop-component.directive.ts` | `popBackground` default, the `rgba(255,255,255,0)` literal |

**Why no unit tests for the SCSS tasks.** The global stylesheets are not loaded into the Vitest
environment, so `getComputedStyle` assertions against `.widget__card-tooltip` would pass or fail for
reasons unrelated to the change. Tasks 1, 4, 5 and 6 are therefore verified by a Sass compile plus the
manual matrix in Task 8. Tasks 2, 3 and 7 touch TypeScript and *are* unit tested. Writing fake tests
for the CSS would be worse than admitting this.

---

## Task 1: Extract the shared plain-tooltip mixin (item 6, ungated)

**Files:**
- Modify: `web/projects/portal/src/scss/internal/_directives.scss:202-220` (`.widget__default-tooltip`) and `:294-310` (`.hidden__annotation-tooltip`)

**Interfaces:**
- Consumes: nothing.
- Produces: `@mixin plain-tooltip-surface` in `_directives.scss`, holding the eleven shared properties. Tasks 5 and 6 bind *inside* this mixin and inside the gated blocks rather than in the two rules.

- [ ] **Step 1: Read both rules and confirm the property delta before editing**

Run:
```bash
cd community/web/projects/portal/src && sed -n '202,222p;293,312p' scss/internal/_directives.scss
```
Expected: `.widget__default-tooltip` has `max-width: 40vw` and `max-height: 40vh`;
`.hidden__annotation-tooltip` does not. Everything else matches. If this is no longer true, stop and
re-derive the mixin contents — do not proceed on the ticket's description.

- [ ] **Step 2: Add the mixin above `.widget__default-tooltip`**

Insert immediately before the `/** tooltip */` comment block at `_directives.scss:200`:

```scss
// The plain tooltip surface, shared by .widget__default-tooltip and
// .hidden__annotation-tooltip. Deliberately excludes max-width/max-height: only the
// default tooltip caps its size, and folding those in would cap hidden annotations too.
@mixin plain-tooltip-surface {
  overflow: hidden;
  white-space: pre-wrap;
  word-wrap: break-word;

  color: var(--inet-text-color);
  font-size: 12px;
  background-color: var(--inet-dialog-bg-color);
  border-radius: 1px;
  box-shadow: var(--inet-shadow-low);
  padding: var(--inet-space-2) var(--inet-space-3);
  border: 1px solid var(--inet-default-border-color);
  margin: 3px;
}
```

- [ ] **Step 3: Replace both rule bodies with the mixin**

`.widget__default-tooltip` becomes:

```scss
.widget__default-tooltip {
  @include plain-tooltip-surface;

  max-width: 40vw;
  max-height: 40vh;
}
```

`.hidden__annotation-tooltip` becomes:

```scss
.hidden__annotation-tooltip {
  @include plain-tooltip-surface;
}
```

Delete the two commented-out `//display: flex;` / `//flex-direction: column;` pairs while you are here;
they are dead in both rules.

- [ ] **Step 4: Verify the compiled CSS is byte-equivalent for these two selectors**

Run:
```bash
cd community/web && npx sass --load-path=projects/portal/src/scss projects/portal/src/scss/internal/_directives.scss /tmp/after.css --style=expanded
```
Expected: compiles with no error. Grep the output for both selectors and confirm each still emits the
same declarations it did before (the default tooltip with `max-width`/`max-height`, the hidden
annotation without). Property *order* may differ; values must not.

- [ ] **Step 5: Build the portal styles**

Run: `cd community/web && npm run lint`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add community/web/projects/portal/src/scss/internal/_directives.scss
git commit -m "refactor(shell): extract shared plain-tooltip surface mixin

Shell surfaces ticket item 6. .hidden__annotation-tooltip and
.widget__default-tooltip shared eleven properties; a fix to one would have
landed on only one. max-width/max-height stay on the default tooltip only —
the ticket's property-for-property claim omitted them."
```

---

## Task 2: Bind the pop-component named colours (item 12, ungated)

**Files:**
- Modify: `web/projects/portal/src/app/vsobjects/objects/data-tip/vs-pop-component.directive.ts:42` and the `rgba(255,255,255,0)` literal near line 325

**Interfaces:**
- Consumes: nothing.
- Produces: nothing later tasks depend on.

- [ ] **Step 1: Locate both literals**

Run:
```bash
cd community/web/projects/portal/src && grep -n 'popBackground\|rgba(255,255,255,0)\|rgba(255, 255, 255, 0)' app/vsobjects/objects/data-tip/vs-pop-component.directive.ts
```
Expected: an `@Input() popBackground: string = "white"` around line 42, and one
`rgba(255,255,255,0)` assignment around line 325.

- [ ] **Step 2: Swap the default background to the token**

```ts
   @Input() popBackground: string = "var(--inet-dialog-bg-color)";
```

`--inet-dialog-bg-color` resolves through `--inet-shell-surface-default` to `#FFFFFF`
(`_variables.scss:38, 263, 353`), so the rendered pixel is unchanged in the light theme and the surface
now follows a theme.

- [ ] **Step 3: Replace the fully-transparent white with `transparent`**

Change the `rgba(255,255,255,0)` assignment to the string `"transparent"`. Same computed value, and it
states the intent.

- [ ] **Step 4: Run the data-tip and pop-component suites**

Run: `cd community/web && npm run test:portal -- --run vs-pop-component`
Expected: PASS. If no spec matches that filter, run `npm run test:portal` and confirm no new failures.

- [ ] **Step 5: Commit**

```bash
git add community/web/projects/portal/src/app/vsobjects/objects/data-tip/vs-pop-component.directive.ts
git commit -m "refactor(shell): bind pop-component background to the dialog token

Shell surfaces ticket item 12. popBackground defaulted to the named colour
\"white\" so it could not follow a theme; --inet-dialog-bg-color resolves to
the same #FFFFFF today. rgba(255,255,255,0) becomes transparent."
```

---

## Task 3: Declare the data-tip layers against the stacking registry (item 12, ungated)

**Files:**
- Modify: `web/projects/portal/src/scss/internal/_directives.scss:20-25` (`$stacking-order`)
- Modify: `web/projects/portal/src/app/vsobjects/objects/data-tip/date-tip-helper.ts:18-22`

**Interfaces:**
- Consumes: nothing.
- Produces: `DateTipHelper.getPopUpBackgroundZIndex()`, `getPopUpSourceZIndex()` and
  `getPopUpContentBoostZIndex()` keep their existing signatures (`(): number`). Only the returned
  values and their documentation change. No call site changes.

**Read this before starting.** Today's *relative* order is already correct (see correction 2 in the
header). This task makes it declared instead of accidental, and must not change which layer wins.

- [ ] **Step 1: Record the current effective order so you can prove it is preserved**

Run:
```bash
cd community/web/projects/portal/src && sed -n '20,26p' scss/internal/_directives.scss && sed -n '18,23p' app/vsobjects/objects/data-tip/date-tip-helper.ts
```
Expected: registry base `999900` over `(".fixed-dropdown", w-tooltip)`; helper constants
`POP_UP_BACKGROUND_ZINDEX = 9996` and `POP_UP_CONTENT_BOOST_ZINDEX = 99999`. Write the resulting order
down: scrim `9996` < source `9997` < content `≲101000` < `.fixed-dropdown` `999900` < `w-tooltip`
`999901`.

- [ ] **Step 2: Name the pop layers in the registry, below the dropdown layer**

Replace the registry block at `_directives.scss:20-25` with:

```scss
// The shell's declared layer order, lowest first. The pop/data-tip layers sit below
// .fixed-dropdown deliberately: a dropdown opened inside a data tip must render above it.
// Anything added here must be added to the numeric constants in date-tip-helper.ts too —
// that file drives the same layers from TypeScript, because their z-index is assigned
// per-object at runtime rather than by a static class.
$stacking-order: (
        ".pop-component-bg",
        ".pop-component-source",
        ".pop-component-content",
        ".fixed-dropdown",
        w-tooltip
);

@include set-z-index-selector($stacking-order, 999897);
```

`set-z-index-selector` (`scss/internal/_mixins.scss:53`) emits `$startIndex + $i - 1` per entry. Base
`999897` over five entries therefore yields `.pop-component-bg` 999897, `.pop-component-source` 999898,
`.pop-component-content` 999899, `.fixed-dropdown` **999900**, `w-tooltip` **999901** — the last two
keeping exactly the values they have today, with three named layers beneath them.

The base must be `999897`, not `999900`: raising it would push `.fixed-dropdown` and `w-tooltip` to
999903/999904, which is harmless in isolation but silently changes two values other code may compare
against.

- [ ] **Step 3: Point the TypeScript constants at the same three values**

Replace `date-tip-helper.ts:18-22` with:

```ts
// These mirror the three pop/data-tip entries in $stacking-order
// (scss/internal/_directives.scss). They live here as numbers because the pop layers get their
// z-index assigned per object at runtime, not from a static class — change both together.
const POP_UP_BACKGROUND_ZINDEX = 999897;   // .pop-component-bg — the dim scrim
const POP_DIM_COLOR: string = "rgba(0, 0, 0, 0.2)";
// The content layer, reached by adding this to an object's natural z-index. The boost has to
// exceed the highest natural z-index an assembly can carry, so the sum lands on the content
// layer rather than below the scrim.
const POP_UP_CONTENT_BOOST_ZINDEX = 999899;
```

Note `getPopUpSourceZIndex()` returns `POP_UP_BACKGROUND_ZINDEX + 1` = 999898, which is exactly the
`.pop-component-source` layer. Leave that method as it is.

- [ ] **Step 4: Confirm the content boost still clears the scrim from any natural z-index**

The content z-index is `natural + POP_UP_CONTENT_BOOST_ZINDEX` (`vs-object-container.component.ts:585`),
so it is now ≥ 999899 and strictly above both the scrim and the source layer, and above
`.fixed-dropdown` for any natural z-index ≥ 2. **That is a change in relative order** —
previously content sat *below* dropdowns. Cap it instead so the order is preserved:

```ts
   public static getPopUpContentZIndex(naturalZIndex: number): number {
      return Math.min(POP_UP_CONTENT_BOOST_ZINDEX,
                      POP_UP_BACKGROUND_ZINDEX + 2 + (naturalZIndex || 0));
   }
```

Add that method to `DateTipHelper`, then change `vs-object-container.component.ts:585` from
`zIndex += DateTipHelper.getPopUpContentBoostZIndex();` to
`zIndex = DateTipHelper.getPopUpContentZIndex(zIndex);`.

- [ ] **Step 5: Write the failing test for the ordering invariant**

Create `web/projects/portal/src/app/vsobjects/objects/data-tip/date-tip-helper.spec.ts`:

```ts
import { DateTipHelper } from "./date-tip-helper";

describe("DateTipHelper stacking", () => {
   it("orders scrim below source below content", () => {
      expect(DateTipHelper.getPopUpBackgroundZIndex())
         .toBeLessThan(DateTipHelper.getPopUpSourceZIndex());
      expect(DateTipHelper.getPopUpSourceZIndex())
         .toBeLessThan(DateTipHelper.getPopUpContentZIndex(0));
   });

   it("keeps pop content below the dropdown layer for any natural z-index", () => {
      const FIXED_DROPDOWN_Z = 999900;

      for(const natural of [0, 1, 5, 100, 999, 100000]) {
         expect(DateTipHelper.getPopUpContentZIndex(natural)).toBeLessThan(FIXED_DROPDOWN_Z);
      }
   });
});
```

- [ ] **Step 6: Run the test to verify it fails before Step 4's method exists**

If you implemented Step 4 already, revert `getPopUpContentZIndex` temporarily.
Run: `cd community/web && npm run test:portal -- --run date-tip-helper`
Expected: FAIL with `getPopUpContentZIndex is not a function`.

- [ ] **Step 7: Restore the implementation and re-run**

Run: `cd community/web && npm run test:portal -- --run date-tip-helper`
Expected: PASS, both tests.

- [ ] **Step 8: Run the container suites that read these values**

Run: `cd community/web && npm run test:portal -- --run vs-viewsheet` then
`npm run test:portal -- --run vs-object-container`
Expected: PASS. `vs-viewsheet.component.tl.spec.ts:340,348` assert
`getPopUpSourceZIndex()` equality rather than a literal, so they should follow the new value; if either
hardcodes `9997`, update the assertion to call the helper.

- [ ] **Step 9: Commit**

```bash
git add community/web/projects/portal/src/scss/internal/_directives.scss \
        community/web/projects/portal/src/app/vsobjects/objects/data-tip/date-tip-helper.ts \
        community/web/projects/portal/src/app/vsobjects/objects/data-tip/date-tip-helper.spec.ts \
        community/web/projects/portal/src/app/vsobjects/objects/vs-object-container.component.ts
git commit -m "refactor(shell): declare the data-tip layers in \$stacking-order

Shell surfaces ticket item 12. The scrim, source and content layers were three
hand-computed numbers (9996, +1, +99999) sitting two orders of magnitude below a
registry that held only .fixed-dropdown and w-tooltip. They are now named layers
beneath the dropdown layer, which is the order that was already in effect.

.fixed-dropdown and w-tooltip keep their existing 999900/999901. The content
boost is clamped so pop content stays below the dropdown layer from any natural
z-index, which the unclamped addition would not have guaranteed."
```

---

## Task 4: Add `--inet-font-size-lg` (ungated declaration)

**Files:**
- Modify: `web/projects/portal/src/scss/_variables.scss:237` area (`:root`, beside `--inet-font-size-base`)

**Interfaces:**
- Consumes: nothing.
- Produces: `--inet-font-size-lg: 16px`, consumed by Task 6's value role.

- [ ] **Step 1: Confirm the token does not already exist**

Run: `cd community/web/projects/portal/src && grep -n "inet-font-size-lg" scss/_variables.scss`
Expected: no output.

- [ ] **Step 2: Add the declaration next to its neighbour**

Immediately after `--inet-font-size-base: 13px;` at `_variables.scss:237`:

```scss
  // The one step above base. Added for the card tooltip's value role; generally useful, not
  // tooltip-specific. Pairs with --inet-feedback-font-size (11px) as the ramp's caption step.
  --inet-font-size-lg: 16px;
```

- [ ] **Step 3: Verify it resolves**

Run: `cd community/web && npm run lint`
Expected: PASS. A `:root` declaration nothing reads yet changes no rendering, so there is nothing else
to assert at this step.

- [ ] **Step 4: Commit**

```bash
git add community/web/projects/portal/src/scss/_variables.scss
git commit -m "feat(shell): add --inet-font-size-lg (16px)

Chart type sizes ticket. The shell had no name for 16px; the card tooltip's
value role needs one. Additive — nothing reads it yet."
```

---

## Task 5: Gated tooltip bindings — elevation, radii, font size (items 1, 3, 5)

**Files:**
- Modify: `web/projects/portal/src/scss/internal/_directives.scss` (after the three tooltip rules)
- Modify: `web/projects/portal/src/app/graph/objects/chart-area.component.scss` (`.chart__tooltip`)

**Interfaces:**
- Consumes: `@mixin plain-tooltip-surface` from Task 1.
- Produces: a `.viz-modern` block in `_directives.scss` that Task 6 extends with the ramp.

These are three separate value swaps but one gated block, so they ship together.

- [ ] **Step 1: Add the gated block after `.hidden__annotation-tooltip`**

```scss
// Modern visualization gate. The tooltip surfaces are global classes, so the gate is a
// descendant selector on the body class rather than :host-context.
.viz-modern {
  // Item 1 — a position:fixed overlay should not carry the card's own in-flow elevation.
  .widget__default-tooltip,
  .widget__card-tooltip,
  .hidden__annotation-tooltip {
    box-shadow: var(--inet-shadow-overlay);
  }

  // Item 3 — 1px is not on the radius scale (2/3/4/6/pill).
  .widget__default-tooltip,
  .hidden__annotation-tooltip {
    border-radius: var(--inet-radius-sm);
  }

  // Item 3 — 8px is not on the scale either; 6px is the chart card's own radius, so the card
  // tooltip becomes visibly the same family of surface as the card it belongs to.
  .widget__card-tooltip {
    border-radius: var(--inet-radius-xl);
  }

  // Item 5 — 12px is not a token. Body copy on a surface is --inet-font-size-base.
  .widget__default-tooltip,
  .hidden__annotation-tooltip {
    font-size: var(--inet-font-size-base);
  }
}
```

- [ ] **Step 2: Bind the fourth radius call site and its padding**

`.chart__tooltip` is a separate rule in a component stylesheet, not another copy of the default
tooltip — it shares only the `1px` radius, and differs on `white-space` (`pre` vs `pre-wrap`) and
`padding` (`12px` vs the token pair). Add to `app/graph/objects/chart-area.component.scss`:

```scss
:host-context(.viz-modern) .chart__tooltip {
  border-radius: var(--inet-radius-sm);
  padding: var(--inet-space-5);
}
```

`--inet-space-5` is 12px, so the padding is a rename rather than a change.

- [ ] **Step 3: Confirm the gated block does not leak to gate-off**

Run:
```bash
cd community/web && npx sass --load-path=projects/portal/src/scss projects/portal/src/scss/internal/_directives.scss /tmp/gated.css --style=expanded && grep -c "viz-modern" /tmp/gated.css
```
Expected: a non-zero count, and every one of the new declarations appears only inside a
`.viz-modern …` selector. No bare `.widget__card-tooltip { border-radius: 6px }` may appear.

- [ ] **Step 4: Lint**

Run: `cd community/web && npm run lint`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add community/web/projects/portal/src/scss/internal/_directives.scss \
        community/web/projects/portal/src/app/graph/objects/chart-area.component.scss
git commit -m "feat(shell): bind tooltip elevation, radii and font size to tokens under the gate

Shell surfaces ticket items 1, 3 and 5. Elevation moves from --inet-shadow-low
to --inet-shadow-overlay on all three surfaces; the off-scale 1px and 8px radii
bind to --inet-radius-sm and -xl across four call sites; the 12px default font
size binds to --inet-font-size-base.

Gated behind .viz-modern: these move pixels, so gate-off orgs are unaffected."
```

---

## Task 6: Rebuild the CARD type ramp into three roles (item 4, gated)

**Files:**
- Modify: `web/projects/portal/src/scss/internal/_directives.scss:222-287` (`.widget__card-tooltip` interior) and the `.viz-modern` block from Task 5

**Interfaces:**
- Consumes: `--inet-font-size-lg` (Task 4), the `.viz-modern` block (Task 5).
- Produces: nothing later tasks depend on.

**Read `chart-card-design/Chart overlay surfaces - decided visuals.html` §02 before writing this.** It
draws both ramps at real sizes.

The decided ramp:

| Role | Size | Weight | Colour |
|---|---|---|---|
| Value | 16px `--inet-font-size-lg` | 600 | `--inet-text-color` |
| Label | 13px `--inet-font-size-base` | 400 | `--inet-text-muted-color` |
| Caption | 11px `--inet-feedback-font-size` | 400 | `--inet-text-subtle-color` |

Dropped: 14px, 12px and 20px. Hierarchy stops being encoded three times (size, opacity, margin) and is
carried by size and colour only.

- [ ] **Step 1: Inventory what the current rule set actually contains**

Run:
```bash
cd community/web/projects/portal/src && sed -n '222,290p' scss/internal/_directives.scss
```
Expected: `.tt-tier-1` 16px/600, `.tt-tier-2` 13px + `opacity: 0.9`, `.tt-tier-3` 11px +
`opacity: 0.7`, `.tt-tier-2.tt-subtitle` 12px + `opacity: 0.65` + `letter-spacing`,
`.tt-tier-1.tt-subtitle` 14px + `opacity: 0.65` + `letter-spacing`, `.tt-stack-total` 20px, plus
`.tt-section` / `.tt-section.tt-section-first` margin rules and the
`.tt-tier-1:not(:first-child)` / specificity-ordering comment.

**Note the combined-card structure postdates the source ticket.** `.tt-section`,
`.tt-section-first` and the `.tt-tier-2.tt-subtitle` "shared X-dim header" came from the combined-card
and tooltip-tail work. The ramp change reassigns their *type*; it must not remove their *grouping*.
`.tt-section` margins stay.

- [ ] **Step 2: Map each existing class onto a role**

| Existing class | New role | Note |
|---|---|---|
| `.tt-tier-1` | value | already 16px/600; add the explicit colour |
| `.tt-stack-total` | value | 20px → 16px; gains a rule and padding instead |
| `.tt-tier-2` | label | keeps 13px; loses `opacity: 0.9` for muted colour |
| `.tt-tier-3` | caption | keeps 11px as a token; loses `opacity: 0.7` for subtle colour |
| `.tt-tier-1.tt-subtitle` | caption | **deleted as a size**; the base `.tt-subtitle` carries the caption role |
| `.tt-tier-2.tt-subtitle` | caption | **deleted as a size**; its comment says it "reads as a caption", which is now literally true |

- [ ] **Step 3: Add the gated ramp inside the Task 5 `.viz-modern` block**

```scss
  // Item 4 — three roles, not six sizes. 16/13/11 gives ~1.23 and ~1.18 steps; the previous
  // 16→14→13→12→11 ramp stepped 2,1,1,1, and below ~14px a 1px difference is not a signal.
  // De-emphasis moves from opacity to the text ramp: opacity fades a whole subtree and cannot
  // express "lighter" in a dark theme.
  .widget__card-tooltip {
    // value
    .tt-tier-1,
    .tt-stack-total {
      font-size: var(--inet-font-size-lg);
      font-weight: 600;
      color: var(--inet-text-color);
      opacity: 1;
    }

    // label
    .tt-tier-2 {
      font-size: var(--inet-font-size-base);
      font-weight: 400;
      color: var(--inet-text-muted-color);
      opacity: 1;
      margin-top: var(--inet-space-1);
    }

    // caption — tier 3 and every subtitle. A subtitle is the caption role distinguished by the
    // subtle text colour, not by a size of its own.
    .tt-tier-3,
    .tt-subtitle,
    .tt-tier-1.tt-subtitle,
    .tt-tier-2.tt-subtitle {
      font-size: var(--inet-feedback-font-size);
      font-weight: 400;
      color: var(--inet-text-subtle-color);
      opacity: 1;
      letter-spacing: normal;
      margin-top: var(--inet-space-1);
    }

    // Between sections; within a section stays at --inet-space-1 from the role rules above.
    .tt-section,
    .tt-tier-1:not(:first-child) {
      margin-top: var(--inet-space-4);
    }

    .tt-section.tt-section-first {
      margin-top: var(--inet-space-1);
    }

    // The total earns emphasis from a hairline rule and its position rather than from being the
    // largest text on screen. Accepted trade: it is quieter than the old 20px.
    .tt-stack-total {
      margin-top: var(--inet-space-4);
      padding-top: var(--inet-space-3);
      border-top: 1px solid var(--inet-default-border-color);
    }
  }
```

The `opacity: 1` and `letter-spacing: normal` resets are required because the ungated rules still set
`0.9` / `0.7` / `0.65` and `0.02em`; the gated block must override them, not sit beside them.

- [ ] **Step 4: Confirm the tailed variant still has no border, fill or shadow**

`.widget__card-tooltip--tailed` (`_directives.scss:287`) zeroes `background-color`, `border-color` and
`box-shadow` because the SVG tail chrome draws the outline instead. Task 5 added a gated `box-shadow` to
`.widget__card-tooltip`, which has **lower specificity than nothing** — both are single classes, and the
gated rule is a descendant of `.viz-modern`, so it wins. Add inside the `.viz-modern` block:

```scss
  // The tail's SVG chrome draws the outline, so the tailed card must stay flat even under the
  // gate — the gated .widget__card-tooltip shadow above would otherwise win on specificity.
  .widget__card-tooltip--tailed {
    background-color: transparent;
    border-color: transparent;
    box-shadow: none;
  }
```

- [ ] **Step 5: Verify the deleted sizes are gone under the gate and intact without it**

Run:
```bash
cd community/web && npx sass --load-path=projects/portal/src/scss projects/portal/src/scss/internal/_directives.scss /tmp/ramp.css --style=expanded && grep -n "font-size: 14px\|font-size: 12px\|font-size: 20px" /tmp/ramp.css
```
Expected: those three sizes still appear in the **ungated** `.widget__card-tooltip` block (gate-off
behaviour is unchanged) and are overridden inside `.viz-modern`. Confirm by eye that every `.viz-modern`
card-tooltip declaration uses a token, not a literal.

- [ ] **Step 6: Lint**

Run: `cd community/web && npm run lint`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add community/web/projects/portal/src/scss/internal/_directives.scss
git commit -m "feat(shell): rebuild the card tooltip ramp as three roles

Shell surfaces ticket item 4, decided 2026-08-01. Six sizes
(16/14/13/12/11/20) collapse to three roles — value 16, label 13, caption 11 —
with the --inet-text-* ramp carrying de-emphasis instead of opacity, and the
stack total separated by a hairline rule rather than enlarged to 20px.

Subtitles stop being a size and become the caption role. The combined-card
.tt-section grouping is preserved; only the type it carries changes. Subsumes
item 2 (tier opacities).

Gated behind .viz-modern."
```

---

## Task 7: Bind the pop scrim colour under the gate (item 12, gated)

**Files:**
- Modify: `web/projects/portal/src/app/vsobjects/objects/data-tip/date-tip-helper.ts`
- Modify: `web/projects/portal/src/app/vsobjects/objects/data-tip/date-tip-helper.spec.ts`

**Interfaces:**
- Consumes: `GuiTool.isVizModern()` from `app/common/util/gui-tool.ts:65`; `DateTipHelper` from Task 3.
- Produces: `DateTipHelper.popDimColor` keeps its existing getter shape (`static get popDimColor(): string`) and its single consumer at `vs-object-container.component.ts:750`. No call site changes.

The scrim is painted into a canvas via `context.fillStyle`, so it cannot be a CSS variable reference —
the gate has to be read in TypeScript and a literal returned.

- [ ] **Step 1: Write the failing test**

Append to `date-tip-helper.spec.ts`:

```ts
describe("DateTipHelper scrim colour", () => {
   afterEach(() => document.body.classList.remove("viz-modern"));

   it("uses the legacy 0.2 scrim when the modern gate is off", () => {
      expect(DateTipHelper.popDimColor).toBe("rgba(0, 0, 0, 0.2)");
   });

   it("uses the shipped 0.3 overlay scrim when the modern gate is on", () => {
      document.body.classList.add("viz-modern");
      expect(DateTipHelper.popDimColor).toBe("rgba(0, 0, 0, 0.3)");
   });
});
```

- [ ] **Step 2: Run it to verify the second test fails**

Run: `cd community/web && npm run test:portal -- --run date-tip-helper`
Expected: the gate-off test PASSES, the gate-on test FAILS with
`expected 'rgba(0, 0, 0, 0.2)' to be 'rgba(0, 0, 0, 0.3)'`.

- [ ] **Step 3: Implement the gated getter**

Add the import and replace the getter:

```ts
import { GuiTool } from "../../../common/util/gui-tool";
```

```ts
// The system ships --inet-overlay-scrim-bg-color at rgba(0,0,0,0.3); this layer had its own 0.2
// for the same job. The scrim is painted into a canvas via fillStyle, so the token cannot be
// referenced — the value is mirrored here and gated in TS. Keep both in step.
const POP_DIM_COLOR: string = "rgba(0, 0, 0, 0.2)";
const POP_DIM_COLOR_MODERN: string = "rgba(0, 0, 0, 0.3)";
```

```ts
   public static get popDimColor() {
      return GuiTool.isVizModern() ? POP_DIM_COLOR_MODERN : POP_DIM_COLOR;
   }
```

- [ ] **Step 4: Run the tests to verify both pass**

Run: `cd community/web && npm run test:portal -- --run date-tip-helper`
Expected: PASS, all four tests in the file.

- [ ] **Step 5: Check for a circular import**

Run: `cd community/web && npm run lint`
Expected: PASS. `GuiTool` is a leaf utility (`common/util/`), so importing it into `data-tip/` should not
cycle. If the linter reports a cycle, read the gate directly —
`document.body.classList.contains("viz-modern")` — and comment why.

- [ ] **Step 6: Commit**

```bash
git add community/web/projects/portal/src/app/vsobjects/objects/data-tip/date-tip-helper.ts \
        community/web/projects/portal/src/app/vsobjects/objects/data-tip/date-tip-helper.spec.ts
git commit -m "feat(shell): bind the data-tip scrim to the shipped overlay value under the gate

Shell surfaces ticket item 12. Two scrims existed for one job, 0.1 apart —
POP_DIM_COLOR at 0.2 against --inet-overlay-scrim-bg-color at 0.3. The scrim is
canvas-painted so the token cannot be referenced; the value is mirrored and
gated in TypeScript.

Gated behind .viz-modern: 0.2 to 0.3 is a visible change in dimming."
```

---

## Task 8: Verification pass

**Files:** none modified.

**Interfaces:**
- Consumes: every preceding task.
- Produces: a filled-in result for each row, recorded in the PR description.

No code changes. This task exists because six of the seven preceding tasks cannot be proven by unit
test, and because the ticket's whole point is that these classes reach far beyond charts.

- [ ] **Step 1: Run the full frontend suite**

Run:
```bash
cd community/web && npm run test:portal && npm run test:portal:tl && npm run lint
```
Expected: PASS. Record any pre-existing failures separately — `main` is trunk-based and some tests are
known unstable, so compare against a clean checkout before attributing a failure to this PR.

- [ ] **Step 2: Build and start the app**

Run: `cd community/web && npm run build` then start the server per `CLAUDE.md` (`docker compose up -d`
from `docker/target/docker-test`, or the enterprise equivalent). Log in as admin.

- [ ] **Step 3: Turn the gate ON and walk the matrix**

Set `viewsheet.modernVisualization = true` for the org (EM → Properties, per
`VSDensityDefaults.isModern()` reading `SreeEnv`). Confirm `document.body` carries `viz-modern`.

| Check | Expect |
|---|---|
| Hover a cell in a **table** | tooltip has the overlay shadow, 2px radius, 13px text |
| Hover an item in a **selection list** | same treatment — this is the reach the shell/chart split exists for |
| Hover a **range slider** handle, a **gauge**, a **text** assembly | same treatment |
| Hover a chart data point with `tooltipStyle: "CARD"` | three type sizes only; no 14/12/20px; total separated by a hairline rule, not enlarged |
| Hover a CARD tooltip that draws a **tail** | still flat — no border, fill or shadow behind the tail |
| Hover a **hidden annotation** | 2px radius, 13px text, **no** 40vw cap introduced |
| Open a **data tip**, then open a dropdown inside it | dropdown renders above the data tip, as before |
| Open a **drill tip** and a **date-comparison tip** | both still stack above the plot |
| Open a data tip | scrim visibly dims at 0.3 |
| Repeat the CARD tooltip check in **dark mode** (`viewsheet.darkMode = true`) | muted and subtle text get *lighter*, not more transparent — the reason opacity was dropped |

- [ ] **Step 4: Turn the gate OFF and confirm only three things changed**

Set `viewsheet.modernVisualization = false`. Confirm `viz-modern` is off the body.

| Check | Expect |
|---|---|
| Every tooltip surface | **unchanged** — 1px radius, 12px text, `--inet-shadow-low` |
| CARD tooltip | **unchanged** — all six sizes, opacities intact |
| Data tip scrim | **unchanged** at 0.2 |
| Data tip / dropdown / tooltip stacking | **unchanged** relative order |
| Hidden annotation tooltip | **unchanged**, and still uncapped |

This is the pass that protects existing customers. If anything in this table moved, a gated rule leaked.

- [ ] **Step 5: Record results and open the PR**

Community-only PR. Reference the source ticket items in the description: `Shell surfaces - ticket.md`
items 6, 12, 1, 3, 5, 4, and note the two source-doc corrections from this plan's header so the reviewer
is not confused by the ticket saying otherwise.

```bash
git push -u origin feature-74519-shell
```

Do **not** open the enterprise PR — every file here is under `community/`.

---

## Self-review notes

- **Spec coverage.** Design §3.2 items 6, 12 (×3 parts), 1, 3, 5, 4 → Tasks 1, 2/3/7, 5, 5, 5, 6. Design
  §2.4's `--inet-font-size-lg` → Task 4. Design §3.3's three hazards → Task 3's ordering test, Task 6
  Step 4 (tailed variant), Task 8 Step 3 (12-consumer reach). Design §6 → Task 8.
- **Deliberately not covered here:** design §3.4 (the `margin: 3px` / `offsetTop = 15` clearance
  literals) is marked optional and not required by this slice.
- **Known gap accepted:** Tasks 1, 4, 5 and 6 have no unit test, for the reason given under File
  Structure. Task 8 is their gate.
