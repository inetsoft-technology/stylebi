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
class WorksheetModelTest {
   @Test
   void matchesWorksheetTypescriptContract() throws Exception {
      ObjectMapper mapper = new ObjectMapper();

      WorksheetModel model = mapper.readValue(
         """
         {
           "identifier": "ws-1",
           "description": "Sales worksheet"
         }
         """,
         WorksheetModel.class);

      assertEquals("ws-1", model.identifier());
      assertEquals("Sales worksheet", model.description());
      assertNull(model.tables());

      JsonNode output = mapper.valueToTree(model);

      assertTrue(output.has("identifier"));
      assertTrue(output.has("description"));
      assertFalse(output.has("tables"));
      assertFalse(output.has("primaryTable"));
   }
}
