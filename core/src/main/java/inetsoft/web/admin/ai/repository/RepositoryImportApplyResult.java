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

import java.util.List;

/**
 * Result of {@code POST .../import/apply}. {@code status} is {@code "applied"} or {@code
 * "failed"} only -- import has no live inverse in this cut (01-design.md section 6: {@code
 * DeployService.importAsset} is not decomposed into independently-undoable steps the way a
 * multi-entry properties/schedule plan is), so there is no {@code "rolled-back"}/{@code
 * "rollback-failed"} status the way {@code AdminChangesetApplyService}'s own {@code ApplyResult}
 * has -- the Tier-2 snapshot taken for this apply (backupRef) is the only recovery path.
 */
public record RepositoryImportApplyResult(String transactionId, String status, String backupRef,
                                          List<String> failedAssets,
                                          List<String> ignoreUserAssets)
{
}
