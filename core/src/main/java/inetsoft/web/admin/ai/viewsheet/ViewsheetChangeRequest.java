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
package inetsoft.web.admin.ai.viewsheet;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One requested change: either a viewsheet (rename/delete) or a folder (create/delete/rename) --
 * 01-spec.md section 1/11's two-unit-type discriminator. {@link ViewsheetChangePlanService#resolve}
 * re-validates every field independently rather than trusting the caller, per this repo's
 * CLAUDE.md tool-robustness rule.
 *
 * <p>{@code unitType} takes no abbreviation alias (section 11, matching C.4's own deliberate
 * non-aliasing of {@code chain}) -- the two unit types have different key shapes and verb sets, so
 * guessing wrong is unusually costly. {@code verb} accepts the aliases named in section 11
 * ("move"-&gt;rename/"remove"-&gt;delete for viewsheet, "add"-&gt;create/"remove"-&gt;delete for
 * folder).
 *
 * <p>{@code path}/{@code oldPath} are both accepted for folder delete/rename (section 11's
 * {@code RemoveFolderRequest.path} vs {@code RenameFolderRequest.oldPath} naming-inconsistency
 * finding) -- {@link ViewsheetChangePlanService#requireFolderTargetPath} resolves whichever is
 * given, refusing loud if both are given and disagree.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ViewsheetChangeRequest {
   public static final String UNIT_VIEWSHEET = "viewsheet";
   public static final String UNIT_FOLDER = "folder";
   public static final String UNIT_WORKSHEET = "worksheet";

   public static final String VERB_RENAME = "rename";
   public static final String VERB_DELETE = "delete";
   public static final String VERB_CREATE = "create";
   public static final String VERB_UPDATE = "update";

   public String getUnitType() { return unitType; }
   public void setUnitType(String v) { this.unitType = v; }

   public String getVerb() { return verb; }
   public void setVerb(String v) { this.verb = v; }

   /** {@code unitType: "viewsheet"}, both verbs: the asset identifier (section 2). */
   public String getAssetId() { return assetId; }
   public void setAssetId(String v) { this.assetId = v; }

   /** {@code unitType: "viewsheet", verb: "rename"}: the new path. Also {@code unitType: "folder",
    * verb: "rename"}: the new folder path. */
   public String getNewPath() { return newPath; }
   public void setNewPath(String v) { this.newPath = v; }

   /** {@code unitType: "viewsheet", verb: "rename"}: required, {@code true} for global scope. */
   public Boolean getGlobal() { return global; }
   public void setGlobal(Boolean v) { this.global = v; }

   /** A bare identity name or a {@code name:orgId}-shaped key (a plain colon, the human/
    * tool-facing wire format -- see {@link ViewsheetFolderService#parseOwner}, never {@code
    * IdentityID}'s own internal {@code "~;~"}-delimited {@code convertToKey()} format). Used by
    * both unit types: viewsheet rename's owner (required when {@code global: false}, rejected when
    * {@code global: true}), and any folder verb's optional owner. */
   public String getOwner() { return owner; }
   public void setOwner(String v) { this.owner = v; }

   /** {@code unitType: "viewsheet", verb: "delete"}: default {@code false} (section 0.2/11).
    * Also {@code unitType: "folder", verb: "delete"}: default {@code false}, required when the
    * folder contains any viewsheet/worksheet (bug #76469) -- folder delete is NOT metadata-only. */
   public Boolean getForce() { return force; }
   public void setForce(Boolean v) { this.force = v; }

   /** {@code unitType: "folder", verb: "create"}: optional, root if omitted. */
   public String getParentFolder() { return parentFolder; }
   public void setParentFolder(String v) { this.parentFolder = v; }

   /** {@code unitType: "folder", verb: "create"}: required. */
   public String getFolderName() { return folderName; }
   public void setFolderName(String v) { this.folderName = v; }

   /** {@code unitType: "folder"}, {@code verb: "delete"|"rename"}: the folder's current path.
    * Aliased with {@code oldPath} -- either name is accepted. */
   public String getPath() { return path; }
   public void setPath(String v) { this.path = v; }

   /** Alias for {@link #getPath()}, matching {@code RenameFolderRequest.oldPath}'s own field
    * name in the wrapped Public API (section 11). */
   public String getOldPath() { return oldPath; }
   public void setOldPath(String v) { this.oldPath = v; }

   /** {@code verb: "update"} (any unitType), also {@code unitType: "folder", verb: "create"}
    * (Track B/Redmine #76604 Gap5): the new display alias. {@code null} (omitted on the wire)
    * means leave unchanged; an explicit empty string clears it -- this distinction is enforced by
    * the plugin's own normalizer (the Java primitive treats any non-null value as a literal
    * replacement either way), see {@code ViewsheetChangePlanService}'s update resolvers. At
    * least one of {@code alias}/{@code description} is required for {@code verb: "update"}. */
   public String getAlias() { return alias; }
   public void setAlias(String v) { this.alias = v; }

   /** Same semantics as {@link #getAlias()}, for the description field. */
   public String getDescription() { return description; }
   public void setDescription(String v) { this.description = v; }

   private String unitType;
   private String verb;
   private String assetId;
   private String newPath;
   private Boolean global;
   private String owner;
   private Boolean force;
   private String parentFolder;
   private String folderName;
   private String path;
   private String oldPath;
   private String alias;
   private String description;
}
