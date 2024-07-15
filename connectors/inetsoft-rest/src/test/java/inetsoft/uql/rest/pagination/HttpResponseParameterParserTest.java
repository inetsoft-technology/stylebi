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
package inetsoft.uql.rest.pagination;

import inetsoft.test.TestHttpResponse;
import inetsoft.uql.rest.json.JsonTransformer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class HttpResponseParameterParserTest {
   @Test
   public void jsonIntParam() {
      final int expected = 1;
      final LinkedHashMap<String, Object> obj = new LinkedHashMap<>();
      obj.put("page", expected);
      final PaginationParameter param = new PaginationParameter("$.page");
      param.setType(PaginationParamType.JSON_PATH);

      final int actual = parser.parseInt(param, obj, null);
      assertEquals(expected, actual);
   }

   @Test
   void jsonStringParam() {
      final String expected = "abc";
      final LinkedHashMap<String, Object> obj = new LinkedHashMap<>();
      obj.put("prop", expected);
      final PaginationParameter param = new PaginationParameter("$.prop");
      param.setType(PaginationParamType.JSON_PATH);

      final String actual = parser.parseString(param, obj, null);
      assertEquals(expected, actual);
   }

   @Test
   void jsonBooleanParam() {
      final boolean expected = true;
      final LinkedHashMap<String, Object> obj = new LinkedHashMap<>();
      obj.put("bool", expected);
      final PaginationParameter param = new PaginationParameter("$.bool");
      param.setType(PaginationParamType.JSON_PATH);

      final boolean actual = parser.parseBoolean(param, obj, null);
      assertEquals(expected, actual);
   }

   @Test
   void headerInt() {
      final int expected = 1;
      final String name = "header";
      final String value = String.valueOf(expected);
      final TestHttpResponse response = new TestHttpResponse(
         Collections.singletonList(new HttpHeader(name, value)));

      final PaginationParameter param = new PaginationParameter(name);
      param.setType(PaginationParamType.HEADER);

      final int actual = parser.parseInt(param, null, response);
      assertEquals(expected, actual);
   }

   @Test
   void headerBoolean() {
      final String expected = "true";
      final String name = "header";
      final TestHttpResponse response = new TestHttpResponse(
         Collections.singletonList(new HttpHeader(name, expected)));

      final PaginationParameter param = new PaginationParameter(name);
      param.setType(PaginationParamType.HEADER);

      final String actual = parser.parseString(param, null, response);
      assertEquals(expected, actual);
   }

   @Test
   void jsonPathExpression() throws IOException {
      final Object json = transformer.transform(loader.getResourceAsStream("links.json"));

      final PaginationParameter param =
         new PaginationParameter("$._links[?(@.rel == 'next')].href");
      param.setType(PaginationParamType.JSON_PATH);

      final URL expected = new URL("https://api.gosquared.com/people/v1/people?limit=8%2C1");
      final URL actual = parser.parseURL(param, json, null);
      assertEquals(expected, actual);
   }

   private final JsonTransformer transformer = new JsonTransformer();
   private final HttpResponseParameterParser parser = new HttpResponseParameterParser(transformer);
   private final ClassLoader loader = HttpResponseParameterParserTest.class.getClassLoader();
}
