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
package inetsoft.web.binding.service;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.GaugeVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.GaugeVSAssemblyInfo;
import inetsoft.web.binding.handler.VSTreeHandler;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.security.Principal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Pins the contract BindableFieldsService depends on (bug #76592): an assembly-scoped
 * {@code getBinding} builds a tree only for chart/table assemblies and returns null for any other
 * assembly, so callers must not scope a non-data assembly through it.
 */
@Tag("core")
class VSBindingTreeServiceTest {
   private static final Principal USER = () -> "admin";

   private VSTreeHandler handler = mock(VSTreeHandler.class);
   private Viewsheet vs = mock(Viewsheet.class);

   private VSBindingTreeService service() throws Exception {
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      when(rvs.isRuntime()).thenReturn(true);
      when(rvs.getViewsheetSandbox()).thenReturn(Optional.of(mock(ViewsheetSandbox.class)));
      ViewsheetService engine = mock(ViewsheetService.class);
      when(engine.getViewsheet(eq("rt1"), any(Principal.class))).thenReturn(rvs);

      return new VSBindingTreeService(handler, engine);
   }

   @Test
   void returnsNullAndBuildsNoTreeForAGauge() throws Exception {
      GaugeVSAssembly gauge = mock(GaugeVSAssembly.class);
      when(gauge.getVSAssemblyInfo()).thenReturn(mock(GaugeVSAssemblyInfo.class));
      when(vs.getAssembly("Gauge1")).thenReturn(gauge);

      assertNull(service().getBinding("rt1", "Gauge1", false, USER));

      verify(handler, never()).getWSTreeModel(any(), any(), any(), anyBoolean(), any());
      verify(handler, never()).getChartTreeModel(any(), any(), any(), anyBoolean(), any());
      verify(handler, never()).getTableTreeModel(any(), any(), any(), any());
   }

   @Test
   void buildsTheChartTreeForAChart() throws Exception {
      ChartVSAssembly chart = mock(ChartVSAssembly.class);
      when(chart.getVSAssemblyInfo()).thenReturn(mock(ChartVSAssemblyInfo.class));
      when(vs.getAssembly("Chart1")).thenReturn(chart);

      service().getBinding("rt1", "Chart1", false, USER);

      verify(handler).getChartTreeModel(any(), any(), any(ChartVSAssemblyInfo.class),
                                        eq(false), any());
   }

   @Test
   void buildsTheWorksheetTreeWhenNoAssemblyIsNamed() throws Exception {
      service().getBinding("rt1", null, false, USER);

      verify(handler).getWSTreeModel(any(), any(), isNull(), eq(false), any());
   }
}
