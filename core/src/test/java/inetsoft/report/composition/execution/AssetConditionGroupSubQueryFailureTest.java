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
import inetsoft.report.filter.ConditionGroup;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.util.CancelledException;
import inetsoft.util.MessageException;
import inetsoft.util.script.graal.pool.PoolTestSupport;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doReturn;

/**
 * Bug #77123 (Testing): when building a sub-query condition's sub table failed,
 * {@link AssetConditionGroup} and {@code AssetQuery.AssetConditionGroup2} logged a warning and
 * kept the item with column -1, which {@code XConditionGroup} evaluates as TRUE, so the query
 * silently returned unfiltered rows (and cached them). Both now fail the query instead, with
 * every RuntimeException propagated unchanged and checked exceptions wrapped.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, LibManagerTestConfiguration.class, PluginsTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class AssetConditionGroupSubQueryFailureTest {
   // ---- AssetConditionGroup ----

   @Test
   void healthySubQueryFilters() throws Exception {
      assertEquals(List.of(2), filterHealthy(false, "value"));
   }

   @Test
   void healthyCorrelatedSubQueryFilters() throws Exception {
      assertEquals(List.of(2), filterHealthy(true, "value"));
   }

   @Test
   void runtimeExceptionFailsTheQueryUnwrapped() throws Exception {
      MessageException error = new MessageException("Query timeout");
      RuntimeException ex = assertThrows(RuntimeException.class,
         () -> buildGroup(false, error, "value"));
      assertSame(error, ex);
   }

   @Test
   void cancelledExceptionFailsTheQueryUnwrapped() throws Exception {
      CancelledException error = new CancelledException("cancelled");
      RuntimeException ex = assertThrows(RuntimeException.class,
         () -> buildGroup(false, error, "value"));
      assertSame(error, ex);
   }

   @Test
   void checkedExceptionFailsTheQueryWrapped() throws Exception {
      SQLException error = new SQLException("boom");
      RuntimeException ex = assertThrows(RuntimeException.class,
         () -> buildGroup(false, error, "value"));
      assertSame(error, ex.getCause());
   }

   /**
    * A swallowed DB failure or cancel of a correlated sub query arrives as a null sub table,
    * which initSubTable reports as MessageException(invalidTableColumn).
    */
   @Test
   void correlatedNullSubTableFailsTheQuery() {
      assertThrows(MessageException.class, () -> buildGroup(true, null, "value"));
   }

   /** A main-table column that can't be found still ignores the condition (unchanged). */
   @Test
   void missingMainColumnStillIgnoresTheCondition() throws Exception {
      assertEquals(List.of(1, 2, 3), filterHealthy(false, "nosuch"));

      AssetCondition cond = new AssetCondition();
      cond.setOperation(XCondition.EQUAL_TO);
      cond.setType(XSchema.INTEGER);
      cond.addValue(2);
      ConditionList list = new ConditionList();
      list.append(new ConditionItem(new AttributeRef(null, "nosuch"), cond, 0));
      TableLens main = mainTable();
      AssetConditionGroup group = new AssetConditionGroup(
         main, list, AssetQuerySandbox.RUNTIME_MODE, box(), 0L);
      assertEquals(List.of(1, 2, 3), filter(main, group));
   }

   // ---- AssetConditionGroup2 (post-aggregate conditions) ----

   @Test
   void group2HealthySubQueryConstructs() throws Exception {
      assertNotNull(buildGroup2(subTable()));
   }

   @Test
   void group2RuntimeExceptionFailsTheQueryUnwrapped() {
      MessageException error = new MessageException("Query timeout");
      RuntimeException ex = assertThrows(RuntimeException.class, () -> buildGroup2(error));
      assertSame(error, ex);
   }

   @Test
   void group2CheckedExceptionFailsTheQueryWrapped() {
      SQLException error = new SQLException("boom");
      RuntimeException ex = assertThrows(RuntimeException.class, () -> buildGroup2(error));
      assertSame(error, ex.getCause());
   }

   // ---- helpers ----

   private static TableLens mainTable() {
      return new DefaultTableLens(new Object[][] {
         { "value", "key" }, { 1, "a" }, { 2, "a" }, { 3, "a" } });
   }

   private static TableLens subTable() {
      return new DefaultTableLens(new Object[][] { { "v", "k" }, { 2, "a" } });
   }

   private static AssetQuerySandbox box() throws Exception {
      AssetQuerySandbox box = PoolTestSupport.poolBox(false);
      doReturn(new VariableTable()).when(box).getVariableTable();
      return box;
   }

   private static ConditionList subQueryCondition(boolean correlated, String mainColumn) {
      Worksheet ws = new Worksheet();
      ws.addAssembly(new EmbeddedTableAssembly(ws, "S1"));
      SubQueryValue sub = new SubQueryValue();
      sub.setQuery("S1");
      sub.setAttribute(new AttributeRef(null, "v"));

      if(correlated) {
         sub.setMainAttribute(new AttributeRef(null, "key"));
         sub.setSubAttribute(new AttributeRef(null, "k"));
      }

      assertTrue(sub.update(ws));
      AssetCondition cond = new AssetCondition();
      cond.setOperation(XCondition.ONE_OF);
      cond.setType(XSchema.INTEGER);
      cond.addValue(sub);
      ConditionList list = new ConditionList();
      list.append(new ConditionItem(new AttributeRef(null, mainColumn), cond, 0));
      return list;
   }

   /**
    * @param result the sub table the sub query returns, or the exception it throws.
    */
   private static AssetQuery subQuery(Object result) throws Exception {
      AssetQuery query = Mockito.mock(AssetQuery.class);

      if(result instanceof Exception) {
         Mockito.when(query.getTableLens(any())).thenThrow((Exception) result);
      }
      else {
         Mockito.when(query.getTableLens(any())).thenReturn((TableLens) result);
      }

      return query;
   }

   private static AssetConditionGroup buildGroup(boolean correlated, Object result,
                                                 String mainColumn)
      throws Exception
   {
      return buildGroup(mainTable(), correlated, result, mainColumn);
   }

   private static AssetConditionGroup buildGroup(TableLens main, boolean correlated,
                                                 Object result, String mainColumn)
      throws Exception
   {
      AssetQuery query = subQuery(result);
      AssetQuerySandbox box = box();

      try(MockedStatic<AssetQuery> mocked =
             Mockito.mockStatic(AssetQuery.class, Mockito.CALLS_REAL_METHODS))
      {
         mocked.when(() -> AssetQuery.createAssetQuery(any(), anyInt(), any(), anyBoolean(),
                                                       anyLong(), anyBoolean(), anyBoolean()))
            .thenReturn(query);
         return new AssetConditionGroup(main, subQueryCondition(correlated, mainColumn),
                                        AssetQuerySandbox.RUNTIME_MODE, box, 0L);
      }
   }

   private static ConditionGroup buildGroup2(Object result) throws Exception {
      AssetQuery query = subQuery(result);
      AssetQuerySandbox box = box();
      Class<?> type = Class.forName(AssetQuery.class.getName() + "$AssetConditionGroup2");
      Constructor<?> ctor = type.getDeclaredConstructor(
         TableLens.class, ConditionList.class, int.class, AssetQuerySandbox.class,
         List.class, List.class, long.class, boolean.class);
      ctor.setAccessible(true);

      try(MockedStatic<AssetQuery> mocked =
             Mockito.mockStatic(AssetQuery.class, Mockito.CALLS_REAL_METHODS))
      {
         mocked.when(() -> AssetQuery.createAssetQuery(any(), anyInt(), any(), anyBoolean(),
                                                       anyLong(), anyBoolean(), anyBoolean()))
            .thenReturn(query);
         return (ConditionGroup) ctor.newInstance(
            mainTable(), subQueryCondition(false, "value"), AssetQuerySandbox.RUNTIME_MODE,
            box, List.of(0), List.of(), 0L, false);
      }
      catch(InvocationTargetException ex) {
         if(ex.getCause() instanceof Exception) {
            throw (Exception) ex.getCause();
         }

         throw ex;
      }
   }

   private static List<Integer> filterHealthy(boolean correlated, String mainColumn)
      throws Exception
   {
      TableLens main = mainTable();
      return filter(main, buildGroup(main, correlated, subTable(), mainColumn));
   }

   private static List<Integer> filter(TableLens main, ConditionGroup group) throws Exception {
      TableLens filtered = PostProcessor.filter(main, group, box());
      List<Integer> rows = new ArrayList<>();

      for(int r = filtered.getHeaderRowCount(); filtered.moreRows(r); r++) {
         rows.add((Integer) filtered.getObject(r, 0));
      }

      return rows;
   }
}
