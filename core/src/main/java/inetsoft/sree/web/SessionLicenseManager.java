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
package inetsoft.sree.web;

import inetsoft.sree.security.SRPrincipal;
import inetsoft.util.SingletonManager;

import java.util.Set;

@SingletonManager.Singleton(SessionLicenseService.Reference.class)
public interface SessionLicenseManager extends AutoCloseable {
   /**
    * Acquires a new session license from the session license pool for the given
    * user principal.
    * @param srPrincipal The user principal
    */
   void newSession(SRPrincipal srPrincipal);

   /**
    * Releases the session associated with the given principal so that it can
    * be reused,
    * @param srPrincipal The user principal.
    */
   void releaseSession(SRPrincipal srPrincipal);

   Set<SRPrincipal> getActiveSessions();

   /**
    * Clean up any resources used by this manager.
    */
   void dispose();
}
