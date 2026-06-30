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
}
