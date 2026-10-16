# Form Control Density Sizing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Combo box, text input, spinner, submit, checkbox and radio button keep an author's size through density changes and Revert, a legacy control is never resized on open, and checkbox and radio rows follow the density title lane and the selection list's row height.

**Architecture:** Values stay in `VSDensityDefaults`; the size rules move into one new package-private class, `FormControlDensity`, called from each type's `seedChromeDefaults`. The six types adopt PR #6390's author-size hooks plus one new hook, `densitySizeIsHeightOnly()`, which makes a width-only edit not the author's and keeps Width editable in the Size & Position pane. Checkbox and radio button resolve their lane and option row when drawn, guarded by `userTitleHeight` and a new `userCellHeight`, so every renderer follows without edits.

**Tech Stack:** Java 21, JUnit 5, Mockito, Spring test context (`@SreeHome`), Maven (`./mvnw`, module `core`); Angular 21 with Vitest for one pane spec.

**Spec:** `docs/superpowers/specs/lookfeel/2026-10-09-form-control-density-size-design.md`

## Global Constraints

- **Branch and worktree:** `feature-form-control-density`, in the worktree at `C:/Users/Franky/AppData/Local/Temp/claude/E--StyleBI-stylebi-enterprise/0a07e7f1-1fcf-4943-93eb-11ed66798c02/scratchpad/form-control-density` (call it `$WT`). It is stacked on `bug-bottom-tabs-modernize-revert` @ `a41d4cff1d`. Never work in the shared checkout `E:/StyleBI/stylebi-enterprise/community`; other sessions use it.
- **Git:** run each git command as its own call with `git -C "$WT" ...`. Never chain git commands with `&&`; a hook blocks it. Run `git -C "$WT" branch --show-current` before every commit and expect `feature-form-control-density`. Never push; the user pushes.
- **Commit messages:** plain prose subject, no blockquotes, ending with the line `Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>`.
- **Numbers:** control height 24 / 28 / 30, legacy 20. Lane 20 / 26 / 30, legacy 20. Option row 20 / 24 / 28, legacy 20. Option box 40 / 50 / 58, legacy 40. Today's seed wrote option boxes 48 / 56 / 60 and option rows 28 / 36 / 40.
- **Recognised sets:** single-control heights {20, 24, 28, 30}; option boxes {40, 50, 58, 48, 56, 60}; former option rows {28, 36, 40}.
- **Legacy guard (D9):** every unmarked branch runs only when `ctx.transition` is true. A Revert is simulated in tests with `VizContext.ofTransition(null, null)`; a restore with `VizContext.of((VizMark) null)`.
- **Width:** density never writes a control's width.
- **Comments:** a short clause each. Never mention the spec, this plan, decisions (D1…), PR numbers or tickets in source code. No comments in `.html` templates.
- **New files** start with the AGPL header used by `BottomTabsReseedTest.java` (`Copyright (C) 2026  InetSoft Technology`).
- **Unchanged:** the slider, every export painter and exporter, `VSCheckBoxModel`, `VSRadioButtonModel`, every control template except `size-position-pane.component.html`, and selection list, tree, container and calendar behaviour.

## Review Focus

1. **A legacy dashboard opened in the viewer** with a text input an author sized to 28px keeps 28. Pinned in Task 2 (`aRestoreLeavesAnUnmarkedControlAlone`) and Task 6 (`aRestoreLeavesAnUnmarkedBoxAlone`).
2. **Pressing OK in a checkbox dialog at comfortable without touching the size fields** flags nothing: the fields show the resolved 30 / 28 while the stored values are 20. Pinned in Task 7 (`anUntouchedApplyAtComfortableFlagsNothing`).
3. **Ticking "follow default density" on a text input and typing a new width in the same apply** keeps the typed width. Pinned in Task 3 (`aTickedApplyKeepsATypedWidthOnAHeightOnlyType`).
4. **A combo box marked modern** never resolves an option row, so nothing reading its inherited `cellHeight` changes. Pinned in Task 5 (`aComboBoxNeverResolvesARow`).
5. **A checkbox in bottom tabs saved with today's 60px box** stays on its strip when opened. Pinned in Task 6 (`aRestoreKeepsACheckboxWithTodaysBoxOnTheStrip`).

## How to run tests

Java, from `$WT`. `-q` hides the summary on success; read `core/target/surefire-reports/*.txt` for counts:

```bash
cd "$WT"
./mvnw -q test -pl core -Dtest=ClassName -Dsurefire.failIfNoSpecifiedTests=false
```

Several classes: `"-Dtest=A,B,C"`. Do not add `install`; the shared local repository must not receive this branch's jars.

Frontend: Vitest cannot run from a worktree whose `node_modules` is a junction. Task 4 runs `npm ci` in `$WT/web` once, then:

```bash
cd "$WT/web"
npx ng test portal --include="**/size-position-pane.spec.ts"
```

---

### Task 0: Baseline exports (the user, before any production code)

Spec §9.2. No code lands until this is done.

**Files:**
- Create (git-excluded): `$WT/.superpowers/baselines/form-control-density/` (`.superpowers/` is already ignored)

- [ ] **Step 1: Build a fixture on today's server**

On the running server (built from `bug-bottom-tabs-modernize-revert` or older), create four dashboards in the global folder: `FCD Modern Dense`, `FCD Modern Compact`, `FCD Modern Comfortable` (each pinning its density in the dashboard properties) and `FCD Legacy` (Revert applied). Each holds:
- one combo box, text input, spinner and submit at default size;
- a text input widened to 300px;
- a combo box an author made 40px tall;
- a checkbox and a radio button at default size, three values each;
- a checkbox sized by an author to 100px tall, eight values;
- a bottom-tabs container holding a checkbox and a text input.

- [ ] **Step 2: Export and record**

Export each dashboard to PDF, PNG and HTML into the baselines folder, export the four assets as `fcd-fixture.zip` (EM → Content → Repository → Export), and write `README.txt` listing each assembly's name and stored size. The marked copies must show 48 / 56 / 60px checkboxes; only today's seed produces them.

---

### Task 1: Values in `VSDensityDefaults`

**Files:**
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/VSDensityDefaults.java` (after `isControlHeight`, `:179-182`; javadoc `:170-178`)
- Test: `core/src/test/java/inetsoft/uql/viewsheet/internal/VSDensityDefaultsTest.java`

**Interfaces:**
- Produces: `public static boolean isSeededControlHeight(int)`, `public static int optionBoxHeight(VizContext)`, `public static boolean isSeededOptionBoxHeight(int)`, `public static boolean isFormerOptionBoxHeight(int)`, `public static boolean isFormerOptionRow(int)`, package-private `static int optionBoxHeightForMode(String)`.

- [ ] **Step 1: Write the failing tests**

Append to `VSDensityDefaultsTest`:

```java
   @Test
   void theOptionBoxIsTheLaneOverOneRow() {
      assertEquals(40, VSDensityDefaults.optionBoxHeightForMode("dense"));
      assertEquals(50, VSDensityDefaults.optionBoxHeightForMode("compact"));
      assertEquals(58, VSDensityDefaults.optionBoxHeightForMode("comfortable"));

      for(String mode : new String[] { "dense", "compact", "comfortable" }) {
         assertEquals(VSDensityDefaults.titleHeightForMode(mode) +
                         VSDensityDefaults.rowHeightForMode(mode),
                      VSDensityDefaults.optionBoxHeightForMode(mode), mode);
      }
   }

   @Test
   void aLegacyContextYieldsTheLegacyOptionBox() {
      assertEquals(2 * AssetUtil.defh,
                   VSDensityDefaults.optionBoxHeight(VizContext.of((VizMark) null)));
   }

   @Test
   void theSeededHeightSets() {
      for(int h : new int[] { 20, 24, 28, 30 }) {
         assertTrue(VSDensityDefaults.isSeededControlHeight(h), "control " + h);
      }

      for(int h : new int[] { 19, 22, 26, 32, 40 }) {
         assertFalse(VSDensityDefaults.isSeededControlHeight(h), "control " + h);
      }

      for(int h : new int[] { 40, 50, 58, 48, 56, 60 }) {
         assertTrue(VSDensityDefaults.isSeededOptionBoxHeight(h), "box " + h);
      }

      for(int h : new int[] { 44, 54, 64, 100 }) {
         assertFalse(VSDensityDefaults.isSeededOptionBoxHeight(h), "box " + h);
      }

      for(int h : new int[] { 48, 56, 60 }) {
         assertTrue(VSDensityDefaults.isFormerOptionBoxHeight(h), "former box " + h);
      }

      for(int h : new int[] { 40, 50, 58 }) {
         assertFalse(VSDensityDefaults.isFormerOptionBoxHeight(h), "former box " + h);
      }

      for(int h : new int[] { 28, 36, 40 }) {
         assertTrue(VSDensityDefaults.isFormerOptionRow(h), "former row " + h);
      }

      for(int h : new int[] { 20, 24, 30, 35 }) {
         assertFalse(VSDensityDefaults.isFormerOptionRow(h), "former row " + h);
      }
   }
```

- [ ] **Step 2: Run to verify they fail**

Run: `./mvnw -q test -pl core -Dtest=VSDensityDefaultsTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: compilation failure, `cannot find symbol optionBoxHeightForMode`.

- [ ] **Step 3: Implement**

Replace the `isControlHeight` javadoc (`:170-178`) with:

```java
   /**
    * Whether height is one of the three tier control heights (24/28/30), at any tier.
    */
```

Insert after `isControlHeight`'s closing brace:

```java
   /**
    * Whether a single control's height is one the control-height rule writes or replaces: the
    * legacy default or a tier height.
    */
   public static boolean isSeededControlHeight(int height) {
      return height == AssetUtil.defh || isControlHeight(height);
   }

   /**
    * The default box of a checkbox or radio button: its title lane over one option row.
    */
   public static int optionBoxHeight(VizContext ctx) {
      return ctx.modern ? optionBoxHeightForMode(ctx.density) : LEGACY_OPTION_BOX_HEIGHT;
   }

   /**
    * Whether a checkbox or radio box height is one a seed wrote: the legacy box, a tier box, or
    * a box the earlier doubled-control-height seed wrote.
    */
   public static boolean isSeededOptionBoxHeight(int height) {
      return height == LEGACY_OPTION_BOX_HEIGHT
         || height == optionBoxHeightForMode(COMFORTABLE)
         || height == optionBoxHeightForMode(COMPACT)
         || height == optionBoxHeightForMode(DENSE)
         || isFormerOptionBoxHeight(height);
   }

   /**
    * Whether a box height is one the earlier seed wrote: twice a control height.
    */
   public static boolean isFormerOptionBoxHeight(int height) {
      return height % 2 == 0 && isControlHeight(height / 2);
   }

   /**
    * Whether an option row is one the earlier seed wrote: a former box less the legacy lane.
    */
   public static boolean isFormerOptionRow(int height) {
      return isFormerOptionBoxHeight(height + AssetUtil.defh);
   }

   static int optionBoxHeightForMode(String mode) {
      return titleHeightForMode(mode) + rowHeightForMode(mode);
   }

   private static final int LEGACY_OPTION_BOX_HEIGHT = 2 * AssetUtil.defh;
```

- [ ] **Step 4: Run to verify they pass**

Run: `./mvnw -q test -pl core -Dtest=VSDensityDefaultsTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS, every test in the class.

- [ ] **Step 5: Commit**

```bash
git -C "$WT" branch --show-current
git -C "$WT" add core/src/main/java/inetsoft/uql/viewsheet/internal/VSDensityDefaults.java core/src/test/java/inetsoft/uql/viewsheet/internal/VSDensityDefaultsTest.java
git -C "$WT" commit -m "Add the option box and the seeded-height sets to the density values" -m "Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 2: The single-height rule, its author flag and the legacy guard

**Files:**
- Create: `core/src/main/java/inetsoft/uql/viewsheet/internal/FormControlDensity.java`
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/VSAssemblyInfo.java` (after `defaultSize`, `:1469-1471`)
- Modify: `ComboBoxVSAssemblyInfo.java:426-434`, `TextInputVSAssemblyInfo.java:92-100`, `SpinnerVSAssemblyInfo.java:75-83`, `SubmitVSAssemblyInfo.java:92-100` (all in `core/src/main/java/inetsoft/uql/viewsheet/internal/`)
- Create: `core/src/test/java/inetsoft/uql/viewsheet/internal/ControlDensitySizeTest.java`
- Modify: `core/src/test/java/inetsoft/uql/viewsheet/internal/ControlHeightFollowDensityTest.java`

**Interfaces:**
- Consumes: Task 1's `isSeededControlHeight`, `isControlHeight`, `controlHeight(VizContext)`.
- Produces: `public boolean VSAssemblyInfo.densitySizeIsHeightOnly()` (default false), `public final boolean VSAssemblyInfo.isAuthorSizeChange(Dimension before, Dimension after)`, `static void FormControlDensity.seedControlHeight(VSAssemblyInfo, VizContext)`, `static Dimension FormControlDensity.controlSize(VSAssemblyInfo, VizContext)`. On the four types: `takesDensitySize()` true, `densitySizeIsHeightOnly()` true, `followsDensitySize()`, `defaultSize(VizContext)`.

- [ ] **Step 1: Write the failing tests**

Create `ControlDensitySizeTest.java`:

```java
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
package inetsoft.uql.viewsheet.internal;

import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.asset.internal.AssetUtil;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Dimension;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ControlDensitySizeTest {
   @BeforeEach
   void density() {
      SreeEnv.setProperty("viewsheet.density", "comfortable");
   }

   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.density", null);
   }

   static Stream<Supplier<VSAssemblyInfo>> controls() {
      return Stream.of(ComboBoxVSAssemblyInfo::new, TextInputVSAssemblyInfo::new,
                       SpinnerVSAssemblyInfo::new, SubmitVSAssemblyInfo::new);
   }

   private static VSAssemblyInfo marked(Supplier<VSAssemblyInfo> type, int height) {
      VSAssemblyInfo info = type.get();
      info.setVizMark(VizMark.MODERN_LIGHT);
      info.setPixelSize(new Dimension(100, height));
      return info;
   }

   @ParameterizedTest
   @MethodSource("controls")
   void aDensityChangeMovesAnUnflaggedControlAndLeavesAFlaggedOne(Supplier<VSAssemblyInfo> type) {
      VSAssemblyInfo following = marked(type, 30);
      VSAssemblyInfo authored = marked(type, 30);
      authored.setUserSize(true);

      SreeEnv.setProperty("viewsheet.density", "dense");
      following.seedChromeDefaults(VizContext.of(following));
      authored.seedChromeDefaults(VizContext.of(authored));

      assertEquals(24, following.getPixelSize().height);
      assertEquals(30, authored.getPixelSize().height);
   }

   @ParameterizedTest
   @MethodSource("controls")
   void revertKeepsAFlaggedHeightAndReturnsAnUnflaggedOne(Supplier<VSAssemblyInfo> type) {
      VSAssemblyInfo following = marked(type, 28);
      VSAssemblyInfo authored = marked(type, 28);
      authored.setUserSize(true);

      for(VSAssemblyInfo info : new VSAssemblyInfo[] { following, authored }) {
         info.setVizMark(null);
         info.seedChromeDefaults(VizContext.ofTransition(null, null));
      }

      assertEquals(AssetUtil.defh, following.getPixelSize().height);
      assertEquals(28, authored.getPixelSize().height);
   }

   @ParameterizedTest
   @MethodSource("controls")
   void aRestoreLeavesAnUnmarkedControlAlone(Supplier<VSAssemblyInfo> type) {
      VSAssemblyInfo info = type.get();
      info.setVizMark(null);
      info.setPixelSize(new Dimension(100, 28));

      info.seedChromeDefaults(VizContext.of((VizMark) null));

      assertEquals(28, info.getPixelSize().height, "a legacy author height survives an open");
   }

   @ParameterizedTest
   @MethodSource("controls")
   void followsDensitySizeRecognisesExactlyTheRuleHeights(Supplier<VSAssemblyInfo> type) {
      for(int h : new int[] { 20, 24, 28, 30 }) {
         assertTrue(marked(type, h).followsDensitySize(), "height " + h);
      }

      assertFalse(marked(type, 40).followsDensitySize(), "an author height");

      VSAssemblyInfo flagged = marked(type, 30);
      flagged.setUserSize(true);
      assertFalse(flagged.followsDensitySize(), "a flagged height");
   }

   @ParameterizedTest
   @MethodSource("controls")
   void resetSizeWritesTheTierHeightAndKeepsTheWidth(Supplier<VSAssemblyInfo> type) {
      VSAssemblyInfo info = marked(type, 40);
      info.setPixelSize(new Dimension(300, 40));
      info.setUserSize(true);

      info.resetSize(VizContext.of(info));

      assertEquals(new Dimension(300, 30), info.getPixelSize());
      assertFalse(info.isUserSize());
   }

   @ParameterizedTest
   @MethodSource("controls")
   void onlyAHeightChangeIsTheAuthors(Supplier<VSAssemblyInfo> type) {
      VSAssemblyInfo info = type.get();

      assertTrue(info.takesDensitySize());
      assertTrue(info.densitySizeIsHeightOnly());
      assertFalse(info.isAuthorSizeChange(new Dimension(100, 30), new Dimension(300, 30)));
      assertTrue(info.isAuthorSizeChange(new Dimension(100, 30), new Dimension(100, 40)));
   }

   @Test
   void aBoxTypeTakesEitherDimension() {
      SelectionListVSAssemblyInfo list = new SelectionListVSAssemblyInfo();

      assertFalse(list.densitySizeIsHeightOnly());
      assertTrue(list.isAuthorSizeChange(new Dimension(100, 120), new Dimension(120, 120)));
      assertFalse(list.isAuthorSizeChange(new Dimension(100, 120), new Dimension(100, 120)));
   }
}
```

In `ControlHeightFollowDensityTest`, make the single-height Revert tests run a Revert rather than a restore. In each of `spinnerHeightRestoresLegacyOnRevert`, `spinnerHeightAtACustomValueIsUnaffectedByRevert` and `submitHeightRestoresLegacyOnRevert`, change

```java
      info.seedChromeDefaults(VizContext.of((VizMark) null));
```

to

```java
      info.seedChromeDefaults(VizContext.ofTransition(null, null));
```

In `spinnerHeightRestoresLegacyOnRevert`, replace the comment line above that call with `// a Revert runs with an unmarked transition context`.

Replace `aControlHandSizedToATierValueIsAlsoMoved` (`:411-422`) with:

```java
   @Test
   void anUnflaggedControlAtATierValueIsMoved() {
      SreeEnv.setProperty("viewsheet.density", "comfortable");
      SpinnerVSAssemblyInfo info = new SpinnerVSAssemblyInfo();
      info.setPixelSize(new Dimension(info.getPixelSize().width, 28));

      info.seedChromeDefaults(VizContext.of(VizMark.MODERN_LIGHT));

      assertEquals(30, info.getPixelSize().height, "content saved before the flag reads as seeded");
   }

   @Test
   void aFlaggedControlAtATierValueIsKept() {
      SreeEnv.setProperty("viewsheet.density", "comfortable");
      SpinnerVSAssemblyInfo info = new SpinnerVSAssemblyInfo();
      info.setPixelSize(new Dimension(info.getPixelSize().width, 28));
      info.setUserSize(true);

      info.seedChromeDefaults(VizContext.of(VizMark.MODERN_LIGHT));

      assertEquals(28, info.getPixelSize().height);
   }
```

- [ ] **Step 2: Run to verify they fail**

Run: `./mvnw -q test -pl core "-Dtest=ControlDensitySizeTest,ControlHeightFollowDensityTest" -Dsurefire.failIfNoSpecifiedTests=false`
Expected: compilation failure, `cannot find symbol densitySizeIsHeightOnly`.

- [ ] **Step 3: Add the hooks to `VSAssemblyInfo`**

Insert after `defaultSize(VizContext)` (`:1469-1471`):

```java
   /**
    * Whether density owns only this type's height, so its width is always the author's.
    */
   public boolean densitySizeIsHeightOnly() {
      return false;
   }

   /**
    * Whether a size change is the author's: the height alone when density owns only the height,
    * either dimension otherwise.
    */
   public final boolean isAuthorSizeChange(Dimension before, Dimension after) {
      return densitySizeIsHeightOnly() ? before.height != after.height : !before.equals(after);
   }
```

- [ ] **Step 4: Create `FormControlDensity`**

```java
/*
 * (the AGPL header from Step 1)
 */
package inetsoft.uql.viewsheet.internal;

import inetsoft.uql.asset.internal.AssetUtil;

import java.awt.Dimension;

/**
 * The density size rules the form controls share. Their values live in VSDensityDefaults.
 */
final class FormControlDensity {
   private FormControlDensity() {
   }

   /**
    * The single-height rule for combo box, text input, spinner and submit. The legacy branch
    * runs only on a Revert or mode switch, so a restore never resizes an unmarked control.
    */
   static void seedControlHeight(VSAssemblyInfo info, VizContext ctx) {
      if(info.isUserSize()) {
         return;
      }

      Dimension size = info.getPixelSize();

      if(ctx.modern && VSDensityDefaults.isSeededControlHeight(size.height)) {
         info.setPixelSize(new Dimension(size.width, VSDensityDefaults.controlHeight(ctx)));
      }
      else if(!ctx.modern && ctx.transition && VSDensityDefaults.isControlHeight(size.height)) {
         info.setPixelSize(new Dimension(size.width, AssetUtil.defh));
      }
   }

   /**
    * A single control's density size: its own width over the tier height.
    */
   static Dimension controlSize(VSAssemblyInfo info, VizContext ctx) {
      return new Dimension(info.getPixelSize().width, VSDensityDefaults.controlHeight(ctx));
   }
}
```

- [ ] **Step 5: Use it in the four types**

In each of `ComboBoxVSAssemblyInfo`, `TextInputVSAssemblyInfo`, `SpinnerVSAssemblyInfo` and `SubmitVSAssemblyInfo`, replace the block that starts at the comment `// also re-fires on a density change, or a seeded control is stranded at the old tier` and ends at the `else if(!ctx.modern && ...)` branch's closing brace with:

```java
      FormControlDensity.seedControlHeight(this, ctx);
```

Then add these overrides directly after that `seedChromeDefaults` method, in each of the four:

```java
   @Override
   public boolean takesDensitySize() {
      return true;
   }

   @Override
   public boolean densitySizeIsHeightOnly() {
      return true;
   }

   @Override
   public boolean followsDensitySize() {
      return !isUserSize() && VSDensityDefaults.isSeededControlHeight(getPixelSize().height);
   }

   @Override
   protected Dimension defaultSize(VizContext ctx) {
      return FormControlDensity.controlSize(this, ctx);
   }
```

Each file already imports `java.awt.*`. Remove the `AssetUtil` import only if the compiler reports it unused (Combo and Spinner still use it in their constructors).

- [ ] **Step 6: Run to verify they pass**

Run: `./mvnw -q test -pl core "-Dtest=ControlDensitySizeTest,ControlHeightFollowDensityTest,AuthorSizeFlagTest,VSDensityDefaultsTest" -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS. `ControlHeightFollowDensityTest`'s checkbox and radio tests still pass here; Task 6 replaces them.

- [ ] **Step 7: Commit**

```bash
git -C "$WT" branch --show-current
git -C "$WT" add core/src/main/java/inetsoft/uql/viewsheet/internal/FormControlDensity.java core/src/main/java/inetsoft/uql/viewsheet/internal/VSAssemblyInfo.java core/src/main/java/inetsoft/uql/viewsheet/internal/ComboBoxVSAssemblyInfo.java core/src/main/java/inetsoft/uql/viewsheet/internal/TextInputVSAssemblyInfo.java core/src/main/java/inetsoft/uql/viewsheet/internal/SpinnerVSAssemblyInfo.java core/src/main/java/inetsoft/uql/viewsheet/internal/SubmitVSAssemblyInfo.java core/src/test/java/inetsoft/uql/viewsheet/internal/ControlDensitySizeTest.java core/src/test/java/inetsoft/uql/viewsheet/internal/ControlHeightFollowDensityTest.java
git -C "$WT" commit -m "Keep an author's control height through density changes and Revert, and a legacy one on open" -m "Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 3: Dialog and composer plumbing for a height-only size

**Files:**
- Modify: `core/src/main/java/inetsoft/web/composer/model/vs/SizePositionPaneModel.java` (field block `:141-143`, accessors near `:84-89`)
- Modify: `core/src/main/java/inetsoft/web/viewsheet/service/VSDialogService.java` (`:165-208`)
- Modify: `core/src/main/java/inetsoft/web/composer/vs/objects/controller/ComposerObjectService.java:148`
- Modify: `core/src/main/java/inetsoft/web/viewsheet/service/VSInputService.java` (text input `:747-751` and `:872`; combo box `:1829-1833` and `:2013`; spinner `:2225-2229` and `:2291`)
- Modify: `core/src/main/java/inetsoft/web/composer/vs/dialog/SubmitPropertyDialogService.java` (`:83-87` and `:136`)
- Test: `core/src/test/java/inetsoft/web/viewsheet/service/SizeFollowsDensityDialogTest.java`
- Test: `core/src/test/java/inetsoft/web/composer/vs/objects/controller/ComposerObjectServiceTest.java`

**Interfaces:**
- Consumes: Task 2's `isAuthorSizeChange`, `densitySizeIsHeightOnly`, the four types' overrides.
- Produces: `SizePositionPaneModel.isDensityHeightOnly()` / `setDensityHeightOnly(boolean)`; `VSDialogService.setAssemblySizeFollowingDensity(VSAssemblyInfo info, SizePositionPaneModel model, Viewsheet viewsheet)`.

- [ ] **Step 1: Write the failing tests**

Append to `SizeFollowsDensityDialogTest`:

```java
   private static TextInputVSAssemblyInfo textInput(Dimension size, boolean userSize) {
      TextInputVSAssemblyInfo info = new TextInputVSAssemblyInfo();
      info.setVizMark(VizMark.MODERN_LIGHT);
      info.setPixelSize(size);
      info.setUserSize(userSize);
      return info;
   }

   @Test
   void readMarksAHeightOnlyType() {
      SizePositionPaneModel model = new SizePositionPaneModel();
      VSDialogService.readSizeFollowsDensity(textInput(new Dimension(100, 30), false), model, true);

      assertEquals(Boolean.TRUE, model.getSizeFollowsDensity());
      assertTrue(model.isDensityHeightOnly());

      SizePositionPaneModel box = new SizePositionPaneModel();
      VSDialogService.readSizeFollowsDensity(
         container(VizMark.MODERN_LIGHT, new Dimension(300, 360), false), box, true);
      assertFalse(box.isDensityHeightOnly());
   }

   @Test
   void aTickedApplyKeepsATypedWidthOnAHeightOnlyType() {
      TextInputVSAssemblyInfo info = textInput(new Dimension(100, 40), true);
      SizePositionPaneModel model = model(true, 300, 40);

      VSDialogService.applyDensitySize(info, model);

      assertEquals(300, model.getWidth(), "the typed width survives");
      assertEquals(30, model.getHeight());
      assertFalse(info.isUserSize());
   }

   @Test
   void aWidthOnlyEditIsNotTheAuthorsHeight() {
      TextInputVSAssemblyInfo widened = textInput(new Dimension(100, 30), false);
      VSDialogService.recordAuthorSize(widened, model(null, 300, 30), new Dimension(100, 30));
      assertFalse(widened.isUserSize());

      TextInputVSAssemblyInfo taller = textInput(new Dimension(100, 30), false);
      VSDialogService.recordAuthorSize(taller, model(null, 100, 40), new Dimension(100, 30));
      assertTrue(taller.isUserSize());
   }

   @Test
   void theFollowingSetterRunsTheThreeStepsInOrder() {
      Viewsheet vs = new Viewsheet();
      TextInputVSAssembly assembly = new TextInputVSAssembly(vs, "TextInput1");
      vs.addAssembly(assembly);
      TextInputVSAssemblyInfo info = (TextInputVSAssemblyInfo) assembly.getVSAssemblyInfo();
      info.setVizMark(VizMark.MODERN_LIGHT);
      info.setPixelSize(new Dimension(100, 30));
      VSDialogService service = new VSDialogService(null, null);

      service.setAssemblySizeFollowingDensity(info, model(null, 250, 30), vs);
      assertEquals(new Dimension(250, 30), info.getPixelSize());
      assertFalse(info.isUserSize(), "a widened control keeps following");

      service.setAssemblySizeFollowingDensity(info, model(null, 250, 44), vs);
      assertEquals(new Dimension(250, 44), info.getPixelSize());
      assertTrue(info.isUserSize());

      service.setAssemblySizeFollowingDensity(info, model(true, 250, 44), vs);
      assertEquals(new Dimension(250, 30), info.getPixelSize());
      assertFalse(info.isUserSize());
   }
```

Add imports to `SizeFollowsDensityDialogTest` if missing: `inetsoft.uql.viewsheet.TextInputVSAssembly`.

Append to `ComposerObjectServiceTest`, beside `aDraggedListIsFlagged`:

```java
   @Test
   void aTallerTextInputIsFlagged() throws Exception {
      Viewsheet vs = new Viewsheet();
      vs.getVSAssemblyInfo().setName("vs1");
      TextInputVSAssembly input = new TextInputVSAssembly(vs, "TextInput1");
      input.getVSAssemblyInfo().setPixelOffset(new Point(0, 0));
      input.getVSAssemblyInfo().setPixelSize(new Dimension(100, 30));
      vs.addAssembly(input);

      resize(vs, "TextInput1", 100, 44);

      assertTrue(input.getVSAssemblyInfo().isUserSize());
   }

   @Test
   void aWiderTextInputIsNotFlagged() throws Exception {
      Viewsheet vs = new Viewsheet();
      vs.getVSAssemblyInfo().setName("vs1");
      TextInputVSAssembly input = new TextInputVSAssembly(vs, "TextInput1");
      input.getVSAssemblyInfo().setPixelOffset(new Point(0, 0));
      input.getVSAssemblyInfo().setPixelSize(new Dimension(100, 30));
      vs.addAssembly(input);

      resize(vs, "TextInput1", 300, 30);

      assertFalse(input.getVSAssemblyInfo().isUserSize(), "density owns only the height");
   }
```

`ComposerObjectServiceTest` already imports `inetsoft.uql.viewsheet.*`.

- [ ] **Step 2: Run to verify they fail**

Run: `./mvnw -q test -pl core "-Dtest=SizeFollowsDensityDialogTest,ComposerObjectServiceTest" -Dsurefire.failIfNoSpecifiedTests=false`
Expected: compilation failure, `cannot find symbol isDensityHeightOnly`.

- [ ] **Step 3: Add the model field**

In `SizePositionPaneModel.java`, add the field beside `sizeFollowsDensity` (`:143`):

```java
   private boolean densityHeightOnly;
```

and the accessors after `setSizeFollowsDensity`:

```java
   public boolean isDensityHeightOnly() {
      return densityHeightOnly;
   }

   public void setDensityHeightOnly(boolean densityHeightOnly) {
      this.densityHeightOnly = densityHeightOnly;
   }
```

- [ ] **Step 4: Change the `VSDialogService` helpers**

In `readSizeFollowsDensity`, after `model.setSizeFollowsDensity(...)`:

```java
      model.setDensityHeightOnly(info.densitySizeIsHeightOnly());
```

Replace `applyDensitySize`'s body after the guard with:

```java
      info.resetSize(VizContext.of(info));
      Dimension size = info.getPixelSize();

      // a height-only type keeps the width typed in the same apply
      if(!info.densitySizeIsHeightOnly()) {
         model.setWidth(size.width);
      }

      model.setHeight(size.height);
```

In `recordAuthorSize`, replace

```java
      if(Boolean.FALSE.equals(follows) ||
         model.getWidth() != shown.width || model.getHeight() != shown.height)
```

with

```java
      if(Boolean.FALSE.equals(follows) ||
         info.isAuthorSizeChange(shown, new Dimension(model.getWidth(), model.getHeight())))
```

Add after `recordAuthorSize`:

```java
   /**
    * Write a dialog's size the way every density-sized box does: the follow checkbox first, then
    * the typed size, then the author's claim.
    */
   public void setAssemblySizeFollowingDensity(VSAssemblyInfo info, SizePositionPaneModel model,
                                               Viewsheet viewsheet)
   {
      Dimension shown = new Dimension(getAssemblySize(info, viewsheet));
      applyDensitySize(info, model);
      setAssemblySize(info, model);
      recordAuthorSize(info, model, shown);
   }
```

- [ ] **Step 5: Use `isAuthorSizeChange` in the composer resize**

`ComposerObjectService.java:148`, replace

```java
         if(info.takesDensitySize() && !size.equals(originalSize)) {
```

with

```java
         if(info.takesDensitySize() && info.isAuthorSizeChange(originalSize, size)) {
```

Change the comment above it to `// an author change; Align and Distribute resend sizes unchanged`.

- [ ] **Step 6: Wire the four single-height dialogs**

`VSInputService`, text input read (after `sizePositionPaneModel.setPositions(pos, size);`, `:750`):

```java
      VSDialogService.readSizeFollowsDensity(textInputAssemblyInfo, sizePositionPaneModel, true);
```

text input apply (`:872`), replace `dialogService.setAssemblySize(textInputAssemblyInfo, sizePositionPaneModel);` with:

```java
      dialogService.setAssemblySizeFollowingDensity(
         textInputAssemblyInfo, sizePositionPaneModel, viewsheet.getViewsheet());
```

Combo box read, after `sizePositionPaneModel.setPositions(pos, size);` (`:1832`):

```java
      VSDialogService.readSizeFollowsDensity(comboBoxAssemblyInfo, sizePositionPaneModel, true);
```

Combo box apply (`:2013`), after the `getWidth(...)` adjustment at `:2010-2012`, replace `dialogService.setAssemblySize(comboBoxAssemblyInfo, sizePositionPaneModel);` with:

```java
      dialogService.setAssemblySizeFollowingDensity(
         comboBoxAssemblyInfo, sizePositionPaneModel, viewsheet.getViewsheet());
```

Spinner read, after `sizePositionPaneModel.setPositions(pos, size);` (`:2228`):

```java
      VSDialogService.readSizeFollowsDensity(spinnerAssemblyInfo, sizePositionPaneModel, true);
```

Spinner apply (`:2291`), replace `dialogService.setAssemblySize(spinnerAssemblyInfo, sizePositionPaneModel);` with:

```java
      dialogService.setAssemblySizeFollowingDensity(
         spinnerAssemblyInfo, sizePositionPaneModel, viewsheet.getViewsheet());
```

`SubmitPropertyDialogService` read, after `sizePositionPaneModel.setPositions(pos, size);` (`:86`):

```java
      VSDialogService.readSizeFollowsDensity(submitAssemblyInfo, sizePositionPaneModel, true);
```

`SubmitPropertyDialogService` apply (`:136`), replace `dialogService.setAssemblySize(submitAssemblyInfo, sizePositionPaneModel);` with:

```java
      dialogService.setAssemblySizeFollowingDensity(
         submitAssemblyInfo, sizePositionPaneModel, viewsheet.getViewsheet());
```

In all four apply methods the runtime viewsheet variable is named `viewsheet`. Add `import inetsoft.web.viewsheet.service.VSDialogService;` to `SubmitPropertyDialogService` if it is not already imported.

- [ ] **Step 7: Run to verify they pass**

Run: `./mvnw -q test -pl core "-Dtest=SizeFollowsDensityDialogTest,ComposerObjectServiceTest,CalendarSizeFollowsDensityTest,SelectionContainerSizeFollowTest,SizeFollowsDensityReadTest" -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS. The calendar and container classes prove #6390's box behaviour is unchanged.

- [ ] **Step 8: Commit**

```bash
git -C "$WT" branch --show-current
git -C "$WT" add core/src/main/java/inetsoft/web/composer/model/vs/SizePositionPaneModel.java core/src/main/java/inetsoft/web/viewsheet/service/VSDialogService.java core/src/main/java/inetsoft/web/composer/vs/objects/controller/ComposerObjectService.java core/src/main/java/inetsoft/web/viewsheet/service/VSInputService.java core/src/main/java/inetsoft/web/composer/vs/dialog/SubmitPropertyDialogService.java core/src/test/java/inetsoft/web/viewsheet/service/SizeFollowsDensityDialogTest.java core/src/test/java/inetsoft/web/composer/vs/objects/controller/ComposerObjectServiceTest.java
git -C "$WT" commit -m "Offer follow default density for the single-height controls, and treat only a height change as the author's" -m "Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 4: Width stays editable for a height-only type in the Size & Position pane

**Files:**
- Modify: `web/projects/portal/src/app/vsobjects/model/size-position-pane-model.ts`
- Modify: `web/projects/portal/src/app/vsobjects/dialog/size-position-pane.component.ts` (`initForm` `:73-78`, `sizeFollowChanged` `:124-129`)
- Modify: `web/projects/portal/src/app/vsobjects/dialog/size-position-pane.component.html:48`
- Test: `web/projects/portal/src/app/vsobjects/dialog/size-position-pane.spec.ts`

**Interfaces:**
- Consumes: Task 3's `densityHeightOnly` on the server model, serialized as `densityHeightOnly`.

- [ ] **Step 1: Install the worktree's frontend dependencies (once)**

Run: `cd "$WT/web"` then `npm ci`
Expected: completes. Do not junction `node_modules`; Vitest fails on junctions.

- [ ] **Step 2: Write the failing tests**

Append inside the `describe` in `size-position-pane.spec.ts`:

```ts
   it("should keep width editable while a height-only size follows the density", () => {
      fixture.componentInstance.model = createModel({sizeFollowsDensity: true, densityHeightOnly: true});
      fixture.detectChanges();

      expect(fixture.componentInstance.form.controls["width"].disabled).toBeFalsy();
      expect(fixture.componentInstance.form.controls["height"].disabled).toBeTruthy();
   });

   it("should leave width editable when a height-only size checkbox is ticked", () => {
      fixture.componentInstance.model = createModel({sizeFollowsDensity: false, densityHeightOnly: true});
      fixture.detectChanges();

      fixture.componentInstance.sizeFollowChanged(true);

      expect(fixture.componentInstance.form.controls["width"].disabled).toBeFalsy();
      expect(fixture.componentInstance.form.controls["height"].disabled).toBeTruthy();
   });
```

- [ ] **Step 3: Run to verify they fail**

Run: `npx ng test portal --include="**/size-position-pane.spec.ts"`
Expected: FAIL. TypeScript first reports `densityHeightOnly` is not in `SizePositionPaneModel`.

- [ ] **Step 4: Implement**

`size-position-pane-model.ts`, after `sizeFollowsDensity?: boolean;`:

```ts
   densityHeightOnly?: boolean;
```

`size-position-pane.component.ts`, in `initForm`, the width control's `disabled` becomes:

```ts
         disabled: (!this.layoutEnabled || this.model.locked ||
                    this.model.sizeFollowsDensity === true && !this.model.densityHeightOnly)},
```

`sizeFollowChanged` becomes:

```ts
   sizeFollowChanged(follows: boolean): void {
      this.model.sizeFollowsDensity = follows;
      const enabled = this.layoutEnabled && !this.model.locked;
      this.setEnabled("width", enabled && (!follows || !!this.model.densityHeightOnly));
      this.setEnabled("height", enabled && !follows);
   }
```

`size-position-pane.component.html:48`, the Width stepper's binding becomes:

```html
                            [disabled]="!layoutEnabled || model.locked || model.sizeFollowsDensity === true && !model.densityHeightOnly"
```

- [ ] **Step 5: Run to verify they pass**

Run: `npx ng test portal --include="**/size-position-pane.spec.ts"`
Expected: PASS, including the four existing size-follow cases.

- [ ] **Step 6: Commit**

```bash
git -C "$WT" branch --show-current
git -C "$WT" add web/projects/portal/src/app/vsobjects/model/size-position-pane-model.ts web/projects/portal/src/app/vsobjects/dialog/size-position-pane.component.ts web/projects/portal/src/app/vsobjects/dialog/size-position-pane.component.html web/projects/portal/src/app/vsobjects/dialog/size-position-pane.spec.ts
git -C "$WT" commit -m "Keep Width editable while a height-only size follows the density" -m "Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 5: The option row's author flag and its resolution

**Files:**
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/ListInputVSAssemblyInfo.java` (accessors `:157-168`, `writeAttributes` `:363-373`, `parseAttributes` `:381-397`, `copyViewInfo` `:664-674`, fields `:869`)
- Modify: `core/src/main/java/inetsoft/web/viewsheet/service/VSInputService.java:252`
- Create: `core/src/test/java/inetsoft/uql/viewsheet/internal/OptionRowTest.java`
- Create: `core/src/test/java/inetsoft/web/viewsheet/service/VSInputServiceDetailHeightTest.java`

**Interfaces:**
- Produces on `ListInputVSAssemblyInfo`: `public boolean isUserCellHeight()`, `public void setUserCellHeight(boolean)`, `public int getCellHeightValue()`, `protected boolean takesDensityRow()` (false), `getCellHeight()` resolving.

- [ ] **Step 1: Write the failing tests**

Create `OptionRowTest.java` (AGPL header from Task 2):

```java
package inetsoft.uql.viewsheet.internal;

import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.asset.internal.AssetUtil;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class OptionRowTest {
   @BeforeEach
   void density() {
      SreeEnv.setProperty("viewsheet.density", "comfortable");
   }

   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.density", null);
   }

   @Test
   void aComboBoxNeverResolvesARow() {
      ComboBoxVSAssemblyInfo info = new ComboBoxVSAssemblyInfo();
      info.setVizMark(VizMark.MODERN_LIGHT);

      assertEquals(AssetUtil.defh, info.getCellHeight());
   }

   @Test
   void aSetFlagRoundTripsAndAnUnsetOneWritesNothing() throws Exception {
      ComboBoxVSAssemblyInfo clean = new ComboBoxVSAssemblyInfo();
      assertFalse(xml(clean).contains("userCellHeight"), "an unedited row keeps its saved form");

      ComboBoxVSAssemblyInfo saved = new ComboBoxVSAssemblyInfo();
      saved.setUserCellHeight(true);
      saved.setCellHeight(33);
      assertTrue(xml(saved).contains("userCellHeight=\"true\""));

      ComboBoxVSAssemblyInfo loaded = new ComboBoxVSAssemblyInfo();
      loaded.parseXML(element(saved));
      assertTrue(loaded.isUserCellHeight());
      assertEquals(33, loaded.getCellHeightValue());
   }

   @Test
   void copyCarriesTheFlag() {
      ComboBoxVSAssemblyInfo from = new ComboBoxVSAssemblyInfo();
      from.setUserCellHeight(true);
      ComboBoxVSAssemblyInfo to = new ComboBoxVSAssemblyInfo();

      assertTrue(to.copyViewInfo(from, false), "a changed flag reports a change");
      assertTrue(to.isUserCellHeight());
   }

   private static String xml(VSAssemblyInfo info) {
      StringWriter sw = new StringWriter();
      PrintWriter writer = new PrintWriter(sw);
      info.writeXML(writer);
      writer.flush();
      return sw.toString();
   }

   private static Element element(VSAssemblyInfo info) throws Exception {
      DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
      dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      Document doc = dbf.newDocumentBuilder()
         .parse(new ByteArrayInputStream(xml(info).getBytes(StandardCharsets.UTF_8)));
      return doc.getDocumentElement();
   }
}
```

Task 6 adds the checkbox and radio resolution cases, once those types take a row.

Create `VSInputServiceDetailHeightTest.java` (AGPL header):

```java
package inetsoft.web.viewsheet.service;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.test.*;
import inetsoft.uql.viewsheet.CheckBoxVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.CheckBoxVSAssemblyInfo;
import inetsoft.web.binding.handler.VSAssemblyInfoHandler;
import inetsoft.web.binding.handler.VSColumnHandler;
import inetsoft.web.binding.service.DataRefModelFactoryService;
import inetsoft.web.composer.vs.objects.controller.VSObjectPropertyService;
import inetsoft.web.composer.vs.objects.controller.VSTrapService;
import inetsoft.web.portal.controller.database.QueryManagerService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@ExtendWith(MockitoExtension.class)
@Tag("core")
class VSInputServiceDetailHeightTest {
   @Test
   void aDraggedOptionRowIsTheAuthors() throws Exception {
      Viewsheet vs = new Viewsheet();
      CheckBoxVSAssembly assembly = new CheckBoxVSAssembly(vs, "CheckBox1");
      vs.addAssembly(assembly);
      when(viewsheetService.getViewsheet(anyString(), nullable(Principal.class))).thenReturn(rvs);
      when(rvs.getViewsheet()).thenReturn(vs);
      VSInputService service = new VSInputService(
         vsObjectService, coreLifecycleService, viewsheetService, vsObjectPropertyService,
         dialogService, trapService, dataRefModelFactoryService, assemblyInfoHandler,
         columnHandler, queryManagerService);

      service.setDetailHeight("Viewsheet1", "CheckBox1", 33, null);

      CheckBoxVSAssemblyInfo info = (CheckBoxVSAssemblyInfo) assembly.getVSAssemblyInfo();
      assertEquals(33, info.getCellHeightValue());
      assertTrue(info.isUserCellHeight());
   }

   @Mock VSObjectService vsObjectService;
   @Mock CoreLifecycleService coreLifecycleService;
   @Mock ViewsheetService viewsheetService;
   @Mock VSObjectPropertyService vsObjectPropertyService;
   @Mock VSDialogService dialogService;
   @Mock VSTrapService trapService;
   @Mock DataRefModelFactoryService dataRefModelFactoryService;
   @Mock VSAssemblyInfoHandler assemblyInfoHandler;
   @Mock VSColumnHandler columnHandler;
   @Mock QueryManagerService queryManagerService;
   @Mock RuntimeViewsheet rvs;
}
```

- [ ] **Step 2: Run to verify they fail**

Run: `./mvnw -q test -pl core "-Dtest=OptionRowTest,VSInputServiceDetailHeightTest" -Dsurefire.failIfNoSpecifiedTests=false`
Expected: compilation failure, `cannot find symbol setUserCellHeight`.

- [ ] **Step 3: Implement on `ListInputVSAssemblyInfo`**

Replace the `getCellHeight()` method (`:163-168`) with:

```java
   /**
    * The option row height to draw: the tier row for a marked type that takes one, while its
    * author has not set the row and it is stored at the legacy height; the stored row otherwise.
    */
   public int getCellHeight() {
      if(!takesDensityRow() || userCellHeight || cellHeight != AssetUtil.defh ||
         getVizMark() == null)
      {
         return cellHeight;
      }

      return VSDensityDefaults.cellHeight(VizContext.of(this));
   }

   /**
    * The stored option row height.
    */
   public int getCellHeightValue() {
      return cellHeight;
   }

   /**
    * Whether the author set the option row height.
    */
   public boolean isUserCellHeight() {
      return userCellHeight;
   }

   public void setUserCellHeight(boolean userCellHeight) {
      this.userCellHeight = userCellHeight;
   }

   /**
    * Whether this type's option row follows density. Overridden by the titled option lists.
    */
   protected boolean takesDensityRow() {
      return false;
   }
```

In `writeAttributes`, after the `cellHeight` line:

```java

      if(userCellHeight) {
         writer.print(" userCellHeight=\"true\"");
      }
```

In `parseAttributes`, after the `cellHeight` parse block:

```java

      userCellHeight = "true".equals(Tool.getAttribute(elem, "userCellHeight"));
```

In `copyViewInfo`, after the `cellHeight` block:

```java

      if(userCellHeight != cinfo.userCellHeight) {
         userCellHeight = cinfo.userCellHeight;
         result = true;
      }
```

Beside the `cellHeight` field (`:869`):

```java
   private boolean userCellHeight = false;
```

- [ ] **Step 4: Flag the composer's row drag**

`VSInputService.java:252`, after `assemblyInfo.setCellHeight((int) height);`:

```java
         assemblyInfo.setUserCellHeight(true);
```

- [ ] **Step 5: Run to verify they pass**

Run: `./mvnw -q test -pl core "-Dtest=OptionRowTest,VSInputServiceDetailHeightTest,ControlHeightFollowDensityTest" -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS. Nothing takes a row yet, so `ControlHeightFollowDensityTest` is unchanged.

- [ ] **Step 6: Commit**

```bash
git -C "$WT" branch --show-current
git -C "$WT" add core/src/main/java/inetsoft/uql/viewsheet/internal/ListInputVSAssemblyInfo.java core/src/main/java/inetsoft/web/viewsheet/service/VSInputService.java core/src/test/java/inetsoft/uql/viewsheet/internal/OptionRowTest.java core/src/test/java/inetsoft/web/viewsheet/service/VSInputServiceDetailHeightTest.java
git -C "$WT" commit -m "Record an author's option row height, and resolve the row for types that follow density" -m "Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 6: Checkbox and radio button follow the lane, the row and the option box

**Files:**
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/FormControlDensity.java`
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/CheckBoxVSAssemblyInfo.java` (`getTitleHeight` `:133-136`; seed block and comment `:472-505`)
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/RadioButtonVSAssemblyInfo.java` (`getTitleHeight` `:155-158`; javadoc `:403-415`; seed block `:431-455`)
- Create: `core/src/test/java/inetsoft/uql/viewsheet/internal/OptionBoxDensityTest.java`
- Modify: `core/src/test/java/inetsoft/uql/viewsheet/internal/ControlHeightFollowDensityTest.java`
- Modify: `core/src/test/java/inetsoft/uql/viewsheet/internal/TitleLaneHeightRowTest.java:57-77`
- Modify: `core/src/test/java/inetsoft/uql/viewsheet/internal/BottomTabsReseedTest.java`

**Interfaces:**
- Consumes: Task 1's option values, Task 2's hooks, Task 5's row accessors.
- Produces: `static <T extends ListInputVSAssemblyInfo & TitledVSAssemblyInfo> int FormControlDensity.lane(T, VizContext)`, `row(T, VizContext)`, `Dimension optionBoxSize(T, VizContext)`, `void seedOptionBox(T, VizContext)`.

- [ ] **Step 1: Write the failing tests**

Create `OptionBoxDensityTest.java` (AGPL header):

```java
package inetsoft.uql.viewsheet.internal;

import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.asset.internal.AssetUtil;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Dimension;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class OptionBoxDensityTest {
   @BeforeEach
   void density() {
      SreeEnv.setProperty("viewsheet.density", "comfortable");
   }

   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.density", null);
   }

   static Stream<Supplier<ListInputVSAssemblyInfo>> types() {
      return Stream.of(CheckBoxVSAssemblyInfo::new, RadioButtonVSAssemblyInfo::new);
   }

   private static ListInputVSAssemblyInfo marked(Supplier<ListInputVSAssemblyInfo> type,
                                                 int box, int row)
   {
      ListInputVSAssemblyInfo info = type.get();
      info.setVizMark(VizMark.MODERN_LIGHT);
      info.setPixelSize(new Dimension(200, box));
      info.setCellHeight(row);
      return info;
   }

   private static void open(ListInputVSAssemblyInfo info) {
      info.seedChromeDefaults(VizContext.of(info));
   }

   private static void revert(ListInputVSAssemblyInfo info) {
      info.setVizMark(null);
      info.seedChromeDefaults(VizContext.ofTransition(null, null));
   }

   private static TitledVSAssemblyInfo titled(ListInputVSAssemblyInfo info) {
      return (TitledVSAssemblyInfo) info;
   }

   private static int rows(ListInputVSAssemblyInfo info) {
      int content = info.getPixelSize().height - titled(info).getTitleHeight();
      return Math.max(1, content / info.getCellHeight());
   }

   @ParameterizedTest
   @MethodSource("types")
   void aNewMarkedBoxIsTheLaneOverOneRowAtEveryTier(Supplier<ListInputVSAssemblyInfo> type) {
      int[][] tiers = { { 20, 20, 40 }, { 26, 24, 50 }, { 30, 28, 58 } };
      String[] modes = { "dense", "compact", "comfortable" };

      for(int i = 0; i < modes.length; i++) {
         SreeEnv.setProperty("viewsheet.density", modes[i]);
         ListInputVSAssemblyInfo info = marked(type, 2 * AssetUtil.defh, AssetUtil.defh);

         open(info);

         assertEquals(tiers[i][0], titled(info).getTitleHeight(), modes[i] + " lane");
         assertEquals(tiers[i][1], info.getCellHeight(), modes[i] + " row");
         assertEquals(tiers[i][2], info.getPixelSize().height, modes[i] + " box");
         assertEquals(1, rows(info), modes[i] + " rows");
         assertEquals(AssetUtil.defh, info.getCellHeightValue(), "no tier row is stored");
      }
   }

   @ParameterizedTest
   @MethodSource("types")
   void todaysSeededBoxIsRewrittenOnOpen(Supplier<ListInputVSAssemblyInfo> type) {
      ListInputVSAssemblyInfo info = marked(type, 60, 40);

      open(info);

      assertEquals(58, info.getPixelSize().height);
      assertEquals(AssetUtil.defh, info.getCellHeightValue());
      assertEquals(28, info.getCellHeight());
   }

   @ParameterizedTest
   @MethodSource("types")
   void todaysDenseBoxReturnsToTheLegacySize(Supplier<ListInputVSAssemblyInfo> type) {
      SreeEnv.setProperty("viewsheet.density", "dense");
      ListInputVSAssemblyInfo info = marked(type, 48, 28);

      open(info);

      assertEquals(40, info.getPixelSize().height);
      assertEquals(20, info.getCellHeight());
   }

   @ParameterizedTest
   @MethodSource("types")
   void anAuthorBoxKeepsItsSizeButLosesTodaysRow(Supplier<ListInputVSAssemblyInfo> type) {
      ListInputVSAssemblyInfo info = marked(type, 140, 40);

      open(info);

      assertEquals(140, info.getPixelSize().height);
      assertEquals(28, info.getCellHeight());
   }

   @ParameterizedTest
   @MethodSource("types")
   void revertReturnsTheLegacyBoxAndRow(Supplier<ListInputVSAssemblyInfo> type) {
      ListInputVSAssemblyInfo modern = marked(type, 40, 20);
      open(modern);
      revert(modern);

      assertEquals(40, modern.getPixelSize().height);
      assertEquals(20, titled(modern).getTitleHeight());
      assertEquals(20, modern.getCellHeight());

      ListInputVSAssemblyInfo neverOpened = marked(type, 60, 40);
      revert(neverOpened);

      assertEquals(40, neverOpened.getPixelSize().height, "today's pair reverts directly too");
      assertEquals(20, neverOpened.getCellHeightValue());
   }

   @ParameterizedTest
   @MethodSource("types")
   void aRestoreLeavesAnUnmarkedBoxAlone(Supplier<ListInputVSAssemblyInfo> type) {
      ListInputVSAssemblyInfo info = type.get();
      info.setVizMark(null);
      info.setPixelSize(new Dimension(200, 56));
      info.setCellHeight(36);

      info.seedChromeDefaults(VizContext.of((VizMark) null));

      assertEquals(56, info.getPixelSize().height);
      assertEquals(36, info.getCellHeightValue());
   }

   @ParameterizedTest
   @MethodSource("types")
   void flaggedValuesSurviveOpenDensityAndRevert(Supplier<ListInputVSAssemblyInfo> type) {
      ListInputVSAssemblyInfo info = marked(type, 60, 40);
      info.setUserSize(true);
      info.setUserCellHeight(true);

      open(info);
      SreeEnv.setProperty("viewsheet.density", "dense");
      open(info);
      revert(info);

      assertEquals(60, info.getPixelSize().height);
      assertEquals(40, info.getCellHeightValue());
   }

   @ParameterizedTest
   @MethodSource("types")
   void anAuthorLaneIsKeptAndTheBoxGrowsAroundIt(Supplier<ListInputVSAssemblyInfo> type) {
      ListInputVSAssemblyInfo info = marked(type, 40, 20);
      titled(info).setTitleHeightValue(40);
      titled(info).setUserTitleHeight(true);

      open(info);

      assertEquals(40, titled(info).getTitleHeight());
      assertEquals(68, info.getPixelSize().height);
   }

   @ParameterizedTest
   @MethodSource("types")
   void aDensityChangeMovesAFollowingBox(Supplier<ListInputVSAssemblyInfo> type) {
      ListInputVSAssemblyInfo info = marked(type, 40, 20);
      open(info);
      assertEquals(58, info.getPixelSize().height);

      SreeEnv.setProperty("viewsheet.density", "compact");
      open(info);

      assertEquals(50, info.getPixelSize().height);
   }

   @ParameterizedTest
   @MethodSource("types")
   void theSizeHooks(Supplier<ListInputVSAssemblyInfo> type) {
      ListInputVSAssemblyInfo following = marked(type, 58, 20);
      assertTrue(following.takesDensitySize());
      assertTrue(following.densitySizeIsHeightOnly());
      assertTrue(following.followsDensitySize());

      assertFalse(marked(type, 100, 20).followsDensitySize(), "an author box");

      ListInputVSAssemblyInfo flagged = marked(type, 140, 20);
      flagged.setUserSize(true);
      flagged.resetSize(VizContext.of(flagged));
      assertEquals(new Dimension(200, 58), flagged.getPixelSize());
      assertFalse(flagged.isUserSize());
   }

   @ParameterizedTest
   @MethodSource("types")
   void aFlaggedOrUnmarkedRowDoesNotResolve(Supplier<ListInputVSAssemblyInfo> type) {
      ListInputVSAssemblyInfo flagged = marked(type, 58, 20);
      flagged.setUserCellHeight(true);
      assertEquals(20, flagged.getCellHeight());

      ListInputVSAssemblyInfo unmarked = type.get();
      unmarked.setVizMark(null);
      assertEquals(20, unmarked.getCellHeight());
   }
}
```

In `TitleLaneHeightRowTest`, add to `includedTypesTakeTheDensityRow` (`:57`):

```java
      assertEquals(26, marked(new CheckBoxVSAssemblyInfo()).getTitleHeight(), "check box");
      assertEquals(26, marked(new RadioButtonVSAssemblyInfo()).getTitleHeight(), "radio button");
```

and delete `excludedTypesNeverTakeTheDensityRow` with its `@Test` (`:71-77`).

In `ControlHeightFollowDensityTest`, delete the checkbox and radio tests, which pin the doubled box and the fixed title: `checkBoxHeightScalesByItsLegacyTwoRowRatio`, `checkBoxTitleHeightStaysFixedWhileCellHeightAbsorbsTheContainerGrowth`, `checkBoxCellHeightRestoresLegacyOnRevert`, `checkBoxCellHeightIsLeftAloneWhenAlreadyResized`, `checkBoxHeightIsLeftAloneWhenAlreadyResized`, `checkBoxHeightRestoresLegacyTwoRowRatioOnRevert`, `checkBoxHeightAtAnOddValueIsUnaffectedByRevert`, the radio comment block and `radioButtonHeightScalesByItsLegacyTwoRowRatio`, `radioButtonTitleHeightStaysFixedWhileCellHeightAbsorbsTheContainerGrowth`, `radioButtonHeightRestoresLegacyTwoRowRatioOnRevert`, `radioButtonHeightIsLeftAloneWhenAlreadyResized`, `checkBoxHeightFollowsALaterDensityChange`, `radioButtonHeightFollowsALaterDensityChange` and `aDoubledControlHandSizedToATierValueIsAlsoMoved`. `OptionBoxDensityTest` covers each behaviour under the new values.

Append to `BottomTabsReseedTest`:

```java
   // today's seed left a 60px box; the open rewrites it to 58 and keeps its bottom on the strip
   @Test
   void aRestoreKeepsACheckboxWithTodaysBoxOnTheStrip() throws Exception {
      gate(true);
      SreeEnv.setProperty("viewsheet.density", "comfortable");
      Viewsheet vs = new Viewsheet();
      CheckBoxVSAssembly checkBox = new CheckBoxVSAssembly(vs, "CheckBox1");
      vs.addAssembly(checkBox);
      CheckBoxVSAssemblyInfo info = (CheckBoxVSAssemblyInfo) checkBox.getVSAssemblyInfo();
      info.setVizMark(VizMark.MODERN_LIGHT);
      info.setPixelSize(new Dimension(200, 60));
      info.setCellHeight(40);
      info.setPixelOffset(new Point(78, STRIP - 60));
      tabs(vs, true, "CheckBox1");

      StringWriter sw = new StringWriter();
      checkBox.writeState(new PrintWriter(sw), true);
      checkBox.parseState(Tool.parseXML(new StringReader(sw.toString())).getDocumentElement());

      assertEquals(58, info.getPixelSize().height, "premise: the box rule ran");
      assertEquals(new Point(78, STRIP - 58), info.getPixelOffset());
   }
```

Add `import inetsoft.uql.viewsheet.CheckBoxVSAssembly;` to `BottomTabsReseedTest`.

- [ ] **Step 2: Run to verify they fail**

Run: `./mvnw -q test -pl core "-Dtest=OptionBoxDensityTest,TitleLaneHeightRowTest,BottomTabsReseedTest" -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL. `aNewMarkedBoxIsTheLaneOverOneRowAtEveryTier` reports `expected: <20> but was: <...>` at the row assertion, and `TitleLaneHeightRowTest` reports `check box ==> expected: <26> but was: <20>`.

- [ ] **Step 3: Add the option-list rules to `FormControlDensity`**

Append inside the class:

```java
   /**
    * The lane a checkbox or radio button takes under ctx: the stored height when its author set
    * it or moved it off the legacy height, the tier lane otherwise.
    */
   static <T extends ListInputVSAssemblyInfo & TitledVSAssemblyInfo> int lane(T info,
                                                                             VizContext ctx)
   {
      int stored = info.getTitleHeightValue();
      return info.isUserTitleHeight() || stored != info.getLegacyTitleHeight() ?
         stored : VSDensityDefaults.titleHeight(ctx);
   }

   /**
    * The option row under ctx, by the same tests as the lane.
    */
   static <T extends ListInputVSAssemblyInfo & TitledVSAssemblyInfo> int row(T info,
                                                                            VizContext ctx)
   {
      int stored = info.getCellHeightValue();
      return info.isUserCellHeight() || stored != AssetUtil.defh ?
         stored : VSDensityDefaults.cellHeight(ctx);
   }

   /**
    * An option list's density size: its own width over its lane and one row.
    */
   static <T extends ListInputVSAssemblyInfo & TitledVSAssemblyInfo> Dimension optionBoxSize(
      T info, VizContext ctx)
   {
      return new Dimension(info.getPixelSize().width, lane(info, ctx) + row(info, ctx));
   }

   /**
    * The checkbox and radio rule: clear a row the earlier seed stored, then size the box. The
    * legacy branches run only on a Revert or mode switch.
    */
   static <T extends ListInputVSAssemblyInfo & TitledVSAssemblyInfo> void seedOptionBox(
      T info, VizContext ctx)
   {
      int box = info.getPixelSize().height;
      int storedRow = info.getCellHeightValue();

      if(!info.isUserCellHeight() && VSDensityDefaults.isFormerOptionRow(storedRow) &&
         (ctx.modern || ctx.transition && VSDensityDefaults.isFormerOptionBoxHeight(box) &&
            storedRow == box - info.getTitleHeightValue()))
      {
         info.setCellHeight(AssetUtil.defh);
      }

      if(info.isUserSize() || !VSDensityDefaults.isSeededOptionBoxHeight(box)) {
         return;
      }

      if(ctx.modern) {
         info.setPixelSize(optionBoxSize(info, ctx));
      }
      else if(ctx.transition) {
         info.setPixelSize(new Dimension(info.getPixelSize().width,
                                         VSDensityDefaults.optionBoxHeight(ctx)));
      }
   }
```

- [ ] **Step 4: Change `CheckBoxVSAssemblyInfo`**

`getTitleHeight()` (`:133-136`) returns:

```java
      return VSDensityDefaults.titleHeight(this, titleInfo.getTitleHeight());
```

In `seedChromeDefaults`, replace everything from the comment `// legacy default is 2 * defh (title lane + one data row); ...` (`:472`) through the closing brace of the `else if(!ctx.modern && ...)` branch (`:504`) with:

```java
      FormControlDensity.seedOptionBox(this, ctx);
```

Add after `seedChromeDefaults`:

```java
   @Override
   public boolean takesDensitySize() {
      return true;
   }

   @Override
   public boolean densitySizeIsHeightOnly() {
      return true;
   }

   @Override
   public boolean followsDensitySize() {
      return !isUserSize() && VSDensityDefaults.isSeededOptionBoxHeight(getPixelSize().height);
   }

   @Override
   protected Dimension defaultSize(VizContext ctx) {
      return FormControlDensity.optionBoxSize(this, ctx);
   }

   @Override
   protected boolean takesDensityRow() {
      return true;
   }
```

- [ ] **Step 5: Change `RadioButtonVSAssemblyInfo` the same way**

`getTitleHeight()` (`:155-158`) returns:

```java
      return VSDensityDefaults.titleHeight(this, titleInfo.getTitleHeight());
```

In the `seedChromeDefaults` javadoc (`:403-415`), delete the paragraph that starts `Legacy default is 2 * defh` and ends `which shares this exact shape.`, and the blank ` *` line before it. Replace the seed block from `// also re-fires on a density change, ...` (`:431`) through the `else if` branch's closing brace (`:454`) with:

```java
      FormControlDensity.seedOptionBox(this, ctx);
```

Add after `seedChromeDefaults`:

```java
   @Override
   public boolean takesDensitySize() {
      return true;
   }

   @Override
   public boolean densitySizeIsHeightOnly() {
      return true;
   }

   @Override
   public boolean followsDensitySize() {
      return !isUserSize() && VSDensityDefaults.isSeededOptionBoxHeight(getPixelSize().height);
   }

   @Override
   protected Dimension defaultSize(VizContext ctx) {
      return FormControlDensity.optionBoxSize(this, ctx);
   }

   @Override
   protected boolean takesDensityRow() {
      return true;
   }
```

- [ ] **Step 6: Run to verify they pass**

Run: `./mvnw -q test -pl core "-Dtest=OptionBoxDensityTest,OptionRowTest,TitleLaneHeightRowTest,BottomTabsReseedTest,ControlHeightFollowDensityTest,ControlDensitySizeTest" -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git -C "$WT" branch --show-current
git -C "$WT" add core/src/main/java/inetsoft/uql/viewsheet/internal/FormControlDensity.java core/src/main/java/inetsoft/uql/viewsheet/internal/CheckBoxVSAssemblyInfo.java core/src/main/java/inetsoft/uql/viewsheet/internal/RadioButtonVSAssemblyInfo.java core/src/test/java/inetsoft/uql/viewsheet/internal/OptionBoxDensityTest.java core/src/test/java/inetsoft/uql/viewsheet/internal/ControlHeightFollowDensityTest.java core/src/test/java/inetsoft/uql/viewsheet/internal/TitleLaneHeightRowTest.java core/src/test/java/inetsoft/uql/viewsheet/internal/BottomTabsReseedTest.java
git -C "$WT" commit -m "Give checkbox and radio button the density lane and the selection list's row" -m "Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 7: Checkbox and radio dialogs

**Files:**
- Modify: `core/src/main/java/inetsoft/web/viewsheet/service/VSDialogService.java`
- Modify: `core/src/main/java/inetsoft/web/viewsheet/service/VSInputService.java` (checkbox read `:450-458`, apply `:566-573`; radio read `:1196-1202`, apply `:1318-1325`)
- Create: `core/src/test/java/inetsoft/web/viewsheet/service/OptionHeightsDialogTest.java`

**Interfaces:**
- Consumes: Task 3's `setAssemblySizeFollowingDensity`, Task 5's row accessors, Task 6's lane.
- Produces: `public static <T extends ListInputVSAssemblyInfo & TitledVSAssemblyInfo> void VSDialogService.readOptionHeights(T, SizePositionPaneModel)` and `applyOptionHeights(T, SizePositionPaneModel)`.

- [ ] **Step 1: Write the failing tests**

Create `OptionHeightsDialogTest.java` (AGPL header):

```java
package inetsoft.web.viewsheet.service;

import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.web.composer.model.vs.SizePositionPaneModel;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Dimension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class OptionHeightsDialogTest {
   @BeforeEach
   void density() {
      SreeEnv.setProperty("viewsheet.density", "comfortable");
   }

   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.density", null);
   }

   private static CheckBoxVSAssemblyInfo checkBox(VizMark mark) {
      CheckBoxVSAssemblyInfo info = new CheckBoxVSAssemblyInfo();
      info.setVizMark(mark);
      info.setPixelSize(new Dimension(200, 58));
      return info;
   }

   private static SizePositionPaneModel read(CheckBoxVSAssemblyInfo info) {
      SizePositionPaneModel model = new SizePositionPaneModel();
      VSDialogService.readOptionHeights(info, model);
      return model;
   }

   @Test
   void readShowsTheResolvedHeightsAndOffersBothCheckboxes() {
      SizePositionPaneModel model = read(checkBox(VizMark.MODERN_LIGHT));

      assertEquals(30, model.getTitleHeight());
      assertEquals(28, model.getCellHeight());
      assertEquals(Boolean.TRUE, model.getTitleHeightFollowsDensity());
      assertEquals(Boolean.TRUE, model.getCellHeightFollowsDensity());
   }

   @Test
   void readOffersNoCheckboxWhenUnmarked() {
      SizePositionPaneModel model = read(checkBox(null));

      assertEquals(AssetUtil.defh, model.getTitleHeight());
      assertNull(model.getTitleHeightFollowsDensity());
      assertNull(model.getCellHeightFollowsDensity());
   }

   @Test
   void anUntouchedApplyAtComfortableFlagsNothing() {
      CheckBoxVSAssemblyInfo info = checkBox(VizMark.MODERN_LIGHT);
      SizePositionPaneModel model = read(info);
      model.setTitleHeightFollowsDensity(null);
      model.setCellHeightFollowsDensity(null);

      VSDialogService.applyOptionHeights(info, model);

      assertFalse(info.isUserTitleHeight());
      assertFalse(info.isUserCellHeight());
      assertEquals(AssetUtil.defh, info.getTitleHeightValue());
      assertEquals(AssetUtil.defh, info.getCellHeightValue());
   }

   @Test
   void untickingFlagsAndStoresTheTypedValues() {
      CheckBoxVSAssemblyInfo info = checkBox(VizMark.MODERN_LIGHT);
      SizePositionPaneModel model = read(info);
      model.setTitleHeightFollowsDensity(false);
      model.setTitleHeight(34);
      model.setCellHeightFollowsDensity(false);
      model.setCellHeight(32);

      VSDialogService.applyOptionHeights(info, model);

      assertTrue(info.isUserTitleHeight());
      assertEquals(34, info.getTitleHeight());
      assertTrue(info.isUserCellHeight());
      assertEquals(32, info.getCellHeight());
   }

   @Test
   void tickingClearsTheFlagsAndStoresTheLegacyValues() {
      CheckBoxVSAssemblyInfo info = checkBox(VizMark.MODERN_LIGHT);
      info.setTitleHeightValue(34);
      info.setUserTitleHeight(true);
      info.setCellHeight(32);
      info.setUserCellHeight(true);
      SizePositionPaneModel model = read(info);
      model.setTitleHeightFollowsDensity(true);
      model.setCellHeightFollowsDensity(true);

      VSDialogService.applyOptionHeights(info, model);

      assertFalse(info.isUserTitleHeight());
      assertEquals(30, info.getTitleHeight());
      assertFalse(info.isUserCellHeight());
      assertEquals(28, info.getCellHeight());
   }

   @Test
   void anUnmarkedEditIsStoredAndFlagged() {
      CheckBoxVSAssemblyInfo info = checkBox(null);
      SizePositionPaneModel model = read(info);
      model.setCellHeight(25);

      VSDialogService.applyOptionHeights(info, model);

      assertEquals(25, info.getCellHeightValue());
      assertTrue(info.isUserCellHeight());
      assertFalse(info.isUserTitleHeight(), "an untouched title is not flagged");
   }
}
```

- [ ] **Step 2: Run to verify they fail**

Run: `./mvnw -q test -pl core -Dtest=OptionHeightsDialogTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: compilation failure, `cannot find symbol readOptionHeights`.

- [ ] **Step 3: Add the helpers to `VSDialogService`**

Add `import inetsoft.uql.asset.internal.AssetUtil;`. Then, after `setAssemblySizeFollowingDensity`:

```java
   /**
    * Fill a checkbox or radio dialog's lane and row: the heights drawn, and a follow checkbox for
    * each when the assembly is marked.
    */
   public static <T extends ListInputVSAssemblyInfo & TitledVSAssemblyInfo> void readOptionHeights(
      T info, SizePositionPaneModel model)
   {
      boolean marked = info.getVizMark() != null;
      model.setTitleHeight(VSDensityDefaults.titleHeight(info, info.getTitleHeightValue()));
      model.setTitleHeightFollowsDensity(marked ? !info.isUserTitleHeight() : null);
      int cellHeight = info.getCellHeight();
      model.setCellHeight(cellHeight <= 0 ? AssetUtil.defh : cellHeight);
      model.setCellHeightFollowsDensity(marked ? !info.isUserCellHeight() : null);
   }

   /**
    * Apply a checkbox or radio dialog's lane and row. No answer stores a value only when it
    * differs from the height drawn, so an untouched field never claims it.
    */
   public static <T extends ListInputVSAssemblyInfo & TitledVSAssemblyInfo> void applyOptionHeights(
      T info, SizePositionPaneModel model)
   {
      Boolean titleFollows = model.getTitleHeightFollowsDensity();

      if(Boolean.TRUE.equals(titleFollows)) {
         info.setUserTitleHeight(false);
         info.setTitleHeightValue(info.getLegacyTitleHeight());
      }
      else if(Boolean.FALSE.equals(titleFollows) || model.getTitleHeight() !=
         VSDensityDefaults.titleHeight(info, info.getTitleHeightValue()))
      {
         info.setUserTitleHeight(true);
         info.setTitleHeightValue(model.getTitleHeight());
      }

      Boolean rowFollows = model.getCellHeightFollowsDensity();

      if(Boolean.TRUE.equals(rowFollows)) {
         info.setUserCellHeight(false);
         info.setCellHeight(AssetUtil.defh);
      }
      else if(Boolean.FALSE.equals(rowFollows) || model.getCellHeight() != info.getCellHeight()) {
         info.setUserCellHeight(true);
         info.setCellHeight(model.getCellHeight());
      }
   }
```

- [ ] **Step 4: Wire the two dialogs**

`VSInputService` checkbox read: replace

```java
      sizePositionPaneModel.setTitleHeight(checkBoxAssemblyInfo.getTitleHeightValue());
```

(`:454`) with

```java
      VSDialogService.readOptionHeights(checkBoxAssemblyInfo, sizePositionPaneModel);
      VSDialogService.readSizeFollowsDensity(checkBoxAssemblyInfo, sizePositionPaneModel, true);
```

and delete the two `cellHeight` lines that follow `setContainer` (`:457-458`).

Checkbox apply: replace `dialogService.setAssemblySize(checkBoxAssemblyInfo, sizePositionPaneModel);` (`:566`) with

```java
      dialogService.setAssemblySizeFollowingDensity(
         checkBoxAssemblyInfo, sizePositionPaneModel, viewsheet.getViewsheet());
```

and replace the title `if` block and the `setCellHeight` line (`:569-573`) with

```java
      VSDialogService.applyOptionHeights(checkBoxAssemblyInfo, sizePositionPaneModel);
```

Radio read (`:1200-1202`): replace the `setTitleHeight` line and the two `cellHeight` lines with the same two calls on `radioButtonAssemblyInfo`. Radio apply: the same two replacements at `:1318` and `:1321-1325`.

- [ ] **Step 5: Run to verify they pass**

Run: `./mvnw -q test -pl core "-Dtest=OptionHeightsDialogTest,SizeFollowsDensityDialogTest,VSInputServiceDetailHeightTest" -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git -C "$WT" branch --show-current
git -C "$WT" add core/src/main/java/inetsoft/web/viewsheet/service/VSDialogService.java core/src/main/java/inetsoft/web/viewsheet/service/VSInputService.java core/src/test/java/inetsoft/web/viewsheet/service/OptionHeightsDialogTest.java
git -C "$WT" commit -m "Offer follow default density for the checkbox and radio lane, row and size" -m "Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 8: Whole-branch verification and the manual checks

**Files:**
- Create (untracked, as earlier slices): `docs/superpowers/plans/2026-10-09-form-control-density-manual-tests.md`

- [ ] **Step 1: Run every related class**

Run: `./mvnw -q test -pl core "-Dtest=VSDensityDefaultsTest,ControlDensitySizeTest,ControlHeightFollowDensityTest,OptionRowTest,OptionBoxDensityTest,TitleLaneHeightRowTest,AuthorSizeFlagTest,BottomTabsReseedTest,SizeFollowsDensityDialogTest,SizeFollowsDensityReadTest,SelectionContainerSizeFollowTest,CalendarSizeFollowsDensityTest,OptionHeightsDialogTest,VSInputServiceDetailHeightTest,ComposerObjectServiceTest,SelectionDensitySizeTest,ContainerDensitySizeTest,CalendarDensitySizeTest,RangeSliderTitleLaneTest" -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS. Report the per-class counts from `core/target/surefire-reports`.

- [ ] **Step 2: Run the full core suite**

Run: `./mvnw -q test -pl core`
Expected: PASS apart from the two known environment failures (`RelFaultInjectionTest` is flaky; `SetTableLensMergedTableFailureTest` needs POSIX permissions and fails on Windows). Any other failure is this branch's: read its report before going further.

- [ ] **Step 3: Write the manual checks doc**

One check per bullet of spec §9.2's "After" list, each naming the fixture assembly from Task 0's README, the action, and the expected number: the default boxes 40 / 50 / 58 and controls 24 / 28 / 30 by tier; the widened text input still following; the taller combo box fixed at 40; ticking returning a box to its tier with Width still editable; Revert keeping the author sizes and returning the rest to 20 / 40; viewer against PDF, PNG and HTML for the lane and rows; the legacy copy identical to its baselines; the bottom-tabs children on their strip after open and after a density change.

- [ ] **Step 4: Hand over**

Tell the user the server needs a rebuild from `feature-form-control-density` (the shared checkout must be switched to this branch for that, after checking no peer session is using it), then run the checks doc against Task 0's baselines. Do not commit the checks doc.
