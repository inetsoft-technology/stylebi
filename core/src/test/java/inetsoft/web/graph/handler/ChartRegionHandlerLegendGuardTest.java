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

package inetsoft.web.graph.handler;

import inetsoft.report.composition.region.ChartArea;
import inetsoft.uql.viewsheet.graph.ChartDescriptor;
import inetsoft.uql.viewsheet.graph.ChartInfo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Bug #79112: the legend dialog model dereferenced a null chart area (no laid-out graph: empty,
 * unbound or still computing) and a null legends area, which surfaced as a raw HTTP 500. It is now
 * a refusal that names the legends. The handler holds no state, so no Spring context is needed.
 */
@Tag("core")
class ChartRegionHandlerLegendGuardTest {
   private final ChartRegionHandler handler = new ChartRegionHandler();

   @Test
   void createRefusesANullChartArea() {
      IllegalArgumentException e = assertThrows(
         IllegalArgumentException.class,
         () -> handler.createLegendFormatDialogModel(
            mock(ChartInfo.class), null, mock(ChartDescriptor.class), 0));
      assertTrue(e.getMessage().contains("legends"), e.getMessage());
   }

   @Test
   void createRefusesAGraphWithoutALegendsArea() {
      // a mock's getLegendsArea() is null: a graph that has no legend group
      assertThrows(
         IllegalArgumentException.class,
         () -> handler.createLegendFormatDialogModel(
            mock(ChartInfo.class), mock(ChartArea.class), mock(ChartDescriptor.class), 0));
   }

   @Test
   void updateRefusesANullChartArea() {
      assertThrows(
         IllegalArgumentException.class,
         () -> handler.updateLegendFormatDialogModel(
            null, mock(ChartInfo.class), null, mock(ChartDescriptor.class), 0));
   }
}
