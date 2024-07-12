/*
 * inetsoft-rest - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.test;

import inetsoft.uql.rest.HttpResponse;
import inetsoft.uql.rest.RestRequest;

import java.util.Objects;

/**
 * Request-response tuple.
 */
public class RequestResponse {
    public RequestResponse(RestRequest request, HttpResponse response) {
        this.request = request;
        this.response = response;
    }

    public RestRequest getRequest() {
        return request;
    }

    public HttpResponse getResponse() {
        return response;
    }

    @Override
    public boolean equals(Object o) {
        if(this == o) {
            return true;
        }

        if(o == null || getClass() != o.getClass()) {
            return false;
        }

        RequestResponse that = (RequestResponse) o;
        return Objects.equals(request, that.request) &&
           Objects.equals(response, that.response);
    }

    @Override
    public int hashCode() {
        return Objects.hash(request, response);
    }

    @Override
    public String toString() {
        return "RequestResponse{" +
           "request=" + request +
           ", response=" + response +
           '}';
    }

    private final RestRequest request;
    private final HttpResponse response;
}
