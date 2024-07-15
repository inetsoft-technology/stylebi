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
package inetsoft.uql.jdbc;

import inetsoft.uql.util.XUtil;
import inetsoft.util.Tool;
import inetsoft.util.XMLSerializable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.PrintWriter;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.Vector;

/**
 * The XExpression used to describe the expression attribute of XFilterNode.
 * There are three types of expressions: subquery, field, and other.
 * A subquery expression contains a UniformSQL as its value. A field
 * expression contains a qualified column name. An other expression
 * contains any constant value.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class XExpression implements Cloneable, Serializable, XMLSerializable {
   /**
    * Field expression type.
    */
   public static final String FIELD = "Field";
   /**
    * Subquery expression type.
    */
   public static final String SUBQUERY = "Subquery";
   /**
    * 'Expression' expression type.
    */
   public static final String EXPRESSION = "Expression";
   /**
    * 'Value' expression type.
    */
   public static final String VALUE = "Value";
   /**
    * 'Variable' expression type.
    */
   public static final String VARIABLE = "Variable";
   /**
    * XML tag of expression
    */
   public static final String XML_TAG = "expression";

   /**
    * No quote.
    */
   public static final int QUOTE_NONE = 0;

   /**
    * Double quotation marks.
    */
   public static final int QUOTE_DOUBLE = 1;
   /**
    * Single quotation marks.
    */
   public static final int QUOTE_SINGLE = 2;

   /**
    * Create an empty expression.
    */
   public XExpression() {
      super();
   }

   /**
    * Create an expression of specified type.
    */
   public XExpression(Object value, String type) {
      setValue(value, type);
   }

   /**
    * Set quote type.
    */
   public void setQuote(int quote) {
      this.quote = quote;
   }

   /**
    * Get quote type.
    */
   public int getQuote() {
      return quote;
   }

   /**
    * Get quoted value.
    */
   public String getQuotedValue() {
      if(quote == QUOTE_NONE) {
         String value = toString();

         // @by larryl, if this string is a field, need to handle special
         // characters in the table/col name
         if(FIELD.equals(type)) {
            value = XUtil.quoteName(value, null);
         }

         return value;
      }
      else if(quote == QUOTE_DOUBLE) {
         return '\"' + toString() + '\"';
      }
      else if(quote == QUOTE_SINGLE) {
         return '`' + toString() + '`';
      }
      else {
         throw new RuntimeException("Unsupported quote type found: " + quote);
      }
   }

   public void setValue(Object value) {
      this.value = (value != null) ? value : "";
   }

   /**
    * Set the value and type of this expression.
    */
   public void setValue(Object value, String type) {
      if(type.equals(EXPRESSION) || type.equals(VALUE) || type.equals(SUBQUERY)) {
         this.type = type;
      }
      else {
         this.type = FIELD;
      }

      this.value = (value != null) ? value : "";
   }

   /**
    * Get the value of expression.
    * @return expression value
    */
   public Object getValue() {
      return value;
   }

   /**
    * Get the type of this expression.
    */
   public String getType() {
      return type;
   }

   public String toString() {
      String str = "";

      if(value != null) {
         if(type.equals(FIELD)) {
            str = (value != null ? value.toString() : "");
         }
         else if(type.equals(SUBQUERY)) {
            str = "(" + ((UniformSQL) value).toString() + ")";
         }
         else if(type.equals(EXPRESSION) || type.equals(VALUE)) {
            str = toString(value);
         }
         else {
            str = "";
         }

         return str;
      }

      return "";
   }

   /**
    * Get the string representation.
    */
   public String toString(Object value) {
      if(value instanceof Object[]) {
         Object[] arr = (Object[]) value;
         StringBuilder sb = new StringBuilder();

         for(int i = 0; i < arr.length; i++) {
            if(i > 0) {
               sb.append(',');
            }

            String text = toString(arr[i]);
            sb.append(text);
         }

         return sb.toString();
      }

      return value == null ? "" : value.toString();
   }

   /**
    * Parse the XML element that contains information on this express.
    */
   @Override
   public void parseXML(Element node) throws Exception {
      type = node.getAttribute("type");

      // @by vincentx, 2004-08-17
      // handles backward compatibility of xexpression types
      if(type.equalsIgnoreCase("OTHER")) {
          String val = Tool.getValue(node);

          if(XUtil.parseDate(val) != null) {
             type = VALUE;
             value = XUtil.parseDate(val);
          }
          else {
             type = EXPRESSION;
             value = val;
          }
      }
      else if(type.equals(SUBQUERY)) {
         NodeList nlist = Tool.getChildNodesByTagName(node, UniformSQL.XML_TAG);

         if(nlist != null) {
            UniformSQL subquery = new UniformSQL();

            subquery.parseXML((Element) nlist.item(0));
            value = subquery;
         }
      }
      else if(type.equals(FIELD)) {
         String nval = Tool.getValue(node);
         value = nval != null ? nval.trim() : nval;
      }
      else {
         String nval = Tool.getValue(node);

         if(nval != null) {
            if(nval.indexOf(",") > 0) {
               value = nval.split(",");
            }
            else {
               value = nval;
            }
         }
      }
   }

   /**
    * Generate the XML segment to represent this expression.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      writer.print("<" + XML_TAG + " ");
      writer.print("type=\"" + type + "\"");
      writer.println(">");

      if(type.equals(SUBQUERY)) {
         ((UniformSQL) value).writeXML(writer);
      }
      else {
         writer.print("<![CDATA[");

         if(value != null) {
            String exp = "";

            if(value instanceof Object[]) {
               Object[] tmp = (Object[]) value;

               for(int i = 0; i < tmp.length; i++) {
                  exp += (i > 0 ? "," : "") + tmp[i].toString();
               }
            }
            else if(type.equals(FIELD)) {
               exp = value.toString().trim();
            }
            else {
               exp = value.toString();
            }

            writer.print(exp);
         }

         writer.println("]]>");
      }

      writer.println("</" + XML_TAG + ">");
   }

   @Override
   public Object clone() {
      try {
         XExpression expr = (XExpression) super.clone();
         Object value = expr.getValue();

         // @by jasons, attempt to clone value
         if(value instanceof Cloneable && !(value instanceof Object[])) {
            try {
               Method m = value.getClass().getMethod("clone", new Class[0]);

               if(m != null) {
                  expr.value = m.invoke(value, new Object[0]);
               }
               else {
                  expr.value = value;
               }
            }
            catch(Exception exc) {
               LOG.debug("Failed to clone value: " + value, exc);
               expr.value = value;
            }
         }
         else {
            expr.value = value;
         }

         return expr;
      }
      catch(Exception e) {
         LOG.error("Failed to clone object", e);
         return null;
      }
   }

   /**
    * Get the set function names list .
    */
   public static String[] getAllFunctionNames() {
      String[] funcs = new String[functionNameList.size()];
      functionNameList.copyInto(funcs);
      return funcs;
   }

   /**
    * Compare the values in the expression.
    */
   public boolean equals(Object obj) {
      try {
         XExpression exp = (XExpression) obj;
         return type.equals(exp.type) && value.equals(exp.value);
      }
      catch(Exception ex) {
         return false;
      }
   }

   private int quote = QUOTE_NONE;
   private Object value = "";
   private String type = FIELD;

   private static Vector opList = new Vector();
   private static Vector functionNameList = new Vector();
   static {
      functionNameList.addElement("Sum");
      functionNameList.addElement("Avg");
      functionNameList.addElement("Count");
      functionNameList.addElement("Max");
      functionNameList.addElement("Min");
   }

   private static final Logger LOG =
      LoggerFactory.getLogger(XExpression.class);
}
