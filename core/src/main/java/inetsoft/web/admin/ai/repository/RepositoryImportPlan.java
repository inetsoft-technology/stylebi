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
package inetsoft.web.admin.ai.repository;

import com.fasterxml.jackson.annotation.JsonInclude;
import inetsoft.web.admin.content.repository.model.BookmarkConflict;
import inetsoft.web.admin.content.repository.model.ExportedAssetsModel;

import java.util.List;

/**
 * Result of {@code POST .../import/preview} -- a real before/after diff (which target assets
 * already exist and will be overwritten vs. created new), not merely an acknowledgement flag
 * (03-reconcile.md: adopts the full preview/apply/planHash/taskToken shape over a leaner
 * {@code requiresAgentSignoff}-only alternative, closing the same confirm-then-swap gap Schedule
 * Tasks' own {@code taskToken} digest exists to close). {@code taskToken} is {@code null} when
 * returned inside a {@code PlanHashMismatchException}/{@code TaskTokenMismatchException} 409 body
 * (mirrors {@code AdminChangesetApplyService}'s own rule: a conflict response must never hand
 * back a fresh token for an unreviewed retry).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RepositoryImportPlan(String stagingToken, ExportedAssetsModel jarInfo,
                                   List<AssetExistenceEntry> willOverwrite,
                                   List<AssetExistenceEntry> willCreate,
                                   List<BookmarkConflict> bookmarkConflicts,
                                   boolean acknowledgeOverwriteRequired,
                                   boolean requiresAgentSignoff, boolean requiresStorageBackup,
                                   String planHash, String taskToken)
{
   public RepositoryImportPlan withoutTaskToken() {
      return new RepositoryImportPlan(stagingToken, jarInfo, willOverwrite, willCreate,
         bookmarkConflicts, acknowledgeOverwriteRequired, requiresAgentSignoff,
         requiresStorageBackup, planHash, null);
   }
}
