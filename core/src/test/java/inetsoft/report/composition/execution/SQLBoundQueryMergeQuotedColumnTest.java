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

import inetsoft.report.TableLens;
import inetsoft.report.XSessionManager;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.storage.BlobStorageManager;
import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.SQLBoundTableAssemblyInfo;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.jdbc.*;
import inetsoft.uql.jdbc.util.JDBCUtil;
import inetsoft.uql.path.XSelection;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.*;
import inetsoft.util.ConfigurationContext;
import inetsoft.util.Plugins;
import inetsoft.util.credential.CredentialService;
import org.apache.derby.jdbc.EmbeddedDataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import javax.sql.DataSource;
import java.lang.reflect.*;
import java.sql.*;
import java.util.*;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #77786. A SQL edited worksheet table that parses is merged (SQLBoundQuery.merge), which
 * rebuilds the select list, and the worksheet's group by, aggregates, sort and conditions,
 * from the bare paths of the parsed select columns. A column written as a quoted identifier
 * ({@code o."Mixed"}, {@code o."Order-Date"}, {@code "Order Date"}) is stored without its
 * quotes, with a per-position quote record, which the merge dropped: the merged query read
 * {@code o.Mixed}, which is the column MIXED on Derby (wrong rows when the table has it, an
 * error when it doesn't), or failed on {@code o.Order-Date}.
 * <p>
 * The merged query is executed by JDBCHandler on Derby, as XSessionManager does, with and
 * without the table metadata resolved (JDBCUtil.fixSelectionInfo sets the select columns'
 * tables, without it the table of a column is guessed from its text).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  SQLBoundQueryMergeQuotedColumnTest.JdbcConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class SQLBoundQueryMergeQuotedColumnTest {
   private static final String DB = "memory:bug77786merge";
   // TW has a case-folded twin MIXED of "Mixed", NT doesn't
   private static final String[] TW_COLUMNS = { "Order-Date", "AMT", "Mixed", "Order Date", "MIXED" };
   private static final String[] NT_COLUMNS = { "Order-Date", "AMT", "Mixed", "Order Date" };

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
         DataSource ds = recording(derby());
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
   }

   @BeforeAll
   static void createTables() throws Exception {
      try(Connection conn = derby().getConnection(); Statement stmt = conn.createStatement()) {
         for(String table : new String[] { "tw", "nt" }) {
            try {
               stmt.executeUpdate("drop table " + table);
            }
            catch(Exception ignore) {
               // first run
            }
         }

         stmt.executeUpdate("create table tw (\"Order-Date\" int, amt int, \"Mixed\" int, " +
                               "\"Order Date\" int, mixed int)");
         stmt.executeUpdate("insert into tw values (1, 10, 100, 11, -5), (2, 20, 200, 22, -6)");
         stmt.executeUpdate("create table nt (\"Order-Date\" int, amt int, \"Mixed\" int, " +
                               "\"Order Date\" int)");
         stmt.executeUpdate("insert into nt values (1, 10, 100, 11), (2, 20, 200, 22)");
      }
   }

   // ---- select list (mergeColumn) ----

   // the reported case on a table with a case-folded twin: o.Mixed is the column MIXED
   @ParameterizedTest(name = "metadata={0}")
   @ValueSource(booleans = { false, true })
   void quotedMixedCaseWithTwin(boolean meta) throws Exception {
      {
         Result r = run("select o.\"Mixed\", o.amt from tw o", "tw", meta, null);
         assertEquals(List.of(List.of(100, 10), List.of(200, 20)),
                      sorted(r, meta, "Mixed", "amt"), r.msg(meta));
      }
   }

   @ParameterizedTest(name = "metadata={0}")
   @ValueSource(booleans = { false, true })
   void quotedMixedCaseWithoutTwin(boolean meta) throws Exception {
      {
         Result r = run("select o.\"Mixed\", o.amt from nt o", "nt", meta, null);
         assertEquals(List.of(List.of(100, 10), List.of(200, 20)),
                      sorted(r, meta, "Mixed", "amt"), r.msg(meta));
      }
   }

   @ParameterizedTest(name = "metadata={0}")
   @ValueSource(booleans = { false, true })
   void quotedHyphenColumn(boolean meta) throws Exception {
      {
         Result r = run("select o.\"Order-Date\", o.amt from nt o", "nt", meta, null);
         assertEquals(List.of(List.of(1, 10), List.of(2, 20)),
                      sorted(r, meta, "Order-Date", "amt"), r.msg(meta));
      }
   }

   @ParameterizedTest(name = "metadata={0}")
   @ValueSource(booleans = { false, true })
   void unqualifiedQuotedSpaceColumn(boolean meta) throws Exception {
      {
         Result r = run("select \"Order Date\", amt from nt", "nt", meta, null);
         assertEquals(List.of(List.of(11, 10), List.of(22, 20)),
                      sorted(r, meta, "Order Date", "amt"), r.msg(meta));
      }
   }

   // the second column is the unquoted mixed (MIXED on Derby). It is found ignoring case by
   // the merge, as o.Mixed, and must not take the quote of the first column. (With the table
   // metadata resolved, both columns are named Mixed and the worksheet keeps one.)
   @Test
   void quotedAndUnquotedTwinInSelect() throws Exception {
      Result r = run("select o.\"Mixed\", o.mixed from tw o", "tw", false, null);
      assertNotNull(r.ordered, r.msg(false));
      List<List<Integer>> rows = new ArrayList<>(r.ordered);
      rows.sort(Comparator.comparing(Object::toString));
      assertEquals(List.of(List.of(100, -5), List.of(200, -6)), rows, r.msg(false));
   }

   // the merge writes into the table's own query, a second run merges the merged query
   @Test
   void secondRunKeepsQuotes() throws Exception {
      Worksheet ws = new Worksheet();
      SQLBoundTableAssembly table = newTable(ws, "select o.\"Mixed\", o.amt from tw o", "tw",
                                             false);

      for(int i = 0; i < 2; i++) {
         VariableTable vars = new VariableTable();
         SQLBoundQuery query = new SQLBoundQuery(
            AssetQuerySandbox.RUNTIME_MODE, new AssetQuerySandbox(ws), table, false, false);
         query.merge(vars);
         executed.set(null);
         List<List<Integer>> rows = execute(query.getQuery(), vars, new ArrayList<>());
         rows.sort(Comparator.comparing(Object::toString));
         assertEquals(List.of(List.of(100, 10), List.of(200, 20)), rows,
                      "run " + i + ": " + executed.get());
      }
   }

   // ---- worksheet sort (mergeOrderBy) ----

   @ParameterizedTest(name = "metadata={0}")
   @ValueSource(booleans = { false, true })
   void worksheetSortOnQuotedColumn(boolean meta) throws Exception {
      {
         Result r = run("select o.\"Mixed\", o.amt from tw o", "tw", meta,
                        t -> sort(t, "Mixed", XConstants.SORT_DESC));
         assertEquals(List.of(List.of(200), List.of(100)), rows(r, meta, "Mixed"), r.msg(meta));

         r = run("select o.\"Order-Date\", o.amt from nt o", "nt", meta,
                 t -> sort(t, "Order-Date", XConstants.SORT_DESC));
         assertEquals(List.of(List.of(2), List.of(1)), rows(r, meta, "Order-Date"),
                      r.msg(meta));
      }
   }

   // ---- worksheet condition (createItem) ----

   @ParameterizedTest(name = "metadata={0}")
   @ValueSource(booleans = { false, true })
   void worksheetConditionOnQuotedColumn(boolean meta) throws Exception {
      {
         Result r = run("select o.\"Mixed\", o.amt from tw o", "tw", meta,
                        t -> condition(t, "Mixed", 150));
         assertEquals(List.of(List.of(200, 20)), sorted(r, meta, "Mixed", "amt"), r.msg(meta));

         r = run("select \"Order Date\", amt from nt", "nt", meta,
                 t -> condition(t, "Order Date", 15));
         assertEquals(List.of(List.of(22, 20)), sorted(r, meta, "Order Date", "amt"),
                      r.msg(meta));

         r = run("select o.\"Order-Date\", o.amt from nt o", "nt", meta,
                 t -> condition(t, "Order-Date", 1));
         assertEquals(List.of(List.of(2, 20)), sorted(r, meta, "Order-Date", "amt"),
                      r.msg(meta));
      }
   }

   // ---- aggregates (mergeAggregates) and group by (mergeGroupBy) ----

   // sum("Mixed") by amt. Merged into the sql with the table metadata resolved, otherwise
   // aggregated after the query, which then selects the column
   @ParameterizedTest(name = "metadata={0}")
   @ValueSource(booleans = { false, true })
   void sumOfQuotedColumn(boolean meta) throws Exception {
      {
         Result r = run("select o.\"Mixed\", o.amt from tw o", "tw", meta,
                        t -> aggregate(t, "amt", "Mixed"));
         assertEquals(List.of(List.of(100, 10), List.of(200, 20)),
                      sorted(r, meta, "Mixed", "amt"), r.msg(meta));
         assertTrue(!meta || r.sql.contains("SUM(o.\"Mixed\")"), r.msg(meta));

         r = run("select o.\"Mixed\", o.amt from tw o", "tw", meta,
                 t -> aggregate(t, null, "Mixed"));
         assertEquals(List.of(List.of(300)), sorted(r, meta, "Mixed"), r.msg(meta));
      }
   }

   // group by "Mixed", sum(amt)
   @ParameterizedTest(name = "metadata={0}")
   @ValueSource(booleans = { false, true })
   void groupByQuotedColumn(boolean meta) throws Exception {
      {
         Result r = run("select o.\"Mixed\", o.amt from tw o", "tw", meta,
                        t -> aggregate(t, "Mixed", "amt"));
         assertEquals(List.of(List.of(100, 10), List.of(200, 20)),
                      sorted(r, meta, "Mixed", "amt"), r.msg(meta));
         assertTrue(!meta || r.sql.contains("group by o.\"Mixed\""), r.msg(meta));
      }
   }

   // ---- no quote record: the merged sql is the same as before the fix ----

   // the sql is the same as before the fix (captured with the PreAssetQuery of main)
   @ParameterizedTest(name = "metadata={0}")
   @ValueSource(booleans = { false, true })
   void unquotedQueryUnchanged(boolean meta) throws Exception {
      String sql = "select o.amt, o.mixed from tw o";
      Result r = run(sql, "tw", meta, t -> sort(t, "mixed", XConstants.SORT_DESC));
      assertEquals(meta ? "select o.AMT, o.Mixed from tw o order by o.Mixed desc" :
                      "select o.amt, o.mixed from tw o order by o.mixed desc", r.sql);
      assertEquals(List.of(List.of(-5), List.of(-6)), rows(r, meta, "mixed"), r.msg(meta));

      r = run(sql, "tw", meta, t -> condition(t, "mixed", -6));
      assertEquals(meta ? "select o.AMT, o.Mixed from tw o where o.Mixed > ?" :
                      "select o.amt, o.mixed from tw o where o.mixed > ?", r.sql);
      assertEquals(List.of(List.of(-5)), rows(r, meta, "mixed"), r.msg(meta));

      r = run(sql, "tw", meta, t -> aggregate(t, "mixed", "amt"));
      assertEquals(meta ? "select SUM(o.AMT) as AMT, o.Mixed from tw o group by o.Mixed" :
                      "select o.amt, o.mixed from tw o order by o.mixed asc", r.sql);

      r = run(sql, "tw", meta, t -> aggregate(t, "amt", "mixed"));
      assertEquals(meta ? "select SUM(o.Mixed) as Mixed, o.AMT from tw o group by o.AMT" :
                      "select o.amt, o.mixed from tw o order by o.amt asc", r.sql);
   }

   // ---- postgresql (case-sensitive helper, quotes in the stored text), sql only ----

   // postgresql quotes every segment and stores some of them with their quotes, the record
   // must not quote a name twice. Only the unqualified "Order Date" changed: it was generated
   // unquoted before the fix. The other expected sql is the same as before the fix.
   @Test
   void postgresqlMergedSql() throws Exception {
      String quoted = "select o.\"Mixed\", o.amt from tw o";
      String plain = "select o.amt, o.mixed from tw o";
      String[][] cases = {
         { quoted, null, null, "select \"o\".\"amt\", \"o\".\"Mixed\" from \"tw\" o" },
         { quoted, "sort", "Mixed",
           "select \"o\".\"amt\", \"o\".\"Mixed\" from \"tw\" o order by \"o\".\"Mixed\" desc" },
         { quoted, "cond", "Mixed",
           "select \"o\".\"amt\", \"o\".\"Mixed\" from \"tw\" o where \"o\".\"Mixed\" > $(X)" },
         { quoted, "sum", "Mixed", "select \"o\".\"amt\", SUM(o.\"Mixed\") as \"Mixed\" " +
           "from \"tw\" o group by \"o\".\"amt\"" },
         { quoted, "group", "Mixed", "select \"o\".\"Mixed\", SUM(o.\"amt\") as \"ALIAS_0\" " +
           "from \"tw\" o group by \"o\".\"Mixed\"" },
         { "select \"Order Date\", amt from tw", null, null,
           "select \"amt\", \"Order Date\" from \"tw\"" },
         { "select o.\"Mixed\", o.mixed from tw o", null, null,
           "select \"o\".\"mixed\", \"o\".\"Mixed\" from \"tw\" o" },
         { plain, "sort", "mixed",
           "select \"o\".\"amt\", \"o\".\"mixed\" from \"tw\" o order by \"o\".\"mixed\" desc" },
         { plain, "cond", "mixed",
           "select \"o\".\"amt\", \"o\".\"mixed\" from \"tw\" o where \"o\".\"mixed\" > $(X)" },
         { plain, "sum", "mixed", "select \"o\".\"amt\", SUM(o.\"mixed\") as \"ALIAS_0\" " +
           "from \"tw\" o group by \"o\".\"amt\"" },
         { plain, "group", "mixed", "select \"o\".\"mixed\", SUM(o.\"amt\") as \"ALIAS_0\" " +
           "from \"tw\" o group by \"o\".\"mixed\"" },
      };

      for(String[] c : cases) {
         assertEquals(c[3], pgSql(c[0], c[1], c[2]), c[0] + " " + c[1]);
      }
   }

   // ---- a worksheet condition and sort on the same quoted column ----
   @ParameterizedTest(name = "metadata={0}")
   @ValueSource(booleans = { false, true })
   void conditionAndSortOnQuotedColumn(boolean meta) throws Exception {
      Result r = run("select o.\"Mixed\", o.amt from tw o", "tw", meta, t -> {
         sort(t, "Mixed", XConstants.SORT_DESC);
         condition(t, "Mixed", 50);
      });
      assertEquals(List.of(List.of(200, 20), List.of(100, 10)), rows(r, meta, "Mixed", "amt"),
                   r.msg(meta));

      r = run("select o.\"Mixed\", o.amt from tw o", "tw", meta, t -> {
         sort(t, "Mixed", XConstants.SORT_ASC);
         condition(t, "Mixed", 150);
      });
      assertEquals(List.of(List.of(200, 20)), rows(r, meta, "Mixed", "amt"), r.msg(meta));
   }

   // ---- group by one quoted column, sum of another quoted column ----
   @ParameterizedTest(name = "metadata={0}")
   @ValueSource(booleans = { false, true })
   void groupByQuotedSumOtherQuoted(boolean meta) throws Exception {
      Result r = run("select o.\"Order-Date\", o.\"Mixed\", o.amt from tw o", "tw", meta,
                     t -> aggregate(t, "Order-Date", "Mixed"));
      assertEquals(List.of(List.of(1, 100), List.of(2, 200)),
                   sorted(r, meta, "Order-Date", "Mixed"), r.msg(meta));

      r = run("select o.\"Mixed\", o.\"Order Date\", o.amt from tw o", "tw", meta,
              t -> aggregate(t, "Mixed", "Order Date"));
      assertEquals(List.of(List.of(100, 11), List.of(200, 22)),
                   sorted(r, meta, "Mixed", "Order Date"), r.msg(meta));
   }

   // ---- distinct, with the other column hidden ----
   @ParameterizedTest(name = "metadata={0}")
   @ValueSource(booleans = { false, true })
   void distinctWithHiddenColumn(boolean meta) throws Exception {
      Result r = run("select o.\"Mixed\", o.amt from tw o", "tw", meta, t -> {
         t.setDistinct(true);
         ((ColumnRef) ref(t, "amt")).setVisible(false);
      });
      assertEquals(List.of(List.of(100), List.of(200)), sorted(r, meta, "Mixed"), r.msg(meta));
   }

   // ---- oracle (upper-folding) and postgresql, a quoted "Mixed" and lower case "mixed",
   // sql only: quoted wherever the column is used, never quoted twice ----
   @Test
   void dialectMergedSql() throws Exception {
      String[][] cases = {
         { "select o.\"Mixed\", o.amt from tw o", null, null },
         { "select o.\"Mixed\", o.amt from tw o", "sort", "Mixed" },
         { "select o.\"Mixed\", o.amt from tw o", "cond", "Mixed" },
         { "select o.\"Mixed\", o.amt from tw o", "sum", "Mixed" },
         { "select o.\"Mixed\", o.amt from tw o", "group", "Mixed" },
         { "select o.\"mixed\", o.amt from tw o", null, null },
         { "select o.\"mixed\", o.amt from tw o", "sort", "mixed" },
         { "select o.\"mixed\", o.amt from tw o", "cond", "mixed" },
         { "select o.\"mixed\", o.amt from tw o", "sum", "mixed" },
         { "select o.\"mixed\", o.amt from tw o", "group", "mixed" },
         { "select \"mixed\", amt from tw", "sort", "mixed" },
         { "select \"Mixed\", amt from tw", "cond", "Mixed" },
      };

      for(String db : new String[] { "oracle", "postgresql" }) {
         for(String[] c : cases) {
            String out = sqlFor(db, c[0], c[1], c[2]);
            assertFalse(out.contains("\"\""), db + " double quote: " + out);
            String seg = c[0].contains("\"mixed\"") ? "mixed" : "Mixed";

            // the quoted segment must be generated quoted everywhere it appears
            if("oracle".equals(db) || !"mixed".equals(seg)) {
               // an output alias (as Mixed) only names the column
               assertFalse(out.replaceAll(" as \\w+", "").matches(".*[ .(]" + seg + "\\b.*"),
                           db + " unquoted " + seg + ": " + out);
            }
         }
      }
   }

   private static String sqlFor(String db, String sql, String op, String column)
      throws Exception
   {
      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("bug77786" + db);

      if("oracle".equals(db)) {
         ds.setDriver("oracle.jdbc.OracleDriver");
         ds.setURL("jdbc:oracle:thin:@localhost:1521:x");
         ds.setRuntimeProductName("oracle");
      }
      else {
         ds.setDriver("org.postgresql.Driver");
         ds.setURL("jdbc:postgresql://localhost/db");
         ds.setRuntimeProductName("postgresql");
         ds.setProductVersion("10.0");
      }

      Worksheet ws = new Worksheet();
      SQLBoundTableAssembly table = newTable(ws, sql, ds);

      if("sort".equals(op)) {
         sort(table, column, XConstants.SORT_DESC);
      }
      else if("cond".equals(op)) {
         condition(table, column, 0);
      }
      else if("sum".equals(op)) {
         aggregate(table, "amt", column);
      }
      else if("group".equals(op)) {
         aggregate(table, column, "amt");
      }

      SQLBoundQuery query = new SQLBoundQuery(
         AssetQuerySandbox.RUNTIME_MODE, new AssetQuerySandbox(ws), table, false, false);
      query.merge(new VariableTable());
      UniformSQL copy = (UniformSQL) query.getQuery().getSQLDefinition().clone();
      copy.clearSQLString();
      return normalize(copy.getSQLString()).replaceAll("dataselcond[0-9_]+", "X");
   }

   private static String pgSql(String sql, String op, String column) throws Exception {
      Worksheet ws = new Worksheet();
      SQLBoundTableAssembly table = newTable(ws, sql, postgresql());

      if("sort".equals(op)) {
         sort(table, column, XConstants.SORT_DESC);
      }
      else if("cond".equals(op)) {
         condition(table, column, 0);
      }
      else if("sum".equals(op)) {
         aggregate(table, "amt", column);
      }
      else if("group".equals(op)) {
         aggregate(table, column, "amt");
      }

      SQLBoundQuery query = new SQLBoundQuery(
         AssetQuerySandbox.RUNTIME_MODE, new AssetQuerySandbox(ws), table, false, false);
      query.merge(new VariableTable());
      UniformSQL copy = (UniformSQL) query.getQuery().getSQLDefinition().clone();
      copy.clearSQLString();
      return normalize(copy.getSQLString()).replaceAll("dataselcond[0-9_]+", "X");
   }

   /**
    * The rows of some columns, found by name (the metadata state reorders the select list and
    * names a column by its metadata case), in the order the query returned them.
    */
   private static List<List<Integer>> rows(Result r, boolean meta, String... names) {
      assertNotNull(r.ordered, r.msg(meta));
      int[] idx = new int[names.length];

      for(int i = 0; i < names.length; i++) {
         idx[i] = -1;

         for(int c = 0; c < r.headers.size() && idx[i] < 0; c++) {
            String header = r.headers.get(c).toLowerCase();
            String name = names[i].toLowerCase();

            if(header.equals(name) || header.endsWith("." + name)) {
               idx[i] = c;
            }
         }

         assertTrue(idx[i] >= 0, names[i] + " not in " + r.headers + ", " + r.msg(meta));
      }

      List<List<Integer>> rows = new ArrayList<>();

      for(List<Integer> row : r.ordered) {
         List<Integer> nrow = new ArrayList<>();

         for(int i : idx) {
            nrow.add(row.get(i));
         }

         rows.add(nrow);
      }

      return rows;
   }

   // the rows of some columns, sorted
   private static List<List<Integer>> sorted(Result r, boolean meta, String... names) {
      List<List<Integer>> rows = rows(r, meta, names);
      rows.sort(Comparator.comparing(Object::toString));
      return rows;
   }

   // ---- harness ----

   private record Result(String sql, List<String> headers, List<List<Integer>> ordered,
                         String error)
   {
      String msg(boolean meta) {
         return (meta ? "META: " : "NO-META: ") + sql + (error != null ? " error: " + error : "");
      }
   }

   private static Result run(String sql, String dbTable, boolean meta,
                             Consumer<SQLBoundTableAssembly> customize) throws Exception
   {
      Worksheet ws = new Worksheet();
      SQLBoundTableAssembly table = newTable(ws, sql, dbTable, meta);

      if(customize != null) {
         customize.accept(table);
      }

      VariableTable vars = new VariableTable();
      SQLBoundQuery query = new SQLBoundQuery(
         AssetQuerySandbox.RUNTIME_MODE, new AssetQuerySandbox(ws), table, false, false);
      query.merge(vars);
      executed.set(null);
      List<String> headers = new ArrayList<>();

      try {
         List<List<Integer>> ordered = execute(query.getQuery(), vars, headers);
         return new Result(normalize(executed.get()), headers, ordered, null);
      }
      catch(Throwable ex) {
         return new Result(normalize(executed.get()), headers, null, ex.toString());
      }
   }

   private static String normalize(String sql) {
      return sql == null ? null : sql.trim().replaceAll("\\s+", " ");
   }

   // the worksheet column of a name, names are of the metadata case when it is resolved
   private static DataRef ref(SQLBoundTableAssembly table, String name) {
      ColumnSelection cols = table.getColumnSelection(false);
      DataRef ref = cols.getAttribute(name);

      for(int i = 0; ref == null && i < cols.getAttributeCount(); i++) {
         if(cols.getAttribute(i).getName().replace("\"", "").equalsIgnoreCase(name)) {
            ref = cols.getAttribute(i);
         }
      }

      assertNotNull(ref, name);
      return ref;
   }

   private static void sort(SQLBoundTableAssembly table, String name, int order) {
      SortRef sort = new SortRef(ref(table, name));
      sort.setOrder(order);
      SortInfo info = new SortInfo();
      info.addSort(sort);
      table.setSortInfo(info);
   }

   private static void condition(SQLBoundTableAssembly table, String name, int value) {
      AssetCondition cond = new AssetCondition();
      cond.setOperation(XCondition.GREATER_THAN);
      cond.setType(XSchema.INTEGER);
      cond.addValue(value);
      ConditionList list = new ConditionList();
      list.append(new ConditionItem(ref(table, name), cond, 0));
      table.setPreConditionList(list);
   }

   private static void aggregate(SQLBoundTableAssembly table, String group, String sum) {
      AggregateInfo info = new AggregateInfo();

      if(group != null) {
         info.addGroup(new GroupRef(ref(table, group)));
      }

      info.addAggregate(new AggregateRef(ref(table, sum), AggregateFormula.SUM));
      table.setAggregateInfo(info);
      table.setAggregate(true);
   }

   private static SQLBoundTableAssembly newTable(Worksheet ws, String sqlText, String dbTable,
                                                 boolean meta) throws Exception
   {
      return newTable(ws, sqlText, dbTable, meta, dataSource());
   }

   private static SQLBoundTableAssembly newTable(Worksheet ws, String sqlText,
                                                 JDBCDataSource ds) throws Exception
   {
      return newTable(ws, sqlText, null, false, ds);
   }

   private static SQLBoundTableAssembly newTable(Worksheet ws, String sqlText, String dbTable,
                                                 boolean meta, JDBCDataSource ds)
      throws Exception
   {
      UniformSQL usql = new UniformSQL();
      usql.setDataSource(ds);
      Method parse = UniformSQL.class.getDeclaredMethod("parse", String.class, int.class,
                                                        long.class);
      parse.setAccessible(true);
      // parse(String, int, long) is package private, PARSE_ALL is 0
      parse.invoke(usql, sqlText, 0, 4000L);
      assertEquals(UniformSQL.PARSE_SUCCESS, usql.getParseResult(), sqlText);
      assertFalse(usql.isLossy(), sqlText);
      usql.setSQLString(sqlText, false);

      if(meta) {
         // what JDBCUtil.fixUniformSQLInfo does with the table metadata from the database
         String[] names = "tw".equals(dbTable) ? TW_COLUMNS : NT_COLUMNS;
         String alias = usql.getTableAlias(0);

         for(String name : names) {
            usql.addField(new XField(name, name, alias, XSchema.INTEGER));
         }

         JDBCUtil.fixSelectionInfo(usql);
         JDBCUtil.fixWhereInfo(usql);
         usql.syncTable();
      }

      JDBCQuery query = new JDBCQuery();
      query.setName("bug77786");
      query.setUserQuery(true);
      query.setDataSource(ds);
      query.setSQLDefinition(usql);

      SQLBoundTableAssembly table = new SQLBoundTableAssembly(ws, "T1");
      SQLBoundTableAssemblyInfo info = (SQLBoundTableAssemblyInfo) table.getTableInfo();
      info.setQuery(query);
      info.setSourceInfo(new SourceInfo(SourceInfo.DATASOURCE, ds.getName(), ds.getName()));
      table.setSQLEdited(true);

      // the worksheet columns are named by the last segment of the select column, as
      // QueryManagerService.getColumnSelection names them
      ColumnSelection columns = new ColumnSelection();
      XSelection selection = usql.getSelection();

      for(int i = 0; i < selection.getColumnCount(); i++) {
         String path = selection.getColumn(i);
         String name = path.substring(path.lastIndexOf('.') + 1);
         ColumnRef column = new ColumnRef(new AttributeRef(null, name));
         column.setDataType(XSchema.INTEGER);
         columns.addAttribute(column);
      }

      table.setColumnSelection(columns, false);
      table.setColumnSelection(columns, true);
      ws.addAssembly(table);
      return table;
   }

   // the merged query run as AssetQuery runs it, through XSessionManager and JDBCHandler
   private static List<List<Integer>> execute(JDBCQuery query, VariableTable vars,
                                              List<String> headers) throws Exception
   {
      XDataService dataService = mock(XDataService.class);
      when(dataService.execute(any(), any(XQuery.class), any(), any(), anyBoolean(), any()))
         .thenAnswer(inv -> {
            JDBCHandler handler = new JDBCHandler();
            XQuery xquery = inv.getArgument(1);
            handler.connect(xquery.getDataSource(), inv.getArgument(2));
            return handler.execute(xquery, inv.getArgument(2), inv.getArgument(3),
                                   inv.getArgument(5));
         });
      XSessionManager session = new XSessionManager(
         dataService, ConfigurationContext.getContext().getSpringBean(XSessionService.class),
         null);
      session.setCacheData(false);

      try {
         TableLens lens = session.getXNodeTableLens(query, vars, null, null, null, -1);
         assertNotNull(lens, "query failed: " + executed.get());
         lens.moreRows(Integer.MAX_VALUE);
         List<List<Integer>> rows = new ArrayList<>();

         for(int c = 0; c < lens.getColCount(); c++) {
            headers.add(String.valueOf(lens.getObject(0, c)));
         }

         for(int r = lens.getHeaderRowCount(); r < lens.getRowCount(); r++) {
            List<Integer> row = new ArrayList<>();

            for(int c = 0; c < lens.getColCount(); c++) {
               Object val = lens.getObject(r, c);
               row.add(val == null ? null : ((Number) val).intValue());
            }

            rows.add(row);
         }

         return rows;
      }
      finally {
         session.tearDown();
      }
   }

   // JDBCHandler generates the sql on a clone of the query, record the sql that runs
   private static DataSource recording(DataSource ds) {
      return proxy(DataSource.class, ds);
   }

   @SuppressWarnings("unchecked")
   private static <T> T proxy(Class<T> type, T target) {
      return (T) Proxy.newProxyInstance(
         SQLBoundQueryMergeQuotedColumnTest.class.getClassLoader(), new Class<?>[] { type },
         (p, method, args) -> {
            String name = method.getName();

            if(args != null && args.length > 0 && args[0] instanceof String &&
               (name.startsWith("prepare") || name.startsWith("execute")) &&
               ((String) args[0]).trim().toLowerCase().startsWith("select"))
            {
               executed.set((String) args[0]);
            }

            Object result;

            try {
               result = method.invoke(target, args);
            }
            catch(InvocationTargetException e) {
               throw e.getCause();
            }

            if(result instanceof Connection && type != Connection.class) {
               return proxy(Connection.class, (Connection) result);
            }

            if(result instanceof Statement && !(result instanceof PreparedStatement)) {
               return proxy(Statement.class, (Statement) result);
            }

            return result;
         });
   }

   private static final ThreadLocal<String> executed = new ThreadLocal<>();

   private static JDBCDataSource dataSource() {
      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("bug77786merge");
      ds.setDriver("org.apache.derby.jdbc.EmbeddedDriver");
      ds.setURL("jdbc:derby:" + DB);
      ds.setRequireLogin(false);
      return ds;
   }

   private static JDBCDataSource postgresql() {
      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("bug77786postgresql");
      ds.setDriver("org.postgresql.Driver");
      ds.setURL("jdbc:postgresql://localhost/db");
      ds.setRuntimeProductName("postgresql");
      ds.setProductVersion("10.0");
      return ds;
   }

   private static DataSource derby() {
      EmbeddedDataSource ds = new EmbeddedDataSource();
      ds.setDatabaseName(DB);
      ds.setCreateDatabase("create");
      return ds;
   }
}
