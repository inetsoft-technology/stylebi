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
import inetsoft.uql.schema.XSchema;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * ListBindingInfo contains list binding information. The information will
 * be executed to fill the data consumer with label data and value data.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public class ListBindingInfo extends BindingInfo {
   /**
    * Constructor.
    */
   public ListBindingInfo() {
      super();
   }

   /**
    * Get the label column.
    * @return the label column of this list binding info.
    */
   public DataRef getLabelColumn() {
      return lcolumn;
   }

   /**
    * Get the data type.
    * @return the data type.
    */
   public String getDataType() {
      return vcolumn == null ? XSchema.STRING : vcolumn.getDataType();
   }

   /**
    * Set the label column to this list binding info.
    * @param column the specified label column.
    */
   public void setLabelColumn(DataRef column) {
      lcolumn = column;
   }

   /**
    * Get the value column.
    * @return the value column of this list binding info.
    */
   public DataRef getValueColumn() {
      return vcolumn;
   }

   /**
    * Set the value column to this list binding info.
    * @param column the specified value column.
    */
   public void setValueColumn(DataRef column) {
      vcolumn = column;
   }

   /**
    * Get the dynamic values.
    * @return the dynamic values.
    */
   @Override
   public List<DynamicValue> getDynamicValues() {
      return new ArrayList<>();
   }

   /**
    * Rename the depended. This method should be called when an assembly or
    * other named variables are renamed. It updates of the dynamic references
    * to use the new name.
    * @param oname the specified old name.
    * @param nname the specified new name.
    */
   @Override
   public void renameDepended(String oname, String nname, Viewsheet vs) {
      // do nothing
   }

   /**
    * Write contents.
    * @param writer the specified writer.
    */
   @Override
   protected void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      if(lcolumn != null) {
         writer.print("<labelColumn>");
         lcolumn.writeXML(writer);
         writer.print("</labelColumn>");
      }

      if(vcolumn != null) {
         writer.print("<valueColumn>");
         vcolumn.writeXML(writer);
         writer.print("</valueColumn>");
      }
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   @Override
   protected void parseContents(Element elem) throws Exception {
      super.parseContents(elem);

      Element lnode = Tool.getChildNodeByTagName(elem, "labelColumn");

      if(lnode != null) {
         lnode = Tool.getFirstChildNode(lnode);
         lcolumn = AbstractDataRef.createDataRef(lnode);
      }

      Element vnode = Tool.getChildNodeByTagName(elem, "valueColumn");

      if(vnode != null) {
         vnode = Tool.getFirstChildNode(vnode);
         vcolumn = AbstractDataRef.createDataRef(vnode);
      }
   }

   /**
    * Get the string representation.
    * @return the string representation of this object.
    */
   public String toString() {
      return "ListBindingInfo: [" + getTableName() + ", " + lcolumn + ", " +
             vcolumn + "]";
   }

   /**
    * Clone the object.
    * @return the cloned object.
    */
   @Override
   public Object clone() {
      try {
         ListBindingInfo info = (ListBindingInfo) super.clone();

         if(lcolumn != null) {
            info.lcolumn = (DataRef) lcolumn.clone();
         }

         if(vcolumn != null) {
            info.vcolumn = (DataRef) vcolumn.clone();
         }

         return info;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone ListBindingInfo", ex);
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

      if(!(obj instanceof ListBindingInfo)) {
         return false;
      }

      ListBindingInfo info = (ListBindingInfo) obj;

      return Tool.equals(lcolumn, info.lcolumn) &&
         Tool.equals(vcolumn, info.vcolumn);
   }

   private DataRef lcolumn;
   private DataRef vcolumn;

   private static final Logger LOG =
      LoggerFactory.getLogger(ListBindingInfo.class);
}
