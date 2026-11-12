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

import java.security.Principal;

public class SessionLoggedOutEvent extends SessionEvent {
   public SessionLoggedOutEvent(Object source, Principal principal) {
      super(source, principal);
      this.endReason = null;
   }

   public SessionLoggedOutEvent(Object source, Principal principal, boolean invalidateSession) {
      super(source, principal, invalidateSession);
      this.endReason = null;
   }

   /**
    * Creates a logout event that tells the user's browser why the session ended.
    *
    * @param source            the source of the event.
    * @param principal         the principal that is logged out.
    * @param invalidateSession {@code true} to invalidate the HTTP session.
    * @param endReason         one of the fixed {@code REASON_*} codes, or {@code null}.
    */
   public SessionLoggedOutEvent(Object source, Principal principal, boolean invalidateSession,
                                String endReason)
   {
      super(source, principal, invalidateSession);
      this.endReason = endReason;
   }

   /**
    * Gets the reason the session ended, to be passed to the browser.
    *
    * @return one of the {@code REASON_*} codes, or {@code null} for no specific reason.
    */
   public String getEndReason() {
      return endReason;
   }

   /**
    * End reason: the user was signed out because the per-client session limit
    * ({@code http.session.maxPerClient}) was reached by a newer login.
    */
   public static final String REASON_LIMIT = "limit";

   private final String endReason;
}
