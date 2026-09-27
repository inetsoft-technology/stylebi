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
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.portal.CustomThemesManager;
import inetsoft.sree.portal.CustomThemesManagerMocks;
import inetsoft.sree.schedule.ScheduleManager;
import inetsoft.sree.security.*;
import inetsoft.sree.web.SessionLicenseManager;
import inetsoft.sree.web.SessionLicenseServiceProvider;
import inetsoft.sree.web.dashboard.DashboardManager;
import inetsoft.sree.web.dashboard.DashboardRegistryManager;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.util.Identity;
import inetsoft.util.*;
import inetsoft.web.AutoSaveUtils;
import inetsoft.web.admin.favorites.FavoritesService;
import inetsoft.web.admin.security.user.IdentityThemeService;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Issue #77092: a site admin whose current organization differs from the edited organization
 * (REST, shell, or a stale EM pane) updates the organization's members. Whether the organization
 * id changed must be decided from the edited organization's old id, not from the caller's current
 * organization. Otherwise every kept user, group and role, including the hidden members the caller
 * cannot administer, is written and then removed (deleted), and the permissions of the caller's
 * current organization are moved into the edited organization.
 * <p>
 * The provider is stateful, so a {@code setUser(x)} followed by {@code removeUser(x)} on the same
 * key is visible as a deleted user. The private {@code updateOrganizationMembers} is invoked by
 * reflection.
 */
@Tag("core")
class IdentityServiceCrossOrgMemberUpdateTest {
   private IdentityService service;
   private EditableAuthenticationProvider provider;
   private SecurityProvider securityProvider;
   private SecurityEngine securityEngine;
   private RepletRegistryManager repletRegistryManager;
   private DashboardRegistryManager dashboardRegistryManager;
   private OrganizationManager organizationManager;
   private MockedStatic<OrganizationManager> organizationManagerStatic;
   private MockedStatic<UserEnv> userEnv;
   private MockedStatic<AutoSaveUtils> autoSave;
   private java.security.Principal oldThreadPrincipal;
   private java.security.Principal requester;
   private boolean realPermissions;
   private final Map<IdentityID, User> users = new LinkedHashMap<>();
   private final Map<IdentityID, Group> groups = new LinkedHashMap<>();
   private final Map<IdentityID, Role> roles = new LinkedHashMap<>();
   // (type, oldName, newName, oldOrgId, newOrgId, doReplace) of each permission re-scope
   private final List<Object[]> permissionCalls = new ArrayList<>();

   @BeforeEach
   void setUp() {
      provider = mock(EditableAuthenticationProvider.class);
      when(provider.getUsers()).thenAnswer(inv -> users.keySet().toArray(new IdentityID[0]));
      when(provider.getGroups()).thenAnswer(inv -> groups.keySet().toArray(new IdentityID[0]));
      when(provider.getRoles()).thenAnswer(inv -> roles.keySet().toArray(new IdentityID[0]));
      when(provider.getUser(any())).thenAnswer(inv -> users.get(inv.<IdentityID>getArgument(0)));
      when(provider.getGroup(any())).thenAnswer(inv -> groups.get(inv.<IdentityID>getArgument(0)));
      when(provider.getRole(any())).thenAnswer(inv -> roles.get(inv.<IdentityID>getArgument(0)));
      doAnswer(inv -> users.put(inv.<User>getArgument(1).getIdentityID(), inv.getArgument(1)))
         .when(provider).setUser(any(), any());
      doAnswer(inv -> groups.put(inv.<Group>getArgument(1).getIdentityID(), inv.getArgument(1)))
         .when(provider).setGroup(any(), any());
      doAnswer(inv -> roles.put(inv.<Role>getArgument(1).getIdentityID(), inv.getArgument(1)))
         .when(provider).setRole(any(), any());
      doAnswer(inv -> users.remove(inv.<IdentityID>getArgument(0)))
         .when(provider).removeUser(any());
      doAnswer(inv -> groups.remove(inv.<IdentityID>getArgument(0)))
         .when(provider).removeGroup(any());
      doAnswer(inv -> groups.remove(inv.<IdentityID>getArgument(0)))
         .when(provider).removeGroup(any(), anyBoolean());
      doAnswer(inv -> roles.remove(inv.<IdentityID>getArgument(0)))
         .when(provider).removeRole(any());

      securityProvider = mock(SecurityProvider.class);
      when(securityProvider.getAuthorizationProvider()).thenReturn(mock(AuthorizationChain.class));
      when(securityProvider.getOrganization(anyString()))
         .thenAnswer(inv -> new FSOrganization(inv.<String>getArgument(0)));
      securityEngine = mock(SecurityEngine.class);
      when(securityEngine.getSecurityProvider()).thenReturn(securityProvider);

      CustomThemesManager themesManager = mock(CustomThemesManager.class);
      CustomThemesManagerMocks.applyUpdates(themesManager);
      repletRegistryManager = mock(RepletRegistryManager.class);
      dashboardRegistryManager = mock(DashboardRegistryManager.class);
      IndexedStorage indexedStorage = mock(IndexedStorage.class);
      when(indexedStorage.getKeys(any(), anyString())).thenReturn(Collections.emptySet());
      SessionLicenseManager sessionLicenseManager = mock(SessionLicenseManager.class);
      when(sessionLicenseManager.getActiveSessions()).thenReturn(new HashSet<>());
      SessionLicenseServiceProvider sessionProvider = mock(SessionLicenseServiceProvider.class);
      when(sessionProvider.getSessionLicenseManager()).thenReturn(sessionLicenseManager);

      service = mock(IdentityService.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));
      ReflectionTestUtils.setField(service, "securityEngine", securityEngine);
      ReflectionTestUtils.setField(service, "securityProvider", securityProvider);
      ReflectionTestUtils.setField(service, "themeService",
                                   new IdentityThemeService(themesManager));
      ReflectionTestUtils.setField(service, "cluster", mock(Cluster.class));
      ReflectionTestUtils.setField(service, "sessionLicenseServiceProvider", sessionProvider);
      ReflectionTestUtils.setField(service, "authenticationService",
                                   mock(AuthenticationService.class));
      ReflectionTestUtils.setField(service, "dashboardManager", mock(DashboardManager.class));
      ReflectionTestUtils.setField(service, "scheduleManager", mock(ScheduleManager.class));
      ReflectionTestUtils.setField(service, "repletRegistryManager", repletRegistryManager);
      ReflectionTestUtils.setField(service, "dashboardRegistryManager", dashboardRegistryManager);
      ReflectionTestUtils.setField(service, "indexedStorage", indexedStorage);
      ReflectionTestUtils.setField(service, "favoritesService", mock(FavoritesService.class));
      // LOG is a final instance field set by the constructor, which the mock bypasses
      ReflectionTestUtils.setField(service, "LOG", LoggerFactory.getLogger(IdentityService.class));

      doAnswer(inv -> {
         permissionCalls.add(inv.getArguments());
         return realPermissions ? inv.callRealMethod() : null;
      }).when(service).updateIdentityPermissions(anyInt(), any(), any(), any(), any(),
                                                 anyBoolean());

      organizationManager = mock(OrganizationManager.class);
      when(organizationManager.getCurrentOrgID()).thenReturn(HOST_ORG);
      organizationManagerStatic = mockStatic(OrganizationManager.class, CALLS_REAL_METHODS);
      organizationManagerStatic.when(OrganizationManager::getInstance)
         .thenReturn(organizationManager);
      userEnv = mockStatic(UserEnv.class);
      autoSave = mockStatic(AutoSaveUtils.class);

      XPrincipal threadPrincipal = mock(XPrincipal.class);
      when(threadPrincipal.getGroups()).thenReturn(new String[0]);
      when(threadPrincipal.getName()).thenReturn(new IdentityID("admin", HOST_ORG).convertToKey());
      requester = threadPrincipal;
      oldThreadPrincipal = ThreadContext.getPrincipal();
      ThreadContext.setPrincipal(threadPrincipal);
      Tool.clearUserMessage();
   }

   @AfterEach
   void tearDown() {
      Tool.clearUserMessage();
      ThreadContext.setPrincipal(oldThreadPrincipal);
      autoSave.close();
      userEnv.close();
      organizationManagerStatic.close();
   }

   // a site admin in the host org keeps every member of organization1 with the id unchanged:
   // nothing may be written, removed or re-scoped, including the hidden member
   @Test
   void crossOrg_idUnchanged_keptMembersSurvive() throws Exception {
      seedOrg1();

      updateMembers(ORG_1, ORG_1);

      assertEquals(Set.of(ALICE, HIDDEN), users.keySet());
      assertEquals(Set.of(SALES), groups.keySet());
      assertEquals(Set.of(ANALYST), roles.keySet());
      verifyNoProviderWrites();
      assertTrue(permissionCalls.isEmpty(), "no permission may be re-scoped");
      verify(repletRegistryManager, never()).changeOrgID(any(), any(), any(), anyBoolean());
      verify(dashboardRegistryManager, never()).migrateRegistry(any(), any(), any());
   }

   // with the real permission re-scoping, a permission of the caller's current org must not be
   // moved or copied into the edited organization
   @Test
   void crossOrg_idUnchanged_hostOrgPermissionNotMoved() throws Exception {
      realPermissions = true;
      AuthorizationProvider authz = mock(AuthorizationProvider.class);
      List<Tuple4<ResourceType, String, String, Permission>> permissions = List.of(
         new Tuple4<>(ResourceType.REPORT, HOST_ORG, "hostFolder/sales", new Permission()));
      when(authz.getPermissions()).thenReturn(permissions);
      AuthorizationChain chain = mock(AuthorizationChain.class);
      when(chain.getProviders()).thenReturn(List.of(authz));
      when(securityEngine.getAuthorizationChain()).thenReturn(Optional.of(chain));
      seedOrg1();

      updateMembers(ORG_1, ORG_1);

      verify(authz, never()).setPermission(any(), anyString(), any(), anyString());
      verify(authz, never()).removePermission(any(), anyString(), anyString());
      assertEquals(Set.of(ALICE, HIDDEN), users.keySet());
   }

   // a real id change by a site admin in the host org moves the members, and every registry and
   // permission migration uses the edited org's old id, never the caller's current org
   @Test
   void crossOrg_idChanged_membersMovedFromEditedOrg() throws Exception {
      seedOrg1();

      updateMembers(ORG_2, ORG_1);

      assertMovedToOrg2();
      assertPermissionsFromOrg1();
      verify(repletRegistryManager, never()).changeOrgID(any(), eq(HOST_ORG), any(), anyBoolean());
      verify(dashboardRegistryManager, times(2)).migrateRegistry(
         any(), argThat(o -> o != null && ORG_1.equals(o.getId())), any());
      verify(dashboardRegistryManager, never()).migrateRegistry(
         any(), argThat(o -> o != null && HOST_ORG.equals(o.getId())), any());
   }

   // control: the org admin of organization1 saving its own members, id unchanged
   @Test
   void sameOrg_idUnchanged_noWrites() throws Exception {
      when(organizationManager.getCurrentOrgID()).thenReturn(ORG_1);
      seedOrg1();

      updateMembers(ORG_1, ORG_1);

      assertEquals(Set.of(ALICE, HIDDEN), users.keySet());
      assertEquals(Set.of(SALES), groups.keySet());
      assertEquals(Set.of(ANALYST), roles.keySet());
      verifyNoProviderWrites();
      assertTrue(permissionCalls.isEmpty());
   }

   // control: the EM rename of the current org's id moves the members, as before
   @Test
   void sameOrg_idChanged_membersMoved() throws Exception {
      when(organizationManager.getCurrentOrgID()).thenReturn(ORG_1);
      seedOrg1();

      updateMembers(ORG_2, ORG_1);

      assertMovedToOrg2();
      assertPermissionsFromOrg1();
   }

   // a group and a role dropped in the same save as an id change are deleted, like a dropped user,
   // instead of being left in the old org and carried over to the new id
   @Test
   void idChanged_droppedGroupAndRoleDeleted() throws Exception {
      when(organizationManager.getCurrentOrgID()).thenReturn(ORG_1);
      seedOrg1();
      IdentityID temps = addGroup("temps", ORG_1);
      IdentityID auditor = addRole("auditor", ORG_1);

      updateMembers(ORG_2, ORG_1);

      assertMovedToOrg2();
      assertFalse(groups.containsKey(temps), "the dropped group must be deleted");
      assertFalse(roles.containsKey(auditor), "the dropped role must be deleted");
      assertFalse(groups.containsKey(new IdentityID("temps", ORG_2)));
      assertFalse(roles.containsKey(new IdentityID("auditor", ORG_2)));
   }

   // organization1 has alice, the hidden user, the sales group and the analyst role, and the
   // save keeps all four; the hidden user is listed in the members but not in the models, as
   // setOrganizationInfo() adds the members the caller cannot administer
   private void seedOrg1() {
      addUser(ALICE.getName(), ORG_1);
      addUser(HIDDEN.getName(), ORG_1);
      addGroup(SALES.getName(), ORG_1);
      addRole(ANALYST.getName(), ORG_1);
   }

   private void assertMovedToOrg2() {
      assertEquals(Set.of(new IdentityID("alice", ORG_2), new IdentityID("hidden", ORG_2)),
                   users.keySet());
      assertEquals(Set.of(new IdentityID("sales", ORG_2)), groups.keySet());
      assertEquals(Set.of(new IdentityID("analyst", ORG_2)), roles.keySet());
   }

   private void assertPermissionsFromOrg1() {
      assertFalse(permissionCalls.isEmpty());

      for(Object[] call : permissionCalls) {
         assertEquals(ORG_1, call[3], "old org of " + Arrays.toString(call));
         assertEquals(ORG_2, call[4], "new org of " + Arrays.toString(call));
      }
   }

   private void verifyNoProviderWrites() {
      verify(provider, never()).setUser(any(), any());
      verify(provider, never()).removeUser(any());
      verify(provider, never()).setGroup(any(), any());
      verify(provider, never()).removeGroup(any());
      verify(provider, never()).removeGroup(any(), anyBoolean());
      verify(provider, never()).setRole(any(), any());
      verify(provider, never()).removeRole(any());
   }

   private void updateMembers(String orgID, String oldOrgID) throws Exception {
      FSOrganization org = new FSOrganization(orgID);
      org.setMembers(new String[] { "alice", "hidden", "sales", "analyst" });
      List<IdentityModel> models = List.of(
         model("alice", orgID, Identity.USER),
         model("sales", orgID, Identity.GROUP),
         model("analyst", orgID, Identity.ROLE));
      Method method = IdentityService.class.getDeclaredMethod(
         "updateOrganizationMembers", Organization.class, List.class, String.class,
         EditableAuthenticationProvider.class, java.security.Principal.class);
      method.setAccessible(true);

      try {
         method.invoke(service, org, models, oldOrgID, provider, requester);
      }
      catch(InvocationTargetException ex) {
         throw (Exception) ex.getCause();
      }
   }

   private static IdentityModel model(String name, String orgID, int type) {
      return IdentityModel.builder().identityID(new IdentityID(name, orgID)).type(type).build();
   }

   private IdentityID addUser(String name, String orgID) {
      IdentityID id = new IdentityID(name, orgID);
      users.put(id, new FSUser(new IdentityID(name, orgID)));
      return id;
   }

   private IdentityID addGroup(String name, String orgID) {
      IdentityID id = new IdentityID(name, orgID);
      groups.put(id, new FSGroup(new IdentityID(name, orgID)));
      return id;
   }

   private IdentityID addRole(String name, String orgID) {
      IdentityID id = new IdentityID(name, orgID);
      roles.put(id, new FSRole(new IdentityID(name, orgID)));
      return id;
   }

   private static final String HOST_ORG = "host-org";
   private static final String ORG_1 = "organization1";
   private static final String ORG_2 = "organization2";
   private static final IdentityID ALICE = new IdentityID("alice", ORG_1);
   private static final IdentityID HIDDEN = new IdentityID("hidden", ORG_1);
   private static final IdentityID SALES = new IdentityID("sales", ORG_1);
   private static final IdentityID ANALYST = new IdentityID("analyst", ORG_1);
}
