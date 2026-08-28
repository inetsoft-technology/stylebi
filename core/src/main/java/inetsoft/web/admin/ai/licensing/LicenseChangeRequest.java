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
package inetsoft.web.admin.ai.licensing;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One requested license key change: add or remove one key by its literal string (01-spec.md
 * section 1/11). {@link LicenseChangePlanService#resolve} re-validates every field independently
 * rather than trusting the caller, per this repo's CLAUDE.md tool-robustness rule -- verb aliasing
 * (e.g. "install"/"uninstall") and the "no update verb exists" refusal are the plugin (TypeScript)
 * tool layer's job, matching {@code ClusterChangePlanService.requireVerb}'s own precedent of
 * exact-label-only validation in Java.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class LicenseChangeRequest {
   public static final String VERB_ADD = "add";
   public static final String VERB_REMOVE = "remove";

   public String getVerb() { return verb; }
   public void setVerb(String v) { this.verb = v; }

   /** The literal license key string -- the only identifier this area has (01-spec.md section 2). */
   public String getKey() { return key; }
   public void setKey(String v) { this.key = v; }

   private String verb;
   private String key;
}
