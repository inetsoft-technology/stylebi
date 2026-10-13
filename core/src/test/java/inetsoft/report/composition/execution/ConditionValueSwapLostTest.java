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
import inetsoft.util.script.graal.pool.PoolConfig;
import inetsoft.util.script.graal.pool.WorksheetScriptEnv;
import inetsoft.util.stall.LockStallException;
import inetsoft.util.swap.DataUnavailable;
import inetsoft.util.swap.LostSwapFile;
import org.apache.derby.jdbc.EmbeddedDataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
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
 * Bug #78450: a worksheet condition {@code id > [script]} whose script reads worksheet table
 * L, whose data is not available (a lost swap file or a lock stall) or which failed to load.
 * The read of the viewsheet table must fail with the failure in its cause chain and cache
 * nothing, and once L is readable the same sandbox must get rows 6..10. Same fixture as
 * ConditionExpressionStopCacheTest.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  LibManagerTestConfiguration.class,
                                  ConditionValueSwapLostTest.TestConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ConditionValueSwapLostTest {
   private static final String DB = "memory:bug78450";

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

      @Bean
      public MVManager mvManager() {
         return mock(MVManager.class);
      }

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
   void setUp() {
      SreeEnv.setProperty(PoolConfig.ENABLED, "false");
      AssetDataCache.getCache().clearCache();
      lost = new LostSwapFile();
   }

   @AfterEach
   void tearDown() {
      SreeEnv.remove(PoolConfig.ENABLED);

      for(AssetQuerySandbox box : boxes) {
         if(box.peekScriptEnv() instanceof WorksheetScriptEnv env) {
            env.retire();
         }
      }

      boxes.clear();
      AssetDataCache.getCache().clearCache();
      lost.close();
   }

   /** Control: L readable, rows 6..10. */
   @Test
   void controlReadable() throws Exception {
      Worksheet ws = embedded(false);
      LBox box = box(ws);
      box.table.lost = false;
      assertEquals(List.of(6, 7, 8, 9, 10), ids(vsTable(ws, box, "S", "v").getData("TableV")));
   }

   /** ConditionGroup.getExpressionVal: the pre-condition of an embedded table. */
   @ParameterizedTest
   @ValueSource(booleans = { false, true })
   void lostPreCondition(boolean pool) throws Exception {
      SreeEnv.setProperty(PoolConfig.ENABLED, pool + "");
      Worksheet ws = embedded(false);
      LBox box = box(ws);
      assertUnavailableNotCached("embedded pre pool=" + pool, vsTable(ws, box, "S", "v"), box);
   }

   /** AssetQuery.AssetConditionGroup2: a post-aggregate condition. */
   @Test
   void lostPostCondition() throws Exception {
      Worksheet ws = embedded(true);
      LBox box = box(ws);
      assertUnavailableNotCached("embedded post", vsTable(ws, box, "S", "v"), box);
   }

   /** PreAssetQuery.execScriptExpression + PreConditionListHandler.createCondNode. */
   @ParameterizedTest
   @ValueSource(booleans = { false, true })
   void lostMergedCondition(boolean sqlEdited) throws Exception {
      Worksheet ws = new Worksheet();
      SQLBoundTableAssembly t = sqlTable(ws, sqlEdited);
      t.setPreConditionList(conditions(t.getColumnSelection(false).getAttribute("id"), SCRIPT));
      addL(ws);
      LBox box = box(ws);
      assertUnavailableNotCached("merged sqlEdited=" + sqlEdited, vsTable(ws, box, "T1", "g"), box);
   }

   /** VSAQuery.executeScript: the viewsheet table's own condition reads L. */
   @Test
   void lostViewsheetCondition() throws Exception {
      Worksheet ws = embedded(false);
      ((EmbeddedTableAssembly) ws.getAssembly("S")).setPreConditionList(new ConditionList());
      LBox box = box(ws);
      ViewsheetSandbox vbox = vsTable(ws, box, "S", "v");
      TableVSAssembly table = (TableVSAssembly) vbox.getViewsheet().getAssembly("TableV");
      table.setPreConditionList(conditions(new ColumnRef(new AttributeRef(null, "id")), SCRIPT));
      assertUnavailableNotCached("vs condition", vbox, box);
   }

   /** The lock stall axis: L's fetch stalls. */
   @Test
   void stalledPreCondition() throws Exception {
      Worksheet ws = embedded(false);
      LBox box = box(ws);
      box.stall = true;
      assertUnavailableNotCached("stall embedded pre", vsTable(ws, box, "S", "v"), box);
   }

   @Test
   void stalledMergedCondition() throws Exception {
      Worksheet ws = new Worksheet();
      SQLBoundTableAssembly t = sqlTable(ws, false);
      t.setPreConditionList(conditions(t.getColumnSelection(false).getAttribute("id"), SCRIPT));
      addL(ws);
      LBox box = box(ws);
      box.stall = true;
      assertUnavailableNotCached("stall merged", vsTable(ws, box, "T1", "g"), box);
   }

   @Test
   void stalledPostCondition() throws Exception {
      Worksheet ws = embedded(true);
      LBox box = box(ws);
      box.stall = true;
      assertUnavailableNotCached("stall embedded post", vsTable(ws, box, "S", "v"), box);
   }

   /** The load failure axis: L failed to load for a scheduled run (bug #78071). */
   @Test
   void loadFailedPreCondition() throws Exception {
      Worksheet ws = embedded(false);
      LBox box = box(ws);
      box.loadFailed = true;
      assertUnavailableNotCached("load embedded pre", vsTable(ws, box, "S", "v"), box);
   }

   @Test
   void loadFailedPostCondition() throws Exception {
      Worksheet ws = embedded(true);
      LBox box = box(ws);
      box.loadFailed = true;
      assertUnavailableNotCached("load embedded post", vsTable(ws, box, "S", "v"), box);
   }

   @ParameterizedTest
   @ValueSource(booleans = { false, true })
   void loadFailedMergedCondition(boolean sqlEdited) throws Exception {
      Worksheet ws = new Worksheet();
      SQLBoundTableAssembly t = sqlTable(ws, sqlEdited);
      t.setPreConditionList(conditions(t.getColumnSelection(false).getAttribute("id"), SCRIPT));
      addL(ws);
      LBox box = box(ws);
      box.loadFailed = true;
      assertUnavailableNotCached("load merged sqlEdited=" + sqlEdited,
                                 vsTable(ws, box, "T1", "g"), box);
   }

   @Test
   void loadFailedViewsheetCondition() throws Exception {
      Worksheet ws = embedded(false);
      ((EmbeddedTableAssembly) ws.getAssembly("S")).setPreConditionList(new ConditionList());
      LBox box = box(ws);
      box.loadFailed = true;
      ViewsheetSandbox vbox = vsTable(ws, box, "S", "v");
      TableVSAssembly table = (TableVSAssembly) vbox.getViewsheet().getAssembly("TableV");
      table.setPreConditionList(conditions(new ColumnRef(new AttributeRef(null, "id")), SCRIPT));
      assertUnavailableNotCached("load vs condition", vbox, box);
   }

   /**
    * Read 1 must fail with the unavailable data or the load failure in its chain and cache
    * nothing; once L is readable the same sandbox gets 6..10. Prints what happened before
    * asserting.
    */
   private void assertUnavailableNotCached(String label, ViewsheetSandbox vbox, LBox box)
      throws Exception
   {
      Object data1 = null;
      Throwable ex1 = null;

      try {
         data1 = vbox.getData("TableV");
      }
      catch(Throwable ex) {
         ex1 = ex;
      }

      Object cached = dmap(vbox).get("TableV", DataMap.NORMAL);
      boolean loadFailed = box.loadFailed;
      box.table.lost = false;
      box.stall = false;
      box.loadFailed = false;
      Object data2 = null;
      Throwable ex2 = null;

      try {
         data2 = vbox.getData("TableV");
      }
      catch(Throwable ex) {
         ex2 = ex;
      }

      System.out.println("B78450 [" + label + "] read1=" +
         (ex1 != null ? describe(ex1) : "rows " + safeIds(data1)) +
         " | dmap=" + cached +
         " | read2=" + (ex2 != null ? describe(ex2) : "rows " + safeIds(data2)));

      assertNotNull(ex1, label + ": read 1 did not fail, rows " + safeIds(data1));
      assertNotNull(loadFailed ? TableLoadException.find(ex1) : DataUnavailable.find(ex1),
                    label + ": read 1 failure lost the cause: " + describe(ex1));
      assertNull(cached, label + ": read 1 cached " + cached);
      assertEquals(List.of(6, 7, 8, 9, 10), safeIds(data2), label + ": read 2");
   }

   private static String describe(Throwable ex) {
      StringBuilder sb = new StringBuilder();

      for(Throwable t = ex; t != null; t = t.getCause()) {
         if(t != ex) {
            sb.append(" <- ");
         }

         String msg = String.valueOf(t.getMessage());
         sb.append(t.getClass().getSimpleName()).append('(')
            .append(msg.length() > 60 ? msg.substring(0, 60) + "..." : msg).append(')');

         if(t.getCause() == t) {
            break;
         }
      }

      return sb.toString().replace('\n', ' ');
   }

   private static final String SCRIPT = "L.length > 0 ? 5 : 0";

   private static ConditionList conditions(DataRef ref, String exp) {
      ExpressionValue eval = new ExpressionValue();
      eval.setType(ExpressionValue.JAVASCRIPT);
      eval.setExpression(exp + " /*b78450_" + NONCE.incrementAndGet() + "*/");
      AssetCondition cond = new AssetCondition();
      cond.setOperation(XCondition.GREATER_THAN);
      cond.setType(XSchema.INTEGER);
      cond.addValue(eval);
      ConditionList list = new ConditionList();
      list.append(new ConditionItem(ref, cond, 0));
      return list;
   }

   private static void addL(Worksheet ws) {
      EmbeddedTableAssembly l = new EmbeddedTableAssembly(ws, "L");
      l.setEmbeddedData(new XEmbeddedTable(new String[] { XSchema.INTEGER },
                                           new Object[][] { { "x" }, { 1 }, { 2 } }));
      ws.addAssembly(l);
   }

   private static Worksheet embedded(boolean post) {
      Worksheet ws = new Worksheet();
      Object[][] data = new Object[11][];
      data[0] = new Object[] { "id", "v" };

      for(int i = 1; i <= 10; i++) {
         data[i] = new Object[] { i, i };
      }

      EmbeddedTableAssembly s = new EmbeddedTableAssembly(ws, "S");
      s.setEmbeddedData(new XEmbeddedTable(new String[] { XSchema.INTEGER, XSchema.INTEGER }, data));
      ws.addAssembly(s);
      addL(ws);
      ColumnSelection columns = s.getColumnSelection(false);

      if(post) {
         AggregateInfo info = new AggregateInfo();
         info.addGroup(new GroupRef(columns.getAttribute("id")));
         AggregateRef agg = new AggregateRef(columns.getAttribute("v"), AggregateFormula.SUM);
         info.addAggregate(agg);
         s.setAggregateInfo(info);
         s.setPostConditionList(conditions(agg, SCRIPT));
      }
      else {
         s.setPreConditionList(conditions(columns.getAttribute("id"), SCRIPT));
      }

      return ws;
   }

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
      ds.setName("bug78450");
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
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET, "test/Bug78450",
         null, OrganizationManager.getInstance().getCurrentOrgID());
      ViewsheetSandbox vbox = new ViewsheetSandbox(vs, AbstractSheet.SHEET_RUNTIME_MODE, null,
                                                   false, entry);
      Field wbox = ViewsheetSandbox.class.getDeclaredField("wbox");
      wbox.setAccessible(true);
      wbox.set(vbox, box);
      return vbox;
   }

   /** A sandbox whose table L is the lost-swap table, or whose fetch of L stalls or fails. */
   class LBox extends AssetQuerySandbox {
      LBox(Worksheet ws) {
         super(ws);
         table = new LostSwapFile.Table(new Object[][] { { "x" }, { 1 }, { 2 } }, 1, true, lost);
      }

      @Override
      public TableLens getTableLens(String name, int mode, VariableTable vars) throws Exception {
         if("L".equals(name)) {
            if(stall) {
               throw new LockStallException("b78450-site", "t", 1000, null);
            }

            if(loadFailed) {
               throw new TableLoadException("b78450 load failed", null);
            }

            return table;
         }

         return super.getTableLens(name, mode, vars);
      }

      final LostSwapFile.Table table;
      volatile boolean stall;
      volatile boolean loadFailed;
   }

   private LBox box(Worksheet ws) {
      LBox box = new LBox(ws);
      boxes.add(box);
      return box;
   }

   private static DataMap dmap(ViewsheetSandbox box) throws Exception {
      Field f = ViewsheetSandbox.class.getDeclaredField("dmap");
      f.setAccessible(true);
      return (DataMap) f.get(box);
   }

   private static List<Object> safeIds(Object data) {
      if(!(data instanceof TableLens)) {
         return data == null ? null : List.of(String.valueOf(data));
      }

      return ids(data);
   }

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
   private final List<AssetQuerySandbox> boxes = new ArrayList<>();
   private LostSwapFile lost;
}
