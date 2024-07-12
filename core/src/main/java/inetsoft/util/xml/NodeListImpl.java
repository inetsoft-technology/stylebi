/*
 * inetsoft-core - StyleBI is a business intelligence web application.
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
package inetsoft.util.xml;

import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.List;

/**
 * Simple NodeList implementation that supports adding items to the list.
 */
public class NodeListImpl implements NodeList {
   public NodeListImpl() {
      nodes = new ArrayList<>();
   }

   @Override
   public int getLength() {
      return nodes.size();
   }

   @Override
   public Node item(int index) {
      return nodes.get(index);
   }

   public void addItem(Node node) {
      nodes.add(node);
   }

   public List<Node> getNodes() {
      return nodes;
   }

   private final List<Node> nodes;
}
