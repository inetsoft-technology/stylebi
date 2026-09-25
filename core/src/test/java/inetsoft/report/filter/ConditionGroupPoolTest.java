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
package inetsoft.report.filter;

import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.test.*;
import inetsoft.uql.ConditionItem;
import inetsoft.uql.ConditionList;
import inetsoft.uql.XCondition;
import inetsoft.uql.asset.AssetCondition;
import inetsoft.uql.asset.ExpressionValue;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.sree.SreeEnv;
import inetsoft.util.script.graal.pool.PoolConfig;
import inetsoft.util.script.graal.pool.PoolMetrics;
import inetsoft.util.script.graal.pool.PoolTestSupport;
import inetsoft.util.script.graal.pool.WorksheetScriptEnv;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Spec §6.6 (bug #76960): in pool mode a script condition value is built without the unread
 * conditionGroupScope env global.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, LibManagerTestConfiguration.class, PluginsTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ConditionGroupPoolTest {
   @Test
   void poolModeWritesNoConditionGroupScope() throws Exception {
      AssetQuerySandbox box = PoolTestSupport.poolBox(true);
      WorksheetScriptEnv env = (WorksheetScriptEnv) box.getScriptEnv();
      long version = env.getStateVersion();

      AssetCondition condition = new AssetCondition();
      condition.setOperation(XCondition.EQUAL_TO);
      condition.setType(XSchema.INTEGER);
      ExpressionValue value = new ExpressionValue();
      value.setExpression("1 + 1");
      value.setType(ExpressionValue.JAVASCRIPT);
      condition.addValue(value);
      ConditionList list = new ConditionList();
      list.append(new ConditionItem(new AttributeRef(null, "value"), condition, 0));
      DefaultTableLens table = new DefaultTableLens(new Object[][] {{"value"}, {2}, {3}});

      new ConditionGroup(table, list, box);

      assertEquals(version, env.getStateVersion(), "conditionGroupScope was put into the env");
   }

   /**
    * Final review I3: a condition built without a sandbox runs its script on a throwaway
    * sandbox, which is never disposed; its pooled contexts are released at once, so they do
    * not count toward the node's slots until they are collected.
    */
   @Test
   void throwawaySandboxOfAConditionReleasesItsContexts() throws Exception {
      SreeEnv.setProperty(PoolConfig.ENABLED, "true");

      try {
         AssetCondition condition = new AssetCondition();
         condition.setOperation(XCondition.EQUAL_TO);
         condition.setType(XSchema.INTEGER);
         ExpressionValue value = new ExpressionValue();
         value.setExpression("1 + 1");
         value.setType(ExpressionValue.JAVASCRIPT);
         condition.addValue(value);
         ConditionList list = new ConditionList();
         list.append(new ConditionItem(new AttributeRef(null, "value"), condition, 0));
         DefaultTableLens table = new DefaultTableLens(new Object[][] {{"value"}, {2}, {3}});
         int before = PoolMetrics.nodeSlots();

         ConditionGroup group = new ConditionGroup(table, list, null);

         assertTrue(group.evaluate(table, 1));
         assertFalse(group.evaluate(table, 2));
         assertEquals(before, PoolMetrics.nodeSlots(), "the throwaway sandbox kept a context");
      }
      finally {
         SreeEnv.remove(PoolConfig.ENABLED);
      }
   }
}
