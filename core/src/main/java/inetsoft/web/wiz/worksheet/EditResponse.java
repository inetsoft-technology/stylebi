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
package inetsoft.web.wiz.worksheet;

/**
 * Response body for {@link WorksheetAgentController#edit}, populated only for {@code add_table}
 * (every other op keeps getting an empty {@code 204 No Content} response).
 *
 * <p>{@code add_table} can silently create its new assembly under a name other than the one
 * requested, via {@code AssetUtil.getNextName}'s collision-suffixing (e.g. a request for
 * {@code "ORDERS"} lands as {@code "ORDERS1"} if {@code "ORDERS"} is already taken).
 * {@code assemblyName} discloses the actual resolved name so the caller does not have to
 * discover it via a follow-up {@code read_worksheet_model} call.</p>
 */
public record EditResponse(String assemblyName) {
}
