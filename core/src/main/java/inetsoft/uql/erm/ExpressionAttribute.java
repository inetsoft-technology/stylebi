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
package inetsoft.uql.erm;

import inetsoft.uql.XMetaInfo;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.schema.XSchema;
import inetsoft.util.Tool;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.PrintWriter;
import java.util.*;

/**
 * An ExpressionAttribute encapsulates an attribute of an entity that is defined
 * with an expression.
 *
 * @author  InetSoft Technology Corp.
 * @since   4.4
 */
public class ExpressionAttribute extends XAttribute {
   /**
    * Creates a new instance of ExpressionAttribute. Default constructor that
    * should only be used when loading the attribute from an XML file.
    */
   ExpressionAttribute() {
      setDataType(XSchema.DOUBLE);
   }

   /**
    * Create an expression with the specified name.
    */
   public ExpressionAttribute(String name) {
      setName(name);
      setDataType(XSchema.DOUBLE);
   }

   /**
    * Create an expression with the specified name and sql expression.
    */
   public ExpressionAttribute(String name, String expr) {
      setName(name);
      setExpression(expr);
      setDataType(XSchema.DOUBLE);
   }

   /**
    * Check if the attribute is an expression.
    *
    * @return <tt>true</tt> if is an expression, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isExpression() {
      return true;
   }

   /**
    * Set the SQL expression of this reference.
    *
    * @param expression a SQL expression
    */
   public void setExpression(String expression) {
      expr = expression == null ? "" : expression;
   }

   /**
    * Get the name of the database column this attribute is mapped to.
    *
    * @return the name of a database column.
    */
   public String getExpression() {
      return expr;
   }

   /**
    * Check if the expression is an aggregate expression.
    *
    */
   public boolean isAggregateExpression() {
      return aggr;
   }

   /**
    * Set true if it is an aggregate expression.
    */
   public void setAggregateExpression(boolean b) {
      aggr = b;
   }

   /**
    * Writes the XML element representation of this object.
    *
    * @param writer the output stream to which to write the XML data.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      String formula = getDefaultFormula();

      writer.println("<attribute class=\"inetsoft.uql.erm.ExpressionAttribute" +
         "\" name=\"" + Tool.escape(getName()) +
         "\" type=\"" + getDataType() +
         "\" browse=\"" + isBrowseable() +
         "\" refType=\"" + getRefType() +
         "\" parseable=\"" + isParseable() +
         (formula == null ? "" :  "\" formula=\"" + formula) +
         "\" aggregate=\"" + isAggregateExpression() + "\">");
      writer.println("<description>" +
         (getDescription() == null ? "" : getDescription()) + "</description>");
      writer.println("<expr><![CDATA[" +  getExpression() + "]]></expr>");

      if(getBrowseDataQuery() != null) {
         writer.print("<browseDataQuery>");
         writer.print("<![CDATA[");
         writer.print(getBrowseDataQuery());
         writer.print("]]>");
         writer.println("</browseDataQuery>");
      }

      XMetaInfo meta = getXMetaInfo();

      if(!meta.isEmpty()) {
         meta.writeXML(writer);
      }

      Object[] entries = getOuterDependencies();

      if(entries.length > 0) {
         writer.println("<dependencies>");

         for(Object entry : entries) {
            if(entry instanceof AssetEntry) {
               ((AssetEntry) entry).writeXML(writer);
            }
         }

         writer.println("</dependencies>");
      }

      writer.println("</attribute>");
   }

   /**
    * Reads in a attribute definition from its XML representation.
    *
    * @param tag the XML Element for this object.
    *
    * @throws Exception if an error occurs while parsing the XML element.
    */
   @Override
   public void parseXML(Element tag) throws Exception {
      String attr = null;

      if((attr = Tool.getAttribute(tag, "name")) != null) {
         setName(attr);
      }

      if((attr = Tool.getAttribute(tag, "type")) != null) {
         setDataType(attr);
      }

      if((attr = Tool.getAttribute(tag, "browse")) != null) {
         setBrowseable(attr.equals("true"));
      }

      if((attr = Tool.getAttribute(tag, "refType")) != null) {
         setRefType(Integer.parseInt(attr));
      }

      if((attr = Tool.getAttribute(tag, "aggregate")) != null) {
         setAggregateExpression("true".equals(attr));
      }

      setDefaultFormula(Tool.getAttribute(tag, "formula"));

      setParseable(!"false".equals(Tool.getAttribute(tag, "parseable")));

      NodeList nl = Tool.getChildNodesByTagName(tag, "description");

      if(nl != null && nl.getLength() > 0) {
         setDescription(Tool.getValue(nl.item(0)));
      }

      String expr = null;

      nl = Tool.getChildNodesByTagName(tag, "expr");

      if(nl != null && nl.getLength() > 0) {
         expr = Tool.getValue(nl.item(0));
         expr = expr == null || expr.equals("") ? null : expr;
         setExpression(expr);
      }

      nl = Tool.getChildNodesByTagName(tag, "browseDataQuery");

      if(nl != null && nl.getLength() > 0) {
         setBrowseDataQuery(Tool.getValue(nl.item(0)));
      }

      Element elem = Tool.getChildNodeByTagName(tag, "XMetaInfo");

      if(elem != null) {
         XMetaInfo meta = new XMetaInfo();
         meta.parseXML(elem);
         setXMetaInfo(meta);
      }

      Element dsnode = Tool.getChildNodeByTagName(tag, "dependencies");

      if(dsnode != null) {
         NodeList list = Tool.getChildNodesByTagName(dsnode, "assetEntry");

         for(int i = 0; list != null && i < list.getLength(); i++) {
            Element anode = (Element) list.item(i);
            AssetEntry entry = AssetEntry.createAssetEntry(anode);
            addOuterDependency(entry);
         }
      }
   }

   /**
    * Gets a textual representation of this attribute. For user interface
    * purposes use the {@link #getName()} method.
    *
    * @return a string representation of this object. This value will have the
    *         format <CODE>ExpressionAttribute: <I>attribute name</I></CODE>.
    */
   public String toString() {
      return "ExpressionAttribute: " + getName();
   }

   /*
    * Determines if this object is equivilent to another object.
    *
    * @param obj the reference object with which to compare.
    *
    * @return <code>true</code> if the objects are are equivilent,
    *         <code>false</code> otherwise.
    */
   public boolean equals(Object obj) {
      if(!(obj instanceof ExpressionAttribute)) {
         return false;
      }

      ExpressionAttribute xattr = (ExpressionAttribute) obj;

      if(this.getName().equals(xattr.getName())) {
         return this.getExpression() == null ||
            this.getExpression().equals(xattr.getExpression());
      }

      return false;
   }

   /**
    * Gets the name of the database tables this attribute is mapped to.
    *
    * @return the names of a database tables.
    */
   @Override
   public String[] getTables() {
      String expr = getExpression();
      int idx = -1;
      Vector tables = new Vector();

      // look for field['table.column'] and return array of tables
      while((idx = expr.indexOf("field['", idx)) != -1) {
         idx = idx + 7;

         if(idx < getExpression().length() - 2) {
            int end = getExpression().indexOf("']", idx);

            if(end > idx) {
               String fname = getExpression().substring(idx, end).trim();

               if(fname.length() >= 3) {
                  fname = fname.trim();
                  int dot = fname.lastIndexOf('.');

                  String result = dot < 0 ? null : fname.substring(0, dot);

                  if(!tables.contains(result)) {
                     tables.add(result);
                  }
               }

               idx = end + 2;
            }
         }
      }

      String[] ret = new String[tables.size()];

      for(int i = 0; i < ret.length; i++) {
         ret[i] = (String) tables.elementAt(i);
      }

      return ret;
   }

   /**
    * Ge the full name of all datatable columns referenced by this attribute.
    */
   @Override
   public String[] getColumns() {
      String expr = getExpression();
      int idx = -1;
      Set cols = new HashSet();

      while((idx = expr.indexOf("field['", idx)) != -1) {
         idx = idx + 7;

         if(idx < getExpression().length() - 2) {
            int end = getExpression().indexOf("']", idx);

            if(end > idx) {
               String fname = getExpression().substring(idx, end).trim();
               int idx2 = fname.indexOf(".");

               if(idx2 < 0 || fname.length() < 3) {
                  throw new RuntimeException("Invalid field found: " + fname);
               }

               cols.add(fname);
               idx = end + 2;
            }
         }
      }

      String[] arr = new String[cols.size()];
      cols.toArray(arr);
      return arr;
   }

   /**
    * Check if the attribute is parseable.
    */
   public boolean isParseable() {
      return parseable;
   }

   /**
    * Set true if it is parseable.
    */
   public void setParseable(boolean parseable) {
      this.parseable = parseable;
   }

   private String expr;
   private boolean aggr = false;
   private boolean parseable = true;
}

