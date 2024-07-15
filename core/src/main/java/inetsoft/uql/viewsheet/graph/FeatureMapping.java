/*
 * inetsoft-core - StyleBI is a business intelligence web application.
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
package inetsoft.uql.viewsheet.graph;

import inetsoft.util.Tool;
import inetsoft.util.XMLSerializable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.PrintWriter;
import java.io.Serializable;
import java.util.*;

/**
 * Data structure that encapsulates feature mapping infomation.
 *
 * @author InetSoft Technology
 * @since  10.2
 */
public class FeatureMapping implements XMLSerializable, Cloneable, Serializable {
   /**
    * Constructure.
    */
   public FeatureMapping() {
   }

   /**
    * Constructure.
    * @param id feature mapping id.
    * @param algorithm algorithm this feature mapping uses.
    * @param type map type.
    * @param layer map layer.
    */
   public FeatureMapping(String id, String algorithm, String type, int layer)
   {
      this.id = id;
      this.algorithm = algorithm;
      this.type = type;
      this.layer = layer;
   }

   /**
    * Write the xml segment to print writer.
    * @param writer the destination print writer.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      writer.println(
         "<FeatureMapping class=\"inetsoft.uql.viewsheet.graph.FeatureMapping\""
         + " id=\"" +  id + "\" algorithm=\"" +
         algorithm + "\" type=\"" + type + "\" layer=\"" + layer + "\">");

      for(Map.Entry<String, String> entry : mappings.entrySet()) {
         writer.println("<mapping>");
         writer.println("<value>");
         writer.println("<![CDATA[" + entry.getKey() + "]]>");
         writer.println("</value>");
         writer.println("<geoCode>");
         writer.println("<![CDATA[" + entry.getValue() + "]]>");
         writer.println("</geoCode>");
         writer.println("</mapping>");
      }

      if(dupMapping != null && dupMapping.size() > 0) {
         Iterator<String> keys = dupMapping.keySet().iterator();
         writer.println("<dupmappings>");

         while(keys.hasNext()) {
            String name = keys.next();

            List<String> values = dupMapping.get(name);
            writer.println("<dupmapping><![CDATA[" + name + "]]>");

            for(String value:values) {
               writer.print("<value>");
               writer.print("<![CDATA[" + value + "]]>");
               writer.print("</value>");
            }

            writer.println("</dupmapping>");
         }

         writer.println("</dupmappings>");
      }

      writer.println("</FeatureMapping>");
   }

   /**
    * Method to parse an xml segment.
    */
   @Override
   public void parseXML(Element tag) throws Exception {
      id = Tool.getAttribute(tag, "id");
      algorithm = Tool.getAttribute(tag, "algorithm");
      type = Tool.getAttribute(tag, "type");
      layer = Integer.parseInt(Tool.getAttribute(tag, "layer"));

      NodeList list = Tool.getChildNodesByTagName(tag, "mapping");

      for(int i = 0; i < list.getLength(); i++) {
         Element node = (Element) list.item(i);
         Element valueNode = Tool.getChildNodeByTagName(node, "value");
         String value = valueNode == null ? null : Tool.getValue(valueNode);
         Element codeNode = Tool.getChildNodeByTagName(node, "geoCode");
         String geoCode = codeNode == null ? null : Tool.getValue(codeNode);

         value = value == null ? Tool.getAttribute(node, "value") : value;
         geoCode = geoCode == null ? Tool.getAttribute(node, "geoCode") :
            geoCode;
         mappings.put(value, geoCode);
      }

      Element dnodes = Tool.getChildNodeByTagName(tag, "dupmappings");

      if(dnodes == null) {
         return;
      }

      list = Tool.getChildNodesByTagName(dnodes, "dupmapping");
      dupMapping = new HashMap<>();

      for(int i = 0; i < list.getLength(); i++) {
         Element dnode = (Element) list.item(i);
         String name = Tool.getValue(dnode);
         List<String> values = new ArrayList<>();
         NodeList vnodes = Tool.getChildNodesByTagName(dnode, "value");

         for(int j = 0; j < vnodes.getLength(); j++) {
            Element vnode = (Element) vnodes.item(j);
            values.add(Tool.getValue(vnode));
         }

         dupMapping.put(name, values);
      }
   }

   /**
    * Add mapping.
    * @param value the value that feature mapping mapped to.
    * @param geoCode geographic code.
    */
   public void addMapping(String value, String geoCode) {
      mappings.put(value, geoCode);
   }

   /**
    * Get manual mappings.
    */
   public Map<String, String> getMappings() {
      return mappings;
   }

   /**
    * Set manual mappings.
    */
   public void setMappings(Map<String, String> mappings) {
      this.mappings = new LinkedHashMap<>(mappings);
   }

   /**
    * Remove the specified mapping.
    * @param value the value that feature mapping mapped to.
    */
   public void removeMapping(String value) {
      mappings.remove(value);
   }

   /**
    * Set duplicate mappings.
    */
   public void setDupMapping(Map<String, List<String>> dupMapping) {
      this.dupMapping = dupMapping;
   }

   /**
    * Get duplicate mappings.
    */
   public Map<String, List<String>> getDupMapping() {
      return dupMapping;
   }

   /**
    * Get algorithm name.
    */
   public String getAlgorithm() {
      return algorithm;
   }

   /**
    * Get map layer.
    */
   public int getLayer() {
      return layer;
   }

   /**
    * Get mapping id.
    */
   public String getID() {
      return id;
   }

   /**
    * Get map tyoe.
    */
   public String getType() {
      return type;
   }

   /**
    * Set algorithm name.
    */
   public void setAlgorithm(String algorithm) {
      this.algorithm = algorithm;
   }

   /**
    * Set map layer.
    */
   public void setLayer(int layer) {
      this.layer = layer;
   }

   /**
    * Set mapping id.
    */
   public void setID(String id) {
      this.id = id;
   }

   /**
    * Set map tyoe.
    */
   public void setType(String type) {
      this.type = type;
   }

   /**
    * Create a clone of this object.
    */
   @Override
   public Object clone() {
      try {
         FeatureMapping obj = (FeatureMapping) super.clone();
         obj.mappings = (LinkedHashMap<String, String>) mappings.clone();

         return obj;
      }
      catch(Exception e) {
         LOG.error("Failed to clone FeatureMapping", e);
         return null;
      }
   }

   /**
    * To string.
    */
   public String toString() {
      return "FeatureMapping: [" + id + ", " + type + ", " + algorithm + ", " +
         layer + ", " + mappings.toString() + "]";
   }

   /**
    * Check if equals or not.
    */
   public boolean equals(Object obj) {
      if(!(obj instanceof FeatureMapping)) {
         return false;
      }

      FeatureMapping mapping = (FeatureMapping) obj;

      return Tool.equals(algorithm, mapping.algorithm) &&
         Tool.equals(type, mapping.type) && Tool.equals(id, mapping.id) &&
         Tool.equals(layer, mapping.layer) &&
         Tool.equals(mappings, mapping.mappings);
   }

   private String algorithm;
   private String type;
   private int layer;
   private String id;
   private LinkedHashMap<String, String> mappings = new LinkedHashMap<>();
   private Map<String, List<String>> dupMapping;
   private static final Logger LOG =
      LoggerFactory.getLogger(FeatureMapping.class);
}
