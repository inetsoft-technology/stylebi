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
package inetsoft.web.admin.datasource;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import inetsoft.report.internal.Util;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.util.Tool;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;

import java.sql.Connection;
import java.util.Objects;

/**
 * {@code JdbcDataSourceProperties} contains the properties of a JDBC data source definition.
 */
@Validated
@Schema(description = "The properties of a JDBC data source definition.")
public class JdbcDataSourceProperties extends DataSourceProperties {
   /**
    * Creates a new instance of {@code JdbcDataSourceProperties}.
    */
   public JdbcDataSourceProperties() {
      setType(Type.JDBC);
   }

   /**
    * Creates a new instance of {@code JdbcDataSourceProperties}.
    *
    * @param ds the data source from which to copy the properties.
    * @param id the unique identifier of the data source.
    */
   public JdbcDataSourceProperties(JDBCDataSource ds, String id) {
      setId(id);
      setName(ds.getName());
      setType(Type.JDBC);
      setUrl(ds.getURL());
      setDriver(ds.getDriver());
      setRequireLogin(ds.isRequireLogin());
      setUser(ds.getUser());
      setPassword(Util.PLACEHOLDER_PASSWORD);
      setDefaultDatabase(ds.getDefaultDatabase());
      setTableName(getTableOption(ds.getTableNameOption()));
      setIsolation(IsolationLevel.fromValue(ds.getTransactionIsolation()));
      setAnsiJoin(ds.isAnsiJoin());

      if(Tool.isCloudSecrets()) {
         setUseCredentialId(ds.isUseCredentialId());
         setCredentialID(ds.getCredentialId());
      }
   }

   @Override
   @NotNull
   @Schema(
      description = "The unique identifier of the data source.",
      example = "3B1FAA9AE1140E6580AE5C44CAD29631")
   public String getId() {
      return super.getId();
   }

   @Override
   @NotNull
   @Schema(description = "The name of the data source.", example = "Orders")
   public String getName() {
      return super.getName();
   }

   /**
    * Gets the JDBC URL of the database.
    *
    * @return the URL.
    */
   @NotNull
   @Schema(
      description = "The JDBC URL of the database.",
      example = "jdbc:derby:classpath:orders;user=SA")
   public String getUrl() {
      return url;
   }

   /**
    * Sets the JDBC URL of the database.
    *
    * @param url the URL.
    */
   public void setUrl(String url) {
      this.url = url;
   }

   /**
    * Gets the fully-qualified class name of the JDBC driver.
    *
    * @return the driver class name.
    */
   @NotNull
   @Schema(
      description = "The fully-qualified class name of the JDBC driver.",
      example = "org.apache.derby.jdbc.EmbeddedDriver")
   public String getDriver() {
      return driver;
   }

   /**
    * Sets the fully-qualified class name of the JDBC driver.
    *
    * @param driver the driver class name.
    */
   public void setDriver(String driver) {
      this.driver = driver;
   }

   /**
    * Gets the flag that indicates if the database requires authentication.
    *
    * @return @{code true} if a login is required; {@code false} otherwise.
    */
   @NotNull
   @Schema(
      description = "A flag that indicates if the database requires authentication.",
      example = "true")
   public boolean isRequireLogin() {
      return requireLogin;
   }

   /**
    * Sets the flag that indicates if the database requires authentication.
    *
    * @param requireLogin @{code true} if a login is required; {@code false} otherwise.
    */
   public void setRequireLogin(boolean requireLogin) {
      this.requireLogin = requireLogin;
   }

   /**
    * Gets the user name used to authenticate with the database.
    *
    * @return the user name.
    */
   @Schema(
      description = "The user name used to authenticate with the database. Required if `requireLogin` is `true`.",
      example = "SA")
   public String getUser() {
      return user;
   }

   /**
    * Sets the user name used to authenticate with the database.
    *
    * @param user the user name.
    */
   public void setUser(String user) {
      this.user = user;
   }

   /**
    * Gets the password used to authenticate with the database.
    *
    * @return the password.
    */
   @Schema(
      description = "The password used to authenticate with the database. Required if `requireLogin` is `true`.",
      example = "secret")
   public String getPassword() {
      return password;
   }

   /**
    * Sets the password used to authenticate with the database.
    *
    * @param password the password.
    */
   public void setPassword(String password) {
      this.password = password;
   }

   /**
    * Gets the name of the default database. If not specified, the default database for the login
    * will be used.
    *
    * @return the default database name.
    */
   @Schema(
      description = "The name of the default database or catalog. If not specified, the default database for the login will be used.",
      example = "APP")
   public String getDefaultDatabase() {
      return defaultDatabase;
   }

   /**
    * Sets the name of the default database. If not specified, the default database for the login
    * will be used.
    *
    * @param defaultDatabase the default database name.
    */
   public void setDefaultDatabase(String defaultDatabase) {
      this.defaultDatabase = defaultDatabase;
   }

   @Schema(
      description = "The Table Name option lets you choose the representation of table names in the SQL sent to the database.",
      example = "Defalut")
   public String getTableName() {
      return tableName;
   }

   public void setTableName(String tableName) {
      this.tableName = tableName;
   }

   /**
    * Get the transaction isolation level.
    *
    * @return the transaction isolation level.
    */
   @Schema(
      description = "The transaction isolation level.")
   public IsolationLevel getIsolation() {
      return isolation;
   }

   /**
    * Sets the transaction isolation level.
    *
    * @param isolation the transaction isolation level.
    */
   public void setIsolation(IsolationLevel isolation) {
      this.isolation = isolation;
   }

   /**
    * Get whether to use the ANSI join syntax for inner and outer joins.
    *
    * @return @{code true} if a login is required; {@code false} otherwise.
    */
   @Schema(
      description = "A flag that indicates whether to use the ANSI join syntax.",
      example = "true")
   public boolean isAnsiJoin() {
      return ansiJoin;
   }

   /**
    * Set whether to use the ANSI join syntax for inner and outer joins.
    *
    * @param ansiJoin @{code true} if ANSI join syntax is used; {@code false} otherwise.
    */
   public void setAnsiJoin(boolean ansiJoin) {
      this.ansiJoin = ansiJoin;
   }

   /**
    * Get whether to use the authentication credential.
    *
    * @return @{code true} if a login is required; {@code false} otherwise.
    */
   @Schema(
      description = "A flag that indicates whether to use the authentication credential.",
      example = "false")
   public boolean isUseCredentialId() {
      return useCredentialId;
   }

   /**
    * Set whether to use the authentication credential.
    *
    * @param useCredentialId @{code true} if the authentication credential is used; {@code false} otherwise.
    */
   public void setUseCredentialId(boolean useCredentialId) {
      this.useCredentialId = useCredentialId;
   }

   /**
    * Get the authentication credential id.
    *
    * @return the authentication credential id.
    */
   @Schema(
      description = "The authentication credential id.")
   public String getCredentialID() {
      return credentialID;
   }

   /**
    * Set the authentication credential id.
    *
    * @param credentialID the authentication credential id.
    */
   public void setCredentialID(String credentialID) {
      this.credentialID = credentialID;
   }

   @Override
   public boolean equals(Object o) {
      if(this == o) {
         return true;
      }

      if(o == null || getClass() != o.getClass()) {
         return false;
      }

      if(!super.equals(o)) {
         return false;
      }

      JdbcDataSourceProperties that = (JdbcDataSourceProperties) o;
      return requireLogin == that.requireLogin &&
         Objects.equals(url, that.url) &&
         Objects.equals(driver, that.driver) &&
         Objects.equals(user, that.user) &&
         Objects.equals(password, that.password) &&
         Objects.equals(defaultDatabase, that.defaultDatabase) &&
         Objects.equals(tableName, that.tableName) &&
         Objects.equals(isolation, that.isolation) &&
         ansiJoin == that.ansiJoin &&
         useCredentialId == that.useCredentialId &&
         Objects.equals(credentialID, that.credentialID);

   }

   @Override
   public int hashCode() {
      return Objects.hash(
         super.hashCode(), url, driver, requireLogin, user, password, defaultDatabase);
   }

   @Override
   public String toString() {
      return "JdbcDataSourceProperties{" +
         "id='" + getId() + '\'' +
         ", name ='" + getName() + '\'' +
         ", type ='" + getType() + '\'' +
         ", url='" + url + '\'' +
         ", driver='" + driver + '\'' +
         ", requireLogin=" + requireLogin +
         ", user='" + user + '\'' +
         ", password='" + password + '\'' +
         ", defaultDatabase='" + defaultDatabase + '\'' +
         ", tableName='" + tableName + '\'' +
         ", isolation=" + isolation +
         ", ansiJoin=" + ansiJoin +
         ", useCredential=" + useCredentialId +
         ", credentialID='" + credentialID + '\'' +
         '}';
   }

   private String getTableOption(int option) {
      return switch(option) {
         case 0 -> CATALOG_SCHEMA_OPTION;
         case 1 -> SCHEMA_OPTION;
         case 2 -> TABLE_OPTION;
         case 3 -> DEFAULT_OPTION;
         default -> null;
      };
   }

   private String url;
   private String driver;
   private boolean requireLogin;
   private String user;
   private String password;
   private String defaultDatabase;
   private String tableName;
   private IsolationLevel isolation = IsolationLevel.DEFAULT; // default
   private boolean ansiJoin = false; // use ansi syntax for join
   private boolean useCredentialId = false;
   private String credentialID;

   public static final String DEFAULT_OPTION = "Default";
   public static final String CATALOG_SCHEMA_OPTION = "Catalog.Schema.Table";
   public static final String SCHEMA_OPTION = "Schema.Table";
   public static final String TABLE_OPTION = "Table";

   /**
    * Enumeration of the types of data sources.
    */
   public enum IsolationLevel {
      DEFAULT(-1),
      TRANSACTION_READ_UNCOMMITTED(Connection.TRANSACTION_READ_UNCOMMITTED),
      TRANSACTION_READ_COMMITTED(Connection.TRANSACTION_READ_COMMITTED),
      TRANSACTION_REPEATABLE_READ(Connection.TRANSACTION_REPEATABLE_READ),
      TRANSACTION_SERIALIZABLE(Connection.TRANSACTION_SERIALIZABLE);

      public final int value;

      IsolationLevel(int value) {
         this.value = value;
      }

      @Override
      @JsonValue
      public String toString() {
         return "" + value;
      }

      @SuppressWarnings("unused")
      @JsonCreator
      public static IsolationLevel fromValue(int value) {
         for(IsolationLevel level : values()) {
            if(level.value == value) {
               return level;
            }
         }

         return DEFAULT;
      }
   }
}
