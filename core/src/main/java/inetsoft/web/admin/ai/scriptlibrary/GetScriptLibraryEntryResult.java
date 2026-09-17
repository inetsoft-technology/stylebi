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
 * Response body for {@code GET /api/wiz/v1/admin/script-library/entry}. {@code found: false} is a
 * normal 200 answer, not an error -- matching {@code get_recycle_bin_entry}/
 * {@code get_viewsheet_folder}'s own precedent -- in which case every field below {@code found}
 * is {@code null}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GetScriptLibraryEntryResult(boolean found, String name, String description,
                                          String text)
{
   public static GetScriptLibraryEntryResult notFound() {
      return new GetScriptLibraryEntryResult(false, null, null, null);
   }

   public static GetScriptLibraryEntryResult of(ScriptLibraryEntryDetail detail) {
      return new GetScriptLibraryEntryResult(true, detail.name(), detail.description(),
                                             detail.text());
   }
}
