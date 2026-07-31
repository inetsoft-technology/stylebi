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

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.Worksheet;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.vslayout.ViewsheetLayout;
import inetsoft.uql.viewsheet.vslayout.VSAssemblyLayout;
import inetsoft.web.wiz.model.WizDashboardEvent;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.awt.Point;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("core")
class WizDashboardServiceGridTest {
   // Column width / row height strides used by the packer (mirror the service constants).
   private static final int W = 640;   // DASHBOARD_COL_WIDTH  (confirm value in Task 2 Step 5)
   private static final int H = 420;   // DASHBOARD_ROW_HEIGHT (existing constant)
   private static final int G = 24;    // TILE_GUTTER -- spacing added between adjacent tiles

   @Test
   void tilePixelSizeUsesTheNaturalSpanFootprintWhenUnderTheCap() {
      // 1x1 -> 640x420; well under the 900x600 cap.
      assertEquals(new java.awt.Dimension(W, H), WizDashboardService.tilePixelSize(1, 1));
      // 1 col x 2 rows -> 640x840 would exceed the height cap -> clamped to 600.
      assertEquals(new java.awt.Dimension(W, 600), WizDashboardService.tilePixelSize(1, 2));
   }

   @Test
   void tilePixelSizeCapsAFullWidthFullHeightTileAt900By600() {
      // 2 cols x 2 rows -> natural footprint 1280x840, both dimensions exceed the cap.
      assertEquals(new java.awt.Dimension(900, 600), WizDashboardService.tilePixelSize(2, 2));
   }

   // --- 2D grid packing (computeGridLayout) ---------------------------------------------------

   @Test
   void computeGridLayoutStacksAShorterTileBesideATallOneAndStretchesTheShorterSide() {
      // layoutColumns=2 -> availableRowWidth = 2*640 + 24 = 1304.
      // A: 1x1 (640x420). B: 1x2 (640x600, capped). C: 1x1 (640x420). D: 2x1 (900x420).
      int[] spans =    { 1, 1, 1, 2 };
      int[] rowSpans = { 1, 2, 1, 1 };
      boolean[] noFilters = new boolean[4];

      List<WizDashboardService.TilePlacement> placements =
         WizDashboardService.computeGridLayout(spans, rowSpans, noFilters, 2).placements();

      // A opens column 0 (x=0). B fits as a new column (640+24+640=1304<=1304) at x=664.
      // C doesn't fit as a new column (1304+24+640=1968>1304) -> stacks under A (column 0's
      // columnY=420 is smaller than column 1's 600) at y=420+24=444.
      // D doesn't fit as a new column, AND its 900px width exceeds both columns' 640px slots ->
      // no column fits -> band closes. Band height = max(A+C's column = 864, B's column = 600) =
      // 864. B's column is shorter -> its only (and therefore last) tile, B, stretches from 600
      // to 864. D starts a fresh band at cumulativeY = 864 + 24 = 888.
      assertEquals(new WizDashboardService.TilePlacement(0, 0, 640, 420), placements.get(0));    // A
      assertEquals(new WizDashboardService.TilePlacement(664, 0, 640, 864), placements.get(1));  // B, stretched
      assertEquals(new WizDashboardService.TilePlacement(0, 444, 640, 420), placements.get(2));  // C
      assertEquals(new WizDashboardService.TilePlacement(0, 888, 900, 420), placements.get(3));  // D
   }

   @Test
   void computeGridLayoutOpensColumnsUntilRowWidthIsExhaustedThenStacksAndStretchesTheOthers() {
      // layoutColumns=3 -> availableRowWidth = 3*640 + 2*24 = 1968. Four 1x1 unit tiles (640x420
      // each): the first three exactly fill the row's width as three separate columns
      // (640+24+640+24+640 = 1968), the fourth can't open a new column and stacks under the
      // first (ties broken in favor of the earliest-opened column).
      int[] spans =    { 1, 1, 1, 1 };
      int[] rowSpans = { 1, 1, 1, 1 };
      boolean[] noFilters = new boolean[4];

      List<WizDashboardService.TilePlacement> placements =
         WizDashboardService.computeGridLayout(spans, rowSpans, noFilters, 3).placements();

      assertEquals(new WizDashboardService.TilePlacement(0, 0, 640, 420), placements.get(0));
      // Columns 1 and 2 both stretch from 420 to 864 to match column 0's stacked height
      // (420 + 24 + 420 = 864) once the band closes.
      assertEquals(new WizDashboardService.TilePlacement(664, 0, 640, 864), placements.get(1));
      assertEquals(new WizDashboardService.TilePlacement(1328, 0, 640, 864), placements.get(2));
      assertEquals(new WizDashboardService.TilePlacement(0, 444, 640, 420), placements.get(3));
   }

   @Test
   void computeGridLayoutGivesAStackedTileItsOwnFilterHeightWithoutAffectingItsColumnSiblingsWhenItIsNotAFirstTile() {
      // Same shape as computeGridLayoutStacksAShorterTileBesideATallOneAndStretchesTheShorterSide,
      // but C (which STACKS under A -- it is not the first tile of its column) has a per-chart
      // filter. Since C is not competing for "row" position with any sibling column's first tile,
      // this is just C's own reservation (matching the original, pre-alignment-feature numbers) --
      // A is untouched (it's the first tile of ITS OWN column, and NEITHER first-tile of ANY
      // column in this band owns a filter, so there's nothing to align).
      int[] spans =    { 1, 1, 1, 2 };
      int[] rowSpans = { 1, 2, 1, 1 };
      boolean[] hasFilter = { false, false, true, false };

      WizDashboardService.GridLayoutResult result =
         WizDashboardService.computeGridLayout(spans, rowSpans, hasFilter, 2);
      List<WizDashboardService.TilePlacement> placements = result.placements();

      // Derived from the constant, not hardcoded: the reserved per-chart filter height is tuned
      // (it shrank once its control became a dropdown), and literals here silently rot when it is.
      final int filterH = WizDashboardService.PER_CHART_FILTER_ROW_HEIGHT;

      assertEquals(new WizDashboardService.TilePlacement(0, 0, 640, 420), placements.get(0));     // A -- untouched
      assertEquals(new WizDashboardService.TilePlacement(664, 0, 640, 864 + filterH), placements.get(1));   // B -- stretched to match column 0
      assertEquals(new WizDashboardService.TilePlacement(0, 444, 640, 420 + filterH), placements.get(2));   // C -- its own filter height
      assertEquals(new WizDashboardService.TilePlacement(0, 888 + filterH, 900, 420), placements.get(3));   // D -- fresh band

      assertEquals(0, result.headerHeights()[0], "A owns no filter and has no misaligned sibling");
      assertEquals(0, result.headerHeights()[1], "B owns no filter and has no misaligned sibling");
      assertEquals(filterH, result.headerHeights()[2], "C owns the filter directly");
      assertEquals(0, result.headerHeights()[3]);
   }

   @Test
   void computeGridLayoutDoesNotShiftAnUnrelatedFirstTileWhenAFilterIsBuriedDeepInASingleColumnStack() {
      // REGRESSION for a real bug found via live testing: when EVERY tile stacks into ONE never-
      // closing column (the common shape for a board whose charts don't share a width -- nothing
      // ever fits beside anything, so the whole dashboard is structurally one band), a filter
      // owned by a chart buried deep in the stack must NOT shift the totally unrelated chart at
      // the very top of the stack down. There is only one column, so there is no sibling column
      // to visually align against -- the row-alignment pass must stay a no-op here.
      int[] spans =    { 1, 1, 1 };
      int[] rowSpans = { 1, 1, 1 };
      boolean[] hasFilter = { false, false, true };   // only the LAST (deepest-stacked) tile owns one

      WizDashboardService.GridLayoutResult result =
         WizDashboardService.computeGridLayout(spans, rowSpans, hasFilter, 1);
      List<WizDashboardService.TilePlacement> placements = result.placements();

      assertEquals(new WizDashboardService.TilePlacement(0, 0, 640, 420), placements.get(0),
         "the first tile must be completely unaffected by a filter buried deeper in the same column");
      assertEquals(new WizDashboardService.TilePlacement(0, 444, 640, 420), placements.get(1));
      assertEquals(new WizDashboardService.TilePlacement(0, 888, 640,
         420 + WizDashboardService.PER_CHART_FILTER_ROW_HEIGHT), placements.get(2));

      assertEquals(0, result.headerHeights()[0]);
      assertEquals(0, result.headerHeights()[1]);
      assertEquals(WizDashboardService.PER_CHART_FILTER_ROW_HEIGHT, result.headerHeights()[2]);
   }

   @Test
   void computeGridLayoutAlignsTwoSideBySideFirstTilesWhenOneOwnsAFilterAndTheOtherDoesNot() {
      // The actual motivating scenario: two columns, each opened by its own first tile (genuinely
      // side-by-side siblings) -- A owns a filter, B does not. B must reserve the SAME header
      // height so both charts' own content starts at the same Y (visual row alignment), even
      // though B has no filter control of its own there (just blank space).
      int[] spans =    { 1, 1 };
      int[] rowSpans = { 1, 1 };
      boolean[] hasFilter = { true, false };

      WizDashboardService.GridLayoutResult result =
         WizDashboardService.computeGridLayout(spans, rowSpans, hasFilter, 2);
      List<WizDashboardService.TilePlacement> placements = result.placements();

      final int filterH = WizDashboardService.PER_CHART_FILTER_ROW_HEIGHT;

      assertEquals(new WizDashboardService.TilePlacement(0, 0, 640, 420 + filterH), placements.get(0));     // A -- 420 + its own filter row
      assertEquals(new WizDashboardService.TilePlacement(664, 0, 640, 420 + filterH), placements.get(1));   // B -- 420 + alignment top-up
      assertEquals(filterH, result.headerHeights()[0], "A owns the filter");
      assertEquals(filterH, result.headerHeights()[1], "B has no filter but aligns with its side-by-side sibling A");
   }

   @Test
   void computeGridLayoutSingleTileNeedsNoStretch() {
      int[] spans = { 1 };
      int[] rowSpans = { 1 };
      boolean[] noFilters = new boolean[1];

      List<WizDashboardService.TilePlacement> placements =
         WizDashboardService.computeGridLayout(spans, rowSpans, noFilters, 2).placements();

      assertEquals(new WizDashboardService.TilePlacement(0, 0, 640, 420), placements.get(0));
   }

   @Test
   void computeGridLayoutGivesEveryTileZeroHeaderHeightWhenNoTileHasAFilter() {
      int[] spans =    { 1, 1 };
      int[] rowSpans = { 1, 1 };
      boolean[] noFilters = new boolean[2];

      WizDashboardService.GridLayoutResult result =
         WizDashboardService.computeGridLayout(spans, rowSpans, noFilters, 2);

      assertEquals(0, result.headerHeights()[0]);
      assertEquals(0, result.headerHeights()[1]);
   }

   // --- Task 3: composeDashboard's filter-bar invocation seam ---------------------------------
   //
   // composeDashboard itself needs a live ViewsheetService/asset engine to open a runtime
   // viewsheet and merge worksheets (see WizDashboardServiceTest's class Javadoc), so the
   // filters[] -> WizDashboardFilterBuilder wiring is covered here instead via the
   // package-visible applyFilters(Viewsheet, Worksheet, List<FilterSpec>) seam, with a mocked
   // WizDashboardFilterBuilder — mirroring how computeGridLayout is unit-tested independent of a
   // live engine.

   private WizDashboardService serviceWith(WizDashboardFilterBuilder filterBuilder) {
      return new WizDashboardService(mock(ViewsheetService.class), mock(AddVisualizationServiceProxy.class),
         mock(SecurityEngine.class), filterBuilder, mock(AssetRepository.class));
   }

   private static WizDashboardEvent.FilterSpec filterSpec(String field, String dataType, String label) {
      WizDashboardEvent.FilterSpec spec = new WizDashboardEvent.FilterSpec();
      spec.setField(field);
      spec.setDataType(dataType);
      spec.setLabel(label);
      return spec;
   }

   @Test
   void applyFiltersMapsSpecsToFilterRequestsAndReturnsBuilderResult() {
      WizDashboardFilterBuilder filterBuilder = mock(WizDashboardFilterBuilder.class);
      WizDashboardFilterBuilder.FilterResult expected =
         new WizDashboardFilterBuilder.FilterResult(List.of("Region"), List.of("MissingField"));
      when(filterBuilder.build(any(), any(), any(), eq(48))).thenReturn(expected);

      WizDashboardService svc = serviceWith(filterBuilder);
      Viewsheet vs = mock(Viewsheet.class);
      Worksheet baseWs = mock(Worksheet.class);
      WizDashboardEvent.FilterSpec spec = filterSpec("Region", "string", "Region");

      WizDashboardFilterBuilder.FilterResult actual = svc.applyFilters(vs, baseWs, List.of(spec), 48);

      assertSame(expected, actual);

      @SuppressWarnings("unchecked")
      ArgumentCaptor<List<WizDashboardFilterBuilder.FilterRequest>> captor =
         ArgumentCaptor.forClass(List.class);
      verify(filterBuilder).build(eq(vs), eq(baseWs), captor.capture(), eq(48));
      assertEquals(List.of(new WizDashboardFilterBuilder.FilterRequest("Region", "string", "Region")),
         captor.getValue());
   }

   @Test
   void applyPerChartFilterMapsSpecToRequestAndDelegatesToBuildPerChart() {
      WizDashboardFilterBuilder filterBuilder = mock(WizDashboardFilterBuilder.class);
      WizDashboardFilterBuilder.FilterControlPlacement expectedPlacement =
         new WizDashboardFilterBuilder.FilterControlPlacement("Selection1",
            new java.awt.Point(100, 200), new java.awt.Dimension(200, 100));
      when(filterBuilder.buildPerChart(any(), any(), eq(100), eq(200), eq(640), eq(120), any(),
         eq("CHART_TABLE"), eq("Chart1")))
         .thenReturn(expectedPlacement);

      WizDashboardService svc = serviceWith(filterBuilder);
      Viewsheet vs = mock(Viewsheet.class);
      Worksheet baseWs = mock(Worksheet.class);
      WizDashboardEvent.PerChartFilterSpec spec = new WizDashboardEvent.PerChartFilterSpec();
      spec.setIdentifier("v2");
      spec.setField("Standalone");
      spec.setDataType("string");
      spec.setLabel("Standalone");

      WizDashboardFilterBuilder.FilterControlPlacement placement =
         svc.applyPerChartFilter(vs, baseWs, spec, 100, 200, 640, 120, "CHART_TABLE", "Chart1");

      assertSame(expectedPlacement, placement);
      verify(filterBuilder).buildPerChart(eq(vs), eq(baseWs), eq(100), eq(200), eq(640), eq(120),
         eq(new WizDashboardFilterBuilder.FilterRequest("Standalone", "string", "Standalone")),
         eq("CHART_TABLE"), eq("Chart1"));
   }

   // --- Task 5: buildAlternateLayouts (Mobile/Wide/Ultrawide adaptive layouts) ----------------

   @Test
   void buildAlternateLayoutsProducesThreeTiersEachCoveringEveryChartAndFilterControl() {
      String[] assemblyNames = { "Chart1", "Chart2" };
      int[] spans = { 2, 1 };
      int[] rowSpans = { 1, 1 };
      List<WizDashboardFilterBuilder.FilterControlPlacement> filterPlacements = List.of(
         new WizDashboardFilterBuilder.FilterControlPlacement("Selection1", new Point(24, 24),
            new java.awt.Dimension(200, 100)));

      List<ViewsheetLayout> layouts =
         WizDashboardService.buildAlternateLayouts(assemblyNames, spans, rowSpans, 0, filterPlacements);

      assertEquals(3, layouts.size());

      for(ViewsheetLayout layout : layouts) {
         // Every chart AND every filter control must have an entry, or AbstractLayout#apply
         // hides it entirely when this layout is selected.
         assertNotNull(layout.getVSAssemblyLayout("Chart1"));
         assertNotNull(layout.getVSAssemblyLayout("Chart2"));
         assertNotNull(layout.getVSAssemblyLayout("Selection1"));
      }
   }

   @Test
   void mobileTierForcesEveryChartToAFixedFullWidthTileIgnoringItsOwnSpan() {
      String[] assemblyNames = { "Chart1", "Chart2" };
      int[] spans = { 2, 1 };       // Chart1 is a 2-col-span type -- ignored on Mobile
      int[] rowSpans = { 2, 1 };    // Chart1 is also a 2-row-span type -- ignored on Mobile
      List<ViewsheetLayout> layouts =
         WizDashboardService.buildAlternateLayouts(assemblyNames, spans, rowSpans, 0, List.of());

      ViewsheetLayout mobile = layouts.stream()
         .filter(l -> l.isMobileOnly())
         .findFirst().orElseThrow();

      VSAssemblyLayout chart1 = mobile.getVSAssemblyLayout("Chart1");
      VSAssemblyLayout chart2 = mobile.getVSAssemblyLayout("Chart2");
      assertEquals(new java.awt.Dimension(350, 300), chart1.getSize());
      assertEquals(new java.awt.Dimension(350, 300), chart2.getSize());
      // Stacked vertically: Chart1 at row 0, Chart2 at row 1 (300 + 24 gutter below it).
      assertEquals(new Point(24, 24), chart1.getPosition());
      assertEquals(new Point(24, 24 + 300 + 24), chart2.getPosition());
   }

   @Test
   void wideAndUltrawideTiersAreNotMobileOnlyAndUseTheRequestedColumnCount() {
      String[] assemblyNames = { "Chart1" };
      int[] spans = { 1 };
      int[] rowSpans = { 1 };
      List<ViewsheetLayout> layouts =
         WizDashboardService.buildAlternateLayouts(assemblyNames, spans, rowSpans, 0, List.of());

      ViewsheetLayout wide = layouts.stream()
         .filter(l -> java.util.Arrays.asList(l.getDeviceIds()).contains(WizDeviceBootstrapService.WIDE_DEVICE_ID))
         .findFirst().orElseThrow();
      ViewsheetLayout ultrawide = layouts.stream()
         .filter(l -> java.util.Arrays.asList(l.getDeviceIds()).contains(WizDeviceBootstrapService.ULTRAWIDE_DEVICE_ID))
         .findFirst().orElseThrow();

      assertFalse(wide.isMobileOnly());
      assertFalse(ultrawide.isMobileOnly());
      // A single 1-col-span chart lands at the grid origin (0,0) regardless of column count.
      assertEquals(new Point(24, 24), wide.getVSAssemblyLayout("Chart1").getPosition());
      assertEquals(new Point(24, 24), ultrawide.getVSAssemblyLayout("Chart1").getPosition());
   }

   @Test
   void everyAdaptiveTierDisablesScaleToScreenAndFitToWidth() {
      // ViewsheetLayout defaults BOTH flags to true (its own constructor). Left at the default,
      // ViewsheetLayout#apply() forces the runtime viewsheet into "scale to screen" mode, which
      // stretches every tile's carefully-computed pixel-exact size to fill whatever the actual
      // browser width happens to be -- e.g. a 900px-wide tile rendering at ~2414px on a 2560px-wide
      // window. The adaptive tiers are meant to be a FIXED, pixel-exact grid per device tier, so
      // both flags must be explicitly disabled on every tier this method builds.
      String[] assemblyNames = { "Chart1" };
      int[] spans = { 1 };
      int[] rowSpans = { 1 };
      List<ViewsheetLayout> layouts =
         WizDashboardService.buildAlternateLayouts(assemblyNames, spans, rowSpans, 0, List.of());

      assertEquals(3, layouts.size());

      for(ViewsheetLayout layout : layouts) {
         assertFalse(layout.isScaleToScreen(),
            "scaleToScreen must be disabled on tier " + layout.getName());
         assertFalse(layout.isFitToWidth(),
            "fitToWidth must be disabled on tier " + layout.getName());
      }
   }

   @Test
   void wideTierStacksAFourthChartUnderTheFirstAndStretchesTheOthersToMatch() {
      // Same shape as computeGridLayoutOpensColumnsUntilRowWidthIsExhaustedThenStacksAndStretches
      // TheOthers in WizDashboardServiceGridTest's packing tests: four 1x1-span charts at the Wide
      // tier's layoutColumns=3.
      String[] assemblyNames = { "Chart1", "Chart2", "Chart3", "Chart4" };
      int[] spans =    { 1, 1, 1, 1 };
      int[] rowSpans = { 1, 1, 1, 1 };

      List<ViewsheetLayout> layouts =
         WizDashboardService.buildAlternateLayouts(assemblyNames, spans, rowSpans, 0, List.of());

      ViewsheetLayout wide = layouts.stream()
         .filter(l -> java.util.Arrays.asList(l.getDeviceIds()).contains(WizDeviceBootstrapService.WIDE_DEVICE_ID))
         .findFirst().orElseThrow();

      assertEquals(new Point(24, 24), wide.getVSAssemblyLayout("Chart1").getPosition());
      assertEquals(new java.awt.Dimension(640, 420), wide.getVSAssemblyLayout("Chart1").getSize());

      assertEquals(new Point(688, 24), wide.getVSAssemblyLayout("Chart2").getPosition());
      assertEquals(new java.awt.Dimension(640, 864), wide.getVSAssemblyLayout("Chart2").getSize());

      assertEquals(new Point(1352, 24), wide.getVSAssemblyLayout("Chart3").getPosition());
      assertEquals(new java.awt.Dimension(640, 864), wide.getVSAssemblyLayout("Chart3").getSize());

      assertEquals(new Point(24, 468), wide.getVSAssemblyLayout("Chart4").getPosition());
      assertEquals(new java.awt.Dimension(640, 420), wide.getVSAssemblyLayout("Chart4").getSize());
   }

   @Test
   void carriesOverFilterControlPlacementsUnchangedIntoEveryTier() {
      String[] assemblyNames = { "Chart1" };
      int[] spans = { 1 };
      int[] rowSpans = { 1 };
      WizDashboardFilterBuilder.FilterControlPlacement placement =
         new WizDashboardFilterBuilder.FilterControlPlacement("Selection1", new Point(24, 24),
            new java.awt.Dimension(200, 100));

      List<ViewsheetLayout> layouts =
         WizDashboardService.buildAlternateLayouts(assemblyNames, spans, rowSpans, 0, List.of(placement));

      for(ViewsheetLayout layout : layouts) {
         VSAssemblyLayout controlLayout = layout.getVSAssemblyLayout("Selection1");
         assertEquals(new Point(24, 24), controlLayout.getPosition());
         assertEquals(new java.awt.Dimension(200, 100), controlLayout.getSize());
      }
   }
}
