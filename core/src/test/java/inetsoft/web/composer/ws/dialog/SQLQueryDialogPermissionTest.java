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
package inetsoft.web.composer.ws.dialog;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.sree.security.*;
import inetsoft.test.*;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.asset.internal.SQLBoundTableAssemblyInfo;
import inetsoft.uql.jdbc.*;
import inetsoft.uql.jdbc.util.JDBCUtil;
import inetsoft.uql.util.ColumnCache;
import inetsoft.web.binding.drm.ColumnRefModel;
import inetsoft.web.composer.model.TreeNodeModel;
import inetsoft.web.composer.model.ws.BasicSQLQueryModel;
import inetsoft.web.composer.model.ws.SQLQueryDialogModel;
import inetsoft.web.portal.controller.database.*;
import inetsoft.web.portal.model.database.AdvancedSQLQueryModel;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.Principal;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77163: every SQL query dialog / query manager entry point that uses a data source by
 * name must require DATA_SOURCE READ on that name before the source is loaded, bound or
 * browsed. Labels follow the diagnosis:
 * <ul>
 *    <li>S1 GET /api/composer/ws/sql-query-dialog-model (new and existing assembly)</li>
 *    <li>S2 GET /api/data/condition/subquery/sql-query-dialog-model</li>
 *    <li>S3 POST /api/composer/ws/sql-query-dialog/clear</li>
 *    <li>S4 POST /api/composer/ws/sql-query-dialog/browse-data</li>
 *    <li>S5 POST /api/composer/ws/sql-query-dialog/get-sql-string</li>
 *    <li>S7 POST /api/composer/ws/sql-query-dialog/table-columns</li>
 *    <li>S8 POST /api/composer/ws/sql-query-dialog/change-edit-mode</li>
 *    <li>S9 POST /api/composer/ws/sql-query-dialog/query/update</li>
 *    <li>S10 STOMP /events/ws/dialog/sql-query-dialog-model (setModel)</li>
 *    <li>Q1 POST /api/data/datasource/query/data-source-tree</li>
 * </ul>
 * The S5/S2 allow cases cover the VPM subquery editor, whose users hold READ.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class SQLQueryDialogPermissionTest {
   private static final String ALLOWED = "DS_OK";
   private static final String DENIED = "DS1";

   private SecurityEngine securityEngine;
   private RuntimeQueryService runtimeQueryService;
   private XRepository repository;
   private DataSourceService dataSourceService;
   private QueryManagerService queryManager;
   private ViewsheetService wsEngine;
   private SQLQueryDialogService service;
   private SQLQueryDialogController controller;
   private final Principal principal = () -> "bob";

   @BeforeEach
   void setUp() throws Exception {
      securityEngine = mock(SecurityEngine.class);
      runtimeQueryService = mock(RuntimeQueryService.class);
      repository = mock(XRepository.class);
      dataSourceService = mock(DataSourceService.class);
      queryManager = new QueryManagerService(runtimeQueryService, repository, dataSourceService,
                                             securityEngine, mock(ColumnCache.class));
      wsEngine = mock(ViewsheetService.class);
      service = new SQLQueryDialogService(wsEngine, queryManager,
                                          mock(QueryGraphModelService.class), repository,
                                          securityEngine);
      controller = new SQLQueryDialogController(mock(SQLQueryDialogServiceProxy.class));
      controller.setXRepository(repository);
      controller.setSecurityEngine(securityEngine);
      controller.setQueryManagerService(queryManager);

      when(securityEngine.checkPermission(
         any(Principal.class), eq(ResourceType.PHYSICAL_TABLE), eq("*"), eq(ResourceAction.ACCESS)))
         .thenReturn(true);
      grantRead(ALLOWED);
   }

   private void grantRead(String... names) throws Exception {
      Set<String> readable = Set.of(names);
      when(securityEngine.checkPermission(
         any(Principal.class), eq(ResourceType.DATA_SOURCE), nullable(String.class), eq(ResourceAction.READ)))
         .thenAnswer(inv -> readable.contains(inv.<String>getArgument(2)));
   }

   private void verifyReadChecked(String name) throws Exception {
      verify(securityEngine).checkPermission(
         principal, ResourceType.DATA_SOURCE, name, ResourceAction.READ);
   }

   private static void assertDenied(Executable call) {
      assertThrows(java.lang.SecurityException.class, call);
   }

   private void verifyNothingLoaded() throws Exception {
      verify(repository, never()).getDataSource(anyString());
      verify(dataSourceService, never()).getDataSource(anyString());
   }

   private static JDBCDataSource source(String name) {
      JDBCDataSource ds = mock(JDBCDataSource.class);
      when(ds.getFullName()).thenReturn(name);
      return ds;
   }

   private static JDBCQuery queryBoundTo(String name) {
      JDBCQuery query = mock(JDBCQuery.class);
      JDBCDataSource ds = name == null ? null : source(name);
      when(query.getDataSource()).thenReturn(ds);
      return query;
   }

   /** A live runtime query, as bound by an earlier dialog call, on the given source. */
   private RuntimeQueryService.RuntimeXQuery runtimeQueryBoundTo(String id, String name) {
      RuntimeQueryService.RuntimeXQuery runtimeQuery = mock(RuntimeQueryService.RuntimeXQuery.class);
      JDBCQuery query = queryBoundTo(name);
      when(runtimeQuery.getQuery()).thenReturn(query);
      when(runtimeQuery.getId()).thenReturn(id);
      when(runtimeQueryService.getRuntimeQuery(id)).thenReturn(runtimeQuery);
      return runtimeQuery;
   }

   private static SQLQueryDialogModel simpleModel(String dataSource) {
      SQLQueryDialogModel model = new SQLQueryDialogModel();
      model.setDataSource(dataSource);
      model.setSimpleModel(new BasicSQLQueryModel());
      return model;
   }

   private static SQLQueryDialogModel advancedModel(String dataSource, String runtimeQueryId) {
      SQLQueryDialogModel model = new SQLQueryDialogModel();
      model.setDataSource(dataSource);
      model.setRuntimeId(runtimeQueryId);
      model.setAdvancedEdit(true);
      model.setAdvancedModel(new AdvancedSQLQueryModel());
      return model;
   }

   private static AssetEntry queryEntry(AssetEntry.Type type, String path, String prefix) {
      AssetEntry entry = new AssetEntry(AssetRepository.QUERY_SCOPE, type, path, null);
      entry.setProperty("prefix", prefix);
      return entry;
   }

   // ---- S1: open the dialog ----

   @Test
   void s1NewQueryDeniedWithoutRead() throws Exception {
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(new Worksheet());

      assertDenied(() -> queryManager.getSqlQueryDialogModel(rws, null, DENIED, principal));
      verifyReadChecked(DENIED);
      verifyNothingLoaded();
      verify(runtimeQueryService, never()).createRuntimeQuery(any(), any(), any(), any());
   }

   @Test
   void s1ExistingAssemblyDeniedOnBoundSource() throws Exception {
      RuntimeWorksheet rws = existingSqlTable("T1", queryBoundTo(DENIED));

      assertDenied(() -> queryManager.getSqlQueryDialogModel(rws, "T1", DENIED, principal));
      verifyReadChecked(DENIED);
      verify(runtimeQueryService, never()).createRuntimeQuery(any(), any(), any(), any());
   }

   @Test
   void s1ExistingAssemblyWithoutBoundSourceFailsClosed() throws Exception {
      RuntimeWorksheet rws = existingSqlTable("T1", queryBoundTo(null));

      assertDenied(() -> queryManager.getSqlQueryDialogModel(rws, "T1", ALLOWED, principal));
      verify(runtimeQueryService, never()).createRuntimeQuery(any(), any(), any(), any());
   }

   private static RuntimeWorksheet existingSqlTable(String name, JDBCQuery query) {
      Worksheet ws = mock(Worksheet.class);
      SQLBoundTableAssembly assembly = mock(SQLBoundTableAssembly.class);
      SQLBoundTableAssemblyInfo info = mock(SQLBoundTableAssemblyInfo.class);
      when(ws.getAssembly(name)).thenReturn(assembly);
      when(assembly.getTableInfo()).thenReturn(info);
      when(info.getQuery()).thenReturn(query);
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      return rws;
   }

   // ---- S2: subquery model ----

   @Test
   void s2SubQueryModelDeniedWithoutRead() throws Exception {
      assertDenied(() -> controller.getSubQueryModel(DENIED, principal));
      verifyReadChecked(DENIED);
   }

   @Test
   void s2SubQueryModelAllowedWithReadForVpmEditor() throws Exception {
      SQLQueryDialogModel model = controller.getSubQueryModel(ALLOWED, principal);
      assertEquals(ALLOWED, model.getDataSource());
      assertEquals(List.of(ALLOWED), model.getDataSources());
      verifyReadChecked(ALLOWED);
   }

   // ---- S3: clear ----

   @Test
   void s3ClearQueryDeniedWithoutRead() throws Exception {
      assertDenied(() -> queryManager.clearQuery("rq-1", DENIED, "T1", false, principal));
      verifyReadChecked(DENIED);
      verifyNothingLoaded();
      verify(runtimeQueryService, never()).createRuntimeQuery(any(), any(), any(), any());
      verify(runtimeQueryService, never()).saveRuntimeQuery(any());
   }

   // ---- S4: browse data ----

   @Test
   void s4BrowseDataDeniedWithoutRead() throws Exception {
      when(wsEngine.getWorksheet("ws-1", principal)).thenReturn(mock(RuntimeWorksheet.class));

      assertDenied(() -> service.browseData("ws-1", DENIED, new ColumnRefModel(), principal));
      verifyReadChecked(DENIED);
      verify(wsEngine, never()).getWorksheet(any(), any());
   }

   // ---- S5: SQL string ----

   @Test
   void s5SqlStringDeniedWithoutRead() throws Exception {
      try(MockedStatic<JDBCUtil> jdbcUtil = mockStatic(JDBCUtil.class)) {
         assertDenied(() -> controller.getSQLString(simpleModel(DENIED), principal));
         verifyReadChecked(DENIED);
         verifyNothingLoaded();
         jdbcUtil.verify(() -> JDBCUtil.createSQL(any(), any(), any(), any(), any(), any()), never());
      }
   }

   @ParameterizedTest
   @NullSource
   @ValueSource(strings = { "", "   " })
   void s5SqlStringRejectsMissingName(String name) throws Exception {
      assertDenied(() -> controller.getSQLString(simpleModel(name), principal));
      verifyNothingLoaded();
      verifyNoInteractions(securityEngine);
   }

   @Test
   void checkFailureIsTreatedAsDenied() throws Exception {
      when(securityEngine.checkPermission(
         any(Principal.class), eq(ResourceType.DATA_SOURCE), nullable(String.class), eq(ResourceAction.READ)))
         .thenThrow(new inetsoft.sree.security.SecurityException("boom"));

      assertDenied(() -> controller.getSQLString(simpleModel(ALLOWED), principal));
      verifyNothingLoaded();
   }

   @Test
   void s5SqlStringAllowedWithReadForVpmEditor() throws Exception {
      JDBCDataSource ds = source(ALLOWED);
      when(repository.getDataSource(ALLOWED)).thenReturn(ds);
      UniformSQL sql = mock(UniformSQL.class);
      when(sql.getSQLString()).thenReturn("SELECT 1");

      try(MockedStatic<JDBCUtil> jdbcUtil = mockStatic(JDBCUtil.class)) {
         jdbcUtil.when(() -> JDBCUtil.createSQL(same(ds), any(), any(), any(), any(), any()))
            .thenReturn(sql);
         assertEquals("SELECT 1", controller.getSQLString(simpleModel(ALLOWED), principal));
      }

      verifyReadChecked(ALLOWED);
   }

   // ---- S7: table columns ----

   @Test
   void s7TableColumnsDeniedOnEntryPrefix() throws Exception {
      AssetRepository assetRepository = wireAssetRepository();
      AssetEntry table = queryEntry(AssetEntry.Type.PHYSICAL_TABLE, DENIED + "/T", DENIED);

      assertDenied(() -> controller.getTableColumns(table, principal));
      verifyReadChecked(DENIED);
      verifyNoInteractions(assetRepository);
   }

   @Test
   void s7TableColumnsAllowedWithRead() throws Exception {
      AssetRepository assetRepository = wireAssetRepository();
      AssetEntry table = queryEntry(AssetEntry.Type.PHYSICAL_TABLE, ALLOWED + "/T", ALLOWED);
      AssetEntry[] columns = new AssetEntry[0];
      when(assetRepository.getEntries(table, principal, ResourceAction.READ)).thenReturn(columns);

      assertSame(columns, controller.getTableColumns(table, principal));
      verifyReadChecked(ALLOWED);
   }

   private AssetRepository wireAssetRepository() {
      AssetRepository assetRepository = mock(AssetRepository.class);
      when(wsEngine.getAssetRepository()).thenReturn(assetRepository);
      ReflectionTestUtils.setField(controller, "wsEngine", wsEngine);
      return assetRepository;
   }

   // ---- S8: change edit mode ----

   @Test
   void s8ToAdvancedDeniedWithoutRead() throws Exception {
      runtimeQueryBoundTo("rq-1", ALLOWED);
      SQLQueryDialogModel model = simpleModel(DENIED);
      model.setRuntimeId("rq-1");

      assertDenied(() -> service.changeEditMode("", "rq-1", true, model, principal));
      verifyReadChecked(DENIED);
      verifyNothingLoaded();
   }

   @Test
   void s8ToSimpleDeniedOnRuntimeQueryBoundSource() throws Exception {
      RuntimeQueryService.RuntimeXQuery runtimeQuery = runtimeQueryBoundTo("rq-1", DENIED);

      assertDenied(() -> service.changeEditMode(
         "", "rq-1", false, advancedModel(ALLOWED, "rq-1"), principal));
      verifyReadChecked(DENIED);
      verify(runtimeQueryService, never()).saveRuntimeQuery(any());
      verify(runtimeQuery.getQuery(), never()).getSQLDefinition();
   }

   // ---- S9: query update ----

   @Test
   void s9UpdateQueryDeniedWithoutRead() throws Exception {
      RuntimeQueryService.RuntimeXQuery runtimeQuery = runtimeQueryBoundTo("rq-1", ALLOWED);

      assertDenied(() -> controller.updateQueryBySample(
         new BasicSQLQueryModel(), "rq-1", DENIED, principal));
      verifyReadChecked(DENIED);
      verifyNothingLoaded();
      verify(runtimeQuery, never()).setQuery(any());
      verify(runtimeQueryService, never()).saveRuntimeQuery(any());
   }

   // ---- S10: setModel (service layer) ----

   @Test
   void s10SetModelSimpleDeniedWithoutRead() throws Exception {
      assertDenied(() -> service.setModel(
         "ws-1", simpleModel(DENIED), principal, mock(CommandDispatcher.class)));
      verifyReadChecked(DENIED);
      verifyNoInteractions(wsEngine);
      verifyNothingLoaded();
   }

   @Test
   void s10SetModelAdvancedDeniedOnRuntimeQueryBoundSource() throws Exception {
      RuntimeQueryService.RuntimeXQuery runtimeQuery = runtimeQueryBoundTo("rq-1", DENIED);

      assertDenied(() -> service.setModel(
         "ws-1", advancedModel(ALLOWED, "rq-1"), principal, mock(CommandDispatcher.class)));
      verifyReadChecked(ALLOWED);
      verifyReadChecked(DENIED);
      verifyNoInteractions(wsEngine);
      verify(runtimeQuery.getQuery(), never()).clone();
   }

   @Test
   void s10SetModelAdvancedWithoutBoundSourceFailsClosed() throws Exception {
      runtimeQueryBoundTo("rq-1", null);

      assertDenied(() -> service.setModel(
         "ws-1", advancedModel(ALLOWED, "rq-1"), principal, mock(CommandDispatcher.class)));
      verifyNoInteractions(wsEngine);
   }

   @Test
   void s10SetModelAdvancedAllowedWhenBothReadable() throws Exception {
      runtimeQueryBoundTo("rq-1", ALLOWED);
      IllegalStateException reached = new IllegalStateException("past the permission check");
      when(wsEngine.getWorksheet("ws-1", principal)).thenThrow(reached);

      assertSame(reached, assertThrows(IllegalStateException.class, () -> service.setModel(
         "ws-1", advancedModel(ALLOWED, "rq-1"), principal, mock(CommandDispatcher.class))));
      verify(securityEngine, times(2)).checkPermission(
         principal, ResourceType.DATA_SOURCE, ALLOWED, ResourceAction.READ);
   }

   // ---- Q1: data source tree ----

   @Test
   void q1TopLevelDeniedWithoutRead() throws Exception {
      try(MockedStatic<AssetUtil> assetUtil = mockStatic(AssetUtil.class)) {
         AssetRepository assetRepository = mock(AssetRepository.class);
         assetUtil.when(() -> AssetUtil.getAssetRepository(false)).thenReturn(assetRepository);
         when(assetRepository.getEntries(any(), any(), any(), any())).thenReturn(new AssetEntry[0]);

         assertDenied(() -> queryManager.getDataSourceTreeNode(DENIED, false, null, principal));
         verifyReadChecked(DENIED);
         verifyNoInteractions(assetRepository);
      }
   }

   @Test
   void q1ExpandedEntryDeniedWhenPrefixDiffersFromParam() throws Exception {
      AssetEntry expanded = queryEntry(AssetEntry.Type.PHYSICAL_FOLDER, DENIED + "/TABLE", DENIED);

      try(MockedStatic<AssetUtil> assetUtil = mockStatic(AssetUtil.class)) {
         AssetRepository assetRepository = mock(AssetRepository.class);
         assetUtil.when(() -> AssetUtil.getAssetRepository(false)).thenReturn(assetRepository);
         when(assetRepository.getEntries(any(), any(), any(), any())).thenReturn(new AssetEntry[0]);

         assertDenied(() -> queryManager.getDataSourceTreeNode(ALLOWED, true, expanded, principal));
         verifyReadChecked(ALLOWED);
         verifyReadChecked(DENIED);
         verifyNoInteractions(assetRepository);
      }
   }

   @Test
   void q1ExpandedEntryAllowedWithRead() throws Exception {
      AssetEntry expanded = queryEntry(AssetEntry.Type.PHYSICAL_FOLDER, ALLOWED + "/TABLE", ALLOWED);

      try(MockedStatic<AssetUtil> assetUtil = mockStatic(AssetUtil.class)) {
         AssetRepository assetRepository = mock(AssetRepository.class);
         assetUtil.when(() -> AssetUtil.getAssetRepository(false)).thenReturn(assetRepository);
         when(assetRepository.getEntries(same(expanded), same(principal), eq(ResourceAction.READ),
                                         any())).thenReturn(new AssetEntry[0]);

         TreeNodeModel tree = queryManager.getDataSourceTreeNode(ALLOWED, true, expanded, principal);
         assertNotNull(tree);
         assertTrue(tree.children().isEmpty());
         verify(assetRepository).getEntries(same(expanded), same(principal),
                                            eq(ResourceAction.READ), any());
      }
   }
}
