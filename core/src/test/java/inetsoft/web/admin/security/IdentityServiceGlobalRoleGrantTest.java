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
 * Bug #77275: only a site admin may change the org-less (global) role grants of an identity
 * permission. A non site admin's save through setIdentityPermissions must keep them unchanged,
 * even if the request body carries crafted global role rows, while its org-scoped grants are
 * still saved.
 */
@Tag("core")
class IdentityServiceGlobalRoleGrantTest {
   private static final String ORG = "orga";
   private static final IdentityID BOB = new IdentityID("bob", ORG);
   private static final IdentityID DESIGNER = new IdentityID("Designer", ORG);

   private IdentityService service;
   private SecurityProvider securityProvider;
   private AuthorizationProvider authz;
   private OrganizationManager orgManager;
   private MockedStatic<OrganizationManager> orgManagerStatic;
   private final Principal principal = mock(Principal.class);

   @BeforeEach
   void setUp() {
      authz = mock(AuthorizationProvider.class);
      securityProvider = mock(SecurityProvider.class);
      when(securityProvider.getAuthorizationProvider()).thenReturn(authz);

      service = mock(IdentityService.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));
      ReflectionTestUtils.setField(service, "securityProvider", securityProvider);

      orgManager = mock(OrganizationManager.class);
      when(orgManager.getCurrentOrgID()).thenReturn(ORG);
      orgManagerStatic = mockStatic(OrganizationManager.class);
      orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);
   }

   @AfterEach
   void tearDown() {
      orgManagerStatic.close();
   }

   // org admin, crafted body adds G2@null and Organization Administrator@null -> neither added,
   // the site admin's R1@null kept, the org-scoped row saved
   @Test
   void orgAdminCannotAddGlobalRoleGrants() {
      Permission permission = storedPermission(ResourceType.SECURITY_USER, BOB, ResourceAction.ADMIN);
      orgAdmin();

      Permission saved = save(ResourceType.SECURITY_USER, BOB,
                              role("Analyst", ORG), role("G2", null),
                              role("Organization Administrator", null));

      assertSame(permission, saved);
      assertEquals(Set.of("Analyst@orga", "R1@null"), roleGrants(saved, ResourceAction.ADMIN));
   }

   // org admin that was explicitly given ADMIN on R1 (so the re-add loop does not put it back)
   // omits R1@null from the body -> still kept, only a site admin may remove it
   @Test
   void orgAdminCannotRemoveGlobalRoleGrant() {
      storedPermission(ResourceType.SECURITY_USER, BOB, ResourceAction.ADMIN);
      orgAdmin();
      when(securityProvider.checkAnyPermission(any(), any(), anyString(), any())).thenReturn(true);

      Permission saved = save(ResourceType.SECURITY_USER, BOB, role("Analyst", ORG));

      assertEquals(Set.of("Analyst@orga", "R1@null"), roleGrants(saved, ResourceAction.ADMIN));
   }

   // org admin normal round-trip (global rows hidden by getPermission): org-scoped user, group
   // and role grants are saved, the global grant is kept
   @Test
   void orgAdminOrgScopedGrantsStillSaved() {
      storedPermission(ResourceType.SECURITY_USER, BOB, ResourceAction.ADMIN);
      orgAdmin();

      Permission saved = save(ResourceType.SECURITY_USER, BOB,
                              role("Analyst", ORG), role("Viewer", ORG),
                              identity("alice", ORG, Identity.USER),
                              identity("sales", ORG, Identity.GROUP));

      assertEquals(Set.of("Analyst@orga", "Viewer@orga", "R1@null"),
                   roleGrants(saved, ResourceAction.ADMIN));
      assertEquals(Set.of("alice@orga"), names(saved.getAllUserGrants(ResourceAction.ADMIN)));
      assertEquals(Set.of("sales@orga"), names(saved.getAllGroupGrants(ResourceAction.ADMIN)));
   }

   // the ASSIGN grant of an individual role is gated the same way
   @Test
   void orgAdminCannotAddGlobalRoleAssignGrant() {
      storedPermission(ResourceType.SECURITY_ROLE, DESIGNER, ResourceAction.ASSIGN);
      orgAdmin();

      Permission saved = save(ResourceType.SECURITY_ROLE, DESIGNER,
                              role("Analyst", ORG), role("G2", null));

      assertEquals(Set.of("Analyst@orga", "R1@null"), roleGrants(saved, ResourceAction.ASSIGN));
   }

   // no stored permission yet: an org admin's crafted global rows are not written
   @Test
   void orgAdminNewPermissionGetsNoGlobalRoleGrants() {
      orgAdmin();

      Permission saved = save(ResourceType.SECURITY_USER, BOB, role("Analyst", ORG), role("G2", null));

      assertEquals(Set.of("Analyst@orga"), roleGrants(saved, ResourceAction.ADMIN));
   }

   // site admin can replace the global grants
   @Test
   void siteAdminCanReplaceGlobalRoleGrants() {
      storedPermission(ResourceType.SECURITY_USER, BOB, ResourceAction.ADMIN);
      siteAdmin();

      Permission saved = save(ResourceType.SECURITY_USER, BOB, role("Analyst", ORG), role("G2", null));

      assertEquals(Set.of("Analyst@orga", "G2@null"), roleGrants(saved, ResourceAction.ADMIN));
   }

   // site admin can clear the global grants
   @Test
   void siteAdminCanClearGlobalRoleGrants() {
      storedPermission(ResourceType.SECURITY_USER, BOB, ResourceAction.ADMIN);
      siteAdmin();

      Permission saved = save(ResourceType.SECURITY_USER, BOB, role("Analyst", ORG));

      assertEquals(Set.of("Analyst@orga"), roleGrants(saved, ResourceAction.ADMIN));
   }

   private void orgAdmin() {
      when(orgManager.isSiteAdmin(any(Principal.class))).thenReturn(false);
      // an org admin administers the roles of its own org, not the global ones
      when(securityProvider.checkAnyPermission(any(), any(), anyString(), any()))
         .thenAnswer(inv -> inv.<String>getArgument(2).endsWith(IdentityID.KEY_DELIMITER + ORG));
   }

   private void siteAdmin() {
      when(orgManager.isSiteAdmin(any(Principal.class))).thenReturn(true);
      when(securityProvider.checkAnyPermission(any(), any(), anyString(), any())).thenReturn(true);
   }

   private Permission storedPermission(ResourceType type, IdentityID id, ResourceAction action) {
      Permission permission = new Permission();
      Set<Permission.PermissionIdentity> grants = new HashSet<>();
      grants.add(new Permission.PermissionIdentity("Everyone", ORG));
      grants.add(new Permission.PermissionIdentity("R1", null));
      permission.setRoleGrants(action, grants);
      when(authz.getPermission(type, id)).thenReturn(permission);
      return permission;
   }

   private Permission save(ResourceType type, IdentityID id, IdentityModel... rows) {
      service.setIdentityPermissions(id, id, type, principal, List.of(rows), ORG);
      ArgumentCaptor<Permission> captor = ArgumentCaptor.forClass(Permission.class);
      verify(authz).setPermission(eq(type), eq(id), captor.capture());
      return captor.getValue();
   }

   private static IdentityModel role(String name, String org) {
      return identity(name, org, Identity.ROLE);
   }

   private static IdentityModel identity(String name, String org, int type) {
      return IdentityModel.builder().identityID(new IdentityID(name, org)).type(type).build();
   }

   private static Set<String> roleGrants(Permission permission, ResourceAction action) {
      return names(permission.getAllRoleGrants(action));
   }

   private static Set<String> names(Set<Permission.PermissionIdentity> grants) {
      return grants.stream()
         .map(g -> g.getName() + "@" + g.getOrganizationID())
         .collect(Collectors.toSet());
   }
}
