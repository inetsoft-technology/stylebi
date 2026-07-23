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
import inetsoft.uql.viewsheet.Viewsheet;
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

   @Test
   void twoUnitTilesFillRowThenWrap() {
      int[] spans = { 1, 1, 1 };
      assertEquals(new Point(0, 0),     WizDashboardService.gridOrigin(spans, 2, 0));
      assertEquals(new Point(W, 0),     WizDashboardService.gridOrigin(spans, 2, 1));
      assertEquals(new Point(0, H),     WizDashboardService.gridOrigin(spans, 2, 2)); // wrapped
   }

   @Test
   void fullWidthTileTakesWholeRow() {
      int[] spans = { 2, 1, 1 };   // tile0 spans both columns
      assertEquals(new Point(0, 0),  WizDashboardService.gridOrigin(spans, 2, 0));
      assertEquals(new Point(0, H),  WizDashboardService.gridOrigin(spans, 2, 1)); // pushed to row 2
      assertEquals(new Point(W, H),  WizDashboardService.gridOrigin(spans, 2, 2));
   }

   @Test
   void unitTileAfterFullWidthStartsFreshRow() {
      int[] spans = { 1, 2 };   // tile0 unit in row0 col0; tile1 full-width can't fit → row1
      assertEquals(new Point(0, 0), WizDashboardService.gridOrigin(spans, 2, 0));
      assertEquals(new Point(0, H), WizDashboardService.gridOrigin(spans, 2, 1));
   }

   // --- Task 3: composeDashboard's filter-bar invocation seam ---------------------------------
   //
   // composeDashboard itself needs a live ViewsheetService/asset engine to open a runtime
   // viewsheet and merge worksheets (see WizDashboardServiceTest's class Javadoc), so the
   // filters[] -> WizDashboardFilterBuilder wiring is covered here instead via the
   // package-visible applyFilters(Viewsheet, List<FilterSpec>) seam, with a mocked
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
      when(filterBuilder.build(any(), any())).thenReturn(expected);

      WizDashboardService svc = serviceWith(filterBuilder);
      Viewsheet vs = mock(Viewsheet.class);
      WizDashboardEvent.FilterSpec spec = filterSpec("Region", "string", "Region");

      WizDashboardFilterBuilder.FilterResult actual = svc.applyFilters(vs, List.of(spec));

      assertSame(expected, actual);

      @SuppressWarnings("unchecked")
      ArgumentCaptor<List<WizDashboardFilterBuilder.FilterRequest>> captor =
         ArgumentCaptor.forClass(List.class);
      verify(filterBuilder).build(eq(vs), captor.capture());
      assertEquals(List.of(new WizDashboardFilterBuilder.FilterRequest("Region", "string", "Region")),
         captor.getValue());
   }
}
