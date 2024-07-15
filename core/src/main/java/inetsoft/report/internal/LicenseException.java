/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.report.internal;

import inetsoft.sree.SreeEnv;

/**
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class LicenseException extends RuntimeException {
   /**
    * Constructs an exception class 
    * with the specified detail message. 
    *
    * @param message the detail message.
    */
   public LicenseException(String message) {
      this(message, null);
   }

   /**
    * Creates a new instance of <tt>LicenseException</tt>.
    *
    * @param message          the error message.
    * @param localizedMessage the localized error message.
    */
   public LicenseException(String message, String localizedMessage) {
      super(message);
      this.localizedMessage = localizedMessage;
   }
   
   @Override
   public void printStackTrace() {
      if(isLoggable()) {
         super.printStackTrace();
      }
      else {
         System.err.println(this.toString());
      }
   }

   @Override
   public void printStackTrace(java.io.PrintStream s) {
      if(isLoggable()) {
         super.printStackTrace(s);
      }
      else {
         s.println(this);
      }
   }

   @Override
   public void printStackTrace(java.io.PrintWriter s) {
      if(isLoggable()) {
         super.printStackTrace(s);
      }
      else {
         s.println(this);
      }
   }

   @Override
   public StackTraceElement[] getStackTrace() {
      StackTraceElement[] result;

      if(isLoggable()) {
         result = super.getStackTrace();
      }
      else {
         result = new StackTraceElement[0];
      }

      return result;
   }

   private boolean isLoggable() {
      if(loggable == null) {
         // @by jasonshobe, 1/4/2017, I don't think it is necessary to obfuscate this any
         // more than this. If some one is capable of decompiling the code and finding
         // this property, they are capable of removing the overridden methods anyway. We
         // just won't make this property public--security through obscurity.
         loggable = "true".equals(SreeEnv.getProperty("show.license.exception"));
      }

      return loggable;
   }

   @Override
   public String getLocalizedMessage() {
      return localizedMessage == null ?
         super.getLocalizedMessage() : localizedMessage;
   }

   @Override
   public String toString() {
      String s = getClass().getName();
      String message = getMessage();
      return (message != null) ? (s + ": " + message) : s;
   }

   private final String localizedMessage;
   private Boolean loggable;
}

