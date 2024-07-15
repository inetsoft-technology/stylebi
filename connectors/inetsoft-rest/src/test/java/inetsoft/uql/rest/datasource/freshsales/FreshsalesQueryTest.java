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
package inetsoft.uql.rest.datasource.freshsales;

import inetsoft.test.EndpointsSource;
import inetsoft.test.SreeHome;
import inetsoft.uql.VariableTable;
import inetsoft.uql.XTableNode;
import inetsoft.uql.rest.json.RestJsonRuntime;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SreeHome
class FreshsalesQueryTest {

   @ParameterizedTest(name = "{0}")
   @EndpointsSource(dataSource = FreshsalesDataSource.class, query = FreshsalesQuery.class)
   @Tag("endpoints")
   void testRunQuery(@SuppressWarnings("unused") String endpoint, FreshsalesQuery query) {
      RestJsonRuntime runtime = new RestJsonRuntime();
      XTableNode results = runtime.runQuery(query, new VariableTable());
      assertNotNull(results);
      int rowCount = 0;

      while(results.next()) {
         ++rowCount;
      }

      assertTrue(rowCount > 0);
   }
}
