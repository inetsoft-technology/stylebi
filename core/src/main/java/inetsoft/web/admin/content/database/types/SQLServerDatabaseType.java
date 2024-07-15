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
package inetsoft.web.admin.content.database.types;

import com.fasterxml.jackson.annotation.JsonTypeName;
import inetsoft.web.admin.content.database.DatabaseNameInfo;
import inetsoft.web.admin.content.database.NetworkLocation;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Implementation of <tt>DatabaseType</tt> for SQL Server databases.
 */
@Component
public final class SQLServerDatabaseType
   extends AbstractDatabaseType<SQLServerDatabaseType.SQLServerDatabaseInfo>
{
   /**
    * Creates a new instance of <tt>SQLServerDatabaseType</tt>.
    */
   public SQLServerDatabaseType() {
      super(TYPE, DRIVER);
   }

   @Override
   protected NetworkLocation parseUrl(String url, SQLServerDatabaseInfo info) {
      NetworkLocation location = new NetworkLocation();
      location.setHostName("localhost");
      location.setPortNumber(1433);
      Matcher matcher = PATTERN.matcher(url);

      if(matcher.matches()) {
         location.setHostName(matcher.group(1));
         info.setInstanceName(matcher.group(2));
         info.setDatabaseName(matcher.group(5));

         if(matcher.group(3) != null) {
            location.setPortNumber(Integer.parseInt(matcher.group(3)));
         }

         String properties = "";

         if(matcher.group(4) != null) {
            properties += matcher.group(4);

            if(properties.endsWith(";")) {
               properties = properties.substring(0, properties.length() - 1);
            }
         }

         if(matcher.group(6) != null) {
            properties += matcher.group(6);

            if(properties.endsWith(";")) {
               properties = properties.substring(0, properties.length() - 1);
            }
         }

         info.setProperties(properties);
      }
      else {
         Matcher matcher1 = PATTERN1.matcher(url);

         if(matcher1.matches()) {
            location.setHostName(matcher1.group(1));
            info.setInstanceName(matcher1.group(2));

            if(matcher1.group(3) != null) {
               location.setPortNumber(Integer.parseInt(matcher1.group(3)));
            }

            String properties = "";

            if(matcher1.group(4) != null) {
               properties += matcher1.group(4);

               if(properties.endsWith(";")) {
                  properties = properties.substring(0, properties.length() - 1);
               }
            }

            info.setProperties(properties);
         }
      }

      return location;
   }

   @Override
   public SQLServerDatabaseInfo createDatabaseInfo() {
      return new SQLServerDatabaseInfo();
   }

   @Override
   public String formatUrl(NetworkLocation network, SQLServerDatabaseInfo info) {
      return formatUrl0(network.getHostName(), network.getPortNumber(),
                        info.getInstanceName(), info.getDatabaseName(), info.getProperties());
   }

   public static String formatUrl0(String hostName, int port, String instanceName,
                                   String dbName, String properties)
   {
      String url;

      if(StringUtils.isEmpty(instanceName)) {
         url = String.format(FORMAT1, hostName, port);
      }
      else {
         url = String.format(FORMAT2, hostName, instanceName, port);
      }

      if(!StringUtils.isEmpty(properties)) {
         url = url + joinPropertiesChar(TYPE) + properties;
      }

      if(!StringUtils.isEmpty(dbName)) {
         url = url + ";database=" + dbName;
      }

      return url;
   }

   @Override
   public int getDefaultPort() {
      return DEFAULT_PORT;
   }

   public static final int DEFAULT_PORT = 1433;
   public static final String TYPE = "SQLSERVER";
   private static final String DRIVER =
      "com.microsoft.sqlserver.jdbc.SQLServerDriver";
   private static final Pattern PATTERN = Pattern.compile(
      "^jdbc:sqlserver://([^\\\\:]+)(?:\\\\([^:]+))?(?::(\\d+))(?:;((?:.+;)?)(?:database(?:Name)?=([^;]+)((?:;.+)?))?)?$");
   private static final Pattern PATTERN1 = Pattern.compile(
           "^jdbc:sqlserver://([^\\\\:]+)(?:\\\\([^:]+))?(?::(\\d+))(?:;((?:.+)?))?$");
   private static final String FORMAT1 = "jdbc:sqlserver://%s:%d";
   private static final String FORMAT2 = "jdbc:sqlserver://%s\\%s:%d";

   /**
    * Implementation of <tt>DatabaseInfo</tt> for SQL server databases.
    */
   @JsonTypeName(TYPE)
   public static final class SQLServerDatabaseInfo extends DatabaseNameInfo {
      /**
       * Gets the name of the database instance.
       *
       * @return the instance name.
       */
      public String getInstanceName() {
         return instanceName;
      }

      /**
       * Sets the name of the database instance.
       *
       * @param instanceName the instance name.
       */
      public void setInstanceName(String instanceName) {
         this.instanceName = instanceName;
      }

      private String instanceName;
   }
}
