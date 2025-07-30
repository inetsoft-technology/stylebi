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

import inetsoft.report.Comparer;
import inetsoft.report.filter.*;
import inetsoft.uql.asset.DateCondition;
import inetsoft.uql.asset.ExpressionValue;
import inetsoft.uql.erm.AbstractDataRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.*;
import inetsoft.util.*;
import org.pojava.datetime.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.PrintWriter;
import java.sql.Timestamp;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * A Condition object defines a comparison operation to be performed on a
 * value to determine if it should be included in a result set.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public class Condition extends AbstractCondition {
   /**
    * Checks if a String follows the StyleReport convention for declaring
    * variables. Variables start with $( and end with ).
    * @param v0 the String representation of the variable to check
    */
   public static boolean isVariable(Object v0) {
      return isVariable(v0, false);
   }

   /**
    * Checks if a String follows the StyleReport convention for declaring
    * variables. Variables start with $( and end with ).
    * @param v0 the String representation of the variable to check
    * @param unrestricted check if allows empty value
    */
   public static boolean isVariable(Object v0, boolean unrestricted) {
      String v = getRawValueString(v0);
      return v != null && v.startsWith("$(") && v.endsWith(")") &&
         (unrestricted || v.length() > 3);
   }

   // get string for checking variable
   public static String getRawValueString(Object value) {
      if(value == null) {
         return "";
      }
      else if(value instanceof UserVariable) {
         return "$(" + ((UserVariable) value).getName() + ")";
      }
      else if(value instanceof ExpressionValue) {
         return ((ExpressionValue) value).getExpression();
      }

      return value.toString();
   }

   /**
    * Check if the string is a session variable, _USER_, _ROLES_, _GROUPS_.
    */
   public static boolean isSessionVariable(Object v0) {
      String v = getValueString(v0);

      if(!v.startsWith("$(") || !v.endsWith(")") || v.length() <= 3) {
         return false;
      }

      v = v.substring(2, v.length() - 1);
      return "_USER_".equals(v) || "_ROLES_".equals(v) || "_GROUPS_".equals(v);
   }

   /**
    * Create a condition for a default data type of <code>STRING</code>.
    */
   public Condition() {
      super();

      caseSensitive = Tool.isCaseSensitive();
      ctype = true;
   }


   /**
    * Create a condition for a default data type of <code>STRING</code>.
    */
   public Condition(boolean ctype, boolean dupcheck) {
      super();

      caseSensitive = Tool.isCaseSensitive();
      this.ctype = ctype;
      this.dupcheck = dupcheck;
   }

   /**
    * Create a condition for the specified data type.
    * @param type the data type of the condition. Must be one of the data type
    * constants defined in {@link inetsoft.uql.schema.XSchema}.
    */
   public Condition(String type) {
      this();
      setType(type);
   }

   /**
    * Set the condition value data type.
    * @param type the data type of the condition. Must be one of the data type
    * constants defined in {@link inetsoft.uql.schema.XSchema}.
    */
   @Override
   public void setType(String type) {
      if(type == null) {
         type = XSchema.STRING;
      }

      super.setType(type);

      if(type.equals(XSchema.TIME_INSTANT)) {
         allDateFmt = CoreTool.timeInstantFmt;
      }
      else if(type.equals(XSchema.TIME)) {
         allDateFmt = CoreTool.timeFmt;
      }
      else if(type.equals(XSchema.DATE)) {
         allDateFmt = CoreTool.dateFmt;
      }

      comp = null;
      convertType();
   }

   /**
    * Set whether the test will be case sensitive when comparing strings.
    * @param caseSensitive <code>true</code> for case sensitive comparisons.
    */
   public void setCaseSensitive(boolean caseSensitive) {
      this.caseSensitive = caseSensitive;
   }

   /**
    * Determine if the test will be case sensitive when comparing strings.
    * @return <code>true</code> for case sensitive comparisons.
    */
   public boolean isCaseSensitive() {
      return caseSensitive;
   }

   /**
    * Set whether to ignore this condition.
    */
   public void setIgnored(boolean ignored) {
      this.ignored = ignored;
   }

   /**
    * Check whether to ignore this condition.
    */
   public boolean isIgnored() {
      return ignored;
   }

   /**
    * Check if type is changeable.
    * @return <tt>true</tt> if changeable, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isTypeChangeable() {
      return true;
   }

   /**
    * Check if operation is changeable.
    * @return <tt>true</tt> if changeable, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isOperationChangeable() {
      return true;
   }

   /**
    * Check if equal is changeable.
    * @return <tt>true</tt> if changeable, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isEqualChangeable() {
      return true;
   }

   /**
    * Check if negated is changeable.
    * @return <tt>true</tt> if changeable, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isNegatedChangeable() {
      return true;
   }

   /**
    * Add a condition value. Condition values are the predefined objects that
    * objects will be compared with.
    * @param value the condition value.
    */
   public void addValue(Object value) {
      if(dupcheck && containsValue(value)) {
         return;
      }

      if(value == null) {
         return;
      }

      values.add(convertType(value));
   }

   /**
    * Set (replace) a condition value.
    * @param idx value index.
    * @param value the condition value.
    */
   public void setValue(int idx, Object value) {
      if(value instanceof Object[]) {
         Object[] convertedValues = new Object[((Object[]) value).length];

         for(int i = 0; i < convertedValues.length; i++) {
            convertedValues[i] = convertType(((Object[]) value)[i]);
         }

         value = convertedValues;
      }

      values.set(idx, convertType(value));
   }

   /**
    * Set a condition value from a variable or expression. The value may be normalized
    * (e.g. converted to array for one-of).
    */
   public void setDynamicValue(int i, Object userv, boolean asIs) {
      if(op == ONE_OF && userv instanceof String && !asIs) {
         userv = Tool.convertParameter((String) userv);
      }
      else if(op == ONE_OF && userv instanceof Object[] && !asIs) {
         userv = flattenParameters((Object[]) userv);
      }

      setValue(i, userv);
   }

   private static Object[] flattenParameters(Object[] params) {
      List<Object> list = new ArrayList<>();

      for(Object param : params) {
         if(param instanceof String) {
            String[] arr = Tool.convertParameter((String) param);

            if(arr != null) {
               list.addAll(Arrays.asList(arr));
            }
         }
         else {
            list.add(param);
         }
      }

      return list.toArray(new Object[0]);
   }

   /**
    * Clear all values from this condition.
    */
   public void removeAllValues() {
      // @by stephenwebster, For #621
      // Do not use Vector for 'values'.  It will cause a deadlock.
      values = new ArrayList<>();
   }

   /**
    * Get the number of values in this condition.
    * @return the number of values.
    */
   public int getValueCount() {
      return values.size();
   }

   /**
    * Get the specified value.
    * @param index the zero-based index of the value to get.
    * @return the value object.
    */
   public Object getValue(int index) {
      if(index < 0 || index >= values.size()) {
         return null;
      }

      Object val = values.get(index);

      return (val instanceof String) && ctype && op != DATE_IN ?
         getObject(getType(), (String) val) : val;
   }

   /**
    * Get the data ref value if any.
    * @return the contained data ref value if any.
    */
   public DataRef[] getDataRefValues() {
      List<DataRef> list = new ArrayList<>();

      for(int i = 0; i < getValueCount(); i++) {
         Object obj = getValue(i);

         if(!(obj instanceof DataRef)) {
            continue;
         }

         list.add((DataRef) obj);
      }

      DataRef[] arr = new DataRef[list.size()];
      list.toArray(arr);

      return arr;
   }

   /**
    * Set whether to ignore the null value.
    */
   public void setIgnoreNullValue(boolean ignoreNullValue) {
      this.ignoreNullValue = ignoreNullValue;
   }

   /**
    * Check whether to ignore the null value.
    */
   public boolean isIgnoreNullValue() {
      return ignoreNullValue;
   }

   /**
    * Replace all embeded user variables with value from variable table.
    */
   @Override
   public void replaceVariable(VariableTable vart) {
      ignored = false;

      for(int i = 0; vart != null && i < getValueCount(); i++) {
         Object val = getValue(i);
         String name = null;
         Object userv = null;

         if(val instanceof UserVariable) {
            UserVariable var = (UserVariable) val;

            name = var.getName();

            try {
               userv = vart.get(var);
            }
            catch(Exception ex) {
               LOG.error("Failed to set value of user variable " + name, ex);
            }
         }
         else if(isVariable(val)) {
            name = getRawValueString(val);
            name = name.substring(2, name.length() - 1);

            try {
               userv = vart.get(name);
            }
            catch(Exception ex) {
               LOG.error("Failed to set value of variable " + name, ex);
            }
         }

         if(name != null) {
            // @by larryl, in sync with query generator
            if(op == ONE_OF && userv instanceof Object[] &&
               ((Object[]) userv).length == 1 && ((Object[]) userv)[0] == null)
            {
               ignored = true;
            }
            else if(userv != null) {
               values.set(i, userv);
            }
            else {
               ignored = true;
            }
         }
      }
   }

   /**
    * Get all variables in the condition value list.
    * @return the variable list.
    */
   @Override
   public UserVariable[] getAllVariables() {
      UserVariable[] vars;
      List<UserVariable> list = new ArrayList<>();

      for(int i = 0; i < getValueCount(); i++) {
         Object val = getValue(i);
         UserVariable uvar = null;

         if(val instanceof String && isVariable(val)) {
            String name = (String) val;
            name = name.substring(2, name.length() - 1);
            uvar = new UserVariable();

            if(VariableTable.isBuiltinVariable(name)) {
               continue;
            }

            uvar.setName(name);
            uvar.setAlias(name);
            uvar.setTypeNode(XSchema.createPrimitiveType(type));

            if(!list.contains(uvar)) {
               list.add(uvar);
            }
         }
         else if(val instanceof UserVariable) {
            uvar = (UserVariable) val;
            uvar.setTypeNode(XSchema.createPrimitiveType(type));

            if(VariableTable.isBuiltinVariable(uvar.getName())) {
               continue;
            }

            if(!list.contains(uvar)) {
               list.add(uvar);
            }
         }

         // @by larryl, if the variable is used as the value of oneOf, allow
         // multiple selections
         if(op == ONE_OF && uvar != null) {
            uvar.setMultipleSelection(true);
         }
      }

      vars = new UserVariable[list.size()];
      list.toArray(vars);

      return vars;
   }

   /**
    * Get the values of a row.
    * @return the values of the row.
    */
   public List<Object> getValues() {
      return values;
   }

   /**
    * Set the values of a row.
    */
   public void setValues(List<Object> values) {
      this.values = values;
   }

   /*
    * Check if should convert data type.
    * @return <tt>true</tt> to convert data type, <tt>false</tt> otherwise.
    */
   public final boolean isConvertingType() {
      return ctype;
   }

   /*
    * Set whether should convert data type.
    * @param ctype <tt>true</tt> to convert data type, <tt>false</tt> otherwise.
    */
   public final void setConvertingType(boolean ctype) {
      this.ctype = ctype;
   }

   /**
    * Evaluate this condition against the specified value object.
    * @param value the value object this condition should be compared with.
    * @return <code>true</code> if the value object meets this condition.
    */
   @Override
   public boolean evaluate(Object value) {
      // @by billh, if any parameter in the condition has no corresponding
      // value, we always return true to ignore the condition, then we could
      // keep in sync with preprocess filtering and postprocess filtering
      if(ignored) {
         return true;
      }

      // @by billh, if the value is an array, use the first element
      // of the array to compare, for the value of our ListElement is
      // always an array. The logic does no harm because the following
      // compare logic assumes that the value is a meaningful object.
      // But for role, the value should always be an array for role
      // comparer to handle
      if(value instanceof Object[] && !type.equals(XSchema.ROLE)) {
         Object[] vals = (Object[]) value;

         if(vals.length > 0) {
            value = vals[0];
         }
      }

      if(comp == null) {
         if(type.equals(XSchema.TIME)) {
            comp = new TimeComparer(allDateFmt.get());
         }
         else if(type.equals(XSchema.DATE)) {
            comp = new DateComparer(allDateFmt.get());
         }
         else if(type.equals(XSchema.TIME_INSTANT)) {
            comp = new DateTimeComparer(allDateFmt.get());
         }
         else if(type.equals(XSchema.ROLE)) {
            comp = new RoleComparer();
         }
         else if(type.equals(XSchema.BOOLEAN)) {
            comp = new BooleanComparer();
         }
         else {
            comp = ImmutableDefaultComparer.getInstance(caseSensitive);
         }
      }

      boolean result = false;
      List<Object> values = getValues();

      for(int i = 0; i < values.size(); i++) {
         Object val = values.get(i);

         if(val instanceof String) {
            if(val.equals(XConstants.CONDITION_NULL_VALUE)) {
               setOperation(XCondition.NULL);
            }
            else if(val.equals(XConstants.CONDITION_EMPTY_STRING)) {
               setValue(i, "");
            }
            else if(val.equals(XConstants.CONDITION_NULL_STRING)) {
               setValue(i, "null");
            }
         }
         else if(val instanceof Object[]) {
            Object[] objs = (Object[]) val;

            if(objs.length == 1) {
               if(XConstants.CONDITION_NULL_VALUE.equals(objs[0])) {
                  setOperation(XCondition.NULL);
                  break;
               }
               else if(XConstants.CONDITION_EMPTY_STRING.equals(objs[0])) {
                  objs[0] = "";
               }
               else if(XConstants.CONDITION_NULL_STRING.equals(objs[0]))
               {
                  objs[0] = "null";
               }
            }

            for(int j = 0; j < objs.length && objs.length > 1; j++) {
               if(XConstants.CONDITION_NULL_VALUE.equals(objs[j]) ||
                  XConstants.CONDITION_EMPTY_STRING.equals(objs[j]))
               {
                  LOG.warn(
                     "Ignore the NULL_VALUE or EMPTY_STRING condition");
               }
            }
         }
      }

      if(op == ONE_OF) {
         // in sync with sql
         if(value == null) {
            return false;
         }

         // in sync with sql
         if(values.size() == 1 && values.get(0) == null) {
            return false;
         }

         List<Object> currentSortedValues = sortedValues;

         // optimization, avoid iterative search. (54486)
         if(sortedValues == null || !Tool.equals(Tool.getDataType(value), type)) {
            currentSortedValues = new ArrayList<>();

            for(Object obj : values) {
               if(obj instanceof Object[]) {
                  for(Object v : (Object[]) obj) {
                     currentSortedValues.add(normalizeValue(v, value));
                  }
               }
               else {
                  currentSortedValues.add(normalizeValue(obj, value));
               }
            }

            Collections.sort(currentSortedValues, comp);

            if(value != null && !"".equals(value)) {
               sortedValues = currentSortedValues;
            }
         }
         else {
            currentSortedValues = sortedValues;
         }

         String valueType = Tool.getDataType(value);

         if(!Tool.equals(valueType, type) && sortedValues != null &&
            sortedValues.size() > 0 && !Tool.equals(Tool.getDataType(sortedValues.get(0)), valueType))
         {
            final Object currentValue = value;
            result = sortedValues.stream().anyMatch(sv -> comp.compare(currentValue, sv) == 0);
         }
         else {
            result = Collections.binarySearch(currentSortedValues, value, comp) >= 0;
         }
      }
      else if(op == LESS_THAN) {
         // in sync with sql
         if(value == null) {
            return false;
         }

         if(values.size() > 0) {
            Object obj = values.get(0);
            obj = normalizeValue(obj, value);

            // in sync with sql
            if(obj == null) {
               return false;
            }

            int r = comp.compare(value, obj);
            result = isEqual() ? (r <= 0) : (r < 0);
         }
      }
      else if(op == GREATER_THAN) {
         // in sync with sql
         if(value == null) {
            return false;
         }

         if(values.size() > 0) {
            Object obj = values.get(0);
            obj = normalizeValue(obj, value);

            // in sync with sql
            if(obj == null) {
               return false;
            }

            int r = comp.compare(value, obj);
            result = isEqual() ? (r >= 0) : (r > 0);
         }
      }
      else if(op == EQUAL_TO) {
         if(values.size() > 0) {
            Object obj = values.get(0);
            obj = normalizeValue(obj, value);

            // in sync with sql
            if(obj == null && isIgnoreNullValue()) {
               return true;
            }

            result = comp.compare(value, obj) == 0;
         }
      }
      else if(op == BETWEEN) {
         // in sync with sql
         if(value == null) {
            return false;
         }

         if(values.size() > 1) {
            Object obj1 = values.get(0);
            obj1 = normalizeValue(obj1, value);
            Object obj2 = values.get(1);
            obj2 = normalizeValue(obj2, value);

            // in sync with sql
            if(obj1 == null || obj2 == null) {
               return false;
            }

            result = (comp.compare(value, obj1) >= 0) &&
               (comp.compare(value, obj2) <= 0);
         }
      }
      else if(op == STARTING_WITH) {
         // in sync with sql
         if(value == null) {
            return false;
         }

         if(values.size() > 0) {
            Object obj = values.get(0);
            obj = normalizeValue(obj, value);

            value = stringValue(value);
            obj = stringValue(obj);

            // in sync with sql
            if(obj == null) {
               return false;
            }

            if(value != null) {
               if(caseSensitive) {
                  result = ((String) value).startsWith((String) obj);
               }
               else {
                  String value2 = ((String) value).toLowerCase();
                  String obj2 = ((String) obj).toLowerCase();
                  result = value2.startsWith(obj2);
               }
            }
         }
      }
      else if(op == CONTAINS) {
         // in sync with sql
         if(value == null) {
            return false;
         }

         if(values.size() > 0) {
            Object obj = values.get(0);
            obj = normalizeValue(obj, value);

            value = stringValue(value);
            obj = stringValue(obj);

            // in sync with sql
            if(obj == null) {
               return false;
            }

            if(value != null) {
               if(caseSensitive) {
                  result = ((String) value).contains((String) obj);
               }
               else {
                  String value2 = ((String) value).toLowerCase();
                  String obj2 = ((String) obj).toLowerCase();
                  result = value2.contains(obj2);
               }
            }
         }
      }
      else if(op == LIKE) {
         // in sync with sql
         if(value == null) {
            return false;
         }

         if(values.size() > 0) {
            Object obj = values.get(0);
            obj = normalizeValue(obj, value);

            value = stringValue(value);
            obj = stringValue(obj);

            // in sync with sql
            if(obj == null) {
               return false;
            }

            if(value != null) {
               Pattern pattern = getLikePattern((String) obj, caseSensitive);
               Matcher matcher = pattern.matcher((String) value);
               result = matcher.find();
            }
         }
      }
      else if(op == DATE_IN) {
         if(value == null) {
            return false;
         }

         if(values.size() > 0) {
            Object obj = values.get(0);
            result = isInDateRange(obj, value);
         }
      }
      else if(op == NULL) {
         result = value == null ||
            (value instanceof String && ((String) value).length() == 0) ||
            (value instanceof String && value.equals("null") && !isStrictMatchNull());
      }

      return isNegated() ? !result : result;
   }

   public static Pattern getLikePattern(String obj, boolean caseSensitive) {
      StringBuilder regex = new StringBuilder();
      regex.append('^');

      for(int i = 0; i < obj.length(); i++) {
         char c = obj.charAt(i);

         switch(c) {
         case '%':
            regex.append(".*");
            break;

         case '?':
            regex.append(".");
            break;

         case '\\':
         case '^':
         case '.':
         case '$':
         case '|':
         case '(':
         case ')':
         case '[':
         case ']':
         case '*':
         case '+':
         case '{':
         case '}':
         case ',':
         case '/':
            regex.append('\\');

         default:
            regex.append(c);
         }
      }

      regex.append('$');
      Pattern pattern;

      if(caseSensitive) {
         pattern = Pattern.compile(regex.toString());
      }
      else {
         pattern = Pattern.compile(regex.toString(), Pattern.CASE_INSENSITIVE);
      }
      return pattern;
   }

   public boolean isInDateRange(Object obj, Object value) {
      if(value == null || obj == null || !(value instanceof Date)) {
         return false;
      }

      boolean result = false;
      Date date = (Date) value;
      Date now = new Date(System.currentTimeMillis());
      int nquarters;
      int dquarters;
      int nmonths;
      int dmonths;
      int w1;
      int w2;
      int d1;
      int d2;
      String str = stringValue(obj);

      if(str.equalsIgnoreCase("last year")) {
         result = getYear(now) - LAST_YEAR == getYear(date);
      }
      else if(str.equalsIgnoreCase("this year")) {
         result = getYear(now) - THIS_YEAR == getYear(date);
      }
      else if(str.equalsIgnoreCase("this quarter")) {
         nquarters = getYear(now) * 4 + getQuarter(now);
         dquarters = getYear(date) * 4 + getQuarter(date);
         result = nquarters - THIS_QUARTER == dquarters + THIS_YEAR * 4;
      }
      else if(str.equalsIgnoreCase("last quarter")) {
         nquarters = getYear(now) * 4 + getQuarter(now);
         dquarters = getYear(date) * 4 + getQuarter(date);
         result = nquarters - LAST_QUARTER == dquarters + THIS_YEAR * 4;
      }
      else if(str.equalsIgnoreCase("this quarter last year")) {
         nquarters = getYear(now) * 4 + getQuarter(now);
         dquarters = getYear(date) * 4 + getQuarter(date);
         result = nquarters - THIS_QUARTER == dquarters + LAST_YEAR * 4;
      }
      else if(str.equalsIgnoreCase("last quarter last year")) {
         nquarters = getYear(now) * 4 + getQuarter(now);
         dquarters = getYear(date) * 4 + getQuarter(date);
         result = nquarters - LAST_QUARTER == dquarters + LAST_YEAR * 4;
      }
      else if(str.equalsIgnoreCase("1st quarter this year")) {
         if(getYear(now) - THIS_YEAR != getYear(date)) {
            return isNegated();
         }

         result = getQuarter(date) == 0;
      }
      else if(str.equalsIgnoreCase("2nd quarter this year")) {
         if(getYear(now) - THIS_YEAR != getYear(date)) {
            return isNegated();
         }

         result = getQuarter(date) == 1;
      }
      else if(str.equalsIgnoreCase("3rd quarter this year")) {
         if(getYear(now) - THIS_YEAR != getYear(date)) {
            return isNegated();
         }

         result = getQuarter(date) == 2;
      }
      else if(str.equalsIgnoreCase("4th quarter this year")) {
         if(getYear(now) - THIS_YEAR != getYear(date)) {
            return isNegated();
         }

         result = getQuarter(date) == 3;
      }
      else if(str.equalsIgnoreCase("1st quarter last year")) {
         if(getYear(now) - LAST_YEAR != getYear(date)) {
            return isNegated();
         }

         result = getQuarter(date) == 0;
      }
      else if(str.equalsIgnoreCase("2nd quarter last year")) {
         if(getYear(now) - LAST_YEAR != getYear(date)) {
            return isNegated();
         }

         result = getQuarter(date) == 1;
      }
      else if(str.equalsIgnoreCase("3rd quarter last year")) {
         if(getYear(now) - LAST_YEAR != getYear(date)) {
            return isNegated();
         }

         result = getQuarter(date) == 2;
      }
      else if(str.equalsIgnoreCase("4th quarter last year")) {
         if(getYear(now) - LAST_YEAR != getYear(date)) {
            return isNegated();
         }

         result = getQuarter(date) == 3;
      }
      else if(str.equalsIgnoreCase("1st half of this year")) {
         if(getYear(now) - THIS_YEAR != getYear(date)) {
            return isNegated();
         }

         result = getHalfYear(date) == 0;
      }
      else if(str.equalsIgnoreCase("2nd half of this year")) {
         if(getYear(now) - THIS_YEAR != getYear(date)) {
            return isNegated();
         }

         result = getHalfYear(date) == 1;
      }
      else if(str.equalsIgnoreCase("1st half of last year")) {
         if(getYear(now) - LAST_YEAR != getYear(date)) {
            return isNegated();
         }

         result = getHalfYear(date) == 0;
      }
      else if(str.equalsIgnoreCase("2nd half of last year")) {
         if(getYear(now) - LAST_YEAR != getYear(date)) {
            return isNegated();
         }

         result = getHalfYear(date) == 1;
      }
      else if(str.equalsIgnoreCase("this month")) {
         nmonths = getYear(now) * 12 + getMonth(now);
         dmonths = getYear(date) * 12 + getMonth(date);
         result = nmonths - THIS_MONTH == dmonths + THIS_YEAR * 12;
      }
      else if(str.equalsIgnoreCase("last month")) {
         nmonths = getYear(now) * 12 + getMonth(now);
         dmonths = getYear(date) * 12 + getMonth(date);
         result = nmonths - LAST_MONTH == dmonths + THIS_YEAR * 12;
      }
      else if(str.equalsIgnoreCase("this month last year")) {
         nmonths = getYear(now) * 12 + getMonth(now);
         dmonths = getYear(date) * 12 + getMonth(date);
         result = nmonths - THIS_MONTH == dmonths + LAST_YEAR * 12;
      }
      else if(str.equalsIgnoreCase("last month last year")) {
         nmonths = getYear(now) * 12 + getMonth(now);
         dmonths = getYear(date) * 12 + getMonth(date);
         result = nmonths - LAST_MONTH == dmonths + LAST_YEAR * 12;
      }
      else if(str.equalsIgnoreCase("last 1 months")) {
         result = isBoolMonth(0, 2, date, now);
      }
      else if(str.equalsIgnoreCase("last 3 months")) {
         result = isBoolMonth(0, 4, date, now);
      }
      else if(str.equalsIgnoreCase("last 6 months")) {
         result = isBoolMonth(0, 7, date, now);
      }
      else if(str.equalsIgnoreCase("last 12 months")) {
         result = isBoolMonth(0, 13, date, now);
      }
      else if(str.equalsIgnoreCase("last 18 months")) {
         result = isBoolMonth(0, 19, date, now);
      }
      else if(str.equalsIgnoreCase("last 24 months")) {
         result = isBoolMonth(0, 25, date, now);
      }
      else if(str.equalsIgnoreCase("last 36 months")) {
         result = isBoolMonth(0, 37, date, now);
      }
      else if(str.equalsIgnoreCase("last 48 months")) {
         result = isBoolMonth(0, 49, date, now);
      }
      else if(str.equalsIgnoreCase("last 60 months")) {
         result = isBoolMonth(0, 61, date, now);
      }
      else if(str.equalsIgnoreCase("last 72 months")) {
         result = isBoolMonth(0, 73, date, now);
      }
      else if(str.equalsIgnoreCase("last 84 months")) {
         result = isBoolMonth(0, 85, date, now);
      }
      else if(str.equalsIgnoreCase("last 96 months")) {
         result = isBoolMonth(0, 97, date, now);
      }
      else if(str.equalsIgnoreCase("last 108 months")) {
         result = isBoolMonth(0, 109, date, now);
      }
      else if(str.equalsIgnoreCase("last 120 months")) {
         result = isBoolMonth(0, 121, date, now);
      }
      else if(str.equalsIgnoreCase("this january")) {
         if(getYear(now) - THIS_YEAR != getYear(date)) {
            return isNegated();
         }

         result = getMonth(date) == JANUARY;
      }
      else if(str.equalsIgnoreCase("this february")) {
         if(getYear(now) - THIS_YEAR != getYear(date)) {
            return isNegated();
         }

         result = getMonth(date) == FEBRUARY;
      }
      else if(str.equalsIgnoreCase("this march")) {
         if(getYear(now) - THIS_YEAR != getYear(date)) {
            return isNegated();
         }

         result = getMonth(date) == MARCH;
      }
      else if(str.equalsIgnoreCase("this april")) {
         if(getYear(now) - THIS_YEAR != getYear(date)) {
            return isNegated();
         }

         result = getMonth(date) == APRIL;
      }
      else if(str.equalsIgnoreCase("this may")) {
         if(getYear(now) - THIS_YEAR != getYear(date)) {
            return isNegated();
         }

         result = getMonth(date) == MAY;
      }
      else if(str.equalsIgnoreCase("this june")) {
         if(getYear(now) - THIS_YEAR != getYear(date)) {
            return isNegated();
         }

         result = getMonth(date) == JUNE;
      }
      else if(str.equalsIgnoreCase("this july")) {
         if(getYear(now) - THIS_YEAR != getYear(date)) {
            return isNegated();
         }

         result = getMonth(date) == JULY;
      }
      else if(str.equalsIgnoreCase("this august")) {
         if(getYear(now) - THIS_YEAR != getYear(date)) {
            return isNegated();
         }

         result = getMonth(date) == AUGUST;
      }
      else if(str.equalsIgnoreCase("this september")) {
         if(getYear(now) - THIS_YEAR != getYear(date)) {
            return isNegated();
         }

         result = getMonth(date) == SEPTEMBER;
      }
      else if(str.equalsIgnoreCase("this october")) {
         if(getYear(now) - THIS_YEAR != getYear(date)) {
            return isNegated();
         }

         result = getMonth(date) == OCTOBER;
      }
      else if(str.equalsIgnoreCase("this november")) {
         if(getYear(now) - THIS_YEAR != getYear(date)) {
            return isNegated();
         }

         result = getMonth(date) == NOVEMBER;
      }
      else if(str.equalsIgnoreCase("this december")) {
         if(getYear(now) - THIS_YEAR != getYear(date)) {
            return isNegated();
         }

         result = getMonth(date) == DECEMBER;
      }
      else if(str.equalsIgnoreCase("last january")) {
         if(getYear(now) - LAST_YEAR != getYear(date)) {
            return isNegated();
         }

         result = getMonth(date) == JANUARY;
      }
      else if(str.equalsIgnoreCase("last february")) {
         if(getYear(now) - LAST_YEAR != getYear(date)) {
            return isNegated();
         }

         result = getMonth(date) == FEBRUARY;
      }
      else if(str.equalsIgnoreCase("last march")) {
         if(getYear(now) - LAST_YEAR != getYear(date)) {
            return isNegated();
         }

         result = getMonth(date) == MARCH;
      }
      else if(str.equalsIgnoreCase("last april")) {
         if(getYear(now) - LAST_YEAR != getYear(date)) {
            return isNegated();
         }

         result = getMonth(date) == APRIL;
      }
      else if(str.equalsIgnoreCase("last may")) {
         if(getYear(now) - LAST_YEAR != getYear(date)) {
            return isNegated();
         }

         result = getMonth(date) == MAY;
      }
      else if(str.equalsIgnoreCase("last june")) {
         if(getYear(now) - LAST_YEAR != getYear(date)) {
            return isNegated();
         }

         result = getMonth(date) == JUNE;
      }
      else if(str.equalsIgnoreCase("last july")) {
         if(getYear(now) - LAST_YEAR != getYear(date)) {
            return isNegated();
         }

         result = getMonth(date) == JULY;
      }
      else if(str.equalsIgnoreCase("last august")) {
         if(getYear(now) - LAST_YEAR != getYear(date)) {
            return isNegated();
         }

         result = getMonth(date) == AUGUST;
      }
      else if(str.equalsIgnoreCase("last september")) {
         if(getYear(now) - LAST_YEAR != getYear(date)) {
            return isNegated();
         }

         result = getMonth(date) == SEPTEMBER;
      }
      else if(str.equalsIgnoreCase("last october")) {
         if(getYear(now) - LAST_YEAR != getYear(date)) {
            return isNegated();
         }

         result = getMonth(date) == OCTOBER;
      }
      else if(str.equalsIgnoreCase("last november")) {
         if(getYear(now) - LAST_YEAR != getYear(date)) {
            return isNegated();
         }

         result = getMonth(date) == NOVEMBER;
      }
      else if(str.equalsIgnoreCase("last december")) {
         if(getYear(now) - LAST_YEAR != getYear(date)) {
            return isNegated();
         }

         result = getMonth(date) == DECEMBER;
      }
      else if(str.equalsIgnoreCase("this week")) {
         w1 = getWeeks(date);
         w2 = getWeeks(now);
         result = w1 + THIS_WEEK == w2;
      }
      else if(str.equalsIgnoreCase("last week")) {
         w1 = getWeeks(date);
         w2 = getWeeks(now);
         result = w1 + LAST_WEEK == w2;
      }
      else if(str.equalsIgnoreCase("week before last week")) {
         w1 = getWeeks(date);
         w2 = getWeeks(now);
         result = w1 + 2 == w2;
      }
      else if(str.equalsIgnoreCase("last 4 weeks")) {
         w1 = getWeeks(date);
         w2 = getWeeks(now);
         result = w1 <= w2 && w1 + 5 > w2;
      }
      else if(str.equalsIgnoreCase("last 5-8 weeks")) {
         w1 = getWeeks(date);
         w2 = getWeeks(now);
         result = w1 + 5 <= w2 && w1 + 9 > w2;
      }
      else if(str.equalsIgnoreCase("last 7 days")) {
         d1 = getDays(date);
         d2 = getDays(now);
         result = d1 <= d2 && d1 + 8 > d2;
      }
      else if(str.equalsIgnoreCase("last 8-14 days")) {
         d1 = getDays(date);
         d2 = getDays(now);
         result = d1 + 8 <= d2 && d1 + 15 > d2;
      }
      else if(str.equalsIgnoreCase("last 30 days")) {
         d1 = getDays(date);
         d2 = getDays(now);
         result = d1 <= d2 && d1 + 31 > d2;
      }
      else if(str.equalsIgnoreCase("last 31-60 days")) {
         d1 = getDays(date);
         d2 = getDays(now);
         result = d1 + 31 <= d2 && d1 + 61 > d2;
      }
      else if(str.equalsIgnoreCase("today")) {
         d1 = getDays(date);
         d2 = getDays(now);
         result = d1 == d2;
      }
      else if(str.equalsIgnoreCase("tomorrow")) {
         d1 = getDays(date);
         d2 = getDays(now);
         result = (d1 + 1) == d2;
      }
      else if(str.equalsIgnoreCase("yesterday")) {
         d1 = getDays(date);
         d2 = getDays(now);
         result = (d1 + 1) == d2;
      }
      else if(str.equalsIgnoreCase("year to date")) {
         long timestamp = ((Date) value).getTime();
         long[] range = getRange(System.currentTimeMillis(), THIS_YEAR,
            THIS_QUARTER, THIS_MONTH, 'Y');
         result = timestamp >= range[0] && timestamp < range[1];
      }
      else if(str.equalsIgnoreCase("year to date last year")) {
         long timestamp = ((Date) value).getTime();
         long[] range = getRange(System.currentTimeMillis(), LAST_YEAR,
            THIS_QUARTER, THIS_MONTH, 'Y');
         result = timestamp >= range[0] && timestamp < range[1];
      }
      else if(str.equalsIgnoreCase("quarter to date")) {
         long timestamp = ((Date) value).getTime();
         long[] range = getRange(System.currentTimeMillis(), THIS_YEAR,
            THIS_QUARTER, THIS_MONTH, 'Q');
         result = timestamp >= range[0] && timestamp < range[1];
      }
      else if(str.equalsIgnoreCase("quarter to date last year")) {
         long timestamp = ((Date) value).getTime();
         long[] range = getRange(System.currentTimeMillis(), LAST_YEAR,
            THIS_QUARTER, THIS_MONTH, 'Q');
         result = timestamp >= range[0] && timestamp < range[1];
      }
      else if(str.equalsIgnoreCase("quarter to date last quarter")) {
         long timestamp = ((Date) value).getTime();
         long[] range = getRange(System.currentTimeMillis(), THIS_YEAR,
            LAST_QUARTER, THIS_MONTH, 'Q');
         result = timestamp >= range[0] && timestamp < range[1];
      }
      else if(str.equalsIgnoreCase("month to date")) {
         long timestamp = ((Date) value).getTime();
         long[] range = getRange(System.currentTimeMillis(), THIS_YEAR,
            THIS_QUARTER, THIS_MONTH, 'M');
         result = timestamp >= range[0] && timestamp < range[1];
      }
      else if(str.equalsIgnoreCase("month to date last year")) {
         long timestamp = ((Date) value).getTime();
         long[] range = getRange(System.currentTimeMillis(), LAST_YEAR,
            THIS_QUARTER, THIS_MONTH, 'M');
         result = timestamp >= range[0] && timestamp < range[1];
      }
      else if(str.equalsIgnoreCase("month to date last month")) {
         long timestamp = ((Date) value).getTime();
         long[] range = getRange(System.currentTimeMillis(), THIS_YEAR,
            THIS_QUARTER, LAST_MONTH, 'M');
         result = timestamp >= range[0] && timestamp < range[1];
      }

      return result;
   }

   /**
    * Get the year of a date.
    * @param date the specified date.
    * @return the year of the date.
    */
   private int getYear(Date date) {
      CALENDAR.setTime(date);

      return CALENDAR.get(Calendar.YEAR);
   }

   /**
    * Get the month of a date.
    * @param date the specified date.
    * @return the month of the date.
    */
   private int getMonth(Date date) {
      CALENDAR.setTime(date);

      return CALENDAR.get(Calendar.MONTH);
   }

   /**
    * Get the quarter of a date.
    * @param date the specified date.
    * @return the quarter of the date.
    */
   private int getQuarter(Date date) {
      CALENDAR.setTime(date);

      return CALENDAR.get(Calendar.MONTH) / 3;
   }

   /**
    * Get the half year of a date.
    * @param date the specified date.
    * @return the half year of the date.
    */
   private int getHalfYear(Date date) {
      CALENDAR.setTime(date);

      return CALENDAR.get(Calendar.MONTH) / 6;
   }

   /**
    * Get the weeks of a date from 1970-01-01 on.
    * @param date the specified date.
    * @return the week of the date.
    */
   private int getWeeks(Date date) {
      int days = getDays(date);

      return (days + 4) / 7;
   }

   /**
    * Get the days of a date from 1970-01-01 on.
    * @param date the specified date.
    * @return the day of the date.
    */
   private int getDays(Date date) {
      long ts = date.getTime() + ZONE.getRawOffset() +
         (ZONE.inDaylightTime(date) ? ZONE.getDSTSavings() : 0);

      return (int) (ts / ONE_DAY);
   }

   /**
    * Get the date(only includes year, month and day) from a calendar.
    */
   private long getDate(Calendar cal) {
      int year = cal.get(Calendar.YEAR);
      int month = cal.get(Calendar.MONTH);
      int day = cal.get(Calendar.DAY_OF_MONTH);
      cal.clear();
      cal.set(year, month, day);

      return cal.getTimeInMillis();
   }

   /**
    * Calculates the date range relative to the time provided.
    *
    * @param timeInMillis  the time to base the range on
    * @return  an array, index 0 is the start,
    *                    index 1 is the end of the range.
    */
    private long[] getRange(long timeInMillis, int yearOffset,
       int quarterOffset, int monthOffset, char startReference)
    {
       Calendar cal = CoreTool.calendar.get();
       cal.setTimeInMillis(timeInMillis);

       // Calculate END
       int months = (yearOffset * 4 + quarterOffset) * 3 + monthOffset;
       cal.add(Calendar.MONTH, -months);
       long end = cal.getTimeInMillis();

       // Start should at 00:00:00
       int hours = cal.get(Calendar.HOUR_OF_DAY);
       cal.add(Calendar.HOUR_OF_DAY, -hours);
       int minutes = cal.get(Calendar.MINUTE);
       cal.add(Calendar.MINUTE, -minutes);
       int seconds = cal.get(Calendar.SECOND);
       cal.add(Calendar.SECOND, -seconds);
       int milliseconds = cal.get(Calendar.MILLISECOND);
       cal.add(Calendar.MILLISECOND, -milliseconds);

       // Calculate START
       switch(startReference) {
          case 'Y':
             cal.set(cal.get(Calendar.YEAR), 0, 1);
             break;
          case 'Q':
             int quarter = cal.get(Calendar.MONTH) / 3;
             cal.set(cal.get(Calendar.YEAR), quarter * 3, 1);
             break;
          case 'M':
             cal.set(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), 1);
             break;
       }

       return new long[] {cal.getTimeInMillis(), end};
    }

   private boolean isBoolMonth(int from, int to, Date value, Date now) {
      boolean isTimestamp = false;

      if(value instanceof Timestamp) {
         isTimestamp = true;
      }
      else if(value == null) {
         return false;
      }

      long date = value.getTime();
      CALENDAR.setTime(now);
      CALENDAR.add(Calendar.MONTH, -to + 1);
      long date1 =
         isTimestamp ? CALENDAR.getTimeInMillis() : getDate(CALENDAR);

      CALENDAR.setTime(now);
      CALENDAR.add(Calendar.MONTH, from);
      long date2 = isTimestamp ? CALENDAR.getTimeInMillis() : getDate(CALENDAR);
      return date1 <= date && date2 >= date;
   }

   /**
    * Convert null condition to sql mergeable condition.
    */
   public Condition toNullSqlCondition(int yn) {
      Calendar cal = CoreTool.calendar.get();
      cal.setTimeInMillis(System.currentTimeMillis());
      int year = cal.get(Calendar.YEAR);
      cal.set(year + yn, 0, 1); // Jan 1st yn years earlier
      java.sql.Date date1 = new java.sql.Date(cal.getTimeInMillis());
      cal.set(year + yn, 11, 31); // Sep 31th yn years earlier
      java.sql.Date date2 = new java.sql.Date(cal.getTimeInMillis());

      return createSqlCondition(date1, date2);
   }

   /**
    * Convert this condition to sql mergeable condition.
    */
   public Condition toSqlCondition(boolean isTimestamp, String str) {
      List<DateCondition> list = Arrays.stream(DateCondition.getBuiltinDateConditions())
         .filter(con -> str.equalsIgnoreCase(con.getName()))
         .collect(Collectors.toList());

      if(list != null && list.size() == 1) {
         return list.get(0).toSqlCondition(isTimestamp);
      }
      else {
         return toNullSqlCondition(1);
      }
   }

      /**
    * Create a sql condition, this condition is date type which is between date1
    * and date2.
    * @param date1 the earlier date.
    * @param date2 the later date.
    */
   protected Condition createSqlCondition(Date date1, Date date2) {
      Condition condition = new Condition();
      condition.setNegated(isNegated());
      condition.setOperation(XCondition.BETWEEN);
      condition.addValue(date1);
      condition.addValue(date2);

      return condition;
   }

   /**
    * Set whether optimization is check on.
    */
   public void setOptimized(boolean optimized) {
      this.optimized = optimized;
   }

   /**
    * Check whether optimization is checked on.
    */
   public boolean isOptimized() {
      return optimized;
   }

   /**
    * Normalize a value.
    * @param value the spefied value to be normalized.
    */
   protected Object normalizeValue(Object value, Object target) {
      if(value instanceof Object[]) {
         Object[] arr = (Object[]) value;

         if(arr.length > 0) {
            value = arr[0];
         }
      }

      if(target instanceof Date && value instanceof String) {
         try {
            String dataType = null;

            if(Tool.isDateWithoutTime((String) value)) {
               value = DateTime.parse((String) value).toDate();
               dataType = Tool.DATE;
            }
            else if(Tool.isDate((String) value)) {
               value = DateTime.parse((String) value).toDate();
               dataType = Tool.TIME_INSTANT;
            }
            else if(((String) value).startsWith("{")) {
               String str = (String) value;

               if(str.startsWith("{d ")) {
                  value = CoreTool.dateFmt.get().parse(str);
               }
               else if(str.startsWith("{t ")) {
                  value = CoreTool.timeFmt.get().parse(str);
               }
               else if(str.startsWith("{ts ")) {
                  value = CoreTool.timeInstantFmt.get().parse(str);
               }
            }
            else {
               value = target instanceof java.sql.Time ?
                  Tool.parseTime(((String) value)) : DateTime.parse((String) value).toDate();
            }

            if(dataType == null) {
               dataType = target instanceof java.sql.Date &&
                  Tool.getDataType(value.getClass()) == "timeInstant" ?
                  "timeInstant" : Tool.getDataType(target.getClass());
            }

            value = CoreTool.getData(dataType, value);
         }
         catch(Exception ex) {
            // ignore it
         }
      }
      else if(target instanceof Float && value instanceof String) {
         value = Float.valueOf(value.toString());
      }

      return value;
   }

   /**
    * Check if the condition is a valid condition.
    * @return true if is valid, false otherwise.
    */
   @Override
   public boolean isValid() {
      for(int i = 0; i < getValueCount(); i++) {
         if(getValue(i) instanceof UserVariable) {
            UserVariable var = (UserVariable) getValue(i);

            if("".equals(var.getName())) {
               return false;
            }
         }
         else if(getValue(i) instanceof String) {
            String val = (String) getValue(i);

            if(val.equals("$()")) {
               return false;
            }
         }
      }

      return true;
   }

   /**
    * Write the contents.
    * @param writer the specified print writer.
    */
   @Override
   public void writeContents(PrintWriter writer) {
      try {
         Tool.useDatetimeWithMillisFormat.set(isDatetimeWithMillis());

         for(int j = 0; j < getValueCount(); j++) {
            Object val = getValue(j);
            writeConditionValue(writer, val);
         }
      }
      finally {
         Tool.useDatetimeWithMillisFormat.set(false);
      }
   }

   /**
    * Convert a value to String.
    */
   private static String stringValue(Object obj) {
      if(obj instanceof Object[]) {
         Object[] arr = (Object[]) obj;
         StringBuilder buf = new StringBuilder();

         for(int i = 0; i < arr.length; i++) {
            if(i > 0) {
               buf.append(',');
            }

            buf.append(arr[i]);
         }

         return buf.toString();
      }
      else {
         return (obj == null) ? null : obj.toString();
      }
   }

   /**
    * Set the condition value object as xml.
    */
   protected void writeConditionValue(PrintWriter writer, Object val) {
      if(val instanceof java.util.Date) {
         if(XSchema.DATE.equals(getType())) {
            val = new java.sql.Date(((Date) val).getTime());
         }
         else if(XSchema.TIME_INSTANT.equals(getType())) {
            val = new java.sql.Timestamp(((Date) val).getTime());
         }
         else if(XSchema.TIME.equals(getType())) {
            val = new java.sql.Time(((Date) val).getTime());
         }
      }

      if(val instanceof UserVariable) {
         UserVariable var = (UserVariable) val;

         writer.print("<condition_data isvariable=\"true\" name=\"" +
                      Tool.escape(var.getName()) + "\"");

         if(var.getChoiceQuery() != null) {
            writer.print(" fieldname=\"" + Tool.escape(var.getChoiceQuery()) +
                         "\"");
         }

         writer.println("/>");
      }
      else if(isVariable(val)) {
         String str = getRawValueString(val);
         String name = str.substring(2, str.length() - 1);

         writer.println("<condition_data isvariable=\"true\" name=\"" +
                        Tool.escape(name) + "\">" + "</condition_data>");
      }
      else if(val instanceof DataRef) {
         writer.println("<condition_data isfield=\"true\">");
         ((DataRef) val).writeXML(writer);
         writer.println("</condition_data>");
      }
      else if(val instanceof Object[]) {
         writer.println(
            "<condition_data isarray=\"true\">");

         for(Object item : ((Object[]) val)) {
            writer.println(
               "<condition_data_item><![CDATA[" + getValueString(item) +
               "]]></condition_data_item>");
         }

         writer.println("</condition_data>");
      }
      else {
         writer.println("<condition_data><![CDATA[" +
                        Tool.byteEncode(getValueString(val), true) +
                        "]]></condition_data>");
      }
   }

   /**
    * Parse the contents.
    * @param elem the specified xml element.
    */
   @Override
   public void parseContents(Element elem) throws Exception {
      NodeList nlist = elem.getChildNodes();

      for(int i = 0; i < nlist.getLength(); i++) {
         if(!(nlist.item(i) instanceof Element)) {
            continue;
         }

         Element atag = (Element) nlist.item(i);

         try {
            Tool.useDatetimeWithMillisFormat.set(isDatetimeWithMillis());

            if(atag.getTagName().equals("condition_data")) {
               Object val = parseConditionValue(atag);

               if(val != null) {
                  addValue(val);
               }
            }
         }
         finally {
            Tool.useDatetimeWithMillisFormat.set(false);
         }
      }
   }

   /**
    * Parse the condition value.
    */
   protected Object parseConditionValue(Element atag) throws Exception {
      boolean isvar =
         "true".equals(Tool.getAttribute(atag, "isvariable"));
      boolean isfield =
         "true".equals(Tool.getAttribute(atag, "isfield"));
      boolean isarray = "true".equals(Tool.getAttribute(atag, "isarray"));

      if(isvar) {
         String str = Tool.getAttribute(atag, "name");
         UserVariable var = new UserVariable(str);

         var.setAlias(str);
         var.setTypeNode(new XTypeNode(type));
         var.setChoiceQuery(Tool.getAttribute(atag, "fieldname"));

         return var;
      }
      else if(isfield) {
         NodeList nlist2 = atag.getChildNodes();

         for(int k = 0; k < nlist2.getLength(); k++) {
            if(!(nlist2.item(k) instanceof Element)) {
               continue;
            }

            Element tag2 = (Element) nlist2.item(k);
            return AbstractDataRef.createDataRef(tag2);
         }
      }
      else if(isarray) {
         NodeList nlist2 =
            Tool.getChildNodesByTagName(atag, "condition_data_item");
         Object[] items = new Object[nlist2.getLength()];

         for(int k = 0; k < nlist2.getLength(); k++) {
            items[k] = Tool.getValue(nlist2.item(k), false, true, true);
         }

         return items;
      }

      String val = Tool.byteDecode(Tool.getValue(atag, false, true, true));

      // @by mikec, for starting with and contains, we always use string
      // comparasion, in this case the dest value should be a string
      // if we parse it to other object, will cause the comparasion failure.
      return (op == STARTING_WITH || op == CONTAINS || op == LIKE ||  !ctype) ?
         val : getObject(getType(), (val == null ? "" : val));
   }

   /**
    * Get a textual representation of this object.
    * @return a <code>String</code> containing a textual representation of this
    * object.
    */
   public String toString() {
      StringBuilder strbuff = new StringBuilder();
      Catalog catalog = Catalog.getCatalog();
      strbuff.append(" [");

      if(negated) {
         strbuff.append(catalog.getString("is not"));
      }
      else {
         strbuff.append(catalog.getString("is"));
      }

      strbuff.append("]");
      strbuff.append(" [");

      if(op == EQUAL_TO) {
         strbuff.append(catalog.getString("equal to"));
      }
      else if(op == ONE_OF) {
         strbuff.append(catalog.getString("one of"));
      }
      else if(op == LESS_THAN) {
         strbuff.append(catalog.getString("less than"));
      }
      else if(op == GREATER_THAN) {
         strbuff.append(catalog.getString("greater than"));
      }
      else if(op == BETWEEN) {
         strbuff.append(catalog.getString("between"));
      }
      else if(op == STARTING_WITH) {
         strbuff.append(catalog.getString("starting with"));
      }
      else if(op == CONTAINS) {
         strbuff.append(catalog.getString("contains"));
      }
      else if(op == LIKE) {
         strbuff.append(catalog.getString("like"));
      }
      else if(op == DATE_IN) {
         strbuff.append(catalog.getString("in range"));
      }
      else if(op == NULL) {
         strbuff.append(catalog.getString("null"));
      }
      else if(op == PSEUDO) {
         strbuff.append("pseudo");
      }

      if(equal && (op == LESS_THAN || op == GREATER_THAN)) {
         strbuff.append(" ").append(catalog.getString("or equal to"));
      }

      strbuff.append("]");

      if(op != NULL) {
         strbuff.append(" [");

         for(int i = 0; i < values.size(); i++) {
            Object vobj = values.get(i);

            if(vobj instanceof Object[]) {
               strbuff.append(Tool.arrayToString(vobj));
            }
            else {
               String value = type.equals(XSchema.BOOLEAN) ?
                  catalog.getString(getValueString(vobj)) : getValueString(vobj);
               strbuff.append(value);

               if(i < values.size() - 1) {
                  if(strbuff.capacity() > 80) {
                     strbuff.append("...");
                     break;
                  }
                  else {
                     strbuff.append(",");
                  }
               }
            }
         }

         strbuff.append("]");
      }

      return strbuff.toString();
   }

   /**
    * Print the key to identify this content object. If the keys of two content
    * objects are equal, the content objects are equal too.
    */
   @Override
   public boolean printKey(PrintWriter writer) throws Exception {
      super.printKey(writer);
      writer.print("[");
      int cnt = values.size();
      int delta = Math.max(1, cnt / 20);

      for(int i = 0; i < cnt; i += delta) {
         writer.print(",");
         writer.print(values.get(i));
      }

      writer.print("]");
      return true;
   }

   /**
    * Check if equals another object.
    */
   public boolean equals(Object obj) {
      if(!super.equals(obj)) {
         return false;
      }

      if(!(obj instanceof Condition)) {
         return false;
      }

      Condition cond2 = (Condition) obj;

      if(op == NULL) {
         return strictMatchNull == cond2.strictMatchNull;
      }

      return values.equals(cond2.values);
   }

   @Override
   public int hashCode() {
      return Objects.hash(super.hashCode(), op == NULL ? null : values, strictMatchNull);
   }

   /**
    * Creates and returns a copy of this object.
    * @return a condition Object defining the same parameters as this.
    */
   @Override
   public Condition clone() {
      try {
         Condition cond = (Condition) super.clone();
         cond.values = Tool.deepCloneCollection(values);
         cond.strictMatchNull = strictMatchNull;

         return cond;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone object", ex);
      }

      return null;
   }

   private void convertType() {
      if(!ctype) {
         return;
      }

      for(int i = 0; i < values.size(); i++) {
         Object val = values.get(i);

         val = convertType(val);
         values.set(i, val);
      }
   }

   /**
    * Convert to Date if date is stored as string.
    */
   private Object convertType(Object val) {
      if(val instanceof String && (op == STARTING_WITH || op == CONTAINS || op == LIKE || !ctype)) {
         return val;
      }

      if(val instanceof String) {
         if(isVariable(val)) {
            // don't parse variable
         }
         else if(type.equals(XSchema.TIME_INSTANT)) {
            if(op == DATE_IN) {
               return val;
            }

            try {
               if(CoreTool.timeInstantFmt.get().parse((String) val) != null) {
                  val = new java.sql.Timestamp(
                     CoreTool.timeInstantFmt.get().parse((String) val).getTime());
               }
               else {
                  val = new java.sql.Timestamp(
                     CoreTool.timeInstantFmt_old.get().parse((String)val).getTime());
               }
            }
            catch(Exception ex) {
               // try again with DateTime
            }

            try {
               if(!(val instanceof java.sql.Timestamp)) {
                  DateTime dt = DateTime.parse((String) val, CoreTool.getDateTimeConfig());

                  if(dt != null) {
                     val = new java.sql.Timestamp(dt.toMillis());
                  }
               }
            }
            catch(Exception ex) {
               LOG.warn(Catalog.getCatalog()
                  .getString("common.condition.date.convertError", val));
            }
         }
         else if(type.equals(XSchema.DATE)) {
            if(op == DATE_IN) {
               return val;
            }
            else {
               try {
                  val = new java.sql.Date(CoreTool.dateFmt.get().parse((String) val).getTime());
               }
               catch(Exception ex) {
                  // try again with DateTime
               }

               try {
                  if(!(val instanceof java.sql.Date)) {
                     DateTime dt = DateTime.parse((String) val, CoreTool.getDateTimeConfig());

                     if(dt != null) {
                        val = new java.sql.Date(dt.toMillis());
                     }
                  }
               }
               catch(Exception ex) {
                  LOG.warn(Catalog.getCatalog()
                     .getString("common.condition.date.convertError", val));
               }
            }
         }
         else if(type.equals(XSchema.TIME)) {
            try {
               if(CoreTool.timeFmt.get().parse((String) val) != null) {
                  val = new java.sql.Time(
                     CoreTool.timeFmt.get().parse((String) val).getTime());
               }
               else {
                  val = new java.sql.Time(
                     CoreTool.timeFmt_old.get().parse((String) val).getTime());
               }
            }
            catch(Exception ex) {
               // try again with DateTime
            }

            if(val.toString().length() == Tool.DEFAULT_TIME_PATTERN.length()) {
               try {
                  Date time = Tool.parseTime(val.toString());

                  if(time != null) {
                     val = new java.sql.Time(time.getTime());
                  }
               }
               catch(Exception ignore) {
               }
            }
            else {
               try {
                  if(!(val instanceof java.sql.Time)) {
                     DateTime dt = DateTime.parse((String) val, CoreTool.getDateTimeConfig());

                     if(dt != null) {
                        val = new java.sql.Time(dt.toMillis());
                     }
                  }
               }
               catch(Exception ex) {
                  LOG.warn(Catalog.getCatalog()
                     .getString("common.condition.date.convertError", val));
               }
            }
         }
         else if(type.equals(XSchema.BOOLEAN)) {
            try {
               val = Boolean.valueOf((String) val);
            }
            catch(Exception ex) {
               LOG.warn("Failed to parse boolean", ex);
            }
         }
         else if(type.equals(XSchema.DOUBLE)) {
            try {
               val = Double.valueOf((String) val);
            }
            catch(Exception ex) {
               LOG.warn("Failed to parse double", ex);
            }
         }
      }
      else if(val instanceof UserVariable) {
         if(op == DATE_IN) {
            DateCondition[] dateConditions = DateCondition.parseBuiltinDateConditions();
            String[] lables = new String[dateConditions.length];

            if(dateConditions != null) {
                for(int i = 0; i < dateConditions.length; i++) {
                   lables[i] = dateConditions[i].getName();
                }
            }

            type = XSchema.STRING;
            UserVariable uvar = (UserVariable) val;
            uvar.setValues(lables);
            uvar.setChoices(lables);
            uvar.setTypeNode(XSchema.createPrimitiveType(type));
         }
         else {
            UserVariable uvar = (UserVariable) val;
            uvar.setTypeNode(XSchema.createPrimitiveType(type));
         }
      }

      return val;
   }

   /**
    * Check if contains a values.
    * @param name the specified condtion name.
    * @return <code>true</code> if contains, otherwise <code>false</code>.
    */
   public final boolean containsValue(Object name) {
      if(getOperation() == BETWEEN) {
         return false;
      }

      for(Object value : values) {
         if(name != null && name.equals(value)) {
            return true;
         }
      }

      return false;
   }

   public boolean isStrictMatchNull() {
      return strictMatchNull;
   }

   public void setStrictMatchNull(boolean strictMatchNull) {
      this.strictMatchNull = strictMatchNull;
   }

   public void clearCache() {
      this.sortedValues = null;
   }

   private boolean caseSensitive;
   // @by stephenwebster, For #621
   // Do not use Vector for 'values'.  It will cause a deadlock.
   // It seems unlikely to get a problem here, but if a ConcurrentModification
   // Exception occurs, we will need a read/write lock.
   private List<Object> values = new ArrayList<>();
   private List<Object> sortedValues;
   private boolean ignoreNullValue = true;
   private boolean strictMatchNull = true;
   private transient boolean ctype;
   private transient boolean dupcheck;
   private transient boolean ignored;
   private transient Comparer comp;
   private transient boolean optimized;

   private static final Logger LOG = LoggerFactory.getLogger(Condition.class);
   private static final long ONE_DAY = 24 * 60 * 60 * 1000;
   private static final int TIME_OFFSET = 5;
   private final Calendar CALENDAR = new GregorianCalendar();
   private static final TimeZone ZONE = TimeZone.getDefault();
   /**
    * User defined this year.
    */
   private static final int THIS_YEAR = 0;
   /**
    * User defined last year.
    */
   private static final int LAST_YEAR = 1;
   /**
    * User defined this quarter.
    */
   private static final int THIS_QUARTER = 0;
   /**
    * User defined last quarter.
    */
   private static final int LAST_QUARTER = 1;
   /**
    * User defined this month.
    */
   private static final int THIS_MONTH = 0;
   /**
    * User defined last month.
    */
   private static final int LAST_MONTH = 1;
   /**
    * User defined this week.
    */
   private static final int THIS_WEEK = 0;
   /**
    * User defined last week.
    */
   private static final int LAST_WEEK = 1;
   /**
    * User defined the month.
    */
   private static final int JANUARY = 0;
   private static final int FEBRUARY = 1;
   private static final int MARCH = 2;
   private static final int APRIL = 3;
   private static final int MAY = 4;
   private static final int JUNE = 5;
   private static final int JULY = 6;
   private static final int AUGUST = 7;
   private static final int SEPTEMBER = 8;
   private static final int OCTOBER = 9;
   private static final int NOVEMBER = 10;
   private static final int DECEMBER = 11;
}
