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
 * Implementation of <tt>DatabaseType</tt> for PostgreSQL databases.
 */
@Component
public final class PostgreSQLDatabaseType
   extends AbstractDatabaseType<PostgreSQLDatabaseType.PostgreSQLDatabaseInfo>
{
   /**
    * Creates a new instance of <tt>PostgreSQLDatabaseType</tt>.
    */
   public PostgreSQLDatabaseType() {
      super(TYPE, DRIVER);
   }

   @Override
   protected NetworkLocation parseUrl(String url, PostgreSQLDatabaseInfo info) {
      NetworkLocation location = new NetworkLocation();
      location.setHostName("localhost");
      location.setPortNumber(5432);

      Matcher matcher = PATTERN.matcher(url);

      if(matcher.matches()) {
         location.setHostName(matcher.group(1));

         if(matcher.group(2) != null) {
            location.setPortNumber(Integer.parseInt(matcher.group(2)));
         }

         String dbName = matcher.group(3);
         info.setDatabaseName(StringUtils.isEmpty(dbName) ? "" : dbName);
         info.setProperties(matcher.group(4));
      }

      return location;
   }

   @Override
   public PostgreSQLDatabaseInfo createDatabaseInfo() {
      return new PostgreSQLDatabaseInfo();
   }

   @Override
   public String formatUrl(NetworkLocation network, PostgreSQLDatabaseInfo info) {
      return formatUrl0(network.getHostName(), network.getPortNumber(),
         info.getDatabaseName(), info.getProperties());
   }

   public static String formatUrl0(String hostName, int port, String dbName, String properties) {
      dbName = StringUtils.isEmpty(dbName) ? "" : dbName;
      String url = String.format(FORMAT, hostName, port, dbName);

      if(!StringUtils.isEmpty(properties)) {
         url = url + joinPropertiesChar(TYPE) + properties;
      }

      return url;
   }

   @Override
   public int getDefaultPort() {
      return DEFAULT_PORT;
   }

   public static final int DEFAULT_PORT = 5432;
   public static final String TYPE = "POSTGRESQL";
   private static final String DRIVER = "org.postgresql.Driver";
   private static final Pattern PATTERN =
      Pattern.compile("^jdbc:postgresql://([^/^:]+):?([0-9]+)?/([^?]+)?(?:\\?(.+))?$");
   private static final String FORMAT = "jdbc:postgresql://%s:%d/%s";

   /**
    * Implementation of <tt>DatabaseInfo</tt> for PostgreSQL databases.
    */
   @JsonTypeName(TYPE)
   public static final class PostgreSQLDatabaseInfo extends DatabaseNameInfo {
   }
}
