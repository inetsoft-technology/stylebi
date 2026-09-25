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
import inetsoft.report.lens.FormulaTableLens;
import inetsoft.test.*;
import inetsoft.uql.Condition;
import inetsoft.uql.schema.XSchema;
import inetsoft.util.script.JavaScriptEngine;
import inetsoft.util.script.ScriptEnv;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Spec gate 17 (N12), bug #76960: with the context pool off, ConditionFilter2 still takes the
 * execution lock of the env it captured while it populates its rows over a formula lens, as on
 * main (#5506, #5536, #5548). The probe base table records whether its reader held a script lock.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, LibManagerTestConfiguration.class, PluginsTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class PoolOffConditionFilterLockingTest {
   @Test
   public void poolOffConditionFilterTakesTheCapturedEnvLock() throws Exception {
      AssetQuerySandbox box = Mockito.mock(AssetQuerySandbox.class, Mockito.CALLS_REAL_METHODS);
      Field lock = AssetQuerySandbox.class.getDeclaredField("lock");
      lock.setAccessible(true);
      lock.set(box, new Object());

      ScriptEnv env = box.getScriptEnv();
      env.compile("1");
      ProbeTable probe = new ProbeTable(ROWS);
      TableLens formula = new FormulaTableLens(probe, new String[] {"f"}, new String[] {"1"},
                                               env, null);
      TableLens filter = PostProcessor.filter(formula, allRows(), box);

      assertFalse(filter.moreRows(Integer.MAX_VALUE));
      assertEquals(ROWS + 1, filter.getRowCount());
      assertTrue(probe.sawScriptLock, "pool-off CF2 must populate under the env's lock");
   }

   static ConditionGroup allRows() {
      Condition condition = new Condition();
      condition.setOperation(Condition.GREATER_THAN);
      condition.addValue(-1);
      condition.setType(XSchema.INTEGER);
      ConditionGroup group = new ConditionGroup();
      group.addCondition(1, condition, 0);
      return group;
   }

   /**
    * Rows {@code (k<i%6>, i%30, i)}; remembers whether any data-row moreRows ran while the
    * reading thread held a script lock.
    */
   static final class ProbeTable extends DefaultTableLens {
      ProbeTable(int rows) {
         super(data(rows));
      }

      @Override
      public boolean moreRows(int row) {
         if(row > 0 && JavaScriptEngine.holdsScriptLock()) {
            sawScriptLock = true;
         }

         return super.moreRows(row);
      }

      private static Object[][] data(int rows) {
         Object[][] data = new Object[rows + 1][];
         data[0] = new Object[] {"group", "value", "id"};

         for(int i = 1; i <= rows; i++) {
            data[i] = new Object[] {"k" + (i % 6), i % 30, i};
         }

         return data;
      }

      volatile boolean sawScriptLock;
   }

   static final int ROWS = 300;
}
