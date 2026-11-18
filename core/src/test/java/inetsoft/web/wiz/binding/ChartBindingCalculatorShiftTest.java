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

import inetsoft.report.composition.graph.calc.ChangeCalc;
import inetsoft.report.composition.graph.calc.ValueOfCalc;
import inetsoft.report.internal.graph.ChangeChartProcessor;
import inetsoft.uql.XConstants;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.graph.Calculator;
import inetsoft.uql.viewsheet.graph.DefaultVSChartInfo;
import inetsoft.uql.viewsheet.graph.GraphTypes;
import inetsoft.uql.viewsheet.graph.VSAestheticRef;
import inetsoft.uql.viewsheet.graph.VSChartAggregateRef;
import inetsoft.uql.viewsheet.graph.VSChartDimensionRef;
import inetsoft.uql.viewsheet.graph.VSChartInfo;
import inetsoft.web.wiz.pairing.WizAgentTestSupport;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #81687: a CHANGE/VALUE calculator whose shift is finer than its dimension's date level is
 * refused on the final chart info, whatever order the calculator and the level were written in.
 */
@WizAgentTestSupport
@Tag("core")
class ChartBindingCalculatorShiftTest {
   private static VSChartDimensionRef dimension(int level) {
      AttributeRef attribute = new AttributeRef(null, "Order Date");
      attribute.setDataType(XSchema.DATE);
      VSChartDimensionRef dim = new VSChartDimensionRef();
      dim.setDataRef(new ColumnRef(attribute));
      dim.setDateLevelValue(String.valueOf(level));
      return dim;
   }

   private static VSChartAggregateRef sum(Calculator calc) {
      AttributeRef attribute = new AttributeRef(null, "Sales");
      attribute.setDataType(XSchema.DOUBLE);
      VSChartAggregateRef sales = new VSChartAggregateRef();
      sales.setDataRef(new ColumnRef(attribute));
      sales.setFormulaValue("Sum");
      sales.setCalculator(calc);
      return sales;
   }

   private static ValueOfCalc calc(boolean change, int from) {
      ValueOfCalc calc = change ? new ChangeCalc() : new ValueOfCalc();
      calc.setFrom(from);
      calc.setColumnName("Order Date");
      return calc;
   }

   private static VSChartInfo onY(int level, Calculator calc) {
      VSChartInfo info = new DefaultVSChartInfo();
      info.setChartType(GraphTypes.CHART_LINE);
      info.addXField(dimension(level));
      info.addYField(sum(calc));
      new ChangeChartProcessor().fixAggregateRefs(info);
      return info;
   }

   private static void refused(int level, int from, boolean change) {
      IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
         () -> ChartBindingService.requireCalculatorShiftsLand(onY(level, calc(change, from))));
      assertTrue(e.getMessage().contains("calculateInfo.from " + from), e.getMessage());
      assertTrue(e.getMessage().contains("every cell would be blank"), e.getMessage());
   }

   private static void accepted(int level, int from) {
      assertDoesNotThrow(
         () -> ChartBindingService.requireCalculatorShiftsLand(onY(level, calc(true, from))));
   }

   @Test
   void refusesTheShiftsSmallerThanTheBucket() {
      for(boolean change : new boolean[] {true, false}) {
         refused(XConstants.QUARTER_DATE_GROUP, 7, change);
         refused(XConstants.QUARTER_DATE_GROUP, 6, change);
         refused(XConstants.YEAR_DATE_GROUP, 5, change);
         refused(XConstants.YEAR_DATE_GROUP, 6, change);
         refused(XConstants.YEAR_DATE_GROUP, 7, change);
         refused(XConstants.MONTH_DATE_GROUP, 6, change);
      }
   }

   @Test
   void refusalNamesFromAndTheLevel() {
      IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
         () -> ChartBindingService.requireCalculatorShiftsLand(
            onY(XConstants.QUARTER_DATE_GROUP, calc(true, 7))));
      assertTrue(e.getMessage().contains("Previous Month"), e.getMessage());
      assertTrue(e.getMessage().contains("quarter"), e.getMessage());
   }

   @Test
   void acceptsTheShiftsThatLand() {
      accepted(XConstants.QUARTER_DATE_GROUP, 5);
      accepted(XConstants.QUARTER_DATE_GROUP, 4);
      accepted(XConstants.MONTH_DATE_GROUP, 7);
      accepted(XConstants.MONTH_DATE_GROUP, 4);
      accepted(XConstants.YEAR_DATE_GROUP, 4);
      accepted(XConstants.WEEK_DATE_GROUP, 6);
      accepted(XConstants.QUARTER_DATE_GROUP, 1);
      accepted(XConstants.QUARTER_DATE_GROUP, 8);
   }

   /** The calculator is on the info first; the level is switched afterwards. */
   @Test
   void levelChangedAfterTheCalculatorIsRefused() {
      VSChartInfo info = onY(XConstants.MONTH_DATE_GROUP, calc(true, 7));
      assertDoesNotThrow(() -> ChartBindingService.requireCalculatorShiftsLand(info));
      info.removeXField(0);
      info.addXField(dimension(XConstants.QUARTER_DATE_GROUP));
      ((ValueOfCalc) ((VSChartAggregateRef) info.getYFields()[0]).getCalculator())
         .setColumnName("Order Date");
      new ChangeChartProcessor().fixAggregateRefs(info);
      assertThrows(IllegalArgumentException.class,
                   () -> ChartBindingService.requireCalculatorShiftsLand(info));
   }

   @Test
   void aestheticMeasureIsChecked() {
      VSChartInfo info = new DefaultVSChartInfo();
      info.setChartType(GraphTypes.CHART_LINE);
      info.addXField(dimension(XConstants.QUARTER_DATE_GROUP));
      info.addYField(sum(null));
      VSAestheticRef color = new VSAestheticRef();
      color.setDataRef(sum(calc(true, 7)));
      info.setColorField(color);
      new ChangeChartProcessor().fixAggregateRefs(info);
      assertThrows(IllegalArgumentException.class,
                   () -> ChartBindingService.requireCalculatorShiftsLand(info));
   }

   @Test
   void unnamedOrUnresolvedDimensionIsNotRefused() {
      ValueOfCalc blank = calc(true, 7);
      blank.setColumnName(null);
      assertDoesNotThrow(() -> ChartBindingService.requireCalculatorShiftsLand(
         onY(XConstants.QUARTER_DATE_GROUP, blank)));
   }
}
