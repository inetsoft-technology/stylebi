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
package inetsoft.uql.rest.json.lookup;

import java.util.Collections;
import java.util.List;

public class JsonLookupQuery {
   public JsonLookupEndpoint getLookupEndpoint() {
      return lookupEndpoint;
   }

   public void setLookupEndpoint(JsonLookupEndpoint lookupEndpoint) {
      this.lookupEndpoint = lookupEndpoint;
   }

   public String getJsonPath() {
      return jsonPath;
   }

   public void setJsonPath(String jsonPath) {
      this.jsonPath = jsonPath;
   }

   public boolean isExpandArrays() {
      return expandArrays;
   }

   public void setExpandArrays(boolean expandArrays) {
      this.expandArrays = expandArrays;
   }

   public boolean isTopLevelOnly() {
      return topLevelOnly;
   }

   public void setTopLevelOnly(boolean topLevelOnly) {
      this.topLevelOnly = topLevelOnly;
   }

   public List<JsonLookupQuery> getLookupQueries() {
      return lookupQueries;
   }

   public void setLookupQueries(List<JsonLookupQuery> lookupQueries) {
      this.lookupQueries = lookupQueries;
   }

   private JsonLookupEndpoint lookupEndpoint;
   private String jsonPath;
   private boolean expandArrays;
   private boolean topLevelOnly;
   private List<JsonLookupQuery> lookupQueries = Collections.emptyList();
}
