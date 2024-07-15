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
package inetsoft.uql.asset.internal;

import inetsoft.report.internal.table.TableFormat;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.XFormatInfo;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.util.Tool;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.PrintWriter;
import java.text.Format;
import java.util.*;

/**
 * UnpivotTableAssemblyInfo stores basic un-pivot table assembly information.
 *
 * @version 11.2
 * @author InetSoft Technology Corp
 */
public class UnpivotTableAssemblyInfo extends ComposedTableAssemblyInfo {
   /**
    * Constructor.
    */
   public UnpivotTableAssemblyInfo() {
      super();
   }

   /**
    * Check if is composed.
    * @return <tt>true</tt> if composed, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isComposed() {
      return false;
   }

   /**
    * Set header columns.
    * @param hcol the specified header columns count.
    */
   public void setHeaderColumns(int hcol) {
      this.hcol = hcol;
   }

   /**
    * Get header columns.
    * @return header columns count.
    */
   public int getHeaderColumns() {
      return hcol;
   }

   /**
    * Change the column type to new type and parse the data by the format.
    *
    * @param col the specified column to be changed type.
    * @param type the type the column will be changed.
    * @param format the specified format.
    */
   public void setColumnType(DataRef col, String type, XFormatInfo format) {
      setColumnType(col, type, format, true);
   }

   /**
    * Change the column type to new type and parse the data by the format.
    *
    * @param col the specified column to be changed type.
    * @param type the type the column will be changed.
    * @param format the specified format.
    * @param force <tt>true</tt> ignore the parse data error. else show error.
    */
   public void setColumnType(DataRef col, String type, XFormatInfo format, boolean force) {
      ColumnSelection privateColumnSelection = getPrivateColumnSelection();

      if(privateColumnSelection == null) {
         return;
      }

      DataRef attribute = privateColumnSelection.findAttribute(col);

      if(!(attribute instanceof ColumnRef)) {
         return;
      }

      ((ColumnRef) attribute).setDataType(type);
      ChangeColumnItem item = new ChangeColumnItem(type, format, force);
      changedTypeCols.put(col.getName(),  item);
   }

   /**
    * Whether the column is changed type.
    *
    * @param col the specified column.
    * @return
    */
   public boolean columnTypeChanged(DataRef col) {
      if(col == null) {
         return false;
      }

      return changedTypeCols.get(col.getName()) != null;
   }

   /**
    * Get the column changed format info.
    *
    * @param col the specified column.
    * @return
    */
   public XFormatInfo getChangedTypeColumnFormatInfo(String col) {
      ChangeColumnItem item = changedTypeCols.get(col);
      Object formatInfo = item == null ? null : item.format;

      if(!(formatInfo instanceof XFormatInfo)) {
         return null;
      }

      return (XFormatInfo) formatInfo;
   }

   /**
    * Get the column changed format.
    *
    * @param col the specified column.
    * @return
    */
   public Format getChangedTypeColumnFormat(DataRef col) {
      return getChangedTypeColumnFormat(col.getName());
   }

   /**
    * Get the column changed format.
    *
    * @param col the specified column name.
    * @return
    */
   public Format getChangedTypeColumnFormat(String col) {
      ChangeColumnItem item = changedTypeCols.get(col);

      if(item == null) {
         return null;
      }

      Object formatInfo = item.format;

      if(!(formatInfo instanceof XFormatInfo)) {
         return null;
      }

      XFormatInfo xFormatInfo = (XFormatInfo) formatInfo;

      if(xFormatInfo != null && !Tool.isEmptyString(xFormatInfo.getFormat())) {
         return TableFormat.getFormat(xFormatInfo.getFormat(), xFormatInfo.getFormatSpec());
      }

      return null;
   }

   public List<String> getChangedTypeCols() {
      return new ArrayList<>(changedTypeCols.keySet());
   }

   /**
    * Get the type of the changed column.
    */
   public String getChangedColType(String colName) {
      ChangeColumnItem changeColumnItem = changedTypeCols.get(colName);

      return changeColumnItem == null ? null : changeColumnItem.type;
   }

   /**
    * Whether force parse the column to the set type.
    *
    * @param col the specified column.
    * @return
    */
   public boolean forceParseDataByFormat(DataRef col) {
      return forceParseDataByFormat(col.getName());
   }

   /**
    * Whether force parse the column to the set type.
    *
    * @param columnName the specified column name.
    * @return
    */
   public boolean forceParseDataByFormat(String columnName) {
      ChangeColumnItem changeColumnItem = changedTypeCols.get(columnName);

      return changeColumnItem != null && changeColumnItem.forceParse;
   }

   /**
    * Write attributes.
    * @param writer the specified writer.
    */
   @Override
   protected void writeAttributes(PrintWriter writer) {
      super.writeAttributes(writer);
      writer.print(" hcol=\"" + hcol + "\"");
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   @Override
   protected void parseAttributes(Element elem) {
      super.parseAttributes(elem);

      String attr = Tool.getAttribute(elem, "hcol");

      if(attr != null) {
         hcol = Integer.parseInt(attr);
      }
   }

   @Override
   protected void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      if(changedTypeCols != null) {
         writer.print("<changedTypeCols>");

         for(Map.Entry<String, ChangeColumnItem> entry : changedTypeCols.entrySet()) {
            ChangeColumnItem column = entry.getValue();
            writer.print("<column name=\"" + entry.getKey() +
               "\" force=\"" + column.forceParse +"\" type=\"" + column.type + "\">");

            if(column.format instanceof XFormatInfo) {
               ((XFormatInfo) column.format).writeXML(writer);
            }

            writer.print("</column>");
         }

         writer.print("</changedTypeCols>");
      }
   }

   @Override
   protected void parseContents(Element elem) throws Exception {
      super.parseContents(elem);
      Element colsFormatEle = Tool.getChildNodeByTagName(elem, "changedTypeCols");

      if(colsFormatEle != null) {
         NodeList columns = Tool.getChildNodesByTagName(colsFormatEle, "column");

         if(columns != null) {
            for(int i = 0; i < columns.getLength(); i++) {
               if(!(columns.item(i) instanceof Element)) {
                  continue;
               }

               Element colEle = (Element) columns.item(i);
               String columnName = colEle.getAttribute("name");
               String type = colEle.getAttribute("type");
               String forceStr = colEle.getAttribute("force");
               boolean force = true;

               if(forceStr != null) {
                  try {
                     force = Tool.getBooleanData(forceStr);
                  }
                  catch(Exception ignore) {
                  }
               }

               Element fmtNode = Tool.getChildNodeByTagName(colEle, "XFormatInfo");
               XFormatInfo xFormatInfo = new XFormatInfo();

               if(fmtNode != null) {
                  xFormatInfo.parseXML(fmtNode);
               }

               ChangeColumnItem item = new ChangeColumnItem(type, xFormatInfo, force);
               changedTypeCols.put(columnName, item);
            }
         }
      }
   }

   @Override
   public Object clone(boolean recursive) {
      UnpivotTableAssemblyInfo clone = (UnpivotTableAssemblyInfo) super.clone(recursive);

      if(changedTypeCols != null) {
         clone.changedTypeCols = Tool.deepCloneMap(changedTypeCols);
      }

      return clone;
   }

   private class ChangeColumnItem {
      public ChangeColumnItem(String type, Object format, boolean forceParse) {
         this.format = format;
         this.forceParse = forceParse;
         this.type = type;
      }

      private Object format;
      private boolean forceParse = true;
      private String type;
   }

   private int hcol;
   private Map<String, ChangeColumnItem> changedTypeCols = new HashMap<>();
}
