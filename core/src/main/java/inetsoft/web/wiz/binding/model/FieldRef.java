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
package inetsoft.web.wiz.binding.model;

/**
 * The agent-facing shape of one bound field, shared by every binding spec (2a–2e) and
 * embedded by highlights in spec #4.
 *
 * <p>{@code type} is the mandatory discriminator — {@code "dimension"} or {@code "measure"}.
 * A reference without it is exactly the input that gets coerced onto the wrong shelf and
 * renders plausibly wrong, so it is never defaulted.
 *
 * @param column     the column name, as it appears in the binding tree
 * @param type       "dimension" or "measure"
 * @param aggregate  aggregate formula, measures only (e.g. "Sum", "Count")
 * @param dateLevel  date grouping level, dimensions only
 * @param namedGroup named-group name, dimensions only
 */
public record FieldRef(String column, String type, String aggregate, String dateLevel,
                       String namedGroup) {}
