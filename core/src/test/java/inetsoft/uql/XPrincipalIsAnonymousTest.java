/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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
package inetsoft.uql;

import inetsoft.sree.security.IdentityID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77341: {@link Principal#getName()} returns the identity key (name~;~orgID), so
 * comparing it to {@link XPrincipal#ANONYMOUS} is always false. {@link XPrincipal#isAnonymous}
 * compares the user name part of the key.
 */
@Tag("core")
class XPrincipalIsAnonymousTest {
   @Test
   void anonymousKeyIsAnonymous() {
      assertTrue(XPrincipal.isAnonymous(
         principal(new IdentityID(XPrincipal.ANONYMOUS, "host-org").convertToKey())));
   }

   @Test
   void anonymousGlobalKeyIsAnonymous() {
      assertTrue(XPrincipal.isAnonymous(
         principal(new IdentityID(XPrincipal.ANONYMOUS, null).convertToKey())));
   }

   @Test
   void plainAnonymousNameIsAnonymous() {
      assertTrue(XPrincipal.isAnonymous(principal(XPrincipal.ANONYMOUS)));
   }

   @Test
   void userNamePrefixedWithAnonymousIsNotAnonymous() {
      assertFalse(XPrincipal.isAnonymous(
         principal(new IdentityID("anonymousbob", "host-org").convertToKey())));
      assertFalse(XPrincipal.isAnonymous(principal("anonymousbob")));
   }

   @Test
   void otherUserIsNotAnonymous() {
      assertFalse(XPrincipal.isAnonymous(
         principal(new IdentityID("alice", "host-org").convertToKey())));
      // anonymous as the organization id must not count
      assertFalse(XPrincipal.isAnonymous(
         principal(new IdentityID("alice", XPrincipal.ANONYMOUS).convertToKey())));
   }

   @Test
   void nullOrEmptyIsNotAnonymous() {
      assertFalse(XPrincipal.isAnonymous(null));
      assertFalse(XPrincipal.isAnonymous(principal(null)));
      assertFalse(XPrincipal.isAnonymous(principal("")));
   }

   private static Principal principal(String name) {
      return () -> name;
   }
}
