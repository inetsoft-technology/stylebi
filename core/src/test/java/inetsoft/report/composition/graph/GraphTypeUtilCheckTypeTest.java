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
package inetsoft.report.composition.graph;

import inetsoft.uql.viewsheet.graph.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GraphTypeUtilCheckTypeTest {

   // -----------------------------------------------------------------------
   // Single-style (non-multi-styles)
   // -----------------------------------------------------------------------

   @Test
   void singleStyle_explicitBarType() {
      VSChartInfo info = new VSChartInfo();
      info.setChartType(GraphTypes.CHART_BAR);

      assertTrue(GraphTypeUtil.checkType(info, GraphTypes::isBar));
      assertFalse(GraphTypeUtil.checkType(info, GraphTypes::isLine));
   }

   @Test
   void singleStyle_autoResolvesToRTType() {
      VSChartInfo info = new VSChartInfo();
      info.setChartType(GraphTypes.CHART_AUTO);
      info.setRTChartType(GraphTypes.CHART_BAR);

      assertTrue(GraphTypeUtil.checkType(info, GraphTypes::isBar));
   }

   // -----------------------------------------------------------------------
   // Multi-styles: per-field type resolution
   // -----------------------------------------------------------------------

   @Test
   void multiStyles_fieldExplicitBar() {
      VSChartInfo info = new VSChartInfo();
      info.setChartType(GraphTypes.CHART_BAR);
      info.setMultiStyles(true);

      VSChartAggregateRef yRef = createAggRef(GraphTypes.CHART_BAR);
      info.addYField(yRef);

      assertTrue(GraphTypeUtil.checkType(info, GraphTypes::isBar));
   }

   @Test
   void multiStyles_fieldAutoWithOverallBar_usesRTType() {
      // Bug case: overall is BAR, per-field is AUTO (resolved to bar at runtime).
      // Old code used cinfo.getChartType() (BAR != AUTO) → per-field getChartType() = AUTO → failed.
      VSChartInfo info = new VSChartInfo();
      info.setChartType(GraphTypes.CHART_BAR);
      info.setMultiStyles(true);

      VSChartAggregateRef yRef = createAggRef(GraphTypes.CHART_AUTO);
      yRef.setRTChartType(GraphTypes.CHART_BAR);
      info.addYField(yRef);

      assertTrue(GraphTypeUtil.checkType(info, GraphTypes::isBar),
         "AUTO field with RT type BAR should pass isBar check");
   }

   @Test
   void multiStyles_fieldAutoWithOverallAuto_usesRTType() {
      VSChartInfo info = new VSChartInfo();
      info.setChartType(GraphTypes.CHART_AUTO);
      info.setMultiStyles(true);

      VSChartAggregateRef yRef = createAggRef(GraphTypes.CHART_AUTO);
      yRef.setRTChartType(GraphTypes.CHART_LINE);
      info.addYField(yRef);

      assertTrue(GraphTypeUtil.checkType(info, GraphTypes::isLine));
      assertFalse(GraphTypeUtil.checkType(info, GraphTypes::isBar));
   }

   @Test
   void multiStyles_mixedTypes_returnsTrueIfAnyMatches() {
      VSChartInfo info = new VSChartInfo();
      info.setChartType(GraphTypes.CHART_AUTO);
      info.setMultiStyles(true);

      VSChartAggregateRef barRef = createAggRef(GraphTypes.CHART_BAR);
      VSChartAggregateRef lineRef = createAggRef(GraphTypes.CHART_LINE);
      info.addYField(barRef);
      info.addYField(lineRef);

      assertTrue(GraphTypeUtil.checkType(info, GraphTypes::isBar));
      assertTrue(GraphTypeUtil.checkType(info, GraphTypes::isLine));
      assertFalse(GraphTypeUtil.checkType(info, GraphTypes::isArea));
   }

   @Test
   void multiStyles_xFieldsAlsoChecked() {
      VSChartInfo info = new VSChartInfo();
      info.setChartType(GraphTypes.CHART_AUTO);
      info.setMultiStyles(true);

      VSChartAggregateRef xRef = createAggRef(GraphTypes.CHART_BAR);
      info.addXField(xRef);

      assertTrue(GraphTypeUtil.checkType(info, GraphTypes::isBar));
   }

   // -----------------------------------------------------------------------
   // Date comparison: single-style chart made multi by DC runtime
   // -----------------------------------------------------------------------

   @Test
   void dcApplied_singleStyleBar_notTreatedAsMultiStyles() {
      // DC sets runtimeMulti=true on a single-style bar chart.
      // checkType should ignore the DC override and check the overall type.
      VSChartInfo info = new VSChartInfo();
      info.setChartType(GraphTypes.CHART_BAR);
      info.setRuntimeDateComparisonRefs(new ChartRef[]{ new VSChartDimensionRef() });
      info.setRuntimeMulti(true);

      assertTrue(GraphTypeUtil.checkType(info, GraphTypes::isBar),
         "DC should not hide bar type from single-style chart");
   }

   @Test
   void dcApplied_genuineMultiStyles_stillChecksPerField() {
      // Chart that was already multi-styles before DC should still iterate fields.
      VSChartInfo info = new VSChartInfo();
      info.setChartType(GraphTypes.CHART_AUTO);
      info.setMultiStyles(true);
      info.setRuntimeDateComparisonRefs(new ChartRef[]{ new VSChartDimensionRef() });
      info.setRuntimeMulti(true);

      VSChartAggregateRef yRef = createAggRef(GraphTypes.CHART_LINE);
      info.addYField(yRef);

      assertTrue(GraphTypeUtil.checkType(info, GraphTypes::isLine));
      assertFalse(GraphTypeUtil.checkType(info, GraphTypes::isBar));
   }

   @Test
   void dcApplied_lineChart_rtChangedToBar_returnsTrue() {
      // Non-value+ DC on a LINE chart: DC sets getRTChartType() to BAR.
      // Bar rounding UI should be visible.
      VSChartInfo info = new VSChartInfo();
      info.setChartType(GraphTypes.CHART_LINE);
      info.setRTChartType(GraphTypes.CHART_BAR);
      info.setRuntimeDateComparisonRefs(new ChartRef[]{ new VSChartDimensionRef() });
      info.setRuntimeMulti(false);

      assertTrue(GraphTypeUtil.checkType(info, GraphTypes::isBar),
         "DC-created bars on a LINE chart should pass isBar check");
      assertTrue(GraphTypeUtil.checkType(info, ctype ->
               (GraphTypes.isBar(ctype) || GraphTypes.isInterval(ctype)) &&
               !GraphTypes.is3DBar(ctype) && !GraphTypes.isPareto(ctype) &&
               !GraphTypes.isWaterfall(ctype) && !GraphTypes.isFunnel(ctype)),
         "barCornerRadiusVisible predicate should be true for DC bar on LINE chart");
   }

   @Test
   void dcApplied_lineChart_rtStillLine_returnsFalse() {
      // DC applied but RT type not changed to BAR (e.g., value+ with LINE output).
      // Bar rounding UI should remain hidden.
      VSChartInfo info = new VSChartInfo();
      info.setChartType(GraphTypes.CHART_LINE);
      info.setRTChartType(GraphTypes.CHART_LINE);
      info.setRuntimeDateComparisonRefs(new ChartRef[]{ new VSChartDimensionRef() });
      info.setRuntimeMulti(true);

      assertFalse(GraphTypeUtil.checkType(info, GraphTypes::isBar),
         "DC on LINE chart where RT stays LINE should not pass isBar");
   }

   // -----------------------------------------------------------------------
   // Edge cases
   // -----------------------------------------------------------------------

   @Test
   void nullChartInfo_returnsFalse() {
      assertFalse(GraphTypeUtil.checkType(null, GraphTypes::isBar));
   }

   @Test
   void multiStyles_noAggregateFields_returnsFalse() {
      VSChartInfo info = new VSChartInfo();
      info.setChartType(GraphTypes.CHART_BAR);
      info.setMultiStyles(true);
      // no fields added

      assertFalse(GraphTypeUtil.checkType(info, GraphTypes::isBar));
   }

   @Test
   void multiStyles_fieldAutoWithRTNotSet_returnsFalseForBar() {
      // RT type defaults to CHART_AUTO when not set — isBar(AUTO) is false
      VSChartInfo info = new VSChartInfo();
      info.setChartType(GraphTypes.CHART_BAR);
      info.setMultiStyles(true);

      VSChartAggregateRef yRef = createAggRef(GraphTypes.CHART_AUTO);
      // don't set RT type — defaults to CHART_AUTO
      info.addYField(yRef);

      assertFalse(GraphTypeUtil.checkType(info, GraphTypes::isBar),
         "AUTO field without RT type set should not pass isBar");
      assertTrue(GraphTypeUtil.checkType(info, GraphTypes::isAuto),
         "AUTO field without RT type should pass isAuto");
   }

   // -----------------------------------------------------------------------
   // Helper
   // -----------------------------------------------------------------------

   private static VSChartAggregateRef createAggRef(int chartType) {
      VSChartAggregateRef ref = new VSChartAggregateRef();
      ref.setChartType(chartType);
      return ref;
   }
}
