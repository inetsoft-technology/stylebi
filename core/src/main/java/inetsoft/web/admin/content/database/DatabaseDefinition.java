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

import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Bean that encapsulates the details of how to connect to a database.
 */
public class DatabaseDefinition {
   /**
    * Gets the name of this database connection.
    *
    * @return the name.
    */
   public String getName() {
      return name;
   }

   /**
    * Sets the name of this database connection.
    *
    * @param name the name.
    */
   public void setName(String name) {
      this.name = name;
   }

   public String getOldName() {
      return oname;
   }

   public void setOldName(String oname) {
      this.oname = oname;
   }

   /**
    * Gets the type of database.
    *
    * @return the database type.
    */
   public String getType() {
      return type;
   }

   /**
    * Sets the type of database.
    *
    * @param type the database type.
    */
   public void setType(String type) {
      this.type = type;
   }

   /**
    * Gets the additional, database-specific properties.
    *
    * @return the database properties.
    */
   public DatabaseInfo getInfo() {
      return info;
   }

   /**
    * Sets the additional, database-specific properties.
    *
    * @param info the database properties.
    */
   public void setInfo(DatabaseInfo info) {
      this.info = info;
   }

   /**
    * Gets the location of the database on the network.
    *
    * @return the network location.
    */
   public NetworkLocation getNetwork() {
      return network;
   }

   /**
    * Sets the location of the database on the network.
    *
    * @param network the network location.
    */
   public void setNetwork(NetworkLocation network) {
      this.network = network;
   }

   /**
    * Gets the credentials used to authenticate with the database.
    *
    * @return the authentication details.
    */
   public AuthenticationDetails getAuthentication() {
      return authentication;
   }

   /**
    * Sets the credentials used to authenticate with the database.
    *
    * @param authentication the authentication details.
    */
   public void setAuthentication(AuthenticationDetails authentication) {
      this.authentication = authentication;
   }

   /**
    * Gets the flag that indicates if the current user is allowed to delete or
    * rename this database connection.
    *
    * @return <tt>true</tt> if deletable; <tt>false</tt> otherwise.
    */
   public boolean isDeletable() {
      return deletable;
   }

   /**
    * Sets the flag that indicates if the current user is allowed to delete or
    * rename this database connection.
    *
    * @param deletable <tt>true</tt> if deletable; <tt>false</tt> otherwise.
    */
   public void setDeletable(boolean deletable) {
      this.deletable = deletable;
   }

   public String getDescription() {
      return description;
   }

   public void setDescription(String description) {
      this.description = description;
   }

   /**
    * Set whether deny to unassgined user,
    * if true, a user without any additional dataSource access will can't get data.
    */
   public void setUnasgn(boolean a) {
      this.unasgn = a;
   }

   /**
    * Get whether deny to unassgined user,
    * if true, a user without any additional dataSource access will can't get data.
    */
   public boolean isUnasgn() {
      return unasgn;
   }

   public boolean isAnsiJoin() {
      return ansiJoin;
   }

   public void setAnsiJoin(boolean ansiJoin) {
      this.ansiJoin = ansiJoin;
   }

   public int getTransactionIsolation() {
      return isolation;
   }

   public void setTransactionIsolation(int isolation) {
      this.isolation = isolation;
   }

   public int getTableNameOption() {
      return option;
   }

   public void setTableNameOption(int option) {
      this.option = option;
   }

   public String getDefaultDatabase() {
      return defaultdb;
   }

   public void setDefaultDatabase(String defaultdb) {
      this.defaultdb = defaultdb;
   }

   public boolean isChangeDefaultDB() {
      return changeDefaultDB;
   }

   public void setChangeDefaultDB(boolean changeDefaultDB) {
      this.changeDefaultDB = changeDefaultDB;
   }

   public String getCloudError() {
      return cloudError;
   }

   public void setCloudError(String cloudError) {
      this.cloudError = cloudError;
   }

   private String name;
   private String oname;
   private String type;
   @JsonTypeInfo(
      use = JsonTypeInfo.Id.NAME,
      include = JsonTypeInfo.As.EXTERNAL_PROPERTY,
      property = "type")
   private DatabaseInfo info;
   private NetworkLocation network = new NetworkLocation();
   private AuthenticationDetails authentication;
   private boolean deletable;
   private String description;
   private boolean unasgn;
   private boolean ansiJoin;
   private int isolation = -1;
   private int option;
   private String defaultdb = null; // set the default database
   private boolean changeDefaultDB;
   private String cloudError;
}
