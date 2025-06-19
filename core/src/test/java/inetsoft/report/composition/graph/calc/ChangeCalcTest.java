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

public class ChangeCalcTest {
   ChangeCalc changeCalc;

   @Test
   void testCreateCalcColumn() {
      changeCalc = new ChangeCalc();
      changeCalc.setColumnName("name");
      changeCalc.setFirstWeek(true);
      changeCalc.setFrom(ValueOfCalc.NEXT);
      changeCalc.setAsPercent(true);
      ChangeColumn  changeColumn = (ChangeColumn )changeCalc.createCalcColumn("id");

      assertEquals("% Change from next name: id", changeColumn.getHeader());
      assertEquals(ChangeCalc.CHANGE, changeCalc.getType());
   }

   @Test
   void testUpdateRef() {
      changeCalc = new ChangeCalc();
      VSDimensionRef mockDRef1 = mock(VSDimensionRef.class);
      when(mockDRef1.getFullName()).thenReturn("oname");

      VSDimensionRef mockDRef2 = mock(VSDimensionRef.class);
      when(mockDRef2.getFullName()).thenReturn("nname");

      changeCalc.setColumnName("oname");
      changeCalc.updateRefs(List.of(mockDRef1), List.of(mockDRef2));

      assertEquals("nname", changeCalc.getColumnNameValue());

      //check other getPrefixView0, getPrefixView
      changeCalc.setFrom(ValueOfCalc.PREVIOUS_WEEK);
      assertEquals("Change from previous week of nname", changeCalc.getPrefixView0());

      changeCalc.setAsPercent(true);
      changeCalc.setFrom(ValueOfCalc.PREVIOUS_RANGE);
      assertEquals("% Change from previous range of nname: ", changeCalc.getPrefixView());
      assertTrue(changeCalc.isPercent());

      // check equal function
      assertFalse(changeCalc.equals(null));

      Object changeCalc1 = changeCalc.clone();
      ((ChangeCalc)changeCalc1).setColumnName("test");
       assertNotEquals(changeCalc, changeCalc1);
   }
}
