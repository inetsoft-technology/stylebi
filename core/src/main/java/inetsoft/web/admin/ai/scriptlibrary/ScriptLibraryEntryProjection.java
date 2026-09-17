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
package inetsoft.web.admin.ai.scriptlibrary;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * One Script Library entry, projected from {@code LibManager} into a stable, MCP-facing shape --
 * the {@code list_script_library} response element. Deliberately omits the script's own body
 * text (unlike {@link ScriptLibraryEntryDetail}) so a listing of a library with many/large
 * functions stays cheap -- the same "list is lightweight, get is full" split
 * {@code RecycleBinEntryProjection}/{@code GetRecycleBinEntryResult} already establish.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ScriptLibraryEntryProjection(String name, String description) {
}
