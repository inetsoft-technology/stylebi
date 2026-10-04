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
import inetsoft.report.lens.xnode.XNodeTableLens;
import inetsoft.sree.SreeEnv;
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
import java.lang.reflect.*;
import java.security.Principal;
import java.sql.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #77715. A mirror that renames a column to an alias the helper rejects outputs it as
 * ALIAS_0. A mirror over a plain mirror over it used to drop the column silently, because
 * MirrorQuery cached a sub alias miss computed while its query had no from clause yet, and
 * the cached miss hid the alias of the child mirror. The tables are read through
 * AssetQuerySandbox.getTableLens, which is what the worksheet shows.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  MirrorQuerySubAliasCacheTest.JdbcConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class MirrorQuerySubAliasCacheTest {
   private static final String DB = "memory:bug77715mirror";
   private static final String LONG = "a_very_long_column_alias_name_over_28";
   private static final String LONG2 = "a_very_long_column_alias_name_renamed";
   private static final String L3 = "another_very_long_column_alias_name_x";
   private static final String EXPR = "third_very_long_expression_alias_name";
   private static final int WIDE = 400;

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

      // runs the merged query on derby, what XSessionManager does without its cache
      @Bean
      public XSessionManager xSessionManager(XRepository repository,
                                             XSessionService sessionService)
         throws Exception
      {
         return new XSessionManager(repository, sessionService, mock(DataSourceRegistry.class)) {
            @Override
            public TableLens getXNodeTableLens(XQuery query, VariableTable qvars, Principal user,
                                               XQueryRepository rep, Hashtable queries, long ts)
               throws Exception
            {
               JDBCHandler handler = new JDBCHandler();
               handler.connect(((JDBCQuery) query).getDataSource(), qvars);
               XNode node = handler.execute((JDBCQuery) query.clone(), qvars, user, null);
               XNodeTableLens lens = new XNodeTableLens(node);
               lens.moreRows(Integer.MAX_VALUE);
               return lens;
            }
         };
      }
   }

   @BeforeAll
   static void createTables() throws Exception {
      try(Connection conn = derby().getConnection(); Statement stmt = conn.createStatement()) {
         for(String table : new String[] { "a", "w" }) {
            try {
               stmt.executeUpdate("drop table " + table);
            }
            catch(Exception ignore) {
               // first run
            }
         }

         stmt.executeUpdate("create table a (k int, name varchar(20), x int)");
         stmt.executeUpdate("insert into a values (1, 'x', 5), (2, 'y', 6), (2, 'z', 7)");

         StringBuilder create = new StringBuilder("create table w (");
         StringBuilder insert = new StringBuilder("insert into w values (");

         for(int i = 0; i < WIDE; i++) {
            create.append(i == 0 ? "" : ", ").append("c").append(i).append(" int");
            insert.append(i == 0 ? "" : ", ").append(i);
         }

         stmt.executeUpdate(create.append(")").toString());
         stmt.executeUpdate(insert.append(")").toString());
      }
   }

   @BeforeEach
   void limitAlias() {
      // Derby then rejects aliases over 28 characters, as Oracle and PostgreSQL always do
      SreeEnv.setProperty("limit.alias.length", "true");
   }

   @AfterEach
   void resetLimitAlias() {
      SreeEnv.setProperty("limit.alias.length", null);
   }

   // T0 -> T1 (renames k to LONG, output as ALIAS_0) -> M1 -> M2 -> M3
   @Test
   void mirrorOfPlainMirrorKeepsRenamedColumn() throws Exception {
      Worksheet ws = new Worksheet();
      MirrorTableAssembly t1 = mirror(ws, "T1", table(ws, "T0", "select a.k, a.name from a",
                                                      "k", "name"));
      rename(t1, "k", LONG);
      mirror(ws, "M3", mirror(ws, "M2", mirror(ws, "M1", t1)));
      List<String> expected = List.of("[" + LONG + ", name]", "[1, x]", "[2, y]", "[2, z]");

      assertEquals(expected, lens(ws, "M1"));
      assertEquals(expected, lens(ws, "M2"));
      assertEquals(expected, lens(ws, "M3"));
   }

   // plain mirrors over a grouped mirror G1 of T1, one and two levels
   @Test
   void mirrorOfGroupedMirrorKeepsRenamedColumn() throws Exception {
      Worksheet ws = new Worksheet();
      MirrorTableAssembly t1 = mirror(ws, "T1", table(ws, "T0", "select a.k, a.name from a",
                                                      "k", "name"));
      rename(t1, "k", LONG);
      MirrorTableAssembly g1 = mirror(ws, "G1", t1);
      group(ws, g1, LONG, "name");
      mirror(ws, "G1MM", mirror(ws, "G1M", g1));
      List<String> g1Lens = lens(ws, "G1");

      assertEquals(List.of("[1, 1]", "[2, 2]"), g1Lens.subList(1, g1Lens.size()),
                   "G1 is not grouped: " + g1Lens);
      assertTrue(g1Lens.get(0).startsWith("[" + LONG + ", "), g1Lens.get(0));
      assertEquals(g1Lens, lens(ws, "G1M"));
      assertEquals(g1Lens, lens(ws, "G1MM"));
   }

   // a grouped mirror G2 over a plain mirror M1 of T1, and a plain mirror over G2
   @Test
   void groupedMirrorOfPlainMirrorKeepsRenamedColumn() throws Exception {
      Worksheet ws = new Worksheet();
      MirrorTableAssembly t1 = mirror(ws, "T1", table(ws, "T0", "select a.k, a.name from a",
                                                      "k", "name"));
      rename(t1, "k", LONG);
      MirrorTableAssembly g2 = mirror(ws, "G2", mirror(ws, "M1", t1));
      group(ws, g2, LONG, "name");
      mirror(ws, "G2M", g2);
      List<String> g2Lens = lens(ws, "G2");

      assertEquals(List.of("[1, 1]", "[2, 2]"), g2Lens.subList(1, g2Lens.size()),
                   "G2 is not grouped: " + g2Lens);
      assertTrue(g2Lens.get(0).startsWith("[" + LONG + ", "), g2Lens.get(0));
      assertEquals(g2Lens, lens(ws, "G2M"));
   }

   // 15X: M1 also renames x to the rejected L3 and sorts on it
   @Test
   void mirrorOfSortedRenamingMirrorKeepsBothRenamedColumns() throws Exception {
      Worksheet ws = new Worksheet();
      MirrorTableAssembly t1 = mirror(ws, "T1", table(
         ws, "T0", "select a.k, a.name, a.x from a", "k", "name", "x"));
      rename(t1, "k", LONG);
      MirrorTableAssembly m1 = mirror(ws, "M1", t1);
      rename(m1, "x", L3);
      SortInfo sort = new SortInfo();
      SortRef sref = new SortRef(column(m1, L3));
      sref.setOrder(XConstants.SORT_DESC);
      sort.addSort(sref);
      m1.setSortInfo(sort);
      mirror(ws, "M3", mirror(ws, "M2", m1));
      List<String> expected =
         List.of("[" + LONG + ", name, " + L3 + "]", "[2, z, 7]", "[2, y, 6]", "[1, x, 5]");

      assertEquals(expected, lens(ws, "M1", false));
      assertEquals(expected, lens(ws, "M2", false));
      assertEquals(expected, lens(ws, "M3", false));
   }

   // X2: M1's own sql expression column has a rejected alias, over the aliasing T1
   @Test
   void mirrorExpressionColumnWithRejectedAlias() throws Exception {
      Worksheet ws = new Worksheet();
      MirrorTableAssembly t1 = mirror(ws, "T1", table(ws, "T0", "select a.k, a.name from a",
                                                      "k", "name"));
      rename(t1, "k", LONG);
      MirrorTableAssembly m1 = mirror(ws, "M1", t1);
      ExpressionRef exp = new ExpressionRef(null, EXPR);
      exp.setExpression("field['" + LONG + "'] + 10");
      ColumnRef column = new ColumnRef(exp);
      column.setDataType(XSchema.INTEGER);
      column.setSQL(true);
      m1.getColumnSelection(false).addAttribute(column);
      new AssetQuerySandbox(ws).refreshColumnSelection("M1", true);
      mirror(ws, "M3", mirror(ws, "M2", m1));
      List<String> expected = List.of("[" + LONG + ", name, " + EXPR + "]", "[1, x, 11]",
                                      "[2, y, 12]", "[2, z, 12]");

      assertEquals(expected, lens(ws, "M1"));
      assertEquals(expected, lens(ws, "M2"));
      assertEquals(expected, lens(ws, "M3"));
   }

   // 45140: the sql of T1 names the column LONG, which the merged sql outputs as ALIAS_0
   @Test
   void mirrorsOfSqlAliasedColumn() throws Exception {
      Worksheet ws = new Worksheet();
      SQLBoundTableAssembly t1 = table(ws, "T1", "select a.k " + LONG + " from a", LONG);
      MirrorTableAssembly m1 = mirror(ws, "M1", t1);
      MirrorTableAssembly m2 = mirror(ws, "M2", m1);
      SortInfo sort = new SortInfo();
      SortRef sref = new SortRef(column(m2, LONG));
      sref.setOrder(XConstants.SORT_DESC);
      sort.addSort(sref);
      m2.setSortInfo(sort);
      mirror(ws, "M3", mirror(ws, "M2P", m1));

      assertEquals(List.of("[" + LONG + "]", "[1]", "[2]", "[2]"), lens(ws, "M1"));
      assertEquals(List.of("[" + LONG + "]", "[2]", "[2]", "[1]"), lens(ws, "M2", false));
      // 45764, the header maps back to the original name through every level
      assertEquals(List.of("[" + LONG + "]", "[1]", "[2]", "[2]"), lens(ws, "M3"));
   }

   // D2: M1 renames the sql aliased column to another rejected alias
   @Test
   void mirrorRenamesSqlAliasedColumnToRejectedAlias() throws Exception {
      Worksheet ws = new Worksheet();
      SQLBoundTableAssembly t1 = table(ws, "T1", "select a.k " + LONG + " from a", LONG);
      MirrorTableAssembly m1 = mirror(ws, "M1", t1);
      rename(m1, LONG, LONG2);
      mirror(ws, "M2", m1);
      List<String> expected = List.of("[" + LONG2 + "]", "[1]", "[2]", "[2]");

      assertEquals(expected, lens(ws, "M1"));
      assertEquals(expected, lens(ws, "M2"));
   }

   // 400 columns, 5 of them renamed to rejected aliases, three mirror levels
   @Test
   void wideMirrorChainKeepsAllColumns() throws Exception {
      Worksheet ws = new Worksheet();
      String[] names = new String[WIDE];
      StringBuilder sql = new StringBuilder("select ");

      for(int i = 0; i < WIDE; i++) {
         names[i] = "c" + i;
         sql.append(i == 0 ? "" : ", ").append("w.c").append(i);
      }

      MirrorTableAssembly t1 = mirror(ws, "T1", table(ws, "T0", sql + " from w", names));

      for(int i = 0; i < 5; i++) {
         rename(t1, "c" + i, "a_very_long_column_alias_name_number_" + i);
      }

      MirrorTableAssembly m3 = mirror(ws, "M3", mirror(ws, "M2", mirror(ws, "M1", t1)));

      for(int run = 0; run < 3; run++) {
         long start = System.currentTimeMillis();
         AssetQuery query = AssetQuery.createAssetQuery(
            m3, AssetQuerySandbox.RUNTIME_MODE, new AssetQuerySandbox(ws), false, -1L, true,
            false);
         query.merge(new VariableTable());
         System.out.println("Bug #77715 wide chain merge: " +
                               (System.currentTimeMillis() - start) + " ms");
      }

      TableLens lens = new AssetQuerySandbox(ws).getTableLens("M3",
                                                              AssetQuerySandbox.RUNTIME_MODE);
      lens.moreRows(Integer.MAX_VALUE);
      assertEquals(WIDE, lens.getColCount());

      for(int i = 0; i < WIDE; i++) {
         assertEquals(i, ((Number) lens.getObject(1, i)).intValue(), "column " + i);
      }
   }

   private static SQLBoundTableAssembly table(Worksheet ws, String name, String sql,
                                              String... columns)
      throws Exception
   {
      UniformSQL usql = new UniformSQL();
      Method parse = UniformSQL.class.getDeclaredMethod("parse", String.class, int.class,
                                                        long.class);
      parse.setAccessible(true);
      // parse(String, int, long) is package private, PARSE_ALL is 0
      parse.invoke(usql, sql, 0, 4000L);
      usql.setSQLString(sql, false);
      assertEquals(UniformSQL.PARSE_SUCCESS, usql.getParseResult(), sql);

      JDBCDataSource ds = dataSource();
      usql.setDataSource(ds);
      JDBCQuery query = new JDBCQuery();
      query.setName("bug77715");
      query.setUserQuery(true);
      query.setDataSource(ds);
      query.setSQLDefinition(usql);

      SQLBoundTableAssembly table = new SQLBoundTableAssembly(ws, name);
      SQLBoundTableAssemblyInfo info = (SQLBoundTableAssemblyInfo) table.getTableInfo();
      info.setQuery(query);
      info.setSourceInfo(new SourceInfo(SourceInfo.DATASOURCE, ds.getName(), ds.getName()));
      table.setSQLEdited(true);

      ColumnSelection selection = new ColumnSelection();

      for(String column : columns) {
         ColumnRef ref = new ColumnRef(new AttributeRef(null, column));
         ref.setDataType("name".equals(column) ? XSchema.STRING : XSchema.INTEGER);
         selection.addAttribute(ref);
      }

      table.setColumnSelection(selection, false);
      table.setColumnSelection(selection.clone(), true);
      ws.addAssembly(table);
      return table;
   }

   private static MirrorTableAssembly mirror(Worksheet ws, String name, TableAssembly base) {
      MirrorTableAssembly mirror = new MirrorTableAssembly(ws, name, base);
      ws.addAssembly(mirror);
      return mirror;
   }

   // what renaming a column in the worksheet does to both column selections
   private static void rename(TableAssembly table, String attr, String alias) {
      for(boolean pub : new boolean[] { false, true }) {
         ColumnSelection columns = table.getColumnSelection(pub);
         boolean found = false;

         for(int i = 0; i < columns.getAttributeCount(); i++) {
            ColumnRef column = (ColumnRef) columns.getAttribute(i);

            if(attr.equals(column.getAttribute())) {
               column.setAlias(alias);
               found = true;
            }
         }

         assertTrue(found, attr + " not in " + table.getName());
      }
   }

   private static ColumnRef column(TableAssembly table, String name) {
      ColumnRef column = (ColumnRef) table.getColumnSelection(false).getAttribute(name);
      assertNotNull(column, name + " not in " + table.getName());
      return column;
   }

   // what the aggregate dialog does: group by one column, count another
   private static void group(Worksheet ws, TableAssembly table, String group, String count)
      throws Exception
   {
      AggregateInfo info = new AggregateInfo();
      info.addGroup(new GroupRef(column(table, group)));
      info.addAggregate(new AggregateRef(column(table, count), AggregateFormula.COUNT_ALL));
      table.setAggregateInfo(info);
      table.setAggregate(true);
      new AssetQuerySandbox(ws).refreshColumnSelection(table.getName(), true);
   }

   private static List<String> lens(Worksheet ws, String name) throws Exception {
      return lens(ws, name, true);
   }

   // the header row, then the data rows (sorted unless the order is asserted)
   private static List<String> lens(Worksheet ws, String name, boolean sortRows)
      throws Exception
   {
      TableLens lens = new AssetQuerySandbox(ws).getTableLens(name, AssetQuerySandbox.RUNTIME_MODE);
      lens.moreRows(Integer.MAX_VALUE);
      List<String> rows = new ArrayList<>();

      for(int r = 0; r < lens.getRowCount(); r++) {
         List<String> row = new ArrayList<>();

         for(int c = 0; c < lens.getColCount(); c++) {
            Object value = lens.getObject(r, c);
            row.add(value instanceof Number ? Integer.toString(((Number) value).intValue()) :
                       String.valueOf(value));
         }

         rows.add(row.toString());
      }

      if(sortRows && rows.size() > 1) {
         Collections.sort(rows.subList(1, rows.size()));
      }

      return rows;
   }

   private static JDBCDataSource dataSource() {
      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("bug77715mirror");
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
