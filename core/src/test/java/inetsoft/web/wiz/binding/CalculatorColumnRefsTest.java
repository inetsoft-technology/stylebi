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
import inetsoft.report.composition.graph.calc.CompoundGrowthCalc;
import inetsoft.report.composition.graph.calc.PercentCalc;
import inetsoft.report.composition.graph.calc.RunningTotalCalc;
import inetsoft.report.composition.graph.calc.ValueOfCalc;
import inetsoft.report.internal.graph.ChangeChartProcessor;
import inetsoft.uql.XConstants;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.graph.AbstractCalc;
import inetsoft.uql.viewsheet.graph.Calculator;
import inetsoft.uql.viewsheet.graph.DefaultVSChartInfo;
import inetsoft.uql.viewsheet.graph.GraphTypes;
import inetsoft.uql.viewsheet.graph.VSAestheticRef;
import inetsoft.uql.viewsheet.graph.VSChartAggregateRef;
import inetsoft.uql.viewsheet.graph.VSChartDimensionRef;
import inetsoft.uql.viewsheet.graph.VSChartInfo;
import inetsoft.web.binding.model.graph.CalculateInfo;
import inetsoft.web.binding.model.graph.calc.ChangeCalcInfo;
import inetsoft.web.binding.model.graph.calc.CompoundGrowthCalcInfo;
import inetsoft.web.binding.model.graph.calc.PercentCalcInfo;
import inetsoft.web.binding.model.graph.calc.RunningTotalCalcInfo;
import inetsoft.web.wiz.binding.model.FieldRef;
import inetsoft.web.wiz.pairing.WizAgentTestSupport;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #78751 (DCG-044): every calculator that designates a dimension -- CHANGE/VALUE and PERCENT
 * {@code columnName}, RUNNINGTOTAL/COMPOUNDGROWTH {@code breakBy} -- on a shelf or an aesthetic
 * channel resolves a unique bare name and is refused (naming the key) when the runtime clears it.
 */
@WizAgentTestSupport
@Tag("core")
class CalculatorColumnRefsTest {
   private VSChartInfo lastInfo;

   private static VSChartDimensionRef dimension(String column, int level) {
      AttributeRef attribute = new AttributeRef(null, column);
      attribute.setDataType(XSchema.DATE);
      VSChartDimensionRef dim = new VSChartDimensionRef();
      dim.setDataRef(new ColumnRef(attribute));
      dim.setDateLevelValue(String.valueOf(level));
      return dim;
   }

   private static VSChartDimensionRef region() {
      AttributeRef attribute = new AttributeRef(null, "Region");
      attribute.setDataType(XSchema.STRING);
      VSChartDimensionRef dim = new VSChartDimensionRef();
      dim.setDataRef(new ColumnRef(attribute));
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

   /** x = [Region, Month(Order Date)], the measure with {@code calc} on y; fixed up. */
   private VSChartAggregateRef onY(Calculator calc) {
      VSChartInfo info = new DefaultVSChartInfo();
      info.setChartType(GraphTypes.CHART_LINE);
      info.addXField(region());
      info.addXField(dimension("Order Date", XConstants.MONTH_DATE_GROUP));
      VSChartAggregateRef sales = sum(calc);
      info.addYField(sales);
      new ChangeChartProcessor().fixAggregateRefs(info);
      lastInfo = info;
      return sales;
   }

   private static PercentCalc percent(String columnName) {
      PercentCalc calc = new PercentCalc();
      calc.setLevel(PercentCalc.GRAND_TOTAL);
      calc.setColumnName(columnName);
      return calc;
   }

   private static RunningTotalCalc running(boolean compound, String breakBy) {
      RunningTotalCalc calc = compound ? new CompoundGrowthCalc() : new RunningTotalCalc();
      calc.setBreakBy(breakBy);
      return calc;
   }

   private String percentColumn(String columnName) {
      return ((PercentCalc) onY(percent(columnName)).getCalculator()).getColumnName();
   }

   private String breakBy(boolean compound, String breakBy) {
      return ((RunningTotalCalc) onY(running(compound, breakBy)).getCalculator()).getBreakBy();
   }

   private static FieldRef field(CalculateInfo calc) {
      return new FieldRef("Sales", "measure", "Sum", null, null, null, null, null, calc);
   }

   private static PercentCalcInfo percentInfo(String columnName) {
      PercentCalcInfo calc = new PercentCalcInfo();
      calc.setColumnName(columnName);
      return calc;
   }

   private static RunningTotalCalcInfo runningInfo(boolean compound, String breakBy) {
      RunningTotalCalcInfo calc = compound ? new CompoundGrowthCalcInfo() : new RunningTotalCalcInfo();
      calc.setBreakBy(breakBy);
      return calc;
   }

   // ── processor: PERCENT columnName ──

   @Test
   void percentColumnNameKeepsAnExactNameAndResolvesABareOne() {
      assertEquals("Region", percentColumn("Region"));
      assertEquals("Month(Order Date)", percentColumn("Month(Order Date)"));
      assertEquals("Month(Order Date)", percentColumn("Order Date"));
      assertEquals("Month(Order Date)", percentColumn("order date"));
   }

   @Test
   void percentColumnNameUnknownOrBlankIsCleared() {
      assertNull(percentColumn("Nope"));
      assertNull(percentColumn(null));
   }

   // ── processor: RUNNINGTOTAL / COMPOUNDGROWTH breakBy ──

   @Test
   void breakByKeepsAnExactNameAndResolvesABareOne() {
      for(boolean compound : new boolean[] {false, true}) {
         assertEquals("Region", breakBy(compound, "Region"));
         assertEquals("Month(Order Date)", breakBy(compound, "Order Date"));
      }
   }

   @Test
   void breakByUnknownIsCleared() {
      for(boolean compound : new boolean[] {false, true}) {
         assertNull(breakBy(compound, "Nope"));
      }
   }

   /** RunningTotalCalc.getColumnName() is its breakBy, so the inner sentinels live there. */
   @Test
   void breakByInnerSentinelIsLeftAlone() {
      assertEquals(AbstractCalc.ROW_INNER, breakBy(false, AbstractCalc.ROW_INNER));
      assertEquals(AbstractCalc.COLUMN_INNER, breakBy(true, AbstractCalc.COLUMN_INNER));
   }

   // ── read-back refusal (shelf) ──

   @Test
   void readBackRefusesAClearedPercentColumnNamingColumnName() {
      onY(percent("Nope"));
      IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
         () -> ChartBindingService.requireCalculatorsResolved(
            lastInfo, "y", List.of(field(percentInfo("Nope")))));
      assertTrue(e.getMessage().contains("calculateInfo.columnName"), e.getMessage());
      assertTrue(e.getMessage().contains("Month(Order Date)"), e.getMessage());
   }

   @Test
   void readBackRefusesAClearedBreakByNamingBreakBy() {
      for(boolean compound : new boolean[] {false, true}) {
         onY(running(compound, "Nope"));
         IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> ChartBindingService.requireCalculatorsResolved(
               lastInfo, "y", List.of(field(runningInfo(compound, "Nope")))));
         assertTrue(e.getMessage().contains("calculateInfo.breakBy"), e.getMessage());
      }
   }

   @Test
   void readBackAcceptsResolvedAndBlankReferences() {
      onY(percent("Order Date"));
      assertDoesNotThrow(() -> ChartBindingService.requireCalculatorsResolved(
         lastInfo, "y", List.of(field(percentInfo("Order Date")))));

      onY(running(false, null));
      assertDoesNotThrow(() -> ChartBindingService.requireCalculatorsResolved(
         lastInfo, "y", List.of(field(runningInfo(false, null)))));
      assertDoesNotThrow(() -> ChartBindingService.requireCalculatorsResolved(
         lastInfo, "y", List.of(field(runningInfo(false, "")))));

      onY(percent(null));
      assertDoesNotThrow(() -> ChartBindingService.requireCalculatorsResolved(
         lastInfo, "y", List.of(field(percentInfo(null)))));
   }

   // ── aesthetic channel ──

   /** x = [Month(Order Date)], a plain measure on y, the measure with {@code calc} on color. */
   private static VSChartInfo withColorMeasure(Calculator calc) {
      VSChartInfo info = new DefaultVSChartInfo();
      info.setChartType(GraphTypes.CHART_LINE);
      info.addXField(dimension("Order Date", XConstants.MONTH_DATE_GROUP));
      info.addYField(sum(null));
      VSAestheticRef color = new VSAestheticRef();
      color.setDataRef(sum(calc));
      info.setColorField(color);
      new ChangeChartProcessor().fixAggregateRefs(info);
      return info;
   }

   private static Calculator colorCalc(VSChartInfo info) {
      return ((VSChartAggregateRef) info.getColorField().getDataRef()).getCalculator();
   }

   @Test
   void aestheticChangeColumnNameResolvesABareName() {
      ChangeCalc calc = new ChangeCalc();
      calc.setColumnName("Order Date");
      assertEquals("Month(Order Date)",
                   ((ValueOfCalc) colorCalc(withColorMeasure(calc))).getColumnName());
   }

   @Test
   void aestheticPercentAndBreakByAreResolvedToo() {
      assertEquals("Month(Order Date)",
                   ((PercentCalc) colorCalc(withColorMeasure(percent("Order Date")))).getColumnName());
      assertEquals("Month(Order Date)",
                   ((RunningTotalCalc) colorCalc(withColorMeasure(running(false, "order date"))))
                      .getBreakBy());
   }

   @Test
   void aestheticReadBackRefusesAClearedReference() {
      ChangeCalc change = new ChangeCalc();
      change.setColumnName("Nope");
      VSChartInfo changed = withColorMeasure(change);
      assertNull(((ValueOfCalc) colorCalc(changed)).getColumnName());

      ChangeCalcInfo sentChange = new ChangeCalcInfo();
      sentChange.setColumnName("Nope");
      IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
         () -> ChartBindingService.requireAestheticCalculatorResolved(
            changed, "color", field(sentChange)));
      assertTrue(e.getMessage().contains("calculateInfo.columnName"), e.getMessage());

      VSChartInfo percented = withColorMeasure(percent("Nope"));
      assertThrows(IllegalArgumentException.class,
         () -> ChartBindingService.requireAestheticCalculatorResolved(
            percented, "color", field(percentInfo("Nope"))));
   }

   @Test
   void aestheticReadBackAcceptsAResolvedName() {
      ChangeCalc change = new ChangeCalc();
      change.setColumnName("Order Date");
      VSChartInfo info = withColorMeasure(change);
      ChangeCalcInfo sent = new ChangeCalcInfo();
      sent.setColumnName("Order Date");
      assertDoesNotThrow(() -> ChartBindingService.requireAestheticCalculatorResolved(
         info, "color", field(sent)));
   }
}
