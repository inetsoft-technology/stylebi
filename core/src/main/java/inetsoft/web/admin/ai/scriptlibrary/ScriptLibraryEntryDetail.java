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
 * A single Script Library entry's full detail, including its body text -- the
 * {@code get_script_library_entry} response element. {@code text} is the same value
 * {@code composer-chat}'s own {@code read_script_library_function} exposes; this area does not
 * offer a way to WRITE it (only {@code name}/{@code description}, matching Enterprise Manager's
 * own Script Library settings-page editor, {@code ScriptSettingsModel}) -- editing a script's
 * body is composer-chat's capability, not admin-chat's.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ScriptLibraryEntryDetail(String name, String description, String text) {
}
