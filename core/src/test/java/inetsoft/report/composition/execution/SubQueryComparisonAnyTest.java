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
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.util.script.graal.pool.PoolTestSupport;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doReturn;

/**
 * Bug #82411: a comparison (&lt;, &lt;=, &gt;, &gt;=) with a sub query compared the row with the
 * first row of the sub query only. It uses ANY semantics now, as the pushed-down
 * {@code op any (select ...)}: true if any non-null sub query value satisfies it; unknown (the
 * row is dropped, negated or not) if none does and a value is null; false for an empty sub query,
 * which a negation turns into true.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, LibManagerTestConfiguration.class, PluginsTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class SubQueryComparisonAnyTest {
   private static final Double[] ROWS = { 0.0, 0.07, 0.2 };
   private static final int GT = XCondition.GREATER_THAN;
   private static final int LT = XCondition.LESS_THAN;

   @Test
   void greaterThanIsIndependentOfTheSubQueryOrder() throws Exception {
      assertEquals(List.of(0.07, 0.2), filter(GT, false, false, 0.10, 0.0));
      assertEquals(List.of(0.07, 0.2), filter(GT, false, false, 0.0, 0.10));
   }

   @Test
   void lessThanIsIndependentOfTheSubQueryOrder() throws Exception {
      assertEquals(List.of(0.0, 0.07), filter(LT, false, false, 0.10, 0.0));
      assertEquals(List.of(0.0, 0.07), filter(LT, false, false, 0.0, 0.10));
   }

   @Test
   void orEqualOperators() throws Exception {
      assertEquals(List.of(0.07, 0.2), filter(GT, true, false, 0.2, 0.07));
      assertEquals(List.of(0.0, 0.07), filter(LT, true, false, 0.0, 0.07));
      assertEquals(List.of(0.2), filter(GT, true, false, 0.2));
   }

   @Test
   void negatedComparison() throws Exception {
      assertEquals(List.of(0.0), filter(GT, false, true, 0.10, 0.0));
      assertEquals(List.of(0.2), filter(LT, false, true, 0.10, 0.0));
      assertEquals(List.of(0.0), filter(GT, true, true, 0.2, 0.07));
   }

   @Test
   void emptySubQueryIsFalseAndNegatedTrue() throws Exception {
      assertEquals(List.of(), filter(GT, false, false));
      assertEquals(List.of(), filter(LT, true, false));
      assertEquals(List.of(0.0, 0.07, 0.2), filter(GT, false, true));
      assertEquals(List.of(0.0, 0.07, 0.2), filter(LT, true, true));
   }

   @Test
   void nullValueWithoutMatchIsUnknownAndNotNegated() throws Exception {
      assertEquals(List.of(), filter(GT, false, false, null, 0.5));
      assertEquals(List.of(), filter(GT, false, true, null, 0.5));
      assertEquals(List.of(), filter(GT, false, false, (Double) null));
      assertEquals(List.of(), filter(GT, false, true, (Double) null));
   }

   @Test
   void nullValueWithAMatchIsTrue() throws Exception {
      assertEquals(List.of(0.07, 0.2), filter(GT, false, false, null, 0.05));
      // a row without a match stays unknown, a matching row is negated to false
      assertEquals(List.of(), filter(GT, false, true, null, 0.05));
      assertEquals(List.of(0.0, 0.07), filter(LT, false, false, 0.1, null));
   }

   @Test
   void singleValueIsUnchanged() throws Exception {
      assertEquals(List.of(0.07, 0.2), filter(GT, false, false, 0.05));
      assertEquals(List.of(0.0), filter(GT, false, true, 0.05));
      assertEquals(List.of(0.0), filter(LT, false, false, 0.05));
      assertEquals(List.of(0.07, 0.2), filter(LT, false, true, 0.05));
      assertEquals(List.of(0.07, 0.2), filter(GT, true, false, 0.07));
   }

   // the extremes kept between the rows follow the values of the sub query when they change
   @Test
   void extremesFollowTheSubQueryValues() throws Exception {
      Worksheet ws = new Worksheet();
      ws.addAssembly(new EmbeddedTableAssembly(ws, "S1"));
      SubQueryValue value = new SubQueryValue();
      value.setQuery("S1");
      value.setAttribute(new AttributeRef(null, "v"));
      assertTrue(value.update(ws));
      AssetCondition cond = new AssetCondition();
      cond.setOperation(GT);
      cond.setType(XSchema.DOUBLE);
      cond.addValue(value);
      TableLens main = new DefaultTableLens(new Object[][] { { "value" }, { 0.07 } });
      cond.initMainTable(main);

      cond.initSubTable(new DefaultTableLens(new Object[][] { { "v" }, { 0.5 }, { 0.1 } }));
      assertFalse(cond.evaluate(0.07));
      assertTrue(cond.evaluate(0.2));

      // new values of the sub query (re-executed or new variables)
      cond.reset();
      cond.initSubTable(new DefaultTableLens(new Object[][] { { "v" }, { 0.5 }, { 0.01 } }));
      assertTrue(cond.evaluate(0.07));
      assertFalse(cond.evaluate(0.005));

      cond.reset();
      cond.initSubTable(new DefaultTableLens(new Object[][] { { "v" } }));
      assertFalse(cond.evaluate(0.07));
   }

   @Test
   void mixedTypesAndDatesKeepTheComparison() throws Exception {
      Worksheet ws = new Worksheet();
      ws.addAssembly(new EmbeddedTableAssembly(ws, "S1"));
      SubQueryValue value = new SubQueryValue();
      value.setQuery("S1");
      value.setAttribute(new AttributeRef(null, "v"));
      assertTrue(value.update(ws));
      AssetCondition cond = new AssetCondition();
      cond.setOperation(LT);
      cond.setType(XSchema.INTEGER);
      cond.addValue(value);
      cond.initMainTable(new DefaultTableLens(new Object[][] { { "value" }, { 1 } }));
      cond.initSubTable(new DefaultTableLens(new Object[][] { { "v" }, { 3L }, { 9L } }));

      assertTrue(cond.evaluate(8));
      assertFalse(cond.evaluate(9));
   }

   // the extremes are only used when the values and the row are of one kind: the strings "9"
   // and "10" compare as text with each other, but as numbers with a number
   @Test
   void numericStringsAreComparedWithTheRowAsNumbers() throws Exception {
      AssetCondition cond = fixedCondition(LT, XSchema.DOUBLE, "9", "10");
      assertTrue(cond.evaluate(9.5));
      assertFalse(cond.evaluate(10.5));
      cond = fixedCondition(GT, XSchema.DOUBLE, "9", "10");
      assertTrue(cond.evaluate(9.5));
      assertFalse(cond.evaluate(8.5));
   }

   @Test
   void mixedNumberAndStringValuesFallBackToTheLoop() throws Exception {
      AssetCondition cond = fixedCondition(LT, XSchema.DOUBLE, "9", 10);
      assertTrue(cond.evaluate(9.5));
      assertFalse(cond.evaluate(10.5));
      cond = fixedCondition(GT, XSchema.DOUBLE, 10, "9");
      assertTrue(cond.evaluate(9.5));
      assertFalse(cond.evaluate(8.5));
   }

   private static AssetCondition fixedCondition(int operation, String type, Object... subValues)
      throws Exception
   {
      Worksheet ws = new Worksheet();
      ws.addAssembly(new EmbeddedTableAssembly(ws, "S1"));
      SubQueryValue value = new SubQueryValue();
      value.setQuery("S1");
      value.setAttribute(new AttributeRef(null, "v"));
      assertTrue(value.update(ws));
      AssetCondition cond = new AssetCondition();
      cond.setOperation(operation);
      cond.setType(type);
      cond.addValue(value);
      cond.initMainTable(new DefaultTableLens(new Object[][] { { "value" }, { 1.0 } }));
      Object[][] sub = new Object[subValues.length + 1][];
      sub[0] = new Object[] { "v" };

      for(int i = 0; i < subValues.length; i++) {
         sub[i + 1] = new Object[] { subValues[i] };
      }

      cond.initSubTable(new DefaultTableLens(sub));
      return cond;
   }

   // ---- helpers ----

   /** The values of the main table rows that pass the condition. */
   private static List<Double> filter(int operation, boolean equal, boolean negated,
                                      Double... subValues)
      throws Exception
   {
      Object[][] main = new Object[ROWS.length + 1][];
      main[0] = new Object[] { "value" };

      for(int i = 0; i < ROWS.length; i++) {
         main[i + 1] = new Object[] { ROWS[i] };
      }

      Object[][] sub = new Object[subValues.length + 1][];
      sub[0] = new Object[] { "v" };

      for(int i = 0; i < subValues.length; i++) {
         sub[i + 1] = new Object[] { subValues[i] };
      }

      TableLens mainTable = new DefaultTableLens(main);
      TableLens subTable = new DefaultTableLens(sub);
      Worksheet ws = new Worksheet();
      ws.addAssembly(new EmbeddedTableAssembly(ws, "S1"));
      SubQueryValue value = new SubQueryValue();
      value.setQuery("S1");
      value.setAttribute(new AttributeRef(null, "v"));
      assertTrue(value.update(ws));
      AssetCondition cond = new AssetCondition();
      cond.setOperation(operation);
      cond.setEqual(equal);
      cond.setNegated(negated);
      cond.setType(XSchema.DOUBLE);
      cond.addValue(value);
      ConditionList list = new ConditionList();
      list.append(new ConditionItem(new AttributeRef(null, "value"), cond, 0));

      AssetQuery query = Mockito.mock(AssetQuery.class);
      Mockito.when(query.getTableLens(any())).thenReturn(subTable);
      AssetQuerySandbox box = PoolTestSupport.poolBox(false);
      doReturn(new VariableTable()).when(box).getVariableTable();
      AssetConditionGroup group;

      try(MockedStatic<AssetQuery> mocked =
             Mockito.mockStatic(AssetQuery.class, Mockito.CALLS_REAL_METHODS))
      {
         mocked.when(() -> AssetQuery.createAssetQuery(any(), anyInt(), any(), anyBoolean(),
                                                       anyLong(), anyBoolean(), anyBoolean()))
            .thenReturn(query);
         group = new AssetConditionGroup(mainTable, list, AssetQuerySandbox.RUNTIME_MODE, box, 0L);
      }

      TableLens filtered = inetsoft.report.composition.execution.PostProcessor.filter(
         mainTable, group, box);
      List<Double> rows = new ArrayList<>();

      for(int r = filtered.getHeaderRowCount(); filtered.moreRows(r); r++) {
         rows.add((Double) filtered.getObject(r, 0));
      }

      return rows;
   }
}
