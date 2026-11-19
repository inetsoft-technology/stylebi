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
package inetsoft.uql.rest;

import inetsoft.uql.rest.datasource.graphql.GraphQLQuery;
import inetsoft.uql.rest.json.JsonTransformer;
import inetsoft.uql.rest.json.RestJsonQuery;
import inetsoft.uql.rest.json.lookup.LookupService;
import inetsoft.test.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #81771: a JSON/GraphQL query whose response parsed but matched no rows must report the run
 * as completed (TabularQuery.isRunCompleted), so the add_table refusal says "zero rows" instead
 * of blaming datasource credentials. A failed request must not read as completed.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, CredentialTestConfig.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
public class RestJsonQueryRunnerRunCompletedTest {
   @Test
   void zeroRowResponseMarksTheRunCompleted() throws Exception {
      final GraphQLQuery query = new GraphQLQuery();
      final Map<String, Object> response =
         Map.of("data", Map.of("orders", Map.of("edges", List.of())));

      new RestJsonQueryRunner(query, q -> new ListStrategy(List.of(response)),
                              new LookupService(), new JsonTransformer()).runStream();

      assertTrue(query.isRunCompleted(), "a response that was parsed but held no rows is a " +
         "completed run, so the refusal can say zero rows instead of blaming credentials");
   }

   @Test
   void failedRequestDoesNotMarkTheRunCompleted() throws Exception {
      final RestJsonQuery query = new RestJsonQuery();

      new RestJsonQueryRunner(query, q -> new ListStrategy(null),
                              new LookupService(), new JsonTransformer()).runStream();

      assertFalse(query.isRunCompleted());
   }

   @Test
   void noResponseDoesNotMarkTheRunCompleted() throws Exception {
      final RestJsonQuery query = new RestJsonQuery();

      new RestJsonQueryRunner(query, q -> new ListStrategy(List.of()),
                              new LookupService(), new JsonTransformer()).runStream();

      assertFalse(query.isRunCompleted());
   }

   /** Hands back the given responses; a null list throws on first read, like a failed request. */
   private static final class ListStrategy implements RestDataIteratorStrategy<Object> {
      ListStrategy(List<Object> responses) {
         this.responses = responses;
         this.iterator = responses == null ? null : responses.iterator();
      }

      @Override
      public boolean hasNext() {
         return responses == null || iterator.hasNext();
      }

      @Override
      public Object next() {
         if(responses == null) {
            throw new IllegalStateException("HTTP 401");
         }

         return iterator.next();
      }

      @Override
      public void close() {
      }

      private final List<Object> responses;
      private final Iterator<Object> iterator;
   }
}
