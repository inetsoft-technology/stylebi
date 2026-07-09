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
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.web.wiz.model.CreateVisualizationModel;
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
 * {@code WizVsService#createViewsheetInternal} (reached via the public
 * {@code createViewsheet} entry point): the check must run before any temporary viewsheet
 * is opened, and must deny with a {@link SecurityException} when the permission is refused.
 */
@Tag("core")
class WizVsServiceCreateViewsheetSecurityTest {

   @Test
   void createViewsheet_throwsSecurityExceptionAndSkipsOpenWhenPermissionDenied() throws Exception {
      ViewsheetService vsService = mock(ViewsheetService.class);
      AssetRepository engine = mock(AssetRepository.class);
      SecurityEngine securityEngine = mock(SecurityEngine.class);
      Principal principal = mock(Principal.class);

      when(securityEngine.checkPermission(
         eq(principal), eq(ResourceType.VIEWSHEET), eq("*"), eq(ResourceAction.ACCESS)))
         .thenReturn(false);

      WizVsService service = new WizVsService(vsService, engine, securityEngine);
      CreateVisualizationModel model = new CreateVisualizationModel();

      SecurityException ex = assertThrows(SecurityException.class,
         () -> service.createViewsheet(model, principal));

      assertNotNull(ex.getMessage(), "SecurityException should carry a localized denial message");

      // Nothing past the gate should have been touched: no temp viewsheet opened, no
      // sheet lookups performed.
      verifyNoInteractions(vsService);
      verifyNoInteractions(engine);
   }

   @Test
   void createViewsheet_proceedsPastPermissionCheckWhenGranted() throws Exception {
      ViewsheetService vsService = mock(ViewsheetService.class);
      AssetRepository engine = mock(AssetRepository.class);
      SecurityEngine securityEngine = mock(SecurityEngine.class);
      Principal principal = mock(Principal.class);

      when(securityEngine.checkPermission(
         eq(principal), eq(ResourceType.VIEWSHEET), eq("*"), eq(ResourceAction.ACCESS)))
         .thenReturn(true);

      // A distinctive marker exception thrown by the very next collaborator call
      // (openTemporaryViewsheet) proves execution reached past the security gate,
      // regardless of how the rest of the (unmocked) flow would behave.
      RuntimeException marker = new RuntimeException("marker-past-gate");
      when(vsService.openTemporaryViewsheet(any(), any(), eq(principal), any()))
         .thenThrow(marker);

      WizVsService service = new WizVsService(vsService, engine, securityEngine);
      CreateVisualizationModel model = new CreateVisualizationModel();

      RuntimeException thrown = assertThrows(RuntimeException.class,
         () -> service.createViewsheet(model, principal));

      assertEquals("marker-past-gate", thrown.getMessage(),
         "should fail past the gate on the marker, not be denied by SecurityException");

      verify(securityEngine).checkPermission(
         principal, ResourceType.VIEWSHEET, "*", ResourceAction.ACCESS);
      verify(vsService).openTemporaryViewsheet(any(), any(), eq(principal), any());
   }

   /**
    * The save choke point: persistViewsheet must refuse (and not call setViewsheet) when the caller
    * lacks the VIEWSHEET/ACCESS composer action right — a user without viewsheet-composer access
    * must not be able to save a mutated viewsheet even though the asset WRITE ACL might allow it.
    */
   @Test
   void persistViewsheet_throwsSecurityExceptionAndSkipsSaveWhenPermissionDenied() throws Exception {
      ViewsheetService vsService = mock(ViewsheetService.class);
      AssetRepository engine = mock(AssetRepository.class);
      SecurityEngine securityEngine = mock(SecurityEngine.class);
      Principal principal = mock(Principal.class);
      Viewsheet vs = mock(Viewsheet.class);

      when(securityEngine.checkPermission(
         eq(principal), eq(ResourceType.VIEWSHEET), eq("*"), eq(ResourceAction.ACCESS)))
         .thenReturn(false);

      WizVsService service = new WizVsService(vsService, engine, securityEngine);

      assertThrows(SecurityException.class,
         () -> service.persistViewsheet(vs, "1^128^__NULL__^visualizations/abc^host-org", principal));

      // No save must occur when the composer action right is denied.
      verifyNoInteractions(vsService);
      verifyNoInteractions(engine);
   }

   @Test
   void validateBinding_throwsSecurityExceptionAndSkipsOpenWhenPermissionDenied() throws Exception {
      ViewsheetService vsService = mock(ViewsheetService.class);
      AssetRepository engine = mock(AssetRepository.class);
      SecurityEngine securityEngine = mock(SecurityEngine.class);
      Principal principal = mock(Principal.class);

      when(securityEngine.checkPermission(
         eq(principal), eq(ResourceType.VIEWSHEET), eq("*"), eq(ResourceAction.ACCESS)))
         .thenReturn(false);

      WizVsService service = new WizVsService(vsService, engine, securityEngine);

      assertThrows(SecurityException.class,
         () -> service.validateBinding(new CreateVisualizationModel(), principal));

      verifyNoInteractions(vsService);
   }

   @Test
   void deleteViewsheet_throwsSecurityExceptionAndSkipsRemovalWhenPermissionDenied() throws Exception {
      ViewsheetService vsService = mock(ViewsheetService.class);
      AssetRepository engine = mock(AssetRepository.class);
      SecurityEngine securityEngine = mock(SecurityEngine.class);
      Principal principal = mock(Principal.class);

      when(securityEngine.checkPermission(
         eq(principal), eq(ResourceType.VIEWSHEET), eq("*"), eq(ResourceAction.ACCESS)))
         .thenReturn(false);

      WizVsService service = new WizVsService(vsService, engine, securityEngine);

      assertThrows(SecurityException.class,
         () -> service.deleteViewsheet("1^128^__NULL__^visualizations/abc^host-org", principal));

      // No asset removal when the composer action right is denied.
      verifyNoInteractions(engine);
   }
}
