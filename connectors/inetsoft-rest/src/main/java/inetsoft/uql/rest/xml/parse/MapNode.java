/*
 * inetsoft-rest - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.uql.rest.xml.parse;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.util.*;

/**
 * Map representation of an XML element.
 */
@JsonSerialize
public class MapNode extends LinkedHashMap<String, Object> implements ParsedNode {
   public MapNode() {
      super();
   }

   public MapNode(Map<String, Object> map) {
      super(map);
   }

   /**
    * Get the lookup entities of this MapNode.
    *
    * @param entityPath an xml path to an entity, e.g. /table/tr/td.
    *
    * @return the list of entities as MapNodes.
    */
   public List<MapNode> getLookupEntities(String entityPath) {
      final String[] tags = entityPath.split("/");
      return getLookupEntities(tags, 0);
   }

   /**
    * Get the lookup entities of this MapNode.
    *
    * @param tags  the split strings consisting of the sequence of xml tags leading to the lookup
    *              entity.
    * @param index the current index in the tags array.
    *
    * @return the list of entities as MapNodes.
    */
   private List<MapNode> getLookupEntities(String[] tags, int index) {
      if(tags.length <= index) {
         return Collections.singletonList(this);
      }

      final String tag = tags[index];

      if(tag.isEmpty()) {
         return getLookupEntities(tags, index + 1);
      }

      final Object o = get(tag);

      if(o instanceof List) {
         final List<?> nodeList = (List<?>) o;
         final List<MapNode> nodes = new ArrayList<>();

         for(final Object obj : nodeList) {
            if(obj instanceof MapNode) {
               nodes.addAll(((MapNode) obj).getLookupEntities(tags, index + 1));
            }
         }

         return nodes;
      }
      else if(o instanceof MapNode) {
         return ((MapNode) o).getLookupEntities(tags, index + 1);
      }
      else {
         return Collections.emptyList();
      }
   }
}
