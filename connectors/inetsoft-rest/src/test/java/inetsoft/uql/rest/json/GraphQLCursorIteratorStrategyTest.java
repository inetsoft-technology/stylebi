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
package inetsoft.uql.rest.json;

import inetsoft.test.*;
import inetsoft.uql.rest.HttpResponse;
import inetsoft.uql.rest.IHttpHandler;
import inetsoft.uql.rest.RestErrorHandler;
import inetsoft.uql.rest.RestRequest;
import inetsoft.uql.rest.datasource.graphql.GraphQLDataSource;
import inetsoft.uql.rest.datasource.graphql.GraphQLQuery;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the pagination termination conditions of the GraphQL cursor strategy.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, CredentialTestConfig.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
public class GraphQLCursorIteratorStrategyTest {
   /**
    * A new cursor on every page keeps the iteration going until the API stops returning
    * one.
    */
   @Test
   public void distinctCursorsPaginateUntilCursorIsNull() throws Exception {
      assertEquals(2, countPages(page("c1"), page("c2"), page(null)));
   }

   /**
    * Some GraphQL APIs echo the last page's cursor instead of returning null, which would
    * otherwise paginate forever.
    */
   @Test
   public void echoedCursorEndsPagination() throws Exception {
      assertEquals(1, countPages(page("c1"), page("c1")));
   }

   @Test
   public void blankCursorEndsPagination() throws Exception {
      assertEquals(0, countPages(page("")));
   }

   @Test
   public void missingCursorEndsPagination() throws Exception {
      assertEquals(0, countPages("{\"data\":{\"items\":{\"pageInfo\":{}}}}"));
   }

   /**
    * Iterates the strategy over the given response bodies and returns the number of pages
    * that were consumed.
    */
   private int countPages(String... responseBodies) throws Exception {
      final GraphQLQuery query = createQuery();
      final SequencedHttpHandler httpHandler = new SequencedHttpHandler(responseBodies);
      final GraphQLCursorIteratorStrategy strategy = new GraphQLCursorIteratorStrategy(
         query, new JsonTransformer(), httpHandler, new RestErrorHandler());
      int count = 0;

      while(strategy.hasNext()) {
         assertNotNull(strategy.next());
         count++;
      }

      return count;
   }

   private GraphQLQuery createQuery() {
      final GraphQLDataSource dataSource = new GraphQLDataSource();
      dataSource.setURL("http://host/graphql");

      final GraphQLQuery query = new GraphQLQuery();
      query.setDataSource(dataSource);
      query.setRequestType("POST");
      query.setQueryString("query($cursor: String) { items(after: $cursor) { pageInfo { endCursor } } }");
      query.setVariables("{}");
      query.setUsePagination(true);
      query.setCursorPagination(true);
      query.setPaginationVariable("cursor");
      query.setPaginationCountPath(CURSOR_PATH);

      return query;
   }

   private String page(String cursor) {
      final String value = cursor == null ? "null" : "\"" + cursor + "\"";
      return "{\"data\":{\"items\":{\"pageInfo\":{\"endCursor\":" + value + "}}}}";
   }

   /**
    * Returns the configured response bodies in order. The shared TestHttpHandler cannot be
    * used here because every page of a GraphQL query is requested from the same URL, so all
    * of the responses would collapse onto a single request key.
    */
   private static final class SequencedHttpHandler implements IHttpHandler {
      SequencedHttpHandler(String... responseBodies) {
         this.responseBodies = new ArrayDeque<>(Arrays.asList(responseBodies));
      }

      @Override
      public HttpResponse executeRequest(RestRequest request) {
         assertFalse(responseBodies.isEmpty(), "Unexpected request, all responses consumed");
         return new TestHttpResponse(responseBodies.remove());
      }

      @Override
      public void close() {
         // no-op
      }

      private final Deque<String> responseBodies;
   }

   private static final String CURSOR_PATH = "$.data.items.pageInfo.endCursor";
}
