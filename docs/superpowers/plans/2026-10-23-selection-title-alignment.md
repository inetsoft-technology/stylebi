# Selection Title Alignment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A modern, standalone, left-aligned selection list's or tree's title text starts over its first row's leading glyph, in the browser, in export and in print layout. Its header band, kebab and legacy assemblies stay exactly as they are.

**Architecture:** The browser computes the offset in `VSSelection.getTitleTextLeft()` and hands it to `title-cell` as a default left padding, which a `format.css` title padding still overrides. Export and print share one predicate, `SelectionBaseVSAssemblyInfo.isTitleOverRows()`. Export resolves the indent through the exporter, next to `getSelectionCardInset`, so Excel and CSV are gated out. Print pads the selection's title text box by the card inset.

**Tech Stack:** Java 21, JUnit 5, Mockito, Spring test context (`@SreeHome`), Maven (`./mvnw`, module `core`); Angular 21 with Vitest.

**Spec:** `docs/superpowers/specs/lookfeel/2026-10-22-selection-title-alignment-design.md` (PR #7275). Task 1 amends it. The rulings, and the reasons other options were not taken, are in `E:/Temp/bug-79230-selection-title-alignment-decisions.md`, which travels with this plan.

## Global Constraints

- **Branch and worktree:** `bug-79230`, in the worktree at `C:/Users/Franky/AppData/Local/Temp/claude/E--StyleBI-stylebi-enterprise/fb6e825e-a5ec-419d-bd47-7724ac2e17d6/scratchpad/selection-title-alignment` (call it `$WT`). It was cut from `origin/epic-74519` at `3f94a2968c`, with no upstream. Never work in the shared checkout `E:/StyleBI/stylebi-enterprise/community`; other sessions use it.
- **Git:** run each git command as its own call with `git -C "$WT" ...`. Never chain git commands with `&&`, because a hook blocks it. Run `git -C "$WT" branch --show-current` before every commit and expect `bug-79230`. Never push; the user pushes.
- **Commit messages:** subject `Bug #79230, <what changed>`, a plain prose body if needed, no blockquotes, ending with `Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>`.
- **Numbers:**
  - Browser target: `getContentLeft() + 2 + (controller.indent === TREE_INDENT ? 0 : LIST_INDENT)`, where `LIST_INDENT` = 4 and `TREE_INDENT` = 16. That is list 14 / 18 / 22 at inset 8 / 12 / 16, a multi-level tree 10 / 14 / 18, and 6 at inset 0.
  - Export target: `inset.left + 6`, for lists and trees alike, scaled by the coordinate helper's scale.
  - Print target: `inset.left`.
- **Gate:** the assembly's mark. In the portal that is `model.vizModern`; in Java it is `getVizMark() != null`. Never gate on the padding's presence, and never on the `.viz-shell` body class. An unmarked list seeded since #6157 carries `Insets(0,0,0,0)`, and one under a `format.css` class padding carries a real inset. Both keep today's title.
- **Applies only when:** marked, not inside a selection container (`containerType === "VSSelectionContainer"` / `getContainer() instanceof CurrentSelectionVSAssembly`), title left-aligned (`hAlign` neither `"center"` nor `"right"`), and no `format.css` title padding (`titleFormat.padding` / `getTitlePadding()` is null).
- **Unchanged:** the header band (border, background, round corner, width), the kebab and dropdown toggle, `VSObjectContainer.getLaneInset`, the container's own title, the outside-selection rows (`current-selection.component`), the `listSelectedString` readout, the dropdown panel, Excel and CSV.
- **Comments:** a short clause each. Never mention the spec, this plan, decisions, PR numbers or tickets in source code. No comments in `.html` templates.
- **New Java test files** start with the AGPL header of `core/src/test/java/inetsoft/uql/viewsheet/internal/ControlDensitySizeTest.java` (`Copyright (C) 2026  InetSoft Technology`) and use its class annotations: `@ExtendWith(SpringExtension.class)`, `@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)`, `@DirtiesContext(classMode = AFTER_CLASS)`, `@SreeHome`, `@Tag("core")`. Run each new or changed class alone and confirm it passes.

## Review Focus

1. **A legacy list, including one under a `format.css` class padding, opened in the viewer and exported.** Its rows may already be inset, but its title stays at 2px in the browser and unpadded in export and print. Pinned in Task 2 (`keeps a legacy list's title even with an inset`), Task 3 (`anUnmarkedListKeepsItsTitle`), Task 4 (`anUnmarkedSelectionTakesNoIndent`) and Task 5 (`aLegacyPrintedTitleIsUnchanged`).
2. **A modern list whose author set the inset to 0.** Its title starts at 6, over the checkbox, in the browser and in export. Pinned in Task 2 (`aligns a zero-inset list with its checkbox`) and Task 4 (`anAuthorZeroInsetStillOffsets`).
3. **A `format.css` title padding on a modern list.** It wins on every surface, a zero value included. Pinned in Task 2 (`lets a css title padding win, even zero`), Task 3 (`aCssTitlePaddingKeepsItsOwn`) and Task 4 (`theHelperPassesACssTitlePaddingThrough`).
4. **A list inside a selection container, expanded or collapsed.** Its title is unchanged in the viewer and in export. Pinned in Task 2 (`leaves a container child's title alone`) and Task 3 (`aContainerChildKeepsItsTitle`).
5. **Double-clicking a modern title in the composer.** The edit input starts its text where the label did, and a legacy edit input is untouched. Pinned in Task 2 (`moves the edit input with the default`, `keeps a legacy edit input as it was`).

## How to run tests

Java, from `$WT`. `-q` hides the summary on success, so read `core/target/surefire-reports/*.txt` for counts. `-am` builds the modules core needs, without installing anything into the shared local repository:

```bash
cd "$WT"
./mvnw -q -o test -pl core -am -Dtest=ClassName -Dsurefire.failIfNoSpecifiedTests=false
```

For several classes, use `"-Dtest=A,B,C"`. Never add `install`. If `-o` fails on a missing artifact, drop `-o` once.

Frontend: Vitest cannot run from a worktree whose `node_modules` is a junction. Task 2 runs `npm ci` in `$WT/web` once, then:

```bash
cd "$WT/web"
npx ng test portal --include="**/<spec-file>.spec.ts"
```

---

### Task 1: Amend the design note

**Files:**
- Modify: `docs/superpowers/specs/lookfeel/2026-10-22-selection-title-alignment-design.md`

Mark each change the way `2026-10-01-selection-family-padding-design.md` does: strike the replaced sentence with `~~…~~` and follow it with a paragraph that opens `**Amended 2026-10-23.**`. The content comes from the decisions file. Cite code as the note already does (`File.java:line` against `3f94a2968c`).

- [ ] **Step 1: Header.** Add to **Not in scope**: a list or tree inside a selection container, whose title keeps 2px (D7).
- [ ] **Step 2: §2.** The kebab and the dropdown toggle sit on top of the title rather than truncating it: the label spans the lane with an ellipsis. The conclusion stands, because the only cost is still the left offset.
- [ ] **Step 3: D3.**
  - The tree target is `inset.left + 2` only for a tree of more than one level, because the cell indent follows `levels > 1` (`vs-selection.component.ts:409`). A one-level tree takes the list target.
  - The browser reads `getContentLeft()`, so a dropdown title follows the open panel's horizontal inset.
- [ ] **Step 4: D4.** The gate is the mark, not "inset absent or 0". State the two facts that rule out the inset test: the unmarked zero seed, and the unmarked `format.css` padding. D5 holds under the mark gate.
- [ ] **Step 5: New D6–D9.** D6: left-aligned titles only. D7: container children keep a 2px title. D8: export uses `inset.left + 2 + LIST_INDENT` for lists and trees. D9: print pads the title box by `inset.left`. Each gets a one-paragraph *why* and *not taken* from the decisions file.
- [ ] **Step 6: §4.**
  - **Browser:** drop "or binds it on the title's wrapper", because the wrapper holds the title background. The offset is a `title-cell` input that the label and the edit input bind.
  - **Export:** name the six call sites from Task 4, and note PowerPoint's scaling.
  - **Print:** `createTitle` insets no titled assembly, so the selection callers pass the inset.
- [ ] **Step 7: §5 and §6.**
  - §5: reword the kebab check to "the ellipsis moves in by the offset; kebab and toggle positions unchanged". The export check becomes "within 2px of the first row's text (PDF/SVG/PPT) or checkbox (HTML)". Add: a container child is unchanged, and a centered title is unchanged.
  - §6: close both open items, pointing at D7.
- [ ] **Step 8: Commit**

```bash
git -C "$WT" add docs/superpowers/specs/lookfeel/2026-10-22-selection-title-alignment-design.md
git -C "$WT" commit -m "Bug #79230, amend the selection title alignment note with the implementation rulings"
```

(Add the attribution line to the message, as every commit here does.)

---

### Task 2: Browser — the title starts over the first row's glyph

**Files:**
- Modify: `web/projects/portal/src/app/vsobjects/objects/title-cell/title-cell.component.ts` (inputs, `:36-47`)
- Modify: `web/projects/portal/src/app/vsobjects/objects/title-cell/title-cell.component.html` (`:50`, `:65-77`)
- Modify: `web/projects/portal/src/app/vsobjects/objects/selection/vs-selection.component.ts` (beside `getContentLeft()`, `:969`; constants `:216-217`)
- Modify: `web/projects/portal/src/app/vsobjects/objects/selection/vs-selection.component.html` (the `<title-cell>`, `:108-123`)
- Create: `web/projects/portal/src/app/vsobjects/objects/title-cell/title-cell.component.spec.ts`
- Test: `web/projects/portal/src/app/vsobjects/objects/selection/vs-selection.component.spec.ts`

**Interfaces:**
- Produces:
  - `TitleCell`: `@Input() defaultTextLeft: number = null`.
  - `TitleCell.getTextPaddingLeft(): number` returns `titleFormat?.padding?.left ?? defaultTextLeft`. Null means the stylesheet's 2px applies.
  - `TitleCell.getEditPaddingLeft(): number` returns `defaultTextLeft == null ? null : getTextPaddingLeft()`.
  - `VSSelection.getTitleTextLeft(): number` returns the label's left padding, or null where today's 2px stays.
  - `VSSelection.CELL_CONTAINER_MARGIN: number = 2` (static), the row container's `margin-left` in `selection-list-cell.component.scss:28`.

- [ ] **Step 1: Install the worktree's frontend dependencies**

Run: `cd "$WT/web" && npm ci`
Expected: completes without errors. This is a one-time setup for this worktree.

- [ ] **Step 2: Write the failing `title-cell` spec**

Use the AGPL header from `vs-selection.component.spec.ts` (`Copyright (C) 2026  InetSoft Technology`).

```ts
import { TestUtils } from "../../../common/test/test-utils";
import { TitleCell } from "./title-cell.component";

function cell(padding: any = undefined, defaultTextLeft: number = null): TitleCell {
   const comp = new TitleCell({ viewer: true } as any);
   comp.titleFormat = Object.assign(TestUtils.createMockVSFormatModel(), { padding });
   comp.defaultTextLeft = defaultTextLeft;
   return comp;
}

describe("TitleCell text padding", () => {
   it("leaves the stylesheet's padding without a default", () => {
      expect(cell().getTextPaddingLeft()).toBeNull();
   });

   it("takes the caller's default", () => {
      expect(cell(undefined, 22).getTextPaddingLeft()).toBe(22);
   });

   it("lets a css title padding win, even zero", () => {
      expect(cell({ top: 0, left: 0, bottom: 0, right: 0 }, 22).getTextPaddingLeft()).toBe(0);
   });

   it("moves the edit input with the default", () => {
      expect(cell(undefined, 22).getEditPaddingLeft()).toBe(22);
   });

   it("keeps a legacy edit input as it was", () => {
      expect(cell({ top: 0, left: 5, bottom: 0, right: 0 }).getEditPaddingLeft()).toBeNull();
   });
});
```

- [ ] **Step 3: Write the failing `vs-selection` specs**

Append to `vs-selection.component.spec.ts`. Import `makeMockTreeModel` beside `makeMockListModel`.

```ts
describe("VSSelection title alignment", () => {
   const inset = (px: number) => ({ top: px, left: px, bottom: px, right: px });
   const modern = (fields: any = {}) =>
      ({ objectFormat: fmt(132, 202), titleFormat: fmt(132, 30), vizModern: true, ...fields });
   const list = (fields: any = {}) => createComponent(modern(fields));

   async function tree(fields: any = {}): Promise<VSSelection> {
      const model = Object.assign(makeMockTreeModel(), modern(fields));
      return (await createSelectionComponent({ model })).comp;
   }

   it("starts a modern list's title over its checkbox", async () => {
      expect((await list({ padding: inset(8) })).getTitleTextLeft()).toBe(14);
      expect((await list({ padding: inset(12) })).getTitleTextLeft()).toBe(18);
      expect((await list({ padding: inset(16) })).getTitleTextLeft()).toBe(22);
   });

   it("starts a multi-level tree's title over its root toggle", async () => {
      expect((await tree({ levels: 2, padding: inset(16) })).getTitleTextLeft()).toBe(18);
   });

   it("treats a one-level tree like a list", async () => {
      expect((await tree({ levels: 1, padding: inset(16) })).getTitleTextLeft()).toBe(22);
   });

   it("aligns a zero-inset list with its checkbox", async () => {
      expect((await list({ padding: inset(0) })).getTitleTextLeft()).toBe(6);
   });

   it("follows a dropdown's horizontal inset", async () => {
      const comp = await list({ padding: inset(16), dropdown: true, maxMode: false });

      expect(comp.getTitleTextLeft()).toBe(22);
   });

   it("keeps a legacy list's title even with an inset", async () => {
      expect((await list({ vizModern: false, padding: inset(16) })).getTitleTextLeft()).toBeNull();
   });

   it("leaves a container child's title alone", async () => {
      const comp = await list({ padding: inset(16), containerType: "VSSelectionContainer" });

      expect(comp.getTitleTextLeft()).toBeNull();
   });

   it("leaves a centered or right-aligned title alone", async () => {
      for(const hAlign of ["center", "right"]) {
         const titleFormat = fmt(132, 30);
         titleFormat.hAlign = hAlign;

         expect((await list({ padding: inset(16), titleFormat })).getTitleTextLeft()).toBeNull();
      }
   });
});
```

- [ ] **Step 4: Run both specs to verify they fail**

Run: `npx ng test portal --include="**/title-cell.component.spec.ts"`, then the same with `--include="**/vs-selection.component.spec.ts"`.
Expected: FAIL, because `getTextPaddingLeft`, `getEditPaddingLeft`, `defaultTextLeft` and `getTitleTextLeft` do not exist.

- [ ] **Step 5: Implement**
  - `TitleCell`: the input and the two methods from Interfaces.
  - `title-cell.component.html`: the label's `[style.padding-left.px]` binds `getTextPaddingLeft()`, and the edit `<input>` gains `[style.padding-left.px]="getEditPaddingLeft()"`.
  - `VSSelection.getTitleTextLeft()`: null unless `model.vizModern`, `!inContainer`, and `titleFormat.hAlign` is neither `"center"` nor `"right"`. Otherwise return `getContentLeft() + CELL_CONTAINER_MARGIN + (controller.indent === VSSelection.TREE_INDENT ? 0 : VSSelection.LIST_INDENT)`.
  - `vs-selection.component.html`: the `<title-cell>` gains `[defaultTextLeft]="getTitleTextLeft()"`.

  The `format.css` title padding needs no check in `getTitleTextLeft()`, because `title-cell` already lets it win. `strictTemplates` is off, so re-read both template edits by eye.

- [ ] **Step 6: Run the specs to verify they pass**

Run: the two Step 4 commands, then `--include="**/vs-selection*.spec.ts"` to cover the neighbouring selection specs.
Expected: PASS, with no other test in those files changed.

- [ ] **Step 7: Commit**

```bash
git -C "$WT" add web/projects/portal/src/app/vsobjects/objects/title-cell web/projects/portal/src/app/vsobjects/objects/selection/vs-selection.component.ts web/projects/portal/src/app/vsobjects/objects/selection/vs-selection.component.html web/projects/portal/src/app/vsobjects/objects/selection/vs-selection.component.spec.ts
git -C "$WT" commit -m "Bug #79230, start a modern selection title over its first row's glyph"
```

---

### Task 3: The shared predicate for export and print

**Files:**
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/SelectionBaseVSAssemblyInfo.java` (beside `getTitlePadding()`, `:415`)
- Create: `core/src/test/java/inetsoft/uql/viewsheet/internal/SelectionTitleOverRowsTest.java`

**Interfaces:**
- Produces: `public boolean isTitleOverRows()` on `SelectionBaseVSAssemblyInfo`. It is true when all of these hold:
  - `getVizMark() != null`;
  - `getTitlePadding() == null`;
  - the title is left-aligned;
  - the assembly is not in a selection container.

  The alignment rule mirrors `VSCSSUtil.gethAlign`. Take the alignment of `getFormatInfo().getFormat(TITLEPATH, false)`. It is left when that format is null, when `(align & H_LEFT) != 0`, or when `(align & (H_CENTER | H_RIGHT | H_CURRENCY)) == 0`.

  For the container check, look up `getViewsheet().getAssembly(getName())`. If the result is a `VSAssembly` whose `getContainer()` is a `CurrentSelectionVSAssembly`, the assembly is in a container. A null viewsheet counts as standalone.

- [ ] **Step 1: Write the failing tests** in `SelectionTitleOverRowsTest`. Use a `Viewsheet` with a `SelectionListVSAssembly "List1"` added. Mark with `info.setVizMark(VizMark.MODERN_LIGHT)`.
  - `aMarkedStandaloneListTakesIt`: `assertTrue(info.isTitleOverRows())`.
  - `aMarkedTreeTakesIt`: the same for a `SelectionTreeVSAssembly`.
  - `anUnmarkedListKeepsItsTitle`: no mark, `setPadding(new Insets(16, 16, 16, 16))`; `assertFalse`.
  - `aCssTitlePaddingKeepsItsOwn`: marked, `setTitlePadding(new Insets(0, 9, 0, 0), CompositeValue.Type.CSS)`; `assertFalse`.
  - `aCenteredOrRightTitleKeepsItsPlace`: marked, with the title format's user alignment `StyleConstants.H_CENTER`, then `H_RIGHT`. Create the format the way `PrintLayoutConverterFixture.titleFormat()` does. `assertFalse` for each.
  - `aContainerChildKeepsItsTitle`: marked list named in a `CurrentSelectionVSAssembly` through `setAssemblies(new String[] { "List1" })`, both added to the viewsheet; `assertFalse`.

- [ ] **Step 2: Run it to verify it fails**

Run: `./mvnw -q -o test -pl core -am -Dtest=SelectionTitleOverRowsTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL to compile, because `isTitleOverRows` is not defined.

- [ ] **Step 3: Implement `isTitleOverRows()`** as specified in Interfaces, with a one-clause javadoc.

- [ ] **Step 4: Run it to verify it passes**

Run: the Step 2 command.
Expected: PASS, 6 tests.

- [ ] **Step 5: Commit**

```bash
git -C "$WT" add core/src/main/java/inetsoft/uql/viewsheet/internal/SelectionBaseVSAssemblyInfo.java core/src/test/java/inetsoft/uql/viewsheet/internal/SelectionTitleOverRowsTest.java
git -C "$WT" commit -m "Bug #79230, name when a selection title starts over its rows"
```

---

### Task 4: Export — the title takes the indent in every card format

**Files:**
- Modify: `core/src/main/java/inetsoft/report/io/viewsheet/VSExporter.java` (beside `getSelectionCardInset`, `:92`)
- Modify: `core/src/main/java/inetsoft/report/io/viewsheet/AbstractVSExporter.java` (beside `getSelectionCardInset`, `:2027`)
- Modify: `core/src/main/java/inetsoft/report/io/viewsheet/ExporterHelper.java` (beside `getCardInset`, `:75`)
- Modify (the six title sites):
  - `core/src/main/java/inetsoft/report/io/viewsheet/pdf/PDFSelectionListHelper.java:119-120`
  - `core/src/main/java/inetsoft/report/io/viewsheet/svg/SVGSelectionListHelper.java:106-108`
  - `utils/inetsoft-xml-formats/src/main/java/inetsoft/report/io/viewsheet/ppt/PPTSelectionListHelper.java:117`
  - `core/src/main/java/inetsoft/report/io/viewsheet/VSSelectionTreeHelper.java:268-269`
  - `core/src/main/java/inetsoft/report/io/viewsheet/html/HTMLSelectionListHelper.java:99`
  - `core/src/main/java/inetsoft/report/io/viewsheet/html/HTMLSelectionTreeHelper.java:87`
- Modify: `core/src/main/java/inetsoft/report/io/viewsheet/html/HTMLCoordinateHelper.java` (`appendContainerTitle`, `:1094-1108`)
- Create: `core/src/test/java/inetsoft/report/io/viewsheet/SelectionTitleIndentExportTest.java`
- Create: `core/src/test/java/inetsoft/report/io/viewsheet/html/HTMLSelectionTitleIndentTest.java`
- Test: `core/src/test/java/inetsoft/report/io/viewsheet/SelectionListExporterWiringTest.java`

**Interfaces:**
- Consumes: `SelectionBaseVSAssemblyInfo.isTitleOverRows()` (Task 3).
- Produces:
  - `VSExporter`: `default int getSelectionTitleIndent(SelectionBaseVSAssemblyInfo info)`, returning 0. Its javadoc says it is the pixel left padding that starts a selection's title over its rows in this format, or 0.
  - `AbstractVSExporter` overrides it: `insetsTableCard() && info != null && info.isTitleOverRows()` ? `getSelectionCardInset(info).left + SELECTION_TITLE_ROW_OFFSET` : 0. The constant is `private static final int SELECTION_TITLE_ROW_OFFSET = 6`, commented as a browser row's 2px cell margin plus its 4px list indent.
  - `ExporterHelper`: `protected Insets getTitlePadding(SelectionBaseVSAssemblyInfo info)` delegates with scale 1, and `protected Insets getTitlePadding(SelectionBaseVSAssemblyInfo info, double scale)` does the work. With an exporter and an indent > 0, it returns `new Insets(0, (int) Math.round(indent * scale), 0, 0)`; otherwise it returns `info.getTitlePadding()` unchanged. A CSS title padding is never scaled, as today.
  - `HTMLCoordinateHelper.appendContainerTitle(StringBuffer, SelectionVSAssemblyInfo, int, double, String, Insets padding)`: the existing five-argument form delegates with `((TitledVSAssemblyInfo) info).getTitlePadding()`, so the time slider caller at `:1063` is unchanged.

- [ ] **Step 1: Write the failing resolution tests** in `SelectionTitleIndentExportTest`. Its infos are bare and marked with `setVizMark(VizMark.MODERN_LIGHT)` plus `setPadding(new Insets(px, px, px, px))`; no viewsheet means standalone. Use `new HTMLVSExporter(new ByteArrayOutputStream())` unless stated.
  - `aMarkedListsTitleStartsOverItsRows`: indent 14 / 18 / 22 at inset 8 / 12 / 16.
  - `aTreeTakesTheListOffset`: a marked tree at 16 → 22.
  - `anAuthorZeroInsetStillOffsets`: a marked list at 0 → 6.
  - `excelAndCsvTakeNoIndent`: `Mockito.mock(ExcelVSExporter.class, Mockito.CALLS_REAL_METHODS)` and `new CSVVSExporter()` → 0, as `SelectionExportInsetTest.excelTreeTakesNoInset` builds them.
  - `anUnmarkedSelectionTakesNoIndent`: no mark, inset 16 → 0.
  - `theHelperScalesTheIndent`: `VSSelectionListHelper` with the HTML exporter, `getTitlePadding(info, 0.75)` at inset 16 → `new Insets(0, 17, 0, 0)`.
  - `theHelperPassesACssTitlePaddingThrough`: marked, with `setTitlePadding(new Insets(1, 9, 1, 3), CompositeValue.Type.CSS)` → `assertEquals(new Insets(1, 9, 1, 3), helper.getTitlePadding(info))`.
  - `theHelperGivesNoPaddingWithoutAnIndent`: unmarked → `assertNull(helper.getTitlePadding(info))`.
  - `theTreeTitleTakesTheIndent`: a `VSSelectionTreeHelper` subclass that overrides `writeText(Rectangle2D, VSCompositeFormat, String, VSCompositeFormat, Insets)` (`VSSelectionTreeHelper.java:298`) to capture the padding. Set the exporter, `boundsList = List.of(new Rectangle2D.Double(0, 0, 132, 30))`, and a mocked `cHelper` whose `getScale()` is 1.0. The tree is a `SelectionTreeVSAssembly` added to a `Viewsheet`, because `getTitle()` reads the info's viewsheet. It is marked, at inset 16, with `setTitleValue("Region")`. `writeTitle(info)` captures `new Insets(0, 22, 0, 0)`.

- [ ] **Step 2: Write the failing HTML and wiring tests**
  - `HTMLSelectionTitleIndentTest`: build the list and tree the way `HTMLSelectionCardInsetTest.writeList` / `writeTree` do, but marked, at inset 16, with `setTitleVisibleValue(true)` and `setTitleValue("Region")`. `appendContainerTitle` writes the span only for a non-null title.
    - `aMarkedListsTitleSpanStartsOverItsRows` and `aMarkedTreesTitleSpanStartsOverItsRows`: the HTML contains `<span style='display:block;padding-left:22px;padding-right:0px;padding-top:0px;padding-bottom:0px;'>`.
    - `anUnmarkedTitleSpanIsUnchanged`: unmarked, so it contains `<span style='display:block;'>`.
  - `SelectionListExporterWiringTest`: add `pdfResolvesAListsTitleIndentThroughItself`. Mark `markedList()`'s info, run `prepareAssembly` / `writeSelectionList` on the spied PDF exporter, then `verify(exporter, atLeastOnce()).getSelectionTitleIndent(info)`.

- [ ] **Step 3: Run them to verify they fail**

Run: `./mvnw -q -o test -pl core -am "-Dtest=SelectionTitleIndentExportTest,HTMLSelectionTitleIndentTest,SelectionListExporterWiringTest" -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL to compile, because `getSelectionTitleIndent` and `getTitlePadding` are not defined.

- [ ] **Step 4: Implement** the Interfaces block, then switch the six sites from `info.getTitlePadding()`:
  - PDF, SVG and tree: pass `getTitlePadding(info, cHelper.getScale())`.
  - PPT: `helper.setPadding(getTitlePadding(info, cHelper.getScale()))`, which carries `PIXEL_TO_POINT`.
  - HTML list and tree: pass `getTitlePadding(info)` as the new last argument of `appendContainerTitle`.

  Leave `VSSelectionListHelper.write`'s `writeTitleInContainer(..., info.getTitlePadding())` as it is, because container children keep their title. `isTitleOverRows()` would answer false there anyway.

- [ ] **Step 5: Run them to verify they pass**

Run: the Step 3 command, then `"-Dtest=SelectionExportInsetTest,HTMLSelectionCardInsetTest"` to cover the neighbours.
Expected: PASS, with the neighbours unchanged.

- [ ] **Step 6: Compile the PowerPoint module**

Run: `./mvnw -q -o compile -pl utils/inetsoft-xml-formats -am`
Expected: BUILD SUCCESS. Nothing in `core` tests `PPTSelectionListHelper`, so Task 6 checks it by exporting.

- [ ] **Step 7: Commit**

```bash
git -C "$WT" add core/src/main/java/inetsoft/report/io/viewsheet utils/inetsoft-xml-formats/src/main/java/inetsoft/report/io/viewsheet/ppt/PPTSelectionListHelper.java core/src/test/java/inetsoft/report/io/viewsheet/SelectionTitleIndentExportTest.java core/src/test/java/inetsoft/report/io/viewsheet/html/HTMLSelectionTitleIndentTest.java core/src/test/java/inetsoft/report/io/viewsheet/SelectionListExporterWiringTest.java
git -C "$WT" commit -m "Bug #79230, export a modern selection title over its rows"
```

---

### Task 5: Print layout — the title box follows the inset

**Files:**
- Modify: `core/src/main/java/inetsoft/uql/viewsheet/internal/VsToReportConverter.java` (`createTitle`, `:1446-1493`; `addSelectionList` `:1876`; `addSelectionTree` `:1926`)
- Test: `core/src/test/java/inetsoft/uql/viewsheet/internal/SelectionPrintInsetTest.java`

**Interfaces:**
- Consumes: `SelectionBaseVSAssemblyInfo.isTitleOverRows()` (Task 3).
- Produces:
  - `private Rectangle createTitle(VSAssembly assembly, String title, String sectionName, boolean onlyTitle, int titleIndent)`: after `applyFormat`, when `titleIndent > 0`, clone the text box's padding (a zero `Insets` if null), set `left = titleIndent` and store it. The existing four-argument form delegates with 0, so no other assembly changes.
  - `private static int getTitleIndent(SelectionBaseVSAssemblyInfo info)`: `info.isTitleOverRows() && info.getPadding() != null ? info.getPadding().left : 0`. `addSelectionList` and `addSelectionTree` pass it to `createTitle`.

- [ ] **Step 1: Write the failing tests** in `SelectionPrintInsetTest`.
  - `place()` hides the title, so these call `setTitleVisible(true)` after it.
  - With no selected values the converter adds only the title box, so `convertedBoxFor` returns it unchanged.
  - The baseline is `new TextBoxElementDef(new TabularSheet(null, null), new DefaultTextLens("x")).getPadding()`, as `aLegacySelectionPrintsUnchanged` builds it.

  The tests:
  - `thePrintedTitleStartsOverTheValues`: `list("comfortable")` with the title visible → the baseline with `left = 16`.
  - `thePrintedTreeTitleStartsOverTheValues`: a marked comfortable tree, set up as in `thePrintedTreeTakesItsInset`, with the title visible → the baseline with `left = 16`.
  - `aLegacyPrintedTitleIsUnchanged`: an unmarked list with the title visible → the baseline.

- [ ] **Step 2: Run it to verify it fails**

Run: `./mvnw -q -o test -pl core -am -Dtest=SelectionPrintInsetTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL. The two marked tests see the baseline's left, not 16.

- [ ] **Step 3: Implement** the Interfaces block.

- [ ] **Step 4: Run it to verify it passes**

Run: the Step 2 command, then `"-Dtest=PrintLayoutCardTopTest,PrintLayoutCardArithmeticTest,PrintLayoutCellPaddingTest"` to cover the converter's other titled paths.
Expected: PASS, 9 tests in `SelectionPrintInsetTest`, with the neighbours unchanged.

- [ ] **Step 5: Commit**

```bash
git -C "$WT" add core/src/main/java/inetsoft/uql/viewsheet/internal/VsToReportConverter.java core/src/test/java/inetsoft/uql/viewsheet/internal/SelectionPrintInsetTest.java
git -C "$WT" commit -m "Bug #79230, pad a modern selection's printed title by its card inset"
```

---

### Task 6: Live check

Run on a server built from `bug-79230`. Results go in the PR description, not in the repository.

- [ ] **Step 1: Browser, at comfortable / compact / dense.**
  - Use a marked list, a two-level tree, a one-level tree and a dropdown list (open).
  - For each, in the viewer and the composer, compare the left of the label's text box with the left of the first row's glyph. The text box is `.selection-list-title-label`'s rect plus its computed `padding-left`. The glyph is `.selection-list-icon-layout` plus its `padding-left` for a list; for a tree, it is the toggle's rect.
  - Expected: equal within 1px, at 14 / 18 / 22 from the card's inner edge for a list.
- [ ] **Step 2: Unchanged cases.**
  - Expected: no change on a legacy list (Revert), a list in a selection container (expanded and collapsed) and a centered title.
  - Expected: the kebab and dropdown toggle sit where they did, and only the ellipsis moves in.
  - On a long title at the 132px comfortable default, the ellipsis moves in by 20px.
- [ ] **Step 3: Composer edit.** Double-click a modern title.
  - Expected: the input's text starts where the label's did.
- [ ] **Step 4: Export, Match Layout.** Export the same dashboard to PDF, PNG (SVG path), PPTX and HTML.
  - Expected: the title text sits within 2px of the first row's text (PDF/PNG/PPTX) or checkbox (HTML).
  - Expected: a container child and the legacy dashboard are byte- or pixel-identical to an export from `epic-74519`.
  - Expected: Excel is unchanged.
- [ ] **Step 5: Print layout.**
  - Expected: a marked list's printed title starts at its value text's left edge.
  - Expected: a legacy list prints as before.
