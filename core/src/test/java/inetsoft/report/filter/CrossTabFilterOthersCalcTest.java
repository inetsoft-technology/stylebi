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
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.test.*;
import inetsoft.uql.XConstants;
import inetsoft.uql.viewsheet.graph.AbstractCalc;

import java.util.*;
import java.util.stream.Stream;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
/**
 * The calculated measures (Change, Value Of, Running Total, Moving, Percent of total) of a
 * crosstab whose innermost row dimension has Top N with "Group all others together": the
 * Others row must show what the calculation gives for the sum of the other groups. The oracle is
 * the same data with the groups outside the top N relabeled "Others" in the base table and no
 * Top N; a plain group has no Others handling of its own, so the cells of both crosstabs must be
 * equal (details, the Others row, totals).
 *
 * The crosstab is wired like CrossTabFilterPercentCalcTotalTest wires it (the way
 * AbstractCrosstabVSAQuery does for a viewsheet crosstab): rows [Cat] or [Outer, Cat], columns
 * [Month] or [Year, Month], the calculation along Month, "Compute trend and comparison for
 * totals" off.
 */
class CrossTabFilterOthersCalcTest {
   private static final String SUM = "Sum(Total)";
   private static final String CALC = "C Sum(Total)";
   private static final String CALC_PATH = CALC + ".1";
   private static final String[] KINDS = { "ChangePct", "ChangeAbs", "ValueOf", "Running", "Moving",
                                           "Percent" };

   static Stream<org.junit.jupiter.params.provider.Arguments> configs() {
      List<org.junit.jupiter.params.provider.Arguments> list = new ArrayList<>();

      for(String kind : KINDS) {
         for(int rl = 1; rl <= 2; rl++) {
            for(int cl = 1; cl <= 2; cl++) {
               for(boolean aggTopN : new boolean[] { false, true }) {
                  list.add(org.junit.jupiter.params.provider.Arguments.of(kind, rl, cl, aggTopN));
               }
            }
         }
      }

      return list.stream();
   }

   @ParameterizedTest(name = "{0} rows={1} cols={2} aggregateTopN={3}")
   @MethodSource("configs")
   void othersRowShowsTheCalculationOfTheMergedGroups(String kind, int rowLevels, int colLevels,
                                                      boolean aggTopN)
   {
      List<Object[]> rows = baseRows();
      Map<String, Object> actual = cells(build(rows, kind, rowLevels, colLevels, true, aggTopN));
      Map<String, Object> expected =
         cells(build(relabelOthers(rows, rowLevels), kind, rowLevels, colLevels, false, aggTopN));
      List<String> problems = new ArrayList<>();
      int others = 0;

      for(Map.Entry<String, Object> e : actual.entrySet()) {
         assertTrue(expected.containsKey(e.getKey()), "no expected cell for " + e.getKey());

         if(e.getKey().contains("Others")) {
            others++;
         }

         if(!same(expected.get(e.getKey()), e.getValue())) {
            problems.add(e.getKey() + " expected=" + expected.get(e.getKey()) + " actual="
                            + e.getValue());
         }
      }

      assertTrue(others > 0, "the crosstab has no Others cell");
      assertTrue(problems.isEmpty(), problems.size() + " cells differ:\n"
         + String.join("\n", problems.subList(0, Math.min(10, problems.size()))));
   }

   // ---- crosstab under test ----

   /** Outer {b1, b2} x Cat {a1..a6} x Year {2022, 2023} x Month {1..3}, sparse, no ties in Sum. */
   private static List<Object[]> baseRows() {
      Random rnd = new Random(2);
      List<Object[]> rows = new ArrayList<>();
      int cats = 6;

      for(String outer : new String[] { "b1", "b2" }) {
         for(int c = 0; c < cats; c++) {
            for(int year = 2022; year <= 2023; year++) {
               for(int month = 1; month <= 3; month++) {
                  if(rnd.nextInt(5) == 0) {
                     continue;
                  }

                  double base = (cats - c) * 100 + ("b1".equals(outer) ? 0 : 7) * (c + 1);
                  rows.add(new Object[] {
                     outer, "a" + (c + 1), year, month,
                     base + rnd.nextInt(50) + (year - 2022) * 3 + month
                  });
               }
            }
         }
      }

      return rows;
   }

   /** The categories outside the top 2 (by Sum within the outer group) become Others. */
   private static List<Object[]> relabelOthers(List<Object[]> rows, int rowLevels) {
      Map<String, Map<String, Double>> totals = new TreeMap<>();

      for(Object[] row : rows) {
         String parent = rowLevels == 1 ? "*" : (String) row[0];
         totals.computeIfAbsent(parent, k -> new HashMap<>())
            .merge((String) row[1], (Double) row[4], Double::sum);
      }

      Map<String, Set<String>> kept = new HashMap<>();

      for(Map.Entry<String, Map<String, Double>> e : totals.entrySet()) {
         List<Map.Entry<String, Double>> list = new ArrayList<>(e.getValue().entrySet());
         list.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
         Set<String> set = new HashSet<>();

         for(int i = 0; i < Math.min(2, list.size()); i++) {
            set.add(list.get(i).getKey());
         }

         kept.put(e.getKey(), set);
      }

      List<Object[]> out = new ArrayList<>();

      for(Object[] row : rows) {
         Object[] copy = row.clone();
         String parent = rowLevels == 1 ? "*" : (String) row[0];

         if(!kept.get(parent).contains(row[1])) {
            copy[1] = CrossFilter.OTHERS;
         }

         out.add(copy);
      }

      return out;
   }

   private static CrossTabFilter build(List<Object[]> rowsIn, String kind, int rowLevels,
                                       int colLevels, boolean topN, boolean aggTopN)
   {
      List<Object[]> all = new ArrayList<>();
      all.add(new Object[] { "Outer", "Cat", "Year", "Month", "Total" });
      all.addAll(rowsIn);
      int[] rows = rowLevels == 1 ? new int[] { 1 } : new int[] { 0, 1 };
      int[] cols = colLevels == 1 ? new int[] { 3 } : new int[] { 2, 3 };
      Formula second = new SumFormula();

      if("Percent".equals(kind)) {
         ((PercentageFormula) second).setPercentageType(XConstants.PERCENTAGE_OF_GRANDTOTAL);
      }

      CrossTabFilter f = new CrossTabFilter(new DefaultTableLens(all.toArray(new Object[0][])),
                                            rows, cols, new int[] { 4, 4 },
                                            new Formula[] { new SumFormula(), second });
      f.setRowHeaders(rowLevels == 1 ? List.of("Cat") : List.of("Outer", "Cat"));
      f.setColHeaders(colLevels == 1 ? List.of("Month") : List.of("Year", "Month"));

      if(rowLevels == 2) {
         f.setSuppressRowGroupTotal(false, 0);
      }

      if(colLevels == 2) {
         f.setSuppressColumnGroupTotal(false, 0);
      }

      if("Percent".equals(kind)) {
         f.setPercentageDirection(XConstants.PERCENTAGE_BY_COL);
      }

      f.setSuppressRowGrandTotal(false);
      f.setSuppressColumnGrandTotal(false);
      f.setSummarySideBySide(true);
      f.setAggregateTopN(aggTopN);

      if(topN) {
         f.setRowTopN(rowLevels - 1, 0, 2, false, true);
      }

      f.setSortOthersLast(true);
      CalcColumn calc = calc(kind);

      if(calc instanceof AbstractColumn) {
         ((AbstractColumn) calc).setCalculateTotal(false);
      }

      calc.setColIndex(1);
      List<CalcColumn> calcs = new ArrayList<>();
      calcs.add(calc);
      f.setCalcs(calcs, false);
      Map<String, String> headers = new HashMap<>();
      headers.put(CALC, SUM);
      f.setMeasureNames(new String[] { SUM, CALC }, headers);
      f.moreRows(Integer.MAX_VALUE);
      return f;
   }

   private static CalcColumn calc(String kind) {
      String inner = AbstractCalc.COLUMN_INNER;

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
      case "Moving": {
         MovingColumn c = new MovingColumn(SUM, CALC);
         c.setFormula(new SumFormula());
         c.setInnerDim(inner);
         c.setPreCnt(1);
         c.setIncludeCurrent(true);
         return c;
      }
      default: {
         PercentColumn c = new PercentColumn(SUM, CALC);
         c.setLevel(PercentCalc.GRAND_TOTAL);
         return c;
      }
      }
   }

   /** The calc cells, keyed by row tuple and column tuple. */
   private static Map<String, Object> cells(CrossTabFilter f) {
      Map<String, Object> map = new TreeMap<>();
      TableDataDescriptor desc = f.getDescriptor();

      for(int r = f.getHeaderRowCount(); r < f.getRowCount(); r++) {
         for(int c = f.getHeaderColCount(); c < f.getColCount(); c++) {
            TableDataPath path = desc.getCellDataPath(r, c);

            if(path != null && path.getPath().length > 0 &&
               CALC_PATH.equals(path.getPath()[path.getPath().length - 1]))
            {
               map.put(Arrays.toString(f.getRowTuple(r).getRow()) + " x " +
                          Arrays.toString(f.getColTuple(c).getRow()), f.getObject(r, c));
            }
         }
      }

      return map;
   }

   private static boolean same(Object want, Object have) {
      if(want == null || have == null) {
         return want == have;
      }

      if(want instanceof Number && have instanceof Number) {
         double w = ((Number) want).doubleValue();
         return Math.abs(w - ((Number) have).doubleValue()) < 1e-9 * Math.max(1, Math.abs(w));
      }

      return want.equals(have);
   }
}
