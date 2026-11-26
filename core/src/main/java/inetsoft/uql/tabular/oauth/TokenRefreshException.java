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
package inetsoft.uql.tabular.oauth;

/**
 * Thrown when the access token of a data source could not be refreshed. A failed refresh must not
 * be swallowed (Bug #82260), the query would go on with the stale access token and fail with a
 * misleading authentication error.
 */
public class TokenRefreshException extends RuntimeException {
   public TokenRefreshException(String dataSourceName, Exception cause) {
      super(createMessage(dataSourceName, cause), cause);
   }

   private static String createMessage(String dataSourceName, Exception cause) {
      final StringBuilder message = new StringBuilder(
         "Failed to refresh the access token of data source '" + dataSourceName + "'");
      final String detail = cause.getMessage();

      if(detail != null && !detail.isBlank()) {
         message.append(": ").append(detail.strip().replaceAll("[.]+$", ""));
      }

      message.append(". Please authorize the data source again.");
      return message.toString();
   }
}
