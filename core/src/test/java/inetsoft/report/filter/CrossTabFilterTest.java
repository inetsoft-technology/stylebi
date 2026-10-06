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

import inetsoft.report.TableLens;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.test.*;
import inetsoft.uql.XTable;
import inetsoft.uql.asset.Worksheet;
import inetsoft.util.swap.SwapFileReadException;
import inetsoft.util.swap.SwapLostTestSupport.LostTable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Tag;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static inetsoft.util.swap.SwapLostTestSupport.*;

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
    * A lost swap file of the base fails the read of the crosstab with the swap file read
    * failure, not with a NullPointerException of the missing data (bug #77651). Nothing is
    * kept, so the next read fails the same way while the file is lost.
    */
   @Test
   public void lostSwapFileOfTheBaseFailsEveryRead() throws Exception {
      SwapFileReadException lost = swapLost();
      CrossTabFilter crosstab = crosstab(new LostTable(values(40), 21, lost));

      Assertions.assertSame(lost, swapIn(failureOf(15, crosstab::getRowCount)), "first read");
      Assertions.assertSame(lost, swapIn(failureOf(15, crosstab::getRowCount)), "second read");
   }

   /**
    * A wrapped lost swap file is found in the cause chain.
    */
   @Test
   public void wrappedLostSwapFileOfTheBaseFailsTheRead() throws Exception {
      SwapFileReadException lost = swapLost();
      CrossTabFilter crosstab =
         crosstab(new LostTable(values(40), 21, new RuntimeException("wrapped", lost)));

      Assertions.assertSame(lost, swapIn(failureOf(15, crosstab::getRowCount)));
   }

   private static CrossTabFilter crosstab(TableLens base) {
      return new CrossTabFilter(base, new int[] { 0 }, new int[0], new int[] { 1 },
                                new Formula[] { new SumFormula() });
   }

   /**
    * {@code rows} rows of {@code key, value}, value {@code i} for row {@code i}.
    */
   private static Object[][] values(int rows) {
      Object[][] data = new Object[rows + 1][];
      data[0] = new Object[] { "key", "value" };

      for(int r = 1; r <= rows; r++) {
         data[r] = new Object[] { "k" + r, r };
      }

      return data;
   }
}
