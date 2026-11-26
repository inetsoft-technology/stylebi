/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;

/**
 * Loads the tern definitions generated from the {@code @Tern*} annotations
 * ({@code js-functions.generated.json}) for the script editors.
 *
 * The generated file keeps its "!define" type definitions (e.g. the base types behind
 * "+Coordinate" references) apart from its top-level keys. A top-level key is a global
 * name in the editor, so "!define" has to be merged into the library's "!define" rather than
 * copied over it, and nothing else may be promoted to the top level (Bug #82370).
 */
public final class GeneratedScriptDefinitions {
   private GeneratedScriptDefinitions() {
   }

   /**
    * Copy the generated top-level definitions into {@code functions}.
    *
    * @return the generated "!define" types (never null), to be merged into the library with
    *         {@link #mergeDefine(ObjectNode, ObjectNode)}.
    */
   public static ObjectNode addGenerated(ObjectMapper mapper, ObjectNode functions)
      throws IOException
   {
      ObjectNode generated = (ObjectNode) mapper.readTree(
         GeneratedScriptDefinitions.class.getResource(
            "/inetsoft/web/binding/js-functions.generated.json"));
      ObjectNode define = mapper.createObjectNode();

      // a "!define" of the hand written definitions is kept too
      JsonNode own = functions.remove("!define");

      if(own instanceof ObjectNode) {
         define.setAll((ObjectNode) own);
      }

      for(Iterator<Map.Entry<String, JsonNode>> it = generated.fields(); it.hasNext();) {
         Map.Entry<String, JsonNode> e = it.next();

         if("!define".equals(e.getKey())) {
            if(e.getValue() instanceof ObjectNode) {
               define.setAll((ObjectNode) e.getValue());
            }
         }
         else {
            functions.set(e.getKey(), e.getValue());
         }
      }

      return define;
   }

   /**
    * Merge types into the "!define" of the library, keeping what is already there.
    */
   public static void mergeDefine(ObjectNode library, ObjectNode define) {
      JsonNode current = library.get("!define");

      if(current instanceof ObjectNode) {
         ((ObjectNode) current).setAll(define);
      }
      else {
         library.set("!define", define);
      }
   }
}
