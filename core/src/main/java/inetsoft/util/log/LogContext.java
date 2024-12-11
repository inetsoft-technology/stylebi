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
package inetsoft.util.log;

/**
 * Enumeration of the categories of context-specific logging.
 *
 * @author InetSoft Techology
 * @since  12.0
 */
public enum LogContext {
   /**
    * Indicates that the logging context refers to a user.
    */
   USER(null),

   /**
    * Indicates that the logging context refers to a group.
    */
   GROUP(null),

   /**
    * Indicates that the logging context refers to a role.
    */
   ROLE(null),

   /**
    * Indicates that the logging context refers to a report.
    */
   REPORT("report:"),

   /**
    * Indicates that the logging context refers to a viewsheet.
    */
   DASHBOARD("view:"),

   /**
    * Indicates that the logging context refers to a query.
    */
   QUERY("query:"),

   /**
    * Indicates that the logging context refers to a model.
    */
   MODEL("model:"),

   /**
    * Indicates that the logging context refers to a worksheet.
    */
   WORKSHEET("worksheet:"),

   /**
    * Indicates that the logging context refers to a schedule task.
    */
   SCHEDULE_TASK("ScheduleTask:"),

   /**
    * Indicates that the logging context refers to a log category (logger).
    */
   CATEGORY(null),

   /**
    * Indicates that the logging context refers to a viewsheet assembly.
    */
   ASSEMBLY("assembly:"),

   /**
    * Indicates that the logging context refers to a worksheet table assembly.
    */
   TABLE("table:"),

   /**
    * Indicates that the logging context refers to an organization.
    */
   ORGANIZATION("organization:");

   private final String prefix;

   LogContext(String prefix) {
      this.prefix = prefix;
   }

   public String getPrefix() {
      return this.prefix;
   }

   public String getRecord(String name) {
      return prefix + name;
   }

   public String getRecordName(Object record) {
      return String.valueOf(record).substring(prefix.length());
   }

   public static LogContext findMatchingContext(Object record) {
      if(record instanceof String) {
         String recordString = (String) record;

         for(LogContext context : values()) {
            if(context.prefix != null && recordString.startsWith(context.prefix)) {
               return context;
            }
         }
      }

      return null;
   }
}
