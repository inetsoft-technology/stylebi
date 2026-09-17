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

/**
 * The {@code get_viewsheet_folder} projection (01-spec.md section 3) -- a new read shape backed by
 * {@link ViewsheetFolderService}, since no server-side folder read method exists at all on {@code
 * ViewsheetService}.
 *
 * <p>{@code found: false} is a normal answer, not an error (matching C.4's own
 * {@code get_permission_grant} precedent). {@code path}/{@code owner} echo back the NORMALIZED
 * values ({@link ViewsheetFolderService#normalizeFolderPath}) actually resolved against the
 * registry, not necessarily the caller's raw input verbatim.
 */
public record GetViewsheetFolderResult(boolean found, String path, String owner, String alias,
                                       String description)
{
}
