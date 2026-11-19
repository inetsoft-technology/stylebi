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
package inetsoft.uql.rest.datasource.influxdb;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the InfluxDB endpoint catalogue without a running server.
 */
class InfluxDBEndpointsCatalogTest {
   /**
    * InfluxDB 2.x serves the v1-compatible authorizations under {@code /private}; the same path
    * without that prefix is answered with the web UI's HTML page.
    */
   @Test
   void legacyAuthorizationsUsePrivatePrefix() throws Exception {
      String prefix = "/private/legacy/authorizations";
      assertTrue(suffixOf("Legacy Authorizations").startsWith(prefix + "?"));
      assertEquals(prefix + "/{Authorization ID?}", suffixOf("Legacy Authorization"));
   }

   private String suffixOf(String name) throws Exception {
      try(InputStream input = getClass().getResourceAsStream("endpoints.json")) {
         assertNotNull(input, "endpoints.json is missing");
         JsonNode endpoints = new ObjectMapper().readTree(input).get("endpoints");

         for(JsonNode endpoint : endpoints) {
            if(name.equals(endpoint.get("name").asText())) {
               return endpoint.get("suffix").asText();
            }
         }
      }

      throw new AssertionError("No endpoint named " + name);
   }
}
