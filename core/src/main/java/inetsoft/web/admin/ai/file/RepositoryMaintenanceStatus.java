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

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Response shape shared by {@code get_rebuild_dependencies_status}/
 * {@code get_repair_repository_folders_status}. {@code error}, when present, is always a short,
 * clean message -- never the raw stack-trace string {@code FileService}'s own status DTOs put
 * in their {@code error} field.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RepositoryMaintenanceStatus(String token, boolean complete, boolean failed,
                                          String error)
{
}
