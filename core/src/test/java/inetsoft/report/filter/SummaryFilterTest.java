/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.schema.XSchema;
import inetsoft.util.swap.SwapFileReadException;
import inetsoft.util.swap.SwapLostTestSupport.LostTable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Tag;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;

import static inetsoft.util.swap.SwapLostTestSupport.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome()
@Tag("core")
public class SummaryFilterTest {
   @Test
   public void simpleGroup() {
      DefaultTableLens tbl1 = new DefaultTableLens(new Object[][] {
         {"col1", "col2", "col3"},
         {"a", 1, 5},
         {"a", 1, 2},
         {"b", 3, 10},
         {"b", 1, 2.5},
         {"c", 1, 3}
      });

      final SummaryFilter summary =
         new SummaryFilter(tbl1, new int[] {0, 1}, new int[] {2}, new SumFormula(), null);
      summary.moreRows(Integer.MAX_VALUE);

      XTableUtil.assertEquals(summary, new Object[][] {
         {"col1", "col2", "col3"},
         {"a", 1, 7.0},
         {"b", 3, 10.0},
         {"b", 1, 2.5},
         {"c", 1, 3.0}
      });
   }

   // Bug #79768: a summary with no aggregation shows the first value of the group
   @Test
   public void noneFormulaKeepsFirstValue() {
      DefaultTableLens tbl1 = new DefaultTableLens(new Object[][] {
         {"col1", "col2"},
         {"a", 10},
         {"a", 5},
         {"b", 7},
         {"b", 6}
      });

      final SummaryFilter summary = new SummaryFilter(
         tbl1, new int[] {0}, new int[] {1}, new DefaultFormula(), null);
      summary.moreRows(Integer.MAX_VALUE);

      XTableUtil.assertEquals(summary, new Object[][] {
         {"col1", "col2"},
         {"a", 10},
         {"b", 7}
      });
   }

   // Bug #78458: a cell set by setObject() must not make the other cells read the
   // "undefined" sentinel of the cell value matrix
   @Test
   public void setObjectKeepsTheOtherCells() {
      DefaultTableLens tbl1 = new DefaultTableLens(new Object[][] {
         {"col1", "col2", "col3"},
         {"a", 1, 5},
         {"a", 1, 2},
         {"b", 3, 10}
      });

      final SummaryFilter summary =
         new SummaryFilter(tbl1, new int[] {0, 1}, new int[] {2}, new SumFormula(), null);
      summary.moreRows(Integer.MAX_VALUE);
      summary.setObject(1, 0, "x");
      summary.setObject(2, 2, null);

      XTableUtil.assertEquals(summary, new Object[][] {
         {"col1", "col2", "col3"},
         {"x", 1, 7.0},
         {"b", 3, null}
      });
   }

   @Test
   public void topTwo() {
      DefaultTableLens tbl1 = new DefaultTableLens(new Object[][] {
         {"col1", "col2", "col3"},
         {"a", 1, 5},
         {"a", 1, 2},
         {"b", 1, 2.5},
         {"b", 3, 10},
         {"c", 1, 3}
      });

      final SummaryFilter summary =
         new SummaryFilter(tbl1, new int[] {0, 1}, new int[] {2}, new SumFormula(), null);
      summary.setTopN(0, 0, 2, false);
      summary.moreRows(Integer.MAX_VALUE);

      XTableUtil.assertEquals(summary, new Object[][] {
         {"col1", "col2", "col3"},
         {"b", 1, 2.5},
         {"b", 3, 10.0},
         {"a", 1, 7.0},
      });
   }

   @Test
   public void topTwoAndOthers() {
      DefaultTableLens tbl1 = new DefaultTableLens(new Object[][] {
         {"col1", "col2", "col3"},
         {"a", 1, 5},
         {"a", 1, 2},
         {"b", 1, 2.5},
         {"b", 3, 10},
         {"c", 1, 1},
         {"d", 2, 2},
         {"e", 1, 3}
      });

      final SummaryFilter summary =
         new SummaryFilter(tbl1, new int[] {0, 1}, new int[] {2}, new SumFormula(), null);
      summary.setTopN(0, 0, 2, false, true, true);
      summary.moreRows(Integer.MAX_VALUE);

      XTableUtil.assertEquals(summary, new Object[][] {
         {"col1", "col2", "col3"},
         {"b", 1, 2.5},
         {"b", 3, 10.0},
         {"a", 1, 7.0},
         {"Others", 1, 4.0},
         {"Others", 2, 2.0},
      });
   }

   @Test
   public void grandTotal() {
      DefaultTableLens tbl1 = new DefaultTableLens(new Object[][] {
         {"col1", "col2", "col3", "col4"},
         {"a", 1, 2, 1},
         {"a", 1, 2, 2},
         {"b", 1, 2.5, 3},
         {"b", 3, 10, 4},
         {"c", 1, 1, 5},
      });

      final Formula[] formulas = {new SumFormula(), new MaxFormula()};
      final SummaryFilter summary =
         new SummaryFilter(tbl1, new int[] {0, 1}, new int[] {2, 3}, formulas, formulas);
      summary.moreRows(Integer.MAX_VALUE);

      XTableUtil.assertEquals(summary, new Object[][] {
         {"col1", "col2", "col3", "col4"},
         {"a", 1, 4.0, 2},
         {"b", 1, 2.5, 3},
         {"b", 3, 10.0, 4},
         {"c", 1, 1.0, 5},
         {"Total", null, 17.5, 5},
      });
   }

   @Test
   public void pglvl() {
      DefaultTableLens tbl1 = new DefaultTableLens(new Object[][] {
         {"col1", "col2", "col3", "col4"},
         {"a", 1, 1, 1},
         {"a", 1, 2, 1},
         {"b", 1, 2.5, 2},
         {"b", 3, 10, 6},
         {"c", 1, 1, 5},
      });

      final SumFormula formula = new SumFormula();
      formula.setPercentageType(StyleConstants.PERCENTAGE_OF_GROUP);
      final Formula[] formulas = {formula};
      final SummaryFilter summary =
         new SummaryFilter(tbl1, new int[] {0, 1, 2}, new int[] {3}, formulas, null, false, 1);
      summary.moreRows(Integer.MAX_VALUE);

      XTableUtil.assertEquals(summary, new Object[][] {
         {"col1", "col2", "col3", "col4"},
         {"a", 1, 1, 0.5},
         {"a", 1, 2, 0.5},
         {"b", 1, 2.5, 0.25},
         {"b", 3, 10, .75},
         {"c", 1, 1, 1.0},
      });
   }

   @Test
   public void hierarchy() {
      DefaultTableLens tbl1 = new DefaultTableLens(new Object[][] {
         {"col1", "col2", "col3"},
         {"a", 1, 1},
         {"a", 1, 2},
         {"b", 1, 2.5},
         {"b", 3, 10},
         {"c", 1, 1},
      });

      final SumFormula formula = new SumFormula();
      final Formula[] formulas = {formula};
      final SummaryFilter summary =
         new SummaryFilter(tbl1, new int[] {0, 1}, new int[] {2}, formulas, null, true);
      summary.moreRows(Integer.MAX_VALUE);

      XTableUtil.assertEquals(summary, new Object[][] {
         {"col1", "col2", "col3"},
         {"a", 1, 3.0},
         {"a", null, 3.0},
         {"b", 1, 2.5},
         {"b", 3, 10.0},
         {"b", null, 12.5},
         {"c", 1, 1.0},
         {"c", null, 1.0},
      });
   }

   @Test
   public void sortByVal() {
      DefaultTableLens tbl1 = new DefaultTableLens(new Object[][] {
         {"col1", "col2", "col3"},
         {"a", 1, 1},
         {"a", 1, 2},
         {"b", 1, 2.5},
         {"b", 3, 10},
         {"c", 1, 1},
      });

      final SumFormula formula = new SumFormula();
      final Formula[] formulas = {formula};
      final SummaryFilter summary =
         new SummaryFilter(tbl1, new int[] {0, 1}, new int[] {2}, formulas, null);
      summary.setSortByValInfo(0, 0, false);
      summary.moreRows(Integer.MAX_VALUE);

      XTableUtil.assertEquals(summary, new Object[][] {
         {"col1", "col2", "col3"},
         {"b", 1, 2.5},
         {"b", 3, 10.0},
         {"a", 1, 3.0},
         {"c", 1, 1.0},
      });
   }

   @Test
   public void timeSeries() {
      DefaultTableLens tbl1 = new DefaultTableLens(new Object[][] {
         {"col1", "col2", "col3"},
         {"a", date("2021-01-03"), 1},
         {"a", date("2021-01-03"), 2},
         {"b", date("2021-01-10"), 2.5},
         {"b", date("2021-01-24"), 10},
         {"c", date("2021-01-24"), 1},
      });

      final SumFormula formula = new SumFormula();
      final Formula[] formulas = {formula};
      final SummaryFilter summary =
         new SummaryFilter(tbl1, new int[] {0, 1}, new int[] {2}, formulas, null);
      summary.setTimeSeries(true);
      summary.setTimeSeriesLevel(XConstants.WEEK_DATE_GROUP);
      summary.moreRows(Integer.MAX_VALUE);

      XTableUtil.assertEquals(summary, new Object[][] {
         {"col1", "col2", "col3"},
         {"a", date("2021-01-03"), 3.0},
         {"b", date("2021-01-10"), 2.5},
         {"b", date("2021-01-17"), null},
         {"b", date("2021-01-24"), 10.0},
         {"c", date("2021-01-24"), 1.0},
      });
   }

   @Test
   public void sortOrderGroupCondition() {
      DefaultTableLens tbl1 = new DefaultTableLens(new Object[][] {
         {"col1", "col2"},
         {"a", 1},
         {"a", 2},
         {"b", 2},
         {"b", 3},
         {"c", 1},
      });

      final SummaryFilter summary =
         new SummaryFilter(tbl1, new int[] {0, 1}, new int[0], (Formula) null, null);

      final Condition condition = new Condition();
      condition.setOperation(Condition.EQUAL_TO);
      condition.addValue(2);
      condition.setType(XSchema.INTEGER);
      final ConditionGroup conditionGroup = new ConditionGroup();
      conditionGroup.addCondition(1, condition, 0);

      final SortOrder sortOrder = new SortOrder(XConstants.SORT_SPECIFIC);
      sortOrder.addGroupCondition("Group", conditionGroup);
      sortOrder.setOthers(SortOrder.LEAVE_OTHERS);
      summary.setGroupOrder(1, sortOrder);
      summary.moreRows(Integer.MAX_VALUE);

      XTableUtil.assertEquals(summary, new Object[][] {
         {"col1", "col2"},
         {"a", "Group"},
         {"a", "1"},
         {"b", "Group"},
         {"b", "3"},
         {"c", "1"},
      });
   }

   @Test
   public void testSerialize() throws Exception {
      DefaultTableLens tbl1 = new DefaultTableLens(new Object[][] {
         {"col1", "col2", "col3"},
         {"a", 1, 5},
         {"a", 1, 2},
         {"b", 3, 10},
         {"b", 1, 2.5},
         {"c", 1, 3}
      });
      final SummaryFilter originalTable =
         new SummaryFilter(tbl1, new int[] {0, 1}, new int[] {2}, new SumFormula(), null);
      originalTable.moreRows(Integer.MAX_VALUE);

      XTable deserializedTable = TestSerializeUtils.serializeAndDeserialize(originalTable);

      Assertions.assertEquals(SummaryFilter.class, deserializedTable.getClass());
   }

   @Test
   public void testSerializeTimeSeries() throws Exception {
      DefaultTableLens tbl1 = new DefaultTableLens(new Object[][] {
         {"col1", "col2", "col3"},
         {"a", date("2021-01-03"), 1},
         {"a", date("2021-01-03"), 2},
         {"b", date("2021-01-10"), 2.5},
         {"b", date("2021-01-24"), 10},
         {"c", date("2021-01-24"), 1},
         });

      final SumFormula formula = new SumFormula();
      final Formula[] formulas = {formula};
      final SummaryFilter originalTable =
         new SummaryFilter(tbl1, new int[] {0, 1}, new int[] {2}, formulas, null);
      originalTable.setTimeSeries(true);
      originalTable.setTimeSeriesLevel(XConstants.WEEK_DATE_GROUP);
      originalTable.moreRows(Integer.MAX_VALUE);

      XTable deserializedTable = TestSerializeUtils.serializeAndDeserialize(originalTable);

      Assertions.assertEquals(SummaryFilter.class, deserializedTable.getClass());
   }

   @Test
   public void testSerializeSortOrderGroupCondition() throws Exception {
      DefaultTableLens tbl1 = new DefaultTableLens(new Object[][] {
         {"col1", "col2"},
         {"a", 1},
         {"a", 2},
         {"b", 2},
         {"b", 3},
         {"c", 1},
         });

      final SummaryFilter originalTable =
         new SummaryFilter(tbl1, new int[] {0, 1}, new int[0], (Formula) null, null);

      final Condition condition = new Condition();
      condition.setOperation(Condition.EQUAL_TO);
      condition.addValue(2);
      condition.setType(XSchema.INTEGER);
      final ConditionGroup conditionGroup = new ConditionGroup();
      conditionGroup.addCondition(1, condition, 0);

      final SortOrder sortOrder = new SortOrder(XConstants.SORT_SPECIFIC);
      sortOrder.addGroupCondition("Group", conditionGroup);
      sortOrder.setOthers(SortOrder.LEAVE_OTHERS);
      originalTable.setGroupOrder(1, sortOrder);
      originalTable.moreRows(Integer.MAX_VALUE);

      XTable deserializedTable = TestSerializeUtils.serializeAndDeserialize(originalTable);

      Assertions.assertEquals(SummaryFilter.class, deserializedTable.getClass());
   }

   /**
    * The rows merged into the "Others" bucket must be ordered by their own group level's
    * sort order, the same as every other group at that level. Gathering them from
    * several parent groups must not leave them in the order the parents were ranked in.
    *
    * This is the shape a chart with a top-N "sort by value" outer dimension produces, and
    * order-dependent calculations (running total, moving, change/previous) read the
    * resulting row order. (74910)
    */
   @Test
   public void othersBucketKeepsInnerGroupOrder() {
      // col1 is ranked by value so the outer groups are gathered out of col2 order;
      // "d"/"e"/"f"/"g" all fall outside the top 2 and merge into "Others"
      DefaultTableLens tbl1 = new DefaultTableLens(new Object[][] {
         {"col1", "col2", "col3"},
         {"a", 1, 50},
         {"b", 2, 40},
         {"d", 7, 4},
         {"e", 3, 3},
         {"f", 9, 2},
         {"g", 5, 1}
      });

      final SummaryFilter summary =
         new SummaryFilter(tbl1, new int[] {0, 1}, new int[] {2}, new SumFormula(), null);
      summary.setGroupOrder(1, new SortOrder(XConstants.SORT_ASC));
      summary.setTopN(0, 0, 2, false, true, true);
      summary.moreRows(Integer.MAX_VALUE);

      XTableUtil.assertEquals(summary, new Object[][] {
         {"col1", "col2", "col3"},
         {"a", 1, 50.0},
         {"b", 2, 40.0},
         {"Others", 3, 3.0},
         {"Others", 5, 1.0},
         {"Others", 7, 4.0},
         {"Others", 9, 2.0},
      });
   }

   /**
    * A descending inner group order must be honoured inside the "Others" bucket too.
    */
   @Test
   public void othersBucketKeepsInnerGroupOrderDesc() {
      DefaultTableLens tbl1 = new DefaultTableLens(new Object[][] {
         {"col1", "col2", "col3"},
         {"a", 1, 50},
         {"b", 2, 40},
         {"d", 7, 4},
         {"e", 3, 3},
         {"f", 9, 2},
         {"g", 5, 1}
      });

      final SummaryFilter summary =
         new SummaryFilter(tbl1, new int[] {0, 1}, new int[] {2}, new SumFormula(), null);
      summary.setGroupOrder(1, new SortOrder(XConstants.SORT_DESC));
      summary.setTopN(0, 0, 2, false, true, true);
      summary.moreRows(Integer.MAX_VALUE);

      XTableUtil.assertEquals(summary, new Object[][] {
         {"col1", "col2", "col3"},
         {"a", 1, 50.0},
         {"b", 2, 40.0},
         {"Others", 9, 2.0},
         {"Others", 7, 4.0},
         {"Others", 5, 1.0},
         {"Others", 3, 3.0},
      });
   }

   /**
    * With more than one group level below the "Others" level, the merged rows must be
    * ordered level by level (year, then name), not by the innermost level alone, so that
    * a running total broken by the outer dimension accumulates in group order. (78468)
    */
   @Test
   public void othersBucketKeepsIntermediateGroupOrder() {
      SummaryFilter summary = othersSummary(XConstants.SORT_ASC, XConstants.SORT_ASC);
      summary.moreRows(Integer.MAX_VALUE);

      assertOthersRows(summary, new Object[][] {
         {2001, "a", 3.0}, {2001, "b", 5.0}, {2001, "c", 1.0},
         {2002, "a", 2.0}, {2002, "b", 4.0},
         {2003, "a", 7.0}, {2003, "d", 6.0}
      });
   }

   /**
    * A descending intermediate group order is honoured inside "Others". (78468)
    */
   @Test
   public void othersBucketKeepsIntermediateGroupOrderDesc() {
      SummaryFilter summary = othersSummary(XConstants.SORT_DESC, XConstants.SORT_ASC);
      summary.moreRows(Integer.MAX_VALUE);

      assertOthersRows(summary, new Object[][] {
         {2003, "a", 7.0}, {2003, "d", 6.0},
         {2002, "a", 2.0}, {2002, "b", 4.0},
         {2001, "a", 3.0}, {2001, "b", 5.0}, {2001, "c", 1.0}
      });
   }

   /**
    * An intermediate level sorted by value is ordered inside "Others" by its total over
    * the "Others" bucket (2003 = 13, 2001 = 9, 2002 = 6). (78468)
    */
   @Test
   public void othersBucketSortsIntermediateLevelByValue() {
      SummaryFilter summary = othersSummary(XConstants.SORT_ASC, XConstants.SORT_ASC);
      summary.setSortByValInfo(1, 0, false);
      summary.moreRows(Integer.MAX_VALUE);

      assertOthersRows(summary, new Object[][] {
         {2003, "a", 7.0}, {2003, "d", 6.0},
         {2001, "a", 3.0}, {2001, "b", 5.0}, {2001, "c", 1.0},
         {2002, "a", 2.0}, {2002, "b", 4.0}
      });
   }

   /**
    * An unsorted innermost level keeps the data order inside each intermediate group of
    * "Others", instead of the order of the parents the rows were gathered from. (78468)
    */
   @Test
   public void othersBucketKeepsDataOrderOfUnsortedLevel() {
      SummaryFilter summary = othersSummary(XConstants.SORT_ASC, XConstants.SORT_NONE);
      summary.moreRows(Integer.MAX_VALUE);

      assertOthersRows(summary, new Object[][] {
         {2001, "c", 1.0}, {2001, "a", 3.0}, {2001, "b", 5.0},
         {2002, "a", 2.0}, {2002, "b", 4.0},
         {2003, "d", 6.0}, {2003, "a", 7.0}
      });
   }

   /**
    * With a top-N "Others" on the year level too, the years merged into the inner
    * "Others" of several types are still an "Others" group, so they stay last inside the
    * outer "Others" whatever the year order is. (78468)
    */
   @Test
   public void othersBucketKeepsStackedOthersLast() {
      SummaryFilter summary = othersSummary(XConstants.SORT_DESC, XConstants.SORT_ASC);
      summary.setTopN(1, 0, 1, false, false, true);
      summary.moreRows(Integer.MAX_VALUE);

      // B keeps 2003 and C keeps 2001; B/2001, B/2002, C/2002, C/2003 are the inner Others
      assertOthersRows(summary, new Object[][] {
         {2003, "d", 6.0},
         {2001, "a", 3.0}, {2001, "b", 5.0},
         {"Others", "a", 9.0}, {"Others", "b", 4.0}, {"Others", "c", 1.0}
      });
   }

   /**
    * Ordering the "Others" rows doesn't change their parent: a percentage of group is
    * still a percentage of the whole "Others" bucket (28). (78468)
    */
   @Test
   public void othersBucketPercentageOfGroupUnchanged() {
      SumFormula formula = new SumFormula();
      formula.setPercentageType(StyleConstants.PERCENTAGE_OF_GROUP);
      SummaryFilter summary = othersSummary(XConstants.SORT_ASC, XConstants.SORT_ASC, formula);
      summary.moreRows(Integer.MAX_VALUE);

      assertOthersRows(summary, new Object[][] {
         {2001, "a", 3 / 28.0}, {2001, "b", 5 / 28.0}, {2001, "c", 1 / 28.0},
         {2002, "a", 2 / 28.0}, {2002, "b", 4 / 28.0},
         {2003, "a", 7 / 28.0}, {2003, "d", 6 / 28.0}
      });
   }

   private static SummaryFilter othersSummary(int yearOrder, int nameOrder) {
      return othersSummary(yearOrder, nameOrder, new SumFormula());
   }

   /**
    * Type, year and name grouped, Top 1 type ("A", 200) with "Others" for "B" and "C".
    */
   private static SummaryFilter othersSummary(int yearOrder, int nameOrder, Formula formula) {
      DefaultTableLens tbl = new DefaultTableLens(new Object[][] {
         {"type", "year", "name", "v"},
         {"A", 2001, "a", 100},
         {"A", 2002, "b", 100},
         {"B", 2001, "c", 1},
         {"B", 2002, "a", 2},
         {"B", 2003, "d", 6},
         {"C", 2001, "a", 3},
         {"C", 2001, "b", 5},
         {"C", 2002, "b", 4},
         {"C", 2003, "a", 7}
      });

      SummaryFilter summary =
         new SummaryFilter(tbl, new int[] {0, 1, 2}, new int[] {3}, formula, null);
      summary.setGroupOrder(0, new SortOrder(XConstants.SORT_ASC));
      summary.setGroupOrder(1, new SortOrder(yearOrder));
      summary.setGroupOrder(2, new SortOrder(nameOrder));
      summary.setTopN(0, 0, 1, false, false, true);
      return summary;
   }

   /**
    * Assert the year, name and value of the "Others" rows, in order.
    */
   private static void assertOthersRows(SummaryFilter summary, Object[][] expected) {
      java.util.List<Object[]> rows = new java.util.ArrayList<>();

      for(int r = summary.getHeaderRowCount(); summary.moreRows(r); r++) {
         if("Others".equals(summary.getObject(r, 0))) {
            rows.add(new Object[] { summary.getObject(r, 1), summary.getObject(r, 2),
                                    summary.getObject(r, 3) });
         }
      }

      Assertions.assertEquals(expected.length, rows.size(), "Others rows");

      for(int i = 0; i < expected.length; i++) {
         Object[] row = rows.get(i);
         Assertions.assertEquals(expected[i][0], row[0], "year of Others row " + i);
         Assertions.assertEquals(expected[i][1], row[1], "name of Others row " + i);
         Assertions.assertEquals(((Number) expected[i][2]).doubleValue(),
                                 ((Number) row[2]).doubleValue(), 1e-9,
                                 "value of Others row " + i);
      }
   }

   private Date date(String date) {
      return new Date(LocalDate.parse(date).atStartOfDay()
                         .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
   }

   /**
    * A lost swap file of the base fails the read of the summary on its worker, it is never a
    * summary of the rows read before the failure (bug #77651).
    */
   @Test
   public void lostSwapFileOfTheBaseFailsTheRead() throws Exception {
      SwapFileReadException lost = swapLost();
      SummaryFilter summary = summary(new LostTable(values(40), 21, lost));

      assertSwapOf(lost, swapIn(failureOf(15, () -> drain(summary))));
   }

   /**
    * A wrapped lost swap file is found in the cause chain.
    */
   @Test
   public void wrappedLostSwapFileOfTheBaseFailsTheRead() throws Exception {
      SwapFileReadException lost = swapLost();
      SummaryFilter summary =
         summary(new LostTable(values(40), 21, new RuntimeException("wrapped", lost)));

      assertSwapOf(lost, swapIn(failureOf(15, () -> drain(summary))));
   }

   /**
    * A pass that completed because a cancel already ended the rows of its base (a query
    * manager cancels the lenses below the summary first) is cut short: the cancel of the
    * summary that follows stays a cancel (bug #78200).
    */
   @Test
   public void cancelAfterACancelledBaseEndedThePassIsACancel() {
      CancelledBase base = new CancelledBase(values(10));
      base.cancel();
      SummaryFilter summary = new SummaryFilter(new SortFilter(base, new int[] { 0 }),
                                                new int[] { 0 }, new int[] { 1 },
                                                new SumFormula(), new SumFormula());
      drain(summary);

      summary.cancel();
      Assertions.assertTrue(summary.isCancelled(), "the cut-short summary is not cancelled");
   }

   /**
    * The control: a cancel after the pass completed over a whole base is no cancel.
    */
   @Test
   public void cancelAfterThePassCompletedOverAWholeBaseIsNoCancel() {
      CancelledBase base = new CancelledBase(values(10));
      SummaryFilter summary = new SummaryFilter(new SortFilter(base, new int[] { 0 }),
                                                new int[] { 0 }, new int[] { 1 },
                                                new SumFormula(), new SumFormula());
      drain(summary);

      summary.cancel();
      Assertions.assertFalse(summary.isCancelled(), "the complete summary is cancelled");
   }

   /** A base whose cancel is recorded, as a query result whose rows a cancel ended. */
   private static final class CancelledBase extends DefaultTableLens
      implements inetsoft.report.internal.table.CancellableTableLens
   {
      CancelledBase(Object[][] data) {
         super(data);
      }

      @Override
      public void cancel() {
         cancelled = true;
      }

      @Override
      public boolean isCancelled() {
         return cancelled;
      }

      private volatile boolean cancelled;
   }

   private static SummaryFilter summary(DefaultTableLens base) {
      return new SummaryFilter(base, new int[] { 0 }, new int[] { 1 }, new SumFormula(),
                               new SumFormula());
   }

   /**
    * {@code rows} rows of {@code key, value}, key {@code k(i % 3)} and value {@code i} for
    * row {@code i}.
    */
   private static Object[][] values(int rows) {
      Object[][] data = new Object[rows + 1][];
      data[0] = new Object[] { "key", "value" };

      for(int r = 1; r <= rows; r++) {
         data[r] = new Object[] { "k" + (r % 3), r };
      }

      return data;
   }

   // -----------------------------------------------------------------------
   // Summary formulas through a table summary (ported from the 13.8
   // SummaryFormulas*FeatureTest classes; the direct formula API is tested in
   // the test class of each formula)
   // -----------------------------------------------------------------------

   private static final double EPS = 1e-12;

   private static void pairs(Formula formula, Object[]... pairs) {
      for(Object[] pair : pairs) {
         formula.addValue(pair);
      }
   }

   private static Object[] p(Object x, Object y) {
      return new Object[] { x, y };
   }

   /**
    * Summarize {g, x, y} rows by g: group rows 1.., then the grand total row.
    */
   private static SummaryFilter summarize(Formula formula, Object[][] rows) {
      Object[][] cells = new Object[rows.length + 1][];
      cells[0] = new Object[] { "g", "x", "y" };
      System.arraycopy(rows, 0, cells, 1, rows.length);
      DefaultTableLens data = new DefaultTableLens(cells);
      SummaryFilter summary = new SummaryFilter(
         data, new int[] { 0 }, new int[] { 1 }, formula, formula);
      summary.moreRows(Integer.MAX_VALUE);
      return summary;
   }

   private static double asDouble(Object result) {
      Assertions.assertInstanceOf(Double.class, result);
      return (Double) result;
   }

   // gap:report.summary-formulas.bivariate#correlation-coefficient
   @Test
   public void correlationInATableSummaryUsesTheSecondColumn() {
      Object[][] rows = {
         { "a", 1, 2 }, { "a", 2, 4 }, { "a", 3, 6 },
         { "b", 1, 6 }, { "b", 2, 4 }, { "b", 3, 2 }
      };
      SummaryFilter summary = summarize(new CorrelationFormula(2), rows);

      // one row for each group, then the grand total over all six pairs
      Assertions.assertEquals(4, summary.getRowCount());
      Assertions.assertEquals("a", summary.getObject(1, 0));
      Assertions.assertEquals(1.0, (Double) summary.getObject(1, 1), EPS);
      Assertions.assertEquals("b", summary.getObject(2, 0));
      Assertions.assertEquals(-1.0, (Double) summary.getObject(2, 1), EPS);
      // x = 1,2,3,1,2,3 and y = 2,4,6,6,4,2: sxy = 2+8+18+6+8+6 = 48, sx = 12, sy = 24,
      // sxx = 28, syy = 112: r = (6*48 - 12*24) / sqrt((6*28 - 144) * (6*112 - 576)) = 0
      Assertions.assertEquals(0.0, (Double) summary.getObject(3, 1), EPS);
   }

   // gap:report.summary-formulas.bivariate#covariance-value
   @Test
   public void covarianceInATableSummaryUsesTheSecondColumn() {
      Object[][] rows = {
         { "a", 1, 2 }, { "a", 2, 4 }, { "a", 3, 6 }, { "b", 1, 6 }, { "b", 3, 2 }
      };
      SummaryFilter summary = summarize(new CovarianceFormula(2), rows);

      Assertions.assertEquals(4, summary.getRowCount());
      Assertions.assertEquals(4.0 / 3, (Double) summary.getObject(1, 1), EPS);
      // (1,6),(3,2): means 2 and 4, (-1)(2) + (1)(-2) = -4, over n = 2
      Assertions.assertEquals(-2.0, (Double) summary.getObject(2, 1), EPS);
      // all five pairs: sx = 10, sy = 20, sxy = 2+8+18+6+6 = 40, so 40/5 - 2*4 = 0
      Assertions.assertEquals(0.0, (Double) summary.getObject(3, 1), EPS);
   }

   // gap:report.summary-formulas.bivariate#correlation-no-numeric-input-null
   @Test
   public void correlationGroupOfOnlyNullPairsHasNoResult() {
      // a table group whose pairs are all null shows no result, the next group still computes
      SummaryFilter summary = summarize(new CorrelationFormula(2), new Object[][] {
         { "a", null, null }, { "a", null, null },
         { "b", 1, 1 }, { "b", 2, 2 }, { "b", 3, 3 }
      });
      Assertions.assertEquals(4, summary.getRowCount());
      Assertions.assertNull(summary.getObject(1, 1));
      Assertions.assertEquals(1.0, (Double) summary.getObject(2, 1), EPS);
   }

   // gap:report.summary-formulas.bivariate#covariance-no-numeric-input-null
   @Test
   public void covarianceGroupOfOnlyNullPairsHasNoResult() {
      SummaryFilter summary = summarize(new CovarianceFormula(2), new Object[][] {
         { "a", null, null }, { "a", null, null }, { "b", 1, 2 }, { "b", 2, 4 }, { "b", 3, 6 }
      });
      Assertions.assertEquals(4, summary.getRowCount());
      Assertions.assertNull(summary.getObject(1, 1));
      Assertions.assertEquals(4.0 / 3, (Double) summary.getObject(2, 1), EPS);
   }

   // gap:report.summary-formulas.bivariate#correlation-skips-incomplete-pairs
   @Test
   public void correlationInATableSummarySkipsIncompletePairs() {
      CorrelationFormula whole = new CorrelationFormula(2);
      pairs(whole, p(1, 2), p(2, 7), p(3, 4), p(4, 5));
      Double expected = (Double) whole.getResult();

      // a table summary skips pairs with a null or text member, in the group and in the
      // grand total
      SummaryFilter summary = summarize(new CorrelationFormula(2), new Object[][] {
         { "a", 1, 2 }, { "a", null, 9 }, { "a", 2, 7 }, { "a", "x", 5 },
         { "a", 3, 4 }, { "a", 4, null }, { "a", 4, 5 }
      });
      Assertions.assertEquals(3, summary.getRowCount());
      Assertions.assertEquals(expected, summary.getObject(1, 1));
      Assertions.assertEquals(expected, summary.getObject(2, 1));
   }

   // gap:report.summary-formulas.bivariate#covariance-skips-incomplete-pairs
   @Test
   public void covarianceInATableSummarySkipsIncompletePairs() {
      CovarianceFormula whole = new CovarianceFormula(2);
      pairs(whole, p(1, 2), p(2, 4), p(3, 6));
      Double expected = (Double) whole.getResult();

      // a table summary skips them the same way
      SummaryFilter summary = summarize(new CovarianceFormula(2), new Object[][] {
         { "a", 1, 2 }, { "a", null, 9 }, { "a", 2, 4 }, { "a", "x", 5 },
         { "a", 3, 6 }, { "a", 6, null }
      });
      Assertions.assertEquals(3, summary.getRowCount());
      Assertions.assertEquals(expected, summary.getObject(1, 1));
      Assertions.assertEquals(expected, summary.getObject(2, 1));
   }

   // gap:report.summary-formulas.bivariate#correlation-zero-variance
   @Test
   public void correlationInATableSummaryZeroVarianceHasNoValue() {
      // a table group of one pair shows no value, and a constant group too
      SummaryFilter summary = summarize(new CorrelationFormula(2), new Object[][] {
         { "a", 1, 5 }, { "b", 1, 5 }, { "b", 2, 5 }, { "b", 3, 5 }
      });
      Assertions.assertEquals(4, summary.getRowCount());
      Assertions.assertNull(summary.getObject(1, 1));
      Assertions.assertNull(summary.getObject(2, 1));
      Assertions.assertNull(summary.getObject(3, 1));
   }

   // gap:report.summary-formulas.bivariate#correlation-skips-incomplete-pairs
   @Test
   public void correlationInATableSummarySkipsABlankTextPair() {
      CorrelationFormula whole = new CorrelationFormula(2);
      pairs(whole, p(1, 2), p(2, 7), p(3, 4), p(4, 5));

      SummaryFilter summary = summarize(new CorrelationFormula(2), new Object[][] {
         { "a", 1, 2 }, { "a", "", "" }, { "a", 2, 7 }, { "a", 3, 4 }, { "a", 4, 5 }
      });
      Assertions.assertEquals(whole.getResult(), summary.getObject(1, 1));
   }

   // gap:report.summary-formulas.bivariate#covariance-skips-incomplete-pairs
   @Test
   public void covarianceInATableSummarySkipsABlankTextPair() {
      CovarianceFormula whole = new CovarianceFormula(2);
      pairs(whole, p(1, 2), p(2, 4), p(3, 6));

      SummaryFilter summary = summarize(new CovarianceFormula(2), new Object[][] {
         { "a", 1, 2 }, { "a", "", "" }, { "a", 2, 4 }, { "a", 3, 6 }
      });
      Assertions.assertEquals(whole.getResult(), summary.getObject(1, 1));
   }

   // gap:report.summary-formulas.common#pair-formula-fed-secondary-column-values
   @Test
   public void weightedAverageFedSecondaryColumnValues() {
      // a group by column 0, the weighted average of column 1 by the weights of column 2
      DefaultTableLens table = new DefaultTableLens(new Object[][] {
         {"g", "x", "w"},
         {"a", 10, 2},
         {"a", 30, 3},
         {"b", 5, 2}
      });
      SummaryFilter filter = new SummaryFilter(
         table, new int[] {0}, new int[] {1}, new WeightedAverageFormula(2), null);
      filter.moreRows(Integer.MAX_VALUE);
      Assertions.assertEquals(3, filter.getRowCount());
      Assertions.assertEquals("a", filter.getObject(1, 0));
      Assertions.assertEquals(22.0, ((Number) filter.getObject(1, 1)).doubleValue(), 1e-9);
      Assertions.assertEquals("b", filter.getObject(2, 0));
      Assertions.assertEquals(5.0, ((Number) filter.getObject(2, 1)).doubleValue(), 1e-9);

      // a row with no weight is left out (13.8 also checked this through the report-only
      // RunningTotal, which StyleBI does not have)
      DefaultTableLens gap = new DefaultTableLens(new Object[][] {
         {"g", "x", "w"}, {"a", 10, 1}, {"a", 99, null}, {"a", 30, 3}
      });
      SummaryFilter skipping = new SummaryFilter(
         gap, new int[] {0}, new int[] {1}, new WeightedAverageFormula(2), null);
      skipping.moreRows(Integer.MAX_VALUE);
      Assertions.assertEquals(25.0, ((Number) skipping.getObject(1, 1)).doubleValue(), 1e-9);
   }

   // Bug #80738: a table summary of numeric text with blank cells ranks the numbers
   @Test
   public void maxAndNthBlankStringsInATableSummary() {
      DefaultTableLens data = new DefaultTableLens(new Object[][] {
         { "g", "v" }, { "a", "1" }, { "a", "" }, { "a", "3" }, { "b", "" }, { "b", "5" },
         { "c", "" }
      });
      Formula[] formulas = {
         new MaxFormula(), new NthLargestFormula(1), new NthLargestFormula(2),
         new NthSmallestFormula(2)
      };
      // rows: group a, group b, group c (only a blank), then the grand total
      Object[][] expected = {
         { "3", "3", "1", "3" }, { "5", "5", null, null }, { null, null, null, null },
         { "5", "5", "3", "3" }
      };

      for(int f = 0; f < formulas.length; f++) {
         SummaryFilter summary = new SummaryFilter(
            data, new int[] { 0 }, new int[] { 1 }, (Formula) formulas[f].clone(),
            (Formula) formulas[f].clone());
         summary.moreRows(Integer.MAX_VALUE);
         Assertions.assertEquals(5, summary.getRowCount());

         for(int g = 0; g < 4; g++) {
            Object actual = summary.getObject(g + 1, 1);
            String msg = formulas[f].getName() + " row " + (g + 1);

            if(expected[g][f] == null) {
               // no value to calculate: nothing, not a blank ranked as the largest
               Assertions.assertTrue(actual == null || "".equals(actual), msg + " " + actual);
            }
            else {
               Assertions.assertEquals(expected[g][f], actual, msg);
            }
         }
      }
   }

   // Bug #80868: a table summary of numeric text with a blank cell has a variance
   @Test
   public void blankStringsInAVarianceTableSummary() {
      DefaultTableLens data = new DefaultTableLens(new Object[][] {
         { "g", "v" }, { "a", "2" }, { "a", "" }, { "a", "4" }, { "a", "6" }, { "b", "" },
         { "b", "7" }
      });
      Formula[] formulas = {
         new VarianceFormula(), new StandardDeviationFormula(), new PopulationVarianceFormula(),
         new PopulationStandardDeviationFormula()
      };
      double[] a = { 4.0, 2.0, 8.0 / 3, Math.sqrt(8.0 / 3) };
      // the grand total of 2, 4, 6, 7
      double mean = 19.0 / 4;
      double sumSq = (2 - mean) * (2 - mean) + (4 - mean) * (4 - mean) + (6 - mean) * (6 - mean)
         + (7 - mean) * (7 - mean);
      double[] total = { sumSq / 3, Math.sqrt(sumSq / 3), sumSq / 4, Math.sqrt(sumSq / 4) };

      for(int f = 0; f < formulas.length; f++) {
         SummaryFilter summary = new SummaryFilter(
            data, new int[] { 0 }, new int[] { 1 }, (Formula) formulas[f].clone(),
            (Formula) formulas[f].clone());
         summary.moreRows(Integer.MAX_VALUE);

         Assertions.assertEquals(a[f], asDouble(summary.getObject(1, 1)), 1e-9,
                                 formulas[f].getName() + " group a");
         Assertions.assertEquals(total[f], asDouble(summary.getObject(3, 1)), 1e-9,
                                 formulas[f].getName() + " total");
      }
   }

   // gap:report.summary-formulas.text#concat-comma-separated
   @Test
   public void concatInATableSummaryJoinsEachGroup() {
      DefaultTableLens data = new DefaultTableLens(new Object[][] {
         { "g", "v" },
         { "a", "x" }, { "a", "y" }, { "a", "z" }, { "b", "p" }, { "b", "q" }
      });
      SummaryFilter summary = new SummaryFilter(
         data, new int[] { 0 }, new int[] { 1 }, new ConcatFormula(), new ConcatFormula());
      summary.moreRows(Integer.MAX_VALUE);

      // one row for each group, then the grand total
      Assertions.assertEquals(4, summary.getRowCount());
      Assertions.assertEquals("a", summary.getObject(1, 0));
      Assertions.assertEquals("x,y,z", summary.getObject(1, 1));
      Assertions.assertEquals("b", summary.getObject(2, 0));
      Assertions.assertEquals("p,q", summary.getObject(2, 1));
      Assertions.assertEquals("x,y,z,p,q", summary.getObject(3, 1));
   }

   // gap:report.summary-formulas.text#concat-no-values-empty-string
   @Test
   public void concatGroupOfOnlyNullsShowsEmptyString() {
      // a table summary of a group whose values are all null shows the empty string
      DefaultTableLens data = new DefaultTableLens(new Object[][] {
         { "g", "v" }, { "a", null }, { "a", null }, { "b", "k" }
      });
      SummaryFilter summary = new SummaryFilter(
         data, new int[] { 0 }, new int[] { 1 }, new ConcatFormula(), null);
      summary.moreRows(Integer.MAX_VALUE);
      Assertions.assertEquals(3, summary.getRowCount());
      Assertions.assertEquals("a", summary.getObject(1, 0));
      Assertions.assertEquals("", summary.getObject(1, 1));
      Assertions.assertEquals("k", summary.getObject(2, 1));
   }

   // Bug #80621: a table summary keeps a group's leading blank string
   @Test
   public void concatInATableSummaryKeepsALeadingBlankString() {
      DefaultTableLens data = new DefaultTableLens(new Object[][] {
         { "g", "v" }, { "a", "" }, { "a", "x" }, { "b", "p" }, { "b", "" }, { "c", "" }
      });
      SummaryFilter summary = new SummaryFilter(
         data, new int[] { 0 }, new int[] { 1 }, new ConcatFormula(), new ConcatFormula());
      summary.moreRows(Integer.MAX_VALUE);

      Assertions.assertEquals(5, summary.getRowCount());
      Assertions.assertEquals(",x", summary.getObject(1, 1));
      Assertions.assertEquals("p,", summary.getObject(2, 1));
      Assertions.assertEquals("", summary.getObject(3, 1));
      Assertions.assertEquals(",x,p,,", summary.getObject(4, 1));
   }
}
