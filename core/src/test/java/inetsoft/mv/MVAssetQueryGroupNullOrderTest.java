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
package inetsoft.mv;

import inetsoft.report.TableLens;
import inetsoft.report.composition.execution.AssetQuery;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.Organization;
import inetsoft.test.*;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.VariableTable;
import inetsoft.uql.XConstants;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.jdbc.SQLHelper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression test for Redmine bug #80446. A grouped worksheet table with a max row count shows
 * different groups with and without a materialized view when the group column has NULLs: the MV
 * path sorts the groups in memory with NULL lowest and then truncates, while a database that
 * sorts NULL high (DB2, Oracle, PostgreSQL) returns the NULL group last. The MV group sort must
 * follow the source dialect, only for a grouped table with max rows.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, PluginsTestConfiguration.class,
                                SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class MVAssetQueryGroupNullOrderTest {
   @BeforeEach
   void setUp() {
      user = new XPrincipal(new IdentityID("u1", Organization.getDefaultOrganizationID()));
   }

   @AfterEach
   void tearDown() {
      DialectTable.helper = null;
   }

   @Test
   void nullsHighDialectKeepsNonNullGroupsAndDropsNullGroup() throws Exception {
      DialectTable.helper = SQLHelper.getSQLHelper("db2");

      assertEquals(List.of("0.0", "0.01", "0.05", "0.1", "0.15"),
                   run(true, 5, XConstants.SORT_ASC));
   }

   @Test
   void nullsHighDialectDescendingPutsNullGroupFirst() throws Exception {
      DialectTable.helper = SQLHelper.getSQLHelper("oracle");

      assertEquals(List.of("null", "0.15", "0.1", "0.05", "0.01"),
                   run(true, 5, XConstants.SORT_DESC));
   }

   @Test
   void nullsLowDialectIsUnchanged() throws Exception {
      DialectTable.helper = SQLHelper.getSQLHelper("mysql");

      assertEquals(List.of("null", "0.0", "0.01", "0.05", "0.1"),
                   run(true, 5, XConstants.SORT_ASC));
   }

   @Test
   void unknownSourceIsUnchanged() throws Exception {
      DialectTable.helper = null;

      assertEquals(List.of("null", "0.0", "0.01", "0.05", "0.1"),
                   run(true, 5, XConstants.SORT_ASC));
   }

   @Test
   void noMaxRowsIsUnchanged() throws Exception {
      DialectTable.helper = SQLHelper.getSQLHelper("db2");

      assertEquals(List.of("null", "0.0", "0.01", "0.05", "0.1", "0.15"),
                   run(true, 0, XConstants.SORT_ASC));
   }

   /**
    * Applies the MV post sorts and the max rows filter, in the order of
    * {@code MVAssetQuery.getTableLens()}, to a table of six groups (one of them NULL) in
    * scrambled order, and returns the first column of the result.
    */
   private List<String> run(boolean grouped, int maxRows, int order) throws Exception {
      Worksheet ws = new Worksheet();
      DialectTable table = new DialectTable(ws, "T1");
      table.setSourceInfo(new SourceInfo(SourceInfo.PHYSICAL_TABLE, "ds1", "T"));
      ColumnSelection sel = new ColumnSelection();
      ColumnRef discount = new ColumnRef(new AttributeRef(null, "DISCOUNT"));
      ColumnRef id = new ColumnRef(new AttributeRef(null, "ORDER_ID"));
      sel.addAttribute(discount);
      sel.addAttribute(id);
      table.setColumnSelection(sel);

      if(grouped) {
         AggregateInfo ainfo = new AggregateInfo();
         ainfo.addGroup(new GroupRef(discount));
         ainfo.addAggregate(new AggregateRef(id, AggregateFormula.COUNT_ALL));
         table.setAggregateInfo(ainfo);
      }

      SortInfo sinfo = new SortInfo();
      SortRef sort = new SortRef(discount);
      sort.setOrder(order);
      sinfo.addSort(sort);
      table.setSortInfo(sinfo);
      table.setMaxRows(maxRows);
      ws.addAssembly(table);

      AssetEntry wsEntry = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
                                          AssetEntry.Type.WORKSHEET, "ws1", null);
      table.setRuntimeMV(new RuntimeMV(wsEntry, null, null, "T1", "MV1", false, 0L, null));
      AssetQuery query = MVAssetQuery.createQuery(table, user, new VariableTable(), false,
                                                  AssetQuerySandbox.RUNTIME_MODE);
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
      List<String> rows = new ArrayList<>();

      result.moreRows(Integer.MAX_VALUE);

      for(int r = 1; r < result.getRowCount(); r++) {
         rows.add(String.valueOf(result.getObject(r, 0)));
      }

      return rows;
   }

   private static Object invoke(Object obj, String name, Object... args)
      throws Exception
   {
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
}
