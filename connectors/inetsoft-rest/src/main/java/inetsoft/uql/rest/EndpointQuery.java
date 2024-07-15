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
package inetsoft.uql.rest;

import inetsoft.uql.rest.json.SuffixTemplate;
import inetsoft.uql.rest.json.lookup.JsonLookupEndpoint;
import inetsoft.uql.rest.json.lookup.JsonLookupQuery;
import inetsoft.uql.tabular.*;

import java.util.List;

public interface EndpointQuery {
   /**
    * Allows subclasses to modify or react to the populated suffix template variables.
    *
    * @param suffix the suffix variables.
    */
   default void postprocessSuffix(SuffixTemplate suffix) {
      // no-op
   }

   RestParameters getParameters();

   Object clone();

   String getLookupEndpoint0();

   void setLookupEndpoint0(String lookupEndpoint);

   String[][] getLookupEndpoints0();

   boolean isLookupEndpointVisible0();

   String getLookupEndpoint1();

   void setLookupEndpoint1(String lookupEndpoint);

   String[][] getLookupEndpoints1();

   boolean isLookupEndpointVisible1();

   String getLookupEndpoint2();

   void setLookupEndpoint2(String lookupEndpoint);

   String[][] getLookupEndpoints2();

   boolean isLookupEndpointVisible2();

   String getLookupEndpoint3();

   void setLookupEndpoint3(String lookupEndpoint);

   String[][] getLookupEndpoints3();

   boolean isLookupEndpointVisible3();

   String getLookupEndpoint4();

   void setLookupEndpoint4(String lookupEndpoint);

   String[][] getLookupEndpoints4();

   boolean isLookupEndpointVisible4();

   boolean isLookupExpanded();

   void setLookupExpanded(boolean expanded);

   boolean isLookupTopLevelOnly();

   void setLookupTopLevelOnly(boolean lookupExpandTop);

   void addLookupQuery();

   void addLookupQuery(String sessionId);

   boolean isLookupExpandTopVisible();

   boolean isAddLookupQueryButtonEnabled();

   void removeLookupQuery();

   void removeLookupQuery(String sessionId);

   boolean isRemoveLookupQueryButtonEnabled();

   int getLookupQueryDepth();

   String getLookupEndpoint(int index);

   boolean lookupEndpointVisible(int index);

   String getParentEndpointOfLookupIndex(int index);

   void setLookupEndpoint(String lookupEndpoint, int index);

   String[][] getLookupEndpoints(int index);

   List<JsonLookupQuery> getLookupQueries();

   JsonLookupEndpoint getJsonLookupEndpoint(String parentEndpoint, String lookupEndpoint);

   void setLookupQueries(List<JsonLookupQuery> lookupQueries);

   String getEndpoint();

   void setEndpoint(String endpoint);

   void setParameters(RestParameters restParameters);

   void setJsonPath(String jsonPath);

   void setExpanded(boolean expandArrays);

   void setExpandTop(boolean topLevelOnly);

   boolean isExpanded();

   boolean isExpandTop();
}
