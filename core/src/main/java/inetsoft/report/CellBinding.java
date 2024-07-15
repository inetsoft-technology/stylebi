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
package inetsoft.report;

import inetsoft.util.Tool;
import inetsoft.util.XMLSerializable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.io.Serializable;

/**
 * Defines the per cell binding. A cell can be bound to a static text,
 * a column, a formula.
 *
 * @version 10.3
 * @author InetSoft Technology Corp
 */
public class CellBinding implements Serializable, Cloneable, XMLSerializable  {
   /**
    * Cell binding: Static text in a cell.
    */
   public static final int BIND_TEXT = 1;
   /**
    * Cell binding: Bind to a column in the data table lens.
    */
   public static final int BIND_COLUMN = 2;
   /**
    * Cell binding: Bind to a formula.
    */
   public static final int BIND_FORMULA = 3;

   /**
    * Cell binding: unknow type in a cell.
    */
   public static final int UNKNOWN = -1;
   /**
    * Cell binding: group binding in a cell.
    */
   public static final int GROUP = 1;
   /**
    * Cell binding: normal binding in a cell.
    */
   public static final int DETAIL = 2;
   /**
    * Cell binding: summary binding in a cell.
    */
   public static final int SUMMARY = 3;

   /**
    * Default constructor.
    */
   public CellBinding() {
   }

   /**
    * Create a binding with specified type.
    * @param type one of the binding types, e.g. BIND_COLUMN.
    * @param value the binding value. See getValue().
    */
   public CellBinding(int type, String value) {
      this();
      this.type = type;
      this.value = value;
   }

   /**
    * Get cell binding type.
    */
   public int getType() {
      return type;
   }

   /**
    * Set cell binding type.
    * @param type one of the binding types, e.g. BIND_COLUMN.
    */
   public void setType(int type) {
      this.type = type;
   }

   /**
    * Get cell binding structure type.
    */
   public int getBType() {
      return btype;
   }

   /**
    * Set cell binding structure type.
    * @param btype GROUP, DETAIL, or SUMMARY.
    */
   public void setBType(int btype) {
      this.btype = btype;
   }

   /**
    * Check value is as group.
    */
   public boolean isAsGroup() {
      return getBType() != GROUP ? false : asGroup;
   }

   /**
    * Set value as group.
    */
   public void setAsGroup(boolean asGroup) {
      this.asGroup = asGroup;
   }

   /**
    * Get the binding value. The meaning of the value depends on the type
    * of the binding. For text binding, the value is the static text. For
    * column binding, the value is the column name. For formula binding,
    * the value is formula string.
    */
   public String getValue() {
      return value;
   }

   /**
    * Set the value of the binding.
    */
   public void setValue(String value) {
      this.value = value;
   }

   public String getValue0() {
      return value0;
   }

   public void setValue0(String value0) {
      this.value0 = value0;
   }

   /**
    * Write data into xml format.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      writer.print("<cellBinding ");
      writeAttributes(writer);
      writer.println(">");
      writeContents(writer);
      writer.println("</cellBinding>");
   }

   protected void writeAttributes(PrintWriter writer)  {
      writer.print(" type=\"" + type + "\" btype=\"" + btype + "\"");

      if(!asGroup) {
         writer.print(" asGroup=\"" + asGroup + "\"");
      }
   }

   protected void writeContents(PrintWriter writer) {
      if(value != null) {
         writer.println("<value><![CDATA[" + value + "]]></value>");
      }

      if(formula != null) {
         writer.println("<formula><![CDATA[" + formula + "]]></formula>");
      }
   }

   /**
    * Parse xml data into object.
    */
   @Override
   public void parseXML(Element tag) throws Exception {
      parseAttributes(tag);
      parseContents(tag);
   }

   protected void parseAttributes(Element tag) throws Exception {
      String val = Tool.getAttribute(tag, "type");

      if(val != null) {
         type = Integer.parseInt(val);
      }

      val = Tool.getAttribute(tag, "btype");

      if(val != null) {
         btype = Integer.parseInt(val);
      }

      val = Tool.getAttribute(tag, "asGroup");
      asGroup = !"false".equalsIgnoreCase(val);
   }

   protected void parseContents(Element tag) throws Exception {
      value = Tool.getChildValueByTagName(tag, "value");
   }

   /**
    * Check if current binding is empty, which means no value.
    */
   public boolean isEmpty() {
      return getValue() == null || "".equals(getValue());
   }

   /**
    * Make a copy of the object.
    */
   @Override
   public Object clone() {
      try {
         CellBinding obj = (CellBinding) super.clone();

         return obj;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone cell binding", ex);
      }

      return null;
   }

   /**
    * Check the obj is eqauls with this object or not.
    */
   public boolean equalsContent(Object obj) {
      if(isEmpty() && obj == null) {
         return true;
      }

      if(!(obj instanceof CellBinding)) {
         return false;
      }

      CellBinding binding = (CellBinding) obj;
      return binding.type == type && Tool.equals(binding.value, value);
   }

   /**
    * Check the obj is eqauls with this object or not.
    */
   public boolean equals(Object obj) {
      if(!(obj instanceof CellBinding)) {
         return false;
      }

      CellBinding binding = (CellBinding) obj;
      return binding.type == type && btype == binding.btype &&
             Tool.equals(binding.value, value);
   }

   public int hashCode() {
      return type + 7 * btype + ((value != null) ? value.hashCode() : 0);
   }

   //-----------------debug-------------------------
   public String toString() {
      String val = value;
      val = val == null ? "" : val;

      switch(type) {
      case BIND_COLUMN:
         val = "[" + val + "]";
         break;
      case BIND_FORMULA:
         val = "=" + val;
         break;
      }

      return val;
   }

   /**
    * Set the formula field when the formula is generated by the column binding.
    */
   public void setFormula(String formula) {
      this.formula = formula;
   }

   private int type = BIND_TEXT;
   private String value; // text, column name, formula, or query name
   private transient String value0; // used when convert crosstab to freehand, value0 is the header which applied calc.
   private String formula;
   private int btype = DETAIL; // detail, group or summary
   // detail region, group cell binding as group value or detail value,
   // if asGroup is true, then is not show group columns, if asGroup is false,
   // then is show group column
   private boolean asGroup = true;

   private static final Logger LOG =
      LoggerFactory.getLogger(CellBinding.class);
}
