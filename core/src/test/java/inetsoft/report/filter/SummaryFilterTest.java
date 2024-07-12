/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.report.filter;

import inetsoft.report.StyleConstants;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.test.SreeHome;
import inetsoft.test.XTableUtil;
import inetsoft.uql.Condition;
import inetsoft.uql.XConstants;
import inetsoft.uql.schema.XSchema;
import org.junit.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;

@SreeHome()
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

   private Date date(String date) {
      return new Date(LocalDate.parse(date).atStartOfDay()
                         .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
   }
}
