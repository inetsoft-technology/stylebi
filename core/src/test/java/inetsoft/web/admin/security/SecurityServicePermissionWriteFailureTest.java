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

/*
 * Bug #77798: the permission writers throw when the permission storage write fails. When the
 * identity is already created or renamed, the REST create and update of a user must still run
 * the steps after the permission write (theme, identity audit, rename migrations), then report
 * the failure.
 *
 * These tests run against the inetsoft-core jar, so the failing write is a mocked
 * authorization provider whose setPermission throws, as the fixed FileAuthorizationProvider
 * does.
 */

import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.portal.CustomTheme;
import inetsoft.sree.portal.CustomThemesManager;
import inetsoft.sree.security.*;
import inetsoft.util.MessageException;
import inetsoft.util.audit.Audit;
import inetsoft.web.admin.general.LocalizationSettingsService;
import inetsoft.web.admin.security.action.ActionPermissionService;
import inetsoft.web.admin.security.user.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.quality.Strictness;

import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("core")
class SecurityServicePermissionWriteFailureTest {
   @BeforeEach
   void setUp() {
      AuthenticationProvider authenticationProvider =
         mock(AuthenticationProvider.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));
      securityProvider = mock(SecurityProvider.class, withSettings().lenient());
      when(securityProvider.getAuthenticationProvider()).thenReturn(authenticationProvider);
      authz = mock(AuthorizationChain.class, withSettings().lenient());
      when(securityProvider.getAuthorizationProvider()).thenReturn(authz);
      failure = new MessageException("The permission may not have been saved");
      doThrow(failure).when(authz)
         .setPermission(any(ResourceType.class), any(IdentityID.class), any(Permission.class));
      doThrow(failure).when(authz)
         .setPermission(any(ResourceType.class), any(IdentityID.class), any(Permission.class), any());

      SecurityEngine securityEngine = mock(SecurityEngine.class, withSettings().lenient());
      when(securityEngine.getSecurityProvider()).thenReturn(securityProvider);
      securityEngineStatic = mockStatic(SecurityEngine.class, withSettings().strictness(Strictness.LENIENT));
      securityEngineStatic.when(SecurityEngine::getSecurity).thenReturn(securityEngine);

      editableProvider = mock(EditableAuthenticationProvider.class,
                              withSettings().defaultAnswer(CALLS_REAL_METHODS).lenient());

      sUtilStatic = mockStatic(SUtil.class, withSettings().strictness(Strictness.LENIENT));
      sUtilStatic.when(() -> SUtil.getEditableAuthenticationProvider(securityProvider))
         .thenReturn(editableProvider);
      sUtilStatic.when(SUtil::loadLocaleProperties).thenReturn(new Properties());

      OrganizationManager orgManager = mock(OrganizationManager.class, withSettings().lenient());
      organizationManagerStatic = mockStatic(OrganizationManager.class, withSettings().strictness(Strictness.LENIENT));
      organizationManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);
      when(orgManager.getCurrentOrgID()).thenReturn("org1");
      when(orgManager.isSiteAdmin(any(Principal.class))).thenReturn(false);

      audit = mock(Audit.class, withSettings().lenient());
      auditStatic = mockStatic(Audit.class, withSettings().strictness(Strictness.LENIENT));
      auditStatic.when(Audit::getInstance).thenReturn(audit);

      sreeEnvStatic = mockStatic(SreeEnv.class, withSettings().strictness(Strictness.LENIENT));

      principal = mock(Principal.class, withSettings().lenient());
      when(principal.getName()).thenReturn(new IdentityID("caller", "org1").convertToKey());

      when(securityProvider.getOrganizationIDs())
         .thenReturn(new String[]{ "org1", Organization.getDefaultOrganizationID() });
      when(securityProvider.checkPermission(eq(principal), any(ResourceType.class), anyString(),
                                            eq(ResourceAction.ADMIN)))
         .thenReturn(true);
      when(editableProvider.getRoles()).thenReturn(new IdentityID[0]);

      themes = new HashSet<>();
      CustomThemesManager customThemesManager = mock(CustomThemesManager.class, withSettings().lenient());
      when(customThemesManager.getCustomThemes()).thenAnswer(inv -> themes);
      doAnswer(invocation -> {
         CustomThemesManager.ThemesUpdate<?> update = invocation.getArgument(0);
         Set<CustomTheme> result = update.apply(new HashSet<>(themes));

         if(result != null) {
            themes = result;
         }

         return null;
      }).when(customThemesManager).updateCustomThemes(any());

      userTreeService = mock(UserTreeService.class, withSettings().lenient());
      service = new SecurityService(
         securityEngine, mock(IdentityService.class, withSettings().lenient()),
         mock(ActionPermissionService.class), mock(LocalizationSettingsService.class),
         new IdentityThemeService(customThemesManager), mock(SystemAdminService.class),
         userTreeService, customThemesManager);
   }

   @AfterEach
   void tearDown() {
      sUtilStatic.close();
      organizationManagerStatic.close();
      auditStatic.close();
      sreeEnvStatic.close();
      securityEngineStatic.close();
   }

   // a rename that omits adminIdentities moves the grant keyed by the user; when that write
   // fails the rename migrations still run, and the failure is reported after them
   @Test
   void updateUserRename_grantMoveFails_migrationRunsThenFailureReported() throws Exception {
      IdentityID oldId = new IdentityID("u1", "org1");
      IdentityID newId = new IdentityID("u2", "org1");
      FSUser oldUser = new FSUser(oldId);
      when(securityProvider.getUser(oldId)).thenReturn(oldUser);
      when(editableProvider.getUser(oldId)).thenReturn(oldUser);
      when(authz.getPermission(ResourceType.SECURITY_USER, oldId)).thenReturn(new Permission());

      SecurityUser request = new SecurityUser();
      request.setIdentityID(newId);

      MessageException thrown = assertThrows(MessageException.class,
         () -> service.updateUser(oldId, request, principal));

      assertSame(failure, thrown);
      verify(userTreeService).migrateUserRename(oldId, newId);
   }

   @Test
   void updateUserRename_migrationAlsoFails_attachedToPermissionFailure() throws Exception {
      IdentityID oldId = new IdentityID("u1", "org1");
      IdentityID newId = new IdentityID("u2", "org1");
      FSUser oldUser = new FSUser(oldId);
      when(securityProvider.getUser(oldId)).thenReturn(oldUser);
      when(editableProvider.getUser(oldId)).thenReturn(oldUser);
      when(authz.getPermission(ResourceType.SECURITY_USER, oldId)).thenReturn(new Permission());
      Exception migrationFailure = new Exception("migration failed");
      doThrow(migrationFailure).when(userTreeService).migrateUserRename(oldId, newId);

      SecurityUser request = new SecurityUser();
      request.setIdentityID(newId);

      MessageException thrown = assertThrows(MessageException.class,
         () -> service.updateUser(oldId, request, principal));

      assertSame(failure, thrown);
      assertArrayEquals(new Throwable[] { migrationFailure }, thrown.getSuppressed());
   }

   // the user is created before its admin grant is written; the theme and the identity audit
   // still run, and the failure is reported after them
   @Test
   void createUser_grantFails_themeAndIdentityAuditRunThenFailureReported() throws Exception {
      themes.add(theme("t1", "org1"));
      when(editableProvider.getOrganization("org1")).thenReturn(new FSOrganization("org1"));

      SecurityUser request = new SecurityUser();
      request.setIdentityID(new IdentityID("n1", "org1"));
      request.setPassword("Str0ng!Passw0rd");
      request.setTheme("t1");
      request.setAdminIdentities(new AdminIdentities());

      MessageException thrown = assertThrows(MessageException.class,
         () -> service.createUser(request, null, principal));

      assertSame(failure, thrown);
      verify(editableProvider).addUser(any(FSUser.class));
      assertEquals(List.of("n1"), themes.stream().filter(t -> "t1".equals(t.getId()))
         .findFirst().orElseThrow().getUsers());
      verify(audit).auditIdentityInfo(any(), eq(principal));
   }

   private static CustomTheme theme(String id, String orgID) {
      CustomTheme theme = new CustomTheme();
      theme.setId(id);
      theme.setName(id);
      theme.setOrgID(orgID);
      theme.setJarPath("portal/" + orgID + "/theme/" + id + ".jar");
      theme.setUsers(new ArrayList<>());
      return theme;
   }

   private Set<CustomTheme> themes;
   private SecurityProvider securityProvider;
   private AuthorizationChain authz;
   private MessageException failure;
   private EditableAuthenticationProvider editableProvider;
   private UserTreeService userTreeService;
   private Audit audit;
   private Principal principal;
   private SecurityService service;
   private MockedStatic<SUtil> sUtilStatic;
   private MockedStatic<OrganizationManager> organizationManagerStatic;
   private MockedStatic<Audit> auditStatic;
   private MockedStatic<SreeEnv> sreeEnvStatic;
   private MockedStatic<SecurityEngine> securityEngineStatic;
}
