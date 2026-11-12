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
 * The draft-only write of a viewsheet condition value (Bug #80459). The decisive property is that
 * the runtime viewsheet is never mutated: the write revision is the one the user's open dialog
 * will echo on OK, so bumping it here would make that OK fail with a write conflict.
 */
@WizAgentTestSupport
class ScriptEditServiceVsConditionTest {
   private RuntimeViewsheet rvs;
   private SheetAgentBroadcastService broadcast;
   private ScriptEditService service;
   private final Principal agent = TestPrincipals.user("alice", "host-org");

   @BeforeEach
   void setUp() {
      Viewsheet vs = new Viewsheet();
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

   private static ScriptTarget filter() throws Exception {
      return ScriptTarget.of(ScriptTarget.Kind.VS_CONDITION_VALUE, "Table1", "filter");
   }

   private static ScriptTarget highlight() throws Exception {
      return ScriptTarget.of(ScriptTarget.Kind.VS_CONDITION_VALUE, "Table1", "highlight:Hi");
   }

   @Test
   void pushesExactlyTheGrantedTupleAndTheText() throws Exception {
      ScriptWriteResult result = service.writeConditionValueDraft(rvs, agent, filter(), "1000");

      verify(broadcast).pushScriptRefresh(rvs, "Viewsheet/foo-7", agent, "vsConditionValue",
                                          "Table1", "filter", "1000");
      assertTrue(result.ok());
      assertEquals(Boolean.TRUE, result.pending());
      assertTrue(result.note().contains("Nothing is applied"), result.note());
      assertNull(result.warning());
   }

   @Test
   void neverTouchesTheRuntimeViewsheet() throws Exception {
      service.writeConditionValueDraft(rvs, agent, filter(), "1000");

      verify(rvs, never()).bumpWriteRevision();
      verify(rvs, never()).addCheckpoint(any());
      verify(broadcast, never()).broadcastRefresh(any(), any(), any(), any());
      verify(broadcast, never()).broadcastScriptRefresh(any(), any(), any(), any(), any(), any(),
                                                         any());
   }

   @Test
   void aFilterThatDoesNotParseIsRefusedAndNothingIsPushed() throws Exception {
      PairingException ex = assertThrows(PairingException.class,
         () -> service.writeConditionValueDraft(rvs, agent, filter(), "1 +"));

      assertTrue(ex.getMessage().contains("does not parse as a JavaScript expression"),
                 ex.getMessage());
      assertTrue(ex.getMessage().contains("Nothing was pushed."), ex.getMessage());
      verify(broadcast, never()).pushScriptRefresh(any(), any(), any(), any(), any(), any(),
                                                    any());
   }

   @Test
   void aHighlightThatDoesNotParseIsOnlyAWarning() throws Exception {
      ScriptWriteResult result = service.writeConditionValueDraft(rvs, agent, highlight(), "1 +");

      assertTrue(result.ok());
      assertNotNull(result.warning());
      verify(broadcast).pushScriptRefresh(rvs, "Viewsheet/foo-7", agent, "vsConditionValue",
                                          "Table1", "highlight:Hi", "1 +");
   }

   @Test
   void nullAndBlankTextAreRefused() throws Exception {
      PairingException nul = assertThrows(PairingException.class,
         () -> service.writeConditionValueDraft(rvs, agent, filter(), null));
      assertTrue(nul.getMessage().contains("text is required."), nul.getMessage());

      for(String blank : new String[] { "", "   " }) {
         PairingException ex = assertThrows(PairingException.class,
            () -> service.writeConditionValueDraft(rvs, agent, highlight(), blank));
         assertTrue(ex.getMessage().contains("The expression is empty") &&
                    ex.getMessage().contains("a condition value cannot be blank"),
                    ex.getMessage());
      }

      verify(broadcast, never()).pushScriptRefresh(any(), any(), any(), any(), any(), any(),
                                                    any());
   }

   @Test
   void anUnreachableDialogFailsLoud() throws Exception {
      when(broadcast.pushScriptRefresh(any(), any(), any(), any(), any(), any(), any()))
         .thenReturn(false);

      PairingException ex = assertThrows(PairingException.class,
         () -> service.writeConditionValueDraft(rvs, agent, filter(), "1000"));

      assertTrue(ex.getMessage().contains(
         "is not reachable (closed or disconnected), so nothing was pushed"), ex.getMessage());
      assertTrue(ex.getMessage().contains("Connect to Claude"), ex.getMessage());
   }

   @Test
   void aDeletedAssemblyFailsLoudBeforeAnyPush() throws Exception {
      ScriptTarget gone = ScriptTarget.of(ScriptTarget.Kind.VS_CONDITION_VALUE, "Gone", "filter");

      PairingException ex = assertThrows(PairingException.class,
         () -> service.writeConditionValueDraft(rvs, agent, gone, "1000"));

      assertTrue(ex.getMessage().contains("Assembly not found: Gone"), ex.getMessage());
      verify(broadcast, never()).pushScriptRefresh(any(), any(), any(), any(), any(), any(),
                                                    any());
   }

   @Test
   void theGenericWriteAndEnableEntryPointsRefuseTheKind() throws Exception {
      assertThrows(PairingException.class, () -> service.write(rvs, agent, filter(), "1000"));

      PairingException enable = assertThrows(PairingException.class,
         () -> service.setEnabled(rvs, filter(), true));
      assertTrue(enable.getMessage().contains("a condition value has no enable flag"),
                 enable.getMessage());
      verifyNoInteractions(broadcast);
   }
}
