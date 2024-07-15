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
package inetsoft.uql.viewsheet;

import inetsoft.uql.XCubeMember;
import inetsoft.uql.XMetaInfo;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.erm.AbstractDataRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.io.PrintWriter;

/**
 * A VSMeasure object represents a measure.
 *
 * @author InetSoft Technology
 * @version 8.5
 */
public class VSMeasure implements XCubeMember {
   /**
    * Constructor of VSMeasure.
    */
   public VSMeasure() {
      super();
   }

   /**
    * Get the name of this dimension.
    * @return the name of this dimension.
    */
   @Override
   public String getName() {
      return name;
   }

   /**
    * Set the name of this cube member.
    * @param name the member name.
    */
   public void setName(String name) {
      this.name = name;
   }

   /**
    * Get the data reference of this cube member.
    * @return an XDataRef object.
    */
   @Override
   public DataRef getDataRef() {
      return dataRef;
   }

   /**
    * Set the DataRef object that this cube member wraps.
    * @param dataRef the DataRef object.
    */
   public void setDataRef(DataRef dataRef) {
      this.dataRef = dataRef;
   }

   /**
    * Get the column ref.
    * @return the column ref.
    */
   public ColumnRef getColumnRef() {
      if(dataRef == null) {
         return null;
      }

      ColumnRef column = new ColumnRef(dataRef);
      column.setDataType(getDataType());
      column.setAlias(getName());

      return column;
   }

   /**
    * Get the data type.
    * @return the data type.
    */
   public String getDataType() {
      return dataRef.getDataType();
   }

   /**
    * Get the folder of this measure.
    * @return folder of this measure.
    */
   @Override
   public String getFolder() {
      // not currently implemented
      return null;
   }

   /**
    * Get the data type of this cube member.
    * @return the data type. This will be one of the data type constants
    * defined in XSchema.
    */
   @Override
   public String getType() {
      return dataRef == null ? XSchema.STRING : dataRef.getDataType();
   }

   /**
    * Get the XMetaInfo of this cube member.
    *
    * @return the XMetaInfo of this cube member.
    */
   @Override
   public XMetaInfo getXMetaInfo() {
      return null;
   }

   @Override
   public String getOriginalType() {
      return originalType;
   }

   @Override
   public void setOriginalType(String originalType) {
      this.originalType = originalType;
   }

   @Override
   public String getDateLevel() {
      return null;
   }

   @Override
   public void setDateLevel(String originalType) {
   }
   
   /**
    * Get the string representation.
    * @return the string representation.
    */
   public String toString() {
      return super.toString() + "[" + name + dataRef + "]";
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
      Element cnode = Tool.getChildNodeByTagName(elem, "dataRef");

      if(cnode != null) {
         dataRef = AbstractDataRef.createDataRef(cnode);
      }

      name = Tool.getChildValueByTagName(elem, "name");
   }

   /**
    * Write the xml segment to print writer.
    * @param writer the destination print writer.
    */
   @Override
   public final void writeXML(PrintWriter writer) {
      writer.print("<VSMeasure class=\"" + getClass().getName()+ "\" ");
      writeAttributes(writer);
      writer.println(">");
      writeContents(writer);
      writer.print("</VSMeasure>");
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
      if(dataRef != null) {
         dataRef.writeXML(writer);
      }

      if(name != null) {
         writer.print("<name>");
         writer.print("<![CDATA[" + name + "]]>");
         writer.println("</name>");
      }
   }

   /**
    * Create a copy of this object.
    * @return a copy of this object.
    */
   @Override
   public Object clone() {
      try {
         VSMeasure measure = (VSMeasure) super.clone();
         measure.dataRef = (DataRef) dataRef.clone();

         return measure;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone VSMeasure", ex);
      }

      return null;
   }

   /**
    * Check if equals another object.
    */
   public boolean equals(Object obj) {
      if(!(obj instanceof VSMeasure)) {
         return false;
      }

      VSMeasure measure = (VSMeasure) obj;
      return Tool.equals(dataRef, measure.dataRef) &&
         Tool.equals(name, measure.name);
   }

   private DataRef dataRef = null;
   private String name = "";
   private String originalType = null;

   private static final Logger LOG =
      LoggerFactory.getLogger(VSMeasure.class);
 }
