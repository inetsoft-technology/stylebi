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

import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.portal.CustomThemesManager;
import inetsoft.sree.security.*;
import inetsoft.uql.XPrincipal;
import inetsoft.util.MessageException;
import inetsoft.util.audit.Audit;
import inetsoft.util.config.InetsoftConfig;
import inetsoft.web.admin.general.LocalizationSettingsService;
import inetsoft.web.admin.security.action.ActionPermissionService;
import inetsoft.web.admin.security.user.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.quality.Strictness;

import java.security.Principal;
import java.util.HashSet;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77504: PUT /api/public/security/users/{name} (SecurityService.updateUser) ignored the
 * password of the SecurityUser request body, while createUser applies it. The request returned
 * 204 and the password stayed unchanged. IdentityService.setIdentity() changes the password when
 * EditUserPaneModel.password() is set (as the EM user pane does), so the password must reach it,
 * and a weak password must be rejected before setIdentity() writes anything.
 */
@Tag("core")
class SecurityServiceUpdateUserPasswordTest {
   @BeforeEach
   void setUp() {
      AuthenticationProvider authenticationProvider =
         mock(AuthenticationProvider.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));
      securityProvider = mock(SecurityProvider.class, withSettings().lenient());
      when(securityProvider.getAuthenticationProvider()).thenReturn(authenticationProvider);
      // an update that omits adminIdentities re-keys the grant through the authorization
      // provider (Bug #77326)
      when(securityProvider.getAuthorizationProvider())
         .thenReturn(mock(AuthorizationChain.class, withSettings().lenient()));

      SecurityEngine securityEngine = mock(SecurityEngine.class, withSettings().lenient());
      when(securityEngine.getSecurityProvider()).thenReturn(securityProvider);
      securityEngineStatic = mockStatic(SecurityEngine.class,
                                        withSettings().strictness(Strictness.LENIENT));
      securityEngineStatic.when(SecurityEngine::getSecurity).thenReturn(securityEngine);

      editableProvider = mock(EditableAuthenticationProvider.class,
                              withSettings().defaultAnswer(CALLS_REAL_METHODS).lenient());
      doReturn(new IdentityID[0]).when(editableProvider).getUsers();
      doReturn(new IdentityID[0]).when(editableProvider).getGroups();
      doReturn(new IdentityID[0]).when(editableProvider).getRoles();

      sUtilStatic = mockStatic(SUtil.class, withSettings().strictness(Strictness.LENIENT));
      sUtilStatic.when(() -> SUtil.getEditableAuthenticationProvider(securityProvider))
         .thenReturn(editableProvider);
      sUtilStatic.when(SUtil::loadLocaleProperties).thenReturn(new Properties());

      organizationManagerStatic = mockStatic(OrganizationManager.class,
                                             withSettings().strictness(Strictness.LENIENT));
      organizationManagerStatic.when(OrganizationManager::getInstance)
         .thenReturn(mock(OrganizationManager.class, withSettings().lenient()));

      auditStatic = mockStatic(Audit.class, withSettings().strictness(Strictness.LENIENT));
      auditStatic.when(Audit::getInstance).thenReturn(mock(Audit.class, withSettings().lenient()));

      sreeEnvStatic = mockStatic(SreeEnv.class, withSettings().strictness(Strictness.LENIENT));
      // OrganizationIdRules reads the external storage config, keep it off the file system
      inetsoftConfigStatic = mockStatic(InetsoftConfig.class,
                                        withSettings().strictness(Strictness.LENIENT));
      inetsoftConfigStatic.when(InetsoftConfig::getInstance).thenReturn(mock(InetsoftConfig.class));

      identityService = mock(IdentityService.class, withSettings().lenient());

      // an XPrincipal, like the principals the public API and the shell pass in
      XPrincipal caller = mock(XPrincipal.class, withSettings().lenient());
      when(caller.getName()).thenReturn(new IdentityID("caller", "org1").convertToKey());
      when(caller.getOrgId()).thenReturn("org1");
      principal = caller;

      CustomThemesManager customThemesManager =
         mock(CustomThemesManager.class, withSettings().lenient());
      when(customThemesManager.getCustomThemes()).thenReturn(new HashSet<>());
      themeService = spy(new IdentityThemeService(customThemesManager));

      service = new SecurityService(
         securityEngine, identityService, mock(ActionPermissionService.class),
         mock(LocalizationSettingsService.class), themeService,
         mock(SystemAdminService.class), mock(UserTreeService.class, withSettings().lenient()),
         customThemesManager);

      oldUser = new FSUser(USER_ID);
      when(securityProvider.getUser(USER_ID)).thenReturn(oldUser);
      when(securityProvider.checkPermission(principal, ResourceType.SECURITY_USER,
                                            USER_ID.convertToKey(), ResourceAction.ADMIN))
         .thenReturn(true);
      when(editableProvider.getUser(USER_ID)).thenReturn(oldUser);
   }

   @AfterEach
   void tearDown() {
      sUtilStatic.close();
      organizationManagerStatic.close();
      auditStatic.close();
      sreeEnvStatic.close();
      securityEngineStatic.close();
      inetsoftConfigStatic.close();
   }

   @Test
   void updateUser_withPassword_passesPasswordToSetIdentity() throws Exception {
      SecurityUser request = new SecurityUser();
      request.setIdentityID(USER_ID);
      request.setPassword("N3w-Passw0rd!");

      service.updateUser(USER_ID, request, principal);

      assertEquals("N3w-Passw0rd!", captureSetIdentityModel().password(),
                   "updateUser dropped the request password, the user's password is unchanged");
   }

   @Test
   void updateUser_withoutPassword_keepsCurrentPassword() throws Exception {
      SecurityUser request = new SecurityUser();
      request.setIdentityID(USER_ID);
      request.setAlias("New Alias");

      service.updateUser(USER_ID, request, principal);

      // setIdentity() keeps the stored hash when the model password is null
      assertNull(captureSetIdentityModel().password(),
                 "an update that omits the password must keep the current password");
   }

   // setIdentity() renames the org member before it validates the password, so a weak password
   // must be rejected before setIdentity() is called, as createUser() does
   @ParameterizedTest
   @ValueSource(strings = { "success123", "" })
   void updateUser_weakPassword_rejectedBeforeSetIdentity(String password) throws Exception {
      SecurityUser request = new SecurityUser();
      request.setIdentityID(new IdentityID("u1-renamed", "org1"));
      request.setPassword(password);

      // updateUser() reports a refusal before any write as PreMutationRefusalException, with the
      // original exception as its cause
      SecurityService.PreMutationRefusalException ex = assertThrows(
         SecurityService.PreMutationRefusalException.class,
         () -> service.updateUser(USER_ID, request, principal));
      assertInstanceOf(MessageException.class, ex.getCause());

      verify(identityService, never()).setIdentity(any(), any(), any(), any());
      verify(themeService, never()).assignTheme(any(), any(), any(), any(), any());
   }

   private EditUserPaneModel captureSetIdentityModel() throws Exception {
      ArgumentCaptor<EditUserPaneModel> captor = ArgumentCaptor.forClass(EditUserPaneModel.class);
      verify(identityService).setIdentity(eq(oldUser), captor.capture(), eq(editableProvider),
                                          eq(principal));
      return captor.getValue();
   }

   private static final IdentityID USER_ID = new IdentityID("u1", "org1");

   private SecurityProvider securityProvider;
   private EditableAuthenticationProvider editableProvider;
   private IdentityService identityService;
   private IdentityThemeService themeService;
   private FSUser oldUser;
   private Principal principal;
   private SecurityService service;
   private MockedStatic<SUtil> sUtilStatic;
   private MockedStatic<OrganizationManager> organizationManagerStatic;
   private MockedStatic<Audit> auditStatic;
   private MockedStatic<SreeEnv> sreeEnvStatic;
   private MockedStatic<SecurityEngine> securityEngineStatic;
   private MockedStatic<InetsoftConfig> inetsoftConfigStatic;
}
