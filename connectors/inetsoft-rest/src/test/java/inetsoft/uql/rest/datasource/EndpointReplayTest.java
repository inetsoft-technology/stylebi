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

package inetsoft.uql.rest.datasource;

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.EndpointsTestConfig;
import inetsoft.test.ScriptedHttpHandler;
import inetsoft.test.SreeHome;
import inetsoft.test.SwapperTestConfiguration;
import inetsoft.uql.VariableTable;
import inetsoft.uql.XTableNode;
import inetsoft.uql.rest.datasource.airtable.AirtableDataSource;
import inetsoft.uql.rest.datasource.airtable.AirtableQuery;
import inetsoft.uql.tabular.RestParameter;
import inetsoft.uql.tabular.RestParameters;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Runs a real query through the real runtime, pagination and JSON transformer, with the HTTP
 * responses scripted, so no network or credentials are needed.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class, EndpointsTestConfig.class,
               SwapperTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class)
@SreeHome
class EndpointReplayTest {
   @Test
   void airtableRecordsAreReadAcrossPagesFollowingTheOffset() throws Exception {
      ScriptedHttpHandler http = new ScriptedHttpHandler()
         .on("/v0/app1/Stories", """
            {"records":[{"id":"rec1","fields":{"Name":"A"}},{"id":"rec2","fields":{"Name":"B"}}],
             "offset":"next-page"}""", """
            {"records":[{"id":"rec3","fields":{"Name":"C"}}]}""");

      List<Map<String, Object>> rows = run(airtable("Records", "$.records",
         Map.of("Base ID", "app1", "Table Name", "Stories", "Time Zone", "America/New_York",
                 "User Locale", "en-us")), http);

      assertEquals(3, rows.size(), rows.toString());
      assertEquals(List.of("rec1", "rec2", "rec3"), column(rows, "id"));
      assertEquals(2, http.requests().size(), http.requests().toString());
      assertFalse(http.requests().get(0).contains("offset="), http.requests().get(0));
      assertTrue(http.requests().get(0).contains("pageSize=100"), http.requests().get(0));
      assertTrue(http.requests().get(1).contains("offset=next-page"), http.requests().get(1));
   }

   @Test
   void airtableSingleRecordMakesOneRequest() throws Exception {
      ScriptedHttpHandler http = new ScriptedHttpHandler()
         .on("/v0/app1/Stories/rec9", "{\"id\":\"rec9\",\"fields\":{\"Name\":\"Z\"}}");

      List<Map<String, Object>> rows = run(airtable("Record", "$",
         Map.of("Base ID", "app1", "Table Name", "Stories", "Record ID", "rec9")), http);

      assertEquals(1, rows.size(), rows.toString());
      assertEquals(1, http.requests().size());
   }

   private static AirtableQuery airtable(String endpoint, String jsonPath,
                                         Map<String, String> parameters)
   {
      AirtableDataSource dataSource = new AirtableDataSource();
      dataSource.setApiToken("token");
      AirtableQuery query = new AirtableQuery();
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

   private static List<Map<String, Object>> run(AirtableQuery query, ScriptedHttpHandler http) {
      XTableNode table = new ScriptedHttpHandler.Runtime(http).runQuery(query, new VariableTable());
      assertNotNull(table);
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

   private static List<Object> column(List<Map<String, Object>> rows, String name) {
      List<Object> values = new ArrayList<>();

      for(Map<String, Object> row : rows) {
         values.add(row.get(name));
      }

      return values;
   }
}
