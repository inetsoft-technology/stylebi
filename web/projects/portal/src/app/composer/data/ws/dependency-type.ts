/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
export enum DependencyType {
   EXPRESSION,
   GROUPING,
   SUBQUERY_FILTER,
   VARIABLE_FILTER,
   INNER_JOIN,
   OUTER_JOIN,
   SECONDARY_JOIN,
   CROSS_JOIN,
   BASE_CONCATENATED_TABLE,
   UNION,
   INTERSECTION,
   MINUS,
   MERGE,
   MIRROR,
   ROTATION,
   UNPIVOT,
   SQL_CONDITION_VARIABLE,
   TABULAR_SUBQUERY,
   VARIABLE_SUBQUERY,
   JOIN
}
