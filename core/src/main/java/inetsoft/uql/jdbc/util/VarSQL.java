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
package inetsoft.uql.jdbc.util;

import inetsoft.uql.AbstractCondition;
import inetsoft.uql.VariableTable;
import inetsoft.uql.util.XUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * SQL variable processing.
 *
 * @version 12.2 02/05/2017
 * @author InetSoft Technology Corp
 */
public class VarSQL {
   public enum SQLType { STATEMENT, PROC, STRING }

   /**
    * How a value spliced into a quoted string literal is escaped.
    * <ul>
    *   <li>{@link #SQL} (default): SQL quote-doubling of the enclosing quote
    *   character, plus backslash doubling only when
    *   {@link #setBackslashIsEscapeChar} is set (Bug #76822).</li>
    *   <li>{@link #JSON}: fail-closed unicode-escape encoding for JSON /
    *   Mongo Extended JSON query text (as parsed by bson's
    *   {@code JsonReader}): every ASCII character other than
    *   {@code [A-Za-z0-9 _]}, and the first character of a {@code String}
    *   value, is written as <code>&#92;uXXXX</code>; non-ASCII characters
    *   pass through. Inside a {@code '...'} / {@code "..."} string bson
    *   decodes this to exactly the input value (Bug #76864). If the template
    *   desynchronises this class's SQL-oriented lexer from bson's (e.g. a
    *   placeholder or an unpaired quote inside a {@code /regex/} literal), a
    *   value in bson structural context fails to parse instead of adding keys
    *   or becoming a different bson type (Bug #77105).</li>
    * </ul>
    */
   public enum LiteralEscapeStyle { SQL, JSON }

   /**
    * Set the SQL string type.
    */
   public void setSQLType(SQLType type) {
      this.sqlType = type;
   }

   /**
    * Set whether the target database's SQL dialect treats a backslash as an
    * escape character inside a {@code '...'} string literal (e.g. MySQL/MariaDB
    * in their default {@code sql_mode}). Most dialects (PostgreSQL with the
    * default {@code standard_conforming_strings=on}, Oracle, SQL Server, DB2,
    * and others) do not, and a backslash is just a literal character there —
    * the default is {@code false}. This only affects how a value spliced into
    * a quoted {@code $(name)} placeholder (see {@link #replaceVariables}) is
    * escaped; callers that know the target dialect should set it accordingly.
    */
   public void setBackslashIsEscapeChar(boolean backslashIsEscapeChar) {
      this.backslashIsEscapeChar = backslashIsEscapeChar;
   }

   /**
    * Set how string values spliced into the text as literals are escaped —
    * both a value filling a quoted {@code '$(name)'} / {@code "$(name)"}
    * placeholder and the {@code '...'} literal produced for an unquoted
    * placeholder in {@link SQLType#STRING} mode. Defaults to
    * {@link LiteralEscapeStyle#SQL}. Callers that splice into JSON query text
    * (e.g. tabular connector properties with {@code @Property(sql=true)})
    * must use {@link LiteralEscapeStyle#JSON}.
    */
   public void setLiteralEscapeStyle(LiteralEscapeStyle literalEscapeStyle) {
      this.literalEscapeStyle = literalEscapeStyle == null ?
         LiteralEscapeStyle.SQL : literalEscapeStyle;
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
                  else if(embed) {
                     // $(@var) intentionally embeds a raw, unescaped SQL
                     // fragment (e.g. an admin-authored condition/expression
                     // snippet) rather than a data value, so it is spliced
                     // in verbatim regardless of quote state
                     if(val != null) {
                        sql.append(val.toString());
                     }
                     // $(@var) should add null to avoid the value missing
                     else if(sqlType == SQLType.STRING) {
                        sql.append("null");
                     }
                  }
                  else if(inQuote != 0) {
                     // variable used inside a quoted string literal - escape
                     // it so the value can't terminate the literal early or
                     // splice additional SQL (Bug #76822 / Redmine WSQ-008).
                     // this covers both a placeholder that fills the whole
                     // literal ('$(name)') and one that shares it with other
                     // literal text (e.g. '$(name)%' for STARTING_WITH),
                     // since only the value itself is escaped, not the
                     // surrounding literal text
                     if(val != null) {
                        // JSON style: a Number/Boolean/Date is emitted as is,
                        // like toSQLConstant does, so it keeps its type even
                        // if bson is not really inside this quote; any other
                        // value is encoded as a String (Bug #77105)
                        String text = val.toString();

                        if(literalEscapeStyle == LiteralEscapeStyle.JSON &&
                           isJsonSafeScalar(val, text))
                        {
                           sql.append(text);
                        }
                        else {
                           sql.append(escapeQuotedLiteralValue(text, (char) inQuote, true));
                        }
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
                           temp = temp.toLowerCase(Locale.ROOT);

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
    * Escape a value that is being spliced into a SQL string literal so it
    * cannot terminate the literal early or inject additional SQL. Always
    * doubles the literal's own quote character (the standard,
    * dialect-independent SQL escape for an embedded quote, safe on every
    * supported dialect). Additionally doubles backslashes, but only when
    * {@link #backslashIsEscapeChar} says the target dialect treats backslash
    * as a string-literal escape character (e.g. default MySQL/MariaDB) — on
    * dialects where backslash has no special meaning inside a literal
    * (Postgres with standard_conforming_strings, Oracle, SQL Server, DB2,
    * etc.), doubling it unconditionally would corrupt any value containing a
    * genuine literal backslash.
    * <p>
    * In {@link LiteralEscapeStyle#JSON} style the value is instead encoded by
    * {@link #escapeJsonStringValue}; {@code stringValue} (JSON style only,
    * ignored for SQL style) turns on its first-character rule.
    */
   private String escapeQuotedLiteralValue(String value, char quoteChar,
                                           boolean stringValue)
   {
      if(literalEscapeStyle == LiteralEscapeStyle.JSON) {
         return escapeJsonStringValue(value, stringValue);
      }

      StringBuilder escaped = new StringBuilder(value.length());

      for(int i = 0; i < value.length(); i++) {
         char c = value.charAt(i);

         if(c == quoteChar || (backslashIsEscapeChar && c == '\\')) {
            escaped.append(c);
         }

         escaped.append(c);
      }

      return escaped.toString();
   }

   /**
    * Escape a value spliced into JSON (Mongo Extended JSON) query text at a
    * position this class's lexer believes is (or wraps to be) inside a
    * {@code '...'} or {@code "..."} string literal.
    * <p>
    * Every ASCII character other than {@code [A-Za-z0-9 _]} is written as
    * <code>&#92;uXXXX</code>, and so is the first character when
    * {@code encodeFirst} is set (String values). Non-ASCII characters pass
    * through. Inside a bson string literal of either delimiter this decodes to
    * exactly the input value, and the encoded text has no quote, {@code /},
    * structural punctuation or bare backslash, so it cannot end a string or
    * {@code /regex/} literal bson is really in.
    * <p>
    * This lexer does not match bson's ({@code /regex/} literals, SQL comments
    * and backslash handling differ), so a template can make it believe a
    * placeholder is quoted when bson is in structural context (Bug #77105).
    * Such a String value then starts with a bare <code>&#92;u</code> and fails
    * to parse, instead of adding keys or turning into a MinKey, Infinity,
    * null, boolean or number literal. Inside a regex literal bson keeps the
    * <code>&#92;uXXXX</code> text as-is, which the MongoDB server rejects for
    * punctuation; {@code {$regex: '...$(name)...'}} is the supported way to
    * parameterise a regex.
    * <p>
    * Axis covered: template positions (quoted placeholder; unquoted scalar,
    * array scalar and {@code in} list via {@link #toSQLConstant}) &times;
    * value types. String values (and any other object) get the full encoding
    * including the first character. Number, Boolean and Date values do not
    * reach this method in JSON style (see {@link #isJsonSafeScalar}): they
    * are emitted unescaped, as {@link #toSQLConstant} does, so they keep
    * their type in either lexer state.
    * <p>
    * An empty String encodes to nothing. In a desync template at a bare array
    * element position ({@code [$(p)]}) bson then reads one element fewer; no
    * key or type is added.
    * <p>
    * This does not make a value safe inside server-side JavaScript
    * ({@code $where}, {@code $function.body}, {@code $accumulator},
    * mapReduce): bson decodes the value exactly and it then becomes JS source.
    */
   private static String escapeJsonStringValue(String value, boolean encodeFirst) {
      StringBuilder escaped = new StringBuilder(value.length() * 2);

      for(int i = 0; i < value.length(); i++) {
         char c = value.charAt(i);
         boolean plain = c >= 128 || c == ' ' || c == '_' || (c >= '0' && c <= '9') ||
            (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z');

         if(plain && !(encodeFirst && i == 0)) {
            escaped.append(c);
         }
         else {
            escaped.append('\\').append('u')
               .append(HEX[(c >> 12) & 0xf]).append(HEX[(c >> 8) & 0xf])
               .append(HEX[(c >> 4) & 0xf]).append(HEX[c & 0xf]);
         }
      }

      return escaped.toString();
   }

   /**
    * True if a non-String value can be spliced into JSON query text without
    * encoding: a Number, Boolean or Date whose text only uses
    * {@code [A-Za-z0-9 .:+-]} (e.g. {@code -3}, {@code 1.5}, {@code 1E+5},
    * {@code Infinity}, {@code true}, {@code 2024-01-02 03:04:05.0}). Such text
    * has no quote, {@code /} or backslash, so it cannot end a string or
    * {@code /regex/} literal bson is in, and no {@code , { } [ ]}, so it cannot
    * add keys or elements; inside a real bson string it decodes unchanged.
    * This matches {@link #toSQLConstant}, which emits these values unescaped
    * too (Bug #77105).
    */
   private static boolean isJsonSafeScalar(Object val, String text) {
      if(!(val instanceof Number || val instanceof Boolean || val instanceof java.util.Date)) {
         return false;
      }

      for(int i = 0; i < text.length(); i++) {
         char c = text.charAt(i);

         if(!((c >= '0' && c <= '9') || (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') ||
            c == ' ' || c == '.' || c == ':' || c == '+' || c == '-'))
         {
            return false;
         }
      }

      return true;
   }

   private static final char[] HEX = "0123456789abcdef".toCharArray();

   /**
    * Convert to SQL constant values (e.g. quoted string, date/time).
    */
   protected String toSQLConstant(Object val) {
      val = XUtil.toSQLValue(val, 0);

      if(val instanceof java.util.Date) {
         return AbstractCondition.getValueSQLString(val);
      }
      else if(val instanceof String) {
         // the value supplies the whole literal here, so it must be escaped
         // or it could close the literal early (Bug #76864)
         return "'" + escapeQuotedLiteralValue(val.toString(), '\'', true) + "'";
      }

      return val + "";
   }

   private SQLType sqlType = SQLType.STATEMENT;
   private boolean backslashIsEscapeChar = false;
   private LiteralEscapeStyle literalEscapeStyle = LiteralEscapeStyle.SQL;
   private List<Object> params = new ArrayList();
   private List<String> names = new ArrayList();
   private static final Logger LOG = LoggerFactory.getLogger(VarSQL.class);
}
