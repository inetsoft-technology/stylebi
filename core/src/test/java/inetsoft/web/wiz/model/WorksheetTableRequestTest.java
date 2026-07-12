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

import java.util.List;

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

   @Test
   void windowColumnRoundTrips() throws Exception {
      ObjectMapper mapper = new ObjectMapper();

      WorksheetTable table = mapper.readValue(
         """
         {
           "windowColumns": [
             {
               "name": "rn",
               "fn": "ROW_NUMBER",
               "partitionBy": ["stage"],
               "orderBy": [ { "field": "amount", "direction": "DESC" } ]
             }
           ]
         }
         """,
         WorksheetTable.class);

      assertEquals(1, table.getWindowColumns().size());
      WorksheetTable.WindowColumnInfo win = table.getWindowColumns().get(0);
      assertEquals("rn", win.getName());
      assertEquals("ROW_NUMBER", win.getFn());
      assertEquals(List.of("stage"), win.getPartitionBy());
      assertEquals(1, win.getOrderBy().size());
      assertEquals("amount", win.getOrderBy().get(0).getField());
      assertEquals("DESC", win.getOrderBy().get(0).getDirection());

      JsonNode output = mapper.valueToTree(table);
      assertEquals("ROW_NUMBER", output.at("/windowColumns/0/fn").asText());
   }

   @Test
   void windowColumnFrameRoundTrips() throws Exception {
      ObjectMapper mapper = new ObjectMapper();

      WorksheetTable t = mapper.readValue(
         """
         { "windowColumns": [ { "name":"ma","fn":"AVG","column":"amount",
           "orderBy":[{"field":"t","direction":"ASC"}],
           "frame":{"startBound":"PRECEDING","startOffset":2,"endBound":"CURRENT_ROW"} } ] }
         """,
         WorksheetTable.class);

      var f = t.getWindowColumns().get(0).getFrame();
      assertEquals("PRECEDING", f.getStartBound());
      assertEquals(2, f.getStartOffset());
      assertEquals("CURRENT_ROW", f.getEndBound());
   }
}
