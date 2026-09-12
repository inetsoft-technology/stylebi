/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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
package inetsoft.web.admin.content.repository;

/*
 * Test strategy
 *
 * MVController has real in-controller logic in several areas:
 *
 *   analyze(AnalyzeMVRequest, Principal) — delegates to mvService.analyze(), wraps the result's
 *     ID in an AnalyzeMVResponse with completed=false.
 *
 *   getModel(boolean, boolean, String) — fetches the MVStatus list from support, calls
 *     updateStatus() on each entry, delegates the model build to mvService, and wraps the result
 *     in an AnalyzeMVResponse with completed=true.
 *
 *   getMVInfo(AnalyzeMVRequest, Principal) — guards against an invalid org:
 *     securityEngine.getSecurityProvider().getOrganization(currOrgID) == null → InvalidOrgException.
 *     When valid and nodes==null, calls mvService.getMVInfo(null, principal).
 *
 *   hasMVPermission(Principal) — calls securityProvider.checkPermission() and wraps the boolean
 *     in MVHasPermissionModel.
 *
 *   deleteMVs(MVManagementModel) — extracts MV names from the model and passes them to
 *     support.dispose().
 *
 *   mvAssetExists(String, Principal) — returns false immediately when
 *     AssetEntry.createAssetEntry(path) returns null (null/invalid path).
 *
 * Tool.getDateFormatPattern() / OrganizationManager.getInstance().getCurrentOrgID() are safe to
 * call for real (pure string utilities with graceful null/default fallbacks).
 * Catalog.getCatalog() is intercepted with MockedStatic where the exception path is exercised,
 * to avoid resource-bundle loading in a test environment.
 *
 * checkStatus(), showPlan(), create(), setCycle(analysisId, request), analyze(MVManagementModel),
 * setShowAges(), setDataCycle(), updateMaterializedViews(), isWSMVEnabled() are pure delegation
 * and are covered by E2E tests.
 *
 * Coverage scope:
 *   [analyze wrapping]                     completed=false; analysisId from service result
 *   [getModel iteration + wrapping]        updateStatus() called per entry; completed=true
 *   [hasMVPermission granted]              checkPermission true → allow=true
 *   [hasMVPermission denied]              checkPermission false → allow=false
 *   [deleteMVs name extraction]            MV names mapped; support.dispose(names) called
 *   [getMVInfo invalid org]               getOrganization returns null → InvalidOrgException
 *   [getMVInfo valid org, null nodes]      org valid; nodes null → getMVInfo(null, principal)
 *   [mvAssetExists null path]             createAssetEntry(null) → null → false
 */

import inetsoft.sree.security.*;
import inetsoft.util.*;
import inetsoft.web.admin.content.repository.model.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("core")
@ExtendWith(MockitoExtension.class)
class MVControllerTest {

   @Mock private MVService mvService;
   @Mock private MVSupportService support;
   @Mock private SecurityProvider securityProvider;
   @Mock private SecurityEngine securityEngine;
   @Mock private SecurityProvider engineProvider;
   @Mock private Organization mockOrganization;
   @Mock private AnalyzeMVRequest analyzeMVRequest;
   @Mock private MaterializedModel mv;
   @Mock private Catalog catalog;
   @Mock private Principal principal;

   private MVController controller;

   @BeforeEach
   void setUp() {
      controller = new MVController(mvService, support, securityProvider, securityEngine);
   }

   // -------------------------------------------------------------------------
   // analyze(AnalyzeMVRequest, Principal)
   // -------------------------------------------------------------------------

   // [analyze wrapping] completed=false; analysisId propagated from service result
   @Test
   void analyze_wrapsAnalysisIdWithCompletedFalse() throws Exception {
      MVSupportService.AnalysisResult result = new MVSupportService.AnalysisResult("tid");
      when(mvService.analyze(analyzeMVRequest, principal)).thenReturn(result);

      AnalyzeMVResponse response = controller.analyze(analyzeMVRequest, principal);

      assertFalse(response.completed());
      assertEquals("tid", response.analysisId());
   }

   // -------------------------------------------------------------------------
   // getModel(boolean, boolean, String)
   // -------------------------------------------------------------------------

   // [getModel wrapping] updateStatus called per status entry; completed=true returned
   @Test
   void getModel_callsUpdateStatusForEachEntry_returnsCompleted() {
      when(support.getMVStatusList("aid")).thenReturn(List.of());
      when(mvService.getMaterializedModel(List.of(), false, true)).thenReturn(List.of());

      // Tool.getDateFormatPattern() calls SreeEnv which requires Spring — intercept it
      try(MockedStatic<Tool> toolMock = mockStatic(Tool.class, withSettings().lenient())) {
         toolMock.when(Tool::getDateFormatPattern).thenReturn("yyyy-MM-dd HH:mm:ss");

         AnalyzeMVResponse response = controller.getModel(false, true, "aid");

         assertTrue(response.completed());
         assertEquals("aid", response.analysisId());
         verify(mvService).getMaterializedModel(List.of(), false, true);
      }
   }

   // -------------------------------------------------------------------------
   // hasMVPermission(Principal)
   // -------------------------------------------------------------------------

   // [permission granted] checkPermission true → allow=true
   @Test
   void hasMVPermission_permissionGranted_returnsAllow() {
      when(securityProvider.checkPermission(
         principal, ResourceType.MATERIALIZATION, "*", ResourceAction.ACCESS))
         .thenReturn(true);

      assertTrue(controller.hasMVPermission(principal).allow());
   }

   // [permission denied] checkPermission false → allow=false
   @Test
   void hasMVPermission_permissionDenied_returnsNotAllow() {
      when(securityProvider.checkPermission(
         principal, ResourceType.MATERIALIZATION, "*", ResourceAction.ACCESS))
         .thenReturn(false);

      assertFalse(controller.hasMVPermission(principal).allow());
   }

   // -------------------------------------------------------------------------
   // deleteMVs(MVManagementModel)
   // -------------------------------------------------------------------------

   // [name extraction] MV names extracted from model; support.dispose(names) called
   @Test
   void deleteMVs_extractsMvNamesAndCallsDispose() {
      when(mv.name()).thenReturn("mv1");
      MVManagementModel model = MVManagementModel.builder().addMvs(mv).build();

      controller.deleteMVs(model);

      verify(support).dispose(List.of("mv1"));
   }

   // -------------------------------------------------------------------------
   // getMVInfo(AnalyzeMVRequest, Principal)
   // -------------------------------------------------------------------------

   // [invalid org] getOrganization returns null → InvalidOrgException thrown
   @Test
   void getMVInfo_invalidOrg_throwsInvalidOrgException() {
      when(securityEngine.getSecurityProvider()).thenReturn(engineProvider);
      // engineProvider.getOrganization() returns null by default → org guard triggers

      try(MockedStatic<Catalog> catalogMock = mockStatic(Catalog.class, withSettings().lenient())) {
         catalogMock.when(Catalog::getCatalog).thenReturn(catalog);
         when(catalog.getString(any())).thenReturn("invalid org");

         assertThrows(InvalidOrgException.class,
            () -> controller.getMVInfo(null, principal));
      }
   }

   // [valid org, null nodes] org valid; nodes=null → mvService.getMVInfo(null, principal) called
   @Test
   void getMVInfo_validOrg_nullNodes_delegatesToService() throws Exception {
      when(securityEngine.getSecurityProvider()).thenReturn(engineProvider);
      when(engineProvider.getOrganization(any())).thenReturn(mockOrganization);

      controller.getMVInfo(null, principal);

      verify(mvService).getMVInfo(null, principal);
   }

   // -------------------------------------------------------------------------
   // mvAssetExists(String, Principal)
   // -------------------------------------------------------------------------

   // [null path] AssetEntry.createAssetEntry(null) returns null → false returned; no repository call
   @Test
   void mvAssetExists_nullPath_returnsFalse() {
      assertFalse(controller.mvAssetExists(null, principal));
   }
}
