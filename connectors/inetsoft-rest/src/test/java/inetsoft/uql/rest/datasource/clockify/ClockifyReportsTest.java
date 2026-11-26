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
package inetsoft.uql.rest.datasource.clockify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.test.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.net.URI;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Clockify reports are served from reports.api.clockify.me and the detailed report is paged
 * through detailedFilter.page / detailedFilter.pageSize in the request body (Clockify Reports API
 * reference).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class, EndpointsTestConfig.class,
               SwapperTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ClockifyReportsTest {
   private static ClockifyQuery report(String endpoint, String jsonPath) {
      ClockifyDataSource dataSource = new ClockifyDataSource();
      dataSource.setApiKey("key");
      ClockifyQuery query = ReplaySupport.query(dataSource, new ClockifyQuery(), endpoint, jsonPath,
                                                Map.of("Workspace Id", "ws1"));
      query.setRequestBody("{\"dateRangeStart\":\"2020-05-10T00:00:00.000Z\"," +
                           "\"dateRangeEnd\":\"2020-05-16T23:59:59.000Z\"}");
      return query;
   }

   private static String entries(int first, int count) {
      return "{\"timeentries\":" + ReplaySupport.array(first, count) + "}";
   }

   @Test
   void reportEndpointsAreServedFromTheReportsHost() throws Exception {
      for(String name : List.of("Summary Report", "Detailed Report", "Weekly Report")) {
         ScriptedHttpHandler http = new ScriptedHttpHandler().on(url -> true, "{}");
         ReplaySupport.runTable(report(name, "$"), http);
         assertFalse(http.requests().isEmpty(), name);
         assertEquals("reports.api.clockify.me", URI.create(http.requests().get(0)).getHost(), name);
         assertTrue(http.requests().get(0).contains("/v1/workspaces/ws1/reports/"), name);
      }
   }

   @Test
   void detailedReportIsPagedThroughTheBodyUntilAnEmptyPage() throws Exception {
      ScriptedHttpHandler http = new ScriptedHttpHandler().on(
         url -> true, entries(1, 3), entries(4, 2), entries(0, 0));
      List<Map<String, Object>> rows = ReplaySupport.run(report("Detailed Report", "$.timeentries"), http);

      assertEquals(5, rows.size(), rows.toString());
      assertEquals(3, http.requests().size(), http.requests().toString());
      ObjectMapper mapper = new ObjectMapper();

      for(int i = 0; i < 3; i++) {
         JsonNode filter = mapper.readTree(http.bodies().get(i)).get("detailedFilter");
         assertNotNull(filter, http.bodies().get(i));
         assertEquals(i + 1, filter.get("page").asInt(), http.bodies().get(i));
         assertEquals(200, filter.get("pageSize").asInt(), http.bodies().get(i));
      }

      assertTrue(http.bodies().get(0).contains("dateRangeStart"), http.bodies().get(0));
   }
}
