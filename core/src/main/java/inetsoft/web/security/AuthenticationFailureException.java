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
package inetsoft.web.security;

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
   }

   /**
    * Gets the reason that the authentication failed.
    *
    * @return the reason.
    */
   public AuthenticationFailureReason getReason() {
      return reason;
   }

   private final AuthenticationFailureReason reason;
}
