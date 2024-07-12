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
package inetsoft.util;

import inetsoft.util.log.LogLevel;

import java.security.Principal;

public class InvalidUserException extends MessageException {
   public InvalidUserException(String message, Principal user) {
      super(message);
      this.user = user;
   }

   public InvalidUserException(String message, LogLevel logLevel, boolean dumpStack,
                               Principal user)
   {
      super(message, logLevel, dumpStack);
      this.user = user;
   }

   public Principal getUser() {
      return user;
   }

   public void setUser(Principal user) {
      this.user = user;
   }

   private Principal user;
}