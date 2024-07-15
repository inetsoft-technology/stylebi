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
package inetsoft.uql.viewsheet;

import inetsoft.uql.*;
import inetsoft.uql.erm.DataRef;
import inetsoft.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.*;

import java.io.PrintWriter;
import java.util.*;

/**
 * A VSCube object represents a cube.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public class VSCube implements XCube, XMLSerializable {
   /**
    * Constructor of VSCube.
    */
   public VSCube() {
      super();
   }

   /**
    * Get the name of this cube.
    * @return the name of this cube.
    */
   @Override
   public String getName() {
      return name;
   }

   /**
    * Set the name of this cube.
    * @param name the cube name.
    */
   public void setName(String name) {
      this.name = name;
   }

   /**
    * Set the dimensions of this cube.
    * @param dimensions the ArrayList of XDimension objects associated with this cube.
    */
   public void setDimensions(List<XDimension> dimensions) {
      this.dimensions = dimensions;
   }

   /**
    * Get all the dimensions contained in this cube.
    * @return an Enumeration of XDimension objects.
    */
   @Override
   public Enumeration<XDimension> getDimensions() {
      return new IteratorEnumeration<>(dimensions.iterator());
   }

   /**
    * Get the specified dimension.
    * @param name the name of the dimension.
    * @return an XDimension object or <code>null</code> if a dimension with the
    *         specified name does not exist.
    */
   @Override
   public XDimension getDimension(String name) {
      for(int i = 0; i < dimensions.size(); i++) {
         XDimension dimension = dimensions.get(i);

         if(Tool.equals(dimension.getName(), name)) {
            return dimension;
         }
      }

      return null;
   }

   /**
    * Set the measures of this cube.
    * @param measures the ArrayList of XCubeMember objects associated with this cube.
    */
   public void setMeasures(List<XCubeMember> measures) {
      this.measures = measures;
   }

   /**
    * Get all the measures contained in this cube.
    * @return an Enumeration of XCubeMember objects.
    */
   @Override
   public Enumeration<XCubeMember> getMeasures() {
      return new IteratorEnumeration<>(measures.iterator());
   }

   /**
    * Get the specified measure.
    * @param name the name of the measure.
    * @return an XCubeMember object or <code>null</code> if a measure with the
    *         specified name does not exist.
    */
   @Override
   public XCubeMember getMeasure(String name) {
      for(int i = 0; i < measures.size(); i++) {
         XCubeMember measure = measures.get(i);

         if(measure.getName().equals(name)) {
            return measure;
         }
      }

      return null;
   }

   /**
    * Get the string representation.
    * @return the string representation.
    */
   public String toString() {
      return super.toString() + "[" + name + ", " + dimensions + ", " + measures + "]";
   }

   /**
    * Method to parse an xml segment.
    * @param elem the specified xml element.
    */
   @Override
   public final void parseXML(Element elem) throws Exception {
      parseAttributes(elem);
      parseContents(elem);
   }

   /**
    * Parse attributes.
    * @param elem the specified xml element.
    */
   protected void parseAttributes(Element elem) {
      // do nothing
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   protected void parseContents(Element elem) throws Exception {
      name = "";
      dimensions.clear();
      measures.clear();

      Element anode = Tool.getChildNodeByTagName(elem, "name");

      if(anode != null) {
         name = Tool.getValue(anode) == null ? "" : Tool.getValue(anode);
      }

      NodeList list = Tool.getChildNodesByTagName(elem, "VSDimension");

      for(int i = 0; list != null && i < list.getLength(); i++) {
         Node node = list.item(i);

         VSDimension dimension = new VSDimension();
         dimension.parseXML((Element) node);
         dimensions.add(dimension);
      }

      list = Tool.getChildNodesByTagName(elem, "VSMeasure");

      for(int i = 0; i < list.getLength(); i++) {
         Node node = list.item(i);

         VSMeasure measure = new VSMeasure();
         measure.parseXML((Element) node);
         measures.add(measure);
      }
   }

   /**
    * Write the xml segment to print writer.
    * @param writer the destination print writer.
    */
   @Override
   public final void writeXML(PrintWriter writer) {
      writer.print("<VSCube class=\"" + getClass().getName()+ "\" ");
      writeAttributes(writer);
      writer.println(">");
      writeContents(writer);
      writer.print("</VSCube>");
   }

   /**
    * Write attributes.
    * @param writer the specified writer.
    */
   protected void writeAttributes(PrintWriter writer) {
      // do nothing
   }

   /**
    * Write contents.
    * @param writer the specified writer.
    */
   protected void writeContents(PrintWriter writer) {
      if(name != null) {
         writer.print("<name>");
         writer.print("<![CDATA[" + name + "]]>");
         writer.println("</name>");
      }

      for(int i = 0; dimensions != null && i < dimensions.size(); i++) {
         VSDimension dimension = (VSDimension) dimensions.get(i);
         dimension.writeXML(writer);
      }

      for(int i = 0; measures != null && i < measures.size(); i++) {
         VSMeasure measure = (VSMeasure) measures.get(i);
         measure.writeXML(writer);
      }
   }

   /**
    * Create a copy of this object.
    * @return a copy of this object.
    */
   @Override
   public Object clone() {
      try {
         VSCube cube = (VSCube) super.clone();
         cube.dimensions = Tool.deepCloneCollection(dimensions);
         cube.measures = Tool.deepCloneCollection(measures);

         return cube;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone VSCube", ex);
      }

      return null;
   }

   /**
    * Check if equals another objects.
    */
   public boolean equals(Object obj) {
      if(!(obj instanceof VSCube)) {
         return false;
      }

      VSCube cube = (VSCube) obj;

      return Tool.equals(name, cube.name) &&
         dimensions.equals(cube.dimensions) && measures.equals(cube.measures);
   }

   /**
    * Validate this viewsheet cube.
    * @param columns the specified column selection.
    * @return <tt>true if changed, <tt>false</tt> otherwise.
    */
   public boolean validate(ColumnSelection columns) {
      if(columns == null || columns.getAttributeCount() == 0) {
         return false;
      }

      boolean changed = false;

      for(int i = dimensions.size() - 1; i >= 0; i--) {
         VSDimension dimension = (VSDimension) dimensions.get(i);
         dimension.validate(columns);

         if(dimension.isEmpty()) {
            dimensions.remove(i);
            changed = true;
         }
      }

      for(int i = measures.size() - 1; i >= 0; i--) {
         VSMeasure measure = (VSMeasure) measures.get(i);
         DataRef ref = measure.getDataRef();

         if(ref == null || columns.getAttribute(ref.getName()) == null) {
            measures.remove(i);
            changed = true;
         }
      }

      return changed;
   }

   /**
    * Get cube type.
    * @return cube type.
    */
   @Override
   public String getType() {
      return "";
   }

   /**
    * Check if this cube is empty.
    * @return <tt>true</tt> if yes, <tt>false</tt> otherwise.
    */
   public boolean isEmpty() {
      return dimensions.size() == 0 || measures.size() == 0;
   }

   private String name = "";
   private List<XDimension> dimensions = new ArrayList<>();
   private List<XCubeMember> measures = new ArrayList<>();

   private static final Logger LOG =
      LoggerFactory.getLogger(VSCube.class);
}
