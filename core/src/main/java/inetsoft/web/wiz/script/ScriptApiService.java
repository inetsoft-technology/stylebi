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
package inetsoft.web.wiz.script;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import inetsoft.web.wiz.script.model.FunctionSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Best-effort "Layer A" static metadata lookup (the generated Tern {@code js-functions} JSON the
 * script editor's autocomplete already uses). Deliberately minimal for this slice — this covers
 * only the statically-generated global/type function signatures; it does NOT cover a chart
 * assembly's dynamically-registered scriptable members (see {@link ScriptContextService} for
 * that "Layer B" surface, which is what the chart-customization use case actually needs).
 */
@Service
public class ScriptApiService {
   private static final Logger LOG = LoggerFactory.getLogger(ScriptApiService.class);
   private static final String RESOURCE_FUNCTIONS = "/inetsoft/web/binding/js-functions.json";
   private static final String RESOURCE_GENERATED = "/inetsoft/web/binding/js-functions.generated.json";

   public ScriptApiService() {
   }

   /**
    * Test seam: uses the given, already merged, catalogue root instead of loading the resources.
    */
   ScriptApiService(JsonNode root) {
      this.root = root;
   }

   /**
    * Looks up a top-level function ({@code "dateAdd"}) or a prototype method
    * ({@code "Type.method"}, e.g. {@code "AreaElement.setBorderColor"}).
    * Returns {@code found=false} rather than throwing when nothing matches; a miss whose name
    * equals a catalogue entry ignoring case carries that canonical spelling as
    * {@code didYouMean} (a String for one match, a sorted List for several).
    */
   public FunctionSignature lookup(String name) {
      if(name == null || name.isBlank()) {
         return new FunctionSignature(name, false, null, null, false);
      }

      JsonNode r = root();
      int dot = name.indexOf('.');
      JsonNode node;

      if(dot > 0) {
         String type = name.substring(0, dot);
         String method = name.substring(dot + 1);
         JsonNode typeNode = r.get(type);
         node = typeNode == null ? null : typeNode.path("prototype").get(method);
      }
      else {
         node = r.get(name);
      }

      if(node == null || node.isMissingNode()) {
         return new FunctionSignature(name, false, null, null, false, suggest(r, name, dot));
      }

      String type = node.path("!type").asText(null);
      String url = node.path("!url").asText(null);
      boolean sample = node.path("!sample").asBoolean(false);
      return new FunctionSignature(name, true, type, url, sample);
   }

   /**
    * Case-insensitive candidates for a missed name, over the same merged root {@code lookup}
    * reads (so SREE_ONLY names are never suggested). Dotted names only match prototype methods,
    * with both halves compared ignoring case.
    */
   private Object suggest(JsonNode r, String name, int dot) {
      List<String> matches = new ArrayList<>();

      if(dot > 0) {
         String type = name.substring(0, dot);
         String method = name.substring(dot + 1);

         for(Iterator<String> types = r.fieldNames(); types.hasNext();) {
            String t = types.next();

            if(t.startsWith("!") || !t.equalsIgnoreCase(type)) {
               continue;
            }

            for(Iterator<String> methods = r.get(t).path("prototype").fieldNames();
                methods.hasNext();)
            {
               String m = methods.next();

               if(!m.startsWith("!") && m.equalsIgnoreCase(method)) {
                  matches.add(t + "." + m);
               }
            }
         }
      }
      else {
         for(Iterator<String> keys = r.fieldNames(); keys.hasNext();) {
            String k = keys.next();

            if(!k.startsWith("!") && k.equalsIgnoreCase(name)) {
               matches.add(k);
            }
         }
      }

      if(matches.isEmpty()) {
         return null;
      }

      if(matches.size() == 1) {
         return matches.get(0);
      }

      matches.sort(null);
      return matches;
   }

   private synchronized JsonNode root() {
      if(root == null) {
         root = load();
      }

      return root;
   }

   private JsonNode load() {
      try {
         ObjectNode functions = (ObjectNode) readResource(RESOURCE_FUNCTIONS);
         ObjectNode generated = (ObjectNode) readResource(RESOURCE_GENERATED);

         for(Iterator<Map.Entry<String, JsonNode>> it = generated.fields(); it.hasNext();) {
            Map.Entry<String, JsonNode> e = it.next();
            functions.set(e.getKey(), e.getValue());
         }

         // Report/Sree-scoped globals aren't valid in a viewsheet script -- the script editor's
         // own autocomplete (VSScriptableService.createStaticDefinitions) hides them the same way.
         functions.remove(SREE_ONLY);

         // Excel-style CALC functions (day, eomonth, ...) are nested under "CALC" in the source
         // file but are callable bare, unqualified, in a viewsheet script -- mirrors
         // VSScriptableService.createStaticDefinitions, the UI script editor's own resolution of
         // this same static metadata, so a bare lookup here matches what a human actually sees.
         JsonNode calc = functions.get("CALC");

         if(calc != null && calc.isObject()) {
            ObjectNode merged = MAPPER.createObjectNode();
            merged.setAll((ObjectNode) calc);
            merged.setAll(functions);
            return merged;
         }

         return functions;
      }
      catch(Exception e) {
         LOG.warn("Failed to load script API metadata", e);
         return MAPPER.createObjectNode();
      }
   }

   private static final Set<String> SREE_ONLY = new HashSet<>();

   static {
      SREE_ONLY.add("showReplet");
      SREE_ONLY.add("showReport");
      SREE_ONLY.add("showURL");
      SREE_ONLY.add("promptParameters");
      SREE_ONLY.add("sendRequest");
      SREE_ONLY.add("refresh");
      SREE_ONLY.add("reprint");
      SREE_ONLY.add("setChanged");
      SREE_ONLY.add("scrollTo");
      SREE_ONLY.add("showStatus");
      SREE_ONLY.add("dataBinding");
   }

   private JsonNode readResource(String path) throws Exception {
      try(InputStream in = getClass().getResourceAsStream(path)) {
         if(in == null) {
            LOG.warn("Script API metadata resource not found: {}", path);
            return MAPPER.createObjectNode();
         }

         return MAPPER.readTree(in);
      }
   }

   private static final ObjectMapper MAPPER = new ObjectMapper();

   private volatile JsonNode root;
}
