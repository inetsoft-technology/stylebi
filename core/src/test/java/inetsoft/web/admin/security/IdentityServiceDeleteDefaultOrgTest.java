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
package inetsoft.web.admin.security;

import inetsoft.sree.RepletRegistryManager;
import inetsoft.sree.UserEnv;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.portal.CustomThemesManager;
import inetsoft.sree.portal.CustomThemesManagerMocks;
import inetsoft.sree.schedule.ScheduleManager;
import inetsoft.sree.security.*;
import inetsoft.sree.web.SessionLicenseServiceProvider;
import inetsoft.sree.web.dashboard.DashboardManager;
import inetsoft.sree.web.dashboard.DashboardRegistryManager;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.util.Identity;
import inetsoft.util.Catalog;
import inetsoft.util.IndexedStorage;
import inetsoft.util.ThreadContext;
import inetsoft.util.audit.ActionRecord;
import inetsoft.util.audit.Audit;
import inetsoft.web.AutoSaveUtils;
import inetsoft.web.admin.favorites.FavoritesService;
import inetsoft.web.admin.security.user.IdentityThemeService;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentMatchers;
import org.mockito.MockedStatic;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #78675 (P2): the default (host) and self organizations are protected by their id, not by
 * their name. A delete request carrying the protected id under any name, in any case, is refused
 * by {@link IdentityService#deleteIdentities} before anything is removed, whichever caller
 * (the EM role controller or the security service) reached it.
 */
@Tag("core")
class IdentityServiceDeleteDefaultOrgTest {
   private IdentityService service;
   private EditableAuthenticationProvider provider;
   private CustomThemesManager themesManager;
   private FavoritesService favoritesService;
   private Principal principal;
   private MockedStatic<SUtil> sutil;
   private MockedStatic<Audit> audit;
   private MockedStatic<SecurityEngine> securityEngineStatic;
   private MockedStatic<UserEnv> userEnv;
   private MockedStatic<AutoSaveUtils> autoSave;
   private MockedStatic<OrganizationManager> orgManagerStatic;
   private Principal oldThreadPrincipal;

   @BeforeEach
   void setUp() throws Exception {
      provider = mock(EditableAuthenticationProvider.class);
      when(provider.getProviderName()).thenReturn(PROVIDER);
      when(provider.getUsers(any(IdentityID.class))).thenReturn(new IdentityID[0]);
      when(provider.getUsers()).thenReturn(new IdentityID[0]);
      when(provider.getGroups()).thenReturn(new IdentityID[0]);
      when(provider.getRoles()).thenReturn(new IdentityID[0]);

      AuthenticationChain chain = mock(AuthenticationChain.class);
      when(chain.getProviders()).thenReturn(List.of(provider));
      SecurityProvider securityProvider = mock(SecurityProvider.class);
      when(securityProvider.getAuthenticationProvider()).thenReturn(chain);

      SecurityEngine securityEngine = mock(SecurityEngine.class);
      when(securityEngine.checkPermission(any(Principal.class), any(ResourceType.class),
                                          anyString(), any(ResourceAction.class)))
         .thenReturn(true);

      themesManager = mock(CustomThemesManager.class);
      CustomThemesManagerMocks.applyUpdates(themesManager);
      favoritesService = mock(FavoritesService.class);

      service = mock(IdentityService.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));
      ReflectionTestUtils.setField(service, "securityEngine", securityEngine);
      ReflectionTestUtils.setField(service, "securityProvider", securityProvider);
      ReflectionTestUtils.setField(service, "themeService",
                                   new IdentityThemeService(themesManager));
      ReflectionTestUtils.setField(service, "cluster", mock(Cluster.class));
      ReflectionTestUtils.setField(service, "sessionLicenseServiceProvider",
                                   mock(SessionLicenseServiceProvider.class));
      ReflectionTestUtils.setField(service, "dashboardManager", mock(DashboardManager.class));
      ReflectionTestUtils.setField(service, "scheduleManager", mock(ScheduleManager.class));
      ReflectionTestUtils.setField(service, "repletRegistryManager",
                                   mock(RepletRegistryManager.class));
      ReflectionTestUtils.setField(service, "dashboardRegistryManager",
                                   mock(DashboardRegistryManager.class));
      ReflectionTestUtils.setField(service, "indexedStorage", mock(IndexedStorage.class));
      ReflectionTestUtils.setField(service, "favoritesService", favoritesService);
      // LOG is a final instance field set by the constructor, which the mock bypasses
      ReflectionTestUtils.setField(service, "LOG", LoggerFactory.getLogger(IdentityService.class));

      // the permission cleanup is covered elsewhere and is not what these tests are about
      doNothing().when(service).updateIdentityPermissions(anyInt(), any(), any(), any(), any(),
                                                          anyBoolean());
      doReturn(mock(IdentityInfo.class)).when(service).getIdentityInfo(any(), anyInt(), any());

      principal = mock(Principal.class);
      when(principal.getName()).thenReturn(new IdentityID("admin", HOST_ORG).convertToKey());

      sutil = mockStatic(SUtil.class);
      sutil.when(() -> SUtil.getActionRecord(any(Principal.class), anyString(), any(), anyString()))
         .thenAnswer(inv -> new ActionRecord());
      audit = mockStatic(Audit.class);
      audit.when(Audit::getInstance).thenReturn(mock(Audit.class));
      securityEngineStatic = mockStatic(SecurityEngine.class);
      userEnv = mockStatic(UserEnv.class);
      autoSave = mockStatic(AutoSaveUtils.class);
      OrganizationManager orgManager = mock(OrganizationManager.class);
      when(orgManager.isSiteAdmin(any(Principal.class))).thenReturn(true);
      orgManagerStatic = mockStatic(OrganizationManager.class);
      orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);

      // the group delete branch removes the group from the calling principal
      XPrincipal threadPrincipal = mock(XPrincipal.class);
      when(threadPrincipal.getGroups()).thenReturn(new String[0]);
      oldThreadPrincipal = ThreadContext.getPrincipal();
      ThreadContext.setPrincipal(threadPrincipal);
   }

   @AfterEach
   void tearDown() {
      ThreadContext.setPrincipal(oldThreadPrincipal);
      orgManagerStatic.close();
      autoSave.close();
      userEnv.close();
      securityEngineStatic.close();
      audit.close();
      sutil.close();
   }


   @Test
   void deleteProtectedOrganizationIdUnderAnyNameIsRefused() {
      Catalog catalog = Catalog.getCatalog(principal);
      String delDefOrg = catalog.getString("em.security.delDefOrg");
      String delSelfOrg = catalog.getString("em.security.delSelfOrg");

      assertEquals(List.of(delDefOrg), delete("x", Organization.getDefaultOrganizationID()));
      assertEquals(List.of(delDefOrg),
                   delete("HOST ORGANIZATION", Organization.getDefaultOrganizationID()));
      assertEquals(List.of(delDefOrg),
                   delete("x", Organization.getDefaultOrganizationID().toUpperCase()));
      assertEquals(List.of(delSelfOrg), delete("x", Organization.getSelfOrganizationID()));
      assertEquals(List.of(delSelfOrg),
                   delete("x", Organization.getSelfOrganizationID().toLowerCase()));

      verify(provider, never()).removeOrganization(any());
   }

   @Test
   void deleteOrganizationNamedLikeTheHostOrgButWithAnotherIdIsNotRefused() {
      List<String> warnings = delete(Organization.getDefaultOrganizationName(), "org-a");

      Catalog catalog = Catalog.getCatalog(principal);
      assertFalse(warnings.contains(catalog.getString("em.security.delDefOrg")), warnings::toString);
      assertFalse(warnings.contains(catalog.getString("em.security.delSelfOrg")), warnings::toString);
   }

   @Test
   void deleteOrganizationWithoutIdIsNotRefusedAsProtected() {
      List<String> warnings = delete("x", null);

      Catalog catalog = Catalog.getCatalog(principal);
      assertFalse(warnings.contains(catalog.getString("em.security.delDefOrg")), warnings::toString);
      assertFalse(warnings.contains(catalog.getString("em.security.delSelfOrg")), warnings::toString);
   }

   private List<String> delete(String name, String orgID) {
      IdentityModel model = IdentityModel.builder()
         .identityID(new IdentityID(name, orgID)).type(Identity.ORGANIZATION).build();
      return service.deleteIdentities(new IdentityModel[] { model }, PROVIDER, principal);
   }

   private static final String PROVIDER = "Primary";
   private static final String HOST_ORG = "host-org";
}
