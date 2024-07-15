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
package inetsoft.uql.rest.json;

import inetsoft.uql.VariableTable;
import inetsoft.uql.XTableNode;
import inetsoft.uql.schema.XSchema;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.net.URISyntaxException;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SimpleJsonTest {
   @Test
   void queryFile() throws URISyntaxException {
      final RestJsonQuery query = createRestJsonQuery();
      final RestJsonRuntime runtime = new RestJsonRuntime();
      final XTableNode result = runtime.runQuery(query, new VariableTable());
      assertEquals(result.getColCount(), 4);
      result.next();
      assertEquals("NJ", result.getObject(0));
   }

   @Test
   void queryColumnTypeIsUsed() throws URISyntaxException {
      final RestJsonQuery query = createRestJsonQuery();
      final RestJsonRuntime runtime = new RestJsonRuntime();
      final XTableNode originalResult = runtime.runQuery(query, new VariableTable());
      assertEquals(Integer.class, originalResult.getType(2));

      query.setColumnType("demographics.population", XSchema.DOUBLE);
      final XTableNode newResult = runtime.runQuery(query, new VariableTable());
      assertEquals(Double.class, newResult.getType(2));
   }

   private RestJsonQuery createRestJsonQuery() throws URISyntaxException {
      RestJsonDataSource xds = new RestJsonDataSource();
      ClassLoader loader = getClass().getClassLoader();
      File file = Paths.get(loader.getResource("simple.json").toURI()).toFile();
      xds.setURL(file.toURI().toString());
      RestJsonQuery query = new RestJsonQuery();
      query.setDataSource(xds);
      return query;
   }
}
