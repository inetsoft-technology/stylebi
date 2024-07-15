/*
 * inetsoft-rest - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.test;

import inetsoft.uql.rest.json.AbstractEndpoint;
import inetsoft.uql.rest.json.lookup.JsonLookupEndpoint;

import java.util.*;

public class TestEndpoint extends AbstractEndpoint {
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
   public List<JsonLookupEndpoint> getLookups() {
      return lookups;
   }

   public void setLookups(List<JsonLookupEndpoint> lookups) {
      this.lookups = lookups;
   }

   private String name;
   private String suffix;
   private List<JsonLookupEndpoint> lookups = Collections.emptyList();
}
