/*
 * inetsoft-rest - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.uql.rest.json.lookup;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.module.SimpleModule;
import inetsoft.test.TestEndpoint;
import inetsoft.uql.rest.json.EndpointJsonQuery;
import org.hamcrest.core.*;
import org.junit.Assert;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.net.URL;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

public class LookupEndpointValidationTest {
   @Test
   public void validateLookupEndpoints() throws IOException {
      final List<Path> endpoints = getAllEndpoints();

      for(Path endpoint : endpoints) {
         testEndpoint(endpoint);
      }
   }

   private List<Path> getAllEndpoints() throws IOException {
      final ArrayList<Path> endpointPaths = new ArrayList<>();
      final Enumeration<URL> resources = getClass().getClassLoader().getResources(
         "inetsoft/uql/rest/datasource");

      while(resources.hasMoreElements()) {
         String resource = resources.nextElement().getPath();

         if(resource.startsWith("/") && resource.contains(":")) {
            resource = resource.substring(1);
         }

         final Path path = Paths.get(resource);

         if(Files.isDirectory(path)) {
            Files.walk(path).filter(p -> p.endsWith("endpoints.json"))
                 .forEach(endpointPaths::add);
         }
      }

      return endpointPaths;
   }

   private void testEndpoint(Path endpointsPath) throws IOException {
      try(InputStream input = Files.newInputStream(endpointsPath)) {
         final Map<String, TestEndpoint> endpoints = createObjectMapper().readValue(
            input, TestEndpoints.class).toMap();

         final List<JsonLookupEndpoint> lookups = endpoints.values().stream()
                                                           .flatMap(e -> e.getLookups().stream())
                                                           .collect(Collectors.toList());
         final Set<String> keyset = endpoints.keySet();

         for(JsonLookupEndpoint lookup : lookups) {
            Assert.assertThat(keyset, IsCollectionContaining.hasItem(lookup.endpoint()));

            final TestEndpoint testEndpoint = endpoints.get(lookup.endpoint());

            if(testEndpoint != null) {
               Assert.assertThat(testEndpoint.getSuffix(), StringContains.containsString(
                  String.format("{%s", lookup.parameterName())));
            }
         }
      }
   }

   private static ObjectMapper createObjectMapper() {
      final ObjectMapper mapper = new ObjectMapper();
      mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
      final SimpleModule module = new SimpleModule();
      module.setDeserializerModifier(new JsonLookupEndpointsModifier());
      mapper.registerModule(module);
      return mapper;
   }

   private static class TestEndpoints extends EndpointJsonQuery.Endpoints<TestEndpoint> {
   }
}
