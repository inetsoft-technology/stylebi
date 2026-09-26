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
 * Issue #77056: the EM user/group/role editors changed the theme assignments before the identity
 * edit was validated, so a rename rejected by setIdentity() (e.g. a duplicate name) or an edit of
 * a user that does not exist still rewrote the themes.
 */

import inetsoft.sree.internal.DataCycleManager;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.portal.CustomTheme;
import inetsoft.sree.portal.CustomThemesManager;
import inetsoft.sree.security.*;
import inetsoft.uql.XRepository;
import inetsoft.util.IndexedStorage;
import inetsoft.util.MessageException;
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
class UserTreeServiceThemeUpdateTest {
   @BeforeEach
   void setUp() throws Exception {
      sUtilStatic = mockStatic(SUtil.class, withSettings().strictness(Strictness.LENIENT));
      orgManager = mock(OrganizationManager.class, withSettings().lenient());
      when(orgManager.getCurrentOrgID()).thenReturn(ORG);
      when(orgManager.isSiteAdmin(any(Principal.class))).thenReturn(true);
      orgManagerStatic = mockStatic(OrganizationManager.class,
                                    withSettings().strictness(Strictness.LENIENT));
      orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);

      provider = mock(EditableAuthenticationProvider.class, withSettings().lenient());
      providerService =
         mock(AuthenticationProviderService.class, withSettings().lenient());
      when(providerService.getProviderByName("Primary")).thenReturn(provider);

      SecurityProvider securityProvider = mock(SecurityProvider.class, withSettings().lenient());
      when(securityProvider.getOrganization(ORG)).thenReturn(new FSOrganization(ORG));
      securityEngine = mock(SecurityEngine.class, withSettings().lenient());
      when(securityEngine.getSecurityProvider()).thenReturn(securityProvider);

      systemAdminService =
         mock(SystemAdminService.class, withSettings().lenient());
      when(systemAdminService.hasSysAdmin(any())).thenReturn(true);
      when(systemAdminService.hasOrgAdmin(any())).thenReturn(true);

      identityService = mock(IdentityService.class);
      doThrow(new MessageException("duplicate name"))
         .when(identityService).setIdentity(any(), any(), any(), any());
      themeService = mock(IdentityThemeService.class);
      principal = mock(Principal.class, withSettings().lenient());
      when(principal.getName()).thenReturn(new IdentityID("admin", ORG).convertToKey());

      service = new UserTreeService(
         providerService, systemAdminService, identityService, null, securityEngine, themeService,
         null, null, null, null, null, null, null, null, null, null, null);
   }

   @AfterEach
   void tearDown() {
      orgManagerStatic.close();
      sUtilStatic.close();
   }

   @Test
   void editGroup_renameRejected_themesNotUpdated() {
      when(provider.getGroup(new IdentityID("sales", ORG))).thenReturn(new FSGroup(
         new IdentityID("sales", ORG)));
      EditGroupPaneModel model = EditGroupPaneModel.builder()
         .name("marketing")
         .oldName("sales")
         .organization(ORG)
         .build();

      assertThrows(MessageException.class, () ->
         service.editGroup("Primary", new IdentityID("sales", ORG), model, principal));

      verifyNoInteractions(themeService);
   }

   @Test
   void editRole_renameRejected_themesNotUpdated() {
      when(provider.getRole(new IdentityID("designer", ORG))).thenReturn(new FSRole(
         new IdentityID("designer", ORG)));
      EditRolePaneModel model = EditRolePaneModel.builder()
         .name("viewer")
         .oldName("designer")
         .isSysAdmin(false)
         .isOrgAdmin(false)
         .organization(ORG)
         .build();

      assertThrows(MessageException.class, () -> service.editRole(model, "Primary", principal));

      verifyNoInteractions(themeService);
   }

   @Test
   void editUser_renameRejected_themesNotUpdated() {
      FSUser bob = new FSUser(new IdentityID("bob", ORG));
      bob.setPassword("secret");
      when(provider.getUser(new IdentityID("bob", ORG))).thenReturn(bob);

      assertThrows(MessageException.class, () ->
         service.editUser(userModel("bob", "alice"), "Primary", principal));

      verifyNoInteractions(themeService);
   }

   @Test
   void editUser_userDoesNotExist_themesNotUpdated() {
      when(provider.getUser(any())).thenReturn(null);

      assertThrows(MessageException.class, () ->
         service.editUser(userModel("ghost", "ghost"), "Primary", principal));

      verifyNoInteractions(themeService);
   }

   // a successful EM role rename renames the role only in the themes of the role's organization,
   // except for a global role (no organization), which is renamed in every organization's themes
   @Test
   void editRole_renameSucceeds_themeRenameScopedToRoleOrg() throws Exception {
      CustomTheme aTheme = theme("aTheme", ORG);
      CustomTheme bTheme = theme("bTheme", "organizationB");
      CustomTheme globalTheme = theme("globalTheme", null);
      CustomThemesManager themesManager = mock(CustomThemesManager.class);
      when(themesManager.getCustomThemes())
         .thenReturn(new HashSet<>(Set.of(aTheme, bTheme, globalTheme)));
      XRepository repository = mock(XRepository.class);
      when(repository.getDataSourceFullNames(any())).thenReturn(new String[0]);
      doNothing().when(identityService).setIdentity(any(), any(), any(), any());
      UserTreeService renameService = new UserTreeService(
         providerService, systemAdminService, identityService, null, securityEngine,
         new IdentityThemeService(themesManager), null, null, null, null, null, null, null, null,
         repository, null, null);

      when(provider.getRole(new IdentityID("designer", ORG))).thenReturn(new FSRole(
         new IdentityID("designer", ORG)));
      renameService.editRole(roleModel("designer", "viewer", ORG), "Primary", principal);

      assertEquals(List.of("viewer"), aTheme.getRoles());
      assertEquals(List.of("designer"), bTheme.getRoles(), "org B's designer must keep its theme");
      assertEquals(List.of("designer"), globalTheme.getRoles(),
         "a global theme's designer belongs to the default organization");

      when(provider.getRole(new IdentityID("designer", null))).thenReturn(new FSRole(
         new IdentityID("designer", null)));
      renameService.editRole(roleModel("designer", "lead", null), "Primary", principal);

      assertEquals(List.of("viewer"), aTheme.getRoles());
      assertEquals(List.of("lead"), bTheme.getRoles());
      assertEquals(List.of("lead"), globalTheme.getRoles());
   }

   // review finding IMPORTANT-1: the permission check is on the group in the path, so the theme
   // rename must be scoped by that group's organization. A same-named group in another
   // organization keeps its theme. (A body whose organization differs from the path is rejected
   // outright since #77078, see UserTreeServiceEditGroupOrgTest.)
   @Test
   void editGroup_renameSucceeds_themeRenameScopedToPathGroupOrg() throws Exception {
      CustomTheme aTheme = theme("aTheme", ORG);
      aTheme.getGroups().add("sales");
      CustomTheme bTheme = theme("bTheme", "organizationB");
      bTheme.getGroups().add("sales");
      CustomThemesManager themesManager = mock(CustomThemesManager.class);
      when(themesManager.getCustomThemes()).thenReturn(new HashSet<>(Set.of(aTheme, bTheme)));
      doNothing().when(identityService).setIdentity(any(), any(), any(), any());
      UserTreeService renameService = new UserTreeService(
         providerService, systemAdminService, identityService, null, securityEngine,
         new IdentityThemeService(themesManager), null, null, mock(DataCycleManager.class), null,
         null, mock(IndexedStorage.class), null, null, null, null, null);
      IdentityID pathGroup = new IdentityID("sales", ORG);
      when(provider.getGroup(pathGroup)).thenReturn(new FSGroup(pathGroup));
      EditGroupPaneModel model = EditGroupPaneModel.builder()
         .name("sales2")
         .oldName("sales")
         .organization(ORG)
         .build();

      renameService.editGroup("Primary", pathGroup, model, principal);

      assertEquals(List.of("sales2"), aTheme.getGroups());
      assertEquals(List.of("sales"), bTheme.getGroups(), "org B's sales must keep its theme");
   }

   private static EditRolePaneModel roleModel(String oldName, String name, String orgID) {
      return EditRolePaneModel.builder()
         .name(name)
         .oldName(oldName)
         .isSysAdmin(false)
         .isOrgAdmin(false)
         .organization(orgID)
         .build();
   }

   private static CustomTheme theme(String id, String orgID) {
      CustomTheme theme = new CustomTheme();
      theme.setId(id);
      theme.setName(id);
      theme.setOrgID(orgID);
      theme.getRoles().add("designer");
      return theme;
   }

   private static EditUserPaneModel userModel(String oldName, String name) {
      return EditUserPaneModel.builder()
         .name(name)
         .oldName(oldName)
         .organization(ORG)
         .theme("someTheme")
         .build();
   }

   private static final String ORG = "organizationA";
   private EditableAuthenticationProvider provider;
   private AuthenticationProviderService providerService;
   private SystemAdminService systemAdminService;
   private SecurityEngine securityEngine;
   private IdentityService identityService;
   private IdentityThemeService themeService;
   private Principal principal;
   private UserTreeService service;
   private OrganizationManager orgManager;
   private MockedStatic<SUtil> sUtilStatic;
   private MockedStatic<OrganizationManager> orgManagerStatic;
}
