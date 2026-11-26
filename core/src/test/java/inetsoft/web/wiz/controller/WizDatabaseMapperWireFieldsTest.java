/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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

package inetsoft.web.wiz.controller;

import inetsoft.web.admin.content.database.DatabaseDefinition;
import inetsoft.web.admin.content.database.DatabaseTypeService;
import inetsoft.web.admin.content.database.types.MySQLDatabaseType;
import inetsoft.web.admin.content.database.types.MySQLDatabaseType.MySQLDatabaseInfo;
import inetsoft.web.wiz.model.WizAuthentication;
import inetsoft.web.wiz.model.WizDatabaseDefinition;
import inetsoft.web.wiz.model.WizDatabaseInfo;
import inetsoft.web.wiz.model.WizNetworkLocation;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

/**
 * Bug #82092: the wire form carries the Portal's URL "Properties" text and the "Disable Default
 * Connection" flag in both directions.
 */
@Tag("core")
class WizDatabaseMapperWireFieldsTest {
   @Test
   void readEmitsTheStoredPropertiesAndUnasgn() {
      MySQLDatabaseInfo info = new MySQLDatabaseType().createDatabaseInfo();
      info.setDatabaseName("d");
      info.setProperties("useSSL=true");
      DatabaseDefinition stored = new DatabaseDefinition();
      stored.setName("db");
      stored.setType(MySQLDatabaseType.TYPE);
      stored.setInfo(info);
      stored.setUnasgn(true);

      WizDatabaseDefinition wire = mapper().toWizDefinition(stored);

      assertEquals("useSSL=true", wire.info().properties());
      assertEquals(Boolean.TRUE, wire.unasgn());
   }

   @Test
   void writeMapsPropertiesAndKeepsNullAsNullSoTheStoredValueCanBeCarriedOver() {
      DatabaseDefinition withText = mapper().toDatabaseDefinition(wire("useSSL=true", null), "db");
      assertEquals("useSSL=true", withText.getInfo().getProperties());

      DatabaseDefinition omitted = mapper().toDatabaseDefinition(wire(null, null), "db");
      assertNull(omitted.getInfo().getProperties());

      DatabaseDefinition cleared = mapper().toDatabaseDefinition(wire("", null), "db");
      assertEquals("", cleared.getInfo().getProperties());
   }

   @Test
   void theMapperNeverTakesUnasgnFromTheWire() {
      // The flag belongs to a base source; the base controller applies it itself, and an additional
      // connection (which converts through this method) must never pick it up.
      DatabaseDefinition result = mapper().toDatabaseDefinition(wire(null, true), "db");

      assertFalse(result.isUnasgn());
   }

   private static WizDatabaseMapper mapper() {
      DatabaseTypeService types = mock(DatabaseTypeService.class);
      doReturn(new MySQLDatabaseType()).when(types).getDatabaseType(MySQLDatabaseType.TYPE);
      return new WizDatabaseMapper(types);
   }

   private static WizDatabaseDefinition wire(String properties, Boolean unasgn) {
      return new WizDatabaseDefinition(
         "db", null, MySQLDatabaseType.TYPE, new WizNetworkLocation("h", 3306),
         new WizAuthentication(true, "u", null),
         new WizDatabaseInfo("d", null, null, null, null, null, null, null, false, null, null,
                             properties),
         false, -1, 3, null, false, false, unasgn);
   }
}
