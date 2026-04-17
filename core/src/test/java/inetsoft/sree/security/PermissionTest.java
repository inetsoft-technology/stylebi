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

/*
 * Permission decision trees
 *
 * check(PermissionIdentity, ResourceAction, int type)
 *  ├─ [A] type is invalid                              → false
 *  ├─ [B] grants.get(action) == null                   → false
 *  ├─ [C] identity found by equals()                   → true
 *  ├─ [D] equals() fails, equalsIgnoreCase() succeeds  → true  (AD/LDAP compat fix)
 *  └─ [E] found by neither                             → false
 *
 * getGrants(action, identityType, orgId)
 *  ├─ [A] identityType invalid                         → empty set
 *  ├─ [B] no entry for action                          → empty set
 *  ├─ [C] orgId == null                                → all grants (no filter)
 *  ├─ [D] orgId matches grant.organizationID           → included
 *  ├─ [E] ROLE type + grant.organizationID == null     → always included (global role)
 *  └─ [F] orgId mismatch (non-role-null-org)           → excluded
 *
 * setGrants(action, identityType, entities)
 *  ├─ [A] identityType invalid                         → no-op
 *  ├─ [B] entities == null                             → removes action from map
 *  └─ [C] entities != null                             → stores defensive copy
 *
 * isBlank()
 *  ├─ [A] all grant sets empty                         → true
 *  └─ [B] at least one non-empty entry                 → false
 *
 * isBlank(orgId)
 *  ├─ [A] no identity matches orgId                    → true
 *  └─ [B] at least one identity matches orgId          → false
 *
 * isOrgInPerm(action, orgId)
 *  ├─ [A] user grant orgId matches                     → true
 *  ├─ [B] group grant orgId matches                    → true
 *  ├─ [C] role grant non-null orgId matches            → true
 *  ├─ [D] org grant orgId matches                      → true
 *  ├─ [E] no match in any type                         → false
 *  └─ [F] role grant null orgId                        → NOT matched  ← asymmetry vs getGrants [E]
 *
 * cleanOrganizationFromPermission(action, orgId)
 *  ├─ [A] user grants for orgId removed, others kept
 *  ├─ [B] group grants for orgId removed, others kept
 *  ├─ [C] role with null orgId NOT removed (Tool.equals null-safe)
 *  └─ [D] org grants for orgId removed, others kept
 *
 * clone()
 *  └─ [A] deep copy: mutations to clone do not affect original
 */

import inetsoft.uql.util.Identity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class PermissionTest {

   private static final String ORG_A = "orgA";
   private static final String ORG_B = "orgB";

   // ── check() ───────────────────────────────────────────────────────────────

   // [Path C] Exact match found in grants set → true for all four identity types
   @ParameterizedTest
   @MethodSource("allIdentityTypes")
   void check_exactMatch_returnsTrue(int identityType) {
      Permission perm = new Permission();
      Permission.PermissionIdentity identity = new Permission.PermissionIdentity("alice", ORG_A);
      perm.setGrants(ResourceAction.READ, identityType, Set.of(identity));
      assertTrue(perm.check(identity, ResourceAction.READ, identityType));
   }

   private static Stream<Arguments> allIdentityTypes() {
      return Stream.of(
         // ✓ user identity
         Arguments.of(Identity.USER),
         // ✓ role identity
         Arguments.of(Identity.ROLE),
         // ✓ group identity
         Arguments.of(Identity.GROUP),
         // ✓ organization identity
         Arguments.of(Identity.ORGANIZATION)
      );
   }

   // [Path A] Invalid identity type → false, no exception
   @Test
   void check_invalidType_returnsFalse() {
      Permission perm = new Permission();
      Permission.PermissionIdentity identity = new Permission.PermissionIdentity("alice", ORG_A);
      perm.setGrants(ResourceAction.READ, Identity.USER, Set.of(identity));
      assertFalse(perm.check(identity, ResourceAction.READ, 99));
   }

   // [Path B] No grants stored for the requested action → false
   @Test
   void check_noGrantsForAction_returnsFalse() {
      Permission perm = new Permission();
      assertFalse(perm.check(new Permission.PermissionIdentity("alice", ORG_A),
                             ResourceAction.READ, Identity.USER));
   }

   // [Path E] Different identity in grants → false
   @Test
   void check_differentIdentity_returnsFalse() {
      Permission perm = new Permission();
      perm.setGrants(ResourceAction.READ, Identity.USER,
                     Set.of(new Permission.PermissionIdentity("bob", ORG_A)));
      assertFalse(perm.check(new Permission.PermissionIdentity("alice", ORG_A),
                             ResourceAction.READ, Identity.USER));
   }

   // [Path D] Case-insensitive fallback → grant found regardless of case
   // Risk 3: AD/LDAP providers may return names in different casing than stored grants
   // Condition: grant stored as "Alice"/"OrgA", check with "alice"/"orga" — exact equals() fails
   @Test
   void check_caseInsensitiveFallback_returnsTrue() {
      Permission perm = new Permission();
      perm.setGrants(ResourceAction.READ, Identity.USER,
                     Set.of(new Permission.PermissionIdentity("Alice", "OrgA")));
      assertTrue(perm.check(new Permission.PermissionIdentity("alice", "orga"),
                            ResourceAction.READ, Identity.USER));
   }

   // ── getGrants(action, identityType, orgId) ────────────────────────────────

   // [Path A] Invalid identity type → empty set, no exception
   @Test
   void getGrants_invalidType_returnsEmptySet() {
      Permission perm = new Permission();
      perm.setGrants(ResourceAction.READ, Identity.USER,
                     Set.of(new Permission.PermissionIdentity("alice", ORG_A)));
      assertTrue(perm.getGrants(ResourceAction.READ, 99, ORG_A).isEmpty());
   }

   // [Path B] No entry for action → empty set
   @Test
   void getGrants_noEntryForAction_returnsEmptySet() {
      assertTrue(new Permission().getGrants(ResourceAction.READ, Identity.USER, ORG_A).isEmpty());
   }

   // [Path C] orgId == null → all grants returned regardless of organization
   // Risk 3: callers passing null orgId receive grants from every org — potential over-privilege
   @Test
   void getGrants_orgIdNull_returnsAllOrgs() {
      Permission perm = new Permission();
      perm.setGrants(ResourceAction.READ, Identity.USER, Set.of(
         new Permission.PermissionIdentity("alice", ORG_A),
         new Permission.PermissionIdentity("bob", ORG_B)
      ));
      assertEquals(2, perm.getGrants(ResourceAction.READ, Identity.USER, null).size());
   }

   // [Path D] orgId matches → only matching grants returned
   @Test
   void getGrants_orgIdFilter_returnsMatchingOrgOnly() {
      Permission perm = new Permission();
      perm.setGrants(ResourceAction.READ, Identity.USER, Set.of(
         new Permission.PermissionIdentity("alice", ORG_A),
         new Permission.PermissionIdentity("bob", ORG_B)
      ));
      Set<Permission.PermissionIdentity> result = perm.getGrants(ResourceAction.READ, Identity.USER, ORG_A);
      assertEquals(1, result.size());
      assertEquals("alice", result.iterator().next().getName());
   }

   // [Path F] orgId mismatch → empty set
   @Test
   void getGrants_orgIdMismatch_returnsEmptySet() {
      Permission perm = new Permission();
      perm.setGrants(ResourceAction.READ, Identity.USER,
                     Set.of(new Permission.PermissionIdentity("alice", ORG_B)));
      assertTrue(perm.getGrants(ResourceAction.READ, Identity.USER, ORG_A).isEmpty());
   }

   // [Path E] ROLE with null organizationID → always included for any orgId query
   // Risk 3: global roles (cross-org) must be visible from every org's context;
   //         roles from a different org must remain excluded
   @Test
   void getGrants_roleWithNullOrg_alwaysIncluded() {
      Permission perm = new Permission();
      perm.setGrants(ResourceAction.READ, Identity.ROLE, Set.of(
         new Permission.PermissionIdentity("globalRole", null),
         new Permission.PermissionIdentity("orgBRole", ORG_B)
      ));
      Set<Permission.PermissionIdentity> result = perm.getGrants(ResourceAction.READ, Identity.ROLE, ORG_A);
      assertTrue(result.stream().anyMatch(id -> "globalRole".equals(id.getName())),
                 "Global role (null org) must be visible from any org");
      assertFalse(result.stream().anyMatch(id -> "orgBRole".equals(id.getName())),
                  "Role from a different org must not be visible");
   }

   // ── setGrants(action, identityType, entities) ─────────────────────────────

   // [Path A] Invalid identity type → no-op, no exception, no state change
   @Test
   void setGrants_invalidType_isNoOp() {
      Permission perm = new Permission();
      assertDoesNotThrow(() -> perm.setGrants(ResourceAction.READ, 99,
                                              Set.of(new Permission.PermissionIdentity("alice", ORG_A))));
      assertTrue(perm.getGrants(ResourceAction.READ, Identity.USER, null).isEmpty());
   }

   // [Path B] Null entities → removes the action key entirely from the map
   @Test
   void setGrants_nullEntities_removesAction() {
      Permission perm = new Permission();
      perm.setGrants(ResourceAction.READ, Identity.USER,
                     Set.of(new Permission.PermissionIdentity("alice", ORG_A)));
      perm.setGrants(ResourceAction.READ, Identity.USER, null);
      assertTrue(perm.getGrants(ResourceAction.READ, Identity.USER, null).isEmpty());
   }

   // [Path C] Non-null entities → stored as a defensive copy; external mutation has no effect
   @Test
   void setGrants_storesDefensiveCopy_externalMutationIgnored() {
      Permission perm = new Permission();
      Set<Permission.PermissionIdentity> mutableSet = new HashSet<>();
      mutableSet.add(new Permission.PermissionIdentity("alice", ORG_A));
      perm.setGrants(ResourceAction.READ, Identity.USER, mutableSet);

      mutableSet.add(new Permission.PermissionIdentity("bob", ORG_A));

      assertEquals(1, perm.getGrants(ResourceAction.READ, Identity.USER, null).size());
   }

   // ── isBlank() ─────────────────────────────────────────────────────────────

   // [Path A] Newly created Permission has no grants → blank
   @Test
   void isBlank_newPermission_returnsTrue() {
      assertTrue(new Permission().isBlank());
   }

   // [Path B] Any grant added → not blank; each identity type is stored in its own field,
   // so all four must be checked independently to catch a bug where one field is checked twice
   @ParameterizedTest
   @MethodSource("allIdentityTypes")
   void isBlank_afterAddingGrant_returnsFalse(int identityType) {
      Permission perm = new Permission();
      perm.setGrants(ResourceAction.READ, identityType,
                     Set.of(new Permission.PermissionIdentity("alice", ORG_A)));
      assertFalse(perm.isBlank());
   }

   // ── isBlank(orgId) ────────────────────────────────────────────────────────

   // [Path A/B] isBlank(orgId) — blank when no grants belong to orgId, not blank when they do
   @ParameterizedTest
   @MethodSource("isBlankForOrgCases")
   void isBlankForOrg(String grantOrg, String checkOrg, boolean expected) {
      Permission perm = new Permission();
      perm.setGrants(ResourceAction.READ, Identity.USER,
                     Set.of(new Permission.PermissionIdentity("alice", grantOrg)));
      assertEquals(expected, perm.isBlank(checkOrg));
   }

   private static Stream<Arguments> isBlankForOrgCases() {
      return Stream.of(
         // ✓ grant belongs to a different org → blank for the checked org
         Arguments.of(ORG_B, ORG_A, true),
         // ✗ grant belongs to the checked org → not blank
         Arguments.of(ORG_A, ORG_A, false)
      );
   }

   // ── isOrgInPerm(action, orgId) ────────────────────────────────────────────

   // [Path A-D] Each identity type can signal org presence
   @ParameterizedTest
   @MethodSource("allIdentityTypes")
   void isOrgInPerm_grantMatchesOrg_returnsTrue(int identityType) {
      Permission perm = new Permission();
      perm.setGrants(ResourceAction.READ, identityType,
                     Set.of(new Permission.PermissionIdentity("x", ORG_A)));
      assertTrue(perm.isOrgInPerm(ResourceAction.READ, ORG_A));
   }

   // [Path E] No grant matches orgId → false
   @Test
   void isOrgInPerm_noMatchingGrant_returnsFalse() {
      Permission perm = new Permission();
      perm.setGrants(ResourceAction.READ, Identity.USER,
                     Set.of(new Permission.PermissionIdentity("alice", ORG_B)));
      assertFalse(perm.isOrgInPerm(ResourceAction.READ, ORG_A));
   }

   // [Path F] Role grant with null orgId is NOT matched by isOrgInPerm
   // Risk 3: asymmetry — null-org roles ARE included in getGrants [E] but NOT in isOrgInPerm [F];
   //         the two methods answer different questions: "who can access?" vs "does this org own a grant?"
   @Test
   void isOrgInPerm_roleNullOrg_notMatched() {
      Permission perm = new Permission();
      perm.setGrants(ResourceAction.READ, Identity.ROLE,
                     Set.of(new Permission.PermissionIdentity("globalRole", null)));
      assertFalse(perm.isOrgInPerm(ResourceAction.READ, ORG_A),
                  "Null-org role must not count as belonging to any specific org");
   }

   // ── cleanOrganizationFromPermission(action, orgId) ────────────────────────

   // [Path A/B/D] cleanOrganizationFromPermission removes orgId grants and keeps other orgs
   // for user, group, and organization identity types (role null-org special case tested separately)
   @ParameterizedTest
   @MethodSource("identityTypesExcludingRole")
   void cleanOrg_removesGrantsForOrg_keepsOtherOrgs(int identityType) {
      Permission perm = new Permission();
      perm.setGrants(ResourceAction.READ, identityType, Set.of(
         new Permission.PermissionIdentity("entityA", ORG_A),
         new Permission.PermissionIdentity("entityB", ORG_B)
      ));
      perm.cleanOrganizationFromPermission(ResourceAction.READ, ORG_A);
      Set<Permission.PermissionIdentity> remaining =
         perm.getGrants(ResourceAction.READ, identityType, null);
      assertFalse(remaining.stream().anyMatch(id -> ORG_A.equals(id.getOrganizationID())));
      assertTrue(remaining.stream().anyMatch(id -> ORG_B.equals(id.getOrganizationID())));
   }

   private static Stream<Arguments> identityTypesExcludingRole() {
      return Stream.of(
         // ✓ user grants
         Arguments.of(Identity.USER),
         // ✓ group grants
         Arguments.of(Identity.GROUP),
         // ✓ organization grants
         Arguments.of(Identity.ORGANIZATION)
      );
   }

   // [Path C] Role with null orgId is NOT removed during org cleanup
   // Risk 3: global roles must survive org deletion/cleanup; Tool.equals(null, orgId) == false
   @Test
   void cleanOrg_roleWithNullOrg_survivesCleanup() {
      Permission perm = new Permission();
      perm.setGrants(ResourceAction.READ, Identity.ROLE, Set.of(
         new Permission.PermissionIdentity("globalRole", null),
         new Permission.PermissionIdentity("orgRole", ORG_A)
      ));
      perm.cleanOrganizationFromPermission(ResourceAction.READ, ORG_A);
      Set<Permission.PermissionIdentity> remaining =
         perm.getGrants(ResourceAction.READ, Identity.ROLE, null);
      assertTrue(remaining.stream().anyMatch(id -> "globalRole".equals(id.getName())),
                 "Global role (null org) must survive org cleanup");
      assertFalse(remaining.stream().anyMatch(id -> "orgRole".equals(id.getName())));
   }

   // ── clone() ───────────────────────────────────────────────────────────────

   // [Path A] clone() produces a deep copy; adding a grant to clone must not affect original
   @Test
   void clone_isDeepCopy_mutationDoesNotAffectOriginal() {
      Permission original = new Permission();
      original.setGrants(ResourceAction.READ, Identity.USER,
                         Set.of(new Permission.PermissionIdentity("alice", ORG_A)));
      Permission clone = (Permission) original.clone();

      clone.setGrants(ResourceAction.READ, Identity.USER,
                      Set.of(new Permission.PermissionIdentity("bob", ORG_A)));

      Set<Permission.PermissionIdentity> originalGrants =
         original.getGrants(ResourceAction.READ, Identity.USER, null);
      assertEquals(1, originalGrants.size());
      assertEquals("alice", originalGrants.iterator().next().getName());
   }

   // ── org-scoped setters ────────────────────────────────────────────────────

   // setUserGrantsForOrg replaces all grants for the target org; other orgs are unaffected
   @Test
   void setUserGrantsForOrg_replacesExistingOrgGrants_keepsOtherOrgs() {
      Permission perm = new Permission();
      perm.setGrants(ResourceAction.READ, Identity.USER, Set.of(
         new Permission.PermissionIdentity("alice", ORG_A),
         new Permission.PermissionIdentity("bob", ORG_B)
      ));
      perm.setUserGrantsForOrg(ResourceAction.READ, Set.of("charlie"), ORG_A);

      Set<Permission.PermissionIdentity> all =
         perm.getGrants(ResourceAction.READ, Identity.USER, null);
      assertTrue(all.stream()
                    .anyMatch(id -> "charlie".equals(id.getName()) && ORG_A.equals(id.getOrganizationID())),
                 "New user charlie should be present under orgA");
      assertFalse(all.stream().anyMatch(id -> "alice".equals(id.getName())),
                  "Old user alice should be replaced");
      assertTrue(all.stream()
                    .anyMatch(id -> "bob".equals(id.getName()) && ORG_B.equals(id.getOrganizationID())),
                 "User bob under orgB must be unaffected");
   }

   // Risk 3: setRoleGrantsForOrg(oldOrgId, newOrgId) renames org in-place;
   //         roles from unrelated orgs must not be touched
   @Test
   void setRoleGrantsForOrg_withOldAndNewOrgId_renamesCorrectly() {
      Permission perm = new Permission();
      perm.setGrants(ResourceAction.READ, Identity.ROLE, Set.of(
         new Permission.PermissionIdentity("admin", ORG_A),
         new Permission.PermissionIdentity("viewer", ORG_B)
      ));
      perm.setRoleGrantsForOrg(ResourceAction.READ, Set.of("admin"), ORG_A, "orgC");

      Set<Permission.PermissionIdentity> all =
         perm.getGrants(ResourceAction.READ, Identity.ROLE, null);
      assertTrue(all.stream()
                    .anyMatch(id -> "admin".equals(id.getName()) && "orgC".equals(id.getOrganizationID())),
                 "admin role should now be under orgC");
      assertFalse(all.stream()
                     .anyMatch(id -> "admin".equals(id.getName()) && ORG_A.equals(id.getOrganizationID())),
                  "admin role should no longer be under orgA");
      assertTrue(all.stream()
                    .anyMatch(id -> "viewer".equals(id.getName()) && ORG_B.equals(id.getOrganizationID())),
                 "viewer role under orgB must not be affected");
   }

   // ── splitPermissionForOrg() ───────────────────────────────────────────────

   // splitPermissionForOrg isolates each org's grants into a separate Permission instance
   @Test
   void splitPermissionForOrg_producesOnePermissionPerOrg() {
      Permission perm = new Permission();
      perm.setGrants(ResourceAction.READ, Identity.USER, Set.of(
         new Permission.PermissionIdentity("alice", ORG_A),
         new Permission.PermissionIdentity("bob", ORG_B)
      ));
      Map<String, Permission> split = perm.splitPermissionForOrg();

      assertTrue(split.containsKey(ORG_A));
      assertTrue(split.containsKey(ORG_B));
      assertTrue(split.get(ORG_A).getGrants(ResourceAction.READ, Identity.USER, null)
                      .stream().allMatch(id -> ORG_A.equals(id.getOrganizationID())),
                 "orgA slice must contain only orgA grants");
      assertTrue(split.get(ORG_B).getGrants(ResourceAction.READ, Identity.USER, null)
                      .stream().allMatch(id -> ORG_B.equals(id.getOrganizationID())),
                 "orgB slice must contain only orgB grants");
   }

   // ── PermissionIdentity.equals ─────────────────────────────────────────────

   // equals() uses Tool.equals for both fields — handles null correctly
   @ParameterizedTest
   @MethodSource("permissionIdentityEqualsCases")
   void permissionIdentity_equals(String name1, String org1, String name2, String org2, boolean expected) {
      Permission.PermissionIdentity a = new Permission.PermissionIdentity(name1, org1);
      Permission.PermissionIdentity b = new Permission.PermissionIdentity(name2, org2);
      assertEquals(expected, a.equals(b));
   }

   private static Stream<Arguments> permissionIdentityEqualsCases() {
      return Stream.of(
         // ✓ exact name and org match
         Arguments.of("alice", ORG_A, "alice", ORG_A, true),
         // ✓ both name and org null
         Arguments.of(null, null, null, null, true),
         // ✗ name differs
         Arguments.of("alice", ORG_A, "bob", ORG_A, false),
         // ✗ org differs
         Arguments.of("alice", ORG_A, "alice", ORG_B, false),
         // ✗ one org null, other non-null
         Arguments.of("alice", null, "alice", ORG_A, false)
      );
   }

   // ── PermissionIdentity.equalsIgnoreCase ───────────────────────────────────

   // equalsIgnoreCase() performs case-insensitive matching on both fields
   @ParameterizedTest
   @MethodSource("permissionIdentityEqualsIgnoreCaseCases")
   void permissionIdentity_equalsIgnoreCase(String name1, String org1,
                                            String name2, String org2, boolean expected)
   {
      Permission.PermissionIdentity a = new Permission.PermissionIdentity(name1, org1);
      Permission.PermissionIdentity b = new Permission.PermissionIdentity(name2, org2);
      assertEquals(expected, a.equalsIgnoreCase(b));
   }

   private static Stream<Arguments> permissionIdentityEqualsIgnoreCaseCases() {
      return Stream.of(
         // ✓ same case — matches
         Arguments.of("alice", ORG_A, "alice", ORG_A, true),
         // ✓ name and org differ only in case — matches
         Arguments.of("Alice", "OrgA", "alice", "orga", true),
         // ✗ name mismatch
         Arguments.of("alice", ORG_A, "bob", ORG_A, false),
         // ✗ org mismatch
         Arguments.of("alice", ORG_A, "alice", ORG_B, false)
      );
   }

   // equalsIgnoreCase(null) → false, no NPE
   @Test
   void permissionIdentity_equalsIgnoreCase_nullOther_returnsFalse() {
      assertFalse(new Permission.PermissionIdentity("alice", ORG_A).equalsIgnoreCase(null));
   }

   // Risk 3: documents bug at line 1154 — condition checks other.name instead of other.organizationID.
   // When this.organizationID == null, equalsIgnoreCase always returns false even if names match
   // case-insensitively and both orgs are null. As a result, case-insensitive matching silently
   // fails for identities with no org scope.
   @Test
   void permissionIdentity_equalsIgnoreCase_nullOrgBug_returnsFalse() {
      Permission.PermissionIdentity a = new Permission.PermissionIdentity("Alice", null);
      Permission.PermissionIdentity b = new Permission.PermissionIdentity("alice", null);
      // If the bug were fixed this should return true (names match, both orgs null).
      // Actual behavior due to the bug: false.
      assertFalse(a.equalsIgnoreCase(b));
   }
}
