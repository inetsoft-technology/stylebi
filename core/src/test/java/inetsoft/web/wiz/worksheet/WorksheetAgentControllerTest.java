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

import inetsoft.report.composition.RuntimeSheet;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.report.composition.WorksheetService;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.uql.VariableTable;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.sree.security.SecurityException;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.XNode;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.SQLBoundTableAssemblyInfo;
import inetsoft.uql.asset.sync.DependencyTool;
import inetsoft.uql.asset.sync.RenameDependencyInfo;
import inetsoft.uql.asset.sync.RenameTransformHandler;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.erm.XAttribute;
import inetsoft.uql.erm.XDataModel;
import inetsoft.uql.erm.XEntity;
import inetsoft.uql.erm.XLogicalModel;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.uql.jdbc.JDBCQuery;
import inetsoft.uql.jdbc.util.SQLTypes;
import inetsoft.web.wiz.model.DatabaseTableMeta;
import inetsoft.uql.schema.UserVariable;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.uql.tabular.RestParameter;
import inetsoft.uql.tabular.TabularDataSource;
import inetsoft.uql.tabular.TabularUtil;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.web.composer.ws.LayoutGraphService;
import inetsoft.web.composer.ws.joins.InnerJoinService;
import inetsoft.web.portal.controller.database.DataSourceService;
import inetsoft.web.portal.controller.database.QueryManagerService;
import inetsoft.web.wiz.pairing.*;
import inetsoft.web.wiz.service.FakeNamedConnectorQuery;
import inetsoft.web.wiz.service.MetadataApiService;
import inetsoft.web.wiz.service.RenderNotReadyException;
import inetsoft.web.wiz.worksheet.model.WorksheetModel;
import inetsoft.web.wiz.worksheet.model.WorksheetPropertiesModel;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
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
      return controller(feature, join, sessions, read, edit, ws,
                        mock(inetsoft.uql.asset.sync.RenameTransformHandler.class));
   }

   private static WorksheetAgentController controller(SheetAgentFeature feature,
                                                       SheetJoinService join,
                                                       SheetSessionService sessions,
                                                       WorksheetReadService read,
                                                       WorksheetEditService edit,
                                                       WorksheetService ws,
                                                       inetsoft.uql.asset.sync.RenameTransformHandler renameTransformHandler)
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
                                          mock(inetsoft.sree.security.SecurityEngine.class),
                                          renameTransformHandler);
   }

   /** Like the 6-arg {@code controller}, but lets a {@code dependents} test control the
    *  {@link AssetRepository} instead of getting an unstubbed mock. */
   private static WorksheetAgentController controller(SheetAgentFeature feature,
                                                       SheetJoinService join,
                                                       SheetSessionService sessions,
                                                       WorksheetReadService read,
                                                       WorksheetEditService edit,
                                                       WorksheetService ws,
                                                       AssetRepository assetRepository)
   {
      return new WorksheetAgentController(feature, join, sessions, read, edit, ws,
                                          mock(WorksheetPreviewService.class),
                                          mock(SheetAgentBroadcastService.class),
                                          mock(inetsoft.uql.XRepository.class),
                                          assetRepository,
                                          mock(inetsoft.web.wiz.service.MetadataApiService.class),
                                          mock(inetsoft.web.portal.controller.database.QueryManagerService.class),
                                          mock(inetsoft.web.composer.ws.LayoutGraphService.class),
                                          mock(inetsoft.web.portal.controller.database.DataSourceService.class),
                                          mock(inetsoft.sree.security.SecurityEngine.class),
                                          mock(inetsoft.uql.asset.sync.RenameTransformHandler.class));
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
         dataSourceService, securityEngine,
         mock(inetsoft.uql.asset.sync.RenameTransformHandler.class));
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

   /** Builds a {@code reorder_concat_subtables} EditRequest that routes to reorderConcatSubtables(). */
   private static EditRequest reorderConcatSubtablesRequest(String table, List<String> subtables) {
      return new EditRequest(
         "reorder_concat_subtables", table, null, null, null, null, null, null, null, null,
         null, null, null, false, null, null, null, null, null, null, null, null, null, null,
         null, null, null, null, null, null, null, null, null, null, null, null, null,
         null, null, null, null, null, null, null, null, null, null, null, null, null, null,
         null, subtables,
         null, null,
         null, null, null, null, null, null, null
      );
   }

   /** Builds a {@code refresh_data} EditRequest that routes to refreshData(). */
   private static EditRequest refreshDataRequest(String table) {
      return new EditRequest(
         "refresh_data", table, null, null, null, null, null, null, null, null,
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
      when(joinSvc.join(eq("CODE"), eq(agent)))
         .thenReturn(new SheetJoinService.JoinOutcome(s, "Sales Analysis"));

      WorksheetAgentController ctrl = controller(featureOn(), joinSvc,
         mock(SheetSessionService.class), mock(WorksheetReadService.class),
         mock(WorksheetEditService.class), mock(WorksheetService.class));

      WorksheetAgentController.JoinResponse resp = ctrl.join(new WorksheetAgentController.JoinRequest("CODE"), agent);

      assertEquals("TOK-1", resp.sessionToken());
      assertEquals("Worksheet/ws-1", resp.runtimeId());
      assertEquals("alice~;~host-org", resp.ownerIdentity());
      assertEquals("Sales Analysis", resp.sheetLabel());
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
      when(joinSvc.join(eq("CODE"), eq(agent)))
         .thenReturn(new SheetJoinService.JoinOutcome(s, null));

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
   // dependents
   // ---------------------------------------------------------------------------

   @Test
   void dependentsRejectsFlagOff() {
      WorksheetAgentController ctrl = controller(featureOff(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), mock(WorksheetEditService.class),
         mock(WorksheetService.class));

      ResponseStatusException ex = assertThrows(ResponseStatusException.class,
         () -> ctrl.dependents("TOK-DEP-OFF", TestPrincipals.user("alice", "host-org")));
      assertEquals(403, ex.getStatusCode().value());
   }

   /** An unsaved worksheet can have no dependents -- nothing can point at an asset that was
    *  never saved, so this must not attempt the repository lookup at all. */
   @Test
   void dependentsReportsUnsavedWorksheetAsHavingNone() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      AssetEntry tempEntry = new AssetEntry(AssetRepository.TEMPORARY_SCOPE,
         AssetEntry.Type.WORKSHEET, "__TEMPORARY__/ws-1", null);
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getEntry()).thenReturn(tempEntry);

      WorksheetEditService editSvc = mock(WorksheetEditService.class);
      when(editSvc.resolve(eq("TOK-DEP-TEMP"), eq(agent))).thenReturn(rws);

      AssetRepository repo = mock(AssetRepository.class);

      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), editSvc, mock(WorksheetService.class), repo);

      Map<String, Object> result = ctrl.dependents("TOK-DEP-TEMP", agent);

      assertEquals(Boolean.FALSE, result.get("saved"));
      assertEquals(List.of(), result.get("dependents"));
      verifyNoInteractions(repo);
   }

   @Test
   void dependentsReportsNoneForASavedWorksheetNothingDependsOn() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      AssetEntry entry = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
         AssetEntry.Type.WORKSHEET, "Orders WS", null);
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getEntry()).thenReturn(entry);

      WorksheetEditService editSvc = mock(WorksheetEditService.class);
      when(editSvc.resolve(eq("TOK-DEP-NONE"), eq(agent))).thenReturn(rws);

      AssetRepository repo = mock(AssetRepository.class);
      when(repo.getSheetDependencies(eq(entry), eq(agent))).thenReturn(new AssetEntry[0]);

      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), editSvc, mock(WorksheetService.class), repo);

      Map<String, Object> result = ctrl.dependents("TOK-DEP-NONE", agent);

      assertEquals(Boolean.TRUE, result.get("saved"));
      assertEquals("Orders WS", result.get("path"));
      assertEquals(List.of(), result.get("dependents"));
      assertTrue(result.get("summary").toString().contains("No saved asset"));
   }

   /** The actual "who uses this worksheet" case: a saved viewsheet depends on it, and that must
    *  be named by path and type rather than left for the caller to take on faith. */
   @Test
   void dependentsListsDependentAssetsByPathAndType() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      AssetEntry entry = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
         AssetEntry.Type.WORKSHEET, "Orders WS", null);
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getEntry()).thenReturn(entry);

      WorksheetEditService editSvc = mock(WorksheetEditService.class);
      when(editSvc.resolve(eq("TOK-DEP-SOME"), eq(agent))).thenReturn(rws);

      AssetEntry dependentVs = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
         AssetEntry.Type.VIEWSHEET, "Sales Dashboard", null);

      AssetRepository repo = mock(AssetRepository.class);
      when(repo.getSheetDependencies(eq(entry), eq(agent)))
         .thenReturn(new AssetEntry[]{ dependentVs });

      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), editSvc, mock(WorksheetService.class), repo);

      Map<String, Object> result = ctrl.dependents("TOK-DEP-SOME", agent);

      assertEquals(Boolean.TRUE, result.get("saved"));
      @SuppressWarnings("unchecked")
      List<Map<String, Object>> dependents = (List<Map<String, Object>>) result.get("dependents");
      assertEquals(1, dependents.size());
      assertEquals("Sales Dashboard", dependents.get(0).get("path"));
      assertEquals("viewsheet", dependents.get(0).get("type"));
      assertTrue(result.get("summary").toString().contains("Sales Dashboard"));
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
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));

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
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));

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
         null,                  // labels
         null,                  // choices
         null,                  // joinPaths
         null,                  // mergeable
         null,                  // visibleInViewsheet
         null,                  // confirmed
         null,                  // rowCount
         null                   // concatDistinct
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
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));

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

   /**
    * Bug #76350 follow-on (item A): {@code refresh_data} on an explicit table called
    * {@code refreshColumnSelection} (the call that actually executes a crosstab/grouped table's
    * query, via {@code AssetQuerySandbox}'s internal {@code query.getTableLens}) with no bound at
    * all -- a table whose query hadn't run yet in this runtime (e.g. PWA-005's cross-join-grouped
    * crosstab) blocked this request for however long that query took. Mirrors
    * {@code ViewsheetEditServiceTest#resizeThrowsRenderNotReadyWhenTableDataIsSlowAndDoesNotDelegate}.
    */
   @Test
   void refreshDataThrowsRenderNotReadyWhenColumnSelectionIsSlow() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      Worksheet ws = new Worksheet();
      TableAssembly crosstab = TestWorksheets.withGroupSumAndSort(
         TestWorksheets.nonEmbeddedTableWithColumns(ws, "Crosstab1", "cust", "amount"),
         "cust", "amount");
      ws.addAssembly(crosstab);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      AssetQuerySandbox box = mock(AssetQuerySandbox.class);
      when(rws.getAssetQuerySandbox()).thenReturn(box);
      doAnswer(invocation -> {
         Thread.sleep(3_000);
         return null;
      }).when(box).refreshColumnSelection(eq("Crosstab1"), anyBoolean());

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      when(sessions.resolve(eq("TOK-RD"), any())).thenReturn(session("TOK-RD"));
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      WorksheetEditService editSvc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));

      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), editSvc, mock(WorksheetService.class));

      assertThrows(RenderNotReadyException.class,
         () -> ctrl.edit("TOK-RD", refreshDataRequest("Crosstab1"), agent));
   }

   /** A table whose data is already warm (or warms within the bound) refreshes normally. */
   @Test
   void refreshDataSucceedsWhenColumnSelectionIsFast() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      Worksheet ws = new Worksheet();
      TableAssembly crosstab = TestWorksheets.withGroupSumAndSort(
         TestWorksheets.nonEmbeddedTableWithColumns(ws, "Crosstab1", "cust", "amount"),
         "cust", "amount");
      ws.addAssembly(crosstab);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      AssetQuerySandbox box = mock(AssetQuerySandbox.class);
      when(rws.getAssetQuerySandbox()).thenReturn(box);

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      when(sessions.resolve(eq("TOK-RD2"), any())).thenReturn(session("TOK-RD2"));
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      WorksheetEditService editSvc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));

      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), editSvc, mock(WorksheetService.class));

      ctrl.edit("TOK-RD2", refreshDataRequest("Crosstab1"), agent);

      verify(box, atLeastOnce()).refreshColumnSelection(eq("Crosstab1"), anyBoolean());
   }

   /**
    * Bug #76350 follow-on (item A), review round 1: {@code insertColumn} calls the same unwrapped
    * {@code refreshColumnSelection}/{@code loadTableData} pair 3a already bounds for
    * {@code refresh_data} -- a prior {@code set_group_aggregate(crosstab=true)} on this same
    * embedded table makes it pay the same expensive query-execution path.
    */
   @Test
   void insertColumnThrowsRenderNotReadyWhenColumnSelectionIsSlow() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      // Row 0 is the header row insertColumn's setEmbeddedData/getEmbeddedData work off of --
      // TestWorksheets.tableWithColumns only sets a ColumnSelection, no actual XEmbeddedTable
      // rows, so insertColumn's real column-insertion logic needs a table built this way instead
      // (mirrors WorksheetEditServiceMutatorsTest#embeddedWithData).
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = new EmbeddedTableAssembly(ws, "T");
      t.setEmbeddedData(new XEmbeddedTable(
         new String[] { XSchema.STRING, XSchema.DOUBLE },
         new Object[][] { { "cust", "amount" }, { "x1", 1.0 } }));
      TestWorksheets.withGroupSumAndSort(t, "cust", "amount");
      ws.addAssembly(t);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      AssetQuerySandbox box = mock(AssetQuerySandbox.class);
      when(rws.getAssetQuerySandbox()).thenReturn(box);
      doAnswer(invocation -> {
         Thread.sleep(3_000);
         return null;
      }).when(box).refreshColumnSelection(eq("T"), anyBoolean());

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      when(sessions.resolve(eq("TOK-ICS"), any())).thenReturn(session("TOK-ICS"));
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      WorksheetEditService editSvc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));

      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), editSvc, mock(WorksheetService.class));

      assertThrows(RenderNotReadyException.class,
         () -> ctrl.edit("TOK-ICS", insertColumnRequest("T"), agent));
   }

   /**
    * Bug #76350 follow-on (item A), review round 1: {@code reorderConcatSubtables} calls the same
    * unwrapped pair -- a prior {@code set_group_aggregate(crosstab=true)} on this same
    * concatenated table makes it pay the same expensive query-execution path.
    */
   @Test
   void reorderConcatSubtablesThrowsRenderNotReadyWhenColumnSelectionIsSlow() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly a = TestWorksheets.tableWithColumns(ws, "A", "k", "v");
      EmbeddedTableAssembly b = TestWorksheets.tableWithColumns(ws, "B", "k", "v");
      ws.addAssembly(a);
      ws.addAssembly(b);

      TableAssemblyOperator top = new TableAssemblyOperator();
      TableAssemblyOperator.Operator op = new TableAssemblyOperator.Operator();
      op.setOperation(TableAssemblyOperator.UNION);
      top.addOperator(op);

      ConcatenatedTableAssembly concat = new ConcatenatedTableAssembly(
         ws, "Concat1", new TableAssembly[]{ a, b }, new TableAssemblyOperator[]{ top });
      TestWorksheets.withGroupSumAndSort(concat, "k", "v");
      ws.addAssembly(concat);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      AssetQuerySandbox box = mock(AssetQuerySandbox.class);
      when(rws.getAssetQuerySandbox()).thenReturn(box);
      doAnswer(invocation -> {
         Thread.sleep(3_000);
         return null;
      }).when(box).refreshColumnSelection(eq("Concat1"), anyBoolean());

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      when(sessions.resolve(eq("TOK-RC"), any())).thenReturn(session("TOK-RC"));
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      WorksheetEditService editSvc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));

      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), editSvc, mock(WorksheetService.class));

      assertThrows(RenderNotReadyException.class,
         () -> ctrl.edit("TOK-RC", reorderConcatSubtablesRequest("Concat1", List.of("B", "A")), agent));
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
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));

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
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));

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
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));

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
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));

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
      return addVariableRequest(name, type, null);
   }

   /** Same as {@link #addVariableRequest(String, String)}, with an explicit default value. */
   private static EditRequest addVariableRequest(String name, String type, String defaultValue) {
      return new EditRequest(
         "add_variable", null, null, name, type, null, null, null, null, null, null, null, null, false,
         null, null, null, null, null,
         null, null, null, null, null, null, null, null, null, null, null, null, null,
         null, null, null, null, null, null, null, null, null, null,
         null, null, null, null,
         null, null,
         null, defaultValue,
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
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));

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
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));

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

   /**
    * L2-Group7 regressions: add_variable previously accepted a name the Composer's own
    * Variable dialog would refuse ({@code doesNotStartWithNumber}/{@code variableSpecialCharacters}
    * are Angular-only, no Java match anywhere), a name colliding case-insensitively with an
    * existing variable ({@code Worksheet.getAssembly} is case-sensitive, unlike the dialog's own
    * {@code FormValidators.exists(..., {ignoreCase:true})}), an unrecognized {@code type}
    * (silently leaving the variable with no type node at all), and a {@code defaultValue} that
    * doesn't parse under the declared type (silently storing the raw, unparsed string).
    */
   @ParameterizedTest
   @CsvSource({
      "1BadName, start with a digit",
      "'Bad*Name', contains characters",
   })
   void editRejectsAddVariableWithInvalidName(String badName, String expectedFragment)
      throws Exception
   {
      Principal agent = TestPrincipals.user("alice", "host-org");
      Worksheet ws = new Worksheet();

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      when(sessions.resolve(eq("TOK-AVN"), any())).thenReturn(session("TOK-AVN"));
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      WorksheetEditService editSvc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));

      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), editSvc, mock(WorksheetService.class));

      PairingException ex = assertThrows(PairingException.class,
         () -> ctrl.edit("TOK-AVN", addVariableRequest(badName, "string"), agent));
      assertTrue(ex.getMessage().contains(expectedFragment), ex.getMessage());
      assertNull(ws.getAssembly(badName));
   }

   @Test
   void editRejectsAddVariableWithCaseInsensitiveDuplicateName() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      Worksheet ws = new Worksheet();
      AssetVariable existingVar = new AssetVariable("TestVar");
      existingVar.setTypeNode(XSchema.createPrimitiveType(XSchema.STRING));
      DefaultVariableAssembly existing = new DefaultVariableAssembly(ws, "TestVar");
      existing.setVariable(existingVar);
      ws.addAssembly(existing);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      when(sessions.resolve(eq("TOK-AVCI"), any())).thenReturn(session("TOK-AVCI"));
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      WorksheetEditService editSvc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));

      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), editSvc, mock(WorksheetService.class));

      PairingException ex = assertThrows(PairingException.class,
         () -> ctrl.edit("TOK-AVCI", addVariableRequest("testvar", "string"), agent));
      assertTrue(ex.getMessage().contains("already exists"), ex.getMessage());
      assertNull(ws.getAssembly("testvar"),
         "a second, differently-cased variable must not have been created");
   }

   @Test
   void editRejectsAddVariableWithUnrecognizedType() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      Worksheet ws = new Worksheet();

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      when(sessions.resolve(eq("TOK-AVT"), any())).thenReturn(session("TOK-AVT"));
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      WorksheetEditService editSvc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));

      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), editSvc, mock(WorksheetService.class));

      PairingException ex = assertThrows(PairingException.class,
         () -> ctrl.edit("TOK-AVT", addVariableRequest("TypeTest", "totally_bogus_type"), agent));
      assertTrue(ex.getMessage().contains("totally_bogus_type"), ex.getMessage());
      assertNull(ws.getAssembly("TypeTest"),
         "an invalid type must reject the whole call, not create a typeless variable");
   }

   @Test
   void editRejectsAddVariableWithUnparsableDefaultValue() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      Worksheet ws = new Worksheet();

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      when(sessions.resolve(eq("TOK-AVD2"), any())).thenReturn(session("TOK-AVD2"));
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      WorksheetEditService editSvc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));

      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), editSvc, mock(WorksheetService.class));

      PairingException ex = assertThrows(PairingException.class,
         () -> ctrl.edit("TOK-AVD2",
            addVariableRequest("IntTest", "integer", "not_a_number"), agent));
      assertTrue(ex.getMessage().contains("not_a_number"), ex.getMessage());
      assertNull(ws.getAssembly("IntTest"),
         "an unparsable default value must reject the whole call");
   }

   /**
    * PR #4901 round-2 review follow-up: {@code createVariable} builds a
    * {@code DefaultVariableAssembly} and adds it directly, bypassing
    * {@code WorksheetEditService.Editor.placeAssembly}'s {@code requireStorableName} guard the
    * same way {@code addJoin}/{@code duplicateAssembly}/{@code renameVariable} did before the
    * round-1 fix. {@code AssemblyInfo:254} writes the name into a CDATA section verbatim, so a
    * name containing the terminator closes it early and leaves malformed XML in storage.
    */
   @Test
   void editRejectsAddVariableWithANameThatWouldBreakTheStoredXml() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      Worksheet ws = new Worksheet();

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      when(sessions.resolve(eq("TOK-AV-CDATA"), any())).thenReturn(session("TOK-AV-CDATA"));
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      WorksheetEditService editSvc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));

      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), editSvc, mock(WorksheetService.class));

      PairingException ex = assertThrows(PairingException.class,
         () -> ctrl.edit("TOK-AV-CDATA", addVariableRequest("bad]]>name", "double"), agent));
      assertTrue(ex.getMessage().contains("]]>"), ex.getMessage());
      assertNull(ws.getAssembly("bad]]>name"));
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
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));

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
         choices,                     // choices
         null,                        // joinPaths
         null,                        // mergeable
         null,                        // visibleInViewsheet
         null,                        // confirmed
         null,                        // rowCount
         null                         // concatDistinct
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
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));

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
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));

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
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));

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
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));

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
   // set_variable_values (L2-Group8)
   // ---------------------------------------------------------------------------

   /** Builds a {@code set_variable_values} EditRequest that routes to setVariableValues(). */
   private static EditRequest setVariableValuesRequest(Map<String, String> variableValues) {
      return new EditRequest(
         "set_variable_values", null, null, null, null, null, null, null, null, null,
         null, null, null, false,
         null, null, null, null, null,
         null, null, null, null, null, null, null, null, null, null, null, null, null,
         null, null, null, null, null, null, null, null, null, null,
         null, null, null, variableValues,
         null, null,
         null, null,
         null, null, null,
         null, null,
         null, null, null, null, null, null, null
      );
   }

   private static DefaultVariableAssembly boundedChoiceVariable(
      Worksheet ws, String name, String type, Object[] choiceValues, int displayStyle)
   {
      AssetVariable var = new AssetVariable(name);
      var.setTypeNode(XSchema.createPrimitiveType(type));
      var.setChoices(choiceValues);
      var.setValues(choiceValues);
      var.setDisplayStyle(displayStyle);
      DefaultVariableAssembly assembly = new DefaultVariableAssembly(ws, name);
      assembly.setVariable(var);
      ws.addAssembly(assembly);
      return assembly;
   }

   @Test
   void setVariableValuesRejectsUnknownVariableName() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      Worksheet ws = new Worksheet();

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      when(rws.getAssetQuerySandbox()).thenReturn(mock(AssetQuerySandbox.class));

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      when(sessions.resolve(eq("TOK-SVV1"), any())).thenReturn(session("TOK-SVV1"));
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      WorksheetEditService editSvc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));

      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), editSvc, mock(WorksheetService.class));

      // L2-Group8: a typo'd/nonexistent variable name previously returned {ok:true} and did
      // nothing -- the canonical CLAUDE.md tool-misuse shape (silent no-op on a natural mistake).
      PairingException ex = assertThrows(PairingException.class, () -> ctrl.edit(
         "TOK-SVV1", setVariableValuesRequest(Map.of("TotallyNonexistentVar", "x")), agent));
      assertTrue(ex.getMessage().contains("TotallyNonexistentVar"), ex.getMessage());
   }

   @Test
   void setVariableValuesCoercesDeclaredType() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      Worksheet ws = new Worksheet();
      variableAssemblyForValues(ws, "NumVar", XSchema.INTEGER);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      AssetQuerySandbox box = mock(AssetQuerySandbox.class);
      when(rws.getAssetQuerySandbox()).thenReturn(box);

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      when(sessions.resolve(eq("TOK-SVV2"), any())).thenReturn(session("TOK-SVV2"));
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      WorksheetEditService editSvc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));

      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), editSvc, mock(WorksheetService.class));

      // L2-Group8: previously put every value into the VariableTable as a raw string, with no
      // type conversion at all -- VariableInputDialogService.initVariableInfos converts every
      // submitted string through this same CoreTool.getData(type, val, true) call first.
      ctrl.edit("TOK-SVV2", setVariableValuesRequest(Map.of("NumVar", "42")), agent);

      ArgumentCaptor<VariableTable> captor = ArgumentCaptor.forClass(VariableTable.class);
      verify(box).refreshVariableTable(captor.capture());
      assertEquals(42, captor.getValue().get("NumVar"),
         "the declared-integer variable's value must be a typed Integer, not the raw string");
   }

   @Test
   void setVariableValuesRejectsValueNotInBoundedChoiceList() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      Worksheet ws = new Worksheet();
      boundedChoiceVariable(ws, "ChoiceVar", XSchema.STRING,
         new Object[] {"East", "West"}, UserVariable.LIST);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      when(rws.getAssetQuerySandbox()).thenReturn(mock(AssetQuerySandbox.class));

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      when(sessions.resolve(eq("TOK-SVV3"), any())).thenReturn(session("TOK-SVV3"));
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      WorksheetEditService editSvc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));

      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), editSvc, mock(WorksheetService.class));

      // L2-Group8: a bounded-picker variable (combobox/list/radio/checkboxes) can only ever be
      // given one of its declared values through the native "Enter Parameters" prompt -- the
      // widget itself cannot render anything else.
      PairingException ex = assertThrows(PairingException.class, () -> ctrl.edit(
         "TOK-SVV3", setVariableValuesRequest(Map.of("ChoiceVar", "NotInList")), agent));
      assertTrue(ex.getMessage().contains("NotInList"), ex.getMessage());
      assertTrue(ex.getMessage().contains("ChoiceVar"), ex.getMessage());
   }

   @Test
   void setVariableValuesAllowsAnyValueForFreeTextVariable() throws Exception {
      // A free-text variable (displayStyle NONE) has no such restriction natively either -- a
      // backend guard must not make this tool *more* restrictive than the UI it is compared
      // against, even though the variable happens to carry a (non-enforced) choices list.
      Principal agent = TestPrincipals.user("alice", "host-org");
      Worksheet ws = new Worksheet();
      boundedChoiceVariable(ws, "FreeVar", XSchema.STRING,
         new Object[] {"East", "West"}, UserVariable.NONE);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      AssetQuerySandbox box = mock(AssetQuerySandbox.class);
      when(rws.getAssetQuerySandbox()).thenReturn(box);

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      when(sessions.resolve(eq("TOK-SVV4"), any())).thenReturn(session("TOK-SVV4"));
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      WorksheetEditService editSvc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));

      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), editSvc, mock(WorksheetService.class));

      ctrl.edit("TOK-SVV4", setVariableValuesRequest(Map.of("FreeVar", "AnythingGoes")), agent);

      ArgumentCaptor<VariableTable> captor = ArgumentCaptor.forClass(VariableTable.class);
      verify(box).refreshVariableTable(captor.capture());
      assertEquals("AnythingGoes", captor.getValue().get("FreeVar"));
   }

   private static DefaultVariableAssembly variableAssemblyForValues(
      Worksheet ws, String name, String type)
   {
      AssetVariable var = new AssetVariable(name);
      var.setTypeNode(XSchema.createPrimitiveType(type));
      DefaultVariableAssembly assembly = new DefaultVariableAssembly(ws, name);
      assembly.setVariable(var);
      ws.addAssembly(assembly);
      return assembly;
   }

   // ---------------------------------------------------------------------------
   // setProperties (L2-Group9)
   // ---------------------------------------------------------------------------

   @ParameterizedTest
   @CsvSource({
      "'Bad/Alias', does not allow",
      "'Bad<Alias>', does not allow",
      "'_LeadingUnderscore', letter or digit",
      // Review finding on PR #4920: Character.isLetterOrDigit is Unicode-aware and would accept
      // a Cyrillic/Greek/Hangul/etc. leading character, which the Angular validator this method
      // mirrors (assetNameStartWithCharDigit) never allowed -- only ASCII letters/digits, Latin-1
      // Supplement + Latin Extended-A, and CJK Unified Ideographs.
      "'Пример', letter or digit",
   })
   void setPropertiesRejectsInvalidAlias(String badAlias, String expectedFragment)
      throws Exception
   {
      Principal agent = TestPrincipals.user("alice", "host-org");
      Worksheet ws = new Worksheet();

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      when(sessions.resolve(eq("TOK-SP1"), any())).thenReturn(session("TOK-SP1"));
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      WorksheetEditService editSvc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));

      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), editSvc, mock(WorksheetService.class));

      // L2-Group9: assetEntryBannedCharacters/assetNameStartWithCharDigit are Angular-only --
      // not even WorksheetPropertyDialogService.process() re-validated before this fix.
      PairingException ex = assertThrows(PairingException.class, () -> ctrl.setProperties(
         "TOK-SP1", new WorksheetAgentController.WorksheetPropertiesRequest(badAlias, null),
         agent));
      assertTrue(ex.getMessage().toLowerCase().contains(expectedFragment.toLowerCase()),
         ex.getMessage());
      assertNull(ws.getWorksheetInfo().getAlias(),
         "a rejected alias must not have been applied");
   }

   @Test
   void setPropertiesAcceptsValidAlias() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      Worksheet ws = new Worksheet();

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      when(sessions.resolve(eq("TOK-SP2"), any())).thenReturn(session("TOK-SP2"));
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      WorksheetEditService editSvc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));

      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), editSvc, mock(WorksheetService.class));

      ctrl.setProperties("TOK-SP2",
         new WorksheetAgentController.WorksheetPropertiesRequest("Good Alias 1", null), agent);

      assertEquals("Good Alias 1", ws.getWorksheetInfo().getAlias());
   }

   /**
    * Review finding on PR #4920: the fixed start-character check must still accept everything
    * the Angular validator (assetNameStartWithCharDigit) allows -- CJK Unified Ideographs and
    * the Latin-1 Supplement/Latin Extended-A block -- not just plain ASCII.
    */
   @ParameterizedTest
   @CsvSource({
      "'报表别名'",
      "'Ā_LatinExtendedA'",
   })
   void setPropertiesAcceptsNonAsciiAliasWithinAllowedRanges(String goodAlias) throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      Worksheet ws = new Worksheet();

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      when(sessions.resolve(eq("TOK-SP3"), any())).thenReturn(session("TOK-SP3"));
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      WorksheetEditService editSvc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));

      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), editSvc, mock(WorksheetService.class));

      ctrl.setProperties("TOK-SP3",
         new WorksheetAgentController.WorksheetPropertiesRequest(goodAlias, null), agent);

      assertEquals(goodAlias, ws.getWorksheetInfo().getAlias());
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
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));

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
    * PR #4901 round-2 review follow-up: an audit for other bypasses of the round-1
    * {@code requireStorableName} guard (added for {@code placeAssembly}/{@code renameTable}/
    * {@code addJoin}/{@code duplicateAssembly}/{@code renameVariable}) turned up a third site:
    * {@code createEmbeddedTable}'s {@code name} param -- the import's caller-supplied table
    * name -- is used verbatim and, unlike {@code add_table}'s datasource-bound paths, is never
    * run through {@code AssetUtil.normalizeTable} either. {@code AssemblyInfo:254} writes the
    * name into a CDATA section verbatim, so a name containing the terminator closes it early and
    * leaves malformed XML in storage.
    */
   @Test
   void importCsvRefusesANameThatWouldBreakTheStoredXml() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      Worksheet ws = new Worksheet();
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      when(rws.getAssetQuerySandbox()).thenReturn(mock(AssetQuerySandbox.class));

      WorksheetAgentController ctrl = importController("TOK-CSV-CDATA", rws);

      PairingException ex = assertThrows(PairingException.class,
         () -> ctrl.importCsv("TOK-CSV-CDATA",
            new WorksheetAgentController.ImportCsvRequest("bad]]>name", "a,b\n1,x\n2,y"), agent));
      assertTrue(ex.getMessage().contains("]]>"), ex.getMessage());
      assertNull(ws.getAssembly("bad]]>name"));
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

   // ---------------------------------------------------------------------------
   // stringColumns / stringColumnIndexes (L2-Group6 flagship finding)
   // ---------------------------------------------------------------------------

   @Test
   void importCsvStringColumnsPreservesLeadingZerosWithoutBreakingOtherNumericColumn()
      throws Exception
   {
      Worksheet ws = new Worksheet();
      WorksheetAgentController ctrl = importCtrl(ws, "TOK-SC1");

      // The audit's exact reproduction: a zero-padded ZIP code alongside a genuinely-numeric
      // column. detectType=true alone would strip ZIP's leading zeros; detectType=false alone
      // would turn AMOUNT into a string too. stringColumns lets ZIP opt out without touching
      // AMOUNT.
      ctrl.importCsv("TOK-SC1", new WorksheetAgentController.ImportCsvRequest(
            "ZipTest", "ZIP,AMOUNT\n00501,10.50\n00544,20.75\n10001,30.00",
            null, null, null, true, null, null, null, null, null,
            List.of("ZIP"), null),
         TestPrincipals.user("alice", "host-org"));

      EmbeddedTableAssembly t = importedTable(ws, "ZipTest");
      ColumnSelection cols = t.getColumnSelection(false);
      assertEquals(XSchema.STRING, ((ColumnRef) cols.getAttribute("ZIP")).getDataType(),
         "ZIP must stay a string column");
      assertNotEquals(XSchema.STRING, ((ColumnRef) cols.getAttribute("AMOUNT")).getDataType(),
         "AMOUNT must still be detected as numeric -- the whole point of a per-column override");
      assertEquals("00501", String.valueOf(t.getEmbeddedData().getObject(1, 0)),
         "the leading zeros must survive");
   }

   @Test
   void importCsvStringColumnIndexesResolvesSameColumnAsStringColumns() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetAgentController ctrl = importCtrl(ws, "TOK-SC2");
      String csv = "ZIP,AMOUNT\n00501,10.50";

      ctrl.importCsv("TOK-SC2", new WorksheetAgentController.ImportCsvRequest(
            "ByName", csv, null, null, null, true, null, null, null, null, null,
            List.of("ZIP"), null),
         TestPrincipals.user("alice", "host-org"));
      ctrl.importCsv("TOK-SC2", new WorksheetAgentController.ImportCsvRequest(
            "ByIndex", csv, null, null, null, true, null, null, null, null, null,
            null, List.of(0)),
         TestPrincipals.user("alice", "host-org"));

      assertEquals("00501",
         String.valueOf(importedTable(ws, "ByName").getEmbeddedData().getObject(1, 0)));
      assertEquals("00501",
         String.valueOf(importedTable(ws, "ByIndex").getEmbeddedData().getObject(1, 0)),
         "stringColumnIndexes must resolve to the same result as the equivalent stringColumns");
   }

   @Test
   void importCsvStringColumnsRejectsUnknownColumnName() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetAgentController ctrl = importCtrl(ws, "TOK-SC3");

      ResponseStatusException ex = assertThrows(ResponseStatusException.class,
         () -> ctrl.importCsv("TOK-SC3", new WorksheetAgentController.ImportCsvRequest(
               "X", "ZIP,AMOUNT\n00501,10.50", null, null, null, null, null, null, null, null,
               null, List.of("NoSuchColumn"), null),
            TestPrincipals.user("alice", "host-org")));

      assertEquals(400, ex.getStatusCode().value());
      assertTrue(ex.getReason().contains("NoSuchColumn"), ex.getReason());
      assertTrue(ex.getReason().contains("ZIP") && ex.getReason().contains("AMOUNT"),
         "the refusal should name the file's actual columns: " + ex.getReason());
   }

   @Test
   void importCsvStringColumnIndexesRejectsOutOfRangeIndex() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetAgentController ctrl = importCtrl(ws, "TOK-SC4");

      ResponseStatusException ex = assertThrows(ResponseStatusException.class,
         () -> ctrl.importCsv("TOK-SC4", new WorksheetAgentController.ImportCsvRequest(
               "X", "ZIP,AMOUNT\n00501,10.50", null, null, null, null, null, null, null, null,
               null, null, List.of(5)),
            TestPrincipals.user("alice", "host-org")));

      assertEquals(400, ex.getStatusCode().value());
      assertTrue(ex.getReason().contains("5"), ex.getReason());
   }

   @Test
   void importCsvStringColumnsRejectedWithUnpivot() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetAgentController ctrl = importCtrl(ws, "TOK-SC5");

      ResponseStatusException ex = assertThrows(ResponseStatusException.class,
         () -> ctrl.importCsv("TOK-SC5", new WorksheetAgentController.ImportCsvRequest(
               "X", "id,q1,q2\n1,10,20", null, null, null, null, null, null, true, null,
               null, List.of("q1"), null),
            TestPrincipals.user("alice", "host-org")));

      assertEquals(400, ex.getStatusCode().value());
      assertTrue(ex.getReason().contains("unpivot"), ex.getReason());
   }

   @Test
   void importCsvFileHonoursStringColumns() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetAgentController ctrl = importCtrl(ws, "TOK-SC6");

      ctrl.importCsvFile("TOK-SC6",
         new MockMultipartFile("file", "d.csv", "text/csv",
                               "ZIP,AMOUNT\n00501,10.50".getBytes()),
         "ZipFile", null, null, null, true, null, null, null, null, null,
         List.of("ZIP"), null,
         TestPrincipals.user("alice", "host-org"));

      assertEquals("00501",
         String.valueOf(importedTable(ws, "ZipFile").getEmbeddedData().getObject(1, 0)),
         "the multipart route must plumb stringColumns through the same way the JSON route does");
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
            null, null,
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
            null, null,
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
         null, null,
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
         () -> ctrl.importExcel("TOK", null, "XLSX", null, null, null, null, null,
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
         () -> ctrl.importExcel("TOK", file, "PDF", null, null, null, null, null,
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
            () -> ctrl.importExcel("TOK", file, "XLSX", null, null, null, null, null,
                                   TestPrincipals.user("alice", "host-org")));
         assertEquals(400, ex.getStatusCode().value());
      }
   }

   // ---------------------------------------------------------------------------
   // Import file-size cap precedence (CSV must not honour excel.import.max)
   // ---------------------------------------------------------------------------

   @Test
   void importCsvIgnoresExcelImportMax() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      Worksheet ws = new Worksheet();
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      when(rws.getAssetQuerySandbox()).thenReturn(mock(AssetQuerySandbox.class));

      WorksheetAgentController ctrl = importController("TOK-CSV-NOEXCELMAX", rws);

      try(MockedStatic<SreeEnv> sreeEnv = mockStatic(SreeEnv.class, CALLS_REAL_METHODS)) {
         // Tiny enough that honouring it (the pre-fix, excel-first-then-csv behavior) would
         // reject this CSV. csv.import.max is left unset.
         sreeEnv.when(() -> SreeEnv.getProperty("excel.import.max")).thenReturn("1");

         WorksheetAgentController.ImportCsvResponse resp = ctrl.importCsv("TOK-CSV-NOEXCELMAX",
            new WorksheetAgentController.ImportCsvRequest("Imported", "a,b\n1,x\n2,y"), agent);

         assertEquals("Imported", resp.tableName());
      }
   }

   @Test
   void importCsvStillHonorsCsvImportMax() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      Worksheet ws = new Worksheet();
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      when(rws.getAssetQuerySandbox()).thenReturn(mock(AssetQuerySandbox.class));

      WorksheetAgentController ctrl = importController("TOK-CSV-CSVMAX", rws);

      try(MockedStatic<SreeEnv> sreeEnv = mockStatic(SreeEnv.class, CALLS_REAL_METHODS)) {
         sreeEnv.when(() -> SreeEnv.getProperty("csv.import.max")).thenReturn("1");

         ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> ctrl.importCsv("TOK-CSV-CSVMAX",
               new WorksheetAgentController.ImportCsvRequest("Imported", "a,b\n1,x\n2,y"), agent));
         assertEquals(400, ex.getStatusCode().value());
      }
   }

   @Test
   void importCsvTreatsMalformedCsvImportMaxAsUnbounded() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      Worksheet ws = new Worksheet();
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      when(rws.getAssetQuerySandbox()).thenReturn(mock(AssetQuerySandbox.class));

      WorksheetAgentController ctrl = importController("TOK-CSV-BADMAX", rws);

      try(MockedStatic<SreeEnv> sreeEnv = mockStatic(SreeEnv.class, CALLS_REAL_METHODS)) {
         sreeEnv.when(() -> SreeEnv.getProperty("csv.import.max")).thenReturn("not-a-number");

         WorksheetAgentController.ImportCsvResponse resp = ctrl.importCsv("TOK-CSV-BADMAX",
            new WorksheetAgentController.ImportCsvRequest("Imported", "a,b\n1,x\n2,y"), agent);

         assertEquals("Imported", resp.tableName());
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

   /** C2(a): detach must also notify the tab bar the agent is no longer attached. */
   @Test
   void detachNotifiesTabBar() {
      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetAgentBroadcastService broadcast = mock(SheetAgentBroadcastService.class);
      Principal agent = TestPrincipals.user("alice", "host-org");
      JoinSession s = session("TOK-D");
      when(sessions.resolve(eq("TOK-D"), any())).thenReturn(s);

      WorksheetAgentController ctrl = new WorksheetAgentController(featureOff(),
         mock(SheetJoinService.class), sessions,
         mock(WorksheetReadService.class), mock(WorksheetEditService.class),
         mock(WorksheetService.class), mock(WorksheetPreviewService.class), broadcast,
         mock(inetsoft.uql.XRepository.class), mock(inetsoft.uql.asset.AssetRepository.class),
         mock(inetsoft.web.wiz.service.MetadataApiService.class),
         mock(inetsoft.web.portal.controller.database.QueryManagerService.class),
         mock(inetsoft.web.composer.ws.LayoutGraphService.class),
         mock(inetsoft.web.portal.controller.database.DataSourceService.class),
         mock(inetsoft.sree.security.SecurityEngine.class),
         mock(inetsoft.uql.asset.sync.RenameTransformHandler.class));

      ctrl.detach("TOK-D", agent);

      verify(broadcast).sendAgentInactive(s);
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

   /**
    * Regression for Bug PVA-005: {@code addBoundTable} used to call the 2-arg
    * {@code AssetUtil.getNextName(ws, prefix)} overload, which hardcodes {@code preferred = null}
    * and so always appended a "1" suffix even when the plain table name was free. With no
    * colliding assembly on the worksheet, {@code add_table(table:"ORDERS", ...)} must create an
    * assembly literally named {@code "ORDERS"}, not {@code "ORDERS1"}.
    */
   @Test
   void addBoundTableUsesPlainNameWhenNoCollision() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      WorksheetEditService editSvc = mock(WorksheetEditService.class);
      SecurityEngine securityEngine = mock(SecurityEngine.class);
      MetadataApiService metadataApiService = mock(MetadataApiService.class);
      DataSourceService dataSourceService = mock(DataSourceService.class);

      when(securityEngine.checkPermission(eq(agent), eq(ResourceType.PHYSICAL_TABLE),
                                          eq("*"), eq(ResourceAction.ACCESS)))
         .thenReturn(true);
      when(dataSourceService.checkPermission(eq("MyDatasource"), eq(ResourceAction.READ), eq(agent)))
         .thenReturn(true);

      JDBCDataSource jdbcDs = mock(JDBCDataSource.class);
      when(metadataApiService.getJDBCDatasource("MyDatasource")).thenReturn(jdbcDs);

      XNode tableMetaData = new XNode("ORDERS");
      tableMetaData.setAttribute("type", "TABLE");
      when(metadataApiService.getTableMetaData(eq(jdbcDs), isNull(), isNull(), eq("ORDERS")))
         .thenReturn(tableMetaData);

      DatabaseTableMeta tableMeta = new DatabaseTableMeta();
      tableMeta.setColumns(new ArrayList<>());
      when(metadataApiService.getTableDetails(eq("MyDatasource"), eq("ORDERS"), isNull(), isNull(),
                                              eq(agent)))
         .thenReturn(tableMeta);

      Worksheet ws = new Worksheet();
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      when(editSvc.applyOnRuntime(eq("TOK-BT4"), eq(agent), any())).thenAnswer(inv -> {
         WorksheetEditService.ThrowingFunction<RuntimeWorksheet, ?> fn = inv.getArgument(2);
         return fn.apply(rws);
      });

      WorksheetAgentController ctrl = securityController(editSvc,
         dataSourceService, securityEngine, metadataApiService,
         mock(XRepository.class), mock(QueryManagerService.class));

      EditRequest req = addBoundTableRequest("ORDERS", "MyDatasource");

      try(MockedStatic<SQLTypes> sqlTypes = mockStatic(SQLTypes.class)) {
         SQLTypes types = mock(SQLTypes.class);
         sqlTypes.when(() -> SQLTypes.getSQLTypes(jdbcDs)).thenReturn(types);
         when(types.getQualifiedName(eq(tableMetaData), eq(jdbcDs))).thenReturn("ORDERS");

         ctrl.edit("TOK-BT4", req, agent);
      }

      assertNotNull(ws.getAssembly("ORDERS"),
         "with no collision the new table must keep the plain name, not 'ORDERS1'");
      assertNull(ws.getAssembly("ORDERS1"),
         "no 'ORDERS1'-suffixed assembly should be created when 'ORDERS' was free");
   }

   /**
    * Companion to {@link #addBoundTableUsesPlainNameWhenNoCollision()}: proves the fix did not
    * remove the genuine-collision fallback — when {@code "ORDERS"} is already taken, the new
    * table must still be named {@code "ORDERS1"}.
    */
   @Test
   void addBoundTableStillSuffixesOnRealCollision() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      WorksheetEditService editSvc = mock(WorksheetEditService.class);
      SecurityEngine securityEngine = mock(SecurityEngine.class);
      MetadataApiService metadataApiService = mock(MetadataApiService.class);
      DataSourceService dataSourceService = mock(DataSourceService.class);

      when(securityEngine.checkPermission(eq(agent), eq(ResourceType.PHYSICAL_TABLE),
                                          eq("*"), eq(ResourceAction.ACCESS)))
         .thenReturn(true);
      when(dataSourceService.checkPermission(eq("MyDatasource"), eq(ResourceAction.READ), eq(agent)))
         .thenReturn(true);

      JDBCDataSource jdbcDs = mock(JDBCDataSource.class);
      when(metadataApiService.getJDBCDatasource("MyDatasource")).thenReturn(jdbcDs);

      XNode tableMetaData = new XNode("ORDERS");
      tableMetaData.setAttribute("type", "TABLE");
      when(metadataApiService.getTableMetaData(eq(jdbcDs), isNull(), isNull(), eq("ORDERS")))
         .thenReturn(tableMetaData);

      DatabaseTableMeta tableMeta = new DatabaseTableMeta();
      tableMeta.setColumns(new ArrayList<>());
      when(metadataApiService.getTableDetails(eq("MyDatasource"), eq("ORDERS"), isNull(), isNull(),
                                              eq(agent)))
         .thenReturn(tableMeta);

      Worksheet ws = new Worksheet();
      ws.addAssembly(new PhysicalBoundTableAssembly(ws, "ORDERS"));
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      when(editSvc.applyOnRuntime(eq("TOK-BT5"), eq(agent), any())).thenAnswer(inv -> {
         WorksheetEditService.ThrowingFunction<RuntimeWorksheet, ?> fn = inv.getArgument(2);
         return fn.apply(rws);
      });

      WorksheetAgentController ctrl = securityController(editSvc,
         dataSourceService, securityEngine, metadataApiService,
         mock(XRepository.class), mock(QueryManagerService.class));

      EditRequest req = addBoundTableRequest("ORDERS", "MyDatasource");

      try(MockedStatic<SQLTypes> sqlTypes = mockStatic(SQLTypes.class)) {
         SQLTypes types = mock(SQLTypes.class);
         sqlTypes.when(() -> SQLTypes.getSQLTypes(jdbcDs)).thenReturn(types);
         when(types.getQualifiedName(eq(tableMetaData), eq(jdbcDs))).thenReturn("ORDERS");

         ctrl.edit("TOK-BT5", req, agent);
      }

      assertNotNull(ws.getAssembly("ORDERS1"),
         "with a real collision on 'ORDERS' the new table must fall back to 'ORDERS1'");
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
   // add_table with a logicalModel — naming for addLogicalModelTable()
   // ---------------------------------------------------------------------------

   /**
    * Regression for Bug PVA-005: {@code addLogicalModelTable} carries the identical
    * wrong-overload {@code AssetUtil.getNextName(ws, prefix)} bug as {@code addBoundTable}
    * (same file, a different call site) -- with no colliding assembly on the worksheet,
    * {@code add_table(table:"Customer", datasource:..., logicalModel:...)} must create an
    * assembly literally named {@code "Customer"}, not {@code "Customer1"}.
    */
   @Test
   void addLogicalModelTableUsesPlainNameWhenNoCollision() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      XEntity customerEntity = new XEntity("Customer");
      customerEntity.addAttribute(new XAttribute("Name", "SA.CUSTOMERS", "NAME", XSchema.STRING));
      XLogicalModel orderModel = new XLogicalModel("Order Model");
      orderModel.addEntity(customerEntity);

      // See addNamedGroupWithLogicalModelBuildsRealDatasourceSourceInfo() above for why the
      // XDataModel/logical model lookup is mocked directly rather than exercised through a real
      // DataSourceRegistry-backed XDataModel.
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
      when(editSvc.applyOnRuntime(eq("TOK-LM1"), eq(agent), any())).thenAnswer(inv -> {
         WorksheetEditService.ThrowingFunction<RuntimeWorksheet, ?> fn = inv.getArgument(2);
         return fn.apply(rws);
      });

      WorksheetAgentController ctrl = securityController(editSvc,
         dataSourceService, mock(SecurityEngine.class), mock(MetadataApiService.class),
         mock(XRepository.class), mock(QueryManagerService.class));

      EditRequest req = addTabularTableRequest("Customer", "Examples/Orders", "Order Model",
         null, null, null, null, null, null, null, null, null);

      ctrl.edit("TOK-LM1", req, agent);

      assertNotNull(ws.getAssembly("Customer"),
         "with no collision the new entity table must keep the plain name, not 'Customer1'");
      assertNull(ws.getAssembly("Customer1"),
         "no 'Customer1'-suffixed assembly should be created when 'Customer' was free");
   }

   /**
    * Companion to {@link #addLogicalModelTableUsesPlainNameWhenNoCollision()}: proves the fix did
    * not remove the genuine-collision fallback for this call site either.
    */
   @Test
   void addLogicalModelTableStillSuffixesOnRealCollision() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      XEntity customerEntity = new XEntity("Customer");
      customerEntity.addAttribute(new XAttribute("Name", "SA.CUSTOMERS", "NAME", XSchema.STRING));
      XLogicalModel orderModel = new XLogicalModel("Order Model");
      orderModel.addEntity(customerEntity);

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
      ws.addAssembly(new BoundTableAssembly(ws, "Customer"));
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);

      WorksheetEditService editSvc = mock(WorksheetEditService.class);
      when(editSvc.applyOnRuntime(eq("TOK-LM2"), eq(agent), any())).thenAnswer(inv -> {
         WorksheetEditService.ThrowingFunction<RuntimeWorksheet, ?> fn = inv.getArgument(2);
         return fn.apply(rws);
      });

      WorksheetAgentController ctrl = securityController(editSvc,
         dataSourceService, mock(SecurityEngine.class), mock(MetadataApiService.class),
         mock(XRepository.class), mock(QueryManagerService.class));

      EditRequest req = addTabularTableRequest("Customer", "Examples/Orders", "Order Model",
         null, null, null, null, null, null, null, null, null);

      ctrl.edit("TOK-LM2", req, agent);

      assertNotNull(ws.getAssembly("Customer1"),
         "with a real collision on 'Customer' the new entity table must fall back to 'Customer1'");
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

   /**
    * PR #4901 round-2 review follow-up: {@code addDatasourceScopedNamedGroup} builds a
    * {@code DefaultNamedGroupAssembly} and adds it directly, the same unescaped-CDATA write path
    * {@code createVariable} and the round-1 sites ({@code addJoin}/{@code duplicateAssembly}/
    * {@code renameVariable}) had before being guarded. Checked up front, before any permission
    * check or datasource/logical-model lookup, so a doomed name fails fast without touching
    * either mock.
    */
   @Test
   void addNamedGroupRefusesANameThatWouldBreakTheStoredXml() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService editSvc = mock(WorksheetEditService.class);
      DataSourceService dataSourceService = mock(DataSourceService.class);

      WorksheetAgentController ctrl = securityController(editSvc,
         dataSourceService, mock(SecurityEngine.class), mock(MetadataApiService.class),
         mock(XRepository.class), mock(QueryManagerService.class));

      EditRequest req = namedGroupDatasourceRequest(
         "bad]]>name", "Examples/Orders", "Order Model", null, null,
         "Customer", "State", List.of(), false);

      PairingException ex = assertThrows(PairingException.class,
         () -> ctrl.edit("TOK-NGD-CDATA", req, agent));
      assertTrue(ex.getMessage().contains("]]>"), ex.getMessage());
      verifyNoInteractions(dataSourceService);
      verifyNoInteractions(editSvc);
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
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));

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

   /**
    * Bug #76350 follow-on (item A), review round 1: {@code editSqlQuery} calls
    * {@code refreshColumnSelection} unwrapped after committing the new SQL/column selection --
    * the same unbounded-hang shape as {@code refreshData}'s single-table branch (3a), reachable
    * on any SQL-bound table since {@code set_group_aggregate} can make one crosstab-shaped.
    * {@code queryManagerService.getColumnSelection} is stubbed directly (bypassing real JDBC
    * metadata resolution) and the SQL string has no {@code FROM} clause, so
    * {@code JDBCUtil.fixUniformSQLInfo}'s in-memory shortcut ({@code sql.getTableCount() <= 0})
    * fires without a live datasource or a {@code Config} Spring bean -- letting the test reach
    * the wrap without a live JDBC connection, matching this class's existing "stop before real
    * query execution" convention for SQL-query tests (e.g.
    * {@code addSqlQueryGrantedPassesBothPermissionGates}, which also uses a table-less
    * {@code "SELECT 1"}).
    */
   @Test
   void editSqlQueryThrowsRenderNotReadyWhenColumnSelectionIsSlow() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      Worksheet ws = new Worksheet();
      SQLBoundTableAssembly sqlt = new SQLBoundTableAssembly(ws, "SqlTable1");
      ((SQLBoundTableAssemblyInfo) sqlt.getInfo()).setQuery(new JDBCQuery());
      ws.addAssembly(sqlt);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      AssetQuerySandbox box = mock(AssetQuerySandbox.class);
      when(rws.getAssetQuerySandbox()).thenReturn(box);
      doAnswer(invocation -> {
         Thread.sleep(3_000);
         return null;
      }).when(box).refreshColumnSelection(eq("SqlTable1"), anyBoolean());

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      when(sessions.resolve(eq("TOK-ES4"), any())).thenReturn(session("TOK-ES4"));
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      WorksheetEditService editSvc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));

      SecurityEngine securityEngine = mock(SecurityEngine.class);
      when(securityEngine.checkPermission(eq(agent), eq(ResourceType.FREE_FORM_SQL),
                                          eq("*"), eq(ResourceAction.ACCESS)))
         .thenReturn(true);

      QueryManagerService queryManagerService = mock(QueryManagerService.class);
      ColumnSelection newColumns = new ColumnSelection();
      newColumns.addAttribute(new ColumnRef(new AttributeRef(null, "a")));
      newColumns.addAttribute(new ColumnRef(new AttributeRef(null, "b")));
      when(queryManagerService.getColumnSelection(any(), any(), any(), any(), any()))
         .thenReturn(newColumns);

      WorksheetAgentController ctrl = securityController(editSvc, mock(DataSourceService.class),
         securityEngine, mock(MetadataApiService.class), mock(XRepository.class),
         queryManagerService);

      EditRequest req = editSqlQueryRequest("SqlTable1", "SELECT 1");

      assertThrows(RenderNotReadyException.class,
         () -> ctrl.edit("TOK-ES4", req, agent));
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

   /**
    * PR #4901 round-2 review follow-up: like {@code createEmbeddedTable}, {@code addSqlQuery}'s
    * {@code name} is the caller-supplied table name used verbatim -- never run through
    * {@code AssetUtil.normalizeTable} -- and is written directly into a new
    * {@code SQLBoundTableAssembly} added to the worksheet. Checked as the very first thing inside
    * the {@code applyOnRuntime} callback, before any SQL parsing or JDBC metadata work, so a
    * doomed name fails fast without needing the SQL to actually parse.
    */
   @Test
   void addSqlQueryRefusesANameThatWouldBreakTheStoredXml() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      SecurityEngine securityEngine = mock(SecurityEngine.class);
      DataSourceService dataSourceService = mock(DataSourceService.class);
      XRepository xrepository = mock(XRepository.class);

      when(securityEngine.checkPermission(eq(agent), eq(ResourceType.FREE_FORM_SQL),
                                          eq("*"), eq(ResourceAction.ACCESS)))
         .thenReturn(true);
      when(dataSourceService.checkPermission(eq("MyDatasource"), eq(ResourceAction.READ), eq(agent)))
         .thenReturn(true);

      // Mocked: constructing a real JDBCDataSource pulls a CredentialService bean the
      // lightweight test context does not provide (see editSqlQueryDeniedByDatasourceReadThrows-
      // PairingException above), and requireStorableName throws before this datasource's
      // metadata is ever touched.
      JDBCDataSource jdbcDs = mock(JDBCDataSource.class);
      when(xrepository.getDataSource("MyDatasource")).thenReturn(jdbcDs);

      Worksheet ws = new Worksheet();
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);

      WorksheetEditService editSvc = mock(WorksheetEditService.class);
      when(editSvc.applyOnRuntime(eq("TOK-SQ-CDATA"), eq(agent), any())).thenAnswer(inv -> {
         WorksheetEditService.ThrowingFunction<RuntimeWorksheet, ?> fn = inv.getArgument(2);
         return fn.apply(rws);
      });

      WorksheetAgentController ctrl = securityController(editSvc,
         dataSourceService, securityEngine, mock(MetadataApiService.class),
         xrepository, mock(QueryManagerService.class));

      WorksheetAgentController.SqlQueryRequest body =
         new WorksheetAgentController.SqlQueryRequest("MyDatasource", "SELECT 1", "bad]]>name");

      PairingException ex = assertThrows(PairingException.class,
         () -> ctrl.addSqlQuery("TOK-SQ-CDATA", body, agent));
      assertTrue(ex.getMessage().contains("]]>"), ex.getMessage());
      assertNull(ws.getAssembly("bad]]>name"));
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
   // save -- PVA-003: Save-As silently orphans a connected viewsheet's base pointer
   // ---------------------------------------------------------------------------

   /**
    * Regression for Bug PVA-003: {@code save(name:...)} on an already-saved worksheet used to
    * never look at any connected {@link RuntimeViewsheet}, so a viewsheet whose
    * {@code Viewsheet.getBaseEntry()} pointed at the pre-Save-As entry kept pointing at it
    * indefinitely, with no warning. The fix does not repoint the viewsheet (no such tool exists
    * yet) but must surface a warning naming the affected, still-open viewsheet.
    */
   @Test
   void saveAsWarnsWhenAConnectedViewsheetStillPointsAtTheOldEntry() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      AssetEntry oldEntry = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
         AssetEntry.Type.WORKSHEET, "Orders WS", null);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getEntry()).thenReturn(oldEntry);
      when(rws.getWorksheet()).thenReturn(new Worksheet());
      when(rws.getCurrent()).thenReturn(2);

      WorksheetEditService edit = mock(WorksheetEditService.class);
      when(edit.resolveWithSession(eq("TOK-SAVE1"), eq(agent)))
         .thenReturn(new WorksheetEditService.ResolvedSession(rws, "rt-ws-1"));

      Viewsheet vs = mock(Viewsheet.class);
      when(vs.getBaseEntry()).thenReturn(oldEntry);
      RuntimeViewsheet connectedRvs = mock(RuntimeViewsheet.class);
      when(connectedRvs.getViewsheet()).thenReturn(vs);
      when(connectedRvs.getID()).thenReturn("rt-vs-1");

      WorksheetService ws = mock(WorksheetService.class);
      when(ws.getRuntimeSheets(eq(agent)))
         .thenReturn(new RuntimeSheet[]{ connectedRvs });

      WorksheetAgentController ctrl = controller(featureOn(), mock(SheetJoinService.class),
         mock(SheetSessionService.class), mock(WorksheetReadService.class), edit, ws);

      Map<String, Object> result = ctrl.save("TOK-SAVE1",
         new WorksheetAgentController.SaveRequest("Orders WS Copy", null), agent);

      assertEquals(Boolean.TRUE, result.get("ok"));
      Object warning = result.get("warning");
      assertNotNull(warning, "a connected viewsheet still pointing at the old entry must be " +
         "surfaced as a warning");
      assertTrue(warning.toString().contains("rt-vs-1"),
         "the warning should name the affected viewsheet runtime: " + warning);
   }

   /** No connected viewsheet, no warning -- the common case stays a plain success. */
   @Test
   void saveAsWithNoConnectedViewsheetHasNoWarning() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      AssetEntry oldEntry = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
         AssetEntry.Type.WORKSHEET, "Orders WS", null);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getEntry()).thenReturn(oldEntry);
      when(rws.getWorksheet()).thenReturn(new Worksheet());
      when(rws.getCurrent()).thenReturn(1);

      WorksheetEditService edit = mock(WorksheetEditService.class);
      when(edit.resolveWithSession(eq("TOK-SAVE2"), eq(agent)))
         .thenReturn(new WorksheetEditService.ResolvedSession(rws, "rt-ws-2"));

      WorksheetService ws = mock(WorksheetService.class);
      when(ws.getRuntimeSheets(eq(agent))).thenReturn(new RuntimeSheet[0]);

      WorksheetAgentController ctrl = controller(featureOn(), mock(SheetJoinService.class),
         mock(SheetSessionService.class), mock(WorksheetReadService.class), edit, ws);

      Map<String, Object> result = ctrl.save("TOK-SAVE2",
         new WorksheetAgentController.SaveRequest("Orders WS Copy", null), agent);

      assertEquals(Boolean.TRUE, result.get("ok"));
      assertNull(result.get("warning"));
   }

   /** A first save out of TEMPORARY_SCOPE is not a Save-As -- no connected-viewsheet lookup at all. */
   @Test
   void firstSaveOutOfTemporaryScopeDoesNotCheckForConnectedViewsheets() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      AssetEntry tempEntry = new AssetEntry(AssetRepository.TEMPORARY_SCOPE,
         AssetEntry.Type.WORKSHEET, "__TEMPORARY__/ws-1", null);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getEntry()).thenReturn(tempEntry);
      when(rws.getWorksheet()).thenReturn(new Worksheet());
      when(rws.getCurrent()).thenReturn(0);

      WorksheetEditService edit = mock(WorksheetEditService.class);
      when(edit.resolveWithSession(eq("TOK-SAVE3"), eq(agent)))
         .thenReturn(new WorksheetEditService.ResolvedSession(rws, "rt-ws-3"));

      WorksheetService ws = mock(WorksheetService.class);

      WorksheetAgentController ctrl = controller(featureOn(), mock(SheetJoinService.class),
         mock(SheetSessionService.class), mock(WorksheetReadService.class), edit, ws);

      Map<String, Object> result = ctrl.save("TOK-SAVE3",
         new WorksheetAgentController.SaveRequest("agent_ws_1", null), agent);

      assertEquals(Boolean.TRUE, result.get("ok"));
      assertNull(result.get("warning"));
      verify(ws, never()).getRuntimeSheets(any());
   }

   // ---------------------------------------------------------------------------
   // save -- L2-Group10: duplicate-name/overwrite confirmation, control-char sanitization
   // ---------------------------------------------------------------------------

   @Test
   void saveRejectsDuplicateNameWithoutConfirmation() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      AssetEntry oldEntry = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
         AssetEntry.Type.WORKSHEET, "Orders WS", null);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getEntry()).thenReturn(oldEntry);
      when(rws.getWorksheet()).thenReturn(new Worksheet());
      when(rws.getCurrent()).thenReturn(1);

      WorksheetEditService edit = mock(WorksheetEditService.class);
      when(edit.resolveWithSession(eq("TOK-SAVE-DUP1"), eq(agent)))
         .thenReturn(new WorksheetEditService.ResolvedSession(rws, "rt-ws-dup1"));

      WorksheetService ws = mock(WorksheetService.class);
      when(ws.getRuntimeSheets(eq(agent))).thenReturn(new RuntimeSheet[0]);
      when(ws.isDuplicatedEntry(any(), any())).thenReturn(true);

      WorksheetAgentController ctrl = controller(featureOn(), mock(SheetJoinService.class),
         mock(SheetSessionService.class), mock(WorksheetReadService.class), edit, ws);

      // L2-Group10: SaveWorksheetDialogService.process0()'s Save-As dialog refuses (pending
      // confirmation) when isDuplicatedEntry() reports the target already exists -- this agent
      // path previously called setWorksheet unconditionally, silently overwriting whatever
      // "Orders WS Copy" already held.
      PairingException ex = assertThrows(PairingException.class, () -> ctrl.save("TOK-SAVE-DUP1",
         new WorksheetAgentController.SaveRequest("Orders WS Copy", null), agent));
      assertTrue(ex.getMessage().contains("already exists"), ex.getMessage());
      verify(ws, never()).setWorksheet(any(), any(), any(), anyBoolean(), anyBoolean());
   }

   @Test
   void saveOverwritesDuplicateNameWhenConfirmed() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      AssetEntry oldEntry = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
         AssetEntry.Type.WORKSHEET, "Orders WS", null);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getEntry()).thenReturn(oldEntry);
      when(rws.getWorksheet()).thenReturn(new Worksheet());
      when(rws.getCurrent()).thenReturn(1);

      WorksheetEditService edit = mock(WorksheetEditService.class);
      when(edit.resolveWithSession(eq("TOK-SAVE-DUP2"), eq(agent)))
         .thenReturn(new WorksheetEditService.ResolvedSession(rws, "rt-ws-dup2"));

      WorksheetService ws = mock(WorksheetService.class);
      when(ws.getRuntimeSheets(eq(agent))).thenReturn(new RuntimeSheet[0]);
      when(ws.isDuplicatedEntry(any(), any())).thenReturn(true);

      WorksheetAgentController ctrl = controller(featureOn(), mock(SheetJoinService.class),
         mock(SheetSessionService.class), mock(WorksheetReadService.class), edit, ws);

      Map<String, Object> result = ctrl.save("TOK-SAVE-DUP2",
         new WorksheetAgentController.SaveRequest("Orders WS Copy", null, true), agent);

      assertEquals(Boolean.TRUE, result.get("ok"));
      verify(ws).setWorksheet(any(), any(), any(), anyBoolean(), anyBoolean());
   }

   @Test
   void saveSanitizesControlCharactersInName() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      AssetEntry tempEntry = new AssetEntry(AssetRepository.TEMPORARY_SCOPE,
         AssetEntry.Type.WORKSHEET, "__TEMPORARY__/ws-1", null);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getEntry()).thenReturn(tempEntry);
      when(rws.getWorksheet()).thenReturn(new Worksheet());
      when(rws.getCurrent()).thenReturn(0);

      WorksheetEditService edit = mock(WorksheetEditService.class);
      when(edit.resolveWithSession(eq("TOK-SAVE-CTRL"), eq(agent)))
         .thenReturn(new WorksheetEditService.ResolvedSession(rws, "rt-ws-ctrl"));

      WorksheetService ws = mock(WorksheetService.class);

      WorksheetAgentController ctrl = controller(featureOn(), mock(SheetJoinService.class),
         mock(SheetSessionService.class), mock(WorksheetReadService.class), edit, ws);

      // L2-Group10: SaveWorksheetDialogService.process()/process0() both strip control
      // characters via SUtil.removeControlChars(name) before using it -- this agent path
      // previously only trimmed surrounding whitespace, letting an embedded control character
      // (here, a literal tab) straight into the saved asset's name.
      ctrl.save("TOK-SAVE-CTRL",
         new WorksheetAgentController.SaveRequest("L2\tCtrlTab", null), agent);

      ArgumentCaptor<AssetEntry> entryCaptor = ArgumentCaptor.forClass(AssetEntry.class);
      verify(ws).setWorksheet(any(), entryCaptor.capture(), any(), anyBoolean(), anyBoolean());
      assertEquals("L2CtrlTab", entryCaptor.getValue().getName(),
         "the control character must be stripped from the saved name");
   }

   // ---------------------------------------------------------------------------
   // save -- PVA-002: a plain re-save must proactively invalidate a connected viewsheet's
   // stale bindable-fields cache instead of relying on the lazy timestamp check
   //
   // NOTE: PVA-009 was originally paired with PVA-002 under this same mechanism, but that
   // pairing was invalidated on recheck -- PVA-009's actual failing call
   // (set_table_source/set_chart_source) goes through VSBindingService.createSourceTables, a
   // different, cache-free code path this fix does not touch. See
   // docs/teams/2026-08-29-bugs-plugin-composer-corpus/cluster-C/03-escalate-pva008.md.
   // PVA-009 is excluded from this fix pass.
   // ---------------------------------------------------------------------------

   /**
    * Regression for Bug PVA-002: {@code list_bindable_fields} reads a connected viewsheet's base
    * worksheet through a lazy {@code Worksheet.getLastModified()} comparison
    * ({@code BindableFieldsService.list()} / {@code CubeTreeModelBuilder}) that only re-checks on
    * the next read -- a plain re-save used to never proactively tell a connected viewsheet its
    * base worksheet changed. A plain save-in-place (no name) must now call
    * {@code resetRuntime()} on every connected viewsheet synchronously.
    */
   @Test
   void plainSaveInPlaceResetsConnectedViewsheetRuntimes() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      AssetEntry entry = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
         AssetEntry.Type.WORKSHEET, "Orders WS", null);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getEntry()).thenReturn(entry);
      when(rws.getWorksheet()).thenReturn(new Worksheet());
      when(rws.getCurrent()).thenReturn(5);

      WorksheetEditService edit = mock(WorksheetEditService.class);
      when(edit.resolveWithSession(eq("TOK-SAVE4"), eq(agent)))
         .thenReturn(new WorksheetEditService.ResolvedSession(rws, "rt-ws-4"));

      Viewsheet vs = mock(Viewsheet.class);
      when(vs.getBaseEntry()).thenReturn(entry);
      RuntimeViewsheet connectedRvs = mock(RuntimeViewsheet.class);
      when(connectedRvs.getViewsheet()).thenReturn(vs);

      WorksheetService ws = mock(WorksheetService.class);
      when(ws.getRuntimeSheets(eq(agent))).thenReturn(new RuntimeSheet[]{ connectedRvs });

      WorksheetAgentController ctrl = controller(featureOn(), mock(SheetJoinService.class),
         mock(SheetSessionService.class), mock(WorksheetReadService.class), edit, ws);

      // Unrelated to this test's own concern (PVA-002's reset), but every plain in-place save now
      // also runs PVA-011's rename check (DependencyTransformer.createRenameInfo), which always
      // calls DependencyTool.getDependencies(id) once even when there is nothing to rename --
      // stub it so this test doesn't reach the real Spring-backed dependency storage service.
      Map<String, Object> result;

      try(MockedStatic<DependencyTool> dependencyTool = mockStatic(DependencyTool.class)) {
         dependencyTool.when(() -> DependencyTool.getDependencies(anyString())).thenReturn(List.of());

         result = ctrl.save("TOK-SAVE4", new WorksheetAgentController.SaveRequest(null, null), agent);
      }

      assertEquals(Boolean.TRUE, result.get("ok"));
      verify(connectedRvs).resetRuntime();
   }

   /** A Save-As must not reset anything -- see the PVA-003 warning test for that path instead. */
   @Test
   void saveAsDoesNotResetAnyConnectedViewsheetRuntime() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      AssetEntry oldEntry = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
         AssetEntry.Type.WORKSHEET, "Orders WS", null);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getEntry()).thenReturn(oldEntry);
      when(rws.getWorksheet()).thenReturn(new Worksheet());
      when(rws.getCurrent()).thenReturn(5);

      WorksheetEditService edit = mock(WorksheetEditService.class);
      when(edit.resolveWithSession(eq("TOK-SAVE5"), eq(agent)))
         .thenReturn(new WorksheetEditService.ResolvedSession(rws, "rt-ws-5"));

      Viewsheet vs = mock(Viewsheet.class);
      when(vs.getBaseEntry()).thenReturn(oldEntry);
      RuntimeViewsheet connectedRvs = mock(RuntimeViewsheet.class);
      when(connectedRvs.getViewsheet()).thenReturn(vs);
      when(connectedRvs.getID()).thenReturn("rt-vs-5");

      WorksheetService ws = mock(WorksheetService.class);
      when(ws.getRuntimeSheets(eq(agent))).thenReturn(new RuntimeSheet[]{ connectedRvs });

      WorksheetAgentController ctrl = controller(featureOn(), mock(SheetJoinService.class),
         mock(SheetSessionService.class), mock(WorksheetReadService.class), edit, ws);

      ctrl.save("TOK-SAVE5",
         new WorksheetAgentController.SaveRequest("Orders WS Copy", null), agent);

      verify(connectedRvs, never()).resetRuntime();
   }

   // ---------------------------------------------------------------------------
   // save -- PVA-011: a plain re-save must cascade a column/table rename to already-saved
   // dependent assets (e.g. a bound viewsheet), the same "Dependencies Changed" update the
   // Composer UI's own Save button performs -- there is no user in an MCP session to answer
   // that dialog, so this must happen unconditionally.
   // ---------------------------------------------------------------------------

   /**
    * Regression for Bug PVA-011: {@code save()} used to call only
    * {@code worksheetService.setWorksheet(...)}, never {@code DependencyTransformer.createRenameInfo}
    * / {@code RenameTransformHandler.addTransformTask} -- so a column renamed via
    * {@code rename_column} and then saved through this endpoint left every already-saved dependent
    * viewsheet's binding silently pointing at the old name. A rename with a real persisted
    * dependent must now synchronously cascade (waitDone=true) and say so in the response.
    */
   @Test
   void plainSaveInPlaceCascadesRenamedColumnToDependentAsset() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      AssetEntry entry = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
         AssetEntry.Type.WORKSHEET, "Orders WS", null);

      Worksheet worksheet = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(worksheet, "T", "DISCOUNT");
      worksheet.addAssembly(t);

      // Simulate rename_column("T", "DISCOUNT", "DISCOUNT_PCT"): the display name changes (here,
      // via alias) but oldName is left untouched -- exactly what DependencyTransformer.
      // addAssemblyRenameInfo diffs against to detect the rename.
      ColumnRef discount = (ColumnRef) t.getColumnSelection(false).getAttribute("DISCOUNT");
      discount.setOldName("DISCOUNT");
      discount.setAlias("DISCOUNT_PCT");

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getEntry()).thenReturn(entry);
      when(rws.getWorksheet()).thenReturn(worksheet);
      when(rws.getCurrent()).thenReturn(3);

      WorksheetEditService edit = mock(WorksheetEditService.class);
      when(edit.resolveWithSession(eq("TOK-SAVE6"), eq(agent)))
         .thenReturn(new WorksheetEditService.ResolvedSession(rws, "rt-ws-6"));

      WorksheetService ws = mock(WorksheetService.class);
      when(ws.getRuntimeSheets(eq(agent))).thenReturn(new RuntimeSheet[0]);

      RenameTransformHandler renameTransformHandler = mock(RenameTransformHandler.class);

      // DependencyTransformer.createRenameInfo(rws) resolves dependents through
      // DependencyTool.getDependencies(entryId), which is backed by a live cluster/storage
      // service; stub it to report the worksheet has one dependent asset (a viewsheet), the way a
      // real installation's persisted dependency graph would.
      AssetEntry dependentViewsheet = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
         AssetEntry.Type.VIEWSHEET, "Orders VS", null);

      try(MockedStatic<DependencyTool> dependencyTool = mockStatic(DependencyTool.class)) {
         dependencyTool.when(() -> DependencyTool.getDependencies(anyString()))
            .thenReturn(List.of(dependentViewsheet));

         WorksheetAgentController ctrl = controller(featureOn(), mock(SheetJoinService.class),
            mock(SheetSessionService.class), mock(WorksheetReadService.class), edit, ws,
            renameTransformHandler);

         Map<String, Object> result = ctrl.save("TOK-SAVE6",
            new WorksheetAgentController.SaveRequest(null, null), agent);

         assertEquals(Boolean.TRUE, result.get("ok"));
         assertEquals(Boolean.TRUE, result.get("dependenciesUpdated"),
            "a save that found a renamed column with a real dependent must report the cascade");
         assertNotNull(result.get("message"));
         verify(renameTransformHandler)
            .addTransformTask(any(RenameDependencyInfo.class), eq(true));
      }
   }

   /**
    * An ordinary re-save with no rename must not invoke the cascade at all -- the common case
    * (the vast majority of saves) stays exactly as fast/simple as before this fix.
    */
   @Test
   void plainSaveInPlaceDoesNotCascadeWhenNoColumnWasRenamed() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      AssetEntry entry = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
         AssetEntry.Type.WORKSHEET, "Orders WS", null);

      Worksheet worksheet = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(worksheet, "T", "DISCOUNT");
      worksheet.addAssembly(t);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getEntry()).thenReturn(entry);
      when(rws.getWorksheet()).thenReturn(worksheet);
      when(rws.getCurrent()).thenReturn(3);

      WorksheetEditService edit = mock(WorksheetEditService.class);
      when(edit.resolveWithSession(eq("TOK-SAVE7"), eq(agent)))
         .thenReturn(new WorksheetEditService.ResolvedSession(rws, "rt-ws-7"));

      WorksheetService ws = mock(WorksheetService.class);
      when(ws.getRuntimeSheets(eq(agent))).thenReturn(new RuntimeSheet[0]);

      RenameTransformHandler renameTransformHandler = mock(RenameTransformHandler.class);

      WorksheetAgentController ctrl = controller(featureOn(), mock(SheetJoinService.class),
         mock(SheetSessionService.class), mock(WorksheetReadService.class), edit, ws,
         renameTransformHandler);

      Map<String, Object> result;

      try(MockedStatic<DependencyTool> dependencyTool = mockStatic(DependencyTool.class)) {
         dependencyTool.when(() -> DependencyTool.getDependencies(anyString())).thenReturn(List.of());

         result = ctrl.save("TOK-SAVE7", new WorksheetAgentController.SaveRequest(null, null), agent);
      }

      assertEquals(Boolean.TRUE, result.get("ok"));
      assertNull(result.get("dependenciesUpdated"));
      verifyNoInteractions(renameTransformHandler);
   }

   /** A Save-As must not cascade -- a brand-new entry has no pre-existing dependent to update. */
   @Test
   void saveAsDoesNotCascadeRenames() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      AssetEntry oldEntry = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
         AssetEntry.Type.WORKSHEET, "Orders WS", null);

      Worksheet worksheet = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(worksheet, "T", "DISCOUNT");
      worksheet.addAssembly(t);

      ColumnRef discount = (ColumnRef) t.getColumnSelection(false).getAttribute("DISCOUNT");
      discount.setOldName("DISCOUNT");
      discount.setAlias("DISCOUNT_PCT");

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getEntry()).thenReturn(oldEntry);
      when(rws.getWorksheet()).thenReturn(worksheet);
      when(rws.getCurrent()).thenReturn(4);

      WorksheetEditService edit = mock(WorksheetEditService.class);
      when(edit.resolveWithSession(eq("TOK-SAVE8"), eq(agent)))
         .thenReturn(new WorksheetEditService.ResolvedSession(rws, "rt-ws-8"));

      WorksheetService ws = mock(WorksheetService.class);
      when(ws.getRuntimeSheets(eq(agent))).thenReturn(new RuntimeSheet[0]);

      RenameTransformHandler renameTransformHandler = mock(RenameTransformHandler.class);

      WorksheetAgentController ctrl = controller(featureOn(), mock(SheetJoinService.class),
         mock(SheetSessionService.class), mock(WorksheetReadService.class), edit, ws,
         renameTransformHandler);

      Map<String, Object> result = ctrl.save("TOK-SAVE8",
         new WorksheetAgentController.SaveRequest("Orders WS Copy", null), agent);

      assertEquals(Boolean.TRUE, result.get("ok"));
      assertNull(result.get("dependenciesUpdated"));
      verifyNoInteractions(renameTransformHandler);
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
