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
package inetsoft.web.admin.ai.identities;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One requested identity change: create or delete one user/group/role/organization. See
 * {@code 01-spec.md} section 1 for the verb set and section 11 for the tool-layer validation this
 * request shape assumes has already run (verb/unitType aliasing) --
 * {@link IdentityChangePlanService#resolve} re-validates independently rather than trusting the
 * caller, per this repo's CLAUDE.md tool-robustness rule.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class IdentityChangeRequest {
   public static final String VERB_CREATE = "create";
   public static final String VERB_DELETE = "delete";
   public static final String VERB_UPDATE = "update";

   public String getVerb() { return verb; }
   public void setVerb(String v) { this.verb = v; }

   public String getUnitType() { return unitType; }
   public void setUnitType(String v) { this.unitType = v; }

   /** Required for {@code delete} (a bare name or {@code name:orgId}-shaped key -- organization
    * uses its bare id); forbidden for {@code create}, which derives its own id from {@code spec}. */
   public String getId() { return id; }
   public void setId(String v) { this.id = v; }

   /** Required for {@code create}; forbidden for {@code delete} (spec section 11 -- rejected loud,
    * not silently ignored). */
   public IdentitySpec getSpec() { return spec; }
   public void setSpec(IdentitySpec v) { this.spec = v; }

   private String verb;
   private String unitType;
   private String id;
   private IdentitySpec spec;
}
