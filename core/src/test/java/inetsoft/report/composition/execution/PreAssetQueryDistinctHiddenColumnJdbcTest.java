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

import inetsoft.mv.MVManager;
import inetsoft.report.TableLens;
import inetsoft.report.XSessionManager;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.storage.BlobStorageManager;
import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.SQLBoundTableAssemblyInfo;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.ExpressionRef;
import inetsoft.uql.jdbc.*;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.*;
import inetsoft.util.Plugins;
import inetsoft.util.credential.CredentialService;
import org.apache.derby.jdbc.EmbeddedDataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import javax.sql.DataSource;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.Statement;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #82380: a distinct table whose SQL select list has a hidden column (a base column of a
 * visible script column, or a column only a post-processed condition needs) gets DISTINCT in
 * the SQL over the hidden column. The table must still be de-duplicated on its visible
 * columns. A table whose SQL select list holds only visible columns keeps the plain SQL
 * DISTINCT and its rows.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  LibManagerTestConfiguration.class,
                                  PreAssetQueryDistinctHiddenColumnJdbcTest.JdbcConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class PreAssetQueryDistinctHiddenColumnJdbcTest {
   private static final String DB = "memory:preassetquerydistincthidden";

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
         return mock(XRepository.class);
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

   // two values that differ only by case (ca, CA): Derby compares case-sensitively
   @BeforeAll
   static void createTables() throws Exception {
      try(Connection conn = derby().getConnection(); Statement stmt = conn.createStatement()) {
         try {
            stmt.executeUpdate("drop table cu");
         }
         catch(Exception ignore) {
            // first run
         }

         stmt.executeUpdate("create table cu (id int, state varchar(10))");
         stmt.executeUpdate("insert into cu values (1, 'ca'), (2, 'ca'), (3, 'ca'), " +
                               "(4, 'ca'), (5, 'ny'), (6, 'ny'), (7, 'CA')");
      }
   }

   /**
    * The reported case: the base columns are hidden and a visible script column reads them.
    * The SQL selects the hidden state with DISTINCT, and the script folds ca and CA into CA.
    */
   @Test
   void scriptColumnOverHiddenBaseColumnsIsDistinctOnItsValues() throws Exception {
      Built t = table();
      hide(t, "id");
      hide(t, "state");
      addColumn(t, script("U", "field['state'].toUpperCase()", XSchema.STRING));

      Result result = run(t);

      assertTrue(result.sql.startsWith("select distinct"), result.sql);
      assertEquals(List.of("CA", "NY"), result.rows);
   }

   /**
    * A hidden script column used only by a condition that runs after the query puts its base
    * column (id) in the SQL select list, so the SQL DISTINCT keeps one row per id.
    */
   @Test
   void hiddenColumnOfPostConditionIsNotPartOfTheDistinct() throws Exception {
      Built t = table();
      hide(t, "id");
      ColumnRef p = script("P", "field['id']", XSchema.INTEGER);
      p.setVisible(false);
      addColumn(t, p);
      t.table.setPreConditionList(greaterThanZero(p));

      Result result = run(t);

      assertEquals(List.of("CA", "ca", "ny"), result.rows);
   }

   /**
    * Control: every column of the SQL select list is visible. A visible script column can
    * not make two SQL-distinct rows equal, so the rows stay as SQL DISTINCT returns them,
    * including ca and CA, which a case-insensitive in-memory distinct would merge.
    */
   @Test
   void visibleBaseColumnWithScriptColumnKeepsSqlDistinctRows() throws Exception {
      Built t = table();
      hide(t, "id");
      addColumn(t, script("U", "field['state'].toUpperCase()", XSchema.STRING));

      Result result = run(t);

      assertTrue(result.sql.startsWith("select distinct"), result.sql);
      assertEquals(List.of("CA|CA", "ca|CA", "ny|NY"), result.rows);
   }

   /** Control: a plain distinct table is merged into the SQL as before. */
   @Test
   void plainVisibleColumnsArePushedToSql() throws Exception {
      Built t = table();
      hide(t, "id");

      Result result = run(t);

      assertTrue(result.sql.startsWith("select distinct cu.state"), result.sql);
      assertFalse(result.sql.contains("cu.id"), result.sql);
      assertEquals(List.of("CA", "ca", "ny"), result.rows);
   }

   private static final class Built {
      final Worksheet ws = new Worksheet();
      SQLBoundTableAssembly table;
   }

   private static final class Result {
      String sql;
      List<String> rows;
   }

   private static Result run(Built t) throws Exception {
      t.table.setDistinct(true);
      AssetQuery query = AssetQuery.createAssetQuery(
         t.table, AssetQuerySandbox.RUNTIME_MODE, new AssetQuerySandbox(t.ws), false, -1L,
         true, false);
      TableLens lens = query.getTableLens(new VariableTable());
      assertNotNull(lens, "the query failed, see the log");
      lens.moreRows(Integer.MAX_VALUE);
      List<String> rows = new ArrayList<>();

      for(int r = 1; r < lens.getRowCount(); r++) {
         StringJoiner row = new StringJoiner("|");

         for(int c = 0; c < lens.getColCount(); c++) {
            row.add(String.valueOf(lens.getObject(r, c)));
         }

         rows.add(row.toString());
      }

      // the row order is not part of the contract
      Collections.sort(rows);
      Result result = new Result();
      result.sql = ((PreAssetQuery) query).getUniformSQL().getSQLString().trim()
         .replaceAll("\\s+", " ");
      result.rows = rows;
      return result;
   }

   private static ConditionList greaterThanZero(ColumnRef column) {
      AssetCondition cond = new AssetCondition(XSchema.INTEGER);
      cond.setOperation(XCondition.GREATER_THAN);
      cond.addValue(0);
      ConditionList conds = new ConditionList();
      conds.append(new ConditionItem(column, cond, 0));
      return conds;
   }

   private static ColumnRef script(String name, String expression, String type) {
      ExpressionRef exp = new ExpressionRef();
      exp.setName(name);
      exp.setExpression(expression);
      ColumnRef column = new ColumnRef(exp);
      column.setDataType(type);
      // an expression column is a SQL expression by default; this one is a script
      column.setSQL(false);
      return column;
   }

   // the public column selection is built from the private one and leaves hidden columns out
   private static void hide(Built t, String name) {
      ColumnSelection columns = t.table.getColumnSelection(false);
      find(columns, name).setVisible(false);
      t.table.setColumnSelection(columns, false);
   }

   private static ColumnRef find(ColumnSelection columns, String name) {
      for(int i = 0; i < columns.getAttributeCount(); i++) {
         ColumnRef column = (ColumnRef) columns.getAttribute(i);

         if(name.equals(column.getAlias()) || name.equals(column.getAttribute())) {
            return column;
         }
      }

      throw new AssertionError("no column " + name + " in " + columns);
   }

   private static void addColumn(Built t, ColumnRef column) {
      ColumnSelection columns = t.table.getColumnSelection(false);
      columns.addAttribute(column);
      t.table.setColumnSelection(columns, false);
   }

   private static Built table() throws Exception {
      String text = "select cu.id, cu.state from cu";
      UniformSQL usql = new UniformSQL();
      Method parse = UniformSQL.class.getDeclaredMethod("parse", String.class, int.class,
                                                        long.class);
      parse.setAccessible(true);
      // parse(String, int, long) is package private, PARSE_ALL is 0
      parse.invoke(usql, text, 0, 4000L);
      usql.setSQLString(text, false);
      assertEquals(UniformSQL.PARSE_SUCCESS, usql.getParseResult(), text);

      JDBCDataSource ds = dataSource();
      usql.setDataSource(ds);
      JDBCQuery query = new JDBCQuery();
      query.setName("T1");
      query.setUserQuery(true);
      query.setDataSource(ds);
      query.setSQLDefinition(usql);

      Built t = new Built();
      SQLBoundTableAssembly table = new SQLBoundTableAssembly(t.ws, "T1");
      SQLBoundTableAssemblyInfo info = (SQLBoundTableAssemblyInfo) table.getTableInfo();
      info.setQuery(query);
      info.setSourceInfo(new SourceInfo(SourceInfo.DATASOURCE, ds.getName(), ds.getName()));
      table.setSQLEdited(true);
      table.setProperty("no_cache", "true");

      ColumnSelection columns = new ColumnSelection();
      String[][] cols = { { "id", XSchema.INTEGER }, { "state", XSchema.STRING } };

      for(String[] col : cols) {
         AttributeRef attr = new AttributeRef(col[0]);
         attr.setDataType(col[1]);
         ColumnRef ref = new ColumnRef(attr);
         ref.setDataType(col[1]);
         columns.addAttribute(ref);
      }

      table.setColumnSelection(columns, false);
      table.setColumnSelection((ColumnSelection) columns.clone(), true);
      t.ws.addAssembly(table);
      t.table = table;
      return t;
   }

   private static JDBCDataSource dataSource() {
      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("preassetquerydistincthidden");
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
