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

import inetsoft.uql.asset.AssetObject;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.io.PrintWriter;

/**
 * CellRef refers to a cell in an <tt>XTable</tt>.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public class CellRef implements AssetObject {
   /**
    * Create a cell ref.
    */
   public CellRef() {
      super();
   }

   /**
    * Create a cell ref.
    * @param col the specified column name.
    * @param row the specified row index.
    */
   public CellRef(String col, int row) {
      this();

      this.col = col;
      this.row = row;
   }

   /**
    * Get the column.
    * @return the column.
    */
   public String getCol() {
      return col;
   }

   /**
    * Set the column.
    * @param col the specified column.
    */
   public void setCol(String col) {
      this.col = col;
   }

   /**
    * Get the row.
    * @return the row.
    */
   public int getRow() {
      return row;
   }

   /**
    * Set the row.
    * @param row the specified row.
    */
   public void setRow(int row) {
      this.row = row;
   }

   /**
    * Clone this object.
    * @return the cloned object.
    */
   @Override
   public Object clone() {
      try {
         return super.clone();
      }
      catch(Exception ex) {
         LOG.error("Failed to clone CellRef", ex);
      }

      return null;
   }

   /**
    * Get the hash code value.
    * @return the hash code value.
    */
   public int hashCode() {
      return col.hashCode() ^ row;
   }

   /**
    * Check if equals another object.
    * @param obj the specified object.
    * @return <tt>true</tt> if equals the object, <tt>false</tt> otherwise.
    */
   public boolean equals(Object obj) {
      if(!(obj instanceof CellRef)) {
         return false;
      }

      CellRef ref = (CellRef) obj;

      return Tool.equals(col, ref.col) && row == ref.row;
   }

   /**
    * Write the xml segment to print writer.
    * @param writer the destination print writer.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      writer.print("<cellRef row=\"" + row + "\">");
      writer.print("<![CDATA[" + col + "]]>");
      writer.println("</cellRef>");
   }

   /**
    * Method to parse an xml segment.
    * @param elem the specified xml element.
    */
   @Override
   public void parseXML(Element elem) throws Exception {
      this.row = Integer.parseInt(Tool.getAttribute(elem, "row"));
      this.col = Tool.getValue(elem);
   }

   /**
    * Get the string representation.
    * @return the string representation.
    */
   public String toString() {
      return "CellRef[" + row + ", " + col + ']';
   }

   private int row = -1;
   private String col = null;

   private static final Logger LOG =
      LoggerFactory.getLogger(CellRef.class);
}
