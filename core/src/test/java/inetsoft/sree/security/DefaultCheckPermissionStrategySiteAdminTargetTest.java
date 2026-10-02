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
import inetsoft.util.Tool;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77347, a user that is a system administrator (holds a system administrator role directly
 * or through a group) is out of reach of every grant a non-system-admin caller can hold: the
 * org-wide delegated grants (org node, Users root, Groups root, org self grant, user wildcard)
 * and explicit per-user grants. Only system/site admin callers get permission over such a user.
 * Ordinary users stay covered by the delegated grants.
 *
 * Runs multi-tenant with an org-scoped sysadmin-flagged role and single-tenant with the
 * built-in, org-less Administrator role.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome()
@Tag("core")
class DefaultCheckPermissionStrategySiteAdminTargetTest {
   static final String DELEGATE = "testUser";
   static final String DELEGATE_ROLE = "testRole";

   enum Mode {
      MULTI_TENANT("testOrg", true, new IdentityID("sysRole", "testOrg")),
      SINGLE_TENANT(Organization.getDefaultOrganizationID(), false,
                    new IdentityID("Administrator", null));

      Mode(String org, boolean multiTenant, IdentityID sysRole) {
         this.org = org;
         this.multiTenant = multiTenant;
         this.sysRole = sysRole;
      }

      final String org;
      final boolean multiTenant;
      final IdentityID sysRole;
   }

   /** The delegated grants a non-org-admin, non-site-admin user of the org can hold. */
   enum DelegatedGrant {
      ORG_NODE,       // ADMIN on the org's SECURITY_ORGANIZATION node
      USERS_ROOT,     // ADMIN on the "Users" root of the org
      GROUPS_ROOT,    // ADMIN on the "Groups" root of the org (target user is in a group)
      ORG_SELF_GRANT, // role ADMIN on the org self grant, reached through the fallback
      USER_WILDCARD,  // the SECURITY_USER wildcard keyed by org id
      EXPLICIT_USER   // explicit ADMIN grant on the target user itself
   }

   static Stream<Arguments> cases() {
      return Arrays.stream(Mode.values())
         .flatMap(m -> Arrays.stream(DelegatedGrant.values()).map(g -> Arguments.of(m, g)));
   }

   SecurityProvider mockProvider;
   DefaultCheckPermissionStrategy strategy;
   Mode mode;

   // a user holding a system administrator role directly, member of the plain group grpA
   IdentityID siteAdmin;
   // a user with no role of its own, member of "admins", a group that holds the sysadmin role
   IdentityID groupSiteAdmin;
   // control: an ordinary user, member of grpA
   IdentityID plainUser;

   void init(Mode mode) {
      this.mode = mode;
      String org = mode.org;
      siteAdmin = new IdentityID("rootAdmin", org);
      groupSiteAdmin = new IdentityID("groupAdmin", org);
      plainUser = new IdentityID("bobA", org);

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
      lenient().when(mockProvider.getAllRoles(any(IdentityID[].class))).thenAnswer(inv -> inv.getArgument(0));
      lenient().when(mockProvider.getOrgNameFromID(anyString())).thenReturn(org);
      lenient().when(mockProvider.getPermission(any(ResourceType.class), anyString(), anyString())).thenReturn(null);
      lenient().when(mockProvider.getPermission(any(ResourceType.class), anyString())).thenReturn(null);
      lenient().when(mockProvider.getPermission(any(ResourceType.class), any(IdentityID.class))).thenReturn(null);
      AuthenticationProvider mockAuthProvider = Mockito.mock(AuthenticationProvider.class);
      lenient().when(mockProvider.getAuthenticationProvider()).thenReturn(mockAuthProvider);
      Organization mockOrg = Mockito.mock(Organization.class);
      lenient().when(mockOrg.getOrganizationID()).thenReturn(org);
      lenient().when(mockOrg.getRoles()).thenReturn(new IdentityID[0]);
      lenient().when(mockProvider.getOrganization(anyString())).thenReturn(mockOrg);

      lenient().when(mockProvider.isSystemAdministratorRole(eq(mode.sysRole))).thenReturn(true);
      stubGroup("admins", new IdentityID[]{ mode.sysRole });
      stubGroup("grpA", new IdentityID[0]);
      stubUser(siteAdmin, new IdentityID[]{ mode.sysRole }, "grpA");
      // getRoles() doesn't fold in group roles, so group-derived sysadmin must be computed
      stubUser(groupSiteAdmin, new IdentityID[0], "admins");
      stubUser(plainUser, new IdentityID[0], "grpA");
   }

   @ParameterizedTest(name = "{0}: {1} -> site admin user")
   @MethodSource("cases")
   void delegatedGrantDoesNotReachSiteAdminUser(Mode mode, DelegatedGrant grant) {
      init(mode);
      stubGrant(grant);

      for(ResourceAction action : ResourceAction.values()) {
         assertFalse(check(delegate(), siteAdmin, action),
                     grant + " delegate got " + action + " over site admin user " +
                     siteAdmin.convertToKey());
      }
   }

   @ParameterizedTest(name = "{0}: {1} -> user in admin-granting group")
   @MethodSource("cases")
   void delegatedGrantDoesNotReachUserInAdminGrantingGroup(Mode mode, DelegatedGrant grant) {
      init(mode);
      stubGrant(grant);

      for(ResourceAction action : ResourceAction.values()) {
         assertFalse(check(delegate(), groupSiteAdmin, action),
                     grant + " delegate got " + action + " over user " +
                     groupSiteAdmin.convertToKey() + ", a system administrator through a group");
      }
   }

   @ParameterizedTest(name = "{0}: {1} -> plain user (control)")
   @MethodSource("cases")
   void delegatedGrantStillReachesPlainUser(Mode mode, DelegatedGrant grant) {
      init(mode);
      stubGrant(grant);
      assertTrue(check(delegate(), plainUser, ResourceAction.ADMIN),
                 grant + " delegate must keep ADMIN over a plain user");
   }

   @ParameterizedTest(name = "{0}: system admin caller -> site admin users")
   @EnumSource(Mode.class)
   void systemAdminCallerKeepsPermissionOverSiteAdmins(Mode mode) {
      init(mode);
      SRPrincipal sysAdmin = principal("otherAdmin", new IdentityID[]{ mode.sysRole }, new String[0]);

      assertTrue(check(sysAdmin, siteAdmin, ResourceAction.ADMIN));
      assertTrue(check(sysAdmin, groupSiteAdmin, ResourceAction.ADMIN));
      assertTrue(check(sysAdmin, plainUser, ResourceAction.ADMIN));
   }

   @ParameterizedTest(name = "{0}: site admin through group -> itself and other site admins")
   @EnumSource(Mode.class)
   void groupDerivedSiteAdminCallerKeepsPermissionOverSiteAdmins(Mode mode) {
      init(mode);
      SRPrincipal caller = principal(groupSiteAdmin.getName(), new IdentityID[0],
                                     new String[]{ "admins" });

      assertTrue(check(caller, groupSiteAdmin, ResourceAction.ADMIN));
      assertTrue(check(caller, siteAdmin, ResourceAction.ADMIN));
   }

   private SRPrincipal delegate() {
      return principal(DELEGATE, new IdentityID[]{ new IdentityID(DELEGATE_ROLE, mode.org) },
                       new String[0]);
   }

   private SRPrincipal principal(String name, IdentityID[] roles, String[] groups) {
      return new SRPrincipal(new IdentityID(name, mode.org), roles, groups, mode.org,
                             Tool.getSecureRandom().nextLong());
   }

   private boolean check(SRPrincipal caller, IdentityID target, ResourceAction action) {
      try(MockedStatic<SUtil> sutilMock = Mockito.mockStatic(SUtil.class, Mockito.CALLS_REAL_METHODS);
          MockedStatic<OrganizationManager> omMock =
             Mockito.mockStatic(OrganizationManager.class, Mockito.CALLS_REAL_METHODS))
      {
         sutilMock.when(SUtil::isMultiTenant).thenReturn(mode.multiTenant);
         sutilMock.when(() -> SUtil.isInternalUser(any())).thenReturn(false);

         OrganizationManager mockOM = mock(OrganizationManager.class);
         omMock.when(OrganizationManager::getInstance).thenReturn(mockOM);
         omMock.when(OrganizationManager::getCurrentOrgName).thenReturn(mode.org);
         when(mockOM.getCurrentOrgID()).thenReturn(mode.org);
         when(mockOM.getCurrentOrgID(any())).thenReturn(mode.org);
         // no caller is recognized as site admin through isSiteAdmin(Principal), only through
         // its roles; the targets are site admins only through provider data
         when(mockOM.isSiteAdmin(any(Principal.class))).thenReturn(false);

         return strategy.checkPermission(caller, ResourceType.SECURITY_USER,
                                         target.convertToKey(), action);
      }
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
      IdentityID id = new IdentityID(name, mode.org);
      Group group = mock(Group.class);
      lenient().when(group.getIdentityID()).thenReturn(id);
      lenient().when(group.getName()).thenReturn(name);
      lenient().when(group.getOrganizationID()).thenReturn(mode.org);
      lenient().when(group.getRoles()).thenReturn(roles);
      lenient().when(group.getGroups()).thenReturn(new String[0]);
      lenient().when(mockProvider.getGroup(eq(id))).thenReturn(group);
      lenient().when(mockProvider.getGroupParentGroups(eq(id))).thenReturn(new String[0]);
   }

   private void stubGrant(DelegatedGrant grant) {
      String org = mode.org;
      Permission admin = new Permission();
      admin.setUserGrantsForOrg(ResourceAction.ADMIN, Set.of(DELEGATE), org);

      switch(grant) {
      case ORG_NODE:
         when(mockProvider.getPermission(eq(ResourceType.SECURITY_ORGANIZATION),
                                         eq(new IdentityID(org, org))))
            .thenReturn(admin);
         break;
      case USERS_ROOT:
         when(mockProvider.getPermission(eq(ResourceType.SECURITY_USER),
                                         eq(new IdentityID("Users", org))))
            .thenReturn(admin);
         break;
      case GROUPS_ROOT:
         // the dedicated root check reads the IdentityID overload, the group traversal reads
         // the String key overload; both resolve to the same stored grant in production
         IdentityID groupsRoot = new IdentityID("Groups", org);
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
         roleAdmin.setRoleGrantsForOrg(ResourceAction.ADMIN, Set.of(DELEGATE_ROLE), org);
         when(mockProvider.getPermission(eq(ResourceType.SECURITY_ORGANIZATION),
                                         eq(new IdentityID(org, org)), eq(org)))
            .thenReturn(roleAdmin);
         break;
      case USER_WILDCARD:
         when(mockProvider.getPermission(eq(ResourceType.SECURITY_USER), eq(org)))
            .thenReturn(admin);
         break;
      case EXPLICIT_USER:
         // an explicit ADMIN grant on each target user, like the one written for the creator
         // of a user (IdentityService.createIdentityPermissions)
         when(mockProvider.getPermission(eq(ResourceType.SECURITY_USER),
                                         argThat((String k) -> k != null && !k.startsWith("Users") &&
                                            k.contains(IdentityID.KEY_DELIMITER)),
                                         anyString()))
            .thenReturn(admin);
         break;
      }
   }
}
