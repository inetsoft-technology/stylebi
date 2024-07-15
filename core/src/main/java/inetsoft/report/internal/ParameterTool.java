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
package inetsoft.report.internal;

import inetsoft.report.ReportSheet;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.VariableTable;
import inetsoft.util.*;

import java.text.NumberFormat;
import java.util.*;

/**
 * Utility methods operating on the inetsoft.report packages.
 *
 * @version 13.5, 10/26/2021
 * @author InetSoft Technology Corp
 */
public class ParameterTool {
   public ParameterTool() {
   }

   /**
    * Check if the text has parameter. We using these formats for parameters:
    *     1.   {parameter.var}   // no format, return original data to string.
    *     2.   {parameter.var, number, '$#,###M'}
    *     3.   {parameter.var, text, 'aaaaa'}
    *     4.   {parameter.var, date, 'MM/d/yy'}
    *     5.   {parameter.var, percent}
    *     6.   {parameter.var, currency}
    */
   public boolean containsParameter(String text) {
      if(text == null || !text.contains(TEXT_PARAMETER_PREFIX) ||
         !text.contains(TEXT_PARAMETER_END))
      {
         return false;
      }

      int start = text.indexOf(TEXT_PARAMETER_PREFIX);
      int end = text.indexOf(TEXT_PARAMETER_END);

      return end > start + 10;
   }

   /**
    *  Parse parameter to right values.
    */
   public String parseParameters(VariableTable table, String text) {
      String ntext = text;

      while(containsParameter(ntext)) {
         ntext = parseParameter(table, ntext);
      }

      return ntext;
   }

   public List<String> getParameters(RuntimeViewsheet rvs) {
      List<String> params = new ArrayList<>();
      params.add("_GROUPS_");
      params.add("_ROLES_");
      params.add("_USER_");
      params.add("__principal__");

      VariableTable vtable = rvs.getViewsheetSandbox().getVariableTable();
      Enumeration<String> iter = vtable.keys();

      while(iter.hasMoreElements()) {
         String key = iter.nextElement();

         if(!params.contains(key)) {
            params.add(key);
         }
      }

      return params;
   }

   /**
    * Get parameters from report. The parameters should sort as name and the
    * group/role/user/principal will be on the top. Such as:
    *  _GROUPS_
    *  _ROLES_
    *  _USER_
    *  __principal__
    *  aa
    *  bb
    *  cc
    */
   public Vector getParameters(ReportSheet report) {
      Vector<String> params = new Vector<String>();

      if(report != null) {
         if(!params.contains("__principal__")) {
            params.add(0, "__principal__");
         }

         if(!params.contains("_USER_")) {
            params.add(0,"_USER_");
         }

         if(!params.contains("_ROLES_")) {
            params.add(0, "_ROLES_");
         }

         if(!params.contains("_GROUPS_")) {
            params.add(0, "_GROUPS_");
         }
      }

      return params;
   }

   private String parseParameter(VariableTable table, String text) {
      StringBuilder buffer = new StringBuilder();
      int start = text.indexOf(TEXT_PARAMETER_PREFIX);

      if(start < 0) {
         return text;
      }

      buffer.append(text.substring(0, start));

      int end = text.indexOf("}");
      int specIndex = text.indexOf("'");

      // For example, if like this: {parameter.var, text, '{0}_aa'}.
      if(specIndex > start && specIndex < end) {
         end = text.indexOf("'}") + 1;
      }

      if(end == -1) {
         return text;
      }

      buffer.append(getParameterString(table, text.substring(start + 1, end)));

      if(end < text.length() - 1) {
         buffer.append(text.substring(end + 1));
      }

      return buffer.toString();
   }

   private String getParameterString(VariableTable table, String text) {
      if(text == null || text.length() == 0) {
         return text;
      }

      String[] strs = Tool.split(text, ',');

      if(strs.length < 1 || !strs[0].startsWith(PARAMETER_PREFIX)) {
         return text;
      }

      String paraName = strs[0].trim();
      paraName = paraName.replace(PARAMETER_PREFIX, "");
      Object paraValue = null;

      try {
         paraValue = table.get(paraName);
      }
      catch(Exception ignore) {
         // If do not get variable value, ignore it and return ""
      }

      String fmt = null;
      String spec = null;

      try {
         if(paraValue != null && strs.length >= 2) {
            fmt = strs[1].trim();

            if(text.contains("'") && text.lastIndexOf("'") > text.indexOf("'")) {
               spec = text.substring(text.indexOf("'") + 1, text.lastIndexOf("'"));

               if(!(paraValue instanceof Object[])) {
                  return formatParameterValue(paraValue, fmt, spec);
               }
            }
            else if(PARAMETER_CURRENCY_FORMAT.equals(fmt) || PARAMETER_PERCENT_FORMAT.equals(fmt)) {
               if(!(paraValue instanceof Object[])) {
                  return formatParameterValue(paraValue, fmt, null);
               }
            }
         }
      }
      catch(Exception ignore) {
         // format error, ignore format.
      }


      if(paraValue instanceof Object[]) {
         StringBuilder buffer = new StringBuilder();
         Object[] values = (Object[]) paraValue;
         buffer.append("[");

         for(int i = 0; i < values.length; i++) {
            String val = values[i].toString();

            try {
               val = fmt == null ? val :
                  formatParameterValue(values[i], fmt, spec);
            }
            catch(Exception ignore) {
               // format error, ignore format.
            }

            if(i < values.length - 1) {
               buffer.append(val + ",");
            }
            else {
               buffer.append(val);
            }
         }

         buffer.append("]");

         return buffer.toString();
      }


      return paraValue == null ? "null" : paraValue.toString();
   }

   private String formatParameterValue(Object para, String fmt, String spec) {
      String valueString = para.toString();

      if(PARAMETER_TEXT_FORMAT.equals(fmt)) {
         MessageFormat format = new MessageFormat(spec);

         return format.format(valueString);
      }
      else if(PARAMETER_DATE_FORMAT.equals(fmt)) {
         ExtendedDateFormat format = new ExtendedDateFormat(spec);

         if(para instanceof Date) {
            return format.format(para);
         }
      }
      else if(isNumberFormat(fmt)) {
         double value = Double.NaN;

         try {
            value = Double.valueOf(valueString);
         }
         catch(Exception ex) {
            // if failed, just return a string to avoid exception
            return para.toString();
         }

         if(!Double.isNaN(value)) {
            return getNumberFormatValue(fmt, spec, value);
         }
      }

      return valueString;
   }

   private boolean isNumberFormat(String fmt) {
      return PARAMETER_NUMBER_FORMAT.equals(fmt) || PARAMETER_CURRENCY_FORMAT.equals(fmt) ||
         PARAMETER_PERCENT_FORMAT.equals(fmt);
   }

   private String getNumberFormatValue(String fmt, String spec, double value) {
      NumberFormat format = null;

      if(PARAMETER_NUMBER_FORMAT.equals(fmt)) {
         format = new ExtendedDecimalFormat(spec);
      }
      else if(PARAMETER_CURRENCY_FORMAT.equals(fmt)) {
         format = NumberFormat.getCurrencyInstance();
      }
      else if(PARAMETER_PERCENT_FORMAT.equals(fmt)) {
         format = NumberFormat.getPercentInstance();
      }

      return format.format(value);
   }

   private static final String TEXT_PARAMETER_PREFIX = "{parameter.";
   private static final String TEXT_PARAMETER_END = "}";
   private static final String PARAMETER_PREFIX = "parameter.";
   private static final String PARAMETER_NUMBER_FORMAT = "number";
   private static final String PARAMETER_TEXT_FORMAT = "text";
   private static final String PARAMETER_DATE_FORMAT = "date";
   private static final String PARAMETER_CURRENCY_FORMAT = "currency";
   private static final String PARAMETER_PERCENT_FORMAT = "percent";
}