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

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The layer and map type of one geo field, read from the chart's geo column (the Composer's own
 * per-column Geographic setting) rather than from the shelf entry (Bug #81664).
 *
 * @param layer     the layer name (Country, State, Province, City, Zip), or {@code null} when the
 *                  layer is not a plain layer id: a variable or expression ({@code layerValue}
 *                  then carries its text), or none set yet
 * @param mapType   the map type the column was registered with (U.S., Canada, ...), or
 *                  {@code null} when none is recorded
 * @param layerValue the stored layer value: the numeric layer id as text, or a dynamic
 *                  ({@code $(name)} / {@code =script}) value
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GeoFieldLayer(String layer, String mapType, String layerValue) {
}
