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
 * JSON response of {@code POST /api/wiz/v1/admin/repository/export} -- resolves the selection,
 * filters by permission, computes dependents, and creates the export zip synchronously
 * ({@code ExportAssetService.createExport} is confirmed synchronous under the hood), returning
 * {@code exportId} for the immediately-following {@code GET .../export/download/{exportId}} the
 * plugin tool makes to actually stream the bytes -- two HTTP round trips, but still ONE agent-
 * facing MCP tool call (03-reconcile.md: a single tool, not a single HTTP request).
 */
public record RepositoryExportResult(String exportId, String fileName, int exportedCount,
                                     List<String> unresolvedEntities,
                                     List<String> skippedForPermission,
                                     List<String> includedDependencies)
{
}
