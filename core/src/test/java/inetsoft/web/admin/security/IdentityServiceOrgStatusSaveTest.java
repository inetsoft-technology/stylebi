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
import inetsoft.util.*;
import inetsoft.util.log.LogManager;
import inetsoft.web.AutoSaveUtils;
import inetsoft.web.admin.favorites.FavoritesService;
import inetsoft.web.admin.security.user.EditOrganizationPaneModel;
import inetsoft.web.admin.security.user.IdentityThemeService;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("core")
/**
 * Bug #77147: an organization save must persist the status of the model, whether the id is kept
 * or changed.
 */
class IdentityServiceOrgStatusSaveTest {
   private IdentityService service;
   private EditableAuthenticationProvider provider;
   private MockedStatic<OrganizationManager> organizationManagerStatic;
   private MockedStatic<UserEnv> userEnv;
   private MockedStatic<AutoSaveUtils> autoSave;
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

      SecurityProvider securityProvider = mock(SecurityProvider.class);
      when(securityProvider.getAuthorizationProvider()).thenReturn(mock(AuthorizationChain.class));
      when(securityProvider.getUser(any())).thenAnswer(inv -> users.get(inv.<IdentityID>getArgument(0)));
      when(securityProvider.checkPermission(any(), any(ResourceType.class), anyString(),
                                            any(ResourceAction.class))).thenReturn(true);
      SecurityEngine securityEngine = mock(SecurityEngine.class);
      when(securityEngine.getSecurityProvider()).thenReturn(securityProvider);

      CustomThemesManager themesManager = mock(CustomThemesManager.class);
      CustomThemesManagerMocks.applyUpdates(themesManager);
      when(themesManager.getCustomThemes()).thenReturn(new HashSet<>(List.of(theme("t1"))));
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
      ReflectionTestUtils.setField(service, "repletRegistryManager",
                                   mock(RepletRegistryManager.class, RETURNS_DEEP_STUBS));
      ReflectionTestUtils.setField(service, "dashboardRegistryManager", mock(DashboardRegistryManager.class));
      ReflectionTestUtils.setField(service, "indexedStorage", indexedStorage);
      ReflectionTestUtils.setField(service, "favoritesService", mock(FavoritesService.class));
      DataSpace dataSpace = mock(DataSpace.class);
      when(dataSpace.getOrgScopedPaths(any())).thenReturn(new String[0]);
      ReflectionTestUtils.setField(service, "dataSpace", dataSpace);
      ReflectionTestUtils.setField(service, "logManager", mock(LogManager.class));
      ReflectionTestUtils.setField(service, "LOG", LoggerFactory.getLogger(IdentityService.class));
      doNothing().when(service).updateIdentityPermissions(anyInt(), any(), any(), any(), any(), anyBoolean());

      OrganizationManager organizationManager = mock(OrganizationManager.class);
      when(organizationManager.getCurrentOrgID()).thenReturn(ORG_1);
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
      FSOrganization stored = new FSOrganization(ORG_1);
      stored.setName("Old");
      stored.setMembers(new String[] { "alice", "bob" });
      stored.setTheme("t1");
      stored.setLocale(null);
      orgs.put(ORG_1, stored);
   }

   @AfterEach
   void tearDown() {
      Tool.clearUserMessage();
      ThreadContext.setPrincipal(oldThreadPrincipal);
      autoSave.close();
      userEnv.close();
      organizationManagerStatic.close();
   }

   @Test
   void sameId_deactivate_persisted() throws Exception {
      Identity result = save(ORG_1, "Old", false);
      assertAll(
         () -> assertFalse(orgs.get(ORG_1).isActive(), "stored org must be inactive"),
         () -> assertFalse(((Organization) result).isActive(), "returned org must be inactive")
      );
   }

   @Test
   void sameId_rename_deactivate_persisted() throws Exception {
      save(ORG_1, "New", false);
      Organization stored = orgs.get(ORG_1);
      assertAll(
         () -> assertEquals("New", stored.getName(), "name"),
         () -> assertFalse(stored.isActive(), "stored org must be inactive")
      );
   }

   @Test
   void sameId_reactivate_persisted() throws Exception {
      ((FSOrganization) orgs.get(ORG_1)).setActive(false);
      save(ORG_1, "Old", true);
      assertTrue(orgs.get(ORG_1).isActive(), "stored org must be active again");
   }

   @Test
   void sameId_keepActive_persisted() throws Exception {
      save(ORG_1, "Old", true);
      assertTrue(orgs.get(ORG_1).isActive(), "stored org must stay active");
   }

   // an id change hands the edited org, with the model status, to copyOrganization()
   @Test
   void idChange_inactive_statusPassedToCopy() throws Exception {
      ((FSOrganization) orgs.get(ORG_1)).setActive(false);
      Identity result = save("organization2", "Old", false);
      ArgumentCaptor<Organization> edited = ArgumentCaptor.forClass(Organization.class);
      verify(provider).copyOrganization(any(), edited.capture(), eq("organization2"), any(),
                                        any(IdentityService.class), any(IdentityThemeService.class),
                                        any(DashboardRegistryManager.class), any(),
                                        any(), eq(true));
      assertAll(
         () -> assertFalse(edited.getValue().isActive(), "edited org must be inactive"),
         () -> assertFalse(((Organization) result).isActive(), "returned org must be inactive")
      );
   }

   private Identity save(String newId, String newName, boolean status) throws Exception {
      FSOrganization oldOrg = (FSOrganization) orgs.get(ORG_1).clone();
      EditOrganizationPaneModel model = EditOrganizationPaneModel.builder()
         .id(newId)
         .name(newName)
         .oldName("Old")
         .theme("t1")
         .status(status)
         .members(List.of(IdentityModel.builder().identityID(ALICE).type(Identity.USER).build(),
                          IdentityModel.builder().identityID(BOB).type(Identity.USER).build()))
         .build();
      Method method = IdentityService.class.getDeclaredMethod(
         "setOrganizationInfo", FSOrganization.class, EditOrganizationPaneModel.class,
         EditableAuthenticationProvider.class, java.security.Principal.class);
      method.setAccessible(true);

      try(MockedStatic<SUtil> sutil = mockStatic(SUtil.class, CALLS_REAL_METHODS)) {
         sutil.when(SUtil::loadLocaleProperties).thenReturn(new Properties());
         return (Identity) method.invoke(service, oldOrg, model, provider, requester);
      }
      catch(InvocationTargetException ex) {
         throw (Exception) ex.getCause();
      }
   }

   private static CustomTheme theme(String id) {
      CustomTheme theme = new CustomTheme();
      theme.setId(id);
      return theme;
   }

   private static final String ORG_1 = "organization1";
   private static final IdentityID ALICE = new IdentityID("alice", ORG_1);
   private static final IdentityID BOB = new IdentityID("bob", ORG_1);
}
