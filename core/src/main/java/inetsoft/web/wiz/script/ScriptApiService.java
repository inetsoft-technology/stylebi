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
import inetsoft.web.wiz.script.model.FunctionSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.InputStream;

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
   private static final String RESOURCE = "/inetsoft/web/binding/js-functions.generated.json";

   /**
    * Looks up a top-level function ({@code "dateAdd"}) or a prototype method
    * ({@code "Type.method"}, e.g. {@code "AreaElement.setBorderColor"}).
    * Returns {@code found=false} rather than throwing when nothing matches.
    */
   public FunctionSignature lookup(String name) {
      if(name == null || name.isBlank()) {
         return new FunctionSignature(name, false, null, null);
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
         return new FunctionSignature(name, false, null, null);
      }

      String type = node.path("!type").asText(null);
      String url = node.path("!url").asText(null);
      return new FunctionSignature(name, true, type, url);
   }

   private synchronized JsonNode root() {
      if(root == null) {
         root = load();
      }

      return root;
   }

   private JsonNode load() {
      try(InputStream in = getClass().getResourceAsStream(RESOURCE)) {
         if(in == null) {
            LOG.warn("Script API metadata resource not found: {}", RESOURCE);
            return new ObjectMapper().createObjectNode();
         }

         return new ObjectMapper().readTree(in);
      }
      catch(Exception e) {
         LOG.warn("Failed to load script API metadata", e);
         return new ObjectMapper().createObjectNode();
      }
   }

   private volatile JsonNode root;
}
