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
package inetsoft.uql;

import inetsoft.uql.asset.ExpressionValue;
import inetsoft.uql.schema.UserVariable;
import inetsoft.uql.schema.XSchema;
import inetsoft.util.*;
import org.pojava.datetime.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import org.w3c.dom.Element;

import java.io.*;
import java.sql.Time;
import java.text.DateFormat;
import java.text.ParseException;
import java.util.Date;
import java.util.Objects;

/**
 * XCondition defines the condition methods.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public abstract class AbstractCondition implements XCondition {
   /**
    * Create default value for user variable.
    * @param type of variable.
    * @return default value.
    */
   public static Object createDefaultValue(String type) {
      Object value = "";

      if(type == null) {
         return value;
      }

      if(type.equals(XSchema.STRING) || type.equals(XSchema.CHAR)) {
         value = "";
      }
      else if(type.equals(XSchema.BOOLEAN)) {
         value = true;
      }
      else if(type.equals(XSchema.BYTE)) {
         value = (byte) 0;
      }
      else if(type.equals(XSchema.DOUBLE)) {
         value = 0D;
      }
      else if(type.equals(XSchema.INTEGER)) {
         value = 0;
      }
      else if(type.equals(XSchema.FLOAT)) {
         value = 0F;
      }
      else if(type.equals(XSchema.LONG)) {
         value = 0L;
      }
      else if(type.equals(XSchema.SHORT)) {
         value = (short) 0;
      }
      else if(type.equals(XSchema.DATE) || type.equals(XSchema.TIME) ||
         type.equals(XSchema.TIME_INSTANT)) {
         value = new Date(System.currentTimeMillis());
      }

      return value;
   }

   /**
    * Get a sql string representation of the specified object's value. The format
    * of the resulting String is determined by the type of object specified.
    * @param value the object to get a a representation of.
    * @return the sql string representation of the specified object's value.
    */
   public static String getValueSQLString(Object value) {
      if(value == null) {
         return "";
      }
      else if(value instanceof java.sql.Date) {
         return CoreTool.dateFmt.get().format(value);
      }
      else if(value instanceof java.sql.Time) {
         return CoreTool.timeFmt.get().format(value);
      }
      else if(value instanceof java.sql.Timestamp) {
         return CoreTool.timeInstantFmt.get().format(value);
      }
      else if(value instanceof java.util.Date) {
         return CoreTool.timeInstantFmt.get().format(
            new java.sql.Timestamp(((java.util.Date) value).getTime()));
      }
      else if(value instanceof UserVariable) {
         return "$(" + ((UserVariable) value).getName() + ")";
      }
      else if (value instanceof ExpressionValue) {
         return ((ExpressionValue) value).getExpression();
      }
      else if(value instanceof Number) {
         return Tool.toString(value);
      }
      else if(value instanceof String) {
         return "'" + value + "'";
      }
      else if(value instanceof Object[]) {
         Object[] arr = (Object[]) value;

         if(arr.length == 0) {
            return "";
         }

         StringBuilder builder = new StringBuilder();

         for(int i = 0; i < arr.length; i++) {
            if(i != 0) {
               builder.append(",");
            }

            builder.append(getValueSQLString(arr[i]));
         }

         return builder.toString();
      }
      else {
         return value.toString();
      }
   }

   /**
    * Get a String representation of the specified object's value. The format
    * of the resulting String is determined by the type of object specified.
    * @param value the object to get a a representation of.
    * @return the String representation of the specified object's value.
    */
   public static String getValueString(Object value) {
      if(value instanceof String) {
         return (String) value;
      }
      else if(value == null) {
         return "";
      }
      else if(value instanceof java.sql.Date) {
         return CoreTool.dateFmt.get().format(value);
      }
      else if(value instanceof java.sql.Time) {
         return CoreTool.timeFmt.get().format(value);
      }
      else if(value instanceof java.sql.Timestamp) {
         return CoreTool.timeInstantFmt.get().format(value);
      }
      else if(value instanceof UserVariable) {
         return "$(" + ((UserVariable) value).getName() + ")";
      }
      else if(value instanceof Number) {
         return Tool.toString(value);
      }

      return value.toString();
   }

   /**
    * Get a String representation of the specified object's value. The format
    * of the resulting String is determined by the type of object specified.
    * @param value the object to get a a representation of.
    * @param type the data type.
    * @return the String representation of the specified object's value.
    */
   public static String getValueString(Object value, String type) {
      return getValueString(value, type, true);
   }

   /**
    * Get a String representation of the specified object's value. The format
    * of the resulting String is determined by the type of object specified.
    * @param value the object to get a a representation of.
    * @param type the data type.
    * @param def <tt>true</tt> to use default value, <tt>false</tt> otherwise.
    * @return the String representation of the specified object's value.
    */
   public static String getValueString(Object value, String type, boolean def) {
      return getValueString(value, type, def, false);
   }

   /**
    * Get a String representation of the specified object's value. The format
    * of the resulting String is determined by the type of object specified.
    * @param value the object to get a a representation of.
    * @param type the data type.
    * @param def <tt>true</tt> to use default value, <tt>false</tt> otherwise.
    * @return the String representation of the specified object's value.
    */
   public static String getValueString(Object value, String type, boolean def, boolean databricks) {
      if(value == null) {
         return def ? createDefaultValue(type).toString() : Tool.NULL;
      }

      DateFormat format = getDateFormat(type, databricks);

      if(format != null && (value instanceof Date)) {
         return format.format(value);
      }

      return getValueString(value);
   }

   public static Object getData(String type, String value) {
      if(XSchema.isDateType(type)) {
         if(StringUtils.isEmpty(value)) {
            return null;
         }

         DateFormat fmt = getDateFormat(type);

         try {
            Date date = fmt.parse(value);

            if(XSchema.TIME.equals(type)) {
               date = new Time(date.getTime());
            }

            return date;
         }
         catch(ParseException ignore) {
         }
      }

      return Tool.getData(type, value, true);
   }

   public static DateFormat getDateFormat(String type) {
      return getDateFormat(type, false);
   }

   /**
    * @param type the data type.
    * @param databricks true if databricks db else false.
    * @return
    */
   public static DateFormat getDateFormat(String type, boolean databricks) {
      if(type.equals(XSchema.DATE)) {
         return CoreTool.dateFmt.get();
      }
      else if(type.equals(XSchema.TIME_INSTANT)) {
         if(databricks) {
            return CoreTool.databricksTimeInstantFmt.get();
         }

         return CoreTool.timeInstantFmt.get();
      }
      else if(type.equals(XSchema.TIME)) {
         return CoreTool.timeFmt.get();
      }

      return null;
   }

   /**
    * Check a string value.
    * @param value the specified value.
    * @param type the specified data type.
    */
   public static void checkValueString(String value, String type)
      throws Exception
   {
      if(value == null || value.equals("") || value.equals("null") ||
         (value.startsWith("$(") && value.endsWith(")")))
      {
         return;
      }
      else if(type.equals(XSchema.FLOAT)) {
         Float.valueOf(value);
      }
      else if(type.equals(XSchema.DOUBLE)) {
         Double.valueOf(value);
      }
      else if(type.equals(XSchema.BYTE)) {
         Byte.valueOf(value);
      }
      else if(type.equals(XSchema.SHORT)) {
         Short.valueOf(value);
      }
      else if(type.equals(XSchema.INTEGER)) {
         Integer.valueOf(value);
      }
      else if(type.equals(XSchema.LONG)) {
         Long.valueOf(value);
      }
      else if(type.equals(XSchema.BOOLEAN)) {
         Boolean.valueOf(value);
      }
      else if(type.equals(XSchema.TIME_INSTANT)) {
         CoreTool.timeInstantFmt.get().parse(value);
      }
      else if(type.equals(XSchema.TIME)) {
         CoreTool.timeFmt.get().parse(value);
      }
      else if(type.equals(XSchema.DATE)) {
         CoreTool.dateFmt.get().parse(value);
      }
   }

   /**
    * Get an Object instance with the specified value and type.
    * @param type the data type of the desired Object.  Must be one of the data
    * type constants defined in {@link inetsoft.uql.schema.XSchema}.
    * @param value a String containing the desired value of the Object.
    * @return an instance of the specified type of Object, having the specified
    * value. If <i>value</i> is not a defined data type, <i>value<i> is
    * returned. If <i>var</i> is true, a user variable is returned.
    */
   public static Object getObject(String type, String value, boolean var) {
      if(var) {
         UserVariable variable = new UserVariable(value);

         variable.setTypeNode(XSchema.createPrimitiveType(type));
         return variable;
      }
      else {
         return getObject(type, value);
      }
   }

   /**
    * Get an Object instance with the specified value and type.
    * @param type the data type of the desired Object. Must be one of the data
    * type constants defined in {@link inetsoft.uql.schema.XSchema}.
    * @param value a String containing the desired value of the Object.
    * @return an instance of the specified type of Object, having the specified
    * value. If <i>value</i> is not a defined data type, <i>value<i> is
    * returned.
    */
   public static Object getObject(String type, String value) {
      if(value == null ||
         (!(XSchema.STRING.equals(type) || XSchema.CHAR.equals(type)) && "".equals(value)))
      {
         Object value0 = createDefaultValue(type);

         if(LOG.isDebugEnabled()) {
            LOG.debug("Condition value was not set. Using default value " + value +
                         " for type (" + type + ")");
         }

         return value0;
      }
      else if(type.equals(XSchema.FLOAT)) {
         try {
            return Float.valueOf(value);
         }
         catch(Exception ex) {
            LOG.info("Invalid condition float value: " + value, ex);
            return 0F;
         }
      }
      else if(type.equals(XSchema.DOUBLE)) {
         try {
            return Double.valueOf(value);
         }
         catch(Exception ex) {
            LOG.info("Invalid condition double value: " + value, ex);
            return 0D;
         }
      }
      else if(type.equals(XSchema.BYTE)) {
         try {
            return Byte.valueOf(value);
         }
         catch(Exception ex) {
            LOG.info("Invalid condition byte value: " + value, ex);
            return (byte) 0;
         }
      }
      else if(type.equals(XSchema.SHORT)) {
         try {
            return Short.valueOf(value);
         }
         catch(Exception ex) {
            try {
               return Double.valueOf(value).shortValue();
            }
            catch(Exception e) {
               LOG.info("Invalid condition short value: " + value, ex);
            }

            return (short) 0;
         }
      }
      else if(type.equals(XSchema.INTEGER)) {
         try {
            return Integer.valueOf(value);
         }
         catch(Exception ex) {
            try {
               return Double.valueOf(value).intValue();
            }
            catch(Exception e) {
               LOG.info("Invalid condition integer value: " + value, ex);
            }

            return 0;
         }
      }
      else if(type.equals(XSchema.LONG)) {
         try {
            return Long.valueOf(value);
         }
         catch(Exception e) {
            try {
               return Double.valueOf(value).longValue();
            }
            catch(Exception ex) {
               LOG.info("Invalid condition long value value: " + value, ex);
            }

            return 0L;
         }
      }
      else if(type.equals(XSchema.BOOLEAN)) {
         try {
            return Boolean.valueOf(value);
         }
         catch(Exception e) {
            LOG.info("Invalid condition boolean value: " + value, e);
            return Boolean.FALSE;
         }
      }
      else if(type.equals(XSchema.TIME_INSTANT)) {
         // try new format first, then old format
         if(value.startsWith("{ts")) {
            try {
               return new java.sql.Timestamp(CoreTool.timeInstantFmt.get().parse(value).getTime());
            }
            catch(Exception ex) {
            }
         }

         if(value.startsWith("{d")) {
            try {
               return new java.sql.Timestamp(
                  CoreTool.timeInstantFmt_old.get().parse(value).getTime());
            }
            catch(Exception ex) {
            }

            try {
               return new java.sql.Timestamp(CoreTool.dateFmt.get().parse(value).getTime());
            }
            catch(Exception ex) {
            }
         }

         if(CoreTool.isDate(value) || value.startsWith("{d")) {
            try {
               return DateTime.parse(value, CoreTool.getDateTimeConfig()).toTimestamp();
            }
            catch(Exception e) {
               // ignore
            }
         }

         if(LOG.isDebugEnabled()) {
            LOG.debug(Catalog.getCatalog().getString("common.condition.date.convertError", value));
         }

         return new java.sql.Timestamp(System.currentTimeMillis());
      }
      else if(type.equals(XSchema.TIME)) {
         // try new format first, then old format
         if(value.startsWith("{t")) {
            try {
               return new java.sql.Time(CoreTool.timeFmt.get().parse(value).getTime());
            }
            catch(Exception ex) {
               // ignore
            }
         }

         if(value.startsWith("{d")) {
            try {
               return new java.sql.Time(CoreTool.timeFmt_old.get().parse(value).getTime());
            }
            catch(Exception ex) {
               // ignore
            }
         }

         if(value.contains(":")) {
            try {
               DateFormat dfmt =
                  CoreTool.createDateFormat(CoreTool.DEFAULT_TIME_PATTERN);
               return new java.sql.Time(dfmt.parse(value).getTime());
            }
            catch(Exception e) {
               // ignore
            }
         }

         if(LOG.isDebugEnabled()) {
            LOG.debug(Catalog.getCatalog().getString("common.condition.date.convertError", value));
         }

         return new java.sql.Time(System.currentTimeMillis());
      }
      else if(type.equals(XSchema.DATE)) {
         try {
            if(value.startsWith("{d")) {
               DateFormat dfmt = CoreTool.dateFmt.get();

               try {
                  return new java.sql.Date(dfmt.parse(value).getTime());
               }
               catch(Exception e) {
                  // ignore
               }
            }

            if(CoreTool.isDate(value)) {
               return new java.sql.Date(
                  DateTime.parse(value, CoreTool.getDateTimeConfig()).toMillis());
            }
         }
         catch(Exception e) {
            // ignore
         }

         if(LOG.isDebugEnabled()) {
            LOG.debug(Catalog.getCatalog().getString("common.condition.date.convertError", value));
         }

         return new java.sql.Date(System.currentTimeMillis());
      }

      return value;
   }

   /**
    * Get a Date object with a value equal to that specified.
    * @param val a String representation of a date. The value should have the
    * format <i>yyyy-MM-dd</i>.
    * @return a Data object with the specified value. If unable to parse the
    * value String, the current date will be returned.
    */
   public static Date getDate(String val) {
      Date dt;

      try {
         dt = allDateFmt.get().parse(val);
      }
      catch(ParseException e) {
         dt = null;
      }

      return (dt == null) ? new Date() : dt;
   }

   /**
    * Get an sql Date object due to the type.
    * @param val the date to be converted.
    * @param type the type of the date.
    * @return a sql Date object with the specified value.
    */
   public static Object getDateObject(String type, Object val) {
      if(!(val instanceof Date) || type == null) {
         return null;
      }

      if(type.equals(XSchema.TIME_INSTANT)) {
         return new java.sql.Timestamp(((java.util.Date) val).getTime());
      }
      else if(type.equals(XSchema.TIME)) {
         return new java.sql.Time(((java.util.Date) val).getTime());
      }
      else if(type.equals(XSchema.DATE)) {
         return new java.sql.Date(((java.util.Date) val).getTime());
      }

      return null;
   }

   /**
    * Get a Boolean object with a value equal to that specified.
    * @param val a String representation of a boolean. The value should be
    * <code>true</code> or <code>false</code>.
    * @return a Boolean object with the specified value.
    */
   public static Boolean getBoolean(String val) {
      return "true".equalsIgnoreCase(val) ? Boolean.TRUE : Boolean.FALSE;
   }

   /**
    * Create one xcondition from an xml element.
    * @param elem the specified xml element.
    * @return the created xcondition.
    */
   public static XCondition createXCondition(Element elem) throws Exception {
      String cls = Tool.getAttribute(elem, "class");
      XCondition condition = (XCondition) Class.forName(cls).newInstance();
      condition.parseXML(elem);
      return condition;
   }

   /**
    * Constructor.
    */
   public AbstractCondition() {
      super();
   }

   /**
    * Get the condition value data type.
    * @return the data type of this condition. The type will be one of the
    * constants defined in {@link inetsoft.uql.schema.XSchema}.
    */
   @Override
   public String getType() {
      return type;
   }

   /**
    * Set the condition value data type.
    * @param type the data type of the condition. Must be one of the data type
    * constants defined in {@link inetsoft.uql.schema.XSchema}.
    */
   @Override
   public void setType(String type) {
      if(!isTypeChangeable()) {
         return;
      }

      this.type = type;
   }

   /**
    * Get the comparison operation of this condition.
    * @return one of the operation constant, one of the operation constants
    * defined in this class.
    * @see #EQUAL_TO
    * @see #ONE_OF
    * @see #LESS_THAN
    * @see #GREATER_THAN
    * @see #BETWEEN
    * @see #STARTING_WITH
    * @see #LIKE
    * @see #CONTAINS
    * @see #NULL
    * @see #TOP_N
    * @see #DATE_IN
    */
   @Override
   public int getOperation() {
      return op;
   }

   /**
    * Set the comparison operation of this condition.
    * @param op one of the operation constants defined in this class.
    */
   @Override
   public void setOperation(int op) {
      if(!isOperationChangeable()) {
         return;
      }

      this.op = op;
      equal = false;
   }

   /**
    * Determine whether equivalence will be tested in addition to the
    * defined comparison operation.
    * @return <code>true</code> if equivalence will be tested
    */
   @Override
   public final boolean isEqual() {
      return equal;
   }

   /**
    * Set the equal to option when the comparison operation is
    * <code>LESS_THAN</code> or <code>GREATER_THAN</code>, i.e.
    * <code><i>a</i> &gt;= <i>b</i></code>.
    * @param equal <code>true</code> if equivalence should be tested
    */
   @Override
   public void setEqual(boolean equal) {
      if(isEqualChangeable()) {
         this.equal = equal;
      }
   }

   /**
    * Set whether this condition result should be negated. A negated condition
    * will evaluate as <code>true</code> if the if its condition definition(s)
    * are <b>not</b> met.
    * @return <code>true</code> if this condition is negated.
    */
   @Override
   public boolean isNegated() {
      return negated;
   }

   /**
    * Determine whether this condition result should be negated. A negated
    * condition will evaluate as <code>true</code> if the if its condition
    * definition(s) are <b>not</b> met.
    * @param negated <code>true</code> if this condition is negated.
    */
   @Override
   public void setNegated(boolean negated) {
      if(isNegatedChangeable()) {
         this.negated = negated;
      }
   }

   /**
    * Writer the attributes.
    * @param writer the specified print writer.
    */
   @Override
   public void writeAttributes(PrintWriter writer) {
      writer.print(" type=\"" + getType() + "\"");
      writer.print(" negated=\"" + isNegated() + "\"");
      writer.print(" operation=\"" + getOperation() + "\"");
      writer.print(" equal=\"" + isEqual() + "\"");
   }

   /**
    * Parse the attributes.
    * @param elem the specified xml element.
    */
   @Override
   public void parseAttributes(Element elem) throws Exception {
      String str;

      if((str = Tool.getAttribute(elem, "type")) != null) {
         setType(str);
      }

      if((str = Tool.getAttribute(elem, "negated")) != null) {
         setNegated(str.equalsIgnoreCase("true"));
      }

      if((str = Tool.getAttribute(elem, "operation")) != null) {
         setOperation(Integer.parseInt(str));
      }

      if((str = Tool.getAttribute(elem, "equal")) != null) {
         setEqual(str.equalsIgnoreCase("true"));
      }
   }

   /**
    * Clone the object.
    * @return the cloned object.
    */
   @Override
   public Object clone() {
      try {
         return super.clone();
      }
      catch(Exception ex) {
         return null;
      }
   }

   /**
    * Read object.
    */
   private void readObject(java.io.ObjectInputStream s)
      throws ClassNotFoundException, java.io.IOException
   {
      s.defaultReadObject();
   }

   /**
    * Write object.
    */
   private void writeObject(ObjectOutputStream stream) throws IOException {
      stream.defaultWriteObject();
   }

   /**
    * Check if equals another object in content.
    */
   @Override
   public boolean equalsContent(Object obj) {
      return equals(obj);
   }

   /**
    * Print the key to identify this content object. If the keys of two content
    * objects are equal, the content objects are equal too.
    */
   @Override
   public boolean printKey(PrintWriter writer) throws Exception {
      String name = getClass().getName();
      int idx = name.lastIndexOf(".");
      name = idx >= 0 ? name.substring(idx + 1) : name;
      writer.print(name);
      writer.print("[");
      writer.print(type);
      writer.print(",");
      writer.print(op);
      writer.print(",");
      writer.print(negated);
      writer.print(",");
      writer.print(equal);
      writer.print("]");
      return true;
   }

   /**
    * Check if equqls another object.
    */
   public boolean equals(Object obj) {
      if(!(obj instanceof AbstractCondition)) {
         return false;
      }

      AbstractCondition cond2 = (AbstractCondition) obj;
      boolean eq = Objects.equals(type, cond2.type);
      eq = eq && op == cond2.op;
      eq = eq && negated == cond2.negated;
      eq = eq && equal == cond2.equal;

      return eq;
   }

   @Override
   public int hashCode() {
      return Objects.hash(type, op, negated, equal);
   }

   /**
    * Write this data selection to XML.
    * @param writer the stream to output the XML text to
    */
   @Override
   public void writeXML(PrintWriter writer) {
      writer.print("<xCondition class=\"" + getClass().getName() + "\"");
      writeAttributes(writer);
      writer.println(">");

      writeContents(writer);
      writer.println("</xCondition>");
   }

   /**
    * Read in the XML representation of this object.
    * @param ctag the XML element representing this object.
    */
   @Override
   public void parseXML(Element ctag) throws Exception {
      parseAttributes(ctag);
      parseContents(ctag);
   }

   protected String type = XSchema.STRING;
   protected int op = EQUAL_TO;
   protected boolean negated = false;
   protected boolean equal = false;

   static ThreadLocal<DateFormat> allDateFmt = ThreadLocal.withInitial(Tool::createDateFormat);
   private static final Logger LOG = LoggerFactory.getLogger(AbstractCondition.class);
}
