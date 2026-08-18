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

/**
 * One connector type as the catalogue administration page sees it: what it is called, and how much
 * of it there is to ingest.
 *
 * <p>The type strings are {@code Config.getTabularDataSourceTypes()}, i.e. exactly what
 * {@code XDataSource.getType()} returns for a data source of that connector — {@code "Rest.Stripe"},
 * not the {@code "Rest"} of {@code getBaseType()}. That is what makes this list joinable with the
 * types reported by the data source browser.</p>
 *
 * @param type           the registered data source type, e.g. {@code "Rest.Stripe"}.
 * @param status         {@code CATALOGUED} when the connector ships an {@code endpoints.json};
 *                       {@code NOT_CATALOGUED} when it loads but ships none — the normal state of a
 *                       connector that needs user-supplied documentation; {@code UNAVAILABLE} when
 *                       its plugin could not be loaded or its catalogue could not be read, which is
 *                       an environment problem and may be retried. Kept as three values rather than
 *                       a boolean for the reason spelled out on {@link WizEndpointCatalogResponse}:
 *                       a classification failure must never read as a classification result.
 * @param endpointCount  endpoints declared, 0 unless {@code CATALOGUED}.
 * @param describedCount how many of those carry a description. Only described endpoints can be
 *                       retrieved by meaning, so this is the number that decides what an ingest is
 *                       worth.
 */
public record WizEndpointCatalogType(
   String type,
   String status,
   int endpointCount,
   int describedCount)
{
   public static final String CATALOGUED = "CATALOGUED";
   public static final String NOT_CATALOGUED = "NOT_CATALOGUED";
   public static final String UNAVAILABLE = "UNAVAILABLE";
}
