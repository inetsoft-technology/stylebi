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
package inetsoft.uql.rest.datasource.jira;

import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.uql.rest.json.EndpointJsonQuery.Endpoints;
import inetsoft.uql.rest.pagination.PaginationParamType;
import inetsoft.uql.rest.pagination.PaginationSpec;
import inetsoft.uql.rest.pagination.PaginationType;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.*;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Jira pages two different ways and the connector has to pick between them per endpoint.
 *
 * <p>Almost every endpoint pages by {@code startAt} against the {@code total} the response carries.
 * The enhanced issue search does not: it returns an opaque {@code nextPageToken} and no total at
 * all. Driving it with offset paging fails silently -- there is no total to count towards and
 * advancing {@code startAt} does nothing -- which is a wrong answer rather than an error, so it is
 * worth pinning both branches.</p>
 */
class JiraPaginationTest {
   @Test
   void enhancedSearchDeclaresTokenPaging() {
      JiraEndpoint endpoint = endpoint("JQL Search");
      assertEquals(PaginationType.ITERATION, endpoint.getPageType(),
                   "JQL Search must declare token paging; without it the endpoint silently " +
                   "falls back to offset paging, which cannot page it");
   }

   @Test
   void everyOtherEndpointLeavesPageTypeUnset() {
      Map<String, JiraEndpoint> endpoints = load();
      assertFalse(endpoints.isEmpty(), "no Jira endpoints loaded");

      BiConsumer<String, JiraEndpoint> checkUnset = (name, endpoint) ->
         assertNull(endpoint.getPageType(),
                    name + " declares a pageType; only the enhanced search should, since " +
                    "everything else pages the standard way");

      endpoints.forEach((name, endpoint) -> {
         if(!"JQL Search".equals(name)) {
            checkUnset.accept(name, endpoint);
         }
      });
   }

   @Test
   void tokenPagingReadsAndWritesNextPageToken() {
      PaginationSpec spec = paginationFor("JQL Search");

      assertEquals(PaginationType.ITERATION, spec.getType());
      assertEquals("$.nextPageToken", spec.getHasNextParam().getValue());
      assertEquals(PaginationParamType.JSON_PATH, spec.getHasNextParam().getType());
      assertEquals("$.nextPageToken", spec.getPageOffsetParamToRead().getValue());
      assertEquals(PaginationParamType.JSON_PATH, spec.getPageOffsetParamToRead().getType());
      assertEquals("nextPageToken", spec.getPageOffsetParamToWrite().getValue());
      assertEquals(PaginationParamType.QUERY, spec.getPageOffsetParamToWrite().getType());
   }

   @Test
   void undeclaredEndpointsStillPageByOffset() {
      // The regression this guards: adding the pageType property must not change how the other
      // 152 endpoints page.
      PaginationSpec spec = paginationFor("Projects");

      assertEquals(PaginationType.TOTAL_COUNT_AND_OFFSET, spec.getType());
      assertEquals("$.total", spec.getTotalCountParam().getValue());
      assertEquals("startAt", spec.getOffsetParam().getValue());
      assertEquals("maxResults", spec.getMaxResultsPerPageParam().getValue());
   }

   @Test
   void unpagedEndpointsPageNotAtAll() {
      assertEquals(PaginationType.NONE, paginationFor("Dashboard").getType());
   }

   private static JiraEndpoint endpoint(String name) {
      JiraEndpoint endpoint = load().get(name);
      assertNotNull(endpoint, "endpoint not found: " + name);
      return endpoint;
   }

   /**
    * Parses the resource with the loader's own mapper rather than calling {@code Endpoints.load},
    * which reads SreeEnv and so needs a Spring context this test has no reason to stand up.
    */
   private static Map<String, JiraEndpoint> load() {
      try(InputStream input = JiraQuery.class.getResourceAsStream("endpoints.json")) {
         assertNotNull(input, "jira/endpoints.json not on the classpath");
         ObjectMapper mapper = Endpoints.createObjectMapper();
         JiraEndpoints parsed = mapper.readValue(input, JiraEndpoints.class);
         Map<String, JiraEndpoint> byName = new LinkedHashMap<>();

         for(JiraEndpoint endpoint : parsed.getEndpoints()) {
            byName.put(endpoint.getName(), endpoint);
         }

         return byName;
      }
      catch(Exception e) {
         throw new AssertionError("could not read jira/endpoints.json", e);
      }
   }

   /** Runs the connector's own branch rather than restating it, so the test can catch it changing. */
   private static PaginationSpec paginationFor(String name) {
      JiraQuery query = new JiraQuery();
      query.updatePagination(endpoint(name));
      return query.getPaginationSpec();
   }
}
