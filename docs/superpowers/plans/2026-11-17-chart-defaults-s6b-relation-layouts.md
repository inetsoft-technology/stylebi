# Chart Defaults S6b — Relation Layouts Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** On a modern (marked) relation chart:
- a circular network stays inside the plot, at most 80% of its shorter side, centred
- a network spreads its nodes to fill the plot
- a tree wider than the plot closes its sibling gaps, then shrinks to 75%, then scrolls, and opens with its root in view
- a tree box under 25px tall keeps a 2px corner

Classic charts are unchanged.

**Architecture:** Every item is a drawing rule (kind D). Nothing is stored: no XML, seed, flag, reset slot or residue rule.
- **Graph package.** New `RelationElement` properties default to off, and off draws today's layout (M1):
  - `plotFit`: lay out in the plot box
  - `cornerFloor`: keep a rounded node corner at least 2px
- **The plot box comes from a replot.** The first plot lays out in the element's layout size, as today. `RelationCoord.requiresReplot()` then asks for a second plot. `Plotter.plotAndLayout` and `VGraphPair.createEVGraph` already replot for maps, and on the second plot the element lays out in `RelationCoord.getPlotBox()`.
- **Generator.** `GraphGenerator.applyModernMarkRules` sets both flags on marked relation charts. `plotFit` is off when the author resized the plot.
- **First view.** `PlotArea` finds the tree's root, `Plot` carries it to the browser as `scrollAnchorX` / `scrollAnchorY`, and `ChartArea.resetScrollPosition` centres the root wherever it resets the scroll today.

**Tech Stack:** Java 21, JUnit 5, Spring test context (`@SreeHome`), Maven module `core`; Angular 21, Vitest, `@testing-library/angular` (portal `test-tl` target).

**Spec:** `docs/superpowers/specs/lookfeel/2026-11-06-chart-defaults-design.md` (S6 amended in `bab561703f`). The relevant parts are:
- §3 M1 and M9
- §5 S6: the shaping fact "Relation layouts are sized for the whole card", rows S6-4 to S6-6, and the section "S6b relation layouts"
- §8 bugs 02 and 03, §9's S6b items, and §11 "S6b"
- §10 D61, D97, D98, D106 and D107

**Builds on:** S6a (`bug-80455-s6` at `263f6be838`). Uses `BarVO.flooredCornerFraction(double, double)` (S4a, public static) and `GraphGenerator.applyModernMarkRules(GraphElement, int)`. S6a's `EGraph.topInset` already takes the title gap out of the coordinate bounds, so the plot box needs nothing more for S6-1.

**Verified against:** `bug-80455-s6b` at `263f6be838`. Paths are under `core/src/main/java/inetsoft/` unless they start with `core/` or `web/`.

## Corrections to the spec

| # | Spec | Code at `263f6be838` | This plan |
|---|---|---|---|
| C1 | S6b: "whether the plot box comes from a replot or from an estimate" (§9) | Node shapes scale with the screen transform (`RelationVO.paint`, `getShapes`: `getScreenTransform().createTransformedShape(this.shape)`), and `RelationVO` caches the shape when it is built. "Positions only" must therefore move the mx geometry before the VOs exist, that is, in `RelationElement.mxLayout`. The legend's size is known only after `VGraph.layout0` (`:745-779` sets the coordinate bounds) | **Replot.** `RelationCoord.requiresReplot()` is true while a fitted element laid out in a box other than the plot box. `Plotter.plotAndLayout:104-107` replots once; `VGraphPair:886-890` replots the expanded graph once |
| C2 | S6-4: "the node-count minimum radius in `mxCircleLayout` is dropped" | `mxCircleLayout.execute:246`: `r = max(n × max × sizeFactor / π, radius)`, `sizeFactor` 0.75 (`:67`), setter at `:106` | `layout.setSizeFactor(0)` on a fitted circle |
| C3 | S6-6: "a sibling gap of 24 and a level gap of 22" | `mxCompactTreeLayout`: sibling gap = `2 × nodeDistance` (`:640`, `:654`), level gap = `nodeDistance + levelDistance` (`:623`). `RelationElement:345` sets `nodeDistance` 12; `levelDistance` defaults to 10 | Closing the sibling gaps to 8 lays out again with `nodeDistance` 4 and `levelDistance` 18, so the level gap stays 22 |
| C4 | S6-4, S6-5: the drawing "stays inside the box" | Relation charts always scroll (`GraphUtil.isHScrollable:3126`). The expanded graph is as large as `RelationCoord.getUnitMinWidth/Height` (`:188-226`), which back-divides the visual bounds, labels included (`VGraphPair:855-871`). A label a few pixels past the box would make a few pixels of scroll | A fitted circle or network reports a minimum size of 1. `RelationCoord.fit`'s existing shrink (`:84-89`) then keeps an overhanging label inside the plot. A tree keeps today's minimum, so it scrolls |
| C5 | S6-6: "the server sends the root's position" | `Plot` has no such field. Precedent: `Plot.geoPadding()` (`web/graph/model/Plot.java:50`, `@Nullable Double`), filled in `GraphBuilder:563-576`. In the browser, `plot.bounds` is the whole scrollable plot and `plot.layoutBounds` the visible part (`chart-area.component.ts:980-984`; `pageRight:1587-1589` scrolls to at most `bounds.width − layoutBounds.width`) | Two `@Nullable Double`s on `Plot`, in the plot's region coordinates. The browser centres them in `layoutBounds`, clamped to the scroll range |

**Design calls (§9 left these to the builder):**
- **The first plot** lays out in the element's layout size (the content size, as today). The replot uses `RelationCoord.getPlotBox()`: the coordinate bounds less `RelationCoord.GAP` (4) on each side and 1 more, the box `fit` centres the drawing in. The extra pixel matches `fit`'s `plotw = w − 1` (`:68-69`), so a drawing that fills the box reports a minimum width equal to the plot width, which does not scroll (`VGraphPair:871-880`).
- **Facets** keep today's layout. The element checks `getParentCoordinate()` of its coordinate, so the generator needs no facet test.
- **Network fill is per axis.** x and y scale separately, so the nodes reach all four sides; "fill" read literally. Edges stay straight and crossings do not change.
- **The 75% floor is a scale, not a box size.** A tree whose node size is bound keeps its proportions; 33×23 is the default 44×30 box at the floor.
- **Room for outside labels** is today's: 20px across and two text heights down (`RelationElement:321-324`), taken from the box when labels are outside the node.
- **The root anchor** is the centre of the union of the root nodes' regions, so a forest centres its group of roots. It is sent only for a fitted tree.
- **The corner floor** covers every rounded corner on a marked relation node, an author's small fraction included, as S4-2 does for bars (`applyModernMarkRules:5198`). A square node (fraction 0) stays square.

**Known limitations:**
- **Cost.** A marked relation chart lays out twice per graph, and VS draws a real-size and an expanded graph. A 120-node network runs its organic layout four times instead of twice.
- **Neighbours may touch** on a 120-node circle (D106).
- **Exports** draw the whole tree shrunk to the card, as today, but they start from the 8px-gap layout, so the boxes are larger than today's.
- **Older builds and Revert** draw today's layout (M9).

## Global Constraints

- **Worktree:** `$WT` is the executor's worktree on branch `bug-80455-s6b`, at or after this plan's commit. Never work in `E:/StyleBI/stylebi-enterprise/community` or any other worktree.
- **Shared machine.** Other sessions run servers and live checks from other worktrees, using the shared Maven repository and the shared asset data. So:
  - Never run `mvnw install`, `mvnw package -pl server`, `spring-boot:run`, or anything else that writes outside `$WT`.
  - Never start a server or bind or call ports 8080, 8081 or 5005.
  - Run only the test classes a step names (Task 5 names packages). The full `core` suite runs only when the controller says so.
- **Git:**
  - Each git command is its own call, `git -C "$WT" ...`, with no `&&` anywhere in the command.
  - One commit per task, subject `Chart defaults S6, <what changed>`, last line `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
  - Subagents never run `git add`, `git commit` or `git stash` (a hook blocks them); the controller stages and commits after review. Never push.
- **Scope is the mark (M1).** `plotFit` and `cornerFloor` default to false, and false draws today's output. A classic chart is pixel-identical.
- **An author's plot resize** (`isWidthResized()` or `isHeightResized()` on the chart info) turns `plotFit` off. The corner floor stays on.
- **Values (all under `plotFit`, in screen pixels):**
  - **Circle:** `radius = max(0, (0.8 × min(boxW, boxH) − largest node side) / 2)`, `sizeFactor` 0.
  - **Network:** the finished layout's node positions are scaled per axis, so the nodes' extents span the box less the label room. Node sizes are unchanged.
  - **Tree:** natural `nodeDistance` 12 and `levelDistance` 10. If the breadth is wider than the box, lay out again with 4 and 18. If it is still wider, scale positions and sizes by `max(0.75, box breadth / breadth)`. The breadth is the width for a top-to-bottom or bottom-to-top tree, the height for a left-to-right or right-to-left tree.
  - **Corner floor:** `BarVO.flooredCornerFraction(fraction, min(w, h))`.
- **Comments:** a short clause each, with no ticket, PR, slice or design-doc names. No comments in Angular `.html` files.
- **New files** start with the AGPL header of `SeedChromeDefaultsTest.java`, `Copyright (C) 2026  InetSoft Technology`.
- **Tests that reach Spring beans** use `@ExtendWith(SpringExtension.class)`, `@ContextConfiguration(classes = BaseTestConfiguration.class, initializers = ConfigurationContextInitializer.class)`, `@SreeHome`, `@Tag("core")`, and clear `viewsheet.modernVisualization`, `viewsheet.darkMode` and `viewsheet.density` in `@AfterEach`.

## Review Focus

1. **A brushed marked relation chart** has a second, overlay `RelationElement` that never lays out (`createGeometry:136`, `:217`). The replot must settle and the overlay must sit on its nodes. Pinned by `aBrushedFittedChartSettlesInOneReplot` (Task 1).
2. **A plot smaller than a node**, e.g. a 60×40 card, must give no negative radius, scale or size and no NaN. Pinned by `aTinyBoxGivesNoNegativeLayout` (Task 1) and `aTinyBoxFloorsTheTreeScale` (Task 3).
3. **A one-node or two-node network** must not divide by zero when it scales positions (`maxX == 0`). Pinned by `aOneOrTwoNodeNetworkStaysFinite` (Task 2).
4. **Each of the four tree orientations** fits along its own breadth, and its root anchor lies at the right end. Pinned by `everyOrientationFitsItsBreadth` (Task 3) and `theAnchorFollowsTheOrientation` (Task 4).
5. **A forest** (several roots, joined by the hidden forest root at `:289-297`) fits all of its trees and centres the group of roots. Pinned by `aForestFitsAndCentresItsRoots` (Task 4).

## File Structure

- **Task 1:** `graph/element/RelationElement.java` (`plotFit`, `laidOutBox`, `createGeometry`, `mxLayout` CIRCLE, `equalsContent`), `graph/coord/RelationCoord.java` (`getPlotBox`, `requiresReplot`, minimum sizes), `report/composition/graph/GraphGenerator.java` (`applyModernMarkRules`).
- **Task 2:** `graph/element/RelationElement.java` (`mxLayout` ORGANIC, the scale after the move to the top left), `graph/coord/RelationCoord.java` (minimum sizes cover ORGANIC).
- **Task 3:** `graph/element/RelationElement.java` (`mxLayout` COMPACT_TREE, `cornerFloor`), `graph/visual/RelationVO.java` (`getInsideTextBox:276-300`, `resolveNodeShape:380-396`), `GraphGenerator.applyModernMarkRules`.
- **Task 4:** `graph/geometry/RelationGeometry.java` (`root`), `RelationElement.createGeometry` (marks the roots), `report/composition/region/PlotArea.java` (`getScrollAnchor`), `web/graph/model/Plot.java`, `web/graph/GraphBuilder.java` (`:565-576`), `web/projects/portal/src/app/graph/model/plot.ts`, `web/projects/portal/src/app/graph/objects/chart-area.component.ts` (`resetScrollPosition:442-467`).
- **Task 5:** spec §10 and the "S6b relation layouts" section; handoff `E:/Temp/chart-defaults-s6b-checks-handoff.md`.

**Test harnesses:**
- **Graph-level tests** copy `core/src/test/java/inetsoft/graph/coord/RelationCoordMinSizeTest.treeCoord`: an `EGraph` with a `RelationCoord` and a `RelationElement`, laid out with `Plotter.getPlotter(egraph).plotAndLayout(data, 0, 0, w, h)`. Mirror the generator's element setup (`GraphGenerator:3984-4007`):
  - **Circle and network:** node 5×5, label placement `TOP`, a text frame on the target so labels exist.
  - **Tree:** node 40×30, placement `CENTER`.
  - **Every element:** `StaticSizeFrame(1)` on both size frames, so a tree box is 44×30.
  - **The card:** use 376×190 for the plot of a 400×240 card at compact.
- **Graph space is y-up.** A node's drawn box is `RelationVO.getShapes()[0].getBounds2D()`, and the plot is `vgraph.getPlotBounds()`.
- **Generator tests** add to `core/src/test/java/inetsoft/report/composition/graph/ModernMarkRulesTest.java`. Use its `generator(Chart, VizMark, Object[][])` and the relation chart built in `edgeLargest`, factored into a helper `relationChart(int chartType)`.

## How to run tests

From `$WT`:

```bash
./mvnw -q test -pl core "-Dtest=ClassA,ClassB" -Dsurefire.failIfNoSpecifiedTests=false
```

- Read `core/target/surefire-reports/*.txt` for counts.
- Run every new or changed class alone once as well (CLAUDE.md).
- If compilation fails with "Attempt to recreate … HeartBeatServiceProxy", delete the untracked `core/target/generated-test-sources`.
- **Portal TL (Task 4):** from `$WT/web`, `npx ng run portal:test-tl --include='**/chart-area.component.interaction.tl.spec.ts'`.
  - Never use `ng test portal` for a `.tl.spec.ts`: it matches nothing and exits 0.
  - If `$WT/web/node_modules` is missing, run `npm ci` in `$WT/web` first. Never junction another checkout's `node_modules`; Vitest fails on it.

---

### Task 1: Lay out a marked circular network in the plot box (S6-4, D97, D106)

**Files:**
- Modify: `graph/element/RelationElement.java` (`createGeometry:117-305`, `mxLayout` CIRCLE `:327-340`, `equalsContent:1124-1150`, fields)
- Modify: `graph/coord/RelationCoord.java` (new `getPlotBox`, `requiresReplot`; `getUnitMinWidth:188`, `getUnitMinHeight:210`)
- Modify: `report/composition/graph/GraphGenerator.java` (`applyModernMarkRules:5193-5229`)
- Test: `core/src/test/java/inetsoft/graph/coord/RelationPlotFitTest.java` (new, Spring), `ModernMarkRulesTest` (add cases)

**Interfaces:**
- Produces:
  - `RelationElement.setPlotFit(boolean)` / `boolean isPlotFit()`: default false, part of `equalsContent`. Javadoc: lay out inside the plot box (circle, network, tree); off keeps today's layout.
  - `Dimension2D RelationElement.getLaidOutBox()`: the box the last `createGeometry` laid out in. It is a `transient` field, set to null at the start of `createGeometry`. It stays null when the element used no box: `plotFit` off, an overlay, a faceted or non-relation coordinate, or an algorithm not yet fitted (only CIRCLE in this task).
  - `Dimension2D RelationCoord.getPlotBox()`: null when `getCoordBounds()` is null or the coordinate has a parent coordinate. Otherwise `new DimensionD(max(0, w − 2·GAP − 1), max(0, h − 2·GAP − 1))` (367×181 for a 376×190 plot).
  - `RelationCoord.requiresReplot()`: true when `getPlotBox()` is not null and a `RelationElement` of `getVGraph().getEGraph()` has `isPlotFit()` and a non-null laid-out box that differs from it by more than 0.5 in width or height. False when `getVGraph()` is null.
  - **The box an element lays out in** (private in `RelationElement`): `plotFit`, no `"overlay"` hint, the coordinate of the `GGraph` is a `RelationCoord` with no parent coordinate, and the algorithm is fitted. Then the box is `getPlotBox()`, or `layoutSize` while `getPlotBox()` is null (the first plot). Otherwise no box.

- [ ] **Step 1: Write the failing graph tests** (`RelationPlotFitTest`, circles of N nodes: a root `R` with N−1 targets, 376×190)
  - `aFittedCircleStaysInsideThePlot`: at 8, 30 and 120 nodes, every node box is inside `getPlotBounds()` (±1). Then a node colour frame on the target, with its legend at `GraphConstants.RIGHT`: the plot is narrower and every node box is still inside.
  - `aFittedCircleIsAtMostEightyPercent`: at 8 nodes, the union of the node boxes is at most `0.8 × min(plot box) + 1` and at least `0.6 ×` it on its larger side. Its centre is within 2px of the plot's centre.
  - `aFittedCircleDoesNotScroll`: at 120 nodes, `vgraph.getMinPlotWidth() < getPlotBounds().getWidth()`, and the same for height.
  - `oneReplotSettlesTheBox`: after `plotAndLayout`, `getLaidOutBox()` equals `coord.getPlotBox()` and `coord.requiresReplot()` is false.
  - `aBrushedFittedChartSettlesInOneReplot`: add a second `RelationElement` with `setHint("overlay", "true")` and the same fields → `requiresReplot()` is false after `plotAndLayout`, and the overlay's VOs sit on the base element's nodes (same centres ±1).
  - `aTinyBoxGivesNoNegativeLayout`: 60×40 at 30 nodes → `getLayoutRadius() >= 0`, and every coordinate is finite.
  - `aClassicCircleKeepsTodaysLayout`: `plotFit` off at 120 nodes → `getLaidOutBox()` is null, `requiresReplot()` is false, and `getLayoutRadius()` is at least `120 × maxNode × 0.75 / π` − 0.5 (the node-count minimum still applies). The minimum plot width is greater than the plot width (it scrolls, as today).
  - `aFacetedCircleLaysOutWithoutABox`: `coord.setParentCoordinate(new FacetCoord())`, then `elem.createGeometry(data, new GGraph(egraph, coord, data))` → `getLaidOutBox()` is null and `coord.getPlotBox()` is null.

- [ ] **Step 2: Write the failing generator tests** (`ModernMarkRulesTest`)
  - `aMarkedRelationChartFitsThePlot`: `CHART_TREE`, `CHART_NETWORK` and `CHART_CIRCULAR` with `VizMark.MODERN_LIGHT` → `isPlotFit()`.
  - `aClassicRelationChartDoesNot`: the same three, mark `null` → false.
  - `anAuthorsPlotResizeKeepsTodaysLayout`: `setWidthResized(true)` on the chart info → false; `setHeightResized(true)` alone → false.

- [ ] **Step 3: Run them and see them fail**

Run: `./mvnw -q test -pl core "-Dtest=RelationPlotFitTest,ModernMarkRulesTest" -Dsurefire.failIfNoSpecifiedTests=false`
Expected: compile failure on `setPlotFit`, `getLaidOutBox` and `getPlotBox`.

- [ ] **Step 4: Implement**
  - **`RelationElement`:** the fields and accessors next to `smoothEdges`. `createGeometry` resolves the box before `mxLayout` and stores it in `laidOutBox`. `mxLayout` CIRCLE with a box: `setSizeFactor(0)` and the radius from Global Constraints. With no box, today's code.
  - **`RelationCoord`:** `getPlotBox`, `requiresReplot`. `getUnitMinWidth` and `getUnitMinHeight` return 1 when the coordinate has no parent and its first element is a `RelationElement` with `isPlotFit()` and algorithm `CIRCLE`.
  - **`GraphGenerator.applyModernMarkRules`:** a `RelationElement` branch, `relation.setPlotFit(!info.isWidthResized() && !info.isHeightResized())`.

- [ ] **Step 5: Run the tests and see them pass**, plus `RelationCoordMinSizeTest`, `RelationElementLayoutCenterTest`, `RelationEdgeVOClipTest` and `VSChartRelationNodeLegendTest` (slow-tagged: add `-Dsurefire.groups=slow` for that class) unchanged.

- [ ] **Step 6: Commit** — `Chart defaults S6, keep a modern circular network inside its plot`

---

### Task 2: Spread a marked network to fill the plot box (S6-5, D97)

**Files:**
- Modify: `graph/element/RelationElement.java` (`mxLayout` ORGANIC `:360-381`, after the move to the top left `:415-423`)
- Modify: `graph/coord/RelationCoord.java` (the minimum-size rule)
- Test: `RelationPlotFitTest` (add cases)

**Interfaces:**
- Consumes: Task 1's box resolution, `getLaidOutBox()` and minimum-size rule.
- Produces: ORGANIC is a fitted algorithm, and the minimum-size rule covers it.

- [ ] **Step 1: Write the failing tests** (networks of N nodes in clusters of five: each cluster a hub with four leaves, hubs chained, 376×190 unless stated)
  - **The available box** in these tests is `availW = plotBox.w − 20` by `availH = plotBox.h − 2 × text height`, with the text height from `GTool.getFontMetrics(elem.getTextSpec().getFont()).getHeight()`.
  - `aFittedNetworkFillsThePlot`: at 8, 30 and 120 nodes, with and without a node colour legend at `RIGHT`:
    - every node box is inside the plot (±1)
    - the union of the node boxes spans at least 95% of `availW` and of `availH`
  - `aSmallNetworkSpreadsOnALargeCard`: 8 nodes at 800×600 → the span is at least 95% of `availW` and `availH`. Classic at 800×600 spans less than 50% of the plot width, as today.
  - `nodesKeepTheirSize`: every node box is the same size as with `plotFit` off.
  - `aFittedNetworkDoesNotScroll`: target labels 30 characters long → the minimum plot width and height are below the plot's.
  - `aOneOrTwoNodeNetworkStaysFinite`: one row `A → B`, and one row `A → A` → every coordinate is finite and inside the plot.

- [ ] **Step 2: Run them and see them fail**

Run: `./mvnw -q test -pl core "-Dtest=RelationPlotFitTest" -Dsurefire.failIfNoSpecifiedTests=false`
Expected: the new network cases fail (span and scroll), the circle cases pass.

- [ ] **Step 3: Implement**
  - **ORGANIC with a box:** the organic bounds come from the box instead of `layoutSize`; the label room and the `min(20, 5%)` gaps are taken off as today.
  - **Scale the positions.** After the move to the top left, take `availW` and `availH`, the box less the label room.
    - `sx` is the smallest `(availW − wᵢ) / xᵢ` over nodes with `xᵢ > 0`, and at least 0. It is 1 when no node has `xᵢ > 0`. `sy` is the same in y.
    - Apply `AffineTransform.getScaleInstance(sx, sy)` with `transformPosition`, as `scaleGeometries:960-973` does, to every node geometry and every edge geometry.
  - **`RelationCoord`:** the minimum-size rule covers `ORGANIC`.

- [ ] **Step 4: Run the tests and see them pass**, plus Task 1's list.

- [ ] **Step 5: Commit** — `Chart defaults S6, spread a modern network to fill its plot`

---

### Task 3: Fit a marked tree's breadth, then scroll; floor its corners (S6-6, D98, D107)

**Files:**
- Modify: `graph/element/RelationElement.java` (`mxLayout` COMPACT_TREE `:341-349`, after `layout0.execute:408`, after the move to the top left `:415-423`; `cornerFloor`; `equalsContent`)
- Modify: `graph/visual/RelationVO.java` (`getInsideTextBox:296`, `resolveNodeShape:387`)
- Modify: `GraphGenerator.applyModernMarkRules` (the Task 1 branch)
- Test: `core/src/test/java/inetsoft/graph/element/RelationTreeFitTest.java` (new, Spring), `core/src/test/java/inetsoft/graph/visual/RelationVONodeShapeTest.java` (add cases), `ModernMarkRulesTest` (add a case)

**Interfaces:**
- Consumes: Task 1's box resolution.
- Produces:
  - COMPACT_TREE is a fitted algorithm.
  - `RelationElement.setCornerFloor(boolean)` / `boolean isCornerFloor()`: default false, part of `equalsContent`. Javadoc as `IntervalElement.isCornerFloor`.

- [ ] **Step 1: Write the failing tree tests** (`RelationTreeFitTest`, 376×190; "the canonical tree" is 15 nodes: `R → A..E`, `A..D → two leaves each`, `E → one leaf`, so 9 leaves)
  - `aTreeThatFitsKeepsItsNaturalLayout`: `R → 4 leaves` → bottom-row gaps 24 and boxes 44×30, the same with `plotFit` on and off.
  - `theCanonicalTreeClosesGapsThenShrinks`. Let `s = 367 / (9 × 44 + 8 × 8)` = 367 / 460, about 0.8.
    - The leaves are `44s × 30s` (±1), with gaps of `8s` (±1).
    - The breadth fits the plot box, and `vgraph.getMinPlotWidth()` is at most the plot width, so there is no horizontal scroll.
  - `levelGapsShrinkOnlyWithTheScale`: in the canonical tree, the gap between level 1's bottom and level 2's top is `22s` (±1).
  - `aWideTreeStopsAtSeventyFivePercentAndScrolls`: `R → 4 → 40 leaves` → leaves 33×23 (±1), gaps 6 (±1), and a minimum plot width greater than the plot width.
  - `everyOrientationFitsItsBreadth`, for the canonical tree:
    - **left-to-right, and right-to-left (`setFlipped(true)`)**, on a 376×300 plot (box 367×291). Stacked by height, the breadth is 9 × 30 + 8 × 24 = 462, and 334 with the gaps closed. So:
      - the boxes scale by 291 / 334
      - the breadth along y fits 291, with no vertical scroll
      - the plot width plays no part: with the plot narrowed to 200 wide, the scale is the same
    - **bottom-to-top:** as top-to-bottom, the breadth along x fits 367.
  - `aSizeBoundTreeKeepsItsProportions`: a `LinearSizeFrame` on the node size with two leaf values → the ratio of the two leaves' widths is the same with `plotFit` on and off (±0.02).
  - `aTinyBoxFloorsTheTreeScale`: the canonical tree at 60×40 → leaves 33×23 (±1), all sizes positive.
  - `aClassicTreeKeepsTodaysLayout`: `plotFit` off, the canonical tree → gaps 24, boxes 44×30, and the minimum plot width is greater than the plot width.

- [ ] **Step 2: Write the failing corner tests** (`RelationVONodeShapeTest`, `resolveNodeShape` with fraction 0.08)
  - `aShortMarkedNodeTakesTheTwoPixelCorner`: `cornerFloor` on, box 33×23 → a `RoundRectangle2D` with arc width 4 (±0.01).
  - `aTallMarkedNodeKeepsItsFraction`: `cornerFloor` on, box 44×30 → arc 4.8.
  - `aClassicNodeIsNotFloored`: `cornerFloor` off, box 33×23 → arc 3.68.
  - `aSquareNodeStaysSquare`: fraction 0 with `cornerFloor` on → null.
  - `theTextBoxFollowsTheFlooredCorner`: `getInsideTextBox` at 33×23 with the floor on insets by `2 × (1 − 1/√2)`.

- [ ] **Step 3: Write the failing generator test** (`ModernMarkRulesTest`): `aMarkedRelationChartFloorsItsCorners` (`CHART_TREE`, marked → `isCornerFloor()`; classic → false).

- [ ] **Step 4: Run them and see them fail**

Run: `./mvnw -q test -pl core "-Dtest=RelationTreeFitTest,RelationVONodeShapeTest,ModernMarkRulesTest" -Dsurefire.failIfNoSpecifiedTests=false`
Expected: compile failure on `setCornerFloor`.

- [ ] **Step 5: Implement**
  - **Measure the breadth.** After `layout0.execute`, when a tree has a box, its breadth is `max(x + w) − min(x)` over the `RelationGeometry` nodes (`y` and `h` for a horizontal tree). The hidden forest root is not one of them.
  - **The available breadth** is the box width (height for a horizontal tree), less the label room on that side when labels are outside.
  - **Wider than that:** `setNodeDistance(4)`, `setLevelDistance(18)`, execute again, and measure again. If it is still wider, the scale is `s = max(0.75, available / breadth)`.
  - **Scale after the move to the top left**, before `flipMajorAxis`. Apply `geo.transform(AffineTransform.getScaleInstance(s, s))` (positions and sizes) to every node geometry and every edge geometry.
  - **`RelationVO`:** where `getNodeCornerRadius()` is read, use `BarVO.flooredCornerFraction(r, min(w, h))` when `elem.isCornerFloor()`.
  - **`applyModernMarkRules`:** `relation.setCornerFloor(true)`.

- [ ] **Step 6: Run the tests and see them pass**, plus `RelationCoordMinSizeTest`, `RelationVOInsideTextBoxTest`, `RelationElementLayoutCenterTest`, `RelationEdgeVOClipTest` and `RelationPlotFitTest`.

- [ ] **Step 7: Commit** — `Chart defaults S6, fit a modern tree to its plot width and keep its corners`

---

### Task 4: Open a marked tree on its root (S6-6, D107)

**Files:**
- Modify: `graph/geometry/RelationGeometry.java` (`root` next to `multiParent:184-190`)
- Modify: `graph/element/RelationElement.java` (`createGeometry`: the nodes added to `roots` at `:249`)
- Modify: `report/composition/region/PlotArea.java` (new `getScrollAnchor`)
- Modify: `web/graph/model/Plot.java`, `web/graph/GraphBuilder.java` (`:565-576`)
- Modify: `web/projects/portal/src/app/graph/model/plot.ts`, `web/projects/portal/src/app/graph/objects/chart-area.component.ts` (`resetScrollPosition:442-467`)
- Test: `core/src/test/java/inetsoft/report/composition/region/TreeScrollAnchorTest.java` (new, Spring), `web/projects/portal/src/app/graph/objects/chart-area.component.interaction.tl.spec.ts` (Group 5, add cases)

**Interfaces:**
- Consumes: Task 1's `isPlotFit()` and Task 3's tree fit.
- Produces:
  - `RelationGeometry.setRoot(boolean)` / `boolean isRoot()`: true for a node that is never a target.
  - `Point2D PlotArea.getScrollAnchor()`: the centre of the union of the regions of the root nodes' `VisualObjectArea`s, in the plot's region coordinates.
    - Only when the plot graph's first element is a `RelationElement` with `isPlotFit()` and algorithm `COMPACT_TREE`. Otherwise null.
    - Only nodes of the element without the `"overlay"` hint count.
  - `@Nullable Double Plot.scrollAnchorX()` / `scrollAnchorY()`, filled from `getScrollAnchor()`.
  - TS `Plot`: `scrollAnchorX?: number; scrollAnchorY?: number;`.

- [ ] **Step 1: Write the failing Java tests** (`TreeScrollAnchorTest`)
  - **Harness:** a `ModernMarkRulesTest`-style generator, then `new ChartArea(vgraph, graph, gen.getData(), null, cinfo).getPlotArea()`, as `ModernAxisTitleTest:114` builds its area.
  - `theAnchorIsTheRootsCentre`: a marked 60-node tree → the anchor equals the centre of the root node's area region (±0.5).
  - `theAnchorFollowsTheOrientation`:
    - top-to-bottom: the anchor's y is within one box height of the plot's top.
    - left-to-right: its x is within one box width of the left.
    - bottom-to-top and right-to-left: at the far end.
  - `aForestFitsAndCentresItsRoots`: two roots, `R1 → 3 leaves` and `R2 → 3 leaves` → the anchor is the centre of the union of the two root regions. Both trees fit the plot width.
  - `onlyAFittedTreeHasAnAnchor`: a classic tree, a marked network and a marked circle → null.
  - `rootsAreTheSourceOnlyNodes` (graph level): in the canonical tree only `R` is a root; in a forest, both roots.

- [ ] **Step 2: Write the failing TL tests** (Group 5, `makePlot` with the new fields)
  - `centres a tree's root on the first draw`: `resetScrollPosition(null, plot)`, bounds 1000×200, layoutBounds 370×190, anchor (500, 15) → `scrollLeft` 315, `scrollTop` 0.
  - `clamps the root to the scroll range`: anchor x 990 → 630; anchor x 10 → 0.
  - `centres the root again when the plot width changes`: an old plot 800 wide, a new plot 1000 wide with anchor x 500 → 315, and `onScroll` emitted.
  - `centres a left-to-right tree vertically`: bounds 370×900, layoutBounds 370×190, anchor (15, 450) → `scrollLeft` 0, `scrollTop` 355.
  - `keeps the scroll when the size is unchanged, even with an anchor`: as the existing case, with an anchor → 10 and 20 are kept.
  - The existing three cases pass unchanged.

- [ ] **Step 3: Run them and see them fail**

Run: `./mvnw -q test -pl core "-Dtest=TreeScrollAnchorTest" -Dsurefire.failIfNoSpecifiedTests=false`
Expected: compile failure on `getScrollAnchor` and `isRoot`.

Run (from `$WT/web`): `npx ng run portal:test-tl --include='**/chart-area.component.interaction.tl.spec.ts'`
Expected: the new anchor cases fail.

- [ ] **Step 4: Implement**
  - **Roots:** `createGeometry` calls `setRoot(true)` on each node it adds to `roots`.
  - **`PlotArea.getScrollAnchor`:** walk the plot's child areas, as `getChildAreas` builds them.
  - **`Plot` and `GraphBuilder`:** the two fields, filled from the anchor, null when there is none.
  - **`ChartArea.resetScrollPosition`.** Wherever it sets `scrollLeft` or `scrollTop` to 0 today, set it to `anchoredScroll(anchor, view, content)` instead:
    - `anchoredScroll` returns 0 when the anchor is null. Otherwise it returns `clamp(anchor − view / 2, 0, max(0, content − view))`.
    - For `scrollLeft` the arguments are `nplot.scrollAnchorX`, `nplot.layoutBounds.width` and `nplot.bounds.width`; for `scrollTop`, the y anchor and the heights.
    - When `oplot` is null and `nplot` is not, emit `onScroll` once if either value is not 0. When `nplot` is null, both are 0, as today.

- [ ] **Step 5: Run the tests and see them pass**, and run `chart-area.component.risk.tl.spec.ts` and `chart-area.component.display.tl.spec.ts` the same way, one `--include` per run. `makePlot` (`chart-area.component.test-helpers.ts:63`) takes the new fields as overrides.

- [ ] **Step 6: Commit** — `Chart defaults S6, open a modern tree with its root in view`

---

### Task 5: Whole-branch check, spec record and live-check handoff (all S6b)

**Files:** spec `docs/superpowers/specs/lookfeel/2026-11-06-chart-defaults-design.md` (§10 and the "S6b relation layouts" section). Handoff: `E:/Temp/chart-defaults-s6b-checks-handoff.md`.

- [ ] **Step 1: Run the graph, generator and region packages**

Run: `./mvnw -q test -pl core "-Dtest=inetsoft.graph.**.*Test,inetsoft.report.composition.graph.**.*Test,inetsoft.report.composition.region.**.*Test" -Dsurefire.failIfNoSpecifiedTests=false`
Expected: 0 failures. Report the counts from `core/target/surefire-reports`. Then run `VSChartRelationNodeLegendTest` with `-Dsurefire.groups=slow`.

- [ ] **Step 2: Record the design calls in the spec.** Add rows D108 to D114 to §10 ("Design, from the S6b plan"), one for each of the seven design calls in this plan's list, plus any ruling made during execution. Add one line to the "S6b relation layouts" section citing them. Commit: `Chart defaults S6, record the S6b design calls`.

- [ ] **Step 3: Write the live-check handoff**, in the format of `E:/Temp/chart-defaults-s6a-checks-handoff.md`: base build `263f6be838`, head build this branch, and a Result column. Use the bundle's repro charts (`chart-defaults-design/repro/charts.md`, "Circular network", "Network", "Tree"). Rows:
  - **S6-4:** circular networks of 8, 30 and 120 nodes, and one with colour by cluster (a legend), at dense, compact and comfortable.
    - The ring and its labels are inside the plot with no scroll, at most 80% of the shorter side and centred.
    - The base build scrolls or cuts the 120-node ring.
  - **S6-5:** Network, Network 30, Network 120, text labels, and the small 300×180 card: the nodes fill the plot inside the padding with no scroll. On a 1000×600 card the nodes spread (bug 03's case).
  - **S6-6:**
    - The 15-node tree fits a 400×240 card with no horizontal scroll.
    - The 60- and 200-node trees scroll and open with the root in view, at the top centre. The same in the left-to-right, bottom-to-top and right-to-left orientations.
    - Resizing the assembly re-centres the root, in the composer, Preview and the portal viewer.
    - Long names are cut at about 4-5 characters at the floor.
    - Shrunk boxes keep a 2px corner.
  - **Author resize:** a plot resized by the author (`VSChartPlotResizeService`) draws as the base build.
  - **Exports:** PNG and PDF of each chart draw the whole drawing inside the card, the tree shrunk as on the base build.
  - **Brushing:** a brushed marked tree and network, with the overlay on its nodes.
  - **Revert:** after Revert, each chart draws as the base build.
  - **Classic identity:** a classic dashboard with each relation type (tiles, Save As XML, PNG and PDF) is identical across the two builds.

- [ ] **Step 4: Report** the counts, the handoff path and anything left for the owner. No further commit unless a test fix was needed (then `Chart defaults S6, <fix>`).
