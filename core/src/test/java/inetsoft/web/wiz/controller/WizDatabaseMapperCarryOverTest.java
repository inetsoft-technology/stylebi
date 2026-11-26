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
import inetsoft.web.admin.content.database.DatabaseInfo;
import inetsoft.web.admin.content.database.NetworkLocation;
import inetsoft.web.admin.content.database.types.AccessDatabaseType;
import inetsoft.web.admin.content.database.types.AccessDatabaseType.AccessDatabaseInfo;
import inetsoft.web.admin.content.database.types.MySQLDatabaseType;
import inetsoft.web.admin.content.database.types.MySQLDatabaseType.MySQLDatabaseInfo;
import inetsoft.web.admin.content.database.types.OracleDatabaseType;
import inetsoft.web.admin.content.database.types.OracleDatabaseType.OracleDatabaseInfo;
import inetsoft.web.admin.content.database.types.PostgreSQLDatabaseType;
import inetsoft.web.admin.content.database.types.PostgreSQLDatabaseType.PostgreSQLDatabaseInfo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #82088: an update rebuilds the connection info from the wiz contract, which carries no URL
 * properties, so the formatted URL lost them. These tests drive the real database types' parse
 * (read side) and formatUrl (write side).
 */
@Tag("core")
class WizDatabaseMapperCarryOverTest {
   @Test
   void postgresDescriptionOnlyUpdateKeepsPropertiesAndTheUrlByteExact() {
      String url = "jdbc:postgresql://127.0.0.1:1/d?ssl=true";
      PostgreSQLDatabaseType type = new PostgreSQLDatabaseType();
      PostgreSQLDatabaseInfo storedInfo = type.createDatabaseInfo();
      DatabaseDefinition current = stored(PostgreSQLDatabaseType.TYPE, storedInfo);
      NetworkLocation network = type.parse(null, url, storedInfo);
      assertEquals("ssl=true", storedInfo.getProperties());

      PostgreSQLDatabaseInfo info = type.createDatabaseInfo();
      info.setDatabaseName("d");
      DatabaseDefinition updated = stored(PostgreSQLDatabaseType.TYPE, info);
      assertEquals("jdbc:postgresql://127.0.0.1:1/d", type.formatUrl(network, info),
                   "without the carry-over the properties are gone");

      WizDatabaseMapper.carryOverUneditedSettings(current, updated);

      assertEquals("ssl=true", info.getProperties());
      assertEquals(url, type.formatUrl(network, info));
   }

   @Test
   void mysqlKeepsItsProperties() {
      String url = "jdbc:mysql://h:3306/d?useSSL=true&serverTimezone=UTC";
      MySQLDatabaseType type = new MySQLDatabaseType();
      MySQLDatabaseInfo storedInfo = type.createDatabaseInfo();
      NetworkLocation network = type.parse(null, url, storedInfo);

      MySQLDatabaseInfo info = type.createDatabaseInfo();
      info.setDatabaseName(storedInfo.getDatabaseName());
      WizDatabaseMapper.carryOverUneditedSettings(
         stored(MySQLDatabaseType.TYPE, storedInfo), stored(MySQLDatabaseType.TYPE, info));

      assertEquals(url, type.formatUrl(network, info));
   }

   @Test
   void oracleKeepsItsProperties() {
      String url = "jdbc:oracle:thin:@h:1521:orcl?oracle.net.ssl=true";
      OracleDatabaseType type = new OracleDatabaseType();
      OracleDatabaseInfo storedInfo = type.createDatabaseInfo();
      NetworkLocation network = type.parse(null, url, storedInfo);

      OracleDatabaseInfo info = type.createDatabaseInfo();
      info.setSid(storedInfo.getSid());
      WizDatabaseMapper.carryOverUneditedSettings(
         stored(OracleDatabaseType.TYPE, storedInfo), stored(OracleDatabaseType.TYPE, info));

      assertEquals(url, type.formatUrl(network, info));
   }

   @Test
   void accessKeepsItsDataSourceNameAndProperties() {
      AccessDatabaseType type = new AccessDatabaseType();
      AccessDatabaseInfo storedInfo = type.createDatabaseInfo();
      storedInfo.setDataSourceName("C:/x/db.accdb");
      storedInfo.setProperties("memory=false");
      String url = type.formatUrl(new NetworkLocation(), storedInfo);

      AccessDatabaseInfo info = type.createDatabaseInfo();
      WizDatabaseMapper.carryOverUneditedSettings(
         stored(AccessDatabaseType.TYPE, storedInfo), stored(AccessDatabaseType.TYPE, info));

      assertEquals("C:/x/db.accdb", info.getDataSourceName());
      assertEquals(url, type.formatUrl(new NetworkLocation(), info));
   }

   @Test
   void aChangedHostAndDatabaseOfTheSameTypeStillCarryTheProperties() {
      PostgreSQLDatabaseType type = new PostgreSQLDatabaseType();
      PostgreSQLDatabaseInfo storedInfo = type.createDatabaseInfo();
      type.parse(null, "jdbc:postgresql://h1:5432/d1?ssl=true", storedInfo);

      PostgreSQLDatabaseInfo info = type.createDatabaseInfo();
      info.setDatabaseName("d2");
      WizDatabaseMapper.carryOverUneditedSettings(
         stored(PostgreSQLDatabaseType.TYPE, storedInfo), stored(PostgreSQLDatabaseType.TYPE, info));

      NetworkLocation network = new NetworkLocation();
      network.setHostName("h2");
      network.setPortNumber(5433);
      assertEquals("jdbc:postgresql://h2:5433/d2?ssl=true", type.formatUrl(network, info));
   }

   @Test
   void customUrlModeIsLeftAloneAndNothingIsAppendedToTheCustomUrl() {
      MySQLDatabaseType type = new MySQLDatabaseType();
      MySQLDatabaseInfo storedInfo = type.createDatabaseInfo();
      storedInfo.setProperties("useSSL=true");
      MySQLDatabaseInfo info = type.createDatabaseInfo();
      info.setCustomEditMode(true);
      info.setCustomUrl("jdbc:mysql://x:3306/y?a=b");
      DatabaseDefinition current = stored(MySQLDatabaseType.TYPE, storedInfo);
      DatabaseDefinition updated = stored(MySQLDatabaseType.TYPE, info);

      WizDatabaseMapper.carryOverUneditedSettings(current, updated);
      WizDatabaseMapper.carryOverUneditedSettings(current, updated);

      assertTrue(info.isCustomEditMode());
      assertEquals("jdbc:mysql://x:3306/y?a=b", info.getCustomUrl(),
                   "the custom URL is used as typed, with no properties appended");
      assertEquals("useSSL=true", info.getProperties());
   }

   @Test
   void aTypeSwitchCarriesNothing() {
      PostgreSQLDatabaseInfo storedInfo = new PostgreSQLDatabaseType().createDatabaseInfo();
      storedInfo.setProperties("ssl=true");
      MySQLDatabaseInfo info = new MySQLDatabaseType().createDatabaseInfo();

      WizDatabaseMapper.carryOverUneditedSettings(
         stored(PostgreSQLDatabaseType.TYPE, storedInfo), stored(MySQLDatabaseType.TYPE, info));

      assertNull(info.getProperties());
   }

   @Test
   void typesAreComparedIgnoringCase() {
      PostgreSQLDatabaseInfo storedInfo = new PostgreSQLDatabaseType().createDatabaseInfo();
      storedInfo.setProperties("ssl=true");
      PostgreSQLDatabaseInfo info = new PostgreSQLDatabaseType().createDatabaseInfo();

      WizDatabaseMapper.carryOverUneditedSettings(
         stored("postgresql", storedInfo), stored(PostgreSQLDatabaseType.TYPE, info));

      assertEquals("ssl=true", info.getProperties());
   }

   @Test
   void propertiesTheUpdateSetItselfAreNeverOverwritten() {
      PostgreSQLDatabaseType type = new PostgreSQLDatabaseType();
      PostgreSQLDatabaseInfo storedInfo = type.createDatabaseInfo();
      storedInfo.setProperties("ssl=true");
      PostgreSQLDatabaseInfo info = type.createDatabaseInfo();
      info.setDatabaseName("d");
      info.setProperties("sslmode=require");

      WizDatabaseMapper.carryOverUneditedSettings(
         stored(PostgreSQLDatabaseType.TYPE, storedInfo), stored(PostgreSQLDatabaseType.TYPE, info));

      NetworkLocation network = new NetworkLocation();
      network.setHostName("h");
      network.setPortNumber(5432);
      assertEquals("jdbc:postgresql://h:5432/d?sslmode=require", type.formatUrl(network, info));
   }

   @Test
   void applyingTheCarryOverTwiceDoesNotAppendTwice() {
      PostgreSQLDatabaseType type = new PostgreSQLDatabaseType();
      PostgreSQLDatabaseInfo storedInfo = type.createDatabaseInfo();
      storedInfo.setProperties("ssl=true");
      PostgreSQLDatabaseInfo info = type.createDatabaseInfo();
      info.setDatabaseName("d");
      DatabaseDefinition current = stored(PostgreSQLDatabaseType.TYPE, storedInfo);
      DatabaseDefinition updated = stored(PostgreSQLDatabaseType.TYPE, info);

      WizDatabaseMapper.carryOverUneditedSettings(current, updated);
      WizDatabaseMapper.carryOverUneditedSettings(current, updated);

      NetworkLocation network = new NetworkLocation();
      network.setHostName("h");
      network.setPortNumber(5432);
      assertEquals("jdbc:postgresql://h:5432/d?ssl=true", type.formatUrl(network, info));
   }

   @Test
   void nullsAreTolerated() {
      DatabaseDefinition withInfo = stored(PostgreSQLDatabaseType.TYPE,
                                           new PostgreSQLDatabaseType().createDatabaseInfo());
      DatabaseDefinition noInfo = stored(PostgreSQLDatabaseType.TYPE, null);
      DatabaseDefinition noType = stored(null, new PostgreSQLDatabaseType().createDatabaseInfo());

      assertDoesNotThrow(() -> {
         WizDatabaseMapper.carryOverUneditedSettings(null, withInfo);
         WizDatabaseMapper.carryOverUneditedSettings(withInfo, null);
         WizDatabaseMapper.carryOverUneditedSettings(noInfo, withInfo);
         WizDatabaseMapper.carryOverUneditedSettings(withInfo, noInfo);
         WizDatabaseMapper.carryOverUneditedSettings(noType, withInfo);
         WizDatabaseMapper.carryOverUneditedSettings(withInfo, noType);
      });
   }

   @Test
   void baseUpdateKeepsTheStoredUnasgnWhenTheCallerSentNone() {
      DatabaseDefinition current = stored(MySQLDatabaseType.TYPE, new MySQLDatabaseType().createDatabaseInfo());
      current.setUnasgn(true);
      DatabaseDefinition updated = stored(MySQLDatabaseType.TYPE, new MySQLDatabaseType().createDatabaseInfo());

      WizDatabaseMapper.carryOverUneditedSettings(current, updated, null);

      assertTrue(updated.isUnasgn(), "an absent flag must never reset a security switch");
   }

   @Test
   void baseUpdateAppliesTheCallersUnasgnBothWays() {
      DatabaseDefinition off = stored(MySQLDatabaseType.TYPE, new MySQLDatabaseType().createDatabaseInfo());
      DatabaseDefinition setOn = stored(MySQLDatabaseType.TYPE, new MySQLDatabaseType().createDatabaseInfo());
      WizDatabaseMapper.carryOverUneditedSettings(off, setOn, true);
      assertTrue(setOn.isUnasgn());

      DatabaseDefinition on = stored(MySQLDatabaseType.TYPE, new MySQLDatabaseType().createDatabaseInfo());
      on.setUnasgn(true);
      DatabaseDefinition setOff = stored(MySQLDatabaseType.TYPE, new MySQLDatabaseType().createDatabaseInfo());
      WizDatabaseMapper.carryOverUneditedSettings(on, setOff, false);
      assertFalse(setOff.isUnasgn(), "an explicit false clears the flag");
   }

   @Test
   void additionalConnectionPathStillForcesTheStoredUnasgn() {
      DatabaseDefinition current = stored(MySQLDatabaseType.TYPE, new MySQLDatabaseType().createDatabaseInfo());
      current.setUnasgn(true);
      DatabaseDefinition updated = stored(MySQLDatabaseType.TYPE, new MySQLDatabaseType().createDatabaseInfo());
      updated.setUnasgn(false);

      WizDatabaseMapper.carryOverUneditedSettings(current, updated);

      assertTrue(updated.isUnasgn(), "the two-argument form is the additional-connection rule");
   }

   @Test
   void explicitEmptyPropertiesClearTheStoredOnesAndNullKeepsThem() {
      MySQLDatabaseType type = new MySQLDatabaseType();
      MySQLDatabaseInfo storedInfo = type.createDatabaseInfo();
      storedInfo.setProperties("useSSL=true");
      DatabaseDefinition current = stored(MySQLDatabaseType.TYPE, storedInfo);

      MySQLDatabaseInfo cleared = type.createDatabaseInfo();
      cleared.setProperties("");
      WizDatabaseMapper.carryOverUneditedSettings(current, stored(MySQLDatabaseType.TYPE, cleared));
      assertEquals("", cleared.getProperties());

      MySQLDatabaseInfo untouched = type.createDatabaseInfo();
      WizDatabaseMapper.carryOverUneditedSettings(current, stored(MySQLDatabaseType.TYPE, untouched));
      assertEquals("useSSL=true", untouched.getProperties());
   }

   private static DatabaseDefinition stored(String type, DatabaseInfo info) {
      DatabaseDefinition definition = new DatabaseDefinition();
      definition.setType(type);
      definition.setInfo(info);
      return definition;
   }
}
