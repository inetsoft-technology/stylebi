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

import inetsoft.uql.schema.XTypeNode;
import org.w3c.dom.*;

import java.util.*;

/**
 * Parser for a document with a schema. Adds typing as it parses the document.
 */
class SchemaDocumentParser implements DocumentParser {
   SchemaDocumentParser(XTypeNode rootType) {
      this.rootType = rootType;
   }

   public void append(Node node) {
      final ArrayDeque<String> path = new ArrayDeque<>();
      final XTypeNode nodeType = finder.find(node, rootType);
      append(node, nodeType, path, root);
   }

   private void append(Node node, XTypeNode nodeType, Deque<String> path, EditableNode parent) {
      final EditableNode currNode = new EditableNode();
      path.add(node.getNodeName());

      if(!nodeType.isPrimitive()) {
         if(node.hasChildNodes()) {
            appendChild(node, nodeType, path, currNode);
         }
         else {
            currNode.setValue(null);
         }
      }
      else {
         currNode.setValue(node.getTextContent());
         typeMap.register(nodeType, new ArrayList<>(path));
      }

      path.removeLast();
      parent.appendNode(node.getNodeName(), currNode.toParsedNode());
   }

   private void appendChild(Node node,
                            XTypeNode nodeType,
                            Deque<String> path,
                            EditableNode editableNode)
   {
      final NodeList childNodes = node.getChildNodes();

      for(int i = 0; i < childNodes.getLength(); i++) {
         final Node childNode = childNodes.item(i);
         final short childNodeType = childNode.getNodeType();

         if(childNodeType == Node.TEXT_NODE) {
            final String text = childNode.getNodeValue();

            if(!text.trim().isEmpty()) {
               editableNode.setValue(text);
            }
         }
         else if(childNodeType == Node.ELEMENT_NODE) {
            final String childNodeName = childNode.getNodeName();
            final XTypeNode childType = (XTypeNode) nodeType.getChild(childNodeName);

            if(childType == null) {
               final String nodeName = node.getNodeName();
               final String message =
                  String.format("XMLSchema is missing definition for %s/%s", nodeName, childNodeName);
               throw new IllegalStateException(message);
            }

            append(childNode, childType, path, editableNode);
         }
      }
   }

   public ParsedNode getRoot() {
      return root.toParsedNode();
   }

   @Override
   public void resetRoot() {
      root = new EditableNode();
   }

   public TypeMap getTypeMap() {
      return typeMap;
   }

   private EditableNode root = new EditableNode();

   private final TypeMap typeMap = new TypeMap();
   private final XTypeNodeFinder finder = new XTypeNodeFinder();
   private final XTypeNode rootType;
}
