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
package inetsoft.test;

import inetsoft.uql.rest.HttpResponse;
import inetsoft.uql.rest.pagination.HttpHeader;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Class intended for mocking HTTP responses.
 */
public class TestHttpResponse implements HttpResponse {
   public TestHttpResponse(String responseBody) {
      this(responseBody, Collections.emptyList());
   }

   public TestHttpResponse(String responseBody, List<HttpHeader> headers) {
      this(new ByteArrayInputStream(responseBody.getBytes(StandardCharsets.UTF_8)), headers,
         responseBody.length());
      this.responseBodyString = responseBody;
   }

   public TestHttpResponse(List<HttpHeader> headers) {
      this(null, headers, 0);
   }

   public TestHttpResponse(InputStream responseBody, List<HttpHeader> headers, long contentLength) {
      this.responseBody = responseBody;
      this.headers = headers;
      this.contentLength = contentLength;
   }

   @Override
   public InputStream getResponseBodyAsStream() {
      return responseBody;
   }

   @Override
   public String getResponseHeaderValue(String headerName) {
      return headers.stream()
         .filter(h -> h.getName().equals(headerName))
         .findFirst()
         .map(HttpHeader::getValue)
         .orElse(null);
   }

   @Override
   public Map<String, String> getResponseHeaders() {
      return headers.stream()
         .collect(Collectors.toMap(HttpHeader::getName, HttpHeader::getValue, (oldValue, newValue) -> oldValue));
   }

   @Override
   public String getURI() {
      return null;
   }

   @Override
   public int getResponseStatusCode() {
      return 200;
   }

   @Override
   public String getResponseStatusText() {
      return "OK";
   }

   @Override
   public long getContentLength() {
      return contentLength;
   }

   @Override
   public void close() {
      // no-op
   }

   private final InputStream responseBody;
   private final List<HttpHeader> headers;
   private final long contentLength;

   @SuppressWarnings({"unused", "FieldCanBeLocal"})
   private String responseBodyString; // Makes the responseBody easily readable when debugging.
}
