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

import inetsoft.sree.internal.SUtil;
import inetsoft.test.*;
import inetsoft.uql.util.Identity;
import inetsoft.util.Tool;
import inetsoft.web.admin.schedule.ScheduleTaskIdentityChecker;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77498, the Bug #77347 fix keeps org-wide delegated grants (org node, Users root, Groups
 * root, org self grant, user wildcard) and explicit grants away from users that are SYSTEM
 * administrators. They must not reach users, groups and roles that grant ORGANIZATION
 * administrator either. Otherwise a delegate that is not an org admin gets SECURITY_USER ADMIN
 * over an org admin, which is the only check SecurityApiService.changeUserPassword, EM edit-user
 * (password field) and Log-in-As make, or SECURITY_GROUP ADMIN over a group holding an org admin
 * role, which lets it run a schedule task as that group. Only site admins and org admins (role
 * based) keep permission over such an identity. A holder of ADMIN on the org node that is not
 * an org admin by role gets the same treatment as the other delegates.
 * <p>
 * Mirrors DefaultCheckPermissionStrategySiteAdminTargetTest (multi-tenant mode).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@SreeHome()
@Tag("core")
class DefaultCheckPermissionStrategyOrgAdminTargetTest {
   static final String ORG = "testOrg";
   static final String DELEGATE = "testUser";
   static final String DELEGATE_ROLE = "testRole";
   static final IdentityID GLOBAL_OA_ROLE = new IdentityID("Organization Administrator", null);
   static final IdentityID FLAGGED_OA_ROLE = new IdentityID("orgBoss", ORG);
   static final IdentityID OA_GROUP = new IdentityID("oaGroup", ORG);
   static final IdentityID PLAIN_GROUP = new IdentityID("grpA", ORG);

   enum DelegatedGrant { ORG_NODE, USERS_ROOT, GROUPS_ROOT, ORG_SELF_GRANT, USER_WILDCARD, EXPLICIT_USER }

   enum Target { GLOBAL_OA_ROLE_USER, FLAGGED_ROLE_USER, OA_GROUP_USER }

   static Stream<Arguments> cases() {
      return Arrays.stream(Target.values())
         .flatMap(t -> Arrays.stream(DelegatedGrant.values()).map(g -> Arguments.of(t, g)));
   }

   SecurityProvider mockProvider;
   DefaultCheckPermissionStrategy strategy;
   final Map<Target, IdentityID> targets = new EnumMap<>(Target.class);
   IdentityID plainUser;

   @BeforeEach
   void init() {
      plainUser = new IdentityID("bobA", ORG);
      targets.put(Target.GLOBAL_OA_ROLE_USER, new IdentityID("oaUser", ORG));
      targets.put(Target.FLAGGED_ROLE_USER, new IdentityID("bossUser", ORG));
      targets.put(Target.OA_GROUP_USER, new IdentityID("groupBoss", ORG));

      mockProvider = Mockito.mock(SecurityProvider.class);
      strategy = new DefaultCheckPermissionStrategy(mockProvider);

      lenient().when(mockProvider.getUser(any())).thenReturn(null);
      lenient().when(mockProvider.getGroup(any())).thenReturn(null);
      lenient().when(mockProvider.getRole(any())).thenReturn(null);
      lenient().when(mockProvider.getRoles(any())).thenReturn(new IdentityID[0]);
      lenient().when(mockProvider.getUserGroups(any())).thenReturn(new String[0]);
      lenient().when(mockProvider.getAllGroups(any(IdentityID[].class))).thenAnswer(inv -> inv.getArgument(0));
      lenient().when(mockProvider.isSystemAdministratorRole(any())).thenReturn(false);
      lenient().when(mockProvider.isOrgAdministratorRole(any())).thenReturn(false);
      lenient().when(mockProvider.isOrgAdministratorRole(eq(GLOBAL_OA_ROLE))).thenReturn(true);
      lenient().when(mockProvider.isOrgAdministratorRole(eq(FLAGGED_OA_ROLE))).thenReturn(true);
      lenient().when(mockProvider.getAllRoles(any(IdentityID[].class))).thenAnswer(inv -> inv.getArgument(0));
      lenient().when(mockProvider.getOrgNameFromID(anyString())).thenReturn(ORG);
      lenient().when(mockProvider.getPermission(any(ResourceType.class), anyString(), anyString())).thenReturn(null);
      lenient().when(mockProvider.getPermission(any(ResourceType.class), anyString())).thenReturn(null);
      lenient().when(mockProvider.getPermission(any(ResourceType.class), any(IdentityID.class))).thenReturn(null);
      AuthenticationProvider mockAuthProvider = Mockito.mock(AuthenticationProvider.class);
      lenient().when(mockProvider.getAuthenticationProvider()).thenReturn(mockAuthProvider);
      Organization mockOrg = Mockito.mock(Organization.class);
      lenient().when(mockOrg.getOrganizationID()).thenReturn(ORG);
      lenient().when(mockOrg.getRoles()).thenReturn(new IdentityID[0]);
      lenient().when(mockProvider.getOrganization(anyString())).thenReturn(mockOrg);

      stubRole(FLAGGED_OA_ROLE);
      stubRole(new IdentityID("analysts", ORG));
      stubGroup(OA_GROUP.name, new IdentityID[]{ FLAGGED_OA_ROLE });
      stubGroup(PLAIN_GROUP.name, new IdentityID[0]);
      stubUser(targets.get(Target.GLOBAL_OA_ROLE_USER), new IdentityID[]{ GLOBAL_OA_ROLE }, "grpA");
      stubUser(targets.get(Target.FLAGGED_ROLE_USER), new IdentityID[]{ FLAGGED_OA_ROLE }, "grpA");
      stubUser(targets.get(Target.OA_GROUP_USER), new IdentityID[0], "oaGroup");
      stubUser(plainUser, new IdentityID[0], "grpA");
   }

   @ParameterizedTest(name = "{1} delegate -> {0}")
   @MethodSource("cases")
   void delegatedGrantDoesNotReachOrgAdminUser(Target target, DelegatedGrant grant) {
      stubGrant(grant);
      IdentityID user = targets.get(target);

      for(ResourceAction action : ResourceAction.values()) {
         assertFalse(check(delegate(), ResourceType.SECURITY_USER, user.convertToKey(), action),
                     grant + " delegate (not an org admin) got " + action +
                     " over org admin user " + user.convertToKey() + " (" + target + ")");
      }
   }

   @ParameterizedTest(name = "{0} delegate -> plain user (control)")
   @EnumSource(DelegatedGrant.class)
   void delegatedGrantStillReachesPlainUser(DelegatedGrant grant) {
      stubGrant(grant);
      assertTrue(check(delegate(), ResourceType.SECURITY_USER, plainUser.convertToKey(), ResourceAction.ADMIN),
                 grant + " delegate must keep ADMIN over a plain user");
   }

   @ParameterizedTest(name = "org admin caller -> {0}")
   @EnumSource(Target.class)
   void orgAdminCallerKeepsPermissionOverOrgAdminIdentities(Target target) {
      SRPrincipal orgAdmin = new SRPrincipal(new IdentityID("otherBoss", ORG),
                                             new IdentityID[]{ FLAGGED_OA_ROLE }, new String[0],
                                             ORG, Tool.getSecureRandom().nextLong());
      assertTrue(check(orgAdmin, ResourceType.SECURITY_USER, targets.get(target).convertToKey(),
                       ResourceAction.ADMIN));
      assertTrue(check(orgAdmin, ResourceType.SECURITY_GROUP, OA_GROUP.convertToKey(),
                       ResourceAction.ADMIN));
      assertTrue(check(orgAdmin, ResourceType.SECURITY_ROLE, FLAGGED_OA_ROLE.convertToKey(),
                       ResourceAction.ADMIN));
   }

   /** The EM editRole gate (ADMIN on the role) stays closed on a role flagged org admin. */
   @Test
   void rolesRootDelegate_hasNoAdminOnOrgAdminFlaggedRole() {
      Permission admin = new Permission();
      admin.setUserGrantsForOrg(ResourceAction.ADMIN, Set.of(DELEGATE), ORG);
      when(mockProvider.getPermission(eq(ResourceType.SECURITY_ROLE),
                                      eq(new IdentityID("Organization Roles", ORG)), anyString()))
         .thenReturn(admin);
      assertFalse(check(delegate(), ResourceType.SECURITY_ROLE, FLAGGED_OA_ROLE.convertToKey(),
                        ResourceAction.ADMIN));
      // control: a plain org role stays administrable
      assertTrue(check(delegate(), ResourceType.SECURITY_ROLE,
                       new IdentityID("analysts", ORG).convertToKey(), ResourceAction.ADMIN));
   }

   /** An org node delegate is not an org admin, so it has no ADMIN on a flagged role. */
   @Test
   void orgNodeDelegate_hasNoAdminOnOrgAdminFlaggedRole() {
      stubGrant(DelegatedGrant.ORG_NODE);
      assertFalse(check(delegate(), ResourceType.SECURITY_ROLE, FLAGGED_OA_ROLE.convertToKey(),
                        ResourceAction.ADMIN));
      assertTrue(check(delegate(), ResourceType.SECURITY_ROLE,
                       new IdentityID("analysts", ORG).convertToKey(), ResourceAction.ADMIN));
   }

   /** The EM editGroup gate (ADMIN on the group) stays closed on a group holding an org admin role. */
   @ParameterizedTest(name = "{0} delegate -> org admin group")
   @EnumSource(value = DelegatedGrant.class, names = { "ORG_NODE", "GROUPS_ROOT" })
   void delegatedGrantDoesNotReachOrgAdminGroup(DelegatedGrant grant) {
      stubGrant(grant);
      assertFalse(check(delegate(), ResourceType.SECURITY_GROUP, OA_GROUP.convertToKey(),
                        ResourceAction.ADMIN));
      // control: a plain group stays administrable
      assertTrue(check(delegate(), ResourceType.SECURITY_GROUP, PLAIN_GROUP.convertToKey(),
                       ResourceAction.ADMIN));
   }

   /**
    * A Groups root delegate can't make its schedule task run as a group holding an org admin
    * role (SUtil builds the execute-as principal with the group's roles), but can still pick a
    * plain group.
    */
   @Test
   void groupsRootDelegate_executeAsOrgAdminGroup_isRefused() {
      stubGrant(DelegatedGrant.GROUPS_ROOT);
      lenient().when(mockProvider.getGroups()).thenReturn(new IdentityID[]{ OA_GROUP, PLAIN_GROUP });
      lenient().when(mockProvider.isVirtual()).thenReturn(false);
      lenient().when(mockProvider.checkPermission(any(Principal.class), any(ResourceType.class),
                                                  anyString(), any(ResourceAction.class)))
         .thenAnswer(inv -> strategy.checkPermission(inv.getArgument(0), inv.getArgument(1),
                                                     inv.getArgument(2), inv.getArgument(3)));
      ScheduleTaskIdentityChecker checker = new ScheduleTaskIdentityChecker(mockProvider);
      SRPrincipal caller = delegate();
      IdentityID owner = new IdentityID(DELEGATE, ORG);

      assertFalse(inMocks(() -> checker.isExecuteAsAllowed(OA_GROUP, Identity.GROUP, owner, caller)),
                  "Groups root delegate may not run a task as org admin group " + OA_GROUP);
      assertTrue(inMocks(() -> checker.isExecuteAsAllowed(PLAIN_GROUP, Identity.GROUP, owner, caller)),
                 "Groups root delegate keeps running a task as plain group " + PLAIN_GROUP);
   }

   private SRPrincipal delegate() {
      return new SRPrincipal(new IdentityID(DELEGATE, ORG),
                             new IdentityID[]{ new IdentityID(DELEGATE_ROLE, ORG) }, new String[0],
                             ORG, Tool.getSecureRandom().nextLong());
   }

   private boolean check(SRPrincipal caller, ResourceType type, String resource,
                         ResourceAction action)
   {
      return inMocks(() -> strategy.checkPermission(caller, type, resource, action));
   }

   private <T> T inMocks(Supplier<T> call) {
      try(MockedStatic<SUtil> sutilMock = Mockito.mockStatic(SUtil.class, Mockito.CALLS_REAL_METHODS);
          MockedStatic<OrganizationManager> omMock =
             Mockito.mockStatic(OrganizationManager.class, Mockito.CALLS_REAL_METHODS))
      {
         sutilMock.when(SUtil::isMultiTenant).thenReturn(true);
         sutilMock.when(() -> SUtil.isInternalUser(any())).thenReturn(false);

         OrganizationManager mockOM = mock(OrganizationManager.class);
         omMock.when(OrganizationManager::getInstance).thenReturn(mockOM);
         omMock.when(OrganizationManager::getCurrentOrgName).thenReturn(ORG);
         when(mockOM.getCurrentOrgID()).thenReturn(ORG);
         when(mockOM.getCurrentOrgID(any())).thenReturn(ORG);
         when(mockOM.isSiteAdmin(any(Principal.class))).thenReturn(false);
         when(mockOM.isOrgAdmin(any(Principal.class))).thenReturn(false);

         return call.get();
      }
   }

   private void stubRole(IdentityID id) {
      Role role = mock(Role.class);
      lenient().when(role.getIdentityID()).thenReturn(id);
      lenient().when(role.getName()).thenReturn(id.getName());
      lenient().when(role.getOrganizationID()).thenReturn(id.getOrgID());
      lenient().when(role.getRoles()).thenReturn(new IdentityID[0]);
      lenient().when(mockProvider.getRole(eq(id))).thenReturn(role);
   }

   private void stubUser(IdentityID id, IdentityID[] roles, String group) {
      User user = mock(User.class);
      lenient().when(user.getIdentityID()).thenReturn(id);
      lenient().when(user.getName()).thenReturn(id.getName());
      lenient().when(user.getOrganizationID()).thenReturn(id.getOrgID());
      lenient().when(user.getGroups()).thenReturn(new String[]{ group });
      lenient().when(user.getRoles()).thenReturn(roles);
      lenient().when(mockProvider.getUser(eq(id))).thenReturn(user);
      lenient().when(mockProvider.getUserGroups(eq(id))).thenReturn(new String[]{ group });
      lenient().when(mockProvider.getRoles(eq(id))).thenReturn(roles);
   }

   private void stubGroup(String name, IdentityID[] roles) {
      IdentityID id = new IdentityID(name, ORG);
      Group group = mock(Group.class);
      lenient().when(group.getIdentityID()).thenReturn(id);
      lenient().when(group.getName()).thenReturn(name);
      lenient().when(group.getOrganizationID()).thenReturn(ORG);
      lenient().when(group.getRoles()).thenReturn(roles);
      lenient().when(group.getGroups()).thenReturn(new String[0]);
      lenient().when(mockProvider.getGroup(eq(id))).thenReturn(group);
      lenient().when(mockProvider.getGroupParentGroups(eq(id))).thenReturn(new String[0]);
   }

   private void stubGrant(DelegatedGrant grant) {
      Permission admin = new Permission();
      admin.setUserGrantsForOrg(ResourceAction.ADMIN, Set.of(DELEGATE), ORG);

      switch(grant) {
      case ORG_NODE:
         when(mockProvider.getPermission(eq(ResourceType.SECURITY_ORGANIZATION),
                                         eq(new IdentityID(ORG, ORG))))
            .thenReturn(admin);
         break;
      case USERS_ROOT:
         when(mockProvider.getPermission(eq(ResourceType.SECURITY_USER),
                                         eq(new IdentityID("Users", ORG))))
            .thenReturn(admin);
         break;
      case GROUPS_ROOT:
         // the dedicated root check reads the IdentityID overload, the group traversal reads
         // the String key overload; both resolve to the same stored grant in production
         IdentityID groupsRoot = new IdentityID("Groups", ORG);
         String groupsRootKey = groupsRoot.convertToKey();
         when(mockProvider.getPermission(eq(ResourceType.SECURITY_GROUP), eq(groupsRoot)))
            .thenReturn(admin);
         when(mockProvider.getPermission(eq(ResourceType.SECURITY_GROUP),
                                         argThat((String k) -> groupsRootKey.equalsIgnoreCase(k)),
                                         anyString()))
            .thenReturn(admin);
         break;
      case ORG_SELF_GRANT:
         Permission roleAdmin = new Permission();
         roleAdmin.setRoleGrantsForOrg(ResourceAction.ADMIN, Set.of(DELEGATE_ROLE), ORG);
         when(mockProvider.getPermission(eq(ResourceType.SECURITY_ORGANIZATION),
                                         eq(new IdentityID(ORG, ORG)), eq(ORG)))
            .thenReturn(roleAdmin);
         break;
      case USER_WILDCARD:
         when(mockProvider.getPermission(eq(ResourceType.SECURITY_USER), eq(ORG)))
            .thenReturn(admin);
         break;
      case EXPLICIT_USER:
         // an explicit ADMIN grant on each target user, e.g. one a delegate planted through
         // the permitted identities of a user edit before this fix
         when(mockProvider.getPermission(eq(ResourceType.SECURITY_USER),
                                         argThat((String k) -> k != null && !k.startsWith("Users") &&
                                            k.contains(IdentityID.KEY_DELIMITER)),
                                         anyString()))
            .thenReturn(admin);
         break;
      }
   }
}
