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

import org.apache.commons.lang.math.NumberUtils;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Parser for a document with no schema.
 */
public class NoSchemaDocumentParser implements DocumentParser {
   @Override
   public void append(Node node) {
      append(node, root);
   }

   private void append(Node node, EditableNode parent) {
      final EditableNode editNode = new EditableNode();
      String nodeName = node.getNodeName();

      if(node.hasChildNodes()) {
         appendChildNodes(node, editNode);
      }
      else if(node.getNodeType() == Node.TEXT_NODE) {
         nodeName = "#text";
         final String text = node.getNodeValue().trim();

         if(!text.isEmpty()) {
            final Object value = parseValue(text);
            editNode.setValue(value);
         }
      }

      parent.appendNode(nodeName, editNode.toParsedNode());
   }

   private void appendChildNodes(Node node, EditableNode editNode) {
      final NodeList childNodes = node.getChildNodes();

      for(int i = 0; i < childNodes.getLength(); i++) {
         final Node childNode = childNodes.item(i);
         final short childType = childNode.getNodeType();

         if(childType == Node.TEXT_NODE) {
            final String text = childNode.getNodeValue().trim();

            if(!text.isEmpty()) {
               final Object value = parseValue(text);
               editNode.setValue(value);
            }
         }
         else if(childType == Node.ELEMENT_NODE){
            append(childNode, editNode);
         }
      }
   }

   /**
    * Tries to parse String value from text node into a more specific java type.
    *
    * @param text the text to parse
    * @return the parsed java object or the original text if the type could not be narrowed down.
    */
   private Object parseValue(String text) {
      Object value = text;

      if(NumberUtils.isNumber(text)) {
         try {
            value = NumberUtils.createNumber(text);
         }
         catch(NumberFormatException ignore) {
         }
      }
      else if("true".equals(text) || "false".equals(text)) {
         value = Boolean.valueOf(text);
      }

      return value;
   }

   @Override
   public ParsedNode getRoot() {
      return root.toParsedNode();
   }

   @Override
   public void resetRoot() {
      root = new EditableNode();
   }

   private EditableNode root = new EditableNode();
}
