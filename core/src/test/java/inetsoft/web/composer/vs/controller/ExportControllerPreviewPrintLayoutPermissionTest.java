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
 * Bug #79412: previewPrintLayout=true skipped the Export toolbar-action check for every
 * format. The flag may only exempt a PDF export; the gate decision is made on the effective
 * format (after the outtype override). Drives the real ExportController; the export service
 * proxy throws a sentinel from the first call after the gate, so "passed the gate" is observable
 * without a viewsheet.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ExportControllerPreviewPrintLayoutPermissionTest {
   static final String PASSED = "passed the export permission gate";

   SecurityEngine securityEngine;
   VSExportService exportService;
   ExportController controller;
   Principal principal;

   @BeforeEach
   void setUp() throws Exception {
      securityEngine = mock(SecurityEngine.class);
      exportService = mock(VSExportService.class);
      // a plain runtime id is not an asset path, so the controller goes straight to the proxy
      ExportControllerServiceProxy proxy = mock(ExportControllerServiceProxy.class, inv -> {
         throw new IllegalStateException(PASSED);
      });
      controller = new ExportController(
         mock(VSLifecycleService.class), mock(BinaryTransferService.class),
         mock(AssemblyImageServiceProxy.class), mock(AssemblyImageService.class),
         exportService, proxy, securityEngine);
      principal = mock(Principal.class);
      when(principal.getName()).thenReturn("user~;~host-org");
   }

   private void setExport(boolean enabled) throws Exception {
      when(securityEngine.checkPermission(any(), eq(ResourceType.VIEWSHEET_TOOLBAR_ACTION),
                                          eq("Export"), eq(ResourceAction.READ)))
         .thenReturn(enabled);
   }

   /** @return true if the request got past the gate, false if refused for Export */
   private boolean passesGate(Integer format, String outtype, boolean previewPrintLayout)
      throws Exception
   {
      HttpServletRequest request = mock(HttpServletRequest.class);
      HttpSession session = mock(HttpSession.class);
      when(session.getId()).thenReturn("sess");
      when(request.getSession()).thenReturn(session);
      when(request.getParameterMap()).thenReturn(Map.of());
      // a referer makes the controller rethrow instead of forwarding to the error page
      when(request.getHeader("referer")).thenReturn("http://localhost/");
      HttpServletResponse response = mock(HttpServletResponse.class);
      Optional<Boolean> none = Optional.empty();

      try {
         controller.exportViewsheet(
            "vs-1", "host-org", Optional.ofNullable(format), none, none, none,
            Optional.of(previewPrintLayout), none, Optional.empty(),
            Optional.ofNullable(outtype), none, Optional.empty(), Optional.empty(), none, none,
            Optional.empty(), none, response, request, principal);
         return true;
      }
      catch(MessageException e) {
         return false;
      }
      catch(IllegalStateException e) {
         assertEquals(PASSED, e.getMessage());
         return true;
      }
   }

   @Test
   void deniedWithFlagAndNonPdfOuttypeIsRefused() throws Exception {
      setExport(false);

      for(String type : new String[] { "xlsx", "xls", "pptx", "csv", "html", "png", "vso" }) {
         assertFalse(passesGate(null, type, true), "outtype=" + type);
      }
   }

   @Test
   void deniedWithFlagAndNonPdfFormatWithoutOuttypeIsRefused() throws Exception {
      setExport(false);

      for(int format : new int[] { FileFormatInfo.EXPORT_TYPE_EXCEL,
                                   FileFormatInfo.EXPORT_TYPE_POWERPOINT,
                                   FileFormatInfo.EXPORT_TYPE_SNAPSHOT,
                                   FileFormatInfo.EXPORT_TYPE_PNG,
                                   FileFormatInfo.EXPORT_TYPE_HTML,
                                   FileFormatInfo.EXPORT_TYPE_CSV }) {
         assertFalse(passesGate(format, null, true), "format=" + format);
      }
   }

   @Test
   void outtypeOverridesFormatForTheGate() throws Exception {
      setExport(false);
      // format says PDF, outtype (which wins) says xlsx
      assertFalse(passesGate(FileFormatInfo.EXPORT_TYPE_PDF, "xlsx", true));
      // format says Excel, outtype (which wins) says pdf: effective format is PDF
      assertTrue(passesGate(FileFormatInfo.EXPORT_TYPE_EXCEL, "pdf", true));
   }

   @Test
   void deniedWithFlagAndPdfPrintPreviewIsAllowed() throws Exception {
      setExport(false);
      assertTrue(passesGate(null, null, true));
      assertTrue(passesGate(FileFormatInfo.EXPORT_TYPE_PDF, null, true));
      assertTrue(passesGate(null, "pdf", true));
   }

   @Test
   void deniedWithoutFlagIsRefusedForEveryFormat() throws Exception {
      setExport(false);
      assertFalse(passesGate(null, null, false));
      assertFalse(passesGate(null, "pdf", false));
      assertFalse(passesGate(null, "xlsx", false));
      assertFalse(passesGate(FileFormatInfo.EXPORT_TYPE_EXCEL, null, false));
   }

   @Test
   void allowedPassesForEveryFormatWithOrWithoutFlag() throws Exception {
      setExport(true);

      for(boolean flag : new boolean[] { false, true }) {
         assertTrue(passesGate(null, null, flag));
         assertTrue(passesGate(null, "xlsx", flag));
         assertTrue(passesGate(null, "vso", flag));
         assertTrue(passesGate(FileFormatInfo.EXPORT_TYPE_CSV, null, flag));
      }
   }
}
