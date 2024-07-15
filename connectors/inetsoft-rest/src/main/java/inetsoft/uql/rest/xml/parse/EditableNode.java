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
package inetsoft.uql.rest.xml.parse;

import java.util.*;

/**
 * Node which records values during the parsing of a document.
 * Once the end of the element is reached, the node is turned into a ParsedNode.
 */
class EditableNode {
   void appendNode(String name, ParsedNode node) {
      if(multiChildren.containsKey(name)) {
         multiChildren.get(name).add(node);
      }
      else if(singleChildren.containsKey(name)) {
         final ParsedNode presentNode = singleChildren.remove(name);
         final ArrayList<ParsedNode> nodes = new ArrayList<>();
         nodes.add(presentNode);
         nodes.add(node);
         multiChildren.put(name, nodes);
      }
      else {
         singleChildren.put(name, node);
      }
   }

   void setValue(Object value) {
      this.value = value;
   }

   ParsedNode toParsedNode() {
      final ParsedNode node;

      if(!singleChildren.isEmpty() || !multiChildren.isEmpty()) {
         node = toMapNode();
      }
      else {
         node = new ValueNode(value);
      }

      return node;
   }

   private MapNode toMapNode() {
      final MapNode map = new MapNode();

      for(final Map.Entry<String, ParsedNode> entry : singleChildren.entrySet()) {
         final String name = entry.getKey();
         final ParsedNode value = entry.getValue();
         map.put(name, value);
      }

      for(final Map.Entry<String, List<ParsedNode>> entry : multiChildren.entrySet()) {
         final String name = entry.getKey();
         final List<ParsedNode> value = entry.getValue();
         map.put(name, value);
      }

      if(value != null) {
         map.put("", value);
      }

      return map;
   }

   // LinkedHashMap to preserve insertion order
   private final Map<String, ParsedNode> singleChildren = new LinkedHashMap<>(0);
   private final Map<String, List<ParsedNode>> multiChildren = new LinkedHashMap<>(0);
   private Object value;
}
