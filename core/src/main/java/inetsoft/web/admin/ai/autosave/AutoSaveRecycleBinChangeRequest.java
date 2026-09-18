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
package inetsoft.web.admin.ai.autosave;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One requested change against an Auto Save Recycle Bin entry: {@code verb: "restore"|"delete"},
 * {@code id} (the entry's storage key, never hand-constructed), {@code assetName}
 * ({@code restore} only, optional -- defaults to the entry's own original path), and
 * {@code overwrite} ({@code restore} only).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AutoSaveRecycleBinChangeRequest {
   public static final String VERB_RESTORE = "restore";
   public static final String VERB_DELETE = "delete";

   public String getVerb() { return verb; }
   public void setVerb(String v) { this.verb = v; }

   /** The entry's storage key, exactly as returned by {@code list_autosave_entries}/
    * {@code get_autosave_entry}. */
   public String getId() { return id; }
   public void setId(String v) { this.id = v; }

   /** {@code verb: "restore"} only, optional -- the full destination asset path. Defaults to the
    * entry's own original path (de-normalized from its storage key) when omitted. */
   public String getAssetName() { return assetName; }
   public void setAssetName(String v) { this.assetName = v; }

   /** {@code verb: "restore"} only, default {@code false}. If something already occupies the
    * destination, {@code overwrite: false} refuses the plan loud; {@code overwrite: true} accepts
    * the collision but PERMANENTLY DESTROYS whatever currently occupies that path. Refused
    * (not merely ignored) when set on a {@code delete} entry. */
   public Boolean getOverwrite() { return overwrite; }
   public void setOverwrite(Boolean v) { this.overwrite = v; }

   private String verb;
   private String id;
   private String assetName;
   private Boolean overwrite;
}
