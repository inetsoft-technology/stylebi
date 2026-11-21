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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import inetsoft.web.wiz.controller.WizLogicalModelWire.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins the logical model wire records: the field names (the contract with the plugin's bridge
 * types), the refusal of an unknown key (charter C8) and the three states of {@code oldName}.
 */
@Tag("core")
class WizLogicalModelWireTest {
   private final ObjectMapper mapper = new ObjectMapper();

   private static List<String> names(Class<?> record) {
      return Arrays.stream(record.getRecordComponents()).map(RecordComponent::getName).toList();
   }

   @Test
   void fieldNamesArePinned() {
      assertEquals(List.of("name", "description", "view", "folder", "connection", "parent",
                           "extended", "entities"), names(Definition.class));
      assertEquals(List.of("name", "oldName", "description", "visible", "baseElement",
                           "errorMessage", "attributes"), names(Entity.class));
      assertEquals(List.of("name", "oldName", "description", "visible", "type", "table", "column",
                           "expression", "aggregate", "parseable", "dataType", "refType",
                           "formula", "browseData", "browseQuery", "format", "drill",
                           "baseElement", "errorMessage"), names(Attribute.class));
      assertEquals(List.of("format", "formatSpec", "durationPadZeros"), names(Format.class));
      assertEquals(List.of("name", "linkType", "link", "targetFrame", "tip", "passParams",
                           "disablePrompting", "params", "query", "queryFields"),
                   names(DrillPath.class));
      assertEquals(List.of("database", "name", "parent"), names(GetRequest.class));
      assertEquals(List.of("database", "definition", "dryRun"), names(CreateRequest.class));
      assertEquals(List.of("database", "name", "definition", "dryRun", "baseRevision"),
                   names(UpdateRequest.class));
      assertEquals(List.of("ok", "definition", "folder", "readOnly", "revision"),
                   names(GetResponse.class));
      assertEquals(List.of("ok", "name", "folder", "definition", "warnings", "revision"),
                   names(CreateResponse.class));
      assertEquals(List.of("ok", "definition", "warnings", "elementDependents",
                           "viewDependents", "revision"), names(UpdateResponse.class));
   }

   @Test
   void anUnknownKeyIsRefusedAtEveryLevelAndNamed() {
      UnrecognizedPropertyException top = assertThrows(
         UnrecognizedPropertyException.class,
         () -> mapper.readValue("{\"database\":\"d\",\"definiton\":{}}", CreateRequest.class));
      assertEquals("definiton", top.getPropertyName());

      UnrecognizedPropertyException deep = assertThrows(
         UnrecognizedPropertyException.class,
         () -> mapper.readValue(
            "{\"name\":\"m\",\"entities\":[{\"name\":\"E\",\"attributes\":["
               + "{\"name\":\"A\",\"colunm\":\"X\"}]}]}", Definition.class));
      assertEquals("colunm", deep.getPropertyName());
   }

   @Test
   void oldNameHasThreeStatesTheJsonDistinguishes() throws Exception {
      Entity absent = mapper.readValue("{\"name\":\"E\"}", Entity.class);
      Entity explicitNull = mapper.readValue("{\"name\":\"E\",\"oldName\":null}", Entity.class);
      Entity named = mapper.readValue("{\"name\":\"E\",\"oldName\":\"Old\"}", Entity.class);
      Attribute attributeNull = mapper.readValue("{\"name\":\"A\",\"oldName\":null}",
                                                 Attribute.class);

      assertNull(absent.oldName());
      assertEquals(WizLogicalModelWire.EXPLICIT_NEW, explicitNull.oldName());
      assertEquals("Old", named.oldName());
      assertEquals(WizLogicalModelWire.EXPLICIT_NEW, attributeNull.oldName());
   }

   @Test
   void aGetResponseKeepsANullFolderExplicit() throws Exception {
      String json = mapper.writeValueAsString(new GetResponse(true, null, null, false, null));

      assertTrue(json.contains("\"folder\":null"), json);
      assertTrue(json.contains("\"readOnly\":false"), json);
   }
}
