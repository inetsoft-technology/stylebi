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
import inetsoft.uql.viewsheet.TableVSAssembly;
import inetsoft.uql.viewsheet.TextVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.web.wiz.pairing.*;
import inetsoft.web.wiz.script.model.ScriptWriteResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * The draft-only write of a {@code vsHyperlinkExpression} (Bug #80459). The decisive properties:
 * the pushed text always carries ONE leading '=' (the Hyperlink dialog's editor holds the
 * expression with it), a syntax error and a blank are refused before any push, and the runtime
 * viewsheet is never mutated (the dialog echoes its write revision back on OK).
 */
@WizAgentTestSupport
class ScriptEditServiceHyperlinkTest {
   private RuntimeViewsheet rvs;
   private SheetAgentBroadcastService broadcast;
   private ScriptEditService service;
   private final Principal agent = TestPrincipals.user("alice", "host-org");

   @BeforeEach
   void setUp() {
      Viewsheet vs = new Viewsheet();
      vs.addAssembly(new TextVSAssembly(vs, "Text1"));
      vs.addAssembly(new TableVSAssembly(vs, "Table1"));
      rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      when(rvs.getID()).thenReturn("Viewsheet/foo-7");
      broadcast = mock(SheetAgentBroadcastService.class);
      when(broadcast.pushScriptRefresh(any(), any(), any(), any(), any(), any(), any()))
         .thenReturn(true);
      // The script engine is not initialised in unit tests, so "1 +" is told apart by hand.
      service = new ScriptEditService(mock(SheetSessionService.class),
                                      mock(SheetRuntimeAccess.class), broadcast) {
         @Override
         String conditionSyntaxProblem(String text) {
            return text.trim().endsWith("+") ? "unexpected end of input" : null;
         }
      };
   }

   private static ScriptTarget link(String assembly, String region) throws Exception {
      return ScriptTarget.of(ScriptTarget.Kind.VS_HYPERLINK_EXPRESSION, assembly, region);
   }

   private void assertNothingPushed() {
      verify(broadcast, never()).pushScriptRefresh(any(), any(), any(), any(), any(), any(),
                                                    any());
   }

   @Test
   void aTextWithoutEqualsIsPushedWithOneAndTheNoteSaysSo() throws Exception {
      ScriptWriteResult result = service.writeDraft(rvs, agent, link("Text1", "link"), "x");

      verify(broadcast).pushScriptRefresh(rvs, "Viewsheet/foo-7", agent, "vsHyperlinkExpression",
                                          "Text1", "link", "=x");
      assertTrue(result.ok());
      assertEquals(Boolean.TRUE, result.pending());
      assertNull(result.warning());
      assertTrue(result.note().contains("Nothing is applied"), result.note());
      assertTrue(result.note().endsWith(" A leading '=' was added: the dialog's editor holds " +
                                        "the expression with it."), result.note());
   }

   @Test
   void aTextWithEqualsIsPushedUnchangedWithoutTheExtraSentence() throws Exception {
      ScriptWriteResult result =
         service.writeDraft(rvs, agent, link("Text1", "link"), "=\"https://x/\" + 1");

      verify(broadcast).pushScriptRefresh(rvs, "Viewsheet/foo-7", agent, "vsHyperlinkExpression",
                                          "Text1", "link", "=\"https://x/\" + 1");
      assertFalse(result.note().contains("leading '='"), result.note());
   }

   @Test
   void theMessageLinkFormIsPushedAsIs() throws Exception {
      service.writeDraft(rvs, agent, link("Text1", "link"), "=\"message:hello\"");

      verify(broadcast).pushScriptRefresh(rvs, "Viewsheet/foo-7", agent, "vsHyperlinkExpression",
                                          "Text1", "link", "=\"message:hello\"");
   }

   @Test
   void pushesExactlyTheGrantedTupleForEveryRegion() throws Exception {
      for(String region : new String[] { "title", "emptyPlot", "field[Total Sales]",
                                         "axis[Region]", "text[Sales]", "cell[2,1]" })
      {
         String assembly = region.startsWith("cell") ? "Table1" : "Text1";

         service.writeDraft(rvs, agent, link(assembly, region), "y");

         verify(broadcast).pushScriptRefresh(rvs, "Viewsheet/foo-7", agent,
            "vsHyperlinkExpression", assembly, region, "=y");
      }
   }

   @Test
   void aDoubleEqualsABareEqualsAndABlankAreRefusedBeforeAnyPush() throws Exception {
      for(String bad : new String[] { "==x", "=", " = ", "", "   " }) {
         PairingException ex = assertThrows(PairingException.class,
            () -> service.writeDraft(rvs, agent, link("Text1", "link"), bad), bad);

         assertEquals(PairingException.Kind.INVALID_ARGUMENT, ex.getKind(), ex.getMessage());
         assertTrue(ex.getMessage().contains("Nothing was pushed"), ex.getMessage());
      }

      assertNothingPushed();
   }

   @Test
   void aDollarCallIsRefusedBeforeAnyPushWhetherOrNotTheTextStartsWithEquals() throws Exception {
      for(String bad : new String[] { "$(x)", "=$(x)", "String($(x)) == 'x'" }) {
         PairingException ex = assertThrows(PairingException.class,
            () -> service.writeDraft(rvs, agent, link("Text1", "link"), bad), bad);

         assertEquals(PairingException.Kind.INVALID_ARGUMENT, ex.getKind(), ex.getMessage());
         assertTrue(ex.getMessage().contains("That expression calls $(...). A Hyperlink " +
            "Expression is JavaScript and $ is not defined in it"), ex.getMessage());
         assertTrue(ex.getMessage().contains("parameter.<name>"), ex.getMessage());
         assertTrue(ex.getMessage().contains("Nothing was pushed"), ex.getMessage());
      }

      assertNothingPushed();
   }

   @Test
   void aDollarInsideAStringLiteralOrAMemberCallIsNotACallAndIsPushed() throws Exception {
      service.writeDraft(rvs, agent, link("Text1", "link"), "=\"message:$(x)\"");
      service.writeDraft(rvs, agent, link("Text1", "link"), "a.$(1)");

      verify(broadcast).pushScriptRefresh(rvs, "Viewsheet/foo-7", agent, "vsHyperlinkExpression",
                                          "Text1", "link", "=\"message:$(x)\"");
      verify(broadcast).pushScriptRefresh(rvs, "Viewsheet/foo-7", agent, "vsHyperlinkExpression",
                                          "Text1", "link", "=a.$(1)");
   }

   @Test
   void nullIsRefused() throws Exception {
      PairingException ex = assertThrows(PairingException.class,
         () -> service.writeDraft(rvs, agent, link("Text1", "link"), null));

      assertTrue(ex.getMessage().contains("text is required."), ex.getMessage());
      assertNothingPushed();
   }

   @Test
   void aSyntaxErrorIsRefusedWhetherOrNotTheTextStartsWithEquals() throws Exception {
      for(String bad : new String[] { "1 +", "=1 +" }) {
         PairingException ex = assertThrows(PairingException.class,
            () -> service.writeDraft(rvs, agent, link("Text1", "link"), bad), bad);

         assertTrue(ex.getMessage().contains("The expression does not parse as a JavaScript " +
            "expression (unexpected end of input). Nothing was pushed."), ex.getMessage());
      }

      assertNothingPushed();
   }

   @Test
   void theSyntaxCheckSeesTheTextWithoutTheEquals() throws Exception {
      java.util.List<String> seen = new java.util.ArrayList<>();
      ScriptEditService recording = new ScriptEditService(mock(SheetSessionService.class),
         mock(SheetRuntimeAccess.class), broadcast) {
         @Override
         String conditionSyntaxProblem(String text) {
            seen.add(text);
            return null;
         }
      };

      recording.writeDraft(rvs, agent, link("Text1", "link"), "a + b");
      recording.writeDraft(rvs, agent, link("Text1", "link"), "=a + b");

      assertEquals(java.util.List.of("a + b", "a + b"), seen);
   }

   @Test
   void aMissingAssemblyFailsLoudBeforeAnyPush() throws Exception {
      PairingException ex = assertThrows(PairingException.class,
         () -> service.writeDraft(rvs, agent, link("Gone", "link"), "x"));

      assertEquals("Assembly not found: Gone", ex.getMessage());
      assertNothingPushed();
   }

   @Test
   void anUnreachableDialogFailsLoud() throws Exception {
      when(broadcast.pushScriptRefresh(any(), any(), any(), any(), any(), any(), any()))
         .thenReturn(false);

      PairingException ex = assertThrows(PairingException.class,
         () -> service.writeDraft(rvs, agent, link("Text1", "link"), "x"));

      assertTrue(ex.getMessage().contains(
         "is not reachable (closed or disconnected), so nothing was pushed"), ex.getMessage());
      assertTrue(ex.getMessage().contains("vsHyperlinkExpression:Text1:link"), ex.getMessage());
   }

   @Test
   void neverTouchesTheRuntimeViewsheetNorBumpsTheWriteRevision() throws Exception {
      service.writeDraft(rvs, agent, link("Text1", "link"), "x");

      verify(rvs, never()).bumpWriteRevision();
      verify(rvs, never()).addCheckpoint(any());
      verify(broadcast, never()).broadcastRefresh(any(), any(), any(), any());
      verify(broadcast, never()).broadcastScriptRefresh(any(), any(), any(), any(), any(), any(),
                                                         any());
   }

   @Test
   void theTableCellNoteWarnsAboutTheRowLink() throws Exception {
      ScriptWriteResult result = service.writeDraft(rvs, agent, link("Table1", "cell[2,1]"), "x");

      assertTrue(result.note().contains("Apply to Row"), result.note());
      assertTrue(result.note().contains("get_hyperlink {assembly: 'Table1', row: 2, col: 1}"),
                 result.note());
   }

   @Test
   void theGenericWriteAndEnableEntryPointsRefuseTheKind() throws Exception {
      PairingException write = assertThrows(PairingException.class,
         () -> service.write(rvs, agent, link("Text1", "link"), "=x"));
      assertTrue(write.getMessage().contains("'vsHyperlinkExpression' is a draft-only target"),
                 write.getMessage());

      PairingException enable = assertThrows(PairingException.class,
         () -> service.setEnabled(rvs, link("Text1", "link"), true));
      assertEquals("'vsHyperlinkExpression': a hyperlink expression has no enable flag; only " +
                   "scripts can be enabled or disabled.", enable.getMessage());
      verifyNoInteractions(broadcast);
      verify(rvs, never()).bumpWriteRevision();
   }

   @Test
   void aWorksheetDraftKindIsNotPushedByTheViewsheetService() throws Exception {
      for(ScriptTarget.Kind kind : new ScriptTarget.Kind[] {
         ScriptTarget.Kind.WORKSHEET_VARIABLE_DEFAULT, ScriptTarget.Kind.WORKSHEET_SQL_QUERY })
      {
         ScriptTarget t = ScriptTarget.of(kind, "X");

         PairingException push = assertThrows(PairingException.class,
            () -> service.writeDraft(rvs, agent, t, "x"));
         assertTrue(push.getMessage().contains("is a worksheet draft target"), push.getMessage());

         PairingException write = assertThrows(PairingException.class,
            () -> service.write(rvs, agent, t, "x"));
         assertTrue(write.getMessage().contains("worksheet draft-only target"),
                    write.getMessage());

         PairingException enable = assertThrows(PairingException.class,
            () -> service.setEnabled(rvs, t, true));
         assertTrue(enable.getMessage().contains("worksheet draft-only target with no enable"),
                    enable.getMessage());
      }

      assertNothingPushed();
      verify(rvs, never()).bumpWriteRevision();
   }
}
