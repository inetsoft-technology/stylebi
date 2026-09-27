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
package inetsoft.sree.security;

import inetsoft.mv.MVManager;
import inetsoft.report.LibManagerProvider;
import inetsoft.report.internal.license.LicenseManager;
import inetsoft.sree.ClientInfo;
import inetsoft.sree.RepletRegistryManager;
import inetsoft.sree.RepletRepository;
import inetsoft.sree.internal.DataCycleManager;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.internal.cluster.MockCluster;
import inetsoft.sree.portal.CustomThemesManager;
import inetsoft.sree.portal.PortalThemesManager;
import inetsoft.sree.schedule.ScheduleClient;
import inetsoft.sree.schedule.ScheduleManager;
import inetsoft.sree.security.support.SecurityTestDataBuilder;
import inetsoft.sree.web.SessionLicenseServiceProvider;
import inetsoft.sree.web.dashboard.DashboardManager;
import inetsoft.sree.web.dashboard.DashboardRegistryManager;
import inetsoft.storage.BlobStorageManager;
import inetsoft.storage.ExternalStorageService;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.sync.DependencyStorageService;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.Identity;
import inetsoft.util.DataSpace;
import inetsoft.util.IndexedStorage;
import inetsoft.util.ThreadContext;
import inetsoft.util.log.LogManager;
import inetsoft.web.RecycleBin;
import inetsoft.web.admin.favorites.FavoritesService;
import inetsoft.web.admin.pageheader.EmPageHeaderController;
import inetsoft.web.admin.pageheader.EmPageHeaderModel;
import inetsoft.web.admin.security.IdentityModel;
import inetsoft.web.admin.security.IdentityService;
import inetsoft.web.admin.security.user.EditGroupPaneModel;
import inetsoft.web.admin.security.user.EditRolePaneModel;
import inetsoft.web.admin.security.user.IdentityThemeService;
import inetsoft.web.admin.server.NodeProtectionService;
import inetsoft.web.session.IgniteSessionRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Bug #77199: {@link OrganizationManager#isSiteAdmin(java.security.Principal)} and
 * {@link OrganizationManager#isOrgAdmin(java.security.Principal)} trusted the roles of the session
 * principal. For a login against the security provider, those are the expanded roles at login
 * time. Only the user editor rewrites live principals, so revoking Administrator by editing a
 * group or a role left it in the session until the user logged in again.
 *
 * <p>The principals here come from a real {@link SecurityEngine} login and are read back from an
 * {@link IgniteSessionRepository} session through a serialization round trip, as another node
 * would see them. {@code SecurityTestDataBuilder.principalOf} is not used because it builds a
 * principal with the direct roles only, which hides this class of bug.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class OrganizationManagerStalePrincipalTest {
   @BeforeAll
   static void setUpAll() throws Exception {
      builder = SecurityTestDataBuilder.create()
         .addOrg("acme", ORG)
         .addOrg("widgets", ORG2)
         // the administrator that makes the edits
         .addSysAdminRole("BossAdmin", ORG)
         .addUser("boss", ORG, PASSWORD)
         .addUserToRole("boss", "BossAdmin", ORG)
         // admin through a group
         .addSysAdminRole("AdminG", ORG)
         .addGroup("G", ORG)
         .addRoleToGroup("AdminG", "G", ORG)
         .addUser("ug", ORG, PASSWORD)
         .addUserToGroup("ug", "G", ORG)
         // admin through a parent role
         .addSysAdminRole("AdminP", ORG)
         .addRole("Child", ORG)
         .addRoleParent("Child", "AdminP", ORG)
         .addUser("ur", ORG, PASSWORD)
         .addUserToRole("ur", "Child", ORG)
         // admin as a direct member of the role
         .addSysAdminRole("AdminM", ORG)
         .addUser("um", ORG, PASSWORD)
         .addUserToRole("um", "AdminM", ORG)
         // admin revoked in the user editor
         .addSysAdminRole("AdminU", ORG)
         .addUser("uu", ORG, PASSWORD)
         .addUserToRole("uu", "AdminU", ORG)
         // organization admin through a group
         .addRole("OrgAdminG", ORG)
         .addGroup("GO", ORG)
         .addRoleToGroup("OrgAdminG", "GO", ORG)
         .addUser("uo", ORG, PASSWORD)
         .addUserToGroup("uo", "GO", ORG)
         // admin that is removed from the store while logged in
         .addSysAdminRole("AdminD", ORG)
         .addGroup("GD", ORG)
         .addRoleToGroup("AdminD", "GD", ORG)
         .addUser("ud", ORG, PASSWORD)
         .addUserToGroup("ud", "GD", ORG)
         // admin revoked on another node
         .addSysAdminRole("AdminX", ORG)
         .addGroup("GX", ORG)
         .addRoleToGroup("AdminX", "GX", ORG)
         .addUser("ux", ORG, PASSWORD)
         .addUserToGroup("ux", "GX", ORG)
         // admin revoked before switching the EM organization
         .addSysAdminRole("AdminS", ORG)
         .addGroup("GS", ORG)
         .addRoleToGroup("AdminS", "GS", ORG)
         .addUser("us", ORG, PASSWORD)
         .addUserToGroup("us", "GS", ORG)
         // admin through the parent group of the user's group
         .addSysAdminRole("AdminN", ORG)
         .addGroup("Top", ORG)
         .addGroup("Sub", ORG)
         .addGroupParent("Sub", "Top", ORG)
         .addRoleToGroup("AdminN", "Top", ORG)
         .addUser("un", ORG, PASSWORD)
         .addUserToGroup("un", "Sub", ORG)
         // direct member of the built-in organization administrator role
         .addRole("Organization Administrator", ORG)
         .addUser("uoa", ORG, PASSWORD)
         .addUserToRole("uoa", "Organization Administrator", ORG)
         // non-admin users
         .addUser("plain", ORG, PASSWORD)
         .addUser("grantee", ORG, PASSWORD)
         .addSysAdminRole("AdminV", ORG)
         .setup();

      // The builder's provider is a separate instance from the one in the security engine's
      // chain. Both use the same storage, but each has its own user role cache, so the builder's
      // instance stands in for the provider on another cluster node.
      chainProvider = (FileAuthenticationProvider)
         ((AuthenticationChain) SecurityEngine.getSecurity().getSecurityProvider()
            .getAuthenticationProvider()).getProviderList().get(0);
      otherNodeProvider =
         (FileAuthenticationProvider) ReflectionTestUtils.getField(builder, "authcProvider");
      assertNotSame(chainProvider, otherNodeProvider);

      for(String name : new String[] { "OrgAdminG", "Organization Administrator" }) {
         FSRole orgAdminRole = (FSRole) chainProvider.getRole(new IdentityID(name, ORG));
         orgAdminRole.setOrgAdmin(true);
         chainProvider.setRole(orgAdminRole.getIdentityID(), orgAdminRole);
      }

      sutil = mockStatic(SUtil.class, CALLS_REAL_METHODS);
      sutil.when(SUtil::isMultiTenant).thenReturn(true);
      sutil.when(() -> SUtil.setAdditionalDatasource(any())).thenAnswer(invocation -> null);

      cluster = new MockCluster();
      sessionRepository = new IgniteSessionRepository(
         SecurityEngine.getSecurity(), mock(AuthenticationService.class),
         mock(NodeProtectionService.class), cluster);
      sessionRepository.afterPropertiesSet();
      identityService = createIdentityService(sessionRepository);
   }

   @AfterAll
   static void tearDownAll() throws Exception {
      ThreadContext.setContextPrincipal(null);
      ThreadContext.setPrincipal(null);

      if(sutil != null) {
         sutil.close();
      }

      if(sessionRepository != null) {
         sessionRepository.destroy();
      }

      if(builder != null) {
         builder.teardown();
      }
   }

   @BeforeEach
   void setUp() {
      boss = login("boss");
      ThreadContext.setContextPrincipal(boss);
      ThreadContext.setPrincipal(boss);
   }

   @Test
   void groupRevokeIsEffectiveInLiveSession() throws Exception {
      String session = store(login("ug"));
      assertTrue(isSiteAdmin(session));

      IdentityID group = new IdentityID("G", ORG);
      EditGroupPaneModel model = EditGroupPaneModel.builder()
         .name("G").organization(ORG)
         .roles(new ArrayList<>())
         .members(List.of(userModel("ug")))
         .build();
      identityService.setIdentity(chainProvider.getGroup(group), model, chainProvider, boss);

      assertEquals(0, chainProvider.getGroup(group).getRoles().length);
      assertTrue(Arrays.asList(readBack(session).getRoles()).contains(new IdentityID("AdminG", ORG)),
                 "the session principal still holds the role that was expanded at login");
      assertFalse(isSiteAdmin(session));
   }

   @Test
   void parentRoleRevokeIsEffectiveInLiveSession() throws Exception {
      String session = store(login("ur"));
      assertTrue(isSiteAdmin(session));

      IdentityID child = new IdentityID("Child", ORG);
      EditRolePaneModel model = EditRolePaneModel.builder()
         .name("Child").oldName("Child").organization(ORG)
         .isSysAdmin(false).isOrgAdmin(false)
         .roles(new ArrayList<>())
         .members(List.of(userModel("ur")))
         .build();
      identityService.setIdentity(chainProvider.getRole(child), model, chainProvider, boss);

      assertEquals(0, chainProvider.getRole(child).getRoles().length);
      assertFalse(isSiteAdmin(session));
   }

   @Test
   void roleMemberRemovalIsEffectiveInLiveSession() throws Exception {
      String session = store(login("um"));
      assertTrue(isSiteAdmin(session));

      IdentityID role = new IdentityID("AdminM", ORG);
      EditRolePaneModel model = EditRolePaneModel.builder()
         .name("AdminM").oldName("AdminM").organization(ORG)
         .isSysAdmin(true).isOrgAdmin(false)
         .roles(new ArrayList<>())
         .members(new ArrayList<>())
         .build();
      identityService.setIdentity(chainProvider.getRole(role), model, chainProvider, boss);

      assertEquals(0, chainProvider.getUser(new IdentityID("um", ORG)).getRoles().length);
      assertFalse(isSiteAdmin(session));
   }

   /**
    * Control: the user editor already rewrites live principals, so this was never stale.
    */
   @Test
   void userEditorRevokeIsEffectiveInLiveSession() throws Exception {
      String session = store(login("uu"));
      assertTrue(isSiteAdmin(session));

      IdentityID userID = new IdentityID("uu", ORG);
      FSUser user = (FSUser) chainProvider.getUser(userID);
      user.setRoles(new IdentityID[0]);
      chainProvider.setUser(userID, user);
      // the same call that IdentityService.setUserInfo() makes
      sessionRepository.updatePrincipalRolesAndGroups(
         userID, user.getRoles(), user.getGroups(), chainProvider);

      assertFalse(isSiteAdmin(session));
   }

   @Test
   void orgAdminGroupRevokeIsEffectiveInLiveSession() throws Exception {
      String session = store(login("uo"));
      assertTrue(OrganizationManager.getInstance().isOrgAdmin(readBack(session)));

      IdentityID group = new IdentityID("GO", ORG);
      EditGroupPaneModel model = EditGroupPaneModel.builder()
         .name("GO").organization(ORG)
         .roles(new ArrayList<>())
         .members(List.of(userModel("uo")))
         .build();
      identityService.setIdentity(chainProvider.getGroup(group), model, chainProvider, boss);

      assertFalse(OrganizationManager.getInstance().isOrgAdmin(readBack(session)));
   }

   @Test
   void grantIsEffectiveInLiveSession() throws Exception {
      String session = store(login("grantee"));
      assertFalse(isSiteAdmin(session));

      IdentityID userID = new IdentityID("grantee", ORG);
      FSUser user = (FSUser) chainProvider.getUser(userID);
      user.setRoles(new IdentityID[] { new IdentityID("AdminV", ORG) });
      chainProvider.setUser(userID, user);

      assertTrue(isSiteAdmin(session));
   }

   /**
    * The user's session outlives the removal of the user from the store, e.g. by a change in an
    * external directory. {@code FileAuthenticationProvider.removeUser()} does not clear the user
    * role cache, so {@code isSiteAdmin(IdentityID)} would still say true.
    */
   @Test
   void deletedUserWithLiveSessionIsNotSiteAdmin() throws Exception {
      String session = store(login("ud"));
      assertTrue(isSiteAdmin(session));

      chainProvider.removeUser(new IdentityID("ud", ORG));

      assertNull(chainProvider.getUser(new IdentityID("ud", ORG)));
      assertFalse(isSiteAdmin(session));
   }

   /**
    * The revoke is made through another provider instance that shares the storage, as on another
    * cluster node. This node's user role cache is not invalidated, so the answer must not come
    * from {@code AuthenticationProvider.getRoles(IdentityID)}.
    */
   @Test
   void revokeOnAnotherNodeIsEffectiveInLiveSession() throws Exception {
      IdentityID userID = new IdentityID("ux", ORG);
      String session = store(login("ux"));
      assertTrue(isSiteAdmin(session));
      assertTrue(Arrays.asList(chainProvider.getRoles(userID)).contains(new IdentityID("AdminX", ORG)));

      IdentityID groupID = new IdentityID("GX", ORG);
      FSGroup group = (FSGroup) otherNodeProvider.getGroup(groupID);
      group.setRoles(new IdentityID[0]);
      otherNodeProvider.setGroup(groupID, group);

      assertEquals(0, chainProvider.getGroup(groupID).getRoles().length);
      assertFalse(isSiteAdmin(session));
   }

   /**
    * The roles of a parent group of the user's group are included, so a genuine admin through a
    * nested group is not locked out, and a revoke on the parent group is effective.
    */
   @Test
   void parentGroupAdminIsKeptAndItsRevokeIsEffective() throws Exception {
      String session = store(login("un"));
      assertTrue(isSiteAdmin(session));

      IdentityID groupID = new IdentityID("Top", ORG);
      FSGroup group = (FSGroup) chainProvider.getGroup(groupID);
      group.setRoles(new IdentityID[0]);
      chainProvider.setGroup(groupID, group);

      assertTrue(Arrays.asList(readBack(session).getRoles()).contains(new IdentityID("AdminN", ORG)),
                 "the session principal still holds the role that was expanded at login");
      assertFalse(isSiteAdmin(session));
   }

   /**
    * The built-in organization administrator role is hidden when multi-tenancy is disabled, so
    * a user that is a direct member of it is not an organization admin in a single-tenant
    * installation, the same as before the storage-only check.
    */
   @Test
   void organizationAdministratorRoleIsIgnoredWithoutMultiTenancy() throws Exception {
      SRPrincipal principal;

      try {
         sutil.when(SUtil::isMultiTenant).thenReturn(false);
         principal = readBack(store(login("uoa")));
         assertEquals("true", principal.getProperty("__internal__"));
         assertFalse(OrganizationManager.getInstance().isOrgAdmin(principal));
      }
      finally {
         sutil.when(SUtil::isMultiTenant).thenReturn(true);
      }

      // the same principal is an organization admin when multi-tenancy is enabled
      assertTrue(OrganizationManager.getInstance().isOrgAdmin(principal));
   }

   /**
    * An SSO (e.g. OpenID) principal is not internal. Its roles are asserted by the identity
    * provider, so they are still trusted even when a stored user with the same name exists.
    */
   @Test
   void ssoPrincipalWithStoredNonAdminUserKeepsAssertedAdminRole() {
      IdentityID userID = new IdentityID("plain", ORG);
      assertNotNull(chainProvider.getUser(userID));
      SRPrincipal principal = new SRPrincipal(
         new ClientInfo(userID, "127.0.0.1"), new IdentityID[] { new IdentityID("AdminV", ORG) },
         new String[0], ORG, 1L);

      assertNull(principal.getProperty("__internal__"));
      assertTrue(OrganizationManager.getInstance().isSiteAdmin(principal));
   }

   /**
    * A principal that represents a role, e.g. for running as a role identity, is internal and
    * virtual. There is no stored user with its name, so its own roles are used.
    */
   @Test
   void virtualRolePrincipalKeepsItsAdminRole() {
      Role role = chainProvider.getRole(new IdentityID("AdminV", ORG));
      SRPrincipal principal = SUtil.getPrincipal(role, "127.0.0.1", false);

      assertEquals("true", principal.getProperty("__internal__"));
      assertEquals("true", principal.getProperty("virtual"));
      assertTrue(OrganizationManager.getInstance().isSiteAdmin(principal));
   }

   /**
    * The EM organization switch is gated by isSiteAdmin(), so a user whose Administrator role was
    * revoked through a group edit can no longer switch into another tenant.
    */
   @Test
   void revokedAdminCannotSwitchOrganization() throws Exception {
      SRPrincipal user = login("us");
      assertTrue(OrganizationManager.getInstance().isSiteAdmin(user));
      IdentityID groupID = new IdentityID("GS", ORG);
      FSGroup group = (FSGroup) chainProvider.getGroup(groupID);
      group.setRoles(new IdentityID[0]);
      chainProvider.setGroup(groupID, group);

      Cluster emCluster = mock(Cluster.class);
      DataSourceRegistry dataSourceRegistry = mock(DataSourceRegistry.class);
      EmPageHeaderController controller = new EmPageHeaderController(
         SecurityEngine.getSecurity(), emCluster, dataSourceRegistry, mock(MVManager.class),
         mock(IndexedStorage.class));
      controller.setCurrOrg(
         new EmPageHeaderModel(null, null, ORG2, chainProvider.getProviderName(), true), user);

      // the principal only keeps curr_org_id in an EM session, so check the switch's side effects
      verify(emCluster, never()).sendMessage(any());
      verify(dataSourceRegistry, never()).init();
   }

   private static SRPrincipal login(String name) {
      IdentityID userID = new IdentityID(name, ORG);
      SecurityEngine engine = SecurityEngine.getSecurity();
      SRPrincipal principal = (SRPrincipal) engine.authenticate(
         new ClientInfo(userID, "127.0.0.1", "session-" + name),
         new DefaultTicket(userID, PASSWORD), engine.getSecurityProvider());
      assertNotNull(principal, "login failed for " + name);
      assertEquals("true", principal.getProperty("__internal__"));
      return principal;
   }

   private static String store(SRPrincipal principal) {
      IgniteSessionRepository.IgniteSession session = sessionRepository.createSession();
      session.setAttribute(RepletRepository.PRINCIPAL_COOKIE, principal);
      sessionRepository.save(session);
      return session.getId();
   }

   /**
    * Reads the session principal back as a copy, as another node would see it.
    */
   private static SRPrincipal readBack(String sessionId) throws Exception {
      SRPrincipal principal = sessionRepository.findById(sessionId)
         .getAttribute(RepletRepository.PRINCIPAL_COOKIE);
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();

      try(ObjectOutputStream out = new ObjectOutputStream(bytes)) {
         out.writeObject(principal);
      }

      try(ObjectInputStream in =
             new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray())))
      {
         return (SRPrincipal) in.readObject();
      }
   }

   private static boolean isSiteAdmin(String sessionId) throws Exception {
      return OrganizationManager.getInstance().isSiteAdmin(readBack(sessionId));
   }

   private static IdentityModel userModel(String name) {
      return IdentityModel.builder()
         .identityID(new IdentityID(name, ORG))
         .type(Identity.USER)
         .build();
   }

   private static IdentityService createIdentityService(IgniteSessionRepository repository) {
      SecurityEngine engine = SecurityEngine.getSecurity();
      return new IdentityService(
         engine, engine.getSecurityProvider(), mock(IdentityThemeService.class),
         mock(AuthenticationService.class), mock(BlobStorageManager.class),
         mock(FavoritesService.class), mock(Cluster.class), mock(MVManager.class),
         mock(DataCycleManager.class), mock(DataSourceRegistry.class), mock(LogManager.class),
         mock(LicenseManager.class), mock(ScheduleManager.class), mock(IndexedStorage.class),
         Optional.empty(), mock(ScheduleClient.class), mock(CustomThemesManager.class),
         mock(SessionLicenseServiceProvider.class), mock(DashboardRegistryManager.class),
         mock(LibManagerProvider.class), mock(DashboardManager.class),
         mock(PortalThemesManager.class), mock(RecycleBin.class), mock(DataSpace.class),
         mock(DependencyStorageService.class), mock(ExternalStorageService.class),
         mock(XRepository.class), mock(RepletRegistryManager.class), Optional.of(repository));
   }

   private static final String ORG = "acme_id";
   private static final String ORG2 = "widgets_id";
   private static final String PASSWORD = "Passw0rd!";

   private static SecurityTestDataBuilder builder;
   private static FileAuthenticationProvider chainProvider;
   private static FileAuthenticationProvider otherNodeProvider;
   private static MockedStatic<SUtil> sutil;
   private static MockCluster cluster;
   private static IgniteSessionRepository sessionRepository;
   private static IdentityService identityService;
   private SRPrincipal boss;
}
