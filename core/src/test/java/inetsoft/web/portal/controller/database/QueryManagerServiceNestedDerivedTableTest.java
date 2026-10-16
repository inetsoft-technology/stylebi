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
package inetsoft.web.portal.controller.database;

import inetsoft.report.TableLens;
import inetsoft.report.XSessionManager;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.report.composition.execution.SQLBoundQuery;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.storage.BlobStorageManager;
import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.SQLBoundTableAssemblyInfo;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.jdbc.*;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.*;
import inetsoft.util.ConfigurationContext;
import inetsoft.util.Plugins;
import inetsoft.util.credential.CredentialService;
import inetsoft.web.portal.model.database.AdvancedSQLQueryModel;
import org.apache.derby.jdbc.EmbeddedDataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import javax.sql.DataSource;
import java.lang.reflect.Constructor;
import java.security.Principal;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Duration;
import java.util.*;
import java.util.function.IntFunction;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #78119. The query editor's save (the parse and the advanced model it returns), its
 * preview and OK, and the run of a worksheet SQL-bound table, each took minutes for a query
 * with 12 nested FROM derived tables (the reported SQL), because every stringification
 * generated each derived table again, 4 times per nesting level. This runs the real
 * QueryManagerService, JDBCUtil, JDBCHandler, SQLBoundQuery and XSessionManager on embedded
 * Derby. The data source reports itself as PostgreSQL, so PostgreSQLHelper generates the SQL
 * (as on the reported source) and Derby runs it.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  QueryManagerServiceNestedDerivedTableTest.JdbcConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class QueryManagerServiceNestedDerivedTableTest {
   private static final String DB = "memory:bug78119";
   private static final String RID = "rq-78119";
   private static final int ROWS = 700;
   private static final int DEPTH = 12;
   // a step took minutes at depth 12 before the fix, and takes milliseconds now
   private static final Duration BUDGET = Duration.ofSeconds(20);
   private static final Principal USER = () -> "admin";
   private static final List<XSessionManager> sessions = new ArrayList<>();

   // the reported sql at depth 12:
   // select * from (select * from ... (select * from "ORDERS") x11 ...) x1) x0
   private static final IntFunction<String> PLAIN = d -> {
      String sql = "select * from \"ORDERS\"";

      for(int i = d - 1; i >= 0; i--) {
         sql = "select * from (" + sql + ") x" + i;
      }

      return sql;
   };

   // each level joins its derived table to ORDERS. The aliases are upper case: PostgreSQLHelper
   // quotes them, and Derby folds the unquoted ones to upper case.
   private static final IntFunction<String> JOIN = d -> {
      String sql = "select O.ID, O.AMT from \"ORDERS\" O";

      for(int i = d - 1; i >= 0; i--) {
         sql = "select X" + i + ".ID, X" + i + ".AMT from (" + sql + ") X" + i +
            " inner join \"ORDERS\" O" + i + " on X" + i + ".ID = O" + i + ".ID";
      }

      return sql;
   };

   @Configuration
   static class JdbcConfig {
      // JDBCDataSource's constructor needs CredentialService, whose constructor is
      // package-private
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

      @Bean(destroyMethod = "")
      public ConnectionPoolFactory connectionPoolFactory() {
         ConnectionPoolFactory factory = mock(ConnectionPoolFactory.class);
         DataSource derby = derby();
         when(factory.getConnectionPool(any(), any())).thenReturn(derby);
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

      // the table metadata (SQLTypes) is read through XSessionManager.getSessionManager()
      @Bean
      public XSessionManager xSessionManager() {
         XSessionManager sessionManager = mock(XSessionManager.class);
         when(sessionManager.getSession()).thenReturn("bug78119");
         return sessionManager;
      }
   }

   @BeforeAll
   static void createTable() throws Exception {
      try(Connection conn = derby().getConnection(); Statement stmt = conn.createStatement()) {
         try {
            stmt.executeUpdate("drop table \"ORDERS\"");
         }
         catch(Exception ignore) {
            // first run
         }

         stmt.executeUpdate("create table \"ORDERS\" (ID int not null primary key, AMT int)");

         for(int i = 1; i <= ROWS; i++) {
            stmt.addBatch("insert into \"ORDERS\" values (" + i + ", " + (i * 10) + ")");
         }

         stmt.executeBatch();
      }
   }

   @BeforeEach
   void setUp() throws Exception {
      reset(repository);
      when(repository.getMetaData(any(), any(), any(), anyBoolean(), any())).thenAnswer(
         inv -> handler(inv.getArgument(1)).getMetaData(inv.getArgument(2)));
      // the steps XEngine.execute takes for a JDBC query
      when(repository.execute(any(), any(XQuery.class), any(), any(), anyBoolean(), any()))
         .thenAnswer(inv -> {
            XQuery query = inv.getArgument(1);
            JDBCHandler handler = handler(query.getDataSource());

            if(inv.<Boolean>getArgument(4)) {
               handler.reset(query);
            }

            ((UniformSQL) ((JDBCQuery) query).getSQLDefinition())
               .setHint(UniformSQL.HINT_WITHOUT_SORTED_SQL, true);
            query.getVariableNames();
            return handler.execute(query, inv.getArgument(2), inv.getArgument(3), null);
         });
      rqs = mock(RuntimeQueryService.class);
      qms = new QueryManagerService(rqs, repository, mock(DataSourceService.class),
                                    mock(SecurityEngine.class), mock(ColumnCache.class));
   }

   // XSessionManager.finalize() calls tearDown(), which closes the data service
   @AfterEach
   void tearDownSessions() {
      sessions.forEach(XSessionManager::tearDown);
      sessions.clear();
   }

   /**
    * The reported steps on the reported sql: Parse SQL on, enter the sql (the save request
    * parses it and returns the advanced model), Preview, OK.
    */
   @Test
   void queryEditorReportedSql() {
      runQueryEditor("plain", PLAIN.apply(DEPTH), false);
   }

   /**
    * Derived tables joined to a table, with ANSI joins: the column lookups and the join
    * sides used to generate each derived table again too.
    */
   @Test
   void queryEditorJoinAnsi() {
      runQueryEditor("join", JOIN.apply(DEPTH), true);
   }

   /**
    * A worksheet SQL-bound table with the same sql: the merge and the run through
    * XSessionManager and JDBCHandler.
    */
   @Test
   void worksheetSqlBoundTable() {
      for(boolean ansi : new boolean[] { false, true }) {
         for(String shape : new String[] { "plain", "join" }) {
            String sql = (shape.equals("plain") ? PLAIN : JOIN).apply(DEPTH);
            int rows = timed("worksheet " + shape + " ansi=" + ansi,
                             () -> runWorksheet(sql, ansi));
            assertEquals(ROWS, rows, shape + " ansi=" + ansi);
         }
      }
   }

   private void runQueryEditor(String shape, String text, boolean ansi) {
      UniformSQL sql = install(ansi);

      timed(shape + " save: parseSqlString", () -> {
         assertNull(qms.parseSqlString(RID, text, false, true, USER));
         return null;
      });
      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult());
      assertFalse(sql.isLossy());

      AdvancedSQLQueryModel model = timed(shape + " save: getAdvancedQueryModel",
         () -> qms.getAdvancedQueryModel(runtimeQuery, USER));
      assertNotNull(model.getFreeFormSQLPaneModel().getGeneratedSqlString());

      // the header row and the data rows
      String[][] data = timed(shape + " preview", () -> qms.loadQueryData(RID, null, USER));
      assertEquals(ROWS + 1, data.length, shape + " preview");
      data = timed(shape + " preview with sql", () -> qms.loadQueryData(RID, text, USER));
      assertEquals(ROWS + 1, data.length, shape + " preview with sql");

      timed(shape + " OK: updateQuery", () -> {
         qms.updateQuery(RID, model, null, true);
         return null;
      });
      data = timed(shape + " preview after OK", () -> qms.loadQueryData(RID, null, USER));
      assertEquals(ROWS + 1, data.length, shape + " preview after OK");
   }

   private static <T> T timed(String step, ThrowingSupplier<T> body) {
      return assertTimeoutPreemptively(BUDGET, body::get,
         () -> step + " at depth " + DEPTH + " took over " + BUDGET.toSeconds() + " s");
   }

   @FunctionalInterface
   private interface ThrowingSupplier<T> {
      T get() throws Throwable;
   }

   private UniformSQL install(boolean ansi) {
      JDBCDataSource ds = dataSource(ansi);
      UniformSQL sql = new UniformSQL();
      sql.setParseSQL(true);
      sql.setDataSource(ds);
      JDBCQuery query = new JDBCQuery();
      query.setName("bug78119");
      query.setDataSource(ds);
      query.setSQLDefinition(sql);
      runtimeQuery = new RuntimeQueryService.RuntimeXQuery(query, RID, ds.getName());
      // loadQueryData clones the variables
      runtimeQuery.setVariables(new VariableTable());
      when(rqs.getRuntimeQuery(RID)).thenReturn(runtimeQuery);
      return sql;
   }

   // as SQLQueryDialogService.setUpTableWithSQLString builds a sql-edited table
   private static int runWorksheet(String text, boolean ansi) throws Exception {
      JDBCDataSource ds = dataSource(ansi);
      UniformSQL usql = new UniformSQL();
      usql.setDataSource(ds);

      synchronized(usql) {
         usql.setParseSQL(true);
         usql.setSQLString(text, true);
         usql.wait(10000);
      }

      assertEquals(UniformSQL.PARSE_SUCCESS, usql.getParseResult());
      JDBCQuery query = new JDBCQuery();
      query.setName("bug78119ws");
      query.setUserQuery(true);
      query.setDataSource(ds);
      query.setSQLDefinition(usql);

      Worksheet ws = new Worksheet();
      SQLBoundTableAssembly table = new SQLBoundTableAssembly(ws, "T1");
      SQLBoundTableAssemblyInfo info = (SQLBoundTableAssemblyInfo) table.getTableInfo();
      info.setQuery(query);
      info.setSourceInfo(new SourceInfo(SourceInfo.PHYSICAL_TABLE, ds.getName(), ds.getName()));
      table.setSQLEdited(true);
      ColumnSelection selection = new ColumnSelection();

      for(String name : new String[] { "ID", "AMT" }) {
         ColumnRef column = new ColumnRef(new AttributeRef(null, name));
         column.setDataType(XSchema.INTEGER);
         selection.addAttribute(column);
      }

      table.setColumnSelection(selection, false);
      table.setColumnSelection(selection, true);
      ws.addAssembly(table);

      VariableTable vars = new VariableTable();
      SQLBoundQuery bound = new SQLBoundQuery(
         AssetQuerySandbox.RUNTIME_MODE, new AssetQuerySandbox(ws), table, false, false);
      bound.merge(vars);
      JDBCQuery jquery = bound.getQuery();

      XDataService dataService = mock(XDataService.class);
      when(dataService.execute(any(), any(XQuery.class), any(), any(), anyBoolean(), any()))
         .thenAnswer(inv -> {
            XQuery q = inv.getArgument(1);
            JDBCHandler handler = new JDBCHandler();
            handler.connect(q.getDataSource(), inv.getArgument(2));
            return handler.execute(q, inv.getArgument(2), inv.getArgument(3),
                                   inv.getArgument(5));
         });
      XSessionManager session = new XSessionManager(
         dataService, ConfigurationContext.getContext().getSpringBean(XSessionService.class),
         null);
      session.setCacheData(false);
      sessions.add(session);
      TableLens lens = session.getXNodeTableLens(jquery, vars, null, null, null, -1);
      assertNotNull(lens, "query failed, see log");
      lens.moreRows(Integer.MAX_VALUE);
      return lens.getRowCount() - 1;
   }

   private JDBCHandler handler(XDataSource dataSource) throws Exception {
      JDBCHandler handler = new JDBCHandler();
      handler.setRepository(repository);
      handler.connect(dataSource, new VariableTable());
      return handler;
   }

   private static JDBCDataSource dataSource(boolean ansi) {
      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("bug78119-" + ansi);
      ds.setDriver("org.apache.derby.jdbc.EmbeddedDriver");
      ds.setURL("jdbc:derby:" + DB);
      ds.setRequireLogin(false);
      // PostgreSQLHelper generates the sql, as on the reported source, and Derby runs it
      ds.setRuntimeProductName("postgresql");
      ds.setProductVersion("10.0");
      ds.setAnsiJoin(ansi);
      return ds;
   }

   private static DataSource derby() {
      EmbeddedDataSource ds = new EmbeddedDataSource();
      ds.setDatabaseName(DB);
      ds.setCreateDatabase("create");
      return ds;
   }

   @Autowired
   private XRepository repository;
   private RuntimeQueryService rqs;
   private QueryManagerService qms;
   private RuntimeQueryService.RuntimeXQuery runtimeQuery;
}
