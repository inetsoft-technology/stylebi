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
package inetsoft.uql.jdbc;

import org.junit.jupiter.api.RepeatedTest;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Regression test for Redmine #76616 - variables in the "Enter Parameters"
 * dialog were shown out of order (e.g. var2 before var1) because
 * {@link inetsoft.uql.XQuery} tracked its discovered variables in a
 * ConcurrentHashMap, whose iteration order is hash-based rather than the
 * order variables were declared/discovered in the query. Repeated to guard
 * against a fix that happens to preserve order for this one hash arrangement
 * but not in general.
 */
class JDBCQueryVariableOrderTest {
   @RepeatedTest(5)
   void variablesShouldBeOrderedByAppearanceInSql() {
      JDBCQuery query = new JDBCQuery();
      query.setSQLDefinition(new FreeformSQL(
         "select * from orders where order_date between $(var1) and $(var2)"));

      List<String> names = Collections.list(query.getVariableNames());

      assertEquals(Arrays.asList("var1", "var2"), names);
   }

   @RepeatedTest(5)
   void variablesShouldBeOrderedByAppearanceInSqlForManyVariables() {
      JDBCQuery query = new JDBCQuery();
      query.setSQLDefinition(new FreeformSQL(
         "select * from t where a = $(charlie) and b = $(alpha) and c = $(bravo) " +
         "and d = $(delta) and e = $(echo)"));

      List<String> names = Collections.list(query.getVariableNames());

      assertEquals(Arrays.asList("charlie", "alpha", "bravo", "delta", "echo"), names);
   }
}
