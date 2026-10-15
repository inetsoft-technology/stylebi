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

import inetsoft.mv.MVDef;
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
 * Bug #78446: a shared (brush/data tip) condition on a date range, WeekOfYear(d) = 4, applied
 * to a table that has a same-named column that is not a date range (in a viewsheet, the
 * pass-through of a date comparison runtime calc field). VSAQuery.fixConditionList adds the
 * hidden date range column under a reserved name and binds the condition to it. Run on a
 * database table, the condition filters on the date's week and the output has neither the
 * hidden column nor a renamed (_1) copy of the same-named column.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  VSAQueryConditionColumnJdbcTest.JdbcConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class VSAQueryConditionColumnJdbcTest {
   private static final String DB = "memory:vsaqueryconditioncolumn";
   private static final String WEEK = "WeekOfYear(d)";

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

   // d is in the plain weeks 3, 4, 4, 5 of 2021 (weeks start on Sunday)
   @BeforeAll
   static void createTables() throws Exception {
      try(Connection conn = derby().getConnection(); Statement stmt = conn.createStatement()) {
         try {
            stmt.executeUpdate("drop table ev");
         }
         catch(Exception ignore) {
            // first run
         }

         stmt.executeUpdate("create table ev (d date, v int, w int)");
         stmt.executeUpdate("insert into ev values ('2021-01-14', 1, 13), " +
                               "('2021-01-17', 10, 14), ('2021-01-20', 100, 14), " +
                               "('2021-01-25', 1000, 4)");
      }
   }

   /**
    * The condition filters on the week of d, not on the same-named column w (whose value 4
    * is on a row of week 5), and the output keeps the same-named column under its own name.
    */
   @Test
   void dateRangeConditionNextToSameNamedColumnFiltersOnTheDate() throws Exception {
      Worksheet ws = new Worksheet();
      SQLBoundTableAssembly t1 = sqlTable(ws);
      MirrorTableAssembly m1 = new MirrorTableAssembly(ws, "M1", t1);
      m1.setProperty("no_cache", "true");
      ws.addAssembly(m1);
      m1.update();
      // the same-named column that is not a date range
      ColumnSelection columns = m1.getColumnSelection(false);
      ((ColumnRef) find(columns, "w")).setAlias(WEEK);
      m1.setColumnSelection(columns, false);
      m1.update();

      ConditionList conds = weekCondition(4);
      String before = conds.toString();
      ConditionList applied = conds.clone();
      fixConditionList(applied, m1.getColumnSelection(false));
      fixConditionList(applied, m1.getColumnSelection(true));
      m1.setPreRuntimeConditionList(applied);

      DataRef attr = applied.getConditionItem(0).getAttribute();
      assertNotEquals(WEEK, attr.getName(), "the condition is bound to a reserved name");
      assertTrue(((ColumnRef) attr).getDataRef() instanceof DateRangeRef, attr.toString());
      assertEquals(before, conds.toString(), "the original condition is not changed");

      for(boolean pub : new boolean[] { false, true }) {
         ColumnSelection sel = m1.getColumnSelection(pub);
         DataRef same = sel.getAttribute(WEEK, false);
         assertNotNull(same, "the same-named column is kept, pub=" + pub);
         assertFalse(((ColumnRef) same).getDataRef() instanceof DateRangeRef, same.toString());
         DataRef hidden = sel.getAttribute(attr.getName(), false);
         assertNotNull(hidden, "the hidden column, pub=" + pub + " " + sel);
         assertFalse(((ColumnRef) hidden).isVisible(), hidden.toString());
         int count = 0;

         for(int i = 0; i < sel.getAttributeCount(); i++) {
            count += sel.getAttribute(i).getName().startsWith(WEEK) ? 1 : 0;
         }

         // the second call does not add another renamed column
         assertEquals(2, count, "WeekOfYear columns, pub=" + pub + " " + sel);
      }

      AssetQuery query = AssetQuery.createAssetQuery(
         m1, AssetQuerySandbox.RUNTIME_MODE, new AssetQuerySandbox(ws), false, -1L, true, false);
      TableLens lens = query.getTableLens(new VariableTable());
      assertNotNull(lens, "the query failed, see the log");
      lens.moreRows(Integer.MAX_VALUE);
      List<Object> headers = new ArrayList<>();

      for(int c = 0; c < lens.getColCount(); c++) {
         headers.add(lens.getObject(0, c));
      }

      assertEquals(List.of("d", "v", WEEK), headers);
      List<Integer> values = new ArrayList<>();

      for(int r = 1; r < lens.getRowCount(); r++) {
         values.add(((Number) lens.getObject(r, 1)).intValue());
      }

      assertEquals(List.of(10, 100), values, "the rows of week 4");
   }

   /**
    * A materialized view finds the column of a date range condition by its base column and
    * date level (MVDef.getMVHeader), so the reserved name binds to the same MV column.
    */
   @Test
   void renamedConditionColumnHasTheSameMVHeader() throws Exception {
      ConditionList conds = weekCondition(4);
      DataRef original = conds.getConditionItem(0).getAttribute();
      ColumnSelection cols = new ColumnSelection();
      AttributeRef base = new AttributeRef(null, "d");
      base.setDataType(XSchema.DATE);
      cols.addAttribute(new ColumnRef(base));
      cols.addAttribute(new ColumnRef(new AttributeRef(null, WEEK)));
      fixConditionList(conds, cols);
      DataRef renamed = conds.getConditionItem(0).getAttribute();

      assertNotEquals(original.getName(), renamed.getName());
      assertEquals(MVDef.getMVHeader(original), MVDef.getMVHeader(renamed));
   }

   private static ConditionList weekCondition(int week) {
      AttributeRef base = new AttributeRef(null, "d");
      base.setDataType(XSchema.DATE);
      DateRangeRef range = new DateRangeRef(WEEK, base, DateRangeRef.WEEK_OF_YEAR_PART);
      range.setOriginalType(XSchema.DATE);
      ColumnRef column = new ColumnRef(range);
      column.setDataType(XSchema.INTEGER);
      Condition cond = new Condition(XSchema.INTEGER);
      cond.setOperation(XCondition.EQUAL_TO);
      cond.addValue(week);
      ConditionList conds = new ConditionList();
      conds.append(new ConditionItem(column, cond, 0));
      return conds;
   }

   // the shared condition path (applySharedCondition) of VSAQuery
   private static void fixConditionList(ConditionList conds, ColumnSelection cols)
      throws Exception
   {
      Method method = VSAQuery.class.getDeclaredMethod(
         "fixConditionList", ConditionList.class, ColumnSelection.class, boolean.class);
      method.setAccessible(true);
      method.invoke(null, conds, cols, true);
   }

   private static SQLBoundTableAssembly sqlTable(Worksheet ws) throws Exception {
      String text = "select ev.d, ev.v, ev.w from ev";
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

      SQLBoundTableAssembly table = new SQLBoundTableAssembly(ws, "T1");
      SQLBoundTableAssemblyInfo info = (SQLBoundTableAssemblyInfo) table.getTableInfo();
      info.setQuery(query);
      info.setSourceInfo(new SourceInfo(SourceInfo.DATASOURCE, ds.getName(), ds.getName()));
      table.setSQLEdited(true);
      table.setProperty("no_cache", "true");

      ColumnSelection columns = new ColumnSelection();
      String[][] cols = { { "d", XSchema.DATE }, { "v", XSchema.INTEGER },
                          { "w", XSchema.INTEGER } };

      for(String[] col : cols) {
         AttributeRef attr = new AttributeRef(col[0]);
         attr.setDataType(col[1]);
         ColumnRef ref = new ColumnRef(attr);
         ref.setDataType(col[1]);
         columns.addAttribute(ref);
      }

      table.setColumnSelection(columns, false);
      table.setColumnSelection((ColumnSelection) columns.clone(), true);
      ws.addAssembly(table);
      return table;
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

   private static JDBCDataSource dataSource() {
      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("vsaqueryconditioncolumn");
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
