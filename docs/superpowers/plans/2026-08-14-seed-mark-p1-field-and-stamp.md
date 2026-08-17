# Seed Mark P1 — The Field and the Stamp — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give every viewsheet assembly a persisted provenance mark recording the modern-visualization gate in force when it was created, written at creation and carried by copies, with nothing reading it yet.

**Architecture:** A nullable `VizMark` enum field on `VSAssemblyInfo`, persisted as one XML attribute through the existing `writeAttributes`/`parseAttributes` pair. A viewsheet stamps itself from the org gate in its info's constructor; an assembly inherits the host viewsheet's mark at the single creation funnel. Parse assigns the field unconditionally, so a file carrying no mark always loads unmarked — that one rule is what stops fifteen years of saved dashboards being retro-marked.

**Tech Stack:** Java 21, JUnit 5 (`@Tag("core")`, Spring `@ContextConfiguration` + `@SreeHome`), Maven via `./mvnw`.

**Spec:** `docs/superpowers/specs/2026-08-14-seed-mark-forward-half-design.md` — read §1 before starting. Behaviour is governed by `docs/superpowers/specs/lookfeel/seeded-value-reversibility-decisions.md` decisions 1, 2, 3 and 8.

## Global Constraints

- **Nothing reads the mark in P1.** No rendering, model, resolver or CSS change. `grep` for `getVizMark` at the end must find only the tests, `copyViewInfo`, and `VSEventUtil.stampVizMark`. This mirrors how `userTitleHeight` shipped in `07c91926e` ("Nothing reads the flag yet").
- **Zero visual change.** A dashboard opened, saved and re-opened must render identically and its XML must be byte-identical apart from a `vizMark` attribute on assemblies genuinely created under the gate.
- **Absent attribute means unmarked, always.** `parseAttributes` assigns unconditionally. Never conditional on the attribute being present, and never derived from anything.
- **Unrecognized attribute values parse to `null`,** not to a guess. A future third state read by an older build must degrade to legacy.
- **The mark never enters any `equals()`.** Provenance must not become a change-detection trigger. Same reasoning that kept `userTitleHeight` out of `TitleInfo.equals()`.
- **Baseline:** community `viz-updates` @ `952614aa7`. All line numbers below were read at that commit; re-check with `grep` before editing if the branch has moved.
- **Java file headers:** every new `.java` file starts with the AGPL header block copied verbatim from a sibling file in the same directory, with the year `2026`.
- **Comment style:** short clauses, not full sentences. No references to tickets, PRs or design docs in source comments.
- **Test commands run from `community/`.** `core`'s surefire is configured with `<groups>core</groups>`, so **every new test class must carry `@Tag("core")`** or it silently does not run.

---

## Prerequisite: P0

Before starting, delete the dev and test dashboards on `viz-updates` that were created with `viewsheet.modernVisualization` on. They carry seeded modern values with no mark; under decision 2 nothing will ever touch them again, so leaving them in place means every later phase is verified against permanently half-and-half content. Not a code task — just do it, and confirm it is done before Task 1.

---

## File Structure

**Created:**

| File | Responsibility |
|---|---|
| `core/src/main/java/inetsoft/uql/viewsheet/internal/VizMark.java` | The enum, its persisted string form, and the gate → mark mapping. No dependency on `Viewsheet` or `VSAssemblyInfo`. |
| `core/src/test/java/inetsoft/uql/viewsheet/internal/VizMarkTest.java` | The enum's parse/format contract and the gate mapping. |
| `core/src/test/java/inetsoft/uql/viewsheet/internal/VSAssemblyInfoVizMarkTest.java` | Persistence round-trip, clear-on-absent, and the `copyViewInfo` carry. |
| `core/src/test/java/inetsoft/uql/viewsheet/ViewsheetVizMarkTest.java` | The sheet stamp and the retro-marking guard. |
| `core/src/test/java/inetsoft/analytic/composition/event/VSEventUtilVizMarkTest.java` | Host inheritance at the creation funnel. |

**Modified:**

| File | Change |
|---|---|
| `core/src/main/java/inetsoft/uql/viewsheet/internal/VSAssemblyInfo.java` | the field, its accessors, `writeAttributes` (`:844`), `parseAttributes` (`:878`), `copyViewInfo` (`:606`) |
| `core/src/main/java/inetsoft/uql/viewsheet/internal/ViewsheetVSAssemblyInfo.java` | stamp from the gate in the constructor (`:41-45`), before `initDefaultFormat()` |
| `core/src/main/java/inetsoft/uql/viewsheet/Viewsheet.java` | belt-and-braces clear in `parseContents` (`:4209`); host-mark stamp in `getWarningTextAssembly` (`:4711`) |
| `core/src/main/java/inetsoft/analytic/composition/event/VSEventUtil.java` | `stampVizMark` helper, called before `initDefaultFormat()` (`:2095`) |

---

### Task 1: The `VizMark` enum

**Files:**
- Create: `core/src/main/java/inetsoft/uql/viewsheet/internal/VizMark.java`
- Test: `core/src/test/java/inetsoft/uql/viewsheet/internal/VizMarkTest.java`

**Interfaces:**
- Consumes: `VSDensityDefaults.isModern()` and `VSDensityDefaults.isDark()` (`VSDensityDefaults.java:39,47`) — both already exist.
- Produces: `enum VizMark { MODERN_LIGHT, MODERN_DARK }` with instance method `String value()` and statics `VizMark fromGate()` and `VizMark parse(String)`. Both statics may return `null`. Later tasks call all three.

- [ ] **Step 1: Write the failing test**

Create `core/src/test/java/inetsoft/uql/viewsheet/internal/VizMarkTest.java`. Copy the AGPL header verbatim from `core/src/test/java/inetsoft/uql/viewsheet/internal/VSDensityDefaultsTest.java`, then:

```java
package inetsoft.uql.viewsheet.internal;

import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class VizMarkTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
   }

   @Test
   void persistedFormRoundTrips() {
      assertEquals("modern-light", VizMark.MODERN_LIGHT.value());
      assertEquals("modern-dark", VizMark.MODERN_DARK.value());
      assertEquals(VizMark.MODERN_LIGHT, VizMark.parse("modern-light"));
      assertEquals(VizMark.MODERN_DARK, VizMark.parse("modern-dark"));
   }

   @Test
   void absentOrUnrecognizedParsesToUnmarked() {
      // unmarked is the safe direction: a future state read by an older build degrades to legacy
      assertNull(VizMark.parse(null));
      assertNull(VizMark.parse(""));
      assertNull(VizMark.parse("modern"));
      assertNull(VizMark.parse("MODERN_LIGHT"));
      assertNull(VizMark.parse("modern-sepia"));
   }

   @Test
   void gateOffStampsNothing() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "false");
      assertNull(VizMark.fromGate());

      // dark alone is not a gate: isDark() already requires the master gate
      SreeEnv.setProperty("viewsheet.darkMode", "true");
      assertNull(VizMark.fromGate());
   }

   @Test
   void gateOnStampsTheTuple() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      assertEquals(VizMark.MODERN_LIGHT, VizMark.fromGate());

      SreeEnv.setProperty("viewsheet.darkMode", "true");
      assertEquals(VizMark.MODERN_DARK, VizMark.fromGate());
   }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run from `community/`:
```bash
./mvnw test -pl core -Dtest=VizMarkTest
```
Expected: compilation failure — `cannot find symbol: class VizMark`.

- [ ] **Step 3: Write the implementation**

Create `core/src/main/java/inetsoft/uql/viewsheet/internal/VizMark.java`. Copy the AGPL header verbatim from `VSDensityDefaults.java` in the same directory, changing the year to `2026`, then:

```java
package inetsoft.uql.viewsheet.internal;

/**
 * An assembly's provenance mark: which modern-visualization gate was in force when it was created.
 * Absent (null) means legacy or unclaimed content, which no automatic behavior ever touches.
 */
public enum VizMark {
   MODERN_LIGHT("modern-light"),
   MODERN_DARK("modern-dark");

   VizMark(String value) {
      this.value = value;
   }

   /** The persisted form, written as the vizMark XML attribute. */
   public String value() {
      return value;
   }

   /**
    * The mark for an assembly created now, or null when the gate is off. Only consulted where an
    * assembly is genuinely being created; existing content keeps whatever it already carries.
    */
   public static VizMark fromGate() {
      if(!VSDensityDefaults.isModern()) {
         return null;
      }

      return VSDensityDefaults.isDark() ? MODERN_DARK : MODERN_LIGHT;
   }

   /**
    * Parse a persisted mark. Absent or unrecognized reads as unmarked, so a state written by a newer
    * build degrades to legacy rather than being guessed at.
    */
   public static VizMark parse(String value) {
      for(VizMark mark : values()) {
         if(mark.value.equals(value)) {
            return mark;
         }
      }

      return null;
   }

   private final String value;
}
```

Note: fix the stray indentation on the closing brace of `value()` when you paste — it should align with the method's `public`.

- [ ] **Step 4: Run the test to verify it passes**

```bash
./mvnw test -pl core -Dtest=VizMarkTest
```
Expected: PASS, 4 tests.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/inetsoft/uql/viewsheet/internal/VizMark.java \
        core/src/test/java/inetsoft/uql/viewsheet/internal/VizMarkTest.java
git commit -m "$(cat <<'EOF'
feat(viewsheet): add the VizMark provenance enum

Two states rather than one boolean: the dark bit has to be recoverable from
the mark alone, so a dark-marked assembly pasted into a light dashboard stays
internally consistent and switching dark mode off cannot strand a persisted
dark background under light chrome.

Unrecognized values parse to unmarked rather than to a guess, so a state
written by a newer build degrades to legacy in an older one.

Nothing uses it yet.

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 2: The field, its persistence, and the clear-on-absent rule

**Files:**
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/VSAssemblyInfo.java` — field block (end of file, after `padding`), accessors, `writeAttributes` (`:848-871`), `parseAttributes` (`:878-915`)
- Test: `core/src/test/java/inetsoft/uql/viewsheet/internal/VSAssemblyInfoVizMarkTest.java`

**Interfaces:**
- Consumes: `VizMark.value()` and `VizMark.parse(String)` from Task 1.
- Produces: `public VizMark getVizMark()` and `public void setVizMark(VizMark)` on `VSAssemblyInfo`, inherited by every assembly info. Tasks 3, 4 and 5 call both.

**Context you need:** `AssemblyInfo.writeXML` (`core/src/main/java/inetsoft/uql/asset/internal/AssemblyInfo.java:179`) wraps everything in a single `<assemblyInfo class="...">` element and calls `writeAttributes` for the attribute list; `parseXML` (`:279`) calls `parseAttributes` then `parseContents`. So one attribute is all this task adds to the file format.

- [ ] **Step 1: Write the failing test**

Create `core/src/test/java/inetsoft/uql/viewsheet/internal/VSAssemblyInfoVizMarkTest.java`. Copy the AGPL header from `TitleInfoTest.java` in the same directory, then:

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
class VSAssemblyInfoVizMarkTest {
   @Test
   void newInfoIsUnmarked() {
      assertNull(new TextVSAssemblyInfo().getVizMark());
   }

   @Test
   void aMarkSurvivesARoundTrip() throws Exception {
      assertEquals(VizMark.MODERN_LIGHT, roundTrip(VizMark.MODERN_LIGHT).getVizMark(),
                   "a light mark must survive a save and load");
      assertEquals(VizMark.MODERN_DARK, roundTrip(VizMark.MODERN_DARK).getVizMark(),
                   "a dark mark must survive a save and load");
   }

   @Test
   void anUnmarkedInfoWritesNoAttribute() throws Exception {
      assertFalse(toXml(new TextVSAssemblyInfo()).contains("vizMark"),
                  "an unmarked assembly must add nothing to the file format");
   }

   @Test
   void parsingAFileWithNoMarkClearsAStampedField() throws Exception {
      // the load path constructs before it parses, so a constructor stamp must not survive a file
      // that carries no mark - this is what keeps legacy content unmarked
      String legacy = toXml(new TextVSAssemblyInfo());

      TextVSAssemblyInfo stamped = new TextVSAssemblyInfo();
      stamped.setVizMark(VizMark.MODERN_DARK);
      parseInto(stamped, legacy);

      assertNull(stamped.getVizMark(),
                 "an absent attribute must clear the field, not leave it alone");
   }

   @Test
   void copyViewInfoCarriesTheMark() {
      TextVSAssemblyInfo source = new TextVSAssemblyInfo();
      source.setVizMark(VizMark.MODERN_DARK);
      TextVSAssemblyInfo target = new TextVSAssemblyInfo();

      target.copyViewInfo(source, true);

      assertEquals(VizMark.MODERN_DARK, target.getVizMark(),
                   "changing an object's type must not silently modernize a legacy assembly");
   }

   @Test
   void copyViewInfoCarriesTheAbsenceOfAMark() {
      TextVSAssemblyInfo source = new TextVSAssemblyInfo();
      TextVSAssemblyInfo target = new TextVSAssemblyInfo();
      target.setVizMark(VizMark.MODERN_LIGHT);

      target.copyViewInfo(source, true);

      assertNull(target.getVizMark(), "the copy carries the source's state in both directions");
   }

   /** Write an info to XML and parse it into a fresh one. */
   private static TextVSAssemblyInfo roundTrip(VizMark mark) throws Exception {
      TextVSAssemblyInfo written = new TextVSAssemblyInfo();
      written.setVizMark(mark);

      TextVSAssemblyInfo parsed = new TextVSAssemblyInfo();
      parseInto(parsed, toXml(written));

      return parsed;
   }

   private static String toXml(VSAssemblyInfo info) {
      StringWriter buf = new StringWriter();
      PrintWriter writer = new PrintWriter(buf);
      info.writeXML(writer);
      writer.flush();
      return buf.toString();
   }

   private static void parseInto(VSAssemblyInfo info, String xml) throws Exception {
      Document doc = Tool.parseXML(new StringReader(xml));
      info.parseXML(doc.getDocumentElement());
   }
}
```

Note: `copyViewInfo` is `protected` and this test is in the same package, so calling it directly is legal. Testing `copyViewInfo` rather than the public `copyInfo` keeps the test off the input- and output-data copy paths, which need a bound assembly.

- [ ] **Step 2: Run the test to verify it fails**

```bash
./mvnw test -pl core -Dtest=VSAssemblyInfoVizMarkTest
```
Expected: compilation failure — `cannot find symbol: method getVizMark()`.

- [ ] **Step 3: Add the field and accessors**

In `VSAssemblyInfo.java`, add to the private field block at the end of the file, immediately after `private Insets padding = new Insets(0, 0, 0, 0);`:

```java
   private VizMark vizMark;
```

Then add the accessors. Put them immediately before `public VSAssemblyInfo clone()` (`:1082`):

```java
   /**
    * The provenance mark recording the gate in force when this assembly was created. Null means
    * unmarked - legacy or unclaimed content, which no automatic behavior ever touches.
    */
   public VizMark getVizMark() {
      return vizMark;
   }

   /**
    * Set the provenance mark. Only creation and a copy from another info may set this; it records
    * when an assembly was made and is never a user-editable property.
    */
   public void setVizMark(VizMark vizMark) {
      this.vizMark = vizMark;
   }
```

`clone(boolean shallow)` needs no change — `super.clone()` at `:1094` copies the enum reference, which is all a mark needs.

- [ ] **Step 4: Write the attribute**

In `writeAttributes`, add after the `scriptEnabled` line (`:870`), so it is the last attribute written:

```java
      if(vizMark != null) {
         writer.print(" vizMark=\"" + vizMark.value() + "\"");
      }
```

- [ ] **Step 5: Parse the attribute, unconditionally**

In `parseAttributes`, add after the `scriptEnabled` line (`:914`), as the last statement:

```java
      // assigned unconditionally: an absent attribute means unmarked, and the load path constructs
      // before it parses, so a conditional read would let a constructor stamp survive a legacy file
      vizMark = VizMark.parse(Tool.getAttribute(elem, "vizMark"));
```

- [ ] **Step 6: Carry the mark in `copyViewInfo`**

In `copyViewInfo(VSAssemblyInfo info, boolean deep)`, add alongside the other non-view properties — immediately after the `desc` block that ends with `// needn't reset view` (`:625-628`):

```java
      if(!Tool.equals(vizMark, info.vizMark)) {
         vizMark = info.vizMark;
         // needn't reset view: provenance, copied so a type change cannot modernize legacy content
      }
```

Do **not** set `result = true`. Provenance is not a view change, and the paths that copy an info either share the mark already (the property dialog's edited clone) or are building a replacement assembly that has not been rendered yet.

- [ ] **Step 7: Run the test to verify it passes**

```bash
./mvnw test -pl core -Dtest=VSAssemblyInfoVizMarkTest
```
Expected: PASS, 6 tests.

- [ ] **Step 8: Run the neighbouring suites for regressions**

```bash
./mvnw test -pl core -Dtest='TitleInfoTest,VSDensityDefaultsTest,VSObjectChromeDefaultsTest'
```
Expected: PASS. These are the classes that touch the same file and the same gate.

- [ ] **Step 9: Commit**

```bash
git add core/src/main/java/inetsoft/uql/viewsheet/internal/VSAssemblyInfo.java \
        core/src/test/java/inetsoft/uql/viewsheet/internal/VSAssemblyInfoVizMarkTest.java
git commit -m "$(cat <<'EOF'
feat(viewsheet): persist an assembly's provenance mark

One nullable field on VSAssemblyInfo, written as a single attribute and omitted
when absent, so an unmarked assembly adds nothing to the file format.

The parse is unconditional, and that is the load-bearing part. The load path
constructs an info before it parses one, so a conditional read would let a mark
stamped at construction survive a file that carries none - which would mark
every dashboard ever saved on first open. Absent means unmarked, with nothing
inferred from anything.

copyViewInfo carries the mark without reporting a view change. The path that
needs it is changing an object's type, which creates a fresh assembly through
the creation funnel and then copies the old info onto it; without the carry,
turning a legacy table into a chart would silently modernize it.

The mark stays out of equals() so provenance never becomes a change-detection
trigger, matching userTitleHeight.

Nothing reads it yet.

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 3: The sheet stamps itself, and the retro-marking guard

**Files:**
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/ViewsheetVSAssemblyInfo.java:41-45` (the constructor)
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/Viewsheet.java:4209` (`parseContents`)
- Test: `core/src/test/java/inetsoft/uql/viewsheet/ViewsheetVizMarkTest.java`

**Interfaces:**
- Consumes: `VizMark.fromGate()` (Task 1), `VSAssemblyInfo.setVizMark`/`getVizMark` (Task 2).
- Produces: a `Viewsheet` whose `getVSAssemblyInfo().getVizMark()` is the gate's mark when it was constructed and never parsed, and null when it was loaded from a file that carries no mark. Task 4 reads this as the host mark.

**Why the constructor, and read this before editing.** `ViewsheetVSAssemblyInfo`'s constructor already calls `initDefaultFormat()` (`:44`), which seeds the page background. Stamping in the constructor — *before* that call — means the seed can later resolve against the assembly's own mark rather than the gate, and it covers every route to a new viewsheet without hunting for them. It is safe only because Task 2's parse clears the field: `RuntimeViewsheet.loadXml(new Viewsheet(), …)` (`RuntimeViewsheet.java:157,167,177,267`) constructs then parses, so a loaded sheet ends up with whatever its file says.

The design document describes a different shape for this — stamp at the creation funnel, then call `initDefaultFormat()` a second time. Constructor-stamping is strictly better and supersedes it: no double init, no funnel to find. Update the design's §1 "sheet-level wart" section and its open item 2 when this task lands.

- [ ] **Step 1: Write the failing test**

Create `core/src/test/java/inetsoft/uql/viewsheet/ViewsheetVizMarkTest.java`. Copy the AGPL header from a sibling test in `core/src/test/java/inetsoft/uql/viewsheet/`, then:

```java
package inetsoft.uql.viewsheet;

import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.internal.VizMark;
import inetsoft.util.Tool;
import org.junit.jupiter.api.*;
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
class ViewsheetVizMarkTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
   }

   @Test
   void aNewSheetTakesTheGate() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      assertEquals(VizMark.MODERN_LIGHT, new Viewsheet().getVSAssemblyInfo().getVizMark());

      SreeEnv.setProperty("viewsheet.darkMode", "true");
      assertEquals(VizMark.MODERN_DARK, new Viewsheet().getVSAssemblyInfo().getVizMark());
   }

   @Test
   void aNewSheetUnderAClosedGateIsUnmarked() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "false");
      assertNull(new Viewsheet().getVSAssemblyInfo().getVizMark());
   }

   @Test
   void aLegacySheetLoadedUnderAnOpenGateStaysUnmarked() throws Exception {
      // the case the whole design turns on: opening old content in a modern product must not claim it
      SreeEnv.setProperty("viewsheet.modernVisualization", "false");
      String legacy = toXml(new Viewsheet());

      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      Viewsheet loaded = parse(legacy);

      assertNull(loaded.getVSAssemblyInfo().getVizMark(),
                 "a sheet saved before the mark existed must load unmarked, gate or no gate");
   }

   @Test
   void aMarkedSheetLoadedUnderAClosedGateStaysMarked() throws Exception {
      // the mirror: the mark comes from the file, not from the live gate
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      String modern = toXml(new Viewsheet());

      SreeEnv.setProperty("viewsheet.modernVisualization", "false");
      Viewsheet loaded = parse(modern);

      assertEquals(VizMark.MODERN_LIGHT, loaded.getVSAssemblyInfo().getVizMark());
   }

   private static String toXml(Viewsheet vs) {
      StringWriter buf = new StringWriter();
      PrintWriter writer = new PrintWriter(buf);
      vs.writeXML(writer);
      writer.flush();
      return buf.toString();
   }

   private static Viewsheet parse(String xml) throws Exception {
      Document doc = Tool.parseXML(new StringReader(xml));
      Viewsheet vs = new Viewsheet();
      vs.parseXML(doc.getDocumentElement());
      return vs;
   }
}
```

**If the two round-trip tests turn out to need more context than a unit test can supply** — a repository, an asset entry, a bound worksheet — do not fight it and do not weaken the assertions. Delete those two tests, keep the two constructor tests, and cover the round trip in the manual check at Step 7 instead. Record the substitution in the commit message. The behaviour is already unit-tested at the info level by Task 2's `parsingAFileWithNoMarkClearsAStampedField`; these two tests exist to prove the composite path, not the rule.

- [ ] **Step 2: Run the test to verify it fails**

```bash
./mvnw test -pl core -Dtest=ViewsheetVizMarkTest
```
Expected: FAIL — `aNewSheetTakesTheGate` gets `null`, because nothing stamps yet.

- [ ] **Step 3: Stamp in the constructor**

In `ViewsheetVSAssemblyInfo.java`, the constructor currently reads:

```java
   public ViewsheetVSAssemblyInfo() {
      super();
      this.setPixelOffset(new Point(0, 0));
      initDefaultFormat();
   }
```

Change it to:

```java
   public ViewsheetVSAssemblyInfo() {
      super();
      this.setPixelOffset(new Point(0, 0));
      // stamped before the seeds run, so the page background can resolve against the mark; the load
      // path parses after constructing, which clears this for a file that carries no mark
      setVizMark(VizMark.fromGate());
      initDefaultFormat();
   }
```

No import is needed — `VizMark` is in the same package.

`Viewsheet.java:2397` needs no action: it is `Viewsheet`'s override of `initDefaultFormat()`, delegating to
the info, not a construction-time call. The third site from the design's open item 3, `Viewsheet.java:4715`,
is handled in Task 4.

- [ ] **Step 4: Add the guard in `Viewsheet.parseContents`**

`Viewsheet.parseContents` parses the sheet's own info only when the node is present (`:4209-4218`):

```java
      Element anode = Tool.getChildNodeByTagName(elem, "assemblyInfo");

      if(anode != null) {
```

Insert an unconditional clear immediately **before** that `Element anode = …` line:

```java
      // the constructor stamps from the gate; clear it here so a file with no assemblyInfo node
      // cannot inherit the stamp, which would claim content nobody opted in
      info.setVizMark(null);
```

This has no unit test: every viewsheet this product has ever written emits the node (`:4047`), so the branch is unreachable through normal input. It is two lines against an unrecoverable failure, and the comment is what stops someone deleting it as dead code.

- [ ] **Step 5: Run the test to verify it passes**

```bash
./mvnw test -pl core -Dtest=ViewsheetVizMarkTest
```
Expected: PASS.

- [ ] **Step 6: Run the viewsheet suites for regressions**

```bash
./mvnw test -pl core -Dtest='Viewsheet*Test,VSAssemblyInfoVizMarkTest,VizMarkTest'
```
Expected: PASS. The constructor now reads two `SreeEnv` properties on every `new Viewsheet()`, including every load — confirm nothing that constructs viewsheets in bulk has slowed or broken.

- [ ] **Step 7: Manual check**

With `viewsheet.modernVisualization` **on** in EM, open a dashboard saved before this branch. Save it. Inspect the saved asset XML and confirm **no** `vizMark` attribute appears anywhere in it. Then create a new dashboard, save it, and confirm its sheet-level `assemblyInfo` carries `vizMark="modern-light"`. Confirm both dashboards render exactly as they did before this task.

- [ ] **Step 8: Commit**

```bash
git add core/src/main/java/inetsoft/uql/viewsheet/internal/ViewsheetVSAssemblyInfo.java \
        core/src/main/java/inetsoft/uql/viewsheet/Viewsheet.java \
        core/src/test/java/inetsoft/uql/viewsheet/ViewsheetVizMarkTest.java
git commit -m "$(cat <<'EOF'
feat(viewsheet): stamp a new viewsheet with the gate it was created under

The stamp goes in ViewsheetVSAssemblyInfo's constructor, which already calls
initDefaultFormat, so it lands before the page-background seed and covers every
route to a new dashboard without enumerating them. It is safe there only because
the parse clears the field: the load path constructs a viewsheet and then parses
into it, so a file carrying no mark ends up unmarked no matter what the gate says.

parseContents clears the mark before its conditional parse of the sheet's own
assemblyInfo node. Every viewsheet this product writes emits that node, so the
branch is unreachable through normal input, but a file without one would inherit
the constructor's stamp and claim content nobody opted in - which is not
recoverable once it has been saved.

Nothing reads the mark yet.

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 4: A new assembly inherits its host viewsheet's mark

**Files:**
- Modify: `core/src/main/java/inetsoft/analytic/composition/event/VSEventUtil.java` — `createVSAssembly` (`:1984`), stamp before `:2095`
- Test: `core/src/test/java/inetsoft/analytic/composition/event/VSEventUtilVizMarkTest.java`

**Interfaces:**
- Consumes: `VSAssemblyInfo.getVizMark`/`setVizMark` (Task 2), the sheet mark from Task 3.
- Produces: `static void stampVizMark(Viewsheet vs, VSAssembly assembly)` on `VSEventUtil` — package-private, so the test can call it without a `RuntimeViewsheet`.

**Why the host and not the gate.** Decision 3: creating means "make me one like this dashboard." A legacy dashboard opened in a gate-on composer must be able to gain a new chart without that chart arriving modern — otherwise the dashboard becomes mixed by accident, and decision 2's promise that unmarked content is never touched is broken through the front door.

**Why the ordering matters.** The stamp must precede `initDefaultFormat()` so that, from P2 onward, the creation seeds resolve against the assembly's own mark instead of the gate. Nothing in P1 depends on it, and getting it right now costs nothing.

- [ ] **Step 1: Write the failing test**

Create `core/src/test/java/inetsoft/analytic/composition/event/VSEventUtilVizMarkTest.java`. Copy the AGPL header from a sibling file in `core/src/main/java/inetsoft/analytic/composition/event/`, then:

```java
package inetsoft.analytic.composition.event;

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.TextVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.VizMark;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class VSEventUtilVizMarkTest {
   @Test
   void aNewAssemblyTakesTheHostsMark() {
      Viewsheet host = new Viewsheet();
      host.getVSAssemblyInfo().setVizMark(VizMark.MODERN_DARK);
      TextVSAssembly assembly = new TextVSAssembly(host, "Text1");

      VSEventUtil.stampVizMark(host, assembly);

      assertEquals(VizMark.MODERN_DARK, assembly.getVSAssemblyInfo().getVizMark(),
                   "a new assembly inherits the dashboard it is created on");
   }

   @Test
   void aNewAssemblyOnALegacyHostStaysUnmarked() {
      // adding a chart to an old dashboard in a modern product must not modernize the chart
      Viewsheet host = new Viewsheet();
      host.getVSAssemblyInfo().setVizMark(null);
      TextVSAssembly assembly = new TextVSAssembly(host, "Text1");
      assembly.getVSAssemblyInfo().setVizMark(VizMark.MODERN_LIGHT);

      VSEventUtil.stampVizMark(host, assembly);

      assertNull(assembly.getVSAssemblyInfo().getVizMark(),
                 "the host's absence of a mark is inherited as firmly as a mark is");
   }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
./mvnw test -pl core -Dtest=VSEventUtilVizMarkTest
```
Expected: compilation failure — `cannot find symbol: method stampVizMark`.

- [ ] **Step 3: Add the helper**

In `VSEventUtil.java`, add immediately after the `createVSAssembly(RuntimeViewsheet, int)` method:

```java
   /**
    * Give a newly created assembly its host viewsheet's provenance mark. Creating means "one like
    * this dashboard", so a legacy dashboard's new assemblies stay legacy even under an open gate.
    * Copies and imports keep their own mark and never reach here - they are parsed, not created.
    */
   static void stampVizMark(Viewsheet vs, VSAssembly assembly) {
      assembly.getVSAssemblyInfo().setVizMark(vs.getVSAssemblyInfo().getVizMark());
   }
```

- [ ] **Step 4: Call it before `initDefaultFormat()`**

In `createVSAssembly`, the code after the `switch` currently reads (`:2094-2098`):

```java
      assert assembly != null;
      assembly.initDefaultFormat();
      vs.addAssembly(assembly);

      return assembly;
```

Change it to:

```java
      assert assembly != null;
      // before initDefaultFormat, so the creation seeds resolve against this assembly's own mark
      stampVizMark(vs, assembly);
      assembly.initDefaultFormat();
      vs.addAssembly(assembly);

      return assembly;
```

- [ ] **Step 5: Run the test to verify it passes**

```bash
./mvnw test -pl core -Dtest=VSEventUtilVizMarkTest
```
Expected: PASS, 2 tests.

- [ ] **Step 6: Stamp the one assembly created outside the funnel**

`Viewsheet.getWarningTextAssembly(boolean)` (`Viewsheet.java:4711`) builds the sheet's warning label itself and
calls `initDefaultFormat()` directly (`:4715`), bypassing `createVSAssembly`. Left alone it would be
permanently unmarked, so from P4 onward a modern dashboard would carry a legacy-chromed warning label. One
line fixes it. The block currently reads:

```java
      if(assembly == null && createIfNotExist) {
         TextVSAssembly textVSAssembly = new TextVSAssembly(this, VS_WARNING_TEXT);
         textVSAssembly.getTextInfo().setAutoSize(true);
         textVSAssembly.initDefaultFormat();
```

Change it to:

```java
      if(assembly == null && createIfNotExist) {
         TextVSAssembly textVSAssembly = new TextVSAssembly(this, VS_WARNING_TEXT);
         textVSAssembly.getTextInfo().setAutoSize(true);
         // created outside createVSAssembly, so it inherits the host mark here instead
         textVSAssembly.getVSAssemblyInfo().setVizMark(getVSAssemblyInfo().getVizMark());
         textVSAssembly.initDefaultFormat();
```

Inline rather than a call to `VSEventUtil.stampVizMark` — `uql.viewsheet` must not depend on
`analytic.composition.event`.

This is the last such site. `AnnotationLineVSAssemblyInfo:44` and `AnnotationRectangleVSAssemblyInfo:98` call
`super.initDefaultFormat()` from inside their own overrides, and both assembly types are created through the
funnel's `switch`, so they are already covered.

- [ ] **Step 7: Verify nothing reads the mark**

```bash
grep -rn "getVizMark" --include=*.java core/src/main
```
Expected: exactly three hits — the accessor's own declaration in `VSAssemblyInfo.java`, the host read in
`VSEventUtil.stampVizMark`, and the host read in `Viewsheet.getWarningTextAssembly`. `copyViewInfo` touches
the field directly rather than through the getter, so it will not appear. Nothing in a resolver, a model, a
painter or an exporter. If anything else appears, it does not belong in P1.

- [ ] **Step 8: Run the full core suite**

```bash
./mvnw test -pl core
```
Expected: PASS, or the same pre-existing failures the branch had before this plan started — check against a clean run on `952614aa7` before blaming this work. `main` is trunk-based and some tests are known unstable.

- [ ] **Step 9: Manual check**

Under a gate-on product: open a **legacy** dashboard, add a chart, save it. Confirm the saved XML has **no** `vizMark` on the new chart — it inherited the unmarked host. Then create a **new** dashboard, add a chart, save it, and confirm the chart carries `vizMark="modern-light"`. Confirm both dashboards render as they did before.

- [ ] **Step 10: Commit**

```bash
git add core/src/main/java/inetsoft/analytic/composition/event/VSEventUtil.java \
        core/src/main/java/inetsoft/uql/viewsheet/Viewsheet.java \
        core/src/test/java/inetsoft/analytic/composition/event/VSEventUtilVizMarkTest.java
git commit -m "$(cat <<'EOF'
feat(viewsheet): give new assemblies their host dashboard's mark

createVSAssembly is the single funnel for a genuinely new assembly - copies,
pastes and imports are parsed rather than created, so they keep their own mark
without any code. The stamp reads the host viewsheet rather than the gate:
creating means one like this dashboard, so adding a chart to a legacy dashboard
in a modern product leaves the chart legacy instead of quietly making the
dashboard mixed.

The call sits before initDefaultFormat so the creation seeds can later resolve
against the assembly's own mark rather than the gate. Nothing depends on that
ordering yet; it is free now and awkward to correct later.

The helper is package-private so it can be tested without standing up a
RuntimeViewsheet.

getWarningTextAssembly builds the sheet's warning label itself rather than going
through the funnel, so it inherits the host mark inline. Without it that one
assembly would be unmarked forever and would show legacy chrome on a modern
dashboard once the read paths follow the mark.

Nothing reads the mark yet.

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 5: Update the design document

**Files:**
- Modify: `docs/superpowers/specs/2026-08-14-seed-mark-forward-half-design.md`

Two things the design got wrong about mechanism, discovered while building. The design is the reference for P2–P5, so it has to be right before the next plan is written from it.

- [ ] **Step 1: Replace the sheet-level wart section**

In §1, replace the whole subsection headed "The sheet-level wart" — heading included — with:

```markdown
### The sheet stamps itself in its constructor

`ViewsheetVSAssemblyInfo`'s constructor already calls `initDefaultFormat()` (`:44`). The stamp goes there,
immediately before that call, so the page-background seed resolves against the sheet's own mark and no second
init is needed. It also covers every route to a new viewsheet without enumerating them.

Safe only because of the parse rule above: `RuntimeViewsheet.loadXml(new Viewsheet(), …)` constructs and then
parses, so a loaded sheet ends up with whatever its file says. The two are one decision. `Viewsheet.parseContents`
additionally clears the mark before its conditional parse of the sheet's `assemblyInfo` node (`:4209`), because
a file lacking that node would otherwise inherit the constructor's stamp.

**Rejected — removing the constructor's `initDefaultFormat()` call and placing it only at creation sites.**
It changes what a parsed legacy sheet falls back to when its XML carries no background value, which is a
rendering change to every saved dashboard in exchange for tidiness.
```

- [ ] **Step 2: Close open item 2**

In "Open items to settle while writing the plans", replace item 2 — the one asking for the composer's
new-viewsheet funnel to be located — with:

```markdown
2. ~~**The composer's new-viewsheet funnel.**~~ Closed in P1: stamping in `ViewsheetVSAssemblyInfo`'s
   constructor covers every construction path, so no funnel needs finding. Kept as the record of why the
   question went away rather than being answered.
```

- [ ] **Step 3: Amend the stamp table**

In §1's "Where the mark is written" table, replace the first row

```markdown
| A genuinely new viewsheet — `DashboardController.java:292,421` and the composer's equivalent | from the gate: `isModern()` → `isDark() ? MODERN_DARK : MODERN_LIGHT`, else null |
```

with two rows:

```markdown
| Any new viewsheet — `ViewsheetVSAssemblyInfo`'s constructor (`:44`), un-stamped by parse | `VizMark.fromGate()` |
| `Viewsheet.getWarningTextAssembly` (`:4711`) — the one assembly built outside the funnel | the host viewsheet's mark, inline |
```

- [ ] **Step 4: Commit**

```bash
git add docs/superpowers/specs/2026-08-14-seed-mark-forward-half-design.md
git commit -m "$(cat <<'EOF'
docs(chart-card): record how P1 stamps the sheet mark

The design had the sheet stamped at the creation funnel and initDefaultFormat
called a second time afterwards. Stamping in ViewsheetVSAssemblyInfo's
constructor is strictly better: it lands before the page-background seed, needs
no second init, and covers every route to a new dashboard - which closes the
open item asking for the composer's new-viewsheet funnel to be found.

Safe only because the parse clears the field, so the two are one decision and
are now recorded together.

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Done when

- `VizMark` exists with four passing enum tests.
- `VSAssemblyInfo` carries the field, persists it, clears it on a mark-free parse, and carries it through `copyViewInfo` — six passing tests.
- A new viewsheet stamps itself from the gate; a legacy one loaded under an open gate stays unmarked.
- A new assembly inherits its host's mark, in both directions.
- `grep -rn "getVizMark" --include=*.java core/src/main` finds three hits: the accessor declaration, `VSEventUtil.stampVizMark`, and `Viewsheet.getWarningTextAssembly`.
- The manual checks in Tasks 3 and 4 confirm a legacy dashboard round-trips with no `vizMark` anywhere and renders unchanged.
- The design document matches what was built.

**Not in P1, and do not let it creep in:** `VizContext`, any change to a `VS*Defaults` class, the `VSObjectModel` field, any CSS, and Modernize. Those are P2, P3 and P5.
