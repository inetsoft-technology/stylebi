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
package inetsoft.uql.jdbc.util;

import inetsoft.uql.*;
import inetsoft.uql.jdbc.XFilterNode;
import inetsoft.uql.jdbc.XFilterNodeItem;
import inetsoft.uql.schema.UserVariable;
import org.w3c.dom.Element;

import java.io.PrintWriter;

/**
 * A ConditionList stores a list of XFilterNodeLevel to be applied to
 * the resulting data.
 */
public class FilterList extends HierarchyList {
   /**
    * Get the XFilterNode at the specified index.
    *
    * @param index the specified index
    * @return the XFilterNode at the specified index
    */
   public XFilterNode getXFilterNode(int index) {
      if(index >= 0 && index < getSize()) {
         return ((XFilterNodeItem) list.elementAt(index)).getNode();
      }
      else {
         return null;
      }
   }

   /**
    * Replace the XFilterNode at the specified index.
    *
    * @param index the index of the ConditionItem
    * @param filterNodeItem the new XFilterNode
    */
   public void setXFilterNodeItem(int index, XFilterNodeItem filterNodeItem){
      if(index >= 0 && index < getSize()) {
         list.set(index, filterNodeItem);
      }
   }

   /**
    * Get the contained ConditionList.
    * @return the contained CondtiionList.
    */
   @Override
   public ConditionList getConditionList() {
      return null;
   }

   /**
    * Get the ConditionItem at the specified index.
    * @param index the specified index.
    * @return the ConditionItem at the specified index.
    */
   @Override
   public ConditionItem getConditionItem(int index) {
      return null;
   }

   /**
    * Get the JunctionOperator at the specified index.
    * @param index the specified index.
    * @return the JunctionOperator at the specified index.
    */
   @Override
   public JunctionOperator getJunctionOperator(int index) {
      return null;
   }

   /**
    * Read in the XML representation of this object.
    * @param ctag the XML element representing this object.
    */
   @Override
   public void parseXML(Element ctag) throws Exception {
      return;
   }

   /**
    * Write this data selection to XML.
    * @param writer the stream to output the XML text to
    */
   @Override
   public void writeXML(PrintWriter writer) {
      return;
   }

   /**
    * Replace all embeded user variables.
    * @param vars the specified variable table.
    */
   @Override
   public void replaceVariables(VariableTable vars) {
      return;
   }

   /**
    * Get all variables in the condition value list.
    * @return the variable list.
    */
   @Override
   public UserVariable[] getAllVariables() {
      return new UserVariable[0];
   }

   /**
    * Clone the object.
    * @return the cloned object.
    */
   @Override
   public Object clone() {
      return this;
   }
}
