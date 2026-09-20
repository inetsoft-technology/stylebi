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
 * Regression coverage for Bug #75671 (vertical privilege escalation via public security API
 * role updates, fixed in commit c8bb2cba / PR #523). updateUser/updateGroup/updateRole applied
 * caller-supplied roles/inheritedRoles with no permission check on the roles themselves, so an
 * org-scoped admin could grant the site-wide "Administrator" role to themselves or anyone else
 * they can already administer. The fix added SecurityService.filterSystemAdminRoles(), which
 * drops any role for which AuthenticationProvider.isSystemAdministratorRole() is true unless the
 * caller is already a site admin (OrganizationManager.isSiteAdmin()).
 *
 * These tests drive the three fixed call sites end-to-end (not just the private filter method)
 * so a regression that stops calling filterSystemAdminRoles at one of the three sites -- the
 * actual shape of the original bug -- would be caught, not just a regression in the filter logic
 * itself.
 */

import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.portal.CustomTheme;
import inetsoft.sree.portal.CustomThemesManager;
import inetsoft.sree.security.*;
import inetsoft.uql.util.Identity;
import inetsoft.util.Catalog;
import inetsoft.util.audit.Audit;
import inetsoft.web.admin.InvalidResourceException;
import inetsoft.web.admin.general.LocalizationSettingsService;
import inetsoft.web.admin.security.action.ActionPermissionService;
import inetsoft.web.admin.security.action.ActionTreeNode;
import inetsoft.web.admin.security.user.*;
import inetsoft.web.security.auth.UnauthorizedAccessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.security.Principal;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("core")
class SecurityServiceTest {
   @BeforeEach
   void setUp() {
      authenticationProvider = mock(AuthenticationProvider.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));
      securityProvider = mock(SecurityProvider.class, withSettings().lenient());
      when(securityProvider.getAuthenticationProvider()).thenReturn(authenticationProvider);

      SecurityEngine securityEngine = mock(SecurityEngine.class, withSettings().lenient());
      when(securityEngine.getSecurityProvider()).thenReturn(securityProvider);
      // ActionRecord's constructor (invoked by createUser/createGroup/createRole) reaches for
      // the static SecurityEngine.getSecurity() singleton directly, independent of the instance
      // injected into SecurityService's constructor.
      securityEngineStatic = mockStatic(SecurityEngine.class,
                                        withSettings().strictness(org.mockito.quality.Strictness.LENIENT));
      securityEngineStatic.when(SecurityEngine::getSecurity).thenReturn(securityEngine);

      editableProvider = mock(EditableAuthenticationProvider.class,
                              withSettings().defaultAnswer(CALLS_REAL_METHODS).lenient());

      sUtilStatic = mockStatic(SUtil.class, withSettings().strictness(org.mockito.quality.Strictness.LENIENT));
      sUtilStatic.when(() -> SUtil.getEditableAuthenticationProvider(securityProvider))
         .thenReturn(editableProvider);
      sUtilStatic.when(SUtil::loadLocaleProperties).thenReturn(new Properties());

      OrganizationManager orgManager = mock(OrganizationManager.class, withSettings().lenient());
      organizationManagerStatic = mockStatic(OrganizationManager.class,
                                             withSettings().strictness(org.mockito.quality.Strictness.LENIENT));
      organizationManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);
      this.orgManager = orgManager;
      // applyOrganizationProperties's OrganizationManager.runInOrgScope call would otherwise be a
      // silent no-op under this static mock (a static mock with no CALLS_REAL_METHODS default
      // answer never invokes the real body) -- make it actually run the supplied Callable, so
      // property-write tests exercise the real SreeEnv.setProperty/getProperty logic inside it.
      organizationManagerStatic.when(() -> OrganizationManager.runInOrgScope(anyString(), any()))
         .thenAnswer(inv -> {
            java.util.concurrent.Callable<?> callable = inv.getArgument(1);
            return callable.call();
         });

      Audit audit = mock(Audit.class, withSettings().lenient());
      auditStatic = mockStatic(Audit.class, withSettings().strictness(org.mockito.quality.Strictness.LENIENT));
      auditStatic.when(Audit::getInstance).thenReturn(audit);

      sreeEnvStatic = mockStatic(SreeEnv.class, withSettings().strictness(org.mockito.quality.Strictness.LENIENT));
      // Default: no org-scoped properties stored anywhere -- getOrganizationModel's properties
      // scan (readOrganizationProperties) calls SreeEnv.getProperties().keySet() unconditionally,
      // so every test that reaches getOrganization/getOrganizationModel needs a non-null Properties
      // here, not just the tests that care about the properties field itself.
      sreeEnvStatic.when(SreeEnv::getProperties).thenReturn(new Properties());

      identityService = mock(IdentityService.class, withSettings().lenient());
      systemAdminService = mock(SystemAdminService.class, withSettings().lenient());

      principal = mock(Principal.class, withSettings().lenient());
      when(principal.getName()).thenReturn(new IdentityID("caller", "org1").convertToKey());

      actionPermissionService = mock(ActionPermissionService.class, withSettings().lenient());

      // Real IdentityThemeService (not a mock) backed by a mocked CustomThemesManager, so the
      // theme regression tests below can assert against actual persisted membership lists
      // instead of just verifying a call was made with the right arguments.
      customThemesManager = mock(CustomThemesManager.class, withSettings().lenient());
      when(customThemesManager.getCustomThemes()).thenReturn(new HashSet<>());
      themeService = new IdentityThemeService(customThemesManager);

      userTreeService = mock(UserTreeService.class, withSettings().lenient());

      service = new SecurityService(
         securityEngine, identityService, actionPermissionService,
         mock(LocalizationSettingsService.class), themeService,
         systemAdminService, userTreeService, customThemesManager);
   }

   @AfterEach
   void tearDown() {
      sUtilStatic.close();
      organizationManagerStatic.close();
      auditStatic.close();
      sreeEnvStatic.close();
      securityEngineStatic.close();
   }

   private static final IdentityID ADMIN_ROLE = new IdentityID("Administrator", "host-org");
   private static final IdentityID VIEWER_ROLE = new IdentityID("viewer", "org1");

   // ── getUser locale (Bug #76609) ───────────────────────────────────────────
   //
   // getUserModel used to convert the stored locale code back into a display label by matching
   // against localizationSettingsService.getModel().locales() -- a list gated by the
   // "locale.available" property, which is empty by default. On a deployment where no locales
   // have been explicitly enabled, that lookup never matches anything and always returned null,
   // regardless of what was actually stored. Fixed to return the stored code verbatim, like every
   // other passthrough field, so a locale set via createUser/updateUser round-trips back through
   // getUser as the same code.

   @Test
   void getUser_returnsStoredLocaleCodeVerbatim() throws Exception {
      IdentityID userId = new IdentityID("user1", "org1");
      FSUser storedUser = new FSUser(userId);
      storedUser.setLocale("en_US");
      when(securityProvider.checkPermission(principal, ResourceType.SECURITY_USER,
                                            userId.convertToKey(), ResourceAction.ADMIN))
         .thenReturn(true);
      when(securityProvider.getUser(userId)).thenReturn(storedUser);

      SecurityUser result = service.getUser(userId, principal);

      assertEquals("en_US", result.getLocale());
   }

   // ── updateUser ──────────────────────────────────────────────────────────

   @Test
   void updateUser_nonSiteAdmin_filtersAdministratorRole_keepsOtherRoles() throws Exception {
      IdentityID userId = new IdentityID("orgadmin1", "org1");
      FSUser oldUser = new FSUser(userId);
      when(securityProvider.getUser(userId)).thenReturn(oldUser);
      when(securityProvider.checkPermission(principal, ResourceType.SECURITY_USER,
                                            userId.convertToKey(), ResourceAction.ADMIN))
         .thenReturn(true);
      when(editableProvider.getUser(userId)).thenReturn(oldUser);
      when(orgManager.isSiteAdmin(principal)).thenReturn(false);

      SecurityUser request = new SecurityUser();
      request.setIdentityID(userId);
      request.setRoles(List.of(ADMIN_ROLE, VIEWER_ROLE));

      service.updateUser(userId, request, principal);

      ArgumentCaptor<EditUserPaneModel> captor = ArgumentCaptor.forClass(EditUserPaneModel.class);
      verify(identityService).setIdentity(eq(oldUser), captor.capture(), eq(editableProvider), eq(principal));
      assertEquals(List.of(VIEWER_ROLE), captor.getValue().roles());
   }

   @Test
   void updateUser_nonSiteAdmin_filtersSpoofedOrgIdAdministratorRole() throws Exception {
      // Reproduces Bug #75732. An org admin spoofs the organization id on the global system
      // Administrator role ({Administrator, host-org}). This models the AuthenticationChain
      // semantics that caused the escalation: isSystemAdministratorRole() returns false for the
      // phantom foreign-org role (the chain gates provider selection on getRole() != null, and
      // the exact key {Administrator, host-org} does not match the stored {Administrator, null}),
      // yet the name still resolves to the real global system admin role. The pre-fix filter
      // relied solely on isSystemAdministratorRole(role) and therefore let this role through.
      IdentityID userId = new IdentityID("orgadmin1", "org1");
      IdentityID globalAdmin = new IdentityID("Administrator", null);
      FSUser oldUser = new FSUser(userId);
      when(securityProvider.getUser(userId)).thenReturn(oldUser);
      when(securityProvider.checkPermission(principal, ResourceType.SECURITY_USER,
                                            userId.convertToKey(), ResourceAction.ADMIN))
         .thenReturn(true);
      when(editableProvider.getUser(userId)).thenReturn(oldUser);
      when(orgManager.isSiteAdmin(principal)).thenReturn(false);

      // Override the by-name interface default to reproduce the chain's exact-key behavior.
      doReturn(false).when(authenticationProvider).isSystemAdministratorRole(ADMIN_ROLE);
      doReturn(null).when(authenticationProvider).getRole(ADMIN_ROLE);
      doReturn(true).when(authenticationProvider).isSystemAdministratorRole(globalAdmin);

      SecurityUser request = new SecurityUser();
      request.setIdentityID(userId);
      request.setRoles(List.of(ADMIN_ROLE, VIEWER_ROLE));

      service.updateUser(userId, request, principal);

      ArgumentCaptor<EditUserPaneModel> captor = ArgumentCaptor.forClass(EditUserPaneModel.class);
      verify(identityService).setIdentity(eq(oldUser), captor.capture(), eq(editableProvider), eq(principal));
      assertEquals(List.of(VIEWER_ROLE), captor.getValue().roles());
   }

   @Test
   void updateUser_nonSiteAdmin_keepsExistingOrgScopedRoleNamedAdministrator() throws Exception {
      // Guards the fallback against false positives: a legitimate org-scoped role that merely
      // shares the "Administrator" name but is NOT the global system admin role must be kept.
      // Because it resolves via getRole() (it actually exists), the spoofed-role fallback -- which
      // only fires when getRole() == null -- does not strip it.
      IdentityID userId = new IdentityID("orgadmin1", "org1");
      IdentityID orgScopedAdmin = new IdentityID("Administrator", "org1");
      FSUser oldUser = new FSUser(userId);
      when(securityProvider.getUser(userId)).thenReturn(oldUser);
      when(securityProvider.checkPermission(principal, ResourceType.SECURITY_USER,
                                            userId.convertToKey(), ResourceAction.ADMIN))
         .thenReturn(true);
      when(editableProvider.getUser(userId)).thenReturn(oldUser);
      when(orgManager.isSiteAdmin(principal)).thenReturn(false);

      // The org-scoped role exists and is not a system admin role (chain delegates to the
      // provider, whose FSRole.isSysAdmin() is false).
      doReturn(false).when(authenticationProvider).isSystemAdministratorRole(orgScopedAdmin);
      doReturn(new FSRole(orgScopedAdmin)).when(authenticationProvider).getRole(orgScopedAdmin);

      SecurityUser request = new SecurityUser();
      request.setIdentityID(userId);
      request.setRoles(List.of(orgScopedAdmin, VIEWER_ROLE));

      service.updateUser(userId, request, principal);

      ArgumentCaptor<EditUserPaneModel> captor = ArgumentCaptor.forClass(EditUserPaneModel.class);
      verify(identityService).setIdentity(eq(oldUser), captor.capture(), eq(editableProvider), eq(principal));
      assertEquals(List.of(orgScopedAdmin, VIEWER_ROLE), captor.getValue().roles());
   }

   @Test
   void updateUser_siteAdmin_keepsAdministratorRole() throws Exception {
      IdentityID userId = new IdentityID("siteadmin1", "org1");
      FSUser oldUser = new FSUser(userId);
      when(securityProvider.getUser(userId)).thenReturn(oldUser);
      when(securityProvider.checkPermission(principal, ResourceType.SECURITY_USER,
                                            userId.convertToKey(), ResourceAction.ADMIN))
         .thenReturn(true);
      when(editableProvider.getUser(userId)).thenReturn(oldUser);
      when(orgManager.isSiteAdmin(principal)).thenReturn(true);

      SecurityUser request = new SecurityUser();
      request.setIdentityID(userId);
      request.setRoles(List.of(ADMIN_ROLE, VIEWER_ROLE));

      service.updateUser(userId, request, principal);

      ArgumentCaptor<EditUserPaneModel> captor = ArgumentCaptor.forClass(EditUserPaneModel.class);
      verify(identityService).setIdentity(eq(oldUser), captor.capture(), eq(editableProvider), eq(principal));
      assertEquals(List.of(ADMIN_ROLE, VIEWER_ROLE), captor.getValue().roles());
   }

   @Test
   void updateUser_rolesNotSpecified_doesNotSetRoles() throws Exception {
      IdentityID userId = new IdentityID("orgadmin1", "org1");
      FSUser oldUser = new FSUser(userId);
      when(securityProvider.getUser(userId)).thenReturn(oldUser);
      when(securityProvider.checkPermission(principal, ResourceType.SECURITY_USER,
                                            userId.convertToKey(), ResourceAction.ADMIN))
         .thenReturn(true);
      when(editableProvider.getUser(userId)).thenReturn(oldUser);
      when(orgManager.isSiteAdmin(principal)).thenReturn(false);

      SecurityUser request = new SecurityUser();
      request.setIdentityID(userId);
      request.setRoles(null);

      service.updateUser(userId, request, principal);

      ArgumentCaptor<EditUserPaneModel> captor = ArgumentCaptor.forClass(EditUserPaneModel.class);
      verify(identityService).setIdentity(eq(oldUser), captor.capture(), eq(editableProvider), eq(principal));
      assertTrue(captor.getValue().roles().isEmpty());
   }

   // ── updateUser locale (Bug #76609) ───────────────────────────────────────
   //
   // updateUser passed request.getLocale() (a code, per the Public API's documented shape)
   // straight into EditUserPaneModel.locale() with no transform, but IdentityService.setUserInfo
   // -- shared with the EM edit-user pane, whose own locale dropdown genuinely works in display
   // labels -- reverse-looks-up that field expecting a label, so a code silently resolved to
   // nothing there too. Fixed by validating the code the same way createUser does and translating
   // it to the matching label before handing it to the shared, unmodified EditUserPaneModel/
   // setUserInfo contract, so it round-trips back to the same code.

   @Test
   void updateUser_validLocaleCode_translatesToLabelForSharedSetUserInfoContract() throws Exception {
      IdentityID userId = new IdentityID("orgadmin1", "org1");
      FSUser oldUser = new FSUser(userId);
      when(securityProvider.getUser(userId)).thenReturn(oldUser);
      when(securityProvider.checkPermission(principal, ResourceType.SECURITY_USER,
                                            userId.convertToKey(), ResourceAction.ADMIN))
         .thenReturn(true);
      when(editableProvider.getUser(userId)).thenReturn(oldUser);
      when(orgManager.isSiteAdmin(principal)).thenReturn(false);
      Properties localeProperties = new Properties();
      localeProperties.setProperty("en_US", "English(America)");
      sUtilStatic.when(SUtil::loadLocaleProperties).thenReturn(localeProperties);

      SecurityUser request = new SecurityUser();
      request.setIdentityID(userId);
      request.setLocale("en_US");

      service.updateUser(userId, request, principal);

      ArgumentCaptor<EditUserPaneModel> captor = ArgumentCaptor.forClass(EditUserPaneModel.class);
      verify(identityService).setIdentity(eq(oldUser), captor.capture(), eq(editableProvider), eq(principal));
      assertEquals("English(America)", captor.getValue().locale());
   }

   @Test
   void updateUser_unrecognizedLocale_throwsInsteadOfSilentlyDroppingIt() throws Exception {
      IdentityID userId = new IdentityID("orgadmin1", "org1");
      FSUser oldUser = new FSUser(userId);
      when(securityProvider.getUser(userId)).thenReturn(oldUser);
      when(securityProvider.checkPermission(principal, ResourceType.SECURITY_USER,
                                            userId.convertToKey(), ResourceAction.ADMIN))
         .thenReturn(true);
      when(editableProvider.getUser(userId)).thenReturn(oldUser);
      when(orgManager.isSiteAdmin(principal)).thenReturn(false);
      Properties localeProperties = new Properties();
      localeProperties.setProperty("en_US", "English(America)");
      sUtilStatic.when(SUtil::loadLocaleProperties).thenReturn(localeProperties);

      SecurityUser request = new SecurityUser();
      request.setIdentityID(userId);
      request.setLocale("English(America)");

      assertThrows(InvalidResourceException.class,
                   () -> service.updateUser(userId, request, principal));
      verify(identityService, never()).setIdentity(any(), any(), any(), any());
   }

   // ── updateGroup ─────────────────────────────────────────────────────────

   @Test
   void updateGroup_nonSiteAdmin_filtersAdministratorRole_keepsOtherRoles() throws Exception {
      IdentityID groupId = new IdentityID("orggroup1", "org1");
      FSGroup oldGroup = new FSGroup(groupId);
      when(securityProvider.getGroup(groupId)).thenReturn(oldGroup);
      when(securityProvider.checkPermission(principal, ResourceType.SECURITY_GROUP,
                                            groupId.convertToKey(), ResourceAction.ADMIN))
         .thenReturn(true);
      when(editableProvider.getGroup(groupId)).thenReturn(oldGroup);
      when(orgManager.isSiteAdmin(principal)).thenReturn(false);

      SecurityGroup request = new SecurityGroup();
      request.setIdentityID(groupId);
      request.setRoles(List.of(ADMIN_ROLE, VIEWER_ROLE));

      service.updateGroup(groupId, request, principal);

      ArgumentCaptor<EditGroupPaneModel> captor = ArgumentCaptor.forClass(EditGroupPaneModel.class);
      verify(identityService).setIdentity(eq(oldGroup), captor.capture(), eq(editableProvider), eq(principal));
      assertEquals(List.of(VIEWER_ROLE), captor.getValue().roles());
   }

   @Test
   void updateGroup_siteAdmin_keepsAdministratorRole() throws Exception {
      IdentityID groupId = new IdentityID("orggroup1", "org1");
      FSGroup oldGroup = new FSGroup(groupId);
      when(securityProvider.getGroup(groupId)).thenReturn(oldGroup);
      when(securityProvider.checkPermission(principal, ResourceType.SECURITY_GROUP,
                                            groupId.convertToKey(), ResourceAction.ADMIN))
         .thenReturn(true);
      when(editableProvider.getGroup(groupId)).thenReturn(oldGroup);
      when(orgManager.isSiteAdmin(principal)).thenReturn(true);

      SecurityGroup request = new SecurityGroup();
      request.setIdentityID(groupId);
      request.setRoles(List.of(ADMIN_ROLE, VIEWER_ROLE));

      service.updateGroup(groupId, request, principal);

      ArgumentCaptor<EditGroupPaneModel> captor = ArgumentCaptor.forClass(EditGroupPaneModel.class);
      verify(identityService).setIdentity(eq(oldGroup), captor.capture(), eq(editableProvider), eq(principal));
      assertEquals(List.of(ADMIN_ROLE, VIEWER_ROLE), captor.getValue().roles());
   }

   @Test
   void updateGroup_rolesNotSpecified_doesNotSetRoles() throws Exception {
      IdentityID groupId = new IdentityID("orggroup1", "org1");
      FSGroup oldGroup = new FSGroup(groupId);
      when(securityProvider.getGroup(groupId)).thenReturn(oldGroup);
      when(securityProvider.checkPermission(principal, ResourceType.SECURITY_GROUP,
                                            groupId.convertToKey(), ResourceAction.ADMIN))
         .thenReturn(true);
      when(editableProvider.getGroup(groupId)).thenReturn(oldGroup);
      when(orgManager.isSiteAdmin(principal)).thenReturn(false);

      SecurityGroup request = new SecurityGroup();
      request.setIdentityID(groupId);
      request.setRoles(null);

      service.updateGroup(groupId, request, principal);

      ArgumentCaptor<EditGroupPaneModel> captor = ArgumentCaptor.forClass(EditGroupPaneModel.class);
      verify(identityService).setIdentity(eq(oldGroup), captor.capture(), eq(editableProvider), eq(principal));
      assertTrue(captor.getValue().roles().isEmpty());
   }

   // ── updateRole (inheritedRoles) ─────────────────────────────────────────

   @Test
   void updateRole_nonSiteAdmin_filtersAdministratorInheritedRole_keepsOtherRoles() throws Exception {
      IdentityID roleId = new IdentityID("orgrole1", "org1");
      FSRole oldRole = new FSRole(roleId);
      when(securityProvider.getRole(roleId)).thenReturn(oldRole);
      when(securityProvider.checkPermission(principal, ResourceType.SECURITY_ROLE,
                                            roleId.convertToKey(), ResourceAction.ADMIN))
         .thenReturn(true);
      when(editableProvider.getRole(roleId)).thenReturn(oldRole);
      when(identityService.getIdentityInfo(roleId, Identity.ROLE, editableProvider))
         .thenReturn(new IdentityInfo());
      when(orgManager.isSiteAdmin(principal)).thenReturn(false);

      SecurityRole request = new SecurityRole();
      request.setIdentityID(roleId);
      request.setInheritedRoles(List.of(ADMIN_ROLE, VIEWER_ROLE));

      service.updateRole(roleId, request, principal);

      ArgumentCaptor<EditRolePaneModel> captor = ArgumentCaptor.forClass(EditRolePaneModel.class);
      verify(identityService).setIdentity(eq(oldRole), captor.capture(), eq(editableProvider), eq(principal));
      assertEquals(List.of(VIEWER_ROLE), captor.getValue().roles());
   }

   @Test
   void updateRole_siteAdmin_keepsAdministratorInheritedRole() throws Exception {
      IdentityID roleId = new IdentityID("orgrole1", "org1");
      FSRole oldRole = new FSRole(roleId);
      when(securityProvider.getRole(roleId)).thenReturn(oldRole);
      when(securityProvider.checkPermission(principal, ResourceType.SECURITY_ROLE,
                                            roleId.convertToKey(), ResourceAction.ADMIN))
         .thenReturn(true);
      when(editableProvider.getRole(roleId)).thenReturn(oldRole);
      when(identityService.getIdentityInfo(roleId, Identity.ROLE, editableProvider))
         .thenReturn(new IdentityInfo());
      when(orgManager.isSiteAdmin(principal)).thenReturn(true);

      SecurityRole request = new SecurityRole();
      request.setIdentityID(roleId);
      request.setInheritedRoles(List.of(ADMIN_ROLE, VIEWER_ROLE));

      service.updateRole(roleId, request, principal);

      ArgumentCaptor<EditRolePaneModel> captor = ArgumentCaptor.forClass(EditRolePaneModel.class);
      verify(identityService).setIdentity(eq(oldRole), captor.capture(), eq(editableProvider), eq(principal));
      assertEquals(List.of(ADMIN_ROLE, VIEWER_ROLE), captor.getValue().roles());
   }

   @Test
   void updateRole_inheritedRolesNotSpecified_doesNotSetRoles() throws Exception {
      IdentityID roleId = new IdentityID("orgrole1", "org1");
      FSRole oldRole = new FSRole(roleId);
      when(securityProvider.getRole(roleId)).thenReturn(oldRole);
      when(securityProvider.checkPermission(principal, ResourceType.SECURITY_ROLE,
                                            roleId.convertToKey(), ResourceAction.ADMIN))
         .thenReturn(true);
      when(editableProvider.getRole(roleId)).thenReturn(oldRole);
      when(identityService.getIdentityInfo(roleId, Identity.ROLE, editableProvider))
         .thenReturn(new IdentityInfo());
      when(orgManager.isSiteAdmin(principal)).thenReturn(false);

      SecurityRole request = new SecurityRole();
      request.setIdentityID(roleId);
      request.setInheritedRoles(null);

      service.updateRole(roleId, request, principal);

      ArgumentCaptor<EditRolePaneModel> captor = ArgumentCaptor.forClass(EditRolePaneModel.class);
      verify(identityService).setIdentity(eq(oldRole), captor.capture(), eq(editableProvider), eq(principal));
      assertTrue(captor.getValue().roles().isEmpty());
   }

   // ── createUser / createGroup / createRole ───────────────────────────────
   //
   // Bug #75671-class gap found while writing the update* regression tests above: createUser's
   // and createGroup's "only add roles if api user has root role permission" gate
   // (checkPermission(SECURITY_ROLE|SECURITY_USER|SECURITY_GROUP, "*"@currentOrg, ADMIN)) turns
   // out to also be satisfied by an ordinary org-admin -- confirmed empirically against
   // DefaultCheckPermissionStrategy's checkOrgAdminPermission(), whose SECURITY_ROLE branch
   // unconditionally returns true for the wildcard "*"@orgID resource for any org-admin. So that
   // gate never actually restricted the roles field to site admins; it just happened to also let
   // org-admins through. createRole's inheritedRoles had no such filtering at all. All three now
   // route through filterSystemAdminRoles() the same way updateUser/updateGroup/updateRole do.

   private void stubCommonCreateGates() {
      when(securityProvider.getOrganizationIDs()).thenReturn(new String[]{ "org1" });
      when(securityProvider.checkPermission(eq(principal), any(ResourceType.class), anyString(),
                                            eq(ResourceAction.ADMIN)))
         .thenReturn(true);
      when(orgManager.getCurrentOrgID()).thenReturn("org1");
      when(orgManager.isSiteAdmin(principal)).thenReturn(false);
      when(editableProvider.getRoles()).thenReturn(new IdentityID[0]);
      when(editableProvider.getRole(VIEWER_ROLE)).thenReturn(new FSRole(VIEWER_ROLE));
      // Administrator legitimately exists in the provider (it's the built-in site-admin role) --
      // stubbed non-null so the "role exists" filter isn't what removes it from the result;
      // only filterSystemAdminRoles() should be responsible for that, which is what's under test.
      when(editableProvider.getRole(ADMIN_ROLE)).thenReturn(new FSRole(ADMIN_ROLE));
   }

   @Test
   void createUser_nonSiteAdmin_filtersAdministratorRole_keepsOtherRoles() throws Exception {
      stubCommonCreateGates();
      when(editableProvider.getOrganization("org1")).thenReturn(new FSOrganization("org1"));

      SecurityUser request = new SecurityUser();
      request.setIdentityID(new IdentityID("newuser1", "org1"));
      request.setPassword("Str0ng!Passw0rd");
      request.setRoles(List.of(ADMIN_ROLE, VIEWER_ROLE));

      service.createUser(request, null, principal);

      ArgumentCaptor<FSUser> captor = ArgumentCaptor.forClass(FSUser.class);
      verify(editableProvider).addUser(captor.capture());
      assertEquals(List.of(VIEWER_ROLE), List.of(captor.getValue().getRoles()));
   }

   // ── createUser locale (Bug #76609) ───────────────────────────────────────
   //
   // getLocale(String) used to do a reverse (label -> code) lookup against
   // SUtil.loadLocaleProperties(), so a caller passing a locale code (the Public API's documented
   // shape, e.g. "en_US") matched no property value and silently persisted as null. Fixed by
   // validateLocale(), which treats the input as a code and stores it verbatim, throwing loudly
   // for anything that isn't a known code instead of dropping it.

   @Test
   void createUser_validLocaleCode_persistsVerbatim() throws Exception {
      stubCommonCreateGates();
      when(editableProvider.getOrganization("org1")).thenReturn(new FSOrganization("org1"));
      Properties localeProperties = new Properties();
      localeProperties.setProperty("en_US", "English(America)");
      sUtilStatic.when(SUtil::loadLocaleProperties).thenReturn(localeProperties);

      SecurityUser request = new SecurityUser();
      request.setIdentityID(new IdentityID("newuser1", "org1"));
      request.setPassword("Str0ng!Passw0rd");
      request.setLocale("en_US");

      service.createUser(request, null, principal);

      ArgumentCaptor<FSUser> captor = ArgumentCaptor.forClass(FSUser.class);
      verify(editableProvider).addUser(captor.capture());
      assertEquals("en_US", captor.getValue().getLocale());
   }

   @Test
   void createUser_unrecognizedLocale_throwsInsteadOfSilentlyDroppingIt() throws Exception {
      stubCommonCreateGates();
      when(editableProvider.getOrganization("org1")).thenReturn(new FSOrganization("org1"));
      Properties localeProperties = new Properties();
      localeProperties.setProperty("en_US", "English(America)");
      sUtilStatic.when(SUtil::loadLocaleProperties).thenReturn(localeProperties);

      SecurityUser request = new SecurityUser();
      request.setIdentityID(new IdentityID("newuser2", "org1"));
      request.setPassword("Str0ng!Passw0rd");
      // A display label, not a code -- exactly the input shape the old reverse lookup expected
      // and the fixed validation rejects instead of silently accepting.
      request.setLocale("English(America)");

      assertThrows(InvalidResourceException.class,
                   () -> service.createUser(request, null, principal));
      verify(editableProvider, never()).addUser(any());
   }

   @Test
   void createGroup_nonSiteAdmin_filtersAdministratorRole_keepsOtherRoles() throws Exception {
      stubCommonCreateGates();

      SecurityGroup request = new SecurityGroup();
      request.setIdentityID(new IdentityID("newgroup1", "org1"));
      request.setOrgID("org1");
      request.setRoles(List.of(ADMIN_ROLE, VIEWER_ROLE));

      service.createGroup(request, null, principal);

      ArgumentCaptor<FSGroup> captor = ArgumentCaptor.forClass(FSGroup.class);
      verify(editableProvider).addGroup(captor.capture());
      assertEquals(List.of(VIEWER_ROLE), List.of(captor.getValue().getRoles()));
   }

   @Test
   void createRole_nonSiteAdmin_filtersAdministratorInheritedRole_keepsOtherRoles() throws Exception {
      stubCommonCreateGates();

      SecurityRole request = new SecurityRole();
      request.setIdentityID(new IdentityID("neworgrole1", "org1"));
      request.setInheritedRoles(List.of(ADMIN_ROLE, VIEWER_ROLE));

      service.createRole(request, null, principal);

      ArgumentCaptor<FSRole> captor = ArgumentCaptor.forClass(FSRole.class);
      verify(editableProvider).addRole(captor.capture());
      assertEquals(List.of(VIEWER_ROLE), List.of(captor.getValue().getRoles()));
   }

   // ── createRole/updateRole/getRole defaultRole & sysAdmin (Bug #76715) ───────────────────

   @Test
   void createRole_setsDefaultRoleAndSysAdminFromRequest() throws Exception {
      stubCommonCreateGates();

      SecurityRole request = new SecurityRole();
      request.setIdentityID(new IdentityID("neworgrole2", "org1"));
      request.setDefaultRole(true);
      request.setSysAdmin(true);

      service.createRole(request, null, principal);

      ArgumentCaptor<FSRole> captor = ArgumentCaptor.forClass(FSRole.class);
      verify(editableProvider).addRole(captor.capture());
      assertTrue(captor.getValue().isDefaultRole());
      assertTrue(captor.getValue().isSysAdmin());
   }

   @Test
   void createRole_omittedDefaultRoleAndSysAdmin_defaultToFalse() throws Exception {
      stubCommonCreateGates();

      SecurityRole request = new SecurityRole();
      request.setIdentityID(new IdentityID("neworgrole3", "org1"));

      service.createRole(request, null, principal);

      ArgumentCaptor<FSRole> captor = ArgumentCaptor.forClass(FSRole.class);
      verify(editableProvider).addRole(captor.capture());
      assertFalse(captor.getValue().isDefaultRole());
      assertFalse(captor.getValue().isSysAdmin());
   }

   @Test
   void updateRole_setsDefaultRoleAndSysAdminFromRequest() throws Exception {
      IdentityID roleId = new IdentityID("orgrole1", "org1");
      FSRole oldRole = new FSRole(roleId);
      when(securityProvider.getRole(roleId)).thenReturn(oldRole);
      when(securityProvider.checkPermission(principal, ResourceType.SECURITY_ROLE,
                                            roleId.convertToKey(), ResourceAction.ADMIN))
         .thenReturn(true);
      when(editableProvider.getRole(roleId)).thenReturn(oldRole);

      SecurityRole request = new SecurityRole();
      request.setIdentityID(roleId);
      request.setDefaultRole(true);
      request.setSysAdmin(true);

      service.updateRole(roleId, request, principal);

      ArgumentCaptor<EditRolePaneModel> captor = ArgumentCaptor.forClass(EditRolePaneModel.class);
      verify(identityService).setIdentity(eq(oldRole), captor.capture(), eq(editableProvider), eq(principal));
      assertTrue(captor.getValue().defaultRole());
      assertTrue(captor.getValue().isSysAdmin());
   }

   // updateRole's request already carries the caller's override or the current value, preserved by
   // IdentityMerge.mergeRole upstream of this method in the identities apply path (IdentityMergeTest
   // covers that preservation directly) -- this pins updateRole's OWN contract: it must trust
   // request verbatim, the same blind-overwrite-on-omission behavior description/theme already have
   // here, not silently re-derive from the role's current persisted state as it used to.
   @Test
   void updateRole_omittedDefaultRoleAndSysAdmin_blindlyOverwritesToFalse() throws Exception {
      IdentityID roleId = new IdentityID("orgrole1", "org1");
      FSRole oldRole = new FSRole(roleId);
      oldRole.setDefaultRole(true);
      oldRole.setSysAdmin(true);
      when(securityProvider.getRole(roleId)).thenReturn(oldRole);
      when(securityProvider.checkPermission(principal, ResourceType.SECURITY_ROLE,
                                            roleId.convertToKey(), ResourceAction.ADMIN))
         .thenReturn(true);
      when(editableProvider.getRole(roleId)).thenReturn(oldRole);

      SecurityRole request = new SecurityRole();
      request.setIdentityID(roleId);
      // defaultRole/sysAdmin left unset on the raw request -- a direct REST caller who omits them.

      service.updateRole(roleId, request, principal);

      ArgumentCaptor<EditRolePaneModel> captor = ArgumentCaptor.forClass(EditRolePaneModel.class);
      verify(identityService).setIdentity(eq(oldRole), captor.capture(), eq(editableProvider), eq(principal));
      assertFalse(captor.getValue().defaultRole());
      assertFalse(captor.getValue().isSysAdmin());
   }

   @Test
   void getRole_existingRole_returnsDefaultRoleAndSysAdmin() throws Exception {
      IdentityID role = new IdentityID("Analyst", "org1");
      when(securityProvider.checkPermission(principal, ResourceType.SECURITY_ROLE,
                                            role.convertToKey(), ResourceAction.ADMIN))
         .thenReturn(true);
      FSRole existingRole = new FSRole(role, "Read-only analyst role");
      existingRole.setDefaultRole(true);
      existingRole.setSysAdmin(true);
      when(securityProvider.getRole(role)).thenReturn(existingRole);
      doReturn(new Identity[0]).when(authenticationProvider).getRoleMembers(role);
      IdentityInfo info = new IdentityInfo(existingRole, authenticationProvider);
      when(identityService.getIdentityInfo(role, Identity.ROLE, securityProvider)).thenReturn(info);
      when(identityService.getPermission(eq(role), eq(ResourceType.SECURITY_ROLE), any(), eq(principal)))
         .thenReturn(List.of());
      when(securityProvider.isSystemAdministratorRole(role)).thenReturn(true);

      SecurityRole result = service.getRole(role, principal);

      assertEquals(Boolean.TRUE, result.getDefaultRole());
      assertEquals(Boolean.TRUE, result.getSysAdmin());
   }

   // ── createRole/updateRole/getRole orgAdmin (Bug #76824) ─────────────────────────────────
   //
   // orgAdmin's update-path contract is deliberately NOT the same blind-overwrite-on-omission
   // contract sysAdmin/defaultRole use above: an omitted orgAdmin must preserve the role's current
   // server-side state (identityTools.ts's own omit-preserves-current-value update semantics),
   // not reset to false. See the comment at SecurityService.updateRole's isOrgAdmin(...) line.

   @Test
   void createRole_setsOrgAdminFromRequest() throws Exception {
      stubCommonCreateGates();

      SecurityRole request = new SecurityRole();
      request.setIdentityID(new IdentityID("neworgrole4", "org1"));
      request.setOrgAdmin(true);

      service.createRole(request, null, principal);

      ArgumentCaptor<FSRole> captor = ArgumentCaptor.forClass(FSRole.class);
      verify(editableProvider).addRole(captor.capture());
      assertTrue(captor.getValue().isOrgAdmin());
   }

   @Test
   void createRole_omittedOrgAdmin_defaultsToFalse() throws Exception {
      stubCommonCreateGates();

      SecurityRole request = new SecurityRole();
      request.setIdentityID(new IdentityID("neworgrole5", "org1"));

      service.createRole(request, null, principal);

      ArgumentCaptor<FSRole> captor = ArgumentCaptor.forClass(FSRole.class);
      verify(editableProvider).addRole(captor.capture());
      assertFalse(captor.getValue().isOrgAdmin());
   }

   @Test
   void updateRole_setsOrgAdminFromRequestWhenExplicitlyProvided() throws Exception {
      IdentityID roleId = new IdentityID("orgrole2", "org1");
      FSRole oldRole = new FSRole(roleId);
      when(securityProvider.getRole(roleId)).thenReturn(oldRole);
      when(securityProvider.checkPermission(principal, ResourceType.SECURITY_ROLE,
                                            roleId.convertToKey(), ResourceAction.ADMIN))
         .thenReturn(true);
      when(editableProvider.getRole(roleId)).thenReturn(oldRole);
      when(editableProvider.isOrgAdministratorRole(roleId)).thenReturn(false);

      SecurityRole request = new SecurityRole();
      request.setIdentityID(roleId);
      request.setOrgAdmin(true);

      service.updateRole(roleId, request, principal);

      ArgumentCaptor<EditRolePaneModel> captor = ArgumentCaptor.forClass(EditRolePaneModel.class);
      verify(identityService).setIdentity(eq(oldRole), captor.capture(), eq(editableProvider), eq(principal));
      assertTrue(captor.getValue().isOrgAdmin());
   }

   @Test
   void updateRole_omittedOrgAdmin_preservesCurrentValueRatherThanOverwriting() throws Exception {
      IdentityID roleId = new IdentityID("orgrole3", "org1");
      FSRole oldRole = new FSRole(roleId);
      when(securityProvider.getRole(roleId)).thenReturn(oldRole);
      when(securityProvider.checkPermission(principal, ResourceType.SECURITY_ROLE,
                                            roleId.convertToKey(), ResourceAction.ADMIN))
         .thenReturn(true);
      when(editableProvider.getRole(roleId)).thenReturn(oldRole);
      // The role is currently an org-admin role server-side.
      when(editableProvider.isOrgAdministratorRole(roleId)).thenReturn(true);

      SecurityRole request = new SecurityRole();
      request.setIdentityID(roleId);
      // orgAdmin left unset on the request -- must NOT be reset to false, unlike sysAdmin/defaultRole.

      service.updateRole(roleId, request, principal);

      ArgumentCaptor<EditRolePaneModel> captor = ArgumentCaptor.forClass(EditRolePaneModel.class);
      verify(identityService).setIdentity(eq(oldRole), captor.capture(), eq(editableProvider), eq(principal));
      assertTrue(captor.getValue().isOrgAdmin());
   }

   @Test
   void getRole_existingRole_returnsOrgAdmin() throws Exception {
      IdentityID role = new IdentityID("OrgAdminRole", "org1");
      when(securityProvider.checkPermission(principal, ResourceType.SECURITY_ROLE,
                                            role.convertToKey(), ResourceAction.ADMIN))
         .thenReturn(true);
      FSRole existingRole = new FSRole(role, "Org admin role");
      when(securityProvider.getRole(role)).thenReturn(existingRole);
      doReturn(new Identity[0]).when(authenticationProvider).getRoleMembers(role);
      IdentityInfo info = new IdentityInfo(existingRole, authenticationProvider);
      when(identityService.getIdentityInfo(role, Identity.ROLE, securityProvider)).thenReturn(info);
      when(identityService.getPermission(eq(role), eq(ResourceType.SECURITY_ROLE), any(), eq(principal)))
         .thenReturn(List.of());
      when(securityProvider.isOrgAdministratorRole(role)).thenReturn(true);

      SecurityRole result = service.getRole(role, principal);

      assertEquals(Boolean.TRUE, result.getOrgAdmin());
   }

   // ── createUser/createGroup parent-group permission key form (Bug #76654) ────────────────
   //
   // createUser's request.getGroups() filter and createGroup's request.getParentGroups() filter
   // called checkPermission(SECURITY_GROUP, <bare group name>, ADMIN) while every sibling field
   // in these same methods -- and the correctly-implemented updateUser/updateParentGroups
   // counterparts -- check the group's org-scoped key (IdentityID(name, orgID).convertToKey()).
   // A bare name never matches a permission actually granted (and stored) against the keyed
   // IdentityID, so a caller who legitimately holds ADMIN on a specific, non-root parent group
   // had it silently dropped. stubCommonCreateGates()'s checkPermission(..., anyString(), ...)
   // -> true stub can't tell a bare name from a keyed one, so these tests deliberately do NOT
   // use it for the SECURITY_GROUP check -- they stub a discriminating Answer instead, so this
   // regresses loudly (test fails) if the bare name is ever checked again instead of the key.

   @Test
   void createGroup_parentGroupPermission_checkedWithOrgScopedKey_notBareName() throws Exception {
      when(securityProvider.getOrganizationIDs()).thenReturn(new String[]{ "org1" });
      when(orgManager.getCurrentOrgID()).thenReturn("org1");
      when(orgManager.isSiteAdmin(principal)).thenReturn(false);
      when(editableProvider.getRoles()).thenReturn(new IdentityID[0]);

      IdentityID parentGroupId = new IdentityID("parentgroup1", "org1");
      String expectedKey = parentGroupId.convertToKey();
      when(authenticationProvider.getGroup(parentGroupId)).thenReturn(new FSGroup(parentGroupId));

      // Caller holds ADMIN on parentgroup1 only via its org-scoped key -- not on the bare name,
      // not on any root/wildcard SECURITY_GROUP resource.
      when(securityProvider.checkPermission(eq(principal), eq(ResourceType.SECURITY_GROUP),
                                            anyString(), eq(ResourceAction.ADMIN)))
         .thenAnswer(inv -> expectedKey.equals(inv.getArgument(2)));

      SecurityGroup request = new SecurityGroup();
      request.setIdentityID(new IdentityID("newgroup1", "org1"));
      request.setOrgID("org1");
      request.setParentGroups(List.of("parentgroup1"));

      service.createGroup(request, null, principal);

      ArgumentCaptor<FSGroup> captor = ArgumentCaptor.forClass(FSGroup.class);
      verify(editableProvider).addGroup(captor.capture());
      assertTrue(List.of(captor.getValue().getGroups()).contains("parentgroup1"),
         "caller holds ADMIN on parentgroup1 (checked via its org-scoped key) but it was "
         + "dropped because createGroup checked the bare name instead");
   }

   @Test
   void createUser_groupPermission_checkedWithOrgScopedKey_notBareName() throws Exception {
      when(securityProvider.getOrganizationIDs()).thenReturn(new String[]{ "org1" });
      when(orgManager.getCurrentOrgID()).thenReturn("org1");
      when(orgManager.isSiteAdmin(principal)).thenReturn(false);
      when(editableProvider.getRoles()).thenReturn(new IdentityID[0]);
      when(editableProvider.getOrganization("org1")).thenReturn(new FSOrganization("org1"));

      IdentityID groupId = new IdentityID("group1", "org1");
      String expectedKey = groupId.convertToKey();
      when(authenticationProvider.getGroup(groupId)).thenReturn(new FSGroup(groupId));

      // Caller holds ADMIN on group1 only via its org-scoped key -- not on the bare name, not on
      // any root/wildcard SECURITY_GROUP resource.
      when(securityProvider.checkPermission(eq(principal), eq(ResourceType.SECURITY_GROUP),
                                            anyString(), eq(ResourceAction.ADMIN)))
         .thenAnswer(inv -> expectedKey.equals(inv.getArgument(2)));

      SecurityUser request = new SecurityUser();
      request.setIdentityID(new IdentityID("newuser1", "org1"));
      request.setPassword("Str0ng!Passw0rd");
      request.setGroups(List.of("group1"));

      service.createUser(request, null, principal);

      ArgumentCaptor<FSUser> captor = ArgumentCaptor.forClass(FSUser.class);
      verify(editableProvider).addUser(captor.capture());
      assertTrue(List.of(captor.getValue().getGroups()).contains("group1"),
         "caller holds ADMIN on group1 (checked via its org-scoped key) but it was "
         + "dropped because createUser checked the bare name instead");
   }

   // ── theme (Bug #76638) ────────────────────────────────────────────────────
   //
   // theme was validated, echoed in preview, and threaded into the SecurityUser/Group/Role
   // request DTO, but createUser/createGroup/createRole never read request.getTheme() at all,
   // and updateUser/updateGroup/updateRole captured it into the Edit*PaneModel passed to
   // identityService.setIdentity(...) but the value was never read back out anywhere -- the
   // rename-only themeService.updateTheme(oldName, newName, fn) call that followed setIdentity()
   // never received the new theme value. Fixed by IdentityThemeService.assignTheme(oldId, id,
   // ntheme, fn), which both keeps the reverse-index membership in sync on rename (like the old
   // updateTheme) and adds the identity to the theme named by ntheme.

   @Test
   void createUser_withTheme_assignsIdentityToMatchingCustomTheme() throws Exception {
      stubCommonCreateGates();
      when(editableProvider.getOrganization("org1")).thenReturn(new FSOrganization("org1"));
      CustomTheme theme = new CustomTheme();
      theme.setId("theme-1");
      when(customThemesManager.getCustomThemes()).thenReturn(new HashSet<>(Set.of(theme)));

      SecurityUser request = new SecurityUser();
      request.setIdentityID(new IdentityID("newuser1", "org1"));
      request.setPassword("Str0ng!Passw0rd");
      request.setTheme("theme-1");

      service.createUser(request, null, principal);

      assertTrue(theme.getUsers().contains("newuser1"),
         "Bug #76638: theme supplied on create must actually be persisted as CustomTheme "
         + "membership, not just echoed in preview");
   }

   @Test
   void createGroup_withTheme_assignsIdentityToMatchingCustomTheme() throws Exception {
      stubCommonCreateGates();
      CustomTheme theme = new CustomTheme();
      theme.setId("theme-1");
      when(customThemesManager.getCustomThemes()).thenReturn(new HashSet<>(Set.of(theme)));

      SecurityGroup request = new SecurityGroup();
      request.setIdentityID(new IdentityID("newgroup1", "org1"));
      request.setOrgID("org1");
      request.setTheme("theme-1");

      service.createGroup(request, null, principal);

      assertTrue(theme.getGroups().contains("newgroup1"));
      assertFalse(theme.getUsers().contains("newgroup1"),
         "must land in the theme's groups list, not its users list -- the pre-fix code called "
         + "the rename-only helper with CustomTheme::getUsers on a group create");
   }

   // Round-2 review finding (05-review-r1.md): getGroupModel (the read path behind getGroup(),
   // the group-axis analog of get_identity_user) looked up an assigned theme in
   // CustomTheme::getRoles instead of CustomTheme::getGroups -- so even after the write side
   // above correctly added the group to the theme's groups list, reading the group back would
   // report theme:null almost every time. Goes through the actual getGroup() read path (not
   // just asserting against the mocked CustomThemesManager's list contents directly) so a
   // regression here would actually be caught.
   @Test
   void createGroup_withTheme_thenGetGroup_readsBackTheAssignedTheme() throws Exception {
      stubCommonCreateGates();
      CustomTheme theme = new CustomTheme();
      theme.setId("theme-1");
      when(customThemesManager.getCustomThemes()).thenReturn(new HashSet<>(Set.of(theme)));

      IdentityID groupId = new IdentityID("newgroup1", "org1");
      SecurityGroup request = new SecurityGroup();
      request.setIdentityID(groupId);
      request.setOrgID("org1");
      request.setTheme("theme-1");

      service.createGroup(request, null, principal);

      when(securityProvider.getGroup(groupId)).thenReturn(new FSGroup(groupId));
      when(identityService.getIdentityInfo(groupId, Identity.GROUP, securityProvider))
         .thenReturn(new IdentityInfo());

      SecurityGroup result = service.getGroup(groupId, principal);

      assertEquals("theme-1", result.getTheme(),
         "Bug #76638 (review round 2): a theme correctly written to CustomTheme.getGroups() "
         + "on create must also be readable back via getGroup() -- getGroupModel was looking in "
         + "CustomTheme::getRoles instead of CustomTheme::getGroups");
   }

   @Test
   void createRole_withTheme_assignsIdentityToMatchingCustomTheme() throws Exception {
      stubCommonCreateGates();
      CustomTheme theme = new CustomTheme();
      theme.setId("theme-1");
      when(customThemesManager.getCustomThemes()).thenReturn(new HashSet<>(Set.of(theme)));

      SecurityRole request = new SecurityRole();
      request.setIdentityID(new IdentityID("neworgrole1", "org1"));
      request.setTheme("theme-1");

      service.createRole(request, null, principal);

      assertTrue(theme.getRoles().contains("neworgrole1"));
      assertFalse(theme.getUsers().contains("neworgrole1"),
         "must land in the theme's roles list, not its users list -- the pre-fix code called "
         + "the rename-only helper with CustomTheme::getUsers on a role create");
   }

   @Test
   void updateUser_withTheme_assignsIdentityToMatchingCustomTheme() throws Exception {
      IdentityID userId = new IdentityID("orgadmin1", "org1");
      FSUser oldUser = new FSUser(userId);
      when(securityProvider.getUser(userId)).thenReturn(oldUser);
      when(securityProvider.checkPermission(principal, ResourceType.SECURITY_USER,
                                            userId.convertToKey(), ResourceAction.ADMIN))
         .thenReturn(true);
      when(editableProvider.getUser(userId)).thenReturn(oldUser);
      when(orgManager.isSiteAdmin(principal)).thenReturn(false);
      CustomTheme theme = new CustomTheme();
      theme.setId("theme-1");
      when(customThemesManager.getCustomThemes()).thenReturn(new HashSet<>(Set.of(theme)));

      SecurityUser request = new SecurityUser();
      request.setIdentityID(userId);
      request.setTheme("theme-1");

      service.updateUser(userId, request, principal);

      assertTrue(theme.getUsers().contains("orgadmin1"),
         "Bug #76638: theme supplied on update must actually be persisted, not just captured "
         + "into EditUserPaneModel and dropped");
   }

   @Test
   void updateGroup_withTheme_assignsIdentityToMatchingCustomTheme() throws Exception {
      IdentityID groupId = new IdentityID("orggroup1", "org1");
      FSGroup oldGroup = new FSGroup(groupId);
      when(securityProvider.getGroup(groupId)).thenReturn(oldGroup);
      when(securityProvider.checkPermission(principal, ResourceType.SECURITY_GROUP,
                                            groupId.convertToKey(), ResourceAction.ADMIN))
         .thenReturn(true);
      when(editableProvider.getGroup(groupId)).thenReturn(oldGroup);
      when(orgManager.isSiteAdmin(principal)).thenReturn(false);
      CustomTheme theme = new CustomTheme();
      theme.setId("theme-1");
      when(customThemesManager.getCustomThemes()).thenReturn(new HashSet<>(Set.of(theme)));

      SecurityGroup request = new SecurityGroup();
      request.setIdentityID(groupId);
      request.setTheme("theme-1");

      service.updateGroup(groupId, request, principal);

      assertTrue(theme.getGroups().contains("orggroup1"));
   }

   @Test
   void updateRole_withTheme_assignsIdentityToMatchingCustomTheme() throws Exception {
      IdentityID roleId = new IdentityID("orgrole1", "org1");
      FSRole oldRole = new FSRole(roleId);
      when(securityProvider.getRole(roleId)).thenReturn(oldRole);
      when(securityProvider.checkPermission(principal, ResourceType.SECURITY_ROLE,
                                            roleId.convertToKey(), ResourceAction.ADMIN))
         .thenReturn(true);
      when(editableProvider.getRole(roleId)).thenReturn(oldRole);
      when(identityService.getIdentityInfo(roleId, Identity.ROLE, editableProvider))
         .thenReturn(new IdentityInfo());
      when(orgManager.isSiteAdmin(principal)).thenReturn(false);
      CustomTheme theme = new CustomTheme();
      theme.setId("theme-1");
      when(customThemesManager.getCustomThemes()).thenReturn(new HashSet<>(Set.of(theme)));

      SecurityRole request = new SecurityRole();
      request.setIdentityID(roleId);
      request.setTheme("theme-1");

      service.updateRole(roleId, request, principal);

      assertTrue(theme.getRoles().contains("orgrole1"));
   }

   // ── organization theme (Bug #76671) ──────────────────────────────────────
   //
   // createOrganization's only membership-registration call used the rename-only
   // themeService.updateTheme(null, name, CustomTheme::getUsers) -- wrong helper (whose
   // add-branch structurally cannot fire for a brand-new identity), wrong getter (getUsers
   // instead of getOrganizations), and never even passed request.getTheme() as an argument.
   // Fixed to mirror createUser/createGroup/createRole's assignTheme(...) pattern.

   @Test
   void createOrganization_withTheme_assignsIdentityToMatchingCustomTheme() throws Exception {
      when(orgManager.isSiteAdmin(principal)).thenReturn(true);
      CustomTheme theme = new CustomTheme();
      theme.setId("theme-1");
      when(customThemesManager.getCustomThemes()).thenReturn(new HashSet<>(Set.of(theme)));

      // id deliberately differs from name -- round 1's fixture (id == name) masked the
      // id-vs-name key choice entirely (05-review-r1.md, Finding 1's test-coverage note)
      SecurityOrganization request = new SecurityOrganization();
      request.setId("neworg-id");
      request.setName("New Organization");
      request.setTheme("theme-1");

      service.createOrganization(request, null, principal);

      assertTrue(theme.getOrganizations().contains("neworg-id"),
         "Bug #76671: theme supplied on create must actually be persisted as CustomTheme "
         + "membership, keyed by org id (not display name) -- CustomThemesImpl.getUserTheme, "
         + "the real runtime consumer, checks org id membership despite its misleadingly-named "
         + "local variable, and every other production writer of this list (ThemeService's "
         + "\"Default for This Organization\" toggle, IdentityService.updateCustomThemeOrganization) "
         + "keys by id too");
      assertFalse(theme.getOrganizations().contains("New Organization"),
         "must not also write a name-keyed entry alongside the id-keyed one");

      // Bug #76671 finding 1 (05-review-r1.md): getOrganizations() list membership alone is
      // never consulted by CustomThemesImpl.getUserTheme() unless the org's separate
      // orgSelectedTheme pointer is also set -- list membership without the pointer is a
      // no-op at runtime even though the EM API reads it back correctly.
      verify(customThemesManager).setOrgSelectedTheme("theme-1", "neworg-id");
   }

   // createOrganization's currentTheme filter had a missing-parens operator-precedence bug
   // (`(A && B) || C` instead of the evident `(A && B) || (!B && C)` intent) that forced
   // evaluation of `t.getOrgID().equals(name)` on any pre-existing global (orgID == null) theme
   // whose name didn't match the request -- including the common case of no theme requested at
   // all -- throwing an NPE on essentially every createOrganization call in a system with any
   // global theme. Also promoted from the diagnosis/refutation's "additional observation": the
   // filter matched by CustomTheme.getName() instead of getId(), inconsistent with every other
   // theme lookup in the codebase (IdentityThemeService.getTheme(), assignTheme, updateTheme all
   // key by id).
   @Test
   void createOrganization_mismatchedGlobalTheme_doesNotThrowNPE() throws Exception {
      when(orgManager.isSiteAdmin(principal)).thenReturn(true);
      CustomTheme globalTheme = new CustomTheme();
      globalTheme.setId("unrelated-theme");
      globalTheme.setOrgID(null);
      when(customThemesManager.getCustomThemes()).thenReturn(new HashSet<>(Set.of(globalTheme)));

      SecurityOrganization request = new SecurityOrganization();
      request.setId("neworg2");
      request.setName("neworg2");
      // deliberately no theme requested, matching the reporter's most common trigger case

      assertDoesNotThrow(() -> service.createOrganization(request, null, principal),
         "Bug #76671 finding 2a: an unrelated pre-existing global theme must not NPE a "
         + "createOrganization call that didn't even request a theme");
   }

   // Round 2 (06-fix-r2.md), responding to 05-review-r1.md's Finding 1 / Finding 3:
   //
   // - Finding 1 traced the real runtime consumer, CustomThemesImpl.getUserTheme(), and found
   //   its getOrganizations() membership check is actually keyed by org id, not display name,
   //   despite a misleadingly-named local variable ("orgName") that is in fact assigned straight
   //   from provider.getOrganizationIDs() (ids). Every other production writer of this list
   //   (ThemeService's "Default for This Organization" toggle,
   //   IdentityService.updateCustomThemeOrganization) also keys by id.
   // - Finding 3 found that updateOrganization's round-1 assignTheme(...) call duplicated a
   //   write that IdentityService.setIdentity() -> setOrganizationInfo() ->
   //   updateCustomThemeOrganization() already performs correctly (id-keyed list entry +
   //   orgSelectedTheme pointer) on every updateOrganization call, under a second, wrongly
   //   name-keyed entry. The fix removes updateOrganization's own write entirely and instead
   //   fixes the read side: getOrganizationModel()/IdentityThemeService.getTheme() now looks up
   //   by identityID.orgID, matching what updateCustomThemeOrganization actually writes.
   //
   // updateOrganization's own theme handling is now verified negatively -- it must not perform
   // an independent CustomTheme.getOrganizations() write, since that's delegated wholesale to
   // identityService.setIdentity() (mocked in this test class; covered for real in
   // IdentityServiceTest). The read side is verified by seeding the mocked CustomThemesManager
   // with exactly what the real write path produces (an id-keyed entry) and confirming
   // getOrganization() reads it back.
   @Test
   void updateOrganization_withTheme_doesNotIndependentlyWriteThemeMembership() throws Exception {
      String orgId = "org-xyz";
      String orgName = "Xyz Organization";
      FSOrganization oldOrganization = new FSOrganization(orgId);
      oldOrganization.setName(orgName);
      when(securityProvider.getOrganization(orgId)).thenReturn(oldOrganization);
      when(securityProvider.checkPermission(principal, ResourceType.SECURITY_ORGANIZATION,
                                            orgId, ResourceAction.ADMIN))
         .thenReturn(true);
      when(editableProvider.getOrganization(orgId)).thenReturn(oldOrganization);
      when(editableProvider.getOrgIdFromName(orgName)).thenReturn(orgId);
      when(orgManager.isSiteAdmin(principal)).thenReturn(true);
      CustomTheme theme = new CustomTheme();
      theme.setId("theme-1");
      theme.setOrganizations(new java.util.ArrayList<>());
      when(customThemesManager.getCustomThemes()).thenReturn(new HashSet<>(Set.of(theme)));

      SecurityOrganization request = new SecurityOrganization();
      request.setId(orgId);
      request.setName(orgName);
      request.setTheme("theme-1");

      // identityService is mocked in this test class, so its real setIdentity() ->
      // updateCustomThemeOrganization() body does not run here -- if updateOrganization still
      // performed its own (round-1) list write, this mock would be the only writer and the
      // duplicate would show up below.
      service.updateOrganization(orgId, request, principal);

      assertTrue(theme.getOrganizations().isEmpty(),
         "Bug #76671 finding 3: updateOrganization must not independently mutate "
         + "CustomTheme.getOrganizations() -- IdentityService.setIdentity() -> "
         + "setOrganizationInfo() -> updateCustomThemeOrganization() already does this correctly "
         + "(id-keyed entry + orgSelectedTheme pointer) on every call; a second write here "
         + "duplicated the entry under the organization's display name instead of its id");
      verify(customThemesManager, never()).setOrgSelectedTheme(anyString(), anyString());
   }

   // ────────── createOrganization locale ──────────
   //
   // createOrganization resolved its locale through LocaleService.getLocale(), a different source
   // of truth from every other locale path in this API: LocaleService's map is built only from the
   // SreeEnv property "locale.available", while createUser/updateUser/updateOrganization validate
   // against the data-space file locale.properties. "locale.available" is unset by default, so the
   // map is empty and the lookup returned null for *every* input -- silently dropping even a
   // perfectly valid code that updateOrganization accepts. Fixed to use validateLocale(), matching
   // createUser: store a known code verbatim, throw loudly for anything else.
   //
   // LocaleService itself was deliberately left alone: its three other callers
   // (AuthenticationService's login and updateLocale paths, SUtil.getSessionRecord) all treat a
   // null return as the normal "no locale configured" outcome, so making it throw would raise on
   // essentially every login on a default deployment.

   @Test
   void createOrganization_validLocaleCode_persistsVerbatim() throws Exception {
      when(orgManager.isSiteAdmin(principal)).thenReturn(true);
      Properties localeProperties = new Properties();
      localeProperties.setProperty("en_US", "English(America)");
      sUtilStatic.when(SUtil::loadLocaleProperties).thenReturn(localeProperties);

      SecurityOrganization request = new SecurityOrganization();
      request.setId("neworg-id");
      request.setName("New Organization");
      request.setLocale("en_US");

      service.createOrganization(request, null, principal);

      ArgumentCaptor<FSOrganization> captor = ArgumentCaptor.forClass(FSOrganization.class);
      verify(editableProvider).addOrganization(captor.capture());
      // Before the fix this resolved through LocaleService, whose map is empty unless the
      // "locale.available" SreeEnv property is set -- so even this valid code persisted as null.
      assertEquals("en_US", captor.getValue().getLocale());
   }

   @Test
   void createOrganization_copyFromOrg_unrecognizedLocale_throwsOnThatPathToo() throws Exception {
      when(orgManager.isSiteAdmin(principal)).thenReturn(true);
      Properties localeProperties = new Properties();
      localeProperties.setProperty("en_US", "English(America)");
      sUtilStatic.when(SUtil::loadLocaleProperties).thenReturn(localeProperties);

      SecurityOrganization request = new SecurityOrganization();
      request.setId("neworg-id");
      request.setName("New Organization");
      request.setLocale("English(America)");

      // The copyFrom branch returns before the locale is ever used, so validation is hoisted above
      // it: an identical body must not be accepted here and rejected on the ordinary create path.
      assertThrows(InvalidResourceException.class,
                   () -> service.createOrganization(request, "source-org", principal));
      verify(userTreeService, never())
         .createOrganization(any(), any(), any(), any(), any(), any());
   }

   @Test
   void createOrganization_unrecognizedLocale_throwsInsteadOfSilentlyDroppingIt() throws Exception {
      when(orgManager.isSiteAdmin(principal)).thenReturn(true);
      Properties localeProperties = new Properties();
      localeProperties.setProperty("en_US", "English(America)");
      sUtilStatic.when(SUtil::loadLocaleProperties).thenReturn(localeProperties);

      SecurityOrganization request = new SecurityOrganization();
      request.setId("neworg-id");
      request.setName("New Organization");
      // A display label, not a code -- same rejected input shape as the createUser/updateUser
      // and updateOrganization cases above.
      request.setLocale("English(America)");

      assertThrows(InvalidResourceException.class,
                   () -> service.createOrganization(request, null, principal));
      verify(editableProvider, never()).addOrganization(any());
   }

   // ── createOrganization/updateOrganization/getOrganization properties (Bug #76715) ───────

   @Test
   void createOrganization_writesPropertiesIntoSreeEnv() throws Exception {
      when(orgManager.isSiteAdmin(principal)).thenReturn(true);

      SecurityOrganization request = new SecurityOrganization();
      request.setId("neworg-id");
      request.setName("New Organization");
      request.setProperties(List.of(PropertyModel.builder().name("custom.key").value("v").build()));

      service.createOrganization(request, null, principal);

      sreeEnvStatic.verify(() -> SreeEnv.setProperty("custom.key", "v", true));
   }

   @Test
   void updateOrganization_writesPropertiesAndClearsOmittedQuotaKeyForEmParity() throws Exception {
      String orgId = "org-xyz";
      String orgName = "Xyz Organization";
      FSOrganization oldOrganization = new FSOrganization(orgId);
      oldOrganization.setName(orgName);
      when(securityProvider.getOrganization(orgId)).thenReturn(oldOrganization);
      when(securityProvider.checkPermission(principal, ResourceType.SECURITY_ORGANIZATION,
                                            orgId, ResourceAction.ADMIN))
         .thenReturn(true);
      when(editableProvider.getOrganization(orgId)).thenReturn(oldOrganization);
      when(editableProvider.getOrgIdFromName(orgName)).thenReturn(orgId);
      when(orgManager.isSiteAdmin(principal)).thenReturn(true);
      // A quota key currently set, not mentioned in this update's properties list -- this general-
      // purpose method mirrors EM's own editOrganization clear-on-omission behavior for these 4
      // named keys (the identities admin-chat path protects against this instead, one layer up, in
      // IdentityMerge.mergeOrganization -- see IdentityMergeTest).
      sreeEnvStatic.when(() -> SreeEnv.getProperty("max.row.count", false, true)).thenReturn("1000");

      SecurityOrganization request = new SecurityOrganization();
      request.setId(orgId);
      request.setName(orgName);
      request.setProperties(List.of(PropertyModel.builder().name("other").value("y").build()));

      service.updateOrganization(orgId, request, principal);

      sreeEnvStatic.verify(() -> SreeEnv.setProperty("other", "y", true));
      sreeEnvStatic.verify(() -> SreeEnv.setProperty("max.row.count", null, true));
   }

   @Test
   void getOrganization_readsPropertiesBackFromSreeEnv() throws Exception {
      String orgId = "org-xyz";
      String orgName = "Xyz Organization";
      FSOrganization organization = new FSOrganization(orgId);
      organization.setName(orgName);
      when(securityProvider.getOrganization(orgId)).thenReturn(organization);
      when(securityProvider.checkPermission(principal, ResourceType.SECURITY_ORGANIZATION,
                                            orgId, ResourceAction.ADMIN))
         .thenReturn(true);
      when(identityService.getIdentityInfo(any(IdentityID.class), eq(Identity.ORGANIZATION),
                                           eq(securityProvider)))
         .thenReturn(new IdentityInfo());
      Properties raw = new Properties();
      raw.setProperty("inetsoft.org." + orgId.toLowerCase() + ".custom.key", "v");
      sreeEnvStatic.when(SreeEnv::getProperties).thenReturn(raw);
      sreeEnvStatic.when(() -> SreeEnv.getProperty("custom.key", false, true)).thenReturn("v");

      SecurityOrganization result = service.getOrganization(orgId, principal);

      assertEquals(1, result.getProperties().size());
      assertEquals("custom.key", result.getProperties().get(0).name());
      assertEquals("v", result.getProperties().get(0).value());
   }

   // ── getOrganization properties cross-org read scope (Bug #76798) ────────
   //
   // readOrganizationProperties re-resolved each already-found, prefix-stripped property name
   // through SreeEnv.getProperty(name, false, true) -> PropertiesEngine.useAvailableOrgProperty,
   // which derives "current org" from OrganizationManager.getCurrentOrgID() (the calling
   // principal's own org) instead of the orgId parameter actually being read -- so an admin
   // reading an organization other than their own got every property silently dropped, even
   // though the value was persisted correctly (confirmed by the reporter via the raw prefixed
   // key). The sibling write path, applyOrganizationProperties, already wraps its SreeEnv calls
   // in OrganizationManager.runInOrgScope(orgId, ...); the read path needed the same wrap.
   //
   // Both SreeEnv and OrganizationManager are fully static-mocked in this file, so the real
   // PropertiesEngine/OrganizationContextHolder internals never run here. This test models their
   // coupling directly: getCurrentOrgID() is backed by a mutable holder that only
   // OrganizationManager.runInOrgScope(orgId, ...) is allowed to swing to orgId for the scope's
   // duration (mirroring OrganizationContextHolder's real ThreadLocal push/finally-restore), and
   // the SreeEnv.getProperty stub only resolves the property when the simulated "current org"
   // matches orgId -- exactly the real coupling that dropped the property when the caller's own
   // org differed from the org being read.
   @Test
   void getOrganization_callerInDifferentOrg_stillReadsBackProperties() throws Exception {
      String orgId = "org-b";
      String[] currentOrg = { "org-a" };
      when(orgManager.getCurrentOrgID()).thenAnswer(inv -> currentOrg[0]);
      organizationManagerStatic.when(() -> OrganizationManager.runInOrgScope(anyString(), any()))
         .thenAnswer(inv -> {
            String scopedOrg = inv.getArgument(0);
            String previous = currentOrg[0];
            currentOrg[0] = scopedOrg;

            try {
               java.util.concurrent.Callable<?> callable = inv.getArgument(1);
               return callable.call();
            }
            finally {
               currentOrg[0] = previous;
            }
         });

      FSOrganization organization = new FSOrganization(orgId);
      organization.setName("Org B");
      when(securityProvider.getOrganization(orgId)).thenReturn(organization);
      when(securityProvider.checkPermission(principal, ResourceType.SECURITY_ORGANIZATION,
                                            orgId, ResourceAction.ADMIN))
         .thenReturn(true);
      when(identityService.getIdentityInfo(any(IdentityID.class), eq(Identity.ORGANIZATION),
                                           eq(securityProvider)))
         .thenReturn(new IdentityInfo());
      Properties raw = new Properties();
      raw.setProperty("inetsoft.org." + orgId.toLowerCase() + ".custom.key", "v");
      sreeEnvStatic.when(SreeEnv::getProperties).thenReturn(raw);
      sreeEnvStatic.when(() -> SreeEnv.getProperty(eq("custom.key"), eq(false), eq(true)))
         .thenAnswer(inv -> orgId.equals(currentOrg[0]) ? "v" : null);

      SecurityOrganization result = service.getOrganization(orgId, principal);

      assertEquals(1, result.getProperties().size(),
         "Bug #76798: readOrganizationProperties must read back properties for an org other "
         + "than the caller's own current org, not just the caller's own org");
      assertEquals("custom.key", result.getProperties().get(0).name());
      assertEquals("v", result.getProperties().get(0).value());
   }

   // ── updateOrganization locale (Bug #76678) ─────────────────────────────
   //
   // updateOrganization passed request.getLocale() (a code, per the Public API's documented
   // shape) straight into EditOrganizationPaneModel.locale() with no transform, same defect
   // shape as updateUser's already-fixed #76609. IdentityService.setIdentity() ->
   // setOrganizationInfo() reverse-looks-up that field expecting a label, so a code silently
   // resolved to nothing (null persisted), no exception. Fixed by translating the code to its
   // matching label via toLocaleLabel(), mirroring updateUser's own fix exactly.

   @Test
   void updateOrganization_validLocaleCode_translatesToLabelForSharedSetIdentityContract() throws Exception {
      String orgId = "org-xyz";
      String orgName = "Xyz Organization";
      FSOrganization oldOrganization = new FSOrganization(orgId);
      oldOrganization.setName(orgName);
      when(securityProvider.getOrganization(orgId)).thenReturn(oldOrganization);
      when(securityProvider.checkPermission(principal, ResourceType.SECURITY_ORGANIZATION,
                                            orgId, ResourceAction.ADMIN))
         .thenReturn(true);
      when(editableProvider.getOrganization(orgId)).thenReturn(oldOrganization);
      when(editableProvider.getOrgIdFromName(orgName)).thenReturn(orgId);
      when(orgManager.isSiteAdmin(principal)).thenReturn(true);
      Properties localeProperties = new Properties();
      localeProperties.setProperty("en_US", "English(America)");
      sUtilStatic.when(SUtil::loadLocaleProperties).thenReturn(localeProperties);

      SecurityOrganization request = new SecurityOrganization();
      request.setId(orgId);
      request.setName(orgName);
      request.setLocale("en_US");

      service.updateOrganization(orgId, request, principal);

      ArgumentCaptor<EditOrganizationPaneModel> captor = ArgumentCaptor.forClass(EditOrganizationPaneModel.class);
      verify(identityService).setIdentity(eq(oldOrganization), captor.capture(), eq(editableProvider), eq(principal));
      assertEquals("English(America)", captor.getValue().locale());
   }

   @Test
   void updateOrganization_unrecognizedLocale_throwsInsteadOfSilentlyDroppingIt() throws Exception {
      String orgId = "org-xyz";
      String orgName = "Xyz Organization";
      FSOrganization oldOrganization = new FSOrganization(orgId);
      oldOrganization.setName(orgName);
      when(securityProvider.getOrganization(orgId)).thenReturn(oldOrganization);
      when(securityProvider.checkPermission(principal, ResourceType.SECURITY_ORGANIZATION,
                                            orgId, ResourceAction.ADMIN))
         .thenReturn(true);
      when(editableProvider.getOrganization(orgId)).thenReturn(oldOrganization);
      when(editableProvider.getOrgIdFromName(orgName)).thenReturn(orgId);
      when(orgManager.isSiteAdmin(principal)).thenReturn(true);
      Properties localeProperties = new Properties();
      localeProperties.setProperty("en_US", "English(America)");
      sUtilStatic.when(SUtil::loadLocaleProperties).thenReturn(localeProperties);

      SecurityOrganization request = new SecurityOrganization();
      request.setId(orgId);
      request.setName(orgName);
      request.setLocale("English(America)");

      assertThrows(InvalidResourceException.class,
                   () -> service.updateOrganization(orgId, request, principal));
      verify(identityService, never()).setIdentity(any(), any(), any(), any());
   }

   @Test
   void getOrganization_themeMembershipKeyedById_readsBackTheAssignedTheme() throws Exception {
      String orgId = "org-xyz";
      String orgName = "Xyz Organization";
      FSOrganization organization = new FSOrganization(orgId);
      organization.setName(orgName);
      when(securityProvider.getOrganization(orgId)).thenReturn(organization);
      when(securityProvider.checkPermission(principal, ResourceType.SECURITY_ORGANIZATION,
                                            orgId, ResourceAction.ADMIN))
         .thenReturn(true);
      when(identityService.getIdentityInfo(any(IdentityID.class), eq(Identity.ORGANIZATION),
                                           eq(securityProvider)))
         .thenReturn(new IdentityInfo());
      CustomTheme theme = new CustomTheme();
      theme.setId("theme-1");
      // exactly what the real, already-correct IdentityService.updateCustomThemeOrganization()
      // writes: an id-keyed entry, not a name-keyed one
      theme.setOrganizations(List.of(orgId));
      when(customThemesManager.getCustomThemes()).thenReturn(new HashSet<>(Set.of(theme)));

      SecurityOrganization result = service.getOrganization(orgId, principal);

      assertEquals("theme-1", result.getTheme(),
         "Bug #76671 finding 3: getOrganizationModel must read CustomTheme.getOrganizations() "
         + "membership keyed by org id, matching what IdentityService."
         + "updateCustomThemeOrganization actually writes -- reading by display name (the "
         + "pre-fix behavior) could never see this entry whenever an org's id differs from its "
         + "name");
   }

   // ── changeUserPassword ──────────────────────────────────────────────────
   //
   // The public API's changeUserPassword bypassed IdentityService.validatePasswordStrength()
   // entirely -- unlike createUser (line 255) and every other password-setting entry point
   // (ChangePasswordController, ChangePasswordDialogController, UserTreeService) -- so a caller
   // with SECURITY_USER/ADMIN permission on a target user could set a password that violates the
   // configured strength policy (e.g. "success123": no uppercase, no special character) and the
   // target user could then log in with it.

   @Test
   void changeUserPassword_weakPassword_rejectedBeforeApplied() throws Exception {
      IdentityID userId = new IdentityID("user0", "org1");
      FSUser user = new FSUser(userId);
      when(securityProvider.checkPermission(principal, ResourceType.SECURITY_USER,
                                            userId.convertToKey(), ResourceAction.ADMIN))
         .thenReturn(true);
      when(editableProvider.getUser(userId)).thenReturn(user);

      assertThrows(inetsoft.util.MessageException.class,
                   () -> service.changeUserPassword(userId, "success123", principal));

      verify(editableProvider, never()).changePassword(any(), any());
      verify(editableProvider, never()).addUser(any());
   }

   @Test
   void changeUserPassword_strongPassword_isApplied() throws Exception {
      IdentityID userId = new IdentityID("user0", "org1");
      FSUser user = new FSUser(userId);
      when(securityProvider.checkPermission(principal, ResourceType.SECURITY_USER,
                                            userId.convertToKey(), ResourceAction.ADMIN))
         .thenReturn(true);
      when(editableProvider.getUser(userId)).thenReturn(user);

      service.changeUserPassword(userId, "Str0ng!Passw0rd", principal);

      verify(editableProvider).changePassword(userId, "Str0ng!Passw0rd");
   }

   // ── getRole ─────────────────────────────────────────────────────────────
   //
   // Regression coverage for Bug #75728 (community commit 8a8a9557d): getRole() for a
   // non-existent role silently returned 200 with a fabricated non-empty Role instead of 404.
   // The real root cause was in VirtualAuthenticationProvider.getRole() (a different provider
   // implementation) fabricating a non-null Role for any name -- SecurityService.getRole()
   // itself already has the correct null-check below and was never changed by the fix. This test
   // pins that existing null-check contract as depth-of-defense: it simulates a provider that
   // correctly returns null for an unknown role (independent of which concrete provider), so if
   // this null-check is ever accidentally removed, this test goes red immediately.
   @Test
   void getRole_nonExistentRole_throwsMissingResourceException() throws Exception {
      IdentityID role = new IdentityID("Missing Role", "org1");
      when(securityProvider.checkPermission(principal, ResourceType.SECURITY_ROLE,
                                            role.convertToKey(), ResourceAction.ADMIN))
         .thenReturn(true);
      when(securityProvider.getRole(role)).thenReturn(null);

      assertThrows(inetsoft.web.security.auth.MissingResourceException.class,
                   () -> service.getRole(role, principal));
   }

   @Test
   void getRole_existingRole_returnsPopulatedModel() throws Exception {
      IdentityID role = new IdentityID("Analyst", "org1");
      when(securityProvider.checkPermission(principal, ResourceType.SECURITY_ROLE,
                                            role.convertToKey(), ResourceAction.ADMIN))
         .thenReturn(true);
      FSRole existingRole = new FSRole(role, "Read-only analyst role");
      when(securityProvider.getRole(role)).thenReturn(existingRole);
      // doReturn (not when/thenReturn) -- authenticationProvider uses CALLS_REAL_METHODS, and
      // when(...) would execute the real getRoleMembers() default body first, which NPEs on the
      // unstubbed getGroups()/getUsers() calls it makes internally.
      doReturn(new Identity[0]).when(authenticationProvider).getRoleMembers(role);
      IdentityInfo info = new IdentityInfo(existingRole, authenticationProvider);
      when(identityService.getIdentityInfo(role, Identity.ROLE, securityProvider)).thenReturn(info);
      when(identityService.getPermission(eq(role), eq(ResourceType.SECURITY_ROLE), any(), eq(principal)))
         .thenReturn(List.of());

      SecurityRole result = service.getRole(role, principal);

      assertEquals("Read-only analyst role", result.getDescription());
      assertEquals(role, result.getIdentityID());
   }

   // ── deleteOrganization ──────────────────────────────────────────────────
   //
   // Regression coverage for Bug #76357: deleteOrganization had no default-org/self-org
   // refusal, so any caller with SECURITY_ORGANIZATION/ADMIN permission (including the
   // unguarded REST endpoint and LocalSecurityClientService passthroughs) could delete
   // the default or self organization outright.

   // Stubs the full path to a successful deletion (permission granted, organization found,
   // deleteIdentity's own orphaned-admin checks satisfied) so that the refusal tests below
   // are discriminating: without the default/self-org guard, deleteOrganization would reach
   // and complete deleteIdentity's success path rather than fail for an unrelated reason
   // (e.g. systemAdminService's default mock answer of false for hasOrgAdminAfterDelete).
   private void stubDeletableOrganization(String organizationid) throws Exception {
      when(securityProvider.checkPermission(principal, ResourceType.SECURITY_ORGANIZATION,
                                            organizationid, ResourceAction.ADMIN))
         .thenReturn(true);
      when(editableProvider.getOrganization(organizationid)).thenReturn(new FSOrganization(organizationid));
      when(editableProvider.getOrgNameFromID(organizationid)).thenReturn(organizationid);
      when(systemAdminService.hasOrgAdminAfterDelete(any())).thenReturn(true);
      when(systemAdminService.hasSystemAdminAfterDelete(any())).thenReturn(true);
      when(identityService.deleteIdentities(any(), any(), eq(principal))).thenReturn(List.of());
   }

   @Test
   void deleteOrganization_defaultOrg_refused() throws Exception {
      String orgId = Organization.getDefaultOrganizationID();
      stubDeletableOrganization(orgId);

      Exception ex = assertThrows(Exception.class, () -> service.deleteOrganization(orgId, principal));
      assertTrue(ex.getMessage().contains("default organization"));

      verify(identityService, never()).deleteIdentities(any(), any(), any());
   }

   @Test
   void deleteOrganization_selfOrg_refused() throws Exception {
      String orgId = Organization.getSelfOrganizationID();
      stubDeletableOrganization(orgId);

      Exception ex = assertThrows(Exception.class, () -> service.deleteOrganization(orgId, principal));
      assertTrue(ex.getMessage().contains("self organization"));

      verify(identityService, never()).deleteIdentities(any(), any(), any());
   }

   @Test
   void deleteOrganization_defaultOrg_differentCase_refused() throws Exception {
      // Regression test for the case-sensitivity compounding factor: under
      // security.user.caseSensitive=false, DatabaseAuthenticationProvider.getOrganization
      // resolves a differently-cased id to the same canonical protected organization, so
      // the guard must use equalsIgnoreCase, not equals.
      String orgId = "HOST-ORG";
      stubDeletableOrganization(orgId);

      Exception ex = assertThrows(Exception.class, () -> service.deleteOrganization(orgId, principal));
      assertTrue(ex.getMessage().contains("default organization"));

      verify(identityService, never()).deleteIdentities(any(), any(), any());
   }

   @Test
   void deleteOrganization_nonProtectedOrg_stillDeletes() throws Exception {
      String orgId = "org1";
      stubDeletableOrganization(orgId);

      service.deleteOrganization(orgId, principal);

      verify(identityService).deleteIdentities(any(), any(), eq(principal));
   }

   // ── deleteIdentity's PreMutationRefusalException classification (bug 76444) ─────────────
   //
   // IdentityChangesetApplyService relies on this classification to decide whether a delete
   // failure is safe to re-verify as "nothing was mutated" -- it must only trust
   // PreMutationRefusalException, never a plain Exception, so this boundary needs its own
   // direct coverage independent of that caller's mocks.

   @Test
   void deleteGroup_hasMembers_throwsPreMutationRefusalException() throws Exception {
      IdentityID groupId = new IdentityID("analysts", "host-org");
      FSGroup existing = new FSGroup(groupId);
      when(securityProvider.checkPermission(principal, ResourceType.SECURITY_GROUP,
                                            groupId.convertToKey(), ResourceAction.ADMIN))
         .thenReturn(true);
      when(editableProvider.getGroup(groupId)).thenReturn(existing);
      when(systemAdminService.hasOrgAdminAfterDelete(any())).thenReturn(true);
      when(systemAdminService.hasSystemAdminAfterDelete(any())).thenReturn(true);
      String delgroupMessage = Catalog.getCatalog(principal).getString("em.security.delgroup");
      when(identityService.deleteIdentities(any(), any(), eq(principal)))
         .thenReturn(List.of(delgroupMessage));

      SecurityService.PreMutationRefusalException ex = assertThrows(
         SecurityService.PreMutationRefusalException.class,
         () -> service.deleteGroup(groupId, principal));

      assertEquals(delgroupMessage, ex.getMessage());
   }

   @Test
   void deleteGroup_noOrgAdminWouldRemain_throwsPreMutationRefusalException() throws Exception {
      IdentityID groupId = new IdentityID("analysts", "host-org");
      FSGroup existing = new FSGroup(groupId);
      when(securityProvider.checkPermission(principal, ResourceType.SECURITY_GROUP,
                                            groupId.convertToKey(), ResourceAction.ADMIN))
         .thenReturn(true);
      when(editableProvider.getGroup(groupId)).thenReturn(existing);
      when(systemAdminService.hasOrgAdminAfterDelete(any())).thenReturn(false);

      assertThrows(SecurityService.PreMutationRefusalException.class,
         () -> service.deleteGroup(groupId, principal));

      verify(identityService, never()).deleteIdentities(any(), any(), any());
   }

   @Test
   void deleteGroup_ambiguousSyncFailure_throwsPlainExceptionNotPreMutationRefusal() throws Exception {
      // The message IdentityService.deleteIdentities' own catch-and-report-as-warning fallback
      // produces when syncIdentity fails partway through (community/core IdentityService.java,
      // AFTER dashboard/schedule cleanup and the cluster message, not before) -- must NOT be
      // classified as a pre-mutation refusal, unlike the delgroup/delself/no-admin messages.
      IdentityID groupId = new IdentityID("analysts", "host-org");
      FSGroup existing = new FSGroup(groupId);
      when(securityProvider.checkPermission(principal, ResourceType.SECURITY_GROUP,
                                            groupId.convertToKey(), ResourceAction.ADMIN))
         .thenReturn(true);
      when(editableProvider.getGroup(groupId)).thenReturn(existing);
      when(systemAdminService.hasOrgAdminAfterDelete(any())).thenReturn(true);
      when(systemAdminService.hasSystemAdminAfterDelete(any())).thenReturn(true);
      when(identityService.deleteIdentities(any(), any(), eq(principal)))
         .thenReturn(List.of("Failed to delete identity " + groupId + "."));

      Exception ex = assertThrows(Exception.class, () -> service.deleteGroup(groupId, principal));

      assertFalse(ex instanceof SecurityService.PreMutationRefusalException);
   }

   // ── identityType cross-check (Bug #76352, PM-002) ───────────────────────
   //
   // "Everyone" is a built-in ROLE, but identityType only ever validated enum membership
   // (USER/GROUP/ROLE/ORGANIZATION), never the identity's actual kind -- so
   // identityType=GROUP, identityId=Everyone sailed through and created a permission grant
   // keyed by (Everyone, GROUP), silently wrong. checkIdType(String, IdentityID) now
   // cross-checks the claimed type against the identity's real kind via the same
   // getUser/getGroup/getRole/getOrganization lookups the rest of the provider API uses, and
   // throws IllegalArgumentException (mapped to 400 by AdminPermissionController's existing
   // handleIllegalArgument -- no new exception handler needed). The pre-existing "Invalid
   // identity type" enum-membership failure, and getPermission/setPermission's unrelated
   // checkPermissionAccess denial ("Permission denied to access permission"), both keep
   // throwing UnauthorizedAccessException exactly as before -- only the new kind-mismatch
   // branch uses IllegalArgumentException.

   private static final IdentityID EVERYONE_ROLE = new IdentityID("Everyone", "org1");

   @Test
   void getPermissionGrant_claimedGroupButActuallyRole_throwsIllegalArgumentException() {
      when(securityProvider.getRole(EVERYONE_ROLE)).thenReturn(new FSRole(EVERYONE_ROLE));

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.getPermissionGrant("Examples/Census", "ASSET", "Everyone", "GROUP",
                                          principal));

      assertTrue(ex.getMessage().contains("GROUP"));
      assertTrue(ex.getMessage().contains("ROLE"));
      verifyNoInteractions(actionPermissionService);
   }

   @Test
   void createPermissionGrant_claimedGroupButActuallyRole_throwsIllegalArgumentException() {
      when(securityProvider.getRole(EVERYONE_ROLE)).thenReturn(new FSRole(EVERYONE_ROLE));

      PermissionGrant grant = new PermissionGrant();
      grant.setIdentityID(EVERYONE_ROLE);
      grant.setType("GROUP");
      grant.setActions(List.of("READ"));

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.createPermissionGrant("Examples/Census", "ASSET", grant, principal));

      assertTrue(ex.getMessage().contains("GROUP"));
      assertTrue(ex.getMessage().contains("ROLE"));
      verify(securityProvider, never())
         .setPermission(any(ResourceType.class), any(String.class), any(Permission.class));
   }

   @Test
   void updatePermissionGrant_oldIdentityClaimedGroupButActuallyRole_throwsIllegalArgumentException() {
      // Isolates the OLD-identity checkIdType call -- the 5th call site missed by the first
      // refutation round -- by making the new identity correctly typed so a bug that only
      // covers the new-identity check would let this slip through.
      when(securityProvider.getRole(EVERYONE_ROLE)).thenReturn(new FSRole(EVERYONE_ROLE));

      IdentityID correctUser = new IdentityID("alice", "org1");
      PermissionGrant newGrant = new PermissionGrant();
      newGrant.setIdentityID(correctUser);
      newGrant.setType("USER");
      newGrant.setActions(List.of("READ"));

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.updatePermissionGrant("Examples/Census", "ASSET",
            EVERYONE_ROLE.convertToKey(), "GROUP", newGrant, principal));

      assertTrue(ex.getMessage().contains("GROUP"));
      assertTrue(ex.getMessage().contains("ROLE"));
      verifyNoInteractions(actionPermissionService);
   }

   @Test
   void updatePermissionGrant_newIdentityClaimedGroupButActuallyRole_throwsIllegalArgumentException() {
      // Isolates the NEW-identity checkIdType call by making the old identity correctly typed.
      IdentityID correctUser = new IdentityID("alice", "org1");
      when(securityProvider.getUser(correctUser)).thenReturn(new FSUser(correctUser));
      when(securityProvider.getRole(EVERYONE_ROLE)).thenReturn(new FSRole(EVERYONE_ROLE));

      PermissionGrant newGrant = new PermissionGrant();
      newGrant.setIdentityID(EVERYONE_ROLE);
      newGrant.setType("GROUP");
      newGrant.setActions(List.of("READ"));

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.updatePermissionGrant("Examples/Census", "ASSET",
            correctUser.convertToKey(), "USER", newGrant, principal));

      assertTrue(ex.getMessage().contains("GROUP"));
      assertTrue(ex.getMessage().contains("ROLE"));
      verifyNoInteractions(actionPermissionService);
   }

   @Test
   void deletePermissionGrant_claimedGroupButActuallyRole_throwsIllegalArgumentException() {
      when(securityProvider.getRole(EVERYONE_ROLE)).thenReturn(new FSRole(EVERYONE_ROLE));

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.deletePermissionGrant("Examples/Census", "ASSET",
            EVERYONE_ROLE.convertToKey(), "GROUP", principal));

      assertTrue(ex.getMessage().contains("GROUP"));
      assertTrue(ex.getMessage().contains("ROLE"));
      verifyNoInteractions(actionPermissionService);
   }

   @Test
   void createPermissionGrant_correctlyTypedGroup_stillSucceeds() throws Exception {
      // Guards the fix against being too strict: a resolvable, correctly-typed identity must
      // still succeed normally.
      IdentityID financeGroup = new IdentityID("Finance", "org1");
      when(securityProvider.getGroup(financeGroup)).thenReturn(new FSGroup(financeGroup));
      stubPermissionAccessGranted(ResourceType.ASSET, "Examples/Census");

      PermissionGrant grant = new PermissionGrant();
      grant.setIdentityID(financeGroup);
      grant.setType("GROUP");
      grant.setActions(List.of("READ"));

      service.createPermissionGrant("Examples/Census", "ASSET", grant, principal);

      verify(securityProvider).setPermission(eq(ResourceType.ASSET), eq("Examples/Census"), any());
   }

   @Test
   void createPermissionGrant_identityNotFoundUnderAnyKind_stillPermitted() throws Exception {
      // Narrower-scoping decision: only a resolvable-but-mismatched kind is refused. An
      // identity that does not exist under ANY kind (e.g. not yet synced from LDAP/SSO) is
      // still permitted -- this is deliberately left open as a separate product question, not
      // tightened by this fix.
      IdentityID notYetSynced = new IdentityID("not-yet-synced-user", "org1");
      stubPermissionAccessGranted(ResourceType.ASSET, "Examples/Census");

      PermissionGrant grant = new PermissionGrant();
      grant.setIdentityID(notYetSynced);
      grant.setType("USER");
      grant.setActions(List.of("READ"));

      service.createPermissionGrant("Examples/Census", "ASSET", grant, principal);

      verify(securityProvider).setPermission(eq(ResourceType.ASSET), eq("Examples/Census"), any());
   }

   @Test
   void createPermissionGrant_correctlyTypedIdentity_permissionAccessDenied_throwsUnauthorizedAccessException() {
      // Round-2 refutation's key finding: getPermission/setPermission's pre-existing
      // checkPermissionAccess denial throws the SAME exception type (UnauthorizedAccessException)
      // for a completely different reason (no resource-permission access, not a bad
      // identityType) and must NOT be remapped by the new kind-mismatch logic/handler.
      IdentityID financeGroup = new IdentityID("Finance", "org1");
      when(securityProvider.getGroup(financeGroup)).thenReturn(new FSGroup(financeGroup));
      stubPermissionAccessDenied(ResourceType.ASSET, "Examples/Census");

      PermissionGrant grant = new PermissionGrant();
      grant.setIdentityID(financeGroup);
      grant.setType("GROUP");
      grant.setActions(List.of("READ"));

      UnauthorizedAccessException ex = assertThrows(UnauthorizedAccessException.class,
         () -> service.createPermissionGrant("Examples/Census", "ASSET", grant, principal));

      assertEquals("Permission denied to access permission", ex.getMessage());
   }

   private void stubPermissionAccessGranted(ResourceType type, String resourcePath) {
      when(actionPermissionService.getActionTree(principal)).thenReturn(emptyActionTree());
      when(securityProvider.checkPermission(principal, type, resourcePath, ResourceAction.ADMIN))
         .thenReturn(true);
   }

   private void stubPermissionAccessDenied(ResourceType type, String resourcePath) {
      when(actionPermissionService.getActionTree(principal)).thenReturn(emptyActionTree());
      when(securityProvider.checkPermission(principal, type, resourcePath, ResourceAction.ADMIN))
         .thenReturn(false);
   }

   private ActionTreeNode emptyActionTree() {
      return ActionTreeNode.builder()
         .label("root")
         .folder(false)
         .actions(EnumSet.noneOf(ResourceAction.class))
         .build();
   }

   private SecurityProvider securityProvider;
   private AuthenticationProvider authenticationProvider;
   private EditableAuthenticationProvider editableProvider;
   private OrganizationManager orgManager;
   private IdentityService identityService;
   private SystemAdminService systemAdminService;
   private UserTreeService userTreeService;
   private ActionPermissionService actionPermissionService;
   private Principal principal;
   private SecurityService service;
   private IdentityThemeService themeService;
   private CustomThemesManager customThemesManager;
   private MockedStatic<SUtil> sUtilStatic;
   private MockedStatic<OrganizationManager> organizationManagerStatic;
   private MockedStatic<Audit> auditStatic;
   private MockedStatic<SreeEnv> sreeEnvStatic;
   private MockedStatic<SecurityEngine> securityEngineStatic;
}
