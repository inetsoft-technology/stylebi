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
      Set<String> columns = new HashSet<>();
      ColumnIterator iterator = new ColumnIterator(exp);
      iterator.addColumnListener(columns::add);
      iterator.iterate();

      assertEquals(expected, columns);
   }
}
