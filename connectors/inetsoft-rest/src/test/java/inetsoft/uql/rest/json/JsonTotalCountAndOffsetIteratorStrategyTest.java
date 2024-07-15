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
package inetsoft.uql.rest.json;

import inetsoft.test.RequestResponse;
import inetsoft.test.TestHttpHandler;
import inetsoft.test.TestHttpResponse;
import inetsoft.uql.rest.RestErrorHandler;
import inetsoft.uql.rest.RestRequest;
import inetsoft.uql.rest.pagination.*;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class JsonTotalCountAndOffsetIteratorStrategyTest {
    @Test
    public void test() throws Exception {
        final RestJsonQuery query = new RestJsonQuery();
        final RestJsonDataSource datasource = new RestJsonDataSource();
        datasource.setURL("http://host");
        query.setDataSource(datasource);
        final PaginationSpec spec = query.getPaginationSpec();
        spec.setType(PaginationType.TOTAL_COUNT_AND_OFFSET);
        spec.setTotalCountParam(new PaginationParameter("$.totalCount", PaginationParamType.JSON_PATH));
        spec.setOffsetParam(new PaginationParameter("offset", PaginationParamType.QUERY));
        spec.setMaxResultsPerPage(2);

        final List<RequestResponse> requestResponses = new ArrayList<>();

        final String page1 = "{\"totalCount\": 5}"; // 2
        final String page2 = "{}"; // 4
        final String page3 = "{}"; // 5

        requestResponses.add(new RequestResponse(RestRequest.builder().query(query)
           .build(), new TestHttpResponse(page1)));
        requestResponses.add(new RequestResponse(RestRequest.builder().query(query)
           .queryParameters(Collections.singletonMap("offset", "2"))
           .build(), new TestHttpResponse(page2)));
        requestResponses.add(new RequestResponse(RestRequest.builder().query(query)
           .queryParameters(Collections.singletonMap("offset", "4"))
           .build(), new TestHttpResponse(page3)));

        final TestHttpHandler httpHandler = new TestHttpHandler(requestResponses);
        final JsonTransformer transformer = new JsonTransformer();

        final JsonTotalCountAndOffsetIteratorStrategy strategy = new JsonTotalCountAndOffsetIteratorStrategy(
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
        spec.setType(PaginationType.TOTAL_COUNT_AND_OFFSET);
        spec.setTotalCountParam(new PaginationParameter("$.totalCount", PaginationParamType.JSON_PATH));
        spec.setOffsetParam(new PaginationParameter("$.offset", PaginationParamType.JSON_PATH));
        spec.setMaxResultsPerPage(2);

        final List<RequestResponse> requestResponses = new ArrayList<>();

        // expected queries in query requests
        final RestJsonQuery query1 = (RestJsonQuery) query.clone();
        final RestJsonQuery query2 = (RestJsonQuery) query.clone();
        query2.setRequestBody("{\"offset\":2}");
        final RestJsonQuery query3 = (RestJsonQuery) query.clone();
        query3.setRequestBody("{\"offset\":4}");

        final String page1 = "{\"totalCount\": 5}"; // 2
        final String page2 = "{}"; // 4
        final String page3 = "{}"; // 5

        requestResponses.add(new RequestResponse(RestRequest.builder().query(query1)
                                                    .build(), new TestHttpResponse(page1)));
        requestResponses.add(new RequestResponse(RestRequest.builder().query(query2)
                                                    .build(), new TestHttpResponse(page2)));
        requestResponses.add(new RequestResponse(RestRequest.builder().query(query3)
                                                    .build(), new TestHttpResponse(page3)));

        final TestHttpHandler httpHandler = new TestHttpHandler(requestResponses);
        final JsonTransformer transformer = new JsonTransformer();

        final JsonTotalCountAndOffsetIteratorStrategy strategy = new JsonTotalCountAndOffsetIteratorStrategy(
           query, transformer, httpHandler, new RestErrorHandler(), new HttpResponseParameterParser(transformer));
        int count = 0;

        while(strategy.hasNext()) {
            assertNotNull(strategy.next());
            count++;
        }

        assertEquals(3, count);
    }


    @Test
    public void testOffsetWithExistingBody() throws Exception {
        final RestJsonQuery query = new RestJsonQuery();
        query.setRequestType("POST");
        query.setRequestBody("{\"test\":true,\"offset\":0}");
        final RestJsonDataSource datasource = new RestJsonDataSource();
        datasource.setURL("http://host");
        query.setDataSource(datasource);
        final PaginationSpec spec = query.getPaginationSpec();
        spec.setType(PaginationType.TOTAL_COUNT_AND_OFFSET);
        spec.setTotalCountParam(new PaginationParameter("$.totalCount", PaginationParamType.JSON_PATH));
        spec.setOffsetParam(new PaginationParameter("$.offset", PaginationParamType.JSON_PATH));
        spec.setMaxResultsPerPage(2);

        final List<RequestResponse> requestResponses = new ArrayList<>();

        // expected queries in query requests
        final RestJsonQuery query1 = (RestJsonQuery) query.clone();
        query1.setRequestBody("{\"test\":true,\"offset\":0}");
        final RestJsonQuery query2 = (RestJsonQuery) query.clone();
        query2.setRequestBody("{\"test\":true,\"offset\":2}");
        final RestJsonQuery query3 = (RestJsonQuery) query.clone();
        query3.setRequestBody("{\"test\":true,\"offset\":4}");

        final String page1 = "{\"totalCount\": 5}"; // 2
        final String page2 = "{}"; // 4
        final String page3 = "{}"; // 5

        requestResponses.add(new RequestResponse(RestRequest.builder().query(query1)
                                                    .build(), new TestHttpResponse(page1)));
        requestResponses.add(new RequestResponse(RestRequest.builder().query(query2)
                                                    .build(), new TestHttpResponse(page2)));
        requestResponses.add(new RequestResponse(RestRequest.builder().query(query3)
                                                    .build(), new TestHttpResponse(page3)));

        final TestHttpHandler httpHandler = new TestHttpHandler(requestResponses);
        final JsonTransformer transformer = new JsonTransformer();

        final JsonTotalCountAndOffsetIteratorStrategy strategy = new JsonTotalCountAndOffsetIteratorStrategy(
           query, transformer, httpHandler, new RestErrorHandler(), new HttpResponseParameterParser(transformer));
        int count = 0;

        while(strategy.hasNext()) {
            assertNotNull(strategy.next());
            count++;
        }

        assertEquals(3, count);
    }
}
