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
package inetsoft.uql.tabular;

/**
 * One column of a dataset.
 *
 * @param name the column name as the source reports it. Non-blank.
 * @param type any {@link inetsoft.uql.schema.XSchema} type constant — e.g. XSchema.STRING, LONG,
 *             DOUBLE, DATE, TIME_INSTANT, TIME, BOOLEAN. The names listed here are illustrative,
 *             not exhaustive: the actual, closed vocabulary is every {@code public static final
 *             String} constant {@code XSchema} itself declares (P5 review r3 — an earlier trailing
 *             "..." on this list was read as open-ended and cost a full round to resolve; it is
 *             not open-ended). The connector maps its own native type onto this vocabulary; the
 *             native type name does not cross this boundary.
 */
public record TabularColumn(String name, String type) {}
