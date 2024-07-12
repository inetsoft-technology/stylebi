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
package inetsoft.util;

import inetsoft.uql.asset.ConfirmException;
import inetsoft.util.log.LogLevel;

/**
 * UserMessage contains a message and a level that is one of:
 * {@link ConfirmException#OK}
 * {@link ConfirmException#TRACE}
 * {@link ConfirmException#DEBUG}
 * {@link ConfirmException#INFO}
 * {@link ConfirmException#WARNING}
 * {@link ConfirmException#ERROR}
 * {@link ConfirmException#CONFIRM}
 * {@link ConfirmException#PROGRESS}
 * {@link ConfirmException#OVERRIDE}
 */
public class UserMessage {
   public UserMessage(String message, int level) {
      this.message = message;
      this.level = level;
   }

   public UserMessage(String message, int level, String assemblyName) {
      this.message = message;
      this.level = level;
      this.assemblyName = assemblyName;
   }

   /**
    * Convert ConfirmException/MessageCommand level to LogLevel
    */
   public static LogLevel levelToLogLevel(int level) {
      final LogLevel logLevel;
      switch(level) {
         case ConfirmException.TRACE:
         case ConfirmException.DEBUG:
            logLevel = LogLevel.DEBUG;
            break;
         case ConfirmException.OK:
         case ConfirmException.INFO:
         case ConfirmException.CONFIRM:
         case ConfirmException.PROGRESS:
         case ConfirmException.OVERRIDE:
            logLevel = LogLevel.INFO;
            break;
         case ConfirmException.WARNING:
            logLevel = LogLevel.WARN;
            break;
         case ConfirmException.ERROR:
            logLevel = LogLevel.ERROR;
            break;
         default:
            logLevel = LogLevel.OFF;
      }

      return logLevel;
   }

   public LogLevel getLogLevel() {
      return levelToLogLevel(level);
   }

   /**
    * Merge this UserMessage and another UserMessage into a new UserMessage with their
    * messages separated by a newline character and the higher priority of the two levels
    *
    * @param userMessage the UserMessage to append to this UserMessage
    *
    * @return a new UserMessage or this if the other message is invalid
    */
   public UserMessage merge(UserMessage userMessage) {
      if(userMessage != null) {
         final String message = userMessage.getMessage();

         if(message != null && !this.message.contains(message)) {
            final String newMessage = String.format("%s\n%s", message, this.message);
            final int newLevel = Math.max(this.level, userMessage.level);
            return new UserMessage(newMessage, newLevel);
         }
      }

      return this;
   }

   public String getMessage() {
      return message;
   }

   public int getLevel() {
      return level;
   }

   public String getAssemblyName() {
      return assemblyName;
   }

   public void setAssemblyName(String assemblyName) {
      this.assemblyName = assemblyName;
   }

   @Override
   public String toString() {
      return "UserMessage(" + message + "," + level + "," + assemblyName + ")";
   }

   private final String message;
   private final int level;
   private String assemblyName;
}
