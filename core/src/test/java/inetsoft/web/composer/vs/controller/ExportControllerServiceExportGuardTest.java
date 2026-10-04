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
package inetsoft.web.composer.vs.controller;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.ChangedAssemblyList;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.uql.util.XSessionService;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.ViewsheetInfo;
import inetsoft.uql.viewsheet.FileFormatInfo;
import inetsoft.util.FileSystemService;
import inetsoft.util.MessageException;
import inetsoft.web.service.BinaryTransferService;
import inetsoft.web.viewsheet.service.*;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;

import java.io.OutputStream;
import java.security.Principal;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77227: concurrent exports (or a print) of one runtime viewsheet each ran
 * refreshViewsheet(reset=true) on the shared sandbox at the same time. The export must be
 * claimed atomically before the refresh, a concurrent export rejected without refreshing, and
 * the claim released even when the export fails, by the owner only.
 */
@Tag("core")
class ExportControllerServiceExportGuardTest {
   ViewsheetService viewsheetService;
   CoreLifecycleService coreLifecycleService;
   ExportControllerService service;
   RuntimeViewsheet rvs;
   MockedStatic<CommandDispatcher> dispatcherStatic;
   ExecutorService pool;

   @BeforeEach
   void setUp() throws Exception {
      viewsheetService = mock(ViewsheetService.class);
      coreLifecycleService = mock(CoreLifecycleService.class);
      service = new ExportControllerService(viewsheetService, coreLifecycleService,
                                            mock(BinaryTransferService.class));

      Viewsheet vs = mock(Viewsheet.class);
      when(vs.getViewsheetInfo()).thenReturn(mock(ViewsheetInfo.class));
      rvs = spy(new RuntimeViewsheet());
      doReturn(vs).when(rvs).getViewsheet();
      doReturn(Optional.empty()).when(rvs).getViewsheetSandbox();
      doReturn("vs-1").when(rvs).getID();
      when(viewsheetService.getViewsheet(eq("vs-1"), nullable(Principal.class))).thenReturn(rvs);

      // withDummyDispatcher needs a cluster; run the task inline with a mock dispatcher
      dispatcherStatic = mockStatic(CommandDispatcher.class);
      dispatcherStatic.when(() -> CommandDispatcher.withDummyDispatcher(
            nullable(Principal.class), any()))
         .thenAnswer(inv -> inv.<CommandDispatcher.DummyDispatcherTask<?>>getArgument(1)
            .apply(mock(CommandDispatcher.class)));

      pool = Executors.newSingleThreadExecutor(r -> {
         Thread t = new Thread(r);
         t.setDaemon(true);
         return t;
      });
   }

   @AfterEach
   void tearDown() {
      dispatcherStatic.close();
      pool.shutdownNow();
      ViewsheetSandbox.exportRefresh.remove();
   }

   @Test
   void concurrentExportIsRejectedBeforeRefreshAndClaimIsReleasedAfterFailure()
      throws Exception
   {
      AtomicInteger refreshes = new AtomicInteger();
      Throwable[] second = new Throwable[1];
      Object[] flagWhileRejected = new Object[1];
      int[] refreshesWhenRejected = new int[1];
      boolean[] exportRefreshInRefresh = new boolean[1];

      doAnswer(inv -> {
         if(refreshes.incrementAndGet() == 1) {
            exportRefreshInRefresh[0] = Boolean.TRUE.equals(ViewsheetSandbox.exportRefresh.get());

            // a second export of the same runtime viewsheet arrives during the refresh
            second[0] = pool.submit(() -> {
               try {
                  export();
                  return null;
               }
               catch(Throwable t) {
                  return t;
               }
            }).get(10, TimeUnit.SECONDS);
            flagWhileRejected[0] = rvs.getProperty("__EXPORTING__");
            refreshesWhenRejected[0] = refreshes.get();
         }

         throw new IllegalStateException("refresh failed");
      }).when(coreLifecycleService).refreshViewsheet(
         any(RuntimeViewsheet.class), nullable(String.class), nullable(String.class),
         any(CommandDispatcher.class), anyBoolean(), anyBoolean(), anyBoolean(),
         nullable(ChangedAssemblyList.class));

      IllegalStateException first = assertThrows(IllegalStateException.class, this::export);
      assertEquals("refresh failed", first.getMessage());
      assertTrue(exportRefreshInRefresh[0]);

      assertInstanceOf(MessageException.class, second[0],
                       "the concurrent export was not rejected");
      // Bug #77597: typed, so the wiz API can report it as retryable
      assertInstanceOf(ExportInProgressException.class, second[0]);
      assertEquals(1, refreshesWhenRejected[0], "the rejected export refreshed");
      assertEquals("true", flagWhileRejected[0],
                   "the rejected export cleared the running export's flag");

      // the failed owner released the claim and reset the thread local on its thread
      assertNull(rvs.getProperty("__EXPORTING__"));
      assertFalse(Boolean.TRUE.equals(ViewsheetSandbox.exportRefresh.get()),
                  "exportRefresh leaked to the pooled thread after a failed refresh");
      verify(viewsheetService, never()).closeViewsheet(anyString(), nullable(Principal.class));

      // the next export is accepted (it refreshes again, and fails the same way)
      assertThrows(IllegalStateException.class, this::export);
      assertEquals(2, refreshes.get());
      assertNull(rvs.getProperty("__EXPORTING__"));
   }

   @Test
   void runtimeExportOfVSExportServiceIsRejectedWhileAnExportRuns() throws Exception {
      SecurityEngine securityEngine = mock(SecurityEngine.class);
      VSExportService exportService = new VSExportService(
         viewsheetService, coreLifecycleService, mock(ParameterService.class), securityEngine,
         mock(XSessionService.class), mock(FileSystemService.class));

      assertTrue(rvs.beginExport(), "the viewer export claims the runtime viewsheet");

      try {
         assertThrows(ExportInProgressException.class, () -> exportService.exportViewsheet(
            rvs, FileFormatInfo.EXPORT_TYPE_PDF, false, false, true, false, false,
            new String[0], false, new ExportResponse((OutputStream) null), null));
         assertThrows(ExportInProgressException.class, this::export);

         // rejected before doing anything, and the owner's flag is untouched
         verifyNoInteractions(securityEngine);
         verify(coreLifecycleService, never()).refreshViewsheet(
            any(RuntimeViewsheet.class), nullable(String.class), nullable(String.class),
            any(CommandDispatcher.class), anyBoolean(), anyBoolean(), anyBoolean(),
            nullable(ChangedAssemblyList.class));
         assertEquals("true", rvs.getProperty("__EXPORTING__"));
         assertFalse(rvs.beginExport());
      }
      finally {
         rvs.endExport();
      }

      assertNull(rvs.getProperty("__EXPORTING__"));
      assertTrue(rvs.beginExport(), "the claim was not released");
      rvs.endExport();
   }

   private void export() throws Exception {
      service.exportViewsheet("vs-1", FileFormatInfo.EXPORT_TYPE_EXCEL, null, false, false,
                              false, true, false, false, new String[0], false, false, null,
                              null);
   }
}
