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
package inetsoft.web.admin.security;

/*
 * Bug #77352: REST createGroup/updateGroup/createRole/updateRole (and so the Shell DSL
 * securityGroup/securityRole { theme }) accepted a theme but never assigned it, like
 * createUser/updateUser before Bug #77265.
 *
 * Fixture is that of SecurityServiceUserThemeTest (#77265).
 */

import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.portal.CustomTheme;
import inetsoft.sree.portal.CustomThemesManager;
import inetsoft.sree.security.*;
import inetsoft.util.audit.Audit;
import inetsoft.web.admin.general.LocalizationSettingsService;
import inetsoft.web.admin.security.action.ActionPermissionService;
import inetsoft.web.admin.security.user.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("core")
class SecurityServiceGroupRoleThemeTest {
   @BeforeEach
   void setUp() {
      authenticationProvider = mock(AuthenticationProvider.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));
      securityProvider = mock(SecurityProvider.class, withSettings().lenient());
      when(securityProvider.getAuthenticationProvider()).thenReturn(authenticationProvider);
      // a rename that omits adminIdentities re-keys the grant through the authorization
      // provider (Bug #77326)
      when(securityProvider.getAuthorizationProvider())
         .thenReturn(mock(AuthorizationChain.class, withSettings().lenient()));

      SecurityEngine securityEngine = mock(SecurityEngine.class, withSettings().lenient());
      when(securityEngine.getSecurityProvider()).thenReturn(securityProvider);
      securityEngineStatic = mockStatic(SecurityEngine.class,
                                        withSettings().strictness(org.mockito.quality.Strictness.LENIENT));
      securityEngineStatic.when(SecurityEngine::getSecurity).thenReturn(securityEngine);

      editableProvider = mock(EditableAuthenticationProvider.class,
                              withSettings().defaultAnswer(CALLS_REAL_METHODS).lenient());
      doReturn(new IdentityID[0]).when(editableProvider).getUsers();
      doReturn(new IdentityID[0]).when(editableProvider).getGroups();
      doReturn(new IdentityID[0]).when(editableProvider).getRoles();

      sUtilStatic = mockStatic(SUtil.class, withSettings().strictness(org.mockito.quality.Strictness.LENIENT));
      sUtilStatic.when(() -> SUtil.getEditableAuthenticationProvider(securityProvider))
         .thenReturn(editableProvider);
      sUtilStatic.when(SUtil::loadLocaleProperties).thenReturn(new Properties());

      OrganizationManager orgManager = mock(OrganizationManager.class, withSettings().lenient());
      organizationManagerStatic = mockStatic(OrganizationManager.class,
                                             withSettings().strictness(org.mockito.quality.Strictness.LENIENT));
      organizationManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);
      when(orgManager.getCurrentOrgID()).thenReturn("org1");
      when(orgManager.getCurrentOrgID(any())).thenReturn("org1");
      when(orgManager.isSiteAdmin(any(Principal.class))).thenReturn(false);

      Audit audit = mock(Audit.class, withSettings().lenient());
      auditStatic = mockStatic(Audit.class, withSettings().strictness(org.mockito.quality.Strictness.LENIENT));
      auditStatic.when(Audit::getInstance).thenReturn(audit);

      sreeEnvStatic = mockStatic(SreeEnv.class, withSettings().strictness(org.mockito.quality.Strictness.LENIENT));

      principal = mock(Principal.class, withSettings().lenient());
      when(principal.getName()).thenReturn(new IdentityID("caller", "org1").convertToKey());

      when(securityProvider.getOrganizationIDs())
         .thenReturn(new String[]{ "org1", "org2", Organization.getDefaultOrganizationID() });
      when(securityProvider.checkPermission(eq(principal), any(ResourceType.class), anyString(),
                                            eq(ResourceAction.ADMIN)))
         .thenReturn(true);

      identityService = mock(IdentityService.class, withSettings().lenient());
      when(identityService.getIdentityInfo(any(), anyInt(), any())).thenReturn(new IdentityInfo());

      themes = new HashSet<>();
      customThemesManager = mock(CustomThemesManager.class, withSettings().lenient());
      when(customThemesManager.getCustomThemes()).thenAnswer(inv -> themes);
      doAnswer(invocation -> {
         CustomThemesManager.ThemesUpdate<?> update = invocation.getArgument(0);
         Set<CustomTheme> result = update.apply(new HashSet<>(themes));

         if(result != null) {
            themes = result;
         }

         return null;
      }).when(customThemesManager).updateCustomThemes(any());

      service = new SecurityService(
         securityEngine, identityService,
         mock(ActionPermissionService.class), mock(LocalizationSettingsService.class),
         new IdentityThemeService(customThemesManager), mock(SystemAdminService.class),
         mock(UserTreeService.class, withSettings().lenient()), customThemesManager);
   }

   @AfterEach
   void tearDown() {
      sUtilStatic.close();
      organizationManagerStatic.close();
      auditStatic.close();
      sreeEnvStatic.close();
      securityEngineStatic.close();
   }

   @Test
   void createGroup_validThemeId_assignsGroup() throws Exception {
      themes.add(theme("t1", "org1"));

      SecurityGroup request = new SecurityGroup();
      request.setIdentityID(new IdentityID("g1", "org1"));
      request.setOrgID("org1");
      request.setTheme("t1");
      service.createGroup(request, null, principal);

      verify(editableProvider).addGroup(any(FSGroup.class));
      assertEquals(List.of("g1"), theme("t1").getGroups(),
                   "createGroup with theme t1 must add the group to t1's groups");
   }

   @Test
   void updateGroup_validThemeId_assignsGroup() throws Exception {
      themes.add(theme("t1", "org1"));
      IdentityID groupId = new IdentityID("g1", "org1");
      FSGroup oldGroup = new FSGroup(groupId);
      when(securityProvider.getGroup(groupId)).thenReturn(oldGroup);
      when(editableProvider.getGroup(groupId)).thenReturn(oldGroup);

      SecurityGroup request = new SecurityGroup();
      request.setIdentityID(groupId);
      request.setTheme("t1");
      service.updateGroup(groupId, request, principal);

      assertEquals(List.of("g1"), theme("t1").getGroups(),
                   "updateGroup with theme t1 must add the group to t1's groups");
   }

   @Test
   void createRole_validThemeId_assignsRole() throws Exception {
      themes.add(theme("t1", "org1"));

      SecurityRole request = new SecurityRole();
      request.setIdentityID(new IdentityID("r1", "org1"));
      request.setTheme("t1");
      service.createRole(request, null, principal);

      verify(editableProvider).addRole(any(FSRole.class));
      assertEquals(List.of("r1"), theme("t1").getRoles(),
                   "createRole with theme t1 must add the role to t1's roles");
   }

   @Test
   void updateRole_validThemeId_assignsRole() throws Exception {
      themes.add(theme("t1", "org1"));
      IdentityID roleId = new IdentityID("r1", "org1");
      FSRole oldRole = new FSRole(roleId);
      when(securityProvider.getRole(roleId)).thenReturn(oldRole);
      when(editableProvider.getRole(roleId)).thenReturn(oldRole);

      SecurityRole request = new SecurityRole();
      request.setIdentityID(roleId);
      request.setTheme("t1");
      service.updateRole(roleId, request, principal);

      assertEquals(List.of("r1"), theme("t1").getRoles(),
                   "updateRole with theme t1 must add the role to t1's roles");
   }

   @Test
   void updateGroup_themeOmitted_keepsAssignment() throws Exception {
      CustomTheme t1 = theme("t1", "org1");
      t1.getGroups().add("g1");
      themes.add(t1);
      IdentityID groupId = new IdentityID("g1", "org1");
      FSGroup oldGroup = new FSGroup(groupId);
      when(securityProvider.getGroup(groupId)).thenReturn(oldGroup);
      when(editableProvider.getGroup(groupId)).thenReturn(oldGroup);

      SecurityGroup request = new SecurityGroup();
      request.setIdentityID(groupId);
      service.updateGroup(groupId, request, principal);

      assertEquals(List.of("g1"), theme("t1").getGroups(), "an omitted theme is unchanged");

      request.setTheme("");
      service.updateGroup(groupId, request, principal);

      assertTrue(theme("t1").getGroups().isEmpty(), "an empty theme clears the assignment");
   }

   // Bug #77304: the reserved "default" id selects the default theme like ""
   @Test
   void updateGroupAndRole_defaultThemeId_clearsAssignment() throws Exception {
      CustomTheme t1 = theme("t1", "org1");
      t1.getGroups().add("g1");
      t1.getRoles().add("r1");
      themes.add(t1);
      IdentityID groupId = new IdentityID("g1", "org1");
      FSGroup oldGroup = new FSGroup(groupId);
      when(securityProvider.getGroup(groupId)).thenReturn(oldGroup);
      when(editableProvider.getGroup(groupId)).thenReturn(oldGroup);
      IdentityID roleId = new IdentityID("r1", "org1");
      FSRole oldRole = new FSRole(roleId);
      when(securityProvider.getRole(roleId)).thenReturn(oldRole);
      when(editableProvider.getRole(roleId)).thenReturn(oldRole);

      SecurityGroup groupRequest = new SecurityGroup();
      groupRequest.setIdentityID(groupId);
      groupRequest.setTheme(CustomTheme.DEFAULT_THEME_ID);
      service.updateGroup(groupId, groupRequest, principal);
      SecurityRole roleRequest = new SecurityRole();
      roleRequest.setIdentityID(roleId);
      roleRequest.setTheme(CustomTheme.DEFAULT_THEME_ID);
      service.updateRole(roleId, roleRequest, principal);

      assertTrue(theme("t1").getGroups().isEmpty(), "\"default\" clears the group's theme");
      assertTrue(theme("t1").getRoles().isEmpty(), "\"default\" clears the role's theme");
   }

   @Test
   void updateGroup_otherOrgThemeId_ignored() throws Exception {
      CustomTheme t1 = theme("t1", "org1");
      t1.getGroups().add("g1");
      themes.add(t1);
      themes.add(theme("t2", "org2"));
      IdentityID groupId = new IdentityID("g1", "org1");
      FSGroup oldGroup = new FSGroup(groupId);
      when(securityProvider.getGroup(groupId)).thenReturn(oldGroup);
      when(editableProvider.getGroup(groupId)).thenReturn(oldGroup);

      SecurityGroup request = new SecurityGroup();
      request.setIdentityID(groupId);
      request.setTheme("t2");
      service.updateGroup(groupId, request, principal);

      assertTrue(theme("t2").getGroups().isEmpty(), "org1 must not assign groups to org2's theme");
      assertEquals(List.of("g1"), theme("t1").getGroups(), "an ignored theme keeps the old theme");
   }

   @Test
   void updateRole_renameWithoutTheme_keepsAssignment() throws Exception {
      CustomTheme t1 = theme("t1", "org1");
      t1.getRoles().add("r1");
      themes.add(t1);
      IdentityID roleId = new IdentityID("r1", "org1");
      FSRole oldRole = new FSRole(roleId);
      when(securityProvider.getRole(roleId)).thenReturn(oldRole);
      when(editableProvider.getRole(roleId)).thenReturn(oldRole);

      SecurityRole request = new SecurityRole();
      request.setIdentityID(new IdentityID("r2", "org1"));
      service.updateRole(roleId, request, principal);

      assertEquals(List.of("r2"), theme("t1").getRoles());
   }

   // a global role is assigned only among the current organization's themes; the global theme
   // and another organization's theme keep the role (organization admin, multi-tenant)
   @Test
   void updateRole_globalRole_assignedInCurrentOrgThemesOnly() throws Exception {
      sUtilStatic.when(SUtil::isMultiTenant).thenReturn(true);
      themes.add(theme("t1", "org1"));
      CustomTheme t2 = theme("t2", "org2");
      t2.getRoles().add("Designer");
      themes.add(t2);
      CustomTheme global = theme("g", null);
      global.getRoles().add("Designer");
      themes.add(global);
      IdentityID pathId = new IdentityID("Designer", GLOBAL_ORG_KEY);
      FSRole oldRole = new FSRole(new IdentityID("Designer", null));
      when(securityProvider.getRole(pathId)).thenReturn(oldRole);
      when(editableProvider.getRole(pathId)).thenReturn(oldRole);

      SecurityRole request = new SecurityRole();
      request.setIdentityID(new IdentityID("Designer", null));
      request.setTheme("t2");
      service.updateRole(pathId, request, principal);

      assertTrue(theme("t1").getRoles().isEmpty(), "org2's theme is ignored for org1");

      request.setTheme("t1");
      service.updateRole(pathId, request, principal);

      assertEquals(List.of("Designer"), theme("t1").getRoles());
      assertEquals(List.of("Designer"), theme("t2").getRoles(), "org2's assignment must be kept");
      assertEquals(List.of("Designer"), theme("g").getRoles(),
                   "an organization admin must not change a global theme");
   }

   private CustomTheme theme(String id) {
      return themes.stream().filter(t -> id.equals(t.getId())).findFirst().orElseThrow();
   }

   private static CustomTheme theme(String id, String orgID) {
      CustomTheme theme = new CustomTheme();
      theme.setId(id);
      theme.setName(id);
      theme.setOrgID(orgID);
      theme.setJarPath("portal/" + orgID + "/theme/" + id + ".jar");
      theme.setUsers(new ArrayList<>());
      theme.setGroups(new ArrayList<>());
      theme.setRoles(new ArrayList<>());
      return theme;
   }

   private static final String GLOBAL_ORG_KEY = "__GLOBAL__";
   private Set<CustomTheme> themes;
   private SecurityProvider securityProvider;
   private AuthenticationProvider authenticationProvider;
   private EditableAuthenticationProvider editableProvider;
   private IdentityService identityService;
   private CustomThemesManager customThemesManager;
   private Principal principal;
   private SecurityService service;
   private MockedStatic<SUtil> sUtilStatic;
   private MockedStatic<OrganizationManager> organizationManagerStatic;
   private MockedStatic<Audit> auditStatic;
   private MockedStatic<SreeEnv> sreeEnvStatic;
   private MockedStatic<SecurityEngine> securityEngineStatic;
}
