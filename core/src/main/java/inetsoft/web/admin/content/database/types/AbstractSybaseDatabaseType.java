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
import org.springframework.util.StringUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Implementation of <tt>DatabaseType</tt> for Sybase databases.
 */
public class AbstractSybaseDatabaseType
   extends AbstractDatabaseType<AbstractSybaseDatabaseType.SybaseDatabaseInfo>
{
   /**
    * Creates a new instance of <tt>SybaseDatabaseType</tt>.
    */
   protected AbstractSybaseDatabaseType(String driver) {
      super(TYPE, driver);
   }

   @Override
   protected NetworkLocation parseUrl(String url, SybaseDatabaseInfo info) {
      NetworkLocation location = new NetworkLocation();
      location.setHostName("localhost");
      location.setPortNumber(5000);

      if(url == null) {
         return location;
      }

      Matcher matcher = PATTERN.matcher(url);

      if(matcher.matches()) {
         location.setHostName(matcher.group(1));
         location.setPortNumber(Integer.parseInt(matcher.group(2)));
         info.setDatabaseName(matcher.group(3));
         info.setProperties(matcher.group(4));
      }

      return location;
   }

   @Override
   public SybaseDatabaseInfo createDatabaseInfo() {
      return new SybaseDatabaseInfo();
   }

   @Override
   public String formatUrl(NetworkLocation network, SybaseDatabaseInfo info) {
      String dbName = info != null ? info.getDatabaseName() : null;
      String properties = info != null ? info.getProperties() : null;

      return formatUrl0(network.getHostName(), network.getPortNumber(),
         dbName, properties);
   }

   public static String formatUrl0(String hostName, int port, String dbName, String properties) {
      String url;

      if(StringUtils.isEmpty(dbName)) {
         url = String.format(FORMAT2, hostName, port);
      }
      else {
         url = String.format(FORMAT1, hostName, port, dbName);
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

   public static final int DEFAULT_PORT = 5000;
   public static final String TYPE = "SYBASE";
   private static final Pattern PATTERN =
      Pattern.compile("^jdbc:sybase:Tds:([^/]+):([0-9]+)(?:/([^?]+))?(?:\\?(.+))?$");
   private static final String FORMAT1 = "jdbc:sybase:Tds:%s:%d/%s";
   private static final String FORMAT2 = "jdbc:sybase:Tds:%s:%d";

   /**
    * Implementation of <tt>DatabaseInfo</tt> for Sybase databases.
    */
   @JsonTypeName(TYPE)
   public static final class SybaseDatabaseInfo extends DatabaseNameInfo {
   }
}
