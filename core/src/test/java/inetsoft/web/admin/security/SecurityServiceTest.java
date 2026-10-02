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
import inetsoft.sree.portal.CustomThemesManagerMocks;
import inetsoft.sree.security.*;
import inetsoft.uql.util.Identity;
import inetsoft.util.Catalog;
import inetsoft.util.audit.ActionRecord;
import inetsoft.util.audit.Audit;
import inetsoft.web.admin.InvalidResourceException;
import inetsoft.web.admin.general.LocalizationSettingsService;
import inetsoft.web.admin.security.action.ActionPermissionService;
import inetsoft.web.admin.security.action.ActionTreeNode;
import inetsoft.web.admin.security.user.*;
import inetsoft.web.security.auth.MissingResourceException;
import inetsoft.web.security.auth.ResourceExistsException;
import inetsoft.web.security.auth.UnauthorizedAccessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.security.Principal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import inetsoft.mv.MVManager;
import inetsoft.report.internal.license.LicenseManager;
import inetsoft.sree.RepletRegistryManager;
import inetsoft.sree.internal.DataCycleManager;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.web.dashboard.DashboardRegistryManager;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.sync.DependencyStorageService;
import inetsoft.uql.erm.HiddenColumns;
import inetsoft.uql.erm.XDataModel;
import inetsoft.uql.erm.vpm.VirtualPrivateModel;
import inetsoft.util.IndexedStorage;
import inetsoft.util.MessageException;
import inetsoft.util.config.InetsoftConfig;
import inetsoft.web.RecycleBin;
import inetsoft.web.admin.favorites.FavoritesService;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

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

      // an update that omits adminIdentities re-keys the grant through the authorization
      // provider on a rename (Bug #77326); tests that check the grant replace it via stubAuthz()
      when(securityProvider.getAuthorizationProvider())
         .thenReturn(mock(AuthorizationChain.class, withSettings().lenient()));

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
      // OrganizationIdRules reads the external storage config, keep it off the file system
      inetsoftConfigStatic = mockStatic(InetsoftConfig.class,
                                        withSettings().strictness(org.mockito.quality.Strictness.LENIENT));
      inetsoftConfigStatic.when(InetsoftConfig::getInstance).thenReturn(mock(InetsoftConfig.class));

      identityService = mock(IdentityService.class, withSettings().lenient());
      // the org self grant merge is IdentityService.setIdentityPermissions() with an explicit
      // bucket org (Bug #77271). Forward it to a real IdentityService over the same mocks, so the
      // org grant tests below check the permission calls the merge makes.
      IdentityService realIdentityService = new IdentityService(
         securityEngine, securityProvider,
         null, null, null, null, null, null, null, null, null, null, null, null, // positions 3-14
         Optional.empty(),                                                    // position 15
         null, null, null, null, null, null, null, null, null, null, null, null, null, // 16-28
         Optional.empty());                                                   // position 29
      doAnswer(inv -> {
         realIdentityService.setIdentityPermissions(
            inv.getArgument(0), inv.getArgument(1), inv.getArgument(2), inv.getArgument(3),
            inv.getArgument(4), inv.getArgument(5), inv.getArgument(6));
         return null;
      }).when(identityService).setIdentityPermissions(
         any(), any(), any(), any(), any(), any(), any());
      systemAdminService = mock(SystemAdminService.class, withSettings().lenient());

      // an XPrincipal, like the principals the public API and the shell pass in
      XPrincipal caller = mock(XPrincipal.class, withSettings().lenient());
      when(caller.getName()).thenReturn(new IdentityID("caller", "org1").convertToKey());
      when(caller.getOrgId()).thenReturn("org1");
      principal = caller;

      actionPermissionService = mock(ActionPermissionService.class, withSettings().lenient());

      // Real IdentityThemeService (not a mock) backed by a mocked CustomThemesManager, so the
      // theme regression tests below can assert against actual persisted membership lists
      // instead of just verifying a call was made with the right arguments.
      customThemesManager = mock(CustomThemesManager.class, withSettings().lenient());
      when(customThemesManager.getCustomThemes()).thenReturn(new HashSet<>());
      CustomThemesManagerMocks.applyUpdates(customThemesManager);
      themeService = new IdentityThemeService(customThemesManager);

      userTreeService = mock(UserTreeService.class, withSettings().lenient());

      // Bug #76828: updateUser/updateGroup now resolve every requested role's existence via
      // resolveRoleReferencesOrThrow(provider, ...) before it can reach builder.roles(...), so
      // any test asserting a role survives filterSystemAdminRoles into the final model needs
      // that role to actually resolve against editableProvider (the same provider these methods
      // use), not just against the separate authenticationProvider mock used for the
      // sysAdmin-name permission check. Stubbed non-null here so that check isn't what removes
      // ADMIN_ROLE/VIEWER_ROLE from a test's result -- only filterSystemAdminRoles() should be
      // responsible for that.
      when(editableProvider.getRole(VIEWER_ROLE)).thenReturn(new FSRole(VIEWER_ROLE));
      when(editableProvider.getRole(ADMIN_ROLE)).thenReturn(new FSRole(ADMIN_ROLE));

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
      inetsoftConfigStatic.close();
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
      // Also exists against editableProvider, the provider resolveRoleReferencesOrThrow actually
      // checks (bug #76828's resolution step), so it resolves instead of being rejected as unknown.
      doReturn(new FSRole(orgScopedAdmin)).when(editableProvider).getRole(orgScopedAdmin);

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

   // Issue #77171: omitted roles keep the user's current roles
   @Test
   void updateUser_rolesNotSpecified_keepsCurrentRoles() throws Exception {
      IdentityID userId = new IdentityID("orgadmin1", "org1");
      FSUser oldUser = new FSUser(userId);
      oldUser.setRoles(new IdentityID[] { VIEWER_ROLE });
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
      assertEquals(List.of(VIEWER_ROLE), captor.getValue().roles());
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

      // Bug #76855: updateUser's own precondition/model-building segment now wraps any thrown
      // exception (this one included) as PreMutationRefusalException -- see
      // SecurityService.PreMutationRefusalException's javadoc -- so this asserts on the new
      // wrapper type, with the original InvalidResourceException preserved as its cause.
      SecurityService.PreMutationRefusalException ex = assertThrows(
         SecurityService.PreMutationRefusalException.class,
         () -> service.updateUser(userId, request, principal));
      assertInstanceOf(InvalidResourceException.class, ex.getCause());
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

   // Issue #77171: omitted roles keep the group's current roles
   @Test
   void updateGroup_rolesNotSpecified_keepsCurrentRoles() throws Exception {
      IdentityID groupId = new IdentityID("orggroup1", "org1");
      FSGroup oldGroup = new FSGroup(groupId, null, new String[0], new IdentityID[] { VIEWER_ROLE });
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
      assertEquals(List.of(VIEWER_ROLE), captor.getValue().roles());
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

   // Issue #77171: omitted inherited roles keep the role's current inherited roles
   @Test
   void updateRole_inheritedRolesNotSpecified_keepsCurrentRoles() throws Exception {
      IdentityID roleId = new IdentityID("orgrole1", "org1");
      FSRole oldRole = new FSRole(roleId, new IdentityID[] { VIEWER_ROLE });
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
      assertEquals(List.of(VIEWER_ROLE), captor.getValue().roles());
   }

   // ── Issue #77171: user/group/role updates keep the fields that the request omits ──
   //
   // A field that the request omits (null) keeps the identity's current value in the editable
   // provider. An explicit value, including an empty list or string, replaces it. Ported from
   // the enterprise SecurityApiServiceTest on main; a locale is a code here (Bug #76707), and an
   // empty locale code maps to no label (null), which setIdentity() clears like an empty one.

   private static final IdentityID U1 = new IdentityID("u1", "org1");
   private static final IdentityID ORG1_OTHER_USER = new IdentityID("u2", "org1");

   private FSUser stubInactiveUser() {
      FSUser oldUser = new FSUser(U1);
      oldUser.setActive(false);
      oldUser.setLocale("en_US");
      oldUser.setAlias("User One");
      oldUser.setEmails(new String[] { "old@x.com" });
      oldUser.setGroups(new String[] { "sales" });
      oldUser.setRoles(new IdentityID[] { VIEWER_ROLE });
      when(securityProvider.getUser(U1)).thenReturn(oldUser);
      when(editableProvider.getUser(U1)).thenReturn(oldUser);
      allowAdmin();
      Properties locales = new Properties();
      locales.setProperty("en_US", "English(America)");
      sUtilStatic.when(SUtil::loadLocaleProperties).thenReturn(locales);
      return oldUser;
   }

   private EditUserPaneModel updateUserAndCapture(SecurityUser request) throws Exception {
      service.updateUser(U1, request, principal);
      ArgumentCaptor<EditUserPaneModel> captor = ArgumentCaptor.forClass(EditUserPaneModel.class);
      verify(identityService).setIdentity(any(), captor.capture(), eq(editableProvider), eq(principal));
      return captor.getValue();
   }

   private static SecurityUser userRequest() {
      SecurityUser request = new SecurityUser();
      request.setIdentityID(U1);
      return request;
   }

   @Test
   void updateUser_activeOmitted_inactiveUserStaysInactive() throws Exception {
      stubInactiveUser();
      SecurityUser request = userRequest();
      request.setEmails(List.of("new@x.com"));

      assertFalse(updateUserAndCapture(request).status());
   }

   @Test
   void updateUser_activeOmitted_selfUpdate_staysActive() throws Exception {
      stubInactiveUser();
      when(principal.getName()).thenReturn(U1.convertToKey());

      assertTrue(updateUserAndCapture(userRequest()).status());
   }

   @Test
   void updateUser_explicitActiveTrue_activates() throws Exception {
      stubInactiveUser();
      SecurityUser request = userRequest();
      request.setActive(Boolean.TRUE);

      assertTrue(updateUserAndCapture(request).status());
   }

   @Test
   void updateUser_explicitActiveFalse_deactivates() throws Exception {
      FSUser oldUser = stubInactiveUser();
      oldUser.setActive(true);
      SecurityUser request = userRequest();
      request.setActive(Boolean.FALSE);

      assertFalse(updateUserAndCapture(request).status());
   }

   @Test
   void updateUser_fieldsOmitted_keepsLocaleAliasEmailsGroupsRoles() throws Exception {
      stubInactiveUser();

      EditUserPaneModel model = updateUserAndCapture(userRequest());

      assertAll(
         () -> assertEquals("English(America)", model.locale(), "locale"),
         () -> assertEquals("User One", model.alias(), "alias"),
         () -> assertEquals("old@x.com", model.email(), "emails"),
         () -> assertEquals(List.of(SALES), model.members().stream()
            .map(IdentityModel::identityID).toList(), "groups"),
         () -> assertEquals(List.of(VIEWER_ROLE), model.roles(), "roles"));
   }

   @Test
   void updateUser_groupsOmitted_keepsGroupCallerCannotAdminister() throws Exception {
      stubInactiveUser();
      denyAdmin(ResourceType.SECURITY_GROUP, SALES);

      EditUserPaneModel model = updateUserAndCapture(userRequest());

      assertEquals(List.of(SALES), model.members().stream().map(IdentityModel::identityID).toList());
   }

   @Test
   void updateUser_explicitEmptyValues_clear() throws Exception {
      stubInactiveUser();
      SecurityUser request = userRequest();
      request.setLocale("");
      request.setAlias("");
      request.setEmails(List.of());
      request.setGroups(List.of());
      request.setRoles(List.of());

      EditUserPaneModel model = updateUserAndCapture(request);

      assertAll(
         // the empty code maps to no label, which setIdentity() clears like an empty one
         () -> assertNull(model.locale(), "locale"),
         () -> assertEquals("", model.alias(), "alias"),
         () -> assertEquals("", model.email(), "emails"),
         () -> assertTrue(model.members().isEmpty(), "groups"),
         () -> assertTrue(model.roles().isEmpty(), "roles"));
   }

   @Test
   void updateUser_explicitEmptyAdminIdentities_clearsGrants() throws Exception {
      stubInactiveUser();
      when(identityService.getPermission(U1, ResourceType.SECURITY_USER, "org1", principal))
         .thenReturn(List.of(model(ORG1_OTHER_USER, Identity.USER)));
      SecurityUser request = userRequest();
      request.setAdminIdentities(new AdminIdentities());

      service.updateUser(U1, request, principal);

      verify(identityService).setIdentityPermissions(
         eq(U1), eq(U1), eq(ResourceType.SECURITY_USER), eq(principal), eq(List.of()), anyString());
   }

   @Test
   void updateUser_identityIDOmitted_defaultsToPathName() throws Exception {
      stubInactiveUser();
      SecurityUser request = new SecurityUser();
      request.setEmails(List.of("new@x.com"));

      EditUserPaneModel model = updateUserAndCapture(request);

      assertEquals("u1", model.name());
      assertEquals("u1", model.oldName());
   }

   @Test
   void createUser_activeOmitted_createsActiveUser() throws Exception {
      stubCommonCreateGates();
      when(editableProvider.getOrganization("org1")).thenReturn(new FSOrganization("org1"));
      SecurityUser request = new SecurityUser();
      request.setIdentityID(new IdentityID("newuser1", "org1"));
      request.setPassword("Str0ng!Passw0rd");

      service.createUser(request, null, principal);

      ArgumentCaptor<FSUser> captor = ArgumentCaptor.forClass(FSUser.class);
      verify(editableProvider).addUser(captor.capture());
      assertTrue(captor.getValue().isActive());
   }

   @Test
   void securityUser_json_activeOmittedIsNull_explicitValueKept_serializedByGet() throws Exception {
      com.fasterxml.jackson.databind.ObjectMapper mapper =
         new com.fasterxml.jackson.databind.ObjectMapper();

      assertNull(mapper.readValue("{}", SecurityUser.class).getActive());
      assertNull(mapper.readValue("{\"active\":null}", SecurityUser.class).getActive());
      assertEquals(Boolean.FALSE,
                   mapper.readValue("{\"active\":false}", SecurityUser.class).getActive());

      // GET (getUserModel) always sets the status, so its JSON still contains "active"
      SecurityUser user = new SecurityUser();
      user.setActive(Boolean.FALSE);
      assertTrue(mapper.writeValueAsString(user).contains("\"active\":false"));
      user.setActive(Boolean.TRUE);
      assertTrue(mapper.writeValueAsString(user).contains("\"active\":true"));
   }

   // the omitted member lists are covered by the Bug #77303 tests below
   private FSGroup stubSalesGroup() {
      FSGroup oldGroup = new FSGroup(SALES, null, new String[0], new IdentityID[] { VIEWER_ROLE });
      when(securityProvider.getGroup(SALES)).thenReturn(oldGroup);
      when(editableProvider.getGroup(SALES)).thenReturn(oldGroup);
      allowAdmin();
      return oldGroup;
   }

   private EditGroupPaneModel updateGroupAndCapture(SecurityGroup request) throws Exception {
      service.updateGroup(SALES, request, principal);
      ArgumentCaptor<EditGroupPaneModel> captor = ArgumentCaptor.forClass(EditGroupPaneModel.class);
      verify(identityService).setIdentity(any(), captor.capture(), eq(editableProvider), eq(principal));
      return captor.getValue();
   }

   private static SecurityGroup groupRequest() {
      SecurityGroup request = new SecurityGroup();
      request.setIdentityID(SALES);
      return request;
   }

   @Test
   void updateGroup_rolesOmitted_keepCurrent() throws Exception {
      stubSalesGroup();

      EditGroupPaneModel model = updateGroupAndCapture(groupRequest());

      assertEquals(List.of(VIEWER_ROLE), model.roles());
   }

   @Test
   void updateGroup_explicitEmptyMembersAndRoles_clear() throws Exception {
      stubSalesGroup();
      SecurityGroup request = groupRequest();
      request.setMemberUsers(List.of());
      request.setMemberGroups(List.of());
      request.setRoles(List.of());

      EditGroupPaneModel model = updateGroupAndCapture(request);

      assertAll(
         () -> assertTrue(model.members().isEmpty(), "members"),
         () -> assertTrue(model.roles().isEmpty(), "roles"));
   }

   @Test
   void updateGroup_identityIDOmitted_defaultsToPathName() throws Exception {
      stubSalesGroup();

      EditGroupPaneModel model = updateGroupAndCapture(new SecurityGroup());

      assertEquals("sales", model.name());
   }

   // the omitted assignment lists are covered by the Bug #77303 tests below
   private FSRole stubAnalystRole() {
      FSRole oldRole = new FSRole(ANALYST, new IdentityID[] { VIEWER_ROLE }, "Analysts");
      when(securityProvider.getRole(ANALYST)).thenReturn(oldRole);
      when(editableProvider.getRole(ANALYST)).thenReturn(oldRole);
      allowAdmin();
      when(identityService.getIdentityInfo(ANALYST, Identity.ROLE, editableProvider))
         .thenReturn(new IdentityInfo());
      return oldRole;
   }

   private EditRolePaneModel updateRoleAndCapture(SecurityRole request) throws Exception {
      service.updateRole(ANALYST, request, principal);
      ArgumentCaptor<EditRolePaneModel> captor = ArgumentCaptor.forClass(EditRolePaneModel.class);
      verify(identityService).setIdentity(any(), captor.capture(), eq(editableProvider), eq(principal));
      return captor.getValue();
   }

   private static SecurityRole roleRequest() {
      SecurityRole request = new SecurityRole();
      request.setIdentityID(ANALYST);
      return request;
   }

   @Test
   void updateRole_descriptionInheritedRolesOmitted_keepCurrent() throws Exception {
      stubAnalystRole();

      EditRolePaneModel model = updateRoleAndCapture(roleRequest());

      assertAll(
         () -> assertEquals("Analysts", model.description(), "description"),
         () -> assertEquals(List.of(VIEWER_ROLE), model.roles(), "inherited roles"));
   }

   @Test
   void updateRole_explicitEmptyValues_clear() throws Exception {
      stubAnalystRole();
      SecurityRole request = roleRequest();
      request.setDescription("");
      request.setInheritedRoles(List.of());
      request.setAssignedUsers(List.of());
      request.setAssignedGroups(List.of());

      EditRolePaneModel model = updateRoleAndCapture(request);

      assertAll(
         () -> assertEquals("", model.description(), "description"),
         () -> assertTrue(model.roles().isEmpty(), "inherited roles"),
         () -> assertTrue(model.members().isEmpty(), "assignments"));
   }

   @Test
   void updateRole_identityIDOmitted_defaultsToPathName() throws Exception {
      stubAnalystRole();

      EditRolePaneModel model = updateRoleAndCapture(new SecurityRole());

      assertEquals("analyst", model.name());
   }

   private static IdentityModel model(IdentityID id, int type) {
      return IdentityModel.builder().identityID(id).type(type).build();
   }

   // ── Bug #77326: an omitted role/group/user field keeps the stored value ─
   //
   // The EM model was built with only the fields the request sets, so an omitted field took the
   // model default (an empty list, null, or true for the user status) and setIdentity() stored
   // it. An omitted adminIdentities was passed to setIdentityPermissions() as an empty list,
   // which drops every grantee the caller can administer. The stored identities below have a
   // value for every field, so a kept value can only come from the provider.

   private static final IdentityID PARENT_ROLE = new IdentityID("parent", "org2");

   private FSRole storedAnalystRole() {
      return stubUpdatableRole(
         ANALYST_ROLE, new FSRole(ANALYST_ROLE, new IdentityID[] { PARENT_ROLE }, "keep me"));
   }

   private FSGroup storedSalesGroup() {
      FSGroup oldGroup = stubUpdatableGroup(SALES_GROUP);
      oldGroup.setRoles(new IdentityID[] { PARENT_ROLE });
      return oldGroup;
   }

   // sales@org2: role parent, group g1, an email, an alias, locale en_US and disabled
   private FSUser storedSalesUser() {
      FSUser oldUser = stubUpdatableUser(SALES_USER);
      oldUser.setRoles(new IdentityID[] { PARENT_ROLE });
      oldUser.setGroups(new String[] { "g1" });
      oldUser.setEmails(new String[] { "a@b.c" });
      oldUser.setAlias("Ali");
      oldUser.setLocale("en_US");
      oldUser.setActive(false);
      Properties locales = new Properties();
      locales.setProperty("en_US", "English(America)");
      locales.setProperty("de_DE", "Deutsch(Deutschland)");
      sUtilStatic.when(SUtil::loadLocaleProperties).thenReturn(locales);
      return oldUser;
   }

   private EditGroupPaneModel captureGroupModel(FSGroup oldGroup) throws Exception {
      ArgumentCaptor<EditGroupPaneModel> captor = ArgumentCaptor.forClass(EditGroupPaneModel.class);
      verify(identityService).setIdentity(eq(oldGroup), captor.capture(), eq(editableProvider),
                                          eq(principal));
      return captor.getValue();
   }

   private EditUserPaneModel captureUserModel(FSUser oldUser) throws Exception {
      ArgumentCaptor<EditUserPaneModel> captor = ArgumentCaptor.forClass(EditUserPaneModel.class);
      verify(identityService).setIdentity(eq(oldUser), captor.capture(), eq(editableProvider),
                                          eq(principal));
      return captor.getValue();
   }

   // the grant is neither merged nor written
   private void verifyGrantUntouched(AuthorizationProvider authz) {
      verify(identityService, never())
         .setIdentityPermissions(any(), any(), any(), any(), any(), any());
      verify(identityService, never())
         .setIdentityPermissions(any(), any(), any(), any(), any(), any(), any());
      verifyNoInteractions(authz);
   }

   @Test
   void updateRole_omittedFields_keepStoredDescriptionInheritedRolesAndGrant() throws Exception {
      FSRole oldRole = storedAnalystRole();
      AuthorizationProvider authz = stubAuthz();
      // an org admin: the kept roles are not filtered, since they do not change
      when(orgManager.isSiteAdmin(principal)).thenReturn(false);

      service.updateRole(ANALYST_ROLE, roleRequest(ANALYST_ROLE), principal);

      EditRolePaneModel model = captureRoleModel(oldRole);
      assertEquals(List.of(PARENT_ROLE), model.roles());
      assertEquals("keep me", model.description());
      verifyGrantUntouched(authz);
   }

   @Test
   void updateRole_emptyValues_clearDescriptionInheritedRolesAndGrant() throws Exception {
      FSRole oldRole = storedAnalystRole();
      SecurityRole request = roleRequest(ANALYST_ROLE);
      request.setDescription("");
      request.setInheritedRoles(List.of());
      request.setAdminIdentities(new AdminIdentities());

      service.updateRole(ANALYST_ROLE, request, principal);

      EditRolePaneModel model = captureRoleModel(oldRole);
      assertEquals(List.of(), model.roles());
      assertEquals("", model.description());
      // an org role's grants are scoped to the role's own org (Bug #76866)
      verify(identityService).setIdentityPermissions(
         eq(ANALYST_ROLE), eq(ANALYST_ROLE), eq(ResourceType.SECURITY_ROLE), eq(principal),
         eq(List.of()), eq("org2"));
   }

   @Test
   void updateRole_listedValues_replaceStoredValues() throws Exception {
      FSRole oldRole = storedAnalystRole();
      SecurityRole request = roleRequest(ANALYST_ROLE);
      request.setDescription("new");
      request.setInheritedRoles(List.of(VIEWER_ROLE));

      service.updateRole(ANALYST_ROLE, request, principal);

      EditRolePaneModel model = captureRoleModel(oldRole);
      assertEquals(List.of(VIEWER_ROLE), model.roles());
      assertEquals("new", model.description());
   }

   @Test
   void updateRole_renamedWithOmittedAdminIdentitiesAndNoGrant_createsNoGrant() throws Exception {
      storedAnalystRole();
      AuthorizationProvider authz = stubAuthz();

      service.updateRole(ANALYST_ROLE, roleRequest(new IdentityID("analyst2", "org2")), principal);

      verify(authz).getPermission(ResourceType.SECURITY_ROLE, ANALYST_ROLE);
      verify(authz, never()).setPermission(any(ResourceType.class), any(IdentityID.class),
                                           any(Permission.class));
      verify(authz, never()).removePermission(any(ResourceType.class), any(IdentityID.class));
      verify(identityService, never())
         .setIdentityPermissions(any(), any(), any(), any(), any(), any());
   }

   @Test
   void updateRole_globalRoleOmittedFields_keepStoredAndRekeyGrantWithNullOrg() throws Exception {
      IdentityID globalParent = new IdentityID("gParent", null);
      IdentityID renamed = new IdentityID("gRole2", null);
      FSRole oldRole = stubUpdatableRole(
         GLOBAL_ROLE_PATH, new FSRole(GLOBAL_ROLE, new IdentityID[] { globalParent }, "global"));
      AuthorizationProvider authz = stubAuthz();
      Permission grant = new Permission();
      when(authz.getPermission(ResourceType.SECURITY_ROLE, GLOBAL_ROLE)).thenReturn(grant);

      service.updateRole(GLOBAL_ROLE_PATH, roleRequest(renamed), principal);

      EditRolePaneModel model = captureRoleModel(oldRole);
      assertEquals(List.of(globalParent), model.roles());
      assertEquals("global", model.description());
      verify(authz).setPermission(ResourceType.SECURITY_ROLE, renamed, grant);
      verify(authz).removePermission(ResourceType.SECURITY_ROLE, GLOBAL_ROLE);
   }

   @Test
   void updateGroup_omittedRolesAndAdminIdentities_keepStoredRolesAndGrant() throws Exception {
      FSGroup oldGroup = storedSalesGroup();
      AuthorizationProvider authz = stubAuthz();
      when(orgManager.isSiteAdmin(principal)).thenReturn(false);

      service.updateGroup(SALES_GROUP, groupRequest(SALES_GROUP), principal);

      assertEquals(List.of(PARENT_ROLE), captureGroupModel(oldGroup).roles());
      verifyGrantUntouched(authz);
   }

   @Test
   void updateGroup_emptyValues_clearRolesAndGrant() throws Exception {
      FSGroup oldGroup = storedSalesGroup();
      SecurityGroup request = groupRequest(SALES_GROUP);
      request.setRoles(List.of());
      request.setAdminIdentities(new AdminIdentities());

      service.updateGroup(SALES_GROUP, request, principal);

      assertEquals(List.of(), captureGroupModel(oldGroup).roles());
      verify(identityService).setIdentityPermissions(
         eq(SALES_GROUP), eq(SALES_GROUP), eq(ResourceType.SECURITY_GROUP), eq(principal),
         eq(List.of()), eq("org2"));
   }

   @Test
   void updateUser_omittedFields_keepStoredValuesAndGrant() throws Exception {
      FSUser oldUser = storedSalesUser();
      AuthorizationProvider authz = stubAuthz();
      when(orgManager.isSiteAdmin(principal)).thenReturn(false);

      service.updateUser(SALES_USER, userRequest(SALES_USER), principal);

      EditUserPaneModel model = captureUserModel(oldUser);
      assertEquals(List.of(PARENT_ROLE), model.roles());
      // the kept group is not filtered by the caller's permissions, since it does not change
      assertEquals(Set.of(groupKey(new IdentityID("g1", "org2"))), memberKeys(model.members()));
      assertEquals("a@b.c", model.email());
      assertEquals("Ali", model.alias());
      // setIdentity() takes the label and maps it back to the stored key
      assertEquals("English(America)", model.locale());
      assertFalse(model.status(), "an omitted active flag must not re-enable the user");
      verifyGrantUntouched(authz);
   }

   @Test
   void updateUser_emptyValues_clearStoredValuesAndGrant() throws Exception {
      FSUser oldUser = storedSalesUser();
      SecurityUser request = userRequest(SALES_USER);
      request.setRoles(List.of());
      request.setGroups(List.of());
      request.setEmails(List.of());
      request.setAlias("");
      request.setLocale("");
      request.setActive(Boolean.TRUE);
      request.setAdminIdentities(new AdminIdentities());

      service.updateUser(SALES_USER, request, principal);

      EditUserPaneModel model = captureUserModel(oldUser);
      assertEquals(List.of(), model.roles());
      assertEquals(List.of(), model.members());
      assertEquals("", model.email());
      assertEquals("", model.alias());
      // the empty code maps to no label, which setIdentity() clears like an empty one
      assertNull(model.locale());
      assertTrue(model.status());
      verify(identityService).setIdentityPermissions(
         eq(SALES_USER), eq(SALES_USER), eq(ResourceType.SECURITY_USER), eq(principal),
         eq(List.of()), eq(""));
   }

   @Test
   void updateUser_listedValues_replaceStoredValues() throws Exception {
      FSUser oldUser = storedSalesUser();
      allowAdminOnEveryIdentity();
      IdentityID g2 = new IdentityID("g2", "org2");
      doReturn(new FSGroup(g2)).when(authenticationProvider).getGroup(g2);
      SecurityUser request = userRequest(SALES_USER);
      request.setGroups(List.of("g2"));
      request.setEmails(List.of("x@y.z", "u@v.w"));
      request.setAlias("Bo");
      // the REST locale is a code (Bug #76707), passed to setIdentity() as its label
      request.setLocale("de_DE");

      service.updateUser(SALES_USER, request, principal);

      EditUserPaneModel model = captureUserModel(oldUser);
      assertEquals(Set.of(groupKey(g2)), memberKeys(model.members()));
      assertEquals("x@y.z,u@v.w", model.email());
      assertEquals("Bo", model.alias());
      assertEquals("Deutsch(Deutschland)", model.locale());
   }

   @Test
   void updateUser_activeFalseForAnotherUser_disables() throws Exception {
      FSUser oldUser = stubUpdatableUser(SALES_USER);
      SecurityUser request = userRequest(SALES_USER);
      request.setActive(Boolean.FALSE);

      service.updateUser(SALES_USER, request, principal);

      assertFalse(captureUserModel(oldUser).status());
   }

   @Test
   void updateUser_activeFalseForSelf_staysActive() throws Exception {
      IdentityID self = new IdentityID("caller", "org1");
      FSUser oldUser = stubUpdatableUser(self);
      SecurityUser request = userRequest(self);
      request.setActive(Boolean.FALSE);

      service.updateUser(self, request, principal);

      assertTrue(captureUserModel(oldUser).status(), "a caller cannot deactivate itself");
   }

   // ── updateRole permission-write org (bug #76866 regression) ─────────────
   //
   // IdentityService.setIdentityPermissions now refuses an all-empty org id (bug #76866). A global
   // (org-less) role has no org of its own, so updateRole used to pass "" and the call threw after
   // setIdentity() had already committed the rename/member change. updateRole must pass the
   // editing org explicitly for a global role, and the role's own org otherwise. The requests
   // below carry adminIdentities, since an update that omits them keeps the grant without calling
   // setIdentityPermissions() (Bug #77326).

   @Test
   void updateRole_globalRole_passesCallerCurrentOrgToSetIdentityPermissions() throws Exception {
      IdentityID roleId = new IdentityID("grole", null);
      FSRole oldRole = new FSRole(roleId);
      when(securityProvider.getRole(roleId)).thenReturn(oldRole);
      when(securityProvider.checkPermission(principal, ResourceType.SECURITY_ROLE,
                                            roleId.convertToKey(), ResourceAction.ADMIN))
         .thenReturn(true);
      when(editableProvider.getRole(roleId)).thenReturn(oldRole);
      when(orgManager.getCurrentOrgID(principal)).thenReturn("org1");

      SecurityRole request = new SecurityRole();
      request.setIdentityID(new IdentityID("grole2", null));
      request.setAdminIdentities(new AdminIdentities());

      service.updateRole(roleId, request, principal);

      verify(identityService).setIdentityPermissions(
         eq(new IdentityID("grole", null)), eq(new IdentityID("grole2", null)),
         eq(ResourceType.SECURITY_ROLE), eq(principal), any(), eq("org1"));
   }

   @Test
   void updateRole_orgScopedRole_passesRoleOwnOrgToSetIdentityPermissions() throws Exception {
      IdentityID roleId = new IdentityID("orgrole1", "org2");
      FSRole oldRole = new FSRole(roleId);
      when(securityProvider.getRole(roleId)).thenReturn(oldRole);
      when(securityProvider.checkPermission(principal, ResourceType.SECURITY_ROLE,
                                            roleId.convertToKey(), ResourceAction.ADMIN))
         .thenReturn(true);
      when(editableProvider.getRole(roleId)).thenReturn(oldRole);
      when(orgManager.getCurrentOrgID(principal)).thenReturn("org1");

      SecurityRole request = new SecurityRole();
      request.setIdentityID(roleId);
      request.setAdminIdentities(new AdminIdentities());

      service.updateRole(roleId, request, principal);

      verify(identityService).setIdentityPermissions(
         eq(roleId), eq(roleId), eq(ResourceType.SECURITY_ROLE), eq(principal), any(), eq("org2"));
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

   // Bug #77381: create paths write the identity to the provider directly, bypassing the role
   // assignment check in IdentityService.setIdentity(), so the roles taken from the request must
   // go through IdentityService.checkAssignableRoles(). The default roles applied when the request
   // roles are not honored must not, or a caller without ASSIGN on a default role could not create
   // any user.

   @Test
   void createUser_requestedRoles_areCheckedForAssignability() throws Exception {
      stubCommonCreateGates();
      when(editableProvider.getOrganization("org1")).thenReturn(new FSOrganization("org1"));
      SecurityUser request = new SecurityUser();
      request.setIdentityID(new IdentityID("newuser1", "org1"));
      request.setPassword("Str0ng!Passw0rd");
      request.setRoles(List.of(ADMIN_ROLE, VIEWER_ROLE));

      service.createUser(request, null, principal);

      verify(identityService).checkAssignableRoles(eq(Set.of(VIEWER_ROLE)), isNull(), eq(principal));
   }

   @Test
   void createUser_unassignableRequestedRole_isRejected() throws Exception {
      stubCommonCreateGates();
      when(editableProvider.getOrganization("org1")).thenReturn(new FSOrganization("org1"));
      doThrow(new java.lang.SecurityException("denied"))
         .when(identityService).checkAssignableRoles(any(), any(), eq(principal));
      SecurityUser request = new SecurityUser();
      request.setIdentityID(new IdentityID("newuser1", "org1"));
      request.setPassword("Str0ng!Passw0rd");
      request.setRoles(List.of(VIEWER_ROLE));

      assertThrows(UnauthorizedAccessException.class,
                   () -> service.createUser(request, null, principal));
      verify(editableProvider, never()).addUser(any());
   }

   @Test
   void createUser_defaultRoleFallback_isNotCheckedForAssignability() throws Exception {
      stubCommonCreateGates();
      when(editableProvider.getOrganization("org1")).thenReturn(new FSOrganization("org1"));
      // without ADMIN on the roles root the request roles are ignored and default roles apply
      when(securityProvider.checkPermission(eq(principal), eq(ResourceType.SECURITY_ROLE),
                                            anyString(), eq(ResourceAction.ADMIN)))
         .thenReturn(false);
      FSRole defaultRole = new FSRole(VIEWER_ROLE);
      defaultRole.setDefaultRole(true);
      when(editableProvider.getRoles()).thenReturn(new IdentityID[] { VIEWER_ROLE });
      when(editableProvider.getRole(VIEWER_ROLE)).thenReturn(defaultRole);
      SecurityUser request = new SecurityUser();
      request.setIdentityID(new IdentityID("newuser1", "org1"));
      request.setPassword("Str0ng!Passw0rd");
      request.setRoles(List.of(VIEWER_ROLE));

      service.createUser(request, null, principal);

      verify(identityService, never()).checkAssignableRoles(any(), any(), any());
      ArgumentCaptor<FSUser> captor = ArgumentCaptor.forClass(FSUser.class);
      verify(editableProvider).addUser(captor.capture());
      assertEquals(List.of(VIEWER_ROLE), List.of(captor.getValue().getRoles()));
   }

   @Test
   void createGroup_unassignableRequestedRole_isRejected() throws Exception {
      stubCommonCreateGates();
      doThrow(new java.lang.SecurityException("denied"))
         .when(identityService).checkAssignableRoles(any(), any(), eq(principal));
      SecurityGroup request = new SecurityGroup();
      request.setIdentityID(new IdentityID("newgroup1", "org1"));
      request.setOrgID("org1");
      request.setRoles(List.of(VIEWER_ROLE));

      assertThrows(UnauthorizedAccessException.class,
                   () -> service.createGroup(request, null, principal));
      verify(editableProvider, never()).addGroup(any());
   }

   @Test
   void createGroup_requestedRoles_areCheckedForAssignability() throws Exception {
      stubCommonCreateGates();
      SecurityGroup request = new SecurityGroup();
      request.setIdentityID(new IdentityID("newgroup1", "org1"));
      request.setOrgID("org1");
      request.setRoles(List.of(VIEWER_ROLE));

      service.createGroup(request, null, principal);

      verify(identityService).checkAssignableRoles(eq(Set.of(VIEWER_ROLE)), isNull(), eq(principal));
   }

   @Test
   void createRole_unassignableInheritedRole_isRejected() throws Exception {
      stubCommonCreateGates();
      doThrow(new java.lang.SecurityException("denied"))
         .when(identityService).checkAssignableRoles(any(), any(), eq(principal));
      SecurityRole request = new SecurityRole();
      request.setIdentityID(new IdentityID("neworgrole1", "org1"));
      request.setInheritedRoles(List.of(VIEWER_ROLE));

      assertThrows(UnauthorizedAccessException.class,
                   () -> service.createRole(request, null, principal));
      verify(editableProvider, never()).addRole(any());
   }

   // ── global role name resolution (Bug #76828) ─────────────────────────────────────────────
   //
   // "Administrator" and "Organization Administrator" are created exactly once as GLOBAL roles
   // (orgID == null). A caller assigning either by bare name via spec.roles builds the
   // IdentityID with its own org (never null), so a plain provider.getRole(role) lookup on that
   // org-scoped key never matches the real global role's key -- createUser/createGroup silently
   // dropped the role with no error, and updateUser/updateGroup had no resolvability check at
   // all and stored the broken reference verbatim. Fixed by resolveRoleReference(), which falls
   // back to the org-less global key only when the direct org-scoped lookup already found
   // nothing, and resolveRoleReferencesOrThrow(), which throws MissingResourceException naming
   // any role that still doesn't resolve instead of silently dropping or storing it.

   private static final IdentityID ORG_ADMIN_ROLE_REQUESTED = new IdentityID("Organization Administrator", "org1");
   private static final IdentityID ORG_ADMIN_ROLE_GLOBAL = new IdentityID("Organization Administrator", null);

   @Test
   void createUser_globalRoleRequestedWithCallerOrg_resolvesToGlobalKey() throws Exception {
      stubCommonCreateGates();
      when(editableProvider.getOrganization("org1")).thenReturn(new FSOrganization("org1"));
      when(editableProvider.getRole(ORG_ADMIN_ROLE_REQUESTED)).thenReturn(null);
      when(editableProvider.getRole(ORG_ADMIN_ROLE_GLOBAL)).thenReturn(new FSRole(ORG_ADMIN_ROLE_GLOBAL));

      SecurityUser request = new SecurityUser();
      request.setIdentityID(new IdentityID("neworgadminuser1", "org1"));
      request.setPassword("Str0ng!Passw0rd");
      request.setRoles(List.of(ORG_ADMIN_ROLE_REQUESTED, VIEWER_ROLE));

      service.createUser(request, null, principal);

      ArgumentCaptor<FSUser> captor = ArgumentCaptor.forClass(FSUser.class);
      verify(editableProvider).addUser(captor.capture());
      assertEquals(Set.of(ORG_ADMIN_ROLE_GLOBAL, VIEWER_ROLE),
                  Set.of(captor.getValue().getRoles()),
                  "Bug #76828: a global role requested with the caller's own org id must resolve "
                  + "to its real global key instead of being silently dropped");
   }

   @Test
   void createUser_unresolvableRole_throwsInsteadOfSilentlyDroppingIt() throws Exception {
      stubCommonCreateGates();
      when(editableProvider.getOrganization("org1")).thenReturn(new FSOrganization("org1"));
      IdentityID noSuchRole = new IdentityID("NoSuchRole", "org1");
      when(editableProvider.getRole(noSuchRole)).thenReturn(null);
      when(editableProvider.getRole(new IdentityID("NoSuchRole", null))).thenReturn(null);

      SecurityUser request = new SecurityUser();
      request.setIdentityID(new IdentityID("newuser2", "org1"));
      request.setPassword("Str0ng!Passw0rd");
      request.setRoles(List.of(noSuchRole, VIEWER_ROLE));

      MissingResourceException ex = assertThrows(MissingResourceException.class,
         () -> service.createUser(request, null, principal));
      assertTrue(ex.getMessage().contains("NoSuchRole"));
      verify(editableProvider, never()).addUser(any());
   }

   @Test
   void createGroup_globalRoleRequestedWithCallerOrg_resolvesToGlobalKey() throws Exception {
      stubCommonCreateGates();
      when(editableProvider.getRole(ORG_ADMIN_ROLE_REQUESTED)).thenReturn(null);
      when(editableProvider.getRole(ORG_ADMIN_ROLE_GLOBAL)).thenReturn(new FSRole(ORG_ADMIN_ROLE_GLOBAL));

      SecurityGroup request = new SecurityGroup();
      request.setIdentityID(new IdentityID("neworgadmingroup1", "org1"));
      request.setOrgID("org1");
      request.setRoles(List.of(ORG_ADMIN_ROLE_REQUESTED, VIEWER_ROLE));

      service.createGroup(request, null, principal);

      ArgumentCaptor<FSGroup> captor = ArgumentCaptor.forClass(FSGroup.class);
      verify(editableProvider).addGroup(captor.capture());
      assertEquals(Set.of(ORG_ADMIN_ROLE_GLOBAL, VIEWER_ROLE), Set.of(captor.getValue().getRoles()));
   }

   @Test
   void updateUser_globalRoleRequestedWithCallerOrg_resolvesToGlobalKey() throws Exception {
      IdentityID userId = new IdentityID("orgadminuser1", "org1");
      FSUser oldUser = new FSUser(userId);
      when(securityProvider.getUser(userId)).thenReturn(oldUser);
      when(securityProvider.checkPermission(principal, ResourceType.SECURITY_USER,
                                            userId.convertToKey(), ResourceAction.ADMIN))
         .thenReturn(true);
      when(editableProvider.getUser(userId)).thenReturn(oldUser);
      when(orgManager.isSiteAdmin(principal)).thenReturn(false);
      when(editableProvider.getRole(ORG_ADMIN_ROLE_REQUESTED)).thenReturn(null);
      when(editableProvider.getRole(ORG_ADMIN_ROLE_GLOBAL)).thenReturn(new FSRole(ORG_ADMIN_ROLE_GLOBAL));

      SecurityUser request = new SecurityUser();
      request.setIdentityID(userId);
      request.setRoles(List.of(ORG_ADMIN_ROLE_REQUESTED, VIEWER_ROLE));

      service.updateUser(userId, request, principal);

      ArgumentCaptor<EditUserPaneModel> captor = ArgumentCaptor.forClass(EditUserPaneModel.class);
      verify(identityService).setIdentity(eq(oldUser), captor.capture(), eq(editableProvider), eq(principal));
      assertEquals(Set.of(ORG_ADMIN_ROLE_GLOBAL, VIEWER_ROLE), Set.copyOf(captor.getValue().roles()),
                  "Bug #76828: updateUser had no resolvability check at all -- the broken "
                  + "org-scoped reference was stored verbatim instead of resolving to the real "
                  + "global role");
   }

   @Test
   void updateUser_unresolvableRole_throwsInsteadOfSilentlyStoringIt() throws Exception {
      IdentityID userId = new IdentityID("orgadminuser2", "org1");
      FSUser oldUser = new FSUser(userId);
      when(securityProvider.getUser(userId)).thenReturn(oldUser);
      when(securityProvider.checkPermission(principal, ResourceType.SECURITY_USER,
                                            userId.convertToKey(), ResourceAction.ADMIN))
         .thenReturn(true);
      when(editableProvider.getUser(userId)).thenReturn(oldUser);
      when(orgManager.isSiteAdmin(principal)).thenReturn(false);
      IdentityID noSuchRole = new IdentityID("NoSuchRole", "org1");
      when(editableProvider.getRole(noSuchRole)).thenReturn(null);
      when(editableProvider.getRole(new IdentityID("NoSuchRole", null))).thenReturn(null);

      SecurityUser request = new SecurityUser();
      request.setIdentityID(userId);
      request.setRoles(List.of(noSuchRole));

      // Bug #76855: wrapped as PreMutationRefusalException now -- see the locale test above.
      SecurityService.PreMutationRefusalException ex = assertThrows(
         SecurityService.PreMutationRefusalException.class,
         () -> service.updateUser(userId, request, principal));
      assertInstanceOf(MissingResourceException.class, ex.getCause());
      assertTrue(ex.getCause().getMessage().contains("NoSuchRole"));
      verify(identityService, never()).setIdentity(any(), any(), any(), any());
   }

   @Test
   void updateGroup_globalRoleRequestedWithCallerOrg_resolvesToGlobalKey() throws Exception {
      IdentityID groupId = new IdentityID("orgadmingroup1", "org1");
      FSGroup oldGroup = new FSGroup(groupId);
      when(securityProvider.getGroup(groupId)).thenReturn(oldGroup);
      when(securityProvider.checkPermission(principal, ResourceType.SECURITY_GROUP,
                                            groupId.convertToKey(), ResourceAction.ADMIN))
         .thenReturn(true);
      when(editableProvider.getGroup(groupId)).thenReturn(oldGroup);
      when(orgManager.isSiteAdmin(principal)).thenReturn(false);
      when(editableProvider.getRole(ORG_ADMIN_ROLE_REQUESTED)).thenReturn(null);
      when(editableProvider.getRole(ORG_ADMIN_ROLE_GLOBAL)).thenReturn(new FSRole(ORG_ADMIN_ROLE_GLOBAL));

      SecurityGroup request = new SecurityGroup();
      request.setIdentityID(groupId);
      request.setRoles(List.of(ORG_ADMIN_ROLE_REQUESTED, VIEWER_ROLE));

      service.updateGroup(groupId, request, principal);

      ArgumentCaptor<EditGroupPaneModel> captor = ArgumentCaptor.forClass(EditGroupPaneModel.class);
      verify(identityService).setIdentity(eq(oldGroup), captor.capture(), eq(editableProvider), eq(principal));
      assertEquals(Set.of(ORG_ADMIN_ROLE_GLOBAL, VIEWER_ROLE), Set.copyOf(captor.getValue().roles()));
   }

   @Test
   void updateUser_nonSiteAdmin_stillFiltersAdministratorRole_afterResolution() throws Exception {
      // Guards ordering: the system-admin permission gate must still run BEFORE the new
      // resolve-or-throw step, so the fallback resolution logic does not become an alternate
      // route around the existing security check for who may assign "Administrator".
      IdentityID userId = new IdentityID("orgadminuser3", "org1");
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
      assertEquals(List.of(VIEWER_ROLE), captor.getValue().roles(),
                  "the system-admin permission gate must still strip \"Administrator\" for a "
                  + "non-site-admin even after the role-resolution fix");
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
      // an org theme: a global theme's bare names only refer to default-org identities
      theme.setOrgID("org1");
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
      // an org theme: a global theme's bare names only refer to default-org identities
      theme.setOrgID("org1");
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
      // an org theme: a global theme's bare names only refer to default-org identities
      theme.setOrgID("org1");
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
      // an org theme: a global theme's bare names only refer to default-org identities
      theme.setOrgID("org1");
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
      // an org theme: a global theme's bare names only refer to default-org identities
      theme.setOrgID("org1");
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
      // an org theme: a global theme's bare names only refer to default-org identities
      theme.setOrgID("org1");
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
      // an org theme: a global theme's bare names only refer to default-org identities
      theme.setOrgID("org1");
      when(customThemesManager.getCustomThemes()).thenReturn(new HashSet<>(Set.of(theme)));

      SecurityRole request = new SecurityRole();
      request.setIdentityID(roleId);
      request.setTheme("theme-1");

      service.updateRole(roleId, request, principal);

      assertTrue(theme.getRoles().contains("orgrole1"));
   }

   // ── theme organization scoping (Bug #77056) ─────────────────────────────

   // Bug #77056: the group's organization is passed to the theme rename, so a same-named
   // group of another organization keeps its theme assignment
   @Test
   void updateGroup_rename_themeRenameScopedToGroupOrg() throws Exception {
      IdentityID groupId = new IdentityID("sales", "org1");
      FSGroup oldGroup = new FSGroup(groupId);
      when(securityProvider.getGroup(groupId)).thenReturn(oldGroup);
      when(securityProvider.checkPermission(principal, ResourceType.SECURITY_GROUP,
                                            groupId.convertToKey(), ResourceAction.ADMIN))
         .thenReturn(true);
      when(editableProvider.getGroup(groupId)).thenReturn(oldGroup);
      CustomTheme org1Theme = theme("org1Theme", "org1");
      org1Theme.getGroups().add("sales");
      CustomTheme org2Theme = theme("org2Theme", "org2");
      org2Theme.getGroups().add("sales");
      when(customThemesManager.getCustomThemes())
         .thenReturn(new HashSet<>(Set.of(org1Theme, org2Theme)));

      SecurityGroup request = new SecurityGroup();
      request.setIdentityID(new IdentityID("sales2", "org1"));

      service.updateGroup(groupId, request, principal);

      assertEquals(List.of("sales2"), org1Theme.getGroups());
      assertEquals(List.of("sales"), org2Theme.getGroups());
      assertEquals("org2", org2Theme.getOrgID());
   }

   // Bug #77056: createGroup/createRole passed a null old name to the theme rename, which
   // threw a NullPointerException for every global theme after the identity had been added
   @Test
   void createGroup_globalThemeExists_noExceptionAndThemeUntouched() throws Exception {
      stubCommonCreateGates();
      CustomTheme globalTheme = theme("globalTheme", null);
      when(customThemesManager.getCustomThemes()).thenReturn(new HashSet<>(Set.of(globalTheme)));

      SecurityGroup request = new SecurityGroup();
      request.setIdentityID(new IdentityID("newgroup1", "org1"));
      request.setOrgID("org1");

      assertDoesNotThrow(() -> service.createGroup(request, null, principal));

      assertNull(globalTheme.getOrgID());
      assertEquals("portal/theme/globalTheme.jar", globalTheme.getJarPath());
      assertFalse(globalTheme.getGroups().contains("newgroup1"));
   }

   @Test
   void createRole_globalThemeExists_noExceptionAndThemeUntouched() throws Exception {
      stubCommonCreateGates();
      CustomTheme globalTheme = theme("globalTheme", null);
      when(customThemesManager.getCustomThemes()).thenReturn(new HashSet<>(Set.of(globalTheme)));

      SecurityRole request = new SecurityRole();
      request.setIdentityID(new IdentityID("neworgrole1", "org1"));

      assertDoesNotThrow(() -> service.createRole(request, null, principal));

      assertNull(globalTheme.getOrgID());
      assertEquals("portal/theme/globalTheme.jar", globalTheme.getJarPath());
      assertFalse(globalTheme.getRoles().contains("neworgrole1"));
   }

   private static CustomTheme theme(String id, String orgID) {
      CustomTheme theme = new CustomTheme();
      theme.setId(id);
      theme.setName(id);
      theme.setOrgID(orgID);
      theme.setJarPath(orgID == null ? "portal/theme/" + id + ".jar" :
                          "portal/" + orgID + "/theme/" + id + ".jar");
      return theme;
   }

   // ── getGroup (theme) ────────────────────────────────────────────────────
   //
   // Bug #77096: getGroupModel looked the group's theme up in CustomTheme.getRoles instead of
   // getGroups, so the group GET reported a same-named role's theme and missed the group's own.
   // These tests use the fixture's real IdentityThemeService over the given themes so the
   // accessor is exercised.

   @Test
   void getGroup_themeAssignedToGroup_returnsThatTheme() throws Exception {
      SecurityGroup result =
         getGroupWithThemes(groupRoleTheme("groupTheme", List.of("sales"), List.of()));

      assertEquals("groupTheme", result.getTheme());
   }

   @Test
   void getGroup_themeAssignedOnlyToSameNamedRole_returnsNoTheme() throws Exception {
      SecurityGroup result =
         getGroupWithThemes(groupRoleTheme("roleTheme", List.of(), List.of("sales")));

      assertNull(result.getTheme());
   }

   @Test
   void getGroup_groupAndSameNamedRoleHaveDifferentThemes_returnsGroupTheme() throws Exception {
      SecurityGroup result = getGroupWithThemes(
         groupRoleTheme("groupTheme", List.of("sales"), List.of()),
         groupRoleTheme("roleTheme", List.of(), List.of("sales")));

      assertEquals("groupTheme", result.getTheme());
   }

   @Test
   void getGroup_noThemeAssigned_returnsNoTheme() throws Exception {
      SecurityGroup result =
         getGroupWithThemes(groupRoleTheme("otherTheme", List.of("hr"), List.of("hr")));

      assertNull(result.getTheme());
   }

   private SecurityGroup getGroupWithThemes(CustomTheme... themes) throws Exception {
      // host-org: the themes below are global (no orgID), and a global theme only applies to
      // default-org identities once #77056 scopes theme references by organization
      IdentityID groupId = new IdentityID("sales", Organization.getDefaultOrganizationID());
      when(securityProvider.checkPermission(principal, ResourceType.SECURITY_GROUP,
                                            groupId.convertToKey(), ResourceAction.ADMIN))
         .thenReturn(true);
      when(securityProvider.getGroup(groupId)).thenReturn(new FSGroup(groupId));
      when(identityService.getIdentityInfo(groupId, Identity.GROUP, securityProvider))
         .thenReturn(new IdentityInfo());
      when(customThemesManager.getCustomThemes()).thenReturn(new HashSet<>(Arrays.asList(themes)));

      return service.getGroup(groupId, principal);
   }

   private static CustomTheme groupRoleTheme(String id, List<String> groups, List<String> roles) {
      CustomTheme theme = new CustomTheme();
      theme.setId(id);
      theme.setName(id);
      theme.setGroups(new ArrayList<>(groups));
      theme.setRoles(new ArrayList<>(roles));
      return theme;
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
      // only a site admin manages an org other than their own (Bug #77216)
      when(orgManager.isSiteAdmin(principal)).thenReturn(true);
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
      // only a site admin manages an org other than their own (Bug #77216)
      when(orgManager.isSiteAdmin(principal)).thenReturn(true);
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

      // Bug #76855: wrapped as PreMutationRefusalException now -- see updateUser's locale test.
      SecurityService.PreMutationRefusalException ex = assertThrows(
         SecurityService.PreMutationRefusalException.class,
         () -> service.updateOrganization(orgId, request, principal));
      assertInstanceOf(InvalidResourceException.class, ex.getCause());
      verify(identityService, never()).setIdentity(any(), any(), any(), any());
   }

   @Test
   void getOrganization_idDiffersFromName_readsBackTheAssignedTheme() throws Exception {
      String orgId = "org-xyz";
      // only a site admin manages an org other than their own (Bug #77216)
      when(orgManager.isSiteAdmin(principal)).thenReturn(true);
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
      // what IdentityService.updateCustomThemeOrganization() writes: the org's own theme ID and
      // an id-keyed membership entry, not a name-keyed one
      organization.setTheme("theme-1");
      theme.setOrganizations(List.of(orgId));
      when(customThemesManager.getCustomThemes()).thenReturn(new HashSet<>(Set.of(theme)));

      SecurityOrganization result = service.getOrganization(orgId, principal);

      // Bug #76671 finding 3: an org whose id differs from its name must still read back its
      // theme. The GET reports the org's own theme (Organization.theme), which the PUT compares
      // against and writes back (Bug #77086), not a membership lookup by display name.
      assertEquals("theme-1", result.getTheme());
   }

   private SecurityOrganization newOrgRequest(String id, String name) {
      SecurityOrganization request = new SecurityOrganization();
      request.setId(id);
      request.setName(name);
      return request;
   }

   // ── createOrganization member user default password (Bug #77207) ────────
   //
   // The non-clone create path ignored defaultPassword and gave every member user the
   // hard-coded, policy-violating password "success123". defaultPassword is now required and
   // validated (before any write) whenever memberUsers is not empty, and used for the members.

   @Test
   void createOrganization_memberUsers_noDefaultPassword_rejectedBeforeAnyWrite() {
      when(orgManager.isSiteAdmin(principal)).thenReturn(true);
      when(securityProvider.getOrganizationIDs()).thenReturn(new String[]{ "host-org" });
      SecurityOrganization request = newOrgRequest("sales", "Sales");
      request.setMemberUsers(List.of("alice"));
      request.setMemberGroups(List.of("staff"));
      request.setRoles(List.of("analyst"));

      assertThrows(inetsoft.util.MessageException.class,
                   () -> service.createOrganization(request, null, principal));

      verify(editableProvider, never()).addOrganization(any());
      verify(editableProvider, never()).addUser(any());
      verify(editableProvider, never()).addGroup(any());
      verify(editableProvider, never()).addRole(any());
      sUtilStatic.verify(() -> SUtil.setPassword(any(FSUser.class), any()), never());
   }

   @Test
   void createOrganization_memberUsers_weakDefaultPassword_rejectedBeforeAnyWrite() {
      when(orgManager.isSiteAdmin(principal)).thenReturn(true);
      when(securityProvider.getOrganizationIDs()).thenReturn(new String[]{ "host-org" });
      SecurityOrganization request = newOrgRequest("sales", "Sales");
      request.setMemberUsers(List.of("alice"));
      request.setMemberGroups(List.of("staff"));
      request.setRoles(List.of("analyst"));
      request.setDefaultPassword("success123");

      assertThrows(inetsoft.util.MessageException.class,
                   () -> service.createOrganization(request, null, principal));

      verify(editableProvider, never()).addOrganization(any());
      verify(editableProvider, never()).addUser(any());
      verify(editableProvider, never()).addGroup(any());
      verify(editableProvider, never()).addRole(any());
      sUtilStatic.verify(() -> SUtil.setPassword(any(FSUser.class), anyString()), never());
   }

   @Test
   void createOrganization_memberUsers_weakDefaultPassword_noSuccessAuditRecorded() {
      // createOrganization's finally marks any created ActionRecord SUCCESS, so the password
      // check must run before the record exists or a rejected request is audited as a success
      when(orgManager.isSiteAdmin(principal)).thenReturn(true);
      when(securityProvider.getOrganizationIDs()).thenReturn(new String[]{ "host-org" });
      SecurityOrganization request = newOrgRequest("sales", "Sales");
      request.setMemberUsers(List.of("alice"));
      request.setDefaultPassword("success123");

      assertThrows(inetsoft.util.MessageException.class,
                   () -> service.createOrganization(request, null, principal));

      // only a SUCCESS record is wrong here; a future FAILURE record on rejection is legitimate
      verify(Audit.getInstance(), never()).auditAction(
         argThat(r -> r != null && ActionRecord.ACTION_STATUS_SUCCESS.equals(r.getActionStatus())),
         any());
      // no organization was created, so no identity-info (create) record may exist at all
      verify(Audit.getInstance(), never()).auditIdentityInfo(any(), any());
   }

   @Test
   void createOrganization_memberUsers_strongDefaultPassword_membersGetThatPassword()
      throws Exception
   {
      when(orgManager.isSiteAdmin(principal)).thenReturn(true);
      when(securityProvider.getOrganizationIDs()).thenReturn(new String[]{ "host-org" });
      SecurityOrganization request = newOrgRequest("sales", "Sales");
      request.setMemberUsers(List.of("alice", "bob"));
      request.setDefaultPassword("Str0ng!Passw0rd");

      service.createOrganization(request, null, principal);

      verify(editableProvider).addOrganization(any());
      verify(editableProvider, times(2)).addUser(any());
      sUtilStatic.verify(() -> SUtil.setPassword(any(FSUser.class), eq("Str0ng!Passw0rd")), times(2));
      sUtilStatic.verify(() -> SUtil.setPassword(any(FSUser.class), eq("success123")), never());
   }

   @Test
   void createOrganization_emptyMemberUsers_noDefaultPassword_proceeds() throws Exception {
      // Bug #77207: defaultPassword stays optional when no member user is created
      when(orgManager.isSiteAdmin(principal)).thenReturn(true);
      when(securityProvider.getOrganizationIDs()).thenReturn(new String[]{ "host-org" });
      SecurityOrganization request = newOrgRequest("sales", "Sales");
      request.setMemberUsers(List.of());

      service.createOrganization(request, null, principal);

      verify(editableProvider).addOrganization(any());
      verify(editableProvider, never()).addUser(any());
   }

   // ── organization name/id namespace (Bug #77082) ─────────────────────────
   //
   // Org names and ids share one case-insensitive namespace (bare SECURITY_ORGANIZATION keys are
   // sometimes ids and sometimes names), but updateOrganization had no name check at all and
   // never compared a name with another org's id (or vice versa), so an org admin could rename
   // their org to another org's id or name. updateOrganization wraps the refusal in
   // PreMutationRefusalException (Bug #76855), with the ResourceExistsException as its cause;
   // createOrganization throws the ResourceExistsException directly.

   private FSOrganization stubOrgsForUpdate() {
      FSOrganization org1 = new FSOrganization("org1");
      org1.setName("Org One");
      FSOrganization org2 = new FSOrganization("org2");
      org2.setName("acme");
      when(securityProvider.checkPermission(principal, ResourceType.SECURITY_ORGANIZATION,
                                            "org1", ResourceAction.ADMIN))
         .thenReturn(true);
      when(securityProvider.getOrganization("org1")).thenReturn(org1);
      when(securityProvider.getOrganization("org2")).thenReturn(org2);
      doReturn(org1).when(editableProvider).getOrganization("org1");
      when(securityProvider.getOrganizationIDs()).thenReturn(new String[]{ "host-org", "org1", "org2" });
      // an accepted rename re-keys the org's self grant in its own bucket (Bug #77206)
      stubAuthz();
      return org1;
   }

   private ResourceExistsException assertUpdateOrganizationRefusedAsExisting(
      String id, SecurityOrganization request)
   {
      SecurityService.PreMutationRefusalException thrown = assertThrows(
         SecurityService.PreMutationRefusalException.class,
         () -> service.updateOrganization(id, request, principal));
      return assertInstanceOf(ResourceExistsException.class, thrown.getCause());
   }

   @Test
   void updateOrganization_renameToAnotherOrgIdIgnoringCase_rejected() throws Exception {
      stubOrgsForUpdate();

      ResourceExistsException thrown =
         assertUpdateOrganizationRefusedAsExisting("org1", newOrgRequest("org1", "ORG2"));

      assertEquals("ORG2", thrown.getMessage());
      verify(identityService, never()).setIdentity(any(), any(), any(), any());
   }

   @Test
   void updateOrganization_renameToAnotherOrgNameIgnoringCase_rejected() throws Exception {
      stubOrgsForUpdate();

      ResourceExistsException thrown =
         assertUpdateOrganizationRefusedAsExisting("org1", newOrgRequest("org1", "Acme"));

      assertEquals("Acme", thrown.getMessage());
      verify(identityService, never()).setIdentity(any(), any(), any(), any());
   }

   @Test
   void updateOrganization_idChangedToAnotherOrgNameIgnoringCase_rejected() throws Exception {
      stubOrgsForUpdate();

      ResourceExistsException thrown =
         assertUpdateOrganizationRefusedAsExisting("org1", newOrgRequest("ACME", "Org One"));

      assertEquals("ACME", thrown.getMessage());
      verify(identityService, never()).setIdentity(any(), any(), any(), any());
   }

   @Test
   void updateOrganization_nameEqualToOwnId_accepted() throws Exception {
      FSOrganization org1 = stubOrgsForUpdate();

      service.updateOrganization("org1", newOrgRequest("org1", "org1"), principal);

      verify(identityService).setIdentity(eq(org1), any(EditOrganizationPaneModel.class),
                                          eq(editableProvider), eq(principal));
   }

   @Test
   void updateOrganization_existingNameCollision_unchangedNameStillEditable() throws Exception {
      // pre-existing data: org1's name already equals org2's id; saving with that name unchanged
      // (only the id changes) must not be rejected
      FSOrganization org1 = stubOrgsForUpdate();
      org1.setName("org2");

      service.updateOrganization("org1", newOrgRequest("org1b", "org2"), principal);

      verify(identityService).setIdentity(eq(org1), any(EditOrganizationPaneModel.class),
                                          eq(editableProvider), eq(principal));
   }

   @Test
   void updateOrganization_caseOnlyIdChangeOntoCaseVariantTwin_rejected() throws Exception {
      // pre-existing twins "host-org" and "HOST-ORG": changing HOST-ORG's id to "host-org"
      // would copyOrganization(replace=true) into the existing default org. HOST-ORG is not the
      // caller's own org, so only a site admin may edit it (Bug #77216)
      when(orgManager.isSiteAdmin(principal)).thenReturn(true);
      FSOrganization variant = new FSOrganization("HOST-ORG");
      variant.setName("Case Variant");
      when(securityProvider.checkPermission(principal, ResourceType.SECURITY_ORGANIZATION,
                                            "HOST-ORG", ResourceAction.ADMIN))
         .thenReturn(true);
      when(securityProvider.getOrganization("HOST-ORG")).thenReturn(variant);
      doReturn(variant).when(editableProvider).getOrganization("HOST-ORG");
      when(securityProvider.getOrganizationIDs()).thenReturn(new String[]{ "host-org", "HOST-ORG" });

      ResourceExistsException thrown = assertUpdateOrganizationRefusedAsExisting(
         "HOST-ORG", newOrgRequest("host-org", "Case Variant"));

      assertEquals("host-org", thrown.getMessage());
      verify(identityService, never()).setIdentity(any(), any(), any(), any());
   }

   @Test
   void updateOrganization_caseOnlyRenameOntoAnotherOrgsExactName_rejected() throws Exception {
      // pre-existing case-insensitive name collision: org1 "ACME" vs org2 "acme"
      FSOrganization org1 = stubOrgsForUpdate();
      org1.setName("ACME");

      ResourceExistsException thrown =
         assertUpdateOrganizationRefusedAsExisting("org1", newOrgRequest("org1", "acme"));

      assertEquals("acme", thrown.getMessage());
      verify(identityService, never()).setIdentity(any(), any(), any(), any());
   }

   @Test
   void updateOrganization_caseOnlyRenameOfOwnNameAndId_noTwin_accepted() throws Exception {
      FSOrganization org1 = stubOrgsForUpdate();

      service.updateOrganization("org1", newOrgRequest("ORG1", "ORG ONE"), principal);

      verify(identityService).setIdentity(eq(org1), any(EditOrganizationPaneModel.class),
                                          eq(editableProvider), eq(principal));
   }

   @Test
   void createOrganization_nameEqualToExistingOrgIdIgnoringCase_rejected() {
      when(orgManager.isSiteAdmin(principal)).thenReturn(true);
      when(securityProvider.getOrganizationIDs()).thenReturn(new String[]{ "host-org", "org1" });

      ResourceExistsException thrown = assertThrows(ResourceExistsException.class,
         () -> service.createOrganization(newOrgRequest("neworg", "ORG1"), null, principal));

      assertEquals("ORG1", thrown.getMessage());
      verify(editableProvider, never()).addOrganization(any());
   }

   @Test
   void createOrganization_idEqualToExistingOrgNameIgnoringCase_rejected() {
      FSOrganization org1 = new FSOrganization("org1");
      org1.setName("acme");
      when(orgManager.isSiteAdmin(principal)).thenReturn(true);
      when(securityProvider.getOrganizationIDs()).thenReturn(new String[]{ "host-org", "org1" });
      when(securityProvider.getOrganization("org1")).thenReturn(org1);

      ResourceExistsException thrown = assertThrows(ResourceExistsException.class,
         () -> service.createOrganization(newOrgRequest("ACME", "New Org"), "org1", principal));

      assertEquals("ACME", thrown.getMessage());
      verifyNoInteractions(userTreeService);
   }

   // ── updateOrganization member lists (Bug #77228) ────────────────────────
   //
   // updateOrganization guarded the memberGroups list on getMemberUsers() == null, so a body
   // with memberUsers but no memberGroups threw an NPE, and a body with memberGroups but no
   // memberUsers silently dropped the groups. A body that omits both lists keeps the current
   // members (Bug #77093), which are empty here.

   @Test
   void updateOrganization_memberUsersWithoutMemberGroups_updatesUsers() throws Exception {
      FSOrganization org1 = stubOrgsForUpdate();
      when(securityProvider.checkPermission(eq(principal), eq(ResourceType.SECURITY_USER),
                                            anyString(), eq(ResourceAction.ADMIN)))
         .thenReturn(true);
      SecurityOrganization request = newOrgRequest("org1", "Org One");
      request.setMemberUsers(List.of("u1"));

      service.updateOrganization("org1", request, principal);

      ArgumentCaptor<EditOrganizationPaneModel> model =
         ArgumentCaptor.forClass(EditOrganizationPaneModel.class);
      verify(identityService).setIdentity(eq(org1), model.capture(), eq(editableProvider),
                                          eq(principal));
      assertEquals(List.of(IdentityModel.builder().identityID(new IdentityID("u1", "org1"))
                              .type(Identity.USER).build()),
                   model.getValue().members());
   }

   @Test
   void updateOrganization_memberGroupsWithoutMemberUsers_keepsGroups() throws Exception {
      FSOrganization org1 = stubOrgsForUpdate();
      when(securityProvider.checkPermission(eq(principal), eq(ResourceType.SECURITY_GROUP),
                                            anyString(), eq(ResourceAction.ADMIN)))
         .thenReturn(true);
      SecurityOrganization request = newOrgRequest("org1", "Org One");
      request.setMemberGroups(List.of("g1"));

      service.updateOrganization("org1", request, principal);

      ArgumentCaptor<EditOrganizationPaneModel> model =
         ArgumentCaptor.forClass(EditOrganizationPaneModel.class);
      verify(identityService).setIdentity(eq(org1), model.capture(), eq(editableProvider),
                                          eq(principal));
      assertEquals(List.of(IdentityModel.builder().identityID(new IdentityID("g1", "org1"))
                              .type(Identity.GROUP).build()),
                   model.getValue().members());
   }

   @Test
   void updateOrganization_memberUsersAndMemberGroupsOmitted_emptyMembers() throws Exception {
      FSOrganization org1 = stubOrgsForUpdate();
      SecurityOrganization request = newOrgRequest("org1", "Org One");

      service.updateOrganization("org1", request, principal);

      ArgumentCaptor<EditOrganizationPaneModel> model =
         ArgumentCaptor.forClass(EditOrganizationPaneModel.class);
      verify(identityService).setIdentity(eq(org1), model.capture(), eq(editableProvider),
                                          eq(principal));
      assertEquals(List.of(), model.getValue().members());
   }

   // ── createOrganization / updateOrganization org id case (Bug #76995) ────
   //
   // Org ids are case-insensitive system-wide (lowercased storage buckets and org-scoped
   // properties, case-insensitive org-boundary and ACL identity checks), but the public API only
   // did an exact duplicate-id lookup on create and no duplicate-id check at all on update, so an
   // org "HOST-ORG" could be created alongside (or renamed next to) "host-org". The clone branch
   // also passed id/name to UserTreeService.createOrganization in swapped order. The id check is
   // now part of checkOrganizationIdentityConflict (Bug #77082).

   @Test
   void createOrganization_caseVariantOfExistingOrgId_rejected() {
      when(orgManager.isSiteAdmin(principal)).thenReturn(true);
      when(securityProvider.getOrganizationIDs()).thenReturn(new String[]{ "host-org", "org1" });

      ResourceExistsException thrown = assertThrows(ResourceExistsException.class,
                   () -> service.createOrganization(newOrgRequest("HOST-ORG", "Case Variant"),
                                                    null, principal));

      // the id collided, so the message names the id, not the (free) requested name
      assertEquals("HOST-ORG", thrown.getMessage());
      verify(editableProvider, never()).addOrganization(any());
   }

   @Test
   void createOrganization_nonCollidingId_plainCreateProceeds()
      throws Exception
   {
      // control: an id that no existing org has (ignoring case) passes the id check and the
      // plain (non-clone) create proceeds
      when(orgManager.isSiteAdmin(principal)).thenReturn(true);
      when(securityProvider.getOrganizationIDs()).thenReturn(new String[]{ "host-org", "org2" });

      service.createOrganization(newOrgRequest("Sales", "Sales West"), null, principal);

      verify(editableProvider).addOrganization(any());
   }

   @Test
   void createOrganization_cloneOfCaseVariantOrgId_rejectedBeforeClone() {
      when(orgManager.isSiteAdmin(principal)).thenReturn(true);
      when(securityProvider.getOrganizationIDs()).thenReturn(new String[]{ "host-org", "org1" });

      assertThrows(ResourceExistsException.class,
                   () -> service.createOrganization(newOrgRequest("HOST-ORG", "Case Variant"),
                                                    "org1", principal));

      verifyNoInteractions(userTreeService);
   }

   @Test
   void createOrganization_clone_passesNameThenIdToUserTreeService() throws Exception {
      when(orgManager.isSiteAdmin(principal)).thenReturn(true);
      when(securityProvider.getOrganizationIDs()).thenReturn(new String[]{ "host-org", "org1" });
      doReturn("Primary").when(editableProvider).getProviderName();
      SecurityOrganization request = newOrgRequest("neworg", "New Org");
      request.setDefaultPassword("Str0ng!Passw0rd");

      service.createOrganization(request, "org1", principal);

      // signature: createOrganization(copyFromOrgID, providerName, orgName, orgID, principal, pwd)
      verify(userTreeService).createOrganization("org1", "Primary", "New Org", "neworg",
                                                 principal, "Str0ng!Passw0rd");
   }

   @Test
   void updateOrganization_renameToCaseVariantOfAnotherOrgId_rejected() throws Exception {
      FSOrganization org1 = new FSOrganization("org1");
      org1.setName("Org One");
      when(securityProvider.checkPermission(principal, ResourceType.SECURITY_ORGANIZATION,
                                            "org1", ResourceAction.ADMIN))
         .thenReturn(true);
      when(securityProvider.getOrganization("org1")).thenReturn(org1);
      doReturn(org1).when(editableProvider).getOrganization("org1");
      when(securityProvider.getOrganizationIDs()).thenReturn(new String[]{ "host-org", "org1" });

      // updateOrganization wraps the refusal (Bug #76855), keeping ResourceExistsException as cause
      assertUpdateOrganizationRefusedAsExisting("org1", newOrgRequest("Host-Org", "Org One"));

      verify(identityService, never()).setIdentity(any(), any(), any(), any());
   }

   @Test
   void updateOrganization_renameToExactDuplicateOfAnotherOrgId_rejected() throws Exception {
      // before #76995 the REST update path had no duplicate-id check at all, not even exact
      FSOrganization org1 = new FSOrganization("org1");
      org1.setName("Org One");
      when(securityProvider.checkPermission(principal, ResourceType.SECURITY_ORGANIZATION,
                                            "org1", ResourceAction.ADMIN))
         .thenReturn(true);
      when(securityProvider.getOrganization("org1")).thenReturn(org1);
      doReturn(org1).when(editableProvider).getOrganization("org1");
      when(securityProvider.getOrganizationIDs()).thenReturn(new String[]{ "host-org", "org1", "org2" });

      ResourceExistsException thrown =
         assertUpdateOrganizationRefusedAsExisting("org1", newOrgRequest("org2", "Org One"));

      assertEquals("org2", thrown.getMessage());
      verify(identityService, never()).setIdentity(any(), any(), any(), any());
   }

   @Test
   void updateOrganization_idUnchanged_notRejectedEvenIfCaseVariantPairAlreadyExists()
      throws Exception
   {
      // a pre-existing case-variant pair (created before the fix) must stay editable by a site
      // admin, the only one who manages an org other than their own (Bug #77216)
      when(orgManager.isSiteAdmin(principal)).thenReturn(true);
      FSOrganization variant = new FSOrganization("HOST-ORG");
      variant.setName("Case Variant");
      when(securityProvider.checkPermission(principal, ResourceType.SECURITY_ORGANIZATION,
                                            "HOST-ORG", ResourceAction.ADMIN))
         .thenReturn(true);
      when(securityProvider.getOrganization("HOST-ORG")).thenReturn(variant);
      doReturn(variant).when(editableProvider).getOrganization("HOST-ORG");
      when(securityProvider.getOrganizationIDs()).thenReturn(new String[]{ "host-org", "HOST-ORG" });

      service.updateOrganization("HOST-ORG", newOrgRequest("HOST-ORG", "Case Variant"), principal);

      verify(identityService).setIdentity(eq(variant), any(EditOrganizationPaneModel.class),
                                          eq(editableProvider), eq(principal));
   }

   // ── system administrator parent groups (Bug #77073) ─────────────────────
   //
   // Group membership set through the public API is written to the provider directly, so it
   // bypasses the system administrator grant check in IdentityService.setIdentity(). The
   // SECURITY_GROUP ADMIN check alone does not stop an org admin from adding an identity under a
   // group whose ancestor grants system administrator (or, for a caller holding the org or
   // "Groups" root ADMIN grant, under the administrator group itself).

   private void stubParentGroupGrantsSystemAdmin(String group) {
      stubParentGroupExists(group);
      doThrow(new java.lang.SecurityException("grants system administrator"))
         .when(identityService).checkSystemAdminParentGroup(group, "org1", principal);
   }

   // createUser/createGroup only keep a requested parent group that exists
   // (filterPermittedIds's authenticationProvider.getGroup() check), so the group must resolve
   // for the system administrator check to be reached at all
   private void stubParentGroupExists(String group) {
      IdentityID groupId = new IdentityID(group, "org1");
      doReturn(new FSGroup(groupId)).when(authenticationProvider).getGroup(groupId);
   }

   @Test
   void createUser_parentGroupGrantsSystemAdmin_rejectedBeforeUserAdded() throws Exception {
      stubCommonCreateGates();
      stubParentGroupGrantsSystemAdmin("admins");

      SecurityUser request = new SecurityUser();
      request.setIdentityID(new IdentityID("escalated", "org1"));
      request.setPassword("Str0ng!Passw0rd");
      request.setGroups(List.of("admins"));

      assertThrows(UnauthorizedAccessException.class,
                   () -> service.createUser(request, null, principal));
      verify(editableProvider, never()).addUser(any());
   }

   @Test
   void createUser_ordinaryParentGroup_isChecked_andUserAddedToGroup() throws Exception {
      stubCommonCreateGates();
      stubParentGroupExists("plain");
      when(editableProvider.getOrganization("org1")).thenReturn(new FSOrganization("org1"));

      SecurityUser request = new SecurityUser();
      request.setIdentityID(new IdentityID("newuser1", "org1"));
      request.setPassword("Str0ng!Passw0rd");
      request.setGroups(List.of("plain"));

      service.createUser(request, null, principal);

      verify(identityService).checkSystemAdminParentGroup("plain", "org1", principal);
      ArgumentCaptor<FSUser> captor = ArgumentCaptor.forClass(FSUser.class);
      verify(editableProvider).addUser(captor.capture());
      assertEquals(List.of("plain"), List.of(captor.getValue().getGroups()));
   }

   @Test
   void createGroup_parentGroupGrantsSystemAdmin_rejectedBeforeAnyWrite() throws Exception {
      stubCommonCreateGates();
      stubParentGroupGrantsSystemAdmin("admins");

      SecurityGroup request = new SecurityGroup();
      request.setIdentityID(new IdentityID("sneaky", "org1"));
      request.setOrgID("org1");
      request.setParentGroups(List.of("admins"));
      request.setMemberUsers(List.of("caller"));

      assertThrows(UnauthorizedAccessException.class,
                   () -> service.createGroup(request, null, principal));
      verify(editableProvider, never()).addGroup(any());
      verify(editableProvider, never()).setUser(any(), any());
      verify(editableProvider, never()).setGroup(any(), any());
   }

   @Test
   void updateGroup_newParentGroupGrantsSystemAdmin_rejectedBeforeAnyWrite() throws Exception {
      IdentityID groupId = new IdentityID("plain", "org1");
      FSGroup oldGroup = new FSGroup(groupId);
      when(securityProvider.getGroup(groupId)).thenReturn(oldGroup);
      when(securityProvider.checkPermission(eq(principal), eq(ResourceType.SECURITY_GROUP),
                                            anyString(), eq(ResourceAction.ADMIN)))
         .thenReturn(true);
      when(editableProvider.getGroup(groupId)).thenReturn(oldGroup);
      stubParentGroupGrantsSystemAdmin("admins");

      SecurityGroup request = new SecurityGroup();
      request.setIdentityID(groupId);
      request.setParentGroups(List.of("admins"));

      // updateGroup wraps pre-mutation refusals (Bug #76855), keeping the original as the cause
      SecurityService.PreMutationRefusalException thrown = assertThrows(
         SecurityService.PreMutationRefusalException.class,
         () -> service.updateGroup(groupId, request, principal));
      assertInstanceOf(UnauthorizedAccessException.class, thrown.getCause());
      verify(identityService, never()).setIdentity(any(), any(), any(), any());
      verify(editableProvider, never()).setGroup(any(), any());
   }

   @Test
   void updateGroup_existingParentGroupNotRechecked_updateProceeds() throws Exception {
      IdentityID groupId = new IdentityID("plain", "org1");
      FSGroup oldGroup = new FSGroup(groupId);
      oldGroup.setGroups(new String[]{ "admins" });
      when(securityProvider.getGroup(groupId)).thenReturn(oldGroup);
      when(securityProvider.checkPermission(principal, ResourceType.SECURITY_GROUP,
                                            groupId.convertToKey(), ResourceAction.ADMIN))
         .thenReturn(true);
      when(editableProvider.getGroup(groupId)).thenReturn(oldGroup);
      stubParentGroupGrantsSystemAdmin("admins");

      SecurityGroup request = new SecurityGroup();
      request.setIdentityID(groupId);
      request.setParentGroups(List.of("admins"));

      service.updateGroup(groupId, request, principal);

      verify(identityService, never()).checkSystemAdminParentGroup(eq("admins"), any(), any());
      verify(identityService).setIdentity(eq(oldGroup), any(EditGroupPaneModel.class),
                                          eq(editableProvider), eq(principal));
   }

   // ── role inheriting a system administrator role (Bug #77199) ────────────
   //
   // filterSystemAdminRoles only dropped a role that is itself a system administrator role, so
   // a non-site-admin could still grant site-wide administrator privileges by assigning an org
   // role that inherits the Administrator role.

   @Test
   void createUser_nonSiteAdmin_filtersRoleInheritingAdministrator() throws Exception {
      stubCommonCreateGates();
      when(editableProvider.getOrganization("org1")).thenReturn(new FSOrganization("org1"));
      IdentityID inheritsAdmin = new IdentityID("inheritsAdmin", "org1");
      FSRole inheritsAdminRole = new FSRole(inheritsAdmin);
      inheritsAdminRole.setRoles(new IdentityID[]{ ADMIN_ROLE });
      when(editableProvider.getRole(inheritsAdmin)).thenReturn(inheritsAdminRole);
      doReturn(inheritsAdminRole).when(authenticationProvider).getRole(inheritsAdmin);

      SecurityUser request = new SecurityUser();
      request.setIdentityID(new IdentityID("newuser1", "org1"));
      request.setPassword("Str0ng!Passw0rd");
      request.setRoles(List.of(inheritsAdmin, VIEWER_ROLE));

      service.createUser(request, null, principal);

      ArgumentCaptor<FSUser> captor = ArgumentCaptor.forClass(FSUser.class);
      verify(editableProvider).addUser(captor.capture());
      assertEquals(List.of(VIEWER_ROLE), List.of(captor.getValue().getRoles()));
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
      // only a site admin manages an org other than their own (Bug #77216)
      when(orgManager.isSiteAdmin(principal)).thenReturn(true);
      stubDeletableOrganization(orgId);

      Exception ex = assertThrows(Exception.class, () -> service.deleteOrganization(orgId, principal));
      assertTrue(ex.getMessage().contains("default organization"));

      verify(identityService, never()).deleteIdentities(any(), any(), any());
   }

   @Test
   void deleteOrganization_selfOrg_refused() throws Exception {
      String orgId = Organization.getSelfOrganizationID();
      // only a site admin manages an org other than their own (Bug #77216)
      when(orgManager.isSiteAdmin(principal)).thenReturn(true);
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
      // only a site admin manages an org other than their own (Bug #77216)
      when(orgManager.isSiteAdmin(principal)).thenReturn(true);
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
      // a grant is written for the caller's current org only (Bug #77274)
      when(orgManager.getCurrentOrgID()).thenReturn("org1");
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
      // a grant is written for the caller's current org only (Bug #77274)
      when(orgManager.getCurrentOrgID()).thenReturn("org1");
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


   // ── ported from the enterprise SecurityApiServiceTest on main ──────────────
   //
   // On this branch the public REST security API logic lives here, and the enterprise
   // SecurityApiService only delegates to it, so main's SecurityApiServiceTest additions are
   // ported to this class.

   // ── Bug #77303: an omitted role/group member list keeps the current members ─
   //
   // setIdentity() replaces the members with the ones in the model, so a REST update that omits
   // memberUsers/memberGroups (group) or assignedUsers/assignedGroups (role) removed the group or
   // role from every current member. None of the members below is permitted to the caller, so a
   // kept member can only come from the provider, not from the permission-filtered request list.

   private static final IdentityID MEMBER_USER = new IdentityID("m1", "org2");
   private static final IdentityID OTHER_USER = new IdentityID("m2", "org2");
   private static final IdentityID OTHER_ORG_USER = new IdentityID("m1", "org3");
   private static final IdentityID MEMBER_GROUP = new IdentityID("sub1", "org2");
   private static final IdentityID OTHER_GROUP = new IdentityID("sub2", "org2");

   private FSUser providerUser(IdentityID id, IdentityID[] roles, String... groups) {
      FSUser user = new FSUser(id);
      user.setRoles(roles);
      user.setGroups(groups);
      doReturn(user).when(editableProvider).getUser(id);
      return user;
   }

   private FSGroup providerGroup(IdentityID id, IdentityID[] roles, String... groups) {
      FSGroup group = new FSGroup(id);
      group.setRoles(roles);
      group.setGroups(groups);
      doReturn(group).when(editableProvider).getGroup(id);
      return group;
   }

   // sales@org2 has the direct members m1@org2 and sub1@org2; m1@org3 belongs to a same-named
   // group of another org
   private FSGroup stubSalesGroupMembers() {
      FSGroup oldGroup = stubUpdatableGroup(SALES_GROUP);
      providerUser(MEMBER_USER, new IdentityID[0], "sales");
      providerUser(OTHER_USER, new IdentityID[0], "other");
      providerUser(OTHER_ORG_USER, new IdentityID[0], "sales");
      providerGroup(MEMBER_GROUP, new IdentityID[0], "sales");
      providerGroup(OTHER_GROUP, new IdentityID[0]);
      doReturn(new IdentityID[] { MEMBER_USER, OTHER_USER, OTHER_ORG_USER })
         .when(editableProvider).getUsers();
      doReturn(new IdentityID[] { SALES_GROUP, MEMBER_GROUP, OTHER_GROUP })
         .when(editableProvider).getGroups();
      return oldGroup;
   }

   // analyst@org2 is assigned directly to m1@org2 and sub1@org2
   private FSRole stubAnalystRoleAssignees() {
      FSRole oldRole = stubUpdatableRole(ANALYST_ROLE);
      providerUser(MEMBER_USER, new IdentityID[] { ANALYST_ROLE });
      providerUser(OTHER_USER, new IdentityID[] { VIEWER_ROLE });
      providerGroup(MEMBER_GROUP, new IdentityID[] { VIEWER_ROLE, ANALYST_ROLE });
      providerGroup(OTHER_GROUP, new IdentityID[0]);
      doReturn(new IdentityID[] { MEMBER_USER, OTHER_USER }).when(editableProvider).getUsers();
      doReturn(new IdentityID[] { MEMBER_GROUP, OTHER_GROUP }).when(editableProvider).getGroups();
      return oldRole;
   }

   private static Set<String> memberKeys(List<IdentityModel> members) {
      Set<String> keys = new HashSet<>();

      for(IdentityModel member : members) {
         assertTrue(keys.add(member.type() + ":" + member.identityID().convertToKey()),
                    "duplicate member " + member.identityID());
      }

      return keys;
   }

   private static String userKey(IdentityID id) {
      return Identity.USER + ":" + id.convertToKey();
   }

   private static String groupKey(IdentityID id) {
      return Identity.GROUP + ":" + id.convertToKey();
   }

   private Set<String> captureGroupMembers(FSGroup oldGroup) throws Exception {
      ArgumentCaptor<EditGroupPaneModel> captor = ArgumentCaptor.forClass(EditGroupPaneModel.class);
      verify(identityService).setIdentity(eq(oldGroup), captor.capture(), eq(editableProvider), eq(principal));
      return memberKeys(captor.getValue().members());
   }

   private Set<String> captureRoleMembers(FSRole oldRole) throws Exception {
      ArgumentCaptor<EditRolePaneModel> captor = ArgumentCaptor.forClass(EditRolePaneModel.class);
      verify(identityService).setIdentity(eq(oldRole), captor.capture(), eq(editableProvider), eq(principal));
      return memberKeys(captor.getValue().members());
   }

   @Test
   void updateGroup_memberListsNull_keepsCurrentMembers() throws Exception {
      FSGroup oldGroup = stubSalesGroupMembers();

      service.updateGroup(SALES_GROUP, groupRequest(SALES_GROUP), principal);

      assertEquals(Set.of(userKey(MEMBER_USER), groupKey(MEMBER_GROUP)),
                   captureGroupMembers(oldGroup));
   }

   @Test
   void updateGroup_memberUsersNull_keepsUsers_emptyMemberGroupsClearsGroups() throws Exception {
      FSGroup oldGroup = stubSalesGroupMembers();
      SecurityGroup request = groupRequest(SALES_GROUP);
      request.setMemberGroups(List.of());

      service.updateGroup(SALES_GROUP, request, principal);

      assertEquals(Set.of(userKey(MEMBER_USER)), captureGroupMembers(oldGroup));
   }

   @Test
   void updateGroup_memberGroupsNull_keepsGroups_emptyMemberUsersClearsUsers() throws Exception {
      FSGroup oldGroup = stubSalesGroupMembers();
      SecurityGroup request = groupRequest(SALES_GROUP);
      request.setMemberUsers(List.of());

      service.updateGroup(SALES_GROUP, request, principal);

      assertEquals(Set.of(groupKey(MEMBER_GROUP)), captureGroupMembers(oldGroup));
   }

   @Test
   void updateGroup_emptyMemberLists_stillClearMembers() throws Exception {
      FSGroup oldGroup = stubSalesGroupMembers();
      SecurityGroup request = groupRequest(SALES_GROUP);
      request.setMemberUsers(List.of());
      request.setMemberGroups(List.of());

      service.updateGroup(SALES_GROUP, request, principal);

      assertEquals(Set.of(), captureGroupMembers(oldGroup));
   }

   @Test
   void updateGroup_renamedWithNullMemberLists_keepsMembersOfOldName() throws Exception {
      FSGroup oldGroup = stubSalesGroupMembers();

      service.updateGroup(SALES_GROUP, groupRequest(new IdentityID("sales2", "org2")), principal);

      assertEquals(Set.of(userKey(MEMBER_USER), groupKey(MEMBER_GROUP)),
                   captureGroupMembers(oldGroup));
   }

   @Test
   void updateRole_assignedListsNull_keepsCurrentAssignees() throws Exception {
      FSRole oldRole = stubAnalystRoleAssignees();

      service.updateRole(ANALYST_ROLE, roleRequest(ANALYST_ROLE), principal);

      assertEquals(Set.of(userKey(MEMBER_USER), groupKey(MEMBER_GROUP)),
                   captureRoleMembers(oldRole));
   }

   @Test
   void updateRole_assignedUsersNull_keepsUsers_emptyAssignedGroupsClearsGroups() throws Exception {
      FSRole oldRole = stubAnalystRoleAssignees();
      SecurityRole request = roleRequest(ANALYST_ROLE);
      request.setAssignedGroups(List.of());

      service.updateRole(ANALYST_ROLE, request, principal);

      assertEquals(Set.of(userKey(MEMBER_USER)), captureRoleMembers(oldRole));
   }

   @Test
   void updateRole_assignedGroupsNull_keepsGroups_emptyAssignedUsersClearsUsers() throws Exception {
      FSRole oldRole = stubAnalystRoleAssignees();
      SecurityRole request = roleRequest(ANALYST_ROLE);
      request.setAssignedUsers(List.of());

      service.updateRole(ANALYST_ROLE, request, principal);

      assertEquals(Set.of(groupKey(MEMBER_GROUP)), captureRoleMembers(oldRole));
   }

   @Test
   void updateRole_emptyAssignedLists_stillClearAssignees() throws Exception {
      FSRole oldRole = stubAnalystRoleAssignees();
      SecurityRole request = roleRequest(ANALYST_ROLE);
      request.setAssignedUsers(List.of());
      request.setAssignedGroups(List.of());

      service.updateRole(ANALYST_ROLE, request, principal);

      assertEquals(Set.of(), captureRoleMembers(oldRole));
   }

   @Test
   void updateRole_renamedWithNullAssignedLists_keepsAssigneesOfOldId() throws Exception {
      FSRole oldRole = stubAnalystRoleAssignees();

      service.updateRole(ANALYST_ROLE, roleRequest(new IdentityID("analyst2", "org2")), principal);

      assertEquals(Set.of(userKey(MEMBER_USER), groupKey(MEMBER_GROUP)),
                   captureRoleMembers(oldRole));
   }

   @Test
   void updateRole_globalRoleAssignedListsNull_keepsAssigneesOfEveryOrg() throws Exception {
      // a site admin addresses a global role with ?orgId=__GLOBAL__; its assignees store the
      // role with a null org, and an org role of the same name is a different role
      IdentityID pathId = new IdentityID("gRole", GLOBAL_ORG_KEY);
      IdentityID globalRole = new IdentityID("gRole", null);
      FSRole oldRole = stubUpdatableRole(pathId, new FSRole(globalRole));
      IdentityID org1User = new IdentityID("u1", "org1");
      IdentityID org2User = new IdentityID("u2", "org2");
      IdentityID orgRoleUser = new IdentityID("u3", "org2");
      IdentityID org2Group = new IdentityID("g1", "org2");
      providerUser(org1User, new IdentityID[] { globalRole });
      providerUser(org2User, new IdentityID[] { globalRole });
      providerUser(orgRoleUser, new IdentityID[] { new IdentityID("gRole", "org2") });
      providerGroup(org2Group, new IdentityID[] { globalRole });
      doReturn(new IdentityID[] { org1User, org2User, orgRoleUser }).when(editableProvider).getUsers();
      doReturn(new IdentityID[] { org2Group }).when(editableProvider).getGroups();

      service.updateRole(pathId, roleRequest(pathId), principal);

      assertEquals(Set.of(userKey(org1User), userKey(org2User), groupKey(org2Group)),
                   captureRoleMembers(oldRole));
   }

   // ── Bug #77327: global role update/get/delete with the global org key ───
   //
   // The Shell local client (and CustomApiServices setup scripts / custom endpoints) address a
   // global role with the org "__GLOBAL__", the key form of its null org, and call the service
   // directly. updateRole passed the literal to setIdentity(), which then skipped every user and
   // group (they store the role with a null org) and re-stored the role with the literal org,
   // and mapped every listed name to a nonexistent name@__GLOBAL__ identity that was dropped.
   // With the org decoded to null, a listed bare name is resolved in the current org, a key
   // names another org's identity, other orgs' assignees are kept, and an unknown name is
   // rejected instead of being dropped (a dropped name would remove the role from it).

   private static final IdentityID GLOBAL_ROLE_PATH = new IdentityID("gRole", "__GLOBAL__");
   private static final IdentityID GLOBAL_ROLE = new IdentityID("gRole", null);
   private static final IdentityID G_ORG1_HOLDER = new IdentityID("u1", "org1");
   private static final IdentityID G_ORG1_OTHER = new IdentityID("u4", "org1");
   private static final IdentityID G_ORG2_HOLDER = new IdentityID("u2", "org2");
   private static final IdentityID G_ORG2_OTHER = new IdentityID("u5", "org2");
   private static final IdentityID G_ORG3_HOLDER = new IdentityID("u3", "org3");
   private static final IdentityID G_ORG1_GROUP = new IdentityID("g1", "org1");
   private static final IdentityID G_ORG2_GROUP = new IdentityID("g2", "org2");

   // the current org is org1; the global role is held by u1@org1, u2@org2, u3@org3, g1@org1 and
   // g2@org2, and u4@org1 and u5@org2 do not hold it
   private FSRole stubGlobalRoleAssignees() {
      FSRole oldRole = stubUpdatableRole(GLOBAL_ROLE_PATH, new FSRole(GLOBAL_ROLE));
      when(orgManager.getCurrentOrgID()).thenReturn("org1");
      when(orgManager.getCurrentOrgID(any())).thenReturn("org1");
      when(securityProvider.checkPermission(eq(principal), eq(ResourceType.SECURITY_USER),
                                            anyString(), eq(ResourceAction.ADMIN)))
         .thenReturn(true);
      when(securityProvider.checkPermission(eq(principal), eq(ResourceType.SECURITY_GROUP),
                                            anyString(), eq(ResourceAction.ADMIN)))
         .thenReturn(true);
      providerUser(G_ORG1_HOLDER, new IdentityID[] { GLOBAL_ROLE });
      providerUser(G_ORG1_OTHER, new IdentityID[0]);
      providerUser(G_ORG2_HOLDER, new IdentityID[] { GLOBAL_ROLE });
      providerUser(G_ORG2_OTHER, new IdentityID[0]);
      providerUser(G_ORG3_HOLDER, new IdentityID[] { GLOBAL_ROLE });
      providerGroup(G_ORG1_GROUP, new IdentityID[] { GLOBAL_ROLE });
      providerGroup(G_ORG2_GROUP, new IdentityID[] { GLOBAL_ROLE });
      doReturn(new IdentityID[] { G_ORG1_HOLDER, G_ORG1_OTHER, G_ORG2_HOLDER, G_ORG2_OTHER,
                                  G_ORG3_HOLDER })
         .when(editableProvider).getUsers();
      doReturn(new IdentityID[] { G_ORG1_GROUP, G_ORG2_GROUP }).when(editableProvider).getGroups();
      return oldRole;
   }

   private EditRolePaneModel captureRoleModel(FSRole oldRole) throws Exception {
      ArgumentCaptor<EditRolePaneModel> captor = ArgumentCaptor.forClass(EditRolePaneModel.class);
      verify(identityService).setIdentity(eq(oldRole), captor.capture(), eq(editableProvider),
                                          eq(principal));
      return captor.getValue();
   }

   @Test
   void updateRole_globalRoleKey_passesNullOrgToSetIdentity() throws Exception {
      FSRole oldRole = stubGlobalRoleAssignees();

      service.updateRole(GLOBAL_ROLE_PATH, roleRequest(GLOBAL_ROLE_PATH), principal);

      EditRolePaneModel model = captureRoleModel(oldRole);
      assertNull(model.organization());
      assertEquals("gRole", model.oldName());
   }

   @Test
   void updateRole_globalRoleKey_listedBareNamesAndKeysResolved_otherOrgsKept() throws Exception {
      FSRole oldRole = stubGlobalRoleAssignees();
      SecurityRole request = roleRequest(GLOBAL_ROLE_PATH);
      // u4 is a bare name of the current org1, u5~;~org2 names another org by key
      request.setAssignedUsers(List.of("u4", G_ORG2_OTHER.convertToKey()));
      request.setAssignedGroups(List.of("g1"));

      service.updateRole(GLOBAL_ROLE_PATH, request, principal);

      // org1 and org2 are listed, so their holders u1 and u2 are replaced; org3 is not listed,
      // so u3 keeps the role, as does g2@org2 because the group list names only org1
      assertEquals(Set.of(userKey(G_ORG1_OTHER), userKey(G_ORG2_OTHER), userKey(G_ORG3_HOLDER),
                          groupKey(G_ORG1_GROUP), groupKey(G_ORG2_GROUP)),
                   captureRoleMembers(oldRole));
   }

   @Test
   void updateRole_globalRoleKey_emptyLists_clearCurrentOrgOnly() throws Exception {
      FSRole oldRole = stubGlobalRoleAssignees();
      SecurityRole request = roleRequest(GLOBAL_ROLE_PATH);
      request.setAssignedUsers(List.of());
      request.setAssignedGroups(List.of());

      service.updateRole(GLOBAL_ROLE_PATH, request, principal);

      assertEquals(Set.of(userKey(G_ORG2_HOLDER), userKey(G_ORG3_HOLDER), groupKey(G_ORG2_GROUP)),
                   captureRoleMembers(oldRole));
   }

   @Test
   void updateRole_globalRoleKey_bareNameRoundTrip_keepsOtherOrgHolders() throws Exception {
      FSRole oldRole = stubGlobalRoleAssignees();
      SecurityRole request = roleRequest(GLOBAL_ROLE_PATH);
      request.setAssignedUsers(List.of("u1"));
      request.setAssignedGroups(List.of("g1"));

      service.updateRole(GLOBAL_ROLE_PATH, request, principal);

      assertEquals(Set.of(userKey(G_ORG1_HOLDER), userKey(G_ORG2_HOLDER), userKey(G_ORG3_HOLDER),
                          groupKey(G_ORG1_GROUP), groupKey(G_ORG2_GROUP)),
                   captureRoleMembers(oldRole));
   }

   @Test
   void updateRole_globalRoleKey_unresolvableName_rejectedWithoutChange() throws Exception {
      stubGlobalRoleAssignees();
      SecurityRole request = roleRequest(GLOBAL_ROLE_PATH);
      // u2 exists only in org2, so as a bare name of the current org1 it is not a user
      request.setAssignedUsers(List.of("u1", "u2"));

      inetsoft.web.security.auth.MissingResourceException e =
         assertRefusedBeforeMutation(inetsoft.web.security.auth.MissingResourceException.class,
                      () -> service.updateRole(GLOBAL_ROLE_PATH, request, principal));

      assertTrue(e.getMessage().contains("u2"), e.getMessage());
      verify(identityService, never()).setIdentity(any(), any(), any(), any());
   }

   @Test
   void updateRole_globalRoleKey_unresolvableGroupKey_rejected() throws Exception {
      stubGlobalRoleAssignees();
      SecurityRole request = roleRequest(GLOBAL_ROLE_PATH);
      request.setAssignedGroups(List.of(new IdentityID("g9", "org2").convertToKey()));

      assertRefusedBeforeMutation(inetsoft.web.security.auth.MissingResourceException.class,
                   () -> service.updateRole(GLOBAL_ROLE_PATH, request, principal));
      verify(identityService, never()).setIdentity(any(), any(), any(), any());
   }

   @Test
   void updateRole_orgRole_unknownBareName_stillDropped() throws Exception {
      // the org role path is unchanged: names are resolved in the role's org and filtered
      FSRole oldRole = stubAnalystRoleAssignees();
      SecurityRole request = roleRequest(ANALYST_ROLE);
      request.setAssignedUsers(List.of("nobody"));
      request.setAssignedGroups(List.of());

      service.updateRole(ANALYST_ROLE, request, principal);

      assertEquals(Set.of(), captureRoleMembers(oldRole));
   }

   @Test
   void updateRole_globalRoleKey_realSetRoleInfoAppliesAssigneesAndKeepsRoleGlobal()
      throws Exception
   {
      // runs the model updateRole builds through the real IdentityService.setRoleInfo(), since
      // identityService is a mock and only the model would otherwise be checked
      FSRole oldRole = stubGlobalRoleAssignees();
      SecurityRole request = roleRequest(GLOBAL_ROLE_PATH);
      request.setAssignedUsers(List.of("u4"));

      service.updateRole(GLOBAL_ROLE_PATH, request, principal);
      applyRoleModelWithRealSetRoleInfo(captureRoleModel(oldRole));

      assertArrayEquals(new IdentityID[] { GLOBAL_ROLE },
                        editableProvider.getUser(G_ORG1_OTHER).getRoles());
      assertArrayEquals(new IdentityID[0], editableProvider.getUser(G_ORG1_HOLDER).getRoles());
      assertArrayEquals(new IdentityID[] { GLOBAL_ROLE },
                        editableProvider.getUser(G_ORG2_HOLDER).getRoles());
      assertArrayEquals(new IdentityID[] { GLOBAL_ROLE },
                        editableProvider.getUser(G_ORG3_HOLDER).getRoles());
      assertArrayEquals(new IdentityID[] { GLOBAL_ROLE },
                        editableProvider.getGroup(G_ORG1_GROUP).getRoles());
      assertArrayEquals(new IdentityID[] { GLOBAL_ROLE },
                        editableProvider.getGroup(G_ORG2_GROUP).getRoles());
      ArgumentCaptor<Role> stored = ArgumentCaptor.forClass(Role.class);
      verify(editableProvider).setRole(any(), stored.capture());
      assertNull(stored.getValue().getOrganizationID());
   }

   @Test
   void updateRole_globalRoleKey_realSetRoleInfoKeyEntryReplacesOnlyListedOrgs() throws Exception {
      // a name~;~org entry names another org's user; through the real setRoleInfo() only the
      // current org1 and the listed org2 are replaced, and org3's holder keeps the role
      FSRole oldRole = stubGlobalRoleAssignees();
      SecurityRole request = roleRequest(GLOBAL_ROLE_PATH);
      request.setAssignedUsers(List.of(G_ORG2_OTHER.convertToKey()));
      request.setAssignedGroups(List.of());

      service.updateRole(GLOBAL_ROLE_PATH, request, principal);
      applyRoleModelWithRealSetRoleInfo(captureRoleModel(oldRole));

      assertArrayEquals(new IdentityID[] { GLOBAL_ROLE },
                        editableProvider.getUser(G_ORG2_OTHER).getRoles());
      assertArrayEquals(new IdentityID[0], editableProvider.getUser(G_ORG2_HOLDER).getRoles());
      assertArrayEquals(new IdentityID[0], editableProvider.getUser(G_ORG1_HOLDER).getRoles());
      assertArrayEquals(new IdentityID[0], editableProvider.getUser(G_ORG1_OTHER).getRoles());
      assertArrayEquals(new IdentityID[] { GLOBAL_ROLE },
                        editableProvider.getUser(G_ORG3_HOLDER).getRoles());
      assertArrayEquals(new IdentityID[0], editableProvider.getGroup(G_ORG1_GROUP).getRoles());
      assertArrayEquals(new IdentityID[] { GLOBAL_ROLE },
                        editableProvider.getGroup(G_ORG2_GROUP).getRoles());
   }

   /**
    * Applies a role model to the editable provider's users and groups with the real
    * IdentityService.setRoleInfo(), as IdentityService.setIdentity() does.
    */
   private void applyRoleModelWithRealSetRoleInfo(EditRolePaneModel model) throws Exception {
      IdentityService real =
         mock(IdentityService.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));
      SecurityEngine engine = mock(SecurityEngine.class);
      when(engine.isSecurityEnabled()).thenReturn(true);
      ReflectionTestUtils.setField(real, "securityProvider", securityProvider);
      ReflectionTestUtils.setField(real, "securityEngine", engine);
      ReflectionTestUtils.setField(real, "dashboardManager",
                                   mock(inetsoft.sree.web.dashboard.DashboardManager.class));
      ReflectionTestUtils.setField(real, "scheduleManager",
                                   mock(inetsoft.sree.schedule.ScheduleManager.class));
      ReflectionTestUtils.setField(real, "LOG", LoggerFactory.getLogger(IdentityService.class));
      List<IdentityID> userV = new ArrayList<>();
      List<IdentityID> groupV = new ArrayList<>();

      for(IdentityModel member : model.members()) {
         (member.type() == Identity.USER ? userV : groupV).add(member.identityID());
      }

      Method method = IdentityService.class.getDeclaredMethod(
         "setRoleInfo", EditRolePaneModel.class, EditableAuthenticationProvider.class,
         IdentityID[].class, IdentityID[].class, String[].class, List.class, List.class,
         List.class, Principal.class);
      method.setAccessible(true);
      method.invoke(real, model, editableProvider, editableProvider.getUsers(),
                    editableProvider.getGroups(), new String[0], userV, groupV,
                    new ArrayList<IdentityID>(), principal);
   }

   @Test
   void updateRole_globalRoleKeyRenamed_renamesRoleInThemes() throws Exception {
      stubGlobalRoleAssignees();
      when(customThemesManager.getCustomThemes())
         .thenReturn(new HashSet<>(List.of(groupRoleTheme("t1", List.of(), List.of("gRole")))));

      service.updateRole(GLOBAL_ROLE_PATH, roleRequest(new IdentityID("gRole2", null)), principal);

      @SuppressWarnings("unchecked")
      ArgumentCaptor<Set<CustomTheme>> captor = ArgumentCaptor.forClass(Set.class);
      verify(customThemesManager).setCustomThemes(captor.capture());
      assertEquals(List.of("gRole2"), captor.getValue().iterator().next().getRoles());
   }

   @Test
   void getRole_globalRoleKey_returnsNullOrgAndGlobalRoleTheme() throws Exception {
      FSRole globalRole = new FSRole(GLOBAL_ROLE, "global");
      when(securityProvider.checkPermission(principal, ResourceType.SECURITY_ROLE,
                                            GLOBAL_ROLE_PATH.convertToKey(), ResourceAction.ADMIN))
         .thenReturn(true);
      when(securityProvider.getRole(GLOBAL_ROLE_PATH)).thenReturn(globalRole);
      doReturn(new Identity[0]).when(authenticationProvider).getRoleMembers(GLOBAL_ROLE);
      when(identityService.getIdentityInfo(GLOBAL_ROLE_PATH, Identity.ROLE, securityProvider))
         .thenReturn(new IdentityInfo(globalRole, authenticationProvider));
      when(identityService.getPermission(any(), eq(ResourceType.SECURITY_ROLE), any(),
                                         eq(principal)))
         .thenReturn(List.of());
      when(customThemesManager.getCustomThemes())
         .thenReturn(new HashSet<>(List.of(groupRoleTheme("t1", List.of(), List.of("gRole")))));

      SecurityRole result = service.getRole(GLOBAL_ROLE_PATH, principal);

      assertEquals(GLOBAL_ROLE, result.getIdentityID());
      assertEquals("t1", result.getTheme());
   }

   @Test
   void deleteRole_globalRoleKey_deletesWithNullOrg() throws Exception {
      FSRole globalRole = new FSRole(GLOBAL_ROLE);
      when(securityProvider.checkPermission(principal, ResourceType.SECURITY_ROLE,
                                            GLOBAL_ROLE_PATH.convertToKey(), ResourceAction.ADMIN))
         .thenReturn(true);
      when(editableProvider.getRole(GLOBAL_ROLE_PATH)).thenReturn(globalRole);
      SystemAdminService systemAdminService = mock(SystemAdminService.class);
      when(systemAdminService.hasOrgAdminAfterDelete(any())).thenReturn(true);
      when(systemAdminService.hasSystemAdminAfterDelete(any())).thenReturn(true);
      when(identityService.deleteIdentities(any(), any(), any())).thenReturn(List.of());
      SecurityService deleteService = new SecurityService(
         SecurityEngine.getSecurity(), identityService, mock(ActionPermissionService.class),
         mock(LocalizationSettingsService.class), themeService, systemAdminService,
         userTreeService, customThemesManager);

      deleteService.deleteRole(GLOBAL_ROLE_PATH, principal);

      ArgumentCaptor<IdentityModel[]> captor = ArgumentCaptor.forClass(IdentityModel[].class);
      verify(identityService).deleteIdentities(captor.capture(), any(), eq(principal));
      assertEquals(GLOBAL_ROLE, captor.getValue()[0].identityID());
      assertNull(captor.getValue()[0].identityID().orgID);
   }

   // Bug #77186: the plain create keyed member users, groups and roles by the org *name*, so
   // they were created under a non-existent org id (or over another org's identities when the
   // name equaled that org's id), and add* silently replaced any existing identity.

   @Test
   void createOrganization_members_keyedByOrgIdNotName() throws Exception {
      when(orgManager.isSiteAdmin(principal)).thenReturn(true);
      when(securityProvider.getOrganizationIDs()).thenReturn(new String[]{ "host-org" });
      SecurityOrganization request = newOrgRequest("o2", "Org Two");
      request.setMemberUsers(List.of("bob"));
      request.setMemberGroups(List.of("staff"));
      request.setRoles(List.of("analyst"));
      request.setDefaultPassword("Str0ng!Passw0rd");

      service.createOrganization(request, null, principal);

      ArgumentCaptor<User> user = ArgumentCaptor.forClass(User.class);
      ArgumentCaptor<Group> group = ArgumentCaptor.forClass(Group.class);
      ArgumentCaptor<Role> role = ArgumentCaptor.forClass(Role.class);
      verify(editableProvider).addUser(user.capture());
      verify(editableProvider).addGroup(group.capture());
      verify(editableProvider).addRole(role.capture());
      assertEquals(new IdentityID("bob", "o2"), user.getValue().getIdentityID());
      assertEquals(new IdentityID("staff", "o2"), group.getValue().getIdentityID());
      assertEquals(new IdentityID("analyst", "o2"), role.getValue().getIdentityID());
   }

   @Test
   void createOrganization_existingMemberUser_rejectedBeforeAnyWrite() {
      when(orgManager.isSiteAdmin(principal)).thenReturn(true);
      when(securityProvider.getOrganizationIDs()).thenReturn(new String[]{ "host-org" });
      when(editableProvider.getUser(new IdentityID("alice", "new2")))
         .thenReturn(new FSUser(new IdentityID("alice", "new2")));
      SecurityOrganization request = newOrgRequest("new2", "New Two");
      request.setMemberUsers(List.of("alice"));
      request.setDefaultPassword("Str0ng!Passw0rd");

      ResourceExistsException thrown = assertThrows(ResourceExistsException.class,
         () -> service.createOrganization(request, null, principal));

      assertEquals("alice", thrown.getMessage());
      verify(editableProvider, never()).addOrganization(any());
      verify(editableProvider, never()).addUser(any());
      verify(Audit.getInstance(), never()).auditAction(any(), any());
   }

   @Test
   void createOrganization_existingMemberGroupOrRole_rejectedBeforeAnyWrite() {
      when(orgManager.isSiteAdmin(principal)).thenReturn(true);
      when(securityProvider.getOrganizationIDs()).thenReturn(new String[]{ "host-org" });
      when(editableProvider.getRole(new IdentityID("analyst", "new2")))
         .thenReturn(new FSRole(new IdentityID("analyst", "new2")));
      SecurityOrganization request = newOrgRequest("new2", "New Two");
      request.setMemberGroups(List.of("staff"));
      request.setRoles(List.of("analyst"));

      assertThrows(ResourceExistsException.class,
                   () -> service.createOrganization(request, null, principal));

      verify(editableProvider, never()).addOrganization(any());
      verify(editableProvider, never()).addGroup(any());
      verify(editableProvider, never()).addRole(any());
   }

   @Test
   void createOrganization_theme_matchesByNameAndNewOrgId() throws Exception {
      // the filter used to read (name && global) || orgID.equals(orgName): a global theme with
      // another name threw an NPE, any theme of an org whose id equaled the new org's name
      // matched regardless of its name, and the new org's own theme never matched
      when(orgManager.isSiteAdmin(principal)).thenReturn(true);
      when(securityProvider.getOrganizationIDs()).thenReturn(new String[]{ "host-org" });
      when(customThemesManager.getCustomThemes()).thenReturn(new HashSet<>(Set.of(
         theme("otherGlobal", null), theme("red", "Org Two"), theme("blue", "o2"))));
      SecurityOrganization request = newOrgRequest("o2", "Org Two");
      request.setTheme("blue");

      service.createOrganization(request, null, principal);

      ArgumentCaptor<Organization> org = ArgumentCaptor.forClass(Organization.class);
      verify(editableProvider).addOrganization(org.capture());
      assertEquals("blue", ((FSOrganization) org.getValue()).getTheme());
   }

   // Bug #77083: the requested-theme lookup parsed as (name matches && global) || orgID equals
   // org name, so a global theme with another name threw a NullPointerException, and a theme of
   // an organization whose ID equals the new organization's name was selected whatever was asked
   // for. The organization stores the theme ID, as GlobalStyleController and the REST GET/PUT do.

   private String createOrganizationWithThemes(String requestedTheme, CustomTheme... themes)
      throws Exception
   {
      when(orgManager.isSiteAdmin(principal)).thenReturn(true);
      when(securityProvider.getOrganizationIDs()).thenReturn(new String[]{ "host-org", "org1" });
      when(customThemesManager.getCustomThemes()).thenReturn(new HashSet<>(Arrays.asList(themes)));
      SecurityOrganization request = newOrgRequest("sales", "Sales West");
      request.setTheme(requestedTheme);

      service.createOrganization(request, null, principal);

      ArgumentCaptor<Organization> captor = ArgumentCaptor.forClass(Organization.class);
      verify(editableProvider).addOrganization(captor.capture());
      return captor.getValue().getTheme();
   }

   private static CustomTheme namedTheme(String id, String name, String orgID) {
      CustomTheme theme = theme(id, orgID);
      theme.setName(name);
      return theme;
   }

   @Test
   void createOrganization_nonMatchingGlobalTheme_noExceptionAndNoTheme() throws Exception {
      assertNull(createOrganizationWithThemes("Other", namedTheme("g1", "Global", null)));
   }

   @Test
   void createOrganization_noThemeRequestedWithGlobalTheme_noTheme() throws Exception {
      assertNull(createOrganizationWithThemes(null, namedTheme("g1", "Global", null)));
   }

   @Test
   void createOrganization_globalThemeRequestedByName_storesThemeId() throws Exception {
      assertEquals("g1", createOrganizationWithThemes(
         "Global", namedTheme("g1", "Global", null), namedTheme("g2", "Other Global", null)));
   }

   @Test
   void createOrganization_globalThemeRequestedById_storesThemeId() throws Exception {
      assertEquals("g1", createOrganizationWithThemes("g1", namedTheme("g1", "Global", null)));
   }

   @Test
   void createOrganization_idMatchPreferredOverNameMatch() throws Exception {
      // theme "a" is named "g1", theme "g1" is named "Global": a request for "g1" means the ID
      assertEquals("g1", createOrganizationWithThemes(
         "g1", namedTheme("a", "g1", null), namedTheme("g1", "Global", null)));
   }

   @Test
   void createOrganization_otherOrgThemeRequested_notSelected() throws Exception {
      assertNull(createOrganizationWithThemes("Org1 Theme", namedTheme("o1", "Org1 Theme", "org1")));
   }

   @Test
   void createOrganization_themeOfOrgIdEqualToNewOrgName_notSelectedForOtherRequest()
      throws Exception
   {
      // an organization whose ID equals the new organization's name must not donate its theme
      assertNull(createOrganizationWithThemes(
         "Global", namedTheme("o1", "Private", "Sales West")));
   }

   @Test
   void createOrganization_unknownThemeRequested_noTheme() throws Exception {
      assertNull(createOrganizationWithThemes(
         "Missing", namedTheme("g1", "Global", null), namedTheme("o1", "Private", "org1")));
      verify(customThemesManager, never()).setCustomThemes(any());
      verify(customThemesManager, never()).setOrgSelectedTheme(anyString(), anyString());
   }

   @Test
   void createOrganization_newOrgsOwnThemeRequested_selected() throws Exception {
      assertEquals("s1", createOrganizationWithThemes("Sales Theme",
         namedTheme("s1", "Sales Theme", "sales"), namedTheme("o1", "Sales Theme", "org1")));
   }

   // Bug #77117: create and update share one lookup (SecurityService.findOrganizationTheme)

   @Test
   void createOrganization_ownThemeBeatsSameNamedGlobalWithLowerId() throws Exception {
      assertEquals("s1", createOrganizationWithThemes("Shared",
         namedTheme("a", "Shared", null), namedTheme("s1", "Shared", "sales")));
   }

   @Test
   void createOrganization_emptyOrgIdThemeIsGlobal() throws Exception {
      assertEquals("g1", createOrganizationWithThemes("Global", namedTheme("g1", "Global", "")));
   }

   @Test
   void createOrganization_reservedDefaultId_noThemeEvenWithThemeNamedDefault() throws Exception {
      // Bug #77304: the reserved id "default" means "use the default theme", so create stores no
      // theme, as for an omitted one, and never resolves it to a theme named "default" (default1)
      assertNull(createOrganizationWithThemes(CustomTheme.DEFAULT_THEME_ID,
         namedTheme("default1", CustomTheme.DEFAULT_THEME_ID, null)));
      verify(customThemesManager, never()).setCustomThemes(any());
      verify(customThemesManager, never()).setOrgSelectedTheme(anyString(), anyString());
   }

   @Test
   void createOrganization_duplicateThemeNames_lowestIdSelected() throws Exception {
      assertEquals("a", createOrganizationWithThemes("Global",
         namedTheme("c", "Global", null), namedTheme("a", "Global", null),
         namedTheme("b", "Global", null)));
   }

   // the organization default is kept in three places (Organization.theme, the theme's
   // organizations list and the organization's selected theme, see claude/theme.md), which
   // updateOrganization() keeps in step through IdentityService.updateCustomThemeOrganization()
   @Test
   void createOrganization_themeRequested_allThreeOrgDefaultCopiesSet() throws Exception {
      CustomTheme global = namedTheme("g1", "Global", null);
      CustomTheme other = namedTheme("g2", "Other", null);

      assertEquals("g1", createOrganizationWithThemes("Global", global, other));

      // the theme change goes through the cluster-locked read-modify-write (Bug #76978)
      verify(customThemesManager).updateCustomThemes(any());
      ArgumentCaptor<Set<CustomTheme>> captor = ArgumentCaptor.forClass(Set.class);
      verify(customThemesManager).setCustomThemes(captor.capture());
      CustomTheme stored = captor.getValue().stream()
         .filter(t -> "g1".equals(t.getId())).findFirst().orElseThrow();
      assertEquals(List.of("sales"), stored.getOrganizations());
      assertTrue(captor.getValue().stream().anyMatch(t -> "g2".equals(t.getId())));
      assertTrue(other.getOrganizations().isEmpty());
      verify(customThemesManager).setOrgSelectedTheme("g1", "sales");
   }

   @Test
   void createOrganization_noThemeRequested_themesAndSelectionUntouched() throws Exception {
      assertNull(createOrganizationWithThemes(null, namedTheme("g1", "Global", null)));
      verify(customThemesManager, never()).setCustomThemes(any());
      verify(customThemesManager, never()).setOrgSelectedTheme(anyString(), anyString());
   }

   @Test
   void createOrganization_themeRemovedBeforeLockedUpdate_selectionNotSet() throws Exception {
      // the theme is found by the lookup but is gone when read again under the themes lock
      when(orgManager.isSiteAdmin(principal)).thenReturn(true);
      when(securityProvider.getOrganizationIDs()).thenReturn(new String[]{ "host-org" });
      when(customThemesManager.getCustomThemes())
         .thenReturn(new HashSet<>(Set.of(namedTheme("g1", "Global", null))))
         .thenReturn(new HashSet<>());
      SecurityOrganization request = newOrgRequest("sales", "Sales West");
      request.setTheme("Global");

      service.createOrganization(request, null, principal);

      verify(customThemesManager).updateCustomThemes(any());
      verify(customThemesManager, never()).setCustomThemes(any());
      verify(customThemesManager, never()).setOrgSelectedTheme(anyString(), anyString());
   }

   // Bug #77136: the plain REST create adds the organization directly, bypassing
   // UserTreeService.createOrganization, so it must check the id against OrganizationIdRules
   // itself: no system storage folder names (backup, heapdump, status) and no path characters.

   @ParameterizedTest
   @ValueSource(strings = { "backup", "HeapDump", "status", "a/b", ".." })
   void createOrganization_reservedOrUnsafeId_rejected(String id) {
      when(orgManager.isSiteAdmin(principal)).thenReturn(true);
      when(securityProvider.getOrganizationIDs()).thenReturn(new String[]{ "host-org", "org1" });

      MessageException thrown = assertThrows(MessageException.class,
         () -> service.createOrganization(newOrgRequest(id, "New Org"), null, principal));

      assertEquals(Catalog.getCatalog().getString("em.security.reservedOrganizationID", id),
                   thrown.getMessage());
      verify(editableProvider, never()).addOrganization(any());
   }

   @Test
   void createOrganization_cloneToReservedId_rejectedBeforeClone() {
      when(orgManager.isSiteAdmin(principal)).thenReturn(true);
      when(securityProvider.getOrganizationIDs()).thenReturn(new String[]{ "host-org", "org1" });
      SecurityOrganization request = newOrgRequest("backup", "New Org");
      request.setDefaultPassword("Str0ng!Passw0rd");

      assertThrows(MessageException.class, () -> service.createOrganization(request, "org1", principal));

      verifyNoInteractions(userTreeService);
   }

   // Bug #77082: org names and ids share one case-insensitive namespace (bare
   // SECURITY_ORGANIZATION keys are sometimes ids and sometimes names), but updateOrganization had
   // no name check at all and never compared a name with another org's id (or vice versa), so an
   // org admin could rename their org to another org's id or name.

   // Bug #77093: dropping a member from an organization deletes the identity, so a member list
   // that the REST update omits (null) must leave the organization's members of that type
   // unchanged, instead of being treated as an empty list that deletes all of them
   @Test
   void updateOrganization_memberUsersNull_keepsAllUsersAndHonorsGroups() throws Exception {
      stubOrgsForUpdate();
      stubOrg1Members();
      allowAdmin();
      SecurityOrganization request = newOrgRequest("org1", "Org One");
      request.setMemberGroups(List.of("sales"));
      request.setRoles(List.of("analyst"));

      service.updateOrganization("org1", request, principal);

      EditOrganizationPaneModel model = captureOrgModel();
      assertEquals(Set.of(CALLER, BOB, CAROL), members(model, Identity.USER));
      // the groups were ignored when memberUsers was null (wrong field checked)
      assertEquals(Set.of(SALES), members(model, Identity.GROUP));
      assertEquals(Set.of(ANALYST), members(model, Identity.ROLE));
   }

   @Test
   void updateOrganization_memberGroupsNull_keepsAllGroupsWithoutNpe() throws Exception {
      stubOrgsForUpdate();
      stubOrg1Members();
      allowAdmin();
      SecurityOrganization request = newOrgRequest("org1", "Org One");
      request.setMemberUsers(List.of("caller", "bob"));
      request.setRoles(List.of("analyst"));

      service.updateOrganization("org1", request, principal);

      EditOrganizationPaneModel model = captureOrgModel();
      assertEquals(Set.of(CALLER, BOB), members(model, Identity.USER));
      assertEquals(Set.of(SALES, HIDDEN_GROUP), members(model, Identity.GROUP));
      assertEquals(Set.of(ANALYST), members(model, Identity.ROLE));
   }

   @Test
   void updateOrganization_rolesNull_keepsAllRoles() throws Exception {
      stubOrgsForUpdate();
      stubOrg1Members();
      allowAdmin();
      SecurityOrganization request = newOrgRequest("org1", "Org One");
      request.setMemberUsers(List.of("caller"));
      request.setMemberGroups(List.of("sales"));

      service.updateOrganization("org1", request, principal);

      EditOrganizationPaneModel model = captureOrgModel();
      assertEquals(Set.of(CALLER), members(model, Identity.USER));
      assertEquals(Set.of(SALES), members(model, Identity.GROUP));
      assertEquals(Set.of(ANALYST, HIDDEN_ROLE), members(model, Identity.ROLE));
   }

   // an explicit empty list still removes every member of that type (unchanged behavior)
   @Test
   void updateOrganization_emptyMemberLists_stillClearMembers() throws Exception {
      stubOrgsForUpdate();
      stubOrg1Members();
      allowAdmin();
      SecurityOrganization request = newOrgRequest("org1", "Org One");
      request.setMemberUsers(List.of());
      request.setMemberGroups(List.of());
      request.setRoles(List.of());

      service.updateOrganization("org1", request, principal);

      assertTrue(captureOrgModel().members().isEmpty());
   }

   // the omitted members are unchanged, so they are not filtered by the caller's permissions.
   // setIdentity() keeps the users the caller cannot administer, but not such groups and roles
   @Test
   void updateOrganization_orgAdminNullLists_keepsHiddenGroupAndRole() throws Exception {
      stubOrgsForUpdate();
      stubOrg1Members();
      allowAdmin();
      denyAdmin(ResourceType.SECURITY_GROUP, HIDDEN_GROUP);
      denyAdmin(ResourceType.SECURITY_ROLE, HIDDEN_ROLE);

      service.updateOrganization("org1", newOrgRequest("org1", "Org One"), principal);

      EditOrganizationPaneModel model = captureOrgModel();
      assertEquals(Set.of(CALLER, BOB, CAROL), members(model, Identity.USER));
      assertEquals(Set.of(SALES, HIDDEN_GROUP), members(model, Identity.GROUP));
      assertEquals(Set.of(ANALYST, HIDDEN_ROLE), members(model, Identity.ROLE));
   }

   // a list that is present is still limited to the identities the caller can administer
   @Test
   void updateOrganization_orgAdminExplicitLists_stillFilteredByPermission() throws Exception {
      stubOrgsForUpdate();
      stubOrg1Members();
      allowAdmin();
      denyAdmin(ResourceType.SECURITY_GROUP, HIDDEN_GROUP);
      denyAdmin(ResourceType.SECURITY_ROLE, HIDDEN_ROLE);
      SecurityOrganization request = newOrgRequest("org1", "Org One");
      request.setMemberUsers(List.of("caller"));
      request.setMemberGroups(List.of("sales", "hidden-group"));
      request.setRoles(List.of("analyst", "hidden-role"));

      service.updateOrganization("org1", request, principal);

      EditOrganizationPaneModel model = captureOrgModel();
      assertEquals(Set.of(SALES), members(model, Identity.GROUP));
      assertEquals(Set.of(ANALYST), members(model, Identity.ROLE));
   }

   // the omitted members come from the editable provider, which setIdentity() compares against.
   // An identity of another provider in the chain would be created as a blank local identity
   @Test
   void updateOrganization_nullLists_takeMembersFromEditableProviderOnly() throws Exception {
      stubOrgsForUpdate();
      stubOrg1Members();
      allowAdmin();
      when(securityProvider.getUsers()).thenReturn(new IdentityID[]{
         CALLER, BOB, CAROL, new IdentityID("ldap-user", "org1") });
      when(securityProvider.getGroups()).thenReturn(new IdentityID[]{
         SALES, HIDDEN_GROUP, new IdentityID("ldap-group", "org1") });
      when(securityProvider.getRoles()).thenReturn(new IdentityID[]{
         ANALYST, HIDDEN_ROLE, new IdentityID("ldap-role", "org1") });

      service.updateOrganization("org1", newOrgRequest("org1", "Org One"), principal);

      EditOrganizationPaneModel model = captureOrgModel();
      // also excludes the identities of other organizations
      assertEquals(Set.of(CALLER, BOB, CAROL), members(model, Identity.USER));
      assertEquals(Set.of(SALES, HIDDEN_GROUP), members(model, Identity.GROUP));
      assertEquals(Set.of(ANALYST, HIDDEN_ROLE), members(model, Identity.ROLE));
   }

   // on an id change the omitted members carry over to the new organization id
   @Test
   void updateOrganization_idChangeWithNullLists_movesAllMembers() throws Exception {
      stubOrgsForUpdate();
      stubOrg1Members();
      allowAdmin();
      when(securityProvider.getOrganizationIDs()).thenReturn(new String[]{ "host-org", "org1" });

      service.updateOrganization("org1", newOrgRequest("org1b", "Org One"), principal);

      EditOrganizationPaneModel model = captureOrgModel();
      assertEquals(Set.of(new IdentityID("caller", "org1b"), new IdentityID("bob", "org1b"),
                          new IdentityID("carol", "org1b")),
                   members(model, Identity.USER));
      assertEquals(Set.of(new IdentityID("sales", "org1b"), new IdentityID("hidden-group", "org1b")),
                   members(model, Identity.GROUP));
      assertEquals(Set.of(new IdentityID("analyst", "org1b"), new IdentityID("hidden-role", "org1b")),
                   members(model, Identity.ROLE));
   }

   // end to end with the real member update of IdentityService: an update that omits every
   // member list must not remove, move or create any identity of the organization
   @Test
   void updateOrganization_nullLists_memberUpdateChangesNoIdentity_sameOrg() throws Exception {
      when(orgManager.getCurrentOrgID()).thenReturn("org1");
      assertNullListUpdateChangesNoIdentity();
   }

   // the same for a site admin whose current organization is not the edited one. Until #77092 is
   // fixed, updateOrganizationMembers() compares the edited org id with the current org id and
   // moves (sets, then removes) every kept member, so the omitted members are still deleted.
   // Run with -Djunit.jupiter.conditions.deactivate=org.junit.*DisabledCondition to check it
   @Disabled("Bug #77092: enable when the community fix of the organization id change check is merged")
   @Test
   void updateOrganization_nullLists_memberUpdateChangesNoIdentity_crossOrg() throws Exception {
      when(orgManager.getCurrentOrgID()).thenReturn("host-org");
      assertNullListUpdateChangesNoIdentity();
   }

   // Bug #77146: setIdentity() applies the model's theme and locale, and clears them when they are
   // null, so a theme or locale that the REST update omits must keep the organization's current
   // value. The locale is passed as the label that setIdentity() maps back to the stored key
   @Test
   void updateOrganization_themeAndLocaleNull_memberOnlyUpdate_keepsCurrent() throws Exception {
      stubOrgsForUpdate();
      stubOrg1ThemeAndLocale("t1", "en_US");
      stubOrg1Members();
      allowAdmin();
      SecurityOrganization request = newOrgRequest("org1", "Org One");
      request.setMemberUsers(List.of("caller", "bob"));

      service.updateOrganization("org1", request, principal);

      EditOrganizationPaneModel model = captureOrgModel();
      assertEquals("t1", model.theme());
      assertEquals("English(America)", model.locale());
   }

   @Test
   void updateOrganization_themeAndLocaleNull_renameOnlyUpdate_keepsCurrent() throws Exception {
      stubOrgsForUpdate();
      stubOrg1ThemeAndLocale("t1", "en_US");

      service.updateOrganization("org1", newOrgRequest("org1", "Org Renamed"), principal);

      EditOrganizationPaneModel model = captureOrgModel();
      assertEquals("Org Renamed", model.name());
      assertEquals("t1", model.theme());
      assertEquals("English(America)", model.locale());
   }

   // an organization without a theme or locale keeps having none
   @Test
   void updateOrganization_themeAndLocaleNull_noCurrentValues_staysNull() throws Exception {
      stubOrgsForUpdate();
      stubOrg1ThemeAndLocale(null, null);

      service.updateOrganization("org1", newOrgRequest("org1", "Org One"), principal);

      EditOrganizationPaneModel model = captureOrgModel();
      assertNull(model.theme());
      assertNull(model.locale());
   }

   @Test
   void updateOrganization_explicitThemeAndLocale_passedThrough() throws Exception {
      stubOrgsForUpdate();
      stubOrg1ThemeAndLocale("t1", "en_US");
      SecurityOrganization request = newOrgRequest("org1", "Org One");
      request.setTheme("t2");
      // the public API takes a locale code, setIdentity() gets its label
      request.setLocale("de_DE");

      service.updateOrganization("org1", request, principal);

      EditOrganizationPaneModel model = captureOrgModel();
      assertEquals("t2", model.theme());
      assertEquals("Deutsch(Deutschland)", model.locale());
   }

   // an empty theme is passed through, and setIdentity() clears it like null
   @Test
   void updateOrganization_themeEmpty_clearsThemeAndKeepsLocale() throws Exception {
      stubOrgsForUpdate();
      stubOrg1ThemeAndLocale("t1", "en_US");
      SecurityOrganization request = newOrgRequest("org1", "Org One");
      request.setTheme("");

      service.updateOrganization("org1", request, principal);

      EditOrganizationPaneModel model = captureOrgModel();
      assertEquals("", model.theme());
      assertEquals("English(America)", model.locale());
   }

   // an empty locale matches no label, so setIdentity() clears it
   @Test
   void updateOrganization_localeEmpty_clearsLocaleAndKeepsTheme() throws Exception {
      stubOrgsForUpdate();
      stubOrg1ThemeAndLocale("t1", "en_US");
      SecurityOrganization request = newOrgRequest("org1", "Org One");
      request.setLocale("");

      service.updateOrganization("org1", request, principal);

      EditOrganizationPaneModel model = captureOrgModel();
      assertEquals("t1", model.theme());
      // the empty code maps to no label, which setIdentity() clears like an empty one
      assertNull(model.locale());
   }

   // Bug #77170: the GET of an organization returns the locale key, and setIdentity() only
   // accepts the label, so a GET followed by a PUT of the same body must keep the locale. On this
   // branch the REST locale is always a code (Bug #76707), converted to its label for setIdentity().
   @Test
   void organization_getThenPutSameLocale_keepsLocale() throws Exception {
      stubOrgsForUpdate();
      stubOrg1ThemeAndLocale("t1", "en_US");
      doReturn(new String[0]).when(securityProvider).getOrganizationMembers("org1");
      when(identityService.getIdentityInfo(any(), eq(Identity.ORGANIZATION), any()))
         .thenAnswer(inv -> new IdentityInfo(editableProvider.getOrganization("org1"), securityProvider));

      SecurityOrganization got = service.getOrganization("org1", principal);
      assertEquals("en_US", got.getLocale());

      SecurityOrganization request = newOrgRequest("org1", "Org One");
      request.setLocale(got.getLocale());
      service.updateOrganization("org1", request, principal);

      assertEquals("English(America)", captureOrgModel().locale());
   }

   // a value that is both a key and a label is a locale code, so its own label is passed on
   // (main treated it as a label; the REST locale is a code on this branch, Bug #76707)
   @Test
   void updateOrganization_localeCodeThatIsAlsoLabel_passedAsItsLabel() throws Exception {
      stubOrgsForUpdate();
      stubOrg1ThemeAndLocale("t1", null);
      Properties localeProperties = new Properties();
      localeProperties.setProperty("en_US", "English");
      localeProperties.setProperty("English", "Other");
      sUtilStatic.when(SUtil::loadLocaleProperties).thenReturn(localeProperties);
      SecurityOrganization request = newOrgRequest("org1", "Org One");
      request.setLocale("English");

      service.updateOrganization("org1", request, principal);

      assertEquals("Other", captureOrgModel().locale());
   }

   // a stored locale key without a label in locale.properties cannot be passed as a label, so it
   // is cleared even though the request omits the locale (documented limitation)
   @Test
   void updateOrganization_localeNull_storedKeyWithoutLabel_isCleared() throws Exception {
      stubOrgsForUpdate();
      stubOrg1ThemeAndLocale("t1", "xx_YY");

      service.updateOrganization("org1", newOrgRequest("org1", "Org One"), principal);

      EditOrganizationPaneModel model = captureOrgModel();
      assertEquals("t1", model.theme());
      assertNull(model.locale());
   }

   // a label that locale.properties gives to more than one key is passed as is, and setIdentity()
   // may map it back to another of those keys (documented limitation)
   @Test
   void updateOrganization_localeNull_duplicateLabel_passesSharedLabel() throws Exception {
      stubOrgsForUpdate();
      stubOrg1ThemeAndLocale("t1", "en_GB");
      Properties localeProperties = new Properties();
      localeProperties.setProperty("en_US", "English");
      localeProperties.setProperty("en_GB", "English");
      sUtilStatic.when(SUtil::loadLocaleProperties).thenReturn(localeProperties);

      service.updateOrganization("org1", newOrgRequest("org1", "Org One"), principal);

      assertEquals("English", captureOrgModel().locale());
   }

   // Bug #77147: the REST organization has no status, so an update must keep the stored status
   // instead of passing the model default, which would reactivate an inactive organization
   @Test
   void updateOrganization_inactiveOrg_staysInactive() throws Exception {
      stubOrgsForUpdate();
      ((FSOrganization) editableProvider.getOrganization("org1")).setActive(false);

      service.updateOrganization("org1", newOrgRequest("org1", "Org Renamed"), principal);

      assertFalse(captureOrgModel().status());
   }

   @Test
   void updateOrganization_activeOrg_staysActive() throws Exception {
      stubOrgsForUpdate();

      service.updateOrganization("org1", newOrgRequest("org1", "Org Renamed"), principal);

      assertTrue(captureOrgModel().status());
   }

   // Bug #77117: the REST create matched the organization theme by name, but the update passed
   // the value verbatim to IdentityService, whose eligibility checks only match theme ids, so a
   // theme name on update was silently ignored (site admin) or rejected (org admin). The update
   // now resolves a name to the id of an eligible (global or own-organization) theme first.

   private String updateOrgTheme(String requested, CustomTheme... themes) throws Exception {
      return updateOrgTheme("org1", requested, themes);
   }

   private String updateOrgTheme(String newId, String requested, CustomTheme... themes)
      throws Exception
   {
      when(customThemesManager.getCustomThemes()).thenReturn(new HashSet<>(Arrays.asList(themes)));
      SecurityOrganization request = newOrgRequest(newId, "Org One");
      request.setTheme(requested);

      service.updateOrganization("org1", request, principal);

      return captureOrgModel().theme();
   }

   @Test
   void updateOrganization_themeName_resolvedToGlobalThemeId() throws Exception {
      stubOrgsForUpdate();

      assertEquals("g1", updateOrgTheme("Blue Global", namedTheme("g1", "Blue Global", null)));
   }

   @Test
   void updateOrganization_themeName_resolvedToOwnOrgThemeId() throws Exception {
      stubOrgsForUpdate();

      assertEquals("t1", updateOrgTheme("Mine", namedTheme("t1", "Mine", "org1")));
   }

   @Test
   void updateOrganization_themeId_unchanged() throws Exception {
      // control: the ids that EM and existing REST clients send are passed through as is
      stubOrgsForUpdate();

      assertEquals("g1", updateOrgTheme("g1", namedTheme("g1", "Blue Global", null)));
   }

   @Test
   void updateOrganization_themeId_winsOverNameEqualToThatId() throws Exception {
      stubOrgsForUpdate();

      assertEquals("g1", updateOrgTheme("g1", namedTheme("a", "g1", null),
                                        namedTheme("g1", "Other", null)));
   }

   @Test
   void updateOrganization_themeName_ownOrgThemeWinsOverSameNamedGlobal() throws Exception {
      // the global theme has the lower id, so this is not the lowest-id tie-break
      stubOrgsForUpdate();

      assertEquals("z-org1", updateOrgTheme("Blue", namedTheme("a-global", "Blue", null),
                                            namedTheme("z-org1", "Blue", "org1")));
   }

   @Test
   void updateOrganization_themeName_sameNamedGlobals_lowestIdWins() throws Exception {
      // a theme with an empty org id is global, as in IdentityService
      stubOrgsForUpdate();

      assertEquals("b", updateOrgTheme("Blue", namedTheme("c", "Blue", null),
                                       namedTheme("b", "Blue", "")));
   }

   @Test
   void updateOrganization_otherOrgThemeName_passedThroughUnresolved() throws Exception {
      // the sink then ignores it (site admin) or rejects it (org admin), as before
      stubOrgsForUpdate();

      assertEquals("Private", updateOrgTheme("Private", namedTheme("o2", "Private", "org2")));
   }

   @Test
   void updateOrganization_unknownTheme_passedThroughUnresolved() throws Exception {
      stubOrgsForUpdate();

      assertEquals("unknown", updateOrgTheme("unknown", namedTheme("g1", "Blue", null)));
   }

   @Test
   void updateOrganization_nullTheme_unchanged() throws Exception {
      stubOrgsForUpdate();

      assertNull(updateOrgTheme(null, namedTheme("blank", null, null)));
   }

   @Test
   void updateOrganization_emptyTheme_notResolvedAsName() throws Exception {
      stubOrgsForUpdate();

      assertEquals("", updateOrgTheme("", namedTheme("blank", "", null)));
   }

   @Test
   void updateOrganization_reservedDefaultId_passedThroughEvenWithThemeNamedDefault()
      throws Exception
   {
      // Bug #77304: the reserved id "default" means "use the default theme" and the sink clears
      // the assignment, so it is never resolved by name to a theme named "default" (default1),
      // which stays reachable by its own id
      stubOrgsForUpdate();

      assertEquals(CustomTheme.DEFAULT_THEME_ID,
                   updateOrgTheme(CustomTheme.DEFAULT_THEME_ID,
                                  namedTheme("default1", CustomTheme.DEFAULT_THEME_ID, null)));
   }

   @Test
   void updateOrganization_defaultWithoutThemeOfThatName_passedThroughUnresolved()
      throws Exception
   {
      stubOrgsForUpdate();

      assertEquals(CustomTheme.DEFAULT_THEME_ID,
                   updateOrgTheme(CustomTheme.DEFAULT_THEME_ID, namedTheme("g1", "Blue", null)));
   }

   @Test
   void updateOrganization_otherOrgThemeId_resolvedToEligibleThemeWithThatName()
      throws Exception
   {
      // the id match only considers eligible themes, so another org's theme id does not win
      // over an eligible theme whose name is that value
      stubOrgsForUpdate();

      assertEquals("t1", updateOrgTheme("x", namedTheme("x", "Mine", "org2"),
                                        namedTheme("t1", "x", "org1")));
   }

   @Test
   void updateOrganization_idRename_themeNameResolvedAgainstCurrentOrgId() throws Exception {
      // the org's own themes still carry the current id until setIdentity() copies them
      stubOrgsForUpdate();

      assertEquals("t1", updateOrgTheme("org1b", "Mine", namedTheme("t1", "Mine", "org1")));
   }

   @Test
   void updateOrganization_legacyNameStored_putByNamePassesIdToSink() throws Exception {
      // a create on main stored the theme name; a PUT with that name now passes the id to the
      // sink. The sink is mocked here, so this only checks the REST layer, not the stored heal.
      FSOrganization org1 = stubOrgsForUpdate();
      org1.setTheme("Blue Global");

      assertEquals("g1", updateOrgTheme("Blue Global", namedTheme("g1", "Blue Global", null)));
   }

   @Test
   void updateOrganization_themeOmitted_legacyStoredNameKeptUnresolved() throws Exception {
      // Bug #77146 keeps the current theme when the PUT omits it; only a theme that the request
      // supplies is resolved, so a locale-only PUT must not rewrite a legacy stored name
      stubOrgsForUpdate();
      stubOrg1ThemeAndLocale("Blue Global", "en_US");
      when(customThemesManager.getCustomThemes())
         .thenReturn(new HashSet<>(List.of(namedTheme("g1", "Blue Global", null))));
      SecurityOrganization request = newOrgRequest("org1", "Org One");
      // the REST locale is a code (Bug #76707), passed to setIdentity() as its label
      request.setLocale("de_DE");

      service.updateOrganization("org1", request, principal);

      EditOrganizationPaneModel model = captureOrgModel();
      assertEquals("Blue Global", model.theme());
      assertEquals("Deutsch(Deutschland)", model.locale());
   }

   private void stubOrg1ThemeAndLocale(String theme, String locale) {
      FSOrganization org1 = (FSOrganization) editableProvider.getOrganization("org1");
      org1.setTheme(theme);
      org1.setLocale(locale);
      Properties localeProperties = new Properties();
      localeProperties.setProperty("en_US", "English(America)");
      localeProperties.setProperty("de_DE", "Deutsch(Deutschland)");
      sUtilStatic.when(SUtil::loadLocaleProperties).thenReturn(localeProperties);
   }

   private void assertNullListUpdateChangesNoIdentity() throws Exception {
      stubOrgsForUpdate();
      stubOrg1Members();
      allowAdmin();

      for(IdentityID user : List.of(CALLER, BOB, CAROL)) {
         doReturn(new FSUser(user)).when(editableProvider).getUser(user);
      }

      for(IdentityID group : List.of(SALES, HIDDEN_GROUP)) {
         doReturn(new FSGroup(group)).when(editableProvider).getGroup(group);
      }

      for(IdentityID role : List.of(ANALYST, HIDDEN_ROLE)) {
         doReturn(new FSRole(role)).when(editableProvider).getRole(role);
      }

      Cluster cluster = mock(Cluster.class);
      IdentityService memberService = createMemberUpdateService(cluster);
      doAnswer(inv -> {
         updateOrganizationMembers(memberService, inv.getArgument(1));
         return null;
      }).when(identityService).setIdentity(any(), any(), any(), any());

      service.updateOrganization("org1", newOrgRequest("org1", "Org One"), principal);

      verify(editableProvider, never()).removeUser(any());
      verify(editableProvider, never()).removeGroup(any());
      verify(editableProvider, never()).removeGroup(any(), anyBoolean());
      verify(editableProvider, never()).removeRole(any());
      verify(editableProvider, never()).setUser(any(), any());
      verify(editableProvider, never()).setGroup(any(), any());
      verify(editableProvider, never()).setRole(any(), any());
      // a dropped member is deleted in the organization scope, which the static mock of
      // OrganizationManager skips, but its removal is always broadcast
      verify(cluster, never()).sendMessage(any());
   }

   // an IdentityService with only the dependencies of the member update, created without
   // invoking its constructor
   private IdentityService createMemberUpdateService(Cluster cluster) {
      IdentityService memberService =
         mock(IdentityService.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));
      when(securityProvider.getAuthorizationProvider()).thenReturn(mock(AuthorizationChain.class));
      ReflectionTestUtils.setField(memberService, "securityProvider", securityProvider);
      ReflectionTestUtils.setField(memberService, "repletRegistryManager",
                                   mock(RepletRegistryManager.class));
      ReflectionTestUtils.setField(memberService, "dashboardRegistryManager",
                                   mock(DashboardRegistryManager.class));
      ReflectionTestUtils.setField(memberService, "favoritesService", mock(FavoritesService.class));
      ReflectionTestUtils.setField(memberService, "indexedStorage", mock(IndexedStorage.class));
      ReflectionTestUtils.setField(memberService, "cluster", cluster);
      ReflectionTestUtils.setField(memberService, "LOG",
                                   LoggerFactory.getLogger(IdentityService.class));
      doNothing().when(memberService).updateIdentityPermissions(anyInt(), any(), any(), any(),
                                                                any(), anyBoolean());
      return memberService;
   }

   // calls updateOrganizationMembers() with the arguments that setOrganizationInfo() passes
   private void updateOrganizationMembers(IdentityService memberService,
                                          EditOrganizationPaneModel model)
      throws Exception
   {
      FSOrganization org = new FSOrganization(model.id());
      org.setMembers(model.members().stream()
                        .map(member -> member.identityID().getName())
                        .toArray(String[]::new));
      Method method = IdentityService.class.getDeclaredMethod(
         "updateOrganizationMembers", Organization.class, List.class, String.class,
         EditableAuthenticationProvider.class, Principal.class);
      method.setAccessible(true);

      try {
         method.invoke(memberService, org, model.members(), "org1", editableProvider, principal);
      }
      catch(InvocationTargetException ex) {
         throw (Exception) ex.getCause();
      }
   }

   private void stubOrg1Members() {
      doReturn(new IdentityID[]{ CALLER, BOB, CAROL, new IdentityID("dave", "org2") })
         .when(editableProvider).getUsers();
      doReturn(new IdentityID[]{ SALES, HIDDEN_GROUP, new IdentityID("sales", "org2") })
         .when(editableProvider).getGroups();
      doReturn(new IdentityID[]{ ANALYST, HIDDEN_ROLE, new IdentityID("Everyone", null) })
         .when(editableProvider).getRoles();
   }

   private void allowAdmin() {
      when(securityProvider.checkPermission(eq(principal), any(ResourceType.class), anyString(),
                                            eq(ResourceAction.ADMIN)))
         .thenReturn(true);
   }

   private void denyAdmin(ResourceType type, IdentityID id) {
      when(securityProvider.checkPermission(principal, type, id.convertToKey(),
                                            ResourceAction.ADMIN))
         .thenReturn(false);
   }

   private EditOrganizationPaneModel captureOrgModel() throws Exception {
      ArgumentCaptor<EditOrganizationPaneModel> captor =
         ArgumentCaptor.forClass(EditOrganizationPaneModel.class);
      verify(identityService).setIdentity(any(), captor.capture(), eq(editableProvider),
                                          eq(principal));
      return captor.getValue();
   }

   private static Set<IdentityID> members(EditOrganizationPaneModel model, int type) {
      Set<IdentityID> ids = new HashSet<>();

      for(IdentityModel member : model.members()) {
         if(member.type() == type) {
            ids.add(member.identityID());
         }
      }

      return ids;
   }

   private static final IdentityID CALLER = new IdentityID("caller", "org1");
   private static final IdentityID BOB = new IdentityID("bob", "org1");
   private static final IdentityID CAROL = new IdentityID("carol", "org1");
   private static final IdentityID SALES = new IdentityID("sales", "org1");
   private static final IdentityID HIDDEN_GROUP = new IdentityID("hidden-group", "org1");
   private static final IdentityID ANALYST = new IdentityID("analyst", "org1");
   private static final IdentityID HIDDEN_ROLE = new IdentityID("hidden-role", "org1");

   // ── organization self grant (Bug #77206) ────────────────────────────────
   //
   // The org endpoints are not @SwitchOrg, so the caller's ambient org (normally host-org for a
   // site admin) is not the target org. The org's self grant must still be read, written and
   // removed in the target org's bucket, keyed (name, id), with the grantees scoped to the target
   // org, because that is where the permission check reads it. A request without adminIdentities
   // must keep the existing admins on every update branch.

   private static final String P = "p77206";
   private static final String Q = "q77206";

   private AuthorizationProvider stubAuthz() {
      AuthorizationProvider authz = mock(AuthorizationProvider.class, withSettings().lenient());
      when(securityProvider.getAuthorizationProvider()).thenReturn(authz);
      return authz;
   }

   private static AdminIdentities adminUsers(IdentityID... users) {
      AdminIdentities ids = new AdminIdentities();
      ids.setUsers(List.of(users));
      return ids;
   }

   private void allowUserAdmin(IdentityID user) {
      when(securityProvider.checkPermission(principal, ResourceType.SECURITY_USER,
                                            user.convertToKey(), ResourceAction.ADMIN))
         .thenReturn(true);
   }

   private void stubOrgForUpdate(String id, String name) {
      // the caller is a site admin, the only caller that may update an org other than its own
      // (Bug #77216)
      when(orgManager.isSiteAdmin(principal)).thenReturn(true);
      FSOrganization org = new FSOrganization(id);
      org.setName(name);
      when(securityProvider.checkPermission(principal, ResourceType.SECURITY_ORGANIZATION,
                                            id, ResourceAction.ADMIN))
         .thenReturn(true);
      when(securityProvider.getOrganization(id)).thenReturn(org);
      doReturn(org).when(editableProvider).getOrganization(id);
      when(securityProvider.getOrganizationIDs()).thenReturn(new String[]{ "host-org", id });
   }

   // no permission call may go through the ambient-bucket (no org argument) overloads
   private void verifyOnlyExplicitBucketCalls(AuthorizationProvider authz) {
      verify(authz, never()).getPermission(any(ResourceType.class), any(IdentityID.class));
      verify(authz, never()).setPermission(any(ResourceType.class), any(IdentityID.class),
                                           any(Permission.class));
      verify(authz, never()).removePermission(any(ResourceType.class), any(IdentityID.class));
      verify(identityService, never()).setIdentityPermissions(
         any(), any(), eq(ResourceType.SECURITY_ORGANIZATION), any(), any(), any());
   }

   // the wrapper delegates the merge with the organization as both bucket and grantee scope
   private void verifyOrgGrantDelegated(IdentityID baseKey, IdentityID newKey, String orgId) {
      verify(identityService).setIdentityPermissions(
         eq(baseKey), eq(newKey), eq(ResourceType.SECURITY_ORGANIZATION), eq(principal), any(),
         eq(orgId), eq(orgId));
   }

   private void verifyOrgGrantNotDelegated() {
      verify(identityService, never()).setIdentityPermissions(
         any(), any(), any(), any(), any(), any(), any());
   }

   private Permission captureOrgGrant(AuthorizationProvider authz, IdentityID key, String bucket) {
      ArgumentCaptor<Permission> captor = ArgumentCaptor.forClass(Permission.class);
      verify(authz).setPermission(eq(ResourceType.SECURITY_ORGANIZATION), eq(key),
                                  captor.capture(), eq(bucket));
      return captor.getValue();
   }

   @Test
   void createOrganization_adminIdentities_storedInTargetBucketKeyedNameId() throws Exception {
      when(orgManager.isSiteAdmin(principal)).thenReturn(true);
      when(securityProvider.getOrganizationIDs()).thenReturn(new String[]{ "host-org" });
      AuthorizationProvider authz = stubAuthz();
      IdentityID u = new IdentityID("u", P);
      allowUserAdmin(u);
      SecurityOrganization request = newOrgRequest(P, "Name77206");
      request.setAdminIdentities(adminUsers(u));

      service.createOrganization(request, null, principal);

      Permission grant = captureOrgGrant(authz, new IdentityID("Name77206", P), P);
      assertEquals(Set.of(u), grant.getOrgScopedUserGrants(ResourceAction.ADMIN, P));
      verify(authz, never()).setPermission(any(ResourceType.class), eq(new IdentityID(P, P)),
                                           any(Permission.class), any());
      verifyOnlyExplicitBucketCalls(authz);
   }

   @Test
   void createOrganization_copyFrom_appliesAdminIdentitiesInTargetBucket() throws Exception {
      when(orgManager.isSiteAdmin(principal)).thenReturn(true);
      when(securityProvider.getOrganizationIDs()).thenReturn(new String[]{ "host-org", "org1" });
      doReturn("Primary").when(editableProvider).getProviderName();
      AuthorizationProvider authz = stubAuthz();
      IdentityID u = new IdentityID("u", P);
      allowUserAdmin(u);
      SecurityOrganization request = newOrgRequest(P, "Name77206");
      request.setAdminIdentities(adminUsers(u));
      // community copyOrganization names the new org by its id, not by the request name
      FSOrganization created = new FSOrganization(P);
      created.setName(P);
      doAnswer(inv -> {
         doReturn(created).when(editableProvider).getOrganization(P);
         return null;
      }).when(userTreeService).createOrganization("org1", "Primary", "Name77206", P, principal, null);

      service.createOrganization(request, "org1", principal);

      verify(userTreeService).createOrganization("org1", "Primary", "Name77206", P, principal, null);
      // keyed by the org as created, (id, id), which is where the copied grant and the
      // permission check are
      Permission grant = captureOrgGrant(authz, new IdentityID(P, P), P);
      assertEquals(Set.of(u), grant.getOrgScopedUserGrants(ResourceAction.ADMIN, P));
      verify(authz, never()).setPermission(any(ResourceType.class),
                                           eq(new IdentityID("Name77206", P)),
                                           any(Permission.class), any());
      verifyOnlyExplicitBucketCalls(authz);
   }

   @Test
   void createOrganization_copyFrom_noAdminIdentities_keepsCopiedGrant() throws Exception {
      when(orgManager.isSiteAdmin(principal)).thenReturn(true);
      when(securityProvider.getOrganizationIDs()).thenReturn(new String[]{ "host-org", "org1" });
      doReturn("Primary").when(editableProvider).getProviderName();
      AuthorizationProvider authz = stubAuthz();

      service.createOrganization(newOrgRequest(P, "Name77206"), "org1", principal);

      verify(userTreeService).createOrganization("org1", "Primary", "Name77206", P, principal, null);
      verifyNoInteractions(authz);
   }

   @Test
   void createOrganization_noAdminIdentities_writesNoGrant() throws Exception {
      when(orgManager.isSiteAdmin(principal)).thenReturn(true);
      when(securityProvider.getOrganizationIDs()).thenReturn(new String[]{ "host-org" });
      AuthorizationProvider authz = stubAuthz();

      service.createOrganization(newOrgRequest(P, "Name77206"), null, principal);

      verifyNoInteractions(authz);
   }

   @Test
   void updateOrganization_plain_adminIdentities_writtenInTargetBucketScopedToTarget()
      throws Exception
   {
      stubOrgForUpdate(P, "Name77206");
      AuthorizationProvider authz = stubAuthz();
      IdentityID u = new IdentityID("u", P);
      allowUserAdmin(u);
      IdentityID key = new IdentityID("Name77206", P);
      SecurityOrganization request = newOrgRequest(P, "Name77206");
      request.setAdminIdentities(adminUsers(u));

      service.updateOrganization(P, request, principal);

      verifyOrgGrantDelegated(key, key, P);
      // read by the wrapper for the legacy base selection, then by the merge (Bug #77271)
      verify(authz, times(2)).getPermission(ResourceType.SECURITY_ORGANIZATION, key, P);
      Permission grant = captureOrgGrant(authz, key, P);
      assertEquals(Set.of(u), grant.getOrgScopedUserGrants(ResourceAction.ADMIN, P));
      assertTrue(grant.getOrgScopedUserGrants(ResourceAction.ADMIN, "host-org").isEmpty());
      verify(authz, never()).removePermission(any(ResourceType.class), eq(key), any());
      verify(authz).removePermission(ResourceType.SECURITY_ORGANIZATION, new IdentityID(P, P), P);
      verifyOnlyExplicitBucketCalls(authz);
   }

   @Test
   void updateOrganization_revoke_keepsGranteesCallerCannotAdminister() throws Exception {
      stubOrgForUpdate(P, "Name77206");
      AuthorizationProvider authz = stubAuthz();
      IdentityID key = new IdentityID("Name77206", P);
      Permission existing = new Permission();
      existing.setUserGrantsForOrg(ResourceAction.ADMIN, Set.of("visible", "hidden"), P);
      when(authz.getPermission(ResourceType.SECURITY_ORGANIZATION, key, P)).thenReturn(existing);
      when(securityProvider.checkAnyPermission(
         principal, ResourceType.SECURITY_USER, new IdentityID("visible", P).convertToKey(),
         EnumSet.of(ResourceAction.ADMIN)))
         .thenReturn(true);
      SecurityOrganization request = newOrgRequest(P, "Name77206");
      request.setAdminIdentities(adminUsers());

      service.updateOrganization(P, request, principal);

      Permission grant = captureOrgGrant(authz, key, P);
      assertEquals(Set.of(new IdentityID("hidden", P)),
                   grant.getOrgScopedUserGrants(ResourceAction.ADMIN, P));
      verifyOnlyExplicitBucketCalls(authz);
   }

   @Test
   void updateOrganization_plain_noAdminIdentities_leavesGrantUntouched() throws Exception {
      stubOrgForUpdate(P, "Name77206");
      AuthorizationProvider authz = stubAuthz();

      service.updateOrganization(P, newOrgRequest(P, "Name77206"), principal);

      verifyNoInteractions(authz);
      verify(identityService, never()).setIdentityPermissions(
         any(), any(), any(), any(), any(), any());
      verifyOrgGrantNotDelegated();
   }

   @Test
   void updateOrganization_nameRename_adminIdentities_rekeyedInTargetBucket() throws Exception {
      stubOrgForUpdate(P, "Old Name");
      AuthorizationProvider authz = stubAuthz();
      IdentityID u = new IdentityID("u", P);
      allowUserAdmin(u);
      IdentityID oldKey = new IdentityID("Old Name", P);
      IdentityID newKey = new IdentityID("New Name", P);
      SecurityOrganization request = newOrgRequest(P, "New Name");
      request.setAdminIdentities(adminUsers(u));

      service.updateOrganization(P, request, principal);

      verifyOrgGrantDelegated(oldKey, newKey, P);
      verify(authz, times(2)).getPermission(ResourceType.SECURITY_ORGANIZATION, oldKey, P);
      Permission grant = captureOrgGrant(authz, newKey, P);
      assertEquals(Set.of(u), grant.getOrgScopedUserGrants(ResourceAction.ADMIN, P));
      verify(authz).removePermission(ResourceType.SECURITY_ORGANIZATION, oldKey, P);
      verify(authz).removePermission(ResourceType.SECURITY_ORGANIZATION, new IdentityID(P, P), P);
      verifyOnlyExplicitBucketCalls(authz);
   }

   @Test
   void updateOrganization_nameRename_noAdminIdentities_movesExistingGrant() throws Exception {
      stubOrgForUpdate(P, "Old Name");
      AuthorizationProvider authz = stubAuthz();
      IdentityID oldKey = new IdentityID("Old Name", P);
      IdentityID newKey = new IdentityID("New Name", P);
      Permission existing = new Permission();
      existing.setUserGrantsForOrg(ResourceAction.ADMIN, Set.of("u"), P);
      when(authz.getPermission(ResourceType.SECURITY_ORGANIZATION, oldKey, P)).thenReturn(existing);

      service.updateOrganization(P, newOrgRequest(P, "New Name"), principal);

      verifyOrgGrantNotDelegated();
      verify(authz).setPermission(eq(ResourceType.SECURITY_ORGANIZATION), eq(newKey),
                                  same(existing), eq(P));
      assertEquals(Set.of(new IdentityID("u", P)),
                   existing.getOrgScopedUserGrants(ResourceAction.ADMIN, P));
      verify(authz).removePermission(ResourceType.SECURITY_ORGANIZATION, oldKey, P);
      verify(authz, never()).removePermission(any(ResourceType.class), eq(new IdentityID(P, P)),
                                              any());
      verifyOnlyExplicitBucketCalls(authz);
   }

   @Test
   void updateOrganization_idRename_adminIdentities_writtenInNewBucketFromMigratedKey()
      throws Exception
   {
      // setIdentity() has already migrated the grant to (name, new id) in the new id's bucket
      stubOrgForUpdate(P, "Org Name");
      AuthorizationProvider authz = stubAuthz();
      IdentityID u = new IdentityID("u", Q);
      allowUserAdmin(u);
      IdentityID newKey = new IdentityID("Org Name", Q);
      SecurityOrganization request = newOrgRequest(Q, "Org Name");
      request.setAdminIdentities(adminUsers(u));

      service.updateOrganization(P, request, principal);

      verifyOrgGrantDelegated(newKey, newKey, Q);
      verify(authz, times(2)).getPermission(ResourceType.SECURITY_ORGANIZATION, newKey, Q);
      Permission grant = captureOrgGrant(authz, newKey, Q);
      assertEquals(Set.of(u), grant.getOrgScopedUserGrants(ResourceAction.ADMIN, Q));
      verify(authz, never()).removePermission(any(ResourceType.class), eq(newKey), any());
      verify(authz).removePermission(ResourceType.SECURITY_ORGANIZATION, new IdentityID(Q, Q), Q);
      verify(authz, never()).setPermission(any(ResourceType.class), any(IdentityID.class),
                                           any(Permission.class), eq(P));
      verifyOnlyExplicitBucketCalls(authz);
   }

   @Test
   void updateOrganization_idAndNameRename_adminIdentities_readsNewNameNewIdKey()
      throws Exception
   {
      stubOrgForUpdate(P, "Old Name");
      AuthorizationProvider authz = stubAuthz();
      IdentityID u = new IdentityID("u", Q);
      allowUserAdmin(u);
      IdentityID newKey = new IdentityID("New Name", Q);
      SecurityOrganization request = newOrgRequest(Q, "New Name");
      request.setAdminIdentities(adminUsers(u));

      service.updateOrganization(P, request, principal);

      verifyOrgGrantDelegated(newKey, newKey, Q);
      verify(authz, times(2)).getPermission(ResourceType.SECURITY_ORGANIZATION, newKey, Q);
      verify(authz, never()).getPermission(any(ResourceType.class),
                                           eq(new IdentityID("Old Name", Q)), any());
      Permission grant = captureOrgGrant(authz, newKey, Q);
      assertEquals(Set.of(u), grant.getOrgScopedUserGrants(ResourceAction.ADMIN, Q));
      verifyOnlyExplicitBucketCalls(authz);
   }

   @Test
   void updateOrganization_idRename_noAdminIdentities_keepsMigratedAdmins() throws Exception {
      // before the fix the REST call ran with an empty admin list and revoked every org admin
      stubOrgForUpdate(P, "Org Name");
      AuthorizationProvider authz = stubAuthz();

      service.updateOrganization(P, newOrgRequest(Q, "Org Name"), principal);

      verifyNoInteractions(authz);
      verify(identityService, never()).setIdentityPermissions(
         any(), any(), any(), any(), any(), any());
      verifyOrgGrantNotDelegated();
   }

   @Test
   void createOrganization_copyFrom_orgNotCreated_writesNoGrant() throws Exception {
      // if the copy did not create the org, no admin grant may be pre-seeded for its id
      when(orgManager.isSiteAdmin(principal)).thenReturn(true);
      when(securityProvider.getOrganizationIDs()).thenReturn(new String[]{ "host-org", "org1" });
      doReturn("Primary").when(editableProvider).getProviderName();
      AuthorizationProvider authz = stubAuthz();
      IdentityID u = new IdentityID("u", P);
      allowUserAdmin(u);
      SecurityOrganization request = newOrgRequest(P, "Name77206");
      request.setAdminIdentities(adminUsers(u));

      service.createOrganization(request, "org1", principal);

      verify(userTreeService).createOrganization("org1", "Primary", "Name77206", P, principal, null);
      verifyNoInteractions(authz);
   }

   @Test
   void updateOrganization_legacyIdIdGrantOnly_keepsItsHiddenGranteesBeforeRemovingIt()
      throws Exception
   {
      stubOrgForUpdate(P, "Name77206");
      AuthorizationProvider authz = stubAuthz();
      IdentityID key = new IdentityID("Name77206", P);
      IdentityID legacyKey = new IdentityID(P, P);
      Permission legacy = new Permission();
      legacy.setUserGrantsForOrg(ResourceAction.ADMIN, Set.of("visible", "hidden"), P);
      when(authz.getPermission(ResourceType.SECURITY_ORGANIZATION, legacyKey, P)).thenReturn(legacy);
      when(securityProvider.checkAnyPermission(
         principal, ResourceType.SECURITY_USER, new IdentityID("visible", P).convertToKey(),
         EnumSet.of(ResourceAction.ADMIN)))
         .thenReturn(true);
      SecurityOrganization request = newOrgRequest(P, "Name77206");
      request.setAdminIdentities(adminUsers());

      service.updateOrganization(P, request, principal);

      // the legacy grant is the merge base, so the merge reads and removes it as the old key
      verifyOrgGrantDelegated(legacyKey, key, P);
      verify(authz).getPermission(ResourceType.SECURITY_ORGANIZATION, key, P);
      Permission grant = captureOrgGrant(authz, key, P);
      assertEquals(Set.of(new IdentityID("hidden", P)),
                   grant.getOrgScopedUserGrants(ResourceAction.ADMIN, P));
      // removed as the re-keyed old key and as the stale legacy key; the second is a no-op
      verify(authz, atLeastOnce())
         .removePermission(ResourceType.SECURITY_ORGANIZATION, legacyKey, P);
      verifyOnlyExplicitBucketCalls(authz);
   }

   @Test
   void updateOrganization_legacyAndNameIdGrants_dropsStaleLegacyGrantees() throws Exception {
      // with a (name, id) grant the check ignores the legacy (id, id) grant (Bug #77185), so a
      // grantee only in the legacy grant is stale: it is not merged, and the legacy is removed
      stubOrgForUpdate(P, "Name77206");
      AuthorizationProvider authz = stubAuthz();
      IdentityID key = new IdentityID("Name77206", P);
      IdentityID legacyKey = new IdentityID(P, P);
      Permission current = new Permission();
      current.setUserGrantsForOrg(ResourceAction.ADMIN, Set.of("visible"), P);
      Permission legacy = new Permission();
      legacy.setUserGrantsForOrg(ResourceAction.ADMIN, Set.of("visible", "hidden"), P);
      when(authz.getPermission(ResourceType.SECURITY_ORGANIZATION, key, P)).thenReturn(current);
      when(authz.getPermission(ResourceType.SECURITY_ORGANIZATION, legacyKey, P)).thenReturn(legacy);
      when(securityProvider.checkAnyPermission(
         principal, ResourceType.SECURITY_USER, new IdentityID("visible", P).convertToKey(),
         EnumSet.of(ResourceAction.ADMIN)))
         .thenReturn(true);
      SecurityOrganization request = newOrgRequest(P, "Name77206");
      request.setAdminIdentities(adminUsers());

      service.updateOrganization(P, request, principal);

      Permission grant = captureOrgGrant(authz, key, P);
      assertEquals(Set.of(), grant.getOrgScopedUserGrants(ResourceAction.ADMIN, P));
      verify(authz, never()).getPermission(any(ResourceType.class), eq(legacyKey), any());
      verify(authz).removePermission(ResourceType.SECURITY_ORGANIZATION, legacyKey, P);
      verifyOnlyExplicitBucketCalls(authz);
   }

   @Test
   void updateOrganization_renameBackToId_legacyAndNameIdGrants_overwritesStaleLegacyGrantees()
      throws Exception
   {
      // renaming back to the id overwrites the stale legacy (id, id) grant, which the check
      // ignored while the (name, id) grant existed (Bug #77185), without merging its grantees
      stubOrgForUpdate(P, "Name77206");
      AuthorizationProvider authz = stubAuthz();
      IdentityID oldKey = new IdentityID("Name77206", P);
      IdentityID legacyKey = new IdentityID(P, P);
      Permission current = new Permission();
      current.setUserGrantsForOrg(ResourceAction.ADMIN, Set.of("visible"), P);
      Permission legacy = new Permission();
      legacy.setUserGrantsForOrg(ResourceAction.ADMIN, Set.of("visible", "hidden"), P);
      when(authz.getPermission(ResourceType.SECURITY_ORGANIZATION, oldKey, P)).thenReturn(current);
      when(authz.getPermission(ResourceType.SECURITY_ORGANIZATION, legacyKey, P)).thenReturn(legacy);
      when(securityProvider.checkAnyPermission(
         principal, ResourceType.SECURITY_USER, new IdentityID("visible", P).convertToKey(),
         EnumSet.of(ResourceAction.ADMIN)))
         .thenReturn(true);
      SecurityOrganization request = newOrgRequest(P, P);
      request.setAdminIdentities(adminUsers());

      service.updateOrganization(P, request, principal);

      verifyOrgGrantDelegated(oldKey, legacyKey, P);
      Permission grant = captureOrgGrant(authz, legacyKey, P);
      assertEquals(Set.of(), grant.getOrgScopedUserGrants(ResourceAction.ADMIN, P));
      verify(authz, never()).getPermission(any(ResourceType.class), eq(legacyKey), any());
      verify(authz).removePermission(ResourceType.SECURITY_ORGANIZATION, oldKey, P);
      verify(authz, never()).removePermission(any(ResourceType.class), eq(legacyKey), any());
      verifyOnlyExplicitBucketCalls(authz);
   }

   @Test
   void updateOrganization_revoke_legacyAndNameIdGrants_dropsStaleLegacyEditedFlag()
      throws Exception
   {
      // the check ignores the legacy grant while a (name, id) grant exists (Bug #77185), so its
      // edited flag is not carried onto the (name, id) grant
      stubOrgForUpdate(P, "Name77206");
      AuthorizationProvider authz = stubAuthz();
      IdentityID key = new IdentityID("Name77206", P);
      IdentityID legacyKey = new IdentityID(P, P);
      Permission current = new Permission();
      current.setUserGrantsForOrg(ResourceAction.ADMIN, Set.of("visible"), P);
      Permission legacy = new Permission();
      legacy.setUserGrantsForOrg(ResourceAction.ADMIN, Set.of("visible"), P);
      legacy.updateGrantAllByOrg(P, true);
      when(authz.getPermission(ResourceType.SECURITY_ORGANIZATION, key, P)).thenReturn(current);
      when(authz.getPermission(ResourceType.SECURITY_ORGANIZATION, legacyKey, P)).thenReturn(legacy);
      SecurityOrganization request = newOrgRequest(P, "Name77206");
      request.setAdminIdentities(adminUsers());

      service.updateOrganization(P, request, principal);

      Permission grant = captureOrgGrant(authz, key, P);
      assertFalse(grant.hasOrgEditedGrantAll(P));
      verify(authz).removePermission(ResourceType.SECURITY_ORGANIZATION, legacyKey, P);
      verifyOnlyExplicitBucketCalls(authz);
   }

   @Test
   void updateOrganization_renameBackToId_revoke_legacyAndNameIdGrants_dropsStaleLegacyEditedFlag()
      throws Exception
   {
      // renaming back to the id overwrites the stale legacy grant, its edited flag is not carried
      stubOrgForUpdate(P, "Name77206");
      AuthorizationProvider authz = stubAuthz();
      IdentityID oldKey = new IdentityID("Name77206", P);
      IdentityID legacyKey = new IdentityID(P, P);
      Permission legacy = new Permission();
      legacy.updateGrantAllByOrg(P, true);
      when(authz.getPermission(ResourceType.SECURITY_ORGANIZATION, oldKey, P))
         .thenReturn(new Permission());
      when(authz.getPermission(ResourceType.SECURITY_ORGANIZATION, legacyKey, P)).thenReturn(legacy);
      SecurityOrganization request = newOrgRequest(P, P);
      request.setAdminIdentities(adminUsers());

      service.updateOrganization(P, request, principal);

      Permission grant = captureOrgGrant(authz, legacyKey, P);
      assertFalse(grant.hasOrgEditedGrantAll(P));
      verifyOnlyExplicitBucketCalls(authz);
   }

   @Test
   void updateOrganization_revoke_uneditedGrants_doesNotSetEditedFlag() throws Exception {
      // an explicit revoke leaves the edited flag as it was, it does not force it on
      stubOrgForUpdate(P, "Name77206");
      AuthorizationProvider authz = stubAuthz();
      IdentityID key = new IdentityID("Name77206", P);
      IdentityID legacyKey = new IdentityID(P, P);
      when(authz.getPermission(ResourceType.SECURITY_ORGANIZATION, key, P))
         .thenReturn(new Permission());
      when(authz.getPermission(ResourceType.SECURITY_ORGANIZATION, legacyKey, P))
         .thenReturn(new Permission());
      SecurityOrganization request = newOrgRequest(P, "Name77206");
      request.setAdminIdentities(adminUsers());

      service.updateOrganization(P, request, principal);

      assertFalse(captureOrgGrant(authz, key, P).hasOrgEditedGrantAll(P));
      verifyOnlyExplicitBucketCalls(authz);
   }

   @Test
   void updateOrganization_renameBackToId_noAdminIdentities_overwritesStaleLegacyGrantees()
      throws Exception
   {
      // an omitted list keeps the effective admins, the (name, id) grant. The legacy (id, id)
      // grant was ignored by the check while that grant existed (Bug #77185), so moving the grant
      // onto the legacy key overwrites it and a grantee only in the legacy grant is not revived
      stubOrgForUpdate(P, "Name77206");
      AuthorizationProvider authz = stubAuthz();
      IdentityID oldKey = new IdentityID("Name77206", P);
      IdentityID legacyKey = new IdentityID(P, P);
      Permission current = new Permission();
      current.setUserGrantsForOrg(ResourceAction.ADMIN, Set.of("a"), P);
      Permission legacy = new Permission();
      legacy.setUserGrantsForOrg(ResourceAction.ADMIN, Set.of("a", "b"), P);
      legacy.updateGrantAllByOrg(P, true);
      when(authz.getPermission(ResourceType.SECURITY_ORGANIZATION, oldKey, P)).thenReturn(current);
      when(authz.getPermission(ResourceType.SECURITY_ORGANIZATION, legacyKey, P)).thenReturn(legacy);

      service.updateOrganization(P, newOrgRequest(P, P), principal);

      verifyOrgGrantNotDelegated();
      verify(authz).setPermission(eq(ResourceType.SECURITY_ORGANIZATION), eq(legacyKey),
                                  same(current), eq(P));
      assertEquals(Set.of(new IdentityID("a", P)),
                   current.getOrgScopedUserGrants(ResourceAction.ADMIN, P));
      assertFalse(current.hasOrgEditedGrantAll(P));
      verify(authz, never()).getPermission(any(ResourceType.class), eq(legacyKey), any());
      verify(authz).removePermission(ResourceType.SECURITY_ORGANIZATION, oldKey, P);
      verify(authz, never()).removePermission(any(ResourceType.class), eq(legacyKey), any());
      verifyOnlyExplicitBucketCalls(authz);
   }

   @Test
   void updateOrganization_revokeFromHostOrg_keepsTargetHiddenGranteesAndOtherOrgScopes()
      throws Exception
   {
      // the caller's ambient org is host-org; the merge must read the target org's grantees, and
      // must not re-scope a grantee of another org to the target org
      when(orgManager.getCurrentOrgID()).thenReturn("host-org");
      stubOrgForUpdate(P, "Name77206");
      AuthorizationProvider authz = stubAuthz();
      IdentityID key = new IdentityID("Name77206", P);
      Permission existing = new Permission();
      existing.setUserGrantsForOrg(ResourceAction.ADMIN, Set.of("hidden"), P);
      existing.setUserGrantsForOrg(ResourceAction.ADMIN, Set.of("x"), Q);
      when(authz.getPermission(ResourceType.SECURITY_ORGANIZATION, key, P)).thenReturn(existing);
      SecurityOrganization request = newOrgRequest(P, "Name77206");
      request.setAdminIdentities(adminUsers());

      service.updateOrganization(P, request, principal);

      verifyOrgGrantDelegated(key, key, P);
      Permission grant = captureOrgGrant(authz, key, P);
      assertEquals(Set.of(new IdentityID("hidden", P)),
                   grant.getOrgScopedUserGrants(ResourceAction.ADMIN, P));
      assertEquals(Set.of(new IdentityID("x", Q)),
                   grant.getOrgScopedUserGrants(ResourceAction.ADMIN, Q));
      verifyOnlyExplicitBucketCalls(authz);
   }

   @Test
   void updateOrganization_bodyWithoutId_keepsPathIdAndTargetBucket() throws Exception {
      stubOrgForUpdate(P, "Name77206");
      AuthorizationProvider authz = stubAuthz();
      IdentityID u = new IdentityID("u", P);
      allowUserAdmin(u);
      IdentityID key = new IdentityID("Name77206", P);
      SecurityOrganization request = newOrgRequest(null, "Name77206");
      request.setAdminIdentities(adminUsers(u));

      service.updateOrganization(P, request, principal);

      ArgumentCaptor<EditOrganizationPaneModel> model =
         ArgumentCaptor.forClass(EditOrganizationPaneModel.class);
      verify(identityService).setIdentity(any(), model.capture(), any(), eq(principal));
      assertEquals(P, model.getValue().id(), "an omitted id is not an id rename");
      Permission grant = captureOrgGrant(authz, key, P);
      assertEquals(Set.of(u), grant.getOrgScopedUserGrants(ResourceAction.ADMIN, P));
      verifyOnlyExplicitBucketCalls(authz);
   }

   // ── organization self grant, global roles (Bug #77274 / Bug #77275) ─────
   //
   // convertAdminIdentitiesModel adds every requested existing role when the caller has ADMIN on
   // its org's role root, which an org admin has, so a global role such as Administrator@null
   // reached setOrganizationPermissions, which wrote the global role grants unconditionally. Only
   // a site admin may change them (as in EM, Bug #77254), a non site admin keeps them unchanged.

   private void stubGlobalRoleOrgUpdate(AuthorizationProvider authz, IdentityID key) {
      stubOrgForUpdate(P, "Name77206");
      Permission existing = new Permission();
      existing.setRoleGrantsForOrg(ResourceAction.ADMIN, Set.of("R1"), null);
      when(authz.getPermission(ResourceType.SECURITY_ORGANIZATION, key, P)).thenReturn(existing);
      when(securityProvider.checkPermission(eq(principal), eq(ResourceType.SECURITY_ROLE),
                                            anyString(), eq(ResourceAction.ADMIN)))
         .thenReturn(true);
      when(securityProvider.getRole(new IdentityID("Administrator", null)))
         .thenReturn(mock(Role.class));
   }

   private static SecurityOrganization globalRoleAdminRequest() {
      AdminIdentities ids = new AdminIdentities();
      ids.setRoles(List.of(new IdentityID("Administrator", null)));
      SecurityOrganization request = new SecurityOrganization();
      request.setId(P);
      request.setName("Name77206");
      request.setAdminIdentities(ids);
      return request;
   }

   private static Set<String> globalRoleNames(Permission permission) {
      Set<String> names = new HashSet<>();

      for(Permission.PermissionIdentity grant : permission.getAllRoleGrants(ResourceAction.ADMIN)) {
         if(grant.getOrganizationID() == null) {
            names.add(grant.getName());
         }
      }

      return names;
   }

   @Test
   void updateOrganization_nonSiteAdmin_globalRoleAdminIgnored_existingGlobalRoleKept()
      throws Exception
   {
      AuthorizationProvider authz = stubAuthz();
      IdentityID key = new IdentityID("Name77206", P);
      stubGlobalRoleOrgUpdate(authz, key);
      // after the stub, which makes the caller a site admin: an org admin of P updates its own
      // org, the only org a non site admin may update (Bug #77216)
      when(orgManager.isSiteAdmin(principal)).thenReturn(false);
      when(((XPrincipal) principal).getOrgId()).thenReturn(P);

      service.updateOrganization(P, globalRoleAdminRequest(), principal);

      assertEquals(Set.of("R1"), globalRoleNames(captureOrgGrant(authz, key, P)));
   }

   @Test
   void updateOrganization_siteAdmin_globalRoleAdminWritten() throws Exception {
      when(orgManager.isSiteAdmin(principal)).thenReturn(true);
      AuthorizationProvider authz = stubAuthz();
      IdentityID key = new IdentityID("Name77206", P);
      stubGlobalRoleOrgUpdate(authz, key);
      when(securityProvider.checkAnyPermission(any(), any(), anyString(), any())).thenReturn(true);

      service.updateOrganization(P, globalRoleAdminRequest(), principal);

      assertEquals(Set.of("Administrator"), globalRoleNames(captureOrgGrant(authz, key, P)));
   }

   // -- Bug #77216: a non site admin only manages their own organization ---------------------
   // DefaultCheckPermissionStrategy passed SECURITY_ORGANIZATION ADMIN for a bare org id of
   // another org, so an org admin of org1 could PUT /organizations/host-org and overwrite (and
   // delete the members of) the host org. The service must confine the caller to their own org
   // whatever the permission check returns, so the stubs below grant it for every org.

   private void grantOrgAdminEverywhere(Principal caller) {
      when(securityProvider.checkPermission(eq(caller), eq(ResourceType.SECURITY_ORGANIZATION),
                                            anyString(), eq(ResourceAction.ADMIN)))
         .thenReturn(true);
   }

   private void stubOrganization(String id, String name) {
      FSOrganization org = new FSOrganization(id);
      org.setName(name);
      when(securityProvider.getOrganization(id)).thenReturn(org);
      doReturn(org).when(editableProvider).getOrganization(id);
      doReturn(name).when(editableProvider).getOrgNameFromID(id);
   }

   @Test
   void updateOrganization_otherOrg_rejectedBeforeWrite() throws Exception {
      grantOrgAdminEverywhere(principal);
      stubOrganization("host-org", "Host Organization");
      when(securityProvider.getOrganizationIDs()).thenReturn(new String[]{ "host-org", "org1" });

      assertRefusedBeforeMutation(UnauthorizedAccessException.class,
                   () -> service.updateOrganization("host-org",
                                                    newOrgRequest("host-org", "Host Organization"),
                                                    principal));

      verify(identityService, never()).setIdentity(any(), any(), any(), any());
   }

   @Test
   void updateOrganization_caseVariantOfOwnOrgId_rejected() throws Exception {
      // org ids can differ only in case in legacy case-variant pairs, so the match is exact
      grantOrgAdminEverywhere(principal);
      stubOrganization("ORG1", "Org One Variant");

      assertRefusedBeforeMutation(UnauthorizedAccessException.class,
                   () -> service.updateOrganization("ORG1",
                                                    newOrgRequest("ORG1", "Org One Variant"),
                                                    principal));

      verify(identityService, never()).setIdentity(any(), any(), any(), any());
   }

   @Test
   void getOrganization_otherOrg_rejected() throws Exception {
      grantOrgAdminEverywhere(principal);
      stubOrganization("host-org", "Host Organization");

      assertThrows(UnauthorizedAccessException.class,
                   () -> service.getOrganization("host-org", principal));

      verify(identityService, never()).getIdentityInfo(any(), anyInt(), any());
   }

   @Test
   void getOrganization_missingOtherOrg_rejectedWithoutRevealingIt() throws Exception {
      grantOrgAdminEverywhere(principal);

      assertThrows(UnauthorizedAccessException.class,
                   () -> service.getOrganization("no-such-org", principal));
   }

   @Test
   void deleteOrganization_otherOrg_rejectedBeforeDelete() throws Exception {
      grantOrgAdminEverywhere(principal);
      stubOrganization("host-org", "Host Organization");

      assertThrows(UnauthorizedAccessException.class,
                   () -> service.deleteOrganization("host-org", principal));

      verifyNoInteractions(identityService);
   }

   @Test
   void updateOrganization_ownOrg_proceeds() throws Exception {
      grantOrgAdminEverywhere(principal);
      stubOrganization("org1", "Org One");
      when(securityProvider.getOrganizationIDs()).thenReturn(new String[]{ "host-org", "org1" });

      service.updateOrganization("org1", newOrgRequest("org1", "Org One"), principal);

      verify(identityService).setIdentity(any(Organization.class),
                                          any(EditOrganizationPaneModel.class),
                                          eq(editableProvider), eq(principal));
   }

   @Test
   void updateOrganization_homeOrgUsedNotSwitchedCurrentOrg() throws Exception {
      // the current org can be moved to another org (curr_org_id), the home org is what counts.
      // The current org stubs are the caller's switched context, isOwnOrganization() doesn't read
      // them, so a regression to the current org would make this test fail
      XPrincipal caller = mock(XPrincipal.class, withSettings().lenient());
      when(caller.getName()).thenReturn(new IdentityID("caller", "org1").convertToKey());
      when(caller.getOrgId()).thenReturn("org1");
      when(caller.getCurrentOrgId()).thenReturn("host-org");
      when(orgManager.getCurrentOrgID(caller)).thenReturn("host-org");
      grantOrgAdminEverywhere(caller);
      stubOrganization("host-org", "Host Organization");
      stubOrganization("org1", "Org One");
      when(securityProvider.getOrganizationIDs()).thenReturn(new String[]{ "host-org", "org1" });

      assertRefusedBeforeMutation(UnauthorizedAccessException.class,
                   () -> service.updateOrganization("host-org",
                                                    newOrgRequest("host-org", "Host Organization"),
                                                    caller));
      verify(identityService, never()).setIdentity(any(), any(), any(), any());

      service.updateOrganization("org1", newOrgRequest("org1", "Org One"), caller);
      verify(identityService).setIdentity(any(Organization.class),
                                          any(EditOrganizationPaneModel.class),
                                          eq(editableProvider), eq(caller));
   }

   @Test
   void updateOrganization_siteAdmin_managesOtherOrg() throws Exception {
      when(orgManager.isSiteAdmin(principal)).thenReturn(true);
      grantOrgAdminEverywhere(principal);
      stubOrganization("org2", "Org Two");
      when(securityProvider.getOrganizationIDs()).thenReturn(new String[]{ "host-org", "org1", "org2" });

      service.updateOrganization("org2", newOrgRequest("org2", "Org Two"), principal);

      verify(identityService).setIdentity(any(Organization.class),
                                          any(EditOrganizationPaneModel.class),
                                          eq(editableProvider), eq(principal));
   }

   @Test
   void updateOrganization_ownOrgWithoutPermission_stillRejected() throws Exception {
      // the own-org confinement is in addition to, not instead of, the permission check
      stubOrganization("org1", "Org One");

      assertRefusedBeforeMutation(UnauthorizedAccessException.class,
                   () -> service.updateOrganization("org1", newOrgRequest("org1", "Org One"),
                                                    principal));
      verify(identityService, never()).setIdentity(any(), any(), any(), any());
   }

   // ── Bug #77099: REST rename runs the storage/MV/data-cycle migrations ───
   //
   // updateUser/updateGroup renamed the identity through setIdentity but never ran the rename
   // migrations that EM's editUser/editGroup run (private assets, task XML, MV, data-cycle
   // recipients, dependency storage, recycle bin, favorites). The migration must run on the
   // stored identity's org, only after the provider rename (and, for groups, the parent-group
   // move) succeeded, and only when the name actually changed.

   private static final IdentityID SALES_USER = new IdentityID("sales", "org2");
   private static final IdentityID SALES_GROUP = new IdentityID("sales", "org2");

   private FSUser stubUpdatableUser(IdentityID userId) {
      FSUser oldUser = new FSUser(userId);
      when(securityProvider.getUser(userId)).thenReturn(oldUser);
      when(securityProvider.checkPermission(principal, ResourceType.SECURITY_USER,
                                            userId.convertToKey(), ResourceAction.ADMIN))
         .thenReturn(true);
      when(editableProvider.getUser(userId)).thenReturn(oldUser);
      when(orgManager.isSiteAdmin(principal)).thenReturn(true);
      return oldUser;
   }

   private FSGroup stubUpdatableGroup(IdentityID groupId) {
      FSGroup oldGroup = new FSGroup(groupId);
      when(securityProvider.getGroup(groupId)).thenReturn(oldGroup);
      when(securityProvider.checkPermission(principal, ResourceType.SECURITY_GROUP,
                                            groupId.convertToKey(), ResourceAction.ADMIN))
         .thenReturn(true);
      when(editableProvider.getGroup(groupId)).thenReturn(oldGroup);
      when(orgManager.isSiteAdmin(principal)).thenReturn(true);
      return oldGroup;
   }

   private static SecurityUser userRequest(IdentityID bodyId) {
      SecurityUser request = new SecurityUser();
      request.setIdentityID(bodyId);
      return request;
   }

   private static SecurityGroup groupRequest(IdentityID bodyId) {
      SecurityGroup request = new SecurityGroup();
      request.setIdentityID(bodyId);
      return request;
   }

   @Test
   void updateUser_renamed_migratesUserRenameOnStoredOrgAfterSetIdentity() throws Exception {
      FSUser oldUser = stubUpdatableUser(SALES_USER);
      IdentityID renamed = new IdentityID("sales3", "org2");
      AuthorizationProvider authz = stubAuthz();
      Permission grant = new Permission();
      when(authz.getPermission(ResourceType.SECURITY_USER, SALES_USER)).thenReturn(grant);

      // The body carries a different org; the migration must still use the stored/path org.
      service.updateUser(SALES_USER, userRequest(new IdentityID("sales3", "org3")), principal);

      // the body omits adminIdentities, so the grant is moved to the new key (Bug #77326)
      InOrder order = inOrder(identityService, authz, userTreeService);
      order.verify(identityService).setIdentity(eq(oldUser), any(EditUserPaneModel.class),
                                                eq(editableProvider), eq(principal));
      order.verify(authz).setPermission(ResourceType.SECURITY_USER, renamed, grant);
      order.verify(authz).removePermission(ResourceType.SECURITY_USER, SALES_USER);
      order.verify(userTreeService).migrateUserRename(SALES_USER, renamed);
      verifyNoMoreInteractions(userTreeService);
   }

   @Test
   void updateUser_nameUnchanged_doesNotMigrate() throws Exception {
      FSUser oldUser = stubUpdatableUser(SALES_USER);
      SecurityUser request = userRequest(SALES_USER);
      request.setEmails(List.of("sales@example.com"));

      service.updateUser(SALES_USER, request, principal);

      verify(identityService).setIdentity(eq(oldUser), any(EditUserPaneModel.class),
                                          eq(editableProvider), eq(principal));
      verifyNoInteractions(userTreeService);
   }

   @Test
   void updateUser_adminDenied_doesNotMigrate() throws Exception {
      stubUpdatableUser(SALES_USER);
      when(securityProvider.checkPermission(principal, ResourceType.SECURITY_USER,
                                            SALES_USER.convertToKey(), ResourceAction.ADMIN))
         .thenReturn(false);

      assertRefusedBeforeMutation(UnauthorizedAccessException.class, () ->
         service.updateUser(SALES_USER, userRequest(new IdentityID("sales3", "org2")), principal));

      verify(identityService, never()).setIdentity(any(), any(), any(), any());
      verifyNoInteractions(userTreeService);
   }

   @Test
   void updateUser_setIdentityThrows_doesNotMigrate() throws Exception {
      stubUpdatableUser(SALES_USER);
      doThrow(new IllegalStateException("provider write failed"))
         .when(identityService).setIdentity(any(), any(), any(), any());

      assertThrows(IllegalStateException.class, () ->
         service.updateUser(SALES_USER, userRequest(new IdentityID("sales3", "org2")), principal));

      verifyNoInteractions(userTreeService);
   }

   @Test
   void updateUser_migrationThrows_propagates() throws Exception {
      stubUpdatableUser(SALES_USER);
      doThrow(new IllegalStateException("storage migration failed"))
         .when(userTreeService).migrateUserRename(any(), any());

      IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
         service.updateUser(SALES_USER, userRequest(new IdentityID("sales3", "org2")), principal));

      assertEquals("storage migration failed", ex.getMessage());
   }

   @Test
   void updateGroup_renamed_migratesGroupRenameLastOnStoredOrg() throws Exception {
      FSGroup oldGroup = stubUpdatableGroup(SALES_GROUP);
      IdentityID renamed = new IdentityID("sales2", "org2");
      AuthorizationProvider authz = stubAuthz();
      Permission grant = new Permission();
      when(authz.getPermission(ResourceType.SECURITY_GROUP, SALES_GROUP)).thenReturn(grant);

      // The body carries a different org; the migration must still use the stored/path org.
      service.updateGroup(SALES_GROUP, groupRequest(new IdentityID("sales2", "org3")), principal);

      // The migration must follow updateParentGroups (which re-saves the group under the new
      // ID), so a migration failure can't skip the provider-level parent-group move. The body
      // omits adminIdentities, so the grant is moved to the new key (Bug #77326).
      InOrder order = inOrder(identityService, authz, editableProvider, userTreeService);
      order.verify(identityService).setIdentity(eq(oldGroup), any(EditGroupPaneModel.class),
                                                eq(editableProvider), eq(principal));
      order.verify(authz).setPermission(ResourceType.SECURITY_GROUP, renamed, grant);
      order.verify(authz).removePermission(ResourceType.SECURITY_GROUP, SALES_GROUP);
      order.verify(editableProvider).setGroup(eq(renamed), any(FSGroup.class));
      order.verify(userTreeService).migrateGroupRename(SALES_GROUP, renamed);
      verifyNoMoreInteractions(userTreeService);
   }

   @Test
   void updateGroup_nameUnchanged_doesNotMigrate() throws Exception {
      FSGroup oldGroup = stubUpdatableGroup(SALES_GROUP);

      service.updateGroup(SALES_GROUP, groupRequest(SALES_GROUP), principal);

      verify(identityService).setIdentity(eq(oldGroup), any(EditGroupPaneModel.class),
                                          eq(editableProvider), eq(principal));
      verifyNoInteractions(userTreeService);
   }

   @Test
   void updateGroup_adminDenied_doesNotMigrate() throws Exception {
      stubUpdatableGroup(SALES_GROUP);
      when(securityProvider.checkPermission(principal, ResourceType.SECURITY_GROUP,
                                            SALES_GROUP.convertToKey(), ResourceAction.ADMIN))
         .thenReturn(false);

      assertRefusedBeforeMutation(UnauthorizedAccessException.class, () ->
         service.updateGroup(SALES_GROUP, groupRequest(new IdentityID("sales2", "org2")), principal));

      verify(identityService, never()).setIdentity(any(), any(), any(), any());
      verifyNoInteractions(userTreeService);
   }

   @Test
   void updateGroup_setIdentityThrows_doesNotMigrate() throws Exception {
      stubUpdatableGroup(SALES_GROUP);
      doThrow(new IllegalStateException("provider write failed"))
         .when(identityService).setIdentity(any(), any(), any(), any());

      assertThrows(IllegalStateException.class, () ->
         service.updateGroup(SALES_GROUP, groupRequest(new IdentityID("sales2", "org2")), principal));

      verifyNoInteractions(userTreeService);
   }

   @Test
   void updateGroup_migrationThrows_propagates() throws Exception {
      stubUpdatableGroup(SALES_GROUP);
      doThrow(new IllegalStateException("storage migration failed"))
         .when(userTreeService).migrateGroupRename(any(), any());

      IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
         service.updateGroup(SALES_GROUP, groupRequest(new IdentityID("sales2", "org2")), principal));

      assertEquals("storage migration failed", ex.getMessage());
   }

   // ── Bug #77113: REST role rename runs the VPM hidden-column role migration ─
   //
   // updateRole renamed the role through setIdentity but never ran EM editRole's
   // migrateRoleRename, so VPM HiddenColumns kept listing the old role name and users of the
   // renamed role lost the hidden-column exemption.

   private static final IdentityID ANALYST_ROLE = new IdentityID("analyst", "org2");
   // the org ID a site admin passes as ?orgId to address a global identity
   private static final String GLOBAL_ORG_KEY = "__GLOBAL__";

   private FSRole stubUpdatableRole(IdentityID pathId, FSRole oldRole) {
      when(securityProvider.getRole(pathId)).thenReturn(oldRole);
      when(securityProvider.checkPermission(principal, ResourceType.SECURITY_ROLE,
                                            pathId.convertToKey(), ResourceAction.ADMIN))
         .thenReturn(true);
      when(editableProvider.getRole(pathId)).thenReturn(oldRole);
      when(identityService.getIdentityInfo(pathId, Identity.ROLE, editableProvider))
         .thenReturn(new IdentityInfo());
      when(orgManager.isSiteAdmin(principal)).thenReturn(true);
      return oldRole;
   }

   private FSRole stubUpdatableRole(IdentityID roleId) {
      return stubUpdatableRole(roleId, new FSRole(roleId));
   }

   private static SecurityRole roleRequest(IdentityID bodyId) {
      SecurityRole request = new SecurityRole();
      request.setIdentityID(bodyId);
      return request;
   }

   @Test
   void updateRole_renamed_migratesRoleRenameLastOnStoredOrg() throws Exception {
      FSRole oldRole = stubUpdatableRole(ANALYST_ROLE);
      IdentityID renamed = new IdentityID("analyst2", "org2");
      AuthorizationProvider authz = stubAuthz();
      Permission grant = new Permission();
      when(authz.getPermission(ResourceType.SECURITY_ROLE, ANALYST_ROLE)).thenReturn(grant);

      // The body carries a different org; the migration must still use the stored/path org.
      service.updateRole(ANALYST_ROLE, roleRequest(new IdentityID("analyst2", "org3")), principal);

      // the body omits adminIdentities, so the grant is moved to the new key (Bug #77326)
      InOrder order = inOrder(identityService, authz, userTreeService);
      order.verify(identityService).setIdentity(eq(oldRole), any(EditRolePaneModel.class),
                                                eq(editableProvider), eq(principal));
      order.verify(authz).setPermission(ResourceType.SECURITY_ROLE, renamed, grant);
      order.verify(authz).removePermission(ResourceType.SECURITY_ROLE, ANALYST_ROLE);
      order.verify(userTreeService).migrateRoleRename(ANALYST_ROLE, renamed);
      verifyNoMoreInteractions(userTreeService);
   }

   @Test
   void updateRole_nameUnchanged_doesNotMigrate() throws Exception {
      FSRole oldRole = stubUpdatableRole(ANALYST_ROLE);

      service.updateRole(ANALYST_ROLE, roleRequest(ANALYST_ROLE), principal);

      verify(identityService).setIdentity(eq(oldRole), any(EditRolePaneModel.class),
                                          eq(editableProvider), eq(principal));
      verifyNoInteractions(userTreeService);
   }

   @Test
   void updateRole_noIdentityIdInBody_keepsNameAndDoesNotMigrate() throws Exception {
      FSRole oldRole = stubUpdatableRole(ANALYST_ROLE);

      // Bug #77171: an omitted identityID keeps the role's name (the path name), so the update
      // is not a rename and runs no VPM rename migration
      service.updateRole(ANALYST_ROLE, roleRequest(null), principal);

      ArgumentCaptor<EditRolePaneModel> captor = ArgumentCaptor.forClass(EditRolePaneModel.class);
      verify(identityService).setIdentity(eq(oldRole), captor.capture(), eq(editableProvider),
                                          eq(principal));
      assertEquals(ANALYST_ROLE.name, captor.getValue().name());
      // an omitted adminIdentities keeps the role's grants (Bug #77326)
      verify(identityService, never())
         .setIdentityPermissions(any(), any(), any(), any(), any(), any());
      verifyNoInteractions(userTreeService);
   }

   @Test
   void updateRole_adminDenied_doesNotMigrate() throws Exception {
      stubUpdatableRole(ANALYST_ROLE);
      when(securityProvider.checkPermission(principal, ResourceType.SECURITY_ROLE,
                                            ANALYST_ROLE.convertToKey(), ResourceAction.ADMIN))
         .thenReturn(false);

      assertRefusedBeforeMutation(UnauthorizedAccessException.class, () ->
         service.updateRole(ANALYST_ROLE, roleRequest(new IdentityID("analyst2", "org2")), principal));

      verify(identityService, never()).setIdentity(any(), any(), any(), any());
      verifyNoInteractions(userTreeService);
   }

   @Test
   void updateRole_setIdentityThrows_doesNotMigrate() throws Exception {
      stubUpdatableRole(ANALYST_ROLE);
      doThrow(new IllegalStateException("provider write failed"))
         .when(identityService).setIdentity(any(), any(), any(), any());

      assertThrows(IllegalStateException.class, () ->
         service.updateRole(ANALYST_ROLE, roleRequest(new IdentityID("analyst2", "org2")), principal));

      verifyNoInteractions(userTreeService);
   }

   @Test
   void updateRole_migrationThrows_propagates() throws Exception {
      stubUpdatableRole(ANALYST_ROLE);
      doThrow(new IllegalStateException("vpm migration failed"))
         .when(userTreeService).migrateRoleRename(any(), any());

      IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
         service.updateRole(ANALYST_ROLE, roleRequest(new IdentityID("analyst2", "org2")), principal));

      assertEquals("vpm migration failed", ex.getMessage());
   }

   // The migration's old role ID is derived from the stored role (the one setIdentity renames),
   // falling back to the path for a missing name or org. With the exact-key file provider the two
   // agree, so these stub a stored role that differs from the path to pin the derivation.

   @Test
   void updateRole_storedOrgDiffersFromPath_migratesOnStoredOrg() throws Exception {
      stubUpdatableRole(ANALYST_ROLE, new FSRole(new IdentityID("analyst", "org5")));

      service.updateRole(ANALYST_ROLE, roleRequest(new IdentityID("analyst2", "org3")), principal);

      verify(userTreeService).migrateRoleRename(new IdentityID("analyst", "org5"),
                                                new IdentityID("analyst2", "org5"));
      verifyNoMoreInteractions(userTreeService);
   }

   @Test
   void updateRole_storedOrgNull_migratesOnPathOrg() throws Exception {
      stubUpdatableRole(ANALYST_ROLE, new FSRole(new IdentityID("analyst", null)));

      service.updateRole(ANALYST_ROLE, roleRequest(new IdentityID("analyst2", "org3")), principal);

      verify(userTreeService).migrateRoleRename(ANALYST_ROLE, new IdentityID("analyst2", "org2"));
      verifyNoMoreInteractions(userTreeService);
   }

   @Test
   void updateRole_storedNameDiffersFromPath_migratesStoredName() throws Exception {
      // hypothetical provider whose stored name differs from the path name (none do today); the stored name must win
      stubUpdatableRole(ANALYST_ROLE, new FSRole(new IdentityID("Analyst", "org2")));

      service.updateRole(ANALYST_ROLE, roleRequest(new IdentityID("analyst2", "org2")), principal);

      verify(userTreeService).migrateRoleRename(new IdentityID("Analyst", "org2"),
                                                new IdentityID("analyst2", "org2"));
      verifyNoMoreInteractions(userTreeService);
   }

   @Test
   void updateRole_storedNameAndOrgNull_migratesPathId() throws Exception {
      stubUpdatableRole(ANALYST_ROLE, new FSRole());

      service.updateRole(ANALYST_ROLE, roleRequest(new IdentityID("analyst2", "org2")), principal);

      verify(userTreeService).migrateRoleRename(ANALYST_ROLE, new IdentityID("analyst2", "org2"));
      verifyNoMoreInteractions(userTreeService);
   }

   // Real UserTreeService helpers (not a mock): the REST rename must reach the actual storage,
   // MV, data-cycle, dependency, recycle-bin and favorites migrations, scoped to the renamed
   // identity's org even when the caller's current org is a different one.

   private SecurityService serviceWithRealUserTree(UserTreeService realUserTree) {
      return new SecurityService(
         SecurityEngine.getSecurity(), identityService, mock(ActionPermissionService.class),
         mock(LocalizationSettingsService.class), mock(IdentityThemeService.class),
         mock(SystemAdminService.class), realUserTree,
         mock(CustomThemesManager.class));
   }

   private UserTreeService realUserTree(IndexedStorage storage, DataCycleManager cycleManager,
                                        MVManager mvManager, FavoritesService favorites,
                                        DependencyStorageService dependencies, RecycleBin recycleBin)
   {
      return new UserTreeService(
         mock(AuthenticationProviderService.class), mock(SystemAdminService.class), identityService,
         mock(LocalizationSettingsService.class), SecurityEngine.getSecurity(),
         mock(IdentityThemeService.class), mock(SimpMessagingTemplate.class), favorites,
         cycleManager, mock(LicenseManager.class), mvManager, storage,
         mock(CustomThemesManager.class), mock(DashboardRegistryManager.class),
         mock(XRepository.class), dependencies, recycleBin);
   }

   @Test
   void updateUser_renamed_realHelperMigratesAllStoresInUserOrgScope() throws Exception {
      stubUpdatableUser(SALES_USER);
      organizationManagerStatic.when(() -> OrganizationManager.runInOrgScope(anyString(), any()))
         .thenCallRealMethod();
      IndexedStorage storage = mock(IndexedStorage.class);
      DataCycleManager cycleManager = mock(DataCycleManager.class);
      MVManager mvManager = mock(MVManager.class);
      FavoritesService favorites = mock(FavoritesService.class);
      DependencyStorageService dependencies = mock(DependencyStorageService.class);
      RecycleBin recycleBin = mock(RecycleBin.class);
      List<String> orgsSeen = new java.util.ArrayList<>();
      doAnswer(inv -> orgsSeen.add(OrganizationContextHolder.getCurrentOrgId()))
         .when(storage).migrateStorageData(any(IdentityID.class), any(IdentityID.class), anyInt());
      SecurityService realService = serviceWithRealUserTree(
         realUserTree(storage, cycleManager, mvManager, favorites, dependencies, recycleBin));
      IdentityID renamed = new IdentityID("sales3", "org2");

      // The caller's session org is org1 (site admin acting on org2 via ?orgId).
      OrganizationContextHolder.setCurrentOrgId("org1");

      try {
         realService.updateUser(SALES_USER, userRequest(new IdentityID("sales3", "org3")), principal);
         assertEquals("org1", OrganizationContextHolder.getCurrentOrgId());
      }
      finally {
         OrganizationContextHolder.clear();
      }

      verify(storage).migrateStorageData(SALES_USER, renamed, Identity.USER);
      assertEquals(List.of("org2"), orgsSeen);
      verify(favorites).moveFavorites(SALES_USER.convertToKey(), renamed.convertToKey());
      verify(mvManager).migrateUserAssetsMV(SALES_USER, renamed);
      verify(mvManager).updateMVUser(SALES_USER, renamed);
      verify(cycleManager).updateCycleInfoNotify("sales", "sales3", true);
      verify(dependencies).migrateStorageData(SALES_USER, renamed);
      verify(recycleBin).renameUser(SALES_USER, renamed);
   }

   @Test
   void updateGroup_renamed_realHelperMigratesGroupStoresInGroupOrgScope() throws Exception {
      stubUpdatableGroup(SALES_GROUP);
      organizationManagerStatic.when(() -> OrganizationManager.runInOrgScope(anyString(), any()))
         .thenCallRealMethod();
      IndexedStorage storage = mock(IndexedStorage.class);
      DataCycleManager cycleManager = mock(DataCycleManager.class);
      MVManager mvManager = mock(MVManager.class);
      RecycleBin recycleBin = mock(RecycleBin.class);
      List<String> orgsSeen = new java.util.ArrayList<>();
      doAnswer(inv -> orgsSeen.add(OrganizationContextHolder.getCurrentOrgId()))
         .when(storage).migrateStorageData(any(IdentityID.class), any(IdentityID.class), anyInt());
      SecurityService realService = serviceWithRealUserTree(realUserTree(
         storage, cycleManager, mvManager, mock(FavoritesService.class),
         mock(DependencyStorageService.class), recycleBin));
      IdentityID renamed = new IdentityID("sales2", "org2");

      OrganizationContextHolder.setCurrentOrgId("org1");

      try {
         realService.updateGroup(SALES_GROUP, groupRequest(new IdentityID("sales2", "org3")), principal);
         assertEquals("org1", OrganizationContextHolder.getCurrentOrgId());
      }
      finally {
         OrganizationContextHolder.clear();
      }

      verify(storage).migrateStorageData(SALES_GROUP, renamed, Identity.GROUP);
      assertEquals(List.of("org2"), orgsSeen);
      verify(cycleManager).updateCycleInfoNotify("sales", "sales2", false);
      // group renames have no user-only side effects
      verifyNoInteractions(mvManager, recycleBin);
   }

   // Real UserTreeService.migrateRoleRename against real VPM HiddenColumns. Each org has one data
   // source whose data model holds a VPM "v" hiding columns from the given roles. Saves are
   // recorded as "save:<data source>@<current org>" so the org scope of each write is checked.

   private final List<String> vpmEvents = new java.util.ArrayList<>();

   private XRepository vpmRepository(java.util.Map<String, HiddenColumns> hiddenByOrg)
      throws Exception
   {
      XRepository repository = mock(XRepository.class);
      when(repository.getDataSourceFullNames(any(IdentityID.class)))
         .thenAnswer(inv -> new String[] { "ds-" + inv.<IdentityID>getArgument(0).getOrgID() });

      for(java.util.Map.Entry<String, HiddenColumns> entry : hiddenByOrg.entrySet()) {
         String dataSource = "ds-" + entry.getKey();
         VirtualPrivateModel vpm = new VirtualPrivateModel("v");
         vpm.setHiddenColumns(entry.getValue());
         XDataModel dataModel = mock(XDataModel.class);
         when(dataModel.getVirtualPrivateModelNames()).thenReturn(new String[] { "v" });
         when(dataModel.getVirtualPrivateModel("v")).thenReturn(vpm);
         doAnswer(inv -> vpmEvents.add(
            "save:" + dataSource + "@" + OrganizationContextHolder.getCurrentOrgId()))
            .when(dataModel).addVirtualPrivateModel(any(VirtualPrivateModel.class), anyBoolean());
         when(repository.getDataModel(dataSource)).thenReturn(dataModel);
      }

      return repository;
   }

   private static HiddenColumns hiddenFrom(String... roles) {
      HiddenColumns hidden = new HiddenColumns();

      for(String role : roles) {
         hidden.addRole(role);
      }

      return hidden;
   }

   private static List<String> rolesOf(HiddenColumns hidden) {
      return java.util.Collections.list(hidden.getRoles());
   }

   private SecurityService serviceWithRealVpmHelper(XRepository repository) {
      return serviceWithRealUserTree(new UserTreeService(
         mock(AuthenticationProviderService.class), mock(SystemAdminService.class), identityService,
         mock(LocalizationSettingsService.class), SecurityEngine.getSecurity(),
         mock(IdentityThemeService.class), mock(SimpMessagingTemplate.class),
         mock(FavoritesService.class), mock(DataCycleManager.class), mock(LicenseManager.class),
         mock(MVManager.class), mock(IndexedStorage.class), mock(CustomThemesManager.class),
         mock(DashboardRegistryManager.class), repository, mock(DependencyStorageService.class),
         mock(RecycleBin.class)));
   }

   private void renameRoleAsCallerInOrg1(SecurityService realService, IdentityID pathId,
                                         String newName) throws Exception
   {
      organizationManagerStatic.when(() -> OrganizationManager.runInOrgScope(anyString(), any()))
         .thenCallRealMethod();
      // The caller's session org is org1 (site admin acting on another org via ?orgId).
      OrganizationContextHolder.setCurrentOrgId("org1");

      try {
         realService.updateRole(pathId, roleRequest(new IdentityID(newName, "org3")), principal);
         assertEquals("org1", OrganizationContextHolder.getCurrentOrgId());
      }
      finally {
         OrganizationContextHolder.clear();
      }
   }

   @Test
   void updateRole_renamed_realHelperRenamesVpmRoleInRoleOrgOnly() throws Exception {
      stubUpdatableRole(ANALYST_ROLE);
      HiddenColumns org1Hidden = hiddenFrom("analyst");
      HiddenColumns org2Hidden = hiddenFrom("analyst");
      XRepository repository = vpmRepository(java.util.Map.of("org1", org1Hidden, "org2", org2Hidden));

      renameRoleAsCallerInOrg1(serviceWithRealVpmHelper(repository), ANALYST_ROLE, "analyst2");

      assertEquals(List.of("analyst2"), rolesOf(org2Hidden));
      // org1's "analyst" is a different role (org-scoped roles are per org)
      assertEquals(List.of("analyst"), rolesOf(org1Hidden));
      assertEquals(List.of("save:ds-org2@org2"), vpmEvents);
      verify(repository, never()).getDataSourceFullNames(new IdentityID("org1", "org1"));
   }

   @Test
   void updateRole_globalRoleNullStoredOrg_realHelperRenamesVpmRoleInEveryOrg() throws Exception {
      // Site admin renames a global role via ?orgId=__GLOBAL__; the stored role has a null org.
      IdentityID pathId = new IdentityID("gRole", GLOBAL_ORG_KEY);
      FSRole oldRole = new FSRole(new IdentityID("gRole", null));
      assertNull(oldRole.getOrganizationID());
      stubUpdatableRole(pathId, oldRole);
      when(securityProvider.getOrganizationIDs()).thenReturn(new String[] { "org1", "org2" });
      HiddenColumns org1Hidden = hiddenFrom("gRole");
      HiddenColumns org2Hidden = hiddenFrom("gRole", "other");
      XRepository repository = vpmRepository(java.util.Map.of("org1", org1Hidden, "org2", org2Hidden));

      renameRoleAsCallerInOrg1(serviceWithRealVpmHelper(repository), pathId, "gRole2");

      assertEquals(List.of("gRole2"), rolesOf(org1Hidden));
      assertEquals(List.of("other", "gRole2"), rolesOf(org2Hidden));
      assertEquals(List.of("save:ds-org1@org1", "save:ds-org2@org2"), vpmEvents);
   }

   @Test
   void updateRole_globalRoleLiteralGlobalStoredOrg_realHelperRenamesVpmRoleInEveryOrg()
      throws Exception
   {
      // A global role that an earlier Shell local update-role re-stored with the literal global
      // org key as its organization ID instead of null (Bug #77327, fixed: updateRole now passes
      // the null org to setIdentity, which re-stores the role with a null org). Stored data from
      // before the fix can still have the literal, so the migration must handle it.
      IdentityID pathId = new IdentityID("gRole", GLOBAL_ORG_KEY);
      FSRole oldRole = new FSRole(pathId);
      assertEquals(GLOBAL_ORG_KEY, oldRole.getOrganizationID());
      stubUpdatableRole(pathId, oldRole);
      when(securityProvider.getOrganizationIDs()).thenReturn(new String[] { "org1", "org2" });
      HiddenColumns org1Hidden = hiddenFrom("gRole");
      HiddenColumns org2Hidden = hiddenFrom("gRole");
      XRepository repository = vpmRepository(java.util.Map.of("org1", org1Hidden, "org2", org2Hidden));

      renameRoleAsCallerInOrg1(serviceWithRealVpmHelper(repository), pathId, "gRole2");

      assertEquals(List.of("gRole2"), rolesOf(org1Hidden));
      assertEquals(List.of("gRole2"), rolesOf(org2Hidden));
      assertEquals(List.of("save:ds-org1@org1", "save:ds-org2@org2"), vpmEvents);
      verify(repository, never()).getDataSourceFullNames(
         new IdentityID(GLOBAL_ORG_KEY, GLOBAL_ORG_KEY));
   }

   // ── Bug #77114: a rename rewrites the renamed identity's own permitted-identities entry ─
   //
   // A GET of a user/group/role returns its admin identities, which include the identity's own
   // ADMIN self grant under its current name. PUTting that body back with a new name passed the
   // old-name entry to setIdentityPermissions(), which replaces the grantees, so the renamed
   // identity's grant named the old (no longer existing) identity. The same-type entry naming the
   // old identity must be rewritten to the new one; every other entry is passed on unchanged.

   private void allowAdminOnEveryIdentity() {
      when(securityProvider.checkPermission(eq(principal), any(ResourceType.class), anyString(),
                                            eq(ResourceAction.ADMIN)))
         .thenReturn(true);
   }

   private static AdminIdentities adminIdentities(List<IdentityID> users, List<IdentityID> groups,
                                                  List<IdentityID> roles)
   {
      AdminIdentities ids = new AdminIdentities();
      ids.setUsers(users);
      ids.setGroups(groups);
      ids.setRoles(roles);
      return ids;
   }

   private static String permittedKey(int type, String name, String orgID) {
      return type + ":" + name + "@" + orgID;
   }

   @SuppressWarnings("unchecked")
   private Set<String> capturePermittedIdentities(ResourceType type) {
      ArgumentCaptor<List<IdentityModel>> captor = ArgumentCaptor.forClass(List.class);
      verify(identityService).setIdentityPermissions(
         any(), any(), eq(type), eq(principal), captor.capture(), any());
      Set<String> keys = new HashSet<>();

      for(IdentityModel model : captor.getValue()) {
         keys.add(permittedKey(model.type(), model.identityID().name, model.identityID().orgID));
      }

      return keys;
   }

   @Test
   void updateUser_renamed_rewritesOwnUserEntry_keepsOtherEntriesUnchanged() throws Exception {
      stubUpdatableUser(SALES_USER);
      allowAdminOnEveryIdentity();
      SecurityUser request = userRequest(new IdentityID("sales3", "org2"));
      // a same-named group and role are other identities; an entry of another org is not
      // re-stamped with the renamed user's org
      request.setAdminIdentities(adminIdentities(
         List.of(SALES_USER, new IdentityID("other", "org9")),
         List.of(new IdentityID("sales", "org2")), List.of(new IdentityID("sales", "org2"))));

      service.updateUser(SALES_USER, request, principal);

      assertEquals(Set.of(permittedKey(Identity.USER, "sales3", "org2"),
                          permittedKey(Identity.USER, "other", "org9"),
                          permittedKey(Identity.GROUP, "sales", "org2"),
                          permittedKey(Identity.ROLE, "sales", "org2")),
                   capturePermittedIdentities(ResourceType.SECURITY_USER));
   }

   @Test
   void updateUser_renamed_ownEntryWithoutOrg_rewritten() throws Exception {
      stubUpdatableUser(SALES_USER);
      allowAdminOnEveryIdentity();
      SecurityUser request = userRequest(new IdentityID("sales3", "org2"));
      // setIdentityPermissions() stamps the path org on a user grantee, so an entry sent without
      // an org still names the renamed user
      request.setAdminIdentities(
         adminIdentities(List.of(new IdentityID("sales", null)), null, null));

      service.updateUser(SALES_USER, request, principal);

      assertEquals(Set.of(permittedKey(Identity.USER, "sales3", "org2")),
                   capturePermittedIdentities(ResourceType.SECURITY_USER));
   }

   @Test
   void updateUser_renamedByDelegatedCaller_rewritesOwnEntry_keepsCallerEntry() throws Exception {
      stubUpdatableUser(SALES_USER);
      when(orgManager.isSiteAdmin(principal)).thenReturn(false);
      // the caller administers the user and itself through grants, not as an admin
      IdentityID delegate = new IdentityID("dlg", "org2");
      when(securityProvider.checkPermission(principal, ResourceType.SECURITY_USER,
                                            delegate.convertToKey(), ResourceAction.ADMIN))
         .thenReturn(true);
      SecurityUser request = userRequest(new IdentityID("sales3", "org2"));
      request.setAdminIdentities(adminIdentities(List.of(SALES_USER, delegate), null, null));

      service.updateUser(SALES_USER, request, principal);

      assertEquals(Set.of(permittedKey(Identity.USER, "sales3", "org2"),
                          permittedKey(Identity.USER, "dlg", "org2")),
                   capturePermittedIdentities(ResourceType.SECURITY_USER));
   }

   @Test
   void updateUser_nameUnchanged_passesOwnEntryThrough() throws Exception {
      stubUpdatableUser(SALES_USER);
      allowAdminOnEveryIdentity();
      SecurityUser request = userRequest(SALES_USER);
      request.setAdminIdentities(adminIdentities(List.of(SALES_USER), null, null));

      service.updateUser(SALES_USER, request, principal);

      assertEquals(Set.of(permittedKey(Identity.USER, "sales", "org2")),
                   capturePermittedIdentities(ResourceType.SECURITY_USER));
   }

   @Test
   void updateGroup_renamed_rewritesOwnGroupEntry_keepsSameNamedUser() throws Exception {
      stubUpdatableGroup(SALES_GROUP);
      allowAdminOnEveryIdentity();
      SecurityGroup request = groupRequest(new IdentityID("sales2", "org2"));
      request.setAdminIdentities(adminIdentities(
         List.of(new IdentityID("sales", "org2")), List.of(SALES_GROUP), null));

      service.updateGroup(SALES_GROUP, request, principal);

      assertEquals(Set.of(permittedKey(Identity.GROUP, "sales2", "org2"),
                          permittedKey(Identity.USER, "sales", "org2")),
                   capturePermittedIdentities(ResourceType.SECURITY_GROUP));
   }

   @Test
   void updateRole_renamed_rewritesOwnRoleEntry_keepsSameNamedGlobalRoleAndUser() throws Exception {
      stubUpdatableRole(ANALYST_ROLE);
      allowAdminOnEveryIdentity();
      SecurityRole request = roleRequest(new IdentityID("analyst2", "org2"));
      // a global role of the same name is a different role
      request.setAdminIdentities(adminIdentities(
         List.of(new IdentityID("analyst", "org2")), null,
         List.of(ANALYST_ROLE, new IdentityID("analyst", null))));

      service.updateRole(ANALYST_ROLE, request, principal);

      assertEquals(Set.of(permittedKey(Identity.ROLE, "analyst2", "org2"),
                          permittedKey(Identity.ROLE, "analyst", null),
                          permittedKey(Identity.USER, "analyst", "org2")),
                   capturePermittedIdentities(ResourceType.SECURITY_ROLE));
   }

   @Test
   void updateRole_globalRoleRenamed_rewritesOwnGlobalEntry_keepsSameNamedOrgRole() throws Exception {
      stubUpdatableRole(GLOBAL_ROLE_PATH, new FSRole(GLOBAL_ROLE));
      allowAdminOnEveryIdentity();
      SecurityRole request = roleRequest(new IdentityID("gRole2", null));
      request.setAdminIdentities(adminIdentities(
         null, null, List.of(GLOBAL_ROLE, new IdentityID("gRole", "org1"))));

      service.updateRole(GLOBAL_ROLE_PATH, request, principal);

      assertEquals(Set.of(permittedKey(Identity.ROLE, "gRole2", null),
                          permittedKey(Identity.ROLE, "gRole", "org1")),
                   capturePermittedIdentities(ResourceType.SECURITY_ROLE));
   }

   // ── getOrganization / getOrganizations theme ────────────────────────────
   //
   // Bug #77086: the organization GET looked the theme up in the themes' organizations lists
   // (which hold organization IDs) by the organization *name*, and only among themes of the
   // caller's current organization, so it returned null whenever name != ID or a site admin
   // read another organization's private theme, and could return another organization's theme
   // when the name equals that organization's ID. A GET/PUT round trip then cleared the theme.
   // The GET now reports Organization.theme when it names an existing theme, like the EM pane.

   private FSOrganization stubThemedOrganization(String id, String name, String theme) {
      FSOrganization organization = new FSOrganization(id);
      organization.setName(name);
      organization.setTheme(theme);
      when(securityProvider.checkPermission(principal, ResourceType.SECURITY_ORGANIZATION,
                                            id, ResourceAction.ADMIN))
         .thenReturn(true);
      when(securityProvider.checkPermission(principal, ResourceType.SECURITY_ORGANIZATION,
                                            new IdentityID(name, id).convertToKey(),
                                            ResourceAction.ADMIN))
         .thenReturn(true);
      when(securityProvider.getOrganization(id)).thenReturn(organization);
      when(securityProvider.getOrganizationId(name)).thenReturn(id);
      return organization;
   }

   private void stubOrganizationGet(String currentOrgID, CustomTheme... themes) {
      when(orgManager.getCurrentOrgID()).thenReturn(currentOrgID);
      when(orgManager.isSiteAdmin(principal)).thenReturn(true);
      when(customThemesManager.getCustomThemes()).thenReturn(new HashSet<>(Arrays.asList(themes)));
      when(identityService.getIdentityInfo(any(), eq(Identity.ORGANIZATION), any()))
         .thenReturn(new IdentityInfo());
   }

   private static CustomTheme memberTheme(String id, String orgID, String... organizations) {
      CustomTheme theme = theme(id, orgID);
      theme.setOrganizations(new ArrayList<>(Arrays.asList(organizations)));
      return theme;
   }

   @Test
   void getOrganization_nameDiffersFromId_returnsGlobalTheme() throws Exception {
      stubThemedOrganization("acme", "Acme Corp", "g1");
      stubOrganizationGet("acme", memberTheme("g1", null, "acme"));

      assertEquals("g1", service.getOrganization("acme", principal).getTheme());
   }

   @Test
   void getOrganization_siteAdminInOtherOrg_returnsTargetOrgPrivateTheme() throws Exception {
      stubThemedOrganization("acme", "Acme Corp", "a1");
      stubOrganizationGet("host-org", memberTheme("a1", "acme", "acme"));

      assertEquals("a1", service.getOrganization("acme", principal).getTheme());
   }

   @Test
   void getOrganization_nameEqualsIdAndPrivateThemeReadFromOtherOrg_returnsTheme() throws Exception {
      stubThemedOrganization("acme", "acme", "a1");
      stubOrganizationGet("host-org", memberTheme("a1", "acme", "acme"));

      assertEquals("a1", service.getOrganization("acme", principal).getTheme());
   }

   @Test
   void getOrganization_hostOrgWithOrgDefault_returnsTheme() throws Exception {
      stubThemedOrganization("host-org", "Host Organization", "h1");
      stubOrganizationGet("host-org", memberTheme("h1", "host-org", "host-org"));

      assertEquals("h1", service.getOrganization("host-org", principal).getTheme());
   }

   @Test
   void getOrganization_nameEqualsOtherOrgId_returnsOwnThemeNotOtherOrgs() throws Exception {
      // org "orgx" is named "acme", the ID of another organization with theme g1
      stubThemedOrganization("orgx", "acme", "x1");
      stubOrganizationGet("host-org", memberTheme("g1", null, "acme"),
                          memberTheme("x1", null, "orgx"));

      assertEquals("x1", service.getOrganization("orgx", principal).getTheme());
   }

   @Test
   void getOrganization_nameEqualsOtherOrgIdAndNoTheme_returnsNull() throws Exception {
      stubThemedOrganization("orgx", "acme", null);
      stubOrganizationGet("host-org", memberTheme("g1", null, "acme"));

      assertNull(service.getOrganization("orgx", principal).getTheme());
   }

   @Test
   void getOrganization_legacyThemeName_returnsNull() throws Exception {
      // before #77083 the REST create stored the theme name, which is not a theme ID
      CustomTheme theme = memberTheme("blue-id", null);
      theme.setName("Blue");
      stubThemedOrganization("acme", "Acme Corp", "Blue");
      stubOrganizationGet("host-org", theme);

      assertNull(service.getOrganization("acme", principal).getTheme());
   }

   @Test
   void getOrganization_deletedTheme_returnsNull() throws Exception {
      // name == ID, so the old membership lookup found g1 although the organization's theme
      // is a deleted one, which the EM pane shows as no theme
      stubThemedOrganization("acme", "acme", "gone");
      stubOrganizationGet("host-org", memberTheme("g1", null, "acme"));

      assertNull(service.getOrganization("acme", principal).getTheme());
   }

   @Test
   void getOrganization_noTheme_returnsNullEvenIfMembershipListed() throws Exception {
      // single-tenant EM "Default" writes the global pointer and clears Organization.theme
      stubThemedOrganization("host-org", "Host Organization", null);
      stubOrganizationGet("host-org", memberTheme("g1", null, "host-org"));

      assertNull(service.getOrganization("host-org", principal).getTheme());
   }

   @Test
   void getOrganization_putBackUnchanged_themeUnchanged() throws Exception {
      FSOrganization acme = stubThemedOrganization("acme", "Acme Corp", "a1");
      stubOrganizationGet("host-org", memberTheme("a1", "acme", "acme"));
      doReturn(acme).when(editableProvider).getOrganization("acme");
      doReturn("acme").when(editableProvider).getOrgIdFromName("Acme Corp");
      when(securityProvider.getOrganizationIDs()).thenReturn(new String[]{ "host-org", "acme" });
      stubAuthz();

      SecurityOrganization body = service.getOrganization("acme", principal);
      service.updateOrganization("acme", body, principal);

      // IdentityService.setIdentity() only changes the theme copies when the PUT theme differs
      // from Organization.theme
      ArgumentCaptor<EditOrganizationPaneModel> captor =
         ArgumentCaptor.forClass(EditOrganizationPaneModel.class);
      verify(identityService).setIdentity(eq(acme), captor.capture(), eq(editableProvider),
                                          eq(principal));
      assertEquals(acme.getTheme(), captor.getValue().theme());
   }

   @Test
   void getOrganizations_reportsEachOrganizationsOwnTheme() throws Exception {
      stubThemedOrganization("acme", "Acme Corp", "a1");
      stubThemedOrganization("orgx", "acme", "x1");
      stubThemedOrganization("beta", "Beta Inc", "Blue");
      stubOrganizationGet("host-org", memberTheme("a1", "acme", "acme"),
                          memberTheme("g1", null, "acme"), memberTheme("x1", null, "orgx"));
      when(securityProvider.getOrganizationNames())
         .thenReturn(new String[]{ "Acme Corp", "acme", "Beta Inc" });

      Map<String, String> themes = new HashMap<>();

      for(SecurityOrganization organization :
         service.getOrganizations(principal).getOrganizations())
      {
         themes.put(organization.getId(), organization.getTheme());
      }

      Map<String, String> expected = new HashMap<>();
      expected.put("acme", "a1");
      expected.put("orgx", "x1");
      expected.put("beta", null);
      assertEquals(expected, themes);
   }
   private MockedStatic<InetsoftConfig> inetsoftConfigStatic;

   /**
    * Asserts that an update is refused before any mutation. The update methods wrap a
    * pre-mutation refusal in {@link SecurityService.PreMutationRefusalException} (Bug #76855),
    * with the original exception as its cause, which is returned.
    */
   private static <T extends Throwable> T assertRefusedBeforeMutation(
      Class<T> causeType, org.junit.jupiter.api.function.Executable update)
   {
      SecurityService.PreMutationRefusalException ex =
         assertThrows(SecurityService.PreMutationRefusalException.class, update);
      return assertInstanceOf(causeType, ex.getCause());
   }
}
