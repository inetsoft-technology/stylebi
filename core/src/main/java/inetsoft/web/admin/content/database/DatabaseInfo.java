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
package inetsoft.web.admin.content.database;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import inetsoft.web.admin.content.database.types.*;

import java.util.TreeMap;

/**
 * Class that encapsulates database-specific settings.
 */

@JsonSubTypes({
   @JsonSubTypes.Type(value = CustomDatabaseType.CustomDatabaseInfo.class, name = CustomDatabaseType.TYPE),
   @JsonSubTypes.Type(value = AccessDatabaseType.AccessDatabaseInfo.class, name = AccessDatabaseType.TYPE),
   @JsonSubTypes.Type(value = OracleDatabaseType.OracleDatabaseInfo.class, name = OracleDatabaseType.TYPE),
   @JsonSubTypes.Type(value = SQLServerDatabaseType.SQLServerDatabaseInfo.class, name = SQLServerDatabaseType.TYPE),
   @JsonSubTypes.Type(value = InformixDatabaseType.InformixDatabaseInfo.class, name = InformixDatabaseType.TYPE),
   @JsonSubTypes.Type(value = PostgreSQLDatabaseType.PostgreSQLDatabaseInfo.class, name = PostgreSQLDatabaseType.TYPE),
   @JsonSubTypes.Type(value = DB2DatabaseType.DB2DatabaseInfo.class, name = DB2DatabaseType.TYPE),
   @JsonSubTypes.Type(value = MySQLDatabaseType.MySQLDatabaseInfo.class, name = MySQLDatabaseType.TYPE)
})
public abstract class DatabaseInfo {
   /**
    * Gets the product name of the database.
    *
    * @return the product name.
    */
   public String getProductName() {
      return productName;
   }

   /**
    * Sets the product name of the database.
    *
    * @param productName the product name.
    */
   public void setProductName(String productName) {
      this.productName = productName;
   }

   /**
    * Get the connection properties.
    */
   public String getProperties() {
      return properties;
   }

   /**
    * Set the connection properties.
    */
   public void setProperties(String properties) {
      this.properties = properties;
   }

   /**
    * Set pool properties.
    */
   public void setPoolProperties(TreeMap<String, String> properties) {
      this.poolProperties = properties;
   }

   /**
    * get pool properties
    * @return
    */
   public TreeMap<String, String> getPoolProperties(){
      return this.poolProperties;
   }

   public boolean isCustomEditMode() {
      return customEditMode;
   }

   public void setCustomEditMode(boolean customEditMode) {
      this.customEditMode = customEditMode;
   }

   public String getCustomUrl() {
      return customUrl;
   }

   public void setCustomUrl(String customUrl) {
      this.customUrl = customUrl;
   }

   private String productName;
   private String properties;

   private TreeMap<String, String> poolProperties;
   private boolean customEditMode;
   private String customUrl;
}
