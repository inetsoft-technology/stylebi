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
 * Bug #77268: a site admin (current org "host-org") who opens organization "orga" in EM >
 * Security > Users and saves it with no change, or a locale-only change, must not have its
 * members touched at all. The reported deletion itself is already fixed by #77092 (d25489a84):
 * IdentityService.updateOrganizationMembers()'s orgIdChange is computed from the edited
 * organization's own old id, not the caller's current org. This test instead exercises the
 * residual defect: IdentityService.setOrganizationInfo() gated the member update on
 * "!Tool.equals(oldOrg.getMembers(), memberNames)", comparing a String[] against a List, which
 * Tool.equals() (CoreTool.equals()) always treats as unequal -- so the gate was always true and
 * updateOrganizationMembers() ran on every save, whether or not membership actually changed.
 * <p>
 * The test drives the real EM entry point, UserTreeService.editOrganization(), with a real
 * IdentityService (not a mock) and a stateful EditableAuthenticationProvider, so it exercises the
 * exact save path an EM GET-then-POST round trip takes, not just the private helper in isolation.
 * <p>
 * updateOrganizationMembers() itself only ever moves/removes/creates a member when the
 * organization id changed or the member set actually changed (read the method: every write is
 * gated by "orgIdChange" or "!members.contains(...)"), so a genuinely unchanged save produces no
 * provider writes in EITHER version of the gate -- the observable difference the fix makes is
 * that updateOrganizationMembers() is not even invoked, which this test confirms indirectly via
 * eprovider.getUsers()/getGroups()/getRoles(), the three calls updateOrganizationMembers() makes
 * before doing anything else and which setOrganizationInfo() itself never calls on its own.
 */

import inetsoft.mv.MVManager;
import inetsoft.sree.RepletRegistryManager;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.security.*;
import inetsoft.sree.web.SessionLicenseManager;
import inetsoft.sree.web.SessionLicenseServiceProvider;
import inetsoft.sree.web.dashboard.DashboardManager;
import inetsoft.sree.web.dashboard.DashboardRegistryManager;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.sync.DependencyStorageService;
import inetsoft.uql.util.Identity;
import inetsoft.util.IndexedStorage;
import inetsoft.util.ThreadContext;
import inetsoft.util.Tool;
import inetsoft.util.audit.ActionRecord;
import inetsoft.web.AutoSaveUtils;
import inetsoft.web.RecycleBin;
import inetsoft.web.admin.favorites.FavoritesService;
import inetsoft.web.admin.security.AuthenticationProviderService;
import inetsoft.web.admin.security.IdentityModel;
import inetsoft.web.admin.security.IdentityService;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("core")
class UserTreeServiceEditOrganizationSiteAdminNoOpTest {
   @BeforeEach
   void setUp() {
      provider = mock(EditableAuthenticationProvider.class, withSettings().lenient());
      when(provider.getOrganizationId("Org A")).thenReturn(ORG_ID);
      when(provider.getOrganization(anyString())).thenAnswer(inv -> orgs.get(inv.getArgument(0)));
      when(provider.getUsers()).thenAnswer(inv -> users.keySet().toArray(new IdentityID[0]));
      when(provider.getGroups()).thenAnswer(inv -> groups.keySet().toArray(new IdentityID[0]));
      when(provider.getRoles()).thenAnswer(inv -> roles.keySet().toArray(new IdentityID[0]));
      when(provider.getUser(any())).thenAnswer(inv -> users.get(inv.<IdentityID>getArgument(0)));
      when(provider.getGroup(any())).thenAnswer(inv -> groups.get(inv.<IdentityID>getArgument(0)));
      when(provider.getRole(any())).thenAnswer(inv -> roles.get(inv.<IdentityID>getArgument(0)));
      doAnswer(inv -> users.put(inv.<User>getArgument(1).getIdentityID(), inv.getArgument(1)))
         .when(provider).setUser(any(), any());
      doAnswer(inv -> users.remove(inv.<IdentityID>getArgument(0)))
         .when(provider).removeUser(any());

      AuthenticationProviderService providerService =
         mock(AuthenticationProviderService.class, withSettings().lenient());
      when(providerService.getProviderByName("Primary")).thenReturn(provider);

      securityProvider = mock(SecurityProvider.class, withSettings().lenient());
      when(securityProvider.getUser(any())).thenAnswer(inv -> users.get(inv.<IdentityID>getArgument(0)));
      // the caller cannot administer the hidden user, so it must be kept back as a member
      when(securityProvider.checkPermission(any(), eq(ResourceType.SECURITY_USER), anyString(),
                                            eq(ResourceAction.ADMIN)))
         .thenAnswer(inv -> !HIDDEN.convertToKey().equals(inv.getArgument(2)));
      when(securityProvider.getAuthorizationProvider()).thenReturn(mock(AuthorizationChain.class,
                                                                        withSettings().lenient()));
      SecurityEngine securityEngine = mock(SecurityEngine.class, withSettings().lenient());
      when(securityEngine.getSecurityProvider()).thenReturn(securityProvider);
      when(securityEngine.isSecurityEnabled()).thenReturn(true);

      SystemAdminService systemAdminService = mock(SystemAdminService.class, withSettings().lenient());
      when(systemAdminService.hasSysAdmin(any())).thenReturn(true);
      when(systemAdminService.hasOrgAdmin(any())).thenReturn(true);

      organizationManager = mock(OrganizationManager.class, withSettings().lenient());
      when(organizationManager.getCurrentOrgID()).thenReturn(HOST_ORG);
      when(organizationManager.isSiteAdmin(any(Principal.class))).thenReturn(true);
      organizationManagerStatic = mockStatic(OrganizationManager.class,
                                             withSettings().defaultAnswer(CALLS_REAL_METHODS));
      organizationManagerStatic.when(OrganizationManager::getInstance)
         .thenReturn(organizationManager);

      // getActionRecord() must return a real record: setIdentity() mutates it. isMultiTenant()
      // is forced true so setIdentity() skips the license-manager call, which this test does not
      // set up. loadLocaleProperties() backs the locale lookup in setOrganizationInfo().
      sUtilStatic = mockStatic(SUtil.class, withSettings().strictness(
         org.mockito.quality.Strictness.LENIENT));
      sUtilStatic.when(() -> SUtil.getActionRecord(any(Principal.class), anyString(), any(),
                                                   anyString()))
         .thenAnswer(inv -> new ActionRecord());
      sUtilStatic.when(SUtil::isMultiTenant).thenReturn(true);
      sUtilStatic.when(SUtil::loadLocaleProperties).thenReturn(new Properties());

      autoSave = mockStatic(AutoSaveUtils.class);
      sreeEnvStatic = mockStatic(SreeEnv.class, withSettings().strictness(
         org.mockito.quality.Strictness.LENIENT));
      // SecurityEngine.touch() (a static method, distinct from the instance mock above) writes a
      // real cache file via FileSystemService, which needs a Spring context this test has none of
      securityEngineStatic = mockStatic(SecurityEngine.class, withSettings().strictness(
         org.mockito.quality.Strictness.LENIENT));

      identityService = mock(IdentityService.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));
      ReflectionTestUtils.setField(identityService, "securityEngine", securityEngine);
      ReflectionTestUtils.setField(identityService, "securityProvider", securityProvider);
      ReflectionTestUtils.setField(identityService, "cluster", mock(Cluster.class, withSettings().lenient()));
      ReflectionTestUtils.setField(identityService, "sessionLicenseServiceProvider",
                                   mock(SessionLicenseServiceProvider.class, withSettings().lenient()));
      ReflectionTestUtils.setField(identityService, "repletRegistryManager",
                                   mock(RepletRegistryManager.class, withSettings().lenient()));
      ReflectionTestUtils.setField(identityService, "dashboardRegistryManager",
                                   mock(DashboardRegistryManager.class, withSettings().lenient()));
      ReflectionTestUtils.setField(identityService, "indexedStorage",
                                   mock(IndexedStorage.class, withSettings().lenient()));
      ReflectionTestUtils.setField(identityService, "favoritesService",
                                   mock(FavoritesService.class, withSettings().lenient()));
      ReflectionTestUtils.setField(identityService, "dashboardManager",
                                   mock(DashboardManager.class, withSettings().lenient()));
      ReflectionTestUtils.setField(identityService, "mvManager",
                                   mock(MVManager.class, withSettings().lenient()));
      // LOG is a final instance field set by the constructor, which the mock bypasses
      ReflectionTestUtils.setField(identityService, "LOG", LoggerFactory.getLogger(IdentityService.class));

      requester = mock(XPrincipal.class, withSettings().lenient());
      when(requester.getGroups()).thenReturn(new String[0]);
      when(requester.getName()).thenReturn(new IdentityID("admin", HOST_ORG).convertToKey());
      oldThreadPrincipal = ThreadContext.getPrincipal();
      ThreadContext.setPrincipal(requester);

      service = new UserTreeService(
         providerService, systemAdminService, identityService, null, securityEngine, null, null,
         mock(FavoritesService.class, withSettings().lenient()), null, null, null, null, null,
         mock(DashboardRegistryManager.class, withSettings().lenient()), null, null, null);

      seedOrg();
   }

   @AfterEach
   void tearDown() {
      ThreadContext.setPrincipal(oldThreadPrincipal);
      autoSave.close();
      sUtilStatic.close();
      sreeEnvStatic.close();
      securityEngineStatic.close();
      organizationManagerStatic.close();
   }

   // no change at all: the reporter's exact repro is GET the model, POST it back unmodified
   @Test
   void siteAdmin_noOpSave_membersUntouched() throws Exception {
      editOrganization(model(ORG_NAME, ORG_NAME, ORG_ID, null));

      assertMembersSurvive();
      verifyMemberUpdateNotRun();
   }

   // the reporter's other variant: only the locale changes
   @Test
   void siteAdmin_localeOnlyChange_membersUntouched() throws Exception {
      editOrganization(model(ORG_NAME, ORG_NAME, ORG_ID, "en_US"));

      assertMembersSurvive();
      verifyMemberUpdateNotRun();
   }

   // control: a real membership change (alice dropped) must still run the member update
   @Test
   void siteAdmin_memberDropped_updateStillRuns() throws Exception {
      EditOrganizationPaneModel model = EditOrganizationPaneModel.builder()
         .name(ORG_NAME)
         .oldName(ORG_NAME)
         .id(ORG_ID)
         .theme(null)
         .members(List.of(
            IdentityModel.builder().identityID(SALES).type(Identity.GROUP).build(),
            IdentityModel.builder().identityID(ANALYST).type(Identity.ROLE).build()))
         .build();

      editOrganization(model);

      assertFalse(users.containsKey(ALICE), "a genuinely dropped member must still be removed");
      verify(provider, atLeastOnce()).getUser(ALICE);
   }

   private void editOrganization(EditOrganizationPaneModel model) throws Exception {
      service.editOrganization(model, "Primary", requester);
   }

   private static EditOrganizationPaneModel model(String name, String oldName, String id,
                                                   String locale)
   {
      return EditOrganizationPaneModel.builder()
         .name(name)
         .oldName(oldName)
         .id(id)
         .theme(null)
         .locale(locale)
         .members(List.of(
            IdentityModel.builder().identityID(ALICE).type(Identity.USER).build(),
            IdentityModel.builder().identityID(SALES).type(Identity.GROUP).build(),
            IdentityModel.builder().identityID(ANALYST).type(Identity.ROLE).build()))
         .build();
   }

   private void assertMembersSurvive() {
      assertEquals(Set.of(ALICE, HIDDEN), users.keySet());
      assertEquals(Set.of(SALES), groups.keySet());
      assertEquals(Set.of(ANALYST), roles.keySet());
   }

   // updateOrganizationMembers()'s own kept-member loop is the only place, on this scenario, that
   // calls eprovider.getUser(IdentityID) (singular) -- IdentityService.setIdentity()'s own
   // wrapper calls getUsers()/getGroups() (plural) for every identity type regardless, and
   // keepUndeletableGroupsAndRoles() (which runs before the fixed gate) calls getGroups()/
   // getRoles(), so none of those four are a useful signal here. isExistingIdentityOfOtherOrg()
   // also calls getUser(IdentityID) but only for a member of a DIFFERENT org, which none of this
   // test's members are. A genuinely unchanged save must not invoke updateOrganizationMembers(),
   // hence never call eprovider.getUser(IdentityID) at all.
   private void verifyMemberUpdateNotRun() {
      verify(provider, never()).getUser(any());
      verify(provider, never()).setUser(any(), any());
      verify(provider, never()).removeUser(any());
   }

   private void seedOrg() {
      FSOrganization org = new FSOrganization(ORG_ID);
      org.setName(ORG_NAME);
      org.setMembers(new String[] { "alice", "hidden", "sales", "analyst" });
      orgs.put(ORG_ID, org);
      users.put(ALICE, new FSUser(ALICE));
      users.put(HIDDEN, new FSUser(HIDDEN));
      groups.put(SALES, new FSGroup(SALES));
      roles.put(ANALYST, new FSRole(ANALYST));
   }

   private static final String HOST_ORG = "host-org";
   private static final String ORG_ID = "orga";
   private static final String ORG_NAME = "Org A";
   private static final IdentityID ALICE = new IdentityID("alice", ORG_ID);
   private static final IdentityID HIDDEN = new IdentityID("hidden", ORG_ID);
   private static final IdentityID SALES = new IdentityID("sales", ORG_ID);
   private static final IdentityID ANALYST = new IdentityID("analyst", ORG_ID);

   private final Map<String, Organization> orgs = new LinkedHashMap<>();
   private final Map<IdentityID, User> users = new LinkedHashMap<>();
   private final Map<IdentityID, Group> groups = new LinkedHashMap<>();
   private final Map<IdentityID, Role> roles = new LinkedHashMap<>();
   private EditableAuthenticationProvider provider;
   private SecurityProvider securityProvider;
   private IdentityService identityService;
   private UserTreeService service;
   private XPrincipal requester;
   private Principal oldThreadPrincipal;
   private OrganizationManager organizationManager;
   private MockedStatic<OrganizationManager> organizationManagerStatic;
   private MockedStatic<SUtil> sUtilStatic;
   private MockedStatic<SreeEnv> sreeEnvStatic;
   private MockedStatic<SecurityEngine> securityEngineStatic;
   private MockedStatic<AutoSaveUtils> autoSave;
}
