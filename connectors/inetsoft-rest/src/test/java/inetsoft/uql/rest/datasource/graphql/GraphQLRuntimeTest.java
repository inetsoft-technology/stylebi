/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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

import inetsoft.test.*;
import inetsoft.uql.rest.datasource.shopify.ShopifyDataSource;
import inetsoft.uql.tabular.HttpParameter;
import inetsoft.uql.tabular.HttpParameter.ParameterType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The parameters that GraphQLRuntime.addParams generates are written into the query HTTP
 * parameters, which are saved with the data source. When a query-time copy is saved, the next
 * query starts from the generated parameters of the last one, so they must replace, not be added
 * next to, the saved copies.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, CredentialTestConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
class GraphQLRuntimeTest {
   @Test
   void generatedHeaderIsNotDuplicatedWhenQueryCopyIsSaved() {
      ShopifyDataSource stored = createShopifyDataSource("token");

      for(int i = 0; i < 3; i++) {
         ShopifyDataSource runtime = (ShopifyDataSource) stored.clone();
         new GraphQLRuntime().addParams(new GraphQLQuery(), runtime, true);
         // the query-time copy is saved, so the next query starts from it
         stored = runtime;
      }

      List<HttpParameter> tokens = find(stored, ParameterType.HEADER, TOKEN_HEADER);
      assertEquals(1, tokens.size());
      assertEquals("token", tokens.get(0).getValue());
   }

   @Test
   void savedCopiesOfGeneratedHeaderAreReplaced() {
      ShopifyDataSource dataSource = createShopifyDataSource("new-token");
      dataSource.setQueryHttpParameters(new HttpParameter[] {
         header(TOKEN_HEADER, "old-token"),
         header(TOKEN_HEADER, "old-token"),
         header(TOKEN_HEADER.toLowerCase(), "old-token")
      });

      new GraphQLRuntime().addParams(new GraphQLQuery(), dataSource, true);

      HttpParameter[] params = dataSource.getQueryHttpParameters();
      assertEquals(1, params.length);
      assertEquals(TOKEN_HEADER, params[0].getName());
      assertEquals("new-token", params[0].getValue());
   }

   @Test
   void userConfiguredParametersArePreserved() {
      GraphQLDataSource dataSource = new GraphQLDataSource();
      HttpParameter authorization = header("Authorization", "Bearer abc");
      HttpParameter custom = HttpParameter.builder()
         .type(ParameterType.QUERY).name("tenant").value("t1").build();
      dataSource.setQueryHttpParameters(new HttpParameter[] { authorization, custom });

      new GraphQLRuntime().addParams(new GraphQLQuery(), dataSource, true);

      assertArrayEquals(new HttpParameter[] { authorization, custom },
                        dataSource.getQueryHttpParameters());
   }

   @Test
   void getQueryParametersDoNotAccumulate() {
      GraphQLDataSource stored = new GraphQLDataSource();

      for(String queryString : new String[] { "{ a }", "{ b }", "{ c }" }) {
         GraphQLDataSource runtime = (GraphQLDataSource) stored.clone();
         GraphQLQuery query = new GraphQLQuery();
         query.setQueryString(queryString);
         query.setVariables("{\"q\": \"" + queryString + "\"}");
         new GraphQLRuntime().addParams(query, runtime, false);
         stored = runtime;
      }

      List<HttpParameter> queries = find(stored, ParameterType.QUERY, "query");
      List<HttpParameter> variables =
         find(stored, ParameterType.QUERY, GraphQLDataSource.VARIABLE_KEY);
      assertEquals(1, queries.size());
      assertEquals("{ c }", queries.get(0).getValue());
      assertEquals(1, variables.size());
      assertEquals("{\"q\": \"{ c }\"}", variables.get(0).getValue());
   }

   private static ShopifyDataSource createShopifyDataSource(String accessToken) {
      ShopifyDataSource dataSource = new ShopifyDataSource();
      dataSource.setName("shopify");
      dataSource.setAccessToken(accessToken);
      return dataSource;
   }

   private static HttpParameter header(String name, String value) {
      return HttpParameter.builder().type(ParameterType.HEADER).name(name).value(value).build();
   }

   private static List<HttpParameter> find(AbstractGraphQLDataSource<?> dataSource,
                                           ParameterType type, String name)
   {
      return Arrays.stream(dataSource.getQueryHttpParameters())
         .filter(p -> p.getType() == type && name.equalsIgnoreCase(p.getName()))
         .toList();
   }

   private static final String TOKEN_HEADER = "X-Shopify-Access-Token";
}
