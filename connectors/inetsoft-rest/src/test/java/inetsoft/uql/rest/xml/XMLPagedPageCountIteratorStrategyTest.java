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
package inetsoft.uql.rest.xml;

import inetsoft.test.RequestResponse;
import inetsoft.test.TestHttpHandler;
import inetsoft.test.TestHttpResponse;
import inetsoft.uql.rest.RestErrorHandler;
import inetsoft.uql.rest.RestRequest;
import inetsoft.uql.rest.pagination.PaginationParamType;
import inetsoft.uql.rest.pagination.PaginationParameter;
import inetsoft.uql.rest.pagination.PaginationSpec;
import inetsoft.uql.rest.pagination.PaginationType;
import inetsoft.uql.rest.xml.parse.*;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class XMLPagedPageCountIteratorStrategyTest {
    @Test
    public void test() throws Exception {
        final RestXMLQuery query = new RestXMLQuery();
        final RestXMLDataSource datasource = new RestXMLDataSource();
        datasource.setURL("http://host/{{pageNumber}}");
        query.setDataSource(datasource);
        query.setXpath("/data/id/text()");
        final PaginationSpec spec = query.getPaginationSpec();
        spec.setType(PaginationType.PAGE_COUNT);
        spec.setPageNumberUrlVariable(new PaginationParameter("pageNumber", PaginationParamType.URL_VARIABLE));
        spec.setPageCountXpath(new PaginationParameter("/data/pageCount", PaginationParamType.XPATH));
        spec.setZeroBasedPageIndex(false);

        final List<RequestResponse> requestResponses = new ArrayList<>();
        final String page1 = "<data><pageCount>3</pageCount><id>1</id></data>";
        final String page2 = "<data><pageCount>3</pageCount><id>2</id></data>";
        final String page3 = "<data><pageCount>3</pageCount><id>3</id><id>4</id></data>";

        requestResponses.add(new RequestResponse(RestRequest.builder().query(query)
           .urlVariables(Collections.singletonMap("pageNumber", "1"))
           .build(), new TestHttpResponse(page1)));
        requestResponses.add(new RequestResponse(RestRequest.builder().query(query)
           .urlVariables(Collections.singletonMap("pageNumber", "2"))
           .build(), new TestHttpResponse(page2)));
        requestResponses.add(new RequestResponse(RestRequest.builder().query(query)
           .urlVariables(Collections.singletonMap("pageNumber", "3"))
           .build(), new TestHttpResponse(page3)));

        final TestHttpHandler httpHandler = new TestHttpHandler(requestResponses);
        final XMLPagedStreamTransformer transformer = new XMLPagedStreamTransformer(query);

        final XMLPagedPageCountIteratorStrategy strategy = new XMLPagedPageCountIteratorStrategy(
           query, transformer, httpHandler, new RestErrorHandler());

        List<Map<String, Object>> values = new ArrayList<>();
        values.add(new MapNode(Collections.singletonMap("#text", new ValueNode(1))));
        values.add(new MapNode(Collections.singletonMap("#text", new ValueNode(2))));
        values.add(new MapNode(Collections.singletonMap("#text", Arrays.asList(new ValueNode(3), new ValueNode(4)))));
        int count = 0;

        for(int i = 0; i < values.size(); i++, count++) {
            assertEquals(values.get(i), strategy.next());
        }

        assertFalse(strategy.hasNext());
        assertEquals(values.size(), count);
    }
}
