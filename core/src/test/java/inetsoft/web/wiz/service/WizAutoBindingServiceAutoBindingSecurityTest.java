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
import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.sree.security.SecurityException;
import inetsoft.web.wiz.model.AutoBindingRequest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Covers the VIEWSHEET/ACCESS authorization gate at the top of
 * {@code WizAutoBindingService#autoBindingInternal} — the single choke point shared by both
 * the public {@code autoBinding()} entry point and {@code changeType()}'s fallback path. The
 * check must run before any temporary recommendation viewsheet is opened, and must deny with a
 * {@link SecurityException} when the permission is refused.
 */
@Tag("core")
class WizAutoBindingServiceAutoBindingSecurityTest {

   private static WizAutoBindingService createService(ViewsheetService viewsheetService,
                                                       SecurityEngine securityEngine)
   {
      // Collaborators other than viewsheetService/securityEngine are not reached before (denied
      // case) or immediately after (granted case, via the openTemporaryViewsheet marker) the
      // permission gate. Some of them (e.g. VSWizardBindingHandler) have static initializers
      // that reach into SreeEnv/Spring and cannot even be mocked in a plain unit-test
      // environment (no Spring context) — mirroring the WizAutoBindingServiceSetChartColorsTest /
      // WizAutoBindingServiceSetChartFormatTest pattern in this package, pass null instead.
      return new WizAutoBindingService(
         viewsheetService, null, null, null, null, mock(WizVsService.class), securityEngine);
   }

   @Test
   void autoBinding_throwsSecurityExceptionAndSkipsOpenWhenPermissionDenied() throws Exception {
      ViewsheetService vsService = mock(ViewsheetService.class);
      SecurityEngine securityEngine = mock(SecurityEngine.class);
      Principal principal = mock(Principal.class);

      when(securityEngine.checkPermission(
         eq(principal), eq(ResourceType.VIEWSHEET), eq("*"), eq(ResourceAction.ACCESS)))
         .thenReturn(false);

      WizAutoBindingService service = createService(vsService, securityEngine);
      AutoBindingRequest request = new AutoBindingRequest();

      SecurityException ex = assertThrows(SecurityException.class,
         () -> service.autoBinding(request, principal));

      assertNotNull(ex.getMessage(), "SecurityException should carry a localized denial message");

      // Nothing past the gate should have been touched: no temp viewsheet opened.
      verifyNoInteractions(vsService);
   }

   @Test
   void autoBinding_proceedsPastPermissionCheckWhenGranted() throws Exception {
      ViewsheetService vsService = mock(ViewsheetService.class);
      SecurityEngine securityEngine = mock(SecurityEngine.class);
      Principal principal = mock(Principal.class);

      when(securityEngine.checkPermission(
         eq(principal), eq(ResourceType.VIEWSHEET), eq("*"), eq(ResourceAction.ACCESS)))
         .thenReturn(true);

      // A distinctive marker exception thrown by the very next collaborator call
      // (openTemporaryViewsheet, since request.getAutoBindingRuntimeId() is empty) proves
      // execution reached past the security gate.
      RuntimeException marker = new RuntimeException("marker-past-gate");
      when(vsService.openTemporaryViewsheet(any(), any(), eq(principal), any()))
         .thenThrow(marker);

      WizAutoBindingService service = createService(vsService, securityEngine);
      AutoBindingRequest request = new AutoBindingRequest();

      RuntimeException thrown = assertThrows(RuntimeException.class,
         () -> service.autoBinding(request, principal));

      assertEquals("marker-past-gate", thrown.getMessage(),
         "should fail past the gate on the marker, not be denied by SecurityException");

      verify(securityEngine).checkPermission(
         principal, ResourceType.VIEWSHEET, "*", ResourceAction.ACCESS);
      verify(vsService).openTemporaryViewsheet(any(), any(), eq(principal), any());
   }
}
