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

import inetsoft.uql.rest.json.*;
import org.junit.jupiter.api.Test;

import java.net.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

class URLCreatorTest {
   @Test
   void createBasicUrl() throws MalformedURLException, URISyntaxException {
      final AbstractRestQuery query = createBaseQuery();
      query.setSuffix("/all");

      final RestRequest request = RestRequest.builder()
         .query(query)
         .build();

      final URL actualUrl = URLCreator.fromRestRequest(request);
      final URL expectedUrl = new URL("https://restcountries.eu/rest/v2/all");
      assertEquals(expectedUrl, actualUrl);
   }

   @Test
   void createUrlWithQueryParameters() throws MalformedURLException, URISyntaxException {
      final AbstractRestQuery query = createBaseQuery();
      query.setSuffix("/name/aruba");

      final Map<String, String> params = new HashMap<>();
      params.put("fullText", "true");

      final RestRequest request = RestRequest.builder()
         .query(query)
         .queryParameters(params)
         .build();

      final URL actualUrl = URLCreator.fromRestRequest(request);
      final URL expectedUrl = new URL("https://restcountries.eu/rest/v2/name/aruba?fullText=true");
      assertEquals(expectedUrl, actualUrl);
   }

   @Test
   void createUrlWithUrlVariables() throws MalformedURLException, URISyntaxException {
      final AbstractRestQuery query = createBaseQuery();
      query.setSuffix("/currency/{{currency}}");

      final Map<String, String> params = new HashMap<>();
      params.put("currency", "JPY");

      final RestRequest request = RestRequest.builder()
         .query(query)
         .urlVariables(params)
         .build();

      final URL actualUrl = URLCreator.fromRestRequest(request);
      final URL expectedUrl = new URL("https://restcountries.eu/rest/v2/currency/JPY");
      assertEquals(expectedUrl, actualUrl);
   }

   @Test
   void createFileUrl() throws MalformedURLException, URISyntaxException {
      final RestJsonDataSource dataSource = new RestJsonDataSource();
      dataSource.setURL("file:///home/user/");
      final RestJsonQuery query = new RestJsonQuery();
      query.setDataSource(dataSource);
      query.setSuffix("file.json");

      final RestRequest request = RestRequest.builder()
         .query(query)
         .build();

      final URL actualUrl = URLCreator.fromRestRequest(request);
      final URL expectedUrl = new URL("file:///home/user/file.json");
      assertEquals(expectedUrl, actualUrl);
   }

   @Test
   void createUrlWithQueryParamsAsSuffix() throws MalformedURLException, URISyntaxException {
      final RestJsonDataSource dataSource = new RestJsonDataSource();
      dataSource.setURL("https://restcountries.eu/rest/v2/name/aruba?");

      final RestJsonQuery query = new RestJsonQuery();
      query.setDataSource(dataSource);
      query.setSuffix("fullText=true");

      final RestRequest request = RestRequest.builder()
         .query(query)
         .build();

      final URL actualUrl = URLCreator.fromRestRequest(request);
      final URL expectedUrl = new URL("https://restcountries.eu/rest/v2/name/aruba?fullText=true");
      assertEquals(expectedUrl, actualUrl);
   }

   private AbstractRestQuery createBaseQuery() {
      final RestJsonDataSource dataSource = new RestJsonDataSource();
      dataSource.setURL("https://restcountries.eu/rest/v2");

      final RestJsonQuery query = new RestJsonQuery();
      query.setDataSource(dataSource);

      return query;
   }
}