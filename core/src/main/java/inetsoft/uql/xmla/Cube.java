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
package inetsoft.uql.xmla;

import inetsoft.uql.*;
import inetsoft.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.PrintWriter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Cube represents a cube in an OLAP server.
 *
 * @author  InetSoft Technology
 * @since   10.1
 */
public class Cube implements XCube, XMLSerializable {
   /**
    * Get the name of this cube.
    *
    * @return the name of this cube.
    */
   @Override
   public String getName() {
      return name;
   }

   /**
    * Set the name of this cube.
    *
    * @param name cube name.
    */
   public void setName(String name) {
      this.name = name;
   }

   /**
    * Get all the dimensions contained in this cube.
    *
    * @return an Iterator of Dimension objects.
    */
   @Override
   public Enumeration getDimensions() {
      return new IteratorEnumeration(dimensions.iterator());
   }

   /**
    * Set all the dimensions contained in this cube.
    *
    * @param dimensions Dimensions.
    */
   @SuppressWarnings("unchecked")
   public void setDimensions(Collection dimensions) {
      this.dimensions = new ArrayList<>(dimensions);
   }

   /**
    * Get dimension.
    * @param name the specified dimension unique name.
    * @return dimension of that name, return null if not found.
    */
   @Override
   public XDimension getDimension(String name) {
      if(maps == null) {
         maps = new HashMap<>();
      }

      Dimension dim = maps.get(name);

      if(dim != null) {
         return dim;
      }

      Enumeration dims = getDimensions();

      while(dims.hasMoreElements()) {
         dim = (Dimension) dims.nextElement();

         if(Objects.equals(name, dim.getDimensionName()) ||
            Objects.equals(name, dim.getUniqueName()) ||
            Objects.equals(name, dim.getName()))
         {
            maps.put(name, dim);
            return dim;
         }

         if(dim instanceof HierDimension) {
            HierDimension hdim = (HierDimension) dim;
            String dname = dim.getDimensionName();
            String uname = hdim.getName();
            String hname = hdim.getHierarchyName();
            String huname = hdim.getHierarchyUniqueName();
            String caption = hdim.getHierCaption();

            if(Objects.equals(name, hname) ||
               Objects.equals(name, huname) ||
               Objects.equals(name, caption) ||
               Objects.equals(name, dname + "." + hname) ||
               Objects.equals(name, dname + "." + huname) ||
               Objects.equals(name, dname + "." + caption) ||
               Objects.equals(name, uname + "." + hname) ||
               Objects.equals(name, uname + "." + huname) ||
               Objects.equals(name, uname + "." + caption))
            {
               maps.put(name, dim);
               return dim;
            }
         }
      }

      return null;
   }

   /**
    * Get all the measures contained in this cube.
    *
    * @return an Iterator of CubeMember objects representing measures.
    */
   @Override
   public Enumeration getMeasures() {
      return new IteratorEnumeration(measures.iterator());
   }

   /**
    * Set all the measures contained in this cube.
    *
    * @param measures CubeMembers representing measures.
    */
   @SuppressWarnings("unchecked")
   public void setMeasures(Collection measures) {
      this.measures = new ArrayList<>(measures);
   }

   /**
    * Get the caption of this cube.
    *
    * @return the caption of this cube.
    */
   public String getCaption() {
      return cubCaption == null ? name : cubCaption;
   }

   /**
    * Set the caption of this cube.
    *
    * @param cubCaption the caption of this cube.
    */
   public void setCaption(String cubCaption) {
      this.cubCaption = cubCaption;
   }

   /**
    * Get cube type.
    * @return cube type.
    */
   @Override
   public String getType() {
      return type;
   }

   /**
    * Set cube type.
    * @param type cube type.
    */
   public void setType(String type) {
      this.type = type;
   }

   /**
    * Get measure.
    * @param name the specified measure unique name.
    * @return measure of that name, return null if not found.
    */
   @Override
   public XCubeMember getMeasure(String name) {
      Enumeration meas = getMeasures();

      while(meas.hasMoreElements()) {
         Measure measure = (Measure) meas.nextElement();

         if(Objects.equals(name, measure.getUniqueName())) {
            return measure;
         }

         if(Objects.equals(name, measure.getName())) {
            return measure;
         }
      }

      return null;
   }

   /**
    * Write an XML element representation of this object.
    *
    * @param writer the output stream to which to write the XML data.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      writer.print("<Cube name=\"");
      writer.print(Tool.byteEncode(name));
      writer.print("\" cubCaption=\"");
      writer.print(Tool.byteEncode(getCaption()));
      writer.print("\" type=\"");
      writer.print(type);
      writer.print("\">");
      writer.print("<Dimensions>");

      if(dimensions != null) {
         for(Dimension dimension : dimensions) {
            dimension.writeXML(writer);
         }
      }

      writer.print("</Dimensions>");
      writer.print("<Measures>");

      if(measures != null) {
         for(Measure measure : measures) {
            measure.writeXML(writer);
         }
      }

      writer.print("</Measures>");
      writer.print("</Cube>");
   }

   /**
    * Read in the definition of this object from an XML tag.
    *
    * @param tag the XML Element representing this object.
    */
   @Override
   public void parseXML(Element tag) throws Exception {
      String val = Tool.getAttribute(tag, "name");

      if(val != null) {
         name = Tool.byteDecode(val);
      }

      val = Tool.getAttribute(tag, "cubCaption");

      if(val != null) {
         cubCaption = Tool.byteDecode(val);
      }
      else {
         cubCaption = name;
      }

      type = Tool.getAttribute(tag, "type");

      Element dimsNode = Tool.getChildNodeByTagName(tag, "Dimensions");
      NodeList dimsList = Tool.getChildNodesByTagName(dimsNode, "Dimension");
      dimensions = new ArrayList<>();

      for(int i = 0; i < dimsList.getLength(); i++) {
         Element elem = (Element) dimsList.item(i);
         String classname = Tool.getAttribute(elem, "classname");
         Dimension dim = (Dimension) Class.forName(classname).newInstance();
         dim.parseXML(elem);
         dimensions.add(dim);
      }

      Element measNode = Tool.getChildNodeByTagName(tag, "Measures");
      NodeList measList = Tool.getChildNodesByTagName(measNode, "Measure");
      measures = new ArrayList<>();
      Measure mea;

      for(int i = 0; i < measList.getLength(); i++) {
         mea = new Measure();
         mea.parseXML((Element) measList.item(i));
         measures.add(mea);
      }
   }

   /**
    * Create a copy of this object.
    *
    * @return a copy of this object.
    */
   @Override
   public Object clone() {
      try {
         Cube cube = (Cube) super.clone();

         if(dimensions != null) {
            cube.setDimensions(dimensions.stream()
               .map(d -> (Dimension) d.clone())
               .collect(Collectors.toList()));
         }

         if(measures != null) {
            cube.setMeasures(measures.stream()
               .map(m -> (Measure) m.clone())
               .collect(Collectors.toList()));
         }

         return cube;
      }
      catch(CloneNotSupportedException e) {
         LOG.error(e.getMessage(), e);
      }

      return null;
   }

   private String name;
   private List<Dimension> dimensions = null;
   private List<Measure> measures = null;
   private String type;
   private String cubCaption;
   private Map<String, Dimension> maps;

   private static final Logger LOG = LoggerFactory.getLogger(Cube.class);
}