/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.uql.rest.json;

import inetsoft.uql.rest.json.lookup.JsonLookupEndpoint;

import java.util.*;

public class AbstractEndpoint implements EndpointJsonQuery.Endpoint {
   @Override
   public String getName() {
      return name;
   }

   public void setName(String name) {
      this.name = name;
   }

   /**
    * A human-readable account of what this endpoint returns and when to reach for it.
    *
    * <p>Optional, and absent from most {@code endpoints.json} files. It exists for callers that
    * have to CHOOSE an endpoint rather than invoke one already chosen — a name alone carries very
    * little: "Charges" does not say whether it holds payments, disputes or payouts, and several
    * connectors have a dozen names that read alike.</p>
    *
    * <p>Deliberately not used at query time. Nothing about executing a request depends on it, so a
    * missing or stale description can never change what a query does.</p>
    */
   public String getDescription() {
      return description;
   }

   public void setDescription(String description) {
      this.description = description;
   }

   @Override
   public String getSuffix() {
      return suffix;
   }

   public void setSuffix(String suffix) {
      this.suffix = suffix;
   }

   @Override
   public boolean isPaged() {
      return paged;
   }

   public void setPaged(boolean paged) {
      this.paged = paged;
   }

   public List<JsonLookupEndpoint> getLookups() {
      return lookups;
   }

   public void setLookups(List<JsonLookupEndpoint> lookups) {
      this.lookups = lookups;
   }

   /**
    * {@code description} is deliberately excluded here and from {@link #hashCode()}: rewording an
    * endpoint does not make it a different endpoint, and folding it in would make identity depend
    * on prose that is expected to be revised.
    */
   @Override
   public boolean equals(Object o) {
      if(this == o) return true;
      if(o == null || getClass() != o.getClass()) return false;
      AbstractEndpoint that = (AbstractEndpoint) o;
      return Objects.equals(name, that.name) &&
         Objects.equals(suffix, that.suffix) &&
         paged == that.paged &&
         Objects.equals(lookups, that.lookups);
   }

   @Override
   public int hashCode() {
      return Objects.hash(name, suffix, lookups, paged);
   }

   private String name;
   private String description;
   private String suffix;
   private boolean paged;
   private List<JsonLookupEndpoint> lookups = Collections.emptyList();
}
