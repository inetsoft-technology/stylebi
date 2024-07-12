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
package inetsoft.sree.web;

import inetsoft.sree.security.*;

/**
 * Base class for implementations of <tt>SessionLicenseManager</tt>.
 */
abstract class AbstractSessionService implements SessionLicenseManager, SessionListener {
   /**
    * Creates a new instance of <tt>AbstractSessionService</tt>.
    */
   protected AbstractSessionService() {
      AuthenticationService.getInstance().addSessionListener(this);
   }

   @Override
   public void loggedIn(SessionEvent event) {
      // NO-OP
   }

   @Override
   public void loggedOut(SessionEvent event) {
      if(event.getPrincipal() instanceof SRPrincipal) {
         releaseSession((SRPrincipal) event.getPrincipal());
      }
   }

   @Override
   public void dispose() {
      AuthenticationService.getInstance().removeSessionListener(this);
   }

   @Override
   public void close() throws Exception {
      dispose();
   }
}
