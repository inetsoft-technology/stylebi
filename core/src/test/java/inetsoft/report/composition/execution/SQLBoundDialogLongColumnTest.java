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

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.report.composition.event.AssetEventUtil;
import inetsoft.report.lens.xnode.XNodeTableLens;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.storage.BlobStorageManager;
import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.SQLBoundTableAssemblyInfo;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.jdbc.*;
import inetsoft.uql.jdbc.util.JDBCUtil;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.schema.XTypeNode;
import inetsoft.uql.util.*;
import inetsoft.util.Plugins;
import inetsoft.util.credential.CredentialService;
import inetsoft.web.composer.model.ws.*;
import inetsoft.web.composer.ws.assembly.WorksheetEventUtil;
import inetsoft.web.composer.ws.dialog.SQLQueryDialogService;
import inetsoft.web.portal.controller.database.*;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.apache.derby.jdbc.EmbeddedDataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
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
 * Bug #77711. A SQL-bound table made in the SQL query dialog's simple mode is built from a
 * query that wasn't parsed, so QueryManagerService.getColumnSelection names its columns by
 * the database's labels of the generated sql. A long name is generated as ALIAS_n, but the
 * query stores the name, and the merged query finds a column by the stored name. So the
 * column must be named by the stored name, or the merged query drops it.
 *
 * Run on Derby with limit.alias.length=true, which rejects the long name as Oracle does.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  SQLBoundDialogLongColumnTest.JdbcConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class SQLBoundDialogLongColumnTest {
   private static final String DB = "memory:bug77711sqlbound";
   // over the 28 bytes of isValidAlias
   private static final String LONG = "CUSTOMER_ACCOUNT_OPENING_DATE_LOCAL";
   private static final String RID = "rq-77711-bound";
   private static final String WS = "ws-77711-bound";
   private static final XRepository REPOSITORY = newRepository();

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
      public XRepository xRepository() throws Exception {
         return REPOSITORY;
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

   @AfterEach
   void resetLimit() {
      SreeEnv.setProperty("limit.alias.length", null);
   }

   @Test
   void dialogTableKeepsTheLongColumn() throws Exception {
      SreeEnv.setProperty("limit.alias.length", "true");
      JDBCDataSource ds = dataSource();
      UniformSQL sql = JDBCUtil.createSQL(ds, tables(), new String[] { "EMP.ID", "EMP." + LONG },
                                          new XJoin[0], new ArrayList<>(), null);
      // so the columns are read from the metadata of the generated sql
      assertEquals(UniformSQL.PARSE_INIT, sql.getParseResult());
      JDBCQuery query = new JDBCQuery();
      query.setName("bug77711");
      query.setUserQuery(true);
      query.setDataSource(ds);
      query.setSQLDefinition(sql);

      Worksheet ws = new Worksheet();
      SQLBoundTableAssembly table = new SQLBoundTableAssembly(ws, "T1");
      SQLBoundTableAssemblyInfo info = (SQLBoundTableAssemblyInfo) table.getTableInfo();
      info.setQuery(query);
      info.setSourceInfo(new SourceInfo(SourceInfo.DATASOURCE, ds.getName(), ds.getName()));
      ws.addAssembly(table);

      // what SQLQueryDialogService.setUpTable does
      QueryManagerService service = new QueryManagerService(
         mock(RuntimeQueryService.class), REPOSITORY, mock(DataSourceService.class),
         mock(SecurityEngine.class), mock(ColumnCache.class));
      ColumnSelection columns =
         service.getColumnSelection(query, new VariableTable(), table, null, new HashMap<>());
      table.setColumnSelection(columns);
      table.setSQLEdited(false);

      List<String> names = new ArrayList<>();

      for(int i = 0; i < columns.getAttributeCount(); i++) {
         names.add(columns.getAttribute(i).getAttribute());
      }

      Collections.sort(names);
      assertEquals(List.of(LONG, "ID"), names);

      SQLBoundQuery bound = new SQLBoundQuery(
         AssetQuerySandbox.RUNTIME_MODE, new AssetQuerySandbox(ws), table, false, false);
      bound.merge(new VariableTable());
      String text = bound.getQuery().getSQLAsString();
      assertTrue(text.contains(LONG), text);
      assertEquals(List.of("[10, 1]", "[20, 2]"), rows(bound.getQuery()));
   }

   /**
    * A table saved before the fix stores ALIAS_0 for the long name and has a worksheet
    * condition on the ALIAS_0 column. An OK in the simple mode of the dialog renames the
    * column to the name, and the condition follows it, so the table is still filtered.
    */
   @Test
   void simpleOkKeepsTheConditionOfARenamedColumn() throws Exception {
      SreeEnv.setProperty("limit.alias.length", "true");
      JDBCDataSource ds = dataSource();
      UniformSQL sql = JDBCUtil.createSQL(ds, tables(), new String[] { "EMP.ID", "EMP." + LONG },
                                          new XJoin[0], new ArrayList<>(), null);
      sql.getSelection().setAlias(1, "ALIAS_0");
      JDBCQuery query = new JDBCQuery();
      query.setName("bug77711");
      query.setUserQuery(true);
      query.setDataSource(ds);
      query.setSQLDefinition(sql);

      Worksheet ws = new Worksheet();
      SQLBoundTableAssembly table = new SQLBoundTableAssembly(ws, "T1");
      SQLBoundTableAssemblyInfo info = (SQLBoundTableAssemblyInfo) table.getTableInfo();
      info.setQuery(query);
      info.setSourceInfo(new SourceInfo(SourceInfo.DATASOURCE, ds.getFullName(), ds.getFullName()));
      ColumnSelection columns = new ColumnSelection();

      for(String name : new String[] { "ALIAS_0", "ID" }) {
         ColumnRef column = new ColumnRef(new AttributeRef(null, name));
         column.setDataType(XSchema.INTEGER);
         // as on a worksheet that was opened
         column.setOldName(name);
         columns.addAttribute(column);
      }

      table.setColumnSelection(columns);
      AssetCondition condition = new AssetCondition(XSchema.INTEGER);
      condition.setOperation(XCondition.EQUAL_TO);
      condition.addValue(10);
      ConditionList conds = new ConditionList();
      conds.append(new ConditionItem(new ColumnRef(new AttributeRef(null, "ALIAS_0")), condition, 0));
      table.setPreConditionList(conds);
      ws.addAssembly(table);

      // the table as it was saved
      assertEquals(List.of("[10, 1]"), run(ws, table));

      // open the dialog and press OK in simple mode, without a change
      RuntimeQueryService rqs = mock(RuntimeQueryService.class);
      RuntimeQueryService.RuntimeXQuery runtimeQuery =
         new RuntimeQueryService.RuntimeXQuery(query.clone(), RID, ds.getFullName());
      runtimeQuery.setVariables(new VariableTable());
      runtimeQuery.initQueryAliasMapping();
      when(rqs.getRuntimeQuery(RID)).thenReturn(runtimeQuery);
      when(REPOSITORY.getDataSource(ds.getFullName())).thenReturn(ds);
      SecurityEngine security = mock(SecurityEngine.class);
      when(security.checkPermission(any(), any(), any(String.class), any())).thenReturn(true);
      QueryManagerService service = new QueryManagerService(
         rqs, REPOSITORY, mock(DataSourceService.class), security, mock(ColumnCache.class));
      ViewsheetService wsEngine = mock(ViewsheetService.class);
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      AssetQuerySandbox box = mock(AssetQuerySandbox.class);
      when(box.getVariableTable()).thenReturn(new VariableTable());
      when(rws.getAssetQuerySandbox()).thenReturn(box);
      when(wsEngine.getWorksheet(eq(WS), any())).thenReturn(rws);
      when(wsEngine.getAssetRepository()).thenReturn(mock(AssetRepository.class));
      SQLQueryDialogService dialog = new SQLQueryDialogService(
         wsEngine, service, mock(QueryGraphModelService.class), REPOSITORY, security);

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
      model.setCloseDialog(true);

      // the refreshes after the edit need a running worksheet
      try(MockedStatic<WorksheetEventUtil> ignored = mockStatic(WorksheetEventUtil.class);
          MockedStatic<AssetEventUtil> ignored2 = mockStatic(AssetEventUtil.class))
      {
         dialog.setModel(WS, model, () -> "admin", mock(CommandDispatcher.class));
      }

      assertNotNull(table.getColumnSelection().getAttribute(LONG));
      ConditionList renamed = (ConditionList) table.getPreConditionList();
      assertEquals(LONG, renamed.getConditionItem(0).getAttribute().getAttribute());
      assertEquals(List.of("[10, 1]"), run(ws, table));
   }

   // the rows of the merged query of the table
   private static List<String> run(Worksheet ws, SQLBoundTableAssembly table) throws Exception {
      SQLBoundQuery bound = new SQLBoundQuery(
         AssetQuerySandbox.RUNTIME_MODE, new AssetQuerySandbox(ws), table, false, false);
      // the merged condition values are variables of the merge
      VariableTable vars = new VariableTable();
      bound.merge(vars);
      return rows(bound.getQuery(), vars);
   }

   // the values of each row, largest first, since the generated sql sorts its columns
   private static List<String> rows(JDBCQuery query) throws Exception {
      return rows(query, new VariableTable());
   }

   private static List<String> rows(JDBCQuery query, VariableTable vars) throws Exception {
      JDBCHandler handler = new JDBCHandler();
      handler.connect(query.getDataSource(), vars);
      XNode node = handler.execute(query, vars, null, null);
      XNodeTableLens lens = new XNodeTableLens(node);
      lens.moreRows(Integer.MAX_VALUE);
      List<String> rows = new ArrayList<>();

      for(int r = 1; r < lens.getRowCount(); r++) {
         List<Integer> row = new ArrayList<>();

         for(int c = 0; c < lens.getColCount(); c++) {
            row.add(((Number) lens.getObject(r, c)).intValue());
         }

         row.sort(Collections.reverseOrder());
         rows.add(row.toString());
      }

      Collections.sort(rows);
      return rows;
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

   // the columns of a generated sql, read from a prepared statement as JDBCHandler does
   private static XRepository repository() throws Exception {
      XRepository repository = mock(XRepository.class);
      when(repository.getMetaData(any(), any(), any(), anyBoolean(), any())).thenAnswer(inv -> {
         XNode mtype = inv.getArgument(2);

         if(!"SQL".equals(mtype.getAttribute("type"))) {
            return new XNode("properties");
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
      ds.setName("bug77711sqlbound");
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
