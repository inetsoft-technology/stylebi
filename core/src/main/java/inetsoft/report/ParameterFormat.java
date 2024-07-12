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
package inetsoft.report;

import inetsoft.report.filter.CrossTabFilter;
import inetsoft.report.internal.Util;
import inetsoft.report.internal.table.*;
import inetsoft.report.lens.HeaderRowTableLens;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.*;
import java.util.*;

/**
 * ParameterFormat is used to format hyperlink tooltip.
 *
 * @version 9.1
 * @author InetSoft Technology Corp
 */
public class ParameterFormat extends Format {
   /**
    * Constructor.
    */
   public ParameterFormat() {
      super();
   }

   /**
    * Formats an object to produce a string.
    * @param obj the object to format.
    * @param map a map contains key-value pairs.
    * @return formatted string.
    */
   public String format(Object obj, Map map) {
      try {
         return formatObj(obj, map);
      }
      catch(RuntimeException re) {
         LOG.warn("Failed to format value: " + obj, re);
         return obj.toString();
      }
   }

   /**
    * Formats an object to produce a string.
    * @param obj the object to format.
    * @param table table lens containing real table data.
    * @param r table row.
    * @param c table column.
    * @return formatted string.
    */
   public String format(Object obj, TableLens table, int r, int c) {
      if(obj == null || obj.equals("")) {
         return null;
      }

      Map map = new HashMap();
      int type = table.getDescriptor().getType();

      if(type == TableDataDescriptor.CROSSTAB_TABLE) {
         CrossTabFilter crosstab = Util.getCrosstab(table);
         int r0 = TableTool.getBaseRowIndex(table, crosstab, r);

         if(r0 < 0) {
            r0 = 0;
         }

         crosstab.getKeyValuePairs(r0, c, map);
      }
      else {
         String[] params = TableHyperlinkAttr.getAvailableParameters(table, r, c);

         if(type == TableDataDescriptor.CALC_TABLE) {
            RuntimeCalcTableLens calc = (RuntimeCalcTableLens)
               Util.getNestedTable(table, RuntimeCalcTableLens.class);

            if(calc != null) {
               map = calc.getKeyValuePairs(r, c, null);

               if(map.size() == 0) {
                  map = getKeyValuePairs(calc, r, params);
               }
            }
         }
         else {
            TableLens lens = table;
            int r0 = r;

            // @by larryl, the column header in freehand and header row tables
            // are meaning less for hyperlink, should get the real header
            while(lens instanceof HeaderRowTableLens) {
               TableFilter filter = (TableFilter) lens;
               r0 = filter.getBaseRowIndex(r0);
               lens = filter.getTable();
            }

            map = getKeyValuePairs(lens, r0, params);
         }
      }

      if(r >= 0 && c >= 0) {
         map.put(StyleConstants.COLUMN, table.getObject(r, c));
      }

      return format(obj, map);
   }

   /**
   * Formats an object and appends the resulting text to a given string
   * buffer.
   * If the <code>pos</code> argument identifies a field used by the format,
   * then its indices are set to the beginning and end of the first such
   * field encountered.
   *
   * @param obj    The object to format
   * @param toAppendTo    where the text is to be appended
   * @param pos    A <code>FieldPosition</code> identifying a field
   *               in the formatted text
   * @return       the string buffer passed in as <code>toAppendTo</code>,
   *               with formatted text appended
   * @exception NullPointerException if <code>toAppendTo</code> or
   *            <code>pos</code> is null
   * @exception IllegalArgumentException if the Format cannot format the given
   *            object
   */
   @Override
   public StringBuffer format(Object obj, StringBuffer toAppendTo,
                              FieldPosition pos)
   {
      return null;
   }

   /**
   * Parses text from a string to produce an object.
   * <p>
   * The method attempts to parse text starting at the index given by
   * <code>pos</code>.
   * If parsing succeeds, then the index of <code>pos</code> is updated
   * to the index after the last character used (parsing does not necessarily
   * use all characters up to the end of the string), and the parsed
   * object is returned. The updated <code>pos</code> can be used to
   * indicate the starting point for the next call to this method.
   * If an error occurs, then the index of <code>pos</code> is not
   * changed, the error index of <code>pos</code> is set to the index of
   * the character where the error occurred, and null is returned.
   *
   * @param source A <code>String</code>, part of which should be parsed.
   * @param pos A <code>ParsePosition</code> object with index and error
   *            index information as described above.
   * @return An <code>Object</code> parsed from the string. In case of
   *         error, returns null.
   * @exception NullPointerException if <code>pos</code> is null.
   */
   @Override
   public Object parseObject(String source, ParsePosition pos) {
      return null;
   }

   /**
    * Formats an object to produce a string.
    * @param obj the object to format.
    * @param map a map contains key-value pairs.
    * @return formatted string.
    */
   public String formatObj(Object obj, Map map) {
      if(obj == null) {
         return null;
      }

      StringBuilder result = new StringBuilder();
      StringBuilder param = new StringBuilder();
      boolean inparam = false;
      boolean inquote = false;
      String val = obj.toString();

      for(int i = 0; i < val.length(); i++) {
         // is a quote character?
         if(isQuoteChar(val, i)) {
            if(!inparam) {
               result.append("'");
            }
            else {
               param.append("'");
            }

            i += 1;
            continue;
         }
         // not yet in quote symbol?
         else if(!inquote) {
            // in quote symbol?
            if(val.charAt(i) == '\'') {
               inquote = true;

               // in param symbol? forbidden
               if(inparam) {
                  throw new RuntimeException(
                     "invalid string to be formatted found: " + val);
               }

               continue;
            }
            // not yet in parameter symbol?
            else if(!inparam) {
               if(val.charAt(i) != '{') {
                  result.append(val.charAt(i));
                  continue;
               }
               // parameter symbol start?
               else {
                  inparam = true;
                  continue;
               }
            }
            // in parameter symbol?
            else {
               if(val.charAt(i) != '}') {
                  param.append(val.charAt(i));
                  continue;
               }
               // parameter symbol end?
               else {
                  inparam = false;
                  String key = param.toString();
                  Object value = map.get(key);
                  value = value == null ? "" : value;
                  result.append(value);

                  param = new StringBuilder();
                  continue;
               }
            }
         }
         // in quote symbol?
         else {
            if(val.charAt(i) != '\'') {
               result.append(val.charAt(i));
               continue;
            }
            // quote symbol end?
            else {
               inquote = false;
               continue;
            }
         }
      }

      // in param symbol? forbidden
      if(inparam) {
         throw new RuntimeException(
            "invalid string to be formatted found: " + val);
      }
      // in quote symbol? deprecated
      else if(inquote) {
         /*
         throw new RuntimeException(
            "invalid string to be formatted found: " + val);
         */
         // @by billh, fix customer bug bug1309447688004
         return obj.toString();
      }
      else {
         return result.toString();
      }
   }

   /**
    * Get current value of the specified field.
    */
   private Map<String, Object> getKeyValuePairs(TableLens table, int r, String[] params) {
      Map<String, Object> map = new HashMap<>();
      Map<Object, Integer> colmap = new Hashtable<>();

      for(int i = 0; i < table.getColCount(); i++) {
         Object header = table.getObject(0, i);

         if(header != null && !header.equals("")) {
            colmap.put(header, i);
         }
      }

      for(int i = 0; i < params.length; i++) {
         Integer col = colmap.get(params[i]);

         if(col != null) {
            map.put(params[i], table.getObject(r, col));
         }
         // not find on the top most table, check for base tables
         else {
            int r2 = r;
            TableLens tbl2 = table;

            param:
            while(tbl2 instanceof TableFilter) {
               r2 = ((TableFilter) tbl2).getBaseRowIndex(r2);

               if(r2 > 0) {
                  tbl2 = ((TableFilter) tbl2).getTable();

                  for(int j = 0; j < tbl2.getColCount(); j++) {
                     Object header = tbl2.getObject(0, j);

                     if(header != null && header.equals(params[i])) {
                        map.put(params[i], tbl2.getObject(r2, j));
                        break param;
                     }
                  }
               }
               else {
                  break;
               }
            }
         }
      }

      return map;
   }

   /**
    * Check if is a quote char, a quote char should be "''".
    *
    * @param val the specified string
    * @param index the specified index
    * @return true if is, false otherwise
    */
   private boolean isQuoteChar(String val, int index) {
      return val.charAt(index) == '\'' &&
         val.length() >= index + 2 && val.charAt(index + 1) == '\'';
   }

   private static final Logger LOG =
      LoggerFactory.getLogger(ParameterFormat.class);
}
