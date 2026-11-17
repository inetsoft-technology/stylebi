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
import inetsoft.web.wiz.worksheet.WorksheetMutationSupport;
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
 * Bug #81559: a percentage option on a worksheet aggregate must change the values the worksheet
 * read returns. Source S has the rows (a, 100, 1), (a, 200, 2), (b, 300, 3), so the Sum(Y) of
 * group a is 300 and of b is 300 (each 50% of the grand total, 100% of its own group).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  AggregatePercentageTest.JdbcConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class AggregatePercentageTest {
   private static final String DB = "memory:aggregatepercentage";

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
         inetsoft.uql.erm.XDataModel model = dataModel();
         XRepository repository = mock(XRepository.class);
         when(repository.getDataSource(any(String.class))).thenReturn(dataSource());
         when(repository.getDataModel(any(String.class))).thenReturn(model);
         return repository;
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

   @Test
   void percentageOfGrandTotalOverEmbedded() throws Exception {
      Worksheet ws = new Worksheet();
      MirrorTableAssembly m = mirror(ws, "M", embedded(ws, "S"));
      aggregate(m, "G", pct("Y", XConstants.PERCENTAGE_OF_GRANDTOTAL));
      assertEquals(List.of(row("a", 0.5), row("b", 0.5)), rows(ws, m));
   }

   @Test
   void percentageOfSubTotalOverEmbedded() throws Exception {
      Worksheet ws = new Worksheet();
      MirrorTableAssembly m = mirror(ws, "M", embedded(ws, "S"));
      aggregate(m, new String[] { "G", "X" }, pct("Y", XConstants.PERCENTAGE_OF_GROUP));
      assertEquals(List.of(row("a", 0.33, 1), row("a", 0.67, 2), row("b", 1.0, 3)),
                   rows(ws, m));
   }

   @Test
   void percentageOfGrandTotalOverTwoGroupsEmbedded() throws Exception {
      Worksheet ws = new Worksheet();
      MirrorTableAssembly m = mirror(ws, "M", embedded(ws, "S"));
      aggregate(m, new String[] { "G", "X" }, pct("Y", XConstants.PERCENTAGE_OF_GRANDTOTAL));
      assertEquals(List.of(row("a", 0.17, 1), row("a", 0.33, 2), row("b", 0.5, 3)),
                   rows(ws, m));
   }

   @Test
   void percentageOfSubTotalOverSql() throws Exception {
      Worksheet ws = new Worksheet();
      MirrorTableAssembly m = mirror(ws, "M", sqlTable(ws, "S", "select s.g, s.y, s.x from s"));
      aggregate(m, new String[] { "G", "X" }, pct("Y", XConstants.PERCENTAGE_OF_GROUP));
      assertEquals(List.of(row("a", 0.33, 1), row("a", 0.67, 2), row("b", 1.0, 3)),
                   rows(ws, m));
   }

   @Test
   void percentageOfSubTotalOnPhysicalTable() throws Exception {
      Worksheet ws = new Worksheet();
      PhysicalBoundTableAssembly s = physicalTable(ws, "S");
      aggregate(s, new String[] { "G", "X" }, pct("Y", XConstants.PERCENTAGE_OF_GROUP));
      assertEquals(List.of(row("a", 0.33, 1), row("a", 0.67, 2), row("b", 1.0, 3)),
                   rows(ws, s));
   }

   // the agent's own mutation path (set_group_aggregate), not a hand-built AggregateInfo
   @Test
   void percentageOfSubTotalThroughApplyAggregateInfo() throws Exception {
      Worksheet ws = new Worksheet();
      MirrorTableAssembly m = mirror(ws, "M", embedded(ws, "S"));
      WorksheetMutationSupport.applyAggregateInfo(
         m,
         List.of(new WorksheetMutationSupport.GroupSpec("G"),
                 new WorksheetMutationSupport.GroupSpec("X")),
         List.of(new WorksheetMutationSupport.AggregateSpec(
            "Y", "Sum", "SHARE", null, null, "group")));
      m.setAggregate(true);
      m.update();
      assertEquals(List.of(row("a", 0.33, 1), row("a", 0.67, 2), row("b", 1.0, 3)),
                   rows(ws, m));
   }

   @Test
   void percentageOfGrandTotalThroughApplyAggregateInfo() throws Exception {
      Worksheet ws = new Worksheet();
      PhysicalBoundTableAssembly s = physicalTable(ws, "S");
      WorksheetMutationSupport.applyAggregateInfo(
         s, List.of(new WorksheetMutationSupport.GroupSpec("G")),
         List.of(new WorksheetMutationSupport.AggregateSpec(
            "Y", "Sum", "SHARE", null, null, "grand_total")));
      s.setAggregate(true);
      s.update();
      assertEquals(List.of(row("a", 0.5), row("b", 0.5)), rows(ws, s));
   }

   private static BoundTableAssembly modelTable(Worksheet ws, String name) {
      BoundTableAssembly table = new BoundTableAssembly(ws, name);
      table.setSourceInfo(new SourceInfo(SourceInfo.MODEL, dataSource().getName(), "lm"));
      table.setProperty("no_cache", "true");
      ColumnSelection columns = new ColumnSelection();
      String[] types = { XSchema.STRING, XSchema.INTEGER, XSchema.INTEGER };
      String[] names = { "G", "Y", "X" };

      for(int i = 0; i < names.length; i++) {
         ColumnRef ref = new ColumnRef(new AttributeRef("E", names[i]));
         ref.setDataType(types[i]);
         columns.addAttribute(ref);
      }

      table.setColumnSelection(columns);
      ws.addAssembly(table);
      return table;
   }

   private static inetsoft.uql.erm.XDataModel dataModel() {
      inetsoft.uql.erm.XLogicalModel lm = new inetsoft.uql.erm.XLogicalModel("lm");
      lm.setPartition("p");
      lm.setDataSource(dataSource().getName());
      inetsoft.uql.erm.XEntity entity = new inetsoft.uql.erm.XEntity("E");

      for(String column : new String[] { "G", "Y", "X" }) {
         entity.addAttribute(new inetsoft.uql.erm.XAttribute(column, "S", column));
      }

      lm.addEntity(entity);
      inetsoft.uql.erm.XPartition partition = new inetsoft.uql.erm.XPartition("p");
      partition.addTable("S", null, null);
      inetsoft.uql.erm.XDataModel model = mock(inetsoft.uql.erm.XDataModel.class);
      lm.setDataModel(model);
      when(model.getDataSource()).thenReturn(dataSource().getName());
      when(model.getLogicalModel(org.mockito.ArgumentMatchers.eq("lm"), any())).thenReturn(lm);
      when(model.getPartition(org.mockito.ArgumentMatchers.eq("p"), any())).thenReturn(partition);
      return model;
   }

   private static PhysicalBoundTableAssembly physicalTable(Worksheet ws, String name) {
      PhysicalBoundTableAssembly table = new PhysicalBoundTableAssembly(ws, name);
      JDBCDataSource ds = dataSource();
      table.setSourceInfo(new SourceInfo(SourceInfo.PHYSICAL_TABLE, ds.getName(), "S"));
      table.setProperty("no_cache", "true");
      ColumnSelection columns = new ColumnSelection();
      String[] types = { XSchema.STRING, XSchema.INTEGER, XSchema.INTEGER };
      String[] names = { "G", "Y", "X" };

      for(int i = 0; i < names.length; i++) {
         ColumnRef ref = new ColumnRef(new AttributeRef(null, names[i]));
         ref.setDataType(types[i]);
         columns.addAttribute(ref);
      }

      table.setColumnSelection(columns);
      ws.addAssembly(table);
      return table;
   }

   private record Agg(String column, AggregateFormula formula, String alias, int percentage) {
      Agg(String column, AggregateFormula formula, String alias) {
         this(column, formula, alias, XConstants.PERCENTAGE_NONE);
      }
   }

   private static Agg pct(String column, int option) {
      return new Agg(column, AggregateFormula.SUM, null, option);
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
      aggregate(table, new String[] { group }, aggs);
   }

   private static void aggregate(TableAssembly table, String[] groups, Agg... aggs) {
      ColumnSelection columns = table.getColumnSelection(false);
      AggregateInfo info = new AggregateInfo();

      for(String group : groups) {
         info.addGroup(new GroupRef(find(columns, group)));
      }

      List<ColumnRef> refs = new ArrayList<>();

      for(Agg agg : aggs) {
         refs.add((ColumnRef) find(columns, agg.column));
      }

      for(int i = 0; i < aggs.length; i++) {
         if(aggs[i].alias != null) {
            refs.get(i).setAlias(aggs[i].alias);
         }

         AggregateRef aref = new AggregateRef(refs.get(i), aggs[i].formula);
         aref.setPercentageOption(aggs[i].percentage);
         info.addAggregate(aref);
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
               row.add(value instanceof Number ? pctValue((Number) value) : value);
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

   // integers stay integers (the group column X), fractions are rounded to two places
   private static Object pctValue(Number n) {
      double d = n.doubleValue();
      return d == Math.rint(d) && !(n instanceof Double) ? (Object) n.intValue()
         : (Object) (Math.round(d * 100.0) / 100.0);
   }

   private static JDBCDataSource dataSource() {
      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("aggregatepercentage");
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
