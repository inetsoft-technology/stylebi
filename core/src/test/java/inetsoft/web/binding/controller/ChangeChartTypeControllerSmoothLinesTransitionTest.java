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
package inetsoft.web.binding.controller;

import inetsoft.uql.viewsheet.graph.GraphTypes;
import inetsoft.uql.viewsheet.graph.PlotDescriptor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the smoothLines defaulting/reset matrix applied when a chart's type changes.
 * Covers the Area↔Line, Circular↔Line, and Area↔Circular transitions.
 */
class ChangeChartTypeControllerSmoothLinesTransitionTest {
   private static PlotDescriptor descWith(boolean smoothLines) {
      PlotDescriptor pd = new PlotDescriptor();
      pd.setSmoothLines(smoothLines);
      return pd;
   }

   // --- Default-on transitions ---

   @Test
   void barToArea_setsSmoothLinesTrue() {
      PlotDescriptor pd = descWith(false);
      ChangeChartTypeController.applySmoothLinesTransition(
         GraphTypes.CHART_BAR, GraphTypes.CHART_AREA, pd);
      assertTrue(pd.isSmoothLines(),
         "first transition into Area should default smoothLines on");
   }

   @Test
   void lineToStackedArea_setsSmoothLinesTrue() {
      PlotDescriptor pd = descWith(false);
      ChangeChartTypeController.applySmoothLinesTransition(
         GraphTypes.CHART_LINE, GraphTypes.CHART_AREA_STACK, pd);
      assertTrue(pd.isSmoothLines(),
         "first transition into stacked Area should default smoothLines on");
   }

   @Test
   void barToCircular_setsSmoothLinesTrue() {
      PlotDescriptor pd = descWith(false);
      ChangeChartTypeController.applySmoothLinesTransition(
         GraphTypes.CHART_BAR, GraphTypes.CHART_CIRCULAR, pd);
      assertTrue(pd.isSmoothLines(),
         "first transition into Circular should default smoothLines on");
   }

   @Test
   void networkToCircular_setsSmoothLinesTrue() {
      PlotDescriptor pd = descWith(false);
      ChangeChartTypeController.applySmoothLinesTransition(
         GraphTypes.CHART_NETWORK, GraphTypes.CHART_CIRCULAR, pd);
      assertTrue(pd.isSmoothLines(),
         "Network → Circular is a first-into-circular transition; defaults on");
   }

   // --- Reset transitions ---

   @Test
   void areaToLine_resetsSmoothLinesFalse() {
      PlotDescriptor pd = descWith(true);
      ChangeChartTypeController.applySmoothLinesTransition(
         GraphTypes.CHART_AREA, GraphTypes.CHART_LINE, pd);
      assertFalse(pd.isSmoothLines(),
         "Area → Line must clear smoothLines so the line chart isn't silently smooth");
   }

   @Test
   void stackedAreaToStackedLine_resetsSmoothLinesFalse() {
      PlotDescriptor pd = descWith(true);
      ChangeChartTypeController.applySmoothLinesTransition(
         GraphTypes.CHART_AREA_STACK, GraphTypes.CHART_LINE_STACK, pd);
      assertFalse(pd.isSmoothLines());
   }

   @Test
   void circularToLine_resetsSmoothLinesFalse() {
      PlotDescriptor pd = descWith(true);
      ChangeChartTypeController.applySmoothLinesTransition(
         GraphTypes.CHART_CIRCULAR, GraphTypes.CHART_LINE, pd);
      assertFalse(pd.isSmoothLines(),
         "Circular → Line must clear smoothLines so the line chart isn't silently smooth");
   }

   @Test
   void circularToStackedLine_resetsSmoothLinesFalse() {
      PlotDescriptor pd = descWith(true);
      ChangeChartTypeController.applySmoothLinesTransition(
         GraphTypes.CHART_CIRCULAR, GraphTypes.CHART_LINE_STACK, pd);
      assertFalse(pd.isSmoothLines());
   }

   // --- No-op transitions ---

   @Test
   void areaToArea_preservesUserSetting() {
      PlotDescriptor offToOff = descWith(false);
      ChangeChartTypeController.applySmoothLinesTransition(
         GraphTypes.CHART_AREA, GraphTypes.CHART_AREA_STACK, offToOff);
      assertFalse(offToOff.isSmoothLines(),
         "Area → Area is not a first-into transition; user's off setting must persist");

      PlotDescriptor onToOn = descWith(true);
      ChangeChartTypeController.applySmoothLinesTransition(
         GraphTypes.CHART_AREA, GraphTypes.CHART_AREA_STACK, onToOn);
      assertTrue(onToOn.isSmoothLines(),
         "Area → Area is not a first-into transition; user's on setting must persist");
   }

   @Test
   void circularToCircular_preservesUserSetting() {
      PlotDescriptor pd = descWith(false);
      ChangeChartTypeController.applySmoothLinesTransition(
         GraphTypes.CHART_CIRCULAR, GraphTypes.CHART_CIRCULAR, pd);
      assertFalse(pd.isSmoothLines(),
         "same-type transition must not toggle smoothLines");
   }

   @Test
   void areaToCircular_setsSmoothLinesTrue() {
      // Both branches happen to overlap here: area→circular is first-into-circular.
      PlotDescriptor pd = descWith(false);
      ChangeChartTypeController.applySmoothLinesTransition(
         GraphTypes.CHART_AREA, GraphTypes.CHART_CIRCULAR, pd);
      assertTrue(pd.isSmoothLines());
   }

   @Test
   void circularToArea_setsSmoothLinesTrue() {
      PlotDescriptor pd = descWith(false);
      ChangeChartTypeController.applySmoothLinesTransition(
         GraphTypes.CHART_CIRCULAR, GraphTypes.CHART_AREA, pd);
      assertTrue(pd.isSmoothLines(),
         "Circular → Area is a first-into-area transition; defaults on");
   }

   @Test
   void barToLine_doesNotTouchSmoothLines() {
      PlotDescriptor on = descWith(true);
      ChangeChartTypeController.applySmoothLinesTransition(
         GraphTypes.CHART_BAR, GraphTypes.CHART_LINE, on);
      assertTrue(on.isSmoothLines(),
         "Bar → Line is neither a default-on nor a reset transition");

      PlotDescriptor off = descWith(false);
      ChangeChartTypeController.applySmoothLinesTransition(
         GraphTypes.CHART_BAR, GraphTypes.CHART_LINE, off);
      assertFalse(off.isSmoothLines());
   }

   @Test
   void circularToNetwork_leavesSmoothLinesUnchanged() {
      // Network/Tree don't render with smoothLines, so the value is irrelevant for rendering.
      // The transition is intentionally a no-op (no rendering effect, no UI surface that
      // exposes the field for these types).
      PlotDescriptor pd = descWith(true);
      ChangeChartTypeController.applySmoothLinesTransition(
         GraphTypes.CHART_CIRCULAR, GraphTypes.CHART_NETWORK, pd);
      assertTrue(pd.isSmoothLines(),
         "no reset is wired for Circular → Network because the flag has no rendering "
         + "effect on Network and the checkbox is hidden");
   }
}
