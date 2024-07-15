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

import inetsoft.test.TestEndpoint;
import inetsoft.uql.rest.json.EndpointJsonQuery;
import inetsoft.uql.rest.xml.EndpointXMLQuery;

import java.util.Map;

class TestQuery extends EndpointXMLQuery<TestEndpoint> {
   public TestQuery() {
      super("Test");
   }

   @Override
   public String getURL() {
      return url == null ? super.getURL() : url;
   }

   public void setUrl(String url) {
      this.url = url;
   }

   @Override
   public String getSuffix() {
      return url == null ? super.getSuffix() : "";
   }

   @Override
   public Map<String, TestEndpoint> getEndpointMap() {
      return Singleton.INSTANCE.endpoints;
   }

   private static final class TestEndpoints extends EndpointJsonQuery.Endpoints<TestEndpoint> {
   }

   enum Singleton {
      INSTANCE;
      Map<String, TestEndpoint> endpoints = EndpointJsonQuery.Endpoints.load(
         TestQuery.TestEndpoints.class);
   }

   private String url;
}
