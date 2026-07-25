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
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.sree.security.SecurityException;
import inetsoft.uql.asset.AssetContent;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.viewsheet.Viewsheet;
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
 * Covers the authorization gate at the top of {@code AddVisualizationService#doAddVisualization}
 * (reached via the public {@code addVisualization} entry point): unlike the other three wiz
 * security gates, this one checks {@link ResourceType#WORKSHEET}/{@link ResourceAction#ACCESS} —
 * not VIEWSHEET — because this method may create a brand-new backing {@code Worksheet()} for the
 * wiz dashboard (see the code comment on doAddVisualization). The check must run before any
 * runtime viewsheet lookup, and must deny with a {@link SecurityException} when refused.
 */
@Tag("core")
class AddVisualizationServiceSecurityTest {

   @Test
   void addVisualization_throwsSecurityExceptionAndSkipsLookupWhenPermissionDenied() throws Exception {
      ViewsheetService vsService = mock(ViewsheetService.class);
      AssetRepository assetRepository = mock(AssetRepository.class);
      WsMergeService wsMergeService = mock(WsMergeService.class);
      SecurityEngine securityEngine = mock(SecurityEngine.class);
      Principal principal = mock(Principal.class);
      AssetEntry vizEntry = mock(AssetEntry.class);

      when(securityEngine.checkPermission(
         eq(principal), eq(ResourceType.WORKSHEET), eq("*"), eq(ResourceAction.ACCESS)))
         .thenReturn(false);

      AddVisualizationService service =
         new AddVisualizationService(vsService, assetRepository, wsMergeService, securityEngine);

      SecurityException ex = assertThrows(SecurityException.class,
         () -> service.addVisualization("rt-1", vizEntry, 0, 0, 1.0f, null, principal));

      assertNotNull(ex.getMessage(), "SecurityException should carry a localized denial message");

      // Denied before any runtime viewsheet lookup or asset access.
      verifyNoInteractions(vsService);
      verifyNoInteractions(assetRepository);
      verifyNoInteractions(wsMergeService);
   }

   @Test
   void addVisualization_proceedsPastPermissionCheckWhenGranted() throws Exception {
      ViewsheetService vsService = mock(ViewsheetService.class);
      AssetRepository assetRepository = mock(AssetRepository.class);
      WsMergeService wsMergeService = mock(WsMergeService.class);
      SecurityEngine securityEngine = mock(SecurityEngine.class);
      Principal principal = mock(Principal.class);
      AssetEntry vizEntry = mock(AssetEntry.class);

      when(securityEngine.checkPermission(
         eq(principal), eq(ResourceType.WORKSHEET), eq("*"), eq(ResourceAction.ACCESS)))
         .thenReturn(true);

      // A distinctive marker exception thrown by the very next collaborator call
      // (viewsheetService.getViewsheet) proves execution reached past the security gate.
      RuntimeException marker = new RuntimeException("marker-past-gate");
      when(vsService.getViewsheet(eq("rt-1"), eq(principal))).thenThrow(marker);

      AddVisualizationService service =
         new AddVisualizationService(vsService, assetRepository, wsMergeService, securityEngine);

      RuntimeException thrown = assertThrows(RuntimeException.class,
         () -> service.addVisualization("rt-1", vizEntry, 0, 0, 1.0f, null, principal));

      assertEquals("marker-past-gate", thrown.getMessage(),
         "should fail past the gate on the marker, not be denied by SecurityException");

      verify(securityEngine).checkPermission(
         principal, ResourceType.WORKSHEET, "*", ResourceAction.ACCESS);
      verify(vsService).getViewsheet("rt-1", principal);
   }

   /**
    * IDOR regression guard: the client-supplied {@code vizEntry} must be loaded with
    * permission=true so the READ ACL actually runs. If it were loaded with permission=false
    * (the original bug), a caller could merge any viewsheet they cannot read into their own
    * dashboard and exfiltrate its data. This test drives execution to the vizEntry load and
    * asserts the permission flag is true.
    */
   @Test
   void addVisualization_loadsVizEntryWithPermissionCheckEnabled() throws Exception {
      ViewsheetService vsService = mock(ViewsheetService.class);
      AssetRepository assetRepository = mock(AssetRepository.class);
      WsMergeService wsMergeService = mock(WsMergeService.class);
      SecurityEngine securityEngine = mock(SecurityEngine.class);
      Principal principal = mock(Principal.class);
      AssetEntry vizEntry = mock(AssetEntry.class);

      when(securityEngine.checkPermission(
         eq(principal), eq(ResourceType.WORKSHEET), eq("*"), eq(ResourceAction.ACCESS)))
         .thenReturn(true);

      // A valid wiz dashboard runtime so execution reaches the vizEntry load.
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      Viewsheet dashVS = mock(Viewsheet.class);
      Viewsheet.WizInfo wizInfo = mock(Viewsheet.WizInfo.class);
      when(vsService.getViewsheet(eq("rt-1"), eq(principal))).thenReturn(rvs);
      when(rvs.getViewsheet()).thenReturn(dashVS);
      when(dashVS.getWizInfo()).thenReturn(wizInfo);
      when(wizInfo.isWizSheet()).thenReturn(true);

      // The vizEntry load returns null, so the method throws right after — but only after the
      // (permission=true) getSheet call we are asserting on has been made.
      when(assetRepository.getSheet(eq(vizEntry), eq(principal), eq(true), any(AssetContent.class)))
         .thenReturn(null);

      AddVisualizationService service =
         new AddVisualizationService(vsService, assetRepository, wsMergeService, securityEngine);

      assertThrows(Exception.class,
         () -> service.addVisualization("rt-1", vizEntry, 0, 0, 1.0f, null, principal));

      // The attacker-controlled vizEntry must be loaded with the READ permission check ENABLED.
      verify(assetRepository).getSheet(eq(vizEntry), eq(principal), eq(true), any(AssetContent.class));
   }
}
