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

import inetsoft.test.*;
import inetsoft.uql.rest.RestErrorHandler;
import inetsoft.uql.rest.RestRequest;
import inetsoft.uql.rest.pagination.*;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class JsonPagedLinkIterationStrategyTest {
    @Test
    public void test() throws Exception {
        final RestJsonQuery query = new RestJsonQuery();
        final RestJsonDataSource datasource = new RestJsonDataSource();
        datasource.setURL("http://host");
        query.setDataSource(datasource);

        final PaginationSpec spec = query.getPaginationSpec();
        spec.setType(PaginationType.LINK_ITERATION);
        final PaginationParameter linkParam = new PaginationParameter("Link", PaginationParamType.LINK_HEADER);
        linkParam.setLinkRelation("next");
        spec.setLinkParam(linkParam);

        final List<RequestResponse> requestResponses = new ArrayList<>();

        final String page1 = "{}";
        final String page2 = "{}";
        final String page3 = "{}";

        final List<HttpHeader> page1Headers = Collections.singletonList(
           new HttpHeader("Link", "<http://example.com?page=2>; rel=\"next\""));
        final List<HttpHeader> page2Headers = Collections.singletonList(
           new HttpHeader("Link", "<http://example.com?page=3>; rel=\"next\">"));

        requestResponses.add(new RequestResponse(RestRequest.builder().query(query)
           .build(), new TestHttpResponse(page1, page1Headers)));
        requestResponses.add(new RequestResponse(RestRequest.builder().query(query)
           .url(URI.create("http://example.com?page=2").toURL())
           .build(), new TestHttpResponse(page2, page2Headers)));
        requestResponses.add(new RequestResponse(RestRequest.builder().query(query)
           .url(URI.create("http://example.com?page=3").toURL())
           .build(), new TestHttpResponse(page3)));

        final TestHttpHandler httpHandler = new TestHttpHandler(requestResponses);
        final JsonTransformer transformer = new JsonTransformer();

        final JsonPagedLinkIterationStrategy strategy = new JsonPagedLinkIterationStrategy(
           query, transformer, httpHandler, new RestErrorHandler(), new HttpResponseParameterParser(transformer));
        int count = 0;

        while(strategy.hasNext()) {
            assertEquals(Collections.emptyMap(), strategy.next());
            count++;
        }

        assertEquals(3, count);
    }
}
