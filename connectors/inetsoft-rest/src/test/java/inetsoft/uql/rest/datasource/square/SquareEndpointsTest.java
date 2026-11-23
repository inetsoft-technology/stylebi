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
package inetsoft.uql.rest.datasource.square;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The List Catalog lookups pass the looked-up object's id to the inventory endpoints, which
 * expect a catalog item variation id, so the lookup path must be the variation level.
 */
class SquareEndpointsTest {
   private static final String VARIATIONS_PATH = "$.objects[*].item_data.variations[*]";

   @Test
   void listCatalogInventoryCountLookupIsKeyedOnVariations() throws Exception {
      assertEquals(VARIATIONS_PATH, listCatalogLookup("Inventory Count").path("jsonPath").asText());
   }

   @Test
   void listCatalogInventoryChangesLookupIsKeyedOnVariations() throws Exception {
      assertEquals(VARIATIONS_PATH, listCatalogLookup("Inventory Changes").path("jsonPath").asText());
   }

   private JsonNode listCatalogLookup(String name) throws Exception {
      try(InputStream in = getClass().getResourceAsStream("endpoints.json")) {
         assertNotNull(in);
         JsonNode endpoints = new ObjectMapper().readTree(in).path("endpoints");
         assertTrue(endpoints.size() > 0);

         for(JsonNode endpoint : endpoints) {
            if("List Catalog".equals(endpoint.path("name").asText())) {
               for(JsonNode lookup : endpoint.path("lookups")) {
                  if(name.equals(lookup.path("endpoint").asText())) {
                     return lookup;
                  }
               }
            }
         }
      }

      throw new AssertionError("Missing List Catalog lookup: " + name);
   }
}
