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
 * <p>Two spellings exist in the wild and both must bind. Stripe and Zendesk write
 * {@code "endpoints": ["Disputes", "Refunds"]}; GitHub writes {@code "endpoint": "Issue Event"}.
 * The connector loader reconciles them with a deserializer modifier, which core cannot reuse
 * because it lives in the connector module. Carrying both fields and normalizing in
 * {@link #targets()} is the smaller of the two costs.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WizEndpointLookup(
   String endpoint,
   List<String> endpoints,
   String jsonPath,
   String key,
   String parameterName)
{
   /** Both spellings collapsed into one list. Never null. */
   public List<String> targets() {
      if(endpoints != null && !endpoints.isEmpty()) {
         return endpoints;
      }

      return endpoint == null ? List.of() : List.of(endpoint);
   }
}
