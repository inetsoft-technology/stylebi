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
package inetsoft.uql.rest.xml.lookup;

import inetsoft.report.lens.xnode.XNodeTableLens;
import inetsoft.test.*;
import inetsoft.test.TestEndpoint;
import inetsoft.uql.XTableNode;
import inetsoft.uql.rest.*;
import inetsoft.uql.rest.json.*;
import inetsoft.uql.rest.json.lookup.*;
import inetsoft.uql.rest.xml.*;
import org.junit.jupiter.api.Test;

import java.net.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class XMLLookupIntegrationTest {
   @Test
   void lookupEndpoint() {
      final TestQuery query = new TestQuery();
      final TestEndpoint endpoint = query.getEndpointMap().get("Customers");
      assertEquals(endpoint.getLookups().size(), 1);

      final JsonLookupEndpoint lookup = endpoint.getLookups().get(0);

      final EndpointJsonQuery.Endpoint lookupEndpointReference = query.getEndpointMap().get(lookup.endpoint());

      assertEquals(query.getEndpointMap().get("Customer Orders"), lookupEndpointReference);
   }

   @Test
   void runsLookupQueries() throws MalformedURLException, URISyntaxException {
      final TestQuery query = createTestQuery();

      final List<RequestResponse> requestResponses = createRequestResponses(query);
      final TestHttpHandler httpHandler = new TestHttpHandler(requestResponses);
      final XMLRestDataIteratorStrategyFactory factory =
         new XMLRestDataIteratorStrategyFactory(httpHandler);
      final RestXMLQueryRunner runner = new RestXMLQueryRunner(query, factory);

      final XTableNode data = runner.run();
      final XNodeTableLens lens = new XNodeTableLens(data);
      lens.moreRows(Integer.MAX_VALUE);
//        Util.printTable(lens);
      assertEquals("s1", lens.getObject(1, 2));
      assertEquals("s2", lens.getObject(2, 2));
      assertEquals("s3", lens.getObject(3, 2));
      assertEquals(lens.getRowCount(), 4);
   }

   private List<RequestResponse> createRequestResponses(TestQuery query) throws MalformedURLException {
      final List<RequestResponse> requestResponses = new ArrayList<>();
      final String customers =
         "<Customers>" +
            "<Customer>" +
               "<id>c1</id>" +
            "</Customer>" +
            "<Customer>" +
               "<id>c2</id>" +
            "</Customer>" +
         "</Customers>";
      
      final String orders1 =
         "<Orders>" +
            "<Order>" +
               "<id>o1</id>" +
            "</Order>" +
            "<Order>" +
               "<id>o2</id>" +
            "</Order>" +
         "</Orders>";
      final String orders2 =
         "<Orders>" +
            "<Order>" +
               "<id>o3</id>" +
            "</Order>" +
         "</Orders>";
      
      final String supplier1 =
         "<Supplier>" +
            "<name>s1</name>" +
         "</Supplier>";
      final String supplier2 =
         "<Supplier>" +
            "<name>s2</name>" +
         "</Supplier>";
      final String supplier3 =
         "<Supplier>" +
            "<name>s3</name>" +
         "</Supplier>";

      query = (TestQuery) query.clone();
      query.setUrl("http://host/customers");
      requestResponses.add(new RequestResponse(RestRequest.builder().query(query)
         .build(), new TestHttpResponse(customers)));

      query = (TestQuery) query.clone();
      query.setUrl("http://host/customers/c1/orders");
      requestResponses.add(new RequestResponse(RestRequest.builder().query(query)
         .build(), new TestHttpResponse(orders1)));

      query = (TestQuery) query.clone();
      query.setUrl("http://host/customers/c2/orders");
      requestResponses.add(new RequestResponse(RestRequest.builder().query(query)
         .build(), new TestHttpResponse(orders2)));

      query = (TestQuery) query.clone();
      query.setUrl("http://host/orders/o1/supplier");
      requestResponses.add(new RequestResponse(RestRequest.builder().query(query)
         .build(), new TestHttpResponse(supplier1)));

      query = (TestQuery) query.clone();
      query.setUrl("http://host/orders/o2/supplier");
      requestResponses.add(new RequestResponse(RestRequest.builder().query(query)
         .build(), new TestHttpResponse(supplier2)));

      query = (TestQuery) query.clone();
      query.setUrl("http://host/orders/o3/supplier");
      requestResponses.add(new RequestResponse(RestRequest.builder().query(query)
         .build(), new TestHttpResponse(supplier3)));

      return requestResponses;
   }

   private TestQuery createTestQuery() {
      final TestQuery query = new TestQuery();

      final RestXMLDataSource dataSource = new RestXMLDataSource();
      dataSource.setURL("http://host");

      query.setDataSource(dataSource);
      query.setEndpoint("Customers");
      query.setExpandTop(false);

      final JsonLookupQuery orderSupplier = new JsonLookupQuery();
      orderSupplier.setLookupEndpoint(query.getEndpointMap().get("Customer Orders").getLookups().get(0));

      final JsonLookupQuery customerOrders = new JsonLookupQuery();
      customerOrders.setLookupEndpoint(query.getEndpointMap().get("Customers").getLookups().get(0));
      customerOrders.setLookupQueries(Collections.singletonList(orderSupplier));
      customerOrders.setExpandArrays(true);

      query.setLookupQueries(Collections.singletonList(customerOrders));

      return query;
   }
}
