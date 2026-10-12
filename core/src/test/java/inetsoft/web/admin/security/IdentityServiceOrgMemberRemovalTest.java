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
import inetsoft.sree.portal.CustomTheme;
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
import org.mockito.ArgumentMatchers;
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
 * Issue #77087: a user, group or role dropped from an organization's members in the EM
 * organization pane (or the REST organization update) is deleted, so it must get the same cleanup
 * as a delete through {@link IdentityService#deleteIdentities}. Otherwise a new identity with the
 * same name inherits its themes, schedule tasks, private assets and other per-identity data.
 * <p>
 * The service is created without invoking its constructor, and only the dependencies that the
 * member update touches are injected. The private {@code updateOrganizationMembers} is invoked by
 * reflection.
 */
@Tag("core")
class IdentityServiceOrgMemberRemovalTest {
   private IdentityService service;
   private EditableAuthenticationProvider provider;
   private CustomThemesManager themesManager;
   private FavoritesService favoritesService;
   private ScheduleManager scheduleManager;
   private DashboardManager dashboardManager;
   private RepletRegistryManager repletRegistryManager;
   private DashboardRegistryManager dashboardRegistryManager;
   private IndexedStorage indexedStorage;
   private AuthenticationService authenticationService;
   private SessionLicenseManager sessionLicenseManager;
   private Cluster cluster;
   private OrganizationManager organizationManager;
   private MockedStatic<OrganizationManager> organizationManagerStatic;
   private MockedStatic<UserEnv> userEnv;
   private MockedStatic<AutoSaveUtils> autoSave;
   private java.security.Principal oldThreadPrincipal;
   private java.security.Principal requester;
   private final List<IdentityID> users = new ArrayList<>();
   private final List<IdentityID> groups = new ArrayList<>();
   private final List<IdentityID> roles = new ArrayList<>();
   private final Set<SRPrincipal> sessions = new HashSet<>();

   @BeforeEach
   void setUp() {
      provider = mock(EditableAuthenticationProvider.class);
      when(provider.getUsers()).thenAnswer(inv -> users.toArray(new IdentityID[0]));
      when(provider.getGroups()).thenAnswer(inv -> groups.toArray(new IdentityID[0]));
      when(provider.getRoles()).thenAnswer(inv -> roles.toArray(new IdentityID[0]));

      SecurityProvider securityProvider = mock(SecurityProvider.class);
      when(securityProvider.getAuthorizationProvider()).thenReturn(mock(AuthorizationChain.class));

      themesManager = mock(CustomThemesManager.class);
      CustomThemesManagerMocks.applyUpdates(themesManager);
      favoritesService = mock(FavoritesService.class);
      scheduleManager = mock(ScheduleManager.class);
      dashboardManager = mock(DashboardManager.class);
      repletRegistryManager = mock(RepletRegistryManager.class);
      dashboardRegistryManager = mock(DashboardRegistryManager.class);
      indexedStorage = mock(IndexedStorage.class);
      when(indexedStorage.getKeys(any(), anyString())).thenReturn(Set.of(ASSET_KEY));
      authenticationService = mock(AuthenticationService.class);
      sessionLicenseManager = mock(SessionLicenseManager.class);
      when(sessionLicenseManager.getActiveSessions()).thenReturn(sessions);
      SessionLicenseServiceProvider sessionProvider = mock(SessionLicenseServiceProvider.class);
      when(sessionProvider.getSessionLicenseManager()).thenReturn(sessionLicenseManager);
      cluster = mock(Cluster.class);

      service = mock(IdentityService.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));
      ReflectionTestUtils.setField(service, "securityProvider", securityProvider);
      ReflectionTestUtils.setField(service, "themeService",
                                   new IdentityThemeService(themesManager));
      ReflectionTestUtils.setField(service, "cluster", cluster);
      ReflectionTestUtils.setField(service, "sessionLicenseServiceProvider", sessionProvider);
      ReflectionTestUtils.setField(service, "authenticationService", authenticationService);
      ReflectionTestUtils.setField(service, "dashboardManager", dashboardManager);
      ReflectionTestUtils.setField(service, "scheduleManager", scheduleManager);
      ReflectionTestUtils.setField(service, "repletRegistryManager", repletRegistryManager);
      ReflectionTestUtils.setField(service, "dashboardRegistryManager", dashboardRegistryManager);
      ReflectionTestUtils.setField(service, "indexedStorage", indexedStorage);
      ReflectionTestUtils.setField(service, "favoritesService", favoritesService);
      // LOG is a final instance field set by the constructor, which the mock bypasses
      ReflectionTestUtils.setField(service, "LOG", LoggerFactory.getLogger(IdentityService.class));

      // the permission cleanup is covered elsewhere and is not what these tests are about
      doNothing().when(service).updateIdentityPermissions(anyInt(), any(), any(), any(), any(),
                                                          anyBoolean());

      // runInOrgScope() stays real, only the current organization is controlled
      organizationManager = mock(OrganizationManager.class);
      when(organizationManager.getCurrentOrgID()).thenReturn(ORG_A);
      organizationManagerStatic = mockStatic(OrganizationManager.class, CALLS_REAL_METHODS);
      organizationManagerStatic.when(OrganizationManager::getInstance)
         .thenReturn(organizationManager);
      userEnv = mockStatic(UserEnv.class);
      autoSave = mockStatic(AutoSaveUtils.class);

      // the group delete branch removes the group from the calling principal
      XPrincipal threadPrincipal = mock(XPrincipal.class);
      when(threadPrincipal.getGroups()).thenReturn(new String[0]);
      when(threadPrincipal.getName()).thenReturn(ADMIN.convertToKey());
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

   // every delete cleanup runs for each dropped user, group and role, the kept member is not
   // touched, and the favorites are swept once for all dropped users
   @Test
   void droppedMembers_getFullDeleteCleanup() throws Exception {
      IdentityID bob = addUser("bob", ORG_A);
      IdentityID carol = addUser("carol", ORG_A);
      IdentityID alice = addUser("alice", ORG_A);
      IdentityID sales = addGroup("sales", ORG_A);
      IdentityID analyst = addRole("analyst", ORG_A);
      SRPrincipal bobSession = session(bob);
      SRPrincipal aliceSession = session(alice);
      CustomTheme aTheme = theme("aTheme", ORG_A, "bob", "carol", "alice");
      aTheme.getGroups().add("sales");
      aTheme.getRoles().add("analyst");
      CustomTheme bTheme = theme("bTheme", ORG_B, "bob");
      bTheme.getGroups().add("sales");
      stubThemes(aTheme, bTheme);

      updateMembers(ORG_A, ORG_A, alice);

      verify(provider).removeUser(bob);
      verify(provider).removeUser(carol);
      verify(provider).removeGroup(sales);
      verify(provider).removeRole(analyst);
      verify(provider, never()).removeUser(alice);

      assertEquals(List.of("alice"), aTheme.getUsers());
      assertTrue(aTheme.getGroups().isEmpty());
      assertTrue(aTheme.getRoles().isEmpty());
      assertEquals(List.of("bob"), bTheme.getUsers(), "org B's bob must keep its theme");
      assertEquals(List.of("sales"), bTheme.getGroups(), "org B's sales must keep its theme");

      for(IdentityID id : List.of(bob, carol)) {
         verify(scheduleManager).identityRemoved(argThat(i -> id.equals(i.getIdentityID())),
                                                 eq(ORG_A));
         verify(dashboardManager).setDashboards(
            argThat(i -> i != null && id.equals(i.getIdentityID())), isNull());
         verify(repletRegistryManager).removeUser(id);
         verify(dashboardRegistryManager).clear(id);
         userEnv.verify(() -> UserEnv.removeUser(id));
         autoSave.verify(() -> AutoSaveUtils.deleteUserAutoSaveFiles(id));
      }

      verify(scheduleManager).identityRemoved(argThat(i -> sales.equals(i.getIdentityID())),
                                              eq(ORG_A));
      verify(scheduleManager).identityRemoved(argThat(i -> analyst.equals(i.getIdentityID())),
                                              eq(ORG_A));
      verify(scheduleManager, never())
         .identityRemoved(argThat(i -> alice.equals(i.getIdentityID())), nullable(String.class));
      userEnv.verify(() -> UserEnv.removeUser(alice), never());

      // user-scoped assets of both dropped users
      verify(indexedStorage, times(2)).remove(ASSET_KEY);
      verify(favoritesService).removeFavorites(
         ArgumentMatchers.<Collection<IdentityID>>argThat(
            ids -> ids.size() == 2 && ids.contains(bob) && ids.contains(carol)));

      verify(authenticationService).logout(bobSession, true);
      verify(authenticationService, never()).logout(eq(aliceSession), anyBoolean());
      verify(cluster, times(4)).sendMessage(any(IdentityChangedMessage.class));
      assertNull(Tool.getUserMessage());
   }

   // a site admin updating another organization through the REST API keeps its own current
   // organization, so the cleanup must switch to the edited organization, otherwise the
   // dashboards of a same-named user of the current organization are cleared
   @Test
   void droppedUser_cleanedInEditedOrgScope() throws Exception {
      when(organizationManager.getCurrentOrgID()).thenReturn(HOST_ORG);
      IdentityID bob = addUser("bob", ORG_B);
      List<String> dashboardOrgs = new ArrayList<>();
      doAnswer(inv -> {
         dashboardOrgs.add(OrganizationContextHolder.getCurrentOrgId());
         return null;
      }).when(dashboardManager).setDashboards(any(), isNull());

      updateMembers(ORG_B, ORG_B);

      verify(provider).removeUser(bob);
      assertEquals(List.of(ORG_B), dashboardOrgs);
      assertNotEquals(ORG_B, OrganizationContextHolder.getCurrentOrgId(),
                      "the organization scope must be restored");
   }

   // a failure of the cleanup must still delete the dropped member, report it to the user, and
   // leave the other dropped members' cleanup intact
   @Test
   void cleanupFails_memberStillRemovedAndReported() throws Exception {
      IdentityID bob = addUser("bob", ORG_A);
      IdentityID carol = addUser("carol", ORG_A);
      IdentityID sales = addGroup("sales", ORG_A);
      // the dashboards are cleaned up best-effort after the removal (Bug #77797), so the failures
      // are injected into steps that still fail the cleanup
      doAnswer(inv -> {
         when(provider.getUser(bob)).thenReturn(null);
         return null;
      }).when(provider).removeUser(bob);
      doAnswer(inv -> {
         when(provider.getGroup(sales)).thenReturn(null);
         return null;
      }).when(provider).removeGroup(sales);
      userEnv.when(() -> UserEnv.removeUser(bob)).thenThrow(new RuntimeException("storage down"));
      doThrow(new RuntimeException("Failed to update permissions")).when(service)
         .updateIdentityPermissions(anyInt(), eq(sales), any(), any(), any(), anyBoolean());

      updateMembers(ORG_A, ORG_A);

      verify(provider).removeUser(bob);
      verify(provider).removeUser(carol);
      verify(provider).removeGroup(sales);
      verify(favoritesService).removeFavorites(
         ArgumentMatchers.<Collection<IdentityID>>argThat(
            ids -> ids.contains(bob) && ids.contains(carol)));
      userEnv.verify(() -> UserEnv.removeUser(carol));

      UserMessage message = Tool.getUserMessage();
      assertNotNull(message);
      assertTrue(message.getMessage().contains("bob"), message.getMessage());
      assertTrue(message.getMessage().contains("sales"), message.getMessage());
      assertFalse(message.getMessage().contains("carol"), message.getMessage());
   }

   // when even the provider remove fails, the user was not deleted, so its favorites stay
   @Test
   void cleanupAndRemoveFail_userNotSweptFromFavorites() throws Exception {
      IdentityID bob = addUser("bob", ORG_A);
      IdentityID carol = addUser("carol", ORG_A);
      doThrow(new RuntimeException("Failed to save dashboard"))
         .when(dashboardManager).setDashboards(
            argThat(i -> i != null && bob.equals(i.getIdentityID())), isNull());
      doThrow(new RuntimeException("provider unavailable")).when(provider).removeUser(bob);

      updateMembers(ORG_A, ORG_A);

      verify(favoritesService).removeFavorites(
         ArgumentMatchers.<Collection<IdentityID>>argThat(
            ids -> !ids.contains(bob) && ids.contains(carol)));
      assertNotNull(Tool.getUserMessage());
   }

   // when the cleanup fails after the user was already removed from the provider, the user must
   // not be removed a second time, and it still counts as deleted: logged out, announced and swept
   // from the favorites
   @Test
   void cleanupFailsAfterRemove_noSecondRemove_userStillTreatedAsDeleted() throws Exception {
      IdentityID bob = addUser("bob", ORG_A);
      SRPrincipal bobSession = session(bob);
      doAnswer(inv -> {
         when(provider.getUser(bob)).thenReturn(null);
         return null;
      }).when(provider).removeUser(bob);
      userEnv.when(() -> UserEnv.removeUser(bob)).thenThrow(new RuntimeException("storage down"));

      updateMembers(ORG_A, ORG_A);

      verify(provider, times(1)).removeUser(bob);
      // the cleanup ran with the removal and is not repeated
      verify(scheduleManager, times(1))
         .identityRemoved(argThat(i -> bob.equals(i.getIdentityID())), eq(ORG_A));
      verify(dashboardManager, times(1)).setDashboards(
         argThat(i -> i != null && bob.equals(i.getIdentityID())), isNull());
      verify(repletRegistryManager, times(1)).removeUser(bob);
      verify(authenticationService).logout(bobSession, true);
      verify(cluster).sendMessage(any(IdentityChangedMessage.class));
      verify(favoritesService).removeFavorites(
         ArgumentMatchers.<Collection<IdentityID>>argThat(ids -> ids.contains(bob)));
      UserMessage message = Tool.getUserMessage();
      assertNotNull(message);
      assertTrue(message.getMessage().contains("bob"), message.getMessage());
   }

   // Bug #77797: when the first removal fails, the delete keeps the member's dashboards, schedule
   // tasks and portal registry. The retry then removes the member, so that cleanup must run once,
   // in the edited organization's scope
   @Test
   void firstRemoveFails_retrySucceeds_cleanupRunsOnce() throws Exception {
      when(organizationManager.getCurrentOrgID()).thenReturn(HOST_ORG);
      IdentityID bob = addUser("bob", ORG_A);
      IdentityID sales = addGroup("sales", ORG_A);
      List<String> dashboardOrgs = new ArrayList<>();
      doAnswer(inv -> {
         dashboardOrgs.add(OrganizationContextHolder.getCurrentOrgId());
         return null;
      }).when(dashboardManager).setDashboards(any(), isNull());
      doThrow(new RuntimeException("storage timeout"))
         .doAnswer(inv -> {
            when(provider.getUser(bob)).thenReturn(null);
            return null;
         })
         .when(provider).removeUser(bob);
      doThrow(new RuntimeException("storage timeout"))
         .doAnswer(inv -> {
            when(provider.getGroup(sales)).thenReturn(null);
            return null;
         })
         .when(provider).removeGroup(sales);

      updateMembers(ORG_A, ORG_A);

      verify(provider, times(2)).removeUser(bob);
      verify(provider, times(2)).removeGroup(sales);

      for(IdentityID id : List.of(bob, sales)) {
         verify(scheduleManager, times(1))
            .identityRemoved(argThat(i -> id.equals(i.getIdentityID())), eq(ORG_A));
         verify(dashboardManager, times(1)).setDashboards(
            argThat(i -> i != null && id.equals(i.getIdentityID())), isNull());
      }

      verify(repletRegistryManager, times(1)).removeUser(bob);
      verify(dashboardRegistryManager, times(1)).clear(bob);
      assertEquals(List.of(ORG_A, ORG_A), dashboardOrgs);
      verify(cluster, times(2)).sendMessage(any(IdentityChangedMessage.class));
      UserMessage message = Tool.getUserMessage();
      assertNotNull(message);
      assertTrue(message.getMessage().contains("bob"), message.getMessage());
   }

   // when the retry fails too, the member is kept and so are its tasks and dashboards
   @Test
   void firstRemoveAndRetryFail_noCleanup() throws Exception {
      IdentityID bob = addUser("bob", ORG_A);
      doThrow(new RuntimeException("provider unavailable")).when(provider).removeUser(bob);

      updateMembers(ORG_A, ORG_A);

      verify(provider, times(2)).removeUser(bob);
      verify(scheduleManager, never())
         .identityRemoved(argThat(i -> bob.equals(i.getIdentityID())), nullable(String.class));
      verify(dashboardManager, never()).setDashboards(any(), isNull());
      verify(repletRegistryManager, never()).removeUser(bob);
      verify(cluster, never()).sendMessage(any(IdentityChangedMessage.class));
      assertNotNull(Tool.getUserMessage());
   }

   // with an organization id change, a kept user is moved to the new id and must not be cleaned,
   // while a dropped user is cleaned under the old id
   @Test
   void orgIdChange_movedUserNotCleaned_droppedUserCleanedUnderOldId() throws Exception {
      IdentityID bob = addUser("bob", ORG_A);
      IdentityID alice = addUser("alice", ORG_A);
      IdentityID movedAlice = new IdentityID("alice", ORG_A2);
      CustomTheme aTheme = theme("aTheme", ORG_A, "bob", "alice");
      stubThemes(aTheme);

      updateMembers(ORG_A2, ORG_A, movedAlice);

      verify(provider).removeUser(bob);
      verify(scheduleManager).identityRemoved(argThat(i -> bob.equals(i.getIdentityID())),
                                              eq(ORG_A));
      assertEquals(List.of("alice"), aTheme.getUsers());

      verify(provider).setUser(eq(movedAlice), any(User.class));
      verify(provider).removeUser(alice);
      verify(scheduleManager, never()).identityRemoved(
         argThat(i -> "alice".equals(i.getIdentityID().getName())), nullable(String.class));
      verify(repletRegistryManager, never()).removeUser(alice);
      userEnv.verify(() -> UserEnv.removeUser(alice), never());
      autoSave.verify(() -> AutoSaveUtils.deleteUserAutoSaveFiles(alice), never());
      verify(favoritesService).removeFavorites(
         ArgumentMatchers.<Collection<IdentityID>>argThat(
            ids -> ids.size() == 1 && ids.contains(bob)));
   }

   // like deleteIdentities(), the requesting user is never deleted, and its session is not
   // logged out in the middle of its own request
   @Test
   void selfDrop_requesterNotDeletedNorLoggedOut() throws Exception {
      IdentityID admin = addUser(ADMIN.getName(), ORG_A);
      IdentityID bob = addUser("bob", ORG_A);
      SRPrincipal adminSession = session(admin);

      updateMembers(ORG_A, ORG_A);

      verify(provider, never()).removeUser(admin);
      verify(authenticationService, never()).logout(eq(adminSession), anyBoolean());
      verify(scheduleManager, never()).identityRemoved(
         argThat(i -> admin.equals(i.getIdentityID())), nullable(String.class));
      verify(provider).removeUser(bob);
      UserMessage message = Tool.getUserMessage();
      assertNotNull(message);
      assertEquals(Catalog.getCatalog().getString("em.security.delself"), message.getMessage(),
                   "the refusal must be the only message, and must use the delete-yourself text");
   }

   private void updateMembers(String orgID, String oldOrgID, IdentityID... keptUsers)
      throws Exception
   {
      FSOrganization org = new FSOrganization(orgID);
      org.setMembers(Arrays.stream(keptUsers).map(IdentityID::getName).toArray(String[]::new));
      List<IdentityModel> models = Arrays.stream(keptUsers)
         .<IdentityModel>map(id -> IdentityModel.builder().identityID(id).type(Identity.USER).build())
         .toList();
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

   private IdentityID addUser(String name, String orgID) {
      IdentityID id = new IdentityID(name, orgID);
      FSUser user = new FSUser(id);
      users.add(id);
      when(provider.getUser(id)).thenReturn(user);
      return id;
   }

   private IdentityID addGroup(String name, String orgID) {
      IdentityID id = new IdentityID(name, orgID);
      groups.add(id);
      when(provider.getGroup(id)).thenReturn(new FSGroup(id));
      return id;
   }

   private IdentityID addRole(String name, String orgID) {
      IdentityID id = new IdentityID(name, orgID);
      roles.add(id);
      when(provider.getRole(id)).thenReturn(new FSRole(id));
      return id;
   }

   private SRPrincipal session(IdentityID id) {
      SRPrincipal principal = mock(SRPrincipal.class);
      when(principal.getIdentityID()).thenReturn(id);
      sessions.add(principal);
      return principal;
   }

   private void stubThemes(CustomTheme... themes) {
      when(themesManager.getCustomThemes()).thenReturn(new HashSet<>(Arrays.asList(themes)));
   }

   private static CustomTheme theme(String id, String orgID, String... users) {
      CustomTheme theme = new CustomTheme();
      theme.setId(id);
      theme.setName(id);
      theme.setOrgID(orgID);
      theme.getUsers().addAll(Arrays.asList(users));
      return theme;
   }

   private static final String HOST_ORG = "host-org";
   private static final String ORG_A = "organizationa";
   private static final String ORG_A2 = "organizationa2";
   private static final String ORG_B = "organization1";
   private static final IdentityID ADMIN = new IdentityID("admin", ORG_A);
   private static final String ASSET_KEY = "1^4097^bob~;~organizationa^private";
}
