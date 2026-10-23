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
 * Bug #76487 / Bug #76486: EM "New User" seeded the new user with every role flagged Default,
 * whatever the caller could assign. A delegated user manager (ADMIN on the users root only) thus
 * created users holding a system or organization administrator role. The real
 * UserTreeService.createUser and the real IdentityService default role filter run here against
 * a mocked provider; the user handed to addUser is the one checked. Not run on a live instance.
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
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("core")
class UserTreeServiceCreateUserDefaultRolesTest {
   private static final String ORG = "orga";
   private static final IdentityID PLAIN = new IdentityID("Viewer", ORG);
   private static final IdentityID SYS_ORG_ADMIN = new IdentityID("SysDef", null);
   private static final IdentityID ORG_BOSS = new IdentityID("OrgBoss", ORG);

   private final Map<IdentityID, FSRole> roles = new HashMap<>();
   private EditableAuthenticationProvider provider;
   private OrganizationManager orgManager;
   private Principal principal;
   private UserTreeService service;
   private MockedStatic<SUtil> sUtilStatic;
   private MockedStatic<Audit> auditStatic;
   private MockedStatic<SreeEnv> sreeEnvStatic;
   private MockedStatic<OrganizationManager> orgManagerStatic;

   @BeforeEach
   void setUp() throws Exception {
      sUtilStatic = mockStatic(SUtil.class, withSettings().strictness(Strictness.LENIENT));
      sUtilStatic.when(() -> SUtil.getActionRecord(any(Principal.class), anyString(), any(), anyString()))
         .thenReturn(mock(ActionRecord.class));
      sUtilStatic.when(SUtil::isMultiTenant).thenReturn(true);
      auditStatic = mockStatic(Audit.class, withSettings().strictness(Strictness.LENIENT));
      auditStatic.when(Audit::getInstance).thenReturn(mock(Audit.class));
      sreeEnvStatic = mockStatic(SreeEnv.class, withSettings().strictness(Strictness.LENIENT));
      sreeEnvStatic.when(SreeEnv::getProperties).thenReturn(new Properties());

      orgManager = mock(OrganizationManager.class, withSettings().lenient());
      when(orgManager.getCurrentOrgID()).thenReturn(ORG);
      orgManagerStatic = mockStatic(OrganizationManager.class,
                                    withSettings().strictness(Strictness.LENIENT));
      orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);
      caller(false, false);

      add(PLAIN, false, false);
      add(SYS_ORG_ADMIN, true, true);
      add(ORG_BOSS, false, true);

      provider = mock(EditableAuthenticationProvider.class, withSettings().lenient());
      when(provider.getGroups()).thenReturn(new IdentityID[0]);
      when(provider.getRoles()).thenReturn(roles.keySet().toArray(new IdentityID[0]));
      when(provider.getRole(any())).thenAnswer(inv -> roles.get(inv.<IdentityID>getArgument(0)));
      when(provider.getOrganization(ORG)).thenAnswer(inv -> {
         FSOrganization org = new FSOrganization(ORG);
         org.setName("Org A");
         return org;
      });
      when(provider.isSystemAdministratorRole(any())).thenAnswer(inv -> {
         FSRole role = roles.get(inv.<IdentityID>getArgument(0));
         return role != null && role.isSysAdmin();
      });
      when(provider.isOrgAdministratorRole(any())).thenAnswer(inv -> {
         FSRole role = roles.get(inv.<IdentityID>getArgument(0));
         return role != null && (role.isOrgAdmin() || role.isSysAdmin());
      });
      doCallRealMethod().when(provider).getAllRoles(any());
      doCallRealMethod().when(provider).getAllGroups(any());
      AuthenticationProviderService providerService =
         mock(AuthenticationProviderService.class, withSettings().lenient());
      when(providerService.getProviderByName("Primary")).thenReturn(provider);

      SecurityProvider securityProvider = mock(SecurityProvider.class, withSettings().lenient());
      when(securityProvider.getAuthenticationProvider()).thenReturn(provider);
      when(securityProvider.getOrganization(anyString()))
         .thenAnswer(inv -> new FSOrganization(inv.<String>getArgument(0)));
      // the delegate holds ADMIN on the users root, which is all createUser asks for
      when(securityProvider.checkPermission(any(Principal.class), any(ResourceType.class),
                                            anyString(), any(ResourceAction.class))).thenReturn(true);
      SecurityEngine securityEngine = mock(SecurityEngine.class, withSettings().lenient());
      when(securityEngine.getSecurityProvider()).thenReturn(securityProvider);
      when(securityEngine.isSecurityEnabled()).thenReturn(true);

      // the real default role filter, with the collaborators it reads
      IdentityService identityService = mock(
         IdentityService.class,
         withSettings().lenient().defaultAnswer(CALLS_REAL_METHODS));
      ReflectionTestUtils.setField(identityService, "securityEngine", securityEngine);
      ReflectionTestUtils.setField(identityService, "securityProvider", securityProvider);
      // a mock skips the field initializer
      ReflectionTestUtils.setField(identityService, "LOG", org.slf4j.LoggerFactory.getLogger(IdentityService.class));
      doNothing().when(identityService).checkSystemAdminParentGroup(any(), any(), any());
      doReturn(null).when(identityService).createIdentityPermissions(any(), any(), any());

      LocalizationSettingsService localizationService =
         mock(LocalizationSettingsService.class, withSettings().lenient());
      when(localizationService.getModel()).thenReturn(mock(LocalizationSettingsModel.class));
      CustomThemesManager themesManager = mock(CustomThemesManager.class, withSettings().lenient());
      when(themesManager.getCustomThemes()).thenReturn(new HashSet<>());

      principal = mock(Principal.class, withSettings().lenient());
      when(principal.getName()).thenReturn(new IdentityID("delegate", ORG).convertToKey());

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

   private void add(IdentityID id, boolean sysAdmin, boolean orgAdmin) {
      FSRole role = new FSRole(id);
      role.setDefaultRole(true);
      role.setSysAdmin(sysAdmin);
      role.setOrgAdmin(orgAdmin);
      roles.put(id, role);
   }

   private void caller(boolean siteAdmin, boolean orgAdmin) {
      when(orgManager.isSiteAdmin(any(Principal.class))).thenReturn(siteAdmin);
      when(orgManager.isOrgAdmin(any(Principal.class))).thenReturn(orgAdmin);
   }

   private Set<IdentityID> createdUserRoles() throws Exception {
      assertNotNull(service.createUser("Primary", null, principal));
      ArgumentCaptor<FSUser> captor = ArgumentCaptor.forClass(FSUser.class);
      verify(provider).addUser(captor.capture());
      return new HashSet<>(Arrays.asList(captor.getValue().getRoles()));
   }

   @Test
   void delegate_getsOrdinaryDefaultsButNoAdministratorDefault() throws Exception {
      assertEquals(Set.of(PLAIN), createdUserRoles());
   }

   @Test
   void orgAdmin_keepsOrgAdminDefaultsButNoSystemAdministratorOne() throws Exception {
      caller(false, true);

      assertEquals(Set.of(PLAIN, ORG_BOSS), createdUserRoles());
   }

   @Test
   void siteAdmin_stillGetsEveryDefault() throws Exception {
      caller(true, false);

      assertEquals(Set.of(PLAIN, SYS_ORG_ADMIN, ORG_BOSS), createdUserRoles());
   }
}
