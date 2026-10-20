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
package inetsoft.uql.asset;

import inetsoft.uql.*;
import inetsoft.uql.erm.*;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.XNamedGroupInfo;
import inetsoft.util.CoreTool;
import inetsoft.util.Tool;
import org.apache.commons.text.StringEscapeUtils;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.util.*;

/**
 * NamedRangeRef represents a group range data ref.
 *
 * @version 10.2
 * @author InetSoft Technology Corp
 */
public class NamedRangeRef extends ExpressionRef implements AssetObject,
   XConstants, SQLExpressionRef, RangeRef, DataRefWrapper
{
   /**
    * Constructor.
    */
   public NamedRangeRef() {
      super();
      dbtype = "";
   }

   /**
    * Constructor.
    */
   public NamedRangeRef(String attr) {
      this();
      this.attr = attr;
   }

   /**
    * Constructor.
    */
   public NamedRangeRef(String attr, DataRef ref) {
      this(attr);
      this.ref = ref;
   }

   /**
    * Get the field's name without the "Group" tag.
    */
   public static String getBaseName(String name) {
      return getBaseName(name, null);
   }

    /**
     * Get the field's name without the "Group" tag.
     */
    public static String getBaseName(String name, String entity) {
       if(name == null) {
          return null;
       }

       String originalName = name;
       boolean nameContainsEntity = entity != null && name.startsWith(entity + ".");

       if(nameContainsEntity) {
          name = name.substring(entity.length() + 1);
       }

       if(!name.startsWith("DataGroup") && !name.startsWith("ColorGroup") &&
          !name.startsWith("ShapeGroup") && !name.startsWith("SizeGroup") &&
          !name.startsWith("TextGroup"))
       {
          return originalName;
       }

       int idx = name.indexOf('(');
       int last = name.lastIndexOf(')');

       if(idx > 0 && last > idx) {
          if(nameContainsEntity) {
             return entity + "." + name.substring(idx + 1, last);
          }

          return name.substring(idx + 1, last);
       }

       return originalName;
    }

    /**
    * Get the database type.
    * @return the database type.
    */
   @Override
   public String getDBType() {
      return dbtype;
   }

   /**
    * Set the database type.
    * @param dbtype the specified database type.
    */
   @Override
   public void setDBType(String dbtype) {
      this.dbtype = dbtype == null ? "" : dbtype;
   }

   /**
    * Get the database version.
    */
   @Override
   public String getDBVersion() {
      return dbversion;
   }

   /**
    * Set the database version.
    */
   @Override
   public void setDBVersion(String version) {
      this.dbversion = version;
   }

   /**
    * Check if this date range ref is mergeable.
    * @return <tt>true</tt> if mergeable, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isMergeable() {
      if("derby".equals(dbtype)) {
         // @by billh, when both group and aggregate are defined, it cannot
         // be supported by derby. Let's wait for enhancement from derby
         /*
         try {
            float ver = Float.parseFloat(dbversion);
            return ver >= 10.3;
         }
         catch(Exception e) {
            // ignore it
         }
         */

         return false;
      }

      return !dbtype.equals("access");
   }

   /**
    * Check if expression is editable.
    * @return <tt>true</tt> if editable, <tt>false</tt> otherwise
    */
   @Override
   public boolean isExpressionEditable() {
      return false;
   }

   /**
    * Get the contained data ref.
    * @return the contained data ref.
    */
   @Override
   public DataRef getDataRef() {
      return ref;
   }

   /**
    * Set the data ref.
    * @param ref the specified data ref.
    */
   @Override
   public void setDataRef(DataRef ref) {
      this.ref = ref;
   }

   /**
    * Get the name of an attribute with a non-date range option.
    * @param attr column name
    * @param option group type
    * @return alias name
    */
   public static String getName(String attr, int option) {
      StringBuilder sb = new StringBuilder();

      switch(option) {
      case DATA_GROUP:
         sb.append("DataGroup");
         break;
      case COLOR_GROUP:
         sb.append("ColorGroup");
         break;
      case SHAPE_GROUP:
         sb.append("ShapeGroup");
         break;
      case SIZE_GROUP:
         sb.append("SizeGroup");
         break;
      case TEXTURE_GROUP:
         sb.append("TextGroup");
         break;
      default:
         throw new RuntimeException("Unsupported option found: " + option);
      }

      sb.append('(');
      sb.append(attr);
      sb.append(')');

      return sb.toString();
   }

   /**
    * Get the name of this reference.
    * @return the reference name.
    */
   @Override
   public String getName() {
      return attr;
   }

   /**
    * Set the name of the field.
    * @param name the name of the field
    */
   @Override
   public void setName(String name) {
      this.attr = name;
      super.setName(name);
   }

   /**
    * Get the referenced attribute.
    * @return the name of the attribute.
    */
   @Override
   public String getAttribute() {
      return attr;
   }

   /**
    * set the data type.
    */
   @Override
   public void setDataType(String dataType) {
      this.dataType = dataType;
   }

   /**
    * Get the data type.
    */
   @Override
   public String getDataType() {
      return dataType;
   }

   /**
    * Set the data type of the base column.
    */
   public void setBaseDataType(String dtype) {
      this.baseDataType = dtype;
   }

   /**
    * Get the data type of the base column.
    */
   public String getBaseDataType() {
      return baseDataType;
   }

   /**
    * Get the SQL expression of this reference.
    * @return a SQL expression
    */
   @Override
   public String getExpression() {
      String[] names = groupNameInfo != null ? groupNameInfo.getGroups() : null;

      if(names == null || names.length == 0) {
         return "0";
      }

      StringBuilder sb = new StringBuilder();
      String field = "field['" + ref.getName() + "']";
      sb.append("(CASE \n");
      boolean isdate = XSchema.isDateType(getBaseDataType());

      for(int i = 0; i < names.length; i++) {
         ConditionList clist = groupNameInfo.getGroupCondition(names[i]);
         List list = new ArrayList();
         boolean between = true;

         if(clist == null) {
            continue;
         }

         boolean includeLastDay = false;

         if(!isLegacyConditionList(clist)) {
            sb.append(" WHEN ");
            sb.append(getSQLPredicate(clist, field, isdate));
            sb.append(" THEN '").append(escapeSQLString(names[i])).append("'");
            continue;
         }

         for(int j = 0; j < clist.getConditionSize(); j++) {
            ConditionItem item = clist.getConditionItem(j);

            if(item == null) {
               continue;
            }

            Condition condition = item.getCondition();
            int op = condition.getOperation();

            if(j == 0) {
               if(op != XCondition.GREATER_THAN || !condition.isEqual()) {
                  between = false;
               }
            }
            else if(j == 2) {
               if(op != XCondition.LESS_THAN) {
                  between = false;
               }

               includeLastDay = op == XCondition.LESS_THAN && condition.isEqual();
            }

            if(op == XCondition.NULL) {
               sb.append(" WHEN \n");
               sb.append(field);
               sb.append(" IS NULL ");
               sb.append("THEN ");
               sb.append("'" + escapeSQLString(names[i]) + "'");
            }
            else {
               for(int k = 0; k < condition.getValueCount(); k++) {
                  Object value = condition.getValue(k);

                  if(isdate) {
                     value = condition.getValueSQLString(value);
                  }

                  if(("db2".equals(getDBType()) ||
                      "derby".equals(getDBType()) ||
                      "sybase".equals(getDBType())) &&
                     XSchema.isNumericType(getBaseDataType()) || isdate)
                  {
                     list.add(value);
                  }
                  else {
                     list.add("'" + escapeSQLString("" + value) + "'");
                  }
               }
            }
         }

         sb.append(" WHEN ");
         sb.append(field);

         // for date >= start && date < end condition generated by Calendar.
         // if we need to support more generic condition, we should change
         // the code to use SQLHelper to produce a condition expression instead
         // of hardcoding the logic for a couple of known condition types
         if(between && list.size() == 2) {
            sb.append(" >= " + list.get(0) + " AND " + field +
               (includeLastDay ? " <= " : " < ") + list.get(1));
         }
         else {
            sb.append(" IN (");

            for(int j = 0; j < list.size(); j++) {
               if(j > 0) {
                  sb.append(", ");
               }

               sb.append(list.get(j));
            }

            sb.append(")");
         }

         sb.append("THEN ");
         sb.append("'" + escapeSQLString(names[i]) + "'");
      }

      if(isdate) {
         sb.append("\n ELSE " + "''");
      }
      else {
         sb.append("\n ELSE " + getConvertField(field));
      }

      sb.append(" END)");

      return sb.toString();
   }

   /**
    * Whether the condition is one whose rendering predates operator support
    * (equal, less, greater, null; not negated). Those keep their original output.
    */
   private static boolean isLegacyCondition(Condition condition) {
      int op = condition.getOperation();

      return !condition.isNegated() &&
         (op == XCondition.EQUAL_TO || op == XCondition.LESS_THAN ||
          op == XCondition.GREATER_THAN || op == XCondition.NULL);
   }

   private static boolean isLegacyConditionList(ConditionList clist) {
      for(int j = 0; j < clist.getConditionSize(); j++) {
         ConditionItem item = clist.getConditionItem(j);

         if(item != null && !isLegacyCondition(item.getCondition())) {
            return false;
         }
      }

      return true;
   }

   /**
    * Render a condition (any operator, negation aware) as a null-safe script predicate.
    */
   private String getScriptPredicate(Condition condition, String field, boolean fnumber,
                                     boolean fdate)
   {
      int op = condition.getOperation();
      boolean numeric = fnumber || XSchema.isNumericType(getBaseDataType());
      boolean cs = condition.isCaseSensitive();
      String lhs;

      if(fdate) {
         lhs = "(" + field + ".getYear() * 10000 + " + field + ".getMonth() * 100 + " +
            field + ".getDate())";
      }
      else if(numeric) {
         lhs = "Number(" + field + ")";
      }
      else {
         lhs = cs ? "String(" + field + ")" : "String(" + field + ").toLowerCase()";
      }

      if(op == XCondition.NULL) {
         return condition.isNegated() ? "(" + field + " != null)" : "(" + field + " == null)";
      }

      List<String> rhs = new ArrayList<>();

      for(int k = 0; k < condition.getValueCount(); k++) {
         rhs.add(getScriptLiteral(condition.getValue(k), numeric, fdate, cs));
      }

      String pred;

      if(rhs.isEmpty()) {
         pred = "false";
      }
      else {
         switch(op) {
         case XCondition.EQUAL_TO:
            pred = lhs + " == " + rhs.get(0);
            break;
         case XCondition.LESS_THAN:
            pred = lhs + (condition.isEqual() ? " <= " : " < ") + rhs.get(0);
            break;
         case XCondition.GREATER_THAN:
            pred = lhs + (condition.isEqual() ? " >= " : " > ") + rhs.get(0);
            break;
         case XCondition.ONE_OF:
            StringBuilder one = new StringBuilder();

            for(int k = 0; k < rhs.size(); k++) {
               one.append(k > 0 ? " || " : "").append(lhs).append(" == ").append(rhs.get(k));
            }

            pred = one.toString();
            break;
         case XCondition.BETWEEN:
            pred = rhs.size() < 2 ? "false" :
               lhs + " >= " + rhs.get(0) + " && " + lhs + " <= " + rhs.get(1);
            break;
         case XCondition.STARTING_WITH:
            pred = fdate || numeric ? "false" : lhs + ".indexOf(" + rhs.get(0) + ") == 0";
            break;
         case XCondition.CONTAINS:
            pred = fdate || numeric ? "false" : lhs + ".indexOf(" + rhs.get(0) + ") >= 0";
            break;
         case XCondition.LIKE:
            if(fdate || numeric) {
               pred = "false";
            }
            else {
               String regex =
                  Condition.getLikePattern("" + condition.getValue(0), true).pattern();
               pred = "new RegExp(\"" + StringEscapeUtils.escapeEcmaScript(regex) + "\"" +
                  (cs ? "" : ", \"i\"") + ").test(String(" + field + "))";
            }

            break;
         default:
            pred = "false";
         }
      }

      pred = field + " != null && (" + pred + ")";
      return condition.isNegated() ? "!(" + pred + ")" : "(" + pred + ")";
   }

   private String getScriptLiteral(Object value, boolean numeric, boolean fdate, boolean cs) {
      if(fdate && value instanceof Date) {
         Calendar calendar = CoreTool.calendar.get();
         calendar.clear();
         calendar.setTime((Date) value);
         return "" + ((calendar.get(Calendar.YEAR) - 1900) * 10000 +
            calendar.get(Calendar.MONTH) * 100 + calendar.get(Calendar.DATE));
      }

      String str = value == null ? "" : "" + value;

      if(numeric) {
         try {
            double d = Double.parseDouble(str.trim());

            if(!Double.isNaN(d) && !Double.isInfinite(d)) {
               return Double.toString(d);
            }
         }
         catch(NumberFormatException ignore) {
            // not a number, rendered as a string literal below
         }
      }

      return "\"" + StringEscapeUtils.escapeEcmaScript(cs ? str : str.toLowerCase()) + "\"";
   }

   /**
    * Render a condition list (any operators, negation and junction aware) as a SQL predicate.
    */
   private String getSQLPredicate(ConditionList clist, String field, boolean isdate) {
      StringBuilder sb = new StringBuilder();
      boolean firstItem = true;

      for(int j = 0; j < clist.getConditionSize(); j++) {
         ConditionItem item = clist.getConditionItem(j);

         if(item == null) {
            continue;
         }

         if(!firstItem) {
            sb.append(clist.getJunction(j - 1) == JunctionOperator.OR ? " OR " : " AND ");
         }

         firstItem = false;
         sb.append(getSQLPredicate(item.getCondition(), field, isdate));
      }

      return sb.length() == 0 ? "1=0" : "(" + sb + ")";
   }

   private String getSQLPredicate(Condition condition, String field, boolean isdate) {
      int op = condition.getOperation();
      String pred;

      if(op == XCondition.NULL) {
         return condition.isNegated() ? "(" + field + " IS NOT NULL)" :
            "(" + field + " IS NULL)";
      }

      List<String> lits = new ArrayList<>();

      for(int k = 0; k < condition.getValueCount(); k++) {
         lits.add(getSQLLiteral(condition, condition.getValue(k), isdate));
      }

      if(lits.isEmpty()) {
         pred = "1=0";
      }
      else {
         switch(op) {
         case XCondition.EQUAL_TO:
            pred = field + " = " + lits.get(0);
            break;
         case XCondition.LESS_THAN:
            pred = field + (condition.isEqual() ? " <= " : " < ") + lits.get(0);
            break;
         case XCondition.GREATER_THAN:
            pred = field + (condition.isEqual() ? " >= " : " > ") + lits.get(0);
            break;
         case XCondition.ONE_OF:
            pred = field + " IN (" + String.join(", ", lits) + ")";
            break;
         case XCondition.BETWEEN:
            pred = lits.size() < 2 ? "1=0" :
               field + " BETWEEN " + lits.get(0) + " AND " + lits.get(1);
            break;
         case XCondition.STARTING_WITH:
            pred = field + " LIKE " + getLikeLiteral(condition.getValue(0), false, true) +
               " ESCAPE '!'";
            break;
         case XCondition.CONTAINS:
            pred = field + " LIKE " + getLikeLiteral(condition.getValue(0), true, true) +
               " ESCAPE '!'";
            break;
         case XCondition.LIKE:
            pred = field + " LIKE " + getLikeLiteral(condition.getValue(0), false, false);
            break;
         default:
            pred = "1=0";
         }
      }

      return condition.isNegated() ? "(" + field + " IS NULL OR NOT (" + pred + "))" :
         "(" + pred + ")";
   }

   private String getSQLLiteral(Condition condition, Object value, boolean isdate) {
      if(isdate) {
         return "" + condition.getValueSQLString(value);
      }

      String str = value == null ? "" : "" + value;

      if(("db2".equals(getDBType()) || "derby".equals(getDBType()) ||
          "sybase".equals(getDBType())) && XSchema.isNumericType(getBaseDataType()))
      {
         try {
            Double.parseDouble(str.trim());
            return str.trim();
         }
         catch(NumberFormatException ignore) {
            // quoted below
         }
      }

      return "'" + escapeSQLString(str) + "'";
   }

   /**
    * Escape a value for use inside a single-quoted SQL string literal. MySQL/MariaDB treat a
    * backslash as an escape character, so it is doubled there; elsewhere it is a plain character.
    */
   private String escapeSQLString(String str) {
      if("mysql".equalsIgnoreCase(getDBType()) || "mariadb".equalsIgnoreCase(getDBType())) {
         str = str.replace("\\", "\\\\");
      }

      return str.replace("'", "''");
   }

   private String getLikeLiteral(Object value, boolean leading, boolean startsOrContains) {
      String str = value == null ? "" : "" + value;

      if(startsOrContains) {
         str = str.replace("!", "!!").replace("%", "!%").replace("_", "!_");
      }

      str = escapeSQLString(str);
      return "'" + (leading ? "%" : "") + str + (startsOrContains ? "%" : "") + "'";
   }

   /**
    * Encode script value.
    */
   private String encodeScript(String val) {
      return Tool.escapeJavascript(Tool.encodeNL(val));
   }

   /**
    * Get the script expression of this reference.
    * @return a script expression
    */
   @Override
   public String getScriptExpression() {
      String[] names = groupNameInfo.getGroups();

      if(names == null || names.length == 0) {
         return "0";
      }

      StringBuilder sb = new StringBuilder();
      String field = "field['" + Tool.escapeJavascript(ref.getName()) + "']";
      String tfield = "field['" + Tool.escapeJavascript(ref.getName()) + "']";
      boolean first = true;
      boolean fnumber = XSchema.FLOAT.equals(getBaseDataType()) ||
         XSchema.DOUBLE.equals(getBaseDataType());
      boolean fdate = XSchema.isDateType(getBaseDataType());
      boolean cnull = false;

      OUTER:
      for(int i = 0; i < names.length; i++) {
         ConditionList clist = groupNameInfo.getGroupCondition(names[i]);

         if(clist == null) {
            continue;
         }

         for(int j = 0; j < clist.getConditionSize(); j += 2) {
            ConditionItem item = clist.getConditionItem(j);
            Condition condition = item.getCondition();
            int op = condition.getOperation();

            if(op == XCondition.NULL) {
               cnull = true;
               break OUTER;
            }
         }
      }

      for(int i = 0; i < names.length; i++) {
         ConditionList clist = groupNameInfo.getGroupCondition(names[i]);

         if(clist == null) {
            continue;
         }

         if(!first) {
            sb.append("else ");
         }

         sb.append("if(");
         first = false;

         for(int j = 0; j < clist.getConditionSize(); j++) {
            ConditionItem item = clist.getConditionItem(j);
            int junction = clist.getJunction(j - 1);

            if(item == null) {
               continue;
            }

            if(j > 0) {
               sb.append((junction == JunctionOperator.OR) ? " || " : " && ");
            }

            Condition condition = item.getCondition();
            int op = condition.getOperation();

            if(op == XCondition.NULL && !condition.isNegated()) {
               sb.append(" (null == (!" + field + "? null : " +
                            tfield + "))");
            }
            else if(!isLegacyCondition(condition)) {
               sb.append(getScriptPredicate(condition, field, fnumber, fdate));
            }
            else {
               for(int k = 0; k < condition.getValueCount(); k++) {
                  sb.append('(');
                  Object value = condition.getValue(k);
                  String val = value == null ? null : fdate && value instanceof Date ?
                     CoreTool.formatDateTime((Date) value) :  "" + value;

                  boolean isNull = val == null || "".equals(val);
                  boolean isNullStr = "null".equalsIgnoreCase(val);
                  val = encodeScript(val);

                  if(fnumber) {
                     sb.append(field + " != null && abs(" + condition.getValue(k) +
                        " - " + field + ") < 0.000001 )");
                     continue;
                  }
                  else if(fdate && !isNull && value instanceof Date) {
                     Calendar calendar = CoreTool.calendar.get();
                     calendar.clear();
                     calendar.setTime((Date) value);
                     int year = calendar.get(Calendar.YEAR);
                     int month = calendar.get(Calendar.MONTH);
                     int day = calendar.get(Calendar.DATE);
                     String diff = "(" + field + " == null ? -1 : " +
                        field + ".getYear() * 10000 + " + field +
                        ".getMonth() * 100 + " + field + ".getDate() - " +
                        ((year - 1900) * 10000 + month * 100 + day) + ")";

                     switch(op) {
                     case XCondition.EQUAL_TO:
                        sb.append(diff + " == 0)");
                        break;
                     case XCondition.LESS_THAN:
                        sb.append(condition.isEqual() ?
                           diff + " <= 0)" : diff + " < 0)");
                        break;
                     case XCondition.GREATER_THAN:
                        sb.append(condition.isEqual() ?
                           diff + " >= 0)" : diff + " > 0)");
                        break;
                     }

                     continue;
                  }

                  if(isNull) {
                     sb.append("null");
                  }
                  else {
                     sb.append("\"" + val + "\"");
                  }

                  switch(op) {
                  case XCondition.EQUAL_TO:
                     sb.append(" == ");
                     break;
                  case XCondition.LESS_THAN:
                     sb.append(condition.isEqual() ? " >= " : " > ");
                     break;
                  case XCondition.GREATER_THAN:
                     sb.append(condition.isEqual() ? " <= " : " < ");
                     break;
                  }

                  String field0 = field;
                  String tfield0 = tfield;

                  if(isNullStr && op == XCondition.EQUAL_TO) {
                     field0 = "(" + field + " == null ? null : " + field + ".toLowerCase())";
                     tfield0 = "(" + tfield + " == null ? null : " + tfield + ".toLowerCase())";
                  }

                  String replaceAll = "";

                  if("string".equalsIgnoreCase(ref.getDataType())) {
                     replaceAll = ".replace(/\\\\/g, \"\\\\\\\\\").replace(/\\n/g, \"\\\\n\")";
                  }

                  boolean isEqualsNull = XCondition.EQUAL_TO == op && isNull;

                  if(isEqualsNull) {
                     sb.append("(!" + field0 + "? null :" + tfield0 + replaceAll + "))");
                  }
                  else {
                     sb.append("(" + field0 + " == null ? " + field0 + " : " +
                        tfield0 + replaceAll + "))");
                  }

               }
            }
         }

         sb.append(") {\n");
         sb.append("   \"").append(StringEscapeUtils.escapeEcmaScript(names[i])).append("\";");
         sb.append("\n}");
      }

      sb.append("else {\n");

      if(fnumber) {
         sb.append("   " + field + " == null ? " + field +
            " : numberToString(" + field + ");\n");
      }
      else {
         sb.append("   " + field + " == null ? " + field + " : " +
            tfield + ";\n");
      }

      sb.append("}");
      return sb.toString();
   }

   /**
    * Get the groupNameInfo.
    */
   public XNamedGroupInfo getNamedGroupInfo() {
      return groupNameInfo;
   }

   /**
    * Set the groupNameInfo.
    * @param groupNameInfo XNamedGroupInfo
    */
   public void setNamedGroupInfo(XNamedGroupInfo groupNameInfo) {
      this.groupNameInfo = groupNameInfo;
   }

   /**
    * Convert the filed to String type.
    * @param field the field
    * @return field converted filed
    *
    */
   private String getConvertField(String field) {
      if(!XSchema.isNumericType(getBaseDataType())) {
         return field;
      }

      if("oracle".equals(getDBType()) || "mysql".equals(getDBType())) {
         return "CONCAT(" + field + ", '')";
      }
      else if("sql server".equals(getDBType()) || "sybase".equals(getDBType()))
      {
         return "RTRIM(CONVERT(CHAR, " + field + "))";
      }
      else if("db2".equals(getDBType()) || "derby".equals(getDBType())) {
         return "RTRIM(CHAR(" + field + "))";
      }

      return field;
   }

   @Override
   protected void writeAttributes(PrintWriter writer) {
      writer.print(" attr=\"" + Tool.escape(attr) + "\"");
      writer.print(" dataType=\"" + dataType + "\"");
      writer.print(" baseDataType=\"" + baseDataType + "\"");
   }

   @Override
   protected void parseAttributes(Element tag) throws Exception {
      attr = Tool.getAttribute(tag, "attr");
      dataType = Tool.getAttribute(tag, "dataType");
      baseDataType = Tool.getAttribute(tag, "baseDataType");
   }

   @Override
   protected void writeContents(PrintWriter writer) {
      ref.writeXML(writer);
   }

   /**
    * Read in the contents of this object from an xml tag.
    * @param tag the specified xml element.
    */
   @Override
   protected void parseContents(Element tag) throws Exception {
      Element dnode = Tool.getChildNodeByTagName(tag, "dataRef");
      ref = createDataRef(dnode);
   }

   public static final int DATA_GROUP = 0;
   public static final int COLOR_GROUP = 1;
   public static final int SHAPE_GROUP = 2;
   public static final int SIZE_GROUP = 3;
   public static final int TEXTURE_GROUP = 4;
   private DataRef ref;
   private String attr;
   private String dataType;
   private String baseDataType;
   private XNamedGroupInfo groupNameInfo;
   private transient String dbtype;
   private transient String dbversion;
}
