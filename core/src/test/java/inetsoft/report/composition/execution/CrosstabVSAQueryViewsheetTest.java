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

package inetsoft.report.composition.execution;

import inetsoft.report.TableLens;
import inetsoft.report.composition.graph.calc.PercentCalc;
import inetsoft.report.filter.*;
import inetsoft.sree.SreeEnv;
import inetsoft.test.*;
import inetsoft.test.dc.DcInMemoryViewsheetFixture;
import inetsoft.uql.XConstants;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.Tool;
import inetsoft.util.UserMessage;

import java.sql.Date;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static inetsoft.report.composition.execution.CrosstabVSAQuerySupport.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;

/**
 * Crosstab of a viewsheet, read through the real entry {@code box.getData(name)}
 * (VSAQuery.createQuery, CrosstabVSAQuery, AbstractCrosstabVSAQuery, CrossTabFilter) over an
 * in-memory worksheet table. Slow: the first viewsheet costs about 9 s of cold start, like the
 * date comparison viewsheet tests that use the same fixture.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  LibManagerTestConfiguration.class, PluginsTestConfiguration.class,
                                  DcInMemoryViewsheetFixture.Config.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("slow")
public class CrosstabVSAQueryViewsheetTest {
   private final DcInMemoryViewsheetFixture fixture = new DcInMemoryViewsheetFixture();
   private final Map<String, String> savedProperties = new HashMap<>();

   @BeforeEach
   void setUp() {
      fixture.setUp();
      Tool.clearUserMessage();

      for(String name : new String[] { "table.output.maxrow", "max.col.count", "week.start" }) {
         savedProperties.put(name, SreeEnv.getProperty(name));
      }
   }

   @AfterEach
   void tearDown() {
      savedProperties.forEach((name, value) -> {
         if(value == null) {
            SreeEnv.remove(name);
         }
         else {
            SreeEnv.setProperty(name, value);
         }
      });
      Tool.clearWeekStartCache();
      Tool.clearUserMessage();
      fixture.tearDown();
   }

   private DcInMemoryViewsheetFixture.RuntimeSheet open(List<Object[]> rows) throws Exception {
      return fixture.openRuntime(NAMES, TYPES, rows);
   }

   // ---- plain crosstab on string dimensions ----

   @Test
   void crosstabOnStringDimensionsHoldsTheSumOfTheBaseRowsInEveryCellAndTotal() throws Exception {
      List<Object[]> rows = salesRows();
      var sheet = open(rows);
      add(sheet, "Plain", new VSDimensionRef[] { dim("Region") }, new VSDimensionRef[] { dim("Product") },
          agg("Qty", AggregateFormula.SUM));
      List<List<Object>> body = body(data(sheet, "Plain"));
      String[] regions = { "E", "W" };
      String[] products = { "p1", "p2", "p3" };

      assertEquals(regions.length + 1, body.size(), "regions and the grand total row");

      for(int i = 0; i <= regions.length; i++) {
         assertEquals(products.length + 1, body.get(i).size(), "products and the total column");

         for(int j = 0; j <= products.length; j++) {
            List<Object[]> match = where(where(rows, 0, i < regions.length ? regions[i] : null),
                                         1, j < products.length ? products[j] : null);

            if(match.isEmpty()) {
               assertTrue(body.get(i).get(j) == null || "".equals(body.get(i).get(j)) ||
                          body.get(i).get(j) instanceof Number &&
                             ((Number) body.get(i).get(j)).doubleValue() == 0,
                          "empty cell " + i + "," + j);
            }
            else {
               assertNumber(sum(match, 3), body.get(i).get(j), "cell " + i + "," + j);
            }
         }
      }
   }

   // ---- percentage of group with one header level is the percentage of the grand total ----

   @ParameterizedTest(name = "byRow={0}")
   @ValueSource(booleans = { true, false })
   void percentOfGroupOnASingleHeaderLevelEqualsPercentOfGrandTotal(boolean byRow) throws Exception {
      var sheet = open(salesRows());
      List<List<List<Object>>> results = new ArrayList<>();

      for(int type : new int[] { XConstants.PERCENTAGE_OF_GROUP, XConstants.PERCENTAGE_OF_GRANDTOTAL }) {
         VSAggregateRef agg = agg("Qty", AggregateFormula.SUM);
         agg.setPercentageOption(type);
         String name = "Pct" + type + byRow;
         CrosstabVSAssembly x = add(sheet, name,
                                    byRow ? new VSDimensionRef[] { dim("Region") }
                                       : new VSDimensionRef[] { dim("Region"), dim("Product") },
                                    byRow ? new VSDimensionRef[] { dim("Product") }
                                       : new VSDimensionRef[] { dim("Grade") }, agg);
         x.getVSCrosstabInfo().setPercentageByValue(
            (byRow ? XConstants.PERCENTAGE_BY_ROW : XConstants.PERCENTAGE_BY_COL) + "");
         results.add(body(data(sheet, name)));
      }

      assertFalse(results.get(0).isEmpty());
      assertEquals(results.get(1), results.get(0));
   }

   @Test
   void percentOfGroupWithTwoRowHeaderLevelsDiffersFromPercentOfGrandTotal() throws Exception {
      var sheet = open(salesRows());
      List<List<List<Object>>> results = new ArrayList<>();

      for(int type : new int[] { XConstants.PERCENTAGE_OF_GROUP, XConstants.PERCENTAGE_OF_GRANDTOTAL }) {
         VSAggregateRef agg = agg("Qty", AggregateFormula.SUM);
         agg.setPercentageOption(type);
         String name = "PctTwo" + type;
         CrosstabVSAssembly x = add(sheet, name,
                                    new VSDimensionRef[] { dim("Region"), dim("Product") },
                                    new VSDimensionRef[] { dim("Grade") }, agg);
         x.getVSCrosstabInfo().setPercentageByValue(XConstants.PERCENTAGE_BY_ROW + "");
         results.add(body(data(sheet, name)));
      }

      assertNotEquals(results.get(1), results.get(0));
   }

   // ---- aggregate formulas: the value does not depend on whether totals are shown ----

   private void assertFormulaDetailIsIndependentOfTotals(String name, VSAggregateRef agg,
                                                         Function<List<Object[]>, Object> oracle)
      throws Exception
   {
      List<Object[]> rows = salesRows();
      var sheet = open(rows);
      CrosstabVSAssembly off = add(sheet, name + "Off", new VSDimensionRef[] { dim("Region") },
                                   new VSDimensionRef[] { dim("Product") }, agg);
      off.getVSCrosstabInfo().setRowTotalVisibleValue("false");
      off.getVSCrosstabInfo().setColTotalVisibleValue("false");
      add(sheet, name + "On", new VSDimensionRef[] { dim("Region") },
          new VSDimensionRef[] { dim("Product") }, (VSAggregateRef) agg.clone());
      List<List<Object>> without = body(data(sheet, name + "Off"));
      List<List<Object>> with = body(data(sheet, name + "On"));
      String[] regions = { "E", "W" };
      String[] products = { "p1", "p2", "p3" };
      int checked = 0;

      assertEquals(regions.length, without.size(), "no grand total row");

      for(int i = 0; i < regions.length; i++) {
         for(int j = 0; j < products.length; j++) {
            List<Object[]> match = where(where(rows, 0, regions[i]), 1, products[j]);

            if(match.isEmpty()) {
               continue;
            }

            Object expected = oracle.apply(match);

            for(Object actual : new Object[] { without.get(i).get(j), with.get(i).get(j) }) {
               if(expected instanceof Number) {
                  assertNumber(((Number) expected).doubleValue(), actual,
                               name + " " + regions[i] + products[j]);
               }
               else {
                  assertEquals(expected, actual, name + " " + regions[i] + products[j]);
               }
            }

            checked++;
         }
      }

      assertEquals(5, checked);
   }

   private static Object feed(Formula formula, List<Object[]> rows, int col, int col2) {
      for(Object[] r : rows) {
         formula.addValue(col2 < 0 ? r[col] : new Object[] { r[col], r[col2] });
      }

      return formula.getResult();
   }

   @Test
   void standardDeviationCellsEqualTheDeviationOfTheRowsWithOrWithoutTotals() throws Exception {
      assertFormulaDetailIsIndependentOfTotals(
         "StdDev", agg("Qty", AggregateFormula.STANDARD_DEVIATION),
         rows -> feed(new StandardDeviationFormula(), rows, 3, -1));
   }

   @Test
   void productCellsEqualTheProductOfTheRowsWithOrWithoutTotals() throws Exception {
      assertFormulaDetailIsIndependentOfTotals("Product", agg("Qty", AggregateFormula.PRODUCT),
                                               rows -> rows.stream().mapToDouble(
                                                  r -> ((Number) r[3]).doubleValue())
                                                  .reduce(1, (a, b) -> a * b));
   }

   @Test
   void concatCellsEqualTheConcatOfTheRowsWithOrWithoutTotals() throws Exception {
      assertFormulaDetailIsIndependentOfTotals("Concat", agg("Qty", AggregateFormula.CONCAT),
                                               rows -> feed(new ConcatFormula(), rows, 3, -1));
   }

   @Test
   void covarianceCellsEqualTheCovarianceOfTheRowsWithOrWithoutTotals() throws Exception {
      VSAggregateRef agg = agg("Qty", AggregateFormula.COVARIANCE);
      ColumnRef second = new ColumnRef(new AttributeRef(null, "Price"));
      second.setDataType(XSchema.DOUBLE);
      agg.setSecondaryColumnValue("Price");
      agg.setSecondaryColumn(second);
      assertFormulaDetailIsIndependentOfTotals(
         "Covariance", agg, rows -> feed(new CovarianceFormula(4), rows, 3, 4));
   }

   @Test
   void secondSmallestCellsEqualTheSecondSmallestOfTheRowsWithOrWithoutTotals() throws Exception {
      VSAggregateRef agg = agg("Qty", AggregateFormula.NTH_SMALLEST);
      agg.setN(2);
      assertFormulaDetailIsIndependentOfTotals("NthSmallest", agg, rows -> {
         List<Double> values = new ArrayList<>();
         rows.forEach(r -> values.add(((Number) r[3]).doubleValue()));
         Collections.sort(values);
         return values.get(1);
      });
   }

   // ---- maximum rows and columns of the table output ----

   private static int rowLimitedSpanHeight(TableLens lens, int r) {
      java.awt.Dimension span = lens.getSpan(r, 0);
      return span == null ? 1 : span.height;
   }

   @Test
   void tableOutputMaxRowCutsTheRowsAndShortensTheSpanThatCrossesTheLimit() throws Exception {
      var sheet = open(salesRows());
      add(sheet, "Full", new VSDimensionRef[] { dim("Region"), dim("Product") },
          new VSDimensionRef[] { dim("Grade") }, agg("Qty", AggregateFormula.SUM));
      TableLens full = data(sheet, "Full");
      int total = full.getRowCount();
      // the setting counts body rows: the header rows are kept on top of it
      int bodyLimit = 4;
      int limit = full.getHeaderRowCount() + bodyLimit;
      assertTrue(limit < total, "the limit is below the row count");

      SreeEnv.setProperty("table.output.maxrow", String.valueOf(bodyLimit));
      add(sheet, "Limited", new VSDimensionRef[] { dim("Region"), dim("Product") },
          new VSDimensionRef[] { dim("Grade") }, agg("Qty", AggregateFormula.SUM));
      TableLens cut = data(sheet, "Limited");
      boolean crossing = false;

      assertEquals(limit, cut.getRowCount());

      for(int r = 0; r < limit; r++) {
         for(int c = 0; c < cut.getColCount(); c++) {
            assertEquals(full.getObject(r, c), cut.getObject(r, c), "cell " + r + "," + c);
         }

         int height = rowLimitedSpanHeight(full, r);
         java.awt.Dimension span = full.getSpan(r, 0);

         if(span != null && r + height > limit) {
            crossing = true;
            assertEquals(Math.max(1, limit - r), rowLimitedSpanHeight(cut, r), "span at row " + r);
         }
         else if(span != null) {
            assertEquals(height, rowLimitedSpanHeight(cut, r), "span at row " + r);
         }
      }

      assertTrue(crossing, "a span crosses the limit");
   }

   @Test
   void tableOutputMaxRowAboveTheRowCountCutsNothing() throws Exception {
      var sheet = open(salesRows());
      SreeEnv.setProperty("table.output.maxrow", "1000");
      add(sheet, "NotReached", new VSDimensionRef[] { dim("Region") },
          new VSDimensionRef[] { dim("Product") }, agg("Qty", AggregateFormula.SUM));
      TableLens lens = data(sheet, "NotReached");

      assertEquals(lens.getHeaderRowCount() + 3, lens.getRowCount(), "two regions and the total");
   }

   @ParameterizedTest(name = "alsoLimitRows={0}")
   @ValueSource(booleans = { false, true })
   void maxColumnCountCutsTheColumnsAndQueuesAUserMessage(boolean alsoLimitRows) throws Exception {
      var sheet = open(salesRows());
      add(sheet, "Wide" + alsoLimitRows, new VSDimensionRef[] { dim("Region") },
          new VSDimensionRef[] { dim("Product"), dim("Grade") },
          agg("Qty", AggregateFormula.SUM));
      TableLens full = data(sheet, "Wide" + alsoLimitRows);
      int limit = full.getColCount() - 3;
      Tool.clearUserMessage();

      if(alsoLimitRows) {
         SreeEnv.setProperty("table.output.maxrow",
                             String.valueOf(full.getRowCount() - full.getHeaderRowCount() - 1));
      }

      SreeEnv.setProperty("max.col.count", String.valueOf(limit));
      add(sheet, "Narrow" + alsoLimitRows, new VSDimensionRef[] { dim("Region") },
          new VSDimensionRef[] { dim("Product"), dim("Grade") },
          agg("Qty", AggregateFormula.SUM));
      TableLens cut = data(sheet, "Narrow" + alsoLimitRows);

      assertEquals(limit, cut.getColCount());
      UserMessage message = Tool.getUserMessage();
      assertNotNull(message, "the user is told the columns were cut");
      assertTrue(message.getMessage().contains(
         DataVSAQuery.getColumnLimitNotification()), message.getMessage());
   }

   @Test
   void maxColumnCountIsNotAppliedWhenTheQueryIsNotColumnLimited() throws Exception {
      var sheet = open(salesRows());
      add(sheet, "NoLimit", new VSDimensionRef[] { dim("Region") },
          new VSDimensionRef[] { dim("Product"), dim("Grade") }, agg("Qty", AggregateFormula.SUM));
      TableLens full = data(sheet, "NoLimit");
      SreeEnv.setProperty("max.col.count", String.valueOf(full.getColCount() - 3));
      TableLens limited = (TableLens) new CrosstabVSAQuery(
         sheet.box(), "NoLimit", false, true).getTableLens();
      TableLens unlimited = (TableLens) new CrosstabVSAQuery(
         sheet.box(), "NoLimit", false, false).getTableLens();
      limited.moreRows(Integer.MAX_VALUE);
      unlimited.moreRows(Integer.MAX_VALUE);

      assertEquals(full.getColCount() - 3, limited.getColCount());
      assertEquals(full.getColCount(), unlimited.getColCount());
   }

   @Test
   void dimensionSortedByTheValueOfAPercentCalculatedMeasureFollowsTheSums() throws Exception {
      List<Object[]> rows = salesRows();
      var sheet = open(rows);
      VSAggregateRef sum = agg("Qty", AggregateFormula.SUM);
      PercentCalc calc = new PercentCalc();
      calc.setLevel(PercentCalc.GRAND_TOTAL);
      sum.setCalculator(calc);
      VSDimensionRef product = dim("Product");
      product.setOrder(XConstants.SORT_VALUE_DESC);
      product.setSortByColValue(sum.getFullName());
      add(sheet, "SortedByValue", new VSDimensionRef[] { product }, new VSDimensionRef[0], sum);
      TableLens lens = data(sheet, "SortedByValue");
      List<String> order = new ArrayList<>();

      for(int r = lens.getHeaderRowCount(); r < lens.getRowCount() - 1; r++) {
         order.add(String.valueOf(lens.getObject(r, 0)));
      }

      List<String> expected = new ArrayList<>(List.of("p1", "p2", "p3"));
      expected.sort(Comparator.comparingDouble(
         (String p) -> sum(where(rows, 1, p), 3)).reversed());
      assertEquals(expected, order);
   }

   // ---- DayOfWeek honours week.start ----

   private List<Integer> weekdays(String weekStart, int level, boolean onColumns) throws Exception {
      SreeEnv.setProperty("week.start", weekStart);
      Tool.clearWeekStartCache();
      List<Object[]> rows = new ArrayList<>();

      for(int d = 1; d <= 7; d++) {
         rows.add(new Object[] { "E", "p1", "g1", d, 1.0, Date.valueOf(LocalDate.of(2024, 1, d)) });
      }

      var sheet = open(rows);
      String name = "Days" + weekStart + level + onColumns;
      VSDimensionRef day = dateDim("Day", level);
      add(sheet, name, onColumns ? new VSDimensionRef[0] : new VSDimensionRef[] { day },
          onColumns ? new VSDimensionRef[] { day } : new VSDimensionRef[0],
          agg("Qty", AggregateFormula.SUM));
      TableLens lens = data(sheet, name);
      List<Integer> days = new ArrayList<>();
      int count = onColumns ? lens.getColCount() - 1 : lens.getRowCount() - 1;

      for(int i = onColumns ? lens.getHeaderColCount() : lens.getHeaderRowCount(); i < count; i++) {
         Object value = onColumns ? lens.getObject(0, i) : lens.getObject(i, 0);
         days.add(value instanceof Number n ? n.intValue() : dayNumber(String.valueOf(value)));
      }

      return days;
   }

   private static int dayNumber(String name) {
      for(java.time.DayOfWeek day : java.time.DayOfWeek.values()) {
         if(name.equalsIgnoreCase(day.getDisplayName(java.time.format.TextStyle.FULL,
                                                      Locale.ENGLISH)))
         {
            return day.getValue() % 7 + 1;
         }
      }

      return -1;
   }

   @ParameterizedTest(name = "onColumns={0}")
   @ValueSource(booleans = { false, true })
   void dayOfWeekHeadersStartOnTheConfiguredWeekStart(boolean onColumns) throws Exception {
      // Calendar numbering: Sunday = 1 .. Saturday = 7
      assertEquals(List.of(2, 3, 4, 5, 6, 7, 1),
                   weekdays("monday", XConstants.DAY_OF_WEEK_DATE_GROUP, onColumns));
      assertEquals(List.of(1, 2, 3, 4, 5, 6, 7),
                   weekdays("sunday", XConstants.DAY_OF_WEEK_DATE_GROUP, onColumns));
   }

   @ParameterizedTest(name = "onColumns={0}")
   @ValueSource(booleans = { false, true })
   void monthOfYearHeadersAreNotAffectedByWeekStart(boolean onColumns) throws Exception {
      assertEquals(weekdays("sunday", XConstants.MONTH_OF_YEAR_DATE_GROUP, onColumns),
                   weekdays("monday", XConstants.MONTH_OF_YEAR_DATE_GROUP, onColumns));
   }

   // ---- summary-only crosstab ----

   @Test
   void crosstabWithoutHeadersIsOneRowWithTheSumOfTheBaseRows() throws Exception {
      List<Object[]> rows = salesRows();
      var sheet = open(rows);
      add(sheet, "SummaryOnly", new VSDimensionRef[0], new VSDimensionRef[0],
          agg("Qty", AggregateFormula.SUM));
      TableLens lens = data(sheet, "SummaryOnly");

      assertEquals(lens.getHeaderRowCount() + 1, lens.getRowCount());
      assertNumber(sum(rows, 3), lens.getObject(lens.getRowCount() - 1, 0), "the sum");
   }

   @Test
   void crosstabWithoutHeadersAndWithFormulaNoneIsThePlainColumn() throws Exception {
      List<Object[]> rows = salesRows();
      var sheet = open(rows);
      add(sheet, "PlainColumn", new VSDimensionRef[0], new VSDimensionRef[0],
          agg("Qty", AggregateFormula.NONE));
      TableLens lens = data(sheet, "PlainColumn");

      assertEquals(rows.size(), lens.getRowCount() - lens.getHeaderRowCount());

      for(int i = 0; i < rows.size(); i++) {
         assertNumber(((Number) rows.get(i)[3]).doubleValue(),
                      lens.getObject(lens.getHeaderRowCount() + i, 0), "row " + i);
      }
   }

   // ---- subtotal and grand total switches ----

   @ParameterizedTest(name = "subtotals={0}")
   @ValueSource(booleans = { true, false })
   void subtotalAndGrandTotalSwitchesAddTheTotalRowsAndColumnsTheyName(boolean subtotals)
      throws Exception
   {
      var sheet = open(salesRows());
      int regions = 2;
      int regionProducts = 5;
      int grades = 2;

      for(boolean rowGrand : new boolean[] { true, false }) {
         for(boolean colGrand : new boolean[] { true, false }) {
            VSDimensionRef region = dim("Region");
            region.setSubTotalVisibleValue(subtotals + "");
            VSDimensionRef product = dim("Product");
            String name = "Totals" + subtotals + rowGrand + colGrand;
            CrosstabVSAssembly x = add(sheet, name, new VSDimensionRef[] { region, product },
                                       new VSDimensionRef[] { dim("Grade") },
                                       agg("Qty", AggregateFormula.SUM));
            x.getVSCrosstabInfo().setRowTotalVisibleValue(rowGrand + "");
            x.getVSCrosstabInfo().setColTotalVisibleValue(colGrand + "");
            List<List<Object>> body = body(data(sheet, name));

            assertEquals(regionProducts + (subtotals ? regions : 0) + (rowGrand ? 1 : 0),
                         body.size(), name + " rows");
            assertEquals(grades + (colGrand ? 1 : 0), body.get(0).size(), name + " columns");
         }
      }
   }

   // ---- runtime sort of a measure column ----

   @Test
   void sortInfoOnAMeasureColumnOrdersTheDataRowsDescendingAndKeepsHeaderAndTotal()
      throws Exception
   {
      List<Object[]> rows = salesRows();
      var sheet = open(rows);
      CrosstabVSAssembly x = add(sheet, "SortedMeasure", new VSDimensionRef[] { dim("Product") },
                                 new VSDimensionRef[0], agg("Qty", AggregateFormula.SUM));
      SortInfo sortInfo = new SortInfo();
      SortRef sort = new SortRef(new ColumnRef(new AttributeRef(null, "Sum(Qty)")));
      sort.setOrder(XConstants.SORT_DESC);
      sortInfo.addSort(sort);
      ((TableDataVSAssemblyInfo) x.getInfo()).setSortInfo(sortInfo);
      TableLens lens = data(sheet, "SortedMeasure");
      int first = lens.getHeaderRowCount();
      int last = lens.getRowCount() - 1;
      List<String> order = new ArrayList<>();

      for(int r = first; r < last; r++) {
         order.add(String.valueOf(lens.getObject(r, 0)));
      }

      List<String> expected = new ArrayList<>(List.of("p1", "p2", "p3"));
      expected.sort(Comparator.comparingDouble((String p) -> sum(where(rows, 1, p), 3)).reversed());

      assertEquals(expected, order, "data rows by Sum(Qty) descending");
      assertNumber(sum(rows, 3), lens.getObject(last, lens.getColCount() - 1),
                   "the grand total row stays last");
   }

   // ---- a changed setting is applied on the next read ----

   @Test
   void readingAgainAfterTurningTheGrandTotalOffEqualsAFreshCrosstabWithItOff() throws Exception {
      var sheet = open(salesRows());
      CrosstabVSAssembly x = add(sheet, "Changed", new VSDimensionRef[] { dim("Region") },
                                 new VSDimensionRef[] { dim("Product") },
                                 agg("Qty", AggregateFormula.SUM));
      List<List<Object>> before = body(data(sheet, "Changed"));
      x.getVSCrosstabInfo().setRowTotalVisibleValue("false");
      sheet.box().resetDataMap("Changed");
      List<List<Object>> after = body(data(sheet, "Changed"));

      CrosstabVSAssembly fresh = add(sheet, "Fresh", new VSDimensionRef[] { dim("Region") },
                                     new VSDimensionRef[] { dim("Product") },
                                     agg("Qty", AggregateFormula.SUM));
      fresh.getVSCrosstabInfo().setRowTotalVisibleValue("false");

      assertEquals(before.size() - 1, after.size());
      assertEquals(body(data(sheet, "Fresh")), after);
   }
}
