# Selection Family Padding Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give the selection list, tree and container a density card inset and cell padding, and a default size that follows density so a default-size list still shows five rows.

**Architecture:** Both values seed in `SelectionBaseVSAssemblyInfo.seedChromeDefaults`, the one hook that fires at creation and on Modernize. The browser subtracts the inset in two methods — `getBodyHeight()` and `getBodyWidth()` — because selections have no column grid. Export lands in two shared helper bases rather than per-format leaves, gated by the existing `insetsTableCard()` predicate so Excel and CSV stay out.

**Tech Stack:** Java 21, Angular 21.2, Vitest 4.1.7, Maven.

**Spec:** `docs/superpowers/specs/lookfeel/2026-10-01-selection-family-padding-design.md`

## Global Constraints

- **Card inset matrix: 16 / 12 / 8** (comfortable / compact / dense). Shared with chart and table via `VSDensityDefaults.chartPaddingForMode`. No new numbers.
- **Cell padding matrix: 6·8 / 4·6 / 3·4.** Shared with table via `VSDensityDefaults.cellPaddingForMode`.
- **Title lane: 30 / 26 / 20** (`titleHeightForMode`). **Selection cell height: 28 / 24 / 20** (`rowHeightForMode`).
- **Legacy default size: `AssetUtil.defw` × `AssetUtil.defh * 6` = 100 × 120.**
- **Density default size: 132×202 / 124×170 / 116×136.** Formula: `width = inset.left + 100 + inset.right`, `height = inset.top + title + 5 × cell + inset.bottom`.
- **No migration.** Nothing rewrites a stored size, inset or cell padding on load. Only creation and an explicit Modernize change an assembly.
- **The size rule rewrites only sizes it could have written** — the legacy default or a tier default. Any other size is an author size.
- `web/tsconfig.json` has `strict: false` and `strictTemplates: false`. **Angular template bindings are not type-checked** — removing a model field will not produce a build error at a template that reads it.
- Comments are *why*, not *what*; no comments in `.html` files; no ticket or PR numbers in source comments.

## How to run tests

```bash
# one core test class (from community/)
./mvnw test -pl core -Dtest=SelectionCardInsetSeedTest

# one core test method
./mvnw test -pl core -Dtest=SelectionCardInsetSeedTest#seedsTheTierInsetAtEachDensity

# one frontend spec (from community/web/)
npx ng test portal --include="**/vs-selection.component.spec.ts"

# one testing-library spec — note the portal:test-tl target; `ng test portal` matches no .tl files
npx ng run portal:test-tl --include="**/selection-general-pane.component.tl.spec.ts"
```

**Never run the full TL suite.** Always scope with `--include`.

## Review Focus

Five behaviours the spec implies that no task's happy path would exercise. Each has its test assigned to the task that owns the code.

1. **A selection inside a selection container must not double-inset.** The container insets its children; the child then insets its own rows inside the space it was given. If both apply the full inset, a nested list loses twice the space. **Task 7**, `aNestedListDoesNotDoubleInset`.
2. **An author-resized selection must survive Modernize.** The size rule runs on Modernize, so without the recognition guard it would overwrite a size the author chose. **Task 5**, `anAuthorSizeIsLeftAlone`.
3. **A `format.css` padding must beat the density seed, wholesale.** `CompositeValue` resolves USER > CSS > DEFAULT; the seed must not overwrite a CSS value or be added to it. **Task 4**, `aCssPaddingBeatsTheSeed`.
4. **Excel must show no inset, including for trees.** `ExcelSelectionTreeHelper` inherits through the shared base, so it would pick the inset up unless the `insetsTableCard()` gate stops it. **Task 9**, `excelTreeTakesNoInset`.
5. **A dropdown selection must not inset its panel.** `getBodyHeight()`'s dropdown branch computes `cellHeight * listHeight` and never reads `objectFormat.height`, so an inset applied there would shrink the panel for no reason. **Task 7**, `aDropdownPanelIsUnchanged`.

---

### Task 1: Converge the padding model shape

Prerequisite, and its own commit. Selections become the fifth assembly to carry a card inset; converging first means they declare an existing field rather than a sixth variant of one.

**Files:**
- Modify: `core/src/main/java/inetsoft/web/viewsheet/model/chart/VSChartModel.java:72-75, 426-439, 647`
- Modify: `core/src/main/java/inetsoft/web/viewsheet/model/VSGaugeModel.java:35-38, 60-73, 92`
- Modify: `core/src/main/java/inetsoft/web/viewsheet/model/VSTextModel.java:46-49, 166-179, 220-223`
- Modify: `web/projects/portal/src/app/vsobjects/model/vs-object-model.ts`
- Modify: `web/projects/portal/src/app/vsobjects/model/vs-chart-model.ts:43-46`
- Modify: `web/projects/portal/src/app/vsobjects/model/output/vs-gauge-model.ts:24-27`
- Modify: `web/projects/portal/src/app/vsobjects/model/output/vs-text-model.ts:29-32`
- Modify: `web/projects/portal/src/app/vsobjects/objects/table/table-content-rect.ts:18-23`
- Modify: `web/projects/portal/src/app/vsobjects/objects/vs-object-container.component.ts:531-543, 1075-1076`
- Modify: `web/projects/portal/src/app/vsobjects/objects/chart/vs-chart.component.html:65,66,73`
- Modify: `web/projects/portal/src/app/vsobjects/objects/chart/vs-chart.component.ts:1503-1504`
- Modify: `web/projects/portal/src/app/graph/objects/chart-area.component.ts:1411,1413,1426,1428,1444,1459`
- Modify: `web/projects/portal/src/app/vsobjects/objects/output/gauge/vs-gauge.component.html:42-45`
- Modify: `web/projects/portal/src/app/vsobjects/objects/output/gauge/vs-gauge.component.ts:84,96`
- Modify: `web/projects/portal/src/app/vsobjects/objects/output/text/vs-text.component.html:56-59`

**Interfaces:**
- Produces: `TablePadding { top, left, bottom, right }` exported from `vs-object-model.ts`; `VSObjectModel.padding?: TablePadding`. Later tasks read `object.padding`.

- [ ] **Step 1: Move `TablePadding` to the base model file**

In `vs-object-model.ts`, above the `VSObjectModel` interface:

```typescript
/**
 * The card inset an assembly draws its content inside. Optional because only the assemblies that
 * have a card carry one; the rest resolve to no inset.
 */
export interface TablePadding {
   top: number;
   left: number;
   bottom: number;
   right: number;
}
```

Add to the `VSObjectModel` interface body:

```typescript
   padding?: TablePadding;
```

In `table-content-rect.ts`, delete the local `TablePadding` declaration and re-export so existing importers keep working:

```typescript
import { TablePadding } from "../../model/vs-object-model";
export { TablePadding };
```

- [ ] **Step 2: Swap the three Java models to `Insets`**

In each of `VSChartModel`, `VSGaugeModel` and `VSTextModel`, replace the four int assignments with one, the four getters with one, and the four fields with one. For `VSChartModel`:

```java
      this.padding = info.getPadding();
```

```java
   public Insets getPadding() {
      return padding;
   }
```

```java
   private Insets padding;
```

`VSTextModel`'s field is `final`; keep it final. Add `import java.awt.Insets;` where absent. Delete `getPaddingTop/Left/Bottom/Right` from all three.

- [ ] **Step 3: Swap the three TS model interfaces**

In each of `vs-chart-model.ts`, `vs-gauge-model.ts` and `vs-text-model.ts`, delete the four `padding*?: number` lines. They inherit `padding?: TablePadding` from `VSObjectModel`.

- [ ] **Step 4: Collapse the fork in `vs-object-container.component.ts`**

Replace `getLaneInset` (`:531-543`) with:

```typescript
   /** The card inset the title lane sits inside. An assembly without a card has none. */
   private static getLaneInset(object: VSObjectModel): { top: number, left: number, right: number } {
      return object.padding ?? { top: 0, left: 0, right: 0, bottom: 0 };
   }
```

At `:1075-1076`, inside `getChartAnnotationRestrictTo`:

```typescript
         x: fmt.left + contentBounds.x + (chart.padding?.left || 0),
         y: fmt.top + contentBounds.y + (chart.padding?.top || 0) + titleHeight,
```

- [ ] **Step 5: Update the remaining TypeScript consumers**

`vs-chart.component.ts:1503-1504`:

```typescript
      return new Rectangle(chartContainerBounds.x + contentBounds.x + (this.model.padding?.left || 0),
         chartContainerBounds.y + contentBounds.y + (this.model.padding?.top || 0) + titleHeight,
```

`vs-gauge.component.ts:84,96`:

```typescript
         width -= (this.model.padding?.left || 0) + (this.model.padding?.right || 0);
```

```typescript
         height -= (this.model.padding?.top || 0) + (this.model.padding?.bottom || 0);
```

`chart-area.component.ts` — six reads at `:1411, 1413, 1426, 1428, 1444, 1459`. Each `chart.paddingX` becomes `(chart.padding?.X || 0)`; `chartModel.paddingTop` and `chartModel.paddingLeft` likewise.

- [ ] **Step 6: Update the eleven template bindings — these are NOT compiler-checked**

`strictTemplates: false`, so a missed binding compiles and renders padding 0. Grep to prove none is left:

```bash
cd web/projects && grep -rn "padding\(Top\|Left\|Bottom\|Right\)" --include=*.html . | grep -v node_modules | grep -v "cell\.view\."
```

Expected after the edits: no matches.

`vs-chart.component.html:65,66,73`:

```html
        [style.left.px]="model.padding?.left"
        [style.top.px]="model.padding?.top"
```

```html
        [titleWidth]="model.objectFormat.width - (model.padding?.left || 0) - (model.padding?.right || 0)"
```

`vs-gauge.component.html:42-45` and `vs-text.component.html:56-59` take the same shape:

```html
    [style.padding-top.px]="model.padding?.top"
    [style.padding-left.px]="model.padding?.left"
    [style.padding-bottom.px]="model.padding?.bottom"
    [style.padding-right.px]="model.padding?.right"
```

- [ ] **Step 7: Update the three TL specs**

```bash
cd web/projects && grep -rln "padding\(Top\|Left\|Bottom\|Right\)" --include=*.spec.ts . | grep -v node_modules
```

Expected: `chart-area.component.display.tl.spec.ts`, `vs-simple-cell.component.tl.spec.ts`, `vs-object-container.component.display.tl.spec.ts`. In each, replace the flat fixture fields with `padding: { top: N, left: N, bottom: N, right: N }`.

- [ ] **Step 8: Build and run the affected specs**

```bash
./mvnw -q -pl core compile
cd web && npx ng test portal --include="**/vs-object-container.component.spec.ts"
cd web && npx ng run portal:test-tl --include="**/vs-object-container.component.display.tl.spec.ts"
cd web && npx ng run portal:test-tl --include="**/chart-area.component.display.tl.spec.ts"
```

Expected: compile clean, all three green.

- [ ] **Step 9: Commit**

```bash
git add core/src/main/java/inetsoft/web/viewsheet/model web/projects/portal/src/app
git commit -m "Converge the card inset onto one model shape"
```

---

### Task 2: The cell padding's author predicate on the selection base

**Files:**
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/SelectionBaseVSAssemblyInfo.java` (beside `setCellPadding` at `:161`)
- Test: `core/src/test/java/inetsoft/uql/viewsheet/internal/SelectionCellPaddingTierTest.java`

**Interfaces:**
- Produces: `SelectionBaseVSAssemblyInfo.isUserCellPadding()` and `resetUserCellPadding()`. Tasks 4 and 8 call both.

- [ ] **Step 1: Write the failing test**

```java
@Test
void theAuthorPredicateFollowsTheUserTier() {
   SelectionListVSAssemblyInfo info = new SelectionListVSAssemblyInfo();

   assertFalse(info.isUserCellPadding());

   info.setCellPadding(new Insets(6, 8, 6, 8), CompositeValue.Type.DEFAULT);
   assertFalse(info.isUserCellPadding(), "a seeded value is not an author opinion");

   info.setCellPadding(new Insets(2, 2, 2, 2), CompositeValue.Type.USER);
   assertTrue(info.isUserCellPadding());

   info.resetUserCellPadding();
   assertFalse(info.isUserCellPadding());
   assertEquals(new Insets(6, 8, 6, 8), info.getCellPadding(),
                "clearing the opinion falls back to the seed, not to null");
}
```

- [ ] **Step 2: Run it and watch it fail**

```bash
./mvnw test -pl core -Dtest=SelectionCellPaddingTierTest#theAuthorPredicateFollowsTheUserTier
```

Expected: FAIL, `cannot find symbol: method isUserCellPadding()`.

- [ ] **Step 3: Add both methods**

Mirroring `TableDataVSAssemblyInfo:940-951`:

```java
   /**
    * Whether the author set the cell padding. Surfaced in the property dialog as the cell padding
    * pane's follow-the-default checkbox, inverted.
    */
   public boolean isUserCellPadding() {
      return cellPadding.hasUserValue();
   }

   /**
    * Drop the author's cell padding and let the density decide again. What the follow-the-default
    * checkbox calls when it is checked - writing the density value into the USER tier instead
    * would pin the current tier.
    */
   public void resetUserCellPadding() {
      cellPadding.resetUserValue();
   }
```

- [ ] **Step 4: Run it and watch it pass**

```bash
./mvnw test -pl core -Dtest=SelectionCellPaddingTierTest
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/inetsoft/uql/viewsheet/internal/SelectionBaseVSAssemblyInfo.java core/src/test/java/inetsoft/uql/viewsheet/internal/SelectionCellPaddingTierTest.java
git commit -m "Give the selection base its cell padding author predicate"
```

---

### Task 3: The selection size matrix and its recognition predicate

Keeps the numbers in `VSDensityDefaults` beside the matrices they are built from, mirroring `isControlHeight(int)` at `:178`.

**Files:**
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/VSDensityDefaults.java`
- Test: `core/src/test/java/inetsoft/uql/viewsheet/internal/SelectionSizeMatrixTest.java`

**Interfaces:**
- Produces: `VSDensityDefaults.selectionSize(VizContext)` returning `Dimension`, and `VSDensityDefaults.isSeededSelectionSize(Dimension)` returning `boolean`. Task 5 calls both.

- [ ] **Step 1: Write the failing test**

```java
@Test
void theSizeMatrixHoldsFiveRowsAtEveryTier() {
   assertEquals(new Dimension(132, 202), sizeAt("comfortable"));
   assertEquals(new Dimension(124, 170), sizeAt("compact"));
   assertEquals(new Dimension(116, 136), sizeAt("dense"));
}

@Test
void aLegacyContextKeepsTheLegacySize() {
   SreeEnv.setProperty("viewsheet.density", "comfortable");
   assertEquals(new Dimension(100, 120), VSDensityDefaults.selectionSize(VizContext.of(null)));
}

@Test
void theRecognitionPredicateAcceptsOnlySeededSizes() {
   assertTrue(VSDensityDefaults.isSeededSelectionSize(new Dimension(100, 120)), "legacy");
   assertTrue(VSDensityDefaults.isSeededSelectionSize(new Dimension(132, 202)), "comfortable");
   assertTrue(VSDensityDefaults.isSeededSelectionSize(new Dimension(124, 170)), "compact");
   assertTrue(VSDensityDefaults.isSeededSelectionSize(new Dimension(116, 136)), "dense");

   assertFalse(VSDensityDefaults.isSeededSelectionSize(new Dimension(300, 400)), "author size");
   assertFalse(VSDensityDefaults.isSeededSelectionSize(new Dimension(132, 203)), "one off the tier");
   assertFalse(VSDensityDefaults.isSeededSelectionSize(new Dimension(202, 132)), "axes swapped");
}

private Dimension sizeAt(String density) {
   SreeEnv.setProperty("viewsheet.density", density);
   return VSDensityDefaults.selectionSize(VizContext.of(VizMark.MODERN_LIGHT));
}

@AfterEach
void reset() {
   SreeEnv.setProperty("viewsheet.density", null);
}
```

- [ ] **Step 2: Run it and watch it fail**

```bash
./mvnw test -pl core -Dtest=SelectionSizeMatrixTest
```

Expected: FAIL, `cannot find symbol: method selectionSize(VizContext)`.

- [ ] **Step 3: Add both methods**

```java
   /**
    * The default size of a selection list or tree: the card inset around a title lane and five
    * rows. Five because that is what the legacy 100x120 default showed, and because a row count
    * that varied by tier would be a worse inconsistency than the one the inset removes.
    *
    * The width's content half stays at defw: density has an opinion about row height and the
    * title lane, and none about how wide a label column should be.
    */
   public static Dimension selectionSize(VizContext ctx) {
      if(!ctx.modern) {
         return new Dimension(AssetUtil.defw, AssetUtil.defh * SELECTION_ROWS);
      }

      return selectionSizeForMode(ctx.density);
   }

   /**
    * Whether a size is one selectionSize() could have written - the legacy default, or a tier
    * default. Anything else is an author size, and the seed must leave it alone.
    *
    * An author cell height takes the assembly out of this set too, since the size it produced
    * will not match any tier. That is correct: an author who sized the rows has an opinion about
    * the box as well.
    */
   public static boolean isSeededSelectionSize(Dimension size) {
      if(size == null) {
         return false;
      }

      if(size.width == AssetUtil.defw && size.height == AssetUtil.defh * SELECTION_ROWS) {
         return true;
      }

      return size.equals(selectionSizeForMode(COMFORTABLE))
         || size.equals(selectionSizeForMode(COMPACT))
         || size.equals(selectionSizeForMode(DENSE));
   }

   private static Dimension selectionSizeForMode(String mode) {
      Insets inset = chartPaddingForMode(mode);
      return new Dimension(inset.left + AssetUtil.defw + inset.right,
                           inset.top + titleHeightForMode(mode)
                              + SELECTION_ROWS * rowHeightForMode(mode) + inset.bottom);
   }

   /**
    * Five data rows. The legacy default was defh * 6 - a 20px title lane over five 20px rows - so
    * the lane is counted separately in both branches above and this constant means one thing.
    */
   private static final int SELECTION_ROWS = 5;
```

and the legacy branch of `selectionSize` reads:

```java
         return new Dimension(AssetUtil.defw, AssetUtil.defh * (SELECTION_ROWS + 1));
```

- [ ] **Step 4: Run it and watch it pass**

```bash
./mvnw test -pl core -Dtest=SelectionSizeMatrixTest
```

Expected: PASS, 3 tests.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/inetsoft/uql/viewsheet/internal/VSDensityDefaults.java core/src/test/java/inetsoft/uql/viewsheet/internal/SelectionSizeMatrixTest.java
git commit -m "Add the selection size matrix and its recognition predicate"
```

---

### Task 4: Seed the card inset and cell padding

**Files:**
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/SelectionBaseVSAssemblyInfo.java:934-949` (`seedChromeDefaults`), plus a new `defaultPadding` override
- Test: `core/src/test/java/inetsoft/uql/viewsheet/internal/SelectionCardInsetSeedTest.java`

**Interfaces:**
- Consumes: `isUserCellPadding()` from Task 2.
- Produces: a seeded `getPadding()` and `getCellPadding()` on all three selection types.

- [ ] **Step 1: Write the failing tests**

```java
@Test
void seedsTheTierInsetAtEachDensity() {
   assertEquals(new Insets(16, 16, 16, 16), seededInset("comfortable"));
   assertEquals(new Insets(12, 12, 12, 12), seededInset("compact"));
   assertEquals(new Insets(8, 8, 8, 8), seededInset("dense"));
}

@Test
void seedsTheTierCellPaddingAtEachDensity() {
   assertEquals(new Insets(6, 8, 6, 8), seededCellPadding("comfortable"));
   assertEquals(new Insets(4, 6, 4, 6), seededCellPadding("compact"));
   assertEquals(new Insets(3, 4, 3, 4), seededCellPadding("dense"));
}

@Test
void revertClearsBothValues() {
   SelectionListVSAssemblyInfo info = seeded("comfortable");
   info.setVizMark(null);
   info.seedChromeDefaults(VizContext.of(info));

   assertEquals(new Insets(0, 0, 0, 0), info.getPadding(), "inset back to legacy zero");
   assertNull(info.getCellPadding(), "cell padding back to legacy absence");
}

/** Review Focus 3. */
@Test
void aCssPaddingBeatsTheSeed() {
   SelectionListVSAssemblyInfo info = new SelectionListVSAssemblyInfo();
   info.setVizMark(VizMark.MODERN_LIGHT);
   info.setCellPadding(new Insets(1, 1, 1, 1), CompositeValue.Type.CSS);
   SreeEnv.setProperty("viewsheet.density", "comfortable");

   info.seedChromeDefaults(VizContext.of(info));

   assertEquals(new Insets(1, 1, 1, 1), info.getCellPadding(),
                "CSS beats DEFAULT wholesale - the seed must not replace it or add to it");
}

@Test
void anAuthorCellPaddingBeatsBoth() {
   SelectionListVSAssemblyInfo info = new SelectionListVSAssemblyInfo();
   info.setVizMark(VizMark.MODERN_LIGHT);
   info.setCellPadding(new Insets(1, 1, 1, 1), CompositeValue.Type.CSS);
   info.setCellPadding(new Insets(9, 9, 9, 9), CompositeValue.Type.USER);
   SreeEnv.setProperty("viewsheet.density", "comfortable");

   info.seedChromeDefaults(VizContext.of(info));

   assertEquals(new Insets(9, 9, 9, 9), info.getCellPadding());
}

private Insets seededInset(String density) {
   return seeded(density).getPadding();
}

private Insets seededCellPadding(String density) {
   return seeded(density).getCellPadding();
}

private SelectionListVSAssemblyInfo seeded(String density) {
   SreeEnv.setProperty("viewsheet.density", density);
   SelectionListVSAssemblyInfo info = new SelectionListVSAssemblyInfo();
   info.setVizMark(VizMark.MODERN_LIGHT);
   info.seedChromeDefaults(VizContext.of(info));
   return info;
}

@AfterEach
void reset() {
   SreeEnv.setProperty("viewsheet.density", null);
}
```

- [ ] **Step 2: Run them and watch them fail**

```bash
./mvnw test -pl core -Dtest=SelectionCardInsetSeedTest
```

Expected: FAIL — `seedsTheTierInsetAtEachDensity` gets `Insets(0,0,0,0)`, `seedsTheTierCellPaddingAtEachDensity` gets `null`.

- [ ] **Step 3: Add the override and the two seed branches**

In `SelectionBaseVSAssemblyInfo`, beside the other overrides:

```java
   @Override
   protected Insets defaultPadding(VizContext ctx) {
      return VSDensityDefaults.tablePadding(ctx);
   }
```

In `seedChromeDefaults`, after the existing `super.seedChromeDefaults(ctx)` call and the cell foreground block:

```java
      // the cell's content inset. Seeded rather than resolved at render so it travels in an
      // exported asset. Both branches write: Revert calls this with an unmarked context and needs
      // the legacy absence restored, not the modern value left in place
      if(!isUserCellPadding()) {
         setCellPadding(VSDensityDefaults.cellPadding(ctx), CompositeValue.Type.DEFAULT);
      }

      // the card inset. A format.css padding on the assembly class installed its own through
      // setCSSDefaults and keeps it, which is what isCssPaddingDefined guards
      if(!isUserPadding() && !isCssPaddingDefined()) {
         setPadding(VSDensityDefaults.tablePadding(ctx));
      }
```

- [ ] **Step 4: Run them and watch them pass**

```bash
./mvnw test -pl core -Dtest=SelectionCardInsetSeedTest
```

Expected: PASS, 5 tests.

- [ ] **Step 5: Run the neighbouring suites for regressions**

```bash
./mvnw test -pl core -Dtest='*Selection*,*Density*,*ChromeDefaults*'
```

Expected: all pass.

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/inetsoft/uql/viewsheet/internal/SelectionBaseVSAssemblyInfo.java core/src/test/java/inetsoft/uql/viewsheet/internal/SelectionCardInsetSeedTest.java
git commit -m "Seed the selection family's card inset and cell padding"
```

---

### Task 5: The density-aware default size

**Files:**
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/SelectionBaseVSAssemblyInfo.java` (`seedChromeDefaults`)
- Test: `core/src/test/java/inetsoft/uql/viewsheet/internal/SelectionDensitySizeTest.java`

**Interfaces:**
- Consumes: `VSDensityDefaults.selectionSize(VizContext)` and `isSeededSelectionSize(Dimension)` from Task 3.

- [ ] **Step 1: Write the failing tests**

```java
@Test
void aFreshListTakesTheTierSize() {
   assertEquals(new Dimension(132, 202), sizeAfterSeed("comfortable", new Dimension(100, 120)));
   assertEquals(new Dimension(124, 170), sizeAfterSeed("compact", new Dimension(100, 120)));
   assertEquals(new Dimension(116, 136), sizeAfterSeed("dense", new Dimension(100, 120)));
}

/** Review Focus 2. */
@Test
void anAuthorSizeIsLeftAlone() {
   assertEquals(new Dimension(300, 400), sizeAfterSeed("comfortable", new Dimension(300, 400)));
}

@Test
void aDensityChangeMovesASeededSizeToTheNewTier() {
   assertEquals(new Dimension(124, 170), sizeAfterSeed("compact", new Dimension(132, 202)),
                "a size seeded at comfortable follows to compact rather than stranding");
}

@Test
void revertRestoresTheLegacySize() {
   SreeEnv.setProperty("viewsheet.density", "comfortable");
   SelectionListVSAssemblyInfo info = new SelectionListVSAssemblyInfo();
   info.setPixelSize(new Dimension(132, 202));
   info.setVizMark(null);

   info.seedChromeDefaults(VizContext.of(info));

   assertEquals(new Dimension(100, 120), info.getPixelSize());
}

@Test
void revertLeavesAnAuthorSizeAlone() {
   SreeEnv.setProperty("viewsheet.density", "comfortable");
   SelectionListVSAssemblyInfo info = new SelectionListVSAssemblyInfo();
   info.setPixelSize(new Dimension(300, 400));
   info.setVizMark(null);

   info.seedChromeDefaults(VizContext.of(info));

   assertEquals(new Dimension(300, 400), info.getPixelSize());
}

/** Spec D6: loading an asset must not resize it. The parse funnel does not call the hook. */
@Test
void parsingAnAssetDoesNotResizeIt() throws Exception {
   SreeEnv.setProperty("viewsheet.density", "comfortable");
   SelectionListVSAssemblyInfo saved = new SelectionListVSAssemblyInfo();
   saved.setVizMark(VizMark.MODERN_LIGHT);
   saved.setPixelSize(new Dimension(100, 120));

   SelectionListVSAssemblyInfo loaded = new SelectionListVSAssemblyInfo();
   loaded.parseXML(toElement(saved));

   assertEquals(new Dimension(100, 120), loaded.getPixelSize(),
                "a marked list stored at the legacy size stays there until something seeds it");
}

@Test
void theContainerKeepsItsOwnBasis() {
   SreeEnv.setProperty("viewsheet.density", "comfortable");
   CurrentSelectionVSAssemblyInfo info = new CurrentSelectionVSAssemblyInfo();
   Dimension before = info.getPixelSize();
   info.setVizMark(VizMark.MODERN_LIGHT);

   info.seedChromeDefaults(VizContext.of(info));

   assertEquals(new Dimension(before.width + 32, before.height + 32), info.getPixelSize(),
                "the container grows by its inset; the five-row rule is not its rule");
}

private Dimension sizeAfterSeed(String density, Dimension start) {
   SreeEnv.setProperty("viewsheet.density", density);
   SelectionListVSAssemblyInfo info = new SelectionListVSAssemblyInfo();
   info.setPixelSize(start);
   info.setVizMark(VizMark.MODERN_LIGHT);
   info.seedChromeDefaults(VizContext.of(info));
   return info.getPixelSize();
}

@AfterEach
void reset() {
   SreeEnv.setProperty("viewsheet.density", null);
}
```

- [ ] **Step 2: Run them and watch them fail**

```bash
./mvnw test -pl core -Dtest=SelectionDensitySizeTest
```

Expected: FAIL — `aFreshListTakesTheTierSize` gets `100x120`.

- [ ] **Step 3: Add the size rule to `seedChromeDefaults`**

In `SelectionBaseVSAssemblyInfo.seedChromeDefaults`, after the two seed branches from Task 4:

```java
      // the box follows density, because the content inside it does. A tier's taller rows and
      // title lane cost a default-size list two of its five rows, and the inset would cost a
      // third, so the box grows to keep the row count the legacy default had.
      //
      // Guarded on a size this rule could itself have written, so an author who sized the
      // assembly keeps their size through a Modernize or a density change. Revert reverses it,
      // or a reverted list keeps a box sized for rows it no longer has
      if(isSeededSize(getPixelSize())) {
         setPixelSize(seededSize(ctx));
      }
```

with the two overridable members on `SelectionBaseVSAssemblyInfo` that the container replaces:

```java
   /** The size this type takes when nobody has sized it. */
   protected Dimension seededSize(VizContext ctx) {
      return VSDensityDefaults.selectionSize(ctx);
   }

   /** Whether a size is one seededSize() could have written, at any tier or legacy. */
   protected boolean isSeededSize(Dimension size) {
      return VSDensityDefaults.isSeededSelectionSize(size);
   }
```

Override both in `CurrentSelectionVSAssemblyInfo`:

```java
   /**
    * The container holds child assemblies, not rows, so it grows by its inset alone - the
    * five-row rule that sizes a list says nothing about it.
    */
   @Override
   protected Dimension seededSize(VizContext ctx) {
      Insets inset = VSDensityDefaults.tablePadding(ctx);
      return new Dimension(3 * AssetUtil.defw + inset.left + inset.right,
                           12 * AssetUtil.defh + inset.top + inset.bottom);
   }

   @Override
   protected boolean isSeededSize(Dimension size) {
      if(size == null) {
         return false;
      }

      for(VizContext ctx : VizContext.allModes()) {
         if(size.equals(seededSize(ctx))) {
            return true;
         }
      }

      return size.width == 3 * AssetUtil.defw && size.height == 12 * AssetUtil.defh;
   }
```

If `VizContext.allModes()` does not exist, add it to `VSDensityDefaults` instead as
`static Insets[] allTierPaddings()` and compare against `3 * defw + h` for each — the shape that
matters is that the container recognises its own four sizes, not a list's.

- [ ] **Step 4: Run them and watch them pass**

```bash
./mvnw test -pl core -Dtest=SelectionDensitySizeTest
```

Expected: PASS, 6 tests.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/inetsoft/uql/viewsheet/internal
git commit -m "Size a selection to its density, keeping five rows at every tier"
```

---

### Task 6: Ship the inset to the browser

**Files:**
- Modify: `core/src/main/java/inetsoft/web/viewsheet/model/VSSelectionBaseModel.java:34-55`
- Modify: `web/projects/portal/src/app/vsobjects/model/vs-selection-base-model.ts`
- Test: `core/src/test/java/inetsoft/web/viewsheet/model/SelectionModelPaddingTest.java`

**Interfaces:**
- Consumes: `VSObjectModel.padding?: TablePadding` from Task 1.
- Produces: `VSSelectionBaseModel.padding`, read by Task 7.

- [ ] **Step 1: Write the failing test**

```java
@Test
void theModelCarriesTheSeededInset() {
   SreeEnv.setProperty("viewsheet.density", "comfortable");
   SelectionListVSAssembly assembly = markedSelectionList();

   VSSelectionListModel model = new VSSelectionListModel(assembly, null);

   assertEquals(new Insets(16, 16, 16, 16), model.getPadding());
}

@Test
void anUnmarkedSelectionCarriesNoInset() {
   SelectionListVSAssembly assembly = new SelectionListVSAssembly(new Viewsheet(), "Sel1");

   VSSelectionListModel model = new VSSelectionListModel(assembly, null);

   assertEquals(new Insets(0, 0, 0, 0), model.getPadding());
}
```

- [ ] **Step 2: Run it and watch it fail**

```bash
./mvnw test -pl core -Dtest=SelectionModelPaddingTest
```

Expected: FAIL, `cannot find symbol: method getPadding()`.

- [ ] **Step 3: Add the field**

In `VSSelectionBaseModel`'s constructor, beside `cellHeight`:

```java
      padding = assemblyInfo.getPadding();
```

with the getter and field:

```java
   public Insets getPadding() {
      return padding;
   }
```

```java
   private Insets padding;
```

In `vs-selection-base-model.ts`, nothing is added — `padding?: TablePadding` is inherited from `VSObjectModel` (Task 1).

- [ ] **Step 4: Run it and watch it pass**

```bash
./mvnw test -pl core -Dtest=SelectionModelPaddingTest
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/inetsoft/web/viewsheet/model/VSSelectionBaseModel.java core/src/test/java/inetsoft/web/viewsheet/model/SelectionModelPaddingTest.java
git commit -m "Send the selection card inset to the browser"
```

---

### Task 7: Browser geometry

**Files:**
- Modify: `web/projects/portal/src/app/vsobjects/objects/selection/vs-selection.component.ts:946-957` (`getBodyHeight`), `:959` (`getBodyWidth`)
- Test: `web/projects/portal/src/app/vsobjects/objects/selection/vs-selection.component.spec.ts`

**Interfaces:**
- Consumes: `model.padding` from Task 6.

- [ ] **Step 1: Write the failing tests**

```typescript
describe("card inset", () => {
   it("takes the inset out of the body height", () => {
      const component = createComponent({
         objectFormat: fmt(132, 202), titleFormat: fmt(132, 30),
         padding: { top: 16, left: 16, bottom: 16, right: 16 }
      });

      expect(component.getBodyHeight()).toBe(140);
   });

   it("takes the inset out of the body width", () => {
      const component = createComponent({
         objectFormat: fmt(132, 202), titleFormat: fmt(132, 30),
         padding: { top: 16, left: 16, bottom: 16, right: 16 }
      });

      expect(component.getBodyWidth()).toBe(100);
   });

   // Review Focus 1
   it("aNestedListDoesNotDoubleInset", () => {
      const component = createComponent({
         objectFormat: fmt(132, 202), titleFormat: fmt(132, 30),
         padding: { top: 16, left: 16, bottom: 16, right: 16 }
      });
      component.inContainer = true;

      expect(component.getBodyHeight()).toBe(172);
   });

   // Review Focus 5
   it("aDropdownPanelIsUnchanged", () => {
      const component = createComponent({
         objectFormat: fmt(132, 202), titleFormat: fmt(132, 30), dropdown: true,
         listHeight: 5, cellHeight: 28,
         padding: { top: 16, left: 16, bottom: 16, right: 16 }
      });

      expect(component.getBodyHeight()).toBe(140);
   });

   it("an assembly with no inset is unchanged", () => {
      const component = createComponent({
         objectFormat: fmt(100, 120), titleFormat: fmt(100, 20)
      });

      expect(component.getBodyHeight()).toBe(100);
   });
});
```

- [ ] **Step 2: Run them and watch them fail**

```bash
cd web && npx ng test portal --include="**/vs-selection.component.spec.ts"
```

Expected: FAIL — the first two return the un-inset values (172 and 132).

- [ ] **Step 3: Subtract the inset in both methods**

In `getBodyHeight()`, add beside the existing offsets, and apply it only on the branch that reads `objectFormat.height`:

```typescript
      const inset = this._model.padding;
      const insetY = this.inContainer ? 0 : (inset?.top || 0) + (inset?.bottom || 0);
```

then subtract `insetY` on the final branch only, leaving the `inContainer` and `dropdown` branches as they are. `inContainer` is zero because the container already took its own inset out of the space it handed down; the dropdown panel is transient chrome rather than the card.

In `getBodyWidth()`:

```typescript
      const inset = this._model.padding;
      const insetX = this.inContainer ? 0 : (inset?.left || 0) + (inset?.right || 0);
```

subtracted from the returned width.

- [ ] **Step 4: Run them and watch them pass**

```bash
cd web && npx ng test portal --include="**/vs-selection.component.spec.ts"
```

Expected: PASS, 5 new tests, no existing test broken.

- [ ] **Step 5: Commit**

```bash
git add web/projects/portal/src/app/vsobjects/objects/selection
git commit -m "Take the card inset out of a selection's body geometry"
```

---

### Task 8: The property dialogs

**Files:**
- Modify: `core/src/main/java/inetsoft/web/composer/model/vs/SelectionGeneralPaneModel.java`
- Modify: `core/src/main/java/inetsoft/web/composer/model/vs/SelectionContainerGeneralPaneModel.java`
- Modify: `core/src/main/java/inetsoft/web/composer/vs/dialog/SelectionListPropertyDialogService.java`
- Modify: `core/src/main/java/inetsoft/web/composer/vs/dialog/SelectionTreePropertyDialogService.java`
- Modify: `core/src/main/java/inetsoft/web/composer/vs/dialog/SelectionContainerPropertyDialogService.java`
- Test: `core/src/test/java/inetsoft/web/composer/vs/dialog/SelectionPaddingDialogTest.java`

**Interfaces:**
- Consumes: `isUserCellPadding()` / `resetUserCellPadding()` from Task 2.

**Placement note:** both panes go on the **per-type** general pane models, as tables do with `TableViewGeneralPaneModel`. They must NOT go on `SizePositionPaneModel`, which 21 general pane models share — padding controls there would surface on calendars, images, shapes and tabs.

- [ ] **Step 1: Write the failing tests**

```java
@Test
void theLoadSideShowsTheSeededValuesAndTheCheckbox() {
   SelectionListVSAssemblyInfo info = seededList("comfortable");

   SelectionListPropertyDialogModel model = load(info);
   PaddingPaneModel pane = model.getSelectionGeneralPaneModel().getPaddingPaneModel();

   assertEquals(16, pane.getTop());
   assertEquals(Boolean.TRUE, pane.getFollowsDefault(), "seeded means it is following the default");
}

@Test
void anUnmarkedSelectionHidesTheCheckbox() {
   SelectionListVSAssemblyInfo info = new SelectionListVSAssemblyInfo();

   SelectionListPropertyDialogModel model = load(info);

   assertNull(model.getSelectionGeneralPaneModel().getPaddingPaneModel().getFollowsDefault(),
              "null hides it: an unmarked selection has no default to follow");
}

@Test
void checkingTheBoxClearsTheOpinionRatherThanPinningTheTier() {
   SelectionListVSAssemblyInfo info = seededList("comfortable");
   info.setCellPadding(new Insets(9, 9, 9, 9), CompositeValue.Type.USER);

   writeCellPadding(info, new Insets(9, 9, 9, 9), Boolean.TRUE);

   assertFalse(info.isUserCellPadding());
   assertEquals(new Insets(6, 8, 6, 8), info.getCellPadding());
}

@Test
void clearingTheBoxStoresTheEditedValue() {
   SelectionListVSAssemblyInfo info = seededList("comfortable");

   writeCellPadding(info, new Insets(9, 9, 9, 9), Boolean.FALSE);

   assertTrue(info.isUserCellPadding());
   assertEquals(new Insets(9, 9, 9, 9), info.getCellPadding());
}
```

- [ ] **Step 2: Run them and watch them fail**

```bash
./mvnw test -pl core -Dtest=SelectionPaddingDialogTest
```

Expected: FAIL, `cannot find symbol: method getPaddingPaneModel()`.

- [ ] **Step 3: Add the two panes to both general pane models**

In `SelectionGeneralPaneModel` and `SelectionContainerGeneralPaneModel`:

```java
   public PaddingPaneModel getPaddingPaneModel() {
      return paddingPaneModel;
   }

   public void setPaddingPaneModel(PaddingPaneModel paddingPaneModel) {
      this.paddingPaneModel = paddingPaneModel;
   }

   public PaddingPaneModel getCellPaddingPaneModel() {
      return cellPaddingPaneModel;
   }

   public void setCellPaddingPaneModel(PaddingPaneModel cellPaddingPaneModel) {
      this.cellPaddingPaneModel = cellPaddingPaneModel;
   }

   private PaddingPaneModel paddingPaneModel = new PaddingPaneModel();
   private PaddingPaneModel cellPaddingPaneModel = new PaddingPaneModel();
```

- [ ] **Step 4: Add the read side to all three services**

Mirroring `TableViewPropertyDialogService:121-141`:

```java
      PaddingPaneModel paddingPaneModel = selectionGeneralPane.getPaddingPaneModel();
      Insets padding = info.getPadding();
      paddingPaneModel.setTop(padding.top);
      paddingPaneModel.setLeft(padding.left);
      paddingPaneModel.setBottom(padding.bottom);
      paddingPaneModel.setRight(padding.right);
      // null hides the checkbox: an unmarked selection has no default to follow
      paddingPaneModel.setFollowsDefault(
         info.getVizMark() == null ? null : !info.isUserPadding());

      PaddingPaneModel cellPaddingPaneModel = selectionGeneralPane.getCellPaddingPaneModel();
      Insets cellPadding = info.getCellPadding();
      cellPaddingPaneModel.setTop(cellPadding == null ? 0 : cellPadding.top);
      cellPaddingPaneModel.setLeft(cellPadding == null ? 0 : cellPadding.left);
      cellPaddingPaneModel.setBottom(cellPadding == null ? 0 : cellPadding.bottom);
      cellPaddingPaneModel.setRight(cellPadding == null ? 0 : cellPadding.right);
      cellPaddingPaneModel.setFollowsDefault(
         info.getVizMark() == null ? null : !info.isUserCellPadding());
```

The container reads `getPadding()` only — it has no cell padding, so it gets one pane, not two. Remove `getCellPaddingPaneModel` from `SelectionContainerGeneralPaneModel` if it was added in Step 3.

- [ ] **Step 5: Add the write side to all three services**

Mirroring `TableViewPropertyDialogService:276-294`:

```java
      Insets editedCellPadding = new Insets(cellPaddingPaneModel.getTop(),
                                            cellPaddingPaneModel.getLeft(),
                                            cellPaddingPaneModel.getBottom(),
                                            cellPaddingPaneModel.getRight());
      Boolean cellPaddingFollowsDefault = cellPaddingPaneModel.getFollowsDefault();

      if(cellPaddingFollowsDefault == null) {
         // no checkbox was shown, so this selection is not marked; store only a real edit. the
         // load side shows 0 for an absent padding, so all zeros means none rather than a pinned 0
         if(editedCellPadding.equals(new Insets(0, 0, 0, 0))) {
            info.resetUserCellPadding();
         }
         else if(!editedCellPadding.equals(info.getCellPadding())) {
            info.setCellPadding(editedCellPadding, CompositeValue.Type.USER);
         }
      }
      else if(cellPaddingFollowsDefault) {
         // clear the opinion and let the density decide, the same shape Revert uses. Writing the
         // current density value into the USER tier here would pin this tier
         info.resetUserCellPadding();
      }
      else {
         info.setCellPadding(editedCellPadding, CompositeValue.Type.USER);
      }
```

and for the card inset, the same shape against `setUserPadding(boolean)` / `resetPadding(VizContext)`.

- [ ] **Step 6: Run them and watch them pass**

```bash
./mvnw test -pl core -Dtest=SelectionPaddingDialogTest
```

Expected: PASS, 4 tests.

- [ ] **Step 7: Add the Angular panes**

The `PaddingPane` component already exists and is used by the table dialog. Add two instances to `selection-general-pane.component.html` and one to `selection-container-general-pane.component.html`:

```html
<padding-pane [model]="model.paddingPaneModel"
              [label]="'_#(Padding)'"></padding-pane>
<padding-pane [model]="model.cellPaddingPaneModel"
              [label]="'_#(Cell Padding)'"></padding-pane>
```

The container gets the first only. Read the real selector and input names off the table general pane's template before writing this — it is the working example, and its label strings are already in the catalog. No comments in `.html` files.

- [ ] **Step 8: Run the frontend spec**

```bash
cd web && npx ng test portal --include="**/selection-general-pane.component.spec.ts"
```

Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add core/src/main/java/inetsoft/web/composer core/src/test/java/inetsoft/web/composer web/projects/portal/src/app/composer
git commit -m "Add the padding panes to the three selection property dialogs"
```

---

### Task 9: Export

**Files:**
- Modify: `core/src/main/java/inetsoft/report/io/viewsheet/VSSelectionListHelper.java`
- Modify: `core/src/main/java/inetsoft/report/io/viewsheet/VSCurrentSelectionHelper.java`
- Test: `core/src/test/java/inetsoft/report/io/viewsheet/SelectionExportInsetTest.java`

**Interfaces:**
- Consumes: `AbstractVSExporter.getTableCardInset(TableDataVSAssemblyInfo)` as the pattern; a selection-shaped sibling is added for `SelectionBaseVSAssemblyInfo`.

- [ ] **Step 1: Write the failing tests**

```java
@Test
void theGridOriginMovesInByTheInset() {
   SelectionListVSAssemblyInfo info = seededList("comfortable");

   Rectangle bounds = contentBoundsFor(info, new Rectangle(0, 0, 132, 202));

   assertEquals(new Rectangle(16, 16, 100, 170), bounds);
}

/** Review Focus 4. */
@Test
void excelTreeTakesNoInset() {
   SelectionTreeVSAssemblyInfo info = seededTree("comfortable");

   Rectangle bounds = contentBoundsFor(info, new Rectangle(0, 0, 132, 202), excelExporter());

   assertEquals(new Rectangle(0, 0, 132, 202), bounds,
                "insetsTableCard() is false for Excel, and the tree inherits through the base");
}

@Test
void anUnmarkedSelectionIsUnchanged() {
   SelectionListVSAssemblyInfo info = new SelectionListVSAssemblyInfo();

   Rectangle bounds = contentBoundsFor(info, new Rectangle(0, 0, 100, 120));

   assertEquals(new Rectangle(0, 0, 100, 120), bounds);
}
```

- [ ] **Step 2: Run them and watch them fail**

```bash
./mvnw test -pl core -Dtest=SelectionExportInsetTest
```

Expected: FAIL — `theGridOriginMovesInByTheInset` gets the un-inset rect.

- [ ] **Step 3: Resolve and apply the inset in the two bases**

Add to `AbstractVSExporter`, beside `getTableCardInset`:

```java
   /**
    * The card inset a selection draws its rows inside, or zero where this format does not inset a
    * card. Gated on the same predicate the table card uses: Excel and CSV are cell grids, where a
    * pixel inset means nothing.
    */
   public Insets getSelectionCardInset(SelectionBaseVSAssemblyInfo info) {
      Insets padding = info == null || !insetsTableCard() ? null : info.getPadding();
      return padding == null ? new Insets(0, 0, 0, 0) : (Insets) padding.clone();
   }
```

In `VSSelectionListHelper` and `VSCurrentSelectionHelper`, resolve the inset once and shrink the content rect by it. The border, background and round corner keep the full assembly bounds — only the rows move in:

```java
   /**
    * The rect the rows draw into: the assembly bounds less the card inset. The card itself - its
    * border, background and round corner - keeps the full bounds, which is the same split the
    * chart and table card use.
    */
   protected Rectangle getContentBounds(SelectionBaseVSAssemblyInfo info, Rectangle bounds) {
      Insets inset = exporter.getSelectionCardInset(info);

      return new Rectangle(bounds.x + inset.left,
                           bounds.y + inset.top,
                           Math.max(0, bounds.width - inset.left - inset.right),
                           Math.max(0, bounds.height - inset.top - inset.bottom));
   }
```

Clamp at zero on both axes: an author inset larger than the assembly would otherwise produce a negative width, which several downstream painters divide by.

Then feed `getContentBounds(info, bounds)` to the row-drawing loop in each base, leaving the call that paints the card background and border reading the original `bounds`.

- [ ] **Step 4: Run them and watch them pass**

```bash
./mvnw test -pl core -Dtest=SelectionExportInsetTest
```

Expected: PASS, 3 tests.

- [ ] **Step 5: Run the export suites for regressions**

```bash
./mvnw test -pl core -Dtest='*Selection*,*Export*,*Helper*'
```

Expected: all pass.

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/inetsoft/report/io/viewsheet core/src/test/java/inetsoft/report/io/viewsheet
git commit -m "Inset the exported selection card"
```

---

### Task 10: Print layout

**Files:**
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/VsToReportConverter.java:682-703`
- Test: `core/src/test/java/inetsoft/uql/viewsheet/internal/SelectionPrintInsetTest.java`

- [ ] **Step 1: Write the failing test**

```java
@Test
void thePrintedSelectionTakesItsInset() {
   SelectionListVSAssemblyInfo info = seededList("comfortable");

   TextBoxElement box = convertedBoxFor(info);

   assertEquals(new Insets(16, 16, 16, 16), box.getPadding());
}

@Test
void aLegacySelectionPrintsUnchanged() {
   SelectionListVSAssemblyInfo info = new SelectionListVSAssemblyInfo();

   TextBoxElement box = convertedBoxFor(info);

   assertEquals(new Insets(0, 0, 0, 0), box.getPadding());
}
```

- [ ] **Step 2: Run it and watch it fail**

```bash
./mvnw test -pl core -Dtest=SelectionPrintInsetTest
```

Expected: FAIL on the first.

- [ ] **Step 3: Pass the inset into the converted box**

In `addSelectionList`, `addSelectionTree` and `addCurrentSelection`, read the inset off the info and set it on the converted box before it is added:

```java
         Insets inset = info.getPadding();

         if(inset != null) {
            box.setPadding((Insets) inset.clone());
         }
```

Clone rather than share: the element outlives the conversion, and a shared `Insets` is mutable, so a later edit to the assembly's padding would silently rewrite an already-converted page.

Read the exact setter name off `TextBoxElement` before writing this — if it exposes padding through its `TextBoxElementInfo` rather than directly, route through that instead, and keep the clone.

- [ ] **Step 4: Run it and watch it pass**

```bash
./mvnw test -pl core -Dtest=SelectionPrintInsetTest
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/inetsoft/uql/viewsheet/internal/VsToReportConverter.java core/src/test/java/inetsoft/uql/viewsheet/internal/SelectionPrintInsetTest.java
git commit -m "Inset a selection in print layout"
```

---

### Task 11: The fixture and the manual checks

**Files:**
- Create: `docs/superpowers/plans/2026-10-01-selection-padding-manual-checks.md`
- Create (local, gitignored): `.superpowers/baselines/selection-padding/`

Scope is spec §8.1 — four roles, three formats, Match Layout only. Deliberately lighter than the table fixture; the reasoning for each omission is in the spec, not repeated here.

- [ ] **Step 1: Build the fixture viewsheets**

Four marked roles in one viewsheet — `SelList`, `SelTree`, `SelContainer` (holding a list and a tree), `SelNoTitle` (title hidden) — plus an unmarked copy of the same four as the legacy control. Use `vsclient.js` / `flows.js` from `.superpowers/baselines/density-padding-export/` to drive Save As, Modernize/Revert and the per-dashboard density.

- [ ] **Step 2: Capture before and after**

Three formats — HTML, PDF, Excel — at Match Layout, at each of the three tiers plus the unmarked control. Use `sree.py` for the density flip and the export pull.

- [ ] **Step 3: Measure**

`html_measure.js` for the HTML rects, `inset_audit.py` for the PDF bands. Record the numbers in the manual-checks doc with the date and the commit they were captured at.

Expected, comfortable: body inset 16 on every edge; five rows visible on `SelList`; `SelContainer`'s nested list inset once, not twice; `SelNoTitle` inset with no lane; **Excel identical before and after, all tiers**.

- [ ] **Step 4: Write the manual-checks doc**

One check per role plus the Excel opt-out, each with its expected number and its failure signature, following the shape of `2026-09-23-density-padding-slice-a-manual-checks.md`. Record that the fixture is local state and cannot be cited as a committed baseline.

- [ ] **Step 5: Commit the doc only**

```bash
git add docs/superpowers/plans/2026-10-01-selection-padding-manual-checks.md
git commit -m "Record the selection padding manual checks"
```
