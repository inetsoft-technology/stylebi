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
package inetsoft.uql.rest.datasource.twitter;

import inetsoft.uql.VariableTable;
import inetsoft.uql.XTableNode;
import inetsoft.uql.rest.json.EndpointJsonRuntime;
import inetsoft.uql.tabular.TabularQuery;

import java.util.Map;

/**
 * Twitter needs to customize the data source based on the query endpoint
 */
public class TwitterRuntime extends EndpointJsonRuntime {
   @Override
   public XTableNode runQuery(TabularQuery query, VariableTable params) {
      final TwitterQuery twitterQuery = (TwitterQuery) query;
      final TwitterDataSource dataSource = ((TwitterDataSource) query.getDataSource());
      final Map<String, TwitterEndpoint> endpoints = twitterQuery.getEndpointMap();

      final String endpoint = twitterQuery.getEndpoint();
      final TwitterEndpoint twitterEndpoint = endpoints.get(endpoint);
      twitterQuery.updatePagination(twitterEndpoint);
      return super.runQuery(query, params);
   }
}
