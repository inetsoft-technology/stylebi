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
import org.springframework.util.StringUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Abstract of <tt>DatabaseType</tt> for DB2 databases.
 */
public class AbstractDB2DatabaseType
   extends AbstractDatabaseType<AbstractDB2DatabaseType.DB2DatabaseInfo>
{
   /**
    * Creates a new instance of <tt>DB2DatabaseType</tt>.
    */
   protected AbstractDB2DatabaseType(String driver) {
      super(TYPE, driver);
   }

   @Override
   protected NetworkLocation parseUrl(String url, DB2DatabaseInfo info) {
      NetworkLocation location = new NetworkLocation();
      location.setHostName("localhost");
      location.setPortNumber(50000);

      Matcher matcher = PATTERN.matcher(url);

      if(matcher.matches()) {
         location.setHostName(matcher.group(1));

         if(matcher.group(2) != null) {
            location.setPortNumber(Integer.parseInt(matcher.group(2)));
         }

         info.setDatabaseName(matcher.group(3));
         info.setProperties(matcher.group(4));
      }

      return location;
   }

   @Override
   public DB2DatabaseInfo createDatabaseInfo() {
      return new DB2DatabaseInfo();
   }

   @Override
   public String formatUrl(NetworkLocation network, DB2DatabaseInfo info) {
      String dbName = info != null ? info.getDatabaseName() : null;
      String properties = info != null ? info.getProperties() : null;

      return formatUrl0(network.getHostName(), network.getPortNumber(),
         dbName, properties);
   }

   public static String formatUrl0(String hostName, int port, String dbName, String properties) {
      String url = String.format(FORMAT, hostName, port, dbName != null ? dbName : "");

      if(!StringUtils.isEmpty(properties)) {
         url = url + joinPropertiesChar(TYPE) + properties;
      }

      return url;
   }

   @Override
   public int getDefaultPort() {
      return DEFAULT_PORT;
   }

   public static final int DEFAULT_PORT = 50000;
   public static final String TYPE = "DB2";
   private static final Pattern PATTERN =
      Pattern.compile("^jdbc:db2://([^/:]+)(?::([0-9]+))?/([^?]+)?(?:\\?(.+))?$");
   private static final String FORMAT = "jdbc:db2://%s:%d/%s";

   /**
    * Implementation of <tt>DatabaseInfo</tt> for DB2 databases.
    */
   @JsonTypeName(TYPE)
   public static final class DB2DatabaseInfo extends DatabaseNameInfo {
   }
}
