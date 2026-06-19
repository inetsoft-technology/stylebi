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
package inetsoft.web.wiz.script.knowledge;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.web.wiz.script.model.FunctionSignature;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.util.*;

/**
 * Serves the generated StyleBI scripting API metadata from
 * {@code js-functions.json} (Tern format).
 *
 * <p>The metadata is loaded once at startup and kept in memory.
 * It describes:</p>
 * <ul>
 *   <li>Top-level built-in functions ({@code formatDate}, {@code runQuery}, etc.)</li>
 *   <li>Prototype methods on JavaScript types ({@code Number.toFixed}, etc.)</li>
 * </ul>
 *
 * <p><strong>Note:</strong> the resource must be regenerated against the modernized
 * JavaScript runtime (Phase 0) so the signatures match the engine's actual surface.</p>
 */
@Service
public class ScriptApiService {

   private static final String RESOURCE_PATH =
      "/inetsoft/web/binding/js-functions.json";

   /** Raw parsed tree: top-level key → {"!type":…, "!url":…, "prototype":{…}} */
   private Map<String, Map<String, Object>> tree;

   @PostConstruct
   void load() {
      try(InputStream is = ScriptApiService.class.getResourceAsStream(RESOURCE_PATH)) {
         if(is == null) {
            tree = Collections.emptyMap();
            return;
         }

         ObjectMapper mapper = new ObjectMapper();
         tree = mapper.readValue(is, new TypeReference<Map<String, Map<String, Object>>>() {});
      }
      catch(Exception e) {
         tree = Collections.emptyMap();
      }
   }

   /**
    * Look up a single function or prototype method by name.
    *
    * <p>Recognised name formats:</p>
    * <ul>
    *   <li>{@code "formatDate"} — a top-level function</li>
    *   <li>{@code "Number.toFixed"} — a prototype method on type {@code Number}</li>
    * </ul>
    *
    * @param name the function or method name (see above)
    * @return the matching {@link FunctionSignature}, or {@code null} when not found
    */
   public FunctionSignature lookup(String name) {
      if(name == null || name.isBlank()) {
         return null;
      }

      if(name.contains(".")) {
         // "Type.method" — look in the prototype
         int dot = name.indexOf('.');
         String typeName = name.substring(0, dot);
         String methodName = name.substring(dot + 1);
         return lookupPrototype(typeName, methodName, name);
      }

      // Top-level function
      Map<String, Object> entry = tree.get(name);

      if(entry == null) {
         return null;
      }

      String type = (String) entry.get("!type");
      String url = (String) entry.get("!url");
      return new FunctionSignature(name, type, url);
   }

   /**
    * Returns the full parsed function tree (the raw Tern structure).
    * Callers should treat this as read-only.
    *
    * @return the parsed metadata; never {@code null}, may be empty if the resource was missing
    */
   public Map<String, Map<String, Object>> tree() {
      return Collections.unmodifiableMap(tree);
   }

   // -------------------------------------------------------------------------
   // Helpers
   // -------------------------------------------------------------------------

   @SuppressWarnings("unchecked")
   private FunctionSignature lookupPrototype(String typeName, String methodName, String fullName) {
      Map<String, Object> typeEntry = tree.get(typeName);

      if(typeEntry == null) {
         return null;
      }

      Object protoObj = typeEntry.get("prototype");

      if(!(protoObj instanceof Map)) {
         return null;
      }

      Map<String, Object> prototype = (Map<String, Object>) protoObj;
      Object methodObj = prototype.get(methodName);

      if(!(methodObj instanceof Map)) {
         return null;
      }

      Map<String, Object> methodEntry = (Map<String, Object>) methodObj;
      String type = (String) methodEntry.get("!type");
      String url = (String) methodEntry.get("!url");
      return new FunctionSignature(fullName, type, url);
   }
}
