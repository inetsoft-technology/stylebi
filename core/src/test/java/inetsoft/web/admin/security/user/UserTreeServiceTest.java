/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.web.admin.security.user;

/*
 * Regression coverage for Bug #76812. getOrganizationModel's property scan re-resolved each
 * already-found, prefix-stripped property name through SreeEnv.getProperty(name, false, true) ->
 * PropertiesEngine.useAvailableOrgProperty, which derives "current org" from
 * OrganizationManager.getCurrentOrgID() (the calling principal's own org) instead of the orgID
 * parameter actually being read -- so an admin reading an organization other than their own got
 * every property silently dropped, even though the value was persisted correctly under the
 * org-prefixed key. This is the identical defect shape already fixed for the sibling read path,
 * SecurityService.readOrganizationProperties, in Bug #76798 (see
 * SecurityServiceTest.getOrganization_callerInDifferentOrg_stillReadsBackProperties).
 */

import inetsoft.mv.MVManager;
import inetsoft.report.internal.license.LicenseManager;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.DataCycleManager;
import inetsoft.sree.portal.CustomThemesManager;
import inetsoft.sree.security.*;
import inetsoft.sree.web.dashboard.DashboardRegistryManager;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.sync.DependencyStorageService;
import inetsoft.util.IndexedStorage;
import inetsoft.web.RecycleBin;
import inetsoft.web.admin.favorites.FavoritesService;
import inetsoft.web.admin.general.LocalizationSettingsService;
import inetsoft.web.admin.security.AuthenticationProviderService;
import inetsoft.web.admin.security.IdentityService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.security.Principal;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Callable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

@Tag("core")
class UserTreeServiceTest {
   @BeforeEach
   void setUp() {
      authenticationProviderService = mock(AuthenticationProviderService.class, withSettings().lenient());
      systemAdminService = mock(SystemAdminService.class, withSettings().lenient());
      identityService = mock(IdentityService.class, withSettings().lenient());
      localizationSettingsService = mock(LocalizationSettingsService.class, withSettings().lenient());
      securityEngine = mock(SecurityEngine.class, withSettings().lenient());
      themeService = mock(IdentityThemeService.class, withSettings().lenient());
      messagingTemplate = mock(SimpMessagingTemplate.class, withSettings().lenient());
      favoritesService = mock(FavoritesService.class, withSettings().lenient());
      dataCycleManager = mock(DataCycleManager.class, withSettings().lenient());
      licenseManager = mock(LicenseManager.class, withSettings().lenient());
      mvManager = mock(MVManager.class, withSettings().lenient());
      indexedStorage = mock(IndexedStorage.class, withSettings().lenient());
      customThemesManager = mock(CustomThemesManager.class, withSettings().lenient());
      dashboardRegistryManager = mock(DashboardRegistryManager.class, withSettings().lenient());
      xRepository = mock(XRepository.class, withSettings().lenient());
      dependencyStorageService = mock(DependencyStorageService.class, withSettings().lenient());
      recycleBin = mock(RecycleBin.class, withSettings().lenient());

      securityProvider = mock(SecurityProvider.class, withSettings().lenient());
      when(securityEngine.getSecurityProvider()).thenReturn(securityProvider);

      currentProvider = mock(AuthenticationProvider.class, withSettings().lenient());
      when(authenticationProviderService.getProviderByName("provider1")).thenReturn(currentProvider);
      when(currentProvider.getGroups()).thenReturn(new IdentityID[0]);

      when(localizationSettingsService.getModel())
         .thenReturn(inetsoft.web.admin.general.model.LocalizationSettingsModel.builder().build());

      principal = mock(Principal.class, withSettings().lenient());
      when(principal.getName()).thenReturn(new IdentityID("caller", "org-a").convertToKey());

      organizationManagerStatic = mockStatic(OrganizationManager.class,
                                             withSettings().strictness(org.mockito.quality.Strictness.LENIENT));
      OrganizationManager orgManager = mock(OrganizationManager.class, withSettings().lenient());
      organizationManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);
      currentOrg = new String[]{ "org-a" };
      when(orgManager.getCurrentOrgID()).thenAnswer(inv -> currentOrg[0]);
      organizationManagerStatic.when(() -> OrganizationManager.runInOrgScope(anyString(), any()))
         .thenAnswer(inv -> {
            String scopedOrg = inv.getArgument(0);
            String previous = currentOrg[0];
            currentOrg[0] = scopedOrg;

            try {
               Callable<?> callable = inv.getArgument(1);
               return callable.call();
            }
            finally {
               currentOrg[0] = previous;
            }
         });

      when(currentProvider.getOrganization("org-a")).thenReturn(new FSOrganization("org-a"));

      sreeEnvStatic = mockStatic(SreeEnv.class, withSettings().strictness(org.mockito.quality.Strictness.LENIENT));
      sreeEnvStatic.when(SreeEnv::getProperties).thenReturn(new Properties());

      when(identityService.getPermission(any(IdentityID.class), eq(ResourceType.SECURITY_ORGANIZATION),
                                         anyString(), eq(principal)))
         .thenReturn(Collections.emptyList());
      when(identityService.getIdentityInfo(any(IdentityID.class), eq(inetsoft.uql.util.Identity.ORGANIZATION),
                                           eq(currentProvider)))
         .thenReturn(new IdentityInfo());

      service = new UserTreeService(
         authenticationProviderService, systemAdminService, identityService,
         localizationSettingsService, securityEngine, themeService, messagingTemplate,
         favoritesService, dataCycleManager, licenseManager, mvManager, indexedStorage,
         customThemesManager, dashboardRegistryManager, xRepository, dependencyStorageService,
         recycleBin);
   }

   @AfterEach
   void tearDown() {
      organizationManagerStatic.close();
      sreeEnvStatic.close();
   }

   // ── getOrganizationModel properties cross-org read scope (Bug #76812) ──────
   //
   // Same defect shape and same mocking approach as
   // SecurityServiceTest.getOrganization_callerInDifferentOrg_stillReadsBackProperties (Bug
   // #76798): OrganizationManager and SreeEnv are fully static-mocked, so the real
   // PropertiesEngine/OrganizationContextHolder internals never run. getCurrentOrgID() is backed
   // by a mutable holder that only OrganizationManager.runInOrgScope(orgId, ...) is allowed to
   // swing to orgId for the scope's duration, and the SreeEnv.getProperty stub only resolves the
   // property when the simulated "current org" matches the org actually being read -- exactly the
   // real coupling that dropped the property when the caller's own org differed from orgID.
   @Test
   void getOrganizationModel_callerInDifferentOrg_stillReadsBackProperties() throws Exception {
      String orgId = "org-b";
      IdentityID orgIdentity = new IdentityID("Org B", orgId);
      when(currentProvider.getOrganization(orgId)).thenReturn(new FSOrganization(orgId));

      Properties raw = new Properties();
      raw.setProperty("inetsoft.org." + orgId + ".custom.key", "v");
      sreeEnvStatic.when(SreeEnv::getProperties).thenReturn(raw);
      sreeEnvStatic.when(() -> SreeEnv.getProperty(eq("custom.key"), eq(false), eq(true)))
         .thenAnswer(inv -> orgId.equals(currentOrg[0]) ? "v" : null);

      EditOrganizationPaneModel result =
         service.getOrganizationModel("provider1", orgIdentity, principal, false, null);

      assertEquals(1, result.properties().size(),
         "Bug #76812: getOrganizationModel must read back properties for an org other than the "
         + "calling principal's own current org, not just the caller's own org");
      assertEquals("custom.key", result.properties().get(0).name());
      assertEquals("v", result.properties().get(0).value());
   }

   private AuthenticationProviderService authenticationProviderService;
   private SystemAdminService systemAdminService;
   private IdentityService identityService;
   private LocalizationSettingsService localizationSettingsService;
   private SecurityEngine securityEngine;
   private IdentityThemeService themeService;
   private SimpMessagingTemplate messagingTemplate;
   private FavoritesService favoritesService;
   private DataCycleManager dataCycleManager;
   private LicenseManager licenseManager;
   private MVManager mvManager;
   private IndexedStorage indexedStorage;
   private CustomThemesManager customThemesManager;
   private DashboardRegistryManager dashboardRegistryManager;
   private XRepository xRepository;
   private DependencyStorageService dependencyStorageService;
   private RecycleBin recycleBin;

   private SecurityProvider securityProvider;
   private AuthenticationProvider currentProvider;
   private Principal principal;
   private UserTreeService service;
   private String[] currentOrg;

   private MockedStatic<OrganizationManager> organizationManagerStatic;
   private MockedStatic<SreeEnv> sreeEnvStatic;
}
