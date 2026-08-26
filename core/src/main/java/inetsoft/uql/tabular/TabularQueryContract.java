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

import com.fasterxml.jackson.databind.JsonNode;

/**
 * What {@code GET /api/wiz/tabular/query-schema} answers: everything a caller needs to build a
 * query against one tabular data source, and nothing else.
 *
 * <p>Deliberately SEPARATE from {@link TabularQuerySchema}, which is the extractor's own working
 * view — the flat parameter list, the dependency matrix, the {@code @View} notes,
 * the unreferenced-property list. All of that is INPUT to
 * {@link TabularQueryParamsSchemaBuilder}, and none of it is something a caller can act on that
 * {@code queryParamsSchema} does not already say: the conditional structure is expressed as
 * {@code allOf}/{@code if}/{@code then}, the notes are folded into the root description, and the
 * facts that survive nowhere else — which property names a file, which selects a sheet — are
 * stamped as {@code format}.</p>
 *
 * <p>Two types rather than one type with five {@code @JsonIgnore} getters, because the two are
 * different things and only one of them is a promise to a caller. With the annotation approach the
 * class both was and was not the wire, and keeping it that way needed a test asserting the
 * serialized field set, i.e. a test whose only job was to catch someone forgetting an annotation.
 * A type the wire cannot see cannot be leaked onto it by omission.</p>
 *
 * @param dataSourceType    {@code XDataSource.getType()}, e.g. {@code "Rest.GitHub"}. The join key:
 *                          a caller's own catalogue of a connector's targets is keyed by this, and
 *                          it names the subject of an error message when a build fails.
 * @param queryParamsSchema a JSON Schema (draft 2020-12) for the {@code queryParams} object, keyed
 *                          by the connector's own property names.
 */
public record TabularQueryContract(String dataSourceType, JsonNode queryParamsSchema) {
}
