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
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.web.wiz.pairing.*;
import inetsoft.web.wiz.script.knowledge.ScriptApiService;
import inetsoft.web.wiz.script.knowledge.ScriptContextService;
import inetsoft.web.wiz.script.model.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("core")
@WizAgentTestSupport
class ScriptAgentControllerTest {

   // ---------------------------------------------------------------------------
   // Helpers
   // ---------------------------------------------------------------------------

   private static JoinSession session(String token, String runtimeId) {
      return new JoinSession(token, runtimeId, "alice~;~host-org",
                             SheetType.VIEWSHEET, 0L, Long.MAX_VALUE,
                             JoinSession.ConnectionMode.PAIRED);
   }

   private static SheetAgentFeature featureOn() {
      SheetAgentFeature f = mock(SheetAgentFeature.class);
      when(f.isEnabled()).thenReturn(true);
      return f;
   }

   private static SheetAgentFeature featureOff() {
      SheetAgentFeature f = mock(SheetAgentFeature.class);
      when(f.isEnabled()).thenReturn(false);
      return f;
   }

   private static ScriptAgentController controller(SheetAgentFeature feature,
                                                    SheetJoinService join,
                                                    SheetSessionService sessions,
                                                    SheetRuntimeAccess runtimeAccess,
                                                    SheetAgentBroadcastService broadcast,
                                                    ScriptReadService read,
                                                    ScriptWriteService write,
                                                    ScriptExecuteService execute,
                                                    ScriptContextService ctx,
                                                    ScriptApiService api,
                                                    ViewsheetService vsSvc)
   {
      return new ScriptAgentController(feature, join, sessions, runtimeAccess,
                                       broadcast, read, write, execute, ctx, api, vsSvc);
   }

   /** Wire up a mock session + mock runtimeAccess that returns the given rvs. */
   private static void stubSession(SheetSessionService sessions,
                                   SheetRuntimeAccess runtimeAccess,
                                   JoinSession session,
                                   RuntimeViewsheet rvs)
      throws PairingException
   {
      when(sessions.resolve(eq(session.sessionToken()), any())).thenReturn(session);
      when(runtimeAccess.getSheetForPairing(eq(SheetType.VIEWSHEET),
                                            eq(session.runtimeId()), any()))
         .thenReturn(rvs);
   }

   // ---------------------------------------------------------------------------
   // join
   // ---------------------------------------------------------------------------

   @Test
   void joinReturnsSessionToken() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      JoinSession s = session("TOK-1", "Viewsheet/vs-1");

      SheetJoinService joinSvc = mock(SheetJoinService.class);
      when(joinSvc.join(eq("CODE"), eq(agent))).thenReturn(s);

      ScriptAgentController ctrl = controller(
         featureOn(), joinSvc,
         mock(SheetSessionService.class), mock(SheetRuntimeAccess.class),
         mock(SheetAgentBroadcastService.class), mock(ScriptReadService.class),
         mock(ScriptWriteService.class), mock(ScriptExecuteService.class),
         mock(ScriptContextService.class), mock(ScriptApiService.class),
         mock(ViewsheetService.class));

      ScriptAgentController.JoinResponse resp = ctrl.join("CODE", agent);

      assertEquals("TOK-1", resp.sessionToken());
      assertEquals("Viewsheet/vs-1", resp.runtimeId());
      assertEquals("alice~;~host-org", resp.ownerIdentity());
   }

   @Test
   void joinRejectsFlagOff() {
      ScriptAgentController ctrl = controller(
         featureOff(), mock(SheetJoinService.class),
         mock(SheetSessionService.class), mock(SheetRuntimeAccess.class),
         mock(SheetAgentBroadcastService.class), mock(ScriptReadService.class),
         mock(ScriptWriteService.class), mock(ScriptExecuteService.class),
         mock(ScriptContextService.class), mock(ScriptApiService.class),
         mock(ViewsheetService.class));

      ResponseStatusException ex = assertThrows(ResponseStatusException.class,
         () -> ctrl.join("CODE", TestPrincipals.user("alice", "host-org")));
      assertEquals(403, ex.getStatusCode().value());
   }

   // ---------------------------------------------------------------------------
   // detach
   // ---------------------------------------------------------------------------

   @Test
   void detachClosesSessionEvenWhenFlagOff() {
      SheetSessionService sessions = mock(SheetSessionService.class);

      ScriptAgentController ctrl = controller(
         featureOff(), mock(SheetJoinService.class), sessions,
         mock(SheetRuntimeAccess.class), mock(SheetAgentBroadcastService.class),
         mock(ScriptReadService.class), mock(ScriptWriteService.class),
         mock(ScriptExecuteService.class), mock(ScriptContextService.class),
         mock(ScriptApiService.class), mock(ViewsheetService.class));

      ctrl.detach("TOK-D");

      verify(sessions).close("TOK-D");
   }

   // ---------------------------------------------------------------------------
   // targets
   // ---------------------------------------------------------------------------

   @Test
   void targetsReturnsScriptInfoList() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      JoinSession s = session("TOK-T", "Viewsheet/vs-1");

      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      stubSession(sessions, runtimeAccess, s, rvs);

      ScriptInfo vsInit = new ScriptInfo("vs-init", "", true);
      ScriptReadService readSvc = mock(ScriptReadService.class);
      when(readSvc.list(rvs)).thenReturn(List.of(vsInit));

      ScriptAgentController ctrl = controller(
         featureOn(), mock(SheetJoinService.class), sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), readSvc,
         mock(ScriptWriteService.class), mock(ScriptExecuteService.class),
         mock(ScriptContextService.class), mock(ScriptApiService.class),
         mock(ViewsheetService.class));

      List<ScriptInfo> result = ctrl.targets("TOK-T", agent);

      assertEquals(1, result.size());
      assertEquals("vs-init", result.get(0).target());
   }

   @Test
   void targetsThrowsOnExpiredSession() {
      Principal agent = TestPrincipals.user("alice", "host-org");
      SheetSessionService sessions = mock(SheetSessionService.class);
      when(sessions.resolve(any(), any())).thenReturn(null);

      ScriptAgentController ctrl = controller(
         featureOn(), mock(SheetJoinService.class), sessions,
         mock(SheetRuntimeAccess.class), mock(SheetAgentBroadcastService.class),
         mock(ScriptReadService.class), mock(ScriptWriteService.class),
         mock(ScriptExecuteService.class), mock(ScriptContextService.class),
         mock(ScriptApiService.class), mock(ViewsheetService.class));

      assertThrows(PairingException.class, () -> ctrl.targets("INVALID", agent));
   }

   // ---------------------------------------------------------------------------
   // readScript
   // ---------------------------------------------------------------------------

   @Test
   void readScriptReturnsSingleInfo() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      JoinSession s = session("TOK-R", "Viewsheet/vs-1");

      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      stubSession(sessions, runtimeAccess, s, rvs);

      ScriptInfo info = new ScriptInfo("vs-init", "runQuery();", true);
      ScriptReadService readSvc = mock(ScriptReadService.class);
      when(readSvc.read(eq(rvs), any(ScriptTarget.class))).thenReturn(info);

      ScriptAgentController ctrl = controller(
         featureOn(), mock(SheetJoinService.class), sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), readSvc,
         mock(ScriptWriteService.class), mock(ScriptExecuteService.class),
         mock(ScriptContextService.class), mock(ScriptApiService.class),
         mock(ViewsheetService.class));

      ScriptInfo result = ctrl.readScript("TOK-R", "vs-init", agent);

      assertEquals("vs-init", result.target());
      assertEquals("runQuery();", result.text());
   }

   @Test
   void readScriptThrowsOnBadTarget() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      JoinSession s = session("TOK-BT", "Viewsheet/vs-1");

      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      stubSession(sessions, runtimeAccess, s, rvs);

      ScriptAgentController ctrl = controller(
         featureOn(), mock(SheetJoinService.class), sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(ScriptReadService.class),
         mock(ScriptWriteService.class), mock(ScriptExecuteService.class),
         mock(ScriptContextService.class), mock(ScriptApiService.class),
         mock(ViewsheetService.class));

      assertThrows(PairingException.class, () -> ctrl.readScript("TOK-BT", "bogus::", agent));
   }

   // ---------------------------------------------------------------------------
   // writeScript
   // ---------------------------------------------------------------------------

   @Test
   void writeScriptDelegatesToWriteServiceAndBroadcasts() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      JoinSession s = session("TOK-W", "Viewsheet/vs-1");

      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      stubSession(sessions, runtimeAccess, s, rvs);

      ScriptWriteService writeSvc = mock(ScriptWriteService.class);
      SheetAgentBroadcastService broadcast = mock(SheetAgentBroadcastService.class);

      ScriptAgentController ctrl = controller(
         featureOn(), mock(SheetJoinService.class), sessions, runtimeAccess,
         broadcast, mock(ScriptReadService.class), writeSvc,
         mock(ScriptExecuteService.class), mock(ScriptContextService.class),
         mock(ScriptApiService.class), mock(ViewsheetService.class));

      ScriptEditRequest req = new ScriptEditRequest("vs-init", "alert(1);", null, null);
      ctrl.writeScript("TOK-W", req, agent);

      verify(writeSvc).write(eq(rvs), argThat(t -> t.location() == ScriptLocation.VS_INIT),
                             eq("alert(1);"));
      verify(broadcast).broadcastRefresh(eq(rvs), eq(SheetType.VIEWSHEET), eq("Viewsheet/vs-1"),
                                         eq(agent));
   }

   // ---------------------------------------------------------------------------
   // enableScript
   // ---------------------------------------------------------------------------

   @Test
   void enableScriptDelegatesToWriteServiceAndBroadcasts() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      JoinSession s = session("TOK-EN", "Viewsheet/vs-1");

      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      stubSession(sessions, runtimeAccess, s, rvs);

      ScriptWriteService writeSvc = mock(ScriptWriteService.class);
      SheetAgentBroadcastService broadcast = mock(SheetAgentBroadcastService.class);

      ScriptAgentController ctrl = controller(
         featureOn(), mock(SheetJoinService.class), sessions, runtimeAccess,
         broadcast, mock(ScriptReadService.class), writeSvc,
         mock(ScriptExecuteService.class), mock(ScriptContextService.class),
         mock(ScriptApiService.class), mock(ViewsheetService.class));

      ScriptEditRequest req = new ScriptEditRequest("assembly:Chart1", null, true, null);
      ctrl.enableScript("TOK-EN", req, agent);

      verify(writeSvc).setEnabled(eq(rvs),
                                   argThat(t -> t.location() == ScriptLocation.ASSEMBLY
                                               && "Chart1".equals(t.assemblyName())),
                                   eq(true));
      verify(broadcast).broadcastRefresh(any(), eq(SheetType.VIEWSHEET), any(), any());
   }

   // ---------------------------------------------------------------------------
   // execute (dry-run)
   // ---------------------------------------------------------------------------

   @Test
   void dryRunDelegatesToExecuteService() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      JoinSession s = session("TOK-DR", "Viewsheet/vs-1");

      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      stubSession(sessions, runtimeAccess, s, rvs);

      ScriptExecResult expected = ScriptExecResult.dryRunSuccess(42, List.of());
      ScriptExecuteService execSvc = mock(ScriptExecuteService.class);
      when(execSvc.dryRun(eq(rvs), any(ScriptTarget.class))).thenReturn(expected);

      ScriptAgentController ctrl = controller(
         featureOn(), mock(SheetJoinService.class), sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(ScriptReadService.class),
         mock(ScriptWriteService.class), execSvc,
         mock(ScriptContextService.class), mock(ScriptApiService.class),
         mock(ViewsheetService.class));

      ScriptExecResult result = ctrl.dryRun("TOK-DR",
         new ScriptEditRequest("vs-init", null, null, null), agent);

      assertTrue(result.ok());
      assertEquals(42, result.value());
   }

   // ---------------------------------------------------------------------------
   // execute-live
   // ---------------------------------------------------------------------------

   @Test
   void runLiveBroadcastsOnSuccess() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      JoinSession s = session("TOK-LV", "Viewsheet/vs-1");

      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      stubSession(sessions, runtimeAccess, s, rvs);

      ScriptExecResult ok = ScriptExecResult.success(null);
      ScriptExecuteService execSvc = mock(ScriptExecuteService.class);
      when(execSvc.runLive(eq(rvs), any(ScriptTarget.class), eq(false))).thenReturn(ok);

      SheetAgentBroadcastService broadcast = mock(SheetAgentBroadcastService.class);

      ScriptAgentController ctrl = controller(
         featureOn(), mock(SheetJoinService.class), sessions, runtimeAccess,
         broadcast, mock(ScriptReadService.class), mock(ScriptWriteService.class),
         execSvc, mock(ScriptContextService.class), mock(ScriptApiService.class),
         mock(ViewsheetService.class));

      ScriptExecResult result = ctrl.runLive("TOK-LV",
         new ScriptEditRequest("vs-load", null, null, false), agent);

      assertTrue(result.ok());
      verify(broadcast).broadcastRefresh(eq(rvs), eq(SheetType.VIEWSHEET),
                                          eq("Viewsheet/vs-1"), eq(agent));
   }

   @Test
   void runLiveSkipsBroadcastOnFailure() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      JoinSession s = session("TOK-LF", "Viewsheet/vs-1");

      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      stubSession(sessions, runtimeAccess, s, rvs);

      ScriptExecResult failure = ScriptExecResult.failure(
         new ScriptError("syntax error", null));
      ScriptExecuteService execSvc = mock(ScriptExecuteService.class);
      when(execSvc.runLive(eq(rvs), any(ScriptTarget.class), anyBoolean())).thenReturn(failure);

      SheetAgentBroadcastService broadcast = mock(SheetAgentBroadcastService.class);

      ScriptAgentController ctrl = controller(
         featureOn(), mock(SheetJoinService.class), sessions, runtimeAccess,
         broadcast, mock(ScriptReadService.class), mock(ScriptWriteService.class),
         execSvc, mock(ScriptContextService.class), mock(ScriptApiService.class),
         mock(ViewsheetService.class));

      ScriptExecResult result = ctrl.runLive("TOK-LF",
         new ScriptEditRequest("vs-init", null, null, false), agent);

      assertFalse(result.ok());
      verify(broadcast, never()).broadcastRefresh(any(), any(), any(), any());
   }

   @Test
   void runLivePassesConfirmedFlag() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      JoinSession s = session("TOK-CF", "Viewsheet/vs-1");

      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      stubSession(sessions, runtimeAccess, s, rvs);

      ScriptExecuteService execSvc = mock(ScriptExecuteService.class);
      when(execSvc.runLive(any(), any(), eq(true)))
         .thenReturn(ScriptExecResult.success(null));

      ScriptAgentController ctrl = controller(
         featureOn(), mock(SheetJoinService.class), sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(ScriptReadService.class),
         mock(ScriptWriteService.class), execSvc,
         mock(ScriptContextService.class), mock(ScriptApiService.class),
         mock(ViewsheetService.class));

      ctrl.runLive("TOK-CF",
         new ScriptEditRequest("vs-init", null, null, true), agent);

      verify(execSvc).runLive(any(), any(), eq(true));
   }

   // ---------------------------------------------------------------------------
   // context
   // ---------------------------------------------------------------------------

   @Test
   void contextDelegatesToContextService() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      JoinSession s = session("TOK-CTX", "Viewsheet/vs-1");

      Viewsheet vs = new Viewsheet();
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      stubSession(sessions, runtimeAccess, s, rvs);

      ScriptContextService ctxSvc = new ScriptContextService();

      ScriptAgentController ctrl = controller(
         featureOn(), mock(SheetJoinService.class), sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(ScriptReadService.class),
         mock(ScriptWriteService.class), mock(ScriptExecuteService.class),
         ctxSvc, mock(ScriptApiService.class), mock(ViewsheetService.class));

      ScriptContext ctx = ctrl.context("TOK-CTX", agent);

      assertNotNull(ctx);
      assertTrue(ctx.globals().contains("runQuery"));
   }

   // ---------------------------------------------------------------------------
   // signature
   // ---------------------------------------------------------------------------

   @Test
   void signatureDelegatesToApiService() {
      ScriptApiService apiSvc = mock(ScriptApiService.class);
      FunctionSignature sig = new FunctionSignature("formatDate", "fn(date: date) -> string", null);
      when(apiSvc.lookup("formatDate")).thenReturn(sig);

      ScriptAgentController ctrl = controller(
         featureOn(), mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(SheetRuntimeAccess.class), mock(SheetAgentBroadcastService.class),
         mock(ScriptReadService.class), mock(ScriptWriteService.class),
         mock(ScriptExecuteService.class), mock(ScriptContextService.class),
         apiSvc, mock(ViewsheetService.class));

      FunctionSignature result = ctrl.signature("formatDate");

      assertEquals("formatDate", result.name());
   }

   @Test
   void signatureReturnsNullForUnknown() {
      ScriptApiService apiSvc = mock(ScriptApiService.class);
      when(apiSvc.lookup(anyString())).thenReturn(null);

      ScriptAgentController ctrl = controller(
         featureOn(), mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(SheetRuntimeAccess.class), mock(SheetAgentBroadcastService.class),
         mock(ScriptReadService.class), mock(ScriptWriteService.class),
         mock(ScriptExecuteService.class), mock(ScriptContextService.class),
         apiSvc, mock(ViewsheetService.class));

      assertNull(ctrl.signature("noSuchFunction"));
   }

   // ---------------------------------------------------------------------------
   // save
   // ---------------------------------------------------------------------------

   @Test
   void saveDelegatesToViewsheetService() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      JoinSession s = session("TOK-SV", "Viewsheet/vs-1");

      Viewsheet vs = new Viewsheet();
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      when(rvs.getEntry()).thenReturn(null);

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      stubSession(sessions, runtimeAccess, s, rvs);

      ViewsheetService vsSvc = mock(ViewsheetService.class);

      ScriptAgentController ctrl = controller(
         featureOn(), mock(SheetJoinService.class), sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(ScriptReadService.class),
         mock(ScriptWriteService.class), mock(ScriptExecuteService.class),
         mock(ScriptContextService.class), mock(ScriptApiService.class), vsSvc);

      ctrl.save("TOK-SV", agent);

      verify(vsSvc).setViewsheet(eq(vs), isNull(), eq(agent), eq(true), eq(true));
   }
}
