/*
 * This file is part of StyleBI.
 * Copyright (C) 2026 InetSoft Technology
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
package inetsoft.web.graph;

import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * A chart with Enable Drilling off must not report drill levels, so the viewer does not offer
 * Drill Down/Up Filter on it (Bug #81237).
 */
@Tag("core")
class GraphBuilderDrillEnabledTest {
   @Test
   void drillDisabledChartReportsDisabled() {
      assertFalse(GraphBuilder.isDrillEnabled(chart(false)));
   }

   @Test
   void drillEnabledChartReportsEnabled() {
      assertTrue(GraphBuilder.isDrillEnabled(chart(true)));
   }

   private ChartVSAssembly chart(boolean drill) {
      ChartVSAssemblyInfo info = mock(ChartVSAssemblyInfo.class);
      when(info.isDrillEnabled()).thenReturn(drill);
      ChartVSAssembly assembly = mock(ChartVSAssembly.class);
      when(assembly.getChartInfo()).thenReturn(info);
      return assembly;
   }
}
