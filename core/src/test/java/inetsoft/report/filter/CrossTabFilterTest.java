/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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
import inetsoft.uql.XTable;
import inetsoft.uql.asset.Worksheet;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Tag;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, LibManagerTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class CrossTabFilterTest {
   @Test
   public void testSerialize() throws Exception {
      int[] rowh = new int[]{ 0 };
      int[] colh = new int[]{ 1 };
      int[] dcol = new int[]{ 1, 2 };
      Formula[] formulas = new Formula[]{ new SumFormula(), new AverageFormula() };
      CrossTabFilter originalTable = new CrossTabFilter(XTableUtil.getDefaultTableLens(), rowh, colh,
                                                        dcol, formulas);
      XTable deserializedTable = TestSerializeUtils.serializeAndDeserialize(originalTable);
      Assertions.assertEquals(CrossTabFilter.class, deserializedTable.getClass());
   }

   @Test
   public void testSerializeCalcFieldFormula() throws Exception {
      int[] rowh = new int[]{ 0 };
      int[] colh = new int[]{ 1 };
      int[] dcol = new int[]{ 1, 2 };
      String expr = "new Date().getTime()";
      Worksheet ws = new Worksheet();
      AssetQuerySandbox box = new AssetQuerySandbox(ws);
      CalcFieldFormula calcFieldFormula = new CalcFieldFormula(expr, new String[0], new Formula[0],
                                                               new int[0], box.getScriptEnv(),
                                                               box.getScope());
      Formula[] formulas = new Formula[]{ new SumFormula(), calcFieldFormula };
      CrossTabFilter originalTable = new CrossTabFilter(XTableUtil.getDefaultTableLens(), rowh, colh,
                                                        dcol, formulas);
      XTable deserializedTable = TestSerializeUtils.serializeAndDeserialize(originalTable);
      Assertions.assertEquals(CrossTabFilter.class, deserializedTable.getClass());
   }

   /**
    * A measure bound with formula "none" is valid only when the worksheet already
    * pre-aggregates to exactly one row per dimension value (e.g. a mirror table that
    * grouped by STATE and computed a DistinctCount alias). Applying a chart/crosstab-level
    * "top N + group others" ranking on that dimension folds the non-top-N rows into a
    * single merged "Others" cell, so the crosstab must re-total those already-aggregated
    * rows. Before the fix, the merged cell was recomputed by replaying a cloned NoneFormula
    * over every merged row — NoneFormula.addValue() overwrites rather than accumulates, so
    * the "Others" cell ended up holding whatever row was processed last (here, WA's 1)
    * instead of the sum of all merged rows (NY+TX+WA = 6).
    */
   @Test
   public void testTopNGroupOthersWithNoneFormulaSumsMergedRows() {
      Object[][] data = new Object[][]{
         { "STATE", "CUSTOMER_COUNT" },
         { "NJ", 6 },
         { "CA", 4 },
         { "MA", 4 },
         { "NY", 3 },
         { "TX", 2 },
         { "WA", 1 },
      };

      int[] rowh = new int[]{ 0 };
      int[] colh = new int[0];
      int[] dcol = new int[]{ 1 };
      Formula[] formulas = new Formula[]{ new NoneFormula() };

      CrossTabFilter table = new CrossTabFilter(new DefaultTableLens(data), rowh, colh, dcol,
                                                formulas);
      // top 3 states by CUSTOMER_COUNT, remaining states grouped into "Others".
      table.setRowTopN(0, 0, 3, false, true);

      Object othersTotal = null;

      for(int r = 0; r < table.getRowCount(); r++) {
         if("Others".equals(table.getObject(r, 0))) {
            othersTotal = table.getObject(r, 1);
         }
      }

      Assertions.assertNotNull(othersTotal, "Others row not found in crosstab output");
      Assertions.assertEquals(6.0, ((Number) othersTotal).doubleValue(), 0.0001,
                              "Others should sum the merged NY+TX+WA rows (3+2+1=6), " +
                              "not just the last-processed row's raw value");
   }
}
