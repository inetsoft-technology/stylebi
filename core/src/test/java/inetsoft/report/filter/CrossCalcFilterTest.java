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

import inetsoft.report.composition.graph.calc.PercentCalc;
import inetsoft.test.*;
import inetsoft.uql.XTable;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.VSAggregateRef;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@SreeHome
public class CrossCalcFilterTest {
   @Test
   public void testSerialize() throws Exception {
      int[] rowh = new int[]{ 0 };
      int[] colh = new int[]{ 1 };
      int[] dcol = new int[]{ 1, 2 };
      Formula[] formulas = new Formula[]{ new SumFormula(), new AverageFormula() };
      CrossTabFilter crossTabFilter = new CrossTabFilter(XTableUtil.getDefaultTableLens(), rowh, colh,
                                                         dcol, formulas);
      CrossCalcFilter originalTable = new CrossCalcFilter(crossTabFilter, new DataRef[0]);
      XTable deserializedTable = TestSerializeUtils.serializeAndDeserialize(originalTable);
      Assertions.assertEquals(CrossCalcFilter.class, deserializedTable.getClass());
   }

   @Test
   public void testSerializePercentCalc() throws Exception {
      int[] rowh = new int[]{ 0 };
      int[] colh = new int[]{ 1 };
      int[] dcol = new int[]{ 1, 2 };
      Formula[] formulas = new Formula[]{ new SumFormula(), new AverageFormula() };
      CrossTabFilter crossTabFilter = new CrossTabFilter(XTableUtil.getDefaultTableLens(), rowh, colh,
                                                         dcol, formulas);

      PercentCalc cal = new PercentCalc();
      cal.setLevel(PercentCalc.GRAND_TOTAL);
      cal.setAlias("Percent of Grand Total");
      VSAggregateRef ref = new VSAggregateRef();
      ref.setDataRef(new AttributeRef("total"));
      ref.setCalculator(cal);
      CrossCalcFilter originalTable = new CrossCalcFilter(crossTabFilter, new DataRef[]{ ref });
      XTable deserializedTable = TestSerializeUtils.serializeAndDeserialize(originalTable);
      Assertions.assertEquals(CrossCalcFilter.class, deserializedTable.getClass());
   }
}
