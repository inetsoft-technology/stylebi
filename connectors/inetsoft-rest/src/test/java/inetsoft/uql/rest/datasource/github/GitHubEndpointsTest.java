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
package inetsoft.uql.rest.datasource.github;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GitHub removed the Source Imports REST API (every call answers 404 "This endpoint has been
 * deprecated"), so endpoints.json must not list those endpoints.
 */
class GitHubEndpointsTest {
   @Test
   void doesNotListRemovedSourceImportEndpoints() throws Exception {
      try(InputStream in = getClass().getResourceAsStream("endpoints.json")) {
         assertNotNull(in);
         JsonNode endpoints = new ObjectMapper().readTree(in).path("endpoints");
         assertTrue(endpoints.size() > 0);

         for(JsonNode endpoint : endpoints) {
            String name = endpoint.path("name").asText();
            String suffix = endpoint.path("suffix").asText();
            assertFalse(name.startsWith("Source Import"), name);
            assertFalse(suffix.matches("/repos/[^/]+/[^/]+/import(/.*)?"), suffix);
         }
      }
   }
}
