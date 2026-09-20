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
import java.util.Set;

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
      assertEquals(current.getDefaultRole(), merged.getDefaultRole());
      assertEquals(current.getSysAdmin(), merged.getSysAdmin());
      assertEquals(current.getOrgAdmin(), merged.getOrgAdmin());
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

   // bug-76715: the single most important regression test in this whole build (design's own
   // sequencing warning) -- updating a field UNRELATED to defaultRole/sysAdmin on an existing
   // defaultRole:true/sysAdmin:true role must not silently un-default/un-sysAdmin it. Landing the
   // read-path fix (SecurityService.getRoleModel) before this merge fix is what makes
   // current.getDefaultRole()/getSysAdmin() non-null here in the first place.
   @Test void mergeRoleOmittedDefaultRoleAndSysAdminSurviveAnUnrelatedFieldUpdate() {
      IdentityID id = new IdentityID("Viewer", "host-org");
      SecurityRole current = fullRole(id);
      current.setDefaultRole(true);
      current.setSysAdmin(true);
      IdentitySpec spec = new IdentitySpec();
      spec.setDescription("New description"); // unrelated field; defaultRole/sysAdmin left absent

      SecurityRole merged = IdentityMerge.mergeRole(current, spec, id);

      assertEquals(Boolean.TRUE, merged.getDefaultRole());
      assertEquals(Boolean.TRUE, merged.getSysAdmin());
   }

   @Test void mergeRoleExplicitDefaultRoleAndSysAdminOverrideCurrent() {
      IdentityID id = new IdentityID("Viewer", "host-org");
      SecurityRole current = fullRole(id);
      current.setDefaultRole(true);
      current.setSysAdmin(true);
      IdentitySpec spec = new IdentitySpec();
      spec.setDefaultRole(false);
      spec.setSysAdmin(false);

      SecurityRole merged = IdentityMerge.mergeRole(current, spec, id);

      assertEquals(Boolean.FALSE, merged.getDefaultRole());
      assertEquals(Boolean.FALSE, merged.getSysAdmin());
   }

   // bug-76824: orgAdmin needs the same current.getOrgAdmin()-populated-first sequencing as
   // defaultRole/sysAdmin above -- an unrelated update must not silently un-org-admin an existing
   // orgAdmin:true role.
   @Test void mergeRoleOmittedOrgAdminSurvivesAnUnrelatedFieldUpdate() {
      IdentityID id = new IdentityID("Viewer", "host-org");
      SecurityRole current = fullRole(id);
      current.setOrgAdmin(true);
      IdentitySpec spec = new IdentitySpec();
      spec.setDescription("New description"); // unrelated field; orgAdmin left absent

      SecurityRole merged = IdentityMerge.mergeRole(current, spec, id);

      assertEquals(Boolean.TRUE, merged.getOrgAdmin());
   }

   @Test void mergeRoleExplicitOrgAdminOverridesCurrent() {
      IdentityID id = new IdentityID("Viewer", "host-org");
      SecurityRole current = fullRole(id);
      current.setOrgAdmin(true);
      IdentitySpec spec = new IdentitySpec();
      spec.setOrgAdmin(false);

      SecurityRole merged = IdentityMerge.mergeRole(current, spec, id);

      assertEquals(Boolean.FALSE, merged.getOrgAdmin());
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

   // bug-76834: organization id rename is a real, supported capability -- reserved-id/duplicate-id
   // validated by IdentityChangePlanService.resolveUpdateOrganization before this ever runs, so the
   // merge itself just needs to prefer spec.getId() when present, mirroring mergeUser/mergeGroup/
   // mergeRole's own renamedOrCurrent pattern for their name field. Renamed from
   // mergeOrganizationNeverReadsSpecId, which asserted the opposite, now-removed invariant.
   @Test void mergeOrganizationPrefersSpecIdWhenPresent() {
      SecurityOrganization current = fullOrganization("org1");
      IdentitySpec spec = new IdentitySpec();
      spec.setId("org2");
      spec.setOrgName("New Org Name");

      SecurityOrganization merged = IdentityMerge.mergeOrganization(current, spec, "org1");

      assertEquals("org2", merged.getId());
   }

   @Test void mergeOrganizationFallsBackToCurrentOrgIdWhenSpecIdIsAbsentOrBlank() {
      SecurityOrganization current = fullOrganization("org1");
      IdentitySpec spec = new IdentitySpec();
      spec.setOrgName("New Org Name");

      assertEquals("org1", IdentityMerge.mergeOrganization(current, spec, "org1").getId());

      spec.setId("   ");
      assertEquals("org1", IdentityMerge.mergeOrganization(current, spec, "org1").getId());
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

   @Test void mergeOrganizationOmittedPropertiesKeepsCurrentPropertiesUntouched() {
      SecurityOrganization current = fullOrganization("org1");
      current.setProperties(List.of(property("custom.key", "custom-value")));
      IdentitySpec spec = new IdentitySpec();
      spec.setLocale("fr_FR"); // unrelated field; properties left absent

      SecurityOrganization merged = IdentityMerge.mergeOrganization(current, spec, "org1");

      assertEquals(current.getProperties(), merged.getProperties());
   }

   @Test void mergeOrganizationExplicitPropertiesDeduplicatesByLastWriteWinsPerName() {
      SecurityOrganization current = fullOrganization("org1");
      IdentitySpec spec = new IdentitySpec();
      spec.setProperties(List.of(property("custom.key", "first"), property("custom.key", "second")));

      SecurityOrganization merged = IdentityMerge.mergeOrganization(current, spec, "org1");

      assertEquals(List.of(property("custom.key", "second")), merged.getProperties());
   }

   // bug-76715 human decision 4: wiz's identities path must NOT replicate EM's own 4-named-quota-
   // key clear-on-omission asymmetry -- an update that touches an unrelated property must not
   // silently clear a previously-set quota key it never mentioned.
   @Test void mergeOrganizationPropertiesReIncludesOmittedQuotaKeysFromCurrent() {
      SecurityOrganization current = fullOrganization("org1");
      current.setProperties(List.of(property("max.row.count", "1000"), property("other", "x")));
      IdentitySpec spec = new IdentitySpec();
      // Caller lists only "other" with a new value; quota key max.row.count is unmentioned.
      spec.setProperties(List.of(property("other", "y")));

      SecurityOrganization merged = IdentityMerge.mergeOrganization(current, spec, "org1");

      assertEquals(
         Set.of(property("other", "y"), property("max.row.count", "1000")),
         Set.copyOf(merged.getProperties()));
   }

   @Test void mergeOrganizationPropertiesExplicitQuotaKeyOverridesCurrent() {
      SecurityOrganization current = fullOrganization("org1");
      current.setProperties(List.of(property("max.row.count", "1000")));
      IdentitySpec spec = new IdentitySpec();
      spec.setProperties(List.of(property("max.row.count", "2000")));

      SecurityOrganization merged = IdentityMerge.mergeOrganization(current, spec, "org1");

      assertEquals(List.of(property("max.row.count", "2000")), merged.getProperties());
   }

   @Test void mergeOrganizationExplicitEmptyPropertiesClearsNonQuotaProperties() {
      SecurityOrganization current = fullOrganization("org1");
      current.setProperties(List.of(property("custom.key", "value")));
      IdentitySpec spec = new IdentitySpec();
      spec.setProperties(List.of());

      SecurityOrganization merged = IdentityMerge.mergeOrganization(current, spec, "org1");

      assertEquals(List.of(), merged.getProperties());
   }

   // Decision 4's quota-key protection is not carved out for the empty-list case: even an explicit
   // "clear everything" properties:[] must not silently drop a previously-set quota key the caller
   // never named -- the whole point is that a quota key is only ever cleared by naming it.
   @Test void mergeOrganizationExplicitEmptyPropertiesStillReIncludesQuotaKeys() {
      SecurityOrganization current = fullOrganization("org1");
      current.setProperties(List.of(property("max.row.count", "1000")));
      IdentitySpec spec = new IdentitySpec();
      spec.setProperties(List.of());

      SecurityOrganization merged = IdentityMerge.mergeOrganization(current, spec, "org1");

      assertEquals(List.of(property("max.row.count", "1000")), merged.getProperties());
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

   private static PropertyModel property(String name, String value) {
      return PropertyModel.builder().name(name).value(value).build();
   }
}
