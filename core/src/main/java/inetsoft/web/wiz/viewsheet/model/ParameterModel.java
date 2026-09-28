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
package inetsoft.web.wiz.viewsheet.model;

import java.util.List;

/**
 * One variable (parameter) the connected viewsheet's source or viewsheet tree references, as the
 * agent sees it. {@code choices}/{@code values}/{@code currentValue} are {@code null} when not
 * applicable — {@code null} rather than an empty list distinguishes "no enumerated picker" from
 * "an empty one". {@code values}, when present, is index-paired with {@code choices}: {@code
 * values.get(i)} is the real, matchable value behind the human-facing label {@code
 * choices.get(i)} -- a caller should submit an entry from {@code values} to
 * {@code set_parameters}, not the label from {@code choices}.
 */
public record ParameterModel(String name, String label, String type, boolean multipleSelection,
                             boolean boundToInputAssembly, List<Object> choices,
                             List<Object> values, List<Object> currentValue) {
}
