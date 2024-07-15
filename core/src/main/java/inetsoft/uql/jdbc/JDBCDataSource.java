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
package inetsoft.uql.jdbc;

import inetsoft.sree.SreeEnv;
import inetsoft.uql.AdditionalConnectionDataSource;
import inetsoft.uql.XDataSource;
import inetsoft.uql.erm.*;
import inetsoft.uql.jdbc.util.JDBCUtil;
import inetsoft.uql.schema.UserVariable;
import inetsoft.uql.util.XUtil;
import inetsoft.util.*;
import inetsoft.web.admin.content.database.*;
import inetsoft.web.admin.content.database.types.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.PrintWriter;
import java.util.*;

import static inetsoft.web.admin.content.database.types.MySQLDatabaseType.DEFAULT_HOST;

/**
 * JDBC data source represents a SQL database. It records JDBC URL and
 * driver class.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class JDBCDataSource extends AdditionalConnectionDataSource<JDBCDataSource> {
   /**
    * Normal jdbc odbc data source.
    */
   public static final int JDBC_ODBC = 0x0;
   /**
    * Oracle jdbc data source.
    */
   public static final int JDBC_ORACLE = DOMAIN_ORACLE;
   /**
    * Sybase jdbc data source.
    */
   public static final int JDBC_SYBASE = 0x2;
   /**
    * Informix jdbc data source.
    */
   public static final int JDBC_INFORMIX = 0x3;
   /**
    * Db2 jdbc data source.
    */
   public static final int JDBC_DB2 = DOMAIN_DB2;
   /**
    * My SQL jdbc data source.
    */
   public static final int JDBC_MYSQL = 0x5;
   /**
    * Access data source.
    */
   public static final int JDBC_ACCESS = 0x6;
   /**
    * SQL server data source.
    */
   public static final int JDBC_SQLSERVER = DOMAIN_SQLSERVER;
   /**
    * Ingres jdbc data source.
    */
   public static final int JDBC_INGRES = 0x8;

   /**
    * Cloudscape jdbc data source.
    */
   public static final int JDBC_CLOUDSCAPE = 0x9;

   /**
    * SQL anywhere jdbc data source.
    */
   public static final int JDBC_SQLANYWHERE = 0xA;

   /**
    * PostgreSQL jdbc data source.
    */
   public static final int JDBC_POSTGRESQL = 0xB;

   /**
    * LucidDB jdbc data source.
    */
   public static final int JDBC_LUCIDDB = 0xC;

   /**
    * Hadoop Hive JDBC data source.
    */
   public static final int JDBC_HIVE = 0xD;

   /**
    * Oracle BI data source.
    */
   public static final int JDBC_OBIEE = 0xE;

   /**
    * Impala Cloudera DB data source.
    */
   public static final int JDBC_IMPALA = 0xF;

   public static final int JDBC_DREMIO = 0x10;

   public static final int JDBC_SNOWFLAKE = 0x11;

   /**
    * MongoDB with Unity JDBC driver.
    */
   public static final int JDBC_MONGO = 0x12;

   /**
    * Normal jdbc odbc data source.
    */
   public static final String ODBC = "ODBC";
   /**
    * Oracle jdbc data source.
    */
   public static final String ORACLE = "ORACLE";
   /**
    * Sybase jdbc data source.
    */
   public static final String SYBASE = "SYBASE";
   /**
    * Informix jdbc data source.
    */
   public static final String INFORMIX = "INFORMIX";
   /**
    * Db2 jdbc data source.
    */
   public static final String DB2 = "DB2";
   /**
    * My SQL jdbc data source.
    */
   public static final String MYSQL = "MYSQL";
   /**
    * Access data source.
    */
   public static final String ACCESS = "ACCESS";
   /**
    * SQL server data source.
    */
   public static final String SQLSERVER = "SQLSERVER";
   /**
    * Ingres jdbc data source.
    */
   public static final String INGRES = "INGRES";

   /**
    * Cloudscape jdbc data source.
    */
   public static final String CLOUDSCAPE = "CLOUDSCAPE";

   /**
    * SQL anywhere jdbc data source.
    */
   public static final String SQLANYWHERE = "SQLANYWHERE";

   /**
    * PostgreSQL jdbc data source.
    */
   public static final String POSTGRESQL = "POSTGRESQL";

   /**
    * LucidDB jdbc data source.
    * the suffix NONE represents the database only supports TRANSACTION_NONE
    *    isolation leveL
    */
   public static final String LUCIDDB = "LUCIDDB_NONE";

   /**
    * Hadoop Hive JDBC data source.
    */
   public static final String HIVE = "HIVE";

   /**
    * Oracle BI data source.
    */
   public static final String OBIEE = "OBIEE";

   /**
    * Impala Cloudera DB data source.
    */
   public static final String IMPALA = "IMPALA";

   public static final String DREMIO = "DREMIO";

   public static final String SNOWFLAKE = "SNOWFLAKE";

   public static final String MONGO = "MONGO";

   /**
    * Table name option as catalog.schema.table.
    */
   public static final int CATALOG_SCHEMA_OPTION = 0;
   /**
    * Table name option as schema.table.
    */
   public static final int SCHEMA_OPTION = 1;
   /**
    * Table name option as table.
    */
   public static final int TABLE_OPTION = 2;
   /*
    * Default table name option.
    */
   public static final int DEFAULT_OPTION = 3;

   /**
    * Create a JDBC data source.
    */
   public JDBCDataSource() {
      super(JDBC, JDBCDataSource.class);
   }

   /**
    * Set the JDBC driver class full name.
    */
   public void setDriver(String driver) {
      this.driver = driver;
      setDatabaseType0();
      resetDatabaseDefinition();
   }

   /**
    * Get the JDBC driver class full name.
    */
   public String getDriver() {
      return this.driver;
   }

   /**
    * Get the max cursor number the database allow to proceed at the same time.
    */
   @SuppressWarnings("unused")
   public int getMaxCursor() {
      String prop = SreeEnv.getProperty("jdbc.max.cursor");

      if(prop != null) {
         return Integer.parseInt(prop);
      }

      return 1;
   }

   /**
    * Set the JDBC URL of the JDBC data source.
    */
   public void setURL(String url) {
      this.url = url;
      rurl = null;
      runtimeProductName = null;
      setDatabaseType0();
      resetDatabaseDefinition();
   }

   /**
    * Get the JDBC URL of the JDBC data source.
    */
   public String getURL() {
      return getURL(true);
   }

   /**
    * Get the JDBC URL of the JDBC data source.
    */
   public String getURL(boolean runtime) {
      return runtime ? Tool.isEmptyString(rurl) ? XUtil.replaceEnvVariable(url) : rurl : url;
   }

   public DatabaseInfo getDatabaseInfo() {
      if(this.databaseDefinition == null) {
         this.resetDatabaseDefinition();
      }

      return this.databaseDefinition.getInfo();
   }

   private NetworkLocation getNetwork() {
      if(this.databaseDefinition == null) {
         this.resetDatabaseDefinition();
      }

      return databaseDefinition.getNetwork();
   }

   public String getCustomUrl() {
      return customUrl;
   }

   public void setCustomUrl(String customUrl) {
      this.customUrl = customUrl;
      this.rurl = null;
   }

   public String getHost() {
      return unsupportedHostField() ? null
         : getNetwork().getHostName();
   }

   public void setHost(String host) {
      NetworkLocation network;

      if(!unsupportedHostField() && (network = getNetwork()) != null) {
         network.setHostName(host);
      }
   }

   public int getPort() {
      return unsupportedPortField() ? -1 : getNetwork().getPortNumber();
   }

   public void setPort(int port) {
      NetworkLocation network;

      if(!unsupportedPortField() && (network = getNetwork()) != null) {
         network.setPortNumber(port);
      }
   }

   public String getInstanceName() {
      if(!unsupportedDBField()) {
         final DatabaseInfo databaseInfo = getDatabaseInfo();

         if(this.dbtype == JDBC_SQLSERVER) {
            return ((SQLServerDatabaseType.SQLServerDatabaseInfo) databaseInfo)
               .getInstanceName();
         }
      }

      return null;
   }

   public void setInstanceName(String instanceName) {
      if(!unsupportedDBField()) {
         if(this.dbtype == JDBC_SQLSERVER) {
            ((SQLServerDatabaseType.SQLServerDatabaseInfo) getDatabaseInfo())
               .setInstanceName(instanceName);
         }
      }
   }

   public String getDbName() {
      if(!unsupportedDBField()) {
         final DatabaseInfo databaseInfo = getDatabaseInfo();

         if(this.dbtype == JDBC_ORACLE) {
            return ((OracleDatabaseType.OracleDatabaseInfo) databaseInfo).getSid();
         }
         else if(databaseInfo instanceof DatabaseNameInfo){
            return ((DatabaseNameInfo) databaseInfo).getDatabaseName();
         }
      }

      return null;
   }

   public void setDbName(String dbName) {
      if(!unsupportedDBField()) {
         if(this.dbtype == JDBC_ORACLE) {
            ((OracleDatabaseType.OracleDatabaseInfo) getDatabaseInfo()).setSid(dbName);
         }
         else {
            ((DatabaseNameInfo) getDatabaseInfo()).setDatabaseName(dbName);
         }
      }
   }

   public String getFilePath() {
      if(supportedFileField()) {
         return ((AccessDatabaseType.AccessDatabaseInfo) getDatabaseInfo())
            .getDataSourceName();
      }

      return null;
   }

   public void setFilePath(String filePath) {
      if(supportedFileField()) {
         ((AccessDatabaseType.AccessDatabaseInfo) getDatabaseInfo())
            .setDataSourceName(filePath);
      }
   }

   public String getServerName() {
      if(supportedServerNameField()) {
         return ((InformixDatabaseType.InformixDatabaseInfo) getDatabaseInfo())
            .getServerName();
      }

      return null;
   }

   public void setServerName(String serverName) {
      if(supportedServerNameField()) {
         ((InformixDatabaseType.InformixDatabaseInfo) getDatabaseInfo())
            .setServerName(serverName);
      }
   }

   public String getDbLocal() {
      if(supportedDbLocalField()) {
         return ((InformixDatabaseType.InformixDatabaseInfo) getDatabaseInfo())
            .getDatabaseLocale();
      }

      return null;
   }

   public void setDbLocal(String dbLocal) {
      if(supportedDbLocalField()) {
         ((InformixDatabaseType.InformixDatabaseInfo) getDatabaseInfo())
            .setDatabaseLocale(dbLocal);
      }
   }

   public String getConnectionProps() {
      return getDatabaseInfo().getProperties();
   }

   public void setConnectionProps(String connectionProps) {
      getDatabaseInfo().setProperties(connectionProps);
   }

   public void setPoolProperties(TreeMap<String, String> properties){
      this.poolProperties = properties;
   }

   public TreeMap<String, String> getPoolProperties(){
      return this.poolProperties;
   }

   public void putPoolProperties(String key, String value) {
      this.poolProperties.put(key, value);
   }

   public void removePoolProperties(String key) {
      this.poolProperties.remove(key);
   }


   /**
    * Set whether this data source requires user login during connection.
    */
   public void setRequireLogin(boolean login) {
      this.login = login;
   }

   /**
    * Check if this data soure requires user login.
    */
   public boolean isRequireLogin() {
      return this.login;
   }

   /**
    * Set whether this data source requires save user and password.
    */
   public void setRequireSave(boolean save) {
      this.save = save;
   }

   /**
    * Check if this data soure requires save user and password.
    */
   public boolean isRequireSave() {
      return this.save;
   }

   /**
    * Set the default database name. If this option is not set, the
    * connection uses the default database for the login.
    */
   public void setDefaultDatabase(String dbname) {
      if(dbname != null && dbname.trim().length() > 0) {
         this.defaultdb = dbname.trim();
      }
      else {
         this.defaultdb = null;
      }
   }

   /**
    * Get the default database name.
    */
   public String getDefaultDatabase() {
      return this.defaultdb;
   }

   /**
    * Set the user id.
    */
   public void setUser(String user) {
      this.user = user;
   }

   /**
    * Get the used user id.
    */
   public String getUser() {
      return this.user;
   }

   /**
    * Check if the specified data source supports cancel.
    */
   public boolean supportsCancel() {
      // @by billh, fix customer bug bug1305563167584
      // The method hashcode/equals in Statement from intersys cache db does
      // not work as the others
      return !"com.intersys.jdbc.CacheDriver".equals(driver);
   }

   /**
    * Set the user password.
    */
   public void setPassword(String password) {
      this.password = password;
   }

   /**
    * Get the used user password.
    */
   public String getPassword() {
      return this.password;
   }

   /**
    * Set the product id.
    */
   public void setProductName(String product) {
      this.product = product;
      setDatabaseType0();
      resetDatabaseDefinition();
   }

   /**
    * Get the used product id.
    */
   public String getProductName() {
      return this.product;
   }

   /**
    * Set the product version.
    */
   public void setProductVersion(String version) {
      this.version = version;
   }

   /**
    * Get the product version.
    */
   public String getProductVersion() {
      return this.version;
   }

   /**
    * Get the detected product name.
    */
   public String getRuntimeProductName() {
      return runtimeProductName;
   }

   /**
    * Set the detected product name. This is used as a cache for performance reason.
    * The value is detcted from database url at runtime.
    */
   public void setRuntimeProductName(String runtimeProductName) {
      this.runtimeProductName = runtimeProductName;
   }

   /**
    * Set the transaction isolation level.
    * @param level one of the constants defined in java.sql.Connection.
    */
   public void setTransactionIsolation(int level) {
      isolation = level;
   }

   /**
    * Get the transaction isolation level.
    * @return isolation level or -1 if using driver default.
    */
   public int getTransactionIsolation() {
      return isolation;
   }

   /**
    * Set the table name option.
    * @param option the selected table option.
    */
   public void setTableNameOption(int option) {
      this.option = option;
   }

   /**
    * Get the table name option.
    * @return the table name option.
    */
   public int getTableNameOption() {
      return getBaseDatasource() != null ? getBaseDatasource().option : option;
   }

   /**
    * Set whether to use the ANSI join syntax for inner and outer joins.
    */
   public void setAnsiJoin(boolean ansi) {
      ansiJoin = ansi;
   }

   /**
    * Check whether to use the ANSI join syntax for inner and outer joins.
    */
   public boolean isAnsiJoin() {
      return ansiJoin;
   }

   /**
    * Set the database type.
    * @param type a constant represents the database type.
    */
   public void setDatabaseType(int type) {
      dbtype = type;
      runtimeProductName = null;
   }

   /**
    * Get the database type.
    * @return a constant represents the database type.
    */
   public int getDatabaseType() {
      return dbtype;
   }

   public String getDatabaseTypeString() {
      switch(getDatabaseType()) {
      case JDBC_ORACLE:
         return ORACLE;
      case JDBC_SYBASE:
         return SYBASE;
      case JDBC_INFORMIX:
         return INFORMIX;
      case JDBC_DB2:
         return DB2;
      case JDBC_MYSQL:
         return MYSQL;
      case JDBC_ACCESS:
         return ACCESS;
      case JDBC_SQLSERVER:
         return SQLSERVER;
      case JDBC_INGRES:
         return INGRES;
      case JDBC_CLOUDSCAPE:
         return CLOUDSCAPE;
      case JDBC_SQLANYWHERE:
         return SQLANYWHERE;
      case JDBC_POSTGRESQL:
         return POSTGRESQL;
      case JDBC_LUCIDDB:
         return LUCIDDB;
      case JDBC_HIVE:
         return HIVE;
      case JDBC_OBIEE:
         return OBIEE;
      case JDBC_IMPALA:
         return IMPALA;
      case JDBC_DREMIO:
         return DREMIO;
      case JDBC_SNOWFLAKE:
         return SNOWFLAKE;
      case JDBC_MONGO:
         return MONGO;
      default:
         return ODBC;
      }
   }

   @SuppressWarnings("unused")
   public static boolean isSupportCatalog(int toption) {
      return toption == JDBCDataSource.CATALOG_SCHEMA_OPTION || toption == JDBCDataSource.DEFAULT_OPTION;
   }

   public static boolean isSupportSchema(int toption) {
      return toption != JDBCDataSource.TABLE_OPTION;
   }

   /**
    * Get the domain type associated with this datasource. Return DOMAIN_NONE if
    * this datasource does not support domain.
    */
   @Override
   public int getDomainType() {
      return dbtype;
   }

   /**
    * Check if the database is a special type.
    * @param type a constant represents the database type.
    */
   public boolean checkDatabaseType(int type) {
      return (dbtype ^ type) == 0x0;
   }

   /**
    * Get the data source connection parameters.
    */
   @Override
   public UserVariable[] getParameters() {
      if(!isRequireLogin() ||
         getUser() != null && getUser().length() > 0 && getPassword() != null)
      {
         return null;
      }

      List<UserVariable> params = new ArrayList<>();

      if(getUser() == null || getUser().isEmpty()) {
         UserVariable param = new UserVariable(XUtil.DB_USER_PREFIX + getFullName());
         param.setAlias(Catalog.getCatalog().getString("User"));
         param.setSource(getFullName());
         params.add(param);
      }

      if(getPassword() == null || getPassword().isEmpty()) {
         UserVariable param = new UserVariable(XUtil.DB_PASSWORD_PREFIX + getFullName());
         param.setAlias(Catalog.getCatalog().getString("Password"));
         param.setSource(getFullName());
         param.setHidden(true);
         params.add(param);
      }

      return params.toArray(new UserVariable[0]);
   }

   @Override
   public boolean equals(Object obj) {
      if(obj instanceof JDBCDataSource) {
         JDBCDataSource jdbcObj = (JDBCDataSource) obj;
         String obj_driver = jdbcObj.getDriver();

         if(obj_driver == null) {
            obj_driver = "";
         }

         String obj_url = jdbcObj.getURL();

         if(obj_url == null) {
            obj_url = "";
         }

         // default database
         String obj_defaultdb = jdbcObj.getDefaultDatabase();

         if(obj_defaultdb == null) {
            obj_defaultdb = "";
         }

         String driver = this.getDriver();

         if(driver == null) {
            driver = "";
         }

         String url = this.getURL();

         if(url == null) {
            url = "";
         }

         String defaultdb = this.getDefaultDatabase();

         if(defaultdb == null) {
            defaultdb = "";
         }

         boolean result = true;

         if((!driver.equals(obj_driver)) || (!url.equals(obj_url)) ||
            (!defaultdb.equals(obj_defaultdb))) {
            result = false;
         }
         else if(isRequireLogin() != jdbcObj.isRequireLogin()) {
            result = false;
         }
         else if(isRequireLogin() && !Tool.equals(user, jdbcObj.user)) {
            result = false;
         }
         else if(isRequireLogin() && !Tool.equals(password, jdbcObj.password)) {
            result = false;
         }
         else if(isCustomEditMode() != jdbcObj.isCustomEditMode()) {
            result = false;
         }
         else if(!Tool.equals(getCustomUrl(), jdbcObj.getCustomUrl())) {
            result = false;
         }
         else if(isAnsiJoin() != jdbcObj.isAnsiJoin()) {
            result = false;
         }
         else if(!Tool.equals(getDefaultDatabase(), jdbcObj.getDefaultDatabase())) {
            result = false;
         }
         else if(getTransactionIsolation() != jdbcObj.getTransactionIsolation()) {
            result = false;
         }
         else if(getTableNameOption() != jdbcObj.getTableNameOption()) {
            // @by jasons, this affects the way in which the meta-data is
            // reported and requires that cached copies be replaced
            result = false;
         }
         else if(!Tool.equals(getDescription(), jdbcObj.getDescription())) {
            result = false;
         }
         else if(unasgn != jdbcObj.unasgn) {
            result = false;
         }
         else if(!Tool.equals(poolProperties, jdbcObj.poolProperties)){
            return false;
         }

         return result;
      }

      return super.equals(obj);
   }

   /**
    * Parse the XML element that contains information on this
    * data source.
    */
   @Override
   public void parseXML(Element root) throws Exception {
      super.parseXML(root);

      setURL(Tool.getAttribute(root, "url"));
      String customUrl = Tool.getAttribute(root, "customUrl");
      setCustomUrl(customUrl == null ? getURL() : customUrl);
      setCustomEditMode(Boolean.parseBoolean(Tool.getAttribute(root, "customEditMode")));
      this.driver = Tool.getAttribute(root, "driver");
      String attr = Tool.getAttribute(root, "requireLogin");
      this.login = attr == null || attr.equalsIgnoreCase("true");

      if((attr = Tool.getAttribute(root, "defaultDB")) != null) {
         if(attr.trim().length() > 0) {
            this.defaultdb = attr.trim();
         }
         else {
            this.defaultdb = null;
         }
      }
      else {
         this.defaultdb = null;
      }

      this.user = Tool.getAttribute(root, "user");
      this.password = Tool.decryptPassword(Tool.getAttribute(root, "password"));
      attr = Tool.getAttribute(root, "save");
      this.save = attr == null && user != null && user.length() > 0 ||
         "true".equals(attr);
      this.product = Tool.getAttribute(root, "product");
      this.version = Tool.getAttribute(root, "version");
      this.custom = "true".equals(Tool.getAttribute(root, "custom"));
      attr = Tool.getAttribute(root, "transactionIsolation");

      if(attr != null) {
         isolation = Integer.parseInt(attr);
      }

      poolProperties = new TreeMap<>();
      String xmlVersion = Tool.getChildValueByTagName(root, "version");

      if(Objects.equals(xmlVersion, FileVersions.DATASOURCE)) {
         Element props = Tool.getChildNodeByTagName(root, "poolProperties");

         if(props != null) {
            NodeList nodes = Tool.getChildNodesByTagName(props, "property");

            for(int i = 0; i < nodes.getLength(); i++) {
               Element elem = (Element) nodes.item(i);
               String name = Tool.getChildValueByTagName(elem, "name");
               String value = Tool.getChildValueByTagName(elem, "value");
               poolProperties.put(name, value);
            }
         }
      }
      else { // backward compatibility
         String prefix = "inetsoft.uql.jdbc.pool." + getFullName() + ".";
         Properties env = SreeEnv.getProperties();

         for(String name : env.stringPropertyNames()) {
            if(name.startsWith(prefix)) {
               poolProperties.put(name.substring(prefix.length()), env.getProperty(name));
            }
         }
      }

      attr = Tool.getAttribute(root, "nameOption");

      if(attr != null) {
         option = Integer.parseInt(attr);
         option = option == -1 ? DEFAULT_OPTION : option;
      }

      attr = Tool.getAttribute(root, "ansiJoin");

      if(attr != null) {
         ansiJoin = attr.equalsIgnoreCase("true");
      }

      attr = Tool.getAttribute(root, "unasgn");

      if(attr != null) {
         unasgn = "true".equalsIgnoreCase(attr);
      }

      // password should be non-null
      if(this.password == null) {
         this.password = "";
      }

      setDatabaseType0();
      resetDatabaseDefinition();
   }

   /**
    * Generate the XML segment to represent this data source.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      writer.print("<ds_jdbc url=\"" + Tool.escape(url)
         + "\" customUrl=\"" + Tool.escape(customUrl)
         + "\" customEditMode=\"" + customEditMode
         + "\" driver=\"" + Tool.escape(driver)
         + "\" requireLogin=\"" + login +
         "\" save=\"" + save +
         "\" transactionIsolation=\"" + isolation +
         "\" nameOption=\"" + option +
         "\" ansiJoin=\"" + ansiJoin +
         "\" unasgn=\"" + unasgn +
         "\" custom=\"" + custom + "\"");

      if(defaultdb != null) {
         writer.print(" defaultDB=\"" + Tool.escape(defaultdb) + "\"");
      }

      if(user != null) {
         writer.print(" user=\"" + Tool.escape(user) + "\" password=\"" +
                         Tool.escape(Tool.encryptPassword(password)) + "\"");
      }

      if(product != null) {
         writer.print(" product=\"" + Tool.escape(product) + "\"");
      }

      if(version != null) {
         writer.print(" version=\"" + Tool.escape(version) + "\"");
      }

      writer.println(">");

      if(poolProperties != null && !poolProperties.isEmpty()) {
         writer.print("<poolProperties>");

         for(Map.Entry<String, String> entry : poolProperties.entrySet()) {
            writer.println("<property>");
            writer.format("<name><![CDATA[%s]]></name>%n", entry.getKey());
            writer.format("<value><![CDATA[%s]]></value>%n", entry.getValue());
            writer.println("</property>");
         }

         writer.print("</poolProperties>");
      }

      super.writeXML(writer);
      writer.println("</ds_jdbc>");
   }

   /**
    * Set database type according to URL and Driver.
    */
   private void setDatabaseType0() {
      String url = getURL(false);

      // for sql anywhere 12
      //noinspection ConstantConditions
      if(url == null && getDriver() == null ||
         (getDriver() == null && !url.contains("jdbc:sqlanywhere")))
      {
         setDatabaseType(JDBC_ODBC);
         return;
      }

      String urlLower = url != null ? url.toLowerCase() : "";
      String driverLower =
         getDriver() == null ? "" : getDriver().toLowerCase();

      // for other sql anywhere version
      boolean type = driverLower.contains("anywhere") ||
         urlLower.contains("anywhere");

      if(type) {
         setDatabaseType(JDBC_SQLANYWHERE);
         return;
      }

      type = urlLower.contains("ucanaccess") || driverLower.contains("ucanaccess");

      if(type) {
         setDatabaseType(JDBC_ACCESS);
         return;
      }

      type = urlLower.contains("odbc") || driverLower.contains("odbc");

      if(type) {
         if(product != null && product.contains("SQLSERVER")) {
            setDatabaseType(JDBC_SQLSERVER);
         }
         else {
            setDatabaseType(JDBC_ODBC);
         }

         return;
      }

      type = (urlLower.contains("oracle") || driverLower.contains("oracle")) &&
         //@by jasons, don't use oracle for J.D. Edwards driver
         !driverLower.contains("jdedwards") &&
         //@by jasonshobe, don't use oracle for OBIEE driver
         !driverLower.contains("anajdbcdriver");

      if(type) {
         setDatabaseType(JDBC_ORACLE);
         return;
      }

      type = urlLower.contains("sybase") || driverLower.contains("sybase");

      if(type) {
         setDatabaseType(JDBC_SYBASE);
         return;
      }

      type = urlLower.contains("db2") || driverLower.contains("db2");

      if(type) {
         setDatabaseType(JDBC_DB2);
         return;
      }

      type = urlLower.contains("informix") || driverLower.contains("informix");

      if(type) {
         setDatabaseType(JDBC_INFORMIX);
         return;
      }

      type = urlLower.contains("mysql") || driverLower.contains("mysql") ||
         urlLower.contains("mariadb") || driverLower.contains("mariadb");

      if(type) {
         setDatabaseType(JDBC_MYSQL);
         return;
      }

      type = urlLower.contains("sqlserver") ||
         driverLower.contains("sqlserver");

      if(type) {
         setDatabaseType(JDBC_SQLSERVER);
         return;
      }

      type = urlLower.contains("edbc") || driverLower.contains("ca.edbc") ||
         urlLower.contains("ingres") || driverLower.contains("com.ingres");

      if(type) {
         setDatabaseType(JDBC_INGRES);
         return;
      }

      type = urlLower.contains("derby") || driverLower.contains("derby");

      if(type) {
         setDatabaseType(JDBC_CLOUDSCAPE);
         return;
      }

      type = urlLower.contains("postgresql") ||
         driverLower.contains("postgresql");

      if(type) {
         setDatabaseType(JDBC_POSTGRESQL);
         return;
      }

      type = urlLower.contains("luciddb") ||
         driverLower.contains("luciddb");

      if(type) {
         setDatabaseType(JDBC_LUCIDDB);
         return;
      }

      // be specific because we may want to support the jdbc:hive2: driver (for
      // the beeswax server) later
      type = urlLower.startsWith("jdbc:hive:") ||
         urlLower.startsWith("jdbc:hive2:") ||
         driverLower.equals("org.apache.hadoop.hive.jdbc.hivedriver") ||
         driverLower.equals("org.apache.hive.jdbc.hivedriver");

      if(type) {
         setDatabaseType(JDBC_HIVE);
         return;
      }

      type = urlLower.startsWith("jdbc:oraclebi:") ||
         driverLower.equals("oracle.bi.jdbc.anajdbcdriver");

      if(type) {
         setDatabaseType(JDBC_OBIEE);
         return;
      }

      type = urlLower.startsWith("jdbc:impala:") ||
         driverLower.startsWith("com.cloudera.impala");

      if(type) {
         setDatabaseType(JDBC_IMPALA);
         return;
      }

      type = urlLower.startsWith("jdbc:dremio:") || driverLower.equals("com.dremio.jdbc.driver");

      if(type) {
         setDatabaseType(JDBC_DREMIO);
         return;
      }

      type = urlLower.startsWith("jdbc:snowflake:") || driverLower.equals("net.snowflake.client.jdbc.snowflakedriver");

      if(type) {
         setDatabaseType(JDBC_SNOWFLAKE);
         return;
      }

      type = urlLower.startsWith("jdbc:mongo:") || driverLower.equals("mongodb.jdbc.mongodriver");

      if(type) {
         setDatabaseType(JDBC_MONGO);
         return;
      }

      setDatabaseType(JDBC_ODBC);
   }

   /**
    * Get a list of the schemas this datasource uses for system tables.
    */
   @SuppressWarnings("unused")
   public String[] getSystemSchemas() {
      if(systemSchemas == null) {
         readTableFilter();
      }

      return systemSchemas;
   }

   /**
    * Get a list of the catalogs this datasource uses for system tables.
    */
   @SuppressWarnings("unused")
   public String[] getSystemCatalogs() {
      if(systemCatalogs == null) {
         readTableFilter();
      }

      return systemCatalogs;
   }

   private synchronized void readTableFilter() {
      if(systemSchemas == null || systemCatalogs == null) {
         List<String> schemas = new ArrayList<>();
         List<String> catalogs = new ArrayList<>();

         try {
            org.w3c.dom.Document doc = Tool.parseXML(getClass().
               getResourceAsStream("/inetsoft/uql/jdbc/tablefilter.xml"));
            org.w3c.dom.Element root = doc.getDocumentElement();
            org.w3c.dom.Node filternode = null;
            org.w3c.dom.NodeList nodes = root.getChildNodes();

            for(int i = 0; i < nodes.getLength(); i++) {
               org.w3c.dom.Node node = nodes.item(i);

               if(node.getNodeName().equals("database")) {
                  int dbtype = Integer.parseInt(node.getAttributes().
                     getNamedItem("type").getNodeValue());

                  if(dbtype == getDatabaseType()) {
                     filternode = node;
                     break;
                  }
               }
            }

            if(filternode != null) {
               nodes = filternode.getChildNodes();

               for(int i = 0; i < nodes.getLength(); i++) {
                  org.w3c.dom.Node node = nodes.item(i);

                  if(node.getNodeName().equals("catalog")) {
                     org.w3c.dom.NodeList valuenodes = node.getChildNodes();

                     for(int j = 0; j < valuenodes.getLength(); j++) {
                        org.w3c.dom.Node textnode = valuenodes.item(j);

                        if(textnode.getNodeName().equals("#text")) {
                           catalogs.add(textnode.getNodeValue());
                        }
                     }
                  }
                  else if(node.getNodeName().equals("schema")) {
                     org.w3c.dom.NodeList valuenodes = node.getChildNodes();

                     for(int j = 0; j < valuenodes.getLength(); j++) {
                        org.w3c.dom.Node textnode = valuenodes.item(j);

                        if(textnode.getNodeName().equals("#text")) {
                           schemas.add(textnode.getNodeValue());
                        }
                     }
                  }
               }
            }
         }
         catch(Exception exc) {
            LOG.error("Failed to parse table filter", exc);
         }

         systemSchemas = schemas.toArray(new String[0]);
         systemCatalogs = catalogs.toArray(new String[0]);
      }
   }

   @Override
   protected void renameDatasourceChildren(String oname, String nname) {
      XDataModel model = getRegistry().getDataModel(getFullName());
      String[] names = model.getPartitionNames();

      for(String name : names) {
         XPartition partition = model.getPartition(name);
         XPartition extend = partition.getPartition(oname);

         if(extend != null && extend.getConnection() != null) {

            if(partition.containPartition(nname)) {
               extend.setConnection(null);
            }
            else {
               extend.setConnection(nname);
               extend.setName(nname);
               partition.renamePartition(oname, extend);
            }
         }
      }

      for(String name : model.getLogicalModelNames()) {
         XLogicalModel lm = model.getLogicalModel(name);
         XLogicalModel extend = lm.getLogicalModel(oname);

         if(extend != null && extend.getConnection() != null) {
            if(lm.containLogicalModel(nname)) {
               extend.setConnection(null);
            }
            else {
               extend.setConnection(nname);
               extend.setName(nname);
               lm.renameLogicalModel(oname, extend);
            }
         }
      }
   }

   /**
    * Identity of an datasource.
    */
   public String getIdentity() {
      return url + "__USER__" + user + "__DEFDB__" + defaultdb;
   }

   /*
    * Set whether deny to unassgined user,
    * if true, a user without any additional dataSource access will can't get data.
    */
   public void setUnasgn(boolean a) {
      this.unasgn = a;
   }

   /*
    * Get whether deny to unassgined user,
    * if true, a user without any additional dataSource access will can't get data.
    */
   public boolean isUnasgn() {
      return unasgn;
   }

   /**
    * Returns whether this data source connection was configured manually.
    */
   public boolean isCustom() {
      return custom;
   }

   /**
    * Set whether this data source connection was configured manually.
    */
   public void setCustom(boolean c) {
      custom = c;
   }

   public String defaultHostName() {
      return DEFAULT_HOST;
   }

   public int defaultPort() {
      switch(dbtype) {
         case JDBC_MYSQL:
            return MySQLDatabaseType.DEFAULT_PORT;
         case JDBC_INFORMIX:
            return InformixDatabaseType.DEFAULT_PORT;
         case JDBC_ORACLE:
            return OracleDatabaseType.DEFAULT_PORT;
         case JDBC_POSTGRESQL:
            return PostgreSQLDatabaseType.DEFAULT_PORT;
         case JDBC_SYBASE:
            return SybaseDatabaseType.DEFAULT_PORT;
         case JDBC_DB2:
            return DB2DatabaseType.DEFAULT_PORT;
         case JDBC_SQLSERVER:
            return SQLServerDatabaseType.DEFAULT_PORT;
         case JDBC_OBIEE:
            return 9501;
         case JDBC_DREMIO:
            return 31010;
         case JDBC_SNOWFLAKE:
            return 443;
         case JDBC_INGRES:
            return 117;
         case JDBC_HIVE:
            return 10000;
         case JDBC_CLOUDSCAPE: // derby server mode
            return 1527;
         case JDBC_MONGO:
            return 27017;

      default:
            return 0;
      }
   }

   public boolean unsupportedPortField() {
      return isCustomEditMode() || dbtype == JDBC_ACCESS || dbtype == JDBC_SQLANYWHERE;
   }

   public boolean unsupportedHostField() {
      return isCustomEditMode() || dbtype == JDBC_ACCESS;
   }

   @SuppressWarnings("BooleanMethodIsAlwaysInverted")
   public boolean unsupportedDBField() {
      return isCustomEditMode() || dbtype == JDBC_ACCESS;
   }

   public boolean optionalDBField() {
      return !isCustomEditMode() && (dbtype == JDBC_SQLSERVER || dbtype == JDBC_SYBASE);
   }

   public boolean supportedFileField() {
      return !isCustomEditMode() && dbtype == JDBC_ACCESS;
   }

   public boolean supportedServerNameField() {
      return !isCustomEditMode() && dbtype == JDBC_INFORMIX;
   }

   public boolean supportedDbLocalField() {
      return !isCustomEditMode() && dbtype == JDBC_INFORMIX;
   }

   public boolean supportedInstanceNameField() {
      return !isCustomEditMode() && dbtype == JDBC_SQLSERVER;
   }

   public boolean isCustomEditMode() {
      return customEditMode || custom
         || databaseDefinition != null
            && CustomDatabaseType.TYPE.equals(databaseDefinition.getType());
   }

   public void setCustomEditMode(boolean customEditMode) {
      this.customEditMode = customEditMode;
   }

   public DatabaseDefinition getDatabaseDefinition() {
      return databaseDefinition;
   }

   /**
    * sync url
    */
   @SuppressWarnings({ "rawtypes", "unchecked" })
   public void syncUrl() {
      if(isCustomEditMode()) {
         url = customUrl;
         DatabaseType databaseType = JDBCUtil.getJDBCDatabaseType(getDatabaseTypeString());

         if(databaseType != null) {
            // datasourceInfo will be custom info when the JDBC TYPE is other,
            // so need to crate a specific info use databaseType.
            DatabaseInfo info = databaseType.createDatabaseInfo();
            databaseType.parse(getDriver(), url, info);
            setConnectionProps(info.getProperties());
         }

         return;
      }
      else if(databaseDefinition == null || !StringUtils.hasText(getHost()) && getPort() < 1
         && !StringUtils.hasText(getDbName()) && !StringUtils.hasText(getFilePath()))
      {
         url = JDBC_URL_TEMPLATE;
         return;
      }

      url = formatUrl();
      rurl = null;
   }

   private String formatUrl() {
      return JDBCUtil.formatUrl(databaseDefinition);
   }

   public void reset() {
      url = rurl = customUrl = null;
      databaseDefinition = null;
      // clean user name and password.
      user = password = null;
   }

   public void resetDatabaseDefinition() {
   }

   @Override
   public boolean equalsConnection(XDataSource dx) {
      try {
         JDBCDataSource jdbc = (JDBCDataSource) dx;

         return Objects.equals(driver, jdbc.driver) &&
            Objects.equals(url, jdbc.url) &&
            Objects.equals(user, jdbc.user) &&
            Objects.equals(password, jdbc.password) &&
            Objects.equals(defaultdb, jdbc.defaultdb);
      }
      catch(Exception ex) {
         // ignore
      }

      return false;
   }

   /**
    * Create a clone of this object.
    */
   @Override
   @SuppressWarnings("unchecked")
   public Object clone() {
      JDBCDataSource source = (JDBCDataSource) super.clone();
      source.poolProperties = Tool.deepCloneMap(poolProperties);

      return source;
   }

   private String[] systemSchemas = null;
   private String[] systemCatalogs = null;
   private int dbtype = JDBC_ACCESS;
   private String driver = AccessDatabaseType.DRIVER;
   private String url = AccessDatabaseType.URL_PREFIX + AccessDatabaseType.FILE_PATH;
   // for split url setting
   private DatabaseDefinition databaseDefinition;
   private String customUrl;
   private boolean customEditMode; // display as custom edit mode.

   private transient String rurl;
   private boolean login = true; // need login
   private boolean save = true; // save user/password
   private String user = ""; // user id
   private String password = "";  // password
   private String product = null; // database product name
   private String version = null; // database product version
   private String defaultdb = null; // set the default database
   private int isolation = -1; // default
   private boolean ansiJoin = false; // use ansi syntax for join
   private int option = DEFAULT_OPTION;
   private TreeMap<String, String> poolProperties = new TreeMap<>();
   private boolean custom = false;
   private boolean unasgn = false;
   private String runtimeProductName = null;

   public static final String JDBC_URL_TEMPLATE = "jdbc:<subprotocol>://<host>[:<port>]/databaseName";

   private static final Logger LOG = LoggerFactory.getLogger(JDBCDataSource.class);
}
