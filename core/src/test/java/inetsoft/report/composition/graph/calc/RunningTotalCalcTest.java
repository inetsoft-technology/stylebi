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
import inetsoft.uql.viewsheet.VSDimensionRef;
import inetsoft.uql.viewsheet.graph.AbstractCalc;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class RunningTotalCalcTest {
   private  RunningTotalCalc runningTotalCalc;

   @Test
   void testCreateCalcColumn() {
      runningTotalCalc = new RunningTotalCalc();
      RunningTotalColumn  runningTotalColumn = (RunningTotalColumn) runningTotalCalc.createCalcColumn("id");

      //if break by not null
      assertEquals("Running Sum of year: id", runningTotalColumn.getHeader());

      runningTotalCalc.setBreakBy(AbstractCalc.ROW_INNER);
      runningTotalCalc.setAggregate(XConstants.AVERAGE_FORMULA);
      runningTotalCalc.setResetLevel(RunningTotalColumn.NONE);
      runningTotalColumn = (RunningTotalColumn) runningTotalCalc.createCalcColumn("id2");

      assertEquals(AbstractCalc.ROW_INNER, runningTotalCalc.getBreakBy());
      assertEquals(XConstants.AVERAGE_FORMULA, runningTotalCalc.getAggregate());
      assertEquals(RunningTotalColumn.NONE, runningTotalCalc.getResetLevel());
      assertEquals("Running Average by row: id2", runningTotalColumn.getHeader());
   }

   @Test
   void testUpdateRef() {
      runningTotalCalc = new RunningTotalCalc();
      VSDimensionRef mockDRef1 = mock(VSDimensionRef.class);
      when(mockDRef1.getFullName()).thenReturn("oname");

      VSDimensionRef mockDRef2 = mock(VSDimensionRef.class);
      when(mockDRef2.getFullName()).thenReturn("nname");

      runningTotalCalc.setBreakBy("oname");
      runningTotalCalc.updateRefs(List.of(mockDRef1), List.of(mockDRef2));

      assertEquals("nname", runningTotalCalc.getBreakByValue());

      runningTotalCalc.setResetLevel(RunningTotalColumn.QUARTER);
      assertEquals("Running of quarter nname", runningTotalCalc.getPrefixView0());
      assertEquals("Running of quarter nname: ", runningTotalCalc.getPrefixView());

      runningTotalCalc.setResetLevel(RunningTotalColumn.WEEK);
      assertEquals("Running Sum of week nname", runningTotalCalc.getPrefix0());
      assertEquals("Running Sum of week nname: ", runningTotalCalc.getPrefix());

      assertEquals(runningTotalCalc.RUNNINGTOTAL, runningTotalCalc.getType());
      assertEquals("RunningTotal(Sum,3)", runningTotalCalc.getName0());
      assertNull(runningTotalCalc.getLabel());

      boolean result = runningTotalCalc.equals(null);
      assertFalse(result);

      Object runningTotalCalc1 = runningTotalCalc.clone();
      result =  runningTotalCalc.equals(runningTotalCalc1);
      assertTrue(result);
   }
}
