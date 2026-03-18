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
package inetsoft.sree.security;

/**
 * Utility for reading required administrator credentials from the environment.
 */
public final class AdminCredentialUtil {
   private AdminCredentialUtil() {
   }

   /**
    * Returns the administrator password from the {@code INETSOFT_ADMIN_PASSWORD} environment
    * variable. Throws {@link IllegalStateException} if the variable is not set or is blank.
    */
   public static String getRequiredAdminPassword() {
      String password = System.getenv("INETSOFT_ADMIN_PASSWORD");

      if(password == null || password.isBlank()) {
         throw new IllegalStateException(
            "The INETSOFT_ADMIN_PASSWORD environment variable must be set before starting the server.");
      }

      return password.trim();
   }
}
