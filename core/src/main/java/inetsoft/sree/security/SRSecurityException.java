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

/**
 * Generate security excption
 * 
 * @author InetSoft Technology Corp.
 * @version 5.1, 9/20/2003
 */
public class SRSecurityException extends Exception {
   /**
    * Constructs an <code>SRSecurityException</code> with <code>null</code>
    * as its error detail message.
    */
   public SRSecurityException() {
      super();
   }

   /**
    * Constructs an <code>SRSecurityException</code> with the specified detail
    * message. The error message string <code>msg</code> can later be
    * retrieved by the <code>{@link java.lang.Throwable#getMessage}</code>
    * method of class <code>java.lang.Throwable</code>.
    *
    * @param  msg   the detail message.
    */
   public SRSecurityException(String msg) {
      super(msg);
   }

   /**
    * Constructs an <code>SRSecurityException</code> with the specified detail
    * message. The error message string <code>msg</code> can later be
    * retrieved by the <code>{@link java.lang.Throwable#getMessage}</code>
    * method of class <code>java.lang.Throwable</code>.
    *
    * @param message the detail message.
    * @param cause   the root cause.
    */
   public SRSecurityException(String message, Throwable cause) {
      super(message, cause);
   }
}

