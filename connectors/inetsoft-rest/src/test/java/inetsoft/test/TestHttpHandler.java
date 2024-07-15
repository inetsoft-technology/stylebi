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
package inetsoft.test;

import inetsoft.uql.rest.*;

import java.net.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class TestHttpHandler implements IHttpHandler {
    public TestHttpHandler(List<RequestResponse> requestResponses)
       throws MalformedURLException, URISyntaxException
    {
        this.requestResponses = new HashMap<>();

        for(RequestResponse requestResponse : requestResponses) {
            this.requestResponses.put(requestResponse.getRequest().key(), requestResponse);
        }
    }

    public HttpResponse executeRequest(RestRequest request)
       throws MalformedURLException, URISyntaxException
    {
        final RequestResponse requestResponse = requestResponses.get(request.key());
        assertNotNull(requestResponse, "Expected request key: " + request.key());
        assertArrayEquals(requestResponse.getRequest().dataSource().getQueryHttpParameters(),
           request.dataSource().getQueryHttpParameters());

        return requestResponse.getResponse();
    }

    @Override
    public void close() {
        // no-op
    }

    private final Map<String, RequestResponse> requestResponses;
}
