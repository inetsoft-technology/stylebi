/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.sree.security;

import java.security.Principal;
import java.util.EventObject;

/**
 * Event that signals that a user has logged into or out of the application.
 *
 * @since 12.3
 */
public class SessionEvent extends EventObject {
   /**
    * Creates a new instance of <tt>SessionEvent</tt>.
    *
    * @param source    the source of the event.
    * @param principal the principal identifying the user that logged in or out.
    */
   public SessionEvent(Object source, Principal principal) {
      this(source, principal, false);
   }

   /**
    * Creates a new instance of <tt>SessionEvent</tt>.
    *
    * @param source            the source of the event.
    * @param principal         the principal identifying the user that logged in or out.
    * @param invalidateSession {@code true} if the session should be invalidated; {@code false}
    *                          otherwise.
    */
   public SessionEvent(Object source, Principal principal, boolean invalidateSession) {
      super(source);
      this.principal = principal;
      this.invalidateSession = invalidateSession;
   }

   /**
    * Gets the principal that identifies the user that logged in or out.
    *
    * @return the principal.
    */
   public Principal getPrincipal() {
      return principal;
   }

   /**
    * Gets a flag indicating if the HTTP session should be invalidated.
    *
    * @return {@code true} if the session should be invalidated; {@code false} otherwise.
    */
   public boolean isInvalidateSession() {
      return invalidateSession;
   }

   private final Principal principal;
   private final boolean invalidateSession;
}
