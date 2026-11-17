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
import inetsoft.web.wiz.script.model.ScriptTargetInfo;
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
 * The vsHyperlinkExpression pane session through the real controller + a REAL
 * {@link ScriptEditService}. The controller needs no change: its draft branch keys on
 * {@code isDraftOnly()}, so this pins that a new draft kind is routed to {@code writeDraft} before
 * the runtime-writing path, and that the grant scopes every endpoint. Bug #80459.
 */
@WizAgentTestSupport
class ViewsheetAgentControllerHyperlinkTest {
   private static final String LINK = "TOK-HL";
   private static final String WHOLE = "TOK-WHOLE";
   private static final String RUNTIME_ID = "Viewsheet/hl";

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
      vs.addAssembly(new TableVSAssembly(vs, "Table1"));
      rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      when(rvs.getID()).thenReturn(RUNTIME_ID);

      SheetAgentFeature feature = mock(SheetAgentFeature.class);
      when(feature.isEnabled()).thenReturn(true);

      SheetSessionService sessions = mock(SheetSessionService.class);
      when(sessions.resolve(eq(LINK), eq("admin"))).thenReturn(
         session(LINK, new EditorContext("vsHyperlinkExpression", "Table1", "cell[2,1]", null)));
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

   private void assertNothingPushed() {
      verify(broadcast, never()).pushScriptRefresh(any(), any(), any(), any(), any(), any(),
                                                    any());
   }

   @Test
   void writeScriptPushesTheEqualsNormalizedDraftAndNeverWritesTheRuntime() throws Exception {
      var req = new ViewsheetAgentController.WriteScriptRequest(
         null, null, "vsHyperlinkExpression", "Table1", "cell[2,1]", "\"https://x/\" + 1");

      ScriptWriteResult result = controller.writeScript(LINK, req, agent);

      assertNotNull(result);
      assertEquals(Boolean.TRUE, result.pending());
      verify(broadcast).pushScriptRefresh(rvs, RUNTIME_ID, agent, "vsHyperlinkExpression",
                                          "Table1", "cell[2,1]", "=\"https://x/\" + 1");
      assertRuntimeUntouched();
   }

   @Test
   void writeScriptAcceptsTheIdDialect() throws Exception {
      ScriptTarget t = ScriptTarget.of(ScriptTarget.Kind.VS_HYPERLINK_EXPRESSION, "Table1",
                                       "cell[2,1]");
      var req = new ViewsheetAgentController.WriteScriptRequest(null, t.id(), null, null, null,
                                                                "=x");

      assertEquals(Boolean.TRUE, controller.writeScript(LINK, req, agent).pending());
      verify(broadcast).pushScriptRefresh(rvs, RUNTIME_ID, agent, "vsHyperlinkExpression",
                                          "Table1", "cell[2,1]", "=x");
   }

   @Test
   void writeScriptRefusesAnotherRegionAssemblyOrKind() throws Exception {
      for(String[] other : new String[][] { { "Table1", "cell[2,2]" }, { "Table1", "link" },
                                            { "Text1", "cell[2,1]" } })
      {
         var req = new ViewsheetAgentController.WriteScriptRequest(
            null, null, "vsHyperlinkExpression", other[0], other[1], "x");

         assertRefused("This session is paired to the web-link expression of the link of cell " +
                       "row 2, col 1 on 'Table1'", () -> controller.writeScript(LINK, req, agent));
      }

      var dynamic = new ViewsheetAgentController.WriteScriptRequest(
         null, null, "dynamicValue", "Table1", "title", "1 > 0");
      assertRefused("This session is paired to", () -> controller.writeScript(LINK, dynamic, agent));

      var condition = new ViewsheetAgentController.WriteScriptRequest(
         null, null, "vsConditionValue", "Table1", "filter", "1000");
      assertRefused("This session is paired to",
                    () -> controller.writeScript(LINK, condition, agent));

      var main = new ViewsheetAgentController.WriteScriptRequest(
         null, null, "assemblyMain", "Table1", null, "x = 1");
      assertRefused("may not act on", () -> controller.writeScript(LINK, main, agent));

      assertNothingPushed();
      assertRuntimeUntouched();
   }

   @Test
   void aWholeSheetSessionCannotWriteTheKind() throws Exception {
      var req = new ViewsheetAgentController.WriteScriptRequest(
         null, null, "vsHyperlinkExpression", "Table1", "cell[2,1]", "x");

      assertRefused("a hyperlink expression has no name at sheet level",
                    () -> controller.writeScript(WHOLE, req, agent));
      assertNothingPushed();
      assertRuntimeUntouched();
   }

   @Test
   void aRefusedDraftIsRefusedBeforeAnyPush() throws Exception {
      for(String bad : new String[] { "", "=", "==x", "1 +", "=1 +" }) {
         var req = new ViewsheetAgentController.WriteScriptRequest(
            null, null, "vsHyperlinkExpression", "Table1", "cell[2,1]", bad);

         assertThrows(PairingException.class, () -> controller.writeScript(LINK, req, agent), bad);
      }

      assertNothingPushed();
      assertRuntimeUntouched();
   }

   @Test
   void aMalformedRegionIsRefusedByTheTargetGrammarBeforeAnyPush() throws Exception {
      var req = new ViewsheetAgentController.WriteScriptRequest(
         null, null, "vsHyperlinkExpression", "Table1", "cell[1]", "x");

      assertRefused("kind 'vsHyperlinkExpression' requires a valid 'name'",
                    () -> controller.writeScript(LINK, req, agent));
      assertNothingPushed();
   }

   @Test
   void readScriptReturnsThePendingDraft() throws Exception {
      ScriptInfo info = controller.readScript(
         LINK, null, null, "vsHyperlinkExpression", "Table1", "cell[2,1]", agent);

      assertEquals("", info.text());
      assertEquals(Boolean.TRUE, info.pending());
      assertTrue(info.note().contains("get_hyperlink"), info.note());
   }

   @Test
   void readScriptOfAnotherRegionIsRefused() {
      assertRefused("This session is paired to", () -> controller.readScript(
         LINK, null, null, "vsHyperlinkExpression", "Table1", "link", agent));
   }

   @Test
   void theTargetsListHoldsExactlyTheGrantedHyperlink() throws Exception {
      var response = controller.targets(LINK, agent);
      List<ScriptTargetInfo> targets = response.targets();

      assertEquals(1, targets.size());
      assertEquals("vsHyperlinkExpression", targets.get(0).kind());
      assertEquals("cell[2,1]", targets.get(0).name());
   }

   @Test
   void executeAndLiveRunRefuseWithoutRefreshingTheViewsheet() throws Exception {
      var exec = new ViewsheetAgentController.ExecuteRequest(
         null, null, "vsHyperlinkExpression", "Table1", "cell[2,1]");
      var live = new ViewsheetAgentController.ExecuteLiveRequest(
         null, null, "vsHyperlinkExpression", "Table1", "cell[2,1]", true);

      assertRefused("not a runnable script", () -> controller.execute(LINK, exec, agent));
      assertRefused("not a runnable script", () -> controller.executeLive(LINK, live, agent));
      verifyNoInteractions(refresh);
      assertRuntimeUntouched();
   }

   @Test
   void enableRefuses() throws Exception {
      var req = new ViewsheetAgentController.SetEnabledRequest(
         null, null, "vsHyperlinkExpression", "Table1", "cell[2,1]", true);

      assertRefused("a hyperlink expression has no enable flag",
                    () -> controller.setEnabled(LINK, req, agent));
      verify(rvs, never()).addCheckpoint(any());
      verify(rvs, never()).bumpWriteRevision();
   }

   @Test
   void contextRefuses() {
      assertRefused("has no scripting context", () -> controller.context(
         LINK, null, null, "vsHyperlinkExpression", "Table1", "cell[2,1]", agent));
   }
}
