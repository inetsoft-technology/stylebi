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
package inetsoft.uql.rest.json;

import inetsoft.test.RequestResponse;
import inetsoft.test.TestHttpHandler;
import inetsoft.test.TestHttpResponse;
import inetsoft.uql.rest.pagination.PagedIterationIteratorStrategy;
import inetsoft.uql.rest.RestDataIteratorStrategy;
import inetsoft.uql.rest.RestErrorHandler;
import inetsoft.uql.rest.RestRequest;
import inetsoft.uql.rest.pagination.*;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class PagedIterationIteratorStrategyTest {
    @Test
    public void test() throws Exception {
        final RestJsonQuery query = new RestJsonQuery();
        final RestJsonDataSource datasource = new RestJsonDataSource();
        datasource.setURL("http://host");
        query.setDataSource(datasource);
        final PaginationSpec spec = query.getPaginationSpec();
        spec.setType(PaginationType.ITERATION);
        spec.setHasNextParam(new PaginationParameter("$.next_offset", PaginationParamType.JSON_PATH));
        spec.setPageOffsetParamToRead(new PaginationParameter("$.next_offset", PaginationParamType.JSON_PATH));
        spec.setPageOffsetParamToWrite(new PaginationParameter("offset", PaginationParamType.QUERY));

        final List<RequestResponse> requestResponses = new ArrayList<>();

        final String page1 = "{\"next_offset\": 2}";
        final String page2 = "{\"next_offset\": 3}";
        final String page3 = "{}";

        requestResponses.add(new RequestResponse(RestRequest.builder().query(query)
           .build(), new TestHttpResponse(page1)));
        requestResponses.add(new RequestResponse(RestRequest.builder().query(query)
           .queryParameters(Collections.singletonMap("offset", "2"))
           .build(), new TestHttpResponse(page2)));
        requestResponses.add(new RequestResponse(RestRequest.builder().query(query)
           .queryParameters(Collections.singletonMap("offset", "3"))
           .build(), new TestHttpResponse(page3)));

        final TestHttpHandler httpHandler = new TestHttpHandler(requestResponses);
        final JsonTransformer transformer = new JsonTransformer();

        final RestDataIteratorStrategy<Object> strategy = new PagedIterationIteratorStrategy<>(
           query, transformer, httpHandler, new RestErrorHandler(), new HttpResponseParameterParser(transformer));
        int count = 0;

        while(strategy.hasNext()) {
            assertNotNull(strategy.next());
            count++;
        }

        assertEquals(3, count);
    }

   @Test
   public void testOffsetInBody() throws Exception {
      final RestJsonQuery query = new RestJsonQuery();
      query.setRequestType("POST");
      final RestJsonDataSource datasource = new RestJsonDataSource();
      datasource.setURL("http://host");
      query.setDataSource(datasource);
      final PaginationSpec spec = query.getPaginationSpec();
      spec.setType(PaginationType.ITERATION);
      spec.setHasNextParam(new PaginationParameter("$.next_offset", PaginationParamType.JSON_PATH));
      spec.setPageOffsetParamToRead(new PaginationParameter("$.next_offset", PaginationParamType.JSON_PATH));
      spec.setPageOffsetParamToWrite(new PaginationParameter("offset", PaginationParamType.JSON_PATH));

      final List<RequestResponse> requestResponses = new ArrayList<>();

      // expected queries in query requests
      final RestJsonQuery query1 = (RestJsonQuery) query.clone();
      final RestJsonQuery query2 = (RestJsonQuery) query.clone();
      query2.setRequestBody("{\"offset\":[2]}");
      final RestJsonQuery query3 = (RestJsonQuery) query.clone();
      query3.setRequestBody("{\"offset\":\"strOffset\"}");

      final String page1 = "{\"next_offset\": [2]}";
      final String page2 = "{\"next_offset\": \"strOffset\"}";
      final String page3 = "{}";

      requestResponses.add(new RequestResponse(RestRequest.builder().query(query1)
                                                  .build(), new TestHttpResponse(page1)));
      requestResponses.add(new RequestResponse(RestRequest.builder().query(query2)
                                                  .build(), new TestHttpResponse(page2)));
      requestResponses.add(new RequestResponse(RestRequest.builder().query(query3)
                                                  .build(), new TestHttpResponse(page3)));

      final TestHttpHandler httpHandler = new TestHttpHandler(requestResponses);
      final JsonTransformer transformer = new JsonTransformer();

      final RestDataIteratorStrategy<Object> strategy = new PagedIterationIteratorStrategy<>(
         query, transformer, httpHandler, new RestErrorHandler(), new HttpResponseParameterParser(transformer));
      int count = 0;

      while(strategy.hasNext()) {
         assertNotNull(strategy.next());
         count++;
      }

      assertEquals(3, count);
   }
}
