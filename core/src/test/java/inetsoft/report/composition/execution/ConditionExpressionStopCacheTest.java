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
import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.storage.BlobStorageManager;
import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.SQLBoundTableAssemblyInfo;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.jdbc.*;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.*;
import inetsoft.uql.viewsheet.*;
import inetsoft.util.Plugins;
import inetsoft.util.credential.CredentialService;
import inetsoft.util.script.graal.ScriptStopTestSupport;
import inetsoft.util.script.graal.ScriptTimeoutGuard;
import inetsoft.util.script.graal.pool.PoolConfig;
import inetsoft.util.script.graal.pool.WorksheetScriptEnv;
import org.apache.derby.jdbc.EmbeddedDataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import javax.sql.DataSource;
import java.lang.reflect.*;
import java.sql.Connection;
import java.sql.Statement;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #78212: a viewsheet table over a worksheet table whose condition value is a script
 * expression stopped by the real script timeout (the script runs for 1.5 s, past a 1 s
 * timeout). The four places that evaluate a condition expression value rebuilt the failure
 * as a new script exception without the stop flag, so {@code ViewsheetSandbox.getData}
 * cached NULL and the table kept showing no data after the timeout was raised. On a
 * database-bound table the condition is merged into the SQL, where the failure was replaced
 * by {@code field = field}: the query returned and cached every row without an error.
 * Now the read fails with the stop, nothing is cached, and the next read gets rows 6..10.
 * Slow: every case waits for the real timeout and then runs the slow script again.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  LibManagerTestConfiguration.class,
                                  ConditionExpressionStopCacheTest.TestConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("slow")
class ConditionExpressionStopCacheTest {
   private static final String DB = "memory:bug78212";

   @Configuration
   static class TestConfig {
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
         return new Plugins(blobStorageManager.getStorage("plugins", true), cluster, eventPublisher);
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

      // a cached table is checked against its materialized view (none here)
      @Bean
      public MVManager mvManager() {
         return mock(MVManager.class);
      }

      // runs the query's SQL on the Derby table
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
         return new XSessionManager(dataService, sessionService, mock(DataSourceRegistry.class));
      }
   }

   @BeforeAll
   static void createTable() throws Exception {
      try(Connection conn = derby().getConnection(); Statement stmt = conn.createStatement()) {
         try {
            stmt.executeUpdate("drop table small");
         }
         catch(Exception ignore) {
         }

         stmt.executeUpdate("create table small (id int, g int)");

         for(int i = 1; i <= 10; i++) {
            stmt.executeUpdate("insert into small values (" + i + ", " + i + ")");
         }
      }
   }

   @BeforeEach
   void setUp() throws Exception {
      previousTimeout = ScriptStopTestSupport.setTimeout("1");
      SreeEnv.setProperty(PoolConfig.ENABLED, "false");
      AssetDataCache.getCache().clearCache();

      // the first script of a cold JVM can outlast its timeout without being stopped, run
      // one before the test's slow script
      if(!warmedUp) {
         Worksheet ws = embedded("0", false);
         ids(vsTable(ws, box(ws), "S", "v").getData("TableV"));
         AssetDataCache.getCache().clearCache();
         warmedUp = true;
      }
   }

   @AfterEach
   void tearDown() throws Exception {
      Thread.interrupted();
      ScriptStopTestSupport.setTimeout(previousTimeout);
      SreeEnv.remove(PoolConfig.ENABLED);

      for(AssetQuerySandbox box : boxes) {
         if(box.peekScriptEnv() instanceof WorksheetScriptEnv env) {
            env.retire();
         }
      }

      boxes.clear();
      AssetDataCache.getCache().clearCache();
   }

   /** ConditionGroup.getExpressionVal: the pre-condition of an embedded worksheet table. */
   @ParameterizedTest
   @ValueSource(booleans = { false, true })
   void stoppedPreConditionIsNotCached(boolean pool) throws Exception {
      SreeEnv.setProperty(PoolConfig.ENABLED, pool + "");
      Worksheet ws = embedded(slow(), false);
      assertStopNotCached(vsTable(ws, box(ws), "S", "v"));
   }

   /** AssetQuery.AssetConditionGroup2: a post-aggregate condition. */
   @Test
   void stoppedPostConditionIsNotCached() throws Exception {
      Worksheet ws = embedded(slow(), true);
      assertStopNotCached(vsTable(ws, box(ws), "S", "v"));
   }

   /** VSAQuery.executeScript: the viewsheet table's own condition. */
   @Test
   void stoppedViewsheetConditionIsNotCached() throws Exception {
      Worksheet ws = embedded("0", false);
      ViewsheetSandbox vbox = vsTable(ws, box(ws), "S", "v");
      TableVSAssembly table = (TableVSAssembly) vbox.getViewsheet().getAssembly("TableV");
      table.setPreConditionList(
         conditions(new ColumnRef(new AttributeRef(null, "id")), slow()));
      assertStopNotCached(vbox);
   }

   /**
    * PreAssetQuery.execScriptExpression and PreConditionListHandler.createCondNode: the
    * pre-condition of a database-bound table, merged into the SQL. A new session reads first,
    * so that a correct read of the old sandbox could not refill the shared cache.
    */
   @ParameterizedTest
   @ValueSource(booleans = { false, true })
   void stoppedMergedConditionReturnsNoUnfilteredRows(boolean sqlEdited) throws Exception {
      Worksheet ws = new Worksheet();
      SQLBoundTableAssembly t = sqlTable(ws, sqlEdited);
      t.setPreConditionList(conditions(t.getColumnSelection(false).getAttribute("id"), slow()));
      ViewsheetSandbox vbox = vsTable(ws, box(ws), "T1", "g");

      assertStop(() -> vbox.getData("TableV"), "the first read");
      assertNull(dmap(vbox).get("TableV", DataMap.NORMAL), "the stop is cached");

      ScriptStopTestSupport.setTimeout("10");
      assertEquals(List.of(6, 7, 8, 9, 10), ids(vsTable(ws, box(ws), "T1", "g").getData("TableV")),
                   "a new session");
      assertEquals(List.of(6, 7, 8, 9, 10), ids(vbox.getData("TableV")), "the same sandbox");
   }

   /** An ordinary script error is no stop and is still cached as NULL. */
   @Test
   void conditionErrorIsStillCached() throws Exception {
      Worksheet ws = embedded("throw new Error('bad'); /*e78212_" + NONCE.incrementAndGet() + "*/",
                              false);
      ViewsheetSandbox vbox = vsTable(ws, box(ws), "S", "v");

      Throwable ex = assertThrows(Throwable.class, () -> vbox.getData("TableV"));
      assertFalse(ScriptTimeoutGuard.isStop(ex), "an error is a stop: " + ex);
      assertEquals("__null__", String.valueOf(dmap(vbox).get("TableV", DataMap.NORMAL)));
      assertNull(vbox.getData("TableV"));
   }

   /** An ordinary error in a merged condition still ignores the condition (as before). */
   @Test
   void mergedConditionErrorIsStillIgnored() throws Exception {
      Worksheet ws = new Worksheet();
      SQLBoundTableAssembly t = sqlTable(ws, false);
      t.setPreConditionList(conditions(t.getColumnSelection(false).getAttribute("id"),
         "throw new Error('bad'); /*m78212_" + NONCE.incrementAndGet() + "*/"));
      ViewsheetSandbox vbox = vsTable(ws, box(ws), "T1", "g");

      assertEquals(List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10), ids(vbox.getData("TableV")));
   }

   /**
    * The first read fails with the stop and caches nothing; with the timeout raised the same
    * sandbox gets rows 6..10.
    */
   private void assertStopNotCached(ViewsheetSandbox vbox) throws Exception {
      assertStop(() -> vbox.getData("TableV"), "the first read");
      assertNull(dmap(vbox).get("TableV", DataMap.NORMAL), "the stop is cached");

      ScriptStopTestSupport.setTimeout("10");
      assertEquals(List.of(6, 7, 8, 9, 10), ids(vbox.getData("TableV")), "the second read");
   }

   private static void assertStop(Executable read, String what) {
      Throwable ex = assertThrows(Throwable.class, read, what + ": no stop");
      assertTrue(ScriptTimeoutGuard.isStop(ex), what + ": not the stop: " + ex);
   }

   /** A script that runs for 1.5 s and returns 5, unique so that no compiled copy is reused. */
   private static String slow() {
      return "var t0 = Date.now(); while(Date.now() - t0 < 1500){}; 5 /*s78212_" +
         NONCE.incrementAndGet() + "*/";
   }

   /** {@code ref > [exp]}. */
   private static ConditionList conditions(DataRef ref, String exp) {
      ExpressionValue eval = new ExpressionValue();
      eval.setType(ExpressionValue.JAVASCRIPT);
      eval.setExpression(exp);
      AssetCondition cond = new AssetCondition();
      cond.setOperation(XCondition.GREATER_THAN);
      cond.setType(XSchema.INTEGER);
      cond.addValue(eval);
      ConditionList list = new ConditionList();
      list.append(new ConditionItem(ref, cond, 0));
      return list;
   }

   /**
    * S: embedded id 1..10, v = id, with the pre-condition {@code id > [exp]}, or, if
    * {@code post}, grouped by id with sum(v) and the post-condition {@code sum(v) > [exp]}.
    */
   private static Worksheet embedded(String exp, boolean post) {
      Worksheet ws = new Worksheet();
      Object[][] data = new Object[11][];
      data[0] = new Object[] { "id", "v" };

      for(int i = 1; i <= 10; i++) {
         data[i] = new Object[] { i, i };
      }

      EmbeddedTableAssembly s = new EmbeddedTableAssembly(ws, "S");
      s.setEmbeddedData(new XEmbeddedTable(new String[] { XSchema.INTEGER, XSchema.INTEGER }, data));
      ws.addAssembly(s);
      ColumnSelection columns = s.getColumnSelection(false);

      if(post) {
         AggregateInfo info = new AggregateInfo();
         info.addGroup(new GroupRef(columns.getAttribute("id")));
         AggregateRef agg = new AggregateRef(columns.getAttribute("v"), AggregateFormula.SUM);
         info.addAggregate(agg);
         s.setAggregateInfo(info);
         s.setPostConditionList(conditions(agg, exp));
      }
      else {
         s.setPreConditionList(conditions(columns.getAttribute("id"), exp));
      }

      return ws;
   }

   /** T1: {@code select small.id, small.g from small} on the Derby table. */
   private static SQLBoundTableAssembly sqlTable(Worksheet ws, boolean sqlEdited)
      throws Exception
   {
      String text = "select small.id, small.g from small";
      UniformSQL usql = new UniformSQL();
      Method parse = UniformSQL.class.getDeclaredMethod("parse", String.class, int.class, long.class);
      parse.setAccessible(true);
      parse.invoke(usql, text, 0, 4000L);
      usql.setSQLString(text, false);
      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("bug78212");
      ds.setDriver("org.apache.derby.jdbc.EmbeddedDriver");
      ds.setURL("jdbc:derby:" + DB);
      ds.setRequireLogin(false);
      usql.setDataSource(ds);
      JDBCQuery query = new JDBCQuery();
      query.setName("T1");
      query.setUserQuery(true);
      query.setDataSource(ds);
      query.setSQLDefinition(usql);
      SQLBoundTableAssembly table = new SQLBoundTableAssembly(ws, "T1");
      SQLBoundTableAssemblyInfo info = (SQLBoundTableAssemblyInfo) table.getTableInfo();
      info.setQuery(query);
      info.setSourceInfo(new SourceInfo(SourceInfo.DATASOURCE, ds.getName(), ds.getName()));
      table.setSQLEdited(sqlEdited);
      ColumnSelection columns = new ColumnSelection();

      for(String column : new String[] { "id", "g" }) {
         ColumnRef ref = new ColumnRef(new AttributeRef(column));
         ref.setDataType(XSchema.INTEGER);
         columns.addAttribute(ref);
      }

      table.setColumnSelection(columns, false);
      table.setColumnSelection((ColumnSelection) columns.clone(), true);
      ws.addAssembly(table);
      return table;
   }

   /**
    * A viewsheet with the table TableV of the columns id and {@code other} of the worksheet
    * table {@code source}.
    */
   private static ViewsheetSandbox vsTable(Worksheet ws, AssetQuerySandbox box, String source,
                                           String other)
      throws Exception
   {
      Viewsheet vs = new Viewsheet();
      Field wsField = Viewsheet.class.getDeclaredField("ws");
      wsField.setAccessible(true);
      wsField.set(vs, ws);
      TableVSAssembly table = new TableVSAssembly(vs, "TableV");
      table.setSourceInfo(new SourceInfo(XSourceInfo.ASSET, null, source));
      ColumnSelection cols = new ColumnSelection();

      for(String name : new String[] { "id", other }) {
         cols.addAttribute(new ColumnRef(new AttributeRef(null, name)));
      }

      table.setColumnSelection(cols);
      vs.addAssembly(table);
      AssetEntry entry = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET, "test/Bug78212",
         null, OrganizationManager.getInstance().getCurrentOrgID());
      ViewsheetSandbox vbox = new ViewsheetSandbox(vs, AbstractSheet.SHEET_RUNTIME_MODE, null,
                                                   false, entry);
      Field wbox = ViewsheetSandbox.class.getDeclaredField("wbox");
      wbox.setAccessible(true);
      wbox.set(vbox, box);
      return vbox;
   }

   private AssetQuerySandbox box(Worksheet ws) {
      AssetQuerySandbox box = new AssetQuerySandbox(ws);
      boxes.add(box);
      return box;
   }

   private static DataMap dmap(ViewsheetSandbox box) throws Exception {
      Field f = ViewsheetSandbox.class.getDeclaredField("dmap");
      f.setAccessible(true);
      return (DataMap) f.get(box);
   }

   /** The first column's values. */
   private static List<Object> ids(Object data) {
      assertInstanceOf(TableLens.class, data, "no table");
      TableLens t = (TableLens) data;
      List<Object> vals = new ArrayList<>();
      t.moreRows(0);

      for(int r = t.getHeaderRowCount(); t.moreRows(r); r++) {
         Object v = t.getObject(r, 0);
         vals.add(v instanceof Number n ? n.intValue() : v);
      }

      return vals;
   }

   private static DataSource derby() {
      EmbeddedDataSource ds = new EmbeddedDataSource();
      ds.setDatabaseName(DB);
      ds.setCreateDatabase("create");
      return ds;
   }

   private static final AtomicLong NONCE = new AtomicLong();
   private static boolean warmedUp;
   private final List<AssetQuerySandbox> boxes = new ArrayList<>();
   private String previousTimeout;
}
