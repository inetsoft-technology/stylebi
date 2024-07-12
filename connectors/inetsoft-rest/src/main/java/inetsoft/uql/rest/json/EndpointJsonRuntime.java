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
package inetsoft.uql.rest.json;

import inetsoft.uql.VariableTable;
import inetsoft.uql.rest.*;
import inetsoft.uql.rest.json.lookup.LookupService;
import inetsoft.uql.rest.pagination.HttpResponseParameterParser;
import inetsoft.uql.tabular.TabularDataSource;

public class EndpointJsonRuntime extends RestJsonRuntime {
   @Override
   public void testDataSource(TabularDataSource ds, VariableTable params) throws Exception {
      if(!(ds instanceof EndpointJsonDataSource)) {
         throw new IllegalArgumentException(
            "Only subclasses of EndpointJsonDataSource are supported");
      }

      EndpointJsonDataSource dataSource = (EndpointJsonDataSource) ds;

      if(dataSource.getTestSuffix() == null) {
         super.testDataSource(ds, params);
         return;
      }

      RestJsonQuery query = new RestJsonQuery(dataSource.getType());
      query.setDataSource(dataSource);
      query.setSuffix(dataSource.getTestSuffix());

      RestQuotaManager.withQuota(dataSource, () -> testDataSource(dataSource, query));
   }

   @Override
   protected QueryRunner getQueryRunner(AbstractRestQuery q) {
      final EndpointJsonQuery<?> query = (EndpointJsonQuery<?>) q;
      final JsonTransformer transformer = new JsonTransformer();
      final JsonRestDataIteratorStrategyFactory factory = new JsonRestDataIteratorStrategyFactory(
         transformer, new HttpResponseParameterParser(transformer));
      final LookupService lookupService = new LookupService();
      return new EndpointJsonQueryRunner(query, factory, lookupService, transformer);
   }

   private void testDataSource(EndpointJsonDataSource dataSource, RestJsonQuery query)
      throws Exception
   {
      try(HttpHandler handler = new HttpHandler();
          HttpResponse response = handler.executeRequest(RestRequest.fromQuery(query)))
      {
         dataSource.validateTestResponse(response);
      }
   }
}
