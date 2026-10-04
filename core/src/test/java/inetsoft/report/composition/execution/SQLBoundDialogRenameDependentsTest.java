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
package inetsoft.report.composition.execution;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.mv.MVManager;
import inetsoft.report.TableLens;
import inetsoft.report.XSessionManager;
import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.report.composition.event.AssetEventUtil;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.storage.BlobStorageManager;
import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.SQLBoundTableAssemblyInfo;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.ExpressionRef;
import inetsoft.uql.jdbc.*;
import inetsoft.uql.jdbc.util.JDBCUtil;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.schema.XTypeNode;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.*;
import inetsoft.util.Plugins;
import inetsoft.util.credential.CredentialService;
import inetsoft.web.composer.model.ws.*;
import inetsoft.web.composer.ws.assembly.WorksheetEventUtil;
import inetsoft.web.composer.ws.dialog.SQLQueryDialogService;
import inetsoft.web.portal.controller.database.*;
import inetsoft.web.portal.model.database.AdvancedSQLQueryModel;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.apache.derby.jdbc.EmbeddedDataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import javax.sql.DataSource;
import java.lang.reflect.Constructor;
import java.sql.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Bug #77711. A SQL-bound table saved before the fix stores ALIAS_0 for a long name, and
 * the worksheet binds to the ALIAS_0 column: a formula column and a numeric range column of
 * the table, and a mirror table over it with its own formula column. An OK in the SQL query
 * dialog renames the column to the name. In simple mode, and through the advanced mode, each
 * of them must still compute the same values, run on Derby through AssetQuery.getTableLens
 * as the worksheet does.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  SQLBoundDialogRenameDependentsTest.JdbcConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class SQLBoundDialogRenameDependentsTest {
   private static final String DB = "memory:bug77711dependents";
   // over the 28 bytes of isValidAlias
   private static final String LONG = "CUSTOMER_ACCOUNT_OPENING_DATE_LOCAL";
   private static final String RID = "rq-77711-dependents";
   private static final String WS = "ws-77711-dependents";
   private static final XRepository REPOSITORY = newRepository();
   private static final ObjectMapper MAPPER = new ObjectMapper()
      .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

   @Configuration
   static class JdbcConfig {
      @Bean
      public CredentialService credentialService() throws Exception {
         Constructor<CredentialService> ctor = CredentialService.class.getDeclaredConstructor();
         ctor.setAccessible(true);
         return ctor.newInstance();
      }

      @Bean
      public Plugins plugins(BlobStorageManager blobStorageManager, Cluster cluster,
                             ApplicationEventPublisher eventPublisher)
      {
         return new Plugins(blobStorageManager.getStorage("plugins", true), cluster,
                            eventPublisher);
      }

      @Bean
      public ConnectionPoolFactory connectionPoolFactory() {
         DataSource ds = derby();
         ConnectionPoolFactory factory = mock(ConnectionPoolFactory.class);
         when(factory.getConnectionPool(any(), any())).thenReturn(ds);
         return factory;
      }

      @Bean
      public Drivers drivers(Plugins plugins, ConnectionPoolFactory connectionPoolFactory) {
         return new Drivers(plugins, connectionPoolFactory);
      }

      @Bean
      public Config config(Plugins plugins) {
         return new Config(plugins);
      }

      @Bean
      public XRepository xRepository() {
         return REPOSITORY;
      }

      @Bean
      @SuppressWarnings("unchecked")
      public AssetDataCache assetDataCache() {
         ObjectProvider<DistributedTableCacheStore> provider = mock(ObjectProvider.class);
         when(provider.getObject()).thenReturn(mock(DistributedTableCacheStore.class));
         return new AssetDataCache(mock(DataSourceRegistry.class), provider);
      }

      @Bean
      public MVManager mvManager() {
         return mock(MVManager.class);
      }

      // the data service stands in for XEngine.execute and runs the query on Derby
      @Bean
      public XSessionManager xSessionManager(XSessionService sessionService) throws Exception {
         XDataService dataService = mock(XDataService.class);
         when(dataService.execute(any(), any(XQuery.class), any(), any(), anyBoolean(), any()))
            .thenAnswer(inv -> {
               XQuery query = inv.getArgument(1);
               VariableTable vars = inv.getArgument(2);
               JDBCHandler handler = new JDBCHandler();
               handler.connect(query.getDataSource(), vars);
               return handler.execute(query, vars, inv.getArgument(3), inv.getArgument(5));
            });
         XSessionManager manager =
            new XSessionManager(dataService, sessionService, mock(DataSourceRegistry.class));
         manager.setCacheData(false);
         return manager;
      }
   }

   @BeforeAll
   static void createTable() throws Exception {
      try(Connection conn = derby().getConnection(); Statement stmt = conn.createStatement()) {
         try {
            stmt.executeUpdate("drop table EMP");
         }
         catch(Exception ignore) {
            // first run
         }

         stmt.executeUpdate("create table EMP (ID int, " + LONG + " int)");
         stmt.executeUpdate("insert into EMP values (1, 10), (2, 20)");
      }
   }

   @BeforeEach
   void limitAlias() {
      SreeEnv.setProperty("limit.alias.length", "true");
   }

   @AfterEach
   void resetLimitAlias() {
      SreeEnv.setProperty("limit.alias.length", null);
   }

   @Test
   void simpleOk() throws Exception {
      Fixture fixture = new Fixture();
      fixture.assertValues();

      fixture.ok(fixture.simpleModel(true));

      fixture.assertValues();
   }

   @Test
   void advancedOk() throws Exception {
      Fixture fixture = new Fixture();
      fixture.assertValues();

      fixture.ok(fixture.advancedModel(true));

      fixture.assertValues();
   }

   @Test
   void simpleApplyThenAdvancedOk() throws Exception {
      Fixture fixture = new Fixture();
      fixture.assertValues();

      fixture.ok(fixture.simpleModel(false));
      fixture.assertValues();
      fixture.ok(fixture.advancedModel(true));

      fixture.assertValues();
   }

   @Test
   void advancedApplyThenOk() throws Exception {
      Fixture fixture = new Fixture();
      fixture.assertValues();

      SQLQueryDialogModel model = fixture.advancedModel(false);
      fixture.ok(model);
      fixture.assertValues();
      model.setAdvancedModel(echo(fixture.service.getAdvancedQueryModel(
         fixture.service.getRuntimeQuery(RID), null)));
      model.setCloseDialog(true);
      fixture.ok(model);

      fixture.assertValues();
   }

   // an old table T1 with the dependents, and the opened SQL query dialog of T1
   private static class Fixture {
      Fixture() throws Exception {
         ds = dataSource();
         UniformSQL sql = JDBCUtil.createSQL(ds, tables(), new String[] { "EMP.ID", "EMP." + LONG },
                                             new XJoin[0], new ArrayList<>(), null);
         sql.getSelection().setAlias(1, "ALIAS_0");
         JDBCQuery query = new JDBCQuery();
         query.setName("bug77711");
         query.setUserQuery(true);
         query.setDataSource(ds);
         query.setSQLDefinition(sql);

         ws = new Worksheet();
         table = new SQLBoundTableAssembly(ws, "T1");
         SQLBoundTableAssemblyInfo info = (SQLBoundTableAssemblyInfo) table.getTableInfo();
         info.setQuery(query);
         info.setSourceInfo(new SourceInfo(SourceInfo.DATASOURCE, ds.getFullName(), ds.getFullName()));
         table.setProperty("no_cache", "true");
         ColumnSelection columns = new ColumnSelection();

         for(String name : new String[] { "ALIAS_0", "ID" }) {
            ColumnRef column = new ColumnRef(new AttributeRef(null, name));
            column.setDataType(XSchema.INTEGER);
            columns.addAttribute(column);
         }

         // a formula column and a numeric range column over ALIAS_0
         ExpressionRef formula = new ExpressionRef(null, "F");
         formula.setExpression("field['ALIAS_0'] + 1");
         ColumnRef fcol = new ColumnRef(formula);
         fcol.setDataType(XSchema.INTEGER);
         columns.addAttribute(fcol);
         NumericRangeRef range = new NumericRangeRef("N", new AttributeRef(null, "ALIAS_0"));
         ValueRangeInfo rangeInfo = new ValueRangeInfo();
         rangeInfo.setValues(new double[] { 15 });
         rangeInfo.setLabels(new String[] { "low", "high" });
         range.setValueRangeInfo(rangeInfo);
         columns.addAttribute(new ColumnRef(range));

         // as on a worksheet that was opened
         for(int i = 0; i < columns.getAttributeCount(); i++) {
            ((ColumnRef) columns.getAttribute(i)).setOldName(columns.getAttribute(i).getName());
         }

         table.setColumnSelection(columns, false);
         table.setColumnSelection((ColumnSelection) columns.clone(), true);
         ws.addAssembly(table);

         // a mirror over T1 with a formula column over its T1.ALIAS_0 column
         mirror = new MirrorTableAssembly(ws, "M1", table);
         mirror.setProperty("no_cache", "true");
         ws.addAssembly(mirror);
         mirror.update();
         ColumnSelection mcols = mirror.getColumnSelection(false);
         ExpressionRef mformula = new ExpressionRef(null, "G");
         mformula.setExpression("field['T1.ALIAS_0'] * 2");
         ColumnRef gcol = new ColumnRef(mformula);
         gcol.setDataType(XSchema.INTEGER);
         mcols.addAttribute(gcol);
         mirror.setColumnSelection(mcols, false);
         mirror.update();

         RuntimeQueryService rqs = mock(RuntimeQueryService.class);
         RuntimeQueryService.RuntimeXQuery runtimeQuery =
            new RuntimeQueryService.RuntimeXQuery(query.clone(), RID, ds.getFullName());
         runtimeQuery.setVariables(new VariableTable());
         runtimeQuery.initQueryAliasMapping();
         when(rqs.getRuntimeQuery(RID)).thenReturn(runtimeQuery);
         when(REPOSITORY.getDataSource(ds.getFullName())).thenReturn(ds);
         DataSourceService dss = mock(DataSourceService.class);
         when(dss.getDataSource(ds.getFullName())).thenReturn(ds);
         SecurityEngine security = mock(SecurityEngine.class);
         when(security.checkPermission(any(), any(), any(String.class), any())).thenReturn(true);
         service = new QueryManagerService(rqs, REPOSITORY, dss, security, mock(ColumnCache.class));
         ViewsheetService wsEngine = mock(ViewsheetService.class);
         rws = mock(RuntimeWorksheet.class);
         when(rws.getWorksheet()).thenReturn(ws);
         AssetQuerySandbox box = mock(AssetQuerySandbox.class);
         when(box.getVariableTable()).thenReturn(new VariableTable());
         when(rws.getAssetQuerySandbox()).thenReturn(box);
         when(wsEngine.getWorksheet(eq(WS), any())).thenReturn(rws);
         when(wsEngine.getAssetRepository()).thenReturn(mock(AssetRepository.class));
         dialog = new SQLQueryDialogService(
            wsEngine, service, mock(QueryGraphModelService.class), REPOSITORY, security);
      }

      // T1's formula and range columns and M1's formula column, by ID, as before the OK
      void assertValues() throws Exception {
         Map<Integer, List<Object>> t1 = values(table, "F", "N");
         assertEquals(Map.of(1, List.of(11, "low"), 2, List.of(21, "high")), t1, t1.toString());
         Map<Integer, List<Object>> m1 = values(mirror, "G");
         assertEquals(Map.of(1, List.of(20), 2, List.of(40)), m1, m1.toString());
      }

      ExpressionRef formula() {
         return (ExpressionRef) ((ColumnRef) table.getColumnSelection().getAttribute("F"))
            .getDataRef();
      }

      SQLQueryDialogModel simpleModel(boolean close) {
         BasicSQLQueryModel simple = new BasicSQLQueryModel();
         simple.setTables(tables());
         simple.setColumns(new SQLQueryDialogColumnModel[] {
            SQLQueryDialogColumnModel.builder().name("EMP.ID").build(),
            SQLQueryDialogColumnModel.builder().name("EMP." + LONG).build() });
         simple.setJoins(new JoinItemModel[0]);
         simple.setConditionList(new ArrayList<>());
         SQLQueryDialogModel model = new SQLQueryDialogModel();
         model.setName("T1");
         model.setDataSource(ds.getFullName());
         model.setRuntimeId(RID);
         model.setSimpleModel(simple);
         model.setCloseDialog(close);
         return model;
      }

      // the switch to advanced mode, with the model the client sends back
      SQLQueryDialogModel advancedModel(boolean close) throws Exception {
         SQLQueryDialogModel model = simpleModel(close);
         AdvancedSQLQueryModel advanced = service.convertToAdvancedQueryModel(rws, model, null);
         model.setAdvancedEdit(true);
         model.setAdvancedModel(echo(advanced));
         return model;
      }

      void ok(SQLQueryDialogModel model) throws Exception {
         // the refreshes after the edit need a running worksheet
         try(MockedStatic<WorksheetEventUtil> ignored = mockStatic(WorksheetEventUtil.class);
             MockedStatic<AssetEventUtil> ignored2 = mockStatic(AssetEventUtil.class))
         {
            dialog.setModel(WS, model, () -> "admin", mock(CommandDispatcher.class));
         }
      }

      final JDBCDataSource ds;
      final Worksheet ws;
      final SQLBoundTableAssembly table;
      final MirrorTableAssembly mirror;
      final QueryManagerService service;
      final RuntimeWorksheet rws;
      final SQLQueryDialogService dialog;
   }

   // the values of the columns by the ID of the row, from the table as the worksheet runs it
   private static Map<Integer, List<Object>> values(TableAssembly table, String... names)
      throws Exception
   {
      AssetQuery query = AssetQuery.createAssetQuery(
         table, AssetQuerySandbox.RUNTIME_MODE, new AssetQuerySandbox(table.getWorksheet()),
         false, -1L, true, false);
      TableLens lens = query.getTableLens(new VariableTable());
      assertNotNull(lens, table.getName() + " failed, see the log");
      lens.moreRows(Integer.MAX_VALUE);
      List<String> header = new ArrayList<>();

      for(int c = 0; c < lens.getColCount(); c++) {
         header.add(String.valueOf(lens.getObject(0, c)));
      }

      int id = header.indexOf("ID");
      assertTrue(id >= 0, header.toString());
      Map<Integer, List<Object>> values = new HashMap<>();

      for(int r = 1; r < lens.getRowCount(); r++) {
         List<Object> row = new ArrayList<>();

         for(String name : names) {
            int c = header.indexOf(name);
            assertTrue(c >= 0, name + " not in " + header);
            Object value = lens.getObject(r, c);
            row.add(value instanceof Number ? ((Number) value).intValue() : String.valueOf(value));
         }

         values.put(((Number) lens.getObject(r, id)).intValue(), row);
      }

      return values;
   }

   private static AdvancedSQLQueryModel echo(AdvancedSQLQueryModel model) throws Exception {
      return MAPPER.readValue(MAPPER.writeValueAsString(model), AdvancedSQLQueryModel.class);
   }

   private static Map<String, AssetEntry> tables() {
      AssetEntry table = new AssetEntry(AssetRepository.QUERY_SCOPE, AssetEntry.Type.PHYSICAL_TABLE,
                                        "EMP", null);
      table.setProperty("source", "EMP");
      Map<String, AssetEntry> tables = new LinkedHashMap<>();
      // not keyed by the table name, so the columns' types are not looked up
      tables.put("not-a-table", table);
      return tables;
   }

   private static XRepository newRepository() {
      try {
         return repository();
      }
      catch(Exception ex) {
         throw new IllegalStateException(ex);
      }
   }

   // the metadata of EMP for JDBCUtil.fixUniformSQLInfo, and the columns of a generated sql,
   // read from a prepared statement as JDBCHandler does
   private static XRepository repository() throws Exception {
      XRepository repository = mock(XRepository.class);
      when(repository.getMetaData(any(), any(), any(), anyBoolean(), any())).thenAnswer(inv -> {
         XNode mtype = inv.getArgument(2);

         if("DBPROPERTIES".equals(mtype.getAttribute("type"))) {
            return new XNode("properties");
         }

         if(!"SQL".equals(mtype.getAttribute("type"))) {
            XTypeNode result = new XTypeNode("Result");

            for(String column : new String[] { "ID", LONG }) {
               result.addChild(XSchema.createPrimitiveType(column, Integer.class));
            }

            XTypeNode meta = new XTypeNode("meta");
            meta.addChild(result);
            return meta;
         }

         XTypeNode root = new XTypeNode("table");

         try(Connection conn = derby().getConnection();
             PreparedStatement stmt = conn.prepareStatement((String) mtype.getAttribute("sql")))
         {
            ResultSetMetaData meta = stmt.getMetaData();

            for(int i = 1; i <= meta.getColumnCount(); i++) {
               XTypeNode node = XSchema.createPrimitiveType(XSchema.INTEGER);
               node.setName(meta.getColumnLabel(i));
               root.addChild(node, false, false);
            }
         }

         return root;
      });

      return repository;
   }

   private static JDBCDataSource dataSource() {
      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("bug77711dependents");
      ds.setDriver("org.apache.derby.jdbc.EmbeddedDriver");
      ds.setURL("jdbc:derby:" + DB);
      ds.setRequireLogin(false);
      return ds;
   }

   private static DataSource derby() {
      EmbeddedDataSource ds = new EmbeddedDataSource();
      ds.setDatabaseName(DB);
      ds.setCreateDatabase("create");
      return ds;
   }
}
