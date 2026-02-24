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

import inetsoft.report.internal.LicenseException;

import java.util.List;

/**
 * Exception thrown when the session limit is exceeded for a site administrator user. Unlike
 * a regular {@link LicenseException}, this exception carries information about the currently
 * active sessions so that the administrator can choose one to terminate.
 */
public class SessionsExceededException extends LicenseException {
   public SessionsExceededException(String message, List<ActiveSessionInfo> activeSessions) {
      super(message);
      this.activeSessions = activeSessions;
   }

   /**
    * Gets the list of currently active sessions.
    */
   public List<ActiveSessionInfo> getActiveSessions() {
      return activeSessions;
   }

   private final List<ActiveSessionInfo> activeSessions;
}