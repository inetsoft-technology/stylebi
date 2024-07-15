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
package inetsoft.uql.rest.xml.parse;

import inetsoft.uql.schema.XTypeNode;
import org.w3c.dom.Node;

import java.util.ArrayDeque;

/**
 * Finds the XTypeNode for a given node.
 */
class XTypeNodeFinder {
   XTypeNode find(Node node, XTypeNode rootType) {
      final ArrayDeque<String> path = getPath(node);
      XTypeNode type = rootType;

      while(type != null && !path.isEmpty()) {
         final String nodeName = path.pop();

         if(nodeName.equals(type.getName())) {
            continue;
         }

         type = (XTypeNode) type.getChild(nodeName);
      }

      return type;
   }

   private ArrayDeque<String> getPath(Node node) {
      final ArrayDeque<String> path = new ArrayDeque<>();
      Node currNode = node;
      Node parent;

      while((parent = currNode.getParentNode()) != null) {
         path.push(currNode.getNodeName());
         currNode = parent;
      }

      return path;
   }
}
