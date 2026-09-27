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
package inetsoft.uql.mongodb;

import inetsoft.uql.VariableTable;
import inetsoft.uql.tabular.TabularUtil;
import org.bson.Document;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Bug #76864: a variable substituted into the MongoDB query text through
 * {@link TabularUtil#replaceVariables(Object, VariableTable)} must parse (with the
 * real bson parser used by {@code MongoRuntime}) to exactly the input value, with
 * no value-supplied keys, for unquoted, single-quoted and double-quoted placeholders.
 */
class MongoQueryVariableEscapeTest {
   static Stream<String> values() {
      return Stream.of(
         "plain", "O'Brien", "a\"b", "x\\", "C:\\Users\\bob", "', admin: '1",
         "x\\', admin: '1", "\", admin: \"1", "it's \"q\" \\ end\\", "");
   }

   @ParameterizedTest
   @MethodSource("values")
   void unquotedPlaceholder_roundTripsExactly(String value) {
      assertRoundTrip("{name: $(p), other: 1}", value, value);
   }

   @ParameterizedTest
   @MethodSource("values")
   void singleQuotedPlaceholder_roundTripsExactly(String value) {
      assertRoundTrip("{name: '$(p)', other: 1}", value, value);
   }

   @ParameterizedTest
   @MethodSource("values")
   void doubleQuotedPlaceholder_roundTripsExactly(String value) {
      assertRoundTrip("{name: \"$(p)\", other: 1}", value, value);
   }

   @ParameterizedTest
   @MethodSource("values")
   void arrayValueInScalarContext_roundTripsFirstElement(String value) {
      assertRoundTrip("{name: $(p), other: 1}", new String[] { value, "second" }, value);
   }

   private static void assertRoundTrip(String match, Object value, String expected) {
      MongoQuery query = new MongoQuery();
      query.setQueryString("{aggregate: 'c', pipeline: [{$match: " + match + "}], cursor: {}}");
      VariableTable vars = new VariableTable();
      vars.put("p", value);
      TabularUtil.replaceVariables(query, vars);

      Document doc = Document.parse(query.getQueryString());
      assertEquals(new HashSet<>(Arrays.asList("aggregate", "pipeline", "cursor")), doc.keySet());
      List<?> pipeline = (List<?>) doc.get("pipeline");
      assertEquals(1, pipeline.size());
      Document stage = (Document) pipeline.get(0);
      assertEquals(Collections.singleton("$match"), stage.keySet());
      Document matchDoc = (Document) stage.get("$match");
      assertEquals(new HashSet<>(Arrays.asList("name", "other")), matchDoc.keySet());
      assertEquals(expected, matchDoc.get("name"));
      assertEquals(1, matchDoc.get("other"));
   }
}
