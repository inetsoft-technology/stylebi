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
package inetsoft.uql.util;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Bug #77663, the column iterator didn't know the quoting rules of sql. A double quote in a
 * literal ended or opened a literal that swallowed the later columns or found columns in the
 * literal, a double quoted name was dropped, and the last name was never found.
 */
@Tag("core")
class ColumnIteratorTest {
   static Stream<Arguments> expressions() {
      return Stream.of(
         Arguments.of("T.A = 'it\"s' and T.B = 1", Set.of("T.A", "T.B")),
         Arguments.of("T.A = '' and T.B = 1", Set.of("T.A", "T.B")),
         Arguments.of("T.A = 'a''s x' and T.B = 1", Set.of("T.A", "T.B")),
         // a double quote and a split char in a literal
         Arguments.of("T.A = '\" ' and T.B = 1 and T.C = 2", Set.of("T.A", "T.B", "T.C")),
         // a name in a literal is not a column
         Arguments.of("T.A = 'say \"hi\" T.Z now' and T.B = 1", Set.of("T.A", "T.B")),
         Arguments.of("T.A = 'it''s T.Z' and T.B = 1", Set.of("T.A", "T.B")),
         // a double quoted name
         Arguments.of("\"T\".\"A\" = 1 and T.B = 1", Set.of("\"T\".\"A\"", "T.B")),
         Arguments.of("\"T\".\"A B\" = 1", Set.of("\"T\".\"A B\"")),
         // the last name
         Arguments.of("T.A = 'x' || T.B", Set.of("T.A", "T.B")),
         Arguments.of("T.A + T.B", Set.of("T.A", "T.B")),
         // a literal that is not closed
         Arguments.of("T.A = 'x and T.B = 1", Set.of("T.A")),
         // a script field
         Arguments.of("field['a b'] + T.B", Set.of("a b", "T.B")));
   }

   @ParameterizedTest(name = "{0}")
   @MethodSource("expressions")
   void columnsOfExpression(String exp, Set<String> expected) {
      assertEquals(expected, getColumns(exp, null));
   }

   /**
    * Bug #77697, the column iterator didn't know the quoting and comment rules of the
    * database: a ' in a bracket quoted name or a # comment opened a literal, a backslash
    * escaped quote ended a literal, a variable name was a column, and the name before a
    * literal was dropped.
    */
   static Stream<Arguments> dialectExpressions() {
      return Stream.of(
         // a ' in a bracket quoted name doesn't open a literal
         Arguments.of("sql server", "concat($(a.b), T.A, [it's], 'x', T.B)",
                      Set.of("T.A", "[it's]", "T.B")),
         Arguments.of("sql server", "T.A % 2 + [Customer's Name] + T.B",
                      Set.of("T.A", "[Customer's Name]", "T.B")),
         Arguments.of("sql server", "T.A + [it's] + T.B;", Set.of("T.A", "[it's]", "T.B")),
         Arguments.of("sql server", "concat($(a.b), T.[Customer's], 'x', T.B)",
                      Set.of("T.[Customer's]", "T.B")),
         // a generic database: a [ is an array or a subscript, not a quoted name
         Arguments.of("default", "contains(ARRAY [T.B], T.A)", Set.of("T.A", "T.B")),
         Arguments.of("default", "cardinality(ARRAY [T.A, T.B]) > 0", Set.of("T.A", "T.B")),
         Arguments.of("default", "x[1] + T.B", Set.of("T.B")),
         // the name before a literal is a column, not the prefix of a literal
         Arguments.of(null, "concat($(p), T.A^'x')", Set.of("T.A")),
         Arguments.of(null, "concat($(a.b), T.A^'x', T.B)", Set.of("T.A", "T.B")),
         Arguments.of(null, "T.A = N'x' and T.B = _utf8'y'", Set.of("T.A", "T.B")),
         // a variable is not a column
         Arguments.of(null, "$(a.b) + a.c", Set.of("a.c")),
         Arguments.of(null, "case when $(a.b) is null then 1 else a.c end", Set.of("a.c")),
         // a mysql # comment
         Arguments.of("mysql", "T.A # it's\n + T.B", Set.of("T.A", "T.B")),
         Arguments.of("mysql", "concat(T.A, # it's\n T.B, 'x')",
                      Set.of("T.A", "T.B")),
         Arguments.of(null, "T.A /* it's */ + T.B -- it's\n + T.C",
                      Set.of("T.A", "T.B", "T.C")),
         // a mysql backslash escape. A double quoted string is one name, as a quoted name,
         // the name in it is not a column (T.B")
         Arguments.of("mysql", "concat(T.A, 'it\\'s', T.B)", Set.of("T.A", "T.B")),
         Arguments.of("mysql", "concat(T.A, \"it\\\"s T.B\")",
                      Set.of("T.A", "\"it\\\"s T.B\"")),
         Arguments.of("mysql", "concat(T.A, \"it\\\"s T.Z\", T.B)",
                      Set.of("T.A", "\"it\\\"s T.Z\"", "T.B")));
   }

   @ParameterizedTest(name = "{0}: {1}")
   @MethodSource("dialectExpressions")
   void columnsOfDialectExpression(String dbType, String exp, Set<String> expected) {
      Set<String> columns = getColumns(exp, dbType);
      // a name without a table (a function, $) is not replaced, so it doesn't matter
      columns.removeIf(c -> c.indexOf('.') < 0 && c.indexOf('[') < 0);
      assertEquals(expected, columns);
   }

   private static Set<String> getColumns(String exp, String dbType) {
      Set<String> columns = new HashSet<>();
      ColumnIterator iterator = new ColumnIterator(exp, dbType);
      iterator.addColumnListener(columns::add);
      iterator.iterate();
      return columns;
   }
}
