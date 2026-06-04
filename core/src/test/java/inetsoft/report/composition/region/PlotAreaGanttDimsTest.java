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
package inetsoft.report.composition.region;

import inetsoft.uql.viewsheet.graph.ChartInfo;
import inetsoft.uql.viewsheet.graph.ChartRef;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("core")
class PlotAreaGanttDimsTest {
   @Test
   void yDimsMoveToHeadXDimsToTail() {
      // element.getDims() for a gantt with X bound puts X first, then Y.
      // The reorder must hoist Y to the front so the row identifier leads
      // the tooltip regardless of binding order.
      ChartInfo info = chartInfoWithY("reseller", "datagroup(city)");
      String[] result = PlotArea.ganttDimsYFirst(
         new String[] { "month", "reseller", "datagroup(city)" }, info);

      assertArrayEquals(
         new String[] { "reseller", "datagroup(city)", "month" }, result);
   }

   @Test
   void preservesRelativeOrderWithinHeadAndTail() {
      ChartInfo info = chartInfoWithY("y1", "y2");
      String[] result = PlotArea.ganttDimsYFirst(
         new String[] { "x1", "y1", "x2", "y2", "x3" }, info);

      assertArrayEquals(
         new String[] { "y1", "y2", "x1", "x2", "x3" }, result);
   }

   @Test
   void returnsUnchangedWhenNoDimMatchesY() {
      ChartInfo info = chartInfoWithY("reseller");
      String[] result = PlotArea.ganttDimsYFirst(
         new String[] { "month", "product" }, info);

      assertArrayEquals(new String[] { "month", "product" }, result);
   }

   @Test
   void handlesEmptyDims() {
      ChartInfo info = chartInfoWithY("reseller");
      String[] result = PlotArea.ganttDimsYFirst(new String[0], info);

      assertArrayEquals(new String[0], result);
   }

   @Test
   void innermostYDimIsLastRTYField() {
      // The innermost (plot-adjacent) Y dim is the last RTYField; it headlines
      // the gantt card tooltip as the row that identifies the hovered bar.
      ChartInfo info = chartInfoWithY("reseller", "datagroup(city)");
      String result = PlotArea.ganttInnermostYDim(
         info, Set.of("reseller", "datagroup(city)", "state"));

      assertEquals("datagroup(city)", result);
   }

   @Test
   void innermostYDimSkipsFieldsNotRendered() {
      // Walk outward to the first Y dim that actually renders a tooltip row.
      ChartInfo info = chartInfoWithY("reseller", "datagroup(city)");
      String result = PlotArea.ganttInnermostYDim(info, Set.of("reseller"));

      assertEquals("reseller", result);
   }

   @Test
   void innermostYDimNullWhenNoYBound() {
      ChartInfo info = chartInfoWithY();
      assertNull(PlotArea.ganttInnermostYDim(info, Set.of("state", "city")));
   }

   @Test
   void innermostYDimNullWhenNoYInAllfields() {
      ChartInfo info = chartInfoWithY("reseller");
      assertNull(PlotArea.ganttInnermostYDim(info, Set.of()));
   }

   @Test
   void contextDimsDropHeadlinePreservingOrder() {
      // ganttDimsYFirst order (outer Y, inner Y, then X); removing the innermost
      // Y headline leaves outer Y then X as tier-3 context.
      String[] result = PlotArea.ganttContextDims(
         new String[] { "reseller", "datagroup(city)", "state", "city" },
         "datagroup(city)");

      assertArrayEquals(new String[] { "reseller", "state", "city" }, result);
   }

   @Test
   void contextDimsUnchangedWhenHeadlineAbsent() {
      String[] result = PlotArea.ganttContextDims(
         new String[] { "state", "city" }, "reseller");

      assertArrayEquals(new String[] { "state", "city" }, result);
   }

   @Test
   void ganttCardLayoutComposesHeadlineDatesThenContext() {
      // Exercises the gantt card algorithm end-to-end the way getToolTip wires it:
      // resolve the innermost-Y headline, order rows [headline, dates, context,
      // aesthetics], size tier-2 to the date count, then render. Ties the helper
      // contracts (ganttInnermostYDim/ganttContextDims) to the tier output so a
      // regression in either the ordering or the grouping is caught together.
      String[] orderedDims = { "reseller", "datagroup(city)", "state" }; // ganttDimsYFirst order
      String[] dates = { "orderdate", "end", "milestone" };              // element.getVars()
      ChartInfo info = chartInfoWithY("reseller", "datagroup(city)");
      Set<String> allfields = Set.of("reseller", "datagroup(city)", "state",
         "orderdate", "end", "milestone", "product_name");

      String headline = PlotArea.ganttInnermostYDim(info, allfields);
      String[] context = PlotArea.ganttContextDims(orderedDims, headline);

      assertEquals("datagroup(city)", headline);
      assertArrayEquals(new String[] { "reseller", "state" }, context);

      IndexedSet<String> palette = new IndexedSet<>();
      ChartToolTip tip = new ChartToolTip();
      tip.setStyle(ChartInfo.TooltipStyle.CARD);
      tip.addTooltip(palette.put(headline), palette.put("Piscataway"));
      tip.addTooltip(palette.put("orderdate"), palette.put("2000-10-22"));
      tip.addTooltip(palette.put("end"), palette.put("2000-11-01"));
      tip.addTooltip(palette.put("milestone"), palette.put("2000-10-29"));
      tip.addTooltip(palette.put("reseller"), palette.put("false"));
      tip.addTooltip(palette.put("state"), palette.put("NJ"));
      tip.addTooltip(palette.put("product_name"), palette.put("Web Bridge"));
      tip.setGroupedTiers(true);
      tip.setTier2GroupSize(dates.length); // measurePairs

      String out = tip.getTooltip(palette);

      assertTrue(out.contains("<div class=\"tt-tier-1\">datagroup(city):&nbsp;Piscataway"),
                 "Innermost Y dim headlines at tier-1");
      assertTrue(out.contains("<div class=\"tt-tier-2\">orderdate:&nbsp;2000-10-22"));
      assertTrue(out.contains("<div class=\"tt-tier-2\">end:&nbsp;2000-11-01"));
      assertTrue(out.contains("<div class=\"tt-tier-2\">milestone:&nbsp;2000-10-29"),
                 "The date measures group at tier-2");
      assertTrue(out.contains("<div class=\"tt-tier-3\">reseller:&nbsp;false"),
                 "Outer Y dim drops to tier-3 context");
      assertTrue(out.contains("<div class=\"tt-tier-3\">state:&nbsp;NJ"),
                 "X dim drops to tier-3 context");
      assertTrue(out.contains("<div class=\"tt-tier-3\">product_name:&nbsp;Web Bridge"),
                 "Aesthetic drops to tier-3 context");
      assertFalse(out.contains("tt-tier-4"));
   }

   @Test
   void ganttCardNoYDimRendersUniformTier() {
      // No Y dim → ganttInnermostYDim returns null → the card falls back to a
      // uniform tier: every row at the same tier with no headline, mirroring the
      // getToolTip fallback. Uniform tier renders all rows at tier-2.
      ChartInfo info = chartInfoWithY();
      assertNull(PlotArea.ganttInnermostYDim(info, Set.of("orderdate", "end")));

      IndexedSet<String> palette = new IndexedSet<>();
      ChartToolTip tip = new ChartToolTip();
      tip.setStyle(ChartInfo.TooltipStyle.CARD);
      tip.addTooltip(palette.put("orderdate"), palette.put("2000-10-05"));
      tip.addTooltip(palette.put("end"), palette.put("2000-10-15"));
      tip.setUniformTier(true);

      String out = tip.getTooltip(palette);

      assertTrue(out.contains("<div class=\"tt-tier-2\">orderdate:&nbsp;2000-10-05"));
      assertTrue(out.contains("<div class=\"tt-tier-2\">end:&nbsp;2000-10-15"));
      assertFalse(out.contains("tt-tier-1"), "No headline div when no Y dim");
      assertFalse(out.contains("tt-tier-3"), "Uniform tier keeps every row equal");
   }

   private static ChartInfo chartInfoWithY(String... yFullNames) {
      ChartRef[] refs = new ChartRef[yFullNames.length];

      for(int i = 0; i < yFullNames.length; i++) {
         ChartRef ref = mock(ChartRef.class);
         when(ref.getFullName()).thenReturn(yFullNames[i]);
         refs[i] = ref;
      }

      ChartInfo info = mock(ChartInfo.class);
      when(info.getRTYFields()).thenReturn(refs);
      return info;
   }
}
