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

/** Shared pairing utilities. */
public final class PairingUtil {
   private PairingUtil() {}

   /**
    * Returns true iff the agentUser's logical identity (IdentityID = name+org)
    * matches the ownerIdentity string (IdentityID key format: "name~;~org").
    */
   public static boolean sameLogicalUser(String ownerIdentity, Principal agentUser) {
      // Fail closed: a non-XPrincipal agent carries no IdentityID key to compare (its plain name
      // lacks the "name~;~org" form ownerIdentity uses), so we cannot prove a match and must reject
      // rather than risk a false positive. Not reachable in practice — the join flow's agent is
      // always the XPrincipal rebuilt from the JWT by WizServiceAuthenticationFilter.
      if (!(agentUser instanceof XPrincipal p)) return false;
      IdentityID agentId = IdentityID.getIdentityIDFromKey(p.getName());
      return ownerIdentity.equals(agentId == null ? p.getName() : agentId.convertToKey());
   }

   /**
    * Returns true iff two principals resolve to the same logical identity (IdentityID = name+org).
    *
    * <p>Compares logical identity rather than {@code Principal.equals}, because the two principals
    * legitimately differ as objects: the runtime owner is the browser-session {@code SRPrincipal},
    * while the agent principal is rebuilt from the JWT by {@code WizServiceAuthenticationFilter}
    * (different {@code ClientInfo}/secureId). {@code SRPrincipal.equals} would therefore reject the
    * real owner; the name+org comparison is what tolerates that.
    */
   public static boolean sameLogicalUser(Principal owner, Principal agentUser) {
      String ownerKey = identityKey(owner);
      return ownerKey != null && sameLogicalUser(ownerKey, agentUser);
   }

   /** The IdentityID key (name+org) for a principal, or null if it cannot be determined. */
   public static String identityKey(Principal user) {
      if (!(user instanceof XPrincipal p)) {
         return user == null ? null : user.getName();
      }

      IdentityID id = IdentityID.getIdentityIDFromKey(p.getName());
      return id == null ? p.getName() : id.convertToKey();
   }
}
