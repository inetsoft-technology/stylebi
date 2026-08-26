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
 * One callable endpoint as the wiz portal sees it.
 *
 * <p>{@code paged} is boxed rather than primitive because several connectors write it as the STRING
 * {@code "true"} and others omit it entirely; Jackson coerces the string, and the box lets "absent"
 * stay distinguishable from "false".</p>
 *
 * <p>Unknown properties are ignored on purpose, and the reverse of what the connector's own loader
 * requires. Fifteen connectors declare extra endpoint properties of their own — {@code pageType},
 * {@code post}, {@code bodyTemplate}, {@code pageLimit}, {@code pagePath},
 * {@code pageRequiredParameter}, {@code paginationPath}, {@code freePageLimit}, {@code url} — none
 * of which this model needs. Binding strictly would take those connectors' catalogues to zero.</p>
 *
 * @param description what the endpoint is for. Null for the entries nobody has described yet, and
 *                    for the ones deliberately left blank because the vendor has withdrawn them.
 * @param responseSchema the response structure the connector's maintainer declared for this
 *                       endpoint, straight out of {@code endpoints.json}. Null means the endpoint
 *                       has not been curated, which is equivalent to not having declared one.
 *                       This record is the ONLY channel this value has into wiz.
 *
 *                       <p>Typed as {@code Object} rather than {@code Map<String, Object>} for the
 *                       same reason as {@code AbstractEndpoint.getResponseSchema}: the
 *                       representation allows an array at the root, and binding that to a
 *                       {@code Map} would be a type mismatch on a known property that
 *                       {@code ignoreUnknown} cannot excuse, which
 *                       {@link inetsoft.web.wiz.service.EndpointCatalogReader} would surface as an
 *                       {@code IOException} for the whole connector.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WizEndpointCatalogEntry(
   String name,
   String description,
   String suffix,
   Boolean paged,
   List<WizEndpointLookup> lookups,
   Object responseSchema)
{
}
