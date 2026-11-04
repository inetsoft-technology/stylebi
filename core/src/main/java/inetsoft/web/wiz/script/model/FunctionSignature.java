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
 * Best-effort result of {@code get_function_signature} (the static "Layer A" metadata lookup).
 * Deliberately minimal for this slice — see {@code ScriptApiService} javadoc.
 *
 * <p>{@code didYouMean} is only set on a miss whose name matches a catalogue entry ignoring case:
 * a {@code String} (the canonical spelling) for one match, a sorted {@code List<String>} for
 * several. It is a hint only; {@code found} stays false.
 */
public record FunctionSignature(String name, boolean found, String type, String url, boolean sample,
                                @JsonInclude(JsonInclude.Include.NON_NULL) Object didYouMean) {
   public FunctionSignature(String name, boolean found, String type, String url, boolean sample) {
      this(name, found, type, url, sample, null);
   }
}
