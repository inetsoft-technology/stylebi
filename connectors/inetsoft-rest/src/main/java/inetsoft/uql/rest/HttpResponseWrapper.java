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
package inetsoft.uql.rest;

import org.apache.http.*;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest;

import java.io.*;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Wraps an HTTP response object.
 */
public class HttpResponseWrapper implements HttpResponse {
   public HttpResponseWrapper(HttpUriRequest request, org.apache.http.HttpResponse response) {
      this.response = response;
      this.uri = request.getURI().toString();
   }

   @Override
   public InputStream getResponseBodyAsStream() throws IOException {
      return response != null && response.getEntity() != null
         ? response.getEntity().getContent() : null;
   }

   @Override
   public String getResponseHeaderValue(String headerName) {
      Header header = response.getFirstHeader(headerName);
      return header != null ? header.getValue() : null;
   }

   @Override
   public Map<String, String> getResponseHeaders() {
      final HeaderIterator headerIterator = response.headerIterator();
      final Map<String, String> result = new LinkedHashMap<>();

      while(headerIterator.hasNext()) {
         final Header header = headerIterator.nextHeader();
         result.putIfAbsent(header.getName(), header.getValue());
      }

      return result;
   }

   @Override
   public String getURI() {
      return uri;
   }

   @Override
   public int getResponseStatusCode() {
      return response.getStatusLine().getStatusCode();
   }

   @Override
   public String getResponseStatusText() {
      return response.getStatusLine().getReasonPhrase();
   }

   @Override
   public long getContentLength() {
      final HttpEntity entity = response.getEntity();
      return entity == null ? 0 : entity.getContentLength();
   }

   @Override
   public void close() throws IOException {
      if(response instanceof CloseableHttpResponse) {
         ((CloseableHttpResponse) response).close();
      }
   }

   private final org.apache.http.HttpResponse response;
   private final String uri;
}
