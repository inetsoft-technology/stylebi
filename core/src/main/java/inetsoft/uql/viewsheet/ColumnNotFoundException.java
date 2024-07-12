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
package inetsoft.uql.viewsheet;

import inetsoft.util.LogException;
import inetsoft.util.log.LogLevel;

import java.io.Serializable;

/**
 * Column not found exception.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public class ColumnNotFoundException extends RuntimeException
   implements LogException, Serializable
{
   /**
    * Constructor.
    */
   public ColumnNotFoundException() {
      super();
   }

   /**
    * Constructor.
    */
   public ColumnNotFoundException(String message) {
      super(message);
   }

   /**
    * Constructor.
    */
   public ColumnNotFoundException(String message, Throwable cause) {
      super(message);
      this.cause = cause;
   }

   /**
    * Constructor.
    */
   public ColumnNotFoundException(Throwable cause) {
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
