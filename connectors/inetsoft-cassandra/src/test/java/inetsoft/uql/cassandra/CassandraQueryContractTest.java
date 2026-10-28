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
package inetsoft.uql.cassandra;

import com.fasterxml.jackson.databind.JsonNode;
import inetsoft.uql.tabular.TabularQueryParamsSchemaBuilder;
import inetsoft.uql.tabular.TabularQuerySchema;
import inetsoft.uql.tabular.TabularSchemaExtractor;
import inetsoft.uql.util.Config;
import inetsoft.util.ConfigurationContext;
import org.junit.jupiter.api.*;
import org.springframework.context.ApplicationContext;

import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #79592: the contract published for a Cassandra query must say the query text is CQL, not
 * just "Enter Query.", so a caller does not have to guess the dialect.
 */
@Tag("core")
class CassandraQueryContractTest {
   private static ConfigurationContext previous;

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

   @Test
   void queryStringDescriptionNamesCql() {
      JsonNode queryString = build().get("properties").get("queryString");

      assertNotNull(queryString);
      String description = queryString.get("description").asText();
      assertTrue(Pattern.compile("\\bCQL\\b").matcher(description).find(),
                 "queryString description must name CQL: " + description);
      assertTrue(description.contains("''"), "must state the quote-doubling rule: " + description);
   }

   @Test
   void queryStringStaysTheOnlyRequiredProperty() {
      JsonNode required = build().get("required");

      assertEquals(1, required.size());
      assertEquals("queryString", required.get(0).asText());
   }

   private static JsonNode build() {
      CassandraQuery query = new CassandraQuery();
      TabularQuerySchema schema = new TabularSchemaExtractor().extract(query, "Cassandra");
      return TabularQueryParamsSchemaBuilder.build(query, schema, false);
   }
}
