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

import inetsoft.sree.security.*;
import inetsoft.uql.util.Identity;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.Principal;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77868: getPermission(IdentityID, ...) must read the action setIdentityPermissions writes,
 * ASSIGN for a role other than the Roles / Organization Roles roots and ADMIN otherwise.
 */
@Tag("core")
class IdentityServiceRoleGrantReadTest {
   private static final String ORG = "orga";
   private static final IdentityID DESIGNER = new IdentityID("Designer", ORG);

   private IdentityService service;
   private AuthorizationProvider authz;
   private MockedStatic<OrganizationManager> orgManagerStatic;
   private final Principal principal = mock(Principal.class);

   @BeforeEach
   void setUp() {
      authz = mock(AuthorizationProvider.class);
      SecurityProvider securityProvider = mock(SecurityProvider.class);
      when(securityProvider.getAuthorizationProvider()).thenReturn(authz);
      when(securityProvider.checkAnyPermission(any(), any(), anyString(), any())).thenReturn(true);

      service = mock(IdentityService.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));
      ReflectionTestUtils.setField(service, "securityProvider", securityProvider);

      OrganizationManager orgManager = mock(OrganizationManager.class);
      when(orgManager.getCurrentOrgID()).thenReturn(ORG);
      when(orgManager.isSiteAdmin(any(Principal.class))).thenReturn(true);
      orgManagerStatic = mockStatic(OrganizationManager.class);
      orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);
   }

   @AfterEach
   void tearDown() {
      orgManagerStatic.close();
   }

   // a role's grant is ASSIGN, the creator's ADMIN self grant is not returned
   @Test
   void nonRootRoleReadsAssign() {
      stored(ResourceType.SECURITY_ROLE, DESIGNER, "alice", "creator");

      assertEquals(Set.of("alice@orga"), read(ResourceType.SECURITY_ROLE, DESIGNER));
   }

   // the role roots keep ADMIN
   @ParameterizedTest
   @ValueSource(strings = { "Roles", "Organization Roles" })
   void roleRootReadsAdmin(String root) {
      IdentityID rootID = new IdentityID(root, ORG);
      stored(ResourceType.SECURITY_ROLE, rootID, "alice", "creator");

      assertEquals(Set.of("creator@orga"), read(ResourceType.SECURITY_ROLE, rootID));
   }

   // other identity types keep ADMIN
   @Test
   void userGroupAndOrganizationReadAdmin() {
      IdentityID bob = new IdentityID("bob", ORG);
      IdentityID sales = new IdentityID("sales", ORG);
      IdentityID org = new IdentityID(ORG, ORG);
      stored(ResourceType.SECURITY_USER, bob, "alice", "creator");
      stored(ResourceType.SECURITY_GROUP, sales, "alice", "creator");
      stored(ResourceType.SECURITY_ORGANIZATION, org, "alice", "creator");

      assertEquals(Set.of("creator@orga"), read(ResourceType.SECURITY_USER, bob));
      assertEquals(Set.of("creator@orga"), read(ResourceType.SECURITY_GROUP, sales));
      assertEquals(Set.of("creator@orga"), read(ResourceType.SECURITY_ORGANIZATION, org));
   }

   // what setIdentityPermissions writes is what getPermission reads back
   @ParameterizedTest
   @ValueSource(strings = { "Designer", "Roles", "Organization Roles" })
   void roleGrantRoundTrip(String name) {
      roundTrip(ResourceType.SECURITY_ROLE, new IdentityID(name, ORG));
   }

   @Test
   void groupGrantRoundTrip() {
      roundTrip(ResourceType.SECURITY_GROUP, new IdentityID("sales", ORG));
   }

   private void roundTrip(ResourceType type, IdentityID id) {
      IdentityModel alice = IdentityModel.builder()
         .identityID(new IdentityID("alice", ORG)).type(Identity.USER).build();
      service.setIdentityPermissions(id, id, type, principal, List.of(alice), ORG);
      ArgumentCaptor<Permission> captor = ArgumentCaptor.forClass(Permission.class);
      verify(authz).setPermission(eq(type), eq(id), captor.capture());
      when(authz.getPermission(type, id, ORG)).thenReturn(captor.getValue());

      assertEquals(Set.of("alice@orga"), read(type, id));
   }

   private void stored(ResourceType type, IdentityID id, String assignUser, String adminUser) {
      Permission permission = new Permission();
      permission.setUserGrantsForOrg(ResourceAction.ASSIGN, Set.of(assignUser), ORG);
      permission.setUserGrantsForOrg(ResourceAction.ADMIN, Set.of(adminUser), ORG);
      when(authz.getPermission(type, id, ORG)).thenReturn(permission);
   }

   private Set<String> read(ResourceType type, IdentityID id) {
      return service.getPermission(id, type, ORG, principal).stream()
         .map(m -> m.identityID().name + "@" + m.identityID().orgID)
         .collect(Collectors.toSet());
   }
}
