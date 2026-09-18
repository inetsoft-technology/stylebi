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

import inetsoft.web.admin.security.PropertyModel;
import inetsoft.web.admin.security.SecurityOrganization;
import inetsoft.web.admin.security.SecurityRole;
import inetsoft.web.admin.security.SecurityUser;
import inetsoft.sree.security.IdentityID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Spec section 5 (hash input) / section 9 (password never a literal in the projection). Every
 * list-valued field must be sorted so two calls resolving the same logical state always produce
 * the same string, regardless of the DTO's own list order.
 */
@Tag("core")
class IdentityProjectionTest {
   @Test void projectUserIsStableAcrossListOrder() {
      SecurityUser a = user(List.of("g1", "g2"), List.of(new IdentityID("Role1", "host-org")));
      SecurityUser b = user(List.of("g2", "g1"), List.of(new IdentityID("Role1", "host-org")));

      assertEquals(IdentityProjection.projectUser(a), IdentityProjection.projectUser(b));
   }

   @Test void projectUserChangesWhenGroupsChange() {
      SecurityUser a = user(List.of("g1"), List.of());
      SecurityUser b = user(List.of("g1", "g2"), List.of());

      assertNotEquals(IdentityProjection.projectUser(a), IdentityProjection.projectUser(b));
   }

   @Test void projectUserNeverContainsTheLiteralPasswordValue() {
      // SecurityUser never carries a password on read (getUserModel never sets it) -- but this
      // projection method must not leak it even if a caller mistakenly populated the field.
      SecurityUser u = user(List.of(), List.of());
      u.setPassword("SuperSecret123!");

      String projection = IdentityProjection.projectUser(u);
      assertFalse(projection.contains("SuperSecret123!"));
      assertTrue(projection.contains("pw:set"));
   }

   @Test void projectUserSpecDoesNotChangeWhenPasswordValueChangesButPresenceStays() {
      IdentityID id = new IdentityID("bob", "host-org");
      IdentitySpec a = new IdentitySpec();
      a.setPassword("Password1!AAA");
      IdentitySpec b = new IdentitySpec();
      b.setPassword("DifferentPass2@");

      // Different literal values, same presence -- the projection intentionally cannot see the
      // difference (spec section 9), so the plan hash does not perturb on password VALUE alone.
      assertEquals(IdentityProjection.projectUserSpec(id, a),
                   IdentityProjection.projectUserSpec(id, b));
   }

   @Test void projectUserSpecChangesWhenPasswordPresenceChanges() {
      IdentityID id = new IdentityID("bob", "host-org");
      IdentitySpec withPw = new IdentitySpec();
      withPw.setPassword("Password1!AAA");
      IdentitySpec withoutPw = new IdentitySpec();

      assertNotEquals(IdentityProjection.projectUserSpec(id, withPw),
                      IdentityProjection.projectUserSpec(id, withoutPw));
   }

   @Test void projectUserSpecNeverContainsTheLiteralPasswordValue() {
      IdentityID id = new IdentityID("bob", "host-org");
      IdentitySpec spec = new IdentitySpec();
      spec.setPassword("SuperSecret123!");

      assertFalse(IdentityProjection.projectUserSpec(id, spec).contains("SuperSecret123!"));
   }

   @Test void projectUserSpecDefaultsOmittedActiveToTrue() {
      IdentityID id = new IdentityID("bob", "host-org");
      IdentitySpec spec = new IdentitySpec(); // active left unset (null)

      assertTrue(IdentityProjection.projectUserSpec(id, spec).contains("active=true;"));
   }

   @Test void projectNullDtoIsNull() {
      assertNull(IdentityProjection.projectUser(null));
      assertNull(IdentityProjection.projectGroup(null, new IdentityID("g", "host-org")));
      assertNull(IdentityProjection.projectRole(null, new IdentityID("r", "host-org")));
      assertNull(IdentityProjection.projectOrganization(null, "org1"));
   }

   // bug-76715: defaultRole/sysAdmin must be part of the hash/preview-diff projection, or a plan
   // whose ONLY change is one of these 2 fields would go undetected between preview and apply.

   @Test void projectRoleChangesWhenOnlyDefaultRoleChanges() {
      IdentityID id = new IdentityID("Viewer", "host-org");
      SecurityRole a = role(id, false, false);
      SecurityRole b = role(id, true, false);

      assertNotEquals(IdentityProjection.projectRole(a, id), IdentityProjection.projectRole(b, id));
   }

   @Test void projectRoleChangesWhenOnlySysAdminChanges() {
      IdentityID id = new IdentityID("Viewer", "host-org");
      SecurityRole a = role(id, false, false);
      SecurityRole b = role(id, false, true);

      assertNotEquals(IdentityProjection.projectRole(a, id), IdentityProjection.projectRole(b, id));
   }

   @Test void projectRoleTreatsNullDefaultRoleAndSysAdminAsFalse() {
      IdentityID id = new IdentityID("Viewer", "host-org");
      SecurityRole withNulls = new SecurityRole();
      withNulls.setIdentityID(id);
      SecurityRole withFalse = role(id, false, false);

      assertEquals(IdentityProjection.projectRole(withNulls, id),
                  IdentityProjection.projectRole(withFalse, id));
   }

   @Test void projectRoleSpecChangesWhenOnlyDefaultRoleOrSysAdminChanges() {
      IdentityID id = new IdentityID("Viewer", "host-org");
      IdentitySpec a = new IdentitySpec();
      a.setDefaultRole(false);
      IdentitySpec b = new IdentitySpec();
      b.setDefaultRole(true);

      assertNotEquals(IdentityProjection.projectRoleSpec(id, a),
                      IdentityProjection.projectRoleSpec(id, b));
   }

   // bug-76715: properties must be part of the hash/preview-diff projection too, sorted by name so
   // list order never perturbs the hash.

   @Test void projectOrganizationIsStableAcrossPropertyOrder() {
      SecurityOrganization a = organization(
         List.of(property("b", "2"), property("a", "1")));
      SecurityOrganization b = organization(
         List.of(property("a", "1"), property("b", "2")));

      assertEquals(IdentityProjection.projectOrganization(a, "org1"),
                  IdentityProjection.projectOrganization(b, "org1"));
   }

   @Test void projectOrganizationChangesWhenAPropertyValueChanges() {
      SecurityOrganization a = organization(List.of(property("custom.key", "1")));
      SecurityOrganization b = organization(List.of(property("custom.key", "2")));

      assertNotEquals(IdentityProjection.projectOrganization(a, "org1"),
                      IdentityProjection.projectOrganization(b, "org1"));
   }

   @Test void projectOrganizationSpecChangesWhenPropertiesChange() {
      IdentitySpec a = new IdentitySpec();
      a.setProperties(List.of(property("custom.key", "1")));
      IdentitySpec b = new IdentitySpec();
      b.setProperties(List.of(property("custom.key", "2")));

      assertNotEquals(IdentityProjection.projectOrganizationSpec("org1", a),
                      IdentityProjection.projectOrganizationSpec("org1", b));
   }

   private static SecurityRole role(IdentityID id, boolean defaultRole, boolean sysAdmin) {
      SecurityRole r = new SecurityRole();
      r.setIdentityID(id);
      r.setDefaultRole(defaultRole);
      r.setSysAdmin(sysAdmin);
      return r;
   }

   private static SecurityOrganization organization(List<PropertyModel> properties) {
      SecurityOrganization o = new SecurityOrganization();
      o.setId("org1");
      o.setName("Org One");
      o.setProperties(properties);
      return o;
   }

   private static PropertyModel property(String name, String value) {
      return PropertyModel.builder().name(name).value(value).build();
   }

   private static SecurityUser user(List<String> groups, List<IdentityID> roles) {
      SecurityUser u = new SecurityUser();
      u.setIdentityID(new IdentityID("bob", "host-org"));
      u.setActive(true);
      u.setGroups(groups);
      u.setRoles(roles);
      u.setEmails(List.of());
      return u;
   }
}
