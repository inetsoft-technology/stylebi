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

package inetsoft.test;

import inetsoft.uql.VariableTable;
import inetsoft.uql.XTableNode;
import inetsoft.uql.rest.AbstractRestDataSource;
import inetsoft.uql.rest.json.EndpointJsonQuery;
import inetsoft.uql.tabular.RestParameter;
import inetsoft.uql.tabular.RestParameters;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Helpers for tests that run an endpoint query against {@link ScriptedHttpHandler}.
 */
public final class ReplaySupport {
   private ReplaySupport() {
   }

   /**
    * Fills in a query for an endpoint of its data source. Every required parameter has to be
    * given, otherwise the runtime logs the error and returns no table.
    */
   public static <Q extends EndpointJsonQuery<?>> Q query(
      AbstractRestDataSource<?> dataSource, Q query, String endpoint, String jsonPath,
      Map<String, String> parameters)
   {
      query.setDataSource(dataSource);
      query.setJsonPath(jsonPath);
      query.setEndpoint(endpoint);
      RestParameters restParameters = query.getParameters();

      for(RestParameter parameter : restParameters.getParameters()) {
         String value = parameters.get(parameter.getName());

         if(value != null) {
            parameter.setValue(value);
         }
      }

      query.setParameters(restParameters);
      return query;
   }

   /**
    * Runs the query with the requests going to the handler and returns the table, or null if
    * the query returned none: it does so both when it fails (the runtime logs the error) and
    * when there is no data at all.
    */
   public static XTableNode runTable(EndpointJsonQuery<?> query, ScriptedHttpHandler http) {
      XTableNode table = new ScriptedHttpHandler.Runtime(http).runQuery(query, new VariableTable());
      http.assertNoFailures();
      return table;
   }

   /** Runs the query with the requests going to the handler, and returns the rows. */
   public static List<Map<String, Object>> run(EndpointJsonQuery<?> query,
                                               ScriptedHttpHandler http)
   {
      XTableNode table = runTable(query, http);
      assertNotNull(table, "the query returned no table; see the log for the error");
      return rows(table);
   }

   /** Reads the rest of the table into one map per row, keyed by column name. */
   public static List<Map<String, Object>> rows(XTableNode table) {
      List<Map<String, Object>> rows = new ArrayList<>();

      while(table.next()) {
         Map<String, Object> row = new LinkedHashMap<>();

         for(int i = 0; i < table.getColCount(); i++) {
            row.put(table.getName(i), table.getObject(i));
         }

         rows.add(row);
      }

      return rows;
   }

   /** The values of one column. */
   public static List<Object> column(List<Map<String, Object>> rows, String name) {
      List<Object> values = new ArrayList<>();

      for(Map<String, Object> row : rows) {
         values.add(row.get(name));
      }

      return values;
   }

   /** A JSON array of {@code count} objects whose "id" runs from {@code first}. */
   public static String array(int first, int count) {
      StringBuilder json = new StringBuilder("[");

      for(int i = 0; i < count; i++) {
         json.append(i == 0 ? "" : ",").append("{\"id\":").append(first + i).append('}');
      }

      return json.append(']').toString();
   }
}
