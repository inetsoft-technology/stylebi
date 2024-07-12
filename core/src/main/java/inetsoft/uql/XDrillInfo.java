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
package inetsoft.uql;

import inetsoft.uql.erm.DataRef;
import inetsoft.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.PrintWriter;
import java.io.Serializable;
import java.util.Enumeration;
import java.util.Vector;

/**
 * XDrillInfo defines a drill info with a group drill path.
 *
 * @version 9.1, 04/06/2007
 * @author InetSoft Technology Corp
 */
public class XDrillInfo implements XMLSerializable, Serializable, Cloneable {
   /**
    * Create an empty drill info.
    */
   public XDrillInfo() {
   }

   /**
    * Copy drill paths.
    * @param info the drill info to copy from.
    */
   public void copyDrillPaths(XDrillInfo info) {
      if(info == null) {
         return;
      }

      this.paths = Tool.deepCloneCollection(info.paths);
   }

   /**
    * Add a drill path to the info.
    * @param path the drill path.
    */
   public int addDrillPath(DrillPath path) {
      paths.addElement(path);
      return paths.size() - 1;
   }

   /**
    * Remove a selected drill path.
    * @param path the selected drill path.
    */
   public boolean removeDrillPath(DrillPath path) {
      return paths.remove(path);
   }

   /**
    * Remove a selected drill path.
    * @param idx the index of selected drill path.
    */
   public boolean removeDrillPath(int idx) {
      if(idx >= 0) {
         paths.removeElementAt(idx);
         return true;
      }

      return false;
   }

   /**
    * Check if there is no drill paths in the info.
    */
   public boolean isEmpty() {
      return paths.size() <= 0;
   }

   /**
    * Get the drill paths count.
    */
   public int getDrillPathCount() {
      return paths.size();
   }

   /**
    * Get the all drill paths.
    */
   public Enumeration getDrillPaths() {
      return paths.elements();
   }

   /**
    * Set the specified drill path.
    * @param idx the index of the selected drill path.
    * @param path the selected drill path.
    */
   public void setDrillPath(int idx, DrillPath path) {
      paths.setElementAt(path, idx);
   }

   /**
    * Get the specified drill path.
    * @param idx the index of the selected drill path.
    */
   public DrillPath getDrillPath(int idx) {
      return (DrillPath) paths.elementAt(idx);
   }

   /**
    * Check if the specified drill path is in the info.
    */
   public boolean contains(DrillPath path) {
      return paths.indexOf(path) >= 0;
   }

   /**
    * Get the index of the specified drill path.
    */
   public int indexOf(DrillPath path) {
      return paths.indexOf(path);
   }

   /**
    * Get the field this drill is defined on.
    * @return the field this drill is defined.
    */
   public DataRef getColumn() {
      return column;
   }

   /**
    * Set the column this drill is defined on.
    * @param column the specified field this drill is defined on.
    */
   public void setColumn(DataRef column) {
      this.column = column;
   }

   /**
    * Remove all components from the path.
    */
   public void clear() {
      paths.removeAllElements();
      column = null;
   }

   /**
    * Check if equals another object.
    */
   public boolean equals(Object obj) {
      if(!(obj instanceof XDrillInfo)) {
         return false;
      }

      XDrillInfo info = (XDrillInfo) obj;

      if(paths.size() != info.paths.size()) {
         return false;
      }

      for(int i = 0; i < paths.size(); i++) {
         DrillPath path = (DrillPath) paths.get(i);
         DrillPath npath = (DrillPath) info.paths.get(i);

         if(!path.equalsContent(npath)) {
            return false;
         }
      }

      return Tool.equals(column, info.column);
   }

   @Override
   public String toString() {
      return toString(false);
   }

   /**
    * Get the string representaion.
    */
   public String toString(boolean full) {
      if(paths.size() == 0) {
         return Catalog.getCatalog().getString("none");
      }

      StringBuilder buf = new StringBuilder();

      for(int i = 0; i < paths.size(); i++) {
         DrillPath path = (DrillPath) paths.elementAt(i);
         buf.append(path.toString(full));

         if(i < paths.size() - 1) {
            buf.append(", ");
         }
      }

      return buf.toString();
   }

   /**
    * Clone the object.
    */
   @Override
   public Object clone() {
      try {
         XDrillInfo info = (XDrillInfo) super.clone();
         info.paths = Tool.deepCloneCollection(paths);
         info.column = column == null ? null : (DataRef) column.clone();

         return info;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone XDrillInfo", ex);
      }

      return null;
   }

   /**
    * Write the xml segment to print writer.
    * @param writer the destination print writer.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      writer.println("<XDrillInfo>");

      for(int i = 0; i < paths.size(); i++) {
         DrillPath path = (DrillPath) paths.elementAt(i);
         path.writeXML(writer);
      }

      writer.println("</XDrillInfo>");
   }

   /**
    * Method to parse an xml segment.
    */
   @Override
   public void parseXML(Element tag) throws Exception {
      NodeList list = Tool.getChildNodesByTagName(tag, "drillPath");

      for(int i = 0; i < list.getLength(); i++) {
         Element elem = (Element) list.item(i);

         DrillPath path = new DrillPath("");
         path.parseXML(elem);

         addDrillPath(path);
      }
   }

   private Vector paths = new Vector(); // drill paths
   private transient DataRef column;

   private static final Logger LOG =
      LoggerFactory.getLogger(XDrillInfo.class);
}
