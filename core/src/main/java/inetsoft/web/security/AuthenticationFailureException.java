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
package inetsoft.web.security;

import inetsoft.sree.web.ActiveSessionInfo;

import java.util.List;

/**
 * Exception that signals that an authentication failed.
 */
public class AuthenticationFailureException extends Exception {
   /**
    * Creates a new instance of <tt>AuthenticationFailureException</tt>.
    *
    * @param reason the reason that the authentication failed.
    */
   public AuthenticationFailureException(AuthenticationFailureReason reason) {
      this.reason = reason;
      this.activeSessions = null;
   }

   /**
    * Creates a new instance of <tt>AuthenticationFailureException</tt>.
    *
    * @param reason  the reason that the authentication failed.
    * @param message the error message.
    */
   public AuthenticationFailureException(AuthenticationFailureReason reason,
                                         String message)
   {
      super(message);
      this.reason = reason;
      this.activeSessions = null;
   }

   /**
    * Creates a new instance of <tt>AuthenticationFailureException</tt>.
    *
    * @param reason  the reason that the authentication failed.
    * @param message the error message.
    * @param cause   the cause of the exception.
    */
   public AuthenticationFailureException(AuthenticationFailureReason reason,
                                         String message, Throwable cause)
   {
      super(message, cause);
      this.reason = reason;
      this.activeSessions = null;
   }

   /**
    * Creates a new instance of <tt>AuthenticationFailureException</tt> for the case where a
    * site administrator is denied login because the session limit is exceeded.
    *
    * @param reason         the reason that the authentication failed.
    * @param message        the error message.
    * @param activeSessions the list of currently active sessions.
    */
   public AuthenticationFailureException(AuthenticationFailureReason reason,
                                         String message,
                                         List<ActiveSessionInfo> activeSessions)
   {
      super(message);
      this.reason = reason;
      this.activeSessions = activeSessions;
   }

   /**
    * Gets the reason that the authentication failed.
    *
    * @return the reason.
    */
   public AuthenticationFailureReason getReason() {
      return reason;
   }

   /**
    * Gets the list of currently active sessions. Only non-null when the reason is
    * {@link AuthenticationFailureReason#SESSION_EXCEEDED_ADMIN}.
    */
   public List<ActiveSessionInfo> getActiveSessions() {
      return activeSessions;
   }

   private final AuthenticationFailureReason reason;
   private final List<ActiveSessionInfo> activeSessions;
}
