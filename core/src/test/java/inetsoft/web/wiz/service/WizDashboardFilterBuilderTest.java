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
package inetsoft.web.wiz.service;

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.asset.PhysicalBoundTableAssembly;
import inetsoft.uql.asset.SourceInfo;
import inetsoft.uql.asset.Worksheet;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.SelectionVSAssemblyInfo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class WizDashboardFilterBuilderTest {
   private final WizDashboardFilterBuilder builder = new WizDashboardFilterBuilder();

   @Test
   void categoricalFieldMakesASelectionList() {
      Viewsheet vs = new Viewsheet();
      // A field on NO table binds to nothing -> skipped, but the control TYPE is still decided
      // by data type. Assert via a package-visible helper the builder exposes for the type choice:
      AbstractSelectionVSAssembly a = builder.createControlForType(vs, "string", column("Region", "string"));
      assertTrue(a instanceof SelectionListVSAssembly);
   }

   @Test
   void dateAndNumericFieldsMakeRangeSliders() {
      Viewsheet vs = new Viewsheet();
      assertTrue(builder.createControlForType(vs, "date", column("OrderDate", "date")) instanceof TimeSliderVSAssembly);
      assertTrue(builder.createControlForType(vs, "integer", column("Qty", "integer")) instanceof TimeSliderVSAssembly);
   }

   @Test
   void theSharedFilterFactoryStaysInListModeForOtherCallers() {
      // createControlForType delegates to AddFilterService.createFilterAssembly, which the
      // non-dashboard add-filter flow also uses. Dashboard compaction must NOT leak into it.
      Viewsheet vs = new Viewsheet();
      SelectionListVSAssembly a =
         (SelectionListVSAssembly) builder.createControlForType(vs, "string", column("Region", "string"));
      assertEquals(SelectionVSAssemblyInfo.LIST_SHOW_TYPE, a.getSelectionListInfo().getShowType(),
         "the shared factory must keep StyleBI's default list rendering");
   }

   @Test
   void perChartCategoricalFilterRendersAsADropdownNotATallList() {
      // A per-chart filter sits INSIDE its chart's tile, so a multi-row checkbox list steals
      // height from the chart itself. Dropdown mode collapses it to a single title row
      // (SelectionListVSAssemblyInfo#getSizeScale pins the Y scale to 1 in that mode).
      Worksheet ws = new Worksheet();
      ws.addAssembly(physicalTable(ws, "CHART_FINAL", "category_name"));

      Viewsheet vs = new Viewsheet();

      WizDashboardFilterBuilder.FilterControlPlacement placement = builder.buildPerChart(vs, ws, 100, 200, 640, 40,
         new WizDashboardFilterBuilder.FilterRequest("category_name", "string", "Category"), "CHART_FINAL", null);

      assertNotNull(placement);
      SelectionListVSAssembly control = java.util.Arrays.stream(vs.getAssemblies())
         .filter(a -> a instanceof SelectionListVSAssembly)
         .map(a -> (SelectionListVSAssembly) a)
         .findFirst().orElseThrow();
      assertEquals(SelectionVSAssemblyInfo.DROPDOWN_SHOW_TYPE, control.getSelectionListInfo().getShowType(),
         "a per-chart categorical filter must render as a dropdown, not a multi-row list");
   }

   @Test
   void perChartDropdownTitleRowFillsTheReservedHeightSoNoGapShowsAboveTheChart() {
      // A dropdown draws ONLY its title row (default AssetUtil.defh = 20), ignoring the rest of the
      // assembly's pixel height. Reserving more than the row draws leaves the difference as a visible
      // gap between the filter and the chart it belongs to, breaking the single-enclosing-card look
      // applyGroupedCardStyle creates. Pin the title row to the reserved height so the two agree by
      // construction rather than by two magic numbers happening to match.
      Worksheet ws = new Worksheet();
      ws.addAssembly(physicalTable(ws, "CHART_FINAL", "category_name"));

      Viewsheet vs = new Viewsheet();

      builder.buildPerChart(vs, ws, 100, 200, 640, 28,
         new WizDashboardFilterBuilder.FilterRequest("category_name", "string", "Category"), "CHART_FINAL", null);

      SelectionListVSAssembly control = java.util.Arrays.stream(vs.getAssemblies())
         .filter(a -> a instanceof SelectionListVSAssembly)
         .map(a -> (SelectionListVSAssembly) a)
         .findFirst().orElseThrow();
      // The DESIGN value is the one that survives save/reopen, which is how a composed dashboard is used.
      assertEquals(28, control.getSelectionListInfo().getTitleHeightValue(),
         "the dropdown's title row must fill the reserved height exactly, or the leftover shows as a gap");
   }

   @Test
   void perChartRangeSliderIsLeftAloneByTheDropdownTreatment() {
      // TimeSlider has no show type -- it is already a single-row control. The dropdown
      // treatment must not touch it (or throw when it is the control that was created).
      Worksheet ws = new Worksheet();
      ws.addAssembly(physicalTable(ws, "CHART_FINAL", "order_qty"));

      Viewsheet vs = new Viewsheet();

      WizDashboardFilterBuilder.FilterControlPlacement placement = builder.buildPerChart(vs, ws, 0, 0, 640, 40,
         new WizDashboardFilterBuilder.FilterRequest("order_qty", "integer", "Qty"), "CHART_FINAL", null);

      assertNotNull(placement);
      assertTrue(java.util.Arrays.stream(vs.getAssemblies()).anyMatch(a -> a instanceof TimeSliderVSAssembly),
         "a numeric per-chart filter must still be a range slider");
   }

   @Test
   void mergedChartTableNamesCollectsOnlyChartAssembliesOwnTables() {
      Viewsheet vs = new Viewsheet();
      ChartVSAssembly radar = new ChartVSAssembly(vs, "RadarChart");
      boundToTable(radar, "RADAR_FINAL");
      vs.addAssembly(radar);
      ChartVSAssembly mr = new ChartVSAssembly(vs, "MrChart");
      boundToTable(mr, "MR_FINAL");
      vs.addAssembly(mr);
      // A non-chart assembly must not contribute a table name, even though it also implements
      // BindableVSAssembly -- only ChartVSAssembly is considered (see the method's javadoc).
      TextVSAssembly text = new TextVSAssembly(vs, "Title");
      vs.addAssembly(text);

      List<String> names = builder.mergedChartTableNames(vs);

      assertEquals(List.of("RADAR_FINAL", "MR_FINAL"), names);
   }

   @Test
   void buildBindsOnlyToTheChartsOwnTableNotAnUnrelatedTableSharingTheColumn() {
      Worksheet ws = new Worksheet();
      ws.addAssembly(physicalTable(ws, "CHART_FINAL", "category_name", "norm_revenue"));
      // Simulates the shared global-aggregate table upstream of a chart's normalization step:
      // it also exposes category_name, but no chart is bound to it directly, so it must never
      // receive the filter (that was the root cause of the live "No data" regression: binding a
      // filter to this kind of table cascades into the aggregate it feeds).
      ws.addAssembly(physicalTable(ws, "GLOBAL_STATS", "category_name", "min_revenue", "max_revenue"));

      Viewsheet vs = new Viewsheet();
      ChartVSAssembly chart = new ChartVSAssembly(vs, "RadarChart");
      boundToTable(chart, "CHART_FINAL");
      vs.addAssembly(chart);

      WizDashboardFilterBuilder.FilterResult result = builder.build(
         vs, ws, List.of(new WizDashboardFilterBuilder.FilterRequest("category_name", "string", "Category")),
         WizDashboardService.CANVAS_MARGIN);

      assertEquals(List.of("category_name"), result.applied());
      assertTrue(result.skipped().isEmpty());

      AbstractSelectionVSAssembly control = java.util.Arrays.stream(vs.getAssemblies())
         .filter(a -> a instanceof AbstractSelectionVSAssembly)
         .map(a -> (AbstractSelectionVSAssembly) a)
         .findFirst().orElseThrow();
      assertEquals(List.of("CHART_FINAL"), control.getTableNames(),
         "must bind only to the chart's own table, never GLOBAL_STATS");
   }

   @Test
   void preAggregationFilterBindsToTheRawSourceTableNotTheChartsFinalTable() {
      // A column (order `state`) present on the RAW source but NOT on the chart's aggregated final
      // table. Post-aggregation (default) can't reach it -> skipped. Pre-aggregation binds to the
      // raw source (a WHERE before the group-by) so the aggregate re-computes over the subset.
      Worksheet ws = new Worksheet();
      ws.addAssembly(physicalTable(ws, "SO_RAW", "date_order", "amount_total", "state"));
      // The chart's own final (aggregated) table: carries only the grouped dim + measure, no state.
      ws.addAssembly(physicalTable(ws, "SO_REVENUE_BY_QTR", "Quarter(date_order)", "total_revenue"));

      Viewsheet vs = new Viewsheet();
      ChartVSAssembly chart = new ChartVSAssembly(vs, "RevenueChart");
      boundToTable(chart, "SO_REVENUE_BY_QTR");
      vs.addAssembly(chart);

      // Post-aggregation (default): state isn't on the chart's final table -> skipped, nothing added.
      WizDashboardFilterBuilder.FilterResult post = builder.build(
         vs, ws, List.of(new WizDashboardFilterBuilder.FilterRequest("state", "string", "Status")),
         WizDashboardService.CANVAS_MARGIN);
      assertEquals(List.of("state"), post.skipped(), "post-agg can't reach a column not on the final table");
      assertTrue(post.applied().isEmpty());

      // Pre-aggregation: binds to the raw source table that carries state.
      WizDashboardFilterBuilder.FilterResult pre = builder.build(
         vs, ws, List.of(new WizDashboardFilterBuilder.FilterRequest("state", "string", "Status", true)),
         WizDashboardService.CANVAS_MARGIN);
      assertEquals(List.of("state"), pre.applied());
      assertTrue(pre.skipped().isEmpty());

      AbstractSelectionVSAssembly control = java.util.Arrays.stream(vs.getAssemblies())
         .filter(a -> a instanceof AbstractSelectionVSAssembly)
         .map(a -> (AbstractSelectionVSAssembly) a)
         .findFirst().orElseThrow();
      assertEquals(List.of("SO_RAW"), control.getTableNames(),
         "pre-aggregation must bind to the raw source table carrying the column");
   }

   @Test
   void buildReturnsThePlacementOfEachAppliedControl() {
      Worksheet ws = new Worksheet();
      ws.addAssembly(physicalTable(ws, "CHART_FINAL", "category_name"));

      Viewsheet vs = new Viewsheet();
      ChartVSAssembly chart = new ChartVSAssembly(vs, "RadarChart");
      boundToTable(chart, "CHART_FINAL");
      vs.addAssembly(chart);

      WizDashboardFilterBuilder.FilterResult result = builder.build(
         vs, ws, List.of(new WizDashboardFilterBuilder.FilterRequest("category_name", "string", "Category")),
         WizDashboardService.CANVAS_MARGIN);

      assertEquals(1, result.placements().size());
      assertNotNull(result.placements().get(0).assemblyName());
      assertEquals(new java.awt.Point(WizDashboardService.CANVAS_MARGIN, WizDashboardService.CANVAS_MARGIN),
         result.placements().get(0).position());
   }

   @Test
   void firstControlIsOffsetByTheCanvasMarginNotFlushAgainstTheEdge() {
      Worksheet ws = new Worksheet();
      ws.addAssembly(physicalTable(ws, "CHART_FINAL", "category_name"));

      Viewsheet vs = new Viewsheet();
      ChartVSAssembly chart = new ChartVSAssembly(vs, "RadarChart");
      boundToTable(chart, "CHART_FINAL");
      vs.addAssembly(chart);

      builder.build(vs, ws, List.of(new WizDashboardFilterBuilder.FilterRequest("category_name", "string", "Category")),
         WizDashboardService.CANVAS_MARGIN);

      AbstractSelectionVSAssembly control = java.util.Arrays.stream(vs.getAssemblies())
         .filter(a -> a instanceof AbstractSelectionVSAssembly)
         .map(a -> (AbstractSelectionVSAssembly) a)
         .findFirst().orElseThrow();
      assertEquals(WizDashboardService.CANVAS_MARGIN, control.getPixelOffset().x,
         "must sit at the passed startX (the merged charts' left edge)");
      assertEquals(WizDashboardService.CANVAS_MARGIN, control.getPixelOffset().y,
         "must not sit flush against the canvas's top edge");
   }

   @Test
   void buildFilterBarBandAddsATintedBandAndAThinDividerSpanningTheGivenWidth() {
      Viewsheet vs = new Viewsheet();

      List<WizDashboardFilterBuilder.FilterControlPlacement> placements =
         builder.buildFilterBarBand(vs, 48, 12, 1800, 124);

      // Two rectangles: the band (full height) and a thin divider along its bottom edge.
      assertEquals(2, placements.size());
      assertEquals(new java.awt.Point(48, 12), placements.get(0).position());
      assertEquals(new java.awt.Dimension(1800, 124), placements.get(0).size(), "band spans the given width x height");
      assertEquals(new java.awt.Point(48, 12 + 124 - 2), placements.get(1).position(),
         "divider sits along the band's bottom edge");
      assertEquals(new java.awt.Dimension(1800, 2), placements.get(1).size(), "divider is a 2px-tall full-width bar");

      // Both are borderless rectangles rendered as a solid background fill.
      long rectangles = java.util.Arrays.stream(vs.getAssemblies())
         .filter(a -> a instanceof RectangleVSAssembly).count();
      assertEquals(2, rectangles);

      for(inetsoft.uql.asset.Assembly a : vs.getAssemblies()) {
         if(a instanceof RectangleVSAssembly rect) {
            inetsoft.uql.viewsheet.VSFormat fmt = rect.getVSAssemblyInfo().getFormat().getUserDefinedFormat();
            assertNotNull(fmt.getBackground(), "rectangle must carry a fill background so it renders");
         }
      }
   }

   @Test
   void buildPerChartBindsOnlyToTheSpecifiedTableEvenWhenAnotherTableExposesTheSameColumn() {
      Worksheet ws = new Worksheet();
      ws.addAssembly(physicalTable(ws, "CHART_FINAL", "product_name"));
      // A DIFFERENT table also exposes "product_name" -- buildPerChart must never match it,
      // since only ONE table (the caller-specified chartTableName) is ever a candidate.
      ws.addAssembly(physicalTable(ws, "OTHER_CHART_FINAL", "product_name"));

      Viewsheet vs = new Viewsheet();

      WizDashboardFilterBuilder.FilterControlPlacement placement = builder.buildPerChart(vs, ws, 100, 200, 640, 120,
         new WizDashboardFilterBuilder.FilterRequest("product_name", "string", "Product"), "CHART_FINAL", null);

      assertNotNull(placement);
      assertEquals(new java.awt.Point(100, 200), placement.position());
      assertEquals(new java.awt.Dimension(640, 120), placement.size(),
         "the control must span the chart's width and the reserved header height");

      AbstractSelectionVSAssembly control = java.util.Arrays.stream(vs.getAssemblies())
         .filter(a -> a instanceof AbstractSelectionVSAssembly)
         .map(a -> (AbstractSelectionVSAssembly) a)
         .findFirst().orElseThrow();
      assertEquals(List.of("CHART_FINAL"), control.getTableNames(),
         "must bind only to the specified chart table, never any other table exposing the same column");
      assertEquals(100, control.getPixelOffset().x);
      assertEquals(200, control.getPixelOffset().y);
      assertEquals(640, control.getPixelSize().width);
      assertEquals(120, control.getPixelSize().height);
   }

   @Test
   void buildPerChartReturnsNullWhenSkipped() {
      Worksheet ws = new Worksheet();
      ws.addAssembly(physicalTable(ws, "CHART_FINAL", "category_name"));

      Viewsheet vs = new Viewsheet();

      WizDashboardFilterBuilder.FilterControlPlacement placement = builder.buildPerChart(vs, ws, 0, 0, 640, 120,
         new WizDashboardFilterBuilder.FilterRequest("product_name", "string", "Product"), "CHART_FINAL", null);

      assertNull(placement);
      assertEquals(0, vs.getAssemblies().length, "no control should be added when the field isn't on the table");
   }

   @Test
   void buildPerChartWithAnUnresolvableChartAssemblyNameStillCreatesTheControl() {
      // A null/unresolvable chartAssemblyName must never block the filter control itself from
      // being created -- only the visual-grouping styling is skipped.
      Worksheet ws = new Worksheet();
      ws.addAssembly(physicalTable(ws, "CHART_FINAL", "category_name"));

      Viewsheet vs = new Viewsheet();

      WizDashboardFilterBuilder.FilterControlPlacement placement = builder.buildPerChart(vs, ws, 0, 0, 640, 120,
         new WizDashboardFilterBuilder.FilterRequest("category_name", "string", "Category"),
         "CHART_FINAL", "NoSuchAssembly");

      assertNotNull(placement);
      assertEquals(1, vs.getAssemblies().length);
   }

   @Test
   void buildPerChartStylesTheControlAndItsChartAsOneGroupedCardWhenTheChartResolves() {
      Worksheet ws = new Worksheet();
      ws.addAssembly(physicalTable(ws, "CHART_FINAL", "category_name"));

      Viewsheet vs = new Viewsheet();
      ChartVSAssembly chart = new ChartVSAssembly(vs, "RadarChart");
      boundToTable(chart, "CHART_FINAL");
      vs.addAssembly(chart);

      builder.buildPerChart(vs, ws, 100, 200, 640, 120,
         new WizDashboardFilterBuilder.FilterRequest("category_name", "string", "Category"),
         "CHART_FINAL", "RadarChart");

      AbstractSelectionVSAssembly control = java.util.Arrays.stream(vs.getAssemblies())
         .filter(a -> a instanceof AbstractSelectionVSAssembly)
         .map(a -> (AbstractSelectionVSAssembly) a)
         .findFirst().orElseThrow();

      inetsoft.uql.viewsheet.VSFormat controlFormat = control.getVSAssemblyInfo().getFormat().getUserDefinedFormat();
      inetsoft.uql.viewsheet.VSFormat chartFormat = chart.getVSAssemblyInfo().getFormat().getUserDefinedFormat();

      assertNotNull(controlFormat.getBackground());
      assertEquals(controlFormat.getBackground(), chartFormat.getBackground(),
         "control and chart must share the same card background so they read as one unit");

      // The control's bottom border and the chart's top border abut directly -- both must be
      // borderless there so there's no doubled seam line between them.
      assertEquals(inetsoft.report.StyleConstants.NO_BORDER, controlFormat.getBorders().bottom);
      assertEquals(inetsoft.report.StyleConstants.NO_BORDER, chartFormat.getBorders().top);
      // The OUTER edges of the pair are bordered.
      assertEquals(inetsoft.report.StyleConstants.THIN_LINE, controlFormat.getBorders().top);
      assertEquals(inetsoft.report.StyleConstants.THIN_LINE, chartFormat.getBorders().bottom);
   }

   // ChartVSAssembly.setTableName(String) silently no-ops when getSourceInfo() is still null (it
   // builds a local SourceInfo but never calls setSourceInfo(...) to store it back) -- harmless
   // in production, where a chart's SourceInfo is always initialized before setTableName is
   // ever called, but a real gap for a bare `new ChartVSAssembly(...)` test fixture. Set the
   // SourceInfo directly instead of relying on setTableName here.
   private static void boundToTable(ChartVSAssembly chart, String tableName) {
      SourceInfo source = new SourceInfo(SourceInfo.ASSET, null, tableName);
      chart.setSourceInfo(source);
   }

   private static PhysicalBoundTableAssembly physicalTable(Worksheet ws, String assemblyName, String... columns) {
      PhysicalBoundTableAssembly table = new PhysicalBoundTableAssembly(ws, assemblyName);
      SourceInfo si = new SourceInfo(SourceInfo.PHYSICAL_TABLE, "postgres", "public." + assemblyName);
      table.setSourceInfo(si);

      ColumnSelection cs = new ColumnSelection();

      for(String name : columns) {
         AttributeRef ref = new AttributeRef(null, name);
         ref.setDataType(XSchema.STRING);
         ColumnRef col = new ColumnRef(ref);
         col.setDataType(XSchema.STRING);
         cs.addAttribute(col);
      }

      table.setColumnSelection(cs, false);
      return table;
   }

   // Helper mirrors AddFilterService.buildColumnRef (AttributeRef + ColumnRef with dataType).
   // NOTE: ColumnRef lives in inetsoft.uql.asset (not inetsoft.uql.erm as the task brief sketch
   // assumed) — confirmed against AddFilterService's imports in Step 1.
   private static inetsoft.uql.erm.DataRef column(String name, String dtype) {
      inetsoft.uql.erm.AttributeRef attr = new inetsoft.uql.erm.AttributeRef(name);
      attr.setDataType(dtype);
      inetsoft.uql.asset.ColumnRef col = new inetsoft.uql.asset.ColumnRef(attr);
      col.setDataType(dtype);
      return col;
   }
}
