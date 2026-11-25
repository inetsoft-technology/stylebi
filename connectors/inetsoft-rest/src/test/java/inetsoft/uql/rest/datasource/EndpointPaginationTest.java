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

package inetsoft.uql.rest.datasource;

import inetsoft.test.EndpointsSpringContext;
import inetsoft.test.SreeHome;
import inetsoft.uql.rest.json.EndpointJsonQuery;
import inetsoft.uql.rest.json.JsonRestDataIteratorStrategyFactory;
import inetsoft.uql.rest.json.JsonTransformer;
import inetsoft.uql.rest.pagination.HttpResponseParameterParser;
import inetsoft.uql.rest.pagination.PaginationType;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Checks the pagination of every endpoint of every data source: the strategy for the pagination
 * that selecting the endpoint gives the query accepts it. The strategies validate
 * their own settings when they are created (for example, "page count" paging must say where the
 * page count is), so creating the real strategy for each endpoint applies the real rules and
 * needs no request.
 *
 * <p>The "paged" flag of an endpoint is deliberately not compared with the pagination: it is not
 * a contract (Zendesk, SendGrid and GoSquared use link paging for every endpoint, HubSpot picks
 * the type per endpoint, and Asana switches paging on only once a required parameter is set).
 */
@EndpointsSpringContext
@SreeHome
class EndpointPaginationTest {
   @Test
   void everyEndpointGetsAStrategyThatAcceptsItsPagination() throws Exception {
      List<String> problems = new ArrayList<>();
      Map<PaginationType, Integer> used = new TreeMap<>();

      for(Class<?> type : EndpointDefinitionsTest.queryTypes()) {
         EndpointJsonQuery<?> query = EndpointDefinitionsTest.newQuery(type);

         for(String name : query.getEndpointMap().keySet()) {
            query.setEndpoint(name);

            if(query.isPostRequest()) {
               // creating the strategy reads the body of a POST request
               query.setRequestBody("{}");
            }

            used.merge(query.getPaginationType(), 1, Integer::sum);
            String problem = strategyProblem(query);

            if(problem != null) {
               problems.add(type.getSimpleName() + "/" + name + ": " + problem);
            }
         }
      }

      assertNoProblems(problems);
      // guards the test itself: the common ways of paging must really be reached
      assertTrue(used.getOrDefault(PaginationType.NONE, 0) > 100, used.toString());
      assertTrue(used.size() >= 5, used.toString());
   }

   /** Creates the strategy for the query without a network, and returns why it was refused. */
   private static String strategyProblem(EndpointJsonQuery<?> query) throws Exception {
      JsonTransformer transformer = new JsonTransformer();
      JsonRestDataIteratorStrategyFactory factory = new JsonRestDataIteratorStrategyFactory(
         transformer, new HttpResponseParameterParser(transformer));
      Method create = JsonRestDataIteratorStrategyFactory.class
         .getDeclaredMethod("createHttpRestDataIterator", inetsoft.uql.rest.json.RestJsonQuery.class);
      create.setAccessible(true);

      try {
         ((AutoCloseable) create.invoke(factory, query)).close();
         return null;
      }
      catch(InvocationTargetException e) {
         return String.valueOf(e.getCause());
      }
   }

   private static void assertNoProblems(List<String> problems) {
      assertTrue(problems.isEmpty(),
                 problems.size() + " problem(s):\n" + String.join("\n", problems));
   }
}
