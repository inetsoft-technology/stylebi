/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.web.admin.ai.presentation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Small Jackson-tree helpers shared by {@link PresentationSubModel} (field-name introspection) and
 * {@link PresentationChangePlanService} (JSON projection/merge/masking) -- kept in one place so both
 * use the identical {@link ObjectMapper} configuration rather than two independently-constructed
 * ones drifting apart.
 *
 * <p>A plain (non-enum) class deliberately, so {@link #MAPPER} initializes before
 * {@link PresentationSubModel}'s enum constants can call {@link #fieldNames} from their own
 * constructors -- referencing this class triggers its static initialization first, avoiding the
 * "enum constants run before the rest of the enum's own static fields" ordering hazard a shared
 * static field inside the enum itself would hit.
 */
final class PresentationJson {
   private PresentationJson() {
   }

   static final ObjectMapper MAPPER = new ObjectMapper();

   /** The caller-visible-when-non-blank field name test used for {@code webMap}'s two secret-classified
    * fields (01-spec.md section 9) -- masked on every read/projection, refused outright in a write
    * {@code spec} (see {@code PresentationChangePlanService.requireNoSecretFields}). */
   static final Set<String> WEB_MAP_SECRET_FIELDS = Set.of("mapboxToken", "googleKey");
   static final String SECRET_MASK = "********";

   /** The exact JSON property names Jackson would (de)serialize for {@code modelClass}, i.e. the
    * complete, valid {@code spec} field set for one sub-model -- mechanically derived from the model
    * interface itself so it can never drift from what the interface actually declares (the same
    * failure mode the two dead {@code PresentationSettingsModel} fields are proof of).
    *
    * <p>Reflection over the interface's own no-arg instance methods, not Jackson's {@code
    * BeanDescription} introspection -- {@code getSerializationConfig().introspect(...)} on the bare
    * interface type does not follow {@code @JsonSerialize(as = ImmutableXModel.class)} to the
    * generated Immutables class, and returns no properties at all for these fluent-accessor
    * interfaces (confirmed: every field name below matches the interface method name verbatim, no
    * {@code get}/{@code is} prefix to strip, exactly what every {@code getModel}/{@code setModel}
    * body already reads/writes under). */
   static Set<String> fieldNames(Class<?> modelClass) {
      Set<String> names = new LinkedHashSet<>();

      for(Method method : modelClass.getMethods()) {
         if(method.getParameterCount() == 0 && !Modifier.isStatic(method.getModifiers()) &&
            method.getDeclaringClass() != Object.class)
         {
            names.add(method.getName());
         }
      }

      return names;
   }

   static JsonNode toNode(Object model) {
      return MAPPER.valueToTree(model);
   }

   static <T> T toModel(JsonNode node, Class<T> modelClass) throws JsonProcessingException {
      return MAPPER.treeToValue(node, modelClass);
   }

   /** Shallow overlay: every top-level field present in {@code spec} replaces the same field in
    * {@code current} wholesale (never deep-merged) -- correct both for scalar fields and for the two
    * list-valued fields this area has ({@code viewsheetToolbar.options}, {@code portalIntegration.
    * tabs}), which 01-spec.md section 5 and 03-reconcile.md Addition 2 both require to be replaced as
    * a whole, never patched element-by-element. */
   static JsonNode merge(JsonNode current, JsonNode spec) {
      ObjectNode merged = current.deepCopy();
      Iterator<Map.Entry<String, JsonNode>> fields = spec.fields();

      while(fields.hasNext()) {
         Map.Entry<String, JsonNode> field = fields.next();
         merged.set(field.getKey(), field.getValue());
      }

      return merged;
   }

   /** Masks {@code webMap.mapboxToken}/{@code googleKey} to a fixed placeholder wherever a value is
    * about to be shown to a caller (a GET response, or a plan/audit {@code before}/{@code after}
    * projection) -- never used on the node that is about to be deserialized back into a model and
    * written, only on display copies (01-spec.md section 9, "refuse to return ... the real value"). */
   static JsonNode maskWebMapSecrets(JsonNode node) {
      ObjectNode copy = node.deepCopy();

      for(String field : WEB_MAP_SECRET_FIELDS) {
         JsonNode value = copy.get(field);

         if(value != null && value.isTextual() && !value.asText().isEmpty()) {
            copy.put(field, SECRET_MASK);
         }
      }

      return copy;
   }

   static String writeString(JsonNode node) {
      try {
         return MAPPER.writeValueAsString(node);
      }
      catch(JsonProcessingException e) {
         throw new IllegalStateException("failed to serialize a presentation sub-model value", e);
      }
   }
}
