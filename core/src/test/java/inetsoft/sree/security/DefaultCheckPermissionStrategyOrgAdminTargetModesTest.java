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
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77498, the org admin target exclusion of DefaultCheckPermissionStrategy outside the
 * delegate x target matrix of DefaultCheckPermissionStrategyOrgAdminTargetTest:
 * <ul>
 *    <li>single-tenant mode, where the built-in Organization Administrator role is inert (the
 *    same as OrganizationManager.isOrgAdmin()), so a user holding it stays administrable by a
 *    delegate, while a user holding an org role flagged org admin stays protected;</li>
 *    <li>a target that only inherits a flagged role (role inheritance through getAllRoles());</li>
 *    <li>a site admin keeps permission over every org admin user, group and role.</li>
 * </ul>
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@SreeHome()
@Tag("core")
class DefaultCheckPermissionStrategyOrgAdminTargetModesTest {
   static final String ORG = "testOrg";
   static final String DELEGATE = "testUser";
   static final IdentityID GLOBAL_OA_ROLE = new IdentityID("Organization Administrator", null);
   static final IdentityID FLAGGED_OA_ROLE = new IdentityID("orgBoss", ORG);
   static final IdentityID DEPUTY_ROLE = new IdentityID("deputy", ORG);
   static final IdentityID OA_GROUP = new IdentityID("oaGroup", ORG);
   static final IdentityID GLOBAL_OA_USER = new IdentityID("oaUser", ORG);
   static final IdentityID FLAGGED_USER = new IdentityID("bossUser", ORG);
   static final IdentityID DEPUTY_USER = new IdentityID("deputyUser", ORG);

   SecurityProvider mockProvider;
   DefaultCheckPermissionStrategy strategy;

   @BeforeEach
   void init() {
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
      // deputy inherits the flagged org admin role
      lenient().when(mockProvider.getAllRoles(any(IdentityID[].class))).thenAnswer(inv -> {
         IdentityID[] roles = inv.getArgument(0);
         return Arrays.stream(roles)
            .flatMap(r -> DEPUTY_ROLE.equals(r) ? Stream.of(r, FLAGGED_OA_ROLE) : Stream.of(r))
            .distinct().toArray(IdentityID[]::new);
      });
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
      stubRole(DEPUTY_ROLE);
      stubGroup(OA_GROUP.name, new IdentityID[]{ FLAGGED_OA_ROLE });
      stubGroup("grpA", new IdentityID[0]);
      stubUser(GLOBAL_OA_USER, new IdentityID[]{ GLOBAL_OA_ROLE });
      stubUser(FLAGGED_USER, new IdentityID[]{ FLAGGED_OA_ROLE });
      stubUser(DEPUTY_USER, new IdentityID[]{ DEPUTY_ROLE });

      Permission admin = new Permission();
      admin.setUserGrantsForOrg(ResourceAction.ADMIN, Set.of(DELEGATE), ORG);
      lenient().when(mockProvider.getPermission(eq(ResourceType.SECURITY_USER),
                                                eq(new IdentityID("Users", ORG))))
         .thenReturn(admin);
      lenient().when(mockProvider.getPermission(eq(ResourceType.SECURITY_ROLE),
                                                eq(new IdentityID("Organization Roles", ORG)),
                                                anyString()))
         .thenReturn(admin);
   }

   /**
    * Single-tenant mode: the built-in Organization Administrator role makes no one an org admin,
    * so a Users root delegate keeps administering a user holding it (unchanged behaviour), but a
    * user holding an org role flagged org admin stays out of reach.
    */
   @Test
   void singleTenant_globalOaRoleIsInert_flaggedRoleStillProtected() {
      assertTrue(check(delegate(), false, ResourceType.SECURITY_USER, GLOBAL_OA_USER, false),
                 "single-tenant: a user holding the inert Organization Administrator role " +
                 "stays administrable by a Users root delegate");
      assertFalse(check(delegate(), false, ResourceType.SECURITY_USER, FLAGGED_USER, false),
                  "single-tenant: a user holding a flagged org admin role is out of reach");

      // the same caller in multi-tenant mode loses both
      assertFalse(check(delegate(), true, ResourceType.SECURITY_USER, GLOBAL_OA_USER, false));
      assertFalse(check(delegate(), true, ResourceType.SECURITY_USER, FLAGGED_USER, false));
   }

   /** A user or role that only inherits a flagged role grants org admin as well. */
   @Test
   void inheritedOrgAdminRole_isProtected() {
      assertFalse(check(delegate(), true, ResourceType.SECURITY_USER, DEPUTY_USER, false),
                  "a user whose role inherits a flagged org admin role is out of reach");
      assertFalse(check(delegate(), true, ResourceType.SECURITY_ROLE, DEPUTY_ROLE, false),
                  "a role inheriting a flagged org admin role is out of reach");
   }

   /** A site admin keeps permission over every org admin user, group and role. */
   @Test
   void siteAdmin_keepsPermissionOverOrgAdminIdentities() {
      // site admin by OrganizationManager.isSiteAdmin() (stubbed), not by a sysadmin role; the
      // principal needs a role or group, or SRPrincipal.createUser() gives no identity
      SRPrincipal siteAdmin = new SRPrincipal(new IdentityID("site", ORG),
                                              new IdentityID[]{ new IdentityID("siteRole", ORG) },
                                              new String[0], ORG, Tool.getSecureRandom().nextLong());

      for(IdentityID user : new IdentityID[]{ GLOBAL_OA_USER, FLAGGED_USER, DEPUTY_USER }) {
         assertTrue(check(siteAdmin, true, ResourceType.SECURITY_USER, user, true), user.toString());
      }

      assertTrue(check(siteAdmin, true, ResourceType.SECURITY_GROUP, OA_GROUP, true));
      assertTrue(check(siteAdmin, true, ResourceType.SECURITY_ROLE, FLAGGED_OA_ROLE, true));
      assertTrue(check(siteAdmin, true, ResourceType.SECURITY_ROLE, DEPUTY_ROLE, true));
   }

   private SRPrincipal delegate() {
      return new SRPrincipal(new IdentityID(DELEGATE, ORG),
                             new IdentityID[]{ new IdentityID("testRole", ORG) }, new String[0],
                             ORG, Tool.getSecureRandom().nextLong());
   }

   private boolean check(SRPrincipal caller, boolean multiTenant, ResourceType type,
                         IdentityID resource, boolean siteAdmin)
   {
      try(MockedStatic<SUtil> sutilMock = Mockito.mockStatic(SUtil.class, Mockito.CALLS_REAL_METHODS);
          MockedStatic<OrganizationManager> omMock =
             Mockito.mockStatic(OrganizationManager.class, Mockito.CALLS_REAL_METHODS))
      {
         sutilMock.when(SUtil::isMultiTenant).thenReturn(multiTenant);
         sutilMock.when(() -> SUtil.isInternalUser(any())).thenReturn(false);

         OrganizationManager mockOM = mock(OrganizationManager.class);
         omMock.when(OrganizationManager::getInstance).thenReturn(mockOM);
         omMock.when(OrganizationManager::getCurrentOrgName).thenReturn(ORG);
         when(mockOM.getCurrentOrgID()).thenReturn(ORG);
         when(mockOM.getCurrentOrgID(any())).thenReturn(ORG);
         when(mockOM.isSiteAdmin(any(Principal.class))).thenReturn(siteAdmin);
         when(mockOM.isOrgAdmin(any(Principal.class))).thenReturn(false);

         return strategy.checkPermission(caller, type, resource.convertToKey(), ResourceAction.ADMIN);
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

   private void stubUser(IdentityID id, IdentityID[] roles) {
      User user = mock(User.class);
      lenient().when(user.getIdentityID()).thenReturn(id);
      lenient().when(user.getName()).thenReturn(id.getName());
      lenient().when(user.getOrganizationID()).thenReturn(id.getOrgID());
      lenient().when(user.getGroups()).thenReturn(new String[]{ "grpA" });
      lenient().when(user.getRoles()).thenReturn(roles);
      lenient().when(mockProvider.getUser(eq(id))).thenReturn(user);
      lenient().when(mockProvider.getUserGroups(eq(id))).thenReturn(new String[]{ "grpA" });
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
}
