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
package inetsoft.uql.rest;

import inetsoft.test.TestHttpResponse;
import inetsoft.uql.rest.pagination.HttpHeader;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SerializableHttpResponseTest {
   @Test
   public void serializationTest() throws IOException, ClassNotFoundException {
      final String body = "response body";
      final List<HttpHeader> headers = Arrays.asList(new HttpHeader("header name", "header value"));
      final TestHttpResponse testResponse = new TestHttpResponse(body, headers);
      final SerializableHttpResponse serializableResponse = new SerializableHttpResponse(testResponse);

      final ByteArrayOutputStream output = new ByteArrayOutputStream();

      try(ObjectOutputStream out = new ObjectOutputStream(output)) {
         out.writeObject(serializableResponse);
      }

      try(ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(output.toByteArray()))) {
         SerializableHttpResponse response = (SerializableHttpResponse) in.readObject();
         assertEquals("header value", response.getResponseHeaderValue("header name"));
         assertEquals("response body", new String(IOUtils.toByteArray(response.getResponseBodyAsStream())));
      }
   }
}
