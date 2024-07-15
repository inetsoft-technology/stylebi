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
package inetsoft.uql.viewsheet.graph;

import inetsoft.uql.asset.AssetObject;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.PrintWriter;
import java.util.*;

/**
 * VSPoint, it defines a point in graph by holding dimension and measure values.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public class VSPoint implements AssetObject {
   /**
    * Constructor.
    */
   public VSPoint() {
      super();
      pairs = new ArrayList<>();
      vmap = new HashMap<>();
   }

   /**
    * Check if equals another objects.
    * @param obj the specified object.
    * @return <tt>true</tt> if equals, <tt>false</tt> otherwise.
    */
   public boolean equals(Object obj) {
      if(!(obj instanceof VSPoint)) {
         return false;
      }

      VSPoint point = (VSPoint) obj;

      return pairs.equals(point.pairs);
   }

   @Override
   public int hashCode() {
      return pairs.hashCode();
   }

   /**
    * Add a FieldVulue to value set.
    * @param fldValue be added to value set
    */
   public void addValue(VSFieldValue fldValue) {
      pairs.add(fldValue);
      vmap.put(fldValue.getFieldName(), fldValue);
   }

   /**
    * Remove the value at the specified index.
    */
   public void removeValue(int idx) {
      vmap.remove(getValue(idx).getFieldName());
      pairs.remove(idx);
   }

   /**
    * Clear all FieldVulues from FieldVulue set.
    */
   public void clearValues() {
      pairs.clear();
      vmap.clear();
   }

   /**
    * Get the value count.
    */
   public int getValueCount() {
      return pairs.size();
   }

   /**
    * Test if the selection is empty.
    */
   public boolean isEmpty() {
      return pairs.size() == 0;
   }

   /**
    * Get the value at the specified index.
    */
   public VSFieldValue getValue(int idx) {
      return pairs.get(idx);
   }

   /**
    * Get the value of the field.
    */
   public VSFieldValue getValue(String name) {
      return vmap.get(name);
   }

   /**
    * Generate the XML segment to represent this point.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      writer.println("<VSPoint class=\"" + getClass().getName() + "\">");
      Iterator iterator = pairs.iterator();

      while(iterator.hasNext()) {
         ((VSFieldValue) iterator.next()).writeXML(writer);
      }

      writer.println("</VSPoint>");
   }

   /**
    * Parse the XML element that contains information on this
    * point.
    */
   @Override
   public void parseXML(Element tag) throws Exception {
      NodeList xnodes = Tool.getChildNodesByTagName(tag, "VSFieldValue");
      pairs.clear();

      for(int i = 0; i < xnodes.getLength(); i++) {
         Element xnode = (Element) xnodes.item(i);
         VSFieldValue fldValue = new VSFieldValue();
         fldValue.parseXML(xnode);
         addValue(fldValue);
      }
   }

   /**
    * Create a clone of this object.
    */
   @Override
   public Object clone() {
      try {
         VSPoint obj = (VSPoint) super.clone();
         obj.pairs = (ArrayList) pairs.clone();

         return obj;
      }
      catch(Exception e) {
         LOG.error("Failed to clone VSPoint", e);
         return null;
      }
   }

   /**
    * Get the string representation.
    */
   public String toString() {
      return "VSPoint" + '[' + pairs + ']';
   }

   private ArrayList<VSFieldValue> pairs;
   private Map<String,VSFieldValue> vmap;

   private static final Logger LOG = LoggerFactory.getLogger(VSPoint.class);
}
