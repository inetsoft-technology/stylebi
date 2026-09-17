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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * One requested sub-model change: {@code update} one of the sub-models in {@link GeneralSubModel}
 * by short name. {@code spec} is a partial-field object, merged onto the sub-model's current value
 * by {@link GeneralChangePlanService} -- except {@code localization.locales}, which must always be
 * the whole list because its writer rebuilds the entire locale set from what it is given -- a list
 * that drops a configured locale is refused unless {@link #getAcknowledgeLocaleRemoval()} is true.
 *
 * <p><b>{@link #getScope()} and {@link #getOrgId()} exist only to be refused.</b> This area has no
 * scope concept at all: every service behind it writes global {@code SreeEnv} state and takes no
 * org argument, so there is nothing for either field to select. They are declared rather than
 * swallowed by {@code ignoreUnknown} because the two failure modes are not equally bad. A caller
 * that sends {@code scope: "organization"} believing it is confining a change to one tenant, and
 * gets a silent success that in fact changed the setting for every tenant, has been actively
 * misled -- and {@code ignoreUnknown = true} produces exactly that. Binding the names and
 * rejecting them in the plan service turns a silent global write into a loud refusal that tells
 * the caller what this area actually does.
 *
 * <p>{@code ignoreUnknown} is still on for genuinely unrecognized keys, matching every sibling
 * area; it is these two specific names, which a caller has a concrete reason to believe in, that
 * get the explicit treatment.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class GeneralChangeRequest {
   public static final String VERB_UPDATE = "update";

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

   /** Always refused when non-null -- see this class's javadoc. */
   public String getScope() {
      return scope;
   }

   public void setScope(String v) {
      this.scope = v;
   }

   /** Always refused when non-null -- see this class's javadoc. */
   public String getOrgId() {
      return orgId;
   }

   public void setOrgId(String v) {
      this.orgId = v;
   }

   public JsonNode getSpec() {
      return spec;
   }

   public void setSpec(JsonNode v) {
      this.spec = v;
   }

   /**
    * Permits a {@code localization.locales} list that drops a currently-configured locale.
    *
    * <p>Required, and required to be exactly {@code true}, only when the submitted list actually
    * omits one; on every other change it is meaningless and simply ignored. That narrowness is the
    * point, and it is why this flag exists where the area deliberately has no blanket
    * {@code acknowledgeIrreversibleUpdate}: an acknowledgement every plan can satisfy trains a
    * caller to pass it unread, while one that is only ever needed when a locale is genuinely being
    * deleted still means something at the moment it is asked for. Same reasoning as
    * {@code GeneralActionRequest.acknowledgeIrreversibleAction}.
    *
    * <p>Not part of the plan hash: it authorizes a change rather than describing one, so two
    * otherwise-identical plans must hash alike. It has to be sent on the apply as well as the
    * preview, because apply re-resolves the changeset from the request body.
    */
   public Boolean getAcknowledgeLocaleRemoval() {
      return acknowledgeLocaleRemoval;
   }

   public void setAcknowledgeLocaleRemoval(Boolean v) {
      this.acknowledgeLocaleRemoval = v;
   }

   private String verb;
   private String subModel;
   private String scope;
   private String orgId;
   private JsonNode spec;
   private Boolean acknowledgeLocaleRemoval;
}
