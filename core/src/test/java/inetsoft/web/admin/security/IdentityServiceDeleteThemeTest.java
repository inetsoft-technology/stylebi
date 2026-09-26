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
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.portal.CustomTheme;
import inetsoft.sree.portal.CustomThemesManager;
import inetsoft.sree.portal.CustomThemesManagerMocks;
import inetsoft.sree.schedule.ScheduleManager;
import inetsoft.sree.security.*;
import inetsoft.sree.web.SessionLicenseServiceProvider;
import inetsoft.sree.web.dashboard.DashboardManager;
import inetsoft.sree.web.dashboard.DashboardRegistryManager;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.util.Identity;
import inetsoft.util.IndexedStorage;
import inetsoft.util.ThreadContext;
import inetsoft.util.audit.ActionRecord;
import inetsoft.util.audit.Audit;
import inetsoft.web.AutoSaveUtils;
import inetsoft.web.admin.favorites.FavoritesService;
import inetsoft.web.admin.security.user.IdentityThemeService;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentMatchers;
import org.mockito.MockedStatic;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Issue #77084: deleting a user, group or role through {@link IdentityService#deleteIdentities}
 * (the funnel of the EM delete-identities endpoint and the REST delete endpoints) must remove its
 * name from the custom themes, so that a new identity with the same name does not inherit the
 * theme assignment.
 * <p>
 * The service is created without invoking its constructor, and only the dependencies that the
 * delete path touches are injected. The themes are changed through a real
 * {@link IdentityThemeService} over a mock {@link CustomThemesManager}.
 */
@Tag("core")
class IdentityServiceDeleteThemeTest {
   private IdentityService service;
   private EditableAuthenticationProvider provider;
   private CustomThemesManager themesManager;
   private FavoritesService favoritesService;
   private Principal principal;
   private MockedStatic<SUtil> sutil;
   private MockedStatic<Audit> audit;
   private MockedStatic<SecurityEngine> securityEngineStatic;
   private MockedStatic<UserEnv> userEnv;
   private MockedStatic<AutoSaveUtils> autoSave;
   private Principal oldThreadPrincipal;

   @BeforeEach
   void setUp() throws Exception {
      provider = mock(EditableAuthenticationProvider.class);
      when(provider.getProviderName()).thenReturn(PROVIDER);
      when(provider.getUsers(any(IdentityID.class))).thenReturn(new IdentityID[0]);

      AuthenticationChain chain = mock(AuthenticationChain.class);
      when(chain.getProviders()).thenReturn(List.of(provider));
      SecurityProvider securityProvider = mock(SecurityProvider.class);
      when(securityProvider.getAuthenticationProvider()).thenReturn(chain);

      SecurityEngine securityEngine = mock(SecurityEngine.class);
      when(securityEngine.checkPermission(any(Principal.class), any(ResourceType.class),
                                          anyString(), any(ResourceAction.class)))
         .thenReturn(true);

      themesManager = mock(CustomThemesManager.class);
      CustomThemesManagerMocks.applyUpdates(themesManager);
      favoritesService = mock(FavoritesService.class);

      service = mock(IdentityService.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));
      ReflectionTestUtils.setField(service, "securityEngine", securityEngine);
      ReflectionTestUtils.setField(service, "securityProvider", securityProvider);
      ReflectionTestUtils.setField(service, "themeService",
                                   new IdentityThemeService(themesManager));
      ReflectionTestUtils.setField(service, "cluster", mock(Cluster.class));
      ReflectionTestUtils.setField(service, "sessionLicenseServiceProvider",
                                   mock(SessionLicenseServiceProvider.class));
      ReflectionTestUtils.setField(service, "dashboardManager", mock(DashboardManager.class));
      ReflectionTestUtils.setField(service, "scheduleManager", mock(ScheduleManager.class));
      ReflectionTestUtils.setField(service, "repletRegistryManager",
                                   mock(RepletRegistryManager.class));
      ReflectionTestUtils.setField(service, "dashboardRegistryManager",
                                   mock(DashboardRegistryManager.class));
      ReflectionTestUtils.setField(service, "indexedStorage", mock(IndexedStorage.class));
      ReflectionTestUtils.setField(service, "favoritesService", favoritesService);
      // LOG is a final instance field set by the constructor, which the mock bypasses
      ReflectionTestUtils.setField(service, "LOG", LoggerFactory.getLogger(IdentityService.class));

      // the permission cleanup is covered elsewhere and is not what these tests are about
      doNothing().when(service).updateIdentityPermissions(anyInt(), any(), any(), any(), any(),
                                                          anyBoolean());
      doReturn(mock(IdentityInfo.class)).when(service).getIdentityInfo(any(), anyInt(), any());

      principal = mock(Principal.class);
      when(principal.getName()).thenReturn(new IdentityID("admin", HOST_ORG).convertToKey());

      sutil = mockStatic(SUtil.class);
      sutil.when(() -> SUtil.getActionRecord(any(Principal.class), anyString(), any(), anyString()))
         .thenAnswer(inv -> new ActionRecord());
      audit = mockStatic(Audit.class);
      audit.when(Audit::getInstance).thenReturn(mock(Audit.class));
      securityEngineStatic = mockStatic(SecurityEngine.class);
      userEnv = mockStatic(UserEnv.class);
      autoSave = mockStatic(AutoSaveUtils.class);

      // the group delete branch removes the group from the calling principal
      XPrincipal threadPrincipal = mock(XPrincipal.class);
      when(threadPrincipal.getGroups()).thenReturn(new String[0]);
      oldThreadPrincipal = ThreadContext.getPrincipal();
      ThreadContext.setPrincipal(threadPrincipal);
   }

   @AfterEach
   void tearDown() {
      ThreadContext.setPrincipal(oldThreadPrincipal);
      autoSave.close();
      userEnv.close();
      securityEngineStatic.close();
      audit.close();
      sutil.close();
   }

   @Test
   void deleteUser_removedFromOwnOrgThemeOnly() {
      IdentityID bob = new IdentityID("bob", ORG_A);
      stubUser(bob, ORG_A);
      CustomTheme aTheme = theme("aTheme", ORG_A, "bob", "alice");
      CustomTheme bTheme = theme("bTheme", ORG_B, "bob");
      CustomTheme globalTheme = theme("globalTheme", null, "bob");
      stubThemes(aTheme, bTheme, globalTheme);

      List<String> warnings = delete(bob, Identity.USER);

      assertTrue(warnings.isEmpty(), warnings::toString);
      assertEquals(List.of("alice"), aTheme.getUsers(),
                   "the deleted user must be removed from its organization's theme");
      assertEquals(List.of("bob"), bTheme.getUsers(), "org B's bob must keep its theme");
      assertEquals(List.of("bob"), globalTheme.getUsers(),
                   "a global theme's bob belongs to the default organization, not to org A");
   }

   @Test
   void deleteUser_defaultOrg_removedFromGlobalTheme() {
      IdentityID bob = new IdentityID("bob", HOST_ORG);
      stubUser(bob, HOST_ORG);
      CustomTheme globalTheme = theme("globalTheme", null, "bob");
      CustomTheme aTheme = theme("aTheme", ORG_A, "bob");
      stubThemes(globalTheme, aTheme);

      List<String> warnings = delete(bob, Identity.USER);

      assertTrue(warnings.isEmpty(), warnings::toString);
      assertTrue(globalTheme.getUsers().isEmpty());
      assertEquals(List.of("bob"), aTheme.getUsers(), "org A's bob must keep its theme");
   }

   // the user's organization is read from the stored user, so a request without an organization
   // does not remove a same-named user of every organization from its themes
   @Test
   void deleteUser_requestWithoutOrg_usesStoredUserOrg() {
      IdentityID bob = new IdentityID("bob", null);
      stubUser(bob, ORG_A);
      CustomTheme aTheme = theme("aTheme", ORG_A, "bob");
      CustomTheme bTheme = theme("bTheme", ORG_B, "bob");
      stubThemes(aTheme, bTheme);

      List<String> warnings = delete(bob, Identity.USER);

      assertTrue(warnings.isEmpty(), warnings::toString);
      assertTrue(aTheme.getUsers().isEmpty());
      assertEquals(List.of("bob"), bTheme.getUsers(), "org B's bob must keep its theme");
   }

   @Test
   void deleteGroup_removedFromOwnOrgThemeGroups() {
      IdentityID sales = new IdentityID("sales", ORG_A);
      Group group = mock(Group.class);
      when(group.getOrganizationID()).thenReturn(ORG_A);
      when(provider.getGroup(sales)).thenReturn(group);
      CustomTheme aTheme = theme("aTheme", ORG_A);
      aTheme.getGroups().add("sales");
      aTheme.getUsers().add("sales");
      CustomTheme bTheme = theme("bTheme", ORG_B);
      bTheme.getGroups().add("sales");
      stubThemes(aTheme, bTheme);

      List<String> warnings = delete(sales, Identity.GROUP);

      assertTrue(warnings.isEmpty(), warnings::toString);
      verify(provider).removeGroup(sales);
      assertTrue(aTheme.getGroups().isEmpty());
      assertEquals(List.of("sales"), aTheme.getUsers(), "a user named like the group stays");
      assertEquals(List.of("sales"), bTheme.getGroups(), "org B's sales must keep its theme");
   }

   @Test
   void deleteGlobalRole_removedFromEveryTheme() {
      IdentityID designer = new IdentityID("Designer", null);
      Role role = mock(Role.class);
      when(role.getOrganizationID()).thenReturn(null);
      when(provider.getRole(designer)).thenReturn(role);
      CustomTheme aTheme = theme("aTheme", ORG_A);
      aTheme.getRoles().add("Designer");
      CustomTheme bTheme = theme("bTheme", ORG_B);
      bTheme.getRoles().add("Designer");
      CustomTheme globalTheme = theme("globalTheme", null);
      globalTheme.getRoles().add("Designer");
      stubThemes(aTheme, bTheme, globalTheme);

      List<String> warnings = delete(designer, Identity.ROLE);

      assertTrue(warnings.isEmpty(), warnings::toString);
      verify(provider).removeRole(designer);
      assertTrue(aTheme.getRoles().isEmpty());
      assertTrue(bTheme.getRoles().isEmpty());
      assertTrue(globalTheme.getRoles().isEmpty());
   }

   @Test
   void deleteUser_notReferenced_noThemeWrite() {
      IdentityID bob = new IdentityID("bob", ORG_A);
      stubUser(bob, ORG_A);
      stubThemes(theme("aTheme", ORG_A, "alice"), theme("bTheme", ORG_B, "bob"));

      List<String> warnings = delete(bob, Identity.USER);

      assertTrue(warnings.isEmpty(), warnings::toString);
      verify(themesManager).updateCustomThemes(any());
      verify(themesManager, never()).setCustomThemes(any());
   }

   // a failure of the theme store must neither skip the rest of the user cleanup nor report the
   // delete as failed, because the user has already been removed from the provider
   @Test
   void deleteUser_themeStoreFails_restOfDeleteCompletes() {
      IdentityID bob = new IdentityID("bob", ORG_A);
      stubUser(bob, ORG_A);
      stubThemes(theme("aTheme", ORG_A, "bob"));
      doThrow(new RuntimeException("theme store unavailable"))
         .when(themesManager).setCustomThemes(any());

      List<String> warnings = delete(bob, Identity.USER);

      verify(themesManager).updateCustomThemes(any());
      assertTrue(warnings.isEmpty(), warnings::toString);
      verify(provider).removeUser(bob);
      userEnv.verify(() -> UserEnv.removeUser(bob));
      autoSave.verify(() -> AutoSaveUtils.deleteUserAutoSaveFiles(bob));
      verify(favoritesService).removeFavorites(
         ArgumentMatchers.<Collection<IdentityID>>argThat(ids -> ids.contains(bob)));
   }

   // the EM multi-select delete posts the whole selection in one call; every identity in it must
   // be removed from its own organization's themes, and an organization role only from the
   // themes of its organization
   @Test
   void deleteBulk_userGroupOrgRole_eachRemovedFromOwnOrgThemes() {
      IdentityID bob = new IdentityID("bob", ORG_A);
      stubUser(bob, ORG_A);
      IdentityID sales = new IdentityID("sales", ORG_A);
      Group group = mock(Group.class);
      when(group.getOrganizationID()).thenReturn(ORG_A);
      when(provider.getGroup(sales)).thenReturn(group);
      IdentityID analyst = new IdentityID("analyst", ORG_A);
      Role role = mock(Role.class);
      when(role.getOrganizationID()).thenReturn(ORG_A);
      when(provider.getRole(analyst)).thenReturn(role);
      CustomTheme aTheme = theme("aTheme", ORG_A, "bob", "alice");
      aTheme.getGroups().add("sales");
      aTheme.getRoles().add("analyst");
      CustomTheme bTheme = theme("bTheme", ORG_B, "bob");
      bTheme.getGroups().add("sales");
      bTheme.getRoles().add("analyst");
      CustomTheme globalTheme = theme("globalTheme", null);
      globalTheme.getRoles().add("analyst");
      stubThemes(aTheme, bTheme, globalTheme);
      IdentityModel[] models = {
         IdentityModel.builder().identityID(bob).type(Identity.USER).build(),
         IdentityModel.builder().identityID(sales).type(Identity.GROUP).build(),
         IdentityModel.builder().identityID(analyst).type(Identity.ROLE).build()
      };

      List<String> warnings = service.deleteIdentities(models, PROVIDER, principal);

      assertTrue(warnings.isEmpty(), warnings::toString);
      verify(themesManager, times(3)).updateCustomThemes(any());
      assertEquals(List.of("alice"), aTheme.getUsers());
      assertTrue(aTheme.getGroups().isEmpty());
      assertTrue(aTheme.getRoles().isEmpty());
      assertEquals(List.of("bob"), bTheme.getUsers(), "org B's bob must keep its theme");
      assertEquals(List.of("sales"), bTheme.getGroups(), "org B's sales must keep its theme");
      assertEquals(List.of("analyst"), bTheme.getRoles(), "org B's analyst must keep its theme");
      assertEquals(List.of("analyst"), globalTheme.getRoles(),
                   "a global theme's analyst belongs to the default organization, not to org A");
   }

   private List<String> delete(IdentityID id, int type) {
      IdentityModel model = IdentityModel.builder().identityID(id).type(type).build();
      return service.deleteIdentities(new IdentityModel[] { model }, PROVIDER, principal);
   }

   private void stubUser(IdentityID id, String orgID) {
      User user = mock(User.class);
      when(user.getOrganizationID()).thenReturn(orgID);
      when(provider.getUser(id)).thenReturn(user);
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

   private static final String PROVIDER = "Primary";
   private static final String HOST_ORG = "host-org";
   private static final String ORG_A = "organizationA";
   private static final String ORG_B = "organization1";
}
