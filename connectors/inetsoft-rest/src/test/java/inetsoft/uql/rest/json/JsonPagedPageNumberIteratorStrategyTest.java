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
import inetsoft.uql.rest.RestErrorHandler;
import inetsoft.uql.rest.RestRequest;
import inetsoft.uql.rest.pagination.PaginationParamType;
import inetsoft.uql.rest.pagination.PaginationParameter;
import inetsoft.uql.rest.pagination.PaginationSpec;
import inetsoft.uql.rest.pagination.PaginationType;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class JsonPagedPageNumberIteratorStrategyTest {
    @Test
    public void test() throws Exception {
        final RestJsonQuery query = new RestJsonQuery();
        final RestJsonDataSource datasource = new RestJsonDataSource();
        datasource.setURL("http://host");
        query.setDataSource(datasource);
        final PaginationSpec spec = query.getPaginationSpec();
        spec.setType(PaginationType.PAGE);
        spec.setPageNumberParamToWrite(new PaginationParameter("pageNumber", PaginationParamType.QUERY));
        spec.setZeroBasedPageIndex(false);
        spec.setRecordCountPath("results");

        final List<RequestResponse> requestResponses = new ArrayList<>();

        final String page1 = "{\"pageNumber\": 1, \"results\": 1}";
        final String page2 = "{\"pageNumber\": 2, \"results\": 1}";
        final String page3 = "{}";

        requestResponses.add(new RequestResponse(RestRequest.builder().query(query)
           .queryParameters(Collections.singletonMap("pageNumber", "1"))
           .build(), new TestHttpResponse(page1)));
        requestResponses.add(new RequestResponse(RestRequest.builder().query(query)
           .queryParameters(Collections.singletonMap("pageNumber", "2"))
           .build(), new TestHttpResponse(page2)));
        requestResponses.add(new RequestResponse(RestRequest.builder().query(query)
           .queryParameters(Collections.singletonMap("pageNumber", "3"))
           .build(), new TestHttpResponse(page3)));

        final TestHttpHandler httpHandler = new TestHttpHandler(requestResponses);

        final JsonPagedPageNumberIteratorStrategy strategy = new JsonPagedPageNumberIteratorStrategy(
           query, new JsonTransformer(), httpHandler, new RestErrorHandler());
        int count = 0;

        while(strategy.hasNext()) {
            assertNotNull(strategy.next());
            count++;
        }

        assertEquals(2, count);
    }
}
