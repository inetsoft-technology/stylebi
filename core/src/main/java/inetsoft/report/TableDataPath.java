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
package inetsoft.report;

import com.fasterxml.jackson.annotation.*;
import inetsoft.uql.schema.XSchema;
import inetsoft.util.*;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.*;

/**
 * Table data path contains the structural infos of a table row/col/cell. It
 * contains infos like type, data type, etc.
 *
 * @version 6.1
 * @author InetSoft Technology Corp
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TableDataPath implements XMLSerializable, DataSerializable,
   Serializable, Cloneable
{
   /**
    * Table header row/col/cell.
    */
   public static final int HEADER = 0x0100;
   /**
    * Table detail row/col/cell.
    */
   public static final int DETAIL = 0x0200;
   /**
    * Table trailer row/col/cell.
    */
   public static final int TRAILER = 0x0400;
   /**
    * Table group header row/col/cell.
    */
   public static final int GROUP_HEADER = 0x0800;
   /**
    * Table summary row/col/cell.
    */
   public static final int SUMMARY = 0x1000;
   /**
    * Hidden Table summary row/col/cell.
    */
   public static final int HIDDEN_SUMMARY = 0x1001;
   /**
    * Table summary header row/col/cell.
    */
   public static final int SUMMARY_HEADER = 0x1200;
   /**
    * Table grand total row/col/cell.
    */
   public static final int GRAND_TOTAL = TRAILER;
   /**
    * Unknown.
    */
   public static final int UNKNOWN = 0x2000;
   /**
    * Title.
    */
   public static final int TITLE = 0x4000;
   /**
    * Calendar title.
    */
   public static final int CALENDAR_TITLE = 0x4001;
   /**
    * Year calendar.
    */
   public static final int YEAR_CALENDAR = 0x4002;
   /**
    * Month calendar.
    */
   public static final int MONTH_CALENDAR = 0x4004;
   /**
    * Object.
    */
   public static final int OBJECT = 0x8000;
   /**
    * Viewsheet component.
    */
   public static final int SHEET = 0x10000;

   /**
    * Create a default table data path.
    */
   public TableDataPath() {
      this.col = false;
      this.row = false;
      this.level = -1;
      this.index = 0;
      this.colIndex = -1;
      this.type = DETAIL;
      this.dataType = XSchema.STRING;
      this.path = new String[0];
   }

   /**
    * Create a col table data path.
    * @param header the specified column header
    */
   public TableDataPath(String header) {
      this.col = true;
      this.row = false;
      this.level = -1;
      this.index = 0;
      this.colIndex = -1;
      this.type = UNKNOWN;
      this.dataType = XSchema.STRING;
      this.path = new String[] {header};
   }

   /**
    * Create a row table data path.
    * @param level the specified table data path level
    * @param type the specified table data path type
    */
   public TableDataPath(int level, int type) {
      this(level, type, 0);
   }

   /**
    * Create a row table data path.
    * @param level the specified table data path level
    * @param type the specified table data path type
    * @param index row index.
    */
   public TableDataPath(int level, int type, int index) {
      this.col = false;
      this.row = true;
      this.level = level;
      this.index = index;
      this.colIndex = -1;
      this.type = type;
      this.dataType = XSchema.STRING;
      this.path = new String[0];
   }

   /**
    * Create a cell table data path.
    * @param level the specified table data path level
    * @param type the specified table data path type
    * @param dataType the specified data type, which is defined in <tt>XSchema</tt>
    * @param path of the table data path from root to leaf as a string array
    */
   public TableDataPath(int level, int type, String dataType, String[] path) {
      this(level, type, dataType, path, false, false);
   }

   /**
    * Create a table data path.
    * @param level the specified table data path level
    * @param type the specified table data path type
    * @param dataType the specified data type, which is defined in <tt>XSchema</tt>
    * @param path of the table data path from root to leaf as a string array
    */
   public TableDataPath(int level, int type, String dataType, String[] path,
                        boolean row, boolean col) {
      if(row && col) {
         throw new RuntimeException("Path should not be both row and col path");
      }
      // cell data path must set the path
      else if(!row && !col && path == null) {
         throw new RuntimeException("Path should not be null");
      }

      this.col = col;
      this.row = row;
      this.level = level;
      this.index = 0;
      this.colIndex = -1;
      this.type = type;
      this.dataType = dataType;
      this.path = path;
   }

   /**
    * Check if is a row table data path.
    * @return true if is a row table data path, false otherwise.
    */
   @JsonInclude(JsonInclude.Include.NON_DEFAULT)
   public boolean isRow() {
      return row;
   }

   /**
    * Set if is a row table data path.
    * @param row true if is a row table data path, false otherwise.
    */
   public void setRow(boolean row) {
      this.row = row;
   }

   /**
    * Check if is a col table data path.
    * @return true if is a col table data path, false otherwise.
    */
   @JsonInclude(JsonInclude.Include.NON_DEFAULT)
   public boolean isCol() {
      return col;
   }

   /**
    * Set if is a col table data path.
    * @param col true if is a col table data path, false otherwise.
    */
   public void setCol(boolean col) {
      this.col = col;
   }

   /**
    * Check if is a cell table data path.
    * @return true if is a cell table data path, false otherwise.
    */
   @JsonIgnore
   public boolean isCell() {
      return !row && !col;
   }

   /**
    * Check if this table data path is special.
    * @return <tt>true</tt> if special, <tt>false</tt> otherwise.
    */
   @JsonIgnore
   public boolean isSpecial() {
      return type != HEADER && type != DETAIL && type != TRAILER &&
             type != GROUP_HEADER && type != SUMMARY && type != GRAND_TOTAL;
   }

   /**
    * Set table data path level, which is useful for nested table.
    * @param level table data path level
    */
   public void setLevel(int level) {
      this.level = level;
   }

   /**
    * Get table data path level, which is useful for nested table.
    * @return table data path level
    */
   public int getLevel() {
      return level;
   }

   /**
    * Set the row index of this data path.
    * @param index row index the specified row index.
    */
   public int setIndex(int index) {
      return this.index = index;
   }

   /**
    * Get the row index of this data path.
    * @return row index.
    */
   public int getIndex() {
      return index;
   }

   /**
    * Get the col index of this data path.
    * @return col index.
    */
   public int getColIndex() {
      return colIndex;
   }

   /**
    * Set the col index to this data path.
    * @param colIndex col index the specified column index.
    */
   public void setColIndex(int colIndex) {
      this.colIndex = colIndex;
   }

   /**
    * Get table data path type, which is one of the types defined in
    * TableDataPath like <tt>HEADER</tt>, <tt>GROUP_TOTAL</tt>, etc.
    * @return table data path type
    */
   public int getType() {
      return type;
   }

   /**
    * Set table data path type, which is one of the types defined in
    * TableDataPath like <tt>HEADER</tt>, <tt>GROUP_TOTAL</tt>, etc.
    * @param type table data path type
    */
   public void setType(int type) {
      this.type = type;
   }

   /**
    * Get data type of the table data path.
    * @return data type of the table data path
    */
   public String getDataType() {
      return dataType;
   }

   /**
    * Set data type of the table data path.
    * @param dataType data type of the table data path
    */
   public void setDataType(String dataType) {
      this.dataType = dataType;
   }

   /**
    * Get path of the table data path.
    * @return path of the table data path from root to leaf as a string array
    */
   public String[] getPath() {
      return path;
   }

   /**
    * Set path of the table data path.
    * @param path path of the table data path from root to leaf as a string array
    */
   public void setPath(String[] path) {
      this.path = path;
   }

   /**
    * Write data to a DataOutputStream.
    * @param output the destination DataOutputStream.
    */
   @Override
   public void writeData(DataOutputStream output) throws IOException {
      output.writeInt(level);
      output.writeInt(index);
      output.writeBoolean(col);
      output.writeBoolean(row);
      output.writeInt(type);

      output.writeBoolean(dataType == null);

      if(dataType != null) {
         output.writeUTF(dataType);
      }

      output.writeInt(path.length);

      for(int i = 0; i < path.length; i++) {
         boolean isNull = path[i] == null;
         output.writeBoolean(isNull);

         if(!isNull) {
            output.writeUTF(path[i]);
         }
      }
   }

   /**
    * Parse data from an InputStream.
    * @param input the source DataInputStream.
    * @return <tt>true</tt> if successfully parsed, <tt>false</tt> otherwise.
    */
   @Override
   public boolean parseData(DataInputStream input) {
      return true;
   }

   /**
    * Write the xml segment to print writer.
    * @param writer the destination print writer.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      writer.println("<tableDataPath>");

      writer.print("<level>");
      writer.print(level);
      writer.println("</level>");

      writer.print("<index>");
      writer.print(index);
      writer.println("</index>");

      writer.print("<col>");
      writer.print(col);
      writer.println("</col>");

      writer.print("<row>");
      writer.print(row);
      writer.println("</row>");

      writer.print("<type>");
      writer.print(type);
      writer.println("</type>");

      writer.print("<dataType>");
      writer.print(dataType);
      writer.println("</dataType>");

      writer.println("<path>");

      for(int i = 0; i < path.length; i++) {
         writer.print("<aPath>");
         writer.print("<![CDATA[" + path[i] + "]]>");
         writer.println("</aPath>");
      }

      writer.println("</path>");

      writer.println("</tableDataPath>");
   }

   /**
    * Method to parse an xml segment.
    * @param tag the specified xml element
    */
   @Override
   public void parseXML(Element tag) throws Exception {
      Element levelnode = Tool.getChildNodeByTagName(tag, "level");
      Element indexnode = Tool.getChildNodeByTagName(tag, "index");
      Element colnode = Tool.getChildNodeByTagName(tag, "col");
      Element rownode = Tool.getChildNodeByTagName(tag, "row");
      Element typenode = Tool.getChildNodeByTagName(tag, "type");
      Element dataTypeNode = Tool.getChildNodeByTagName(tag, "dataType");
      Element pathnode = Tool.getChildNodeByTagName(tag, "path");
      NodeList pathnodes = Tool.getChildNodesByTagName(pathnode, "aPath");

      if(levelnode != null) {
         this.level = Integer.parseInt(Tool.getValue(levelnode));
      }

      if(indexnode != null) {
         this.index = Integer.parseInt(Tool.getValue(indexnode));
      }

      if(colnode != null) {
         this.col = Tool.getValue(colnode).equals("true");
      }

      if(rownode != null) {
         this.row = Tool.getValue(rownode).equals("true");
      }

      if(typenode != null) {
         this.type = Integer.parseInt(Tool.getValue(typenode));
      }

      if(dataTypeNode != null) {
         this.dataType = Tool.getValue(dataTypeNode);
      }

      this.path = new String[pathnodes.getLength()];

      for(int i = 0; i < pathnodes.getLength(); i++) {
         Element apathnode = (Element) pathnodes.item(i);
         this.path[i] = Tool.getValue(apathnode);
         this.path[i] = this.path[i] == null ? "" : this.path[i];
      }
   }

   /**
    * Check if equals another object.
    * @return ture if equals, false otherwise
    */
   public boolean equals(Object obj) {
      if(!(obj instanceof TableDataPath)) {
         return false;
      }

      TableDataPath tpath2 = (TableDataPath) obj;

      if(level != tpath2.level || type != tpath2.type || col != tpath2.col ||
         row != tpath2.row || path.length != tpath2.path.length ||
         index != tpath2.index || colIndex != tpath2.colIndex)
      {
         return false;
      }

      for(int i = 0; i < path.length; i++) {
         if(!path[i].equals(tpath2.path[i])) {
            return false;
         }
      }

      return true;
   }

   /**
    * Get hash code of the table data path for map.
    * @return hash code of the table data path
    */
   public int hashCode() {
      int hash = level + index + (col ? 107 : 0) + (row ? 701 : 0) + type;

      for(int i = 0; i < path.length; i++) {
         hash += path[i].hashCode();
      }

      return hash;
   }

   /**
    * Return the string representation.
    */
   public String toString() {
      Catalog catalog = Catalog.getCatalog();
      StringBuilder sb = new StringBuilder();

      switch(type) {
      case TITLE:
         sb.append(catalog.getString("Title") + " ");
         break;
      case HEADER:
         sb.append(catalog.getString("Header") + " ");
         break;
      case DETAIL:
         sb.append(catalog.getString("Detail") + " ");
         break;
      case GROUP_HEADER:
         sb.append(catalog.getString("Group Header"));

         if(level >= 0) {
            sb.append("-" + level);
         }

         sb.append(" ");
         break;
      case SUMMARY:
         sb.append(catalog.getString("Summary"));

         if(level >= 0) {
            sb.append("-" + level);
         }

         sb.append(" ");
         break;
      case GRAND_TOTAL:
         sb.append(catalog.getString("Grand Total") + " ");
         break;
      case SUMMARY_HEADER:
         sb.append(catalog.getString("Summary Header") + " ");
         break;
      }

      if(isRow()) {
         sb.append(catalog.getString("Row") + "-" + index);
      }
      else if(isCol()) {
         sb.append(catalog.getString("Column"));
      }
      else {
         sb.append(catalog.getString("Cell"));
      }

      if(path.length > 0) {
         sb.append(" [");

         for(int i = 0; i < path.length; i++) {
            if(i > 0) {
               sb.append("-");
            }

            sb.append(path[i]);
         }

         sb.append("] ");
      }

      return sb.toString();
   }

   /**
    * Clone the table data path.
    * @return the cloned table data path
    */
   @Override
   public Object clone() {
      return clone(null);
   }

   /**
    * Clone the table data path.
    * @return the cloned table data path
    */
   public Object clone(String[] arr) {
      TableDataPath tpath2 = new TableDataPath();

      tpath2.level = level;
      tpath2.index = index;
      tpath2.col = col;
      tpath2.row = row;
      tpath2.type = type;
      tpath2.dataType = dataType;

      if(arr == null) {
         tpath2.path = new String[path.length];
         System.arraycopy(path, 0, tpath2.path, 0, path.length);
      }
      else {
         tpath2.path = arr;
      }

      return tpath2;
   }

   private int level;
   private boolean col;
   private boolean row;
   private int type;
   private String dataType;
   private String[] path;
   private int index;
   private int colIndex;
 }
