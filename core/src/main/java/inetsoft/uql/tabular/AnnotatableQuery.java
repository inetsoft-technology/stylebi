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
 * Opt-in capability for a METADATA-class {@link TabularQuery}: names the {@code @Property} whose
 * {@code tagsMethod}-resolved candidate values enumerate this data source's annotatable targets —
 * one target per {@link inetsoft.uql.schema.XTypeNode XTypeNode[]} {@link TabularQuery#getOutputColumns()}
 * returns once that property is set to one of those values.
 *
 * Mirrors {@link BrowsableQuery}'s role for FILE-class connectors: an explicit, per-connector
 * opt-in rather than a heuristic scan of {@code @PropertyEditor} shapes ("the one no-dependsOn
 * tagsMethod param"), because such a heuristic picks the wrong property for a connector whose
 * target sits behind a dependent chain — SharePoint's {@code site -> list} is the concrete case:
 * the no-dependsOn property there is {@code site}, a grouping level, not an annotation target.
 * SharePoint therefore does not implement this interface.
 */
public interface AnnotatableQuery {
   /** The {@code @Property} name whose resolved tagsMethod values are this source's annotation targets. */
   String getAnnotationTargetProperty();
}
