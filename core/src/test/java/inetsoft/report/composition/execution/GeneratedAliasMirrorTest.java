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
import inetsoft.uql.erm.DataRef;
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
import java.sql.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Generated ALIAS_N names of a SQL bound worksheet table and the mirrors over it. An alias
 * longer than 28 characters is rejected with limit.alias.length=true (as on Oracle), so the
 * sql outputs the column as ALIAS_N, and every level above has to refer to it by the name
 * that level outputs. Each case runs the real worksheet path: AssetQuery.getTableLens, which
 * merges the mirrors into one sql statement, runs it on Derby through XSessionManager and
 * JDBCHandler, and names the columns of the result.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  GeneratedAliasMirrorTest.JdbcConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class GeneratedAliasMirrorTest {
   private static final String DB = "memory:generatedaliasmirror";
   private static final String LONG = "very_long_column_alias_name_over_28";
   private static final String LONG2 = "very_long_column_alias_name_renamed";
   private static final String L1 = "first_very_long_column_alias_name_1";
   private static final String L2 = "second_very_long_column_alias_name2";
   private static final String L3 = "third_very_long_column_alias_name_3";
   private static final int WIDE = 300;

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

   @BeforeAll
   static void createTables() throws Exception {
      StringBuilder wide = new StringBuilder("create table w (");
      StringBuilder wideRow = new StringBuilder("insert into w values (");

      for(int i = 0; i < WIDE; i++) {
         wide.append(i > 0 ? ", " : "").append("c").append(i).append(" int");
         wideRow.append(i > 0 ? ", " : "").append(i);
      }

      String[] tables = { "a", "b", "c", "ka", "t", "u", "tb", "w" };
      String[] ddl = {
         "create table a (x int)", "insert into a values (1)",
         "create table b (y int)", "insert into b values (2)",
         "create table c (" + L2 + " int)", "insert into c values (3)",
         "create table ka (k int)", "insert into ka values (1), (2), (3)",
         "create table t (ALIAS_0 int, x int)", "insert into t values (100, 1)",
         "create table u (x int)", "insert into u values (7)",
         "create table tb (ALIAS_0 int, b int)", "insert into tb values (1, 3)",
         wide + ")", wideRow + ")" };

      try(Connection conn = derby().getConnection(); Statement stmt = conn.createStatement()) {
         for(String table : tables) {
            try {
               stmt.executeUpdate("drop table " + table);
            }
            catch(Exception ignore) {
               // first run
            }
         }

         for(String sql : ddl) {
            stmt.executeUpdate(sql);
         }
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

   // ---- Bug #77713: a generated name keyed by a bare column name ----

   /**
    * Two derived tables both output ALIAS_0, so the second column gets a new name. It was
    * keyed by its bare column name, and c.L2 (another table's column of that name, renamed
    * to the rejected L3) found it through the table-stripping lookup, so T1 output ALIAS_1
    * twice and a mirror over it failed.
    */
   @Test
   void twoDerivedTablesAndSameNamedColumnOfAnotherTable() throws Exception {
      String sql = "select s1." + L1 + ", s2." + L2 + ", c." + L2 + " as " + L3 +
         " from (select a.x as " + L1 + " from a) s1, (select b.y as " + L2 + " from b) s2, c";
      Worksheet ws = new Worksheet();
      SQLBoundTableAssembly t1 = sqlTable(ws, "T1", sql);

      Result base = run(ws, t1);
      assertEquals(List.of(List.of(1, 2, 3)), base.rows, base.toString());
      assertOutputsDistinct(base);

      MirrorTableAssembly m1 = mirror(ws, "M1", t1);
      Result mirror = run(ws, m1);
      assertEquals(List.of(L1, L2, L3), mirror.header, mirror.toString());
      assertEquals(List.of(List.of(1, 2, 3)), mirror.rows, mirror.toString());

      MirrorTableAssembly g1 = mirror(ws, "G1", t1);
      group(g1, L3, L1);
      Result grouped = run(ws, g1);
      assertEquals(List.of(List.of(1, 3)), grouped.rows, grouped.toString());
   }

   /**
    * c.L2 as L2 gets ALIAS_0 keyed by its alias L2, and c2.L2 as L3 found the same key
    * through the table-stripping lookup, so both were output as ALIAS_0.
    */
   @Test
   void aliasEqualToAnotherColumnsName() throws Exception {
      String sql = "select c." + L2 + " as " + L2 + ", c2." + L2 + " as " + L3 + " from c, c c2";
      Worksheet ws = new Worksheet();
      SQLBoundTableAssembly t1 = sqlTable(ws, "T1", sql);

      Result base = run(ws, t1);
      assertOutputsDistinct(base);

      Result mirror = run(ws, mirror(ws, "M1", t1));
      assertEquals(List.of(L2, L3), mirror.header, mirror.toString());
      assertEquals(List.of(List.of(3, 3)), mirror.rows, mirror.toString());

      MirrorTableAssembly g1 = mirror(ws, "G1", t1);
      group(g1, L3, L2);
      Result grouped = run(ws, g1);
      assertEquals(List.of(List.of(3, 3)), grouped.rows, grouped.toString());
   }

   /**
    * Controls for #77713, correct before and after: only one derived table outputs ALIAS_0,
    * and the first column of the plain table has no alias.
    */
   @Test
   void controlsWithoutTheCollision() throws Exception {
      Worksheet ws = new Worksheet();
      SQLBoundTableAssembly t1 = sqlTable(ws, "T1", "select s1.x1, s2." + L2 + ", c." + L2 +
         " as " + L3 + " from (select a.x as x1 from a) s1, (select b.y as " + L2 +
         " from b) s2, c");
      Result mirror = run(ws, mirror(ws, "M1", t1));
      assertEquals(List.of(List.of(1, 2, 3)), mirror.rows, mirror.toString());

      SQLBoundTableAssembly t2 = sqlTable(ws, "T2", "select c." + L2 + ", c2." + L2 + " as " +
         L3 + " from c, c c2");
      Result mirror2 = run(ws, mirror(ws, "M2", t2));
      assertEquals(List.of(List.of(3, 3)), mirror2.rows, mirror2.toString());
   }

   /**
    * Two columns with the same path, one renamed to another rejected alias. Each column must
    * get its own name although a lookup by path finds the same mapping for both.
    */
   @Test
   void twoColumnsWithTheSamePath() throws Exception {
      Worksheet ws = new Worksheet();
      SQLBoundTableAssembly t1 = sqlTable(ws, "T1", "select s." + LONG + ", s." + LONG +
         " as " + LONG2 + " from (select ka.k as " + LONG + " from ka) s");

      Result base = run(ws, t1);
      assertOutputsDistinct(base);

      Result mirror = run(ws, mirror(ws, "M1", t1));
      assertEquals(List.of(LONG, LONG2), mirror.header, mirror.toString());
      assertEquals(List.of(List.of(1, 1), List.of(2, 2), List.of(3, 3)), mirror.rows,
                   mirror.toString());
   }

   // ---- Bug #77714: a valid own alias mapped to the sub-level ALIAS_N ----

   /**
    * M1 renames the ALIAS_0 column of T1 to c, so M1 outputs it as c. M1's selection still
    * mapped c to ALIAS_0, and M2 over M1 selected M1.ALIAS_0, a column M1 doesn't output.
    */
   @Test
   void mirrorOfMirrorThatRenamesAGeneratedColumn() throws Exception {
      Worksheet ws = new Worksheet();
      SQLBoundTableAssembly t1 = sqlTable(ws, "T1", "select ka.k " + LONG + " from ka");
      MirrorTableAssembly m1 = mirror(ws, "M1", t1);
      rename(m1, LONG, "c");

      Result r1 = run(ws, m1);
      assertEquals(List.of("c"), r1.header, r1.toString());

      Result r2 = run(ws, mirror(ws, "M2", m1));
      assertEquals(List.of(List.of(1), List.of(2), List.of(3)), r2.rows, r2.toString());
      assertTrue(r2.sql.contains("M1.c"), r2.sql);

      // sorted on the renamed column
      MirrorTableAssembly sorted = mirror(ws, "M2S", m1);
      sort(sorted, "c");
      Result r2s = run(ws, sorted);
      assertEquals(List.of(List.of(3), List.of(2), List.of(1)), r2s.rows, r2s.toString());

      // renamed again, and a mirror above that
      MirrorTableAssembly renamed = mirror(ws, "M2R", m1);
      rename(renamed, "c", "d");
      Result r2r = run(ws, renamed);
      assertEquals(List.of("d"), r2r.header, r2r.toString());
      assertEquals(List.of(List.of(1), List.of(2), List.of(3)), r2r.rows, r2r.toString());

      Result r3 = run(ws, mirror(ws, "M3", renamed));
      assertEquals(List.of(List.of(1), List.of(2), List.of(3)), r3.rows, r3.toString());
   }

   /**
    * Controls for 45140 and 45764, correct before and after. A mirror refers to the column by
    * the ALIAS_N its base outputs, and the header maps back to the original name through
    * every level.
    */
   @Test
   void generatedNamesThroughAMirrorChain() throws Exception {
      Worksheet ws = new Worksheet();

      // D: no rename, M1 and M2 over T1 (45140), plain and sorted
      SQLBoundTableAssembly t1 = sqlTable(ws, "T1", "select ka.k " + LONG + " from ka");
      MirrorTableAssembly m1 = mirror(ws, "M1", t1);
      Result d2 = run(ws, mirror(ws, "M2", m1));
      assertTrue(d2.sql.contains("M1.ALIAS_0"), d2.sql);
      assertEquals(List.of(List.of(1), List.of(2), List.of(3)), d2.rows, d2.toString());
      assertTrue(d2.header.get(0).endsWith(LONG), d2.toString());

      MirrorTableAssembly d2s = mirror(ws, "M2S", m1);
      sort(d2s, LONG);
      Result sorted = run(ws, d2s);
      assertEquals(List.of(List.of(3), List.of(2), List.of(1)), sorted.rows, sorted.toString());

      // D2: M1 renames to another rejected alias
      MirrorTableAssembly r1 = mirror(ws, "R1", t1);
      rename(r1, LONG, LONG2);
      Result rr2 = run(ws, mirror(ws, "R2", r1));
      assertEquals(List.of(List.of(1), List.of(2), List.of(3)), rr2.rows, rr2.toString());
      assertTrue(rr2.header.get(0).endsWith(LONG2), rr2.toString());

      // N: three levels over a long alias (45764)
      SQLBoundTableAssembly n1 = sqlTable(ws, "N1", "select u.x " + LONG + " from u");
      MirrorTableAssembly n2 = mirror(ws, "N2", n1);
      MirrorTableAssembly n3 = mirror(ws, "N3", n2);
      Result n4 = run(ws, mirror(ws, "N4", n3));
      assertEquals(List.of(List.of(7)), n4.rows, n4.toString());
      assertTrue(n4.header.get(0).endsWith(LONG), n4.toString());
   }

   /**
    * Controls with a nested base, correct before and after: T1 renames the derived table's
    * ALIAS_0 column to c, and a mirror renames c to d.
    */
   @Test
   void nestedBaseControls() throws Exception {
      Worksheet ws = new Worksheet();
      SQLBoundTableAssembly t1 = sqlTable(ws, "T1", "select s." + LONG + " c from (select ka.k " +
         LONG + " from ka) s");
      MirrorTableAssembly m1 = mirror(ws, "M1", t1);
      Result b = run(ws, mirror(ws, "M2", m1));
      assertEquals(List.of("c"), b.header, b.toString());
      assertEquals(List.of(List.of(1), List.of(2), List.of(3)), b.rows, b.toString());

      MirrorTableAssembly e1 = mirror(ws, "E1", t1);
      rename(e1, "c", "d");
      Result e2 = run(ws, mirror(ws, "E2", e1));
      assertEquals(List.of("d"), e2.header, e2.toString());
      assertEquals(List.of(List.of(1), List.of(2), List.of(3)), e2.rows, e2.toString());
   }

   // ---- Bug #77716: a generated name equal to another column's output name ----

   /**
    * The column tb.ALIAS_0 outputs ALIAS_0, and the rejected alias of the next column was
    * replaced by ALIAS_0 too. T1 named both columns after the long alias, and a mirror over
    * it failed on the ambiguous ALIAS_0.
    */
   @Test
   void generatedNameEqualToAColumnName() throws Exception {
      Worksheet ws = new Worksheet();
      SQLBoundTableAssembly t1 = sqlTable(ws, "T1", "select tb.ALIAS_0, tb.b as " + LONG +
         " from tb");

      Result base = run(ws, t1);
      assertOutputsDistinct(base);
      assertEquals(List.of("ALIAS_0", LONG), base.header, base.toString());
      assertEquals(List.of(List.of(1, 3)), base.rows, base.toString());

      Result mirror = run(ws, mirror(ws, "M1", t1));
      assertEquals(List.of(List.of(1, 3)), mirror.rows, mirror.toString());
   }

   // ---- Bug #77717: a real ALIAS_0 column next to a generated or inherited ALIAS_0 ----

   /**
    * ALIAS_0 is generated only inside the derived table s, and the outer column is renamed
    * Foo. T1 mapped ALIAS_0 to Foo although it outputs t.ALIAS_0 as ALIAS_0, so the real
    * column was named Foo, a mirror of a mirror showed its values under the name Foo, and the
    * mirror of a mirror with every column failed.
    */
   @Test
   void realColumnNextToARenamedInheritedName() throws Exception {
      Worksheet ws = new Worksheet();
      SQLBoundTableAssembly t1 = sqlTable(ws, "T1", "select t.ALIAS_0, s." + LONG +
         " Foo from t, (select u.x " + LONG + " from u) s");

      Result base = run(ws, t1);
      assertEquals(List.of("ALIAS_0", "Foo"), base.header, base.toString());
      assertEquals(List.of(List.of(100, 7)), base.rows, base.toString());

      // a mirror of a mirror with every column
      MirrorTableAssembly m1 = mirror(ws, "M1", t1);
      Result mm1 = run(ws, mirror(ws, "MM1", m1));
      assertEquals(List.of(List.of(100, 7)), mm1.rows, mm1.toString());

      // a mirror of a mirror that shows only the real ALIAS_0 column
      MirrorTableAssembly m7 = mirror(ws, "M7", t1);
      hide(m7, "Foo");
      Result mm7 = run(ws, mirror(ws, "MM7", m7));
      assertEquals(List.of("ALIAS_0"), mm7.header, mm7.toString());
      assertEquals(List.of(List.of(100)), mm7.rows, mm7.toString());
   }

   /**
    * The same with the outer column unaliased: it inherited ALIAS_0 from s although
    * t.ALIAS_0 outputs ALIAS_0 too, so T1 output ALIAS_0 twice and every mirror failed.
    */
   @Test
   void realColumnNextToAnInheritedName() throws Exception {
      Worksheet ws = new Worksheet();
      SQLBoundTableAssembly t1 = sqlTable(ws, "T1", "select t.ALIAS_0, s." + LONG +
         " from t, (select u.x " + LONG + " from u) s");

      Result base = run(ws, t1);
      assertOutputsDistinct(base);
      assertEquals(List.of(List.of(100, 7)), base.rows, base.toString());

      MirrorTableAssembly m1 = mirror(ws, "M1", t1);
      Result r1 = run(ws, m1);
      assertEquals(List.of(List.of(100, 7)), r1.rows, r1.toString());

      Result mm1 = run(ws, mirror(ws, "MM1", m1));
      assertEquals(List.of(List.of(100, 7)), mm1.rows, mm1.toString());

      // the columns swapped
      SQLBoundTableAssembly t2 = sqlTable(ws, "T2", "select s." + LONG + ", t.ALIAS_0 from t, " +
         "(select u.x " + LONG + " from u) s");
      Result r2 = run(ws, mirror(ws, "M2", t2));
      assertEquals(List.of(List.of(7, 100)), r2.rows, r2.toString());
   }

   // ---- 53160: a mirror chain over a wide table ----

   /**
    * A mirror chain over a SQL bound table of several hundred columns, a few of them with
    * rejected aliases. Every column is kept with its value, and the merge stays fast.
    */
   @Test
   void wideMirrorChain() throws Exception {
      StringBuilder sql = new StringBuilder("select ");

      for(int i = 0; i < WIDE; i++) {
         sql.append(i > 0 ? ", " : "").append("w.c").append(i);

         if(i % 60 == 0) {
            sql.append(" as ").append(LONG).append("_").append(i);
         }
      }

      sql.append(" from w");
      Worksheet ws = new Worksheet();
      SQLBoundTableAssembly t1 = sqlTable(ws, "T1", sql.toString());
      MirrorTableAssembly m1 = mirror(ws, "M1", t1);
      MirrorTableAssembly m2 = mirror(ws, "M2", m1);
      MirrorTableAssembly m3 = mirror(ws, "M3", m2);
      long merge = Long.MAX_VALUE;

      for(int i = 0; i < 3; i++) {
         long start = System.nanoTime();
         AssetQuery query = AssetQuery.createAssetQuery(
            m3, AssetQuerySandbox.RUNTIME_MODE, new AssetQuerySandbox(ws), false, -1L, true, false);
         query.merge(new VariableTable());
         merge = Math.min(merge, (System.nanoTime() - start) / 1_000_000);
      }

      System.out.println("GeneratedAliasMirrorTest wide merge of M3 (ms, best of 3): " + merge);
      Result r3 = run(ws, m3);
      List<Integer> expected = new ArrayList<>();

      for(int i = 0; i < WIDE; i++) {
         expected.add(i);
      }

      assertEquals(List.of(expected), r3.rows, r3.sql);
      assertTrue(r3.header.get(60).endsWith(LONG + "_60"), r3.header.toString());
   }

   private static void assertOutputsDistinct(Result result) {
      assertEquals(result.labels.size(), new HashSet<>(result.labels).size(),
                   "duplicate output names: " + result);
   }

   private static SQLBoundTableAssembly sqlTable(Worksheet ws, String name, String text)
      throws Exception
   {
      UniformSQL usql = new UniformSQL();
      Method parse = UniformSQL.class.getDeclaredMethod("parse", String.class, int.class,
                                                        long.class);
      parse.setAccessible(true);
      // parse(String, int, long) is package private, PARSE_ALL is 0
      parse.invoke(usql, text, 0, 4000L);
      usql.setSQLString(text, false);
      assertEquals(UniformSQL.PARSE_SUCCESS, usql.getParseResult(), text);
      assertFalse(usql.isLossy(), text);

      JDBCDataSource ds = dataSource();
      usql.setDataSource(ds);
      JDBCQuery query = new JDBCQuery();
      query.setName(name);
      query.setUserQuery(true);
      query.setDataSource(ds);
      query.setSQLDefinition(usql);

      SQLBoundTableAssembly table = new SQLBoundTableAssembly(ws, name);
      SQLBoundTableAssemblyInfo info = (SQLBoundTableAssemblyInfo) table.getTableInfo();
      info.setQuery(query);
      info.setSourceInfo(new SourceInfo(SourceInfo.DATASOURCE, ds.getName(), ds.getName()));
      table.setSQLEdited(true);
      table.setProperty("no_cache", "true");

      // as QueryManagerService.getColumnSelection names the columns: the alias, else the
      // column name
      JDBCSelection selection = (JDBCSelection) usql.getSelection();
      ColumnSelection columns = new ColumnSelection();

      for(int i = 0; i < selection.getColumnCount(); i++) {
         String path = selection.getColumn(i);
         String alias = selection.getAlias(i);
         String column = alias != null && !alias.isEmpty() ? alias :
            path.substring(path.lastIndexOf('.') + 1);
         ColumnRef ref = new ColumnRef(new AttributeRef(column));
         ref.setDataType(XSchema.INTEGER);
         columns.addAttribute(ref);
      }

      table.setColumnSelection(columns, false);
      table.setColumnSelection((ColumnSelection) columns.clone(), true);
      ws.addAssembly(table);
      return table;
   }

   private static MirrorTableAssembly mirror(Worksheet ws, String name, TableAssembly base) {
      MirrorTableAssembly mirror = new MirrorTableAssembly(ws, name, base);
      // each case runs its own query, not a cached result of another table
      mirror.setProperty("no_cache", "true");
      ws.addAssembly(mirror);
      mirror.update();
      return mirror;
   }

   // a rename in the worksheet sets the alias of the column (RenameColumnController)
   private static void rename(TableAssembly table, String column, String alias) {
      ColumnSelection columns = table.getColumnSelection(false);
      ((ColumnRef) find(columns, column)).setAlias(alias);
      table.setColumnSelection(columns, false);
      table.update();
   }

   private static void hide(TableAssembly table, String column) {
      ColumnSelection columns = table.getColumnSelection(false);
      ((ColumnRef) find(columns, column)).setVisible(false);
      table.setColumnSelection(columns, false);
      table.update();
   }

   private static void sort(TableAssembly table, String column) {
      SortRef sort = new SortRef(find(table.getColumnSelection(false), column));
      sort.setOrder(XConstants.SORT_DESC);
      SortInfo info = new SortInfo();
      info.addSort(sort);
      table.setSortInfo(info);
      table.update();
   }

   private static void group(TableAssembly table, String group, String sum) {
      ColumnSelection columns = table.getColumnSelection(false);
      AggregateInfo info = new AggregateInfo();
      info.addGroup(new GroupRef(find(columns, group)));
      info.addAggregate(new AggregateRef(find(columns, sum), AggregateFormula.SUM));
      table.setAggregateInfo(info);
      table.update();
   }

   private static DataRef find(ColumnSelection columns, String name) {
      for(int i = 0; i < columns.getAttributeCount(); i++) {
         ColumnRef column = (ColumnRef) columns.getAttribute(i);

         if(name.equals(column.getAlias()) || name.equals(column.getAttribute())) {
            return column;
         }
      }

      throw new AssertionError("no column " + name + " in " + columns);
   }

   /**
    * Merge the table and run the merged query as XSessionManager does, then run the table
    * through AssetQuery.getTableLens with a new sandbox, as the worksheet does, for the
    * names of its columns.
    */
   private static Result run(Worksheet ws, TableAssembly table) throws Exception {
      VariableTable vars = new VariableTable();
      AssetQuery merged = AssetQuery.createAssetQuery(
         table, AssetQuerySandbox.RUNTIME_MODE, new AssetQuerySandbox(ws), false, -1L, true, false);
      merged.merge(vars);
      JDBCQuery jquery = merged.getQuery();
      Result result = new Result();
      result.name = table.getName();
      result.sql = jquery.getSQLAsString().replaceAll("\\s+", " ");
      result.labels = labels(jquery.getSQLAsString());

      try {
         JDBCHandler handler = new JDBCHandler();
         handler.connect(jquery.getDataSource(), vars);
         XNodeTableLens rows = new XNodeTableLens(handler.execute(jquery, vars, null, null));
         result.rows = rows(rows);
      }
      catch(Exception ex) {
         result.error = ex.toString();
      }

      AssetQuery query = AssetQuery.createAssetQuery(
         table, AssetQuerySandbox.RUNTIME_MODE, new AssetQuerySandbox(ws), false, -1L, true, false);
      TableLens lens = query.getTableLens(vars);
      assertNotNull(lens, "the query failed, see the log: " + result);
      lens.moreRows(Integer.MAX_VALUE);

      for(int c = 0; c < lens.getColCount(); c++) {
         result.header.add(String.valueOf(lens.getObject(0, c)));
      }

      return result;
   }

   private static List<List<Object>> rows(TableLens lens) {
      List<List<Object>> rows = new ArrayList<>();
      lens.moreRows(Integer.MAX_VALUE);

      for(int r = 1; r < lens.getRowCount(); r++) {
         List<Object> row = new ArrayList<>();

         for(int c = 0; c < lens.getColCount(); c++) {
            Object value = lens.getObject(r, c);
            row.add(value instanceof Number ? ((Number) value).intValue() : value);
         }

         rows.add(row);
      }

      return rows;
   }

   // the output names of the merged sql, as the database labels them
   private static List<String> labels(String sql) {
      List<String> labels = new ArrayList<>();

      try(Connection conn = derby().getConnection(); Statement stmt = conn.createStatement();
          ResultSet rs = stmt.executeQuery(sql))
      {
         ResultSetMetaData meta = rs.getMetaData();

         for(int i = 1; i <= meta.getColumnCount(); i++) {
            labels.add(meta.getColumnLabel(i).toUpperCase());
         }
      }
      catch(SQLException ex) {
         labels.add("error: " + ex.getMessage());
      }

      return labels;
   }

   private static JDBCDataSource dataSource() {
      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("generatedaliasmirror");
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

   private static final class Result {
      String name;
      String sql;
      List<String> labels = new ArrayList<>();
      List<String> header = new ArrayList<>();
      List<List<Object>> rows = new ArrayList<>();
      String error;

      @Override
      public String toString() {
         return name + " sql=" + sql + " labels=" + labels + " header=" + header + " rows=" +
            rows + (error != null ? " error=" + error : "");
      }
   }
}
