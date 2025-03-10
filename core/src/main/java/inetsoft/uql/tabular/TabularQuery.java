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
package inetsoft.uql.tabular;

import inetsoft.uql.VariableTable;
import inetsoft.uql.XQuery;
import inetsoft.uql.path.XSelection;
import inetsoft.uql.schema.*;
import inetsoft.uql.tabular.impl.TabularHandler;
import inetsoft.util.Tool;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.PrintWriter;
import java.util.*;

/**
 * This is the base class for defining a tabular query.
 *
 * @version 12.0, 11/15/2013
 * @author InetSoft Technology Corp
 */
public abstract class TabularQuery extends XQuery {
   public TabularQuery(String type) {
      super(type);
   }

   public void loadOutputColumns(VariableTable vtable) throws Exception {
      TabularHandler handler = new TabularHandler();
      int maxRows = getMaxRows();
      setMaxRows(100);

      try {
         // not need to get most up-to-date data for refreshing columns.
         vtable.put(XQuery.HINT_PREVIEW, "true");
         handler.execute(this, vtable, null, null);
      }
      finally {
         vtable.remove(XQuery.HINT_PREVIEW);
         setMaxRows(maxRows);
      }
   }

   /**
    * Return the output columns of the query. If the query implementation is
    * able to find the column information, it should return the columns
    * here. Otherwise, the column information will be captured when a query
    * is previewed.
    */
   public XTypeNode[] getOutputColumns() {
      return cols;
   }

   /**
    * Set the output columns.
    */
   public void setOutputColumns(XTypeNode[] cols) {
      this.cols = cols;
   }

   /**
    * Get the output meta data of query (pre-selection).
    */
   @Override
   public XTypeNode getOutputType(Object session, boolean full) {
      XTypeNode root = new XTypeNode("table");
      XTypeNode[] cols = getOutputColumns();

      if(cols != null) {
         for(XTypeNode node : getOutputColumns()) {
            root.addChild(node);
         }
      }

      return root;
   }

   @Override
   protected void findVariables(Map varmap) {
      for(UserVariable var: TabularUtil.findVariables(this)) {
         addVariable(var);
      }
   }

   /**
    * Get the XSelection object representing the selected columns.
    */
   @Override
   public XSelection getSelection() {
      return null;
   }

   /**
    * Set the column type to use for data conversion.
    * @param header column full header, e.g. path in json.
    * @param type data type in XSchema.
    */
   public void setColumnType(String header, String type) {
      if(type == null) {
         typemap.remove(header);
      }
      else {
         typemap.put(header, type);
      }
   }

   /**
    * Get the column type to use for data conversion.
    */
   public String getColumnType(String header) {
      return typemap.get(header);
   }

   /**
    * Get the names that have explicit types set through setColumnType().
    */
   public Collection<String> getTypedColumns() {
      return typemap.keySet();
   }

   /**
    * Set the format for the column used for type conversion.
    * @param header column full header.
    * @param format format used to TableFormat.getFormat() call.
    */
   public void setColumnFormat(String header, String format) {
      if(format == null) {
         fmtmap.remove(header);
      }
      else {
         fmtmap.put(header, format);
      }
   }

   /**
    * Get the format for the column used for type conversion.
    */
   public String getColumnFormat(String header) {
      return fmtmap.get(header);
   }

   /**
    * Set the formatExtent extent for the column used for type conversion.
    * @param header column full header.
    * @param formatExtent formatExtent extent used to TableFormat.getFormat() call.
    */
   public void setColumnFormatExtent(String header, String formatExtent) {
      if(formatExtent == null) {
         extentmap.remove(header);
      }
      else {
         extentmap.put(header, formatExtent);
      }
   }

   /**
    * Get the format extent for the column used for type conversion.
    */
   public String getColumnFormatExtent(String header) {
      return extentmap.get(header);
   }

   /**
    * Get the assets referenced by this query.
    * @param assets a list of all tables in same worksheet.
    */
   public String[] getDependedAssets(String[] assets) {
      return new String[0];
   }

   @Override
   public final void writeXML(PrintWriter writer) {
      writer.print("<query_" + getType() + " ");
      writeAttributes(writer);
      writer.println(">");

      super.writeXML(writer);
      writeContents(writer);
      writer.println("</query_" + getType() + ">");
   }

   @Override
   public final void parseXML(Element root) throws Exception {
      super.parseXML(root);
      parseAttributes(root);
      parseContents(root);
   }

   /**
    * Write the attributes of the XML tag.
    */
   protected void writeAttributes(PrintWriter writer) {
      writer.print(" class=\"" + this.getClass().getName() + "\"");
   }

   /**
    * Write the contents of the XML tag.
    */
   protected void writeContents(PrintWriter writer) {
      writer.println("<outputColumns>");

      if(cols != null) {
         for(XTypeNode col : cols) {
            if(col != null) {
               col.writeXML(writer);
            }
         }
      }

      writer.println("</outputColumns>");

      writer.println("<columnTypes>");

      for(String col : typemap.keySet()) {
         writer.println("<columnType>");
         writer.println("<name><![CDATA[" + col + "]]></name>");
         writer.println("<type><![CDATA[" + typemap.get(col) + "]]></type>");
         writer.println("</columnType>");
      }

      writer.println("</columnTypes>");

      writer.println("<columnFormats>");

      for(String col : fmtmap.keySet()) {
         writer.println("<columnFormat>");
         writer.println("<name><![CDATA[" + col + "]]></name>");
         writer.println("<format><![CDATA[" + fmtmap.get(col) + "]]></format>");
         writer.println("</columnFormat>");
      }

      writer.println("</columnFormats>");

      writer.println("<columnFormatExtents>");

      for(String col : extentmap.keySet()) {
         writer.println("<columnFormatExtent>");
         writer.println("<name><![CDATA[" + col + "]]></name>");
         writer.println("<FormatExtent><![CDATA[" + extentmap.get(col) + "]]></FormatExtent>");
         writer.println("</columnFormatExtent>");
      }

      writer.println("</columnFormatExtents>");
   }

   /**
    * Parse the attributes of the XML tag.
    */
   protected void parseAttributes(Element tag) throws Exception {
   }

   /**
    * Parse the contents of the XML tag.
    */
   protected void parseContents(Element tag) throws Exception {
      Element elem = Tool.getChildNodeByTagName(tag, "outputColumns");

      if(elem != null) {
         NodeList list = Tool.getChildNodesByTagName(elem, "element");
         cols = new XTypeNode[list.getLength()];

         for(int i = 0; i < cols.length; i++) {
            Element node = (Element) list.item(i);
            cols[i] = XSchema.createPrimitiveType(Tool.getAttribute(node, "type"));
            cols[i].setName(Tool.getAttribute(node, "name"));
         }
      }

      elem = Tool.getChildNodeByTagName(tag, "columnTypes");

      if(elem != null) {
         NodeList list = Tool.getChildNodesByTagName(elem, "columnType");

         for(int i = 0; i < list.getLength(); i++) {
            Element node = (Element) list.item(i);
            typemap.put(Tool.getChildValueByTagName(node, "name"),
                        Tool.getChildValueByTagName(node, "type"));
         }
      }

      elem = Tool.getChildNodeByTagName(tag, "columnFormats");

      if(elem != null) {
         NodeList list = Tool.getChildNodesByTagName(elem, "columnFormat");

         for(int i = 0; i < list.getLength(); i++) {
            Element node = (Element) list.item(i);
            fmtmap.put(Tool.getChildValueByTagName(node, "name"),
                       Tool.getChildValueByTagName(node, "format"));
         }
      }

      elem = Tool.getChildNodeByTagName(tag, "columnFormatExtents");

      if(elem != null) {
         NodeList list = Tool.getChildNodesByTagName(elem, "columnFormatExtent");

         for(int i = 0; i < list.getLength(); i++) {
            Element node = (Element) list.item(i);
            fmtmap.put(Tool.getChildValueByTagName(node, "name"),
                       Tool.getChildValueByTagName(node, "FormatExtent"));
         }
      }
   }

   /**
    * Copy query properties from an existing query.
    */
   public void copyInfo(TabularQuery query) {
   }

   @Override
   public TabularQuery clone() {
      TabularQuery copy = (TabularQuery) super.clone();
      copy.typemap = new HashMap<>(typemap);
      copy.fmtmap = new HashMap<>(fmtmap);
      copy.extentmap = new HashMap<>(extentmap);

      if(cols != null) {
         copy.cols = Arrays.copyOf(cols, cols.length);
      }

      return copy;
   }

   private XTypeNode[] cols;
   private Map<String, String> typemap = new HashMap<>();
   private Map<String, String> fmtmap = new HashMap<>();
   private Map<String, String> extentmap = new HashMap<>();
   public static final String OUTER_TABLE_NAME_PROPERTY_PREFIX = "outer.table.name.";
   public static final String IS_OUTER_TABLE = "__is_outer_table__";
}
