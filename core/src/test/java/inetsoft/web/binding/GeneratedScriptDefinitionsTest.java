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
package inetsoft.web.binding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.web.composer.script.service.ScriptService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Method;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #82370: the synthetic union base types (ColorFrame, Coordinate, ...) emitted by the
 * tern annotation processor are type names for "+Name" references, not globals. Both script
 * editors must publish them under "!define" and never as top-level (global) names.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class GeneratedScriptDefinitionsTest {
   private static final List<String> NON_GLOBAL_UNIONS = List.of(
      "ColorFrame", "Coordinate", "GraphForm", "LineFrame", "SchemaPainter", "ShapeFrame",
      "SizeFrame", "TextFrame", "TextureFrame", "VisualFrame");

   @Test
   void generatedFileKeepsUnionsUnderDefine() throws Exception {
      ObjectNode generated = (ObjectNode) mapper.readTree(
         getClass().getResource("/inetsoft/web/binding/js-functions.generated.json"));
      assertNoTopLevelUnions(generated);
      assertUnionsDefined(generated.get("!define"));
      assertTrue(generated.get("!unions").isArray(), "the incremental marker is kept");
   }

   @Test
   void scriptServicePublishesUnionsOnlyUnderDefine() throws Exception {
      ObjectNode library = newLibrary();
      new ScriptService(null, null, null, null, null).createStaticDefinitions(mapper, library);
      assertPublished(library);
   }

   @Test
   void vsScriptableServicePublishesUnionsOnlyUnderDefine() throws Exception {
      ObjectNode library = newLibrary();
      VSScriptableService service = new VSScriptableService(null, null, null, null);
      Method method = VSScriptableService.class.getDeclaredMethod(
         "createStaticDefinitions", ObjectMapper.class, ObjectNode.class);
      method.setAccessible(true);
      method.invoke(service, mapper, library);
      assertPublished(library);
   }

   @Test
   void mergeKeepsTypesAlreadyDefinedInTheLibrary() throws Exception {
      ObjectNode library = newLibrary();
      ((ObjectNode) library.get("!define")).set("Existing", mapper.createObjectNode());
      new ScriptService(null, null, null, null, null).createStaticDefinitions(mapper, library);
      assertTrue(library.get("!define").has("Existing"),
                 "types the library already defined survive the merge");
      assertTrue(library.get("!define").has("Coordinate"));
   }

   private void assertPublished(ObjectNode library) {
      assertNoTopLevelUnions(library);
      assertUnionsDefined(library.get("!define"));

      // the chain EGraph.getCoordinate() -> Coordinate still resolves a type
      JsonNode type = library.get("EGraph").get("prototype").get("getCoordinate").get("!type");
      assertNotNull(type);
      assertTrue(type.asText().endsWith("+Coordinate"), type.asText());
      assertTrue(library.get("!define").has("Coordinate"));
   }

   private void assertNoTopLevelUnions(ObjectNode root) {
      for(String name : NON_GLOBAL_UNIONS) {
         assertFalse(root.has(name), name + " must not be a global of the editor");
      }
   }

   private void assertUnionsDefined(JsonNode define) {
      assertNotNull(define, "the union types live under !define");

      for(String name : NON_GLOBAL_UNIONS) {
         assertTrue(define.has(name), name + " is defined under !define");
         assertTrue(define.get(name).has("prototype"), name + " carries its members");
      }

      assertTrue(define.has("Scale"));
   }

   private ObjectNode newLibrary() {
      ObjectNode library = mapper.createObjectNode();
      library.put("!name", "inetsoft");
      library.set("!define", mapper.createObjectNode());
      return library;
   }

   private final ObjectMapper mapper = new ObjectMapper();
}
