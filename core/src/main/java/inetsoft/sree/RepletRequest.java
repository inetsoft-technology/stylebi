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
package inetsoft.sree;

import inetsoft.sree.internal.HttpXMLSerializable;
import inetsoft.sree.schedule.ScheduleParameterScope;
import inetsoft.sree.web.*;
import inetsoft.uql.VariableTable;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.XUtil;
import inetsoft.util.*;
import inetsoft.util.script.ScriptEnv;
import inetsoft.util.script.ScriptException;
import inetsoft.web.composer.model.vs.DynamicValueModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.*;
import java.lang.reflect.Array;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The RepletRequest holds the report request parameter values. If is
 * created by the viewer to pass into a replet during report creation
 * or customization. A replet can access the parameter values by
 * calling one of the getter method.
 * <p>
 * Each request has a request name. It could not one of the pre-defined
 * request names, such as CREATE or CUSTOMIZE. It could also be a form
 * name if a request is submitted from a embedded form. It is up to the
 * replet to check for request name to take appropriate actions.
 * <p>
 * If replet is running in a web environment, the request and
 * response object can be retrieved using the special SERVICE_REQUEST and
 * SERVICE_RESPONSE parameter name.
 *
 * @version 3.0, 5/10/2000
 * @author InetSoft Technology Corp
 */
public class RepletRequest implements java.io.Serializable, Cloneable, HttpXMLSerializable {
   /**
    * The name for the ServiceRequest parameter. This parameter is
    * only available if the replet is running through one of the web clients.
    * @see inetsoft.sree.web.ServiceRequest
    */
   public static final String SERVICE_REQUEST = VariableTable.SERVICE_REQUEST;
   /**
    * The name for the ServiceResponse parameter. This parameter is
    * only available if the replet is running through one of the web clients.
    * @see inetsoft.sree.web.ServiceResponse
    */
   public static final String SERVICE_RESPONSE = VariableTable.SERVICE_RESPONSE;

   /**
    * Create an empty request.
    */
   public RepletRequest() {
      this(new Hashtable<>());
   }

   /**
    * Create a request from the values in a hastable.
    * @param pairs paramter values.
    */
   public RepletRequest(Map<String, Object> pairs) {
      params = new ConcurrentHashMap<>(pairs);
   }

   /**
    * Get the value of a parameter.
    * @param name parameter name.
    * @return parameter value.
    */
   public Object getParameter(String name) {
      return getInnerParamter(name);
   }

   /**
    * Get the value of a parameter.
    * @param name parameter name.
    * @param def default value if the parameter is null.
    * @return parameter value.
    */
   public Object getParameter(String name, Object def) {
      Object val = getInnerParamter(name);

      return (val == null) ? def : val;
   }

   /**
    * Get the value of a parameter as a boolean. If the value is not a
    * booleam object, it is converted to a boolean by assigning true
    * if the value is not null, false otherwise.
    * @param name name of the parameter.
    * @return boolean value.
    */
   public boolean getBoolean(String name) {
      Object obj = getParameter(name);

      return obj != null && (!(obj instanceof Boolean) || (Boolean) obj);
   }

   /**
    * Get the value of a parameter as a string. If the value is null,
    * an empty string is returned.
    * @param name parameter name.
    * @return value as string.
    */
   public String getString(String name) {
      Object obj = getParameter(name);

      return (obj == null) ? "" : obj.toString();
   }

   /**
    * Get the value of a parameter as an integer. If the value is
    * does not contain a value integer, an exception is thrown.
    * @param name parameter name.
    * @return value as integer.
    */
   public int getInt(String name) {
      Object obj = getParameter(name);

      if(obj instanceof Number) {
         return ((Number) obj).intValue();
      }

      try {
         return (obj == null) ? 0 : Integer.parseInt(obj.toString());
      }
      catch(Exception ex) {
         return 0;
      }
   }

   /**
    * Get the value of a parameter as a long. If the value is
    * does not contain a value integer, an exception is thrown.
    * @param name parameter name.
    * @return value as long.
    */
   public long getLong(String name) {
      Object obj = getParameter(name);

      if(obj instanceof Number) {
         return ((Number) obj).longValue();
      }

      try {
         return (obj == null) ? 0 : Long.parseLong(obj.toString());
      }
      catch(Exception ex) {
         return 0;
      }
   }

   /**
    * Get the value of a parameter as an double. If the value is
    * does not contain a value double, an exception is thrown.
    * @param name parameter name.
    * @return value as double.
    */
   public double getDouble(String name) {
      Object obj = getParameter(name);

      if(obj instanceof Number) {
         return ((Number) obj).doubleValue();
      }

      try {
         return (obj == null) ? 0.0 : Double.parseDouble(obj.toString());
      }
      catch(Exception ex) {
         return 0;
      }
   }

   /**
    * Get the parameter value as an array.
    * @param name parameter name.
    * @return an array. It could be null.
    */
   public Object[] getArray(String name) {
      Object obj = getParameter(name);

      if(obj == null) {
         return new Object[0];
      }

      if(obj instanceof String) {
         return Tool.split((String) obj, ',');
      }
      else if(obj.getClass().isArray()) {
         return (Object[]) obj;
      }
      else {
         return new Object[] {obj};
      }
   }

   /**
    * Get a parameter value as a date. If the value is not a Date type,
    * it is converted to a Date using the date format specified in the
    * configuration file.
    * @param name parameter name.
    * @return date or null if the data is in wrong format.
    */
   public Date getDate(String name) {
      Object obj = getParameter(name);

      if(obj instanceof Date) {
         return (Date) obj;
      }

      try {
         // @by jasonshobe, don't cache date formats, they are not thread safe
         return obj == null ? null : Tool.getDateFormat().parse(obj.toString());
      }
      catch(Throwable e) {
         return null;
      }
   }

   /**
    * Get a parameter value as a time. If the value is not a Date type,
    * it is converted to a Date using the date format specified in the
    * configuration file.
    * @param name parameter name.
    * @return date or null if the data is in wrong format.
    */
   public Date getTime(String name) {
      Object obj = getParameter(name);

      if(obj instanceof Date) {
         return (Date) obj;
      }

      try {
         // @by jasonshobe, don't cache date formats, they are not thread safe
         // @by jasonshobe, don't cache date formats, they are not thread safe
         return obj == null ? null : Tool.getTimeFormat(false).parse(obj.toString());
      }
      catch(Throwable e) {
         return null;
      }
   }

   /**
    * Get the number of parameters.
    */
   public int getParameterCount() {
      return params.size() + getInternalParameters().size();
   }

   /**
    * Get the names of all parameters
    */
   public Enumeration<String> getParameterNames() {
      final List<IteratorEnumeration<String>> enums =
         Arrays.asList(new IteratorEnumeration<>(params.keySet().iterator()),
                       new IteratorEnumeration<>(getInternalParameters().keySet().iterator()));

      return new EnumEnumeration<>(enums);
   }

   /**
    * Get the internal parameters which are kept in the session of the
    * ServiceRequest but not in the parameters' map.
    */
   private Map<String, Object> getInternalParameters() {
      HashMap<String, Object> iParams = new HashMap<>();
      Object obj = params.get(SERVICE_REQUEST);

      if(obj instanceof ServiceRequest) {
         ServiceRequest request = (ServiceRequest) obj;
         Enumeration e = request.getAttributeNames();

         while(e.hasMoreElements()) {
            String attrName = (String) e.nextElement();
            Object attribute = request.getAttribute(attrName);

            if(attribute != null && isInternalParameter(attrName) &&
               !attrName.startsWith("__private__"))
            {
               iParams.put(attrName, attribute);
            }
         }
      }

      return iParams;
   }

   /**
    * Check if a parameter is kept in the session of the ServiceRequest
    * parameter.
    */
   public boolean isInternalParameter(String name) {
      return !params.containsKey(name) && !name.startsWith("__private_") &&
         getParameter(name) != null;
   }

   /**
    * Set the value of a parameter.
    * @param name parameter name.
    * @param val parameter value.
    */
   public void setParameter(String name, Object val) {
      setParameter(name, val, null);
   }

   /**
    * Set the value of a parameter.
    * @param name parameter name.
    * @param val parameter value.
    * @param type the parameter data type.
    */
   public void setParameter(String name, Object val, String type) {
      synchronized(params) {
         Object obj = params.get(SERVICE_REQUEST);
         boolean contained = false;

         if(obj instanceof ServiceRequest) {
            ServiceRequest request = (ServiceRequest) obj;
            contained = request.getParameter(name) != null;
         }

         if(!contained && isInternalParameter(name)) {
            return;
         }

         if(name != null) {
            if(val == null) {
               val = NULL;
            }

            params.put(name, val);

            if(XSchema.TIME_INSTANT.equals(type)) {
               addDateTime(name);
            }
         }
      }
   }

   /**
    * Distinguish date and time instance type.
    * @param name parameter name.
    */
   public void addDateTime(String name) {
      dateTimeList.add(name);
   }

   /**
    * Set parameters from another replet request.
    * <p>
    * The method only accept not contained parameters.
    *
    * @param req the specified replet request
    */
   public void setParameters(RepletRequest req) {
     if(req == null) {
        return;
     }

     Enumeration<String> params = req.getParameterNames();

      while(params.hasMoreElements()) {
         String name = params.nextElement();

         // fix bug1257243399484, if the req contains a value with null object,
         // also replace the value to current request
         if(!req.isInternalParameter(name) &&
            (getParameter(name) == null || isInternalParameter(name)))
         {
            setParameter(name, req.getParameter(name));
         }
      }
   }

   /**
    * Remove parameters from another replet request.
    *
    * @param req the specified replet request
    */
   public void removeParameters(RepletRequest req) {
      Enumeration<String> params = req.getParameterNames();

      while(params.hasMoreElements()) {
         String name = params.nextElement();

         if(getParameter(name) != null && !isInternalParameter(name) &&
            req.getParameter(name) != null && !req.isInternalParameter(name))
         {
            remove(name);
         }
      }
   }

   /**
    * Check if the named value is set in the date time list.
    * @param name parameter name.
    */
   public boolean containsDateTime(String name) {
      return dateTimeList.contains(name);
   }

   /**
    * Remove a parameter from the request.
    * @param name parameter name.
    */
   public void remove(String name) {
      synchronized(params) {
         params.remove(name);
         dateTimeList.remove(name);
      }
   }

   /**
    * Remove parameters from the request.
    * @param names parameter names.
    */
   public void remove(List<String> names) {
      for(String name : names) {
         remove(name);
      }
   }

   /**
    * Check if the named value is set in the request.
    */
   public boolean contains(String name) {
      return params.containsKey(name) || isInternalParameter(name);
   }

   /**
    * Remove all existing parameters.
    */
   public void removeAll() {
      synchronized(params) {
         params.clear();
         dateTimeList.clear();
      }
   }

   /**
    * Set a hint. The interpretation of hints are implementation dependent
    * and are for internal use only.
    */
   public void setHint(String name, Object value) {
      if(hints == null) {
         hints = new Hashtable<>();
      }

      if(value == null) {
         hints.remove(name);
      }
      else {
         hints.put(name, value);
      }
   }

   /**
    * Get a hint on how to process a report.
    */
   public Object getHint(String name) {
      return (hints == null) ? null : hints.get(name);
   }

   /**
    * To string.
    */
   public String toString() {
      synchronized(params) {
         StringBuilder str = new StringBuilder("RepletRequest@" + hashCode() + ": ");

         for(String key : params.keySet()) {
            Object val = params.get(key);
            String type = (val == NULL) ? "null" : val.getClass().getName();

            str.append(key).append("=").append(val).append("(").append(type).append(") ");
         }

         str.append("]");
         return str.toString();
      }
   }

   /**
    * Compare two requests to see if they contain the same parameters.
    */
   public boolean equals(Object obj) {
      if(obj instanceof RepletRequest) {
         RepletRequest req = (RepletRequest) obj;
         return params.equals(req.params);
      }

      return false;
   }

   @Override
   public Object clone() {
      try {
         RepletRequest req = (RepletRequest) super.clone();

         synchronized(params) {
            req.params = new ConcurrentHashMap<>(params);
         }

         if(hints != null) {
            req.hints = (Hashtable<String, Object>) hints.clone();
         }

         return req;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone object", ex);
      }

      return null;
   }

   /**
    * Parse the XML element that contains info about this request.
    */
   @Override
   public void parseXML(Element req) throws Exception {
      NodeList paramlist = Tool.getChildNodesByTagName(req, "Parameter");

      for(int k = 0; k < paramlist.getLength(); k++) {
         Element param = (Element) paramlist.item(k);
         String pname = byteDecode(param.getAttribute("name"));
         Object val = getParameterValue(param);
         this.setParameter(pname, val);
      }
   }

   /**
    * Write the request to a xml file.
    * @param writer the PrintWriter
    */
   @Override
   public void writeXML(PrintWriter writer) {
      writer.println("<Request>");

      Enumeration<String> names = this.getParameterNames();

      while(names.hasMoreElements()) {
         String name = names.nextElement();

         // internal variable, ignore
         if(name.startsWith("__service_") || isInternalParameter(name)) {
            continue;
         }

         Object val = this.getParameter(name);
         writer.println(getParameterString(name, val));
      }

      writer.println("</Request>");
   }

   /**
    * Encode non-ascii characters to unicode enclosed in '[]'.
    * @param source source string.
    * @return encoded string.
    */
   @Override
   public String byteEncode(String source) {
      return encoding ? Tool.byteEncode2(source) : Tool.escape(source);
   }

   /**
    * Convert the encoded string to the original unencoded string.
    * @param encString a string encoded using the byteEncode method.
    * @return original string.
    */
   @Override
   public String byteDecode(String encString) {
      return encoding ? Tool.byteDecode(encString) : encString;
   }

   /**
    * Check if this object should encoded when writing.
    * @return <code>true</code> if should encoded, <code>false</code> otherwise.
    */
   @Override
   public boolean isEncoding() {
      return encoding;
   }

   /**
    * Set encoding flag.
    * @param encoding true to encode.
    */
   @Override
   public void setEncoding(boolean encoding) {
      this.encoding = encoding;
   }

   public String getParameterDataType(String name) {
      return getParameterDataType(name, false);
   }

   public String getParameterDataType(String name, boolean supportJs) {
      if(containsDateTime(name)) {
         return XSchema.TIME_INSTANT;
      }

      Object val = getParameter(name);

      return RepletRequest.getParameterValueDataType(val, supportJs);
   }

   /**
    * Get data type of parameter value.
    */
   public static String getParameterValueDataType(Object val) {
      return getParameterValueDataType(val, false);
   }

   /**
    * Get data type of parameter value.
    * @param val parameter value.
    * @param supportJs true: get first type when type is array.
    */
   public static String getParameterValueDataType(Object val, boolean supportJs) {
      String dataType = XSchema.STRING;

      if(val instanceof Boolean) {
         dataType = XSchema.BOOLEAN;
      }
      else if(val instanceof Double || val instanceof Float) {
         dataType = XSchema.DOUBLE;
      }
      else if(val instanceof Number) {
         dataType = XSchema.INTEGER;
      }
      else if(val instanceof java.sql.Time) {
         dataType = XSchema.TIME;
      }
      else if(val instanceof Date) {
         GregorianCalendar cal = new GregorianCalendar();
         cal.setTime((Date) val);

         if(cal.get(Calendar.YEAR) == 1970 && cal.get(Calendar.MONTH) == Calendar.JANUARY &&
            cal.get(Calendar.DAY_OF_MONTH) == 1)
         {
            dataType = XSchema.TIME;
         }
         else if(cal.get(Calendar.HOUR_OF_DAY) != 0 ||
            cal.get(Calendar.MINUTE) != 0 || cal.get(Calendar.SECOND) != 0)
         {
            dataType = XSchema.TIME_INSTANT;
         }
         else {
            dataType = XSchema.DATE;
         }
      }
      else if(val instanceof Object[]) {
         Object[] vals = (Object[]) val;

         if(supportJs && vals.length > 0) {
            return getParameterValueDataType(vals[0], supportJs);
         }

         StringBuilder buffer = new StringBuilder("new Array(");

         for(int i = 0; i < vals.length; i++) {
            buffer.append("'").append(getParameterValueDataType(vals[i])).append("'");

            if(i < vals.length - 1) {
               buffer.append(", ");
            }
         }

         buffer.append(")");
         dataType = buffer.toString();
      }
      // treat everything else as string
      else if(val != null) {
         dataType = XSchema.STRING;
      }

      return dataType;
   }

   /**
    * Get parameter value string.
    */
   public static String getParameterValueString(Object val) {
      String str = null;

      if(val instanceof Boolean || val instanceof Integer ||
         val instanceof Double)
      {
         str = val.toString();
      }
      else if(val instanceof java.sql.Time) {
         GregorianCalendar cal = new GregorianCalendar();
         cal.setTime((Date) val);
         str = timeFmt.format((Date) val);
      }
      else if(val instanceof Date) {
         GregorianCalendar cal = new GregorianCalendar();
         cal.setTime((Date) val);

         if(cal.get(Calendar.YEAR) == 1970 && cal.get(Calendar.MONTH) == Calendar.JANUARY &&
            cal.get(Calendar.DAY_OF_MONTH) == 1)
         {
            str = timeFmt.format((Date) val);
         }
         else if(cal.get(Calendar.HOUR_OF_DAY) != 0 ||
            cal.get(Calendar.MINUTE) != 0 || cal.get(Calendar.SECOND) != 0)
         {
            str = datetimeFmt.format((Date) val);
         }
         else {
            str = dateFmt.format((Date) val);
         }
      }
      else if(val instanceof Object[]) {
         StringBuilder buffer = new StringBuilder("new Array(");
         Object[] vals = (Object[]) val;

         for(int i = 0; i < vals.length; i++) {
            buffer.append("'").append(getParameterValueString(vals[i])).append("'");

            if(i < vals.length - 1) {
               buffer.append(", ");
            }
         }

         buffer.append(")");
         return buffer.toString();
      }
      // treat everything else as string
      else if(val != null) {
         str = val.toString();
      }

      return Tool.byteEncode(str);
   }

   private Object getSerializeValue(String name, Object val) {
      if(val instanceof java.sql.Time) {
         GregorianCalendar cal = new GregorianCalendar();
         cal.setTime((Date) val);
         val = timeFmt.format((Date) val);
      }
      else if(val instanceof Date) {
         GregorianCalendar cal = new GregorianCalendar();
         cal.setTime((Date) val);

         if(cal.get(Calendar.YEAR) == 1970 && cal.get(Calendar.MONTH) == Calendar.JANUARY &&
            cal.get(Calendar.DAY_OF_MONTH) == 1)
         {
            val = timeFmt.format((Date) val);
         }
         else if(dateTimeList.contains(name) || cal.get(Calendar.HOUR_OF_DAY) != 0 ||
            cal.get(Calendar.MINUTE) != 0 || cal.get(Calendar.SECOND) != 0)
         {
            val = datetimeFmt.format((Date) val);
         }
         else {
            val = dateFmt.format((Date) val);
         }
      }
      else {
         if(val == null) {
            val = "__NULL__";
         }
         else {
            val = Tool.byteEncode(String.valueOf(val));
         }
      }

      return val;
   }

   private Object getValueFormSerialize(String pvalue, String ptype) {
      if(ptype.equals(XSchema.STRING)) {
        return "__NULL__".equals(pvalue) ? null : Tool.byteDecode(pvalue);
      }
      else if(ptype.equals(XSchema.BOOLEAN)) {
         return Boolean.valueOf(pvalue);
      }
      else if(ptype.equals(XSchema.INTEGER)) {
         return Integer.valueOf(pvalue);
      }
      else if(ptype.equals(XSchema.DOUBLE)) {
         return Double.valueOf(pvalue);
      }
      else if(ptype.equals(XSchema.DATE)) {
         try {
            return dateFmt.parse(pvalue);
         }
         catch(Throwable e) {
            LOG.error("Failed to parse date: " + pvalue, e);
         }

         return null;
      }
      else if(ptype.equals(XSchema.TIME_INSTANT)) {
         try {
            return new Timestamp(datetimeFmt.parse(pvalue).getTime());
         }
         catch(Throwable e) {
            LOG.error("Failed to parse date/time:" + pvalue, e);
         }

         return null;
      }
      else if(ptype.equals(XSchema.TIME)) {
         try {
            return new java.sql.Time(timeFmt.parse(pvalue).getTime());
         }
         catch(Throwable e) {
            LOG.error("Failed to parse time: " + pvalue, e);
         }

         return null;
      }

      return null;
   }

   /**
    * Get parameter representative string.
    */
   private String getParameterString(String name, Object val) {
      StringBuilder buffer = new StringBuilder();
      buffer.append("<Parameter name=\"").append(byteEncode(name)).append("\" ");

      if(val instanceof DynamicParameterValue) {
         DynamicParameterValue dynamicParameterValue = (DynamicParameterValue) val;
         buffer.append(" dynamicType=\"" + dynamicParameterValue.getType() + "\" ");
         val = dynamicParameterValue.getValue();

         buffer.append("type=\"" + dynamicParameterValue.getDataType() + "\"");
         buffer.append(">");
         buffer.append("<value>");

         if(DynamicValueModel.VALUE.equals(dynamicParameterValue.getType())) {
            if(val instanceof Object[]) {
               buffer.append("<parameters>");
               Object[] arr = (Object[]) val;

               for(Object arrItem : arr) {
                  buffer.append(getParameterString(name, arrItem));
               }

               buffer.append("</parameters>");
            }
            else {
               buffer.append("<![CDATA[").append(getSerializeValue(name, val)).append("]]>");
            }
         }
         else {
            buffer.append("<![CDATA[").append(val).append("]]>");
         }

         buffer.append("</value>");
         buffer.append("</Parameter>");

         return buffer.toString();
      }

      if(val instanceof Boolean) {
         buffer.append("type=\"Boolean\">");
      }
      else if(val instanceof Integer) {
         buffer.append("type=\"Integer\">");
      }
      else if(val instanceof Double) {
         buffer.append("type=\"Double\">");
      }
      else if(val instanceof java.sql.Time) {
         GregorianCalendar cal = new GregorianCalendar();
         cal.setTime((Date) val);
         val = timeFmt.format((Date) val);

         buffer.append("type=\"Time\">");
      }
      else if(val instanceof Date) {
         GregorianCalendar cal = new GregorianCalendar();
         cal.setTime((Date) val);

         if(cal.get(Calendar.YEAR) == 1970 && cal.get(Calendar.MONTH) == Calendar.JANUARY &&
            cal.get(Calendar.DAY_OF_MONTH) == 1)
         {
            buffer.append("type=\"Time\">");
            val = timeFmt.format((Date) val);
         }
         else if(dateTimeList.contains(name) || cal.get(Calendar.HOUR_OF_DAY) != 0 ||
            cal.get(Calendar.MINUTE) != 0 || cal.get(Calendar.SECOND) != 0)
         {
            buffer.append("type=\"TimeInstant\">");
            val = datetimeFmt.format((Date) val);
         }
         else {
            buffer.append("type=\"Date\">");
            val = dateFmt.format((Date) val);
         }
      }
      else if(val instanceof Instant) {
         buffer.append("type=\"TimeInstant\">");
      }
      else if(val instanceof Object[]) {
         Object[] vals = (Object[]) val;
         buffer.append("type=\"Array\">");
         buffer.append("<parameters>");

         for(final Object v : vals) {
            buffer.append(getParameterString(name, v));
         }

         buffer.append("</parameters>");
         buffer.append("</Parameter>");
         return buffer.toString();
      }
      // treat everything else as string
      else {
         buffer.append("type=\"String\">");

         if(val == null) {
            val = "__NULL__";
         }
         else {
            val = Tool.byteEncode(String.valueOf(val));
         }
      }

      buffer.append("<value>");
      buffer.append("<![CDATA[").append(val).append("]]>");
      buffer.append("</value>");
      buffer.append("</Parameter>");

      return buffer.toString();
   }

   private Map<String, Object> getParameterMap(String name, Object val) {
      Map<String, Object> parameter = new HashMap<>();

      parameter.put("name", byteEncode(name));

      if(val instanceof Boolean) {
         parameter.put("type", "Boolean");
      }
      else if(val instanceof Integer) {
         parameter.put("type", "Integer");
      }
      else if(val instanceof Double) {
         parameter.put("type", "Double");
      }
      else if(val instanceof java.sql.Time) {
         GregorianCalendar cal = new GregorianCalendar();
         cal.setTime((Date) val);
         val = timeFmt.format((Date) val);

         parameter.put("type", "Time");
      }
      else if(val instanceof Date) {
         GregorianCalendar cal = new GregorianCalendar();
         cal.setTime((Date) val);

         if(cal.get(Calendar.YEAR) == 1970 && cal.get(Calendar.MONTH) == Calendar.JANUARY &&
            cal.get(Calendar.DAY_OF_MONTH) == 1)
         {
            parameter.put("type", "Time");
            val = timeFmt.format((Date) val);
         }
         else if(dateTimeList.contains(name) || cal.get(Calendar.HOUR_OF_DAY) != 0 ||
            cal.get(Calendar.MINUTE) != 0 || cal.get(Calendar.SECOND) != 0)
         {
            parameter.put("type", "TimeInstant");
            val = datetimeFmt.format((Date) val);
         }
         else {
            parameter.put("type", "Date");
            val = dateFmt.format((Date) val);
         }
      }
      else if(val instanceof Object[]) {
         Object[] vals = (Object[]) val;
         parameter.put("type", "Array");
         List<Object> parameters = new ArrayList<>();

         for(final Object v : vals) {
            parameters.add(getParameterMap(name, v));
         }

         parameter.put("parameters", parameters);
         return parameter;
      }
      // treat everything else as string
      else {
         parameter.put("type", "String");

         if(val == null) {
            val = "__NULL__";
         }
         else {
            val = Tool.byteEncode(String.valueOf(val));
         }
      }

      parameter.put("value", val.toString());

      return parameter;
   }

   /**
    * Get parameter value object.
    */
   private Object getParameterValue(Element param) {
      String pname = byteDecode(param.getAttribute("name"));
      String ptype = param.getAttribute("type");
      String pvalue = byteDecode(Tool.getAttribute(param, "value"));

      Element valueNode =
         Tool.getChildNodeByTagName(param, "value");

      if(valueNode != null) {
         pvalue = Tool.getValue(valueNode);
         pvalue = pvalue != null ? pvalue : "";
      }

      String dynamicType = param.getAttribute("dynamicType");
      DynamicParameterValue parameterValue = null;

      if(!Tool.isEmptyString(dynamicType)) {
         parameterValue = new DynamicParameterValue();
         parameterValue.setType(dynamicType);
         parameterValue.setDataType(ptype);

         Object value = pvalue;

         if(DynamicValueModel.VALUE.equalsIgnoreCase(dynamicType)) {
            Element valuesNode =
                    valueNode == null ? null : Tool.getChildNodeByTagName(valueNode, "parameters");

            if(valuesNode != null) {
               NodeList parameters = Tool.getChildNodesByTagName(valuesNode, "Parameter");
               Object[] array = new Object[parameters.getLength()];

               for(int x = 0; x < parameters.getLength(); x++) {
                  array[x] = getParameterValue((Element) parameters.item(x));
               }

               value = array;
            }
            else {
               value = getValueFormSerialize(pvalue, ptype);
            }
         }

         parameterValue.setValue(value);

         return parameterValue;
      }

      if(ptype.equals("String")) {
         String val = "__NULL__".equals(pvalue) ? null : Tool.byteDecode(pvalue);

         if(parameterValue != null) {
            parameterValue.setValue(val);

            return parameterValue;
         }

         return val;
      }
      else if(ptype.equals("Boolean")) {
         Boolean val = Boolean.valueOf(pvalue);

         if(parameterValue != null) {
            parameterValue.setValue(val);

            return parameterValue;
         }

         return val;
      }
      else if(ptype.equals("Integer")) {
         Integer val = Integer.valueOf(pvalue);

         if(parameterValue != null) {
            parameterValue.setValue(val);

            return parameterValue;
         }

         return val;
      }
      else if(ptype.equals("Double")) {
         Double val = Double.valueOf(pvalue);

         if(parameterValue != null) {
            parameterValue.setValue(val);

            return parameterValue;
         }

         return val;
      }
      else if(ptype.equals("Date")) {
         try {
            Date val = dateFmt.parse(pvalue);

            if(parameterValue != null) {
               parameterValue.setValue(val);

               return parameterValue;
            }

            return val;
         }
         catch(Throwable e) {
            LOG.error("Failed to parse date: " + pvalue, e);
         }

         return null;
      }
      else if(ptype.equals("TimeInstant")) {
         try {
            Timestamp val = new Timestamp(datetimeFmt.parse(pvalue).getTime());
            dateTimeList.add(pname);

            if(parameterValue != null) {
               parameterValue.setValue(val);

               return parameterValue;
            }

            return val;
         }
         catch(Throwable e) {
            LOG.error("Failed to parse date/time:" + pvalue, e);
         }

         return null;
      }
      else if(ptype.equals("Time")) {
         try {
            java.sql.Time val = new java.sql.Time(timeFmt.parse(pvalue).getTime());

            if(parameterValue != null) {
               parameterValue.setValue(val);

               return parameterValue;
            }

            return val;
         }
         catch(Throwable e) {
            LOG.error("Failed to parse time: " + pvalue, e);
         }

         return null;
      }
      else if(ptype.equals("Array")) {
         Element parametersElem =
            Tool.getChildNodeByTagName(param, "parameters");

         if(parametersElem == null) {
            return null;
         }

         NodeList parameters = Tool.getChildNodesByTagName(parametersElem, "Parameter");
         Object[] array = new Object[parameters.getLength()];

         for(int x = 0; x < parameters.getLength(); x++) {
            array[x] = getParameterValue((Element) parameters.item(x));
         }

         return array;
      }

      return null;
   }

   /**
    * get value from params hashtable, it will check the key of
    * HTTP_REQUEST, HTTP_RESPONSE for Backward compatibility problem.
    * (bug1053999007156)
    */
   private Object getInnerParamter(String name) {
      if(name == null) {
         return null;
      }

      if(name.equals(SERVICE_REQUEST)) {
         Object obj = params.get(name);

         if(obj instanceof ServiceRequestWrapper) {
            obj = ((ServiceRequestWrapper) obj).getRequest();
         }

         return obj;
      }

      if(params.get(name) == NULL) {
         Object obj = params.get(SERVICE_REQUEST);

         if(obj instanceof ServiceRequest) {
            return ((ServiceRequest) obj).getAttribute(name);
         }

         return null;
      }

      return params.get(name);
   }

   private void readObject(java.io.ObjectInputStream s)
      throws ClassNotFoundException, java.io.IOException {
      s.defaultReadObject();
   }

   private void writeObject(ObjectOutputStream stream) throws IOException {
      // http request and response are not serializable
      synchronized(params) {
         params.remove(SERVICE_REQUEST);
         params.remove(SERVICE_RESPONSE);
         Iterator<String> parameterNames = params.keySet().iterator();

         while(parameterNames.hasNext()) {
            String name = parameterNames.next();

            if(params.get(name) == NULL) {
               params.remove(name);
            }
         }
      }

      stream.defaultWriteObject();
   }

   /**
    * Execute the dynamic parameter and convert result to a value.
    */
   public void executeParameter() {
      ScheduleParameterScope scope = new ScheduleParameterScope();
      Enumeration<String> parameterNames = getParameterNames();
      ScriptEnv senv = scope.getScriptEnv();
      senv.addTopLevelParentScope(scope);

      while(parameterNames.hasMoreElements()) {
         String paramName = parameterNames.nextElement();
         Object parameter = getParameter(paramName);

         if(parameter instanceof DynamicParameterValue) {
            setParameter(paramName, executeParameter(((DynamicParameterValue) parameter), scope));
         }
      }
   }

   public static Object executeParameter(DynamicParameterValue parameter, ScheduleParameterScope scope) {
      if(DynamicValueModel.EXPRESSION.equals(parameter.getType())) {
         String exp = (String) parameter.getValue();
         Object val = executeScript(scope, exp.substring(1).trim());
         Object data = convertScriptResultToValue(parameter.getDataType(), val);

         return data;
      }

      return parameter.getValue();
   }

   private static Object convertScriptResultToValue(String type, Object val) {
      if(val != null && val.getClass().isArray()) {
         Object[] valuesArray = new Object[Array.getLength(val)];

         for(int i = 0; i < valuesArray.length; i++) {
            valuesArray[i] = convertScriptResultToValue(type, Array.get(val, i));
         }

         return valuesArray;
      }

      return Tool.getData(type, val);
   }

   private static Object executeScript(ScheduleParameterScope scope, String cmd) {
      if(cmd == null || cmd.trim().length() == 0) {
         return null;
      }

      ScriptEnv senv = scope.getScriptEnv();
      Object val;

      try {
         val = senv.exec(senv.compile(cmd), scope, null, null);
      }
      catch(Exception ex) {
         String suggestion = senv.getSuggestion(ex, null, scope);
         String msg = "Script error: " + ex.getMessage() +
            (suggestion != null ? "\nTo fix: " + suggestion : "") +
            "\nScript failed:\n" + XUtil.numbering(cmd);

         if(LOG.isDebugEnabled()) {
            LOG.debug(msg, ex);
         }
         else {
            LOG.warn(msg);
         }

         throw new ScriptException(msg);
      }

      return val;
   }

   private Map<String, Object> params;
   private Vector<String> dateTimeList = new Vector<>(); // try to keep time instance type
   private transient Hashtable<String, Object> hints;
   private boolean encoding = false;
   private static final Object NULL = new Object();

   /**
    * The pattern used to format and parse date values.
    */
   public static final String DATE_FORMAT = "yyyy-MM-dd";

   /**
    * The pattern used to format and parse time values.
    */
   public static final String TIME_FORMAT = "HH:mm:ss";

   /**
    * The pattern used to format and parse date/time values.
    */
   public static final String DATE_TIME_FORMAT = "yyyy-MM-dd HH:mm:ss";

   private static final SimpleDateFormat dateFmt = Tool.createDateFormat(DATE_FORMAT);
   private static final SimpleDateFormat timeFmt = Tool.createDateFormat(TIME_FORMAT);
   private static final SimpleDateFormat datetimeFmt = Tool.createDateFormat(DATE_TIME_FORMAT);
   private static final Logger LOG = LoggerFactory.getLogger(RepletRequest.class);
}
