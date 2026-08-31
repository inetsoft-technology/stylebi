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
package inetsoft.web.viewsheet.service;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.uql.util.XSessionService;
import inetsoft.uql.viewsheet.FileFormatInfo;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.vslayout.LayoutInfo;
import inetsoft.util.FileSystemService;
import inetsoft.web.wiz.pairing.TestPrincipals;
import inetsoft.web.wiz.pairing.WizAgentTestSupport;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.security.Principal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Regression coverage for bug PSM-004 fix A, split out from {@link VSExportServiceTest} because
 * exercising the real {@code doExportViewsheet} needs {@code CommandDispatcher.withDummyDispatcher}
 * -> {@code Cluster.getInstance()}, which is only resolvable under the Spring context
 * {@code @WizAgentTestSupport} boots (via {@code BaseTestConfiguration}'s {@code MockCluster}
 * bean) -- {@link VSExportServiceTest} stays a bare, fast unit test class that never needs it.
 */
@WizAgentTestSupport
class VSExportServiceRefreshTest {
   /**
    * Before this fix, {@code VSExportService.doExportViewsheet} set
    * {@code ViewsheetSandbox.exportRefresh} true, called {@code CoreLifecycleService
    * .refreshViewsheet}, then set it back false -- with no try/finally. A throwing
    * {@code refreshViewsheet} (the whole-viewsheet render's actual observed failure mode, PSM-004)
    * left the ThreadLocal stuck true on whatever thread ran it; {@code TableDataVSAScriptable}
    * and {@code ViewsheetSandbox} both read it afterward, so a stuck true corrupts later
    * behavior on that thread. This does not reproduce the cross-request wedge itself
    * (RenderWaitSupport runs the real caller on a fresh virtual thread per call -- see the
    * PSM-004 diagnosis), only that the ThreadLocal is no longer left dirty on whichever thread
    * the throw happens on.
    */
   @Test
   void exportRefreshIsClearedEvenWhenRefreshViewsheetThrows() throws Exception {
      CoreLifecycleService coreLifecycleService = mock(CoreLifecycleService.class);
      doThrow(new RuntimeException("boom -- simulated refreshViewsheet failure"))
         .when(coreLifecycleService).refreshViewsheet(
            any(), anyString(), isNull(), any(), eq(false), eq(true), eq(true), any());

      VSExportService service = new VSExportService(
         mock(ViewsheetService.class), coreLifecycleService, mock(ParameterService.class),
         mock(SecurityEngine.class), mock(XSessionService.class), mock(FileSystemService.class));

      Viewsheet vs = mock(Viewsheet.class);
      when(vs.getLayoutInfo()).thenReturn(mock(LayoutInfo.class));
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      when(rvs.getViewsheetSandbox()).thenReturn(Optional.of(mock(ViewsheetSandbox.class)));
      when(rvs.getID()).thenReturn("rt1");

      Principal principal = TestPrincipals.user("alice", "host-org");
      ByteArrayOutputStream baos = new ByteArrayOutputStream();

      assertThrows(RuntimeException.class, () -> service.exportViewsheet(
         rvs, FileFormatInfo.EXPORT_TYPE_PNG, true, false, true, false, false, null, false,
         new ExportResponse(baos), principal),
         "the underlying failure must still propagate -- this fix is about not leaving " +
         "exportRefresh stuck, not about swallowing the error");

      assertFalse(Boolean.TRUE.equals(ViewsheetSandbox.exportRefresh.get()),
         "exportRefresh must be cleared even when refreshViewsheet throws mid-export");
   }
}
