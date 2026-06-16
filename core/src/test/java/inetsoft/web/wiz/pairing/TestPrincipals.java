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

import inetsoft.sree.security.IdentityID;
import inetsoft.uql.XPrincipal;

import java.security.Principal;

final class TestPrincipals {
   /**
    * Builds an XPrincipal with the given name+org without requiring a Spring context.
    * Uses the protected no-arg XPrincipal constructor (designed for Ignite deserialization)
    * and sets the name field directly, matching the IdentityID key format used at runtime.
    */
   static Principal user(String name, String org) {
      return new MinimalXPrincipal(new IdentityID(name, org));
   }

   /** Minimal XPrincipal that sets its name without calling XSessionService. */
   private static final class MinimalXPrincipal extends XPrincipal {
      MinimalXPrincipal(IdentityID identityID) {
         // protected no-arg constructor skips XSessionService (safe for unit tests)
         super();
         this.name = identityID.convertToKey();
      }
   }
}
