# Anchored Strip Density Gating Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the anchored mini-toolbar a compact-and-above affordance and give the title lane a density row, so a 24px strip stops overflowing a 20px lane.

**Architecture:** Two halves that ship in order. The client half reads the density mode from the `viz-density-<mode>` body class — already set by all three shells — and refuses to anchor under dense, falling back to the legacy hover-revealed overlay that still ships. The server half adds a `titleHeight()` to `VSDensityDefaults` following the exact shape of the existing `rowHeight()`, so compact and comfortable lanes grow to hold the strip.

**Tech Stack:** Angular 21 / TypeScript 5.9, SCSS, Vitest (`*.spec.ts` unit, `*.tl.spec.ts` MSW-backed integration), Java 21, JUnit.

## Global Constraints

- Everything that moves a pixel sits behind `GuiTool.isVizModern()` (client) or `VSDensityDefaults.isModern()` (server). Gate-off output must be byte-identical.
- Dense must equal today. It is the default mode and the one mode with a parity obligation.
- Never scope defensively to the chart. These files are shared by all six anchored assembly types; the edit lands once, in shared code.
- Deletions before bindings, inside every task.
- No comments in Angular HTML template files.
- Do not reference ticket numbers, PR numbers, or design-doc filenames in source comments. Reference behaviour and code.

## Source of truth

`docs/superpowers/specs/lookfeel/chart-card-design2/Visualization Widget Spec.dc.html` §05 ("The anchored strip does not fit the title lane") and §08 step 3. The decision, verbatim on the two numbers that matter:

- *"Title height joins the density matrix as its own row at 20 / 26 / 30 — dense pinned to `defh`, compact and comfortable borrowing the header row's steps. Compact's 26px lane holds the 24px strip with 1px clearance either side."*
- *"A 24px strip cannot fit a 20px lane, so the anchored strip is a compact-and-above affordance. Dense falls back to the existing hover-revealed overlay — the legacy `.mini-toolbar:hover` path, already shipping, no new code."*

## Why the order is client-then-server

Task 1 alone makes the default mode correct, since dense stops anchoring at all. Task 2 alone would leave dense anchoring into a 20px lane, which is the defect. Neither task regresses anything on its own, but Task 1 delivers more.

## File structure

| File | Responsibility | Task |
|---|---|---|
| `web/projects/portal/src/app/common/util/gui-tool.ts` | Reads the gate and the density mode off `document.body`. Gains one method | 1 |
| `web/projects/portal/src/app/common/util/gui-tool.spec.ts` | Unit tests for that method | 1 |
| `web/projects/portal/src/app/vsobjects/objects/vs-object-container.component.ts` | Decides whether an assembly anchors. Gains the density condition | 1 |
| `web/projects/portal/src/app/vsobjects/objects/mini-toolbar/mini-toolbar.component.scss` | Pins the strip height. Literal becomes a token | 1 |
| `web/projects/portal/src/app/vsobjects/objects/vs-object-container.component.display.tl.spec.ts` | Integration coverage for anchoring per density | 1 |
| `core/src/main/java/inetsoft/uql/viewsheet/internal/VSDensityDefaults.java` | Owns the density matrix server-side. Gains `titleHeight()` | 2 |
| `core/src/test/java/inetsoft/uql/viewsheet/internal/VSDensityDefaultsTest.java` | Unit tests for it | 2 |

---

### Task 1: Gate the strip to compact-and-above, and swap the height literal

**Files:**
- Modify: `web/projects/portal/src/app/common/util/gui-tool.ts:64-75`
- Modify: `web/projects/portal/src/app/common/util/gui-tool.spec.ts`
- Modify: `web/projects/portal/src/app/vsobjects/objects/vs-object-container.component.ts:472-484`
- Modify: `web/projects/portal/src/app/vsobjects/objects/mini-toolbar/mini-toolbar.component.scss:81-82`
- Test: `web/projects/portal/src/app/vsobjects/objects/vs-object-container.component.display.tl.spec.ts`

**Interfaces:**
- Consumes: `GuiTool.isVizModern(): boolean` (existing, `gui-tool.ts:65`), which reads `document.body.classList.contains("viz-modern")`.
- Produces: `GuiTool.vizDensityMode(): "dense" | "compact" | "comfortable"` and `GuiTool.isVizDensityAtLeastCompact(): boolean`. `VSObjectContainer.isKebabResident(object)` and `.isToolbarAnchored(object)` keep their existing signatures `(object: VSObjectModel) => boolean` and change only their result under dense.

**Background the implementer needs.** The density mode reaches the browser as a body class. `portal/app.component.ts:271`, `composer/app.component.ts:144` and `viewer-app.component.ts:2795` each add `viz-density-${mode}` and remove the other two, from a `vizDensity` field on their model. When no class is present, `_viz-tokens.scss:109-110` treats bare `.viz-modern` as dense, so the TypeScript must default to dense too or the two layers disagree.

- [ ] **Step 1: Write the failing test for the density reader**

Add to `web/projects/portal/src/app/common/util/gui-tool.spec.ts`:

```typescript
describe("GuiTool density mode", () => {
   afterEach(() => {
      document.body.classList.remove(
         "viz-modern", "viz-density-dense", "viz-density-compact", "viz-density-comfortable");
   });

   it("defaults to dense when no density class is present", () => {
      document.body.classList.add("viz-modern");
      expect(GuiTool.vizDensityMode()).toBe("dense");
   });

   it("reads the density class when one is present", () => {
      document.body.classList.add("viz-modern", "viz-density-compact");
      expect(GuiTool.vizDensityMode()).toBe("compact");
      document.body.classList.remove("viz-density-compact");
      document.body.classList.add("viz-density-comfortable");
      expect(GuiTool.vizDensityMode()).toBe("comfortable");
   });

   it("reports compact and comfortable as at-least-compact, dense as not", () => {
      document.body.classList.add("viz-modern");
      expect(GuiTool.isVizDensityAtLeastCompact()).toBe(false);
      document.body.classList.add("viz-density-compact");
      expect(GuiTool.isVizDensityAtLeastCompact()).toBe(true);
      document.body.classList.remove("viz-density-compact");
      document.body.classList.add("viz-density-comfortable");
      expect(GuiTool.isVizDensityAtLeastCompact()).toBe(true);
   });
});
```

- [ ] **Step 2: Run it and confirm it fails**

Run from `web/`:
```bash
npx ng test portal --include='**/gui-tool.spec.ts' --watch=false
```
Expected: FAIL — `GuiTool.vizDensityMode is not a function`.

- [ ] **Step 3: Implement the reader**

In `gui-tool.ts`, immediately after `getMiniToolbarHeight()` (currently ending line 75):

```typescript
   // Density reaches the browser as a viz-density-<mode> body class, set by the portal, composer
   // and viewer shells. Bare .viz-modern means dense, matching the _viz-tokens.scss fallback.
   static vizDensityMode(): "dense" | "compact" | "comfortable" {
      if(document.body.classList.contains("viz-density-comfortable")) {
         return "comfortable";
      }

      return document.body.classList.contains("viz-density-compact") ? "compact" : "dense";
   }

   // A 24px strip does not fit dense's 20px title lane, so anchoring is a compact-and-above
   // affordance; dense keeps the hover-revealed overlay.
   static isVizDensityAtLeastCompact(): boolean {
      return GuiTool.vizDensityMode() !== "dense";
   }
```

- [ ] **Step 4: Run it and confirm it passes**

Run from `web/`:
```bash
npx ng test portal --include='**/gui-tool.spec.ts' --watch=false
```
Expected: PASS.

- [ ] **Step 5: Write the failing test for the anchoring gate**

Add to `web/projects/portal/src/app/vsobjects/objects/vs-object-container.component.display.tl.spec.ts`, following the file's existing pattern of toggling body classes around a render:

```typescript
describe("anchoring is a compact-and-above affordance", () => {
   afterEach(() => {
      document.body.classList.remove(
         "viz-modern", "viz-density-dense", "viz-density-compact", "viz-density-comfortable");
   });

   it("does not anchor an anchored-type assembly under dense", async() => {
      document.body.classList.add("viz-modern", "viz-density-dense");
      const { container } = await renderContainerWithChart();
      expect(container.querySelector(".mini-toolbar--anchored")).toBeNull();
   });

   it("anchors the same assembly under compact", async() => {
      document.body.classList.add("viz-modern", "viz-density-compact");
      const { container } = await renderContainerWithChart();
      expect(container.querySelector(".mini-toolbar--anchored")).not.toBeNull();
   });
});
```

If `renderContainerWithChart()` does not already exist in this file, use whatever render helper the
file's existing `viz-modern` anchoring tests call — do not introduce a second helper for the same job.

- [ ] **Step 6: Run it and confirm the dense case fails**

Run from `web/`:
```bash
npx ng test portal --project=test-tl --include='**/vs-object-container.component.display.tl.spec.ts' --watch=false
```
Expected: FAIL on the dense case — the strip anchors today regardless of density.

**Never run the full TL suite unscoped.** It times out and orphans multi-GB node workers. Always pass `--include`.

- [ ] **Step 7: Add the density condition**

In `vs-object-container.component.ts`, change `isKebabResident` (currently lines 482-484). Leave `isToolbarAnchored` alone — it already delegates to `isKebabResident`, so gating the one gates both:

```typescript
   public isKebabResident(object: VSObjectModel): boolean {
      return GuiTool.isVizModern() && GuiTool.isVizDensityAtLeastCompact() &&
         isAnchoredAssemblyType(object.objectType);
   }
```

Update the existing doc comment above it so it states the density condition rather than only the type
condition — it currently explains why touch keeps the design without a lane, and that reasoning still
holds, but a reader needs to know dense opts out entirely.

- [ ] **Step 8: Run both suites and confirm they pass**

Run from `web/`:
```bash
npx ng test portal --include='**/vs-object-container.component*.spec.ts' --watch=false
npx ng test portal --project=test-tl --include='**/vs-object-container.component.display.tl.spec.ts' --watch=false
```
Expected: PASS. If a pre-existing test asserted anchoring without setting a density class, it was
relying on the old unconditional behaviour — add `viz-density-compact` to that test's setup rather than
weakening the new condition.

- [ ] **Step 9: Swap the height literal for the shipped token**

In `mini-toolbar.component.scss`, line 82, inside the `:host-context(.viz-modern) .mini-toolbar .mini-toolbar-container` rule:

```scss
  height: var(--inet-control-height-sm);
```

`--inet-control-height-sm` is `24px` at `scss/_variables.scss:475`, so this is value-identical. Then update
the comment at `gui-tool.ts:61-62` — it says the constant is "coupled to the pinned container height in
mini-toolbar.component.scss"; name the token so the next reader finds it.

Leave `GuiTool.MINI_TOOLBAR_HEIGHT_MODERN = 24` as a number. It is used in JS positioning math, and a
CSS custom property is not readable there without a computed-style call in a hot path.

- [ ] **Step 10: Confirm the compiled CSS is unchanged**

Run from `web/`:
```bash
npx ng test portal --include='**/mini-toolbar.component.spec.ts' --watch=false
```
Expected: PASS. The positioning tests assert 24px; they must still pass, which is what proves the token
resolved to the same value.

- [ ] **Step 11: Commit**

```bash
git add web/projects/portal/src/app/common/util/gui-tool.ts \
        web/projects/portal/src/app/common/util/gui-tool.spec.ts \
        web/projects/portal/src/app/vsobjects/objects/vs-object-container.component.ts \
        web/projects/portal/src/app/vsobjects/objects/vs-object-container.component.display.tl.spec.ts \
        web/projects/portal/src/app/vsobjects/objects/mini-toolbar/mini-toolbar.component.scss
git commit -m "feat(vsobjects): make the anchored mini-toolbar a compact-and-above affordance

A 24px strip does not fit dense's 20px title lane. Dense now keeps the
hover-revealed overlay that still ships; compact and comfortable anchor.
Also binds the pinned strip height to --inet-control-height-sm, the same
24px as a shipped step.

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
```

---

### Task 2: Give the title lane a density row

**Files:**
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/VSDensityDefaults.java:70-115`
- Test: `core/src/test/java/inetsoft/uql/viewsheet/internal/VSDensityDefaultsTest.java`

**Interfaces:**
- Consumes: `VSDensityDefaults.isModern(): boolean` (`:39`), `VSDensityDefaults.mode(): String` (`:54`), and the private constants `COMFORTABLE`/`COMPACT`/`DENSE` (`:117-119`).
- Produces: `public static int titleHeight()` returning `20` under dense or gate-off, `26` under compact, `30` under comfortable.

**Background the implementer needs.** `VSDensityDefaults` already carries three height methods —
`rowHeight()` (`:70`), `headerRowHeight()` (`:77`) and `cellHeight()` (`:85`) — each shaped
`isModern() ? <perMode> : AssetUtil.defh`. `titleHeight()` is a fourth of exactly that shape. It is
**not** mark-gated: none of its three siblings are, and the seed mark's future change gates all the
height methods at once. Making this one wait would leave it inconsistent with the row it joins.

Dense returns `AssetUtil.defh` rather than a literal `20`, matching `rowHeight()`'s gate-off branch —
dense's parity obligation is with `defh`, not with the number `defh` happens to equal.

- [ ] **Step 1: Write the failing test**

Add to `VSDensityDefaultsTest.java`, following the file's existing property-stubbing pattern:

```java
   @Test
   void titleHeightIsDefhWhenGateIsOff() {
      withModern(false, () -> assertEquals(AssetUtil.defh, VSDensityDefaults.titleHeight()));
   }

   @Test
   void titleHeightIsDefhUnderDense() {
      withModernMode("dense", () -> assertEquals(AssetUtil.defh, VSDensityDefaults.titleHeight()));
   }

   @Test
   void titleHeightGrowsToHoldTheStripUnderCompact() {
      withModernMode("compact", () -> assertEquals(26, VSDensityDefaults.titleHeight()));
   }

   @Test
   void titleHeightIsThirtyUnderComfortable() {
      withModernMode("comfortable", () -> assertEquals(30, VSDensityDefaults.titleHeight()));
   }
```

Use the helpers the existing tests in this file already use to stub `viewsheet.modernVisualization`
and `viewsheet.density`; if they are named differently from `withModern`/`withModernMode`, use the
existing names rather than adding new helpers.

- [ ] **Step 2: Run it and confirm it fails**

Run from the repo root:
```bash
./mvnw test -pl core -Dtest=VSDensityDefaultsTest
```
Expected: FAIL — `cannot find symbol: method titleHeight()`.

- [ ] **Step 3: Implement it**

In `VSDensityDefaults.java`, after `cellHeight()` (currently ending line 87):

```java
   /**
    * The title lane's height. Compact and comfortable borrow the header row's steps so the lane can
    * hold the 24px anchored strip with clearance; dense stays at defh, which is the one tier that
    * must equal legacy and the one where the strip does not anchor at all.
    */
   public static int titleHeight() {
      return isModern() ? titleHeightForMode(mode()) : AssetUtil.defh;
   }

   static int titleHeightForMode(String mode) {
      switch(normalizeMode(mode)) {
      case COMFORTABLE:
         return 30;
      case COMPACT:
         return 26;
      default:
         return AssetUtil.defh;
      }
   }
```

Match `rowHeightForMode`'s existing brace and indentation style rather than this block's, if the two
differ — the surrounding file wins.

- [ ] **Step 4: Run it and confirm it passes**

Run from the repo root:
```bash
./mvnw test -pl core -Dtest=VSDensityDefaultsTest
```
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/inetsoft/uql/viewsheet/internal/VSDensityDefaults.java \
        core/src/test/java/inetsoft/uql/viewsheet/internal/VSDensityDefaultsTest.java
git commit -m "feat(viewsheet): give the title lane a density row at 20/26/30

Compact and comfortable borrow the header row's steps so the lane holds the
24px anchored strip with clearance. Dense stays at defh, the tier that must
equal legacy and the one where the strip does not anchor.

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
```

---

### Task 3: Wire the title height into the assemblies that have a lane

**Files:**
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/ChartVSAssemblyInfo.java:2636`
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/TableDataVSAssemblyInfo.java:241`
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/SelectionBaseVSAssemblyInfo.java:299`
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/CurrentSelectionVSAssemblyInfo.java:205`
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/CalendarVSAssemblyInfo.java:629`

**Interfaces:**
- Consumes: `VSDensityDefaults.titleHeight(): int` from Task 2.
- Produces: nothing new. Five existing `getTitleHeight()` overrides change what they return under the gate.

**Background the implementer needs.** `getTitleHeight()` is overridden per assembly type in eight
classes. Five of them are anchored types or on the rollout path and take the density row. Three do
**not**: `CheckBoxVSAssemblyInfo`, `RadioButtonVSAssemblyInfo` and `TimeSliderVSAssemblyInfo` are input
and range-slider assemblies that never anchor a strip — the widget spec excludes the range slider
permanently — so growing their lane would move layout for no benefit. Leave all three alone.

**Read each override before editing it.** Several return a stored value when the author has set one and
fall back to a default otherwise. The density row belongs in the *default* branch only: an author-set
title height is a USER-tier value and must keep winning, exactly as it does for row height.

- [ ] **Step 1: Read the five overrides and record which branch is the default**

Run from the repo root:
```bash
for f in Chart TableData SelectionBase CurrentSelection Calendar; do
  echo "=== $f ==="
  grep -n "getTitleHeight" -A 12 "core/src/main/java/inetsoft/uql/viewsheet/internal/${f}VSAssemblyInfo.java"
done
```
Write down, per class, which returned expression is the unset-by-author default. If any of the five has
no default branch — it always returns a stored value — stop and raise it: that class needs its default
established before a density row can reach it, which is a different change from this one.

- [ ] **Step 2: Write the failing test**

Create `core/src/test/java/inetsoft/uql/viewsheet/internal/TitleHeightDensityTest.java`:

```java
   @Test
   void chartTitleLaneFollowsDensityWhenTheAuthorHasNotSetOne() {
      ChartVSAssemblyInfo info = new ChartVSAssemblyInfo();
      withModernMode("compact", () -> assertEquals(26, info.getTitleHeight()));
      withModernMode("dense", () -> assertEquals(AssetUtil.defh, info.getTitleHeight()));
   }

   @Test
   void anAuthorSetTitleHeightStillWinsUnderEveryDensity() {
      ChartVSAssemblyInfo info = new ChartVSAssemblyInfo();
      info.setTitleHeight(44);
      withModernMode("compact", () -> assertEquals(44, info.getTitleHeight()));
      withModernMode("comfortable", () -> assertEquals(44, info.getTitleHeight()));
   }

   @Test
   void theRangeSliderLaneNeverMoves() {
      TimeSliderVSAssemblyInfo info = new TimeSliderVSAssemblyInfo();
      final int before = info.getTitleHeight();
      withModernMode("comfortable", () -> assertEquals(before, info.getTitleHeight()));
   }
```

Reuse the gate-stubbing helper from `VSDensityDefaultsTest`; if it is private there, extract it to a
package-visible test helper in the same package rather than duplicating the stubbing logic.

- [ ] **Step 3: Run it and confirm it fails**

Run from the repo root:
```bash
./mvnw test -pl core -Dtest=TitleHeightDensityTest
```
Expected: FAIL on the compact case — the lane is `defh` at every density today.

- [ ] **Step 4: Point each default branch at the density row**

In each of the five classes, replace the default-branch constant with `VSDensityDefaults.titleHeight()`
and add the import. The author-set branch is untouched. For example, where a class currently returns
`AssetUtil.defh` as its unset default:

```java
      return VSDensityDefaults.titleHeight();
```

Do not add a `VSDensityDefaults.isModern()` check at the call site. `titleHeight()` already returns
`AssetUtil.defh` when the gate is off, so a second check would be redundant and would drift.

- [ ] **Step 5: Run the new test and the surrounding suites**

Run from the repo root:
```bash
./mvnw test -pl core -Dtest=TitleHeightDensityTest
./mvnw test -pl core -Dtest='VS*AssemblyInfoTest'
```
Expected: PASS. A failure in the second command means an existing test asserted `defh` under a modern
gate with a non-dense density; check whether that test set a density at all before changing it.

- [ ] **Step 6: Confirm the lane holds the strip end to end**

Build and start the server, set Modern Visualization on and density to compact in the EM Look and Feel
page, then open a dashboard with a chart, a table and a selection list:

```bash
./mvnw clean install -DskipTests -pl core -am
cd docker/target/docker-test && docker compose up -d
```

Check: the strip sits inside the title lane with clearance at compact and comfortable; under dense the
strip is absent until hover, and the lane is the same height it was before this change. Compare a dense
screenshot against one taken with the gate off — they must match.

- [ ] **Step 7: Commit**

```bash
git add core/src/main/java/inetsoft/uql/viewsheet/internal/ChartVSAssemblyInfo.java \
        core/src/main/java/inetsoft/uql/viewsheet/internal/TableDataVSAssemblyInfo.java \
        core/src/main/java/inetsoft/uql/viewsheet/internal/SelectionBaseVSAssemblyInfo.java \
        core/src/main/java/inetsoft/uql/viewsheet/internal/CurrentSelectionVSAssemblyInfo.java \
        core/src/main/java/inetsoft/uql/viewsheet/internal/CalendarVSAssemblyInfo.java \
        core/src/test/java/inetsoft/uql/viewsheet/internal/TitleHeightDensityTest.java
git commit -m "feat(viewsheet): read the title lane height from the density row

Chart, table family, selection family, current selection and calendar take
the lane's density height when the author has not set one. Input assemblies
and the range slider keep theirs; they never anchor a strip.

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
```

---

## What this plan does not do

- **It does not gate the heights on the seed mark.** The mark does not exist, and none of the three
  existing height methods are gated on it. When the mark lands, all four gate together in one change.
- **It does not touch the range slider, check box or radio button lanes.** They never anchor.
- **It does not change `GuiTool.MINI_TOOLBAR_HEIGHT_MODERN`.** The strip keeps its size; the lane grows.
  That is the decision's own framing: "the lane grows; the strip keeps its size."
