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

package inetsoft.report.composition;

import inetsoft.report.composition.execution.TimeSliderVSAQuery;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.TimeSliderVSAssemblyInfo;
import inetsoft.web.viewsheet.service.VSSelectionService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #78073: a number range slider whose increment scales to just below an integer
 * (0.57 * 100 = 56.99999999999999) must still step by that increment and reach the data max.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class TimeSliderTickStepTest {
   /**
    * The grid steps by the slider's increment and its last tick reaches the data max, both for
    * user-set increments and for clean user-set increments replaced by a getNiceNumbers one
    * (0.001 -> 0.0014299999999999998, 0.75 -> 0.7505).
    */
   @ParameterizedTest
   @CsvSource({
      // user-set increments that scale to just below an integer
      "0, 57, 0.57",
      "0, 29, 0.29",
      "0, 0.057, 3e-4",
      "0, 0.003, 3e-4",
      // clean user-set increments replaced by a messy getNiceNumbers increment (0.304 + 5.7)
      "0, 1.001, 0.001",
      "0.304, 6.0040000000000004, 0.75"
   })
   void ticksStepByIncrementAndReachMax(double min, double max, double rangeSize)
      throws Exception
   {
      Viewsheet vs = new Viewsheet();
      TimeSliderVSAssembly ts = createNumberSlider(vs, rangeSize);
      query(vs, new Object[] { min, max });

      double[] v = values(ts);
      double inc = ((TimeSliderVSAssemblyInfo) ts.getInfo()).getTimeSliderSelection()
         .getIncrement();
      int n = v.length;

      assertTrue(n > 2, "ticks: " + n);
      assertTrue(v[0] <= min + 1e-9 * Math.max(1, Math.abs(min)),
                 "first tick " + v[0] + " is above the data min " + min);
      assertTrue(v[n - 1] >= max - 1e-9 * Math.max(1, Math.abs(max)),
                 "last tick " + v[n - 1] + " is below the data max " + max + " (inc " + inc + ")");

      for(int i = 1; i < n; i++) {
         assertEquals(inc, v[i] - v[i - 1], 1e-6 * inc,
                      "step " + i + " (" + v[i - 1] + " -> " + v[i] + ")");
      }
   }

   /**
    * Selecting from the second tick to the last tick filters with an upper bound at the data
    * max, so rows in (56, 57] are kept.
    */
   @Test
   void selectionToLastTickKeepsTopRows() throws Exception {
      Viewsheet vs = new Viewsheet();
      TimeSliderVSAssembly ts = createNumberSlider(vs, 0.57);
      query(vs, new Object[] { 0, 57 });
      int n = ts.getSelectionList().getSelectionValueCount();
      select(ts, 1, n - 1);

      ConditionList conds = ts.getConditionList();
      assertNotNull(conds, "a partial selection filters");
      assertEquals(0.57, bound(conds, XCondition.GREATER_THAN), 1e-9);
      assertEquals(57, bound(conds, XCondition.LESS_THAN), 1e-9);
   }

   private static double bound(ConditionList conds, int op) {
      for(int i = 0; i < conds.getSize(); i++) {
         if(conds.getItem(i) instanceof ConditionItem item &&
            item.getXCondition() instanceof Condition cond && cond.getOperation() == op)
         {
            return ((Number) cond.getValue(0)).doubleValue();
         }
      }

      fail("no condition with operation " + op + " in " + conds);
      return Double.NaN;
   }

   private static double[] values(TimeSliderVSAssembly ts) {
      SelectionList list = ts.getSelectionList();
      double[] v = new double[list.getSelectionValueCount()];

      for(int i = 0; i < v.length; i++) {
         v[i] = Double.parseDouble(list.getSelectionValue(i).getValue());
      }

      return v;
   }

   private static TimeSliderVSAssembly createNumberSlider(Viewsheet vs, double rangeSize) {
      TimeSliderVSAssembly ts = new TimeSliderVSAssembly(vs, SLIDER);
      SingleTimeInfo tinfo = new SingleTimeInfo();
      ColumnRef ref = new ColumnRef(new AttributeRef("Order", "Quantity"));
      ref.setDataType(XSchema.DOUBLE);
      tinfo.setDataRef(ref);
      tinfo.setRangeTypeValue(TimeInfo.NUMBER);
      tinfo.setRangeSizeValue(rangeSize);
      ts.setTimeInfo(tinfo);
      ts.setUpperInclusiveValue(true);
      vs.addAssembly(ts);
      return ts;
   }

   private static void query(Viewsheet vs, Object[] minMax) throws Exception {
      ViewsheetSandbox box = mock(ViewsheetSandbox.class);
      when(box.getID()).thenReturn("vs1");
      when(box.getViewsheet()).thenReturn(vs);
      when(box.getMode()).thenReturn(RuntimeViewsheet.VIEWSHEET_RUNTIME_MODE);
      new TimeSliderVSAQuery(box, SLIDER).refreshSelectionValue(minMax);
   }

   // the same selection the client's ApplySelectionEvent makes (selected = start <= i <= end)
   private static void select(TimeSliderVSAssembly ts, int from, int to) throws Exception {
      SelectionList slist = ts.getSelectionList();

      for(int i = 0; i < slist.getSelectionValueCount(); i++) {
         slist.getSelectionValue(i).setSelected(i >= from && i <= to);
      }

      Method apply = VSSelectionService.class.getDeclaredMethod(
         "applySelection", SelectionVSAssembly.class, SelectionList.class, boolean.class);
      apply.setAccessible(true);
      apply.invoke(mock(VSSelectionService.class), ts, slist, true);
   }

   private static final String SLIDER = "RangeSlider1";
}
