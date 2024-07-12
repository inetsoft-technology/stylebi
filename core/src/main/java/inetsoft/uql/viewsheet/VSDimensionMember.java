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
package inetsoft.uql.viewsheet;

import inetsoft.uql.XCubeMember;
import inetsoft.uql.XMetaInfo;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.asset.DateRangeRef;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.erm.AbstractDataRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.io.PrintWriter;

/**
 * A VSDimensionMember object represents a dimension level member.
 *
 * @author InetSoft Technology
 * @version 8.5
 */
public class VSDimensionMember implements XCubeMember {
   /**
    * Constructor of VSCubeMember.
    */
   public VSDimensionMember() {
      super();
   }

   /**
    * Get the map layer id.
    */
   public int getMapLayerID() {
      return layerID;
   }

   /**
    * Set the map layer id.
    */
   public void setMapLayerID(int id) {
      this.layerID = id;
   }

   /**
    * Get the name of this cube member.
    * @return the name of this cube member.
    */
   @Override
   public String getName() {
      return (name != null && name.length() > 0) ? name : dataRef.getName();
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
    * Get the data type.
    * @return the data type.
    */
   public String getDataType() {
      ColumnRef column = getColumnRef();
      return column == null ? XSchema.STRING : column.getDataType();
   }

   /**
    * Get the column ref.
    * @return the column ref.
    */
   public ColumnRef getColumnRef() {
      if(dataRef == null) {
         return null;
      }

      String dtype = dataRef.getDataType();
      DataRef ref2;

      if(AssetUtil.isDateType(dtype)) {
         ref2 = new DateRangeRef(getName(), dataRef, option);
         ((DateRangeRef) ref2).setOriginalType(dtype);
      }
      else {
         ref2 = dataRef;
      }

      ColumnRef column = new ColumnRef(dataRef);
      column.setDataType(ref2.getDataType());
      column.setAlias(getName());

      return column;
   }

   /**
    * Get the data type of this cube member.
    * @return the data type. This will be one of the data type constants
    * defined in XSchema.
    */
   @Override
   public String getType() {
      if(dataRef == null) {
         return XSchema.STRING;
      }

      ColumnRef column = getColumnRef();

      return column.getDataType();
   }

   /**
    * Get the date option of this cube member.
    * @return the date option.
    */
   public int getDateOption() {
      return option;
   }

   /**
    * Set the date option (options defined in DateRangeRef).
    * @param option the date option
    */
   public void setDateOption(int option) {
      this.option = option;
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
      return this.dateLevel;
   }

   @Override
   public void setDateLevel(String dateLevel) {
      this.dateLevel = dateLevel;
   }

   /**
    * Get the string representation.
    * @return the string representation of this cube member.
    */
   public String toString() {
      return super.toString() + "[" + name + ", " + dataRef + "]";
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
      String opt = Tool.getAttribute(elem, "option");

      if(opt != null) {
         try {
            this.option = Integer.parseInt(opt);
         }
         catch(Exception ex) {
            // ignore it
         }
      }

      this.layerID = Integer.parseInt(Tool.getAttribute(elem, "layerID"));
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
      writer.print("<VSDimensionMember class=\"" + getClass().getName()+ "\" ");
      writeAttributes(writer);
      writer.println(">");
      writeContents(writer);
      writer.print("</VSDimensionMember>");
   }

   /**
    * Write attributes.
    * @param writer the specified writer.
    */
   protected void writeAttributes(PrintWriter writer) {
      writer.print(" option=\"" + option + "\" layerID=\"" + layerID + "\"");
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
         VSDimensionMember member = (VSDimensionMember) super.clone();

         if(dataRef != null) {
            member.dataRef = (DataRef) dataRef.clone();
         }

         return member;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone VSDimensionMember", ex);
      }

      return null;
   }

   /**
    * Check if equals another object.
    */
   public boolean equals(Object obj) {
      if(!(obj instanceof VSDimensionMember)) {
         return false;
      }

      VSDimensionMember member = (VSDimensionMember) obj;

      return Tool.equals(dataRef, member.dataRef) && option == member.option &&
         Tool.equals(name, member.name) && layerID == member.layerID;
   }

   private DataRef dataRef;
   private int option = DateRangeRef.YEAR_INTERVAL;
   private String name = "";
   private int layerID;
   private String originalType = null;
   private String dateLevel = null;

   private static final Logger LOG =
      LoggerFactory.getLogger(VSDimensionMember.class);
}
