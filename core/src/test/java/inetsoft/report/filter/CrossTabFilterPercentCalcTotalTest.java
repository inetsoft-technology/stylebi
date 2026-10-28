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
package inetsoft.report.filter;

import inetsoft.graph.data.CalcColumn;
import inetsoft.report.TableDataDescriptor;
import inetsoft.report.TableDataPath;
import inetsoft.report.composition.graph.calc.*;
import inetsoft.report.internal.table.TableFormat;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.test.*;
import inetsoft.uql.XConstants;
import inetsoft.uql.XMetaInfo;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.VSDimensionRef;
import inetsoft.uql.viewsheet.graph.AbstractCalc;
import inetsoft.uql.viewsheet.graph.Calculator;
import inetsoft.uql.viewsheet.internal.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #78858, #78859: with "Compute Trend and Comparison for Totals" off, a crosstab total
 * cell shows the plain aggregate instead of the calc value. For a percent calc (% Change,
 * CompoundGrowth) the descriptor still formats that cell as a percent, so it read as
 * total x 100%. Those cells are now blank. The crosstab is wired the way
 * AbstractCrosstabVSAQuery and CrosstabVSAQuery wire it for a viewsheet crosstab.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class CrossTabFilterPercentCalcTotalTest {
   /**
    * % Change and CompoundGrowth (isAsPercent) with the option off: every total cell of the
    * calc is blank instead of the raw total.
    */
   @ParameterizedTest(name = "{0} sideBySide={1} byRow={2}")
   @MethodSource("percentLayouts")
   void percentCalcTotalsAreBlank(String kind, boolean sideBySide, boolean byRow) {
      CrossTabFilter f = build(kind, false, sideBySide, byRow, false);
      List<Cell> totals = calcCells(f, true);

      assertFalse(totals.isEmpty());

      for(Cell cell : totals) {
         assertNull(cell.value, cell.toString());
         assertEquals(TableFormat.PERCENT_FORMAT, cell.format, cell.toString());
      }

      if("ChangePct".equals(kind)) {
         // detail cells keep the computed change: no previous month (null), then +50%
         List<Object> details = new ArrayList<>();
         calcCells(f, false).forEach(c -> details.add(c.value));
         assertTrue(details.contains(0.5), details.toString());
         assertTrue(details.stream().allMatch(v -> v == null || v.equals(0.5)),
                    details.toString());
      }
   }

   /**
    * Non-percent calcs with the option off keep the plain aggregate on totals, by design.
    */
   @ParameterizedTest(name = "{0} sideBySide={1} byRow={2}")
   @MethodSource("nonPercentLayouts")
   void nonPercentCalcTotalsKeepAggregate(String kind, boolean sideBySide, boolean byRow) {
      CrossTabFilter f = build(kind, false, sideBySide, byRow, false);
      List<Cell> totals = calcCells(f, true);

      assertFalse(totals.isEmpty());

      for(Cell cell : totals) {
         assertNotNull(cell.value, cell.toString());
         assertEquals(cell.sum, cell.value, cell.toString());
      }

      assertTrue(totals.stream().anyMatch(c -> Double.valueOf(750).equals(c.value)),
                 totals.toString());
   }

   /**
    * Percent of grand total is not in the calc value map: its totals keep their ratio and the
    * percent format, with the option off and on.
    */
   @ParameterizedTest(name = "calcTotal={0} sideBySide={1} byRow={2}")
   @MethodSource("optionLayouts")
   void percentOfTotalTotalsKeepRatio(boolean calcTotal, boolean sideBySide, boolean byRow) {
      CrossTabFilter f = build("Percent", calcTotal, sideBySide, byRow, false);
      List<Cell> totals = calcCells(f, true);

      assertFalse(totals.isEmpty());

      for(Cell cell : totals) {
         assertInstanceOf(Number.class, cell.value, cell.toString());
         double ratio = ((Number) cell.value).doubleValue();
         assertTrue(ratio > 0 && ratio <= 1.0, cell.toString());
         assertEquals(TableFormat.PERCENT_FORMAT, cell.format, cell.toString());
      }

      assertTrue(totals.stream().anyMatch(c -> Double.valueOf(1.0).equals(c.value)),
                 totals.toString());
   }

   /**
    * Bug #79130: with the option on, a CompoundGrowth total cell must not show the raw total
    * (its Sum) formatted as a percent.
    */
   @ParameterizedTest(name = "sideBySide={0} byRow={1}")
   @MethodSource("layouts")
   void compoundGrowthTotalsNeverShowRawTotalWhenOptionOn(boolean sideBySide, boolean byRow) {
      CrossTabFilter f = build("Compound", true, sideBySide, byRow, false);
      List<Cell> totals = calcCells(f, true);

      assertFalse(totals.isEmpty());

      for(Cell cell : totals) {
         assertNull(cell.value, cell.toString());
      }
   }

   /**
    * Bug #79130: detail cells of CompoundGrowth are unchanged by the option.
    */
   @ParameterizedTest(name = "sideBySide={0} byRow={1}")
   @MethodSource("layouts")
   void compoundGrowthDetailCellsUnchangedByOption(boolean sideBySide, boolean byRow) {
      List<Cell> on = calcCells(build("Compound", true, sideBySide, byRow, false), false);
      List<Cell> off = calcCells(build("Compound", false, sideBySide, byRow, false), false);

      assertFalse(on.isEmpty());
      assertEquals(off, on);
   }

   /**
    * With "Compute Trend and Comparison for Totals" on, the totals hold the computed calc
    * value as before, never the raw aggregate.
    */
   @ParameterizedTest(name = "sideBySide={0} byRow={1}")
   @MethodSource("layouts")
   void percentChangeTotalsAreComputedWhenOptionOn(boolean sideBySide, boolean byRow) {
      CrossTabFilter f = build("ChangePct", true, sideBySide, byRow, false);
      List<Cell> totals = calcCells(f, true);

      assertFalse(totals.isEmpty());

      // a total tuple without the Month level has no previous value (null); the grand total
      // of month 2 changes +50% from month 1
      for(Cell cell : totals) {
         assertTrue(cell.value == null || cell.value.equals(0.5), cell.toString());
      }

      assertTrue(totals.stream().anyMatch(c -> Double.valueOf(0.5).equals(c.value)),
                 totals.toString());
   }

   /**
    * With "Fill blank with zero" on, a blanked percent total shows as 0 (0%), like the other
    * blank calc cells, and the Sum totals are unchanged.
    */
   @ParameterizedTest(name = "sideBySide={0} byRow={1}")
   @MethodSource("layouts")
   void percentCalcTotalsAreZeroWithFillBlankWithZero(boolean sideBySide, boolean byRow) {
      CrossTabFilter f = build("ChangePct", false, sideBySide, byRow, true);
      List<Cell> totals = calcCells(f, true);

      assertFalse(totals.isEmpty());

      for(Cell cell : totals) {
         assertEquals(0.0, cell.value, cell.toString());
         assertNotNull(cell.sum, cell.toString());
      }

      assertTrue(totals.stream().anyMatch(c -> Double.valueOf(750).equals(c.sum)),
                 totals.toString());
      assertTrue(totals.stream().anyMatch(c -> Double.valueOf(250).equals(c.sum)),
                 totals.toString());
   }

   /**
    * Three row levels and two column levels with every group total shown: inner subtotals,
    * outer subtotals, row subtotal x column subtotal crossings and the grand totals of a
    * % Change calc are all blank.
    */
   @ParameterizedTest(name = "sideBySide={0}")
   @ValueSource(booleans = { true, false })
   void percentCalcTotalsAreBlankWithMultipleLevels(boolean sideBySide) {
      CrossTabFilter f = buildMultiLevel(sideBySide);
      List<Cell> totals = calcCells(f, true);
      boolean innerSubtotal = false;
      boolean crossing = false;

      assertFalse(totals.isEmpty());

      for(Cell cell : totals) {
         assertNull(cell.value, cell.toString());
         int rsize = f.getRowTuple(cell.row).size();
         int csize = f.getColTuple(cell.col).size();
         innerSubtotal |= rsize == 2;
         crossing |= rsize > 0 && rsize < 3 && csize == 1;
      }

      assertTrue(innerSubtotal, "no Year-Q subtotal cell");
      assertTrue(crossing, "no row subtotal x column subtotal cell");
      assertTrue(calcCells(f, false).stream().anyMatch(c -> c.value != null));
   }

   /**
    * Bug #78859's date comparison path: the calc that date comparison attaches to a crosstab
    * aggregate (DateComparisonInfo.createComparisonCalc, as CrosstabDcProcessor uses it) is a
    * percent ChangeColumn for "Percent Change" and "Value and Percent Change", so its totals
    * are blank with the option off. "Change" stays an absolute change and keeps the plain
    * aggregate on totals.
    */
   @Test
   void dateComparisonPercentCalcTotalsAreBlank() {
      for(int option : new int[] { DateComparisonInfo.PERCENT, DateComparisonInfo.PERCENT_VALUE,
                                   DateComparisonInfo.CHANGE })
      {
         boolean percent = option != DateComparisonInfo.CHANGE;
         Calculator calculator = dateComparison(option).createComparisonCalc(
            yearDim(), true, new ArrayList<>());
         ChangeCalc changeCalc = assertInstanceOf(ChangeCalc.class, calculator);
         assertEquals(percent, changeCalc.isAsPercent(), "option " + option);

         ChangeColumn column =
            assertInstanceOf(ChangeColumn.class, changeCalc.createCalcColumn(SUM));
         assertEquals(percent, column.isAsPercent(), "option " + option);

         // the DC calc compares with the previous year of a date column; the test data has
         // plain Year/Month numbers, so compare with the previous Month instead. The unit of
         // the calc (isAsPercent) is what decides the total cells.
         column.setChangeType(ValueOfCalc.PREVIOUS);
         column.setDim("Month");
         CrossTabFilter f = build("DC", column, false, true, true, false);
         List<Cell> totals = calcCells(f, true);

         assertFalse(totals.isEmpty());

         for(Cell cell : totals) {
            if(percent) {
               assertNull(cell.value, "option " + option + " " + cell);
            }
            else {
               assertEquals(cell.sum, cell.value, "option " + option + " " + cell);
            }
         }
      }
   }

   /**
    * The date comparison of the end-to-end test,
    * DateComparisonCrosstabPercentTotalViewsheetTest: this year vs the previous year, by month.
    */
   private static DateComparisonInfo dateComparison(int option) {
      DateComparisonInfo dcInfo = new DateComparisonInfo();
      StandardPeriods periods = new StandardPeriods();
      periods.setDateLevel(XConstants.YEAR_DATE_GROUP);
      periods.setPreCount(1);
      periods.setToDayAsEndDay(false);
      periods.setEndDateValue("2025-12-31");
      periods.setToDate(false);
      periods.setInclusive(true);
      dcInfo.setDateComparisonPeriods(periods);
      DateComparisonInterval interval = new DateComparisonInterval();
      interval.setGranularity(DateComparisonInfo.MONTH);
      interval.setLevel(DateComparisonInfo.ALL);
      interval.setContextLevel(XConstants.YEAR_DATE_GROUP);
      dcInfo.setDateComparisonInterval(interval);
      dcInfo.setComparisonOption(option);
      return dcInfo;
   }

   private static VSDimensionRef yearDim() {
      VSDimensionRef dim = new VSDimensionRef();
      AttributeRef attr = new AttributeRef(null, "D");
      attr.setDataType(XSchema.DATE);
      ColumnRef column = new ColumnRef(attr);
      column.setDataType(XSchema.DATE);
      dim.setDataRef(column);
      dim.setGroupColumnValue("D");
      dim.setDataType(XSchema.DATE);
      dim.setDateLevelValue(XConstants.YEAR_DATE_GROUP + "");
      return dim;
   }

   /**
    * Bug #79450: rows [Year, month-of-year] with a Previous Month calc on the inner dimension.
    * The month-of-year member is a number, not a Date, so the calc used to throw, the filter
    * swallowed it and getRowCount() hit a NullPointerException. The filter must now build and
    * every cell of the calc column is blank.
    */
   @Test
   void previousMonthOnNonDateInnerRowIsBlankNotAnError() {
      ChangeColumn column = new ChangeColumn(SUM, CALC);
      column.setChangeType(ValueOfCalc.PREVIOUS_MONTH);
      column.setAsPercent(true);
      column.setDim(AbstractCalc.ROW_INNER);
      CrossTabFilter f = build("MOY", column, false, true, true, false);
      List<Cell> details = assertDoesNotThrow(() -> calcCells(f, false));

      assertFalse(details.isEmpty());

      for(Cell cell : details) {
         assertNull(cell.value, cell.toString());
      }
   }

   private static Stream<Arguments> layouts() {
      List<Arguments> list = new ArrayList<>();

      for(boolean sbs : new boolean[] { true, false }) {
         for(boolean byRow : new boolean[] { true, false }) {
            list.add(Arguments.of(sbs, byRow));
         }
      }

      return list.stream();
   }

   private static Stream<Arguments> optionLayouts() {
      return Stream.of(false, true).flatMap(
         ct -> layouts().map(a -> Arguments.of(ct, a.get()[0], a.get()[1])));
   }

   private static Stream<Arguments> percentLayouts() {
      return kindLayouts("ChangePct", "Compound");
   }

   private static Stream<Arguments> nonPercentLayouts() {
      return kindLayouts("ChangeAbs", "ValueOf", "Running", "Moving");
   }

   private static Stream<Arguments> kindLayouts(String... kinds) {
      return Arrays.stream(kinds).flatMap(
         k -> layouts().map(a -> Arguments.of(k, a.get()[0], a.get()[1])));
   }

   /**
    * The cells of the calc measure, either the total cells (row or column tuple is a total)
    * or the detail cells, each with the plain Sum cell of the same tuples.
    */
   private static List<Cell> calcCells(CrossTabFilter f, boolean totals) {
      TableDataDescriptor desc = f.getDescriptor();
      List<Cell> cells = new ArrayList<>();
      boolean sideBySide = f.isSummarySideBySide();

      for(int r = f.getHeaderRowCount(); r < f.getRowCount(); r++) {
         for(int c = f.getHeaderColCount(); c < f.getColCount(); c++) {
            TableDataPath path = desc.getCellDataPath(r, c);

            if(!CALC_PATH.equals(lastPath(path)) ||
               totals != (f.isTotalRow(r) || f.isTotalCol(c)))
            {
               continue;
            }

            int sr = sideBySide ? r : r - 1;
            int sc = sideBySide ? c - 1 : c;
            assertEquals(SUM_PATH, lastPath(desc.getCellDataPath(sr, sc)));
            XMetaInfo info = desc.getXMetaInfo(path);
            String format = info == null || info.getXFormatInfo() == null ? null :
               info.getXFormatInfo().getFormat();
            cells.add(new Cell(r, c, f.getObject(r, c), f.getObject(sr, sc), format));
         }
      }

      return cells;
   }

   private static String lastPath(TableDataPath path) {
      return path == null || path.getPath().length == 0 ? null :
         path.getPath()[path.getPath().length - 1];
   }

   private record Cell(int row, int col, Object value, Object sum, String format) {
   }

   private static CalcColumn calc(String kind, boolean byRow) {
      String inner = byRow ? AbstractCalc.ROW_INNER : AbstractCalc.COLUMN_INNER;

      switch(kind) {
      case "ChangePct": {
         ChangeColumn c = new ChangeColumn(SUM, CALC);
         c.setChangeType(ValueOfCalc.PREVIOUS);
         c.setAsPercent(true);
         c.setDim("Month");
         return c;
      }
      case "ChangeAbs": {
         ChangeColumn c = new ChangeColumn(SUM, CALC);
         c.setChangeType(ValueOfCalc.PREVIOUS);
         c.setAsPercent(false);
         c.setDim("Month");
         return c;
      }
      case "ValueOf": {
         ValueOfColumn c = new ValueOfColumn(SUM, CALC);
         c.setChangeType(ValueOfCalc.PREVIOUS);
         c.setDim("Month");
         return c;
      }
      case "Running": {
         RunningTotalColumn c = new RunningTotalColumn(SUM, CALC);
         c.setFormula(new SumFormula());
         c.setBreakBy(inner);
         return c;
      }
      case "Compound": {
         CompoundGrowthColumn c = new CompoundGrowthColumn(SUM, CALC);
         c.setBreakBy(inner);
         return c;
      }
      case "Moving": {
         MovingColumn c = new MovingColumn(SUM, CALC);
         c.setFormula(new SumFormula());
         c.setInnerDim(inner);
         c.setPreCnt(1);
         c.setIncludeCurrent(true);
         return c;
      }
      case "Percent": {
         PercentColumn c = new PercentColumn(SUM, CALC);
         c.setLevel(PercentCalc.GRAND_TOTAL);
         return c;
      }
      default:
         throw new IllegalArgumentException(kind);
      }
   }

   /**
    * Year {2022, 2023} x Month {1, 2} x Cat {A, B}. By row: rows [Year, Month], cols [Cat];
    * by column: rows [Cat], cols [Year, Month]. Year subtotals and both grand totals shown.
    */
   private static CrossTabFilter build(String kind, boolean calcTotal, boolean sideBySide,
                                       boolean byRow, boolean fillZero)
   {
      return build(kind, calc(kind, byRow), calcTotal, sideBySide, byRow, fillZero);
   }

   private static CrossTabFilter build(String kind, CalcColumn calc, boolean calcTotal,
                                       boolean sideBySide, boolean byRow, boolean fillZero)
   {
      Object[][] data = {
         {"Year", "Month", "Cat", "Total"},
         {2022, 1, "A", 60.0}, {2022, 1, "B", 40.0}, {2022, 2, "A", 90.0}, {2022, 2, "B", 60.0},
         {2023, 1, "A", 120.0}, {2023, 1, "B", 80.0}, {2023, 2, "A", 180.0}, {2023, 2, "B", 120.0},
      };
      Formula f2 = new SumFormula();

      if("Percent".equals(kind)) {
         ((PercentageFormula) f2).setPercentageType(XConstants.PERCENTAGE_OF_GRANDTOTAL);
      }

      int[] rows = byRow ? new int[] {0, 1} : new int[] {2};
      int[] cols = byRow ? new int[] {2} : new int[] {0, 1};
      CrossTabFilter f = new CrossTabFilter(new DefaultTableLens(data), rows, cols,
                                            new int[] {3, 3}, new Formula[] {new SumFormula(), f2});

      if(byRow) {
         f.setSuppressRowGroupTotal(false, 0);
      }
      else {
         f.setSuppressColumnGroupTotal(false, 0);
      }

      f.setRowHeaders(byRow ? List.of("Year", "Month") : List.of("Cat"));
      f.setColHeaders(byRow ? List.of("Cat") : List.of("Year", "Month"));

      if("Percent".equals(kind)) {
         f.setPercentageDirection(byRow ? XConstants.PERCENTAGE_BY_ROW
                                        : XConstants.PERCENTAGE_BY_COL);
      }

      return finish(f, calc, calcTotal, sideBySide, fillZero);
   }

   /**
    * Rows [Year, Q, Month], cols [Cat, Sub], % Change from the previous month, every group
    * total shown, option off.
    */
   private static CrossTabFilter buildMultiLevel(boolean sideBySide) {
      List<Object[]> data = new ArrayList<>();
      data.add(new Object[] {"Year", "Q", "Month", "Cat", "Sub", "Total"});

      for(int year : new int[] {2022, 2023}) {
         for(int month : new int[] {1, 2, 4, 5}) {
            for(String cat : new String[] {"A", "B"}) {
               for(String sub : new String[] {"x", "y"}) {
                  double value = (year - 2021) * 100 + month * 10 + ("A".equals(cat) ? 1 : 3) +
                     ("x".equals(sub) ? 0 : 5);
                  data.add(new Object[] {year, month < 3 ? 1 : 2, month, cat, sub, value});
               }
            }
         }
      }

      CrossTabFilter f = new CrossTabFilter(
         new DefaultTableLens(data.toArray(new Object[0][])), new int[] {0, 1, 2},
         new int[] {3, 4}, new int[] {5, 5}, new Formula[] {new SumFormula(), new SumFormula()});
      f.setSuppressRowGroupTotal(false, 0);
      f.setSuppressRowGroupTotal(false, 1);
      f.setSuppressColumnGroupTotal(false, 0);
      f.setRowHeaders(List.of("Year", "Q", "Month"));
      f.setColHeaders(List.of("Cat", "Sub"));
      return finish(f, calc("ChangePct", true), false, sideBySide, false);
   }

   private static CrossTabFilter finish(CrossTabFilter f, CalcColumn calc, boolean calcTotal,
                                        boolean sideBySide, boolean fillZero)
   {
      f.setSuppressRowGrandTotal(false);
      f.setSuppressColumnGrandTotal(false);
      f.setSummarySideBySide(sideBySide);
      f.setFillBlankWithZero(fillZero);

      if(calc instanceof AbstractColumn) {
         ((AbstractColumn) calc).setCalculateTotal(calcTotal);
      }

      calc.setColIndex(1);
      List<CalcColumn> calcs = new ArrayList<>();
      calcs.add(calc);
      f.setCalcs(calcs, calcTotal);
      Map<String, String> calcHeaders = new HashMap<>();
      calcHeaders.put(CALC, SUM);
      f.setMeasureNames(new String[] {SUM, CALC}, calcHeaders);
      f.moreRows(Integer.MAX_VALUE);
      return f;
   }

   private static final String SUM = "Sum(Total)";
   private static final String CALC = "C Sum(Total)";
   // data path names of the cells: the column name for the aggregate, the calc header
   // with the aggregate index for the calc
   private static final String SUM_PATH = "Total";
   private static final String CALC_PATH = CALC + ".1";
}
