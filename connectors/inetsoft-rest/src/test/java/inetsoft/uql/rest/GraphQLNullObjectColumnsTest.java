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
package inetsoft.uql.rest;

import inetsoft.test.*;
import inetsoft.uql.XTableNode;
import inetsoft.uql.rest.datasource.graphql.GraphQLQuery;
import inetsoft.uql.util.BaseJsonTable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Bug #82230: the table a GraphQL query's runner builds has the leaf columns the selection set
 * names, also when a nested object (monday.com folder, out_of_office, department) is null on every
 * returned row; objects that are present build the same columns as before.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, CredentialTestConfig.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
class GraphQLNullObjectColumnsTest {
   private static final String USERS =
      "query { users(limit: 10) { id name out_of_office { active type } department { id } } }";

   @Test
   void objectsNullOnEveryRowBuildTheSelectedLeafColumns() {
      Map<String, Object> user = new LinkedHashMap<>();
      user.put("id", "1");
      user.put("name", "A");
      user.put("out_of_office", null);
      user.put("department", null);

      assertEquals(List.of("id", "name", "out_of_office.active", "out_of_office.type",
                           "department.id"),
                   columns(build(USERS, "$.data.users[*]", List.of(user))));
   }

   @Test
   void presentObjectsBuildTheSameColumns() {
      Map<String, Object> user = new LinkedHashMap<>();
      user.put("id", "1");
      user.put("name", "A");
      user.put("out_of_office", Map.of("active", true, "type", "vacation"));
      user.put("department", Map.of("id", "d1"));

      assertEquals(Set.of("id", "name", "out_of_office.active", "out_of_office.type",
                          "department.id"),
                   Set.copyOf(columns(build(USERS, "$.data.users[*]", List.of(user)))));
   }

   @Test
   void nonResolvingJsonPathKeepsTheDataDrivenColumns() {
      Map<String, Object> user = new LinkedHashMap<>();
      user.put("id", "1");
      user.put("department", null);

      assertEquals(List.of("id", "department"),
                   columns(build(USERS, "$.data.users[?(@.id)]", List.of(user))));
   }

   private static final String ITEMS = "{ boards { id items { id } } }";

   private static List<Object> nullItemsRows() {
      Map<String, Object> board = new LinkedHashMap<>();
      board.put("id", "1");
      board.put("items", null);
      return List.of(board);
   }

   @Test
   void nullListOfObjectsInExpandedModeBuildsLeaves() {
      assertEquals(List.of("id", "items.id"),
                   columns(build(ITEMS, "$.data.boards[*]", nullItemsRows(), q -> {})));
   }

   @Test
   void nonNullListOfObjectsStaysTheSingleColumn() {
      Map<String, Object> board = new LinkedHashMap<>();
      board.put("id", "1");
      board.put("items", List.of(Map.of("id", "i1")));

      assertEquals(List.of("id", "items"),
                   columns(build(ITEMS, "$.data.boards[*]", List.of(board), q -> {})));
   }

   @Test
   void notExpandedDeclaresNothing() {
      assertEquals(List.of("id", "items"),
                   columns(build(ITEMS, "$.data.boards[*]", nullItemsRows(),
                                 q -> q.setExpanded(false))));
   }

   private static BaseJsonTable build(String queryString, String jsonPath, List<Object> rows) {
      return build(queryString, jsonPath, rows, q -> {});
   }

   private static BaseJsonTable build(String queryString, String jsonPath, List<Object> rows,
                                      java.util.function.Consumer<GraphQLQuery> mode)
   {
      GraphQLQuery query = new GraphQLQuery();
      mode.accept(query);
      query.setQueryString(queryString);
      query.setJsonPath(jsonPath);

      AbstractQueryRunner<GraphQLQuery> runner = new AbstractQueryRunner<>(query) {
         @Override
         public XTableNode runStream() {
            return null;
         }

         @Override
         public boolean isCancelled() {
            return false;
         }

         @Override
         public boolean isLiveMode() {
            return false;
         }

         @Override
         public long getTouchTimestamp() {
            return 0;
         }
      };

      BaseJsonTable table = runner.getTable();
      table.load(rows);
      return table;
   }

   private static List<String> columns(BaseJsonTable table) {
      List<String> names = new ArrayList<>();

      for(int i = 0; i < table.getColCount(); i++) {
         names.add(table.getName(i));
      }

      return names;
   }
}
