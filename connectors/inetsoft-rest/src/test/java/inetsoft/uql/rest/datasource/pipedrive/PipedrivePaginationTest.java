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
package inetsoft.uql.rest.datasource.pipedrive;

import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.uql.rest.json.EndpointJsonQuery.Endpoints;
import inetsoft.uql.rest.pagination.PaginationParamType;
import inetsoft.uql.rest.pagination.PaginationSpec;
import inetsoft.uql.rest.pagination.PaginationType;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * This connector spans two Pipedrive APIs, and they page incompatibly.
 *
 * <p>v1 pages by a {@code start} offset it reads back from
 * {@code additional_data.pagination.next_start}. v2 pages by an opaque {@code cursor} read from
 * {@code additional_data.next_cursor} and carries no offset and no total at all. Driving either
 * with the other's spec fails SILENTLY -- the first page returns and iteration stops, or the
 * cursor is never sent -- so the branch is worth pinning rather than trusting.</p>
 */
class PipedrivePaginationTest {
   @Test
   void versionTwoEndpointsPageByCursor() {
      PaginationSpec spec = paginationFor("Deals");

      assertEquals(PaginationType.ITERATION, spec.getType());
      assertEquals("$.additional_data.next_cursor", spec.getHasNextParam().getValue());
      assertEquals(PaginationParamType.JSON_PATH, spec.getHasNextParam().getType());
      assertEquals("$.additional_data.next_cursor", spec.getPageOffsetParamToRead().getValue());
      assertEquals("cursor", spec.getPageOffsetParamToWrite().getValue());
      assertEquals(PaginationParamType.QUERY, spec.getPageOffsetParamToWrite().getType());
   }

   @Test
   void versionOneEndpointsStillPageByOffset() {
      // The regression this guards: migrating some endpoints to v2 must not change how the
      // eighty that stayed on v1 page.
      PaginationSpec spec = paginationFor("Notes");

      assertEquals(PaginationType.ITERATION, spec.getType());
      assertEquals("$.additional_data.pagination.more_items_in_collection",
                   spec.getHasNextParam().getValue());
      assertEquals("$.additional_data.pagination.next_start",
                   spec.getPageOffsetParamToRead().getValue());
      assertEquals("start", spec.getPageOffsetParamToWrite().getValue());
   }

   @Test
   void unpagedEndpointsPageNotAtAll() {
      assertEquals(PaginationType.NONE, paginationFor("Deal").getType());
   }

   /**
    * The branch keys on the suffix, so an endpoint reachable at a v2 path and paged the v1 way --
    * or the reverse -- is the failure mode. Both directions are checked across every endpoint
    * rather than on a sample.
    */
   @Test
   void everyEndpointPagesTheWayItsPathDemands() {
      Map<String, PipedriveEndpoint> endpoints = load();
      assertFalse(endpoints.isEmpty(), "no Pipedrive endpoints loaded");
      List<String> wrong = new ArrayList<>();

      endpoints.forEach((name, endpoint) -> {
         if(!endpoint.isPaged()) {
            return;
         }

         PipedriveQuery query = new PipedriveQuery();
         query.updatePagination(endpoint);
         String cursor = query.getPaginationSpec().getPageOffsetParamToWrite().getValue();
         boolean v2Path = endpoint.getSuffix().startsWith(PipedriveQuery.VERSION_2_PREFIX);
         String expected = v2Path ? "cursor" : "start";

         if(!expected.equals(cursor)) {
            wrong.add(name + " (" + endpoint.getSuffix().split("\\?")[0] + ") pages by " + cursor);
         }
      });

      assertTrue(wrong.isEmpty(),
                 wrong.size() + " endpoint(s) page the wrong way for their path:\n  "
                    + String.join("\n  ", wrong));
   }

   /** Nothing should be left half-migrated: every suffix belongs to one API or the other. */
   @Test
   void everyEndpointTargetsOneOfTheTwoApis() {
      List<String> stray = load().values().stream()
         .map(PipedriveEndpoint::getSuffix)
         .filter(s -> !s.startsWith(PipedriveQuery.VERSION_2_PREFIX) && !s.startsWith("/v1/"))
         .toList();

      assertTrue(stray.isEmpty(), "suffixes on neither v1 nor v2: " + stray);
   }

   private static PaginationSpec paginationFor(String name) {
      PipedriveEndpoint endpoint = load().get(name);
      assertNotNull(endpoint, "endpoint not found: " + name);
      PipedriveQuery query = new PipedriveQuery();
      query.updatePagination(endpoint);
      return query.getPaginationSpec();
   }

   /**
    * Parses the resource with the loader's own mapper rather than calling {@code Endpoints.load},
    * which reads SreeEnv and so needs a Spring context this test has no reason to stand up.
    */
   private static Map<String, PipedriveEndpoint> load() {
      try(InputStream input = PipedriveQuery.class.getResourceAsStream("endpoints.json")) {
         assertNotNull(input, "pipedrive/endpoints.json not on the classpath");
         ObjectMapper mapper = Endpoints.createObjectMapper();
         PipedriveEndpoints parsed = mapper.readValue(input, PipedriveEndpoints.class);
         Map<String, PipedriveEndpoint> byName = new LinkedHashMap<>();

         for(PipedriveEndpoint endpoint : parsed.getEndpoints()) {
            byName.put(endpoint.getName(), endpoint);
         }

         return byName;
      }
      catch(Exception e) {
         throw new AssertionError("could not read pipedrive/endpoints.json", e);
      }
   }
}
