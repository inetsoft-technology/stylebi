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
   void twoUnitTilesFillRowThenWrap() {
      int[] spans = { 1, 1, 1 };
      assertEquals(new Point(0, 0),       WizDashboardService.gridOrigin(spans, 2, 0));
      assertEquals(new Point(W + G, 0),   WizDashboardService.gridOrigin(spans, 2, 1));
      assertEquals(new Point(0, H + G),   WizDashboardService.gridOrigin(spans, 2, 2)); // wrapped
   }

   @Test
   void gridOriginAddsTileGutterBetweenAdjacentColumnsAndRows() {
      int[] spans = { 1, 1, 1 };
      // Same 3-tile wrap-at-2-columns layout as twoUnitTilesFillRowThenWrap, but asserting the
      // gutter is present: tile1 starts G pixels further right than a flush W would put it, and
      // the wrapped row starts G pixels further down than a flush H would put it.
      assertEquals(new Point(0, 0),       WizDashboardService.gridOrigin(spans, 2, 0));
      assertEquals(new Point(W + G, 0),   WizDashboardService.gridOrigin(spans, 2, 1));
      assertEquals(new Point(0, H + G),   WizDashboardService.gridOrigin(spans, 2, 2));
   }

   @Test
   void fullWidthTileTakesWholeRow() {
      int[] spans = { 2, 1, 1 };   // tile0 spans both columns
      assertEquals(new Point(0, 0),        WizDashboardService.gridOrigin(spans, 2, 0));
      assertEquals(new Point(0, H + G),    WizDashboardService.gridOrigin(spans, 2, 1)); // pushed to row 2
      assertEquals(new Point(W + G, H + G), WizDashboardService.gridOrigin(spans, 2, 2));
   }

   @Test
   void unitTileAfterFullWidthStartsFreshRow() {
      int[] spans = { 1, 2 };   // tile0 unit in row0 col0; tile1 full-width can't fit → row1
      assertEquals(new Point(0, 0),     WizDashboardService.gridOrigin(spans, 2, 0));
      assertEquals(new Point(0, H + G), WizDashboardService.gridOrigin(spans, 2, 1));
   }

   @Test
   void tallTileReservesItsRowHeightForShorterNeighbors() {
      int[] spans = { 2, 1, 1 };     // tile0: 2 cols wide; tile1, tile2: 1 col each
      int[] rowSpans = { 2, 1, 1 };  // tile0: 2 ROWS tall; tile1, tile2: 1 row each

      // tile0 (2x2) fills the whole row by itself (spanCols=2 == layoutColumns) -> row 0.
      assertEquals(new Point(0, 0), WizDashboardService.gridOrigin(spans, rowSpans, 2, 0));
      // tile1 and tile2 (1x1 each) wrap into what would be "row 1". tile0's reserved height is
      // its CAPPED tilePixelSize height (2x2 -> 900x600, both dimensions over the 900x600 cap),
      // not the raw 2*H=840 -- reserving the raw span height left a dead gap below a capped tile.
      // Plus TILE_GUTTER between the two rows.
      assertEquals(new Point(0, 600 + G),     WizDashboardService.gridOrigin(spans, rowSpans, 2, 1));
      assertEquals(new Point(W + G, 600 + G), WizDashboardService.gridOrigin(spans, rowSpans, 2, 2));
   }

   @Test
   void shortTileNextToTallTileSharesTheTallRowsHeightNotItsOwn() {
      int[] spans = { 1, 1 };       // tile0 and tile1 share one row (1 col each, layoutColumns=2)
      int[] rowSpans = { 2, 1 };    // tile0: 2 rows tall; tile1: 1 row tall

      // Both are in the SAME row (col 0 and col 1), so both share row index 0 -> same Y.
      assertEquals(new Point(0, 0),     WizDashboardService.gridOrigin(spans, rowSpans, 2, 0));
      assertEquals(new Point(W + G, 0), WizDashboardService.gridOrigin(spans, rowSpans, 2, 1));
   }

   @Test
   void threeRowsOfMixedHeightsCompoundCumulativeYCorrectly() {
      // Row 0: one 2x2 tile (fills both columns, 2 rows tall) -> capped tilePixelSize height 600.
      // Row 1: one 2x1 tile (fills both columns, 1 row tall) -> uncapped height 420 (== H).
      // Row 2: two 1x1 tiles. TILE_GUTTER is added between each pair of consecutive rows.
      int[] spans =    { 2, 2, 1, 1 };
      int[] rowSpans = { 2, 1, 1, 1 };

      assertEquals(new Point(0, 0),                     WizDashboardService.gridOrigin(spans, rowSpans, 2, 0));
      assertEquals(new Point(0, 600 + G),               WizDashboardService.gridOrigin(spans, rowSpans, 2, 1));
      assertEquals(new Point(0, 600 + G + H + G),       WizDashboardService.gridOrigin(spans, rowSpans, 2, 2));
      assertEquals(new Point(W + G, 600 + G + H + G),   WizDashboardService.gridOrigin(spans, rowSpans, 2, 3));
   }

   @Test
   void aTileWithAPerChartFilterReservesExtraRowHeight() {
      int[] spans = { 2, 2 };       // two full-width tiles, one per row
      int[] rowSpans = { 1, 1 };
      boolean[] hasFilter = { true, false };   // only tile0 has a per-chart filter

      // tile0's row reserves its normal height (H=420) PLUS PER_CHART_FILTER_ROW_HEIGHT (120),
      // PLUS TILE_GUTTER before row1.
      assertEquals(new Point(0, H + 120 + G),
         WizDashboardService.gridOrigin(spans, rowSpans, hasFilter, 2, 1));
   }

   @Test
   void rowHeightReservationTakesTheMaxAcrossTilesInTheRowIncludingPerChartFilterExtras() {
      int[] spans =    { 1, 1, 2 };   // tile0, tile1 share row0 (1 col each); tile2 is full-width row1
      int[] rowSpans = { 1, 1, 1 };
      boolean[] hasFilter = { false, true, false };   // only tile1 (in row0) has a filter

      // row0's height is max(tile0's H, tile1's H+120) = H+120, even though tile0 itself has none.
      // Plus TILE_GUTTER before row1.
      assertEquals(new Point(0, H + 120 + G),
         WizDashboardService.gridOrigin(spans, rowSpans, hasFilter, 2, 2));
   }

   @Test
   void gridOriginFourArgOverloadIsUnaffectedByTheNewParameter() {
      // Re-assert one of the existing mixed-height cases through the OLD 4-arg overload, proving
      // it still delegates to an implicit all-false hasPerChartFilter and its behavior is
      // byte-for-byte unchanged by this task.
      int[] spans =    { 2, 2, 1, 1 };
      int[] rowSpans = { 2, 1, 1, 1 };
      assertEquals(new Point(0, 600 + G + H + G), WizDashboardService.gridOrigin(spans, rowSpans, 2, 2));
   }

   @Test
   void twoOversizedTilesPackIntoTheSameRowWhenTheirActualCappedWidthsFit() {
      // Two 2-col-span tiles. At layoutColumns=2 each nominally-2-col tile would take a whole
      // row by itself (see fullWidthTileTakesWholeRow) -- but at layoutColumns=3, the available
      // row width (3*W + 2*G = 1968) comfortably fits both tiles' ACTUAL CAPPED widths (900 each,
      // 1824 total incl. one gutter), even though their NOMINAL spans (2+2=4) would have exceeded
      // 3 columns under the old column-unit model. This is the exact case that motivated this fix.
      int[] spans = { 2, 2 };
      int[] rowSpans = { 1, 1 };

      assertEquals(new Point(0, 0),   WizDashboardService.gridOrigin(spans, rowSpans, 3, 0));
      assertEquals(new Point(924, 0), WizDashboardService.gridOrigin(spans, rowSpans, 3, 1));
   }

   @Test
   void oversizedTilesStillWrapWhenTheyGenuinelyDoNotFit() {
      // Three 2-col-span tiles at layoutColumns=4 (available row width = 4*W + 3*G = 2632).
      // Two tiles' actual capped widths fit (900 + 24 + 900 = 1824 <= 2632), but a third would
      // need 1824 + 24 + 900 = 2748 > 2632 -- so it correctly wraps to a new row instead of being
      // crammed in, proving the fix doesn't over-pack when there truly isn't room.
      int[] spans = { 2, 2, 2 };
      int[] rowSpans = { 1, 1, 1 };

      assertEquals(new Point(0, 0),     WizDashboardService.gridOrigin(spans, rowSpans, 4, 0));
      assertEquals(new Point(924, 0),   WizDashboardService.gridOrigin(spans, rowSpans, 4, 1));
      assertEquals(new Point(0, H + G), WizDashboardService.gridOrigin(spans, rowSpans, 4, 2));
   }

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

   // --- Task 3: composeDashboard's filter-bar invocation seam ---------------------------------
   //
   // composeDashboard itself needs a live ViewsheetService/asset engine to open a runtime
   // viewsheet and merge worksheets (see WizDashboardServiceTest's class Javadoc), so the
   // filters[] -> WizDashboardFilterBuilder wiring is covered here instead via the
   // package-visible applyFilters(Viewsheet, Worksheet, List<FilterSpec>) seam, with a mocked
   // WizDashboardFilterBuilder — mirroring how gridOrigin is unit-tested independent of a live
   // engine.

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
      when(filterBuilder.build(any(), any(), any())).thenReturn(expected);

      WizDashboardService svc = serviceWith(filterBuilder);
      Viewsheet vs = mock(Viewsheet.class);
      Worksheet baseWs = mock(Worksheet.class);
      WizDashboardEvent.FilterSpec spec = filterSpec("Region", "string", "Region");

      WizDashboardFilterBuilder.FilterResult actual = svc.applyFilters(vs, baseWs, List.of(spec));

      assertSame(expected, actual);

      @SuppressWarnings("unchecked")
      ArgumentCaptor<List<WizDashboardFilterBuilder.FilterRequest>> captor =
         ArgumentCaptor.forClass(List.class);
      verify(filterBuilder).build(eq(vs), eq(baseWs), captor.capture());
      assertEquals(List.of(new WizDashboardFilterBuilder.FilterRequest("Region", "string", "Region")),
         captor.getValue());
   }

   @Test
   void applyPerChartFilterMapsSpecToRequestAndDelegatesToBuildPerChart() {
      WizDashboardFilterBuilder filterBuilder = mock(WizDashboardFilterBuilder.class);
      WizDashboardFilterBuilder.FilterControlPlacement expectedPlacement =
         new WizDashboardFilterBuilder.FilterControlPlacement("Selection1",
            new java.awt.Point(100, 200), new java.awt.Dimension(200, 100));
      when(filterBuilder.buildPerChart(any(), any(), eq(100), eq(200), any(), eq("CHART_TABLE")))
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
         svc.applyPerChartFilter(vs, baseWs, spec, 100, 200, "CHART_TABLE");

      assertSame(expectedPlacement, placement);
      verify(filterBuilder).buildPerChart(eq(vs), eq(baseWs), eq(100), eq(200),
         eq(new WizDashboardFilterBuilder.FilterRequest("Standalone", "string", "Standalone")),
         eq("CHART_TABLE"));
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
