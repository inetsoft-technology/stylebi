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
package inetsoft.web.admin.content.database;

/**
 * Bean that represents driver availability
 */
public class DriverAvailability {
   /**
    * Get if odbc datasources are available.
    * @return  odbc availability
    */
   public boolean isOdbcAvailable() {
      return odbcAvailable;
   }

   /**
    * Sets whether odbc data sources are available.
    * @param odbcAvailable odbc availability
    */
   public void setOdbcAvailable(boolean odbcAvailable) {
      this.odbcAvailable = odbcAvailable;
   }

   /**
    * Gets an array of odbc data sources
    *
    * @return odbc data source names
    */
   public String[] getOdbcDataSources() {
      return odbcDataSources;
   }

   /**
    * Sets the odbc data sources
    *
    * @param odbcDataSources data source names
    */
   public void setOdbcDataSources(String[] odbcDataSources) {
      this.odbcDataSources = odbcDataSources;
   }

   /**
    * Gets the driver information.
    *
    * @return the drivers.
    */
   public DriverInfo[] getDrivers() {
      return drivers;
   }

   /**
    * Sets the driver information.
    *
    * @param drivers the drivers.
    */
   public void setDrivers(DriverInfo[] drivers) {
      this.drivers = drivers;
   }

   /**
    * Gets the names of the installed driver classes.
    *
    * @return the driver class names.
    */
   public String[] getDriverClasses() {
      return driverClasses;
   }

   /**
    * Sets the names of the installed driver classes.
    *
    * @param driverClasses the driver class names.
    */
   public void setDriverClasses(String[] driverClasses) {
      this.driverClasses = driverClasses;
   }

   private boolean odbcAvailable;
   private String[] odbcDataSources;
   private DriverInfo[] drivers;
   private String[] driverClasses;

   /**
    * Class that encapsulates information about a database driver.
    */
   public static final class DriverInfo {
      /**
       * Gets the database type to which the driver applies.
       *
       * @return the type.
       */
      public String getType() {
         return type;
      }

      /**
       * Sets the database type to which the driver applies.
       *
       * @param type the type.
       */
      public void setType(String type) {
         this.type = type;
      }

      /**
       * Gets the flag that indicates if the driver is installed.
       *
       * @return <tt>true</tt> if installed; <tt>false</tt> otherwise.
       */
      public boolean isInstalled() {
         return installed;
      }

      /**
       * Sets the flag that indicates if the driver is installed.
       *
       * @param installed <tt>true</tt> if installed; <tt>false</tt> otherwise.
       */
      public void setInstalled(boolean installed) {
         this.installed = installed;
      }

      /**
       * Gets the default port number for this driver.
       *
       * @return the port number.
       */
      public int getDefaultPort() {
         return defaultPort;
      }

      /**
       * Sets the default port number for this driver.
       *
       * @param defaultPort the port number.
       */
      public void setDefaultPort(int defaultPort) {
         this.defaultPort = defaultPort;
      }

      private String type;
      private boolean installed;
      private int defaultPort;
   }
}
