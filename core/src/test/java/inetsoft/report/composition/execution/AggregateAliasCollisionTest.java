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
 * Bug #78529: an aggregate whose alias is the name of another column of the table. The
 * worksheet read (AssetQuerySandbox.getTableLens) maps the aggregated result back to the
 * table's columns in AssetQueryCacheNormalizer, which looked each column up by entity + alias.
 * That key is the identifier of the other column, so the aliased aggregate showed the other
 * column's values, or was dropped when the key matched nothing. Source S has the columns G, Y
 * and X, and the rows (a, 100, 1), (a, 200, 2) and (b, 300, 3), so for group a Sum(Y) is 300,
 * Sum(X) is 3 and Max(X) is 2.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  AggregateAliasCollisionTest.JdbcConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class AggregateAliasCollisionTest {
   private static final String DB = "memory:aggregatealiascollision";

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
         XRepository dataService = mock(XRepository.class);
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
      try(Connection conn = derby().getConnection(); Statement stmt = conn.createStatement()) {
         try {
            stmt.executeUpdate("drop table s");
         }
         catch(Exception ignore) {
            // first run
         }

         stmt.executeUpdate("create table s (g varchar(10), y int, x int)");
         stmt.executeUpdate("insert into s values ('a', 100, 1), ('a', 200, 2), ('b', 300, 3)");
      }
   }

   /**
    * The reported shape: Sum(X), and Sum(Y) aliased X. Sum(Y) showed Sum(X).
    */
   @Test
   void aliasEqualToAnAggregatedColumn() throws Exception {
      Worksheet ws = new Worksheet();
      MirrorTableAssembly m = mirror(ws, "M", embedded(ws, "S"));
      aggregate(m, "G", sum("Y", "X"), sum("X", null));
      assertEquals(List.of(row("a", 300, 3), row("b", 300, 3)), rows(ws, m));
   }

   /**
    * The same without any generated _1 alias: Sum(X) aliased X2, Sum(Y) aliased X.
    */
   @Test
   void aliasEqualToAnAggregatedColumnWithItsOwnAlias() throws Exception {
      Worksheet ws = new Worksheet();
      MirrorTableAssembly m = mirror(ws, "M", embedded(ws, "S"));
      aggregate(m, "G", sum("Y", "X"), sum("X", "X2"));
      assertEquals(List.of(row("a", 300, 3), row("b", 300, 3)), rows(ws, m));
   }

   /**
    * Sum(Y) aliased X, with X itself not in the output. The aggregate was dropped.
    */
   @Test
   void aliasEqualToAColumnNotInTheOutput() throws Exception {
      Worksheet ws = new Worksheet();
      MirrorTableAssembly m = mirror(ws, "M", embedded(ws, "S"));
      aggregate(m, "G", sum("Y", "X"));
      assertEquals(List.of(row("a", 300), row("b", 300)), rows(ws, m));
   }

   /**
    * The same with the aggregate turned off on the table, so its result is mapped back the
    * same way for having fewer columns than the table.
    */
   @Test
   void aliasEqualToAColumnNotInTheOutputAggregateOff() throws Exception {
      Worksheet ws = new Worksheet();
      MirrorTableAssembly m = mirror(ws, "M", embedded(ws, "S"));
      aggregate(m, "G", sum("Y", "X"));
      m.setAggregate(false);
      assertEquals(List.of(row("a", 300), row("b", 300)), rows(ws, m));
   }

   /**
    * The other column's formula doesn't matter: Sum(Y) aliased X showed Max(X).
    */
   @Test
   void aliasEqualToAColumnWithAnotherFormula() throws Exception {
      Worksheet ws = new Worksheet();
      MirrorTableAssembly m = mirror(ws, "M", embedded(ws, "S"));
      aggregate(m, "G", sum("Y", "X"),
                new Agg("X", AggregateFormula.MAX, null));
      assertEquals(List.of(row("a", 300, 2), row("b", 300, 3)), rows(ws, m));
   }

   /**
    * No alias is written on the aggregates at all: S renames X to Z and the mirror renames Y to
    * Z. The worksheet reaches this by renaming M.Y to Z and then S.X to Z, which reaches M,
    * since each rename checks only its own table.
    */
   @Test
   void collisionFromRenames() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly s = embedded(ws, "S");
      rename(s, "X", "Z");
      MirrorTableAssembly m = mirror(ws, "M", s);
      rename(m, "Y", "Z");
      aggregate(m, "G", sum("Y", null), sum("Z", null));
      assertEquals(List.of(row("a", 300, 3), row("b", 300, 3)), rows(ws, m));
   }

   /**
    * Control, correct before and after: an alias that isn't the name of another column.
    */
   @Test
   void aliasNotEqualToAnotherColumn() throws Exception {
      Worksheet ws = new Worksheet();
      MirrorTableAssembly m = mirror(ws, "M", embedded(ws, "S"));
      aggregate(m, "G", sum("Y", "FOO"), sum("X", null));
      assertEquals(List.of(row("a", 300, 3), row("b", 300, 3)), rows(ws, m));
   }

   /**
    * Control, correct before and after: no aggregate, the columns are mapped back by their
    * position in the column selection.
    */
   @Test
   void detailMirrorWithAliases() throws Exception {
      Worksheet ws = new Worksheet();
      MirrorTableAssembly m = mirror(ws, "M", embedded(ws, "S"));
      rename(m, "Y", "X2");
      assertEquals(List.of(row("a", 100, 1), row("a", 200, 2), row("b", 300, 3)), rows(ws, m));
   }

   /**
    * The reported shape over a SQL source, where the mirror is merged into the sql.
    */
   @Test
   void aliasEqualToAnAggregatedColumnOverSql() throws Exception {
      Worksheet ws = new Worksheet();
      SQLBoundTableAssembly s = sqlTable(ws, "S", "select s.g, s.y, s.x from s");
      MirrorTableAssembly m = mirror(ws, "M", s);
      aggregate(m, "G", sum("Y", "X"), sum("X", null));
      assertEquals(List.of(row("a", 300, 3), row("b", 300, 3)), rows(ws, m));

      MirrorTableAssembly m2 = mirror(ws, "M2", s);
      aggregate(m2, "G", sum("Y", "X"));
      assertEquals(List.of(row("a", 300), row("b", 300)), rows(ws, m2));
   }

   private record Agg(String column, AggregateFormula formula, String alias) {
   }

   private static Agg sum(String column, String alias) {
      return new Agg(column, AggregateFormula.SUM, alias);
   }

   private static List<Object> row(Object... values) {
      return Arrays.asList(values);
   }

   private static EmbeddedTableAssembly embedded(Worksheet ws, String name) {
      EmbeddedTableAssembly table = new EmbeddedTableAssembly(ws, name);
      Object[][] data = {
         { "G", "Y", "X" }, { "a", 100, 1 }, { "a", 200, 2 }, { "b", 300, 3 } };
      String[] types = { XSchema.STRING, XSchema.INTEGER, XSchema.INTEGER };
      table.setEmbeddedData(new XEmbeddedTable(types, data));
      ws.addAssembly(table);
      return table;
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

      // as QueryManagerService.getColumnSelection names the columns
      ColumnSelection columns = new ColumnSelection();
      String[] types = { XSchema.STRING, XSchema.INTEGER, XSchema.INTEGER };
      String[] names = { "G", "Y", "X" };

      for(int i = 0; i < names.length; i++) {
         ColumnRef ref = new ColumnRef(new AttributeRef(names[i]));
         ref.setDataType(types[i]);
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

   // an alias on an aggregate is the alias of its column, as the wiz aggregate tool sets it
   private static void aggregate(TableAssembly table, String group, Agg... aggs) {
      ColumnSelection columns = table.getColumnSelection(false);
      AggregateInfo info = new AggregateInfo();
      info.addGroup(new GroupRef(find(columns, group)));
      List<ColumnRef> refs = new ArrayList<>();

      for(Agg agg : aggs) {
         refs.add((ColumnRef) find(columns, agg.column));
      }

      for(int i = 0; i < aggs.length; i++) {
         if(aggs[i].alias != null) {
            refs.get(i).setAlias(aggs[i].alias);
         }

         info.addAggregate(new AggregateRef(refs.get(i), aggs[i].formula));
      }

      table.setColumnSelection(columns, false);
      table.setAggregateInfo(info);
      table.setAggregate(true);
      table.update();
   }

   // by the column name first, the alias may be the name of another column
   private static DataRef find(ColumnSelection columns, String name) {
      for(int i = 0; i < columns.getAttributeCount(); i++) {
         ColumnRef column = (ColumnRef) columns.getAttribute(i);

         if(name.equals(column.getAttribute())) {
            return column;
         }
      }

      for(int i = 0; i < columns.getAttributeCount(); i++) {
         ColumnRef column = (ColumnRef) columns.getAttribute(i);

         if(name.equals(column.getAlias())) {
            return column;
         }
      }

      throw new AssertionError("no column " + name + " in " + columns);
   }

   /**
    * The rows of the table as the worksheet reads it, sorted by the group column.
    */
   private static List<List<Object>> rows(Worksheet ws, TableAssembly table) throws Exception {
      AssetQuerySandbox box = new AssetQuerySandbox(ws);

      try {
         TableLens lens = box.getTableLens(table.getName(), AssetQuerySandbox.RUNTIME_MODE);
         assertNotNull(lens, table.getName());
         List<List<Object>> rows = new ArrayList<>();

         for(int r = 1; lens.moreRows(r); r++) {
            List<Object> row = new ArrayList<>();

            for(int c = 0; c < lens.getColCount(); c++) {
               Object value = lens.getObject(r, c);
               row.add(value instanceof Number ? ((Number) value).intValue() : value);
            }

            rows.add(row);
         }

         rows.sort(Comparator.comparing(row -> String.valueOf(row.get(0))));
         return rows;
      }
      finally {
         box.dispose();
      }
   }

   private static JDBCDataSource dataSource() {
      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("aggregatealiascollision");
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
