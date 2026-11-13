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
 * The draft-only write of a {@code dynamicValue} (Bug #80459). As for {@code vsConditionValue} the
 * decisive property is that the runtime viewsheet is never mutated: the write revision is the one
 * the user's open dialog echoes on OK, so bumping it here would fail the user's own OK.
 */
@WizAgentTestSupport
class ScriptEditServiceDynamicValueTest {
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

   private static ScriptTarget visible() throws Exception {
      return ScriptTarget.of(ScriptTarget.Kind.DYNAMIC_VALUE, "Text1", "visible");
   }

   private void assertNothingPushed() {
      verify(broadcast, never()).pushScriptRefresh(any(), any(), any(), any(), any(), any(),
                                                    any());
   }

   @Test
   void pushesTheNormalizedTextAndExactlyTheGrantedTuple() throws Exception {
      ScriptWriteResult result = service.writeDraft(rvs, agent, visible(), "= 1 > 0");

      verify(broadcast).pushScriptRefresh(rvs, "Viewsheet/foo-7", agent, "dynamicValue", "Text1",
                                          "visible", "1 > 0");
      assertTrue(result.ok());
      assertEquals(Boolean.TRUE, result.pending());
      assertNull(result.warning());
      assertTrue(result.note().contains("Nothing is applied"), result.note());
      assertTrue(result.note().endsWith(" A leading '=' was removed: the dialog's editor holds " +
                                        "the expression without it."), result.note());
   }

   @Test
   void aTextWithoutALeadingEqualsIsPushedVerbatimWithoutTheExtraSentence() throws Exception {
      ScriptWriteResult result = service.writeDraft(rvs, agent, visible(), "parameter.n > 1");

      verify(broadcast).pushScriptRefresh(rvs, "Viewsheet/foo-7", agent, "dynamicValue", "Text1",
                                          "visible", "parameter.n > 1");
      assertFalse(result.note().contains("leading '='"), result.note());
   }

   @Test
   void neverTouchesTheRuntimeViewsheet() throws Exception {
      service.writeDraft(rvs, agent, visible(), "1 > 0");

      verify(rvs, never()).bumpWriteRevision();
      verify(rvs, never()).addCheckpoint(any());
      verify(broadcast, never()).broadcastRefresh(any(), any(), any(), any());
      verify(broadcast, never()).broadcastScriptRefresh(any(), any(), any(), any(), any(), any(),
                                                         any());
   }

   @Test
   void nullBlankAndABareEqualsAreRefusedAndNothingIsPushed() throws Exception {
      PairingException nul = assertThrows(PairingException.class,
         () -> service.writeDraft(rvs, agent, visible(), null));
      assertTrue(nul.getMessage().contains("text is required."), nul.getMessage());

      for(String blank : new String[] { "", "   ", "=", " = " }) {
         PairingException ex = assertThrows(PairingException.class,
            () -> service.writeDraft(rvs, agent, visible(), blank), blank);

         assertEquals(PairingException.Kind.INVALID_ARGUMENT, ex.getKind());
         assertTrue(ex.getMessage().contains("it would store a bare '='"), ex.getMessage());
      }

      assertNothingPushed();
   }

   @Test
   void aBareVariableReferenceIsRefusedAndNothingIsPushed() throws Exception {
      PairingException ex = assertThrows(PairingException.class,
         () -> service.writeDraft(rvs, agent, visible(), "$(Foo)"));

      assertTrue(ex.getMessage().contains("is a variable reference, not an expression"),
                 ex.getMessage());
      assertNothingPushed();
   }

   @Test
   void aSyntaxErrorIsRefusedAndNothingIsPushed() throws Exception {
      PairingException ex = assertThrows(PairingException.class,
         () -> service.writeDraft(rvs, agent, visible(), "1 >0 +"));

      assertTrue(ex.getMessage().contains("The expression does not parse as a JavaScript " +
         "expression (unexpected end of input). Nothing was pushed."), ex.getMessage());
      assertNothingPushed();
   }

   @Test
   void anUnreachableDialogFailsLoud() throws Exception {
      when(broadcast.pushScriptRefresh(any(), any(), any(), any(), any(), any(), any()))
         .thenReturn(false);

      PairingException ex = assertThrows(PairingException.class,
         () -> service.writeDraft(rvs, agent, visible(), "1 > 0"));

      assertTrue(ex.getMessage().contains(
         "is not reachable (closed or disconnected), so nothing was pushed"), ex.getMessage());
      assertTrue(ex.getMessage().contains("Connect to Claude"), ex.getMessage());
      assertTrue(ex.getMessage().contains("dynamicValue:Text1:visible"), ex.getMessage());
   }

   @Test
   void aDeletedAssemblyFailsLoudBeforeAnyPush() throws Exception {
      ScriptTarget gone = ScriptTarget.of(ScriptTarget.Kind.DYNAMIC_VALUE, "Gone", "visible");

      PairingException ex = assertThrows(PairingException.class,
         () -> service.writeDraft(rvs, agent, gone, "1 > 0"));

      assertTrue(ex.getMessage().contains("Assembly not found: Gone"), ex.getMessage());
      assertNothingPushed();
   }

   @Test
   void theGenericWriteAndEnableEntryPointsRefuseTheKind() throws Exception {
      PairingException write = assertThrows(PairingException.class,
         () -> service.write(rvs, agent, visible(), "1 > 0"));
      assertTrue(write.getMessage().contains("'dynamicValue' is a draft-only target"),
                 write.getMessage());

      PairingException enable = assertThrows(PairingException.class,
         () -> service.setEnabled(rvs, visible(), true));
      assertEquals("'dynamicValue': a dynamic value has no enable flag; only scripts can be " +
                   "enabled or disabled.", enable.getMessage());
      verifyNoInteractions(broadcast);
      verify(rvs, never()).bumpWriteRevision();
   }

   @Test
   void writeDraftRefusesAKindThatIsNotDraftOnly() throws Exception {
      ScriptTarget main = ScriptTarget.of(ScriptTarget.Kind.ASSEMBLY_MAIN, "Text1");

      assertThrows(PairingException.class, () -> service.writeDraft(rvs, agent, main, "x = 1"));
      assertNothingPushed();
   }

   // ---- the vsConditionValue path after the isDraftOnly refactor ---------------------------

   @Test
   void theVsConditionPathStillBehavesThroughTheSharedWriteDraft() throws Exception {
      ScriptTarget filter = ScriptTarget.of(ScriptTarget.Kind.VS_CONDITION_VALUE, "Table1",
                                            "filter");
      ScriptTarget highlight = ScriptTarget.of(ScriptTarget.Kind.VS_CONDITION_VALUE, "Table1",
                                               "highlight:Hi");

      service.writeDraft(rvs, agent, filter, "1000");

      verify(broadcast).pushScriptRefresh(rvs, "Viewsheet/foo-7", agent, "vsConditionValue",
                                          "Table1", "filter", "1000");

      // A condition text is pushed untouched: no '=' normalization, a highlight only warns.
      ScriptWriteResult warned = service.writeDraft(rvs, agent, highlight, "=1 +");
      assertNotNull(warned.warning());
      verify(broadcast).pushScriptRefresh(rvs, "Viewsheet/foo-7", agent, "vsConditionValue",
                                          "Table1", "highlight:Hi", "=1 +");
      assertThrows(PairingException.class,
                   () -> service.writeDraft(rvs, agent, filter, "1 +"));
      verify(rvs, never()).bumpWriteRevision();
   }
}
