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

import inetsoft.web.admin.content.database.NetworkLocation;
import inetsoft.web.admin.content.database.types.SQLServerDatabaseType.SQLServerDatabaseInfo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("core")
class SQLServerDatabaseTypeTest {
   private final SQLServerDatabaseType type = new SQLServerDatabaseType();

   private String roundTrip(String url, String instance, String db, String props, int port) {
      SQLServerDatabaseInfo info = type.createDatabaseInfo();
      NetworkLocation loc = type.parse(null, url, info);
      assertEquals("h", loc.getHostName());
      assertEquals(port, loc.getPortNumber());
      assertEquals(instance, info.getInstanceName());
      assertEquals(db, info.getDatabaseName());
      assertEquals(props, info.getProperties());
      return type.formatUrl(loc, info);
   }

   @Test
   void databaseNameFirstThenProperties() {
      assertEquals("jdbc:sqlserver://h\\inst:1434;encrypt=true;database=db",
         roundTrip("jdbc:sqlserver://h\\inst:1434;databaseName=db;encrypt=true",
                   "inst", "db", "encrypt=true", 1434));
   }

   @Test
   void databaseNameFirstThenSeveralProperties() {
      assertEquals("jdbc:sqlserver://h:1433;a=1;b=2;database=db",
         roundTrip("jdbc:sqlserver://h:1433;database=db;a=1;b=2", null, "db", "a=1;b=2", 1433));
   }

   @Test
   void propertiesBeforeDatabaseName() {
      assertEquals("jdbc:sqlserver://h\\inst:1434;encrypt=true;database=db",
         roundTrip("jdbc:sqlserver://h\\inst:1434;encrypt=true;databaseName=db",
                   "inst", "db", "encrypt=true", 1434));
   }

   @Test
   void propertiesOnBothSides() {
      assertEquals("jdbc:sqlserver://h:1433;a=1;b=2;database=db",
         roundTrip("jdbc:sqlserver://h:1433;a=1;databaseName=db;b=2", null, "db", "a=1;b=2", 1433));
   }

   @Test
   void databaseNameOnly() {
      assertEquals("jdbc:sqlserver://h:1433;database=db",
         roundTrip("jdbc:sqlserver://h:1433;databaseName=db", null, "db", "", 1433));
   }

   @Test
   void propertiesOnly() {
      assertEquals("jdbc:sqlserver://h:1433;encrypt=true",
         roundTrip("jdbc:sqlserver://h:1433;encrypt=true", null, null, "encrypt=true", 1433));
   }

   @Test
   void hostAndPortOnly() {
      assertEquals("jdbc:sqlserver://h:1433",
         roundTrip("jdbc:sqlserver://h:1433", null, null, "", 1433));
   }
}
