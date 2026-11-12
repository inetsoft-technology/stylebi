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
 * The vsConditionValue pane session through the real controller + a REAL {@link ScriptEditService}
 * (a mocked one would skip the mutation lambdas that hold the pane-scope checks). Bug #80459.
 */
@WizAgentTestSupport
class ViewsheetAgentControllerVsConditionTest {
   private static final String TOKEN = "TOK-VSC";
   private static final String RUNTIME_ID = "Viewsheet/vs-cond";

   private RuntimeViewsheet rvs;
   private SheetAgentBroadcastService broadcast;
   private ScriptStateRefreshService refresh;
   private ViewsheetAgentController controller;
   private final Principal agent = () -> "admin";

   @BeforeEach
   void setUp() throws Exception {
      Viewsheet vs = new Viewsheet();
      vs.addAssembly(new TableVSAssembly(vs, "Table1"));
      vs.addAssembly(new TableVSAssembly(vs, "Table2"));
      rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      when(rvs.getID()).thenReturn(RUNTIME_ID);

      SheetAgentFeature feature = mock(SheetAgentFeature.class);
      when(feature.isEnabled()).thenReturn(true);

      JoinSession session = new JoinSession(TOKEN, RUNTIME_ID, "admin", SheetType.VIEWSHEET, 0L,
         Long.MAX_VALUE, JoinSession.ConnectionMode.PAIRED, null, null,
         new EditorContext("vsConditionValue", "Table1", "filter", null));
      SheetSessionService sessions = mock(SheetSessionService.class);
      when(sessions.resolve(eq(TOKEN), eq("admin"))).thenReturn(session);

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
         new ScriptEditService(sessions, runtimeAccess, broadcast), read,
         new ScriptExecuteService(read), refresh, context, mock(ScriptApiService.class),
         mock(ScriptImageService.class), mock(ViewsheetService.class), mock(SheetAgentBroadcastService.class));
   }

   private static void assertRefused(String fragment, Executable action) {
      PairingException ex = assertThrows(PairingException.class, action);
      assertTrue(ex.getMessage().contains(fragment), ex.getMessage());
   }

   @Test
   void writeScriptPushesTheDraftAndNeverWritesTheRuntime() throws Exception {
      var req = new ViewsheetAgentController.WriteScriptRequest(
         null, null, "vsConditionValue", "Table1", "filter", "1000");

      ScriptWriteResult result = controller.writeScript(TOKEN, req, agent);

      assertNotNull(result);
      assertEquals(Boolean.TRUE, result.pending());
      verify(broadcast).pushScriptRefresh(rvs, RUNTIME_ID, agent, "vsConditionValue", "Table1",
                                          "filter", "1000");
      verify(rvs, never()).bumpWriteRevision();
      verify(rvs, never()).addCheckpoint(any());
      verify(broadcast, never()).broadcastRefresh(any(), any(), any(), any());
   }

   @Test
   void writeScriptAcceptsTheIdDialect() throws Exception {
      ScriptTarget t = ScriptTarget.of(ScriptTarget.Kind.VS_CONDITION_VALUE, "Table1", "filter");
      var req = new ViewsheetAgentController.WriteScriptRequest(
         null, t.id(), null, null, null, "1000");

      assertEquals(Boolean.TRUE, controller.writeScript(TOKEN, req, agent).pending());
   }

   @Test
   void writeScriptRefusesAnotherAssemblyOrAnotherScopeOfTheSameAssembly() throws Exception {
      for(String[] other : new String[][] { { "Table2", "filter" }, { "Table1", "highlight:Hi" } }) {
         var req = new ViewsheetAgentController.WriteScriptRequest(
            null, null, "vsConditionValue", other[0], other[1], "1000");

         assertRefused("This session is paired to", () -> controller.writeScript(TOKEN, req, agent));
      }

      verify(broadcast, never()).pushScriptRefresh(any(), any(), any(), any(), any(), any(),
                                                    any());
   }

   @Test
   void writeScriptRefusesTheAssemblyScriptOfTheSameAssembly() {
      var req = new ViewsheetAgentController.WriteScriptRequest(
         null, null, "assemblyMain", "Table1", null, "x = 1");

      assertRefused("may not act on", () -> controller.writeScript(TOKEN, req, agent));
      verify(rvs, never()).bumpWriteRevision();
   }

   @Test
   void readScriptReturnsThePendingDraft() throws Exception {
      ScriptInfo info = controller.readScript(
         TOKEN, null, null, "vsConditionValue", "Table1", "filter", agent);

      assertEquals("", info.text());
      assertEquals(Boolean.TRUE, info.pending());
   }

   @Test
   void executeAndLiveRunRefuseWithoutRefreshingTheViewsheet() {
      var exec = new ViewsheetAgentController.ExecuteRequest(
         null, null, "vsConditionValue", "Table1", "filter");
      var live = new ViewsheetAgentController.ExecuteLiveRequest(
         null, null, "vsConditionValue", "Table1", "filter", true);

      assertRefused("not a runnable script", () -> controller.execute(TOKEN, exec, agent));
      assertRefused("not a runnable script", () -> controller.executeLive(TOKEN, live, agent));
      verifyNoInteractions(refresh);
      verify(broadcast, never()).broadcastRefresh(any(), any(), any(), any());
   }

   @Test
   void enableRefuses() {
      var req = new ViewsheetAgentController.SetEnabledRequest(
         null, null, "vsConditionValue", "Table1", "filter", true);

      assertRefused("a condition value has no enable flag",
                    () -> controller.setEnabled(TOKEN, req, agent));
      verify(rvs, never()).addCheckpoint(any());
   }

   @Test
   void contextRefuses() {
      assertRefused("has no scripting context", () -> controller.context(
         TOKEN, null, null, "vsConditionValue", "Table1", "filter", agent));
   }
}
