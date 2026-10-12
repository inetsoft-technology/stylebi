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

import inetsoft.sree.portal.CustomThemesManager;
import inetsoft.sree.security.*;
import inetsoft.sree.security.support.SecurityTestDataBuilder;
import inetsoft.test.*;
import inetsoft.util.ThreadContext;
import inetsoft.util.audit.Audit;
import inetsoft.web.admin.general.LocalizationSettingsService;
import inetsoft.web.admin.security.action.ActionPermissionService;
import inetsoft.web.admin.security.user.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/*
 * Bug #77519: the public API GET of a group, role or user returns every member, but the PUT
 * saved an explicit member list filtered by the caller's ADMIN permission as the complete list.
 * An unchanged GET then PUT by a caller that is not a site admin silently removed the members it
 * can't administer: system-administrator-granting users and groups, and for a role delegate any
 * assignee it doesn't administer through another grant.
 *
 * This drives the real SecurityService against the real IdentityService,
 * FileAuthenticationProvider and DefaultCheckPermissionStrategy, because the removal happens in
 * IdentityService.setGroupInfo()/setRoleInfo()/setUserInfo(), which SecurityServiceTest mocks.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class,
                                  PermissionMatrixOrgLifecycleTest.CopyOnReadClusterConfig.class,
                                  SecurityServiceRoleAssignmentIntegrationTest.TestBeans.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class SecurityServiceHiddenMemberRoundTripIntegrationTest {
   private static final String HOST = "host-org";
   private static final String ORG_ID = "p77519";

   private static final IdentityID SYS_ADMIN_ROLE = new IdentityID("sysAdmin77519", ORG_ID);
   private static final IdentityID BOSS_ROLE = new IdentityID("boss77519", ORG_ID);
   private static final IdentityID PLAIN_ROLE = new IdentityID("plainRole", ORG_ID);
   private static final IdentityID PLAIN_GROUP = new IdentityID("plainGroup", ORG_ID);
   private static final IdentityID OTHER_GROUP = new IdentityID("otherGroup", ORG_ID);
   private static final IdentityID ADMIN_KIDS = new IdentityID("adminKids", ORG_ID);
   private static final IdentityID PLAIN_KID = new IdentityID("plainKid", ORG_ID);
   private static final IdentityID U1 = new IdentityID("u1", ORG_ID);
   private static final IdentityID SA2 = new IdentityID("sa2", ORG_ID);
   private static final IdentityID SA3 = new IdentityID("sa3", ORG_ID);

   private SecurityTestDataBuilder builder;
   private AuthorizationChain chain;
   private FileAuthenticationProvider fileProvider;
   private SecurityService service;
   private SRPrincipal delegate;
   private SRPrincipal boss;
   private SRPrincipal siteAdmin;
   private MockedStatic<Audit> auditStatic;

   @BeforeEach
   void setUp() throws Exception {
      // plainGroup has the member users u1 and sa2 (which grants system administrator) and the
      // member groups adminKids (which grants system administrator) and plainKid. u1 is also in
      // otherGroup. plainRole is assigned to u1, sa2 and otherGroup. sa3 grants system
      // administrator and is in no group. delegate holds ADMIN on plainGroup and plainRole only,
      // and boss is an organization administrator.
      builder = SecurityTestDataBuilder.create()
         .addOrg(Organization.getDefaultOrganizationName(), HOST)
         .addSysAdminRole("SiteAdmin77519", HOST)
         .addUser("sa", HOST, "password")
         .addUserToRole("sa", "SiteAdmin77519", HOST)
         .addOrg("Name77519", ORG_ID)
         .addSysAdminRole(SYS_ADMIN_ROLE.name, ORG_ID)
         .addOrgAdminRole(BOSS_ROLE.name, ORG_ID)
         .addRole(PLAIN_ROLE.name, ORG_ID)
         .addGroup(PLAIN_GROUP.name, ORG_ID)
         .addGroup(OTHER_GROUP.name, ORG_ID)
         .addGroup(ADMIN_KIDS.name, ORG_ID)
         .addGroup(PLAIN_KID.name, ORG_ID)
         .addGroupParent(ADMIN_KIDS.name, PLAIN_GROUP.name, ORG_ID)
         .addGroupParent(PLAIN_KID.name, PLAIN_GROUP.name, ORG_ID)
         .addRoleToGroup(SYS_ADMIN_ROLE.name, ADMIN_KIDS.name, ORG_ID)
         .addRoleToGroup(PLAIN_ROLE.name, OTHER_GROUP.name, ORG_ID)
         .addUser("delegate", ORG_ID, "password")
         .addUser("boss", ORG_ID, "password")
         .addUser(U1.name, ORG_ID, "password")
         .addUser(SA2.name, ORG_ID, "password")
         .addUser(SA3.name, ORG_ID, "password")
         .addUserToRole("boss", BOSS_ROLE.name, ORG_ID)
         .addUserToRole(SA2.name, SYS_ADMIN_ROLE.name, ORG_ID)
         .addUserToRole(SA3.name, SYS_ADMIN_ROLE.name, ORG_ID)
         .addUserToRole(U1.name, PLAIN_ROLE.name, ORG_ID)
         .addUserToRole(SA2.name, PLAIN_ROLE.name, ORG_ID)
         .addUserToGroup(U1.name, PLAIN_GROUP.name, ORG_ID)
         .addUserToGroup(SA2.name, PLAIN_GROUP.name, ORG_ID)
         .addUserToGroup(U1.name, OTHER_GROUP.name, ORG_ID)
         .setup();

      chain = SecurityEngine.getSecurity().getAuthorizationChain()
         .orElseThrow(() -> new AssertionError("expected an AuthorizationChain"));
      fileProvider = (FileAuthenticationProvider)
         ((AuthenticationChain) SecurityEngine.getSecurity().getSecurityProvider()
            .getAuthenticationProvider()).getProviders().get(0);

      grant(ResourceType.SECURITY_GROUP, PLAIN_GROUP, "delegate");
      grant(ResourceType.SECURITY_ROLE, PLAIN_ROLE, "delegate");

      Audit audit = mock(Audit.class);
      auditStatic = mockStatic(Audit.class);
      auditStatic.when(Audit::getInstance).thenReturn(audit);

      service = new SecurityService(
         SecurityEngine.getSecurity(), SecurityServiceRoleAssignmentIntegrationTest.newIdentityService(),
         mock(ActionPermissionService.class),
         mock(LocalizationSettingsService.class, RETURNS_DEEP_STUBS), mock(IdentityThemeService.class),
         mock(SystemAdminService.class), mock(UserTreeService.class), mock(CustomThemesManager.class));

      delegate = builder.principalOf("delegate", ORG_ID);
      boss = builder.principalOf("boss", ORG_ID);
      siteAdmin = builder.principalOf("sa", HOST);

      assertFalse(OrganizationManager.getInstance().isOrgAdmin(delegate),
                  "precondition: delegate is not an organization administrator");
      assertTrue(OrganizationManager.getInstance().isOrgAdmin(boss),
                 "precondition: boss is an organization administrator");
      assertTrue(OrganizationManager.getInstance().isSiteAdmin(siteAdmin),
                 "precondition: sa is a site administrator");
   }

   @AfterEach
   void tearDown() {
      ThreadContext.setContextPrincipal(null);

      if(auditStatic != null) {
         auditStatic.close();
      }

      if(chain != null) {
         chain.removePermission(ResourceType.SECURITY_GROUP, PLAIN_GROUP, ORG_ID);
         chain.removePermission(ResourceType.SECURITY_ROLE, PLAIN_ROLE, ORG_ID);
         chain.removePermission(ResourceType.SECURITY_USER, U1, ORG_ID);
      }

      if(builder != null) {
         builder.teardown();
         builder = null;
      }
   }

   // ---- updateGroup ------------------------------------------------------------------------

   @Test
   void delegate_groupRoundTrip_keepsSysAdminMemberUser() throws Exception {
      assertFalse(admin(delegate, ResourceType.SECURITY_USER, SA2),
                  "precondition: the delegate can't administer sa2");
      SecurityGroup body = asCaller(delegate, () -> service.getGroup(PLAIN_GROUP, delegate));
      assertEquals(Set.of(U1.name, SA2.name), new HashSet<>(body.getMemberUsers()),
                   "precondition: the GET returns every member user");

      asCaller(delegate, () -> { service.updateGroup(PLAIN_GROUP, body, delegate); return null; });

      assertEquals(Set.of(U1, SA2), memberUsers(PLAIN_GROUP), "an unchanged PUT keeps every member");
      assertEquals(Set.of(ADMIN_KIDS, PLAIN_KID), memberGroups(PLAIN_GROUP));
   }

   @Test
   void orgAdmin_groupRoundTrip_keepsSysAdminMemberUserAndGroup() throws Exception {
      assertFalse(admin(boss, ResourceType.SECURITY_GROUP, ADMIN_KIDS),
                  "precondition: the org admin can't administer adminKids");
      SecurityGroup body = asCaller(boss, () -> service.getGroup(PLAIN_GROUP, boss));
      assertEquals(Set.of(ADMIN_KIDS.name, PLAIN_KID.name), new HashSet<>(body.getMemberGroups()),
                   "precondition: the GET returns every member group");

      asCaller(boss, () -> { service.updateGroup(PLAIN_GROUP, body, boss); return null; });

      assertEquals(Set.of(U1, SA2), memberUsers(PLAIN_GROUP));
      assertEquals(Set.of(ADMIN_KIDS, PLAIN_KID), memberGroups(PLAIN_GROUP));
   }

   @Test
   void orgAdmin_groupEmptyLists_removeOnlyAdministrableMembers() throws Exception {
      SecurityGroup body = new SecurityGroup();
      body.setMemberUsers(List.of());
      body.setMemberGroups(List.of());

      asCaller(boss, () -> { service.updateGroup(PLAIN_GROUP, body, boss); return null; });

      assertEquals(Set.of(SA2), memberUsers(PLAIN_GROUP), "u1 is removed, sa2 is kept");
      assertEquals(Set.of(ADMIN_KIDS), memberGroups(PLAIN_GROUP), "plainKid is removed");
   }

   @Test
   void orgAdmin_groupList_stillRefusesAddingNotAdministrableNonMember() throws Exception {
      SecurityGroup body = new SecurityGroup();
      body.setMemberUsers(List.of(U1.name, SA2.name, SA3.name));

      asCaller(boss, () -> { service.updateGroup(PLAIN_GROUP, body, boss); return null; });

      assertEquals(Set.of(U1, SA2), memberUsers(PLAIN_GROUP), "sa3 is still left out");
   }

   @Test
   void siteAdmin_groupEmptyLists_removeEveryMember() throws Exception {
      SecurityGroup body = new SecurityGroup();
      body.setMemberUsers(List.of());
      body.setMemberGroups(List.of());

      asCaller(siteAdmin, () -> { service.updateGroup(PLAIN_GROUP, body, siteAdmin); return null; });

      assertEquals(Set.of(), memberUsers(PLAIN_GROUP));
      assertEquals(Set.of(), memberGroups(PLAIN_GROUP));
   }

   // ---- updateRole -------------------------------------------------------------------------

   @Test
   void roleDelegate_roleRoundTrip_keepsAssigneesItCannotAdminister() throws Exception {
      assertTrue(admin(delegate, ResourceType.SECURITY_USER, U1),
                 "precondition: the delegate administers u1 through plainGroup");
      assertFalse(admin(delegate, ResourceType.SECURITY_GROUP, OTHER_GROUP),
                  "precondition: the delegate can't administer otherGroup");
      SecurityRole body = asCaller(delegate, () -> service.getRole(PLAIN_ROLE, delegate));
      assertEquals(Set.of(U1.name, SA2.name), new HashSet<>(body.getAssignedUsers()));
      assertEquals(Set.of(OTHER_GROUP.name), new HashSet<>(body.getAssignedGroups()));

      asCaller(delegate, () -> { service.updateRole(PLAIN_ROLE, body, delegate); return null; });

      assertTrue(hasRole(U1), "u1 keeps the role");
      assertTrue(hasRole(SA2), "sa2 keeps the role");
      assertTrue(groupHasRole(OTHER_GROUP), "otherGroup keeps the role");
   }

   @Test
   void roleDelegate_roleList_removesOnlyAdministrableAssignee() throws Exception {
      SecurityRole body = new SecurityRole();
      body.setAssignedUsers(List.of());
      body.setAssignedGroups(List.of());

      asCaller(delegate, () -> { service.updateRole(PLAIN_ROLE, body, delegate); return null; });

      assertFalse(hasRole(U1), "u1, which the delegate administers, loses the role");
      assertTrue(hasRole(SA2), "sa2 keeps the role");
      assertTrue(groupHasRole(OTHER_GROUP), "otherGroup keeps the role");
   }

   @Test
   void roleDelegate_roleList_stillRefusesAssigningNotAdministrableNonAssignee() throws Exception {
      assertFalse(admin(delegate, ResourceType.SECURITY_USER, SA3),
                  "precondition: the delegate can't administer sa3");
      assertFalse(hasRole(SA3), "precondition: sa3 doesn't hold the role");
      SecurityRole body = new SecurityRole();
      body.setAssignedUsers(List.of(U1.name, SA2.name, SA3.name));

      asCaller(delegate, () -> { service.updateRole(PLAIN_ROLE, body, delegate); return null; });

      assertFalse(hasRole(SA3), "sa3 is still left out");
      assertTrue(hasRole(U1));
      assertTrue(hasRole(SA2));
      assertTrue(groupHasRole(OTHER_GROUP), "the omitted group list keeps otherGroup");
   }

   // ---- updateUser -------------------------------------------------------------------------

   @Test
   void delegate_userRoundTrip_keepsGroupItCannotAdminister() throws Exception {
      SecurityUser body = asCaller(delegate, () -> service.getUser(U1, delegate));
      assertEquals(Set.of(PLAIN_GROUP.name, OTHER_GROUP.name), new HashSet<>(body.getGroups()));

      asCaller(delegate, () -> { service.updateUser(U1, body, delegate); return null; });

      assertEquals(Set.of(PLAIN_GROUP.name, OTHER_GROUP.name), groups(U1));
   }

   @Test
   void delegate_userEmptyGroups_leavesOnlyAdministrableGroup() throws Exception {
      SecurityUser body = new SecurityUser();
      body.setGroups(List.of());

      asCaller(delegate, () -> { service.updateUser(U1, body, delegate); return null; });

      assertEquals(Set.of(OTHER_GROUP.name), groups(U1), "u1 leaves only plainGroup");
   }

   private interface Call<T> {
      T run() throws Exception;
   }

   private static <T> T asCaller(SRPrincipal caller, Call<T> call) throws Exception {
      ThreadContext.setContextPrincipal(caller);
      return OrganizationManager.runInOrgScope(ORG_ID, call::run);
   }

   private boolean admin(SRPrincipal caller, ResourceType type, IdentityID id) throws Exception {
      return asCaller(caller, () -> SecurityEngine.getSecurity().getSecurityProvider()
         .checkPermission(caller, type, id.convertToKey(), ResourceAction.ADMIN));
   }

   private Set<IdentityID> memberUsers(IdentityID group) {
      Set<IdentityID> result = new HashSet<>();

      for(IdentityID id : fileProvider.getUsers()) {
         User user = fileProvider.getUser(id);

         if(ORG_ID.equals(id.orgID) && user.getGroups() != null &&
            Arrays.asList(user.getGroups()).contains(group.name))
         {
            result.add(id);
         }
      }

      return result;
   }

   private Set<IdentityID> memberGroups(IdentityID group) {
      Set<IdentityID> result = new HashSet<>();

      for(IdentityID id : fileProvider.getGroups()) {
         Group child = fileProvider.getGroup(id);

         if(ORG_ID.equals(id.orgID) && child.getGroups() != null &&
            Arrays.asList(child.getGroups()).contains(group.name))
         {
            result.add(id);
         }
      }

      return result;
   }

   private Set<String> groups(IdentityID user) {
      String[] groups = fileProvider.getUser(user).getGroups();
      return groups == null ? Set.of() : new HashSet<>(Arrays.asList(groups));
   }

   private boolean hasRole(IdentityID user) {
      IdentityID[] roles = fileProvider.getUser(user).getRoles();
      return roles != null && Arrays.asList(roles).contains(PLAIN_ROLE);
   }

   private boolean groupHasRole(IdentityID group) {
      IdentityID[] roles = fileProvider.getGroup(group).getRoles();
      return roles != null && Arrays.asList(roles).contains(PLAIN_ROLE);
   }

   private void grant(ResourceType type, IdentityID id, String... users) {
      Permission permission = new Permission();
      permission.setUserGrantsForOrg(ResourceAction.ADMIN, Set.of(users), ORG_ID);
      permission.updateGrantAllByOrg(ORG_ID, true);
      chain.setPermission(type, id, permission, ORG_ID);
   }
}
