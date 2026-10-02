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
package inetsoft.sree;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the mapping of {@code INETSOFT_*} environment variables to property names, which the
 * early-loaded properties and {@link PropertiesEngine#getPropertyFromNonStorageSources(String)}
 * share (Bug #77323).
 */
@Tag("core")
class EarlyLoadedPropertiesEnvironmentTest {
   @Test
   void mapsInetsoftVariablesToLowercaseDottedNames() {
      Map<String, String> env = new HashMap<>();
      env.put("INETSOFT_SECURITY_USERS_MULTITENANT", "true");
      env.put("INETSOFT_SECURITY_EXPOSEDEFAULTORGTOALL", "true");
      env.put("PATH", "/usr/bin");

      Map<String, String> mapped =
         EarlyLoadedProperties.mapEnvironment(env, new Properties());

      assertEquals("true", mapped.get("security.users.multitenant"));
      assertEquals("true", mapped.get("security.exposedefaultorgtoall"));
      assertEquals(2, mapped.size());
   }

   @Test
   void usesTheNameOfAMatchingDefault() {
      Properties defaults = new Properties();
      defaults.setProperty("StyleReport.locale.resource", "inetsoft/util/srinter");
      Map<String, String> env = Map.of("INETSOFT_STYLEREPORT_LOCALE_RESOURCE", "custom");

      Map<String, String> mapped = EarlyLoadedProperties.mapEnvironment(env, defaults);

      assertEquals(Map.of("StyleReport.locale.resource", "custom"), mapped);
   }

   @Test
   void skipsSecretVariables() {
      Map<String, String> env = Map.of(
         "INETSOFT_MASTER_PASSWORD", "a",
         "INETSOFT_MASTER_SALT", "b",
         "INETSOFT_ADMIN_PASSWORD", "c");

      assertTrue(EarlyLoadedProperties.mapEnvironment(env, new Properties()).isEmpty());
   }
}
