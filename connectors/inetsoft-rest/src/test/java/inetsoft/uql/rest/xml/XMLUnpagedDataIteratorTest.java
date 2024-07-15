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
import org.junit.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class XMLUnpagedDataIteratorTest {
    @Test
    public void test() throws Exception {
        final RestXMLQuery query = new RestXMLQuery();
        final RestXMLDataSource datasource = new RestXMLDataSource();
        datasource.setURL("file:///test.txt");
        query.setDataSource(datasource);

        final List<RequestResponse> requestResponses = new ArrayList<>();
        final String response = "<value>1</value>";
        requestResponses.add(new RequestResponse(RestRequest.fromQuery(query), new TestHttpResponse(response)));

        final TestHttpHandler httpHandler = new TestHttpHandler(requestResponses);
        final XMLBasicStreamTransformer transformer = new XMLBasicStreamTransformer(query);

        final XMLUnpagedDataIterator strategy = new XMLUnpagedDataIterator(
           query, transformer, httpHandler, new RestErrorHandler());

        while(strategy.hasNext()) {
            final Object next = strategy.next();
            assertNotNull(next);
            System.out.println(next);
        }
    }
}
