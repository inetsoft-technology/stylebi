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
import inetsoft.report.composition.ExpiredSheetException;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.sree.security.SecurityException;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.web.wiz.model.CreateViewsheetResult;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * {@link WizVsService#fetchAssemblyData} — the whole of {@code POST /api/wiz/viewsheet/assembly-data}.
 *
 * <p>These sit at the SERVICE level on purpose. The controller tests stub this method out, so they pin
 * what the controller does with a result and nothing about how one is produced — which is how the
 * "a reaped runtime comes back empty" claim came to be documented without being true: the runtime
 * lookup throws {@link ExpiredSheetException} rather than returning null, and a test that stubs the
 * method can never say so.
 */
@Tag("core")
class WizVsServiceFetchAssemblyDataTest {
   /** A SecurityEngine that grants VIEWSHEET/ACCESS, so the action gate passes. */
   private static SecurityEngine grantedSecurity() throws Exception {
      SecurityEngine sec = mock(SecurityEngine.class);
      when(sec.checkPermission(any(), any(), anyString(), any())).thenReturn(true);
      return sec;
   }

   private static WizVsService service(ViewsheetService vsService, SecurityEngine sec) {
      return new WizVsService(vsService, mock(AssetRepository.class), sec, null, null);
   }

   @Test
   void returnsNoDataWhenTheRuntimeHasExpired() throws Exception {
      // The ORDINARY case on this path, not a failure: a question about a chart from an earlier
      // session. The chart is equally unavailable to the user at that point, so "no data to answer
      // from" is the accurate outcome — and the caller reads an empty result as exactly that.
      // Reported as an error instead, it became a 500 with an ERROR-level stack trace for routine
      // operation, which buries the failures that do matter.
      ViewsheetService vsService = mock(ViewsheetService.class);
      when(vsService.getViewsheet("rt-dead", null))
         .thenThrow(new ExpiredSheetException("rt-dead", null));

      CreateViewsheetResult result = service(vsService, grantedSecurity())
         .fetchAssemblyData("rt-dead", "Chart1", null);

      assertNotNull(result);
      assertNull(result.getRows());
   }

   @Test
   void returnsNoDataWhenTheAssemblyIsNoLongerInTheRuntime() throws Exception {
      // A deleted assembly reads the same way as an expired runtime, and must: both mean "nothing to
      // answer from" rather than "this chart is empty".
      ViewsheetService vsService = mock(ViewsheetService.class);
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      Viewsheet vs = mock(Viewsheet.class);

      when(vsService.getViewsheet("rt-1", null)).thenReturn(rvs);
      when(rvs.getViewsheet()).thenReturn(vs);
      when(vs.getAssembly("Chart1")).thenReturn(null);

      CreateViewsheetResult result = service(vsService, grantedSecurity())
         .fetchAssemblyData("rt-1", "Chart1", null);

      assertNotNull(result);
      assertNull(result.getRows());
   }

   @Test
   void requiresTheViewsheetActionRightBeforeReadingAnything() throws Exception {
      // Every other endpoint-backed method in the wiz services gates on this, including the read-only
      // one (WizAutoBindingService#getChartAestheticModel). Skipping it here would make the endpoint
      // that returns raw ROW DATA the only ungated one.
      ViewsheetService vsService = mock(ViewsheetService.class);
      SecurityEngine denied = mock(SecurityEngine.class);
      when(denied.checkPermission(any(), any(), anyString(), any())).thenReturn(false);

      assertThrows(SecurityException.class,
                   () -> service(vsService, denied).fetchAssemblyData("rt-1", "Chart1", null));

      // Denied before the runtime is even looked up.
      verifyNoInteractions(vsService);
   }

   @Test
   void rejectsBlankIdentifiers() throws Exception {
      // The controller's @NotBlank covers the HTTP path; this covers the method, which is public now
      // and which the sibling removeVisualization validates the same way.
      ViewsheetService vsService = mock(ViewsheetService.class);
      WizVsService svc = service(vsService, grantedSecurity());

      assertThrows(IllegalArgumentException.class, () -> svc.fetchAssemblyData("", "Chart1", null));
      assertThrows(IllegalArgumentException.class, () -> svc.fetchAssemblyData("rt-1", "", null));
      assertThrows(IllegalArgumentException.class, () -> svc.fetchAssemblyData(null, "Chart1", null));
      assertThrows(IllegalArgumentException.class, () -> svc.fetchAssemblyData("rt-1", null, null));
   }
}
