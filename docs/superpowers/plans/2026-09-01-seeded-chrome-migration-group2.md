# Seeded Chrome Migration, Group 2 — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Convert the last three titled assembly types (checkbox, radio button, calendar) from read-time chrome substitution to creation-time seeding, delete both remaining read-time resolvers, and extend Excel's dark opt-out to titles and painted pictures.

**Architecture:** Each type writes its chrome into the stored `DEFAULT` tier from its own `seedChromeDefaults(VizContext)` override, on both the modern and legacy branches, instead of having a resolver substitute onto a throwaway clone at every render. A value in the stored format travels in an exported asset; a value computed at render does not. Once all three convert, `VSTitleChromeDefaults.applyModernDefaults` and `VSCalendarChromeDefaults` have no consumers and are deleted, and the export site the title resolver occupied is reused for the inverse Excel substitution.

**Tech Stack:** Java 21, JUnit 5 + Spring Test (`@SreeHome`, `ConfigurationContextInitializer`), Maven multi-module reactor.

**Spec:** [`docs/superpowers/specs/lookfeel/2026-09-01-seeded-chrome-migration-group2-design.md`](../specs/lookfeel/2026-09-01-seeded-chrome-migration-group2-design.md)

## Global Constraints

- **Both branches of every seed must write.** `VizModernizeUtil.revert` clears the mark and re-runs `seedChromeDefaults`; it does no other per-assembly work. A value written only on the modern branch is stranded by Revert.
- **The legacy branch must reproduce a gate-off creation exactly**, not approximately. `FormatInfo.equals` is a deep value comparison (`FormatInfo:534-541`) that other code depends on — Task 3 is the case in point.
- **Never install a fresh composite from the hook.** It runs again on assemblies that already exist (Modernize, Revert, `reseedAfterRestore`); installing a new composite drops the author's `USER` tier. Mutate what is there.
- **No customization guards.** A seed writes `DEFAULT` only, so a `USER` or `CSS` value outranks it by construction. Do not reimplement `isForegroundCustomized` / `isBackgroundCustomized`.
- **`setXxxValue(v)` marks the field defined**; `setXxxValue(v, false)` does not. `VSFormat:314-330`. This distinction is load-bearing in Task 1.
- **`bypassesBaseChrome()` and `installsOwnTitleFormat()` are not modified by any task in this plan.**
- **Run the full `core` suite before the final commit**, and a **clean** (not incremental) 46-module reactor build after Task 5, which changes a signature reaching `utils/inetsoft-xml-formats`.
- Java files use 3-space indent and the existing AGPL header. Match surrounding comment density.

---

## File Structure

**Modified — production:**

| File | Responsibility after this plan |
| --- | --- |
| `core/.../internal/ListInputVSAssemblyInfo.java` | Gains `seedInputTitleLane(VizContext)`, the shared unfilled-lane writer. Not an override and not self-called — `ComboBoxVSAssemblyInfo` extends this class and must not get a rule. |
| `core/.../internal/CheckBoxVSAssemblyInfo.java` | Calls the hook from `setDefaultFormat`; overrides `seedChromeDefaults` to seed its title lane. |
| `core/.../internal/RadioButtonVSAssemblyInfo.java` | Same as checkbox. |
| `core/.../internal/CalendarVSAssemblyInfo.java` | Gains `applyCalendarSeed(FormatInfo, VizContext)` (static), a `seedChromeDefaults` override, the hook call in `initDefaultFormat`, and the cloned + seeded prototype swap in `copyViewInfo`. |
| `core/.../gui/viewsheet/VSCalendar.java` | Year-view foreground fallback becomes tier-aware; two `applyModernDefaults` call sites unwrapped. |
| `core/.../internal/VSTitleChromeDefaults.java` | Palette supplier only. `applyModernDefaults`, `applyModernDefaultsInPlace`, `isSeededTitle` deleted. |
| `core/.../internal/VSCalendarChromeDefaults.java` | **Deleted.** Its two colours are a third copy of `VSTableStructureDefaults`' body palette. |
| `core/.../io/viewsheet/AbstractVSExporter.java` | Gains `paintsPageBackground()`; the emptied title site becomes the Excel dark opt-out. |
| `core/.../web/viewsheet/service/ExcelVSExporter.java` | Overrides `paintsPageBackground()` to `false` — one override covering both Excel exporters. |
| `core/.../internal/MaxModeSelectionVSAssemblyInfo.java` | Gains the shared title-lane seed and `legacyTitleRuleColors()`. |
| `core/.../internal/SelectionBaseVSAssemblyInfo.java` | Keeps only its `DETAIL`-cell block; title block and helper move to the parent. |
| `core/.../internal/TimeSliderVSAssemblyInfo.java` | `seedChromeDefaults` override and helper deleted; parent does all of its work. |

**Modified — tests:**

| File | Responsibility |
| --- | --- |
| `core/src/test/.../internal/SeedChromeDefaultsTest.java` | Home for every seed assertion. Gains checkbox, radio and calendar factories and triples. |
| `core/src/test/.../internal/CalendarPrototypeSwapTest.java` | **New.** The show-type swap: prototype not aliased, guard still fires for a marked calendar. |
| `core/src/test/.../internal/VSTitleChromeDefaultsTest.java` | Reduced to the palette suppliers; the 18 resolver tests go. |
| `core/src/test/.../internal/VSCalendarChromeDefaultsTest.java` | **Deleted** with its subject. |
| `core/src/test/.../io/viewsheet/ExporterDarkOptOutTest.java` | **New.** The extracted Excel substitution and the predicate. |

---

## Task 1: Checkbox and radio title lane

**Files:**
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/ListInputVSAssemblyInfo.java`
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/CheckBoxVSAssemblyInfo.java:408-431`
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/RadioButtonVSAssemblyInfo.java:375-398`
- Test: `core/src/test/java/inetsoft/uql/viewsheet/internal/SeedChromeDefaultsTest.java`

**Interfaces:**
- Consumes: `VSTitleChromeDefaults.titleRuleBorders()`, `titleRuleColors(VizContext)`, `titleForegroundValue(VizContext)` — all already public and unchanged by this task.
- Produces: `protected final void ListInputVSAssemblyInfo.seedInputTitleLane(VizContext ctx)`. No later task calls it.

**Why these two types need a hook call at all:** both override `setDefaultFormat(boolean)` without calling `super`, so the base's `seedChromeDefaults(VizContext.of(this))` at `VSAssemblyInfo:1235` is unreachable for them today. Both also sit on `bypassesBaseChrome()`, so `super.seedChromeDefaults(ctx)` returns immediately at the guard — that is correct and intended, exactly as for `TextVSAssemblyInfo`.

- [ ] **Step 1: Write the failing tests**

Add to `SeedChromeDefaultsTest.java`, next to the existing `newTimeSlider()` helper:

```java
   private CheckBoxVSAssemblyInfo newCheckBox() {
      Viewsheet vs = new Viewsheet();
      CheckBoxVSAssembly checkbox = new CheckBoxVSAssembly(vs, "CheckBox1");
      checkbox.getVSAssemblyInfo().initDefaultFormat();
      return (CheckBoxVSAssemblyInfo) checkbox.getVSAssemblyInfo();
   }

   private RadioButtonVSAssemblyInfo newRadioButton() {
      Viewsheet vs = new Viewsheet();
      RadioButtonVSAssembly radio = new RadioButtonVSAssembly(vs, "RadioButton1");
      radio.getVSAssemblyInfo().initDefaultFormat();
      return (RadioButtonVSAssemblyInfo) radio.getVSAssemblyInfo();
   }
```

And the assertions:

```java
   @Test
   void aModernCheckBoxTitleIsUnfilledWithABottomRule() {
      gateOn();
      VizContext ctx = VizContext.ofGate();
      VSFormat def = titleFormat(newCheckBox()).getDefaultFormat();

      Insets borders = def.getBordersValue();
      assertEquals(StyleConstants.NONE, borders.top, "no top rule");
      assertEquals(StyleConstants.NONE, borders.left, "no left rule");
      assertEquals(StyleConstants.NONE, borders.right, "no right rule");
      assertEquals(StyleConstants.THIN_LINE, borders.bottom, "a bottom rule only");

      assertEquals(VSTitleChromeDefaults.titleBorderColor(ctx),
                   def.getBorderColorsValue().bottomColor);
      assertEquals(VSTitleChromeDefaults.titleForegroundValue(ctx), def.getForegroundValue());
      assertNull(def.getBackgroundValue(), "the modern input lane carries no fill");
      assertTrue(def.isBackgroundValueDefined(),
                 "defined, so the object background cannot copy down onto the lane");
   }

   @Test
   void aLegacyCheckBoxTitleHasNoRuleOnAnySide() {
      gateOff();
      VSFormat def = titleFormat(newCheckBox()).getDefaultFormat();

      assertEquals(new Insets(0, 0, 0, 0), def.getBordersValue(),
                   "a gate-off input title has never carried a rule");
      assertNull(def.getBorderColorsValue(),
                 "and no rule colour either: storing one would differ from a gate-off creation");
      assertNull(def.getForegroundValue());
      assertNull(def.getForeground(), "the fg field too, or a runtime colour survives Revert");
      assertFalse(def.isBackgroundValueDefined(),
                  "undefined, as a gate-off creation leaves it");
   }

   @Test
   void revertingACheckBoxTitleRestoresAGateOffCreation() {
      gateOff();
      VSFormat expected = titleFormat(newCheckBox()).getDefaultFormat();
      Insets expectedBorders = expected.getBordersValue();
      boolean expectedBgDefined = expected.isBackgroundValueDefined();

      gateOn();
      CheckBoxVSAssemblyInfo info = newCheckBox();
      info.setVizMark(VizMark.fromGate());
      info.seedChromeDefaults(VizContext.of(info));

      info.setVizMark(null);
      info.seedChromeDefaults(VizContext.of((VizMark) null));

      VSFormat reverted = titleFormat(info).getDefaultFormat();
      assertEquals(expectedBorders, reverted.getBordersValue());
      assertNull(reverted.getBorderColorsValue());
      assertNull(reverted.getForegroundValue());
      assertEquals(expectedBgDefined, reverted.isBackgroundValueDefined(),
                   "Revert must match a checkbox that was never modernized, not almost match it");
   }

   @Test
   void aModernRadioButtonTitleTakesTheSameLaneAsTheCheckBox() {
      gateOn();
      VizContext ctx = VizContext.ofGate();
      VSFormat def = titleFormat(newRadioButton()).getDefaultFormat();

      assertEquals(StyleConstants.THIN_LINE, def.getBordersValue().bottom);
      assertEquals(VSTitleChromeDefaults.titleForegroundValue(ctx), def.getForegroundValue());
      assertNull(def.getBackgroundValue());
   }

   @Test
   void aLegacyRadioButtonTitleHasNoRuleOnAnySide() {
      gateOff();
      VSFormat def = titleFormat(newRadioButton()).getDefaultFormat();

      assertEquals(new Insets(0, 0, 0, 0), def.getBordersValue());
      assertNull(def.getForegroundValue());
   }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./mvnw test -pl core -Dtest=SeedChromeDefaultsTest -DfailIfNoTests=false`

Expected: the five new tests FAIL. `aModernCheckBoxTitleIsUnfilledWithABottomRule` fails on `borders.bottom` — a checkbox title's borders are `Insets(0,0,0,0)` today, so the assertion sees `NONE` where it wants `THIN_LINE`.

- [ ] **Step 3: Add the shared lane writer to `ListInputVSAssemblyInfo`**

Add this method to `ListInputVSAssemblyInfo`. Add `java.awt.Insets` to its imports if absent.

```java
   /**
    * The modern title lane for an input widget: unfilled, with a bottom rule. Deliberately not an
    * override and not called from here - ComboBoxVSAssemblyInfo also extends this class and is not
    * a titled card, so each type that wants the lane calls this from its own seedChromeDefaults.
    *
    * The legacy branch nulls the rule colour rather than restoring one, and leaves the background
    * undefined rather than defined-null, because that is what a gate-off creation writes: these
    * titles have never carried a rule or a fill.
    */
   protected final void seedInputTitleLane(VizContext ctx) {
      VSCompositeFormat titleFormat = getFormatInfo().getFormat(TITLEPATH);

      if(titleFormat == null) {
         return;
      }

      VSFormat def = titleFormat.getDefaultFormat();
      def.setBordersValue(ctx.modern
         ? VSTitleChromeDefaults.titleRuleBorders() : new Insets(0, 0, 0, 0));
      def.setBorderColorsValue(ctx.modern ? VSTitleChromeDefaults.titleRuleColors(ctx) : null);
      def.setForegroundValue(ctx.modern ? VSTitleChromeDefaults.titleForegroundValue(ctx) : null);
      // getForeground() falls back to the fg field when fgval yields nothing, so the legacy branch
      // has to null both or a runtime foreground survives the clear
      def.setForeground(null);
      // defined on the modern branch so the object background cannot copy down through
      // getFormat(TITLEPATH, false); left undefined on the legacy branch, as a gate-off creation has it
      def.setBackgroundValue(null, ctx.modern);
   }
```

- [ ] **Step 4: Wire the checkbox**

In `CheckBoxVSAssemblyInfo`, replace the closing of `setDefaultFormat(boolean)` (currently ending `setCSSDefaults();` then `}`) with:

```java
      setCSSDefaults();
      // this type overrides setDefaultFormat without calling super, so the base's hook call is
      // unreachable; seed here instead
      seedChromeDefaults(VizContext.of(this));
   }

   @Override
   protected void seedChromeDefaults(VizContext ctx) {
      // returns at the bypass guard - this type owns its object border and radius
      super.seedChromeDefaults(ctx);
      seedInputTitleLane(ctx);
   }
```

- [ ] **Step 5: Wire the radio button**

Make the identical change in `RadioButtonVSAssemblyInfo`, at the end of its `setDefaultFormat(boolean)`:

```java
      setCSSDefaults();
      // this type overrides setDefaultFormat without calling super, so the base's hook call is
      // unreachable; seed here instead
      seedChromeDefaults(VizContext.of(this));
   }

   @Override
   protected void seedChromeDefaults(VizContext ctx) {
      // returns at the bypass guard - this type owns its object border and radius
      super.seedChromeDefaults(ctx);
      seedInputTitleLane(ctx);
   }
```

- [ ] **Step 6: Run the tests to verify they pass**

Run: `./mvnw test -pl core -Dtest=SeedChromeDefaultsTest -DfailIfNoTests=false`

Expected: PASS, all tests in the class including the pre-existing ones.

- [ ] **Step 7: Sweep for USER-tier writes that would outrank the seed**

Run: `grep -rn "getUserDefinedFormat()" core/src/main/java/inetsoft/report/gui/viewsheet/VSCheckBox.java core/src/main/java/inetsoft/report/gui/viewsheet/VSRadioButton.java core/src/main/java/inetsoft/web/viewsheet/model/VSCheckBoxModel.java core/src/main/java/inetsoft/web/viewsheet/model/VSRadioButtonModel.java`

Expected: no hits. A hit means a render path writes the `USER` tier and silently outranks this seed — the defect Task 4 fixes for the calendar. If there is a hit, stop and report it rather than working around it.

- [ ] **Step 8: Commit**

```bash
git add core/src/main/java/inetsoft/uql/viewsheet/internal/ListInputVSAssemblyInfo.java core/src/main/java/inetsoft/uql/viewsheet/internal/CheckBoxVSAssemblyInfo.java core/src/main/java/inetsoft/uql/viewsheet/internal/RadioButtonVSAssemblyInfo.java core/src/test/java/inetsoft/uql/viewsheet/internal/SeedChromeDefaultsTest.java
```

```bash
git commit -m "feat(viewsheet): seed the checkbox and radio title lanes at creation

Both types override setDefaultFormat without calling super, so the base's
seedChromeDefaults call was unreachable and their title chrome came from the
read-time substitution instead. Each now calls the hook and seeds an unfilled
lane with a bottom rule, the treatment every converted titled type carries.

The legacy branch leaves the background undefined rather than defined-null,
and nulls the rule colour rather than storing one: a gate-off input title has
never carried a rule or a fill, and Revert has to reproduce that exactly.

The writer sits on ListInputVSAssemblyInfo but is not an override and is not
called from there, because ComboBoxVSAssemblyInfo also extends that class and
is not a titled card.

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
```

---

## Task 2: Calendar seed

**Files:**
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/CalendarVSAssemblyInfo.java:86-92` and add two methods
- Test: `core/src/test/java/inetsoft/uql/viewsheet/internal/SeedChromeDefaultsTest.java`

**Interfaces:**
- Consumes: `VSTableStructureDefaults.headerForeground(VizContext)`, `headerBackground(VizContext)`, `bodyForeground(VizContext)`, `bodyBackground(VizContext)` — all public, all returning `null` in light and legacy for the body pair.
- Produces: `static void CalendarVSAssemblyInfo.applyCalendarSeed(FormatInfo fi, VizContext ctx)`. **Task 3 calls this on cloned prototypes**, which is why it takes a `FormatInfo` rather than reading `this`.

**Why a static taking a `FormatInfo`:** the instance hook applies it in place to the installed format, and Task 3 applies it to two cloned static prototypes. One decision about what the values are, in one place.

- [ ] **Step 1: Write the failing tests**

Add the factory and assertions to `SeedChromeDefaultsTest.java`:

```java
   private CalendarVSAssemblyInfo newCalendar() {
      Viewsheet vs = new Viewsheet();
      CalendarVSAssembly calendar = new CalendarVSAssembly(vs, "Calendar1");
      calendar.getVSAssemblyInfo().initDefaultFormat();
      return (CalendarVSAssemblyInfo) calendar.getVSAssemblyInfo();
   }

   private static VSFormat calendarPath(VSAssemblyInfo info, int type) {
      return info.getFormatInfo().getFormat(new TableDataPath(-1, type)).getDefaultFormat();
   }

   @Test
   void aModernCalendarTitleIsUnfilledWithABottomRule() {
      gateOn();
      VizContext ctx = VizContext.ofGate();
      VSFormat def = titleFormat(newCalendar()).getDefaultFormat();

      assertNull(def.getBackgroundValue(), "the modern title lane carries no fill");
      assertEquals(StyleConstants.THIN_LINE, def.getBordersValue().bottom);
      assertEquals(VSTitleChromeDefaults.titleBorderColor(ctx),
                   def.getBorderColorsValue().bottomColor);
      assertEquals(VSTitleChromeDefaults.titleForegroundValue(ctx), def.getForegroundValue());
   }

   @Test
   void aLegacyCalendarTitleKeepsTheFilledBand() {
      gateOff();
      VSFormat def = titleFormat(newCalendar()).getDefaultFormat();

      assertEquals(VSAssemblyInfo.DEFAULT_TITLE_BG, def.getBackgroundValue(),
                   "a gate-off calendar title is filled, unlike an input title");
      assertNull(def.getForegroundValue());
   }

   @Test
   void aModernCalendarHeaderTakesTheTableHeaderNeutrals() {
      gateOn();
      VizContext ctx = VizContext.ofGate();
      VSFormat def = calendarPath(newCalendar(), TableDataPath.CALENDAR_TITLE);

      assertEquals(colorValue(VSTableStructureDefaults.headerForeground(ctx)),
                   def.getForegroundValue(),
                   "the month/year header is a header band, and tracks the table's");
      assertEquals(colorValue(VSTableStructureDefaults.headerBackground(ctx)),
                   def.getBackgroundValue());
   }

   @Test
   void aModernCalendarBodyTracksTheTableBodyInBothSchemes() {
      gateOn();
      VizContext light = VizContext.ofGate();
      VSFormat def = calendarPath(newCalendar(), TableDataPath.MONTH_CALENDAR);

      assertEquals(colorValue(VSTableStructureDefaults.bodyForeground(light)),
                   def.getForegroundValue(),
                   "asserted against the supplier, not a literal, so the two cannot drift");
      assertNull(def.getForegroundValue(),
                 "and in light that supplier is null - the table body is unthemed too");

      SreeEnv.setProperty("viewsheet.darkMode", "true");
      VizContext dark = VizContext.ofGate();
      VSFormat darkDef = calendarPath(newCalendar(), TableDataPath.YEAR_CALENDAR);

      assertEquals(colorValue(VSTableStructureDefaults.bodyForeground(dark)),
                   darkDef.getForegroundValue());
      assertEquals(colorValue(VSTableStructureDefaults.bodyBackground(dark)),
                   darkDef.getBackgroundValue());
      assertNotNull(darkDef.getForegroundValue(), "dark is the branch that has a value");
   }

   @Test
   void revertingACalendarRestoresAGateOffCreation() {
      gateOff();
      CalendarVSAssemblyInfo expected = newCalendar();
      String expectedTitleBg = titleFormat(expected).getDefaultFormat().getBackgroundValue();
      String expectedBodyFg =
         calendarPath(expected, TableDataPath.MONTH_CALENDAR).getForegroundValue();

      gateOn();
      CalendarVSAssemblyInfo info = newCalendar();
      info.setVizMark(VizMark.fromGate());
      info.seedChromeDefaults(VizContext.of(info));

      info.setVizMark(null);
      info.seedChromeDefaults(VizContext.of((VizMark) null));

      assertEquals(expectedTitleBg, titleFormat(info).getDefaultFormat().getBackgroundValue());
      assertEquals(expectedBodyFg,
                   calendarPath(info, TableDataPath.MONTH_CALENDAR).getForegroundValue());
   }
```

Add this helper next to the other private statics in the test class, because the suppliers return `Color` and the formats store `String`:

```java
   private static String colorValue(Color c) {
      return c == null ? null : String.format("0x%06x", c.getRGB() & 0xFFFFFF);
   }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./mvnw test -pl core -Dtest=SeedChromeDefaultsTest -DfailIfNoTests=false`

Expected: the calendar tests FAIL. `aModernCalendarTitleIsUnfilledWithABottomRule` fails on `getBackgroundValue()` — a calendar title carries `DEFAULT_TITLE_BG` in every mode today, because the hook has never run for this type.

- [ ] **Step 3: Add the seed and the hook to `CalendarVSAssemblyInfo`**

Add both methods. `FormatInfo` is already reachable via the `inetsoft.uql.viewsheet.*` import.

```java
   @Override
   protected void seedChromeDefaults(VizContext ctx) {
      // returns at the bypass guard - this type installs its own object format and radius
      super.seedChromeDefaults(ctx);
      applyCalendarSeed(getFormatInfo(), ctx);
   }

   /**
    * Write the mark-dependent chrome into a calendar's stored DEFAULT tiers. Static and taking the
    * FormatInfo rather than reading this, because copyViewInfo applies it to cloned prototypes as
    * well as to the installed format - one decision about what the values are, in one place.
    *
    * The body cells call the table's suppliers rather than carrying their own copy of the palette:
    * the two were already byte-identical, kept in sync by nothing.
    */
   static void applyCalendarSeed(FormatInfo fi, VizContext ctx) {
      if(fi == null) {
         return;
      }

      VSCompositeFormat titleFormat = fi.getFormat(TITLEPATH);

      if(titleFormat != null) {
         VSFormat def = titleFormat.getDefaultFormat();
         def.setBordersValue(ctx.modern
            ? VSTitleChromeDefaults.titleRuleBorders()
            : new Insets(0, 0, StyleConstants.THIN_LINE, 0));
         def.setBorderColorsValue(ctx.modern
            ? VSTitleChromeDefaults.titleRuleColors(ctx) : legacyTitleBorderColors());
         def.setForegroundValue(
            ctx.modern ? VSTitleChromeDefaults.titleForegroundValue(ctx) : null);
         // getForeground() falls back to the fg field when fgval yields nothing
         def.setForeground(null);
         def.setBackgroundValue(ctx.modern ? null : DEFAULT_TITLE_BG);
      }

      // the month/year header is a header band above date cells, the relationship a table header
      // has to its body. Nothing has ever written this path, so a dark calendar drew black ink on
      // the dark card in every server-painted export
      applyTo(fi.getFormat(CALENDAR_TITLE_PATH),
              VSTableStructureDefaults.headerForeground(ctx),
              VSTableStructureDefaults.headerBackground(ctx));

      // the weekday header and the date cells share one format, so both take body semantics
      applyTo(fi.getFormat(CALENDAR_MONTH_PATH),
              VSTableStructureDefaults.bodyForeground(ctx),
              VSTableStructureDefaults.bodyBackground(ctx));
      applyTo(fi.getFormat(CALENDAR_YEAR_PATH),
              VSTableStructureDefaults.bodyForeground(ctx),
              VSTableStructureDefaults.bodyBackground(ctx));
   }

   private static void applyTo(VSCompositeFormat fmt, Color fg, Color bg) {
      if(fmt == null) {
         return;
      }

      VSFormat def = fmt.getDefaultFormat();
      def.setForegroundValue(toValue(fg));
      def.setForeground(null);
      def.setBackgroundValue(toValue(bg));
   }

   private static String toValue(Color c) {
      return c == null ? null : String.format("0x%06x", c.getRGB() & 0xFFFFFF);
   }

   /** The four-side colours a gate-off calendar title carries, from the static prototype. */
   private static BorderColors legacyTitleBorderColors() {
      return new BorderColors(DEFAULT_BORDER_COLOR, new Color(0xC0C0C0),
                              DEFAULT_BORDER_COLOR, DEFAULT_BORDER_COLOR);
   }
```

- [ ] **Step 4: Call the hook from `initDefaultFormat`**

The calendar never calls `setDefaultFormat`, so the base's hook call at `VSAssemblyInfo:1235` has never run for it. Replace `initDefaultFormat` (`:87-92`) with:

```java
   @Override
   public void initDefaultFormat() {
      setFormatInfo(normalDefault.clone());
      setCSSDefaults();
      titleInfo.setTitleHeightValue(36);
      // this type never calls setDefaultFormat, so the base's hook call is unreachable
      seedChromeDefaults(VizContext.of(this));
   }
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./mvnw test -pl core -Dtest=SeedChromeDefaultsTest -DfailIfNoTests=false`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/inetsoft/uql/viewsheet/internal/CalendarVSAssemblyInfo.java core/src/test/java/inetsoft/uql/viewsheet/internal/SeedChromeDefaultsTest.java
```

```bash
git commit -m "feat(viewsheet): seed the calendar's chrome at creation

The calendar is the one type that has never run seedChromeDefaults: its
initDefaultFormat clones a static FormatInfo and never calls setDefaultFormat,
so the base's hook call was unreachable. It now seeds its title lane, its
month/year header and its body cells.

The body cells call VSTableStructureDefaults' suppliers instead of carrying
their own copy of the palette. The two were already byte-identical and kept in
sync by nothing, so the calendar and the table now cannot drift apart.

The month/year header had never been written by anything, in either mode. A
dark calendar drew black ink on the dark card in every server-painted export;
it now takes the table header neutrals, which is the relationship it has to
the date cells below it.

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
```

---

## Task 3: Calendar prototype swap and equality guard

**Files:**
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/CalendarVSAssemblyInfo.java:1058-1069`
- Test: `core/src/test/java/inetsoft/uql/viewsheet/internal/CalendarPrototypeSwapTest.java` (new)

**Interfaces:**
- Consumes: `CalendarVSAssemblyInfo.applyCalendarSeed(FormatInfo, VizContext)` from Task 2.
- Produces: nothing consumed by later tasks.

**The two problems.** `copyViewInfo` swaps the whole `FormatInfo` on a show-type change, gated on `getFormatInfo().equals(normalDefault)`. It installs the JVM-wide static **uncloned** (`:1063`, `:1067`), so a later format edit mutates the prototype and — since `initDefaultFormat` does `normalDefault.clone()` — corrupts every calendar created afterwards in that JVM, across orgs. That bug predates this work. Separately, `FormatInfo.equals` is a deep value comparison, so Task 2's seed makes a seeded calendar unequal to the raw prototype and the guard silently stops firing.

- [ ] **Step 1: Write the failing tests**

Create `core/src/test/java/inetsoft/uql/viewsheet/internal/CalendarPrototypeSwapTest.java`. Copy the AGPL header from `SeedChromeDefaultsTest.java` verbatim.

```java
package inetsoft.uql.viewsheet.internal;

import inetsoft.sree.SreeEnv;
import inetsoft.test.*;
import inetsoft.uql.viewsheet.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The calendar's show-type default swap. It installs a static FormatInfo prototype, so it has two
 * failure modes worth pinning: aliasing the static (which corrupts every later calendar in the
 * JVM), and an equality guard that stops matching once the format carries seeded values.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, LibManagerTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class CalendarPrototypeSwapTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
   }

   private CalendarVSAssemblyInfo newCalendar() {
      Viewsheet vs = new Viewsheet();
      CalendarVSAssembly calendar = new CalendarVSAssembly(vs, "Calendar1");
      calendar.getVSAssemblyInfo().initDefaultFormat();
      return (CalendarVSAssemblyInfo) calendar.getVSAssemblyInfo();
   }

   /** Drive the show-type change through copyViewInfo, the way the property dialog does. */
   private CalendarVSAssemblyInfo swapToDropdown(CalendarVSAssemblyInfo info) {
      CalendarVSAssemblyInfo source = newCalendar();
      source.setShowTypeValue(CalendarVSAssemblyInfo.DROPDOWN_SHOW_TYPE);
      info.copyInfo(source);
      return info;
   }

   @Test
   void theSwapDoesNotAliasTheStaticPrototype() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "false");
      CalendarVSAssemblyInfo info = swapToDropdown(newCalendar());

      // an author edits a format on the swapped calendar
      info.getFormatInfo().getFormat(VSAssemblyInfo.OBJECTPATH)
         .getUserDefinedFormat().setBackgroundValue("0x123456");

      // a calendar created afterwards must not inherit that edit
      CalendarVSAssemblyInfo fresh = swapToDropdown(newCalendar());
      assertNull(fresh.getFormatInfo().getFormat(VSAssemblyInfo.OBJECTPATH)
                    .getUserDefinedFormat().getBackgroundValue(),
                 "the prototype was aliased: an edit to one calendar reached every later one");
   }

   @Test
   void theSwapStillFiresForAMarkedCalendar() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      CalendarVSAssemblyInfo info = newCalendar();
      assertNotNull(info.getVizMark(), "precondition: the gate is on, so creation marks it");

      String before = info.getFormatInfo().getFormat(VSAssemblyInfo.OBJECTPATH)
         .getDefaultFormat().getBackgroundValue();
      swapToDropdown(info);
      String after = info.getFormatInfo().getFormat(VSAssemblyInfo.OBJECTPATH)
         .getDefaultFormat().getBackgroundValue();

      assertNotEquals(before, after,
                      "the guard compared against an unseeded prototype and never matched");
      assertEquals("0xffffff", after, "the dropdown prototype's white object fill");
   }

   @Test
   void theSwapStillFiresForAnUnmarkedCalendar() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "false");
      CalendarVSAssemblyInfo info = swapToDropdown(newCalendar());

      assertEquals("0xffffff", info.getFormatInfo().getFormat(VSAssemblyInfo.OBJECTPATH)
                      .getDefaultFormat().getBackgroundValue());
   }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./mvnw test -pl core -Dtest=CalendarPrototypeSwapTest -DfailIfNoTests=false`

Expected: `theSwapDoesNotAliasTheStaticPrototype` and `theSwapStillFiresForAMarkedCalendar` FAIL. The first because the static is installed uncloned; the second because Task 2's seed broke the equality guard.

If `theSwapStillFiresForAnUnmarkedCalendar` also fails, the test's `swapToDropdown` helper is not reaching `copyViewInfo` — fix the helper before touching production code, since it is the harness for the other two.

- [ ] **Step 3: Clone and seed both prototypes**

Replace the show-type block in `copyViewInfo` (`:1060-1069`) with:

```java
         // set the default formatting based on type. Both prototypes are JVM-wide statics: clone
         // before installing, or a later format edit on this assembly mutates the prototype and
         // every calendar created afterwards inherits it. The guard compares against the prototype
         // as seeded for this assembly's mark, because a seeded format is not equal to a raw one
         VizContext ctx = VizContext.of(this);

         if(getShowType() == CALENDAR_SHOW_TYPE) {
            if(getFormatInfo().equals(seededPrototype(normalDefault, ctx))) {
               setFormatInfo(seededPrototype(dropdownDefault, ctx));
            }
         }
         else if(getFormatInfo().equals(seededPrototype(dropdownDefault, ctx))) {
            setFormatInfo(seededPrototype(normalDefault, ctx));
         }
```

And add next to `applyCalendarSeed`:

```java
   /** A clone of a static prototype, seeded for the given context. Never returns the static. */
   private static FormatInfo seededPrototype(FormatInfo proto, VizContext ctx) {
      FormatInfo copy = proto.clone();
      applyCalendarSeed(copy, ctx);
      return copy;
   }
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./mvnw test -pl core -Dtest=CalendarPrototypeSwapTest -DfailIfNoTests=false`

Expected: PASS, all three.

- [ ] **Step 5: Run the calendar's neighbours for regressions**

Run: `./mvnw test -pl core -Dtest='Calendar*Test,SeedChromeDefaultsTest,VizModernizeUtilTest' -DfailIfNoTests=false`

Expected: PASS. `CalendarPropertyDialogServiceTest` exercises the property-dialog path this task changes.

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/inetsoft/uql/viewsheet/internal/CalendarVSAssemblyInfo.java core/src/test/java/inetsoft/uql/viewsheet/internal/CalendarPrototypeSwapTest.java
```

```bash
git commit -m "fix(viewsheet): stop the calendar show-type swap aliasing a static prototype

copyViewInfo installed normalDefault and dropdownDefault uncloned, handing the
assembly a JVM-wide static. Any later format edit mutated it, and because
initDefaultFormat clones normalDefault, the corruption reached every calendar
created afterwards in that JVM regardless of org. That bug predates the chrome
work and had no test.

The swap now installs a clone. The guard that drives it compares against the
prototype as seeded for the assembly's own mark rather than against the raw
static, because FormatInfo.equals is a deep comparison and a seeded calendar is
not equal to an unseeded prototype - without which the swap would have silently
stopped firing for every marked calendar.

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
```

---

## Task 4: Calendar year-view foreground fallback

**Files:**
- Modify: `core/src/main/java/inetsoft/report/gui/viewsheet/VSCalendar.java:986-988`
- Test: `core/src/test/java/inetsoft/uql/viewsheet/internal/SeedChromeDefaultsTest.java`

**Interfaces:** consumes nothing new; produces nothing.

**The defect.** `VSCalendar:987` writes `format.getUserDefinedFormat().setForeground(new Color(90, 90, 90))` whenever the **user** foreground is null. The `USER` tier outranks `DEFAULT`, so the body colour never reaches the year view. This is already true today with the substitution — a dark year calendar paints `0x5A5A5A` on `0x252428` — and seeding does not change it, because the tier ordering is the same. Widening the guard from the `USER` tier to the resolved composite fixes it: `VSCompositeFormat.getForeground()` resolves `USER` → `CSS` → `DEFAULT` (`:197-204`), so a seeded dark value now suppresses the fallback while light and legacy, where the supplier is null, still get their grey.

- [ ] **Step 1: Write the failing test**

Add to `SeedChromeDefaultsTest.java`:

```java
   @Test
   void theYearViewGreyFallbackDoesNotOutrankASeededBodyColour() {
      gateOn();
      SreeEnv.setProperty("viewsheet.darkMode", "true");
      VizContext ctx = VizContext.ofGate();

      VSCompositeFormat yearFormat = newCalendar().getFormatInfo()
         .getFormat(new TableDataPath(-1, TableDataPath.YEAR_CALENDAR));

      assertEquals(VSTableStructureDefaults.bodyForeground(ctx), yearFormat.getForeground(),
                   "precondition: the seed put the dark body colour on the DEFAULT tier");

      // what VSCalendar does before painting month cells in the year view
      if(yearFormat.getForeground() == null) {
         yearFormat.getUserDefinedFormat().setForeground(new Color(90, 90, 90));
      }

      assertEquals(VSTableStructureDefaults.bodyForeground(ctx), yearFormat.getForeground(),
                   "the grey fallback must not fire when a colour is already resolved");
   }
```

This pins the tier logic the production fix relies on. It fails today because the seeded value is present but the *production* guard reads only the `USER` tier — Step 3 makes the production code match this test's condition.

- [ ] **Step 2: Run the test to verify it fails**

Run: `./mvnw test -pl core -Dtest=SeedChromeDefaultsTest#theYearViewGreyFallbackDoesNotOutrankASeededBodyColour -DfailIfNoTests=false`

Expected: FAIL on the precondition assertion if Task 2 is not yet merged; otherwise PASS at the precondition and the test documents the contract. If it passes outright, still complete Step 3 — the production site is a separate expression from this test's inlined copy.

- [ ] **Step 3: Widen the production guard**

In `VSCalendar.java`, replace:

```java
      if(format.getUserDefinedFormat().getForeground() == null) {
         format.getUserDefinedFormat().setForeground(new Color(90, 90, 90));
      }
```

with:

```java
      // resolve across all three tiers, not just USER: the seeded body colour lives on DEFAULT, and
      // a USER-tier grey written here would outrank it and paint dark-on-dark in the year view
      if(format.getForeground() == null) {
         format.getUserDefinedFormat().setForeground(new Color(90, 90, 90));
      }
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./mvnw test -pl core -Dtest=SeedChromeDefaultsTest -DfailIfNoTests=false`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/inetsoft/report/gui/viewsheet/VSCalendar.java core/src/test/java/inetsoft/uql/viewsheet/internal/SeedChromeDefaultsTest.java
```

```bash
git commit -m "fix(viewsheet): stop the year-view grey outranking the calendar body colour

The year view wrote a 0x5A5A5A fallback to the USER tier whenever the USER tier
held no foreground. USER outranks DEFAULT, so the dark body colour never reached
the year view and a dark calendar painted 0x5A5A5A on 0x252428. True before this
work with the read-time substitution, and equally true after seeding, because
the tier ordering is what decides it.

The guard now resolves across all three tiers. Light and legacy still take the
grey, because the body supplier is null there.

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
```

---

## Task 5: Delete the read-time family

**Files:**
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/VSTitleChromeDefaults.java`
- Delete: `core/src/main/java/inetsoft/uql/viewsheet/internal/VSCalendarChromeDefaults.java`
- Delete: `core/src/test/java/inetsoft/uql/viewsheet/internal/VSCalendarChromeDefaultsTest.java`
- Modify: `core/src/test/java/inetsoft/uql/viewsheet/internal/VSTitleChromeDefaultsTest.java`
- Modify (call sites): `AbstractVSExporter.java:1411,1803,1901,2690`, `ExportUtil.java:114`, `VSSelectionListHelper.java:76`, `VSSelectionTreeHelper.java:267`, `VSTableDataHelper.java:401`, `VsToReportConverter.java:1375,1516`, `FormatPainterService.java:220`, `VSCalendar.java:734,956`, `VSCalendarModel.java:47,77,93`, `VSChartModel.java:59`, `BaseTableModel.java:40`, `VSCheckBoxModel.java:37`, `VSCompositeModel.java:38`, `VSRadioButtonModel.java:37`, `VSRangeSliderModel.java:108`

**Interfaces:**
- Consumes: nothing.
- Produces: `ExportUtil.getBackGroundColor(VSCompositeFormat titleFormat, VSCompositeFormat objectFormat)` — **two parameters**, down from four. Task 6 does not call it.

**Precondition — do this first.** Run the caller sweep across `src/test` as well as `src/main`. Group 1 deleted a method reported as unused that had a live test caller, breaking test compilation.

- [ ] **Step 1: Enumerate every caller, tests included**

Run: `grep -rn "applyModernDefaults\|applyModernDefaultsInPlace\|VSCalendarChromeDefaults\|getBackGroundColor" --include=*.java core utils`

Record the list. It must match the Files section above plus `VSTitleChromeDefaultsTest` and `VSCalendarChromeDefaultsTest`. Any file not in that list is a caller this plan did not anticipate — stop and report it.

- [ ] **Step 2: Unwrap the call sites**

At each site, replace the wrapped call with the bare format lookup. The mechanical shape, using `VSCheckBoxModel:37-38` as the worked example:

```java
      // before
      VSCompositeFormat compositeTitleFormat = VSTitleChromeDefaults.applyModernDefaults(
         fmtInfo.getFormat(titlepath, false), VizContext.of(assemblyInfo));

      // after
      VSCompositeFormat compositeTitleFormat = fmtInfo.getFormat(titlepath, false);
```

For the two in-place sites (`AbstractVSExporter:1803`, `VsToReportConverter:1516`) delete the statement outright — **but leave the surrounding `if(vinfo instanceof TitledVSAssemblyInfo && ...)` block in `AbstractVSExporter` in place**, because Task 6 fills it. For `VSCalendar:734` and `:956`, drop the `VSCalendarChromeDefaults.applyModernDefaults(...)` wrapper and keep the inner `getFormat(...)` call.

Remove any `VizContext` import or local that becomes unused at each site. Do not remove `VizContext` from `AbstractVSExporter` — Task 6 needs it.

- [ ] **Step 3: Narrow `ExportUtil.getBackGroundColor`**

Both remaining parameters were only there to feed the resolver:

```java
   /**
    * Get background color of time slider in current selection.
    * @param titleFormat format of slider title.
    * @param objectFormat format of slider object.
    */
   public static Color getBackGroundColor(VSCompositeFormat titleFormat,
                                          VSCompositeFormat objectFormat)
   {
      return titleFormat != null && titleFormat.getBackground() != null ?
         titleFormat.getBackground() : objectFormat != null &&
         objectFormat.getBackground() != null ? objectFormat.getBackground() : null;
   }
```

Update its callers, which include sites in `utils/inetsoft-xml-formats`.

- [ ] **Step 4: Delete the resolvers**

From `VSTitleChromeDefaults`, delete `applyModernDefaults` (both overloads), `applyModernDefaultsInPlace` (both overloads), `isSeededTitle`, `applyTo`, `isBackgroundCustomized` and `isForegroundCustomized`. Keep `titleBackground`, `titleForeground`, `titleBorderColor`, `titleRuleBorders`, `titleForegroundValue`, `titleRuleColors`, `toValue` and the six colour constants. Replace the class Javadoc's read-time paragraph with a statement that it is a palette supplier and that every titled type seeds its own lane.

Delete `VSCalendarChromeDefaults.java` and `VSCalendarChromeDefaultsTest.java` entirely.

From `VSTitleChromeDefaultsTest`, delete every test that calls a deleted method and keep those asserting the palette suppliers.

- [ ] **Step 5: Clean build across all modules**

Run: `./mvnw clean install -DskipTests -Pcommunity,enterprise`

Expected: BUILD SUCCESS, 46 modules. **`clean` is mandatory, not a preference.** `getBackGroundColor`'s signature reaches `utils/inetsoft-xml-formats`, and three prior signature changes on this branch passed an incremental install with stale callers in that module.

Confirm the module genuinely rebuilt rather than being skipped: `ls -l utils/inetsoft-xml-formats/target/*.jar` and check the timestamp is from this run.

- [ ] **Step 6: Run the full core suite**

Run: `./mvnw test -pl core`

Expected: PASS. Compare the test count against the group-1 baseline of 5219 minus the deleted resolver tests.

- [ ] **Step 7: Commit**

```bash
git add -A core/src/main/java/inetsoft core/src/test/java/inetsoft utils
```

```bash
git commit -m "refactor(viewsheet): delete the read-time chrome resolvers

With the checkbox, radio button and calendar seeding their own chrome, nothing
consumes the substituting resolvers. VSTitleChromeDefaults keeps its palette
suppliers and loses applyModernDefaults, applyModernDefaultsInPlace and
isSeededTitle; VSCalendarChromeDefaults goes entirely, its two colours having
been a third copy of the table's body palette.

Fifteen call sites unwrap to a bare format lookup. getBackGroundColor loses the
VizContext and VSAssemblyInfo parameters this leaves dead, which the compiler
enforces across callers in another Maven module - verified with a clean
46-module reactor rather than an incremental install, for the fourth time on
this branch.

No chrome value is computed at render after this. A dashboard exported from a
build with this work and imported into one without it now renders consistently
rather than mixed.

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
```

---

## Task 6: Excel dark opt-out for titles and painted pictures

**Files:**
- Modify: `core/src/main/java/inetsoft/report/io/viewsheet/AbstractVSExporter.java:1798-1806`
- Modify: `core/src/main/java/inetsoft/web/viewsheet/service/ExcelVSExporter.java`
- Test: `core/src/test/java/inetsoft/report/io/viewsheet/ExporterDarkOptOutTest.java` (new)

**Interfaces:**
- Consumes: `VSObjectChromeDefaults.legacyCellForegroundValue()`, already public and used by the group-1 opt-out.
- Produces: `protected boolean AbstractVSExporter.paintsPageBackground()` and `static void AbstractVSExporter.applyDarkOptOutInPlace(VSCompositeFormat)`.

**Why one site and not ten.** `AbstractVSExporter:1798-1806` already mutates the title format once on the export copy, with the comment *"Every per-widget / per-format title draw reads the title format from this one assembly, so doing it here covers all titled assemblies at once."* Task 5 empties that block; the inverse substitution takes its place. The slider joins it because `VSSlider:138,268` paints its labels from `format.getForeground()`, so a picture and a title cell are fixed by the same branch.

**Why one override.** `ExcelVSExporter` (in `core`) is abstract; `PoiExcelVSExporter extends ExcelVSExporter` and `OfflineExcelVSExporter extends PoiExcelVSExporter`. One override covers both, and `utils/inetsoft-xml-formats` is not touched. `CSVVSExporter` keeps the default and is unaffected, writing no colours.

- [ ] **Step 1: Write the failing test**

Create `core/src/test/java/inetsoft/report/io/viewsheet/ExporterDarkOptOutTest.java` with the AGPL header copied from a neighbouring test.

```java
package inetsoft.report.io.viewsheet;

import inetsoft.uql.viewsheet.VSCompositeFormat;
import inetsoft.uql.viewsheet.internal.VSObjectChromeDefaults;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The inverse of the modern seed, for formats that paint no surface behind their ink. A spreadsheet
 * has no page to paint, so its cells are unfilled white and a seeded light neutral is invisible on
 * them. Tested at the substitution rather than through an exporter, which needs a bootstrapped
 * server - so a mis-wiring at the call site is not caught here. Recorded, not hidden.
 */
@Tag("core")
class ExporterDarkOptOutTest {
   @Test
   void theOptOutPutsTheLegacyInkBackOnTheDefaultTier() {
      VSCompositeFormat format = new VSCompositeFormat();
      format.getDefaultFormat().setForegroundValue("0xcac4d0");

      AbstractVSExporter.applyDarkOptOutInPlace(format);

      assertEquals(VSObjectChromeDefaults.legacyCellForegroundValue(),
                   format.getDefaultFormat().getForegroundValue(),
                   "0xcac4d0 is invisible on an unfilled white cell");
   }

   @Test
   void theOptOutLeavesAUserColourAlone() {
      VSCompositeFormat format = new VSCompositeFormat();
      format.getDefaultFormat().setForegroundValue("0xcac4d0");
      format.getUserDefinedFormat().setForegroundValue("0xff0000");

      AbstractVSExporter.applyDarkOptOutInPlace(format);

      assertEquals("0xff0000", format.getForegroundValue(),
                   "the author's colour outranks both the seed and the opt-out");
   }

   @Test
   void aNullFormatIsTolerated() {
      assertDoesNotThrow(() -> AbstractVSExporter.applyDarkOptOutInPlace(null));
   }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./mvnw test -pl core -Dtest=ExporterDarkOptOutTest -DfailIfNoTests=false`

Expected: FAIL to compile — `applyDarkOptOutInPlace` does not exist.

- [ ] **Step 3: Add the predicate and the substitution**

In `AbstractVSExporter`:

```java
   /**
    * Whether this format paints a surface behind the ink it draws. A PDF page, a PPT slide and a
    * PNG all do; a spreadsheet does not - its cells are unfilled white, so a seeded dark-mode
    * neutral is invisible on them and has to be substituted back. Names the distinction rather
    * than testing for Excel, which is what lets one branch answer for a title cell and a painted
    * picture alike.
    */
   protected boolean paintsPageBackground() {
      return true;
   }

   /**
    * Put the legacy near-black back on a dark-marked format's DEFAULT tier, in place. The export
    * copy is cloned upstream, so this mutates nothing persisted. A USER or CSS colour outranks the
    * DEFAULT tier and is therefore untouched by construction.
    */
   static void applyDarkOptOutInPlace(VSCompositeFormat format) {
      if(format != null) {
         format.getDefaultFormat().setForegroundValue(
            VSObjectChromeDefaults.legacyCellForegroundValue());
      }
   }
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./mvnw test -pl core -Dtest=ExporterDarkOptOutTest -DfailIfNoTests=false`

Expected: PASS.

- [ ] **Step 5: Wire it at the export site**

Replace the block Task 5 emptied (`AbstractVSExporter:1798-1806`) with:

```java
      // A seeded dark neutral assumes a surface behind it. A spreadsheet paints none, so its cells
      // are unfilled white and the ink has to go back to the legacy near-black. Every per-widget
      // title draw reads the title format from this one assembly, so doing it here covers all
      // titled assemblies at once - and the slider joins them because VSSlider paints its labels
      // from format.getForeground(). The viewsheet is cloned upstream, so nothing persisted moves.
      VSAssemblyInfo vinfo = assembly.getVSAssemblyInfo();

      if(!paintsPageBackground() && vinfo != null && vinfo.getFormatInfo() != null
         && VizContext.of(vinfo).dark)
      {
         if(vinfo instanceof TitledVSAssemblyInfo) {
            applyDarkOptOutInPlace(vinfo.getFormatInfo().getFormat(VSAssemblyInfo.TITLEPATH));
         }

         if(vinfo instanceof SliderVSAssemblyInfo) {
            applyDarkOptOutInPlace(vinfo.getFormatInfo().getFormat(VSAssemblyInfo.OBJECTPATH));
         }
      }
```

- [ ] **Step 6: Override the predicate for Excel**

In `core/src/main/java/inetsoft/web/viewsheet/service/ExcelVSExporter.java`:

```java
   /**
    * A spreadsheet has no page to paint. Both Excel exporters extend this class, so this one
    * override covers them.
    */
   @Override
   protected boolean paintsPageBackground() {
      return false;
   }
```

- [ ] **Step 7: Verify the override reaches both exporters**

Run: `grep -rn "paintsPageBackground" --include=*.java core utils`

Expected: exactly three hits — the declaration, the `ExcelVSExporter` override, and the call site. If `PoiExcelVSExporter` or `OfflineExcelVSExporter` declares its own, remove it; the inherited one is the point.

- [ ] **Step 8: Run the full core suite**

Run: `./mvnw test -pl core`

Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add core/src/main/java/inetsoft/report/io/viewsheet/AbstractVSExporter.java core/src/main/java/inetsoft/web/viewsheet/service/ExcelVSExporter.java core/src/test/java/inetsoft/report/io/viewsheet/ExporterDarkOptOutTest.java
```

```bash
git commit -m "fix(export): put legacy ink back on titles and sliders in Excel

A seeded dark neutral assumes a surface behind it. Excel paints none, so its
unfilled white cells rendered the seeded title foreground and the slider's
painted labels invisibly. Group 1 fixed the same defect for selection cells and
parked this half as a doctrine question; the doctrine extends.

It extends at one site rather than ten. The export copy already resolves every
per-widget title draw from one format, so the substitution goes where the
modern one used to, and the slider joins it there because VSSlider paints its
labels from the same getForeground().

paintsPageBackground() names the actual distinction rather than testing for
Excel, which is what lets one branch answer for a title cell and a picture
alike. It is overridden once, on the abstract ExcelVSExporter that both Excel
exporters extend, so inetsoft-xml-formats is untouched.

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
```

---

## Task 7: Hoist the shared title seed to MaxModeSelectionVSAssemblyInfo

**Files:**
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/MaxModeSelectionVSAssemblyInfo.java`
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/SelectionBaseVSAssemblyInfo.java:931-963,1079-1082`
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/TimeSliderVSAssemblyInfo.java:720-740,1149-1152`

**Interfaces:**
- Consumes: nothing new.
- Produces: `protected void MaxModeSelectionVSAssemblyInfo.seedChromeDefaults(VizContext)`. No later task.

**This is a pure refactor: no test changes, and the existing assertions are the safety net.** The two subclasses share a twelve-line title block and a byte-identical `legacyTitleRuleColors()` (`SelectionBaseVSAssemblyInfo:1079-1082`, `TimeSliderVSAssemblyInfo:1149-1152`). Their `seedChromeDefaults` bodies are **not** byte-identical — group 1 added a `DETAIL`-cell block to the selection base only — so the parent takes the title block and the selection base keeps the cell block.

- [ ] **Step 1: Record the green baseline**

Run: `./mvnw test -pl core -Dtest='SeedChromeDefaultsTest,VizModernizeUtilTest,SelectionListModelDarkForegroundTest' -DfailIfNoTests=false`

Expected: PASS. Note the test count; it must be identical after the refactor.

- [ ] **Step 2: Move the title block to the parent**

Add to `MaxModeSelectionVSAssemblyInfo`, and add the imports it needs (`inetsoft.uql.viewsheet.VSCompositeFormat`, `VSFormat`, `BorderColors`):

```java
   @Override
   protected void seedChromeDefaults(VizContext ctx) {
      super.seedChromeDefaults(ctx);

      // the title lane, shared by the selection family and the range slider: both install or
      // overwrite their own title composite, and neither has ever carried a fill, so only the
      // rule's colour and the text colour move. Both branches write, because the legacy one is
      // what Revert relies on to restore a never-modernized assembly
      VSCompositeFormat titleFormat = getFormatInfo().getFormat(TITLEPATH);

      if(titleFormat != null) {
         VSFormat def = titleFormat.getDefaultFormat();
         def.setBordersValue(VSTitleChromeDefaults.titleRuleBorders());
         def.setBorderColorsValue(ctx.modern
            ? VSTitleChromeDefaults.titleRuleColors(ctx) : legacyTitleRuleColors());
         def.setForegroundValue(
            ctx.modern ? VSTitleChromeDefaults.titleForegroundValue(ctx) : null);
         // getForeground() falls back to the fg field when fgval yields nothing, so the legacy
         // branch has to null both or a runtime foreground survives the clear
         def.setForeground(null);
      }
   }

   private static BorderColors legacyTitleRuleColors() {
      return new BorderColors(new Color(0xc0c0c0), new Color(0xc0c0c0),
                              new Color(0xc0c0c0), new Color(0xc0c0c0));
   }
```

- [ ] **Step 3: Reduce the selection base to its cell block**

In `SelectionBaseVSAssemblyInfo`, replace the whole `seedChromeDefaults` override with:

```java
   @Override
   protected void seedChromeDefaults(VizContext ctx) {
      // the title lane is the parent's, shared with the range slider
      super.seedChromeDefaults(ctx);

      // the detail cell's foreground. Seeded rather than substituted at every render so it travels
      // in an exported asset. setDefaultFormat's unconditional near-black stays where it is and
      // this overwrites it; the measure-bar composites are separate paths and are not reached
      VSCompositeFormat cellFormat =
         getFormatInfo().getFormat(new TableDataPath(-1, TableDataPath.DETAIL));

      if(cellFormat != null) {
         cellFormat.getDefaultFormat().setForegroundValue(
            ctx.dark ? VSObjectChromeDefaults.darkForegroundValue()
               : VSObjectChromeDefaults.legacyCellForegroundValue());
      }
   }
```

Delete its `legacyTitleRuleColors()` at `:1079-1082`.

- [ ] **Step 4: Delete the slider's override**

In `TimeSliderVSAssemblyInfo`, delete the entire `seedChromeDefaults` override (`:720-740`) and `legacyTitleRuleColors()` (`:1149-1152`). The parent now does all of its seeding.

**Leave the `seedChromeDefaults(VizContext.of(this))` re-run at `:717` in place** — it exists because `setDefaultFormat` overwrites the title composite the base already seeded, and that reason is unchanged. Leave the equivalent re-run in `SelectionBaseVSAssemblyInfo` too.

- [ ] **Step 5: Collapse the `installsOwnTitleFormat()` branches**

In `VSAssemblyInfo:1345-1350`, replace the two sibling branches with the shared parent:

```java
   private boolean installsOwnTitleFormat() {
      return this instanceof ChartVSAssemblyInfo
         || this instanceof MaxModeSelectionVSAssemblyInfo;
   }
```

This is behaviour-preserving: `MaxModeSelectionVSAssemblyInfo` has exactly `SelectionBaseVSAssemblyInfo` and `TimeSliderVSAssemblyInfo` as subclasses. Confirm with `grep -rn "extends MaxModeSelectionVSAssemblyInfo" --include=*.java core` before making the change — if a third subclass exists, keep the explicit branches.

- [ ] **Step 6: Run the baseline suite and compare**

Run: `./mvnw test -pl core -Dtest='SeedChromeDefaultsTest,VizModernizeUtilTest,SelectionListModelDarkForegroundTest' -DfailIfNoTests=false`

Expected: PASS, with the same test count as Step 1. A pure refactor changes no assertion.

- [ ] **Step 7: Run the full core suite**

Run: `./mvnw test -pl core`

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add core/src/main/java/inetsoft/uql/viewsheet/internal/MaxModeSelectionVSAssemblyInfo.java core/src/main/java/inetsoft/uql/viewsheet/internal/SelectionBaseVSAssemblyInfo.java core/src/main/java/inetsoft/uql/viewsheet/internal/TimeSliderVSAssemblyInfo.java core/src/main/java/inetsoft/uql/viewsheet/internal/VSAssemblyInfo.java
```

```bash
git commit -m "refactor(viewsheet): hoist the shared title seed to MaxModeSelectionVSAssemblyInfo

The selection base and the range slider are siblings, not parent and child,
which is why three separate comments had to warn about it and why
installsOwnTitleFormat() needed a branch for each. Their shared title block and
their byte-identical legacyTitleRuleColors move to the parent they do share.

The bodies were no longer identical: group 1 added a DETAIL-cell seed to the
selection base only. So the parent takes the title block, the selection base
keeps its cell block, and the slider's override goes entirely.

The re-runs stay in both subclasses. Their reasons differ - one replaces the
title composite, the other mutates it in place - and their setDefaultFormat
bodies differ.

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
```

---

## Manual verification

Automated tests do not substitute for any of these. Run them before calling the branch done, and record each outcome.

- [ ] **1. Export a marked dashboard, import it, confirm the values arrive.** The acceptance check — this is the portability defect the whole track exists to close, and the one check that fails before this work.
- [ ] **2. Dark title, slider, selection list and tree exported to Excel.** Fails **silently** as white-on-white if Task 6 is wrong. Open the workbook and confirm the text is readable, do not trust a successful export.
- [ ] **3. Calendar show-type switch, marked and unmarked; then edit a format on the swapped calendar and create a second calendar.** The second calendar must be clean. Blast radius of a miss is every calendar in the JVM.
- [ ] **4. Modernize and Revert a dashboard holding a checkbox, a radio button and a calendar.** All three return to legacy exactly.
- [ ] **5. Dark year-view calendar**, in the browser and in a PDF export. Dates must be legible (Task 4).
- [ ] **6. Ad-hoc filter title**, both modes — the surface the selection-family design flagged as missed.
- [ ] **7. A dark calendar in PPT**, which paints a slide background and must keep the modern ink, unlike Excel.

---

## Notes for whoever picks this up

Three things this plan establishes that should not be re-derived:

- **A `USER`-tier write anywhere in a render path silently outranks a seed.** Task 4 is the instance. The sweep is `grep -rn "getUserDefinedFormat()" ` across every painter and model for the type being converted, run *before* costing the conversion. Task 1 Step 7 does this for the inputs.
- **`setXxxValue(v)` marks the field defined and `setXxxValue(v, false)` does not**, and the difference is visible to `FormatInfo.equals` and to `copyDefaultFormat`'s `!isXxxValueDefined()` guards. A legacy branch that writes `null` where a gate-off creation writes nothing has not reproduced a gate-off creation.
- **Enumerate the paths a type RENDERS, not the paths a resolver was called from.** That is how Task 2 found the untreated `CALENDAR_TITLE` and Task 4 found the year-view fallback; it is the same discipline that cost group 1 a Critical finding when a selection tree's cloned `GROUP_HEADER` composites went stale.
