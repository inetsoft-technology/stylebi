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
package inetsoft.uql.jdbc.util;

import inetsoft.uql.AbstractCondition;
import inetsoft.uql.VariableTable;
import inetsoft.uql.util.XUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;

/**
 * SQL variable processing.
 *
 * @version 12.2 02/05/2017
 * @author InetSoft Technology Corp
 */
public class VarSQL {
   public enum SQLType { STATEMENT, PROC, STRING }

   /**
    * Set the SQL string type.
    */
   public void setSQLType(SQLType type) {
      this.sqlType = type;
   }

   /**
    * Get the values of the parameters used in the SQL.
    */
   public List<Object> getParameterValues() {
      return params;
   }

   /**
    * Get the names of the parameters used in the SQL.
    */
   public List<String> getParameterNames() {
      return names;
   }

   /**
    * Replace variables defined in the SQL string.
    */
   public String replaceVariables(String sqlstr, VariableTable vars) {
      try {
         StringBuilder sql = new StringBuilder();
         boolean escaped = false; // true if escaped
         int inQuote = 0; // ' or " if inside quote
         boolean inSingleLineComment = false; // true if inside single line comment
         boolean inMultiLineComment = false; // true if inside multi line comment
         char[] carr = sqlstr.toCharArray();
         int length = carr.length;

         for(int i = 0; i < length; i++) {
            char ch = carr[i];
            char nch = (i < length - 1) ? carr[i + 1] : ' ';

            // @by stephenwebster, For bug1425596520995
            // If the sequence '/*' is found outside quotes, then assume
            // this is the start/end of a multi line SQL comment.
            if(inMultiLineComment) {
               // signals the end of the multi line comment
               if(ch == '*' && nch == '/') {
                  inMultiLineComment = false;
               }
            }
            // signals the start of a multi line comment
            else if(ch == '/' && nch == '*' && inQuote == 0) {
               inMultiLineComment = true;
            }

            // @by stephenwebster, For bug1425596520995
            // If the sequence '--' is found outside a quoted text, then assume
            // this is the start of a single line SQL comment.
            if(inSingleLineComment) {
               // signals the end of the single line comment
               if(ch == '\n') {
                  inSingleLineComment = false;
               }
            }
            // signals the start of a single line comment
            else if(ch == '-' && nch == '-' && inQuote == 0) {
               inSingleLineComment = true;
            }

            if(escaped) {
               escaped = false;
            }
            // @by stephenwebster, Refine bug1425596520995 and Bug #1582
            // Should not remove the comments as they could be hints into the
            // database.  Instead, ignore quotes that are in comments.
            else if((inQuote == 0 && (ch == '\'' || ch == '"') || ch == inQuote)
                     && !(inMultiLineComment || inSingleLineComment))
            {
               inQuote = (inQuote == 0) ? ch : 0;
            }
            else if(ch == '\\') {
               escaped = true;
               continue;
            }
            // find variable
            else if(ch == '$' && nch == '(' &&
               !(inMultiLineComment || inSingleLineComment))
            {
               int idx = sqlstr.indexOf(')', i + 2);

               if(idx > 0) {
                  String var = sqlstr.substring(i + 2, idx).trim();
                  // if var name is $(@var), the variable is embedded in sql
                  // instead of passed as parameter, @ is stripped from name
                  boolean embed = var.startsWith("@");

                  if(embed) {
                     var = var.substring(1);
                  }

                  Object val = vars.get(var);

                  // change java.util.Date to java.sql.Date, otherwise
                  // the date type is not known by the JDBC driver
                  val = XUtil.toSQLValue(val, 0);

                  // @by jamshedd, replace the vpm control points with the
                  // corresponding vpm condition
                  if(var.startsWith("?")) {
                     sql.append(val);
                  }
                  else if(inQuote != 0 || embed) {
                     // if variabled used in quote, replace the var with string
                     if(val != null) {
                        sql.append(val.toString());
                     }
                     // $(@var) should add null to avoid the value missing
                     else if(embed && sqlType == SQLType.STRING) {
                        sql.append("null");
                     }
                  }
                  // if value is an array, replace with ?,?,?,...
                  else if(val != null && val.getClass().isArray()) {
                     if(sqlType == SQLType.PROC) {
                        int len = Array.getLength(val);
                        StringBuilder str = new StringBuilder();

                        for(int k = 0; k < len; k++) {
                           if(k > 0) {
                              str.append(",");
                           }

                           str.append(Array.get(val, k));
                        }

                        params.add(str.toString());
                        names.add(var);
                        sql.append(" ? ");
                     }
                     else {
                        // check if " in $(var)" or " in ($(var))" pattern
                        boolean in = false;
                        boolean fine = false;
                        boolean quoted = false;
                        int k = sql.length() - 1;

                        for(; k >= 0; k--) {
                           char c = sql.charAt(k);

                           if(c <= ' ') {
                              fine = true;
                           }
                           else if(c == '(') {
                              if(quoted) {
                                 fine = false;
                                 break;
                              }

                              fine = true;
                              quoted = true;
                           }
                           else {
                              break;
                           }
                        }

                        if(k >= 0 && fine) {
                           String temp = sql.substring(0, k + 1);
                           temp = temp.toLowerCase();

                           if(temp.endsWith("in") && temp.length() > 3 &&
                              temp.charAt(temp.length() - 3) <= ' ')
                           {
                              in = true;
                           }
                        }

                        int len = Array.getLength(val);

                        // only in requires multiple values
                        if(!in) {
                           len = Math.min(len, 1);
                        }

                        if(in && !quoted) {
                           sql.append('(');
                        }

                        for(k = 0; k < len; k++) {
                           if(k > 0) {
                              sql.append(",");
                           }

                           if(sqlType == SQLType.STRING) {
                              sql.append(" " + toSQLConstant(Array.get(val, k)) + " ");
                           }
                           else {
                              params.add(Array.get(val, k));
                              names.add(var);
                              sql.append(" ? ");
                           }
                        }

                        if(in && !quoted) {
                           sql.append(')');
                        }
                     }
                  }
                  // replace with actual value
                  else if(sqlType == SQLType.STRING) {
                     sql.append(" " + toSQLConstant(val) + " ");
                  }
                  else {
                     params.add(val);
                     names.add(var);
                     sql.append(" ? ");
                  }

                  i = idx;
                  continue;
               }
            }

            sql.append(ch);
         }

         return sql.toString();
      }
      catch(Exception e) {
         LOG.error("Failed to replace variables in SQL \"" + sqlstr + "\": " + vars,
            e);
      }

      return sqlstr;
   }

   /**
    * Convert to SQL constant values (e.g. quoted string, date/time).
    */
   protected String toSQLConstant(Object val) {
      val = XUtil.toSQLValue(val, 0);

      if(val instanceof java.util.Date) {
         return AbstractCondition.getValueSQLString(val);
      }
      else if(val instanceof String) {
         return "'" + val.toString() + "'";
      }

      return val + "";
   }

   private SQLType sqlType = SQLType.STATEMENT;
   private List<Object> params = new ArrayList();
   private List<String> names = new ArrayList();
   private static final Logger LOG = LoggerFactory.getLogger(VarSQL.class);
}
