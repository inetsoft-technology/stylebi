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
package inetsoft.uql.viewsheet.graph;

import inetsoft.report.composition.graph.VSDataSet;
import inetsoft.report.composition.graph.calc.ChangeColumn;
import inetsoft.report.composition.graph.calc.ValueOfCalc;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.test.*;
import inetsoft.uql.XConstants;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;

import static inetsoft.test.XTableUtil.date;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #77176: since #77010, for the equal-level date comparison shapes (interval level =
 * granularity = context, coarser period, e.g. Quarter / Week to Date / Week), the temp date
 * group has the same full name as the chart's part dimension (e.g. WeekOfQuarter). The temp
 * list was also handed to the change calculator as the lookup ignore list, so the part column
 * was dropped from the previous-period lookup and every part was compared with the first part
 * of the previous period. ChartDcProcessor must hand the calculator a list without the part
 * dimension, while the info keeps the full list (query grouping and the #77010 merge-part
 * wrap depend on it).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ChartDcProcessorCalcTempGroupsTest {
   @Test
   void quarterWeekToDateWeekDropsPartDimFromCalcListOnly() {
      Shape shape = shape(XConstants.QUARTER_DATE_GROUP, DateComparisonInfo.WEEK_TO_DATE,
                          DateComparisonInfo.WEEK, XConstants.WEEK_DATE_GROUP);

      assertTrue(names(shape.tempRefs).contains(shape.partName),
                 "fixture: temp list must contain the part dim (#77010 shape)");
      assertTrue(shape.partName.startsWith("WeekOfQuarter("), shape.partName);
      assertFalse(names(shape.calcList).contains(shape.partName));
      // the info's array is not modified
      assertTrue(names(shape.tempRefs).contains(shape.partName));
   }

   @Test
   void yearSameQuarterQuarterDropsPartDimFromCalcList() {
      Shape shape = shape(XConstants.YEAR_DATE_GROUP, DateComparisonInfo.SAME_QUARTER,
                          DateComparisonInfo.QUARTER, XConstants.QUARTER_DATE_GROUP);

      assertTrue(names(shape.tempRefs).contains(shape.partName));
      assertTrue(shape.partName.startsWith("QuarterOfYear("), shape.partName);
      assertFalse(names(shape.calcList).contains(shape.partName));
   }

   @Test
   void yearMonthToDateMonthDropsPartDimFromCalcList() {
      Shape shape = shape(XConstants.YEAR_DATE_GROUP, DateComparisonInfo.MONTH_TO_DATE,
                          DateComparisonInfo.MONTH, XConstants.MONTH_DATE_GROUP);

      assertTrue(names(shape.tempRefs).contains(shape.partName));
      assertFalse(names(shape.calcList).contains(shape.partName));
   }

   @Test
   void yearWeekToDateWeekKeepsAuxiliaryQuarterOfYearIgnored() {
      Shape shape = shape(XConstants.YEAR_DATE_GROUP, DateComparisonInfo.WEEK_TO_DATE,
                          DateComparisonInfo.WEEK, XConstants.WEEK_DATE_GROUP);

      assertFalse(names(shape.calcList).contains(shape.partName));
      // every temp ref other than the part dim is still ignored
      List<String> expected = new ArrayList<>(names(shape.tempRefs));
      expected.removeIf(shape.partName::equals);
      assertEquals(expected, names(shape.calcList));
   }

   @Test
   void nonCollidingTempRefsAreKept() {
      VSDimensionRef other = dateRef();
      other.setDateLevel(XConstants.YEAR_DATE_GROUP);
      VSDimensionRef part = dateRef();
      part.setDateLevel(XConstants.QUARTER_OF_YEAR_DATE_GROUP);

      List<XDimensionRef> list = ChartDcProcessor.getCalcIgnoreTempGroups(
         new XDimensionRef[] { other, null }, part);

      assertEquals(2, list.size());
      assertSame(other, list.get(0));
      assertNull(list.get(1));
      assertTrue(ChartDcProcessor.getCalcIgnoreTempGroups(null, part).isEmpty());
      assertEquals(1, ChartDcProcessor.getCalcIgnoreTempGroups(
         new XDimensionRef[] { other }, null).size());
   }

   /**
    * The reporter's data (Quarter / Week to Date / Week, Change). Before the fix every week was
    * compared with week 1 of the previous quarter: Q3 wk2 = 57-48 = 9, Q4 wk2 = 62-65 = -3.
    */
   @Test
   void reporterDataComparesSameWeekOfPreviousQuarter() {
      Shape shape = shape(XConstants.QUARTER_DATE_GROUP, DateComparisonInfo.WEEK_TO_DATE,
                          DateComparisonInfo.WEEK, XConstants.WEEK_DATE_GROUP);
      String period = "Quarter(date)";
      Date q2 = date("2021-04-01"), q3 = date("2021-07-01"), q4 = date("2021-10-01");

      DefaultTableLens table = new DefaultTableLens(new Object[][] {
         { period, shape.partName, "v" },
         { q2, 1, 48 }, { q2, 2, 66 }, { q2, 3, 66 }, { q2, 6, 64 },
         { q3, 1, 65 }, { q3, 2, 57 }, { q3, 3, 84 }, { q3, 6, 97 },
         { q4, 1, 70 }, { q4, 2, 62 },
      });

      ChangeColumn column = changeColumn(period, ValueOfCalc.PREVIOUS_QUARTER, shape.calcList);
      VSDataSet data = dataSet(table, period, shape.partName);

      assertEquals(17.0, column.calculate(data, 4, false, false)); // Q3 wk1 65-48
      assertEquals(-9.0, column.calculate(data, 5, false, false)); // Q3 wk2 57-66
      assertEquals(18.0, column.calculate(data, 6, false, false)); // Q3 wk3 84-66
      assertEquals(33.0, column.calculate(data, 7, false, false)); // Q3 wk6 97-64
      assertEquals(5.0, column.calculate(data, 9, false, false));  // Q4 wk2 62-57
   }

   /**
    * Year / Same Quarter / Quarter, Change: each quarter compares with the same quarter of the
    * previous year, not with Q1 of the previous year.
    */
   @Test
   void yearSameQuarterComparesSameQuarterOfPreviousYear() {
      Shape shape = shape(XConstants.YEAR_DATE_GROUP, DateComparisonInfo.SAME_QUARTER,
                          DateComparisonInfo.QUARTER, XConstants.QUARTER_DATE_GROUP);
      String period = "Year(date)";
      Date y20 = date("2020-01-01"), y21 = date("2021-01-01");

      DefaultTableLens table = new DefaultTableLens(new Object[][] {
         { period, shape.partName, "v" },
         { y20, 1, 10 }, { y20, 2, 20 }, { y20, 3, 30 },
         { y21, 1, 11 }, { y21, 2, 25 }, { y21, 3, 27 },
      });

      ChangeColumn column = changeColumn(period, ValueOfCalc.PREVIOUS_YEAR, shape.calcList);
      VSDataSet data = dataSet(table, period, shape.partName);

      assertEquals(1.0, column.calculate(data, 3, false, false));  // 11-10
      assertEquals(5.0, column.calculate(data, 4, false, false));  // 25-20
      assertEquals(-3.0, column.calculate(data, 5, false, false)); // 27-30
   }

   // -- fixture ----------------------------------------------------------------------------

   private record Shape(XDimensionRef[] tempRefs, String partName, List<XDimensionRef> calcList) {
   }

   /** Mirrors ChartDcProcessor.process(): temp refs first, then updateDateDimensionLevel. */
   private static Shape shape(int periodLevel, int intervalLevel, int granularity, int context) {
      DateComparisonInfo dcInfo = new DateComparisonInfo();
      StandardPeriods periods = new StandardPeriods();
      periods.setDateLevel(periodLevel);
      periods.setPreCount(2);
      periods.setToDayAsEndDay(false);
      periods.setEndDateValue("2021-11-10");
      periods.setToDate(false);
      periods.setInclusive(true);
      dcInfo.setDateComparisonPeriods(periods);

      DateComparisonInterval interval = new DateComparisonInterval();
      interval.setGranularity(granularity);
      interval.setLevel(intervalLevel);
      interval.setContextLevel(context);
      dcInfo.setDateComparisonInterval(interval);
      dcInfo.setComparisonOption(DateComparisonInfo.CHANGE);

      Viewsheet vs = new Viewsheet();
      VSDimensionRef dateDim = dateRef();
      XDimensionRef[] tempRefs = DateComparisonUtil.getAllTempDateGroupRef(dcInfo, "ds", dateDim, vs);
      XDimensionRef[] infoCopy = tempRefs.clone();
      dcInfo.updateDateDimensionLevel(dateDim, "ds", vs, true);

      List<XDimensionRef> calcList = ChartDcProcessor.getCalcIgnoreTempGroups(tempRefs, dateDim);
      assertArrayEquals(infoCopy, tempRefs, "the info's temp group array must not be modified");

      return new Shape(tempRefs, dateDim.getFullName(), calcList);
   }

   private static VSDimensionRef dateRef() {
      AttributeRef attr = new AttributeRef("date");
      attr.setDataType(XSchema.DATE);
      return new VSDimensionRef(attr);
   }

   private static List<String> names(Object refs) {
      List<String> names = new ArrayList<>();
      Collection<?> list = refs instanceof Object[] ? Arrays.asList((Object[]) refs) : (List<?>) refs;

      for(Object ref : list) {
         names.add(((XDimensionRef) ref).getFullName());
      }

      return names;
   }

   private static ChangeColumn changeColumn(String period, int ctype, List<XDimensionRef> ignore) {
      ChangeColumn column = new ChangeColumn("v", "Change of v");
      column.setAsPercent(false);
      column.setChangeType(ctype);
      column.setDim(period);
      column.setInnerDim(period);
      column.setDcTempGroups(ignore);
      return column;
   }

   private static VSDataSet dataSet(DefaultTableLens table, String... dims) {
      VSDataRef[] refs = new VSDataRef[dims.length];

      for(int i = 0; i < dims.length; i++) {
         VSDimensionRef ref = mock(VSDimensionRef.class);
         when(ref.getFullName()).thenReturn(dims[i]);
         refs[i] = ref;
      }

      return new VSDataSet(table, refs);
   }
}
