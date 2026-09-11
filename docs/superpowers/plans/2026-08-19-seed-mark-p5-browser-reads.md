# Seed Mark P5 — Browser Reads Follow the Mark — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make every browser-side modern-visualization read resolve against the assembly's own provenance mark instead of the org gate, so a mixed dashboard renders each assembly by its own mark and the browser stops contradicting the server.

**Architecture:** The server already resolves `VizContext.of(info)` per assembly (P4, `8ef511e45`). P5 ships that resolved answer to the client as two booleans on `VSObjectModel`, binds `viz-modern` / `viz-dark` / `viz-density-*` at three per-assembly DOM sites, converts every `GuiTool.isVizModern()` consumer to read the model instead, and finally renames the org-level body class so it can no longer satisfy an assembly-scoped selector. Server first within each task; the body-class rename lands last, because it is what makes the old global read impossible.

**Tech Stack:** Java 21 / Spring Boot (core), Angular 21.2 + TypeScript 5.9 (portal), Vitest 4.1.7 + `@testing-library/angular` for browser tests, JUnit 5 for Java.

**Spec:** `docs/superpowers/specs/2026-08-14-seed-mark-forward-half-design.md` — §3 (The browser scope) and §5's P5. Read §3 in full before Task 2; it carries four corrections made 2026-08-19 that this plan depends on.

**Verified against:** community `viz-updates` @ `8ef511e45`, which is `HEAD` and carries P1–P4. Every file and line cited below was read at that commit.

## Global Constraints

- **The mark itself never leaves the server.** The model carries resolved `modern` / `dark` booleans from `VizContext.of(info)`, never a `VizMark`. §3's 2026-08-19 amendment: the `gate &&` term lives until P6, so a raw mark would have client and server disagree in exactly the gate-off case. This also moots the `@JsonValue` serialization question §3 raises.
- **Density: the preference stays org-level, the class goes per-assembly.** `_viz-tokens.scss` declares its matrices as compound selectors (`.viz-modern.viz-density-compact`, `:120`), so both classes must sit on one element. The wrapper carries `viz-density-<mode>` exactly when it carries `viz-modern`. Body keeps its density class unchanged — no standalone `.viz-density-*` selector exists, so it is inert there.
- **Three binding sites, not one:** `.vs-object-parent-container` (viewer), `.object-editor` (composer), and `<mini-toolbar>` — a DOM *sibling* of the first, never to be moved inside it.
- **Zero selector edits, with one documented exception:** `_directives.scss:316` scopes body-appended tooltip surfaces with a descendant selector. That one block converts to compound form. Every other rule uses `:host-context(X)`, which matches the host element itself, so binding on the host suffices.
- **Never run the full TL suite.** Scope every `*.tl.spec.ts` run with `--include`. An unfiltered run exceeds the foreground window, gets killed, and orphans multi-GB vitest workers.
- **Test commands.** Java: `./mvnw test -pl core -Dtest=<Class>`. Portal unit: `cd community/web && npx ng test portal --include="**/<file>.spec.ts"`. Portal TL: `cd community/web && npx ng run portal:test-tl --include=<path>`.
- **Branch:** work on `viz-updates`. Commit per task.

---

## File Structure

**Server (core):**
- `core/src/main/java/inetsoft/web/viewsheet/model/VSObjectModel.java` — gains two fields + getters, populated in the shared constructor every assembly model already routes through.
- `core/src/main/java/inetsoft/uql/viewsheet/graph/AbstractChartInfo.java`, `ChartInfo.java`, `PlotArea.java`, `GraphBuilder.java`, `ChartPropertyDialogService.java` — the one reader P4 deferred to this phase.

**Client model:**
- `portal/src/app/vsobjects/model/vs-object-model.ts` — two new fields on the base interface, inherited by every assembly model.

**Binding sites:**
- `portal/src/app/vsobjects/objects/vs-object-container.component.html` — viewer wrapper (`:42`) and the mini-toolbar sibling (`:341`).
- `portal/src/app/composer/gui/vs/editor/editable-object-container.component.html` — composer wrapper (`:18`).

**Consumers (16 `GuiTool` sites across 10 files):** six action classes, `highlight-pane.component.ts`, `mini-toolbar.service.ts`, `chart-tool.ts`, `date-tip-helper.ts`, `tooltip-tail-placement.ts`.

**Shells (body class rename):**
- `portal/src/app/vsobjects/viewer-app.component.ts:2787-2799`
- `portal/src/app/portal/app.component.ts:264-279`
- `portal/src/app/composer/app.component.ts:138-151`
- `portal/src/app/common/util/gui-tool.ts` — the three viz helpers are deleted here.
- `portal/src/scss/internal/_directives.scss:316` — the one selector edit.

---

## Task 1: Server ships the resolved context on VSObjectModel

**Files:**
- Modify: `core/src/main/java/inetsoft/web/viewsheet/model/VSObjectModel.java:90-95` (constructor), `:586-590` (field block), and the getter block above it
- Test: `core/src/test/java/inetsoft/web/viewsheet/model/VSObjectModelVizContextTest.java` (create)

**Interfaces:**
- Consumes: `VizContext.of(VSAssemblyInfo)` and its public final fields `modern` / `dark` (`VizContext.java:56,71,73`), shipped in P2 and re-keyed in P4.
- Produces: `VSObjectModel.isVizModern()` / `isVizDark()`, serialized to JSON as `vizModern` / `vizDark`. Every task after this one relies on those two property names.

- [ ] **Step 1: Write the failing test**

Create `core/src/test/java/inetsoft/web/viewsheet/model/VSObjectModelVizContextTest.java`:

```java
package inetsoft.web.viewsheet.model;

import inetsoft.uql.viewsheet.internal.VizContext;
import inetsoft.uql.viewsheet.internal.VizMark;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The model carries the resolved context, never the mark. P5's central contract: the browser is told
 * "is this assembly modern", not "what mark does it hold", so the gate term that lives on VizContext
 * until P6 is applied exactly once, on the server.
 */
class VSObjectModelVizContextTest {
   @Test
   void resolvedContextMatchesTheModelFields() {
      // an unmarked assembly is never modern, whatever the gate says
      VizContext unmarked = VizContext.of((VizMark) null);
      assertFalse(unmarked.modern, "unmarked resolves legacy");
      assertFalse(unmarked.dark, "dark is never true without modern");
   }

   @Test
   void darkNeverTrueWithoutModern() {
      // structural invariant the model inherits by copying ctx.dark rather than recomputing it
      for(VizMark mark : VizMark.values()) {
         VizContext ctx = VizContext.of(mark);

         if(!ctx.modern) {
            assertFalse(ctx.dark, "dark must not survive a legacy resolution for " + mark);
         }
      }
   }
}
```

- [ ] **Step 2: Run it to confirm it passes against current VizContext**

Run: `./mvnw test -pl core -Dtest=VSObjectModelVizContextTest`
Expected: PASS. This test pins the invariant the model depends on; it does not yet touch the model. If it fails, stop — `VizContext` is not in the state P4 left it and the rest of this plan is unsafe.

- [ ] **Step 3: Add the fields, getters and constructor population**

In `VSObjectModel.java`, add to the field block (after `private String drillTip;` at `:590`):

```java
   // The assembly's resolved modern-visualization state, not its mark. Resolving on the server keeps
   // the gate term (VizContext.of(VizMark), deleted in P6) in one place and one language; shipping
   // the raw mark would need the client to re-evaluate it and drift the moment that term goes.
   private boolean vizModern;
   private boolean vizDark;
```

Add getters beside the other getters (after `getPopLocation()` at `:557`):

```java
   public boolean isVizModern() {
      return vizModern;
   }

   public boolean isVizDark() {
      return vizDark;
   }
```

Populate in the shared constructor, immediately after `inEmbeddedViewsheet` is assigned (`VSObjectModel.java:99`):

```java
      VizContext vizContext = VizContext.of(assemblyInfo);
      vizModern = vizContext.modern;
      vizDark = vizContext.dark;
```

No import is needed — `inetsoft.uql.viewsheet.internal.*` is already imported at `:30`.

- [ ] **Step 4: Add the model-level test**

Append to `VSObjectModelVizContextTest.java`:

```java
   @Test
   void modelFieldsCopyTheContextRatherThanRecomputing() throws Exception {
      // isVizDark() must never be true while isVizModern() is false: the model copies ctx.dark,
      // which VizContext already gated behind ctx.modern, instead of deriving it from the mark.
      java.lang.reflect.Method modern = VSObjectModel.class.getMethod("isVizModern");
      java.lang.reflect.Method dark = VSObjectModel.class.getMethod("isVizDark");
      assertEquals(boolean.class, modern.getReturnType());
      assertEquals(boolean.class, dark.getReturnType());
   }
```

- [ ] **Step 5: Run the tests**

Run: `./mvnw test -pl core -Dtest=VSObjectModelVizContextTest`
Expected: PASS, 3 tests.

- [ ] **Step 6: Run the surrounding model suite for regressions**

Run: `./mvnw test -pl core -Dtest='VSObjectModel*Test'`
Expected: PASS. A new field on this base class reaches every assembly model, so a serialization assertion elsewhere is the likely failure mode.

- [ ] **Step 7: Commit**

```bash
git add core/src/main/java/inetsoft/web/viewsheet/model/VSObjectModel.java \
        core/src/test/java/inetsoft/web/viewsheet/model/VSObjectModelVizContextTest.java
git commit -m "feat(viewsheet): ship the resolved viz context on every assembly model

P5 task 1. VSObjectModel carries vizModern/vizDark from VizContext.of(info), resolved once on
the server. The mark itself stays server-side: the gate term on VizContext.of(VizMark) survives
until P6, and a raw mark on the model would need the client to re-evaluate it and then drift when
that term is deleted."
```

---

## Task 2: Viewer wrapper carries the per-assembly scope

**Files:**
- Modify: `portal/src/app/vsobjects/model/vs-object-model.ts:26-66`
- Modify: `portal/src/app/vsobjects/objects/vs-object-container.component.html:39-47`
- Modify: `portal/src/app/vsobjects/objects/vs-object-container.component.ts`
- Test: `portal/src/app/vsobjects/objects/vs-object-container.component.display.tl.spec.ts`

**Interfaces:**
- Consumes: `vizModern` / `vizDark` from Task 1's JSON.
- Produces: `VSObjectContainerComponent.vizDensityClass(vsObject: VSObjectModel): string | null` — returns `viz-density-<mode>` when the assembly is modern, `null` otherwise. Task 3 and Task 4 use the same helper shape.

- [ ] **Step 1: Add the model fields**

In `portal/src/app/vsobjects/model/vs-object-model.ts`, add before the closing brace of `VSObjectModel`:

```ts
   vizModern: boolean;   // resolved server-side from the assembly's mark; not the org gate
   vizDark: boolean;     // never true unless vizModern is true
```

- [ ] **Step 2: Write the failing test**

Append to `vs-object-container.component.display.tl.spec.ts`, inside the existing top-level `describe`:

```ts
   it("scopes viz-modern, viz-dark and the density class per assembly", async () => {
      const modernDark = createMockVSObjectModel("VSChart", "chart1");
      modernDark.vizModern = true;
      modernDark.vizDark = true;
      const legacy = createMockVSObjectModel("VSChart", "chart2");
      legacy.vizModern = false;
      legacy.vizDark = false;

      const { container } = await renderContainer([modernDark, legacy], { vizDensity: "compact" });

      const wrappers = container.querySelectorAll(".vs-object-parent-container");
      expect(wrappers.length).toBe(2);

      expect(wrappers[0].classList.contains("viz-modern")).toBe(true);
      expect(wrappers[0].classList.contains("viz-dark")).toBe(true);
      // the density class must ride along, or _viz-tokens.scss's compound matrices silently
      // fall through to the bare .viz-modern rule and every org renders dense
      expect(wrappers[0].classList.contains("viz-density-compact")).toBe(true);

      expect(wrappers[1].classList.contains("viz-modern")).toBe(false);
      expect(wrappers[1].classList.contains("viz-dark")).toBe(false);
      expect(wrappers[1].classList.contains("viz-density-compact")).toBe(false);
   });
```

Use the file's existing `createMockVSObjectModel` / render helper if it defines them under different names — read the top of the spec first and match what is there rather than introducing a second helper.

- [ ] **Step 3: Run it to verify it fails**

Run: `cd community/web && npx ng run portal:test-tl --include=projects/portal/src/app/vsobjects/objects/vs-object-container.component.display.tl.spec.ts`
Expected: FAIL — the wrapper carries none of the three classes.

- [ ] **Step 4: Add the density helper to the component**

In `vs-object-container.component.ts`, add beside the other per-object class helpers:

```ts
   // The density class must sit on the same element as viz-modern: _viz-tokens.scss declares its
   // matrices as compound selectors (.viz-modern.viz-density-compact, :120). The mode is still one
   // org preference — only whether an assembly honours it comes from the assembly.
   vizDensityClass(vsObject: VSObjectModel): string | null {
      return vsObject.vizModern && !!this.vizDensity ? `viz-density-${this.vizDensity}` : null;
   }
```

Add the `@Input() vizDensity: string;` if the component does not already receive it, and pass it from `viewer-app.component.html` / the composer host using the `vizDensity` each shell already holds (`viewer-app.component.ts:431`).

- [ ] **Step 5: Bind at the wrapper**

In `vs-object-container.component.html`, on the `.vs-object-parent-container` div (`:42`), add beside the existing `[class.*]` bindings:

```html
        [class.viz-modern]="vsObject.vizModern"
        [class.viz-dark]="vsObject.vizDark"
        [ngClass]="vizDensityClass(vsObject)"
```

- [ ] **Step 6: Run the test to verify it passes**

Run: `cd community/web && npx ng run portal:test-tl --include=projects/portal/src/app/vsobjects/objects/vs-object-container.component.display.tl.spec.ts`
Expected: PASS.

- [ ] **Step 7: Run the container's other TL specs**

Run: `cd community/web && npx ng run portal:test-tl --include=projects/portal/src/app/vsobjects/objects/vs-object-container.component.interaction.tl.spec.ts --include=projects/portal/src/app/vsobjects/objects/vs-object-container.component.risk.tl.spec.ts`
Expected: PASS. Do not widen the include to the whole suite.

- [ ] **Step 8: Commit**

```bash
git add community/web/projects/portal/src/app/vsobjects/model/vs-object-model.ts \
        community/web/projects/portal/src/app/vsobjects/objects/vs-object-container.component.ts \
        community/web/projects/portal/src/app/vsobjects/objects/vs-object-container.component.html \
        community/web/projects/portal/src/app/vsobjects/objects/vs-object-container.component.display.tl.spec.ts
git commit -m "feat(vsobjects): scope modern chrome to the assembly in the viewer

P5 task 2, the first of three binding sites. The density class rides along on the wrapper because
_viz-tokens.scss declares its matrices as compound selectors; leaving density on the body while
viz-modern moves would fall through to the bare .viz-modern rule and render every org dense."
```

---

## Task 3: Composer wrapper carries the same scope

**Files:**
- Modify: `portal/src/app/composer/gui/vs/editor/editable-object-container.component.html:18-24`
- Modify: `portal/src/app/composer/gui/vs/editor/editable-object-container.component.ts`
- Test: `portal/src/app/composer/gui/vs/editor/editable-object-container.component.display.tl.spec.ts`

**Interfaces:**
- Consumes: `vsObject.vizModern` / `vizDark` (Task 2's model fields); `vizDensity` from the composer shell.
- Produces: nothing new — the same three classes, on `.object-editor`.

**Why this is a separate task:** the composer does not use `.vs-object-parent-container` at all. It is the shell where Modernize and Revert live, so it is the one that must not be skipped.

- [ ] **Step 1: Write the failing test**

Append to `editable-object-container.component.display.tl.spec.ts`:

```ts
   it("scopes viz-modern, viz-dark and density on .object-editor", async () => {
      const model = createMockVSObjectModel("VSChart", "chart1");
      model.vizModern = true;
      model.vizDark = false;

      const { container } = await renderEditableObjectContainer(model, { vizDensity: "comfortable" });
      const editor = container.querySelector(".object-editor");

      expect(editor.classList.contains("viz-modern")).toBe(true);
      expect(editor.classList.contains("viz-dark")).toBe(false);
      expect(editor.classList.contains("viz-density-comfortable")).toBe(true);
   });
```

Match the file's existing render/mock helper names.

- [ ] **Step 2: Run it to verify it fails**

Run: `cd community/web && npx ng run portal:test-tl --include=projects/portal/src/app/composer/gui/vs/editor/editable-object-container.component.display.tl.spec.ts`
Expected: FAIL — `.object-editor` carries none of the three.

- [ ] **Step 3: Add the helper**

In `editable-object-container.component.ts`, add beside the other class helpers (`vsObject` is already a field, `:161`):

```ts
   // Same rule as the viewer container: modern, dark and density travel together on one element.
   get vizDensityClass(): string | null {
      return this.vsObject?.vizModern && !!this.vizDensity ? `viz-density-${this.vizDensity}` : null;
   }
```

Add `@Input() vizDensity: string;` and pass it from `viewsheet-pane.component.html:96` where `<editable-object-container>` is instantiated, using the composer shell's `vizDensity`.

- [ ] **Step 4: Bind at the wrapper**

In `editable-object-container.component.html`, on the `.object-editor` div (`:18`), add beside `[class.group-container]` (`:22`):

```html
     [class.viz-modern]="vsObject.vizModern"
     [class.viz-dark]="vsObject.vizDark"
     [ngClass]="vizDensityClass"
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `cd community/web && npx ng run portal:test-tl --include=projects/portal/src/app/composer/gui/vs/editor/editable-object-container.component.display.tl.spec.ts`
Expected: PASS.

- [ ] **Step 6: Run the container's sibling TL specs**

Run: `cd community/web && npx ng run portal:test-tl --include=projects/portal/src/app/composer/gui/vs/editor/editable-object-container.component.interaction.tl.spec.ts --include=projects/portal/src/app/composer/gui/vs/editor/editable-object-container.component.risk.tl.spec.ts`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add community/web/projects/portal/src/app/composer/gui/vs/editor/editable-object-container.component.ts \
        community/web/projects/portal/src/app/composer/gui/vs/editor/editable-object-container.component.html \
        community/web/projects/portal/src/app/composer/gui/vs/editor/editable-object-container.component.display.tl.spec.ts \
        community/web/projects/portal/src/app/composer/gui/vs/editor/viewsheet-pane.component.html
git commit -m "feat(composer): scope modern chrome to the assembly in the composer

P5 task 3. The composer renders through .object-editor and never touches
.vs-object-parent-container, so the viewer binding does not reach it. This is the shell Modernize
and Revert act in, so it is the one that must not be skipped."
```

---

## Task 4: Mini-toolbar takes the class directly

**Files:**
- Modify: `portal/src/app/vsobjects/objects/vs-object-container.component.html:341-362`
- Test: `portal/src/app/vsobjects/objects/mini-toolbar/mini-toolbar.component.tl.spec.ts`

**Interfaces:**
- Consumes: `vsObject.vizModern` / `vizDark`, already in scope at `:341` (the tag binds `[dataTipName]="vsObject.absoluteName"`).
- Produces: nothing new.

**Why this cannot be folded into Task 2:** `<mini-toolbar>` sits *outside* the wrapper's closing `</div>` at `:340`. `_moving-resize.scss` addresses it with `+` adjacent-sibling combinators (`:24,27,44,47,53`), and the arrangement is deliberate — `mini-toolbar.component.scss:141` records that the sibling relationship is what makes pointer movement between object and toolbar behave, and `mini-toolbar.component.ts:54` depends on the wrapper's server-assigned `z-index` being a sibling's. **Do not move it inside the wrapper.** It takes the class on itself; `:host-context(X)` matches the host element as well as its ancestors, so its two live rules (`mini-toolbar.component.scss:81` and `:200`) then apply with no selector edit.

- [ ] **Step 1: Write the failing test**

Append to `mini-toolbar.component.tl.spec.ts`:

```ts
   it("applies modern toolbar chrome from the host class, not a body class", async () => {
      // :host-context(.viz-modern) matches the host itself, so the class lands on <mini-toolbar>.
      // Guards the anchored-toolbar rollout: the toolbar is a DOM sibling of the assembly wrapper,
      // so the wrapper binding cannot reach it and it would go silently legacy.
      document.body.classList.remove("viz-modern");

      const { container } = await renderMiniToolbar({ vizModern: true });
      const host = container.querySelector("mini-toolbar");

      expect(host.classList.contains("viz-modern")).toBe(true);
   });
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd community/web && npx ng run portal:test-tl --include=projects/portal/src/app/vsobjects/objects/mini-toolbar/mini-toolbar.component.tl.spec.ts`
Expected: FAIL — the host carries no class.

- [ ] **Step 3: Bind on the element**

In `vs-object-container.component.html`, on the `<mini-toolbar>` tag (`:341`):

```html
        [class.viz-modern]="vsObject.vizModern"
        [class.viz-dark]="vsObject.vizDark"
        [ngClass]="vizDensityClass(vsObject)"
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd community/web && npx ng run portal:test-tl --include=projects/portal/src/app/vsobjects/objects/mini-toolbar/mini-toolbar.component.tl.spec.ts`
Expected: PASS.

- [ ] **Step 5: Run the mini-toolbar unit spec**

Run: `cd community/web && npx ng test portal --include="**/mini-toolbar.component.spec.ts"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add community/web/projects/portal/src/app/vsobjects/objects/vs-object-container.component.html \
        community/web/projects/portal/src/app/vsobjects/objects/mini-toolbar/mini-toolbar.component.tl.spec.ts
git commit -m "feat(vsobjects): scope the mini-toolbar's modern chrome to its own assembly

P5 task 4, the third binding site. <mini-toolbar> is a DOM sibling of the assembly wrapper, not a
descendant — _moving-resize.scss addresses it with + combinators and the arrangement is deliberate
— so it takes the class on itself. :host-context matches the host, so no selector changes."
```

---

## Task 5: Action classes and highlight pane read the model

**Files:**
- Modify: `portal/src/app/vsobjects/action/chart-actions.ts:441,458,514,580`
- Modify: `portal/src/app/vsobjects/action/abstract-vs-actions.ts:432,484`
- Modify: `portal/src/app/vsobjects/action/table-actions.ts:326`
- Modify: `portal/src/app/vsobjects/action/crosstab-actions.ts:392`
- Modify: `portal/src/app/vsobjects/action/calc-table-actions.ts:370`
- Modify: `portal/src/app/widget/highlight/highlight-pane.component.ts:74`
- Test: `portal/src/app/vsobjects/action/chart-actions.spec.ts`

**Interfaces:**
- Consumes: `this.model.vizModern` — every one of these classes already holds a `VSObjectModel` as `this.model`.
- Produces: nothing new. This is a substitution, not an API change.

- [ ] **Step 1: Write the failing test**

Append to `chart-actions.spec.ts`:

```ts
   it("orders the toolbar from the assembly's own mark, not a body class", () => {
      document.body.classList.remove("viz-modern");
      const model = createChartModel();
      model.vizModern = true;

      const actions = new ChartActions(model, /* the spec's existing constructor args */);
      const ids = actions.toolbarActions[0].actions.map(a => a.id());

      // stableFirst ordering is the modern branch; it must be selected by the model alone
      expect(ids).toContain("chart properties");
   });
```

Match the spec's existing construction helper; read the top of the file first.

- [ ] **Step 2: Run it to verify it fails**

Run: `cd community/web && npx ng test portal --include="**/chart-actions.spec.ts"`
Expected: FAIL — with no body class the legacy order is chosen.

- [ ] **Step 3: Substitute in all six files**

Replace `GuiTool.isVizModern()` with `this.model.vizModern` at each site. Two need care rather than a blind swap:

`abstract-vs-actions.ts:484` — the guard already reads `this.model.containerType`, so `this.model` is safe there:

```ts
      if(this.model.vizModern && !GuiTool.isMobileDevice() &&
         this.model.containerType != "VSSelectionContainer" &&
         this.toolbarActions && this.toolbarActions.length > 0)
```

`chart-actions.ts:441,458` are inside `visible: () => …` closures, evaluated after construction — `this.model` is still correct, but confirm the arrow function keeps `this` bound to the actions instance (it does; these are property-initializer arrows).

Leave `GuiTool.isMobileDevice()` alone. It is not a viz read.

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd community/web && npx ng test portal --include="**/chart-actions.spec.ts"`
Expected: PASS.

- [ ] **Step 5: Run the sibling action specs**

Run: `cd community/web && npx ng test portal --include="**/table-actions.spec.ts" --include="**/crosstab-actions.spec.ts" --include="**/calc-table-actions.spec.ts" --include="**/highlight-pane.component.spec.ts"`
Expected: PASS. If a spec sets `document.body.classList.add("viz-modern")` to arrange the modern case, change it to set `model.vizModern = true` — a body-class arrangement will stop working in Task 8 and is exactly the coupling this phase removes.

- [ ] **Step 6: Commit**

```bash
git add community/web/projects/portal/src/app/vsobjects/action/ \
        community/web/projects/portal/src/app/widget/highlight/highlight-pane.component.ts
git commit -m "refactor(vsobjects): read the assembly's mark for toolbar and highlight chrome

P5 task 5. Ten call sites across six action classes and the highlight pane, all of which already
hold the assembly model. Behaviour is unchanged while the gate and every mark agree; it diverges
on a mixed dashboard, which is the point."
```

---

## Task 6: Free functions take the resolved value as a parameter

**Files:**
- Modify: `portal/src/app/vsobjects/objects/mini-toolbar/mini-toolbar.service.ts:74-93`
- Modify: `portal/src/app/vsobjects/action/abstract-vs-actions.ts:143,222`
- Modify: `portal/src/app/vsobjects/objects/vs-object-container.component.ts:486,925`
- Modify: `portal/src/app/graph/model/chart-tool.ts:855,1116`
- Modify: `portal/src/app/graph/objects/chart-plot-area.component.ts:936` and the other `drawRegions` callers
- Modify: `portal/src/app/vsobjects/objects/data-tip/date-tip-helper.ts:42`
- Modify: `portal/src/app/widget/tooltip/tooltip-tail-placement.ts:33,148,172`
- Test: `portal/src/app/vsobjects/objects/mini-toolbar/mini-toolbar.service.spec.ts`

**Interfaces:**
- Produces, and later tasks must match these signatures exactly:
  - `isAnchoredResident(objectType: string, vizModern: boolean): boolean`
  - `isAnchoredChromeSuppressed(objectType: string, vizModern: boolean): boolean`
  - `DateTipHelper.popDimColor(vizModern: boolean): string` — **a method now, not a getter**
  - `tailRadius(vizModern: boolean): number`
  - `buildChromePaths(boxWidth: number, boxHeight: number, tailSide: TailSide, …, vizModern: boolean)` — the new parameter goes last
  - `ChartTool.drawRegions(context, regions, offsetX, offsetY, currentScale?, scaleX?, scaleY?, drawReferLine?, areaName?, vizModern?: boolean)` — appended after `areaName`, defaulting to `false`
  - `ChartTool.drawTouch(context: CanvasRenderingContext2D, x: number, y: number, vizModern: boolean)`

- [ ] **Step 1: Write the failing test**

Append to `mini-toolbar.service.spec.ts`:

```ts
   it("decides residency from the passed value, not a body class", () => {
      document.body.classList.remove("viz-modern");
      document.body.classList.add("viz-density-compact");

      expect(isAnchoredResident("VSChart", true)).toBe(true);
      expect(isAnchoredResident("VSChart", false)).toBe(false);
   });
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd community/web && npx ng test portal --include="**/mini-toolbar.service.spec.ts"`
Expected: FAIL — the function takes one argument.

- [ ] **Step 3: Widen the two mini-toolbar predicates**

```ts
export function isAnchoredResident(objectType: string, vizModern: boolean): boolean {
   return vizModern && GuiTool.isVizDensityAtLeastCompact() &&
      isAnchoredAssemblyType(objectType);
}

export function isAnchoredChromeSuppressed(objectType: string, vizModern: boolean): boolean {
   return vizModern && !GuiTool.isVizDensityAtLeastCompact() &&
      isAnchoredAssemblyType(objectType);
}
```

`isVizDensityAtLeastCompact()` stays for now — it reads density, which is still an org preference on the body, and Task 8 decides its fate.

Update the three callers, all of which hold a model:
- `abstract-vs-actions.ts:143` → `isAnchoredResident(this.model.objectType, this.model.vizModern)`
- `abstract-vs-actions.ts:222` → `isAnchoredChromeSuppressed(this.model.objectType, this.model.vizModern)`
- `vs-object-container.component.ts:486` → `isAnchoredResident(object.objectType, object.vizModern)`

- [ ] **Step 4: Convert the tooltip tail**

In `tooltip-tail-placement.ts`, make the radius explicit rather than ambient:

```ts
/** The tail radius for the given assembly's resolved modern state. */
export function tailRadius(vizModern: boolean): number {
   return vizModern ? TAIL_RADIUS_MODERN : TAIL_RADIUS;
}
```

`clampToEdge` (`:147`) and `buildChromePaths` (`:169`) both call it. Thread the value: `clampToEdge` gains a `vizModern` parameter from its caller, and `buildChromePaths` gains one as its final parameter. `tooltip.component.ts` is the only external consumer of `buildChromePaths` — pass the value the tooltip received from its source assembly. If the tooltip component has no route to a source model, pass `false` and record the gap in the task's commit message rather than inventing one; a legacy tail on a modern tooltip is a 2px difference, and Task 9's manual pass will surface it.

- [ ] **Step 5: Convert the data-tip scrim**

`DateTipHelper.popDimColor` becomes a method taking the value:

```ts
   public static popDimColor(vizModern: boolean): string {
      return vizModern ? POP_DIM_COLOR_MODERN : POP_DIM_COLOR;
   }
```

Its single caller is `vs-object-container.component.ts:925`, inside `drawPopDim()`. The scrim covers the whole container rather than one assembly, so pass the resolved value of the assembly whose pop-up is showing — the component already has `isPopupShowing(vsObject)` and `this.vsInfo.vsObjects`:

```ts
         const popped = this.vsInfo.vsObjects.find(o => this.isPopupShowing(o));
         context.fillStyle = DateTipHelper.popDimColor(!!popped?.vizModern);
```

- [ ] **Step 6: Convert the two chart-tool sites**

`drawRegions` gains a final optional parameter and passes it to the `strokeOnly` computation at `:855`:

```ts
   export function drawRegions(context: CanvasRenderingContext2D, regions: ChartRegion[],
                               offsetX: number, offsetY: number, currentScale?: number,
                               scaleX?: number, scaleY?: number,
                               drawReferLine: boolean = false,
                               areaName: ChartAreaName = null,
                               vizModern: boolean = false): void
```

```ts
         const strokeOnly = vizModern && ChartTool.isChromeArea(areaName);
```

`drawTouch` gains a required parameter:

```ts
   export function drawTouch(context: CanvasRenderingContext2D, x: number, y: number,
                             vizModern: boolean)
   {
      if(context) {
         context.lineWidth = 2;
         context.strokeStyle = vizModern
            ? getComputedStyle(document.documentElement)
                 .getPropertyValue("--inet-primary-color").trim() || "#E58A2A"
            : "#dc581e";
```

Update every caller found by `grep -rn "ChartTool.drawRegions\|ChartTool.drawTouch" --include=*.ts`: `chart-area.component.ts:592`, `chart-object-area-base.ts:158`, `chart-plot-area.component.ts:100,104,936,1195`, `vs-chart.component.ts:749`. Each holds a chart model — pass `this.model.vizModern` (or the equivalent field name on that component's model).

- [ ] **Step 7: Run the tests**

Run: `cd community/web && npx ng test portal --include="**/mini-toolbar.service.spec.ts" --include="**/chart-tool.spec.ts" --include="**/tooltip-tail-placement.spec.ts"`
Expected: PASS. Skip an include for any of those spec files that does not exist.

- [ ] **Step 8: Typecheck the whole portal project**

Run: `cd community/web && npx ng build portal --configuration development`
Expected: SUCCESS. Widening five signatures is exactly the change a scoped test run cannot validate — a missed caller is a compile error, not a test failure.

- [ ] **Step 9: Commit**

```bash
git add community/web/projects/portal/src/app/
git commit -m "refactor(vsobjects): pass the resolved viz state into the ambient viz helpers

P5 task 6. Six free functions read the gate off the body with no assembly in scope; each now takes
the resolved value from a caller that holds one. The pop-dim scrim resolves against the assembly
whose pop-up is showing, since the scrim exists only while one is."
```

---

## Task 7: getTooltipStyle resolves against the assembly

**Files:**
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/graph/AbstractChartInfo.java:3736-3746`
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/graph/ChartInfo.java:889,901,915`
- Modify: `core/src/main/java/inetsoft/report/composition/region/PlotArea.java:871,2230`
- Modify: `core/src/main/java/inetsoft/web/graph/GraphBuilder.java:148`
- Modify: `core/src/main/java/inetsoft/web/composer/vs/dialog/ChartPropertyDialogService.java:227`
- Test: `core/src/test/java/inetsoft/uql/viewsheet/graph/AbstractChartInfoTooltipStyleTest.java` (create)

**Interfaces:**
- Produces: `ChartInfo.getTooltipStyle(VizContext ctx)`. The no-arg `getTooltipStyle()` is retained, delegating with `VizContext.LEGACY`, so report-path callers with no assembly keep compiling and keep resolving AUTO → DEFAULT.

**Why it is here:** P4 deferred this one reader deliberately. Its most visible consumer ships `tooltipStyle` to the browser (`GraphBuilder.java:148`), which stayed org-gated until this phase — fixing the server half alone would have bought nothing visible. See `8ef511e45`'s commit message.

- [ ] **Step 1: Write the failing test**

```java
package inetsoft.uql.viewsheet.graph;

import inetsoft.uql.viewsheet.internal.VizContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AbstractChartInfoTooltipStyleTest {
   @Test
   void autoResolvesFromTheContextRatherThanTheOrgGate() {
      DefaultChartInfo info = new DefaultChartInfo();
      info.setTooltipStyleValue(ChartInfo.TooltipStyle.AUTO);

      assertEquals(ChartInfo.TooltipStyle.DEFAULT, info.getTooltipStyle(VizContext.LEGACY),
                   "a legacy context resolves AUTO to the legacy tooltip");
   }

   @Test
   void anExplicitStyleIgnoresTheContext() {
      DefaultChartInfo info = new DefaultChartInfo();
      info.setTooltipStyleValue(ChartInfo.TooltipStyle.CARD);

      assertEquals(ChartInfo.TooltipStyle.CARD, info.getTooltipStyle(VizContext.LEGACY));
   }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./mvnw test -pl core -Dtest=AbstractChartInfoTooltipStyleTest`
Expected: FAIL to compile — `getTooltipStyle(VizContext)` does not exist.

- [ ] **Step 3: Add the overload**

In `ChartInfo.java`, beside the existing declaration at `:889`:

```java
   /**
    * The effective tooltip style for the given assembly context. AUTO is never a rendered style:
    * it resolves to CARD for a modern assembly and DEFAULT otherwise. The no-arg overload passes
    * LEGACY, for the report path that has no assembly to resolve against.
    */
   TooltipStyle getTooltipStyle(VizContext ctx);
```

In `AbstractChartInfo.java`, replace the body at `:3736-3746`:

```java
   @Override
   public TooltipStyle getTooltipStyle() {
      return getTooltipStyle(VizContext.LEGACY);
   }

   @Override
   public TooltipStyle getTooltipStyle(VizContext ctx) {
      TooltipStyle style =
         parseTooltipStyle(tooltipStyle.getStringValue(false, tooltipStyle.getDValue()));
      // AUTO is never a rendered style: resolve it against the assembly's own context so every
      // runtime consumer agrees. The design value (getTooltipStyleValue) stays AUTO for persistence.
      return style == TooltipStyle.AUTO
         ? (ctx.modern ? TooltipStyle.CARD : TooltipStyle.DEFAULT)
         : style;
   }
```

Add `import inetsoft.uql.viewsheet.internal.VizContext;` to `ChartInfo.java` if it is not already imported.

- [ ] **Step 4: Thread the four viewsheet-path callers**

- `PlotArea.java:871` and `:2230` — resolve the chart's own context. `PlotArea` reaches its `ChartVSAssemblyInfo` at the same seam P4 used for the chart pipeline; pass `VizContext.of(info)`.
- `GraphBuilder.java:148` — `cinfo.getTooltipStyle(ctx)` where `ctx` is the chart's context, matching how the surrounding builder already resolves it in P4.
- `ChartPropertyDialogService.java:227` — the service resolves the dialog's own chart by object id for P4's dialog-model work; reuse that context.
- `ChartInfo.java:901,915` — default methods; give them the `VizContext` parameter and forward it.

- [ ] **Step 5: Run the tests**

Run: `./mvnw test -pl core -Dtest=AbstractChartInfoTooltipStyleTest`
Expected: PASS.

- [ ] **Step 6: Run the chart suite**

Run: `./mvnw test -pl core -Dtest='*Chart*Test'`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add core/src/main/java/inetsoft/uql/viewsheet/graph/ \
        core/src/main/java/inetsoft/report/composition/region/PlotArea.java \
        core/src/main/java/inetsoft/web/graph/GraphBuilder.java \
        core/src/main/java/inetsoft/web/composer/vs/dialog/ChartPropertyDialogService.java \
        core/src/test/java/inetsoft/uql/viewsheet/graph/AbstractChartInfoTooltipStyleTest.java
git commit -m "feat(chart): resolve the AUTO tooltip style against the assembly

P5 task 7, the reader P4 deferred here. Its most visible consumer ships tooltipStyle to the
browser, which stayed org-gated until this phase, so fixing the server half alone would have
bought nothing a user could see. The no-arg overload survives on LEGACY for the report path."
```

---

## Task 8: Rename the body class and delete the global reads

**Files:**
- Modify: `portal/src/app/vsobjects/viewer-app.component.ts:2787-2799`
- Modify: `portal/src/app/portal/app.component.ts:264-279`
- Modify: `portal/src/app/composer/app.component.ts:138-151`
- Modify: `portal/src/app/common/util/gui-tool.ts:65-94`
- Modify: `portal/src/scss/internal/_directives.scss:316-330`
- Modify: `portal/src/app/widget/tooltip/tooltip.component.ts` (bind the class on the tooltip element)
- Test: `portal/src/app/common/util/gui-tool.spec.ts`

**Interfaces:**
- Removes: `GuiTool.isVizModern()`, `GuiTool.vizDensityMode()`, `GuiTool.isVizDensityAtLeastCompact()`. Nothing may call them after this task.
- Produces: body carries `viz-shell` / `viz-shell-dark` plus its unchanged `viz-density-*`.

**This task must come last** among the code tasks: it is what makes the old global read impossible, so every consumer has to be converted first. Run Tasks 5 and 6 to green before starting.

- [ ] **Step 1: Write the guard test**

Create or append to `gui-tool.spec.ts`:

```ts
   it("no longer exposes a global modern-visualization read", () => {
      // The gate is per-assembly from P5 on. A global read cannot answer "is THIS assembly modern",
      // and leaving one available invites a caller that silently regresses a mixed dashboard.
      expect((GuiTool as any).isVizModern).toBeUndefined();
      expect((GuiTool as any).vizDensityMode).toBeUndefined();
      expect((GuiTool as any).isVizDensityAtLeastCompact).toBeUndefined();
   });
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd community/web && npx ng test portal --include="**/gui-tool.spec.ts"`
Expected: FAIL — all three are defined.

- [ ] **Step 3: Confirm there are no remaining consumers**

Run:
```bash
cd community/web/projects && grep -rn "isVizModern\|vizDensityMode\|isVizDensityAtLeastCompact" --include=*.ts . | grep -v spec
```
Expected: only the definitions in `gui-tool.ts`, plus the two `isVizDensityAtLeastCompact()` calls Task 6 left in `mini-toolbar.service.ts`. Convert those two now: `isAnchoredResident` and `isAnchoredChromeSuppressed` take a second parameter for density, or read it from the same model the caller already passes. If anything else appears, it was missed in Task 5 or 6 — go back and convert it rather than keeping the helper alive.

- [ ] **Step 4: Rename in the three shells**

In each shell, replace the modern/dark class names. `viewer-app.component.ts:2789-2798` becomes:

```ts
         const modern: boolean = !!this.modernVisualization;
         // The org-level shell class. Assembly chrome keys off .viz-modern on the assembly's own
         // wrapper (P5); this exists so shell surfaces have a hook and so the body can no longer
         // satisfy an assembly-scoped selector.
         body.classList.toggle("viz-shell", modern);
         body.classList.remove(
            "viz-density-comfortable", "viz-density-compact", "viz-density-dense");

         if(modern && ["comfortable", "compact", "dense"].includes(this.vizDensity)) {
            body.classList.add(`viz-density-${this.vizDensity}`);
         }

         body.classList.toggle("viz-shell-dark", modern && this.darkMode);
```

Apply the same substitution in `portal/app.component.ts:264-279` (`this.VIZ_MODERN_CLASS` / `this.VIZ_DARK_CLASS` constants) and `composer/app.component.ts:138-151` (`:44,47`). The density class stays exactly as it is in all three: no standalone `.viz-density-*` selector exists, so it is inert on the body and harmless.

- [ ] **Step 5: Delete the three helpers**

Remove `isVizModern()` (`:66-68`), `vizDensityMode()` (`:80-86`) and `isVizDensityAtLeastCompact()` (`:92-94`) from `gui-tool.ts`. `getMiniToolbarHeight()` (`:72`) calls `isVizModern()` — give it a `vizModern: boolean` parameter and update its callers, or inline the constant where the caller already knows. `MINI_TOOLBAR_HEIGHT_MODERN` (`:63`) stays.

- [ ] **Step 6: Fix the one descendant selector**

`_directives.scss:316` scopes body-appended tooltip surfaces with `.viz-modern <descendant>`, which cannot be satisfied by a class on the tooltip element itself. Convert to compound form:

```scss
// Modern visualization gate. The tooltip surfaces are global classes appended to body, so they
// carry the scope themselves rather than inheriting it from an ancestor.
.widget__default-tooltip.viz-modern,
.widget__card-tooltip.viz-modern,
.hidden__annotation-tooltip.viz-modern {
  box-shadow: var(--inet-shadow-overlay);
}

.widget__default-tooltip.viz-modern,
.hidden__annotation-tooltip.viz-modern {
  border-radius: var(--inet-radius-sm);
}
```

Preserve every other declaration in that block, converting each selector the same way. Then bind `[class.viz-modern]` on the tooltip element in `tooltip.component.ts` from the value its source assembly passed (the same value Task 6 threaded into `buildChromePaths`).

- [ ] **Step 7: Run the tests**

Run: `cd community/web && npx ng test portal --include="**/gui-tool.spec.ts"`
Expected: PASS.

- [ ] **Step 8: Typecheck**

Run: `cd community/web && npx ng build portal --configuration development`
Expected: SUCCESS.

- [ ] **Step 9: Re-run every TL spec this plan touched**

Run:
```bash
cd community/web && npx ng run portal:test-tl \
  --include=projects/portal/src/app/vsobjects/objects/vs-object-container.component.display.tl.spec.ts \
  --include=projects/portal/src/app/vsobjects/objects/vs-object-container.component.interaction.tl.spec.ts \
  --include=projects/portal/src/app/vsobjects/objects/vs-object-container.component.risk.tl.spec.ts \
  --include=projects/portal/src/app/vsobjects/objects/mini-toolbar/mini-toolbar.component.tl.spec.ts \
  --include=projects/portal/src/app/composer/gui/vs/editor/editable-object-container.component.display.tl.spec.ts
```
Expected: PASS. Do not remove the `--include` flags.

- [ ] **Step 10: Commit**

```bash
git add community/web/projects/portal/src/
git commit -m "refactor(vsobjects): rename the shell class and retire the global gate read

P5 task 8, last of the code tasks. The body now carries viz-shell/viz-shell-dark, so it can no
longer satisfy an assembly-scoped selector, and GuiTool's three viz helpers are deleted — a global
read cannot answer 'is this assembly modern' and leaving one available invites a regression on a
mixed dashboard. The density class stays on the body untouched: no standalone .viz-density-*
selector exists, so it is inert there, while the assembly wrapper carries its own copy.

One selector edit, the exception §3 documents: _directives.scss scoped body-appended tooltip
surfaces with a descendant selector, which a class on the tooltip element cannot satisfy. Those
become compound selectors on the surfaces themselves."
```

---

## Task 9: Verification pass

**Files:** none modified. This task produces the evidence, and a commit only if it finds something.

- [ ] **Step 1: Full core suite**

Run: `./mvnw test -pl core`
Expected: 0 failures, 0 errors. P4's baseline was 4899 run / 67 skipped; the count moves by the tests this plan adds.

- [ ] **Step 2: Portal unit suite**

Run: `cd community/web && npm run test:portal`
Expected: PASS. This is the standard suite, not the TL one, and is safe to run unfiltered.

- [ ] **Step 3: Full cross-module build**

Run: `./mvnw clean install -DskipTests "-Pcommunity,enterprise"`
Expected: SUCCESS. P4 shipped a build break outside `core` because every verification had been scoped `-pl core`; a widened interface method in Task 7 is the same hazard.

- [ ] **Step 4: The manual checks — these need a built server and a browser**

Nine checks. None can be automated; record the result of each.

1. **Mixed dashboard, gate on.** A dashboard holding one Modernized assembly and one unmarked one renders each by its own mark — modern card chrome on the first, legacy on the second, side by side.
2. **The same dashboard's mini-toolbar.** Hover each assembly: the modern one gets the 24px anchored strip, the legacy one the old floating toolbar. This is the check Task 4 exists for.
3. **Composer parity.** Open the same dashboard in the composer: each assembly matches what the viewer showed. This is the check Task 3 exists for.
4. **Density is honoured per assembly.** Set the org density to comfortable. The modern assembly takes 28px rows; the legacy one is unchanged. If both look dense, the density class did not reach the wrapper — the silent failure mode §3 documents.
5. **Dark.** With dark mode on, a `modern-dark` assembly pasted into a light dashboard stays dark and its light siblings stay light.
6. **Gate off, marked content.** Turn `viewsheet.modernVisualization` off. A marked dashboard goes legacy in the browser — the `gate &&` term is still live until P6, and the server and browser must agree on that. Confirm no half-modern rendering.
7. **Tooltips and data tips.** Hover a modern assembly and a legacy one: the tooltip surface chrome follows the assembly it came from, not the last one hovered. Check the pop-dim scrim behind a data tip too.
8. **Chart selection and touch.** On a modern chart, selecting an axis draws a stroke-only outline; on a legacy chart it keeps the fill. Task 6 moved both.
9. **Export agreement.** Export the mixed dashboard to PDF, PNG and Excel. Each assembly exports as it renders. P5 is browser-side, so the expected result is "unchanged from P4" — a difference here means a server read was disturbed.

- [ ] **Step 5: Run P3's eleven outstanding manual checks in the same session**

They need the same setup — a built server, a browser and a legacy dashboard — and have never run. See the design's P3 section for the list, including the twelfth added while building, on what an empty undo checkpoint does to Ctrl+Z. This is the last phase where they can be run against content that is not customer data.

- [ ] **Step 6: Update the roadmap and design status**

In `docs/superpowers/specs/lookfeel/chart-card-roadmap.md`, move P5 to SHIPPED with its commit hash in the dependency picture, re-derive "What to pick up next" (do not edit it in place — the file's own instruction), and record the manual-check results. In the design document, mark P5 done in §5 and note anything the phase established that P6 should read first.

- [ ] **Step 7: Commit the doc update**

```bash
git add community/docs/superpowers/specs/
git commit -m "docs(chart-card): record P5 shipped and re-derive what is next

Browser reads follow the mark. The interim state P4's commit message called 'not a state to ship'
is closed: server and browser now agree on which assemblies are modern, per assembly."
```

---

## Self-Review

**Spec coverage.** §3's four elements each have a task: the model field (Task 1), the per-assembly scope at all three binding sites (Tasks 2–4), the body-class rename (Task 8), the `GuiTool` conversion (Tasks 5–6). §5's P5 verification list is Task 9 steps 4.1, 4.5 and 4.3 — mixed dashboard end to end, a marked assembly pasted into an unmarked sheet, and slices 1–3 behaving per-assembly. The density correction is Task 2 step 4 and Task 9 check 4. The deferred `getTooltipStyle` is Task 7. The `@JsonValue` question §3 raises is answered by Global Constraints — the mark never leaves the server, so it does not arise.

**Type consistency.** `vizModern` / `vizDark` are the property names from Task 1 through Task 8. The widened signatures in Task 6 are restated in that task's Interfaces block and are what Task 8 step 3 checks against.

**Two places where the implementer has to look at the code rather than copy this plan:** the TL specs' existing mock and render helper names (Tasks 2–4 step 1), which differ per file; and `PlotArea`'s route to its `ChartVSAssemblyInfo` (Task 7 step 4), which follows the seam P4 established rather than a new one. Both are called out in place.

**One acknowledged gap, deliberately not designed around:** if `tooltip.component.ts` has no route to its source assembly, Task 6 step 4 passes `false` and records it. A legacy tail radius on a modern tooltip is a 2px difference and Task 9 check 7 will surface it; inventing a plumbing path speculatively would cost more than the defect.
