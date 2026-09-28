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
package inetsoft.web.admin.security.user;

/*
 * Bug #77065: AuthenticationProvider.getGroups() returns the groups of every organization (the
 * File provider keeps one global group store, and a multi-tenant DB provider reads every org's
 * rows). UserTreeService put that list unfiltered into EntityModel.identityNames in five places
 * (getUserModel, getOrganizationModel, createUser, createGroup, createOrganization), so an org
 * admin received every other tenant's group names and org ids. In multi-tenant mode each site
 * must now return only the target org's groups (plus org-less groups); single-tenant keeps all.
 */

import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.portal.CustomThemesManager;
import inetsoft.sree.security.*;
import inetsoft.util.audit.ActionRecord;
import inetsoft.util.audit.Audit;
import inetsoft.web.admin.general.LocalizationSettingsService;
import inetsoft.web.admin.general.model.LocalizationSettingsModel;
import inetsoft.web.admin.security.AuthenticationProviderService;
import inetsoft.web.admin.security.IdentityService;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.mockito.quality.Strictness;

import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("core")
class UserTreeServiceIdentityNamesOrgScopeTest {
   @BeforeEach
   void setUp() {
      sUtilStatic = mockStatic(SUtil.class, withSettings().strictness(Strictness.LENIENT));
      sUtilStatic.when(() -> SUtil.getActionRecord(any(Principal.class), anyString(), any(), anyString()))
         .thenReturn(mock(ActionRecord.class));
      sUtilStatic.when(SUtil::isMultiTenant).thenReturn(true);
      auditStatic = mockStatic(Audit.class, withSettings().strictness(Strictness.LENIENT));
      auditStatic.when(Audit::getInstance).thenReturn(mock(Audit.class));
      sreeEnvStatic = mockStatic(SreeEnv.class, withSettings().strictness(Strictness.LENIENT));
      sreeEnvStatic.when(SreeEnv::getProperties).thenReturn(new Properties());

      orgManager = mock(OrganizationManager.class, withSettings().lenient());
      when(orgManager.getCurrentOrgID()).thenReturn(ORG_A);
      when(orgManager.isSiteAdmin(any(Principal.class))).thenReturn(false);
      orgManagerStatic = mockStatic(OrganizationManager.class,
                                    withSettings().strictness(Strictness.LENIENT));
      orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);

      provider = mock(EditableAuthenticationProvider.class, withSettings().lenient());
      when(provider.getGroups()).thenReturn(new IdentityID[] { G_A, G_B, G_HOST, G_NULL });
      when(provider.getRoles()).thenReturn(new IdentityID[0]);
      when(provider.getOrganization(ORG_A)).thenAnswer(inv -> org(ORG_A, "Org A"));
      when(provider.getOrganization(ORG_B)).thenAnswer(inv -> org(ORG_B, "Org B"));
      AuthenticationProviderService providerService =
         mock(AuthenticationProviderService.class, withSettings().lenient());
      when(providerService.getProviderByName("Primary")).thenReturn(provider);

      SecurityProvider securityProvider = mock(SecurityProvider.class, withSettings().lenient());
      when(securityProvider.getOrganization(anyString()))
         .thenAnswer(inv -> org(inv.getArgument(0), inv.getArgument(0)));
      when(securityProvider.getOrganizationIDs()).thenReturn(new String[] { HOST, ORG_A, ORG_B });
      when(securityProvider.getOrganizationNames())
         .thenReturn(new String[] { "Host Organization", "Org A", "Org B" });
      when(securityProvider.checkPermission(any(Principal.class), any(ResourceType.class),
                                            anyString(), any(ResourceAction.class))).thenReturn(true);
      SecurityEngine securityEngine = mock(SecurityEngine.class, withSettings().lenient());
      when(securityEngine.getSecurityProvider()).thenReturn(securityProvider);

      IdentityService identityService = mock(IdentityService.class, withSettings().lenient());
      when(identityService.getPermission(any(IdentityID.class), any(ResourceType.class),
                                         any(), any())).thenReturn(new ArrayList<>());
      IdentityInfo info = mock(IdentityInfo.class, withSettings().lenient());
      when(info.getMembers()).thenReturn(new ArrayList<>());
      when(info.getRoles()).thenReturn(new IdentityID[0]);
      when(identityService.getIdentityInfo(any(), anyInt(), any())).thenReturn(info);

      LocalizationSettingsService localizationService =
         mock(LocalizationSettingsService.class, withSettings().lenient());
      when(localizationService.getModel()).thenReturn(mock(LocalizationSettingsModel.class));

      CustomThemesManager themesManager = mock(CustomThemesManager.class, withSettings().lenient());
      when(themesManager.getCustomThemes()).thenReturn(new HashSet<>());

      principal = mock(Principal.class, withSettings().lenient());
      when(principal.getName()).thenReturn(new IdentityID("adminA", ORG_A).convertToKey());

      service = new UserTreeService(
         providerService, null, identityService, localizationService, securityEngine,
         mock(IdentityThemeService.class), null, null, null, null, null, null, themesManager,
         null, null, null, null);
   }

   @AfterEach
   void tearDown() {
      sUtilStatic.close();
      auditStatic.close();
      sreeEnvStatic.close();
      orgManagerStatic.close();
   }

   // --- helper: the axis multi-tenant on/off x target org ---

   @Test
   void getOrgGroups_multiTenant_keepsOnlyTargetOrgAndOrglessGroups() {
      assertEquals(List.of(G_A, G_NULL), UserTreeService.getOrgGroups(provider, ORG_A));
      assertEquals(List.of(G_B, G_NULL), UserTreeService.getOrgGroups(provider, ORG_B));
      assertEquals(List.of(G_NULL), UserTreeService.getOrgGroups(provider, "neworg"));
   }

   @Test
   void getOrgGroups_singleTenant_keepsAllGroups() {
      sUtilStatic.when(SUtil::isMultiTenant).thenReturn(false);
      assertEquals(List.of(G_A, G_B, G_HOST, G_NULL), UserTreeService.getOrgGroups(provider, HOST));
   }

   // --- every identityNames population ---

   @Test
   void getUserModel_orgAdmin_returnsOwnOrgGroupsOnly() {
      when(provider.getUser(USER_A)).thenReturn(user(USER_A));
      EditUserPaneModel model = service.getUserModel("Primary", USER_A, principal);
      assertEquals(List.of(G_A, G_NULL), model.identityNames());
   }

   @Test
   void getUserModel_siteAdminViewingOtherOrgUser_returnsTargetUsersOrgGroups() {
      // a site admin can open another org's user without switching org: scope by the user's org
      when(orgManager.isSiteAdmin(any(Principal.class))).thenReturn(true);
      when(principal.getName()).thenReturn(new IdentityID("admin", HOST).convertToKey());
      when(provider.getUser(USER_B)).thenReturn(user(USER_B));
      EditUserPaneModel model = service.getUserModel("Primary", USER_B, principal);
      assertEquals(List.of(G_B, G_NULL), model.identityNames());
   }

   @Test
   void getUserModel_singleTenant_returnsAllGroups() {
      sUtilStatic.when(SUtil::isMultiTenant).thenReturn(false);
      when(provider.getUser(USER_A)).thenReturn(user(USER_A));
      EditUserPaneModel model = service.getUserModel("Primary", USER_A, principal);
      assertEquals(List.of(G_A, G_B, G_HOST, G_NULL), model.identityNames());
   }

   @Test
   void getOrganizationModel_returnsViewedOrgGroupsOnly() {
      EditOrganizationPaneModel model = service.getOrganizationModel(
         "Primary", new IdentityID("Org A", ORG_A), principal, false, null);
      assertEquals(List.of(G_A, G_NULL), model.identityNames());

      // a site admin viewing another org gets that org's groups, not the current org's
      when(orgManager.isSiteAdmin(any(Principal.class))).thenReturn(true);
      model = service.getOrganizationModel(
         "Primary", new IdentityID("Org B", ORG_B), principal, false, null);
      assertEquals(List.of(G_B, G_NULL), model.identityNames());
   }

   @Test
   void createUser_returnsCurrentOrgGroupsOnly() {
      EditUserPaneModel model = service.createUser("Primary", null, principal);
      assertNotNull(model);
      assertEquals(List.of(G_A, G_NULL), model.identityNames());
   }

   @Test
   void createGroup_returnsCurrentOrgGroupsOnly() {
      EditGroupPaneModel model = service.createGroup("Primary", null, principal);
      assertNotNull(model);
      assertEquals(List.of(G_A, G_NULL), model.identityNames());
   }

   @Test
   void createOrganization_returnsNewOrgGroupsOnly() {
      IdentityID newGroup = new IdentityID("gNew", "neworg");
      when(provider.getGroups()).thenReturn(new IdentityID[] { G_A, G_B, G_HOST, G_NULL, newGroup });
      when(provider.getOrganization("neworg")).thenReturn(null);
      when(provider.getOrgIdFromName("New Org")).thenReturn(null);

      EditOrganizationPaneModel model =
         service.createOrganization(null, "Primary", "New Org", "neworg", principal, null);
      assertNotNull(model);
      assertEquals(List.of(G_NULL, newGroup), model.identityNames());
   }

   private static FSOrganization org(String id, String name) {
      FSOrganization org = new FSOrganization(id);
      org.setName(name);
      return org;
   }

   private static FSUser user(IdentityID id) {
      FSUser user = new FSUser(id);
      user.setOrganization(id.orgID);
      return user;
   }

   private static final String HOST = "host-org";
   private static final String ORG_A = "orga";
   private static final String ORG_B = "orgb";
   private static final IdentityID G_A = new IdentityID("gA", ORG_A);
   private static final IdentityID G_B = new IdentityID("gB", ORG_B);
   private static final IdentityID G_HOST = new IdentityID("gHost", HOST);
   private static final IdentityID G_NULL = new IdentityID("gLegacy", null);
   private static final IdentityID USER_A = new IdentityID("userA", ORG_A);
   private static final IdentityID USER_B = new IdentityID("userB", ORG_B);

   private EditableAuthenticationProvider provider;
   private OrganizationManager orgManager;
   private Principal principal;
   private UserTreeService service;
   private MockedStatic<SUtil> sUtilStatic;
   private MockedStatic<Audit> auditStatic;
   private MockedStatic<SreeEnv> sreeEnvStatic;
   private MockedStatic<OrganizationManager> orgManagerStatic;
}
