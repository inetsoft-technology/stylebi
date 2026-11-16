/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

import inetsoft.web.admin.content.database.NetworkLocation;
import inetsoft.web.admin.content.database.types.InformixDatabaseType.InformixDatabaseInfo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Bug #80300: the Informix URL pattern must read back every URL that
 * {@link InformixDatabaseType#formatUrl} writes.
 */
@Tag("core")
class InformixDatabaseTypeTest {
   private static final String DRIVER = "com.informix.jdbc.IfxDriver";
   private final InformixDatabaseType type = new InformixDatabaseType();

   private InformixDatabaseInfo info;

   private NetworkLocation parse(String url) {
      info = type.createDatabaseInfo();
      return type.parse(DRIVER, url, info);
   }

   @Test
   void propertiesAfterDbNameWithColonAreParsed() {
      NetworkLocation net = parse("jdbc:informix-sqli://dbhost:9088/mydb:prop=value");

      assertEquals("dbhost", net.getHostName());
      assertEquals(9088, net.getPortNumber());
      assertEquals("mydb", info.getDatabaseName());
      assertEquals("prop=value", info.getProperties());
      assertNull(info.getServerName());
      assertNull(info.getDatabaseLocale());
   }

   @Test
   void multiplePropertiesAfterColonAreParsed() {
      parse("jdbc:informix-sqli://dbhost:9088/mydb:a=1;b=2");

      assertEquals("mydb", info.getDatabaseName());
      assertEquals("a=1;b=2", info.getProperties());
   }

   @Test
   void serverNameAndLocaleStillParsed() {
      NetworkLocation net = parse(
         "jdbc:informix-sqli://dbhost:9088/mydb:INFORMIXSERVER=srv;DB_LOCALE=en_US.utf8");

      assertEquals("dbhost", net.getHostName());
      assertEquals(9088, net.getPortNumber());
      assertEquals("mydb", info.getDatabaseName());
      assertEquals("srv", info.getServerName());
      assertEquals("en_US.utf8", info.getDatabaseLocale());
      assertNull(info.getProperties());
   }

   @Test
   void serverNameLocaleAndPropertiesStillParsed() {
      parse("jdbc:informix-sqli://dbhost:9088/mydb:INFORMIXSERVER=srv;" +
         "DB_LOCALE=en_US.utf8;a=1;b=2");

      assertEquals("mydb", info.getDatabaseName());
      assertEquals("srv", info.getServerName());
      assertEquals("en_US.utf8", info.getDatabaseLocale());
      assertEquals("a=1;b=2", info.getProperties());
   }

   @Test
   void plainUrlAndTrailingSeparatorStillParsed() {
      NetworkLocation net = parse("jdbc:informix-sqli://dbhost:9088/mydb");
      assertEquals("dbhost", net.getHostName());
      assertEquals(9088, net.getPortNumber());
      assertEquals("mydb", info.getDatabaseName());
      assertNull(info.getProperties());

      net = parse("jdbc:informix-sqli://dbhost:9088/mydb:");
      assertEquals("dbhost", net.getHostName());
      assertEquals("mydb", info.getDatabaseName());
      assertNull(info.getProperties());

      net = parse("jdbc:informix-sqli://dbhost:9088/mydb;");
      assertEquals("dbhost", net.getHostName());
      assertEquals("mydb", info.getDatabaseName());
      assertNull(info.getProperties());
   }

   @Test
   void formatThenParseRoundTripsAllCombinations() {
      String[] servers = { null, "", "srv" };
      String[] locales = { null, "", "en_US.utf8" };
      String[] props = { null, "", "p=1", "p=1;q=2" };

      for(String server : servers) {
         for(String locale : locales) {
            for(String prop : props) {
               InformixDatabaseInfo in = type.createDatabaseInfo();
               in.setDatabaseName("mydb");
               in.setServerName(server);
               in.setDatabaseLocale(locale);
               in.setProperties(prop);
               NetworkLocation netIn = new NetworkLocation();
               netIn.setHostName("dbhost");
               netIn.setPortNumber(9088);

               String url = type.formatUrl(netIn, in);
               String msg = "url=" + url;
               NetworkLocation netOut = parse(url);

               assertEquals("dbhost", netOut.getHostName(), msg);
               assertEquals(9088, netOut.getPortNumber(), msg);
               assertEquals("mydb", info.getDatabaseName(), msg);
               assertEquals(blankToNull(server), info.getServerName(), msg);
               assertEquals(blankToNull(locale), info.getDatabaseLocale(), msg);
               assertEquals(blankToNull(prop), info.getProperties(), msg);
            }
         }
      }
   }

   private static String blankToNull(String s) {
      return s == null || s.isEmpty() ? null : s;
   }
}
