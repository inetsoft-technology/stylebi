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
import inetsoft.web.wiz.pairing.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link ViewsheetAgentController}'s {@code /script/join} endpoint, plus (below)
 * controller-level proof that {@code requirePaneScope} (G2 Task 6) actually runs at each of the
 * seven target-taking endpoints.
 *
 * <p>There was previously no controller-level test for the script join endpoint at all --
 * {@code editorContext} threading (Task 5, G2) was verified only at the
 * {@code SheetJoinService.join} seam ({@code SheetJoinServiceTest}), never through this
 * controller's own {@link ViewsheetAgentController.JoinResponse}. This file closes that gap.
 *
 * <p>{@code @WizAgentTestSupport} (rather than the plain {@code @Tag("core")} the join tests
 * needed) is required from here down: {@code requirePaneScope} reads
 * {@code SreeEnv.getBooleanProperty}, which resolves {@code PropertiesEngine} through a live
 * Spring {@code ApplicationContext} -- without one, that call fails outright, not merely
 * defaults. Confirmed additive: the three {@code join} tests above needed no such context and
 * still pass under it.
 */
@WizAgentTestSupport
class ViewsheetAgentControllerTest {

   @Test
   void joinRefusesWhenTheFeatureIsDisabled() {
      SheetAgentFeature feature = mock(SheetAgentFeature.class);
      when(feature.isEnabled()).thenReturn(false);

      ViewsheetAgentController controller = controllerWith(feature, mock(SheetJoinService.class));

      assertThrows(ResponseStatusException.class,
                   () -> controller.join("CODE", principal()));
   }

   /**
    * The join response must carry the session's editorContext through -- this is what lets a
    * pane-scoped ("Connect to Claude" from a script pane) session be told apart, on the wire,
    * from a whole-sheet toolbar session. Dropping it here doesn't surface as an error; it reads
    * as an ordinary whole-sheet session, indistinguishable from a legitimate toolbar mint.
    */
   @Test
   void joinReturnsTheSessionsEditorContext() throws Exception {
      SheetAgentFeature feature = mock(SheetAgentFeature.class);
      when(feature.isEnabled()).thenReturn(true);

      EditorContext ctx = new EditorContext("assemblyOnClick", "Chart1", null, null);
      JoinSession session = new JoinSession("TOK-1", "Viewsheet/vs-1", "alice~;~host-org",
         SheetType.VIEWSHEET, 0L, Long.MAX_VALUE, JoinSession.ConnectionMode.PAIRED,
         null, null, ctx);

      SheetJoinService joinService = mock(SheetJoinService.class);
      Principal agent = principal();
      when(joinService.join(eq("CODE"), eq(agent))).thenReturn(session);

      ViewsheetAgentController controller = controllerWith(feature, joinService);

      ViewsheetAgentController.JoinResponse resp = controller.join("CODE", agent);

      assertEquals("TOK-1", resp.sessionToken());
      assertEquals(ctx, resp.editorContext());
   }

   @Test
   void joinReturnsNullEditorContextForAWholeSheetSession() throws Exception {
      SheetAgentFeature feature = mock(SheetAgentFeature.class);
      when(feature.isEnabled()).thenReturn(true);

      JoinSession session = new JoinSession("TOK-2", "Viewsheet/vs-2", "alice~;~host-org",
         SheetType.VIEWSHEET, 0L, Long.MAX_VALUE, JoinSession.ConnectionMode.PAIRED,
         null, null, null);

      SheetJoinService joinService = mock(SheetJoinService.class);
      Principal agent = principal();
      when(joinService.join(eq("CODE"), eq(agent))).thenReturn(session);

      ViewsheetAgentController controller = controllerWith(feature, joinService);

      ViewsheetAgentController.JoinResponse resp = controller.join("CODE", agent);

      assertNull(resp.editorContext());
   }

   private static ViewsheetAgentController controllerWith(SheetAgentFeature feature,
                                                          SheetJoinService joinService)
   {
      return new ViewsheetAgentController(feature, joinService,
         mock(SheetSessionService.class), mock(ScriptEditService.class),
         mock(ScriptReadService.class), mock(ScriptExecuteService.class),
         mock(ScriptContextService.class), mock(ScriptApiService.class),
         mock(ScriptImageService.class), mock(ViewsheetService.class),
         mock(SheetAgentBroadcastService.class));
   }

   private static Principal principal() {
      return () -> "admin";
   }

   // ---------------------------------------------------------------------------
   // requirePaneScope wiring -- one test per target-taking endpoint (G2 Task 6 fix round)
   // ---------------------------------------------------------------------------

   /**
    * Proves the controller-level wiring, not just the unit-level rule tested in
    * {@link PaneScopeServiceTest}: a whole-sheet session ({@code editorContext == null})
    * addressing a {@code calcField} target must be refused by every one of the seven endpoints
    * that resolve a {@link ScriptTarget}, in the naming reason
    * ("open its formula editor"), not the policy.
    *
    * <p>Covers all seven sites rather than one representative, because a later task (G2 Task 7/8)
    * edits this same controller again, and the failure this guards against -- someone reorders
    * {@code requirePaneScope(...)} to run after the underlying service call in one endpoint, or
    * drops it from one of the seven during an unrelated edit -- is a per-site regression that a
    * single passing test elsewhere would not catch. The four prior defects in this project with
    * this shape (a declared-but-unenforced field/flag) all escaped a suite that tested the
    * *mechanism* once instead of every call site.
    *
    * <p>Uses a REAL {@link ScriptEditService} (constructed the same way
    * {@code ScriptEditServiceTest} does), backed by mocked {@link SheetSessionService} /
    * {@link SheetRuntimeAccess} / {@link SheetAgentBroadcastService} collaborators, rather than a
    * mocked {@code ScriptEditService} -- a mock's {@code apply}/{@code applyOnRuntime}/
    * {@code applyOnRuntimeIfChanged} would no-op and never invoke the mutation lambda that
    * contains the controller's own {@code requirePaneScope} call, which would prove nothing about
    * the four endpoints wired through those methods (write/setEnabled/execute/executeLive).
    */
   private static final String PANE_TOKEN = "TOK-PANE";
   private static final String PANE_RUNTIME_ID = "Viewsheet/vs-pane";

   private static JoinSession wholeSheetSession() {
      return new JoinSession(PANE_TOKEN, PANE_RUNTIME_ID, "admin", SheetType.VIEWSHEET, 0L,
         Long.MAX_VALUE, JoinSession.ConnectionMode.PAIRED, null, null, null);
   }

   private static ViewsheetAgentController wholeSheetController() throws PairingException {
      SheetAgentFeature feature = mock(SheetAgentFeature.class);
      when(feature.isEnabled()).thenReturn(true);

      SheetSessionService sessionService = mock(SheetSessionService.class);
      when(sessionService.resolve(eq(PANE_TOKEN), eq("admin"))).thenReturn(wholeSheetSession());

      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      when(runtimeAccess.getSheetForPairing(eq(SheetType.VIEWSHEET), eq(PANE_RUNTIME_ID), any()))
         .thenReturn(mock(RuntimeViewsheet.class));

      ScriptEditService editService = new ScriptEditService(sessionService, runtimeAccess,
         mock(SheetAgentBroadcastService.class));

      return new ViewsheetAgentController(feature, mock(SheetJoinService.class), sessionService,
         editService, mock(ScriptReadService.class), mock(ScriptExecuteService.class),
         mock(ScriptContextService.class), mock(ScriptApiService.class),
         mock(ScriptImageService.class), mock(ViewsheetService.class),
         mock(SheetAgentBroadcastService.class));
   }

   private static void assertRefusesCalcFieldReason(Executable action) {
      PairingException ex = assertThrows(PairingException.class, action);
      assertTrue(ex.getMessage().contains("open its formula editor"), ex.getMessage());
      assertFalse(ex.getMessage().contains("require-script-pane"), ex.getMessage());
   }

   @Test
   void readScriptRefusesACalcFieldFromAWholeSheetSession() throws Exception {
      ViewsheetAgentController controller = wholeSheetController();
      Principal agent = principal();

      assertRefusesCalcFieldReason(() ->
         controller.readScript(PANE_TOKEN, null, null, "calcField", "Query1", "Margin", agent));
   }

   @Test
   void writeScriptRefusesACalcFieldFromAWholeSheetSession() throws Exception {
      ViewsheetAgentController controller = wholeSheetController();
      Principal agent = principal();
      var req = new ViewsheetAgentController.WriteScriptRequest(
         null, null, "calcField", "Query1", "Margin", "price - cost");

      assertRefusesCalcFieldReason(() -> controller.writeScript(PANE_TOKEN, req, agent));
   }

   @Test
   void setEnabledRefusesACalcFieldFromAWholeSheetSession() throws Exception {
      ViewsheetAgentController controller = wholeSheetController();
      Principal agent = principal();
      var req = new ViewsheetAgentController.SetEnabledRequest(
         null, null, "calcField", "Query1", "Margin", true);

      assertRefusesCalcFieldReason(() -> controller.setEnabled(PANE_TOKEN, req, agent));
   }

   @Test
   void executeRefusesACalcFieldFromAWholeSheetSession() throws Exception {
      ViewsheetAgentController controller = wholeSheetController();
      Principal agent = principal();
      var req = new ViewsheetAgentController.ExecuteRequest(
         null, null, "calcField", "Query1", "Margin");

      assertRefusesCalcFieldReason(() -> controller.execute(PANE_TOKEN, req, agent));
   }

   @Test
   void executeLiveRefusesACalcFieldFromAWholeSheetSession() throws Exception {
      ViewsheetAgentController controller = wholeSheetController();
      Principal agent = principal();
      var req = new ViewsheetAgentController.ExecuteLiveRequest(
         null, null, "calcField", "Query1", "Margin", true);

      assertRefusesCalcFieldReason(() -> controller.executeLive(PANE_TOKEN, req, agent));
   }

   @Test
   void contextRefusesACalcFieldFromAWholeSheetSession() throws Exception {
      ViewsheetAgentController controller = wholeSheetController();
      Principal agent = principal();

      assertRefusesCalcFieldReason(() ->
         controller.context(PANE_TOKEN, null, null, "calcField", "Query1", "Margin", agent));
   }

   @Test
   void imageRefusesACalcFieldFromAWholeSheetSession() throws Exception {
      ViewsheetAgentController controller = wholeSheetController();
      Principal agent = principal();

      assertRefusesCalcFieldReason(() -> controller.image(
         PANE_TOKEN, null, null, "calcField", "Query1", "Margin", null, null, agent));
   }
}
