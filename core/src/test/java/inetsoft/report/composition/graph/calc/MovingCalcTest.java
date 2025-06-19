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
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class MovingCalcTest {
   private MovingCalc movingCalc;

   @Test
   void testCreateCalcColumn() {
      movingCalc = new MovingCalc();
      movingCalc.setAggregate(XConstants.AVERAGE_FORMULA);
      movingCalc.setInnerDim("date");
      movingCalc.setPrevious(2);
      movingCalc.setNext(1);
      movingCalc.setIncludeCurrentValue(true);
      movingCalc.setNullIfNoEnoughValue(true);

      MovingColumn movingColumn = (MovingColumn) movingCalc.createCalcColumn("id");

      assertEquals("Moving Average of 4 date: id", movingColumn.getHeader());
      assertTrue(movingCalc.isIncludeCurrentValue());
      assertTrue(movingCalc.isNullIfNoEnoughValue());
      assertFalse(movingCalc.isPercent());

      assertEquals(XConstants.AVERAGE_FORMULA, movingCalc.getAggregate());
      assertEquals(ValueOfCalc.MOVING, movingCalc.getType());
      assertEquals(2, movingCalc.getPrevious());
      assertEquals(1, movingCalc.getNext());
      assertEquals("Moving Average of 4 date", movingCalc.getPrefixView0());
      assertEquals("Moving(2,1,Average,true,true)", movingCalc.getName0());

      assertFalse(movingCalc.equals(null));
      Object movingCalc2 = movingCalc.clone();
      assertEquals(movingCalc, movingCalc2);
   }
}
