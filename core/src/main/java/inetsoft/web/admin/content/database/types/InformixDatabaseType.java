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
package inetsoft.web.admin.content.database.types;

import com.fasterxml.jackson.annotation.JsonTypeName;
import inetsoft.web.admin.content.database.DatabaseNameInfo;
import inetsoft.web.admin.content.database.NetworkLocation;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Implementation of <tt>DatabaseType</tt> for Informix databases.
 */
@Component
public final class InformixDatabaseType
   extends AbstractDatabaseType<InformixDatabaseType.InformixDatabaseInfo>
{
   /**
    * Creates a new instance of <tt>InformixDatabaseType</tt>.
    */
   public InformixDatabaseType() {
      super(TYPE, DRIVER);
   }

   @Override
   protected NetworkLocation parseUrl(String url, InformixDatabaseInfo info) {
      NetworkLocation network = new NetworkLocation();
      network.setHostName("localhost");
      network.setPortNumber(1526);

      Matcher matcher = PATTERN.matcher(url);

      if(matcher.matches()) {
         network.setHostName(matcher.group(1));
         network.setPortNumber(Integer.parseInt(matcher.group(2)));
         String dbName = matcher.group(3);
         info.setDatabaseName(StringUtils.isEmpty(dbName) ? "" : dbName);

         if(matcher.group(4) != null) {
            info.setProperties(matcher.group(4));
         }
      }

      HashMap<String, String> map = getUrlParams(url);

      for(String key : map.keySet()) {
         Object value = map.get(key);

         if(value == null) {
            continue;
         }

         if(INFORMIX_SERVER.equalsIgnoreCase(key)) {
            info.setServerName(map.get(key) + "");
         }

         if(DB_LOCALE.equalsIgnoreCase(key)) {
            info.setDatabaseLocale(map.get(key) + "");
         }
      }

      return network;
   }

   private HashMap<String, String> getUrlParams(String url) {
      HashMap<String, String> map = new HashMap<>();

      if(url == null) {
         return map;
      }

      int idx = url.lastIndexOf(":");

      if(idx == -1 || idx + 1 >= url.length()) {
         return map;
      }

      url = url.substring(idx + 1);
      String[] pairs = url.split(";");

      for(String pair : pairs) {
         String[] keyValuePair = pair.split("=");

         if(keyValuePair.length == 2) {
            map.put(keyValuePair[0], keyValuePair[1]);
         }
      }

      return map;
   }

   @Override
   public InformixDatabaseInfo createDatabaseInfo() {
      return new InformixDatabaseInfo();
   }

   @Override
   public String formatUrl(NetworkLocation network, InformixDatabaseInfo info) {
      return formatUrl0(network.getHostName(), network.getPortNumber(), info.getDatabaseName(),
         info.getServerName(), info.getDatabaseLocale(), info.getProperties());
   }

   public static String formatUrl0(String hostName, int port, String dbName,
                                   String serverName, String dbLocale, String properties)
   {
      String url;
      String url2 = "";
      String format1 = !StringUtils.isEmpty(dbName) ? FORMAT1_0 : FORMAT1_1;
      String joinPropChar = StringUtils.isEmpty(serverName) && StringUtils.isEmpty(dbLocale) ?
         joinPropertiesChar(TYPE) : ";";

      if(!StringUtils.isEmpty(serverName)) {
         String format2 = StringUtils.isEmpty(dbLocale) ? FORMAT2_0 : FORMAT2_1;
         url2 = String.format(format2, serverName, dbLocale);
      }
      else if(!StringUtils.isEmpty(dbLocale)) {
         url2 = String.format(FORMAT2_2, dbLocale);
      }

      url = String.format(format1, hostName, port, dbName) + url2;

      if(!StringUtils.isEmpty(properties)) {
         url = url + joinPropChar + properties;
      }

      return url;
   }

   @Override
   public int getDefaultPort() {
      return DEFAULT_PORT;
   }

   public static final int DEFAULT_PORT = 1526;
   public static final String TYPE = "INFORMIX";
   private static final String DRIVER = "com.informix.jdbc.IfxDriver";
   private static final Pattern PATTERN = Pattern.compile(
      "^jdbc:informix-sqli://([^/:]+):(\\d+)/([^:;]+)" +
         "(?i::informixserver=(?:[^;]+))?(?i:;db_locale=(?:[^;]+))?(?:(?:(?::|;)?(?!.))|(?::|;((?::?.+)+)))?$");
   private static final String FORMAT1_0 = "jdbc:informix-sqli://%s:%d/%s";
   private static final String FORMAT1_1 = "jdbc:informix-sqli://%s:%d";
   private static final String FORMAT2_0 = ":INFORMIXSERVER=%s";
   private static final String FORMAT2_1 = ":INFORMIXSERVER=%s;db_locale=%s";
   private static final String FORMAT2_2 = ";db_locale=%s";
   private static final String INFORMIX_SERVER = "informixserver";
   private static final String DB_LOCALE = "db_locale";

   /**
    * Implementation of <tt>DatabaseInfo</tt> for Informix databases.
    */
   @JsonTypeName(TYPE)
   public static final class InformixDatabaseInfo extends DatabaseNameInfo {
      /**
       * Gets the name of the database server.
       *
       * @return the server name.
       */
      public String getServerName() {
         return serverName;
      }

      /**
       * Sets the name of the database server.
       *
       * @param serverName the server name.
       */
      public void setServerName(String serverName) {
         this.serverName = serverName;
      }

      /**
       * Sets the database locale.
       *
       * @param dbLocale the locale of the database.
       */
      public void setDatabaseLocale(String dbLocale) {
         this.databaseLocale = dbLocale;
      }

      /**
       * Gets the database locale.
       *
       * @return the locale of the database.
       */
      public String getDatabaseLocale() {
         return databaseLocale;
      }

      private String serverName;
      private String databaseLocale;
   }
}
