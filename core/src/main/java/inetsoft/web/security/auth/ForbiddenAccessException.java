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
package inetsoft.web.security.auth;

/**
 * {@code ForbiddenAccessException} indicates that an authenticated caller was refused because the
 * request names or targets an organization that the caller may not use. The public API answers it
 * with HTTP 403, unlike the plain {@link UnauthorizedAccessException}, which stays 401. The
 * message is for the log only and is never returned to the client.
 *
 * @hidden
 */
public class ForbiddenAccessException extends UnauthorizedAccessException {
   public ForbiddenAccessException(String message) {
      super(message);
   }
}
