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

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bug #82230: the selection-set reader that tells the table which leaf columns an object has.
 */
class GraphQLSelectionLeavesTest {
   private static final String BOARDS = "$.data.boards[*]";

   @Test
   void collectsRecursiveLeavesOfEachNestedObject() {
      Map<String, List<String>> d = GraphQLSelectionLeaves.declare(
         "query { boards(limit: 5) { id name folder { id name } owner { id team { id } } } }",
         BOARDS);

      assertEquals(List.of("folder", "owner", "owner.team"), List.copyOf(d.keySet()));
      assertEquals(List.of("folder.id", "folder.name"), d.get("folder"));
      assertEquals(List.of("owner.id", "owner.team.id"), d.get("owner"));
      assertEquals(List.of("owner.team.id"), d.get("owner.team"));
   }

   @Test
   void aliasIsTheColumnKey() {
      Map<String, List<String>> d = GraphQLSelectionLeaves.declare(
         "{ boards { dir: folder { id } } }", BOARDS);

      assertEquals(Map.of("dir", List.of("dir.id")), d);
   }

   @Test
   void argumentsWithStringsAndBracesDoNotUnbalanceTheQuery() {
      Map<String, List<String>> d = GraphQLSelectionLeaves.declare(
         "query Q($x: Int = 1) {\n boards(where: {name: \"a } { b\"}, ids: [1,2]) # note }\n" +
         " { id folder(arg: \"\"\"}\"\"\") { id } } }", BOARDS);

      assertEquals(Map.of("folder", List.of("folder.id")), d);
   }

   @Test
   void listOfObjectsIsADeclaredObjectToo() {
      Map<String, List<String>> d = GraphQLSelectionLeaves.declare(
         "{ boards { items { id } } }", BOARDS);

      assertEquals(Map.of("items", List.of("items.id")), d);
   }

   @Test
   void rowRootFollowsTheJsonPath() {
      String q = "{ boards(ids: 1) { id items_page { cursor items { id group { id } } } } }";

      assertEquals(Map.of("group", List.of("group.id")),
                   GraphQLSelectionLeaves.declare(q, "$.data.boards[0].items_page.items[*]"));
   }

   @Test
   void unresolvedOrAmbiguousJsonPathDeclaresNothing() {
      String q = "{ boards { id folder { id } } }";

      assertTrue(GraphQLSelectionLeaves.declare(q, "$").isEmpty());
      assertTrue(GraphQLSelectionLeaves.declare(q, "$.data").isEmpty());
      assertTrue(GraphQLSelectionLeaves.declare(q, "$.data.nope[*]").isEmpty());
      assertTrue(GraphQLSelectionLeaves.declare(q, "$.data.boards[?(@.id)]").isEmpty());
      assertTrue(GraphQLSelectionLeaves.declare(q, "$..boards").isEmpty());
      assertTrue(GraphQLSelectionLeaves.declare(q, "$.data.boards.id").isEmpty());
      assertTrue(GraphQLSelectionLeaves.declare(q, null).isEmpty());
   }

   @Test
   void fragmentsDirectivesAndOtherOperationsBailOut() {
      assertTrue(GraphQLSelectionLeaves.declare(
         "{ boards { ...F } } fragment F on Board { folder { id } }", BOARDS).isEmpty());
      assertTrue(GraphQLSelectionLeaves.declare(
         "{ boards { ... on Board { folder { id } } } }", BOARDS).isEmpty());
      assertTrue(GraphQLSelectionLeaves.declare(
         "{ boards { folder @include(if: true) { id } } }", BOARDS).isEmpty());
      assertTrue(GraphQLSelectionLeaves.declare(
         "mutation { boards { folder { id } } }", BOARDS).isEmpty());
   }

   @Test
   void duplicateResponseKeysMergeDeeply() {
      Map<String, List<String>> d = GraphQLSelectionLeaves.declare(
         "{ boards { a { b { x } } a { b { y } c } } }", BOARDS);

      assertEquals(List.of("a.b.x", "a.b.y", "a.c"), d.get("a"));
   }

   @Test
   void spreadWithoutWhitespaceBailsOut() {
      assertTrue(GraphQLSelectionLeaves.declare(
         "{ boards { id...Frag folder { id } } } fragment Frag on B { x }", BOARDS).isEmpty());
   }

   @Test
   void unbalancedOrEmptyQueryDeclaresNothing() {
      assertTrue(GraphQLSelectionLeaves.declare("{ boards { folder { id } }", BOARDS).isEmpty());
      assertTrue(GraphQLSelectionLeaves.declare("{ boards { folder { id } } } }", BOARDS).isEmpty());
      assertTrue(GraphQLSelectionLeaves.declare("{ boards(a: \"x) { folder { id } } }", BOARDS)
                    .isEmpty());
      assertTrue(GraphQLSelectionLeaves.declare("", BOARDS).isEmpty());
      assertTrue(GraphQLSelectionLeaves.declare(null, BOARDS).isEmpty());
   }
}
