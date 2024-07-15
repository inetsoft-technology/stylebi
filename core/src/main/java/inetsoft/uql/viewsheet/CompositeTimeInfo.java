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

import inetsoft.uql.erm.AbstractDataRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.PrintWriter;

/**
 * CompositeTimeInfo contains composite time information. Composite time is
 * composed by multiple columns with each column contains a date part, such as
 * year, month, and day.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public class CompositeTimeInfo extends TimeInfo {
   /**
    * Constructor.
    */
   public CompositeTimeInfo() {
      super();

      columns = new DataRef[0];
   }

   /**
    * Get the column references of time information.
    * @return the column references of time information.
    */
   public DataRef[] getDataRefs() {
      return columns;
   }

   /**
    * Set the column references of time information.
    * @param columns the column references of time information.
    */
   public void setDataRefs(DataRef[] columns) {
      this.columns = columns == null ? new DataRef[0] : columns;
   }

   /**
    * Write attributes.
    * @param writer the specified writer.
    */
   @Override
   protected void writeAttributes(PrintWriter writer) {
      super.writeAttributes(writer);
   }

   /**
    * Parse attributes.
    * @param elem the specified xml element.
    */
   @Override
   protected void parseAttributes(Element elem) {
      super.parseAttributes(elem);
   }

   /**
    * Write contents.
    * @param writer the specified writer.
    */
   @Override
   protected void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      writer.print("<dataRefs>");

      for(int i = 0; i < columns.length; i++) {
         columns[i].writeXML(writer);
      }

      writer.println("</dataRefs>");
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   @Override
   protected void parseContents(Element elem) throws Exception {
      super.parseContents(elem);

      Element columnsNode = Tool.getChildNodeByTagName(elem, "dataRefs");

      if(columnsNode != null) {
         NodeList columnsList =
            Tool.getChildNodesByTagName(columnsNode, "dataRef");
         columns = new DataRef[columnsList.getLength()];

         for(int i = 0; i < columnsList.getLength(); i++) {
            columns[i] =
               AbstractDataRef.createDataRef((Element) columnsList.item(i));
         }
      }
   }

   /**
    * Clone the object.
    * @return the cloned object.
    */
   @Override
   public Object clone() {
      try {
         CompositeTimeInfo info = (CompositeTimeInfo) super.clone();
         DataRef[] cols = new DataRef[columns.length];

         for(int i = 0; i < cols.length; i++) {
            cols[i] = (DataRef) columns[i].clone();
         }

         info.columns = cols;

         return info;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone CompositeTimeInfo", ex);
      }

      return null;
   }

   /**
    * Check if equals another object.
    */
   public boolean equals(Object obj) {
      if(!super.equals(obj)) {
         return false;
      }

      if(!(obj instanceof CompositeTimeInfo)) {
         return false;
      }

      CompositeTimeInfo info = (CompositeTimeInfo) obj;

      if(columns.length != info.columns.length) {
         return false;
      }

      for(int i = 0; i < columns.length; i++) {
         if(!columns[i].equals(info.columns[i])) {
            return false;
         }
      }

      return true;
   }

   /**
    * Check if the binding information has changed.
    */
   @Override
   public boolean equalsBinding(TimeInfo tinfo) {
      if(!super.equalsBinding(tinfo)) {
         return false;
      }
      
      try {
         CompositeTimeInfo info2 = (CompositeTimeInfo) tinfo;
         
         if(columns.length == info2.columns.length) {
            for(int i = 0; i < columns.length; i++) {
               if(!Tool.equals(columns[i], info2.columns[i])) {
                  return false;
               }
            }
            
            return true;
         }
         
         return false;
      }
      catch(Exception ex) {
         return false;
      }
   }

   private DataRef[] columns;

   private static final Logger LOG =
      LoggerFactory.getLogger(CompositeTimeInfo.class);
}
