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
package inetsoft.web.wiz.binding;

import inetsoft.uql.XConstants;
import inetsoft.uql.viewsheet.graph.GraphTypes;
import inetsoft.web.binding.model.ChartBindingModel;
import inetsoft.web.binding.model.graph.*;
import inetsoft.web.binding.model.graph.calc.ChangeCalcInfo;
import inetsoft.web.wiz.binding.model.FieldRef;
import inetsoft.web.wiz.pairing.WizAgentTestSupport;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #78474: timeSeries:true over a stored non-ascending sort is refused, the stored binding is
 * kept, and each remedy the refusal names makes the resend succeed (verifier coverage beyond the
 * fixer's ChartBindingMutatorTest/ChartAestheticMutatorTest cases).
 */
@WizAgentTestSupport
@Tag("core")
class ChartTimeSeriesSortConflictTest {
   private static FieldRef date(String level, Boolean ts) {
      return new FieldRef("Order Date", "dimension", null, level, null, null, null, null, null,
                          null, null, null, ts);
   }

   private static FieldRef sum() {
      return new FieldRef("Total", "measure", "Sum", null, null);
   }

   private static DimensionSortRanking.Sort sort(String d) {
      return new DimensionSortRanking.Sort(d, null, null);
   }

   private static ChartDimensionRefModel dim(List<ChartRefModel> refs, int i) {
      return (ChartDimensionRefModel) refs.get(i);
   }

   private static ChartBindingModel dcg041Fixture() {
      ChartBindingModel model = new ChartBindingModel();
      model.setChartType(GraphTypes.CHART_LINE);
      ChartBindingMutator.setShelf(model, "x", List.of(date("month", false)));
      ChartBindingMutator.setShelf(model, "y", List.of(sum()));
      return model;
   }

   private static void resetLineChart(ChartBindingModel model) {
      model.setChartType(GraphTypes.CHART_LINE);
      ChartBindingMutator.setShelf(model, "x", List.of(date("month", false)));
      ChartBindingMutator.setSort(model, "x", "Order Date", null, sort("asc"));
   }

   /** DCG-041's exact sequence (control, then defect) on one model, as the live case runs it. */
   @Test
   void dcg041Sequence() {
      ChartBindingModel model = dcg041Fixture();

      // control
      resetLineChart(model);
      ChartBindingMutator.setShelf(model, "x", List.of(date("month", true)));
      assertTrue(dim(model.getXFields(), 0).isTimeSeries());
      IllegalArgumentException control = assertThrows(IllegalArgumentException.class,
         () -> ChartBindingMutator.setSort(model, "x", "Order Date", null, sort("desc")));
      assertTrue(control.getMessage().matches("(?is).*(timeSeries|sort).*"));

      // defect
      resetLineChart(model);
      ChartBindingMutator.setSort(model, "x", "Order Date", null, sort("desc"));
      assertFalse(dim(model.getXFields(), 0).isTimeSeries());
      assertEquals("desc", DimensionSortRanking.describe(dim(model.getXFields(), 0)).get("direction"));

      IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
         () -> ChartBindingMutator.setShelf(model, "x", List.of(date("month", true))));
      assertTrue(e.getMessage().matches("(?is).*(timeSeries|sort).*"));
      // stored binding intact: get_binding would read ts:false, desc
      ChartDimensionRefModel after = dim(model.getXFields(), 0);
      assertFalse(after.isTimeSeries());
      assertEquals("desc", DimensionSortRanking.describe(after).get("direction"));
      assertEquals(1, model.getYFields().size());

      // the remedy the message names
      ChartBindingMutator.setSort(model, "x", "Order Date", null, sort("asc"));
      ChartBindingMutator.setShelf(model, "x", List.of(date("month", true)));
      assertTrue(dim(model.getXFields(), 0).isTimeSeries());
      assertEquals("asc", DimensionSortRanking.describe(dim(model.getXFields(), 0)).get("direction"));

      // and the other remedy: timeSeries:false keeps the sort
      resetLineChart(model);
      ChartBindingMutator.setSort(model, "x", "Order Date", null, sort("desc"));
      ChartBindingMutator.setShelf(model, "x", List.of(date("month", false)));
      assertEquals(XConstants.SORT_DESC, dim(model.getXFields(), 0).getOrder());
   }

   /** C1 4-step loophole, then the message's own remedy (index:1 asc) makes the resend succeed. */
   @Test
   void c1LoopholeRefusedAndRemedyWithIndexWorks() {
      ChartBindingModel model = new ChartBindingModel();
      model.setChartType(GraphTypes.CHART_LINE);
      ChartBindingMutator.setShelf(model, "x", List.of(date("month", true)));
      ChartBindingMutator.setShelf(model, "y", List.of(sum()));
      ChartBindingMutator.setShelf(model, "x", List.of(date("month", null), date("month", false)));
      ChartBindingMutator.setSort(model, "x", "Order Date", 1, sort("desc"));
      List<ChartRefModel> before = new ArrayList<>(model.getXFields());

      IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
         () -> ChartBindingMutator.setShelf(model, "x",
                                            List.of(date("month", null), date("month", true))));
      assertTrue(e.getMessage().contains("index:1"));
      assertEquals(before, model.getXFields());
      assertTrue(dim(model.getXFields(), 0).isTimeSeries());

      ChartBindingMutator.setSort(model, "x", "Order Date", 1, sort("asc"));
      ChartBindingMutator.setShelf(model, "x", List.of(date("month", null), date("month", true)));
      assertTrue(dim(model.getXFields(), 1).isTimeSeries());
      assertEquals(XConstants.SORT_ASC, dim(model.getXFields(), 1).getOrder());
   }

   /** Same column at two levels: index hint points at the sorted level; remedy works. */
   @Test
   void indexHintWithTwoLevels() {
      ChartBindingModel model = new ChartBindingModel();
      model.setChartType(GraphTypes.CHART_LINE);
      ChartBindingMutator.setShelf(model, "x", List.of(date("year", false), date("month", false)));
      ChartBindingMutator.setShelf(model, "y", List.of(sum()));
      ChartBindingMutator.setSort(model, "x", "Order Date", 1, sort("desc"));

      IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
         () -> ChartBindingMutator.setShelf(model, "x",
                                            List.of(date("year", null), date("month", true))));
      assertTrue(e.getMessage().contains("index:1"), e.getMessage());

      // no index when the column is bound once
      ChartBindingModel single = dcg041Fixture();
      ChartBindingMutator.setSort(single, "x", "Order Date", null, sort("desc"));
      IllegalArgumentException e1 = assertThrows(IllegalArgumentException.class,
         () -> ChartBindingMutator.setShelf(single, "x", List.of(date("month", true))));
      assertFalse(e1.getMessage().contains("index"), e1.getMessage());

      ChartBindingMutator.setSort(model, "x", "Order Date", 1, sort("asc"));
      ChartBindingMutator.setShelf(model, "x", List.of(date("year", null), date("month", true)));
      assertTrue(dim(model.getXFields(), 1).isTimeSeries());
   }

   /** Two identical occurrences both sorted desc: hint is generic, not a wrong index. */
   @Test
   void indexHintAmbiguous() {
      ChartBindingModel model = new ChartBindingModel();
      model.setChartType(GraphTypes.CHART_LINE);
      ChartBindingMutator.setShelf(model, "x", List.of(date("month", false), date("month", false)));
      ChartBindingMutator.setShelf(model, "y", List.of(sum()));
      ChartBindingMutator.setSort(model, "x", "Order Date", 0, sort("desc"));
      ChartBindingMutator.setSort(model, "x", "Order Date", 1, sort("desc"));

      IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
         () -> ChartBindingMutator.setShelf(model, "x",
                                            List.of(date("month", null), date("month", true))));
      assertTrue(e.getMessage().contains("index:<"), e.getMessage());
   }

   /** y shelf: refused naming the y shelf and shelf:"y" in the remedy. */
   @Test
   void yShelf() {
      ChartBindingModel model = new ChartBindingModel();
      model.setChartType(GraphTypes.CHART_BAR);
      ChartBindingMutator.setShelf(model, "x", List.of(sum()));
      ChartBindingMutator.setShelf(model, "y", List.of(date("month", false)));
      ChartBindingMutator.setSort(model, "y", "Order Date", null,
         new DimensionSortRanking.Sort("value_desc", "Sum(Total)", null));

      IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
         () -> ChartBindingMutator.setShelf(model, "y", List.of(date("month", true))));
      assertTrue(e.getMessage().contains("y shelf") && e.getMessage().contains("shelf:\"y\""));
      assertEquals("Sum(Total)", dim(model.getYFields(), 0).getSortByCol());
   }

   /** Inline named group on the date dimension, then ts:true with the group re-sent: accepted. */
   @Test
   void namedGroupThenTimeSeries() {
      FieldRef.NamedGroupValues groups = new FieldRef.NamedGroupValues(
         List.of(new FieldRef.NamedGroupValues.Clause("H1", List.of("2020-01", "2020-02"))), null);
      ChartBindingModel model = new ChartBindingModel();
      model.setChartType(GraphTypes.CHART_LINE);
      ChartBindingMutator.setShelf(model, "y", List.of(sum()));
      ChartBindingMutator.setShelf(model, "x", List.of(new FieldRef("Order Date", "dimension",
         null, "month", null, null, null, groups, null, null, null, null, false)));
      assertEquals(XConstants.SORT_SPECIFIC, dim(model.getXFields(), 0).getOrder());

      ChartBindingMutator.setShelf(model, "x", List.of(new FieldRef("Order Date", "dimension",
         null, "month", null, null, null, groups, null, null, null, null, true)));
      assertTrue(dim(model.getXFields(), 0).isTimeSeries());
   }

   /** Aesthetic: refused; following clear + rebind makes the resend succeed. */
   @Test
   void aestheticRemedyWorks() {
      ChartBindingModel model = new ChartBindingModel();
      ChartBindingMutator.setShelf(model, "y", List.of(sum()));
      ChangeCalcInfo change = new ChangeCalcInfo();
      change.setColumnName("Quarter(Order Date)");
      ((ChartAggregateRefModel) model.getYFields().get(0)).setCalculateInfo(change);
      ChartAestheticMutator.setField(model, "color", date("quarter", false));
      ((ChartDimensionRefModel) model.getColorField().getDataInfo()).setOrder(XConstants.SORT_DESC);

      IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
         () -> ChartAestheticMutator.setField(model, "color", date("quarter", true)));

      // a set_chart_field_sort on the colour dimension is possible since bug #78803
      ChartBindingMutator.setSort(model, "color", "Order Date", null, sort("asc"));

      ChartAestheticMutator.clearField(model, "color");
      ChartAestheticMutator.setField(model, "color", date("quarter", true));
      ChartDimensionRefModel color = (ChartDimensionRefModel) model.getColorField().getDataInfo();
      assertTrue(color.isTimeSeries());
      assertNotEquals(XConstants.SORT_DESC, color.getOrder() & ~XConstants.SORT_SPECIFIC);
   }
}
