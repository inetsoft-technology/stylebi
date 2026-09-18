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
package inetsoft.web.admin.ai.permissions;

import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.Permission;
import inetsoft.sree.security.ResourceAction;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@code Permission} has no {@code equals()} and its internal grant sets are unordered
 * (spec section 5) -- these tests exist to close exactly the collision class C.1's
 * {@code SpikeHashProbe} found for a different structural resource: a projection that is
 * insertion-order-sensitive, or that misses a concurrent, unrelated grant.
 */
@Tag("core")
class PermissionProjectionTest {
   @Test void totalProjectionIsNullForNullPermission() {
      assertNull(PermissionProjection.projectTotal(null, "host-org"));
   }

   @Test void totalProjectionIsEmptyStringForEmptyPermission() {
      Permission permission = new Permission();
      assertEquals("", PermissionProjection.projectTotal(permission, "host-org"));
   }

   @Test void totalProjectionIsStableRegardlessOfInsertionOrder() {
      Permission a = new Permission();
      a.setUserGrantsForOrg(ResourceAction.READ, Set.of("alice"), "host-org");
      a.setUserGrantsForOrg(ResourceAction.WRITE, Set.of("bob"), "host-org");

      Permission b = new Permission();
      b.setUserGrantsForOrg(ResourceAction.WRITE, Set.of("bob"), "host-org");
      b.setUserGrantsForOrg(ResourceAction.READ, Set.of("alice"), "host-org");

      assertEquals(PermissionProjection.projectTotal(a, "host-org"),
                   PermissionProjection.projectTotal(b, "host-org"));
   }

   @Test void totalProjectionChangesWhenAnUnrelatedGrantIsAdded() {
      Permission before = new Permission();
      before.setUserGrantsForOrg(ResourceAction.READ, Set.of("alice"), "host-org");

      Permission after = new Permission();
      after.setUserGrantsForOrg(ResourceAction.READ, Set.of("alice"), "host-org");
      after.setUserGrantsForOrg(ResourceAction.ADMIN, Set.of("carol"), "host-org");

      assertNotEquals(PermissionProjection.projectTotal(before, "host-org"),
                      PermissionProjection.projectTotal(after, "host-org"));
   }

   @Test void totalProjectionCoversAllFourIdentityTypes() {
      Permission permission = new Permission();
      permission.setUserGrantsForOrg(ResourceAction.READ, Set.of("alice"), "host-org");
      permission.setGroupGrantsForOrg(ResourceAction.READ, Set.of("everyone"), "host-org");
      permission.setRoleGrantsForOrg(ResourceAction.READ, Set.of("Analyst"), "host-org");
      permission.setOrganizationGrantsForOrg(ResourceAction.READ, Set.of("host-org"), "host-org");

      String projection = PermissionProjection.projectTotal(permission, "host-org");

      assertTrue(projection.contains("USER|host-org|alice"));
      assertTrue(projection.contains("GROUP|host-org|everyone"));
      assertTrue(projection.contains("ROLE|host-org|Analyst"));
      assertTrue(projection.contains("ORGANIZATION"));
   }

   @Test void grantProjectionIsNoActionsSuffixWhenActionsIsNullOrEmpty() {
      IdentityID id = new IdentityID("alice", "host-org");

      assertEquals("USER|host-org|alice", PermissionProjection.projectGrant("USER", id, null));
      assertEquals("USER|host-org|alice", PermissionProjection.projectGrant("USER", id, List.of()));
   }

   @Test void grantProjectionSortsActions() {
      IdentityID id = new IdentityID("alice", "host-org");

      String a = PermissionProjection.projectGrant("USER", id, List.of("WRITE", "READ", "ADMIN"));
      String b = PermissionProjection.projectGrant("USER", id, List.of("ADMIN", "READ", "WRITE"));

      assertEquals(a, b);
      assertEquals("USER|host-org|alice=ADMIN,READ,WRITE", a);
   }

   @Test void actionsForReturnsEmptyForNullPermission() {
      IdentityID id = new IdentityID("alice", "host-org");
      assertTrue(PermissionProjection.actionsFor(null, "USER", id, "host-org").isEmpty());
   }

   @Test void actionsForReturnsEmptyForAnUngrantedIdentity() {
      Permission permission = new Permission();
      permission.setUserGrantsForOrg(ResourceAction.READ, Set.of("alice"), "host-org");
      IdentityID bob = new IdentityID("bob", "host-org");

      assertTrue(PermissionProjection.actionsFor(permission, "USER", bob, "host-org").isEmpty());
   }

   @Test void actionsForReturnsTheGrantedIdentitysActions() {
      Permission permission = new Permission();
      permission.setUserGrantsForOrg(ResourceAction.READ, Set.of("alice"), "host-org");
      permission.setUserGrantsForOrg(ResourceAction.WRITE, Set.of("alice"), "host-org");
      permission.setUserGrantsForOrg(ResourceAction.ADMIN, Set.of("bob"), "host-org");
      IdentityID alice = new IdentityID("alice", "host-org");

      Set<String> actions = PermissionProjection.actionsFor(permission, "USER", alice, "host-org");

      assertEquals(Set.of("READ", "WRITE"), actions);
   }

   // -------------------------------------------------------------------------
   // projectTotalWithOverride (bug 76352 PM-001 -- preview's proposedValue)
   // -------------------------------------------------------------------------

   @Test void withOverrideBuildsFromScratchWhenBasePermissionIsNull() {
      IdentityID bob = new IdentityID("bob", "host-org");

      String projection = PermissionProjection.projectTotalWithOverride(
         null, "host-org", "USER", bob, List.of("READ"));

      assertEquals("USER|host-org|bob=READ;", projection);
   }

   @Test void withOverrideReplacesAnExistingIdentitysGrant() {
      Permission permission = new Permission();
      permission.setUserGrantsForOrg(ResourceAction.READ, Set.of("bob"), "host-org");
      IdentityID bob = new IdentityID("bob", "host-org");

      String projection = PermissionProjection.projectTotalWithOverride(
         permission, "host-org", "USER", bob, List.of("READ", "WRITE"));

      assertEquals("USER|host-org|bob=READ,WRITE;", projection);
   }

   @Test void withOverrideRemovesTheIdentityWhenActionsIsNull() {
      Permission permission = new Permission();
      permission.setUserGrantsForOrg(ResourceAction.READ, Set.of("bob"), "host-org");
      IdentityID bob = new IdentityID("bob", "host-org");

      String projection = PermissionProjection.projectTotalWithOverride(
         permission, "host-org", "USER", bob, null);

      assertEquals("", projection);
   }

   @Test void withOverrideLeavesOtherIdentitiesUntouched() {
      Permission permission = new Permission();
      permission.setUserGrantsForOrg(ResourceAction.READ, Set.of("alice"), "host-org");
      permission.setUserGrantsForOrg(ResourceAction.WRITE, Set.of("bob"), "host-org");
      IdentityID bob = new IdentityID("bob", "host-org");

      String projection = PermissionProjection.projectTotalWithOverride(
         permission, "host-org", "USER", bob, null);

      assertTrue(projection.contains("USER|host-org|alice=READ"));
      assertFalse(projection.contains("bob"));
   }
}
