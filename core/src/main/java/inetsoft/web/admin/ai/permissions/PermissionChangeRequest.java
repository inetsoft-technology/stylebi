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
package inetsoft.web.admin.ai.permissions;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * One requested permission-grant change: create/update/delete a single grant on a resource. See
 * {@code 01-spec.md} section 1 for the verb set and section 11 for the tool-layer validation this
 * request shape assumes has already run (verb aliasing, resource-type allowlist, {@code actions}
 * rejected on a delete) -- {@link PermissionChangePlanService#resolve} re-validates independently
 * rather than trusting the caller, per this repo's CLAUDE.md tool-robustness rule.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class PermissionChangeRequest {
   public static final String VERB_CREATE = "create";
   public static final String VERB_UPDATE = "update";
   public static final String VERB_DELETE = "delete";

   public String getVerb() { return verb; }
   public void setVerb(String v) { this.verb = v; }

   public String getResourceType() { return resourceType; }
   public void setResourceType(String v) { this.resourceType = v; }

   public String getResourcePath() { return resourcePath; }
   public void setResourcePath(String v) { this.resourcePath = v; }

   /** {@code USER}/{@code GROUP}/{@code ROLE}/{@code ORGANIZATION} -- all four are valid on every
    * verb (spec section 2's correction: {@code Identity.Type} includes {@code ORGANIZATION}). */
   public String getIdentityType() { return identityType; }
   public void setIdentityType(String v) { this.identityType = v; }

   /** A bare identity name (org defaults to the caller's) or an {@code IdentityID}-key-shaped
    * string ({@code name:orgId}) -- parsed the same way {@code IdentityID.getIdentityIDFromKey}
    * parses it. */
   public String getIdentityId() { return identityId; }
   public void setIdentityId(String v) { this.identityId = v; }

   /** Required for {@code create}/{@code update}; must be {@code null}/empty for {@code delete}
    * (spec section 11 -- rejected loud, not silently ignored). */
   public List<String> getActions() { return actions; }
   public void setActions(List<String> v) { this.actions = v; }

   private String verb;
   private String resourceType;
   private String resourcePath;
   private String identityType;
   private String identityId;
   private List<String> actions;
}
