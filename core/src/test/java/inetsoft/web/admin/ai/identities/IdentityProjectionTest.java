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
