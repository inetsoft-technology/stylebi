# Group-Subtotal Emphasis in Crosstab/Calc Tables — Phase 9C Item 3 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Under the org-scoped modern visualization gate, give interior crosstab **group subtotals** a
distinct warm-neutral background (`#EEEAE1`) so multi-level crosstabs regain their total hierarchy —
completing the emphasis pass that already ships grand-total (`#E9E4DA`) coloring — defaults-only,
export-consistent, and byte-identical when the gate is off.

**Architecture:** Reuses the Phase 5 C1b mechanism exactly. `DataVSAQuery.getViewTableLens` already
overlays the modern palette onto a per-assembly clone of the shipped "Default Style" via
`applyModernTableStructure`, gated by `VSTableStructureDefaults.isModern()`. This item adds a
**data-borne** emphasis: for group levels 0–9 on both axes it inserts `XTableStyle.Specification`
objects of type `ROW_GROUP_TOTAL` / `COL_GROUP_TOTAL` carrying only a `background` attribute, **ahead of
the shipped zebra spec** in the style's spec list. `XTableStyle.Style.getBackground` resolves the
trailer (grand-total) band before per-cell specs, so grand totals stay distinct; and `findSpec` returns
the first matching spec, so the prepended group-total specs win over alternating-row color on total
cells. The specs self-guard (`matchRowGroup`/`matchColGroup` return `false` when the lens is not a
crosstab or the level exceeds the header count), so plain tables and non-crosstab assemblies are
untouched.

**Tech Stack:** Java 21 (core report-style engine + the viewsheet table render seam), JUnit 5
(`@Tag("core")`). No Angular/CSS/frontend change — viewsheet table cell fills are server-rendered
(System B), baked into both the live model and every export path.

## Global Constraints

Copied verbatim from the Phase 5 plan and the Phase 9C spec; every task's requirements implicitly
include this section.

- **Render-location rule.** Viewsheet table/crosstab cell fills are server-rendered and appear in
  export; browser `--inet-viz-*` CSS cannot color them. The subtotal emphasis **must** be driven
  server-side, at the same `DataVSAQuery` style-injection point that already carries the gridline /
  header / grand-total defaults, so the live model and every export format agree.
- **Gate is server-side and org-scoped.** Modern table structure is selected by
  `VSTableStructureDefaults.isModern()` = `VSDensityDefaults.isModern()` (which reads
  `SreeEnv.getBooleanProperty("viewsheet.modernVisualization", false, true)`, 3rd arg = org-scoped)
  **and** `!"false".equals(SreeEnv.getProperty("viewsheet.modernTableStructure", false, true))`. No new
  property — subtotal emphasis rides the existing structure gate.
- **Default-style only.** The overlay applies only when
  `TableDataVSAssemblyInfo.DEFAULT_STYLE.equals(sname)` (`"Default Style"`); a table assigned any other
  style is never modernized. This is already enforced at `DataVSAQuery.java:133-135` — the new specs are
  added inside `applyModernTableStructure`, which runs only in that branch.
- **Defaults-only.** The modern colors are overlaid onto the cloned Default Style, and
  `VSFormatTableLens` wraps **above** the style (`DataVSAQuery.java:209`), so any user cell/column/row
  format merges on top and wins. Subtotal specs are true defaults the user format still overrides.
- **Grand total stays distinct.** `XTableStyle.Style.getBackground` checks `isTrailerRowFormat` /
  `isTrailerColFormat` (the grand-total band, already set to `#E9E4DA`) **before** the per-cell spec
  lookup (`XTableStyle.java:997-1006`). A cell that is both a grand total and a group total therefore
  keeps the grand-total color; only interior subtotals receive `#EEEAE1`.
- **Crosstab-only.** `matchRowGroup`/`matchColGroup` return `false` when `crosstab == null`
  (`XTableStyle.java:1236,1258`), so plain (non-crosstab) tables are unaffected even though the specs are
  added unconditionally — mirroring the existing "header-col + trailer keys no-op on plain tables"
  comment at `DataVSAQuery.java:234-236`.
- **Gate-off byte-identical.** With the gate off, `applyModernTableStructure` is not called, so the
  cloned Default Style is left exactly as shipped; every render path — live, PDF, PNG, SVG, Excel, PPT,
  HTML — is pixel-identical to today, and no saved sheet XML changes.
- **Background only.** The group-total specs set only the `background` attribute — no foreground, font,
  or border. `findSpec` is per-attribute, so a spec with only `a_background` set is invisible to
  foreground/border lookups.
- **Color value.** `--viz-subtotal-bg = #EEEAE1` (lighter than the grand-total `#E9E4DA`), the value
  already recorded in `visualization-palette-swatches.html`. Light mode only; dark variants ride the
  Phase 9B dark pass.

---

## Grounding (verified against current code, branch `viz-updates`, 2026-07-23)

### The existing modern-structure seam (Phase 5 C1b) — where this item plugs in

`core/src/main/java/inetsoft/report/composition/execution/DataVSAQuery.java`:

| Anchor | Location | Note |
|---|---|---|
| Style clone + gate branch | `DataVSAQuery.java:127-138` | The shipped Default Style is cloned per-assembly (`style.clone()`); when `style instanceof XTableStyle && DEFAULT_STYLE.equals(sname) && VSTableStructureDefaults.isModern()`, `applyModernTableStructure((XTableStyle) style)` runs. |
| Positional palette overlay | `DataVSAQuery.java:238-256` | `applyModernTableStructure` calls `style.put("body.rcolor", …)`, `"header-row.background"`, `"trailer-row.background"`, etc. The new subtotal specs are added here (grand total via `trailer-row/col.background = #E9E4DA` already present at `:254-255`). |
| Style attached after overlay | `DataVSAQuery.java:140` | `style.setTable(data)` runs **after** `applyModernTableStructure`, so at spec-creation time the style's `crosstab`/`table` are not yet bound to `data`. This is fine: specs are evaluated lazily at render time via `findSpec`→`match`, by which point `setTable` has bound the crosstab. Specs are created with `style.new Specification()` so they resolve against the same style instance. |
| User format wraps above style | `DataVSAQuery.java:209` | `new VSFormatTableLens(box, vname, data, !chart)` wraps the styled lens, so user formats win (defaults-only guarantee). |

### The group-total spec machinery (already exists — this item only *uses* it)

`core/src/main/java/inetsoft/report/style/XTableStyle.java`:

| Anchor | Location | Note |
|---|---|---|
| Spec type constants | `XTableStyle.java:1211-1213` | `Specification.REGULAR = 0`, `ROW_GROUP_TOTAL = 1`, `COL_GROUP_TOTAL = 2` (public static final, inside the public inner class `Specification`). |
| Row-group match | `XTableStyle.java:1235-1255` | `matchRowGroup(r,c)`: returns `false` if `crosstab == null`; else matches when base `col >= index`, `index < crosstab.getRowHeaderCount()`, and `crosstab.isTotalCell(baseRow, index)`. `index` is the 0-based row-header (dimension) column. |
| Col-group match | `XTableStyle.java:1257-1274` | `matchColGroup(r,c)`: symmetric, guarded by `crosstab.getColHeaderCount()` and `crosstab.isTotalCell(index, baseCol)`. |
| First-match resolution | `XTableStyle.java:586-596` | `findSpec(r,c,a)` iterates `speclist` in order and returns the **first** spec with `spec.get(a) != null && getMatcher().match(r,c)`. → prepended group-total specs win over the later zebra spec. |
| Background resolution order | `XTableStyle.java:993-1012` (`getBackground`) and `:1022-1041` (`getBackground(r,c,spanRow)`) | Order: header-row → **trailer-row** → header-col → **trailer-col** → `get(r,c,a_background)` (per-cell spec / findSpec) → body. Trailer (grand total) is resolved **before** the per-cell subtotal spec → grand total stays `#E9E4DA`. |
| Spec creation idiom | `XTableStyle.java:1207-1216`, `:1419-1435` | `style.new Specification()` (public ctor); `spec.setType(int)`, `spec.setIndex(int)`, `spec.put("background", Color)`. Precedent: `TableStyleController.java:274`, `TableStyleFormatModel.java:61-63`, `CSSTableStyle.java:241-315`. |
| Spec list append only | `XTableStyle.java:339-341` | `addSpecification(Specification)` does `speclist.add(spec)` (append). **There is no insert-at-index overload today** — Task 2 adds one, because the shipped Default Style already contains the zebra spec and the group-total specs must precede it. |

### `isTotalCell` — confirms the index is a base column/row, and the 0–9 loop is robust

`CrossTabFilter.isTotalCell(int r, int c)` (`CrossTabFilter.java:721-723`) returns
`totalPos.get(r,c) == Boolean.TRUE` — the second argument is a **column**. `matchRowGroup` passes `index`
as that column, so `index` is a 0-based dimension-column level. Looping `index = 0..9` and giving every
level the **same** `#EEEAE1` background is robust to any 0-vs-1-based convention question: a spec at
level `k` only matches cells that are genuinely total cells at dimension column `k` (via `isTotalCell`),
out-of-range levels are blocked by the `index >= getRowHeaderCount()/getColHeaderCount()` guard, and
because every level uses one color, overlapping matches produce the same result. `getRowHeaderCount()` /
`getColHeaderCount()` (`CrossTabFilter.java:364-375`) cap real levels well under 10.

### The precedent that validates "group-total before zebra"

`CSSTableStyle` builds the same spec kinds and deliberately calls `applyGroupings()` (group-total specs)
**before** `applyRowPatterns()`/`applyColPatterns()` (zebra) — `CSSTableStyle.java:49-57` — so the
group-total specs land earlier in `speclist` and win under `findSpec`. This item reproduces that ordering
by inserting the group-total specs at the **front** of the already-populated Default-Style spec list.

### Why the earlier Phase 5 attempt was backed out (and why this one is different)

Phase 5 item 3b tried a per-lens transient flag on `VSTableLens` set by `DataVSAQuery`; the rendering lens
was a different instance, so the flag was always null at render (documented in the Phase 5 deferred list).
This plan carries the emphasis **in the cloned style itself** (data-borne, the same channel that makes the
gridline/header/grand-total colors survive to render and export), so there is no cross-instance flag to
lose.

---

## Decisions

- **R1 — Data-borne group-total specs, not a render-time flag.** The backed-out Phase 5 attempt failed
  because a transient lens flag did not survive to the rendering lens instance. The Default Style clone
  *does* survive (it is what carries the shipped gridline/header colors to export), so the emphasis is
  attached to the style as `Specification` objects. This is the "known-good path" recorded in the Phase 5
  deferred list and the Phase 9C spec.
- **R2 — Prepend ahead of the zebra spec.** `findSpec` returns the first match, and the Default Style
  already contains an odd-row zebra `REGULAR` spec. Group-total specs are inserted at the front so total
  cells get `#EEEAE1` instead of the `#F5F5F5` zebra. Requires a new `addSpecification(int, Specification)`
  insert overload on `XTableStyle` (Task 2) — the append-only `addSpecification` cannot prepend.
- **R3 — Loop levels 0–9 for both axes, uniform color, no crosstab gate at insert time.** Specs
  self-guard (`crosstab == null` → no match; out-of-range level → no match), so they are added
  unconditionally, exactly like the existing header-col/trailer `put(...)` calls that "no-op on plain
  tables". Uniform `#EEEAE1` across levels sidesteps the 0-vs-1-based index question.
- **R4 — Background only; no new property; no swatch change.** Only the `background` attribute is set
  (per-attribute `findSpec` leaves foreground/borders untouched). The emphasis rides the existing
  `viewsheet.modernTableStructure` gate. `--viz-subtotal-bg #EEEAE1` is already in the swatches, so the
  docs task only annotates status.

---

## Task 1: Add the `subtotalBackground()` accessor to `VSTableStructureDefaults`

**Files:**
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/VSTableStructureDefaults.java`
- Test: `core/src/test/java/inetsoft/uql/viewsheet/internal/VSTableStructureDefaultsTest.java`

**Interfaces:**
- Consumes: nothing new (pure color provider).
- Produces (used by Task 3): `public static java.awt.Color subtotalBackground()` — `#EEEAE1`.

- [ ] **Step 1: Add the failing test**

Append this method to the existing `VSTableStructureDefaultsTest` (it already has the sibling
`totalBackgroundValue` at `:51-54` and the private `rgb(Color)` helper at `:56-58`):

```java
   @Test
   void subtotalBackgroundValue() {
      // interior group subtotals; lighter than the grand-total #E9E4DA so the total hierarchy reads
      assertEquals(0xEEEAE1, rgb(VSTableStructureDefaults.subtotalBackground()));
   }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -q -pl core test -Dtest=VSTableStructureDefaultsTest -DfailIfNoTests=false`
Expected: FAIL — `subtotalBackground()` does not exist (compilation error).

- [ ] **Step 3: Add the accessor**

In `VSTableStructureDefaults.java`, add the accessor next to `totalBackground()` (`:70-73`) and the
constant next to `TOTAL_BG` (`:80`):

```java
   /** Interior group-subtotal background — lighter than the grand-total band for hierarchy. */
   public static Color subtotalBackground() {
      return SUBTOTAL_BG;
   }
```

and in the constant block (after `private static final Color TOTAL_BG = new Color(0xE9E4DA);`):

```java
   private static final Color SUBTOTAL_BG = new Color(0xEEEAE1);
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw -q -pl core test -Dtest=VSTableStructureDefaultsTest -DfailIfNoTests=false`
Expected: PASS (6 tests — the 5 existing plus `subtotalBackgroundValue`).

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/inetsoft/uql/viewsheet/internal/VSTableStructureDefaults.java \
        core/src/test/java/inetsoft/uql/viewsheet/internal/VSTableStructureDefaultsTest.java
git commit -m "Phase 9C item 3: add modern group-subtotal background (#EEEAE1)"
```

---

## Task 2: Add an insert-at-index overload to `XTableStyle.addSpecification`

**Files:**
- Modify: `core/src/main/java/inetsoft/report/style/XTableStyle.java:339-341`
- Test: `core/src/test/java/inetsoft/report/style/XTableStyleSpecInsertTest.java`

**Interfaces:**
- Consumes: nothing new.
- Produces (used by Task 3): `public void addSpecification(int index, Specification spec)` — inserts
  `spec` at `index` in the spec list (`speclist.add(index, spec)`), so a caller can place group-total
  specs ahead of the shipped zebra spec that `findSpec` would otherwise match first.

- [ ] **Step 1: Write the failing test**

The insert primitive is fully unit-testable without a crosstab: a `REGULAR` zebra spec appended first,
then a `ROW_GROUP_TOTAL` spec inserted at the front, and the resulting order asserted via the public
`getSpecification`/`getSpecificationCount` API.

```java
package inetsoft.report.style;

import inetsoft.report.lens.DefaultTableLens;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;

import java.awt.Color;

import static org.junit.jupiter.api.Assertions.*;

@Tag("core")
class XTableStyleSpecInsertTest {
   @Test
   void insertPlacesSpecAheadOfExisting() {
      XTableStyle style = new XTableStyle(new DefaultTableLens(new Object[][] {
         {"a", "b"}, {"1", "2"}
      }));

      // shipped-style stand-in: an appended zebra (REGULAR) spec
      XTableStyle.Specification zebra = style.new Specification();
      zebra.setType(XTableStyle.Specification.REGULAR);
      zebra.setIndex(1);
      zebra.setRepeat(true);
      zebra.put("background", new Color(0xF5F5F5));
      style.addSpecification(zebra);

      assertEquals(1, style.getSpecificationCount());

      // group-total spec inserted at the front must precede the zebra spec
      XTableStyle.Specification groupTotal = style.new Specification();
      groupTotal.setType(XTableStyle.Specification.ROW_GROUP_TOTAL);
      groupTotal.setIndex(0);
      groupTotal.put("background", new Color(0xEEEAE1));
      style.addSpecification(0, groupTotal);

      assertEquals(2, style.getSpecificationCount());
      assertSame(groupTotal, style.getSpecification(0), "group-total must be first (findSpec wins)");
      assertSame(zebra, style.getSpecification(1));
   }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -q -pl core test -Dtest=XTableStyleSpecInsertTest -DfailIfNoTests=false`
Expected: FAIL — `addSpecification(int, Specification)` does not exist (compilation error).

- [ ] **Step 3: Add the insert overload**

In `XTableStyle.java`, directly beneath the existing append overload (`:336-341`):

```java
   /**
    * Add a new specification at a specific position. Lower indexes are matched first by findSpec,
    * so inserting ahead of an existing spec makes it win for cells both specs match.
    */
   public void addSpecification(int index, Specification spec) {
      speclist.add(index, spec);
   }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw -q -pl core test -Dtest=XTableStyleSpecInsertTest -DfailIfNoTests=false`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/inetsoft/report/style/XTableStyle.java \
        core/src/test/java/inetsoft/report/style/XTableStyleSpecInsertTest.java
git commit -m "Phase 9C item 3: add insert-at-index addSpecification overload to XTableStyle"
```

---

## Task 3: Insert modern group-subtotal specs in `DataVSAQuery.applyModernTableStructure`

**Files:**
- Modify: `core/src/main/java/inetsoft/report/composition/execution/DataVSAQuery.java` (call a new
  helper at the end of `applyModernTableStructure` `:238-256`; add the helper after it)
- Test: `core/src/test/java/inetsoft/report/style/XTableStyleGroupSubtotalOrderTest.java`

**Interfaces:**
- Consumes: `VSTableStructureDefaults.subtotalBackground()` (Task 1);
  `XTableStyle.addSpecification(int, Specification)` (Task 2);
  `XTableStyle.Specification.ROW_GROUP_TOTAL` / `COL_GROUP_TOTAL` (existing).
- Produces: interior crosstab subtotals rendered with `#EEEAE1` on the live model and every export path
  when the gate is on.

- [ ] **Step 1: Write the failing ordering test**

A full crosstab render needs a live `ViewsheetSandbox` and is the eye-test in Task 4, not a unit test.
This test pins the mechanism the production edit relies on — that the group-total specs (levels 0–9, both
axes, each carrying the subtotal background) are placed **ahead** of a pre-existing zebra spec, so
`findSpec` resolves them first on total cells. It reproduces the exact construction the helper performs
(mirroring how the item-2 plan's seam tests pin the resolver contract at unit granularity).

```java
package inetsoft.report.style;

import inetsoft.report.lens.DefaultTableLens;
import inetsoft.uql.viewsheet.internal.VSTableStructureDefaults;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;

import java.awt.Color;

import static org.junit.jupiter.api.Assertions.*;

@Tag("core")
class XTableStyleGroupSubtotalOrderTest {
   @Test
   void groupTotalSpecsPrecedeZebraAndCarrySubtotalBackground() {
      XTableStyle style = new XTableStyle(new DefaultTableLens(new Object[][] {
         {"a", "b"}, {"1", "2"}
      }));

      // shipped Default-Style stand-in: one appended zebra spec
      XTableStyle.Specification zebra = style.new Specification();
      zebra.setType(XTableStyle.Specification.REGULAR);
      zebra.setIndex(1);
      zebra.setRepeat(true);
      zebra.put("background", new Color(0xF5F5F5));
      style.addSpecification(zebra);

      // reproduce the production insertion: levels 0-9, both axes, prepended in order
      Color subtotal = VSTableStructureDefaults.subtotalBackground();
      int pos = 0;

      for(int level = 0; level < 10; level++) {
         XTableStyle.Specification rowSpec = style.new Specification();
         rowSpec.setType(XTableStyle.Specification.ROW_GROUP_TOTAL);
         rowSpec.setIndex(level);
         rowSpec.put("background", subtotal);
         style.addSpecification(pos++, rowSpec);

         XTableStyle.Specification colSpec = style.new Specification();
         colSpec.setType(XTableStyle.Specification.COL_GROUP_TOTAL);
         colSpec.setIndex(level);
         colSpec.put("background", subtotal);
         style.addSpecification(pos++, colSpec);
      }

      // 20 group-total specs prepended, zebra pushed to the end
      assertEquals(21, style.getSpecificationCount());

      for(int i = 0; i < 20; i++) {
         XTableStyle.Specification spec = style.getSpecification(i);
         int type = spec.getType();
         assertTrue(type == XTableStyle.Specification.ROW_GROUP_TOTAL ||
                    type == XTableStyle.Specification.COL_GROUP_TOTAL,
                    "spec " + i + " must be a group-total spec ahead of the zebra");
         assertEquals(0xEEEAE1, ((Color) spec.get("background")).getRGB() & 0xFFFFFF);
      }

      // zebra survives, last, still #F5F5F5 (untouched)
      XTableStyle.Specification last = style.getSpecification(20);
      assertEquals(XTableStyle.Specification.REGULAR, last.getType());
      assertEquals(0xF5F5F5, ((Color) last.get("background")).getRGB() & 0xFFFFFF);
   }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -q -pl core test -Dtest=XTableStyleGroupSubtotalOrderTest -DfailIfNoTests=false`
Expected: FAIL — `VSTableStructureDefaults.subtotalBackground()` must exist (Task 1) and
`addSpecification(int, Specification)` must exist (Task 2). If Tasks 1–2 are done, this test PASSES as-is
(it exercises only shipped + Task-1/2 API); it is the guard that the construction the helper uses is
correct. Proceed to the production edit regardless — Step 3 is what makes the live/export render apply it.

- [ ] **Step 3: Add the production helper and call it**

In `DataVSAQuery.java`, at the end of `applyModernTableStructure` (after the last `style.put(...)` at
`:255`), add the call:

```java
      applyModernGroupSubtotals(style);
```

Then add the helper directly after `applyModernTableStructure` (after `:256`):

```java
   /**
    * Prepend data-borne group-subtotal emphasis specs so interior crosstab subtotals get a distinct
    * background. Group-total specs must precede the shipped zebra spec (findSpec returns the first
    * match) so they win over alternating-row color on total cells. Levels 0-9 cover both axes; each
    * spec self-guards (matchRowGroup/matchColGroup return false for a non-crosstab lens or a level past
    * the header count), so plain tables are unaffected. Grand totals stay distinct: XTableStyle resolves
    * the trailer band before per-cell specs, so trailer-row/col.background (grand total) still wins.
    */
   private void applyModernGroupSubtotals(XTableStyle style) {
      Color subtotal = VSTableStructureDefaults.subtotalBackground();
      int pos = 0;

      for(int level = 0; level < 10; level++) {
         XTableStyle.Specification rowSpec = style.new Specification();
         rowSpec.setType(XTableStyle.Specification.ROW_GROUP_TOTAL);
         rowSpec.setIndex(level);
         rowSpec.put("background", subtotal);
         style.addSpecification(pos++, rowSpec);

         XTableStyle.Specification colSpec = style.new Specification();
         colSpec.setType(XTableStyle.Specification.COL_GROUP_TOTAL);
         colSpec.setIndex(level);
         colSpec.put("background", subtotal);
         style.addSpecification(pos++, colSpec);
      }
   }
```

`XTableStyle` is already imported in `DataVSAQuery` (it is used at `:133,137,238`); `Color` is already
imported (used at `:239`); `VSTableStructureDefaults` is already imported (used at `:135,239-255`). No
import edits needed.

- [ ] **Step 4: Compile and run the item-3 suite**

Run: `./mvnw -q -pl core test-compile && ./mvnw -q -pl core test -Dtest="VSTableStructureDefaultsTest,XTableStyleSpecInsertTest,XTableStyleGroupSubtotalOrderTest" -DfailIfNoTests=false`
Expected: `core` compiles clean; all three suites PASS.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/inetsoft/report/composition/execution/DataVSAQuery.java \
        core/src/test/java/inetsoft/report/style/XTableStyleGroupSubtotalOrderTest.java
git commit -m "Phase 9C item 3: emphasize crosstab group subtotals under the modern gate"
```

---

## Task 4: Validation — gate-off parity + gate-on crosstab live/export + hierarchy + boundaries

**Files:** none (verification task). Requires a built/running server (`docker compose up` from
`docker/target/docker-test`), an org, and a **multi-level crosstab** viewsheet: at least **two row
dimensions** (so interior row subtotals exist) plus a column dimension and a numeric measure, using the
**Default Style** (no per-cell user format), with **subtotals enabled** (row/column group totals shown).
The gate is toggled via EM → Look and Feel (or `SreeEnv` `viewsheet.modernVisualization` /
`viewsheet.modernTableStructure`).

- [ ] **Step 1: Gate-off byte-identical**

With `viewsheet.modernVisualization=false`, open the crosstab and export it (PDF/PNG/SVG/Excel). Confirm
subtotal rows/columns render with the legacy zebra/body fill (no `#EEEAE1`), and the saved sheet XML is
unchanged (diff the asset). Expected: identical to pre-item-3.

- [ ] **Step 2: Gate-on subtotal emphasis, live + export**

Enable the gate for the org. Reopen the crosstab. Expected: interior **group subtotal** rows and columns
render with the warm-neutral `#EEEAE1` fill in the live viewer **and** in every export format
(PDF/PNG/SVG/Excel). Confirm live and export match (single injection point, shared by both).

- [ ] **Step 3: Hierarchy — grand total stays distinct**

On the same gate-on crosstab, confirm the **grand-total** row/column keeps the grand-total `#E9E4DA`
fill (the trailer band is resolved before the per-cell subtotal spec), so grand total (`#E9E4DA`),
subtotal (`#EEEAE1`), and body (zebra `#F5F5F5` / white) read as three distinct levels. Confirm the
subtotal **label** cells in the dimension columns keep the header-column tint (`#F1EFEA`) — this is
expected: `getBackground` resolves `header-col` before the per-cell spec, so the totaled *measure* cells
get subtotal emphasis while the dimension label stays header-toned.

- [ ] **Step 4: Defaults-only proof**

On a cell inside a subtotal row, set an explicit user background via the format dialog. Expected: the
user color wins under the gate (`VSFormatTableLens` wraps above the style). Clearing it returns the cell
to `#EEEAE1`.

- [ ] **Step 5: Boundary checks**

- A crosstab assigned a **non-Default** table style: no `#EEEAE1` appears in either mode (the overlay
  branch requires `"Default Style"`).
- A **plain table** (non-crosstab, Default Style) under the gate: unchanged — no subtotal fill (the
  specs self-guard on `crosstab == null`).
- `git diff --stat` confirms the change is core Java only: `VSTableStructureDefaults.java`,
  `XTableStyle.java`, `DataVSAQuery.java`, three test files, and docs — no CSS/frontend, no
  `defaults.css`, no new `SreeEnv` property.

- [ ] **Step 6: Scheduled-export org-scope check**

Trigger a scheduled export of the crosstab under the gate; confirm `VSTableStructureDefaults.isModern()`'s
org-scoped `SreeEnv` read resolves on the scheduler thread (as the sibling structure colors already do).

---

## Task 5: Update the swatches + roadmap / phase-9c status notes

**Files:**
- Modify: `docs/superpowers/specs/lookfeel/visualization-palette-swatches.html` (confirm/annotate only)
- Modify: `docs/superpowers/specs/lookfeel/visualization-phase9c-deferred-consolidation-implementation-plan.md`
  (mark scheduled item 3 status)
- Modify: `docs/superpowers/specs/lookfeel/visualization-phase5-implementation-plan.md` (flip item 3b from
  DEFERRED to done)
- Modify: `docs/superpowers/specs/lookfeel/visualization-implementation-roadmap.md` (Phase 9C item 3
  status line)

- [ ] **Step 1: Confirm the subtotal swatch exists**

The `--viz-subtotal-bg #EEEAE1` swatch was recorded during Phase 5. Grep to confirm:
`rg -n "EEEAE1|subtotal" docs/superpowers/specs/lookfeel/visualization-palette-swatches.html`. If present,
annotate it as "shipped (Phase 9C item 3) — interior crosstab group subtotals, data-borne
`ROW_GROUP_TOTAL`/`COL_GROUP_TOTAL` specs prepended ahead of the zebra, defaults-only, export-visible". If
absent, add a Table Structure Tokens swatch for it (light warm neutral, lighter than the grand-total
`#E9E4DA`).

- [ ] **Step 2: Mark item 3 status in the Phase 9C plan and roadmap; close Phase 5 item 3b**

- In `visualization-phase9c-deferred-consolidation-implementation-plan.md` scheduled item 3, add:
  "Status (implemented 2026-07-23): group subtotals emphasized (`#EEEAE1`) via data-borne
  `ROW_GROUP_TOTAL`/`COL_GROUP_TOTAL` specs prepended ahead of the zebra in the cloned Default Style;
  gated, defaults-only, export-consistent; grand total stays distinct via the trailer band."
- In `visualization-phase5-implementation-plan.md`, flip the item 3b entry (`⏸ Group-subtotal emphasis —
  DEFERRED`) to done, referencing the Phase 9C item 3 implementation.
- In `visualization-implementation-roadmap.md` (Phase 9C scheduled tier, item 3 line `:1008-1009`), append
  the implemented-status note mirroring the item-2 status-update precedent (`:781-787`).

- [ ] **Step 3: Commit**

```bash
git add docs/superpowers/specs/lookfeel/visualization-palette-swatches.html \
        docs/superpowers/specs/lookfeel/visualization-phase9c-deferred-consolidation-implementation-plan.md \
        docs/superpowers/specs/lookfeel/visualization-phase5-implementation-plan.md \
        docs/superpowers/specs/lookfeel/visualization-implementation-roadmap.md
git commit -m "Phase 9C item 3: record group-subtotal emphasis shipped; close Phase 5 item 3b"
```

---

## Validation summary

1. **Build/compile:** `./mvnw -q -pl core test-compile` clean; no web/CSS change expected
   (`git diff --name-only` shows only the three core Java files, three test files, and docs).
2. **Unit:** `VSTableStructureDefaultsTest` (subtotal color), `XTableStyleSpecInsertTest` (insert
   primitive), `XTableStyleGroupSubtotalOrderTest` (group-total specs precede the zebra, all carry
   `#EEEAE1`) green.
3. **Gate-off byte-identical:** crosstab live + PDF/PNG/SVG/Excel unchanged; no saved XML change
   (Task 4.1).
4. **Gate-on:** interior subtotals `#EEEAE1`, live == export (Task 4.2).
5. **Hierarchy:** grand total stays `#E9E4DA` (trailer band resolved first); three distinct total levels
   (Task 4.3).
6. **Defaults-only:** a user cell background wins over the subtotal default (Task 4.4).
7. **Boundaries:** non-Default style untouched; plain (non-crosstab) table untouched; no new property
   (Task 4.5).
8. **Org scope:** scheduled export resolves the gate per-org (Task 4.6).

---

## Branching (per CLAUDE.md)

Community-only core Java (the `subtotalBackground()` accessor, the `XTableStyle` insert overload, the
`DataVSAQuery` helper, three tests, docs), continuing the visualization work on `viz-updates` alongside
Phases 5–9. Confirm whether item 3 stays on `viz-updates` or a child `feature-{issue}`. Nothing on `main`
or a `v1.0.x`/`v1.1.x` release branch; nothing pushed/PR'd without explicit approval. An enterprise
submodule-pointer bump only at PR time.

---

## Related

- [visualization-phase9c-deferred-consolidation-implementation-plan.md](../specs/lookfeel/visualization-phase9c-deferred-consolidation-implementation-plan.md) — scheduled item 3 (this plan implements it)
- [visualization-phase5-implementation-plan.md](../specs/lookfeel/visualization-phase5-implementation-plan.md) — Part C1b (the style-layer injection this extends) and item 3b (the deferred subtotal work, with the known-good path)
- [2026-07-23-viz-phase9c-item2-sequential-gradient-ramp.md](2026-07-23-viz-phase9c-item2-sequential-gradient-ramp.md) — sibling Phase 9C item; the gated-resolver + manual-render-validation structure this plan mirrors
- [visualization-palette-swatches.html](../specs/lookfeel/visualization-palette-swatches.html) — `--viz-subtotal-bg #EEEAE1` swatch
