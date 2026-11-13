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

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.viewsheet.TableVSAssembly;
import inetsoft.uql.viewsheet.TextVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.web.binding.VSScriptableService;
import inetsoft.web.wiz.pairing.*;
import inetsoft.web.wiz.script.model.AssemblyContext;
import inetsoft.web.wiz.script.model.ScriptInfo;
import inetsoft.web.wiz.script.model.ScriptWriteResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * The dynamicValue pane session through the real controller + a REAL {@link ScriptEditService}
 * (a mocked one would skip the mutation lambdas that hold the pane-scope checks). The
 * {@code writeScript} cases run for BOTH draft-only kinds: they are the regression for the
 * {@code isDraftOnly()} refactor of the merged vsConditionValue branch. Bug #80459.
 */
@WizAgentTestSupport
class ViewsheetAgentControllerDynamicValueTest {
   private static final String DYNAMIC = "TOK-DV";
   private static final String CONDITION = "TOK-VSC";
   private static final String WHOLE = "TOK-WHOLE";
   private static final String RUNTIME_ID = "Viewsheet/dv";

   private RuntimeViewsheet rvs;
   private SheetAgentBroadcastService broadcast;
   private ScriptStateRefreshService refresh;
   private ViewsheetAgentController controller;
   private final Principal agent = () -> "admin";

   private static JoinSession session(String token, EditorContext ctx) {
      return new JoinSession(token, RUNTIME_ID, "admin", SheetType.VIEWSHEET, 0L, Long.MAX_VALUE,
         JoinSession.ConnectionMode.PAIRED, null, null, ctx);
   }

   @BeforeEach
   void setUp() throws Exception {
      Viewsheet vs = new Viewsheet();
      vs.addAssembly(new TextVSAssembly(vs, "Text1"));
      vs.addAssembly(new TextVSAssembly(vs, "Text2"));
      vs.addAssembly(new TableVSAssembly(vs, "Table1"));
      rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      when(rvs.getID()).thenReturn(RUNTIME_ID);

      SheetAgentFeature feature = mock(SheetAgentFeature.class);
      when(feature.isEnabled()).thenReturn(true);

      SheetSessionService sessions = mock(SheetSessionService.class);
      when(sessions.resolve(eq(DYNAMIC), eq("admin"))).thenReturn(
         session(DYNAMIC, new EditorContext("dynamicValue", "Text1", "visible", null)));
      when(sessions.resolve(eq(CONDITION), eq("admin"))).thenReturn(
         session(CONDITION, new EditorContext("vsConditionValue", "Table1", "filter", null)));
      when(sessions.resolve(eq(WHOLE), eq("admin"))).thenReturn(session(WHOLE, null));

      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      when(runtimeAccess.getSheetForPairing(eq(SheetType.VIEWSHEET), eq(RUNTIME_ID), any()))
         .thenReturn(rvs);

      broadcast = mock(SheetAgentBroadcastService.class);
      when(broadcast.pushScriptRefresh(any(), any(), any(), any(), any(), any(), any()))
         .thenReturn(true);
      refresh = mock(ScriptStateRefreshService.class);

      ScriptReadService read = new ScriptReadService();
      ScriptContextService context = new ScriptContextService(mock(VSScriptableService.class)) {
         @Override
         List<AssemblyContext> describeAssemblies(RuntimeViewsheet r) {
            return List.of();
         }
      };

      controller = new ViewsheetAgentController(feature, mock(SheetJoinService.class), sessions,
         new ScriptEditService(sessions, runtimeAccess, broadcast) {
            @Override
            String conditionSyntaxProblem(String text) {
               return text.trim().endsWith("+") ? "unexpected end of input" : null;
            }
         }, read, new ScriptExecuteService(read), refresh, context,
         mock(ScriptApiService.class), mock(ScriptImageService.class),
         mock(ViewsheetService.class), mock(SheetAgentBroadcastService.class));
   }

   private static void assertRefused(String fragment, Executable action) {
      PairingException ex = assertThrows(PairingException.class, action);
      assertTrue(ex.getMessage().contains(fragment), ex.getMessage());
   }

   private void assertRuntimeUntouched() throws Exception {
      verify(rvs, never()).bumpWriteRevision();
      verify(rvs, never()).addCheckpoint(any());
      verify(broadcast, never()).broadcastRefresh(any(), any(), any(), any());
      verify(broadcast, never()).broadcastScriptRefresh(any(), any(), any(), any(), any(), any(),
                                                         any());
   }

   // ---- writeScript: the draft branch for BOTH draft-only kinds -----------------------------

   @Test
   void writeScriptPushesTheNormalizedDraftAndNeverWritesTheRuntime() throws Exception {
      var req = new ViewsheetAgentController.WriteScriptRequest(
         null, null, "dynamicValue", "Text1", "visible", "= 1 > 0");

      ScriptWriteResult result = controller.writeScript(DYNAMIC, req, agent);

      assertNotNull(result);
      assertEquals(Boolean.TRUE, result.pending());
      verify(broadcast).pushScriptRefresh(rvs, RUNTIME_ID, agent, "dynamicValue", "Text1",
                                          "visible", "1 > 0");
      assertRuntimeUntouched();
   }

   @Test
   void writeScriptStillPushesAConditionValueDraftAndNeverWritesTheRuntime() throws Exception {
      var req = new ViewsheetAgentController.WriteScriptRequest(
         null, null, "vsConditionValue", "Table1", "filter", "1000");

      ScriptWriteResult result = controller.writeScript(CONDITION, req, agent);

      assertNotNull(result);
      assertEquals(Boolean.TRUE, result.pending());
      verify(broadcast).pushScriptRefresh(rvs, RUNTIME_ID, agent, "vsConditionValue", "Table1",
                                          "filter", "1000");
      assertRuntimeUntouched();
   }

   @Test
   void writeScriptAcceptsTheIdDialectForTheDynamicKind() throws Exception {
      ScriptTarget t = ScriptTarget.of(ScriptTarget.Kind.DYNAMIC_VALUE, "Text1", "visible");
      var req = new ViewsheetAgentController.WriteScriptRequest(
         null, t.id(), null, null, null, "1 > 0");

      assertEquals(Boolean.TRUE, controller.writeScript(DYNAMIC, req, agent).pending());
   }

   @Test
   void writeScriptRefusesAnotherAssemblyOrAnotherNameOrAnotherKind() throws Exception {
      for(String[] other : new String[][] { { "Text2", "visible" }, { "Text1", "enabled" } }) {
         var req = new ViewsheetAgentController.WriteScriptRequest(
            null, null, "dynamicValue", other[0], other[1], "1 > 0");

         assertRefused("This session is paired to the expression of the 'visible' property of " +
                       "'Text1'", () -> controller.writeScript(DYNAMIC, req, agent));
      }

      var condition = new ViewsheetAgentController.WriteScriptRequest(
         null, null, "vsConditionValue", "Text1", "filter", "1000");
      assertRefused("This session is paired to", () -> controller.writeScript(DYNAMIC, condition,
                                                                               agent));

      var dynamicOnCondition = new ViewsheetAgentController.WriteScriptRequest(
         null, null, "dynamicValue", "Table1", "filter", "1000");
      assertRefused("This session is paired to",
                    () -> controller.writeScript(CONDITION, dynamicOnCondition, agent));

      var main = new ViewsheetAgentController.WriteScriptRequest(
         null, null, "assemblyMain", "Text1", null, "x = 1");
      assertRefused("may not act on", () -> controller.writeScript(DYNAMIC, main, agent));

      verify(broadcast, never()).pushScriptRefresh(any(), any(), any(), any(), any(), any(),
                                                    any());
      assertRuntimeUntouched();
   }

   @Test
   void aWholeSheetSessionCannotWriteTheDynamicKind() throws Exception {
      var req = new ViewsheetAgentController.WriteScriptRequest(
         null, null, "dynamicValue", "Text1", "visible", "1 > 0");

      assertRefused("a dynamic value has no name at sheet level",
                    () -> controller.writeScript(WHOLE, req, agent));
      verify(broadcast, never()).pushScriptRefresh(any(), any(), any(), any(), any(), any(),
                                                    any());
      assertRuntimeUntouched();
   }

   @Test
   void aRefusedDraftIsRefusedBeforeAnyPush() throws Exception {
      for(String bad : new String[] { "", "=", "$(Foo)", "1 +" }) {
         var req = new ViewsheetAgentController.WriteScriptRequest(
            null, null, "dynamicValue", "Text1", "visible", bad);

         assertThrows(PairingException.class, () -> controller.writeScript(DYNAMIC, req, agent),
                      bad);
      }

      verify(broadcast, never()).pushScriptRefresh(any(), any(), any(), any(), any(), any(),
                                                    any());
      assertRuntimeUntouched();
   }

   // ---- the other endpoints -----------------------------------------------------------------

   @Test
   void readScriptReturnsThePendingDraft() throws Exception {
      ScriptInfo info = controller.readScript(
         DYNAMIC, null, null, "dynamicValue", "Text1", "visible", agent);

      assertEquals("", info.text());
      assertEquals(Boolean.TRUE, info.pending());
   }

   @Test
   void executeAndLiveRunRefuseWithoutRefreshingTheViewsheet() throws Exception {
      var exec = new ViewsheetAgentController.ExecuteRequest(
         null, null, "dynamicValue", "Text1", "visible");
      var live = new ViewsheetAgentController.ExecuteLiveRequest(
         null, null, "dynamicValue", "Text1", "visible", true);

      assertRefused("not a runnable script", () -> controller.execute(DYNAMIC, exec, agent));
      assertRefused("not a runnable script", () -> controller.executeLive(DYNAMIC, live, agent));
      verifyNoInteractions(refresh);
      assertRuntimeUntouched();
   }

   @Test
   void enableRefuses() throws Exception {
      var req = new ViewsheetAgentController.SetEnabledRequest(
         null, null, "dynamicValue", "Text1", "visible", true);

      assertRefused("a dynamic value has no enable flag",
                    () -> controller.setEnabled(DYNAMIC, req, agent));
      verify(rvs, never()).addCheckpoint(any());
      verify(rvs, never()).bumpWriteRevision();
   }

   @Test
   void contextRefuses() {
      assertRefused("has no scripting context", () -> controller.context(
         DYNAMIC, null, null, "dynamicValue", "Text1", "visible", agent));
   }
}
