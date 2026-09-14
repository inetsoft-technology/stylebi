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
package inetsoft.web.admin.ai.file;

/**
 * Response for {@code get_stored_asset} -- metadata only, no content (01-design.md section 6.2).
 * {@code sizeBytes}/{@code lastModified}/{@code editableAsText} are present for a file, {@code
 * null} for a folder.
 */
public record StoredAssetNode(String path, String name, boolean folder, Long sizeBytes,
                              String lastModified, Boolean editableAsText)
{
}
