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
package inetsoft.util.log;

/**
 * Enumeration of logging levels that corresponds to the SLF4J levels.
 */
public enum LogLevel {
   /**
    * Corresponds to the SLF4J "debug" level.
    */
   DEBUG("debug"),

   /**
    * Corresponds to the SLF4J "info" level.
    */
   INFO("info"),

   /**
    * Corresponds to the SLF4J "warn" level.
    */
   WARN("warn"),

   /**
    * Corresponds to the SLF4J "error" level.
    */
   ERROR("error"),

   /**
    * Special level indicating that the logger is disabled.
    */
   OFF("off");

   private final String level;

   LogLevel(String level) {
      this.level = level;
   }

   public String level() {
      return level;
   }
}
