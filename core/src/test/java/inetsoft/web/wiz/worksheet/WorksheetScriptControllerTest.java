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
package inetsoft.web.wiz.worksheet;

import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.report.composition.WorksheetService;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.uql.Condition;
import inetsoft.uql.ConditionItem;
import inetsoft.uql.ConditionList;
import inetsoft.uql.ConditionListWrapper;
import inetsoft.uql.HierarchyItem;
import inetsoft.uql.XCondition;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.BoundTableAssembly;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.asset.Worksheet;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.erm.ExpressionRef;
import inetsoft.uql.XRepository;
import inetsoft.web.composer.ws.LayoutGraphService;
import inetsoft.web.portal.controller.database.DataSourceService;
import inetsoft.web.portal.controller.database.QueryManagerService;
import inetsoft.web.wiz.pairing.*;
import inetsoft.web.wiz.script.model.ScriptInfo;
import inetsoft.web.wiz.script.model.ScriptTargetsResponse;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * G2 Task 8b: controller-level proof that {@link WorksheetScriptService} is actually wired to a
 * real HTTP surface, and that a write reaches {@link WorksheetAgentController#edit} -- the real
 * op -- rather than stopping short of it.
 *
 * <p>Before this class existed, {@link WorksheetScriptService}'s only exerciser was {@code
 * WorksheetScriptServiceTest}, which mocks {@link WorksheetAgentController}. That proves the
 * request this class BUILDS is shaped correctly; it proves nothing about whether the write
 * actually lands. The two {@code *ThroughTheRealEditOp} tests below use a REAL {@link
 * WorksheetEditService} and a REAL {@link WorksheetAgentController} (constructed the same way
 * {@link WorksheetAgentControllerTest} and {@code ViewsheetAgentControllerTest}'s pane-scope
 * fix-round tests do), backed only by mocked {@link SheetSessionService}/{@link
 * SheetRuntimeAccess}/{@link SheetAgentBroadcastService}/{@link SecurityEngine} infrastructure --
 * a MOCKED {@link WorksheetEditService} would have its {@code apply}/{@code applyOnRuntime} no-op
 * and never invoke the mutation lambda that actually rewrites the {@link Worksheet}, which is
 * exactly the failure mode Task 6's fix round found and this task exists to rule out.
 */
@Tag("core")
@WizAgentTestSupport
class WorksheetScriptControllerTest {

   private static final String TOKEN = "TOK-WS";
   private static final String RUNTIME_ID = "Worksheet/ws-1";

   // ---------------------------------------------------------------------------
   // Fixtures
   // ---------------------------------------------------------------------------

   private static JoinSession wholeSheetSession() {
      return new JoinSession(TOKEN, RUNTIME_ID, "alice~;~host-org", SheetType.WORKSHEET, 0L,
         Long.MAX_VALUE, JoinSession.ConnectionMode.PAIRED, null, null, null);
   }

   private static JoinSession paneSession(EditorContext ctx) {
      return new JoinSession(TOKEN, RUNTIME_ID, "alice~;~host-org", SheetType.WORKSHEET, 0L,
         Long.MAX_VALUE, JoinSession.ConnectionMode.PAIRED, null, null, ctx);
   }

   private static SheetAgentFeature featureOn() {
      SheetAgentFeature f = mock(SheetAgentFeature.class);
      when(f.isEnabled()).thenReturn(true);
      return f;
   }

   /**
    * The full stack behind one {@link WorksheetScriptController}: a REAL {@link
    * WorksheetEditService}, a REAL {@link WorksheetAgentController}, and a REAL {@link
    * WorksheetScriptService} wired to it -- only the session/runtime/broadcast/security
    * infrastructure below them is mocked. {@code rws} and {@code broadcast} are exposed so a test
    * can assert the undo checkpoint and refresh broadcast actually fired.
    */
   private record Stack(WorksheetScriptController controller, RuntimeWorksheet rws,
                        SheetAgentBroadcastService broadcast) {}

   private static Stack buildStack(Worksheet ws, JoinSession session) throws Exception {
      SecurityEngine securityEngine = mock(SecurityEngine.class);
      when(securityEngine.checkPermission(
         any(), any(inetsoft.sree.security.ResourceType.class), any(String.class),
         any(inetsoft.sree.security.ResourceAction.class))).thenReturn(true);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);

      SheetSessionService sessionService = mock(SheetSessionService.class);
      when(sessionService.resolve(eq(TOKEN), any())).thenReturn(session);

      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      when(runtimeAccess.getSheetForPairing(eq(SheetType.WORKSHEET), eq(RUNTIME_ID), any()))
         .thenReturn(rws);

      SheetAgentBroadcastService broadcast = mock(SheetAgentBroadcastService.class);

      WorksheetEditService editService =
         new WorksheetEditService(sessionService, runtimeAccess, broadcast, securityEngine);

      WorksheetAgentController worksheetController = new WorksheetAgentController(
         featureOn(), mock(SheetJoinService.class), sessionService,
         mock(WorksheetReadService.class), editService, mock(WorksheetService.class),
         mock(WorksheetPreviewService.class), broadcast,
         mock(XRepository.class), mock(AssetRepository.class),
         mock(inetsoft.web.wiz.service.MetadataApiService.class),
         mock(QueryManagerService.class), mock(LayoutGraphService.class),
         mock(DataSourceService.class), securityEngine);

      WorksheetScriptService scriptService =
         new WorksheetScriptService(editService, worksheetController);

      WorksheetScriptController controller =
         new WorksheetScriptController(featureOn(), sessionService, editService, scriptService);

      return new Stack(controller, rws, broadcast);
   }

   private static Principal agent() {
      return TestPrincipals.user("alice", "host-org");
   }

   // ---------------------------------------------------------------------------
   // The centerpiece: write() against a REAL WorksheetAgentController/WorksheetEditService
   // ---------------------------------------------------------------------------

   @Test
   void writeScriptWritesAnExpressionThroughTheRealEditOp() throws Exception {
      Worksheet ws = new Worksheet();
      BoundTableAssembly t =
         TestWorksheets.nonEmbeddedTableWithColumns(ws, "Query1", "price", "cost");
      ws.addAssembly(t);
      WorksheetMutationSupport.addExpressionColumn(t, "Margin", "field['price']", "double", false);

      JoinSession session =
         paneSession(new EditorContext("worksheetExpression", "Query1", "Margin", null));
      Stack stack = buildStack(ws, session);
      Principal agent = agent();

      var req = new WorksheetScriptController.WriteScriptRequest(
         null, null, "worksheetExpression", "Query1", "Margin",
         "field['price'] - field['cost']", null, null, null);

      stack.controller().writeScript(TOKEN, req, agent);

      ExpressionRef er = findExpression(t, "Margin");
      assertNotNull(er, "the real edit_expression op must have run");
      assertEquals("field['price'] - field['cost']", er.getExpression());

      // Proves the real op ran, not just that no exception was thrown: undo/redo checkpointing
      // and the refresh broadcast are side effects only WorksheetEditService.apply produces.
      verify(stack.rws()).addCheckpoint(any());
      verify(stack.broadcast())
         .broadcastRefresh(eq(stack.rws()), eq(SheetType.WORKSHEET), eq(RUNTIME_ID), eq(agent));
   }

   @Test
   void writeScriptWritesAConditionThroughTheRealEditOp() throws Exception {
      Worksheet ws = new Worksheet();
      BoundTableAssembly t =
         TestWorksheets.nonEmbeddedTableWithColumns(ws, "Query1", "price", "region");
      ws.addAssembly(t);

      JoinSession session =
         paneSession(new EditorContext("worksheetCondition", "Query1", "region", null));
      Stack stack = buildStack(ws, session);
      Principal agent = agent();

      var req = new WorksheetScriptController.WriteScriptRequest(
         null, null, "worksheetCondition", "Query1", "region", "> 0", null, null, null);

      stack.controller().writeScript(TOKEN, req, agent);

      Condition c = findCondition(t, "region");
      assertNotNull(c, "the real edit_condition op must have run");
      assertEquals(XCondition.GREATER_THAN, c.getOperation());
      assertEquals("0", String.valueOf(c.getValue(0)));

      verify(stack.rws()).addCheckpoint(any());
      verify(stack.broadcast())
         .broadcastRefresh(eq(stack.rws()), eq(SheetType.WORKSHEET), eq(RUNTIME_ID), eq(agent));
   }

   private static ExpressionRef findExpression(BoundTableAssembly t, String name) {
      ColumnSelection cs = t.getColumnSelection(false);

      for(int i = 0; i < cs.getAttributeCount(); i++) {
         DataRef ref = cs.getAttribute(i);

         if(ref instanceof ColumnRef cr && cr.getDataRef() instanceof ExpressionRef er
            && (name.equals(er.getName()) || name.equals(er.getAttribute())))
         {
            return er;
         }
      }

      return null;
   }

   private static Condition findCondition(BoundTableAssembly t, String field) {
      ConditionListWrapper wrapper = t.getPreConditionList();

      if(wrapper == null || wrapper.isEmpty()) {
         return null;
      }

      ConditionList cl = wrapper.getConditionList();

      for(int i = 0; i < cl.getSize(); i++) {
         HierarchyItem hi = cl.getItem(i);

         if(hi instanceof ConditionItem ci) {
            DataRef attr = ci.getAttribute();

            if(field.equals(attr.getAttribute()) || field.equals(attr.getName())) {
               return (Condition) ci.getXCondition();
            }
         }
      }

      return null;
   }

   // ---------------------------------------------------------------------------
   // requirePaneScope wiring -- one test per target-taking endpoint (readScript, writeScript)
   // ---------------------------------------------------------------------------

   @Test
   void readScriptRefusesAWholeSheetSessionForAnExpressionTarget() throws Exception {
      Worksheet ws = new Worksheet();
      BoundTableAssembly t = TestWorksheets.nonEmbeddedTableWithColumns(ws, "Query1", "price");
      ws.addAssembly(t);
      WorksheetMutationSupport.addExpressionColumn(t, "Margin", "field['price']", "double", false);

      Stack stack = buildStack(ws, wholeSheetSession());
      Principal agent = agent();

      PairingException ex = assertThrows(PairingException.class, () ->
         stack.controller().readScript(TOKEN, null, null, "worksheetExpression", "Query1", "Margin",
            agent));

      assertTrue(ex.getMessage().contains("open its expression editor"), ex.getMessage());
      assertFalse(ex.getMessage().contains("require-script-pane"), ex.getMessage());
   }

   @Test
   void writeScriptRefusesAWholeSheetSessionForAConditionTarget() throws Exception {
      Worksheet ws = new Worksheet();
      BoundTableAssembly t = TestWorksheets.nonEmbeddedTableWithColumns(ws, "Query1", "region");
      ws.addAssembly(t);

      Stack stack = buildStack(ws, wholeSheetSession());
      Principal agent = agent();
      var req = new WorksheetScriptController.WriteScriptRequest(
         null, null, "worksheetCondition", "Query1", "region", "> 0", null, null, null);

      PairingException ex = assertThrows(PairingException.class,
         () -> stack.controller().writeScript(TOKEN, req, agent));

      assertTrue(ex.getMessage().contains("open its condition editor"), ex.getMessage());
      assertFalse(ex.getMessage().contains("require-script-pane"), ex.getMessage());

      // The refusal must have happened before the real op ran -- the table stays untouched.
      assertNull(findCondition(t, "region"));
   }

   // ---------------------------------------------------------------------------
   // targets -- enumeration, scoped for a pane session exactly as Task 7 scoped the viewsheet one
   // ---------------------------------------------------------------------------

   @Test
   void targetsAdvertisesSupportedKindsAndNarrowsToAPaneScopedGrant() throws Exception {
      Worksheet ws = new Worksheet();
      BoundTableAssembly t =
         TestWorksheets.nonEmbeddedTableWithColumns(ws, "Query1", "price", "region");
      ws.addAssembly(t);
      WorksheetMutationSupport.addExpressionColumn(t, "Margin", "field['price']", "double", false);
      WorksheetMutationSupport.addFilter(t, "region", ">", "0");

      JoinSession session =
         paneSession(new EditorContext("worksheetExpression", "Query1", "Margin", null));
      Stack stack = buildStack(ws, session);

      ScriptTargetsResponse resp = stack.controller().targets(TOKEN, agent());

      assertTrue(resp.supportedKinds().contains("worksheetExpression"), resp.supportedKinds().toString());
      assertTrue(resp.supportedKinds().contains("worksheetCondition"), resp.supportedKinds().toString());
      assertEquals(1, resp.targets().size(), resp.targets().toString());
      assertEquals("Margin", resp.targets().get(0).name());
   }

   @Test
   void targetsShowsTheFullEnumerationForAWholeSheetSession() throws Exception {
      Worksheet ws = new Worksheet();
      BoundTableAssembly t =
         TestWorksheets.nonEmbeddedTableWithColumns(ws, "Query1", "price", "region");
      ws.addAssembly(t);
      WorksheetMutationSupport.addExpressionColumn(t, "Margin", "field['price']", "double", false);
      WorksheetMutationSupport.addFilter(t, "region", ">", "0");

      Stack stack = buildStack(ws, wholeSheetSession());

      ScriptTargetsResponse resp = stack.controller().targets(TOKEN, agent());

      assertEquals(2, resp.targets().size(), resp.targets().toString());
   }

   // ---------------------------------------------------------------------------
   // Feature gate
   // ---------------------------------------------------------------------------

   @Test
   void writeScriptRejectsFlagOff() throws Exception {
      Worksheet ws = new Worksheet();
      BoundTableAssembly t = TestWorksheets.nonEmbeddedTableWithColumns(ws, "Query1", "region");
      ws.addAssembly(t);

      SecurityEngine securityEngine = mock(SecurityEngine.class);
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      SheetSessionService sessionService = mock(SheetSessionService.class);
      SheetAgentFeature featureOff = mock(SheetAgentFeature.class);
      when(featureOff.isEnabled()).thenReturn(false);

      WorksheetEditService editService = new WorksheetEditService(
         sessionService, mock(SheetRuntimeAccess.class),
         mock(SheetAgentBroadcastService.class), securityEngine);
      WorksheetScriptService scriptService = new WorksheetScriptService(
         editService, mock(WorksheetAgentController.class));
      WorksheetScriptController controller =
         new WorksheetScriptController(featureOff, sessionService, editService, scriptService);

      var req = new WorksheetScriptController.WriteScriptRequest(
         null, null, "worksheetCondition", "Query1", "region", "> 0", null, null, null);

      assertThrows(ResponseStatusException.class,
         () -> controller.writeScript(TOKEN, req, agent()));
   }
}
