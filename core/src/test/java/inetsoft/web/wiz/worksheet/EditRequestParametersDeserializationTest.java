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
package inetsoft.web.wiz.worksheet;

import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.web.WebConfig;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies {@code add_table}'s {@code endpoint}+{@code parameters} shape round-trips through the
 * real app {@link ObjectMapper} ({@link WebConfig#objectMapper()}, the same one Spring uses for
 * every {@code @RequestBody EditRequest} controller method) -- {@code EditRequest} has no
 * {@code @JsonIgnoreProperties(ignoreUnknown = true)}, so before {@code parameters} was added to
 * the record, a real caller's {@code add_table} request supplying it (the common case: most
 * real endpoints have required parameters) would 400 at deserialization before
 * {@code addTabularTable} ever ran. See {@link GroupSpecDeserializationTest} for the sibling
 * pattern this mirrors.
 */
@Tag("core")
class EditRequestParametersDeserializationTest {

   private final ObjectMapper mapper = new WebConfig().objectMapper();

   @Test
   void deserializesEndpointWithParameters() throws Exception {
      EditRequest req = mapper.readValue("""
         {
           "op": "add_table",
           "table": "issues",
           "datasource": "SaaS/GitHub Prod",
           "endpoint": "Repository Issue Events",
           "parameters": {"owner": "inetsoft-technology", "repo": "stylebi"}
         }
         """, EditRequest.class);

      assertEquals("Repository Issue Events", req.endpoint());
      assertNotNull(req.parameters());
      assertEquals(2, req.parameters().size());
      assertEquals("inetsoft-technology", req.parameters().get("owner"));
      assertEquals("stylebi", req.parameters().get("repo"));
   }

   @Test
   void deserializesEndpointWithoutParameters() throws Exception {
      EditRequest req = mapper.readValue("""
         {
           "op": "add_table",
           "table": "issues",
           "datasource": "SaaS/GitHub Prod",
           "endpoint": "Repository Issue Events"
         }
         """, EditRequest.class);

      assertEquals("Repository Issue Events", req.endpoint());
      assertNull(req.parameters());
   }

   /**
    * queryParams is appended as the LAST field of the canonical record (after {@code rankings}),
    * matching this file's own established every-new-field-goes-at-the-end convention, so this
    * confirms it round-trips through the real app ObjectMapper the same way {@code parameters}
    * does above -- a real caller's add_table request supplying it must not 400 at deserialization.
    */
   @Test
   void deserializesAddTableWithQueryParams() throws Exception {
      EditRequest req = mapper.readValue("""
         {
           "op": "add_table",
           "table": "Orders",
           "datasource": "OData/Northwind",
           "queryParams": {"entitySet": "Orders", "top": 100, "includeAnnotations": true}
         }
         """, EditRequest.class);

      assertNotNull(req.queryParams());
      assertEquals(3, req.queryParams().size());
      assertEquals("Orders", req.queryParams().get("entitySet"));
      assertEquals(100, req.queryParams().get("top"));
      assertEquals(true, req.queryParams().get("includeAnnotations"));
   }

   /**
    * extraProperties is appended as the LAST field of the canonical record (after
    * {@code queryParams}), matching this file's own established every-new-field-goes-at-the-end
    * convention -- confirms it round-trips through the real app ObjectMapper the same way
    * {@code queryParams} does above.
    */
   @Test
   void deserializesAddTableWithExtraProperties() throws Exception {
      EditRequest req = mapper.readValue("""
         {
           "op": "add_table",
           "table": "Widgets",
           "datasource": "SaaS/Generic REST",
           "suffix": "/v1/widgets",
           "extraProperties": {"jsonPath": "$.items[*]", "timeout": 30, "expanded": true}
         }
         """, EditRequest.class);

      assertNotNull(req.extraProperties());
      assertEquals(3, req.extraProperties().size());
      assertEquals("$.items[*]", req.extraProperties().get("jsonPath"));
      assertEquals(30, req.extraProperties().get("timeout"));
      assertEquals(true, req.extraProperties().get("expanded"));
   }

   /**
    * WBS-029 (bug 76502): {@code variableValues} is now {@code Map<String, Object>} so a JSON
    * array can bind to a genuine multi-value assignment. Confirms the real app
    * {@code ObjectMapper}'s default binding for an untyped {@code Object} map value -- a plain
    * JSON string stays a {@code String} (single-value callers unaffected), and a JSON array of
    * strings becomes a {@code List<String>} (specifically an {@code ArrayList}), not some other
    * unexpected runtime shape -- before {@code WorksheetAgentController.setVariableValues} is
    * considered complete (see design's flagged, not-live-verified risk).
    */
   @Test
   void deserializesVariableValuesStringAndArrayShapes() throws Exception {
      EditRequest req = mapper.readValue("""
         {
           "op": "set_variable_values",
           "variableValues": {
             "Region": "East",
             "Reasons": ["Item defective", "No longer needed"]
           }
         }
         """, EditRequest.class);

      assertNotNull(req.variableValues());
      assertEquals("East", req.variableValues().get("Region"));

      Object reasons = req.variableValues().get("Reasons");
      assertInstanceOf(List.class, reasons);
      assertEquals(List.of("Item defective", "No longer needed"), reasons);

      for(Object element : (List<?>) reasons) {
         assertInstanceOf(String.class, element);
      }
   }
}
