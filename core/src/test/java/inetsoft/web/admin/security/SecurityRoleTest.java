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

import inetsoft.sree.security.IdentityID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Tag("core")
class SecurityRoleTest {
   // Bug #77894
   @Test
   void assigneeOrganizations_inEqualsHashCodeAndToString() {
      SecurityRole a = role(List.of("org1"));
      SecurityRole b = role(List.of("org1"));
      SecurityRole c = role(List.of("org2"));

      assertEquals(a, b);
      assertEquals(a.hashCode(), b.hashCode());
      assertNotEquals(a, c);
      assertNotEquals(a.hashCode(), c.hashCode());
      assertTrue(a.toString().contains("assigneeOrganizations=[org1]"), a.toString());
   }

   @Test
   void nullAssigneeOrganizations_matchesAnyAsOtherFieldsDo() {
      assertEquals(role(null), role(List.of("org1")));
   }

   private static SecurityRole role(List<String> orgs) {
      SecurityRole role = new SecurityRole();
      role.setIdentityID(new IdentityID("gRole", null));
      role.setAssignedUsers(List.of("u1~;~org1"));
      role.setAssigneeOrganizations(orgs);
      return role;
   }
}
