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
package inetsoft.web.composer.vs.dialog;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.DateCompareAbleAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;
import inetsoft.web.binding.handler.VSAssemblyInfoHandler;
import inetsoft.web.binding.service.graph.aesthetic.VisualFrameModelFactoryService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #76522 (DCG-003): {@code isDateComparisonEnabled()} called
 * {@code DateComparisonUtil.isDateComparisonDefined(info, false)} -- {@code checkShareFrom=false}
 * -- copy-pasted (commit 9ea28277e8) from {@code getShare()}'s own, different-purpose usage
 * ("exclude an already-sharing assembly from the share-from candidate list"). That made
 * {@code get_date_comparison} always report a {@code shareAssembly}-configured assembly as
 * disabled, regardless of whether the share is genuinely live. Every render-facing caller of the
 * same predicate uses the no-arg overload ({@code checkShareFrom=true}); this test pins
 * {@code isDateComparisonEnabled()} to that same convention, calling the real (unmocked) method
 * end to end -- only {@code ViewsheetService} and the assembly chain underneath it are mocked.
 */
@Tag("core")
class DateComparisonDialogServiceTest {
   @Test
   void reportsAShareFromAssemblyAsEnabled() throws Exception {
      VSAssemblyInfo info = mock(VSAssemblyInfo.class,
         withSettings().extraInterfaces(DateCompareAbleAssemblyInfo.class));
      when(((DateCompareAbleAssemblyInfo) info).getComparisonShareFrom()).thenReturn("Chart1");

      VSAssembly assembly = mock(VSAssembly.class);
      when(assembly.getVSAssemblyInfo()).thenReturn(info);

      Viewsheet vs = mock(Viewsheet.class);
      when(vs.getAssembly("Chart2")).thenReturn(assembly);

      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);

      ViewsheetService viewsheetService = mock(ViewsheetService.class);
      when(viewsheetService.getViewsheet(anyString(), any(Principal.class))).thenReturn(rvs);

      DateComparisonDialogService service = new DateComparisonDialogService(
         viewsheetService, mock(VSAssemblyInfoHandler.class),
         mock(VisualFrameModelFactoryService.class));

      Boolean enabled = service.isDateComparisonEnabled("rt1", "Chart2", () -> "admin");

      assertTrue(enabled,
                 "a shareAssembly-configured assembly must report enabled, matching every " +
                 "rendering-facing caller of isDateComparisonDefined's own checkShareFrom=true " +
                 "convention");
   }
}
