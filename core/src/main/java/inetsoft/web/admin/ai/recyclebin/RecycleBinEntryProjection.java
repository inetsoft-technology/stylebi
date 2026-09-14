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

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * One recycle bin entry, projected from a {@code RecycleBin.Entry} into a stable, MCP-facing
 * shape -- the {@code list_recycle_bin_entries}/{@code get_recycle_bin_entry} response element
 * (track-a-recycle-bin/03-reconcile.md).
 *
 * <p>{@code path} is the entry's CURRENT (in-trash) storage key, e.g. {@code "Recycle Bin/<uuid>"}
 * -- the identifier every subsequent {@code get_recycle_bin_entry}/{@code
 * preview_recycle_bin_changes} call must echo back verbatim, never hand-constructed, the same
 * discipline a viewsheet {@code assetId} already follows. {@code originalPath}/{@code
 * originalName}/{@code originalType}/{@code originalScope}/{@code originalOwner} describe what the
 * entry WAS before it was recycled -- {@code originalType} is a stable, enum-shaped string
 * ({@code "dashboard"|"worksheet"|"folder"|"worksheetFolder"}), not {@code RecycleUtils
 * .getTypeLabel}'s own free-text EM-table label.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RecycleBinEntryProjection(String path, String originalPath, String originalName,
                                        String originalType, String originalScope,
                                        String originalOwner, String timestamp)
{
   public static final String TYPE_DASHBOARD = "dashboard";
   public static final String TYPE_WORKSHEET = "worksheet";
   public static final String TYPE_FOLDER = "folder";
   public static final String TYPE_WORKSHEET_FOLDER = "worksheetFolder";

   public static final String SCOPE_GLOBAL = "global";
   public static final String SCOPE_USER = "user";
}
