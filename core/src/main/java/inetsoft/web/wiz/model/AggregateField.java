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

package inetsoft.web.wiz.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * One aggregate field of a worksheet table, mirroring the TypeScript {@code AggregateField} shape.
 * <ul>
 *   <li>{@code formula}        – aggregate function name (e.g. "Sum", "Count", "DistinctCount").</li>
 *   <li>{@code alias}          – renamed result column; null when not renamed.</li>
 *   <li>{@code secondaryField} – second column for two-column formulas (Correlation, WeightedAverage…).</li>
 *   <li>{@code n}              – N parameter for Nth/Pth formulas (NthLargest, PthPercentile…).</li>
 * </ul>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AggregateField(String fieldName, String formula, String alias,
                             String secondaryField, Integer n)
{
}
