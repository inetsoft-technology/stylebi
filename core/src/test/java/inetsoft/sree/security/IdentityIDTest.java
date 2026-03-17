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
package inetsoft.sree.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IdentityIDTest {

   private static final String KEY_DELIMITER = "~;~";
   private static final String GLOBAL_ORG_KEY = "__GLOBAL__";

   // ---- convertToKey ----

   @Test
   void convertToKey_withOrgId_producesDelimitedKey() {
      IdentityID id = new IdentityID("alice", "myOrg");
      assertEquals("alice" + KEY_DELIMITER + "myOrg", id.convertToKey());
   }

   @Test
   void convertToKey_withNullOrgId_usesGlobalOrgKey() {
      IdentityID id = new IdentityID("alice", null);
      assertEquals("alice" + KEY_DELIMITER + GLOBAL_ORG_KEY, id.convertToKey());
   }

   @Test
   void convertToKey_delimiterConstantMatches() {
      assertEquals(KEY_DELIMITER, IdentityID.KEY_DELIMITER);
   }

   // ---- getIdentityIDFromKey ----

   @Test
   void getIdentityIDFromKey_null_returnsNull() {
      assertNull(IdentityID.getIdentityIDFromKey(null));
   }

   @Test
   void getIdentityIDFromKey_emptyString_returnsEmptyIdentityID() {
      IdentityID result = IdentityID.getIdentityIDFromKey("");
      assertNotNull(result);
      assertEquals("", result.getName());
      assertEquals("", result.getOrgID());
   }

   @Test
   void getIdentityIDFromKey_withDelimiter_parsesNameAndOrg() {
      String key = "bob" + KEY_DELIMITER + "acme";
      IdentityID result = IdentityID.getIdentityIDFromKey(key);
      assertEquals("bob", result.getName());
      assertEquals("acme", result.getOrgID());
   }

   @Test
   void getIdentityIDFromKey_withGlobalOrgKey_revertsOrgToNull() {
      String key = "carol" + KEY_DELIMITER + GLOBAL_ORG_KEY;
      IdentityID result = IdentityID.getIdentityIDFromKey(key);
      assertEquals("carol", result.getName());
      assertNull(result.getOrgID());
   }

   @Test
   void getIdentityIDFromKey_withNullLiteralOrg_revertsOrgToNull() {
      String key = "dave" + KEY_DELIMITER + "null";
      IdentityID result = IdentityID.getIdentityIDFromKey(key);
      assertEquals("dave", result.getName());
      assertNull(result.getOrgID());
   }

   // ---- round-trip ----

   @Test
   void roundTrip_withNonNullOrg_preservesValues() {
      IdentityID original = new IdentityID("eve", "prodOrg");
      String key = original.convertToKey();
      IdentityID restored = IdentityID.getIdentityIDFromKey(key);
      assertEquals(original.getName(), restored.getName());
      assertEquals(original.getOrgID(), restored.getOrgID());
   }

   @Test
   void roundTrip_withNullOrg_preservesNullOrg() {
      IdentityID original = new IdentityID("frank", null);
      String key = original.convertToKey();
      IdentityID restored = IdentityID.getIdentityIDFromKey(key);
      assertEquals("frank", restored.getName());
      assertNull(restored.getOrgID());
   }

   // ---- equalsIgnoreCase ----

   @Test
   void equalsIgnoreCase_sameCaseNameAndOrg_returnsTrue() {
      IdentityID a = new IdentityID("Alice", "OrgA");
      IdentityID b = new IdentityID("Alice", "OrgA");
      assertTrue(a.equalsIgnoreCase(b));
   }

   @Test
   void equalsIgnoreCase_differentCaseName_returnsTrue() {
      IdentityID a = new IdentityID("Alice", "OrgA");
      IdentityID b = new IdentityID("alice", "OrgA");
      assertTrue(a.equalsIgnoreCase(b));
   }

   @Test
   void equalsIgnoreCase_differentCaseOrg_returnsTrue() {
      IdentityID a = new IdentityID("Alice", "OrgA");
      IdentityID b = new IdentityID("Alice", "orga");
      assertTrue(a.equalsIgnoreCase(b));
   }

   @Test
   void equalsIgnoreCase_differentName_returnsFalse() {
      IdentityID a = new IdentityID("Alice", "OrgA");
      IdentityID b = new IdentityID("Bob", "OrgA");
      assertFalse(a.equalsIgnoreCase(b));
   }

   @Test
   void equalsIgnoreCase_differentOrg_returnsFalse() {
      IdentityID a = new IdentityID("Alice", "OrgA");
      IdentityID b = new IdentityID("Alice", "OrgB");
      assertFalse(a.equalsIgnoreCase(b));
   }

   // ---- compareTo ----

   @Test
   void compareTo_sameNameSameOrg_returnsZero() {
      IdentityID a = new IdentityID("Alice", "Org1");
      IdentityID b = new IdentityID("Alice", "Org1");
      assertEquals(0, a.compareTo(b));
   }

   @Test
   void compareTo_nameAlphaOrder_respectsNameFirst() {
      IdentityID a = new IdentityID("Alice", "Org1");
      IdentityID b = new IdentityID("Bob", "Org1");
      assertTrue(a.compareTo(b) < 0);
      assertTrue(b.compareTo(a) > 0);
   }

   @Test
   void compareTo_sameNameDifferentOrg_ordersByOrg() {
      IdentityID a = new IdentityID("Alice", "Org1");
      IdentityID b = new IdentityID("Alice", "Org2");
      assertTrue(a.compareTo(b) < 0);
   }

   // ---- equals / hashCode ----

   @Test
   void equals_sameValues_returnsTrue() {
      IdentityID a = new IdentityID("alice", "orgX");
      IdentityID b = new IdentityID("alice", "orgX");
      assertEquals(a, b);
   }

   @Test
   void equals_differentName_returnsFalse() {
      IdentityID a = new IdentityID("alice", "orgX");
      IdentityID b = new IdentityID("bob", "orgX");
      assertNotEquals(a, b);
   }

   @Test
   void equals_differentOrg_returnsFalse() {
      IdentityID a = new IdentityID("alice", "orgX");
      IdentityID b = new IdentityID("alice", "orgY");
      assertNotEquals(a, b);
   }

   @Test
   void equals_nullOrg_distinguishedFromNonNullOrg() {
      IdentityID a = new IdentityID("alice", null);
      IdentityID b = new IdentityID("alice", "orgX");
      assertNotEquals(a, b);
   }

   @Test
   void equals_bothNullOrg_equalToEachOther() {
      IdentityID a = new IdentityID("alice", null);
      IdentityID b = new IdentityID("alice", null);
      assertEquals(a, b);
   }

   @Test
   void equals_reflexive() {
      IdentityID a = new IdentityID("alice", "orgX");
      assertEquals(a, a);
   }

   @Test
   void equals_notEqualToNull() {
      IdentityID a = new IdentityID("alice", "orgX");
      assertNotEquals(null, a);
   }

   @Test
   void equals_notEqualToDifferentType() {
      IdentityID a = new IdentityID("alice", "orgX");
      assertNotEquals("alice~;~orgX", a);
   }

   @Test
   void hashCode_equalObjects_haveSameHashCode() {
      IdentityID a = new IdentityID("alice", "orgX");
      IdentityID b = new IdentityID("alice", "orgX");
      assertEquals(a.hashCode(), b.hashCode());
   }

   @Test
   void hashCode_differentObjects_likelyHaveDifferentHashCode() {
      IdentityID a = new IdentityID("alice", "orgX");
      IdentityID b = new IdentityID("bob", "orgY");
      assertNotEquals(a.hashCode(), b.hashCode());
   }

   // ---- clone ----

   @Test
   void clone_producesIndependentCopy() throws CloneNotSupportedException {
      IdentityID original = new IdentityID("alice", "orgX");
      IdentityID clone = (IdentityID) original.clone();

      assertNotSame(original, clone);
      assertEquals(original.getName(), clone.getName());
      assertEquals(original.getOrgID(), clone.getOrgID());
   }

   @Test
   void clone_mutatingCloneDoesNotAffectOriginal() throws CloneNotSupportedException {
      IdentityID original = new IdentityID("alice", "orgX");
      IdentityID clone = (IdentityID) original.clone();
      clone.setName("bob");
      clone.setOrgID("orgY");

      assertEquals("alice", original.getName());
      assertEquals("orgX", original.getOrgID());
   }

   // ---- getLabel ----

   @Test
   void getLabel_inCommunityMode_returnsNameOnly() {
      // In the community module, LicenseManager.isEnterprise() returns false
      // because inetsoft.enterprise.EnterpriseConfig is not on the classpath.
      IdentityID id = new IdentityID("alice", "orgX");
      assertEquals("alice", id.getLabel());
   }
}
