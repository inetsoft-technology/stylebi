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
      // Omitted mode/offsetUnit deserialize to null (WorksheetTableService normalizes null → ROWS).
      assertNull(f.getMode());
      assertNull(f.getOffsetUnit());
   }

   @Test
   void windowColumnFrameModeAndOffsetUnitRoundTrip() throws Exception {
      ObjectMapper mapper = new ObjectMapper();

      WorksheetTable t = mapper.readValue(
         """
         { "windowColumns": [ { "name":"s","fn":"SUM","column":"amount",
           "orderBy":[{"field":"d","direction":"ASC"}],
           "frame":{"mode":"RANGE","startBound":"PRECEDING","startOffset":7,
                     "endBound":"CURRENT_ROW","offsetUnit":"day"} } ] }
         """,
         WorksheetTable.class);

      var f = t.getWindowColumns().get(0).getFrame();
      assertEquals("RANGE", f.getMode());
      assertEquals("day", f.getOffsetUnit());
      assertEquals("PRECEDING", f.getStartBound());
      assertEquals(7, f.getStartOffset());
      assertEquals("CURRENT_ROW", f.getEndBound());

      JsonNode output = mapper.valueToTree(t);
      assertEquals("RANGE", output.at("/windowColumns/0/frame/mode").asText());
      assertEquals("day", output.at("/windowColumns/0/frame/offsetUnit").asText());
   }

   // ─── tabularSource: the generalized target ────────────────────────────────

   /**
    * The field names are the contract with wiz-services, which builds this object. Bound here
    * rather than left to the service tests because a rename that Jackson simply ignores produces a
    * null target and an "is required" error, with nothing saying the caller spelled it differently.
    */
   @Test
   void tabularFileTargetBinds() throws Exception {
      ObjectMapper mapper = new ObjectMapper();

      WorksheetTable t = mapper.readValue(
         """
         { "tableType": "tabular table",
           "tabularSource": {
             "datasourcePath": "Files/Sales",
             "targetKind": "file",
             "target": "2024/sales.xlsx#Q1",
             "params": { "firstRowHeader": "true", "delimiter": ";" },
             "sampleRows": 5
           } }
         """,
         WorksheetTable.class);

      WorksheetTable.TabularSource src = t.getTabularSource();
      assertEquals("file", src.getTargetKind());
      assertEquals("2024/sales.xlsx#Q1", src.getTarget());
      assertEquals("true", src.getParams().get("firstRowHeader"));
      assertEquals(";", src.getParams().get("delimiter"));
      assertEquals(5, src.getSampleRows());
   }

   /**
    * {@code endpoint} was this field's name before a file could be named through it, and it named
    * the same thing. Kept as an alias so a caller written against that shape still binds rather
    * than failing with "target is required" over a value it did supply.
    */
   @Test
   void tabularEndpointBindsAsTargetUnderItsOldName() throws Exception {
      ObjectMapper mapper = new ObjectMapper();

      WorksheetTable t = mapper.readValue(
         """
         { "tableType": "tabular table",
           "tabularSource": { "datasourcePath": "SaaS/Stripe", "endpoint": "Charges",
                              "parameters": { "limit": "100" } } }
         """,
         WorksheetTable.class);

      WorksheetTable.TabularSource src = t.getTabularSource();
      assertEquals("Charges", src.getTarget());
      // Absent, not defaulted on the model: buildTabularTable reads an absent kind as "endpoint",
      // which is where that rule belongs — the model only reports what arrived.
      assertNull(src.getTargetKind());
      assertEquals("100", src.getParameters().get("limit"));
      assertNull(src.getParams());
   }
}
