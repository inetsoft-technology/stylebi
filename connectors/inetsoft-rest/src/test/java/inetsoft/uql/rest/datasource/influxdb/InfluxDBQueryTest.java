/*
 * inetsoft-rest - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.uql.rest.datasource.influxdb;

import inetsoft.test.EndpointsSource;
import inetsoft.test.SreeHome;
import inetsoft.uql.VariableTable;
import inetsoft.uql.XTableNode;
import inetsoft.uql.rest.json.RestJsonRuntime;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
@SreeHome
class InfluxDBQueryTest {

   @Container
   public static final GenericContainer<?> db =
      new GenericContainer<>("influxdb:1.7-alpine")
         .withExposedPorts(8086)
         .withEnv("INFLUXDB_DB", "inetsoftdb1")
         .withEnv("INFLUXDB_HTTP_AUTH_ENABLED", "true")
         .withEnv("INFLUXDB_ADMIN_ENABLED", "true")
         .withEnv("INFLUXDB_ADMIN_USER", "admin")
         .withEnv("INFLUXDB_ADMIN_PASSWORD", "secret")
         .withEnv("INFLUXDB_USER", "inetsoft")
         .withEnv("INFLUXDB_USER_PASSWORD", "secret")
         .waitingFor(Wait.forLogMessage(".*Storing statistics.*\\n", 2));

   @ParameterizedTest(name = "{0}")
   @EndpointsSource(dataSource = InfluxDBDataSource.class, query = InfluxDBQuery.class)
   @Tag("endpoints")
   void testRunQuery(@SuppressWarnings("unused") String endpoint, InfluxDBQuery query) {
      String url = "http://" + db.getContainerIpAddress() + ":" + db.getMappedPort(8086);
      InfluxDBDataSource dataSource = (InfluxDBDataSource) query.getDataSource();
      dataSource.setURL(url);
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
