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
package inetsoft.uql.rest.datasource.quickbooks;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.uql.rest.EndpointQueryDelegate;
import inetsoft.uql.tabular.RestParameter;
import inetsoft.uql.tabular.RestParameters;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * QuickBooks Reports endpoint placeholder corrections (Bug #82206, #82207): the AR/AP Aging Detail
 * Start Due Date placeholder, the Sort Order choice list, and optional Department.
 */
class QuickbooksReportsEndpointsTest {
   @BeforeAll
   static void load() throws Exception {
      endpoints = new ObjectMapper()
         .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
         .readValue(QuickbooksReportsEndpoints.class.getResource("endpoints.json"),
                    QuickbooksReportsEndpoints.class)
         .toMap();
   }

   @Test
   void agingDetailBindsStartDueDateAndEndDueDateSeparately() {
      for(String name : new String[] { "AR Aging Detail", "AP Aging Detail" }) {
         RestParameters params = parameters(name);
         assertNotNull(find(params, "Start Due Date"), name);
         assertNotNull(find(params, "End Due Date"), name);

         find(params, "Report Date").setValue("2025-12-31");
         find(params, "End Due Date").setValue("2025-12-31");

         String url = build(name, params);
         assertTrue(url.contains("end_duedate=2025-12-31"), url);
         assertTrue(url.contains("report_date=2025-12-31"), url);
         assertFalse(url.contains("start_duedate"), url);
      }
   }

   @Test
   void agingDetailStartDueDateIsSentWhenSupplied() {
      RestParameters params = parameters("AR Aging Detail");
      find(params, "Start Due Date").setValue("2025-01-01");
      find(params, "End Due Date").setValue("2025-12-31");

      String url = build("AR Aging Detail", params);
      assertTrue(url.contains("start_duedate=2025-01-01"), url);
      assertTrue(url.contains("end_duedate=2025-12-31"), url);
   }

   @Test
   void sortOrderChoicesAreExactlyAscendDescend() {
      int checked = 0;

      for(String name : endpoints.keySet()) {
         RestParameter sortOrder = find(parameters(name), "Sort Order");

         if(sortOrder != null) {
            assertEquals("ascend|descend", sortOrder.getPlaceholder(), name);
            checked++;
         }
      }

      assertTrue(checked > 0);
   }

   @Test
   void departmentIsOptionalOnEveryEndpoint() {
      int checked = 0;

      for(String name : endpoints.keySet()) {
         RestParameter department = find(parameters(name), "Department");

         if(department != null) {
            assertFalse(department.isRequired(), name + " Department must be optional");
            checked++;
         }
      }

      assertTrue(checked > 0);
   }

   @Test
   void customerIncomeAndGeneralLedgerBuildWithoutDepartment() {
      for(String name : new String[] { "Customer Income", "General Ledger" }) {
         RestParameters params = parameters(name);

         for(RestParameter parameter : params.getParameters()) {
            assertFalse(parameter.isRequired(), name + " requires " + parameter.getName());
         }

         find(params, "Start Date").setValue("2025-01-01");
         find(params, "End Date").setValue("2025-12-31");

         String url = build(name, params);
         assertTrue(url.contains("start_date=2025-01-01"), url);
         assertFalse(url.contains("department="), url);
      }
   }

   private RestParameters parameters(String endpoint) {
      return delegate.getRestParameters(null, endpoint, null, endpoints);
   }

   private String build(String endpoint, RestParameters params) {
      return delegate.getSuffix(endpoint, params, null, endpoints).build();
   }

   private static RestParameter find(RestParameters params, String name) {
      for(RestParameter parameter : params.getParameters()) {
         if(name.equals(parameter.getName())) {
            return parameter;
         }
      }

      return null;
   }

   private static Map<String, QuickbooksReportsEndpoint> endpoints;
   private final EndpointQueryDelegate<QuickbooksReportsEndpoint> delegate =
      new EndpointQueryDelegate<>();
}
