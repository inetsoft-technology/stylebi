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
package inetsoft.web.wiz.script;

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.viewsheet.*;
import inetsoft.web.wiz.pairing.*;
import inetsoft.web.wiz.script.model.ScriptInfo;
import inetsoft.web.wiz.script.model.ScriptTargetInfo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The synthetic target, label, read and note of a {@code vsHyperlinkExpression} session, and the
 * list/read paths of {@code ScriptReadService} that serve it (Bug #80459).
 */
@WizAgentTestSupport
class VsHyperlinkTargetsTest {
   private static EditorContext grant(String assembly, String name) {
      return new EditorContext("vsHyperlinkExpression", assembly, name, null);
   }

   private static JoinSession session(EditorContext ctx) {
      return new JoinSession("TOK", "Viewsheet/foo", "alice~;~host-org", SheetType.VIEWSHEET, 0L,
         Long.MAX_VALUE, JoinSession.ConnectionMode.PAIRED, null, null, ctx);
   }

   private static RuntimeViewsheet runtime() {
      Viewsheet vs = new Viewsheet();
      vs.addAssembly(new TextVSAssembly(vs, "Text1"));
      vs.addAssembly(new TableVSAssembly(vs, "Table1"));
      vs.addAssembly(new ChartVSAssembly(vs, "Chart1"));
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      return rvs;
   }

   // ---- targetInfo ----------------------------------------------------------------------------

   @Test
   void theTargetDescribesTheCellInTheDesignWords() {
      ScriptTargetInfo info =
         VsHyperlinkTargets.targetInfo(grant("Table1", "cell[2,1]"), "table");

      assertEquals("vsHyperlinkExpression", info.kind());
      assertEquals("Table1", info.assembly());
      assertEquals("cell[2,1]", info.name());
      assertEquals("Expression of the hyperlink of cell [2,1] of Table 'Table1' (set_hyperlink " +
                   "row 2, col 1; draft-only: the value is in the user's open Hyperlink dialog)",
                   info.label());
      assertEquals("when the link is clicked, once the user applies the dialog", info.runsWhen());
      assertEquals("vsHyperlinkExpression:Table1:cell[2,1]", info.enableScope());
      assertEquals("viewsheet", info.hostSheet());
      assertEquals(Boolean.TRUE, info.pending());
      assertFalse(info.hasScript());
      assertTrue(info.enabled());
      assertNull(info.target());
   }

   @Test
   void theIdDecodesBackToTheGrantedTuple() throws Exception {
      ScriptTargetInfo info =
         VsHyperlinkTargets.targetInfo(grant("Chart1", "field[Total Sales]"), "chart");
      ScriptTarget back = ScriptTarget.fromId(info.id());

      assertSame(ScriptTarget.Kind.VS_HYPERLINK_EXPRESSION, back.kind());
      assertEquals("Chart1", back.assemblyName());
      assertEquals("field[Total Sales]", back.name());
   }

   @Test
   void everyRegionFormIsWordedAndNamesItsToolArguments() {
      String[][] cases = {
         { "Text1", "link", "text", "Expression of the hyperlink of Text 'Text1' " +
            "(set_hyperlink with no region argument;" },
         { "Chart1", "title", "chart", "Expression of the title hyperlink of Chart 'Chart1' " +
            "(set_hyperlink titleLink: true;" },
         { "Chart1", "emptyPlot", "chart", "Expression of the empty plot hyperlink of Chart " +
            "'Chart1' (set_hyperlink emptyPlotLink: true;" },
         { "Chart1", "field[Total Sales]", "chart", "Expression of the hyperlink of field " +
            "'Total Sales' of Chart 'Chart1' (set_hyperlink colName 'Total Sales';" },
         { "Chart1", "axis[Region]", "chart", "Expression of the hyperlink of the axis of " +
            "'Region' of Chart 'Chart1' (set_hyperlink colName 'Region', axis: true;" },
         { "Chart1", "text[Sales]", "chart", "Expression of the hyperlink of text 'Sales' of " +
            "Chart 'Chart1' (set_hyperlink colName 'Sales', text: true;" },
         { "Calc1", "cell[0,3]", "calctable", "Expression of the hyperlink of cell [0,3] of " +
            "Freehand Table 'Calc1' (set_hyperlink row 0, col 3;" } };

      for(String[] c : cases) {
         ScriptTargetInfo info = VsHyperlinkTargets.targetInfo(grant(c[0], c[1]), c[2]);

         assertTrue(info.label().startsWith(c[3]), info.label());
      }
   }

   @Test
   void anUnknownAssemblyTypeOmitsTheTypeWord() {
      ScriptTargetInfo info = VsHyperlinkTargets.targetInfo(grant("Gone", "link"), null);

      assertTrue(info.label().startsWith("Expression of the hyperlink of 'Gone' ("), info.label());
   }

   @Test
   void aMalformedGrantHasNoTarget() {
      assertNull(VsHyperlinkTargets.targetInfo(grant("Table1", "cell[1]"), "table"));
      assertNull(VsHyperlinkTargets.targetInfo(grant("", "link"), "text"));
      assertNull(VsHyperlinkTargets.targetInfo(grant("Text1", null), "text"));
   }

   // ---- read / note ---------------------------------------------------------------------------

   @Test
   void theReadIsAnEmptyPendingDraftThatSaysSoAndNamesTheReadBackTool() throws Exception {
      ScriptTarget t = ScriptTarget.of(ScriptTarget.Kind.VS_HYPERLINK_EXPRESSION, "Table1",
                                       "cell[2,1]");
      ScriptInfo info = VsHyperlinkTargets.read(t);

      assertEquals("", info.text());
      assertEquals(Boolean.TRUE, info.pending());
      assertTrue(info.enabled());
      assertEquals("vsHyperlinkExpression:Table1:cell[2,1]", info.target());
      assertTrue(info.note().startsWith("Draft-only location: the Web Link Expression of ONE " +
                                        "hyperlink in the user's open Hyperlink dialog"),
                 info.note());
      assertTrue(info.note().contains("NOT an empty expression and NOT a failed update_script"),
                 info.note());
      assertTrue(info.note().contains("get_hyperlink {assembly: 'Table1', row: 2, col: 1}"),
                 info.note());
      assertTrue(info.note().contains("Apply to Row"), info.note());
   }

   @Test
   void theRowLinkWarningOnlyAppearsForACell() throws Exception {
      ScriptTarget text = ScriptTarget.of(ScriptTarget.Kind.VS_HYPERLINK_EXPRESSION, "Text1",
                                          "link");

      assertFalse(VsHyperlinkTargets.note(text, false).contains("Apply to Row"));
      assertFalse(VsHyperlinkTargets.note(text, true).contains("Apply to Row"));
      assertTrue(VsHyperlinkTargets.note(text, false).contains("get_hyperlink {assembly: " +
                                                               "'Text1'}"));
   }

   @Test
   void theWriteNoteSaysNothingIsAppliedAndMentionsTheMessageLinkCase() throws Exception {
      ScriptTarget t = ScriptTarget.of(ScriptTarget.Kind.VS_HYPERLINK_EXPRESSION, "Chart1",
                                       "title");
      String note = VsHyperlinkTargets.note(t, true);

      assertTrue(note.contains("Nothing is applied"), note);
      assertTrue(note.contains("=\"message:"), note);
      assertTrue(note.contains("get_hyperlink {assembly: 'Chart1', titleLink: true}"), note);
   }

   // ---- through ScriptReadService -------------------------------------------------------------

   @Test
   void aPaneSessionListsExactlyItsOwnTargetWithTheTypeFromTheRuntime() {
      ScriptReadService read = new ScriptReadService();
      List<ScriptTargetInfo> targets =
         read.list(runtime(), session(grant("Table1", "cell[2,1]")));

      assertEquals(1, targets.size());
      assertEquals("vsHyperlinkExpression", targets.get(0).kind());
      assertTrue(targets.get(0).label().contains("Table 'Table1'"), targets.get(0).label());
   }

   @Test
   void aGrantOnAnAssemblyThatWasDeletedStillListsAndTheReadNamesTheMissingAssembly()
      throws Exception
   {
      ScriptReadService read = new ScriptReadService();
      RuntimeViewsheet rvs = runtime();
      List<ScriptTargetInfo> targets = read.list(rvs, session(grant("Gone", "link")));

      assertEquals(1, targets.size());

      PairingException ex = assertThrows(PairingException.class, () -> read.read(rvs,
         ScriptTarget.of(ScriptTarget.Kind.VS_HYPERLINK_EXPRESSION, "Gone", "link")));
      assertEquals("Assembly not found: Gone", ex.getMessage());
   }

   @Test
   void readServiceReturnsThePendingDraftForAnExistingAssembly() throws Exception {
      ScriptInfo info = new ScriptReadService().read(runtime(),
         ScriptTarget.of(ScriptTarget.Kind.VS_HYPERLINK_EXPRESSION, "Text1", "link"));

      assertEquals("", info.text());
      assertEquals(Boolean.TRUE, info.pending());
   }

   @Test
   void aWholeSheetSessionListsNoHyperlinkTarget() {
      List<ScriptTargetInfo> targets = new ScriptReadService().list(runtime(), session(null));

      assertTrue(targets.stream().noneMatch(t -> "vsHyperlinkExpression".equals(t.kind())));
   }

   @Test
   void anotherDraftKindStillListsItsOwnTargetNotAHyperlinkOne() {
      ScriptReadService read = new ScriptReadService();
      List<ScriptTargetInfo> dynamic = read.list(runtime(),
         session(new EditorContext("dynamicValue", "Table1", "title", null)));

      assertEquals(1, dynamic.size());
      assertEquals("dynamicValue", dynamic.get(0).kind());
   }

   @Test
   void theWorksheetKindsAreRefusedByTheViewsheetReadWithWhereToGo() {
      for(ScriptTarget.Kind kind : new ScriptTarget.Kind[] {
         ScriptTarget.Kind.WORKSHEET_VARIABLE_DEFAULT, ScriptTarget.Kind.WORKSHEET_SQL_QUERY })
      {
         PairingException ex = assertThrows(PairingException.class,
            () -> new ScriptReadService().read(runtime(), ScriptTarget.of(kind, "X")));

         assertTrue(ex.getMessage().contains("is a worksheet draft target"), ex.getMessage());
      }
   }
}
