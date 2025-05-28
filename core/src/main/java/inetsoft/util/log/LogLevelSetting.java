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
 * Class that represents a custom log level setting.
 *
 * @author InetSoft Technology
 * @since  12.0
 */
public final class LogLevelSetting implements Comparable<LogLevelSetting> {
   public LogLevelSetting() {
   }

   public LogLevelSetting(LogContext context, String name, String orgName, LogLevel level) {
      this.context = context;
      this.name = name;
      this.orgName = orgName;
      this.level = level;
   }

   /**
    * Gets the type of the log context.
    *
    * @return the context type.
    */
   public LogContext getContext() {
      return context;
   }

   /**
    * Sets the type of the log context.
    *
    * @param context the context type.
    */
   public void setContext(LogContext context) {
      this.context = context;
   }

   /**
    * Gets the name of the log context.
    *
    * @return the context name.
    */
   public String getName() {
      return name;
   }

   /**
    * Sets the name of the log context.
    *
    * @param name the context name.
    */
   public void setName(String name) {
      this.name = name;
   }

   /**
    * Get the organization name of log resource.
    *
    * @return the organization name.
    */
   public String getOrgName() {
      return orgName;
   }

   /**
    * Set the organization name of log resource.
    *
    * @param orgName the organization name.
    */
   public void setOrgName(String orgName) {
      this.orgName = orgName;
   }

   /**
    * Gets the log level.
    *
    * @return the level.
    */
   public LogLevel getLevel() {
      return level;
   }

   /**
    * Sets the log level.
    *
    * @param level the level.
    */
   public void setLevel(LogLevel level) {
      this.level = level;
   }

   @Override
   public String toString() {
      return String.format(
         "LogLevelSetting[context=%s,name=%s,organization=%s,level=%s]",
         context.name(), name, orgName, level);
   }

   @Override
   public boolean equals(Object o) {
      if(this == o) {
         return true;
      }

      if(o == null || getClass() != o.getClass()) {
         return false;
      }

      LogLevelSetting that = (LogLevelSetting) o;
      return context == that.context &&
         !(name != null ? !name.equals(that.name) : that.name != null) &&
         !(orgName != null ? !orgName.equals(that.orgName) : that.orgName != null);
   }

   @Override
   public int hashCode() {
      int result = context != null ? context.hashCode() : 0;
      result = 31 * result + (name != null ? name.hashCode() : 0) + (orgName != null ? orgName.hashCode() : 0);
      return result;
   }

   @Override
   public int compareTo(LogLevelSetting o) {
      int result = 0;

      if(context == null && o.context != null) {
         result = -1;
      }
      else if(context != null && o.context == null) {
         result = 1;
      }
      else if(context != null) {
         result = context.compareTo(o.context);
      }

      if(result == 0) {
         if(name == null && o.name != null) {
            result = -1;
         }
         else if(name != null && o.name == null) {
            result = 1;
         }
         else if(name != null) {
            result = name.compareTo(o.name);
         }
      }

      if(result == 0) {
         if(orgName == null && o.orgName != null) {
            result = -1;
         }
         else if(orgName != null && o.orgName == null) {
            result = 1;
         }
         else if(orgName != null) {
            result = orgName.compareTo(o.orgName);
         }
      }

      return result;
   }

   private LogContext context = null;
   private String name = null;
   private String orgName = null;
   private LogLevel level = null;
}
