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

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.ObjectStreamClass;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@Tag("core")
class PermissionIdentityTest {

   @Test
   void equalsIgnoreCase_bothNullOrganizationIds_sameName_matches() {
      Permission.PermissionIdentity left = new Permission.PermissionIdentity("Alice", null);
      Permission.PermissionIdentity right = new Permission.PermissionIdentity("alice", null);

      assertTrue(left.equalsIgnoreCase(right));
   }

   @Test
   void equalsIgnoreCase_nullVsNonNullOrganizationId_noMatch() {
      Permission.PermissionIdentity left = new Permission.PermissionIdentity("Alice", null);
      Permission.PermissionIdentity right = new Permission.PermissionIdentity("Alice", "host-org");

      assertFalse(left.equalsIgnoreCase(right));
   }

   @Test
   void equalsIgnoreCase_bothNonNullOrganizationIds_sameOrg_matches() {
      Permission.PermissionIdentity left = new Permission.PermissionIdentity("alice", "org-a");
      Permission.PermissionIdentity right = new Permission.PermissionIdentity("ALICE", "ORG-A");

      assertTrue(left.equalsIgnoreCase(right));
   }

   @Test
   void equalsIgnoreCase_bothNonNullOrganizationIds_differentOrg_noMatch() {
      Permission.PermissionIdentity left = new Permission.PermissionIdentity("alice", "org-a");
      Permission.PermissionIdentity right = new Permission.PermissionIdentity("ALICE", "org-b");

      assertFalse(left.equalsIgnoreCase(right));
   }

   // Bug #77382: equal instances must have equal hash codes, including null fields
   @Test
   void hashCode_equalInstances_sameHashCode() {
      Permission.PermissionIdentity left = new Permission.PermissionIdentity("alice", "org-a");
      Permission.PermissionIdentity right = new Permission.PermissionIdentity("alice", "org-a");

      assertEquals(left, right);
      assertEquals(left.hashCode(), right.hashCode());
   }

   @Test
   void hashCode_nullNameAndOrg_sameHashCode() {
      Permission.PermissionIdentity left = new Permission.PermissionIdentity(null, null);
      Permission.PermissionIdentity right = new Permission.PermissionIdentity(null, null);
      Permission.PermissionIdentity fromNullId =
         new Permission.PermissionIdentity((IdentityID) null);

      assertEquals(left, right);
      assertEquals(left.hashCode(), right.hashCode());
      assertEquals(left, fromNullId);
      assertEquals(left.hashCode(), fromNullId.hashCode());
   }

   @Test
   void hashCode_nullOrg_sameHashCode() {
      Permission.PermissionIdentity left = new Permission.PermissionIdentity("alice", null);
      Permission.PermissionIdentity right = new Permission.PermissionIdentity("alice", null);

      assertEquals(left, right);
      assertEquals(left.hashCode(), right.hashCode());
   }

   // Bug #77382: HashSet membership must use value equality, not object identity
   @Test
   void hashSet_containsEqualInstance() {
      Set<Permission.PermissionIdentity> set = new HashSet<>();
      set.add(new Permission.PermissionIdentity("alice", "org-a"));

      assertTrue(set.contains(new Permission.PermissionIdentity("alice", "org-a")));
      assertFalse(set.contains(new Permission.PermissionIdentity("alice", "org-b")));
   }

   @Test
   void hashSet_addEqualInstance_doesNotDuplicate() {
      Set<Permission.PermissionIdentity> set = new HashSet<>();
      set.add(new Permission.PermissionIdentity("alice", "org-a"));
      set.add(new Permission.PermissionIdentity("alice", "org-a"));

      assertEquals(1, set.size());
   }

   // The serialVersionUID is pinned to the value computed before hashCode() was added, so that
   // Java-serialized permissions stay compatible across nodes of a mixed-version cluster.
   @Test
   void serialVersionUID_pinnedToPreHashCodeValue() {
      assertEquals(-5948321699001649918L,
                   ObjectStreamClass.lookup(Permission.PermissionIdentity.class).getSerialVersionUID());
   }
}
