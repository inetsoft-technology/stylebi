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
package inetsoft.uql.rest.datasource.servicenow;

import inetsoft.test.*;
import inetsoft.uql.VariableTable;
import inetsoft.uql.XTableNode;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A ServiceNow request whose API answers with an empty result list ended the offset paging
 * without recording that a response had been received, so the caller blamed the credentials
 * instead of reporting zero rows. Uses the real endpoints.json and the real endpoint runner.
 */
@EndpointsSpringContext
@SreeHome
@Tag("core")
class ServiceNowEmptyPageTest {
   @Test
   void emptyResultListRecordsTheCompletedRun() {
      ServiceNowQuery query = tableRecords();
      ScriptedHttpHandler http = new ScriptedHttpHandler().on("/now/table/", "{\"result\":[]}");

      assertNull(ReplaySupport.runTable(query, http));
      assertTrue(query.isRunCompleted());
   }

   @Test
   void failedFirstRequestStillThrowsAndIsNotCompleted() {
      ServiceNowQuery query = tableRecords();
      ScriptedHttpHandler http = new ScriptedHttpHandler().onReplies(
         url -> true, ScriptedHttpHandler.failure(new IOException("boom")));

      assertThrows(RuntimeException.class, () -> ReplaySupport.runTable(query, http));
      assertFalse(query.isRunCompleted());
   }

   @Test
   void unparsableResponseIsNotCompleted() {
      ServiceNowQuery query = tableRecords();
      ScriptedHttpHandler http = new ScriptedHttpHandler().on("/now/table/", "<html>not json</html>");

      try {
         XTableNode table = ReplaySupport.runTable(query, http);
         assertNull(table);
      }
      catch(RuntimeException ignore) {
         // a parse failure may surface as an exception; either way the run did not complete
      }

      assertFalse(query.isRunCompleted());
   }

   private static ServiceNowQuery tableRecords() {
      ServiceNowDataSource dataSource = new ServiceNowDataSource();
      dataSource.setInstance("dev123");
      return ReplaySupport.query(dataSource, new ServiceNowQuery(), "Table Records", "$.result",
                                 Map.of("Table Name", "incident"));
   }
}
