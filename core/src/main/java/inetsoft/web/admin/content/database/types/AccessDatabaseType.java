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
import inetsoft.util.Tool;
import inetsoft.web.admin.content.database.DatabaseInfo;
import inetsoft.web.admin.content.database.NetworkLocation;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Implementation of <tt>DatabaseType</tt> for MS Access data sources.
 */
@Component
public final class AccessDatabaseType
   extends AbstractDatabaseType<AccessDatabaseType.AccessDatabaseInfo>
{
   /**
    * Creates a new instance of <tt>AccessDatabaseType</tt>.
    */
   public AccessDatabaseType() {
      super(TYPE, DRIVER);
   }

   @Override
   protected NetworkLocation parseUrl(String url, AccessDatabaseInfo info) {
      Matcher matcher = PATTERN.matcher(url);

      if(matcher.matches()) {
         info.setDataSourceName(Tool.equals("null", matcher.group(1)) ? null : matcher.group(1));
         info.setProperties(matcher.group(2));
      }

      info.setDriverClass(DRIVER);
      info.setJdbcUrl(url);

      return null;
   }

   @Override
   public AccessDatabaseInfo createDatabaseInfo() {
      return new AccessDatabaseInfo();
   }

   @Override
   public String formatUrl(NetworkLocation network, AccessDatabaseInfo info) {
      return formatUrl0(info.getDataSourceName(), info.getProperties());
   }

   public static String formatUrl0(String filePath, String properties) {
      String url = String.format(FORMAT, filePath == null ? "" : filePath);

      if(!StringUtils.isEmpty(properties)) {
         url = url + joinPropertiesChar(TYPE) + properties;
      }

      return url;
   }

   @Override
   public int getDefaultPort() {
      return 0;
   }

   public static final String TYPE = "ACCESS";
   public static final String DRIVER = "net.ucanaccess.jdbc.UcanaccessDriver";
   public static final String URL_PREFIX = "jdbc:ucanaccess://";
   public static final String FILE_PATH = "{file}";
   private static final Pattern PATTERN = Pattern.compile("^" + URL_PREFIX + "([^;]*)(?:;(.+))?$");
   private static final String FORMAT = URL_PREFIX + "%s";

   /**
    * Implementation of <tt>DatabaseInfo</tt> for MS Access data sources.
    */
   @JsonTypeName(TYPE)
   public static final class AccessDatabaseInfo extends DatabaseInfo {
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
      /**
       * Gets the name of the ODBC data source name (DSN).
       *
       * @return the data source name.
       */
      public String getDataSourceName() {
         return dataSourceName;
      }

      /**
       * Sets the name of the ODBC data source name (DSN).
       *
       * @param dataSourceName the data source name.
       */
      public void setDataSourceName(String dataSourceName) {
         this.dataSourceName = dataSourceName;
      }

      public String getTestQuery() {
         return testQuery;
      }

      public void setTestQuery(String testQuery) {
         this.testQuery = testQuery;
      }

      private String dataSourceName;
      private String driverClass;
      private String jdbcUrl;
      private String testQuery;
   }
}
