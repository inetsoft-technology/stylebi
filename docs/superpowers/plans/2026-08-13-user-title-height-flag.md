# userTitleHeight Flag Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a persisted boolean that distinguishes an author-set title height from the default one, so a
later change can substitute a density-derived title height without overriding heights authors chose.

**Architecture:** `TitleInfo` gains a `userTitleHeight` field written to and parsed from the assembly XML.
For files saved before the field existed it is derived on parse by comparing the stored height against the
owning type's own default, which `TitledVSAssemblyInfo` exposes as a `default` method that
`CalendarVSAssemblyInfo` overrides. The flag is stamped explicitly at each call site that represents author
intent, never inside the setter. Nothing reads the flag yet — this change is behaviour-neutral by design.

**Tech Stack:** Java 21, JUnit 5 with the Spring test harness (`@SreeHome`, `BaseTestConfiguration`), Maven.

**Spec:** `docs/superpowers/specs/lookfeel/chart-card-anchored-strip-lane-decisions.md`, section "What this
costs to build" — this is item N in `docs/superpowers/specs/lookfeel/chart-card-roadmap.md`'s dependency
picture. The precedent it mirrors is `userDataRowHeight`, added in 13.3.

**Branch:** continue on `viz-updates`, the branch carrying the rest of this track.

## Global Constraints

- **The flag is design-time.** It qualifies `titleHeightValue` (the `dValue`), never the runtime `rValue`.
  `resetRuntimeValues()` must not touch it.
- **This change alters no behaviour.** Nothing reads `isUserTitleHeight()` when the plan is complete. Any
  task that changes what a user sees has gone wrong.
- **Never stamp inside `setTitleHeightValue`.** The setter stays pure; each author-intent call site stamps
  explicitly on the following line. This matches `userDataRowHeight`.
- **Comments are a short clause, not a full sentence.** Do not reference ticket numbers, PR numbers, design
  documents or this plan in source comments.
- **Minimal scope.** Leave the existing title-height read order exactly as it is.
  **Corrected 2026-08-13:** an earlier revision of this line claimed `writeAttributes` writes both
  `titleHeight` and `titleHeightValue` while `parseAttributes` reads only `titleHeight`, and called that an
  asymmetry to leave alone. That was wrong. `VSUtil.getAttributeStr(elem, prop, def)`
  (`VSUtil.java:2984-2989`) reads `prop + "Value"` first, then `prop`, then the default — so the existing
  read already consults `titleHeightValue` and prefers it. There is no asymmetry. Do not "repair" anything
  here, and detect whether a height was recorded with `getAttributeStr(elem, "titleHeight", null)` rather
  than `Tool.getAttribute`, or a file carrying only `titleHeightValue` reads as having no height.
- **The flag stays out of `equals()`**, matching `TableDataVSAssemblyInfo`, so provenance never becomes a
  change-detection trigger.
- Run `./mvnw test -pl core -Dtest=<TestClass>` for a single test class; `./mvnw test -pl core` for the
  module. On Windows use `.\mvnw.cmd`.
- Commit after each task. Message prefix `feat(viewsheet):`, matching recent commits on this branch.

---

## File structure

| File | Responsibility | Task |
|---|---|---|
| `core/src/main/java/inetsoft/uql/viewsheet/internal/TitleInfo.java` | Owns the field, accessors, XML write, XML parse and the derive | 1, 2 |
| `core/src/test/java/inetsoft/uql/viewsheet/internal/TitleInfoTest.java` | Round-trip, derive and default-state tests | 1, 2, 3 |
| `core/src/main/java/inetsoft/uql/viewsheet/internal/TitledVSAssemblyInfo.java` | Declares the per-type default height and the flag accessors | 3 |
| `core/src/main/java/inetsoft/uql/viewsheet/internal/CalendarVSAssemblyInfo.java` | Overrides that default to 36; delegates the flag; passes the default on parse | 3 |
| Seven other `*VSAssemblyInfo.java` | Delegate the flag; pass their default height on parse | 3 |
| Nine `*PropertyDialogService.java` | Stamp on the Size and Position pane's write | 4 |
| `core/src/main/java/inetsoft/web/composer/vs/objects/controller/ComposerObjectService.java` | Stamp on the title-bar drag gesture | 5 |
| `core/src/main/java/inetsoft/web/viewsheet/service/VSInputService.java` | Stamp for check box and radio button | 5 |
| `core/src/main/java/inetsoft/web/vswizard/handler/SyncTableHandler.java` | Propagate the source's flag, do not stamp | 6 |
| `core/src/main/java/inetsoft/uql/viewsheet/internal/VSAssemblyInfo.java` | Stamp when a customer stylesheet defines the height | 7 |

Why `TitleInfo` and not the assembly infos: `TitledVSAssemblyInfo` is an interface, and its eight
implementors have five different parents (`SelectionVSAssemblyInfo`, `DataVSAssemblyInfo`,
`ListInputVSAssemblyInfo`, `ContainerVSAssemblyInfo`, `MaxModeSelectionVSAssemblyInfo`). `TitleInfo` is the
one object all eight delegate to and where `titleHeight` already lives.

---

## Task 1: The field, its accessors and the XML round-trip

**Files:**
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/TitleInfo.java`
- Test: `core/src/test/java/inetsoft/uql/viewsheet/internal/TitleInfoTest.java` (create)

**Interfaces:**
- Consumes: nothing.
- Produces: `TitleInfo.isUserTitleHeight()` returning `boolean`, `TitleInfo.setUserTitleHeight(boolean)`.
  The XML attribute is named `userTitleHeight`. Later tasks call both.

- [ ] **Step 1: Write the failing test**

Create `core/src/test/java/inetsoft/uql/viewsheet/internal/TitleInfoTest.java`. Copy the licence header
verbatim from `VSDensityDefaultsTest.java` (the same 17-line AGPL block every file in this tree carries),
then:

```java
package inetsoft.uql.viewsheet.internal;

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.util.Tool;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.Document;

import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class TitleInfoTest {
   @Test
   void newTitleInfoIsNotUserSet() {
      assertFalse(new TitleInfo().isUserTitleHeight());
      assertFalse(new TitleInfo("a title").isUserTitleHeight());
   }

   @Test
   void userTitleHeightSurvivesRoundTrip() throws Exception {
      assertTrue(roundTrip(28, true).isUserTitleHeight(),
                 "an author-set height must stay author-set across a save and load");
      assertFalse(roundTrip(28, false).isUserTitleHeight(),
                  "a height not marked author-set must not become author-set on load");
   }

   /** Write a TitleInfo to XML and parse it back. */
   private static TitleInfo roundTrip(int height, boolean userSet) throws Exception {
      TitleInfo written = new TitleInfo("a title");
      written.setTitleHeightValue(height);
      written.setUserTitleHeight(userSet);

      StringWriter buf = new StringWriter();
      PrintWriter writer = new PrintWriter(buf);
      written.writeXML(writer);
      writer.flush();

      Document doc = Tool.parseXML(new StringReader("<assembly>" + buf + "</assembly>"));
      TitleInfo parsed = new TitleInfo();
      parsed.parseXML(doc.getDocumentElement());

      return parsed;
   }
}
```

The `<assembly>` wrapper is required: `TitleInfo.parseXML` looks for a `titleInfo` child of the element it
is given, and `writeXML` emits that `titleInfo` element itself.

- [ ] **Step 2: Run the test to verify it fails**

Run: `./mvnw test -pl core -Dtest=TitleInfoTest`
Expected: compilation failure — `cannot find symbol: method isUserTitleHeight()`.

- [ ] **Step 3: Add the field and accessors**

In `TitleInfo.java`, add the accessors after `setTitleHeightValue` (which ends at `:177`):

```java
   /**
    * Check whether the title height was set by the author rather than left at the default.
    */
   public boolean isUserTitleHeight() {
      return userTitleHeight;
   }

   /**
    * Set whether the title height was set by the author.
    */
   public void setUserTitleHeight(boolean userTitleHeight) {
      this.userTitleHeight = userTitleHeight;
   }
```

Add the field beside the other private fields at the bottom of the class, after `padding`:

```java
   private boolean userTitleHeight = false;
```

Do not add it to `equals()`, `clone()` or `resetRuntimeValues()`. `clone()` calls `super.clone()`, which
copies a primitive `boolean` already.

- [ ] **Step 4: Write and parse the attribute**

In `writeAttributes`, add a line after the `titleHeightValue` line:

```java
      writer.print(" userTitleHeight=\"" + isUserTitleHeight() + "\"");
```

In `parseAttributes`, add a line after the `setTitleHeightValue(...)` line:

```java
      setUserTitleHeight("true".equalsIgnoreCase(Tool.getAttribute(elem, "userTitleHeight")));
```

`Tool.getAttribute` returns `null` for a missing attribute and `"true".equalsIgnoreCase(null)` is `false`,
so a file saved before this change parses as not-user-set. Task 2 replaces that fallback with the derive.

- [ ] **Step 5: Run the test to verify it passes**

Run: `./mvnw test -pl core -Dtest=TitleInfoTest`
Expected: PASS, 2 tests.

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/inetsoft/uql/viewsheet/internal/TitleInfo.java \
        core/src/test/java/inetsoft/uql/viewsheet/internal/TitleInfoTest.java
git commit -m "feat(viewsheet): persist whether a title height was author-set"
```

---

## Task 2: Derive the flag for files saved before it existed

**Files:**
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/TitleInfo.java`
- Test: `core/src/test/java/inetsoft/uql/viewsheet/internal/TitleInfoTest.java`

**Interfaces:**
- Consumes: `isUserTitleHeight()` / `setUserTitleHeight(boolean)` from Task 1.
- Produces: `TitleInfo.parseXML(Element elem, int defaultTitleHeight)` and
  `TitleInfo.parseAttributes(Element elem, int defaultTitleHeight)`. The existing no-argument forms remain
  and delegate with `AssetUtil.defh`. Task 3 calls the two-argument `parseXML`.

- [ ] **Step 1: Write the failing test**

Add to `TitleInfoTest`:

```java
   @Test
   void legacyFileDerivesFromTheDefaultHeight() throws Exception {
      assertFalse(parseLegacy(20, AssetUtil.defh).isUserTitleHeight(),
                  "a height equal to the type's default is not author-set");
      assertTrue(parseLegacy(28, AssetUtil.defh).isUserTitleHeight(),
                 "a height differing from the type's default is author-set");
   }

   @Test
   void legacyFileDerivesAgainstANonStandardDefault() throws Exception {
      assertFalse(parseLegacy(36, 36).isUserTitleHeight(),
                  "a calendar left at its own 36px default is not author-set");
      assertTrue(parseLegacy(20, 36).isUserTitleHeight(),
                 "a calendar moved off its 36px default is author-set");
   }

   @Test
   void anExplicitAttributeBeatsTheDerive() throws Exception {
      Document doc = Tool.parseXML(new StringReader(
         "<assembly><titleInfo titleHeight=\"28\" userTitleHeight=\"false\"/></assembly>"));
      TitleInfo parsed = new TitleInfo();
      parsed.parseXML(doc.getDocumentElement(), AssetUtil.defh);

      assertFalse(parsed.isUserTitleHeight(),
                  "a stored false must not be re-derived to true");
   }

   /** Parse a titleInfo element carrying no userTitleHeight attribute. */
   private static TitleInfo parseLegacy(int height, int defaultHeight) throws Exception {
      Document doc = Tool.parseXML(new StringReader(
         "<assembly><titleInfo titleHeight=\"" + height + "\"/></assembly>"));
      TitleInfo parsed = new TitleInfo();
      parsed.parseXML(doc.getDocumentElement(), defaultHeight);

      return parsed;
   }
```

Add `import inetsoft.uql.asset.internal.AssetUtil;` to the test's imports.

- [ ] **Step 2: Run the test to verify it fails**

Run: `./mvnw test -pl core -Dtest=TitleInfoTest`
Expected: compilation failure — `method parseXML cannot be applied to given types` (two arguments).

- [ ] **Step 3: Add the overloads and the derive**

In `TitleInfo.java`, replace the existing `parseXML(Element)` body so it delegates, and add the new form.
The method is `final`; keep both `final`:

```java
   @Override
   public final void parseXML(Element elem) throws Exception {
      parseXML(elem, AssetUtil.defh);
   }

   /**
    * Method to parse an xml segment.
    * @param elem the specified xml element.
    * @param defaultTitleHeight the owning type's default title height.
    */
   public final void parseXML(Element elem, int defaultTitleHeight) throws Exception {
      Element node = Tool.getChildNodeByTagName(elem, "titleInfo");

      if(node != null) {
         parseAttributes(node, defaultTitleHeight);
         parseContents(node);
      }
      else {// for bc
         node = Tool.getChildNodeByTagName(elem, "titleValue");
         setTitleValue(node == null ? null : Tool.getValue(node));
         setTitleVisibleValue(Tool.getAttribute(elem, "titleVisible"));
         setTitleHeightValue(Integer.parseInt(
            VSUtil.getAttributeStr(elem, "titleHeight", AssetUtil.defh + "")));
         setUserTitleHeight(getTitleHeightValue() != defaultTitleHeight);
      }
   }
```

Do the same for `parseAttributes`, replacing the Task 1 line with the derive:

```java
   protected void parseAttributes(Element elem) {
      parseAttributes(elem, AssetUtil.defh);
   }

   /**
    * Parse attributes.
    * @param elem the specified xml element.
    * @param defaultTitleHeight the owning type's default title height.
    */
   protected void parseAttributes(Element elem, int defaultTitleHeight) {
      setTitleVisibleValue(Tool.getAttribute(elem, "titleVisibleValue"));
      setTitleHeightValue(Integer.parseInt(
         VSUtil.getAttributeStr(elem, "titleHeight", AssetUtil.defh + "")));
      padding.parse(Tool.getAttribute(elem, "padding"));

      // absent in files saved before the flag existed; derive from the type's default
      String prop = VSUtil.getAttributeStr(elem, "userTitleHeight",
                                           (getTitleHeightValue() != defaultTitleHeight) + "");
      setUserTitleHeight("true".equalsIgnoreCase(prop));
   }
```

`getTitleHeightValue()` is read after `setTitleHeightValue`, so it reflects the value just parsed. This
mirrors `TableDataVSAssemblyInfo:997`.

- [ ] **Step 4: Run the test to verify it passes**

Run: `./mvnw test -pl core -Dtest=TitleInfoTest`
Expected: PASS, 5 tests (9 after this task's review-loop fix; see the ledger).

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/inetsoft/uql/viewsheet/internal/TitleInfo.java \
        core/src/test/java/inetsoft/uql/viewsheet/internal/TitleInfoTest.java
git commit -m "feat(viewsheet): derive the author-set title height flag for older files"
```

---

## Task 3: Expose the flag and each type's default on the assembly infos

**Files:**
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/TitledVSAssemblyInfo.java`
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/CalendarVSAssemblyInfo.java` (delegate,
  override, parse site at `:918`)
- Modify, delegate and parse site each: `ChartVSAssemblyInfo.java:1478`, `CheckBoxVSAssemblyInfo.java:339`,
  `CurrentSelectionVSAssemblyInfo.java:440`, `RadioButtonVSAssemblyInfo.java:316`,
  `SelectionBaseVSAssemblyInfo.java:641`, `TableDataVSAssemblyInfo.java:1132`,
  `TimeSliderVSAssemblyInfo.java:633`
- Test: `core/src/test/java/inetsoft/uql/viewsheet/internal/TitleInfoTest.java`

**Interfaces:**
- Consumes: `TitleInfo.parseXML(Element, int)` from Task 2 and the accessors from Task 1.
- Produces: on `TitledVSAssemblyInfo` — `getDefaultTitleHeight()` returning `int` and defaulting to
  `AssetUtil.defh`; `isUserTitleHeight()` returning `boolean`; `setUserTitleHeight(boolean)`. Tasks 4
  through 7 call the latter two. The eventual title-height row calls the first.

Two facts settle the shape of this task. All eight infos reach `TitledVSAssemblyInfo` —
`CalendarVSAssemblyInfo`, `ChartVSAssemblyInfo` and `TimeSliderVSAssemblyInfo` declare it directly, the
other five through `CompositeVSAssemblyInfo` or `CompoundVSAssemblyInfo`, both of which extend it — so a
`default` method reaches all eight. And each info holds its `TitleInfo` in a **private** field with no
`getTitleInfo()` accessor anywhere in the tree, so the flag is only reachable from a call site through a
per-info delegate, exactly as `setTitleHeightValue` already is.

- [ ] **Step 1: Write the failing test**

Add to `TitleInfoTest`:

```java
   @Test
   void eachTypeDeclaresItsOwnDefaultTitleHeight() {
      assertEquals(36, new CalendarVSAssemblyInfo().getDefaultTitleHeight(),
                   "calendar seeds 36 rather than the shared default");
      assertEquals(AssetUtil.defh, new ChartVSAssemblyInfo().getDefaultTitleHeight(),
                   "every other titled type uses the shared default");
   }
```

**One test, deliberately.** An earlier revision of this step also parsed a whole `CalendarVSAssemblyInfo`
from a minimal `<assembly><titleInfo/></assembly>` fragment end to end. That was dropped: `parseXML` on a
concrete assembly info runs the full `parseAttributes` + `parseContents` chain for that type, and
discovering the minimum viable XML for two different assembly types is open-ended work outside this task.
Task 2 already covers the derive against a non-standard baseline directly, at the `TitleInfo` level
(`parseXML(elem, 36)`), which is the same arithmetic this task wires up.

What that leaves unverified by a unit test is the wiring itself — that each of the eight parse sites
passes `getDefaultTitleHeight()` rather than nothing. Step 6 below is an exact-count check for it, and
Task 8 re-checks it. If a site is typed `titleInfo.parseXML(elem)` the count catches it.

- [ ] **Step 2: Run the test to verify it fails**

Run: `./mvnw test -pl core -Dtest=TitleInfoTest`
Expected: compilation failure — `cannot find symbol: method getDefaultTitleHeight()`.

- [ ] **Step 3: Declare all three methods on the interface**

In `TitledVSAssemblyInfo.java`, add beside the existing `setTitleHeightValue` declaration (`:101`):

```java
   /**
    * Get the default title height for this assembly type.
    */
   default int getDefaultTitleHeight() {
      return AssetUtil.defh;
   }

   /**
    * Check whether the title height was set by the author.
    */
   public boolean isUserTitleHeight();

   /**
    * Set whether the title height was set by the author.
    */
   public void setUserTitleHeight(boolean user);
```

Add `import inetsoft.uql.asset.internal.AssetUtil;` if the interface does not already import it.

- [ ] **Step 4: Add the delegate to all eight infos**

In each of the eight infos, beside its existing `setTitleHeightValue` delegate, add:

```java
   @Override
   public boolean isUserTitleHeight() {
      return titleInfo.isUserTitleHeight();
   }

   @Override
   public void setUserTitleHeight(boolean user) {
      titleInfo.setUserTitleHeight(user);
   }
```

The existing delegates are at `CalendarVSAssemblyInfo:647`, `ChartVSAssemblyInfo:2660`,
`CheckBoxVSAssemblyInfo:152`, `CurrentSelectionVSAssemblyInfo:223`, `RadioButtonVSAssemblyInfo:174`,
`SelectionBaseVSAssemblyInfo:317`, `TableDataVSAssemblyInfo:259`, `TimeSliderVSAssemblyInfo:446`.

- [ ] **Step 5: Override the default height for calendar**

In `CalendarVSAssemblyInfo.java`, near `initDefaultFormat` (which seeds the same 36 at `:91`):

```java
   @Override
   public int getDefaultTitleHeight() {
      return 36;
   }
```

Calendar is the only override. If a second type ever seeds its own height in `initDefaultFormat`, it needs
one too.

- [ ] **Step 6: Pass the default at all eight parse sites**

In each of the eight infos, change `titleInfo.parseXML(elem);` to:

```java
      titleInfo.parseXML(elem, getDefaultTitleHeight());
```

Change all eight even though only calendar differs today, so a new titled type is correct without a second
edit.

Then verify the count, since no unit test covers this wiring:

```bash
grep -rn "titleInfo.parseXML(elem, getDefaultTitleHeight())" \
  core/src/main/java/inetsoft/uql/viewsheet/internal/ | wc -l
grep -rn "titleInfo.parseXML(elem)" \
  core/src/main/java/inetsoft/uql/viewsheet/internal/ | wc -l
```

Expected: `8` and `0`. A non-zero second count is a site you missed.

- [ ] **Step 7: Run the tests to verify they pass**

Run: `./mvnw test -pl core -Dtest=TitleInfoTest`
Expected: PASS, 10 tests — 9 carried from Tasks 1 and 2, plus this task's one.

- [ ] **Step 8: Commit**

```bash
git add core/src/main/java/inetsoft/uql/viewsheet/internal/ \
        core/src/test/java/inetsoft/uql/viewsheet/internal/TitleInfoTest.java
git commit -m "feat(viewsheet): derive the title height flag against each type's own default"
```

---

## Task 4: Stamp on the property dialogs

**Files:**
- Modify, one line each: `core/src/main/java/inetsoft/web/composer/vs/dialog/CalcTablePropertyDialogService.java:375`,
  `CalendarPropertyDialogService.java:236`, `ChartPropertyDialogService.java:410`,
  `CrosstabPropertyDialogService.java:298`, `RangeSliderPropertyDialogService.java:261`,
  `SelectionContainerPropertyDialogService.java:149`, `SelectionListPropertyDialogService.java:217`,
  `SelectionTreePropertyDialogService.java:243`, `TableViewPropertyDialogService.java:207`

**Interfaces:**
- Consumes: `setUserTitleHeight(boolean)` on `TitledVSAssemblyInfo` from Task 3.
- Produces: nothing later tasks use.

Each site already reads the Size and Position pane, which is a person typing a number. Every one of the
nine has the same shape:

```java
      <info>.setTitleHeightValue(sizePositionPaneModel.getTitleHeight());
```

- [ ] **Step 1: Add the stamp at each of the nine sites**

Immediately after each `setTitleHeightValue` line, add, using that site's own receiver variable name:

```java
      <info>.setUserTitleHeight(true);
```

The receiver names differ per file — `calcTableAssemblyInfo`, `info`, `assemblyInfo`,
`selectionContainerAssemblyInfo`, `selectionListAssemblyInfo`, `streeInfo`, `tableAssemblyInfo`. Match the
line above rather than renaming anything.

Task 3 added the delegate to all eight infos and declared it on `TitledVSAssemblyInfo`, so the method is
reachable at every one of these sites without further plumbing.

- [ ] **Step 2: Verify it compiles**

Run: `./mvnw -q compile -pl core`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Verify every site was covered**

Run:

```bash
grep -rn -A1 "setTitleHeightValue" core/src/main/java/inetsoft/web/composer/vs/dialog/ \
  | grep -c "setUserTitleHeight"
```

Expected: `9`. A lower number means a site was missed — find it and add the stamp.

- [ ] **Step 4: Run the module tests**

Run: `./mvnw test -pl core -Dtest=TitleInfoTest`
Expected: PASS, 10 tests. No behaviour changed, so nothing else should move.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/inetsoft/web/composer/vs/dialog/ \
        core/src/main/java/inetsoft/uql/viewsheet/internal/
git commit -m "feat(viewsheet): mark a title height set from the property dialog as author-set"
```

---

## Task 5: Stamp on the title-bar drag and the two input dialogs

**Files:**
- Modify: `core/src/main/java/inetsoft/web/composer/vs/objects/controller/ComposerObjectService.java:444`
- Modify: `core/src/main/java/inetsoft/web/viewsheet/service/VSInputService.java:491` and `:1240`

**Interfaces:**
- Consumes: `setUserTitleHeight(boolean)` on `TitledVSAssemblyInfo` from Task 3.
- Produces: nothing later tasks use.

`ComposerObjectService:444` handles the title-height event — a person dragging the title bar's edge in the
composer. It is the direct analogue of `ComposerVSTableService:962` and `:973`, where the row-resize
gesture stamps `userHeaderRowHeight` and `userDataRowHeight`. It is the most direct expression of author
intent in the set and it is not a property-dialog service, so it is easy to miss.

`VSInputService:491` and `:1240` are the check box and radio button property dialogs. Those two types are
excluded from the eventual title-height row, so nothing will read their flag — stamp them anyway, so the
value is correct if they are ever included and so no site is inconsistent with its neighbours.

- [ ] **Step 1: Stamp the composer drag**

In `ComposerObjectService.java`, the existing block reads:

```java
      if(assembly instanceof TitledVSAssembly) {
         ((TitledVSAssemblyInfo) assembly.getInfo()).setTitleHeightValue(titleHeight);
```

Change it to stamp on the same cast:

```java
      if(assembly instanceof TitledVSAssembly) {
         TitledVSAssemblyInfo titledInfo = (TitledVSAssemblyInfo) assembly.getInfo();
         titledInfo.setTitleHeightValue(titleHeight);
         titledInfo.setUserTitleHeight(true);
```

Leave the rest of the block unchanged.

- [ ] **Step 2: Stamp the two input dialogs**

In `VSInputService.java`, after `:491`:

```java
      checkBoxAssemblyInfo.setUserTitleHeight(true);
```

and after `:1240`:

```java
      radioButtonAssemblyInfo.setUserTitleHeight(true);
```

- [ ] **Step 3: Verify it compiles**

Run: `./mvnw -q compile -pl core`
Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add core/src/main/java/inetsoft/web/composer/vs/objects/controller/ComposerObjectService.java \
        core/src/main/java/inetsoft/web/viewsheet/service/VSInputService.java
git commit -m "feat(viewsheet): mark a dragged title height as author-set"
```

---

## Task 6: Carry the flag through the wizard's table sync

**Files:**
- Modify: `core/src/main/java/inetsoft/web/vswizard/handler/SyncTableHandler.java:57`

**Interfaces:**
- Consumes: `isUserTitleHeight()` and `setUserTitleHeight(boolean)` on `TitledVSAssemblyInfo` from Task 3.
- Produces: nothing later tasks use.

This site copies a title height from one info to another inside the visualization wizard. It is a
propagation, not an author action — stamping `true` here would mark every wizard-created table as
author-set and permanently exclude it from the eventual density row. Copy the source's flag instead.

- [ ] **Step 1: Write the failing test**

Add to `TitleInfoTest`:

```java
   @Test
   void copyingATitleHeightCarriesItsProvenance() {
      TitleInfo authorSet = new TitleInfo();
      authorSet.setTitleHeightValue(28);
      authorSet.setUserTitleHeight(true);

      TitleInfo target = new TitleInfo();
      target.setTitleHeightValue(authorSet.getTitleHeightValue());
      target.setUserTitleHeight(authorSet.isUserTitleHeight());

      assertTrue(target.isUserTitleHeight(),
                 "a copied author-set height stays author-set");

      TitleInfo untouched = new TitleInfo();
      TitleInfo copy = new TitleInfo();
      copy.setTitleHeightValue(untouched.getTitleHeightValue());
      copy.setUserTitleHeight(untouched.isUserTitleHeight());

      assertFalse(copy.isUserTitleHeight(),
                  "copying a default height must not manufacture author intent");
   }
```

- [ ] **Step 2: Run the test to verify it passes already**

Run: `./mvnw test -pl core -Dtest=TitleInfoTest`
Expected: PASS, 11 tests. This test documents the contract Step 3 implements at the call site; the accessors
it exercises already exist, so it passes immediately. That is expected — the failing part of this task is
the call site, which has no unit-testable seam.

- [ ] **Step 3: Propagate at the call site**

In `SyncTableHandler.java`, after `:57`:

```java
      targetInfo.setUserTitleHeight(sourceInfo.isUserTitleHeight());
```

Do not write `setUserTitleHeight(true)` here.

- [ ] **Step 4: Verify it compiles**

Run: `./mvnw -q compile -pl core`
Expected: BUILD SUCCESS.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/inetsoft/web/vswizard/handler/SyncTableHandler.java \
        core/src/test/java/inetsoft/uql/viewsheet/internal/TitleInfoTest.java
git commit -m "feat(viewsheet): carry title height provenance through the wizard sync"
```

---

## Task 7: Stamp a stylesheet-defined title height

**Files:**
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/VSAssemblyInfo.java:1441-1445`

**Interfaces:**
- Consumes: `setUserTitleHeight(boolean)` on `TitledVSAssemblyInfo` from Task 3.
- Produces: nothing.

`setCSSDefaults()` sets the title height when the customer's stylesheet defines one. That stylesheet is
customer-authored: no `format.css` ships with the product, nothing in the codebase creates or writes one,
and the only stylesheet that does ship — `core/src/main/resources/inetsoft/util/css/defaults.css`, loaded
at `CSSDictionary.init()` — declares nothing but `color`, so it can never make `isHeightDefined` true.
A height reaching this branch is therefore always a customer's deliberate choice, and it is stamped so the
eventual density row does not override their theme.

- [ ] **Step 1: Stamp inside the existing guard**

The block currently reads:

```java
               // set default css title height
               if(cssDictionary.isHeightDefined(objectCssParam, titleCssParam)) {
                  ((TitledVSAssemblyInfo) this).setTitleHeightValue(
                     cssDictionary.getHeight(objectCssParam, titleCssParam));
               }
```

Change it to:

```java
               // set default css title height
               if(cssDictionary.isHeightDefined(objectCssParam, titleCssParam)) {
                  ((TitledVSAssemblyInfo) this).setTitleHeightValue(
                     cssDictionary.getHeight(objectCssParam, titleCssParam));
                  // a stylesheet height is the customer's choice, not a default
                  ((TitledVSAssemblyInfo) this).setUserTitleHeight(true);
               }
```

- [ ] **Step 2: Verify it compiles**

Run: `./mvnw -q compile -pl core`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Run the full core test suite**

Run: `./mvnw test -pl core`
Expected: no new failures against the pre-change baseline. Some tests on `main` are unstable; compare
against a baseline run rather than assuming green, and record any pre-existing failures.

- [ ] **Step 4: Commit**

```bash
git add core/src/main/java/inetsoft/uql/viewsheet/internal/VSAssemblyInfo.java
git commit -m "feat(viewsheet): mark a stylesheet-defined title height as author-set"
```

---

## Task 8: Confirm the change is behaviour-neutral

**Files:** none modified.

- [ ] **Step 1: Confirm nothing reads the flag**

Run:

```bash
grep -rn "isUserTitleHeight" core/src/main/java | grep -v "PropertyDialogService\|SyncTableHandler"
```

Expected: only the accessor on `TitleInfo`, the interface declaration on `TitledVSAssemblyInfo`, any
per-info delegate added in Task 3, and the `writeAttributes` line. No render path, no model builder, no
export painter. If anything else appears, the change is not behaviour-neutral and this plan's premise has
broken — stop and report it.

- [ ] **Step 2: Confirm the stamping map matches the design**

Run:

```bash
grep -rn "setTitleHeightValue" core/src/main/java | wc -l
grep -rn "setUserTitleHeight(true)" core/src/main/java | wc -l
```

Expected: the first count unchanged from before this work (36); the second is 13 — nine property dialogs,
the composer drag, two input dialogs and the stylesheet branch. `SyncTableHandler` propagates rather than
stamping so it is deliberately not in the second count.

- [ ] **Step 3: Confirm the internal defaults do not stamp**

Run:

```bash
grep -n -A1 "setTitleHeightValue" \
  core/src/main/java/inetsoft/uql/viewsheet/internal/CalendarVSAssemblyInfo.java \
  core/src/main/java/inetsoft/uql/viewsheet/internal/TableDataVSAssemblyInfo.java
```

Expected: no `setUserTitleHeight` next to `CalendarVSAssemblyInfo:91` or `TableDataVSAssemblyInfo:1558`.
Those are the internal seeds; stamping them would mark every calendar and every table as author-set.

- [ ] **Step 4: Round-trip an existing dashboard by hand**

Open any saved viewsheet with a titled assembly in the composer, save it, and reopen it. The title bar must
be the same height it was before. Then set a title height in the property dialog, save, reopen, and confirm
the height persists. This is the one check the unit tests cannot make.

- [ ] **Step 5: Update the roadmap**

In `docs/superpowers/specs/lookfeel/chart-card-roadmap.md`, move the `userTitleHeight` row out of "Ready
now" and into the "Done" table with this work's commit range, and update the dependency picture so N reads
as shipped and L' as unblocked. Follow the file's own hash convention: verify with
`git merge-base --is-ancestor <hash> HEAD`, not `git show`.

```bash
git add docs/superpowers/specs/lookfeel/chart-card-roadmap.md
git commit -m "docs(chart-card): record the title height flag as shipped"
```

---

## What this deliberately does not do

- **It does not change any title height.** The density row that consumes this flag is the next item and is
  specified separately.
- ~~**It does not add an un-stamp path.**~~ **Corrected 2026-08-13, during the final review.** This said
  "no equivalent reset exists for title height", and cited `ComposerVSTableService:412-413` — the very
  lines that disprove it. One does exist: two lines above, `info.resetRowHeights()`
  (`TableDataVSAssemblyInfo.java:1565-1569`) sets the title height back to `AssetUtil.defh`. So the
  reset-table-layout action resets the title height while clearing only the two sibling flags, leaving an
  assembly at the default height with `userTitleHeight="true"` persisted — permanently excluded from the
  density row, and never self-healing, because a stored `true` beats the derive. The un-stamp is added
  beside the other two.
- **It does not touch the browser.** No model field, no TypeScript. The flag is read server-side.
- **It does not resolve which types the density row covers.** Check box, radio button and time slider are
  excluded there; this plan stamps them anyway so the stored value is correct either way.
