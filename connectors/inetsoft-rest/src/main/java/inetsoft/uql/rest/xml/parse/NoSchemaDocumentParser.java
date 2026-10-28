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

import org.apache.commons.lang3.math.NumberUtils;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.List;

/**
 * Parser for a document with no schema.
 */
public class NoSchemaDocumentParser implements DocumentParser {
   @Override
   public void append(Node node) {
      append(node, root);
   }

   private void append(Node node, EditableNode parent) {
      if(node.getNodeType() == Node.ATTRIBUTE_NODE) {
         parent.appendNode(ATTRIBUTE_PREFIX + node.getNodeName(),
                           new ValueNode(parseValue(node.getNodeValue())));
         return;
      }

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

      final List<Node> attributes = getAttributes(node);

      if(editNode.hasChildren()) {
         // the element is a map, so its attributes join its own entries, after the children
         for(final Node attribute : attributes) {
            editNode.appendNode(ATTRIBUTE_PREFIX + attribute.getNodeName(), attributeValue(attribute));
         }

         parent.appendNode(nodeName, editNode.toParsedNode());
      }
      else {
         // a text-only element stays a plain value so its column name does not change; its
         // attributes become sibling entries named <element>@<attribute>
         parent.appendNode(nodeName, editNode.toParsedNode());

         for(final Node attribute : attributes) {
            parent.appendNode(nodeName + ATTRIBUTE_PREFIX + attribute.getNodeName(),
                              attributeValue(attribute));
         }
      }
   }

   private ParsedNode attributeValue(Node attribute) {
      return new ValueNode(parseValue(attribute.getNodeValue()));
   }

   private boolean isNamespaceDeclaration(Node attribute) {
      // a non namespace-aware DOM reports no namespace URI for xmlns attributes
      final String name = attribute.getNodeName();
      return XMLNS_URI.equals(attribute.getNamespaceURI()) || "xmlns".equals(name) ||
         name.startsWith("xmlns:");
   }

   private List<Node> getAttributes(Node node) {
      final List<Node> attributes = new ArrayList<>();
      final NamedNodeMap map = node.getAttributes();

      if(map != null) {
         for(int i = 0; i < map.getLength(); i++) {
            final Node attribute = map.item(i);

            if(!isNamespaceDeclaration(attribute)) {
               attributes.add(attribute);
            }
         }
      }

      return attributes;
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

   private static final String ATTRIBUTE_PREFIX = "@";
   private static final String XMLNS_URI = "http://www.w3.org/2000/xmlns/";
   private EditableNode root = new EditableNode();
}
