# Phase 9C Item 4 — Highlight Semantic Presets Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add one-click modern "Warning" and "Anomaly" color presets to the highlight authoring pane so users can reach the modern semantic colors easily; presets apply literal foreground+background colors that flow through the existing `HighlightGroup` path and are export-visible.

**Architecture:** Purely additive, community-only, client-side. The highlight authoring surface is the shared `HighlightPane` Angular component. We add a preset row inside its Attributes fieldset that, when clicked, writes both `foreground` and `background` onto the selected highlight via the component's existing setters. The row is shown only for non-chart highlights (charts have no background field) and only when the modern visualization gate (`.viz-modern` body class) is active. Gate detection is centralized in a new `GuiTool.isVizModern()` helper.

**Tech Stack:** Angular 21 (standalone components), TypeScript 5.9, SCSS, Vitest 4 (direct-instantiation unit tests).

## Global Constraints

- **Community-only change.** All edits are under `community/web/`. No enterprise, server, or Java changes. The enterprise repo only gets a submodule-pointer bump at PR time (per `CLAUDE.md` branching rules).
- **No forced default / zero export-impact when unused.** Presets are 100% user-applied. A highlight that the user never touches is byte-identical to today. There is no server default to modernize (`Highlight` fg/bg default to `null`).
- **Gate-scoped UI.** The preset row appears only when `document.body.classList.contains("viz-modern")` is true. Gate off ⇒ the highlight pane is byte-identical to today.
- **Non-chart only.** Presets are background-fill + matching-text pairs; the pane only shows Background when `!model.chartAssembly`. Chart highlights (foreground/Color only) do not get presets.
- **Exact modern semantic values** (source of truth: `visualization-palette-swatches.html`, matching the Phase 4 `--inet-viz-*` tokens):
  - Warning — foreground `#7A4E10`, background `#F8E8CC`
  - Anomaly — foreground `#7F2E2E`, background `#F7DEDE`
- **No comments in HTML template files.** Follow the existing code's comment density in `.ts`/`.scss` (short clauses only).
- **New tests go in a standard `*.spec.ts`** (runs in `npm run test:portal`, which is CI-wired), NOT `*.tl.spec.ts` (portal TL suite is not yet in CI per `CLAUDE.md`). Use direct component instantiation, matching the existing `highlight-pane.component.tl.spec.ts` `createPane` pattern, to avoid `FontPane`'s `GET /api/format/fonts` HTTP call.

---

## Ownership note (confirmed against the spec)

Phase 9C item 4's spec says "Confirm ownership vs. Phase 8 before shipping semantic color." Phase 8 owns the categorical chart palette and continuous chart color; it does **not** touch the highlight authoring UI or `HighlightGroup`. Highlight presets are unambiguously this item's scope. No coordination edit to Phase 8 artifacts is required.

## File Structure

| File | Responsibility | Action |
|---|---|---|
| `community/web/projects/portal/src/app/common/util/gui-tool.ts` | Centralize the live `.viz-modern` gate read in `isVizModern()`; reuse it from `getMiniToolbarHeight()`. | Modify |
| `community/web/projects/portal/src/app/common/util/gui-tool.spec.ts` | Unit-test `isVizModern()`. | Modify |
| `community/web/projects/portal/src/app/widget/highlight/highlight-pane.component.ts` | Add `semanticPresets` data, `showSemanticPresets` getter, `applyPreset()` method. | Modify |
| `community/web/projects/portal/src/app/widget/highlight/highlight-pane.component.spec.ts` | Unit-test the new getter + apply logic (new standard spec, CI-wired). | Create |
| `community/web/projects/portal/src/app/widget/highlight/highlight-pane.component.html` | Render the gated preset row inside the Attributes fieldset. | Modify |
| `community/web/projects/portal/src/app/widget/highlight/highlight-pane.component.scss` | Style the preset row and swatch buttons. | Modify |

---

### Task 1: `GuiTool.isVizModern()` gate helper

Centralize the live modern-gate read so the highlight pane and the existing mini-toolbar height logic share one tested seam (DRY). Behavior of `getMiniToolbarHeight()` is unchanged — this is a pure extraction.

**Files:**
- Modify: `community/web/projects/portal/src/app/common/util/gui-tool.ts:66-70`
- Test: `community/web/projects/portal/src/app/common/util/gui-tool.spec.ts`

**Interfaces:**
- Consumes: nothing (reads `document.body.classList`).
- Produces: `static GuiTool.isVizModern(): boolean` — returns `true` when the `viz-modern` class is on `document.body`. Consumed by Task 2.

- [ ] **Step 1: Write the failing test**

Append this block to `gui-tool.spec.ts` (after the existing `getMiniToolbarHeight` describe):

```ts
describe("GuiTool.isVizModern", () => {
   afterEach(() => {
      document.body.classList.remove("viz-modern");
   });

   it("returns false when the modern gate is off", () => {
      expect(GuiTool.isVizModern()).toBe(false);
   });

   it("returns true when .viz-modern is on the body", () => {
      document.body.classList.add("viz-modern");
      expect(GuiTool.isVizModern()).toBe(true);
   });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd community/web && npx ng test portal --include="projects/portal/src/app/common/util/gui-tool.spec.ts"`
Expected: FAIL — `GuiTool.isVizModern is not a function`.

- [ ] **Step 3: Write minimal implementation**

In `gui-tool.ts`, add the helper immediately above `getMiniToolbarHeight()` (around line 66) and refactor `getMiniToolbarHeight()` to call it:

```ts
   // Read the modern visualization gate live: the .viz-modern body class toggles at runtime.
   static isVizModern(): boolean {
      return document.body.classList.contains("viz-modern");
   }

   // The mini-toolbar is positioned by JS (mini-toolbar.component.ts topY), so the height it assumes
   // must match the rendered height.
   static getMiniToolbarHeight(): number {
      return GuiTool.isVizModern()
         ? GuiTool.MINI_TOOLBAR_HEIGHT_MODERN
         : GuiTool.MINI_TOOLBAR_HEIGHT;
   }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd community/web && npx ng test portal --include="projects/portal/src/app/common/util/gui-tool.spec.ts"`
Expected: PASS — all four tests (the two existing `getMiniToolbarHeight` tests still pass, confirming the extraction is behavior-preserving).

- [ ] **Step 5: Commit**

```bash
git add community/web/projects/portal/src/app/common/util/gui-tool.ts community/web/projects/portal/src/app/common/util/gui-tool.spec.ts
git commit -m "feat(viz-9c): add GuiTool.isVizModern() gate helper"
```

---

### Task 2: HighlightPane preset data, visibility getter, apply logic

Add the presets and the logic to the component. All behavior is in testable getters/methods so it is covered by a standard (CI-wired) spec via direct instantiation; the template (Task 3) is only a thin binding to these members.

**Files:**
- Modify: `community/web/projects/portal/src/app/widget/highlight/highlight-pane.component.ts`
- Test: `community/web/projects/portal/src/app/widget/highlight/highlight-pane.component.spec.ts` (create)

**Interfaces:**
- Consumes: `GuiTool.isVizModern()` (Task 1); existing `foreground`/`background` setters on `HighlightPane` (write to `selectedHighlight.foreground`/`.background`); `HighlightDialogModel.chartAssembly: boolean`.
- Produces (consumed by Task 3's template):
  - `interface SemanticPreset { label: string; foreground: string; background: string; }`
  - `readonly semanticPresets: SemanticPreset[]`
  - `get showSemanticPresets(): boolean`
  - `applyPreset(preset: SemanticPreset): void`

- [ ] **Step 1: Write the failing test**

Create `highlight-pane.component.spec.ts` with:

```ts
/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
import { afterEach, describe, expect, it, vi } from "vitest";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { HighlightDialogModel } from "./highlight-dialog-model";
import { HighlightModel } from "./highlight-model";
import { HighlightPane } from "./highlight-pane.component";

function highlight(name: string): HighlightModel {
   return { name, applyRow: false, vsConditionDialogModel: { conditionList: [] } } as HighlightModel;
}

function createModel(chartAssembly: boolean, highlights: HighlightModel[] = []): HighlightDialogModel {
   return { highlights, tableName: "T1", fields: [], chartAssembly } as HighlightDialogModel;
}

function createPane(model: HighlightDialogModel, selected: HighlightModel | null = null) {
   const modalService = { open: vi.fn() };
   const comp = new HighlightPane(modalService as unknown as NgbModal);
   comp.model = model;
   comp.selectedHighlight = selected ?? model.highlights[0] ?? null;
   return comp;
}

describe("HighlightPane — semantic presets", () => {
   afterEach(() => {
      document.body.classList.remove("viz-modern");
   });

   it("shows presets for a non-chart highlight when the modern gate is on", () => {
      document.body.classList.add("viz-modern");
      const comp = createPane(createModel(false, [highlight("h1")]));
      expect(comp.showSemanticPresets).toBe(true);
   });

   it("hides presets when the modern gate is off", () => {
      const comp = createPane(createModel(false, [highlight("h1")]));
      expect(comp.showSemanticPresets).toBe(false);
   });

   it("hides presets for a chart highlight even when the gate is on", () => {
      document.body.classList.add("viz-modern");
      const comp = createPane(createModel(true, [highlight("h1")]));
      expect(comp.showSemanticPresets).toBe(false);
   });

   it("applies both foreground and background of a preset to the selected highlight", () => {
      const h = highlight("h1");
      const comp = createPane(createModel(false, [h]), h);
      const warning = comp.semanticPresets.find((p) => p.label.includes("Warning"))!;

      comp.applyPreset(warning);

      expect(h.foreground).toBe("#7A4E10");
      expect(h.background).toBe("#F8E8CC");
   });

   it("carries the exact modern Warning and Anomaly values", () => {
      const comp = createPane(createModel(false));
      const [warning, anomaly] = comp.semanticPresets;

      expect(warning).toMatchObject({ foreground: "#7A4E10", background: "#F8E8CC" });
      expect(anomaly).toMatchObject({ foreground: "#7F2E2E", background: "#F7DEDE" });
   });

   it("is a no-op when no highlight is selected", () => {
      const comp = createPane(createModel(false, []), null);
      expect(() => comp.applyPreset(comp.semanticPresets[0])).not.toThrow();
      expect(comp.selectedHighlight).toBeNull();
   });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd community/web && npx ng test portal --include="projects/portal/src/app/widget/highlight/highlight-pane.component.spec.ts"`
Expected: FAIL — `comp.showSemanticPresets` / `comp.semanticPresets` / `comp.applyPreset` are undefined.

- [ ] **Step 3: Write minimal implementation**

In `highlight-pane.component.ts`:

Add the import near the other `common/util` import (`ComponentTool` is imported from `"../../common/util/component-tool"`):

```ts
import { GuiTool } from "../../common/util/gui-tool";
```

Add the interface just above the `@Component` decorator:

```ts
interface SemanticPreset {
   label: string;
   foreground: string;
   background: string;
}
```

Add these members to the `HighlightPane` class (e.g. after the `renameIndex` / `conditionsChanged` fields, before the constructor):

```ts
   readonly semanticPresets: SemanticPreset[] = [
      { label: "_#(Warning)", foreground: "#7A4E10", background: "#F8E8CC" },
      { label: "_#(Anomaly)", foreground: "#7F2E2E", background: "#F7DEDE" }
   ];

   // Modern semantic presets: non-chart highlights only (charts have no background), modern gate only.
   get showSemanticPresets(): boolean {
      return !!this.model && !this.model.chartAssembly && GuiTool.isVizModern();
   }

   applyPreset(preset: SemanticPreset): void {
      if(this.selectedHighlight) {
         this.foreground = preset.foreground;
         this.background = preset.background;
      }
   }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd community/web && npx ng test portal --include="projects/portal/src/app/widget/highlight/highlight-pane.component.spec.ts"`
Expected: PASS — all seven tests pass.

- [ ] **Step 5: Commit**

```bash
git add community/web/projects/portal/src/app/widget/highlight/highlight-pane.component.ts community/web/projects/portal/src/app/widget/highlight/highlight-pane.component.spec.ts
git commit -m "feat(viz-9c): add warning/anomaly highlight preset logic"
```

---

### Task 3: Render the preset row (template + styles)

Add the gated preset row to the Attributes fieldset and style it. The row sits below the Foreground/Background row and above the Font row. Because it lives inside `<fieldset [disabled]="!selectedHighlight">`, the preset buttons auto-disable when no highlight is selected (no extra guard needed in the template).

**Files:**
- Modify: `community/web/projects/portal/src/app/widget/highlight/highlight-pane.component.html:63-64` (insert between the color row and the font row, both inside the Attributes `<fieldset>`)
- Modify: `community/web/projects/portal/src/app/widget/highlight/highlight-pane.component.scss`

**Interfaces:**
- Consumes: `showSemanticPresets`, `semanticPresets`, `applyPreset()` (Task 2).
- Produces: nothing consumed downstream (leaf UI).

- [ ] **Step 1: Add the template row**

In `highlight-pane.component.html`, insert this block immediately after the closing `</div>` of the Foreground/Background `<div class="row shell-form-row--field">` (currently ending at line 63) and before the Font `<div class="row shell-form-row--field">` (currently line 64). Both stay inside the Attributes `<fieldset>`:

```html
        @if (showSemanticPresets) {
          <div class="row shell-form-row--field">
            <div class="col highlight-pane__presets">
              <span>_#(Presets):</span>
              @for (preset of semanticPresets; track preset.label) {
                <button type="button" class="btn highlight-pane__preset-btn"
                  [style.background-color]="preset.background" [style.color]="preset.foreground"
                  (click)="applyPreset(preset)">{{preset.label}}</button>
              }
            </div>
          </div>
        }
```

- [ ] **Step 2: Add the styles**

Append to `highlight-pane.component.scss` (tokens `--inet-space-3`, `--inet-radius-md`, `--inet-list-border-color` are already used in this file):

```scss
.highlight-pane__presets {
  align-items: center;
  display: flex;
  gap: var(--inet-space-3);
}

.highlight-pane__preset-btn {
  border: 1px solid var(--inet-list-border-color);
  border-radius: var(--inet-radius-md);
  cursor: pointer;
  padding: 0 var(--inet-space-3);
}
```

- [ ] **Step 3: Build and lint to verify the template compiles**

Run: `cd community/web && npm run build && npm run lint`
Expected: build succeeds (Angular template type-check passes — `showSemanticPresets`, `semanticPresets`, `applyPreset` all resolve on the component); lint clean.

- [ ] **Step 4: Re-run the component unit tests (regression check)**

Run: `cd community/web && npx ng test portal --include="projects/portal/src/app/widget/highlight/highlight-pane.component.spec.ts"`
Expected: PASS — all seven tests still pass (template change does not affect the logic layer).

- [ ] **Step 5: Commit**

```bash
git add community/web/projects/portal/src/app/widget/highlight/highlight-pane.component.html community/web/projects/portal/src/app/widget/highlight/highlight-pane.component.scss
git commit -m "feat(viz-9c): render gated warning/anomaly highlight preset row"
```

---

## Manual validation (owner-run, after the three tasks)

These are the human validation checks; they are not automated in this plan.

1. **Gate off:** open the highlight dialog on a viewsheet table (org not in modern mode). The Attributes section is unchanged — no preset row. ✓
2. **Gate on, table:** with the org in modern visualization mode, open the highlight dialog on a table/crosstab cell. A "Presets: [Warning] [Anomaly]" row appears; each button previews its own colors (amber / red). Selecting a highlight and clicking **Warning** sets Foreground `#7A4E10` and Background `#F8E8CC`; clicking **Anomaly** sets `#7F2E2E` / `#F7DEDE`. ✓
3. **No highlight selected:** the preset buttons are disabled (inherited from the Attributes fieldset). ✓
4. **Chart highlight:** open the highlight dialog on a chart — no preset row (chart shows Color/foreground only). ✓
5. **Export parity:** apply a Warning preset, commit the dialog, then export the viewsheet to PDF/Excel. The highlighted cell renders the amber fill in export (the preset flows through the existing `HighlightGroup` server path — nothing new server-side). ✓

## Sequencing / dependencies

- Task 1 → Task 2 (Task 2 imports `GuiTool.isVizModern()`).
- Task 2 → Task 3 (template binds Task 2's members).
- Independent of Phase 9C items 1/2/3.

## Branching (per CLAUDE.md)

Community-only change. Continue on the visualization-theming branch used by the other Phase 9C items (confirm `epic-74519` vs a child `feature-{issue}` / `bug-{issue}` branch with the initiative owner). Enterprise repo gets a submodule-pointer bump only at PR time. Nothing committed to `main` or a `v1.0.x`/`v1.1.x` release branch; nothing pushed or PR'd without explicit approval.

## Related

- [visualization-phase9c-deferred-consolidation-implementation-plan.md](../specs/lookfeel/visualization-phase9c-deferred-consolidation-implementation-plan.md) — item 4 (this plan's spec)
- [visualization-phase4-implementation-plan.md](../specs/lookfeel/visualization-phase4-implementation-plan.md) — warning/anomaly token definitions + render-location routing (defined-only in Phase 4; adopted here)
- [visualization-palette-swatches.html](../specs/lookfeel/visualization-palette-swatches.html) — modern semantic color source of truth
- Sibling plans: [item 1 (mini-toolbar)](2026-07-23-viz-phase9c-item1-mini-toolbar-compaction.md), [item 3 (subtotal emphasis)](2026-07-23-viz-phase9c-item3-group-subtotal-emphasis.md)
