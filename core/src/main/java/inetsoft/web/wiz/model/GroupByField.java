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
 * One GROUP BY field of a worksheet table, mirroring the TypeScript {@code GroupByField} shape.
 * {@code dateGroupLevel} is the date grouping name (e.g. "year", "month") when the group is a
 * date-range grouping; null otherwise.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GroupByField(String fieldName, String dateGroupLevel) {
}
