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
import inetsoft.uql.schema.XSchema;
import inetsoft.util.script.graal.pool.PoolTestSupport;
import inetsoft.web.wiz.pairing.TestWorksheets;
import inetsoft.web.wiz.worksheet.WorksheetMutationSupport;
import inetsoft.web.wiz.worksheet.model.WorksheetModel;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doReturn;

/**
 * Bug #79224: a {@code "subquery"} {@code ConditionValueSpec}, resolved through the real
 * {@code set_conditions} write path ({@link WorksheetMutationSupport#setConditions}) rather than a
 * hand-built {@link SubQueryValue}, must produce a condition that is not just correctly shaped but
 * actually QUERYABLE -- i.e. {@code sv.update(ws)} has run so {@link AssetConditionGroup}'s
 * constructor (which unconditionally dereferences {@code sub.getTable()}, see
 * {@code AssetConditionGroup.java:108-112}) does not NPE the first time the condition is evaluated.
 * Without the {@code sv.update(ws)} call this fix adds, this test reproduces the exact
 * {@code NullPointerException} the refute pass demonstrated for the diagnosis's original,
 * unrevised design. Mirrors {@link AssetConditionGroupSubQueryFailureTest}'s own Spring/mocking
 * setup (same package, same base config classes), but exercises the end-to-end write path instead
 * of constructing a {@link SubQueryValue} directly.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, LibManagerTestConfiguration.class, PluginsTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class WorksheetMutationSupportSubqueryEvaluationTest {

   @Test
   void uncorrelatedSubqueryValueSpecEvaluatesWithoutNpe() throws Exception {
      assertEquals(List.of(2), filterViaValueSpec(false));
   }

   @Test
   void correlatedSubqueryValueSpecEvaluatesWithoutNpe() throws Exception {
      assertEquals(List.of(2), filterViaValueSpec(true));
   }

   /**
    * Builds a two-table worksheet, writes a {@code "subquery"} {@code ConditionValueSpec} onto
    * the main table via the real {@link WorksheetMutationSupport#setConditions} entry point (the
    * same one {@code set_conditions} calls), confirms the resulting condition's sole value is a
    * {@link SubQueryValue} matching the expected shape, and evaluates it through a real
    * {@link AssetConditionGroup} + {@link PostProcessor#filter}.
    */
   private static List<Object> filterViaValueSpec(boolean correlated) throws Exception {
      Worksheet ws = new Worksheet();
      TableAssembly main = TestWorksheets.nonEmbeddedTableWithColumns(ws, "Main", "value", "key");
      TableAssembly sub = TestWorksheets.nonEmbeddedTableWithColumns(ws, "S1", "v", "k");
      ws.addAssembly(main);
      ws.addAssembly(sub);

      WorksheetMutationSupport.ConditionValueSpec spec = correlated
         ? new WorksheetMutationSupport.ConditionValueSpec(
              "subquery", null, null, null, "S1", "v",
              new WorksheetModel.SubQueryWhereModel("k", "key"), null)
         : new WorksheetMutationSupport.ConditionValueSpec(
              "subquery", null, null, null, "S1", "v", null, null);

      WorksheetMutationSupport.setConditions(main, List.of(
         new WorksheetMutationSupport.ConditionNode(
            new WorksheetMutationSupport.ConditionSpec(
               "value", "one_of", null, false, XSchema.INTEGER, List.of(spec)),
            null, 0)), false);

      Condition c = (Condition) ((ConditionItem) main.getPreConditionList().getConditionList()
         .getItem(0)).getXCondition();
      assertEquals(1, c.getValueCount());
      assertInstanceOf(SubQueryValue.class, c.getValue(0));
      SubQueryValue sv = (SubQueryValue) c.getValue(0);
      assertEquals("S1", sv.getQuery());
      assertEquals("v", sv.getAttribute().getName());

      if(correlated) {
         assertEquals("k", sv.getSubAttribute().getName());
         assertEquals("key", sv.getMainAttribute().getName());
      }
      else {
         assertNull(sv.getSubAttribute());
         assertNull(sv.getMainAttribute());
      }

      // The fix under test: sv.update(ws) must have already run inside conditionValue(), so
      // getTable() is non-null and AssetConditionGroup's constructor below does not NPE.
      assertNotNull(sv.getTable(), "SubQueryValue.getTable() must be populated by conditionValue()");

      TableLens mainLens = new DefaultTableLens(new Object[][] {
         { "value", "key" }, { 1, "a" }, { 2, "a" }, { 3, "a" } });
      TableLens subLens = new DefaultTableLens(new Object[][] { { "v", "k" }, { 2, "a" } });

      AssetQuery query = Mockito.mock(AssetQuery.class);
      Mockito.when(query.getTableLens(any())).thenReturn(subLens);

      AssetQuerySandbox box = PoolTestSupport.poolBox(false);
      doReturn(new VariableTable()).when(box).getVariableTable();

      AssetConditionGroup group;

      try(MockedStatic<AssetQuery> mocked =
             Mockito.mockStatic(AssetQuery.class, Mockito.CALLS_REAL_METHODS))
      {
         mocked.when(() -> AssetQuery.createAssetQuery(any(), anyInt(), any(), anyBoolean(),
                                                       anyLong(), anyBoolean(), anyBoolean()))
            .thenReturn(query);
         group = new AssetConditionGroup(
            mainLens, main.getPreConditionList().getConditionList(),
            AssetQuerySandbox.RUNTIME_MODE, box, 0L);
      }

      TableLens filtered = PostProcessor.filter(mainLens, group, box);
      List<Object> rows = new ArrayList<>();

      for(int r = filtered.getHeaderRowCount(); filtered.moreRows(r); r++) {
         rows.add(filtered.getObject(r, 0));
      }

      return rows;
   }
}
