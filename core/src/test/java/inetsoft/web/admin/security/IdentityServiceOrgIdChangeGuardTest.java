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
import inetsoft.sree.web.SessionLicenseManager;
import inetsoft.sree.web.SessionLicenseServiceProvider;
import inetsoft.sree.web.dashboard.DashboardManager;
import inetsoft.sree.web.dashboard.DashboardRegistryManager;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.util.Identity;
import inetsoft.test.*;
import inetsoft.util.*;
import inetsoft.util.log.LogManager;
import inetsoft.web.AutoSaveUtils;
import inetsoft.web.admin.favorites.FavoritesService;
import inetsoft.web.admin.security.user.EditOrganizationPaneModel;
import inetsoft.web.admin.security.user.IdentityThemeService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.slf4j.LoggerFactory;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
/**
 * Bug #79861: an organization id change must refuse writes into the old organization from the
 * first step that moves its data (the per user registry migration), not only from the copy at the
 * end, and must end the guard whether the change completes or fails.
 */
class IdentityServiceOrgIdChangeGuardTest {
   private IdentityService service;
   private EditableAuthenticationProvider provider;
   private SecurityProvider securityProvider;
   private CustomThemesManager themesManager;
   private DashboardRegistryManager dashboardRegistryManager;
   private RepletRegistryManager repletRegistryManager;
   private MockedStatic<OrganizationManager> organizationManagerStatic;
   private MockedStatic<UserEnv> userEnv;
   private MockedStatic<AutoSaveUtils> autoSave;
   private OrganizationManager organizationManager;
   private final Map<IdentityID, Role> roles = new HashMap<>();
   private java.security.Principal oldThreadPrincipal;
   private java.security.Principal requester;
   private final Map<IdentityID, User> users = new LinkedHashMap<>();
   private final Map<String, Organization> orgs = new HashMap<>();

   @BeforeEach
   void setUp() {
      provider = mock(EditableAuthenticationProvider.class);
      when(provider.getUsers()).thenAnswer(inv -> users.keySet().toArray(new IdentityID[0]));
      when(provider.getGroups()).thenReturn(new IdentityID[0]);
      when(provider.getRoles()).thenReturn(new IdentityID[0]);
      when(provider.getRole(any())).thenAnswer(inv -> roles.get(inv.<IdentityID>getArgument(0)));
      when(provider.getUser(any())).thenAnswer(inv -> users.get(inv.<IdentityID>getArgument(0)));
      doAnswer(inv -> users.put(inv.<User>getArgument(1).getIdentityID(), inv.getArgument(1)))
         .when(provider).setUser(any(), any());
      doAnswer(inv -> users.remove(inv.<IdentityID>getArgument(0)))
         .when(provider).removeUser(any());
      when(provider.getOrganization(anyString()))
         .thenAnswer(inv -> orgs.get(inv.<String>getArgument(0)));
      doAnswer(inv -> orgs.put(inv.getArgument(0), inv.getArgument(1)))
         .when(provider).setOrganization(anyString(), any());
      when(provider.getOrgIdFromName(anyString())).thenAnswer(inv -> orgs.values().stream()
         .filter(o -> Tool.equals(o.getName(), inv.getArgument(0)))
         .map(Organization::getId).findFirst().orElse(null));

      securityProvider = mock(SecurityProvider.class);
      when(securityProvider.getAuthorizationProvider()).thenReturn(mock(AuthorizationChain.class));
      when(securityProvider.getUser(any())).thenAnswer(inv -> users.get(inv.<IdentityID>getArgument(0)));
      when(securityProvider.checkPermission(any(), any(ResourceType.class), anyString(),
                                            any(ResourceAction.class))).thenReturn(true);
      SecurityEngine securityEngine = mock(SecurityEngine.class);
      when(securityEngine.getSecurityProvider()).thenReturn(securityProvider);

      themesManager = mock(CustomThemesManager.class);
      CustomThemesManagerMocks.applyUpdates(themesManager);
      when(themesManager.getCustomThemes())
         .thenReturn(new HashSet<>(List.of(theme("t1"), theme("t2"))));
      repletRegistryManager = mock(RepletRegistryManager.class, RETURNS_DEEP_STUBS);
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
      ReflectionTestUtils.setField(service, "customThemesManager", themesManager);
      ReflectionTestUtils.setField(service, "themeService", new IdentityThemeService(themesManager));
      ReflectionTestUtils.setField(service, "cluster", mock(Cluster.class));
      ReflectionTestUtils.setField(service, "sessionLicenseServiceProvider", sessionProvider);
      ReflectionTestUtils.setField(service, "authenticationService", mock(AuthenticationService.class));
      ReflectionTestUtils.setField(service, "dashboardManager", mock(DashboardManager.class));
      ReflectionTestUtils.setField(service, "scheduleManager", mock(ScheduleManager.class));
      ReflectionTestUtils.setField(service, "repletRegistryManager", repletRegistryManager);
      ReflectionTestUtils.setField(service, "dashboardRegistryManager", dashboardRegistryManager);
      ReflectionTestUtils.setField(service, "indexedStorage", indexedStorage);
      ReflectionTestUtils.setField(service, "favoritesService", mock(FavoritesService.class));
      DataSpace dataSpace = mock(DataSpace.class);
      when(dataSpace.getOrgScopedPaths(any())).thenReturn(new String[0]);
      ReflectionTestUtils.setField(service, "dataSpace", dataSpace);
      ReflectionTestUtils.setField(service, "logManager", mock(LogManager.class));
      ReflectionTestUtils.setField(service, "LOG", LoggerFactory.getLogger(IdentityService.class));
      doNothing().when(service).updateIdentityPermissions(anyInt(), any(), any(), any(), any(), anyBoolean());

      organizationManager = mock(OrganizationManager.class);
      when(organizationManager.getCurrentOrgID()).thenReturn(OLD_ORG);
      organizationManagerStatic = mockStatic(OrganizationManager.class, CALLS_REAL_METHODS);
      organizationManagerStatic.when(OrganizationManager::getInstance).thenReturn(organizationManager);
      userEnv = mockStatic(UserEnv.class);
      autoSave = mockStatic(AutoSaveUtils.class);

      XPrincipal threadPrincipal = mock(XPrincipal.class);
      when(threadPrincipal.getGroups()).thenReturn(new String[0]);
      when(threadPrincipal.getName()).thenReturn(new IdentityID("admin", "host-org").convertToKey());
      requester = threadPrincipal;
      oldThreadPrincipal = ThreadContext.getPrincipal();
      ThreadContext.setPrincipal(threadPrincipal);
      Tool.clearUserMessage();

      users.put(ALICE, new FSUser(ALICE));
      users.put(BOB, new FSUser(BOB));
      FSOrganization stored = new FSOrganization(OLD_ORG);
      stored.setName("Old");
      stored.setMembers(new String[] { "alice", "bob" });
      stored.setTheme("t1");
      stored.setLocale(null);
      orgs.put(OLD_ORG, stored);
   }

   @AfterEach
   void tearDown() {
      Tool.clearUserMessage();
      ThreadContext.setPrincipal(oldThreadPrincipal);
      autoSave.close();
      userEnv.close();
      organizationManagerStatic.close();
   }

   @AfterEach
   void clearGuard() {
      OrgIdChangeGuard.end(OLD_ORG, false);
      OrgIdChangeGuard.clear(OLD_ORG);
      OrgIdChangeGuard.clear(NEW_ORG);
   }

   // the registry migration, the first mutation of an id change, already runs under the guard:
   // another thread (another node's request) is refused, the changing thread is not
   @Test
   void idChange_guardedFromTheFirstMigration() throws Exception {
      List<String> seen = new ArrayList<>();
      doAnswer(inv -> {
         seen.add("running=" + OrgIdChangeGuard.isRunning(OLD_ORG));
         seen.add("other=" + refusedFromOtherThread(OLD_ORG));
         seen.add("otherNew=" + refusedFromOtherThread(NEW_ORG));
         OrgIdChangeGuard.checkWritable(OLD_ORG);
         return null;
      }).when(dashboardRegistryManager).migrateRegistry(any(), any(), any());

      save(NEW_ORG, "New", "t1", null, aliceAndBob());

      // alice is moved by updateOrganizationMembers, the organization's own registry after it
      verify(dashboardRegistryManager, times(2)).migrateRegistry(any(), any(), any());
      assertEquals(List.of("running=true", "other=true", "otherNew=true",
                           "running=true", "other=true", "otherNew=true"), seen);
      assertFalse(OrgIdChangeGuard.isRunning(OLD_ORG), "the change must be ended");
      // a completed change keeps the old id refused for a while
      assertTrue(refusedFromOtherThread(OLD_ORG));
      assertFalse(refusedFromOtherThread(NEW_ORG));
   }

   // a failed change ends the guard and leaves the organization writable
   @Test
   void idChange_failureEndsTheGuard() throws Exception {
      doThrow(new IllegalStateException("migrate failed"))
         .when(dashboardRegistryManager).migrateRegistry(any(), any(), any());
      assertThrows(IllegalStateException.class,
                   () -> save(NEW_ORG, "New", "t1", null, aliceAndBob()));
      assertFalse(OrgIdChangeGuard.isRunning(OLD_ORG));
      assertFalse(refusedFromOtherThread(OLD_ORG));
      assertFalse(refusedFromOtherThread(NEW_ORG));
   }

   // a same-id save is not an id change and must not guard anything
   @Test
   void sameIdSave_notGuarded() throws Exception {
      List<Boolean> refused = new ArrayList<>();
      doAnswer(inv -> refused.add(refusedFromOtherThread(OLD_ORG)))
         .when(dashboardRegistryManager).migrateRegistry(any(), any(), any());
      save(OLD_ORG, "New", "t1", null, aliceAndBob());
      assertTrue(refused.isEmpty(), "no migration expected");
      assertFalse(refusedFromOtherThread(OLD_ORG));
   }

   // the copy at the end of the change reuses the running guard instead of beginning a second
   @Test
   void runningGuardIsReused() throws Exception {
      String opId = OrgIdChangeGuard.begin(OLD_ORG, NEW_ORG);

      try {
         assertTrue(OrgIdChangeGuard.isRunning(OLD_ORG));
         assertTrue(OrgIdChangeGuard.isRunning(NEW_ORG));
      }
      finally {
         OrgIdChangeGuard.end(opId, OLD_ORG, false);
      }

      assertFalse(OrgIdChangeGuard.isRunning(OLD_ORG));
   }

   private Identity save(String newId, String newName, String theme, String locale,
                         List<IdentityModel> members) throws Exception
   {
      FSOrganization oldOrg = (FSOrganization) orgs.get(OLD_ORG).clone();
      EditOrganizationPaneModel model = EditOrganizationPaneModel.builder()
         .id(newId)
         .name(newName)
         .oldName("Old")
         .locale(locale)
         .theme(theme)
         .members(members)
         .build();
      Method method = IdentityService.class.getDeclaredMethod(
         "setOrganizationInfo", FSOrganization.class, EditOrganizationPaneModel.class,
         EditableAuthenticationProvider.class, java.security.Principal.class);
      method.setAccessible(true);
      Properties locales = new Properties();
      locales.setProperty("de_DE", "German");

      try(MockedStatic<SUtil> sutil = mockStatic(SUtil.class, CALLS_REAL_METHODS)) {
         sutil.when(SUtil::loadLocaleProperties).thenReturn(locales);
         return (Identity) method.invoke(service, oldOrg, model, provider, requester);
      }
      catch(InvocationTargetException ex) {
         throw (Exception) ex.getCause();
      }
   }

   private static boolean refusedFromOtherThread(String orgId) throws Exception {
      java.util.concurrent.atomic.AtomicReference<Boolean> result =
         new java.util.concurrent.atomic.AtomicReference<>(false);
      Thread t = new Thread(() -> {
         try {
            OrgIdChangeGuard.checkWritable(orgId);
         }
         catch(OrgIdChangeGuard.OrganizationChangingException e) {
            result.set(true);
         }
      });
      t.start();
      t.join();
      return result.get();
   }

   private static final String NEW_ORG = "bug79861new";

   private List<IdentityModel> aliceAndBob() {
      return List.of(IdentityModel.builder().identityID(ALICE).type(Identity.USER).build());
   }

   private static CustomTheme theme(String id) {
      CustomTheme theme = new CustomTheme();
      theme.setId(id);
      return theme;
   }

   private static final String OLD_ORG = "bug79861old";
   private static final IdentityID ALICE = new IdentityID("alice", OLD_ORG);
   private static final IdentityID BOB = new IdentityID("bob", OLD_ORG);
}
