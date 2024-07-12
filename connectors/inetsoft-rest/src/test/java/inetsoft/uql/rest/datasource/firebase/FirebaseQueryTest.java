/*
 * inetsoft-rest - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.uql.rest.datasource.firebase;

import inetsoft.test.EndpointsSource;
import inetsoft.test.SreeHome;
import inetsoft.uql.VariableTable;
import inetsoft.uql.XTableNode;
import inetsoft.uql.rest.json.RestJsonRuntime;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SreeHome("../../target/config")
class FirebaseQueryTest {
   @ParameterizedTest(name = "{0}")
   @EndpointsSource(dataSource = FirebaseDataSource.class, query = FirebaseQuery.class)
   @Tag("endpoints")
   void testRunQuery(@SuppressWarnings("unused") String endpoint, FirebaseQuery query) {
      RestJsonRuntime runtime = new RestJsonRuntime();
      final FirebaseDataSource dataSource = (FirebaseDataSource) query.getDataSource();
      dataSource.setProjectId("inetsoft-firestone");
      XTableNode results = runtime.runQuery(query, new VariableTable());

      assertNotNull(results);
      int rowCount = 0;

      while(results.next()) {
         ++rowCount;
      }

      assertTrue(rowCount > 0);
   }
}
