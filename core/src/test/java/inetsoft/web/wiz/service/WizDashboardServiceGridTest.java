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

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.*;

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
}
