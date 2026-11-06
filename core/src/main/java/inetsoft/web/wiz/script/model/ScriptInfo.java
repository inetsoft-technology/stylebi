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
package inetsoft.web.wiz.script.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The result of {@code read_script}.
 *
 * @param pending {@code true} only for a pending (Create Expression) session: the column does not
 *                exist yet, so {@code text} is not an expression. Omitted from the JSON otherwise
 * @param note    explains a pending read; omitted from the JSON otherwise
 */
public record ScriptInfo(String target, String text, boolean enabled,
                         @JsonInclude(JsonInclude.Include.NON_NULL) Boolean pending,
                         @JsonInclude(JsonInclude.Include.NON_NULL) String note)
{
   public ScriptInfo(String target, String text, boolean enabled) {
      this(target, text, enabled, null, null);
   }
}
