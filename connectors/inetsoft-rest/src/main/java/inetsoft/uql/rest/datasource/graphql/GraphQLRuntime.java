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
      final AbstractGraphQLDataSource dataSource = (AbstractGraphQLDataSource) query.getDataSource();
      final boolean postRequest = query.isPostRequest();
      addParams(query, dataSource, postRequest);
      return super.runQuery(tabularQuery, params);
   }

   void addParams(GraphQLQuery query, AbstractGraphQLDataSource dataSource, boolean postRequest) {
      final List<HttpParameter> httpParams = new ArrayList<>();

      // preserve the user-configured query HTTP parameters (e.g. an Authorization
      // header); otherwise they are overwritten below and lost during execution
      final HttpParameter[] existing = dataSource.getQueryHttpParameters();

      if(existing != null) {
         httpParams.addAll(Arrays.asList(existing));
      }

      for(HttpParameter param : dataSource.getRequestParameters()) {
         replaceParam(httpParams, param);
      }

      if(!postRequest) {
         final String variables = query.getVariables();
         replaceParam(httpParams, HttpParameter.builder()
                                     .type(HttpParameter.ParameterType.QUERY)
                                     .name(GraphQLDataSource.VARIABLE_KEY)
                                     .value(variables)
                                     .build());
         replaceParam(httpParams, HttpParameter.builder()
                                     .type(HttpParameter.ParameterType.QUERY)
                                     .name("query")
                                     .value(query.getQueryString())
                                     .build());
      }

      dataSource.setQueryHttpParameters(httpParams.toArray(new HttpParameter[0]));
   }

   /**
    * Adds a generated parameter, replacing any parameter of the same type and name. The query
    * HTTP parameters are saved with the data source, so they may already hold an earlier copy.
    */
   private static void replaceParam(List<HttpParameter> params, HttpParameter param) {
      params.removeIf(p -> p != null && isSameParam(p, param));
      params.add(param);
   }

   private static boolean isSameParam(HttpParameter a, HttpParameter b) {
      if(a.getType() != b.getType() || a.getName() == null || b.getName() == null) {
         return false;
      }

      // header names are case-insensitive
      return a.getType() == HttpParameter.ParameterType.HEADER ?
         a.getName().equalsIgnoreCase(b.getName()) : a.getName().equals(b.getName());
   }
}
