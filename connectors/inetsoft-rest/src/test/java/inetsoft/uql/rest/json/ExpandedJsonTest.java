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
package inetsoft.uql.rest.json;

import inetsoft.report.lens.xnode.XNodeTableLens;
import inetsoft.uql.*;
import org.junit.jupiter.api.Test;

import java.net.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ExpandedJsonTest {
   @Test
   void queryFile() throws URISyntaxException {
      final RestJsonQuery query = createRestJsonQuery();
      final RestJsonRuntime runtime = new RestJsonRuntime();
      final XTableNode result = runtime.runQuery(query, new VariableTable());
      final XNodeTableLens lens = new XNodeTableLens(result);
      lens.moreRows(Integer.MAX_VALUE);
      assertEquals(lens.getColCount(), 1);
      assertEquals(lens.getRowCount(), 2);
      assertEquals("empty", lens.getObject(0, 0));
   }

   private RestJsonQuery createRestJsonQuery() throws URISyntaxException {
      RestJsonDataSource xds = new RestJsonDataSource();
      ClassLoader loader = getClass().getClassLoader();
      URI uri = loader.getResource("inetsoft/uql/rest/json/empty.json").toURI();
      xds.setURL(uri.toString());
      RestJsonQuery query = new RestJsonQuery();
      query.setDataSource(xds);
      query.setExpanded(true);
      query.setExpandTop(false);
      return query;
   }
}
