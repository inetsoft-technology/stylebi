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
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package inetsoft.web.wiz.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@Tag("core")
class WorksheetTableRequestTest {
   @Test
   void columnDescriptionRoundTrips() throws Exception {
      ObjectMapper mapper = new ObjectMapper();

      WorksheetTable req = mapper.readValue(
         """
         {
           "columns": [
             {
               "name": "sales",
               "description": "Total sales amount"
             }
           ]
         }
         """,
         WorksheetTable.class);

      JsonNode output = mapper.valueToTree(req);

      assertEquals("Total sales amount", output.at("/columns/0/description").asText());
   }

   @Test
   void batchRequestBindsWorksheetIdAndTables() throws Exception {
      ObjectMapper mapper = new ObjectMapper();

      WorksheetTableRequest req = mapper.readValue(
         """
         {"worksheetId":"w1","tables":[{"tableName":"A","tableType":"physical table"}]}
         """,
         WorksheetTableRequest.class);

      assertEquals("w1", req.getWorksheetId());
      assertEquals(1, req.getTables().size());
      assertEquals("A", req.getTables().get(0).getTableName());
   }
}
