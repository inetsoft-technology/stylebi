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
import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.sree.security.SecurityException;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.SQLBoundTableAssemblyInfo;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.uql.jdbc.JDBCQuery;
import inetsoft.web.composer.ws.LayoutGraphService;
import inetsoft.web.portal.controller.database.DataSourceService;
import inetsoft.web.portal.controller.database.QueryManagerService;
import inetsoft.web.wiz.pairing.*;
import inetsoft.web.wiz.service.MetadataApiService;
import inetsoft.web.wiz.worksheet.model.WorksheetModel;
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
class WorksheetAgentControllerTest {

   // ---------------------------------------------------------------------------
   // Helpers
   // ---------------------------------------------------------------------------

   private static JoinSession session(String token) {
      return new JoinSession(token, "Worksheet/ws-1", "alice~;~host-org",
                             SheetType.WORKSHEET, 0L, Long.MAX_VALUE,
                             JoinSession.ConnectionMode.PAIRED, null, null);
   }

   private static WorksheetAgentController controller(SheetAgentFeature feature,
                                                       SheetJoinService join,
                                                       SheetSessionService sessions,
                                                       WorksheetReadService read,
                                                       WorksheetEditService edit,
                                                       WorksheetService ws)
   {
      return new WorksheetAgentController(feature, join, sessions, read, edit, ws,
                                          mock(WorksheetPreviewService.class),
                                          mock(SheetAgentBroadcastService.class),
                                          mock(inetsoft.uql.XRepository.class),
                                          mock(inetsoft.uql.asset.AssetRepository.class),
                                          mock(inetsoft.web.wiz.service.MetadataApiService.class),
                                          mock(inetsoft.web.portal.controller.database.QueryManagerService.class),
                                          mock(inetsoft.web.composer.ws.LayoutGraphService.class),
                                          mock(inetsoft.web.portal.controller.database.DataSourceService.class),
                                          mock(inetsoft.sree.security.SecurityEngine.class));
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

   /**
    * Builds a controller (feature flag ON) exposing the specific collaborators a permission
    * test needs to stub/verify; every other dependency is a fresh mock.
    */
   private static WorksheetAgentController securityController(
      WorksheetEditService edit,
      DataSourceService dataSourceService,
      SecurityEngine securityEngine,
      MetadataApiService metadataApiService,
      XRepository xrepository,
      QueryManagerService queryManagerService)
   {
      return new WorksheetAgentController(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), edit, mock(WorksheetService.class),
         mock(WorksheetPreviewService.class), mock(SheetAgentBroadcastService.class),
         xrepository, mock(AssetRepository.class), metadataApiService,
         queryManagerService, mock(LayoutGraphService.class),
         dataSourceService, securityEngine);
   }

   /** Builds an {@code add_table} EditRequest that routes to addBoundTable() (no logicalModel). */
   private static EditRequest addBoundTableRequest(String table, String datasource) {
      return new EditRequest(
         "add_table", table, null, null, null, null, null, null, null, null,
         null, null, null, false, null, null, null, null, null, null, null, null, null, null,
         null, null, null, null, datasource, null, null, null, null, null, null, null, null,
         null, null, null, null, null, null, null, null, null, null, null, null, null, null,
         null, null
      );
   }

   /** Builds an {@code edit_sql_query} EditRequest that routes to editSqlQuery(). */
   private static EditRequest editSqlQueryRequest(String table, String expression) {
      return new EditRequest(
         "edit_sql_query", table, null, null, null, null, null, null, null, null,
         null, null, expression, false, null, null, null, null, null, null, null, null, null, null,
         null, null, null, null, null, null, null, null, null, null, null, null, null,
         null, null, null, null, null, null, null, null, null, null, null, null, null, null,
         null, null
      );
   }

   // ---------------------------------------------------------------------------
   // join
   // ---------------------------------------------------------------------------

   @Test
   void joinReturnsSessionToken() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      JoinSession s = session("TOK-1");

      SheetJoinService joinSvc = mock(SheetJoinService.class);
      when(joinSvc.join(eq("CODE"), eq(agent))).thenReturn(s);

      WorksheetAgentController ctrl = controller(featureOn(), joinSvc,
         mock(SheetSessionService.class), mock(WorksheetReadService.class),
         mock(WorksheetEditService.class), mock(WorksheetService.class));

      WorksheetAgentController.JoinResponse resp = ctrl.join(new WorksheetAgentController.JoinRequest("CODE"), agent);

      assertEquals("TOK-1", resp.sessionToken());
      assertEquals("Worksheet/ws-1", resp.runtimeId());
      assertEquals("alice~;~host-org", resp.ownerIdentity());
   }

   @Test
   void joinRejectsFlagOff() {
      WorksheetAgentController ctrl = controller(featureOff(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), mock(WorksheetEditService.class),
         mock(WorksheetService.class));

      ResponseStatusException ex = assertThrows(ResponseStatusException.class,
         () -> ctrl.join(new WorksheetAgentController.JoinRequest("CODE"), TestPrincipals.user("alice", "host-org")));
      assertEquals(403, ex.getStatusCode().value());
   }

   // ---------------------------------------------------------------------------
   // read
   // ---------------------------------------------------------------------------

   @Test
   void readReturnsModel() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "a", "b");
      ws.addAssembly(t);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);

      WorksheetEditService editSvc = mock(WorksheetEditService.class);
      when(editSvc.resolve(eq("TOK"), eq(agent))).thenReturn(rws);

      WorksheetReadService readSvc = new WorksheetReadService();

      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         readSvc, editSvc, mock(WorksheetService.class));

      WorksheetModel model = ctrl.read("TOK", agent);

      assertNotNull(model);
      assertFalse(model.tables().isEmpty());
      assertEquals("T", model.tables().get(0).name());
   }

   // ---------------------------------------------------------------------------
   // edit — dispatch
   // ---------------------------------------------------------------------------

   @Test
   void editDispatchesRemoveColumn() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "x", "y");
      ws.addAssembly(t);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      JoinSession s = session("TOK-E");
      when(sessions.resolve(eq("TOK-E"), any())).thenReturn(s);
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      WorksheetEditService editSvc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class));

      EditRequest req = new EditRequest("remove_column", "T", "x",
         null, null, null, null, null, null, null, null, null, null, false,
         null, null, null, null, null,
         null, null, null, null, null, null, null, null, null, null, null, null, null,
         null, null, null, null, null, null, null, null, null, null,
         null, null, null, null,
         null, null,
         null, null,
         null, null, null);

      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), editSvc, mock(WorksheetService.class));

      ctrl.edit("TOK-E", req, agent);

      assertNull(t.getColumnSelection(false).getAttribute("x"),
                 "column 'x' should have been removed");
      assertNotNull(t.getColumnSelection(false).getAttribute("y"),
                    "column 'y' should still be present");
   }

   @Test
   void editAddColumnAutoNamesOnEmbeddedTableWithoutName() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "x", "y");
      ws.addAssembly(t);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      JoinSession s = session("TOK-AC");
      when(sessions.resolve(eq("TOK-AC"), any())).thenReturn(s);
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      WorksheetEditService editSvc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class));

      // "add_column" with no 'name' on an EMBEDDED table must auto-generate the
      // next available "col" + N, matching the Composer UI's own insert-column
      // behavior (InsertDataService.insertData) — a brand-new spreadsheet-style
      // column has no pre-existing identity to name it after.
      EditRequest req = new EditRequest("add_column", "T", null,
         null, null, null, null, null, null, null, null, null, null, false,
         null, null, null, null, null,
         null, null, null, null, null, null, null, null, null, null, null, null, null,
         null, null, null, null, null, null, null, null, null, null,
         null, null, null, null,
         null, null,
         null, null,
         null, null, null);

      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), editSvc, mock(WorksheetService.class));

      ctrl.edit("TOK-AC", req, agent);

      assertNotNull(t.getColumnSelection(false).getAttribute("col1"),
                    "auto-generated column 'col1' should have been added");
   }

   @Test
   void editRejectsAddColumnWithoutNameOnNonEmbeddedTable() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "x", "y");
      ws.addAssembly(t);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      JoinSession s = session("TOK-ACM");
      when(sessions.resolve(eq("TOK-ACM"), any())).thenReturn(s);
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      WorksheetEditService editSvc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class));

      editSvc.apply("TOK-ACM", agent, editor -> editor.addMirror("M", "T"));

      // "add_column" with no 'name' on a NON-embedded table (here, a mirror) has
      // no embedded grid to insert a blank column into — it means re-adding an
      // existing-but-hidden column, so there is no unambiguous default and it
      // must fail loud rather than poison the column selection with a null name.
      EditRequest req = new EditRequest("add_column", "M", null,
         null, null, null, null, null, null, null, null, null, null, false,
         null, null, null, null, null,
         null, null, null, null, null, null, null, null, null, null, null, null, null,
         null, null, null, null, null, null, null, null, null, null,
         null, null, null, null,
         null, null,
         null, null,
         null, null, null);

      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), editSvc, mock(WorksheetService.class));

      PairingException ex = assertThrows(PairingException.class,
         () -> ctrl.edit("TOK-ACM", req, agent));
      assertTrue(ex.getMessage().contains("name"));
   }

   @Test
   void editRejectsAddExpressionColumnWithoutName() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "x", "y");
      ws.addAssembly(t);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      JoinSession s = session("TOK-AEC");
      when(sessions.resolve(eq("TOK-AEC"), any())).thenReturn(s);
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      WorksheetEditService editSvc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class));

      // Regression for the "alias" vs "name" mixup: calling add_expression_column
      // without 'name' used to silently succeed and create an unreferenceable
      // null-named expression column that broke every subsequent field lookup
      // (set_conditions, set_sort, etc.) on the table.
      EditRequest req = new EditRequest("add_expression_column", "T", null,
         null, null, null, null, null, null, null, null, null, null, false,
         null, null, null, null, null,
         null, null, null, null, null, null, null, null, null, null, null, null, null,
         null, null, null, null, null, null, null, null, null, null,
         null, null, null, null,
         null, null,
         null, null,
         null, null, null);

      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), editSvc, mock(WorksheetService.class));

      PairingException ex = assertThrows(PairingException.class,
         () -> ctrl.edit("TOK-AEC", req, agent));
      assertTrue(ex.getMessage().contains("name"));

      assertTrue(t.getColumnSelection(false).getAttributeCount() == 2,
         "no column should have been added when 'name' was missing");
   }

   @Test
   void editRejectsEditExpressionWithoutName() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "x", "y");
      ws.addAssembly(t);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      JoinSession s = session("TOK-EE");
      when(sessions.resolve(eq("TOK-EE"), any())).thenReturn(s);
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      WorksheetEditService editSvc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class));

      EditRequest req = new EditRequest("edit_expression", "T", null,
         null, null, null, null, null, null, null, null, null, null, false,
         null, null, null, null, null,
         null, null, null, null, null, null, null, null, null, null, null, null, null,
         null, null, null, null, null, null, null, null, null, null,
         null, null, null, null,
         null, null,
         null, null,
         null, null, null);

      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), editSvc, mock(WorksheetService.class));

      PairingException ex = assertThrows(PairingException.class,
         () -> ctrl.edit("TOK-EE", req, agent));
      assertTrue(ex.getMessage().contains("name"));
   }

   // ---------------------------------------------------------------------------
   // detach
   // ---------------------------------------------------------------------------

   @Test
   void detachClosesSession() {
      SheetSessionService sessions = mock(SheetSessionService.class);
      Principal agent = TestPrincipals.user("alice", "host-org");
      // resolve must return a non-null session so the ownership check passes
      when(sessions.resolve(eq("TOK-D"), any())).thenReturn(session("TOK-D"));

      // feature is OFF — detach must still work
      WorksheetAgentController ctrl = controller(featureOff(),
         mock(SheetJoinService.class), sessions,
         mock(WorksheetReadService.class), mock(WorksheetEditService.class),
         mock(WorksheetService.class));

      ctrl.detach("TOK-D", agent);

      verify(sessions).close("TOK-D");
   }

   // ---------------------------------------------------------------------------
   // addBoundTable — PHYSICAL_TABLE / ACCESS permission gate
   // ---------------------------------------------------------------------------

   @Test
   void addBoundTableDeniedThrowsSecurityException() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      WorksheetEditService editSvc = mock(WorksheetEditService.class);
      SecurityEngine securityEngine = mock(SecurityEngine.class);
      MetadataApiService metadataApiService = mock(MetadataApiService.class);

      when(securityEngine.checkPermission(eq(agent), eq(ResourceType.PHYSICAL_TABLE),
                                          eq("*"), eq(ResourceAction.ACCESS)))
         .thenReturn(false);

      WorksheetAgentController ctrl = securityController(editSvc,
         mock(DataSourceService.class), securityEngine, metadataApiService,
         mock(XRepository.class), mock(QueryManagerService.class));

      EditRequest req = addBoundTableRequest("dbo.orders", "MyDatasource");

      assertThrows(SecurityException.class, () -> ctrl.edit("TOK-BT", req, agent));

      verify(securityEngine).checkPermission(eq(agent), eq(ResourceType.PHYSICAL_TABLE),
                                             eq("*"), eq(ResourceAction.ACCESS));
      verifyNoInteractions(metadataApiService);
      verifyNoInteractions(editSvc);
   }

   @Test
   void addBoundTableGrantedPassesPermissionGate() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      WorksheetEditService editSvc = mock(WorksheetEditService.class);
      SecurityEngine securityEngine = mock(SecurityEngine.class);
      MetadataApiService metadataApiService = mock(MetadataApiService.class);

      when(securityEngine.checkPermission(eq(agent), eq(ResourceType.PHYSICAL_TABLE),
                                          eq("*"), eq(ResourceAction.ACCESS)))
         .thenReturn(true);

      // Datasource READ is now checked before the JDBC metadata probe; grant it so execution
      // reaches getJDBCDatasource.
      DataSourceService dataSourceService = mock(DataSourceService.class);
      when(dataSourceService.checkPermission(eq("MyDatasource"), eq(ResourceAction.READ), eq(agent)))
         .thenReturn(true);

      WorksheetAgentController ctrl = securityController(editSvc,
         dataSourceService, securityEngine, metadataApiService,
         mock(XRepository.class), mock(QueryManagerService.class));

      EditRequest req = addBoundTableRequest("dbo.orders", "MyDatasource");

      // Gets past the security gate; fails further downstream because metadataApiService is
      // an unstubbed mock (getJDBCDatasource/getTableMetaData return null), which is expected —
      // we only assert that the PHYSICAL_TABLE check let execution proceed.
      Exception ex = assertThrows(Exception.class, () -> ctrl.edit("TOK-BT2", req, agent));
      assertFalse(ex instanceof SecurityException,
                   "should not fail on the permission check when granted");

      verify(securityEngine).checkPermission(eq(agent), eq(ResourceType.PHYSICAL_TABLE),
                                             eq("*"), eq(ResourceAction.ACCESS));
      verify(metadataApiService).getJDBCDatasource("MyDatasource");
   }

   @Test
   void addBoundTableDeniedByDatasourceReadThrowsPairingExceptionBeforeProbe() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      SecurityEngine securityEngine = mock(SecurityEngine.class);
      MetadataApiService metadataApiService = mock(MetadataApiService.class);
      DataSourceService dataSourceService = mock(DataSourceService.class);

      // Physical-table action granted, but datasource READ denied.
      when(securityEngine.checkPermission(eq(agent), eq(ResourceType.PHYSICAL_TABLE),
                                          eq("*"), eq(ResourceAction.ACCESS)))
         .thenReturn(true);
      when(dataSourceService.checkPermission(eq("MyDatasource"), eq(ResourceAction.READ), eq(agent)))
         .thenReturn(false);

      WorksheetAgentController ctrl = securityController(mock(WorksheetEditService.class),
         dataSourceService, securityEngine, metadataApiService,
         mock(XRepository.class), mock(QueryManagerService.class));

      EditRequest req = addBoundTableRequest("dbo.orders", "MyDatasource");

      PairingException ex = assertThrows(PairingException.class,
         () -> ctrl.edit("TOK-BT3", req, agent));
      assertTrue(ex.getMessage().contains("READ permission"));

      // The READ denial must short-circuit before any JDBC metadata probe.
      verifyNoInteractions(metadataApiService);
   }

   // ---------------------------------------------------------------------------
   // editSqlQuery — FREE_FORM_SQL / ACCESS permission gate
   // ---------------------------------------------------------------------------

   @Test
   void editSqlQueryDeniedThrowsSecurityException() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      WorksheetEditService editSvc = mock(WorksheetEditService.class);
      SecurityEngine securityEngine = mock(SecurityEngine.class);

      when(securityEngine.checkPermission(eq(agent), eq(ResourceType.FREE_FORM_SQL),
                                          eq("*"), eq(ResourceAction.ACCESS)))
         .thenReturn(false);

      WorksheetAgentController ctrl = securityController(editSvc,
         mock(DataSourceService.class), securityEngine, mock(MetadataApiService.class),
         mock(XRepository.class), mock(QueryManagerService.class));

      EditRequest req = editSqlQueryRequest("SqlTable1", "SELECT * FROM foo");

      assertThrows(SecurityException.class, () -> ctrl.edit("TOK-ES", req, agent));

      verify(securityEngine).checkPermission(eq(agent), eq(ResourceType.FREE_FORM_SQL),
                                             eq("*"), eq(ResourceAction.ACCESS));
      verifyNoInteractions(editSvc);
   }

   @Test
   void editSqlQueryGrantedPassesPermissionGate() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      WorksheetEditService editSvc = mock(WorksheetEditService.class);
      SecurityEngine securityEngine = mock(SecurityEngine.class);

      when(securityEngine.checkPermission(eq(agent), eq(ResourceType.FREE_FORM_SQL),
                                          eq("*"), eq(ResourceAction.ACCESS)))
         .thenReturn(true);

      WorksheetAgentController ctrl = securityController(editSvc,
         mock(DataSourceService.class), securityEngine, mock(MetadataApiService.class),
         mock(XRepository.class), mock(QueryManagerService.class));

      EditRequest req = editSqlQueryRequest("SqlTable1", "SELECT * FROM foo");

      // editSvc is a plain mock: applyOnRuntime() is never actually executed, so the call
      // completes without exception — proving the permission check did not block it.
      ctrl.edit("TOK-ES2", req, agent);

      verify(securityEngine).checkPermission(eq(agent), eq(ResourceType.FREE_FORM_SQL),
                                             eq("*"), eq(ResourceAction.ACCESS));
      verify(editSvc).applyOnRuntime(eq("TOK-ES2"), eq(agent), any());
   }

   @Test
   void editSqlQueryDeniedByDatasourceReadThrowsPairingException() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      // Real runtime worksheet holding a SQL-bound table whose query is bound to datasource "myds".
      // The datasource/query are mocked: constructing a real JDBCDataSource pulls a
      // CredentialService bean the lightweight test context does not provide, and the READ-denial
      // path throws before the query's SQL definition is ever touched.
      Worksheet ws = new Worksheet();
      SQLBoundTableAssembly sqlt = new SQLBoundTableAssembly(ws, "SqlTable1");
      JDBCDataSource ds = mock(JDBCDataSource.class);
      when(ds.getFullName()).thenReturn("myds");
      JDBCQuery q = mock(JDBCQuery.class);
      when(q.getDataSource()).thenReturn(ds);
      ((SQLBoundTableAssemblyInfo) sqlt.getInfo()).setQuery(q);
      ws.addAssembly(sqlt);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      when(sessions.resolve(eq("TOK-ES3"), any())).thenReturn(session("TOK-ES3"));
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      WorksheetEditService editSvc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class));

      SecurityEngine securityEngine = mock(SecurityEngine.class);
      // Free-Form SQL right is granted, so we get past the action gate and into the lambda.
      when(securityEngine.checkPermission(eq(agent), eq(ResourceType.FREE_FORM_SQL),
                                          eq("*"), eq(ResourceAction.ACCESS)))
         .thenReturn(true);
      // ...but READ on the assembly's bound datasource is denied.
      DataSourceService dataSourceService = mock(DataSourceService.class);
      when(dataSourceService.checkPermission(eq("myds"), eq(ResourceAction.READ), eq(agent)))
         .thenReturn(false);

      WorksheetAgentController ctrl = securityController(editSvc, dataSourceService, securityEngine,
         mock(MetadataApiService.class), mock(XRepository.class), mock(QueryManagerService.class));

      EditRequest req = editSqlQueryRequest("SqlTable1", "SELECT * FROM foo");

      PairingException ex = assertThrows(PairingException.class,
         () -> ctrl.edit("TOK-ES3", req, agent));
      assertTrue(ex.getMessage().contains("READ permission"),
                 "error should name the denied datasource READ, got: " + ex.getMessage());

      verify(dataSourceService).checkPermission(eq("myds"), eq(ResourceAction.READ), eq(agent));
   }

   // ---------------------------------------------------------------------------
   // addSqlQuery — FREE_FORM_SQL / ACCESS + datasource READ permission gates
   // ---------------------------------------------------------------------------

   @Test
   void addSqlQueryDeniedByFreeFormSqlThrowsSecurityException() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      SecurityEngine securityEngine = mock(SecurityEngine.class);
      DataSourceService dataSourceService = mock(DataSourceService.class);
      XRepository xrepository = mock(XRepository.class);

      when(securityEngine.checkPermission(eq(agent), eq(ResourceType.FREE_FORM_SQL),
                                          eq("*"), eq(ResourceAction.ACCESS)))
         .thenReturn(false);

      WorksheetAgentController ctrl = securityController(mock(WorksheetEditService.class),
         dataSourceService, securityEngine, mock(MetadataApiService.class),
         xrepository, mock(QueryManagerService.class));

      WorksheetAgentController.SqlQueryRequest body =
         new WorksheetAgentController.SqlQueryRequest("MyDatasource", "SELECT 1", null);

      assertThrows(SecurityException.class, () -> ctrl.addSqlQuery("TOK-SQ", body, agent));

      verify(securityEngine).checkPermission(eq(agent), eq(ResourceType.FREE_FORM_SQL),
                                             eq("*"), eq(ResourceAction.ACCESS));
      verifyNoInteractions(dataSourceService);
      verifyNoInteractions(xrepository);
   }

   @Test
   void addSqlQueryDeniedByDatasourceReadThrowsPairingException() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      SecurityEngine securityEngine = mock(SecurityEngine.class);
      DataSourceService dataSourceService = mock(DataSourceService.class);
      XRepository xrepository = mock(XRepository.class);

      when(securityEngine.checkPermission(eq(agent), eq(ResourceType.FREE_FORM_SQL),
                                          eq("*"), eq(ResourceAction.ACCESS)))
         .thenReturn(true);
      when(dataSourceService.checkPermission(eq("MyDatasource"), eq(ResourceAction.READ), eq(agent)))
         .thenReturn(false);

      WorksheetAgentController ctrl = securityController(mock(WorksheetEditService.class),
         dataSourceService, securityEngine, mock(MetadataApiService.class),
         xrepository, mock(QueryManagerService.class));

      WorksheetAgentController.SqlQueryRequest body =
         new WorksheetAgentController.SqlQueryRequest("MyDatasource", "SELECT 1", null);

      PairingException ex = assertThrows(PairingException.class,
         () -> ctrl.addSqlQuery("TOK-SQ2", body, agent));
      assertTrue(ex.getMessage().contains("READ permission"));

      verify(securityEngine).checkPermission(eq(agent), eq(ResourceType.FREE_FORM_SQL),
                                             eq("*"), eq(ResourceAction.ACCESS));
      verify(dataSourceService).checkPermission(eq("MyDatasource"), eq(ResourceAction.READ), eq(agent));
      verifyNoInteractions(xrepository);
   }

   @Test
   void addSqlQueryGrantedPassesBothPermissionGates() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      SecurityEngine securityEngine = mock(SecurityEngine.class);
      DataSourceService dataSourceService = mock(DataSourceService.class);
      XRepository xrepository = mock(XRepository.class);

      when(securityEngine.checkPermission(eq(agent), eq(ResourceType.FREE_FORM_SQL),
                                          eq("*"), eq(ResourceAction.ACCESS)))
         .thenReturn(true);
      when(dataSourceService.checkPermission(eq("MyDatasource"), eq(ResourceAction.READ), eq(agent)))
         .thenReturn(true);

      WorksheetAgentController ctrl = securityController(mock(WorksheetEditService.class),
         dataSourceService, securityEngine, mock(MetadataApiService.class),
         xrepository, mock(QueryManagerService.class));

      WorksheetAgentController.SqlQueryRequest body =
         new WorksheetAgentController.SqlQueryRequest("MyDatasource", "SELECT 1", null);

      // Both gates pass; fails further downstream because xrepository (unstubbed mock)
      // returns null for getDataSource(), which is expected — we only assert that both
      // permission checks let execution proceed past them.
      PairingException ex = assertThrows(PairingException.class,
         () -> ctrl.addSqlQuery("TOK-SQ3", body, agent));
      assertTrue(ex.getMessage().contains("Datasource not found"));

      verify(securityEngine).checkPermission(eq(agent), eq(ResourceType.FREE_FORM_SQL),
                                             eq("*"), eq(ResourceAction.ACCESS));
      verify(dataSourceService).checkPermission(eq("MyDatasource"), eq(ResourceAction.READ), eq(agent));
   }
}
