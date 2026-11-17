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

import inetsoft.sree.security.*;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.FileFormatInfo;
import inetsoft.util.MessageException;
import inetsoft.web.service.BinaryTransferService;
import inetsoft.web.viewsheet.service.*;
import inetsoft.web.viewsheet.controller.AssemblyImageService;
import inetsoft.web.viewsheet.controller.AssemblyImageServiceProxy;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #81183: the Export toolbar action was enforced only on /export/viewsheet. The
 * /export/vs-table and /export/vs-chart endpoints serve the same data as a file and must
 * refuse a principal denied Export before touching the runtime viewsheet or the renderer.
 * The service proxies throw a sentinel from any call, so "passed the gate" is observable.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ExportControllerTableChartPermissionTest {
   static final String PASSED = "passed the export permission gate";

   SecurityEngine securityEngine;
   AssemblyImageServiceProxy imageProxy;
   VSLifecycleService lifecycleService;
   ExportController controller;
   Principal principal;
   HttpServletRequest request;
   HttpSession session;
   HttpServletResponse response;

   @BeforeEach
   void setUp() throws Exception {
      securityEngine = mock(SecurityEngine.class);
      imageProxy = mock(AssemblyImageServiceProxy.class, inv -> {
         throw new IllegalStateException(PASSED);
      });
      lifecycleService = mock(VSLifecycleService.class);
      controller = new ExportController(
         lifecycleService, mock(BinaryTransferService.class), imageProxy,
         mock(AssemblyImageService.class), mock(VSExportService.class),
         mock(ExportControllerServiceProxy.class), securityEngine);
      principal = mock(Principal.class);
      when(principal.getName()).thenReturn("user~;~host-org");
      request = mock(HttpServletRequest.class);
      session = mock(HttpSession.class);
      when(request.getSession()).thenReturn(session);
      response = mock(HttpServletResponse.class);
   }

   private void setExport(boolean enabled) throws Exception {
      when(securityEngine.checkPermission(any(), eq(ResourceType.VIEWSHEET_TOOLBAR_ACTION),
                                          eq("Export"), eq(ResourceAction.READ)))
         .thenReturn(enabled);
   }

   private void exportTable() throws Exception {
      controller.exportViewsheetTable("runtime-1/Table1", request, response, principal);
   }

   private void exportChart() throws Exception {
      controller.exportViewsheetChart("vs-1", "Chart1", 0, 0, false, request, response,
                                      principal, "/");
   }

   @Test
   void tableExportIsRefusedWithoutExportAndLeavesNoSessionState() throws Exception {
      setExport(false);

      assertThrows(MessageException.class, this::exportTable);

      verifyNoInteractions(imageProxy);
      verify(session, never()).setAttribute(eq("vs_table_export"), any());
   }

   @Test
   void tableExportPassesTheGateWithExport() throws Exception {
      setExport(true);

      IllegalStateException e = assertThrows(IllegalStateException.class, this::exportTable);
      assertEquals(PASSED, e.getMessage());
   }

   @Test
   void chartExportIsRefusedWithoutExportBeforeOpeningTheViewsheet() throws Exception {
      setExport(false);

      assertThrows(MessageException.class, this::exportChart);

      verifyNoInteractions(lifecycleService);
      verifyNoInteractions(imageProxy);
   }

   @Test
   void chartExportReachesTheViewsheetWithExport() throws Exception {
      setExport(true);

      // the gate is passed: the next step (resolving the viewsheet path) is attempted and
      // fails for want of a real viewsheet, not with the permission message
      Exception e = assertThrows(Exception.class, this::exportChart);
      assertFalse(e instanceof MessageException
                     && String.valueOf(e.getMessage()).contains("no permission"), e.getMessage());
   }
}
