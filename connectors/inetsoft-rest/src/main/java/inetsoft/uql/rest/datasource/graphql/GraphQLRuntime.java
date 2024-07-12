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
package inetsoft.uql.rest.datasource.graphql;

import inetsoft.uql.VariableTable;
import inetsoft.uql.XTableNode;
import inetsoft.uql.rest.json.RestJsonRuntime;
import inetsoft.uql.tabular.HttpParameter;
import inetsoft.uql.tabular.TabularQuery;

import java.util.*;

public class GraphQLRuntime extends RestJsonRuntime {
   @Override
   public XTableNode runQuery(TabularQuery tabularQuery, VariableTable params) {
      final GraphQLQuery query = (GraphQLQuery) tabularQuery;
      final GraphQLDataSource dataSource = (GraphQLDataSource) query.getDataSource();
      final boolean postRequest = query.isPostRequest();
      addParams(query, dataSource, postRequest);
      return super.runQuery(tabularQuery, params);
   }

   private void addParams(GraphQLQuery query, GraphQLDataSource dataSource, boolean postRequest) {
      final Set<HttpParameter> httpParams =
         new HashSet<>(Arrays.asList(dataSource.getRequestParameters()));

      if(!postRequest) {
         final String variables = query.getVariables();
         httpParams.add(HttpParameter.builder()
                           .type(HttpParameter.ParameterType.QUERY)
                           .name(GraphQLDataSource.VARIABLE_KEY)
                           .value(variables)
                           .build());
         httpParams.add(HttpParameter.builder()
                           .type(HttpParameter.ParameterType.QUERY)
                           .name("query")
                           .value(query.getQueryString())
                           .build());
      }

      dataSource.setQueryHttpParameters(httpParams.toArray(new HttpParameter[0]));
   }
}
