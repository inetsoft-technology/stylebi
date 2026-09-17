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
package inetsoft.web.admin.ai.general;

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
 * Small Jackson-tree helpers shared by {@link GeneralSubModel} (field-name introspection) and
 * {@link GeneralChangePlanService} (JSON projection/merge/masking) -- the direct counterpart of
 * {@code PresentationJson}, kept in one place so both use the identical {@link ObjectMapper}
 * configuration rather than two independently-constructed ones drifting apart.
 *
 * <p>A plain (non-enum) class deliberately, so {@link #MAPPER} initializes before
 * {@link GeneralSubModel}'s enum constants can call {@link #fieldNames} from their own
 * constructors -- referencing this class triggers its static initialization first, avoiding the
 * "enum constants run before the rest of the enum's own static fields" ordering hazard a shared
 * static field inside the enum itself would hit.
 */
final class GeneralJson {
   private GeneralJson() {
   }

   static final ObjectMapper MAPPER = new ObjectMapper();

   /**
    * The four {@code email} fields this area withholds on read and refuses on write.
    *
    * <p>These are exactly the four {@code mail.*} names {@code AdminPropertyCatalog}'s
    * {@code ENCRYPTED_CREDENTIALS} already covers on the generic properties path --
    * {@code mail.smtp.pass}, {@code mail.smtp.clientsecret}, {@code mail.smtp.accesstoken},
    * {@code mail.smtp.refreshtoken}. Keeping the two surfaces in agreement is the point: that
    * catalog tells an agent, as instruction text, that admin-chat will not write these values, and
    * a sibling verb in this package that still wrote them would make that a false statement --
    * the same class of inconsistency Bug #76170 was.
    *
    * <p><b>{@code smtpTokenUri} is deliberately absent.</b> {@code EmailSettingsService.getModel}
    * reads it with {@code SreeEnv.getPassword} but {@code setModel} writes it with a plain
    * {@code SreeEnv.setProperty}, so by the catalog's own rule -- classify by the WRITER, never by
    * the reader -- it is an ordinary property that merely sits beside the credentials. Do not
    * "fix" this by adding it: masking it would withhold an OAuth endpoint URL that EM displays in
    * the clear, and refusing it would make the {@code SASL_XOAUTH2} auth type unconfigurable.
    *
    * <p>{@code smtpSecretId} is likewise absent: under cloud secrets it holds the <i>name</i> of a
    * secret, not the secret. It is refused on write by a separate cloud-secrets rule
    * ({@link GeneralChangePlanService}), not masked on read.
    */
   static final Set<String> EMAIL_SECRET_FIELDS =
      Set.of("smtpPassword", "smtpClientSecret", "smtpAccessToken", "smtpRefreshToken");

   static final String SECRET_MASK = "********";

   /** The exact JSON property names Jackson would (de)serialize for {@code modelClass}, i.e. the
    * complete, valid {@code spec} field set for one sub-model -- mechanically derived from the
    * model interface itself so it can never drift from what the interface declares.
    *
    * <p>Reflection over the interface's own no-arg instance methods, not Jackson's
    * {@code BeanDescription} introspection: on a bare Immutables-style interface the latter does
    * not follow {@code @JsonSerialize(as = ImmutableXModel.class)} to the generated class and
    * returns no properties at all. Identical to {@code PresentationJson.fieldNames}. */
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
    * {@code current} wholesale, never deep-merged. Correct for the scalar fields that dominate
    * this area and required for its one list-valued field, {@code localization.locales}, which
    * {@code LocalizationSettingsService.setModel} rebuilds as a whole (see
    * {@code GeneralChangePlanService.requireWholeLocaleList}). */
   static JsonNode merge(JsonNode current, JsonNode spec) {
      ObjectNode merged = current.deepCopy();
      Iterator<Map.Entry<String, JsonNode>> fields = spec.fields();

      while(fields.hasNext()) {
         Map.Entry<String, JsonNode> field = fields.next();
         merged.set(field.getKey(), field.getValue());
      }

      return merged;
   }

   /** Masks {@code subModel}'s secret-classified fields wherever a value is about to be shown to a
    * caller (a GET response, or a plan/audit {@code before}/{@code after} projection) -- never on
    * the node that is about to be deserialized back into a model and written, only on display
    * copies.
    *
    * <p>Takes the sub-model rather than a field set so that every caller that projects a value for
    * display gets the masking that sub-model declares, without restating which sub-models have
    * secrets. A sub-model with none returns the node unchanged. */
   static JsonNode maskSecrets(GeneralSubModel subModel, JsonNode node) {
      Set<String> fields = subModel.secretFields();

      if(fields.isEmpty()) {
         return node;
      }

      ObjectNode copy = node.deepCopy();

      for(String field : fields) {
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
         throw new IllegalStateException("failed to serialize a general sub-model value", e);
      }
   }
}
