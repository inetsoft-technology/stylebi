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

package inetsoft.web.wiz.request;

import java.util.List;

/**
 * The data source types whose endpoint catalogues are wanted, e.g. {@code "Rest.Stripe"}.
 *
 * <p>Keyed by TYPE rather than by data source path because {@code endpoints.json} is one file per
 * connector type: three Stripe connections in an organization share one catalogue, and asking per
 * connection would return the same 110 entries three times.</p>
 */
public class WizEndpointCatalogRequest {
   public List<String> getTypes() {
      return types;
   }

   public void setTypes(List<String> types) {
      this.types = types;
   }

   private List<String> types;
}
