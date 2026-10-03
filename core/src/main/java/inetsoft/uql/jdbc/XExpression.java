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
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
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
      return quoteQualifier(getQuotedValue0());
   }

   private String getQuotedValue0() {
      if(SUBQUERY.equals(type)) {
         return toParsedSubqueryString();
      }

      if(quote == QUOTE_NONE) {
         String value = toString();

         // @by larryl, if this string is a field, need to handle special
         // characters in the table/col name. Don't apply keyword quoting here
         // since keyword quoting is database-specific and should be handled
         // by the SQL generation layer (SQLHelper). Using keyword quoting with
         // the base SQLHelper (null provider) can incorrectly quote identifiers
         // like "default" which are not keywords in some databases (e.g. Databricks).
         if(FIELD.equals(type)) {
            value = XUtil.quoteName(value, false, null);
         }

         return value;
      }
      else if(quote == QUOTE_DOUBLE || quote == QUOTE_SINGLE) {
         String q = quote == QUOTE_DOUBLE ? "\"" : "`";
         String value = toString();

         // a qualified quoted column (t."MixedCase"), only the column segment is quoted
         if(quotedColumn != null && value.endsWith("." + quotedColumn)) {
            return value.substring(0, value.length() - quotedColumn.length()) + q +
               quotedColumn + q;
         }

         return q + value + q;
      }
      else {
         throw new RuntimeException("Unsupported quote type found: " + quote);
      }
   }

   /**
    * Set the column segment, as written, of a qualified quoted identifier (t."MixedCase"),
    * which is stored without its quotes (t.MixedCase).
    * @param column the segment, or <tt>null</tt> for a bare quoted identifier.
    */
   public void setQuotedColumn(String column) {
      this.quotedColumn = column;
   }

   /**
    * Get the column segment, as written, of a qualified quoted identifier (t."MixedCase").
    * @return the segment, or <tt>null</tt> for a bare quoted identifier.
    */
   public String getQuotedColumn() {
      return quotedColumn;
   }

   /**
    * Check if this is a quoted identifier (e.g. "x y" or t."MixedCase"), which is stored
    * without its quotes.
    */
   public boolean isQuotedField() {
      return FIELD.equals(type) && quote != QUOTE_NONE;
   }

   /**
    * Get the text of this expression to build the text of an enclosing expression. The
    * quotes of a quoted identifier are restored.
    */
   public String toQuotedString() {
      if(SUBQUERY.equals(type)) {
         return toParsedSubqueryString();
      }

      return isQuotedField() ? getQuotedValue() : quoteQualifier(toString());
   }

   /**
    * Get the text of a subquery that becomes part of the text of an enclosing expression or
    * a select column while the sql is parsed, e.g. a scalar subquery in the select list.
    * The quotes of its qualifiers are kept as written, since the outer tables are not known
    * yet and the text is not generated again (#77569).
    */
   private String toParsedSubqueryString() {
      if(!(value instanceof UniformSQL)) {
         return toString();
      }

      UniformSQL sql = (UniformSQL) value;
      sql.setQuoteAsWritten(true);

      try {
         sql.clearCachedString();
         return toString();
      }
      finally {
         sql.setQuoteAsWritten(false);
         sql.clearCachedString();
      }
   }

   /**
    * Set the qualifier segments of a field that were written as quoted identifiers ("a".id),
    * which are stored without their quotes. They are restored when the field is part of the
    * text of an enclosing expression, which is generated as written (#77569).
    * @param segments the 0-based indexes of the quoted segments of the field.
    * @param kinds the quote of each segment in segments: '"', '`' or '['.
    */
   public void setQuotedQualifier(int[] segments, String kinds) {
      boolean valid = segments != null && kinds != null && segments.length == kinds.length();
      this.quotedQualifier = valid ? segments.clone() : null;
      this.quotedQualifierKinds = valid ? kinds : null;
   }

   /**
    * Restore the quotes of the qualifier segments of a field written quoted, recorded while
    * the field is parsed.
    * @param str the text of the field.
    */
   public String quoteQualifier(String str) {
      if(quotedQualifier == null || !FIELD.equals(type) || str == null) {
         return str;
      }

      List<String> segs = splitSegments(str);
      StringBuilder sb = new StringBuilder();

      for(int i = 0; i < segs.size(); i++) {
         String seg = segs.get(i);
         char kind = 0;

         // the last segment is the column, see quotedColumn
         for(int j = 0; j < quotedQualifier.length && i < segs.size() - 1; j++) {
            if(quotedQualifier[j] == i) {
               kind = quotedQualifierKinds.charAt(j);
            }
         }

         if(i > 0) {
            sb.append('.');
         }

         if(kind != 0 && !seg.isEmpty() && "\"`[".indexOf(seg.charAt(0)) < 0) {
            sb.append(kind).append(seg).append(kind == '[' ? ']' : kind);
         }
         else {
            sb.append(seg);
         }
      }

      return sb.toString();
   }

   /**
    * Split a name at the dots that are not inside a quoted segment.
    */
   private static List<String> splitSegments(String name) {
      List<String> segs = new ArrayList<>();
      int start = 0;
      char close = 0;

      for(int i = 0; i < name.length(); i++) {
         char c = name.charAt(i);

         if(close != 0) {
            if(c == close) {
               close = 0;
            }
         }
         else if(c == '"' || c == '`') {
            close = c;
         }
         else if(c == '[') {
            close = ']';
         }
         else if(c == '.') {
            segs.add(name.substring(start, i));
            start = i + 1;
         }
      }

      segs.add(name.substring(start));
      return segs;
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

   public int getSqlType() {
      if(!isSqlTypeSet()) {
         return Types.VARCHAR;
      }

      return sqlType;
   }

   public void setSqlType(int sqlType) {
      this.sqlType = sqlType;
   }

   /**
    * Whether the sql type is set.
    *
    */
   public boolean isSqlTypeSet() {
      return sqlType != -1;
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
         String quoteAttr = Tool.getAttribute(node, "quote");
         String column = Tool.getAttribute(node, "quotedColumn");
         quotedColumn = null;

         // a qualified quoted identifier, see writeXML
         if(quoteAttr == null && column != null && !column.isEmpty()) {
            quoteAttr = Tool.getAttribute(node, "columnQuote");
            quotedColumn = column;
         }

         // a missing or malformed value is unquoted
         if(String.valueOf(QUOTE_DOUBLE).equals(quoteAttr)) {
            quote = QUOTE_DOUBLE;
         }
         else if(String.valueOf(QUOTE_SINGLE).equals(quoteAttr)) {
            quote = QUOTE_SINGLE;
         }
         else {
            quotedColumn = null;
         }
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

      if(isQuotedField()) {
         // a qualified quoted identifier (t."MixedCase") is not written with the quote
         // attribute, which older versions read as quoting the whole name ("t.MixedCase").
         // They ignore columnQuote and generate the name unquoted, as before
         if(quotedColumn != null) {
            writer.print(" columnQuote=\"" + quote + "\" quotedColumn=\"" +
               Tool.escape(quotedColumn) + "\"");
         }
         else {
            writer.print(" quote=\"" + quote + "\"");
         }
      }

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
   private String quotedColumn; // column segment of a qualified quoted identifier
   // qualifier segments written quoted, and their quotes, only used while parsing
   private transient int[] quotedQualifier;
   private transient String quotedQualifierKinds;
   private Object value = "";
   private String type = FIELD;
   private int sqlType = -1;

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
