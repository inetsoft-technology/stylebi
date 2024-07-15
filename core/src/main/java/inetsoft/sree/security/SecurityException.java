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
 * Generate security excption
 * 
 * @author InetSoft Technology Corp.
 * @version 5.1, 9/20/2003
 */
public class SecurityException extends Exception {
   /**
    * Constructs an <code>SecurityException</code> with <code>null</code>
    * as its error detail message.
    */
   public SecurityException() {
      super();
   }

   /**
    * Constructs an <code>SecurityException</code> with the specified detail
    * message. The error message string <code>msg</code> can later be
    * retrieved by the <code>{@link java.lang.Throwable#getMessage}</code>
    * method of class <code>java.lang.Throwable</code>.
    *
    * @param  msg   the detail message.
    */
   public SecurityException(String msg) {
      super(msg);
   }
}

