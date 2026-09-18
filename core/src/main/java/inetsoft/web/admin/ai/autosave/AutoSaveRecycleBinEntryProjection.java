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

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * One Auto Save Recycle Bin entry -- a crash/disconnect recovery copy of an in-progress
 * viewsheet/worksheet edit, projected from an {@code AutoSaveUtils} storage key into a stable,
 * MCP-facing shape. {@code id} is the entry's own storage key (recycle-prefix already stripped by
 * {@code AutoSaveUtils.getName}) -- the identifier every subsequent
 * {@code get_autosave_entry}/{@code preview_autosave_changes} call must echo back verbatim, never
 * hand-constructed, the same discipline a recycle-bin {@code path} or a viewsheet {@code assetId}
 * already follows. {@code path} is the ORIGINAL asset path the draft was being edited at,
 * de-escaped from {@code Tool.normalizeFileName}'s own encoding (see
 * {@code AutoSaveRecycleBinService.denormalizeAssetName}).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AutoSaveRecycleBinEntryProjection(String id, String type, String path,
                                                String scope, String owner, String timestamp)
{
   public static final String TYPE_DASHBOARD = "dashboard";
   public static final String TYPE_WORKSHEET = "worksheet";

   public static final String SCOPE_GLOBAL = "global";
   public static final String SCOPE_USER = "user";
}
