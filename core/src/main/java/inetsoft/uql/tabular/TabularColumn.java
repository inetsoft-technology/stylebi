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
 *             String} constant {@code XSchema} itself declares. The connector maps its own native
 *             type onto this vocabulary; the native type name does not cross this boundary.
 * @param description the column's own description as the source declares it, verbatim — not a
 *                     sentence the connector composes. {@code null} when the source declares
 *                     nothing, never an empty string standing in for "nothing". Where "nothing" is
 *                     represented is source-specific (a protobuf getter never returns null, so a
 *                     connector reading one must translate its own empty string to {@code null}
 *                     here); that translation belongs to the connector, which is the only party
 *                     that knows its source's convention.
 * @param label the column's own display name as the source declares it. {@code null} when blank.
 *              May equal {@code name} — that is still a true statement about the source.
 * @param isDimension three-valued. {@code TRUE}/{@code FALSE} only when the source itself sorts
 *                    this column into one of two disjoint dimension/measure lists; {@code null}
 *                    when the source does not say. Deliberately not an inference: a connector must
 *                    not guess from the column's type (numeric therefore a measure, or similar).
 *                    The three-valued form is required, not merely cautious — {@code FALSE} ("the
 *                    source says this is a measure") and {@code null} ("the source did not say")
 *                    are different facts downstream.
 */
public record TabularColumn(String name, String type, String description, String label,
                             Boolean isDimension)
{
   /**
    * Compatibility constructor for callers written before description/label/isDimension existed —
    * every existing connector's construction site. All three default to null, which
    * {@code TabularCatalogService} treats identically to "the connector said nothing": the key is
    * omitted from the projection entirely, not written empty or false.
    */
   public TabularColumn(String name, String type) {
      this(name, type, null, null, null);
   }
}
