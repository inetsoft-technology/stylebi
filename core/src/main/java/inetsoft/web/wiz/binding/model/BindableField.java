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
 * One bindable column.
 *
 * <p>Carried a {@code role} ("dimension"/"measure") that was hardcoded {@code null} at its only
 * construction site and never populated for any column. A field that is always null is worse than
 * an absent one: it reads as "this column has no role" rather than "this API does not report one".
 * The binding tree does not distinguish the two, so there is nothing to populate it from.
 */
public record BindableField(String column, String dataType) {}
