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
package inetsoft.web.admin.ai.providers;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One requested provider change: create or delete one named entry in one chain (01-spec.md section
 * 1/11). {@link ProviderChangePlanService#resolve} re-validates every field independently rather
 * than trusting the caller, per this repo's CLAUDE.md tool-robustness rule -- verb/chain aliasing
 * (if any) is the plugin (TypeScript) tool layer's job, matching
 * {@code IdentityChangePlanService.requireVerb}/{@code requireUnitType}'s own precedent of
 * exact-label-only validation in Java.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProviderChangeRequest {
   public static final String VERB_CREATE = "create";
   public static final String VERB_DELETE = "delete";

   public String getVerb() { return verb; }
   public void setVerb(String v) { this.verb = v; }

   public String getChain() { return chain; }
   public void setChain(String v) { this.chain = v; }

   /** Required for both verbs: for {@code create}, the id the new provider will have; for
    * {@code delete}, the existing provider's id. */
   public String getName() { return name; }
   public void setName(String v) { this.name = v; }

   /** Required for {@code create} ({@code "FILE"} or, authentication-chain only, {@code "LDAP"});
    * rejected for {@code delete} (01-spec.md section 11). */
   public String getProviderType() { return providerType; }
   public void setProviderType(String v) { this.providerType = v; }

   /** Required for {@code providerType: "LDAP"}; rejected otherwise, including for {@code delete}. */
   public ProviderLdapSpec getSpec() { return spec; }
   public void setSpec(ProviderLdapSpec v) { this.spec = v; }

   private String verb;
   private String chain;
   private String name;
   private String providerType;
   private ProviderLdapSpec spec;
}
