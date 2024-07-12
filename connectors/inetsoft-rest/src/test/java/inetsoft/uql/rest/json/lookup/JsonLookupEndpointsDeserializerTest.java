/*
 * inetsoft-rest - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.uql.rest.json.lookup;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.type.CollectionType;
import org.junit.jupiter.api.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JsonLookupEndpointsDeserializerTest {
   @BeforeEach
   public void setup() {
      mapper = new ObjectMapper();
   }

   @Test
   public void testWithEndpointsArray() throws IOException {
      final List<JsonLookupEndpoint> lookups =
         deserializeLookupEndpoints("[{" +
                                    "\"endpoints\": [\"A\", \"B\"], \n" +
                                    "\"jsonPath\": \"$.[*]\",\n" +
                                    "\"key\": \"id\",\n" +
                                    "\"parameterName\": \"ID\"" +
                                    "},{" +
                                    "\"endpoint\": \"C\",\n" +
                                    "\"jsonPath\": \"$.[*]\",\n" +
                                    "\"key\": \"id\",\n" +
                                    "\"parameterName\": \"ID\"" +
                                    "}]");

      assertEquals(3, lookups.size());
      assertEquals("A", lookups.get(0).endpoint());
      assertEquals("B", lookups.get(1).endpoint());
      assertEquals("C", lookups.get(2).endpoint());
   }

   @Test
   public void testBasicEndpoint() throws IOException {
      final List<JsonLookupEndpoint> lookups =
         deserializeLookupEndpoints("[{" +
                                    "\"endpoint\": \"A\",\n" +
                                    "\"jsonPath\": \"$.[*]\",\n" +
                                    "\"key\": \"id\",\n" +
                                    "\"parameterName\": \"ID\"" +
                                    "}]");

      assertEquals(1, lookups.size());
      assertEquals("A", lookups.get(0).endpoint());
   }

   private List<JsonLookupEndpoint> deserializeLookupEndpoints(String json) throws IOException {
      final InputStream input = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));

      final SimpleModule module = new SimpleModule();
      module.setDeserializerModifier(new JsonLookupEndpointsModifier());
      mapper.registerModule(module);

      final CollectionType type = mapper.getTypeFactory().constructCollectionType(List.class, JsonLookupEndpoint.class);
      return mapper.readValue(input, type);
   }

   private ObjectMapper mapper;
}
