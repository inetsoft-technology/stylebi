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
package inetsoft.report.internal.graph;

import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.graph.AxisDescriptor;
import inetsoft.uql.viewsheet.graph.ChartDescriptor;
import inetsoft.uql.viewsheet.graph.ChartRef;
import inetsoft.uql.viewsheet.graph.DefaultVSChartInfo;
import inetsoft.uql.viewsheet.graph.GraphTypes;
import inetsoft.uql.viewsheet.graph.VSChartAggregateRef;
import inetsoft.uql.viewsheet.graph.VSChartDimensionRef;
import inetsoft.uql.viewsheet.graph.VSChartInfo;
import inetsoft.web.wiz.pairing.WizAgentTestSupport;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #78751 (VCS-029): under an applied changeAndValue date comparison the runtime flags read
 * multi=true / separated=false while the design is multi=false / separated=true. The separate
 * status processor must decide from the design flags, so a request equal to the design is a no-op
 * that leaves the first measure's own y axis settings alone.
 */
@WizAgentTestSupport
@Tag("core")
class ChangeSeparateStatusDesignFlagsTest {
   private static VSChartAggregateRef measure(String column) {
      AttributeRef attribute = new AttributeRef(null, column);
      attribute.setDataType(XSchema.DOUBLE);
      VSChartAggregateRef ref = new VSChartAggregateRef();
      ref.setDataRef(new ColumnRef(attribute));
      ref.setFormulaValue("Sum");
      return ref;
   }

   /** Design: separated, single-style; y[0] has a log-scale axis of its own. */
   private static VSChartInfo chart(boolean underDateComparison) {
      VSChartInfo info = new DefaultVSChartInfo();
      info.setChartType(GraphTypes.CHART_LINE);
      AttributeRef attribute = new AttributeRef(null, "Order Date");
      attribute.setDataType(XSchema.DATE);
      VSChartDimensionRef date = new VSChartDimensionRef();
      date.setDataRef(new ColumnRef(attribute));
      info.addXField(date);
      VSChartAggregateRef first = measure("A");
      AxisDescriptor axis = new AxisDescriptor();
      axis.setLogarithmicScale(true);
      first.setAxisDescriptor(axis);
      info.addYField(first);
      info.addYField(measure("B"));
      info.setSeparatedGraph(true);
      info.setMultiStyles(false);

      if(underDateComparison) {
         info.setRuntimeDateComparisonRefs(new ChartRef[] {new VSChartDimensionRef()});
         info.setRuntimeMulti(true);
         info.setRuntimeSeparated(false);
         assertTrue(info.isMultiStyles());
         assertFalse(info.isDesignMultiStyles());
         assertFalse(info.isSeparatedGraph());
         assertTrue(info.isDesignSeparated());
      }

      return info;
   }

   private static boolean firstLog(VSChartInfo info) {
      return ((VSChartAggregateRef) info.getYField(0)).getAxisDescriptor().isLogarithmicScale();
   }

   @Test
   void noOpSeparateUnderADateComparisonKeepsTheFirstMeasureAxisSettings() {
      VSChartInfo info = chart(true);

      new ChangeSeparateStatusProcessor(info, new ChartDescriptor()).process(true, false);

      assertTrue(firstLog(info));
      assertTrue(info.isDesignSeparated());
      assertFalse(info.isDesignMultiStyles());
   }

   @Test
   void noOpSeparateWithoutADateComparisonKeepsTheFirstMeasureAxisSettings() {
      VSChartInfo info = chart(false);

      new ChangeSeparateStatusProcessor(info, new ChartDescriptor()).process(true, false);

      assertTrue(firstLog(info));
   }

   @Test
   void realToggleUnderADateComparisonMovesTheAxisDescriptorLikeWithoutOne() {
      VSChartInfo withDc = chart(true);
      VSChartInfo without = chart(false);

      new ChangeSeparateStatusProcessor(withDc, new ChartDescriptor()).process(false, false);
      new ChangeSeparateStatusProcessor(without, new ChartDescriptor()).process(false, false);

      assertFalse(withDc.isDesignSeparated());
      assertFalse(without.isDesignSeparated());
      // merged: the first measure's own axis settings become the chart's shared axis
      assertTrue(withDc.getAxisDescriptor().isLogarithmicScale());
      assertEquals(without.getAxisDescriptor().isLogarithmicScale(),
                   withDc.getAxisDescriptor().isLogarithmicScale());
      assertFalse(withDc.isDesignMultiStyles());
   }

   /** Design merged, a shared axis minimum of 5; the first measure's own axis is untouched. */
   private static VSChartInfo mergedChart(boolean underDateComparison) {
      VSChartInfo info = chart(underDateComparison);
      info.setSeparatedGraph(false);
      info.getAxisDescriptor().setMinimum(5);

      if(underDateComparison) {
         // changeAndValue: the override says separated=false/multi=true, same as the design flag
         // read for separated, so make the override differ from the request being made below.
         info.setRuntimeSeparated(false);
      }

      return info;
   }

   private static Number minimumOf(VSChartInfo info, int y) {
      return ((VSChartAggregateRef) info.getYField(y)).getAxisDescriptor().getMinimum();
   }

   @Test
   void mergedToSeparatedUnderADateComparisonCopiesTheSharedAxisToEveryMeasure() {
      VSChartInfo without = mergedChart(false);
      VSChartInfo withDc = mergedChart(true);
      // a fresh y axis of its own would hide a copy that did not happen
      ((VSChartAggregateRef) without.getYField(1)).setAxisDescriptor(new AxisDescriptor());
      ((VSChartAggregateRef) withDc.getYField(1)).setAxisDescriptor(new AxisDescriptor());

      new ChangeSeparateStatusProcessor(without, new ChartDescriptor()).process(true, false);
      new ChangeSeparateStatusProcessor(withDc, new ChartDescriptor()).process(true, false);

      assertEquals(minimumOf(without, 1), minimumOf(withDc, 1));
      assertEquals(minimumOf(without, 0), minimumOf(withDc, 0));
      assertEquals(5, minimumOf(withDc, 1).intValue());
      assertTrue(withDc.isDesignSeparated());
   }

   @Test
   void designMultiStyleToggleUnderADateComparisonDoesNotFail() {
      VSChartInfo info = chart(true);
      info.setMultiStyles(true);

      assertDoesNotThrow(
         () -> new ChangeSeparateStatusProcessor(info, new ChartDescriptor()).process(false, true));
      assertTrue(info.isDesignMultiStyles());
   }
}
