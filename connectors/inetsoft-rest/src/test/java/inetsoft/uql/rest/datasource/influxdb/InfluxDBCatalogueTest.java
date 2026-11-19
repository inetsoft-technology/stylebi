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
import inetsoft.test.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.InputStream;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Checks the InfluxDB endpoint catalogue ({@code endpoints.json}) for the mistakes that cannot be
 * seen from the file but break a call: a required/optional flag that disagrees with what InfluxDB
 * needs, and a lookup that cannot work. A placeholder written {@code {Name}} is required and one
 * written {@code {Name?}} is optional; an optional one that is left out simply vanishes from the
 * request, so an optional id in a path sends a call InfluxDB answers with a 400, or with the list
 * when a single item was asked for.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
class InfluxDBCatalogueTest {
   @BeforeAll
   static void load() throws Exception {
      try(InputStream input = InfluxDBEndpoint.class.getResourceAsStream("endpoints.json")) {
         for(JsonNode endpoint : new ObjectMapper().readTree(input).get("endpoints")) {
            endpoints.put(endpoint.get("name").asText(), endpoint);
         }
      }

      assertFalse(endpoints.isEmpty());
   }

   @Test
   void everyIdInAPathIsRequired() {
      List<String> optional = new ArrayList<>();

      for(Map.Entry<String, JsonNode> e : endpoints.entrySet()) {
         for(String placeholder : placeholders(path(e.getValue()))) {
            if(placeholder.contains("?")) {
               optional.add(e.getKey() + " " + placeholder);
            }
         }
      }

      assertEquals(List.of(), optional,
                   "an optional placeholder in a path is dropped from the request, which then " +
                      "asks for a different resource");
   }

   @Test
   void idsOfASingleItemAndItsSubResourcesAreRequired() {
      for(String name : List.of("Bucket", "Bucket Labels", "Bucket Members", "Bucket Owners")) {
         assertTrue(suffix(name).contains("{Bucket ID}"), name + " " + suffix(name));
      }

      for(String name : List.of("Dashboard", "Dashboard Labels", "Dashboard Members",
                                "Dashboard Owners"))
      {
         assertTrue(suffix(name).contains("{Dashboard ID}"), name + " " + suffix(name));
      }

      assertTrue(suffix("Legacy Authorization").contains("{Authorization ID}"));
   }

   @Test
   void filtersTheServerDoesNotRequireAreOptional() {
      assertTrue(suffix("Tasks").contains("user={User ID?}"), suffix("Tasks"));
      assertTrue(suffix("Notification Rules").contains("tag={Tag?}"),
                 suffix("Notification Rules"));
   }

   @Test
   void organizationFiltersTheServerHonoursAreLeftAsTheyWere() {
      // by design: a call without an organization is unscoped, and for these it is the contract
      assertTrue(suffix("Checks").contains("orgID={Organization ID}"));
      assertTrue(suffix("Notification Rules").contains("orgID={Organization ID}"));
      assertTrue(suffix("Notification Endpoints").contains("orgID={Organization ID}"));
   }

   @Test
   void aLookupParentCanEnumerateItsRowsSoItsPathNeedsNoId() {
      for(Map.Entry<String, JsonNode> e : endpoints.entrySet()) {
         if(e.getValue().has("lookups") && e.getValue().get("lookups").size() > 0) {
            assertFalse(path(e.getValue()).contains("{"),
                        e.getKey() + " is a lookup parent but its path needs an id: " +
                           suffix(e.getKey()));
         }
      }
   }

   @Test
   void dashboardLookupsHangOffTheListNotTheSingleDashboard() {
      assertTrue(endpoints.get("Dashboards").has("lookups"));
      assertFalse(endpoints.get("Dashboard").has("lookups"));

      JsonNode lookup = endpoints.get("Dashboards").get("lookups").get(0);
      assertEquals("$.dashboards[*]", lookup.get("jsonPath").asText());
      assertEquals("Dashboard ID", lookup.get("parameterName").asText());
      assertEquals(List.of("Dashboard Labels", "Dashboard Members", "Dashboard Owners"),
                   names(lookup.get("endpoints")));
   }

   @Test
   void aLookupChildNeedsNothingButTheValueItIsHanded() {
      List<String> problems = new ArrayList<>();

      for(Map.Entry<String, JsonNode> e : endpoints.entrySet()) {
         if(!e.getValue().has("lookups")) {
            continue;
         }

         for(JsonNode lookup : e.getValue().get("lookups")) {
            String handed = lookup.get("parameterName").asText();

            for(String child : names(lookup.get("endpoints"))) {
               assertNotNull(endpoints.get(child), child);

               for(String placeholder : placeholders(suffix(child))) {
                  if(!placeholder.contains("?") && !placeholder.startsWith(handed)) {
                     problems.add(e.getKey() + " -> " + child + " also needs {" + placeholder + "}");
                  }
               }
            }
         }
      }

      // Database Retention Policy Mappings needs an organisation name or id, which a template
      // cannot say; it is out of scope here
      problems.removeIf(p -> p.contains("Database Retention Policy Mappings"));
      assertEquals(List.of(), problems);
   }

   @Test
   void anEndpointReadByOffsetHasNoCursorParameter() {
      // InfluxDB honours "after" over "offset", so a cursor on an endpoint that is read by
      // offset would be asked for again and again
      for(String name : List.of("Buckets", "Users")) {
         assertFalse(suffix(name).contains("after="), name + " " + suffix(name));
      }
   }

   @Test
   void everyPagedEndpointSaysHowItIsPaged() {
      Set<String> paged = new TreeSet<>();

      for(Map.Entry<String, JsonNode> e : endpoints.entrySet()) {
         if(e.getValue().path("paged").asBoolean()) {
            paged.add(e.getKey());
         }
      }

      // the list endpoints take limit and offset, tasks hand back a link to the next page; the
      // single-item and sub-resource endpoints are not paged at all
      assertEquals(new TreeSet<>(List.of("Buckets", "Checks", "Dashboards",
                                         "Notification Endpoints", "Notification Rules",
                                         "Organizations", "Tasks", "Users")), paged);

      for(String name : paged) {
         InfluxDBQuery query = new InfluxDBQuery();
         query.setEndpoint(name);
         assertTrue(query.isPaged(), name);
         assertNotEquals(inetsoft.uql.rest.pagination.PaginationType.NONE,
                         query.getPaginationSpec().getType(), name);
      }
   }

   private static List<String> names(JsonNode array) {
      List<String> names = new ArrayList<>();
      array.forEach(n -> names.add(n.asText()));
      return names;
   }

   private static String suffix(String name) {
      assertNotNull(endpoints.get(name), name);
      return endpoints.get(name).get("suffix").asText();
   }

   private static String path(JsonNode endpoint) {
      String suffix = endpoint.get("suffix").asText();
      int query = suffix.indexOf('?');
      return query < 0 ? suffix : suffix.substring(0, query);
   }

   private static List<String> placeholders(String text) {
      List<String> placeholders = new ArrayList<>();
      Matcher m = Pattern.compile("\\{([^}]*)}").matcher(text);

      while(m.find()) {
         placeholders.add(m.group(1));
      }

      return placeholders;
   }

   private static final Map<String, JsonNode> endpoints = new LinkedHashMap<>();
}
