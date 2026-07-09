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
package inetsoft.web.wiz.controller;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.sree.security.SecurityException;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.web.wiz.service.WizVisualizationService;
import inetsoft.web.wiz.service.WizVsService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the VIEWSHEET/ACCESS authorization gate in {@code ViewsheetRuntimeController#openViewsheet}
 * and {@code #verifyViewsheet}, enforced right after the existing managed-folder path-scoping check
 * and before {@code viewsheetService.openViewsheet(...)} is called.
 */
@Tag("core")
class ViewsheetRuntimeControllerSecurityTest {

   /** A valid identifier whose path lives under the managed wiz visualization root folder,
    *  so the pre-existing path-scoping check passes and the new security gate is reached. */
   private static String managedIdentifier() {
      AssetEntry entry = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET,
         WizVisualizationService.VISUALIZATION_ROOT_FOLDER_PATH + "/abc", null);
      return entry.toIdentifier();
   }

   @Test
   void openViewsheet_throwsSecurityExceptionAndSkipsOpenWhenPermissionDenied() throws Exception {
      ViewsheetService vsService = mock(ViewsheetService.class);
      WizVsService wizVsService = mock(WizVsService.class);
      SecurityEngine securityEngine = mock(SecurityEngine.class);
      Principal principal = mock(Principal.class);

      when(securityEngine.checkPermission(
         eq(principal), eq(ResourceType.VIEWSHEET), eq("*"), eq(ResourceAction.ACCESS)))
         .thenReturn(false);

      ViewsheetRuntimeController controller =
         new ViewsheetRuntimeController(vsService, wizVsService, securityEngine);

      SecurityException ex = assertThrows(SecurityException.class,
         () -> controller.openViewsheet(managedIdentifier(), null, principal));

      assertNotNull(ex.getMessage(), "SecurityException should carry a localized denial message");

      // Denied before the downstream open call.
      verify(vsService, never())
         .openViewsheet(any(AssetEntry.class), any(), anyBoolean());
   }

   @Test
   void openViewsheet_proceedsPastPermissionCheckWhenGranted() throws Exception {
      ViewsheetService vsService = mock(ViewsheetService.class);
      WizVsService wizVsService = mock(WizVsService.class);
      SecurityEngine securityEngine = mock(SecurityEngine.class);
      Principal principal = mock(Principal.class);

      when(securityEngine.checkPermission(
         eq(principal), eq(ResourceType.VIEWSHEET), eq("*"), eq(ResourceAction.ACCESS)))
         .thenReturn(true);

      // A distinctive marker exception from the very next collaborator call
      // (viewsheetService.openViewsheet) proves execution reached past the security gate.
      RuntimeException marker = new RuntimeException("marker-past-gate");
      when(vsService.openViewsheet(any(AssetEntry.class), eq(principal), eq(true)))
         .thenThrow(marker);

      ViewsheetRuntimeController controller =
         new ViewsheetRuntimeController(vsService, wizVsService, securityEngine);

      RuntimeException thrown = assertThrows(RuntimeException.class,
         () -> controller.openViewsheet(managedIdentifier(), null, principal));

      assertEquals("marker-past-gate", thrown.getMessage(),
         "should fail past the gate on the marker, not be denied by SecurityException");

      verify(securityEngine).checkPermission(
         principal, ResourceType.VIEWSHEET, "*", ResourceAction.ACCESS);
      verify(vsService).openViewsheet(any(AssetEntry.class), eq(principal), eq(true));
   }

   @Test
   void verifyViewsheet_throwsSecurityExceptionAndSkipsOpenWhenPermissionDenied() throws Exception {
      ViewsheetService vsService = mock(ViewsheetService.class);
      WizVsService wizVsService = mock(WizVsService.class);
      SecurityEngine securityEngine = mock(SecurityEngine.class);
      Principal principal = mock(Principal.class);

      when(securityEngine.checkPermission(
         eq(principal), eq(ResourceType.VIEWSHEET), eq("*"), eq(ResourceAction.ACCESS)))
         .thenReturn(false);

      ViewsheetRuntimeController controller =
         new ViewsheetRuntimeController(vsService, wizVsService, securityEngine);

      SecurityException ex = assertThrows(SecurityException.class,
         () -> controller.verifyViewsheet(managedIdentifier(), null, principal));

      assertNotNull(ex.getMessage(), "SecurityException should carry a localized denial message");

      // Denied before the runtime is opened — no query is executed for an unauthorized caller.
      verify(vsService, never())
         .openViewsheet(any(AssetEntry.class), any(), anyBoolean());
   }

   @Test
   void verifyViewsheet_proceedsPastPermissionCheckWhenGranted() throws Exception {
      ViewsheetService vsService = mock(ViewsheetService.class);
      WizVsService wizVsService = mock(WizVsService.class);
      SecurityEngine securityEngine = mock(SecurityEngine.class);
      Principal principal = mock(Principal.class);

      when(securityEngine.checkPermission(
         eq(principal), eq(ResourceType.VIEWSHEET), eq("*"), eq(ResourceAction.ACCESS)))
         .thenReturn(true);

      // A distinctive marker exception from the very next collaborator call
      // (viewsheetService.openViewsheet) proves execution reached past the security gate.
      RuntimeException marker = new RuntimeException("marker-past-gate");
      when(vsService.openViewsheet(any(AssetEntry.class), eq(principal), eq(true)))
         .thenThrow(marker);

      ViewsheetRuntimeController controller =
         new ViewsheetRuntimeController(vsService, wizVsService, securityEngine);

      RuntimeException thrown = assertThrows(RuntimeException.class,
         () -> controller.verifyViewsheet(managedIdentifier(), null, principal));

      assertEquals("marker-past-gate", thrown.getMessage(),
         "should fail past the gate on the marker, not be denied by SecurityException");

      verify(securityEngine).checkPermission(
         principal, ResourceType.VIEWSHEET, "*", ResourceAction.ACCESS);
      verify(vsService).openViewsheet(any(AssetEntry.class), eq(principal), eq(true));
   }
}
