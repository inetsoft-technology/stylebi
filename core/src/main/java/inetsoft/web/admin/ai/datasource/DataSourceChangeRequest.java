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
package inetsoft.web.admin.ai.datasource;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

/**
 * One requested data-source change: update or delete a single data source (01-spec.md section 1/
 * 11). {@link DataSourceChangePlanService#resolve} re-validates every field independently rather
 * than trusting the caller, per this repo's CLAUDE.md tool-robustness rule -- verb aliasing (
 * "modify"/"edit" -&gt; update, "remove" -&gt; delete) is accepted here directly (section 11),
 * unlike the exact-label-only precedent providers/schedule set, because this area's own tool
 * layer (plugin/admin) is a separate TypeScript codebase built in parallel and this Java service
 * must not assume it already normalized the verb.
 *
 * <p>{@code spec} is a raw field map, not a typed DTO: section 11 requires an unrecognized field
 * name to be refused loud rather than silently dropped, which needs key-presence detection
 * ({@code Map.containsKey}) to distinguish "field omitted" from "field explicitly set to null" --
 * a typed bean with nullable wrapper fields cannot make that distinction. See
 * {@link DataSourceChangePlanService#resolveUpdate} for the field-by-field validation.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class DataSourceChangeRequest {
   public static final String VERB_UPDATE = "update";
   public static final String VERB_DELETE = "delete";
   /** Bug 76599: a bare data-source folder create, with no other data-source mutation -- unlike
    * {@code update}/{@code delete}, needs no id/name resolution at all (there is no existing data
    * source to resolve; only {@link #getFolderPath()} identifies the target). */
   public static final String VERB_CREATE = "create";

   public String getVerb() { return verb; }
   public void setVerb(String v) { this.verb = v; }

   public String getId() { return id; }
   public void setId(String v) { this.id = v; }

   public String getName() { return name; }
   public void setName(String v) { this.name = v; }

   /** Required for {@code update}; rejected for {@code delete}/{@code create} (section 11). */
   public Map<String, Object> getSpec() { return spec; }
   public void setSpec(Map<String, Object> v) { this.spec = v; }

   /** {@code update} only: required, and must be exactly {@code true}, whenever the resolved
    * current name differs from a submitted {@code spec.name} (03-reconcile.md Addition 1). */
   public Boolean getConfirmRename() { return confirmRename; }
   public void setConfirmRename(Boolean v) { this.confirmRename = v; }

   /** {@code delete} only: default {@code false} (section 0.2/11). */
   public Boolean getForce() { return force; }
   public void setForce(Boolean v) { this.force = v; }

   /** {@code create} only: required -- the full folder path to create (e.g. {@code "A/B"} creates
    * both "A" and "A/B" if missing, the same nested-ancestor behavior {@code update}'s own
    * rename-side-effect folder auto-creation already has). */
   public String getFolderPath() { return folderPath; }
   public void setFolderPath(String v) { this.folderPath = v; }

   private String verb;
   private String id;
   private String name;
   private Map<String, Object> spec;
   private Boolean confirmRename;
   private Boolean force;
   private String folderPath;
}
