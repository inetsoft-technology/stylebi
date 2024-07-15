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
import inetsoft.web.admin.content.database.DatabaseInfo;
import inetsoft.web.admin.content.database.NetworkLocation;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Implementation of <tt>DatabaseType</tt> for Oracle databases.
 */
@Component
public final class OracleDatabaseType
   extends AbstractDatabaseType<OracleDatabaseType.OracleDatabaseInfo>
{
   /**
    * Creates a new instance of <tt>OracleDatabaseType</tt>.
    */
   public OracleDatabaseType() {
      super(TYPE, DRIVER);
   }

   @Override
   protected NetworkLocation parseUrl(String url, OracleDatabaseInfo info) {
      NetworkLocation network = new NetworkLocation();
      network.setHostName("localhost");
      network.setPortNumber(1521);

      Matcher matcher = PATTERN.matcher(url);

      if(matcher.matches()) {
         network.setHostName(matcher.group(1));
         network.setPortNumber(Integer.parseInt(matcher.group(2)));
         info.setSid(matcher.group(3));
         info.setProperties(matcher.group(4));
      }

      return network;
   }

   @Override
   public OracleDatabaseInfo createDatabaseInfo() {
      return new OracleDatabaseInfo();
   }

   @Override
   public String formatUrl(NetworkLocation network, OracleDatabaseInfo info) {
      return formatUrl0(network.getHostName(), network.getPortNumber(),
         info.getSid(), info.getProperties());
   }

   public static String formatUrl0(String hostName, int port, String dbName, String properties) {
      String url = String.format(FORMAT, hostName, port, StringUtils.isEmpty(dbName) ? "orcl" : dbName);

      if(!StringUtils.isEmpty(properties)) {
         url = url + joinPropertiesChar(TYPE) + properties;
      }

      return url;
   }

   @Override
   public int getDefaultPort() {
      return DEFAULT_PORT;
   }

   public static final int DEFAULT_PORT = 1521;
   public static final String TYPE = "ORACLE";
   private static final String DRIVER = "oracle.jdbc.OracleDriver";
   private static final Pattern PATTERN =
      Pattern.compile("^jdbc:oracle:thin:@([^:]+):(\\d+)[:/]([^?]+)?(?:\\?(.+))?$");
   private static final String FORMAT = "jdbc:oracle:thin:@%s:%d:%s";

   /**
    * Implementation of <tt>DatabaseInfo</tt> for Oracle databases.
    */
   @JsonTypeName(TYPE)
   public static final class OracleDatabaseInfo extends DatabaseInfo {
      /**
       * Gets the SID of the database.
       *
       * @return the SID.
       */
      public String getSid() {
         return sid;
      }

      /**
       * Sets the SID of the database.
       *
       * @param sid the SID.
       */
      public void setSid(String sid) {
         this.sid = sid;
      }

      private String sid;
   }
}
