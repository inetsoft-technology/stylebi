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
import inetsoft.web.admin.content.database.DatabaseNameInfo;
import inetsoft.web.admin.content.database.NetworkLocation;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Implementation of <tt>DatabaseType</tt> for MySQL databases.
 */
@Component
public final class MySQLDatabaseType
   extends AbstractDatabaseType<MySQLDatabaseType.MySQLDatabaseInfo>
{
   /**
    * Creates a new instance of <tt>MySQLDatabaseType</tt>.
    */
   public MySQLDatabaseType() {
      super(TYPE, DRIVER);
   }

   @Override
   protected NetworkLocation parseUrl(String url, MySQLDatabaseInfo info) {
      NetworkLocation location = new NetworkLocation();
      location.setHostName(DEFAULT_HOST);
      location.setPortNumber(DEFAULT_PORT);

      Matcher matcher = PATTERN.matcher(url);

      if(matcher.matches()) {
         location.setHostName(matcher.group(1));

         if(matcher.group(2) != null) {
            location.setPortNumber(Integer.parseInt(matcher.group(2)));
         }

         if(matcher.group(3) != null) {
            info.setDatabaseName(matcher.group(3));
         }

         if(matcher.group(4) != null) {
            info.setProperties(matcher.group(4));
         }
      }

      return location;
   }

   @Override
   public MySQLDatabaseInfo createDatabaseInfo() {
      return new MySQLDatabaseInfo();
   }

   @Override
   public String formatUrl(NetworkLocation network, MySQLDatabaseInfo info) {
      String dbName = info != null ? info.getDatabaseName() : null;
      String properties = info != null ? info.getProperties() : null;

      return MySQLDatabaseType.formatUrl0(network.getHostName(),
         network.getPortNumber(), dbName, properties);
   }

   public static String formatUrl0(String hostName, int port, String dbName, String properties) {
      String url;

      if(StringUtils.isEmpty(dbName)) {
         url = String.format(FORMAT1, hostName, port);
      }
      else {
         url = String.format(FORMAT2, hostName, port, dbName);
      }

      if(!StringUtils.isEmpty(properties)) {
         url = url + joinPropertiesChar(TYPE) + properties;
      }

      return url;
   }

   @Override
   public int getDefaultPort() {
      return DEFAULT_PORT;
   }

   public static final String TYPE = "MYSQL";
   public static final String DEFAULT_HOST = "localhost";
   public static final int DEFAULT_PORT = 3306;
   private static final String DRIVER = "com.mysql.jdbc.Driver";
   private static final Pattern PATTERN =
      Pattern.compile("^jdbc:mysql://([^/:<]+)(?::([0-9]+))?(?:/([^?]+))?(?:\\?(.+))?$");
   public static final String FORMAT1 = "jdbc:mysql://%s:%d";
   public static final String FORMAT2 = "jdbc:mysql://%s:%d/%s";

   /**
    * Implementation of <tt>DatabaseInfo</tt> for MySQL databases.
    */
   @JsonTypeName(TYPE)
   public static final class MySQLDatabaseInfo extends DatabaseNameInfo {
   }
}
