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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.uql.rest.datasource.shopify.ShopifyQuery;
import inetsoft.uql.tabular.*;
import inetsoft.uql.util.Config;
import inetsoft.util.ConfigurationContext;
import inetsoft.web.wiz.service.TabularQueryContractSupport;
import org.junit.jupiter.api.*;
import org.springframework.context.ApplicationContext;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Runs the real {@code applyQueryContract} on the real {@link GraphQLQuery} (the monday.com
 * connector's query class), so dropping {@link DerivedRequestBodyQuery} from it goes red.
 */
@Tag("core")
class GraphQLQueryContractTest {
   @BeforeAll
   static void installContext() {
      previous = ConfigurationContext.getContext();
      Config config = mock(Config.class);
      when(config.getResourceBundle(org.mockito.ArgumentMatchers.any())).thenReturn(null);

      ApplicationContext context = mock(ApplicationContext.class);
      when(context.getBean(Config.class)).thenReturn(config);
      ConfigurationContext.getContext().setApplicationContext(context);
   }

   @AfterAll
   static void clearContext() {
      if(previous != null) {
         previous.setApplicationContext(null);
      }
   }

   private static void apply(TabularQuery query, Object... kv) throws Exception {
      Map<String, Object> params = new LinkedHashMap<>();

      for(int i = 0; i < kv.length; i += 2) {
         params.put((String) kv[i], kv[i + 1]);
      }

      TabularQuerySchema schema = new TabularSchemaExtractor().extract(query, query.getType());
      Map<String, PropertyMeta> pmap = TabularUtil.getPropertyMap(query.getClass());
      TabularQueryContractSupport.applyQueryContract(query, pmap, schema, params, "monday.com");
   }

   @Test
   void graphQlQueryIsMarked() {
      assertInstanceOf(DerivedRequestBodyQuery.class, new GraphQLQuery());
   }

   @Test
   void postIsAcceptedAndTheBodyCarriesQueryAndVariables() throws Exception {
      GraphQLQuery query = new GraphQLQuery();

      apply(query, "queryString", "{ boards { id } }", "variables", "{\"a\":1}",
            "requestType", "POST");

      assertEquals("POST", query.getRequestType());
      JsonNode body = new ObjectMapper().readTree(query.getRequestBody());
      assertEquals("{ boards { id } }", body.get("query").asText());
      assertEquals(1, body.get("variables").get("a").asInt());
   }

   @Test
   void anOmittedOrBlankRequestTypeDefaultsToPost() throws Exception {
      GraphQLQuery omitted = new GraphQLQuery();
      apply(omitted, "queryString", "{ me { id } }");
      assertEquals("POST", omitted.getRequestType());

      GraphQLQuery blank = new GraphQLQuery();
      apply(blank, "queryString", "{ me { id } }", "requestType", "");
      assertEquals("POST", blank.getRequestType());
   }

   @Test
   void anExplicitGetStaysGet() throws Exception {
      GraphQLQuery query = new GraphQLQuery();

      apply(query, "queryString", "{ me { id } }", "requestType", "GET");

      assertEquals("GET", query.getRequestType());
   }

   @Test
   void requestBodyIsRefusedByName() {
      GraphQLQuery query = new GraphQLQuery();

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> apply(query, "queryString", "{ me { id } }", "requestType", "POST",
                     "requestBody", "{}"));
      assertTrue(ex.getMessage().contains("requestBody"), ex.getMessage());
   }

   @Test
   void shopifyStillSendsPost() throws Exception {
      ShopifyQuery query = new ShopifyQuery();

      apply(query, "queryString", "{ shop { name } }");

      assertEquals("POST", query.getRequestType());
   }

   private static ConfigurationContext previous;
}
