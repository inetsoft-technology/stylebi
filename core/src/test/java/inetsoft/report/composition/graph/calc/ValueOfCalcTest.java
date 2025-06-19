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

import inetsoft.uql.viewsheet.XDimensionRef;
import inetsoft.uql.viewsheet.internal.DatePeriod;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

public class ValueOfCalcTest {
   ValueOfCalc valueOfCalc;

   @Test
   void testCreateCalcColumn() {
      valueOfCalc = new ValueOfCalc();
      valueOfCalc.setColumnName("date");
      valueOfCalc.setFirstWeek(true);
      valueOfCalc.setFrom(ValueOfCalc.LAST);
      valueOfCalc.setAsPercent(true);

      DatePeriod mockDP1 = mock(DatePeriod.class);
      valueOfCalc.setDcPeriods(List.of(mockDP1));
      XDimensionRef mockXDRef = mock(XDimensionRef.class);
      valueOfCalc.setComparisonDateDims(List.of(mockXDRef));
      ValueOfColumn valueOfColumn = (ValueOfColumn)valueOfCalc.createCalcColumn("id");

      assertEquals("Value of last date: id", valueOfColumn.getHeader());

      assertFalse(valueOfCalc.supportSortByValue());
      valueOfCalc.setFrom(ValueOfCalc.PREVIOUS_MONTH);
      assertTrue(valueOfCalc.supportSortByValue());

      assertEquals("Value of previous month of date", valueOfCalc.getPrefixView0());
      assertEquals(ChangeCalc.VALUE, valueOfCalc.getType());
      assertEquals(1, valueOfCalc.getDynamicValues().size());
   }
}
