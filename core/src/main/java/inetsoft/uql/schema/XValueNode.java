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
package inetsoft.uql.schema;

import inetsoft.uql.XNode;
import inetsoft.uql.asset.ExpressionValue;
import inetsoft.util.Tool;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.text.Format;
import java.text.ParseException;
import java.util.Date;

/**
 * This is the base class for all primitive value node.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class XValueNode extends XNode {
   /**
    * Create a value node.
    */
   public XValueNode() {
   }

   /**
    * Create a value node.
    */
   public XValueNode(String name) {
      super(name);
   }

   /**
    * Set the variable name for this value node. If the variable name
    * is not null, the value of the value node is gotten from the named
    * variable at runtime.
    * @param var variable name.
    */
   public void setVariable(String var) {
      this.var = var;
   }

   /**
    * Get the variable name of this node.
    */
   public String getVariable() {
      return var;
   }

   /**
    * Convert the value to a string.
    */
   public String format() {
      return isExpression() ?
         ((ExpressionValue) getValue()).getExpression() : getValue() + "";
   }

   /**
    * Get the type of this node. The types are defined in XSchema class.
    */
   public String getType() {
      return XSchema.USER_DEFINED;
   }

   /**
    * Set the format string for the type. The meaning of the format
    * depends on the data type. For example, for date related formats,
    * the format string is used to construct a SimpleDateFormat
    * object.
    */
   public void setFormat(Format fmt) {
   }

   /**
    * Parse the string to the value or expression.
    */
   public void parse(String str) throws ParseException {
      if(isExpression()) {
         ExpressionValue expressionValue = new ExpressionValue();
         expressionValue.setType(ExpressionValue.JAVASCRIPT);
         expressionValue.setExpression(str);
         setValue(expressionValue);
      }
      else {
         parse0(str);
      }
   }

   /**
    * Parse the string to the value.
    */
   public void parse0(String str) throws ParseException {
      setValue(str);
   }

   public boolean isExpression() {
      return getValue() instanceof ExpressionValue;
   }

   /**
    * Write the node XML representation.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      String tag = "valuenode";
      writer.print("<" + tag + " _node_name=\"" + Tool.escape(getName()) +
         "\" type=\"" + getType() + "\" null=\"" + (getValue() == null) + "\"");

      if(var != null) {
         writer.print(" variable=\"" + Tool.escape(var) + "\"");
      }

      writer.print(" isExpression=\"" + isExpression() + "\"");
      writer.print(">");

      if(getValue() != null) {
         writer.print("<![CDATA[" + format() + "]]>");
      }

      writer.println("</" + tag + ">");
   }

   public String toString() {
      return (var != null) ?
         (getName() + ": $(" + var + ")") :
         super.toString();
   }

   /**
    * Create a value node from a value.
    */
   public static XValueNode createValueNode(Object value, String name) {
      return createValueNode(value, name, null);
   }

   /**
    * Create a value node from a value.
    */
   public static XValueNode createValueNode(Object value, String name, String type) {
      if(value == null) {
         return null;
      }

      if(type != null) {
         return createValueNodeByType(name, type, value);
      }

      XValueNode node = null;

      if(value instanceof String) {
         node = new StringValue(name);
      }
      else if(value instanceof Boolean) {
         node = new BooleanValue(name);
      }
      else if(value instanceof Double) {
         node = new DoubleValue(name);
      }
      else if(value instanceof Float) {
         node = new FloatValue(name);
      }
      else if(value instanceof Number) {
         node = new IntegerValue(name);
      }
      else if(value instanceof java.sql.Date) {
         node = new DateValue(name);
      }
      else if(value instanceof java.sql.Timestamp) {
         node = new TimeInstantValue(name);
      }
      else if(value instanceof java.sql.Time) {
         node = new TimeValue(name);
      }
      else if(value instanceof Date) {
         node = new TimeInstantValue(name);
      }
      else if(value instanceof Character) {
         node = new CharacterValue(name);
      }
      else if(value instanceof Object[]) {
         Object[] arr = (Object[]) value;

         if(arr.length > 0) {
            node = createValueNode(arr[0], name);
         }
      }

      if(node != null) {
         node.setValue(value);
      }

      return node;
   }

   /**
    * Create a value node from a type.
    */
   public static XValueNode createValueNode(String name, String type) {
      return createValueNodeByType(name, type, null);
   }

   /**
    * Create a value node from a type.
    */
   public static XValueNode createValueNodeByType(String name, String type, Object value) {
      XValueNode node;

      if(type == null || type.equals(XSchema.STRING)) {
         node = new StringValue(name);
      }
      else if(type.equals(XSchema.BOOLEAN)) {
         node = new BooleanValue(name);
      }
      else if(type.equals(XSchema.FLOAT)) {
         node = new FloatValue(name);
      }
      else if(type.equals(XSchema.DOUBLE)) {
         node = new DoubleValue(name);
      }
      else if(type.equals(XSchema.CHAR)) {
         node = new CharacterValue(name);
      }
      else if(type.equals(XSchema.BYTE)) {
         node = new ByteValue(name);
      }
      else if(type.equals(XSchema.SHORT)) {
         node = new ShortValue(name);
      }
      else if(type.equals(XSchema.INTEGER)) {
         node = new IntegerValue(name);
      }
      else if(type.equals(XSchema.LONG)) {
         node = new LongValue(name);
      }
      else if(type.equals(XSchema.TIME_INSTANT)) {
         node = new TimeInstantValue(name);
      }
      else if(type.equals(XSchema.DATE)) {
         node = new DateValue(name);
      }
      else if(type.equals(XSchema.TIME)) {
         node = new TimeValue(name);
      }
      else {
         node = new StringValue(name);
      }

      node.setValue(value);

      return node;
   }

   /**
    * Create a value node from XML element.
    */
   public static XValueNode createValueNode(Element root)
      throws ParseException {
      String type = root.getAttribute("type");
      String name = root.getAttribute("_node_name");
      boolean isExpression = "true".equals(root.getAttribute("isExpression"));

      if(name == null || "".equals(name)) {
         name = root.getAttribute("name");
      }

      String isnull = root.getAttribute("null");
      XValueNode node = createValueNode(name, type);

      if(isExpression) {
         ExpressionValue expressionValue = new ExpressionValue();
         expressionValue.setType(ExpressionValue.JAVASCRIPT);
         node.setValue(expressionValue);
      }

      if(isnull == null || !isnull.equalsIgnoreCase("true")) {
         String value = Tool.getValue(root);

         node.parse((value == null) ? "" : value);
      }

      return node;
   }

   /**
    * from java Object to SQL type
    */
   public Object toSQLValue() {
      return getValue();
   }

   /**
    * Check if equals another object.
    * @return true if equals, false otherwise.
    */
   public boolean equals(Object obj) {
      if(obj == null || obj.getClass() != getClass()) {
         return false;
      }

      XValueNode vnode2 = (XValueNode) obj;

      if(!Tool.equals(this.getName(), vnode2.getName())) {
         return false;
      }

      if(!Tool.equals(this.getValue(), vnode2.getValue())) {
         return false;
      }

      if(!Tool.equals(this.var, vnode2.var)) {
         return false;
      }

      return true;
   }

   /**
    * Get the hash code value.
    * @return the hash code value.
    */
   public int hashCode() {
      return getName().hashCode();
   }

   private String var = null;
}
