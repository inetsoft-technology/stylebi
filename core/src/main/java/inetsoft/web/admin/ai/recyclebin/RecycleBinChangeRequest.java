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
package inetsoft.web.admin.ai.recyclebin;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One requested change against an already-recycled entry: {@code verb: "restore"|"purge"}, {@code
 * path} (the recycle-bin storage key, never hand-constructed), and {@code overwrite} (restore
 * only). Unlike the Viewsheets area's two-unit-type shape, there is no {@code unitType}
 * discriminator here: a recycle-bin entry's own type ({@code RecycleBin.Entry.getType()}) is
 * intrinsic to the already-recycled item and fully determines server-side dispatch
 * (track-a-recycle-bin/01-design.md section 1, 03-reconcile.md).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class RecycleBinChangeRequest {
   public static final String VERB_RESTORE = "restore";
   public static final String VERB_PURGE = "purge";

   public String getVerb() { return verb; }
   public void setVerb(String v) { this.verb = v; }

   /** The recycle-bin storage key, e.g. {@code "Recycle Bin/<uuid>"}, exactly as returned by
    * {@code list_recycle_bin_entries}/{@code get_recycle_bin_entry}. */
   public String getPath() { return path; }
   public void setPath(String v) { this.path = v; }

   /** {@code verb: "restore"} only: default {@code false}. Passed straight through to {@code
    * RecycleUtils.restoreSheet}/{@code restoreWSFolder}/{@code restoreRepositoryFolder}'s own
    * {@code overwrite} parameter once a destination collision has already been classified/gated at
    * plan-resolve time (section 4 risk 1). Refused (not merely ignored) when set on a {@code purge}
    * entry. */
   public Boolean getOverwrite() { return overwrite; }
   public void setOverwrite(Boolean v) { this.overwrite = v; }

   private String verb;
   private String path;
   private Boolean overwrite;
}
