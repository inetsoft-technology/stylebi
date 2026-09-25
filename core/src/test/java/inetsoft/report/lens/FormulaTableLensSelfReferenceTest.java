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
package inetsoft.report.lens;

import inetsoft.report.TableLens;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.report.composition.execution.PostProcessor;
import inetsoft.test.*;
import inetsoft.util.script.ScriptEnv;
import inetsoft.util.script.graal.pool.PoolTestSupport;
import inetsoft.util.script.graal.pool.WorksheetScriptEnv;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A formula whose script reads and then assigns a variable named like its own column: the
 * variable is the row's own cell, so the assignment sets the formula's result for that row and
 * must not re-enter the row that is being computed.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, LibManagerTestConfiguration.class, PluginsTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class FormulaTableLensSelfReferenceTest {
   @ParameterizedTest
   @ValueSource(booleans = { false, true })
   void readThenAssignOfTheOwnColumnSetsTheRowResult(boolean pool) throws Exception {
      // the worksheet path: the sandbox's env and scope, through PostProcessor.formula
      AssetQuerySandbox box = PoolTestSupport.poolBox(pool);
      ScriptEnv env = box.getScriptEnv();

      try {
         Object[][] data = new Object[ROWS + 1][];
         data[0] = new Object[] { "col0" };

         for(int i = 1; i <= ROWS; i++) {
            data[i] = new Object[] { "r" + i };
         }

         TableLens lens = PostProcessor.formula(
            new DefaultTableLens(data), new String[] { "acc" },
            new String[] { "var acc = (acc || 0) + 1; acc" }, env, box.getScope(), null, "T",
            null, List.of(Integer.class), new boolean[] { false });

         assertFalse(lens.moreRows(Integer.MAX_VALUE));
         assertEquals(ROWS + 1, lens.getRowCount(), "the rows of the base, once each");

         for(int r = 1; r <= ROWS; r++) {
            assertEquals(1, ((Number) lens.getObject(r, 1)).intValue(), "row " + r);
         }
      }
      finally {
         if(env instanceof WorksheetScriptEnv) {
            ((WorksheetScriptEnv) env).retire();
         }
      }
   }

   private static final int ROWS = 300;
}
