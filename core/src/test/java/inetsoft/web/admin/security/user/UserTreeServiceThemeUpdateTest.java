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

import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.*;
import inetsoft.util.MessageException;
import inetsoft.web.admin.security.AuthenticationProviderService;
import inetsoft.web.admin.security.IdentityService;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.mockito.quality.Strictness;

import java.security.Principal;

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
      AuthenticationProviderService providerService =
         mock(AuthenticationProviderService.class, withSettings().lenient());
      when(providerService.getProviderByName("Primary")).thenReturn(provider);

      SecurityProvider securityProvider = mock(SecurityProvider.class, withSettings().lenient());
      when(securityProvider.getOrganization(ORG)).thenReturn(new FSOrganization(ORG));
      SecurityEngine securityEngine = mock(SecurityEngine.class, withSettings().lenient());
      when(securityEngine.getSecurityProvider()).thenReturn(securityProvider);

      SystemAdminService systemAdminService =
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
   private IdentityService identityService;
   private IdentityThemeService themeService;
   private Principal principal;
   private UserTreeService service;
   private OrganizationManager orgManager;
   private MockedStatic<SUtil> sUtilStatic;
   private MockedStatic<OrganizationManager> orgManagerStatic;
}
