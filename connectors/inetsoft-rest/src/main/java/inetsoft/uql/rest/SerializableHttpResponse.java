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
package inetsoft.uql.rest;

import org.apache.commons.io.IOUtils;

import java.io.*;
import java.util.*;

/**
 * Class that facilitates serializing HTTP responses to be able to cache them to disk.
 */
public class SerializableHttpResponse implements HttpResponse, Serializable {
   public SerializableHttpResponse(HttpResponse response) throws IOException {
      this.responseBody = IOUtils.toByteArray(response.getResponseBodyAsStream());
      this.responseHeaderValues = response.getResponseHeaders();
      this.uri = response.getURI();
      this.responseStatusCode = response.getResponseStatusCode();
      this.responseStatusText = response.getResponseStatusText();
      this.contentLength = response.getContentLength();
   }

   @Override
   public InputStream getResponseBodyAsStream() {
      return new ByteArrayInputStream(this.responseBody);
   }

   @Override
   public String getResponseHeaderValue(String headerName) {
      return responseHeaderValues.get(headerName);
   }

   @Override
   public Map<String, String> getResponseHeaders() {
      return Collections.unmodifiableMap(responseHeaderValues);
   }

   @Override
   public String getURI() {
      return uri;
   }

   @Override
   public int getResponseStatusCode() {
      return responseStatusCode;
   }

   @Override
   public String getResponseStatusText() {
      return responseStatusText;
   }

   @Override
   public long getContentLength() {
      return contentLength;
   }

   @Override
   public void close() throws IOException {
      // no-op
   }

   private final byte[] responseBody;
   private final Map<String, String> responseHeaderValues;
   private final String uri;
   private final int responseStatusCode;
   private final String responseStatusText;
   private final long contentLength;
}
