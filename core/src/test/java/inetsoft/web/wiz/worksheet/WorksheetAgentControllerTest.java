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
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.sree.security.SecurityException;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.SQLBoundTableAssemblyInfo;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.erm.XAttribute;
import inetsoft.uql.erm.XDataModel;
import inetsoft.uql.erm.XEntity;
import inetsoft.uql.erm.XLogicalModel;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.uql.jdbc.JDBCQuery;
import inetsoft.uql.schema.UserVariable;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.tabular.RestParameter;
import inetsoft.uql.tabular.TabularDataSource;
import inetsoft.uql.tabular.TabularUtil;
import inetsoft.web.composer.ws.LayoutGraphService;
import inetsoft.web.portal.controller.database.DataSourceService;
import inetsoft.web.portal.controller.database.QueryManagerService;
import inetsoft.web.wiz.pairing.*;
import inetsoft.web.wiz.service.FakeNamedConnectorQuery;
import inetsoft.web.wiz.service.MetadataApiService;
import inetsoft.web.wiz.worksheet.model.WorksheetModel;
import inetsoft.web.wiz.worksheet.model.WorksheetPropertiesModel;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
                             JoinSession.ConnectionMode.PAIRED, null, null, null);
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
         null, null,
         null, null,
         null, null, null, null, null, null, null
      );
   }

   /**
    * Builds an {@code add_table} EditRequest that routes to {@code addTabularTable()} -- either
    * the named-connector shape ({@code endpoint} + optional {@code lookup}) or the generic/custom
    * shape ({@code suffix} + optional {@code customLookups}), or a deliberately-contradictory
    * combination of these with {@code logicalModel}/{@code schema}/{@code catalog} for the
    * dispatch-guard tests.
    */
   private static EditRequest addTabularTableRequest(
      String table, String datasource, String logicalModel, String schema, String catalog,
      String endpoint, Map<String, String> parameters, List<String> lookup,
      Boolean lookupExpandArrays, Boolean lookupTopLevelOnly, String suffix,
      List<WorksheetMutationSupport.CustomLookupSpec> customLookups)
   {
      return new EditRequest(
         "add_table", table, null, null, null, null, null, null, null, null,
         null, null, null, false, null, null, null, null, null, null,
         null, null, null, null, null, null, null, null, datasource, schema,
         catalog, logicalModel, null, null, null, null, null, null, null, null,
         null, null, null, null, null, null, null, null, null, null,
         null, null, null, null, null, endpoint, parameters, lookup, lookupExpandArrays,
         lookupTopLevelOnly, suffix, customLookups
      );
   }

   /**
    * Builds a {@code delete_table} EditRequest -- a plainly destructive op that routes through
    * {@code editService}, so a scope guard that failed to fire would show up as a real write
    * attempt rather than an early return.
    */
   private static EditRequest deleteTableRequest(String table) {
      return new EditRequest(
         "delete_table", table, null, null, null, null, null, null, null, null,
         null, null, null, false, null, null, null, null, null, null, null, null, null, null,
         null, null, null, null, null, null, null, null, null, null, null, null, null,
         null, null, null, null, null, null, null, null, null, null, null, null, null, null,
         null, null,
         null, null,
         null, null, null, null, null, null, null
      );
   }

   /** Builds an {@code insert_column} EditRequest that routes to insertColumn(). */
   private static EditRequest insertColumnRequest(String table) {
      return new EditRequest(
         "insert_column", table, null, null, null, null, null, null, null, null,
         null, null, null, false, null, null, null, null, null, null, null, null, null, null,
         null, null, null, null, null, null, null, null, null, null, null, null, null,
         null, null, null, null, null, null, null, null, null, null, null, null, null, null,
         null, null,
         null, null,
         null, null, null, null, null, null, null
      );
   }

   /** Builds an {@code edit_sql_query} EditRequest that routes to editSqlQuery(). */
   private static EditRequest editSqlQueryRequest(String table, String expression) {
      return new EditRequest(
         "edit_sql_query", table, null, null, null, null, null, null, null, null,
         null, null, expression, false, null, null, null, null, null, null, null, null, null, null,
         null, null, null, null, null, null, null, null, null, null, null, null, null,
         null, null, null, null, null, null, null, null, null, null, null, null, null, null,
         null, null,
         null, null,
         null, null, null, null, null, null, null
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

   /**
    * The join response must carry the session's editorContext through -- otherwise a dropped
    * context doesn't surface as an error, it reads as an ordinary whole-sheet session,
    * indistinguishable from a legitimate toolbar mint, on the exact route an agent reads.
    */
   @Test
   void joinReturnsTheSessionsEditorContext() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      EditorContext ctx = new EditorContext("worksheetExpression", "T", "Calc1", null);
      JoinSession s = new JoinSession("TOK-EC", "Worksheet/ws-1", "alice~;~host-org",
         SheetType.WORKSHEET, 0L, Long.MAX_VALUE, JoinSession.ConnectionMode.PAIRED,
         null, null, ctx);

      SheetJoinService joinSvc = mock(SheetJoinService.class);
      when(joinSvc.join(eq("CODE"), eq(agent))).thenReturn(s);

      WorksheetAgentController ctrl = controller(featureOn(), joinSvc,
         mock(SheetSessionService.class), mock(WorksheetReadService.class),
         mock(WorksheetEditService.class), mock(WorksheetService.class));

      WorksheetAgentController.JoinResponse resp = ctrl.join(new WorksheetAgentController.JoinRequest("CODE"), agent);

      assertEquals(ctx, resp.editorContext());
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
   // properties — read
   // ---------------------------------------------------------------------------

   /**
    * The read counterpart of the properties POST. Without it an agent could set a worksheet's
    * alias or description but never read it back, and /model carries neither field.
    */
   @Test
   void getPropertiesReturnsTheSheetsOwnProperties() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      Worksheet ws = new Worksheet();
      ws.getWorksheetInfo().setAlias("Quarterly revenue");
      ws.getWorksheetInfo().setDescription("Set by the agent");

      AssetEntry entry = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET, "Folder/ws-1", null);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      when(rws.getEntry()).thenReturn(entry);

      WorksheetEditService editSvc = mock(WorksheetEditService.class);
      when(editSvc.resolve(eq("TOK"), eq(agent))).thenReturn(rws);

      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         new WorksheetReadService(), editSvc, mock(WorksheetService.class));

      WorksheetPropertiesModel props = ctrl.getProperties("TOK", agent);

      assertEquals("ws-1", props.name());
      assertEquals("Quarterly revenue", props.alias());
      assertEquals("Set by the agent", props.description());
      assertTrue(props.dataSource(),
         "AssetEntry.isReportDataSource() reads true unless the property is explicitly \"false\", "
            + "so an untouched entry is a data source -- the same value the Composer dialog shows");
   }

   /**
    * Pinned to the exact 403 {@code requireEnabled()} throws, the way {@code joinRejectsFlagOff}
    * is. A bare {@code assertThrows(Exception.class, ...)} would not test the flag at all: with
    * both collaborators mocked, dropping {@code requireEnabled()} leaves the endpoint throwing
    * anyway -- {@code requireWholeSheetSession} passes an unresolvable token straight through by
    * design, so {@code editService.resolve} returns {@code null} and {@code readProperties}
    * NPEs. Asserting the status is what tells those two outcomes apart.
    */
   @Test
   void getPropertiesIsRefusedWhenTheFeatureIsOff() {
      Principal agent = TestPrincipals.user("alice", "host-org");

      WorksheetAgentController ctrl = controller(featureOff(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         new WorksheetReadService(), mock(WorksheetEditService.class),
         mock(WorksheetService.class));

      ResponseStatusException ex = assertThrows(ResponseStatusException.class,
         () -> ctrl.getProperties("TOK", agent));
      assertEquals(403, ex.getStatusCode().value());
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
         null, null, null,
         null, null,
         null, null, null, null, null, null, null);

      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), editSvc, mock(WorksheetService.class));

      ctrl.edit("TOK-E", req, agent);

      assertNull(t.getColumnSelection(false).getAttribute("x"),
                 "column 'x' should have been removed");
      assertNotNull(t.getColumnSelection(false).getAttribute("y"),
                    "column 'y' should still be present");
   }

   /**
    * Confirms {@code crosstab} on a {@code set_group_aggregate} {@link EditRequest} reaches
    * {@link AggregateInfo#isCrosstab} through the controller's dispatch switch, not just at the
    * {@code WorksheetEditService.Editor} layer already covered by
    * {@code WorksheetEditServiceMutatorsTest#setGroupAggregateCrosstabTogglesAggregateInfo}.
    */
   @Test
   void editDispatchesSetGroupAggregateWithCrosstab() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "cust", "store", "amount");
      ws.addAssembly(t);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      JoinSession s = session("TOK-XTAB");
      when(sessions.resolve(eq("TOK-XTAB"), any())).thenReturn(s);
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      WorksheetEditService editSvc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class));

      List<WorksheetMutationSupport.GroupSpec> groups = List.of(
         new WorksheetMutationSupport.GroupSpec("cust", null),
         new WorksheetMutationSupport.GroupSpec("store", null));
      List<WorksheetMutationSupport.AggregateSpec> aggregates = List.of(
         new WorksheetMutationSupport.AggregateSpec("amount", "SUM", null));

      EditRequest req = new EditRequest(
         "set_group_aggregate", // op
         "T",                   // table
         null,                  // column
         null,                  // name
         null,                  // type
         null,                  // newName
         null,                  // field
         null,                  // operation
         null,                  // values
         null,                  // direction
         groups,                // groups
         aggregates,            // aggregates
         null,                  // expression
         false,                 // sql
         null,                  // leftTable
         null,                  // leftKey
         null,                  // rightTable
         null,                  // rightKey
         null,                  // joinType
         null,                  // visible
         null,                  // tables
         null,                  // source
         null,                  // concatType
         null,                  // conditions
         null,                  // ranking
         null,                  // headerColumns
         null,                  // dateOption
         null,                  // boundaries
         null,                  // datasource
         null,                  // schema
         null,                  // catalog
         null,                  // logicalModel
         null,                  // leftKeys
         null,                  // rightKeys
         null,                  // row
         null,                  // col
         null,                  // value
         null,                  // index
         null,                  // alias
         null,                  // description
         null,                  // maxRows
         null,                  // distinct
         null,                  // columnOrder
         null,                  // groupMappings
         null,                  // groupOthers
         null,                  // variableValues
         null,                  // x
         null,                  // y
         null,                  // label
         null,                  // defaultValue
         null,                  // mode
         null,                  // insert
         null,                  // subtables
         null,                  // sourceTable
         null,                  // attribute
         null,                  // endpoint
         null,                  // parameters
         null,                  // lookup
         null,                  // lookupExpandArrays
         null,                  // lookupTopLevelOnly
         null,                  // suffix
         null,                  // customLookups
         true,                  // crosstab
         null                   // labels
      );

      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), editSvc, mock(WorksheetService.class));

      ctrl.edit("TOK-XTAB", req, agent);

      assertTrue(t.getAggregateInfo().isCrosstab(),
                 "crosstab on the EditRequest should reach AggregateInfo through the controller");
   }

   /**
    * The snapshot guard for {@code insert_column}, pinned here rather than in
    * {@code WorksheetEditServiceMutatorsTest} because this op is the one that does not live in
    * the {@code Editor}: it manipulates {@link inetsoft.uql.util.XEmbeddedTable} directly from
    * the controller. That is exactly why it was missed when the sibling column ops were first
    * swept, so it is the guard most likely to be dropped again -- and the only one no test
    * covered.
    */
   @Test
   void editInsertColumnRefusesSnapshotEmbeddedTable() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      Worksheet ws = new Worksheet();
      SnapshotEmbeddedTableAssembly t =
         TestWorksheets.snapshotTableWithColumns(ws, "S", "a", "b");
      ws.addAssembly(t);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      when(sessions.resolve(eq("TOK-IC"), any())).thenReturn(session("TOK-IC"));
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      WorksheetEditService editSvc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class));

      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), editSvc, mock(WorksheetService.class));

      PairingException ex = assertThrows(PairingException.class,
         () -> ctrl.edit("TOK-IC", insertColumnRequest("S"), agent));

      assertTrue(ex.getMessage().startsWith("insert_column"), ex.getMessage());
      assertTrue(ex.getMessage().contains("EMBEDDED_SNAPSHOT"), ex.getMessage());
      assertTrue(ex.getMessage().contains("add_expression_column"),
         "the refusal must name the column kind that does work here: " + ex.getMessage());
      assertEquals(2, t.getColumnSelection(false).getAttributeCount(),
         "nothing may be added to the selection when the data cannot follow");
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
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class));

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
         null, null, null,
         null, null,
         null, null, null, null, null, null, null);

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
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class));

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
         null, null, null,
         null, null,
         null, null, null, null, null, null, null);

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
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class));

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
         null, null, null,
         null, null,
         null, null, null, null, null, null, null);

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
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class));

      EditRequest req = new EditRequest("edit_expression", "T", null,
         null, null, null, null, null, null, null, null, null, null, false,
         null, null, null, null, null,
         null, null, null, null, null, null, null, null, null, null, null, null, null,
         null, null, null, null, null, null, null, null, null, null,
         null, null, null, null,
         null, null,
         null, null,
         null, null, null,
         null, null,
         null, null, null, null, null, null, null);

      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), editSvc, mock(WorksheetService.class));

      PairingException ex = assertThrows(PairingException.class,
         () -> ctrl.edit("TOK-EE", req, agent));
      assertTrue(ex.getMessage().contains("name"));
   }

   /** Builds an {@code add_variable} EditRequest that routes to addVariableFromEdit(). */
   private static EditRequest addVariableRequest(String name, String type) {
      return new EditRequest(
         "add_variable", null, null, name, type, null, null, null, null, null, null, null, null, false,
         null, null, null, null, null,
         null, null, null, null, null, null, null, null, null, null, null, null, null,
         null, null, null, null, null, null, null, null, null, null,
         null, null, null, null,
         null, null,
         null, null,
         null, null, null,
         null, null,
         null, null, null, null, null, null, null
      );
   }

   @Test
   void editAddVariableCreatesVariableWhenNameIsNew() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      Worksheet ws = new Worksheet();

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      when(sessions.resolve(eq("TOK-AV"), any())).thenReturn(session("TOK-AV"));
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      WorksheetEditService editSvc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class));

      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), editSvc, mock(WorksheetService.class));

      ctrl.edit("TOK-AV", addVariableRequest("minTotal", "double"), agent);

      Assembly a = ws.getAssembly("minTotal");
      assertTrue(a instanceof VariableAssembly, "a variable assembly should have been created");
   }

   @Test
   void editRejectsAddVariableWithDuplicateName() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      // Regression for Bug #75991: a second add_variable call with a name that
      // already exists used to silently replace the existing variable assembly
      // (including its type and default value) via Worksheet.addAssembly's
      // generic same-name replace semantics. add_variable must fail loud instead
      // and point the caller at edit_variable, which is the explicit modify path.
      Worksheet ws = new Worksheet();
      AssetVariable existingVar = new AssetVariable("minTotal");
      existingVar.setTypeNode(XSchema.createPrimitiveType(XSchema.DOUBLE));
      DefaultVariableAssembly existing = new DefaultVariableAssembly(ws, "minTotal");
      existing.setVariable(existingVar);
      ws.addAssembly(existing);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      when(sessions.resolve(eq("TOK-AVD"), any())).thenReturn(session("TOK-AVD"));
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      WorksheetEditService editSvc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class));

      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), editSvc, mock(WorksheetService.class));

      PairingException ex = assertThrows(PairingException.class,
         () -> ctrl.edit("TOK-AVD", addVariableRequest("minTotal", "date"), agent));
      assertTrue(ex.getMessage().contains("already exists"));
      assertTrue(ex.getMessage().contains("edit_variable"));

      Assembly a = ws.getAssembly("minTotal");
      assertSame(existing, a, "the original variable assembly must not be replaced");
      assertEquals(XSchema.DOUBLE, ((VariableAssembly) a).getVariable().getTypeNode().getType(),
                   "the original variable's type must be unchanged");
   }

   @Test
   void editAddVariableWiresChoicesThrough() throws Exception {
      // Regression for Bug #76328: add_variable/edit_variable had no way to populate
      // UserVariable.setValues()/setChoices()/setDisplayStyle() (the Composer's own Variable
      // dialog "Values" picker). Verifies the edit-dispatch add_variable path wires 'choices'
      // all the way through to the created variable.
      Principal agent = TestPrincipals.user("alice", "host-org");
      Worksheet ws = new Worksheet();

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      when(sessions.resolve(eq("TOK-AVC"), any())).thenReturn(session("TOK-AVC"));
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      WorksheetEditService editSvc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class));

      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), editSvc, mock(WorksheetService.class));

      WorksheetMutationSupport.VariableChoicesSpec choices =
         new WorksheetMutationSupport.VariableChoicesSpec(
            List.of("1", "5", "10"), List.of("One", "Five", "Ten"), null, null, null, "list");

      EditRequest req = new EditRequest(
         "add_variable",              // op
         null,                        // table
         null,                        // column
         "topN",                      // name
         "integer",                   // type
         null,                        // newName
         null,                        // field
         null,                        // operation
         null,                        // values
         null,                        // direction
         null,                        // groups
         null,                        // aggregates
         null,                        // expression
         false,                       // sql
         null, null, null, null, null,  // leftTable, leftKey, rightTable, rightKey, joinType
         null, null, null, null,         // visible, tables, source, concatType
         null, null, null, null, null,   // conditions, ranking, headerColumns, dateOption, boundaries
         null, null, null, null,         // datasource, schema, catalog, logicalModel
         null, null,                     // leftKeys, rightKeys
         null, null, null, null,         // row, col, value, index
         null, null, null, null,         // alias, description, maxRows, distinct
         null, null, null, null,         // columnOrder, groupMappings, groupOthers, variableValues
         null, null, null, null,         // x, y, label, defaultValue
         null, null, null,               // mode, insert, subtables
         null, null,                     // sourceTable, attribute
         null, null, null, null, null, null, null,  // endpoint, parameters, lookup,
                                                      // lookupExpandArrays, lookupTopLevelOnly,
                                                      // suffix, customLookups
         null,                        // crosstab
         null,                        // labels
         choices                      // choices
      );

      ctrl.edit("TOK-AVC", req, agent);

      Assembly a = ws.getAssembly("topN");
      assertTrue(a instanceof VariableAssembly);
      AssetVariable var = ((VariableAssembly) a).getVariable();
      assertArrayEquals(new Object[] {"One", "Five", "Ten"}, var.getChoices());
      assertArrayEquals(new Object[] {1, 5, 10}, var.getValues(),
         "values must be typed to the variable's data type (Integer), not raw strings");
      assertTrue(var.isMultipleSelection());
      assertEquals(UserVariable.LIST, var.getDisplayStyle());
   }

   // ---------------------------------------------------------------------------
   // edit — rename_variable / delete_variable dispatch (Bug #75994)
   // ---------------------------------------------------------------------------

   /** Builds a {@code rename_variable} EditRequest that routes to renameVariable(). */
   private static EditRequest renameVariableRequest(String name, String newName) {
      return new EditRequest(
         "rename_variable", null, null, name, null, newName, null, null, null, null,
         null, null, null, false, null, null, null, null, null, null, null, null, null, null,
         null, null, null, null, null, null, null, null, null, null, null, null, null,
         null, null, null, null, null, null, null, null, null, null, null, null, null, null,
         null, null,
         null, null,
         null, null, null, null, null, null, null
      );
   }

   /** Builds a {@code delete_variable} EditRequest that routes to deleteVariable(). */
   private static EditRequest deleteVariableRequest(String name) {
      return new EditRequest(
         "delete_variable", null, null, name, null, null, null, null, null, null,
         null, null, null, false, null, null, null, null, null, null, null, null, null, null,
         null, null, null, null, null, null, null, null, null, null, null, null, null,
         null, null, null, null, null, null, null, null, null, null, null, null, null, null,
         null, null,
         null, null,
         null, null, null, null, null, null, null
      );
   }

   private static DefaultVariableAssembly addVariable(Worksheet ws, String name) {
      AssetVariable var = new AssetVariable(name);
      DefaultVariableAssembly assembly = new DefaultVariableAssembly(ws, name);
      assembly.setVariable(var);
      ws.addAssembly(assembly);
      return assembly;
   }

   @Test
   void editDispatchesRenameVariable() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      Worksheet ws = new Worksheet();
      addVariable(ws, "minTotal");

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      when(sessions.resolve(eq("TOK-RV"), any())).thenReturn(session("TOK-RV"));
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      WorksheetEditService editSvc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class));

      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), editSvc, mock(WorksheetService.class));

      ctrl.edit("TOK-RV", renameVariableRequest("minTotal", "employee"), agent);

      assertNull(ws.getAssembly("minTotal"), "old variable name should no longer exist");
      assertTrue(ws.getAssembly("employee") instanceof DefaultVariableAssembly,
                 "renamed variable should exist under the new name");
   }

   @Test
   void editRenameVariableThrowsWhenAssemblyNotFound() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      Worksheet ws = new Worksheet();

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      when(sessions.resolve(eq("TOK-RV2"), any())).thenReturn(session("TOK-RV2"));
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      WorksheetEditService editSvc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class));

      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), editSvc, mock(WorksheetService.class));

      PairingException ex = assertThrows(PairingException.class,
         () -> ctrl.edit("TOK-RV2", renameVariableRequest("noSuchVar", "employee"), agent));
      assertTrue(ex.getMessage().contains("noSuchVar"));
   }

   @Test
   void editDispatchesDeleteVariable() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      Worksheet ws = new Worksheet();
      addVariable(ws, "minTotal");

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      when(sessions.resolve(eq("TOK-DV"), any())).thenReturn(session("TOK-DV"));
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      WorksheetEditService editSvc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class));

      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), editSvc, mock(WorksheetService.class));

      ctrl.edit("TOK-DV", deleteVariableRequest("minTotal"), agent);

      assertNull(ws.getAssembly("minTotal"), "variable should have been removed");
   }

   @Test
   void editDeleteVariableThrowsWhenAssemblyNotFound() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "x", "y");
      ws.addAssembly(t);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      when(sessions.resolve(eq("TOK-DV2"), any())).thenReturn(session("TOK-DV2"));
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      WorksheetEditService editSvc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class));

      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), editSvc, mock(WorksheetService.class));

      // "T" exists but is a table, not a variable — delete_variable must reject it
      // rather than silently deleting a same-named table assembly.
      PairingException ex = assertThrows(PairingException.class,
         () -> ctrl.edit("TOK-DV2", deleteVariableRequest("T"), agent));
      assertTrue(ex.getMessage().contains("T"));
      assertNotNull(ws.getAssembly("T"), "table should not have been deleted");
   }

   // ---------------------------------------------------------------------------
   // importCsv / importExcel
   // ---------------------------------------------------------------------------

   private static WorksheetAgentController importController(String token, RuntimeWorksheet rws) throws Exception {
      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      when(sessions.resolve(eq(token), any())).thenReturn(session(token));
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      WorksheetEditService editSvc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class));

      return controller(featureOn(), mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), editSvc, mock(WorksheetService.class));
   }

   @Test
   void importCsvCreatesEmbeddedTable() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      Worksheet ws = new Worksheet();
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      when(rws.getAssetQuerySandbox()).thenReturn(mock(AssetQuerySandbox.class));

      WorksheetAgentController ctrl = importController("TOK-CSV", rws);

      WorksheetAgentController.ImportCsvResponse resp = ctrl.importCsv(
         "TOK-CSV", new WorksheetAgentController.ImportCsvRequest("Imported", "a,b\n1,x\n2,y"), agent);

      assertEquals("Imported", resp.tableName());
      assertEquals(2, resp.rows());
      assertEquals(2, resp.columns());
      assertNotNull(ws.getAssembly("Imported"));
   }

   /**
    * The import settings the Composer's Import Data File dialog exposes, now reachable through the
    * agent too. Before this, the CSV route had its own hand-rolled parser that could honour none of
    * them -- the origin of a cluster of L2 findings. These assert the settings actually reach
    * {@code CSVLoader}, the loader the dialog itself uses.
    */
   private static WorksheetAgentController.ImportCsvRequest csvRequest(
      String name, String csv, String encoding, String delimiter, Boolean tab, Boolean detectType,
      Boolean firstRow, Boolean removeQuotes, Boolean unpivot, Integer headerCols)
   {
      return new WorksheetAgentController.ImportCsvRequest(
         name, csv, encoding, delimiter, tab, detectType, firstRow, removeQuotes, unpivot,
         headerCols, null);
   }

   private static EmbeddedTableAssembly importedTable(Worksheet ws, String name) {
      Assembly a = ws.getAssembly(name);
      assertNotNull(a, "expected an imported table named " + name);
      assertInstanceOf(EmbeddedTableAssembly.class, a);
      return (EmbeddedTableAssembly) a;
   }

   private static WorksheetAgentController importCtrl(Worksheet ws, String token) throws Exception {
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      when(rws.getAssetQuerySandbox()).thenReturn(mock(AssetQuerySandbox.class));
      return importController(token, rws);
   }

   /**
    * The guard the owner asked for explicitly: an agent-imported table must stay a plain
    * {@link EmbeddedTableAssembly}. The dialog builds a {@link SnapshotEmbeddedTableAssembly} from
    * the same loader, and re-importing through this route is the documented way to get an editable
    * copy of snapshot data -- so copying the dialog's choice here would quietly delete the only
    * workaround L2 Finding 5 has.
    */
   @Test
   void importCsvStaysAPlainEmbeddedTableRatherThanASnapshot() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetAgentController ctrl = importCtrl(ws, "TOK-PLAIN");

      ctrl.importCsv("TOK-PLAIN",
         new WorksheetAgentController.ImportCsvRequest("Plain", "a,b\n1,x"),
         TestPrincipals.user("alice", "host-org"));

      EmbeddedTableAssembly t = importedTable(ws, "Plain");
      assertFalse(t instanceof SnapshotEmbeddedTableAssembly,
         "an agent import must remain editable; a snapshot refuses edit_cell/insert_row/delete_row");
   }

   /**
    * Bug #76080: the agent had no way to replace an existing table's data, only create new
    * ones. Replacing must reuse the same assembly object rather than swap in a new one under
    * the same name -- {@link Worksheet#addAssembly} would do that silently -- so anything built
    * on top of the table (a join, a filter, a chart binding) keeps pointing at something real.
    */
   @Test
   void importCsvReplaceTableOverwritesAnExistingEmbeddedTableInPlace() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetAgentController ctrl = importCtrl(ws, "TOK-REPLACE");
      Principal agent = TestPrincipals.user("alice", "host-org");

      ctrl.importCsv("TOK-REPLACE",
         new WorksheetAgentController.ImportCsvRequest("Query1", "a,b\n1,x\n2,y"), agent);
      EmbeddedTableAssembly before = importedTable(ws, "Query1");

      WorksheetAgentController.ImportCsvResponse resp = ctrl.importCsv("TOK-REPLACE",
         new WorksheetAgentController.ImportCsvRequest(
            null, "a,b\n9,z", null, null, null, null, null, null, null, null, "Query1"),
         agent);

      assertEquals("Query1", resp.tableName());
      assertEquals(1, resp.rows());
      assertEquals(1, ws.getAssemblies().length, "replacing must not leave a second table behind");

      EmbeddedTableAssembly after = importedTable(ws, "Query1");
      assertSame(before, after,
         "a replace must keep the same assembly instance, not swap in a new one under the same name");
      assertEquals("9", String.valueOf(after.getEmbeddedData().getObject(1, 0)),
         "the new file's data must have landed on the table");
   }

   @Test
   void importCsvReplaceTableWithDifferentColumnsUpdatesTheColumnSelection() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetAgentController ctrl = importCtrl(ws, "TOK-REPLACE-SHAPE");
      Principal agent = TestPrincipals.user("alice", "host-org");

      ctrl.importCsv("TOK-REPLACE-SHAPE",
         new WorksheetAgentController.ImportCsvRequest("Query1", "a,b\n1,2"), agent);
      ctrl.importCsv("TOK-REPLACE-SHAPE",
         new WorksheetAgentController.ImportCsvRequest(
            null, "x,y,z\n1,2,3", null, null, null, null, null, null, null, null, "Query1"),
         agent);

      ColumnSelection cols = importedTable(ws, "Query1").getColumnSelection(false);
      assertEquals(3, cols.getAttributeCount());
      assertNotNull(cols.getAttribute("x"));
      assertNotNull(cols.getAttribute("z"));
   }

   @Test
   void importCsvReplaceTableFailsLoudWhenTheNamedTableDoesNotExist() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetAgentController ctrl = importCtrl(ws, "TOK-REPLACE-MISS");

      PairingException ex = assertThrows(PairingException.class,
         () -> ctrl.importCsv("TOK-REPLACE-MISS",
            new WorksheetAgentController.ImportCsvRequest(
               null, "a,b\n1,2", null, null, null, null, null, null, null, null, "NoSuchTable"),
            TestPrincipals.user("alice", "host-org")));

      assertTrue(ex.getMessage().contains("NoSuchTable"), ex.getMessage());
   }

   @Test
   void importCsvRejectsBothNameAndReplaceTable() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetAgentController ctrl = importCtrl(ws, "TOK-REPLACE-BOTH");

      ctrl.importCsv("TOK-REPLACE-BOTH",
         new WorksheetAgentController.ImportCsvRequest("Query1", "a,b\n1,2"),
         TestPrincipals.user("alice", "host-org"));

      ResponseStatusException ex = assertThrows(ResponseStatusException.class,
         () -> ctrl.importCsv("TOK-REPLACE-BOTH",
            new WorksheetAgentController.ImportCsvRequest(
               "Other", "a,b\n1,2", null, null, null, null, null, null, null, null, "Query1"),
            TestPrincipals.user("alice", "host-org")));

      assertEquals(400, ex.getStatusCode().value());
   }

   @Test
   void importCsvDetectTypeFalseMakesEveryColumnString() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetAgentController ctrl = importCtrl(ws, "TOK-DT");

      // The same file twice, differing only in detectType. "299.99" and "$499.99" mix numeric
      // sub-formats (plain vs. currency) that CSVLoader can't represent with the single cached
      // Format it picks during type detection -- #4689 (Bug #76203) demotes a column like this to
      // string rather than silently losing whichever values don't match that cached format, so
      // detectType=true and detectType=false converge on the same string result here.
      ctrl.importCsv("TOK-DT", csvRequest("Typed", "price\n299.99\n$499.99",
                                          null, null, null, true, null, null, null, null),
                     TestPrincipals.user("alice", "host-org"));
      ctrl.importCsv("TOK-DT", csvRequest("AsText", "price\n299.99\n$499.99",
                                          null, null, null, false, null, null, null, null),
                     TestPrincipals.user("alice", "host-org"));

      ColumnSelection typed = importedTable(ws, "Typed").getColumnSelection(false);
      ColumnSelection text = importedTable(ws, "AsText").getColumnSelection(false);

      assertEquals(XSchema.STRING, ((ColumnRef) text.getAttribute(0)).getDataType(),
         "detectType=false must leave the column as string");
      assertEquals(XSchema.STRING, ((ColumnRef) typed.getAttribute(0)).getDataType(),
         "detectType=true demotes a mixed numeric sub-format column (plain + currency) to string");
   }

   @Test
   void importCsvDetectTypeTrueDetectsANumericColumn() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetAgentController ctrl = importCtrl(ws, "TOK-DT2");

      // Unlike the mixed plain/currency case above, both values here share the same currency
      // sub-format, so CSVLoader's cached Format never changes between rows and the column is
      // free to be detected as numeric.
      ctrl.importCsv("TOK-DT2", csvRequest("Typed", "price\n$299.99\n$499.99",
                                           null, null, null, true, null, null, null, null),
                     TestPrincipals.user("alice", "host-org"));

      ColumnSelection typed = importedTable(ws, "Typed").getColumnSelection(false);

      assertNotEquals(XSchema.STRING, ((ColumnRef) typed.getAttribute(0)).getDataType(),
         "detectType=true must still detect a numeric column when every value shares one format");
   }

   @Test
   void importCsvFirstRowAsHeaderFalseGeneratesColumnNamesAndKeepsLineOne() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetAgentController ctrl = importCtrl(ws, "TOK-FR");

      WorksheetAgentController.ImportCsvResponse resp = ctrl.importCsv(
         "TOK-FR", csvRequest("NoHeader", "a,b\n1,x", null, null, null, null, false, null, null,
                              null),
         TestPrincipals.user("alice", "host-org"));

      assertEquals(2, resp.rows(), "line 1 counts as data when it is not the header");
      ColumnSelection cs = importedTable(ws, "NoHeader").getColumnSelection(false);
      assertNotNull(cs.getAttribute("col0"), "generated names replace the missing header");
      assertNotNull(cs.getAttribute("col1"));
   }

   @Test
   void importCsvHonoursAnAlternateDelimiter() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetAgentController ctrl = importCtrl(ws, "TOK-DELIM");

      WorksheetAgentController.ImportCsvResponse resp = ctrl.importCsv(
         "TOK-DELIM", csvRequest("Semi", "a;b;c\n1;2;3", null, ";", null, null, null, null, null,
                                 null),
         TestPrincipals.user("alice", "host-org"));

      assertEquals(3, resp.columns(), "a semicolon file is three columns, not one");
      assertNotNull(importedTable(ws, "Semi").getColumnSelection(false).getAttribute("a"));
   }

   @Test
   void importCsvHonoursTabAsTheDelimiter() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetAgentController ctrl = importCtrl(ws, "TOK-TAB");

      WorksheetAgentController.ImportCsvResponse resp = ctrl.importCsv(
         "TOK-TAB", csvRequest("Tabbed", "a\tb\n1\t2", null, null, true, null, null, null, null,
                               null),
         TestPrincipals.user("alice", "host-org"));

      assertEquals(2, resp.columns());
   }

   @Test
   void importCsvUnpivotReshapesCrosstabInput() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetAgentController ctrl = importCtrl(ws, "TOK-UP");

      // A crosstab: one identifier column plus two measure columns. Unpivoting with one header
      // column turns each measure cell into its own row.
      WorksheetAgentController.ImportCsvResponse resp = ctrl.importCsv(
         "TOK-UP", csvRequest("Unpivoted", "region,q1,q2\nEast,1,2\nWest,3,4",
                              null, null, null, null, null, null, true, 1),
         TestPrincipals.user("alice", "host-org"));

      assertEquals(3, resp.columns(),
         "unpivot yields the header column plus a name/value pair");
      assertEquals(4, resp.rows(), "two rows times two measures");
      importedTable(ws, "Unpivoted");
   }

   @Test
   void importCsvRejectsAnUnsupportedEncoding() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetAgentController ctrl = importCtrl(ws, "TOK-ENC");

      ResponseStatusException ex = assertThrows(ResponseStatusException.class,
         () -> ctrl.importCsvFile("TOK-ENC",
            new MockMultipartFile("file", "d.csv", "text/csv", "a,b\n1,2".getBytes()),
            "Bad", "NOT-A-CHARSET", null, null, null, null, null, null, null, null,
            TestPrincipals.user("alice", "host-org")));

      assertEquals(400, ex.getStatusCode().value());
      assertTrue(ex.getReason().contains("NOT-A-CHARSET"), ex.getReason());
   }

   /**
    * A name that is not merely unrecognized but syntactically illegal takes a different route out
    * of {@code Charset}: it throws {@code IllegalCharsetNameException} instead of answering false.
    * Unguarded that escapes as a 500; the caller made the same mistake either way, so it has to
    * land on the same 400 as {@code importCsvRejectsAnUnsupportedEncoding}.
    */
   @Test
   void importCsvRejectsAMalformedEncodingNameRatherThanThrowing() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetAgentController ctrl = importCtrl(ws, "TOK-BADENC");

      ResponseStatusException ex = assertThrows(ResponseStatusException.class,
         () -> ctrl.importCsvFile("TOK-BADENC",
            new MockMultipartFile("file", "d.csv", "text/csv", "a,b\n1,2".getBytes()),
            "Bad", "not a charset", null, null, null, null, null, null, null, null,
            TestPrincipals.user("alice", "host-org")));

      assertEquals(400, ex.getStatusCode().value());
      assertTrue(ex.getReason().contains("not a charset"), ex.getReason());
   }

   /**
    * The JSON route cannot honour an encoding at all -- its text was decoded before the request
    * existed. Refusing says so; silently ignoring it would leave a caller with mojibake and no
    * indication of why their setting did nothing.
    */
   @Test
   void importCsvRejectsANonUtf8EncodingOnTheJsonRoute() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetAgentController ctrl = importCtrl(ws, "TOK-JSONENC");

      ResponseStatusException ex = assertThrows(ResponseStatusException.class,
         () -> ctrl.importCsv("TOK-JSONENC",
            csvRequest("X", "a,b\n1,2", "ISO-8859-1", null, null, null, null, null, null, null),
            TestPrincipals.user("alice", "host-org")));

      assertEquals(400, ex.getStatusCode().value());
      assertTrue(ex.getReason().contains("import-csv-file"),
         "the refusal should point at the route that can honour it: " + ex.getReason());
   }

   /**
    * UTF-8 spelled any of the ways the JDK accepts is what this route already does, so it is not a
    * setting being ignored and must not be refused.
    */
   @Test
   void importCsvAcceptsUtf8SpelledAsAnAliasOnTheJsonRoute() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetAgentController ctrl = importCtrl(ws, "TOK-UTF8");

      ctrl.importCsv("TOK-UTF8",
         csvRequest("Utf8", "a,b\n1,2", "utf8", null, null, null, null, null, null, null),
         TestPrincipals.user("alice", "host-org"));

      importedTable(ws, "Utf8");
   }

   @Test
   void importCsvRejectsAMultiCharacterDelimiter() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetAgentController ctrl = importCtrl(ws, "TOK-BADDELIM");

      ResponseStatusException ex = assertThrows(ResponseStatusException.class,
         () -> ctrl.importCsv("TOK-BADDELIM",
            csvRequest("X", "a,b\n1,2", null, "\\t", null, null, null, null, null, null),
            TestPrincipals.user("alice", "host-org")));

      assertEquals(400, ex.getStatusCode().value());
      assertTrue(ex.getReason().contains("delimiterTab"),
         "the refusal should point at the flag that does what the caller meant: " + ex.getReason());
   }

   /**
    * The whole reason the multipart route exists: text handed over as JSON has already been
    * decoded, so a non-UTF-8 file can only survive if its bytes make the trip. This is L2
    * Finding 13.
    */
   @Test
   void importCsvFileDecodesTheBytesWithTheGivenEncoding() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetAgentController ctrl = importCtrl(ws, "TOK-GBK");
      // Built from code points so this source file stays ASCII: two CJK ideographs, which no
      // single-byte charset can represent, so a wrong decode cannot round-trip by accident.
      String header = new String(new char[] { 0x4ef7, 0x683c });
      byte[] utf16 = (header + ",b\n1,2").getBytes(java.nio.charset.StandardCharsets.UTF_16LE);

      ctrl.importCsvFile("TOK-GBK",
         new MockMultipartFile("file", "d.csv", "text/csv", utf16),
         "Encoded", "UTF-16LE", null, null, null, null, null, null, null, null,
         TestPrincipals.user("alice", "host-org"));

      ColumnSelection cs = importedTable(ws, "Encoded").getColumnSelection(false);
      assertNotNull(cs.getAttribute(header),
         "the header should decode back to its original characters");
   }

   @Test
   void importCsvRejectsBlankCsv() {
      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), mock(WorksheetEditService.class),
         mock(WorksheetService.class));

      ResponseStatusException ex = assertThrows(ResponseStatusException.class,
         () -> ctrl.importCsv("TOK", new WorksheetAgentController.ImportCsvRequest(null, " "),
                              TestPrincipals.user("alice", "host-org")));
      assertEquals(400, ex.getStatusCode().value());
   }

   // NOTE: there is no test here exercising a real .xlsx round-trip through importExcel().
   // ExcelFileSupport.getInstance() reflectively loads PoiExcelFileSupport from the
   // inetsoft-xml-formats module, which itself depends on inetsoft-core — core cannot
   // depend on it back (even at test scope) without creating a cyclic Maven reactor
   // reference. This mirrors the pre-existing gap in ImportCSVDialogService's own Excel
   // path, which has no core-module test coverage for the same structural reason; both
   // are exercised only where core and inetsoft-xml-formats are both on the classpath,
   // i.e. the packaged server.

   // NOTE: the framework-level blanket cap (spring.servlet.multipart.max-file-size /
   // max-request-size, application.yaml) is enforced by Spring/Tomcat's multipart parsing
   // before this controller method is ever invoked, converting an oversized upload to a
   // MaxUploadSizeExceededException (handled by handleMaxUploadSizeException() below). That
   // parsing isn't exercised by calling the controller method directly, so it has no unit
   // test here — it's covered by the same structural gap noted above.

   private static MockMultipartFile excelFile(byte[] content) {
      return new MockMultipartFile("file", "workbook.xlsx",
         "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", content);
   }

   @Test
   void importExcelRejectsMissingFile() {
      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), mock(WorksheetEditService.class),
         mock(WorksheetService.class));

      ResponseStatusException ex = assertThrows(ResponseStatusException.class,
         () -> ctrl.importExcel("TOK", null, "XLSX", null, null, null,
                                TestPrincipals.user("alice", "host-org")));
      assertEquals(400, ex.getStatusCode().value());
   }

   @Test
   void importExcelRejectsUnknownFileType() {
      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), mock(WorksheetEditService.class),
         mock(WorksheetService.class));

      MockMultipartFile file = excelFile(new byte[]{1, 2, 3, 4});

      ResponseStatusException ex = assertThrows(ResponseStatusException.class,
         () -> ctrl.importExcel("TOK", file, "PDF", null, null, null,
                                TestPrincipals.user("alice", "host-org")));
      assertEquals(400, ex.getStatusCode().value());
   }

   @Test
   void importExcelRejectsFileTooLarge() {
      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), mock(WorksheetEditService.class),
         mock(WorksheetService.class));

      MockMultipartFile file = excelFile(new byte[10]);

      try(MockedStatic<SreeEnv> sreeEnv = mockStatic(SreeEnv.class)) {
         sreeEnv.when(() -> SreeEnv.getProperty("excel.import.max")).thenReturn("5");

         ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> ctrl.importExcel("TOK", file, "XLSX", null, null, null,
                                   TestPrincipals.user("alice", "host-org")));
         assertEquals(400, ex.getStatusCode().value());
      }
   }

   // ---------------------------------------------------------------------------
   // Exception handling
   // ---------------------------------------------------------------------------

   @Test
   void handleResponseStatusExceptionIncludesReasonInBody() {
      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), mock(WorksheetEditService.class),
         mock(WorksheetService.class));

      ResponseStatusException ex = new ResponseStatusException(
         HttpStatus.BAD_REQUEST, "fileType must be either \"XLS\" or \"XLSX\"");

      ResponseEntity<Map<String, String>> response = ctrl.handleResponseStatusException(ex);

      assertEquals(400, response.getStatusCode().value());
      assertEquals("fileType must be either \"XLS\" or \"XLSX\"", response.getBody().get("error"));
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
   // add_table with endpoint/suffix — dispatch guards + permission gate for addTabularTable()
   // ---------------------------------------------------------------------------

   @Test
   void addTabularTableRequiresDatasource() {
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService editSvc = mock(WorksheetEditService.class);
      DataSourceService dataSourceService = mock(DataSourceService.class);

      WorksheetAgentController ctrl = securityController(editSvc,
         dataSourceService, mock(SecurityEngine.class), mock(MetadataApiService.class),
         mock(XRepository.class), mock(QueryManagerService.class));

      EditRequest req = addTabularTableRequest("t1", null, null, null, null,
         "Charges", null, null, null, null, null, null);

      PairingException ex = assertThrows(PairingException.class,
         () -> ctrl.edit("TOK-TT1", req, agent));
      assertTrue(ex.getMessage().contains("datasource is required"));
      verifyNoInteractions(dataSourceService);
      verifyNoInteractions(editSvc);
   }

   @Test
   void addTabularTableRejectsLogicalModelTogetherWithEndpoint() {
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService editSvc = mock(WorksheetEditService.class);
      DataSourceService dataSourceService = mock(DataSourceService.class);

      WorksheetAgentController ctrl = securityController(editSvc,
         dataSourceService, mock(SecurityEngine.class), mock(MetadataApiService.class),
         mock(XRepository.class), mock(QueryManagerService.class));

      EditRequest req = addTabularTableRequest("t1", "MyDatasource", "Order Model", null, null,
         "Charges", null, null, null, null, null, null);

      PairingException ex = assertThrows(PairingException.class,
         () -> ctrl.edit("TOK-TT2", req, agent));
      assertTrue(ex.getMessage().contains("logicalModel"));
      verifyNoInteractions(dataSourceService);
      verifyNoInteractions(editSvc);
   }

   @Test
   void addTabularTableRejectsSchemaCatalogTogetherWithEndpoint() {
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService editSvc = mock(WorksheetEditService.class);
      DataSourceService dataSourceService = mock(DataSourceService.class);

      WorksheetAgentController ctrl = securityController(editSvc,
         dataSourceService, mock(SecurityEngine.class), mock(MetadataApiService.class),
         mock(XRepository.class), mock(QueryManagerService.class));

      EditRequest req = addTabularTableRequest("t1", "MyDatasource", null, "dbo", null,
         "Charges", null, null, null, null, null, null);

      PairingException ex = assertThrows(PairingException.class,
         () -> ctrl.edit("TOK-TT3", req, agent));
      assertTrue(ex.getMessage().contains("schema/catalog"));
      verifyNoInteractions(dataSourceService);
      verifyNoInteractions(editSvc);
   }

   @Test
   void addTabularTableRejectsBothEndpointAndSuffix() {
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService editSvc = mock(WorksheetEditService.class);
      DataSourceService dataSourceService = mock(DataSourceService.class);

      WorksheetAgentController ctrl = securityController(editSvc,
         dataSourceService, mock(SecurityEngine.class), mock(MetadataApiService.class),
         mock(XRepository.class), mock(QueryManagerService.class));

      EditRequest req = addTabularTableRequest("t1", "MyDatasource", null, null, null,
         "Charges", null, null, null, null, "/v1/widgets/{id}", null);

      PairingException ex = assertThrows(PairingException.class,
         () -> ctrl.edit("TOK-TT4", req, agent));
      assertTrue(ex.getMessage().contains("both endpoint and suffix"));
      verifyNoInteractions(dataSourceService);
      verifyNoInteractions(editSvc);
   }

   @Test
   void addTabularTableDeniedByDatasourceReadThrowsPairingException() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService editSvc = mock(WorksheetEditService.class);
      DataSourceService dataSourceService = mock(DataSourceService.class);
      XRepository xrepository = mock(XRepository.class);

      when(dataSourceService.checkPermission(eq("MyDatasource"), eq(ResourceAction.READ), eq(agent)))
         .thenReturn(false);

      WorksheetAgentController ctrl = securityController(editSvc,
         dataSourceService, mock(SecurityEngine.class), mock(MetadataApiService.class),
         xrepository, mock(QueryManagerService.class));

      EditRequest req = addTabularTableRequest("t1", "MyDatasource", null, null, null,
         "Charges", null, null, null, null, null, null);

      PairingException ex = assertThrows(PairingException.class,
         () -> ctrl.edit("TOK-TT5", req, agent));
      assertTrue(ex.getMessage().contains("no READ permission on datasource MyDatasource"));

      // The READ denial must short-circuit before the data source (and its connector) is
      // resolved at all -- the next step dials a real, metered endpoint.
      verifyNoInteractions(xrepository);
      verifyNoInteractions(editSvc);
   }

   @Test
   void addTabularTableProceedsPastPermissionGateWhenReadGranted() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService editSvc = mock(WorksheetEditService.class);
      DataSourceService dataSourceService = mock(DataSourceService.class);
      XRepository xrepository = mock(XRepository.class);

      when(dataSourceService.checkPermission(eq("MyDatasource"), eq(ResourceAction.READ), eq(agent)))
         .thenReturn(true);

      WorksheetAgentController ctrl = securityController(editSvc,
         dataSourceService, mock(SecurityEngine.class), mock(MetadataApiService.class),
         xrepository, mock(QueryManagerService.class));

      EditRequest req = addTabularTableRequest("t1", "MyDatasource", null, null, null,
         "Charges", null, null, null, null, null, null);

      // xrepository is an unstubbed mock, so getDataSource returns null and the request fails
      // with "Data source not found" -- proof execution proceeded past the permission gate into
      // addTabularTable's own datasource resolution, exactly the shape
      // createTableTabularTableProceedsWhenDatasourceReadGranted already asserts on the
      // wiz-services side.
      PairingException ex = assertThrows(PairingException.class,
         () -> ctrl.edit("TOK-TT6", req, agent));
      assertTrue(ex.getMessage().contains("Data source not found"), ex.getMessage());
      verify(xrepository).getDataSource(eq("MyDatasource"));
      verifyNoInteractions(editSvc);
   }

   @Test
   void addTabularTableRejectsNonTabularDatasource() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService editSvc = mock(WorksheetEditService.class);
      DataSourceService dataSourceService = mock(DataSourceService.class);
      XRepository xrepository = mock(XRepository.class);

      when(dataSourceService.checkPermission(eq("MyDatasource"), eq(ResourceAction.READ), eq(agent)))
         .thenReturn(true);

      // A JDBC datasource is an XDataSource that is NOT a TabularDataSource.
      JDBCDataSource jdbcDs = mock(JDBCDataSource.class);
      when(jdbcDs.getType()).thenReturn("JDBC");
      when(xrepository.getDataSource(eq("MyDatasource"))).thenReturn(jdbcDs);

      WorksheetAgentController ctrl = securityController(editSvc,
         dataSourceService, mock(SecurityEngine.class), mock(MetadataApiService.class),
         xrepository, mock(QueryManagerService.class));

      EditRequest req = addTabularTableRequest("t1", "MyDatasource", null, null, null,
         "Charges", null, null, null, null, null, null);

      PairingException ex = assertThrows(PairingException.class,
         () -> ctrl.edit("TOK-TT7", req, agent));
      assertTrue(ex.getMessage().contains("not a tabular/REST one"), ex.getMessage());
      verifyNoInteractions(editSvc);
   }

   /**
    * Proves {@code req.parameters()} actually reaches
    * {@code TabularEndpointBindingSupport.applyEndpointContract} through {@code addTabularTable}
    * -- not just that the field exists on {@code EditRequest} and deserializes (see
    * {@code EditRequestParametersDeserializationTest}). {@code Repos} is given a REQUIRED
    * parameter ("id") with no value; supplying it via {@code req.parameters()} must satisfy
    * that requirement and let execution reach {@code editService.applyOnRuntime} -- with the
    * parameter hardcoded to {@code null} (the bug this test guards against), the same request
    * would instead fail with "requires parameter(s) ... id" before ever reaching it.
    */
   @Test
   void addTabularTableThreadsParametersIntoTheSharedHelper() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService editSvc = mock(WorksheetEditService.class);
      DataSourceService dataSourceService = mock(DataSourceService.class);
      XRepository xrepository = mock(XRepository.class);

      when(dataSourceService.checkPermission(eq("MyDatasource"), eq(ResourceAction.READ), eq(agent)))
         .thenReturn(true);

      TabularDataSource<?> ds = mock(TabularDataSource.class);
      when(xrepository.getDataSource(eq("MyDatasource"))).thenReturn(ds);

      WorksheetAgentController ctrl = securityController(editSvc,
         dataSourceService, mock(SecurityEngine.class), mock(MetadataApiService.class),
         xrepository, mock(QueryManagerService.class));

      FakeNamedConnectorQuery query = new FakeNamedConnectorQuery();
      RestParameter idParam = new RestParameter();
      idParam.setName("id");
      idParam.setRequired(true);
      query.getParameters().getParameters().add(idParam);

      EditRequest req = addTabularTableRequest("t1", "MyDatasource", null, null, null,
         "Repos", Map.of("id", "42"), null, null, null, null, null);

      try(MockedStatic<TabularUtil> tabularUtil = mockStatic(TabularUtil.class, CALLS_REAL_METHODS)) {
         tabularUtil.when(() -> TabularUtil.createQuery(eq("MyDatasource"))).thenReturn(query);

         assertDoesNotThrow(() -> ctrl.edit("TOK-TT8", req, agent),
            "the supplied 'id' parameter must satisfy Repos' required contract");
         // editService.applyOnRuntime is only reached AFTER applyEndpointContract succeeds --
         // if 'id' had not been threaded through, a PairingException would have fired first and
         // this mock would never have been touched.
         verify(editSvc).applyOnRuntime(eq("TOK-TT8"), eq(agent), any());
      }
   }

   // ---------------------------------------------------------------------------
   // add_named_group — datasource-scoped "Only For" mode (Bug #76097)
   // ---------------------------------------------------------------------------

   /**
    * Builds an {@code add_named_group} EditRequest scoped to a datasource/logical-model or
    * physical-table path, routing to {@code addDatasourceScopedNamedGroup()}.
    */
   private static EditRequest namedGroupDatasourceRequest(
      String name, String datasource, String logicalModel, String schema, String catalog,
      String sourceTable, String attribute,
      List<WorksheetMutationSupport.GroupMapping> groupMappings, Boolean groupOthers)
   {
      return new EditRequest(
         "add_named_group",   // op
         null,                 // table
         null,                 // column
         name,                 // name
         null,                 // type
         null,                 // newName
         null,                 // field
         null,                 // operation
         null,                 // values
         null,                 // direction
         null,                 // groups
         null,                 // aggregates
         null,                 // expression
         false,                // sql
         null,                 // leftTable
         null,                 // leftKey
         null,                 // rightTable
         null,                 // rightKey
         null,                 // joinType
         null,                 // visible
         null,                 // tables
         null,                 // source
         null,                 // concatType
         null,                 // conditions
         null,                 // ranking
         null,                 // headerColumns
         null,                 // dateOption
         null,                 // boundaries
         datasource,           // datasource
         schema,               // schema
         catalog,              // catalog
         logicalModel,         // logicalModel
         null,                 // leftKeys
         null,                 // rightKeys
         null,                 // row
         null,                 // col
         null,                 // value
         null,                 // index
         null,                 // alias
         null,                 // description
         null,                 // maxRows
         null,                 // distinct
         null,                 // columnOrder
         groupMappings,        // groupMappings
         groupOthers,          // groupOthers
         null,                 // variableValues
         null,                 // x
         null,                 // y
         null,                 // label
         null,                 // defaultValue
         null,                 // mode
         null,                 // insert
         null,                 // subtables
         sourceTable,          // sourceTable
         attribute,            // attribute
         null,                 // endpoint
         null,                 // parameters
         null,                 // lookup
         null,                 // lookupExpandArrays
         null,                 // lookupTopLevelOnly
         null,                 // suffix
         null                  // customLookups
      );
   }

   @Test
   void addNamedGroupRejectsDatasourceCombinedWithTable() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService editSvc = mock(WorksheetEditService.class);
      DataSourceService dataSourceService = mock(DataSourceService.class);

      WorksheetAgentController ctrl = securityController(editSvc,
         dataSourceService, mock(SecurityEngine.class), mock(MetadataApiService.class),
         mock(XRepository.class), mock(QueryManagerService.class));

      EditRequest req = new EditRequest(
         "add_named_group",   // op
         "Customer1",          // table
         "State",              // column
         "G",                  // name
         null,                 // type
         null,                 // newName
         null,                 // field
         null,                 // operation
         null,                 // values
         null,                 // direction
         null,                 // groups
         null,                 // aggregates
         null,                 // expression
         false,                // sql
         null,                 // leftTable
         null,                 // leftKey
         null,                 // rightTable
         null,                 // rightKey
         null,                 // joinType
         null,                 // visible
         null,                 // tables
         null,                 // source
         null,                 // concatType
         null,                 // conditions
         null,                 // ranking
         null,                 // headerColumns
         null,                 // dateOption
         null,                 // boundaries
         "Examples/Orders",    // datasource
         null,                 // schema
         null,                 // catalog
         "Order Model",        // logicalModel
         null,                 // leftKeys
         null,                 // rightKeys
         null,                 // row
         null,                 // col
         null,                 // value
         null,                 // index
         null,                 // alias
         null,                 // description
         null,                 // maxRows
         null,                 // distinct
         null,                 // columnOrder
         List.<WorksheetMutationSupport.GroupMapping>of(), // groupMappings
         false,                // groupOthers
         null,                 // variableValues
         null,                 // x
         null,                 // y
         null,                 // label
         null,                 // defaultValue
         null,                 // mode
         null,                 // insert
         null,                 // subtables
         "Customer",           // sourceTable
         "State",              // attribute
         null,                 // endpoint
         null,                 // parameters
         null,                 // lookup
         null,                 // lookupExpandArrays
         null,                 // lookupTopLevelOnly
         null,                 // suffix
         null                  // customLookups
      );

      PairingException ex = assertThrows(PairingException.class,
         () -> ctrl.edit("TOK-NGD1", req, agent));
      assertTrue(ex.getMessage().contains("mutually exclusive"));
      verifyNoInteractions(dataSourceService);
      verifyNoInteractions(editSvc);
   }

   @Test
   void addNamedGroupRequiresSourceTableWhenDatasourceGiven() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService editSvc = mock(WorksheetEditService.class);
      DataSourceService dataSourceService = mock(DataSourceService.class);

      WorksheetAgentController ctrl = securityController(editSvc,
         dataSourceService, mock(SecurityEngine.class), mock(MetadataApiService.class),
         mock(XRepository.class), mock(QueryManagerService.class));

      EditRequest req = namedGroupDatasourceRequest(
         "G", "Examples/Orders", "Order Model", null, null, null, "State", List.of(), false);

      PairingException ex = assertThrows(PairingException.class,
         () -> ctrl.edit("TOK-NGD2", req, agent));
      assertTrue(ex.getMessage().contains("sourceTable"));
      verifyNoInteractions(dataSourceService);
      verifyNoInteractions(editSvc);
   }

   @Test
   void addNamedGroupRequiresAttributeWhenDatasourceGiven() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService editSvc = mock(WorksheetEditService.class);
      DataSourceService dataSourceService = mock(DataSourceService.class);

      WorksheetAgentController ctrl = securityController(editSvc,
         dataSourceService, mock(SecurityEngine.class), mock(MetadataApiService.class),
         mock(XRepository.class), mock(QueryManagerService.class));

      EditRequest req = namedGroupDatasourceRequest(
         "G", "Examples/Orders", "Order Model", null, null, "Customer", null, List.of(), false);

      PairingException ex = assertThrows(PairingException.class,
         () -> ctrl.edit("TOK-NGD3", req, agent));
      assertTrue(ex.getMessage().contains("attribute"));
      verifyNoInteractions(dataSourceService);
      verifyNoInteractions(editSvc);
   }

   /**
    * PR #4765 review follow-up: {@code sourceTable}/{@code attribute}/{@code logicalModel}/
    * {@code schema}/{@code catalog} must require {@code datasource} server-side too, not only in
    * the plugin's own client-side validation -- without this guard, omitting {@code datasource}
    * (by mistake, or via any caller that bypasses the plugin) fell through to the legacy
    * {@code Editor.addNamedGroup(table, column, type, ...)} path with everything null, silently
    * creating a standalone string-typed grouping and discarding what the caller asked for.
    */
   @Test
   void addNamedGroupRejectsSourceTableWithoutDatasource() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService editSvc = mock(WorksheetEditService.class);
      DataSourceService dataSourceService = mock(DataSourceService.class);

      WorksheetAgentController ctrl = securityController(editSvc,
         dataSourceService, mock(SecurityEngine.class), mock(MetadataApiService.class),
         mock(XRepository.class), mock(QueryManagerService.class));

      EditRequest req = namedGroupDatasourceRequest(
         "G", null, null, null, null, "Customer", "State", List.of(), false);

      PairingException ex = assertThrows(PairingException.class,
         () -> ctrl.edit("TOK-NGD9", req, agent));
      assertTrue(ex.getMessage().contains("datasource"));
      verifyNoInteractions(dataSourceService);
      verifyNoInteractions(editSvc);
   }

   @Test
   void addNamedGroupRejectsAttributeWithoutDatasource() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService editSvc = mock(WorksheetEditService.class);
      DataSourceService dataSourceService = mock(DataSourceService.class);

      WorksheetAgentController ctrl = securityController(editSvc,
         dataSourceService, mock(SecurityEngine.class), mock(MetadataApiService.class),
         mock(XRepository.class), mock(QueryManagerService.class));

      EditRequest req = namedGroupDatasourceRequest(
         "G", null, null, null, null, null, "State", List.of(), false);

      PairingException ex = assertThrows(PairingException.class,
         () -> ctrl.edit("TOK-NGD10", req, agent));
      assertTrue(ex.getMessage().contains("datasource"));
      verifyNoInteractions(dataSourceService);
      verifyNoInteractions(editSvc);
   }

   /**
    * End-to-end regression for Bug #76097: {@code add_named_group} with {@code datasource} +
    * {@code logicalModel} + {@code sourceTable} (entity) + {@code attribute} must produce the
    * same kind of {@code attachedSource}/{@code attachedAttribute} a human creates via the
    * Composer's own "Add Grouping" dialog — {@code SourceInfo(MODEL, datasource, logicalModel)},
    * and an {@code AttributeRef(entity, attribute)} — so "Only For" and "Attribute" resolve
    * correctly, unlike the {@code table}+{@code column} worksheet-attached mode.
    */
   @Test
   void addNamedGroupWithLogicalModelBuildsRealDatasourceSourceInfo() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      XAttribute stateAttr = new XAttribute("State", "SA.CUSTOMERS", "STATE", XSchema.STRING);
      XEntity customerEntity = new XEntity("Customer");
      customerEntity.addAttribute(stateAttr);
      XLogicalModel orderModel = new XLogicalModel("Order Model");
      orderModel.addEntity(customerEntity);

      // XDataModel.addLogicalModel() reaches for a Spring-managed DataSourceRegistry bean
      // (via XDataModel.getRegistry()) that isn't available outside a running application
      // context, so the model is mocked and getLogicalModel() stubbed directly instead of
      // actually registering orderModel on a real XDataModel.
      XDataModel dataModel = mock(XDataModel.class);
      when(dataModel.getLogicalModel("Order Model")).thenReturn(orderModel);

      DataSourceService dataSourceService = mock(DataSourceService.class);
      when(dataSourceService.checkPermission(eq("Examples/Orders"), eq(ResourceAction.READ), eq(agent)))
         .thenReturn(true);
      when(dataSourceService.getDataModel("Examples/Orders")).thenReturn(dataModel);
      when(dataSourceService.getModelAssetEntry(any())).thenAnswer(inv -> inv.getArgument(0));
      when(dataSourceService.checkPermission(any(AssetEntry.class), eq(ResourceAction.READ), eq(agent)))
         .thenReturn(true);

      Worksheet ws = new Worksheet();
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);

      WorksheetEditService editSvc = mock(WorksheetEditService.class);
      when(editSvc.applyOnRuntime(eq("TOK-NGD4"), eq(agent), any())).thenAnswer(inv -> {
         WorksheetEditService.ThrowingFunction<RuntimeWorksheet, ?> fn = inv.getArgument(2);
         return fn.apply(rws);
      });

      WorksheetAgentController ctrl = securityController(editSvc,
         dataSourceService, mock(SecurityEngine.class), mock(MetadataApiService.class),
         mock(XRepository.class), mock(QueryManagerService.class));

      List<WorksheetMutationSupport.GroupMapping> mappings = List.of(
         new WorksheetMutationSupport.GroupMapping("N", List.of("NJ", "NY", "NV")));
      EditRequest req = namedGroupDatasourceRequest(
         "State N Group", "Examples/Orders", "Order Model", null, null,
         "Customer", "State", mappings, true);

      ctrl.edit("TOK-NGD4", req, agent);

      DefaultNamedGroupAssembly nga = (DefaultNamedGroupAssembly) ws.getAssembly("State N Group");
      assertNotNull(nga, "the datasource-scoped grouping must be created in the worksheet");

      SourceInfo attached = nga.getAttachedSource();
      assertEquals(SourceInfo.MODEL, attached.getType());
      assertEquals("Examples/Orders", attached.getPrefix());
      assertEquals("Order Model", attached.getSource());

      DataRef ref = nga.getAttachedAttribute();
      assertInstanceOf(AttributeRef.class, ((ColumnRef) ref).getDataRef());
      AttributeRef attrRef = (AttributeRef) ((ColumnRef) ref).getDataRef();
      assertEquals("Customer", attrRef.getEntity());
      assertEquals("State", attrRef.getAttribute());
      assertEquals(XSchema.STRING, ref.getDataType());

      assertEquals(AttachedAssembly.COLUMN_ATTACHED, nga.getAttachedType());
      assertNotNull(nga.getNamedGroupInfo().getGroupCondition("N"),
         "group mappings must still be applied");
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

   // ---------------------------------------------------------------------------
   // Whole-branch review finding 1 (CRITICAL) -- a pane-scoped code is not a
   // whole-sheet write handle
   // ---------------------------------------------------------------------------

   /**
    * A pane-scoped session names ONE script location. This controller's {@code edit} op
    * dispatcher can delete a table, add a join, or rewrite any column's expression, and it
    * resolves the same session token without ever reading {@code editorContext} -- so before this
    * guard, a code minted from one chart's Script tab was, in practice, an unscoped write handle
    * on the whole worksheet.
    *
    * <p>Refusal must land BEFORE the edit service is touched: {@code verifyNoInteractions} is the
    * load-bearing half of this test, since a refusal thrown after a partial mutation would leave
    * the worksheet changed and still report an error.
    */
   @Test
   void editRefusesAPaneScopedSessionBeforeReachingTheEditService() {
      SheetSessionService sessions = mock(SheetSessionService.class);
      when(sessions.resolve(eq("tok-pane"), anyString())).thenReturn(paneScopedSession("tok-pane"));

      WorksheetEditService edit = mock(WorksheetEditService.class);
      WorksheetAgentController controller = controller(
         featureOn(), mock(SheetJoinService.class), sessions, mock(WorksheetReadService.class),
         edit, mock(WorksheetService.class));

      PairingException thrown = assertThrows(PairingException.class,
         () -> controller.edit("tok-pane", deleteTableRequest("Query1"), agent()));

      assertTrue(thrown.getMessage().contains("scoped to one script location"),
                 thrown.getMessage());
      // The one action that fixes it. A refusal an agent cannot act on just becomes a retry loop.
      assertTrue(thrown.getMessage().contains("Connect to Claude"), thrown.getMessage());
      verifyNoInteractions(edit);
   }

   /**
    * The regression that would be worse than the bug: whole-sheet toolbar pairing is in use by
    * other agents today, and it must be completely unaffected. A session with no
    * {@code editorContext} passes the same guard untouched and reaches the edit service.
    */
   @Test
   void aWholeSheetToolbarSessionIsUnaffectedByTheGuard() throws Exception {
      SheetSessionService sessions = mock(SheetSessionService.class);
      when(sessions.resolve(eq("tok-toolbar"), anyString())).thenReturn(session("tok-toolbar"));

      WorksheetEditService edit = mock(WorksheetEditService.class);
      WorksheetAgentController controller = controller(
         featureOn(), mock(SheetJoinService.class), sessions, mock(WorksheetReadService.class),
         edit, mock(WorksheetService.class));

      controller.edit("tok-toolbar", deleteTableRequest("Query1"), agent());

      verify(edit).apply(eq("tok-toolbar"), any(Principal.class), any());
   }

   /**
    * An expired/foreign token must still report what is actually wrong with it. Answering "you
    * are pane-scoped" to a token that never resolved would send the user to re-pair from the
    * toolbar when the real problem is that this session is gone.
    */
   @Test
   void anUnresolvableTokenStillReportsExpiryNotScope() throws Exception {
      SheetSessionService sessions = mock(SheetSessionService.class);
      when(sessions.resolve(anyString(), anyString())).thenReturn(null);

      WorksheetEditService edit = mock(WorksheetEditService.class);
      WorksheetAgentController controller = controller(
         featureOn(), mock(SheetJoinService.class), sessions, mock(WorksheetReadService.class),
         edit, mock(WorksheetService.class));

      // The guard passes it through untouched; the edit service's own resolution is what
      // decides, and reports "invalid or expired session" rather than a scope problem.
      assertDoesNotThrow(() -> controller.edit("gone", deleteTableRequest("Query1"), agent()));
      verify(edit).apply(eq("gone"), any(Principal.class), any());
   }

   /** The agent principal these guard tests drive the controller with. */
   private static Principal agent() {
      return TestPrincipals.user("alice", "host-org");
   }

   private static JoinSession paneScopedSession(String token) {
      return new JoinSession(token, "Worksheet/ws-1", "alice~;~host-org",
                             SheetType.WORKSHEET, 0L, Long.MAX_VALUE,
                             JoinSession.ConnectionMode.PAIRED, "sock-1", "alice",
                             new EditorContext("worksheetExpression", "Query1", "Margin", null));
   }

   // ---------------------------------------------------------------------------
   // Whole-branch review finding 2 (Important) -- the guard is 12 hand calls, not a chokepoint
   // ---------------------------------------------------------------------------

   /** Methods that must NOT enforce the whole-sheet guard, and why. */
   private static final Set<String> WHOLE_SHEET_GUARD_EXEMPT = Set.of(
      // Ending a pane-scoped session is exactly what detach is for -- see its own javadoc.
      "detach"
   );

   private static String sessionTokenMappingPath(Method m) {
      GetMapping get = m.getAnnotation(GetMapping.class);

      if(get != null && get.value().length > 0) {
         return get.value()[0];
      }

      PostMapping post = m.getAnnotation(PostMapping.class);

      if(post != null && post.value().length > 0) {
         return post.value()[0];
      }

      return null;
   }

   /**
    * Synthesizes a dummy argument per parameter. Safe because every guarded endpoint calls
    * {@code requireWholeSheetSession} as its first or second statement (right after
    * {@code requireEnabled()}), before any parameter other than {@code sessionToken}/
    * {@code user} is ever dereferenced -- so a null request body or unused query param never
    * gets the chance to NPE ahead of the guard.
    */
   private static Object[] paneScopeProbeArgs(Method m, Principal agent, String token) {
      Parameter[] params = m.getParameters();
      Object[] args = new Object[params.length];

      for(int i = 0; i < params.length; i++) {
         Parameter p = params[i];
         Class<?> t = p.getType();

         if(t == Principal.class) {
            args[i] = agent;
         }
         else if(p.isAnnotationPresent(PathVariable.class)) {
            args[i] = token;
         }
         else if(t == int.class) {
            args[i] = 0;
         }
         else if(t == boolean.class) {
            args[i] = false;
         }
         else {
            args[i] = null;
         }
      }

      return args;
   }

   /**
    * Mirror of {@code PaneScopeServiceTest#everyKindIsClassifiedByExactlyOneGuard} for THIS
    * controller. The viewsheet/binding side funnels every endpoint through ONE resolution
    * ({@code ViewsheetSessionService#requireSession}), so a new endpoint there cannot skip the
    * pane-scope refusal even by accident. This controller cannot do the same: {@code editOp} is
    * deliberately shared with the pane-scoped {@code WorksheetScriptService} caller, which has
    * ALREADY run its own narrow {@code PaneScopeService.check} and must not be refused again by
    * the broad whole-sheet check -- see {@link WorksheetAgentController#editOp} javadoc. So the
    * guard here is 14 hand-written {@code requireWholeSheetSession(sessionToken, user)} calls,
    * one per endpoint, and nothing before this test failed the build the day one of them went
    * missing.
    *
    * <p>This closes that gap the way the classification guard closes its own: by reflectively
    * enumerating every {@code @GetMapping}/{@code @PostMapping} method whose path contains
    * {@code {sessionToken}} and proving each one actually refuses a pane-scoped session, rather
    * than trusting that the next endpoint's author remembers to add the call. {@code join} never
    * matches (no {@code {sessionToken}} in its path -- that is where one is minted); {@code
    * detach} matches but is explicitly exempted above, for the reason its own javadoc gives.
    * Endpoint 15 fails THIS test the moment it omits the guard.
    */
   @Test
   void everySessionTokenEndpointRefusesAPaneScopedSessionOrIsExplicitlyExempt() {
      SheetSessionService sessions = mock(SheetSessionService.class);
      when(sessions.resolve(anyString(), anyString())).thenReturn(paneScopedSession("tok-refl"));

      WorksheetAgentController controller = controller(featureOn(), mock(SheetJoinService.class),
         sessions, mock(WorksheetReadService.class), mock(WorksheetEditService.class),
         mock(WorksheetService.class));

      Principal agent = agent();
      List<String> notRefused = new ArrayList<>();
      int checked = 0;

      for(Method m : WorksheetAgentController.class.getDeclaredMethods()) {
         if(m.isSynthetic()) {
            continue;
         }

         String path = sessionTokenMappingPath(m);

         if(path == null || !path.contains("{sessionToken}") ||
            WHOLE_SHEET_GUARD_EXEMPT.contains(m.getName()))
         {
            continue;
         }

         checked++;
         m.setAccessible(true);
         Object[] args = paneScopeProbeArgs(m, agent, "tok-refl");

         try {
            m.invoke(controller, args);
            notRefused.add(m.getName());
         }
         catch(InvocationTargetException e) {
            Throwable cause = e.getCause();

            if(!(cause instanceof PairingException) ||
               !cause.getMessage().contains("scoped to one script location") ||
               !cause.getMessage().contains("Connect to Claude"))
            {
               throw new AssertionError("Endpoint '" + m.getName() + "' rejected the pane-" +
                  "scoped session, but not with the whole-sheet-guard reason -- got: " + cause,
                  cause);
            }
         }
         catch(IllegalAccessException e) {
            throw new AssertionError("Could not invoke '" + m.getName() + "' reflectively", e);
         }
      }

      assertTrue(checked >= 14, "Expected at least the 14 known session-scoped endpoints to " +
         "be enumerated, found " + checked + " -- did WorksheetAgentController's mapping " +
         "annotations change shape?");
      assertTrue(notRefused.isEmpty(), "Endpoint(s) mapped with {sessionToken} did not refuse " +
         "a pane-scoped session: " + notRefused + ". Add requireWholeSheetSession(sessionToken," +
         " user) as their first call (right after requireEnabled()), or add them to " +
         "WHOLE_SHEET_GUARD_EXEMPT above with a reason like detach's.");
   }

}
