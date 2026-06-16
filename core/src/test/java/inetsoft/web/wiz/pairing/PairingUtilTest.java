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
package inetsoft.web.wiz.pairing;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for PairingUtil.sameLogicalUser.
 *
 * [Match]          ownerIdentity matches principal's IdentityID key
 * [NoMatch: user]  different user name -> false
 * [NoMatch: org]   different org -> false
 * [NoMatch: null]  non-XPrincipal principal -> false
 */
@Tag("core")
class PairingUtilTest {

   @Test
   void matchingUserReturnsTrue() {
      Principal p = TestPrincipals.user("alice", "defaultOrg");
      // IdentityID("alice","defaultOrg").convertToKey() = "alice~;~defaultOrg"
      assertTrue(PairingUtil.sameLogicalUser("alice~;~defaultOrg", p));
   }

   @Test
   void differentUserReturnsFalse() {
      Principal p = TestPrincipals.user("alice", "defaultOrg");
      assertFalse(PairingUtil.sameLogicalUser("mallory~;~defaultOrg", p));
   }

   @Test
   void differentOrgReturnsFalse() {
      Principal p = TestPrincipals.user("alice", "orgA");
      assertFalse(PairingUtil.sameLogicalUser("alice~;~orgB", p));
   }

   @Test
   void nonXPrincipalReturnsFalse() {
      Principal p = () -> "alice~;~defaultOrg";
      assertFalse(PairingUtil.sameLogicalUser("alice~;~defaultOrg", p));
   }
}
