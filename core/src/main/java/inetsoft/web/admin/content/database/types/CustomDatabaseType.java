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
package inetsoft.web.admin.content.database.types;

import com.fasterxml.jackson.annotation.JsonTypeName;
import inetsoft.web.admin.content.database.*;
import org.springframework.stereotype.Component;

/**
 * Implementation of <tt>DatabaseType</tt> for arbitrary JDBC connections.
 */
@Component
public final class CustomDatabaseType
   extends DatabaseType<CustomDatabaseType.CustomDatabaseInfo>
{
   /**
    * Creates a new instance of <tt>CustomDatabaseType</tt>.
    */
   public CustomDatabaseType() {
      super(TYPE);
   }

   @Override
   public CustomDatabaseInfo createDatabaseInfo() {
      return new CustomDatabaseInfo();
   }

   @Override
   public NetworkLocation parse(String driverClass, String url,
                                CustomDatabaseInfo info)
   {
      info.setDriverClass(driverClass);
      info.setJdbcUrl(url);
      return null;
   }

   @Override
   public String formatUrl(NetworkLocation network, CustomDatabaseInfo info) {
      return info.getJdbcUrl();
   }

   @Override
   public String getDriverClass(CustomDatabaseInfo info) {
      return info.getDriverClass();
   }

   @Override
   public boolean supportsDriverClass(String driverClass) {
      return true;
   }

   @Override
   public boolean isDriverInstalled() {
      return true;
   }

   @Override
   public int getDefaultPort() {
      return 0;
   }

   public static final String TYPE = "CUSTOM";

   /**
    * Implementation of <tt>DatabaseInfo</tt> for arbitrary JDBC databases.
    */
   @JsonTypeName(TYPE)
   public static final class CustomDatabaseInfo extends DatabaseInfo {
      /**
       * Gets the fully-qualified class name of the JDBC driver.
       *
       * @return the JDBC driver class name.
       */
      public String getDriverClass() {
         return driverClass;
      }

      /**
       * Sets the fully-qualified class name of the JDBC driver.
       *
       * @param driverClass the JDBC driver class name.
       */
      public void setDriverClass(String driverClass) {
         this.driverClass = driverClass;
      }

      /**
       * Get the JDBC URL used to connect to the database.
       *
       * @return the JDBC URL.
       */
      public String getJdbcUrl() {
         return jdbcUrl;
      }

      /**
       * Set the JDBC URL used to connect to the database.
       *
       * @param jdbcUrl the JDBC URL.
       */
      public void setJdbcUrl(String jdbcUrl) {
         this.jdbcUrl = jdbcUrl;
      }

      public String getTestQuery() {
         return testQuery;
      }

      public void setTestQuery(String testQuery) {
         this.testQuery = testQuery;
      }

      private String driverClass;
      private String jdbcUrl;
      private String testQuery;
   }
}
