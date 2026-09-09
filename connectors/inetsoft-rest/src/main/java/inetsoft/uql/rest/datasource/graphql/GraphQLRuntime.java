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

import com.fasterxml.jackson.databind.JsonNode;
import inetsoft.uql.VariableTable;
import inetsoft.uql.XTableNode;
import inetsoft.uql.rest.json.RestJsonRuntime;
import inetsoft.uql.tabular.HttpParameter;
import inetsoft.uql.tabular.TabularCatalog;
import inetsoft.uql.tabular.TabularCatalogProvider;
import inetsoft.uql.tabular.TabularDataSource;
import inetsoft.uql.tabular.TabularDatasetSchema;
import inetsoft.uql.tabular.TabularQuery;

import java.util.*;

/**
 * Shared runtime for {@code graphql}, {@code shopify}, and {@code monday.com} -- all three declare
 * this class as their {@code TabularService.getRuntimeClass()}. {@link TabularCatalogProvider}'s
 * two methods below are pure delegation into {@link GraphQLIntrospectionClient} (I/O) and {@link
 * GraphQLCatalog} (pure schema-walking); see those classes' javadoc for the design, including why
 * the catalog path never reuses {@link #addParams}/{@link #runQuery}.
 */
public class GraphQLRuntime extends RestJsonRuntime implements TabularCatalogProvider {
   @Override
   public TabularCatalog listDatasets(TabularDataSource<?> dataSource) throws Exception {
      JsonNode schema =
         GraphQLIntrospectionClient.introspect((AbstractGraphQLDataSource<?>) dataSource);
      return GraphQLCatalog.listDatasets(schema);
   }

   @Override
   public TabularDatasetSchema describeDataset(TabularDataSource<?> dataSource, String datasetId)
      throws Exception
   {
      JsonNode schema =
         GraphQLIntrospectionClient.introspect((AbstractGraphQLDataSource<?>) dataSource);
      return GraphQLCatalog.describeDataset(schema, datasetId);
   }

   @Override
   public XTableNode runQuery(TabularQuery tabularQuery, VariableTable params) {
      final GraphQLQuery query = (GraphQLQuery) tabularQuery;
      final AbstractGraphQLDataSource dataSource = (AbstractGraphQLDataSource) query.getDataSource();
      final boolean postRequest = query.isPostRequest();
      addParams(query, dataSource, postRequest);
      return super.runQuery(tabularQuery, params);
   }

   private void addParams(GraphQLQuery query, AbstractGraphQLDataSource dataSource, boolean postRequest) {
      final Set<HttpParameter> httpParams = new LinkedHashSet<>();

      // preserve the user-configured query HTTP parameters (e.g. an Authorization
      // header); otherwise they are overwritten below and lost during execution
      final HttpParameter[] existing = dataSource.getQueryHttpParameters();

      if(existing != null) {
         httpParams.addAll(Arrays.asList(existing));
      }

      httpParams.addAll(Arrays.asList(dataSource.getRequestParameters()));

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
