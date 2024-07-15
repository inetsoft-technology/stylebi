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

import inetsoft.uql.asset.AssetObject;
import inetsoft.uql.asset.ValueRangeInfo;
import inetsoft.uql.erm.AbstractDataRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.io.PrintWriter;

/**
 * ColumnSelectionInfo stores basic column selection binding and grouping
 * information.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public class ColumnSelectionInfo implements AssetObject {
   /**
    * Constructor.
    */
   public ColumnSelectionInfo() {
      super();
   }

   /**
    * Get the data reference.
    * @return the data reference.
    */
   public DataRef getDataRef() {
      return column;
   }

   /**
    * Set the data reference.
    * @param ref the specified data reference.
    */
   public void setDataRef(DataRef ref) {
      this.column = ref;
   }

   /**
    * Get the date option.
    * @return the date option.
    */
   public int getDateOption() {
      return option;
   }

   /**
    * Set the date option (options defined in DateRangeRef).
    * @param option the specified date option.
    */
   public void setDateOption(int option) {
      this.option = option;
   }

   /**
    * Get the value range infomation.
    * @return the value range infomation.
    */
   public ValueRangeInfo getValueRangeInfo() {
      return vinfo;
   }

   /**
    * Set the value range infomation.
    * @param info the specified value range infomation.
    */
   public void setValueRangeInfo(ValueRangeInfo info) {
      this.vinfo = info;
   }

   /**
    * Get the string representation.
    * @return the string representaion.
    */
   public String toString() {
      return "[" + column + ", " + option + ", " + vinfo + "]";
   }

   /**
    * Clone the object.
    * @return the cloned object.
    */
   @Override
   public Object clone() {
      try {
         ColumnSelectionInfo info = (ColumnSelectionInfo) super.clone();

         if(column != null) {
            info.column = (DataRef) column.clone();
         }

         if(vinfo != null) {
            info.vinfo = (ValueRangeInfo) vinfo.clone();
         }

         return info;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone ColumnSelectionInfo", ex);
      }

      return null;
   }

   /**
    * Method to parse an xml segment.
    * @param elem the specified xml element.
    */
   @Override
   public void parseXML(Element elem) throws Exception {
      parseAttributes(elem);
      parseContents(elem);
   }

   /**
    * Write the xml segment to print writer.
    * @param writer the destination print writer.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      writer.print("<ColumnSelectionInfo class=\"" +
         getClass().getName()+ "\" ");
      writeAttributes(writer);
      writer.println(">");
      writeContents(writer);
      writer.print("</ColumnSelectionInfo>");
   }

   /**
    * Write contents.
    * @param writer the specified writer.
    */
   protected void writeContents(PrintWriter writer) {
      column.writeXML(writer);
      vinfo.writeXML(writer);
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   protected void parseContents(Element elem) throws Exception {
      Element cnode = Tool.getChildNodeByTagName(elem, "dataRef");
      column = AbstractDataRef.createDataRef(cnode);

      Element inode = Tool.getChildNodeByTagName(elem, "valueRangeInfo");
      vinfo = new ValueRangeInfo();
      vinfo.parseXML(inode);
   }

   /**
    * Write attributes.
    * @param writer the specified writer.
    */
   protected void writeAttributes(PrintWriter writer) {
      writer.print(" dateOption=\"" + option + "\"");
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   protected void parseAttributes(Element elem) {
      option = Integer.parseInt(Tool.getAttribute(elem, "dateOption"));
   }

   /**
    * Check if equals another object.
    * @param obj the specified object.
    * @return <tt>true</tt> if equals, <tt>false</tt> otherwise.
    */
   public boolean equals(Object obj) {
      if(!(obj instanceof ColumnSelectionInfo)) {
         return false;
      }

      ColumnSelectionInfo info = (ColumnSelectionInfo) obj;

      return Tool.equals(column, info.column) && (option == info.option) &&
         Tool.equals(vinfo, info.vinfo);
   }

   private DataRef column;
   private int option;
   private ValueRangeInfo vinfo;

   private static final Logger LOG =
      LoggerFactory.getLogger(ColumnSelectionInfo.class);
}
