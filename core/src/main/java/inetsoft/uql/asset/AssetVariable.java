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
package inetsoft.uql.asset;

import inetsoft.uql.erm.AbstractDataRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.UserVariable;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.io.PrintWriter;

/**
 * Asset variable represents a user defined variable.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public class AssetVariable extends UserVariable {
   /**
    * Constructor.
    */
   public AssetVariable() {
      super();
   }

   /**
    * Constructor.
    */
   public AssetVariable(String name) {
      super(name);
   }

   /**
    * Get the display style.
    * @return the display style.
    */
   @Override
   public int getDisplayStyle() {
      return style;
   }

   /**
    * Set the display style.
    * @param style the specified display style.
    */
   public void setDisplayStyle(int style) {
      this.style = style;
   }

   /**
    * Get the table name.
    * @return the table assembly.
    */
   public String getTableName() {
      return table;
   }

   /**
    * Set the table name.
    * @param table the specified table assembly.
    */
   public void setTableName(String table) {
      if(table != null && table.trim().length() == 0) {
         table = null;
      }

      this.table = table;
   }

   /**
    * Get the table assembly.
    * @return the table assembly.
    */
   public TableAssembly getTable() {
      return tassembly;
   }

   /**
    * Get the value attribute.
    * @return the value attribute.
    */
   public DataRef getValueAttribute() {
      return vattr;
   }

   /**
    * Set the value attribute.
    * @param vattr the specified value attribute.
    */
   public void setValueAttribute(DataRef vattr) {
      this.vattr = vattr;
   }

   /**
    * Get the label attribute.
    * @return the label attribute.
    */
   public DataRef getLabelAttribute() {
      return lattr;
   }

   /**
    * Set the label attribute.
    * @param lattr the specified label attribute.
    */
   public void setLabelAttribute(DataRef lattr) {
      this.lattr = lattr;
   }

   /**
    * Check is the value of the variable needs to be enter by users.
    */
   public boolean isPromptWithDValue() {
      return promptWithDValue;
   }

   /**
    * Update the assembly.
    * @param ws the associated worksheet.
    * @return <tt>true</tt> if successful, <tt>false</tt> otherwise.
    */
   public boolean update(Worksheet ws) {
      if(table == null) {
         return true;
      }

      WSAssembly assembly = ws == null ?
         null : (WSAssembly) ws.getAssembly(table);

      if(assembly == null) {
         LOG.warn("No available assembly: " + table);
         return false;
      }

      if(!assembly.isTable()) {
         LOG.warn("Invalid assembly found: " + assembly);
         return false;
      }

      this.tassembly = (TableAssembly) assembly.clone();

      return true;
   }

   /**
    * Write contents.
    * @param writer the specified print writer.
    */
   @Override
   protected void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      if(table != null) {
         writer.println("<tableAssembly><![CDATA[" + table +
                        "]]></tableAssembly>");
      }

      if(vattr != null) {
         writer.print("<valueAttribute>");
         vattr.writeXML(writer);
         writer.println("</valueAttribute>");
      }

      if(lattr != null) {
         writer.print("<labelAttribute>");
         lattr.writeXML(writer);
         writer.println("</labelAttribute>");
      }
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   @Override
   protected void parseContents(Element elem) throws Exception {
      super.parseContents(elem);

      table = Tool.getChildValueByTagName(elem, "tableAssembly");

      Element vnode = Tool.getChildNodeByTagName(elem, "valueAttribute");

      if(vnode != null) {
         vnode = Tool.getFirstChildNode(vnode);
         vattr = AbstractDataRef.createDataRef(vnode);
      }

      Element lnode = Tool.getChildNodeByTagName(elem, "labelAttribute");

      if(lnode != null) {
         lnode = Tool.getFirstChildNode(lnode);
         lattr = AbstractDataRef.createDataRef(lnode);
      }
   }

   /**
    * Write attributes.
    * @param writer the specified print writer.
    */
   @Override
   protected void writeAttributes(PrintWriter writer) {
      super.writeAttributes(writer);
      writer.print(" style=\"" + style + "\"");
   }

   /**
    * Parse attributes.
    * @param elem the specified xml element.
    */
   @Override
   protected void parseAttributes(Element elem) throws Exception {
      super.parseAttributes(elem);
      style = Integer.parseInt(Tool.getAttribute(elem, "style"));
   }

   /**
    * Check if equals another object.
    * @return true if equals, false otherwise.
    */
   public boolean equals(Object obj) {
      if(!super.equals(obj)) {
         return false;
      }

      if(!(obj instanceof AssetVariable)) {
         return false;
      }

      AssetVariable var2 = (AssetVariable) obj;
      return style == var2.style && Tool.equals(table, var2.table) &&
         Tool.equals(vattr, var2.vattr) && Tool.equals(lattr, var2.lattr);
   }

   /**
    * Get the string representation.
    * @return the string representation.
    */
   public String toString() {
      String str;
      Catalog catalog = Catalog.getCatalog();

      switch(style) {
      case NONE:
         str = catalog.getString("Text Input");
         break;
      case COMBOBOX:
         str = catalog.getString("Combo Box");
         break;
      case LIST:
         str = catalog.getString("List");
         break;
      case RADIO_BUTTONS:
         str = catalog.getString("Radio Buttons");
         break;
      case CHECKBOXES:
         str = catalog.getString("Checkboxes");
         break;
      case DATE_COMBOBOX:
         str = catalog.getString("Date Combo Box");
         break;
      default:
         throw new RuntimeException("Unsupported style found: " + style);
      }

      return getName() + "[" + str + "]";
   }

   private int style; // display style
   private String table; // table assembly
   private DataRef vattr; // value attribute
   private DataRef lattr; // label attribute
   private transient boolean promptWithDValue = false;

   private transient TableAssembly tassembly = null; // runtime table assembly

   private static final Logger LOG =
      LoggerFactory.getLogger(AssetVariable.class);
}
