# Chart Mini-Toolbar Compaction Under Modern Density — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Under the modern visualization gate, render the chart/object mini-toolbar at a compact
height (28px → 24px) with its JS positioning geometry kept in sync, so the toolbar sits tight above
the object instead of overlapping the title (too tall) or leaving a gap (too short).

**Architecture:** The mini-toolbar is absolutely positioned by JS: `topY = top − MINI_TOOLBAR_HEIGHT − adj`
(`mini-toolbar.component.ts:259-269`), where `MINI_TOOLBAR_HEIGHT` is a hardcoded `28`
(`gui-tool.ts:58`). Two prior CSS-only attempts (Phase 6 Part A) failed because a CSS resize desyncs
this constant. The fix makes the height a single gate-aware source of truth in `GuiTool`, forces the
rendered height to match it via a `.viz-modern`-scoped CSS rule that pins the container height, and
repoints every consumer of the constant to the gate-aware accessor. Width and horizontal alignment
(`getActionsWidth` / `alignLeft`) are deliberately **not** touched, so no horizontal misalignment is
possible — this is the height-only slice.

**Tech Stack:** Angular 21 / TypeScript (portal project), SCSS with emulated view encapsulation
(`:host-context`), Vitest 4 (`npx vitest run <spec>` from `web/`).

## Global Constraints

- **Gate-off byte-identical.** With `.viz-modern` absent, every computed style and every JS value must
  be identical to today. Achieved by adding only `:host-context(.viz-modern)` CSS and a gate-conditional
  branch that returns the existing `28` when the gate is off. Never edit the legacy default rules.
- **Browser-DOM only, never exported.** The mini-toolbar is Angular-rendered chrome; it is not part of
  any viewsheet export. No server/Java/`VSFormat` change is in scope. It correctly rides the browser
  `.viz-modern` body class, not a server resolver.
- **The gate signal is the body class.** `document.body` carries `viz-modern` (and
  `viz-density-<comfortable|compact|dense>`), applied in `portal/app.component.ts:262-270`,
  `composer/app.component.ts`, and toggled live at `viewer-app.component.ts:2785`. Read it live each
  call (do not memoize — it changes at runtime when an admin toggles modern mode).
- **Height-only scope.** Compact = 24px height. Horizontal button width, `getActionsWidth`, and
  `alignLeft` are out of scope (see Deferred sub-items). The 24px value is a proposed design default;
  it is defined in one place (`GuiTool.MINI_TOOLBAR_HEIGHT_MODERN`) and one CSS rule, so the owner can
  retune it in two coupled spots.
- **Keep JS and CSS coupled.** The JS constant and the CSS container height MUST hold the same pixel
  value. The CSS pins `height` explicitly (not padding-derived) so the rendered footprint equals the
  constant deterministically.

---

## File Structure

- `projects/portal/src/app/common/util/gui-tool.ts` — add `MINI_TOOLBAR_HEIGHT_MODERN` + a gate-aware
  `getMiniToolbarHeight()` accessor. Single source of truth for the height.
- `projects/portal/src/app/common/util/gui-tool.spec.ts` — **new** unit test for the accessor.
- `projects/portal/src/app/vsobjects/objects/mini-toolbar/mini-toolbar.component.ts` — repoint the
  `miniToolbarHeight` getter to the accessor (drives `topY`).
- `projects/portal/src/app/vsobjects/objects/mini-toolbar/mini-toolbar.component.scss` — add the
  `:host-context(.viz-modern)` rule pinning the container to 24px.
- `projects/portal/src/app/vsobjects/objects/mini-toolbar/mini-toolbar.component.spec.ts` — **new**
  unit test for `topY` under both gate states.
- `projects/portal/src/app/vsobjects/objects/data-tip/vs-data-tip.directive.ts` — repoint the constant
  (line 176) to the accessor.
- `projects/portal/src/app/vsobjects/objects/data-tip/vs-pop-component.directive.ts` — repoint the
  constant (line 296) to the accessor.
- `projects/portal/src/app/binding/editor/binding-editor.component.ts` — repoint the
  `miniToolbarHeight` getter (line 223-225) to the accessor.
- `projects/portal/src/app/binding/editor/binding-editor.component.display.tl.spec.ts` — extend the
  existing `miniToolbarHeight` group with a gate-on case.

---

## Task 1: Gate-aware height accessor in GuiTool

**Files:**
- Modify: `projects/portal/src/app/common/util/gui-tool.ts:58`
- Test: `projects/portal/src/app/common/util/gui-tool.spec.ts` (create)

**Interfaces:**
- Produces: `GuiTool.MINI_TOOLBAR_HEIGHT_MODERN: number` (= 24), and
  `GuiTool.getMiniToolbarHeight(): number` — returns 24 when `document.body` has class `viz-modern`,
  else the existing `GuiTool.MINI_TOOLBAR_HEIGHT` (28). Consumed by Tasks 2, 3, 4.

- [ ] **Step 1: Write the failing test**

Create `projects/portal/src/app/common/util/gui-tool.spec.ts`:

```typescript
import { afterEach, describe, expect, it } from "vitest";
import { GuiTool } from "./gui-tool";

describe("GuiTool.getMiniToolbarHeight", () => {
   afterEach(() => {
      document.body.classList.remove("viz-modern");
   });

   it("returns the legacy height (28) when the modern gate is off", () => {
      expect(GuiTool.getMiniToolbarHeight()).toBe(GuiTool.MINI_TOOLBAR_HEIGHT);
      expect(GuiTool.getMiniToolbarHeight()).toBe(28);
   });

   it("returns the compact height (24) when .viz-modern is on the body", () => {
      document.body.classList.add("viz-modern");
      expect(GuiTool.getMiniToolbarHeight()).toBe(GuiTool.MINI_TOOLBAR_HEIGHT_MODERN);
      expect(GuiTool.getMiniToolbarHeight()).toBe(24);
   });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd web && npx vitest run projects/portal/src/app/common/util/gui-tool.spec.ts`
Expected: FAIL — `GuiTool.getMiniToolbarHeight is not a function` (and `MINI_TOOLBAR_HEIGHT_MODERN` undefined).

- [ ] **Step 3: Write minimal implementation**

In `gui-tool.ts`, replace the single constant line (`:58`):

```typescript
   static readonly MINI_TOOLBAR_HEIGHT = 28;
```

with the constant plus the modern value and accessor:

```typescript
   static readonly MINI_TOOLBAR_HEIGHT = 28;

   // Compact mini-toolbar footprint under the modern visualization gate. Coupled to the pinned
   // container height in mini-toolbar.component.scss (:host-context(.viz-modern)); change both together.
   static readonly MINI_TOOLBAR_HEIGHT_MODERN = 24;

   // The mini-toolbar is positioned by JS (mini-toolbar.component.ts topY), so the height it assumes
   // must match the rendered height. Read the gate live: the .viz-modern body class toggles at runtime.
   static getMiniToolbarHeight(): number {
      return document.body.classList.contains("viz-modern")
         ? GuiTool.MINI_TOOLBAR_HEIGHT_MODERN
         : GuiTool.MINI_TOOLBAR_HEIGHT;
   }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd web && npx vitest run projects/portal/src/app/common/util/gui-tool.spec.ts`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add projects/portal/src/app/common/util/gui-tool.ts projects/portal/src/app/common/util/gui-tool.spec.ts
git commit -m "Add gate-aware mini-toolbar height accessor (viz Phase 9C item 1)"
```

---

## Task 2: Mini-toolbar uses the accessor + compact CSS

**Files:**
- Modify: `projects/portal/src/app/vsobjects/objects/mini-toolbar/mini-toolbar.component.ts:130-132`
- Modify: `projects/portal/src/app/vsobjects/objects/mini-toolbar/mini-toolbar.component.scss`
- Test: `projects/portal/src/app/vsobjects/objects/mini-toolbar/mini-toolbar.component.spec.ts` (create)

**Interfaces:**
- Consumes: `GuiTool.getMiniToolbarHeight()` from Task 1.
- Produces: `MiniToolbar.topY` = `top − getMiniToolbarHeight() − adj` when `top > 20 || forceAbove`.

- [ ] **Step 1: Write the failing test**

Create `projects/portal/src/app/vsobjects/objects/mini-toolbar/mini-toolbar.component.spec.ts`:

```typescript
import { afterEach, describe, expect, it } from "vitest";
import { MiniToolbar } from "./mini-toolbar.component";

// The topY math is pure and depends only on `top`, the context flags, and the pop-component check,
// so we construct the component directly with light mocks rather than through TestBed.
function makeToolbar(): MiniToolbar {
   const contextProvider: any = { composer: false, vsWizard: false, binding: false };
   const element: any = { nativeElement: {} };
   const miniToolbarService: any = {};
   const popComponentService: any = { isPopComponentShow: () => false };
   const comp = new MiniToolbar(contextProvider, element, miniToolbarService, popComponentService);
   comp.assembly = "Chart1";
   return comp;
}

describe("MiniToolbar.topY", () => {
   afterEach(() => {
      document.body.classList.remove("viz-modern");
   });

   it("positions the toolbar 28px above the object when the gate is off", () => {
      const comp = makeToolbar();
      comp.top = 100;
      expect(comp.topY).toBe(72); // 100 - 28 - 0
   });

   it("positions the toolbar 24px above the object under .viz-modern", () => {
      document.body.classList.add("viz-modern");
      const comp = makeToolbar();
      comp.top = 100;
      expect(comp.topY).toBe(76); // 100 - 24 - 0
   });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd web && npx vitest run projects/portal/src/app/vsobjects/objects/mini-toolbar/mini-toolbar.component.spec.ts`
Expected: FAIL — the gate-on case returns 72 (still subtracts the hardcoded 28), not 76.

- [ ] **Step 3: Repoint the getter**

In `mini-toolbar.component.ts`, replace the `miniToolbarHeight` getter (`:130-132`):

```typescript
   get miniToolbarHeight(): number {
      return GuiTool.MINI_TOOLBAR_HEIGHT;
   }
```

with:

```typescript
   get miniToolbarHeight(): number {
      return GuiTool.getMiniToolbarHeight();
   }
```

(`GuiTool` is already imported at `:20`; `topY` at `:267` already reads `this.miniToolbarHeight`.)

- [ ] **Step 4: Add the compact CSS rule**

Append to `mini-toolbar.component.scss` (after the existing `.mini-toolbar-button-group` block). Pin
the container height so the rendered footprint deterministically equals 24px, matching the constant:

```scss
:host-context(.viz-modern) .mini-toolbar .mini-toolbar-container {
  height: 24px;

  button {
    height: 100%;
    padding-top: 0;
    padding-bottom: 0;
    display: inline-flex;
    align-items: center;
  }
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `cd web && npx vitest run projects/portal/src/app/vsobjects/objects/mini-toolbar/mini-toolbar.component.spec.ts`
Expected: PASS (2 tests).

- [ ] **Step 6: Verify gate-off is unchanged**

Run: `cd web && npx vitest run projects/portal/src/app/vsobjects/objects/mini-toolbar/mini-toolbar.component.spec.ts`
Confirm the first test (gate off → 72) still passes, proving no legacy regression. The SCSS change is
scoped to `:host-context(.viz-modern)` so gate-off computed styles are untouched by construction.

- [ ] **Step 7: Commit**

```bash
git add projects/portal/src/app/vsobjects/objects/mini-toolbar/mini-toolbar.component.ts \
        projects/portal/src/app/vsobjects/objects/mini-toolbar/mini-toolbar.component.scss \
        projects/portal/src/app/vsobjects/objects/mini-toolbar/mini-toolbar.component.spec.ts
git commit -m "Compact mini-toolbar height under modern gate (viz Phase 9C item 1)"
```

---

## Task 3: Sync data-tip and pop-component offsets

Both directives reserve space above a data-tip / popped component for the mini-toolbar by subtracting
the constant. If the toolbar is 24px under modern but they still subtract 28, a 4px gap opens. Repoint
both to the accessor. These are runtime viewer directives with heavy DI and no unit harness, so they
are verified by type-check + a documented live check rather than a new unit test.

**Files:**
- Modify: `projects/portal/src/app/vsobjects/objects/data-tip/vs-data-tip.directive.ts:176`
- Modify: `projects/portal/src/app/vsobjects/objects/data-tip/vs-pop-component.directive.ts:296`

**Interfaces:**
- Consumes: `GuiTool.getMiniToolbarHeight()` from Task 1.

- [ ] **Step 1: Repoint vs-data-tip.directive.ts**

Replace at `:176`:

```typescript
            if(this.miniToolbar) {
               top -= GuiTool.MINI_TOOLBAR_HEIGHT;
            }
```

with:

```typescript
            if(this.miniToolbar) {
               top -= GuiTool.getMiniToolbarHeight();
            }
```

- [ ] **Step 2: Repoint vs-pop-component.directive.ts**

Replace at `:296`:

```typescript
      if(this.miniToolbar) {
         top -= GuiTool.MINI_TOOLBAR_HEIGHT;
```

with:

```typescript
      if(this.miniToolbar) {
         top -= GuiTool.getMiniToolbarHeight();
```

(leave the rest of the block — `miniToolbarWidth` etc. — unchanged.)

- [ ] **Step 3: Type-check both directives compile**

Run: `cd web && npx tsc -p projects/portal/tsconfig.app.json --noEmit`
Expected: no new errors referencing the two directives. (`GuiTool` is already imported in both.)

- [ ] **Step 4: Documented live verification**

Build (`cd web && npm run build`) and open a viewsheet with a chart that has a data tip and a popped
component, with modern mode enabled (EM → Look and Feel → Modern Visualization on). Hover to reveal
the mini-toolbar and trigger a data tip / pop. Confirm: the toolbar sits tight above the object with
no gap or title overlap, and the data-tip/pop is offset by the same compact amount. Repeat with modern
off to confirm legacy spacing is unchanged.

- [ ] **Step 5: Commit**

```bash
git add projects/portal/src/app/vsobjects/objects/data-tip/vs-data-tip.directive.ts \
        projects/portal/src/app/vsobjects/objects/data-tip/vs-pop-component.directive.ts
git commit -m "Sync data-tip/pop offsets with compact mini-toolbar height (viz Phase 9C item 1)"
```

---

## Task 4: Binding-editor getter + extend its existing test

The binding editor exposes its own `miniToolbarHeight` getter for the preview layout, and it already
has a baseline test asserting `28`. Repoint the getter and extend the test with a gate-on case.

**Files:**
- Modify: `projects/portal/src/app/binding/editor/binding-editor.component.ts:223-225`
- Test: `projects/portal/src/app/binding/editor/binding-editor.component.display.tl.spec.ts:189-194`

**Interfaces:**
- Consumes: `GuiTool.getMiniToolbarHeight()` from Task 1.

- [ ] **Step 1: Write the failing test (extend the existing group)**

Replace the `Group 5` describe block (`:189-194`) with:

```typescript
describe("BindingEditor — miniToolbarHeight getter", () => {
   afterEach(() => {
      document.body.classList.remove("viz-modern");
   });

   it("should return GuiTool.MINI_TOOLBAR_HEIGHT (28)", async () => {
      const { comp } = await renderComponent();
      expect(comp.miniToolbarHeight).toBe(28);
   });

   it("should return the compact height (24) under .viz-modern", async () => {
      document.body.classList.add("viz-modern");
      const { comp } = await renderComponent();
      expect(comp.miniToolbarHeight).toBe(24);
   });
});
```

Confirm `afterEach` is imported from `vitest` at the top of the file; add it to the existing import if
it is not already present.

- [ ] **Step 2: Run test to verify the new case fails**

Run: `cd web && npx vitest run projects/portal/src/app/binding/editor/binding-editor.component.display.tl.spec.ts`
Expected: the gate-on case FAILS (getter still returns the hardcoded 28).

- [ ] **Step 3: Repoint the getter**

In `binding-editor.component.ts`, replace (`:223-225`):

```typescript
   get miniToolbarHeight(): number {
      return GuiTool.MINI_TOOLBAR_HEIGHT;
   }
```

with:

```typescript
   get miniToolbarHeight(): number {
      return GuiTool.getMiniToolbarHeight();
   }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd web && npx vitest run projects/portal/src/app/binding/editor/binding-editor.component.display.tl.spec.ts`
Expected: PASS (both the baseline 28 case and the new 24 case).

- [ ] **Step 5: Commit**

```bash
git add projects/portal/src/app/binding/editor/binding-editor.component.ts \
        projects/portal/src/app/binding/editor/binding-editor.component.display.tl.spec.ts
git commit -m "Binding-editor mini-toolbar height follows modern gate (viz Phase 9C item 1)"
```

---

## Deferred sub-items (out of scope for this plan)

- **Horizontal compaction.** Reducing button width / horizontal padding would require making
  `MiniToolbarService.getActionsWidth` (`mini-toolbar.service.ts:171-199`, whose `buttonPaddingRem`,
  `actionIconSize`, and `buttonBorder` mirror the CSS) gate-aware **and** repointing `alignLeft`
  (`mini-toolbar.component.ts:125-128`) and the container-width consumers
  (`abstract-vs-actions.ts:125/139/149`, `vs-object-container.ts:475-476`). That is the horizontal
  twin of this vertical fix and carries the alignment risk this plan avoids. Schedule separately if a
  narrower modern toolbar is wanted.
- **Per-density-mode granularity — DECIDED (2026-07-23): keep the single fixed compact height; do NOT
  track density.** The toolbar renders one compact size (24px container / 22px button / 38px width) under
  `.viz-modern` regardless of `viz-density-{comfortable,compact,dense}`. Rationale: (1) the design spec
  excludes small transient chrome ("legends, tooltips, or other small chrome fragments") from local
  density scaling — the hover-reveal mini-toolbar is exactly that; (2) a literal density map would set
  dense (the default modern mode) to `--inet-viz-row-height: 20px`, which clips the 18px `icon-size-small`
  glyph in a container with `overflow:hidden`, violating "dense controls must remain legible and hittable."
  The density token matrix (`_viz-tokens.scss:100-121`, 28/24/20) exists and could be wired cheaply if
  this is ever revisited, but the fixed size is the deliberate choice.

## Self-Review

- **Spec coverage.** Phase 9C item 1's blocker — "JS-computed geometry desyncs from a CSS-only resize"
  — is resolved for the vertical axis: the height lives in one gate-aware JS accessor (Task 1), the CSS
  pins the rendered height to match (Task 2), and all four constant consumers are repointed (Tasks 2–4).
  Horizontal geometry is explicitly deferred, not silently dropped.
- **Placeholder scan.** No TBD/TODO; every code step shows the exact before/after and the run command
  with expected result.
- **Type consistency.** `getMiniToolbarHeight()` and `MINI_TOOLBAR_HEIGHT_MODERN` are defined in Task 1
  and used verbatim in Tasks 2–4. `MINI_TOOLBAR_HEIGHT` (28) is retained, so the two unchanged consumers
  of the raw constant not in scope here (none remain after Tasks 2–4 for the mini-toolbar/data-tip/pop/
  binding paths) are covered; the constant stays exported for the accessor and any external reference.
- **Gate-off safety.** Every behavioral change is guarded by the `.viz-modern` body class (JS branch +
  `:host-context` CSS), so gate-off is byte-identical.
