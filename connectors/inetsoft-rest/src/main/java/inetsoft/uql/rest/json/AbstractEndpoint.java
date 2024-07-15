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
   private String suffix;
   private boolean paged;
   private List<JsonLookupEndpoint> lookups = Collections.emptyList();
}
