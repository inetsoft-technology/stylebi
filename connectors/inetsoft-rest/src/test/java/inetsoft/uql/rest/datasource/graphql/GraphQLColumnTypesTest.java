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
import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.test.*;
import inetsoft.uql.util.JsonTable;
import inetsoft.util.ConfigurationContext;
import inetsoft.util.credential.CredentialService;
import inetsoft.util.credential.CredentialType;
import inetsoft.util.credential.LocalPasswordCredential;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.InputStream;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #82374: schema types for columns that are null on every row.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  GraphQLColumnTypesTest.TestConfig.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
class GraphQLColumnTypesTest {
   private final AtomicInteger calls = new AtomicInteger();
   private GraphQLDataSource dataSource;

   @AfterAll
   static void resetContext() {
      ConfigurationContext.getContext().setApplicationContext(null);
   }

   @BeforeEach
   void setUp() throws Exception {
      JsonNode schema;

      try(InputStream in = getClass().getResourceAsStream("bookstore-schema.json")) {
         assertNotNull(in);
         schema = new ObjectMapper().readTree(in).path("data").path("__schema");
      }

      GraphQLColumnTypes.clearCache();
      GraphQLColumnTypes.introspector = ds -> {
         calls.incrementAndGet();
         return schema;
      };
      dataSource = new GraphQLDataSource();
      dataSource.setName("GraphQLColumnTypesTest");
      dataSource.setURL("http://localhost/graphql");
   }

   @AfterEach
   void tearDown() {
      GraphQLColumnTypes.clearCache();
      GraphQLColumnTypes.introspector = GraphQLIntrospectionClient::introspect;
      GraphQLColumnTypes.ttlMillis = 60_000L;
   }

   @Test
   void resolvesSchemaTypesLikeDescribeDataset() {
      Map<String, String> types = GraphQLColumnTypes.resolve(dataSource, "products");

      assertEquals("double", types.get("price"));
      assertEquals("string", types.get("name"));
   }

   @Test
   void unknownFieldGivesEmptyMapWithoutThrowing() {
      assertTrue(GraphQLColumnTypes.resolve(dataSource, "nope").isEmpty());
   }

   @Test
   void introspectionFailureGivesEmptyMapWithoutThrowing() {
      GraphQLColumnTypes.introspector = ds -> {
         calls.incrementAndGet();
         throw new IllegalStateException("Introspection is disabled");
      };

      assertTrue(GraphQLColumnTypes.resolve(dataSource, "products").isEmpty());
   }

   @Test
   void successAndFailureAreCachedWithinTheTtl() {
      GraphQLColumnTypes.resolve(dataSource, "products");
      GraphQLColumnTypes.resolve(dataSource, "products");
      assertEquals(1, calls.get());

      GraphQLColumnTypes.resolve(dataSource, "nope");
      GraphQLColumnTypes.resolve(dataSource, "nope");
      assertEquals(2, calls.get());
   }

   @Test
   void expiredEntryIsIntrospectedAgain() {
      GraphQLColumnTypes.ttlMillis = -1;
      GraphQLColumnTypes.resolve(dataSource, "products");
      GraphQLColumnTypes.resolve(dataSource, "products");
      assertEquals(2, calls.get());
   }

   @Test
   void queryHookFeedsAnAllNullColumn() {
      GraphQLQuery query = new GraphQLQuery();
      query.setDataSource(dataSource);
      query.setQueryString("{ products { id price } }");
      query.setJsonPath("$.data.products[*]");

      JsonTable table = new JsonTable();
      table.setDeclaredColumnTypes(query.declaredColumnTypes());
      table.load(new ArrayList<>(List.of(row("id", "1", "price", null))));

      assertEquals("double", table.getColumnType("price"));
   }

   private static Map<String, Object> row(Object... kv) {
      Map<String, Object> map = new LinkedHashMap<>();

      for(int i = 0; i < kv.length; i += 2) {
         map.put((String) kv[i], kv[i + 1]);
      }

      return map;
   }

   @Configuration
   static class TestConfig {
      @Bean
      public CredentialService credentialService() {
         CredentialService credentialService = mock(CredentialService.class);
         when(credentialService.createCredential(CredentialType.PASSWORD_OAUTH2_WITH_FLAGS, false))
            .thenAnswer(invocation -> mock(LocalPasswordCredential.class));
         return credentialService;
      }
   }
}
