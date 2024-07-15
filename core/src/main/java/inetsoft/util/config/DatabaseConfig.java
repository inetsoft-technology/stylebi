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
package inetsoft.util.config;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import inetsoft.util.ConfigurationContext;
import inetsoft.util.Tool;
import inetsoft.util.config.json.PasswordDeserializer;
import inetsoft.util.config.json.PasswordSerializer;

import java.io.Serializable;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.util.*;

/**
 * {@code DatabaseConfig} contains the connection information for the storage database.
 */
@InetsoftConfigBean
public class DatabaseConfig implements Serializable {
   /**
    * The database type.
    */
   public DatabaseType getType() {
      return type;
   }

   public void setType(DatabaseType type) {
      Objects.requireNonNull(type, "The database type is required");
      this.type = type;
   }

   /**
    * The JDBC URL for the database.
    */
   public String getJdbcUrl() {
      return jdbcUrl;
   }

   public void setJdbcUrl(String jdbcUrl) {
      this.jdbcUrl = jdbcUrl;
   }

   /**
    * The class name for the JDBC driver.
    */
   public String getDriverClassName() {
      return driverClassName;
   }

   public void setDriverClassName(String driverClassName) {
      this.driverClassName = driverClassName;
   }

   /**
    * The classpath entries from which to load the JDBC driver. This is not used for the audit
    * database. This should not be in the main application classpath as it can conflict with the
    * drivers used for data sources.
    */
   public String[] getDriverClasspath() {
      return driverClasspath;
   }

   public void setDriverClasspath(String[] driverClasspath) {
      this.driverClasspath = driverClasspath;
   }

   /**
    * A flag that indicates if the database requires authentication.
    */
   public boolean isRequiresLogin() {
      return requiresLogin;
   }

   public void setRequiresLogin(boolean requiresLogin) {
      this.requiresLogin = requiresLogin;
   }

   /**
    * The username used to authenticate with the database.
    */
   public String getUsername() {
      return username;
   }

   public void setUsername(String username) {
      this.username = username;
   }

   /**
    * The password used to authenticate with the database.
    */
   @JsonSerialize(using = PasswordSerializer.class)
   @JsonDeserialize(using = PasswordDeserializer.class)
   public String getPassword() {
      return password;
   }

   public void setPassword(String password) {
      this.password = password;
   }

   /**
    * The name of the default database or catalog, if any.
    */
   public String getDefaultDatabase() {
      return defaultDatabase;
   }

   public void setDefaultDatabase(String defaultDatabase) {
      this.defaultDatabase = defaultDatabase;
   }

   /**
    * The transaction isolation level for the database.
    */
   public IsolationLevel getTransactionIsolationLevel() {
      return transactionIsolationLevel;
   }

   public void setTransactionIsolationLevel(IsolationLevel transactionIsolationLevel) {
      this.transactionIsolationLevel = transactionIsolationLevel;
   }

   /**
    * The additional properties for the connection pool.
    */
   public Map<String, String> getPool() {
      if(pool == null) {
         pool = new HashMap<>();
      }

      return pool;
   }

   public void setPool(Map<String, String> pool) {
      this.pool = pool;
   }

   /**
    * The amount of time in milliseconds to wait for the database to be available.
    */
   public long getTimeout() {
      return timeout;
   }

   public void setTimeout(long timeout) {
      this.timeout = timeout;
   }

   /**
    * Creates the configuration for the default database.
    *
    * @return the default database configuration.
    */
   public static DatabaseConfig createDefault() {
      return createDefault(Paths.get(ConfigurationContext.getContext().getHome()));
   }

   /**
    * Creates the configuration for the default database.
    *
    * @param home the path to the configuration home directory.
    *
    * @return the default database configuration.
    */
   public static DatabaseConfig createDefault(Path home) {
      Path dir = home.resolve("inetsoftdb").toAbsolutePath();
      dir.toFile().mkdirs();

      DatabaseConfig config = new DatabaseConfig();
      config.setType(DatabaseType.H2);
      config.setDriverClassName(DatabaseType.H2.getDrivers()[0]);
      config.setJdbcUrl(
         "jdbc:h2:$(sree.home)/inetsoftdb/inetsoftdb;" +
         "MODE=Derby;AUTO_SERVER=TRUE;AUTO_SERVER_PORT=8081;AUTO_RECONNECT=TRUE");
      config.setTransactionIsolationLevel(IsolationLevel.READ_UNCOMMITTED);
      config.setRequiresLogin(true);
      config.setUsername("inetsoft_admin");
      config.setPassword(Tool.generatePassword());
      return config;
   }

   private DatabaseType type;
   private String jdbcUrl;
   private String driverClassName;
   private String[] driverClasspath;
   private boolean requiresLogin;
   private String username;
   private String password;
   private String defaultDatabase;
   private IsolationLevel transactionIsolationLevel = Optional.ofNullable(getType())
      .flatMap(t -> t.getTransactionIsolationLevels().stream().findFirst())
      .orElse(null);
   private Map<String, String> pool;
   private long timeout = 120000L;

   /**
    * {@code DatabaseType} is an enumeration of the supported database types.
    */
   public enum DatabaseType {
      /**
       * Microsoft SQL Server.
       */
      SQL_SERVER("com.microsoft.jdbc.sqlserver.SQLServerDriver",
                "com.microsoft.sqlserver.jdbc.SQLServerDriver"),

      /**
       * Oracle database.
       */
      ORACLE(new String[] { "oracle.jdbc.OracleDriver" },
             EnumSet.of(IsolationLevel.READ_COMMITTED, IsolationLevel.SERIALIZABLE)),

      /**
       * Apache Derby.
       */
      DERBY(new String[] {"org.apache.derby.jdbc.ClientDriver"},
         EnumSet.of(IsolationLevel.READ_COMMITTED, IsolationLevel.READ_UNCOMMITTED,
            IsolationLevel.REPEATABLE_READ, IsolationLevel.SERIALIZABLE)),

      /**
       * IBM DB2.
       */
      DB2(new String[] {"COM.ibm.db2.jdbc.net.DB2Driver", "com.ibm.db2.jcc.DB2Driver"},
         EnumSet.of(IsolationLevel.READ_COMMITTED, IsolationLevel.READ_UNCOMMITTED,
            IsolationLevel.REPEATABLE_READ, IsolationLevel.SERIALIZABLE)),

      /**
       * PostgreSQL.
       */
      POSTGRESQL(new String[] {"org.postgresql.Driver"},
         EnumSet.of(IsolationLevel.READ_COMMITTED, IsolationLevel.READ_UNCOMMITTED,
            IsolationLevel.REPEATABLE_READ, IsolationLevel.SERIALIZABLE)),

      /**
       * MySQL.
       */
      MYSQL(new String[] {"com.mysql.jdbc.Driver"},
         EnumSet.of(IsolationLevel.READ_COMMITTED, IsolationLevel.READ_UNCOMMITTED,
            IsolationLevel.REPEATABLE_READ, IsolationLevel.SERIALIZABLE)),

      /**
       * H2 database.
       */
      H2(new String[] {"org.h2.Driver"},
         EnumSet.of(IsolationLevel.READ_COMMITTED, IsolationLevel.READ_UNCOMMITTED,
            IsolationLevel.REPEATABLE_READ, IsolationLevel.SERIALIZABLE));

      private final String[] drivers;
      private final EnumSet<IsolationLevel> transactionIsolationLevels;

      DatabaseType(String... drivers) {
         this(drivers, EnumSet.allOf(IsolationLevel.class));
      }

      DatabaseType(String[] drivers, EnumSet<IsolationLevel> transactionIsolationLevels) {
         this.drivers = drivers;
         this.transactionIsolationLevels = transactionIsolationLevels;
      }

      /**
       * Gets the fully-qualified class names of the JDBC drivers for this type
       * of database.
       *
       * @return the driver class names.
       */
      public String[] getDrivers() {
         return drivers;
      }

      /**
       * Gets the transaction isolation levels for this type of database.
       *
       * @return the transaction isolation levels.
       */
      public EnumSet<IsolationLevel> getTransactionIsolationLevels() {
         return transactionIsolationLevels;
      }
   }

   /**
    * {@code IsolationLevel} is an enumeration of the database transaction isolation levels.
    */
   public enum IsolationLevel {
      /**
       * Indicates that transactions are not supported.
       */
      NONE(Connection.TRANSACTION_NONE, "TRANSACTION_NONE"),

      /**
       * Indicates that dirty reads are prevented; non-repeatable reads and phantom reads can occur.
       */
      READ_COMMITTED(Connection.TRANSACTION_READ_COMMITTED, "TRANSACTION_READ_COMMITTED"),

      /**
       * Indicates that dirty reads, not-repeatable reads and phantom reads can occur.
       */
      READ_UNCOMMITTED(Connection.TRANSACTION_READ_UNCOMMITTED, "TRANSACTION_READ_UNCOMMITTED"),

      /**
       * Indicates that dirty reads and not-repeatable reads are prevented; phantom reads can occur.
       */
      REPEATABLE_READ(Connection.TRANSACTION_REPEATABLE_READ, "TRANSACTION_REPEATABLE_READ"),

      /**
       * Indicates that dirty reads, non-repeatable reads and phantom reads are prevented.
       */
      SERIALIZABLE(Connection.TRANSACTION_SERIALIZABLE, "TRANSACTION_SERIALIZABLE");

      private final int level;
      private final String levelName;

      IsolationLevel(int level, String levelName) {
         this.level = level;
         this.levelName = levelName;
      }

      /**
       * Gets the integer level value defined in {@link Connection}.
       *
       * @return the level value.
       */
      public int level() {
         return level;
      }

      /**
       * Gets the level constant name defined in {@link Connection}.
       *
       * @return the level constant name.
       */
      public String levelName() {
         return levelName;
      }

      /**
       * Gets the {@code IsolationLevel} for the integer level value defined in {@link Connection}.
       *
       * @param level the level value.
       *
       * @return the matching level.
       */
      public static IsolationLevel valueOfLevel(int level) {
         for(IsolationLevel l : values()) {
            if(l.level == level) {
               return l;
            }
         }

         throw new IllegalArgumentException("Invalid level: " + level);
      }

      /**
       * Gets the {@code IsolationLevel} for the level constant name defined in {@link Connection}.
       *
       * @param levelName the level constant name.
       *
       * @return the matching level.
       */
      public static IsolationLevel valueOfLevelName(String levelName) {
         for(IsolationLevel l : values()) {
            if(l.levelName.equals(levelName)) {
               return l;
            }
         }

         throw new IllegalArgumentException("Invalid level name: " + levelName);
      }
   }
}
