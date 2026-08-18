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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * One lookup relation declared beside an endpoint: the endpoints that can be reached from a value
 * pulled out of this one's response.
 *
 * <p>Two spellings exist in the wild and this record carries both as written. Stripe and Zendesk
 * write {@code "endpoints": ["Disputes", "Refunds"]}; GitHub writes
 * {@code "endpoint": "Issue Event"}. Collapsing them into a single normalized list here would put a
 * third representation of the same fact on the wire alongside the two raw fields, for the benefit
 * of no consumer: the one caller that needs a single list,
 * {@code wiz-services/src/services/tabular/endpointCatalogClient.ts}, already does that
 * normalization itself and is tested for it. This record's job is to reflect the source document,
 * not to pre-digest it.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WizEndpointLookup(
   String endpoint,
   List<String> endpoints,
   String jsonPath,
   String key,
   String parameterName)
{
}
