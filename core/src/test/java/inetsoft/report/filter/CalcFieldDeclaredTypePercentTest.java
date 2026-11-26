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

import inetsoft.report.StyleConstants;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.report.composition.graph.BrushSummaryFilter;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.test.*;
import inetsoft.uql.XConstants;
import inetsoft.uql.asset.Worksheet;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #82427: an aggregate calc field with a declared data type (e.g. Integer) shown as a
 * percentage divided its unconverted result by a total converted to the declared type
 * (1.2 / trunc(1.2) = 120%). A percentage is computed against the unconverted total.
 *
 * <p>Data: CA/LA a=5 b=2 (2.5), CA/SF a=0 b=2 (0), OH/Cleveland a=1.01 b=1 (1.01);
 * CA total 5/4 = 1.25, grand total 6.01/5 = 1.202.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class CalcFieldDeclaredTypePercentTest {
   @BeforeEach
   void setUp() {
      box = new AssetQuerySandbox(new Worksheet());
   }

   @AfterEach
   void tearDown() {
      box.dispose();
   }

   @Test
   void percentageTotalIsNotConvertedToTheDeclaredType() {
      CalcFieldFormula formula = ratio(XConstants.PERCENTAGE_OF_GRANDTOTAL);
      formula.addValue(new Object[] { 6.0, 6.0, 5.0 });

      assertEquals(1, formula.getOriginalResult());
      assertEquals(1.2, ((Number) formula.getPercentageTotal()).doubleValue(), 1e-9);

      formula.setTotal(formula.getPercentageTotal());
      assertEquals(1.0, ((Number) formula.getResult()).doubleValue(), 1e-9);
      // the plain value keeps the declared type
      assertEquals(1, formula.getOriginalResult());
   }

   @Test
   void crosstabPercentOfGrandTotal() {
      Map<String, Object> cells = crosstab(DATA, XConstants.PERCENTAGE_OF_GRANDTOTAL);

      assertPercent(1.0, cells, "Grand Total");
      assertPercent(2.5 / 1.202, cells, "CA/LA");
      assertPercent(1.25 / 1.202, cells, "CA/Total");
      assertPercent(1.01 / 1.202, cells, "OH/Cleveland");
   }

   @Test
   void crosstabPercentOfGroup() {
      Map<String, Object> cells = crosstab(DATA, XConstants.PERCENTAGE_OF_GROUP);

      assertPercent(2.5 / 1.25, cells, "CA/LA");
      // the only member of its group is 100% of the group
      assertPercent(1.0, cells, "OH/Cleveland");
   }

   @Test
   void crosstabTotalBelowOneIsNotZero() {
      Map<String, Object> cells = crosstab(BELOW_ONE, XConstants.PERCENTAGE_OF_GRANDTOTAL);

      // grand total 3 / 5 = 0.6, which an Integer truncates to 0
      assertPercent(1.0, cells, "Grand Total");
      assertPercent(0.25 / 0.6, cells, "X/x1");
      assertPercent(2.0 / 0.6, cells, "X/x2");
   }

   @Test
   void summaryPercentOfGrandTotal() {
      SummaryFilter filter = summary(DATA, XConstants.PERCENTAGE_OF_GRANDTOTAL);
      Map<String, Object> cells = read(filter);

      assertPercent(2.5 / 1.202, cells, "CA/LA");
      assertPercent(1.01 / 1.202, cells, "OH/Cleveland");
      assertPercent(1.0, cells, "Grand Total");
   }

   @Test
   void summaryPercentOfGroup() {
      Map<String, Object> cells = read(summary(DATA, XConstants.PERCENTAGE_OF_GROUP));

      assertPercent(2.5 / 1.25, cells, "CA/LA");
      assertPercent(1.0, cells, "OH/Cleveland");
   }

   @Test
   void summaryTotalBelowOneIsNotZero() {
      Map<String, Object> cells = read(summary(BELOW_ONE, XConstants.PERCENTAGE_OF_GRANDTOTAL));

      assertPercent(0.25 / 0.6, cells, "X/x1");
      assertPercent(1.0, cells, "Grand Total");
   }

   /** A brushed percent is rescaled to the percent of the whole data's total. */
   @Test
   void brushedPercentOfGrandTotal() {
      SummaryFilter all = summary(DATA, XConstants.PERCENTAGE_OF_GRANDTOTAL);
      // brushed: LA and Cleveland, grand total (5 + 1.01) / (2 + 1) = 2.00333
      SummaryFilter brushed = summary(new Object[][] {
         DATA[0], DATA[1], DATA[3]
      }, XConstants.PERCENTAGE_OF_GRANDTOTAL);
      BrushSummaryFilter filter = new BrushSummaryFilter(all, brushed);
      filter.moreRows(Integer.MAX_VALUE);
      Map<String, Object> cells = read(filter, brushed);

      assertPercent(2.5 / 1.202, cells, "CA/LA");
      assertPercent(1.01 / 1.202, cells, "OH/Cleveland");
   }

   private CalcFieldFormula ratio(int percentageType) {
      CalcFieldFormula formula = new CalcFieldFormula(
         SUM_A + " / " + SUM_B, new String[] { SUM_A, SUM_B },
         new Formula[] { new SumFormula(), new SumFormula() }, new int[] { 2, 3 },
         box.getScriptEnv(), box.getScope());
      formula.setDeclaredType("integer");
      formula.setPercentageType(percentageType);
      return formula;
   }

   /** Row headers State, City; one measure; group totals and the grand total shown. */
   private Map<String, Object> crosstab(Object[][] data, int percentageType) {
      CrossTabFilter f = new CrossTabFilter(new DefaultTableLens(data), new int[] { 0, 1 },
                                            new int[0], new int[] { 2 },
                                            new Formula[] { ratio(percentageType) });
      f.setRowHeaders(List.of("State", "City"));
      f.setSuppressRowGroupTotal(false, 0);
      f.setSuppressRowGrandTotal(false);
      f.setPercentageDirection(XConstants.PERCENTAGE_BY_ROW);
      f.moreRows(Integer.MAX_VALUE);

      Map<String, Object> cells = new LinkedHashMap<>();
      int dcol = f.getColCount() - 1;

      for(int r = f.getHeaderRowCount(); r < f.getRowCount(); r++) {
         Object state = f.getObject(r, 0);
         Object city = f.getObject(r, 1);
         String key = f.isGrandTotalRow(r) ? "Grand Total" :
            f.isTotalRow(r) ? state + "/Total" : state + "/" + city;
         cells.put(key, f.getObject(r, dcol));
      }

      return cells;
   }

   /** Group columns State, City; the grand formula has the same percentage type. */
   private SummaryFilter summary(Object[][] data, int percentageType) {
      SummaryFilter filter = new SummaryFilter(new DefaultTableLens(data), new int[] { 0, 1 },
         new int[] { 2 }, new Formula[] { ratio(percentageType) },
         new Formula[] { ratio(percentageType) });
      filter.moreRows(Integer.MAX_VALUE);
      return filter;
   }

   private static Map<String, Object> read(SummaryFilter filter) {
      return read(filter, filter);
   }

   /** Values of the measure column read from table, keyed by the rows of filter. */
   private static Map<String, Object> read(inetsoft.report.TableLens table,
                                           SummaryFilter filter)
   {
      Map<String, Object> cells = new LinkedHashMap<>();
      int dcol = filter.getColCount() - 1;

      for(int r = filter.getHeaderRowCount(); filter.moreRows(r); r++) {
         Object state = filter.getObject(r, 0);
         Object city = filter.getObject(r, 1);
         String key = city == null ? "Grand Total" : state + "/" + city;
         cells.put(key, table.getObject(r, dcol));
      }

      return cells;
   }

   private static void assertPercent(double expected, Map<String, Object> cells, String key) {
      Object value = cells.get(key);
      assertInstanceOf(Number.class, value, key + " in " + cells);
      assertEquals(expected, ((Number) value).doubleValue(), 1e-9, key + " in " + cells);
   }

   private static final String SUM_A = "field['Sum([a])']";
   private static final String SUM_B = "field['Sum([b])']";
   private static final Object[][] DATA = {
      { "State", "City", "a", "b" },
      { "CA", "LA", 5.0, 2.0 },
      { "CA", "SF", 0.0, 2.0 },
      { "OH", "Cleveland", 1.01, 1.0 }
   };
   private static final Object[][] BELOW_ONE = {
      { "State", "City", "a", "b" },
      { "X", "x1", 1.0, 4.0 },
      { "X", "x2", 2.0, 1.0 }
   };
   private AssetQuerySandbox box;
}
