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
package inetsoft.sree;

import inetsoft.util.LogException;
import inetsoft.util.log.LogLevel;

/**
 * Report generation exception.
 *
 * @version 3.0, 5/10/2000
 * @author InetSoft Technology Corp
 */
public class RepletException extends Exception implements LogException,
   java.io.Serializable
{
   /**
    * Create a replet exception with a message.
    */
   public RepletException(String msg) {
      super(msg);
   }

   /**
    * Create a replet exception with a root cause exception.
    */
   public RepletException(Throwable ex) {
      super(ex);
   }

   /**
    * Create a replet exception with a message and a root cause exception.
    */
   public RepletException(String message, Throwable cause) {
      super(message, cause);
   }

   /**
    * Create a replet exception with user info.
    */
   public RepletException(Object info) {
      super();
      this.info = info;
   }

   /**
    * If this exception is a wrapper of another exception, get the wrapped
    * exception.
    */
   public Throwable getThrowable() {
      return getCause();
   }

   /**
    * Get the log level.
    * @return the log level.
    */
   @Override
   public LogLevel getLogLevel() {
      return level;
   }

   /**
    * Set the log level.
    * @param level the specified log level.
    */
   public void setLogLevel(LogLevel level) {
      this.level = level;
   }

   /**
    * Set the user info.
    */
   public void setInfo(Object info) {
      this.info = info;
   }

   /**
    * Get the user info.
    */
   public Object getInfo() {
      return info;
   }

   private Object info = null;
   private LogLevel level = LogLevel.ERROR;
}
