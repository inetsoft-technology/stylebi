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
      assertEquals(3, countPages(page("c1"), page("c2"), page(null)));
   }

   /**
    * Some GraphQL APIs echo the last page's cursor instead of returning null, which would
    * otherwise paginate forever.
    */
   @Test
   public void echoedCursorEndsPagination() throws Exception {
      assertEquals(2, countPages(page("c1"), page("c1")));
   }

   @Test
   public void blankCursorEndsPagination() throws Exception {
      assertEquals(1, countPages(page("")));
   }

   @Test
   public void missingCursorEndsPagination() throws Exception {
      assertEquals(1, countPages("{\"data\":{\"items\":{\"pageInfo\":{}}}}"));
   }

   /**
    * The last page, whose cursor is null, still carries records that must be returned.
    */
   @Test
   public void lastPageWithNullCursorKeepsItsRecords() throws Exception {
      final List<Object> pages = readPages(
         pageWithRecords("c1", 1), pageWithRecords(null, 2, 3));
      assertEquals(2, pages.size());
      assertTrue(pages.get(1).toString().contains("2"));
   }

   @Test
   public void firstPageWithNullCursorKeepsItsRecords() throws Exception {
      assertEquals(1, countPages(pageWithRecords(null, 1)));
   }

   /**
    * An unchanged cursor stops paging without a further request (the handler fails on an
    * unexpected request) and keeps the page that carried it.
    */
   @Test
   public void unchangedCursorStopsWithoutDroppingPage() throws Exception {
      assertEquals(2, countPages(pageWithRecords("c1", 1), pageWithRecords("c1", 2)));
   }

   private String pageWithRecords(String cursor, int... ids) {
      final String value = cursor == null ? "null" : "\"" + cursor + "\"";
      final StringBuilder nodes = new StringBuilder();

      for(int id : ids) {
         nodes.append(nodes.length() > 0 ? "," : "").append("{\"id\":").append(id).append("}");
      }

      return "{\"data\":{\"items\":{\"nodes\":[" + nodes + "],\"pageInfo\":{\"endCursor\":" +
         value + "}}}}";
   }

   private List<Object> readPages(String... responseBodies) throws Exception {
      final GraphQLCursorIteratorStrategy strategy = new GraphQLCursorIteratorStrategy(
         createQuery(), new JsonTransformer(), new SequencedHttpHandler(responseBodies),
         new RestErrorHandler());
      final List<Object> pages = new ArrayList<>();

      while(strategy.hasNext()) {
         pages.add(strategy.next());
      }

      return pages;
   }

   /**
    * Bug #81771: an empty first page is still a successful response, so it is returned as the
    * page that was read (not a null failure-looking result) and ends the iteration.
    */
   @Test
   public void emptyFirstPageIsReturnedAndEndsIteration() throws Exception {
      final GraphQLQuery query = createQuery();
      final GraphQLCursorIteratorStrategy strategy = new GraphQLCursorIteratorStrategy(
         query, new JsonTransformer(), new SequencedHttpHandler(page(null)), new RestErrorHandler());

      assertNotNull(strategy.next());
      assertFalse(strategy.hasNext());
   }

   @Test
   public void failedRequestDoesNotMarkTheRunCompleted() throws Exception {
      final GraphQLQuery query = createQuery();
      final GraphQLCursorIteratorStrategy strategy = new GraphQLCursorIteratorStrategy(
         query, new JsonTransformer(), new IHttpHandler() {
            @Override
            public HttpResponse executeRequest(RestRequest request) {
               throw new IllegalStateException("HTTP 401");
            }

            @Override
            public void close() {
            }
         },
         new RestErrorHandler());

      try {
         strategy.next();
      }
      catch(Exception ignore) {
         // expected: the failure propagates
      }

      assertFalse(query.isRunCompleted());
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
