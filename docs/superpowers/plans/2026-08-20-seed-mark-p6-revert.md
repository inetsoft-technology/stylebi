# Seed Mark P6 — Revert, and the End of the Gate's Read-Time Life — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship Revert — the per-dashboard mirror of Modernize — and with it end the org gate's read-time life, so the gate becomes purely a creation-time switch and an assembly's own mark decides how it renders in either gate state.

**Architecture:** Revert is not a new mechanism. It is `seedChromeDefaults(VizContext.of(info))` called on an assembly whose mark has just been cleared — the identical call creation makes — reached through the same enumeration `Modernize` already uses, with the predicate inverted. Once that reversal path exists, four gate reads that only made sense while gate-off meant legacy are deleted together in one commit: the `gate &&` term in `VizContext.of(VizMark)`, both `PlotDescriptor` seed booleans, and `VSObjectChromeDefaults.resolveSeededCorner`. Splitting any of them leaves a marked assembly modern everywhere except one property. Revert lands and is fully wired *before* that commit, so no intermediate build ships a gate whose off state has no reversal path.

**Tech Stack:** Java 21 / Spring Boot 3.5.8 (core), Angular 21.2 + TypeScript 5.9 (portal, em), JUnit 5 + Mockito for Java, Vitest 4.1.7 for the browser.

**Spec:** `docs/superpowers/specs/2026-08-14-seed-mark-forward-half-design.md` — §5's P6 (five pieces) and §2's "The interim term", both amended 2026-08-20 by the review that produced this plan. Read alongside `docs/superpowers/specs/lookfeel/seeded-value-reversibility-decisions.md` decision 13 and `docs/superpowers/specs/lookfeel/chart-card-roadmap.md` "Carried into P6".

**Verified against:** community `viz-updates` @ `35ca4fce0`, which is `HEAD` and carries P1–P5. Every file and line cited below was read at that commit.

## Global Constraints

- **Tasks 2–6 are the phase. Task 6 is the behaviour reversal and its four deletions land in ONE commit.** `VizContext.java:66`'s `gate &&` term, `PlotDescriptor.modernCornerSeed`, `PlotDescriptor.modernSmoothSeed`, and `VSObjectChromeDefaults.resolveSeededCorner`. Delete the term alone → a marked chart in a gate-off org keeps modern chrome except square bars and straight lines. Delete the booleans with it → it still loses its 12px card radius. Every partial state is worse than either end.
- **This plan deviates from the spec's commit grouping, deliberately.** §5's P6 says pieces 1–4 land together, which would put the revert utility in the same commit as the deletions and leave the composer wiring for afterwards — a build in which Revert exists but nobody can press it. Tasks 2–5 here ship a fully working Revert with **zero** change to how anything renders, and Task 6 is then the pure behaviour reversal with its reversal path already in place. This satisfies both constraints the spec cares about (a reversal path exists before the term dies; the deletions are inseparable) and satisfies the ordering principle §5 says P6 has to bend — so it does not have to bend.
- **`ChartVSAssemblyInfo`'s `else` branch is behaviour-neutral before Task 6.** `seedChromeDefaults` runs at creation and from Modernize only. At gate-off creation the else branch writes `barCornerRadius = 0` (already the field default) and `smoothLines = false` (already the field default), and both setters clear a seed boolean that is already false. Verified by reading `PlotDescriptor.java:1996`, `:2005`, `:2009`.
- **Revert takes no gate term.** `revertable` is "this sheet has marked content", full stop. `modernize()` has a gate floor because modernizing into a closed gate produces content that changes the moment the gate opens; clearing a mark has no such hazard. Decision 13 offers Revert under both gate states on purpose.
- **Revert writes the DEFAULT tier only, plus two untiered chart plot values.** `PlotDescriptor.barCornerRadius` and `smoothLines` are plain fields with no USER tier, so Revert resets them whether an author chose them or not. Accepted (symmetric with Modernize); do not add a preservation check.
- **Never run the full TL suite.** Scope every `*.tl.spec.ts` run with `--include`. An unfiltered run exceeds the foreground window, gets killed, and orphans multi-GB vitest workers.
- **Test commands.** Java: `./mvnw test -pl core -Dtest=<Class>` from `community/`. Portal unit: `cd community/web && npx ng test portal --include="**/<file>.spec.ts"`. Portal TL: `cd community/web && npx ng run portal:test-tl --include=<path>`. EM unit: `cd community/web && npx ng test em --include="**/<file>.spec.ts"`.
- **Branch:** work on `viz-updates` in the `community` submodule. All changes are under `community/`; nothing in the enterprise modules references the gate, `VizContext`, `VizMark` or either seed boolean (verified by grep across `enterprise/`, `server/`, `connectors/`, `integration/`, `shell/`). **Community PR only.**
- **Commit per task.**

---

## File Structure

**Core — the mechanism:**
- `core/src/main/java/inetsoft/uql/viewsheet/internal/VizModernizeUtil.java` — gains `revert()` and `hasMarked()`; its two private enumerations collapse into one `collect(vs, predicate)` so forward and backward genuinely share the traversal rather than duplicating it.
- `core/src/main/java/inetsoft/uql/viewsheet/internal/ChartVSAssemblyInfo.java` — `seedChromeDefaults` gains its `else` branch (`:106-112`).
- `core/src/main/java/inetsoft/uql/viewsheet/internal/VizContext.java` — the `gate &&` term goes (`:66`).
- `core/src/main/java/inetsoft/uql/viewsheet/graph/PlotDescriptor.java` — both seed booleans and both now-redundant `*Value()` accessors go.
- `core/src/main/java/inetsoft/uql/viewsheet/internal/VSObjectChromeDefaults.java` — `resolveSeededCorner` goes (`:68-70`).
- `core/src/main/java/inetsoft/uql/viewsheet/VSCompositeFormat.java` — `resolveDefaultTierCorner` and its TAB carve-out go (`:334-337`); `getRoundCornerValue()` returns the raw DEFAULT-tier radius.

**Core — the two seed-boolean consumers the spec did not list:**
- `core/src/main/java/inetsoft/web/binding/controller/ChangeChartTypeService.java:341` — loses one line.
- `core/src/main/java/inetsoft/web/graph/model/dialog/ChartPlotOptionsPaneModel.java:227-229` — loses its guard, writes unconditionally.

**Core — the wiring, mirroring P3's:**
- `core/src/main/java/inetsoft/web/composer/vs/controller/RevertViewsheetController.java` — new, beside `ModernizeViewsheetController` (58 lines).
- `core/src/main/java/inetsoft/web/composer/vs/controller/RevertViewsheetService.java` — new, beside `ModernizeViewsheetService` (90 lines). `RevertViewsheetServiceProxy` is generated by the `@ClusterProxy` annotation processor into `core/target/generated-sources/annotations` — no file to author, but the controller will not compile until one build has run.
- `core/src/main/java/inetsoft/web/viewsheet/service/CoreLifecycleService.java:314` — a `revertable` flag beside `modernizable` in the same `infoMap`.

**Client:**
- `web/projects/portal/src/app/composer/data/vs/viewsheet.ts:54-56` — a `revertable` field beside `modernizable`.
- `web/projects/portal/src/app/composer/gui/vs/editor/viewsheet-pane.component.ts` — the flag read (`:820`), a menu entry (after the modernize entry at `:416-423`), and a `revert()` method reusing the private `confirm()` helper at `:1447`.
- `web/projects/portal/src/scss/_viz-tokens.scss:127,137` — two added compound selectors (Task 1, an independent P5 regression fix).
- `web/projects/portal/src/app/portal/app.component.ts:272`, `composer/app.component.ts:145`, `vsobjects/viewer-app.component.ts:2796` — the `if(modern)` density guard, dropped in Task 6.
- `core/src/main/resources/inetsoft/util/srinter.properties` — two new keys, and one existing description reworded.

**EM:**
- `web/projects/em/src/app/settings/presentation/look-and-feel-settings-view/look-and-feel-settings-view.component.html:38-49` — Density unhidden, Dark Mode left hidden, and a tooltip on the gate checkbox.

**Tests created:**
- none — every test lands in an existing file.

**Tests modified:** `VizModernizeUtilTest`, `SeedChromeDefaultsTest`, `VizContextTest`, `VizContextReadFlipTest`, `ChartVSAssemblyInfoBarRoundingTest`, `PlotDescriptorXmlTest`, `VSCompositeFormatRoundCornerGateTest`, `VSObjectChromeDefaultsTest`, `ChartVSAScriptableTest`, `ChangeChartTypeServiceSmoothLinesTransitionTest`, `ChartPlotOptionsPaneModelTest`, `VSWizardBindingHandlerSmoothLinesTest`, `viewsheet-pane.component.interaction.tl.spec.ts`. Plus a new `RevertViewsheetServiceTest` modelled on `ModernizeViewsheetServiceTest`.

---

## Task 1: Restore the density tier for org-level surfaces (P5 regression)

Independent of the rest of P6 — a live bug fix that commits on its own, placed first because it is the cheapest and because Task 6 touches the same lines. Pre-P5 the density matrices were compound (`.viz-modern.viz-density-compact`), so a `body` carrying both classes matched and body-appended surfaces resolved the org's chosen density. P5 rewrote them to ancestor-descendant form and put `.viz-shell` only in the bare/dense group, so a body carrying `viz-shell` + `viz-density-compact` now resolves **dense** at every density — a descendant selector cannot match its own element.

**Files:**
- Modify: `web/projects/portal/src/scss/_viz-tokens.scss:127`, `:137`

**Interfaces:**
- Consumes: nothing
- Produces: nothing — CSS only, no symbols

- [ ] **Step 1: Confirm the regression is real before changing anything**

Run:

```bash
cd community && git show 4c237a7dd^:web/projects/portal/src/scss/_viz-tokens.scss | sed -n '105,135p'
```

Expected: the compact matrix reads `.viz-modern.viz-density-compact {` — a **compound** selector. Then:

```bash
cd community && sed -n '113,140p' web/projects/portal/src/scss/_viz-tokens.scss
```

Expected: `.viz-shell` appears only in the group at `:115-117` (the dense values), and the compact/comfortable matrices at `:127` and `:137` are `.viz-density-compact .viz-modern` / `.viz-density-comfortable .viz-modern` — descendant selectors. Those two facts together are the bug.

- [ ] **Step 2: Add the two compound selectors**

At `:127`, change:

```scss
.viz-density-compact .viz-modern {
```

to:

```scss
// the body carries viz-shell and viz-density-<mode> on ONE element, so org-level surfaces need the
// compound form; the descendant form below it is for the per-assembly wrapper
.viz-density-compact.viz-shell,
.viz-density-compact .viz-modern {
```

At `:137`, change:

```scss
.viz-density-comfortable .viz-modern {
```

to:

```scss
.viz-density-comfortable.viz-shell,
.viz-density-comfortable .viz-modern {
```

- [ ] **Step 3: Verify the stylesheet still compiles**

Run: `cd community/web && npx ng build portal --configuration development`
Expected: build succeeds with no sass errors.

- [ ] **Step 4: Record the coverage gap rather than pretending it is covered**

There is no automated test for token resolution — it needs a compiled stylesheet in a real document, which neither the unit nor the TL runner provides. Verification is the build above plus the manual check in Task 8. Add nothing; this step is a reminder not to write a test that asserts a selector string, which would pin the implementation and not the behaviour.

- [ ] **Step 5: Commit**

```bash
cd community && git add web/projects/portal/src/scss/_viz-tokens.scss
git commit -m "fix(viz): restore the density tier for org-level surfaces

P5 rewrote the density matrices from compound to ancestor-descendant form and
listed .viz-shell only in the dense group, so a body carrying viz-shell plus
viz-density-compact resolved dense at every density: a descendant selector
cannot match its own element. Pre-P5 the compound form matched the body and
resolved the org's choice. Restores it for the body-appended combo-box
dropdown, the worksheet details pane and the schedule task list.

The design's section 3 correction box claimed the post-split behaviour matched
the pre-split behaviour; it does not, and that claim is now struck through in
place."
```

---

## Task 2: The chart hook's `else` branch

Behaviour-neutral today, and what makes Revert complete for charts tomorrow. `ChartVSAssemblyInfo.seedChromeDefaults` is forward-only precisely because the two `PlotDescriptor` seed booleans did the reversing; once Task 6 deletes them the hook has to write both legacy values itself.

**Files:**
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/ChartVSAssemblyInfo.java:100-113`
- Test: `core/src/test/java/inetsoft/uql/viewsheet/internal/SeedChromeDefaultsTest.java`

**Interfaces:**
- Consumes: `VizContext` (`modern` field), `PlotDescriptor.setBarCornerRadius(double)`, `PlotDescriptor.setSmoothLines(boolean)`
- Produces: `ChartVSAssemblyInfo.seedChromeDefaults(VizContext)` now writes the plot values on **both** branches. Task 3's revert relies on this.

- [ ] **Step 1: Write the failing test**

Add to `SeedChromeDefaultsTest`:

```java
   @Test
   void chartPlotSeedsAreWrittenOnTheLegacyBranchToo() {
      // the hook has to be able to un-seed, not only seed: Revert calls it with an unmarked
      // context and expects the legacy values written rather than left alone
      ChartVSAssemblyInfo info = new ChartVSAssemblyInfo();
      info.initDefaultFormat();
      PlotDescriptor plot = info.getChartDescriptor().getPlotDescriptor();
      plot.setBarCornerRadius(0.4);
      plot.setSmoothLines(true);

      info.seedChromeDefaults(VizContext.LEGACY);

      assertEquals(0.0, plot.getBarCornerRadiusValue(), 0.0001,
                   "an unmarked context writes the legacy radius");
      assertFalse(plot.isSmoothLinesValue(), "and the legacy smoothing");
   }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd community && ./mvnw test -pl core -Dtest=SeedChromeDefaultsTest#chartPlotSeedsAreWrittenOnTheLegacyBranchToo`
Expected: FAIL — `expected: <0.0> but was: <0.4>`, because the hook has no false branch.

- [ ] **Step 3: Write the implementation**

In `ChartVSAssemblyInfo.java`, replace the body of `seedChromeDefaults`:

```java
   @Override
   protected void seedChromeDefaults(VizContext ctx) {
      super.seedChromeDefaults(ctx);
      getFormat().getDefaultFormat().setBackgroundValue(
         VSObjectChromeDefaults.cardBackgroundCss(ctx));
      PlotDescriptor plotDesc = getChartDescriptor().getPlotDescriptor();

      if(ctx.modern) {
         plotDesc.setBarCornerRadius(0.3);
         plotDesc.setModernCornerSeed(true);
         plotDesc.setSmoothLines(true);
         plotDesc.setModernSmoothSeed(true);
      }
      else {
         // Revert calls this with an unmarked context and needs the legacy values written, not
         // left alone. Value-identical at gate-off creation, where both fields already hold these.
         plotDesc.setBarCornerRadius(0);
         plotDesc.setSmoothLines(false);
      }
   }
```

- [ ] **Step 4: Run the test to verify it passes, then the whole file**

Run: `cd community && ./mvnw test -pl core -Dtest=SeedChromeDefaultsTest`
Expected: PASS, all tests in the class. The existing `:267-269` assertions (gate-off creation leaves `0.0` / `false`) still pass because the else branch writes exactly those values.

- [ ] **Step 5: Verify nothing else regressed on the creation path**

Run: `cd community && ./mvnw test -pl core -Dtest=ChartVSAssemblyInfoBarRoundingTest,VSChartChromeDefaultsTest,VizModernizeUtilTest`
Expected: PASS. `barRadiusNotSeededGateOff` and `smoothLinesNotSeededGateOff` are the two that would catch a mistake here.

- [ ] **Step 6: Commit**

```bash
cd community && git add core/src/main/java/inetsoft/uql/viewsheet/internal/ChartVSAssemblyInfo.java core/src/test/java/inetsoft/uql/viewsheet/internal/SeedChromeDefaultsTest.java
git commit -m "feat(viewsheet): give the chart chrome hook its legacy branch

seedChromeDefaults was forward-only because the two PlotDescriptor seed
booleans did the reversing. Revert calls the same hook with an unmarked
context and needs the legacy plot values written rather than left alone.

Behaviour-neutral: at gate-off creation both fields already hold 0 and false,
and both setters clear a seed boolean that is already false."
```

---

## Task 3: `VizModernizeUtil.revert()` and `hasMarked()`

The mirror of `modernize()`. Nothing calls it yet.

**Files:**
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/VizModernizeUtil.java`
- Test: `core/src/test/java/inetsoft/uql/viewsheet/internal/VizModernizeUtilTest.java`

**Interfaces:**
- Consumes: `VSAssemblyInfo.getVizMark()` / `setVizMark(VizMark)`, `VSAssemblyInfo.seedChromeDefaults(VizContext)` (package-visible — this is why the util lives in `inetsoft.uql.viewsheet.internal`), `VizContext.of(VizMark)`
- Produces: `public static int revert(Viewsheet vs)` returning the number of infos touched; `public static boolean hasMarked(Viewsheet vs)`. Task 4's service calls both.

- [ ] **Step 1: Write the failing tests**

Add to `VizModernizeUtilTest`:

```java
   @Test
   void aLegacySheetHasNothingToRevert() {
      assertFalse(VizModernizeUtil.hasMarked(legacySheet()));
   }

   @Test
   void aModernSheetHasSomethingToRevert() {
      gateOn();
      Viewsheet vs = new Viewsheet();
      vs.addAssembly(new TextVSAssembly(vs, "Text1"));
      assertTrue(VizModernizeUtil.hasMarked(vs));
   }

   @Test
   void revertClearsEveryMarkIncludingTheSheetsOwn() {
      gateOn();
      Viewsheet vs = new Viewsheet();
      vs.addAssembly(new TextVSAssembly(vs, "Text1"));
      vs.addAssembly(new TableVSAssembly(vs, "Table1"));

      assertEquals(3, VizModernizeUtil.revert(vs), "two assemblies plus the sheet itself");

      assertNull(vs.getVSAssemblyInfo().getVizMark());

      for(Assembly assembly : vs.getAssemblies(true)) {
         assertNull(((VSAssembly) assembly).getVSAssemblyInfo().getVizMark());
      }

      assertFalse(VizModernizeUtil.hasMarked(vs), "and there is nothing left to revert");
   }

   @Test
   void revertAlsoUnSeedsTheChrome() {
      gateOn();
      Viewsheet vs = new Viewsheet();
      TableVSAssembly table = new TableVSAssembly(vs, "Table1");
      vs.addAssembly(table);
      table.getVSAssemblyInfo().initDefaultFormat();
      assertEquals(VSObjectChromeDefaults.cardCornerRadius(),
                   table.getVSAssemblyInfo().getFormat().getDefaultFormat().getRoundCornerValue(),
                   "modern: rounded");

      VizModernizeUtil.revert(vs);

      assertEquals(0, table.getVSAssemblyInfo().getFormat().getDefaultFormat()
         .getRoundCornerValue(), "legacy: square");
   }

   @Test
   void revertIsOfferedAndWorksWithTheGateOff() {
      // no gate floor, unlike modernize(): reverting is offered under both gate states on purpose
      gateOn();
      Viewsheet vs = new Viewsheet();
      vs.addAssembly(new TextVSAssembly(vs, "Text1"));

      gateOff();

      assertTrue(VizModernizeUtil.hasMarked(vs));
      assertEquals(2, VizModernizeUtil.revert(vs));
      assertNull(vs.getVSAssemblyInfo().getVizMark());
   }

   @Test
   void revertIsIdempotent() {
      gateOn();
      Viewsheet vs = new Viewsheet();
      vs.addAssembly(new TextVSAssembly(vs, "Text1"));

      assertEquals(2, VizModernizeUtil.revert(vs));
      assertEquals(0, VizModernizeUtil.revert(vs), "a second run touches nothing");
   }

   @Test
   void revertLeavesUnmarkedSiblingsAlone() {
      // the mixed dashboard, backwards: reverting touches only what carries a mark
      Viewsheet vs = legacySheet();
      gateOn();
      TextVSAssembly modern = new TextVSAssembly(vs, "Text2");
      vs.addAssembly(modern);
      modern.getVSAssemblyInfo().setVizMark(VizMark.MODERN_LIGHT);

      assertEquals(1, VizModernizeUtil.revert(vs), "only the marked sibling");
      assertNull(modern.getVSAssemblyInfo().getVizMark());
   }

   @Test
   void revertReachesAnEmbeddedViewsheetContainerButNotItsChildren() {
      gateOn();
      Viewsheet vs = new Viewsheet();
      Viewsheet embedded = new Viewsheet();
      embedded.getVSAssemblyInfo().setName("EmbeddedVS");
      embedded.getVSAssemblyInfo().setVizMark(VizMark.MODERN_LIGHT);
      TextVSAssembly inner = new TextVSAssembly(embedded, "InnerText");
      embedded.addAssembly(inner);
      inner.getVSAssemblyInfo().setVizMark(VizMark.MODERN_LIGHT);
      vs.addAssembly(embedded);

      VizModernizeUtil.revert(vs);

      assertNull(embedded.getVSAssemblyInfo().getVizMark(),
                 "the container is this sheet's content");
      assertEquals(VizMark.MODERN_LIGHT, inner.getVSAssemblyInfo().getVizMark(),
                   "its children are the embedded asset's content and stay untouched");
   }

   @Test
   void revertNeverTouchesAuthorProvenanceFlags() {
      gateOn();
      Viewsheet vs = new Viewsheet();
      TableVSAssembly table = new TableVSAssembly(vs, "Table1");
      vs.addAssembly(table);
      TableVSAssemblyInfo info = (TableVSAssemblyInfo) table.getVSAssemblyInfo();
      info.setTitleHeightValue(44);
      info.setUserTitleHeight(true);

      VizModernizeUtil.revert(vs);

      assertTrue(info.isUserTitleHeight(), "userTitleHeight records an author's choice, not a mark");
      assertEquals(44, info.getTitleHeightValue(), "and the height they chose survives");
   }

   @Test
   void aModernizedThenRevertedSheetMatchesOneNeverModernized() {
      // the phase's headline property, and the reason reversal is routed through the
      // creation path: there is no separate reverser whose behaviour could drift
      Viewsheet reverted = legacySheet();
      TableVSAssembly a = (TableVSAssembly) reverted.getAssembly("Table1");
      a.getVSAssemblyInfo().initDefaultFormat();
      gateOn();
      VizModernizeUtil.modernize(reverted);
      VizModernizeUtil.revert(reverted);

      Viewsheet untouched = legacySheet();
      TableVSAssembly b = (TableVSAssembly) untouched.getAssembly("Table1");
      b.getVSAssemblyInfo().initDefaultFormat();

      VSFormat af = a.getVSAssemblyInfo().getFormat().getDefaultFormat();
      VSFormat bf = b.getVSAssemblyInfo().getFormat().getDefaultFormat();
      assertEquals(bf.getRoundCornerValue(), af.getRoundCornerValue());
      assertEquals(bf.getBorderColorsValue().topColor, af.getBorderColorsValue().topColor);
      assertEquals(bf.getBackgroundValue(), af.getBackgroundValue());
   }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd community && ./mvnw test -pl core -Dtest=VizModernizeUtilTest`
Expected: compilation failure — `cannot find symbol: method revert(Viewsheet)` and `hasMarked(Viewsheet)`.

- [ ] **Step 3: Write the implementation**

In `VizModernizeUtil.java`, add the import:

```java
import java.util.function.Predicate;
```

Add the two public methods after `modernize()`:

```java
   /**
    * Whether this sheet holds anything Revert would act on: the sheet's own info, or any assembly
    * of its own, carrying a mark.
    *
    * No gate term, unlike the modernizable flag: Revert is offered under both gate states on purpose,
    * so an author in a modern org can keep one dashboard classic.
    */
   public static boolean hasMarked(Viewsheet vs) {
      return !marked(vs).isEmpty();
   }

   /**
    * Clear the mark on every marked assembly, and on the sheet itself, then re-seed each through
    * the same hook creation uses. Returns how many were touched.
    *
    * No reverser is written: with the mark cleared, seedChromeDefaults writes the legacy branch of
    * every ternary, which is the identical call a gate-off creation makes. A property added to what
    * Modernize does is therefore reverted by the same edit to the same method, or it is not added.
    *
    * No gate floor, unlike modernize(): modernizing into a closed gate produces content that
    * changes appearance the moment the gate opens, and clearing a mark has no such hazard.
    */
   public static int revert(Viewsheet vs) {
      List<VSAssemblyInfo> targets = marked(vs);
      // every target is unmarked by the time it is seeded, so one context serves them all; the
      // cast picks the VizMark overload rather than the VSAssemblyInfo one
      VizContext ctx = VizContext.of((VizMark) null);

      for(VSAssemblyInfo info : targets) {
         info.setVizMark(null);
         info.seedChromeDefaults(ctx);
      }

      return targets.size();
   }
```

Then replace the private `unmarked()` and `addIfUnmarked()` with one shared traversal:

```java
   /** The unmarked half of the sheet's own content. Modernize's targets. */
   private static List<VSAssemblyInfo> unmarked(Viewsheet vs) {
      return collect(vs, info -> info.getVizMark() == null);
   }

   /** The marked half of the sheet's own content. Revert's targets — the same traversal inverted. */
   private static List<VSAssemblyInfo> marked(Viewsheet vs) {
      return collect(vs, info -> info.getVizMark() != null);
   }

   /**
    * The sheet's own info, every matching assembly of its own, and every matching
    * embedded-viewsheet container of its own. Assemblies belonging to an embedded viewsheet are
    * skipped: they are another asset's content, and this sheet has no business writing to them.
    */
   private static List<VSAssemblyInfo> collect(Viewsheet vs, Predicate<VSAssemblyInfo> test) {
      List<VSAssemblyInfo> targets = new ArrayList<>();

      addIf(targets, vs.getVSAssemblyInfo(), test);

      for(Assembly assembly : vs.getAssemblies(true)) {
         if(!(assembly instanceof VSAssembly)) {
            continue;
         }

         VSAssemblyInfo info = ((VSAssembly) assembly).getVSAssemblyInfo();

         if(info != null && !info.isEmbedded()) {
            addIf(targets, info, test);
         }
      }

      // getAssemblies(true) flattens embedded-viewsheet containers away: the collector recurses into
      // a Viewsheet-typed child and never adds it (Viewsheet.java:3246-3263). The containers are this
      // sheet's own content, so collect them from the direct children; their children belong to the
      // embedded asset and stay excluded by the isEmbedded() test above.
      for(Assembly assembly : vs.getAssemblies(false)) {
         if(assembly instanceof Viewsheet) {
            addIf(targets, ((Viewsheet) assembly).getVSAssemblyInfo(), test);
         }
      }

      return targets;
   }

   private static void addIf(List<VSAssemblyInfo> targets, VSAssemblyInfo info,
                             Predicate<VSAssemblyInfo> test)
   {
      if(info != null && test.test(info)) {
         targets.add(info);
      }
   }
```

Update the class javadoc's first line to cover both directions:

```java
/**
 * Modernize and Revert: move a dashboard's own content between the classic and modern chrome a
 * freshly created dashboard would have. Both run through seedChromeDefaults, which is why they
 * cannot drift apart, and both live in this package because that method is protected.
 *
 * Nothing here is automatic - unmarked content is never modernized and marked content is never
 * reverted unless somebody asks. A mixed dashboard stays mixed either way.
 */
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd community && ./mvnw test -pl core -Dtest=VizModernizeUtilTest`
Expected: PASS — the ten new tests plus all fifteen pre-existing ones, since `unmarked()` now routes through `collect()` and must behave identically.

- [ ] **Step 5: Commit**

```bash
cd community && git add core/src/main/java/inetsoft/uql/viewsheet/internal/VizModernizeUtil.java core/src/test/java/inetsoft/uql/viewsheet/internal/VizModernizeUtilTest.java
git commit -m "feat(viewsheet): revert a dashboard's marked content through the creation path

The mirror of modernize(): the same traversal with the predicate inverted,
clearing each mark and then calling seedChromeDefaults with an unmarked
context - the identical call a gate-off creation makes. No reverser is
authored, so the two directions cannot drift apart.

No gate floor: decision 13 offers Revert under both gate states, so an author
in a modern org can keep one dashboard classic.

The two enumerations collapse into one collect(vs, predicate), so forward and
backward genuinely share the traversal, embedded-container pass included.

Nothing calls it yet."
```

---

## Task 4: The endpoint, the service and the `revertable` flag

**Files:**
- Create: `core/src/main/java/inetsoft/web/composer/vs/controller/RevertViewsheetController.java`
- Create: `core/src/main/java/inetsoft/web/composer/vs/controller/RevertViewsheetService.java`
- Modify: `core/src/main/java/inetsoft/web/viewsheet/service/CoreLifecycleService.java:312-316`
- Test: `core/src/test/java/inetsoft/web/composer/vs/controller/RevertViewsheetServiceTest.java` (create)

**Interfaces:**
- Consumes: `VizModernizeUtil.revert(Viewsheet)`, `VizModernizeUtil.hasMarked(Viewsheet)` from Task 3
- Produces: STOMP endpoint `/events/composer/viewsheet/revert`; `RevertViewsheetService.revert(String runtimeId, Principal, CommandDispatcher, String linkUri)` returning `Void`; the `"revertable"` boolean key on `SetViewsheetInfoCommand`'s `infoMap`. Task 5's client reads that key and sends to that endpoint.

- [ ] **Step 1: Write the failing service tests**

Create `core/src/test/java/inetsoft/web/composer/vs/controller/RevertViewsheetServiceTest.java`. Copy the licence header from `ModernizeViewsheetServiceTest.java` verbatim, then:

```java
package inetsoft.web.composer.vs.controller;

import inetsoft.analytic.composition.ViewsheetEngine;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.sree.SreeEnv;
import inetsoft.test.*;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.VizMark;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.CoreLifecycleService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@Tag("core")
class RevertViewsheetServiceTest {
   @BeforeEach
   void setup() throws Exception {
      service = new RevertViewsheetService(viewsheetEngine, coreLifecycleService, assetRepository);
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      viewsheet = new Viewsheet();
      viewsheet.getVSAssemblyInfo().setVizMark(VizMark.MODERN_LIGHT);
      viewsheet.addAssembly(new TextVSAssembly(viewsheet, "Text1"));
      viewsheet.getAssembly("Text1").getVSAssemblyInfo().setVizMark(VizMark.MODERN_LIGHT);

      when(viewsheetEngine.getViewsheet(anyString(), nullable(Principal.class))).thenReturn(rvs);
      when(rvs.getViewsheet()).thenReturn(viewsheet);
      when(rvs.getEntry()).thenReturn(entry);
      when(rvs.getID()).thenReturn("rid");
   }

   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
   }

   @Test
   void revertClearsAndRefreshes() throws Exception {
      service.revert("rid", principal, dispatcher, "uri");

      assertNull(viewsheet.getVSAssemblyInfo().getVizMark());
      assertNull(viewsheet.getAssembly("Text1").getVSAssemblyInfo().getVizMark());
      verify(assetRepository).checkAssetPermission(eq(principal), eq(entry), any());
      verify(coreLifecycleService).setViewsheetInfo(eq(rvs), eq("uri"), eq(dispatcher));
      verify(coreLifecycleService).refreshViewsheet(eq(rvs), eq("rid"), eq("uri"), eq(dispatcher),
                                                   eq(false), eq(false), eq(true), any());
   }

   @Test
   void revertStillWorksWithTheGateOff() throws Exception {
      // the one behavioural difference from Modernize: no gate floor
      SreeEnv.setProperty("viewsheet.modernVisualization", "false");

      service.revert("rid", principal, dispatcher, "uri");

      assertNull(viewsheet.getVSAssemblyInfo().getVizMark());
      verify(coreLifecycleService).refreshViewsheet(eq(rvs), eq("rid"), eq("uri"), eq(dispatcher),
                                                   eq(false), eq(false), eq(true), any());
   }

   @Test
   void revertRefusesWithoutWritePermission() throws Exception {
      doThrow(new SecurityException("denied"))
         .when(assetRepository).checkAssetPermission(any(), any(), any());

      assertThrows(SecurityException.class,
                   () -> service.revert("rid", principal, dispatcher, "uri"));
      assertEquals(VizMark.MODERN_LIGHT, viewsheet.getVSAssemblyInfo().getVizMark(),
                   "the permission check precedes any write");
   }

   @Test
   void revertDispatchesViewsheetInfoOnPermissionDenial() throws Exception {
      doThrow(new SecurityException("denied"))
         .when(assetRepository).checkAssetPermission(any(), any(), any());

      assertThrows(SecurityException.class,
                   () -> service.revert("rid", principal, dispatcher, "uri"));
      verify(coreLifecycleService).setViewsheetInfo(eq(rvs), eq("uri"), eq(dispatcher));
   }

   @Test
   void revertDispatchesViewsheetInfoOnNothingToDo() throws Exception {
      viewsheet.getVSAssemblyInfo().setVizMark(null);
      viewsheet.getAssembly("Text1").getVSAssemblyInfo().setVizMark(null);

      service.revert("rid", principal, dispatcher, "uri");

      verify(coreLifecycleService).setViewsheetInfo(eq(rvs), eq("uri"), eq(dispatcher));
      verify(coreLifecycleService, never())
         .refreshViewsheet(any(), anyString(), anyString(), any(), anyBoolean(), anyBoolean(),
                           anyBoolean(), any());
   }

   private RevertViewsheetService service;
   private Viewsheet viewsheet;
   @Mock private ViewsheetEngine viewsheetEngine;
   @Mock private CoreLifecycleService coreLifecycleService;
   @Mock private AssetRepository assetRepository;
   @Mock private RuntimeViewsheet rvs;
   @Mock private AssetEntry entry;
   @Mock private Principal principal;
   @Mock private CommandDispatcher dispatcher;
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `cd community && ./mvnw test -pl core -Dtest=RevertViewsheetServiceTest`
Expected: compilation failure — `cannot find symbol: class RevertViewsheetService`.

- [ ] **Step 3: Write the service**

Create `core/src/main/java/inetsoft/web/composer/vs/controller/RevertViewsheetService.java` with the licence header copied verbatim from `ModernizeViewsheetService.java`, then:

```java
package inetsoft.web.composer.vs.controller;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.cluster.*;
import inetsoft.report.composition.ChangedAssemblyList;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.WorksheetEngine;
import inetsoft.sree.security.ResourceAction;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.VizModernizeUtil;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.CoreLifecycleService;
import org.springframework.stereotype.Service;

import java.security.Principal;

/**
 * Revert the focused dashboard: clear the mark on its marked assemblies and give them back the
 * chrome a dashboard created without the gate would have. Composer only, write permission required,
 * and offered under both gate states - unlike Modernize, which needs an open gate.
 */
@Service
@ClusterProxy
public class RevertViewsheetService {
   public RevertViewsheetService(ViewsheetService viewsheetService,
                                 CoreLifecycleService coreLifecycleService,
                                 AssetRepository assetRepository)
   {
      this.viewsheetService = viewsheetService;
      this.coreLifecycleService = coreLifecycleService;
      this.assetRepository = assetRepository;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   @ClusterWriteMethod
   public Void revert(@ClusterProxyKey String runtimeId, Principal principal,
                      CommandDispatcher dispatcher, String linkUri)
      throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, principal);

      try {
         // scope-aware: a private dashboard is its owner's, a global one needs the grant
         assetRepository.checkAssetPermission(principal, rvs.getEntry(), ResourceAction.WRITE);
         Viewsheet vs = rvs.getViewsheet();

         if(vs == null || VizModernizeUtil.revert(vs) == 0) {
            // nothing changed, but the client already cleared its revertable flag when it sent the
            // event; dispatch so it recomputes and the menu entry doesn't stay hidden
            coreLifecycleService.setViewsheetInfo(rvs, linkUri, dispatcher);
            return null;
         }

         ChangedAssemblyList clist =
            coreLifecycleService.createList(false, dispatcher, rvs, linkUri);
         coreLifecycleService.setViewsheetInfo(rvs, linkUri, dispatcher);
         coreLifecycleService.refreshViewsheet(rvs, rvs.getID(), linkUri, dispatcher, false, false,
                                               true, clist);
         return null;
      }
      catch(Exception ex) {
         // the client cleared the revertable flag optimistically when it sent the event; on failure
         // (e.g. permission denial) dispatch the recomputed flag so the entry comes back for the
         // rest of the session, then rethrow so the failure still propagates
         coreLifecycleService.setViewsheetInfo(rvs, linkUri, dispatcher);
         throw ex;
      }
   }

   private final ViewsheetService viewsheetService;
   private final CoreLifecycleService coreLifecycleService;
   private final AssetRepository assetRepository;
}
```

- [ ] **Step 4: Write the controller**

Create `core/src/main/java/inetsoft/web/composer/vs/controller/RevertViewsheetController.java` with the licence header copied verbatim from `ModernizeViewsheetController.java`, then:

```java
package inetsoft.web.composer.vs.controller;

import inetsoft.web.viewsheet.*;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.LinkUri;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

import java.security.Principal;

/**
 * Revert the focused dashboard. One undo step, the same as Modernize - @Undoable snapshots after
 * the action returns, so Ctrl+Z restores the marked state and the modern chrome with it. The
 * confirmation lives on the client, because the undo step is available but far less likely to be
 * reached for calmly after a destructive-looking change.
 */
@Controller
public class RevertViewsheetController {
   @Autowired
   public RevertViewsheetController(RuntimeViewsheetRef runtimeViewsheetRef,
                                    RevertViewsheetServiceProxy revertViewsheetService)
   {
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.revertViewsheetService = revertViewsheetService;
   }

   @Undoable
   @LoadingMask
   @HandleAssetExceptions
   @MessageMapping("composer/viewsheet/revert")
   public void revert(Principal principal, CommandDispatcher commandDispatcher,
                      @LinkUri String linkUri)
      throws Exception
   {
      revertViewsheetService.revert(runtimeViewsheetRef.getRuntimeId(), principal,
                                    commandDispatcher, linkUri);
   }

   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private final RevertViewsheetServiceProxy revertViewsheetService;
}
```

`RevertViewsheetServiceProxy` does not exist as a source file — the `@ClusterProxy` annotation processor generates it into `core/target/generated-sources/annotations/inetsoft/web/composer/vs/controller/`. The first compile after Step 3 creates it; if the controller reports the symbol as missing, run `./mvnw compile -pl core` once and retry.

- [ ] **Step 5: Add the `revertable` flag**

In `CoreLifecycleService.java`, immediately after the `modernizable` put at `:314-315`:

```java
         // recomputed on every refresh, so the composer's Modernize affordance disappears when the
         // action completes and returns if the user undoes it
         infoMap.put("modernizable",
                     VSDensityDefaults.isModern() && VizModernizeUtil.hasUnmarked(vs));
         // no gate term, unlike modernizable: Revert is offered under both gate states on purpose, so
         // an author in a modern org can keep one dashboard classic
         infoMap.put("revertable", VizModernizeUtil.hasMarked(vs));
```

- [ ] **Step 6: Run the tests to verify they pass**

Run: `cd community && ./mvnw test -pl core -Dtest=RevertViewsheetServiceTest,ModernizeViewsheetServiceTest`
Expected: PASS — five new tests and the five existing Modernize ones.

- [ ] **Step 7: Commit**

```bash
cd community && git add core/src/main/java/inetsoft/web/composer/vs/controller/RevertViewsheetController.java core/src/main/java/inetsoft/web/composer/vs/controller/RevertViewsheetService.java core/src/main/java/inetsoft/web/viewsheet/service/CoreLifecycleService.java core/src/test/java/inetsoft/web/composer/vs/controller/RevertViewsheetServiceTest.java
git commit -m "feat(composer): endpoint and revertable flag for Revert

Mirrors the Modernize pair, with one deliberate difference: no gate floor. The
revertable flag is 'this sheet has marked content' with no isModern() term,
because clearing a mark cannot produce content that changes when the gate
moves - which is the whole of decision 13.

@Undoable gives it the same one-step undo Modernize has."
```

---

## Task 5: The composer affordance — menu entry, confirmation, session suppression

Menu entry only, no bar: P3's bar exists because Modernize is an offer the product makes, and Revert is a request the author makes.

**Files:**
- Modify: `web/projects/portal/src/app/composer/data/vs/viewsheet.ts:54-56`
- Modify: `web/projects/portal/src/app/composer/gui/vs/editor/viewsheet-pane.component.ts` — `:416-423` (menu), `:820` (flag read), `:1708-1713` (beside `modernize()`)
- Modify: `core/src/main/resources/inetsoft/util/srinter.properties` — after `composer.vs.parameters` (`:3660`)
- Test: `web/projects/portal/src/app/composer/gui/vs/editor/viewsheet-pane.component.interaction.tl.spec.ts`

**Interfaces:**
- Consumes: the `"revertable"` infoMap key and the `/events/composer/viewsheet/revert` endpoint from Task 4; the existing private `confirm(text: string): Promise<boolean>` helper at `viewsheet-pane.component.ts:1447`
- Produces: `Viewsheet.revertable: boolean`; `ViewsheetPaneComponent.revert(): void`; the menu action id `"composer vspane revert"`

- [ ] **Step 1: Write the failing tests**

Add to `viewsheet-pane.component.interaction.tl.spec.ts`, following the idiom of the existing modernize tests at `:259-297`:

```typescript
   it("should offer Revert in the canvas menu only where there is marked content", async () => {
      const { comp } = await createPane();
      comp.vs.revertable = true;

      const entry = comp.getCanvasActions()
         .find(action => action.id() === "composer vspane revert");
      expect(entry.visible()).toBe(true);

      comp.vs.revertable = false;
      expect(comp.getCanvasActions()
         .find(action => action.id() === "composer vspane revert").visible()).toBe(false);
   });

   it("should not send the revert event when the confirmation is declined", async () => {
      const { comp } = await createPane();
      comp.vs.revertable = true;
      const send = vi.spyOn(comp.vs.socketConnection, "sendEvent");
      vi.spyOn<any, any>(comp, "confirm").mockResolvedValue(false);

      comp.revert();
      await Promise.resolve();

      expect(send).not.toHaveBeenCalled();
      expect(comp.vs.revertable).toBe(true);
   });

   it("should suppress the Modernize bar for the session once a sheet is reverted", async () => {
      const { comp } = await createPane();
      comp.vs.revertable = true;
      comp.vs.modernizeBarDismissed = false;
      const send = vi.spyOn(comp.vs.socketConnection, "sendEvent");
      vi.spyOn<any, any>(comp, "confirm").mockResolvedValue(true);

      comp.revert();
      await Promise.resolve();

      expect(send).toHaveBeenCalledWith("/events/composer/viewsheet/revert");
      expect(comp.vs.revertable).toBe(false);
      // modernizable is recomputed on every refresh, so a reverted sheet re-arms the bar
      // immediately; reusing the per-session dismissal is what stops it offering to undo this
      expect(comp.vs.modernizeBarDismissed).toBe(true);
   });
```

Read the existing tests at `:259-297` first and reuse whatever helper they use to build the component (named `createPane()` above as a placeholder — use the real one) and to reach the canvas actions (named `getCanvasActions()` above — use the real accessor). Do not introduce a new harness.

- [ ] **Step 2: Run to verify they fail**

Run: `cd community/web && npx ng run portal:test-tl --include=projects/portal/src/app/composer/gui/vs/editor/viewsheet-pane.component.interaction.tl.spec.ts`
Expected: FAIL — `comp.vs.revertable` is not a property, and `comp.revert` is not a function.

- [ ] **Step 3: Add the model field**

In `viewsheet.ts`, after `modernizeBarDismissed` at `:56`:

```typescript
   /** Server-computed: this sheet holds marked content. No gate term, unlike modernizable. */
   revertable: boolean = false;
```

- [ ] **Step 4: Read the flag and add the action**

In `viewsheet-pane.component.ts`, beside the `modernizable` read at `:820`:

```typescript
      this.vs.modernizable = !!command.info["modernizable"];
      this.vs.revertable = !!command.info["revertable"];
```

Add the menu entry immediately after the modernize entry (which ends at `:423`):

```typescript
         {
            id: () => "composer vspane revert",
            label: () => "_#(js:composer.vs.revert.menu)",
            icon: () => "reset-icon",
            enabled: () => true,
            visible: () => this.vs.revertable && !this.deployed,
            action: () => this.revert()
         },
```

Add the method immediately after `modernize()` (which ends at `:1713`):

```typescript
   revert(): void {
      this.confirm("_#(js:composer.vs.revert.confirm)").then((ok: boolean) => {
         if(!ok) {
            return;
         }

         // hide the entry at once, for the reason modernize() clears its own flag
         this.vs.revertable = false;
         // modernizable is recomputed on every refresh, so a reverted sheet qualifies again the
         // instant the refresh lands. Reuse the per-session dismissal rather than let the bar
         // offer to undo what was just done.
         this.vs.modernizeBarDismissed = true;
         this.vs.socketConnection.sendEvent("/events/composer/viewsheet/revert");
      });
   }
```

- [ ] **Step 5: Add the two strings**

In `srinter.properties`, between `composer.vs.parameters` (`:3660`) and `composer.vs.scriptHelp`:

```properties
composer.vs.revert.confirm=Revert this dashboard to the classic look? Components using the modern look will go back to classic defaults, including card corners, borders and chart bar rounding. This can be undone with one undo step.
composer.vs.revert.menu=Revert Dashboard to Classic Look
```

- [ ] **Step 6: Run the tests to verify they pass**

Run: `cd community/web && npx ng run portal:test-tl --include=projects/portal/src/app/composer/gui/vs/editor/viewsheet-pane.component.interaction.tl.spec.ts`
Expected: PASS — the three new tests plus the existing modernize ones in the same file.

- [ ] **Step 7: Check the action-suite did not regress**

Run: `cd community/web && npx ng test portal --include="**/assembly-context-menu-items.spec.ts"`
Expected: PASS. A new canvas action changes the menu's contents, and this is the spec that enumerates them.

- [ ] **Step 8: Commit**

```bash
cd community && git add web/projects/portal/src/app/composer/data/vs/viewsheet.ts web/projects/portal/src/app/composer/gui/vs/editor/viewsheet-pane.component.ts web/projects/portal/src/app/composer/gui/vs/editor/viewsheet-pane.component.interaction.tl.spec.ts core/src/main/resources/inetsoft/util/srinter.properties
git commit -m "feat(composer): a Revert action in the canvas menu

Menu entry only - P3's bar exists because Modernize is an offer the product
makes, and Revert is a request the author makes - plus a confirmation, because
it discards chrome an author may have been working against for months.

Reverting also sets modernizeBarDismissed. modernizable is recomputed on every
refresh, so a reverted sheet re-arms the bar immediately and it would offer to
undo what was just done; reusing the existing per-session dismissal avoids a
second suppression rule with its own lifetime. The menu entry is deliberately
unaffected and keeps recomputing.

Revert now works end to end. Nothing renders differently yet."
```

---

## Task 6: The behaviour reversal — four deletions in one commit

**This is the phase.** All four deletions land together; every partial state is worse than either end. The shells' density guard comes with them, because after this commit gate-off no longer means legacy and a marked assembly in a gate-off org would otherwise find no density class on the body.

**Files:**
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/VizContext.java:59-68`
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/graph/PlotDescriptor.java` — `:629-660`, `:1316-1339`, `:1534-1535`, `:1580-1583`, `:1721-1722`, `:1742-1744`, `:1876-1877`, `:1902-1903`, `:1994-2009`
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/ChartVSAssemblyInfo.java:106-118`
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/VSObjectChromeDefaults.java:62-71`
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/VSCompositeFormat.java:322-337`
- Modify: `core/src/main/java/inetsoft/web/binding/controller/ChangeChartTypeService.java:336-345`
- Modify: `core/src/main/java/inetsoft/web/graph/model/dialog/ChartPlotOptionsPaneModel.java:224-230`
- Modify: `web/projects/portal/src/app/portal/app.component.ts:266-281`, `web/projects/portal/src/app/composer/app.component.ts:139-154`, `web/projects/portal/src/app/vsobjects/viewer-app.component.ts:2788-2801`
- Test: `VizContextTest`, `VizContextReadFlipTest`, `ChartVSAssemblyInfoBarRoundingTest`, `VSCompositeFormatRoundCornerGateTest`, `VSObjectChromeDefaultsTest`, `PlotDescriptorXmlTest`, `ChartVSAScriptableTest`, `ChangeChartTypeServiceSmoothLinesTransitionTest`, `ChartPlotOptionsPaneModelTest`, `VSWizardBindingHandlerSmoothLinesTest`, `SeedChromeDefaultsTest`

**Interfaces:**
- Consumes: nothing new
- Produces: `VizContext.of(VizMark)` where `modern == (mark != null)`; `PlotDescriptor.isSmoothLines()` / `getBarCornerRadius()` as plain accessors, with `isSmoothLinesValue()` / `getBarCornerRadiusValue()` **removed**; `VSCompositeFormat.getRoundCornerValue()` returning the raw DEFAULT-tier radius; `VSObjectChromeDefaults.resolveSeededCorner` **removed**

- [ ] **Step 1: Invert the five tests that pin the old behaviour**

These five are the phase's assertion. Write them first, all five, before touching production code.

In `VizContextTest`, replace `ofAMarkIsLegacyWhenTheGateIsOff` (`:96-104`) with:

```java
   @Test
   void ofAMarkIsModernEvenWhenTheGateIsOff() {
      // the gate is a creation-time switch only: it decides what a new assembly is stamped with,
      // never how a stamped one renders
      SreeEnv.setProperty("viewsheet.modernVisualization", "false");
      assertTrue(VizContext.of(VizMark.MODERN_LIGHT).modern,
                 "a mark alone makes it modern - the gate && term is gone");
   }
```

In `VizContextReadFlipTest`, replace `closingTheGateStillRevertsAMarkedAssembly` with:

```java
   @Test
   void closingTheGateRevertsNothing() {
      TableVSAssemblyInfo marked = markedTable();
      SreeEnv.setProperty("viewsheet.modernVisualization", "false");
      assertTrue(VizContext.of(marked).modern,
                 "closing the gate reverts nothing; Revert is the only route back");
   }
```

In `ChartVSAssemblyInfoBarRoundingTest`, replace `seededBarRadiusRevertsWhenGateTurnedOff` and `seededSmoothLinesRevertsWhenGateTurnedOff` with:

```java
   @Test
   void seededBarRadiusSurvivesTheGateTurningOff() {
      PlotDescriptor[] holder = new PlotDescriptor[1];
      withGate("true", () -> holder[0] = newChartPlot());

      withGate("false", () -> assertEquals(0.3, holder[0].getBarCornerRadius(), 1e-9,
                                           "a chart made under the gate stays rounded; Revert is "
                                              + "the only route back to square"));
   }

   @Test
   void seededSmoothLinesSurviveTheGateTurningOff() {
      PlotDescriptor[] holder = new PlotDescriptor[1];
      withGate("true", () -> holder[0] = newChartPlot());

      withGate("false", () -> assertTrue(holder[0].isSmoothLines(),
                                         "a chart made under the gate stays smooth"));
   }
```

and drop the `isModernCornerSeed` / `isModernSmoothSeed` assertion lines from `barRadiusSeededUnderGate` (`:68`), `barRadiusNotSeededGateOff` (`:77`), `smoothLinesSeededUnderGate` (`:113`) and `smoothLinesNotSeededGateOff` (`:122`), leaving the value assertions.

In `VSCompositeFormatRoundCornerGateTest`, replace `defaultTierSeedStrippedGateOff` (`:71-78`) with:

```java
   @Test
   void defaultTierSeedSurvivesGateOff() {
      withGate("false", () -> {
         VSCompositeFormat fmt = withDefaultTierRadius(12);
         assertEquals(12, fmt.getRoundCorner(),
                      "a seeded card keeps its radius; the gate no longer strips it");
         assertEquals(12, fmt.getRoundCornerValue());
      });
   }
```

- [ ] **Step 2: Run the five to verify they fail**

Run:

```bash
cd community && ./mvnw test -pl core -Dtest=VizContextTest,VizContextReadFlipTest,ChartVSAssemblyInfoBarRoundingTest,VSCompositeFormatRoundCornerGateTest
```

Expected: FAIL — five failures, each asserting the value the old mechanism strips. If any of the five *passes* at this point, stop: the mechanism you thought you were deleting is not the one in force.

- [ ] **Step 3: Delete the `gate &&` term**

In `VizContext.java`, replace `of(VizMark)` (`:59-68`):

```java
   /**
    * The context a mark implies. The org gate does not appear: it is a creation-time switch that
    * decides what a new assembly is stamped with, and the mark decides how a stamped one renders.
    * Closing the gate therefore reverts nothing - the per-dashboard Revert action is the only route
    * back. Density still comes from the org: the mark decides whether an assembly honours density,
    * not which density is in force.
    */
   public static VizContext of(VizMark mark) {
      boolean modern = mark != null;
      return new VizContext(modern, modern && mark == VizMark.MODERN_DARK, VSDensityDefaults.mode());
   }
```

- [ ] **Step 4: Delete both `PlotDescriptor` seed booleans**

In `PlotDescriptor.java`:

Replace the smooth-lines block (`:629-660`) with:

```java
   /**
    * Check if line segments connecting points should be drawn as smooth curves.
    */
   public boolean isSmoothLines() {
      return smoothLines;
   }

   /**
    * Set if line segments connecting points should be drawn as smooth curves.
    */
   public void setSmoothLines(boolean smoothLines) {
      this.smoothLines = smoothLines;
   }
```

Replace the bar-radius block (`:1316-1339`) with:

```java
   /** Bar corner radius, 0 to 0.5 as a fraction of bar width. */
   public double getBarCornerRadius() {
      return barCornerRadius;
   }

   public void setBarCornerRadius(double barCornerRadius) {
      this.barCornerRadius = Math.max(0, Math.min(0.5, barCornerRadius));
   }
```

Both `isSmoothLinesValue()` and `getBarCornerRadiusValue()` are deleted with them: they existed only to read past the gate check, they have **no production callers anywhere in the repo** (verified by grep across `community/` and every enterprise module), and the plain getters now return exactly what they returned.

Delete `modernSmoothSeed = "true".equals(Tool.getAttribute(node, "modernSmoothSeed"));` (`:1535`) and `modernCornerSeed = "true".equals(Tool.getAttribute(node, "modernCornerSeed"));` (`:1583`).

Delete the two writer lines (`:1722`, `:1744`).

Delete the two `equals` terms (`:1877`, `:1903`).

Delete both fields and their comment blocks (`:1997-2000`, `:2006-2009`). Keep `barCornerRadius = DEFAULT_BAR_CORNER_RADIUS` (`:2005`) and `smoothLines = false` (`:1996`) with their own comments.

- [ ] **Step 5: Drop the two seed writes from the chart hook**

In `ChartVSAssemblyInfo.java`, the `if(ctx.modern)` block loses its two `setModern*Seed` lines:

```java
      if(ctx.modern) {
         plotDesc.setBarCornerRadius(0.3);
         plotDesc.setSmoothLines(true);
      }
      else {
         // Revert calls this with an unmarked context and needs the legacy values written, not
         // left alone. Value-identical at gate-off creation, where both fields already hold these.
         plotDesc.setBarCornerRadius(0);
         plotDesc.setSmoothLines(false);
      }
```

- [ ] **Step 6: Retire `resolveSeededCorner`**

In `VSObjectChromeDefaults.java`, delete `resolveSeededCorner` and its javadoc (`:62-71`).

In `VSCompositeFormat.java`, replace `getRoundCornerValue()` and delete `resolveDefaultTierCorner` (`:322-337`):

```java
   /**
    * Get roundCorner value.
    */
   @Override
   public int getRoundCornerValue() {
      return userfmt.isRoundCornerValueDefined() ? userfmt.getRoundCornerValue() :
         cssfmt.isRoundCornerValueDefined() ? cssfmt.getRoundCornerValue() :
         deffmt.getRoundCornerValue();
   }
```

The TAB carve-out goes with it: it existed only because `FormatInfo.copyDefaultFormat` copies a composite-resolved radius onto a tab's DEFAULT tier, where the strip would have caught a laundered user radius. With no strip there is nothing to carve out of.

- [ ] **Step 7: Fix the two seed-boolean consumers**

In `ChangeChartTypeService.java`, the `newIsLine` branch (`:337-345`) loses one line:

```java
      else if(newIsLine) {
         if(modern && !oldIsLine) {
            // under the gate a line chart is smooth; set rather than preserve, since a saved chart
            // switched to Line carries no marker
            plotDesc.setSmoothLines(true);
         }
         else if(oldIsArea || oldIsCircular) {
            plotDesc.setSmoothLines(false);
         }
      }
```

In `ChartPlotOptionsPaneModel.java` (`:224-230`), the guard existed only to protect the seed flag, so it goes:

```java
      plotDesc.setBarCornerRadius(barCornerRadius != null ? barCornerRadius : 0);
```

- [ ] **Step 8: Always add the density class**

In all three shells, drop the `if(modern)` guard so `viz-density-<mode>` is present regardless of the gate. After this commit a marked assembly in a gate-off org is modern, and without a density ancestor on the body it would fall back to the bare `.viz-modern` dense tier at every org density.

**This reaches TypeScript as well as CSS, and it is the reason the change matters more than a token fallback.** `GuiTool.vizDensityMode()` (`gui-tool.ts:84-89`) reads the body class directly and returns `"dense"` when none is present, so `isVizDensityAtLeastCompact()` (`:96`) answers `false` for a whole gate-off org today. `isAnchoredResident()` and `isAnchoredChromeSuppressed()` (`mini-toolbar.service.ts:74`, `:90`) both consume it — which means without this step a marked assembly in a gate-off org would take **dense** anchored-strip behaviour whatever density the org chose: no strip at compact or comfortable, and the suppression branch instead.

**And it cannot leak into legacy content, which is what makes it safe.** Both predicates read `vizModern && GuiTool.isVizDensityAtLeastCompact()`, and `vizModern` is the per-assembly flag P5 put on the model. An unmarked assembly has it `false`, so adding a density class to the body of a gate-off org changes nothing for legacy content — verify this by reading those two predicates before making the change, because if either ever drops its `vizModern` term this step becomes a regression rather than a fix.

Related but out of scope: `b371c6aa8` ("stop the modern strip drawing controls that do nothing") changed `showToolbarContainer` so residency no longer decides whether the container's emptiness is checked, and left a proposed follow-on at `plans/2026-08-20-flatten-kebab-under-the-gate.md`. It touches none of P6's files, but it is the commit that made dense's empty-pill branch correct, so a strip anomaly seen while verifying this step should be checked against that plan before being blamed on P6.

`portal/app.component.ts:266-281`:

```typescript
   updateVisualizationMode(): void {
      const body: HTMLElement = this.document.body;
      const modern: boolean = !!this.model.modernVisualization;
      body.classList.toggle(this.VIZ_SHELL_CLASS, modern);
      body.classList.remove(...this.VIZ_DENSITY_CLASSES);
      const densityClass = `viz-density-${this.model.vizDensity}`;

      // unconditional: a marked assembly renders modern in a gate-off org too, and without a
      // density ancestor it would take the bare .viz-modern dense tier whatever the org chose
      if(this.VIZ_DENSITY_CLASSES.includes(densityClass)) {
         body.classList.add(densityClass);
      }

      body.classList.toggle(this.VIZ_SHELL_DARK_CLASS, modern && !!this.model.darkMode);
   }
```

`composer/app.component.ts:139-154` — the same change, reading `model.vizDensity`.

`vsobjects/viewer-app.component.ts:2794-2798`:

```typescript
         body.classList.remove(
            "viz-density-comfortable", "viz-density-compact", "viz-density-dense");

         // unconditional: see the portal shell's comment
         if(["comfortable", "compact", "dense"].includes(this.vizDensity)) {
            body.classList.add(`viz-density-${this.vizDensity}`);
         }
```

- [ ] **Step 9: Delete the tests of deleted mechanisms**

Delete these outright — they assert behaviour that no longer exists:

- `PlotDescriptorXmlTest`: every test whose subject is a seed boolean — the two defaults-are-false tests (`:125-126`, `:202-203` keep their `barCornerRadius` / `smoothLines` halves and lose the seed halves), `modernCornerSeed_roundTripsTrue`, `modernCornerSeed_legacyXmlWithoutAttributeIsFalse`, `modernCornerSeed_participatesInEqualsContent`, `setBarCornerRadius_clearsModernCornerSeed`, `modernSmoothSeed_roundTripsTrue`, `modernSmoothSeed_legacyXmlWithoutAttributeIsFalse`, `modernSmoothSeed_participatesInEqualsContent`, `setSmoothLines_clearsModernSmoothSeed`, and the two gate-collapse tests around `:169-197` and `:233-259`. Rewrite the remaining `*Value()` calls as the plain getters.
- `VSObjectChromeDefaultsTest`: `resolveSeededCornerKeepsSeedUnderGate`, `resolveSeededCornerStripsSeedGateOff`, `resolveSeededCornerPreservesNonSeedValues` (`:212-231`). Keep `cardCornerRadiusConstant`. In `calendarKeepsItsOwnRadiusInBothGateStates` and `cardCornerNotSeededForExcludedTypesUnderGate`, delete the trailing comment clauses that explain survival "because the strip keys on exact equality with 12" — the values are unchanged, the reason is gone.
- `VSCompositeFormatRoundCornerGateTest`: `defaultTierNonSeedValuePreservedGateOff`, `resolvedRadiusCopiedToUserTierIsNotDoubleStripped` and `tabDefaultTierRadiusIsNotStripped` all assert the absence of a strip that no longer exists. Delete them. Keep `defaultTierSeedHonoredUnderGate`, the inverted `defaultTierSeedSurvivesGateOff`, `userTierRadiusSurvivesGateOff`, `userTierRadiusWinsUnderGate` and `bareFormatIsSquareInBothGateStates` — the tier-precedence rules they pin are still real.
- `ChartPlotOptionsPaneModelTest`: the whole `:426-565` block on no-op-save seed preservation. Replace it with one test that an edited radius is written and one that a no-op save leaves the value alone, both on the plain getter.
- `ChangeChartTypeServiceSmoothLinesTransitionTest`, `VSWizardBindingHandlerSmoothLinesTest`, `ChartVSAScriptableTest`: drop the `isModern*Seed` assertion lines and rewrite `isSmoothLinesValue()` as `isSmoothLines()`. Every transition-matrix assertion about `smoothLines` itself stays — that logic is unchanged.
- `SeedChromeDefaultsTest`: rewrite `getBarCornerRadiusValue()` / `isSmoothLinesValue()` at `:256-269` and `:380-382` as the plain getters.

- [ ] **Step 10: Run the full core suite**

Run: `cd community && ./mvnw test -pl core`
Expected: PASS with zero failures and zero errors. Compare the test count against P5's 4905 — it will be **lower**, by roughly the number of tests Step 9 deletes, and that drop is the expected shape of this commit rather than a signal that something was skipped. Record the actual number in the commit message.

- [ ] **Step 11: Run the portal suites the shell change touches**

Run:

```bash
cd community/web && npx ng test portal --include="**/app.component.spec.ts"
cd community/web && npx ng test portal --include="**/viewer-app.component.spec.ts"
```

Expected: PASS. If either spec asserts that no density class is present when the gate is off, that assertion inverts — it is a sixth expected flip, and it belongs in this commit.

- [ ] **Step 12: Verify the persisted guard still holds and no gate reader was missed**

Run:

```bash
cd community && grep -rn "VSDensityDefaults.isModern()" --include=*.java core/src/main
```

Expected: exactly five hits, and no others — `VizContext.java:49` (`ofGate`), `VizMark.java:42` (creation), `VSChartInteractionDefaults.java:48` (accepted cost), `AbstractChartInfo.java:3745` (accepted cost), `CoreLifecycleService.java:315` (the modernizable offer). Any sixth is a stranding this commit was supposed to close.

Run: `cd community && ./mvnw test -pl core -Dtest=VizContextReadFlipTest`
Expected: PASS, including `exactlyOneDocumentedSiteStillReadsTheOrgGate` — it counts `ofGate()` call sites and is unaffected by this commit, which is itself worth noticing: that guard never covered any of the four reads deleted here.

- [ ] **Step 13: Commit**

```bash
cd community && git add -A core/src web/projects/portal/src
git commit -m "feat(viewsheet): the gate stops deciding how anything renders

Four deletions, one commit, because every partial state is worse than either
end. The gate && term in VizContext.of(VizMark); PlotDescriptor's
modernCornerSeed and modernSmoothSeed with their fields, XML attributes, equals
terms, self-clearing setters and the two now-redundant *Value() accessors; and
VSObjectChromeDefaults.resolveSeededCorner with VSCompositeFormat's TAB
carve-out for it. Delete the term alone and a marked chart in a gate-off org
keeps modern chrome except square bars and straight lines; delete the booleans
with it and it still loses its card radius.

resolveSeededCorner was recorded as a follow-on. It is not: its gate read
strands the card radius exactly the way the booleans strand bar corners, on the
more visible property. Retiring it needed a reversal path, and Revert is one.

The three shells now add viz-density-<mode> unconditionally. Gate-off no longer
means legacy, so a marked assembly in a gate-off org would otherwise find no
density ancestor and take the bare .viz-modern dense tier at every org density.

Two gate reads survive as documented accepted costs: getTooltipStyle (R20,
threading it means five ChartArea overloads reached by the report painter, the
exporter, annotations and the scheduler) and isInlineSvg (interaction rather
than chrome, with an explicit graph.svg.inline override).

Five tests invert and are the assertion this commit exists to make. The seed
booleans' test surface across six files, and resolveSeededCorner's dedicated
VSCompositeFormatRoundCornerGateTest, are deleted with the mechanisms, so the
suite total drops."
```

---

## Task 7: EM — unhide density, and say what the gate does

After Task 6 the density preference is read live for every marked assembly in a gate-off org, so hiding its control hides a setting that still works. Dark Mode is different and stays hidden: the dark axis is stamped into the mark at creation, and a gate-off org creates no marked content.

**Files:**
- Modify: `web/projects/em/src/app/settings/presentation/look-and-feel-settings-view/look-and-feel-settings-view.component.html:38-49`
- Modify: `core/src/main/resources/inetsoft/util/srinter.properties`

**Interfaces:**
- Consumes: the existing `visualizationDensity` and `modernVisualization` form controls
- Produces: nothing — template and strings only

- [ ] **Step 1: Move the density field out of the gate's `@if`**

Replace `:38-49`:

```html
        <mat-checkbox formControlName="modernVisualization"
                      matTooltip="_#(em.presentation.lookAndFeel.modernVisualization.tooltip)">_#(Modern Visualization)</mat-checkbox>
        <mat-form-field appearance="outline" color="accent">
          <mat-label>_#(Visualization Density)</mat-label>
          <mat-select formControlName="visualizationDensity">
            <mat-option value="comfortable">_#(Comfortable)</mat-option>
            <mat-option value="compact">_#(Compact)</mat-option>
            <mat-option value="dense">_#(Dense)</mat-option>
          </mat-select>
        </mat-form-field>
        @if (form.controls.modernVisualization?.value) {
          <mat-checkbox formControlName="darkMode">_#(Dark Mode)</mat-checkbox>
        }
```

Density leaves the `@if`; Dark Mode stays inside it.

- [ ] **Step 2: Confirm `MatTooltipModule` is imported**

Run:

```bash
cd community && grep -n "MatTooltipModule\|standalone\|imports" web/projects/em/src/app/settings/presentation/look-and-feel-settings-view/look-and-feel-settings-view.component.ts | head -20
```

If the component is standalone and `MatTooltipModule` is absent from its `imports`, add it (and the `import { MatTooltipModule } from "@angular/material/tooltip";` line). If it is declared in an NgModule, check that module's imports instead. Do not add the tooltip until the module resolves, or the template silently renders no tooltip and no error.

- [ ] **Step 3: Add the description string**

In `srinter.properties`, alongside the other `em.presentation.*` keys (find them with `grep -n "^em\.presentation\." core/src/main/resources/inetsoft/util/srinter.properties | head`):

```properties
em.presentation.lookAndFeel.modernVisualization.tooltip=Governs newly created dashboards and whether the composer offers to modernize an existing one. Dashboards already using the modern look keep it when this is turned off; use Revert Dashboard to Classic Look on an individual dashboard instead.
```

This is decision 13's "the EM property now says something it does not do." The checkbox label is unchanged — a rename would break every existing reference to it in docs and support material, and the tooltip carries the correction.

- [ ] **Step 4: Verify the EM builds and its specs pass**

Run:

```bash
cd community/web && npx ng build em --configuration development
cd community/web && npx ng test em --include="**/look-and-feel-settings-view.component.spec.ts"
```

Expected: build succeeds; the spec passes, or reports no such file (there is no spec for this view at `35ca4fce0` — if so, say that in the commit message rather than adding one, since the change is a template restructure with no logic).

- [ ] **Step 5: Commit**

```bash
cd community && git add web/projects/em/src/app/settings/presentation/look-and-feel-settings-view/look-and-feel-settings-view.component.html core/src/main/resources/inetsoft/util/srinter.properties
git commit -m "feat(em): show Visualization Density regardless of the gate

Density is read live for every marked assembly, in either gate state, and the
body class now carries it unconditionally - so hiding the control behind the
gate checkbox hid a setting that still changes how dashboards render. Dark Mode
stays hidden: the dark axis is stamped into the mark at creation, and a gate-off
org creates no marked content.

The gate checkbox gains a tooltip saying what it now governs - newly created
content and whether Modernize is offered - because 'Modern Visualization: off'
while modern dashboards go on rendering modern reads as a bug otherwise. Label
unchanged; a rename would break existing references."
```

---

## Task 8: Verification pass

No code. This task is the phase's evidence, and none of it can be produced from a test run.

**Files:** none

- [ ] **Step 1: Full automated gates**

Run, and record each number:

```bash
cd community && ./mvnw test -pl core
cd community/web && npx ng test portal
cd community/web && npx ng test em
cd /e/StyleBI/stylebi-enterprise && ./mvnw.cmd install -DskipTests "-Pcommunity,enterprise"
```

Expected: core green (a lower total than P5's 4905, by the tests Task 6 deleted); portal and em green; the cross-module build clean. **Do not** run the TL suites unfiltered — scope any TL check with `--include`.

- [ ] **Step 2: Confirm the P0 prerequisite before believing the byte-comparison check**

The headline check below is meaningless against a pre-mark-cohort asset — one created on `viz-updates` with the gate on before P1 landed, which carries seeded modern values and no mark. Either confirm those dev dashboards are gone, or create the dashboards for Step 3 fresh in this session. Note which you did.

- [ ] **Step 3: The manual checks, in a built server with a browser**

1. **A marked dashboard in a gate-off org renders fully modern** — card radius, bar corners, smooth lines, border colour, card and page background, and the org's density. This could not have passed before Task 6 and is the single clearest signal the four deletions went together. Check the card radius and the bar corners specifically: they are the two the partial states would have broken.
2. **Modernize, then Revert, then compare against a never-modernized dashboard** — same chrome in every property the hook touches.
3. **Undo after Revert restores modern chrome in one step**, and the Modernize bar does not reappear offering to undo it for the rest of the session.
4. **A mixed dashboard reverts only its marked assemblies** and leaves the unmarked ones alone.
5. **Reverting a host does not revert an embedded viewsheet's own assemblies.**
6. **Revert is offered with the gate off** and refused without write permission on the asset.
7. **Export agrees with view** — PDF, PNG and Excel, for a reverted dashboard and for a marked one in a gate-off org. Every value Revert writes is persisted and painter-read, so this is not a formality.
8. **Density applies in a gate-off org with marked content** — set the org to compact, confirm row heights and control heights follow, and confirm the EM still shows the density control. Check the **anchored mini-toolbar** specifically: `isAnchoredResident` reads density through `GuiTool`, so a marked assembly in a compact gate-off org must show the anchored strip rather than the dense suppression. Then open a **legacy** dashboard in the same org and confirm it shows neither — that is the half this step could have broken.
9. **Org-level surfaces take the org density** (Task 1's fix): open a combo-box dropdown in a compact org and confirm its row height is the compact 24px, not the dense 20px.
10. **The two accepted costs are what was accepted, not something worse** — in a gate-off org, a marked chart's tooltip renders legacy chrome and its plot has no inline-SVG animation. Confirm these are the *only* two surfaces that stay legacy.

- [ ] **Step 4: Record the results in the roadmap**

Add P6 to the roadmap's Done table with its commit hashes, and move it off "What to pick up next" — re-derive that ranking from the dependency picture rather than editing it in place, per the file's own instruction. The two items P6 unblocks are the card radius 12→6 constant and, still, L′ as the highest-value visible item.

- [ ] **Step 5: Commit the documentation update**

```bash
cd community && git add docs/superpowers/specs/lookfeel/chart-card-roadmap.md
git commit -m "docs(chart-card): record P6 shipped and re-derive what comes next"
```

---

## Self-Review

**Spec coverage.** §5's P6 piece 1 → Task 3. Piece 2 → Tasks 2 and 6 (the `else` branch lands early because it is behaviour-neutral and Task 3's revert needs it). Piece 3 → Task 6. Piece 4 (`resolveSeededCorner`) → Task 6. Piece 5 (wiring) → Tasks 4 and 5. The three folded-in items → Task 1 (tokens regression), Task 6 Step 8 (density body class), Task 7 (EM). The settled bar-re-arm interaction → Task 5 Step 4. The two accepted costs → verified negatively in Task 8 Step 3 item 10 rather than implemented. The five test inversions → Task 6 Step 1. The deletion surface → Task 6 Step 9. Decision 13's confirmation dialog → Task 5. Decision 13's "no gate floor" → Task 4 Step 5 and its test. The P0 prerequisite → Task 8 Step 2.

**One deliberate deviation, stated in Global Constraints:** the spec groups pieces 1–4 in one commit; this plan ships Revert working first (Tasks 2–5, no rendering change) and makes Task 6 the pure reversal. The spec's two real constraints — a reversal path exists before the term dies, and the deletions are inseparable — both hold, and the ordering principle §5 says P6 must bend does not have to.

**Type consistency check.** `revert(Viewsheet): int` and `hasMarked(Viewsheet): boolean` are defined in Task 3 and consumed in Task 4 under those exact names. `RevertViewsheetService.revert(String, Principal, CommandDispatcher, String): Void` is defined in Task 4 and reached in Task 5 only through the STOMP path `/events/composer/viewsheet/revert`, which matches the `@MessageMapping("composer/viewsheet/revert")` in the same task. `Viewsheet.revertable` and the `"revertable"` infoMap key match between Tasks 4 and 5. `collect(Viewsheet, Predicate<VSAssemblyInfo>)` and `addIf(...)` are introduced in Task 3 and used nowhere else. `isSmoothLinesValue()` / `getBarCornerRadiusValue()` are used in Task 2's test and deleted in Task 6 Step 4 — Task 6 Step 9 rewrites that test's calls onto the plain getters, which is called out explicitly under `SeedChromeDefaultsTest`.

**Two placeholders that are deliberate and flagged as such:** Task 5 Step 1 names `createPane()` and `getCanvasActions()` as stand-ins and instructs the implementer to read the existing modernize tests at `:259-297` and reuse the real harness rather than build one. Naming them concretely would be worse — the real names could not be verified without running that spec file, and inventing them would send the implementer to write a second harness.
