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

package inetsoft.report.composition.graph.calc;

import inetsoft.uql.XConstants;
import inetsoft.uql.viewsheet.graph.AbstractCalc;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

public class CompoundGrowthCalcTest {
   CompoundGrowthCalc compoundGrowthCalc;

   @Test
   void  testCreateCalcColumn() {
      compoundGrowthCalc = new CompoundGrowthCalc();
      compoundGrowthCalc.setAggregate(XConstants.DISTINCTCOUNT_FORMULA);
      compoundGrowthCalc.setResetLevel(RunningTotalColumn.MONTH);
      compoundGrowthCalc.setBreakBy(AbstractCalc.COLUMN_INNER);

      CompoundGrowthColumn  compoundGrowthColumn =
         (CompoundGrowthColumn )compoundGrowthCalc.createCalcColumn("id");

      assertEquals("Compound Growth of month by column: id", compoundGrowthColumn.getHeader());
      assertEquals("Compound Growth of month by column", compoundGrowthCalc.getPrefixView0());
      assertEquals(compoundGrowthCalc.COMPOUNDGROWTH, compoundGrowthCalc.getType());
      assertTrue(compoundGrowthCalc.isPercent());
      assertEquals("CompoundGrowth(2)", compoundGrowthCalc.getName0());

      Object compoundGrowthCalc2 = compoundGrowthCalc.clone();
      assertTrue(compoundGrowthCalc.equals(compoundGrowthCalc2));
      assertFalse(compoundGrowthCalc.equals(mock(PercentCalc.class)));
   }
}
