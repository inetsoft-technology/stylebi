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
package inetsoft.uql.asset;

import inetsoft.util.LogException;
import inetsoft.util.log.LogLevel;

import java.io.Serializable;

/**
 * Invalid dependency exception.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public class InvalidDependencyException extends Exception
   implements LogException, Serializable
{
   /**
    * Constructor.
    */
   public InvalidDependencyException() {
      super();
   }

   /**
    * Constructor.
    */
   public InvalidDependencyException(String message) {
      super(message);
   }

   /**
    * Constructor.
    */
   public InvalidDependencyException(String message, Throwable cause) {
      super(message);
      this.cause = cause;
   }

   /**
    * Constructor.
    */
   public InvalidDependencyException(Throwable cause) {
      super(cause.getMessage());
      this.cause = cause;
   }

   /**
    * Set the exception log level.
    * @param level the specified log level.
    */
   public void setLogLevel(LogLevel level) {
      this.level = level;
   }

   /**
    * Get the log level of the exception.
    * @return the log level.
    */
   @Override
   public LogLevel getLogLevel() {
      return level;
   }

   /**
    * Get wrapped throwable if any.
    */
   public Throwable getThrowable() {
      return cause;
   }
   
   private Throwable cause = null;
   private LogLevel level = LogLevel.INFO;
}
