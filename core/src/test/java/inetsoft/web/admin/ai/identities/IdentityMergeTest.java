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
package inetsoft.web.admin.ai.identities;

import inetsoft.web.admin.security.*;
import inetsoft.sree.security.IdentityID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Design section 3.1/11 -- the direct unit-level version of the charter's central anti-wipe
 * assertion (#2): a spec touching only ONE field must leave every OTHER field exactly as the
 * fetched-current DTO had it, since only {@code emails}/{@code roles} (user),
 * {@code roles} (group), and {@code inheritedRoles} (role) have any real "omitted preserves
 * current" behavior already built into {@code SecurityService.updateUser}/{@code updateGroup}/
 * {@code updateRole} -- every other field is a blind overwrite there, so the merge itself is the
 * only thing standing between a partial spec and a silent wipe.
 */
@Tag("core")
class IdentityMergeTest {
   // -------------------------------------------------------------------------
   // mergeUser
   // -------------------------------------------------------------------------

   @Test void mergeUserOnlyChangesTheTouchedField() {
      IdentityID id = new IdentityID("alice", "host-org");
      SecurityUser current = fullUser(id);
      IdentitySpec spec = new IdentitySpec();
      spec.setAlias("New Alias");

      SecurityUser merged = IdentityMerge.mergeUser(current, spec, id);

      assertEquals("New Alias", merged.getAlias());
      assertEquals(id, merged.getIdentityID());
      assertEquals(current.getLocale(), merged.getLocale());
      assertEquals(current.getTheme(), merged.getTheme());
      assertEquals(current.isActive(), merged.isActive());
      assertEquals(current.getEmails(), merged.getEmails());
      assertEquals(current.getGroups(), merged.getGroups());
      assertEquals(current.getRoles(), merged.getRoles());
      assertNull(merged.getPassword());
   }

   @Test void mergeUserExplicitEmptyEmailsClearsWhileOtherFieldsSurvive() {
      IdentityID id = new IdentityID("alice", "host-org");
      SecurityUser current = fullUser(id);
      IdentitySpec spec = new IdentitySpec();
      spec.setEmails(List.of());

      SecurityUser merged = IdentityMerge.mergeUser(current, spec, id);

      assertEquals(List.of(), merged.getEmails());
      assertEquals(current.getAlias(), merged.getAlias());
      assertEquals(current.getGroups(), merged.getGroups());
      assertEquals(current.getRoles(), merged.getRoles());
   }

   // The exact copy-paste bug design section 2.5 warns about: resolveCreateUser/applyCreateUser's
   // own merge defaults a null `active` to `true` (a brand-new user is active unless told
   // otherwise) -- reusing that expression here would silently re-activate every deactivated user
   // whose update entry doesn't happen to mention `active`.
   @Test void mergeUserOmittedActiveKeepsAnAlreadyInactiveUserInactive() {
      IdentityID id = new IdentityID("bob", "host-org");
      SecurityUser current = fullUser(id);
      current.setActive(false);
      IdentitySpec spec = new IdentitySpec();
      spec.setAlias("Bob B"); // touches an unrelated field; active left absent

      SecurityUser merged = IdentityMerge.mergeUser(current, spec, id);

      assertFalse(merged.isActive());
   }

   @Test void mergeUserExplicitActiveFalseDeactivates() {
      IdentityID id = new IdentityID("carol", "host-org");
      SecurityUser current = fullUser(id);
      assertTrue(current.isActive());
      IdentitySpec spec = new IdentitySpec();
      spec.setActive(false);

      SecurityUser merged = IdentityMerge.mergeUser(current, spec, id);

      assertFalse(merged.isActive());
   }

   @Test void mergeUserWithNameBuildsANewIdentityIdKeepingTheSameOrg() {
      IdentityID id = new IdentityID("alice", "host-org");
      SecurityUser current = fullUser(id);
      IdentitySpec spec = new IdentitySpec();
      spec.setName("alice2");

      SecurityUser merged = IdentityMerge.mergeUser(current, spec, id);

      assertEquals(new IdentityID("alice2", "host-org"), merged.getIdentityID());
   }

   @Test void mergeUserWithoutNameKeepsTheCurrentIdentityId() {
      IdentityID id = new IdentityID("alice", "host-org");
      SecurityUser current = fullUser(id);
      IdentitySpec spec = new IdentitySpec();
      spec.setAlias("unchanged name test");

      SecurityUser merged = IdentityMerge.mergeUser(current, spec, id);

      assertEquals(id, merged.getIdentityID());
   }

   @Test void mergeUserRolesConvertsBareNamesUsingTheCurrentOrg() {
      IdentityID id = new IdentityID("alice", "host-org");
      SecurityUser current = fullUser(id);
      IdentitySpec spec = new IdentitySpec();
      spec.setRoles(List.of("Admin"));

      SecurityUser merged = IdentityMerge.mergeUser(current, spec, id);

      assertEquals(List.of(new IdentityID("Admin", "host-org")), merged.getRoles());
   }

   // -------------------------------------------------------------------------
   // mergeGroup
   // -------------------------------------------------------------------------

   @Test void mergeGroupOnlyChangesTheTouchedField() {
      IdentityID id = new IdentityID("Analysts", "host-org");
      SecurityGroup current = fullGroup(id);
      IdentitySpec spec = new IdentitySpec();
      spec.setParentGroups(List.of("Employees"));

      SecurityGroup merged = IdentityMerge.mergeGroup(current, spec, id);

      assertEquals(List.of("Employees"), merged.getParentGroups());
      assertEquals(id, merged.getIdentityID());
      assertEquals(current.getTheme(), merged.getTheme());
      assertEquals(current.getMemberUsers(), merged.getMemberUsers());
      assertEquals(current.getMemberGroups(), merged.getMemberGroups());
      assertEquals(current.getRoles(), merged.getRoles());
   }

   @Test void mergeGroupExplicitEmptyMemberUsersClearsWhileOtherFieldsSurvive() {
      IdentityID id = new IdentityID("Analysts", "host-org");
      SecurityGroup current = fullGroup(id);
      IdentitySpec spec = new IdentitySpec();
      spec.setMemberUsers(List.of());

      SecurityGroup merged = IdentityMerge.mergeGroup(current, spec, id);

      assertEquals(List.of(), merged.getMemberUsers());
      assertEquals(current.getParentGroups(), merged.getParentGroups());
      assertEquals(current.getMemberGroups(), merged.getMemberGroups());
      assertEquals(current.getRoles(), merged.getRoles());
   }

   @Test void mergeGroupWithNameBuildsANewIdentityId() {
      IdentityID id = new IdentityID("Analysts", "host-org");
      SecurityGroup current = fullGroup(id);
      IdentitySpec spec = new IdentitySpec();
      spec.setName("Analysts2");

      SecurityGroup merged = IdentityMerge.mergeGroup(current, spec, id);

      assertEquals(new IdentityID("Analysts2", "host-org"), merged.getIdentityID());
   }

   // -------------------------------------------------------------------------
   // mergeRole
   // -------------------------------------------------------------------------

   @Test void mergeRoleOnlyChangesTheTouchedField() {
      IdentityID id = new IdentityID("Viewer", "host-org");
      SecurityRole current = fullRole(id);
      IdentitySpec spec = new IdentitySpec();
      spec.setDescription("New description");

      SecurityRole merged = IdentityMerge.mergeRole(current, spec, id);

      assertEquals("New description", merged.getDescription());
      assertEquals(id, merged.getIdentityID());
      assertEquals(current.getTheme(), merged.getTheme());
      assertEquals(current.getAssignedUsers(), merged.getAssignedUsers());
      assertEquals(current.getAssignedGroups(), merged.getAssignedGroups());
      assertEquals(current.getInheritedRoles(), merged.getInheritedRoles());
   }

   @Test void mergeRoleExplicitEmptyAssignedUsersClearsWhileOtherFieldsSurvive() {
      IdentityID id = new IdentityID("Viewer", "host-org");
      SecurityRole current = fullRole(id);
      IdentitySpec spec = new IdentitySpec();
      spec.setAssignedUsers(List.of());

      SecurityRole merged = IdentityMerge.mergeRole(current, spec, id);

      assertEquals(List.of(), merged.getAssignedUsers());
      assertEquals(current.getDescription(), merged.getDescription());
      assertEquals(current.getAssignedGroups(), merged.getAssignedGroups());
      assertEquals(current.getInheritedRoles(), merged.getInheritedRoles());
   }

   @Test void mergeRoleInheritedRolesConvertsBareNamesUsingTheCurrentOrg() {
      IdentityID id = new IdentityID("Viewer", "host-org");
      SecurityRole current = fullRole(id);
      IdentitySpec spec = new IdentitySpec();
      spec.setInheritedRoles(List.of("BaseRole"));

      SecurityRole merged = IdentityMerge.mergeRole(current, spec, id);

      assertEquals(List.of(new IdentityID("BaseRole", "host-org")), merged.getInheritedRoles());
   }

   // -------------------------------------------------------------------------
   // mergeOrganization
   // -------------------------------------------------------------------------

   @Test void mergeOrganizationOnlyChangesTheTouchedField() {
      SecurityOrganization current = fullOrganization("org1");
      IdentitySpec spec = new IdentitySpec();
      spec.setOrgName("New Org Name");

      SecurityOrganization merged = IdentityMerge.mergeOrganization(current, spec, "org1");

      assertEquals("New Org Name", merged.getName());
      assertEquals("org1", merged.getId());
      assertEquals(current.getLocale(), merged.getLocale());
      assertEquals(current.getTheme(), merged.getTheme());
      assertEquals(current.getMemberUsers(), merged.getMemberUsers());
      assertEquals(current.getMemberGroups(), merged.getMemberGroups());
      assertEquals(current.getRoles(), merged.getRoles());
   }

   // spec.id is refused at validation before the merge ever runs (section 2.3 item 3) -- this
   // asserts the merge itself never reads it either, as defense in depth: even if spec.getId()
   // somehow carried a value here, the merged id must still be currentOrgId.
   @Test void mergeOrganizationNeverReadsSpecId() {
      SecurityOrganization current = fullOrganization("org1");
      IdentitySpec spec = new IdentitySpec();
      spec.setId("some-other-id");
      spec.setOrgName("New Org Name");

      SecurityOrganization merged = IdentityMerge.mergeOrganization(current, spec, "org1");

      assertEquals("org1", merged.getId());
   }

   @Test void mergeOrganizationMembershipFieldsAreAlwaysCopiedFromCurrentVerbatim() {
      SecurityOrganization current = fullOrganization("org1");
      IdentitySpec spec = new IdentitySpec();
      spec.setLocale("fr_FR");

      SecurityOrganization merged = IdentityMerge.mergeOrganization(current, spec, "org1");

      assertEquals(current.getMemberUsers(), merged.getMemberUsers());
      assertEquals(current.getMemberGroups(), merged.getMemberGroups());
      assertEquals(current.getRoles(), merged.getRoles());
   }

   // -------------------------------------------------------------------------
   // toIdentityIds (moved from IdentityChangesetApplyService, section 3.1)
   // -------------------------------------------------------------------------

   @Test void toIdentityIdsReturnsNullForNullInput() {
      assertNull(IdentityMerge.toIdentityIds(null, "host-org"));
   }

   @Test void toIdentityIdsConvertsEachBareName() {
      assertEquals(List.of(new IdentityID("a", "host-org"), new IdentityID("b", "host-org")),
                  IdentityMerge.toIdentityIds(List.of("a", "b"), "host-org"));
   }

   // -------------------------------------------------------------------------
   // fixtures
   // -------------------------------------------------------------------------

   private static SecurityUser fullUser(IdentityID id) {
      SecurityUser u = new SecurityUser();
      u.setIdentityID(id);
      u.setAlias("Alice A");
      u.setLocale("en_US");
      u.setTheme("dark");
      u.setActive(true);
      u.setEmails(List.of("alice@x.com"));
      u.setGroups(List.of("Analysts"));
      u.setRoles(List.of(new IdentityID("Viewer", id.orgID)));
      return u;
   }

   private static SecurityGroup fullGroup(IdentityID id) {
      SecurityGroup g = new SecurityGroup();
      g.setIdentityID(id);
      g.setTheme("dark");
      g.setParentGroups(List.of("Employees"));
      g.setMemberUsers(List.of("alice"));
      g.setMemberGroups(List.of("SubGroup"));
      g.setRoles(List.of(new IdentityID("Viewer", id.orgID)));
      return g;
   }

   private static SecurityRole fullRole(IdentityID id) {
      SecurityRole r = new SecurityRole();
      r.setIdentityID(id);
      r.setDescription("Existing description");
      r.setTheme("dark");
      r.setAssignedUsers(List.of("alice"));
      r.setAssignedGroups(List.of("Analysts"));
      r.setInheritedRoles(List.of(new IdentityID("BaseRole", id.orgID)));
      return r;
   }

   private static SecurityOrganization fullOrganization(String id) {
      SecurityOrganization o = new SecurityOrganization();
      o.setId(id);
      o.setName("Org One");
      o.setLocale("en_US");
      o.setTheme("dark");
      o.setMemberUsers(List.of("alice"));
      o.setMemberGroups(List.of("Analysts"));
      o.setRoles(List.of("Viewer"));
      return o;
   }
}
