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

import inetsoft.test.*;
import inetsoft.uql.rest.HttpResponse;
import inetsoft.uql.rest.IHttpHandler;
import inetsoft.uql.rest.RestErrorHandler;
import inetsoft.uql.rest.RestRequest;
import inetsoft.uql.rest.pagination.PaginationParamType;
import inetsoft.uql.rest.pagination.PaginationParameter;
import inetsoft.uql.rest.pagination.PaginationSpec;
import inetsoft.uql.rest.pagination.PaginationType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.IOException;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, CredentialTestConfig.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
public class JsonPagedOffsetIteratorStrategyTest {
    @Test
    public void test() throws Exception {
        final RestJsonQuery query = new RestJsonQuery();
        final RestJsonDataSource datasource = new RestJsonDataSource();
        datasource.setURL("http://host");
        query.setDataSource(datasource);
        final PaginationSpec spec = query.getPaginationSpec();
        spec.setType(PaginationType.OFFSET);
        spec.setRecordCountPath("$.results");
        spec.setOffsetParam(new PaginationParameter("offset", PaginationParamType.QUERY));
        spec.setMaxResultsPerPage(10);
        spec.setZeroBasedPageIndex(false);

        final List<RequestResponse> requestResponses = new ArrayList<>();

        final String page1 = "{\"results\": 2}";
        final String page2 = "{\"results\": 3}";
        final String page3 = "{}";

        requestResponses.add(new RequestResponse(RestRequest.builder().query(query)
           .queryParameters(Collections.singletonMap("offset", "1"))
           .build(), new TestHttpResponse(page1)));
        requestResponses.add(new RequestResponse(RestRequest.builder().query(query)
           .queryParameters(Collections.singletonMap("offset", "3"))
           .build(), new TestHttpResponse(page2)));
        requestResponses.add(new RequestResponse(RestRequest.builder().query(query)
           .queryParameters(Collections.singletonMap("offset", "6"))
           .build(), new TestHttpResponse(page3)));

        final TestHttpHandler httpHandler = new TestHttpHandler(requestResponses);
        final JsonTransformer transformer = new JsonTransformer();

        final JsonPagedOffsetIteratorStrategy strategy = new JsonPagedOffsetIteratorStrategy(
           query, transformer, httpHandler, new RestErrorHandler());
        int count = 0;

        while(strategy.hasNext()) {
            assertNotNull(strategy.next());
            count++;
        }

        assertEquals(2, count);
    }

    @Test
    public void shortPageEndsTheReadWhenAskedTo() throws Exception {
        final RestJsonQuery query = offsetQuery(true);
        // only the first page is registered: a request for another one fails the handler
        final TestHttpHandler httpHandler = new TestHttpHandler(List.of(
           page(query, 1, "{\"results\": 2}")));

        assertEquals(1, read(query, httpHandler));
    }

    @Test
    public void fullPageIsStillFollowedByTheEmptyPageWhenAskedToStopOnShortPage() throws Exception {
        final RestJsonQuery query = offsetQuery(true);
        final TestHttpHandler httpHandler = new TestHttpHandler(List.of(
           page(query, 1, "{\"results\": 10}"),
           page(query, 11, "{\"results\": 4}")));

        assertEquals(2, read(query, httpHandler));

        final RestJsonQuery exact = offsetQuery(true);
        final TestHttpHandler exactHandler = new TestHttpHandler(List.of(
           page(exact, 1, "{\"results\": 10}"),
           page(exact, 11, "{}")));

        assertEquals(1, read(exact, exactHandler));
    }

    @Test
    public void shortPageDoesNotEndTheReadByDefault() throws Exception {
        final RestJsonQuery query = offsetQuery(false);
        final TestHttpHandler httpHandler = new TestHttpHandler(List.of(
           page(query, 1, "{\"results\": 2}"),
           page(query, 3, "{}")));

        assertEquals(1, read(query, httpHandler));
    }

    @Test
    public void emptyFirstPageEndsTheReadAndRecordsTheCompletedRun() throws Exception {
        final RestJsonQuery query = offsetQuery(false);
        final TestHttpHandler httpHandler = new TestHttpHandler(List.of(
           page(query, 1, "{}")));
        final JsonPagedOffsetIteratorStrategy strategy = new JsonPagedOffsetIteratorStrategy(
           query, new JsonTransformer(), httpHandler, new RestErrorHandler());

        assertFalse(strategy.hasNext());
        assertTrue(query.isRunCompleted());
    }

    @Test
    public void failedRequestDoesNotRecordTheCompletedRun() throws Exception {
        final RestJsonQuery query = offsetQuery(false);
        final IHttpHandler failing = new IHttpHandler() {
            @Override
            public HttpResponse executeRequest(RestRequest request) throws IOException {
                throw new IOException("boom");
            }

            @Override
            public void close() {
            }
        };
        final JsonPagedOffsetIteratorStrategy strategy = new JsonPagedOffsetIteratorStrategy(
           query, new JsonTransformer(), failing, new RestErrorHandler());

        assertThrows(IOException.class, strategy::hasNext);
        assertFalse(query.isRunCompleted());
    }

    private static RestJsonQuery offsetQuery(boolean stopOnShortPage) {
        final RestJsonQuery query = new RestJsonQuery();
        final RestJsonDataSource datasource = new RestJsonDataSource();
        datasource.setURL("http://host");
        query.setDataSource(datasource);
        final PaginationSpec spec = query.getPaginationSpec();
        spec.setType(PaginationType.OFFSET);
        spec.setRecordCountPath("$.results");
        spec.setOffsetParam(new PaginationParameter("offset", PaginationParamType.QUERY));
        spec.setMaxResultsPerPage(10);
        spec.setZeroBasedPageIndex(false);
        spec.setStopOnShortPage(stopOnShortPage);
        return query;
    }

    private static RequestResponse page(RestJsonQuery query, int offset, String body) {
        return new RequestResponse(RestRequest.builder().query(query)
           .queryParameters(Collections.singletonMap("offset", Integer.toString(offset)))
           .build(), new TestHttpResponse(body));
    }

    private static int read(RestJsonQuery query, TestHttpHandler httpHandler) throws Exception {
        final JsonPagedOffsetIteratorStrategy strategy = new JsonPagedOffsetIteratorStrategy(
           query, new JsonTransformer(), httpHandler, new RestErrorHandler());
        int count = 0;

        while(strategy.hasNext()) {
            assertNotNull(strategy.next());
            count++;
        }

        return count;
    }
}
