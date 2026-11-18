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

import inetsoft.mv.*;
import inetsoft.report.TableLens;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.Organization;
import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.jdbc.SQLHelper;
import inetsoft.uql.schema.XSchema;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Regression test for Redmine bug #80446, worksheet shape. Query1 is a mirror table over an
 * MV-backed SQL table, grouped by a nullable column with max rows 5. The group sort and the max
 * rows then run in the {@link MirrorQuery} (not in the MVAssetQuery under it), so the
 * source dialect's NULL order has to be applied there.
 * <p>
 * Exercised for real: the query tree built by {@link AssetQuery#createAssetQuery} (a MirrorQuery
 * over a real MVAssetQuery, with the table's runtime MV set by the stubbed MV lookup), and the
 * MirrorQuery's own getSortTableLens / getSortSummaryTableLens / getMaxRowsTableLens on six
 * groups. Stubbed: the MV lookup and the SQL dialect of the table. Not run: reading a real MV
 * and a database.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, PluginsTestConfiguration.class,
                                  SwapperTestConfiguration.class,
                                  MirrorQueryGroupNullOrderTest.RepositoryConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class MirrorQueryGroupNullOrderTest {
   @Configuration
   static class RepositoryConfig {
      @Bean
      XRepository xRepository() {
         return mock(XRepository.class);
      }
   }

   @BeforeEach
   void setUp() {
      user = new XPrincipal(new IdentityID("u1", Organization.getDefaultOrganizationID()));
      wsEntry = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET,
                               "ws1", null);
      mvEnabled = SreeEnv.getProperty("ws.mv.enabled");
      SreeEnv.setProperty("ws.mv.enabled", "true");

      MVMetaData meta = new MVMetaData();
      meta.setBypassVPM(true);
      MVDef def = mock(MVDef.class);
      when(def.getMetaData()).thenReturn(meta);
      when(def.isValidMV(any(TableAssembly.class))).thenReturn(true);
      when(def.canHitMV(any(TableAssembly.class))).thenReturn(true);
      mgr = mock(MVManager.class);
      when(mgr.get("MV1")).thenReturn(def);
      when(mgr.findRuntimeMV(argThat(e -> e != null && e.isWorksheet()), isNull(), isNull(),
                             eq("ORDERS"), any(), isNull(), anyBoolean(), anyBoolean()))
         .thenAnswer(inv -> new RuntimeMV(inv.getArgument(0), null, null, "ORDERS", "MV1",
                                          false, 0L, null));
      mvManager = mockStatic(MVManager.class);
      mvManager.when(MVManager::getManager).thenReturn(mgr);
   }

   @AfterEach
   void tearDown() {
      mvManager.close();
      DialectTable.helper = null;

      if(mvEnabled == null) {
         SreeEnv.remove("ws.mv.enabled");
      }
      else {
         SreeEnv.setProperty("ws.mv.enabled", mvEnabled);
      }
   }

   @Test
   void nullsHighDialectKeepsNonNullGroupsAndDropsNullGroup() throws Exception {
      DialectTable.helper = SQLHelper.getSQLHelper("db2");

      assertEquals(List.of("0.0", "0.01", "0.05", "0.1", "0.15"),
                   run(true, 5, true, XConstants.SORT_ASC));
   }

   @Test
   void nullsHighDialectDescendingPutsNullGroupFirst() throws Exception {
      DialectTable.helper = SQLHelper.getSQLHelper("oracle");

      assertEquals(List.of("null", "0.15", "0.1", "0.05", "0.01"),
                   run(true, 5, true, XConstants.SORT_DESC));
   }

   @Test
   void nullsLowDialectIsUnchanged() throws Exception {
      DialectTable.helper = SQLHelper.getSQLHelper("mysql");

      assertEquals(List.of("null", "0.0", "0.01", "0.05", "0.1"),
                   run(true, 5, true, XConstants.SORT_ASC));
   }

   @Test
   void unknownSourceIsUnchanged() throws Exception {
      DialectTable.helper = null;

      assertEquals(List.of("null", "0.0", "0.01", "0.05", "0.1"),
                   run(true, 5, true, XConstants.SORT_ASC));
   }

   @Test
   void noMaxRowsIsUnchanged() throws Exception {
      DialectTable.helper = SQLHelper.getSQLHelper("db2");

      assertEquals(List.of("null", "0.0", "0.01", "0.05", "0.1", "0.15"),
                   run(true, 0, true, XConstants.SORT_ASC));
   }

   @Test
   void mirrorWithoutMVIsUnchanged() throws Exception {
      // a mirror over a table that has no MV is merged into SQL: the database sorts and limits,
      // the in-memory sorts and max rows are not applied at all and the rows pass untouched
      DialectTable.helper = SQLHelper.getSQLHelper("db2");

      assertEquals(List.of("0.15", "0.05", "null", "0.0", "0.1", "0.01"),
                   run(true, 5, false, XConstants.SORT_ASC));
   }

   /**
    * Builds Query1 (a mirror of ORDERS, grouped by DISCOUNT with a count, max rows), creates the
    * query like a viewsheet does and applies the sorts and max rows of the mirror query to six
    * groups (one NULL) in scrambled order. Returns the group column of the result.
    */
   private List<String> run(boolean grouped, int maxRows, boolean mv, int order)
      throws Exception
   {
      Worksheet ws = new Worksheet();
      DialectTable orders = new DialectTable(ws, "ORDERS");
      orders.setSourceInfo(new SourceInfo(SourceInfo.PHYSICAL_TABLE, "Orders", "ORDERS"));
      ColumnSelection cols = new ColumnSelection();
      cols.addAttribute(column("ORDERS", "DISCOUNT", XSchema.DOUBLE));
      cols.addAttribute(column("ORDERS", "ORDER_ID", XSchema.INTEGER));
      orders.setColumnSelection(cols);
      ws.addAssembly(orders);

      // Query1 is a mirror of the SQL table; the viewsheet binds a mirror of Query1
      MirrorTableAssembly root = new MirrorTableAssembly(ws, "Query1", orders);
      ws.addAssembly(root);

      ColumnSelection rcols = root.getColumnSelection();
      ColumnRef discount = (ColumnRef) rcols.getAttribute(0);
      ColumnRef id = (ColumnRef) rcols.getAttribute(1);
      AggregateInfo ainfo = new AggregateInfo();
      ainfo.addGroup(new GroupRef(discount));
      ainfo.addAggregate(new AggregateRef(id, AggregateFormula.COUNT_ALL));
      root.setAggregateInfo(ainfo);
      SortInfo sinfo = new SortInfo();
      SortRef sort = new SortRef(discount);
      sort.setOrder(order);
      sinfo.addSort(sort);
      root.setSortInfo(sinfo);
      root.setMaxRows(maxRows);

      if(!mv) {
         when(mgr.findRuntimeMV(any(), any(), any(), any(), any(), any(), anyBoolean(),
                                anyBoolean())).thenReturn(null);
      }

      AssetQuerySandbox box = new AssetQuerySandbox(ws, user, new VariableTable());
      box.setWSName("ws1");
      box.setWSEntry(wsEntry);
      // Query1 grouped over a table without MV: a MirrorQuery over a plain query
      AssetQuery query = AssetQuery.createAssetQuery((TableAssembly) root.clone(),
                                                     AssetQuerySandbox.RUNTIME_MODE, box, false,
                                                     -1L, true, false);
      assertInstanceOf(MirrorQuery.class, query);

      if(mv) {
         // put the real MV query of ORDERS under it, as it is when the table has an MV that is
         // read as details and aggregated by the mirror in memory
         DialectTable mvTable = (DialectTable) orders.clone();
         mvTable.setRuntimeMV(new RuntimeMV(wsEntry, null, null, "ORDERS", "MV1", false, 0L,
                                            null));
         setField(query, "query", MVAssetQuery.createQuery(mvTable, user, new VariableTable(),
                                                          true, AssetQuerySandbox.RUNTIME_MODE));
         assertInstanceOf(MVAssetQuery.class, subQuery(query));
      }

      // the mirror query's own table carries the grouping, sort and max rows (the clone made
      // by the query does not keep the aggregate set on the assembly above)
      TableAssembly qtable = (TableAssembly) invoke(query, "getTable");
      qtable.setAggregateInfo(ainfo);
      qtable.setSortInfo(sinfo);
      qtable.setMaxRows(maxRows);

      DefaultTableLens base = new DefaultTableLens(7, 2);
      base.setObject(0, 0, "DISCOUNT");
      base.setObject(0, 1, "Total");
      Object[] values = { 0.15, 0.05, null, 0.0, 0.1, 0.01 };

      for(int i = 0; i < values.length; i++) {
         base.setObject(i + 1, 0, values[i]);
         base.setObject(i + 1, 1, i + 1);
      }

      VariableTable vars = new VariableTable();
      query.merge(vars);
      TableLens result = (TableLens) invoke(query, "getSortTableLens", base, vars);
      result = (TableLens) invoke(query, "getSortSummaryTableLens", result, vars);
      result = (TableLens) invoke(query, "getMaxRowsTableLens", result, vars);
      result.moreRows(Integer.MAX_VALUE);
      List<String> rows = new ArrayList<>();

      for(int r = 1; r < result.getRowCount(); r++) {
         rows.add(String.valueOf(result.getObject(r, 0)));
      }

      return rows;
   }

   private static AssetQuery subQuery(AssetQuery query) throws Exception {
      Field field = MirrorQuery.class.getDeclaredField("query");
      field.setAccessible(true);
      return (AssetQuery) field.get(query);
   }

   private static void setField(Object obj, String name, Object value) throws Exception {
      Field field = MirrorQuery.class.getDeclaredField(name);
      field.setAccessible(true);
      field.set(obj, value);
   }

   private static ColumnRef column(String entity, String name, String type) {
      ColumnRef col = new ColumnRef(new AttributeRef(entity, name));
      col.setDataType(type);
      return col;
   }

   private static Object invoke(Object obj, String name, Object... args) throws Exception {
      for(Class<?> c = obj.getClass(); c != null; c = c.getSuperclass()) {
         for(Method m : c.getDeclaredMethods()) {
            if(m.getName().equals(name) && m.getParameterCount() == args.length) {
               m.setAccessible(true);

               try {
                  return m.invoke(obj, args);
               }
               catch(InvocationTargetException e) {
                  if(e.getCause() instanceof Exception cause) {
                     throw cause;
                  }

                  throw e;
               }
            }
         }
      }

      throw new NoSuchMethodException(name);
   }

   /** A physical table whose SQL helper (the source dialect) is set by the test. */
   public static class DialectTable extends PhysicalBoundTableAssembly {
      public DialectTable(Worksheet ws, String name) {
         super(ws, name);
      }

      @Override
      public SQLHelper getSQLHelper() {
         return helper;
      }

      static SQLHelper helper;
   }

   private XPrincipal user;
   private AssetEntry wsEntry;
   private String mvEnabled;
   private MVManager mgr;
   private MockedStatic<MVManager> mvManager;
}
