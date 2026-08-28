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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * One requested sub-model change: {@code update} one of the 16 sub-models by short name
 * (01-spec.md section 5/11). {@code scope} is deliberately {@code "global"|"organization"}, never an
 * {@code orgId} -- the underlying controller has no way to target a different org's settings at all
 * (01-spec.md section 1); a caller-invented {@code orgId} field would be accepted by nothing
 * underneath and silently ignored, exactly the trap CLAUDE.md's tool-misuse section names.
 *
 * <p>{@code spec} is a partial-field object, merged onto the sub-model's current value
 * ({@link PresentationChangePlanService}) -- except {@code viewsheetToolbar.options} and
 * {@code portalIntegration.tabs}, which must always be the whole list (01-spec.md section 5,
 * 03-reconcile.md Addition 2).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class PresentationChangeRequest {
   public static final String VERB_UPDATE = "update";
   public static final String SCOPE_GLOBAL = "global";
   public static final String SCOPE_ORGANIZATION = "organization";

   public String getVerb() {
      return verb;
   }

   public void setVerb(String v) {
      this.verb = v;
   }

   public String getSubModel() {
      return subModel;
   }

   public void setSubModel(String v) {
      this.subModel = v;
   }

   public String getScope() {
      return scope;
   }

   public void setScope(String v) {
      this.scope = v;
   }

   public JsonNode getSpec() {
      return spec;
   }

   public void setSpec(JsonNode v) {
      this.spec = v;
   }

   private String verb;
   private String subModel;
   private String scope;
   private JsonNode spec;
}
