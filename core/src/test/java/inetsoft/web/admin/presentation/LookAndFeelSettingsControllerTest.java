/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.web.admin.presentation;

/*
 * Test strategy
 *
 * LookAndFeelSettingsController.getIdentities() queries SecurityProvider for all
 * users, groups, and roles, then filters each list to those for which the
 * calling principal has ResourceAction.ADMIN permission.  The resource type
 * used for the permission check differs per collection:
 *
 *   users  → ResourceType.SECURITY_USER   + ADMIN
 *   groups → ResourceType.SECURITY_GROUP  + ADMIN
 *   roles  → ResourceType.SECURITY_ROLE   + ADMIN
 *
 * Behavioral guarantees covered:
 *
 * [G1] Users for which checkPermission returns true are included; others are excluded.
 * [G2] Groups for which checkPermission returns true are included; others are excluded.
 * [G3] Roles are filtered against ResourceType.SECURITY_ROLE, keyed by the role's
 *      IdentityID.convertToKey(); an ADMIN grant on a same-named user (SECURITY_USER)
 *      does not list the role (Bug #78484).
 */

import inetsoft.sree.security.*;
import inetsoft.web.admin.presentation.model.GetAllIdentitiesResponse;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("core")
@ExtendWith(MockitoExtension.class)
class LookAndFeelSettingsControllerTest {

   @Mock private SecurityProvider securityProvider;
   @Mock private Principal principal;

   private LookAndFeelSettingsController<?, ?> controller;

   private final IdentityID permittedUser  = new IdentityID("alice", "org1");
   private final IdentityID deniedUser     = new IdentityID("bob",   "org1");
   private final IdentityID permittedGroup = new IdentityID("admins", "org1");
   private final IdentityID deniedGroup    = new IdentityID("guests", "org1");
   private final IdentityID permittedRole  = new IdentityID("Manager", "org1");
   private final IdentityID deniedRole     = new IdentityID("Viewer",  "org1");

   @BeforeEach
   void setUp() {
      controller = new LookAndFeelSettingsController<>(securityProvider);

      // users
      lenient().when(securityProvider.getUsers())
         .thenReturn(new IdentityID[]{ permittedUser, deniedUser });
      lenient().when(securityProvider.checkPermission(
            principal, ResourceType.SECURITY_USER,
            permittedUser.convertToKey(), ResourceAction.ADMIN))
         .thenReturn(true);
      lenient().when(securityProvider.checkPermission(
            principal, ResourceType.SECURITY_USER,
            deniedUser.convertToKey(), ResourceAction.ADMIN))
         .thenReturn(false);

      // groups
      lenient().when(securityProvider.getGroups())
         .thenReturn(new IdentityID[]{ permittedGroup, deniedGroup });
      lenient().when(securityProvider.checkPermission(
            principal, ResourceType.SECURITY_GROUP,
            permittedGroup.convertToKey(), ResourceAction.ADMIN))
         .thenReturn(true);
      lenient().when(securityProvider.checkPermission(
            principal, ResourceType.SECURITY_GROUP,
            deniedGroup.convertToKey(), ResourceAction.ADMIN))
         .thenReturn(false);

      // roles: ADMIN is granted on "Manager" as a role, and on "Viewer" only as a user of
      // the same name, which must not list the role
      lenient().when(securityProvider.getRoles())
         .thenReturn(new IdentityID[]{ permittedRole, deniedRole });
      lenient().when(securityProvider.checkPermission(
            principal, ResourceType.SECURITY_ROLE,
            permittedRole.convertToKey(), ResourceAction.ADMIN))
         .thenReturn(true);
      lenient().when(securityProvider.checkPermission(
            principal, ResourceType.SECURITY_ROLE,
            deniedRole.convertToKey(), ResourceAction.ADMIN))
         .thenReturn(false);
      lenient().when(securityProvider.checkPermission(
            principal, ResourceType.SECURITY_USER,
            deniedRole.convertToKey(), ResourceAction.ADMIN))
         .thenReturn(true);
   }

   // [G1] only users with ADMIN permission on SECURITY_USER are included
   @Test
   void getIdentities_usersFilteredByAdminPermission() {
      GetAllIdentitiesResponse response = controller.getIdentities(principal);

      assertTrue(response.users().contains(permittedUser));
      assertFalse(response.users().contains(deniedUser));
   }

   // [G2] only groups with ADMIN permission on SECURITY_GROUP are included
   @Test
   void getIdentities_groupsFilteredByAdminPermissionOnSecurityGroup() {
      GetAllIdentitiesResponse response = controller.getIdentities(principal);

      assertTrue(response.groups().contains(permittedGroup));
      assertFalse(response.groups().contains(deniedGroup));
   }

   // [G3] roles are filtered using ResourceType.SECURITY_ROLE, not SECURITY_USER (Bug #78484)
   @Test
   void getIdentities_rolesPermissionCheckedWithSecurityRoleType() {
      GetAllIdentitiesResponse response = controller.getIdentities(principal);

      assertEquals(List.of(permittedRole), response.roles());

      verify(securityProvider).checkPermission(
         principal, ResourceType.SECURITY_ROLE,
         permittedRole.convertToKey(), ResourceAction.ADMIN);
      verify(securityProvider, never()).checkPermission(
         principal, ResourceType.SECURITY_USER,
         permittedRole.convertToKey(), ResourceAction.ADMIN);
      verify(securityProvider, never()).checkPermission(
         principal, ResourceType.SECURITY_USER,
         deniedRole.convertToKey(), ResourceAction.ADMIN);
   }
}
