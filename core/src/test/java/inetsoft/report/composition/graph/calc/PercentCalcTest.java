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

import inetsoft.uql.viewsheet.VSDimensionRef;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PercentCalcTest {
   private  PercentCalc percentCalc;

   @Test
   void testCreateCalcColumn() {
      percentCalc = new PercentCalc();
      percentCalc.setLevel(PercentCalc.GRAND_TOTAL);
      percentCalc.setColumnName("date");
      percentCalc.setPercentageByValue("columns");

      PercentColumn percentColumn =(PercentColumn)percentCalc.createCalcColumn("id");

      assertEquals("% of date by column: id", percentColumn.getHeader());

      assertTrue(percentCalc.supportSortByValue());
      assertNull(percentCalc.getLabel());
      assertTrue(percentCalc.isPercent());
      assertEquals("Percent of date by column",  percentCalc.getPrefixView0());
      assertEquals("Percent of date", percentCalc.toView0());
      assertEquals(2, percentCalc.getDynamicValues().size());
   }

   @Test
   void testUpdateRef() {
      percentCalc = new PercentCalc();
      percentCalc.setLevel(PercentCalc.SUB_TOTAL);
      // didn't set column,
      assertEquals("Percent of Subtotal", percentCalc.toView0());
      assertEquals("Percent of subtotal", percentCalc.getPrefixView0());

      percentCalc.setColumnName("date");
      VSDimensionRef mockDRef1 = mock(VSDimensionRef.class);
      when(mockDRef1.getFullName()).thenReturn("date");
      VSDimensionRef mockDRef2 = mock(VSDimensionRef.class);
      when(mockDRef2.getFullName()).thenReturn("ndate");

      percentCalc.updateRefs(List.of(mockDRef1), List.of(mockDRef2));
      assertEquals("ndate", percentCalc.getColumnNameValue());
      assertEquals("Percent of ndate", percentCalc.toView0());

      Object percentCalc2 = percentCalc.clone();
      ((PercentCalc)percentCalc2).setLevel(PercentCalc.GRAND_TOTAL);
       assertNotEquals(percentCalc, percentCalc2);
   }
}
