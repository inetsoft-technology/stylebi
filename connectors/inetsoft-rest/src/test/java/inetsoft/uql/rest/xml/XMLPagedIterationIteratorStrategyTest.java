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
package inetsoft.uql.rest.xml;

import inetsoft.test.*;
import inetsoft.uql.rest.*;
import inetsoft.uql.rest.pagination.*;
import inetsoft.uql.rest.xml.parse.*;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class XMLPagedIterationIteratorStrategyTest {
   @Test
   public void test() throws Exception {
      final RestXMLQuery query = new RestXMLQuery();
      final RestXMLDataSource datasource = new RestXMLDataSource();
      datasource.setURL("http://host");
      query.setDataSource(datasource);
      query.setXpath("/data/id/text()");
      final PaginationSpec spec = query.getPaginationSpec();
      spec.setType(PaginationType.ITERATION);

      spec.setHasNextParam(new PaginationParameter("/data/nextOffset", PaginationParamType.XPATH));
      spec.setPageOffsetParamToRead(
         new PaginationParameter("/data/nextOffset", PaginationParamType.XPATH));
      spec.setPageOffsetParamToWrite(new PaginationParameter("offset", PaginationParamType.QUERY));

      final List<RequestResponse> requestResponses = new ArrayList<>();
      final String page1 = "<data><id>1</id><nextOffset>2</nextOffset></data>";
      final String page2 = "<data><id>2</id><nextOffset>3</nextOffset></data>";
      final String page3 = "<data><id>3</id><id>4</id><nextOffset/></data>";

      requestResponses.add(new RequestResponse(RestRequest.builder().query(query)
         .build(), new TestHttpResponse(page1)));
      requestResponses.add(new RequestResponse(RestRequest.builder().query(query)
         .queryParameters(Collections.singletonMap("offset", "2"))
         .build(), new TestHttpResponse(page2)));
      requestResponses.add(new RequestResponse(RestRequest.builder().query(query)
         .queryParameters(Collections.singletonMap("offset", "3"))
         .build(), new TestHttpResponse(page3)));

      final TestHttpHandler httpHandler = new TestHttpHandler(requestResponses);
      final XMLIterationStreamTransformer transformer = new XMLIterationStreamTransformer(query);

      final XMLPagedIterationIteratorStrategy strategy = new XMLPagedIterationIteratorStrategy(
         query, transformer, httpHandler, new RestErrorHandler());

      List<Map<String, Object>> expectedValues = new ArrayList<>();
      expectedValues.add(new MapNode(Collections.singletonMap("#text", new ValueNode(1))));
      expectedValues.add(new MapNode(Collections.singletonMap("#text", new ValueNode(2))));
      expectedValues.add(new MapNode(Collections.singletonMap("#text",
         Arrays.asList(new ValueNode(3), new ValueNode(4)))));
      int count = 0;

      for(int i = 0; i < expectedValues.size(); i++, count++) {
         assertEquals(expectedValues.get(i), strategy.next());
      }

      assertFalse(strategy.hasNext());
      assertEquals(expectedValues.size(), count);
   }
}