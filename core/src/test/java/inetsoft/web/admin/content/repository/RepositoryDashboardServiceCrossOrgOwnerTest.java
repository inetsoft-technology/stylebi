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
package inetsoft.web.admin.content.repository;

import inetsoft.report.internal.Util;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.*;
import inetsoft.sree.web.dashboard.*;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.DependencyHandler;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.asset.sync.RenameTransformHandler;
import inetsoft.uql.util.Identity;
import inetsoft.util.MessageException;
import inetsoft.util.audit.ActionRecord;
import inetsoft.util.audit.Audit;
import inetsoft.web.admin.content.repository.model.NewRepositoryFolderRequest;
import inetsoft.web.admin.content.repository.model.RepositoryDashboardSettingsModel;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;

import java.security.Principal;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77349: the EM repository dashboard endpoints must not let an organization administrator
 * pass an owner from another organization and so read, change, create or delete that
 * organization's user dashboards.
 */
@Tag("core")
class RepositoryDashboardServiceCrossOrgOwnerTest {
   private static final IdentityID ADMIN_A = new IdentityID("adminA", "orgA");
   private static final IdentityID CAROL = new IdentityID("carol", "orgA");
   private static final IdentityID BOB = new IdentityID("bob", "orgB");
   private static final IdentityID SITE = new IdentityID("site", "host-org");
   private static final String DASH = "bobDash";

   private MockedStatic<SUtil> sutil;
   private MockedStatic<Audit> audit;
   private MockedStatic<Util> util;
   private MockedStatic<AssetUtil> assetUtil;
   private MockedStatic<OrganizationManager> orgManagerStatic;

   private DashboardRegistryManager registryManager;
   private DashboardRegistry bobRegistry;
   private DashboardRegistry carolRegistry;
   private SecurityProvider securityProvider;
   private OrganizationManager orgManager;
   private RepositoryDashboardService service;
   private XPrincipal adminA;
   private XPrincipal siteAdmin;

   @BeforeEach
   void setUp() {
      sutil = mockStatic(SUtil.class, CALLS_REAL_METHODS);
      sutil.when(() -> SUtil.getActionRecord(any(Principal.class), anyString(), any(), anyString()))
         .thenReturn(mock(ActionRecord.class));
      audit = mockStatic(Audit.class);
      audit.when(Audit::getInstance).thenReturn(mock(Audit.class));
      util = mockStatic(Util.class, CALLS_REAL_METHODS);
      util.when(() -> Util.getObjectFullPath(anyInt(), any(), any(), any())).thenReturn("x");
      assetUtil = mockStatic(AssetUtil.class, CALLS_REAL_METHODS);
      assetUtil.when(() -> AssetUtil.getAssetRepository(false))
         .thenReturn(mock(AssetRepository.class));

      orgManager = mock(OrganizationManager.class);
      when(orgManager.getCurrentOrgID()).thenReturn("orgA");
      when(orgManager.orgAdminUsers(anyString())).thenReturn(Collections.emptyList());
      orgManagerStatic = mockStatic(OrganizationManager.class);
      orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);

      adminA = principal(ADMIN_A);
      siteAdmin = principal(SITE);
      when(orgManager.isSiteAdmin(any(Principal.class))).thenReturn(false);
      when(orgManager.isSiteAdmin(siteAdmin)).thenReturn(true);

      bobRegistry = registry("bob secret desc");
      carolRegistry = registry("carol desc");
      registryManager = mock(DashboardRegistryManager.class);
      when(registryManager.getRegistry(BOB)).thenReturn(bobRegistry);
      when(registryManager.getRegistry(CAROL)).thenReturn(carolRegistry);

      // models the org-admin grant: ADMIN on any dashboard name in the caller's org
      securityProvider = mock(SecurityProvider.class);
      when(securityProvider.checkPermission(any(Principal.class), eq(ResourceType.DASHBOARD),
                                            anyString(), eq(ResourceAction.ADMIN))).thenReturn(true);
      when(securityProvider.getAuthorizationProvider())
         .thenReturn(mock(AuthorizationProvider.class));

      DashboardManager dashboardManager = mock(DashboardManager.class);
      when(dashboardManager.getDashboards(any(Identity.class))).thenReturn(new String[0]);
      SecurityEngine securityEngine = mock(SecurityEngine.class);
      when(securityEngine.isSecurityEnabled()).thenReturn(true);

      service = new RepositoryDashboardService(
         mock(ResourcePermissionService.class), securityProvider,
         mock(ContentRepositoryTreeService.class), dashboardManager, securityEngine,
         mock(DependencyHandler.class), registryManager, mock(RenameTransformHandler.class));
   }

   @AfterEach
   void tearDown() {
      orgManagerStatic.close();
      assetUtil.close();
      util.close();
      audit.close();
      sutil.close();
   }

   @Test
   void getSettings_crossOrgOwner_isRefused() {
      assertThrows(MessageException.class, () -> service.getSettings(DASH, BOB, adminA));
      assertBobRegistryUntouched();
   }

   @Test
   void setSettings_crossOrgOwner_isRefused() {
      assertThrows(MessageException.class,
                   () -> service.setSettings(DASH, model("renamed"), BOB, adminA));
      assertBobRegistryUntouched();
   }

   @Test
   void addDashboard_crossOrgOwner_isRefused() {
      assertThrows(MessageException.class, () -> service.addDashboard(request(BOB), adminA));
      assertBobRegistryUntouched();
   }

   @Test
   void delete_crossOrgOwner_isRefused() {
      assertThrows(MessageException.class, () -> service.delete(DASH, BOB, adminA));
      assertBobRegistryUntouched();
   }

   @Test
   void getSettings_sameOrgOwner_isAllowed() {
      RepositoryDashboardSettingsModel model = service.getSettings(DASH, CAROL, adminA);
      assertEquals("carol desc", model.description());
   }

   @Test
   void setSettings_sameOrgOwner_isAllowed() {
      try {
         service.setSettings(DASH, model("renamed"), CAROL, adminA);
      }
      catch(MessageException e) {
         fail("same-org owner must not be refused: " + e.getMessage());
      }
      catch(Exception ignore) {
         // collaborators downstream of the registry write are not mocked
      }

      verify(carolRegistry).renameDashboard(DASH, "renamed");
   }

   @Test
   void addDashboard_sameOrgOwner_isAllowed() throws Exception {
      service.addDashboard(request(CAROL), adminA);
      verify(carolRegistry).putDashboard(eq("Dashboard1"), any());
   }

   @Test
   void delete_sameOrgOwner_isAllowed() throws Exception {
      service.delete(DASH, CAROL, adminA);
      verify(carolRegistry).removeDashboard(DASH);
   }

   @Test
   void siteAdmin_crossOrgOwner_isAllowed() throws Exception {
      assertEquals("bob secret desc", service.getSettings(DASH, BOB, siteAdmin).description());
      service.delete(DASH, BOB, siteAdmin);
      verify(bobRegistry).removeDashboard(DASH);
   }

   @Test
   void ownDashboard_isAllowed() throws Exception {
      // the caller's own dashboard never needs the org check, even for a plain Principal
      Principal bob = mock(Principal.class);
      when(bob.getName()).thenReturn(BOB.convertToKey());

      service.delete(DASH, BOB, bob);
      verify(bobRegistry).removeDashboard(DASH);
   }

   private void assertBobRegistryUntouched() {
      verify(registryManager, never()).getRegistry(BOB);
      verifyNoInteractions(bobRegistry);
   }

   private static XPrincipal principal(IdentityID id) {
      XPrincipal principal = mock(XPrincipal.class);
      when(principal.getName()).thenReturn(id.convertToKey());
      when(principal.getOrgId()).thenReturn(id.orgID);
      when(principal.getIdentityID()).thenReturn(id);
      return principal;
   }

   private static DashboardRegistry registry(String description) {
      VSDashboard dashboard = new VSDashboard();
      dashboard.setDescription(description);
      DashboardRegistry registry = mock(DashboardRegistry.class);
      when(registry.getDashboard(DASH)).thenReturn(dashboard);
      return registry;
   }

   private static NewRepositoryFolderRequest request(IdentityID owner) {
      NewRepositoryFolderRequest request = new NewRepositoryFolderRequest();
      request.setOwner(owner);
      return request;
   }

   private static RepositoryDashboardSettingsModel model(String name) {
      return RepositoryDashboardSettingsModel.builder()
         .name(name)
         .oname(DASH)
         .viewsheet(new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET,
                                   "vs", null).toIdentifier())
         .enable(true)
         .visible(true)
         .build();
   }
}
