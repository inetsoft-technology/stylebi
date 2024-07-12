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
package inetsoft.storage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import inetsoft.util.Tool;
import org.w3c.dom.*;

import javax.xml.parsers.DocumentBuilderFactory;
import java.util.Iterator;

/**
 * {@code JsonXmlTranscoder} converts between JSON trees and XML DOM trees.
 */
public class JsonXmlTranscoder {
   /**
    * Transcodes an XML document to JSON.
    *
    * @param document the document to transcode.
    *
    * @return the transcoded JSON tree.
    */
   public ObjectNode transcodeToJson(Document document) {
      ObjectMapper mapper = new ObjectMapper();
      ObjectNode root = mapper.createObjectNode();
      transcode(document.getDocumentElement(), root, mapper);
      return (ObjectNode) root.get(document.getDocumentElement().getTagName());
   }

   private void transcode(Element element, ObjectNode parent, ObjectMapper mapper) {
      ObjectNode current = mapper.createObjectNode();
      NamedNodeMap attributes = element.getAttributes();

      for(int i = 0; i < attributes.getLength(); i++) {
         Attr attribute = (Attr) attributes.item(i);
         current.put("@" + attribute.getName(), attribute.getValue());
      }

      String text = Tool.getValue(element);

      if(text != null) {
         current.put("#text", text);
      }

      NodeList nodes = element.getChildNodes();

      for(int i = 0; i < nodes.getLength(); i++) {
         Node node = nodes.item(i);

         if(node.getNodeType() == Node.ELEMENT_NODE) {
            transcode((Element) node, current, mapper);
         }
      }

      JsonNode existing = parent.get(element.getTagName());

      if(existing == null) {
         parent.set(element.getTagName(), current);
      }
      else if(existing.isArray()) {
         ((ArrayNode) existing).add(current);
      }
      else {
         ArrayNode array = mapper.createArrayNode();
         array.add(existing);
         array.add(current);
         parent.set(element.getTagName(), array);
      }
   }

   /**
    * Transcodes a JSON tree to an XML document.
    *
    * @param json        the JSON to transcode.
    * @param rootTagName the tag name for the root element of the XML document.
    *
    * @return the transcoded XML document.
    *
    * @throws Exception if a new document could not be created.
    */
   public Document transcodeToXml(ObjectNode json, String rootTagName) throws Exception {
      Document document =
         DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
      document.appendChild(transcode(rootTagName, json, document));
      return document;
   }

   private Element transcode(String name, ObjectNode node, Document document) {
      Element element = document.createElement(name);

      for(Iterator<String> i = node.fieldNames(); i.hasNext();) {
         String field = i.next();

         if(field.startsWith("@")) {
            element.setAttribute(field.substring(1), node.get(field).asText());
         }
         else if(field.equals("#text")) {
            element.appendChild(document.createCDATASection(node.get(field).asText()));
         }
         else if(node.get(field).isArray()) {
            ArrayNode array = (ArrayNode) node.get(field);

            for(JsonNode child : array) {
               element.appendChild(transcode(field, (ObjectNode) child, document));
            }
         }
         else {
            element.appendChild(transcode(field, (ObjectNode) node.get(field), document));
         }
      }

      return element;
   }
}
