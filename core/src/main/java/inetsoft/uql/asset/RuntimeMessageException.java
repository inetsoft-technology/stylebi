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
package inetsoft.uql.asset;

import inetsoft.util.LogException;
import inetsoft.util.log.LogLevel;

import java.util.HashMap;
import java.util.Map;

/**
 * Runtime exception implementation that holds information about how it is to
 * be logged.
 *
 * @author InetSoft Technology
 * @version 8.0
 */
public class RuntimeMessageException extends RuntimeException
   implements LogException
{
   /**
    * Creates a new instance of MessageException. The log level is
    * Level.SEVERE and the stack trace is printed by default.
    *
    * @param message the error message.
    */
   public RuntimeMessageException(String message) {
      this(message, LogLevel.ERROR);
   }

   /**
    * Creates a new instance of MessageException. The stack trace is printed by
    * default.
    *
    * @param message the error message.
    * @param logLevel the logging level at which to print the exception.
    */
   public RuntimeMessageException(String message, LogLevel logLevel) {
      super(message);
      this.logLevel = logLevel;
   }

   /**
    * Gets the logging level at which to print this exception.
    *
    * @return the logging level..
    */
   @Override
   public LogLevel getLogLevel() {
      return logLevel;
   }

   /**
    * Set a named proeprty.
    */
   public void setProperty(String name, String val) {
      props.put(name, val);
   }

   /**
    * Get a named proeprty.
    */
   public String getProperty(String name) {
      return (String) props.get(name);
   }

   private LogLevel logLevel = LogLevel.ERROR;
   private Map props = new HashMap();
}
