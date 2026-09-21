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
package inetsoft.web.admin.ai.schedule;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Response body for {@code GET /api/wiz/v1/admin/schedule/cycles} (list) and {@code
 * .../cycles/{name}} (read one). Unlike {@link ScheduleFolderView}, there is no {@code found}
 * sentinel here -- a nonexistent name is a 404 ({@code MissingResourceException}, design §3.2),
 * translated by the wiz tool layer into {@code found:false}, not a 200 with an empty body.
 *
 * <p>No top-level {@code timeZone}/{@code label} field (design §1.1, corrected charter assertions
 * 1/3): a cycle's only top-level identity is {@code name}; each entry in {@code conditions}
 * carries its own optional {@code timeZone}, identical to a schedule task's own condition shape.
 * {@code dependentMvNames} is {@code []} whenever {@code inUse} is {@code false}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ScheduleCycleView(String name, List<inetsoft.web.api.schedule.TimeCondition> conditions,
                                boolean inUse, List<String> dependentMvNames)
{
}
