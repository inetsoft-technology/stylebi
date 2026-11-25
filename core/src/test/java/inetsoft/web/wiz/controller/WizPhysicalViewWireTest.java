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
package inetsoft.web.wiz.controller;

import inetsoft.web.wiz.controller.WizPhysicalViewWire.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the physical view wire records: the field names are the contract with the plugin's
 * WIRE_FIELDS table (bridgeTypes.ts: PhysicalGetResponse, PhysicalWriteRequest, PhysicalWarning,
 * PhysicalWriteResponse), so a rename here fails before the two repositories drift.
 */
@Tag("core")
class WizPhysicalViewWireTest {
   private static List<String> names(Class<?> record) {
      return Arrays.stream(record.getRecordComponents()).map(RecordComponent::getName).toList();
   }

   @Test
   void fieldNamesArePinned() {
      assertEquals(List.of("database", "name", "parent"), names(GetRequest.class));
      assertEquals(List.of("ok", "definition", "folder", "readOnly", "revision"), names(GetResponse.class));
      assertEquals(List.of("database", "name", "parent", "definition", "dryRun",
                           "baseRevision"),
                   names(WriteRequest.class));
      assertEquals(List.of("code", "message", "table1", "table2", "joins"),
                   names(Warning.class));
      assertEquals(List.of("ok", "name", "folder", "definition", "warnings", "dependents",
                           "revision"),
                   names(WriteResponse.class));
   }

   @Test
   void theJoinsOfAWarningAreOmittedFromTheJsonWhenNull() throws Exception {
      var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
      String plain = mapper.writeValueAsString(new Warning("UNJOINED_TABLES", "m", "A", "B"));
      String cycle = mapper.writeValueAsString(
         new Warning("CYCLE", "m", "", "", List.of("A.X -> B.Y")));

      assertFalse(plain.contains("joins"), plain);
      assertTrue(cycle.contains("\"joins\":[\"A.X -> B.Y\"]"), cycle);
   }
}
