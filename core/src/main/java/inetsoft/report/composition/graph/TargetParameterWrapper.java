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
package inetsoft.report.composition.graph;

import inetsoft.graph.guide.form.TargetParameter;
import inetsoft.report.filter.Formula;
import inetsoft.uql.viewsheet.DynamicValue;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.awt.*;
import java.io.Serializable;
import java.lang.reflect.Array;
import java.util.List;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Data transport for target aggregate parameters (AVG, SUM, MAX, MIN, etc...)
 */
public class TargetParameterWrapper implements Cloneable, Serializable {
   /**
    * Convenience constructor with DynamicValue argument
    */
   public TargetParameterWrapper(DynamicValue constValue) {
      this(null, constValue);
   }

   /**
    * Convenience constructor with string argument
    */
   public TargetParameterWrapper(String value) {
      this(null, new DynamicValue(value));
   }

   /**
    * Convenience constructor for non constants
    */
   public TargetParameterWrapper(Formula formula) {
      this(formula, new DynamicValue());
   }

   /**
    * Create a new TargetParameter
    */
   public TargetParameterWrapper(Formula formula, DynamicValue constValue)
   {
      setConstantValue(constValue);
      setFormula(formula);
   }

   /**
    * Convenience empty constructor.  Makes a pass-through with default value
    */
   public TargetParameterWrapper() {
      this(null, new DynamicValue("0"));
   }

   // Get the runtime parameter
   public TargetParameter[] unwrap() {
      List<TargetParameter> list = new ArrayList<>();

      // if the target parameter is dynamic, it could be a formula or a
      // value, we check if it's a formula first
      if(VSUtil.isDynamicValue(constValue.getDValue())) {
         Object rval = constValue.getRuntimeValue(true);
         Formula formula2 = createFormula(rval + "");

         if(formula2 != null) {
            return new TargetParameter[] {new TargetParameter(formula2, 0)};
         }
      }

      // if formula is defined, don't use the const value
      if(formula != null) {
         list.add(new TargetParameter(formula, 0.0));
      }
      else if(isTimeField()) {
         list.addAll(convertTime(formula, constValue));
      }
      else if(isDateField()) {
         list.addAll(convertDate(formula, constValue));
      }
      else {
         List<Double> rval = TargetStrategyWrapper.
            runtimeValueOf(constValue, false);

         for(Double val : rval) {
            list.add(new TargetParameter(formula, val));
         }
      }

      return list.toArray(new TargetParameter[list.size()]);
   }

   /**
    * Parse a date string in the format "yyyy-MM-dd HH:mm:ss".  Includes
    * support for partial matches.
    * @param dateStr The string to be parsed.
    * @return a Date object representing the parsed date.
    */
   public static Date parseDate(String dateStr, boolean showError,
                                Component errorParent) {
      Pattern pattern = Pattern.compile(DATE_REGEX);
      Matcher matcher = pattern.matcher(dateStr);
      Calendar cal = new GregorianCalendar();
      String[] matches = new String[matcherGroups.length];
      Integer[] parsedMatches = new Integer[matcherGroups.length];

      cal.set(Calendar.MONTH, Calendar.JANUARY);
      cal.set(Calendar.DAY_OF_MONTH, 1);
      cal.set(Calendar.HOUR_OF_DAY, 0);
      cal.set(Calendar.MINUTE, 0);
      cal.set(Calendar.SECOND, 0);

      if(matcher.matches()) {
         // Get matches for the groups
         for(int i = 0; i < matcherGroups.length; i++) {
            matches[i] = matcher.group(matcherGroups[i]);
         }
      }
      else {
         return null;
      }

      // Try to parse each regex match
      for(int i = 0; i < matches.length; i++) {
         try {
            parsedMatches[i] = matches[i] == null ? null :
               Integer.parseInt(matches[i]) - calendarAdjusts[i];
         }
         catch(NumberFormatException e) {
            if(!matches[i].isEmpty()) {
               return null;
            }

            break;
         }
      }

      // Assign calendar values
      for(int i = 0; i < calendarConstants.length; i++) {
         if(parsedMatches[i] != null) {
            cal.set(calendarConstants[i], parsedMatches[i]);
         }
      }

      return cal.getTime();
   }

   /**
    * Convert a date parameter into a double for usage in the chart engine
    */
   private List<TargetParameter> convertDate(Formula formula,
                                             DynamicValue value)
   {
      Object rval = value.getRuntimeValue(false);
      List<TargetParameter> list = new ArrayList<>();

      if(rval == null) {
         return list;
      }
      else if(rval.getClass().isArray()) {
         for(int i = 0; i < Array.getLength(rval); i++) {
            list.add(convertDate0(formula, Array.get(rval, i)));
         }
      }
      else {
         list.add(convertDate0(formula, rval));
      }

      return list;
   }

   /**
    * Convert a value to a parameter.
    */
   private TargetParameter convertDate0(Formula formula, Object raw) {
      String stringVal = Tool.getStringData(raw);
      Date date;

      // If it's already a date, fine.  Otherwise parse
      if(raw instanceof Date) {
         date = (Date) raw;
      }
      else {
         date = parseDate(stringVal, false, null);
      }

      double timeValue = date == null ? 0 : date.getTime();
      return new TargetParameter(formula, timeValue);
   }

   /**
    * Convert a time parameter into a double for usage in the chart engine
    */
   private List<TargetParameter> convertTime(Formula formula,
                                            DynamicValue value)
   {
      Object rval = value.getRuntimeValue(false);
      List<TargetParameter> list = new ArrayList<>();

      if(rval == null) {
         return list;
      }
      else if(rval.getClass().isArray()) {
         for(int i = 0; i < Array.getLength(rval); i++) {
            list.add(convertTime0(formula, Array.get(rval, i)));
         }
      }
      else {
         list.add(convertTime0(formula, rval));
      }

      return list;
   }

   /**
    * Convert a value to a parameter.
    */
   private TargetParameter convertTime0(Formula formula, Object raw) {
      String stringVal = Tool.getStringData(raw);
      Double timeValue = null;

      // If it's already a date, fine.  Otherwise parse
      if(raw instanceof Date) {
         timeValue = (double) ((Date) raw).getTime();
      }
      else {
         timeValue = parseTime(stringVal);
      }

      return new TargetParameter(formula, timeValue == null ? 0 : timeValue);
   }

   public static Double parseTime(String timeString) {
      Double timeValue = null;
      Pattern pattern = Pattern.compile(TIME_REGEX);
      Matcher matcher = pattern.matcher(timeString);

      if(matcher.matches()) {
         int hour = Integer.parseInt(matcher.group(1));
         int minute = 0;
         int second = 0;

         if(matcher.group(2) != null) {
            minute = Integer.parseInt(matcher.group(2).substring(1));
         }

         if(matcher.group(3) != null) {
            second = Integer.parseInt(matcher.group(3).substring(1));
         }

         // calculate time in millis since epoch
         Calendar cal = new GregorianCalendar();
         cal.set(Calendar.YEAR, 1970);
         cal.set(Calendar.MONTH, Calendar.JANUARY);
         cal.set(Calendar.DAY_OF_MONTH, 1);
         cal.set(Calendar.HOUR_OF_DAY, hour);
         cal.set(Calendar.MINUTE, minute);
         cal.set(Calendar.SECOND, second);
         cal.set(Calendar.MILLISECOND, 0);

         timeValue = (double) cal.getTimeInMillis();
      }

      return timeValue;
   }

   /**
    * @return true if this is a constant parameter
    */
   public boolean isConstant() {
      return this.formula == null;
   }

   /**
    * Return true if describing a date field
    */
   public boolean isDateField() {
      return this.isDateField;
   }

   /**
    * Set wether this is describing a date field
    */
   public void setDateField(boolean value) {
      this.isDateField = value;
   }

   public boolean isTimeField() {
      return isTimeField;
   }

   public void setTimeField(boolean timeField) {
      isTimeField = timeField;
   }

   /**
    * @return the formula used to calculate the runtime value of the parameter
    */
   public Formula getFormula() {
      return formula;
   }

   /**
    * Get the value of the parameter, generally only useful for constant
    * @return constant parameter value
    */
   public DynamicValue getConstantValue() {
      return constValue;
   }

   /**
    * Set the value of the parameter
    */
   public void setConstantValue(DynamicValue newValue) {
      this.constValue = newValue;
   }

   /**
    * Set the value of the parameter
    */
   public void setConstantValue(String newValue) {
      if(constValue == null) {
         constValue = new DynamicValue("0");
      }

      this.constValue.setDValue(newValue);
   }

   /**
    * Return a description of the value
    */
   public String toString() {
      String ret;

      // Use formula if exists, otherwise Dvalue
      if(formula != null) {
         ret = formula.getDisplayName();
      }
      else {
         ret = constValue.getDValue();

         if(ret != null && !ret.isEmpty() && ret.charAt(0) == '=') {
            ret = ret.substring(1, ret.length());
         }
      }

      if(ret != null) {
         // Trim if too long
         if(ret.length() > 20) {
            ret = ret.substring(0, 17) + "...";
         }
      }

      ret = ret == null ? "" : ret;
      return ret;
   }

   public String getXml() {
      StringBuilder sb = new StringBuilder();
      sb.append("<parameter ");

      // Write formula portion
      if(formula != null) {
         String s = "formulaClass=\"" +
            Tool.encodeHTMLAttribute(formula.getClass().getName()) + "\" ";
         sb.append(s);
      }

      // Write dynamic value portion
      if(constValue != null) {
         sb.append("dynValue=\"")
            .append(Tool.encodeHTMLAttribute(constValue.getDValue())).append("\" ");
      }

      if(isDateField) {
         sb.append("dateField=\"true\" ");
      }

      if(isTimeField) {
         sb.append("timeField=\"true\" ");
      }

      sb.append("/>");
      return sb.toString();
   }

   public boolean parseXml(Element element) {
      String className = element.getAttribute("formulaClass");

      // Instantiate formula class from class name attribute
      if(!className.isEmpty()) {
         formula = createFormula(className);
      }
      else {
         formula = null;
      }

      // Extract dynamic value
      String dVal = element.getAttribute("dynValue");

      // Value is essential, type is not
      if(!dVal.isEmpty()) {
         constValue = new DynamicValue(dVal);
      }

      String dateField = element.getAttribute("dateField");
      this.setDateField(dateField.equals("true"));

      String timeField = element.getAttribute("timeField");
      this.setTimeField(timeField.equals("true"));

      // Must have at least a dynamic value OR a formula
      return constValue != null || formula != null;
   }

   public Map<String, Object> getDTO() {
      Map<String, Object> dtoContents = new HashMap<>();

      if(formula != null) {
         dtoContents.put("formulaClass",
                         Tool.encodeHTMLAttribute(formula.getClass().getName()));
      }

      if(constValue != null) {
         dtoContents.put("dynValue",
                         Tool.encodeHTMLAttribute(constValue.getDValue()));
      }

      if(isDateField){
         dtoContents.put("dateField", true);
      }

      return dtoContents;
   }

   public boolean readDTO(Map<String, Object> valueDTO) {
      if(valueDTO.get("formulaClass") != null) {
         String className = (String) valueDTO.get("formulaClass");

         if(!className.isEmpty()) {
            formula = createFormula(className);
         }
         else {
            formula = null;
         }
      }
      else {
         formula = null;
      }

      if(valueDTO.get("dynValue") != null) {
         constValue = new DynamicValue((String) valueDTO.get("dynValue"));
      }

      if(valueDTO.get("dateField") != null) {
         this.setDateField((Boolean) valueDTO.get("dateField"));
      }
      else {
         this.setDateField(false);
      }

      return constValue != null || formula != null;
   }

   /**
    * Try to create a formula.
    */
   private Formula createFormula(String cls) {
      if(cls.indexOf('.') < 0) {
         cls = "inetsoft.report.filter." + cls;

         if(!cls.endsWith("Formula")) {
            cls += "Formula";
         }
      }

      try {
         Class clsObj = Class.forName(cls);
         return (Formula) clsObj.newInstance();
      }
      catch(Throwable ex) {
         return null;
      }
   }

   /**
    * Create a deep copy
    */
   @Override
   public Object clone() {
      DynamicValue dv = getConstantValue();
      Formula formula = getFormula();
      DynamicValue newDv = dv == null ? null : (DynamicValue) dv.clone();
      Formula newFormula = formula == null ? null : (Formula) formula.clone();

      return new TargetParameterWrapper(newFormula, newDv);
   }

   /**
    * Set the formula for calculating the runtime value of the parameter
    * @param formula the formula to use
    */
   public void setFormula(Formula formula) {
      this.formula = formula;
   }

   private Formula formula; // Parameter type

   // For constants, this represents the runtime value.
   private DynamicValue constValue; // Parameter value
   private boolean isDateField = false;
   private boolean isTimeField = false;
   // match yyyy-MM-dd HH:mm:ss.fff, but fff will not be in calculation
   // fix bug1404135923755
   private static final String DATE_REGEX =
      "^(.{4})(-(.{1,2})(-(.{1,2})( (.{1,2})(:(.{1,2}))?(:(.{1,2})([:\\.](.{1,3}))?)?)?)?)?$";
   private static final String TIME_REGEX =
      "^(2[0-3]|[01]?[0-9])(:[0-5]?[0-9])?(:[0-5]?[0-9])?$";

   /*
   This regex actually matches only digits, but in order to catch errors,
   it is nice to match on all characters as above.
   "^(\\d{4})(-(\\d{2})(-(\\d{2})( (\\d{2})(:(\\d{2}))?(:(\\d{2}))?)?)?)?$";
   */

   public static final String DATE_FORMAT_STR = "yyyy-MM-dd HH:mm:ss";
   public static final String TIME_FORMAT_STR = "HH:mm:ss";
   private static int[] matcherGroups = {1, 3, 5, 7, 9, 11};
   private static int[] calendarConstants = {
      Calendar.YEAR, Calendar.MONTH, Calendar.DAY_OF_MONTH,
      Calendar.HOUR_OF_DAY, Calendar.MINUTE, Calendar.SECOND};
   private static int[] calendarAdjusts = {0, 1, 0, 0, 0, 0};
   private static final Logger LOG = LoggerFactory.getLogger(TargetParameterWrapper.class);
}
