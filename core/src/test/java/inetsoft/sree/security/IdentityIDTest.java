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

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link IdentityID}.
 *
 * <p>Class type: data model / utility — decision tree path coverage.
 *
 * <p>Intent vs implementation suspects:
 * <pre>
 * [Suspect 1] getIdentityIDFromKey(key) when key starts with KEY_DELIMITER
 *             intent: key "~;~org" should parse as name="" with orgID="org"
 *             actual: deliminator == 0 fails the `> 0` guard → falls into the no-delimiter
 *                     else-branch, ignores the org token, uses ThreadContext / OrganizationManager
 *
 * [Suspect 2] equalsIgnoreCase(null)
 *             intent: return false (treat null as non-equal)
 *             actual: other.name dereference → NullPointerException
 *
 * [Suspect 3] getConvertKey(idName, orgId) idempotency
 *             intent: return idName unchanged when it already contains KEY_DELIMITER
 *             actual: condition `!idName.startsWith(idName + KEY_DELIMITER)` is always true
 *                     (no string can start with a strictly longer string) → key is always re-appended
 * </pre>
 *
 * <p>Decision trees:
 * <pre>
 * convertToKey()
 *  ├─ [A] orgID != null  →  name + KEY_DELIMITER + orgID
 *  └─ [B] orgID == null  →  name + KEY_DELIMITER + "__GLOBAL__"
 *
 * getIdentityIDFromKey(key)
 *  ├─ [A] key == null                    →  null
 *  ├─ [B] key is empty                   →  IdentityID("", "")  (orgID = "", not null)
 *  ├─ [C] delimiter at index > 0         →  parse name and org
 *  │   ├─ [C1] orgPart == "__GLOBAL__"   →  orgID = null
 *  │   ├─ [C2] orgPart == "null"         →  orgID = null
 *  │   └─ [C3] orgPart is other string   →  orgID = orgPart
 *  └─ [D] delimiter absent or at index 0 →  ThreadContext / OrganizationManager  ← Suspect 1
 *
 * equalsIgnoreCase(other)
 *  ├─ [A] name and org both match case-insensitively  →  true
 *  ├─ [B] name differs                                →  false
 *  ├─ [C] org differs                                 →  false
 *  └─ [D] other == null                               →  NPE  ← Suspect 2
 *
 * compareTo(o)
 *  ├─ [A] name comparison != 0  →  return name comparison (org not consulted)
 *  └─ [B] name comparison == 0  →  return Tool.compare(orgID, o.orgID); null-safe, null sorts first
 *
 * getConvertKey(idName, orgId)
 *  ├─ [A] orgId == null or empty         →  use OrganizationManager.getCurrentOrgID()
 *  ├─ [B] idName has no delimiter        →  append KEY_DELIMITER + orgId
 *  └─ [C] idName already contains delimiter →  return unchanged (intent) / always re-appends (actual)  ← Suspect 3
 * </pre>
 */
class IdentityIDTest {

   private static final String KEY_DELIMITER = "~;~";
   private static final String GLOBAL_ORG_KEY = "__GLOBAL__";

   // ---- convertToKey ----

   @ParameterizedTest
   @MethodSource("convertToKeyCases")
   void convertToKey_producesExpectedKey(String name, String orgID, String expectedKey) {
      IdentityID id = new IdentityID(name, orgID);
      assertEquals(expectedKey, id.convertToKey());
   }

   private static Stream<Arguments> convertToKeyCases() {
      return Stream.of(
         // [Path A] non-null orgID → name + delimiter + orgID
         Arguments.of("alice", "myOrg", "alice" + KEY_DELIMITER + "myOrg"),
         // [Path B] null orgID → replaces with "__GLOBAL__" sentinel
         Arguments.of("alice", null, "alice" + KEY_DELIMITER + GLOBAL_ORG_KEY)
      );
   }

   @Test
   void convertToKey_delimiterConstantMatches() {
      // ensures the test-local constant stays in sync with the production constant
      assertEquals(KEY_DELIMITER, IdentityID.KEY_DELIMITER);
   }

   // ---- getIdentityIDFromKey ----

   @Test
   void getIdentityIDFromKey_null_returnsNull() {
      // [Path A] null input → null output
      assertNull(IdentityID.getIdentityIDFromKey(null));
   }

   @Test
   void getIdentityIDFromKey_emptyString_returnsEmptyIdentityID() {
      // [Path B] empty string → IdentityID("","") — orgID is "" not null
      IdentityID result = IdentityID.getIdentityIDFromKey("");
      assertNotNull(result);
      assertEquals("", result.getName());
      assertEquals("", result.getOrgID());
   }

   @ParameterizedTest
   @MethodSource("getIdentityIDFromKeyCases")
   void getIdentityIDFromKey_withDelimiter_parsesNameAndOrg(
      String key, String expectedName, String expectedOrg)
   {
      // [Path C] delimiter found at index > 0 → parse name and org
      IdentityID result = IdentityID.getIdentityIDFromKey(key);
      assertEquals(expectedName, result.getName());
      assertEquals(expectedOrg, result.getOrgID());
   }

   private static Stream<Arguments> getIdentityIDFromKeyCases() {
      return Stream.of(
         // [Path C3] normal org value kept as-is
         Arguments.of("bob" + KEY_DELIMITER + "acme", "bob", "acme"),
         // [Path C1] "__GLOBAL__" sentinel → orgID reverts to null
         Arguments.of("carol" + KEY_DELIMITER + GLOBAL_ORG_KEY, "carol", null),
         // [Path C2] literal string "null" → orgID reverts to null
         Arguments.of("dave" + KEY_DELIMITER + "null", "dave", null)
      );
   }

   //Bug #74667 
   @Disabled("[Suspect 1] delimiter at index 0 fails the `> 0` guard → falls into the no-delimiter " +
             "else-branch (ThreadContext) instead of parsing empty name with the given org")
   @Test
   void getIdentityIDFromKey_delimiterAtStart_parsesEmptyNameAndOrg() {
      // key "~;~someOrg" has delimiter at position 0; `deliminator > 0` is false
      // → name="" and orgID="someOrg" are not parsed; ThreadContext path is used instead
      String key = KEY_DELIMITER + "someOrg";
      IdentityID result = IdentityID.getIdentityIDFromKey(key);
      assertEquals("", result.getName());
      assertEquals("someOrg", result.getOrgID());
   }

   // ---- round-trip ----

   @ParameterizedTest
   @MethodSource("roundTripCases")
   void roundTrip_convertToKeyAndBack_preservesValues(String name, String orgID) {
      // convertToKey → getIdentityIDFromKey must restore original name and orgID exactly
      IdentityID original = new IdentityID(name, orgID);
      IdentityID restored = IdentityID.getIdentityIDFromKey(original.convertToKey());
      assertEquals(original.getName(), restored.getName());
      assertEquals(original.getOrgID(), restored.getOrgID());
   }

   private static Stream<Arguments> roundTripCases() {
      return Stream.of(
         // non-null orgID round-trips directly
         Arguments.of("eve", "prodOrg"),
         // null orgID round-trips via the "__GLOBAL__" sentinel
         Arguments.of("frank", null)
      );
   }

   // ---- equalsIgnoreCase ----

   @ParameterizedTest
   @MethodSource("equalsIgnoreCaseTrueCases")
   void equalsIgnoreCase_nameAndOrgMatchCaseInsensitively_returnsTrue(IdentityID a, IdentityID b) {
      // [Path A] both fields match when case is ignored → true
      assertTrue(a.equalsIgnoreCase(b));
   }

   private static Stream<Arguments> equalsIgnoreCaseTrueCases() {
      return Stream.of(
         // exact same case on both fields
         Arguments.of(new IdentityID("Alice", "OrgA"), new IdentityID("Alice", "OrgA")),
         // name case differs — must still match
         Arguments.of(new IdentityID("Alice", "OrgA"), new IdentityID("alice", "OrgA")),
         // org case differs — must still match
         Arguments.of(new IdentityID("Alice", "OrgA"), new IdentityID("Alice", "orga"))
      );
   }

   @ParameterizedTest
   @MethodSource("equalsIgnoreCaseFalseCases")
   void equalsIgnoreCase_valuesDiffer_returnsFalse(IdentityID a, IdentityID b) {
      // [Path B/C] name or org is genuinely different (not just case) → false
      assertFalse(a.equalsIgnoreCase(b));
   }

   private static Stream<Arguments> equalsIgnoreCaseFalseCases() {
      return Stream.of(
         // [Path B] name differs entirely
         Arguments.of(new IdentityID("Alice", "OrgA"), new IdentityID("Bob", "OrgA")),
         // [Path C] org differs entirely
         Arguments.of(new IdentityID("Alice", "OrgA"), new IdentityID("Alice", "OrgB"))
      );
   }

   @Test
   void equalsIgnoreCase_bothNullOrgs_returnsTrue() {
      // [Boundary] Tool.equals(null, null, false) → true; null orgs are symmetric
      IdentityID a = new IdentityID("Alice", null);
      IdentityID b = new IdentityID("Alice", null);
      assertTrue(a.equalsIgnoreCase(b));
   }

   @Test
   void equalsIgnoreCase_oneNullOrgOneNonNull_returnsFalse() {
      // [Boundary] Tool.equals(null, "OrgA", false) → false; null ≠ non-null
      IdentityID a = new IdentityID("Alice", null);
      IdentityID b = new IdentityID("Alice", "OrgA");
      assertFalse(a.equalsIgnoreCase(b));
   }

   //Bug #74667 
   @Disabled("[Suspect 2] other == null → NullPointerException on other.name dereference; " +
             "no null guard exists in equalsIgnoreCase()")
   @Test
   void equalsIgnoreCase_nullArgument_throwsNPE() {
      IdentityID a = new IdentityID("Alice", "OrgA");
      assertThrows(NullPointerException.class, () -> a.equalsIgnoreCase(null));
   }

   // ---- compareTo ----

   @Test
   void compareTo_sameNameSameOrg_returnsZero() {
      // [Path A+B] both comparisons produce 0
      IdentityID a = new IdentityID("Alice", "Org1");
      IdentityID b = new IdentityID("Alice", "Org1");
      assertEquals(0, a.compareTo(b));
   }

   @Test
   void compareTo_namesDiffer_respectsNameFirstAndIsAntisymmetric() {
      // [Path A] name comparison non-zero → org not consulted; result must be antisymmetric
      IdentityID a = new IdentityID("Alice", "Org1");
      IdentityID b = new IdentityID("Bob", "Org1");
      assertTrue(a.compareTo(b) < 0);
      assertTrue(b.compareTo(a) > 0);
   }

   @Test
   void compareTo_sameNameDifferentOrg_ordersByOrg() {
      // [Path B] names equal → fall through to org comparison
      IdentityID a = new IdentityID("Alice", "Org1");
      IdentityID b = new IdentityID("Alice", "Org2");
      assertTrue(a.compareTo(b) < 0);
   }

   @Test
   void compareTo_sameNameNullOrgVsNonNull_nullSortsFirst() {
      // [Boundary] Tool.compare(null, "Org1") → -1; null orgID must sort before any non-null org
      IdentityID withNull = new IdentityID("Alice", null);
      IdentityID withOrg = new IdentityID("Alice", "Org1");
      assertTrue(withNull.compareTo(withOrg) < 0);
      assertTrue(withOrg.compareTo(withNull) > 0);
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
   void equals_nullOrgDistinguishedFromNonNullOrg() {
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
   void hashCode_differentObjects_likelyDifferentHashCode() {
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

   @Test
   void clone_withNullOrg_preservesNullOrg() throws CloneNotSupportedException {
      // [Boundary] null orgID must copy as null, not be converted or defaulted
      IdentityID original = new IdentityID("alice", null);
      IdentityID clone = (IdentityID) original.clone();
      assertNotSame(original, clone);
      assertNull(clone.getOrgID());
   }

   // ---- getLabel / getLabelWithCaretDelimiter ----

   @Test
   void getLabel_communityMode_returnsNameOnly() {
      // In the community module LicenseManager.isEnterprise() returns false because
      // inetsoft.enterprise.EnterpriseConfig is not on the classpath; enterprise path is untestable here.
      IdentityID id = new IdentityID("alice", "orgX");
      assertEquals("alice", id.getLabel());
   }

   @Test
   void getLabelWithCaretDelimiter_communityMode_returnsNameOnly() {
      // Same LicenseManager constraint; enterprise path (name^orgID) is untestable here.
      IdentityID id = new IdentityID("alice", "orgX");
      assertEquals("alice", id.getLabelWithCaretDelimiter());
   }

   // ---- getIdentityRootResorucePath ----

   @Test
   void getIdentityRootResorucePath_producesWildcardKey() {
      // Utility wraps IdentityID("*", orgID).convertToKey(); verifies the wildcard key format.
      String key = IdentityID.getIdentityRootResorucePath("myOrg");
      assertEquals("*" + KEY_DELIMITER + "myOrg", key);
   }

   // ---- getConvertKey ----

   @Test
   void getConvertKey_plainName_appendsDelimiterAndOrg() {
      // [Path B] name has no delimiter → appended with KEY_DELIMITER + orgId
      String result = IdentityID.getConvertKey("alice", "myOrg");
      assertEquals("alice" + KEY_DELIMITER + "myOrg", result);
   }


   //Bug #74667 
   @Disabled("[Suspect 3] `!idName.startsWith(idName + KEY_DELIMITER)` is always true — " +
             "a string cannot start with a strictly longer string — so the delimiter is always " +
             "re-appended even when the key is already formatted")
   @Test
   void getConvertKey_alreadyKeyFormatted_returnsUnchanged() {
      // [Path C] idName already contains KEY_DELIMITER → should return unchanged, not re-append
      String alreadyFormatted = "alice" + KEY_DELIMITER + "myOrg";
      String result = IdentityID.getConvertKey(alreadyFormatted, "myOrg");
      assertEquals(alreadyFormatted, result);
   }

   // ---- default constructor ----

   @Test
   void defaultConstructor_fieldsDefaultToNull() {
      // No-arg constructor must leave name and orgID as null (not empty string)
      IdentityID id = new IdentityID();
      assertNull(id.getName());
      assertNull(id.getOrgID());
   }
}
