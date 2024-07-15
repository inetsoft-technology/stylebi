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
package inetsoft.uql.viewsheet.internal;

import inetsoft.util.Tool;
import org.w3c.dom.Element;

import java.io.PrintWriter;

class CrosstabCellValue extends TableCellValue {
   /**
    * The type of annotation cell.
    */
   @Override
   public int getType() {
      return CROSS_TABLE;
   }

   /**
    * Get the location of row headers.
    */
   public int getSplitIndex() {
      return splitIdx;
   }

   /**
    * Set the location of row headers.
    */
   public void setSplitIndex(int idx) {
      this.splitIdx = idx;
   }

   /**
    * Check if this cell is row header cell.
    */
   public boolean isRowHeaderCell() {
      return isRowHeader;
   }

   /**
    * Set this cell as row header cell.
    */
   public void setRowHeaderCell(boolean isRowHeader) {
      this.isRowHeader = isRowHeader;
   }

   /**
    * Check if this cell is column header cell.
    */
   public boolean isColHeaderCell() {
      return isColHeader;
   }

   /**
    * Set this cell as column header cell.
    */
   public void setColHeaderCell(boolean isColHeader) {
      this.isColHeader = isColHeader;
   }

   /**
    * Write contents.
    * @param writer the specified writer.
    */
   @Override
   protected void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      writer.print("<splitIdx><![CDATA[" + splitIdx + "]]></splitIdx>");
      writer.print("<isRowHeader><![CDATA[" + isRowHeader +
                   "]]></isRowHeader>");
      writer.print("<isColHeader><![CDATA[" + isColHeader +
                   "]]></isColHeader>");
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   @Override
   protected void parseContents(Element elem) throws Exception {
      super.parseContents(elem);

      Element node = Tool.getChildNodeByTagName(elem, "splitIdx");

      if(node != null) {
         splitIdx = Integer.parseInt(Tool.getValue(node));
      }

      node = Tool.getChildNodeByTagName(elem, "isRowHeader");

      if(node != null) {
         isRowHeader = "true".equals(Tool.getValue(node));
      }

      node = Tool.getChildNodeByTagName(elem, "isColHeader");

      if(node != null) {
         isColHeader = "true".equals(Tool.getValue(node));
      }
   }

   public boolean equals(Object obj) {
      if(!(obj instanceof CrosstabCellValue)) {
         return false;
      }

      CrosstabCellValue value = (CrosstabCellValue) obj;

      return super.equals(obj) && splitIdx == value.splitIdx &&
         Tool.equals(isRowHeader, value.isRowHeader) &&
         Tool.equals(isColHeader, value.isColHeader);
   }

   private int splitIdx = -1;
   private boolean isRowHeader;
   private boolean isColHeader;
}
