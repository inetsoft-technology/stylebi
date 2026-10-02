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
import inetsoft.uql.ConditionItem;
import inetsoft.uql.ConditionList;
import inetsoft.uql.VariableTable;
import inetsoft.uql.XCondition;
import inetsoft.uql.asset.AssetCondition;
import inetsoft.uql.asset.EmbeddedTableAssembly;
import inetsoft.uql.asset.ExpressionValue;
import inetsoft.uql.asset.Worksheet;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.util.script.graal.pool.PoolTestSupport;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Constructor;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;

/**
 * Spec §6.5 (bug #76960): a post-condition script that references a worksheet table reads that
 * table in the query's own mode. On main the formula-column step had just set the query's mode
 * on the shared scope; in pool mode that scope is never written, so the script needs its own
 * query view.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, LibManagerTestConfiguration.class, PluginsTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class PostConditionQueryViewTest {
   @Test
   void poolModePostConditionReadsTablesInTheQueryMode() throws Exception {
      List<Integer> modes = runPostCondition(true, AssetQuerySandbox.RUNTIME_MODE, 0);
      assertTrue(modes.stream().allMatch(m -> m == AssetQuerySandbox.RUNTIME_MODE), modes::toString);
   }

   @Test
   void poolOffPostConditionUsesTheSharedScopeMode() throws Exception {
      // main: the script runs against the shared scope, whose mode the last writer set
      List<Integer> modes = runPostCondition(false, AssetQuerySandbox.RUNTIME_MODE,
                                             AssetQuerySandbox.LIVE_MODE);
      assertFalse(modes.isEmpty());
      assertTrue(modes.stream().allMatch(m -> m == AssetQuerySandbox.LIVE_MODE), modes::toString);
   }

   /**
    * Build an AssetConditionGroup2 whose condition value is a script reading table T1, and
    * return the modes T1 was fetched in.
    */
   private static List<Integer> runPostCondition(boolean pool, int queryMode, int sharedMode)
      throws Exception
   {
      AssetQuerySandbox box = PoolTestSupport.poolBox(pool);
      Worksheet ws = new Worksheet();
      ws.addAssembly(new EmbeddedTableAssembly(ws, "T1"));
      doReturn(ws).when(box).getWorksheet();
      doReturn(new VariableTable()).when(box).getVariableTable();
      List<Integer> modes = new CopyOnWriteArrayList<>();
      DefaultTableLens t1 = new DefaultTableLens(new Object[][] {{"a"}, {1}, {2}});
      doAnswer(inv -> {
         modes.add(inv.getArgument(1));
         return t1;
      }).when(box).getTableLens(eq("T1"), anyInt(), any());
      box.getScope().setMode(sharedMode);

      AssetCondition condition = new AssetCondition();
      condition.setOperation(XCondition.EQUAL_TO);
      condition.setType(XSchema.INTEGER);
      ExpressionValue value = new ExpressionValue();
      value.setExpression("T1.length");
      value.setType(ExpressionValue.JAVASCRIPT);
      condition.addValue(value);
      ConditionList list = new ConditionList();
      list.append(new ConditionItem(new AttributeRef(null, "value"), condition, 0));
      DefaultTableLens table = new DefaultTableLens(new Object[][] {{"value"}, {2}, {3}});

      Class<?> type = Class.forName(AssetQuery.class.getName() + "$AssetConditionGroup2");
      Constructor<?> ctor = type.getDeclaredConstructor(
         TableLens.class, ConditionList.class, int.class, AssetQuerySandbox.class,
         List.class, List.class, long.class, boolean.class);
      ctor.setAccessible(true);
      ctor.newInstance(table, list, queryMode, box, List.of(), List.of(), 0L, false);

      assertFalse(modes.isEmpty(), "the script did not read T1");
      return modes;
   }
}
